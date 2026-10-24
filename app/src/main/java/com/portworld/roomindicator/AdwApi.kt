package com.portworld.roomindicator

import android.content.Context
import android.util.Log

/**
 * Thin wrapper over the panel's system API (android.app.ADWApiManager, provided
 * by the ROM). Off-panel (emulator / normal Android) the class is absent, so
 * construction and every call are guarded and simply no-op; the app still runs
 * so the UI and Modbus can be developed without the hardware.
 */
class AdwApi(ctx: Context) {

    private val mgr: android.app.ADWApiManager? = try {
        android.app.ADWApiManager(ctx)
    } catch (t: Throwable) {
        Log.w(TAG, "Panel API unavailable (running off-panel?): $t")
        null
    }

    val available: Boolean get() = mgr != null

    /** Map a 0..100 percent to the panel's 0..255 brightness. */
    fun setBrightnessPercent(percent: Int) = safe {
        val lux = (percent.coerceIn(0, 100) * 255) / 100
        mgr?.SetDeviceBrightness(lux)
    }

    /**
     * Arg order per the PCP5 API doc: isStatic, ip, netMask, dns, netGate, dns2.
     * The ROM's handler NPEs on a blank DNS, so never pass empty addresses.
     */
    fun setStaticIp(ip: String, mask: String, gateway: String, dns: String, dns2: String) = safe {
        val d1 = dns.ifBlank { "8.8.8.8" }
        val d2 = dns2.ifBlank { d1 }
        mgr?.SetDeviceEthernetMode(true, ip, mask, d1, gateway, d2)
    }

    fun setDhcp() = safe {
        mgr?.SetDeviceEthernetMode(false, "", "", "", "", "")
    }

    fun getDeviceIp(): String? = try { mgr?.GetDeviceIpAddr() } catch (t: Throwable) { null }

    /** Hide the Android navigation bar for a clean kiosk panel. */
    fun hideNavBar() = safe { mgr?.OpenOrCloseNav(false) }

    /** Reboot the panel (privileged; only effective as a system app). */
    fun reboot() = safe { mgr?.Reboot() }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (t: Throwable) { Log.w(TAG, "Panel API call failed: $t") }
    }

    companion object { private const val TAG = "AdwApi" }
}
