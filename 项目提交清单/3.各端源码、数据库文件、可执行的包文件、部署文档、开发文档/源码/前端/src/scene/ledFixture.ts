import * as THREE from 'three'
import type { GhAssetPack } from './greenhouseAssets'

/** 园艺 LED 灯带模块（约 1.2 m 段 · 多灯珠），对齐 BOM 条形吊装而非示意大方块 */
export function makeLedStripFixture(dim: number, lengthM = 1.22): THREE.Group {
  const g = new THREE.Group()
  g.name = 'led-strip'

  const body = new THREE.Mesh(
    new THREE.BoxGeometry(lengthM, 0.018, 0.038),
    new THREE.MeshStandardMaterial({
      color: 0x1a1c1e,
      metalness: 0.55,
      roughness: 0.38,
    }),
  )
  g.add(body)

  const rail = new THREE.Mesh(
    new THREE.BoxGeometry(lengthM * 0.98, 0.006, 0.012),
    new THREE.MeshStandardMaterial({ color: 0x2c3034, metalness: 0.4, roughness: 0.45 }),
  )
  rail.position.y = 0.014
  g.add(rail)

  const beadMat = new THREE.MeshStandardMaterial({
    color: 0xffe8a8,
    emissive: 0xffcc66,
    emissiveIntensity: 0.15 + dim * 1.35,
    roughness: 0.35,
    metalness: 0.1,
  })
  const n = 14
  const span = lengthM * 0.9
  for (let i = 0; i < n; i++) {
    const bead = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.008, 0.022), beadMat)
    bead.position.set(-span / 2 + (i / (n - 1)) * span, -0.012, 0)
    g.add(bead)
  }

  const hangL = new THREE.Mesh(
    new THREE.CylinderGeometry(0.004, 0.004, 0.06, 6),
    new THREE.MeshStandardMaterial({ color: 0x3a3f44, metalness: 0.5, roughness: 0.4 }),
  )
  hangL.position.set(-lengthM * 0.35, 0.04, 0)
  g.add(hangL)
  const hangR = hangL.clone()
  hangR.position.x = lengthM * 0.35
  g.add(hangR)

  return g
}

export function placeLedStrip(
  assets: GhAssetPack | null | undefined,
  x: number,
  y: number,
  z: number,
  dim: number,
  _isL1 = false,
): THREE.Object3D {
  const length = 1.22
  if (assets?.lamp) {
    const g = assets.lamp.clone(true)
    g.scale.set(1.0, 1.0, 1.0)
    g.position.set(x, z, y)
    g.traverse((obj) => {
      const m = obj as THREE.Mesh
      if (m.isMesh && m.material && !Array.isArray(m.material)) {
        const mat = (m.material as THREE.MeshStandardMaterial).clone()
        if ('emissiveIntensity' in mat) mat.emissiveIntensity = 0.12 + dim * 1.25
        mat.depthWrite = true
        m.renderOrder = 4
        m.material = mat
      }
    })
    return g
  }
  const strip = makeLedStripFixture(dim, length)
  strip.position.set(x, z, y)
  return strip
}
