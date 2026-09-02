/**
 * 园艺三色光分解（演示级）：
 * 日光 ≈ 宽带 PAR；补光灯按作物常用 R/B 偏置（红促光合、蓝调形态、绿穿透）。
 * 非光谱仪真值，对齐「植物不吃全白光」答辩叙事。
 */

export type RgbShare = { r: number; g: number; b: number }

/** 晴天/雾天室外 PAR 在 R/G/B 波段的光子份额（归一） */
export const SUN_SHARE: RgbShare = { r: 0.33, g: 0.37, b: 0.3 }

/** 补光灯默认光谱（按作物） */
export const LED_SHARE_BY_CROP: Record<string, RgbShare> = {
  dendrobium: { r: 0.62, g: 0.12, b: 0.26 },
  anoectochilus: { r: 0.52, g: 0.14, b: 0.34 },
  strawberry: { r: 0.7, g: 0.08, b: 0.22 },
  default: { r: 0.65, g: 0.1, b: 0.25 },
}

export type HeatChannel = 'xray' | 'viridis' | 'rgb' | 'R' | 'G' | 'B'

export const HEAT_CHANNEL_LABEL: Record<HeatChannel, string> = {
  xray: '光照',
  viridis: 'Viridis',
  rgb: '三色合成',
  R: '红 R',
  G: '绿 G',
  B: '蓝 B',
}

export function ledShareForRecipe(recipeId?: string | null, cropKey?: string): RgbShare {
  const id = (recipeId || '').toLowerCase()
  if (id.includes('fragaria') || id.includes('strawberry') || cropKey === 'strawberry') {
    return LED_SHARE_BY_CROP.strawberry
  }
  if (id.includes('anoectochilus') || cropKey === 'anoectochilus') {
    return LED_SHARE_BY_CROP.anoectochilus
  }
  if (id.includes('dendrobium') || cropKey === 'dendrobium') {
    return LED_SHARE_BY_CROP.dendrobium
  }
  return LED_SHARE_BY_CROP.default
}

export function splitRgb(
  sunPpfd: number,
  ledPpfd: number,
  ledShare: RgbShare,
  sunShare: RgbShare = SUN_SHARE,
): { r: number; g: number; b: number; total: number } {
  const r = sunPpfd * sunShare.r + ledPpfd * ledShare.r
  const g = sunPpfd * sunShare.g + ledPpfd * ledShare.g
  const b = sunPpfd * sunShare.b + ledPpfd * ledShare.b
  return { r, g, b, total: r + g + b }
}

/** Viridis 伪彩（对标 horticulture-lighting-simulator） */
export function viridisColor(v: number, maxRef: number): [number, number, number] {
  const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
  const stops: [number, number, number, number][] = [
    [0, 68, 1, 84],
    [0.25, 59, 82, 139],
    [0.5, 33, 145, 140],
    [0.75, 94, 201, 98],
    [1, 253, 231, 37],
  ]
  for (let i = 0; i < stops.length - 1; i++) {
    const a = stops[i]
    const b = stops[i + 1]
    if (t <= b[0]) {
      const u = (t - a[0]) / (b[0] - a[0] || 1)
      return [
        Math.round(a[1] + (b[1] - a[1]) * u),
        Math.round(a[2] + (b[2] - a[2]) * u),
        Math.round(a[3] + (b[3] - a[3]) * u),
      ]
    }
  }
  return [253, 231, 37]
}

export function xrayColor(v: number, maxRef: number): [number, number, number] {
  const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
  // 白 → 红：低光照浅、高光照红
  return [255, Math.round(255 * (1 - t) * 0.92), Math.round(255 * (1 - t) * 0.88)]
}

/**
 * 单通道显示值：抬高补光相对日光的贡献，避免灯关/日光主导时 R/G/B 热力几乎同形。
 * （日光份额接近均匀 → 通道图 ∝ 总 PPFD；灯下红蓝偏置才是「分波段补足」的可见证据）
 */
export function emphasizeLedChannel(
  channelPpfd: number,
  sunPpfd: number,
  sunShareFrac: number,
  ledBoost = 2.8,
): number {
  const sunPart = Math.max(0, sunPpfd) * sunShareFrac
  const ledPart = Math.max(0, channelPpfd - sunPart)
  return sunPart + ledPart * ledBoost
}

export function channelMonoColor(
  v: number,
  maxRef: number,
  channel: 'R' | 'G' | 'B',
): [number, number, number] {
  const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
  const lo = 18
  if (channel === 'R') return [Math.round(lo + t * 237), Math.round(lo * (1 - t)), Math.round(lo * (1 - t))]
  if (channel === 'G') return [Math.round(lo * (1 - t)), Math.round(lo + t * 220), Math.round(lo * (1 - t))]
  return [Math.round(lo * (1 - t) + t * 40), Math.round(lo * (1 - t) + t * 90), Math.round(lo + t * 237)]
}

/** 三色合成：按通道光子通量映射到 RGB 显示色 */
export function rgbCompositeColor(
  r: number,
  g: number,
  b: number,
  maxRef: number,
  sunPpfd = 0,
  ledBoost = 2.2,
): [number, number, number] {
  const er = emphasizeLedChannel(r, sunPpfd, SUN_SHARE.r, ledBoost)
  const eg = emphasizeLedChannel(g, sunPpfd, SUN_SHARE.g, ledBoost)
  const eb = emphasizeLedChannel(b, sunPpfd, SUN_SHARE.b, ledBoost)
  const s = Math.max(maxRef, 1)
  const gain = 1.35
  return [
    Math.min(255, Math.round(28 + (er / s) * 227 * gain)),
    Math.min(255, Math.round(36 + (eg / s) * 219 * gain)),
    Math.min(255, Math.round(48 + (eb / s) * 207 * gain)),
  ]
}
