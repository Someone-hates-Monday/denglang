import type { GhRecipe } from '../api/greenhouse'

/** 作物种类（同种同模） */
export type CropKey = 'dendrobium' | 'anoectochilus' | 'strawberry'

export type BedCropSpec = {
  bedId: string
  zoneId: 'ZONE-A' | 'ZONE-B'
  /** 床中心 Three：x 东、z 北 */
  x: number
  z: number
  y0: number
  y1: number
  x0: number
  x1: number
  roleZh: string
}

/** MVP 演示：整跨单作物，不混种（ZONE-A/B 仅为半跨控光分区） */
export const BAY_SINGLE_CROP: CropKey = 'dendrobium'

export const CROP_META: Record<
  CropKey,
  { nameZh: string; glb: string; color: number; shortZh: string }
> = {
  dendrobium: {
    nameZh: '铁皮石斛',
    glb: 'bed-dendrobium.glb',
    color: 0x34c759,
    shortZh: '石斛',
  },
  anoectochilus: {
    nameZh: '台湾金线莲',
    glb: 'bed-anoectochilus.glb',
    color: 0x0071e3,
    shortZh: '金线莲',
  },
  strawberry: {
    nameZh: '设施草莓',
    glb: 'bed-strawberry.glb',
    color: 0xff3b30,
    shortZh: '草莓',
  },
}

export const BEDS: BedCropSpec[] = [
  { bedId: 'BED-A-S', zoneId: 'ZONE-A', x: 4, z: 1.4, x0: 0.5, x1: 7.5, y0: 0.8, y1: 2.0, roleZh: '南床' },
  { bedId: 'BED-A-M', zoneId: 'ZONE-A', x: 4, z: 3.5, x0: 0.5, x1: 7.5, y0: 2.9, y1: 4.1, roleZh: '中床' },
  { bedId: 'BED-A-N', zoneId: 'ZONE-A', x: 4, z: 5.6, x0: 0.5, x1: 7.5, y0: 5.0, y1: 6.2, roleZh: '北床' },
  { bedId: 'BED-B-S', zoneId: 'ZONE-B', x: 12, z: 1.4, x0: 8.5, x1: 15.5, y0: 0.8, y1: 2.0, roleZh: '南床' },
  { bedId: 'BED-B-M', zoneId: 'ZONE-B', x: 12, z: 3.5, x0: 8.5, x1: 15.5, y0: 2.9, y1: 4.1, roleZh: '中床' },
  { bedId: 'BED-B-N', zoneId: 'ZONE-B', x: 12, z: 5.6, x0: 8.5, x1: 15.5, y0: 5.0, y1: 6.2, roleZh: '北床' },
]

/** 石斛 L1 组培/炼苗搁架：东西半跨中床、北床（南床留强自然光对照） */
export const L1_BED_IDS = new Set(['BED-A-M', 'BED-A-N', 'BED-B-M', 'BED-B-N'])

export function bedHasL1Tier(bedId: string): boolean {
  return L1_BED_IDS.has(bedId)
}

/** 由配方 id 解析作物种类（3D 演示冻结为整跨石斛） */
export function cropKeyFromRecipeId(_recipeId: string | undefined | null, _zoneId: string): CropKey {
  return BAY_SINGLE_CROP
}

export function cropLabel(recipe: GhRecipe | undefined, zoneId: string, recipeId?: string): {
  key: CropKey
  nameZh: string
  stage: string
} {
  const key = cropKeyFromRecipeId(recipe?.recipeId || recipeId, zoneId)
  return {
    key,
    nameZh: recipe?.cropNameZh || CROP_META[key].nameZh,
    stage: recipe?.stage || '—',
  }
}

/** 在床位矩形内对光场网格采样平均 PPFD */
export function sampleBedPpfd(
  grid: { x: number; y: number; ppfd: number; sunPpfd?: number; ledPpfd?: number }[] | undefined,
  bed: BedCropSpec,
): { ppfd: number; sun: number; led: number } {
  if (!grid?.length) return { ppfd: 0, sun: 0, led: 0 }
  let n = 0
  let ppfd = 0
  let sun = 0
  let led = 0
  for (const p of grid) {
    if (p.x >= bed.x0 && p.x <= bed.x1 && p.y >= bed.y0 && p.y <= bed.y1) {
      n++
      ppfd += p.ppfd
      sun += p.sunPpfd ?? 0
      led += p.ledPpfd ?? 0
    }
  }
  if (!n) return { ppfd: 0, sun: 0, led: 0 }
  return { ppfd: ppfd / n, sun: sun / n, led: led / n }
}
