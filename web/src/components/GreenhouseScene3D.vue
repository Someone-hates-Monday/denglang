<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { GhEffectiveLight } from '../api/greenhouse'
import {
  BEDS,
  cropLabel,
  sampleBedPpfd,
  type CropKey,
} from '../scene/cropCatalog'
import {
  loadGreenhouseAssets,
  makeGlbLamp,
  placeGlbStructure,
  type GhAssetPack,
  type ZoneCropInput,
} from '../scene/greenhouseAssets'

const props = defineProps<{
  light: GhEffectiveLight | null
  /** 各区实时光场（作物模型 / 悬停气候按区取） */
  zoneLights?: Record<string, GhEffectiveLight | undefined>
  focusZoneId?: string
  shadeOpenA?: number
  shadeOpenB?: number
  showHeat?: boolean
  /** total=日光基底+补光峰；sun=仅自然光；led=仅补光 */
  heatMode?: 'total' | 'sun' | 'led'
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const assetSource = ref<'glb' | 'procedural' | 'loading'>('loading')
let assets: GhAssetPack | null = null
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let heatMesh: THREE.Mesh | null = null
let heatSunMesh: THREE.Mesh | null = null
let heatLedMesh: THREE.Mesh | null = null
let heatBaseMesh: THREE.Mesh | null = null
let shadeClothA: THREE.Mesh | null = null
let shadeClothB: THREE.Mesh | null = null
let sunLight: THREE.DirectionalLight | null = null
let sunArrow: THREE.ArrowHelper | null = null
let sunDisc: THREE.Mesh | null = null
let sunGroup: THREE.Group | null = null
let lampGroup: THREE.Group | null = null
let sensorGroup: THREE.Group | null = null
let structureKey = ''
let raf = 0
let disposed = false
let heatGrid: {
  nx: number
  ny: number
  L: number
  W: number
  baseZ: number
  ppfd: Float32Array
  sun: Float32Array
  led: Float32Array
} | null = null
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()

const hoverPpfd = ref<number | null>(null)
const hoverSun = ref<number | null>(null)
const hoverLed = ref<number | null>(null)
const hoverXY = ref('')

type CropHover = {
  bedId: string
  zoneId: string
  cropNameZh: string
  roleZh: string
  stage: string
  ppfd: number
  sun: number
  led: number
  targetMin: number
  targetMax: number
  temperatureC: number | null
  humidityPct: number | null
}
const hoverCrop = ref<CropHover | null>(null)
const tooltipPos = ref({ x: 0, y: 0 })
let cropHitRoots: THREE.Object3D[] = []

const Z_L0 = 0.55
const Z_L1 = 1.25

function zoneCropInputs(): ZoneCropInput[] {
  const zl = props.zoneLights || {}
  return (['ZONE-A', 'ZONE-B'] as const).map((zoneId) => {
    const el = zl[zoneId]
    return {
      zoneId,
      recipeId: el?.recipeId,
      recipe: el?.recipe,
    }
  })
}

function cropKeyForZone(zoneId: string): CropKey {
  const zc = zoneCropInputs().find((z) => z.zoneId === zoneId)
  return cropLabel(zc?.recipe, zoneId, zc?.recipeId).key
}

const sunHud = computed(() => {
  const el = props.light?.solarElevationDeg
  const az = props.light?.solarAzimuthDeg
  if (el == null || az == null) return '日光：等待仿真…'
  const dir = az < 135 ? '偏东' : az < 225 ? '正南' : '偏西'
  return `太阳 ${Number(el).toFixed(0)}° · ${Number(az).toFixed(0)}° ${dir}`
})

const modelHud = computed(() => {
  if (assetSource.value === 'loading') return '模型：加载中…'
  if (assetSource.value === 'glb') return '模型：GLB 资产 · cq-demo-bay'
  return '模型：程序化回退'
})

const heatMax = computed(() => {
  const g = props.light?.grid || []
  return Math.max(props.light?.recipe?.ppfdHardMax ?? 120, ...g.map((p) => p.ppfd), 1)
})

const heatMin = computed(() => {
  const g = props.light?.grid || []
  if (!g.length) return 0
  return Math.min(...g.map((p) => p.ppfd))
})

const heatMid = computed(() => (heatMin.value + heatMax.value) / 2)

const legendTicks = computed(() => {
  const lo = Math.floor(heatMin.value)
  const hi = Math.ceil(heatMax.value)
  const mid = Math.round(heatMid.value)
  return { lo, mid, hi }
})

const shadeWarn = computed(() => {
  const a = props.shadeOpenA ?? props.light?.shadeOpenPercent ?? 100
  const b = props.shadeOpenB ?? 100
  const open = Math.min(a, b)
  if (open <= 15) return '遮阳几乎全关 · 青色日光层会塌平，尖峰主要是补光'
  if (open <= 40) return `遮阳开度偏低（约 ${open}%）· 自然光被明显削弱`
  return ''
})

/** Viridis-like：对齐 horticulture-lighting-simulator 可读性 */
function ppfdColor(v: number, maxRef: number): [number, number, number] {
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

function makeShadeTexture(): THREE.CanvasTexture {
  const c = document.createElement('canvas')
  c.width = 64
  c.height = 64
  const ctx = c.getContext('2d')!
  ctx.fillStyle = '#1c2420'
  ctx.fillRect(0, 0, 64, 64)
  ctx.strokeStyle = 'rgba(120,140,128,0.55)'
  ctx.lineWidth = 1
  for (let i = 0; i < 64; i += 3) {
    ctx.beginPath()
    ctx.moveTo(i, 0)
    ctx.lineTo(i, 64)
    ctx.stroke()
  }
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.repeat.set(18, 12)
  return tex
}

function makeSoilTexture(): THREE.CanvasTexture {
  const c = document.createElement('canvas')
  c.width = 128
  c.height = 128
  const ctx = c.getContext('2d')!
  ctx.fillStyle = '#6e7a5c'
  ctx.fillRect(0, 0, 128, 128)
  for (let i = 0; i < 800; i++) {
    const g = 90 + Math.random() * 50
    ctx.fillStyle = `rgba(${g * 0.7},${g},${g * 0.55},${0.15 + Math.random() * 0.25})`
    ctx.fillRect(Math.random() * 128, Math.random() * 128, 1 + Math.random() * 2, 1)
  }
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.repeat.set(8, 5)
  return tex
}

function buildScene(el: HTMLDivElement) {
  const w = el.clientWidth || 800
  const h = el.clientHeight || 460
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xd8dde3)
  scene.fog = new THREE.Fog(0xd8dde3, 28, 70)

  camera = new THREE.PerspectiveCamera(38, w / h, 0.1, 140)
  camera.position.set(18, 10, -10)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(w, h)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  el.innerHTML = ''
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.target.set(8, 1.2, 3.5)
  controls.maxPolarAngle = Math.PI * 0.48
  controls.minDistance = 6
  controls.maxDistance = 42

  scene.add(new THREE.AmbientLight(0xffffff, 0.42))
  sunLight = new THREE.DirectionalLight(0xfff2d6, 1.15)
  sunLight.position.set(4, 16, -14)
  scene.add(sunLight)
  scene.add(new THREE.HemisphereLight(0xeef3f8, 0x6a7560, 0.4))

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(56, 36),
    new THREE.MeshStandardMaterial({ map: makeSoilTexture(), roughness: 0.95, metalness: 0 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.01
  scene.add(ground)

  lampGroup = new THREE.Group()
  sensorGroup = new THREE.Group()
  sunGroup = new THREE.Group()
  scene.add(lampGroup)
  scene.add(sensorGroup)
  scene.add(sunGroup)

  const loop = () => {
    if (disposed) return
    raf = requestAnimationFrame(loop)
    controls?.update()
    if (renderer && scene && camera) renderer.render(scene, camera)
  }
  loop()
}

function makeLabelSprite(text: string, scaleX = 2.8): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 56
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 320, 56)
  ctx.fillStyle = 'rgba(29,29,31,0.72)'
  roundRect(ctx, 8, 8, 304, 40, 8)
  ctx.fill()
  ctx.fillStyle = '#f5f5f7'
  ctx.font = '600 18px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(text, 160, 35)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }),
  )
  spr.scale.set(scaleX, 0.55, 1)
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

function archCurve(x: number, W: number, G: number, H: number) {
  return new THREE.CatmullRomCurve3([
    new THREE.Vector3(x, 0.04, 0),
    new THREE.Vector3(x, G * 0.55, W * 0.08),
    new THREE.Vector3(x, G, W * 0.22),
    new THREE.Vector3(x, H, W * 0.5),
    new THREE.Vector3(x, G, W * 0.78),
    new THREE.Vector3(x, G * 0.55, W * 0.92),
    new THREE.Vector3(x, 0.04, W),
  ])
}

function addTunnelSkin(group: THREE.Group, L: number, W: number, G: number, H: number) {
  const segsX = 24
  const segsA = 28
  const positions: number[] = []
  const indices: number[] = []
  for (let ix = 0; ix <= segsX; ix++) {
    const x = (ix / segsX) * L
    const pts = archCurve(x, W, G, H).getPoints(segsA)
    for (const p of pts) {
      positions.push(p.x, p.y, p.z)
    }
  }
  const row = segsA + 1
  for (let ix = 0; ix < segsX; ix++) {
    for (let ia = 0; ia < segsA; ia++) {
      const a = ix * row + ia
      const b = a + row
      indices.push(a, b, a + 1, b, b + 1, a + 1)
    }
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setIndex(indices)
  geo.computeVertexNormals()

  const skin = new THREE.Mesh(
    geo,
    new THREE.MeshPhysicalMaterial({
      color: 0xeaf3f0,
      transparent: true,
      opacity: 0.2,
      transmission: 0.65,
      thickness: 0.35,
      roughness: 0.22,
      metalness: 0,
      side: THREE.DoubleSide,
      depthWrite: false,
    }),
  )
  group.add(skin)
}

function addStackedCrops(bed: { x0: number; x1: number; y0: number; y1: number }, group: THREE.Group) {
  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88 })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.45, roughness: 0.4 })
  const potMat = new THREE.MeshStandardMaterial({ color: 0x6b4e3a, roughness: 0.82 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f8a58, roughness: 0.55 })
  const leafDark = new THREE.MeshStandardMaterial({ color: 0x2d6a44, roughness: 0.6 })
  const trayMat = new THREE.MeshStandardMaterial({ color: 0xc5d8cc, roughness: 0.7 })
  const bw = bed.x1 - bed.x0
  const bd = bed.y1 - bed.y0
  const cx = (bed.x0 + bed.x1) / 2
  const cz = (bed.y0 + bed.y1) / 2

  const deck0 = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.06, bd), bedMat)
  deck0.position.set(cx, Z_L0, cz)
  group.add(deck0)

  const rail = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.04, 0.04, 0.04), legMat)
  rail.position.set(cx, Z_L0 + 0.05, bed.y0 + 0.02)
  group.add(rail)

  for (const [ox, oz] of [
    [-bw / 2 + 0.08, -bd / 2 + 0.08],
    [bw / 2 - 0.08, -bd / 2 + 0.08],
    [-bw / 2 + 0.08, bd / 2 - 0.08],
    [bw / 2 - 0.08, bd / 2 - 0.08],
  ] as const) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.025, Z_L1, 8), legMat)
    leg.position.set(cx + ox, Z_L1 / 2, cz + oz)
    group.add(leg)
  }

  for (let x = bed.x0 + 0.28; x < bed.x1 - 0.15; x += 0.38) {
    for (const row of [0.28, 0.5, 0.72]) {
      const z = bed.y0 + bd * row
      const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.055, 0.07, 0.1, 10), potMat)
      pot.position.set(x, Z_L0 + 0.08, z)
      group.add(pot)
      const h = 0.18 + ((x * 10 + z) % 7) * 0.012
      const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.08, h, 7), (x + z) % 1.1 > 0.5 ? leafMat : leafDark)
      leaf.position.set(x, Z_L0 + 0.12 + h / 2, z)
      leaf.rotation.y = (x * 3) % 1
      group.add(leaf)
    }
  }

  const deck1 = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.18, 0.045, bd - 0.1), bedMat)
  deck1.position.set(cx, Z_L1, cz)
  group.add(deck1)
  for (let i = 0; i < 9; i++) {
    const tray = new THREE.Mesh(new THREE.BoxGeometry(0.38, 0.04, 0.26), trayMat)
    tray.position.set(bed.x0 + 0.55 + i * 0.72, Z_L1 + 0.05, cz)
    group.add(tray)
    for (let k = 0; k < 3; k++) {
      const sprout = new THREE.Mesh(new THREE.SphereGeometry(0.035, 8, 8), leafMat)
      sprout.position.set(bed.x0 + 0.45 + i * 0.72 + k * 0.1, Z_L1 + 0.11, cz + (k - 1) * 0.05)
      group.add(sprout)
    }
  }
}

function addMatBed(bed: { x0: number; x1: number; y0: number; y1: number }, group: THREE.Group) {
  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88 })
  const soilMat = new THREE.MeshStandardMaterial({ color: 0x3d4f38, roughness: 0.9 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3d9a5c, roughness: 0.5 })
  const leafDark = new THREE.MeshStandardMaterial({ color: 0x2a7044, roughness: 0.55 })
  const berryMat = new THREE.MeshStandardMaterial({ color: 0xd94a4a, roughness: 0.45, emissive: 0x401010, emissiveIntensity: 0.15 })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.45, roughness: 0.4 })
  const bw = bed.x1 - bed.x0
  const bd = bed.y1 - bed.y0
  const cx = (bed.x0 + bed.x1) / 2
  const cz = (bed.y0 + bed.y1) / 2
  const deck = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.08, bd), bedMat)
  deck.position.set(cx, Z_L0, cz)
  group.add(deck)
  for (const [ox, oz] of [
    [-bw / 2 + 0.08, -bd / 2 + 0.08],
    [bw / 2 - 0.08, -bd / 2 + 0.08],
    [-bw / 2 + 0.08, bd / 2 - 0.08],
    [bw / 2 - 0.08, bd / 2 - 0.08],
  ] as const) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.025, Z_L0, 8), legMat)
    leg.position.set(cx + ox, Z_L0 / 2, cz + oz)
    group.add(leg)
  }
  // 栽培槽（金线莲密植 / 草莓高架槽观感）
  const trough = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.1, 0.14, bd - 0.08), soilMat)
  trough.position.set(cx, Z_L0 + 0.1, cz)
  group.add(trough)
  let n = 0
  for (let x = bed.x0 + 0.25; x < bed.x1 - 0.15; x += 0.28) {
    for (let z = bed.y0 + 0.16; z < bed.y1 - 0.12; z += 0.2) {
      n++
      const h = 0.14 + (n % 5) * 0.02
      const tuft = new THREE.Mesh(new THREE.ConeGeometry(0.07, h, 7), n % 2 ? leafMat : leafDark)
      tuft.position.set(x, Z_L0 + 0.18 + h / 2, z)
      tuft.rotation.y = (n * 0.7) % 2
      group.add(tuft)
      if (n % 4 === 0) {
        const berry = new THREE.Mesh(new THREE.SphereGeometry(0.028, 8, 8), berryMat)
        berry.position.set(x + 0.04, Z_L0 + 0.22 + h * 0.4, z)
        group.add(berry)
      }
    }
  }
}

function addAnoectochilusBed(bed: { x0: number; x1: number; y0: number; y1: number }, group: THREE.Group) {
  const bedMat = new THREE.MeshStandardMaterial({ color: 0x3d3830, roughness: 0.9 })
  const mossMat = new THREE.MeshStandardMaterial({ color: 0x1e4a32, roughness: 0.92 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x2a6b45, roughness: 0.55 })
  const veinMat = new THREE.MeshStandardMaterial({
    color: 0xc9a227,
    emissive: 0x8a7010,
    emissiveIntensity: 0.2,
    roughness: 0.4,
  })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.45, roughness: 0.4 })
  const bw = bed.x1 - bed.x0
  const bd = bed.y1 - bed.y0
  const cx = (bed.x0 + bed.x1) / 2
  const cz = (bed.y0 + bed.y1) / 2
  const deck = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.07, bd), bedMat)
  deck.position.set(cx, Z_L0, cz)
  group.add(deck)
  for (const [ox, oz] of [
    [-bw / 2 + 0.08, -bd / 2 + 0.08],
    [bw / 2 - 0.08, -bd / 2 + 0.08],
    [-bw / 2 + 0.08, bd / 2 - 0.08],
    [bw / 2 - 0.08, bd / 2 - 0.08],
  ] as const) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.025, Z_L0, 8), legMat)
    leg.position.set(cx + ox, Z_L0 / 2, cz + oz)
    group.add(leg)
  }
  const pad = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.1, 0.05, bd - 0.08), mossMat)
  pad.position.set(cx, Z_L0 + 0.06, cz)
  group.add(pad)
  let n = 0
  for (let x = bed.x0 + 0.22; x < bed.x1 - 0.15; x += 0.22) {
    for (let z = bed.y0 + 0.14; z < bed.y1 - 0.1; z += 0.16) {
      n++
      const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.055, 8, 6), leafMat)
      leaf.scale.set(1.2, 0.35, 0.9)
      leaf.position.set(x, Z_L0 + 0.12, z)
      group.add(leaf)
      if (n % 3 === 0) {
        const vein = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.01, 0.015), veinMat)
        vein.position.set(x, Z_L0 + 0.14, z)
        vein.rotation.y = (n * 0.4) % 1.5
        group.add(vein)
      }
    }
  }
}

function attachCropHit(
  group: THREE.Group,
  bed: (typeof BEDS)[number],
  info: { key: CropKey; nameZh: string; stage: string },
) {
  const meta = {
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
  hit.position.set((bed.x0 + bed.x1) / 2, 0.9, (bed.y0 + bed.y1) / 2)
  hit.name = `hit-${bed.bedId}`
  hit.userData.cropBed = meta
  group.add(hit)
  cropHitRoots.push(hit)

  const canvas = document.createElement('canvas')
  canvas.width = 420
  canvas.height = 72
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 420, 72)
  ctx.fillStyle = 'rgba(29,29,31,0.82)'
  ctx.beginPath()
  ctx.moveTo(18, 10)
  ctx.arcTo(412, 10, 412, 62, 10)
  ctx.arcTo(412, 62, 8, 62, 10)
  ctx.arcTo(8, 62, 8, 10, 10)
  ctx.arcTo(8, 10, 412, 10, 10)
  ctx.closePath()
  ctx.fill()
  const accent = info.key === 'dendrobium' ? '#34c759' : info.key === 'strawberry' ? '#ff3b30' : '#0071e3'
  ctx.fillStyle = accent
  ctx.fillRect(8, 10, 6, 52)
  ctx.fillStyle = '#f5f5f7'
  ctx.font = '600 22px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(`${info.nameZh} · ${bed.roleZh}`, 210, 44)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }),
  )
  spr.scale.set(3.2, 0.55, 1)
  spr.position.set((bed.x0 + bed.x1) / 2, 2.05, (bed.y0 + bed.y1) / 2)
  spr.userData.cropBed = meta
  group.add(spr)
}

function rebuildStructure(light: GhEffectiveLight) {
  if (!scene) return
  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7
  const H = Number(light.ridgeHeightM) || 3.8
  const G = Number(light.gutterHeightM) || 2.8
  const crops = zoneCropInputs()
  const cropSig = crops.map((c) => `${c.zoneId}:${cropLabel(c.recipe, c.zoneId, c.recipeId).key}`).join('|')
  const mode = assets?.ready ? 'glb' : 'proc'
  const key = `${L}x${W}x${H}-v1.8-${mode}-${cropSig}`
  if (key === structureKey) return
  structureKey = key
  cropHitRoots = []

  scene.children
    .filter((c) => c.userData.structure)
    .forEach((c) => {
      scene!.remove(c)
      c.traverse((obj) => {
        if (obj instanceof THREE.Mesh) {
          obj.geometry.dispose()
          const m = obj.material
          if (Array.isArray(m)) m.forEach((x) => x.dispose())
          else m.dispose()
        }
      })
    })

  const group = new THREE.Group()
  group.userData.structure = true

  const usedGlb = !!(assets && placeGlbStructure(assets, group, crops))
  assetSource.value = usedGlb ? 'glb' : 'procedural'

  if (!usedGlb) {
    buildProceduralStructure(group, L, W, H, G, crops)
  } else {
    group.traverse((obj) => {
      if (obj.userData?.cropBed && obj.name?.startsWith?.('hit-')) cropHitRoots.push(obj)
    })
  }

  const nameA = cropLabel(crops.find((c) => c.zoneId === 'ZONE-A')?.recipe, 'ZONE-A', crops.find((c) => c.zoneId === 'ZONE-A')?.recipeId).nameZh
  const nameB = cropLabel(crops.find((c) => c.zoneId === 'ZONE-B')?.recipe, 'ZONE-B', crops.find((c) => c.zoneId === 'ZONE-B')?.recipeId).nameZh

  const mark = (x: number, label: string, color: number) => {
    const m = new THREE.Mesh(
      new THREE.BoxGeometry(6.8, 0.04, 0.35),
      new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.25 }),
    )
    m.position.set(x, 0.04, 0.45)
    group.add(m)
    const s = makeLabelSprite(label, 3.2)
    s.position.set(x, 1.85, 3.5)
    group.add(s)
  }
  mark(4, `西半跨 A · ${nameA}`, 0x34c759)
  mark(12, `东半跨 B · ${nameB}`, cropKeyForZone('ZONE-B') === 'strawberry' ? 0xff3b30 : 0x0071e3)

  for (const [cx, t] of [
    [4, '外遮阳 · A'],
    [12, '外遮阳 · B'],
  ] as const) {
    if (!usedGlb) {
      const box = new THREE.Mesh(
        new THREE.BoxGeometry(7.2, 0.14, 0.18),
        new THREE.MeshStandardMaterial({ color: 0x2c3338, metalness: 0.4, roughness: 0.45 }),
      )
      box.position.set(cx, 3.52, W - 0.1)
      group.add(box)
    }
    const s = makeLabelSprite(t, 2.6)
    s.position.set(cx, 3.78, W - 0.1)
    group.add(s)
  }

  for (const [t, p] of [
    ['南 · 日光', [L / 2, 0.55, -0.85]],
    [`A · ${nameA}`, [4, 2.35, -0.55]],
    [`B · ${nameB}`, [12, 2.35, -0.55]],
  ] as const) {
    const s = makeLabelSprite(t, 2.9)
    s.position.set(p[0], p[1], p[2])
    group.add(s)
  }

  scene.add(group)
  controls!.target.set(L / 2, 1.15, W / 2)
  camera?.position.set(L * 1.05, H * 1.85, -W * 1.15)
}

function buildProceduralStructure(
  group: THREE.Group,
  L: number,
  W: number,
  H: number,
  G: number,
  crops: ZoneCropInput[],
) {
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x5a6570, metalness: 0.55, roughness: 0.32 })
  const bayCount = 11
  for (let i = 0; i < bayCount; i++) {
    const x = (i / (bayCount - 1)) * L
    const tube = new THREE.Mesh(new THREE.TubeGeometry(archCurve(x, W, G, H), 40, 0.048, 10, false), frameMat)
    group.add(tube)
  }

  const ridge = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, L, 8), frameMat)
  ridge.rotation.z = Math.PI / 2
  ridge.position.set(L / 2, H, W / 2)
  group.add(ridge)
  for (const z of [0.08, W - 0.08]) {
    const gutter = new THREE.Mesh(new THREE.BoxGeometry(L, 0.08, 0.12), frameMat)
    gutter.position.set(L / 2, G * 0.15, z)
    group.add(gutter)
  }

  addTunnelSkin(group, L, W, G, H)

  const endMat = new THREE.MeshPhysicalMaterial({
    color: 0xe8f0ec,
    transparent: true,
    opacity: 0.22,
    transmission: 0.5,
    roughness: 0.2,
    side: THREE.DoubleSide,
  })
  for (const x of [0.05, L - 0.05]) {
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.96, G * 0.92), endMat)
    wall.position.set(x, G * 0.46, W / 2)
    wall.rotation.y = Math.PI / 2
    group.add(wall)
  }
  const door = new THREE.Mesh(
    new THREE.BoxGeometry(0.08, 1.9, 0.95),
    new THREE.MeshStandardMaterial({ color: 0x5a6570, metalness: 0.3, roughness: 0.5 }),
  )
  door.position.set(0.08, 0.95, W / 2)
  group.add(door)

  for (const bed of BEDS) {
    const zc = crops.find((c) => c.zoneId === bed.zoneId)
    const info = cropLabel(zc?.recipe, bed.zoneId, zc?.recipeId)
    if (info.key === 'dendrobium') addStackedCrops(bed, group)
    else if (info.key === 'strawberry') addMatBed(bed, group)
    else addAnoectochilusBed(bed, group)
    attachCropHit(group, bed, info)
  }

  const postMat = new THREE.MeshStandardMaterial({ color: 0x6a737c, metalness: 0.4, roughness: 0.45 })
  for (const z of [1.4, 3.5, 5.6]) {
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.4, 8), postMat)
    post.position.set(8, 1.2, z)
    group.add(post)
  }

  const aisle = new THREE.Mesh(
    new THREE.BoxGeometry(0.95, 0.025, W * 0.88),
    new THREE.MeshStandardMaterial({ color: 0x9aa394, roughness: 0.9 }),
  )
  aisle.position.set(8, 0.015, W / 2)
  group.add(aisle)

  const tintA = new THREE.Mesh(
    new THREE.PlaneGeometry(7, W * 0.9),
    new THREE.MeshBasicMaterial({ color: 0x7eb89a, transparent: true, opacity: 0.08 }),
  )
  tintA.rotation.x = -Math.PI / 2
  tintA.position.set(4, 0.02, W / 2)
  group.add(tintA)
  const tintB = new THREE.Mesh(
    new THREE.PlaneGeometry(7, W * 0.9),
    new THREE.MeshBasicMaterial({
      color: cropKeyForZone('ZONE-B') === 'strawberry' ? 0xc87a7a : 0x7a9ec8,
      transparent: true,
      opacity: 0.08,
    }),
  )
  tintB.rotation.x = -Math.PI / 2
  tintB.position.set(12, 0.02, W / 2)
  group.add(tintB)
}

function updateSun(light: GhEffectiveLight) {
  if (!scene || !sunGroup || !sunLight) return
  while (sunGroup.children.length) sunGroup.remove(sunGroup.children[0])
  if (sunArrow) {
    scene.remove(sunArrow)
    sunArrow = null
  }
  if (sunDisc) {
    scene.remove(sunDisc)
    sunDisc.geometry.dispose()
    ;(sunDisc.material as THREE.Material).dispose()
    sunDisc = null
  }

  const elev = Number(light.solarElevationDeg ?? 0)
  const az = Number(light.solarAzimuthDeg ?? 180)
  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7
  const elevR = (elev * Math.PI) / 180
  const azR = (az * Math.PI) / 180
  // 从棚中心指向太阳的单位向量（北顺时针方位 + 高度角）
  const toSunX = Math.sin(azR) * Math.cos(elevR)
  const toSunY = Math.sin(elevR)
  const toSunZ = Math.cos(azR) * Math.cos(elevR)
  const dist = 18
  const cx = L / 2
  const cz = W / 2
  // DirectionalLight：光线从 position 射向 target——太阳应在 toSun 一侧（此前误用了反向）
  sunLight.position.set(cx + toSunX * dist, Math.max(1.2, toSunY * dist), cz + toSunZ * dist)
  sunLight.target.position.set(cx, 0.9, cz)
  if (!sunLight.target.parent) scene.add(sunLight.target)
  sunLight.intensity = elev > 2 ? 0.45 + (elev / 90) * 1.15 : 0.06
  sunLight.color.set(elev > 15 ? 0xfff1c8 : 0xb8c4d8)

  if (elev > 1) {
    const sunPos = new THREE.Vector3(cx + toSunX * 12, Math.max(2, toSunY * 12), cz + toSunZ * 12)
    // 箭头表示光线传播方向：太阳 → 冠层
    const rayDir = new THREE.Vector3(-toSunX, -toSunY, -toSunZ).normalize()
    sunArrow = new THREE.ArrowHelper(rayDir, sunPos, 8, 0xffb020, 0.5, 0.32)
    scene.add(sunArrow)
    sunDisc = new THREE.Mesh(
      new THREE.SphereGeometry(0.35, 16, 16),
      new THREE.MeshBasicMaterial({ color: 0xffcc44 }),
    )
    sunDisc.position.copy(sunPos)
    scene.add(sunDisc)
    const lab = makeLabelSprite(`日光入射 ${elev.toFixed(0)}°`, 3.0)
    lab.position.copy(sunPos).add(new THREE.Vector3(0, 0.7, 0))
    sunGroup.add(lab)
  }
}

function updateShadeRoll(mesh: THREE.Mesh | null, xCenter: number, span: number, W: number, closed: number) {
  if (!scene) return mesh
  if (!mesh) {
    mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(span, W * 0.88),
      new THREE.MeshStandardMaterial({
        map: makeShadeTexture(),
        transparent: true,
        opacity: 0.55,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    )
    mesh.rotation.x = -Math.PI / 2
    scene.add(mesh)
  }
  const depth = Math.max(0.12, W * 0.88 * Math.max(0.05, closed))
  mesh.scale.set(1, 1, Math.max(0.05, closed))
  mesh.position.set(xCenter, 3.4, W - 0.12 - depth / 2)
  // 遮阳越关越不透明，肉眼能看出「挡光」
  ;(mesh.material as THREE.MeshStandardMaterial).opacity = 0.15 + closed * 0.75
  return mesh
}

function sampleGridCells(
  light: GhEffectiveLight,
  nx: number,
  ny: number,
  L: number,
  W: number,
): { ppfd: Float32Array; sun: Float32Array; led: Float32Array } {
  const ppfd = new Float32Array(nx * ny)
  const sun = new Float32Array(nx * ny)
  const led = new Float32Array(nx * ny)
  const grid = light.grid || []
  if (grid.length && grid[0].x != null) {
    for (const p of grid) {
      const ix = Math.min(nx - 1, Math.max(0, Math.floor(((p.x - 0.25) / (L - 0.5)) * nx)))
      const iy = Math.min(ny - 1, Math.max(0, Math.floor(((p.y - 0.25) / (W - 0.5)) * ny)))
      const i = iy * nx + ix
      ppfd[i] = p.ppfd
      sun[i] = p.sunPpfd ?? 0
      led[i] = p.ledPpfd ?? 0
    }
  } else {
    for (let i = 0; i < Math.min(grid.length, nx * ny); i++) {
      ppfd[i] = grid[i]?.ppfd ?? 0
      sun[i] = grid[i]?.sunPpfd ?? 0
      led[i] = grid[i]?.ledPpfd ?? 0
    }
  }
  return { ppfd, sun, led }
}

function sunColor(v: number, maxRef: number): [number, number, number] {
  const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
  // 日光：深蓝 → 天蓝 → 浅金
  return [
    Math.round(30 + t * 180),
    Math.round(80 + t * 140),
    Math.round(160 + t * 40),
  ]
}

function ledColor(v: number, maxRef: number): [number, number, number] {
  const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
  // 补光：暗底 → 琥珀 → 亮黄
  return [
    Math.round(40 + t * 215),
    Math.round(20 + t * 180),
    Math.round(10 + t * 30),
  ]
}

function buildHeatGeometry(
  nx: number,
  ny: number,
  L: number,
  W: number,
  baseZ: number,
  values: Float32Array,
  maxRef: number,
  amp: number,
  colorFn: (v: number, maxRef: number) => [number, number, number],
): THREE.BufferGeometry {
  const positions: number[] = []
  const colors: number[] = []
  const indices: number[] = []
  for (let iy = 0; iy < ny; iy++) {
    for (let ix = 0; ix < nx; ix++) {
      const v = values[iy * nx + ix] ?? 0
      const t = Math.max(0, Math.min(1, v / Math.max(maxRef, 1)))
      const x = (ix / Math.max(1, nx - 1)) * L
      const z = (iy / Math.max(1, ny - 1)) * W
      const y = baseZ + t * amp
      positions.push(x, y, z)
      const [r, g, b] = colorFn(v, maxRef)
      colors.push(r / 255, g / 255, b / 255)
    }
  }
  for (let iy = 0; iy < ny - 1; iy++) {
    for (let ix = 0; ix < nx - 1; ix++) {
      const a = iy * nx + ix
      const b = a + 1
      const c = a + nx
      const d = c + 1
      indices.push(a, c, b, b, c, d)
    }
  }
  const geo = new THREE.BufferGeometry()
  geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  geo.setIndex(indices)
  geo.computeVertexNormals()
  return geo
}

function disposeMesh(mesh: THREE.Mesh | null) {
  if (!mesh || !scene) return null
  scene.remove(mesh)
  mesh.geometry.dispose()
  ;(mesh.material as THREE.Material).dispose()
  return null
}

function makeHeatLayer(
  geo: THREE.BufferGeometry,
  opacity: number,
  renderOrder: number,
): THREE.Mesh {
  const mesh = new THREE.Mesh(
    geo,
    new THREE.MeshStandardMaterial({
      vertexColors: true,
      transparent: true,
      opacity,
      side: THREE.DoubleSide,
      metalness: 0.05,
      roughness: 0.7,
      depthWrite: false,
    }),
  )
  mesh.renderOrder = renderOrder
  return mesh
}

function makeSensorLabel(text: string): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 72
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 256, 72)
  ctx.fillStyle = 'rgba(29,29,31,0.82)'
  roundRect(ctx, 8, 8, 240, 56, 10)
  ctx.fill()
  ctx.fillStyle = '#34c759'
  ctx.font = '700 22px ui-monospace, monospace'
  ctx.textAlign = 'center'
  ctx.fillText(text, 128, 44)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({ map: new THREE.CanvasTexture(canvas), transparent: true, depthTest: false }),
  )
  spr.scale.set(1.4, 0.4, 1)
  return spr
}

function updateHeatmap(light: GhEffectiveLight) {
  if (!scene) return
  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7
  const nx = light.nx || 32
  const ny = light.ny || 14
  const visible = props.showHeat !== false
  const mode = props.heatMode || 'total'
  const baseZ = Number(light.measurePlaneZ) || 0.9
  const cells = sampleGridCells(light, nx, ny, L, W)
  heatGrid = { nx, ny, L, W, baseZ, ppfd: cells.ppfd, sun: cells.sun, led: cells.led }

  const sunMax = Math.max(...cells.sun, 1)
  const ledMax = Math.max(...cells.led, 1)
  const totMax = Math.max(...cells.ppfd, heatMax.value, 1)

  heatMesh = disposeMesh(heatMesh)
  heatSunMesh = disposeMesh(heatSunMesh)
  heatLedMesh = disposeMesh(heatLedMesh)

  if (visible && (light.grid?.length ?? 0) > 0) {
    if (mode === 'sun' || mode === 'total') {
      // 日光：宽缓起伏（南北梯度），青色系 —— 遮阳一关这层会塌下去
      const sunGeo = buildHeatGeometry(nx, ny, L, W, baseZ, cells.sun, Math.max(sunMax, 40), 0.7, sunColor)
      heatSunMesh = makeHeatLayer(sunGeo, mode === 'sun' ? 0.88 : 0.55, 3)
      scene.add(heatSunMesh)
    }
    if (mode === 'led' || mode === 'total') {
      // 补光：局部尖峰，琥珀系 —— 调光升降峰高
      const ledGeo = buildHeatGeometry(
        nx,
        ny,
        L,
        W,
        baseZ + (mode === 'total' ? 0.02 : 0),
        cells.led,
        Math.max(ledMax, 40),
        mode === 'total' ? 0.85 : 0.95,
        ledColor,
      )
      heatLedMesh = makeHeatLayer(ledGeo, mode === 'led' ? 0.9 : 0.72, 5)
      scene.add(heatLedMesh)
    }
    // 悬停用合成面（不可见碰撞）
    const hitGeo = buildHeatGeometry(nx, ny, L, W, baseZ, cells.ppfd, totMax, 0.5, ppfdColor)
    heatMesh = makeHeatLayer(hitGeo, 0.01, 6)
    heatMesh.visible = true
    scene.add(heatMesh)
  }

  if (!heatBaseMesh) {
    heatBaseMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(L * 0.98, W * 0.95),
      new THREE.MeshBasicMaterial({
        color: 0x1d1d1f,
        transparent: true,
        opacity: 0.06,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    )
    heatBaseMesh.rotation.x = -Math.PI / 2
    heatBaseMesh.renderOrder = 2
    scene.add(heatBaseMesh)
  }
  heatBaseMesh.position.set(L / 2, baseZ, W / 2)
  heatBaseMesh.visible = visible

  const closedA = 1 - (props.shadeOpenA ?? light.shadeOpenPercent ?? 100) / 100
  const closedB = 1 - (props.shadeOpenB ?? light.shadeOpenPercent ?? 100) / 100
  shadeClothA = updateShadeRoll(shadeClothA, 4, 7.6, W, closedA)
  shadeClothB = updateShadeRoll(shadeClothB, 12, 7.6, W, closedB)

  if (lampGroup && sensorGroup) {
    while (lampGroup.children.length) lampGroup.remove(lampGroup.children[0])
    while (sensorGroup.children.length) sensorGroup.remove(sensorGroup.children[0])
    const readings = light.sensorPpfd || {}
    for (const d of light.devices || []) {
      if (d.posX == null || d.posY == null) continue
      if (d.deviceType === 'GROW_LAMP') {
        const dim = (d.dimmingPercent ?? 0) / 100
        const z = d.posZ ?? 1.45
        if (assets?.ready) {
          lampGroup.add(makeGlbLamp(assets, d.posX, d.posY, z, dim))
        } else {
          const bar = new THREE.Mesh(
            new THREE.BoxGeometry(0.55, 0.045, 0.12),
            new THREE.MeshStandardMaterial({
              color: 0x1d1d1f,
              emissive: 0xffcc55,
              emissiveIntensity: 0.25 + dim * 1.35,
              metalness: 0.5,
              roughness: 0.35,
            }),
          )
          bar.position.set(d.posX, z, d.posY)
          lampGroup.add(bar)
        }
        if (dim > 0.02) {
          const beamH = Math.max(0.25, z - baseZ)
          const beam = new THREE.Mesh(
            new THREE.ConeGeometry(0.38, beamH, 16, 1, true),
            new THREE.MeshBasicMaterial({
              color: 0xffe08a,
              transparent: true,
              opacity: 0.12 + dim * 0.2,
              side: THREE.DoubleSide,
              depthWrite: false,
            }),
          )
          beam.position.set(d.posX, z - beamH / 2, d.posY)
          beam.rotation.x = Math.PI
          lampGroup.add(beam)
        }
      } else if (d.deviceType === 'PAR_SENSOR') {
        const z = d.posZ ?? baseZ
        const disc = new THREE.Mesh(
          new THREE.CylinderGeometry(0.08, 0.08, 0.03, 14),
          new THREE.MeshStandardMaterial({
            color: 0xf5f5f7,
            emissive: 0x34c759,
            emissiveIntensity: 0.55,
          }),
        )
        disc.position.set(d.posX, z, d.posY)
        sensorGroup.add(disc)
        const val = readings[d.deviceSn] ?? d.lastPpfd
        const label = makeSensorLabel(
          val != null ? `${Number(val).toFixed(0)} µmol` : 'PAR',
        )
        label.position.set(d.posX, z + 0.28, d.posY)
        sensorGroup.add(label)
      }
    }
  }
}

function clearHover() {
  hoverPpfd.value = null
  hoverSun.value = null
  hoverLed.value = null
  hoverXY.value = ''
  hoverCrop.value = null
}

function fillCropHover(
  meta: {
    bedId: string
    zoneId: string
    cropNameZh: string
    roleZh: string
    stage: string
    x0: number
    x1: number
    y0: number
    y1: number
  },
  clientX: number,
  clientY: number,
) {
  const host = hostRef.value
  if (!host) return
  const rect = host.getBoundingClientRect()
  tooltipPos.value = {
    x: Math.min(rect.width - 220, Math.max(12, clientX - rect.left + 14)),
    y: Math.min(rect.height - 160, Math.max(12, clientY - rect.top + 14)),
  }
  const zone = props.zoneLights?.[meta.zoneId]
  const bed = BEDS.find((b) => b.bedId === meta.bedId)
  const sample = bed
    ? sampleBedPpfd(zone?.grid || props.light?.grid, bed)
    : { ppfd: 0, sun: 0, led: 0 }
  hoverCrop.value = {
    bedId: meta.bedId,
    zoneId: meta.zoneId,
    cropNameZh: meta.cropNameZh,
    roleZh: meta.roleZh,
    stage: meta.stage || zone?.recipe?.stage || '—',
    ppfd: sample.ppfd,
    sun: sample.sun,
    led: sample.led,
    targetMin: zone?.recipe?.ppfdTargetMin ?? 0,
    targetMax: zone?.recipe?.ppfdTargetMax ?? 0,
    temperatureC: zone?.temperatureC ?? null,
    humidityPct: zone?.humidityPct ?? null,
  }
  hoverPpfd.value = sample.ppfd
  hoverSun.value = sample.sun
  hoverLed.value = sample.led
  hoverXY.value = `${meta.cropNameZh} · ${meta.roleZh}`
}

function onPointerMove(ev: PointerEvent) {
  const host = hostRef.value
  if (!host || !camera) {
    clearHover()
    return
  }
  const rect = host.getBoundingClientRect()
  pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)

  if (cropHitRoots.length) {
    const cropHits = raycaster.intersectObjects(cropHitRoots, false)
    if (cropHits.length) {
      const meta = cropHits[0].object.userData.cropBed
      if (meta) {
        fillCropHover(meta, ev.clientX, ev.clientY)
        return
      }
    }
  }

  hoverCrop.value = null
  if (!heatMesh || !heatGrid || props.showHeat === false) {
    clearHover()
    return
  }
  const hits = raycaster.intersectObject(heatMesh, false)
  if (!hits.length) {
    clearHover()
    return
  }
  const p = hits[0].point
  const ix = Math.min(
    heatGrid.nx - 1,
    Math.max(0, Math.round((p.x / heatGrid.L) * (heatGrid.nx - 1))),
  )
  const iy = Math.min(
    heatGrid.ny - 1,
    Math.max(0, Math.round((p.z / heatGrid.W) * (heatGrid.ny - 1))),
  )
  const i = iy * heatGrid.nx + ix
  hoverPpfd.value = heatGrid.ppfd[i]
  hoverSun.value = heatGrid.sun[i]
  hoverLed.value = heatGrid.led[i]
  hoverXY.value = `x=${p.x.toFixed(1)}m · y北=${p.z.toFixed(1)}m`
}

function apply(light: GhEffectiveLight | null) {
  if (!light || !scene) return
  rebuildStructure(light)
  updateSun(light)
  updateHeatmap(light)
}

function onResize() {
  const host = hostRef.value
  if (!host || !camera || !renderer) return
  const w = Math.max(host.clientWidth, 1)
  const h = Math.max(host.clientHeight, 1)
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

let ro: ResizeObserver | null = null

onMounted(async () => {
  const host = hostRef.value
  if (!host) return
  buildScene(host)
  host.addEventListener('pointermove', onPointerMove)
  window.addEventListener('resize', onResize)
  ro = new ResizeObserver(() => onResize())
  ro.observe(host)
  requestAnimationFrame(onResize)

  try {
    assets = await loadGreenhouseAssets()
    assetSource.value = assets.ready ? 'glb' : 'procedural'
  } catch {
    assets = null
    assetSource.value = 'procedural'
  }
  if (disposed) return
  structureKey = ''
  apply(props.light)
})

onUnmounted(() => {
  disposed = true
  cancelAnimationFrame(raf)
  hostRef.value?.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('resize', onResize)
  ro?.disconnect()
  controls?.dispose()
  renderer?.dispose()
})

watch(
  () =>
    [
      props.light,
      props.zoneLights,
      props.showHeat,
      props.shadeOpenA,
      props.shadeOpenB,
      props.focusZoneId,
      props.heatMode,
    ] as const,
  () => apply(props.light),
  { deep: true },
)
</script>

<template>
  <div class="wrap">
    <div ref="hostRef" class="scene" aria-label="智慧光棚三维整跨光场" />
    <div
      v-if="hoverCrop"
      class="crop-tip"
      :style="{ left: tooltipPos.x + 'px', top: tooltipPos.y + 'px' }"
    >
      <p class="tip-title">{{ hoverCrop.cropNameZh }} · {{ hoverCrop.roleZh }}</p>
      <p class="tip-sub">{{ hoverCrop.zoneId }} · {{ hoverCrop.stage }}</p>
      <dl class="tip-grid mono">
        <div>
          <dt>此处实况</dt>
          <dd>{{ hoverCrop.ppfd.toFixed(1) }} µmol</dd>
        </div>
        <div>
          <dt>理想光照</dt>
          <dd>{{ hoverCrop.targetMin }}–{{ hoverCrop.targetMax }}</dd>
        </div>
        <div>
          <dt>日光 / 补光</dt>
          <dd>{{ hoverCrop.sun.toFixed(0) }} / {{ hoverCrop.led.toFixed(0) }}</dd>
        </div>
        <div>
          <dt>温度 · 湿度</dt>
          <dd>
            {{ hoverCrop.temperatureC != null ? hoverCrop.temperatureC.toFixed(1) + '°C' : '—' }} ·
            {{ hoverCrop.humidityPct != null ? hoverCrop.humidityPct.toFixed(0) + '%' : '—' }}
          </dd>
        </div>
      </dl>
    </div>
    <aside class="hud">
      <p class="sun">{{ sunHud }}</p>
      <p class="model">{{ modelHud }}</p>
      <p v-if="shadeWarn" class="warn">{{ shadeWarn }}</p>
      <div class="legend-row">
        <span class="chip sun-chip">青=日光</span>
        <span class="chip led-chip">黄=补光</span>
      </div>
      <div class="scale">
        <div class="bar"><i class="grad" /></div>
        <div class="ticks mono">
          <span>{{ legendTicks.lo }}</span>
          <span>{{ legendTicks.mid }}</span>
          <span>{{ legendTicks.hi }}</span>
        </div>
        <p class="unit">µmol·m⁻²·s⁻¹ · 高度∝分量</p>
      </div>
      <p v-if="hoverCrop" class="hover mono">
        {{ hoverCrop.cropNameZh }} · 实况 {{ hoverCrop.ppfd.toFixed(1) }}
        <br />
        <span class="dim"
          >目标 {{ hoverCrop.targetMin }}–{{ hoverCrop.targetMax }} ·
          {{ hoverCrop.temperatureC ?? '—' }}°C · {{ hoverCrop.humidityPct ?? '—' }}%</span
        >
      </p>
      <p v-else-if="hoverPpfd != null" class="hover mono">
        合计 {{ hoverPpfd.toFixed(1) }}
        <span class="dim"
          >（日 {{ hoverSun?.toFixed(1) ?? '—' }} + 灯 {{ hoverLed?.toFixed(1) ?? '—' }}）</span
        >
        <br />
        <span class="dim">{{ hoverXY }}</span>
      </p>
      <p v-else class="hint">悬停作物床读作物与温光湿 · 或悬停光场读格点</p>
    </aside>
  </div>
</template>
<style scoped>
.wrap {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.scene {
  width: 100%;
  flex: 1 1 auto;
  height: 100%;
  min-height: 280px;
  overflow: hidden;
  background: #d8dde3;
  cursor: crosshair;
}
.hud {
  position: absolute;
  left: 0.75rem;
  bottom: 0.75rem;
  max-width: 17rem;
  padding: 0.55rem 0.7rem;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--ink);
  font-size: 0.72rem;
  line-height: 1.4;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  pointer-events: none;
  box-shadow: var(--shadow-sm);
}
.sun {
  margin: 0 0 0.3rem;
  font-family: var(--font-mono);
  font-size: 0.75rem;
}
.model {
  margin: 0 0 0.25rem;
  font-size: 0.7rem;
  color: var(--accent);
  font-weight: 600;
}
.warn {
  margin: 0.25rem 0;
  padding: 0.3rem 0.4rem;
  background: var(--warning-soft, rgba(255, 149, 0, 0.14));
  color: var(--sodium-deep, #c93400);
  border-radius: 6px;
  font-size: 0.68rem;
  line-height: 1.35;
}
.legend-row {
  display: flex;
  gap: 0.35rem;
  margin: 0.25rem 0;
}
.chip {
  font-size: 0.65rem;
  padding: 0.12rem 0.35rem;
  border-radius: 4px;
  font-weight: 600;
}
.sun-chip {
  background: rgba(60, 140, 220, 0.2);
  color: #1a5f9e;
}
.led-chip {
  background: rgba(255, 180, 40, 0.25);
  color: #9a6200;
}
.hint {
  margin: 0.25rem 0 0;
  color: var(--ink-soft);
}
.scale {
  margin: 0.35rem 0;
}
.bar {
  display: flex;
  height: 10px;
  border-radius: 4px;
  overflow: hidden;
}
.bar .grad {
  flex: 1;
  background: linear-gradient(90deg, #440154, #31688e, #35b779, #fde725);
}
.ticks {
  display: flex;
  justify-content: space-between;
  margin-top: 0.2rem;
  font-size: 0.68rem;
  color: var(--ink-soft);
}
.unit {
  margin: 0.15rem 0 0;
  font-size: 0.65rem;
  color: var(--ink-muted);
}
.hover {
  margin: 0.35rem 0 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--ink);
}
.dim {
  font-weight: 400;
  color: var(--ink-soft);
  font-size: 0.68rem;
}
.crop-tip {
  position: absolute;
  z-index: 4;
  min-width: 11.5rem;
  max-width: 14rem;
  padding: 0.55rem 0.7rem;
  background: rgba(29, 29, 31, 0.9);
  color: #f5f5f7;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: var(--shadow-sm);
  pointer-events: none;
  font-size: 0.72rem;
  line-height: 1.35;
}
.tip-title {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
}
.tip-sub {
  margin: 0.1rem 0 0.45rem;
  color: rgba(245, 245, 247, 0.55);
  font-size: 0.65rem;
}
.tip-grid {
  margin: 0;
  display: grid;
  gap: 0.35rem;
}
.tip-grid div {
  display: grid;
  gap: 0.05rem;
}
.tip-grid dt {
  color: rgba(245, 245, 247, 0.5);
  font-size: 0.62rem;
  font-weight: 500;
}
.tip-grid dd {
  margin: 0;
  font-weight: 600;
  font-size: 0.78rem;
}
</style>
