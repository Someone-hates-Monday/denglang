# 允许热点上的 BearPi 访问本机 EMQX（TCP 1883）
# 必须用「以管理员身份运行」的 PowerShell：
#   powershell -ExecutionPolicy Bypass -File scripts\allow-mqtt-hotspot.ps1

$ErrorActionPreference = 'Stop'
$ruleName = 'Streetlight EMQX 1883'

$existing = netsh advfirewall firewall show rule name="$ruleName"
if ($LASTEXITCODE -eq 0) {
    Write-Host "Firewall rule already exists: $ruleName"
    exit 0
}

netsh advfirewall firewall add rule name="$ruleName" dir=in action=allow protocol=TCP localport=1883 profile=private,public
if ($LASTEXITCODE -ne 0) { throw 'Failed to add firewall rule. Run this script as Administrator.' }
Write-Host "Added firewall rule: $ruleName (TCP 1883 inbound on private+public)"
Write-Host "BearPi can now reach EMQX at this PC's hotspot IP, e.g. 172.20.10.4:1883"
