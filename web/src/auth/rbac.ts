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
  { username: 'changzhang', password: 'demo123', role: 'SITE_MANAGER', blurb: '全局策略与 AUTO' },
  { username: 'nongyi', password: 'demo123', role: 'AGRONOMIST', blurb: '配方与工单审批' },
  { username: 'zhongzhi', password: 'demo123', role: 'GROWER', blurb: '接单执行与现场微调' },
  { username: 'yunwei', password: 'demo123', role: 'DEVICE_OPS', blurb: '设备档案与调试' },
  { username: 'xueyuan', password: 'demo123', role: 'TRAINEE', blurb: '只读 + 实训报告' },
  { username: 'admin', password: 'admin123', role: 'SYS_ADMIN', blurb: '账号与系统配置' },
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
    'dev.crud',
    'dev.debug',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
  ],
  TRAINEE: [
    'dash.view',
    'gh.view',
    'gh.heat',
    'wo.list',
    'log.view',
    'report.view',
    'report.write',
    'contact.send',
    'perm.request',
  ],
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

export function homePathFor(role: Role | null | undefined): string {
  switch (normalizeRole(role)) {
    case 'SITE_MANAGER':
    case 'TRAINEE':
      return '/dashboard'
    case 'AGRONOMIST':
    case 'GROWER':
      return '/greenhouse'
    case 'DEVICE_OPS':
      return '/devices'
    case 'SYS_ADMIN':
      return '/logs'
    default:
      return '/dashboard'
  }
}

export type NavItem = { to: string; label: string; icon: string; cap?: Capability }

const NAV_ALL: NavItem[] = [
  { to: '/dashboard', label: '总览', icon: '◉', cap: 'dash.view' },
  { to: '/greenhouse', label: '冠层光场', icon: '▣', cap: 'gh.view' },
  { to: '/devices', label: '设备', icon: '◎', cap: 'dev.crud' },
  { to: '/reports', label: '报告', icon: '▤', cap: 'report.view' },
  { to: '/logs', label: '控制日志', icon: '≡', cap: 'log.view' },
  { to: '/users', label: '用户', icon: '☺', cap: 'user.manage' },
]

/** 按角色裁剪导航（运维强制看到设备；种植员弱化设备入口） */
export function navFor(role: Role | null | undefined): NavItem[] {
  const r = normalizeRole(role)
  const base = NAV_ALL.filter((item) => {
    if (!item.cap) return true
    if (item.to === '/devices') {
      return r === 'DEVICE_OPS' || r === 'SYS_ADMIN' || can(r, 'dev.debug')
    }
    if (item.to === '/users') return can(r, 'user.manage')
    if (item.to === '/reports') {
      return r === 'SITE_MANAGER' || r === 'AGRONOMIST' || r === 'TRAINEE' || r === 'SYS_ADMIN'
    }
    return can(r, item.cap)
  })

  // 种植员：光场优先，无设备管理入口
  if (r === 'GROWER') {
    return base.filter((i) => i.to !== '/devices' && i.to !== '/users')
  }
  // 农艺：弱化设备
  if (r === 'AGRONOMIST') {
    return base.filter((i) => i.to !== '/devices' && i.to !== '/users')
  }
  // 学员：无日志深挖可保留查看；无用户/设备
  if (r === 'TRAINEE') {
    return base.filter((i) => i.to === '/dashboard' || i.to === '/greenhouse' || i.to === '/reports')
  }
  // 运维：设备 + 日志为主，保留光场只读入口
  if (r === 'DEVICE_OPS') {
    return base.filter((i) => i.to !== '/users' && i.to !== '/reports')
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
