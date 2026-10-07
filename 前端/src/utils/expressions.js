export const EXPRESSION_LABELS = {
  neutral: '平静', happy: '开心', sad: '低落', angry: '生气',
  fearful: '害怕', disgusted: '厌恶', surprised: '惊讶',
}
export const EXPRESSION_KEYS = Object.keys(EXPRESSION_LABELS)
export const EXPRESSION_INTERVAL_MS = 2000
export const EXPRESSION_GAP_MS = 6000
export function dominantExpression(p) {
  return EXPRESSION_KEYS.reduce((best, key) => p[key] > p[best] ? key : best, 'neutral')
}
export function displayExpression(p, previous = null) {
  const smoothed = Object.fromEntries(EXPRESSION_KEYS.map(key => [key, previous ? previous[key] * .55 + p[key] * .45 : p[key]]))
  const ranked = EXPRESSION_KEYS.slice().sort((a, b) => smoothed[b] - smoothed[a])
  return { probabilities: smoothed, key: smoothed[ranked[0]] >= .45 && smoothed[ranked[0]] - smoothed[ranked[1]] >= .12 ? ranked[0] : null }
}
export function expressionSeries(samples, key, start, majorOnly = false) {
  const points = []
  let previous
  for (const sample of samples) {
    if (previous && sample.capturedAt - previous.capturedAt > EXPRESSION_GAP_MS) points.push([(previous.capturedAt + 1 - start) / 1000, null])
    const value = sample.faceDetected && (!majorOnly || dominantExpression(sample.probabilities) === key) ? sample.probabilities[key] * 100 : null
    points.push([(sample.capturedAt - start) / 1000, value])
    previous = sample
  }
  return points
}
export function leadingExpressions(summary) {
  const counts = { ...summary?.dominantCounts, uncertain: summary?.uncertainCount || 0 }
  const keys = [...EXPRESSION_KEYS, 'uncertain']
  const max = Math.max(0, ...keys.map(key => counts[key] || 0))
  return max ? keys.filter(key => counts[key] === max) : []
}
function localStorageOrNull() { try { return globalThis.localStorage } catch { return null } }
function validSample(s) {
  if (!s || !/^[a-zA-Z0-9-]{1,36}$/.test(s.sampleId) || !Number.isInteger(s.questionId) || !Number.isInteger(s.roundNo) || s.roundNo < 1 || !Number.isFinite(s.capturedAt) || s.capturedAt <= 0 || typeof s.faceDetected !== 'boolean') return false
  if (!s.faceDetected) return s.probabilities == null || Object.keys(s.probabilities).length === 0
  return s.probabilities && Object.keys(s.probabilities).length === 7 && EXPRESSION_KEYS.every(k => Number.isFinite(s.probabilities[k]) && s.probabilities[k] >= 0 && s.probabilities[k] <= 1) && Math.abs(EXPRESSION_KEYS.reduce((sum, k) => sum + s.probabilities[k], 0) - 1) <= .02
}
// Stable IDs make retry idempotent; quarantined entries retain their original data and reason.
export function createExpressionQueue(sessionId, send, storage = localStorageOrNull(), options = {}) {
  const key = options.ownerId ? 'offerpilot:expressions:' + options.ownerId + ':' + sessionId : 'offerpilot:expressions:' + sessionId
  const rejectedKey = key + ':rejected'
  let pending = [], rejected = [], saving = null
  const state = { pending: 0, rejected: 0, storageError: !storage, saving: false, authRequired: false, error: '' }
  function read(cacheKey) { try { const v = JSON.parse(storage?.getItem(cacheKey) || '[]'); return Array.isArray(v) ? v : [] } catch { state.storageError = true; return [] } }
  pending = read(key); rejected = read(rejectedKey)
  if (!pending.length && options.ownerId) {
    const legacyKey = 'offerpilot:expressions:' + sessionId
    pending = read(legacyKey)
    if (pending.length) { try { storage.setItem(key, JSON.stringify(pending)); storage.removeItem(legacyKey) } catch { state.storageError = true } }
  }
  function notify() { state.pending = pending.length; state.rejected = rejected.length; options.onState?.({ ...state, rejectedSamples: rejected }) }
  function persist() {
    try {
      for (const [cacheKey, samples] of [[key, pending], [rejectedKey, rejected]]) {
        if (samples.length) storage?.setItem(cacheKey, JSON.stringify(samples))
        else storage?.removeItem(cacheKey)
      }
    } catch { state.storageError = true }
    notify()
  }
  rejected.push(...pending.filter(s => !validSample(s)).map(sample => ({ sample, reason: '本地样本格式不完整，未上传' })))
  pending = pending.filter(validSample); persist()
  async function upload(batch) {
    let result
    try { result = await send(sessionId, { samples: batch }) }
    catch (error) {
      const code = error.code || error.response?.status
      if (code === 401 || code === 403 || /无权|登录已过期/.test(error.message)) { state.authRequired = true; throw error }
      if (code === 400 || code === 422) {
        if (batch.length > 1) { const mid = Math.ceil(batch.length / 2); await upload(batch.slice(0, mid)); await upload(batch.slice(mid)); return }
        rejected.push({ sample: batch[0], reason: error.message || '样本校验失败' })
        pending = pending.filter(s => s.sampleId !== batch[0].sampleId); persist(); return
      }
      throw error
    }
    const accepted = new Set(result?.acceptedIds || batch.map(s => s.sampleId))
    const refused = new Map((result?.rejected || []).map(item => [item.sampleId, item.reason]))
    for (const sample of batch) if (refused.has(sample.sampleId)) rejected.push({ sample, reason: refused.get(sample.sampleId) })
    pending = pending.filter(s => !accepted.has(s.sampleId) && !refused.has(s.sampleId)); persist()
    if (batch.some(s => !accepted.has(s.sampleId) && !refused.has(s.sampleId))) throw new Error('服务端未确认全部样本，保留待重试')
  }
  return {
    get size() { return pending.length },
    get state() { return { ...state, rejectedSamples: rejected } },
    add(sample) { if (validSample(sample)) pending.push(sample); else rejected.push({ sample, reason: '样本格式不完整' }); persist() },
    clearRejected() { rejected = []; persist() },
    flush() {
      if (saving) return saving
      if (state.authRequired) return Promise.reject(new Error('登录已过期，请重新登录后补传'))
      state.saving = true; state.error = ''; notify()
      saving = (async () => { while (pending.length) await upload(pending.slice(0, 100)) })()
        .catch(error => { state.error = error.message || '上传失败'; throw error })
        .finally(() => { saving = null; state.saving = false; notify() })
      return saving
    },
  }
}
export function classifyExpression(probabilities) {
  const ranked = EXPRESSION_KEYS.slice().sort((a,b)=>probabilities[b]-probabilities[a])
  return { key: probabilities[ranked[0]] >= .45 && probabilities[ranked[0]]-probabilities[ranked[1]] >= .12 ? ranked[0] : 'uncertain', first: ranked[0], second: ranked[1] }
}
