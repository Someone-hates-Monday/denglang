# 智慧光棚 · 发布包 {{VERSION}}

一键在本机拉起：**PostgreSQL + EMQX + 后端 API + 前端静态站**。

## 这是什么

| 内容 | 说明 |
|------|------|
| `Start-Guangpeng.ps1` | **一键启动** |
| `Stop-Guangpeng.ps1` | 停止本机 jar / 前端（Docker 可保留） |
| `zhihui-guangpeng.jar` | 后端（JDK 21） |
| `web-dist/` | 前端生产构建（已指向 `http://localhost:8080`） |
| `infra/` | `docker-compose.yml` + 全套 SQL |
| `config/` | 密钥模板（首次启动自动生成 `application-secret.yml`） |
| `tools/init-db.ps1` | 建库与迁移（启动脚本会按需调用） |

协作仓（源码）：https://github.com/Someone-hates-Monday/zhihui-guangpeng  
本 zip 适合作为**课程提交的「项目发布包」**，与源码仓分离分发。

## 环境要求

1. **Windows 10/11** + PowerShell  
2. **Docker Desktop**（WSL2 后端可用）  
3. **JDK 21**（`java -version` 能看到 21）  
4. **Node.js 18+**（仅用于 `npx serve` 托管前端）

## 一键启动

解压后进入本目录，执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\Start-Guangpeng.ps1
```

成功后会自动打开浏览器：

- 前端：http://localhost:4173  
- 后端：http://localhost:8080  
- EMQX 控制台：http://localhost:18083 （`admin` / `public`）

### 演示账号

| 用户名 | 角色 | 密码 |
|--------|------|------|
| `admin` | 系统管理员 | `admin123` |
| `changzhang` | 场长 | `demo123` |
| `nongyi` | 农艺师 | `demo123` |
| `zhongzhi` | 种植员 | `demo123` |
| `yunwei` | 设备运维 | `demo123` |
| `xueyuan` | 学员 | `demo123` |

## 停止

```powershell
powershell -ExecutionPolicy Bypass -File .\Stop-Guangpeng.ps1
```

如需连数据库一起关掉：

```powershell
cd infra
docker compose down
```

清空数据卷（慎用）：

```powershell
docker compose down -v
```

## 强制重建数据库

默认若已有 `gh_*` 表则跳过初始化。需要洗库时：

```powershell
$env:GUANGPENG_FORCE_INIT = "1"
powershell -ExecutionPolicy Bypass -File .\Start-Guangpeng.ps1
```

## 验收清单（提交/答辩）

1. Docker 中 `streetlight-pg`、`streetlight-emqx` 为 Up  
2. 浏览器打开 :4173，用 `admin` / `admin123` 登录  
3. 进入「场务光场」，可见 3D 棚体与光场热力  
4. 切换 `nongyi` / `zhongzhi`，工单批准与接单按钮不同  
5. （可选）等待约 2 分钟，日型仿真推进  

## 常见问题

| 现象 | 处理 |
|------|------|
| 端口 8080/4173 被占用 | 先跑 `Stop-Guangpeng.ps1`，或关掉旧后端/前端 |
| `docker ps` 报错 / 引擎未起 | 打开 Docker Desktop，确认 WSL2 正常 |
| 登录失败 | 看 `.run\backend.log`；确认 `config\application-secret.yml` 密码为 `123456` |
| 前端空白或接口失败 | 确认后端已起；本包前端固定请求 `localhost:8080` |
| 想改密钥 / DB 密码 | 编辑 `config\application-secret.yml` 后重启 jar |

## 版本信息

- 产品名：智慧光棚  
- 工程 ID：`zhihui-guangpeng`  
- 发布版本：`{{VERSION}}`  
- 数据库名（历史兼容）：`smart-street-light`（仅内部连接串，不影响产品名）
