import * as THREE from 'three'

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  const rr = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + rr, y)
  ctx.arcTo(x + w, y, x + w, y + h, rr)
  ctx.arcTo(x + w, y + h, x, y + h, rr)
  ctx.arcTo(x, y + h, x, y, rr)
  ctx.arcTo(x, y, x + w, y, rr)
  ctx.closePath()
}

function applyCrispTexture(canvas: HTMLCanvasElement): THREE.CanvasTexture {
  const tex = new THREE.CanvasTexture(canvas)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.generateMipmaps = false
  tex.minFilter = THREE.LinearFilter
  tex.magFilter = THREE.LinearFilter
  tex.anisotropy = 8
  tex.needsUpdate = true
  return tex
}

const FONT =
  '700 42px "Segoe UI", "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif'

/** 高分辨率 Canvas 精灵：关 mipmap，避免棚内标牌发糊 */
export function makeLabelSprite(text: string, worldWidth = 2.4): THREE.Sprite {
  const dpr = Math.min(typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1, 2)
  const padX = 28
  const fontSize = 42
  const measure = document.createElement('canvas').getContext('2d')!
  measure.font = FONT
  const textW = Math.ceil(measure.measureText(text).width)
  const cssW = Math.max(120, textW + padX * 2)
  const cssH = Math.ceil(fontSize * 1.65)
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(cssW * dpr)
  canvas.height = Math.ceil(cssH * dpr)
  const ctx = canvas.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, cssW, cssH)
  ctx.fillStyle = 'rgba(16, 18, 20, 0.9)'
  roundRect(ctx, 1.5, 1.5, cssW - 3, cssH - 3, 11)
  ctx.fill()
  ctx.strokeStyle = 'rgba(255,255,255,0.28)'
  ctx.lineWidth = 1.25
  ctx.stroke()
  ctx.font = FONT
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = '#f5f5f7'
  ctx.fillText(text, cssW / 2, cssH / 2 + 0.5)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: applyCrispTexture(canvas),
      transparent: true,
      depthTest: false,
      sizeAttenuation: true,
    }),
  )
  spr.scale.set(worldWidth, worldWidth * (cssH / cssW), 1)
  spr.renderOrder = 20
  return spr
}

export function makeAccentLabelSprite(text: string, accentHex: string, worldWidth = 2.6): THREE.Sprite {
  const dpr = Math.min(typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1, 2)
  const padX = 32
  const fontSize = 40
  const measure = document.createElement('canvas').getContext('2d')!
  measure.font = FONT.replace('42px', `${fontSize}px`)
  const textW = Math.ceil(measure.measureText(text).width)
  const cssW = Math.max(160, textW + padX * 2)
  const cssH = Math.ceil(fontSize * 1.7)
  const canvas = document.createElement('canvas')
  canvas.width = Math.ceil(cssW * dpr)
  canvas.height = Math.ceil(cssH * dpr)
  const ctx = canvas.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.font = FONT.replace('42px', `${fontSize}px`)
  ctx.fillStyle = 'rgba(16, 18, 20, 0.9)'
  roundRect(ctx, 1.5, 1.5, cssW - 3, cssH - 3, 11)
  ctx.fill()
  ctx.fillStyle = accentHex
  ctx.fillRect(2, 8, 7, cssH - 16)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = '#f5f5f7'
  ctx.fillText(text, cssW / 2 + 3, cssH / 2 + 0.5)
  const spr = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: applyCrispTexture(canvas),
      transparent: true,
      depthTest: false,
      sizeAttenuation: true,
    }),
  )
  spr.scale.set(worldWidth, worldWidth * (cssH / cssW), 1)
  spr.renderOrder = 20
  return spr
}
