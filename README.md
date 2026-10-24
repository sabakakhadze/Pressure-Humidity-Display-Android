# Humidity / Room Pressure Display — Android

Android port of the ESP32 room indicator: a Modbus TCP slave that the BMS (SBO)
writes humidity / differential pressure into, shown on a Portworld/Sunworld
Android panel (PCP5 family: 55P / 08P / 08M / 10P), portrait orientation.

## What it does
- **Modbus TCP slave** on port 502, unit ID 1, 3 holding registers
  (0 = humidity ×10, 1 = differential pressure ×10 signed, 2 = counter).
  FC03 read, FC06 / FC16 write, humidity validated ≤ 1000, per-register
  staleness → `---` when a register isn't refreshed within the timeout.
  Falls back to port 1502 if 502 can't be bound (e.g. emulator).
- **Monitor screen**: top third is the status bar (green shield ✓ Normal,
  red warning triangle for Pressure/Humidity high|low), readings below,
  full-screen gray **Offline** cover, both / humidity-only / pressure-only modes.
- **Settings** (tap the top-left corner 5 times, PIN `5674` on a keypad):
  display mode, brightness, decimals per sensor, static IP / mask / gateway /
  DNS, alarm limits, data timeout, PIN change, reboot panel; shows current IP,
  build and Modbus port. Auto-closes after 60 s. Network is only reapplied
  when a network field changed (so Modbus clients aren't dropped on Save).

## Panel SDK
`app/libs/adw_api.jar` is the panel's `android.app.ADWApiManager` system API
(`compileOnly`, provided by the ROM). Docs: `API-PCP5.pdf` from Portworld.
Used for brightness, static IP (`SetDeviceEthernetMode`), reboot, current IP
and hiding the nav bar. Off-panel the calls are guarded and no-op.

Static IP and reboot only work when the app is a **privileged system app**,
and the ROM's handler crashes on a blank DNS — the app always sends DNS values.

## Build
JDK 17 + Android SDK. Open in Android Studio and Run, or:
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Setting up a new panel (once per panel)
Connect the panel by USB, build the APK, then run:
```
powershell -File panel\setup-panel.ps1
```
It disables dm-verity, installs the app into `/system/priv-app`, installs
`panel/roomindicator.rc` (lets the app bind port 502 at every boot), and makes
the app the Home launcher so it starts on boot. Then set the static IP in the
app's Settings.

## Updating an installed panel
`adb install -r -t app/build/outputs/apk/debug/app-debug.apk` (or Run from
Android Studio). It installs over the system copy and stays privileged as long
as it's signed with the same key.

## Undo / maintenance
- Get the stock launcher back:
  `adb shell cmd role remove-role-holder --user 0 android.app.role.HOME com.portworld.roomindicator`
- Test without SBO: `tools/modbus-write.ps1 -TargetHost <panel-ip> -Port 502 -Humidity 45.2 -Pressure -12.5`
