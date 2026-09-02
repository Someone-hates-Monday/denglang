/** 升级后清一次旧会话，避免仍用 Mock token 或旧缓存状态 */
const BOOT_KEY = 'streetlight.boot'
const BOOT_VERSION = '3'

export function runBootstrap() {
  if (localStorage.getItem(BOOT_KEY) === BOOT_VERSION) return
  localStorage.removeItem('streetlight.session')
  localStorage.setItem(BOOT_KEY, BOOT_VERSION)
}
