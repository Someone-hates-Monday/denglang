/** 后端时间格式：yyyy-MM-dd HH:mm:ss */
function pad(n: number) {
  return String(n).padStart(2, '0')
}

export function formatBackendTime(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function dateKey(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function parseDateKey(key: string): Date {
  const [y, m, day] = key.split('-').map(Number)
  return new Date(y, m - 1, day)
}

/** 某日 00:00:00 — 23:59:59 */
export function dayRange(day: Date | string) {
  const base = typeof day === 'string' ? parseDateKey(day) : new Date(day)
  const start = new Date(base)
  start.setHours(0, 0, 0, 0)
  const end = new Date(base)
  end.setHours(23, 59, 59, 999)
  return { start: formatBackendTime(start), end: formatBackendTime(end) }
}

/** 当天 00:00:00 — 23:59:59 */
export function todayRange(now = new Date()) {
  return dayRange(now)
}

/** 某月首尾（含） */
export function monthRange(year: number, monthIndex: number) {
  const start = new Date(year, monthIndex, 1, 0, 0, 0, 0)
  const end = new Date(year, monthIndex + 1, 0, 23, 59, 59, 999)
  return { start: formatBackendTime(start), end: formatBackendTime(end), startDate: start, endDate: end }
}

export function extractDateKey(isoLike: string): string {
  return isoLike.slice(0, 10)
}

export function formatDisplayDate(key: string): string {
  const d = parseDateKey(key)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}
