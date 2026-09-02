export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export const RISK_LABEL: Record<RiskLevel, string> = {
  HIGH: '高风险',
  MEDIUM: '中风险',
  LOW: '低风险',
}

/** 告警类型 → 风险等级（含光棚 M6） */
export function alarmRisk(alarmType: string): RiskLevel {
  const t = (alarmType || '').toUpperCase()
  if (
    t === 'OFFLINE' ||
    t === 'DEVICE_OFFLINE' ||
    t === 'COMMAND_TIMEOUT' ||
    t === 'HEARTBEAT_TIMEOUT' ||
    t === 'OVER_PPFD'
  ) {
    return 'HIGH'
  }
  if (t === 'LIGHT_ABNORMAL' || t === 'UNDER_PPFD' || t === 'DLI_LOW' || t === 'DLI_HIGH') {
    return 'MEDIUM'
  }
  return 'LOW'
}

/** 控制日志执行状态 → 风险/严重度（便于筛选与着色） */
export function controlLogRisk(executionStatus: string): RiskLevel {
  const s = (executionStatus || '').toUpperCase()
  if (s === 'TIMEOUT') return 'HIGH'
  if (s === 'PENDING') return 'MEDIUM'
  return 'LOW'
}

export function maxRisk(a: RiskLevel | null | undefined, b: RiskLevel | null | undefined): RiskLevel | null {
  const rank = { HIGH: 3, MEDIUM: 2, LOW: 1 }
  if (!a) return b ?? null
  if (!b) return a
  return rank[a] >= rank[b] ? a : b
}
