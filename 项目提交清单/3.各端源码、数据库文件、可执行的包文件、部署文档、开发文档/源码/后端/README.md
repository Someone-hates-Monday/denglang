# 智慧光棚 · 后端

Spring Boot 服务：鉴权/MQTT 基建 + **`com.cqu.greenhouse`** 光棚域（配方、仿真、补光/遮阳、工单）。

产品文档与启动：[`../docs/greenhouse/IMPLEMENT.md`](../docs/greenhouse/IMPLEMENT.md) · [`../HANDOFF.md`](../HANDOFF.md)

```powershell
docker compose up -d
powershell -ExecutionPolicy Bypass -File scripts\apply-greenhouse.ps1
# 以 profiles=local,secret 启动 jar 或 IDE
```

- API `:8080` · PG `:5433` · EMQX `:1883`
- 光棚 REST：`/greenhouse/**` · MQTT：`smart-greenhouse/{sn}/…`
- 仿真：`greenhouse.sim.enabled=true`，`day-compress-sec: 120`，`interval-ms: 250`（浮点连续推进）
- 棚体：`cq-demo-bay-v1` 16×7 · 网格 32×14 · 布局见 `docs/greenhouse/`

目录名历史遗留；**交付产品是智慧光棚。**
