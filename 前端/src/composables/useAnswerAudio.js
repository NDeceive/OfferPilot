import { ref } from 'vue'
import request from '../utils/request'

/**
 * 每题作答录音。
 *
 * 和 ASR 分开走：ASR 按静音切段送转写，这里只保一份整段录音，
 * 交卷时编码成 16k 单声道 WAV 上传。上传失败进队列，下一题再试一次。
 */

let stream = null
let recorder = null
let chunks = []
let ctx = null
let audioCtx = null

const queue = []
const recording = ref(false)

function releaseStream() {
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
}

function encodeWav(buffer, targetRate = 16000) {
  const srcRate = buffer.sampleRate
  const ch0 = buffer.getChannelData(0)
  const ch1 = buffer.numberOfChannels > 1 ? buffer.getChannelData(1) : null
  const ratio = srcRate / targetRate
  const length = Math.max(0, Math.floor(ch0.length / ratio))
  const pcm = new Int16Array(length)
  for (let i = 0; i < length; i++) {
    const idx = Math.floor(i * ratio)
    let s = ch0[idx] || 0
    if (ch1) s = (s + (ch1[idx] || 0)) / 2
    pcm[i] = Math.max(-1, Math.min(1, s)) * 0x7fff
  }
  const buf = new ArrayBuffer(44 + pcm.length * 2)
  const view = new DataView(buf)
  const str = (o, s) => { for (let i = 0; i < s.length; i++) view.setUint8(o + i, s.charCodeAt(i)) }
  str(0, 'RIFF'); view.setUint32(4, 36 + pcm.length * 2, true); str(8, 'WAVE')
  str(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, targetRate, true); view.setUint32(28, targetRate * 2, true)
  view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  str(36, 'data'); view.setUint32(40, pcm.length * 2, true)
  new Int16Array(buf, 44).set(pcm)
  return new Blob([buf], { type: 'audio/wav' })
}

async function toWav(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)()
  const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer.slice(0))
  return encodeWav(audioBuffer)
}

async function upload(item) {
  const form = new FormData()
  form.append('file', item.blob, 'answer.wav')
  if (item.questionId) form.append('questionId', item.questionId)
  await request.post('/interview/' + item.sessionId + '/answers/' + item.roundNo + '/audio', form)
}

async function flushQueue() {
  while (queue.length) {
    const item = queue[0]
    try {
      await upload(item)
      queue.shift()
    } catch (e) {
      return
    }
  }
}

export function useAnswerAudio() {
  function supported() {
    return typeof window !== 'undefined' && !!window.MediaRecorder && !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
  }

  async function start(next) {
    if (!supported() || !next || !next.sessionId) return
    discard()
    ctx = { ...next }
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      ctx.failed = 'mic_denied'
      return
    }
    chunks = []
    try {
      recorder = new MediaRecorder(stream)
    } catch (e) {
      releaseStream()
      recorder = null
      ctx.failed = 'mic_denied'
      return
    }
    recorder.ondataavailable = e => { if (e.data && e.data.size) chunks.push(e.data) }
    recorder.start(1000)
    recording.value = true
    flushQueue()
  }

  /** 停止并上传当前这一题的录音 */
  function stopAndUpload() {
    if (!recorder || !ctx) return Promise.resolve()
    const current = ctx
    const currentRecorder = recorder
    const parts = chunks
    recorder = null
    chunks = []
    recording.value = false

    return new Promise(resolve => {
      currentRecorder.onstop = async () => {
        releaseStream()
        if (current.failed || !parts.length) { resolve(); return }
        try {
          const raw = new Blob(parts, { type: currentRecorder.mimeType || 'audio/webm' })
          const wav = await toWav(raw)
          if (wav.size <= 44) { resolve(); return }
          const item = { ...current, blob: wav }
          if (queue.length >= 3) queue.shift()
          try {
            await upload(item)
          } catch (e) {
            queue.push(item)
          }
        } catch (e) {
          /* 编码失败就放弃这一题 */
        }
        resolve()
      }
      try { currentRecorder.stop() } catch (e) { releaseStream(); resolve() }
    })
  }

  /** 直接丢弃（离开页面、结束面试又不需要上传时） */
  function discard() {
    if (recorder) {
      try { recorder.stop() } catch (e) { /* ignore */ }
    }
    recorder = null
    chunks = []
    releaseStream()
    recording.value = false
    ctx = null
  }

  return { recording, supported, start, stopAndUpload, discard }
}
