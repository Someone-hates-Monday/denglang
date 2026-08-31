# 智慧光棚 · 排布 / 灯光传感 / 光照模拟 · 解决方案参考

> 版本：`v1.0` · 2026-08-31 · **借鉴清单，非改布局真源**  
> 布局真源仍以 [`layouts/cq-demo-bay-v1.json`](./layouts/cq-demo-bay-v1.json) + [LIGHTING-UPGRADE-v1.3.md](./LIGHTING-UPGRADE-v1.3.md) 为准。  
> 已有摘录：[RESEARCH-SOLUTION.md](./RESEARCH-SOLUTION.md) §3 · [GREENHOUSE-DESIGN-DETAILED.md](./GREENHOUSE-DESIGN-DETAILED.md) §7 · [GLB-PIPELINE.md](./GLB-PIPELINE.md)

---

## 0. 项目内已落地（对照基线）

| 层 | 本仓库现状 | 文档 |
|----|------------|------|
| 植物排布 | 东西向 3 床/半跨；**整跨石斛** L0；西半跨 L0+L1（同种叠层） | LAYOUT / DESIGN-DETAILED §3.0 |
| 布灯 | v1.3：每床 3 灯、Z=1.85、半角 55°、分床控 | LIGHTING-UPGRADE |
| 传感 | 每床 3×PAR，与灯 XY 对齐；冠层测面 | 同上 + BOM |
| 光模拟 MVP | 解析：日光直射/漫射 + 逆平方×光束 + 床遮挡；网格伪彩 | `LightFieldModel` |
| 3D 资产 | Three.js 程序化 + 可选 GLB（`npm run bake:glb`）；Blender 精修路径已写 | GLB-PIPELINE |
| Stretch | Radiance/HLS 离线烘焙；响应矩阵 A·d | RESEARCH / light-field-model |

下文按 **「外部方案 → 可借鉴点 → 对本项目建议」** 组织。

---

## 1. Blender / 3D 资产类方案

| 方案 | 类型 | 技术要点 | 可借鉴 | 对本项目建议 | 许可注意 |
|------|------|----------|--------|--------------|----------|
| **本仓 GLB 管线** | 已接通 | Three `GLTFExporter` → `web/public/models/cq-demo-bay/*.glb`；Blender 同坐标系精修 | 棚壳/床/灯条分件；配方切换换床模 | **继续作为主路径**；单件 &lt;5 MB | 自有 |
| **[horticulture-lighting-simulator](https://github.com/luminousphotonics/horticulture-lighting-simulator)** | 开源 · 首选光学参考 | Radiance + Flask/FastAPI + **Three.js Assembly Viewer**；灯具 **GLB LOD**（high/medium/proxy）；`fixture_groups` 布局 JSON | ① 灯具多 LOD ② 冠层 PPFD 层与 CAD 对齐 ③ 布局 JSON 驱动摆放 ④ 预计算包 + 浏览器播放 | Viewer 观感对齐；**勿**把 Radiance 塞进控制环；Stretch 可学其「离线包播放」 | 查仓库 LICENSE |
| **[MeadowMakerAi/greenhouse-cannabis-model](https://github.com/MeadowMakerAi/greenhouse-cannabis-model)** | 开源演示 | React + R3F；太阳天文位置、24h | 日轨/天空钟、棚内氛围 HUD | 只借交互与日轨；**作物模型勿直接商用抄袭** | 注意条款 |
| **Blender + Python 温室补光闭环试验床** | 学术（IFAC / Processes） | Blender 作**图像反馈控灯**虚拟试验台：场景+相机+控制器同在 Blender | 「离线验证控光策略」；点云/网格上估光 | 课程若要做「视觉控灯」可作 Should 实验；**不替代** MQTT/REST 主环 | 论文方法，无成品仓必跟 |
| **CGTrader / Sketchfab Greenhouse Pack** | 商用/付费资产 | 模块化拱棚、连栋、苗床 | 快速出答辩 cinematic 棚体 | 导出 **GLB** 覆盖 `tunnel-shell`；尺寸缩放到 16×7 m | **买授权**；勿默认可开源再分发 |
| **E-on PlantFactory / PlantCatalog（现免费下载）** | 植株程序化资产 | 真实物种网格，可导出到 Blender 等 | 石斛/草莓类观感增强 | 仅作自建植株参考或自创导出；注意 **PlantCatalog 版权**与分发限制 | 读 Bentley 许可 |
| **UE5 Digital Twin 教程** | 展陈 | FBX/GLB + 外部 API | 答辩大屏 | 读 `/effective-light`；**不进**控制闭环 | Epic 许可 |

### 1.1 Blender 工作流建议（与本仓对齐）

```
layouts/cq-demo-bay-v1.json  (米·西南角·+X东 +Y上 +Z北)
        │
        ├─► npm run bake:glb     → 代理几何（无 Blender 时）
        └─► Blender 精修         → 覆盖同名 .glb（禁止改原点/轴向）
                │
                ├─► Three 控制台（热力仍来自仿真 grid）
                └─►（可选）UE5 展陈
```

**不要做：** 在 Blender 里重算业务 PPFD 当控制输入；热力真源永远是后端网格/传感器。

---

## 2. 植物排布方案（产业 / 规程 → 本棚对照）

### 2.1 铁皮石斛（ZONE-A）

| 外部常见做法 | 要点 | 本项目已落地 | 还可借鉴 |
|--------------|------|--------------|----------|
| 雁荡山设施规程类 | 棚宽 6/8 m；高架床离地 ≥0.5 m；畦宽 1.2–1.5 m；外遮阳 50–65% | 棚 7×16；`zBed≈0.55`；外遮阳；床宽 0.80（演示偏窄） | 答辩说明「床窄为双区演示」；真建可加宽到 1.2 m |
| 双层/炼苗搁架 | L0 栽培 + L1 组培/炼苗 | A 区 L0+L1；L1 独立灯/PAR | 保持 L1 低峰值（已 55）防上层灼苗 |
| 丛植密度 | 产业约 10 cm 级丛距（启动成本文档常见） | 仅视觉代理，不进控制 | 报告可写「演示密度」；控制仍看床面网格 |

### 2.2 设施草莓（ZONE-B 切换）

| 外部常见做法 | 要点 | 本项目 | 还可借鉴 |
|--------------|------|--------|----------|
| 高架基质槽 | 架高约 **70–120 cm**；槽上宽约 **25–33 cm**；架间距约 **1.2–1.5 m** | 用床框代理槽栽；冠层约 0.78–0.95 | GLB `bed-strawberry` 可做成双行槽外形 |
| 定植 | 双行；株距约 **14–20 cm**，行距约 **20 cm**；亩栽约 6500–8000 | 不进控制回路 | 叙述层写清密度；均匀度仍按床面网格 |
| 南北坡降 | 部分规程要求北高南低约 0.5% 排水 | 未建模坡降 | Stretch：床面微坡仅视觉 |

### 2.3 金线莲（ZONE-B 默认）

| 外部常见做法 | 要点 | 本项目 | 还可借鉴 |
|--------------|------|--------|----------|
| 多层遮阳密植盘栽 | 棚内照度约数千 lx；忌下层过暗霉变 | 低 PPFD 配方 25–35；单层床密植代理 | 演示可强调「耐阴对照」；勿拉高 B 区灯峰值 |
| 植物工厂层架 | 层内可控光温湿 | 非本棚形态 | 仅作文对比，不改 JSON |

### 2.4 排布设计原则（跨作物，可答辩）

1. **床长轴 || 灯条长轴（东西）** → 统一灯距、易分床控。  
2. **南床 / 北床自然光梯度** → 显均匀度与补光价值（重庆雾天仍有南北差叙事）。  
3. **走道不布主灯正下方** → 减无效功耗（中央通道 7.5–8.5 m）。  
4. **叠层必独立测光** → L1 遮挡 L0（v1.3 已做 soft occlusion）。

---

## 3. 灯光布置方案（外部 → 本棚）

| 来源 | 布置要点 | 与本项目 v1.3 | 建议动作 |
|------|----------|---------------|----------|
| 园艺工程经验 | 安装高度按**冠层**计；升高→重叠↑、峰值↓；用网格 U₀ 验收 | 净空 **0.95 m**；U₀≥0.6 演示目标 | 保持；报告对比「试验近距 15 cm vs 本设计均匀度优先」 |
| 草莓 LED 试验（红颜） | 灯距冠层约 **15 cm**；PPFD 约 250–490；红蓝 9/1 增产最大；**动态补光**电能效率约 2.6× | 配方目标带 250–400；AUTO 按缺口调 | 控制策略对齐「动态」；几何不必抄 15 cm（热点风险） |
| HLS / basis-matrix | 分区/环带单独开灯建响应矩阵 A，再求权重 w | RESEARCH 已写 `PPFD=A·d+τ·E_sun` | Should：离线算 A，前端改灯高即时刷新 |
| DIALux evo + 园艺换算 | IES/LDT 配光 → Excel 把 lx 换成 PPFD | 本仓用解析锥+峰值标定 | 真机 BOM 阶段：厂商 IES → Dialux 出均匀度图贴答辩 |
| Cerise365+GreenHouse Designer | 专用温室/拱棚补光设计 SaaS；DLI/光谱/调光 | 产业对标产品 | 了解叙事即可；不强制采购 |
| Heliospectra helioCORE | 按自然光动态调光、保 DLI | 产品叙事对齐 | 逻辑自研（已有 preferNaturalLight） |
| GreenLight（WUR 开源温室能耗模型） | LED/HPS 补光下的热负荷与气候耦合 | 本仓暂无热模型 | Stretch：电费+采暖联立时再引 |
| 设施规程补光表述 | 有文件写 LED 补光约 **200 µmol·m⁻²·s⁻¹** 量级 | 草莓配方更高目标带（填谷） | 区分「规程示意」与「试验最优」 |

### 3.1 灯具几何可借鉴清单

| 做法 | 说明 |
|------|------|
| 条形灯沿床长轴 | 本仓已采用；HLS/产业常见 |
| 同床多灯等距 | 本仓 3 灯/床，间距约 2.25 m |
| 分区调光 / 分环权重 | HLS 同心环；本仓 **分床** 等价简化 |
| GLB 灯具 LOD | 学 HLS：远距 proxy、近距 high |
| 光谱通道 | 本仓 RGB 份额演示；真机用配方光谱，不必在 Blender 里烘光谱 |

---

## 4. 传感器布置方案

| 来源 / 原则 | 要点 | 本项目 | 缺口 / 建议 |
|-------------|------|--------|-------------|
| **与执行器同构** | 测点服务「验证灯下 + 床面均匀度」 | 每床 3 PAR，XY≈灯位 | 已对齐 v1.3 |
| **冠层平面** | 量子传感器在冠层顶，非走道、非地坪 | Z=测光面；3D 为托盘环 | 保持 |
| **科研标定** | Apogee SQ-500 / LI-COR LI-190R | BOM 已列；MVP 用 `sim.par` | 答辩声明仿真 vs 真机 |
| **均匀度指标** | U₀=min/avg；或 (max−min)/avg | bedStats.uniformityU0；告警预留 | 前端可标红 U₀&lt;0.55 |
| **多传感门控** | VPD / 土湿 / EC 改目标，不抢光主环 | RESEARCH 伪规则；代码部分未全开 | Should：仿真传感器先开 |
| **PFAL 光学论文** | 层架多点 + 叶面网格估吸收均匀度 | 本棚用床面网格 | 石斛叠层已有 L1 测点 |
| **忌** | 单点代表全区；只放过道 | 文档已禁 | Code review 时盯死 |

**温湿度计（规程）：** 苗床上方 0.3–0.4 m — 与 PAR 冠层面可共杆分层安装（上 PAR、下温湿）。

---

## 5. 光照模拟方案谱系（怎么选）

```
复杂度 ↑
│  FSPM + Radiance 叶面接收（HLS、WUR 番茄/油菜类）     ← Stretch 论文级
│  Radiance / Dialux 冠层平面伪彩 + IES                 ← Stretch 布灯验收
│  响应矩阵 A·d + 日光项（本仓 RESEARCH 目标）           ← Should
│  解析：逆平方×cos×光束 + 直射/漫射 + AABB 遮挡（已实现）← MVP ✓
│  仅常数×调光、无空间场                                 ← 过简，已淘汰
复杂度 ↓
```

| 方案 | 输入 | 输出 | 实时性 | 借鉴用法 |
|------|------|------|--------|----------|
| **本仓 LightFieldModel** | 区几何、灯 dim%、遮阳、重庆日型 | 网格 PPFD、bedStats、sunModel | Tick 级 | **控制 + 热力主路径** |
| **Basis-matrix（HLS 同源思想）** | 单灯/单环单位驱动场 | A，再线性叠加 | 预计算一次 | 改灯位后重烘焙 A |
| **HLS Radiance 预计算包** | 房间尺寸、灯系统 | CSV/热力/植物接收 | 离线；浏览器播放 | 答辩对比「解析 vs 光追」一张图 |
| **DIALux + PPFD 换算** | IES、计算面 | lx→PPFD 表 | 设计阶段 | 真灯选型附件 |
| **Cerise365** | 棚型参数、灯具库 | DLI/补光策略 | SaaS | 产业对标 |
| **Blender Cycles/EEVEE** | 场景网格 | 图像亮度（非 µmol） | 慢 | **仅视觉/图像控灯试验**；须标定才能谈 PPFD |
| **GreenLight** | 气候+灯热 | 能耗/气温 | 仿真日 | 电费+热耦合扩展 |
| **GroIMP / FSPM** | 植株结构+光 | 截获与光合 | 研究 | 远期数字孪生，非实训 MVP |

### 5.1 推荐落地节奏（冻结建议）

| 阶段 | 做 | 不做 |
|------|----|------|
| **现在** | 维持解析光场 + Three 热力 + GLB 观感 | 实时 Radiance、Blender 控灯主环 |
| **Should** | 离线响应矩阵；U₀ 告警；Dialux/HLS 出 1 张对比图 | 重写前端引擎 |
| **Stretch** | HLS 预计算包挂「对照页」；UE 展陈；GreenLight 能耗 | 把 FSPM 绑进 AUTO |

---

## 6. 一页对照：外部最佳实践 vs `cq-demo-bay-v1`

| 维度 | 外部共识 / 标杆 | 本棚 v1.3 | 是否需改真源 |
|------|-----------------|-----------|--------------|
| 3D 栈 | Blender→GLB→Web；光学另算 | 已对齐 | 否 |
| 床方向 | 槽/床东西向 + 吊灯平行 | 已对齐 | 否 |
| 灯高 | 试验常近冠层；工程求均匀则抬高 | **0.95 m 净空**（均匀度优先） | 否（答辩讲清取舍） |
| 灯密度 | 一槽多灯或连续光条 | 3 灯/床 | 否 |
| 控制分区 | 环带 / 行 / 床 | **分床** | 否 |
| PAR | 冠层多点 | 3/床 + L1 | 否 |
| 光模拟 | 平面 PPFD +（可选）叶面 | 平面网格 + 床遮挡 | Should：矩阵 A |
| 草莓近距补光 | ~15 cm 高 PPFD | 几何不采用 | 否；配方目标仍可追 |

---

## 7. 链接速查

| 资源 | URL / 定位 |
|------|------------|
| Horticulture Lighting Simulator | https://github.com/luminousphotonics/horticulture-lighting-simulator |
| HLS · Radiance 论坛发布帖 | https://discourse.radiance-online.org/t/public-github-release-horticulture-lighting-simulator-powered-by-radiance/7041 |
| Basis-matrix 说明帖 | https://discourse.radiance-online.org/t/browser-based-horticultural-lighting-simulation-on-radiance-photon-native-ppfd-concentric-ring-layout-basis-matrix-solver-seeking-technical-feedback/6995 |
| MeadowMaker 温室日轨演示 | https://github.com/MeadowMakerAi/greenhouse-cannabis-model |
| DIALux 园艺照明课程说明 | https://www.dial.de/en-GB/dialux-for-horticulture-lighting |
| Cerise365 Greenhouse Designer | https://www.suntrackertech.com/…/cerise-365-greenhouse-designer |
| Blender 图像补光试验床论文 | IFAC 2024 / Processes 2024「Blender virtual test beds」 |
| PFAL Radiance 光学设计论文 | *Evaluation of the Light Environment of a Plant Factory…*（Radiance + 扫描仪植株） |
| GreenLight 温室补光能耗 | WUR / edepot 开源模型文献 |
| 本仓 GLB | [GLB-PIPELINE.md](./GLB-PIPELINE.md) |
| 本仓布灯传感 | [LIGHTING-UPGRADE-v1.3.md](./LIGHTING-UPGRADE-v1.3.md) |

---

## 8. 编写 / 实现时怎么用本文

1. **写设计说明 / 答辩：** 用 §1–§5 的「外部→本项目」表，证明方案有开源与产业对标，而非拍脑袋。  
2. **改观感：** 只动 Blender/GLB（§1.1），不动 JSON 坐标。  
3. **改光学精度：** 优先 Should「响应矩阵 / HLS 对比图」，不要先上 FSPM。  
4. **改床宽株距叙述：** 可写进报告；**改灯位/测点必须先改 JSON + LIGHTING-UPGRADE**（HANDOFF 冻结规则）。
