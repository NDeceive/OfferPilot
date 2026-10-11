<template>
  <div class="video-tile" :class="{ 'is-cam-off': !camOn }">
    <video
      ref="videoRef"
      class="tile-video"
      :class="{ mirrored: local }"
      :muted="local"
      autoplay
      playsinline
    ></video>

    <!-- 摄像头关闭：track.enabled=false 是黑帧，盖一层占位。显示名首字避免黑块 -->
    <div v-if="!camOn" class="tile-placeholder">
      <div class="avatar">{{ initial }}</div>
      <span class="ph-text">{{ local ? '摄像头已关闭' : `${name} 已关闭摄像头` }}</span>
    </div>

    <div class="tile-bar">
      <span class="tile-name">{{ name }}<em v-if="local">（我）</em></span>
      <span class="state-icon" :class="{ off: !micOn }" :title="micOn ? '麦克风开' : '已静音'">
        <svg v-if="micOn" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
          <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" />
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
          <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" />
          <line x1="1" y1="1" x2="23" y2="23" />
        </svg>
      </span>
    </div>

    <div v-if="connectionState === 'failed'" class="tile-error">连接中断</div>
    <div v-else-if="connectionState === 'disconnected'" class="tile-warn">重连中…</div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  /** MediaStream（本地或远端） */
  stream: { type: Object, default: null },
  name: { type: String, default: '参会者' },
  /** 本地画面：静音自身 + 镜像自拍视角；远端画面不加 */
  local: { type: Boolean, default: false },
  micOn: { type: Boolean, default: true },
  camOn: { type: Boolean, default: true },
  /** RTCPeerConnection.connectionState：'' | connecting | connected | disconnected | failed */
  connectionState: { type: String, default: '' },
})

const videoRef = ref(null)
const initial = computed(() => (props.name || '?').trim().charAt(0) || '?')

// 绑定范式与 components/interview/CameraPreview.vue 保持一致：
// 流换了或元素挂载完成都重挂 srcObject；play() 可能被自动播放策略拒绝，静默即可
// （仍有用户手势的页面里通常不会走到这个分支）。
watch(
  [() => props.stream, videoRef],
  ([currentStream, videoElement]) => {
    if (!videoElement) return
    videoElement.srcObject = currentStream
    if (currentStream) videoElement.play().catch(() => {})
  },
  { immediate: true },
)
</script>

<style scoped>
.video-tile {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #17191c;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.tile-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.tile-video.mirrored { transform: scaleX(-1); }

.tile-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  background: #20242a;
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--accent-500);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
}
.ph-text { font-size: var(--text-xs); color: rgba(255, 255, 255, 0.55); }

.tile-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 8px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.55));
}
.tile-name {
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tile-name em { font-style: normal; font-weight: 400; color: rgba(255, 255, 255, 0.6); }
.state-icon { display: inline-flex; color: rgba(255, 255, 255, 0.9); }
.state-icon.off { color: #f87171; }

.tile-error,
.tile-warn {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}
.tile-error { background: rgba(127, 29, 29, 0.55); }
.tile-warn { background: rgba(0, 0, 0, 0.35); }
</style>
