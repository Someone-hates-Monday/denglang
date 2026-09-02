# 智能光棚 · 适配器对照

> 业务层只认统一字段；硬件差异关在 Adapter 内。

| adapterId | 硬件 | 上行映射 | 下行映射 |
|-----------|------|----------|----------|
| `sim.par` | 3D 光模型 | 坐标采样 → ppfd,lux | — |
| `apogee.sq500` | Apogee SQ-500 | mV×标定 → ppfd | — |
| `licor.li190r` | LI-COR LI-190R | 电流/电压→ppfd | — |
| `renke.rs_gz` | 建大仁科 RS-GZ-N01 | Modbus lux | — |
| `bh1750.i2c` | BH1750 | I2C lux（可×k→ppfd） | — |
| `sim.lamp` | 虚拟灯 | — | dimmingPercent 写状态 |
| `lamp.zpdm651` | ZPDM651 | — | percent→0–10V/PWM |
| `sim.shade` | 虚拟遮阳 | — | shadeOpenPercent 写状态 |
| `shade.chuangming_b` | 创明众联 B | 读开度（若支持） | percent→RS485 定位帧 |

**MVP：** 全部设备档案 `adapterId` 使用 `sim.*`。  
**联真机：** 改档案即可，无需改规则引擎。
