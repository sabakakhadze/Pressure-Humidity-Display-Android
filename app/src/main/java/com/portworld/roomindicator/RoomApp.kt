package com.portworld.roomindicator

import android.app.Application

/**
 * Process-wide singletons: the Modbus slave (started once and kept running),
 * the settings store, and the panel API. Applies stored brightness and static
 * IP at boot, mirroring the ESP32 startup.
 */
class RoomApp : Application() {

    lateinit var store: SettingsStore
        private set
    lateinit var adw: AdwApi
        private set
    lateinit var modbus: ModbusTcpSlave
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        store = SettingsStore(this)
        adw = AdwApi(this)

        val s = store.load()
        adw.setBrightnessPercent(s.brightness)
        // Re-apply the static IP only if the panel isn't already on it;
        // re-applying the same config would bounce the link at every start.
        if (s.staticIp && s.ip.isNotEmpty() && adw.getDeviceIp() != s.ip) {
            adw.setStaticIp(s.ip, s.mask, s.gateway, s.dns, s.dns2)
        }

        modbus = ModbusTcpSlave(unitId = 1, port = 502)
        modbus.start()
    }

    companion object {
        lateinit var instance: RoomApp
            private set
    }
}
