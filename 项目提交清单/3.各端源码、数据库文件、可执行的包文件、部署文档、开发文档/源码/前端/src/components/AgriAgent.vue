<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { greenhouseApi, type GhAgentChatResult } from '../api/greenhouse'
import { offlineAnswer } from '../agent/offlineFallback'
import { useAuthStore } from '../stores/auth'
import type { Role } from '../auth/rbac'

type Msg = {
  role: 'user' | 'agent'
  text: string
  meta?: { toolsUsed?: string[]; citations?: { title: string; source: string }[]; mode?: string }
}

const POS_KEY = 'gh.agent.fabPos.v2'
const FAB_W = 156
const FAB_H = 48
const MARGIN = 12
/** 相对左侧栏「用户」区域上方的默认间距 */
const DEFAULT_GAP_ABOVE_USER = 16

const auth = useAuthStore()
const open = ref(false)
const input = ref('')
const busy = ref(false)
const sessionId = ref<string | undefined>()
const listEl = ref<HTMLElement | null>(null)

/** left / bottom：默认在左侧栏用户卡片上方 */
const pos = ref({ left: 42, bottom: 200 })

const list = ref<Msg[]>([
  {
    role: 'agent',
    text: '你好，我是智慧光棚顾问（只读）。可汇总传感、电费/产量指数、工单与规程知识；调光与接单请到冠层/工单页。按钮可拖动换位置。',
  },
])

const QUICK_BY_ROLE: Record<string, string[]> = {
  SITE_MANAGER: ['当前各区光况摘要', '电费和产量指数怎样', '有多少待批工单', '今天下午要不要加强补光', 'DLI 是什么'],
  AGRONOMIST: ['当前配方硬限', '石斛光配方', '待批工单列表', '过光应该怎么控', '产量指数含义'],
  GROWER: ['待接单工单', '工单怎么审批', '当前 PPFD 多少', '现场要注意什么', 'claim 是什么意思'],
  DEVICE_OPS: ['设备在线情况', '有哪些活跃告警', '怎么接入设备', '当前遮阳开度', '灯具状态'],
  TRAINEE: ['大棚多大', '石斛光配方', 'DLI 是什么', '有哪些角色', '控光闭环是什么'],
  SYS_ADMIN: ['本地端口有哪些', 'MQTT 怎么接入', '当前 PPFD 多少', '仿真日型说明', '角色账号'],
}

const quickQuestions = computed(() => {
  const role = (auth.role || 'GROWER') as Role
  return QUICK_BY_ROLE[role] || QUICK_BY_ROLE.GROWER
})

const fabStyle = computed(() => ({
  left: `${pos.value.left}px`,
  bottom: `${pos.value.bottom}px`,
}))

const panelStyle = computed(() => {
  const vw = typeof window !== 'undefined' ? window.innerWidth : 1200
  const panelW = Math.min(vw * 0.92, 400)
  const maxLeft = Math.max(MARGIN, vw - panelW - MARGIN)
  const left = Math.min(Math.max(MARGIN, pos.value.left), maxLeft)
  return {
    left: `${left}px`,
    bottom: `${pos.value.bottom + FAB_H + 12}px`,
  }
})

function clampPos(left: number, bottom: number) {
  const vw = typeof window !== 'undefined' ? window.innerWidth : 1200
  const vh = typeof window !== 'undefined' ? window.innerHeight : 800
  const maxL = Math.max(MARGIN, vw - FAB_W - MARGIN)
  const maxB = Math.max(MARGIN, vh - FAB_H - MARGIN)
  return {
    left: Math.min(maxL, Math.max(MARGIN, left)),
    bottom: Math.min(maxB, Math.max(MARGIN, bottom)),
  }
}

/** 默认：左侧栏内、用户卡片上方留出纵向间距 */
function defaultPos() {
  const rail = document.querySelector('.rail') as HTMLElement | null
  const user = (document.querySelector('.rail .user-card') ||
    document.querySelector('.rail .foot')) as HTMLElement | null
  if (rail && user) {
    const rr = rail.getBoundingClientRect()
    const ur = user.getBoundingClientRect()
    const left = rr.left + Math.max(MARGIN, (rr.width - FAB_W) / 2)
    const bottom = window.innerHeight - ur.top + DEFAULT_GAP_ABOVE_USER
    return clampPos(left, bottom)
  }
  // 无侧栏时（窄屏）：左下偏上一点
  return clampPos(MARGIN + 8, 120)
}

function loadPos() {
  try {
    const raw = localStorage.getItem(POS_KEY)
    if (!raw) {
      pos.value = defaultPos()
      return
    }
    const parsed = JSON.parse(raw) as { left?: number; bottom?: number }
    if (typeof parsed.left === 'number' && typeof parsed.bottom === 'number') {
      pos.value = clampPos(parsed.left, parsed.bottom)
      return
    }
  } catch {
    /* fallthrough */
  }
  pos.value = defaultPos()
}

function savePos() {
  localStorage.setItem(POS_KEY, JSON.stringify(pos.value))
}

let drag: {
  pointerId: number
  startX: number
  startY: number
  originLeft: number
  originBottom: number
  moved: boolean
} | null = null

function onFabPointerDown(ev: PointerEvent) {
  if (ev.button !== 0) return
  const el = ev.currentTarget as HTMLElement
  el.setPointerCapture(ev.pointerId)
  drag = {
    pointerId: ev.pointerId,
    startX: ev.clientX,
    startY: ev.clientY,
    originLeft: pos.value.left,
    originBottom: pos.value.bottom,
    moved: false,
  }
}

function onFabPointerMove(ev: PointerEvent) {
  if (!drag || ev.pointerId !== drag.pointerId) return
  const dx = ev.clientX - drag.startX
  const dy = ev.clientY - drag.startY
  if (!drag.moved && Math.hypot(dx, dy) < 6) return
  drag.moved = true
  pos.value = clampPos(drag.originLeft + dx, drag.originBottom - dy)
}

function onFabPointerUp(ev: PointerEvent) {
  if (!drag || ev.pointerId !== drag.pointerId) return
  const wasDrag = drag.moved
  try {
    ;(ev.currentTarget as HTMLElement).releasePointerCapture(ev.pointerId)
  } catch {
    /* ignore */
  }
  drag = null
  if (wasDrag) {
    savePos()
    return
  }
  open.value = !open.value
}

function onResize() {
  pos.value = clampPos(pos.value.left, pos.value.bottom)
}

function scrollBottom() {
  setTimeout(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  }, 30)
}

function formatMeta(data: GhAgentChatResult): Msg['meta'] {
  return {
    toolsUsed: data.toolsUsed,
    citations: data.citations,
    mode: data.mode,
  }
}

async function send() {
  const t = input.value.trim()
  if (!t || busy.value) return
  list.value.push({ role: 'user', text: t })
  input.value = ''
  busy.value = true
  try {
    const res = await greenhouseApi.agentChat({
      sessionId: sessionId.value,
      message: t,
      zoneId: 'ZONE-A',
    })
    if (res.code === 200 && res.data?.reply) {
      sessionId.value = res.data.sessionId || sessionId.value
      list.value.push({
        role: 'agent',
        text: res.data.reply,
        meta: formatMeta(res.data),
      })
    } else {
      list.value.push({
        role: 'agent',
        text: offlineAnswer(t),
        meta: { mode: 'offline' },
      })
    }
  } catch {
    list.value.push({
      role: 'agent',
      text: offlineAnswer(t),
      meta: { mode: 'offline' },
    })
  } finally {
    busy.value = false
    scrollBottom()
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void send()
  }
}

function applyChip(q: string) {
  input.value = q
}

onMounted(() => {
  void nextTick(() => loadPos())
  window.addEventListener('resize', onResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <button
    type="button"
    class="agent-fab"
    :class="{ on: open, dragging: false }"
    :style="fabStyle"
    title="拖动换位置 · 单击打开/关闭"
    aria-label="光棚顾问"
    @pointerdown="onFabPointerDown"
    @pointermove="onFabPointerMove"
    @pointerup="onFabPointerUp"
    @pointercancel="onFabPointerUp"
  >
    <span class="fab-icon">{{ open ? '✕' : '💬' }}</span>
    <span v-if="!open" class="fab-label">光棚顾问</span>
    <span class="fab-grip" aria-hidden="true">⋮⋮</span>
  </button>

  <Transition name="pop">
    <div v-if="open" class="panel" role="dialog" aria-label="光棚顾问" :style="panelStyle">
      <div class="head">
        <div class="head-brand">
          <span class="dot" />
          <div>
            <strong>智慧光棚 · 顾问</strong>
            <small>只读 · 拖动按钮可换位</small>
          </div>
        </div>
      </div>

      <div ref="listEl" class="body">
        <div v-for="(m, i) in list" :key="i" class="msg" :class="m.role">
          <div class="bubble">{{ m.text }}</div>
          <div
            v-if="m.meta && (m.meta.toolsUsed?.length || m.meta.citations?.length || m.meta.mode)"
            class="meta"
          >
            <span v-if="m.meta.mode" class="tag">{{ m.meta.mode }}</span>
            <span v-if="m.meta.toolsUsed?.length" class="tag">
              工具 {{ m.meta.toolsUsed.join(', ') }}
            </span>
            <span v-for="(c, ci) in m.meta.citations || []" :key="ci" class="tag cite">
              {{ c.title }}
            </span>
          </div>
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
            @click="applyChip(q)"
          >
            {{ q }}
          </button>
        </div>
        <div class="composer">
          <textarea
            v-model="input"
            rows="1"
            placeholder="问棚况、电费预期、工单或规程…"
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
.agent-fab {
  position: fixed;
  z-index: 90;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px 12px 16px;
  border: none;
  border-radius: var(--radius-full);
  background: linear-gradient(145deg, #1e3a2e 0%, #2f5d4a 100%);
  color: #eef5ef;
  font: inherit;
  font-weight: 600;
  cursor: grab;
  touch-action: none;
  user-select: none;
  box-shadow: 0 10px 26px rgba(20, 45, 34, 0.35);
  transition: box-shadow var(--duration-fast);
}
.agent-fab:active {
  cursor: grabbing;
}
.agent-fab:hover {
  box-shadow: 0 12px 28px rgba(20, 45, 34, 0.42);
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
.fab-grip {
  font-size: 10px;
  letter-spacing: -2px;
  opacity: 0.55;
  margin-left: 2px;
}

.panel {
  position: fixed;
  z-index: 91;
  width: min(92vw, 400px);
  height: min(72vh, 540px);
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border-radius: var(--radius-lg);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28);
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--line) 80%, transparent);
}

.head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--panel) 88%, #1e3a2e);
}
.head-brand {
  display: flex;
  gap: 10px;
  align-items: center;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #3dba7a;
  box-shadow: 0 0 0 3px rgba(61, 186, 122, 0.25);
}
.head strong {
  display: block;
  font-size: var(--text-sm);
}
.head small {
  color: var(--muted);
  font-size: 12px;
}

.body {
  flex: 1;
  overflow: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.msg {
  display: flex;
  flex-direction: column;
  max-width: 92%;
}
.msg.user {
  align-self: flex-end;
  align-items: flex-end;
}
.msg.agent {
  align-self: flex-start;
}
.bubble {
  white-space: pre-wrap;
  word-break: break-word;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: var(--text-sm);
  line-height: 1.45;
  background: color-mix(in srgb, var(--panel-2, #f3f5f2) 100%, transparent);
  border: 1px solid var(--line);
}
.msg.user .bubble {
  background: #1e3a2e;
  color: #eef5ef;
  border-color: transparent;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.tag {
  font-size: 10px;
  color: var(--muted);
  background: color-mix(in srgb, var(--line) 35%, transparent);
  padding: 2px 6px;
  border-radius: 6px;
}
.tag.cite {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  min-height: 1.2em;
}
.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--muted);
  animation: blink 1s infinite ease-in-out;
}
.typing i:nth-child(2) {
  animation-delay: 0.15s;
}
.typing i:nth-child(3) {
  animation-delay: 0.3s;
}
@keyframes blink {
  0%,
  80%,
  100% {
    opacity: 0.35;
  }
  40% {
    opacity: 1;
  }
}

.foot {
  border-top: 1px solid var(--line);
  padding: 10px 12px 12px;
  background: color-mix(in srgb, var(--panel) 92%, transparent);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  max-height: 4.5rem;
  overflow: auto;
}
.chip {
  border: 1px solid var(--line);
  background: transparent;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--ink-soft);
  cursor: pointer;
}
.chip:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.composer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.composer textarea {
  flex: 1;
  resize: none;
  min-height: 38px;
  max-height: 88px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: var(--paper);
  color: var(--text);
  font: inherit;
}
.send {
  border: none;
  border-radius: 10px;
  padding: 8px 14px;
  background: #1e3a2e;
  color: #eef5ef;
  font-weight: 600;
  cursor: pointer;
}
.send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pop-enter-active,
.pop-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

@media (max-width: 640px) {
  .panel {
    width: min(96vw, 400px);
    height: min(68vh, 520px);
  }
}
</style>
