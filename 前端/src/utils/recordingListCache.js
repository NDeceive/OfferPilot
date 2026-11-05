import request from './request'

// 同一场面试的录音列表在一次页面生命周期内只取一次：
// 逐题回顾会给每道题各挂一个内联播放器，不共享缓存就会变成 N 条重复请求。
const CACHE_TTL_MS = 60_000
const cache = new Map()
const inflight = new Map()

export function fetchRecordingList(sessionId) {
  const key = String(sessionId)
  const hit = cache.get(key)
  if (hit && Date.now() - hit.at < CACHE_TTL_MS) return Promise.resolve(hit.items)
  if (inflight.has(key)) return inflight.get(key)
  const promise = request.get(`/interview/${key}/recordings`)
    .then(data => {
      const items = Array.isArray(data) ? data : []
      cache.set(key, { at: Date.now(), items })
      inflight.delete(key)
      return items
    })
    .catch(error => { inflight.delete(key); throw error })
  inflight.set(key, promise)
  return promise
}

export function invalidateRecordingList(sessionId) {
  cache.delete(String(sessionId))
}
