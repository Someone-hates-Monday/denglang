<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import type { DaySeriesPoint } from '../api/greenhouse'

const props = defineProps<{
  series: DaySeriesPoint[]
  minuteOfDay: number
  title?: string
  mode?: 'light' | 'climate'
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

function densifySeries(raw: DaySeriesPoint[]): DaySeriesPoint[] {
  if (raw.length < 2) return raw
  // 按 minuteOfDay 排序；若已够密则原样
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
    vpdKpa: a.vpdKpa != null && b.vpdKpa != null ? L(a.vpdKpa, b.vpdKpa) : a.vpdKpa ?? b.vpdKpa,
    dliSoFar: a.dliSoFar != null && b.dliSoFar != null ? L(a.dliSoFar, b.dliSoFar) : a.dliSoFar ?? b.dliSoFar,
  }
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const W = canvas.width
  const H = canvas.height
  const pad = { l: 44, r: 52, t: 28, b: 32 }
  const plotW = W - pad.l - pad.r
  const plotH = H - pad.t - pad.b
  ctx.clearRect(0, 0, W, H)
  ctx.fillStyle = '#fbfbfd'
  ctx.fillRect(0, 0, W, H)

  const series = densifySeries(props.series || [])
  ctx.fillStyle = '#1d1d1f'
  ctx.font = '600 13px system-ui, sans-serif'
  ctx.fillText(props.title || '日变化', pad.l, 18)

  // axes
  ctx.strokeStyle = 'rgba(0,0,0,0.12)'
  ctx.beginPath()
  ctx.moveTo(pad.l, pad.t)
  ctx.lineTo(pad.l, pad.t + plotH)
  ctx.lineTo(pad.l + plotW, pad.t + plotH)
  ctx.stroke()

  const xAt = (m: number) => pad.l + (m / 1440) * plotW
  // hour ticks
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

  if (!series.length) {
    ctx.fillStyle = '#86868b'
    ctx.fillText('等待仿真采样…（一天压缩为 2 分钟 · 连续推进）', pad.l + 8, pad.t + plotH / 2)
    return
  }

  if (props.mode === 'climate') {
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
  } else {
    const vals = series.flatMap((s) => [
      s.outdoorPpfd,
      s.naturalPpfd,
      s.controlledPpfd,
      s.targetPpfdMax ?? 0,
      s.targetPpfdMin ?? 0,
    ])
    const maxV = Math.max(50, ...vals) * 1.08
    const y = (v: number) => pad.t + plotH - (v / maxV) * plotH

    // 动态目标带（半透明）
    const hasTarget = series.some((s) => s.targetPpfdMax != null)
    if (hasTarget && series.length >= 2) {
      ctx.beginPath()
      series.forEach((s, i) => {
        const xi = xAt(s.minuteOfDay)
        const yi = y(s.targetPpfdMax ?? 0)
        if (i === 0) ctx.moveTo(xi, yi)
        else ctx.lineTo(xi, yi)
      })
      for (let i = series.length - 1; i >= 0; i--) {
        const s = series[i]
        ctx.lineTo(xAt(s.minuteOfDay), y(s.targetPpfdMin ?? 0))
      }
      ctx.closePath()
      ctx.fillStyle = 'rgba(175, 82, 222, 0.14)'
      ctx.fill()
      strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.targetPpfdMax ?? 0), '#af52de', 1)
      strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.targetPpfdMin ?? 0), '#af52de', 1)
    }

    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.outdoorPpfd), '#aeaeb2', 1.5)
    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.naturalPpfd), '#34c759', 2)
    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.controlledPpfd), '#0071e3', 2.5)
    strokeLine(ctx, series, (s) => xAt(s.minuteOfDay), (s) => y(s.ledPpfd), '#ff9500', 1.25)
    legend(ctx, pad.l, [
      { c: '#aeaeb2', t: '室外 PAR' },
      { c: '#34c759', t: '棚内自然' },
      { c: '#0071e3', t: '调控后' },
      { c: '#ff9500', t: '补光' },
      { c: '#af52de', t: '此刻目标带' },
    ])
  }

  // playhead
  const px = xAt(props.minuteOfDay)
  ctx.strokeStyle = '#1d1d1f'
  ctx.setLineDash([4, 3])
  ctx.beginPath()
  ctx.moveTo(px, pad.t)
  ctx.lineTo(px, pad.t + plotH)
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

function legend(
  ctx: CanvasRenderingContext2D,
  x0: number,
  items: { c: string; t: string }[],
) {
  let x = x0 + 8
  ctx.font = '11px system-ui, sans-serif'
  for (const it of items) {
    ctx.fillStyle = it.c
    ctx.fillRect(x, 8, 10, 10)
    ctx.fillStyle = '#6e6e73'
    ctx.fillText(it.t, x + 14, 17)
    x += ctx.measureText(it.t).width + 28
  }
}

onMounted(draw)
watch(() => [props.series, props.minuteOfDay, props.mode], draw, { deep: true })
</script>

<template>
  <canvas ref="canvasRef" class="chart" width="720" height="220" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 8px;
}
</style>
