# -*- coding: utf-8 -*-
"""Rebuild Forest Ink deck: image-first, high space use, full feature coverage."""
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parent
SRC_TMPL = Path(r"c:\Users\Someone\Desktop\Prototype\wuliu-main\.cursor\skills\guizang-ppt-skill\assets\template.html")
html = SRC_TMPL.read_text(encoding="utf-8")

# Forest Ink theme
html = html.replace(
    """    --ink:#0a0a0b;
    --ink-rgb:10,10,11;
    --paper:#f1efea;
    --paper-rgb:241,239,234;
    --paper-tint:#e8e5de;
    --ink-tint:#18181a;""",
    """    /* 🌿 森林墨 */
    --ink:#1a2e1f;
    --ink-rgb:26,46,31;
    --paper:#f5f1e8;
    --paper-rgb:245,241,232;
    --paper-tint:#ece7da;
    --ink-tint:#253d2c;""",
    1,
)
html = html.replace(
    "<title>[必填] 替换为 PPT 标题 · Deck Title</title>",
    "<title>智慧光棚 · 答辩演示 · 森林墨 · 图主导</title>",
    1,
)
html = html.replace(
    "transition:transform .9s cubic-bezier(.77,0,.175,1)",
    "transition:transform .28s ease",
)

# Extra CSS for big screenshots
  /* image-first stage: no frame border, fill width */
  .slide.has-shot{padding:3.2vh 2.4vw 7.5vh 2.4vw!important}
  .shot-head{display:flex;justify-content:space-between;align-items:flex-end;gap:2vw;margin-bottom:.8vh;flex-shrink:0}
  .shot-head .h-md{font-size:2.2vw;margin:0;line-height:1.15}
  .shot-head .one-liner{font-family:var(--sans-zh);font-size:1.05vw;line-height:1.4;opacity:.72;margin:0;max-width:52vw;text-align:right}
  .shot-stage{flex:1;min-height:0;width:100%;overflow:hidden;background:transparent;border:none;border-radius:0;box-shadow:none}
  .shot-stage img{width:100%;height:100%;object-fit:cover;object-position:top center;display:block}
html = html.replace("</style>", extra_css + "\n</style>", 1)


def page(sid, theme, chrome_l, chrome_r, title, oneliner, img, foot_l, foot_r="— · —"):
    return f"""
<section class="slide {theme}" data-slide-id="{sid}">
  <div class="chrome"><div>{chrome_l}</div><div>{chrome_r}</div></div>
  <div class="frame" style="padding-top:1vh;display:flex;flex-direction:column;min-height:0;flex:1">
    <div class="shot-head">
      <h2 class="h-md">{title}</h2>
      <p class="one-liner">{oneliner}</p>
    </div>
    <div class="shot-stage"><img src="{img}" alt="{title}"></div>
  </div>
  <div class="foot"><div>{foot_l}</div><div>{foot_r}</div></div>
</section>
"""


slides = []

# 01 cover
slides.append("""
<section class="slide hero dark" data-slide-id="cover" data-animate="hero">
  <div class="chrome"><div>智慧光棚 · Defense</div><div>Forest Ink · 01 / 14</div></div>
  <div class="frame" style="display:grid;gap:2.4vh;align-content:center;min-height:78vh">
    <div class="kicker">图主导 · 设施农业光环境闭环</div>
    <h1 class="display-zh">智慧光棚</h1>
    <h2 class="h-sub">棚体光场 · 人员工单 · 顾问加分</h2>
    <p class="lead" style="max-width:62vw">感知 → 传输 → 平台 → 应用 → 执行。以大图讲清复杂界面：三维调控、分权工单、MQTT 台账、光棚顾问。</p>
    <div class="meta-row"><span>答辩演示</span><span>·</span><span>主题 · 森林墨</span><span>·</span><span>2026</span></div>
  </div>
  <div class="foot"><div>开场</div><div>— Forest Ink —</div></div>
</section>
""")

# 02 flow
slides.append(page(
    "flow", "light", "Narrative · 闭环", "02 / 14",
    "核心闭环", "测光 → 比对 → 调控 → 审批 → 可追溯；大开度进工单闸门",
    "img/14-flow-loop.png", "流程总览",
))

# 03 arch
slides.append(page(
    "arch", "dark", "Architecture · 分层", "03 / 14",
    "总体架构", "呈现 / 应用 / 数据 / 接入；Agent 只在应用层",
    "img/15-arch-forest.png", "森林墨架构图",
))

# 04 login roles
slides.append(page(
    "login", "light", "People · 入口", "04 / 14",
    "六角色入口", "场长 / 农艺 / 种植 / 运维 / 学员 / 管理员 — 各看各的界面",
    "img/02-login.png", "人员权限总览",
))

# 05 canopy
slides.append(page(
    "canopy", "dark", "Greenhouse · 光场", "05 / 14",
    "冠层三维光场", "灯位 · PPFD 热力 · 分区有效光 / DLI / 在线设备一眼可见",
    "img/03-greenhouse-3d.png", "棚体设计与光场",
))

# 06 field control
slides.append(page(
    "control", "light", "Control · 调控", "06 / 14",
    "现场调控", "配方切换 · 开度微调 · AUTO/手动；与三维光场同屏联动",
    "img/08-field-control.png", "核心调控面板",
))

# 07 work orders
slides.append(page(
    "wo", "dark", "Workflow · 工单", "07 / 14",
    "农艺工单", "≥80% 大开度待批；批准 ≠ 下发，种植员接单执行",
    "img/09-work-orders.png", "人员协同 · 工单",
))

# 08 day curve
slides.append(page(
    "curve", "light", "Climate · 日型", "08 / 14",
    "全日光照曲线", "重庆日型仿真推进；欠光补光 / 过光降灯可回放",
    "img/10-day-curve.png", "仿真日型",
))

# 09 strategy
slides.append(page(
    "strategy", "dark", "Rules · 策略", "09 / 14",
    "控光策略", "目标带、硬限与审批阈值沉淀在规则侧",
    "img/11-strategy.png", "规则与阈值",
))

# 10 devices
slides.append(page(
    "devices", "light", "Channel · 设备", "10 / 14",
    "棚内设备台账", "102 台灯 / PAR / 遮阳；与光场同一套 SN，可强制调光",
    "img/04-devices.png", "MQTT 执行通道",
))

# 11 logs
slides.append(page(
    "logs", "dark", "Trace · 日志", "11 / 14",
    "控制日志", "AUTO / MANUAL / 工单来源可筛；指令全程可追溯",
    "img/05-control-logs.png", "权责追溯",
))

# 12 users
slides.append(page(
    "users", "light", "RBAC · 角色", "12 / 14",
    "用户与角色", "六类职责分离：总览 / 审批 / 执行 / 运维 / 只读 / 管理",
    "img/06-users.png", "人员权限明细",
))

# 13 advisor
slides.append(page(
    "advisor", "dark", "Agent · 顾问", "13 / 14",
    "光棚顾问", "读棚况与规程作答；加分模块，不替代控光主链路",
    "img/07-advisor.png", "应用层 Agent",
))

# 14 close
slides.append("""
<section class="slide hero light" data-slide-id="close" data-animate="hero">
  <div class="chrome"><div>Takeaway · 交付</div><div>14 / 14</div></div>
  <div class="frame" style="display:grid;gap:2vh;align-content:center;min-height:78vh;max-width:80vw">
    <div class="kicker">可演示 · 可部署 · 可追问</div>
    <h1 class="h1-zh">重点回顾</h1>
    <ul style="list-style:none;display:grid;gap:1.2vh;font-family:var(--sans-zh);font-size:1.55vw;line-height:1.5;margin-top:1vh">
      <li>· <strong>棚体与调控</strong>：三维光场 + 现场调控 + 日型/策略</li>
      <li>· <strong>人员与工单</strong>：六角色分权 · 大开度闸门 · 日志追溯</li>
      <li>· <strong>执行通道</strong>：设备台账 · MQTT/EMQX · 可选 BearPi</li>
      <li>· <strong>Agent</strong>：光棚顾问只读加分，主链路不依赖大模型</li>
      <li>· 账号 <strong>admin / admin123</strong> · 发布包 v0.1.2 · 谢谢老师</li>
    </ul>
  </div>
  <div class="foot"><div>收束 · Q&A</div><div>— Forest Ink —</div></div>
</section>
""")

slides_html = "\n".join(slides)
if "<!-- SLIDES_HERE -->" not in html:
    raise SystemExit("marker missing")
html = html.replace("<!-- SLIDES_HERE -->", slides_html, 1)

notes = """const SPEAKER_NOTES = [
  {id:'cover',title:'开场',section:'开场',minutes:0.4,purpose:'建立图主导叙事',talk:['复杂界面用大图讲'],transition:'闭环图'},
  {id:'flow',title:'闭环',section:'总览',minutes:0.6,purpose:'五步闭环',talk:['工单闸门'],transition:'架构'},
  {id:'arch',title:'架构',section:'总览',minutes:0.6,purpose:'四层',talk:['Agent非脊柱'],transition:'角色'},
  {id:'login',title:'六角色',section:'人员',minutes:0.4,purpose:'入口',talk:[],transition:'光场'},
  {id:'canopy',title:'三维光场',section:'棚体',minutes:0.8,purpose:'核心卖点',talk:['PPFD/DLI'],transition:'调控'},
  {id:'control',title:'现场调控',section:'棚体',minutes:0.6,purpose:'人机调控',talk:[],transition:'工单'},
  {id:'wo',title:'工单',section:'人员',minutes:0.7,purpose:'权责分离',talk:['批准≠下发'],transition:'日型'},
  {id:'curve',title:'日曲线',section:'棚体',minutes:0.4,purpose:'仿真',talk:[],transition:'策略'},
  {id:'strategy',title:'策略',section:'棚体',minutes:0.4,purpose:'阈值',talk:[],transition:'设备'},
  {id:'devices',title:'设备',section:'通道',minutes:0.5,purpose:'MQTT',talk:['BearPi可选'],transition:'日志'},
  {id:'logs',title:'日志',section:'人员',minutes:0.4,purpose:'追溯',talk:[],transition:'角色表'},
  {id:'users',title:'角色表',section:'人员',minutes:0.4,purpose:'RBAC',talk:[],transition:'顾问'},
  {id:'advisor',title:'顾问',section:'加分',minutes:0.4,purpose:'Agent定位',talk:['主链路独立'],transition:'收束'},
  {id:'close',title:'收束',section:'收束',minutes:0.5,purpose:'Q&A',talk:['账号与发布包'],transition:'结束'}
];
window.__SPEAKER_NOTES__ = SPEAKER_NOTES;"""

html, n = re.subn(
    r"<script>\s*const SPEAKER_NOTES = \[[\s\S]*?window\.__SPEAKER_NOTES__ = SPEAKER_NOTES;\s*</script>",
    "<script>\n" + notes + "\n</script>",
    html,
    count=1,
)
if n != 1:
    raise SystemExit(f"notes replace failed {n}")

# ensure motion asset
assets = ROOT / "assets"
assets.mkdir(exist_ok=True)
motion_src = Path(r"c:\Users\Someone\Desktop\Prototype\wuliu-main\.cursor\skills\guizang-ppt-skill\assets\motion.min.js")
if motion_src.exists():
    shutil.copy2(motion_src, assets / "motion.min.js")

out = ROOT / "index.html"
out.write_text(html, encoding="utf-8")
print("WROTE", out, "slides", html.count('class="slide '), "bytes", out.stat().st_size)
