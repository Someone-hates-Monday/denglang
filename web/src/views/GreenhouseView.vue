<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  greenhouseApi,
  type GhEffectiveLight,
  type GhRecipe,
  type GhWorkOrder,
  type GhZone,
} from '../api/greenhouse'
import { useAuthStore } from '../stores/auth'
import { useRealtimeStore } from '../stores/realtime'
import GreenhouseScene3D from '../components/GreenhouseScene3D.vue'
import DayCurvesChart from '../components/DayCurvesChart.vue'
import { HEAT_CHANNEL_LABEL, type HeatChannel } from '../scene/spectrumModel'
import { ROLE_LABEL, normalizeRole } from '../auth/rbac'

const auth = useAuthStore()
const realtime = useRealtimeStore()
const roleBanner = computed(() => {
  const r = normalizeRole(auth.role)
  const map: Record<string, string> = {
    SITE_MANAGER: '场长视图：可开关 AUTO、绑已发布配方、代批紧急工单；不可改配方硬限。',
    AGRONOMIST: '农艺视图：配方绑定、气候、审批队列；大动作优先走工单。',
    GROWER: '种植员视图：执行待办工单、阈值内微调遮阳/补光；无 AUTO / 配方编辑。',
    DEVICE_OPS: '运维只读光场；强制调试请走「设备」页。',
    TRAINEE: '学员只读：可看热力与日曲线；生产区不可控执行器。',
    SYS_ADMIN: '系统视图：全能力（演示）。',
  }
  return map[r] || ''
})

const zones = ref<GhZone[]>([])
const recipes = ref<GhRecipe[]>([])
const orders = ref<GhWorkOrder[]>([])
const profiles = ref<Record<string, { id: string; labelZh: string }>>({})
const lights = ref<Record<string, GhEffectiveLight>>({})
const zoneId = ref('ZONE-A')
const showHeat = ref(true)
const heatChannel = ref<HeatChannel>('rgb')
const heatChannels: HeatChannel[] = ['rgb', 'R', 'G', 'B', 'viridis', 'xray']
const lowerTab = ref<'none' | 'charts' | 'orders'>('none')
const err = ref('')
let poll: number | undefined

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
    measurePlaneZ: 0.85,
    nx: focus.nx || a?.nx || 32,
    ny: focus.ny || a?.ny || 14,
    grid,
    devices: [...deviceMap.values()],
    sensorPpfd,
  }
})

const series = computed(() => light.value?.series ?? [])
const dayPct = computed(() => Math.round((light.value?.dayProgress ?? 0) * 100))
const pendingOrders = computed(() => orders.value.filter((o) => o.status === 'PENDING'))

async function refresh() {
  err.value = ''
  try {
    const [z, r, o, p] = await Promise.all([
      greenhouseApi.zones(),
      greenhouseApi.recipes(),
      greenhouseApi.workOrders(),
      greenhouseApi.climateProfiles(),
    ])
    if (z.code !== 200) throw new Error(z.errorMsg || 'zones failed')
    zones.value = z.data || []
    recipes.value = r.data || []
    orders.value = o.data || []
    profiles.value = p.data || {}

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

async function onRecipe(e: Event) {
  await greenhouseApi.bindRecipe(zoneId.value, (e.target as HTMLSelectElement).value)
  await refresh()
}

async function onClimate(e: Event) {
  await greenhouseApi.setClimate(zoneId.value, (e.target as HTMLSelectElement).value)
  await refresh()
}

async function toggleAuto() {
  if (!light.value) return
  await greenhouseApi.setAuto(zoneId.value, !light.value.autoControl)
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

async function completeOrder(id: number) {
  if (!auth.can('wo.complete')) return
  await greenhouseApi.complete(id)
  await refresh()
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

watch(
  () => realtime.greenhouseTick,
  () => refresh(),
)

onMounted(async () => {
  await refresh()
  poll = window.setInterval(refresh, 800)
})

onUnmounted(() => {
  if (poll) window.clearInterval(poll)
})
</script>

<template>
  <div class="ui-page ui-page-fill gh">
    <p v-if="err" class="err">{{ err }} · 请确认后端已启动（:8080）</p>
    <p v-if="roleBanner" class="role-banner">
      <span class="role-pill">{{ ROLE_LABEL[normalizeRole(auth.role)] }}</span>
      {{ roleBanner }}
    </p>

    <div class="top-row">
      <div class="controls">
        <label class="field">
          <span>分区</span>
          <select v-model="zoneId" class="ui-select">
            <option v-for="z in zones" :key="z.zoneId" :value="z.zoneId">{{ z.name }}</option>
          </select>
        </label>
        <label class="field" v-if="auth.can('recipe.bind')">
          <span>配方</span>
          <select class="ui-select" :value="light?.recipeId" @change="onRecipe">
            <option v-for="r in recipes" :key="r.recipeId" :value="r.recipeId">
              {{ r.cropNameZh }} · {{ r.stage }}
            </option>
          </select>
        </label>
        <label class="field" v-else-if="light?.recipeId" >
          <span>配方</span>
          <span class="readonly mono">{{ light.recipeId }}</span>
        </label>
        <label class="field" v-if="auth.can('climate.set')">
          <span>气候</span>
          <select class="ui-select" :value="light?.climateProfileId" @change="onClimate">
            <option v-for="c in climateOptions" :key="c.id" :value="c.id">{{ c.label }}</option>
          </select>
        </label>
        <button
          v-if="auth.can('auto.toggle')"
          type="button"
          class="ui-btn ui-btn-compact"
          @click="toggleAuto"
        >
          自动 {{ light?.autoControl ? '开' : '关' }}
        </button>
        <button
          v-if="auth.can('sim.reset')"
          type="button"
          class="ui-btn ui-btn-secondary ui-btn-compact"
          @click="resetDay"
        >
          重跑今日
        </button>
        <button
          v-if="auth.can('gh.heat')"
          type="button"
          class="ui-btn ui-btn-ghost ui-btn-compact"
          :data-on="showHeat"
          @click="showHeat = !showHeat"
        >
          光场 {{ showHeat ? '开' : '关' }}
        </button>
        <div class="mode-group" v-if="showHeat && auth.can('gh.heat')">
          <button
            v-for="ch in heatChannels"
            :key="ch"
            type="button"
            class="ui-btn ui-btn-ghost ui-btn-compact"
            :data-on="heatChannel === ch"
            @click="heatChannel = ch"
          >
            {{ HEAT_CHANNEL_LABEL[ch] }}
          </button>
        </div>
      </div>

      <div class="clock mono" v-if="light">
        <strong>{{ clockLabel(light.minuteOfDay) }}</strong>
        <div class="track"><i :style="{ width: dayPct + '%' }" /></div>
        <span class="muted">{{ dayPct }}% · 整跨 A+B</span>
      </div>
    </div>

    <div
      class="demo-sliders"
      v-if="light && (auth.can('ctrl.shade') || auth.can('ctrl.dim.low'))"
    >
      <label class="slider" v-if="auth.can('ctrl.shade')">
        <span
          >遮阳粗档 {{ light.shadeOpenPercent }}%（仅
          {{ shadeSteps.join('/') }}；关小挡直射）</span
        >
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
        <span
          >三色补光 {{ avgDim }}%（配方光谱
          <template v-if="!auth.can('ctrl.dim.high')"> · 本角色上限 80%</template>）</span
        >
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
    <p v-else-if="light" class="readonly-hint">当前角色为只读光场，不可直接调节执行器。</p>

    <section class="scene-card ui-card fill-card">
      <GreenhouseScene3D
        :light="bayLight"
        :zone-lights="lights"
        :focus-zone-id="zoneId"
        :shade-open-a="lights['ZONE-A']?.shadeOpenPercent ?? 100"
        :shade-open-b="lights['ZONE-B']?.shadeOpenPercent ?? 100"
        :show-heat="showHeat"
        :heat-channel="heatChannel"
      />
    </section>

    <div class="metrics" v-if="light">
      <div class="metric">
        <span class="k">实况</span>
        <strong class="mono">{{ Number(light.effectivePpfd).toFixed(0) }}</strong>
      </div>
      <div class="metric" v-if="light.dynamicTarget">
        <span class="k">此刻目标</span>
        <strong class="mono"
          >{{ light.dynamicTarget.instantMin.toFixed(0) }}–{{
            light.dynamicTarget.instantMax.toFixed(0)
          }}</strong
        >
      </div>
      <div class="metric" v-if="gapStatus">
        <span class="k">状态</span>
        <strong class="gap" :data-tone="gapStatus.tone">{{ gapStatus.label }}</strong>
      </div>
      <div class="metric">
        <span class="k">日/灯</span>
        <strong class="mono">{{ light.naturalPpfd ?? '—' }} / {{ light.ledPpfd ?? '—' }}</strong>
      </div>
      <div class="metric">
        <span class="k">遮阳 · 调光</span>
        <strong class="mono">{{ light.shadeOpenPercent }}% · {{ avgDim }}%</strong>
      </div>
      <div class="metric">
        <span class="k">DLI</span>
        <strong class="mono"
          >{{ light.dliSoFar
          }}<template v-if="light.dynamicTarget">/{{ light.dynamicTarget.dliTargetMin }}</template></strong
        >
      </div>
      <div class="metric" v-if="economics">
        <span class="k">产量指数</span>
        <strong class="mono">{{ economics.yieldIndex.toFixed(2) }}</strong>
      </div>
      <div class="metric" v-if="economics">
        <span class="k">电费估</span>
        <strong class="mono">¥{{ economics.energyCostYuanEst.toFixed(2) }}</strong>
      </div>
      <div class="metric" v-if="economics">
        <span class="k">平衡分</span>
        <strong class="mono">{{ economics.balanceScore.toFixed(2) }}</strong>
      </div>
    </div>

    <p v-if="economics?.adviceZh" class="econ-advice">{{ economics.adviceZh }}</p>
    <p v-if="light?.bedStats?.length" class="econ-advice bed-stats">
      分床均光：
      <template v-for="(b, i) in light.bedStats" :key="b.bedId">
        <span v-if="i"> · </span>{{ b.bedId.replace('BED-', '') }}
        {{ Number(b.avgPpfd).toFixed(0) }}
        <span class="muted">(U₀ {{ Number(b.uniformityU0).toFixed(2) }})</span>
      </template>
    </p>

    <div class="lower-bar">
      <button
        type="button"
        class="ui-btn ui-btn-ghost ui-btn-compact"
        :data-on="lowerTab === 'charts'"
        @click="lowerTab = lowerTab === 'charts' ? 'none' : 'charts'"
      >
        日曲线
      </button>
      <button
        type="button"
        class="ui-btn ui-btn-ghost ui-btn-compact"
        :data-on="lowerTab === 'orders'"
        @click="lowerTab = lowerTab === 'orders' ? 'none' : 'orders'"
      >
        工单
        <span v-if="pendingOrders.length" class="badge">{{ pendingOrders.length }}</span>
      </button>
      <span v-if="light?.dynamicTarget?.noteZh" class="dyn-note">{{ light.dynamicTarget.noteZh }}</span>
    </div>

    <div class="ui-scroll-panel lower" v-if="lowerTab === 'charts'">
      <div class="charts">
        <div class="ui-card chart-wrap">
          <DayCurvesChart
            title="光照与此刻目标带"
            mode="light"
            :series="series"
            :minute-of-day="light?.minuteOfDay ?? 0"
          />
        </div>
        <div class="ui-card chart-wrap">
          <DayCurvesChart
            title="湿度 / 温度"
            mode="climate"
            :series="series"
            :minute-of-day="light?.minuteOfDay ?? 0"
          />
        </div>
      </div>
    </div>

    <div class="ui-scroll-panel lower" v-else-if="lowerTab === 'orders'">
      <section class="orders ui-card">
        <h3 class="ui-section-title">农艺工单</h3>
        <ul v-if="orders.length">
          <li v-for="o in orders" :key="o.id">
            <div>
              <span class="st" :data-s="o.status">{{ o.status }}</span>
              <strong class="mono">{{ o.zoneId }}</strong>
              <p>{{ o.reason }}</p>
            </div>
            <div class="acts" v-if="o.status === 'PENDING' && auth.can('wo.approve')">
              <button type="button" class="ui-btn ui-btn-compact" @click="approve(o.id)">批准</button>
              <button type="button" class="ui-btn ui-btn-ghost ui-btn-compact" @click="reject(o.id)">
                驳回
              </button>
            </div>
            <div
              class="acts"
              v-else-if="o.status === 'APPROVED' && auth.can('wo.complete')"
            >
              <button type="button" class="ui-btn ui-btn-compact" @click="completeOrder(o.id)">
                完成执行
              </button>
            </div>
          </li>
        </ul>
        <p v-else class="empty">暂无工单</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.gh {
  gap: var(--space-2);
  padding-top: var(--space-2);
  min-height: 0;
}

.err {
  margin: 0;
  padding: 0.55rem 0.75rem;
  background: var(--danger-soft);
  border-left: 3px solid var(--danger);
  border-radius: var(--radius-sm);
}

.role-banner {
  margin: 0;
  padding: 8px 12px;
  font-size: var(--text-sm);
  color: var(--ink-soft);
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
  line-height: 1.45;
}

.role-pill {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--panel);
  color: var(--accent);
  font-weight: 600;
  font-size: var(--text-xs);
}

.readonly-hint {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--ink-muted);
}

.readonly {
  display: inline-block;
  padding: 8px 10px;
  background: var(--paper);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
}

.top-row {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-2);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.field .ui-select {
  width: auto;
  min-width: 9.5rem;
  max-width: 14rem;
  flex: 0 0 auto;
  padding: 6px 10px;
  font-size: var(--text-sm);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-left: auto;
}

.mode-group {
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  background: var(--line);
  border-radius: var(--radius-sm);
}

.demo-sliders {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.slider {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: var(--text-xs);
  color: var(--ink-soft);
}

.slider input[type='range'] {
  width: 100%;
  accent-color: var(--accent);
}

@media (max-width: 720px) {
  .demo-sliders {
    grid-template-columns: 1fr;
  }
}

.ui-btn-ghost[data-on='true'] {
  background: var(--accent-soft);
  color: var(--accent);
}

.clock {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 12rem;
}

.clock strong {
  font-size: var(--text-lg);
  font-weight: 600;
}

.track {
  width: 7rem;
  height: 5px;
  background: var(--line);
  border-radius: var(--radius-full);
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

.scene-card {
  flex: 1 1 0;
  min-height: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.metrics {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(88px, 1fr));
  gap: var(--space-2);
}

.metric {
  padding: 0.4rem 0.55rem;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.metric .k {
  display: block;
  font-size: 10px;
  color: var(--ink-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.metric strong {
  font-size: var(--text-sm);
  font-weight: 600;
}

.econ-advice {
  flex-shrink: 0;
  margin: 0;
  padding: 0.45rem 0.65rem;
  font-size: var(--text-xs);
  color: var(--ink-soft);
  background: var(--panel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-sm);
}

.gap[data-tone='ok'] {
  color: var(--success, #34c759);
}
.gap[data-tone='warn'] {
  color: var(--warning, #ff9500);
}
.gap[data-tone='muted'] {
  color: var(--ink-muted);
}

.lower-bar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.dyn-note {
  flex: 1 1 12rem;
  margin: 0;
  font-size: 0.68rem;
  color: var(--ink-soft);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lower {
  flex: 0 0 auto;
  max-height: min(28vh, 240px);
}

.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}

.chart-wrap {
  padding: var(--space-2);
}

.orders {
  padding: var(--space-3) var(--space-4);
}

.ui-section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge {
  font-size: var(--text-xs);
  font-family: var(--font-mono);
  background: var(--warning-soft);
  color: var(--sodium-deep);
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
}

.orders ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.orders li {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  background: var(--panel-secondary);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.orders p {
  margin: 0.25rem 0 0;
  color: var(--ink-soft);
  font-size: var(--text-sm);
}

.st {
  font-family: var(--font-mono);
  font-size: 10px;
  margin-right: 0.4rem;
  padding: 0.1rem 0.3rem;
  border-radius: 4px;
  background: var(--line);
}

.st[data-s='PENDING'] {
  background: var(--warning-soft);
  color: var(--sodium-deep);
}

.acts {
  display: flex;
  gap: 0.35rem;
}

.empty {
  color: var(--ink-muted);
  margin: 0;
}

@media (max-width: 960px) {
  .charts {
    grid-template-columns: 1fr;
  }
  .scene-card {
    min-height: 280px;
  }
}
</style>
