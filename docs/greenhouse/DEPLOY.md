# 智慧光棚 · 部署文档（从零到跑通）

> 版本：`v1.0` · 2026-08-31
> 目标：**让从未部署过本项目的人，照本文逐步操作即可独立把系统跑起来并通过验收。**
> 对应「项目提交清单」第 3 项：**部署文档**。
> 相关：[IMPLEMENT.md](./IMPLEMENT.md)（实施摘要）· [LOCAL-RUN.md](../../smart-street-light-master/LOCAL-RUN.md)（后端脚本）· [docker-compose.yml](../../smart-street-light-master/docker-compose.yml)

---

## 0. 部署前必读

### 0.1 系统由什么组成

```
┌────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│  └─ 前端控制台（Vue 3 + Vite，端口 5173，产物在 web/dist）        │
│       │ REST / WebSocket（vite 代理转发到 8080）                  │
│       ▼                                                          │
│  后端 API（Spring Boot，端口 8080，Java 21）                      │
│       │                                                          │
│       ├──▶ PostgreSQL（端口 5433，Docker 容器 streetlight-pg）   │
│       └──▶ EMQX MQTT（端口 1883，控制台 18083，Docker 容器）      │
└────────────────────────────────────────────────────────────────┘
```

- **光棚仿真跑在后端进程内**（`greenhouse.sim`），不需要额外仿真容器。
- 数据库与 MQTT 由 **Docker Compose** 提供；后端与前端跑在宿主机。

### 0.2 端口总表

| 端口 | 服务 | 说明 |
|------|------|------|
| 5173 | 前端 Web | 浏览器访问入口 |
| 8080 | 后端 API | REST / WebSocket |
| 5433 | PostgreSQL | 数据库（容器内 5432 映射到宿主机 5433） |
| 1883 | EMQX MQTT | 设备接入 |
| 18083 | EMQX 控制台 | `admin` / `public` |

### 0.3 两种部署方式（先选一种）

| 方式 | 需要的环境 | 效果 | 适用 |
|------|-----------|------|------|
| **A · 完整部署** | Docker + JDK + Node | 后端 + 数据库 + 前端全通，可完整演示仿真/工单/实时数据 | 答辩演示、完整交付 |
| **B · 快速演示（Mock）** | 仅 Node | 只跑前端，用内置模拟数据看界面 | 临时看界面、前端开发 |

> 本文以 **方式 A** 为主线（0–8 节），方式 B 见 [§10](#10-方式b快速演示仅需node)。

### 0.4 需要安装的软件（版本已核实）

| 软件 | 版本要求 | 用途 | 是否必需 |
|------|----------|------|----------|
| WSL 2（含发行版） | Windows 10 22H2+ / 11 | Docker Desktop 运行 Linux 容器的前提 | 方式 A 必需 |
| Docker Desktop | 20.10+ | 提供 PostgreSQL + EMQX 容器 | 方式 A 必需 |
| JDK | **21**（17+ 亦可） | 编译/运行后端 | 方式 A 必需 |
| Node.js | **18+（推荐 20 LTS）** | 前端 | 必需 |
| Git | 任意较新版本 | 拉取代码 | 必需 |
| Maven | 无需本机安装 | 用 Docker Maven 编译后端 | 否 |

---

## 1. 环境准备（一次性）

> 以下安装以 **Windows** 为例。Linux/macOS 思路相同，命令换成对应包管理器。

### 1.1 安装 WSL 2（关键，Docker 依赖它）

Docker Desktop 在 Windows 上用 WSL 2 后端运行 Linux 容器。**若未装 WSL，Docker 引擎会启动失败（常见报错：`docker ps` 返回 500 Internal Server Error）。**

```powershell
# 1) 以“管理员身份”打开 PowerShell，执行：
wsl --install

# 2) 按提示重启电脑（会自动安装默认发行版，如 Ubuntu）
# 3) 重启后确认：
wsl --status
# 应显示“默认分发版”等信息，而不是“未安装适用于 Linux 的 Windows 子系统”
```

> 若 `wsl --status` 仍提示未安装，可再执行 `wsl --install -d Ubuntu` 手动装一个发行版。

### 1.2 安装 Docker Desktop

1. 到官网下载 **Docker Desktop for Windows** 安装包并安装。
2. 打开 Docker Desktop → **Settings → General**，勾选 **“Use the WSL 2 based engine”**。
3. 确认引擎正常：

```powershell
docker version          # 应显示 Client 与 Server 两段
docker ps               # 不报 500，能列出容器（当前为空列表也算正常）
```

> **常见坑：** `docker ps` 报 `500 Internal Server Error ... dockerDesktopLinuxEngine` → 说明 WSL 未装或 Docker Desktop 后端没起来，回到 §1.1。

### 1.3 安装 JDK 21

推荐 [Eclipse Temurin 21](https://adoptium.net/)（LTS）。安装后：

```powershell
java -version
# 应显示 openjdk version "21.x"
```

> 若本机是更高版本（如 25），**运行** jar 通常没问题；但**编译**建议用项目自带的 Docker Maven（见 §6），避免版本差异。

### 1.4 安装 Node.js

推荐 [Node.js 20 LTS](https://nodejs.org/)。安装后：

```powershell
node -v    # v20.x 或更高
npm -v     # 10.x
```

### 1.5 安装 Git

```powershell
git --version
```

---

## 2. 获取代码

方式一：克隆仓库（若有远程仓库地址）：

```powershell
git clone <你的仓库地址>
cd <仓库目录>
```

方式二：直接使用项目文件夹（已拷到本机则跳过本步）。

进入后端目录（后续步骤都从这里开始）：

```powershell
cd smart-street-light-master
```

---

## 3. 启动基础设施（PostgreSQL + EMQX）

```powershell
docker compose up -d
```

- 首次会拉取 `pgvector/pgvector:pg17` 与 `emqx/emqx:5.8.3` 镜像，需联网、稍等。
- 启动两个容器：`streetlight-pg`（PG :5433）、`streetlight-emqx`（EMQX :1883/18083）。

验证：

```powershell
docker ps
# 应看到 NAME 为 streetlight-pg / streetlight-emqx，STATUS 为 Up (healthy) 或 Up
```

> **不启路灯模拟灯**（光棚不依赖）；如误启，可 `docker compose --profile lamp-fleet down`。

---

## 4. 初始化数据库

数据库结构 + 种子数据都在 `sql/` 目录（schema.sql / test-data.sql / migrations/*.sql），无需手工建表。

**场景一：全新数据库（推荐）**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

该脚本会：重建 `smart-street-light` 库 → 应用 schema → 载入测试数据 → 依次执行 `sql/migrations/*.sql`。

**场景二：数据库已存在，只需补温室迁移**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\apply-greenhouse.ps1
```

> 注意：`init-db.ps1` 会 **DROP 重建数据库**，会清空已有数据；已有重要数据时用 `apply-greenhouse.ps1` 增量迁移。

验证（可选）：

```powershell
docker exec -it streetlight-pg psql -U postgres -d smart-street-light -c "\dt gh_*"
# 应列出 gh_zones / gh_recipes / gh_devices / gh_work_orders 等温室表
```

---

## 5. 配置本地密钥

后端运行时需要 `application-secret.yml`（含数据库密码、JWT 密钥）。该文件被 gitignore，**不会随仓库分发，需本地自建**：

```powershell
Copy-Item src\main\resources\application-secret-example.yml src\main\resources\application-secret.yml
```

按需编辑（默认值即可跑通）：

| 项 | 默认值 | 说明 |
|----|--------|------|
| `spring.datasource.password` | `123456` | 与 docker-compose 的 PG 密码一致 |
| `jwt.secret-key` | 模板自带 | JWT 签名密钥，可改 |
| `llm.api-key` | 空 | 可选，问答增强用 |

> 该文件勿提交到 git。

---

## 6. 编译并启动后端

### 6.1 一键脚本（推荐）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
```

脚本依次执行：起 Docker → 初始化库 → **用 Docker Maven 编译** → 启动 `:8080`（前台运行，会占住当前终端，建议单独开一个窗口）。

看到类似日志即成功：

```
Started SmartStreetLightApplication in x.x seconds
Tomcat started on port(s): 8080
```

### 6.2 分步（理解原理，便于排障）

**① 编译（无需本机 Maven，用 Docker 容器编译，固定 JDK 21）：**

```powershell
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-21 mvn package -DskipTests
# 成功后生成 target\smart-street-light-0.0.1-SNAPSHOT.jar
```

> 首次会下载依赖，需联网；国内网络慢可加 `-s settings.xml`（项目自带 `scripts\maven-settings-aliyun.xml` 可改用阿里云镜像）。

**② 启动（需本机 JDK 21）：**

```powershell
java -jar target\smart-street-light-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,secret
```

### 6.3 验证后端

新开一个 PowerShell 窗口执行：

```powershell
Invoke-RestMethod http://localhost:8080/users/login -Method POST -ContentType application/json -Body '{"username":"admin","password":"admin123"}'
# 返回 code=200 即登录成功（code=500 则检查数据库/密钥配置）

Invoke-RestMethod http://localhost:8080/greenhouse/zones
# 应返回 2 个分区（ZONE-A 石斛、ZONE-B 金线莲/草莓）
```

---

## 7. 前端

### 7.1 开发模式（调试用）

```powershell
cd ..\web
npm install        # 安装依赖（首次较慢）
npm run dev        # 启动 Vite，端口 5173
```

浏览器打开 **http://localhost:5173**。

**接口模式配置（重要）：** `web/.env.local` 控制前端连后端还是用内置模拟数据：

```
VITE_API_MODE=mock   # 用内置模拟数据，不需要后端
VITE_API_MODE=http   # 连真后端（需 8080 已启动）
```

> **坑：** `.env.local` 文件**不要带 UTF-8 BOM**（文件头多 3 个隐藏字节），否则 `VITE_API_MODE` 不被识别、前端误认为 HTTP 模式导致登录 502。用 VS Code/记事本另存为“UTF-8（无 BOM）”即可。

### 7.2 构建发布包（用于正式部署/提交）

```powershell
cd ..\web
npm run build       # 产出 web/dist（vue-tsc 类型检查 + vite 打包）
```

产物在 `web/dist`（`index.html` + `assets/` + `models/`）。用任意静态服务器托管即可，例如：

```powershell
npx serve dist      # 或放到 nginx 静态目录
```

> 若 `npm run build` 报 `TS6133: declared but never read` 之类的**未使用变量**类型错误，属于工程历史遗留，不影响运行；删掉对应未使用的声明后重试即可（见 FAQ §9.6）。

---

## 8. 完整验收清单

| # | 验收项 | 操作 | 预期 |
|---|--------|------|------|
| 1 | Docker 正常 | `docker ps` | 两个容器 Up |
| 2 | 数据库就绪 | §4 验证命令 | `gh_*` 表存在 |
| 3 | 后端登录 | §6.3 登录接口 | code=200 |
| 4 | 分区接口 | `GET /greenhouse/zones` | 2 个分区 |
| 5 | 前端登录 | 浏览器 :5173，`admin`/`admin123` | 进入控制台 |
| 6 | 冠层光场 | 登录后进“冠层光场” | 3D 棚体 + PPFD 数据/曲线 |
| 7 | 仿真推进 | 等待 2 分钟 | 日型推进、补光/遮阳联动 |
| 8 | 工单 | 制造大动作（若有） | 出现 PENDING 工单、可审批 |
| 9 | EMQX 控制台 | http://localhost:18083 | `admin`/`public` 可登录 |

> 以上 1–6 为**必达**；7–9 为完整闭环验证。

---

## 9. 常见问题与排障（FAQ）

### 9.1 `docker ps` 报 500 Internal Server Error
- 原因：WSL 未安装，或 Docker Desktop 的 Linux 引擎未启动。
- 解决：`wsl --install` → 重启 → 确认 `wsl --status` 正常 → 重开 Docker Desktop。

### 9.2 端口被占用（8080/5173/5433 已占用）
- 检查：`Get-NetTCPConnection -LocalPort 5173 -State Listen`
- 解决：结束占用进程，或改配置文件端口。

### 9.3 前端登录报「后端无响应 (502) / ECONNREFUSED」
- 原因：`.env.local` 是 `http` 模式但 8080 没起；或 `.env.local` 带 BOM 导致误判 HTTP 模式。
- 解决：确认后端已启动；或把 `VITE_API_MODE` 改 `mock`；检查文件无 BOM（§7.1 坑）。

### 9.4 后端连不上数据库（Failed to connect to PostgreSQL）
- 原因：容器没起，或密码/端口不对。
- 解决：`docker compose up -d`；确认 `application-secret.yml` 的密码 = `123456`、端口 `5433`（`application-local.yml`）。

### 9.5 设备/路灯页显示离线
- 属预期：光棚不依赖路灯模拟灯；离线由心跳超时（默认 180s）判定，不影响光棚演示。

### 9.6 前端构建报 `TS6133 unused variable`
- 工程历史遗留的未使用变量类型错误，不影响运行。
- 解决：按报错删除 `GreenhouseScene3D.vue` 等文件中对应的未使用声明后重跑 `npm run build`。

### 9.7 Java 版本不匹配
- 运行 jar 用 JDK 17+ 均可；**编译建议用 §6.2 的 Docker Maven（JDK 21）**，避免本机 JDK 版本差异导致失败。

### 9.8 想清空重来
```powershell
# 停容器并清数据卷（慎用，会清库）
docker compose down -v
# 清理本地容器/镜像
powershell -ExecutionPolicy Bypass -File scripts\docker-cleanup.ps1 -AlsoImages
```

---

## 10. 方式B：快速演示（仅需 Node，无需 Docker/后端）

只想快速看界面、做前端开发时用：

```powershell
cd web
# 确保 .env.local 里 VITE_API_MODE=mock（且无 BOM）
npm install
npm run dev
```

浏览器打开 http://localhost:5173，用演示账号登录（`admin`/`admin123` 等六账号，见登录页），所有页面用内置模拟数据，无需启动 Docker 与后端。

> 局限：无真实仿真推进、无实时数据、无后端接口；完整演示请用方式 A。

---

## 附：本文对应「项目提交清单」

| 清单项 | 对应交付 | 状态 |
|--------|----------|------|
| 项目源代码 | 仓库（后端 `smart-street-light-master/`、前端 `web/`） | ✅ 已有 |
| 数据库文件 | `sql/`（schema.sql / test-data.sql / migrations/*.sql） | ✅ 已有 |
| 可执行的包文件 | 后端 `target/*.jar`（按 §6 编译）；前端 `web/dist`（按 §7.2 构建） | ⚠️ 需现场构建 |
| **部署文档** | **本文 DEPLOY.md** | ✅ 本次交付 |
| 开发文档 | `docs/greenhouse/` 系列（见 [README.md](./README.md) 索引） | ✅ 已有 |
