<template>
  <div class="digital-human">
    <iframe
      ref="frameRef"
      class="digital-human-frame"
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

const embedBaseUrl = import.meta.env.VITE_DIGITAL_HUMAN_EMBED_URL
  || '/digital-human/offerpilot-embed.html'
const embedUrl = withLayoutVersion(embedBaseUrl)

const frameRef = ref(null)
const connectionState = ref('connecting')
const pendingSpeech = ref(null)
let connectionTimer = null

const statusText = computed(() => ({
  connecting: '数字人连接中',
  ready: '数字人已连接',
  speaking: '数字人播报中',
  error: '数字人连接失败',
})[connectionState.value] || '数字人连接中')

function postToFrame(message) {
  const targetWindow = frameRef.value?.contentWindow
  if (!targetWindow) return false

  targetWindow.postMessage(message, resolveTargetOrigin())
  return true
}

function resolveTargetOrigin() {
  try {
    return new URL(embedUrl, window.location.href).origin
  } catch {
    return window.location.origin
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
  if (connectionState.value === 'ready' || connectionState.value === 'speaking') {
    postToFrame({ type: 'offerpilot.speak', ...pendingSpeech.value })
    pendingSpeech.value = null
    connectionState.value = 'ready'
  }
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
      video {
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
  connectionState.value = 'connecting'
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
  if (event.origin !== resolveTargetOrigin()) return

  const data = event.data || {}
  if (data.type === 'offerpilot.embed.ready') {
    clearTimeout(connectionTimer)
    connectionState.value = 'ready'
    unlockAudio()
    if (pendingSpeech.value) {
      postToFrame({ type: 'offerpilot.speak', ...pendingSpeech.value })
      pendingSpeech.value = null
      connectionState.value = 'ready'
    }
  } else if (data.type === 'offerpilot.embed.disconnected') {
    clearTimeout(connectionTimer)
    connectionState.value = 'error'
  }
}

function startConnectionTimeout() {
  clearTimeout(connectionTimer)
  connectionTimer = setTimeout(() => {
    if (connectionState.value === 'connecting') connectionState.value = 'error'
  }, 20000)
}

watch(
  () => [props.text, props.speechKey],
  ([text, key]) => speak(text, key),
  { immediate: true },
)

window.addEventListener('message', handleMessage)

onBeforeUnmount(() => {
  clearTimeout(connectionTimer)
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

.digital-human-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #e3ebf6;
}

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
