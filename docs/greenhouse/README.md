# 智慧光棚 · 文档索引

> **智慧光棚**：铁皮石斛（分阶段）+ 设施草莓（重庆寡照）光环境管控——空间光场热力 + 重庆日型 + 光配方补/遮闭环 + 农艺工单。  
> **交接入口：** [HANDOFF.md](../../HANDOFF.md)（双轨分工 §0）  
> 符合性与方案：[CONFORMANCE.md](./CONFORMANCE.md) · 差距与发布：[GAP-ASSESSMENT.md](./GAP-ASSESSMENT.md) · 角色：[RBAC-ROLES.md](./RBAC-ROLES.md)

| 文档 | 用途 |
|------|------|
| [GREENHOUSE-LAYOUT.md](./GREENHOUSE-LAYOUT.md) | **棚体空间设计真源**（边界/朝向） |
| [GREENHOUSE-DESIGN-DETAILED.md](./GREENHOUSE-DESIGN-DETAILED.md) | **细化设计**：叠层作物、灯感对应、遮阳材料/卷向、3D 技术路线 |
| [LIGHTING-UPGRADE-v1.3.md](./LIGHTING-UPGRADE-v1.3.md) | **A 轨**：布灯/测点/遮挡/分床控光升级 |
| [layouts/cq-demo-bay-v1.json](./layouts/cq-demo-bay-v1.json) | 机器可读坐标（**v1.4**） |
| [PROJECT.md](./PROJECT.md) | 项目描述 |
| [TEAM-DIVISION.md](./TEAM-DIVISION.md) | **双轨分工** A 控光 / B 角色页面 |
| [CONFORMANCE.md](./CONFORMANCE.md) | 符合性核查 + P0–P3 方案 |
| [GAP-ASSESSMENT.md](./GAP-ASSESSMENT.md) | **成果 vs 场景 vs 发布差距** + 下一步优先级 |
| [RBAC-ROLES.md](./RBAC-ROLES.md) | 角色权限与协同（交 B 轨） |
| [INTEGRATION.md](./INTEGRATION.md) | 对接（MQTT/HTTP） |
| [AGENT.md](./AGENT.md) | **顾问智能体**：只读工具 + 薄 RAG + 对话上下文 |
| [SUBMIT.md](./SUBMIT.md) | 提交与验收清单 |
| [SUBMIT-PLAN.md](./SUBMIT-PLAN.md) | **提交清单盘点 + 六人分工**（对应课程提交清单） |
| [REQUIREMENTS.md](./REQUIREMENTS.md) | **需求清单**（定位/功能/规则/阈值分级/接口清单） |
| [DEPLOY.md](./DEPLOY.md) | **部署文档（从零到跑通，含排障）** |
| [答辩视频脚本.md](./答辩视频脚本.md) | **答辩视频分镜与口播（3 分钟）** |
| [assets/](./assets/) | **原型思维导图 + 功能/技术架构图（PNG）+ 答辩 PPT（.pptx）** |
| [IMPLEMENT.md](./IMPLEMENT.md) | 本地启动与已实现 |
| [RESEARCH-SOLUTION.md](./RESEARCH-SOLUTION.md) | 作物/方案调研 |
| [CROP-ECONOMICS-STANDARDS-REF.md](./CROP-ECONOMICS-STANDARDS-REF.md) | **作物产量·价值·电费·规范**参考汇编（含真实性核对） |
| [SOLUTIONS-LAYOUT-LIGHT-SIM-REF.md](./SOLUTIONS-LAYOUT-LIGHT-SIM-REF.md) | **Blender/排布/布灯传感/光模拟**外部方案借鉴清单 |
| [PRD-MVP.md](./PRD-MVP.md) | MoSCoW / 验收 |
| [HARDWARE-BOM.md](./HARDWARE-BOM.md) | 商购型号 |
| [contracts/mqtt.md](./contracts/mqtt.md) | MQTT |
| [contracts/light-recipe.md](./contracts/light-recipe.md) | 光配方 |
| [contracts/light-field-model.md](./contracts/light-field-model.md) | 光场计算 + ClimateProfile（引用布局真源） |
| [contracts/adapters.md](./contracts/adapters.md) | sim ↔ 真机适配 |

**工程顺序：** 棚体设计（GREENHOUSE-LAYOUT）→ BOM/契约 → 仿真与前后端适配。
