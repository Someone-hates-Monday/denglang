# 智慧光棚 · 成果符合性核查与解决方案

> 核查日：2026-08-31 晚（代码复核 + WO 光晕挂 SN；见 [GAP-ASSESSMENT.md](./GAP-ASSESSMENT.md) §0）· 对照 [PRD-MVP.md](./PRD-MVP.md) / [RBAC-ROLES.md](./RBAC-ROLES.md)  
> 状态：snapshot（随实现更新）
---

## 1. 总判

| 域 | 结论 |
|----|------|
| 光闭环演示（测点→配方→补/遮→3D） | **基本符合**答辩主叙事 |
| 大动作工单可追责（M5 + RBAC） | **符合冻结口径**：AUTO 先执行，超阈值建审计 WO；approve≠execute + claim |
| 角色分权与界面 | **基本符合 R1+**：六角色、`dev.view`、学员禁控、3D 主舞台 |
| 设备/日志页、GH 告警 | **符合**；3D 光晕点选处置为加分体验 |
| 课程提交 / 发布包 | 见 [GAP-ASSESSMENT.md](./GAP-ASSESSMENT.md)：jar 归档 + 答辩视频为硬缺口 |

---

## 2. 符合（PASS）

| ID | 项 | 说明 |
|----|----|------|
| M1 | 区/灯/遮阳/测点档案 + MQTT 模式 | `gh_*` + 仿真 tick |
| M2 | 配方绑定 + 区有效 PPFD | 含动态目标 |
| M4 | 指令下发 + 状态/控制日志（光棚域） | MQTT command + `gh_control_logs` |
| M8 | 3D 棚体 + 冠层热力（含三色通道） | `GreenhouseScene3D` |
| M9 | 重庆日型 + ~2 min 压缩日 | `ClimateProfiles` + dayCompress |
| US-02 | 欠光自动补光 | `applyRules` + cooldown |
| US-05 | 切换金线莲等配方 | UI 绑定即可 |
| B1 | 物理遮阳（直射/漫射） | `physicalShadeTransmittance` |
| B2 | 三色补光光谱份额 | `SpectrumShares` + grid RGB |
| B3 | 遮阳粗档 100/70/40/10 | `LightEconomics` + AUTO snap |
| B4 | 性价比控光（先开遮/先降灯） | `shouldCloseShade` 等 |
| B5 | economics API + 前端产量/电费/建议 | `effective-light.economics` |
| FE1 | 场务光场主路径（原总览已并入 3D HUD） | Greenhouse 统一入口 |
| C3 | approve ≠ execute | `approveWorkOrder` 只改 APPROVED |
| C4 | claim 下发 | `POST .../claim` 下发并 COMPLETED；UI「接单执行」 |
| C1 | AUTO ≥阈值 → 审计 WO | `applyRules`：先 `setDimming`，再 ≥`approveDimAbove` 建「已执行…待复核」 |
| C2 | 遮阳大关 → 审计 WO | 先 `setShadeOpen`，闭光≥`approveShadeAbove` 建审计工单 |
| D1 | R1 角色码 + 服务端鉴权 | 六码 + JWT `role` + `@RequireCap` |
| D2 | 六演示账号 / UI `can()` | 种子 + 登录卡 + 导航/按钮分权 |
| E3 | 按角色导航默认首页 | `homePathFor` + `navFor` |
| P1.1–P1.3 | 角色分权 R1 任务 | 见 §6 P1（已勾） |
| Agent | 顾问智能体（Could） | 后端编排 + 只读工具 + 薄 RAG；前端 AgriAgent；**不**控灯；见 [AGENT.md](./AGENT.md) |

---

## 3. 部分符合（PARTIAL）

| ID | 项 | 差距摘要 |
|----|----|----------|
| M3 / US-03 | 过光策略 | **已对齐**：先降补光，硬限再用遮阳粗档（PRD US-03 已改） |
| M5 / US-04 | 大动作工单 | **冻结为执行+审计**：直发后建 PENDING 复核单；claim 仍为接单叙事（非二次下发门控） |
| M7 / E1 | 前端「设备」 | 已接 `/greenhouse/devices`；运维调试能力仍弱于文档维护窗 |
| US-01 | 测点遥测 | 仿真直写库 + WS；完整 MQTT telemetry 发布可选 |
| Recipe | 配方 CRUD | 可绑定，不可在线改硬限（`recipe.edit` 能力预留） |
| E2 | 日志页 | 已接 `gh_control_logs`（含来源筛选 / 告警 Tab） |
| M6 | 光棚告警（欠/过 PPFD、离线、指令超时） | `gh_alarms` + MQTT `alarm` ingest + WS `/topic/alarms` |

---

## 4. 不符合（FAIL）

| ID | 项 | 现状 |
|----|----|------|
| — | （P2 日志/告警已闭环；余下见 §5 R2–R4） | — |

---

## 5. 仅文档 / 未开工（R2–R4）

| ID | 项 |
|----|----|
| R2 | 权限申请 `gh_perm_requests` |
| R3a | 站内联系 `gh_messages`（待） |
| R3b | 日报告 / 实训报告 `gh_reports`：**已做**（批阅壳；作业流仍浅） |
| R4 | 学员沙箱 `ZONE-SIM` 服务端隔离（生产只读 + 实训报告已有，沙箱可调未落地） |

场景 vs 发布差距的优先级见 **[GAP-ASSESSMENT.md](./GAP-ASSESSMENT.md)**。

---

## 6. 解决方案规划（按优先级）

> **轨归属：** P0 = **A 轨（大棚控制）**；P1–P3 与角色/壳层页 = **B 轨**。入口见 [HANDOFF.md](../../HANDOFF.md) §0。  
> **提交阻塞项**不在本表扩功能，而在 GAP §5 P0（jar / 视频 / 空机验收）。

### P0 · 控光闭环门控（A 轨）

| 任务 | 做法 | 状态 |
|------|------|------|
| P0.1 AUTO 审计工单 | ≥`approveDimAbove` → 执行后建 PENDING 复核 | **已做（执行+审计）** |
| P0.2 遮阳粗档审计 | 闭光≥`approveShadeAbove` → 执行后建 PENDING | **已做（同上）** |
| P0.5 3D 性能与点选 | 热力就地改色；设备签名差分；选中可切换；分区聚焦 | **已做** |
| P0.6 工单光晕挂 SN | 前端用 `targetDeviceSn`，无则退回区遮阳 | **已做** |
| P0.3 工单状态拆分 | approve / claim / complete | **已做** |
| P0.4 文档对齐 M3 | PRD US-03 与经济性口径 | **已做** |

### P1 · 角色分权 R1（B 轨）

| 任务 | 做法 | 状态 |
|------|------|------|
| P1.1 角色码迁移 | 六码 + `V20260831_rbac_roles.sql` | **已做** |
| P1.2 JWT + 守卫 | claim 含 role；`@RequireCap` / 调光阈值 | **已做** |
| P1.3 前端显隐 | `can()` + 首页 + 工单按钮 + `dev.view` | **已做** |

### P2 · 页面与告警对齐 PRD §10

| 任务 | 状态 |
|------|------|
| P2.1 设备页接 `gh_devices` | 基本已做（场长只读） |
| P2.2 日志页接 `gh_control_logs` | **已做** |
| P2.3 M6 告警 | **已做**（含 3D 光晕关联） |

### P3 · R2–R4 协同

权限申请 → 站内联系 →（报告已做）→ 学员沙箱隔离（见 RBAC-ROLES §7、GAP §5 P1/P2）。

---

## 7. 建议执行顺序

```
【提交】GAP P0：jar + dist 归档 → 录答辩视频 → 空机 DEPLOY
【加分】GAP P1：设备级 WO/告警 → 3D 密度 → ZONE-SIM
【不阻塞】R2 / 站内联系 / 维护窗 / 配方硬限在线编辑
```

**不阻塞答辩演示的：** R2–R4 完整协同、配方在线硬限编辑、完整 MQTT telemetry 发布（`greenhouse.sim.publish-mqtt-telemetry`）。