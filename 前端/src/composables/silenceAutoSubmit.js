export const ANSWER_SILENCE_MS = 7000
export const MIN_ANSWER_VOICED_MS = 280

// Tracks an entire answer, independently of the 1.5 s ASR segment boundary.
export function createSilenceAutoSubmit({
  silenceMs = ANSWER_SILENCE_MS,
  minVoicedMs = MIN_ANSWER_VOICED_MS,
} = {}) {
  let lastVoiceAt = null
  let voicedMs = 0
  let triggered = false

  return {
    observe({ voiced, frameDurationMs, now }) {
      if (triggered) return false
      if (voiced) {
        voicedMs += frameDurationMs
        lastVoiceAt = now
        return false
      }
      if (lastVoiceAt === null || voicedMs < minVoicedMs) return false
      if (now - lastVoiceAt < silenceMs) return false
      triggered = true
      return true
    },
    reset() {
      lastVoiceAt = null
      voicedMs = 0
      triggered = false
    },
  }
}
