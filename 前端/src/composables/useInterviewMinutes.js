import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import * as api from '../api/minutes'
import { minutesLabel } from '../utils/minutes'
import { useRoute } from 'vue-router'

// Resolve only the authorized source ID, independently of report generation/rendering.
export function useReportMinutesSession(reportSource) {
  const route = useRoute(), resolved = ref(null)
  let controller
  watch(() => [route.params.id, route.params.sessionId, route.query.id, route.query.sessionId], async () => {
    controller?.abort(); controller = new AbortController(); const signal = controller.signal
    resolved.value = null
    if (route.params.sessionId || route.query.sessionId) return
    const reportId = route.params.id || route.query.id
    if (!reportId) return
    try { const result = await api.getReportMinutesSession(reportId, signal); if (!signal.aborted) resolved.value = result.sessionId }
    catch { /* The report still provides the ID if available; no other user's ID leaks. */ }
  }, { immediate: true })
  onBeforeUnmount(() => controller?.abort())
  return computed(() => Number(route.params.sessionId || route.query.sessionId || resolved.value || reportSource.value) || null)
}

export function useInterviewMinutes(sessionId, wantContent, wantSummary = wantContent) {
  const meta = ref(null), entries = ref([]), summary = ref(null), error = ref(''), actionError = ref(''), busy = ref(false), hasMore = ref(false)
  let controller = new AbortController(), generation = 0, running = false, disposed = false, timer
  const label = computed(() => minutesLabel(meta.value, busy.value, error.value))
  const sortedEntries = computed(() => [...entries.value].sort((a, b) => String(a.submittedAt).localeCompare(String(b.submittedAt)) || a.id - b.id))
  function reset() {
    generation++; controller.abort(); controller = new AbortController(); running = false
    meta.value = null; entries.value = []; summary.value = null; error.value = ''; actionError.value = ''; busy.value = false; hasMore.value = false
  }
  async function page(sid, epoch) {
    const cursor = entries.value.reduce((n, e) => Math.max(n, e.id), 0)
    const result = await api.getMinutesEntries(sid, cursor, controller.signal)
    if (epoch !== generation) return
    const known = new Set(entries.value.map(e => e.id))
    entries.value.push(...result.filter(e => !known.has(e.id)))
    hasMore.value = result.length === 100
  }
  async function refresh() {
    const sid = sessionId.value, epoch = generation
    if (!sid || disposed || document.hidden || running) return
    running = true
    try {
      const result = await api.getMinutes(sid, controller.signal)
      if (epoch !== generation) return
      meta.value = result; error.value = ''
      if (result.lifecycle === 'DELETED') { entries.value = []; summary.value = null; return }
      if (wantContent.value) {
        await page(sid, epoch)
        if (result.hasSummary && wantSummary.value) {
          const value = await api.getMinutesSummary(sid, controller.signal)
          if (epoch === generation) summary.value = value.outputJson ? JSON.parse(value.outputJson) : null
        }
      }
    } catch (e) { if (epoch === generation && e.code !== 'ERR_CANCELED') error.value = e.message || '纪要状态读取失败，请重试' }
    finally { if (epoch === generation) running = false }
  }
  async function action(fn) {
    if (busy.value || !sessionId.value) return
    const sid = sessionId.value, epoch = generation
    busy.value = true; actionError.value = ''
    try { await fn(sid, controller.signal) }
    catch (e) { if (epoch === generation && e.code !== 'ERR_CANCELED') actionError.value = e.message || '操作未确认，请重试' }
    finally {
      if (epoch === generation) {
        // Cancel reads started before the mutation so an old snapshot cannot win.
        generation++; controller.abort(); controller = new AbortController(); running = false
        await refresh(); busy.value = false
      }
    }
  }
  const control = enabled => action((sid, signal) => api.controlMinutes(sid, { enabled, expectedVersion: meta.value.controlVersion, requestId: crypto.randomUUID() }, signal))
  const generate = () => action((sid, signal) => api.generateMinutesSummary(sid, crypto.randomUUID(), signal))
  const remove = () => action((sid, signal) => api.deleteMinutes(sid, signal))
  async function more() { try { await page(sessionId.value, generation); return true } catch (e) { actionError.value = e.message; return false } }
  async function exportContent(kind, format, copy = false) {
    const sid = sessionId.value, epoch = generation
    try {
      const blob = await api.exportMinutes(sid, kind, format, controller.signal)
      if (epoch !== generation) return
      if (copy) {
        if (!navigator.clipboard?.writeText) throw new Error('当前环境无法复制，请下载 TXT 或 Markdown')
        await navigator.clipboard.writeText(await blob.text()); return '已复制'
      }
      const url = URL.createObjectURL(blob), link = document.createElement('a')
      link.href = url; link.download = `面试${sid}-${kind}.${format}`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
      return '已下载'
    } catch (e) { if (epoch === generation) actionError.value = e.message || '导出失败，请重试' }
  }
  watch(sessionId, () => { reset(); refresh() }, { immediate: true })
  watch([wantContent, wantSummary], () => refresh())
  function visible() { if (!document.hidden) refresh() }
  onMounted(() => { document.addEventListener('visibilitychange', visible); timer = setInterval(refresh, 3000) })
  onBeforeUnmount(() => { disposed = true; generation++; controller.abort(); clearInterval(timer); document.removeEventListener('visibilitychange', visible) })
  return { meta, entries: sortedEntries, summary, label, error, actionError, busy, hasMore, refresh, control, generate, remove, more, exportContent }
}
