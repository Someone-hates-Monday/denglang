import * as THREE from 'three'

/**
 * 布局 ↔ Three 坐标（与 cq-demo-bay-v1.json、GLB-PIPELINE 一致）
 *
 * 布局（仿真/API）：+X 东、+Y 北、+Z 上；原点西南角地坪
 * Three.js：position (layoutX, layoutZ, layoutY)
 */
export function layoutToThree(xEast: number, yNorth: number, zUp: number): THREE.Vector3 {
  return new THREE.Vector3(xEast, zUp, yNorth)
}

/** 太阳方位：自北顺时针（°）；正午重庆示范 ≈180°（偏南） */
export function sunDirectionEnu(azFromNorthDeg: number, elevDeg: number): {
  east: number
  north: number
  up: number
} {
  const az = (azFromNorthDeg * Math.PI) / 180
  const el = (elevDeg * Math.PI) / 180
  const horiz = Math.cos(el)
  return {
    east: Math.sin(az) * horiz,
    north: Math.cos(az) * horiz,
    up: Math.sin(el),
  }
}

/** 从棚心指向太阳的单位向量（Three 坐标） */
export function sunDirectionThree(azFromNorthDeg: number, elevDeg: number): THREE.Vector3 {
  const d = sunDirectionEnu(azFromNorthDeg, elevDeg)
  return new THREE.Vector3(d.east, d.up, d.north).normalize()
}

/** 光线传播方向：太阳 → 冠层（Three） */
export function sunLightRayThree(azFromNorthDeg: number, elevDeg: number): THREE.Vector3 {
  return sunDirectionThree(azFromNorthDeg, elevDeg).negate()
}

export function azimuthLabelZh(azFromNorthDeg: number): string {
  const az = ((azFromNorthDeg % 360) + 360) % 360
  if (az >= 337.5 || az < 22.5) return '偏北'
  if (az < 67.5) return '偏东北'
  if (az < 112.5) return '偏东'
  if (az < 157.5) return '偏东南'
  if (az < 202.5) return '偏南'
  if (az < 247.5) return '偏西南'
  if (az < 292.5) return '偏西'
  return '偏西北'
}

/** 默认相机：南侧外俯视，面北（西在左、东在右） */
export function defaultCameraPose(lengthM: number, widthM: number, ridgeM: number) {
  return {
    position: layoutToThree(lengthM * 1.05, -widthM * 1.15, ridgeM * 1.85),
    target: layoutToThree(lengthM / 2, widthM / 2, 1.15),
  }
}
