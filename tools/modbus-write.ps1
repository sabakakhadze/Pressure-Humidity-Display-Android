<#
.SYNOPSIS
  Tiny Modbus TCP master to test the room-indicator app's slave.

  Writes the three holding registers one at a time with FC06 (write-single),
  exactly like Schneider Building Operation does (it cannot do an atomic FC16
  of all three). Values are the human units; the script applies the x10 scaling
  the app expects:
      reg 0 = humidity    x10   (0..100.0 %RH)
      reg 1 = pressure    x10   signed (-xxxx .. +xxxx Pa)
      reg 2 = counter     raw integer

.EXAMPLE
  # One-shot write to an emulator (after: adb forward tcp:1502 tcp:1502)
  .\modbus-write.ps1 -Humidity 45.2 -Pressure -12.5 -Counter 1

.EXAMPLE
  # Keep the panel "online" by refreshing every 2 s, 60 times (counter ramps)
  .\modbus-write.ps1 -Humidity 55 -Pressure 8 -Counter 1 -IntervalSec 2 -Repeat 60

.EXAMPLE
  # Real panel on the network
  .\modbus-write.ps1 -TargetHost 192.168.1.50 -Port 502 -Humidity 48 -Pressure 15
#>
param(
    [double]$Humidity = [double]::NaN,   # %RH   (omit to leave reg 0 untouched)
    [double]$Pressure = [double]::NaN,   # Pa    (omit to leave reg 1 untouched)
    [int]   $Counter  = -1,              # raw   (omit / <0 to leave reg 2 untouched)
    [string]$TargetHost = "127.0.0.1",
    [int]   $Port     = 1502,            # 1502 for emulator, 502 for the panel
    [int]   $Unit     = 1,
    [double]$IntervalSec = 0,            # >0 repeats; keeps data fresh (vs timeout -> "---")
    [int]   $Repeat   = 1
)

function Write-Reg {
    param($Stream, [int]$UnitId, [int]$Addr, [int]$Value)
    $v = $Value -band 0xFFFF
    $pdu = [byte[]]@(0x06, (($Addr -shr 8) -band 0xFF), ($Addr -band 0xFF),
                           (($v    -shr 8) -band 0xFF), ($v    -band 0xFF))
    $txn = Get-Random -Minimum 0 -Maximum 65535
    $len = 1 + $pdu.Length
    $mbap = [byte[]]@((($txn -shr 8) -band 0xFF), ($txn -band 0xFF), 0, 0,
                      (($len -shr 8) -band 0xFF), ($len -band 0xFF), $UnitId)
    $frame = $mbap + $pdu
    $Stream.Write($frame, 0, $frame.Length); $Stream.Flush()

    $buf = New-Object byte[] 260
    $n = $Stream.Read($buf, 0, $buf.Length)
    if ($n -lt 9) { return "reg $Addr -> no/short response" }
    $fc = $buf[7]
    if ($fc -band 0x80) { return "reg $Addr -> EXCEPTION code $($buf[8])" }
    return "reg $Addr = $Value  OK"
}

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($TargetHost, $Port)
} catch {
    Write-Host "Cannot connect to ${TargetHost}:${Port} - is the app running and (emulator) adb forward set?" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}
$stream = $client.GetStream()
$stream.ReadTimeout = 3000
Write-Host "Connected to ${TargetHost}:${Port} (unit $Unit)" -ForegroundColor Green

for ($i = 0; $i -lt $Repeat; $i++) {
    $msgs = @()
    if (-not [double]::IsNaN($Humidity)) {
        $msgs += (Write-Reg $stream $Unit 0 ([int][math]::Round($Humidity * 10)))
    }
    if (-not [double]::IsNaN($Pressure)) {
        $msgs += (Write-Reg $stream $Unit 1 ([int][math]::Round($Pressure * 10)))
    }
    if ($Counter -ge 0) {
        $msgs += (Write-Reg $stream $Unit 2 ($Counter + $i))
    }
    $stamp = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$stamp] $($msgs -join '  |  ')"
    if ($IntervalSec -gt 0 -and $i -lt ($Repeat - 1)) { Start-Sleep -Seconds $IntervalSec }
}

$stream.Close(); $client.Close()
Write-Host "Done." -ForegroundColor Green
