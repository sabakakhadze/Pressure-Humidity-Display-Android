package com.portworld.roomindicator

import android.content.Context

object DisplayMode {
    const val BOTH = 0
    const val HUMIDITY = 1
    const val PRESSURE = 2
}

/** Persisted device settings; defaults match the ESP32 build. */
data class Settings(
    var pin: String = "5674",
    var staticIp: Boolean = false,
    var ip: String = "",
    var mask: String = "",
    var gateway: String = "",
    var dns: String = "8.8.8.8",
    var dns2: String = "8.8.4.4",
    var humLoX10: Int = 300,     // 30.0 %RH
    var humHiX10: Int = 600,     // 60.0 %RH
    var preLoX10: Int = -150,    // -15.0 Pa
    var preHiX10: Int = 150,     // +15.0 Pa
    var timeoutS: Int = 10,
    var displayMode: Int = DisplayMode.BOTH,
    var brightness: Int = 100,   // percent, mapped to 0..255 for the panel API
    var decimalsHum: Boolean = true, // one decimal place on humidity
    var decimalsPre: Boolean = true, // one decimal place on pressure
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("room_cfg", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            pin = sp.getString("pin", d.pin) ?: d.pin,
            staticIp = sp.getBoolean("static", d.staticIp),
            ip = sp.getString("ip", d.ip) ?: d.ip,
            mask = sp.getString("mask", d.mask) ?: d.mask,
            gateway = sp.getString("gw", d.gateway) ?: d.gateway,
            dns = (sp.getString("dns", d.dns) ?: d.dns).ifBlank { d.dns },
            dns2 = (sp.getString("dns2", d.dns2) ?: d.dns2).ifBlank { d.dns2 },
            humLoX10 = sp.getInt("hlo", d.humLoX10),
            humHiX10 = sp.getInt("hhi", d.humHiX10),
            preLoX10 = sp.getInt("plo", d.preLoX10),
            preHiX10 = sp.getInt("phi", d.preHiX10),
            timeoutS = sp.getInt("tmo", d.timeoutS),
            displayMode = sp.getInt("disp", d.displayMode),
            brightness = sp.getInt("bri", d.brightness),
            decimalsHum = sp.getBoolean("decH", d.decimalsHum),
            decimalsPre = sp.getBoolean("decP", d.decimalsPre),
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("pin", s.pin)
            .putBoolean("static", s.staticIp)
            .putString("ip", s.ip)
            .putString("mask", s.mask)
            .putString("gw", s.gateway)
            .putString("dns", s.dns)
            .putString("dns2", s.dns2)
            .putInt("hlo", s.humLoX10)
            .putInt("hhi", s.humHiX10)
            .putInt("plo", s.preLoX10)
            .putInt("phi", s.preHiX10)
            .putInt("tmo", s.timeoutS)
            .putInt("disp", s.displayMode)
            .putInt("bri", s.brightness)
            .putBoolean("decH", s.decimalsHum)
            .putBoolean("decP", s.decimalsPre)
            .apply()
    }
}
