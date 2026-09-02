# 智能光棚 · MQTT 契约

> 真源：本文件 · 版本 `v0.1` · 2026-08-28  
> Broker：与现网一致可用 EMQX（本地 `:1883`）

---

## 1. Topic 约定

前缀：`smart-greenhouse/`

| Topic | 方向 | 说明 |
|-------|------|------|
| `smart-greenhouse/{deviceSn}/telemetry` | 上行 | 传感器遥测（光/可选温湿度） |
| `smart-greenhouse/{deviceSn}/status` | 上行 | 执行器状态回传 |
| `smart-greenhouse/{deviceSn}/alarm` | 上行 | 设备侧告警（可选） |
| `smart-greenhouse/{deviceSn}/command` | 下行 | 控制指令 |
| `smart-greenhouse/{deviceSn}/command/ack` | 上行 | 指令回执（可选，亦可用 status） |

`deviceSn` 全局唯一，建议前缀见 [HARDWARE-BOM.md](../HARDWARE-BOM.md) §4。

---

## 2. 上行：telemetry

```json
{
  "deviceSn": "PAR-ZONE-A-01",
  "deviceType": "PAR_SENSOR",
  "model": "APOGEE_SQ500",
  "adapterId": "sim.par",
  "zoneId": "ZONE-A",
  "position": { "x": 1.2, "y": 0.8, "z": 1.5 },
  "ppfd": 68.5,
  "lux": 12500,
  "conversionProfile": "RB_LED",
  "temperatureC": 24.0,
  "humidityPct": 72.0,
  "timestamp": "2026-08-28T16:00:00+08:00"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| deviceSn | 是 | 设备序列号 |
| deviceType | 是 | `PAR_SENSOR` |
| model | 是 | 见设备枚举 |
| adapterId | 否 | 仿真/驱动标识 |
| zoneId | 是 | 所属分区 |
| position | 演示建议 | 3D 坐标（m） |
| ppfd | **是*** | µmol·m⁻²·s⁻¹；规则引擎主输入 |
| lux | 否 | 有则上报 |
| conversionProfile | lux 转 ppfd 时 | `SUNLIGHT` / `RB_LED` / `FULL_LED` |
| temperatureC / humidityPct | 否 | 扩展 |
| timestamp | 是 | ISO-8601 |

\* 若硬件只有 lux：边缘或云侧按 `conversionProfile` 计算 `ppfd` 后再入规则。

---

## 3. 上行：status（执行器）

```json
{
  "deviceSn": "LAMP-ZONE-A-01",
  "deviceType": "GROW_LAMP",
  "model": "ZPDM651",
  "zoneId": "ZONE-A",
  "online": true,
  "dimmingPercent": 45,
  "powerOn": true,
  "timestamp": "2026-08-28T16:00:05+08:00"
}
```

遮阳：

```json
{
  "deviceSn": "SHADE-ZONE-A",
  "deviceType": "SHADE_ACTUATOR",
  "model": "CHUANGMING_B",
  "zoneId": "ZONE-A",
  "online": true,
  "shadeOpenPercent": 70,
  "motorState": "STOPPED",
  "timestamp": "2026-08-28T16:00:05+08:00"
}
```

| motorState | 含义 |
|------------|------|
| STOPPED / OPENING / CLOSING / FAULT | 运行态 |

---

## 4. 下行：command

### 4.1 补光调光

```json
{
  "commandId": "cmd-20260828-001",
  "deviceSn": "LAMP-ZONE-A-01",
  "command": "SET_DIMMING",
  "dimmingPercent": 45,
  "source": "AUTO",
  "operatorId": null,
  "timestamp": "2026-08-28T16:00:01+08:00"
}
```

### 4.2 遮阳开度

```json
{
  "commandId": "cmd-20260828-002",
  "deviceSn": "SHADE-ZONE-A",
  "command": "SET_OPEN_PERCENT",
  "shadeOpenPercent": 70,
  "source": "MANUAL",
  "operatorId": "3",
  "timestamp": "2026-08-28T16:00:02+08:00"
}
```

### 4.3 其它命令

| command | 适用 | 载荷 |
|---------|------|------|
| `POWER_ON` / `POWER_OFF` | GROW_LAMP | 无额外字段 |
| `STOP` | SHADE_ACTUATOR | 急停 |
| `CALIBRATE_ACK` | 任意 | 运维标定确认（可选） |

| source | 含义 |
|--------|------|
| AUTO | 规则引擎 |
| MANUAL | 人工 |
| WORK_ORDER | 工单批准后下发 |

---

## 5. 指令执行状态（云侧）

与路灯类似，控制日志：

`PENDING → SUCCESS | TIMEOUT | FAIL`

- 收到匹配 `status`（开度/调光误差 ≤ 容差）→ SUCCESS  
- 超过 `commandTimeoutSec`（默认 30）→ TIMEOUT + 告警  

容差默认：调光 ±3%，遮阳 ±5%。

---

## 6. 告警类型（云侧 + 可选设备上行）

| alarmType | 触发 |
|-----------|------|
| UNDER_PPFD | 区有效 PPFD &lt; 配方下限 |
| OVER_PPFD | 区有效 PPFD &gt; 配方上限 |
| DLI_LOW / DLI_HIGH | 日积分偏离（Should） |
| DEVICE_OFFLINE | 心跳/遥测超时 |
| COMMAND_TIMEOUT | 指令无回执 |
| UNIFORMITY_LOW | 区测点相对极差过大（Could） |

---

## 7. 心跳

遥测周期建议 **5–30 s**（演示 5 s）。  
超时阈值默认 **60–90 s** → OFFLINE。
