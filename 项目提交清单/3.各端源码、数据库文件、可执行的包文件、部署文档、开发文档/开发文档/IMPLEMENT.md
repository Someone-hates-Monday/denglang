# 智慧光棚 · 本地实施说明

## 一次启动

1. `docker compose up -d`（PG `:5433` + EMQX `:1883`）  
   清理：`.\scripts\docker-cleanup.ps1 -AlsoImages`
2. 库已存在：`.\scripts\apply-greenhouse.ps1`（含 `V20260830b_layout_cq_demo_bay_v1`）；全新库：`.\scripts\init-db.ps1`
3. 后端：`local,secret`（Docker Maven 打 jar 或 IDE）
4. 前端：`cd ../web && npm run dev` → 登录后默认 **冠层光场** `/greenhouse`

## 已实现

| 能力 | 说明 |
|------|------|
| 产品壳层 | 品牌「智慧光棚」；无路灯地图/阈值页 |
| 棚体真源 | `cq-demo-bay-v1`：16×7 m、脊高 3.8、网格 **32×14**、`bedSunFactor` 南北梯度 |
| 表结构 | `gh_*` + 石斛/草莓/金线莲配方；ZONE-A **4 灯+3 PAR**、ZONE-B **3 灯+3 PAR**（坐标对齐 JSON） |
| 仿真 | `day-compress-sec: 120`；`interval-ms: 250`；**浮点**仿真分钟连续推进；series 密采样；`POST /greenhouse/sim/reset-day` |
| 光场 | 自然光×透光×遮阳×床位梯度 + 灯具衰减；`naturalPpfd` / `controlled` / `series` |
| 规则 | 硬限补/遮、目标带微调、大开度→工单 |
| MQTT | `smart-greenhouse/+/telemetry\|status`；下行 command |
| API | `/greenhouse/**` · `effective-light` 含 geometryId / 脊高 / series |
| 前端 | Three.js 三床+真灯位+南向采光；密采样曲线（可前端插值）；文案标明坐标系 |

登录：`admin` / `admin123`。需求对照见仓库根 [HANDOFF.md](../../HANDOFF.md)。布局真源：[GREENHOUSE-LAYOUT.md](./GREENHOUSE-LAYOUT.md)。
