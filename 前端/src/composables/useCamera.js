import { computed, onUnmounted, ref } from 'vue'

const CAMERA_CONSTRAINTS = {
  width: { ideal: 1280 },
  height: { ideal: 720 },
  facingMode: 'user',
}

export function useCamera() {
  const stream = ref(null)
  const status = ref('idle')
  const errorMessage = ref('')
  const devices = ref([])
  const currentDeviceId = ref('')
  let disposed = false
  let requestId = 0

  const isActive = computed(() => status.value === 'active')
  const isRequesting = computed(() => status.value === 'requesting')

  function stopTracks() {
    if (!stream.value) return
    stream.value.getTracks().forEach((track) => track.stop())
    stream.value = null
  }

  async function refreshDevices() {
    if (!navigator.mediaDevices?.enumerateDevices) return
    try {
      const allDevices = await navigator.mediaDevices.enumerateDevices()
      devices.value = allDevices.filter((device) => device.kind === 'videoinput')
    } catch {
      devices.value = []
    }
  }

  async function startCamera(deviceId = '') {
    if (!navigator.mediaDevices?.getUserMedia) {
      status.value = 'error'
      errorMessage.value = '当前浏览器不支持摄像头，请使用最新版 Chrome 或 Edge。'
      return
    }

    status.value = 'requesting'
    errorMessage.value = ''
    const currentRequestId = ++requestId

    try {
      stopTracks()
      const videoConstraints = deviceId
        ? { ...CAMERA_CONSTRAINTS, deviceId: { exact: deviceId } }
        : CAMERA_CONSTRAINTS
      const newStream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraints,
        audio: false,
      })

      if (disposed || currentRequestId !== requestId) {
        newStream.getTracks().forEach((track) => track.stop())
        return
      }

      stream.value = newStream
      currentDeviceId.value = newStream.getVideoTracks()[0]?.getSettings().deviceId || deviceId
      status.value = 'active'
      await refreshDevices()
    } catch (error) {
      if (disposed || currentRequestId !== requestId) return
      status.value = 'error'
      if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
        errorMessage.value = '摄像头权限被拒绝，请在浏览器地址栏中允许访问。'
      } else if (error?.name === 'NotFoundError' || error?.name === 'OverconstrainedError') {
        errorMessage.value = '没有找到可用的摄像头设备。'
      } else if (error?.name === 'NotReadableError' || error?.name === 'AbortError') {
        errorMessage.value = '摄像头可能正被其他程序占用，请关闭后重试。'
      } else {
        errorMessage.value = '摄像头开启失败，请检查设备和浏览器权限。'
      }
    }
  }

  function stopCamera() {
    requestId++
    stopTracks()
    currentDeviceId.value = ''
    errorMessage.value = ''
    status.value = 'idle'
  }

  async function switchCamera() {
    if (devices.value.length < 2 || isRequesting.value) return
    const currentIndex = devices.value.findIndex(
      (device) => device.deviceId === currentDeviceId.value,
    )
    const nextIndex = currentIndex >= 0 ? (currentIndex + 1) % devices.value.length : 0
    await startCamera(devices.value[nextIndex].deviceId)
  }

  onUnmounted(() => {
    disposed = true
    requestId++
    stopTracks()
  })

  return {
    stream,
    status,
    errorMessage,
    devices,
    isActive,
    isRequesting,
    startCamera,
    stopCamera,
    switchCamera,
  }
}
