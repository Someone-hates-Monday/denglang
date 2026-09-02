# MQTT 单次模拟 — 向 EMQX 发一条光照 + status
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate.ps1 -DeviceSn SN-RM-002 -Intensity 25
# 持续多设备见: mqtt-simulate-fleet.ps1

param(
    [string]$DeviceSn = "SN-RM-001",
    [double]$Intensity = 25.0,
    [string]$EmqxContainer = "streetlight-emqx",
    [int]$BrokerPort = 1883
)

$ErrorActionPreference = "Stop"
$ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$status = if ($Intensity -lt 30) { "ON" } elseif ($Intensity -gt 80) { "OFF" } else { "ON" }

$lightPayload = (@{
    deviceSn       = $DeviceSn
    lightIntensity = $Intensity
    timestamp      = $ts
} | ConvertTo-Json -Compress)

$statusPayload = (@{
    deviceSn  = $DeviceSn
    status    = $status
    timestamp = $ts
} | ConvertTo-Json -Compress)

function Publish-Mqtt([string]$Topic, [string]$Payload) {
    $env:MQTT_PAYLOAD = $Payload
    docker run --rm --network "container:$EmqxContainer" -e MQTT_PAYLOAD eclipse-mosquitto:2 `
        sh -c 'mosquitto_pub -h 127.0.0.1 -p 1883 -t '"$Topic"' -m "$MQTT_PAYLOAD"'
}

Write-Host "Publish light  smart-light/$DeviceSn/light  lux=$Intensity"
Publish-Mqtt "smart-light/$DeviceSn/light" $lightPayload
Write-Host "Publish status smart-light/$DeviceSn/status status=$status"
Publish-Mqtt "smart-light/$DeviceSn/status" $statusPayload
Write-Host "Done. Continuous fleet: scripts\mqtt-simulate-fleet.ps1"
