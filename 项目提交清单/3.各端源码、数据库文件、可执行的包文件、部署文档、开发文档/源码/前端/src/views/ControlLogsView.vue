<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import CalendarFilter, { type DayMeta } from '../components/CalendarFilter.vue'
import {
  greenhouseApi,
  type GhAlarm,
  type GhControlLog,
} from '../api/greenhouse'
import { useRealtimeStore } from '../stores/realtime'
import { dateKey, extractDateKey } from '../utils/datetime'
import {
  alarmRisk,
  controlLogRisk,
  maxRisk,
  RISK_LABEL,
  type RiskLevel,
} from '../utils/risk'

const realtime = useRealtimeStore()

const allLogs = ref<GhControlLog[]>([])
const alarmPool = ref<GhAlarm[]>([])
const nameQuery = ref('')
const riskFilter = ref<'' | RiskLevel>('')
const selectedDay = ref('')
const sourceFilter = ref('')
const tab = ref<'logs' | 'alarms'>('logs')
const resolvingId = ref<number | null>(null)
const loadError = ref('')

function execLabel(status: string) {
  if (status === 'PENDING') return '等待回执'
  if (status === 'TIMEOUT') return '超时'
  if (status === 'SUCCESS') return '已确认'
  if (status === 'FAIL') return '失败'
  return status
}

function execClass(status: string) {
  if (status === 'PENDING') return 'pending'
  if (status === 'TIMEOUT') return 'timeout'
  if (status === 'SUCCESS') return 'ok'
  return ''
}

function sourceLabel(source: string) {
  if (source === 'AUTO') return '自动'
  if (source === 'MANUAL') return '手动'
  if (source === 'WORK_ORDER') return '工单'
  return source
}

function alarmTypeLabel(t: string) {
  const u = (t || '').toUpperCase()
  if (u === 'UNDER_PPFD') return '欠光'
  if (u === 'OVER_PPFD') return '过光'
  if (u === 'DEVICE_OFFLINE' || u === 'OFFLINE') return '离线'
  if (u === 'COMMAND_TIMEOUT') return '指令超时'
  return t
}

function riskClass(level: RiskLevel) {
  if (level === 'HIGH') return 'risk-high'
  if (level === 'MEDIUM') return 'risk-medium'
  return 'risk-low'
}

function expectedFromPayload(c: GhControlLog): string {
  if (!c.payloadJson) return '—'
  try {
    const p = JSON.parse(c.payloadJson) as Record<string, unknown>
    if (p.dimmingPercent != null) return `调光 ${p.dimmingPercent}%`
    if (p.shadeOpenPercent != null) return `遮阳 ${p.shadeOpenPercent}%`
  } catch {
    /* ignore */
  }
  return '—'
}

async function load() {
  loadError.value = ''
  try {
    const [logsRes, alarmRes] = await Promise.all([
      greenhouseApi.controlLogs({ limit: 200, source: sourceFilter.value || undefined }),
      greenhouseApi.alarms({ limit: 200 }),
    ])
    if (logsRes.code === 200) allLogs.value = logsRes.data ?? []
    else loadError.value = logsRes.errorMsg || '加载控制日志失败'
    if (alarmRes.code === 200) alarmPool.value = alarmRes.data ?? []
    else if (!loadError.value) loadError.value = alarmRes.errorMsg || '加载告警失败'
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '网络错误'
  }
}

async function resolveOne(id: number) {
  resolvingId.value = id
  try {
    const res = await greenhouseApi.resolveAlarm(id)
    if (res.code === 200) await load()
    else loadError.value = res.errorMsg || '消警失败'
  } finally {
    resolvingId.value = null
  }
}

/** 日历：优先用当日告警风险着色；无告警时用控制日志执行严重度 */
const dayMeta = computed<Record<string, DayMeta>>(() => {
  const meta: Record<string, DayMeta> = {}
  for (const a of alarmPool.value) {
    const k = extractDateKey(a.createdAt || '')
    if (!k) continue
    meta[k] = {
      risk: maxRisk(meta[k]?.risk, alarmRisk(a.alarmType)),
      count: (meta[k]?.count ?? 0) + 1,
    }
  }
  for (const c of allLogs.value) {
    const k = extractDateKey(c.createdAt || '')
    if (!k) continue
    if (!meta[k]?.risk) {
      meta[k] = {
        risk: maxRisk(meta[k]?.risk, controlLogRisk(c.executionStatus)),
        count: (meta[k]?.count ?? 0) + 1,
      }
    } else {
      meta[k] = { ...meta[k], count: (meta[k].count ?? 0) + 1 }
    }
  }
  return meta
})

const records = computed(() => {
  return allLogs.value.filter((c) => {
    if (nameQuery.value.trim()) {
      const q = nameQuery.value.trim()
      const hit =
        (c.deviceSn && c.deviceSn.includes(q)) ||
        (c.zoneId && c.zoneId.includes(q)) ||
        (c.command && c.command.includes(q))
      if (!hit) return false
    }
    if (selectedDay.value && extractDateKey(c.createdAt || '') !== selectedDay.value) return false
    if (riskFilter.value && controlLogRisk(c.executionStatus) !== riskFilter.value) return false
    return true
  })
})

const alarmRecords = computed(() => {
  return alarmPool.value.filter((a) => {
    if (nameQuery.value.trim()) {
      const q = nameQuery.value.trim()
      const hit =
        (a.deviceSn && a.deviceSn.includes(q)) ||
        (a.zoneId && a.zoneId.includes(q)) ||
        (a.message && a.message.includes(q)) ||
        (a.alarmType && a.alarmType.includes(q))
      if (!hit) return false
    }
    if (selectedDay.value && extractDateKey(a.createdAt || '') !== selectedDay.value) return false
    if (riskFilter.value && alarmRisk(a.alarmType) !== riskFilter.value) return false
    return true
  })
})

const activeAlarmCount = computed(
  () => alarmPool.value.filter((a) => a.status === 'ACTIVE').length,
)

onMounted(load)
watch(sourceFilter, load)
watch(
  () => realtime.alarmSyncTick,
  () => {
    void load()
  },
)

function jumpToday() {
  selectedDay.value = dateKey(new Date())
}
</script>

<template>
  <div class="ui-page ui-page-fill">
    <div class="ui-card fill-card slide-up-enter-active">
      <div class="head-row">
        <h2 class="ui-card-title">控制日志 · 光棚告警</h2>
        <div class="tabs">
          <button
            type="button"
            class="tab"
            :class="{ active: tab === 'logs' }"
            @click="tab = 'logs'"
          >
            控制日志
          </button>
          <button
            type="button"
            class="tab"
            :class="{ active: tab === 'alarms' }"
            @click="tab = 'alarms'"
          >
            告警
            <span v-if="activeAlarmCount" class="tab-badge">{{ activeAlarmCount }}</span>
          </button>
        </div>
      </div>

      <div class="ui-filter-bar">
        <input
          v-model="nameQuery"
          class="ui-input"
          :placeholder="tab === 'logs' ? '设备 SN / 分区 / 指令' : '设备 / 分区 / 类型'"
        />
        <select v-if="tab === 'logs'" v-model="sourceFilter" class="ui-select">
          <option value="">全部来源</option>
          <option value="AUTO">自动</option>
          <option value="MANUAL">手动</option>
          <option value="WORK_ORDER">工单</option>
        </select>
        <select v-model="riskFilter" class="ui-select">
          <option value="">全部风险</option>
          <option value="HIGH">高风险</option>
          <option value="MEDIUM">中风险</option>
          <option value="LOW">低风险</option>
        </select>
        <CalendarFilter
          v-model="selectedDay"
          mode="risk"
          label="日历筛选"
          :day-meta="dayMeta"
        />
        <button type="button" class="ui-btn ui-btn-secondary ui-btn-compact" @click="jumpToday">
          今天
        </button>
        <button type="button" class="ui-btn ui-btn-secondary ui-btn-compact" @click="load">
          刷新
        </button>
      </div>

      <p class="cal-note">
        数据来自光棚域
        <code>gh_control_logs</code>
        /
        <code>gh_alarms</code>
        。日历色点优先标记当日<strong>告警</strong>风险。
      </p>

      <p v-if="loadError" class="ui-empty err">{{ loadError }}</p>

      <div
        v-else-if="tab === 'logs' && records.length"
        class="ui-table-panel ui-table-panel--scroll ui-table-panel--fill"
      >
        <div class="ui-table-wrap">
          <table class="ui-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>设备</th>
                <th>分区</th>
                <th>指令</th>
                <th>来源</th>
                <th>风险</th>
                <th>执行</th>
                <th>期望</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in records" :key="c.id">
                <td class="col-time mono">{{ c.createdAt }}</td>
                <td class="col-name">{{ c.deviceSn || '—' }}</td>
                <td class="col-mono">{{ c.zoneId || '—' }}</td>
                <td class="col-mono mono">{{ c.command }}</td>
                <td class="col-mono">{{ sourceLabel(c.source) }}</td>
                <td>
                  <span :class="['ui-badge', riskClass(controlLogRisk(c.executionStatus))]">
                    {{ RISK_LABEL[controlLogRisk(c.executionStatus)] }}
                  </span>
                </td>
                <td class="col-mono">
                  <span :class="['ui-badge', execClass(c.executionStatus)]">
                    {{ execLabel(c.executionStatus) }}
                  </span>
                </td>
                <td class="col-mono mono">{{ expectedFromPayload(c) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div
        v-else-if="tab === 'alarms' && alarmRecords.length"
        class="ui-table-panel ui-table-panel--scroll ui-table-panel--fill"
      >
        <div class="ui-table-wrap">
          <table class="ui-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>类型</th>
                <th>分区</th>
                <th>设备</th>
                <th>风险</th>
                <th>状态</th>
                <th>说明</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in alarmRecords" :key="a.id">
                <td class="col-time mono">{{ a.createdAt }}</td>
                <td class="col-mono">{{ alarmTypeLabel(a.alarmType) }}</td>
                <td class="col-mono">{{ a.zoneId || '—' }}</td>
                <td class="col-name">{{ a.deviceSn || '—' }}</td>
                <td>
                  <span :class="['ui-badge', riskClass(alarmRisk(a.alarmType))]">
                    {{ RISK_LABEL[alarmRisk(a.alarmType)] }}
                  </span>
                </td>
                <td class="col-mono">{{ a.status }}</td>
                <td class="col-name">{{ a.message }}</td>
                <td>
                  <button
                    v-if="a.status === 'ACTIVE'"
                    type="button"
                    class="ui-btn ui-btn-secondary ui-btn-compact"
                    :disabled="resolvingId === a.id"
                    @click="resolveOne(a.id)"
                  >
                    消警
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <p v-else class="ui-empty">无匹配{{ tab === 'logs' ? '日志' : '告警' }}</p>
    </div>
  </div>
</template>

<style scoped>
.head-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.head-row .ui-card-title {
  margin: 0;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  position: relative;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink-muted);
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: var(--text-sm);
}

.tab.active {
  color: var(--ink);
  border-color: var(--ink-soft);
  background: color-mix(in srgb, var(--ink) 6%, transparent);
}

.tab-badge {
  margin-left: 6px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, #c45c26 85%, #000);
  color: #fff;
}

.cal-note {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  color: var(--ink-muted);
  line-height: 1.5;
}

.cal-note strong {
  color: var(--ink-soft);
  font-weight: 600;
}

.cal-note code {
  font-size: 0.95em;
}

.err {
  color: #b33;
}
</style>
