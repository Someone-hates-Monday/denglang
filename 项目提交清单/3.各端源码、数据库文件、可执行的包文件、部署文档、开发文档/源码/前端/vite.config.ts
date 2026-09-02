import { defineConfig, type ProxyOptions } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import type { IncomingMessage } from 'node:http'

/** SPA 路由与同名 API 前缀冲突时：浏览器导航（Accept: text/html）留给 Vite，XHR/fetch 仍代理到后端 */
function apiProxy(target = 'http://localhost:8080'): ProxyOptions {
  return {
    target,
    changeOrigin: true,
    bypass(req: IncomingMessage) {
      const accept = req.headers.accept ?? ''
      if (accept.includes('text/html')) return '/index.html'
    },
  }
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  define: {
    global: 'globalThis',
  },
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/users': apiProxy(),
      '/devices': apiProxy(),
      '/light-readings': apiProxy(),
      '/alarm-logs': apiProxy(),
      '/threshold-config': apiProxy(),
      '/control-logs': apiProxy(),
      '/knowledge-chunks': apiProxy(),
      '/greenhouse': apiProxy(),
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
