# 仅应用智能光棚迁移（需 streetlight-pg 已运行）
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$MigDir = Join-Path $Root "sql\migrations"
$Files = @(
  "V20260830_greenhouse.sql",
  "V20260830b_layout_cq_demo_bay_v1.sql",
  "V20260830c_layout_v1_1_heights.sql",
  "V20260830d_layout_v1_2_per_bed.sql",
  "V20260830e_sensor_under_lamp.sql"
)

if (-not (docker ps --filter name=streetlight-pg --format "{{.Names}}")) {
  Write-Error "streetlight-pg 未运行。请先: docker compose up -d"
}

foreach ($name in $Files) {
  $Sql = Join-Path $MigDir $name
  if (-not (Test-Path $Sql)) {
    Write-Warning "跳过缺失: $name"
    continue
  }
  Write-Host "Applying $name ..."
  docker cp $Sql "streetlight-pg:/tmp/$name"
  docker exec streetlight-pg psql -U postgres -d smart-street-light -v ON_ERROR_STOP=1 -f "/tmp/$name"
}
Write-Host "Done. Greenhouse tables + cq-demo-bay-v1 layout ready."
