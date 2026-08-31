# GLB 资产管线 · cq-demo-bay

> 控制台 MVP 已支持 **GLB 优先、程序化回退**。热力 / 遮阳 / 日光仍由仿真实时驱动，不进 GLB。

## 目录

```
web/public/models/cq-demo-bay/
  tunnel-shell.glb          # 拱架 + 膜 + 端墙 + 门 + 过道 + 遮阳卷轴
  bed-dendrobium.glb        # 铁皮石斛双层床，原点=床中心
  bed-anoectochilus.glb     # 台湾金线莲密植床
  bed-strawberry.glb        # 设施草莓槽栽床
  lamp-bar.glb              # 灯盘代理
  manifest.json             # 坐标与床位挂载表
```

同种作物共用同一 GLB；B 区按当前配方在金线莲 / 草莓模型间切换（见 `cropCatalog.ts`）。
## 坐标系（冻结）

与 `layouts/cq-demo-bay-v1.json` 一致：

| 轴 | 含义 |
|----|------|
| 原点 | 西南角室内地坪 |
| +X | 东（棚长） |
| +Y | 上（Three / Blender） |
| +Z | 北（布局 JSON 的 `y`） |
| 单位 | 米 |

床位挂载：A 区中心 `(4, 0, 1.4/3.5/5.6)`；B 区 `(12, 0, …)`。

## 烘培（无 Blender 时）

在 `web/` 下：

```bash
npm run bake:glb
```

用 Three.js `GLTFExporter` 写出上述 GLB，可直接被场景加载。

## Blender 精修替换

1. 导入对应 GLB（或从零按尺寸建模）
2. **不要改原点与轴向**；棚体仍 16×7 m，脊高 3.8 m
3. 导出 **glTF Binary (.glb)**，覆盖同名文件
4. 刷新前端；HUD 显示 `模型：GLB 资产 · cq-demo-bay`
5. 可选：在 Blender 里做 LOD / 贴图，文件建议单件 < 5 MB

## 运行时行为

| 层 | 来源 |
|----|------|
| 棚壳 + 苗床 + 灯盘网格 | GLB（失败则程序化） |
| PPFD 热力测量面 | 仿真 `grid`（Viridis） |
| 外遮阳展开 | `shadeOpenPercent` |
| 日光箭头 / 平行光 | `solarElevationDeg` / `solarAzimuthDeg` |

代码：`web/src/scene/greenhouseAssets.ts` · `GreenhouseScene3D.vue`。

## 下一步（可选）

- 用真实植株/灯具 CAD 替换单位床与 `lamp-bar`
- 答辩展陈：同一 GLB 进 UE5，业务数据仍读 `/greenhouse/.../effective-light`
