/**
 * 后端不可用时的本地关键词兜底（旧 AgriAgent 知识库精简版）
 */
type Knowledge = { keys: string[]; topic: string; answer: string }

const KB: Knowledge[] = [
  {
    keys: ['石斛', '组培', '栽培', '光配方', 'ppfd'],
    topic: '铁皮石斛光配方',
    answer:
      '铁皮石斛：组培 60–70（硬限 50–90）PPFD；栽培 90–120（硬限 70–140）。欠光先开遮阳再补光；过光先降补光。',
  },
  {
    keys: ['草莓', '补光', 'dli'],
    topic: '设施草莓',
    answer: '设施草莓目标约 250–400 PPFD、DLI 17–25。演示主叙事为整跨石斛，草莓为配方知识。',
  },
  {
    keys: ['金线莲', '耐阴'],
    topic: '金线莲',
    answer: '金线莲耐阴，目标约 25–35 PPFD，适合配方切换对照。',
  },
  {
    keys: ['dli', '日积分', '产量指数'],
    topic: 'DLI',
    answer: 'DLI≈PPFD×光周期(h)×0.0036。economics 产量指数是 DLI 达成率，不是千克产量。',
  },
  {
    keys: ['工单', '审批', 'claim', 'approve'],
    topic: '工单',
    answer: 'PENDING→APPROVED→claim 完成。approve 不下发；种植员 claim 才下发。助手只读。',
  },
  {
    keys: ['电费', 'economics', '产量', '能耗'],
    topic: '经济性',
    answer: '电费为估算；产量指数为 DLI 达成率。欠光先开遮阳；过光先降灯。',
  },
  {
    keys: ['大棚', '尺寸', '分区', '布局'],
    topic: '棚体',
    answer: 'cq-demo-bay-v1：16×7 m。ZONE-A/B 半跨控光；演示整跨石斛。',
  },
  {
    keys: ['角色', '账号', '权限', 'rbac'],
    topic: '角色',
    answer: '六角色：场长/农艺/种植员/运维/学员/系统。演示账号见登录页卡片。',
  },
  {
    keys: ['mqtt', '接入', 'topic'],
    topic: 'MQTT',
    answer: '前缀 smart-greenhouse/：telemetry/status/alarm 上行，command 下行。EMQX :1883。',
  },
]

function normalize(q: string) {
  return q.toLowerCase().replace(/[\s，。！？、,.;:：；!?'"“”‘’（）()【】\[\]<>《》·-]+/g, '')
}

export function offlineAnswer(q: string): string {
  const t = q.trim()
  if (!t) return '请输入问题。'
  if (/(你好|您好|hello|hi)/i.test(t)) {
    return '你好（离线兜底）。可问石斛配方、工单规则、棚体尺寸等；实时棚况需后端可用。'
  }
  const nq = normalize(t)
  let best: Knowledge | null = null
  let bestScore = 0
  for (const k of KB) {
    let score = 0
    for (const key of k.keys) {
      if (nq.includes(normalize(key))) score += 1
    }
    if (score > bestScore) {
      best = k
      bestScore = score
    }
  }
  if (best) return `（离线知识）${best.answer}`
  return '（离线）未命中本地知识。请确认后端已启动后重试，或问石斛配方 / 工单 / DLI / 棚体尺寸。'
}
