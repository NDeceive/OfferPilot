import { computed, onUnmounted, ref } from 'vue'
import { ensureAppPermissions } from '../utils/plusPermissions'

const VIDEO_CONSTRAINTS = {
  width: { ideal: 1280 },
  height: { ideal: 720 },
  facingMode: 'user',
}

const AUDIO_CONSTRAINTS = {
  echoCancellation: true,
  noiseSuppression: true,
}

/**
 * 会议音视频采集（摄像头 + 麦克风），供视频会议室使用。
 *
 * 相对 useCamera 的差异：
 * - 同时采音频（面对面面试要能对话）；
 * - 没有麦克风 / 麦克风被占用时不整单失败：降级为纯视频进会，由 UI 依据
 *   micAvailable 提示「仅开启视频」；
 * - 静音 / 关摄像头只翻 track.enabled——不触发 WebRTC 重协商，已连的所有
 *   peer 立即生效（黑帧 / 静音帧）；
 * - 桌面浏览器无 plus 环境，ensureAppPermissions 短路 'skipped'，为 APK 留口。
 *
 * @param {{autoStop?: boolean}} [options] autoStop=false 时组件卸载不停流（共享单例用，
 *   生命周期交由 useMeetingRoom 显式管理）
 */
export function useMeetingMedia(options = {}) {
  const autoStop = options.autoStop !== false
  const stream = ref(null)
  const status = ref('idle') // idle | requesting | active | error
  const errorMessage = ref('')
  const micEnabled = ref(true)
  const camEnabled = ref(true)
  const micAvailable = ref(true)
  let disposed = false
  let requestId = 0

  const isActive = computed(() => status.value === 'active')
  const isRequesting = computed(() => status.value === 'requesting')

  function stopTracks() {
    if (!stream.value) return
    stream.value.getTracks().forEach((track) => track.stop())
    stream.value = null
  }

  /**
   * 采流，带「音频设备拉不起来就退纯视频」的降级。
   * 判断方式：整体失败后单独再试一次 video-only，成功 = 锅在音频侧。
   */
  async function acquireStream() {
    try {
      return {
        stream: await navigator.mediaDevices.getUserMedia({ video: VIDEO_CONSTRAINTS, audio: AUDIO_CONSTRAINTS }),
        micMissing: false,
      }
    } catch (error) {
      const videoOnly = await navigator.mediaDevices
        .getUserMedia({ video: VIDEO_CONSTRAINTS, audio: false })
        .catch(() => null)
      if (videoOnly) return { stream: videoOnly, micMissing: true }
      throw error
    }
  }

  async function start() {
    if (status.value === 'active' || status.value === 'requesting') return
    if (!navigator.mediaDevices?.getUserMedia) {
      status.value = 'error'
      errorMessage.value = `当前环境不支持摄像头/麦克风（mediaDevices 缺失，secureContext=${window.isSecureContext}）。局域网 http 页面请改用 localhost 打开，或给 Chrome 加 --unsafely-treat-insecure-origin-as-secure 参数。`
      return
    }

    status.value = 'requesting'
    errorMessage.value = ''
    const currentRequestId = ++requestId
    // App（5+ 运行时）里系统权限必须先主动申请，getUserMedia 才能拿到设备
    const appPermission = await ensureAppPermissions([
      'android.permission.CAMERA',
      'android.permission.RECORD_AUDIO',
    ])

    try {
      const { stream: newStream, micMissing } = await acquireStream()
      if (disposed || currentRequestId !== requestId) {
        newStream.getTracks().forEach((track) => track.stop())
        return
      }
      stream.value = newStream
      micAvailable.value = !micMissing && newStream.getAudioTracks().length > 0
      micEnabled.value = micAvailable.value
      camEnabled.value = newStream.getVideoTracks().length > 0
      status.value = 'active'
    } catch (error) {
      if (disposed || currentRequestId !== requestId) return
      status.value = 'error'
      errorMessage.value = describeError(error, appPermission)
    }
  }

  function stop() {
    requestId++
    stopTracks()
    micEnabled.value = true
    camEnabled.value = true
    micAvailable.value = true
    errorMessage.value = ''
    status.value = 'idle'
  }

  /** 静音/取消静音（翻 track.enabled，不重协商）。返回翻完的状态。 */
  function toggleMic() {
    const track = stream.value?.getAudioTracks()[0]
    if (!track) return false
    track.enabled = !track.enabled
    micEnabled.value = track.enabled
    return micEnabled.value
  }

  /** 关/开摄像头（同上）。返回翻完的状态。 */
  function toggleCam() {
    const track = stream.value?.getVideoTracks()[0]
    if (!track) return true
    track.enabled = !track.enabled
    camEnabled.value = track.enabled
    return camEnabled.value
  }

  function describeError(error, appPermission) {
    if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
      if (appPermission === 'denied') {
        return '摄像头/麦克风权限被拒绝：请到手机「设置 → 应用 → OfferPilot → 权限」中允许「相机」与「麦克风」，然后回来重试。'
      }
      if (appPermission === 'granted') {
        return '系统权限已允许，但摄像头/麦克风请求仍被 WebView 拒绝（NotAllowedError），请重启 App 后重试。'
      }
      return '摄像头/麦克风权限被拒绝，请在浏览器地址栏的权限提示里选择「允许」。'
    }
    if (error?.name === 'NotFoundError' || error?.name === 'OverconstrainedError') {
      return '没有找到可用的摄像头设备。'
    }
    if (error?.name === 'NotReadableError' || error?.name === 'AbortError') {
      return '摄像头可能正被其他程序占用（比如另一个页面开着面试），请关闭后重试。'
    }
    return '摄像头/麦克风开启失败，请检查设备和浏览器权限。'
  }

  onUnmounted(() => {
    if (!autoStop) return
    disposed = true
    requestId++
    stopTracks()
  })

  return {
    stream,
    status,
    errorMessage,
    micEnabled,
    camEnabled,
    micAvailable,
    isActive,
    isRequesting,
    start,
    stop,
    toggleMic,
    toggleCam,
  }
}

let sharedMedia = null

/**
 * 会议流程共用的采集实例：「加入会议」页的设备预览与会议室复用同一条流，
 * 避免进房瞬间「先 stop 再 getUserMedia」的摄像头空档（灯闪 + Windows 上偶发
 * NotReadableError）。autoStop 关闭——流由 useMeetingRoom 的 leave/fail 显式停；
 * 「加入会议」页预览后未进房就离开的场景由其自身在卸载时 stop（见 MeetingJoin）。
 */
export function useMeetingMediaShared() {
  if (!sharedMedia) sharedMedia = useMeetingMedia({ autoStop: false })
  return sharedMedia
}
