/** 开发环境默认连真后端；仅显式 VITE_API_MODE=mock 时才用内存 Mock */
export const apiMode =
  (import.meta.env.VITE_API_MODE as string) || (import.meta.env.DEV ? 'http' : 'mock')
export const isHttpMode = apiMode === 'http'
export const isMockMode = !isHttpMode

export function isMockToken(token: string | null | undefined): boolean {
  return !!token && token.startsWith('mock-')
}

/** STOMP broker URL：开发环境默认走 Vite `/ws` 代理，避免直连 8080 的跨域/IPv6 问题 */
export function wsBrokerUrl(token: string): string {
  const configured = (import.meta.env.VITE_WS_BASE as string) || ''
  if (configured) {
    return `${configured.replace(/\/$/, '')}/ws?token=${encodeURIComponent(token)}`
  }
  if (import.meta.env.DEV) {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${proto}//${location.host}/ws?token=${encodeURIComponent(token)}`
  }
  return `ws://${location.hostname}:8080/ws?token=${encodeURIComponent(token)}`
}
