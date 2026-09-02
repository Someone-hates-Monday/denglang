import { ref } from 'vue'

/** 丢弃过期的并发列表请求，避免后返回的旧数据覆盖新状态 */
export function useLatestRequest() {
  let seq = 0

  async function run<T>(task: () => Promise<T>): Promise<T | undefined> {
    const current = ++seq
    const result = await task()
    if (current !== seq) return undefined
    return result
  }

  function reset() {
    seq += 1
  }

  return { run, reset }
}

/** 单行操作中的行 ID，防止连点或行错位 */
export function useRowAction() {
  const activeId = ref<string | number | null>(null)

  async function run<T>(id: string | number, task: () => Promise<T>): Promise<T | undefined> {
    if (activeId.value === id) return undefined
    activeId.value = id
    try {
      return await task()
    } finally {
      if (activeId.value === id) activeId.value = null
    }
  }

  function isActive(id: string | number) {
    return activeId.value === id
  }

  return { activeId, run, isActive }
}
