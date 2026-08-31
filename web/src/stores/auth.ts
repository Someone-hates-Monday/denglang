import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api/client'
import { isHttpMode, isMockToken } from '../config/runtime'
import {
  can as canCap,
  DEMO_ACCOUNTS,
  homePathFor,
  normalizeRole,
  ROLE_LABEL,
  type Capability,
  type Role,
} from '../auth/rbac'
import type { UserSession } from '../types/domain'

const STORAGE_KEY = 'streetlight.session'

export const useAuthStore = defineStore('auth', () => {
  const session = ref<UserSession | null>(readSession())

  const isAuthed = computed(() => !!session.value?.token)
  const role = computed(() => (session.value ? normalizeRole(session.value.role) : null))
  const roleLabel = computed(() => (role.value ? ROLE_LABEL[role.value] : ''))
  const isAdmin = computed(() => role.value === 'SYS_ADMIN')
  const homePath = computed(() => homePathFor(role.value))

  function can(cap: Capability) {
    return canCap(role.value, cap)
  }

  async function login(username: string, password: string) {
    if (!username.trim() || !password.trim()) throw new Error('请输入用户名和密码')
    const res = await api.login(username.trim(), password.trim())
    if (res.code !== 200) throw new Error(res.errorMsg || '登录失败')
    const data = applyDemoRole(res.data)
    session.value = data
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  }

  async function register(username: string, password: string, role: Role = 'GROWER') {
    if (!username.trim() || !password.trim()) throw new Error('请输入用户名和密码')
    const res = await api.register(username.trim(), password.trim(), normalizeRole(role))
    if (res.code !== 200) throw new Error(res.errorMsg || '注册失败')
  }

  async function logout() {
    session.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    session,
    isAuthed,
    role,
    roleLabel,
    isAdmin,
    homePath,
    can,
    login,
    register,
    logout,
  }
})

/** 演示账号优先用文档角色；旧 ADMIN / MUNICIPAL_STAFF 归一化 */
function applyDemoRole(raw: UserSession): UserSession {
  const demo = DEMO_ACCOUNTS.find((a) => a.username === raw.username)
  const role = demo ? demo.role : normalizeRole(raw.role)
  return { ...raw, role }
}

function readSession(): UserSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as UserSession
    if (isHttpMode && isMockToken(session.token)) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return applyDemoRole(session)
  } catch {
    return null
  }
}
