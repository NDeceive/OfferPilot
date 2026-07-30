<template>
  <div class="interview-page">
    <!-- Topbar -->
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

    <!-- Progress -->
    <div class="progress-track">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>

    <!-- Body -->
    <div class="interview-body">
      <!-- Chat Panel -->
      <div class="chat-panel">
        <div class="chat-messages" ref="messagesRef">
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <div v-if="msg.role === 'ai'" class="avatar ai-av">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a5 5 0 0 1 5 5v3a5 5 0 0 1-10 0V7a5 5 0 0 1 5-5z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              </svg>
            </div>
            <div class="bubble-wrap">
              <span v-if="msg.followup" class="followup-pill">追问</span>
              <div class="bubble">
                <p>{{ msg.text }}</p>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="avatar user-av"><span>张</span></div>
          </div>

          <!-- Typing indicator -->
          <div v-if="isAiTyping" class="msg ai">
            <div class="avatar ai-av">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a5 5 0 0 1 5 5v3a5 5 0 0 1-10 0V7a5 5 0 0 1 5-5z"/>
              </svg>
            </div>
            <div class="bubble typing-bubble">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>

        <!-- Input -->
        <div class="input-bar">
          <div class="mode-tabs">
            <button :class="['mode-tab', { on: inputMode === 'text' }]" @click="inputMode = 'text'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              文字
            </button>
            <button :class="['mode-tab', { on: inputMode === 'voice' }]" @click="inputMode = 'voice'">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              </svg>
              语音
            </button>
          </div>

          <div v-if="inputMode === 'text'" class="text-area-wrap">
            <textarea
              v-model="answer"
              class="answer-textarea"
              placeholder="请在此输入你的回答..."
              rows="3"
              @keydown.ctrl.enter="submitAnswerFn"
            ></textarea>
            <div class="text-actions">
              <span class="shortcut-hint">Ctrl + Enter 发送</span>
              <div class="action-btns">
                <button class="btn-ghost" @click="skipQuestion" :disabled="isSubmitting">跳过</button>
                <button class="btn-send" @click="submitAnswerFn" :disabled="!answer.trim() || isSubmitting">
                  提交
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <div v-else class="voice-area">
            <button :class="['mic-btn', { recording: isRecording }]" @click="toggleRecording">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/>
                <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
                <line x1="12" y1="19" x2="12" y2="23"/>
              </svg>
            </button>
            <span class="mic-label">{{ isRecording ? '正在录音，点击停止...' : '点击开始语音回答' }}</span>
          </div>
        </div>
      </div>

      <!-- Info Panel -->
      <aside class="info-panel">
        <!-- VR Card -->
        <div class="vr-card">
          <div class="vr-content">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="color: var(--neutral-400)">
              <path d="M12 2a5 5 0 0 1 5 5v3a5 5 0 0 1-10 0V7a5 5 0 0 1 5-5z"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2"/>
              <line x1="12" y1="19" x2="12" y2="23"/>
            </svg>
            <span class="vr-title">VR 面试官</span>
            <span class="vr-soon">即将上线</span>
          </div>
        </div>

        <!-- Question Card -->
        <div class="info-card">
          <div class="q-head">
            <span class="q-num">第 {{ currentQuestion }} 题</span>
            <span class="q-of">/ {{ totalQuestions }}</span>
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

        <!-- Eval Card -->
        <div class="info-card">
          <h3 class="info-card-title">实时评估</h3>
          <div class="eval-list">
            <div v-for="(e, i) in evalItems" :key="i" class="eval-row">
              <span class="eval-label">{{ e.name }}</span>
              <div class="eval-track">
                <div class="eval-fill" :style="{ width: e.value + '%', background: e.color }"></div>
              </div>
              <span class="eval-val">{{ e.value }}%</span>
            </div>
          </div>
          <p class="eval-footnote">面试结束后查看完整报告</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { startInterview, submitAnswer, getNextQuestion, finishInterview } from '../api'

const router = useRouter()
const route = useRoute()

// --- Session state ---
const sessionId = ref(null)
const currentQuestionId = ref(null)
const jobTitle = ref('前端开发工程师')
const currentQuestion = ref(1)
const totalQuestions = ref(8)
const timeLeft = ref(1800)
const inputMode = ref('text')
const answer = ref('')
const isRecording = ref(false)
const isAiTyping = ref(false)
const isPaused = ref(false)
const isSubmitting = ref(false)
const messagesRef = ref(null)
const MAX_QUESTIONS = 8

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

const progressPercent = computed(() => (currentQuestion.value / totalQuestions.value) * 100)

let timerInterval = null

// --- Initialize interview on mount ---
onMounted(async () => {
  // Start local timer
  timerInterval = setInterval(() => {
    if (timeLeft.value > 0 && !isPaused.value) timeLeft.value--
  }, 1000)

  // Get jobId from query param, default to 1
  const jobId = Number(route.query.jobId) || 1

  try {
    const res = await startInterview({ jobId })
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

onUnmounted(() => clearInterval(timerInterval))

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
  if (!answer.value.trim() || isSubmitting.value || !sessionId.value) return
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
      // res.followupQuestion is a plain string, use it directly as text
      // questionId stays the same (followup is to the same main question)
      messages.value.push({
        role: 'ai',
        text: res.followupQuestion,
        followup: true,
      })
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
        }
      } catch (nextErr) {
        console.error('Failed to get next question:', nextErr)
        messages.value.push({
          role: 'ai',
          text: '加载下一题失败，你可以手动结束面试。',
          followup: false,
        })
      }

      // Auto-finish when max questions reached
      if (currentQuestion.value >= MAX_QUESTIONS) {
        return await autoFinishInterview()
      }
    } else if (res.nextAction === 'FINISHABLE') {
      // Interview can be finished - call finish
      try {
        const finishRes = await finishInterview(sessionId.value)
        messages.value.push({
          role: 'ai',
          text: '面试结束！感谢你的精彩回答。正在生成你的能力报告...',
          followup: false,
        })
        scrollToBottom()
        const reportId = finishRes || 1
        setTimeout(() => router.push(`/history/${reportId}`), 2000)
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
    } else {
      // Keep local incremental eval as a fallback
      evalItems.value[0].value = Math.min(100, evalItems.value[0].value + Math.floor(Math.random() * 15 + 5))
      evalItems.value[1].value = Math.min(100, evalItems.value[1].value + Math.floor(Math.random() * 12 + 3))
      evalItems.value[2].value = Math.min(100, evalItems.value[2].value + Math.floor(Math.random() * 10 + 5))
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
  if (isSubmitting.value || !sessionId.value) return
  isSubmitting.value = true
  isAiTyping.value = true
  scrollToBottom()

  try {
    // 提交跳过标记（≥15字避免触发"回答过短"追问）
    const isLastQuestion = currentQuestion.value >= MAX_QUESTIONS
    await submitAnswer(sessionId.value, {
      questionId: currentQuestionId.value,
      answer: isLastQuestion ? '（此题已跳过，面试已结束）' : '（此题已跳过，直接进入下一题）',
    })
    isAiTyping.value = false

    // 不管后端返回 FOLLOWUP 还是 NEXT，始终取下一题
    try {
      const nextRes = await getNextQuestion(sessionId.value)
      if (nextRes.question) {
        currentQuestion.value++
        currentQuestionId.value = nextRes.question.id
        questionTypes.value.push(mapQuestionType(nextRes.question.type))
        questionDifficulties.value.push(difficultyLabels[nextRes.question.difficulty] || '中等')
        questionSkills.value.push(nextRes.question.abilityTag || '综合能力')
        messages.value.push({ role: 'ai', text: nextRes.question.content, followup: false })
      } else if (nextRes.nextAction === 'FINISHABLE') {
        return await autoFinishInterview()
      }
    } catch (nextErr) {
      console.error('Failed to get next question after skip:', nextErr)
      messages.value.push({ role: 'ai', text: '加载下一题失败，你可以手动结束面试。', followup: false })
    }

    // Auto-finish when max questions reached
    if (currentQuestion.value >= MAX_QUESTIONS) {
      return await autoFinishInterview()
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

function toggleRecording() {
  isRecording.value = !isRecording.value
}

function togglePause() {
  isPaused.value = !isPaused.value
}

async function endInterview() {
  if (sessionId.value) {
    try {
      const res = await finishInterview(sessionId.value)
      const reportId = res || 1
      router.push(`/history/${reportId}`)
      return
    } catch (e) {
      console.error('Failed to finish interview:', e)
    }
  }
  // Fallback navigation
  router.push('/history/1')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

async function autoFinishInterview() {
  isAiTyping.value = true
  try {
    const finishRes = await finishInterview(sessionId.value)
    messages.value.push({
      role: 'ai',
      text: `已达到 ${MAX_QUESTIONS} 道题目，面试自动结束。正在生成你的能力报告...`,
      followup: false,
    })
    scrollToBottom()
    const reportId = finishRes || 1
    setTimeout(() => router.push(`/history/${reportId}`), 2000)
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
.progress-fill { height: 100%; background: var(--accent-500); transition: width 0.5s var(--ease-out-expo); }

/* Body */
.interview-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 320px;
  margin-top: 59px;
  height: calc(100dvh - 59px);
  overflow: hidden;
}

/* Chat Panel */
.chat-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}
.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: var(--space-4);
  scroll-behavior: smooth;
}

.msg { display: flex; gap: var(--space-3); max-width: 80%; animation: fade-in-up var(--duration-normal) var(--ease-out-expo); }
.msg.user { align-self: flex-end; }
.msg.ai { align-self: flex-start; }
.avatar { width: 34px; height: 34px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-av { background: var(--accent-50); color: var(--accent-600); }
.user-av { background: linear-gradient(135deg, var(--accent-400), var(--accent-600)); color: white; font-size: 13px; font-weight: 600; }
.bubble-wrap { display: flex; flex-direction: column; gap: 4px; }
.followup-pill { font-size: 11px; font-weight: 600; color: var(--accent-700); padding: 2px 10px; background: var(--accent-50); border-radius: var(--radius-full); width: fit-content; }
.bubble { padding: var(--space-3) var(--space-4); border-radius: var(--radius-lg); font-size: var(--text-base); line-height: 1.7; }
.ai .bubble { background: var(--surface-elevated); border: 1px solid var(--neutral-200); color: var(--neutral-800); border-top-left-radius: var(--radius-xs); }
.user .bubble { background: var(--accent-600); color: white; border-bottom-right-radius: var(--radius-xs); }
.typing-bubble { display: flex; gap: 5px; align-items: center; padding: var(--space-3) var(--space-4); }
.dot { width: 7px; height: 7px; border-radius: 50%; background: var(--neutral-400); animation: dot-bounce 1.4s infinite; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce { 0%,60%,100% { transform: translateY(0); } 30% { transform: translateY(-5px); } }

/* Input Bar */
.input-bar { border-top: 1px solid var(--neutral-200); background: var(--surface-elevated); padding: var(--space-4) var(--space-6); flex-shrink: 0; }
.mode-tabs { display: flex; gap: var(--space-2); margin-bottom: var(--space-3); }
.mode-tab { display: flex; align-items: center; gap: 4px; padding: var(--space-1) var(--space-3); border: 1px solid var(--neutral-200); border-radius: var(--radius-full); background: var(--surface-elevated); color: var(--neutral-500); font-size: var(--text-xs); font-weight: 500; transition: all var(--duration-fast); }
.mode-tab:hover { border-color: var(--neutral-300); }
.mode-tab.on { border-color: var(--accent-500); background: var(--accent-50); color: var(--accent-700); }
.text-area-wrap { display: flex; flex-direction: column; gap: var(--space-2); }
.answer-textarea { width: 100%; padding: var(--space-3) var(--space-4); border: 1.5px solid var(--neutral-200); border-radius: var(--radius-md); background: var(--surface-elevated); color: var(--neutral-800); font-family: var(--font-body); font-size: var(--text-base); line-height: 1.6; resize: none; outline: none; transition: border-color var(--duration-normal); }
.answer-textarea:focus { border-color: var(--accent-400); box-shadow: 0 0 0 3px rgba(16,185,129,0.1); }
.answer-textarea::placeholder { color: var(--neutral-400); }
.text-actions { display: flex; justify-content: space-between; align-items: center; }
.shortcut-hint { font-size: var(--text-xs); color: var(--neutral-400); }
.action-btns { display: flex; gap: var(--space-2); }
.btn-ghost { padding: var(--space-2) var(--space-4); border: 1px solid var(--neutral-200); border-radius: var(--radius-sm); background: var(--surface-elevated); color: var(--neutral-600); font-size: var(--text-sm); transition: all var(--duration-fast); }
.btn-ghost:hover { border-color: var(--neutral-300); background: var(--neutral-50); }
.btn-send { display: inline-flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-5); background: var(--accent-600); border: none; border-radius: var(--radius-sm); color: white; font-size: var(--text-sm); font-weight: 600; transition: all var(--duration-normal) var(--ease-out-expo); }
.btn-send:hover { background: var(--accent-500); box-shadow: var(--shadow-accent); }
.btn-send:disabled { opacity: 0.4; cursor: not-allowed; }
.voice-area { display: flex; flex-direction: column; align-items: center; gap: var(--space-4); padding: var(--space-6); }
.mic-btn { width: 68px; height: 68px; border-radius: 50%; border: 2px solid var(--accent-400); background: var(--accent-50); color: var(--accent-600); display: flex; align-items: center; justify-content: center; transition: all var(--duration-normal) var(--ease-out-expo); }
.mic-btn:hover { background: var(--accent-100); box-shadow: var(--shadow-accent); }
.mic-btn.recording { background: rgba(239,68,68,0.08); border-color: var(--color-error); color: var(--color-error); animation: pulse-recording 1.5s ease-in-out infinite; }
@keyframes pulse-recording { 0%,100% { box-shadow: 0 0 0 0 rgba(239,68,68,0.3); } 50% { box-shadow: 0 0 0 12px rgba(239,68,68,0); } }
.mic-label { font-size: var(--text-sm); color: var(--neutral-500); }

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
.vr-card { aspect-ratio: 16/9; background: var(--neutral-100); border-radius: var(--radius-lg); border: 2px dashed var(--neutral-300); display: flex; align-items: center; justify-content: center; flex-shrink: 0; position: sticky; top: 0; z-index: 5; }
.vr-content { display: flex; flex-direction: column; align-items: center; gap: var(--space-2); }
.vr-title { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-500); }
.vr-soon { font-size: 11px; color: var(--neutral-400); padding: 2px 10px; background: var(--neutral-200); border-radius: var(--radius-full); }
.info-card { background: var(--neutral-50); border-radius: var(--radius-lg); padding: var(--space-4); flex-shrink: 0; }
.info-card-title { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-700); margin-bottom: var(--space-3); }
.q-head { display: flex; align-items: baseline; gap: var(--space-1); margin-bottom: var(--space-3); }
.q-num { font-family: var(--font-mono); font-size: var(--text-lg); font-weight: 700; color: var(--accent-600); }
.q-of { font-size: var(--text-sm); color: var(--neutral-500); }
.q-progress { height: 4px; background: var(--neutral-200); border-radius: 2px; margin-bottom: var(--space-3); overflow: hidden; }
.q-bar { height: 100%; background: var(--accent-500); border-radius: 2px; transition: width 0.5s var(--ease-out-expo); }
.q-rows { display: flex; flex-direction: column; gap: var(--space-2); }
.q-row { display: flex; justify-content: space-between; }
.ql { font-size: var(--text-sm); color: var(--neutral-500); }
.qv { font-size: var(--text-sm); font-weight: 500; color: var(--neutral-700); }
.eval-list { display: flex; flex-direction: column; gap: var(--space-3); }
.eval-row { display: flex; align-items: center; gap: var(--space-3); }
.eval-label { width: 60px; font-size: var(--text-xs); color: var(--neutral-600); flex-shrink: 0; }
.eval-track { flex: 1; height: 6px; background: var(--neutral-200); border-radius: 3px; overflow: hidden; }
.eval-fill { height: 100%; border-radius: 3px; transition: width 0.8s var(--ease-out-expo); }
.eval-val { width: 34px; text-align: right; font-family: var(--font-mono); font-size: 11px; font-weight: 600; color: var(--neutral-600); }
.eval-footnote { font-size: var(--text-xs); color: var(--neutral-400); text-align: center; margin-top: var(--space-3); }

/* Responsive */
@media (max-width: 1024px) {
  .interview-body {
    grid-template-columns: 1fr;
    grid-template-rows: 1fr auto;
    overflow-y: auto;
  }
  .chat-panel { height: 50vh; }
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
@media (prefers-reduced-motion: reduce) {
  .msg { animation: none; }
  .dot { animation: none; }
  .mic-btn.recording { animation: none; }
}
</style>
