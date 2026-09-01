import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import {
  BEDS,
  BAY_SINGLE_CROP,
  CROP_META,
  bedHasL1Tier,
  bedLabelAnchor,
  cropLabel,
} from './cropCatalog'
import { makeAccentLabelSprite } from './labelSprite'
import { layoutX } from './layoutCoords'
import type { GhRecipe } from '../api/greenhouse'

export type ZoneCropInput = {
  zoneId: string
  recipeId?: string
  recipe?: GhRecipe
}

export type GhAssetPack = {
  shell: THREE.Object3D | null
  bed: THREE.Object3D | null
  lamp: THREE.Object3D | null
  ready: boolean
  source: 'glb' | 'none'
  aesthetic: string
}

const BASE = '/models/cq-demo-bay'
let cache: Promise<GhAssetPack> | null = null

/** 方案 A：扁平 stylized 材质后处理 */
function stylizeMaterial(m: THREE.Material, meshName: string) {
  const std = m as THREE.MeshStandardMaterial & THREE.MeshPhysicalMaterial
  if (!('roughness' in std)) return
  const n = meshName.toLowerCase()
  const isGlass = n.includes('cover') || n.includes('wall') || n.includes('glass') || (std.transmission ?? 0) > 0
  const isGlow = n.includes('lamp') && n.includes('lens')

  std.flatShading = true
  if (isGlass) {
    std.roughness = 0.12
    std.metalness = 0
    std.envMapIntensity = 0.35
  } else if (isGlow) {
    std.envMapIntensity = 0.2
  } else {
    std.roughness = Math.min(0.92, (std.roughness ?? 0.8) + 0.08)
    std.metalness = (std.metalness ?? 0) * 0.65
    std.envMapIntensity = 0.45
  }
}

function prepare(root: THREE.Object3D) {
  root.traverse((obj) => {
    const mesh = obj as THREE.Mesh
    if (!mesh.isMesh || !mesh.material) return
    mesh.castShadow = false
    mesh.receiveShadow = true
    const name = (mesh.name || obj.name || '').toLowerCase()
    const isShell =
      name.includes('shell') ||
      name.includes('tunnel') ||
      name.includes('cover') ||
      name.includes('arch') ||
      name.includes('end-')
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    for (const mat of mats) {
      stylizeMaterial(mat, name)
      const m = mat as THREE.MeshStandardMaterial & THREE.MeshPhysicalMaterial
      const trans = m.transmission ?? 0
      if (m.transparent || trans > 0) {
        m.depthWrite = false
        if (isShell) m.side = THREE.FrontSide
      }
      if (isShell) {
        mesh.renderOrder = 2
        m.polygonOffset = true
        m.polygonOffsetFactor = 2
        m.polygonOffsetUnits = 2
      } else if (name.includes('bed') || name.includes('orchid') || name.includes('pot') || name.includes('leaf')) {
        mesh.renderOrder = 3
        m.polygonOffset = true
        m.polygonOffsetFactor = 1
        m.polygonOffsetUnits = 1
      }
    }
  })
  return root
}

async function loadOne(loader: GLTFLoader, file: string): Promise<THREE.Object3D | null> {
  try {
    const gltf = await loader.loadAsync(`${BASE}/${file}`)
    return prepare(gltf.scene)
  } catch {
    return null
  }
}

export function loadGreenhouseAssets(): Promise<GhAssetPack> {
  if (!cache) {
    cache = (async () => {
      const loader = new GLTFLoader()
      const [shell, bed, lamp] = await Promise.all([
        loadOne(loader, 'tunnel-shell.glb'),
        loadOne(loader, CROP_META[BAY_SINGLE_CROP].glb),
        loadOne(loader, 'lamp-bar.glb'),
      ])
      const ready = !!(shell && bed)
      return {
        shell,
        bed,
        lamp,
        ready,
        source: ready ? 'glb' : 'none',
        aesthetic: 'stylized-ag-tech-a',
      }
    })()
  }
  return cache
}

function accentCss(): string {
  return `#${CROP_META[BAY_SINGLE_CROP].color.toString(16).padStart(6, '0')}`
}

export function placeGlbStructure(
  assets: GhAssetPack,
  parent: THREE.Group,
  zoneCrops: ZoneCropInput[],
): boolean {
  if (!assets.ready || !assets.shell || !assets.bed) return false
  const byZone = Object.fromEntries(zoneCrops.map((z) => [z.zoneId, z]))

  const shell = assets.shell.clone(true)
  shell.name = 'glb-shell'
  shell.scale.x = -Math.abs(shell.scale.x)
  parent.add(shell)

  for (const bed of BEDS) {
    const zc = byZone[bed.zoneId]
    const info = cropLabel(zc?.recipe, bed.zoneId, zc?.recipeId)

    const unit = assets.bed.clone(true)
    unit.name = bed.bedId

    const l1 = unit.getObjectByName('tier-l1')
    if (l1) l1.visible = bedHasL1Tier(bed.bedId)

    unit.position.set(layoutX(bed.x), 0, bed.z)
    unit.userData.cropBed = {
      bedId: bed.bedId,
      zoneId: bed.zoneId,
      cropKey: info.key,
      cropNameZh: info.nameZh,
      stage: info.stage,
      roleZh: bed.roleZh,
      x0: bed.x0,
      x1: bed.x1,
      y0: bed.y0,
      y1: bed.y1,
    }

    const hit = new THREE.Mesh(
      new THREE.BoxGeometry(bed.x1 - bed.x0, 1.4, bed.y1 - bed.y0),
      new THREE.MeshBasicMaterial({ visible: false }),
    )
    hit.position.set(0, 0.9, 0)
    hit.name = `hit-${bed.bedId}`
    hit.userData.cropBed = unit.userData.cropBed
    unit.add(hit)

    const lab = makeAccentLabelSprite(`${info.nameZh} · ${bed.roleZh}`, accentCss(), 2.15)
    const anchor = bedLabelAnchor(bed)
    lab.position.set(anchor.x - bed.x, anchor.y, anchor.z - bed.z)
    lab.userData.cropBed = unit.userData.cropBed
    lab.userData.bedSign = true
    unit.add(lab)

    parent.add(unit)
  }

  return true
}

export function makeGlbLamp(
  assets: GhAssetPack,
  x: number,
  y: number,
  z: number,
  dim: number,
  isL1 = false,
): THREE.Object3D {
  return placeLedStrip(assets, x, y, z, dim, isL1)
}

/** 重置缓存（热更新调试用） */
export function resetGreenhouseAssetCache() {
  cache = null
}
