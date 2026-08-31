# 智慧光棚 · 成果符合性核查与解决方案

> 核查日：2026-08-31 · 对照 [PRD-MVP.md](./PRD-MVP.md) / [RBAC-ROLES.md](./RBAC-ROLES.md) / 光场经济性实现  
> 状态：snapshot（随实现更新）

---

## 1. 总判

| 域 | 结论 |
|----|------|
| 光闭环演示（测点→配方→补/遮→3D） | **基本符合**答辩主叙事 |
| 大动作工单可追责（M5 + RBAC） | **不符合**预期闭环 |
| 角色分权与界面 | **不符合**（仅文档） |
| 设备/日志页、GH 告警 | **不符合** PRD §10 |

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
| FE1 | 场务总览 + 冠层光场主路径 | Dashboard / Greenhouse |
| DOC1 | PRD §10 / RBAC「现状」描述诚实 | 与代码一致 |

---

## 3. 部分符合（PARTIAL）

| ID | 项 | 差距摘要 |
|----|----|----------|
| M3 / US-03 | 过光策略 | PRD 写「优先遮阳」；现网按经济性「先降灯再粗档遮阳」——产品上更合理，但与旧 PRD 字面不一致 |
| M5 / US-04 | 大动作工单 | 有工单表与审批 API，但 AUTO 从不进 PENDING；批准即下发 |
| M7 | 前端「设备」 | 光况/工单有；设备页仍是路灯域 |
| US-01 | 测点遥测 | 仿真直写库 + WS；未必走完整 MQTT telemetry 发布链路 |
| WO-complete | 完成工单 | API 有、UI 无完成按钮 |
| Recipe | 配方 CRUD | 可绑定，不可在线改硬限 |

---

## 4. 不符合（FAIL）

| ID | 项 | 现状 |
|----|----|------|
| M6 | 光棚告警（欠/过 PPFD、离线、指令超时） | 无 GH 阈值告警落库/页；MQTT alarm 多只打日志 |
| C1 | AUTO ≥80% → PENDING | `applyDimToAll(...,"AUTO")` 直发，条件写反 |
| C2 | 遮阳大动作 → 工单 | 规则从不建遮阳工单 |
| C3 | approve ≠ execute（RBAC） | `approveWorkOrder` 立刻 `setDimming/setShade` |
| C4 | claim / IN_PROGRESS | 状态机与 API 缺失 |
| D1 | R1 角色码 + 服务端鉴权 | 仍 `ADMIN`/`MUNICIPAL_STAFF`；JWT 无 role |
| D2 | 六演示账号 / UI `can()` | 未种子；全员同界面 |
| E1 | 设备页对接 `gh_devices` | `DevicesView` → 路灯 API |
| E2 | 日志页对接 `gh_control_logs` | `ControlLogsView` → 路灯 `/control-logs` |
| E3 | 按角色导航默认首页 | 无 |

---

## 5. 仅文档（DOC_ONLY / 未开工）

| ID | 项 |
|----|----|
| R2 | 权限申请 `gh_perm_requests` |
| R3 | 站内联系 `gh_messages` + 日报告 |
| R4 | 学员沙箱 + 实训报告批阅 |
| Agent | 只读解释（Could） |

---

## 6. 解决方案规划（按优先级）

> **轨归属：** P0 = **A 轨（大棚控制）**；P1–P3 与角色/壳层页 = **B 轨（交接组员）**。入口见 [HANDOFF.md](../../HANDOFF.md) §0。

### P0 · 控光闭环门控（A 轨 · 约 1–2 天）

| 任务 | 做法 | 验收 |
|------|------|------|
| P0.1 修 AUTO 进工单 | `applyDimToAll`：AUTO 且 `next ≥ approveDimAbove` → `createWorkOrder`，不直发；小步仍 AUTO | 欠光拉满会出 PENDING |
| P0.2 遮阳粗档进工单 | 关遮阳档且跨 `approveShadeAbove` 等价条件时建 WO（或场长策略下仍经济性直控小档、大档进单） | 日志/列表可见 SHADE 建议 |
| P0.3 工单状态拆分 | `approve` 只改 APPROVED + 通知；新增 `claim`/`execute`（或种植员「执行」一并下发）；UI：农艺批/驳，种植员执行+完成 | US-R1 可演示 |
| P0.4 文档对齐 M3 | PRD US-03 改为「先降补光，硬限再用遮阳粗档」与经济学一致 | 答辩口径统一 |

### P1 · 角色分权 R1（B 轨 · 约 2 天）

| 任务 | 做法 | 验收 |
|------|------|------|
| P1.1 角色码迁移 | `users.role` 扩六码；种子六账号；旧码映射 | 登录返回新 role |
| P1.2 JWT + 守卫 | claim 含 role；接口按能力 403 | 种植员改配方 403 |
| P1.3 前端显隐 | `can()` + 默认首页 + 工单按钮分角色 | 农艺无「执行」、种植员无「批准」 |

### P2 · 页面与告警对齐 PRD §10（B 轨为主 · 约 1–2 天）

| 任务 | 做法 | 验收 |
|------|------|------|
| P2.1 设备页 | 改接 `/greenhouse/devices` | 见灯/遮阳/PAR |
| P2.2 日志页 | 改接 `/greenhouse/control-logs` | 见 WORK_ORDER/AUTO |
| P2.3 M6 告警 | 硬限/离线写告警；A 可提供规则钩子，B 做展示 | 过欠光可提示 |

### P3 · R2–R4 协同（B 轨 · 有余力）

权限申请 → 站内联系 → 日报告草稿 → 学员沙箱（见 RBAC-ROLES §7）。

---

## 7. 建议执行顺序

```
【A】P0.4 → P0.1 → P0.3 → P0.2
【B 并行】P1 → P2 → P3
```

**不阻塞答辩演示的：** R2–R4、Agent、配方在线 CRUD、完整 MQTT telemetry 发布（仿真直写可接受，口播说明即可）。
