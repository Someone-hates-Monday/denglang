import type { DaySeriesPoint, GhEffectiveLight } from '../api/greenhouse'
import { BEDS } from './cropCatalog'

export type ChartScope = 'bay' | 'zone' | 'beds' | 'sensors'

export type CurveLayerDef = {
  id: string
  label: string
  color: string
  series: DaySeriesPoint[]
  getValue: (p: DaySeriesPoint) => number
  width?: number
  dashed?: boolean
  /** 自定义虚线；与 dashed 同时存在时优先 dash */
  dash?: number[]
}

export type RegionRow = {
  id: string
  kind: 'bay' | 'zone' | 'bed'
  label: string
  zoneId?: string
  bedId?: string
  ppfd: number
  targetMin: number
  targetMax: number
  gap: number
  shadePct: number
  dimPct: number
  status: 'ok' | 'low' | 'high' | 'off'
  statusZh: string
}

const ZONE_COLORS: Record<string, string> = {
  'ZONE-A': '#1d4ed8',
  'ZONE-B': '#c2410c',
}

export { ZONE_COLORS }

/**
 * 分床/测点：色相尽量正交（红/蓝/绿/紫/橙/棕），再配不同虚线，避免图例「一片相近」。
 * 顺序对应 BEDS：西南→西中→西北→东南→东中→东北
 */
const BED_COLORS = ['#dc2626', '#2563eb', '#16a34a', '#7c3aed', '#ea580c', '#92400e']

/** 测点 5 色：红蓝绿橙紫，与分床同系但足够拉开 */
const SENSOR_COLORS = ['#dc2626', '#2563eb', '#16a34a', '#ea580c', '#7c3aed']

const BED_DASHES: (number[] | undefined)[] = [
  undefined,
  [8, 4],
  [3, 3],
  [10, 3, 2, 3],
  [2, 2.5],
  [9, 3, 2, 3, 2, 3],
]

const SENSOR_DASHES: (number[] | undefined)[] = [
  undefined,
  [8, 4],
  [3, 3],
  [10, 3, 2, 3],
  [2, 2.5],
]

/** 测点与床对应（layout v1.5：L0 每床 5×PAR；中/北床 L1 另 5×PAR） */
export const SENSORS_BY_BED: Record<string, string[]> = {
  'BED-A-S': ['PAR-ZONE-A-01', 'PAR-ZONE-A-02', 'PAR-ZONE-A-03', 'PAR-ZONE-A-04', 'PAR-ZONE-A-05'],
  'BED-A-M': ['PAR-ZONE-A-06', 'PAR-ZONE-A-07', 'PAR-ZONE-A-08', 'PAR-ZONE-A-09', 'PAR-ZONE-A-10'],
  'BED-A-N': ['PAR-ZONE-A-11', 'PAR-ZONE-A-12', 'PAR-ZONE-A-13', 'PAR-ZONE-A-14', 'PAR-ZONE-A-15'],
  'BED-A-M-L1': ['PAR-ZONE-A-L1-01', 'PAR-ZONE-A-L1-02', 'PAR-ZONE-A-L1-03', 'PAR-ZONE-A-L1-04', 'PAR-ZONE-A-L1-05'],
  'BED-A-N-L1': ['PAR-ZONE-A-L1-06', 'PAR-ZONE-A-L1-07', 'PAR-ZONE-A-L1-08', 'PAR-ZONE-A-L1-09', 'PAR-ZONE-A-L1-10'],
  'BED-B-S': ['PAR-ZONE-B-01', 'PAR-ZONE-B-02', 'PAR-ZONE-B-03', 'PAR-ZONE-B-04', 'PAR-ZONE-B-05'],
  'BED-B-M': ['PAR-ZONE-B-06', 'PAR-ZONE-B-07', 'PAR-ZONE-B-08', 'PAR-ZONE-B-09', 'PAR-ZONE-B-10'],
  'BED-B-N': ['PAR-ZONE-B-11', 'PAR-ZONE-B-12', 'PAR-ZONE-B-13', 'PAR-ZONE-B-14', 'PAR-ZONE-B-15'],
  'BED-B-M-L1': ['PAR-ZONE-B-L1-01', 'PAR-ZONE-B-L1-02', 'PAR-ZONE-B-L1-03', 'PAR-ZONE-B-L1-04', 'PAR-ZONE-B-L1-05'],
  'BED-B-N-L1': ['PAR-ZONE-B-L1-06', 'PAR-ZONE-B-L1-07', 'PAR-ZONE-B-L1-08', 'PAR-ZONE-B-L1-09', 'PAR-ZONE-B-L1-10'],
}

export function bedShortLabel(bedId: string): string {
  const bed = BEDS.find((b) => b.bedId === bedId)
  const half = bedId.startsWith('BED-A') ? '西' : '东'
  return bed ? `${half}·${bed.roleZh}` : bedId
}

export function zoneShortLabel(zoneId: string, name?: string): string {
  if (name?.includes('半跨')) return name.replace(/·石斛$/, '')
  return zoneId === 'ZONE-A' ? '西半跨' : zoneId === 'ZONE-B' ? '东半跨' : zoneId
}

function avgDimForZone(el: GhEffectiveLight | undefined): number {
  const lamps = (el?.devices || []).filter((d) => d.deviceType === 'GROW_LAMP')
  if (!lamps.length) return 0
  return Math.round(lamps.reduce((s, d) => s + (d.dimmingPercent ?? 0), 0) / lamps.length)
}

function gapStatus(ppfd: number, min: number, max: number, photoperiodMask = 1): RegionRow['status'] {
  if (photoperiodMask < 0.05) return 'off'
  if (ppfd < min - 2) return 'low'
  if (ppfd > max + 2) return 'high'
  return 'ok'
}

function statusZh(status: RegionRow['status']): string {
  if (status === 'low') return '偏低 · 补光/收遮'
  if (status === 'high') return '偏高 · 遮阳/降灯'
  if (status === 'off') return '光周期外'
  return '在目标带'
}

function pointAt(series: DaySeriesPoint[], minute: number): DaySeriesPoint | undefined {
  if (!series.length) return undefined
  let best = series[0]
  let dist = Math.abs(best.minuteOfDay - minute)
  for (const p of series) {
    const d = Math.abs(p.minuteOfDay - minute)
    if (d < dist) {
      best = p
      dist = d
    }
  }
  return best
}

/** 分床曲线：优先该床 L0 测点均值（与区有效光同口径），回退 bedPpfd */
export function bedSeriesPpfd(p: DaySeriesPoint, bedId: string): number {
  const sns = SENSORS_BY_BED[bedId]
  if (sns?.length && p.sensorPpfd) {
    const vals = sns
      .map((sn) => p.sensorPpfd?.[sn])
      .filter((v): v is number => v != null && Number.isFinite(v))
    if (vals.length) {
      return vals.reduce((a, b) => a + b, 0) / vals.length
    }
  }
  const v = p.bedPpfd?.[bedId]
  return v != null && Number.isFinite(v) ? v : Number.NaN
}

export function buildRegionRows(lights: Record<string, GhEffectiveLight>): RegionRow[] {
  const a = lights['ZONE-A']
  const b = lights['ZONE-B']
  const rows: RegionRow[] = []

  const dynA = a?.dynamicTarget
  const dynB = b?.dynamicTarget
  const mask = Math.max(dynA?.photoperiodMask ?? 1, dynB?.photoperiodMask ?? 1)
  const tMin = dynA?.instantMin ?? dynB?.instantMin ?? 0
  const tMax = dynA?.instantMax ?? dynB?.instantMax ?? 0
  const ppfdBay =
    a && b ? (Number(a.effectivePpfd) + Number(b.effectivePpfd)) / 2 : Number(a?.effectivePpfd ?? b?.effectivePpfd ?? 0)
  const gapBay = ppfdBay - (tMin + tMax) / 2
  const stBay = gapStatus(ppfdBay, tMin, tMax, mask)
  rows.push({
    id: 'BAY',
    kind: 'bay',
    label: '整跨平均',
    ppfd: ppfdBay,
    targetMin: tMin,
    targetMax: tMax,
    gap: gapBay,
    shadePct: Math.round(((a?.shadeOpenPercent ?? 100) + (b?.shadeOpenPercent ?? 100)) / 2),
    dimPct: Math.round((avgDimForZone(a) + avgDimForZone(b)) / 2),
    status: stBay,
    statusZh: statusZh(stBay),
  })

  for (const z of ['ZONE-A', 'ZONE-B'] as const) {
    const el = lights[z]
    if (!el) continue
    const dyn = el.dynamicTarget
    const ppfd = Number(el.effectivePpfd)
    const min = dyn?.instantMin ?? 0
    const max = dyn?.instantMax ?? 0
    const st = gapStatus(ppfd, min, max, dyn?.photoperiodMask ?? 1)
    rows.push({
      id: z,
      kind: 'zone',
      label: zoneShortLabel(z, el.name),
      zoneId: z,
      ppfd,
      targetMin: min,
      targetMax: max,
      gap: ppfd - (min + max) / 2,
      shadePct: el.shadeOpenPercent ?? 100,
      dimPct: avgDimForZone(el),
      status: st,
      statusZh: statusZh(st),
    })
  }

  for (const bed of BEDS) {
    const el = lights[bed.zoneId]
    const stat = el?.bedStats?.find((s) => s.bedId === bed.bedId)
    const dyn = el?.dynamicTarget
    const ppfd = stat ? Number(stat.avgPpfd) : Number(el?.sensorPpfd?.[SENSORS_BY_BED[bed.bedId]?.[1] ?? ''] ?? 0)
    const min = dyn?.instantMin ?? 0
    const max = dyn?.instantMax ?? 0
    const st = gapStatus(ppfd, min, max, dyn?.photoperiodMask ?? 1)
    rows.push({
      id: bed.bedId,
      kind: 'bed',
      label: bedShortLabel(bed.bedId),
      zoneId: bed.zoneId,
      bedId: bed.bedId,
      ppfd,
      targetMin: min,
      targetMax: max,
      gap: ppfd - (min + max) / 2,
      shadePct: el?.shadeOpenPercent ?? 100,
      dimPct: avgDimForZone(el),
      status: st,
      statusZh: statusZh(st),
    })
  }

  return rows
}

export function buildChartLayers(
  scope: ChartScope,
  lights: Record<string, GhEffectiveLight>,
  focusZoneId: string,
  focusBedId?: string,
): { anchor: DaySeriesPoint[]; layers: CurveLayerDef[] } {
  const a = lights['ZONE-A']
  const b = lights['ZONE-B']
  const focus = lights[focusZoneId] ?? a ?? b
  const anchor = focus?.series ?? a?.series ?? b?.series ?? []

  if (scope === 'bay') {
    const layers: CurveLayerDef[] = []
    // 只保留调控后有效光：直接对照目标带判断东西半跨是否合理
    if (a?.series?.length) {
      layers.push({
        id: 'ZONE-A',
        label: '西半跨 有效光',
        color: ZONE_COLORS['ZONE-A'],
        series: a.series,
        getValue: (p) => p.controlledPpfd,
        width: 2.5,
      })
    }
    if (b?.series?.length) {
      layers.push({
        id: 'ZONE-B',
        label: '东半跨 有效光',
        color: ZONE_COLORS['ZONE-B'],
        series: b.series,
        getValue: (p) => p.controlledPpfd,
        width: 2.5,
        dash: [7, 4],
      })
    }
    return { anchor: a?.series ?? anchor, layers }
  }

  if (scope === 'beds') {
    const layers: CurveLayerDef[] = []
    BEDS.forEach((bed, i) => {
      const el = lights[bed.zoneId]
      if (!el?.series?.length) return
      layers.push({
        id: bed.bedId,
        label: bedShortLabel(bed.bedId),
        color: BED_COLORS[i % BED_COLORS.length],
        series: el.series,
        getValue: (p) => bedSeriesPpfd(p, bed.bedId),
        width: bed.bedId === focusBedId ? 2.8 : 2,
        dash: BED_DASHES[i % BED_DASHES.length],
      })
    })
    return { anchor, layers }
  }

  if (scope === 'sensors') {
    const bedId = focusBedId ?? 'BED-A-M'
    const bed = BEDS.find((x) => x.bedId === bedId) ?? BEDS[1]
    const el = lights[bed.zoneId]
    const sns = SENSORS_BY_BED[bed.bedId] ?? []
    const layers: CurveLayerDef[] = sns.map((sn, i) => ({
      id: sn,
      label: sn.replace('PAR-', '').replace('ZONE-', ''),
      color: SENSOR_COLORS[i] ?? '#86868b',
      series: el?.series ?? [],
      getValue: (p) => {
        const v = p.sensorPpfd?.[sn]
        return v != null && Number.isFinite(v) ? v : Number.NaN
      },
      width: 2,
      dash: SENSOR_DASHES[i % SENSOR_DASHES.length],
    }))
    return { anchor: el?.series ?? anchor, layers }
  }

  // zone — 有效光 + 无调控自然光对照（目标带由图表绘制）
  const el = focus
  const layers: CurveLayerDef[] = []
  if (el?.series?.length) {
    layers.push({
      id: 'controlled',
      label: '有效光（调控后）',
      color: ZONE_COLORS[focusZoneId] ?? '#1d4ed8',
      series: el.series,
      getValue: (p) => p.controlledPpfd,
      width: 2.5,
    })
    layers.push({
      id: 'natural',
      label: '仅自然光（对照）',
      color: '#8e8e93',
      series: el.series,
      getValue: (p) => p.naturalPpfd,
      width: 1.5,
      dashed: true,
    })
  }
  return { anchor, layers }
}

export function snapshotAtPlayhead(
  series: DaySeriesPoint[],
  minute: number,
): DaySeriesPoint | undefined {
  return pointAt(series, minute)
}
