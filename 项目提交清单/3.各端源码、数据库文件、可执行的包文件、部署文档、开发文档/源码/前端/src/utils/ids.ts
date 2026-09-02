/** 雪花 ID 必须当字符串用，Number() 会丢精度并导致「设备不存在」。 */
export function entityId(v: unknown): string {
  if (v == null || v === '') return ''
  return String(v)
}

export function compareEntityId(a: string, b: string): number {
  try {
    const da = BigInt(a)
    const db = BigInt(b)
    if (da === db) return 0
    return da < db ? -1 : 1
  } catch {
    return a.localeCompare(b)
  }
}
