/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_MODE?: 'mock' | 'http'
  readonly VITE_API_BASE?: string
  readonly VITE_WS_BASE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
    admin?: boolean
  }
}
