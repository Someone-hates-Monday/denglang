/** 智慧光棚 RBAC — 对齐 docs/greenhouse/RBAC-ROLES.md */

export type Role =
  | 'SITE_MANAGER'
  | 'AGRONOMIST'
  | 'GROWER'
  | 'DEVICE_OPS'
  | 'TRAINEE'
  | 'SYS_ADMIN'
  /** @deprecated 旧码，加载时归一化 */
  | 'ADMIN'
  | 'MUNICIPAL_STAFF'

export type Capability =
  | 'dash.view'
  | 'gh.view'
  | 'gh.heat'
  | 'auto.toggle'
  | 'recipe.bind'
  | 'recipe.edit'
  | 'climate.set'
  | 'ctrl.dim.low'
  | 'ctrl.dim.high'
  | 'ctrl.shade'
  | 'sim.reset'
  | 'wo.list'
  | 'wo.approve'
  | 'wo.reject'
  | 'wo.claim'
  | 'wo.complete'
  | 'dev.view'
  | 'dev.crud'
  | 'dev.debug'
  | 'log.view'
  | 'report.view'
  | 'report.write'
  | 'user.manage'
  | 'contact.send'
  | 'perm.request'
  | 'perm.decide'

export const ROLE_LABEL: Record<Role, string> = {
  SITE_MANAGER: '场长',
  AGRONOMIST: '农艺师',
  GROWER: '种植员',
  DEVICE_OPS: '设备运维',
  TRAINEE: '学员',
  SYS_ADMIN: '系统管理员',
  ADMIN: '系统管理员',
  MUNICIPAL_STAFF: '种植员',
}

/** 演示账号（Mock / 文档种子） */
export const DEMO_ACCOUNTS: { username: string; password: string; role: Role; blurb: string }[] = [
  {
    username: 'changzhang',
    password: 'demo123',
    role: 'SITE_MANAGER',
    blurb: '全局 AUTO / 代批工单 · 设备只读总览',
  },
  {
    username: 'nongyi',
    password: 'demo123',
    role: 'AGRONOMIST',
    blurb: '配方气候 · 审批队列 · 日报告',
  },
  {
    username: 'zhongzhi',
    password: 'demo123',
    role: 'GROWER',
    blurb: '接单执行 · 现场微调（≤80%）',
  },
  {
    username: 'yunwei',
    password: 'demo123',
    role: 'DEVICE_OPS',
    blurb: '设备调试 · 强制调光遮阳',
  },
  {
    username: 'xueyuan',
    password: 'demo123',
    role: 'TRAINEE',
    blurb: '只读观察 · 提交实训报告',
  },
  {
    username: 'admin',
    password: 'admin123',
    role: 'SYS_ADMIN',
    blurb: '全能力 · 用户管理',
  },
]

const ALL: Capability[] = [
  'dash.view',
  'gh.view',
  'gh.heat',
  'auto.toggle',
  'recipe.bind',
  'recipe.edit',
  'climate.set',
  'ctrl.dim.low',
  'ctrl.dim.high',
  'ctrl.shade',
  'sim.reset',
  'wo.list',
  'wo.approve',
  'wo.reject',
  'wo.claim',
  'wo.complete',
  'dev.view',
  'dev.crud',
  'dev.debug',
  'log.view',
  'report.view',
  'report.write',
  'user.manage',
  'contact.send',
  'perm.request',
  'perm.decide',
]

/**
 * 场长：策略与审批，设备只读（dev.view），无标定/强制调试。
 * 学员：观察 + 报告，无生产区控制、无日志深挖入口能力。
 */
const ROLE_CAPS: Record<string, Capability[]> = {
  SITE_MANAGER: [
    'dash.view',
    'gh.view',
    'gh.heat',
    'auto.toggle',
    'recipe.bind',
    'climate.set',
    'ctrl.dim.low',
    'ctrl.shade',
    'sim.reset',
    'wo.list',
    'wo.approve',
    'wo.reject',
    'dev.view',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
    'perm.decide',
  ],
  AGRONOMIST: [
    'dash.view',
    'gh.view',
    'gh.heat',
    'auto.toggle',
    'recipe.bind',
    'recipe.edit',
    'climate.set',
    'ctrl.dim.low',
    'ctrl.dim.high',
    'ctrl.shade',
    'sim.reset',
    'wo.list',
    'wo.approve',
    'wo.reject',
    'dev.view',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
    'perm.decide',
  ],
  GROWER: [
    'dash.view',
    'gh.view',
    'gh.heat',
    'ctrl.dim.low',
    'ctrl.shade',
    'wo.list',
    'wo.claim',
    'wo.complete',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
  ],
  DEVICE_OPS: [
    'dash.view',
    'gh.view',
    'gh.heat',
    'ctrl.dim.low',
    'ctrl.dim.high',
    'ctrl.shade',
    'wo.list',
    'wo.claim',
    'wo.complete',
    'dev.view',
    'dev.crud',
    'dev.debug',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
  ],
  TRAINEE: ['dash.view', 'gh.view', 'gh.heat', 'wo.list', 'report.view', 'report.write'],
  SYS_ADMIN: ALL,
}

export function normalizeRole(raw: string | null | undefined): Role {
  const r = (raw || '').toUpperCase()
  if (r === 'ADMIN' || r === 'SYS_ADMIN') return 'SYS_ADMIN'
  if (r === 'MUNICIPAL_STAFF' || r === 'GROWER') return 'GROWER'
  if (r === 'SITE_MANAGER') return 'SITE_MANAGER'
  if (r === 'AGRONOMIST') return 'AGRONOMIST'
  if (r === 'DEVICE_OPS') return 'DEVICE_OPS'
  if (r === 'TRAINEE') return 'TRAINEE'
  return 'GROWER'
}

export function can(role: Role | null | undefined, cap: Capability): boolean {
  if (!role) return false
  const key = normalizeRole(role)
  return (ROLE_CAPS[key] || []).includes(cap)
}

/** 生产区是否允许手动控灯/遮阳（学员禁止） */
export function canControlActuators(role: Role | null | undefined): boolean {
  return can(role, 'ctrl.dim.low') || can(role, 'ctrl.shade')
}

/** 设备页：只读台账 vs 调试 */
export function canDebugDevices(role: Role | null | undefined): boolean {
  return can(role, 'dev.debug')
}

export function canViewDevices(role: Role | null | undefined): boolean {
  return can(role, 'dev.view') || can(role, 'dev.debug') || can(role, 'dev.crud')
}

export function homePathFor(role: Role | null | undefined): string {
  switch (normalizeRole(role)) {
    case 'SYS_ADMIN':
    case 'SITE_MANAGER':
    case 'AGRONOMIST':
    case 'GROWER':
    case 'DEVICE_OPS':
    case 'TRAINEE':
    default:
      return '/greenhouse'
  }
}

export type NavItem = { to: string; label: string; icon: string; cap?: Capability }

const NAV_ALL: NavItem[] = [
  { to: '/greenhouse', label: '场务光场', icon: '▣', cap: 'gh.view' },
  { to: '/devices', label: '设备', icon: '◎', cap: 'dev.view' },
  { to: '/reports', label: '报告', icon: '▤', cap: 'report.view' },
  { to: '/logs', label: '控制日志', icon: '≡', cap: 'log.view' },
  { to: '/users', label: '用户', icon: '☺', cap: 'user.manage' },
]

/** 按角色裁剪导航 */
export function navFor(role: Role | null | undefined): NavItem[] {
  const r = normalizeRole(role)
  const base = NAV_ALL.filter((item) => {
    if (!item.cap) return true
    if (item.to === '/devices') return canViewDevices(r)
    if (item.to === '/users') return can(r, 'user.manage')
    if (item.to === '/logs') return can(r, 'log.view')
    if (item.to === '/reports') return can(r, 'report.view')
    return can(r, item.cap)
  })

  // 学员：仅光场观察 + 报告
  if (r === 'TRAINEE') {
    return base.filter((i) => i.to === '/greenhouse' || i.to === '/reports')
  }
  // 种植员：光场优先，无用户；设备页无入口（无 dev.view）
  if (r === 'GROWER') {
    return base.filter((i) => i.to !== '/users')
  }
  // 农艺：可看设备只读总览，无用户
  if (r === 'AGRONOMIST') {
    return base.filter((i) => i.to !== '/users')
  }
  // 场长：光场 + 设备只读 + 报告 + 日志
  if (r === 'SITE_MANAGER') {
    return base.filter((i) => i.to !== '/users')
  }
  // 运维：设备为主，保留光场/日志/报告
  if (r === 'DEVICE_OPS') {
    return base.filter((i) => i.to !== '/users')
  }
  return base
}

/** 注册可选角色（禁止自选系统管理员） */
export const REGISTERABLE_ROLES: Role[] = [
  'SITE_MANAGER',
  'AGRONOMIST',
  'GROWER',
  'DEVICE_OPS',
  'TRAINEE',
]

export function roleFocusZh(role: Role | null | undefined): string {
  switch (normalizeRole(role)) {
    case 'SITE_MANAGER':
      return '全局策略 · 设备只读 · 代批紧急工单'
    case 'AGRONOMIST':
      return '配方气候 · 审批 · 日光合报告'
    case 'GROWER':
      return '接单执行 · 现场微调'
    case 'DEVICE_OPS':
      return '设备台账与强制调试'
    case 'TRAINEE':
      return '只读观察 · 实训报告'
    case 'SYS_ADMIN':
      return '全系统配置'
    default:
      return ''
  }
}
