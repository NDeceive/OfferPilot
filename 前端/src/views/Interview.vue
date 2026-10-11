<template>
  <div class="interview-page">
    <header class="topbar">
      <div class="topbar-left">
        <router-link :to="route.query.assignmentId?'/my/tasks/'+route.query.assignmentId:'/home'" class="back-btn" aria-label="返回任务或首页">
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
        <button type="button" class="camera-toggle-btn" :class="{ 'is-active': liveExpression.cameraActive }" :aria-pressed="!!liveExpression.cameraActive" :disabled="liveExpression.requesting || expressionEnding" :aria-label="liveExpression.cameraActive ? '关闭摄像头' : '打开摄像头'" :title="liveExpression.cameraActive ? '关闭摄像头' : '打开摄像头'" @click="liveExpression.cameraActive ? cameraPreviewRef?.stopCamera() : cameraPreviewRef?.startCamera()">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="12" height="14" rx="2"/><path d="m15 10 6-4v12l-6-4"/></svg>
          <span class="camera-label-desktop">{{ liveExpression.requesting ? '正在打开…' : liveExpression.cameraActive ? '关闭摄像头' : '打开摄像头' }}</span><span class="camera-label-mobile">{{ liveExpression.requesting ? '打开中…' : liveExpression.cameraActive ? '关摄像头' : '开摄像头' }}</span>
        </button>
        <button v-if="!route.query.assignmentId" class="ctrl-btn" :disabled="pauseChanging || expressionEnding || isSubmitting || isSpeechProcessing" @click="togglePause" :aria-label="isPaused ? '继续面试' : '暂停面试'" :title="isPaused ? '继续' : '暂停'">
          <svg v-if="!isPaused" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="5 3 19 12 5 21 5 3"/>
          </svg>
          <span class="pause-label">{{ isPaused ? '继续' : '暂停' }}</span>
        </button>
        <button class="end-btn" :disabled="expressionEnding" @click="endInterview">{{ sessionFinished ? '已结束' : expressionEnding ? '正在结束…' : '结束面试' }}</button>
      </div>
    </header>

    <div class="progress-track">
      <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
    </div>
    <div v-if="sessionFinished" class="mobile-expression-status completion-status" role="status"><strong>面试已结束</strong><span>报告后台生成中</span><button type="button" @click="leaveFinishedInterview">查看面试记录</button></div>
    <div v-else class="mobile-expression-status" role="status">
      <div class="expression-status-content"><span v-if="finishError" class="finish-error">{{ finishError }}</span><span>当前表情</span><strong>{{ liveExpression.label }}</strong><span v-if="liveExpression.probability != null">{{ Math.round(liveExpression.probability * 100) }}%</span><span v-if="liveExpression.state !== 'active'" class="live-expression-detail">{{ liveExpression.status }}</span></div>
      <details class="camera-tools"><summary>设备与记录 <span aria-hidden="true">⌄</span></summary><div>
        <strong class="settings-heading">设备设置</strong>
        <p v-if="liveExpression.cameraError">{{ liveExpression.cameraError }}</p>
        <button v-if="liveExpression.cameraActive && liveExpression.deviceCount > 1" type="button" @click="cameraPreviewRef?.switchCamera()">切换摄像头</button>
        <p v-else>使用当前默认摄像头</p>
        <button v-if="liveExpression.state === 'error'" type="button" @click="cameraPreviewRef?.retryExpressions()">重试表情识别</button>
        <button v-if="liveExpression.state === 'syncing'" type="button" @click="syncExpressionClock">重新同步时间</button>
        <strong class="settings-heading">表情记录</strong>
        <p>仅保存分类概率与时间，画面在本地处理。</p>
        <ExpressionUploadStatus ref="expressionUploadRef" :session-id="sessionId" />
      </div></details>
    </div>
    <div class="interview-body">
      <div class="chat-panel">
        <div class="digital-human-stage" aria-label="AI 面试官视频">
          <DigitalHumanStage
            ref="digitalHumanRef"
            :text="digitalHumanText"
            :speech-key="digitalHumanSpeechKey"
          />
          <span class="stage-caption">AI 面试官</span>
        </div>

        <p v-if="isMobile && latestQuestion" class="mobile-question">
          <span class="mq-label">当前题目</span>
          <span class="mq-text">{{ latestQuestion }}</span>
        </p>

        <div class="input-bar" :class="{ collapsed: !showTranscript, 'text-open': textAnswerOpen }">
          <div class="transcript-toolbar">
            <span>回答记录</span>
            <button
              type="button"
              aria-controls="interview-transcript"
              :aria-expanded="showTranscript"
              @click="showTranscript = !showTranscript"
            >{{ showTranscript ? '收起回答记录' : '展开回答记录' }}</button>
          </div>
          <div id="interview-transcript" v-show="showTranscript" class="conversation-scroll" ref="messagesRef">
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
          <div class="answer-head">
            <button type="button" class="text-answer-toggle" @click="textAnswerOpen=!textAnswerOpen" :aria-expanded="textAnswerOpen">{{textAnswerOpen?'收起文字输入':'文字作答'}}</button>
            <div v-if="isMobile" class="mobile-cam-chip">
              <CameraPreview ref="cameraPreviewRef" compact :session-id="sessionId" :question-id="currentQuestionId"
                :round-no="currentQuestion" :paused="isPaused || pauseChanging || isSubmitting || expressionEnding || needsNextQuestion" :server-offset="serverOffset" :clock-ready="clockReady"
                @expression-sample="recordExpression" @expression-state="liveExpression = $event" @retry-clock="syncExpressionClock" />
            </div>
          </div>
          <label v-if="textAnswerOpen" class="text-answer">输入回答<textarea v-model="answer" rows="3" maxlength="10000" :disabled="isSubmitting||!sessionId" placeholder="可直接输入，或修改语音识别后的文字，再提交回答。"/></label>
          <MicrophoneControl
            ref="microphoneRef"
            :session-id="sessionId"
            :transcript="answer"
            :disabled="isSubmitting || isPaused || pauseChanging || expressionEnding || needsNextQuestion || !sessionId"
            :paused="isPaused || pauseChanging"
            :allow-skip="!route.query.assignmentId"
            @transcript="appendSpeechTranscript"
            @processing="isSpeechProcessing = $event"
            @submit="submitAnswerFn"
            @skip="skipQuestion"
          />
          <button v-if="needsNextQuestion" type="button" class="next-question-retry" :disabled="isSubmitting" @click="recoverNextQuestion">{{ isSubmitting ? '正在恢复…' : '回答已保存，重试加载下一题' }}</button>
        </div>
      </div>

      <aside class="info-panel">
        <div v-if="!isMobile" class="camera-module">
          <CameraPreview ref="cameraPreviewRef" :session-id="sessionId" :question-id="currentQuestionId"
            :round-no="currentQuestion" :paused="isPaused || pauseChanging || isSubmitting || expressionEnding || needsNextQuestion" :server-offset="serverOffset" :clock-ready="clockReady"
            @expression-sample="recordExpression" @expression-state="liveExpression = $event" @retry-clock="syncExpressionClock">

          </CameraPreview>
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
import { pollInterviewReport } from '../utils/interviewCompletion'
import { startInterview, submitAnswer, getNextQuestion, finishInterview as finishInterviewRequest, getExpressionContext, setInterviewPaused, getReportStatus, getInterviewResume } from '../api'
import ExpressionUploadStatus from '../components/interview/ExpressionUploadStatus.vue'
import CameraPreview from '../components/interview/CameraPreview.vue'
import DigitalHumanStage from '../components/interview/DigitalHumanStage.vue'
import MicrophoneControl from '../components/interview/MicrophoneControl.vue'
import { useIsMobile } from '../mobile/composables/useIsMobile'

const router = useRouter()
const route = useRoute()
const isMobile = useIsMobile()

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
const showTranscript = ref(!isMobile.value) // 桌面默认展开回答记录；手机默认收起，点按钮再看
const evaluationReady = ref(false)
const messagesRef = ref(null)
const cameraPreviewRef = ref(null)
const microphoneRef = ref(null)
const digitalHumanRef = ref(null)
const digitalHumanText = ref('')
const digitalHumanSpeechKey = ref(0)
const expressionEnding = ref(false)
const sessionFinished=ref(false), finishError=ref('')
let finishPromise, reportWaitPromise
const expressionUploadRef = ref(null)
const serverOffset = ref(0)
const clockReady = ref(false)
const pauseChanging = ref(false)
const needsNextQuestion = ref(false)
const liveExpression = ref({ label: '等待开启' })
let pendingAnswerRequest = null
let pendingSkipRequest = null
watch(sessionId, sid => { if (sid) syncExpressionClock() })
async function syncExpressionClock() {
  clockReady.value = false
  const sid = sessionId.value
  if (!sid) return
  const before = Date.now()
  try {
    const context = await getExpressionContext(sid)
    if (sid !== sessionId.value) return
    serverOffset.value = context.serverTime - (before + Date.now()) / 2
    clockReady.value = true
  } catch (error) { console.warn('Expression clock sync failed', error) }
}
function recordExpression(sample) {
  if (expressionEnding.value) return
  expressionUploadRef.value?.add(sample)
}
function finishInterview(sid) {
  if (finishPromise) return finishPromise
  if (sessionFinished.value) return Promise.resolve(sid)
  expressionEnding.value=true; finishError.value=''
  finishPromise=(async()=>{
    try {
      try { cameraPreviewRef.value?.stopExpressions() } catch(error) { console.warn('Stop sampling failed',error) }
      try { Promise.resolve(expressionUploadRef.value?.flush()).catch(error=>console.warn('Expression upload deferred',error)) } catch(error) { console.warn('Expression upload deferred',error) }
      try { await finishInterviewRequest(sid) }
      catch(error) {
        // The server may have committed even when its response was lost.
        let state
        try { state=await getInterviewResume(sid,{timeout:3000}) } catch {}
        if(state?.status!=='FINISHED')throw error
      }
      sessionFinished.value=true
      return sid
    } catch(error) { expressionEnding.value=false; finishError.value='结束结果暂未确认，请检查网络后重试。'; throw error }
  })().finally(()=>{ finishPromise=null })
  return finishPromise
}

const questionTypes = ref([])
const questionDifficulties = ref([])
const questionSkills = ref([])

const difficultyLabels = { 1: '简单', 2: '中等', 3: '困难', 4: '困难' }

const messages = ref([])
const textAnswerOpen = ref(false)

const evalItems = ref([
  { name: '表达能力', value: 0, color: '#10b981' },
  { name: '逻辑性', value: 0, color: '#3b82f6' },
  { name: '技术深度', value: 0, color: '#8b5cf6' },
])

const progressPercent = computed(() => totalDuration.value > 0
  ? ((totalDuration.value - timeLeft.value) / totalDuration.value) * 100
  : 0)

// 手机端默认收起回答记录，用这条常驻展示当前题目，避免看不到题
const latestQuestion = computed(() => {
  for (let i = messages.value.length - 1; i >= 0; i -= 1) {
    if (messages.value[i].role === 'ai' && messages.value[i].text) return messages.value[i].text
  }
  return ''
})

let timerInterval = null
let autoFinished = false

watch([timeLeft, isSubmitting], ([seconds, submitting]) => {
  if (seconds <= 0 && sessionId.value && !submitting && !autoFinished && !expressionEnding.value) {
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
      const resumed = await getInterviewResume(existingSessionId)
      if (resumed.status !== 'ONGOING') autoFinished = true
      totalDuration.value = resumed.durationSeconds || durationSeconds
      timeLeft.value = resumed.remainingSeconds ?? totalDuration.value
      isPaused.value = !!resumed.paused
      if (resumed.status !== 'ONGOING') {
        expressionEnding.value = true
        releaseMediaDevices()
        expressionUploadRef.value?.flush()
        await waitForReport(existingSessionId)
        return
      }
      const prompt = resumed.currentQuestion
      const records = resumed.messages || []
      messages.value = records.map(item => ({ role: item.role === 'INTERVIEWER' ? 'ai' : 'user', text: item.content, followup: item.msgType === 'FOLLOWUP' }))
      for (const item of records.filter(item => item.role === 'INTERVIEWER' && item.msgType === 'MAIN')) {
        const index = (item.roundNo || 1) - 1
        questionTypes.value[index] = mapQuestionType(item.msgType)
        questionDifficulties.value[index] = '中等'
        questionSkills.value[index] = item.abilityTag || '综合能力'
      }
      if (prompt) {
        currentQuestion.value = prompt.roundNo || 1
        currentQuestionId.value = prompt.questionId
        if (resumed.answered) { needsNextQuestion.value = true; await recoverNextQuestion() }
        else if (!isPaused.value) speakQuestion(prompt.content)
      }
      return
    }

    const res = await startInterview({ jobId, durationSeconds, assignmentId:route.query.assignmentId?Number(route.query.assignmentId):undefined })
    sessionId.value = res.sessionId
    if(res.finishable){autoFinished=true;await finishInterview(sessionId.value);releaseMediaDevices();await waitForReport(sessionId.value);return}
    if(res.durationSeconds){totalDuration.value=res.durationSeconds;timeLeft.value=res.remainingSeconds??res.durationSeconds}
    jobTitle.value = res.jobName || '模拟面试'

    // Set first question
    if (res.question) {
      currentQuestion.value=res.question.roundNo||1
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
  if (!answer.value.trim() || isSubmitting.value || isSpeechProcessing.value || isPaused.value || expressionEnding.value || needsNextQuestion.value || !sessionId.value) return
  const userAnswer = answer.value.trim()

  // Push user message
  messages.value.push({ role: 'user', text: userAnswer, followup: false })
  answer.value = ''

  isAiTyping.value = true
  isSubmitting.value = true
  scrollToBottom()

  try {
    if (!pendingAnswerRequest || pendingAnswerRequest.answer !== userAnswer || pendingAnswerRequest.roundNo !== currentQuestion.value) {
      pendingAnswerRequest = { questionId: currentQuestionId.value, roundNo: currentQuestion.value, answer: userAnswer, requestId: crypto.randomUUID() }
    }
    const res = await submitAnswer(sessionId.value, {
      ...pendingAnswerRequest,
    })
    pendingAnswerRequest = null

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
      needsNextQuestion.value = true
      // Fetch the next question from the server
      try {
        const nextRes = await getNextQuestion(sessionId.value)
        if(nextRes.nextAction==='FINISHABLE'){
          await finishInterview(sessionId.value)
          releaseMediaDevices()
          await waitForReport(sessionId.value)
          return
        }
        if (nextRes.question) {
          needsNextQuestion.value = false
          currentQuestion.value = nextRes.question.roundNo || currentQuestion.value + 1
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
          text: sessionFinished.value ? '面试已结束，你可以稍后在面试记录中查看报告。' : '结束结果暂未确认，请检查网络后再次点击结束面试。',
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
    answer.value=userAnswer
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
  if (isSubmitting.value || isSpeechProcessing.value || isPaused.value || expressionEnding.value || needsNextQuestion.value || !sessionId.value) return
  isSubmitting.value = true
  isAiTyping.value = true
  scrollToBottom()

  try {
    if (!pendingSkipRequest || pendingSkipRequest.roundNo !== currentQuestion.value) pendingSkipRequest = {
      questionId: currentQuestionId.value,
      roundNo: currentQuestion.value,
      skipped: true,
      requestId: crypto.randomUUID(),
      answer: '',
    }
    let res = await submitAnswer(sessionId.value, pendingSkipRequest)
    pendingSkipRequest = null
    if (res.nextAction === 'NEXT' && !res.question) { needsNextQuestion.value = true; res = await getNextQuestion(sessionId.value) }

    isAiTyping.value = false

    if (res.nextAction === 'NEXT' && res.question) {
      needsNextQuestion.value = false
      currentQuestion.value = res.question.roundNo || currentQuestion.value + 1
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
    } else if (res.nextAction === 'FINISHABLE') {
      await finishInterview(sessionId.value)
      releaseMediaDevices()
      await waitForReport(sessionId.value)
      return
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

async function recoverNextQuestion() {
  if (!sessionId.value || isPaused.value) return
  isSubmitting.value = true
  try {
    const step = await getNextQuestion(sessionId.value)
    if (step.nextAction === 'FINISHABLE') { await finishInterview(sessionId.value); releaseMediaDevices(); await waitForReport(sessionId.value); return }
    if (step.question) {
      const q=step.question
      currentQuestion.value=q.roundNo || currentQuestion.value+1
      currentQuestionId.value=q.id
      const index=currentQuestion.value-1
      questionTypes.value[index]=mapQuestionType(q.type)
      questionDifficulties.value[index]=difficultyLabels[q.difficulty] || '中等'
      questionSkills.value[index]=q.abilityTag || '综合能力'
      messages.value.push({role:'ai',text:q.content,followup:false})
      speakQuestion(q.content)
      needsNextQuestion.value=false
    }
  } catch { needsNextQuestion.value=true }
  finally { isSubmitting.value=false }
}
async function togglePause() {
  if (!sessionId.value || pauseChanging.value) return
  pauseChanging.value=true
  try {
    if (!isPaused.value) { digitalHumanRef.value?.stop(); await microphoneRef.value?.stopMicrophone() }
    const result=await setInterviewPaused(sessionId.value,!isPaused.value)
    isPaused.value=result.paused
    timeLeft.value=result.remainingSeconds
  } catch (error) { messages.value.push({role:'ai',text:error.message || '暂停状态更新失败，请重试。',followup:false}) }
  finally { pauseChanging.value=false }
}

async function endInterview() {
  if(expressionEnding.value)return
  releaseMediaDevices()
  if (sessionId.value) {
    try {
      const res = await finishInterview(sessionId.value)
      await waitForReport(sessionId.value)
      return
    } catch (e) {
      console.error('Failed to finish interview:', e)
      messages.value.push({ role: 'ai', text: '结束面试失败，请检查网络后再次点击结束面试。', followup: false })
      scrollToBottom()
      return
    }
  }
  // Fallback navigation
  router.push(route.query.assignmentId?'/my/tasks/'+route.query.assignmentId:'/history')
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
      text: sessionFinished.value ? '面试已结束，你可以稍后在面试记录中查看报告。' : '结束结果暂未确认，请检查网络后再次点击结束面试。',
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
  for(const release of [()=>cameraPreviewRef.value?.stopCamera(),()=>microphoneRef.value?.stopMicrophone(),()=>digitalHumanRef.value?.close()]) {
    try { release() } catch(error) { console.warn('Media cleanup failed',error) }
  }
}

function completionDestination() { return route.query.assignmentId ? '/my/tasks/'+route.query.assignmentId : '/history' }
function leaveFinishedInterview() { return router.push(completionDestination()) }
function waitForReport(sid) {
  if(reportWaitPromise)return reportWaitPromise
  sessionFinished.value=true
  const origin=route.fullPath
  reportWaitPromise=(async()=>{
    const status=await pollInterviewReport(config=>getReportStatus(sid,config),{cancelled:()=>route.fullPath!==origin})
    if(route.fullPath!==origin)return
    if(status?.ready && status.reportId) { await router.push({path:'/history/'+status.reportId,query:{assignmentId:route.query.assignmentId}});return }
    await leaveFinishedInterview()
  })().finally(()=>{reportWaitPromise=null})
  return reportWaitPromise
}
</script>

<style scoped>
.expression-note { margin:8px 0; font-size:12px; line-height:1.6; color:var(--neutral-600); }
.text-answer-toggle{min-height:44px;border:0;background:transparent;color:var(--primary-600,#047857);font:inherit;cursor:pointer}.text-answer{display:block;font-size:13px}.text-answer textarea{display:block;width:100%;padding:10px 12px;margin:8px 0;border:1px solid var(--neutral-200,#cbd8d0);border-radius:8px;background:var(--surface-primary,#fff);color:inherit;font:inherit;resize:vertical;max-height:140px}.text-answer-toggle:focus-visible,.text-answer textarea:focus-visible{outline:2px solid #147b57;outline-offset:3px}
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
.vr-card { aspect-ratio: 16/9; min-height: 170px; background: var(--neutral-100); border-radius: var(--radius-lg); border: 1px solid var(--neutral-200); display: flex; align-items: center; justify-content: center; flex-shrink: 0; position: sticky; top: 0; z-index: 5; overflow: hidden; }
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
.topbar{height:58px;padding:0 28px}.progress-track{top:58px;height:2px}.interview-body{grid-template-columns:minmax(0,1fr) 310px;margin-top:60px;height:calc(100dvh - 60px);background:#fff}.chat-panel{position:relative;padding:14px 16px 10px;gap:10px;background:#fff}.digital-human-stage{min-height:0;flex:1;padding:0;border-radius:16px;background:#e3ebf6}.digital-human-stage :deep(.digital-human){border-radius:16px}.question-strip{display:flex;min-height:76px;flex:0 0 76px;align-items:center;justify-content:space-between;gap:20px;padding:11px 16px;border-radius:13px;background:#f8faf9}.question-strip>div{min-width:0}.question-strip strong{display:block;margin-bottom:4px;color:var(--accent-700);font-size:11px}.question-strip p{display:-webkit-box;margin:0;overflow:hidden;color:var(--neutral-800);font-size:13px;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.question-strip button{flex:0 0 auto;padding:6px 9px;border:1px solid var(--neutral-200);border-radius:7px;background:#fff;color:var(--neutral-600);font:inherit;font-size:10px;cursor:pointer}.input-bar{height:auto;flex:0 0 auto;padding:5px 4px 0;border-top:1px solid var(--neutral-200);background:#fff}.conversation-scroll{flex:0 0 auto;min-height:0;max-height:220px;overflow-y:auto;padding:10px 12px;border:1px solid var(--neutral-200);border-radius:12px;background:rgba(255,255,255,.97);box-shadow:0 10px 24px rgba(25,55,45,.10)}.conversation-entry{padding:8px 10px}.conversation-entry p{margin:0;font-size:12px}.transcript-enter-active,.transcript-leave-active{transition:opacity 180ms ease,transform 240ms cubic-bezier(.16,1,.3,1)}.transcript-enter-from,.transcript-leave-to{opacity:0;transform:translateY(8px)}.info-panel{height:100%;padding:14px;gap:12px;background:#fbfcfb}.vr-card{position:relative;top:auto;width:100%;aspect-ratio:16/9;border:0;border-radius:14px;background:#f4f5f6}.info-card{padding:16px;border:1px solid rgba(25,80,60,.06);border-radius:14px;background:#f8faf9}.q-num{font-family:inherit;font-size:18px}.q-progress{height:2px}.q-row{padding:4px 0}.ql,.qv{font-size:12px}.eval-list{gap:11px}.eval-track{height:5px}.eval-val{font-family:inherit}.eval-footnote{margin-bottom:0}.timer{font-family:inherit;font-size:17px}.session-tag{font-size:12px}.end-btn{min-height:36px}.ctrl-btn{height:36px}

@media(max-width:1024px){.interview-page{height:auto;min-height:100dvh;overflow:auto}.interview-body{height:auto;min-height:calc(100dvh - 60px);grid-template-columns:1fr;grid-template-rows:auto auto;overflow:visible}.chat-panel{height:calc(100dvh - 60px);min-height:620px}.info-panel{height:auto;display:grid;grid-template-columns:200px 1fr 1fr;border-top:1px solid var(--neutral-200);border-left:0;overflow:visible}.vr-card{width:auto}.conversation-scroll{right:16px}.digital-human-stage{min-height:360px}}
@media(max-width:700px){.topbar{padding:0 12px}.session-tag{max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.interview-body{margin-top:60px}.chat-panel{height:calc(100dvh - 60px);min-height:600px;padding:9px}.digital-human-stage{min-height:310px}.question-strip{min-height:82px;flex-basis:82px;padding:10px 12px}.question-strip button{display:none}.input-bar{height:auto;min-height:0;flex-basis:auto}.conversation-scroll{right:9px;bottom:84px;left:9px}.info-panel{display:flex;padding:10px;flex-direction:column}.vr-card{width:100%;max-width:none}.topbar-center{position:absolute;left:50%;transform:translateX(-50%)}.session-tag{display:none}}
@media(prefers-reduced-motion:reduce){.transcript-enter-active,.transcript-leave-active{transition:none}}

/* 移动端面试布局（断点与 useIsMobile 一致）：数字人缩小置顶，作答区收口到底部 */
.stage-caption{display:none}
.answer-head{display:flex;flex-direction:column}
@media(max-width:767.98px){
  .digital-human-stage{flex:0 0 auto;min-height:0;display:flex;flex-direction:column;align-items:center;gap:6px;border-radius:0;background:transparent}
  .digital-human-stage :deep(.digital-human){width:100%;max-width:360px;height:auto;aspect-ratio:1920/768}
  .stage-caption{display:block;color:var(--neutral-500);font-size:11px;line-height:1}
  .input-bar{gap:8px;margin-top:auto}
  .answer-head{flex-direction:row;gap:8px}
  .answer-head .text-answer-toggle{flex:1;min-height:48px;border:1.5px solid rgba(4,120,87,.32);border-radius:12px;background:#eef7f2;color:var(--primary-600,#047857);font-weight:700;font-size:13px}
  .text-answer-toggle[aria-expanded="true"]{background:#e2f1e9;border-color:rgba(4,120,87,.5)}
  .mobile-cam-chip{flex:0 0 auto;display:flex;width:84px}
  .mobile-question{margin:0;padding:10px 12px;border-radius:12px;background:#f8faf9;color:var(--neutral-800);font-size:13px;line-height:1.5}
  .mobile-question .mq-label{display:block;margin-bottom:3px;color:var(--accent-700,#047857);font-size:11px;font-weight:600}
  .mobile-question .mq-text{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical;-webkit-line-clamp:4}
}

/* Pre-pull interview layout: the transcript and voice controls share a fixed lower panel. */
.input-bar {
  height: clamp(220px, 27vh, 280px);
  flex: 0 0 auto;
  padding: var(--space-3) var(--space-5);
  overflow: hidden;
}
.input-bar.collapsed { height: auto; min-height: 112px; }
.transcript-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 26px;
  color: var(--neutral-500);
  font-size: 12px;
}
.transcript-toolbar button {
  padding: 3px 8px;
  border: 1px solid var(--neutral-200);
  border-radius: 7px;
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font: inherit;
  cursor: pointer;
}
.transcript-toolbar button:focus-visible { outline: 2px solid var(--accent-600); outline-offset: 2px; }
.conversation-scroll {
  position: static;
  z-index: auto;
  inset: auto;
  max-height: none;
  min-height: 0;
  flex: 1;
  padding: 2px var(--space-2) var(--space-2);
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}
.conversation-entry p { font-size: var(--text-sm); }
@media (max-width: 700px) {
  .input-bar { height: auto; min-height: 280px; padding-inline: var(--space-3); }
  .input-bar.collapsed { min-height: 112px; }
}
/* 文字作答展开时给面板留出高度，避免把回答记录和麦克风挤没 */
.input-bar.text-open { height: clamp(300px, 40vh, 400px); }
/* 手机端面板高度随内容自适应，固定高度会把麦克风/文字作答裁掉 */
@media (max-width: 767.98px) {
  .input-bar.text-open { height: auto; min-height: clamp(300px, 40vh, 400px); }
}

.camera-module { min-width:0; flex:none; }
.mobile-expression-status { display:none; }
.next-question-retry { min-height:44px; padding:8px 12px; border:1px solid var(--accent-600); border-radius:8px; background:var(--surface-primary); color:var(--accent-700); font:inherit; cursor:pointer; }
.ctrl-btn,.end-btn { min-height:44px; min-width:44px; }
.ctrl-btn:focus-visible,.end-btn:focus-visible,.next-question-retry:focus-visible { outline:2px solid var(--accent-600); outline-offset:3px; }
@media (min-width:701px) and (max-width:1024px) {
  .info-panel { grid-template-columns:minmax(240px,1fr) minmax(0,1fr); align-items:start; }
  .camera-module { grid-row:span 2; }
  .info-card { min-width:0; }
}
@media (max-width:700px) {
  .mobile-expression-status { position:fixed; top:58px; left:0; right:0; z-index:15; display:flex; align-items:center; gap:10px; min-height:42px; padding:8px 12px; border-bottom:1px solid var(--neutral-200); background:var(--surface-primary); font-size:13px; color:var(--neutral-600); }
  .mobile-expression-status strong { color:var(--accent-700); }
  .interview-body { margin-top:102px; }
  .chat-panel { height:calc(100dvh - 102px); }
  .camera-module { width:100%; }
}
  .mobile-expression-status { position:fixed; top:60px; left:0; right:0; z-index:15; display:flex; align-items:center; gap:10px; height:48px; padding:0 28px; background:var(--surface-primary,#fff); border-bottom:1px solid var(--neutral-200,#e5e7eb); font-size:14px; color:var(--neutral-600,#4b5563); }
  .mobile-expression-status strong { color:var(--accent-700,#047857); }
  .mobile-expression-status button { min-height:44px; margin-left:auto; border:0; background:transparent; color:var(--accent-700,#047857); font:inherit; cursor:pointer; padding:0 10px; }
  .mobile-expression-status button:focus-visible { outline:2px solid var(--accent-600,#059669); }
  .interview-body { margin-top:108px; height:calc(100dvh - 108px); }
  @media(max-width:1024px) { .interview-body { height:auto; min-height:calc(100dvh - 108px); } .chat-panel { height:calc(100dvh - 108px); } }
  @media(max-width:700px) { .mobile-expression-status { top:60px; padding:0 12px; gap:8px; font-size:13px; } .live-expression-detail { display:none; } .chat-panel { height:calc(100dvh - 108px); } }
.camera-tools { margin-left:auto; position:relative; } .camera-tools summary { min-height:44px; display:flex; align-items:center; cursor:pointer; padding:0 8px; } .camera-tools>div { position:absolute; right:0; top:48px; width:min(300px,calc(100vw - 24px)); padding:16px; border:1px solid var(--neutral-200); border-radius:12px; background:var(--surface-primary,#fff); box-shadow:0 6px 20px rgba(20,35,28,.08); } .camera-tools p { font-size:13px; line-height:1.6; } .camera-tools button { margin:0; }
.camera-toggle-btn { display:inline-flex; align-items:center; justify-content:center; gap:8px; min-width:44px; min-height:44px; padding:8px 12px; border:1px solid var(--neutral-200); border-radius:8px; background:var(--surface-primary,#fff); color:var(--neutral-700); font:inherit; font-size:14px; cursor:pointer; }
.camera-toggle-btn:hover { background:var(--neutral-100); }
@media(max-width:700px) { .camera-toggle-btn { padding:8px; } .camera-toggle-btn span { display:none; } .topbar-center { position:static; left:auto; transform:none; } .topbar-right { gap:6px; } .timer { font-size:14px; } .timer svg { display:none; } }
.camera-toggle-btn { border-color:var(--accent-600,#059669); background:var(--accent-600,#059669); color:#fff; font-weight:600; padding:8px 14px; }
.camera-toggle-btn:hover { background:var(--accent-700,#047857); }
.camera-toggle-btn.is-active { background:var(--accent-50,#ecfdf5); color:var(--accent-700,#047857); border-color:var(--accent-300,#6ee7b7); }
.camera-toggle-btn.is-active:hover { background:var(--accent-100,#d1fae5); }
.camera-toggle-btn:focus-visible { outline:2px solid var(--accent-700,#047857); outline-offset:3px; }
.camera-label-mobile { display:none; }
.topbar .ctrl-btn { width:auto; padding:8px 12px; display:inline-flex; align-items:center; justify-content:center; gap:6px; font:inherit; font-size:14px; }
.expression-status-content { display:flex; align-items:center; gap:10px; min-width:0; }
.camera-tools summary { gap:8px; color:var(--neutral-600); font-size:13px; white-space:nowrap; }
.camera-tools[open] summary { color:var(--accent-700); }
.settings-heading { display:block; margin:8px 0; font-size:14px; }
.camera-tools>div { color:var(--neutral-700); }
.camera-tools button { display:block; width:100%; border:1px solid var(--neutral-200); border-radius:8px; margin:8px 0; text-align:left; }
@media(max-width:700px) { .camera-toggle-btn { gap:6px; padding:8px; font-size:12px; } .camera-toggle-btn .camera-label-desktop { display:none; } .camera-toggle-btn .camera-label-mobile { display:inline; } .expression-status-content { gap:6px; font-size:12px; } .camera-tools summary { padding:0 4px; font-size:12px; } .topbar .end-btn { padding:8px; font-size:12px; } .pause-label { display:none; } .topbar .ctrl-btn { width:44px; padding:8px; } }
.finish-error { color:var(--color-error,#b91c1c); font-size:13px; } .completion-status { gap:12px; } .completion-status button { margin-left:auto; }
.expression-status-content:has(.finish-error)>:not(.finish-error) { display:none; } .finish-error { font-size:12px; line-height:1.4; overflow-wrap:anywhere; } .expression-status-content:has(.finish-error) { flex:1; } </style>
