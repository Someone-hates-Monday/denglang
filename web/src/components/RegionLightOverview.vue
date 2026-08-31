<script setup lang="ts">
import type { RegionRow } from '../scene/dayCurveLayers'

defineProps<{
  rows: RegionRow[]
  selectedId: string
}>()

const emit = defineEmits<{
  select: [row: RegionRow]
}>()
</script>

<template>
  <div class="region-overview">
    <div class="head">
      <h3 class="ui-section-title">分区光照一览</h3>
      <span class="hint"
        >点击行切换视角 · 缺口 = 实况 − 目标中值 · 同半跨内南北床直射差属正常，需 AUTO 分床补光</span
      >
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>区域</th>
            <th>PAR</th>
            <th>目标带</th>
            <th>缺口</th>
            <th>遮阳</th>
            <th>补光</th>
            <th>策略</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
            :class="[row.kind, { active: selectedId === row.id }]"
            @click="emit('select', row)"
          >
            <td class="name">{{ row.label }}</td>
            <td class="mono">{{ row.ppfd.toFixed(0) }}</td>
            <td class="mono muted"
              >{{ row.targetMin.toFixed(0) }}–{{ row.targetMax.toFixed(0) }}</td
            >
            <td class="mono" :data-sign="row.gap >= 0 ? 'pos' : 'neg'">
              {{ row.gap >= 0 ? '+' : '' }}{{ row.gap.toFixed(0) }}
            </td>
            <td class="mono">{{ row.shadePct }}%</td>
            <td class="mono">{{ row.dimPct }}%</td>
            <td><span class="pill" :data-s="row.status">{{ row.statusZh }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.region-overview {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.head .ui-section-title {
  margin: 0;
}

.hint {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

th,
td {
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid var(--border-subtle, rgba(0, 0, 0, 0.06));
}

th {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--ink-muted);
}

tr {
  cursor: pointer;
}

tr:hover td {
  background: rgba(0, 113, 227, 0.04);
}

tr.active td {
  background: rgba(0, 113, 227, 0.08);
}

tr.bed td.name {
  padding-left: 1.25rem;
  font-size: var(--text-xs);
}

tr.zone td.name {
  font-weight: 600;
}

tr.bay td.name {
  font-weight: 700;
}

.mono {
  font-family: ui-monospace, monospace;
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--ink-muted);
}

td[data-sign='neg'] {
  color: #0071e3;
}

td[data-sign='pos'] {
  color: #ff9500;
}

.pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--ink-muted);
  white-space: nowrap;
}

.pill[data-s='ok'] {
  background: rgba(52, 199, 89, 0.15);
  color: #248a3d;
}

.pill[data-s='low'] {
  background: rgba(0, 113, 227, 0.12);
  color: #0071e3;
}

.pill[data-s='high'] {
  background: rgba(255, 149, 0, 0.15);
  color: #c93400;
}

.pill[data-s='off'] {
  background: rgba(0, 0, 0, 0.06);
  color: #86868b;
}
</style>
