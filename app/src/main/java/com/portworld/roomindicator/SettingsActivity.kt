package com.portworld.roomindicator

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Patterns
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val idleClose = Runnable { finish() }
    private val briLevels = intArrayOf(20, 40, 60, 80, 100)

    private val ink = 0xFF1A1C19.toInt()
    private val muted = 0xFF6B6E69.toInt()
    private val screen = 0xFFF3F4F2.toInt()
    private val card = 0xFFFFFFFF.toInt()
    private val border = 0xFFDDE0DB.toInt()
    private val green = 0xFF4AA547.toInt()
    private val red = 0xFFC0392B.toInt()

    private lateinit var app: RoomApp
    private lateinit var s: Settings

    private lateinit var spMode: Spinner
    private lateinit var spBri: Spinner
    private lateinit var swDecHum: Switch
    private lateinit var swDecPre: Switch
    private lateinit var swStatic: Switch
    private lateinit var etIp: EditText
    private lateinit var etMask: EditText
    private lateinit var etGw: EditText
    private lateinit var etDns: EditText
    private lateinit var etDns2: EditText
    private lateinit var etHlo: EditText
    private lateinit var etHhi: EditText
    private lateinit var etPlo: EditText
    private lateinit var etPhi: EditText
    private lateinit var etTmo: EditText
    private lateinit var etPin: EditText
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = RoomApp.instance
        s = app.store.load()
        showKeypad()
    }

    // ---------------------------------------------------------------- PIN pad
    private fun showKeypad() {
        val entered = StringBuilder()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(screen)
            setPadding(dp(28), dp(28), dp(28), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Enter PIN"
            textSize = 26f
            setTextColor(ink)
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP).also { it.bottomMargin = dp(16) })

        val dots = TextView(this).apply {
            text = ""
            textSize = 40f
            setTextColor(ink)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            minHeight = dp(56)
        }
        root.addView(dots, lp(MATCH, WRAP).also { it.bottomMargin = dp(4) })

        val err = TextView(this).apply {
            textSize = 15f; setTextColor(red); gravity = Gravity.CENTER
        }
        root.addView(err, lp(MATCH, WRAP).also { it.bottomMargin = dp(12) })

        fun render() { dots.text = "•".repeat(entered.length) }

        fun press(d: String) {
            err.text = ""
            if (entered.length < 12) { entered.append(d); render() }
        }

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = arrayOf(arrayOf("1","2","3"), arrayOf("4","5","6"), arrayOf("7","8","9"))
        for (r in rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (d in r) row.addView(padButton(d) { press(d) }, padLp())
            grid.addView(row, rowLp())
        }
        // Last row: backspace, 0, OK
        val last = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        last.addView(padButton("⌫") {
            if (entered.isNotEmpty()) { entered.deleteCharAt(entered.length - 1); render() }
        }, padLp())
        last.addView(padButton("0") { press("0") }, padLp())
        last.addView(padButton("✓", green, 0xFFFFFFFF.toInt()) {
            if (entered.toString() == s.pin) buildForm()
            else { err.text = "Wrong PIN"; entered.clear(); render() }
        }, padLp())
        grid.addView(last, rowLp())

        root.addView(grid, lp(MATCH, WRAP).also { it.topMargin = dp(4) })

        root.addView(Button(this).apply {
            text = "Cancel"; isAllCaps = false; textSize = 16f
            setOnClickListener { finish() }
        }, lp(MATCH, WRAP).also { it.topMargin = dp(16) })

        val scroll = ScrollView(this).apply { addView(root); setBackgroundColor(screen) }
        setContentView(scroll)
    }

    private fun padButton(label: String, bg: Int = card, fg: Int = ink, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; textSize = 26f
            setTextColor(fg); setBackgroundColor(bg)
            minHeight = dp(66)
            setOnClickListener { onClick() }
        }

    private fun padLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        .also { it.setMargins(dp(5), dp(5), dp(5), dp(5)) }

    private fun rowLp() = LinearLayout.LayoutParams(MATCH, WRAP)

    // --------------------------------------------------------------- the form
    private fun buildForm() {
        val scroll = ScrollView(this).apply { setBackgroundColor(screen) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }
        scroll.addView(col)

        heading(col, "Settings", 28f)
        val ip = app.adw.getDeviceIp().let { if (it.isNullOrEmpty() || it == "0.0.0.0") "(none)" else it }
        caption(col, "Current IP: $ip")
        caption(col, "Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        val mbPort = app.modbus.boundPort
        caption(col, "Modbus: " + if (mbPort == 0) "(starting…)" else ":$mbPort unit 1")

        section(col, "Display")
        spMode = spinnerField(col, "Mode", arrayOf("Both", "Humidity only", "Pressure only"), s.displayMode)
        spBri = spinnerField(col, "Brightness",
            arrayOf("20%", "40%", "60%", "80%", "100%"), briIndex(s.brightness))
        swDecHum = Switch(this).apply { isChecked = s.decimalsHum }
        switchField(col, "Humidity decimals", swDecHum)
        swDecPre = Switch(this).apply { isChecked = s.decimalsPre }
        switchField(col, "Pressure decimals", swDecPre)

        section(col, "Network")
        swStatic = Switch(this).apply { isChecked = s.staticIp }
        switchField(col, "Static IP", swStatic)
        etIp = textField(col, "IP address", s.ip, false)
        etMask = textField(col, "Subnet mask", s.mask, false)
        etGw = textField(col, "Gateway", s.gateway, false)
        etDns = textField(col, "DNS", s.dns, false)
        etDns2 = textField(col, "Secondary DNS", s.dns2, false)
        // Allow dotted-decimal entry (digits + '.') on the address fields.
        for (f in listOf(etIp, etMask, etGw, etDns, etDns2)) {
            f.keyListener = DigitsKeyListener.getInstance("0123456789.")
        }

        section(col, "Alarm limits")
        etHlo = textField(col, "Humidity min (%RH)", fmt1(s.humLoX10), true)
        etHhi = textField(col, "Humidity max (%RH)", fmt1(s.humHiX10), true)
        etPlo = textField(col, "Pressure min (Pa)", fmt1(s.preLoX10), true)
        etPhi = textField(col, "Pressure max (Pa)", fmt1(s.preHiX10), true)

        section(col, "Data")
        etTmo = textField(col, "Data timeout (s)", s.timeoutS.toString(), false)

        section(col, "Security")
        etPin = textField(col, "New PIN (blank = keep)", "", false).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        section(col, "System")
        col.addView(bigButton("Reboot panel", card, ink) { confirmReboot() },
            lp(MATCH, WRAP).also { it.topMargin = dp(12) })

        status = TextView(this).apply { setTextColor(muted); textSize = 15f }
        col.addView(status, lp(MATCH, WRAP).also { it.topMargin = dp(14) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(bigButton("Cancel", card, ink) { finish() },
            LinearLayout.LayoutParams(0, WRAP, 1f).also { it.rightMargin = dp(8) })
        row.addView(bigButton("Save", green, 0xFFFFFFFF.toInt()) { onSave() },
            LinearLayout.LayoutParams(0, WRAP, 1f).also { it.leftMargin = dp(8) })
        col.addView(row, lp(MATCH, WRAP).also { it.topMargin = dp(18) })

        setContentView(scroll)
        resetIdle()
    }

    private fun onSave() {
        s.displayMode = spMode.selectedItemPosition
        s.brightness = briLevels[spBri.selectedItemPosition]
        s.decimalsHum = swDecHum.isChecked
        s.decimalsPre = swDecPre.isChecked
        s.staticIp = swStatic.isChecked

        if (s.staticIp) {
            val ipT = etIp.text.toString().trim()
            val maskT = etMask.text.toString().trim()
            val gwT = etGw.text.toString().trim()
            if (!isIp(ipT)) return err("Invalid IP address")
            if (!isIp(maskT)) return err("Invalid netmask")
            if (!isIp(gwT)) return err("Invalid gateway")
            val dnsT = etDns.text.toString().trim().ifEmpty { "8.8.8.8" }
            val dns2T = etDns2.text.toString().trim().ifEmpty { dnsT }
            if (!isIp(dnsT)) return err("Invalid DNS")
            if (!isIp(dns2T)) return err("Invalid secondary DNS")
            s.ip = ipT; s.mask = maskT; s.gateway = gwT; s.dns = dnsT; s.dns2 = dns2T
        }

        val hlo = parse1(etHlo.text.toString()) ?: return err("Invalid humidity limits")
        val hhi = parse1(etHhi.text.toString()) ?: return err("Invalid humidity limits")
        val plo = parse1(etPlo.text.toString()) ?: return err("Invalid pressure limits")
        val phi = parse1(etPhi.text.toString()) ?: return err("Invalid pressure limits")
        if (hlo > hhi) return err("Humidity low > high")
        if (plo > phi) return err("Pressure low > high")
        s.humLoX10 = hlo.coerceIn(0, 1000); s.humHiX10 = hhi.coerceIn(0, 1000)
        s.preLoX10 = plo.coerceIn(-32768, 32767); s.preHiX10 = phi.coerceIn(-32768, 32767)

        val tmo = etTmo.text.toString().toIntOrNull() ?: return err("Invalid timeout")
        if (tmo < 1) return err("Timeout must be >= 1 s")
        s.timeoutS = tmo.coerceAtMost(3600)

        val newPin = etPin.text.toString()
        if (newPin.isNotEmpty()) {
            if (newPin.length < 4) return err("PIN needs >= 4 digits")
            s.pin = newPin
        }

        // Only reconfigure the network when a network field actually changed;
        // re-applying the IP bounces the Ethernet link and drops Modbus clients.
        val old = app.store.load()
        val netChanged = old.staticIp != s.staticIp || old.ip != s.ip ||
            old.mask != s.mask || old.gateway != s.gateway || old.dns != s.dns || old.dns2 != s.dns2

        app.store.save(s)
        app.adw.setBrightnessPercent(s.brightness)
        if (netChanged) {
            if (s.staticIp) app.adw.setStaticIp(s.ip, s.mask, s.gateway, s.dns, s.dns2) else app.adw.setDhcp()
        }
        finish()
    }

    private fun err(msg: String) { status.setTextColor(red); status.text = msg }

    private fun confirmReboot() {
        AlertDialog.Builder(this)
            .setTitle("Reboot panel?")
            .setMessage("The panel will restart now.")
            .setPositiveButton("Reboot") { _, _ -> app.adw.reboot() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- view helpers ----
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun heading(parent: LinearLayout, t: String, size: Float) {
        parent.addView(TextView(this).apply {
            text = t; textSize = size; setTextColor(ink)
        }, lp(MATCH, WRAP).also { it.bottomMargin = dp(4) })
    }

    private fun caption(parent: LinearLayout, t: String) {
        parent.addView(TextView(this).apply {
            text = t; textSize = 14f; setTextColor(muted)
        }, lp(MATCH, WRAP))
    }

    private fun section(parent: LinearLayout, t: String) {
        parent.addView(TextView(this).apply {
            text = t; textSize = 18f; setTextColor(green)
        }, lp(MATCH, WRAP).also { it.topMargin = dp(22); it.bottomMargin = dp(2) })
        parent.addView(View(this).apply { setBackgroundColor(border) }, lp(MATCH, dp(1)))
    }

    private fun fieldLabel(t: String) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(muted)
    }

    private fun textField(parent: LinearLayout, label: String, value: String, decimal: Boolean): EditText {
        parent.addView(fieldLabel(label), lp(MATCH, WRAP).also { it.topMargin = dp(12); it.bottomMargin = dp(4) })
        val et = EditText(this).apply {
            setText(value)
            setTextColor(ink)
            setHintTextColor(muted)
            textSize = 18f
            setBackgroundColor(card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = if (decimal)
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            else InputType.TYPE_CLASS_NUMBER
        }
        parent.addView(et, lp(MATCH, WRAP))
        return et
    }

    private fun switchField(parent: LinearLayout, label: String, sw: Switch) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = label; textSize = 16f; setTextColor(ink)
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        row.addView(sw)
        parent.addView(row, lp(MATCH, WRAP).also { it.topMargin = dp(12) })
    }

    private fun spinnerField(parent: LinearLayout, label: String, items: Array<String>, sel: Int): Spinner {
        parent.addView(fieldLabel(label), lp(MATCH, WRAP).also { it.topMargin = dp(12); it.bottomMargin = dp(4) })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(sel.coerceIn(0, items.size - 1))
        parent.addView(sp, lp(MATCH, WRAP))
        return sp
    }

    private fun bigButton(label: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 18f
        setTextColor(fg); setBackgroundColor(bg)
        minHeight = dp(56)
        setOnClickListener { onClick() }
    }

    private fun briIndex(pct: Int): Int {
        var best = 4; var bd = Int.MAX_VALUE
        for (i in briLevels.indices) { val d = abs(pct - briLevels[i]); if (d < bd) { bd = d; best = i } }
        return best
    }
    private fun fmt1(x10: Int): String {
        val mag = abs(x10); return "${if (x10 < 0) "-" else ""}${mag / 10}.${mag % 10}"
    }
    private fun parse1(t: String): Int? {
        val v = t.trim().toDoubleOrNull() ?: return null
        return (v * 10.0).roundToInt()
    }
    private fun isIp(t: String) = Patterns.IP_ADDRESS.matcher(t).matches()

    // ---- inactivity auto-close ----
    private fun resetIdle() { handler.removeCallbacks(idleClose); handler.postDelayed(idleClose, 60_000) }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { resetIdle(); return super.dispatchTouchEvent(ev) }
    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(idleClose) }
}
