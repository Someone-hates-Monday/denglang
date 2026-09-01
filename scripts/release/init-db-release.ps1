# 发布包内数据库初始化
param(
  [Parameter(Mandatory = $true)][string]$PackRoot
)
$ErrorActionPreference = "Stop"
$Schema = Join-Path $PackRoot "infra\sql\schema.sql"
$TestData = Join-Path $PackRoot "infra\sql\test-data.sql"
$Migrations = Join-Path $PackRoot "infra\sql\migrations"

if (-not (docker ps --filter name=streetlight-pg --format "{{.Names}}")) {
  throw "streetlight-pg 未运行"
}

Write-Host "Recreating database smart-street-light..."
@'
DROP DATABASE IF EXISTS "smart-street-light";
CREATE DATABASE "smart-street-light";
'@ | docker exec -i streetlight-pg psql -U postgres

docker cp $Schema streetlight-pg:/tmp/schema.sql
docker cp $TestData streetlight-pg:/tmp/test-data.sql

docker exec streetlight-pg sh -c "grep -v '^CREATE DATABASE' /tmp/schema.sql | psql -U postgres -d smart-street-light -v ON_ERROR_STOP=1 -f -"
docker exec streetlight-pg psql -U postgres -d smart-street-light -v ON_ERROR_STOP=1 -f /tmp/test-data.sql

Get-ChildItem $Migrations -Filter "*.sql" -ErrorAction SilentlyContinue | Sort-Object Name | ForEach-Object {
  Write-Host "Applying $($_.Name)..."
  docker cp $_.FullName "streetlight-pg:/tmp/$($_.Name)"
  docker exec streetlight-pg psql -U postgres -d smart-street-light -v ON_ERROR_STOP=1 -f "/tmp/$($_.Name)"
}

Write-Host "DB ready. Login: admin / admin123"
