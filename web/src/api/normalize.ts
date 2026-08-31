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
  ThresholdConfig,
  ThresholdOverride,
} from '../types/domain'

function asNumber(v: unknown): number {
  if (typeof v === 'number' && Number.isFinite(v)) return v
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function asNullableNumber(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

export function normalizeDeviceDetail(raw: Record<string, unknown>): DeviceDetail {
  return {
    ...normalizeDevice(raw),
    latestLightIntensity:
      raw.latestLightIntensity === null || raw.latestLightIntensity === undefined
        ? null
        : Number(raw.latestLightIntensity),
    activeAlarmCount: asNumber(raw.activeAlarmCount),
  }
}

export function normalizeDevice(raw: Record<string, unknown>): Device {
  return {
    id: asNumber(raw.id),
    deviceName: String(raw.deviceName ?? ''),
    deviceSn: String(raw.deviceSn ?? ''),
    status: raw.status as Device['status'],
    onlineStatus: raw.onlineStatus as Device['onlineStatus'],
    controlMode: (raw.controlMode as Device['controlMode']) || 'AUTO',
    groupName: (() => {
      const g = raw.groupName == null ? '' : String(raw.groupName).trim()
      return g || null
    })(),
    latitude: asNullableNumber(raw.latitude),
    longitude: asNullableNumber(raw.longitude),
    lastHeartbeatTime: (raw.lastHeartbeatTime as string | null) ?? null,
    createdAt: String(raw.createdAt ?? ''),
  }
}

export function normalizeLightReading(raw: Record<string, unknown>): LightReading {
  return {
    id: asNumber(raw.id),
    deviceId: asNumber(raw.deviceId),
    deviceName: String(raw.deviceName ?? ''),
    lightIntensity: Number(raw.lightIntensity),
    createdAt: String(raw.createdAt ?? ''),
  }
}

export function normalizeLatestLight(raw: Record<string, unknown>): LatestLight {
  return {
    deviceId: asNumber(raw.deviceId),
    lightIntensity: Number(raw.lightIntensity),
    createdAt: String(raw.createdAt ?? ''),
  }
}

export function normalizeAlarm(raw: Record<string, unknown>): AlarmLog {
  const deviceName =
    String(raw.deviceName ?? '') ||
    String(raw.deviceSn ?? '') ||
    String(raw.zoneId ?? '') ||
    '光棚'
  return {
    id: String(raw.id ?? ''),
    deviceId: asNumber(raw.deviceId),
    deviceName,
    alarmType: String(raw.alarmType ?? ''),
    message: String(raw.message ?? ''),
    status: raw.status as AlarmLog['status'],
    createdAt: String(raw.createdAt ?? ''),
    resolvedAt: (raw.resolvedAt as string | null) ?? null,
  }
}

export function normalizeControlLog(raw: Record<string, unknown>): ControlLog {
  return {
    id: asNumber(raw.id),
    deviceId: asNullableNumber(raw.deviceId),
    deviceName: (raw.deviceName as string | null) ?? null,
    operatorId: asNullableNumber(raw.operatorId),
    operatorName: (raw.operatorName as string | null) ?? null,
    command: String(raw.command ?? ''),
    source: String(raw.source ?? ''),
    result: String(raw.result ?? ''),
    executionStatus: String(raw.executionStatus ?? 'SUCCESS'),
    expectedStatus: (raw.expectedStatus as string | null) ?? null,
    createdAt: String(raw.createdAt ?? ''),
  }
}

export function normalizeDeviceStatistics(raw: Record<string, unknown>): DeviceStatistics {
  return {
    totalCount: asNumber(raw.totalCount),
    onlineCount: asNumber(raw.onlineCount),
    offlineCount: asNumber(raw.offlineCount),
    onCount: asNumber(raw.onCount),
    offCount: asNumber(raw.offCount),
  }
}

export function normalizeAlarmStatistics(raw: Record<string, unknown>): AlarmStatistics {
  const byType = Array.isArray(raw.byType) ? raw.byType : []
  return {
    activeCount: asNumber(raw.activeCount),
    byType: byType.map((item) => {
      const row = item as Record<string, unknown>
      return { alarmType: String(row.alarmType ?? ''), count: asNumber(row.count) }
    }),
  }
}

export function normalizeThreshold(raw: Record<string, unknown>): ThresholdConfig {
  return {
    id: asNumber(raw.id),
    lightThresholdOn: Number(raw.lightThresholdOn),
    lightThresholdOff: Number(raw.lightThresholdOff),
    heartbeatTimeout: asNumber(raw.heartbeatTimeout),
    updatedAt: String(raw.updatedAt ?? ''),
  }
}

export function normalizeThresholdOverride(raw: Record<string, unknown>): ThresholdOverride {
  return {
    id: String(raw.id ?? ''),
    scopeType: String(raw.scopeType ?? ''),
    scopeKey: String(raw.scopeKey ?? ''),
    scopeLabel: String(raw.scopeLabel ?? raw.scopeKey ?? ''),
    lightThresholdOn: Number(raw.lightThresholdOn),
    lightThresholdOff: Number(raw.lightThresholdOff),
    updatedAt: String(raw.updatedAt ?? ''),
  }
}

export function normalizeEffectiveThreshold(raw: Record<string, unknown>): EffectiveThreshold {
  return {
    lightThresholdOn: Number(raw.lightThresholdOn),
    lightThresholdOff: Number(raw.lightThresholdOff),
    source: String(raw.source ?? 'GLOBAL'),
    sourceKey: raw.sourceKey == null ? null : String(raw.sourceKey),
  }
}

export function normalizePage<T>(
  raw: Record<string, unknown>,
  mapItem: (row: Record<string, unknown>) => T,
): PageResult<T> {
  const records = Array.isArray(raw.records) ? raw.records : []
  return {
    total: asNumber(raw.total),
    records: records.map((row) => mapItem(row as Record<string, unknown>)),
  }
}
