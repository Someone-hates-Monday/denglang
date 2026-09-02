# 清理本机与本项目无关的 Docker 资源，并拉起光棚所需的 PG + EMQX
# 用法: powershell -ExecutionPolicy Bypass -File scripts\docker-cleanup.ps1
# 可选: -AlsoImages  删除 nexent/supabase/miniob 等无关镜像
#       -WithLampFleet  同时拉起路灯 MQTT fleet（默认关；光棚不需要）

param(
    [switch]$AlsoImages,
    [switch]$WithLampFleet
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

Write-Host "== Stop/remove unrelated containers =="
$junkNames = @(
    "nexent-data-process", "nexent-mcp", "nexent-northbound", "nexent-runtime",
    "nexent-config", "nexent-web", "nexent-minio", "nexent-postgresql",
    "nexent-redis", "nexent-elasticsearch",
    "supabase-auth-mini", "supabase-kong-mini", "supabase-db-mini",
    "ems-opengauss",
    "pg17-vector", "emqx"   # 旧 docker.sh 散装名（若存在）
)
foreach ($n in $junkNames) {
    $id = docker ps -aq -f "name=^/${n}$" 2>$null
    if ($id) {
        Write-Host "  rm $n"
        docker rm -f $n 2>$null | Out-Null
    }
}

# 旧 ad-hoc fleet：先摘掉，改由 compose profile 管理（避免双实例）
$oldFleet = docker ps -aq -f "name=^/streetlight-fleet-sim$" 2>$null
if ($oldFleet) {
    Write-Host "  rm streetlight-fleet-sim (will recreate via compose if requested)"
    docker rm -f streetlight-fleet-sim 2>$null | Out-Null
}

Write-Host "== Remove unused networks =="
docker network rm nexent_nexent 2>$null | Out-Null

Write-Host "== Prune dangling volumes (keeps streetlight_pg_data) =="
docker volume prune -f | Out-Null
# 具名但已无容器引用的 nexent 卷
docker volume rm nexent_db-config 2>$null | Out-Null

if ($AlsoImages) {
    Write-Host "== Remove unrelated images =="
    $imgPatterns = @(
        "ccr.ccs.tencentyun.com/nexent-hub/*",
        "docker.m.daocloud.io/supabase/*",
        "docker.1ms.run/supabase/*",
        "docker.m.daocloud.io/kong",
        "docker.m.daocloud.io/postgres:15-alpine",
        "docker.m.daocloud.io/redis",
        "elastic.m.daocloud.io/elasticsearch/*",
        "docker.elastic.co/elasticsearch/*",
        "quay.io/minio/minio",
        "quay.m.daocloud.io/minio/minio",
        "enmotech/opengauss-lite",
        "miniob-course"
    )
    docker images --format "{{.Repository}}:{{.Tag}}" | ForEach-Object {
        $ref = $_
        foreach ($pat in $imgPatterns) {
            if ($ref -like $pat -or $ref -like ($pat -replace '/\*', '/*')) {
                Write-Host "  rmi $ref"
                docker rmi -f $ref 2>$null | Out-Null
                break
            }
        }
    }
    # 再清悬空层
    docker image prune -f | Out-Null
}

Write-Host "== Bring up project stack (PG + EMQX) =="
if ($WithLampFleet) {
    docker compose --profile lamp-fleet up -d
} else {
    docker compose up -d
}

Start-Sleep -Seconds 4
Write-Host ""
Write-Host "Running:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" --filter "name=streetlight-"
Write-Host ""
Write-Host "Core ready: PG :5433  EMQX :1883  Dashboard :18083"
Write-Host "Greenhouse sim = Spring Boot (greenhouse.sim), not a Docker service."
if (-not $WithLampFleet) {
    Write-Host "Lamp fleet off. Streetlight map/devices may show OFFLINE until:"
    Write-Host "  docker compose --profile lamp-fleet up -d"
    Write-Host "  or: scripts\mqtt-simulate-fleet.ps1"
}
