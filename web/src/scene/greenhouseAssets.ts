import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import {
  BEDS,
  CROP_META,
  type CropKey,
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
  beds: Partial<Record<CropKey, THREE.Object3D | null>>
  lamp: THREE.Object3D | null
  ready: boolean
  source: 'glb' | 'none'
}

const BASE = '/models/cq-demo-bay'
let cache: Promise<GhAssetPack> | null = null

function prepare(root: THREE.Object3D) {
  root.traverse((obj) => {
    const m = obj as THREE.Mesh
    if (m.isMesh) {
      m.castShadow = false
      m.receiveShadow = true
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
      const [shell, dendrobium, anoectochilus, strawberry, lamp] = await Promise.all([
        loadOne(loader, 'tunnel-shell.glb'),
        loadOne(loader, CROP_META.dendrobium.glb),
        loadOne(loader, CROP_META.anoectochilus.glb),
        loadOne(loader, CROP_META.strawberry.glb),
        loadOne(loader, 'lamp-bar.glb'),
      ])
      const beds: GhAssetPack['beds'] = { dendrobium, anoectochilus, strawberry }
      const ready = !!(shell && dendrobium && anoectochilus && strawberry)
      return { shell, beds, lamp, ready, source: ready ? 'glb' : 'none' }
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

function accentCss(crop: CropKey): string {
  const c = CROP_META[crop].color
  return `#${c.toString(16).padStart(6, '0')}`
}

/**
 * 按各区当前配方挂载作物模型；同种作物共用同一 GLB 模板。
 * 每个床带 userData.cropBed + 不可见 hit 盒，供悬停查询。
 */
export function placeGlbStructure(
  assets: GhAssetPack,
  parent: THREE.Group,
  zoneCrops: ZoneCropInput[],
): boolean {
  if (!assets.ready || !assets.shell) return false
  const byZone = Object.fromEntries(zoneCrops.map((z) => [z.zoneId, z]))

  const shell = assets.shell.clone(true)
  shell.name = 'glb-shell'
  parent.add(shell)

  for (const bed of BEDS) {
    const zc = byZone[bed.zoneId]
    const info = cropLabel(zc?.recipe, bed.zoneId, zc?.recipeId)
    const template = assets.beds[info.key]
    if (!template) return false

    const unit = template.clone(true)
    unit.name = bed.bedId
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
    // 整床碰撞盒（本地坐标，床中心为原点）
    const hit = new THREE.Mesh(
      new THREE.BoxGeometry(bed.x1 - bed.x0, 1.4, bed.y1 - bed.y0),
      new THREE.MeshBasicMaterial({ visible: false }),
    )
    hit.position.set(0, 0.9, 0)
    hit.name = `hit-${bed.bedId}`
    hit.userData.cropBed = unit.userData.cropBed
    unit.add(hit)

    const lab = makeCropLabel(`${info.nameZh} · ${bed.roleZh}`, accentCss(info.key))
    lab.position.set(0, 2.05, 0)
    lab.userData.cropBed = unit.userData.cropBed
    unit.add(lab)

    parent.add(unit)
  }

  const postMat = new THREE.MeshStandardMaterial({ color: 0x6a737c, metalness: 0.4, roughness: 0.45 })
  for (const z of [1.4, 3.5, 5.6]) {
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.4, 8), postMat)
    post.position.set(8, 1.2, z)
    parent.add(post)
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
    g.scale.setScalar(0.55)
    g.position.set(x, z, y)
    g.traverse((obj) => {
      const m = obj as THREE.Mesh
      if (m.isMesh && m.material && !Array.isArray(m.material)) {
        const mat = (m.material as THREE.MeshStandardMaterial).clone()
        if ('emissiveIntensity' in mat) mat.emissiveIntensity = 0.2 + dim * 1.2
        m.material = mat
      }
    })
    return g
  }
  const bar = new THREE.Mesh(
    new THREE.BoxGeometry(0.35, 0.03, 0.08),
    new THREE.MeshStandardMaterial({
      color: 0x1d1d1f,
      emissive: 0xffcc55,
      emissiveIntensity: 0.25 + dim * 1.35,
      metalness: 0.5,
      roughness: 0.35,
    }),
  )
  bar.position.set(x, z, y)
  return bar
}
