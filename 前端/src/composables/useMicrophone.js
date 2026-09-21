import { computed, onUnmounted, ref } from 'vue'

const TARGET_SAMPLE_RATE = 16000
const SPEECH_THRESHOLD = 0.018
const SILENCE_TO_FINISH_MS = 1500
const MIN_VOICED_MS = 280
const MAX_SEGMENT_MS = 28000
const PRE_ROLL_MS = 300

export function useMicrophone({ onSegment } = {}) {
  const status = ref('idle')
  const level = ref(0)
  const isSpeaking = ref(false)
  const errorMessage = ref('')

  let stream = null
  let audioContext = null
  let sourceNode = null
  let processorNode = null
  let silentGainNode = null
  let disposed = false
  let requestId = 0
  let segmentChunks = []
  let preRollChunks = []
  let segmentStartedAt = 0
  let lastVoiceAt = 0
  let voicedDurationMs = 0

  const isMicOn = computed(() => status.value === 'open')
  const isRequesting = computed(() => status.value === 'requesting')
  const isReady = computed(() => Boolean(stream))

  async function initializeMicrophone() {
    if (stream) return true
    if (!navigator.mediaDevices?.getUserMedia || !window.AudioContext) {
      status.value = 'error'
      errorMessage.value = '当前浏览器不支持麦克风，请使用最新版 Chrome 或 Edge。'
      return false
    }

    status.value = 'requesting'
    errorMessage.value = ''
    const currentRequestId = ++requestId
    let mediaGranted = false

    try {
      const newStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          channelCount: 1,
        },
        video: false,
      })
      mediaGranted = true

      if (disposed || currentRequestId !== requestId) {
        newStream.getTracks().forEach((track) => track.stop())
        return false
      }

      stream = newStream
      audioContext = new AudioContext()
      await audioContext.audioWorklet.addModule('/audio/pcm-capture-processor.js')
      sourceNode = audioContext.createMediaStreamSource(stream)
      processorNode = new AudioWorkletNode(audioContext, 'pcm-capture-processor')
      silentGainNode = audioContext.createGain()
      silentGainNode.gain.value = 0
      processorNode.port.onmessage = ({ data }) => handleAudioChunk(new Float32Array(data))
      sourceNode.connect(processorNode)
      processorNode.connect(silentGainNode)
      silentGainNode.connect(audioContext.destination)
      stream.getAudioTracks().forEach((track) => { track.enabled = true })
      await audioContext.resume()
      status.value = 'open'
      return true
    } catch (error) {
      cleanupAudioGraph()
      status.value = 'error'
      if (mediaGranted) {
        errorMessage.value = '录音组件初始化失败，请刷新页面后重试。'
      } else if (error?.name === 'NotAllowedError' || error?.name === 'SecurityError') {
        errorMessage.value = '麦克风权限被拒绝，请在浏览器地址栏中允许访问。'
      } else if (error?.name === 'NotFoundError') {
        errorMessage.value = '没有找到可用的麦克风设备。'
      } else if (error?.name === 'NotReadableError' || error?.name === 'AbortError') {
        errorMessage.value = '麦克风可能正被其他程序占用，请关闭后重试。'
      } else {
        errorMessage.value = '麦克风开启失败，请检查设备和浏览器权限。'
      }
      return false
    }
  }

  async function openMicrophone() {
    if (!stream) return initializeMicrophone()
    await audioContext?.resume()
    stream.getAudioTracks().forEach((track) => { track.enabled = true })
    status.value = 'open'
    errorMessage.value = ''
    return true
  }

  function muteMicrophone({ flush = true } = {}) {
    if (!stream) return
    if (flush) finishSegment()
    else resetSegment()
    stream.getAudioTracks().forEach((track) => { track.enabled = false })
    status.value = 'muted'
    isSpeaking.value = false
    level.value = 0
  }

  function toggleMicrophone() {
    return isMicOn.value ? muteMicrophone() : openMicrophone()
  }

  function handleAudioChunk(chunk) {
    if (!isMicOn.value) return

    const now = Date.now()
    const chunkDurationMs = chunk.length / audioContext.sampleRate * 1000
    let energy = 0
    for (let i = 0; i < chunk.length; i++) {
      energy += chunk[i] * chunk[i]
    }
    const rms = Math.sqrt(energy / chunk.length)
    level.value = Math.min(1, level.value * 0.62 + rms * 8 * 0.38)
    const hasVoice = rms >= SPEECH_THRESHOLD

    if (!segmentStartedAt) {
      preRollChunks.push(chunk)
      trimPreRoll(audioContext.sampleRate)
      if (!hasVoice) return

      segmentStartedAt = now
      lastVoiceAt = now
      voicedDurationMs = chunkDurationMs
      segmentChunks = preRollChunks
      preRollChunks = []
      isSpeaking.value = true
      return
    }

    segmentChunks.push(chunk)
    if (hasVoice) {
      lastVoiceAt = now
      voicedDurationMs += chunkDurationMs
      isSpeaking.value = true
    } else if (now - lastVoiceAt >= SILENCE_TO_FINISH_MS) {
      finishSegment()
      return
    }

    if (now - segmentStartedAt >= MAX_SEGMENT_MS) {
      finishSegment()
    }
  }

  function trimPreRoll(inputSampleRate) {
    const maxSamples = Math.round(inputSampleRate * PRE_ROLL_MS / 1000)
    let total = preRollChunks.reduce((sum, chunk) => sum + chunk.length, 0)
    while (preRollChunks.length > 1 && total > maxSamples) {
      total -= preRollChunks.shift().length
    }
  }

  function finishSegment() {
    if (!segmentStartedAt) return
    const chunks = segmentChunks
    const inputSampleRate = audioContext?.sampleRate || 48000
    const duration = chunks.reduce((sum, chunk) => sum + chunk.length, 0) / inputSampleRate
    const shouldSend = voicedDurationMs >= MIN_VOICED_MS && duration > 0
    resetSegment()

    if (shouldSend) {
      const merged = mergeChunks(chunks)
      const resampled = resampleAudio(merged, inputSampleRate, TARGET_SAMPLE_RATE)
      const wavBlob = encodeWav(resampled, TARGET_SAMPLE_RATE)
      onSegment?.(wavBlob, Math.min(duration, MAX_SEGMENT_MS / 1000))
    }
  }

  function resetSegment() {
    segmentChunks = []
    preRollChunks = []
    segmentStartedAt = 0
    lastVoiceAt = 0
    voicedDurationMs = 0
    isSpeaking.value = false
    level.value = 0
  }

  async function stopMicrophone({ flush = false } = {}) {
    requestId++
    if (flush) finishSegment()
    else resetSegment()
    cleanupAudioGraph()
    status.value = 'idle'
    errorMessage.value = ''
  }

  function cleanupAudioGraph() {
    processorNode?.disconnect()
    sourceNode?.disconnect()
    silentGainNode?.disconnect()
    if (processorNode) processorNode.port.onmessage = null
    stream?.getTracks().forEach((track) => track.stop())
    audioContext?.close().catch(() => {})
    stream = null
    audioContext = null
    sourceNode = null
    processorNode = null
    silentGainNode = null
  }

  onUnmounted(() => {
    disposed = true
    stopMicrophone()
  })

  return {
    status,
    level,
    isSpeaking,
    errorMessage,
    isMicOn,
    isRequesting,
    isReady,
    openMicrophone,
    muteMicrophone,
    toggleMicrophone,
    stopMicrophone,
  }
}

function mergeChunks(chunks) {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0)
  const merged = new Float32Array(length)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(chunk, offset)
    offset += chunk.length
  }
  return merged
}

function resampleAudio(input, inputRate, outputRate) {
  if (inputRate === outputRate) return input
  const outputLength = Math.round(input.length * outputRate / inputRate)
  const output = new Float32Array(outputLength)
  const ratio = inputRate / outputRate
  for (let i = 0; i < outputLength; i++) {
    const position = i * ratio
    const left = Math.floor(position)
    const right = Math.min(left + 1, input.length - 1)
    const fraction = position - left
    output[i] = input[left] * (1 - fraction) + input[right] * fraction
  }
  return output
}

function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * 2, true)

  let offset = 44
  for (let i = 0; i < samples.length; i++, offset += 2) {
    const sample = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

function writeAscii(view, offset, text) {
  for (let i = 0; i < text.length; i++) {
    view.setUint8(offset + i, text.charCodeAt(i))
  }
}
