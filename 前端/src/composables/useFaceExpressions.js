import { ref, watch, onBeforeUnmount } from 'vue'
import { displayExpression, EXPRESSION_INTERVAL_MS, EXPRESSION_KEYS } from '../utils/expressions'
let modelsPromise
async function loadModels() {
  if (!modelsPromise) modelsPromise = (async () => {
    const faceapi = await import('face-api.js')
    const uri = import.meta.env.BASE_URL + 'models/face-api'
    await Promise.all([faceapi.nets.tinyFaceDetector.loadFromUri(uri), faceapi.nets.faceExpressionNet.loadFromUri(uri)])
    return faceapi
  })().catch(error => { modelsPromise = null; throw error })
  return modelsPromise
}
export function useFaceExpressions(videoRef, isActive, props, emit) {
  const expressionState = ref('closed'), expressionResult = ref(null), updatedAt = ref(null), updating = ref(false), failureReason = ref('')
  let timer, generation = 0, running = false, disposed = false, failures = 0, previous = null
  function stopExpressions() { generation++; clearTimeout(timer); updating.value = false }
  function enabled() { return isActive.value && props.sessionId && props.questionId != null && props.roundNo > 0 && props.clockReady !== false && !props.paused }
  function schedule(token, delay) { if (!disposed && token === generation && enabled()) timer = setTimeout(() => sample(token), delay) }
  async function sample(token) {
    if (disposed || token !== generation || !enabled()) return
    if (running) { schedule(token, 100); return }
    const video = videoRef.value
    if (!video || video.readyState < 2 || !video.videoWidth) { expressionState.value = 'waitingVideo'; schedule(token, 500); return }
    running = true; updating.value = true
    const tick = performance.now()
    const context = { questionId: props.questionId, roundNo: props.roundNo }
    try {
      if (!expressionResult.value) expressionState.value = 'loading'
      const api = await loadModels()
      if (disposed || token !== generation) return
      context.capturedAt = Math.round(Date.now() + (props.serverOffset || 0))
      const result = await api.detectSingleFace(video, new api.TinyFaceDetectorOptions({ inputSize: 224, scoreThreshold: .5 })).withFaceExpressions()
      if (disposed || token !== generation) return
      const probabilities = result ? Object.fromEntries(EXPRESSION_KEYS.map(k => [k, Number(result.expressions[k])])) : null
      failures = 0; failureReason.value = ''; updatedAt.value = Date.now()
      if (result) {
        const display = displayExpression(probabilities, previous)
        previous = display.probabilities
        expressionResult.value = { key: display.key, probability: display.key ? display.probabilities[display.key] : null }
        expressionState.value = 'active'
      } else { previous = null; expressionResult.value = null; expressionState.value = 'noFace' }
      emit('expression-sample', { ...context, sampleId: crypto.randomUUID(), faceDetected: !!result, probabilities })
    } catch (error) {
      if (token !== generation || disposed) return
      failures++; expressionState.value = 'error'; failureReason.value = '模型或推理暂不可用，正在重试'
      console.warn('Face expression inference failed', error)
    } finally {
      running = false
      if (token === generation) updating.value = false
    }
    if (failures >= 5) { failureReason.value = '自动重试已停止，请检查网络后重试识别'; return }
    schedule(token, failures ? Math.min(30000, 2000 * 2 ** Math.min(failures, 4)) : Math.max(0, EXPRESSION_INTERVAL_MS - (performance.now() - tick)))
  }
  function retryExpressions() { stopExpressions(); failures = 0; failureReason.value = ''; if (enabled()) sample(generation) }
  watch(() => [isActive.value, props.sessionId, props.questionId, props.roundNo, props.paused, props.clockReady], () => {
    stopExpressions(); previous = null; expressionResult.value = null; updatedAt.value = null
    if (enabled()) sample(generation)
    else expressionState.value = !isActive.value ? 'closed' : props.paused ? 'paused' : props.clockReady === false ? 'syncing' : 'waitingQuestion'
  }, { immediate: true })
  onBeforeUnmount(() => { disposed = true; stopExpressions() })
  return { expressionState, expressionResult, updatedAt, updating, failureReason, stopExpressions, retryExpressions }
}
