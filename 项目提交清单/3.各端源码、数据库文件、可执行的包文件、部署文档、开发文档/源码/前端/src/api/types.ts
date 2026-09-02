import type {
  AlarmLog,
  AlarmStatistics,
  ApiResult,
  ControlLog,
  Device,
  DeviceDetail,
  DeviceStatistics,
  LatestLight,
  LightReading,
  PageResult,
  Role,
  ThresholdConfig,
  ThresholdOverride,
  TrendPoint,
  UserSession,
} from '../types/domain'

export interface StreetLightApi {
  register(username: string, password: string, role?: Role): Promise<ApiResult<string>>
  login(username: string, password: string): Promise<ApiResult<UserSession>>
  listDevices(params: {
    page?: number
    pageSize?: number
    deviceName?: string
    status?: string
    onlineStatus?: string
  }): Promise<ApiResult<PageResult<Device>>>
  getDevice(id: number): Promise<ApiResult<DeviceDetail>>
  addDevice(body: {
    deviceName: string
    deviceSn: string
    latitude?: number | null
    longitude?: number | null
  }): Promise<ApiResult<string>>
  updateDevice(id: number, body: { deviceName: string }): Promise<ApiResult<string>>
  setDeviceLocation(
    id: number,
    body: { latitude: number | null; longitude: number | null },
  ): Promise<ApiResult<string>>
  deleteDevice(id: number): Promise<ApiResult<string>>
  deviceStatistics(): Promise<ApiResult<DeviceStatistics>>
  switchDevice(id: number, status: 'ON' | 'OFF'): Promise<ApiResult<{ command: string; controlMode?: string }>>
  setControlMode(id: number, mode: 'AUTO' | 'MANUAL'): Promise<ApiResult<string>>
  setDeviceGroup(id: number, groupName: string | null): Promise<ApiResult<string>>
  switchGroup(
    groupName: string,
    status: 'ON' | 'OFF',
  ): Promise<ApiResult<{ count: number; command: string; controlMode?: string }>>
  setGroupControlMode(
    groupName: string,
    mode: 'AUTO' | 'MANUAL',
  ): Promise<ApiResult<{ count: number; mode: string }>>
  listLightReadings(params: {
    page?: number
    pageSize?: number
    deviceId?: number
    groupName?: string
  }): Promise<ApiResult<PageResult<LightReading>>>
  latestLight(deviceId: number): Promise<ApiResult<LatestLight>>
  lightTrend(params: {
    deviceId?: number
    groupName?: string
    startTime: string
    endTime: string
  }): Promise<ApiResult<TrendPoint[]>>
  listAlarms(params: {
    page?: number
    pageSize?: number
    deviceId?: number
    alarmType?: string
    status?: string
  }): Promise<ApiResult<PageResult<AlarmLog>>>
  resolveAlarm(id: string): Promise<ApiResult<string>>
  alarmStatistics(): Promise<ApiResult<AlarmStatistics>>
  getThreshold(): Promise<ApiResult<ThresholdConfig>>
  updateThreshold(body: {
    lightThresholdOn: number
    lightThresholdOff: number
    heartbeatTimeout: number
  }): Promise<ApiResult<string>>
  listThresholdOverrides(): Promise<ApiResult<ThresholdOverride[]>>
  upsertThresholdOverride(body: {
    scopeType: 'DEVICE' | 'GROUP'
    scopeKey: string
    lightThresholdOn: number
    lightThresholdOff: number
  }): Promise<ApiResult<string>>
  deleteThresholdOverride(scopeType: string, scopeKey: string): Promise<ApiResult<string>>
  getEffectiveThreshold(deviceId: number): Promise<ApiResult<import('../types/domain').EffectiveThreshold>>
  listControlLogs(params: {
    page?: number
    pageSize?: number
    deviceId?: number
    source?: string
  }): Promise<ApiResult<PageResult<ControlLog>>>
}

export function ok<T>(data: T): ApiResult<T> {
  return { code: 200, errorMsg: null, data }
}

export function fail<T = never>(msg: string): ApiResult<T> {
  return { code: 500, errorMsg: msg, data: null as T }
}
