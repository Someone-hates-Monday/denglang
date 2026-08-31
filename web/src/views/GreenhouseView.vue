<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  greenhouseApi,
  type DaySeriesPoint,
  type GhAlarm,
  type GhDevice,
  type GhEffectiveLight,
  type GhRecipe,
  type GhWorkOrder,
  type GhZone,
} from '../api/greenhouse'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import GreenhouseScene3D from '../components/GreenhouseScene3D.vue'
import DayCurvesChart from '../components/DayCurvesChart.vue'
import RegionLightOverview from '../components/RegionLightOverview.vue'
import { type HeatChannel } from '../scene/spectrumModel'
import { ROLE_LABEL, normalizeRole, roleFocusZh } from '../auth/rbac'
import { RouterLink } from 'vue-router'
import {
  buildChartLayers,
  buildRegionRows,
  type ChartScope,
  type RegionRow,
} from '../scene/dayCurveLayers'
import {
  buildDeviceStatusMap,
  STATUS_LABEL_ZH,
  type DeviceSceneStatus,
} from '../scene/deviceStatus'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const route = useRoute()
const roleKey = computed(() => normalizeRole(auth.role))
const isTrainee = computed(() => roleKey.value === 'TRAINEE')
const isGrower = computed(() => roleKey.value === 'GROWER')
const isOps = computed(() => roleKey.value === 'DEVICE_OPS')
const canActuate = computed(
  () => auth.can('ctrl.shade') || auth.can('ctrl.dim.low'),
)
const roleBanner = computed(() => roleFocusZh(roleKey.value))

const zones = ref<GhZone[]>([])
const recipes = ref<GhRecipe[]>([])
const orders = ref<GhWorkOrder[]>([])
const devices = ref<GhDevice[]>([])
const alarms = ref<GhAlarm[]>([])
const selectedDeviceSn = ref<string | null>(null)
const handleNote = ref('')
const profiles = ref<Record<string, { id: string; labelZh: string }>>({})
const lights = ref<Record<string, GhEffectiveLight>>({})
const zoneId = ref('ZONE-A')
const showHeat = ref(true)
const heatChannel = ref<HeatChannel>('xray')
const heatChannels: HeatChannel[] = ['xray']
const lowerTab = ref<'none' | 'charts' | 'orders' | 'control' | 'strategy' | 'device'>('none')
type PanelId = typeof lowerTab.value
const chartScope = ref<ChartScope>('bay')
const chartFocusId = ref('BAY')
const chartFocusBedId = ref<string | undefined>()
const err = ref('')
let poll: number | undefined

const showStrategy = computed(
  () =>
    auth.can('recipe.bind') ||
    auth.can('climate.set') ||
    auth.can('auto.toggle') ||
    auth.can('sim.reset'),
)
const roleShort = computed(() => ROLE_LABEL[roleKey.value] || '')

const deviceStatuses = computed(() =>
  buildDeviceStatusMap(devices.value, orders.value, alarms.value),
)

const selectedDevice = computed(() => {
  const sn = selectedDeviceSn.value
  if (!sn) return null
  return devices.value.find((d) => d.deviceSn === sn) || null
})

const selectedStatus = computed((): DeviceSceneStatus | null => {
  const sn = selectedDeviceSn.value
  if (!sn) return null
  return deviceStatuses.value[sn] || null
})

const selectedAlarms = computed(() => {
  const ids = new Set(selectedStatus.value?.alarmIds || [])
  const sn = selectedDeviceSn.value
  return alarms.value.filter(
    (a) =>
      a.status === 'ACTIVE' &&
      (ids.has(a.id) || (sn && a.deviceSn === sn) || (selectedDevice.value && a.zoneId === selectedDevice.value.zoneId && !a.deviceSn)),
  )
})

const selectedOrders = computed(() => {
  const ids = new Set(selectedStatus.value?.workOrderIds || [])
  const sn = selectedDeviceSn.value
  return orders.value.filter(
    (o) =>
      ids.has(o.id) ||
      (sn &&
        o.targetDeviceSn === sn &&
        ['PENDING', 'APPROVED', 'IN_PROGRESS'].includes(o.status)),
  )
})

const attentionCount = computed(
  () =>
    Object.values(deviceStatuses.value).filter((s) => s.tone !== 'ok').length +
    alarms.value.filter((a) => a.status === 'ACTIVE').length,
)

function openPanel(id: Exclude<PanelId, 'none'>) {
  lowerTab.value = lowerTab.value === id ? 'none' : id
  if (lowerTab.value !== 'device') selectedDeviceSn.value = null
}

function closePanel() {
  lowerTab.value = 'none'
  selectedDeviceSn.value = null
}

function onSelectDevice(payload: { deviceSn: string; zoneId?: string; deviceType?: string }) {
  // 已打开详情时直接切换，不清空侧栏
  selectedDeviceSn.value = payload.deviceSn
  if (payload.zoneId) zoneId.value = payload.zoneId
  lowerTab.value = 'device'
  handleNote.value = ''
}

const pendingOrders = computed(() => orders.value.filter((o) => o.status === 'PENDING'))
const onlineCount = computed(() => devices.value.filter((d) => d.onlineStatus === 'ONLINE').length)
const bayDli = computed(() => {
  const a = lights.value['ZONE-A']?.dliSoFar
  const b = lights.value['ZONE-B']?.dliSoFar
  if (a == null && b == null) return null
  return Number((((a ?? 0) + (b ?? 0)) / (a != null && b != null ? 2 : 1)).toFixed(2))
})

const gapStatus = computed(() => {
  const el = light.value
  const dyn = el?.dynamicTarget
  if (!el || !dyn) return null
  const v = Number(el.effectivePpfd)
  if (dyn.photoperiodMask < 0.05) return { label: '光周期外', tone: 'muted' as const }
  if (v < dyn.instantMin - 2) return { label: '偏低 · 先开遮再补', tone: 'warn' as const }
  if (v > dyn.instantMax + 2) return { label: '偏高 · 先降灯', tone: 'warn' as const }
  return { label: '在目标带内', tone: 'ok' as const }
})

const economics = computed(() => light.value?.economics ?? null)
const shadeSteps = computed(() => economics.value?.shadeSteps ?? [100, 70, 40, 10])

const climateOptions = computed(() =>
  Object.values(profiles.value).map((p) => ({ id: p.id, label: p.labelZh })),
)

const light = computed(() => lights.value[zoneId.value] ?? null)

/** 整跨合成：西区 A、东区 B，设备两边都画 */
const bayLight = computed((): GhEffectiveLight | null => {
  const a = lights.value['ZONE-A']
  const b = lights.value['ZONE-B']
  const focus = light.value || a || b
  if (!focus) return null
  const aisle = 8
  const n = Math.max(a?.grid?.length ?? 0, b?.grid?.length ?? 0, focus.grid?.length ?? 0)
  const grid = []
  for (let i = 0; i < n; i++) {
    const fa = a?.grid?.[i]
    const fb = b?.grid?.[i]
    const base = fa || fb || focus.grid?.[i]
    if (!base) continue
    grid.push((base.x < aisle ? fa : fb) || base)
  }
  const deviceMap = new Map<string, NonNullable<GhEffectiveLight['devices']>[number]>()
  for (const d of [...(a?.devices || []), ...(b?.devices || [])]) {
    deviceMap.set(d.deviceSn, d)
  }
  const sensorPpfd = { ...(a?.sensorPpfd || {}), ...(b?.sensorPpfd || {}) }
  return {
    ...focus,
    lengthM: 16,
    widthM: 7,
    measurePlaneZ: Number(a?.measurePlaneZ ?? b?.measurePlaneZ ?? focus.measurePlaneZ) || 0.9,
    nx: focus.nx || a?.nx || 32,
    ny: focus.ny || a?.ny || 14,
    grid,
    devices: [...deviceMap.values()],
    sensorPpfd,
  }
})

const minuteOfDay = computed(() => light.value?.minuteOfDay ?? lights.value['ZONE-A']?.minuteOfDay ?? 0)
const dayPct = computed(() => Math.round((minuteOfDay.value / 1440) * 100))

const regionRows = computed(() => buildRegionRows(lights.value))
const hudRegions = computed(() => regionRows.value.filter((r) => r.kind === 'zone' || r.kind === 'bay'))

const chartBundle = computed(() =>
  buildChartLayers(chartScope.value, lights.value, zoneId.value, chartFocusBedId.value),
)

const controlLayers = computed(() => buildChartLayers('control', lights.value, zoneId.value).layers)

const gapLayers = computed(() => {
  if (chartScope.value === 'bay') {
    const layers: typeof chartBundle.value.layers = []
    for (const z of ['ZONE-A', 'ZONE-B'] as const) {
      const el = lights.value[z]
      if (!el?.series?.length) continue
      layers.push({
        id: `${z}-gap`,
        label: `${z === 'ZONE-A' ? '西半跨' : '东半跨'} 缺口 Δ`,
        color: z === 'ZONE-A' ? '#0071e3' : '#34c759',
        series: el.series,
        getValue: (p: DaySeriesPoint) => p.gapPpfd ?? 0,
        width: 2,
      })
    }
    const avg = buildChartLayers('bay', lights.value, zoneId.value).layers.find((l) => l.id === 'BAY-AVG')
    if (avg) {
      layers.push({
        ...avg,
        id: 'BAY-gap',
        label: '整跨平均缺口',
        getValue: (p: DaySeriesPoint) => p.gapPpfd ?? 0,
        dashed: true,
      })
    }
    return layers
  }
  if (chartScope.value === 'beds') {
    return chartBundle.value.layers.map((l) => ({
      ...l,
      getValue: (p: DaySeriesPoint) => {
        const v = p.bedPpfd?.[l.id]
        const mid = p.targetMid ?? ((p.targetPpfdMin ?? 0) + (p.targetPpfdMax ?? 0)) / 2
        return v != null ? v - mid : 0
      },
    }))
  }
  return buildChartLayers('zone', lights.value, zoneId.value).layers.filter((l) => l.id === 'gap')
})

const chartTitles: Record<ChartScope, string> = {
  bay: '整跨 · 东西半跨 PAR 对比',
  zone: '当前半跨 · 自然 / 补光 / 调控',
  beds: '六床分床 PAR',
  sensors: '单床三测点 PAR',
  control: '分区遮阳 / 补光策略',
}

function onRegionSelect(row: RegionRow) {
  chartFocusId.value = row.id
  if (row.kind === 'bay') {
    chartScope.value = 'bay'
    chartFocusBedId.value = undefined
  } else if (row.kind === 'zone' && row.zoneId) {
    chartScope.value = 'zone'
    zoneId.value = row.zoneId
    chartFocusBedId.value = undefined
  } else if (row.kind === 'bed' && row.bedId) {
    chartScope.value = 'beds'
    chartFocusBedId.value = row.bedId
    if (row.zoneId) zoneId.value = row.zoneId
  }
  lowerTab.value = 'charts'
}

function setChartScope(scope: ChartScope) {
  chartScope.value = scope
  if (scope === 'bay') {
    chartFocusId.value = 'BAY'
    chartFocusBedId.value = undefined
  } else if (scope === 'zone') {
    chartFocusId.value = zoneId.value
    chartFocusBedId.value = undefined
  } else if (scope === 'beds') {
    chartFocusId.value = chartFocusBedId.value ?? 'BED-A-M'
    chartFocusBedId.value = chartFocusId.value
  } else if (scope === 'sensors') {
    chartFocusBedId.value = chartFocusBedId.value ?? 'BED-A-M'
    chartFocusId.value = chartFocusBedId.value
  }
}
async function refresh() {
  err.value = ''
  try {
    const [z, r, o, p, d, a] = await Promise.all([
      greenhouseApi.zones(),
      greenhouseApi.recipes(),
      greenhouseApi.workOrders(),
      greenhouseApi.climateProfiles(),
      greenhouseApi.devices(),
      greenhouseApi.alarms({ status: 'ACTIVE', limit: 80 }),
    ])
    if (z.code !== 200) throw new Error(z.errorMsg || 'zones failed')
    zones.value = z.data || []
    recipes.value = r.data || []
    orders.value = o.data || []
    profiles.value = p.data || {}
    if (d.code === 200) devices.value = d.data || []
    if (a.code === 200) alarms.value = a.data || []

    const map: Record<string, GhEffectiveLight> = {}
    await Promise.all(
      (z.data || []).map(async (zone) => {
        const el = await greenhouseApi.effectiveLight(zone.zoneId)
        if (el.code === 200 && el.data) map[zone.zoneId] = el.data
      }),
    )
    lights.value = map
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

const dendrobiumRecipes = computed(() =>
  recipes.value.filter((r) => r.recipeId.toLowerCase().includes('dendrobium')),
)

const bayRecipeId = computed(() => {
  const a = lights.value['ZONE-A']?.recipeId
  const b = lights.value['ZONE-B']?.recipeId
  return a && b && a !== b ? a : a || b || ''
})

async function onRecipe(e: Event) {
  const id = (e.target as HTMLSelectElement).value
  await Promise.all([
    greenhouseApi.bindRecipe('ZONE-A', id),
    greenhouseApi.bindRecipe('ZONE-B', id),
  ])
  await refresh()
}

async function toggleAuto() {
  if (!light.value) return
  const next = !light.value.autoControl
  await Promise.all([
    greenhouseApi.setAuto('ZONE-A', next),
    greenhouseApi.setAuto('ZONE-B', next),
  ])
  await refresh()
}

async function onClimate(e: Event) {
  const id = (e.target as HTMLSelectElement).value
  await Promise.all([
    greenhouseApi.setClimate('ZONE-A', id),
    greenhouseApi.setClimate('ZONE-B', id),
  ])
  await refresh()
}

async function resetDay() {
  await greenhouseApi.resetDay()
  await refresh()
}

function snapShade(pct: number) {
  const steps = shadeSteps.value
  let best = steps[0]
  let dist = Math.abs(pct - best)
  for (const s of steps) {
    const d = Math.abs(pct - s)
    if (d < dist) {
      best = s
      dist = d
    }
  }
  return best
}

async function onShade(e: Event) {
  const pct = snapShade(Number((e.target as HTMLInputElement).value))
  const sn = zoneId.value === 'ZONE-B' ? 'SHADE-ZONE-B' : 'SHADE-ZONE-A'
  await greenhouseApi.shade(sn, pct)
  await refresh()
}

async function onDim(e: Event) {
  let pct = Number((e.target as HTMLInputElement).value)
  if (!auth.can('ctrl.dim.high') && pct > 80) pct = 80
  if (!auth.can('ctrl.dim.low')) return
  const lamps = (light.value?.devices || []).filter((d) => d.deviceType === 'GROW_LAMP')
  await Promise.all(lamps.map((d) => greenhouseApi.dimming(d.deviceSn, pct)))
  await refresh()
}

const avgDim = computed(() => {
  const lamps = (light.value?.devices || []).filter((d) => d.deviceType === 'GROW_LAMP')
  if (!lamps.length) return 0
  return Math.round(
    lamps.reduce((s, d) => s + (d.dimmingPercent ?? 0), 0) / lamps.length,
  )
})

async function approve(id: number) {
  if (!auth.can('wo.approve')) return
  await greenhouseApi.approve(id)
  await refresh()
}

async function reject(id: number) {
  if (!auth.can('wo.reject')) return
  await greenhouseApi.reject(id)
  await refresh()
}

async function claimOrder(id: number) {
  if (!auth.can('wo.claim')) return
  await greenhouseApi.claim(id)
  await refresh()
}

async function completeOrder(id: number) {
  if (!auth.can('wo.complete')) return
  await greenhouseApi.complete(id)
  handleNote.value = ''
  await refresh()
}

async function resolveSelectedAlarm(id: number) {
  if (!auth.can('log.view')) return
  await greenhouseApi.resolveAlarm(id)
  handleNote.value = ''
  await refresh()
}

function typeLabel(t?: string) {
  if (t === 'GROW_LAMP') return '补光灯'
  if (t === 'PAR_SENSOR') return 'PAR 测点'
  if (t === 'SHADE_ACTUATOR') return '遮阳执行器'
  return t || '设备'
}

function clockLabel(minute: number) {
  const total = Math.max(0, Math.floor(minute))
  const h = Math.floor(total / 60) % 24
  const m = total % 60
  const sec = Math.floor((minute % 1) * 60)
  if (sec > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  }
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

watch(chartFocusBedId, (id) => {
  if (id && (chartScope.value === 'sensors' || chartScope.value === 'beds')) {
    chartFocusId.value = id
  }
})

watch(
  () => realtime.greenhouseTick,
  () => refresh(),
)

onMounted(async () => {
  const tab = String(route.query.tab || '')
  if (tab === 'orders') lowerTab.value = 'orders'
  else if (tab === 'charts') lowerTab.value = 'charts'
  else if (tab === 'control') lowerTab.value = 'control'
  else if (tab === 'strategy') lowerTab.value = 'strategy'
  // 默认始终以 3D 场景为主，不预开侧栏
  await refresh()
  poll = window.setInterval(refresh, 1200)
})

onUnmounted(() => {
  if (poll) window.clearInterval(poll)
})
</script>
<template>
  <div
    class="ui-page ui-page-fill gh"
    :class="{
      'panel-open': lowerTab !== 'none',
      'role-trainee': isTrainee,
      'role-grower': isGrower,
      'role-ops': isOps,
    }"
  >
    <p v-if="err" class="err">{{ err }} · 请确认后端已启动（:8080）</p>

    <header class="gh-bar">
      <div class="gh-bar-left">
        <span class="role-pill" :title="roleBanner">{{ roleShort }}</span>
        <label class="field inline">
          <span>分区</span>
          <select v-model="zoneId" class="ui-select">
            <option v-for="z in zones" :key="z.zoneId" :value="z.zoneId">{{ z.name }}</option>
          </select>
        </label>
        <button
          v-if="auth.can('gh.heat')"
          type="button"
          class="ui-btn ui-btn-ghost ui-btn-compact"
          :data-on="showHeat"
          @click="showHeat = !showHeat"
        >
          光场热力 {{ showHeat ? '开' : '关' }}
        </button>
      </div>
      <div class="gh-bar-right mono" v-if="light || lights['ZONE-A']">
        <strong>{{ clockLabel(minuteOfDay) }}</strong>
        <div class="track" aria-hidden="true"><i :style="{ width: dayPct + '%' }" /></div>
        <span class="muted">日进度 {{ dayPct }}%</span>
      </div>
    </header>

    <div class="gh-body">
      <section class="stage" aria-label="冠层三维光场">
        <div class="stage-scene">
          <GreenhouseScene3D
            :light="bayLight"
            :zone-lights="lights"
            :focus-zone-id="zoneId"
            :shade-open-a="lights['ZONE-A']?.shadeOpenPercent ?? 100"
            :shade-open-b="lights['ZONE-B']?.shadeOpenPercent ?? 100"
            :show-heat="showHeat"
            :heat-channel="heatChannel"
            :device-statuses="deviceStatuses"
            :selected-device-sn="selectedDeviceSn"
            @select-device="onSelectDevice"
          />
        </div>

        <div class="glance" aria-label="关键指标">
          <div class="glance-item">
            <span class="k">有效光</span>
            <strong class="mono">{{ light ? Number(light.effectivePpfd).toFixed(0) : '—' }}</strong>
            <span class="u">µmol</span>
          </div>
          <div class="glance-item" v-if="gapStatus">
            <span class="k">光态</span>
            <strong class="gap" :data-tone="gapStatus.tone">{{ gapStatus.label }}</strong>
          </div>
          <div class="glance-item">
            <span class="k">整跨 DLI</span>
            <strong class="mono">{{ bayDli ?? '—' }}</strong>
          </div>
          <div class="glance-item">
            <span class="k">设备</span>
            <strong class="mono">{{ onlineCount }}/{{ devices.length || '—' }}</strong>
            <span class="u">在线</span>
          </div>
          <div class="glance-item" v-if="attentionCount" data-warn="true">
            <span class="k">待关注</span>
            <strong>{{ attentionCount }}</strong>
            <span class="u">告警/工单光晕</span>
          </div>
          <button
            v-for="row in hudRegions.filter((r) => r.kind === 'zone')"
            :key="row.id"
            type="button"
            class="glance-region"
            :data-status="row.status"
            @click="onRegionSelect(row)"
          >
            <span>{{ row.label }}</span>
            <strong class="mono">{{ row.ppfd.toFixed(0) }}</strong>
          </button>
        </div>

        <div class="status-legend" aria-label="状态光晕图例">
          <span><i data-tone="alarm" />告警</span>
          <span><i data-tone="offline" />离线</span>
          <span><i data-tone="wo-pending" />待审</span>
          <span><i data-tone="wo-approved" />待接</span>
          <span><i data-tone="wo-progress" />执行中</span>
        </div>

        <nav class="dock" aria-label="功能入口">
          <button
            type="button"
            class="dock-btn"
            :data-on="lowerTab === 'charts'"
            @click="openPanel('charts')"
          >
            日曲线
          </button>
          <button
            v-if="auth.can('wo.list')"
            type="button"
            class="dock-btn"
            :data-on="lowerTab === 'orders'"
            @click="openPanel('orders')"
          >
            工单
            <span v-if="pendingOrders.length" class="badge">{{ pendingOrders.length }}</span>
          </button>
          <button
            v-if="canActuate"
            type="button"
            class="dock-btn"
            :data-on="lowerTab === 'control'"
            @click="openPanel('control')"
          >
            现场调控
          </button>
          <button
            v-if="showStrategy"
            type="button"
            class="dock-btn"
            :data-on="lowerTab === 'strategy'"
            @click="openPanel('strategy')"
          >
            策略
          </button>
          <RouterLink v-if="isTrainee" class="dock-link" to="/reports">实训报告</RouterLink>
          <RouterLink v-if="isOps" class="dock-link" to="/devices">设备调试</RouterLink>
          <button
            v-if="lowerTab !== 'none'"
            type="button"
            class="dock-btn dock-close"
            @click="closePanel"
          >
            收起
          </button>
        </nav>
      </section>

      <aside v-if="lowerTab !== 'none'" class="side-panel" :data-panel="lowerTab">
        <header class="side-head">
          <h2>
            <template v-if="lowerTab === 'charts'">日曲线</template>
            <template v-else-if="lowerTab === 'orders'">{{
              isTrainee ? '工单观察' : isGrower ? '我的工单' : '农艺工单'
            }}</template>
            <template v-else-if="lowerTab === 'control'">现场调控</template>
            <template v-else-if="lowerTab === 'device'">设备处置</template>
            <template v-else>策略与气候</template>
          </h2>
          <button type="button" class="ui-btn ui-btn-ghost ui-btn-compact" @click="closePanel">
            关闭
          </button>
        </header>

        <div class="side-body" v-if="lowerTab === 'device'">
          <p class="side-lead">
            在三维中点击带光晕的设备；悬停看摘要，此处可直接处理告警与工单。
          </p>
          <div class="device-card" v-if="selectedDeviceSn">
            <p class="eyebrow">{{ typeLabel(selectedDevice?.deviceType || selectedStatus?.deviceType) }}</p>
            <h3 class="mono">{{ selectedDeviceSn }}</h3>
            <p class="meta">
              {{ selectedDevice?.deviceName || '布局标记' }} ·
              {{ selectedDevice?.zoneId || selectedStatus?.zoneId || '—' }} ·
              {{ selectedDevice?.onlineStatus || '—' }}
            </p>
            <p class="tone-pill" v-if="selectedStatus" :data-tone="selectedStatus.tone">
              {{ selectedStatus.labelZh || STATUS_LABEL_ZH[selectedStatus.tone] }}
            </p>
          </div>

          <label class="field note-field">
            <span>处理备注（本地备忘，随操作清空）</span>
            <textarea
              v-model="handleNote"
              class="ui-input"
              rows="2"
              placeholder="如：已现场确认 / 改派种植员…"
            />
          </label>

          <section class="device-section">
            <h4>关联告警</h4>
            <ul v-if="selectedAlarms.length" class="order-list">
              <li v-for="a in selectedAlarms" :key="a.id">
                <div>
                  <span class="st" data-s="PENDING">{{ a.alarmType }}</span>
                  <strong>{{ a.message }}</strong>
                  <p class="mono">{{ a.deviceSn || a.zoneId || '—' }} · #{{ a.id }}</p>
                </div>
                <div class="acts" v-if="auth.can('log.view')">
                  <button
                    type="button"
                    class="ui-btn ui-btn-compact"
                    @click="resolveSelectedAlarm(a.id)"
                  >
                    消警
                  </button>
                </div>
              </li>
            </ul>
            <p v-else class="empty">无活动告警</p>
          </section>

          <section class="device-section">
            <h4>关联工单</h4>
            <ul v-if="selectedOrders.length" class="order-list">
              <li v-for="o in selectedOrders" :key="o.id">
                <div>
                  <span class="st" :data-s="o.status">{{ o.status }}</span>
                  <strong class="mono">{{ o.zoneId }}</strong>
                  <p class="mono" v-if="o.targetDeviceSn">{{ o.targetDeviceSn }}</p>
                  <p>{{ o.reason }}</p>
                </div>
                <div class="acts" v-if="!isTrainee && o.status === 'PENDING' && auth.can('wo.approve')">
                  <button type="button" class="ui-btn ui-btn-compact" @click="approve(o.id)">
                    批准
                  </button>
                  <button
                    type="button"
                    class="ui-btn ui-btn-ghost ui-btn-compact"
                    @click="reject(o.id)"
                  >
                    驳回
                  </button>
                </div>
                <div
                  class="acts"
                  v-else-if="!isTrainee && o.status === 'APPROVED' && auth.can('wo.claim')"
                >
                  <button type="button" class="ui-btn ui-btn-compact" @click="claimOrder(o.id)">
                    接单执行
                  </button>
                </div>
                <div
                  class="acts"
                  v-else-if="!isTrainee && o.status === 'IN_PROGRESS' && auth.can('wo.complete')"
                >
                  <button type="button" class="ui-btn ui-btn-compact" @click="completeOrder(o.id)">
                    完成
                  </button>
                </div>
              </li>
            </ul>
            <p v-else class="empty">无待办工单（工单光晕多在遮阳执行器上）</p>
          </section>

          <div class="strategy-actions" v-if="canActuate && selectedDevice?.deviceType === 'GROW_LAMP'">
            <button type="button" class="ui-btn ui-btn-secondary" @click="openPanel('control')">
              打开现场调控
            </button>
          </div>
          <div class="strategy-actions" v-if="isOps">
            <RouterLink class="ui-btn" to="/devices">去设备页调试</RouterLink>
          </div>
        </div>

        <div class="side-body" v-else-if="lowerTab === 'strategy'">
          <p class="side-lead">配方 / AUTO / 气候放在此栏，避免挤占三维总览。</p>
          <div class="strategy-grid">
            <label class="field" v-if="auth.can('recipe.bind')">
              <span>配方</span>
              <select class="ui-select" :value="bayRecipeId" @change="onRecipe">
                <option v-for="r in dendrobiumRecipes" :key="r.recipeId" :value="r.recipeId">
                  {{ r.cropNameZh }} · {{ r.stage }}
                </option>
              </select>
            </label>
            <label class="field" v-else-if="light?.recipeId">
              <span>当前配方</span>
              <span class="readonly mono">{{ light.recipeId }}</span>
            </label>
            <label class="field" v-if="auth.can('climate.set')">
              <span>气候日型</span>
              <select class="ui-select" :value="light?.climateProfileId" @change="onClimate">
                <option v-for="c in climateOptions" :key="c.id" :value="c.id">{{ c.label }}</option>
              </select>
            </label>
            <div class="strategy-actions">
              <button
                v-if="auth.can('auto.toggle')"
                type="button"
                class="ui-btn"
                @click="toggleAuto"
              >
                自动调控 {{ light?.autoControl ? '开' : '关' }}
              </button>
              <button
                v-if="auth.can('sim.reset')"
                type="button"
                class="ui-btn ui-btn-secondary"
                @click="resetDay"
              >
                重跑今日
              </button>
            </div>
          </div>
          <dl class="detail-kv" v-if="light">
            <div>
              <dt>目标带</dt>
              <dd class="mono" v-if="light.dynamicTarget">
                {{ light.dynamicTarget.instantMin.toFixed(0) }}–{{
                  light.dynamicTarget.instantMax.toFixed(0)
                }}
              </dd>
              <dd v-else>—</dd>
            </div>
            <div>
              <dt>自然 / 灯</dt>
              <dd class="mono">{{ light.naturalPpfd ?? '—' }} / {{ light.ledPpfd ?? '—' }}</dd>
            </div>
            <div>
              <dt>遮阳 · 调光</dt>
              <dd class="mono">{{ light.shadeOpenPercent }}% · {{ avgDim }}%</dd>
            </div>
            <div v-if="economics">
              <dt>电费估</dt>
              <dd class="mono">¥{{ economics.energyCostYuanEst.toFixed(2) }}</dd>
            </div>
          </dl>
          <p v-if="economics?.adviceZh" class="econ-advice">{{ economics.adviceZh }}</p>
          <p v-if="light?.dynamicTarget?.noteZh" class="econ-advice">{{ light.dynamicTarget.noteZh }}</p>
        </div>

        <div class="side-body" v-else-if="lowerTab === 'control'">
          <p class="side-lead">
            当前分区 {{ zoneId }} ·
            <template v-if="!auth.can('ctrl.dim.high')">本角色调光上限 80%</template>
            <template v-else>可高开度调试</template>
          </p>
          <div class="control-stack" v-if="light && canActuate">
            <label class="slider" v-if="auth.can('ctrl.shade')">
              <span>遮阳粗档 {{ light.shadeOpenPercent }}%（{{ shadeSteps.join('/') }}）</span>
              <input
                type="range"
                min="10"
                max="100"
                step="30"
                :value="light.shadeOpenPercent"
                list="shade-steps"
                @change="onShade"
              />
              <datalist id="shade-steps">
                <option v-for="s in shadeSteps" :key="s" :value="s" />
              </datalist>
            </label>
            <label class="slider" v-if="auth.can('ctrl.dim.low')">
              <span>三色补光 {{ avgDim }}%</span>
              <input
                type="range"
                min="0"
                :max="auth.can('ctrl.dim.high') ? 100 : 80"
                step="5"
                :value="avgDim"
                @change="onDim"
              />
            </label>
          </div>
          <p v-else class="empty">当前角色不可直接调节执行器。</p>
        </div>

        <div class="side-body orders-body" v-else-if="lowerTab === 'orders'">
          <p v-if="isTrainee" class="side-lead">只读观察工单流程，不可批准或接单。</p>
          <ul v-if="orders.length" class="order-list">
            <li v-for="o in orders" :key="o.id">
              <div>
                <span class="st" :data-s="o.status">{{ o.status }}</span>
                <strong class="mono">{{ o.zoneId }}</strong>
                <p>{{ o.reason }}</p>
              </div>
              <div class="acts" v-if="!isTrainee && o.status === 'PENDING' && auth.can('wo.approve')">
                <button type="button" class="ui-btn ui-btn-compact" @click="approve(o.id)">
                  批准
                </button>
                <button
                  type="button"
                  class="ui-btn ui-btn-ghost ui-btn-compact"
                  @click="reject(o.id)"
                >
                  驳回
                </button>
              </div>
              <div
                class="acts"
                v-else-if="!isTrainee && o.status === 'APPROVED' && auth.can('wo.claim')"
              >
                <button type="button" class="ui-btn ui-btn-compact" @click="claimOrder(o.id)">
                  接单执行
                </button>
              </div>
              <div
                class="acts"
                v-else-if="!isTrainee && o.status === 'IN_PROGRESS' && auth.can('wo.complete')"
              >
                <button type="button" class="ui-btn ui-btn-compact" @click="completeOrder(o.id)">
                  完成
                </button>
              </div>
            </li>
          </ul>
          <p v-else class="empty">暂无工单</p>
        </div>

        <div class="side-body charts-body" v-else-if="lowerTab === 'charts'">
          <p class="side-lead">
            AUTO：贴配方光带 · 过亮遮光、光周期内欠光补光。虚线=调整前，实线=调控后。
          </p>
          <div class="ui-card chart-wrap overview-wrap">
            <RegionLightOverview
              :rows="regionRows"
              :selected-id="chartFocusId"
              @select="onRegionSelect"
            />
          </div>
          <div class="chart-toolbar">
            <span class="toolbar-label">视角</span>
            <div class="scope-group">
              <button
                v-for="s in (
                  [
                    ['bay', '整跨'],
                    ['zone', '半跨'],
                    ['beds', '六床'],
                    ['sensors', '测点'],
                    ['control', '控光'],
                  ] as const
                )"
                :key="s[0]"
                type="button"
                class="ui-btn ui-btn-ghost ui-btn-compact"
                :data-on="chartScope === s[0]"
                @click="setChartScope(s[0])"
              >
                {{ s[1] }}
              </button>
            </div>
            <label v-if="chartScope === 'sensors'" class="field sensor-pick">
              <span>床</span>
              <select v-model="chartFocusBedId" class="ui-select">
                <option
                  v-for="b in regionRows.filter((r) => r.kind === 'bed')"
                  :key="b.bedId!"
                  :value="b.bedId"
                >
                  {{ b.label }}
                </option>
              </select>
            </label>
          </div>
          <div class="charts-stack">
            <div class="ui-card chart-wrap" v-if="chartScope !== 'control'">
              <DayCurvesChart
                :title="chartTitles[chartScope]"
                mode="light"
                :anchor-series="chartBundle.anchor"
                :layers="chartBundle.layers"
                :minute-of-day="minuteOfDay"
                :show-outdoor="chartScope === 'zone'"
              />
            </div>
            <div class="ui-card chart-wrap" v-else>
              <DayCurvesChart
                :title="chartTitles.control"
                mode="control"
                :anchor-series="chartBundle.anchor"
                :layers="controlLayers"
                :minute-of-day="minuteOfDay"
              />
            </div>
            <div class="ui-card chart-wrap" v-if="chartScope !== 'control' && gapLayers.length">
              <DayCurvesChart
                title="距理想目标带偏差（ΔPAR）"
                mode="gap"
                :anchor-series="chartBundle.anchor"
                :layers="gapLayers"
                :minute-of-day="minuteOfDay"
              />
            </div>
            <div class="ui-card chart-wrap" v-if="chartScope !== 'control'">
              <DayCurvesChart
                title="分区遮阳 % · 补光 %"
                mode="control"
                :anchor-series="chartBundle.anchor"
                :layers="controlLayers"
                :minute-of-day="minuteOfDay"
              />
            </div>
            <div class="ui-card chart-wrap">
              <DayCurvesChart
                title="湿度 / 温度（当前半跨）"
                mode="climate"
                :anchor-series="chartBundle.anchor"
                :series="light?.series"
                :minute-of-day="minuteOfDay"
              />
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.gh {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
  min-height: 0;
  overflow: hidden;
}

.err {
  margin: 0;
  padding: 0.55rem 0.75rem;
  background: var(--danger-soft);
  border-left: 3px solid var(--danger);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.gh-bar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
  padding: 8px 4px 10px;
  border-bottom: 1px solid var(--line);
}

.gh-bar-left,
.gh-bar-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.role-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 650;
  font-size: var(--text-sm);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.field.inline {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.field .ui-select {
  width: auto;
  min-width: 9rem;
  padding: 6px 10px;
  font-size: var(--text-sm);
}

.ui-btn-ghost[data-on='true'] {
  background: var(--accent-soft);
  color: var(--accent);
}

.gh-bar-right strong {
  font-size: 1.15rem;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.track {
  width: 6.5rem;
  height: 6px;
  background: var(--line);
  border-radius: 999px;
  overflow: hidden;
}

.track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--sodium));
}

.muted {
  color: var(--ink-muted);
  font-size: var(--text-xs);
}

.gh-body {
  flex: 1 1 0;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0;
}

.gh.panel-open .gh-body {
  grid-template-columns: minmax(0, 1fr) minmax(340px, 42%);
}

.stage {
  position: relative;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #d5dae2;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
}

.stage-scene {
  flex: 1 1 0;
  min-height: 320px;
  display: flex;
  flex-direction: column;
}

.glance {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 4;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: min(72%, 520px);
  pointer-events: none;
}

.glance-item,
.glance-region {
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 5.5rem;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
  border-radius: 10px;
  background: color-mix(in srgb, var(--panel) 88%, transparent);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 24px rgba(20, 28, 36, 0.08);
  color: var(--ink);
  font: inherit;
  text-align: left;
}

.glance-item .k,
.glance-region > span:first-child {
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--ink-muted);
}

.glance-item strong,
.glance-region strong {
  font-size: 1.25rem;
  font-weight: 650;
  line-height: 1.15;
}

.glance-item .u {
  font-size: 10px;
  color: var(--ink-muted);
}

.glance-region {
  cursor: pointer;
}

.glance-item[data-warn='true'] {
  border-color: color-mix(in srgb, #c45c26 50%, var(--line));
}

.status-legend {
  position: absolute;
  left: 12px;
  bottom: 14px;
  z-index: 4;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  max-width: min(42%, 280px);
  padding: 8px 10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--panel) 90%, transparent);
  border: 1px solid var(--line);
  backdrop-filter: blur(8px);
  font-size: 11px;
  color: var(--ink-soft);
  pointer-events: none;
}

.status-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.status-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  box-shadow: 0 0 8px currentColor;
}

.status-legend i[data-tone='alarm'] {
  background: #ff3b30;
  color: #ff3b30;
}
.status-legend i[data-tone='offline'] {
  background: #8e8e93;
  color: #8e8e93;
}
.status-legend i[data-tone='wo-pending'] {
  background: #ff9500;
  color: #ff9500;
}
.status-legend i[data-tone='wo-approved'] {
  background: #0071e3;
  color: #0071e3;
}
.status-legend i[data-tone='wo-progress'] {
  background: #af52de;
  color: #af52de;
}

.device-card {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--paper);
}

.device-card .eyebrow {
  margin: 0;
  font-size: 11px;
  color: var(--ink-muted);
}

.device-card h3 {
  margin: 4px 0;
  font-size: 1.05rem;
}

.device-card .meta {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--ink-soft);
}

.tone-pill {
  display: inline-block;
  margin: 10px 0 0;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: var(--text-xs);
  font-weight: 650;
  background: var(--accent-soft);
  color: var(--accent);
}

.tone-pill[data-tone='alarm'] {
  background: color-mix(in srgb, #ff3b30 18%, transparent);
  color: #c62828;
}
.tone-pill[data-tone='offline'] {
  background: color-mix(in srgb, #8e8e93 22%, transparent);
  color: #636366;
}
.tone-pill[data-tone='wo-pending'] {
  background: var(--warning-soft);
  color: var(--sodium-deep);
}
.tone-pill[data-tone='wo-approved'] {
  background: var(--accent-soft);
  color: var(--accent);
}
.tone-pill[data-tone='wo-progress'] {
  background: color-mix(in srgb, #af52de 16%, transparent);
  color: #7b2cbf;
}

.device-section h4 {
  margin: 0 0 8px;
  font-size: var(--text-sm);
}

.note-field textarea {
  width: 100%;
  resize: vertical;
  min-height: 56px;
}

.gap[data-tone='ok'] {
  color: var(--success, #34c759);
}
.gap[data-tone='warn'] {
  color: var(--warning, #ff9500);
  font-size: 0.95rem !important;
}
.gap[data-tone='muted'] {
  color: var(--ink-muted);
  font-size: 0.95rem !important;
}

.dock {
  position: absolute;
  right: 14px;
  bottom: 14px;
  left: auto;
  z-index: 5;
  transform: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  max-width: min(520px, calc(100% - 16rem));
  padding: 8px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--panel) 92%, transparent);
  border: 1px solid var(--line);
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 28px rgba(20, 28, 36, 0.12);
}

.dock-btn,
.dock-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}

.dock-btn:hover,
.dock-link:hover {
  background: var(--paper);
}

.dock-btn[data-on='true'] {
  background: var(--accent-soft);
  color: var(--accent);
}

.dock-close {
  color: var(--ink-muted);
}

.dock-link {
  color: var(--accent);
}

.badge {
  font-size: 11px;
  font-family: var(--font-mono);
  background: var(--warning-soft);
  color: var(--sodium-deep);
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
}

.side-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--line);
  background: var(--panel);
  overflow: hidden;
}

.side-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--line);
}

.side-head h2 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 650;
}

.side-body {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-lead {
  margin: 0;
  font-size: var(--text-sm);
  line-height: 1.5;
  color: var(--ink-soft);
}

.strategy-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.strategy-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.readonly {
  display: inline-block;
  padding: 8px 10px;
  background: var(--paper);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}

.detail-kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 0;
}

.detail-kv > div {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--paper);
}

.detail-kv dt {
  font-size: 11px;
  color: var(--ink-muted);
}

.detail-kv dd {
  margin: 4px 0 0;
  font-weight: 650;
  font-size: 1.05rem;
}

.econ-advice {
  margin: 0;
  padding: 10px 12px;
  font-size: var(--text-sm);
  line-height: 1.45;
  color: var(--ink-soft);
  background: var(--paper);
  border: 1px solid var(--line);
  border-left: 3px solid var(--accent);
  border-radius: 8px;
}

.control-stack {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.slider {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: var(--text-sm);
  color: var(--ink-soft);
}

.slider input[type='range'] {
  width: 100%;
  height: 28px;
  accent-color: var(--accent);
}

.order-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: var(--paper);
  border: 1px solid var(--line);
  border-radius: 10px;
}

.order-list p {
  margin: 6px 0 0;
  color: var(--ink-soft);
  font-size: var(--text-sm);
  line-height: 1.4;
}

.st {
  font-family: var(--font-mono);
  font-size: 11px;
  margin-right: 0.4rem;
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
  background: var(--line);
}

.st[data-s='PENDING'] {
  background: var(--warning-soft);
  color: var(--sodium-deep);
}

.acts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}

.empty {
  color: var(--ink-muted);
  margin: 0;
  font-size: var(--text-sm);
}

.chart-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--ink-muted);
}

.scope-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.sensor-pick {
  margin-left: auto;
}

.charts-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 12px;
}

.chart-wrap {
  padding: 10px;
}

.charts-stack :deep(.chart),
.charts-stack :deep(.chart-host) {
  min-height: 220px;
  height: 220px;
}

@media (max-width: 1100px) {
  .gh.panel-open .gh-body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(42vh, 1fr) minmax(38vh, 1fr);
  }

  .side-panel {
    border-left: 0;
    border-top: 1px solid var(--line);
  }

  .glance {
    max-width: calc(100% - 24px);
  }
}

@media (max-width: 720px) {
  .glance-item strong,
  .glance-region strong {
    font-size: 1.05rem;
  }

  .dock {
    left: 12px;
    right: 12px;
    max-width: none;
    justify-content: center;
  }
}
</style>
