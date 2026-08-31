import type {
  AlarmLog,
  AlarmStatistics,
  ControlLog,
  Device,
  DeviceDetail,
  DeviceStatistics,
  EffectiveThreshold,
  LatestLight,
  LightReading,
  PageResult,
  Role,
  ThresholdConfig,
  ThresholdOverride,
  TrendPoint,
  UserSession,
} from '../types/domain'
import { fail, ok, type StreetLightApi } from './types'

const now = () => {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

let users: { username: string; password: string; role: Role; id: number }[] = [
  { id: 1, username: 'admin', password: 'admin123', role: 'SYS_ADMIN' },
  { id: 2, username: 'changzhang', password: 'demo123', role: 'SITE_MANAGER' },
  { id: 3, username: 'nongyi', password: 'demo123', role: 'AGRONOMIST' },
  { id: 4, username: 'zhongzhi', password: 'demo123', role: 'GROWER' },
  { id: 5, username: 'yunwei', password: 'demo123', role: 'DEVICE_OPS' },
  { id: 6, username: 'xueyuan', password: 'demo123', role: 'TRAINEE' },
  { id: 7, username: 'staff', password: 'staff123', role: 'GROWER' },
]

let devices: Device[] = [
  {
    id: 1,
    deviceName: '人民路001号路灯',
    deviceSn: 'SN-RM-001',
    status: 'OFF',
    onlineStatus: 'OFFLINE',
    controlMode: 'AUTO',
    groupName: '人民路',
    latitude: 29.5647,
    longitude: 106.4674,
    expectedStatus: 'ON',
    statusMatch: false,
    lastHeartbeatTime: '2026-08-25 07:00:00',
    createdAt: '2026-07-26 10:00:00',
  },
  {
    id: 2,
    deviceName: '人民路002号路灯',
    deviceSn: 'SN-RM-002',
    status: 'ON',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: '人民路',
    latitude: 29.56485,
    longitude: 106.4682,
    expectedStatus: 'ON',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-07-31 10:00:00',
  },
  {
    id: 3,
    deviceName: '人民路003号路灯',
    deviceSn: 'SN-RM-003',
    status: 'OFF',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: '人民路',
    latitude: 29.565,
    longitude: 106.469,
    expectedStatus: 'OFF',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-05 10:00:00',
  },
  {
    id: 4,
    deviceName: '解放大道东段灯',
    deviceSn: 'SN-JF-001',
    status: 'ON',
    onlineStatus: 'ONLINE',
    controlMode: 'MANUAL',
    groupName: '解放大道',
    latitude: 29.5662,
    longitude: 106.4686,
    expectedStatus: 'ON',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-07 10:00:00',
  },
  {
    id: 5,
    deviceName: '解放大道西段灯',
    deviceSn: 'SN-JF-002',
    status: 'OFF',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: '解放大道',
    latitude: 29.5664,
    longitude: 106.4695,
    expectedStatus: 'OFF',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-10 10:00:00',
  },
  {
    id: 6,
    deviceName: '滨江步道A灯',
    deviceSn: 'SN-BJ-001',
    status: 'OFF',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: '滨江路',
    latitude: 29.5635,
    longitude: 106.4678,
    expectedStatus: 'OFF',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-13 10:00:00',
  },
  {
    id: 7,
    deviceName: '滨江步道B灯',
    deviceSn: 'SN-BJ-002',
    status: 'ON',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: '滨江路',
    latitude: 29.5633,
    longitude: 106.4687,
    expectedStatus: 'OFF',
    statusMatch: false,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-13 10:00:00',
  },
  {
    id: 8,
    deviceName: '校园主道路灯',
    deviceSn: 'SN-XQ-001',
    status: 'OFF',
    onlineStatus: 'ONLINE',
    controlMode: 'AUTO',
    groupName: null,
    latitude: 29.5654,
    longitude: 106.4698,
    expectedStatus: 'OFF',
    statusMatch: true,
    lastHeartbeatTime: now(),
    createdAt: '2026-08-15 10:00:00',
  },
]

/** 城市道路昼夜 lux 近似（含偏置与噪声） */
function diurnalLux(hour: number, minute: number, bias: number, seed: number): number {
  let base = 20
  if (hour <= 4) base = 1.5 + minute / 60
  else if (hour === 5) base = 6 + minute * 0.4
  else if (hour === 6) base = 35 + minute * 0.9
  else if (hour === 7) base = 90 + minute * 2
  else if (hour >= 8 && hour <= 10) base = 280 + (hour - 8) * 80 + minute * 0.8
  else if (hour >= 11 && hour <= 13) base = 720 + (12 - Math.abs(hour - 12)) * 40 + Math.sin(minute / 10) * 30
  else if (hour >= 14 && hour <= 16) base = 480 - (hour - 14) * 70 + minute * 0.5
  else if (hour === 17) base = 160 - minute * 1.5
  else if (hour === 18) base = 70 - minute * 0.8
  else if (hour === 19) base = 28 - minute * 0.35
  else if (hour >= 20) base = 8 - (hour - 20) * 1.2 + minute * 0.02
  const noise = ((seed * 17 + hour * 3 + minute) % 11) - 5
  return Math.max(0.2, Math.round((base + bias + noise) * 100) / 100)
}

const deviceMeta = [
  { id: 1, name: '人民路001号路灯', bias: -8 },
  { id: 2, name: '人民路002号路灯', bias: 5 },
  { id: 3, name: '人民路003号路灯', bias: 12 },
  { id: 4, name: '解放大道东段灯', bias: -3 },
  { id: 5, name: '解放大道西段灯', bias: 0 },
  { id: 6, name: '滨江步道A灯', bias: 18 },
  { id: 7, name: '滨江步道B灯', bias: 10 },
  { id: 8, name: '校园主道路灯', bias: -15 },
]

let lights: LightReading[] = []
let lightSeq = 1
const today = new Date()
for (let day = 0; day < 3; day++) {
  const d = new Date(today)
  d.setDate(d.getDate() - day)
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  for (let h = 0; h < 24; h++) {
    for (const min of [0, 30]) {
      // 西段离线：最近 3 小时无数据
      const ageHours = day * 24 + (today.getHours() - h) + (today.getMinutes() - min) / 60
      for (const meta of deviceMeta) {
        if (meta.id === 5 && day === 0 && ageHours < 3) continue
        lights.push({
          id: lightSeq++,
          deviceId: meta.id,
          deviceName: meta.name,
          lightIntensity: diurnalLux(h, min, meta.bias, meta.id + day),
          createdAt: `${y}-${mo}-${dd} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}:00`,
        })
      }
    }
  }
}
// 对齐「最新」采样与演示开关态
function bumpLatest(deviceId: number, lux: number) {
  let last = -1
  for (let i = 0; i < lights.length; i++) if (lights[i].deviceId === deviceId) last = i
  if (last >= 0) lights[last] = { ...lights[last], lightIntensity: lux }
}
bumpLatest(2, 12.4)
bumpLatest(3, 356.2)
bumpLatest(4, 520.1)
bumpLatest(6, 412.8)
bumpLatest(7, 95.6)
bumpLatest(8, 268.5)

let alarms: AlarmLog[] = [
  {
    id: '1',
    deviceId: 5,
    deviceName: '解放大道西段灯',
    alarmType: 'OFFLINE',
    message: '设备解放大道西段灯历史心跳超时（已恢复在线）',
    status: 'RESOLVED',
    createdAt: '2026-08-24 10:00:00',
    resolvedAt: '2026-08-24 10:40:00',
  },
  {
    id: '2',
    deviceId: 1,
    deviceName: '人民路001号路灯',
    alarmType: 'OFFLINE',
    message: '设备人民路001号路灯心跳超时，已自动标记为离线',
    status: 'ACTIVE',
    createdAt: '2026-08-25 08:20:00',
    resolvedAt: null,
  },
  {
    id: '3',
    deviceId: 1,
    deviceName: '人民路001号路灯',
    alarmType: 'COMMAND_TIMEOUT',
    message: '设备人民路001号路灯指令 ON 超过 30s 未收到 status 回执',
    status: 'ACTIVE',
    createdAt: '2026-08-25 08:32:00',
    resolvedAt: null,
  },
  {
    id: '4',
    deviceId: 7,
    deviceName: '滨江步道B灯',
    alarmType: 'LIGHT_ABNORMAL',
    message: '设备滨江步道B灯高光照下仍保持开灯，期望与实际不一致',
    status: 'ACTIVE',
    createdAt: '2026-08-25 09:50:00',
    resolvedAt: null,
  },
  {
    id: '5',
    deviceId: 2,
    deviceName: '人民路002号路灯',
    alarmType: 'HEARTBEAT_TIMEOUT',
    message: '设备人民路002号路灯短暂心跳丢失后已恢复',
    status: 'RESOLVED',
    createdAt: '2026-08-23 22:10:00',
    resolvedAt: '2026-08-23 22:30:00',
  },
]

let threshold: ThresholdConfig = {
  id: 1,
  lightThresholdOn: 30,
  lightThresholdOff: 80,
  heartbeatTimeout: 180,
  updatedAt: now(),
}

let thresholdOverrides: ThresholdOverride[] = [
  {
    id: '1',
    scopeType: 'GROUP',
    scopeKey: '人民路',
    scopeLabel: '人民路',
    lightThresholdOn: 25,
    lightThresholdOff: 70,
    updatedAt: now(),
  },
]

let controlLogs: ControlLog[] = [
  {
    id: 1,
    deviceId: 2,
    deviceName: '人民路002号路灯',
    operatorId: null,
    operatorName: null,
    command: 'ON',
    source: 'AUTO',
    result: 'SUCCESS',
    executionStatus: 'SUCCESS',
    expectedStatus: 'ON',
    createdAt: now(),
  },
  {
    id: 2,
    deviceId: 3,
    deviceName: '人民路003号路灯',
    operatorId: null,
    operatorName: null,
    command: 'OFF',
    source: 'AUTO',
    result: 'SUCCESS',
    executionStatus: 'SUCCESS',
    expectedStatus: 'OFF',
    createdAt: '2026-08-25 09:20:00',
  },
  {
    id: 3,
    deviceId: 7,
    deviceName: '滨江步道B灯',
    operatorId: null,
    operatorName: null,
    command: 'OFF',
    source: 'AUTO',
    result: 'SUCCESS',
    executionStatus: 'SUCCESS',
    expectedStatus: 'OFF',
    createdAt: '2026-08-25 09:48:00',
  },
  {
    id: 4,
    deviceId: 4,
    deviceName: '解放大道东段灯',
    operatorId: 1,
    operatorName: 'admin',
    command: 'ON',
    source: 'MANUAL',
    result: 'SUCCESS',
    executionStatus: 'SUCCESS',
    expectedStatus: 'ON',
    createdAt: '2026-08-25 08:00:00',
  },
  {
    id: 5,
    deviceId: 1,
    deviceName: '人民路001号路灯',
    operatorId: 1,
    operatorName: 'admin',
    command: 'ON',
    source: 'MANUAL',
    result: 'SUCCESS',
    executionStatus: 'TIMEOUT',
    expectedStatus: 'ON',
    createdAt: '2026-08-25 08:30:00',
  },
]
let controlSeq = 6

function pageOf<T>(list: T[], page = 1, pageSize = 10): PageResult<T> {
  const start = (page - 1) * pageSize
  return { total: list.length, records: list.slice(start, start + pageSize) }
}

function withMatch(d: Device): Device {
  const expected = d.expectedStatus ?? null
  const statusMatch = !expected || expected === d.status
  return { ...d, expectedStatus: expected, statusMatch }
}

export function createMockApi(): StreetLightApi {
  return {
    async register(username, password, role = 'GROWER') {
      if (!username.trim() || !password.trim()) return fail('用户名和密码不能为空')
      if (users.some((u) => u.username === username)) return fail('用户名已存在')
      users.push({ id: users.length + 1, username, password, role })
      return ok('注册成功')
    },
    async login(username, password) {
      const u = users.find((x) => x.username === username && x.password === password)
      if (!u) return fail('用户名或密码错误')
      return ok({
        token: `mock-${u.role}-${Date.now()}`,
        userId: u.id,
        username: u.username,
        role: u.role,
      } satisfies UserSession)
    },
    async listDevices(params) {
      let list = devices.map(withMatch)
      if (params.deviceName) list = list.filter((d) => d.deviceName.includes(params.deviceName!))
      if (params.status) list = list.filter((d) => d.status === params.status)
      if (params.onlineStatus) list = list.filter((d) => d.onlineStatus === params.onlineStatus)
      return ok(pageOf(list, params.page, params.pageSize))
    },
    async getDevice(id) {
      const d = devices.find((x) => x.id === id)
      if (!d) return fail('设备不存在')
      const latest = [...lights].reverse().find((l) => l.deviceId === id)
      const detail: DeviceDetail = {
        ...withMatch(d),
        latestLightIntensity: latest?.lightIntensity ?? null,
        activeAlarmCount: alarms.filter((a) => a.deviceId === id && a.status === 'ACTIVE').length,
      }
      return ok(detail)
    },
    async addDevice(body) {
      if (devices.some((d) => d.deviceSn === body.deviceSn)) return fail('序列号已存在')
      const d: Device = {
        id: devices.length + 1,
        deviceName: body.deviceName,
        deviceSn: body.deviceSn,
        status: 'OFF',
        onlineStatus: 'OFFLINE',
        controlMode: 'AUTO',
        groupName: null,
        latitude: body.latitude ?? null,
        longitude: body.longitude ?? null,
        lastHeartbeatTime: null,
        createdAt: now(),
      }
      devices = [d, ...devices]
      return ok('添加成功')
    },
    async updateDevice(id, body) {
      devices = devices.map((d) => (d.id === id ? { ...d, deviceName: body.deviceName } : d))
      return ok('修改成功')
    },
    async setDeviceLocation(id, body) {
      const d = devices.find((x) => x.id === id)
      if (!d) return fail('设备不存在')
      const lat = body.latitude
      const lng = body.longitude
      if ((lat == null) !== (lng == null)) return fail('经纬度必须同时填写')
      devices = devices.map((x) =>
        x.id === id ? { ...x, latitude: lat, longitude: lng } : x,
      )
      return ok(lat == null ? '已清除位置' : '位置已更新')
    },
    async deleteDevice(id) {
      devices = devices.filter((d) => d.id !== id)
      return ok('删除成功')
    },
    async deviceStatistics() {
      const stats: DeviceStatistics = {
        totalCount: devices.length,
        onlineCount: devices.filter((d) => d.onlineStatus === 'ONLINE').length,
        offlineCount: devices.filter((d) => d.onlineStatus === 'OFFLINE').length,
        onCount: devices.filter((d) => d.status === 'ON').length,
        offCount: devices.filter((d) => d.status === 'OFF').length,
      }
      return ok(stats)
    },
    async switchDevice(id, status) {
      const deviceId = Number(id)
      const d = devices.find((x) => x.id === deviceId)
      if (!d) return fail('设备不存在')
      devices = devices.map((x) =>
        x.id === deviceId
          ? withMatch({ ...x, status, controlMode: 'MANUAL', expectedStatus: status })
          : x,
      )
      controlLogs = [
        {
          id: controlSeq++,
          deviceId: id,
          deviceName: d.deviceName,
          operatorId: 1,
          operatorName: 'admin',
          command: status === 'ON' ? 'MANUAL_ON' : 'MANUAL_OFF',
          source: 'MANUAL',
          result: 'SUCCESS',
          executionStatus: 'SUCCESS',
          expectedStatus: status,
          createdAt: now(),
        },
        ...controlLogs,
      ]
      return ok({ command: status === 'ON' ? 'MANUAL_ON' : 'MANUAL_OFF', controlMode: 'MANUAL' })
    },
    async setControlMode(id, mode) {
      const deviceId = Number(id)
      if (!devices.find((x) => x.id === deviceId)) return fail('设备不存在')
      devices = devices.map((x) => (x.id === deviceId ? { ...x, controlMode: mode } : x))
      return ok('模式已更新为 ' + mode)
    },
    async setDeviceGroup(id, groupName) {
      const deviceId = Number(id)
      if (!devices.find((x) => x.id === deviceId)) return fail('设备不存在')
      const name = groupName?.trim() || null
      devices = devices.map((x) => (x.id === deviceId ? { ...x, groupName: name } : x))
      return ok(name ? `已加入编组 ${name}` : '已移出编组')
    },
    async switchGroup(groupName, status) {
      const name = groupName.trim()
      const members = devices.filter((d) => d.groupName === name)
      if (!members.length) return fail('编组不存在或组内无设备')
      for (const m of members) {
        await this.switchDevice(m.id, status)
      }
      return ok({
        count: members.length,
        command: status === 'ON' ? 'MANUAL_ON' : 'MANUAL_OFF',
        controlMode: 'MANUAL',
      })
    },
    async setGroupControlMode(groupName, mode) {
      const name = groupName.trim()
      const members = devices.filter((d) => d.groupName === name)
      if (!members.length) return fail('编组不存在或组内无设备')
      for (const m of members) {
        await this.setControlMode(m.id, mode)
      }
      return ok({ count: members.length, mode })
    },
    async listLightReadings(params) {
      let list = [...lights].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
      if (params.deviceId) {
        list = list.filter((l) => l.deviceId === Number(params.deviceId))
      } else if (params.groupName) {
        const ids = new Set(
          devices.filter((d) => d.groupName === params.groupName).map((d) => d.id),
        )
        list = list.filter((l) => ids.has(l.deviceId))
      }
      return ok(pageOf(list, params.page, params.pageSize))
    },
    async latestLight(deviceId) {
      const id = Number(deviceId)
      const latest = [...lights].reverse().find((l) => l.deviceId === id)
      if (!latest) return fail('暂无光照数据')
      return ok({
        deviceId,
        lightIntensity: latest.lightIntensity,
        createdAt: latest.createdAt,
      } satisfies LatestLight)
    },
    async lightTrend(params) {
      let scoped = [...lights]
      if (params.deviceId != null) {
        const id = Number(params.deviceId)
        scoped = scoped.filter((l) => l.deviceId === id)
        let points: TrendPoint[] = scoped.map((l) => ({ time: l.createdAt, value: l.lightIntensity }))
        if (params.startTime) points = points.filter((p) => p.time >= params.startTime)
        if (params.endTime) points = points.filter((p) => p.time <= params.endTime)
        return ok(points)
      }
      if (params.groupName) {
        const ids = new Set(
          devices.filter((d) => d.groupName === params.groupName).map((d) => d.id),
        )
        scoped = scoped.filter((l) => ids.has(l.deviceId))
      }
      const buckets = new Map<string, number[]>()
      for (const l of scoped) {
        if (params.startTime && l.createdAt < params.startTime) continue
        if (params.endTime && l.createdAt > params.endTime) continue
        const key = l.createdAt.slice(0, 16) + ':00'
        const arr = buckets.get(key) ?? []
        arr.push(l.lightIntensity)
        buckets.set(key, arr)
      }
      const points: TrendPoint[] = [...buckets.entries()]
        .sort((a, b) => (a[0] < b[0] ? -1 : 1))
        .map(([time, vals]) => ({
          time,
          value: vals.reduce((s, v) => s + v, 0) / vals.length,
        }))
      return ok(points)
    },
    async listAlarms(params) {
      let list = [...alarms]
      if (params.deviceId) list = list.filter((a) => a.deviceId === params.deviceId)
      if (params.alarmType) list = list.filter((a) => a.alarmType === params.alarmType)
      if (params.status) list = list.filter((a) => a.status === params.status)
      return ok(pageOf(list, params.page, params.pageSize))
    },
    async resolveAlarm(id) {
      const hit = alarms.find((a) => a.id === id)
      if (!hit) return fail('告警不存在')
      if (hit.status === 'RESOLVED') return fail('已解决')
      alarms = alarms.map((a) =>
        a.id === id ? { ...a, status: 'RESOLVED', resolvedAt: now() } : a,
      )
      return ok('处理成功')
    },
    async alarmStatistics() {
      const active = alarms.filter((a) => a.status === 'ACTIVE')
      const map = new Map<string, number>()
      for (const a of active) map.set(a.alarmType, (map.get(a.alarmType) ?? 0) + 1)
      const stats: AlarmStatistics = {
        activeCount: active.length,
        byType: [...map.entries()].map(([alarmType, count]) => ({ alarmType, count })),
      }
      return ok(stats)
    },
    async getThreshold() {
      return ok(threshold)
    },
    async updateThreshold(body) {
      if (body.lightThresholdOn >= body.lightThresholdOff) return fail('开灯阈值必须小于关灯阈值')
      threshold = { ...threshold, ...body, updatedAt: now() }
      return ok('更新成功')
    },
    async listThresholdOverrides() {
      return ok([...thresholdOverrides])
    },
    async upsertThresholdOverride(body) {
      if (body.lightThresholdOn >= body.lightThresholdOff) return fail('开灯阈值必须小于关灯阈值')
      const label =
        body.scopeType === 'DEVICE'
          ? devices.find((d) => String(d.id) === body.scopeKey)?.deviceName ?? body.scopeKey
          : body.scopeKey
      const hit = thresholdOverrides.find(
        (o) => o.scopeType === body.scopeType && o.scopeKey === body.scopeKey,
      )
      if (hit) {
        hit.lightThresholdOn = body.lightThresholdOn
        hit.lightThresholdOff = body.lightThresholdOff
        hit.scopeLabel = label
        hit.updatedAt = now()
      } else {
        thresholdOverrides.push({
          id: String(Date.now()),
          scopeType: body.scopeType,
          scopeKey: body.scopeKey,
          scopeLabel: label,
          lightThresholdOn: body.lightThresholdOn,
          lightThresholdOff: body.lightThresholdOff,
          updatedAt: now(),
        })
      }
      return ok('覆盖已保存')
    },
    async deleteThresholdOverride(scopeType, scopeKey) {
      thresholdOverrides = thresholdOverrides.filter(
        (o) => !(o.scopeType === scopeType && o.scopeKey === scopeKey),
      )
      return ok('覆盖已删除')
    },
    async getEffectiveThreshold(deviceId) {
      const d = devices.find((x) => x.id === Number(deviceId))
      const deviceOv = thresholdOverrides.find(
        (o) => o.scopeType === 'DEVICE' && o.scopeKey === String(deviceId),
      )
      if (deviceOv) {
        return ok({
          lightThresholdOn: deviceOv.lightThresholdOn,
          lightThresholdOff: deviceOv.lightThresholdOff,
          source: 'DEVICE',
          sourceKey: String(deviceId),
        } satisfies EffectiveThreshold)
      }
      const group = d?.groupName?.trim()
      if (group) {
        const groupOv = thresholdOverrides.find(
          (o) => o.scopeType === 'GROUP' && o.scopeKey === group,
        )
        if (groupOv) {
          return ok({
            lightThresholdOn: groupOv.lightThresholdOn,
            lightThresholdOff: groupOv.lightThresholdOff,
            source: 'GROUP',
            sourceKey: group,
          } satisfies EffectiveThreshold)
        }
      }
      return ok({
        lightThresholdOn: threshold.lightThresholdOn,
        lightThresholdOff: threshold.lightThresholdOff,
        source: 'GLOBAL',
        sourceKey: null,
      } satisfies EffectiveThreshold)
    },
    async listControlLogs(params) {
      let list = [...controlLogs]
      if (params.deviceId) list = list.filter((c) => c.deviceId === params.deviceId)
      if (params.source) list = list.filter((c) => c.source === params.source)
      return ok(pageOf(list, params.page, params.pageSize))
    },
  }
}

/** Mock 实时：缓慢抖动在线模拟灯的光照 */
export function mockTickLight(): LatestLight | null {
  const d = devices.find((x) => x.id === 2 && x.onlineStatus === 'ONLINE')
  if (!d) return null
  const intensity = Math.max(5, Math.min(40, 12 + Math.random() * 18))
  const row: LightReading = {
    id: lightSeq++,
    deviceId: 2,
    deviceName: d.deviceName,
    lightIntensity: intensity,
    createdAt: now(),
  }
  lights = [row, ...lights].slice(0, 800)
  return { deviceId: 2, lightIntensity: intensity, createdAt: row.createdAt }
}
