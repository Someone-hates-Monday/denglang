<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { DaySeriesPoint } from '../api/greenhouse'
import type { CurveLayerDef } from '../scene/dayCurveLayers'

const props = defineProps<{
  anchorSeries?: DaySeriesPoint[]
  series?: DaySeriesPoint[]
  layers?: CurveLayerDef[]
  minuteOfDay: number
  title?: string
  mode?: 'light' | 'climate' | 'control' | 'gap'
  /** 仅半跨详情时显示室外 PAR 参考线 */
  showOutdoor?: boolean
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

const GAP_BREAK_MIN = 45

function densifySeries(raw: DaySeriesPoint[]): DaySeriesPoint[] {
  if (raw.length < 2) return raw
  const sorted = [...raw].sort((a, b) => a.minuteOfDay - b.minuteOfDay)
  const gaps: number[] = []
  for (let i = 1; i < sorted.length; i++) {
    gaps.push(sorted[i].minuteOfDay - sorted[i - 1].minuteOfDay)
  }
  const gapCopy = [...gaps].sort((a, b) => a - b)
  const medianGap = gapCopy[Math.floor(gapCopy.length / 2)] ?? 12
  if (medianGap <= 4) return sorted

  const out: DaySeriesPoint[] = []
  const step = Math.max(1, medianGap / 4)
  for (let i = 0; i < sorted.length - 1; i++) {
    const a = sorted[i]
    const b = sorted[i + 1]
    out.push(a)
    const span = b.minuteOfDay - a.minuteOfDay
    if (span <= 0 || span > GAP_BREAK_MIN) continue
    const n = Math.min(8, Math.floor(span / step))
    for (let k = 1; k < n; k++) {
      const t = k / n
      out.push(lerpPoint(a, b, t))
    }
  }
  out.push(sorted[sorted.length - 1])
  return out
}

function lerpPoint(a: DaySeriesPoint, b: DaySeriesPoint, t: number): DaySeriesPoint {
  const L = (x: number, y: number) => x + (y - x) * t
  return {
    minuteOfDay: L(a.minuteOfDay, b.minuteOfDay),
    outdoorPpfd: L(a.outdoorPpfd, b.outdoorPpfd),
    naturalPpfd: L(a.naturalPpfd, b.naturalPpfd),
    sunInPpfd: L(a.sunInPpfd, b.sunInPpfd),
    ledPpfd: L(a.ledPpfd, b.ledPpfd),
    controlledPpfd: L(a.controlledPpfd, b.controlledPpfd),
    humidityPct: L(a.humidityPct, b.humidityPct),
    temperatureC: L(a.temperatureC, b.temperatureC),
    shadeOpenPercent: L(a.shadeOpenPercent, b.shadeOpenPercent),
    avgDimmingPercent: L(a.avgDimmingPercent, b.avgDimmingPercent),
    targetPpfdMin:
      a.targetPpfdMin != null && b.targetPpfdMin != null
        ? L(a.targetPpfdMin, b.targetPpfdMin)
        : a.targetPpfdMin ?? b.targetPpfdMin,
    targetPpfdMax:
      a.targetPpfdMax != null && b.targetPpfdMax != null
        ? L(a.targetPpfdMax, b.targetPpfdMax)
        : a.targetPpfdMax ?? b.targetPpfdMax,
    targetMid:
      a.targetMid != null && b.targetMid != null ? L(a.targetMid, b.targetMid) : a.targetMid ?? b.targetMid,
    gapPpfd: a.gapPpfd != null && b.gapPpfd != null ? L(a.gapPpfd, b.gapPpfd) : a.gapPpfd ?? b.gapPpfd,
    vpdKpa: a.vpdKpa != null && b.vpdKpa != null ? L(a.vpdKpa, b.vpdKpa) : a.vpdKpa ?? b.vpdKpa,
    dliSoFar: a.dliSoFar != null && b.dliSoFar != null ? L(a.dliSoFar, b.dliSoFar) : a.dliSoFar ?? b.dliSoFar,
    bedPpfd: a.bedPpfd,
    sensorPpfd: a.sensorPpfd,
  }
}

function anchorData(): DaySeriesPoint[] {
  return densifySeries(props.anchorSeries ?? props.series ?? [])
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const W = canvas.width
  const H = canvas.height
  const pad = { l: 48, r: 44, t: 58, b: 34 }
  const plotW = W - pad.l - pad.r
  const plotH = H - pad.t - pad.b
  ctx.clearRect(0, 0, W, H)
  ctx.fillStyle = '#fbfbfd'
  ctx.fillRect(0, 0, W, H)

  const anchor = anchorData()

  ctx.fillStyle = '#1d1d1f'
  ctx.font = '600 13px system-ui, sans-serif'
  ctx.fillText(props.title || '日变化', pad.l, 16)

  ctx.strokeStyle = 'rgba(0,0,0,0.12)'
  ctx.beginPath()
  ctx.moveTo(pad.l, pad.t)
  ctx.lineTo(pad.l, pad.t + plotH)
  ctx.lineTo(pad.l + plotW, pad.t + plotH)
  ctx.stroke()

  const xAt = (m: number) => pad.l + (m / 1440) * plotW
  ctx.fillStyle = '#6e6e73'
  ctx.font = '11px ui-monospace, monospace'
  for (let h = 0; h <= 24; h += 4) {
    const x = xAt(h * 60)
    ctx.beginPath()
    ctx.moveTo(x, pad.t + plotH)
    ctx.lineTo(x, pad.t + plotH + 4)
    ctx.stroke()
    ctx.fillText(`${h}:00`, x - 12, pad.t + plotH + 18)
  }

  const layers = props.layers ?? []
  const hasData = anchor.length > 0 || layers.some((l) => l.series.length > 0)
  if (!hasData) {
    ctx.fillStyle = '#86868b'
    ctx.fillText('等待仿真采样…（一天压缩为 2 分钟 · 连续推进）', pad.l + 8, pad.t + plotH / 2)
    return
  }

  let legendItems: { c: string; t: string }[] = []

  if (props.mode === 'climate') {
    const series = anchor
    const hum = series.map((s) => s.humidityPct)
    const temp = series.map((s) => s.temperatureC)
    const maxH = Math.max(100, ...hum)
    const minH = 30
    const maxT = Math.max(40, ...temp)
    const minT = 10
    const yH = (v: number) => pad.t + plotH - ((v - minH) / (maxH - minH)) * plotH
    const yT = (v: number) => pad.t + plotH - ((v - minT) / (maxT - minT)) * plotH
    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => yH(s.humidityPct), '#0071e3', 2)
    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => yT(s.temperatureC), '#ff9500', 2)
    legendItems = [
      { c: '#0071e3', t: '湿度 %' },
      { c: '#ff9500', t: '温度 °C' },
    ]
  } else if (props.mode === 'control') {
    const allVals = layers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p)))
    const maxV = Math.max(100, ...allVals) * 1.05
    const y = (v: number) => pad.t + plotH - (v / maxV) * plotH
    for (const layer of layers) {
      strokeLayer(ctx, densifySeries(layer.series), layer, xAt, y)
    }
    legendItems = layers.map((l) => ({ c: l.color, t: l.label + (l.dashed ? ' ···' : '') }))
  } else if (props.mode === 'gap') {
    const allGaps = layers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p)))
    const ext = Math.max(30, ...allGaps.map(Math.abs), 1) * 1.15
    const y = (v: number) => pad.t + plotH / 2 - (v / ext) * (plotH / 2)
    ctx.strokeStyle = 'rgba(0,0,0,0.2)'
    ctx.setLineDash([2, 4])
    ctx.beginPath()
    ctx.moveTo(pad.l, y(0))
    ctx.lineTo(pad.l + plotW, y(0))
    ctx.stroke()
    ctx.setLineDash([])
    for (const layer of layers) {
      strokeLayer(ctx, densifySeries(layer.series), layer, xAt, y)
    }
    ctx.fillStyle = '#6e6e73'
    ctx.font = '10px system-ui'
    ctx.fillText('↑ 高于目标', pad.l + plotW + 4, pad.t + 10)
    ctx.fillText('↓ 低于目标', pad.l + plotW + 4, pad.t + plotH - 6)
    legendItems = layers.map((l) => ({ c: l.color, t: l.label }))
  } else {
    const drawLayers =
      layers.length > 0
        ? layers
        : [
            {
              id: 'default',
              label: '调控后',
              color: '#0071e3',
              series: anchor,
              getValue: (p: DaySeriesPoint) => p.controlledPpfd,
              width: 2.5,
            } satisfies CurveLayerDef,
          ]

    const vals = [
      ...anchor.flatMap((s) => [s.controlledPpfd, s.targetPpfdMax ?? 0, s.targetPpfdMin ?? 0]),
      ...drawLayers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p))),
    ]
    const maxV = Math.max(50, ...vals.filter((v) => v > 0)) * 1.08
    const y = (v: number) => pad.t + plotH - (v / maxV) * plotH

    const targetPts = anchor.filter((s) => (s.targetPpfdMax ?? 0) > 1 && (s.targetPpfdMin ?? 0) >= 0)
    if (targetPts.length >= 2) {
      ctx.beginPath()
      targetPts.forEach((s, i) => {
        const xi = xAt(s.minuteOfDay)
        const yi = y(s.targetPpfdMax ?? 0)
        if (i === 0) ctx.moveTo(xi, yi)
        else ctx.lineTo(xi, yi)
      })
      for (let i = targetPts.length - 1; i >= 0; i--) {
        const s = targetPts[i]
        ctx.lineTo(xAt(s.minuteOfDay), y(s.targetPpfdMin ?? 0))
      }
      ctx.closePath()
      ctx.fillStyle = 'rgba(175, 82, 222, 0.14)'
      ctx.fill()
      strokeLine(
        ctx,
        targetPts,
        (s) => xAt(s.minuteOfDay),
        (s) => y(s.targetPpfdMax ?? 0),
        '#af52de',
        1,
      )
      strokeLine(
        ctx,
        targetPts,
        (s) => xAt(s.minuteOfDay),
        (s) => y(s.targetPpfdMin ?? 0),
        '#af52de',
        1,
      )
    }

    if (props.showOutdoor) {
      strokeLine(ctx, anchor, (s) => xAt(s.minuteOfDay), (s) => y(s.outdoorPpfd), '#aeaeb2', 1.25)
    }

    for (const layer of drawLayers) {
      strokeLayer(ctx, densifySeries(layer.series), layer, xAt, y)
    }

    legendItems = [
      ...(props.showOutdoor ? [{ c: '#aeaeb2', t: '室外 PAR' }] : []),
      ...drawLayers.map((l) => ({ c: l.color, t: l.label })),
      { c: '#af52de', t: '目标带' },
    ]
  }

  legend(ctx, pad.l, pad.l + plotW, 30, legendItems)

  const px = xAt(Math.min(1440, Math.max(0, props.minuteOfDay)))
  ctx.strokeStyle = '#1d1d1f'
  ctx.setLineDash([4, 3])
  ctx.beginPath()
  ctx.moveTo(px, pad.t)
  ctx.lineTo(px, pad.t + plotH)
  ctx.stroke()
  ctx.setLineDash([])
}

function strokeLayer(
  ctx: CanvasRenderingContext2D,
  series: DaySeriesPoint[],
  layer: CurveLayerDef,
  xAt: (m: number) => number,
  y: (v: number) => number,
) {
  strokeLine(
    ctx,
    series,
    (s) => xAt(s.minuteOfDay),
    (s) => y(layer.getValue(s)),
    layer.color,
    layer.width ?? 2,
    layer.dashed,
  )
}

function strokeLine(
  ctx: CanvasRenderingContext2D,
  series: DaySeriesPoint[],
  x: (s: DaySeriesPoint) => number,
  y: (s: DaySeriesPoint) => number,
  color: string,
  width: number,
  dashed = false,
) {
  if (series.length < 2) return
  ctx.strokeStyle = color
  ctx.lineWidth = width
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  if (dashed) ctx.setLineDash([5, 4])

  let started = false
  for (let i = 0; i < series.length; i++) {
    const s = series[i]
    const prev = i > 0 ? series[i - 1] : null
    const gap = prev ? s.minuteOfDay - prev.minuteOfDay : 0
    if (prev && gap > GAP_BREAK_MIN) {
      started = false
    }
    const xi = x(s)
    const yi = y(s)
    if (!started) {
      ctx.beginPath()
      ctx.moveTo(xi, yi)
      started = true
    } else {
      ctx.lineTo(xi, yi)
    }
  }
  if (started) ctx.stroke()
  ctx.setLineDash([])
}

function legend(
  ctx: CanvasRenderingContext2D,
  x0: number,
  maxX: number,
  y: number,
  items: { c: string; t: string }[],
) {
  if (!items.length) return
  const rowH = 16
  let x = x0
  let row = 0
  ctx.font = '10px system-ui, sans-serif'
  for (const it of items) {
    const w = 24 + ctx.measureText(it.t).width
    if (x + w > maxX - 8 && x > x0) {
      row++
      x = x0
    }
    const yy = y + row * rowH
    ctx.fillStyle = it.c
    ctx.fillRect(x, yy - 9, 10, 10)
    ctx.fillStyle = '#6e6e73'
    ctx.fillText(it.t, x + 14, yy)
    x += w
  }
}

onMounted(draw)
watch(
  () => [props.anchorSeries, props.series, props.layers, props.minuteOfDay, props.mode, props.title, props.showOutdoor],
  draw,
  { deep: true },
)
</script>

<template>
  <canvas ref="canvasRef" class="chart" width="960" height="268" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 8px;
}
</style>
