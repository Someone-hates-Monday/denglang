import type { DaySeriesPoint, GhEffectiveLight } from '../api/greenhouse'
import { BEDS } from './cropCatalog'

export type ChartScope = 'bay' | 'zone' | 'beds' | 'sensors' | 'control'

export type CurveLayerDef = {
  id: string
  label: string
  color: string
  series: DaySeriesPoint[]
  getValue: (p: DaySeriesPoint) => number
  width?: number
  dashed?: boolean
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
  'ZONE-A': '#0071e3',
  'ZONE-B': '#34c759',
}

const BED_COLORS = ['#0071e3', '#5ac8fa', '#5856d6', '#34c759', '#30d158', '#248a3d']

/** 测点与床对应（layout cq-demo-bay-v1） */
export const SENSORS_BY_BED: Record<string, string[]> = {
  'BED-A-S': ['PAR-ZONE-A-01', 'PAR-ZONE-A-02', 'PAR-ZONE-A-03'],
  'BED-A-M': ['PAR-ZONE-A-04', 'PAR-ZONE-A-05', 'PAR-ZONE-A-06'],
  'BED-A-N': ['PAR-ZONE-A-07', 'PAR-ZONE-A-08', 'PAR-ZONE-A-09'],
  'BED-B-S': ['PAR-ZONE-B-01', 'PAR-ZONE-B-02', 'PAR-ZONE-B-03'],
  'BED-B-M': ['PAR-ZONE-B-04', 'PAR-ZONE-B-05', 'PAR-ZONE-B-06'],
  'BED-B-N': ['PAR-ZONE-B-07', 'PAR-ZONE-B-08', 'PAR-ZONE-B-09'],
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

function bayAvgSeries(a: DaySeriesPoint[], b: DaySeriesPoint[]): DaySeriesPoint[] {
  const byMin = new Map<number, DaySeriesPoint>()
  for (const p of a) byMin.set(Math.round(p.minuteOfDay), p)
  const out: DaySeriesPoint[] = []
  for (const pb of b) {
    const m = Math.round(pb.minuteOfDay)
    const pa = byMin.get(m)
    if (!pa) continue
    out.push({
      ...pa,
      controlledPpfd: (pa.controlledPpfd + pb.controlledPpfd) / 2,
      naturalPpfd: (pa.naturalPpfd + pb.naturalPpfd) / 2,
      ledPpfd: (pa.ledPpfd + pb.ledPpfd) / 2,
      gapPpfd:
        pa.gapPpfd != null && pb.gapPpfd != null ? (pa.gapPpfd + pb.gapPpfd) / 2 : pa.gapPpfd,
    })
  }
  return out.length ? out : a.length >= b.length ? a : b
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

  if (scope === 'control') {
    const layers: CurveLayerDef[] = []
    for (const z of ['ZONE-A', 'ZONE-B'] as const) {
      const el = lights[z]
      if (!el?.series?.length) continue
      layers.push({
        id: `${z}-shade`,
        label: `${zoneShortLabel(z, el.name)} 遮阳%`,
        color: ZONE_COLORS[z],
        series: el.series,
        getValue: (p) => p.shadeOpenPercent,
        width: 2,
      })
      layers.push({
        id: `${z}-dim`,
        label: `${zoneShortLabel(z, el.name)} 补光%`,
        color: ZONE_COLORS[z],
        series: el.series,
        getValue: (p) => p.avgDimmingPercent,
        width: 1.5,
        dashed: true,
      })
    }
    return { anchor: a?.series ?? anchor, layers }
  }

  if (scope === 'bay') {
    const layers: CurveLayerDef[] = []
    if (a?.series?.length) {
      layers.push({
        id: 'ZONE-A',
        label: '西半跨 调控后',
        color: ZONE_COLORS['ZONE-A'],
        series: a.series,
        getValue: (p) => p.controlledPpfd,
        width: 2.5,
      })
    }
    if (b?.series?.length) {
      layers.push({
        id: 'ZONE-B',
        label: '东半跨 调控后',
        color: ZONE_COLORS['ZONE-B'],
        series: b.series,
        getValue: (p) => p.controlledPpfd,
        width: 2.5,
      })
    }
    if (a?.series?.length && b?.series?.length) {
      layers.push({
        id: 'BAY-AVG',
        label: '整跨平均',
        color: '#5856d6',
        series: bayAvgSeries(a.series, b.series),
        getValue: (p) => p.controlledPpfd,
        width: 2,
        dashed: true,
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
        getValue: (p) => p.bedPpfd?.[bed.bedId] ?? p.controlledPpfd,
        width: bed.bedId === focusBedId ? 2.8 : 1.75,
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
      color: ['#0071e3', '#5ac8fa', '#5856d6'][i] ?? '#86868b',
      series: el?.series ?? [],
      getValue: (p) => p.sensorPpfd?.[sn] ?? 0,
      width: 2,
    }))
    return { anchor: el?.series ?? anchor, layers }
  }

  // zone — 当前半跨 + 缺口虚线
  const el = focus
  const layers: CurveLayerDef[] = []
  if (el?.series?.length) {
    layers.push({
      id: 'controlled',
      label: '调控后 PAR',
      color: ZONE_COLORS[focusZoneId] ?? '#0071e3',
      series: el.series,
      getValue: (p) => p.controlledPpfd,
      width: 2.5,
    })
    layers.push({
      id: 'natural',
      label: '棚内自然',
      color: '#34c759',
      series: el.series,
      getValue: (p) => p.naturalPpfd,
      width: 1.75,
    })
    layers.push({
      id: 'led',
      label: '补光贡献',
      color: '#ff9500',
      series: el.series,
      getValue: (p) => p.ledPpfd,
      width: 1.25,
    })
    if (el.series.some((p) => p.gapPpfd != null)) {
      layers.push({
        id: 'gap',
        label: '距目标中值 Δ',
        color: '#ff3b30',
        series: el.series,
        getValue: (p) => p.gapPpfd ?? 0,
        width: 1.5,
        dashed: true,
      })
    }
  }
  return { anchor, layers }
}

export function snapshotAtPlayhead(
  series: DaySeriesPoint[],
  minute: number,
): DaySeriesPoint | undefined {
  return pointAt(series, minute)
}
