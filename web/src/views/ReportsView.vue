<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { greenhouseApi, type GhReport } from '../api/greenhouse'
import { useAuthStore } from '../stores/auth'
import { ROLE_LABEL, normalizeRole, roleFocusZh } from '../auth/rbac'

const auth = useAuthStore()
const role = computed(() => normalizeRole(auth.role))
const isTrainee = computed(() => role.value === 'TRAINEE')
const roleZh = computed(() => (auth.role ? ROLE_LABEL[role.value] : ''))
const list = ref<GhReport[]>([])
const selected = ref<GhReport | null>(null)
const zoneId = ref('ZONE-A')
const busy = ref(false)
const err = ref('')
const reviewNote = ref('')

const canReview = computed(() => auth.can('perm.decide') && !isTrainee.value)
const writeLabel = computed(() => (isTrainee.value ? '生成/刷新实训草稿' : '生成/刷新日草稿'))
const lead = computed(() => {
  if (isTrainee.value) {
    return `${roleZh.value} · ${roleFocusZh(role.value)}：观察光场后提交实训报告，由场长/农艺批阅。`
  }
  if (canReview.value) {
    return `${roleZh.value} · ${roleFocusZh(role.value)}：可生成日光合摘要并批阅学员实训报告。`
  }
  return `${roleZh.value} · ${roleFocusZh(role.value)}：可查看与提交本职报告。`
})

function statusLabel(s: string) {
  if (s === 'DRAFT') return '草稿'
  if (s === 'SUBMITTED') return '已提交'
  if (s === 'REVIEWED') return '已批阅'
  if (s === 'ARCHIVED') return '归档'
  return s
}

function typeLabel(t: string) {
  if (t === 'DAILY_LIGHT') return '日光合'
  if (t === 'ENERGY_YIELD') return '能效产量'
  if (t === 'DEVICE_HEALTH') return '设备健康'
  if (t === 'TRAINING') return '实训'
  return t
}

function parseBody(r: GhReport | null): Record<string, unknown> | null {
  if (!r?.bodyJson) return null
  try {
    return JSON.parse(r.bodyJson) as Record<string, unknown>
  } catch {
    return null
  }
}

const body = computed(() => parseBody(selected.value))

async function load() {
  err.value = ''
  try {
    const res = await greenhouseApi.reports({
      limit: 50,
      type: isTrainee.value ? 'TRAINING' : undefined,
    })
    if (res.code !== 200) throw new Error(res.errorMsg || '加载失败')
    list.value = res.data || []
    if (selected.value) {
      const hit = list.value.find((x) => x.id === selected.value!.id)
      selected.value = hit || list.value[0] || null
    } else {
      selected.value = list.value[0] || null
    }
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

async function draft() {
  if (!auth.can('report.write')) return
  busy.value = true
  err.value = ''
  try {
    const res = isTrainee.value
      ? await greenhouseApi.draftTrainingReport(zoneId.value)
      : await greenhouseApi.draftDailyReport(zoneId.value)
    if (res.code !== 200) throw new Error(res.errorMsg || '生成失败')
    await load()
    selected.value = res.data
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function submit() {
  if (!selected.value || !auth.can('report.write')) return
  busy.value = true
  try {
    const res = await greenhouseApi.submitReport(selected.value.id)
    if (res.code !== 200) throw new Error(res.errorMsg || '提交失败')
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function review(approve: boolean) {
  if (!selected.value || !canReview.value) return
  busy.value = true
  try {
    const res = await greenhouseApi.reviewReport(selected.value.id, reviewNote.value, approve)
    if (res.code !== 200) throw new Error(res.errorMsg || '批阅失败')
    reviewNote.value = ''
    await load()
  } catch (e) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="ui-page reports">
    <header class="head">
      <div>
        <h2 class="ui-section-title">{{ isTrainee ? '我的实训报告' : '光棚报告' }}</h2>
        <p class="lead">{{ lead }}</p>
      </div>
      <div class="actions" v-if="auth.can('report.write')">
        <select v-model="zoneId" class="ui-select">
          <option value="ZONE-A">西半跨 ZONE-A</option>
          <option value="ZONE-B">东半跨 ZONE-B</option>
        </select>
        <button type="button" class="ui-btn" :disabled="busy" @click="draft">
          {{ writeLabel }}
        </button>
      </div>
    </header>

    <p v-if="err" class="err">{{ err }}</p>

    <div class="layout">
      <aside class="list ui-card">
        <h3 class="ui-section-title">列表</h3>
        <button
          v-for="r in list"
          :key="r.id"
          type="button"
          class="row"
          :data-on="selected?.id === r.id"
          @click="selected = r"
        >
          <span class="t">{{ typeLabel(r.reportType) }}</span>
          <strong>{{ r.title }}</strong>
          <span class="meta">{{ statusLabel(r.status) }} · {{ r.reportDate }}</span>
        </button>
        <p v-if="!list.length" class="empty">
          {{ isTrainee ? '暂无实训报告，可生成观察草稿。' : '暂无报告。有写权限时可生成日草稿。' }}
        </p>
      </aside>

      <section class="detail ui-card" v-if="selected">
        <div class="detail-head">
          <div>
            <p class="eyebrow">{{ typeLabel(selected.reportType) }} · {{ statusLabel(selected.status) }}</p>
            <h3>{{ selected.title }}</h3>
            <p class="summary">{{ selected.summaryZh }}</p>
          </div>
          <div class="detail-actions">
            <button
              v-if="selected.status === 'DRAFT' && auth.can('report.write')"
              type="button"
              class="ui-btn ui-btn-compact"
              :disabled="busy"
              @click="submit"
            >
              提交
            </button>
          </div>
        </div>

        <p v-if="body?.observerNote" class="observer">{{ String(body.observerNote) }}</p>

        <dl class="kv" v-if="body">
          <div><dt>分区</dt><dd class="mono">{{ body.zoneId }}</dd></div>
          <div><dt>有效光</dt><dd class="mono">{{ body.effectivePpfd }}</dd></div>
          <div><dt>DLI</dt><dd class="mono">{{ body.dliSoFar }}<template v-if="body.dliTargetMin"> / {{ body.dliTargetMin }}</template></dd></div>
          <div v-if="!isTrainee"><dt>待审工单</dt><dd class="mono">{{ body.workOrderPending }}</dd></div>
          <div v-if="!isTrainee"><dt>完成工单</dt><dd class="mono">{{ body.workOrderCompleted }}</dd></div>
          <div v-if="!isTrainee && body.economics && typeof body.economics === 'object'">
            <dt>电费估</dt>
            <dd class="mono">
              ¥{{ Number((body.economics as Record<string, unknown>).energyCostYuanEst ?? 0).toFixed(2) }}
            </dd>
          </div>
        </dl>

        <p v-if="selected.reviewNote" class="note">批阅意见：{{ selected.reviewNote }}</p>

        <div class="review" v-if="canReview && (selected.status === 'SUBMITTED' || selected.status === 'DRAFT')">
          <textarea v-model="reviewNote" class="ui-input" rows="2" placeholder="批阅意见（可选）" />
          <div class="review-btns">
            <button type="button" class="ui-btn ui-btn-compact" :disabled="busy" @click="review(true)">
              通过批阅
            </button>
            <button
              type="button"
              class="ui-btn ui-btn-secondary ui-btn-compact"
              :disabled="busy"
              @click="review(false)"
            >
              退回草稿
            </button>
          </div>
        </div>
      </section>

      <section v-else class="detail ui-card empty-panel">
        <p class="empty">选择左侧报告查看详情</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.reports {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-3);
  align-items: flex-end;
}

.lead {
  margin: 4px 0 0;
  color: var(--ink-soft);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.layout {
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: var(--space-3);
  min-height: 420px;
}

.list,
.detail {
  padding: var(--space-4);
}

.row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  margin: 0 0 6px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.row[data-on='true'] {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.row .t {
  font-size: 10px;
  color: var(--ink-muted);
  letter-spacing: 0.04em;
}

.row .meta {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.detail-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.eyebrow {
  margin: 0;
  font-size: 11px;
  color: var(--ink-muted);
}

.detail h3 {
  margin: 4px 0;
  font-size: var(--text-lg);
}

.summary {
  margin: 0;
  color: var(--ink-soft);
  line-height: 1.5;
}

.observer {
  margin: 0 0 var(--space-3);
  padding: 8px 10px;
  font-size: var(--text-sm);
  color: var(--ink-soft);
  background: var(--accent-soft);
  border-radius: 8px;
}

.kv {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
  margin: 0 0 var(--space-4);
}

.kv div {
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.kv dt {
  font-size: 10px;
  color: var(--ink-muted);
}

.kv dd {
  margin: 2px 0 0;
  font-weight: 600;
}

.note {
  color: var(--ink-soft);
  font-size: var(--text-sm);
}

.review {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: var(--space-4);
}

.review-btns {
  display: flex;
  gap: 8px;
}

.err {
  margin: 0;
  padding: 8px 12px;
  background: var(--danger-soft);
  border-left: 3px solid var(--danger);
  border-radius: 6px;
}

.empty,
.empty-panel {
  color: var(--ink-muted);
  font-size: var(--text-sm);
}

@media (max-width: 800px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
