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
  makeGlbLamp,
  placeGlbStructure,
  type GhAssetPack,
  type ZoneCropInput,
} from '../scene/greenhouseAssets'
import {
  azimuthLabelZh,
  defaultCameraPose,
  layoutToThree,
  sunDirectionThree,
} from '../scene/layoutCoords'
import {
  HEAT_CHANNEL_LABEL,
  channelMonoColor,
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
  focusZoneId?: string
  shadeOpenA?: number
  shadeOpenB?: number
  showHeat?: boolean
  /** xray | viridis | rgb | R | G | B */
  heatChannel?: HeatChannel
}>()

const hostRef = ref<HTMLDivElement | null>(null)
const assetSource = ref<'glb' | 'procedural' | 'loading'>('loading')
/** X 光切片高度（m），滚轮调节 */
const sliceZ = ref(0.85)
const SLICE_Z_MIN = 0.45
const SLICE_Z_MAX = 1.55
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
let shadeClothA: THREE.Mesh | null = null
let shadeClothB: THREE.Mesh | null = null
let sunLight: THREE.DirectionalLight | null = null
let sunArrow: THREE.ArrowHelper | null = null
let sunDisc: THREE.Mesh | null = null
let hemiLight: THREE.HemisphereLight | null = null
let skyDome: THREE.Mesh | null = null
let sunGroup: THREE.Group | null = null
let lampGroup: THREE.Group | null = null
let sensorGroup: THREE.Group | null = null
let structureKey = ''
let raf = 0
let disposed = false
let lastLight: GhEffectiveLight | null = null
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

const heatChannel = computed(() => props.heatChannel || 'rgb')
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

const sliceHud = computed(
  () => `${channelHud.value} · z=${sliceZ.value.toFixed(2)} m · 滚轮调高`,
)

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
    g.addColorStop(0, '#0b1220')
    g.addColorStop(0.55, '#1a2740')
    g.addColorStop(1, '#3a4050')
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
    g.position.set(cx, gravelY, cz)
    tuneMesh(g, RENDER.GROUND)
    group.add(g)
  }

  const path = new THREE.Mesh(new THREE.BoxGeometry(3.2, 0.012, 6.5), dirtMat)
  path.position.set(L / 2, 0.008, -2.8)
  tuneMesh(path, RENDER.FLOOR, { polygonOffset: 1 })
  group.add(path)

  for (const [t, px, pz, sx] of [
    ['北', L / 2, W + 2.2, 2.2],
    ['南', L / 2, -2.2, 2.2],
    ['西', -2.2, W / 2, 2.2],
    ['东', L + 2.2, W / 2, 2.2],
  ] as const) {
    const s = makeLabelSprite(t, sx)
    s.position.set(px, 0.56, pz)
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
    patch.position.set(x, 0.014 + rnd(i * 2) * 0.006, z)
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
    blade.position.set(x, h / 2, z)
    blade.rotation.z = (rnd(i + 2) - 0.5) * 0.35
    blade.rotation.x = (rnd(i + 3) - 0.5) * 0.25
    group.add(blade)
    if (i % 5 === 0) {
      const bloom = new THREE.Mesh(new THREE.SphereGeometry(0.03, 5, 5), flowerMat)
      bloom.position.set(x, h + 0.02, z)
      group.add(bloom)
    }
  }

  for (let i = 0; i < 12; i++) {
    const x = i < 6 ? -2.5 - rnd(i) * 2.5 : L + 2.5 + rnd(i) * 2.5
    const z = 0.5 + rnd(i * 8) * (W + 2)
    const bush = new THREE.Mesh(new THREE.SphereGeometry(0.45 + rnd(i) * 0.35, 8, 6), shrubMat)
    bush.scale.set(1.2, 0.7 + rnd(i * 2) * 0.4, 1.0)
    bush.position.set(x, 0.35, z)
    group.add(bush)
  }

  for (let i = 0; i < 9; i++) {
    const x = -8 + i * 4.2 + rnd(i) * 1.5
    const z = W + 7 + rnd(i * 2) * 3
    const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.12, 1.6 + rnd(i) * 1.2, 6), trunkMat)
    trunk.position.set(x, 0.9, z)
    group.add(trunk)
    const crown = new THREE.Mesh(new THREE.SphereGeometry(0.9 + rnd(i) * 0.5, 8, 6), canopyMat)
    crown.position.set(x, 2.1 + rnd(i) * 0.4, z)
    crown.scale.set(1.2, 0.9, 1.1)
    group.add(crown)
  }

  for (let i = 0; i < 10; i++) {
    const x = 1 + i * 1.55
    if (x > L - 1) continue
    const hedge = new THREE.Mesh(new THREE.SphereGeometry(0.35, 7, 5), shrubMat)
    hedge.scale.set(1.4, 0.55, 0.8)
    hedge.position.set(x, 0.28, -1.35)
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

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, logarithmicDepthBuffer: true })
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
  controls.enableZoom = false
  controls.target.copy(cam0.target)
  controls.maxPolarAngle = Math.PI * 0.48
  controls.minDistance = 6
  controls.maxDistance = 55

  scene.add(new THREE.AmbientLight(0xfff6e8, 0.28))
  sunLight = new THREE.DirectionalLight(0xfff1c8, 1.15)
  sunLight.position.set(4, 16, -14)
  scene.add(sunLight)
  hemiLight = new THREE.HemisphereLight(0xe8f4ff, 0x5a7a48, 0.48)
  scene.add(hemiLight)

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
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.025, Z_L1, 8), legMat)
    leg.position.set(cx + ox, Z_L1 / 2, cz + oz)
    group.add(leg)
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
  const l1Tag = makeLabelSprite('L1 组培/炼苗', 2.4)
  l1Tag.position.set(cx, Z_L1 + 0.55, cz)
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

  const deck0 = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.06, bd), bedMat)
  deck0.position.set(cx, Z_L0, cz)
  group.add(deck0)

  const rail = new THREE.Mesh(new THREE.BoxGeometry(bw + 0.04, 0.04, 0.04), legMat)
  rail.position.set(cx, Z_L0 + 0.05, bed.y0 + 0.02)
  group.add(rail)

  if (!withL1) {
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
  const key = `${L}x${W}x${H}-v1.9-${mode}-${cropSig}`
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
    m.position.set(x, 0.048, 0.4)
    tuneMesh(m, RENDER.FLOOR, { polygonOffset: 1 })
    group.add(m)
  }
  strip(4)
  strip(12)
  const south = makeLabelSprite(`南 · 采光 · 整跨 ${infoA.nameZh}${stage && stage !== '—' ? ` · ${stage}` : ''}`, 5.2)
  south.position.set(L / 2, 0.5, -0.7)
  group.add(south)

  const westA = makeLabelSprite('西半跨', 2.2)
  westA.position.set(-1.3, 1.85, W / 2)
  group.add(westA)
  const eastB = makeLabelSprite('东半跨', 2.2)
  eastB.position.set(L + 1.3, 1.85, W / 2)
  group.add(eastB)

  scene.add(group)
  const cam = defaultCameraPose(L, W, H)
  controls!.target.copy(cam.target)
  camera?.position.copy(cam.position)
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
  for (const x of [0.12, L - 0.12]) {
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.96, G * 0.92), endMat)
    wall.position.set(x, G * 0.46, W / 2)
    wall.rotation.y = Math.PI / 2
    tuneMesh(wall, RENDER.STRUCTURE, { depthWrite: false })
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
    addStackedCrops(bed, group)
    attachCropHit(group, bed, info)
  }

  const postMat = new THREE.MeshStandardMaterial({ color: 0x6a737c, metalness: 0.4, roughness: 0.45 })
  for (const z of [1.4, 3.5, 5.6]) {
    const post = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.04, 2.4, 8), postMat)
    post.position.set(8, 1.2, z)
    group.add(post)
  }

  const aisle = new THREE.Mesh(
    new THREE.BoxGeometry(0.95, 0.04, W * 0.88),
    new THREE.MeshStandardMaterial({ color: 0x9aa394, roughness: 0.9 }),
  )
  aisle.position.set(8, 0.028, W / 2)
  tuneMesh(aisle, RENDER.FLOOR, { polygonOffset: 1 })
  group.add(aisle)
}

function updateSun(light: GhEffectiveLight) {
  if (!scene || !sunGroup || !sunLight) return
  while (sunGroup.children.length) sunGroup.remove(sunGroup.children[0])
  if (sunArrow) {
    scene.remove(sunArrow)
    sunArrow = null
  }

  const elev = Number(light.solarElevationDeg ?? 0)
  const az = Number(light.solarAzimuthDeg ?? 180)
  const L = Number(light.lengthM) || 16
  const W = Number(light.widthM) || 7

  const sm = light.sunModel as { dirEast?: number; dirNorth?: number; dirUp?: number } | undefined
  const towardSun =
    sm?.dirEast != null && sm?.dirNorth != null && sm?.dirUp != null
      ? new THREE.Vector3(sm.dirEast, sm.dirUp, sm.dirNorth).normalize()
      : sunDirectionThree(az, elev)
  const rayDir = towardSun.clone().negate()
  const center = layoutToThree(L / 2, W / 2, 1.2)
  const dist = 22
  const sunPos = center.clone().addScaledVector(towardSun, dist)

  sunLight.position.copy(sunPos)
  sunLight.target.position.copy(center)
  if (!sunLight.target.parent) scene.add(sunLight.target)
  sunLight.target.updateMatrixWorld()
  sunLight.intensity = elev > 2 ? 0.4 + (elev / 90) * 1.25 : 0.06
  sunLight.color.set(elev > 15 ? 0xfff1c8 : elev > 2 ? 0xffc090 : 0x8899bb)

  if (hemiLight) {
    hemiLight.intensity = elev > 2 ? 0.38 + (elev / 90) * 0.22 : 0.16
    hemiLight.color.set(elev > 15 ? 0xe8f4ff : elev > 2 ? 0xffd8b0 : 0x1a2740)
    hemiLight.groundColor.set(elev > 2 ? 0x5a7a48 : 0x2a3030)
  }

  if (skyDome) {
    const mat = skyDome.material as THREE.MeshBasicMaterial
    mat.map?.dispose()
    mat.map = makeSkyTexture(elev)
    mat.needsUpdate = true
  }
  const fogCol = elev < 2 ? 0x1a2740 : elev < 18 ? 0xf0c8a0 : 0xb8d4e8
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
  mesh.position.set(xCenter, 3.42, W - 0.15 - depth / 2)
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

/** 在切片高度 z 上重算亮度，并按日光/补光光谱拆成 R/G/B */
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
  const canopyZ = Number(light.measurePlaneZ) || 0.85
  const nearCanopy = Math.abs(z - canopyZ) < 0.08
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
        const lx = lamp.posX!
        const ly = lamp.posY ?? y
        const lz = lamp.posZ ?? 1.85
        const dx = x - lx
        const dy = y - ly
        const dz = Math.max(0.15, lz - z)
        const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        const cos = dz / dist
        const halfAng = Math.cos((55 * Math.PI) / 180)
        if (cos < halfAng * 0.85) {
          const soft = Math.max(0, (cos - 0.05) / Math.max(0.2, halfAng))
          if (soft < 0.05) continue
        }
        const maxCanopy = (lamp.deviceSn || '').includes('L1')
          ? 55
          : (lamp.deviceSn || '').includes('ZONE-B')
            ? 80
            : 95
        const designH = (lamp.deviceSn || '').includes('L1') ? 0.8 : 0.95
        const peak = maxCanopy * designH * designH
        ledSum += (peak * cos) / (dist * dist) * dim
      }
      const sun = cells.sun[i] ?? 0
      led[i] = ledSum
      bright[i] = sun + ledSum
      if (nearCanopy && hasBackendRgb && cells.r[i] > 0) {
        // 冠层高度优先用后端三色分解（与物理遮阳/配方光谱一致）
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
      positions.push(x, z, zz)
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
      depthTest: true,
      polygonOffset: true,
      polygonOffsetFactor: -4,
      polygonOffsetUnits: -4,
    }),
  )
  mesh.renderOrder = renderOrder
  if (liftY) mesh.position.y = liftY
  return mesh
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
  const brightRef = Math.max(outdoor * 0.55, ...field.bright, heatMax.value, 80)
  const ch = heatChannel.value
  const chRef =
    ch === 'R'
      ? Math.max(...field.r, brightRef * 0.4, 40)
      : ch === 'G'
        ? Math.max(...field.g, brightRef * 0.4, 40)
        : ch === 'B'
          ? Math.max(...field.b, brightRef * 0.4, 40)
          : brightRef

  heatMesh = disposeMesh(heatMesh)
  heatSunMesh = disposeMesh(heatSunMesh)
  heatLedMesh = disposeMesh(heatLedMesh)
  if (sliceGhost && scene) {
    scene.remove(sliceGhost)
    sliceGhost.geometry.dispose()
    ;(sliceGhost.material as THREE.Material).dispose()
    sliceGhost = null
  }

  if (visible && (light.grid?.length ?? 0) > 0) {
    const geo = buildFlatHeatGeometry(nx, ny, L, W, z, (i) => {
      if (ch === 'rgb') return rgbCompositeColor(field.r[i], field.g[i], field.b[i], chRef)
      if (ch === 'R') return channelMonoColor(field.r[i], chRef, 'R')
      if (ch === 'G') return channelMonoColor(field.g[i], chRef, 'G')
      if (ch === 'B') return channelMonoColor(field.b[i], chRef, 'B')
      if (ch === 'viridis') return viridisColor(field.bright[i], chRef)
      return xrayColor(field.bright[i], chRef)
    })
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
    sliceGhost.position.set(L / 2, z + 0.018, W / 2)
    sliceGhost.renderOrder = RENDER.GIZMO
    scene.add(sliceGhost)
  }

  if (heatBaseMesh) {
    heatBaseMesh.visible = false
  }

  const closedA = 1 - (props.shadeOpenA ?? light.shadeOpenPercent ?? 100) / 100
  const closedB = 1 - (props.shadeOpenB ?? light.shadeOpenPercent ?? 100) / 100
  shadeClothA = updateShadeRoll(shadeClothA, 4, 7.6, W, closedA)
  shadeClothB = updateShadeRoll(shadeClothB, 12, 7.6, W, closedB)

  if (lampGroup && sensorGroup) {
    while (lampGroup.children.length) lampGroup.remove(lampGroup.children[0])
    while (sensorGroup.children.length) sensorGroup.remove(sensorGroup.children[0])
    for (const d of light.devices || []) {
      if (d.posX == null || d.posY == null) continue
      if (d.deviceType === 'GROW_LAMP') {
        const dim = (d.dimmingPercent ?? 0) / 100
        const lz = d.posZ ?? 1.85
        if (assets?.ready) {
          lampGroup.add(makeGlbLamp(assets, d.posX, d.posY, lz, dim))
        } else {
          const bar = new THREE.Mesh(
            new THREE.BoxGeometry(0.35, 0.03, 0.08),
            new THREE.MeshStandardMaterial({
              color: 0x1d1d1f,
              emissive: 0xffcc55,
              emissiveIntensity: 0.2 + dim * 1.1,
              metalness: 0.5,
              roughness: 0.35,
            }),
          )
          bar.position.set(d.posX, lz, d.posY)
          lampGroup.add(bar)
        }
      } else if (d.deviceType === 'PAR_SENSOR') {
        const sz = d.posZ ?? 0.85
        const disc = new THREE.Mesh(
          new THREE.CylinderGeometry(0.07, 0.07, 0.025, 12),
          new THREE.MeshStandardMaterial({
            color: 0xf5f5f7,
            emissive: 0x34c759,
            emissiveIntensity: 0.45,
          }),
        )
        disc.position.set(d.posX, sz, d.posY)
        sensorGroup.add(disc)
      }
    }
  }
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
  hoverR.value = heatGrid.r[i]
  hoverG.value = heatGrid.g[i]
  hoverB.value = heatGrid.b[i]
  hoverXY.value = `z=${sliceZ.value.toFixed(2)}m · x=${p.x.toFixed(1)} · y北=${p.z.toFixed(1)}`
}

function onWheel(ev: WheelEvent) {
  if (props.showHeat === false) return
  // 普通滚轮交给 OrbitControls 缩放；Alt+滚轮 才调整切片高度
  if (!ev.altKey) return
  ev.preventDefault()
  const step = ev.deltaY > 0 ? -0.05 : 0.05
  sliceZ.value = Math.max(SLICE_Z_MIN, Math.min(SLICE_Z_MAX, +(sliceZ.value + step).toFixed(2)))
  if (lastLight) updateHeatmap(lastLight)
}

function onSliceInput(ev: Event) {
  sliceZ.value = Number((ev.target as HTMLInputElement).value)
  if (lastLight) updateHeatmap(lastLight)
}

function apply(light: GhEffectiveLight | null) {
  if (!light || !scene) return
  lastLight = light
  if (sliceZ.value === 0.85 && light.measurePlaneZ) {
    // 首次贴近测光面
  }
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
  hostRef.value?.removeEventListener('pointermove', onPointerMove)
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
      props.zoneLights,
      props.showHeat,
      props.heatChannel,
      props.shadeOpenA,
      props.shadeOpenB,
      props.focusZoneId,
    ] as const,
  () => apply(props.light),
  { deep: true },
)
</script>

<template>
  <div class="wrap">
    <div ref="hostRef" class="scene" aria-label="智慧光棚三维整跨光场" />
    <div v-if="hoverCrop" class="crop-tip">
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
      <p class="hint">面北视角：西左东右 · 南为采光侧 · <strong>滚轮缩放</strong> · <strong>Alt+滚轮</strong> 调切片</p>
      <p v-if="shadeWarn" class="warn">{{ shadeWarn }}</p>
      <label class="slice-slider">
        <span>切片高度</span>
        <input
          type="range"
          :min="SLICE_Z_MIN"
          :max="SLICE_Z_MAX"
          step="0.05"
          :value="sliceZ"
          @input="onSliceInput"
        />
      </label>
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
      <p v-else class="hint">拖高度/滚轮换层 · 悬停读 R/G/B</p>
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
  top: 0.6rem;
  bottom: auto;
  max-width: 14rem;
  padding: 0.45rem 0.55rem;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--ink);
  font-size: 0.68rem;
  line-height: 1.35;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  pointer-events: none;
  box-shadow: var(--shadow-sm);
}
.crop-tip {
  position: absolute;
  z-index: 4;
  right: 0.6rem;
  top: 0.6rem;
  left: auto;
  min-width: 11rem;
  max-width: 13.5rem;
  padding: 0.5rem 0.65rem;
  background: rgba(29, 29, 31, 0.92);
  color: #f5f5f7;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: var(--shadow-sm);
  pointer-events: none;
  font-size: 0.7rem;
  line-height: 1.35;
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
  background: linear-gradient(90deg, #05070a, #3a4a58, #c8dce8, #f4fbff);
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
