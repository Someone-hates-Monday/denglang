# 多路模拟路灯持续上报（MQTT → EMQX → 后端）
# 推荐：docker compose --profile lamp-fleet up -d
# 本脚本：临时拉起 / -Stop 停止（转调 compose profile）
#
# 用法:
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate-fleet.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate-fleet.ps1 -Stop

param(
    [int]$IntervalSec = 8,
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$SimName = "streetlight-fleet-sim"
Set-Location $Root

if ($Stop) {
    $existing = docker ps -aq -f "name=^/${SimName}$" 2>$null
    if ($existing) { docker rm -f $SimName | Out-Null }
    docker compose --profile lamp-fleet stop lamp-fleet 2>$null | Out-Null
    Write-Host "Fleet simulator stopped."
    exit 0
}

$existing = docker ps -aq -f "name=^/${SimName}$" 2>$null
if ($existing) {
    Write-Host "Stopping existing $SimName ..."
    docker rm -f $SimName | Out-Null
}

Write-Host "Starting lamp-fleet via docker compose profile (interval hint: ${IntervalSec}s)..."
docker compose --profile lamp-fleet up -d lamp-fleet
Start-Sleep -Seconds 2
$running = docker ps -q -f "name=^/${SimName}$"
if (-not $running) {
    Write-Host "Container not running; logs:" -ForegroundColor Red
    docker logs $SimName 2>&1
    throw "Fleet simulator failed to start"
}
Write-Host "OK. Tail logs: docker logs -f $SimName"
docker logs --tail 20 $SimName
