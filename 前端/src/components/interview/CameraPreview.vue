<template>
  <div class="camera-preview" :class="{ active: isActive }">
    <video
      v-show="isActive"
      ref="videoRef"
      class="camera-video"
      autoplay
      muted
      playsinline
    ></video>

    <div v-if="!isActive" class="camera-placeholder">
      <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M23 7l-7 5 7 5V7z"/>
        <rect x="1" y="5" width="15" height="14" rx="2" ry="2"/>
      </svg>
      <span class="camera-title">摄像头预览</span>
      <span class="camera-hint">
        {{ status === 'error' ? errorMessage : '画面仅在本地显示，不会上传' }}
      </span>
    </div>

    <span v-if="isActive" class="camera-live">
      <span class="live-dot"></span>
      摄像头已开启
    </span>

    <div class="camera-actions">
      <button
        type="button"
        class="camera-btn primary"
        :disabled="isRequesting"
        @click="toggleCamera"
      >
        {{ cameraButtonText }}
      </button>
      <button
        v-if="isActive && devices.length > 1"
        type="button"
        class="camera-btn"
        :disabled="isRequesting"
        @click="switchCamera"
      >
        切换设备
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useCamera } from '../../composables/useCamera'

const videoRef = ref(null)
const {
  stream,
  status,
  errorMessage,
  devices,
  isActive,
  isRequesting,
  startCamera,
  stopCamera,
  switchCamera,
} = useCamera()

const cameraButtonText = computed(() => {
  if (isRequesting.value) return '正在开启...'
  return isActive.value ? '关闭摄像头' : '开启摄像头'
})

watch(
  [stream, videoRef],
  ([currentStream, videoElement]) => {
    if (!videoElement) return
    videoElement.srcObject = currentStream
    if (currentStream) {
      videoElement.play().catch(() => {})
    }
  },
  { immediate: true },
)

function toggleCamera() {
  if (isActive.value) {
    stopCamera()
  } else {
    startCamera()
  }
}

defineExpose({ stopCamera })
</script>

<style scoped>
.camera-preview {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--neutral-100);
  display: flex;
  align-items: center;
  justify-content: center;
}

.camera-preview.active {
  background: #111827;
}

.camera-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scaleX(-1);
}

.camera-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px;
  color: var(--neutral-400);
  text-align: center;
}

.camera-title {
  color: var(--neutral-600);
  font-size: var(--text-sm);
  font-weight: 600;
}

.camera-hint {
  max-width: 240px;
  color: var(--neutral-400);
  font-size: 11px;
  line-height: 1.35;
}

.camera-live {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: var(--radius-full);
  background: rgba(17, 24, 39, 0.72);
  color: white;
  font-size: 10px;
  backdrop-filter: blur(4px);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-400);
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.18);
}

.camera-actions {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 10px;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.camera-btn {
  padding: 5px 10px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--radius-full);
  background: rgba(17, 24, 39, 0.72);
  color: white;
  font-size: 11px;
  transition: all var(--duration-fast);
  backdrop-filter: blur(4px);
}

.camera-preview:not(.active) .camera-btn {
  border-color: var(--neutral-300);
  background: var(--surface-elevated);
  color: var(--neutral-700);
}

.camera-btn.primary:hover:not(:disabled) {
  border-color: var(--accent-400);
  background: var(--accent-600);
  color: white;
}

.camera-btn:disabled {
  cursor: wait;
  opacity: 0.65;
}
</style>
