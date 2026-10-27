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
import { isExplicitPrepChoice } from './aiPrepConversation'

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
    throw Object.assign(new Error(`SSE 连接失败（HTTP ${res.status}）`), { code: res.status })
  }

  // 后端把参数校验失败等异常交给 GlobalExceptionHandler，返回的是 JSON 而非事件流。
  // 此时 HTTP 仍是 200，不校验类型就会把 {"code":400,...} 当 SSE 硬解析（没有 data: 行，
  // 结果是一句都读不到、静默降级）。这里把真实原因抛出去，调用方至少能记进控制台。
  const ctype = res.headers.get('content-type') || ''
  if (!ctype.includes('text/event-stream')) {
    let detail = '', code = res.status
    try {
      const body = await res.json()
      detail = body?.message || ''
      code = body?.code || code
    } catch {
      /* 不是 JSON 就算了 */
    }
    throw Object.assign(new Error(detail || `SSE 响应类型异常（${ctype || '未知'}）`), { code })
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

  if (isExplicitPrepChoice(message)) {
    const guess = guessJob(message)
    if (guess) {
      const focus = MOCK_AI_PREP_FOCUS[guess.job.code] || ''
      return {
        text: `${focus}如果确定用「${guess.job.name}」作为目标岗位，可以点确认；也可以继续讨论。`,
        act: {
          intent: 'PICK_JOB',
          jobCode: guess.job.code,
          jobName: guess.job.name,
          // 命中只提供待确认建议，不自动改变用户的岗位。
          confidence: guess.confident ? 'HIGH' : 'LOW',
        },
      }
    }
    const family = guessFamily(message)
    if (family) {
      return {
        text: `${family}方向可以先了解。你可以浏览相关岗位，也可以接着聊日常工作和能力要求。`,
        act: { intent: 'PICK_JOB', family, confidence: 'LOW' },
      }
    }
  }

  if (stage === 'UPLOAD' && /(?:没有|没写|没带|没准备).{0,6}简历|(?:在线|填一份|现写).{0,6}简历|简历.{0,6}(?:没有|没写|在线)/.test(message)) {
    return {
      text: '没有文件也没关系，在线填一份就行——岗位、技能、项目经历三栏，填完效果一样。',
      act: { intent: 'NO_RESUME', confidence: 'LOW' },
    }
  }

  if (/前端/.test(message) && /后端/.test(message)) {
    return { text: '前端更关注用户看到的页面、交互与浏览器体验，后端更关注业务逻辑、接口、数据库和服务稳定性。两边都需要编程和调试，不能简单按难易选。你做项目时，更喜欢打磨界面，还是实现数据和业务逻辑？' }
  }
  if (/java/i.test(message) && /python/i.test(message)) {
    return { text: 'Java 后端通常围绕业务服务、数据库和工程协作，Python 后端也要掌握接口、数据库与部署，语言只是入口。可以先比较你已有的项目经验和愿意深入的技术栈。你用哪种语言完整做过一个项目？' }
  }
  if (/不会|不懂|没学|零基础|基础差/.test(message)) {
    return { text: '暂时不会某项技术，不等于不适合这个方向。更有用的是看看你现有的基础，以及是否愿意通过一个小项目试试日常工作。你目前最熟悉什么技能？' }
  }
  if (/工资|薪资|就业|前景|好找工作/.test(message)) {
    return { text: '就业机会和薪资会随城市、企业、经验和招聘时间变化，这里的离线演示不能查询实时招聘数据。选方向时可以先比较工作内容、已有能力和补足成本。你更在意哪一个？' }
  }
  if (/没方向|不知道|没想好|迷茫|纠结|先聊|不想|不要|不选/.test(message)) {
    return { text: '可以，先不用选。我们从具体经历聊起，比直接看岗位名更容易判断。最近一次课程作业、项目或实习里，你最愿意花时间做的部分是什么？' }
  }
  const guess = guessJob(message)
  if (guess && /怎么样|做什么|要求|了解|准备|适合|推荐|建议/.test(message)) {
    const focus = MOCK_AI_PREP_FOCUS[guess.job.code] || ''
    return { text: `${focus}这只是了解方向，还没有替你选定。你想先聊工作内容，还是结合自己的项目看看匹配程度？` }
  }
  const previous = (payload.history || []).filter(m => m.role === 'user').map(m => m.content).join(' ')
  if (/项目|课程|实习|学过|会|专业/.test(message + previous)) {
    return { text: '这些经历可以作为探索方向的线索，但还不足以直接定岗。你可以挑一个刚提到的经历，说说自己负责的部分和最有成就感的环节，我们再对照岗位看。' }
  }
  return { text: '可以继续聊，不用现在做选择。这里是离线演示，复杂问题需要连接 AI 后才能结合上下文深入分析；你可以先说说最想了解的工作内容或自己的经历。' }
}

async function playMockSse(path, payload, handlers, options) {
  if (path !== '/ai/prep/stream') {
    throw new Error(`离线演示模式暂未覆盖该事件流接口：${path}`)
  }
  const { text, act } = buildMockReply(payload)
  const signal = options?.signal

  // 这个顺序跟真后端一致：mode 和意图先到，正文再逐字出来。
  // 前端的打字机靠 delta 驱动，意图先落进 pendingAct 等正文说完才执行。
  dispatch(mockFrame('meta', { mode: 'RULE' }), handlers)
  if (act) dispatch(mockFrame('act', act), handlers)

  await mockWait(120, signal)
  for (const chunk of mockChunks(text)) {
    dispatch(mockFrame('delta', { t: chunk }), handlers)
    await mockWait(38, signal)
  }
  dispatch(mockFrame('done', {}), handlers)
}
