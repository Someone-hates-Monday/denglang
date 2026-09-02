/** 与后端 LightFieldModel.unitLedAt / GreenhouseGeometry 对齐的灯带光学（切片重算用） */

export function isUnderShelfLamp(deviceSn: string | undefined | null): boolean {
  if (!deviceSn || deviceSn.includes('L1') || !deviceSn.includes('LAMP-')) return false
  const n = deviceSn.replace(/.*?(\d+)$/, '$1')
  const i = Number.parseInt(n, 10)
  return Number.isFinite(i) && i >= 6
}

export function designClearanceM(deviceSn: string | undefined | null): number {
  if (deviceSn?.includes('L1')) return 0.35
  if (isUnderShelfLamp(deviceSn)) return 0.30
  return 0.85
}

export function beamHalfAngleRad(deviceSn: string | undefined | null): number {
  const deg = deviceSn && (deviceSn.includes('L1') || isUnderShelfLamp(deviceSn)) ? 65 : 55
  return (deg * Math.PI) / 180
}

/** 单灯 dimming=100% 时对点 (x,y,z) 的 PPFD；灯向下照射，切片高于灯心则贡献为 0 */
export function unitLedAt(
  x: number,
  y: number,
  z: number,
  lamp: { posX?: number | null; posY?: number | null; posZ?: number | null; deviceSn?: string },
): number {
  const lx = lamp.posX ?? x
  const ly = lamp.posY ?? y
  const lz = lamp.posZ ?? 1.85
  const dx = x - lx
  const dy = y - ly
  const dz = z - lz
  const dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
  if (dist < 0.05) return 0
  const cosAim = -dz / dist
  if (cosAim <= 0.02) return 0
  const halfAng = beamHalfAngleRad(lamp.deviceSn)
  const ang = Math.acos(Math.min(1, Math.max(-1, cosAim)))
  let beam = 1
  if (ang > halfAng) {
    const soft = Math.max(0, 1 - (ang - halfAng) / (Math.PI / 2 - halfAng + 1e-6))
    beam = soft * soft
    if (beam < 0.02) return 0
  }
  const designH = Math.max(0.25, designClearanceM(lamp.deviceSn))
  const peak = 150 * designH * designH
  return (peak * cosAim) / (dist * dist) * beam
}
