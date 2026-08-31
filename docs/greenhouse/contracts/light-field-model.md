# 智能光棚 · 光场模型与重庆日型契约

> 版本：`v0.2` · 2026-08-30  
> **棚体几何与设备坐标真源：** [GREENHOUSE-LAYOUT.md](../GREENHOUSE-LAYOUT.md) · [`layouts/cq-demo-bay-v1.json`](../layouts/cq-demo-bay-v1.json)  
> 配套：[RESEARCH-SOLUTION.md](../RESEARCH-SOLUTION.md)

> 软件中的 `lengthM/widthM/pos*` 若与 `cq-demo-bay-v1` 不一致，视为**待同步缺陷**，不得另起一套坐标。

---

## 1. 棚体几何 GreenhouseGeometry

对齐设计冻结值 `geometryId = cq-demo-bay-v1`：

```json
{
  "geometryId": "cq-demo-bay-v1",
  "lengthM": 16,
  "widthM": 7,
  "gutterHeightM": 2.8,
  "ridgeHeightM": 3.8,
  "coverTransmittance": 0.65,
  "diffuseFractionDefault": 0.55,
  "canopyHeightM": 0.5,
  "measurePlaneZ": 0.5
}
```

| 字段 | 说明 |
|------|------|
| coverTransmittance | 膜/PC 透光 τ_cover（0–1） |
| measurePlaneZ | 冠层测光平面高度（m）；ZONE-B 可用 0.45 |

坐标系：西南角原点，+X 东、+Y 北、+Z 上；Three 场景为 `(X, Z, Y)`。方位角 **自北顺时针**（°），正午 ≈180°（偏南）。`sunModel.dirEast/dirNorth/dirUp` 与场景日光箭头同源。

自然光项（直射 + 漫射，v1.3.1 起含东西向直射微调）：

```
E_base = E_out × τ_cover × τ_shade × sin(elevation)
E_sun  = E_base × [(1−f_dif)×bedSun×sunOcclusion×bedEastWest + f_dif×bedSunDiffuse]
```

`shadeClosedFraction = 1 - shadeOpenPercent/100`。雾天 `f_dif` 升高，南北差缩小。

---

## 2. 灯具与遮阳布局

完整灯/PAR/床位表以 JSON **v1.3** 为准（见 [LIGHTING-UPGRADE-v1.3.md](../LIGHTING-UPGRADE-v1.3.md)）：

- ZONE-A：每床 3 灯 × 3 床 + 2×L1；`maxPpfdAtCanopy≈95/55`，Z=1.85 / 2.15  
- ZONE-B：每床 3 灯 × 3 床；峰值 ≈80，Z=1.85  
- 半角 55°；床体/搁架遮挡；**分床**调光  
- 外遮阳半跨：`SHADE-ZONE-A` / `SHADE-ZONE-B`，Z≈3.50  
- 网格：`nx=32, ny=14, marginM=0.25`

---

## 3. 响应矩阵（MVP 光学）

对测光网格点 `i`、灯 `j`：

- 离线或启动时算 `A[i,j]` = 灯 j 在 dimming=100% 时对点 i 的贡献（µmol·m⁻²·s⁻¹）  
- 运行时：`ppfd_led[i] = Σ_j A[i,j] * (dimming_j / 100)`  
- `ppfd[i] = ppfd_led[i] + E_sun_in(t, bedOf_i)`  

区有效光：`AVG` / `MIN`（与 light-recipe 一致）。

**均匀度：** `(max-min)/avg`，超过阈值告警 `UNIFORMITY_LOW`。

---

## 4. 重庆日型 ClimateProfile

```json
{
  "profileId": "cq-winter-fog",
  "region": "Chongqing",
  "labelZh": "重庆冬雾寡照",
  "dayLengthHours": 10.5,
  "samples": [
    { "minuteOfDay": 0, "outdoorParPpfd": 0 },
    { "minuteOfDay": 480, "outdoorParPpfd": 40 },
    { "minuteOfDay": 720, "outdoorParPpfd": 90 },
    { "minuteOfDay": 960, "outdoorParPpfd": 35 },
    { "minuteOfDay": 1080, "outdoorParPpfd": 0 }
  ],
  "notes": "典型日模板，非单日实况；可替换为气象站序列"
}
```

| profileId | 场景 |
|-----------|------|
| `cq-winter-fog` | 冬雾：全天低，偏补光 |
| `cq-winter-clear` | 冬晴：午间可用，填谷补光 |
| `cq-summer-noon` | 夏正午：峰值高，偏遮阳 |
| `cq-overcast` | 阴天：中等缺口 |

插值：相邻 `minuteOfDay` 线性插值得到 `E_out_par(t)`。

---

## 5. 仿真器对外接口（逻辑）

| 输入 | 输出 |
|------|------|
| profileId, geometry=`cq-demo-bay-v1`, layout, dimming[], shadeOpen%, t | 网格 PPFD、区有效值、当日累计 DLI |
| 可选 T/RH/含水/EC | 门控后的建议 dimming/shade（规则层消费） |

MQTT 仍只发测点级 telemetry（抽稀网格或固定测点），与 [mqtt.md](./mqtt.md) 兼容。
