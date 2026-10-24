<#
.SYNOPSIS
  One-time setup of a PCP5 panel (55P/08P/08M/10P) for the room indicator app.

  Makes the app a privileged system app (so static IP and Reboot work), installs
  the boot script that allows Modbus port 502, and sets the app as the Home
  launcher so it starts on boot.

  WARNING: this disables dm-verity on the panel and writes to /system. Only run
  it on panels you own. Reversible with `adb enable-verity` (but the app then
  loses system-app status and port 502).

.EXAMPLE
  .\setup-panel.ps1
.EXAMPLE
  .\setup-panel.ps1 -Apk ..\app\build\outputs\apk\release\app-release.apk -Serial ADPCP5E260410217
#>
param(
    [string]$Apk = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk",
    [string]$Serial = "",
    [string]$Adb = "adb"
)

$ErrorActionPreference = "Stop"
$pkg = "com.portworld.roomindicator"
$rc = "$PSScriptRoot\roomindicator.rc"
$s = if ($Serial) { @("-s", $Serial) } else { @() }

function A { & $Adb @s @args }
function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function WaitBoot {
    A wait-for-device
    for ($i = 0; $i -lt 100; $i++) {
        $b = ((A shell getprop sys.boot_completed 2>$null) -join "").Trim()
        if ($b -eq "1") { Start-Sleep 5; return }
        Start-Sleep 3
    }
    throw "Panel did not finish booting"
}
function Root { A root | Out-Null; Start-Sleep 2; A wait-for-device }

if (-not (Test-Path $Apk)) { throw "APK not found: $Apk (build it first)" }

Step "Disable verity"
Root
A disable-verity
A reboot; WaitBoot

Step "Set up overlay and remount /system (needs one reboot)"
Root
A remount
A reboot; WaitBoot
Root
A remount
$w = (A shell "touch /system/.w && echo WRITABLE && rm /system/.w") -join ""
if ($w -notmatch "WRITABLE") { throw "/system is not writable - stopping" }

Step "Install app as privileged system app"
A shell mkdir -p /system/priv-app/RoomIndicator
A push $Apk /system/priv-app/RoomIndicator/RoomIndicator.apk
A shell chmod 755 /system/priv-app/RoomIndicator
A shell chmod 644 /system/priv-app/RoomIndicator/RoomIndicator.apk

Step "Install port-502 boot script"
A push $rc /system/etc/init/roomindicator.rc
A shell chmod 644 /system/etc/init/roomindicator.rc

Step "Remove any user-installed copy so the system copy is used"
try { A uninstall $pkg } catch { Write-Host "(no user copy)" }
A reboot; WaitBoot

Step "Set as Home launcher"
A shell cmd role add-role-holder --user 0 android.app.role.HOME $pkg
A reboot; WaitBoot

Step "Verify"
A shell "dumpsys package $pkg | grep -E 'codePath|privateFlags' | head -2"
A shell "cat /proc/sys/net/ipv4/ip_unprivileged_port_start"
A shell "dumpsys activity activities | grep topResumedActivity | head -1"
Write-Host "`nDone. Set the static IP from the app's Settings (PIN 5674)." -ForegroundColor Green
