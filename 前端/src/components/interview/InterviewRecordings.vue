<template>
  <section class="interview-recordings" aria-label="面试录音">
    <header><h2>面试录音</h2><span v-if="!loading && !listError">{{ records.length }} 段</span></header>
    <p class="recording-note">每段对应一道主问题，包含该题的追问回答。录音可直接播放，无需等待语音分析。</p>
    <p v-if="loading" role="status">正在读取录音…</p>
    <div v-else-if="listError" role="alert"><p>{{ listError }}</p><button type="button" @click="loadList">重新加载录音</button></div>
    <p v-else-if="!records.length" class="recording-note">本场暂无已保存录音。可能未开启录音、尚未上传完成，或录音已删除、到期清理。</p>
    <ol v-else>
      <li v-for="record in records" :key="record.audioId">
        <div class="recording-row">
          <div><strong>第 {{ record.roundNo }} 题录音</strong><span class="recording-duration">{{ duration(record.durationMs) }}</span></div>
          <div class="recording-actions">
            <button type="button" :aria-label="(state(record).url ? '重新播放' : '播放') + '第' + record.roundNo + '题录音'" :disabled="record.available === false || state(record).loading" @click="play(record)">{{ state(record).loading ? '加载中…' : state(record).url ? '重新播放' : '播放录音' }}</button>
            <button type="button" :aria-label="'下载第' + record.roundNo + '题录音 WAV'" :disabled="record.available === false || state(record).loading" @click="download(record)">下载 WAV</button>
          </div>
        </div>
        <p v-if="record.available === false" class="recording-error">录音文件已丢失或被清理，暂时无法播放。</p>
        <p v-if="state(record).error" class="recording-error" role="alert">{{ state(record).error }}</p>
        <audio v-if="state(record).url" :ref="element => setPlayer(record.audioId, element)" :src="state(record).url" controls preload="metadata"
          :aria-label="'第' + record.roundNo + '题录音播放器'" @play="pauseOthers(record.audioId)" @error="state(record).error = '浏览器无法播放这份录音，可以尝试下载 WAV 文件。'"></audio>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import request from '../../utils/request'
import { getApiBase } from '../../utils/apiBase'

const props = defineProps({ sessionId: { type: [Number, String], default: null } })
const records = ref([]), loading = ref(false), listError = ref('')
const files = reactive({}), players = new Map(), controllers = new Set()
let generation = 0
const state = record => files[record.audioId] || (files[record.audioId] = { loading: false, url: '', error: '' })
const setPlayer = (id, element) => element ? players.set(id, element) : players.delete(id)
function duration(ms) {
  const seconds = Math.max(0, Math.round(Number(ms || 0) / 1000))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}
function release() {
  controllers.forEach(controller => controller.abort()); controllers.clear()
  players.forEach(player => player.pause()); players.clear()
  for (const id of Object.keys(files)) { if (files[id].url) URL.revokeObjectURL(files[id].url); delete files[id] }
}
async function loadList() {
  const current = ++generation
  release(); records.value = []; listError.value = ''
  if (!props.sessionId) { loading.value = false; return }
  loading.value = true
  const controller = new AbortController(); controllers.add(controller)
  try {
    const data = await request.get(`/interview/${props.sessionId}/recordings`, { signal: controller.signal })
    if (current === generation) records.value = Array.isArray(data) ? data : []
  } catch (error) {
    if (current === generation && !controller.signal.aborted) listError.value = error.message || '录音列表暂时无法加载，请重试。'
  } finally { controllers.delete(controller); if (current === generation) loading.value = false }
}
async function loadAudio(record) {
  const item = state(record)
  if (item.url) return item.url
  if (item.loading || record.available === false) return null
  const current = generation, controller = new AbortController()
  item.loading = true; item.error = ''; controllers.add(controller)
  try {
    const token = localStorage.getItem('token') || ''
    const response = await fetch(`${getApiBase()}/interview/audio/${record.audioId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}, signal: controller.signal,
    })
    if (!response.ok) throw new Error(response.status === 401 ? '登录已失效，请重新登录后再播放。' : '录音暂时无法读取，请重试。')
    if (response.headers.get('content-type')?.includes('application/json')) {
      const result = await response.json(); throw new Error(result.message || '录音文件暂时不可用。')
    }
    const blob = await response.blob()
    if (current !== generation) return null
    if (!blob.size) throw new Error('录音文件为空。')
    item.url = URL.createObjectURL(blob)
    return item.url
  } catch (error) {
    if (current === generation && !controller.signal.aborted) item.error = error.message || '录音加载失败，请重试。'
    return null
  } finally { controllers.delete(controller); item.loading = false }
}
function pauseOthers(id) { players.forEach((player, key) => { if (key !== id) player.pause() }) }
async function play(record) {
  const current = generation
  if (!await loadAudio(record)) return
  await nextTick()
  if (current !== generation) return
  try { await players.get(record.audioId)?.play() }
  catch { /* Browser autoplay restrictions still allow the visible native playback controls. */ }
}
async function download(record) {
  const current = generation
  const url = await loadAudio(record)
  if (!url || current !== generation) return
  const link = document.createElement('a')
  link.href = url; link.download = `面试-${props.sessionId}-第${record.roundNo}题.wav`
  document.body.append(link); link.click(); link.remove()
}
watch(() => props.sessionId, loadList, { immediate: true })
onBeforeUnmount(() => { generation++; release() })
</script>

<style scoped>
.interview-recordings { margin:20px 0; padding:20px; border:1px solid var(--neutral-200,#dce5e1); border-radius:12px; background:var(--surface-elevated,#fff); color:var(--neutral-800,#233c31); }
header,.recording-row,.recording-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; }
h2 { margin:0; font-size:18px; } header>span,.recording-note,.recording-duration { color:var(--neutral-600,#50675b); font-size:14px; }
.recording-note { margin:10px 0; line-height:1.7; } ol { list-style:none; padding:0; margin:0; } li { padding:16px 0; border-top:1px solid var(--neutral-200,#dce5e1); }
.recording-duration { margin-left:12px; } .recording-actions { flex-wrap:wrap; justify-content:flex-start; }
button { min-height:44px; padding:8px 14px; border:1px solid var(--neutral-300,#bdcdc4); border-radius:8px; background:var(--surface-elevated,#fff); color:var(--neutral-800,#233c31); font:inherit; font-size:14px; cursor:pointer; }
button:hover { background:var(--surface-soft,#f4f8f5); } button:disabled { opacity:.55; cursor:not-allowed; } button:focus-visible { outline:2px solid var(--accent-600,#059669); outline-offset:2px; }
audio { display:block; width:100%; margin-top:12px; } .recording-error { margin:10px 0 0; color:var(--error-700,#a33434); font-size:14px; line-height:1.6; }
@media(max-width:600px) { .interview-recordings { padding:16px; } .recording-row { align-items:flex-start; flex-direction:column; } }
</style>
