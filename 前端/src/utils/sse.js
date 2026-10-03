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
  const store = useUserStore()
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
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
