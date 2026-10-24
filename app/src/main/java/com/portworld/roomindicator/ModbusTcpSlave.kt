package com.portworld.roomindicator

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Minimal Modbus TCP slave exposing 3 holding registers (0=humidity x10,
 * 1=differential pressure x10 signed, 2=counter), the same contract as the
 * ESP32. Accepts FC03 read, FC06 write-single, FC16 write-multiple over
 * registers 0..2, validates humidity <= 1000, and tracks a per-register
 * write time so a register not refreshed within the timeout reads back
 * invalid (UI shows "---").
 *
 * Note: binding port 502 needs system privileges on Android; off-panel use a
 * higher [port] (e.g. 1502). On the real panel ROM, 502 is available.
 */
class ModbusTcpSlave(
    private val unitId: Int = 1,
    private val port: Int = 502,
    private val fallbackPort: Int = 1502,
) {
    @Volatile private var running = false
    private var server: ServerSocket? = null

    /** The port actually bound (502 on the panel, [fallbackPort] off-panel), or 0 if not yet listening. */
    @Volatile var boundPort: Int = 0
        private set

    private val lock = Any()
    private val regs = IntArray(3)
    private val regWriteNs = LongArray(3)   // 0 = never written

    fun start() {
        if (running) return
        running = true
        thread(name = "modbus-slave", isDaemon = true) { serverLoop() }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
    }

    fun snapshot(timeoutMs: Long): Sample {
        val now = System.nanoTime()
        val staleNs = timeoutMs * 1_000_000L
        synchronized(lock) {
            val v0 = regWriteNs[0] != 0L && now - regWriteNs[0] < staleNs
            val v1 = regWriteNs[1] != 0L && now - regWriteNs[1] < staleNs
            val v2 = regWriteNs[2] != 0L && now - regWriteNs[2] < staleNs
            val pressure = if (regs[1] <= Short.MAX_VALUE) regs[1] else regs[1] - 65536
            return Sample(
                humidityX10 = regs[0],
                pressureX10 = pressure,
                counter = regs[2],
                humValid = v0, preValid = v1, cntValid = v2,
                online = v0 || v1 || v2,
            )
        }
    }

    private fun note(reg: Int, value: Int) {
        synchronized(lock) {
            regs[reg] = value and 0xFFFF
            regWriteNs[reg] = System.nanoTime()
        }
    }

    private fun read(reg: Int): Int = synchronized(lock) { regs[reg] }

    private fun serverLoop() {
        // Prefer 502 (works as a system app on the panel); if that is denied
        // (a normal app, e.g. on the emulator) fall back to a high port.
        val ports = if (fallbackPort > 0 && fallbackPort != port)
            intArrayOf(port, fallbackPort) else intArrayOf(port)
        while (running) {
            var s: ServerSocket? = null
            for (p in ports) {
                try { s = ServerSocket(p); boundPort = p; break }
                catch (t: Throwable) { Log.w(TAG, "bind failed on :$p: $t") }
            }
            if (s == null) {
                if (running) try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                continue
            }
            server = s
            Log.i(TAG, "Modbus TCP slave on :$boundPort, unit $unitId")
            try {
                while (running) {
                    val sock = s.accept()
                    thread(isDaemon = true) { handle(sock) }
                }
            } catch (t: Throwable) {
                if (running) {
                    Log.w(TAG, "accept failed on :$boundPort: $t")
                    try { Thread.sleep(2000) } catch (_: InterruptedException) {}
                }
            } finally {
                boundPort = 0
                try { s.close() } catch (_: Throwable) {}
            }
        }
    }

    private fun handle(sock: Socket) {
        sock.use {
            val din = DataInputStream(it.getInputStream())
            val dout = DataOutputStream(it.getOutputStream())
            val header = ByteArray(7)
            while (running) {
                try { din.readFully(header) } catch (_: Throwable) { break }
                val len = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
                val unit = header[6].toInt() and 0xFF
                val pduLen = len - 1
                if (pduLen <= 0 || pduLen > 260) break
                val pdu = ByteArray(pduLen)
                try { din.readFully(pdu) } catch (_: Throwable) { break }
                if (unit != unitId && unit != 0 && unit != 255) continue
                val resp = process(pdu) ?: continue
                val out = ByteArray(7 + resp.size)
                out[0] = header[0]; out[1] = header[1]  // txn id echo
                out[2] = 0; out[3] = 0                  // protocol id
                val l = resp.size + 1
                out[4] = ((l ushr 8) and 0xFF).toByte()
                out[5] = (l and 0xFF).toByte()
                out[6] = header[6]                      // unit id echo
                System.arraycopy(resp, 0, out, 7, resp.size)
                try { dout.write(out); dout.flush() } catch (_: Throwable) { break }
            }
        }
    }

    private fun process(pdu: ByteArray): ByteArray? = when (pdu[0].toInt() and 0xFF) {
        0x03 -> readHolding(pdu)
        0x06 -> writeSingle(pdu)
        0x10 -> writeMultiple(pdu)
        else -> exc(pdu[0].toInt() and 0xFF, 0x01)   // illegal function
    }

    private fun u16(b: ByteArray, off: Int) =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun readHolding(pdu: ByteArray): ByteArray {
        if (pdu.size < 5) return exc(0x03, 0x03)
        val addr = u16(pdu, 1); val qty = u16(pdu, 3)
        if (qty < 1 || qty > 3 || addr < 0 || addr + qty > 3) return exc(0x03, 0x02)
        val bc = qty * 2
        val r = ByteArray(2 + bc)
        r[0] = 0x03; r[1] = bc.toByte()
        for (i in 0 until qty) {
            val v = read(addr + i)
            r[2 + i * 2] = ((v ushr 8) and 0xFF).toByte()
            r[3 + i * 2] = (v and 0xFF).toByte()
        }
        return r
    }

    private fun writeSingle(pdu: ByteArray): ByteArray {
        if (pdu.size < 5) return exc(0x06, 0x03)
        val addr = u16(pdu, 1); val value = u16(pdu, 3)
        if (addr < 0 || addr > 2) return exc(0x06, 0x02)
        if (addr == 0 && value > 1000) return exc(0x06, 0x03)
        note(addr, value)
        return pdu.copyOfRange(0, 5)   // echo request
    }

    private fun writeMultiple(pdu: ByteArray): ByteArray {
        if (pdu.size < 6) return exc(0x10, 0x03)
        val addr = u16(pdu, 1); val qty = u16(pdu, 3); val bc = pdu[5].toInt() and 0xFF
        if (qty < 1 || qty > 3 || addr < 0 || addr + qty > 3) return exc(0x10, 0x02)
        if (bc != qty * 2 || pdu.size < 6 + bc) return exc(0x10, 0x03)
        for (i in 0 until qty) {
            if (addr + i == 0 && u16(pdu, 6 + i * 2) > 1000) return exc(0x10, 0x03)
        }
        for (i in 0 until qty) note(addr + i, u16(pdu, 6 + i * 2))
        return byteArrayOf(0x10, pdu[1], pdu[2], pdu[3], pdu[4])   // echo addr + qty
    }

    private fun exc(fc: Int, code: Int) = byteArrayOf((fc or 0x80).toByte(), code.toByte())

    companion object { private const val TAG = "ModbusSlave" }
}
