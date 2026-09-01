# 打包智慧光棚「一键发布包」到 release/zhihui-guangpeng-<ver>/
# 用法（仓库根目录）：
#   powershell -ExecutionPolicy Bypass -File scripts\release\pack-release.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\release\pack-release.ps1 -Version 0.1.0
param(
  [string]$Version = (Get-Date -Format 'yyyy.MM.dd')
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not (Test-Path (Join-Path $RepoRoot "web\package.json"))) {
  $RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
}
$Backend = Join-Path $RepoRoot "smart-street-light-master"
$Web = Join-Path $RepoRoot "web"
$OutName = "zhihui-guangpeng-$Version"
$OutDir = Join-Path $RepoRoot "release\$OutName"

Write-Host "==> Repo: $RepoRoot"
Write-Host "==> Out:  $OutDir"

if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "config") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "infra") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutDir "tools") | Out-Null

# 1) 前端：生产构建直连本机 API
Write-Host "==> Building frontend (http mode -> :8080)..."
Push-Location $Web
$env:VITE_API_MODE = "http"
$env:VITE_API_BASE = "http://localhost:8080"
$env:VITE_WS_BASE = "ws://localhost:8080"
npm run build
if ($LASTEXITCODE -ne 0) { throw "frontend build failed" }
Pop-Location
Copy-Item -Recurse (Join-Path $Web "dist") (Join-Path $OutDir "web-dist")

# 2) 后端 jar
Write-Host "==> Building backend jar..."
Push-Location $Backend
docker run --rm -v "${Backend}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "backend build failed" }
Pop-Location
Copy-Item (Join-Path $Backend "target\zhihui-guangpeng.jar") (Join-Path $OutDir "zhihui-guangpeng.jar")

# 3) 基础设施与库脚本
Copy-Item (Join-Path $Backend "docker-compose.yml") (Join-Path $OutDir "infra\docker-compose.yml")
Copy-Item -Recurse (Join-Path $Backend "sql") (Join-Path $OutDir "infra\sql")
# 去掉依赖主仓路径的 mqtt 脚本挂载问题：发布 compose 仅保留 PG+EMQX
$compose = Get-Content (Join-Path $OutDir "infra\docker-compose.yml") -Raw
# 保留完整 compose；一键脚本只用 up -d（默认服务）

# 4) 密钥模板
Copy-Item (Join-Path $Backend "src\main\resources\application-secret-example.yml") `
  (Join-Path $OutDir "config\application-secret.example.yml")

# 5) 工具脚本
Copy-Item (Join-Path $PSScriptRoot "Start-Guangpeng.ps1") (Join-Path $OutDir "Start-Guangpeng.ps1")
Copy-Item (Join-Path $PSScriptRoot "Stop-Guangpeng.ps1") (Join-Path $OutDir "Stop-Guangpeng.ps1")
Copy-Item (Join-Path $PSScriptRoot "init-db-release.ps1") (Join-Path $OutDir "tools\init-db.ps1")

# 6) README
$readmeSrc = Join-Path $PSScriptRoot "RELEASE-README.md"
$readme = (Get-Content $readmeSrc -Raw -Encoding UTF8) -replace '\{\{VERSION\}\}', $Version
Set-Content -Path (Join-Path $OutDir "README.md") -Value $readme -Encoding UTF8

# 7) zip
$ZipPath = Join-Path $RepoRoot "release\$OutName.zip"
if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path $OutDir -DestinationPath $ZipPath -Force

Set-Content -Path (Join-Path $RepoRoot "release\LATEST.txt") -Value $OutDir -Encoding UTF8
Write-Host "==> Done"
Write-Host "    Folder: $OutDir"
Write-Host "    Zip:    $ZipPath"
