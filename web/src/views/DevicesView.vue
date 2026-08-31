<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/client'
import { useLatestRequest, useRowAction } from '../composables/useLatestRequest'
import { useRealtimeStore } from '../stores/realtime'
import type { Device } from '../types/domain'

function sortDevices(list: Device[]): Device[] {
  return [...list].sort((a, b) => a.id - b.id)
}

const route = useRoute()
const realtime = useRealtimeStore()
const listRequest = useLatestRequest()
const rowAction = useRowAction()
const groupAction = useRowAction()
const records = ref<Device[]>([])
const total = ref(0)
const msg = ref('')
const form = reactive({ deviceName: '', deviceSn: '' })
const filter = reactive({ deviceName: '', status: '', onlineStatus: '' })
/** 新建编组时的临时输入（按设备 id） */
const draftGroup = reactive<Record<number, string>>({})

function applyRouteFilter() {
  filter.status = typeof route.query.status === 'string' ? route.query.status : ''
  filter.onlineStatus =
    typeof route.query.onlineStatus === 'string' ? route.query.onlineStatus : ''
}

async function load() {
  const res = await listRequest.run(() =>
    api.listDevices({ page: 1, pageSize: 50, ...filter }),
  )
  if (!res || res.code !== 200) return
  records.value = sortDevices(res.data.records)
  total.value = res.data.total
}

onMounted(async () => {
  applyRouteFilter()
  await load()
})

watch(
  () => route.query,
  async () => {
    applyRouteFilter()
    await load()
  },
)

watch(
  () => realtime.deviceSyncTick,
  async () => {
    await load()
  },
)

const grouped = computed(() => {
  const map = new Map<string, Device[]>()
  for (const d of records.value) {
    const name = d.groupName?.trim()
    if (!name) continue
    const list = map.get(name) ?? []
    list.push(d)
    map.set(name, list)
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b, 'zh'))
    .map(([name, devices]) => ({
      name,
      devices: sortDevices(devices),
      onCount: devices.filter((x) => x.status === 'ON').length,
      mode: groupMode(devices),
    }))
})

const groupNames = computed(() => grouped.value.map((g) => g.name))

const ungrouped = computed(() =>
  sortDevices(records.value.filter((d) => !d.groupName?.trim())),
)

/** 已分组 / 未分组 互斥视图，避免双区抢高度把编组挤没 */
const listTab = ref<'grouped' | 'ungrouped'>('grouped')

watch(
  [grouped, ungrouped],
  () => {
    if (listTab.value === 'grouped' && !grouped.value.length && ungrouped.value.length) {
      listTab.value = 'ungrouped'
    } else if (listTab.value === 'ungrouped' && !ungrouped.value.length && grouped.value.length) {
      listTab.value = 'grouped'
    }
  },
  { immediate: true },
)

function groupMode(devices: Device[]): 'AUTO' | 'MANUAL' | 'MIXED' {
  const modes = new Set(devices.map((d) => d.controlMode || 'AUTO'))
  if (modes.size === 1) return modes.has('MANUAL') ? 'MANUAL' : 'AUTO'
  return 'MIXED'
}

async function add() {
  const res = await api.addDevice({ ...form })
  msg.value = res.code === 200 ? res.data : res.errorMsg || '失败'
  if (res.code === 200) {
    form.deviceName = ''
    form.deviceSn = ''
    await load()
  }
}

async function toggle(d: Device) {
  const deviceId = d.id
  const deviceName = d.deviceName
  const next = d.status === 'ON' ? 'OFF' : 'ON'

  await rowAction.run(deviceId, async () => {
    records.value = records.value.map((row) =>
      row.id === deviceId ? { ...row, status: next, controlMode: 'MANUAL' } : row,
    )
    const res = await api.switchDevice(deviceId, next)
    if (res.code === 200) {
      msg.value = `${deviceName}：已下发 ${res.data.command}（手动模式）`
      await load()
    } else {
      msg.value = res.errorMsg || '失败'
      await load()
    }
  })
}

async function setMode(d: Device, mode: 'AUTO' | 'MANUAL') {
  if (d.controlMode === mode) return

  await rowAction.run(d.id, async () => {
    records.value = records.value.map((row) =>
      row.id === d.id ? { ...row, controlMode: mode } : row,
    )
    const res = await api.setControlMode(d.id, mode)
    if (res.code === 200) {
      msg.value =
        mode === 'AUTO'
          ? `${d.deviceName}：已切换为自动（跟随阈值）`
          : `${d.deviceName}：已切换为手动`
      await load()
    } else {
      msg.value = res.errorMsg || '失败'
      await load()
    }
  })
}

async function assignGroup(d: Device, groupName: string | null) {
  const name = groupName?.trim() || null
  if ((d.groupName || null) === name) return

  await rowAction.run(d.id, async () => {
    records.value = records.value.map((row) =>
      row.id === d.id ? { ...row, groupName: name } : row,
    )
    const res = await api.setDeviceGroup(d.id, name)
    if (res.code === 200) {
      msg.value = res.data
      draftGroup[d.id] = ''
      await load()
    } else {
      msg.value = res.errorMsg || '失败'
      await load()
    }
  })
}

function onGroupSelect(d: Device, value: string) {
  if (value === '__new__') {
    draftGroup[d.id] = ''
    return
  }
  void assignGroup(d, value || null)
}

async function confirmNewGroup(d: Device) {
  const name = (draftGroup[d.id] || '').trim()
  if (!name) {
    msg.value = '请输入编组名称'
    return
  }
  await assignGroup(d, name)
}

async function switchGroup(groupName: string, status: 'ON' | 'OFF') {
  await groupAction.run(groupName, async () => {
    const res = await api.switchGroup(groupName, status)
    if (res.code === 200) {
      msg.value = `编组「${groupName}」：已统一${status === 'ON' ? '开灯' : '关灯'}（${res.data.count} 台）`
      await load()
    } else {
      msg.value = res.errorMsg || '失败'
    }
  })
}

async function setGroupMode(groupName: string, mode: 'AUTO' | 'MANUAL') {
  await groupAction.run(groupName, async () => {
    const res = await api.setGroupControlMode(groupName, mode)
    if (res.code === 200) {
      msg.value = `编组「${groupName}」：已统一设为${mode === 'AUTO' ? '自动' : '手动'}（${res.data.count} 台）`
      await load()
    } else {
      msg.value = res.errorMsg || '失败'
    }
  })
}

async function remove(id: number) {
  await rowAction.run(id, async () => {
    await api.deleteDevice(id)
    msg.value = '已删除'
    await load()
  })
}

function isCreatingGroup(d: Device): boolean {
  return Object.prototype.hasOwnProperty.call(draftGroup, d.id)
}

function cancelCreateGroup(d: Device) {
  delete draftGroup[d.id]
}
</script>

<template>
  <div class="ui-page ui-page-fill devices-page">
    <div class="devices-toolbar slide-up-enter-active">
      <section class="ui-card toolbar-card">
        <h2 class="ui-card-title">添加设备</h2>
        <div class="ui-row">
          <input v-model="form.deviceName" class="ui-input" placeholder="设备名称" />
          <input
            v-model="form.deviceSn"
            class="ui-input mono"
            placeholder="deviceSn / MQTT 标识"
          />
          <button type="button" class="ui-btn" @click="add">添加</button>
        </div>
        <p class="hint-line">添加后可在棚内设备列表与冠层光场中查看状态。</p>
        <p v-if="msg" class="ui-msg">{{ msg }}</p>
      </section>

      <section class="ui-card toolbar-card">
        <h2 class="ui-card-title">
          设备列表 <span class="count mono">({{ total }})</span>
        </h2>
        <div class="ui-row">
          <input
            v-model="filter.deviceName"
            class="ui-input"
            placeholder="名称筛选"
            @keyup.enter="load"
          />
          <select v-model="filter.status" class="ui-select" @change="load">
            <option value="">开关 · 全部</option>
            <option value="ON">ON</option>
            <option value="OFF">OFF</option>
          </select>
          <select v-model="filter.onlineStatus" class="ui-select" @change="load">
            <option value="">在线 · 全部</option>
            <option value="ONLINE">ONLINE</option>
            <option value="OFFLINE">OFFLINE</option>
          </select>
          <button type="button" class="ui-btn ui-btn-secondary" @click="load">刷新</button>
        </div>
        <div class="list-tab-row">
          <div class="mode-seg" role="tablist" aria-label="设备列表范围">
            <button
              type="button"
              class="seg-btn"
              role="tab"
              :class="{ on: listTab === 'grouped' }"
              :aria-selected="listTab === 'grouped'"
              @click="listTab = 'grouped'"
            >
              已分组
              <span class="seg-count mono">{{ grouped.reduce((n, g) => n + g.devices.length, 0) }}</span>
            </button>
            <button
              type="button"
              class="seg-btn"
              role="tab"
              :class="{ on: listTab === 'ungrouped' }"
              :aria-selected="listTab === 'ungrouped'"
              @click="listTab = 'ungrouped'"
            >
              未分组
              <span class="seg-count mono">{{ ungrouped.length }}</span>
            </button>
          </div>
          <p class="hint-line">
            {{
              listTab === 'grouped'
                ? '查看编组托盘，可对整组统一开关与模式。'
                : '未分组设备可在此加入或新建编组。'
            }}
          </p>
        </div>
      </section>
    </div>

    <div class="ui-fill-body devices-body slide-up-enter-active slide-up-delay-1">
    <section v-if="listTab === 'grouped'" class="ui-card groups-shell slide-up-enter-active">
      <h2 class="ui-card-title">已分组</h2>
      <p v-if="!grouped.length" class="ui-empty">暂无已分组设备，可在「未分组」中加入编组。</p>
      <template v-else>
      <p class="groups-shell-hint">同名编组集中在此，可对整组统一开关与模式。</p>
      <div class="ui-groups-stack">
    <section
      v-for="g in grouped"
      :key="g.name"
      class="group-tray slide-up-enter-active"
    >
      <header class="group-head">
        <div class="group-title-block">
          <h3 class="group-title">{{ g.name }}</h3>
          <p class="group-meta">
            {{ g.devices.length }} 台 · 开灯 {{ g.onCount }} ·
            {{ g.mode === 'MIXED' ? '模式不一' : g.mode === 'AUTO' ? '自动' : '手动' }}
          </p>
        </div>
        <div class="ui-action-bar group-controls">
          <button
            type="button"
            class="ui-btn ui-btn-compact"
            :disabled="groupAction.isActive(g.name)"
            @click="switchGroup(g.name, 'ON')"
          >
            全开
          </button>
          <button
            type="button"
            class="ui-btn ui-btn-compact ui-btn-secondary"
            :disabled="groupAction.isActive(g.name)"
            @click="switchGroup(g.name, 'OFF')"
          >
            全关
          </button>
          <div class="ui-segment" role="group" aria-label="编组控制模式">
            <button
              type="button"
              :class="{ on: g.mode === 'AUTO' }"
              :disabled="groupAction.isActive(g.name)"
              @click="setGroupMode(g.name, 'AUTO')"
            >
              自动
            </button>
            <button
              type="button"
              :class="{ on: g.mode === 'MANUAL' }"
              :disabled="groupAction.isActive(g.name)"
              @click="setGroupMode(g.name, 'MANUAL')"
            >
              手动
            </button>
          </div>
        </div>
      </header>

      <div class="ui-table-panel ui-table-panel--group">
        <div class="ui-table-wrap">
        <table class="ui-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>SN</th>
              <th>开关</th>
              <th>一致</th>
              <th>在线</th>
              <th>编组</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in g.devices" :key="d.id">
              <td class="col-name">{{ d.deviceName }}</td>
              <td class="col-mono mono">{{ d.deviceSn }}</td>
              <td>
                <span class="ui-pill" :data-on="d.status === 'ON'">{{ d.status }}</span>
              </td>
              <td>
                <span
                  class="ui-badge"
                  :class="d.statusMatch === false ? 'timeout' : 'ok'"
                  :title="
                    d.expectedStatus
                      ? `期望 ${d.expectedStatus} / 实际 ${d.status}`
                      : '暂无期望状态'
                  "
                >
                  {{ d.statusMatch === false ? '不一致' : '一致' }}
                </span>
              </td>
              <td class="col-mono">{{ d.onlineStatus }}</td>
              <td class="group-cell">
                <template v-if="isCreatingGroup(d)">
                  <div class="new-group">
                    <input
                      v-model="draftGroup[d.id]"
                      class="ui-input"
                      placeholder="新编组名"
                      @keyup.enter="confirmNewGroup(d)"
                    />
                    <button
                      type="button"
                      class="ui-btn ui-btn-compact"
                      :disabled="rowAction.isActive(d.id)"
                      @click="confirmNewGroup(d)"
                    >
                      确定
                    </button>
                    <button
                      type="button"
                      class="ui-btn ui-btn-compact ui-btn-secondary"
                      @click="cancelCreateGroup(d)"
                    >
                      取消
                    </button>
                  </div>
                </template>
                <select
                  v-else
                  class="ui-select group-select"
                  :value="d.groupName || ''"
                  :disabled="rowAction.isActive(d.id)"
                  @change="onGroupSelect(d, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="">未分组</option>
                  <option v-for="name in groupNames" :key="name" :value="name">{{ name }}</option>
                  <option value="__new__">新建编组…</option>
                </select>
              </td>
              <td class="col-actions">
                <div class="ui-action-bar">
                  <button
                    type="button"
                    class="ui-btn ui-btn-compact"
                    :disabled="rowAction.isActive(d.id)"
                    @click="toggle(d)"
                  >
                    {{ rowAction.isActive(d.id) ? '…' : d.status === 'ON' ? '关灯' : '开灯' }}
                  </button>
                  <div class="ui-segment" role="group" aria-label="控制模式">
                    <button
                      type="button"
                      :class="{ on: d.controlMode !== 'MANUAL' }"
                      :disabled="rowAction.isActive(d.id)"
                      @click="setMode(d, 'AUTO')"
                    >
                      自动
                    </button>
                    <button
                      type="button"
                      :class="{ on: d.controlMode === 'MANUAL' }"
                      :disabled="rowAction.isActive(d.id)"
                      @click="setMode(d, 'MANUAL')"
                    >
                      手动
                    </button>
                  </div>
                  <button
                    type="button"
                    class="ui-btn ui-btn-compact ui-btn-danger"
                    :disabled="rowAction.isActive(d.id)"
                    @click="remove(d.id)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </section>
      </div>
      </template>
    </section>

    <!-- 未分组 -->
    <section v-else class="ui-card ungrouped-card slide-up-enter-active">
      <h2 class="ui-card-title">未分组</h2>
      <p v-if="!ungrouped.length" class="ui-empty">暂无未分组设备</p>
      <div v-else class="ui-table-panel ui-table-panel--fill">
        <div class="ui-table-wrap">
        <table class="ui-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>SN</th>
              <th>开关</th>
              <th>一致</th>
              <th>在线</th>
              <th>编组</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in ungrouped" :key="d.id">
              <td class="col-name">{{ d.deviceName }}</td>
              <td class="col-mono mono">{{ d.deviceSn }}</td>
              <td>
                <span class="ui-pill" :data-on="d.status === 'ON'">{{ d.status }}</span>
              </td>
              <td>
                <span
                  class="ui-badge"
                  :class="d.statusMatch === false ? 'timeout' : 'ok'"
                  :title="
                    d.expectedStatus
                      ? `期望 ${d.expectedStatus} / 实际 ${d.status}`
                      : '暂无期望状态'
                  "
                >
                  {{ d.statusMatch === false ? '不一致' : '一致' }}
                </span>
              </td>
              <td class="col-mono">{{ d.onlineStatus }}</td>
              <td class="group-cell">
                <template v-if="isCreatingGroup(d)">
                  <div class="new-group">
                    <input
                      v-model="draftGroup[d.id]"
                      class="ui-input"
                      placeholder="新编组名"
                      @keyup.enter="confirmNewGroup(d)"
                    />
                    <button
                      type="button"
                      class="ui-btn ui-btn-compact"
                      :disabled="rowAction.isActive(d.id)"
                      @click="confirmNewGroup(d)"
                    >
                      确定
                    </button>
                    <button
                      type="button"
                      class="ui-btn ui-btn-compact ui-btn-secondary"
                      @click="cancelCreateGroup(d)"
                    >
                      取消
                    </button>
                  </div>
                </template>
                <select
                  v-else
                  class="ui-select group-select"
                  :value="d.groupName || ''"
                  :disabled="rowAction.isActive(d.id)"
                  @change="onGroupSelect(d, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="">未分组</option>
                  <option v-for="name in groupNames" :key="name" :value="name">{{ name }}</option>
                  <option value="__new__">新建编组…</option>
                </select>
              </td>
              <td class="col-actions">
                <div class="ui-action-bar">
                  <button
                    type="button"
                    class="ui-btn ui-btn-compact"
                    :disabled="rowAction.isActive(d.id)"
                    @click="toggle(d)"
                  >
                    {{ rowAction.isActive(d.id) ? '…' : d.status === 'ON' ? '关灯' : '开灯' }}
                  </button>
                  <div class="ui-segment" role="group" aria-label="控制模式">
                    <button
                      type="button"
                      :class="{ on: d.controlMode !== 'MANUAL' }"
                      :disabled="rowAction.isActive(d.id)"
                      @click="setMode(d, 'AUTO')"
                    >
                      自动
                    </button>
                    <button
                      type="button"
                      :class="{ on: d.controlMode === 'MANUAL' }"
                      :disabled="rowAction.isActive(d.id)"
                      @click="setMode(d, 'MANUAL')"
                    >
                      手动
                    </button>
                  </div>
                  <button
                    type="button"
                    class="ui-btn ui-btn-compact ui-btn-danger"
                    :disabled="rowAction.isActive(d.id)"
                    @click="remove(d.id)"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
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

.list-tab-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.mode-seg {
  display: inline-flex;
  padding: 3px;
  background: var(--paper);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-inset);
}

.seg-btn {
  border: none;
  background: transparent;
  padding: 8px 14px;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--ink-muted);
  border-radius: calc(var(--radius-md) - 2px);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition:
    background var(--duration-fast) var(--ease-out),
    color var(--duration-fast);
}

.seg-btn:hover {
  color: var(--ink);
}

.seg-btn.on {
  background: var(--panel);
  color: var(--ink);
  box-shadow: var(--shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.06));
}

.seg-count {
  font-size: 11px;
  color: var(--ink-muted);
  font-weight: 600;
}

.seg-btn.on .seg-count {
  color: var(--sodium-deep);
}

.hint-line {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.hint-line a {
  color: var(--accent);
  text-decoration: none;
  font-weight: 600;
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
  margin-bottom: var(--space-1);
}

.groups-shell-hint {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--ink-muted);
  flex-shrink: 0;
}

.groups-shell .ui-groups-stack {
  flex: 1 1 0;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}

.ungrouped-card {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ungrouped-card .ui-card-title {
  flex-shrink: 0;
  margin-bottom: var(--space-2);
}

.ungrouped-card .ui-table-panel--fill {
  flex: 1 1 0;
  min-height: 0;
}

.ungrouped-card .ui-table-wrap {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
}

.count {
  font-size: var(--text-base);
  font-weight: 400;
  color: var(--ink-muted);
}

.group-tray {
  padding: var(--space-4);
  background: var(--panel-secondary);
  box-shadow: none;
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
  padding: 0 var(--space-1);
}

.group-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  letter-spacing: var(--tracking-tight);
}

.group-meta {
  margin: 4px 0 0;
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.group-controls {
  flex-shrink: 0;
}

.group-cell {
  min-width: 140px;
}

.group-select {
  min-width: 120px;
  padding: 6px 10px;
  font-size: var(--text-sm);
}

.new-group {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: nowrap;
}

.new-group .ui-input {
  width: 120px;
  padding: 6px 10px;
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .devices-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
