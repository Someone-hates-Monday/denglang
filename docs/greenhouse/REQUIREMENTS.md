# 智慧光棚 · 需求清单

> 版本：`v1.0` · 2026-08-31
> 对应「项目提交清单」第 1 项「需求清单」；内容提炼自 [PRD-MVP.md](./PRD-MVP.md) 与契约文档。
> 配套：[原型图（思维导图）](./assets/prototype-mindmap.png) · [功能架构图](./assets/functional-architecture.png) · [技术架构图](./assets/technical-architecture.png)

---

## 1. 项目定位

| 项 | 内容 |
|----|------|
| 产品名 | **智慧光棚** |
| 一句话 | 面向设施栽培的**光环境闭环管控平台**：测光 → 配方比对 → 补光/遮阳联动 → 农艺审批工单可审计 |
| 主用户 | 场长、农艺师、种植员、设备运维（扩展：学员、系统管理员） |
| 主场景 | 重庆近郊单跨拱棚示范单元（16×7 m），主作物铁皮石斛 |
| 辅场景 | 设施草莓（重庆冬寡照产量叙事）、台湾金线莲（耐阴配方切换演示） |
| 非目标 | 全科水肥 SaaS、多园区计费、Agent 直接控灯 |

**差异化亮点**：空间光场 3D 热力 · 重庆日型仿真（一天压缩 2 分钟）· 分床调光 · 角色分权（审批≠执行）。

---

## 2. 功能需求

### 2.1 按 MoSCoW

| 级别 | ID | 功能需求 |
|------|----|----------|
| Must | M1 | 区/测点/灯/遮阳档案 + MQTT 遥测/状态 |
| Must | M2 | 铁皮石斛光配方绑定；区有效 PPFD 聚合（AVG） |
| Must | M3 | 硬限规则：欠光补光、过光遮阳/降灯 |
| Must | M4 | 指令下发 + 状态回写 + 控制日志 |
| Must | M5 | 超阈值大动作 → PENDING 工单 → 农艺批准 → 种植员执行 |
| Must | M6 | 告警：欠/过 PPFD、设备离线、指令超时 |
| Must | M7 | 前端：分区光况、设备状态、工单列表 |
| Must | M8 | 演示级空间棚体：3D 棚架 + 灯位 + 冠层 PPFD 热力 |
| Must | M9 | 重庆典型日驱动自然光（冬雾 + 夏正午）；演示日压缩至约 2 分钟 |
| Should | S1 | 金线莲 / 草莓配方一键切换对比 |
| Should | S2 | DLI 日积分曲线与 DLI 告警 |
| Should | S3 | 布灯响应矩阵：改灯高/间距刷新热力 |
| Should | S4 | 光周期内外策略（夜关灯） |
| Should | S5 | VPD / 土湿 / EC 门控调光（仿真传感器） |
| Could | C1 | 冠层均匀度告警 |
| Could | C2 | 农艺助手（Agent）：解释今日光况 / 建议原因（只读） |
| Could | C3 | 真机一路：RS-GZ 或 BH1750 接入 |
| Won't | W1–W4 | 全环控水肥平台 / 多园区 SaaS 计费 / Agent 直接控灯 / 伪造未核实硬件 |

### 2.2 按功能模块（前端页面）

| 页面 | 功能 | 角色 |
|------|------|------|
| 登录 | 六角色演示账号；JWT 鉴权 | 全部 |
| 场务总览 | 全场 PPFD/DLI/遮阳/温湿度、AUTO 开关、告警摘要 | 全部 |
| 冠层光场 | 3D 棚体 + 热力切片、配方切换、三色光谱、全日曲线、工单审批 | 农艺/场长等 |
| 设备 | 设备档案、在线状态、标定/调试（审计） | 运维 |
| 控制日志 | 指令执行状态（SUCCESS/TIMEOUT/FAIL） | 全部 |
| 报告 | 日光照报告、产量-能耗、设备健康、实训 | 场长/农艺/运维/学员 |
| 农艺助手 | 悬浮问答：作物知识/光环境/角色/工单/接入 | 全部 |

---

## 3. 业务规则

| # | 规则 | 说明 |
|---|------|------|
| R1 | 欠光补光 | 区有效 PPFD < 硬限 → 优先升补光（光周期内），遮阳过厚再收网 |
| R2 | 过光遮阳 | PPFD > 硬限 → **优先**降遮阳开度，仍高再降补光（经济性优先） |
| R3 | 目标带微调 | 目标带内小步微调，避免抖振；带 cooldownSec=60 防抖 |
| R4 | 大开度门控 | 建议调光/遮阳 ≥ **80** → 生成 **PENDING 工单**，禁止直发执行器 |
| R5 | 审批≠执行 | `approve` 仅改状态为 APPROVED 并通知；`claim/execute/complete` 才真正下发 |
| R6 | 光周期 | 光周期外默认关灯（MVP 夜策略） |
| R7 | 离线判定 | 心跳/遥测超时（默认 180s）判离线，不参与区聚合 |
| R8 | 指令容差 | 调光 ±3%、遮阳 ±5%；超时 30s 判 TIMEOUT 并告警 |
| R9 | 配方解耦 | 切换作物/阶段 = 区绑定更换 recipeId，不改代码 |
| R10 | 权限边界 | 种植员不可改配方硬限；运维调试须审计；学员生产区只读 |

---

## 4. 阈值分级（真源：contracts/light-recipe.md）

### 4.1 作物光配方（PPFD µmol·m⁻²·s⁻¹ / DLI mol·m⁻²·d⁻¹）

| 配方 | 阶段 | 硬限下限 | **目标带** | 硬限上限 | DLI 目标 | 光周期 |
|------|------|---------|-----------|---------|----------|--------|
| 铁皮石斛（组培） | TISSUE | 50 | **60–70** | 90 | 2.16–3.02 | 12h |
| 铁皮石斛（栽培） | CULTIVATION | 70 | **90–120** | 140 | 3.89–5.18 | 12h |
| 设施草莓（冬春补光） | WINTER | 150 | **250–400** | 550 | 17–25 | 12h |
| 台湾金线莲（生物量） | BIOMASS | 15 | **25–35** | 55 | 1.26–1.76 | 14h |

> DLI ≈ PPFD × 光周期(h) × 0.0036。

### 4.2 控制与告警阈值

| 项 | 值 |
|----|-----|
| 大开度审批阈值 `approveDimAbove` / `approveShadeAbove` | 80（≥ 则必须进工单） |
| 规则步进 | 调光 5% / 遮阳 10%（契约默认） |
| 冷却 | cooldownSec = 60 |
| 指令超时 | 30s → TIMEOUT + 告警 |
| 遥测周期 / 离线判定 | 5–30s（演示 5s）/ 60–90s → OFFLINE（后端默认 180s） |
| 调光容差 / 遮阳容差 | ±3% / ±5% |
| 多传感门控（可选） | VPD>1.4kPa 降补光 0.85 倍；土湿<25% 禁升调光；EC<0.8 限 50% |

### 4.3 遮阳开度语义

| 值 | 含义 |
|----|------|
| shadeOpenPercent = 100 | 网全收（透光最大） |
| shadeOpenPercent = 0 | 网满展（遮光最大） |
| 外遮阳 maxBlock | 0.85（配合膜透光 0.65） |

---

## 5. 接口清单（真源：INTEGRATION.md / contracts/）

### 5.1 REST（后端 :8080）

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | /users/login | 登录 | 公开 |
| POST | /users/register | 注册（禁自选 SYS_ADMIN） | 公开 |
| GET | /greenhouse/zones | 分区列表（含配方/光况） | 登录 |
| GET | /greenhouse/zones/{id}/effective-light | 区有效 PPFD/DLI/遮阳/温湿度 | 登录 |
| GET/PUT | /greenhouse/recipes/{id} | 光配方详情/编辑 | 农艺/系统 |
| PUT | /greenhouse/zones/{id}/recipe | 绑定配方 | 农艺/场长 |
| POST | /greenhouse/lamps/{sn}/dimming | 手动调光（须权限） | 农艺/运维等 |
| POST | /greenhouse/shades/{sn}/open-percent | 手动遮阳 | 农艺/运维等 |
| GET | /greenhouse/work-orders | 工单列表 | 登录 |
| POST | /greenhouse/work-orders/{id}/approve | 农艺批准（≠执行） | 农艺/场长 |
| POST | /greenhouse/work-orders/{id}/complete | 种植员回填完成 | 种植员 |
| POST | /greenhouse/sim/reset-day | 重跑今日仿真 | 系统/农艺 |
| GET | /devices · /control-logs · /alarm-logs | 设备/日志/告警（对接 gh_*） | 按角色 |
| POST | /knowledge-chunks/rag | 农艺助手问答 | 登录 |

### 5.2 MQTT（EMQX :1883）

| Topic | 方向 | 载荷要点 |
|-------|------|----------|
| smart-greenhouse/{sn}/telemetry | 上行 | ppfd(必填)、lux、temperatureC、humidityPct |
| smart-greenhouse/{sn}/status | 上行 | dimmingPercent / shadeOpenPercent / motorState |
| smart-greenhouse/{sn}/alarm | 上行 | 设备侧告警（可选） |
| smart-greenhouse/{sn}/command | 下行 | SET_DIMMING / SET_OPEN_PERCENT / POWER_ON/OFF / STOP |
| smart-greenhouse/{sn}/command/ack | 上行 | 指令回执 |

### 5.3 统一响应

`{ "code": 200, "data": …, "errorMsg": … }`；鉴权头：`token`。

---

## 6. 验收要点（呼应提交清单）

| # | 验收 |
|---|------|
| 1 | 全新环境按 [DEPLOY.md](./DEPLOY.md) 一次部署成功（Docker + 后端 + 前端） |
| 2 | 登录进冠层，PPFD 曲线实时推进（一天 120s） |
| 3 | 欠光见补光、过光见遮阳（规则闭环） |
| 4 | 大开度生成 PENDING 工单，农艺批、种植员执行后才下发（可审计） |
| 5 | 六角色登录看到不同导航与按钮（分权） |
