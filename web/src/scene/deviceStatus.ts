import type { GhAlarm, GhDevice, GhWorkOrder } from '../api/greenhouse'

/** 3D 设备状态色（告警 > 离线 > 待审工单 > 待接单 > 执行中） */
export type SceneStatusTone =
  | 'alarm'
  | 'offline'
  | 'wo-pending'
  | 'wo-approved'
  | 'wo-progress'
  | 'ok'

export type DeviceSceneStatus = {
  deviceSn: string
  tone: SceneStatusTone
  labelZh: string
  zoneId?: string
  deviceType?: string
  alarmIds: number[]
  workOrderIds: number[]
}

export const STATUS_GLOW_HEX: Record<SceneStatusTone, number> = {
  alarm: 0xff3b30,
  offline: 0x8e8e93,
  'wo-pending': 0xff9500,
  'wo-approved': 0x0071e3,
  'wo-progress': 0xaf52de,
  ok: 0x34c759,
}

export const STATUS_LABEL_ZH: Record<SceneStatusTone, string> = {
  alarm: '待处理告警',
  offline: '设备离线',
  'wo-pending': '待审工单',
  'wo-approved': '待接单',
  'wo-progress': '执行中',
  ok: '正常',
}

const TONE_RANK: Record<SceneStatusTone, number> = {
  alarm: 50,
  offline: 40,
  'wo-pending': 30,
  'wo-approved': 20,
  'wo-progress': 10,
  ok: 0,
}

function shadeSnForZone(zoneId: string) {
  return zoneId === 'ZONE-B' ? 'SHADE-ZONE-B' : 'SHADE-ZONE-A'
}

/** 将告警/工单/在线态汇总到各设备 SN，供三维光晕使用 */
export function buildDeviceStatusMap(
  devices: GhDevice[],
  orders: GhWorkOrder[],
  alarms: GhAlarm[],
): Record<string, DeviceSceneStatus> {
  const map: Record<string, DeviceSceneStatus> = {}

  const ensure = (sn: string, zoneId?: string | null, deviceType?: string) => {
    if (!map[sn]) {
      map[sn] = {
        deviceSn: sn,
        tone: 'ok',
        labelZh: STATUS_LABEL_ZH.ok,
        zoneId: zoneId || undefined,
        deviceType,
        alarmIds: [],
        workOrderIds: [],
      }
    }
    return map[sn]
  }

  for (const d of devices) {
    const row = ensure(d.deviceSn, d.zoneId, d.deviceType)
    if (d.onlineStatus === 'OFFLINE') {
      bump(row, 'offline')
    }
  }

  for (const a of alarms) {
    if (a.status && a.status !== 'ACTIVE') continue
    const sn =
      (a.deviceSn && a.deviceSn.trim()) ||
      (a.zoneId ? shadeSnForZone(a.zoneId) : '') ||
      ''
    if (!sn) continue
    const row = ensure(sn, a.zoneId || undefined)
    row.alarmIds.push(a.id)
    bump(row, 'alarm', a.message || STATUS_LABEL_ZH.alarm)
  }

  for (const o of orders) {
    if (!['PENDING', 'APPROVED', 'IN_PROGRESS'].includes(o.status)) continue
    // 优先挂目标设备；无 SN 时退回该区遮阳执行器（避免整区误染）
    const sn =
      (o.targetDeviceSn && o.targetDeviceSn.trim()) ||
      (o.zoneId ? shadeSnForZone(o.zoneId) : '') ||
      ''
    if (!sn) continue
    const row = ensure(sn, o.zoneId)
    row.workOrderIds.push(o.id)
    if (o.status === 'PENDING') bump(row, 'wo-pending', o.reason || STATUS_LABEL_ZH['wo-pending'])
    else if (o.status === 'APPROVED')
      bump(row, 'wo-approved', o.reason || STATUS_LABEL_ZH['wo-approved'])
    else bump(row, 'wo-progress', o.reason || STATUS_LABEL_ZH['wo-progress'])
  }

  return map
}

function bump(row: DeviceSceneStatus, tone: SceneStatusTone, label?: string) {
  if (TONE_RANK[tone] >= TONE_RANK[row.tone]) {
    row.tone = tone
    row.labelZh = label || STATUS_LABEL_ZH[tone]
  }
}

export function statusNeedsGlow(tone: SceneStatusTone) {
  return tone !== 'ok'
}
