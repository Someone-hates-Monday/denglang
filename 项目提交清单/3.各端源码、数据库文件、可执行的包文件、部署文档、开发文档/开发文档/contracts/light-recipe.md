# 智能光棚 · 光配方与设备模型契约

> 版本：`v0.2` · 2026-08-30  
> 作物策略修订见 [RESEARCH-SOLUTION.md](../RESEARCH-SOLUTION.md)

---

## 1. 设备类型枚举

| deviceType | 说明 |
|------------|------|
| `PAR_SENSOR` | 光照/量子测点 |
| `GROW_LAMP` | 补光灯 |
| `SHADE_ACTUATOR` | 遮阳/遮阴执行器 |
| `EDGE_GATEWAY` | 边缘网关（可选，不入 MVP 演示） |

### model 枚举（与 BOM 对齐）

| model | deviceType |
|-------|------------|
| `APOGEE_SQ500` | PAR_SENSOR |
| `LICOR_LI190R` | PAR_SENSOR |
| `RENKE_RS_GZ_N01` | PAR_SENSOR |
| `BH1750` | PAR_SENSOR |
| `VEML7700` | PAR_SENSOR |
| `SIM_PAR` | PAR_SENSOR |
| `ZPDM651` | GROW_LAMP |
| `SIM_LAMP` | GROW_LAMP |
| `CHUANGMING_B` | SHADE_ACTUATOR |
| `TIANGONG_SHADE` | SHADE_ACTUATOR |
| `SIM_SHADE` | SHADE_ACTUATOR |

---

## 2. 区有效光（聚合）

一区多个测点时：

| 策略 | 定义 | 默认 |
|------|------|------|
| `AVG` | 算术平均 | **是** |
| `MIN` | 最小值（保守，防欠光） | 可选 |
| `P10` | 10 分位 | Could |

规则引擎输入：`zoneEffectivePpfd`（按策略聚合后的 ppfd）。

均匀度（Could）：`(max-min)/avg`；超过 `uniformityMaxRel` 触发 `UNIFORMITY_LOW`。

---

## 3. 光配方 LightRecipe（JSON）

```json
{
  "recipeId": "dendrobium-officinale-veg-v1",
  "crop": "DENDROBIUM_OFFICINALE",
  "cropNameZh": "铁皮石斛",
  "stage": "VEGETATIVE",
  "version": 1,
  "enabled": true,
  "photoperiodHours": 12,
  "ppfdTargetMin": 60,
  "ppfdTargetMax": 70,
  "ppfdHardMin": 50,
  "ppfdHardMax": 90,
  "dliTargetMin": 2.16,
  "dliTargetMax": 3.02,
  "control": {
    "preferNaturalLight": true,
    "autoSupplement": true,
    "autoShade": true,
    "dimmingStepPercent": 5,
    "shadeStepPercent": 10,
    "cooldownSec": 60,
    "requireAgronomistApprovalAboveDim": 80,
    "requireAgronomistApprovalAboveShade": 80
  },
  "references": [
    "Plant factory Dendrobium officinale optimal ~60-70 µmol·m⁻²·s⁻¹; photoinhibition risk above ~90"
  ]
}
```

### 字段说明

| 字段 | 含义 |
|------|------|
| ppfdTargetMin/Max | 目标带（尽量维持） |
| ppfdHardMin/Max | 硬限：低于补光、高于遮光/降灯 |
| dliTarget* | 日积分目标（mol·m⁻²·d⁻¹）；`DLI ≈ PPFD × hours × 0.0036` |
| preferNaturalLight | 先靠遮阳/自然光，再加人工光 |
| cooldownSec | 动作冷却，防抖 |
| requireAgronomistApprovalAbove* | 超过该开度/功率的变更须审批 |

### 冻结作物配方（MVP）

#### A. 铁皮石斛 · 组培/工厂苗 `dendrobium-officinale-tissue-v1`（精细控光主叙事）

| 参数 | 值 |
|------|-----|
| hardMin / target / hardMax | 50 / **60–70** / 90 |
| photoperiodHours | 12 |
| dli 约（12h@65） | ≈ 2.8 |
| 依据 | 人工光工厂：~68 最佳；>92 光抑制、多糖下降 |

> 原 `dendrobium-officinale-veg-v1` 保留为别名，语义对齐本配方。

#### B. 铁皮石斛 · 栽培苗 `dendrobium-officinale-cultivation-v1`

| 参数 | 值 |
|------|-----|
| hardMin / target / hardMax | 70 / **90–120** / 140 |
| photoperiodHours | 12 |
| 依据 | 栽培苗干物率至 ~120 增益明显；~150 出现抑制 |

#### C. 设施草莓 · 冬春补光 `fragaria-greenhouse-winter-v1`（重庆寡照 / 产量叙事）

| 参数 | 值 |
|------|-----|
| hardMin / target / hardMax | 150 / **250–400** / 550 |
| dliTargetMin / Max | **17 / 25** |
| photoperiodHours | 补光窗口优先填自然光低谷（如动态 8–10 h） |
| preferNaturalLight | true |
| 依据 | 园艺 DLI 指南 17–25；红颜日光温室 LED 补光增产约 33–56% |

#### D. 台湾金线莲 · 生物量期 `anoectochilus-formosanus-biomass-v1`

| 参数 | 值 |
|------|-----|
| hardMin / target / hardMax | 15 / 25–35 / 55 |
| photoperiodHours | 14 |
| 说明 | ~30 利生物量；≥60 易受抑 |

切换作物/阶段 = 区绑定更换 `recipeId`，**不改代码**。

### 多传感门控（可选字段，规则层）

```json
"gates": {
  "vpdHighKpa": 1.4,
  "ppfdScaleWhenVpdHigh": 0.85,
  "moistureMinPct": 25,
  "forbidRaiseDimmingWhenDry": true,
  "ecMinMsCm": 0.8,
  "capDimmingPercentWhenEcLow": 50
}
```

---

## 4. 控制规则（伪代码）

```
每周期读取 zoneEffectivePpfd：
  if ppfd > hardMax:
      优先 shadeOpenPercent += step（不超过 100）
      若仍高：dimmingPercent -= step
  else if ppfd < hardMin:
      优先 dimmingPercent += step（若在光周期内）
      若遮阳过厚：shadeOpenPercent -= step
  else if ppfd < targetMin: 微调补光 +
  else if ppfd > targetMax: 微调遮阳 + 或补光 -
  应用 cooldown；大开度变更 → 生成 PENDING 建议工单
```

光周期外：默认 dimming→0（或配方规定的夜灯策略；MVP 关灯）。

---

## 5. HTTP 资源草案（后端）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/greenhouse/zones` | 分区列表 |
| GET | `/greenhouse/zones/{id}/effective-light` | 有效 PPFD/DLI |
| GET/PUT | `/greenhouse/recipes/{id}` | 光配方 |
| PUT | `/greenhouse/zones/{id}/recipe` | 绑定配方 |
| POST | `/greenhouse/lamps/{sn}/dimming` | 手动调光（须权限） |
| POST | `/greenhouse/shades/{sn}/open-percent` | 手动遮阳 |
| GET | `/greenhouse/work-orders` | 工单列表 |
| POST | `/greenhouse/work-orders/{id}/approve` | 农艺审批 |
| POST | `/greenhouse/work-orders/{id}/complete` | 种植员回填 |

统一响应：`{ code, errorMsg, data }`；鉴权头与现网一致可用 `token`。
