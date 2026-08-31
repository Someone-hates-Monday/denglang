export type { Role } from '../auth/rbac'
export { ROLE_LABEL, normalizeRole } from '../auth/rbac'
import type { Role } from '../auth/rbac'

/** 后端：成功 code=200；Header 名 token */
export interface ApiResult<T> {
  code: number
  errorMsg: string | null
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserSession {
  token: string
  userId: number
  username: string
  role: Role
}

export interface Device {
  id: number
  deviceName: string
  deviceSn: string
  status: 'ON' | 'OFF'
  onlineStatus: 'ONLINE' | 'OFFLINE'
  /** AUTO=跟阈值；MANUAL=手动锁定 */
  controlMode: 'AUTO' | 'MANUAL'
  /** 编组名称；null/空=未分组 */
  groupName: string | null
  /** 纬度（GCJ-02）；未标定则为 null */
  latitude: number | null
  /** 经度（GCJ-02）；未标定则为 null */
  longitude: number | null
  /** 最近成功指令期望 status；无则 null */
  expectedStatus?: 'ON' | 'OFF' | string | null
  /** 期望与实际是否一致（C1） */
  statusMatch?: boolean
  lastHeartbeatTime: string | null
  createdAt: string
}

export interface DeviceDetail extends Device {
  latestLightIntensity: number | null
  activeAlarmCount: number
}

export interface DeviceStatistics {
  totalCount: number
  onlineCount: number
  offlineCount: number
  onCount: number
  offCount: number
}

export interface LightReading {
  id: number
  deviceId: number
  deviceName: string
  lightIntensity: number
  createdAt: string
}

export interface LatestLight {
  deviceId: number
  lightIntensity: number
  createdAt: string
}

export interface TrendPoint {
  time: string
  value: number
}

export interface AlarmLog {
  /** 雪花 ID，必须当字符串用，Number() 会丢精度导致处理失败 */
  id: string
  deviceId: number
  deviceName: string
  alarmType: string
  message: string
  status: 'ACTIVE' | 'RESOLVED'
  createdAt: string
  resolvedAt: string | null
}

export interface AlarmStatistics {
  activeCount: number
  byType: { alarmType: string; count: number }[]
}

export interface ThresholdConfig {
  id: number
  lightThresholdOn: number
  lightThresholdOff: number
  heartbeatTimeout: number
  updatedAt: string
}

/** DEVICE | GROUP 覆盖；优先于全局 */
export interface ThresholdOverride {
  id: string
  scopeType: 'DEVICE' | 'GROUP' | string
  scopeKey: string
  scopeLabel: string
  lightThresholdOn: number
  lightThresholdOff: number
  updatedAt: string
}

export interface EffectiveThreshold {
  lightThresholdOn: number
  lightThresholdOff: number
  source: 'DEVICE' | 'GROUP' | 'GLOBAL' | string
  sourceKey: string | null
}

export interface ControlLog {
  id: number
  deviceId: number | null
  deviceName: string | null
  operatorId: number | null
  operatorName: string | null
  command: string
  source: string
  result: string
  executionStatus: 'PENDING' | 'SUCCESS' | 'TIMEOUT' | string
  expectedStatus: string | null
  createdAt: string
}
