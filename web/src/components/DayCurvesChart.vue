<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { DaySeriesPoint } from '../api/greenhouse'
import type { CurveLayerDef } from '../scene/dayCurveLayers'

const props = defineProps<{
  /** 用于目标带、室外 PAR 的锚定序列 */
  anchorSeries?: DaySeriesPoint[]
  /** 兼容旧用法：单序列 */
  series?: DaySeriesPoint[]
  layers?: CurveLayerDef[]
  minuteOfDay: number
  title?: string
  mode?: 'light' | 'climate' | 'control' | 'gap'
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

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
    if (span <= 0) continue
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
  const pad = { l: 44, r: props.mode === 'gap' ? 52 : 52, t: 28, b: 32 }
  const plotW = W - pad.l - pad.r
  const plotH = H - pad.t - pad.b
  ctx.clearRect(0, 0, W, H)
  ctx.fillStyle = '#fbfbfd'
  ctx.fillRect(0, 0, W, H)

  const anchor = anchorData()
  ctx.fillStyle = '#1d1d1f'
  ctx.font = '600 13px system-ui, sans-serif'
  ctx.fillText(props.title || '日变化', pad.l, 18)

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
    ctx.fillText(`${h}:00`, x - 12, pad.t + plotH + 16)
  }

  const layers = props.layers ?? []
  const hasData = anchor.length > 0 || layers.some((l) => l.series.length > 0)
  if (!hasData) {
    ctx.fillStyle = '#86868b'
    ctx.fillText('等待仿真采样…（一天压缩为 2 分钟 · 连续推进）', pad.l + 8, pad.t + plotH / 2)
    return
  }

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
    legend(ctx, pad.l, [
      { c: '#0071e3', t: '湿度 %' },
      { c: '#ff9500', t: '温度 °C' },
    ])
  } else if (props.mode === 'control') {
    const allVals = layers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p)))
    const maxV = Math.max(100, ...allVals) * 1.05
    const y = (v: number) => pad.t + plotH - (v / maxV) * plotH
    for (const layer of layers) {
      const s = densifySeries(layer.series)
      strokeLayer(ctx, s, layer, xAt, y)
    }
    legend(
      ctx,
      pad.l,
      layers.map((l) => ({ c: l.color, t: l.label + (l.dashed ? ' ···' : '') })),
    )
  } else if (props.mode === 'gap') {
    const allGaps = layers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p)))
    const ext = Math.max(30, ...allGaps.map(Math.abs)) * 1.15
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
    ctx.fillText('↑ 高于目标', pad.l + plotW + 4, pad.t + 8)
    ctx.fillText('↓ 低于目标', pad.l + plotW + 4, pad.t + plotH - 4)
    legend(
      ctx,
      pad.l,
      layers.map((l) => ({ c: l.color, t: l.label })),
    )
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
      ...anchor.flatMap((s) => [s.outdoorPpfd, s.targetPpfdMax ?? 0, s.targetPpfdMin ?? 0]),
      ...drawLayers.flatMap((l) => densifySeries(l.series).map((p) => l.getValue(p))),
    ]
    const maxV = Math.max(50, ...vals) * 1.08
    const y = (v: number) => pad.t + plotH - (v / maxV) * plotH

    const hasTarget = anchor.some((s) => s.targetPpfdMax != null)
    if (hasTarget && anchor.length >= 2) {
      ctx.beginPath()
      anchor.forEach((s, i) => {
        const xi = xAt(s.minuteOfDay)
        const yi = y(s.targetPpfdMax ?? 0)
        if (i === 0) ctx.moveTo(xi, yi)
        else ctx.lineTo(xi, yi)
      })
      for (let i = anchor.length - 1; i >= 0; i--) {
        const s = anchor[i]
        ctx.lineTo(xAt(s.minuteOfDay), y(s.targetPpfdMin ?? 0))
      }
      ctx.closePath()
      ctx.fillStyle = 'rgba(175, 82, 222, 0.14)'
      ctx.fill()
      strokeLine(ctx, anchor, (s) => xAt(s.minuteOfDay), (s) => y(s.targetPpfdMax ?? 0), '#af52de', 1)
      strokeLine(ctx, anchor, (s) => xAt(s.minuteOfDay), (s) => y(s.targetPpfdMin ?? 0), '#af52de', 1)
    }

    if (drawLayers.length <= 3) {
      strokeLine(ctx, anchor, (s) => xAt(s.minuteOfDay), (s) => y(s.outdoorPpfd), '#aeaeb2', 1.25)
    }

    for (const layer of drawLayers) {
      strokeLayer(ctx, densifySeries(layer.series), layer, xAt, y)
    }

    const legendItems = [
      ...(drawLayers.length <= 3 ? [{ c: '#aeaeb2', t: '室外 PAR' }] : []),
      ...drawLayers.map((l) => ({ c: l.color, t: l.label })),
      { c: '#af52de', t: '目标带' },
    ]
    legend(ctx, pad.l, legendItems.slice(0, 8))
  }

  const px = xAt(props.minuteOfDay)
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
  if (series.length < 2) return
  ctx.strokeStyle = layer.color
  ctx.lineWidth = layer.width ?? 2
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  if (layer.dashed) ctx.setLineDash([5, 4])
  ctx.beginPath()
  series.forEach((s, i) => {
    const xi = xAt(s.minuteOfDay)
    const yi = y(layer.getValue(s))
    if (i === 0) ctx.moveTo(xi, yi)
    else ctx.lineTo(xi, yi)
  })
  ctx.stroke()
  ctx.setLineDash([])
}

function strokeLine(
  ctx: CanvasRenderingContext2D,
  series: DaySeriesPoint[],
  x: (s: DaySeriesPoint) => number,
  y: (s: DaySeriesPoint) => number,
  color: string,
  width: number,
) {
  if (series.length < 2) return
  ctx.strokeStyle = color
  ctx.lineWidth = width
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  ctx.beginPath()
  series.forEach((s, i) => {
    const xi = x(s)
    const yi = y(s)
    if (i === 0) ctx.moveTo(xi, yi)
    else ctx.lineTo(xi, yi)
  })
  ctx.stroke()
}

function legend(ctx: CanvasRenderingContext2D, x0: number, items: { c: string; t: string }[]) {
  let x = x0 + 8
  ctx.font = '11px system-ui, sans-serif'
  for (const it of items) {
    ctx.fillStyle = it.c
    ctx.fillRect(x, 8, 10, 10)
    ctx.fillStyle = '#6e6e73'
    ctx.fillText(it.t, x + 14, 17)
    x += ctx.measureText(it.t).width + 22
    if (x > ctx.canvas.width - 80) break
  }
}

onMounted(draw)
watch(
  () => [props.anchorSeries, props.series, props.layers, props.minuteOfDay, props.mode, props.title],
  draw,
  { deep: true },
)
</script>

<template>
  <canvas ref="canvasRef" class="chart" width="960" height="240" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 8px;
}
</style>
