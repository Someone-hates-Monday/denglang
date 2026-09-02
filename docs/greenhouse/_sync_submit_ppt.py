# -*- coding: utf-8 -*-
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[2]
src = root / "docs" / "greenhouse" / "defense-deck-forest"
pptx = root / "docs" / "greenhouse" / "defense-presentation-forest-v2.pptx"
dst4 = root / "项目提交清单" / "4.ppt与答辩视频"
html_dst = dst4 / "HTML演示稿-森林墨"

assert src.exists() and pptx.exists() and dst4.exists(), (src.exists(), pptx.exists(), dst4.exists())

if html_dst.exists():
    shutil.rmtree(html_dst)
html_dst.mkdir(parents=True)

shutil.copy2(src / "index.html", html_dst / "index.html")
shutil.copytree(src / "assets", html_dst / "assets")
shutil.copytree(src / "diagrams", html_dst / "diagrams")
shutil.copytree(src / "img", html_dst / "img")

target_pptx = dst4 / "智慧光棚-答辩演示.pptx"
shutil.copy2(pptx, target_pptx)

(dst4 / "说明.md").write_text(
    """# PPT 与答辩视频

- **推荐 PPT**：`智慧光棚-答辩演示.pptx`（森林墨 · 图主导 14 页，最新导出）
- **HTML 同源稿**：`HTML演示稿-森林墨/index.html`（浏览器打开；←→ 翻页；P 演讲者；B 静态）
- 结构：闭环/架构 → 六角色 → 三维光场/调控/工单/日型/策略 → 设备/日志/角色表 → 顾问 → 收束
- 右侧关键点 + 截图 contain 完整显示；答辩视频本次不提交
- 旧稿 `智慧光棚-答辩演示-旧版reveal.pptx` / `HTML演示稿-可选` 仅作对照，答辩请用森林墨版本
""",
    encoding="utf-8",
)

img_n = len(list((html_dst / "img").glob("*")))
print("html_dst", html_dst)
print("img files", img_n)
print("pptx", target_pptx.name, target_pptx.stat().st_size)
print(
    "html bytes",
    sum(p.stat().st_size for p in html_dst.rglob("*") if p.is_file()),
)
for p in sorted(dst4.iterdir(), key=lambda x: x.name):
    print("-", p.name, "DIR" if p.is_dir() else p.stat().st_size)
