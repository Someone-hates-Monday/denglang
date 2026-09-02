import { Client } from '@stomp/stompjs'
import { defineStore } from 'pinia'
import { onScopeDispose, ref } from 'vue'
import { api } from '../api/client'
import { normalizeAlarm, normalizeLatestLight } from '../api/normalize'
import { isMockMode, wsBrokerUrl } from '../config/runtime'
import type { AlarmLog, LatestLight } from '../types/domain'
import { useAuthStore } from './auth'

/** 后端部分路径将 deviceId 序列化为 string，前端统一为 number */
function normalizeLatest(raw: LatestLight & { deviceId?: number | string }): LatestLight {
  return normalizeLatestLight(raw as unknown as Record<string, unknown>)
}

/**
 * Seam: STOMP `/ws?token=` → topics。
 * HTTP 模式从后端 REST + WebSocket 拉取；Mock 模式用定时器模拟光照推送。
 */
export const useRealtimeStore = defineStore('realtime', () => {
  const connected = ref(false)
  const latestLight = ref<LatestLight | null>(null)
  const latestAlarm = ref<AlarmLog | null>(null)
  /** 设备开关/在线状态变更时递增，供总览与设备页 watch 刷新 */
  const deviceSyncTick = ref(0)
  /** 新告警时递增，供告警页 watch 刷新 */
  const alarmSyncTick = ref(0)
  /** 光棚仿真/控制推送 */
  const greenhouseTick = ref(0)
  let client: Client | null = null
  let timer: number | undefined
  let pollTimer: number | undefined

  function bumpDeviceSync() {
    deviceSyncTick.value += 1
  }

async function seedLatestLight() {
    const list = await api.listLightReadings({ page: 1, pageSize: 1 })
    if (list.code === 200 && list.data.records.length) {
      const row = list.data.records[0]
      latestLight.value = normalizeLatest({
        deviceId: row.deviceId,
        lightIntensity: row.lightIntensity,
        createdAt: row.createdAt,
      })
      return
    }
    const devices = await api.listDevices({ page: 1, pageSize: 1 })
    if (devices.code !== 200 || !devices.data.records.length) return
    const deviceId = devices.data.records[0].id
    const latest = await api.latestLight(deviceId)
    if (latest.code === 200 && latest.data) {
      latestLight.value = normalizeLatest(latest.data)
    }
  }

  function connect() {
    disconnect()
    if (isMockMode) {
      connected.value = true
      void import('../api/mock').then(({ mockTickLight }) => {
        timer = window.setInterval(() => {
          latestLight.value = mockTickLight()
          bumpDeviceSync()
        }, 3000)
      })
      return
    }

    const auth = useAuthStore()
    const token = auth.session?.token
    if (!token) return

    void seedLatestLight()
    pollTimer = window.setInterval(() => {
      void seedLatestLight()
    }, 3000)

    client = new Client({
      brokerURL: wsBrokerUrl(token),
      reconnectDelay: 4000,
      onConnect: () => {
        connected.value = true
        client?.subscribe('/topic/light-readings', (msg) => {
          const body = JSON.parse(msg.body) as { data: LatestLight & { deviceId?: number | string } }
          latestLight.value = normalizeLatest(body.data)
        })
        client?.subscribe('/topic/alarms', (msg) => {
          const body = JSON.parse(msg.body) as { data: Record<string, unknown> }
          latestAlarm.value = normalizeAlarm(body.data)
          alarmSyncTick.value += 1
        })
        client?.subscribe('/topic/device-status', () => {
          bumpDeviceSync()
        })
        client?.subscribe('/topic/device-online', () => {
          bumpDeviceSync()
        })
        client?.subscribe('/topic/greenhouse', () => {
          greenhouseTick.value += 1
        })
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: () => {
        connected.value = false
      },
    })
    client.activate()
  }

  function disconnect() {
    if (timer) window.clearInterval(timer)
    if (pollTimer) window.clearInterval(pollTimer)
    timer = undefined
    pollTimer = undefined
    client?.deactivate()
    client = null
    connected.value = false
  }

  function clearAlarmToast() {
    latestAlarm.value = null
  }

  onScopeDispose(disconnect)

  return {
    connected,
    latestLight,
    latestAlarm,
    deviceSyncTick,
    alarmSyncTick,
    greenhouseTick,
    connect,
    disconnect,
    clearAlarmToast,
  }
})
