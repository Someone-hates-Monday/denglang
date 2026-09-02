# 停止发布包拉起的前后端进程（不关 Docker）
$ErrorActionPreference = "Continue"
$PackRoot = $PSScriptRoot
$PidDir = Join-Path $PackRoot ".run"

function Stop-PidFile($name) {
  $f = Join-Path $PidDir $name
  if (-not (Test-Path $f)) { return }
  $id = (Get-Content $f -ErrorAction SilentlyContinue | Select-Object -First 1)
  if ($id) {
    try {
      Stop-Process -Id ([int]$id) -Force -ErrorAction SilentlyContinue
      Write-Host "已停止 PID $id ($name)"
    } catch {}
  }
  Remove-Item $f -Force -ErrorAction SilentlyContinue
}

Stop-PidFile "backend.pid"
Stop-PidFile "frontend.pid"

# 兜底：释放常见端口上的残留
foreach ($port in 8080, 4173) {
  Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object {
      try { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } catch {}
    }
}

Write-Host "已停止本机 jar / 静态站。Docker（PG/EMQX/模拟器）仍在运行；若要全关："
Write-Host "  cd infra; docker compose --profile gh-hw-sim down"
