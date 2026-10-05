<template>
  <div class="interview-page">
    <header class="topbar">
      <div class="topbar-left">
        <router-link to="/home" class="back-btn">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </router-link>
        <span class="session-tag">{{ jobTitle }} 面试</span>
      </div>
      <div class="topbar-center">
        <div class="timer" :class="{ warning: timeLeft < 300, danger: timeLeft < 60 }">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
          </svg>
          <span>{{ formatTime(timeLeft) }}</span>
        </div>
      </div>
      <div class="topbar-right">
        <button class="ctrl-btn" @click="togglePause" :title="isPaused ? '继续' : '暂停'">
          <svg v-if="!isPaused" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
        </button>
        <button class="end-btn" @click="endInterview">结束面试</button>
      </div>
    </header>

    <div class="progress-track">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>

    <div class="interview-body">
      <div class="chat-panel">
        <div class="digital-human-stage" aria-label="AI 面试官视频">
          <DigitalHumanStage
            ref="digitalHumanRef"
            :text="digitalHumanText"
            :speech-key="digitalHumanSpeechKey"
          />
        </div>

        <section class="question-strip">
          <div>
            <strong>面试题</strong>
            <p>{{ currentPrompt || '正在准备面试问题…' }}</p>
          </div>
          <button type="button" :aria-expanded="showTranscript" @click="showTranscript = !showTranscript">
            {{ showTranscript ? '收起记录' : '展开回答记录' }}
          </button>
        </section>

        <Transition name="transcript">
          <div v-if="showTranscript" class="conversation-scroll" ref="messagesRef">
            <div
              v-for="(msg, i) in messages"
              :key="i"
              :class="['conversation-entry', msg.role]"
            >
              <div class="conversation-meta">
                <span>{{ msg.role === 'ai' ? '面试官' : '我的回答' }}</span>
                <span v-if="msg.followup" class="followup-pill">追问</span>
              </div>
              <p>{{ msg.text }}</p>
            </div>

            <div v-if="answer" class="conversation-entry user draft">
              <div class="conversation-meta">
                <span>当前回答</span>
                <span class="draft-pill">{{ isSpeechProcessing ? '识别中' : '待提交' }}</span>
              </div>
              <p>{{ answer }}</p>
            </div>

            <div v-if="isAiTyping" class="conversation-entry ai typing-entry">
              <div class="conversation-meta"><span>面试官</span></div>
              <p>正在思考下一步问题……</p>
            </div>
          </div>
        </Transition>

        <div class="input-bar">
          <MicrophoneControl
            ref="microphoneRef"
            :session-id="sessionId"
            :transcript="answer"
            :disabled="isSubmitting"
            @transcript="appendSpeechTranscript"
            @processing="isSpeechProcessing = $event"
            @submit="submitAnswerFn"
            @skip="skipQuestion"
          />
        </div>
      </div>

      <aside class="info-panel">
        <div class="vr-card">
          <CameraPreview ref="cameraPreviewRef" />
        </div>

        <div class="info-card">
          <div class="q-head">
            <span class="q-num">第 {{ currentQuestion }} 题</span>
            <span class="q-of">{{ formatTime(timeLeft) }}</span>
          </div>
          <div class="q-progress">
            <div class="q-bar" :style="{ width: progressPercent + '%' }"></div>
          </div>
          <div class="q-rows">
            <div class="q-row"><span class="ql">类型</span><span class="qv">{{ questionTypes[currentQuestion - 1] || '技术问题' }}</span></div>
            <div class="q-row"><span class="ql">难度</span><span class="qv">{{ questionDifficulties[currentQuestion - 1] || '中等' }}</span></div>
            <div class="q-row"><span class="ql">考察</span><span class="qv">{{ questionSkills[currentQuestion - 1] || '综合能力' }}</span></div>
          </div>
        </div>

        <div class="info-card">
          <h3 class="info-card-title">实时评估</h3>
          <div class="eval-list">
            <div v-for="(e, i) in evalItems" :key="i" class="eval-row">
              <span class="eval-label">{{ e.name }}</span>
              <div class="eval-track">
                <div class="eval-fill" :style="{ width: (evaluationReady ? e.value : 0) + '%', background: e.color }"></div>
              </div>
              <span class="eval-val">{{ evaluationReady ? `${e.value}%` : '--' }}</span>
            </div>
          </div>
          <p class="eval-footnote">{{ evaluationReady ? '评估随回答持续更新' : '回答提交后生成实时反馈' }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { startInterview, submitAnswer, getNextQuestion, finishInterview, getReportStatus, getSessionMessages } from '../api'
import CameraPreview from '../components/interview/CameraPreview.vue'
import DigitalHumanStage from '../components/interview/DigitalHumanStage.vue'
import MicrophoneControl from '../components/interview/MicrophoneControl.vue'

const router = useRouter()
const route = useRoute()

// --- Session state ---
const sessionId = ref(null)
const currentQuestionId = ref(null)
const jobTitle = ref('前端开发工程师')
const currentQuestion = ref(1)
const totalDuration = ref(1800)
const timeLeft = ref(1800)
const answer = ref('')
const isAiTyping = ref(false)
const isPaused = ref(false)
const isSubmitting = ref(false)
const isSpeechProcessing = ref(false)
const showTranscript = ref(false)
const evaluationReady = ref(false)
const messagesRef = ref(null)
const cameraPreviewRef = ref(null)
const microphoneRef = ref(null)
const digitalHumanRef = ref(null)
const digitalHumanText = ref('')
const digitalHumanSpeechKey = ref(0)

const questionTypes = ref([])
const questionDifficulties = ref([])
const questionSkills = ref([])

const difficultyLabels = { 1: '简单', 2: '中等', 3: '困难', 4: '困难' }

const messages = ref([])

const evalItems = ref([
  { name: '表达能力', value: 0, color: '#10b981' },
  { name: '逻辑性', value: 0, color: '#3b82f6' },
  { name: '技术深度', value: 0, color: '#8b5cf6' },
])

const progressPercent = computed(() => totalDuration.value > 0
  ? ((totalDuration.value - timeLeft.value) / totalDuration.value) * 100
  : 0)
const currentPrompt = computed(() => {
  for (let index = messages.value.length - 1; index >= 0; index--) {
    if (messages.value[index].role === 'ai') return messages.value[index].text
  }
  return ''
})

let timerInterval = null
let autoFinished = false

watch([timeLeft, isSubmitting], ([seconds, submitting]) => {
  if (seconds <= 0 && sessionId.value && !submitting && !autoFinished) {
    autoFinished = true
    autoFinishInterview()
  }
})

// --- Initialize interview on mount ---
onMounted(async () => {
  // Start local timer
  timerInterval = setInterval(() => {
    if (timeLeft.value > 0 && !isPaused.value) timeLeft.value--
  }, 1000)

  // Keep the duration selected on the preparation page.
  const jobId = Number(route.query.jobId) || 1
  const durationSeconds = Math.min(7200, Math.max(300, Number(route.query.durationSeconds) || 1800))
  totalDuration.value = durationSeconds
  timeLeft.value = durationSeconds

  try {
    const existingSessionId = Number(route.query.sessionId) || 0
    if (existingSessionId) {
      sessionId.value = existingSessionId
      jobTitle.value = String(route.query.jobName || '模拟面试')
      const routeQuestion = String(route.query.question || '').trim()
      if (routeQuestion) {
        currentQuestionId.value = Number(route.query.questionId) || null
        questionTypes.value.push(mapQuestionType(route.query.questionType))
        questionDifficulties.value.push(difficultyLabels[route.query.questionDifficulty] || '中等')
        questionSkills.value.push(String(route.query.questionSkill || '综合能力'))
        messages.value.push({ role: 'ai', text: routeQuestion, followup: false })
        speakQuestion(routeQuestion)
      } else {
        const history = await getSessionMessages(existingSessionId)
        const records = Array.isArray(history) ? history : []
        const firstQuestion = records.find(item => item.role === 'INTERVIEWER' && item.msgType === 'MAIN')
        if (firstQuestion) {
          currentQuestionId.value = firstQuestion.questionId
          messages.value.push({ role: 'ai', text: firstQuestion.content, followup: false })
          speakQuestion(firstQuestion.content)
        }
      }
      return
    }

    const res = await startInterview({ jobId, durationSeconds })
    sessionId.value = res.sessionId
    jobTitle.value = res.jobName || '模拟面试'

    // Set first question
    if (res.question) {
      currentQuestionId.value = res.question.id
      questionTypes.value.push(mapQuestionType(res.question.type))
      questionDifficulties.value.push(difficultyLabels[res.question.difficulty] || '中等')
      questionSkills.value.push(res.question.abilityTag || '综合能力')
      messages.value.push({
        role: 'ai',
        text: res.question.content,
        followup: res.question.type === 'FOLLOWUP',
      })
      speakQuestion(res.question.content)
    }
  } catch (e) {
    console.error('Failed to start interview:', e)
    // Fallback: show a generic welcome and allow the user to proceed
    messages.value.push({
      role: 'ai',
      text: '面试初始化失败，请检查网络后刷新重试。',
      followup: false,
    })
  }
})

onUnmounted(() => {
  clearInterval(timerInterval)
  releaseMediaDevices()
})

// --- Helpers ---
function mapQuestionType(type) {
  const map = { MAIN: '主问题', FOLLOWUP: '追问', BEHAVIORAL: '行为面试' }
  return map[type] || '技术问题'
}

function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// --- Submit answer via API ---
async function submitAnswerFn() {
  if (!answer.value.trim() || isSubmitting.value || isSpeechProcessing.value || !sessionId.value) return
  const userAnswer = answer.value.trim()

  // Push user message
  messages.value.push({ role: 'user', text: userAnswer, followup: false })
  answer.value = ''

  isAiTyping.value = true
  isSubmitting.value = true
  scrollToBottom()

  try {
    const res = await submitAnswer(sessionId.value, {
      questionId: currentQuestionId.value,
      answer: userAnswer,
    })

    isAiTyping.value = false

    if (res.nextAction === 'FOLLOWUP' && res.followupQuestion) {
      const followupText = normalizeFollowupQuestion(res.followupQuestion)
      if (!followupText) throw new Error('追问响应缺少问题内容')
      // questionId stays the same (followup is to the same main question)
      messages.value.push({
        role: 'ai',
        text: followupText,
        followup: true,
      })
      speakQuestion(followupText)
    } else if (res.nextAction === 'NEXT') {
      // Fetch the next question from the server
      try {
        const nextRes = await getNextQuestion(sessionId.value)
        if (nextRes.question) {
          currentQuestion.value++
          currentQuestionId.value = nextRes.question.id
          questionTypes.value.push(mapQuestionType(nextRes.question.type))
          questionDifficulties.value.push(difficultyLabels[nextRes.question.difficulty] || '中等')
          questionSkills.value.push(nextRes.question.abilityTag || '综合能力')
          messages.value.push({
            role: 'ai',
            text: nextRes.question.content,
            followup: false,
          })
          speakQuestion(nextRes.question.content)
        }
      } catch (nextErr) {
        console.error('Failed to get next question:', nextErr)
        messages.value.push({
          role: 'ai',
          text: '加载下一题失败，你可以手动结束面试。',
          followup: false,
        })
      }

    } else if (res.nextAction === 'FINISHABLE') {
      // Interview can be finished - call finish
      try {
        const finishRes = await finishInterview(sessionId.value)
        releaseMediaDevices()
        messages.value.push({
          role: 'ai',
          text: '面试结束！感谢你的精彩回答。正在生成你的能力报告...',
          followup: false,
        })
        scrollToBottom()
        await waitForReport(sessionId.value)
        return
      } catch (finishErr) {
        console.error('Failed to finish interview:', finishErr)
        messages.value.push({
          role: 'ai',
          text: '面试结束但报告生成失败，你可以稍后在面试记录中查看。',
          followup: false,
        })
      }
    }

    // Update eval items if server provides them
    if (res.evalItems && Array.isArray(res.evalItems)) {
      evalItems.value = res.evalItems
      evaluationReady.value = true
    }
  } catch (e) {
    console.error('Failed to submit answer:', e)
    isAiTyping.value = false
    messages.value.push({
      role: 'ai',
      text: '提交回答失败，请检查网络后重试。',
      followup: false,
    })
  } finally {
    isSubmitting.value = false
    scrollToBottom()
  }
}

async function skipQuestion() {
  if (isSubmitting.value || isSpeechProcessing.value || !sessionId.value) return
  isSubmitting.value = true
  isAiTyping.value = true
  scrollToBottom()

  try {
    const res = await submitAnswer(sessionId.value, {
      questionId: currentQuestionId.value,
      answer: '',
    })

    isAiTyping.value = false

    if (res.nextAction === 'NEXT' && res.question) {
      currentQuestion.value++
      currentQuestionId.value = res.question.id
      questionTypes.value.push(mapQuestionType(res.question.type))
      questionDifficulties.value.push(difficultyLabels[res.question.difficulty] || '中等')
      questionSkills.value.push(res.question.abilityTag || '综合能力')
      messages.value.push({ role: 'ai', text: res.question.content, followup: false })
      speakQuestion(res.question.content)
    } else if (res.nextAction === 'FOLLOWUP' && res.followupQuestion) {
      const followupText = normalizeFollowupQuestion(res.followupQuestion)
      if (!followupText) throw new Error('追问响应缺少问题内容')
      if (typeof res.followupQuestion !== 'string' && res.followupQuestion.id) {
        currentQuestionId.value = res.followupQuestion.id
      }
      messages.value.push({ role: 'ai', text: followupText, followup: true })
      speakQuestion(followupText)
    } else if (res.nextAction === 'FINISHED') {
      releaseMediaDevices()
      messages.value.push({
        role: 'ai',
        text: '面试结束！感谢你的精彩回答。正在生成你的能力报告...',
        followup: false,
      })
      const reportId = res || 1
      setTimeout(() => router.push(`/history/${reportId}`), 2000)
    }

  } catch (e) {
    console.error('Failed to skip question:', e)
    isAiTyping.value = false
    messages.value.push({ role: 'ai', text: '操作失败，请重试。', followup: false })
  } finally {
    isSubmitting.value = false
    scrollToBottom()
  }
}

function appendSpeechTranscript(text) {
  const normalized = text?.trim()
  if (!normalized) return
  answer.value = answer.value.trim()
    ? `${answer.value.trim()} ${normalized}`
    : normalized
  scrollToBottom()
}

function normalizeFollowupQuestion(value) {
  if (typeof value === 'string') return value.trim()
  return String(value?.content || value?.followUpQuestion || value?.question || '').trim()
}

function togglePause() {
  isPaused.value = !isPaused.value
  if (isPaused.value) digitalHumanRef.value?.stop()
}

async function endInterview() {
  releaseMediaDevices()
  if (sessionId.value) {
    try {
      const res = await finishInterview(sessionId.value)
      await waitForReport(sessionId.value)
      return
    } catch (e) {
      console.error('Failed to finish interview:', e)
    }
  }
  // Fallback navigation
  router.push('/history')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function autoFinishInterview() {
  releaseMediaDevices()
  isAiTyping.value = true
  try {
    const finishRes = await finishInterview(sessionId.value)
    messages.value.push({
      role: 'ai',
      text: '面试时间已到，正在生成你的能力报告…',
      followup: false,
    })
    scrollToBottom()
    await waitForReport(sessionId.value)
  } catch (e) {
    console.error('Auto-finish failed:', e)
    messages.value.push({
      role: 'ai',
      text: '面试结束但报告生成失败，你可以稍后在面试记录中查看。',
      followup: false,
    })
  } finally {
    isAiTyping.value = false
    isSubmitting.value = false
  }
}

function speakQuestion(text) {
  const normalized = text?.trim()
  if (!normalized) return
  digitalHumanText.value = normalized
  digitalHumanSpeechKey.value++
}

function releaseMediaDevices() {
  cameraPreviewRef.value?.stopCamera()
  microphoneRef.value?.stopMicrophone()
  digitalHumanRef.value?.close()
}

async function waitForReport(sid) {
  for (let attempt = 0; attempt < 60; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    try {
      const status = await getReportStatus(sid)
      if (status?.ready && status.reportId) {
        router.push(`/history/${status.reportId}`)
        return
      }
    } catch (error) {
      console.warn('Report is not ready yet:', error)
    }
  }
  router.push('/history')
}
</script>

<style scoped>
.interview-page {
  height: 100dvh;
  overflow: hidden;
  background: var(--surface-primary);
  display: flex;
  flex-direction: column;
}

/* Topbar */
.topbar {
  height: 56px;
  background: var(--surface-elevated);
  border-bottom: 1px solid var(--neutral-200);
  padding: 0 var(--space-6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
}
.topbar-left, .topbar-right { display: flex; align-items: center; gap: var(--space-3); }
.topbar-center { display: flex; align-items: center; }
.back-btn { color: var(--neutral-500); padding: var(--space-2); border-radius: var(--radius-sm); display: flex; transition: all var(--duration-fast); }
.back-btn:hover { color: var(--neutral-700); background: var(--neutral-100); }
.session-tag { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-700); padding: var(--space-1) var(--space-3); background: var(--neutral-100); border-radius: var(--radius-full); }
.timer { display: flex; align-items: center; gap: var(--space-2); font-family: var(--font-mono); font-size: var(--text-lg); font-weight: 600; color: var(--accent-600); }
.timer.warning { color: var(--color-warning); }
.timer.danger { color: var(--color-error); }
.ctrl-btn { width: 36px; height: 36px; border-radius: var(--radius-sm); border: 1px solid var(--neutral-200); background: var(--surface-elevated); color: var(--neutral-600); display: flex; align-items: center; justify-content: center; transition: all var(--duration-fast); }
.ctrl-btn:hover { border-color: var(--neutral-300); background: var(--neutral-50); }
.end-btn { padding: var(--space-2) var(--space-4); border: 1px solid rgba(239,68,68,0.3); border-radius: var(--radius-sm); background: rgba(239,68,68,0.05); color: var(--color-error); font-size: var(--text-sm); font-weight: 500; transition: all var(--duration-fast); }
.end-btn:hover { background: rgba(239,68,68,0.1); }

/* Progress */
.progress-track { position: fixed; top: 56px; left: 0; right: 0; height: 3px; background: var(--neutral-200); z-index: 49; }
.progress-fill { height: 100%; background: var(--accent-500); }

/* Body */
.interview-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 320px;
  margin-top: 59px;
  height: calc(100dvh - 59px);
  overflow: hidden;
}

/* Digital Human + Conversation Panel */
.chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.digital-human-stage {
  flex: 1;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 10px 14px 0;
  background:
    radial-gradient(circle at 50% 45%, rgba(59, 130, 246, 0.08), transparent 42%),
    linear-gradient(180deg, var(--surface-primary), var(--neutral-50));
}

/* Scrollable conversation and bottom controls */
.input-bar {
  height: clamp(300px, 36vh, 370px);
  border-top: 1px solid var(--neutral-200);
  background: var(--surface-elevated);
  padding: var(--space-3) var(--space-5);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.conversation-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 2px var(--space-2) var(--space-2);
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
}

.conversation-entry {
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--neutral-100);
}

.conversation-entry:last-child {
  border-bottom: none;
}

.conversation-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 4px;
  color: var(--neutral-500);
  font-size: 11px;
  font-weight: 600;
}

.conversation-entry.ai .conversation-meta {
  color: var(--accent-700);
}

.conversation-entry.user .conversation-meta {
  color: #2563eb;
}

.conversation-entry p {
  color: var(--neutral-800);
  font-size: var(--text-sm);
  line-height: 1.65;
  white-space: pre-wrap;
}

.conversation-entry.draft {
  background: rgba(59, 130, 246, 0.035);
}

.followup-pill,
.draft-pill {
  padding: 1px 7px;
  border-radius: var(--radius-full);
  background: var(--accent-50);
  color: var(--accent-700);
  font-size: 10px;
}

.draft-pill {
  background: rgba(59, 130, 246, 0.08);
  color: #2563eb;
}

.typing-entry p {
  color: var(--neutral-400);
}

/* Info Panel — independent scrolling column */
.info-panel {
  width: auto;
  flex-shrink: 0;
  height: 100%;
  overflow-y: auto;
  background: var(--surface-elevated);
  border-left: 1px solid var(--neutral-200);
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  scroll-behavior: smooth;
}
.vr-card { aspect-ratio: 16/9; background: var(--neutral-100); border-radius: var(--radius-lg); border: 1px solid var(--neutral-200); display: flex; align-items: center; justify-content: center; flex-shrink: 0; position: sticky; top: 0; z-index: 5; overflow: hidden; }
.info-card { background: var(--neutral-50); border-radius: var(--radius-lg); padding: var(--space-4); flex-shrink: 0; }
.info-card-title { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-700); margin-bottom: var(--space-3); }
.q-head { display: flex; align-items: baseline; gap: var(--space-1); margin-bottom: var(--space-3); }
.q-num { font-family: var(--font-mono); font-size: var(--text-lg); font-weight: 700; color: var(--accent-600); }
.q-of { font-size: var(--text-sm); color: var(--neutral-500); }
.q-progress { height: 4px; background: var(--neutral-200); border-radius: 2px; margin-bottom: var(--space-3); overflow: hidden; }
.q-bar { height: 100%; background: var(--accent-500); border-radius: 2px; }
.q-rows { display: flex; flex-direction: column; gap: var(--space-2); }
.q-row { display: flex; justify-content: space-between; }
.ql { font-size: var(--text-sm); color: var(--neutral-500); }
.qv { font-size: var(--text-sm); font-weight: 500; color: var(--neutral-700); }
.eval-list { display: flex; flex-direction: column; gap: var(--space-3); }
.eval-row { display: flex; align-items: center; gap: var(--space-3); }
.eval-label { width: 60px; font-size: var(--text-xs); color: var(--neutral-600); flex-shrink: 0; }
.eval-track { flex: 1; height: 6px; background: var(--neutral-200); border-radius: 3px; overflow: hidden; }
.eval-fill { height: 100%; border-radius: 3px; }
.eval-val { width: 34px; text-align: right; font-family: var(--font-mono); font-size: 11px; font-weight: 600; color: var(--neutral-600); }
.eval-footnote { font-size: var(--text-xs); color: var(--neutral-400); text-align: center; margin-top: var(--space-3); }

/* Responsive */
@media (max-width: 1024px) {
  .interview-body {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr auto;
    overflow-y: auto;
  }
  .chat-panel { height: 68vh; min-height: 620px; }
  .digital-human-stage { min-height: 360px; }
  .info-panel {
    height: auto;
    max-height: none;
    border-left: none;
    border-top: 1px solid var(--neutral-200);
    flex-direction: row;
    flex-wrap: wrap;
    gap: var(--space-3);
    overflow-y: visible;
  }
  .vr-card { width: 200px; }
  .info-card { flex: 1; min-width: 200px; }
}
@media (max-width: 640px) {
  .input-bar { height: auto; min-height: 340px; padding-inline: var(--space-3); }
}
@media (prefers-reduced-motion: reduce) {
  .conversation-scroll { scroll-behavior: auto; }
}

/* Interview V2 — video-first execution workspace */
.topbar{height:58px;padding:0 28px}.progress-track{top:58px;height:2px}.interview-body{grid-template-columns:minmax(0,1fr) 310px;margin-top:60px;height:calc(100dvh - 60px);background:#fff}.chat-panel{position:relative;padding:14px 16px 10px;gap:10px;background:#fff}.digital-human-stage{min-height:0;flex:1;padding:0;border-radius:16px;background:#e3ebf6}.digital-human-stage :deep(.digital-human){border-radius:16px}.question-strip{display:flex;min-height:76px;flex:0 0 76px;align-items:center;justify-content:space-between;gap:20px;padding:11px 16px;border-radius:13px;background:#f8faf9}.question-strip>div{min-width:0}.question-strip strong{display:block;margin-bottom:4px;color:var(--accent-700);font-size:11px}.question-strip p{display:-webkit-box;margin:0;overflow:hidden;color:var(--neutral-800);font-size:13px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.question-strip button{flex:0 0 auto;padding:6px 9px;border:1px solid var(--neutral-200);border-radius:7px;background:#fff;color:var(--neutral-600);font:inherit;font-size:10px;cursor:pointer}.input-bar{height:68px;flex:0 0 68px;padding:5px 4px 0;border-top:1px solid var(--neutral-200);background:#fff}.conversation-scroll{position:absolute;z-index:12;right:16px;bottom:82px;left:16px;max-height:220px;overflow-y:auto;padding:10px 12px;border:1px solid var(--neutral-200);border-radius:12px;background:rgba(255,255,255,.97);box-shadow:0 16px 38px rgba(25,55,45,.12);backdrop-filter:blur(8px)}.conversation-entry{padding:8px 10px}.conversation-entry p{margin:0;font-size:12px}.transcript-enter-active,.transcript-leave-active{transition:opacity 180ms ease,transform 240ms cubic-bezier(.16,1,.3,1)}.transcript-enter-from,.transcript-leave-to{opacity:0;transform:translateY(8px)}.info-panel{height:100%;padding:14px;gap:12px;background:#fbfcfb}.vr-card{position:relative;top:auto;width:100%;aspect-ratio:16/9;border:0;border-radius:14px;background:#f4f5f6}.info-card{padding:16px;border:1px solid rgba(25,80,60,.06);border-radius:14px;background:#f8faf9}.q-num{font-family:inherit;font-size:18px}.q-progress{height:2px}.q-row{padding:4px 0}.ql,.qv{font-size:12px}.eval-list{gap:11px}.eval-track{height:5px}.eval-val{font-family:inherit}.eval-footnote{margin-bottom:0}.timer{font-family:inherit;font-size:17px}.session-tag{font-size:12px}.end-btn{min-height:36px}.ctrl-btn{height:36px}

@media(max-width:1024px){.interview-page{height:auto;min-height:100dvh;overflow:auto}.interview-body{height:auto;min-height:calc(100dvh - 60px);grid-template-columns:1fr;grid-template-rows:auto auto;overflow:visible}.chat-panel{height:calc(100dvh - 60px);min-height:620px}.info-panel{height:auto;display:grid;grid-template-columns:200px 1fr 1fr;border-top:1px solid var(--neutral-200);border-left:0;overflow:visible}.vr-card{width:auto}.conversation-scroll{right:16px}.digital-human-stage{min-height:360px}}
@media(max-width:700px){.topbar{padding:0 12px}.session-tag{max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.interview-body{margin-top:60px}.chat-panel{height:calc(100dvh - 60px);min-height:600px;padding:9px}.digital-human-stage{min-height:310px}.question-strip{min-height:82px;flex-basis:82px;padding:10px 12px}.question-strip button{display:none}.input-bar{height:72px;min-height:0;flex-basis:72px}.conversation-scroll{right:9px;bottom:84px;left:9px}.info-panel{display:flex;padding:10px;flex-direction:column}.vr-card{width:100%;max-width:none}.topbar-center{position:absolute;left:50%;transform:translateX(-50%)}.session-tag{display:none}}
@media(prefers-reduced-motion:reduce){.transcript-enter-active,.transcript-leave-active{transition:none}}
</style>
