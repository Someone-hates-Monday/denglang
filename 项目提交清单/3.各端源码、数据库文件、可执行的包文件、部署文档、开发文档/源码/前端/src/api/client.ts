import type { ApiResult } from '../types/domain'
import { isHttpMode, isMockMode } from '../config/runtime'
import {
  normalizeAlarm,
  normalizeAlarmStatistics,
  normalizeControlLog,
  normalizeDevice,
  normalizeDeviceDetail,
  normalizeDeviceStatistics,
  normalizeLatestLight,
  normalizeLightReading,
  normalizePage,
  normalizeThreshold,
  normalizeThresholdOverride,
  normalizeEffectiveThreshold,
} from './normalize'
import { createMockApi } from './mock'
import type { StreetLightApi } from './types'

const base = (import.meta.env.VITE_API_BASE as string) || ''
const SESSION_KEY = 'streetlight.session'

function token(): string | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as { token: string }).token : null
  } catch {
    return null
  }
}

function handleUnauthorized() {
  localStorage.removeItem(SESSION_KEY)
  const loginPath = '/login'
  if (!window.location.pathname.endsWith(loginPath)) {
    window.location.assign(`${loginPath}?expired=1`)
  }
}

async function http<T>(path: string, init: RequestInit = {}): Promise<ApiResult<T>> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const t = token()
  if (t) headers.set('token', t)
  const res = await fetch(`${base}${path}`, { ...init, headers })
  const text = await res.text()
  if (res.status === 401) {
    handleUnauthorized()
    throw new Error('登录已过期，请重新登录')
  }
  if (!text) {
    throw new Error(`后端无响应 (${res.status})，请确认 8080 已启动`)
  }
  const body = JSON.parse(text) as ApiResult<T>
  if (body.code === 401) {
    handleUnauthorized()
    throw new Error(body.errorMsg || '登录已过期，请重新登录')
  }
  return body
}

async function httpData<T>(
  path: string,
  init: RequestInit,
  map: (raw: Record<string, unknown>) => T,
): Promise<ApiResult<T>> {
  const body = await http<Record<string, unknown>>(path, init)
  if (body.code === 200 && body.data) {
    return { ...body, data: map(body.data) }
  }
  return body as ApiResult<T>
}

async function httpPage<T>(
  path: string,
  init: RequestInit,
  mapItem: (raw: Record<string, unknown>) => T,
): Promise<ApiResult<import('../types/domain').PageResult<T>>> {
  const body = await http<Record<string, unknown>>(path, init)
  if (body.code === 200 && body.data) {
    return { ...body, data: normalizePage(body.data, mapItem) }
  }
  return body as unknown as ApiResult<import('../types/domain').PageResult<T>>
}

function createHttpApi(): StreetLightApi {
  const q = (params: Record<string, unknown>) => {
    const sp = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
    })
    const s = sp.toString()
    return s ? `?${s}` : ''
  }

  return {
    register: (username, password, role) =>
      http('/users/register', {
        method: 'POST',
        body: JSON.stringify({ username, password, role }),
      }),
    login: (username, password) =>
      http('/users/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      }),
    listDevices: (params) =>
      httpPage('/devices' + q({ page: 1, pageSize: 10, ...params }), {}, normalizeDevice),
    getDevice: (id) => httpData(`/devices/${id}`, {}, normalizeDeviceDetail),
    addDevice: (body) => http('/devices', { method: 'POST', body: JSON.stringify(body) }),
    updateDevice: (id, body) =>
      http(`/devices/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    setDeviceLocation: (id, body) =>
      http(`/devices/${Number(id)}/location`, { method: 'PUT', body: JSON.stringify(body) }),
    deleteDevice: (id) => http(`/devices/${id}`, { method: 'DELETE' }),
    deviceStatistics: () => httpData('/devices/statistics', {}, normalizeDeviceStatistics),
    switchDevice: (id, status) =>
      http(`/devices/${Number(id)}/switch`, { method: 'POST', body: JSON.stringify({ status }) }),
    setControlMode: (id, mode) =>
      http(`/devices/${Number(id)}/control-mode`, {
        method: 'PUT',
        body: JSON.stringify({ mode }),
      }),
    setDeviceGroup: (id, groupName) =>
      http(`/devices/${Number(id)}/group`, {
        method: 'PUT',
        body: JSON.stringify({ groupName }),
      }),
    switchGroup: (groupName, status) =>
      http('/devices/group-switch', {
        method: 'POST',
        body: JSON.stringify({ groupName, status }),
      }),
    setGroupControlMode: (groupName, mode) =>
      http('/devices/group-control-mode', {
        method: 'PUT',
        body: JSON.stringify({ groupName, mode }),
      }),
    listLightReadings: (params) =>
      httpPage('/light-readings' + q({ page: 1, pageSize: 10, ...params }), {}, normalizeLightReading),
    latestLight: (deviceId) =>
      httpData(`/light-readings/latest/${Number(deviceId)}`, {}, normalizeLatestLight),
    lightTrend: (params) =>
      http(`/light-readings/trend${q(params)}`),
    listAlarms: (params) =>
      httpPage('/alarm-logs' + q({ page: 1, pageSize: 10, ...params }), {}, normalizeAlarm),
    resolveAlarm: (id) =>
      http(`/alarm-logs/${encodeURIComponent(String(id))}/resolve`, { method: 'PUT' }),
    alarmStatistics: () => httpData('/alarm-logs/statistics', {}, normalizeAlarmStatistics),
    getThreshold: () => httpData('/threshold-config', {}, normalizeThreshold),
    updateThreshold: (body) =>
      http('/threshold-config', { method: 'PUT', body: JSON.stringify(body) }),
    listThresholdOverrides: async () => {
      const body = await http<Record<string, unknown>[]>('/threshold-config/overrides')
      if (body.code === 200 && Array.isArray(body.data)) {
        return {
          ...body,
          data: body.data.map((row) => normalizeThresholdOverride(row as Record<string, unknown>)),
        }
      }
      return body as unknown as import('../types/domain').ApiResult<
        import('../types/domain').ThresholdOverride[]
      >
    },
    upsertThresholdOverride: (body) =>
      http('/threshold-config/overrides', { method: 'PUT', body: JSON.stringify(body) }),
    deleteThresholdOverride: (scopeType, scopeKey) =>
      http(`/threshold-config/overrides${q({ scopeType, scopeKey })}`, { method: 'DELETE' }),
    getEffectiveThreshold: (deviceId) =>
      httpData(`/threshold-config/effective/${Number(deviceId)}`, {}, normalizeEffectiveThreshold),
    listControlLogs: (params) =>
      httpPage('/control-logs' + q({ page: 1, pageSize: 10, ...params }), {}, normalizeControlLog),
  }
}

export const api: StreetLightApi = isHttpMode ? createHttpApi() : createMockApi()
export { isMockMode }
