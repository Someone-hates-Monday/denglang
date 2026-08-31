import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { can, homePathFor, normalizeRole, type Capability } from '../auth/rbac'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
    caps?: Capability[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('../layouts/AppShell.vue'),
    children: [
      { path: '', redirect: () => homePathFor(useAuthStore().role) },
      {
        path: 'dashboard',
        redirect: '/greenhouse',
      },
      {
        path: 'greenhouse',
        name: 'greenhouse',
        component: () => import('../views/GreenhouseView.vue'),
        meta: { title: '场务光场', caps: ['gh.view'] },
      },
      {
        path: 'devices',
        name: 'devices',
        component: () => import('../views/DevicesView.vue'),
        meta: { title: '设备', caps: ['dev.view', 'dev.debug', 'dev.crud'] },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('../views/ReportsView.vue'),
        meta: { title: '报告', caps: ['report.view'] },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('../views/ControlLogsView.vue'),
        meta: { title: '控制日志', caps: ['log.view'] },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { title: '用户管理', caps: ['user.manage'] },
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isAuthed && to.name === 'login') return auth.homePath
    return true
  }
  if (!auth.isAuthed) return { name: 'login', query: { redirect: to.fullPath } }

  const caps = to.meta.caps as Capability[] | undefined
  if (caps?.length) {
    const r = normalizeRole(auth.role)
    if (!caps.some((c) => can(r, c))) return auth.homePath
  }
  return true
})
