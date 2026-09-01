# 发布包说明

本目录**不入库**大体量 jar / dist（见仓库根 `.gitignore` 的 `/release/`）。

请使用：

```powershell
# 在仓库根目录打包
powershell -ExecutionPolicy Bypass -File scripts\release\pack-release.ps1 -Version 0.1.0
```

产物：

- `release/zhihui-guangpeng-<ver>/` — 可解压即用的一键目录  
- `release/zhihui-guangpeng-<ver>.zip` — 提交 / GitHub Release 附件  

一键启动见包内 `README.md`（由 `scripts/release/RELEASE-README.md` 生成）。

已发布的二进制请到 GitHub Releases 下载：  
https://github.com/Someone-hates-Monday/zhihui-guangpeng/releases
