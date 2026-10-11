/**
 * POST + SSE 的最小实现（fetch 版）。
 *
 * 为什么不用原生 EventSource：
 *   1. 它发不了 Authorization 头，而 AuthInterceptor 只认这个头 —— 要为它单开 ?token= 旁路，
 *      等于把全部接口的安全面一起拉低；
 *   2. 它断线会自动重连，AI 接口每次重连都是一次真实计费调用；
 *   3. 它只能 GET，技能数组得塞进 query string。
 * fetch + ReadableStream 三个问题一起解决，代价是自己分帧（见下）。
 */
import { useUserStore } from '../store/user'
import { getApiBase } from './apiBase'
import { MOCK_AI_PREP, MOCK_AI_PREP_FOCUS, MOCK_JOBS } from './demoData'
import { JOB_FAMILIES, parseJsonField, READY_JOBS } from './jobs'
import { demoState } from './offlineDemo'

/** 解析一帧（已经按空行切好）并派发给同名回调 */
function dispatch(frame, handlers) {
  let event = 'message'
  const dataLines = []

  for (const line of frame.split('\n')) {
    if (!line || line.startsWith(':')) continue // 空行、": keep-alive" 注释
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1) // 规范规定冒号后可选一个空格
    if (field === 'event') event = value
    else if (field === 'data') dataLines.push(value)
  }

  if (!dataLines.length) return
  let payload = {}
  const raw = dataLines.join('\n')
  try {
    payload = raw ? JSON.parse(raw) : {}
  } catch {
    payload = {}
  }
  const fn = handlers[event]
  if (fn) fn(payload)
}

/**
 * 向 /api{path} 发 POST，按 SSE 逐帧回调。
 *
 * @param {string} path      以 / 开头的后端路径，不含 /api 前缀
 * @param {object} payload   JSON 请求体
 * @param {object} handlers  形如 { meta, delta, fallback, done }
 * @param {object} [options] { signal } —— 外部 AbortController
 * @returns {Promise<void>}  流正常读完 resolve；网络/HTTP 错误 reject
 */
export async function postSse(path, payload, handlers = {}, options = {}) {
  if (demoState.active) return playMockSse(path, payload, handlers, options)

  const store = useUserStore()
  const apiBaseUrl = getApiBase()
  // 刻意不设 Accept：浏览器默认的 */* 既能匹配 produces=text/event-stream，
  // 又能在参数校验失败时让后端正常返回 JSON 错误体（写死 Accept 会变成 406）
  const res = await fetch(`${apiBaseUrl}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(store.token ? { Authorization: `Bearer ${store.token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal: options.signal,
  })

  if (!res.ok || !res.body) {
    throw new Error(`SSE 连接失败（HTTP ${res.status}）`)
  }

  // 后端把参数校验失败等异常交给 GlobalExceptionHandler，返回的是 JSON 而非事件流。
  // 此时 HTTP 仍是 200，不校验类型就会把 {"code":400,...} 当 SSE 硬解析（没有 data: 行，
  // 结果是一句都读不到、静默降级）。这里把真实原因抛出去，调用方至少能记进控制台。
  const ctype = res.headers.get('content-type') || ''
  if (!ctype.includes('text/event-stream')) {
    let detail = ''
    try {
      detail = (await res.json())?.message || ''
    } catch {
      /* 不是 JSON 就算了 */
    }
    throw new Error(detail || `SSE 响应类型异常（${ctype || '未知'}）`)
  }

  const reader = res.body.getReader()
  // stream:true：一个中文汉字可能横跨两个 chunk，非流式解码会切出乱码
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')

      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        dispatch(frame, handlers)
      }
    }
    // 服务端若没以空行收尾，最后残留在缓冲区里的一帧也要处理
    if (buffer.trim()) dispatch(buffer, handlers)
  } finally {
    try {
      reader.releaseLock()
    } catch {
      /* 已释放 / 已中止，忽略 */
    }
  }
}

/* ================================================================== */
/*  离线演示模式：假的事件流                                           */
/* ================================================================== */
/**
 * 演示模式下不发 fetch，用定时器把预置话术逐帧喂给 dispatch()。
 *
 * 复用上面那个 dispatch 而不是另写一套解析，是刻意的：真流式和假流式走同一条
 * 派发路径，演示时看到的渲染行为（打字机、意图落点、收尾）就跟真的一模一样，
 * 不会出现「有网好看、没网露馅」。
 */
function mockFrame(event, payload) {
  return `event: ${event}\ndata: ${JSON.stringify(payload)}`
}

/** 可被 AbortSignal 打断的 sleep */
function mockWait(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(Object.assign(new Error('已中止'), { name: 'AbortError' }))
      return
    }
    const onAbort = () => {
      clearTimeout(timer)
      reject(Object.assign(new Error('已中止'), { name: 'AbortError' }))
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

/** 按标点切成 1~4 个字一帧，模拟真流式的吐字节奏 */
function mockChunks(text) {
  const chunks = []
  let rest = String(text || '')
  while (rest) {
    const stop = rest.search(/[，。、；：？！,.]/)
    const size = stop >= 0 && stop < 5 ? stop + 1 : Math.min(3, rest.length)
    chunks.push(rest.slice(0, size))
    rest = rest.slice(size)
  }
  return chunks
}

/** 口语 → 岗位。命中岗位名/code 算确信，只命中一两个技能词就只能算猜 */
function guessJob(message) {
  const text = String(message || '').toLowerCase()
  let best = null
  let bestScore = 0
  for (const job of MOCK_JOBS) {
    if (!READY_JOBS.has(job.code)) continue
    let score = 0
    if (text.includes(job.name.toLowerCase())) score += 10
    if (text.includes(job.code.toLowerCase())) score += 10
    for (const keyword of parseJsonField(job.keywords)) {
      const word = String(keyword).toLowerCase()
      if (word && text.includes(word)) score += 1
    }
    if (score > bestScore) {
      bestScore = score
      best = job
    }
  }
  return best ? { job: best, confident: bestScore >= 10 } : null
}

const FAMILY_HINTS = [
  ['后端开发', ['后端', '服务端', 'backend']],
  ['全栈开发', ['全栈', 'fullstack', 'full-stack']],
  ['前端与客户端开发', ['前端', '客户端', '安卓', 'android', 'ios', '小程序']],
  ['算法与人工智能', ['算法', '人工智能', '大模型', '机器学习']],
  ['产品经理', ['产品经理', '产品岗']],
  ['数据分析', ['数据分析', '分析师', '数分']],
  ['软件测试', ['测试', '测开']],
]

function guessFamily(message) {
  const text = String(message || '').toLowerCase()
  const hit = FAMILY_HINTS.find(([, words]) => words.some((word) => text.includes(word)))
  // 用 JOB_FAMILIES 里的 code 回写，保证和 job.family 字段是同一套取值
  return hit && JOB_FAMILIES.some((family) => family.code === hit[0]) ? hit[0] : null
}

/**
 * 攒这一轮要说的话和要下的意图。
 *
 * 只有开场那一轮（没有 message）才用阶段预置话术和它的 act；用户说了话就以
 * 用户那句话为准——不然用户随便说句什么，界面都会弹一条驴唇不对马嘴的确认栏。
 */
function buildMockReply(payload) {
  const stage = String(payload?.stage || 'GREET').toUpperCase()
  const script = MOCK_AI_PREP[stage] || MOCK_AI_PREP.GREET
  const message = String(payload?.message || '').trim()

  if (!message) return { text: script.text, act: script.act }

  if (stage === 'GREET' || stage === 'PICK_JOB') {
    const guess = guessJob(message)
    if (guess) {
      const focus = MOCK_AI_PREP_FOCUS[guess.job.code] || ''
      return {
        text: `${focus}确定的话我就按「${guess.job.name}」给你出题了。`,
        act: {
          intent: 'PICK_JOB',
          jobCode: guess.job.code,
          jobName: guess.job.name,
          // 只有说清了岗位名才敢自动跳；只说了个技术词就走确认栏，让人来拍板
          confidence: guess.confident ? 'HIGH' : 'LOW',
        },
      }
    }
    const family = guessFamily(message)
    if (family) {
      return {
        text: `${family}方向可以，岗位我列在下面了，你挑一个更具体的。`,
        act: { intent: 'PICK_JOB', family, confidence: 'LOW' },
      }
    }
  }

  if (stage === 'UPLOAD' && /没有|没写|没带|在线|填一份|现写/.test(message)) {
    return {
      text: '没有文件也没关系，在线填一份就行——岗位、技能、项目经历三栏，填完效果一样。',
      act: { intent: 'NO_RESUME', confidence: 'LOW' },
    }
  }

  return {
    text: '收到。这一步按上面说的来就行，有问题随时打断我。',
  }
}

async function playMockSse(path, payload, handlers, options) {
  if (path !== '/ai/prep/stream') {
    throw new Error(`离线演示模式暂未覆盖该事件流接口：${path}`)
  }
  const { text, act } = buildMockReply(payload)
  const signal = options?.signal

  // 这个顺序跟真后端一致：mode 和意图先到，正文再逐字出来。
  // 前端的打字机靠 delta 驱动，意图先落进 pendingAct 等正文说完才执行。
  dispatch(mockFrame('meta', { mode: 'AI' }), handlers)
  if (act) dispatch(mockFrame('act', act), handlers)

  await mockWait(120, signal)
  for (const chunk of mockChunks(text)) {
    dispatch(mockFrame('delta', { t: chunk }), handlers)
    await mockWait(38, signal)
  }
  dispatch(mockFrame('done', {}), handlers)
}
