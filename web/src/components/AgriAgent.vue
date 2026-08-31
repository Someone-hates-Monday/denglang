<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { greenhouseApi, type GhEffectiveLight } from '../api/greenhouse'

/* ============================ 消息与界面状态 ============================ */
type Msg = { role: 'user' | 'agent'; text: string }

const open = ref(false)
const input = ref('')
const busy = ref(false)
const list = ref<Msg[]>([
  {
    role: 'agent',
    text: '你好，我是智慧光棚农艺助手。可以问我：\n· 铁皮石斛 / 草莓 / 金线莲的种植知识（光配方、温度、湿度）\n· 棚内实时光环境（PPFD、DLI、遮阳、湿度）\n试试输入“当前分区 PPFD 多少？”',
  },
])

/** 快捷提问（点选填入输入框） */
const quickQuestions = [
  '石斛光配方',
  '草莓怎么补光',
  '金线莲光配方',
  '当前 PPFD 多少',
  'DLI 是什么',
  '大棚多大',
  '有哪些角色账号',
  '工单怎么审批',
  '怎么接入设备',
  '遮阳怎么控制',
]

const listEl = ref<HTMLElement | null>(null)
let poll: number | undefined

/* ============================ 实时数据缓存 ============================ */
let live: GhEffectiveLight | null = null

async function loadLive() {
  try {
    const zoneId = 'ZONE-A'
    const el = await greenhouseApi.effectiveLight(zoneId)
    if (el.code === 200) live = el.data
  } catch {
    /* 后端不可用时保持 null，实时类问题走兜底文案 */
  }
}

/* ============================ 知识库（源自 docs/greenhouse 与 SQL 配方） ============================ */

type Knowledge = { keys: string[]; topic: string; answer: string; kind?: 'concept' }

/** 铁皮石斛 —— 主叙事作物 */
function dendrobiumRecipes(): Knowledge[] {
  const rec = live?.recipe
  const recipeLine = rec
    ? `当前绑定配方：${rec.cropNameZh}（${rec.stage}）目标带 ${rec.ppfdTargetMin}–${rec.ppfdTargetMax} PPFD，硬限 ${rec.ppfdHardMin}–${rec.ppfdHardMax} PPFD。`
    : ''
  return [
    {
      keys: ['石斛', '组培', '栽培', '光配方', 'ppfd', '光强', '光配方'],
      topic: '铁皮石斛光配方',
      answer:
        '铁皮石斛是本棚主叙事作物，分两个阶段配方（PPFD 为冠层光合有效辐射）：\n' +
        '· 组培（TISSUE）：目标带 60–70，硬限 50–90 µmol·m⁻²·s⁻¹，DLI ~2.2–3.0。\n' +
        '· 栽培（CULTIVATION）：目标带 90–120，硬限 70–140 µmol·m⁻²·s⁻¹，DLI ~3.9–5.2。\n' +
        '控光策略：低于硬限自动补光，高于硬限自动遮阳（欠光补、过光遮）。\n' +
        (recipeLine || '（当前未读到实时配方数据）'),
    },
    {
      keys: ['石斛', '温度', '湿度'],
      topic: '铁皮石斛温湿度',
      answer:
        '重庆冬雾寡照、夏强光，温室宜保温保湿。铁皮石斛喜高湿荫蔽环境：\n' +
        '· 湿度：宜维持较高空气相对湿度（雾化/喷雾）以减缓叶面蒸腾。\n' +
        '· 温度：忌强直射高温；夏季高强度日照需遮阳网（本棚外遮阳约遮 55%）降温。\n' +
        '当前棚内：' + envLine(),
    },
    {
      keys: ['石斛', '种植', '怎么', '养', '管理', '注意'],
      topic: '铁皮石斛种植建议',
      answer:
        '铁皮石斛种植要点（结合本棚设计）：\n' +
        '· 光：栽培阶段目标 90–120 PPFD，怕强直射（棚南侧正午光强，靠外遮阳挡）。\n' +
        '· 摆放：A区高架苗床，部分床上方设组培/炼苗搁架，双层叠栽提高空间利用率。\n' +
        '· 控光闭环：欠光自动补光，过光自动遮阳，大动作走农艺工单待审。',
    },
  ]
}

/** 设施草莓 —— 重庆寡照产量叙事 */
function strawberryRecipes(): Knowledge[] {
  return [
    {
      keys: ['草莓', '光配方', 'ppfd', '光强', 'dli', '补光'],
      topic: '设施草莓光配方',
      answer:
        '设施草莓（红颜等）是重庆冬寡照的产量叙事作物，喜光但畏冬季雾照：\n' +
        '· 目标 PPFD：约 250–400 µmol·m⁻²·s⁻¹，典型补光 ~150（视自然光缺口）。\n' +
        '· 目标 DLI：约 17–25 mol·m⁻²·d⁻¹（国际园艺指南）。\n' +
        '· 光效：研究发现 LED 补光相对对照可增产约 33–56%，采收提前约 10 天；\n' +
        '  常配红蓝光 9/1 光质，动态补光电能效率优于盲目高功率。\n' +
        '（当前 B 区默认为金线莲配方，草莓配方用于切换演示）',
    },
    {
      keys: ['草莓', '温度', '湿度', '管理', '种植'],
      topic: '设施草莓温湿度与种植',
      answer:
        '设施草莓（高架槽/基质栽培）：\n' +
        '· 温度：冬春补光提温利于促花坐果，注意昼温/夜温区分。\n' +
        '· 湿度：维持适宜空气湿度，避免灰霉病高发。\n' +
        '· 光×肥：营养期 DLI ~4–10、PPFD ~100–200；催花与光谱/低温耦合，\n' +
        '  氮肥 × DLI 交互显著——可演示“高氮降光”策略。',
    },
  ]
}

/** 台湾金线莲 —— 耐阴配方切换辅作物 */
function anoectochilusRecipes(): Knowledge[] {
  return [
    {
      keys: ['金线莲', '光配方', 'ppfd', '光强', 'dli', '耐阴'],
      topic: '台湾金线莲光配方',
      answer:
        '台湾金线莲是耐阴极限作物，适合配方切换演示：\n' +
        '· 目标 PPFD：约 25–35 µmol·m⁻²·s⁻¹，硬限 15–55。\n' +
        '· 目标 DLI：约 1.3–1.8 mol·m⁻²·d⁻¹（极低光照需求）。\n' +
        '· 常配重度遮阴、忌强直射——与“补光+遮阳”双执行器叙事一致。',
    },
    {
      keys: ['金线莲', '种植', '怎么', '养', '管理'],
      topic: '台湾金线莲种植',
      answer:
        '台湾金线莲喜荫蔽高湿环境，B区为中北床密植或草莓高架槽切换：\n' +
        '· 光：耐阴，PPFD 25–35 即可，强光需重遮阴。\n' +
        '· 湿度：需较高空气湿度，避免强光直晒萎蔫。\n' +
        '· 定位：作为耐阴对照与配方切换演示作物（金线莲 ⇄ 草莓），验证“作物-配方解耦”。',
    },
  ]
}

/** 通用/棚体/控制类 */
function generalRecipes(): Knowledge[] {
  return [
    {
      keys: ['配方', 'recipe', '切换', '怎么切', '绑定'],
      topic: '配方切换',
      answer:
        '配方切换在“冠层光场”页顶部的配方下拉框中完成。系统按“作物-配方”解耦设计：\n' +
        '切换后下一规则周期即用新目标带控制，无需改代码。\n' +
        '例如切到金线莲配方，目标带变为 25–35 PPFD；切回草莓则为 250–400 PPFD。',
    },
    {
      keys: ['自动', '补光', '遮阳', 'auto', '工单', '控制'],
      topic: '自动补光/遮阳与工单',
      answer:
        '本棚采用“测光 → 光配方 → 补光/遮阳闭环 → 农艺工单”链路：\n' +
        '· 有效 PPFD 低于硬限 → 自动补光（灯 dim 上调）。\n' +
        '· 有效 PPFD 高于硬限 → 自动遮阳（遮阳网开度下调）。\n' +
        '· 超过审批阈值的大动作会生成 PENDING 农艺工单，需场长批准/驳回后执行，保证可审计。',
    },
    {
      keys: ['大棚', '棚', '重庆', '气候', '仿真', '压缩'],
      topic: '大棚与仿真',
      answer:
        '本棚为 cq-demo-bay-v1，重庆示范单跨拱棚（16×7 m，长轴东西，南向采光）：\n' +
        '· 分区：ZONE-A 铁皮石斛、ZONE-B 金线莲/草莓切换。\n' +
        '· 重庆日型：冬雾寡照、夏正午强光驱动自然光项；一天压缩为 120 秒连续仿真。\n' +
        '· 外遮阳：北侧卷轴向南展开，覆盖半跨屋面投影（约遮 55%）。',
    },
  ]
}

/** 概念类：DLI / 光质 / VPD（源自 contracts/light-recipe.md） */
function conceptRecipes(): Knowledge[] {
  return [
    {
      keys: ['dli', '日积分', '光积分', '光累积', '累计光'],
      topic: 'DLI 光日积分',
      kind: 'concept',
      answer:
        'DLI（Daily Light Integral，每日光积分）= 冠层一天累计接收的光量子总量，单位 mol·m⁻²·d⁻¹。\n' +
        '估算公式：DLI ≈ PPFD（µmol·m⁻²·s⁻¹）× 光周期（h）× 0.0036。\n' +
        '例：石斛组培 12h × 65 PPFD ≈ 2.8 mol·m⁻²·d⁻¹；草莓目标 DLI 17–25 对应强补光需求。',
    },
    {
      keys: ['光质', '光谱', '红蓝', '波长', 'nm', '红光', '蓝光', '光谱比'],
      topic: '光质与光谱',
      kind: 'concept',
      answer:
        '本棚补光灯按 BOM 选智圣普 ZPDM651（红蓝 450+660 nm 或全光谱），0–10V/PWM 调光。\n' +
        '设施草莓常配红蓝光 9/1 光质；LED 补光相对对照可增产约 33–56%、采收提前约 10 天。\n' +
        '光谱与 PPFD/DLI 解耦设计：前端“三色光谱”通道可切换查看，不影响目标带控制。',
    },
    {
      keys: ['vpd', '蒸汽压差', '蒸腾', '叶面'],
      topic: 'VPD 蒸汽压差',
      kind: 'concept',
      answer:
        'VPD（饱和蒸汽压差）衡量空气“渴水”程度，越高叶面蒸腾越快。\n' +
        '配方可选门控：vpdHighKpa=1.4 时补光降至 0.85 倍；基质湿度 <25% 时禁止升调光，避免干热胁迫。\n' +
        'MVP 演示主要靠重庆日型派生温湿度，暂不作主控输入。',
    },
  ]
}

/** 棚体与布局类（源自 GREENHOUSE-LAYOUT.md / layouts JSON） */
function layoutRecipes(): Knowledge[] {
  return [
    {
      keys: ['多大', '尺寸', '面积', '棚体', '大棚', '几何', '结构', '脊高', '檐高', '网格', '坐标', '朝向'],
      topic: '棚体尺寸与结构',
      answer:
        'cq-demo-bay-v1 单跨拱棚：净长 16.0 m（东西）× 净宽 7.0 m（南北），檐高 2.8 m、脊高 3.8 m，\n' +
        '覆盖膜透光率 0.65；光场网格 32×14（约 0.5 m 一格）。\n' +
        '坐标约定：西南角为原点，+X 东、+Y 北、+Z 上；长轴东西、南向采光（正午光从南侧进入）。',
    },
    {
      keys: ['分区', 'zone', '床', '床位', 'a区', 'b区', '布局', '栽培'],
      topic: '功能分区与床位',
      answer:
        '两区布局：ZONE-A（X 0.5–7.5 m）铁皮石斛，ZONE-B（X 8.5–15.5 m）金线莲⇄草莓切换；\n' +
        '中央横通道 7.5–8.5 m 供人行/管线。\n' +
        '每区 3 条东西向床：南床（Y 1.0–1.8）、中床（Y 3.1–3.9）、北床（Y 5.2–6.0），床面抬高 0.2 m；\n' +
        '冠层测光面：A 区 0.5 m、B 区 0.45 m。南北自然光梯度：南床 ×1.06、北床 ×0.94。',
    },
    {
      keys: ['灯', '布灯', '灯位', '灯高', '吊灯', '灯具', '安装', '分床'],
      topic: '补光灯排布（v1.3）',
      answer:
        'v1.3 布灯：每床 3 灯、灯发光中心 Z=1.85 m（冠层净空约 0.95 m），光束半角 55°，床架有遮挡，\n' +
        '按床同步调光（分床控光）。\n' +
        '种子实装：A 区 4 灯（LAMP-ZONE-A-01~04，初始 20%）、B 区 3 灯（LAMP-ZONE-B-01~03，初始 10%）。',
    },
    {
      keys: ['遮阳', '遮阳网', '外遮阳', '档位', '开度', 'shade', '遮阴', '半跨'],
      topic: '外遮阳系统',
      answer:
        '外遮阳（脊下 Z≈3.50）分两半跨独立控制：SHADE-ZONE-A 覆盖 X 0–8、SHADE-ZONE-B 覆盖 X 8–16。\n' +
        '最大遮光 maxBlock=0.85；开度语义：shadeOpenPercent 100=网全收（透光最大）、0=网满展（遮光最大）。\n' +
        '执行器按 BOM 为创明众联 B 类（RS485 可运行到任意开度）；夏正午强光靠它降温降光。',
    },
  ]
}

/** 设备与硬件类（源自 HARDWARE-BOM.md） */
function hardwareRecipes(): Knowledge[] {
  return [
    {
      keys: ['传感器', '测点', 'par', '光照传感', '量子', 'sq500', '测光', '探头'],
      topic: 'PAR 光传感器',
      answer:
        '每床 3 个 PAR 测点，A/B 区各 3 个，与灯 XY 对齐、Z=测光面。\n' +
        '型号（BOM）：主 APOGEE SQ-500（PPFD 科研级）、辅建大仁科 RS-GZ-N01（lux/RS485）、\n' +
        '实训 BH1750（I2C）、备选 LI-COR LI-190R。演示默认 sim.par 适配器，切真机只改 adapterId。',
    },
    {
      keys: ['lux', '勒克斯', '换算', '转换', '系数', 'kx'],
      topic: 'lux → PPFD 换算',
      answer:
        'lux 与 PPFD 无统一精确系数（自然光与 LED 光谱不同）。仿真默认：\n' +
        '自然光 k≈0.0185、红蓝 LED 0.012–0.020（默认 0.015）、全光谱≈0.014（ppfd≈lux×k）。\n' +
        '石斛控制阈值一律按 PPFD 比较；若硬件只回 lux，由边缘/云侧先换算再入规则。',
    },
    {
      keys: ['设备', '型号', 'sn', '编号', '命名', '前缀', '适配器', 'adapter'],
      topic: '设备型号与 SN 约定',
      answer:
        'SN 前缀约定：PAR- 光照测点、LAMP- 补光灯、SHADE- 遮阳轴、ZONE- 分区、BED- 栽培床、ENV- 温湿度。\n' +
        '补光灯 ZPDM651（100–300 W，0–10V/PWM 调光），遮阳创明众联 B 类，调光链路 RS485→0–10V。\n' +
        '演示全走 sim.* 适配器；真机仅改设备档案 adapterId 即可切换。',
    },
  ]
}

/** 角色权限与协同类（源自 RBAC-ROLES.md） */
function rbacRecipes(): Knowledge[] {
  return [
    {
      keys: ['角色', '权限', '账号', '登录', '密码', '身份', 'rbac', '场长', '农艺师', '种植员', '运维', '学员', '系统管理员', '哪个'],
      topic: '六角色与演示账号',
      answer:
        '六类角色：场长 SITE_MANAGER（全局策略/报告）、农艺师 AGRONOMIST（配方/审批）、\n' +
        '种植员 GROWER（接单执行）、设备运维 DEVICE_OPS（设备/调试）、学员 TRAINEE（只读实训）、系统管理员 SYS_ADMIN（账号/仿真）。\n' +
        '演示账号：admin/admin123（系统）、changzhang/demo123（场长）、nongyi/demo123（农艺）、\n' +
        'zhongzhi/demo123（种植）、yunwei/demo123（运维）、xueyuan/demo123（学员）。',
    },
    {
      keys: ['工单', '审批', '待办', 'pending', 'approve', '接单', '执行', '完成', '驳回', '状态机', '门控'],
      topic: '工单状态机与审批',
      answer:
        '工单状态机：PENDING（待批）→ APPROVED（已批）→ IN_PROGRESS（接单）→ COMPLETED（完成），\n' +
        '可 REJECTED（驳回）；超时未接单可升级通知场长。\n' +
        '关键约定：approve 只批准、不直接下发执行器；种植员接单/显式 execute 后才真正下发，全程可审计。',
    },
    {
      keys: ['权限申请', '申请', '临时', 'grant', '维护窗', '紧急', '沙箱', '越权'],
      topic: '权限申请',
      answer:
        '越权/临时操作走权限申请（gh_perm_requests）。类型：\n' +
        'EMERGENCY_CTRL（紧急超阈值调光，限时 2h）、MAINT_WINDOW（运维维护窗，窗内暂停 AUTO）、\n' +
        'SANDBOX_ZONE（学员沙箱）、REPORT_EXPORT、ROLE_TEMP。\n' +
        '农艺/场长批准后写临时 grant，所有操作进审计；批准后可选自动生成工单。',
    },
    {
      keys: ['报告', '日报', '产量', '能耗', '经济', 'economics', '电费', '健康'],
      topic: '报告体系',
      answer:
        '报告四类：DAILY_LIGHT 日光照报告（DLI、目标达成、工单摘要、产量指数、电费估）、\n' +
        'ENERGY_YIELD 产量-能耗平衡复盘、DEVICE_HEALTH 设备健康（离线/标定）、TRAINING 学员实训。\n' +
        '日报告可引用当日工单 id、economics 快照与权限申请结果。',
    },
  ]
}

/** 系统接入与运行类（源自 contracts/mqtt.md / IMPLEMENT.md） */
function systemRecipes(): Knowledge[] {
  return [
    {
      keys: ['mqtt', '协议', '接入', '接入设备', '设备接入', '怎么接入', 'topic', '主题', 'broker', '遥测', '指令', '订阅', '物联网', '下发'],
      topic: 'MQTT 接入',
      answer:
        'MQTT Broker 用 EMQX（本地 :1883）。主题前缀 smart-greenhouse/：\n' +
        '{deviceSn}/telemetry 上行遥测、{deviceSn}/status 执行器状态、{deviceSn}/alarm 告警、\n' +
        '{deviceSn}/command 下行指令（SET_DIMMING / SET_OPEN_PERCENT / POWER_ON / POWER_OFF）。\n' +
        '指令执行 PENDING→SUCCESS|TIMEOUT|FAIL；调光容差 ±3%、遮阳 ±5%，超时 30s 判 TIMEOUT 并告警。',
    },
    {
      keys: ['端口', '端口号', 'api', '8080', '5173', '1883', '18083', 'emqx', '怎么跑', '启动', '本地', '地址'],
      topic: '端口与本地运行',
      answer:
        '本地联调端口：Web :5173、后端 API :8080、PostgreSQL :5433、EMQX MQTT :1883、\n' +
        'EMQX 控制台 :18083（admin/public）。\n' +
        '后端以 profiles=local,secret 启动；scripts/run-local.ps1 可一键起 Docker+建库+编译+启动。',
    },
    {
      keys: ['仿真', '模拟', '日型', '压缩', '时间', '一天', 'climate', '重庆', '重跑', 'reset'],
      topic: '仿真与重庆日型',
      answer:
        '重庆日型仿真：一天压缩为 120 秒连续推进（interval-ms 250，约 3 仿真分钟/步），\n' +
        'POST /greenhouse/sim/reset-day 可重跑今日。\n' +
        '日型：cq-winter-fog（冬雾寡照）/ cq-winter-clear / cq-summer-noon / cq-overcast，驱动自然光与南北梯度。',
    },
    {
      keys: ['规则', '闭环', '控制', 'cooldown', '冷却', '硬限', '目标带', '光周期', '逻辑', '怎么控'],
      topic: '控制规则细节',
      answer:
        '规则引擎每周期读区有效 PPFD：\n' +
        '· 高于硬限：优先降遮阳开度，仍高再降补光；\n' +
        '· 低于硬限：优先升补光（光周期内），遮阳过厚再收网；\n' +
        '· 目标带内做微调；带 60s cooldown 防抖。\n' +
        '大开度（≥80）变更进 PENDING 工单、禁直发；光周期外默认关灯（MVP）。',
    },
  ]
}

function envLine(): string {
  if (!live) return '（实时数据暂不可用）'
  return `湿度 ${live.humidityPct ?? '—'}%、温度 ${live.temperatureC ?? '—'}°C、遮阳开度 ${live.shadeOpenPercent}%、DLI 累计 ${live.dliSoFar}。`
}

/** 全部知识条目 */
function allKnowledge(): Knowledge[] {
  return [
    ...dendrobiumRecipes(),
    ...strawberryRecipes(),
    ...anoectochilusRecipes(),
    ...conceptRecipes(),
    ...layoutRecipes(),
    ...hardwareRecipes(),
    ...rbacRecipes(),
    ...systemRecipes(),
    ...generalRecipes(),
  ]
}

/* ============================ 规则匹配引擎 ============================ */

const ALL =
  '作物知识（石斛/草莓/金线莲配方）· 光环境（PPFD/DLI/遮阳/温湿度）· 棚体与布局 · 设备与硬件 · 角色权限与账号 · 工单与审批 · MQTT 接入 · 仿真与运行'

/** 查询归一化：转小写、去标点与空白，提升关键词命中率 */
function normalizeQuery(q: string): string {
  return q.toLowerCase().replace(/[\s，。！？、,.;:：；!?'"“”‘’（）()【】\[\]<>《》·-]+/g, '')
}

/** 从命中条目中挑评分最高者（命中关键词越多越好；同分时更长关键词优先；概念问法优先概念条目） */
function bestKnowledge(q: string): Knowledge | null {
  const nq = normalizeQuery(q)
  const askConcept = /(是什么|啥是|什么叫|定义|解释|概念|介绍)/.test(q)
  let best: Knowledge | null = null
  let bestScore = 0
  let bestKeyLen = 0
  for (const k of allKnowledge()) {
    let score = 0
    let maxLen = 0
    for (const key of k.keys) {
      if (nq.includes(normalizeQuery(key))) {
        score += 1
        const kl = key.length
        if (kl > maxLen) maxLen = kl
      }
    }
    if (askConcept && k.kind === 'concept') score += 1
    if (score > bestScore || (score === bestScore && maxLen > bestKeyLen)) {
      best = k
      bestScore = score
      bestKeyLen = maxLen
    }
  }
  return bestScore > 0 ? best : null
}

/** 实时光环境：仅明确「当前/现在/实时」意图时触发，避免抢答知识类问题 */
function liveRealTimeAnswer(q: string): string | null {
  const wantLive =
    /(实时|当前|现在|最新|此刻|目前)/.test(q) &&
    /(ppfd|光|dli|遮阳|湿度|温度|光照|环境)/.test(q)
  if (!wantLive) {
    return null
  }
  const r = live
  if (!r) {
    return '正在读取棚内实时光环境，请稍候再问我（需后端 greenhouse API 可用）。'
  }
  const parts = [
    `调控后有效 PPFD：${Number(r.effectivePpfd).toFixed(1)} µmol·m⁻²·s⁻¹`,
    `自然光贡献：${r.naturalPpfd ?? '—'} / 补光贡献：${r.ledPpfd ?? '—'}`,
    `遮阳开度：${r.shadeOpenPercent}%`,
    `DLI 累计：${r.dliSoFar}`,
    `湿度：${r.humidityPct ?? '—'}% · 温度：${r.temperatureC ?? '—'}°C`,
  ]
  if (r.recipe) {
    parts.push(
      `当前配方：${r.recipe.cropNameZh}（${r.recipe.stage}）目标带 ${r.recipe.ppfdTargetMin}–${r.recipe.ppfdTargetMax} PPFD`,
    )
  }
  return `🌡 当前 ZONE-A 棚内实时光环境：\n${parts.join('\n')}`
}

/* 默认兜底 */
function fallback(q: string): string {
  const topics = [...new Set(allKnowledge().map((k) => k.topic))]
  return `关于“${q}”，我暂时没找到完全匹配的知识。我可以解答：\n${ALL}\n例如：${topics.join('、')}。\n也可以试试“当前 PPFD 多少”查看实时光环境。`
}

/* ============================ 主入口 ============================ */
function answer(q: string): string {
  const t = q.trim()
  if (!t) return '请输入你想了解的作物或棚内问题。'

  /* 招呼语 */
  if (/(你好|您好|hello|hi|在吗|嗨)/i.test(t)) {
    return '你好！我是智慧光棚农艺助手。请问你想了解哪方面的内容？'
  }
  if (/(谢谢|感谢|thank)/i.test(t)) return '不客气，随时问我。😊'
  if (/(能|可以).*(什么|哪些)|你.*会|介绍.*自己/.test(t)) {
    return `我可以解答：\n${ALL}\n以及棚内实时光环境（PPFD、DLI、遮阳、温湿度）。`
  }

  /* 知识库优先匹配 */
  const k = bestKnowledge(t)
  if (k) return k.answer

  /* 实时光环境（仅明确时间词） */
  const liveAns = liveRealTimeAnswer(t)
  if (liveAns) return liveAns

  return fallback(t)
}

async function send() {
  const t = input.value.trim()
  if (!t || busy.value) return
  list.value.push({ role: 'user', text: t })
  input.value = ''
  busy.value = true
  try {
    await loadLive()
    const ans = answer(t)
    list.value.push({ role: 'agent', text: ans })
  } finally {
    busy.value = false
    scrollBottom()
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function scrollBottom() {
  setTimeout(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  }, 30)
}

/* ============================ 生命周期 ============================ */
onMounted(() => {
  loadLive()
  poll = window.setInterval(loadLive, 4000)
})
onUnmounted(() => {
  if (poll) window.clearInterval(poll)
})
</script>

<template>
  <!-- 悬浮聊天按钮 -->
  <button
    type="button"
    class="agent-fab"
    :class="{ on: open }"
    @click="open = !open"
    aria-label="农艺助手"
  >
    <span class="fab-icon">{{ open ? '✕' : '💬' }}</span>
    <span v-if="!open" class="fab-label">农艺助手</span>
  </button>

  <!-- 聊天面板 -->
  <Transition name="pop">
    <div v-if="open" class="panel" role="dialog" aria-label="农艺助手">
      <div class="head">
        <div class="head-brand">
          <span class="dot" />
          <div>
            <strong>智慧光棚 · 农艺助手</strong>
            <small>铁皮石斛 / 设施草莓 / 金线莲</small>
          </div>
        </div>
      </div>

      <div ref="listEl" class="body">
        <div
          v-for="(m, i) in list"
          :key="i"
          class="msg"
          :class="m.role"
        >
          <div class="bubble">{{ m.text }}</div>
        </div>
        <div v-if="busy" class="msg agent">
          <div class="bubble typing"><i /><i /><i /></div>
        </div>
      </div>

      <div class="foot">
        <div class="chips">
          <button
            v-for="q in quickQuestions"
            :key="q"
            type="button"
            class="chip"
            @click="input = q"
          >
            {{ q }}
          </button>
        </div>
        <div class="composer">
          <textarea
            v-model="input"
            rows="1"
            placeholder="问我关于大棚种植的问题…"
            @keydown="onKey"
          />
          <button type="button" class="send" :disabled="busy || !input.trim()" @click="send">
            发送
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* ========= 悬浮按钮 ========= */
.agent-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 90;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  border: none;
  border-radius: var(--radius-full);
  background: linear-gradient(145deg, #1e3a2e 0%, #2f5d4a 100%);
  color: #eef5ef;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 10px 26px rgba(20, 45, 34, 0.35);
  transition:
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast);
}
.agent-fab:hover {
  transform: translateY(-2px);
}
.agent-fab.on {
  background: var(--sodium);
}
.fab-icon {
  font-size: 18px;
  line-height: 1;
}
.fab-label {
  font-size: var(--text-sm);
}

/* ========= 面板 ========= */
.panel {
  position: fixed;
  right: 24px;
  bottom: 90px;
  z-index: 91;
  width: min(92vw, 380px);
  height: min(72vh, 520px);
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border-radius: var(--radius-lg);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
  overflow: hidden;
  border: 1px solid var(--line);
}

.head {
  padding: 14px 16px;
  background: linear-gradient(135deg, #1e3a2e 0%, #2f5d4a 100%);
  color: #eef5ef;
}
.head-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.head-brand strong {
  display: block;
  font-size: var(--text-sm);
  letter-spacing: var(--tracking-tight);
}
.head-brand small {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.8;
}
.dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--online, #34c759);
  box-shadow: 0 0 8px rgba(52, 199, 89, 0.6);
}

.body {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--paper);
}
.msg {
  display: flex;
}
.msg.user {
  justify-content: flex-end;
}
.bubble {
  max-width: 85%;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.agent .bubble {
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--ink);
  border-top-left-radius: 6px;
}
.msg.user .bubble {
  background: var(--accent);
  color: #fff;
  border-top-right-radius: 6px;
}

/* 打字动画 */
.bubble.typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.bubble.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-muted);
  animation: bounce 1.2s infinite ease-in-out;
}
.bubble.typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.bubble.typing i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.foot {
  padding: 10px 12px;
  border-top: 1px solid var(--line);
  background: var(--panel);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  border: 1px solid var(--line-strong);
  background: var(--paper);
  color: var(--ink-soft);
  border-radius: var(--radius-full);
  padding: 4px 11px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition:
    background var(--duration-fast),
    color var(--duration-fast);
}
.chip:hover {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent);
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.composer textarea {
  flex: 1 1 auto;
  resize: none;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  font: inherit;
  font-size: var(--text-sm);
  max-height: 90px;
  background: var(--panel-secondary);
  color: var(--ink);
}
.composer textarea:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}
.send {
  border: none;
  background: var(--accent);
  color: #fff;
  border-radius: var(--radius-sm);
  padding: 9px 14px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--duration-fast);
}
.send:disabled {
  background: var(--line-strong);
  cursor: not-allowed;
}

/* ========= 弹出动画 ========= */
.pop-enter-active,
.pop-leave-active {
  transition:
    opacity var(--duration-normal) var(--ease-out),
    transform var(--duration-normal) var(--ease-spring);
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.96);
}

@media (max-width: 560px) {
  .panel {
    right: 12px;
    left: 12px;
    bottom: 84px;
    width: auto;
    height: 72vh;
  }
}
</style>
