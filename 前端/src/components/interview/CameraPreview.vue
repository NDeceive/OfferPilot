<template><section class="camera-preview expression-result" :class="{ready:expressionState==='active', compact}" aria-label="摄像头人像"><div class="camera-viewport" :class="{active:isActive}"><video v-show="isActive" ref="videoRef" class="camera-video" autoplay muted playsinline disablepictureinpicture disableremoteplayback></video><div v-if="!isActive" class="camera-placeholder" aria-hidden="true"><svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg></div></div></section></template>
<script setup>
import { computed, ref, watch } from 'vue'
import { useCamera } from '../../composables/useCamera'
import { useFaceExpressions } from '../../composables/useFaceExpressions'
import { EXPRESSION_LABELS } from '../../utils/expressions'
const props = defineProps({ compact: Boolean, sessionId: Number, questionId: Number, roundNo: Number, paused: Boolean, serverOffset: Number, clockReady: { type: Boolean, default: true } })
const emit = defineEmits(['expression-sample', 'expression-state', 'retry-clock'])
const videoRef = ref(null)
const { stream, status, errorMessage, devices, isActive, isRequesting, startCamera, stopCamera, switchCamera } = useCamera()
const { expressionState, expressionResult, updatedAt, updating, failureReason, stopExpressions, retryExpressions } = useFaceExpressions(videoRef, isActive, props, emit)
const resultLabel = computed(() => expressionState.value === 'active' ? (EXPRESSION_LABELS[expressionResult.value?.key] || '暂不明确') : ({
  closed: '等待开启', paused: '采样已暂停', noFace: '未检测到人脸', error: '识别暂不可用',
  loading: '正在准备识别', syncing: '正在同步时间', waitingQuestion: '等待题目开始', waitingVideo: '等待摄像头画面',
}[expressionState.value] || '等待识别'))
const stateLabel = computed(() => ({
  closed: '开启摄像头后自动采样', paused: '继续面试后恢复记录', noFace: '请正对摄像头，保持脸部清晰',
  error: failureReason.value, loading: '首次加载本地模型', syncing: '同步完成后开始记录',
  waitingQuestion: '会话和题目准备完成后自动开始', waitingVideo: '等待可用的视频帧',
}[expressionState.value] || (updating.value ? '采样中 · 保留最近一次结果' : '约每 2 秒更新')))
watch(() => [expressionState.value, expressionResult.value, updatedAt.value, updating.value, isRequesting.value, devices.value, status.value], () => emit('expression-state', { state: expressionState.value, cameraActive:isActive.value, deviceCount:devices.value.length, cameraError:errorMessage.value, requesting: isRequesting.value, label: resultLabel.value, status: stateLabel.value, key: expressionState.value === 'active' ? expressionResult.value?.key : null, probability: expressionState.value === 'active' ? expressionResult.value?.probability : null }), { immediate: true })
watch([stream, videoRef], ([currentStream, video]) => { if (video) { video.srcObject = currentStream; if (currentStream) video.play().catch(() => {}) } }, { immediate: true })
defineExpose({ switchCamera, startCamera, stopCamera, stopExpressions, retryExpressions })
</script>
<style scoped>
.camera-preview { width:100%; border-radius:14px; overflow:hidden; background:var(--neutral-100,#f3f4f6); } .camera-viewport { aspect-ratio:16/9; min-height:150px; } .camera-viewport.active { background:#111827; } .camera-video { width:100%; height:100%; display:block; object-fit:cover; transform:scaleX(-1); } .camera-placeholder { width:100%; height:100%; min-height:150px; display:grid; place-items:center; border:0; background:transparent; color:var(--neutral-500,#6b7280);  } .camera-placeholder:focus-visible { outline:2px solid var(--accent-600,#059669); outline-offset:-3px; }
.camera-preview.compact{width:72px;border-radius:8px}.compact .camera-viewport{min-height:0;height:44px;aspect-ratio:auto}
</style>
