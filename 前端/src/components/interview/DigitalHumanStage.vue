<template>
  <div class="digital-human">
    <img
      class="stage-poster"
      :src="INTERVIEW_AVATAR_ASSETS.poster"
      alt=""
      aria-hidden="true"
    />
    <video
      ref="waitingRef"
      class="stage-video waiting-video"
      :class="{ visible: stage === 'WAITING' }"
      :src="INTERVIEW_AVATAR_ASSETS.waiting"
      :poster="INTERVIEW_AVATAR_ASSETS.poster"
      autoplay muted playsinline preload="auto"
      aria-hidden="true"
      @ended="handleWaitingEnded"
      @error="handleWaitingError"
    />
    <video
      ref="openingRef"
      class="stage-video opening-video"
      :class="{ visible: stage === 'OPENING' }"
      :src="INTERVIEW_AVATAR_ASSETS.opening"
      :poster="INTERVIEW_AVATAR_ASSETS.poster"
      muted playsinline preload="auto"
      aria-hidden="true"
      @playing="handleOpeningPlaying"
      @ended="showLive"
      @error="skipOpening"
    />
    <iframe
      ref="frameRef"
      class="digital-human-frame"
      :class="{ visible: stage === 'LIVE' }"
      :src="embedUrl"
      title="AI 数字人面试官"
      allow="autoplay; fullscreen"
      @load="handleFrameLoad"
    />

    <div class="connection-status" :class="connectionState">
      <span class="status-dot"></span>
      {{ statusText }}
    </div>

    <button
      v-if="connectionState === 'error'"
      type="button"
      class="retry-button"
      @click="reload"
    >
      重新连接
    </button>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { demoState } from '../../utils/offlineDemo'
import { getApiBase } from '../../utils/apiBase'
import { assetUrl } from '../../utils/assetUrl'

const props = defineProps({
  text: {
    type: String,
    default: '',
  },
  speechKey: {
    type: Number,
    default: 0,
  },
})

// 数字人嵌入页地址：优先构建时注入；否则跟随登录页「服务器设置」的服务器地址（换成 8010 端口）。
// 手机 APK 里没有 Vite 代理，相对地址只会指到 App 自己，必须解析成电脑的绝对地址。
function resolveEmbedBaseUrl() {
  const explicit = import.meta.env.VITE_DIGITAL_HUMAN_EMBED_URL
  if (explicit) return explicit
  try {
    const apiHost = new URL(getApiBase()).hostname
    if (apiHost) return `http://${apiHost}:8010/offerpilot-embed.html`
  } catch {
    /* API 根地址是相对路径（Web 端）：继续走 Vite 代理 */
  }
  return '/digital-human/offerpilot-embed.html'
}

const embedBaseUrl = resolveEmbedBaseUrl()
const embedUrl = withLayoutVersion(embedBaseUrl)
const INTERVIEW_AVATAR_ASSETS = {
  waiting: assetUrl('assets/interview-avatar/waiting-loop-v3.mp4'),
  opening: assetUrl('assets/interview-avatar/door-opening-v3.mp4'),
  interviewerFallback: assetUrl('assets/interview-avatar/interviewer-v3.mp4'),
  poster: assetUrl('assets/interview-avatar/closed-door-j0.png'),
}

const frameRef = ref(null)
const waitingRef = ref(null)
const openingRef = ref(null)
const stage = ref('WAITING')
const avatarReady = ref(false)
const waitingFailed = ref(false)
const connectionState = ref('connecting')
const pendingSpeech = ref(null)
let connectionTimer = null
let openingPending = false
let speechTimer = null
let liveReady = false

const statusText = computed(() => ({
  connecting: '正在连接数字人',
  ready: stage.value === 'OPENING' ? 'AI 面试官已就绪' : stage.value === 'LIVE' ? '数字人已连接' : '正在准备 AI 面试官…',
  speaking: '数字人播报中',
  error: '数字人连接异常，请重新连接',
})[connectionState.value] || '正在连接数字人')

function handleWaitingEnded() {
  if (stage.value !== 'WAITING') return
  if (avatarReady.value) {
    enterOpening()
  } else {
    waitingRef.value.currentTime = 0
    waitingRef.value.play().catch(handleWaitingError)
  }
}

function handleWaitingError() {
  waitingFailed.value = true
  if (avatarReady.value) enterOpening()
}

function enterOpening() {
  if (stage.value !== 'WAITING' || !avatarReady.value || openingPending) return
  openingPending = true
  openingRef.value.currentTime = 0
  openingRef.value.play().catch(skipOpening)
}

function handleOpeningPlaying() {
  if (openingPending && avatarReady.value) {
    openingPending = false
    stage.value = 'OPENING'
  }
}

function skipOpening() {
  openingPending = false
  if (avatarReady.value) enterLive()
}

function showLive() {
  if (stage.value === 'OPENING' && avatarReady.value) enterLive()
}

function postToFrame(message) {
  const targetWindow = frameRef.value?.contentWindow
  if (!targetWindow) return false

  targetWindow.postMessage(message, resolveTargetOrigin())
  return true
}

function resolveTargetOrigin() {
  try {
    const origin = new URL(embedUrl, window.location.href).origin
    // file:// 页面下 origin 是字符串 "null"，postMessage 不接受它当 targetOrigin（抛 SyntaxError）。
    // 放宽成 '*'：真正把关的是 handleMessage 里 event.source 是不是我们的 iframe。
    return origin === 'null' ? '*' : origin
  } catch {
    return '*'
  }
}

function withLayoutVersion(url) {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}offerpilotLayout=3`
}

function speak(text, key = '') {
  const normalized = text?.trim()
  if (!normalized) return

  pendingSpeech.value = { text: normalized, key }
  tryFlush()
}

function flushPendingSpeech() {
  if (!pendingSpeech.value) return
  const speech = pendingSpeech.value
  pendingSpeech.value = null
  postToFrame({ type: 'offerpilot.speak', ...speech })
  connectionState.value = 'ready'
}

function tryFlush() {
  if (!pendingSpeech.value) return
  if (stage.value !== 'LIVE' || !liveReady) return
  if (connectionState.value !== 'ready' && connectionState.value !== 'speaking') return
  flushPendingSpeech()
}

function enterLive() {
  if (stage.value === 'LIVE') return
  stage.value = 'LIVE'
  clearTimeout(speechTimer)
  liveReady = false
  speechTimer = setTimeout(() => {
    liveReady = true
    tryFlush()
  }, 1000)
}

function stop() {
  postToFrame({ type: 'offerpilot.stop' })
  if (connectionState.value !== 'error') connectionState.value = 'ready'
}

function close() {
  postToFrame({ type: 'offerpilot.close' })
  pendingSpeech.value = null
}

function unlockAudio() {
  postToFrame({ type: 'offerpilot.unlockAudio' })
}

function handleFrameLoad() {
  applyEmbeddedLayout()
  connectionState.value = 'connecting'
  startConnectionTimeout()
}

function applyEmbeddedLayout() {
  try {
    const document = frameRef.value?.contentDocument
    if (!document?.head) return

    let style = document.getElementById('offerpilot-layout-override')
    if (!style) {
      style = document.createElement('style')
      style.id = 'offerpilot-layout-override'
      document.head.appendChild(style)
    }
    style.textContent = `
      .stage {
        border-radius: 18px !important;
        background:
          radial-gradient(circle at 50% 42%, rgba(255,255,255,.96),
          rgba(225,234,247,.86) 58%, rgba(205,218,237,.9)) !important;
      }
      #video {
        width: 100% !important;
        height: 100% !important;
        object-fit: contain !important;
        object-position: center center !important;
      }
      .status { display: none !important; }
      .connect[disabled] { display: none !important; }
    `
  } catch {
    // 跨域部署时无法访问 iframe 文档，改由数字人嵌入页自身样式控制。
  }
}

function reload() {
  openingPending = false
  stage.value = 'WAITING'
  avatarReady.value = false
  connectionState.value = 'connecting'
  clearTimeout(speechTimer)
  liveReady = false
  openingRef.value?.pause()
  if (!waitingFailed.value && waitingRef.value) {
    waitingRef.value.currentTime = 0
    waitingRef.value.play().catch(handleWaitingError)
  }
  startConnectionTimeout()
  const frame = frameRef.value
  if (!frame) return

  try {
    frame.src = withLayoutVersion(`${embedBaseUrl}${embedBaseUrl.includes('?') ? '&' : '?'}reload=${Date.now()}`)
  } catch {
    frame.src = embedUrl
  }
}

function handleMessage(event) {
  if (event.source !== frameRef.value?.contentWindow) return
  const targetOrigin = resolveTargetOrigin()
  if (targetOrigin !== '*' && event.origin !== targetOrigin) return

  const data = event.data || {}
  if (data.type === 'offerpilot.embed.ready') {
    clearTimeout(connectionTimer)
    connectionState.value = 'ready'
    unlockAudio()
    tryFlush()
  } else if (data.type === 'offerpilot.embed.videoReady') {
    clearTimeout(connectionTimer)
    avatarReady.value = true
    connectionState.value = 'ready'
    if (waitingFailed.value) enterOpening()
  } else if (data.type === 'offerpilot.embed.disconnected') {
    clearTimeout(connectionTimer)
    connectionState.value = 'error'
    avatarReady.value = false
    clearTimeout(speechTimer)
    liveReady = false
    if (stage.value === 'OPENING' || openingPending) {
      openingPending = false
      openingRef.value?.pause()
      stage.value = 'WAITING'
      if (!waitingFailed.value && waitingRef.value) {
        waitingRef.value.currentTime = 0
        waitingRef.value.play().catch(handleWaitingError)
      }
    }
  }
}

function startConnectionTimeout() {
  clearTimeout(connectionTimer)
  // 离线演示模式下别开这个超时：数字人 iframe 本来就连不上，两分钟后会翻成一条
  // 红字「数字人连接异常」，挂在演示画面上很难看。停在「正在连接」比挂着报错体面，
  // 也不必编一句「已就绪」的假话。面试流程本身不依赖它（题目由 messages 驱动）。
  if (demoState.active) return
  connectionTimer = setTimeout(() => {
    // 计时器是挂载时起的，两分钟内可能已经切进演示模式了，触发时再判一次
    if (demoState.active) return
    if (connectionState.value === 'connecting') connectionState.value = 'error'
  }, 120000)
}

watch(
  () => [props.text, props.speechKey],
  ([text, key]) => speak(text, key),
  { immediate: true },
)

window.addEventListener('message', handleMessage)

onBeforeUnmount(() => {
  clearTimeout(connectionTimer)
  clearTimeout(speechTimer)
  close()
  window.removeEventListener('message', handleMessage)
})

defineExpose({
  speak,
  stop,
  close,
  unlockAudio,
  reload,
})
</script>

<style scoped>
.digital-human {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
  background: #e3ebf6;
}

.stage-video,
.digital-human-frame,
.stage-poster {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
  background: #e3ebf6;
  pointer-events: none;
  object-fit: cover;
  object-position: center;
}

.stage-video,
.digital-human-frame { opacity: 0; }
.stage-poster { opacity: 1; }

.waiting-video.visible,
.opening-video.visible,
.digital-human-frame.visible { opacity: 1; }
.opening-video { transition: opacity 250ms ease; }
.digital-human-frame { transition: opacity 250ms ease; }
.digital-human-frame.visible { pointer-events: auto; }
.stage-poster { z-index: 0; }
.digital-human-frame { z-index: 1; }
.opening-video { z-index: 2; }
.waiting-video { z-index: 3; }
.connection-status,
.retry-button { z-index: 4; }

.connection-status {
  position: absolute;
  top: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.84);
  background: rgba(5, 12, 24, 0.58);
  backdrop-filter: blur(10px);
  font-size: 11px;
  pointer-events: none;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.14);
}

.connection-status.ready .status-dot,
.connection-status.speaking .status-dot {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}

.connection-status.error .status-dot {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.15);
}

.retry-button {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  padding: 8px 15px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.82);
}
</style>
