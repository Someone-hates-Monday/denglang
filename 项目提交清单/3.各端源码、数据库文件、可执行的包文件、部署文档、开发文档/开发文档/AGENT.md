# 智慧光棚 · 顾问智能体（Agent）

> 版本：`v1.0` · 2026-09-01 · 应用层只读模块，**不挡**测光→配方→补/遮→工单闭环。

---

## 1. 定位

| 项 | 约定 |
|----|------|
| 角色 | 各岗位顾问：汇总棚况、规程知识、产量指数/电费预期，辅助运维与决策 |
| 写权限 | **v1 只读**；不下发调光/遮阳，不调用 `approve` / `claim` |
| 成功条件 | Could；无 LLM Key 时仍可用工具模板答 + 前端离线知识兜底 |
| 编排位置 | 后端 `com.cqu.greenhouse.agent`；前端 [`AgriAgent.vue`](../../web/src/components/AgriAgent.vue) 仅对话 UI |

---

## 2. RAG 边界（混合）

| 数据 | 机制 |
|------|------|
| 实时棚况 / economics / 工单 / 告警 / 设备 | **Tool** → `IGreenhouseService`（禁止灌向量库） |
| 规程与概念（布局、配方口径、RBAC、作物知识） | **薄 RAG**：内存语料关键词检索；可选 Embedding |
| 角色与会话 | System 注入 + 会话窗（最近 8 轮） |

---

## 3. 工具（只读）

| Tool | 数据 |
|------|------|
| `get_zone_light` | `effective-light`（PPFD/DLI/遮阳/温湿） |
| `get_economics` | 同上 `economics`（产量指数≠kg、电费估、adviceZh） |
| `list_work_orders` | 工单列表（可按 status） |
| `list_alarms` | `gh_alarms` |
| `list_devices` | `gh_devices` |
| `get_recipe` | 区绑定配方 / 配方详情 |
| `search_knowledge` | 静态语料 topK |

执行类操作回复引导用户到工单/冠层页。

---

## 4. 上下文四层

1. **System**：只读顾问；数字以工具为准；过光先降补光；`yieldIndex`=DLI 达成率。  
2. **角色切片**：场长 / 农艺 / 种植员 / 运维 / 学员 / 系统管理员关注点不同。  
3. **棚况快照**：默认 A+B 区 light+economics + PENDING 计数。  
4. **对话窗**：同 `sessionId` 最近 8 轮。

---

## 5. HTTP

见 [INTEGRATION.md](./INTEGRATION.md)：`POST /greenhouse/agent/chat`。

---

## 6. 失败兜底

1. 有工具结果、无 LLM → 后端模板润色。  
2. 后端异常 / 503 → 前端本地关键词知识库（旧 AgriAgent）。  
3. 知识未命中 → 诚实说明并列出可答范围。

---

## 7. 明确不做（本阶段）

- Agent 直接控灯 / claim  
- 依赖外网才能演示  
- 仿真全历史进向量库  
