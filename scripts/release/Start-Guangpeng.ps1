# 智慧光棚一键启动（发布包内运行）
# 依赖：Docker Desktop、JDK 21、可选 Node（用于 npx serve）
$ErrorActionPreference = "Stop"
# 本脚本与 jar 同目录（发布包根）
$PackRoot = $PSScriptRoot
if (-not (Test-Path (Join-Path $PackRoot "zhihui-guangpeng.jar"))) {
  throw "请在发布包根目录运行 Start-Guangpeng.ps1（需与 zhihui-guangpeng.jar 同级）"
}

$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
  [System.Environment]::GetEnvironmentVariable("Path", "User")

function Require-Cmd($name, $hint) {
  if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
    throw "未找到 $name。$hint"
  }
}

Require-Cmd docker "请安装并启动 Docker Desktop。"
Require-Cmd java "请安装 JDK 21 并加入 PATH。"

$prev = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javaLine = (& java -version 2>&1 | Select-Object -First 1) | Out-String
$ErrorActionPreference = $prev
if ($javaLine -notmatch "21\.") {
  Write-Warning "当前 Java：$javaLine（建议 JDK 21）"
}

$Jar = Join-Path $PackRoot "zhihui-guangpeng.jar"
$WebDist = Join-Path $PackRoot "web-dist"
$Compose = Join-Path $PackRoot "infra\docker-compose.yml"
$ConfigDir = Join-Path $PackRoot "config"
$Secret = Join-Path $ConfigDir "application-secret.yml"
$SecretExample = Join-Path $ConfigDir "application-secret.example.yml"
$PidDir = Join-Path $PackRoot ".run"
New-Item -ItemType Directory -Force -Path $PidDir | Out-Null

if (-not (Test-Path $Jar)) { throw "缺少 $Jar" }
if (-not (Test-Path $WebDist)) { throw "缺少 $WebDist" }
if (-not (Test-Path $Compose)) { throw "缺少 $Compose" }

if (-not (Test-Path $Secret)) {
  Copy-Item $SecretExample $Secret
  Write-Host "已生成 config\application-secret.yml（默认密码 123456，可按需修改）"
}

Write-Host "==> 启动 PostgreSQL + EMQX..."
Push-Location (Join-Path $PackRoot "infra")
$prevEa = $ErrorActionPreference
$ErrorActionPreference = "Continue"
docker compose -f docker-compose.yml up -d postgres emqx 2>&1 | Out-Null
$composeExit = $LASTEXITCODE
$ErrorActionPreference = $prevEa
if ($composeExit -ne 0) {
  $pgUp = docker ps --filter "name=streetlight-pg" --filter "health=healthy" --format "{{.Names}}"
  $emqxUp = docker ps --filter "name=streetlight-emqx" --format "{{.Names}}"
  if (-not ($pgUp -and $emqxUp)) { throw "Docker 启动失败，请检查 docker compose 输出。" }
  Write-Host "    容器已在运行，继续..."
}
Pop-Location

Write-Host "==> 等待数据库就绪..."
$ok = $false
for ($i = 0; $i -lt 30; $i++) {
  $name = docker ps --filter "name=streetlight-pg" --filter "health=healthy" --format "{{.Names}}"
  if ($name) { $ok = $true; break }
  Start-Sleep -Seconds 2
}
if (-not $ok) { throw "streetlight-pg 未就绪，请检查 Docker。" }

$Init = Join-Path $PackRoot "tools\init-db.ps1"
$forceInit = $env:GUANGPENG_FORCE_INIT -eq "1"
$needInit = $forceInit
if (-not $needInit) {
  $chk = docker exec streetlight-pg psql -U postgres -d smart-street-light -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE 'gh_%'" 2>$null
  if (-not $chk -or $chk.Trim() -eq "" -or $chk.Trim() -eq "0") {
    $needInit = $true
  }
}
if ($needInit) {
  Write-Host "==> 初始化数据库（首次或 FORCE）..."
  powershell -ExecutionPolicy Bypass -File $Init -PackRoot $PackRoot
} else {
  Write-Host "==> 数据库已有 gh_* 表，跳过初始化（强制重建设 GUANGPENG_FORCE_INIT=1）"
}

Write-Host "==> 启动后端 http://localhost:8080 ..."
$backendOut = Join-Path $PidDir "backend.log"
$backendErr = Join-Path $PidDir "backend.err"
$backend = Start-Process -FilePath "java" -ArgumentList @(
  "-jar", $Jar,
  "--spring.profiles.active=local,secret",
  "--spring.config.additional-location=optional:file:$($ConfigDir.Replace('\','/'))/"
) -PassThru -WindowStyle Hidden -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr
$backend.Id | Set-Content (Join-Path $PidDir "backend.pid")

$apiOk = $false
for ($i = 0; $i -lt 40; $i++) {
  Start-Sleep -Seconds 2
  try {
    $r = Invoke-RestMethod http://localhost:8080/users/login -Method POST -ContentType "application/json" `
      -Body '{"username":"admin","password":"admin123"}' -TimeoutSec 3
    if ($r.code -eq 200) { $apiOk = $true; break }
  } catch { }
}
if (-not $apiOk) {
  Write-Warning "后端尚未通过登录探测，请查看 .run\backend.log"
} else {
  Write-Host "    后端登录探测 OK"
}

Write-Host "==> 启动前端静态站 http://localhost:4173 ..."
Require-Cmd npm "请安装 Node.js（用于 npx serve）。"
$nodeDir = Split-Path (Get-Command node -ErrorAction Stop).Source
$npxExe = Join-Path $nodeDir "npx.cmd"
if (-not (Test-Path $npxExe)) { throw "未找到 npx.cmd，请确认 Node.js 已安装。" }
$frontOut = Join-Path $PidDir "frontend.log"
$frontErr = Join-Path $PidDir "frontend.err"
$front = Start-Process -FilePath $npxExe -ArgumentList @(
  "--yes", "serve", "-s", $WebDist, "-l", "4173"
) -PassThru -WindowStyle Hidden -RedirectStandardOutput $frontOut -RedirectStandardError $frontErr
$front.Id | Set-Content (Join-Path $PidDir "frontend.pid")

Write-Host ""
Write-Host "========================================"
Write-Host " 智慧光棚已启动"
Write-Host " 前端: http://localhost:4173"
Write-Host " 后端: http://localhost:8080"
Write-Host " 账号: admin / admin123"
Write-Host " 停止: .\Stop-Guangpeng.ps1"
Write-Host "========================================"
Start-Process "http://localhost:4173"
