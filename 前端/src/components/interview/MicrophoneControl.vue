<template>
  <div class="voice-answer">
    <span v-if="errorMessage" class="mic-error">{{ errorMessage }}</span>

    <div class="voice-footer">
      <div class="mic-copy">
        <span class="mic-status">{{ statusText }}</span>
        <span class="voice-only-hint">语音文字显示在上方，可滚轮翻阅</span>
      </div>

      <div class="voice-actions">
        <button type="button" class="btn-ghost" :disabled="disabled" @click="requestSkip">
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
          @click="toggleMicrophone"
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
import { computed, ref } from 'vue'
import { transcribeSpeech } from '../../api'
import { useMicrophone } from '../../composables/useMicrophone'

const props = defineProps({
  sessionId: { type: Number, default: null },
  transcript: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['transcript', 'processing', 'submit', 'skip'])
const pendingCount = ref(0)
const recognitionError = ref('')
let uploadQueue = Promise.resolve()
let generation = 0
let submitRequested = false

const {
  status,
  level,
  isSpeaking,
  errorMessage: deviceError,
  isMicOn,
  isRequesting,
  toggleMicrophone,
  muteMicrophone,
  stopMicrophone: stopDevice,
} = useMicrophone({ onSegment: queueTranscription })

const errorMessage = computed(() => recognitionError.value || deviceError.value)
const statusText = computed(() => {
  if (isRequesting.value) return '正在申请麦克风权限...'
  if (pendingCount.value > 0) return '正在识别语音...'
  if (isSpeaking.value) return '正在讲话，停顿后自动识别'
  if (isMicOn.value) return '麦克风已开启，请开始回答'
  if (status.value === 'muted') return '麦克风已静音，点击开麦'
  return '点击开启麦克风'
})

function queueTranscription(blob, duration) {
  if (!props.sessionId) return
  const currentGeneration = generation
  pendingCount.value++
  emit('processing', true)
  recognitionError.value = ''
  uploadQueue = uploadQueue
    .then(async () => {
      const result = await transcribeSpeech(blob, props.sessionId, duration)
      if (currentGeneration === generation && result?.text) emit('transcript', result.text)
    })
    .catch((error) => {
      if (currentGeneration === generation) {
        recognitionError.value = error.response?.data?.message || error.message || '语音识别失败，请重试。'
        submitRequested = false
      }
    })
    .finally(() => {
      if (currentGeneration !== generation) return
      pendingCount.value = Math.max(0, pendingCount.value - 1)
      if (pendingCount.value === 0) {
        emit('processing', false)
        if (submitRequested && !recognitionError.value) {
          submitRequested = false
          emit('submit')
        }
      }
    })
}

function requestSubmit() {
  if (props.disabled) return
  submitRequested = true
  if (isMicOn.value) muteMicrophone()
  if (pendingCount.value === 0) {
    submitRequested = false
    emit('submit')
  }
}

async function requestSkip() {
  generation++
  submitRequested = false
  pendingCount.value = 0
  emit('processing', false)
  await stopDevice()
  emit('skip')
}

async function stopMicrophone() {
  generation++
  submitRequested = false
  pendingCount.value = 0
  emit('processing', false)
  await stopDevice()
}

defineExpose({ stopMicrophone, muteMicrophone })
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
