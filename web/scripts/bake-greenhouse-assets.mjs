/**
 * 方案 A · stylized ag-tech 低模 GLB 烘培
 * 统一 KayKit 式扁平配色 · 整跨石斛 · 可被 Blender 覆盖同名文件
 *
 * 用法：npm run bake:glb
 */
import * as THREE from 'three'
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js'
import { Blob } from 'node:buffer'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

if (typeof globalThis.FileReader === 'undefined') {
  globalThis.Blob = Blob
  globalThis.FileReader = class FileReader {
    result = null
    onload = null
    onerror = null
    onloadend = null
    readAsArrayBuffer(blob) {
      Promise.resolve(blob.arrayBuffer())
        .then((ab) => {
          this.result = ab
          const ev = { target: this }
          this.onload?.(ev)
          this.onloadend?.(ev)
        })
        .catch((err) => {
          this.onerror?.(err)
          this.onloadend?.({ target: this })
        })
    }
  }
}

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, '../public/models/cq-demo-bay')

const L = 16
const W = 7
const G = 2.8
const H = 3.8
const Z_L0 = 0.55
const Z_L1 = 1.25

/** 冻结调色板 — 全场景共用（方案 A） */
const PAL = {
  frame: 0x3d4a56,
  frameDark: 0x2a343c,
  wood: 0x8b6914,
  woodLight: 0xa08030,
  glass: 0xc8e6d4,
  glassEdge: 0x9ab8a8,
  gravel: 0x9a9488,
  soil: 0x5c4a38,
  planter: 0x6b5344,
  planterRim: 0x7a6352,
  leafA: 0x3d9a58,
  leafB: 0x2f7a48,
  leafC: 0x52b86a,
  stem: 0x4a7a42,
  pot: 0xb8734a,
  potDark: 0x8f5a35,
  tray: 0xd4e8dc,
  lampBody: 0x252528,
  lampGlow: 0xffe082,
  shadeRoll: 0x1e2428,
}

function mat(name, color, opts = {}) {
  return new THREE.MeshStandardMaterial({
    color,
    roughness: opts.roughness ?? 0.82,
    metalness: opts.metalness ?? 0.08,
    flatShading: true,
    name,
    ...opts,
  })
}

function glassMat(name, opacity = 0.2) {
  return new THREE.MeshPhysicalMaterial({
    color: PAL.glass,
    transparent: true,
    opacity,
    transmission: 0.55,
    thickness: 0.25,
    roughness: 0.15,
    metalness: 0,
    flatShading: true,
    side: THREE.DoubleSide,
    depthWrite: false,
    name,
  })
}

function archCurve(x) {
  return new THREE.CatmullRomCurve3([
    new THREE.Vector3(x, 0.05, 0),
    new THREE.Vector3(x, G * 0.5, W * 0.1),
    new THREE.Vector3(x, G * 0.92, W * 0.24),
    new THREE.Vector3(x, H * 0.98, W * 0.5),
    new THREE.Vector3(x, G * 0.92, W * 0.76),
    new THREE.Vector3(x, G * 0.5, W * 0.9),
    new THREE.Vector3(x, 0.05, W),
  ])
}

/** 石斛丛模块 A/B — 各 ~50–70 tris，供阵列实例 */
function buildOrchidClump(variant = 0) {
  const g = new THREE.Group()
  g.name = variant ? 'orchid-clump-b' : 'orchid-clump-a'
  const leafMats = [
    mat('leaf-a', PAL.leafA, { roughness: 0.72 }),
    mat('leaf-b', PAL.leafB, { roughness: 0.75 }),
    mat('leaf-c', PAL.leafC, { roughness: 0.7 }),
  ]
  const stemMat = mat('stem', PAL.stem, { roughness: 0.85 })

  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.018, 0.14, 5), stemMat)
  stem.position.y = 0.07
  g.add(stem)

  const configs =
    variant === 0
      ? [
          [0, 0.16, 0, 0.11, 0.32, 0.04, 0],
          [0.06, 0.14, 0.02, 0.09, 0.28, 0.035, 0.4],
          [-0.05, 0.15, -0.02, 0.1, 0.3, 0.038, -0.5],
          [0.02, 0.12, -0.04, 0.08, 0.22, 0.03, 1.1],
        ]
      : [
          [0, 0.17, 0.01, 0.1, 0.34, 0.042, 0.2],
          [-0.04, 0.13, 0.03, 0.085, 0.26, 0.032, -0.7],
          [0.05, 0.15, -0.03, 0.095, 0.29, 0.036, 0.9],
        ]

  configs.forEach(([x, y, z, w, h, d, ry], i) => {
    const leaf = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), leafMats[i % 3])
    leaf.position.set(x, y, z)
    leaf.rotation.y = ry
    leaf.rotation.z = (variant ? -0.25 : 0.2) + i * 0.08
    g.add(leaf)
  })

  return g
}

function buildShell() {
  const root = new THREE.Group()
  root.name = 'tunnel-shell'

  const frameMat = mat('frameSteel', PAL.frame, { metalness: 0.35, roughness: 0.45 })
  const frameDark = mat('frameDark', PAL.frameDark, { metalness: 0.4, roughness: 0.5 })
  const woodMat = mat('woodGutter', PAL.wood, { roughness: 0.88 })
  const floorMat = mat('interiorFloor', PAL.gravel, { roughness: 0.96 })

  const bayCount = 9
  for (let i = 0; i < bayCount; i++) {
    const x = (i / (bayCount - 1)) * L
    const tube = new THREE.Mesh(new THREE.TubeGeometry(archCurve(x), 24, 0.048, 6, false), frameMat)
    tube.name = `arch-${i}`
    root.add(tube)
  }

  const ridge = new THREE.Mesh(new THREE.CylinderGeometry(0.042, 0.042, L, 8), frameDark)
  ridge.rotation.z = Math.PI / 2
  ridge.position.set(L / 2, H * 0.98, W / 2)
  ridge.name = 'ridge'
  root.add(ridge)

  for (const [zi, name] of [
    [0.12, 'gutter-s'],
    [W - 0.12, 'gutter-n'],
  ]) {
    const gutter = new THREE.Mesh(new THREE.BoxGeometry(L, 0.08, 0.12), woodMat)
    gutter.position.set(L / 2, G * 0.1, zi)
    gutter.name = name
    root.add(gutter)
  }

  const segsX = 20
  const segsA = 20
  const positions = []
  const indices = []
  for (let ix = 0; ix <= segsX; ix++) {
    const x = (ix / segsX) * L
    const pts = archCurve(x).getPoints(segsA)
    for (const p of pts) positions.push(p.x, p.y, p.z)
  }
  const row = segsA + 1
  for (let ix = 0; ix < segsX; ix++) {
    for (let ia = 0; ia < segsA; ia++) {
      const a = ix * row + ia
      const b = a + row
      indices.push(a, b, a + 1, b, b + 1, a + 1)
    }
  }
  const skinGeo = new THREE.BufferGeometry()
  skinGeo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  skinGeo.setIndex(indices)
  skinGeo.computeVertexNormals()
  const skin = new THREE.Mesh(skinGeo, glassMat('coverFilm', 0.18))
  skin.name = 'cover'
  root.add(skin)

  const endMat = glassMat('endWall', 0.22)
  for (const [x, name] of [
    [0.05, 'end-west'],
    [L - 0.05, 'end-east'],
  ]) {
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.94, G * 0.9, 1, 1), endMat)
    wall.position.set(x, G * 0.45, W / 2)
    wall.rotation.y = Math.PI / 2
    wall.name = name
    root.add(wall)
  }

  const door = new THREE.Mesh(
    new THREE.BoxGeometry(0.08, 1.85, 0.95),
    mat('door', PAL.frameDark, { roughness: 0.55 }),
  )
  door.position.set(0.08, 0.92, W / 2)
  door.name = 'door-west'
  root.add(door)

  const aisle = new THREE.Mesh(new THREE.BoxGeometry(0.95, 0.025, W * 0.88), floorMat)
  aisle.position.set(8, 0.012, W / 2)
  aisle.name = 'aisle'
  root.add(aisle)

  for (const [cx, name] of [
    [4, 'shade-roller-a'],
    [12, 'shade-roller-b'],
  ]) {
    const box = new THREE.Mesh(
      new THREE.BoxGeometry(7.0, 0.12, 0.18),
      mat('shadeRoll', PAL.shadeRoll, { metalness: 0.25, roughness: 0.55 }),
    )
    box.position.set(cx, 3.48, W - 0.08)
    box.name = name
    root.add(box)
  }

  // 半跨分隔矮柱（视觉）
  for (const z of [1.4, 3.5, 5.6]) {
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.06, 2.2, 0.06), frameDark)
    post.position.set(8, 1.1, z)
    post.name = `aisle-post-${z}`
    root.add(post)
  }

  return root
}

/** 单位床：tier-l0 必显；tier-l1 仅 A 中/北床在运行时显示 */
function buildBedDendrobium() {
  const root = new THREE.Group()
  root.name = 'bed-dendrobium'
  const bw = 7.0
  const bd = 0.8

  const tierL0 = new THREE.Group()
  tierL0.name = 'tier-l0'

  const legMat = mat('leg', PAL.frameDark, { metalness: 0.3, roughness: 0.5 })
  const rimMat = mat('planterRim', PAL.planterRim, { roughness: 0.78 })
  const deckMat = mat('planterDeck', PAL.planter, { roughness: 0.85 })
  const soilMat = mat('soil', PAL.soil, { roughness: 0.95 })
  const potMat = mat('pot', PAL.pot, { roughness: 0.8 })
  const potIn = mat('potInner', PAL.potDark, { roughness: 0.88 })

  // 高架栽培槽（stylized 长条 planter）
  const deck = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.06, bd), deckMat)
  deck.position.set(0, Z_L0 - 0.03, 0)
  tierL0.add(deck)
  const rimS = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.04, 0.04), rimMat)
  rimS.position.set(0, Z_L0 + 0.01, bd / 2 - 0.02)
  tierL0.add(rimS)
  const rimN = rimS.clone()
  rimN.position.z = -bd / 2 + 0.02
  tierL0.add(rimN)

  for (const [ox, oz] of [
    [-bw / 2 + 0.12, -bd / 2 + 0.1],
    [bw / 2 - 0.12, -bd / 2 + 0.1],
    [-bw / 2 + 0.12, bd / 2 - 0.1],
    [bw / 2 - 0.12, bd / 2 - 0.1],
  ]) {
    const leg = new THREE.Mesh(new THREE.BoxGeometry(0.05, Z_L0, 0.05), legMat)
    leg.position.set(ox, Z_L0 / 2, oz)
    tierL0.add(leg)
  }

  const soil = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.14, 0.04, bd - 0.1), soilMat)
  soil.position.set(0, Z_L0 + 0.02, 0)
  tierL0.add(soil)

  const clumpA = buildOrchidClump(0)
  const clumpB = buildOrchidClump(1)
  let i = 0
  for (let x = -bw / 2 + 0.38; x < bw / 2 - 0.22; x += 0.42) {
    for (const row of [-0.2, 0.08]) {
      i++
      const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.055, 0.065, 0.09, 6), potMat)
      pot.position.set(x, Z_L0 + 0.06, row)
      tierL0.add(pot)
      const inner = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.05, 0.03, 6), potIn)
      inner.position.set(x, Z_L0 + 0.1, row)
      tierL0.add(inner)
      const clump = (i % 2 ? clumpB : clumpA).clone(true)
      clump.position.set(x, Z_L0 + 0.1, row)
      clump.rotation.y = (i * 0.55) % (Math.PI * 2)
      clump.scale.setScalar(0.85 + (i % 3) * 0.06)
      tierL0.add(clump)
    }
  }

  root.add(tierL0)

  const tierL1 = new THREE.Group()
  tierL1.name = 'tier-l1'
  const shelfMat = mat('shelf', PAL.woodLight, { roughness: 0.86 })
  const trayMat = mat('tray', PAL.tray, { roughness: 0.75 })

  const shelf = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.16, 0.04, bd - 0.12), shelfMat)
  shelf.position.set(0, Z_L1, 0)
  tierL1.add(shelf)
  for (const ox of [-bw / 2 + 0.15, bw / 2 - 0.15]) {
    const strut = new THREE.Mesh(new THREE.BoxGeometry(0.04, Z_L1 - Z_L0, 0.04), legMat)
    strut.position.set(ox, (Z_L0 + Z_L1) / 2, 0)
    tierL1.add(strut)
  }
  for (let t = 0; t < 9; t++) {
    const tray = new THREE.Mesh(new THREE.BoxGeometry(0.38, 0.035, 0.26), trayMat)
    tray.position.set(-bw / 2 + 0.55 + t * 0.72, Z_L1 + 0.04, 0)
    tierL1.add(tray)
    for (let k = 0; k < 2; k++) {
      const sprout = buildOrchidClump(k).clone(true)
      sprout.scale.setScalar(0.45)
      sprout.position.set(-bw / 2 + 0.48 + t * 0.72 + k * 0.12, Z_L1 + 0.08, (k - 0.5) * 0.08)
      tierL1.add(sprout)
    }
  }
  root.add(tierL1)

  return root
}

function buildLampBar() {
  const root = new THREE.Group()
  root.name = 'lamp-bar'
  const body = new THREE.Mesh(
    new THREE.BoxGeometry(0.55, 0.045, 0.12),
    mat('lampBody', PAL.lampBody, { metalness: 0.4, roughness: 0.4 }),
  )
  root.add(body)
  const hang = new THREE.Mesh(
    new THREE.BoxGeometry(0.04, 0.08, 0.04),
    mat('lampHang', PAL.frameDark, { metalness: 0.35, roughness: 0.5 }),
  )
  hang.position.y = 0.06
  root.add(hang)
  const lens = new THREE.Mesh(
    new THREE.BoxGeometry(0.48, 0.018, 0.085),
    mat('lampLens', PAL.lampGlow, {
      roughness: 0.25,
      emissive: PAL.lampGlow,
      emissiveIntensity: 0.65,
    }),
  )
  lens.position.y = -0.028
  root.add(lens)
  return root
}

async function exportGlb(object, filePath) {
  const exporter = new GLTFExporter()
  const ab = await new Promise((resolve, reject) => {
    exporter.parse(
      object,
      (result) => {
        if (result instanceof ArrayBuffer) resolve(result)
        else reject(new Error('expected binary GLB'))
      },
      (err) => reject(err),
      { binary: true },
    )
  })
  writeFileSync(filePath, Buffer.from(ab))
  console.log('wrote', filePath, `(${(ab.byteLength / 1024).toFixed(1)} KB)`)
}

mkdirSync(OUT, { recursive: true })
await exportGlb(buildShell(), join(OUT, 'tunnel-shell.glb'))
await exportGlb(buildBedDendrobium(), join(OUT, 'bed-dendrobium.glb'))
await exportGlb(buildLampBar(), join(OUT, 'lamp-bar.glb'))

writeFileSync(
  join(OUT, 'manifest.json'),
  JSON.stringify(
    {
      layoutId: 'cq-demo-bay-v1',
      version: '1.2-aesthetic-a',
      aesthetic: 'stylized-ag-tech',
      coordinate: { origin: 'SW', x: 'east', y: 'up', z: 'north', unit: 'm' },
      assets: {
        'tunnel-shell.glb': { role: 'bayShell', lengthM: L, widthM: W, ridgeHeightM: H },
        'bed-dendrobium.glb': {
          role: 'bedUnit',
          crop: 'dendrobium',
          localOrigin: 'bedCenter',
          tiers: ['tier-l0', 'tier-l1'],
        },
        'lamp-bar.glb': { role: 'fixture', localOrigin: 'center' },
      },
      beds: {
        ZONE_A: [
          { id: 'BED-A-S', x: 4, z: 1.4, tiers: ['l0'] },
          { id: 'BED-A-M', x: 4, z: 3.5, tiers: ['l0', 'l1'] },
          { id: 'BED-A-N', x: 4, z: 5.6, tiers: ['l0', 'l1'] },
        ],
        ZONE_B: [
          { id: 'BED-B-S', x: 12, z: 1.4, tiers: ['l0'] },
          { id: 'BED-B-M', x: 12, z: 3.5, tiers: ['l0'] },
          { id: 'BED-B-N', x: 12, z: 5.6, tiers: ['l0'] },
        ],
      },
      notesZh: '方案 A：统一低多边形 stylized 资产；tier-l1 仅西半跨中北床启用',
    },
    null,
    2,
  ),
)
console.log('manifest ok →', OUT)
