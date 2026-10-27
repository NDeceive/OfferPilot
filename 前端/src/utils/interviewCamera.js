import { ensureAppPermissions } from './plusPermissions.js'
import { ref } from 'vue'

export const cameraConsentOpen = ref(false)
let resolveConsent = null

export function answerCameraConsent(allowed) {
  cameraConsentOpen.value = false
  resolveConsent?.(allowed)
  resolveConsent = null
}

function askCameraConsent() {
  cameraConsentOpen.value = true
  return new Promise(resolve => { resolveConsent = resolve })
}

let preparedStream = null
let pendingRequest = null

export async function prepareInterviewCamera() {
  if (preparedStream?.getVideoTracks().some(track => track.readyState === 'live')) return preparedStream
  if (pendingRequest) return pendingRequest
  pendingRequest = (async () => {
    if (!await askCameraConsent()) {
      const error = new Error('已取消进入面试。允许打开摄像头后才能开始面试。')
      error.code = 'CAMERA_CONSENT_DECLINED'
      throw error
    }
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('当前环境无法使用摄像头，请使用 HTTPS 或本机地址，并检查设备。')
    if (await ensureAppPermissions(['android.permission.CAMERA']) === 'denied') throw new Error('请先允许相机权限，才能进入面试。')
    try {
      preparedStream = await navigator.mediaDevices.getUserMedia({ video: { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' }, audio: false })
      return preparedStream
    } catch (error) {
      if (['NotAllowedError', 'SecurityError'].includes(error.name)) throw new Error('摄像头权限被拒绝，无法进入面试。请在浏览器或系统设置中允许摄像头后重试。')
      if (error.name === 'NotFoundError') throw new Error('未找到摄像头，连接摄像头后才能进入面试。')
      throw new Error('摄像头无法开启，请关闭占用摄像头的程序后重试。')
    }
  })()
  try { return await pendingRequest } finally { pendingRequest = null }
}

export function takeInterviewCamera() {
  const stream = preparedStream
  preparedStream = null
  return stream
}

export function releaseInterviewCamera() {
  if (cameraConsentOpen.value) answerCameraConsent(false)
  preparedStream?.getTracks().forEach(track => track.stop())
  preparedStream = null
}
