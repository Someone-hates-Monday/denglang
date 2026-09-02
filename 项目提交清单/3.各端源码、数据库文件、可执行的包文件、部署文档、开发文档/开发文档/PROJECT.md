# 智慧光棚 · 项目描述

> 版本：`v1.0` · 2026-08-30  
> 一句话：**面向设施栽培的光环境闭环平台**——测光 → 配方比对 → 补光/遮阳联动 → 农艺审批工单可审计。

---

## 1. 背景与转型

本仓库由智慧路灯工程转型而来。产品主线为**真实农业光环境对接**；差异化落在：

- **作物光配方**（PPFD/DLI 目标带与硬限）
- **补光 + 遮阳双向控制**（欠光补、过光遮）
- **重庆气候日型驱动**（冬雾寡照 / 夏正午强光；演示日压缩 2 分钟）
- **农艺权限与工单**（大开度变更可追责）
- **空间光场呈现**（棚体 3D + 冠层热力 + 未控/调控曲线）

后端目录名仍可能含 `street-light`（鉴权/MQTT 基建复用）；**前端与文档已去掉路灯产品页。**

---

## 2. 产品定位

| 项 | 内容 |
|----|------|
| 产品名 | **智慧光棚** |
| 主用户 | 场长、农艺师、种植员、设备运维 |
| 主场景 | 连栋/拱棚或植物工厂区；演示主作物为铁皮石斛 |
| 辅场景 | 设施草莓（重庆冬补光产量叙事）、金线莲（耐阴配方切换） |
| 非目标 | 全科水肥 SaaS、多园区计费、Agent 直接控灯 |

---

## 3. 系统能力（当前已实现）

```
重庆日型 / 测点 PPFD
        ↓
   光场模型（自然光×透光×遮阳 + 灯具响应）
        ↓
   光配方硬限 / 目标带规则
        ↓
   补光调光 / 遮阳开度  （大动作 → 工单审批）
        ↓
   状态回写 · 控制日志 · 场务总览 / 空间棚体热力 + 全日曲线
```

| 层 | 内容 |
|----|------|
| 数据 | PostgreSQL `gh_*` 表；迁移 `V20260830_greenhouse.sql` |
| 棚体真源 | [GREENHOUSE-LAYOUT.md](./GREENHOUSE-LAYOUT.md)（`cq-demo-bay-v1`） |
| 接入 | MQTT `smart-greenhouse/{sn}/telemetry\|status\|command` |
| 仿真 | Spring 内 `greenhouse.sim`（无需额外 Docker 仿真服务） |
| API | `/greenhouse/**` |
| 前端 | 场务总览、冠层光场、工单审批；登录默认进光棚 |

详见 [IMPLEMENT.md](./IMPLEMENT.md)、[PRD-MVP.md](./PRD-MVP.md)。

---

## 4. 技术栈

| 端 | 技术 |
|----|------|
| 后端 | Java 21 · Spring Boot · MyBatis-Plus · Eclipse Paho MQTT |
| 前端 | Vue 3 · Vite · Pinia · STOMP WebSocket |
| 基础设施 | Docker：PG(pgvector) `:5433` · EMQX `:1883` |
| 光学 MVP | 响应矩阵/距离衰减网格（非实时 Radiance） |

---

## 5. 文档地图

| 文档 | 用途 |
|------|------|
| [PROJECT.md](./PROJECT.md) | 本文件：产品描述 |
| [TEAM-DIVISION.md](./TEAM-DIVISION.md) | 分工与 RACI |
| [INTEGRATION.md](./INTEGRATION.md) | 对接需求（MQTT/HTTP/联调） |
| [SUBMIT.md](./SUBMIT.md) | 提交说明与验收清单 |
| [PRD-MVP.md](./PRD-MVP.md) | 需求 MoSCoW |
| [contracts/](./contracts/) | 接口契约真源 |
| [../../HANDOFF.md](../../HANDOFF.md) | Agent 交接 |

---

## 6. 本地入口

- Web：http://localhost:5173 · `admin` / `admin123`
- API：http://localhost:8080
- 说明：[IMPLEMENT.md](./IMPLEMENT.md)
