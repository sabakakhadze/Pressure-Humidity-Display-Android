package com.portworld.roomindicator

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: RoomApp
    private var settings = Settings()

    private lateinit var stripBox: LinearLayout
    private lateinit var stripIcon: ImageView
    private lateinit var stripLabel: TextView
    private lateinit var body: LinearLayout
    private lateinit var divider: View
    private lateinit var offline: View

    private lateinit var preBlock: ReadingBlock
    private lateinit var humBlock: ReadingBlock

    private var preAlarm = false
    private var humAlarm = false
    private var curMode = -1
    private var tapCount = 0
    private var lastTapMs = 0L
    private var curIcon = 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    /** One reading: caption on top, big value centred, unit beside/below it. */
    private inner class ReadingBlock(caption: String, unit: String) {
        val frame = FrameLayout(this@MainActivity)
        val captionView = TextView(this@MainActivity)
        val valueView = TextView(this@MainActivity)
        val unitView = TextView(this@MainActivity)
        private var pulse: ObjectAnimator? = null

        init {
            captionView.text = caption
            captionView.setTextColor(0xFF6B6E69.toInt())
            captionView.textSize = 18f
            captionView.setPadding(dp(28), 0, dp(28), 0)
            frame.addView(captionView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).also { it.topMargin = dp(10) })

            valueView.text = "---"
            valueView.setTextColor(0xFF1A1C19.toInt())
            valueView.includeFontPadding = false
            frame.addView(valueView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER))

            unitView.text = unit
            unitView.setTextColor(0xFF6B6E69.toInt())
            unitView.textSize = 22f
            frame.addView(unitView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun applyMode(single: Boolean) {
            valueView.textSize = if (single) 170f else 120f
            unitView.textSize = if (single) 48f else 32f
            val up = unitView.layoutParams as FrameLayout.LayoutParams
            val cp = captionView.layoutParams as FrameLayout.LayoutParams
            if (single) {
                captionView.textSize = 42f
                // Equal top/bottom insets so caption and unit sit balanced
                // around the centred value, not glued to the edges.
                cp.topMargin = dp(60)
                up.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                up.bottomMargin = dp(60); up.rightMargin = 0
            } else {
                captionView.textSize = 28f
                cp.topMargin = dp(34)
                up.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                up.bottomMargin = 0; up.rightMargin = dp(44)
            }
            captionView.layoutParams = cp
            unitView.layoutParams = up
        }

        fun setValue(text: String, alarm: Boolean) {
            valueView.text = text
            valueView.setTextColor(if (alarm) 0xFFC0392B.toInt() else 0xFF1A1C19.toInt())
        }

        fun setPulsing(on: Boolean) {
            if (on && pulse == null) {
                pulse = ObjectAnimator.ofFloat(valueView, "alpha", 1f, 0.4f).apply {
                    duration = 550
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            } else if (!on && pulse != null) {
                pulse?.cancel(); pulse = null; valueView.alpha = 1f
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = RoomApp.instance

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFFF3F4F2.toInt())

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Status strip takes the top third: a big shield icon over a label.
        stripBox = LinearLayout(this)
        stripBox.orientation = LinearLayout.VERTICAL
        stripBox.gravity = Gravity.CENTER
        stripBox.setBackgroundColor(0xFF4AA547.toInt())

        stripIcon = ImageView(this)
        stripIcon.setImageResource(R.drawable.ic_status_ok)
        curIcon = R.drawable.ic_status_ok
        stripBox.addView(stripIcon, LinearLayout.LayoutParams(dp(165), dp(165)))

        stripLabel = TextView(this)
        stripLabel.gravity = Gravity.CENTER
        stripLabel.setTextColor(Color.WHITE)
        stripLabel.textSize = 52f
        stripLabel.text = "Normal"
        stripLabel.setTypeface(null, Typeface.BOLD)
        stripBox.addView(stripLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.topMargin = dp(22) })

        content.addView(stripBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        content.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))

        preBlock = ReadingBlock("Differential pressure", "Pa")
        humBlock = ReadingBlock("Humidity", "RH")

        body.addView(preBlock.frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        divider = View(this).apply { setBackgroundColor(0xFFDDE0DB.toInt()) }
        body.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).also {
                it.leftMargin = dp(24); it.rightMargin = dp(24) })
        body.addView(humBlock.frame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Full-screen offline cover (hidden until offline).
        offline = FrameLayout(this).apply {
            setBackgroundColor(0xFF5A5F5B.toInt())
            visibility = View.GONE
            val t = TextView(this@MainActivity)
            t.text = "Offline"; t.setTextColor(Color.WHITE); t.textSize = 48f
            addView(t, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER))
        }
        root.addView(offline, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Hidden settings entry: 5 taps in the top-left corner within 2 s.
        // Each tap shows a brief count, so it doubles as touch feedback.
        val trigger = View(this)
        trigger.isClickable = true
        root.addView(trigger, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.TOP or Gravity.START))
        trigger.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTapMs > 2000L) tapCount = 0
            lastTapMs = now
            tapCount++
            if (tapCount >= 5) {
                tapCount = 0
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                Toast.makeText(this, "Settings ${tapCount}/5", Toast.LENGTH_SHORT).show()
            }
        }

        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        app.adw.hideNavBar()
    }

    override fun onResume() {
        super.onResume()
        settings = app.store.load()
        app.adw.setBrightnessPercent(settings.brightness)
        applyMode(settings.displayMode)
        hideSystemBars()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun applyMode(mode: Int) {
        curMode = mode
        val showH = mode != DisplayMode.PRESSURE
        val showP = mode != DisplayMode.HUMIDITY
        val single = showH != showP
        preBlock.frame.visibility = if (showP) View.VISIBLE else View.GONE
        humBlock.frame.visibility = if (showH) View.VISIBLE else View.GONE
        divider.visibility = if (!single && showH && showP) View.VISIBLE else View.GONE
        if (showP) preBlock.applyMode(single)
        if (showH) humBlock.applyMode(single)
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (settings.displayMode != curMode) applyMode(settings.displayMode)
            val s = app.modbus.snapshot(settings.timeoutS * 1000L)

            val showH = settings.displayMode != DisplayMode.PRESSURE
            val showP = settings.displayMode != DisplayMode.HUMIDITY
            val humHi = s.humValid && s.humidityX10 > settings.humHiX10
            val humLo = s.humValid && s.humidityX10 < settings.humLoX10
            val preHi = s.preValid && s.pressureX10 > settings.preHiX10
            val preLo = s.preValid && s.pressureX10 < settings.preLoX10
            val vpa = showP && (preHi || preLo)
            val vha = showH && (humHi || humLo)
            val badge = when (settings.displayMode) {
                DisplayMode.HUMIDITY -> s.humValid
                DisplayMode.PRESSURE -> s.preValid
                else -> s.online
            }

            offline.visibility = if (badge) View.GONE else View.VISIBLE

            humBlock.setValue(if (s.humValid) fmtHum(s.humidityX10) else "---", vha)
            preBlock.setValue(if (s.preValid) fmtPre(s.pressureX10) else "---", vpa)

            val (col, iconRes, label) = when {
                vpa -> Triple(0xFFC0392B.toInt(), R.drawable.ic_status_alarm, if (preHi) "Pressure high" else "Pressure low")
                vha -> Triple(0xFFC0392B.toInt(), R.drawable.ic_status_alarm, if (humHi) "Humidity high" else "Humidity low")
                else -> Triple(0xFF4AA547.toInt(), R.drawable.ic_status_ok, "Normal")
            }
            stripBox.setBackgroundColor(col)
            if (iconRes != curIcon) { stripIcon.setImageResource(iconRes); curIcon = iconRes }
            stripLabel.text = label

            if (vpa != preAlarm) { preBlock.setPulsing(vpa); preAlarm = vpa }
            if (vha != humAlarm) { humBlock.setPulsing(vha); humAlarm = vha }

            handler.postDelayed(this, 200)
        }
    }

    private fun fmtHum(x10: Int): String {
        val n = if (settings.decimalsHum) "${x10 / 10}.${x10 % 10}" else "${(x10 + 5) / 10}"
        return "$n%"
    }
    private fun fmtPre(x10: Int): String {
        val mag = if (x10 < 0) -x10 else x10
        val sign = if (x10 < 0) "-" else "+"
        return if (settings.decimalsPre) "$sign${mag / 10}.${mag % 10}" else "$sign${(mag + 5) / 10}"
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
