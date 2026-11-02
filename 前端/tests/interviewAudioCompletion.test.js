import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import { webcrypto } from 'node:crypto'

const source = fs.readFileSync(new URL('../src/views/Interview.vue', import.meta.url), 'utf8')
const ref = value => ({ value })
function load(context, start, end) {
  vm.createContext(context)
  vm.runInContext(source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start))), context)
}

test('last answer waits for recording upload before requesting the next question or report', async () => {
  let release, finished = false, nextRequested = false, marked = false
  const upload = new Promise(resolve => { release = resolve })
  const context = {
    console, crypto: webcrypto, answer: ref('最后一题的回答'),
    isSubmitting: ref(false), isSpeechProcessing: ref(false), isPaused: ref(false),
    expressionEnding: ref(false), needsNextQuestion: ref(false), sessionId: ref(1),
    messages: ref([]), isAiTyping: ref(false), pendingAnswerRequest: null,
    currentQuestionId: ref(3), currentPromptId: ref(300), currentQuestion: ref(3),
    interviewStage: ref('PROFESSIONAL_QUESTIONS'),
    answerAudio: { markSubmitted() { marked = true }, stopAndUpload: () => upload },
    submitAnswer: async () => ({ nextAction: 'NEXT' }),
    getNextQuestion: async () => { nextRequested = true; return { nextAction: 'FINISHABLE' } },
    finishInterview: async () => { finished = true }, releaseMediaDevices() {},
    waitForReport: async () => {}, scrollToBottom() {},
    syncPlan() {},
  }
  load(context, 'async function submitAnswerFn()', 'async function skipQuestion()')
  const pending = context.submitAnswerFn()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(marked, true)
  assert.equal(nextRequested, false)
  assert.equal(finished, false)
  release()
  await pending
  assert.equal(finished, true)
})

test('finish cannot enqueue report until saved-answer uploads succeed; retry remains possible', async () => {
  let uploadFails = true, finishRequests = 0, discarded = 0
  const context = {
    console, finishPromise: null, sessionFinished: ref(false), expressionEnding: ref(false),
    finishError: ref(''), cameraPreviewRef: ref(null), expressionUploadRef: ref(null),
    microphoneRef: ref(null), isAiTyping: ref(false), showTranscript: ref(false),
    messages: ref([]), digitalHumanRef: ref(null), scrollToBottom() {}, releaseMediaDevices() {},
    answerAudio: {
      async flushSubmitted() { if (uploadFails) throw new Error('录音上传失败，请重试') },
      discard() { discarded++ },
    },
    presentInterviewClosing: async () => {},
    finishInterviewRequest: async () => { finishRequests++ },
    getInterviewResume: async () => ({ status: 'ONGOING' }),
  }
  load(context, 'function finishInterview(sid)', 'const questionTypes = ref([])')
  await assert.rejects(context.finishInterview(1), /录音上传失败/)
  assert.equal(finishRequests, 0)
  assert.equal(discarded, 0)
  assert.equal(context.expressionEnding.value, false)
  uploadFails = false
  await context.finishInterview(1)
  assert.equal(finishRequests, 1)
  assert.equal(discarded, 1)
  assert.equal(context.sessionFinished.value, true)
})
