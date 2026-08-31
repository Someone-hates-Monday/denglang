<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { greenhouseApi, type GhDevice, type GhZone } from '../api/greenhouse'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import { ROLE_LABEL, normalizeRole } from '../auth/rbac'

type DeviceKind = '' | 'GROW_LAMP' | 'PAR_SENSOR' | 'SHADE_ACTUATOR'

const TYPE_LABEL: Record<string, string> = {
  GROW_LAMP: '补光灯',
  PAR_SENSOR: 'PAR 测点',
  SHADE_ACTUATOR: '遮阳执行器',
}

const auth = useAuthStore()
const canDebug = computed(() => auth.can('dev.debug'))
const realtime = useRealtimeStore()
const zones = ref<GhZone[]>([])
const records = ref<GhDevice[]>([])
const msg = ref('')
const err = ref('')
const busySn = ref<string | null>(null)
const filter = reactive({
  keyword: '',
  zoneId: '',
  deviceType: '' as DeviceKind,
  onlineStatus: '',
})
let poll: number | undefined

const zoneName = computed(() => {
  const map = new Map<string, string>()
  for (const z of zones.value) map.set(z.zoneId, z.name)
  return map
})

const filtered = computed(() => {
  const kw = filter.keyword.trim().toLowerCase()
  return records.value.filter((d) => {
    if (filter.zoneId && d.zoneId !== filter.zoneId) return false
    if (filter.deviceType && d.deviceType !== filter.deviceType) return false
    if (filter.onlineStatus && d.onlineStatus !== filter.onlineStatus) return false
    if (!kw) return true
    return (
      d.deviceSn.toLowerCase().includes(kw) ||
      d.deviceName.toLowerCase().includes(kw) ||
      d.zoneId.toLowerCase().includes(kw)
    )
  })
})

const grouped = computed(() => {
  const order = zones.value.map((z) => z.zoneId)
  const map = new Map<string, GhDevice[]>()
  for (const d of filtered.value) {
    const list = map.get(d.zoneId) ?? []
    list.push(d)
    map.set(d.zoneId, list)
  }
  const keys = [...map.keys()].sort((a, b) => {
    const ia = order.indexOf(a)
    const ib = order.indexOf(b)
    if (ia >= 0 && ib >= 0) return ia - ib
    if (ia >= 0) return -1
    if (ib >= 0) return 1
    return a.localeCompare(b)
  })
  return keys.map((zoneId) => {
    const devices = [...(map.get(zoneId) ?? [])].sort((a, b) =>
      a.deviceSn.localeCompare(b.deviceSn, 'en'),
    )
    return {
      zoneId,
      name: zoneName.value.get(zoneId) || zoneId,
      devices,
      lampCount: devices.filter((d) => d.deviceType === 'GROW_LAMP').length,
      sensorCount: devices.filter((d) => d.deviceType === 'PAR_SENSOR').length,
      shadeCount: devices.filter((d) => d.deviceType === 'SHADE_ACTUATOR').length,
    }
  })
})

const stats = computed(() => {
  const all = records.value
  return {
    total: all.length,
    lamps: all.filter((d) => d.deviceType === 'GROW_LAMP').length,
    sensors: all.filter((d) => d.deviceType === 'PAR_SENSOR').length,
    shades: all.filter((d) => d.deviceType === 'SHADE_ACTUATOR').length,
    online: all.filter((d) => d.onlineStatus === 'ONLINE').length,
  }
})

function typeLabel(type: string) {
  return TYPE_LABEL[type] || type
}

function posLabel(d: GhDevice) {
  if (d.posX == null || d.posY == null) return '—'
  const z = d.posZ != null ? ` · z ${d.posZ.toFixed(2)}` : ''
  return `x ${d.posX.toFixed(2)} · y ${d.posY.toFixed(2)}${z}`
}

function statusLabel(d: GhDevice) {
  if (d.deviceType === 'GROW_LAMP') {
    const pct = d.dimmingPercent ?? 0
    if (!d.powerOn && pct <= 0) return '关'
    return `调光 ${pct}%`
  }
  if (d.deviceType === 'SHADE_ACTUATOR') {
    return `开度 ${d.shadeOpenPercent ?? 0}%`
  }
  if (d.lastPpfd != null) return `${d.lastPpfd.toFixed(1)} PPFD`
  return '测点'
}

async function load() {
  err.value = ''
  try {
    const [zRes, dRes] = await Promise.all([greenhouseApi.zones(), greenhouseApi.devices()])
    if (zRes.code !== 200) throw new Error(zRes.errorMsg || '分区加载失败')
    if (dRes.code !== 200) throw new Error(dRes.errorMsg || '设备加载失败')
    zones.value = zRes.data || []
    records.value = dRes.data || []
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function setLampDimming(d: GhDevice, percent: number) {
  if (!canDebug.value) return
  if (!auth.can('ctrl.dim.high') && percent > 80) {
    msg.value = '本角色调光上限 80%，请走工单或维护窗'
    return
  }
  busySn.value = d.deviceSn
  msg.value = ''
  try {
    const res = await greenhouseApi.dimming(d.deviceSn, percent)
    msg.value =
      res.code === 200
        ? `${d.deviceName}（${d.deviceSn}）：调光 ${percent}%`
        : res.errorMsg || '调光失败'
    await load()
  } finally {
    busySn.value = null
  }
}

async function setShade(d: GhDevice, percent: number) {
  if (!canDebug.value) return
  busySn.value = d.deviceSn
  msg.value = ''
  try {
    const res = await greenhouseApi.shade(d.deviceSn, percent)
    msg.value =
      res.code === 200
        ? `${d.deviceName}（${d.deviceSn}）：遮阳开度 ${percent}%`
        : res.errorMsg || '遮阳调节失败'
    await load()
  } finally {
    busySn.value = null
  }
}

onMounted(() => {
  void load()
  poll = window.setInterval(() => void load(), 8000)
})
onUnmounted(() => {
  if (poll) window.clearInterval(poll)
})

watch(
  () => realtime.greenhouseTick,
  () => void load(),
)
</script>

<template>
  <div class="ui-page ui-page-fill devices-page">
    <div class="devices-toolbar slide-up-enter-active">
      <section class="ui-card toolbar-card">
        <h2 class="ui-card-title">棚内设备台账</h2>
        <p class="hint-line">
          身份：{{ ROLE_LABEL[normalizeRole(auth.role)] }} · 标识来自光棚布局（如
          <span class="mono">LAMP-ZONE-A-01</span> /
          <span class="mono">PAR-ZONE-B-03</span> /
          <span class="mono">SHADE-ZONE-A</span>），与冠层光场同一套设备。
        </p>
        <div class="stat-row">
          <div class="stat">
            <span class="stat-label">合计</span>
            <strong class="mono">{{ stats.total }}</strong>
          </div>
          <div class="stat">
            <span class="stat-label">补光灯</span>
            <strong class="mono">{{ stats.lamps }}</strong>
          </div>
          <div class="stat">
            <span class="stat-label">PAR</span>
            <strong class="mono">{{ stats.sensors }}</strong>
          </div>
          <div class="stat">
            <span class="stat-label">遮阳</span>
            <strong class="mono">{{ stats.shades }}</strong>
          </div>
          <div class="stat">
            <span class="stat-label">在线</span>
            <strong class="mono on">{{ stats.online }}/{{ stats.total }}</strong>
          </div>
        </div>
        <p class="hint-line">
          <RouterLink to="/greenhouse">打开冠层光场</RouterLink>
          查看三维灯位与光场。
        </p>
        <p v-if="msg" class="ui-msg">{{ msg }}</p>
        <p v-if="err" class="err">{{ err }}</p>
      </section>

      <section class="ui-card toolbar-card">
        <h2 class="ui-card-title">
          筛选
          <span class="count mono">({{ filtered.length }}/{{ records.length }})</span>
        </h2>
        <div class="ui-row">
          <input
            v-model="filter.keyword"
            class="ui-input"
            placeholder="名称 / SN / 分区"
          />
          <select v-model="filter.zoneId" class="ui-select">
            <option value="">分区 · 全部</option>
            <option v-for="z in zones" :key="z.zoneId" :value="z.zoneId">
              {{ z.zoneId }} · {{ z.name }}
            </option>
          </select>
          <select v-model="filter.deviceType" class="ui-select">
            <option value="">类型 · 全部</option>
            <option value="GROW_LAMP">补光灯</option>
            <option value="PAR_SENSOR">PAR 测点</option>
            <option value="SHADE_ACTUATOR">遮阳执行器</option>
          </select>
          <select v-model="filter.onlineStatus" class="ui-select">
            <option value="">在线 · 全部</option>
            <option value="ONLINE">ONLINE</option>
            <option value="OFFLINE">OFFLINE</option>
          </select>
          <button type="button" class="ui-btn ui-btn-secondary" @click="load">刷新</button>
        </div>
      </section>
    </div>

    <div class="ui-fill-body devices-body slide-up-enter-active slide-up-delay-1">
      <section class="ui-card groups-shell">
        <h2 class="ui-card-title">按栽培分区</h2>
        <p v-if="!grouped.length" class="ui-empty">暂无匹配的棚内设备</p>
        <div v-else class="ui-groups-stack">
          <section v-for="g in grouped" :key="g.zoneId" class="group-tray">
            <header class="group-head">
              <div class="group-title-block">
                <h3 class="group-title">
                  <span class="mono">{{ g.zoneId }}</span>
                  {{ g.name }}
                </h3>
                <p class="group-meta">
                  {{ g.devices.length }} 台 · 补光灯 {{ g.lampCount }} · PAR {{ g.sensorCount }} ·
                  遮阳 {{ g.shadeCount }}
                </p>
              </div>
            </header>

            <div class="ui-table-panel ui-table-panel--group">
              <div class="ui-table-wrap">
                <table class="ui-table">
                  <thead>
                    <tr>
                      <th>名称</th>
                      <th>设备 SN</th>
                      <th>类型</th>
                      <th>坐标 (m)</th>
                      <th>状态</th>
                      <th>在线</th>
                      <th class="col-actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="d in g.devices" :key="d.deviceSn">
                      <td class="col-name">{{ d.deviceName }}</td>
                      <td class="col-mono mono">{{ d.deviceSn }}</td>
                      <td>
                        <span class="type-pill" :data-type="d.deviceType">
                          {{ typeLabel(d.deviceType) }}
                        </span>
                      </td>
                      <td class="col-mono mono pos">{{ posLabel(d) }}</td>
                      <td>
                        <span
                          class="ui-pill"
                          :data-on="
                            d.deviceType === 'GROW_LAMP'
                              ? (d.dimmingPercent ?? 0) > 0
                              : d.deviceType === 'SHADE_ACTUATOR'
                                ? true
                                : d.onlineStatus === 'ONLINE'
                          "
                        >
                          {{ statusLabel(d) }}
                        </span>
                      </td>
                      <td class="col-mono">{{ d.onlineStatus || '—' }}</td>
                      <td class="col-actions">
                        <div v-if="!canDebug" class="hint-line">只读台账</div>
                        <div v-else-if="d.deviceType === 'GROW_LAMP'" class="ui-action-bar">
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact ui-btn-secondary"
                            :disabled="busySn === d.deviceSn"
                            @click="setLampDimming(d, 0)"
                          >
                            关灯
                          </button>
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact"
                            :disabled="busySn === d.deviceSn"
                            @click="setLampDimming(d, 40)"
                          >
                            40%
                          </button>
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact"
                            :disabled="busySn === d.deviceSn"
                            @click="setLampDimming(d, 70)"
                          >
                            70%
                          </button>
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact"
                            :disabled="busySn === d.deviceSn || !auth.can('ctrl.dim.high')"
                            @click="setLampDimming(d, 100)"
                          >
                            100%
                          </button>
                        </div>
                        <div v-else-if="d.deviceType === 'SHADE_ACTUATOR'" class="ui-action-bar">
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact ui-btn-secondary"
                            :disabled="busySn === d.deviceSn"
                            @click="setShade(d, 0)"
                          >
                            全闭
                          </button>
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact"
                            :disabled="busySn === d.deviceSn"
                            @click="setShade(d, 40)"
                          >
                            40%
                          </button>
                          <button
                            type="button"
                            class="ui-btn ui-btn-compact"
                            :disabled="busySn === d.deviceSn"
                            @click="setShade(d, 100)"
                          >
                            全开
                          </button>
                        </div>
                        <span v-else class="hint-line">只读测点</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.devices-toolbar {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
  flex-shrink: 0;
}

.toolbar-card .ui-card-title {
  margin-bottom: var(--space-3);
}

.devices-body {
  min-height: 0;
}

.hint-line {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.hint-line a {
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
}

.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.stat {
  min-width: 4.5rem;
  padding: 0.55rem 0.75rem;
  background: var(--paper);
  border-radius: var(--radius-md);
}

.stat-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--ink-muted);
  margin-bottom: 2px;
}

.stat strong {
  font-size: var(--text-lg);
}

.stat strong.on {
  color: var(--sodium-deep, #8a5a12);
}

.err {
  margin: 0;
  background: #f7e4d8;
  border-left: 3px solid #b85c38;
  padding: 0.55rem 0.75rem;
}

.count {
  font-size: var(--text-base);
  font-weight: 400;
  color: var(--ink-muted);
}

.groups-shell {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.groups-shell .ui-card-title {
  flex-shrink: 0;
  margin-bottom: var(--space-3);
}

.groups-shell .ui-groups-stack {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.group-tray {
  padding: var(--space-4);
  background: var(--panel-secondary);
  border: 1px solid var(--line);
  flex-shrink: 0;
}

.group-tray .ui-table-panel {
  box-shadow: none;
  border: 1px solid var(--line);
  background: var(--panel);
}

.group-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-bottom: var(--space-3);
}

.group-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
}

.group-meta {
  margin: 4px 0 0;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.pos {
  font-size: var(--text-xs);
  white-space: nowrap;
}

.type-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 600;
  background: var(--paper);
  color: var(--ink-muted);
}

.type-pill[data-type='GROW_LAMP'] {
  color: #8a5a12;
  background: #f6e7c8;
}

.type-pill[data-type='PAR_SENSOR'] {
  color: #2f5d50;
  background: #dceee7;
}

.type-pill[data-type='SHADE_ACTUATOR'] {
  color: #3d4f6f;
  background: #e2e8f2;
}

@media (max-width: 900px) {
  .devices-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
