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
const pendingSaves = new Set()
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
  if (item.uploading) return item.uploading
  item.uploading = performUpload(item)
  try { return await item.uploading } finally { item.uploading = null }
}

async function performUpload(item) {
  if (!item.blob) {
    item.blob = await toWav(item.raw)
    if (item.blob.size <= 44) throw new Error('录音为空，请重新录音')
  }
  const form = new FormData()
  form.append('file', item.blob, 'answer.wav')
  if (item.questionId != null) form.append('questionId', item.questionId)
  const result = await request.post('/interview/' + item.sessionId + '/answers/' + item.roundNo + '/audio', form)
  item.context.uploaded = result
  item.context.pending = null
  const index = queue.indexOf(item)
  if (index >= 0) queue.splice(index, 1)
  return result
}

async function flushQueue({ required = false, sessionId } = {}) {
  for (const item of [...queue]) {
    if (sessionId && item.sessionId !== sessionId) continue
    try {
      await upload(item)
    } catch (e) {
      if (required) throw Object.assign(new Error('已提交回答的录音尚未保存，请检查网络后重试结束面试'), { code: 'AUDIO_UPLOAD_FAILED' })
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
    const current = { ...next }
    ctx = current
    let acquired
    try {
      acquired = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch (e) {
      current.failed = 'mic_denied'
      return
    }
    if (ctx !== current) { acquired.getTracks().forEach(track => track.stop()); return }
    stream = acquired
    const parts = []
    chunks = parts
    try {
      recorder = new MediaRecorder(stream)
    } catch (e) {
      releaseStream()
      recorder = null
      current.failed = 'mic_denied'
      return
    }
    recorder.ondataavailable = e => { if (e.data && e.data.size) parts.push(e.data) }
    recorder.start(1000)
    recording.value = true
    flushQueue()
  }

  /** 停止并上传当前这一题的录音 */
  function stopAndUpload({ required = false } = {}) {
    if (ctx?.saving) return ctx.saving
    if (!recorder || !ctx) {
      if (ctx?.uploaded) return Promise.resolve(ctx.uploaded)
      if (ctx?.pending) return upload(ctx.pending)
      return required ? Promise.reject(new Error('录音未就绪，请重新开启麦克风')) : Promise.resolve()
    }
    const current = ctx
    const currentRecorder = recorder
    const parts = chunks
    const currentStream = stream
    stream = null
    recorder = null
    chunks = []
    recording.value = false

    const saving = new Promise((resolve, reject) => {
      const release = () => currentStream?.getTracks().forEach(track => track.stop())
      currentRecorder.onstop = async () => {
        release()
        if (current.failed || !parts.length) { current.saveError = new Error('没有有效录音，请重新录音'); if(required) reject(current.saveError); else resolve(); return }
        const raw = new Blob(parts, { type: currentRecorder.mimeType || 'audio/webm' })
        const item = { ...current, raw, context: current }
        current.pending = item
        queue.push(item)
        try { resolve(await upload(item)) }
        catch (e) {
          if(required) reject(Object.assign(new Error('录音保存失败，请检查网络后重试'), { code: 'AUDIO_UPLOAD_FAILED' }))
          else resolve()
        }
      }
      try { currentRecorder.stop() } catch (e) { current.saveError = e; release(); if(required) reject(e); else resolve() }
    })
    current.saving = saving
    pendingSaves.add(saving)
    const done = () => { pendingSaves.delete(saving); current.saving = null }
    saving.then(done, done)
    return saving
  }

  function markSubmitted() {
    if (ctx) ctx.submitted = true
  }

  async function flushSubmitted(sessionId) {
    try {
      // A submitted main answer may still be recording while its follow-up is open.
      if (ctx?.sessionId === sessionId && ctx.submitted) {
        await stopAndUpload()
        if (ctx.saveError) throw ctx.saveError
      }
      await Promise.allSettled([...pendingSaves])
      await flushQueue({ required: true, sessionId })
    } catch (error) {
      throw Object.assign(new Error(error.code === 'AUDIO_UPLOAD_FAILED' ? error.message : '已提交回答的录音尚未保存，请检查网络后重试结束面试'), { code: 'AUDIO_UPLOAD_FAILED' })
    }
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

  return { recording, supported, start, stopAndUpload, markSubmitted, flushSubmitted, discard }
}
