# 智慧光棚 · 发布包 {{VERSION}}

按下面顺序，在 **Windows PowerShell** 里**逐条复制运行**即可。  
不要跳步；每一步看到「成功/正常输出」再做下一步。

发布包下载：https://github.com/Someone-hates-Monday/zhihui-guangpeng/releases  
源码仓：https://github.com/Someone-hates-Monday/zhihui-guangpeng

---

## 第 0 步 · 打开终端

1. 开始菜单搜索 **PowerShell**，以普通用户打开即可。  
2. 建议先执行（允许本机脚本，只需做一次）：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

若提示确认，输入 `Y` 回车。

---

## 第 1 步 · 安装并启动 Docker Desktop

若已安装并常开 Docker，可跳到「检查」命令。

1. 安装（任选一种）：
   - 官网：https://www.docker.com/products/docker-desktop/  
   - 或 winget：

```powershell
winget install -e --id Docker.DockerDesktop
```

2. 安装后**打开 Docker Desktop**，等到状态为 Running（鲸鱼图标稳定）。  
3. 检查：

```powershell
docker version
docker ps
```

应能看到 Client / Server，且 `docker ps` 不报错。  
若报 `500` / 引擎失败：先装好 WSL2，再重启 Docker Desktop。

---

## 第 2 步 · 安装 JDK 21

```powershell
winget install -e --id Microsoft.OpenJDK.21
```

关闭并重新打开 PowerShell，再检查：

```powershell
java -version
```

输出里应出现 `21`。

---

## 第 3 步 · 安装 Node.js（用于托管前端页面）

```powershell
winget install -e --id OpenJS.NodeJS.LTS
```

重新打开 PowerShell，检查：

```powershell
node -v
npm -v
```

应显示版本号（建议 Node 18+）。

---

## 第 4 步 · 下载并解压发布包

### 方式 A · 浏览器（最简单）

1. 打开：https://github.com/Someone-hates-Monday/zhihui-guangpeng/releases/tag/v{{VERSION}}  
2. 下载 **`zhihui-guangpeng-{{VERSION}}.zip`**  
3. 解压到例如：`D:\apps\zhihui-guangpeng-{{VERSION}}\`  
4. 进入目录：

```powershell
cd D:\apps\zhihui-guangpeng-{{VERSION}}
```

（路径改成你实际解压位置。）

### 方式 B · 命令行下载（需已安装 GitHub CLI `gh`）

```powershell
cd $HOME\Downloads
gh release download v{{VERSION}} --repo Someone-hates-Monday/zhihui-guangpeng --pattern "zhihui-guangpeng-{{VERSION}}.zip"
Expand-Archive -Path .\zhihui-guangpeng-{{VERSION}}.zip -DestinationPath .\zhihui-guangpeng-{{VERSION}} -Force
cd .\zhihui-guangpeng-{{VERSION}}\zhihui-guangpeng-{{VERSION}}
```

若解压后多一层文件夹，用 `dir` 找到含有 `Start-Guangpeng.ps1` 的目录再 `cd` 进去：

```powershell
dir
# 确认能看到 Start-Guangpeng.ps1 、 zhihui-guangpeng.jar 、 web-dist
```

---

## 第 5 步 · 一键启动（核心）

在**包含 `Start-Guangpeng.ps1` 的目录**执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\Start-Guangpeng.ps1
```

首次会：

1. 启动 PostgreSQL、EMQX 容器  
2. 自动建库并导入演示数据（若库已存在则跳过）  
3. 启动后端 `:8080`  
4. 启动前端静态站 `:4173`  
5. 尝试自动打开浏览器  

看到类似「智慧光棚已启动」即成功。

### 打开页面

```powershell
Start-Process "http://localhost:4173"
```

或手动访问：

| 地址 | 用途 |
|------|------|
| http://localhost:4173 | **前端（请用这个登录）** |
| http://localhost:8080 | 后端 API |
| http://localhost:18083 | EMQX 控制台（`admin` / `public`） |

### 登录账号（复制用户名密码即可）

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 系统管理员 |
| `changzhang` | `demo123` | 场长 |
| `nongyi` | `demo123` | 农艺师 |
| `zhongzhi` | `demo123` | 种植员 |
| `yunwei` | `demo123` | 设备运维 |
| `xueyuan` | `demo123` | 学员 |

建议先：`admin` / `admin123` → 进入「场务光场」。

---

## 第 6 步 · 快速自检（可选但推荐）

```powershell
docker ps
Invoke-RestMethod http://localhost:8080/users/login -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
```

- `docker ps` 中应有 `streetlight-pg`、`streetlight-emqx`  
- 登录接口返回里 `code` 应为 `200`

---

## 第 7 步 · 停止服务

先停本机前后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\Stop-Guangpeng.ps1
```

若要连数据库容器一起关（仍在发布包目录下）：

```powershell
cd infra
docker compose down
cd ..
```

---

## 附录 A · 强制清空并重建数据库

会删除当前库数据，仅在需要「洗库重来」时使用：

```powershell
$env:GUANGPENG_FORCE_INIT = "1"
powershell -ExecutionPolicy Bypass -File .\Start-Guangpeng.ps1
```

---

## 附录 B · 常见问题（对着命令修）

### 端口被占用

```powershell
powershell -ExecutionPolicy Bypass -File .\Stop-Guangpeng.ps1
```

仍占用时可查看：

```powershell
Get-NetTCPConnection -LocalPort 8080,4173 -State Listen
```

### Docker 没起来

```powershell
# 打开 Docker Desktop 后重试
docker ps
```

### 启动失败看日志

```powershell
Get-Content .\.run\backend.log -Tail 50
Get-Content .\.run\backend.err -Tail 50
Get-Content .\.run\frontend.log -Tail 50
Get-Content .\.run\frontend.err -Tail 50
```

### 前端能开但登录失败

确认用的是 **4173** 而不是只开了 8080；并确认第 5 步后端探测成功。

---

## 包内文件说明（一般不用手动碰）

| 文件/目录 | 作用 |
|-----------|------|
| `Start-Guangpeng.ps1` | 一键启动 |
| `Stop-Guangpeng.ps1` | 停止 jar / 前端 |
| `zhihui-guangpeng.jar` | 后端 |
| `web-dist/` | 前端页面 |
| `infra/` | Docker 与 SQL |
| `config/` | 密钥（首次自动生成 `application-secret.yml`） |
| `tools/init-db.ps1` | 建库脚本（启动时按需调用） |

---

## 版本

- 产品：**智慧光棚**  
- 工程 ID：`zhihui-guangpeng`  
- 本包版本：`{{VERSION}}`
