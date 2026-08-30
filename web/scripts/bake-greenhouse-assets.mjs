/**
 * 烘培 cq-demo-bay 程序化低模 → GLB（可被 Blender 再精修后覆盖）。
 * 坐标系：西南角原点，+X 东，+Y 上，+Z 北（Three 惯例；布局 JSON 的 y=北 → Three z）
 *
 * 用法：node scripts/bake-greenhouse-assets.mjs
 */
import * as THREE from 'three'
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js'
import { Blob } from 'node:buffer'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

// Node 无 FileReader：GLTFExporter 二进制路径需要
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

function archCurve(x) {
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

function buildShell() {
  const root = new THREE.Group()
  root.name = 'tunnel-shell'

  const frameMat = new THREE.MeshStandardMaterial({
    color: 0x5a6570,
    metalness: 0.62,
    roughness: 0.28,
    name: 'frameSteel',
  })
  const bayCount = 12
  for (let i = 0; i < bayCount; i++) {
    const x = (i / (bayCount - 1)) * L
    const tube = new THREE.Mesh(new THREE.TubeGeometry(archCurve(x), 48, 0.055, 10, false), frameMat)
    tube.name = `arch-${i}`
    root.add(tube)
  }

  const ridge = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, L, 10), frameMat)
  ridge.rotation.z = Math.PI / 2
  ridge.position.set(L / 2, H, W / 2)
  ridge.name = 'ridge'
  root.add(ridge)

  for (const [zi, name] of [
    [0.1, 'gutter-s'],
    [W - 0.1, 'gutter-n'],
  ]) {
    const gutter = new THREE.Mesh(new THREE.BoxGeometry(L, 0.1, 0.14), frameMat)
    gutter.position.set(L / 2, G * 0.12, zi)
    gutter.name = name
    root.add(gutter)
  }

  // polycarbonate skin loft
  const segsX = 28
  const segsA = 32
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
  const skin = new THREE.Mesh(
    skinGeo,
    new THREE.MeshPhysicalMaterial({
      color: 0xeaf4f0,
      transparent: true,
      opacity: 0.22,
      transmission: 0.7,
      thickness: 0.4,
      roughness: 0.18,
      metalness: 0,
      side: THREE.DoubleSide,
      depthWrite: false,
      name: 'coverFilm',
    }),
  )
  skin.name = 'cover'
  root.add(skin)

  const endMat = new THREE.MeshPhysicalMaterial({
    color: 0xe8f0ec,
    transparent: true,
    opacity: 0.28,
    transmission: 0.45,
    roughness: 0.22,
    side: THREE.DoubleSide,
    name: 'endWall',
  })
  for (const [x, name] of [
    [0.06, 'end-west'],
    [L - 0.06, 'end-east'],
  ]) {
    const wall = new THREE.Mesh(new THREE.PlaneGeometry(W * 0.96, G * 0.95), endMat)
    wall.position.set(x, G * 0.48, W / 2)
    wall.rotation.y = Math.PI / 2
    wall.name = name
    root.add(wall)
  }

  const door = new THREE.Mesh(
    new THREE.BoxGeometry(0.1, 2.0, 1.05),
    new THREE.MeshStandardMaterial({ color: 0x4a5560, metalness: 0.35, roughness: 0.45, name: 'door' }),
  )
  door.position.set(0.1, 1.0, W / 2)
  door.name = 'door-west'
  root.add(door)

  const aisle = new THREE.Mesh(
    new THREE.BoxGeometry(1.0, 0.03, W * 0.9),
    new THREE.MeshStandardMaterial({ color: 0x8f9888, roughness: 0.92, name: 'aisle' }),
  )
  aisle.position.set(8, 0.015, W / 2)
  aisle.name = 'aisle'
  root.add(aisle)

  for (const [cx, name] of [
    [4, 'shade-roller-a'],
    [12, 'shade-roller-b'],
  ]) {
    const box = new THREE.Mesh(
      new THREE.BoxGeometry(7.2, 0.16, 0.2),
      new THREE.MeshStandardMaterial({ color: 0x2c3338, metalness: 0.45, roughness: 0.4 }),
    )
    box.position.set(cx, 3.52, W - 0.1)
    box.name = name
    root.add(box)
  }

  return root
}

/** 单位床：本地坐标原点在床中心地面投影，床长沿 +X，床宽沿 +Z */
function buildBedDendrobium() {
  const root = new THREE.Group()
  root.name = 'bed-dendrobium'
  const bw = 7.0
  const bd = 0.8

  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88, name: 'deck' })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.5, roughness: 0.4, name: 'leg' })
  const potMat = new THREE.MeshStandardMaterial({ color: 0x6b4e3a, roughness: 0.82, name: 'pot' })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f8a58, roughness: 0.5, name: 'leaf' })
  const trayMat = new THREE.MeshStandardMaterial({ color: 0xc5d8cc, roughness: 0.7, name: 'tray' })

  const deck0 = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.07, bd), bedMat)
  deck0.position.set(0, Z_L0, 0)
  root.add(deck0)

  for (const [ox, oz] of [
    [-bw / 2 + 0.1, -bd / 2 + 0.08],
    [bw / 2 - 0.1, -bd / 2 + 0.08],
    [-bw / 2 + 0.1, bd / 2 - 0.08],
    [bw / 2 - 0.1, bd / 2 - 0.08],
  ]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.028, Z_L1, 8), legMat)
    leg.position.set(ox, Z_L1 / 2, oz)
    root.add(leg)
  }

  let i = 0
  for (let x = -bw / 2 + 0.35; x < bw / 2 - 0.2; x += 0.4) {
    for (const row of [-0.22, 0, 0.22]) {
      i++
      const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.075, 0.1, 10), potMat)
      pot.position.set(x, Z_L0 + 0.08, row)
      root.add(pot)
      const h = 0.2 + (i % 5) * 0.015
      const leaf = new THREE.Mesh(new THREE.ConeGeometry(0.09, h, 8), leafMat)
      leaf.position.set(x, Z_L0 + 0.12 + h / 2, row)
      leaf.rotation.y = (i * 0.6) % 2
      root.add(leaf)
    }
  }

  const deck1 = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.2, 0.05, bd - 0.1), bedMat)
  deck1.position.set(0, Z_L1, 0)
  root.add(deck1)
  for (let t = 0; t < 10; t++) {
    const tray = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.04, 0.28), trayMat)
    tray.position.set(-bw / 2 + 0.55 + t * 0.65, Z_L1 + 0.05, 0)
    root.add(tray)
    for (let k = 0; k < 3; k++) {
      const sprout = new THREE.Mesh(new THREE.SphereGeometry(0.035, 8, 8), leafMat)
      sprout.position.set(-bw / 2 + 0.45 + t * 0.65 + k * 0.1, Z_L1 + 0.11, (k - 1) * 0.05)
      root.add(sprout)
    }
  }

  return root
}

function buildBedStrawberry() {
  const root = new THREE.Group()
  root.name = 'bed-strawberry'
  const bw = 7.0
  const bd = 0.8

  const bedMat = new THREE.MeshStandardMaterial({ color: 0x4a4036, roughness: 0.88 })
  const soilMat = new THREE.MeshStandardMaterial({ color: 0x3d4f38, roughness: 0.9 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3d9a5c, roughness: 0.48 })
  const berryMat = new THREE.MeshStandardMaterial({
    color: 0xd94a4a,
    roughness: 0.4,
    emissive: 0x301010,
    emissiveIntensity: 0.12,
  })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.5, roughness: 0.4 })

  const deck = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.08, bd), bedMat)
  deck.position.set(0, Z_L0, 0)
  root.add(deck)
  for (const [ox, oz] of [
    [-bw / 2 + 0.1, -bd / 2 + 0.08],
    [bw / 2 - 0.1, -bd / 2 + 0.08],
    [-bw / 2 + 0.1, bd / 2 - 0.08],
    [bw / 2 - 0.1, bd / 2 - 0.08],
  ]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.028, Z_L0, 8), legMat)
    leg.position.set(ox, Z_L0 / 2, oz)
    root.add(leg)
  }
  const trough = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.12, 0.14, bd - 0.1), soilMat)
  trough.position.set(0, Z_L0 + 0.1, 0)
  root.add(trough)

  let n = 0
  for (let x = -bw / 2 + 0.3; x < bw / 2 - 0.2; x += 0.3) {
    for (let z = -bd / 2 + 0.18; z < bd / 2 - 0.15; z += 0.22) {
      n++
      const h = 0.15 + (n % 4) * 0.02
      const tuft = new THREE.Mesh(new THREE.ConeGeometry(0.075, h, 7), leafMat)
      tuft.position.set(x, Z_L0 + 0.18 + h / 2, z)
      root.add(tuft)
      if (n % 3 === 0) {
        const berry = new THREE.Mesh(new THREE.SphereGeometry(0.03, 8, 8), berryMat)
        berry.position.set(x + 0.04, Z_L0 + 0.22 + h * 0.35, z)
        root.add(berry)
      }
    }
  }
  return root
}

function buildLampBar() {
  const root = new THREE.Group()
  root.name = 'lamp-bar'
  const body = new THREE.Mesh(
    new THREE.BoxGeometry(0.6, 0.05, 0.14),
    new THREE.MeshStandardMaterial({
      color: 0x1d1d1f,
      metalness: 0.55,
      roughness: 0.35,
      emissive: 0xffcc55,
      emissiveIntensity: 0.35,
    }),
  )
  root.add(body)
  const lens = new THREE.Mesh(
    new THREE.BoxGeometry(0.52, 0.02, 0.1),
    new THREE.MeshStandardMaterial({
      color: 0xffe8a0,
      emissive: 0xffd060,
      emissiveIntensity: 0.8,
      roughness: 0.2,
    }),
  )
  lens.position.y = -0.035
  root.add(lens)
  return root
}

function buildBedAnoectochilus() {
  const root = new THREE.Group()
  root.name = 'bed-anoectochilus'
  const bw = 7.0
  const bd = 0.8

  const bedMat = new THREE.MeshStandardMaterial({ color: 0x3d3830, roughness: 0.9 })
  const mossMat = new THREE.MeshStandardMaterial({ color: 0x1e4a32, roughness: 0.92 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x2a6b45, roughness: 0.55 })
  const veinMat = new THREE.MeshStandardMaterial({
    color: 0xc9a227,
    emissive: 0x8a7010,
    emissiveIntensity: 0.2,
    roughness: 0.4,
  })
  const legMat = new THREE.MeshStandardMaterial({ color: 0x3a3f3c, metalness: 0.5, roughness: 0.4 })

  const deck = new THREE.Mesh(new THREE.BoxGeometry(bw, 0.07, bd), bedMat)
  deck.position.set(0, Z_L0, 0)
  root.add(deck)
  for (const [ox, oz] of [
    [-bw / 2 + 0.1, -bd / 2 + 0.08],
    [bw / 2 - 0.1, -bd / 2 + 0.08],
    [-bw / 2 + 0.1, bd / 2 - 0.08],
    [bw / 2 - 0.1, bd / 2 - 0.08],
  ]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.028, Z_L0, 8), legMat)
    leg.position.set(ox, Z_L0 / 2, oz)
    root.add(leg)
  }
  // 密植垫：金线莲矮冠、叶面带金脉感
  const pad = new THREE.Mesh(new THREE.BoxGeometry(bw - 0.1, 0.05, bd - 0.08), mossMat)
  pad.position.set(0, Z_L0 + 0.06, 0)
  root.add(pad)

  let n = 0
  for (let x = -bw / 2 + 0.22; x < bw / 2 - 0.15; x += 0.22) {
    for (let z = -bd / 2 + 0.14; z < bd / 2 - 0.1; z += 0.16) {
      n++
      const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.055, 8, 6), leafMat)
      leaf.scale.set(1.2, 0.35, 0.9)
      leaf.position.set(x, Z_L0 + 0.12, z)
      root.add(leaf)
      if (n % 3 === 0) {
        const vein = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.01, 0.015), veinMat)
        vein.position.set(x, Z_L0 + 0.14, z)
        vein.rotation.y = (n * 0.4) % 1.5
        root.add(vein)
      }
    }
  }
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
await exportGlb(buildBedStrawberry(), join(OUT, 'bed-strawberry.glb'))
await exportGlb(buildBedAnoectochilus(), join(OUT, 'bed-anoectochilus.glb'))
await exportGlb(buildLampBar(), join(OUT, 'lamp-bar.glb'))

writeFileSync(
  join(OUT, 'manifest.json'),
  JSON.stringify(
    {
      layoutId: 'cq-demo-bay-v1',
      version: '1.1',
      coordinate: { origin: 'SW', x: 'east', y: 'up', z: 'north', unit: 'm' },
      assets: {
        'tunnel-shell.glb': { role: 'bayShell', lengthM: L, widthM: W, ridgeHeightM: H },
        'bed-dendrobium.glb': { role: 'bedUnit', crop: 'dendrobium', localOrigin: 'bedCenter' },
        'bed-anoectochilus.glb': { role: 'bedUnit', crop: 'anoectochilus', localOrigin: 'bedCenter' },
        'bed-strawberry.glb': { role: 'bedUnit', crop: 'strawberry', localOrigin: 'bedCenter' },
        'lamp-bar.glb': { role: 'fixture', localOrigin: 'center' },
      },
      beds: {
        ZONE_A: [
          { id: 'BED-A-S', x: 4, z: 1.4 },
          { id: 'BED-A-M', x: 4, z: 3.5 },
          { id: 'BED-A-N', x: 4, z: 5.6 },
        ],
        ZONE_B: [
          { id: 'BED-B-S', x: 12, z: 1.4 },
          { id: 'BED-B-M', x: 12, z: 3.5 },
          { id: 'BED-B-N', x: 12, z: 5.6 },
        ],
      },
      replaceWithBlender: '用 Blender 精修后覆盖同名 GLB；保持原点与轴向，见 docs/greenhouse/GLB-PIPELINE.md',
    },
    null,
    2,
  ),
)
console.log('manifest ok →', OUT)
