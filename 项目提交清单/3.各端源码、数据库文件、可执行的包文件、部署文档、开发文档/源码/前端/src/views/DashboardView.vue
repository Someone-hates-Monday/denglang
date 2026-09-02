<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import {
  greenhouseApi,
  type GhDevice,
  type GhEffectiveLight,
  type GhWorkOrder,
  type GhZone,
} from '../api/greenhouse'
import { useRealtimeStore } from '../stores/realtime'

const realtime = useRealtimeStore()
const zones = ref<GhZone[]>([])
const lights = ref<Record<string, GhEffectiveLight>>({})
const devices = ref<GhDevice[]>([])
const orders = ref<GhWorkOrder[]>([])
const err = ref('')
let poll: number | undefined

const pendingOrders = computed(() => orders.value.filter((o) => o.status === 'PENDING'))
const onlineCount = computed(() => devices.value.filter((d) => d.onlineStatus === 'ONLINE').length)
const lampCount = computed(() => devices.value.filter((d) => d.deviceType === 'GROW_LAMP').length)
const sensorCount = computed(() => devices.value.filter((d) => d.deviceType === 'PAR_SENSOR').length)

const primary = computed(() => {
  const z = zones.value[0]
  if (!z) return null
  return lights.value[z.zoneId] || null
})

async function load() {
  err.value = ''
  try {
    const [zRes, dRes, oRes] = await Promise.all([
      greenhouseApi.zones(),
      greenhouseApi.devices(),
      greenhouseApi.workOrders(),
    ])
    if (zRes.code !== 200) throw new Error(zRes.errorMsg || '分区加载失败')
    if (dRes.code !== 200) throw new Error(dRes.errorMsg || '设备加载失败')
    zones.value = zRes.data || []
    devices.value = dRes.data || []
    if (oRes.code === 200) orders.value = oRes.data || []

    const map: Record<string, GhEffectiveLight> = {}
    await Promise.all(
      zones.value.map(async (z) => {
        const el = await greenhouseApi.effectiveLight(z.zoneId)
        if (el.code === 200 && el.data) map[z.zoneId] = el.data
      }),
    )
    lights.value = map
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

function clockLabel(minute: number) {
  const h = Math.floor(minute / 60)
  const m = minute % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

onMounted(() => {
  void load()
  poll = window.setInterval(() => void load(), 6000)
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
  <div class="ui-page ui-page-fill dash">
    <p v-if="err" class="err">{{ err }}</p>

    <RouterLink to="/greenhouse" class="hero page-hero ui-link-card slide-up-enter-active">
      <div class="hero-main">
        <p class="eyebrow">智慧光棚 · 区有效 PPFD</p>
        <p class="hero-value mono">
          {{ primary ? primary.effectivePpfd.toFixed(1) : '—' }}
          <span class="unit">µmol·m⁻²·s⁻¹</span>
        </p>
        <p class="hero-meta mono">
          {{ primary ? `${primary.name} · 仿真 ${clockLabel(primary.minuteOfDay)}` : '加载中…' }}
        </p>
      </div>
      <div class="hero-side">
        <p class="threshold-hint" v-if="primary?.recipe">
          目标 {{ primary.recipe.ppfdTargetMin }}–{{ primary.recipe.ppfdTargetMax }}<br />
          硬限 {{ primary.recipe.ppfdHardMin }}–{{ primary.recipe.ppfdHardMax }}
        </p>
        <span class="hero-link">进入冠层光场 →</span>
      </div>
    </RouterLink>

    <div class="ui-fill-body dashboard-body slide-up-enter-active slide-up-delay-1">
      <section class="ops ui-card">
        <h3 class="ui-section-title">场务待办</h3>
        <div class="ops-grid">
          <RouterLink to="/greenhouse" class="ops-item ui-link-card">
            <p class="ops-label">待审批工单</p>
            <strong class="ui-stat-value" :class="{ bad: pendingOrders.length }">
              {{ pendingOrders.length }}
            </strong>
          </RouterLink>
          <div class="ops-item">
            <p class="ops-label">棚内设备在线</p>
            <strong class="ui-stat-value on">{{ onlineCount }}/{{ devices.length }}</strong>
            <p class="ops-meta">测点 {{ sensorCount }} · 补光灯 {{ lampCount }}</p>
          </div>
          <div class="ops-item">
            <p class="ops-label">今日 DLI（主区）</p>
            <strong class="ui-stat-value">{{ primary?.dliSoFar ?? '—' }}</strong>
            <p class="ops-meta">mol·m⁻²·d⁻¹</p>
          </div>
        </div>
      </section>

      <section class="zones">
        <h3 class="ui-section-title">栽培分区</h3>
        <div class="zone-grid">
          <RouterLink
            v-for="z in zones"
            :key="z.zoneId"
            to="/greenhouse"
            class="zone-card ui-card ui-link-card"
          >
            <p class="z-id mono">{{ z.zoneId }}</p>
            <strong>{{ z.name }}</strong>
            <p class="z-ppfd mono">
              {{ lights[z.zoneId]?.effectivePpfd?.toFixed?.(1) ?? z.lastEffectivePpfd ?? '—' }}
              <span>PPFD</span>
            </p>
            <p class="z-meta">
              {{ z.climateProfileId }} · 遮阳 {{ lights[z.zoneId]?.shadeOpenPercent ?? z.shadeOpenPercent }}%
            </p>
            <p class="z-recipe mono">{{ z.recipeId }}</p>
          </RouterLink>
        </div>
      </section>

      <section class="orders ui-card" v-if="pendingOrders.length">
        <h3 class="ui-section-title">待处理建议</h3>
        <ul>
          <li v-for="o in pendingOrders.slice(0, 5)" :key="o.id">
            <span class="mono">{{ o.zoneId }}</span>
            {{ o.reason }}
          </li>
        </ul>
        <RouterLink to="/greenhouse" class="ops-link">去审批 →</RouterLink>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-height: 0;
}

.err {
  background: #f7e4d8;
  border-left: 3px solid #b85c38;
  padding: 0.65rem 0.85rem;
  margin: 0;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 1.25rem 1.5rem;
  text-decoration: none;
  color: inherit;
}

.eyebrow {
  margin: 0;
  font-size: 0.75rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  opacity: 0.72;
}

.hero-value {
  margin: 0.35rem 0;
  font-size: clamp(2rem, 4vw, 2.75rem);
  font-weight: 700;
  line-height: 1.1;
}

.unit {
  font-size: 0.9rem;
  font-weight: 500;
  opacity: 0.7;
  margin-left: 0.25rem;
}

.hero-meta {
  margin: 0;
  opacity: 0.65;
  font-size: 0.85rem;
}

.threshold-hint {
  margin: 0 0 0.5rem;
  font-size: 0.9rem;
  line-height: 1.45;
  opacity: 0.8;
}

.hero-link {
  font-size: 0.9rem;
  font-weight: 600;
}

.dashboard-body {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.85rem;
}

@media (max-width: 900px) {
  .ops-grid {
    grid-template-columns: 1fr;
  }
  .hero {
    flex-direction: column;
  }
}

.ops-item {
  padding: 0.75rem;
}

.ops-label {
  margin: 0 0 0.35rem;
  font-size: 0.8rem;
  opacity: 0.7;
}

.ops-meta {
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  opacity: 0.65;
}

.ops-link {
  display: inline-block;
  margin-top: 0.5rem;
  font-size: 0.85rem;
}

.bad {
  color: #b85c38;
}

.on {
  color: var(--accent, #2f5d4a);
}

.zone-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 0.85rem;
}

.zone-card {
  padding: 1rem;
  text-decoration: none;
  color: inherit;
  display: block;
}

.z-id {
  margin: 0;
  font-size: 0.75rem;
  opacity: 0.6;
}

.zone-card strong {
  display: block;
  margin: 0.25rem 0 0.5rem;
  font-size: 1.05rem;
}

.z-ppfd {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
}

.z-ppfd span {
  font-size: 0.75rem;
  font-weight: 500;
  opacity: 0.65;
  margin-left: 0.25rem;
}

.z-meta,
.z-recipe {
  margin: 0.35rem 0 0;
  font-size: 0.78rem;
  opacity: 0.7;
  word-break: break-all;
}

.orders ul {
  margin: 0;
  padding-left: 1.1rem;
}

.orders li {
  margin-bottom: 0.4rem;
  line-height: 1.4;
}
</style>
