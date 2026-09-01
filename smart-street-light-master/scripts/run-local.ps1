# Local stack: Docker (PG + EMQX) + build + run Spring Boot jar
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java not found. Install JDK 21."
}
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javaLine = (java -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $prevEap
if ($javaLine -notmatch "21\.") {
    Write-Warning "Current Java: $javaLine (JDK 21 recommended)"
}

Write-Host "Starting Docker services..."
docker compose up -d
Start-Sleep -Seconds 6
powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "init-db.ps1")

# 路灯模拟灯（可选）：compose profile；光棚不依赖
# docker compose --profile lamp-fleet up -d

$profiles = "local,secret"
Write-Host "Building with Docker Maven..."
docker run --rm `
    -v "${Root}:/app" `
    -w /app `
    maven:3.9-eclipse-temurin-21 `
    mvn package -DskipTests -q

Write-Host ("Starting http://localhost:8080 profiles=" + $profiles)
java -jar target/zhihui-guangpeng.jar --spring.profiles.active=$profiles
