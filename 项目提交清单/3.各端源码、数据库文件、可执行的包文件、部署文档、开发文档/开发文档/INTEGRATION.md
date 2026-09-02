# 智慧光棚 · 对接需求

> 版本：`v1.0` · 2026-08-30  
> 契约真源优先：`contracts/mqtt.md` · `contracts/light-recipe.md` · `contracts/light-field-model.md`  
> 本文是**联调检查单 + HTTP 清单**，便于前后端/硬件并行。

---

## 1. 环境与账号

| 项 | 值 |
|----|-----|
| Web | http://localhost:5173 |
| API | http://localhost:8080 |
| PG | localhost:5433 / DB `smart-street-light` |
| EMQX | tcp://127.0.0.1:1883 · 控制台 :18083 |
| 登录 | `admin` / `admin123` · Header：`token` |
| 统一响应 | `{ "code": 200\|500, "errorMsg": "...", "data": ... }` |

启动步骤见 [IMPLEMENT.md](./IMPLEMENT.md)。

---

## 2. MQTT 对接（设备 ↔ 云）

前缀：`smart-greenhouse/`

| Topic | 方向 | 用途 |
|-------|------|------|
| `{sn}/telemetry` | 上行 | PAR 测点：必填 `ppfd` |
| `{sn}/status` | 上行 | 灯 `dimmingPercent` / 遮阳 `shadeOpenPercent` |
| `{sn}/command` | 下行 | `SET_DIMMING` / `SET_OPEN_PERCENT` |
| `{sn}/alarm` | 上行 | 可选 |

载荷字段以 [contracts/mqtt.md](./contracts/mqtt.md) 为准。  
演示期可不发 MQTT：后端 `greenhouse.sim` 直接写库并推 `/topic/greenhouse`。

**联调注意**

- 设备 SN 必须先在 `gh_devices` 落档，否则上行丢弃。  
- 发布端与订阅端同 broker 时注意回环；进程内仿真默认**不**外发 telemetry（`greenhouse.sim.publish-mqtt-telemetry=false`）。  
- 可选硬件路径模拟：`scripts/mqtt-simulate-greenhouse.ps1`（compose profile `gh-hw-sim`）订阅 `command` 回 `status`。  
- 光棚 Topic `smart-greenhouse/` 与历史 `smart-light/` 隔离，勿混用。

---

## 3. HTTP 对接（前端 ↔ 云）

鉴权：除登录注册外均带 `token`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/greenhouse/zones` | 分区列表 |
| GET | `/greenhouse/zones/{zoneId}/effective-light` | 有效光 + 网格 + 设备 + 配方 |
| PUT | `/greenhouse/zones/{zoneId}/recipe` | body: `{ "recipeId" }` |
| PUT | `/greenhouse/zones/{zoneId}/climate-profile` | body: `{ "profileId" }` |
| PUT | `/greenhouse/zones/{zoneId}/auto-control` | body: `{ "enabled": true }` |
| GET | `/greenhouse/recipes` | 启用配方 |
| GET | `/greenhouse/devices?zoneId=` | 棚内设备 |
| POST | `/greenhouse/lamps/{sn}/dimming` | `{ "dimmingPercent": 0-100 }` |
| POST | `/greenhouse/shades/{sn}/open-percent` | `{ "shadeOpenPercent": 0-100 }` |
| GET | `/greenhouse/work-orders?status=` | 工单 |
| POST | `/greenhouse/work-orders/{id}/approve` | 批准（**不下发**） |
| POST | `/greenhouse/work-orders/{id}/reject` | 驳回 |
| POST | `/greenhouse/work-orders/{id}/claim` | 接单执行（下发并完成） |
| POST | `/greenhouse/work-orders/{id}/complete` | 完成（仅 `IN_PROGRESS`） |
| GET | `/greenhouse/control-logs?limit=&source=` | 控制日志（`gh_control_logs`） |
| GET | `/greenhouse/alarms?status=&limit=` | 光棚告警（`gh_alarms`） |
| PUT | `/greenhouse/alarms/{id}/resolve` | 消警 |
| GET | `/greenhouse/reports?type=&status=&limit=` | 报告列表 |
| POST | `/greenhouse/reports/daily-draft` | 生成/刷新当日 `DAILY_LIGHT` 草稿 |
| POST | `/greenhouse/reports/{id}/submit` | 提交草稿 |
| POST | `/greenhouse/reports/{id}/review` | 批阅 `{ note, approve }` |
| POST | `/greenhouse/sim/tick` | 手动推进仿真（调试） |
| POST | `/greenhouse/agent/chat` | 顾问智能体问答（只读；见 [AGENT.md](./AGENT.md)） |

前端 Vite 已代理 `/greenhouse` → `:8080`。

### agent/chat

```
POST /greenhouse/agent/chat
Header: token
Body: { "sessionId"?: string, "message": string, "zoneId"?: string }
Resp.data: {
  sessionId: string,
  reply: string,
  toolsUsed: string[],
  citations: [{ title, source }],
  snapshot?: object,
  mode: "llm" | "template" | "knowledge"
}
```

只读：不下发调光/遮阳/工单状态变更。

### effective-light 关键字段（前端依赖）

`zoneId, name, recipeId, climateProfileId, minuteOfDay, outdoorParPpfd, sunInPpfd, effectivePpfd, dliSoFar, shadeOpenPercent, autoControl, nx, ny, lengthM, widthM, grid[{x,y,ppfd}], devices[], recipe`

---

## 4. 实时推送

| 通道 | 用途 |
|------|------|
| STOMP `/topic/greenhouse` | 仿真/控制后刷新总览与冠层 |
| STOMP `/topic/alarms` · `/topic/greenhouse-alarms` | 光棚告警推送（前端 toast / 日志页刷新） |

前端：`stores/realtime.ts` → `greenhouseTick`。

---

## 5. 数据与配方对接

- 配方 ID 与硬限：见 [contracts/light-recipe.md](./contracts/light-recipe.md)  
- 重庆日型 ID：`cq-winter-fog` | `cq-winter-clear` | `cq-summer-noon` | `cq-overcast`  
- 种子分区：`ZONE-A`（石斛）、`ZONE-B`（金线莲/切换）  
- 迁移：`sql/migrations/V20260830_greenhouse.sql` · 脚本 `scripts/apply-greenhouse.ps1`

---

## 6. 联调验收（对接完成定义）

1. 登录成功，默认进入冠层光场。  
2. 总览可见分区有效 PPFD，且随仿真 tick 变化。  
3. 切换 `cq-summer-noon` / `cq-winter-fog`，遮阳或补光有可见动作或工单。  
4. 待审批工单可批准，灯/遮阳状态更新。  
5. MQTT（可选）：用 mosquitto_pub 发一条 telemetry，测点 `last_ppfd` 更新。  
6. 控制日志页可见 `gh_control_logs`；告警 Tab 可见欠/过光或离线；可消警。  
7. （可选）`mqtt-simulate-greenhouse.ps1` 启动后，非 `sim.*` 适配器指令可走 PENDING→SUCCESS。 

失败时优先查：迁移是否执行、`greenhouse.sim.enabled`、EMQX 是否 healthy、浏览器 Network 的 `code`。

---

## 7. 变更流程

1. 改契约文档 → 2. 通知前后端/硬件 → 3. 改代码 → 4. 更新本文「验收」若有新条目 → 5. 提交说明见 [SUBMIT.md](./SUBMIT.md)。
