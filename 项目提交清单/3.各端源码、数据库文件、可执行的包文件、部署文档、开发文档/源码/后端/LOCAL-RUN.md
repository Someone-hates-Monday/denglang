# 本地跑通后端

## 一键（PowerShell）

```powershell
cd smart-street-light-master
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
```

包含：Docker(PG:5433 + EMQX:1883) → 建库 → Docker Maven 编译 → 启动 `:8080`。  
光棚仿真在进程内；路灯模拟灯可选 `docker compose --profile lamp-fleet up -d`。

清理本机无关容器/镜像：`scripts\docker-cleanup.ps1 -AlsoImages`

## 分步

```powershell
docker compose up -d
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1

# 7 盏模拟灯（SN-RM-002～SN-XQ-001）；真机 SN-RM-001 留给 BearPi
powershell -ExecutionPolicy Bypass -File scripts\mqtt-simulate-fleet.ps1
# 停止模拟：同上脚本加 -Stop

# 编译（无需本机 Maven）
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn package -DskipTests

# 运行（需 JDK 21）
java -jar target/zhihui-guangpeng.jar --spring.profiles.active=local,secret
```

## 配置说明

| 文件 | 作用 |
|------|------|
| `docker-compose.yml` | PG `localhost:5433`，EMQX `1883` / 控制台 `18083` |
| `application-local.yml` | 本地 DB/MQTT；`mock-keepalive: false`（勿与 fleet-sim 双开） |

**在线/离线：** 真机与模拟灯均靠 MQTT 光照/status 刷新心跳；断电后约 **心跳超时秒数**（默认 180s）内判离线。仅在不跑 fleet-sim 时可设 `streetlight.demo.mock-keepalive: true` 作兜底。

| `application-secret.yml` | 密码/JWT（已 gitignore，本地自建） |

## 验收

```powershell
Invoke-RestMethod http://localhost:8080/users/login -Method POST -ContentType application/json -Body '{"username":"admin","password":"admin123"}'
```

`code=200` 即成功。

## 前端 HTTP 联调

```powershell
cd ../web
# .env.local 已配置 VITE_API_MODE=http
npm run dev
```

登录 `admin` / `admin123`（来自 `sql/test-data.sql`）。

已有旧库时，重启后端会自动补演示路灯坐标；前端也会按设备 SN 兜底显示。

EMQX 控制台：http://localhost:18083（默认 admin/public）。
