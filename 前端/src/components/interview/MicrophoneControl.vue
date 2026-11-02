<template>
  <div class="voice-answer">
    <span v-if="errorMessage" class="mic-error">{{ errorMessage }}</span>

    <div class="voice-footer">
      <div class="mic-copy">
        <span class="mic-status">{{ statusText }}</span>
        <span class="voice-only-hint">{{ recognitionMode }} · 语音文字显示在上方，可滚轮翻阅</span>
      </div>

      <div class="voice-actions">
        <button v-if="allowSkip" type="button" class="btn-ghost" :disabled="disabled" @click="requestSkip">
          跳过
        </button>
        <button
          type="button"
          class="btn-send"
          :disabled="disabled || isRequesting || (!transcript && !isMicOn)"
          @click="requestSubmit"
        >
          提交
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
        </button>
        <button
          type="button"
          :class="['mic-control', { open: isMicOn, speaking: isSpeaking }]"
          :style="{ '--voice-level': level }"
          :disabled="isRequesting || disabled"
          :title="statusText"
          @click="handleToggleMicrophone"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
            <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
            <line x1="12" y1="19" x2="12" y2="23"/>
            <line v-if="!isMicOn" x1="4" y1="4" x2="20" y2="20"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { transcribeSpeech } from '../../api'
import { useMicrophone } from '../../composables/useMicrophone'

const props = defineProps({
  allowSkip:{type:Boolean,default:true},
  sessionId: { type: Number, default: null },
  transcript: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  paused: { type: Boolean, default: false },
})

const emit = defineEmits(['transcript', 'processing', 'submit', 'skip'])
const pendingCount = ref(0)
const recognitionError = ref('')
let uploadQueue = Promise.resolve()
let generation = 0
const submitRequested = ref(false)
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
const recognitionMode = SpeechRecognition ? '浏览器语音识别' : '智谱语音识别（当前浏览器不支持）'
let recognizer = null
let speechStop = null
let speechStopTimer = null
let resolveSpeechStop = null

const {
  status,
  level,
  isSpeaking,
  errorMessage: deviceError,
  isMicOn,
  isRequesting,
  toggleMicrophone,
  muteMicrophone: muteDevice,
  stopMicrophone: stopDevice,
} = useMicrophone({ onSegment: queueTranscription, onAnswerEnd: requestAutoSubmit })

const errorMessage = computed(() => recognitionError.value || deviceError.value)
const statusText = computed(() => {
  if (props.paused) return '面试已暂停，继续后可重新开启麦克风'
  if (isRequesting.value) return '正在申请麦克风权限...'
  if (submitRequested.value) return '正在等待识别完成后提交...'
  if (pendingCount.value > 0) return '正在识别语音...'
  if (isSpeaking.value) return '正在讲话，停顿 7 秒后自动提交'
  if (isMicOn.value) return '麦克风已开启，回答后停顿 7 秒自动提交'
  if (status.value === 'muted') return '麦克风已静音，点击开麦'
  return '点击开启麦克风'
})

function startSpeech() {
  if (!SpeechRecognition || recognizer || props.paused || recognitionError.value) return
  const currentGeneration = generation
  const current = new SpeechRecognition()
  recognizer = current
  current.lang = 'zh-CN'
  current.continuous = true
  current.interimResults = false
  current.onresult = event => {
    if (currentGeneration !== generation || recognizer !== current) return
    for (let i = event.resultIndex; i < event.results.length; i++) {
      if (event.results[i].isFinal && event.results[i][0].transcript.trim()) {
        emit('transcript', event.results[i][0].transcript)
      }
    }
  }
  current.onerror = event => {
    if (currentGeneration !== generation || recognizer !== current || event.error === 'no-speech') return
    const messages = {
      'not-allowed': '浏览器语音识别权限被拒绝，请允许麦克风权限后重新开麦。',
      'service-not-allowed': '当前浏览器不允许语音识别，请检查浏览器设置。',
      network: '浏览器语音识别服务连接失败，请检查网络后重新开麦。',
      'audio-capture': '浏览器无法获取麦克风音频，请检查设备后重新开麦。',
    }
    recognitionError.value = messages[event.error] || '浏览器语音识别失败，请重新开麦。'
    submitRequested.value = false
    muteDevice({ flush: false })
  }
  current.onend = () => {
    if (recognizer !== current) return
    recognizer = null
    clearTimeout(speechStopTimer)
    const resolve = resolveSpeechStop
    resolveSpeechStop = null
    speechStop = null
    resolve?.()
    if (currentGeneration === generation && isMicOn.value && !recognitionError.value) startSpeech()
  }
  try { current.start() } catch {
    recognizer = null
    recognitionError.value = '浏览器语音识别启动失败，请重新开麦。'
    muteDevice({ flush: false })
  }
}

function stopSpeech() {
  if (speechStop) return speechStop
  if (!recognizer) return Promise.resolve()
  const current = recognizer
  speechStop = new Promise(resolve => { resolveSpeechStop = resolve })
  const pending = speechStop
  // stop() may deliver its final result asynchronously; wait for onend before submitting.
  speechStopTimer = setTimeout(() => {
    recognitionError.value = '浏览器语音识别未能及时结束，请重新开麦后再试。'
    cancelSpeech()
  }, 5000)
  try { current.stop() } catch {
    recognitionError.value = '浏览器语音识别未能正常结束，请重新开麦。'
    cancelSpeech()
  }
  return pending
}

function cancelSpeech() {
  const current = recognizer
  recognizer = null
  clearTimeout(speechStopTimer)
  resolveSpeechStop?.()
  resolveSpeechStop = null
  speechStop = null
  try { current?.abort() } catch { /* Already disconnected. */ }
}

function muteMicrophone(options) {
  muteDevice(options)
  return stopSpeech()
}

watch(isMicOn, on => { if (on) startSpeech(); else stopSpeech() }, { flush: 'sync' })

function queueTranscription(blob, duration) {
  if (!props.sessionId || props.paused) return
  if (SpeechRecognition) return
  const currentGeneration = generation
  pendingCount.value++
  emit('processing', true)
  uploadQueue = uploadQueue
    .then(async () => {
      const result = await transcribeSpeech(blob, props.sessionId, duration)
      if (currentGeneration !== generation) return
      if (!result?.text?.trim()) throw new Error('没有识别到该段语音，请重新录音后提交。')
      emit('transcript', result.text)
    })
    .catch((error) => {
      if (currentGeneration === generation) {
        recognitionError.value = error.response?.data?.message || error.message || '语音识别失败，请重试。'
        submitRequested.value = false
      }
    })
    .finally(async () => {
      if (currentGeneration !== generation) return
      pendingCount.value = Math.max(0, pendingCount.value - 1)
      if (pendingCount.value === 0) {
        emit('processing', false)
        if (submitRequested.value && !recognitionError.value) {
          submitRequested.value = false
          await nextTick()
          if (currentGeneration !== generation) return
          if (props.transcript.trim()) emit('submit')
          else recognitionError.value = '没有识别到回答，请重新开麦后再试。'
        }
      }
    })
}

async function requestSubmit() {
  if (props.disabled || submitRequested.value || recognitionError.value) return
  const currentGeneration = generation
  submitRequested.value = true
  if (SpeechRecognition) emit('processing', true)
  if (isMicOn.value) muteDevice()
  if (SpeechRecognition) {
    await stopSpeech()
    if (currentGeneration !== generation) return
    emit('processing', false)
    await nextTick()
    if (currentGeneration !== generation) return
    if (recognitionError.value) { submitRequested.value = false; return }
  }
  if (pendingCount.value === 0) {
    submitRequested.value = false
    if (props.transcript.trim()) emit('submit')
    else recognitionError.value = '没有识别到回答，请重新开麦后再试。'
  }
}

function requestAutoSubmit() {
  // A failed ASR segment must remain recoverable instead of submitting a partial answer.
  if (recognitionError.value) {
    muteMicrophone({ flush: false })
    return
  }
  requestSubmit()
}

async function handleToggleMicrophone() {
  if (speechStop) await speechStop
  if (props.disabled || props.paused) return
  if (!isMicOn.value) recognitionError.value = ''
  toggleMicrophone()
}

async function requestSkip() {
  if (props.disabled || props.paused) return
  generation++
  cancelSpeech()
  submitRequested.value = false
  pendingCount.value = 0
  emit('processing', false)
  await stopDevice()
  emit('skip')
}

async function stopMicrophone() {
  generation++
  cancelSpeech()
  submitRequested.value = false
  pendingCount.value = 0
  emit('processing', false)
  await stopDevice()
}

defineExpose({ stopMicrophone, muteMicrophone })
watch(() => props.paused, paused => { if (paused) stopMicrophone() })
onUnmounted(() => { generation++; cancelSpeech() })
</script>

<style scoped>
.voice-answer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: var(--surface-elevated);
}

.voice-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: 52px;
  padding-top: 6px;
  border-top: 1px solid var(--neutral-100);
}

.mic-control {
  --voice-level: 0;
  position: relative;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 2px solid var(--neutral-300);
  background: var(--neutral-50);
  color: var(--neutral-500);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-normal);
  flex-shrink: 0;
}

.mic-control.open {
  border-color: var(--accent-500);
  background: var(--accent-50);
  color: var(--accent-700);
  box-shadow: 0 0 0 calc(5px + var(--voice-level) * 14px) rgba(16, 185, 129, 0.12);
}

.mic-control.speaking {
  background: var(--accent-600);
  color: white;
}

.mic-control:disabled {
  cursor: wait;
  opacity: 0.6;
}

.mic-status {
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-weight: 500;
}

.mic-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.voice-only-hint {
  color: var(--neutral-400);
  font-size: 11px;
}

.mic-error {
  padding: 0 var(--space-3);
  color: var(--color-error);
  font-size: var(--text-xs);
}

.voice-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.btn-ghost {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-sm);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-sm);
}

.btn-send {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-5);
  border: none;
  border-radius: var(--radius-sm);
  background: var(--accent-600);
  color: white;
  font-size: var(--text-sm);
  font-weight: 600;
}

.btn-ghost:disabled,
.btn-send:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@media (max-width: 640px) {
  .voice-footer {
    align-items: flex-end;
  }
  .voice-only-hint {
    display: none;
  }
}
</style>
