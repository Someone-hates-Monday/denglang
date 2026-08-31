# Agent Handoff · 智慧光棚

> 2026-08-31 · 仓库根 `wuliu-main` · **先读本文**，再按分工进 `docs/greenhouse/`。

---

## 0. 两人轨分工（冻结 · 2026-08-31）

| 轨道 | 负责范围 | 下一步目标（真源） |
|------|----------|-------------------|
| **A · 大棚设计与控制（本轨优先）** | 棚体布局真源、光场/遮阳/三色补光、动态目标、性价比 AUTO、冠层 3D、工单**审计核心**（执行+审计 WO；approve≠execute） | [GAP-ASSESSMENT.md](./docs/greenhouse/GAP-ASSESSMENT.md) **提交 P0**；加分见 GAP P1 |
| **B · 角色 / 权限 / 页面设计（交接组员）** | 六角色码与鉴权 UI、按角色导航与按钮显隐、设备页/日志页对接 `gh_*`、权限申请/联系/报告界面 | [RBAC-ROLES.md](./docs/greenhouse/RBAC-ROLES.md) **R1→R4**；[CONFORMANCE.md](./docs/greenhouse/CONFORMANCE.md) **P1–P3** |

**边界：**  
- B 轨改角色与壳层页面时，**不要改**光场公式、遮阳档、经济性规则、布局 JSON（除非先改布局文档并知会 A）。  
- A 轨改控光 API 字段时，在 [INTEGRATION.md](./docs/greenhouse/INTEGRATION.md) / contracts 留一句，方便 B 接 UI。

详细编制表：[TEAM-DIVISION.md](./docs/greenhouse/TEAM-DIVISION.md)。

---

## 1. 方案与文档放在哪

| 要找什么 | 路径 |
|----------|------|
| 成果 vs 场景 vs 发布 | [`docs/greenhouse/GAP-ASSESSMENT.md`](./docs/greenhouse/GAP-ASSESSMENT.md) |
| **角色权限 / 工单状态机 / 申请·联系·报告** | [`docs/greenhouse/RBAC-ROLES.md`](./docs/greenhouse/RBAC-ROLES.md) |
| **棚体空间真源（禁擅自改尺寸灯位）** | [`docs/greenhouse/GREENHOUSE-LAYOUT.md`](./docs/greenhouse/GREENHOUSE-LAYOUT.md) + [`layouts/cq-demo-bay-v1.json`](./docs/greenhouse/layouts/cq-demo-bay-v1.json) |
| 细化设计（叠层/遮阳材料/3D） | [`docs/greenhouse/GREENHOUSE-DESIGN-DETAILED.md`](./docs/greenhouse/GREENHOUSE-DESIGN-DETAILED.md) |
| PRD / MoSCoW | [`docs/greenhouse/PRD-MVP.md`](./docs/greenhouse/PRD-MVP.md) |
| 光场 / 配方 / MQTT 契约 | [`docs/greenhouse/contracts/`](./docs/greenhouse/contracts/) |
| 本地启动 | [`docs/greenhouse/IMPLEMENT.md`](./docs/greenhouse/IMPLEMENT.md) |
| 文档总索引 | [`docs/greenhouse/README.md`](./docs/greenhouse/README.md) |

---

## 2. 本轨已落地（A · 控制核心 · 摘要）

- 物理遮阳（直射/漫射）+ 粗档 **100/70/40/10**
- 三色补光光谱 + 网格 `r/g/bPpfd`；前端通道切换
- 动态目标（光周期 / VPD / DLI 追赶）
- 性价比 AUTO：欠光先开遮阳，过光先降灯；economics（产量指数/电费/建议）
- 冠层白→红热力切片 + 作物/设备悬停
- **v1.5 布灯**：双层独立补光；L0 搁架下灯；L1 每床 5 灯+5 PAR；`HORTI_BAR_RB_150`（见 `layouts/cq-demo-bay-v1.json`）

**M5 冻结口径（2026-08-31）：** 产量优先 → AUTO **先执行**调光/遮阳，超阈值另建工单作**审计复核**（文案「已执行…待复核」）；approve≠execute，claim 为种植员接单叙事。与早期「阈值前 PENDING 不直发」PRD 字面不同，以本文 + CONFORMANCE 为准。

---

## 3. 工程顺序（仍冻结）

1. **棚体空间设计**（已定）：布局文档 + JSON  
2. BOM / MQTT / 配方契约  
3. 仿真与光场、前后端坐标同步  

禁止在未改布局文档的情况下「优化」灯位或棚尺寸。

---

## 4. 已定产品要点

| 项 | 决策 |
|----|------|
| 产品 | 测光 → 配方 → 补光/遮阳闭环 → 农艺工单 |
| 棚体 | `cq-demo-bay-v1`：16×7 m；长轴东西；西南角原点 |
| 分区 | 整跨石斛；ZONE-A/B 仅为东西半跨控光分区 |
| 气候 | 重庆日型；演示日约 120s |
| 过光策略 | **先降补光，硬限再用遮阳粗档** |
| 大动作工单 M5 | **执行 + 审计 WO**（非门控阻塞） |

---

## 5. 运行

| 项 | 值 |
|----|-----|
| 登录 | `admin` / `admin123` |
| Web | `:5173` → `/greenhouse` |
| API | `:8080` · PG `:5433` · EMQX `:1883` |

Remotes：`origin`=xikunn/wuliu · `fork`/`denglang`=Someone-hates-Monday

---

## 6. Git

仅用户要求时提交/推送；禁 force-push `main`。改棚体先改 `GREENHOUSE-LAYOUT` + JSON，再改代码。
