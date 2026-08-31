import type { ApiResult } from '../types/domain'

const SESSION_KEY = 'streetlight.session'
const base = (import.meta.env.VITE_API_BASE as string) || ''

function token(): string | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as { token: string }).token : null
  } catch {
    return null
  }
}

async function http<T>(path: string, init: RequestInit = {}): Promise<ApiResult<T>> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const t = token()
  if (t) headers.set('token', t)
  const res = await fetch(`${base}${path}`, { ...init, headers })
  const text = await res.text()
  if (!text) throw new Error(`后端无响应 (${res.status})`)
  return JSON.parse(text) as ApiResult<T>
}

export type GhZone = {
  zoneId: string
  name: string
  recipeId: string
  climateProfileId: string
  autoControl: boolean
  shadeOpenPercent: number
  lastEffectivePpfd?: number
  lastDli?: number
  lengthM?: number
  widthM?: number
}

export type GhRecipe = {
  recipeId: string
  cropNameZh: string
  stage: string
  ppfdTargetMin: number
  ppfdTargetMax: number
  ppfdHardMin: number
  ppfdHardMax: number
  dliTargetMin?: number
  dliTargetMax?: number
}

export type GhDevice = {
  id?: number
  deviceSn: string
  deviceName: string
  zoneId: string
  deviceType: string
  model?: string
  dimmingPercent?: number
  shadeOpenPercent?: number
  powerOn?: boolean
  lastPpfd?: number
  onlineStatus?: string
  posX?: number
  posY?: number
  posZ?: number
  lastSeenAt?: string
}

export type GhWorkOrder = {
  id: number
  zoneId: string
  status: string
  reason: string
  targetDeviceSn?: string | null
  suggestedDimmingPct?: number
  suggestedShadePct?: number
  createdAt?: string
}

export type GhControlLog = {
  id: number
  deviceSn?: string | null
  zoneId?: string | null
  command: string
  source: string
  payloadJson?: string | null
  executionStatus: string
  createdAt?: string
}

export type GhAlarm = {
  id: number
  zoneId?: string | null
  deviceSn?: string | null
  alarmType: string
  message: string
  status: string
  createdAt?: string
  resolvedAt?: string | null
}

export type GhReport = {
  id: number
  reportType: string
  title: string
  status: string
  authorId?: number | null
  authorRole?: string | null
  zoneId?: string | null
  reportDate?: string
  summaryZh?: string | null
  bodyJson?: string | null
  workOrderIds?: string | null
  reviewerId?: number | null
  reviewNote?: string | null
  reviewedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export type DaySeriesPoint = {
  minuteOfDay: number
  outdoorPpfd: number
  naturalPpfd: number
  sunInPpfd: number
  ledPpfd: number
  controlledPpfd: number
  humidityPct: number
  temperatureC: number
  shadeOpenPercent: number
  avgDimmingPercent: number
  /** 动态瞬时目标带 */
  targetPpfdMin?: number
  targetPpfdMax?: number
  targetMid?: number
  gapPpfd?: number
  vpdKpa?: number
  dliSoFar?: number
  /** 分床平均 PAR（日曲线采样） */
  bedPpfd?: Record<string, number>
  /** 各 PAR 测点（日曲线采样） */
  sensorPpfd?: Record<string, number>
}

/** 配方基带经光周期 / VPD / DLI 追赶后的此刻目标 */
export type GhDynamicTarget = {
  recipeMin: number
  recipeMax: number
  instantMin: number
  instantMax: number
  hardMin: number
  hardMax: number
  photoperiodMask: number
  vpdKpa: number
  vpdFactor: number
  dliCatchUp: number
  dliSoFar: number
  dliTargetMin: number
  dliTargetMax: number
  dliExpectedByNow: number
  dliRemainingMin: number
  photoperiodHours: number
  noteZh: string
}

export type GhLightEconomics = {
  shadeSteps: number[]
  shadeOpenSnapped: number
  ledShareR: number
  ledShareG: number
  ledShareB: number
  avgDimmingPercent: number
  lampCount: number
  ledKwhTodayEst: number
  energyCostYuanEst: number
  yieldIndex: number
  balanceScore: number
  naturalPpfd: number
  ledPpfd: number
  effectivePpfd: number
  targetMin: number
  targetMax: number
  yuanPerKwh: number
  adviceZh: string
}

export type GhEffectiveLight = {
  zoneId: string
  name: string
  recipeId: string
  climateProfileId: string
  geometryId?: string
  minuteOfDay: number
  dayProgress?: number
  dayCompressSec?: number
  intervalMs?: number
  minutesPerTick?: number
  outdoorParPpfd: number
  sunInPpfd: number
  naturalPpfd?: number
  ledPpfd?: number
  effectivePpfd: number
  humidityPct?: number
  temperatureC?: number
  vpdKpa?: number
  dliSoFar: number
  shadeOpenPercent: number
  autoControl: boolean
  nx: number
  ny: number
  lengthM: number
  widthM: number
  gutterHeightM?: number
  ridgeHeightM?: number
  measurePlaneZ?: number
  coordinateNoteZh?: string
  solarElevationDeg?: number
  solarAzimuthDeg?: number
  sunVisible?: boolean
  shadeTransmittance?: number
  coverTransmittance?: number
  grid: {
    x: number
    y: number
    ppfd: number
    sunPpfd?: number
    ledPpfd?: number
    rPpfd?: number
    gPpfd?: number
    bPpfd?: number
  }[]
  devices: GhDevice[]
  recipe?: GhRecipe
  dynamicTarget?: GhDynamicTarget
  spectrum?: {
    sunShare: { r: number; g: number; b: number }
    ledShare: { r: number; g: number; b: number }
    noteZh: string
  }
  economics?: GhLightEconomics
  sunModel?: Record<string, unknown>
  bedStats?: {
    bedId: string
    avgPpfd: number
    minPpfd: number
    avgLed: number
    cellCount: number
    uniformityU0: number
  }[]
  sensorPpfd?: Record<string, number>
  series?: DaySeriesPoint[]
}

export const greenhouseApi = {
  zones: () => http<GhZone[]>('/greenhouse/zones'),
  effectiveLight: (zoneId: string) => http<GhEffectiveLight>(`/greenhouse/zones/${zoneId}/effective-light`),
  recipes: () => http<GhRecipe[]>('/greenhouse/recipes'),
  bindRecipe: (zoneId: string, recipeId: string) =>
    http<string>(`/greenhouse/zones/${zoneId}/recipe`, {
      method: 'PUT',
      body: JSON.stringify({ recipeId }),
    }),
  setClimate: (zoneId: string, profileId: string) =>
    http<string>(`/greenhouse/zones/${zoneId}/climate-profile`, {
      method: 'PUT',
      body: JSON.stringify({ profileId }),
    }),
  setAuto: (zoneId: string, enabled: boolean) =>
    http<string>(`/greenhouse/zones/${zoneId}/auto-control`, {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  workOrders: (status?: string) =>
    http<GhWorkOrder[]>(`/greenhouse/work-orders${status ? `?status=${status}` : ''}`),
  approve: (id: number) => http<string>(`/greenhouse/work-orders/${id}/approve`, { method: 'POST' }),
  reject: (id: number) => http<string>(`/greenhouse/work-orders/${id}/reject`, { method: 'POST' }),
  /** 种植员接单执行（下发执行器并完成） */
  claim: (id: number) => http<string>(`/greenhouse/work-orders/${id}/claim`, { method: 'POST' }),
  complete: (id: number) => http<string>(`/greenhouse/work-orders/${id}/complete`, { method: 'POST' }),
  dimming: (sn: string, dimmingPercent: number) =>
    http<string>(`/greenhouse/lamps/${sn}/dimming`, {
      method: 'POST',
      body: JSON.stringify({ dimmingPercent }),
    }),
  shade: (sn: string, shadeOpenPercent: number) =>
    http<string>(`/greenhouse/shades/${sn}/open-percent`, {
      method: 'POST',
      body: JSON.stringify({ shadeOpenPercent }),
    }),
  climateProfiles: () => http<Record<string, { id: string; labelZh: string }>>('/greenhouse/climate-profiles'),
  devices: (zoneId?: string) =>
    http<GhDevice[]>(`/greenhouse/devices${zoneId ? `?zoneId=${encodeURIComponent(zoneId)}` : ''}`),
  controlLogs: (params?: { limit?: number; source?: string }) => {
    const q = new URLSearchParams()
    if (params?.limit != null) q.set('limit', String(params.limit))
    if (params?.source) q.set('source', params.source)
    const qs = q.toString()
    return http<GhControlLog[]>(`/greenhouse/control-logs${qs ? `?${qs}` : ''}`)
  },
  alarms: (params?: { status?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (params?.status) q.set('status', params.status)
    if (params?.limit != null) q.set('limit', String(params.limit))
    const qs = q.toString()
    return http<GhAlarm[]>(`/greenhouse/alarms${qs ? `?${qs}` : ''}`)
  },
  resolveAlarm: (id: number) =>
    http<string>(`/greenhouse/alarms/${id}/resolve`, { method: 'PUT' }),
  reports: (params?: { type?: string; status?: string; limit?: number }) => {
    const q = new URLSearchParams()
    if (params?.type) q.set('type', params.type)
    if (params?.status) q.set('status', params.status)
    if (params?.limit != null) q.set('limit', String(params.limit))
    const qs = q.toString()
    return http<GhReport[]>(`/greenhouse/reports${qs ? `?${qs}` : ''}`)
  },
  report: (id: number) => http<GhReport>(`/greenhouse/reports/${id}`),
  draftDailyReport: (zoneId?: string) =>
    http<GhReport>('/greenhouse/reports/daily-draft', {
      method: 'POST',
      body: JSON.stringify({ zoneId: zoneId || 'ZONE-A' }),
    }),
  draftTrainingReport: (zoneId?: string) =>
    http<GhReport>('/greenhouse/reports/training-draft', {
      method: 'POST',
      body: JSON.stringify({ zoneId: zoneId || 'ZONE-A' }),
    }),
  submitReport: (id: number) =>
    http<string>(`/greenhouse/reports/${id}/submit`, { method: 'POST' }),
  reviewReport: (id: number, note: string, approve = true) =>
    http<string>(`/greenhouse/reports/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ note, approve }),
    }),
  resetDay: () => http<string>('/greenhouse/sim/reset-day', { method: 'POST' }),
}
