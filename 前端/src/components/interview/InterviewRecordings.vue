<template>
  <div v-if="inline && (!hideEmpty || listError || roundRecordings.length)" class="round-recording" :data-round="roundNo ?? ''" :aria-label="`第${roundNo}题录音`">
    <p v-if="loading" class="round-note" role="status">读取录音…</p>
    <template v-else-if="roundRecordings.length">
      <span v-if="heading" class="round-heading">{{ heading }}</span>
      <div v-for="clip in roundRecordings" :key="clip.audioId" class="round-clip">
        <span class="round-label">{{ clipLabel(clip) }}</span>
        <div class="round-row">
          <button type="button" :aria-label="(state(clip).url ? '重新播放' : '播放') + clipAria(clip)"
            :disabled="clip.available === false || state(clip).loading" @click="play(clip)">{{ state(clip).loading ? '加载中…' : state(clip).url ? '重新播放' : '播放录音' }}</button>
          <button type="button" :aria-label="'下载' + clipAria(clip) + ' WAV'" :disabled="clip.available === false || state(clip).loading" @click="download(clip)">下载 WAV</button>
          <span class="round-duration">{{ duration(clip.durationMs) }}</span>
        </div>
        <p v-if="clip.available === false" class="round-error">这段录音已被清理或文件丢失，暂时无法播放。</p>
        <p v-if="state(clip).error" class="round-error" role="alert">{{ state(clip).error }}</p>
        <audio v-if="state(clip).url" :ref="element => setPlayer(clip.audioId, element)" :src="state(clip).url" controls preload="metadata"
          :aria-label="clipAria(clip) + '播放器'" @play="pauseOthers(clip.audioId)" @error="state(clip).error = '浏览器无法播放这份录音，可以尝试下载 WAV 文件。'"></audio>
      </div>
    </template>
    <p v-else-if="!listError" class="round-note">本题无录音。</p>
    <p v-else class="round-error" role="alert">{{ listError }}</p>
  </div>

  <section v-else class="interview-recordings" aria-label="面试录音">
    <header><h2>{{ heading || '面试录音' }}</h2><span v-if="!loading && !listError">{{ records.length }} 段</span></header>
    <p class="recording-note">每段对应一次作答（主回答或追问回答）。录音可直接播放，无需等待语音分析。</p>
    <p v-if="loading" role="status">正在读取录音…</p>
    <div v-else-if="listError" role="alert"><p>{{ listError }}</p><button type="button" @click="loadList">重新加载录音</button></div>
    <p v-else-if="!records.length" class="recording-note">本场暂无已保存录音。可能未开启录音、尚未上传完成，或录音已删除、到期清理。</p>
    <ol v-else>
      <li v-for="record in records" :key="record.audioId">
        <div class="recording-row">
          <div><strong>第 {{ record.roundNo }} 题 · {{ clipLabel(record) }}</strong><span class="recording-duration">{{ duration(record.durationMs) }}</span></div>
          <div class="recording-actions">
            <button type="button" :aria-label="(state(record).url ? '重新播放' : '播放') + clipAria(record)" :disabled="record.available === false || state(record).loading" @click="play(record)">{{ state(record).loading ? '加载中…' : state(record).url ? '重新播放' : '播放录音' }}</button>
            <button type="button" :aria-label="'下载' + clipAria(record) + ' WAV'" :disabled="record.available === false || state(record).loading" @click="download(record)">下载 WAV</button>
          </div>
        </div>
        <p v-if="record.available === false" class="recording-error">录音文件已丢失或被清理，暂时无法播放。</p>
        <p v-if="state(record).error" class="recording-error" role="alert">{{ state(record).error }}</p>
        <audio v-if="state(record).url" :ref="element => setPlayer(record.audioId, element)" :src="state(record).url" controls preload="metadata"
          :aria-label="clipAria(record) + '播放器'" @play="pauseOthers(record.audioId)" @error="state(record).error = '浏览器无法播放这份录音，可以尝试下载 WAV 文件。'"></audio>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getApiBase } from '../../utils/apiBase'
import { fetchRecordingList } from '../../utils/recordingListCache'

const props = defineProps({
  sessionId: { type: [Number, String], default: null },
  /** 内联模式：只渲染指定题号那一段，供「逐题回顾」逐条嵌入 */
  inline: { type: Boolean, default: false },
  roundNo: { type: [Number, String], default: null },
  /** 标题；内联模式只在有录音时显示，整段模式空值时用「面试录音」 */
  heading: { type: String, default: '' },
  /** 内联模式无录音时整块隐藏，避免报告页出现空提示 */
  hideEmpty: { type: Boolean, default: false },
  /** 外部删除录音后递增，触发重新拉取 */
  refreshKey: { type: [Number, String], default: 0 },
})
const records = ref([]), loading = ref(false), listError = ref('')
const files = reactive({}), players = new Map(), controllers = new Set()
let generation = 0

// 列表请求走模块级缓存，逐题内联实例共享同一次请求
async function fetchRecordings(sessionId) {
  return fetchRecordingList(sessionId)
}

// 同一题的录音可能有多段：0 主回答，1 起为追问回答
const roundRecordings = computed(() => {
  if (props.roundNo === null || props.roundNo === undefined) return []
  return records.value
    .filter(item => Number(item.roundNo) === Number(props.roundNo))
    .slice()
    .sort((a, b) => Number(a.answerSeq || 0) - Number(b.answerSeq || 0))
})

function clipLabel(clip) {
  const seq = Number(clip.answerSeq || 0)
  return seq <= 0 ? '主回答' : `追问 ${seq} 的回答`
}

function clipAria(clip) {
  const seq = Number(clip.answerSeq || 0)
  return seq <= 0 ? `第${clip.roundNo}题录音` : `第${clip.roundNo}题追问${seq}录音`
}

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
  try {
    const items = await fetchRecordings(props.sessionId)
    if (current === generation) records.value = items
  } catch (error) {
    if (current === generation) listError.value = error.message || '录音列表暂时无法加载，请重试。'
  } finally { if (current === generation) loading.value = false }
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
  const seq = Number(record.answerSeq || 0)
  const suffix = seq <= 0 ? '' : `追问${seq}`
  link.href = url; link.download = `面试-${props.sessionId}-第${record.roundNo}题${suffix}.wav`
  document.body.append(link); link.click(); link.remove()
}
watch(() => [props.sessionId, props.refreshKey], loadList, { immediate: true })
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

/* 内联模式：嵌在逐题回顾的展开区里，克制一点 */
.round-recording { margin-top:12px; padding-top:12px; border-top:1px dashed var(--neutral-200,#dce5e1); }
.round-row { display:flex; flex-wrap:wrap; align-items:center; gap:10px; }
.round-recording button { min-height:36px; padding:6px 12px; font-size:13px; border-radius:8px; border:1px solid var(--neutral-300,#bdcdc4); background:var(--surface-elevated,#fff); color:var(--neutral-800,#233c31); font:inherit; font-size:13px; cursor:pointer; }
.round-recording button:hover { background:var(--surface-soft,#f4f8f5); }
.round-recording button:disabled { opacity:.55; cursor:not-allowed; }
.round-duration { color:var(--neutral-600,#50675b); font-size:13px; font-variant-numeric:tabular-nums; }
.round-clip + .round-clip { margin-top:10px; padding-top:10px; border-top:1px dashed var(--neutral-200,#dce5e1); }
.round-heading { display:block; margin-bottom:8px; color:var(--neutral-800,#233c31); font-size:13px; font-weight:700; }
.round-label { display:inline-block; margin-bottom:6px; color:var(--neutral-600,#50675b); font-size:12px; font-weight:600; }
.round-note { margin:0; color:var(--neutral-600,#50675b); font-size:13px; }
.round-error { margin:8px 0 0; color:var(--error-700,#a33434); font-size:13px; line-height:1.6; }
@media(max-width:600px) { .interview-recordings { padding:16px; } .interview-recordings .recording-row { align-items:flex-start; flex-direction:column; } }
</style>
