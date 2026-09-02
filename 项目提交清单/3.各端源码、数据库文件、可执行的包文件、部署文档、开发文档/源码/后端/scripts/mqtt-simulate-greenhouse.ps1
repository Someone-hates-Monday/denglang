# 光棚 MQTT 硬件路径模拟（command ACK + 可选 telemetry）
# 用法:
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate-greenhouse.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate-greenhouse.ps1 -Stop

param(
    [int]$IntervalSec = 4,
    [switch]$Stop
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$SimName = "streetlight-gh-hw-sim"
Set-Location $Root

if ($Stop) {
    $existing = docker ps -aq -f "name=^/${SimName}$" 2>$null
    if ($existing) { docker rm -f $SimName | Out-Null }
    docker compose --profile gh-hw-sim stop gh-hw-sim 2>$null | Out-Null
    Write-Host "Greenhouse hardware simulator stopped."
    exit 0
}

$existing = docker ps -aq -f "name=^/${SimName}$" 2>$null
if ($existing) {
    Write-Host "Stopping existing $SimName ..."
    docker rm -f $SimName | Out-Null
}

Write-Host "Starting gh-hw-sim via docker compose (interval hint: ${IntervalSec}s)..."
docker compose --profile gh-hw-sim up -d gh-hw-sim
Start-Sleep -Seconds 2
$running = docker ps -q -f "name=^/${SimName}$"
if (-not $running) {
    Write-Host "Container not running; logs:" -ForegroundColor Red
    docker logs $SimName 2>&1
    throw "Greenhouse hardware simulator failed to start"
}
Write-Host "OK. Tail: docker logs -f $SimName"
docker logs --tail 25 $SimName
