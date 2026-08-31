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

type Knowledge = { keys: string[]; topic: string; answer: string }

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
    ...generalRecipes(),
  ]
}

/* ============================ 规则匹配引擎 ============================ */

const ALL = '作物知识 / 棚体 / 光环境 / 配方 / 补光 / 遮阳'

/** 从命中条目中挑评分最高者（命中关键词越多越好） */
function bestKnowledge(q: string): Knowledge | null {
  let best: Knowledge | null = null
  let bestScore = 0
  for (const k of allKnowledge()) {
    const score = k.keys.reduce((s, key) => s + (q.includes(key) ? 1 : 0), 0)
    if (score > bestScore) {
      best = k
      bestScore = score
    }
  }
  return bestScore > 0 ? best : null
}

function liveRealTimeAnswer(q: string): string | null {
  if (/实时|当前|现在|最新/.test(q) || /ppfd|光强|dli|遮阳|湿度|温度|光照/.test(q)) {
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
  return null
}

/* 默认兜底 */
function fallback(q: string): string {
  const crop = /石斛|草莓|金线莲/ .test(q) ? `关于“${q}”` : `问题“${q}”`
  return `${crop}，我暂时没找到匹配的知识。我可以解答：\n${ALL}\n或者试试查实时数据（如“当前 PPFD”）。`
}

/* ============================ 主入口 ============================ */
function answer(q: string): string {
  const t = q.trim()
  if (!t) return '请输入你想了解的作物或棚内问题。'

  const liveAns = liveRealTimeAnswer(t)
  if (liveAns) return liveAns

  /* 招呼语 */
  if (/(你好|您好|hello|hi|在吗|嗨)/i.test(t)) {
    return '你好！我是智慧光棚农艺助手。请问你想了解哪方面的内容？'
  }
  if (/(谢谢|感谢|thank)/i.test(t)) return '不客气，随时问我。😊'
  if (/(能|可以).*(什么|哪些)|你.*会|介绍.*自己/.test(t)) {
    return `我可以解答：\n${ALL}\n以及棚内实时光环境（PPFD、DLI、遮阳、温湿度）。`
  }

  const k = bestKnowledge(t)
  if (k) return k.answer
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
        <button
          v-for="q in ['石斛光配方', '草莓怎么补光', '当前 PPFD 多少']"
          :key="q"
          type="button"
          class="chip"
          @click="input = q"
        >
          {{ q }}
        </button>
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
.chip {
  align-self: flex-start;
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
