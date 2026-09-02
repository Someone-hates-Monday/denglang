<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RiskLevel } from '../utils/risk'
import { RISK_LABEL } from '../utils/risk'
import { dateKey, formatDisplayDate, parseDateKey } from '../utils/datetime'

export interface DayMeta {
  /** 迷你柱（0–1 相对高度） */
  spark?: number[]
  /** 当日最高风险（告警/日志） */
  risk?: RiskLevel | null
  count?: number
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    dayMeta?: Record<string, DayMeta>
    mode?: 'spark' | 'risk' | 'both'
    label?: string
  }>(),
  {
    dayMeta: () => ({}),
    mode: 'both',
    label: '按日期筛选',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'month-change': [year: number, monthIndex: number]
}>()

const open = ref(false)
const view = ref(parseDateKey(props.modelValue || dateKey(new Date())))

watch(
  () => props.modelValue,
  (v) => {
    if (v) view.value = parseDateKey(v)
  },
)

const title = computed(
  () => `${view.value.getFullYear()}年${view.value.getMonth() + 1}月`,
)

const cells = computed(() => {
  const y = view.value.getFullYear()
  const m = view.value.getMonth()
  const first = new Date(y, m, 1)
  const startPad = (first.getDay() + 6) % 7 // Mon=0
  const daysInMonth = new Date(y, m + 1, 0).getDate()
  const list: { key: string | null; day: number | null }[] = []
  for (let i = 0; i < startPad; i++) list.push({ key: null, day: null })
  for (let d = 1; d <= daysInMonth; d++) {
    const key = dateKey(new Date(y, m, d))
    list.push({ key, day: d })
  }
  while (list.length % 7 !== 0) list.push({ key: null, day: null })
  return list
})

function prevMonth() {
  view.value = new Date(view.value.getFullYear(), view.value.getMonth() - 1, 1)
  emit('month-change', view.value.getFullYear(), view.value.getMonth())
}

function nextMonth() {
  view.value = new Date(view.value.getFullYear(), view.value.getMonth() + 1, 1)
  emit('month-change', view.value.getFullYear(), view.value.getMonth())
}

function pick(key: string) {
  emit('update:modelValue', key)
  open.value = false
}

function clear() {
  emit('update:modelValue', '')
  open.value = false
}

function toggle() {
  open.value = !open.value
  if (open.value) {
    emit('month-change', view.value.getFullYear(), view.value.getMonth())
  }
}

function sparkHeights(key: string): number[] {
  const s = props.dayMeta[key]?.spark
  if (!s?.length) return []
  const max = Math.max(0.01, ...s)
  return s.map((v) => Math.max(8, (v / max) * 100))
}
</script>

<template>
  <div class="cal-wrap">
    <button type="button" class="cal-trigger ui-btn ui-btn-secondary" @click="toggle">
      <span class="cal-ico" aria-hidden="true">▦</span>
      {{ modelValue ? formatDisplayDate(modelValue) : label }}
    </button>

    <Teleport to="body">
      <div v-if="open" class="cal-backdrop" @click="open = false" />
      <div v-if="open" class="cal-pop" role="dialog" aria-label="日历筛选">
        <header class="cal-head">
          <button type="button" class="nav" @click="prevMonth">‹</button>
          <strong>{{ title }}</strong>
          <button type="button" class="nav" @click="nextMonth">›</button>
        </header>

        <div class="weekdays">
          <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
        </div>

        <div class="grid">
          <button
            v-for="(c, i) in cells"
            :key="i"
            type="button"
            class="cell"
            :class="{
              empty: !c.key,
              selected: c.key === modelValue,
              today: c.key === dateKey(new Date()),
            }"
            :disabled="!c.key"
            @click="c.key && pick(c.key)"
          >
            <template v-if="c.key">
              <span class="day-num">{{ c.day }}</span>
              <span
                v-if="(mode === 'spark' || mode === 'both') && sparkHeights(c.key).length"
                class="spark"
              >
                <i
                  v-for="(h, j) in sparkHeights(c.key).slice(0, 6)"
                  :key="j"
                  :style="{ height: h + '%' }"
                />
              </span>
              <span
                v-if="(mode === 'risk' || mode === 'both') && dayMeta[c.key]?.risk"
                class="risk-dot"
                :data-risk="dayMeta[c.key].risk"
                :title="RISK_LABEL[dayMeta[c.key].risk!]"
              />
              <span v-else-if="dayMeta[c.key]?.count" class="count-dot" />
            </template>
          </button>
        </div>

        <footer class="cal-foot">
          <div class="legend">
            <span class="lg" data-risk="HIGH">高</span>
            <span class="lg" data-risk="MEDIUM">中</span>
            <span class="lg" data-risk="LOW">低</span>
          </div>
          <button type="button" class="ui-btn ui-btn-ghost ui-btn-compact" @click="clear">
            清除日期
          </button>
        </footer>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.cal-wrap {
  position: relative;
  display: inline-flex;
}

.cal-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cal-ico {
  opacity: 0.65;
  font-size: 12px;
}

.cal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(0, 0, 0, 0.18);
}

.cal-pop {
  position: fixed;
  z-index: 90;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: min(360px, calc(100vw - 32px));
  padding: 16px;
  background: var(--panel);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  animation: pop-in 0.28s var(--ease-spring) both;
}

@keyframes pop-in {
  from {
    opacity: 0;
    transform: translate(-50%, -46%) scale(0.96);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

.cal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.cal-head strong {
  font-size: var(--text-base);
}

.nav {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--paper);
  cursor: pointer;
  font-size: 18px;
  color: var(--ink);
}

.nav:hover {
  background: var(--accent-soft);
  color: var(--accent);
}

.weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--ink-muted);
  text-align: center;
}

.grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.cell {
  position: relative;
  aspect-ratio: 1;
  border: none;
  border-radius: 10px;
  background: var(--paper);
  cursor: pointer;
  padding: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-height: 44px;
}

.cell.empty {
  background: transparent;
  cursor: default;
}

.cell.today .day-num {
  color: var(--accent);
  font-weight: 700;
}

.cell.selected {
  background: var(--accent-soft);
  box-shadow: inset 0 0 0 1.5px var(--accent);
}

.cell:not(.empty):hover {
  background: #eef1f5;
}

.day-num {
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
}

.spark {
  display: flex;
  align-items: flex-end;
  gap: 1px;
  height: 14px;
  width: 100%;
  max-width: 28px;
}

.spark i {
  flex: 1;
  background: var(--sodium);
  border-radius: 1px;
  opacity: 0.85;
  min-width: 2px;
}

.risk-dot {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.risk-dot[data-risk='HIGH'] {
  background: var(--danger);
}
.risk-dot[data-risk='MEDIUM'] {
  background: var(--warning);
}
.risk-dot[data-risk='LOW'] {
  background: var(--online);
}

.count-dot {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink-muted);
}

.cal-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  gap: 8px;
}

.legend {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--ink-muted);
}

.lg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.lg::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.lg[data-risk='HIGH']::before {
  background: var(--danger);
}
.lg[data-risk='MEDIUM']::before {
  background: var(--warning);
}
.lg[data-risk='LOW']::before {
  background: var(--online);
}
</style>
