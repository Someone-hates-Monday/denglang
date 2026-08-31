import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import {
  BEDS,
  BAY_SINGLE_CROP,
  CROP_META,
  bedHasL1Tier,
  cropLabel,
} from './cropCatalog'
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

function makeCropLabel(text: string, accentHex: string): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 420
  canvas.height = 72
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 420, 72)
  ctx.fillStyle = 'rgba(29,29,31,0.82)'
  roundRect(ctx, 8, 10, 404, 52, 10)
  ctx.fill()
  ctx.fillStyle = accentHex
  ctx.fillRect(8, 10, 6, 52)
  ctx.fillStyle = '#f5f5f7'
  ctx.font = '600 22px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(text, 210, 44)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }),
  )
  spr.scale.set(2.4, 0.42, 1)
  return spr
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
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
  parent.add(shell)

  for (const bed of BEDS) {
    const zc = byZone[bed.zoneId]
    const info = cropLabel(zc?.recipe, bed.zoneId, zc?.recipeId)

    const unit = assets.bed.clone(true)
    unit.name = bed.bedId

    const l1 = unit.getObjectByName('tier-l1')
    if (l1) l1.visible = bedHasL1Tier(bed.bedId)

    unit.position.set(bed.x, 0, bed.z)
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

    const lab = makeCropLabel(`${info.nameZh} · ${bed.roleZh}`, accentCss())
    lab.position.set(0, bedHasL1Tier(bed.bedId) ? 2.35 : 1.75, 0)
    lab.userData.cropBed = unit.userData.cropBed
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
): THREE.Object3D {
  if (assets.lamp) {
    const g = assets.lamp.clone(true)
    g.scale.setScalar(0.58)
    g.position.set(x, z, y)
    g.traverse((obj) => {
      const m = obj as THREE.Mesh
      if (m.isMesh && m.material && !Array.isArray(m.material)) {
        const mat = (m.material as THREE.MeshStandardMaterial).clone()
        if ('emissiveIntensity' in mat) mat.emissiveIntensity = 0.15 + dim * 1.1
        mat.depthWrite = true
        m.renderOrder = 4
        m.material = mat
      }
    })
    return g
  }
  const bar = new THREE.Mesh(
    new THREE.BoxGeometry(0.35, 0.03, 0.08),
    new THREE.MeshStandardMaterial({
      color: 0x252528,
      emissive: 0xffe082,
      emissiveIntensity: 0.2 + dim * 1.2,
      metalness: 0.4,
      roughness: 0.4,
      flatShading: true,
    }),
  )
  bar.position.set(x, z, y)
  return bar
}

/** 重置缓存（热更新调试用） */
export function resetGreenhouseAssetCache() {
  cache = null
}
