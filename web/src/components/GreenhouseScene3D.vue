<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { GhEffectiveLight } from '../api/greenhouse'
import {
  BEDS,
  bedHasL1Tier,
  cropLabel,
  sampleBedPpfd,
  type CropKey,
} from '../scene/cropCatalog'
import {
  loadGreenhouseAssets,
  placeGlbStructure,
  type GhAssetPack,
  type ZoneCropInput,
} from '../scene/greenhouseAssets'
import { placeLedStrip } from '../scene/ledFixture'
import {
  STATUS_GLOW_HEX,
  statusNeedsGlow,
  type DeviceSceneStatus,
  type SceneStatusTone,
} from '../scene/deviceStatus'
import { makeAccentLabelSprite, makeLabelSprite } from '../scene/labelSprite'
import {
  azimuthLabelZh,
  defaultCameraPose,
  layoutToThree,
  layoutX as lx,
  sunDirectionThree,
  threeXToLayout,
} from '../scene/layoutCoords'
import { unitLedAt } from '../scene/lampOptics'
import {
  HEAT_CHANNEL_LABEL,
  SUN_SHARE,
  channelMonoColor,
  emphasizeLedChannel,
  ledShareForRecipe,
  rgbCompositeColor,
  splitRgb,
  viridisColor,
  xrayColor,
  type HeatChannel,
} from '../scene/spectrumModel'

const props = defineProps<{
  light: GhEffectiveLight | null
  /** 各区实时光场（作物模型 / 悬停气候按区取） */
  zoneLights?: Record<string, GhEffectiveLight | undefined>
  shadeOpenA?: number
  shadeOpenB?: number
  showHeat?: boolean
  /** xray | viridis | rgb | R | G | B */
  heatChannel?: HeatChannel
  /** 设备 SN → 告警/工单/离线等状态（驱动光晕） */
  deviceStatuses?: Record<string, DeviceSceneStatus>
  selectedDeviceSn?: string | null
  /** 状态过滤：all | attention | alarm | wo | offline */
  statusFilter?: 'all' | 'attention' | 'alarm' | 'wo' | 'offline'
}>()

const emit = defineEmits<{
  selectDevice: [payload: { deviceSn: string; zoneId?: string; deviceType?: string }]
  clearDevice: []
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const assetSource = ref<'glb' | 'procedural' | 'loading'>('loading')
/** X 光切片高度（m），滚轮调节 */
const sliceZ = ref(0.92)
const SLICE_Z_MIN = 0.4
const SLICE_Z_MAX = 1.65
const LOD_FAR = 22
const LOD_NEAR = 17
let assets: GhAssetPack | null = null
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let heatMesh: THREE.Mesh | null = null
let heatSunMesh: THREE.Mesh | null = null
let heatLedMesh: THREE.Mesh | null = null
let heatBaseMesh: THREE.Mesh | null = null
let sliceGhost: THREE.Line | null = null
let sliceDragging = false
let sliceDragPointerId: number | null = null
let shadeClothA: THREE.Mesh | null = null
let shadeClothB: THREE.Mesh | null = null
let sunLight: THREE.DirectionalLight | null = null
let ambientLight: THREE.AmbientLight | null = null
/** 棚内工作/月光补光：夜间抬高，保证植株与传感器可读 */
let bayFillLight: THREE.PointLight | null = null
let sunArrow: THREE.ArrowHelper | null = null
let sunDisc: THREE.Mesh | null = null
let hemiLight: THREE.HemisphereLight | null = null
let skyDome: THREE.Mesh | null = null
let sunGroup: THREE.Group | null = null
let lampGroup: THREE.Group | null = null
let sensorGroup: THREE.Group | null = null
let markerGroup: THREE.Group | null = null
let clusterGroup: THREE.Group | null = null
let structureKey = ''
/** 首次建棚后不再强制拉回默认视角，避免轮询打断拖拽 */
let structureCameraReady = false
let deviceLayoutKey = ''
let deviceVisualKey = ''
let heatLayoutKey = ''
let sunKey = ''
/** 缓存上一档昼夜，供仅调补光强度时复用 */
let lastNightBlend = -1
let raf = 0
let hoverRaf = 0
let disposed = false
let lastLight: GhEffectiveLight | null = null
let deviceHitRoots: THREE.Object3D[] = []
let clusterHitRoots: THREE.Object3D[] = []
let statusGlowMats: THREE.MeshBasicMaterial[] = []
let ptrDownX = 0
let ptrDownY = 0
let ptrDownT = 0
/** 拖拽中跳过悬停拾取，保持跟手 */
let orbitDragging = false
/** true=远距床位聚合；false=近距单设备 */
let lodClustered = false
const lodClusteredUi = ref(false)
let lodFrame = 0
let heatGrid: {
  nx: number
  ny: number
  L: number
  W: number
  baseZ: number
  ppfd: Float32Array
  sun: Float32Array
  led: Float32Array
  r: Float32Array
  g: Float32Array
  b: Float32Array
} | null = null
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()

const hoverPpfd = ref<number | null>(null)
const hoverSun = ref<number | null>(null)
const hoverLed = ref<number | null>(null)
const hoverR = ref<number | null>(null)
const hoverG = ref<number | null>(null)
const hoverB = ref<number | null>(null)
const hoverXY = ref('')

const heatChannel = computed(() => props.heatChannel || 'xray')
const channelHud = computed(() => HEAT_CHANNEL_LABEL[heatChannel.value])

type CropHover = {
  bedId: string
  zoneId: string
  cropNameZh: string
  roleZh: string
  stage: string
  ppfd: number
  sun: number
  led: number
  /** 此刻动态目标 */
  targetMin: number
  targetMax: number
  /** 配方静态基带（对照） */
  recipeMin: number
  recipeMax: number
  dliSoFar: number | null
  dliTargetMin: number | null
  dliRemaining: number | null
  vpdKpa: number | null
  noteZh: string
  temperatureC: number | null
  humidityPct: number | null
}
const hoverCrop = ref<CropHover | null>(null)
const tooltipPos = ref({ x: 0, y: 0 })
let cropHitRoots: THREE.Object3D[] = []

type DeviceHover = {
  deviceSn: string
  deviceType?: string
  zoneId?: string
  labelZh: string
  tone: SceneStatusTone
}
const hoverDevice = ref<DeviceHover | null>(null)

const Z_L0 = 0.55
const Z_L1 = 1.25

/** 渲染分层，避免共面 z-fighting */
const RENDER = {
  GROUND: 0,
  FLOOR: 1,
  STRUCTURE: 2,
  CROPS: 3,
  SHADE: 4,
  HEAT: 8,
  GIZMO: 12,
} as const

function tuneMaterial(
  mat: THREE.Material,
  opts: { depthWrite?: boolean; polygonOffset?: number; side?: THREE.Side },
) {
  if (opts.depthWrite !== undefined) mat.depthWrite = opts.depthWrite
  if (opts.side !== undefined) mat.side = opts.side
  if (opts.polygonOffset) {
    mat.polygonOffset = true
    mat.polygonOffsetFactor = opts.polygonOffset
    mat.polygonOffsetUnits = opts.polygonOffset
  }
}

function tuneMesh(
  mesh: THREE.Mesh,
  renderOrder: number,
  opts?: { depthWrite?: boolean; polygonOffset?: number },
) {
  mesh.renderOrder = renderOrder
  const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
  for (const m of mats) tuneMaterial(m, { depthWrite: opts?.depthWrite, polygonOffset: opts?.polygonOffset })
}

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

const sunHud = computed(() => {
  const el = props.light?.solarElevationDeg
  const az = props.light?.solarAzimuthDeg
  if (el == null || az == null) return '日光：等待仿真…'
  return `太阳高度 ${Number(el).toFixed(0)}° · 方位 ${Number(az).toFixed(0)}°（${azimuthLabelZh(Number(az))}，自北顺时针）`
})

const modelHud = computed(() => {
  if (assetSource.value === 'loading') return '模型：加载中…'
  if (assetSource.value === 'glb') {
    const tag = assets?.aesthetic === 'stylized-ag-tech-a' ? '方案 A stylized' : 'GLB'
    return `模型：${tag} · cq-demo-bay`
  }
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
  if (open <= 15) return '遮阳几乎全关 · 切片上偏暗'
  if (open <= 40) return `遮阳开度偏低（约 ${open}%）`
  return ''
})

const sliceHud = computed(() => `${channelHud.value} · 整跨切片 · z=${sliceZ.value.toFixed(2)} m`)

function passesStatusFilter(tone: SceneStatusTone | undefined): boolean {
  const f = props.statusFilter || 'all'
  if (f === 'all') return true
  const t = tone || 'ok'
  if (f === 'attention') return t !== 'ok'
  if (f === 'alarm') return t === 'alarm'
  if (f === 'offline') return t === 'offline'
  if (f === 'wo') return t === 'wo-pending' || t === 'wo-approved' || t === 'wo-progress'
  return true
}

/** 已迁到 spectrumModel；保留别名避免旧引用 */

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
  c.width = 256
  c.height = 256
  const ctx = c.getContext('2d')!
  ctx.fillStyle = '#5f7348'
  ctx.fillRect(0, 0, 256, 256)
  for (let i = 0; i < 900; i++) {
    const x = (i * 47) % 256
    const y = (i * 91) % 256
    const g = 70 + ((i * 13) % 50)
    ctx.fillStyle = `rgb(${50 + (i % 30)},${g},${40 + (i % 20)})`
    ctx.fillRect(x, y, 2 + (i % 3), 2 + (i % 2))
  }
  const tex = new THREE.CanvasTexture(c)
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping
  tex.repeat.set(18, 12)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

function makeSkyTexture(elev = 45): THREE.CanvasTexture {
  const c = document.createElement('canvas')
  c.width = 4
  c.height = 256
  const ctx = c.getContext('2d')!
  const g = ctx.createLinearGradient(0, 0, 0, 256)
  if (elev < 2) {
    g.addColorStop(0, '#152033')
    g.addColorStop(0.55, '#2a3a52')
    g.addColorStop(1, '#4a5568')
  } else if (elev < 18) {
    g.addColorStop(0, '#6a8ec8')
    g.addColorStop(0.45, '#f0b070')
    g.addColorStop(1, '#f5d5a8')
  } else {
    g.addColorStop(0, '#6eb0e8')
    g.addColorStop(0.55, '#b8d8f0')
    g.addColorStop(1, '#e8f2dc')
  }
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 4, 256)
  const tex = new THREE.CanvasTexture(c)
  tex.colorSpace = THREE.SRGBColorSpace
  return tex
}

function rnd(seed: number) {
  const x = Math.sin(seed * 12.9898) * 43758.5453
  return x - Math.floor(x)
}

/** 棚外地景：碎石垫、罗盘、杂草灌木与远树（纯视觉，不进控制） */
function addSiteEnvironment(L: number, W: number, group: THREE.Group) {
  const gravelMat = new THREE.MeshStandardMaterial({
    color: 0x9a9488,
    roughness: 0.96,
    flatShading: true,
  })
  const dirtMat = new THREE.MeshStandardMaterial({
    color: 0x6e5a42,
    roughness: 0.97,
    flatShading: true,
  })
  const grassA = new THREE.MeshStandardMaterial({
    color: 0x52b86a,
    roughness: 0.88,
    flatShading: true,
    polygonOffset: true,
    polygonOffsetFactor: 2,
    polygonOffsetUnits: 2,
  })
  const grassB = new THREE.MeshStandardMaterial({
    color: 0x3d9a58,
    roughness: 0.9,
    flatShading: true,
    polygonOffset: true,
    polygonOffsetFactor: 2,
    polygonOffsetUnits: 2,
  })
  const grassC = new THREE.MeshStandardMaterial({
    color: 0x2f7a48,
    roughness: 0.88,
    flatShading: true,
    polygonOffset: true,
    polygonOffsetFactor: 2,
    polygonOffsetUnits: 2,
  })
  const weedMat = new THREE.MeshStandardMaterial({ color: 0x3d7a40, roughness: 0.82, flatShading: true })
  const shrubMat = new THREE.MeshStandardMaterial({ color: 0x355c30, roughness: 0.78, flatShading: true })
  const trunkMat = new THREE.MeshStandardMaterial({ color: 0x6b5344, roughness: 0.92, flatShading: true })
  const canopyMat = new THREE.MeshStandardMaterial({ color: 0x3d9a58, roughness: 0.72, flatShading: true })
  const flowerMat = new THREE.MeshStandardMaterial({ color: 0xd4a018, roughness: 0.55, flatShading: true })

  // 外圈碎石框（不铺进棚内，避免与 GLB/过道地面共面闪烁）
  const apron = 2.0
  const gravelH = 0.012
  const gravelY = -0.004
  for (const [cx, cz, gw, gd] of [
    [L / 2, -apron / 2, L + apron * 2, apron],
    [L / 2, W + apron / 2, L + apron * 2, apron],
    [-apron / 2, W / 2, apron, W + apron * 2],
    [L + apron / 2, W / 2, apron, W + apron * 2],
  ] as const) {
    const g = new THREE.Mesh(new THREE.BoxGeometry(gw, gravelH, gd), gravelMat)
    g.position.set(lx(cx), gravelY, cz)
    tuneMesh(g, RENDER.GROUND)
    group.add(g)
  }

  const path = new THREE.Mesh(new THREE.BoxGeometry(3.2, 0.012, 6.5), dirtMat)
  path.position.set(lx(L / 2), 0.008, -2.8)
  tuneMesh(path, RENDER.FLOOR, { polygonOffset: 1 })
  group.add(path)

  for (const [t, px, pz, sx] of [
    ['北', L / 2, W + 2.2, 1.6],
    ['南 · 采光', L / 2, -2.2, 2.8],
    ['西', -2.2, W / 2, 1.6],
    ['东', L + 2.2, W / 2, 1.6],
  ] as const) {
    const s = makeLabelSprite(t, sx)
    s.position.set(lx(px), 0.62, pz)
    group.add(s)
  }

  for (let i = 0; i < 14; i++) {
    const side = i % 4
    let x = 0
    let z = 0
    if (side === 0) {
      x = rnd(i * 3) * (L + 10) - 5
      z = -2.5 - rnd(i * 5) * 6
    } else if (side === 1) {
      x = rnd(i * 7) * (L + 10) - 5
      z = W + 2 + rnd(i * 9) * 5
    } else if (side === 2) {
      x = -3 - rnd(i * 11) * 5
      z = rnd(i * 13) * (W + 6) - 2
    } else {
      x = L + 3 + rnd(i * 17) * 5
      z = rnd(i * 19) * (W + 6) - 2
    }
    const patch = new THREE.Mesh(
      new THREE.CircleGeometry(1.2 + rnd(i) * 1.8, 10),
      i % 3 === 0 ? grassA : i % 3 === 1 ? grassB : grassC,
    )
    patch.rotation.x = -Math.PI / 2
    patch.position.set(lx(x), 0.014 + rnd(i * 2) * 0.006, z)
    tuneMesh(patch, RENDER.FLOOR, { polygonOffset: 2 })
    group.add(patch)
  }

  for (let i = 0; i < 70; i++) {
    const edge = i % 4
    let x = 0
    let z = 0
    if (edge === 0) {
      x = rnd(i * 3) * (L + 6) - 3
      z = -1.4 - rnd(i * 5) * 5
    } else if (edge === 1) {
      x = rnd(i * 7) * (L + 6) - 3
      z = W + 1.4 + rnd(i * 9) * 4.5
    } else if (edge === 2) {
      x = -1.5 - rnd(i * 11) * 4
      z = rnd(i * 13) * (W + 4) - 1
    } else {
      x = L + 1.5 + rnd(i * 17) * 4
      z = rnd(i * 19) * (W + 4) - 1
    }
    const h = 0.18 + rnd(i * 6) * 0.35
    const blade = new THREE.Mesh(new THREE.ConeGeometry(0.04 + rnd(i) * 0.05, h, 5), i % 4 ? weedMat : grassB)
    blade.position.set(lx(x), h / 2, z)
    blade.rotation.z = (rnd(i + 2) - 0.5) * 0.35
    blade.rotation.x = (rnd(i + 3) - 0.5) * 0.25
    group.add(blade)
    if (i % 5 === 0) {
      const bloom = new THREE.Mesh(new THREE.SphereGeometry(0.03, 5, 5), flowerMat)
      bloom.position.set(lx(x), h + 0.02, z)
      group.add(bloom)
    }
  }

  for (let i = 0; i < 12; i++) {
    const x = i < 6 ? -2.5 - rnd(i) * 2.5 : L + 2.5 + rnd(i) * 2.5
    const z = 0.5 + rnd(i * 8) * (W + 2)
    const bush = new THREE.Mesh(new THREE.SphereGeometry(0.45 + rnd(i) * 0.35, 8, 6), shrubMat)
    bush.scale.set(1.2, 0.7 + rnd(i * 2) * 0.4, 1.0)
    bush.position.set(lx(x), 0.35, z)
    group.add(bush)
  }

  for (let i = 0; i < 9; i++) {
    const x = -8 + i * 4.2 + rnd(i) * 1.5
    const z = W + 7 + rnd(i * 2) * 3
    const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.12, 1.6 + rnd(i) * 1.2, 6), trunkMat)
    trunk.position.set(lx(x), 0.9, z)
    group.add(trunk)
    const crown = new THREE.Mesh(new THREE.SphereGeometry(0.9 + rnd(i) * 0.5, 8, 6), canopyMat)
    crown.position.set(lx(x), 2.1 + rnd(i) * 0.4, z)
    crown.scale.set(1.2, 0.9, 1.1)
    group.add(crown)
  }

  for (let i = 0; i < 10; i++) {
    const x = 1 + i * 1.55
    if (x > L - 1) continue
    const hedge = new THREE.Mesh(new THREE.SphereGeometry(0.35, 7, 5), shrubMat)
    hedge.scale.set(1.4, 0.55, 0.8)
    hedge.position.set(lx(x), 0.28, -1.35)
    group.add(hedge)
  }
}

function buildScene(el: HTMLDivElement) {
  const w = el.clientWidth || 800
  const h = el.clientHeight || 460
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xb8d4e8)
  scene.fog = new THREE.Fog(0xb8d4e8, 28, 70)

  camera = new THREE.PerspectiveCamera(38, w / h, 0.1, 160)
  const cam0 = defaultCameraPose(16, 7, 3.8)
  camera.position.copy(cam0.position)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.setSize(w, h)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  el.innerHTML = ''
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.enableZoom = true
  controls.zoomSpeed = 0.9
  controls.rotateSpeed = 0.72
  controls.panSpeed = 0.7
  controls.target.copy(cam0.target)
  controls.maxPolarAngle = Math.PI * 0.48
  controls.minDistance = 6
  controls.maxDistance = 55
  controls.addEventListener('start', () => {
    orbitDragging = true
    clearHover()
  })
  controls.addEventListener('end', () => {
    orbitDragging = false
  })

  ambientLight = new THREE.AmbientLight(0xfff6e8, 0.32)
  scene.add(ambientLight)
  sunLight = new THREE.DirectionalLight(0xfff1c8, 1.15)
  sunLight.position.set(4, 16, -14)
  scene.add(sunLight)
  hemiLight = new THREE.HemisphereLight(0xe8f4ff, 0x5a7a48, 0.48)
  scene.add(hemiLight)
  // 棚脊下方点光源：夜间充当工作/月光补光，白天压到几乎无感
  bayFillLight = new THREE.PointLight(0xd4e2f4, 0.2, 28, 1.6)
  bayFillLight.position.set(lx(8), 2.6, 3.5)
  scene.add(bayFillLight)

  skyDome = new THREE.Mesh(
    new THREE.SphereGeometry(80, 24, 16),
    new THREE.MeshBasicMaterial({ map: makeSkyTexture(45), side: THREE.BackSide, depthWrite: false }),
  )
  skyDome.renderOrder = -10
  scene.add(skyDome)

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(90, 70),
    new THREE.MeshStandardMaterial({ map: makeSoilTexture(), roughness: 0.95, metalness: 0 }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.03
  ground.renderOrder = RENDER.GROUND
  scene.add(ground)

  sunDisc = new THREE.Mesh(
    new THREE.SphereGeometry(1.15, 24, 24),
    new THREE.MeshBasicMaterial({ color: 0xffe08a, fog: false, depthTest: false }),
  )
  const sunGlow = new THREE.Mesh(
    new THREE.SphereGeometry(1.85, 16, 16),
    new THREE.MeshBasicMaterial({
      color: 0xffc857,
      transparent: true,
      opacity: 0.28,
      fog: false,
      depthWrite: false,
      depthTest: false,
    }),
  )
  sunDisc.add(sunGlow)
  sunDisc.renderOrder = RENDER.GIZMO
  scene.add(sunDisc)

  lampGroup = new THREE.Group()
  sensorGroup = new THREE.Group()
  markerGroup = new THREE.Group()
  clusterGroup = new THREE.Group()
  sunGroup = new THREE.Group()
  scene.add(lampGroup)
  scene.add(sensorGroup)
  scene.add(markerGroup)
  scene.add(clusterGroup)
  scene.add(sunGroup)

  const loop = () => {
    if (disposed) return
    raf = requestAnimationFrame(loop)
    controls?.update()
    lodFrame++
    if (lodFrame % 8 === 0) updateLodMode()
    if (statusGlowMats.length) {
      const pulse = 0.45 + 0.35 * (0.5 + 0.5 * Math.sin(performance.now() * 0.004))
      for (const m of statusGlowMats) m.opacity = pulse
    }
    if (renderer && scene && camera) renderer.render(scene, camera)
  }
  loop()
}

function archCurve(x: number, W: number, G: number, H: number) {
  return new THREE.CatmullRomCurve3([
    new THREE.Vector3(lx(x), 0.04, 0),
    new THREE.Vector3(lx(x), G * 0.55, W * 0.08),
    new THREE.Vector3(lx(x), G, W * 0.22),
    new THREE.Vector3(lx(x), H, W * 0.5),
    new THREE.Vector3(lx(x), G, W * 0.78),
    new THREE.Vector3(lx(x), G * 0.55, W * 0.92),
    new THREE.Vector3(lx(x), 0.04, W),
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
      side: THREE.FrontSide,
      depthWrite: false,
      polygonOffset: true,
      polygonOffsetFactor: 1,
      polygonOffsetUnits: 1,
    }),
  )
  skin.renderOrder = RENDER.STRUCTURE
  group.add(skin)
}

function addL1Rack(bed: { x0: number; x1: number; y0: number; y1: number }, group: THREE.Group) {
  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88 })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.45, roughness: 0.4 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f8a58, roughness: 0.55 })
  const trayMat = new THREE.MeshStandardMaterial({ color: 0xc5d8cc, roughness: 0.7 })
  const bw = bed.x1 - bed.x0
  const bd = bed.y1 - bed.y0
  const cx = (bed.x0 + bed.x1) / 2
  const cz = (bed.y0 + bed.y1) / 2

  for (const [ox, oz] of [
    [-bw / 2 + 0.08, -bd / 2 + 0.08],
    [bw / 2 - 0.08, -bd / 2 + 0.08],
    [-bw / 2 + 0.08, bd / 2 - 0.08],
    [bw / 2 - 0.08, bd / 2 - 0.08],
  ] as const) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.02, Z_L1, 8), legMat)
    leg.position.set(lx(cx + ox), Z_L1 / 2, cz + oz)
    group.add(leg)
  }

  const deck1 = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.18, 0.04, bd - 0.12), bedMat)
  deck1.position.set(lx(cx), Z_L1, cz)
  group.add(deck1)
  // 炼苗穴盘：约 0.28×0.2 m，沿床密铺
  for (let i = 0; i < 18; i++) {
    const tray = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.028, 0.18), trayMat)
    tray.position.set(lx(bed.x0 + 0.4 + i * 0.38), Z_L1 + 0.03, cz)
    group.add(tray)
    for (let k = 0; k < 4; k++) {
      const sprout = new THREE.Mesh(new THREE.SphereGeometry(0.018, 6, 6), leafMat)
      sprout.position.set(
        lx(bed.x0 + 0.32 + i * 0.38 + (k % 2) * 0.08),
        Z_L1 + 0.06,
        cz + (Math.floor(k / 2) - 0.5) * 0.06,
      )
      group.add(sprout)
    }
  }
  const l1Tag = makeLabelSprite('L1 组培/炼苗', 2.0)
  l1Tag.position.set(lx(cx), Z_L1 + 0.42, cz)
  group.add(l1Tag)
}

function addStackedCrops(bed: { x0: number; x1: number; y0: number; y1: number; bedId?: string }, group: THREE.Group) {
  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88 })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.45, roughness: 0.4 })
  const potMat = new THREE.MeshStandardMaterial({ color: 0x6b4e3a, roughness: 0.82 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f8a58, roughness: 0.55 })
  const leafDark = new THREE.MeshStandardMaterial({ color: 0x2d6a44, roughness: 0.6 })
  const bw = bed.x1 - bed.x0
  const bd = bed.y1 - bed.y0
  const cx = (bed.x0 + bed.x1) / 2
  const cz = (bed.y0 + bed.y1) / 2
  const withL1 = bed.bedId ? bedHasL1Tier(bed.bedId) : false

  const deck0 = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.055, bd), bedMat)
  deck0.position.set(lx(cx), Z_L0, cz)
  group.add(deck0)

  const rail = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.04, 0.035, 0.035), legMat)
  rail.position.set(lx(cx), Z_L0 + 0.04, bed.y0 + 0.02)
  group.add(rail)

  if (!withL1) {
    for (const [ox, oz] of [
      [-bw / 2 + 0.08, -bd / 2 + 0.08],
      [bw / 2 - 0.08, -bd / 2 + 0.08],
      [-bw / 2 + 0.08, bd / 2 - 0.08],
      [bw / 2 - 0.08, bd / 2 - 0.08],
    ] as const) {
      const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.018, 0.02, Z_L0, 8), legMat)
      leg.position.set(lx(cx + ox), Z_L0 / 2, cz + oz)
      group.add(leg)
    }
  }

  // 铁皮石斛盆栽：盆径 ~9 cm，丛高 ~12–20 cm，株距 ~15 cm（规程量级，非示意巨株）
  const potPitch = 0.15
  const rows = [0.18, 0.36, 0.54, 0.72, 0.88]
  for (let x = bed.x0 + 0.2; x < bed.x1 - 0.12; x += potPitch) {
    for (const row of rows) {
      const z = bed.y0 + bd * row
      const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.038, 0.045, 0.07, 8), potMat)
      pot.position.set(lx(x), Z_L0 + 0.055, z)
      group.add(pot)
      const h = 0.1 + ((x * 17 + z * 13) % 5) * 0.014
      const leaf = new THREE.Mesh(
        new THREE.ConeGeometry(0.035, h, 6),
        (x + z) % 1.1 > 0.5 ? leafMat : leafDark,
      )
      leaf.position.set(lx(x), Z_L0 + 0.09 + h / 2, z)
      leaf.rotation.y = (x * 5) % 2
      leaf.rotation.z = ((x * 3) % 1) * 0.15 - 0.07
      group.add(leaf)
    }
  }

  if (withL1) addL1Rack(bed, group)
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
  hit.position.set(lx((bed.x0 + bed.x1) / 2), 0.9, (bed.y0 + bed.y1) / 2)
  hit.name = `hit-${bed.bedId}`
  hit.userData.cropBed = meta
  group.add(hit)
  cropHitRoots.push(hit)

  const accent = info.key === 'dendrobium' ? '#34c759' : info.key === 'strawberry' ? '#ff3b30' : '#0071e3'
  const spr = makeAccentLabelSprite(`${info.nameZh} · ${bed.roleZh}`, accent, 3.0)
  spr.position.set(lx((bed.x0 + bed.x1) / 2), 2.05, (bed.y0 + bed.y1) / 2)
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
  const key = `${L}x${W}x${H}-v2.2-ewfix-${mode}-${cropSig}`
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

  addSiteEnvironment(L, W, group)

  const zoneA = crops.find((c) => c.zoneId === 'ZONE-A')
  const infoA = cropLabel(zoneA?.recipe, 'ZONE-A', zoneA?.recipeId)
  const stage = infoA.stage

  const strip = (x: number) => {
    const m = new THREE.Mesh(
      new THREE.BoxGeometry(6.8, 0.02, 0.28),
      new THREE.MeshStandardMaterial({
        color: 0x34c759,
        emissive: 0x34c759,
        emissiveIntensity: 0.2,
      }),
    )
    m.position.set(lx(x), 0.048, 0.4)
    tuneMesh(m, RENDER.FLOOR, { polygonOffset: 1 })
    group.add(m)
  }
  strip(4)
  strip(12)
  const cropTag = makeLabelSprite(
    `整跨 ${infoA.nameZh}${stage && stage !== '—' ? ` · ${stage}` : ''}`,
    4.2,
  )
  cropTag.position.set(lx(L / 2), 0.48, -0.55)
  group.add(cropTag)

  scene.add(group)
  if (!structureCameraReady && camera && controls) {
    structureCameraReady = true
    const cam = defaultCameraPose(L, W, H)
    controls.target.copy(cam.target)
    camera.position.copy(cam.position)
  }
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
  ridge.position.set(lx(L / 2), H, W / 2)
  group.add(ridge)
  for (const z of [0.08, W - 0.08]) {
    const gutter = new THREE.Mesh(new THREE.BoxGeometry(L, 0.08, 0.12), frameMat)
    gutter.position.set(lx(L / 2), G * 0.15, z)
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
  for (const x of [0.12, L - 0.12]) {
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.96, G * 0.92), endMat)
    wall.position.set(lx(x), G * 0.46, W / 2)
    wall.rotation.y = Math.PI / 2
    tuneMesh(wall, RENDER.STRUCTURE, { depthWrite: false })
    group.add(wall)
  }
  const door = new THREE.Mesh(
    new THREE.BoxGeometry(0.08, 1.9, 0.95),
    new THREE.MeshStandardMaterial({ color: 0x5a6570, metalness: 0.3, roughness: 0.5 }),
  )
  door.position.set(lx(0.08), 0.95, W / 2)
  group.add(door)

  for (const bed of BEDS) {
    const zc = crops.find((c) => c.zoneId === bed.zoneId)
    const info = cropLabel(zc?.recipe, bed.zoneId, zc?.recipeId)
    addStackedCrops(bed, group)
    attachCropHit(group, bed, info)
  }

  const postMat = new THREE.MeshStandardMaterial({ color: 0x6a737c, metalness: 0.4, roughness: 0.45 })
  for (const z of [1.4, 3.5, 5.6]) {
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.4, 8), postMat)
    post.position.set(lx(8), 1.2, z)
    group.add(post)
  }

  const aisle = new THREE.Mesh(
    new THREE.BoxGeometry(0.95, 0.04, W * 0.88),
    new THREE.MeshStandardMaterial({ color: 0x9aa394, roughness: 0.9 }),
  )
  aisle.position.set(lx(8), 0.028, W / 2)
  tuneMesh(aisle, RENDER.FLOOR, { polygonOffset: 1 })
  group.add(aisle)
}

function nightBlendFromElev(elev: number): number {
  // 0=白昼，1=深夜；黄昏平滑过渡，保留昼夜叙事但不至于伸手不见五指
  if (elev <= 0) return 1
  if (elev >= 18) return 0
  return 1 - elev / 18
}

function applyNightVisibility(elev: number, force = false) {
  const night = nightBlendFromElev(elev)
  if (!force && Math.abs(night - lastNightBlend) < 0.02) return
  lastNightBlend = night

  if (ambientLight) {
    ambientLight.intensity = 0.32 + night * 0.62
    ambientLight.color.set(night > 0.45 ? 0xb4c6de : 0xfff6e8)
  }
  if (hemiLight) {
    if (elev > 15) {
      hemiLight.intensity = 0.38 + (elev / 90) * 0.22
      hemiLight.color.set(0xe8f4ff)
      hemiLight.groundColor.set(0x5a7a48)
    } else if (elev > 2) {
      hemiLight.intensity = 0.36 + night * 0.2
      hemiLight.color.set(0xffd8b0)
      hemiLight.groundColor.set(0x5a7a48)
    } else {
      // 夜间：冷色环境光但保持可读，避免纯黑半球压死材质
      hemiLight.intensity = 0.48 + night * 0.22
      hemiLight.color.set(0x7a8eb0)
      hemiLight.groundColor.set(0x3d4a42)
    }
  }
  if (sunLight) {
    if (elev > 2) {
      sunLight.intensity = 0.4 + (elev / 90) * 1.25
      sunLight.color.set(elev > 15 ? 0xfff1c8 : 0xffc090)
    } else {
      // 极弱月光轮廓，主要靠 ambient + bayFill
      sunLight.intensity = 0.14 + night * 0.1
      sunLight.color.set(0xa8b8d8)
    }
  }
  if (bayFillLight) {
    bayFillLight.intensity = 0.18 + night * 1.35
    bayFillLight.color.set(night > 0.5 ? 0xc8daf0 : 0xfff2dc)
    bayFillLight.distance = 26 + night * 6
  }
  if (renderer) {
    renderer.toneMappingExposure = 1.05 + night * 0.42
  }
  if (scene?.fog instanceof THREE.Fog) {
    // 夜间雾略退后、略浅，避免远处植株/传感器被吞掉
    scene.fog.near = 28 + night * 10
    scene.fog.far = 70 + night * 28
  }

  // 传感器 / 遮阳标记：夜间略增自发光，位置仍可辨
  if (sensorGroup) {
    for (const root of sensorGroup.children) {
      root.traverse((obj) => {
        const m = obj as THREE.Mesh
        if (!m.isMesh || !m.material || Array.isArray(m.material)) return
        const mat = m.material as THREE.MeshStandardMaterial
        if ('emissiveIntensity' in mat) {
          mat.emissiveIntensity = 0.35 + night * 0.85
        }
      })
    }
  }
  if (markerGroup) {
    for (const root of markerGroup.children) {
      root.traverse((obj) => {
        const m = obj as THREE.Mesh
        if (!m.isMesh || !m.material || Array.isArray(m.material)) return
        const mat = m.material as THREE.MeshStandardMaterial
        if ('emissiveIntensity' in mat) {
          mat.emissiveIntensity = 0.2 + night * 0.55
        }
      })
    }
  }
}

function updateSun(light: GhEffectiveLight) {
  if (!scene || !sunGroup || !sunLight) return
  const elev = Number(light.solarElevationDeg ?? 0)
  const az = Number(light.solarAzimuthDeg ?? 180)
  const key = `${elev.toFixed(1)}:${az.toFixed(1)}:${Number(light.outdoorParPpfd ?? 0).toFixed(0)}`
  if (key === sunKey && sunDisc) {
    applyNightVisibility(elev)
    return
  }
  sunKey = key
  while (sunGroup.children.length) sunGroup.remove(sunGroup.children[0])
  if (sunArrow) {
    scene.remove(sunArrow)
    sunArrow = null
  }

  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7

  const sm = light.sunModel as { dirEast?: number; dirNorth?: number; dirUp?: number } | undefined
  const towardSun =
    sm?.dirEast != null && sm?.dirNorth != null && sm?.dirUp != null
      ? new THREE.Vector3(lx(sm.dirEast), sm.dirUp, sm.dirNorth).normalize()
      : sunDirectionThree(az, elev)
  const rayDir = towardSun.clone().negate()
  const center = layoutToThree(L / 2, W / 2, 1.2)
  const dist = 22
  const sunPos = center.clone().addScaledVector(towardSun, dist)

  sunLight.position.copy(sunPos)
  sunLight.target.position.copy(center)
  if (!sunLight.target.parent) scene.add(sunLight.target)
  sunLight.target.updateMatrixWorld()

  applyNightVisibility(elev, true)

  if (skyDome) {
    const mat = skyDome.material as THREE.MeshBasicMaterial
    mat.map?.dispose()
    mat.map = makeSkyTexture(elev)
    mat.needsUpdate = true
  }
  // 夜间背景仍偏冷，但比纯墨蓝亮一档，便于轮廓辨认
  const fogCol = elev < 2 ? 0x2c3a4f : elev < 18 ? 0xf0c8a0 : 0xb8d4e8
  scene.background = new THREE.Color(fogCol)
  if (scene.fog instanceof THREE.Fog) scene.fog.color.set(fogCol)

  if (sunDisc) {
    sunDisc.visible = elev > -3
    sunDisc.position.copy(sunPos)
    sunDisc.scale.setScalar(elev > 2 ? 1 : 0.7)
    ;(sunDisc.material as THREE.MeshBasicMaterial).color.set(
      elev > 20 ? 0xfff0a8 : elev > 5 ? 0xffb060 : 0xff8060,
    )
  }

  if (elev > 1) {
    const arrowLen = 8
    const arrowOrigin = sunPos.clone().addScaledVector(rayDir, 2.2)
    sunArrow = new THREE.ArrowHelper(rayDir, arrowOrigin, arrowLen, 0xffcc44, 0.5, 0.32)
    scene.add(sunArrow)
    const lab = makeLabelSprite(`日光 ${elev.toFixed(0)}° · ${azimuthLabelZh(az)}`, 3.2)
    lab.position.copy(arrowOrigin).addScaledVector(rayDir, arrowLen * 0.55).add(new THREE.Vector3(0, 0.7, 0))
    sunGroup.add(lab)
  } else {
    const lab = makeLabelSprite('夜间 · 棚内工作补光', 3.0)
    lab.position.set(lx(L / 2), 3.2, W * 0.15)
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
        polygonOffset: true,
        polygonOffsetFactor: -2,
        polygonOffsetUnits: -2,
      }),
    )
    mesh.rotation.x = -Math.PI / 2
    mesh.renderOrder = RENDER.SHADE
    scene.add(mesh)
  }
  const depth = Math.max(0.12, W * 0.88 * Math.max(0.05, closed))
  mesh.scale.set(1, 1, Math.max(0.05, closed))
  mesh.position.set(lx(xCenter), 3.42, W - 0.15 - depth / 2)
  ;(mesh.material as THREE.MeshStandardMaterial).opacity = 0.2 + closed * 0.55
  mesh.renderOrder = RENDER.SHADE
  return mesh
}

function sampleGridCells(
  light: GhEffectiveLight,
  nx: number,
  ny: number,
  L: number,
  W: number,
): {
  ppfd: Float32Array
  sun: Float32Array
  led: Float32Array
  r: Float32Array
  g: Float32Array
  b: Float32Array
} {
  const ppfd = new Float32Array(nx * ny)
  const sun = new Float32Array(nx * ny)
  const led = new Float32Array(nx * ny)
  const r = new Float32Array(nx * ny)
  const g = new Float32Array(nx * ny)
  const b = new Float32Array(nx * ny)
  const grid = light.grid || []
  const fill = (i: number, p: (typeof grid)[number] | undefined) => {
    ppfd[i] = p?.ppfd ?? 0
    sun[i] = p?.sunPpfd ?? 0
    led[i] = p?.ledPpfd ?? 0
    r[i] = p?.rPpfd ?? 0
    g[i] = p?.gPpfd ?? 0
    b[i] = p?.bPpfd ?? 0
  }
  // 后端按 iy→ix 顺序铺满 nx×ny，优先按序填充，避免坐标重映射留下大片 0（发黑）
  if (grid.length === nx * ny) {
    for (let i = 0; i < grid.length; i++) fill(i, grid[i])
    return { ppfd, sun, led, r, g, b }
  }
  if (grid.length && grid[0].x != null) {
    for (const p of grid) {
      const ix = Math.min(nx - 1, Math.max(0, Math.round(((p.x - 0.25) / Math.max(0.5, L - 0.5)) * (nx - 1))))
      const iy = Math.min(ny - 1, Math.max(0, Math.round(((p.y - 0.25) / Math.max(0.5, W - 0.5)) * (ny - 1))))
      fill(iy * nx + ix, p)
    }
  } else {
    for (let i = 0; i < Math.min(grid.length, nx * ny); i++) fill(i, grid[i])
  }
  return { ppfd, sun, led, r, g, b }
}

/** 在切片高度 z 上重算亮度：自然光取冠层网格，补光按灯位向下光束随高度变化 */
function brightnessAtSlice(
  light: GhEffectiveLight,
  nx: number,
  ny: number,
  L: number,
  W: number,
  z: number,
): {
  bright: Float32Array
  sun: Float32Array
  led: Float32Array
  r: Float32Array
  g: Float32Array
  b: Float32Array
} {
  const cells = sampleGridCells(light, nx, ny, L, W)
  const bright = new Float32Array(nx * ny)
  const led = new Float32Array(nx * ny)
  const rCh = new Float32Array(nx * ny)
  const gCh = new Float32Array(nx * ny)
  const bCh = new Float32Array(nx * ny)
  const aisle = L / 2
  const canopyZ = Number(light.measurePlaneZ) || 0.9
  const nearCanopy = Math.abs(z - canopyZ) < 0.04
  const hasBackendRgb = cells.r.some((v) => v > 0)
  const lamps = (light.devices || []).filter((d) => d.deviceType === 'GROW_LAMP' && d.posX != null)
  for (let iy = 0; iy < ny; iy++) {
    for (let ix = 0; ix < nx; ix++) {
      const i = iy * nx + ix
      const x = (ix / Math.max(1, nx - 1)) * L
      const y = (iy / Math.max(1, ny - 1)) * W
      let ledSum = 0
      for (const lamp of lamps) {
        if (lamp.powerOn === false) continue
        const dim = (lamp.dimmingPercent ?? 0) / 100
        if (dim <= 0) continue
        ledSum += unitLedAt(x, y, z, lamp) * dim
      }
      // 贴近测光面时优先用后端网格补光，避免客户端与仿真数值跳变
      if (nearCanopy && (cells.led[i] ?? 0) > 0) {
        ledSum = cells.led[i]
      }
      const sun = cells.sun[i] ?? 0
      led[i] = ledSum
      bright[i] = sun + ledSum
      if (nearCanopy && hasBackendRgb && cells.r[i] > 0) {
        const scale = cells.ppfd[i] > 1e-3 ? bright[i] / cells.ppfd[i] : 1
        rCh[i] = cells.r[i] * scale
        gCh[i] = cells.g[i] * scale
        bCh[i] = cells.b[i] * scale
      } else {
        const zoneRecipe =
          x < aisle
            ? props.zoneLights?.['ZONE-A']?.recipeId || light.recipeId
            : props.zoneLights?.['ZONE-B']?.recipeId || light.recipeId
        const rgb = splitRgb(sun, ledSum, ledShareForRecipe(zoneRecipe))
        rCh[i] = rgb.r
        gCh[i] = rgb.g
        bCh[i] = rgb.b
      }
    }
  }
  return { bright, sun: cells.sun, led, r: rCh, g: gCh, b: bCh }
}

function buildFlatHeatGeometry(
  nx: number,
  ny: number,
  L: number,
  W: number,
  z: number,
  colorAt: (i: number) => [number, number, number],
): THREE.BufferGeometry {
  const positions: number[] = []
  const colors: number[] = []
  const indices: number[] = []
  for (let iy = 0; iy < ny; iy++) {
    for (let ix = 0; ix < nx; ix++) {
      const i = iy * nx + ix
      const x = (ix / Math.max(1, nx - 1)) * L
      const zz = (iy / Math.max(1, ny - 1)) * W
      positions.push(lx(x), z, zz)
      const [r, g, b] = colorAt(i)
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
  liftY = 0,
): THREE.Mesh {
  const mesh = new THREE.Mesh(
    geo,
    new THREE.MeshBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity,
      side: THREE.DoubleSide,
      depthWrite: false,
      depthTest: false,
      toneMapped: false,
      polygonOffset: true,
      polygonOffsetFactor: -4,
      polygonOffsetUnits: -4,
    }),
  )
  mesh.renderOrder = renderOrder
  if (liftY) mesh.position.y = liftY
  return mesh
}

function colorAtHeatIndex(
  i: number,
  ch: HeatChannel,
  field: {
    bright: Float32Array
    sun: Float32Array
    led: Float32Array
    r: Float32Array
    g: Float32Array
    b: Float32Array
  },
  chRef: number,
  nx: number,
  L: number,
  viewR?: Float32Array,
  viewG?: Float32Array,
  viewB?: Float32Array,
): [number, number, number] {
  let rgb: [number, number, number]
  if (ch === 'rgb') {
    rgb = rgbCompositeColor(field.r[i], field.g[i], field.b[i], chRef, field.sun[i])
  } else if (ch === 'R' && viewR) rgb = channelMonoColor(viewR[i], chRef, 'R')
  else if (ch === 'G' && viewG) rgb = channelMonoColor(viewG[i], chRef, 'G')
  else if (ch === 'B' && viewB) rgb = channelMonoColor(viewB[i], chRef, 'B')
  else if (ch === 'viridis') rgb = viridisColor(field.bright[i], chRef)
  else rgb = xrayColor(field.bright[i], chRef)
  return rgb
}

function updateHeatmap(light: GhEffectiveLight) {
  if (!scene) return
  lastLight = light
  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7
  const nx = light.nx || 32
  const ny = light.ny || 14
  const visible = props.showHeat !== false
  const z = sliceZ.value
  const field = brightnessAtSlice(light, nx, ny, L, W, z)
  heatGrid = {
    nx,
    ny,
    L,
    W,
    baseZ: z,
    ppfd: field.bright,
    sun: field.sun,
    led: field.led,
    r: field.r,
    g: field.g,
    b: field.b,
  }

  const outdoor = Number(light.outdoorParPpfd) || 0
  let brightMax = heatMax.value
  for (let i = 0; i < field.bright.length; i++) {
    if (field.bright[i] > brightMax) brightMax = field.bright[i]
  }
  const brightRef = Math.max(outdoor * 0.55, brightMax, 80)
  const ch = heatChannel.value
  const needRgbView = ch === 'R' || ch === 'G' || ch === 'B' || ch === 'rgb'
  let viewR: Float32Array | undefined
  let viewG: Float32Array | undefined
  let viewB: Float32Array | undefined
  let chRef = brightRef
  if (needRgbView) {
    viewR = new Float32Array(field.r.length)
    viewG = new Float32Array(field.g.length)
    viewB = new Float32Array(field.b.length)
    for (let i = 0; i < field.r.length; i++) {
      viewR[i] = emphasizeLedChannel(field.r[i], field.sun[i], SUN_SHARE.r)
      viewG[i] = emphasizeLedChannel(field.g[i], field.sun[i], SUN_SHARE.g)
      viewB[i] = emphasizeLedChannel(field.b[i], field.sun[i], SUN_SHARE.b)
    }
    if (ch === 'R') chRef = Math.max(...viewR, brightRef * 0.35, 40)
    else if (ch === 'G') chRef = Math.max(...viewG, brightRef * 0.25, 30)
    else if (ch === 'B') chRef = Math.max(...viewB, brightRef * 0.3, 35)
    else chRef = Math.max(...viewR, ...viewG, ...viewB, brightRef * 0.4, 40)
  }

  const layout = `${nx}x${ny}x${L}x${W}`
  const canPatch =
    visible &&
    heatMesh &&
    heatLayoutKey === layout &&
    heatMesh.geometry.getAttribute('color')?.count === nx * ny

  if (!visible) {
    heatMesh = disposeMesh(heatMesh)
    heatSunMesh = disposeMesh(heatSunMesh)
    heatLedMesh = disposeMesh(heatLedMesh)
    if (sliceGhost && scene) {
      scene.remove(sliceGhost)
      sliceGhost.geometry.dispose()
      ;(sliceGhost.material as THREE.Material).dispose()
      sliceGhost = null
    }
    heatLayoutKey = ''
  } else if (canPatch && heatMesh) {
    const colors = heatMesh.geometry.getAttribute('color') as THREE.BufferAttribute
    for (let i = 0; i < nx * ny; i++) {
      const [r, g, b] = colorAtHeatIndex(i, ch, field, chRef, nx, L, viewR, viewG, viewB)
      colors.setXYZ(i, r / 255, g / 255, b / 255)
    }
    colors.needsUpdate = true
    heatMesh.position.y = 0.012
    if (sliceGhost) sliceGhost.position.set(lx(L / 2), z + 0.018, W / 2)
    // 切片高度变了：平移顶点 y
    const pos = heatMesh.geometry.getAttribute('position') as THREE.BufferAttribute
    if (Math.abs(pos.getY(0) - z) > 1e-4) {
      for (let i = 0; i < pos.count; i++) pos.setY(i, z)
      pos.needsUpdate = true
    }
  } else {
    heatMesh = disposeMesh(heatMesh)
    heatSunMesh = disposeMesh(heatSunMesh)
    heatLedMesh = disposeMesh(heatLedMesh)
    if (sliceGhost && scene) {
      scene.remove(sliceGhost)
      sliceGhost.geometry.dispose()
      ;(sliceGhost.material as THREE.Material).dispose()
      sliceGhost = null
    }
    const geo = buildFlatHeatGeometry(nx, ny, L, W, z, (i) =>
      colorAtHeatIndex(i, ch, field, chRef, nx, L, viewR, viewG, viewB),
    )
    heatMesh = makeHeatLayer(geo, 0.84, RENDER.HEAT, 0.012)
    scene.add(heatMesh)
    const edge = new THREE.EdgesGeometry(new THREE.PlaneGeometry(L * 0.98, W * 0.95))
    sliceGhost = new THREE.LineSegments(
      edge,
      new THREE.LineBasicMaterial({
        color: 0xa8d4ff,
        transparent: true,
        opacity: 0.55,
        depthTest: false,
      }),
    )
    sliceGhost.rotation.x = -Math.PI / 2
    sliceGhost.position.set(lx(L / 2), z + 0.018, W / 2)
    sliceGhost.renderOrder = RENDER.GIZMO
    scene.add(sliceGhost)
    heatLayoutKey = layout
  }

  if (heatBaseMesh) {
    heatBaseMesh.visible = false
  }

  const closedA = 1 - (props.shadeOpenA ?? light.shadeOpenPercent ?? 100) / 100
  const closedB = 1 - (props.shadeOpenB ?? light.shadeOpenPercent ?? 100) / 100
  shadeClothA = updateShadeRoll(shadeClothA, 4, 7.6, W, closedA)
  shadeClothB = updateShadeRoll(shadeClothB, 12, 7.6, W, closedB)
}

function deviceLayoutSignature(light: GhEffectiveLight): string {
  return (light.devices || [])
    .map(
      (d) =>
        `${d.deviceSn}:${d.deviceType}:${d.posX ?? ''}:${d.posY ?? ''}:${d.posZ ?? ''}`,
    )
    .join('|')
}

function deviceVisualSignature(light: GhEffectiveLight): string {
  const dims = (light.devices || [])
    .map((d) => `${d.deviceSn}:${Math.round((d.dimmingPercent ?? 0) / 5) * 5}:${d.powerOn === false ? 0 : 1}`)
    .join('|')
  const tones = Object.entries(props.deviceStatuses || {})
    .map(([sn, st]) => `${sn}:${st.tone}`)
    .sort()
    .join('|')
  return `${dims}#${tones}#${props.selectedDeviceSn || ''}`
}

function syncDevices(light: GhEffectiveLight) {
  const W = Number(light.widthM) || 7
  const layout = deviceLayoutSignature(light)
  const visual = deviceVisualSignature(light)
  if (layout !== deviceLayoutKey) {
    deviceLayoutKey = layout
    deviceVisualKey = visual
    rebuildDevicePickables(light, W)
    return
  }
  if (visual === deviceVisualKey) {
    rebuildClusters(light)
    applyLodVisibility()
    return
  }

  // 仅选中变化：就地换光晕，不清拾取体、不重建灯带
  const body = visual.slice(0, visual.lastIndexOf('#'))
  const prevBody = deviceVisualKey.slice(0, deviceVisualKey.lastIndexOf('#'))
  if (body === prevBody) {
    deviceVisualKey = visual
    patchSelectionGlows()
    rebuildClusters(light)
    applyLodVisibility()
    return
  }
  deviceVisualKey = visual
  rebuildDevicePickables(light, W)
}

function patchSelectionGlows() {
  statusGlowMats = []
  for (const g of [lampGroup, sensorGroup, markerGroup]) {
    if (!g) continue
    for (const root of g.children) {
      const sn = root.userData.deviceSn as string | undefined
      if (!sn) continue
      for (const c of [...root.children]) {
        if (c.name === 'status-glow') root.remove(c)
      }
      const st = props.deviceStatuses?.[sn]
      const tone = st?.tone || 'ok'
      if (statusNeedsGlow(tone) || props.selectedDeviceSn === sn) {
        const glowTone = statusNeedsGlow(tone) ? tone : 'ok'
        const glow = makeStatusGlow(glowTone, props.selectedDeviceSn === sn)
        glow.position.y = -0.04
        root.add(glow)
      }
    }
  }
}

function makeStatusGlow(tone: SceneStatusTone, selected: boolean): THREE.Group {
  const g = new THREE.Group()
  g.name = 'status-glow'
  const color = STATUS_GLOW_HEX[tone]
  const ringMat = new THREE.MeshBasicMaterial({
    color,
    transparent: true,
    opacity: selected ? 0.95 : 0.7,
    side: THREE.DoubleSide,
    depthWrite: false,
  })
  statusGlowMats.push(ringMat)
  const ring = new THREE.Mesh(new THREE.RingGeometry(0.14, selected ? 0.28 : 0.22, 40), ringMat)
  ring.rotation.x = -Math.PI / 2
  ring.renderOrder = 20
  g.add(ring)
  const haloMat = new THREE.MeshBasicMaterial({
    color,
    transparent: true,
    opacity: selected ? 0.35 : 0.22,
    depthWrite: false,
  })
  statusGlowMats.push(haloMat)
  const halo = new THREE.Mesh(new THREE.SphereGeometry(selected ? 0.32 : 0.24, 16, 12), haloMat)
  halo.renderOrder = 19
  g.add(halo)
  return g
}

function tagDeviceRoot(
  root: THREE.Object3D,
  meta: { deviceSn: string; deviceType: string; zoneId?: string },
  hitRadius: number,
  pushHit?: (hit: THREE.Object3D) => void,
) {
  root.name = meta.deviceSn
  root.userData.deviceSn = meta.deviceSn
  root.userData.deviceType = meta.deviceType
  root.userData.zoneId = meta.zoneId
  const hit = new THREE.Mesh(
    new THREE.SphereGeometry(hitRadius, 10, 10),
    new THREE.MeshBasicMaterial({ visible: false }),
  )
  hit.name = `hit-${meta.deviceSn}`
  hit.userData = { ...meta, pickDevice: true }
  root.add(hit)
  if (pushHit) pushHit(hit)
  else deviceHitRoots.push(hit)

  const st = props.deviceStatuses?.[meta.deviceSn]
  const tone = st?.tone || 'ok'
  if (statusNeedsGlow(tone) || props.selectedDeviceSn === meta.deviceSn) {
    const glowTone = statusNeedsGlow(tone) ? tone : 'ok'
    const glow = makeStatusGlow(glowTone, props.selectedDeviceSn === meta.deviceSn)
    glow.position.y = -0.04
    root.add(glow)
  }
}

function rebuildDevicePickables(light: GhEffectiveLight, W: number) {
  const nextHits: THREE.Object3D[] = []
  statusGlowMats = []
  if (!lampGroup || !sensorGroup || !markerGroup) return

  while (lampGroup.children.length) lampGroup.remove(lampGroup.children[0])
  while (sensorGroup.children.length) sensorGroup.remove(sensorGroup.children[0])
  while (markerGroup.children.length) markerGroup.remove(markerGroup.children[0])

  const placed = new Set<string>()
  const pushHit = (hit: THREE.Object3D) => {
    nextHits.push(hit)
  }

  for (const d of light.devices || []) {
    if (d.posX == null || d.posY == null) continue
    if (d.deviceType === 'GROW_LAMP') {
      const dim = (d.dimmingPercent ?? 0) / 100
      const lz = d.posZ ?? 1.85
      const isL1 = (d.deviceSn || '').includes('L1')
      const root = placeLedStrip(assets, lx(d.posX), d.posY, lz, dim, isL1)
      tagDeviceRoot(
        root,
        { deviceSn: d.deviceSn, deviceType: 'GROW_LAMP', zoneId: d.zoneId },
        0.32,
        pushHit,
      )
      lampGroup.add(root)
      placed.add(d.deviceSn)
    } else if (d.deviceType === 'PAR_SENSOR') {
      const sz = d.posZ ?? 0.9
      const disc = new THREE.Mesh(
        new THREE.CylinderGeometry(0.028, 0.028, 0.012, 10),
        new THREE.MeshStandardMaterial({
          color: 0xf5f5f7,
          emissive: 0x34c759,
          emissiveIntensity: 0.35,
        }),
      )
      const root = new THREE.Group()
      root.position.set(lx(d.posX), sz, d.posY)
      disc.position.set(0, 0, 0)
      root.add(disc)
      tagDeviceRoot(
        root,
        { deviceSn: d.deviceSn, deviceType: 'PAR_SENSOR', zoneId: d.zoneId },
        0.22,
        pushHit,
      )
      sensorGroup.add(root)
      placed.add(d.deviceSn)
    } else if (d.deviceType === 'SHADE_ACTUATOR') {
      placeShadeMarker(d.deviceSn, d.zoneId, d.posX, d.posY, d.posZ, W, pushHit)
      placed.add(d.deviceSn)
    }
  }

  for (const sn of ['SHADE-ZONE-A', 'SHADE-ZONE-B'] as const) {
    if (placed.has(sn)) continue
    const zoneId = sn.endsWith('B') ? 'ZONE-B' : 'ZONE-A'
    const x = zoneId === 'ZONE-B' ? 12 : 4
    placeShadeMarker(sn, zoneId, x, 6.7, 3.5, W, pushHit)
  }

  // 原子替换：避免重建中途 deviceHitRoots 为空导致点选失败
  deviceHitRoots = nextHits
  rebuildClusters(light)
  applyLodVisibility()
  const elev = Number(light.solarElevationDeg ?? lastLight?.solarElevationDeg ?? 0)
  applyNightVisibility(elev, true)
}

function toneRank(t: SceneStatusTone): number {
  if (t === 'alarm') return 50
  if (t === 'offline') return 40
  if (t === 'wo-pending') return 30
  if (t === 'wo-approved') return 20
  if (t === 'wo-progress') return 10
  return 0
}

function bedForDevice(d: { posX?: number | null; posY?: number | null; zoneId?: string }): (typeof BEDS)[number] | null {
  if (d.posX == null || d.posY == null) return null
  return (
    BEDS.find(
      (b) =>
        (!d.zoneId || b.zoneId === d.zoneId) &&
        d.posX! >= b.x0 &&
        d.posX! <= b.x1 &&
        d.posY! >= b.y0 &&
        d.posY! <= b.y1,
    ) || null
  )
}

function rebuildClusters(light: GhEffectiveLight) {
  if (!clusterGroup) return
  while (clusterGroup.children.length) clusterGroup.remove(clusterGroup.children[0])
  clusterHitRoots = []
  const byBed = new Map<
    string,
    { bed: (typeof BEDS)[number]; count: number; tone: SceneStatusTone; sn: string }
  >()
  for (const d of light.devices || []) {
    if (d.deviceType === 'SHADE_ACTUATOR') continue
    const st = props.deviceStatuses?.[d.deviceSn]
    const tone = (st?.tone || 'ok') as SceneStatusTone
    if (!passesStatusFilter(tone)) continue
    const bed = bedForDevice(d)
    if (!bed) continue
    const cur = byBed.get(bed.bedId)
    if (!cur) {
      byBed.set(bed.bedId, { bed, count: 1, tone, sn: d.deviceSn })
    } else {
      cur.count++
      if (toneRank(tone) > toneRank(cur.tone)) {
        cur.tone = tone
        cur.sn = d.deviceSn
      }
    }
  }
  for (const row of byBed.values()) {
    const root = new THREE.Group()
    root.position.set(lx(row.bed.x), 1.55, row.bed.z)
    root.userData = {
      deviceSn: row.sn,
      deviceType: 'CLUSTER',
      zoneId: row.bed.zoneId,
      bedId: row.bed.bedId,
      pickDevice: true,
    }
    const color = STATUS_GLOW_HEX[row.tone]
    const disc = new THREE.Mesh(
      new THREE.CylinderGeometry(0.38, 0.38, 0.04, 24),
      new THREE.MeshBasicMaterial({
        color,
        transparent: true,
        opacity: 0.72,
        depthWrite: false,
      }),
    )
    disc.userData = { ...root.userData }
    root.add(disc)
    const label = makeLabelSprite(`${row.bed.roleZh} · ${row.count}`, 1.8)
    label.position.set(0, 0.35, 0)
    root.add(label)
    clusterGroup.add(root)
    clusterHitRoots.push(disc)
  }
}

function updateLodMode() {
  if (!camera || !controls) return
  const dist = camera.position.distanceTo(controls.target)
  const next = lodClustered ? dist > LOD_NEAR : dist > LOD_FAR
  if (next === lodClustered) {
    applyLodVisibility()
    return
  }
  lodClustered = next
  lodClusteredUi.value = next
  applyLodVisibility()
}

function applyLodVisibility() {
  const detail = !lodClustered
  if (lampGroup) lampGroup.visible = detail
  if (sensorGroup) sensorGroup.visible = detail
  if (markerGroup) markerGroup.visible = detail
  if (clusterGroup) clusterGroup.visible = !detail
  if (detail) {
    for (const g of [lampGroup, sensorGroup, markerGroup]) {
      if (!g) continue
      for (const root of g.children) {
        const sn = root.userData.deviceSn as string | undefined
        const tone = (props.deviceStatuses?.[sn || '']?.tone || 'ok') as SceneStatusTone
        root.visible = passesStatusFilter(tone)
      }
    }
  }
}

function placeShadeMarker(
  deviceSn: string,
  zoneId: string | undefined,
  posX: number,
  posY: number,
  posZ: number | null | undefined,
  W: number,
  pushHit?: (hit: THREE.Object3D) => void,
) {
  if (!markerGroup) return
  const z = posZ ?? 7.35
  const root = new THREE.Group()
  root.position.set(lx(posX), z, posY ?? W * 0.5)
  const body = new THREE.Mesh(
    new THREE.CylinderGeometry(0.06, 0.08, 0.16, 12),
    new THREE.MeshStandardMaterial({
      color: 0x3a3f44,
      metalness: 0.4,
      roughness: 0.45,
      emissive: 0x1c1f22,
      emissiveIntensity: 0.2,
    }),
  )
  root.add(body)
  tagDeviceRoot(root, { deviceSn, deviceType: 'SHADE_ACTUATOR', zoneId }, 0.35, pushHit)
  markerGroup.add(root)
}

function clearHover() {
  hoverPpfd.value = null
  hoverSun.value = null
  hoverLed.value = null
  hoverR.value = null
  hoverG.value = null
  hoverB.value = null
  hoverXY.value = ''
  hoverCrop.value = null
  hoverDevice.value = null
}

function fillDeviceHover(
  meta: { deviceSn: string; deviceType?: string; zoneId?: string },
  clientX: number,
  clientY: number,
) {
  const host = hostRef.value
  if (!host) return
  const rect = host.getBoundingClientRect()
  tooltipPos.value = {
    x: Math.min(rect.width - 240, Math.max(12, clientX - rect.left + 14)),
    y: Math.min(rect.height - 120, Math.max(12, clientY - rect.top + 14)),
  }
  const st = props.deviceStatuses?.[meta.deviceSn]
  hoverDevice.value = {
    deviceSn: meta.deviceSn,
    deviceType: meta.deviceType,
    zoneId: meta.zoneId || st?.zoneId,
    labelZh: st?.labelZh || '设备',
    tone: st?.tone || 'ok',
  }
  hoverCrop.value = null
}

function pickDeviceFromRay(): { deviceSn: string; deviceType?: string; zoneId?: string } | null {
  const roots = lodClustered ? clusterHitRoots : deviceHitRoots
  if (!roots.length) return null
  const hits = raycaster.intersectObjects(roots, false)
  if (!hits.length) return null
  const ud = hits[0].object.userData
  if (!ud?.deviceSn) return null
  return {
    deviceSn: String(ud.deviceSn),
    deviceType: ud.deviceType ? String(ud.deviceType) : undefined,
    zoneId: ud.zoneId ? String(ud.zoneId) : undefined,
  }
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
  const dyn = zone?.dynamicTarget
  hoverCrop.value = {
    bedId: meta.bedId,
    zoneId: meta.zoneId,
    cropNameZh: meta.cropNameZh,
    roleZh: meta.roleZh,
    stage: meta.stage || zone?.recipe?.stage || '—',
    ppfd: sample.ppfd,
    sun: sample.sun,
    led: sample.led,
    targetMin: dyn?.instantMin ?? zone?.recipe?.ppfdTargetMin ?? 0,
    targetMax: dyn?.instantMax ?? zone?.recipe?.ppfdTargetMax ?? 0,
    recipeMin: dyn?.recipeMin ?? zone?.recipe?.ppfdTargetMin ?? 0,
    recipeMax: dyn?.recipeMax ?? zone?.recipe?.ppfdTargetMax ?? 0,
    dliSoFar: dyn?.dliSoFar ?? zone?.dliSoFar ?? null,
    dliTargetMin: dyn?.dliTargetMin ?? zone?.recipe?.dliTargetMin ?? null,
    dliRemaining: dyn?.dliRemainingMin ?? null,
    vpdKpa: dyn?.vpdKpa ?? zone?.vpdKpa ?? null,
    noteZh: dyn?.noteZh ?? '',
    temperatureC: zone?.temperatureC ?? null,
    humidityPct: zone?.humidityPct ?? null,
  }
  hoverPpfd.value = sample.ppfd
  hoverSun.value = sample.sun
  hoverLed.value = sample.led
  hoverXY.value = `${meta.cropNameZh} · ${meta.roleZh}`
}

function setPointerFromEvent(ev: PointerEvent | WheelEvent) {
  const host = hostRef.value
  if (!host || !camera) return false
  const rect = host.getBoundingClientRect()
  pointer.x = ((ev.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((ev.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return true
}

function onPointerMove(ev: PointerEvent) {
  if (sliceDragging) {
    const step = -ev.movementY * 0.01
    sliceZ.value = Math.max(
      SLICE_Z_MIN,
      Math.min(SLICE_Z_MAX, +(sliceZ.value + step).toFixed(2)),
    )
    if (lastLight) updateHeatmap(lastLight)
    return
  }
  if (orbitDragging) return
  if (hoverRaf) cancelAnimationFrame(hoverRaf)
  const cx = ev.clientX
  const cy = ev.clientY
  hoverRaf = requestAnimationFrame(() => {
    hoverRaf = 0
    runHoverPick(cx, cy)
  })
}

function runHoverPick(clientX: number, clientY: number) {
  const host = hostRef.value
  if (!host || !camera || orbitDragging) {
    clearHover()
    return
  }
  const rect = host.getBoundingClientRect()
  pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)

  const device = pickDeviceFromRay()
  if (device) {
    fillDeviceHover(device, clientX, clientY)
    host.style.cursor = 'pointer'
    return
  }
  host.style.cursor = 'crosshair'

  if (cropHitRoots.length) {
    const cropHits = raycaster.intersectObjects(cropHitRoots, false)
    if (cropHits.length) {
      const meta = cropHits[0].object.userData.cropBed
      if (meta) {
        hoverDevice.value = null
        fillCropHover(meta, clientX, clientY)
        return
      }
    }
  }

  hoverCrop.value = null
  hoverDevice.value = null
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
  const layoutHitX = threeXToLayout(p.x)
  const ix = Math.min(
    heatGrid.nx - 1,
    Math.max(0, Math.round((layoutHitX / heatGrid.L) * (heatGrid.nx - 1))),
  )
  const iy = Math.min(
    heatGrid.ny - 1,
    Math.max(0, Math.round((p.z / heatGrid.W) * (heatGrid.ny - 1))),
  )
  const i = iy * heatGrid.nx + ix
  hoverPpfd.value = heatGrid.ppfd[i]
  hoverSun.value = heatGrid.sun[i]
  hoverLed.value = heatGrid.led[i]
  hoverR.value = heatGrid.r[i]
  hoverG.value = heatGrid.g[i]
  hoverB.value = heatGrid.b[i]
  hoverXY.value = `z=${sliceZ.value.toFixed(2)}m · x东=${layoutHitX.toFixed(1)} · y北=${p.z.toFixed(1)}`
}

function onPointerDown(ev: PointerEvent) {
  ptrDownX = ev.clientX
  ptrDownY = ev.clientY
  ptrDownT = Date.now()
  if (props.showHeat === false || !heatMesh || !camera) return
  // Shift + 左键拖：上下移动光照切片
  if (!ev.shiftKey || ev.button !== 0) return
  if (!setPointerFromEvent(ev)) return
  const hits = raycaster.intersectObject(heatMesh, false)
  if (!hits.length) return
  sliceDragging = true
  sliceDragPointerId = ev.pointerId
  if (controls) controls.enabled = false
  hostRef.value?.setPointerCapture(ev.pointerId)
  ev.preventDefault()
}

function onPointerUp(ev: PointerEvent) {
  if (sliceDragging) {
    if (sliceDragPointerId != null && ev.pointerId !== sliceDragPointerId) return
    sliceDragging = false
    sliceDragPointerId = null
    if (controls) controls.enabled = true
    try {
      hostRef.value?.releasePointerCapture(ev.pointerId)
    } catch {
      /* ignore */
    }
    return
  }
  if (ev.button !== 0 || ev.shiftKey) return
  const dist = Math.hypot(ev.clientX - ptrDownX, ev.clientY - ptrDownY)
  if (dist > 8 || Date.now() - ptrDownT > 700) return
  if (!setPointerFromEvent(ev)) return
  const device = pickDeviceFromRay()
  if (device) {
    emit('selectDevice', device)
    return
  }
}

function onWheel(ev: WheelEvent) {
  if (props.showHeat === false) return
  // 普通滚轮：OrbitControls 缩放；Shift/Alt+滚轮：调切片高度
  if (!ev.shiftKey && !ev.altKey) return
  ev.preventDefault()
  ev.stopPropagation()
  const step = ev.deltaY > 0 ? -0.05 : 0.05
  sliceZ.value = Math.max(SLICE_Z_MIN, Math.min(SLICE_Z_MAX, +(sliceZ.value + step).toFixed(2)))
  if (lastLight) updateHeatmap(lastLight)
}

function onSliceInput(ev: Event) {
  sliceZ.value = Number((ev.target as HTMLInputElement).value)
  if (lastLight) updateHeatmap(lastLight)
}

function setSlicePreset(z: number) {
  sliceZ.value = z
  if (lastLight) updateHeatmap(lastLight)
}

function apply(light: GhEffectiveLight | null) {
  if (!light || !scene) return
  lastLight = light
  rebuildStructure(light)
  updateSun(light)
  updateHeatmap(light)
  syncDevices(light)
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
  host.addEventListener('pointerdown', onPointerDown)
  host.addEventListener('pointerup', onPointerUp)
  host.addEventListener('pointercancel', onPointerUp)
  host.addEventListener('wheel', onWheel, { passive: false })
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
  if (hoverRaf) cancelAnimationFrame(hoverRaf)
  hostRef.value?.removeEventListener('pointermove', onPointerMove)
  hostRef.value?.removeEventListener('pointerdown', onPointerDown)
  hostRef.value?.removeEventListener('pointerup', onPointerUp)
  hostRef.value?.removeEventListener('pointercancel', onPointerUp)
  hostRef.value?.removeEventListener('wheel', onWheel)
  window.removeEventListener('resize', onResize)
  ro?.disconnect()
  controls?.dispose()
  renderer?.dispose()
})

watch(
  () =>
    [
      props.light,
      props.showHeat,
      props.heatChannel,
      props.shadeOpenA,
      props.shadeOpenB,
    ] as const,
  () => apply(props.light),
)

watch(
  () => [props.deviceStatuses, props.selectedDeviceSn, props.statusFilter] as const,
  () => {
    if (!lastLight) return
    syncDevices(lastLight)
  },
)

watch(
  () => {
    const zl = props.zoneLights || {}
    return (['ZONE-A', 'ZONE-B'] as const)
      .map((z) => `${z}:${zl[z]?.recipeId || ''}`)
      .join('|')
  },
  () => {
    // 仅配方真正变化时重建棚体，避免轮询清空 structureKey 重置相机
    structureKey = ''
    if (props.light) apply(props.light)
  },
)
</script>

<template>
  <div class="wrap">
    <div ref="hostRef" class="scene" aria-label="智慧光棚三维整跨光场" />
    <div
      v-if="hoverDevice"
      class="crop-tip device-tip"
      :style="{ left: tooltipPos.x + 'px', top: tooltipPos.y + 'px' }"
    >
      <p class="tip-title">{{ hoverDevice.deviceSn }}</p>
      <p class="tip-sub">
        {{ hoverDevice.zoneId || '—' }} ·
        {{
          hoverDevice.deviceType === 'GROW_LAMP'
            ? '补光灯'
            : hoverDevice.deviceType === 'PAR_SENSOR'
              ? 'PAR 测点'
              : hoverDevice.deviceType === 'SHADE_ACTUATOR'
                ? '遮阳'
                : hoverDevice.deviceType || '设备'
        }}
      </p>
      <p class="tip-status" :data-tone="hoverDevice.tone">{{ hoverDevice.labelZh }}</p>
      <p class="tip-muted">点击查看详情并处理</p>
    </div>
    <div
      v-else-if="hoverCrop"
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
          <dt>此刻目标</dt>
          <dd>{{ hoverCrop.targetMin.toFixed(0) }}–{{ hoverCrop.targetMax.toFixed(0) }}</dd>
        </div>
        <div>
          <dt>配方基带</dt>
          <dd>{{ hoverCrop.recipeMin }}–{{ hoverCrop.recipeMax }}</dd>
        </div>
        <div>
          <dt>日 DLI</dt>
          <dd>
            {{ hoverCrop.dliSoFar != null ? hoverCrop.dliSoFar.toFixed(2) : '—' }}
            <template v-if="hoverCrop.dliTargetMin != null"
              >/{{ hoverCrop.dliTargetMin.toFixed(2) }}</template
            >
            <span v-if="hoverCrop.dliRemaining != null && hoverCrop.dliRemaining > 0" class="tip-muted">
              · 缺口 {{ hoverCrop.dliRemaining.toFixed(2) }}</span
            >
          </dd>
        </div>
        <div>
          <dt>日光 / 补光</dt>
          <dd>{{ hoverCrop.sun.toFixed(0) }} / {{ hoverCrop.led.toFixed(0) }}</dd>
        </div>
        <div>
          <dt>温湿 · VPD</dt>
          <dd>
            {{ hoverCrop.temperatureC != null ? hoverCrop.temperatureC.toFixed(1) + '°C' : '—' }} ·
            {{ hoverCrop.humidityPct != null ? hoverCrop.humidityPct.toFixed(0) + '%' : '—' }}
            <template v-if="hoverCrop.vpdKpa != null">
              · {{ hoverCrop.vpdKpa.toFixed(2) }} kPa</template
            >
          </dd>
        </div>
      </dl>
      <p v-if="hoverCrop.noteZh" class="tip-note">{{ hoverCrop.noteZh }}</p>
    </div>
    <aside class="hud">
      <p class="sun">{{ sunHud }}</p>
      <p class="model">{{ modelHud }} · {{ sliceHud }}</p>
      <p class="hint">
        滚轮缩放 · 点击设备看告警/工单 · <strong>拖滑条</strong> /
        <strong>Shift+拖切片</strong> / <strong>Shift+滚轮</strong> 调高度
      </p>
      <p v-if="shadeWarn" class="warn">{{ shadeWarn }}</p>
      <label class="slice-slider">
        <span>切片高度 {{ sliceZ.toFixed(2) }} m</span>
        <input
          type="range"
          :min="SLICE_Z_MIN"
          :max="SLICE_Z_MAX"
          step="0.05"
          :value="sliceZ"
          @pointerdown.stop
          @input="onSliceInput"
        />
      </label>
      <div class="slice-presets">
        <button type="button" class="preset" @click="setSlicePreset(0.9)">L0 冠层 0.90</button>
        <button type="button" class="preset" @click="setSlicePreset(1.2)">L1 搁架 1.20</button>
      </div>
      <p class="lod-hint">{{ lodClusteredUi ? '远距 · 床位聚合' : '近距 · 单设备' }}</p>
      <div class="scale">
        <div class="bar"><i class="grad" :class="heatChannel" /></div>
        <div class="ticks mono">
          <span>{{ legendTicks.lo }}</span>
          <span>{{ legendTicks.mid }}</span>
          <span>{{ legendTicks.hi }}</span>
        </div>
        <p class="unit">{{ channelHud }} · µmol · 平面切片</p>
      </div>
      <p v-if="hoverCrop" class="hover mono">
        {{ hoverCrop.cropNameZh }} · 实况 {{ hoverCrop.ppfd.toFixed(1) }}
        <br />
        <span class="dim"
          >此刻目标 {{ hoverCrop.targetMin.toFixed(0) }}–{{ hoverCrop.targetMax.toFixed(0) }}</span
        >
      </p>
      <p v-else-if="hoverPpfd != null" class="hover mono">
        Σ {{ hoverPpfd.toFixed(1) }}
        <span class="dim"
          >· R {{ hoverR?.toFixed(0) }} · G {{ hoverG?.toFixed(0) }} · B {{ hoverB?.toFixed(0) }}</span
        >
        <br />
        <span class="dim">{{ hoverXY }}</span>
      </p>
      <p v-else class="hint">悬停读点 · Shift 拖切片换层</p>
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
  left: 0.6rem;
  bottom: 0.6rem;
  top: auto;
  max-width: min(15rem, 42vw);
  padding: 0.45rem 0.55rem;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--ink);
  font-size: 0.68rem;
  line-height: 1.35;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  pointer-events: none;
  box-shadow: var(--shadow-sm);
  z-index: 2;
}
.slice-slider {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  margin: 0.35rem 0 0.25rem;
  pointer-events: auto;
  cursor: default;
}
.slice-slider span {
  color: var(--ink-soft);
  font-size: 0.65rem;
}
.slice-slider input[type='range'] {
  width: 100%;
  accent-color: var(--accent);
  pointer-events: auto;
  cursor: pointer;
}
.slice-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin: 0 0 0.2rem;
  pointer-events: auto;
}
.slice-presets .preset {
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.55);
  color: var(--ink);
  font-size: 0.65rem;
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  cursor: pointer;
}
.lod-hint {
  margin: 0;
  font-size: 0.62rem;
  color: var(--ink-soft);
}
.crop-tip {
  position: absolute;
  z-index: 6;
  min-width: 11rem;
  max-width: 14rem;
  padding: 0.55rem 0.7rem;
  background: rgba(29, 29, 31, 0.92);
  color: #f5f5f7;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: var(--shadow-sm);
  pointer-events: none;
  font-size: 0.72rem;
  line-height: 1.35;
}
.device-tip .tip-status {
  margin: 0.35rem 0 0.15rem;
  font-weight: 650;
}
.device-tip .tip-status[data-tone='alarm'] {
  color: #ff6961;
}
.device-tip .tip-status[data-tone='offline'] {
  color: #aeaeb2;
}
.device-tip .tip-status[data-tone='wo-pending'] {
  color: #ffb340;
}
.device-tip .tip-status[data-tone='wo-approved'] {
  color: #64b5ff;
}
.device-tip .tip-status[data-tone='wo-progress'] {
  color: #d4a5ff;
}
.device-tip .tip-status[data-tone='ok'] {
  color: #63e6a0;
}
.device-tip .tip-muted {
  margin: 0;
  opacity: 0.7;
  font-size: 0.65rem;
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
  background: linear-gradient(90deg, #ffffff, #ff2a2a);
}
.bar .grad.viridis {
  background: linear-gradient(90deg, #440154, #31688e, #35b779, #fde725);
}
.bar .grad.rgb {
  background: linear-gradient(90deg, #1a0505, #c02020, #20a040, #2060e0, #f0f0ff);
}
.bar .grad.R {
  background: linear-gradient(90deg, #140808, #ff3030);
}
.bar .grad.G {
  background: linear-gradient(90deg, #081408, #30e050);
}
.bar .grad.B {
  background: linear-gradient(90deg, #080814, #4080ff);
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
.tip-muted {
  font-weight: 400;
  opacity: 0.7;
  font-size: 0.65rem;
}
.tip-note {
  margin: 0.45rem 0 0;
  font-size: 0.62rem;
  line-height: 1.35;
  color: rgba(245, 245, 247, 0.55);
}
</style>
