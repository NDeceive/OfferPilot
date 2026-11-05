<!--
  手机端练习会话：一题一屏，题干、作答、反馈按阅读顺序堆叠。
  主操作只有一个「提交答案 / 提交代码」；提交后主按钮变为已提交，右侧自然进入下一题或交卷。
  草稿沿用桌面端的 practiceDrafts 结构，换设备/换端都能恢复；判定中禁用切题，避免结果错位。
-->
<template>
  <MobileShell :nav="false" :messages="false" title="专项练习" class="mps-shell">
    <header class="mps-top">
      <button type="button" class="mps-back" aria-label="返回专项刷题" @click="exit">←</button>
      <div class="mps-heading">
        <strong>{{ session?.topic || '专项练习' }}</strong>
        <small v-if="questions.length">第 {{ index + 1 }} / {{ questions.length }} 题 · 已提交 {{ completed }}</small>
      </div>
      <template v-if="questions.length">
        <button v-if="readOnly" type="button" class="mps-finish" @click="router.push(reportPath)">报告</button>
        <button v-else type="button" class="mps-finish" :disabled="finishing" @click="openFinish">
          {{ finishing ? '结算中…' : '交卷' }}
        </button>
      </template>
    </header>

    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在准备题目" />
    <MobileState
      v-else-if="loadError"
      kind="error"
      title="题单暂时打不开"
      :description="loadError"
      action="重新加载"
      @action="loadSession"
    />
    <MobileState
      v-else-if="!questions.length"
      title="这个题单暂时没有题目"
      description="可以返回专项刷题，换一个知识点重新组题。"
      action="返回刷题"
      @action="router.push('/learning')"
    />

    <template v-else>
      <div class="mps-progress" role="progressbar" aria-label="练习提交进度" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
        <i :style="{ width: `${progress}%` }"></i>
      </div>

      <nav class="mps-dots" aria-label="题目目录">
        <button
          v-for="(item, number) in questions"
          :key="item.id"
          type="button"
          :class="{ done: isDone(item), active: number === index }"
          :aria-current="number === index ? 'step' : undefined"
          :aria-label="`第 ${number + 1} 题${isDone(item) ? '，已提交' : ''}`"
          @click="go(number)"
        >
          {{ number + 1 }}
        </button>
      </nav>

      <p v-if="saveError" class="mps-save-error" role="alert">{{ saveError }}<button type="button" @click="persist">重试保存</button></p>

      <article :key="question.id" class="mps-question">
        <div class="mps-meta">
          <span class="mps-tag">{{ typeLabel }}</span>
          <span v-if="difficultyText">{{ difficultyText }}</span>
          <span v-if="question.frequency">{{ question.frequency }}</span>
          <button
            type="button"
            class="mps-fav"
            :disabled="favoriteBusy"
            :aria-pressed="Boolean(question.favorite)"
            :aria-label="question.favorite ? '取消收藏本题' : '收藏本题'"
            @click="favorite"
          >{{ question.favorite ? '★' : '☆' }}</button>
        </div>

        <h1>{{ question.title }}</h1>
        <p class="mps-desc">{{ question.description || question.content }}</p>

        <template v-if="question.background">
          <h2>背景材料</h2>
          <p class="mps-desc">{{ question.background }}</p>
        </template>

        <ol v-if="question.subQuestions?.length" class="mps-subquestions">
          <li v-for="item in question.subQuestions" :key="item">{{ item }}</li>
        </ol>

        <template v-if="question.constraints?.length">
          <h2>{{ isCoding ? '要求与约束' : '回答要求' }}</h2>
          <ul class="mps-constraints"><li v-for="item in question.constraints" :key="item">{{ item }}</li></ul>
        </template>

        <pre v-if="isCoding && question.signature" class="mps-signature"><code>{{ question.signature }}</code></pre>

        <article v-for="(example, n) in question.examples || []" :key="n" class="mps-example">
          <h2>示例 {{ n + 1 }}</h2>
          <p>输入</p>
          <pre>{{ example.input }}</pre>
          <p>输出</p>
          <pre>{{ example.expected }}</pre>
          <p v-if="example.explanation" class="mps-hint">{{ example.explanation }}</p>
        </article>

        <div v-if="question.tags?.length" class="mps-tags">
          <span v-for="tag in question.tags" :key="tag">{{ tag }}</span>
        </div>

        <details v-if="question.hints?.length" class="mps-details">
          <summary>卡住了？看一条提示</summary>
          <p v-for="hint in question.hints" :key="hint">{{ hint }}</p>
        </details>

      </article>

      <section class="mps-answer" aria-labelledby="mps-answer-title">
        <header class="mps-answer-head">
          <h2 id="mps-answer-title">{{ answerTitle }}</h2>
          <span>{{ saveLabel }}</span>
        </header>

        <fieldset v-if="isObjective" class="mps-options" :disabled="readOnly || submitting">
          <legend class="sr-only">{{ isMultiple ? '选择全部正确选项' : isTrueFalse ? '判断正误' : '选择一个正确答案' }}</legend>
          <button
            v-for="option in question.options || []"
            :key="option.key"
            type="button"
            class="mps-option"
            :class="{ selected: selectedKeys.includes(option.key) }"
            :aria-pressed="selectedKeys.includes(option.key)"
            @click="pick(option.key)"
          >
            <span v-if="!isTrueFalse" class="mps-option-key">{{ option.key }}</span>
            <span class="mps-option-text">{{ option.text }}</span>
            <span class="mps-option-check" aria-hidden="true"></span>
          </button>
        </fieldset>

        <div v-else-if="isCoding" class="mps-code">
          <div class="mps-code-bar">
            <span>{{ question.language || 'java' }} · 代码</span>
            <button type="button" :disabled="readOnly || submitting" @click="resetCode">重置模板</button>
          </div>
          <textarea
            v-model="current.code"
            :readonly="readOnly"
            spellcheck="false"
            autocapitalize="off"
            autocomplete="off"
            wrap="off"
            aria-label="代码作答"
            placeholder="// 在这里写代码"
          ></textarea>
        </div>

        <textarea
          v-else
          v-model="current.answer"
          :readonly="readOnly"
          class="mps-textarea"
          maxlength="10000"
          aria-label="文字回答"
          placeholder="写下你的回答"
        ></textarea>
      </section>

      <section v-if="showFeedback" ref="feedbackEl" class="mps-feedback" :class="`is-${feedbackTone}`" aria-live="polite">
        <header class="mps-feedback-head">
          <span class="mps-feedback-dot" aria-hidden="true"></span>
          <strong>{{ feedbackTitle }}</strong>
          <small v-if="isCoding && current.taskId">任务 #{{ current.taskId }}</small>
        </header>

        <p v-if="current.error" class="mps-feedback-error">{{ current.error }}</p>
        <p v-else-if="submitting" class="mps-hint">{{ isCoding ? '正在判分，请稍候。' : '正在提交，请稍候。' }}</p>
        <template v-else-if="submitted">
          <template v-if="!feedbackAvailable">
            <p>已保存。</p>
          </template>
          <template v-else>
            <p v-if="current.result?.message && current.result.status !== 'COMPILE_ERROR'">{{ current.result.message }}</p>
            <p v-if="isObjective && correctAnswer" class="mps-correct">正确答案：{{ displayAnswer(correctAnswer) }}</p>
            <div v-if="reference" class="mps-reference">
              <strong>参考解析</strong>
              <p class="mps-pre">{{ reference }}</p>
            </div>
            <pre v-if="isCoding && current.result?.compileOutput" class="mps-compile">{{ current.result.compileOutput }}</pre>
            <div v-if="feedbackAvailable" class="mps-feedback-actions">
              <button type="button" :disabled="reviewBusy" @click="review">
                {{ (current.reviewState || question.reviewState) === 'PENDING' ? '标记复习完成' : '加入待复习' }}
              </button>
            </div>
          </template>
        </template>
      </section>

      <div class="mps-bottom-space" aria-hidden="true"></div>
    </template>

    <footer v-if="questions.length" class="mps-actionbar" aria-label="答题操作">
      <button type="button" class="mps-nav" :disabled="index === 0 || submitting" @click="go(index - 1)">上一题</button>
      <button v-if="readOnly" type="button" class="mps-primary" @click="router.push(reportPath)">查看练习报告</button>
      <button
        v-else
        type="button"
        class="mps-primary"
        :disabled="submitting || (submitted && !dirty)"
        @click="submit"
      >{{ submitLabel }}</button>
      <button
        type="button"
        class="mps-nav"
        :disabled="submitting"
        @click="rightAction"
      >{{ rightLabel }}</button>
    </footer>

    <Transition name="mps-sheet">
      <div v-if="finishOpen" class="mps-sheet-backdrop" @click.self="finishOpen = false">
        <section class="mps-sheet" role="dialog" aria-modal="true" aria-labelledby="mps-finish-title">
          <span class="mps-sheet-grip" aria-hidden="true"></span>
          <h2 id="mps-finish-title">{{ unanswered ? '还有题目没提交' : '全部答完了' }}</h2>
          <p>
            已提交 <strong>{{ completed }}</strong> / {{ questions.length }} 题<template v-if="unanswered">，还有 {{ unanswered }} 题未提交</template>。
          </p>
          <div class="mps-sheet-actions">
            <button type="button" @click="finishOpen = false">继续作答</button>
            <button type="button" class="mps-primary" :disabled="finishing" @click="finish">
              {{ finishing ? '正在结算…' : '交卷并看报告' }}
            </button>
          </div>
        </section>
      </div>
    </Transition>

    <Transition name="mps-toast">
      <p v-if="actionError" class="mps-toast" role="alert">{{ actionError }}</p>
    </Transition>
  </MobileShell>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { useUserStore } from '../../store/user'
import {
  answerPracticeQuestion, createJudgeTask, finishTrainingSession, getJudgeTask,
  getTrainingSession, setPracticeReview, togglePracticeFavorite,
} from '../../services/learningResources'
import { judgeStatusLabel, pendingJudgeStatuses } from '../../utils/practiceJudging'
import { readPracticeDraft, writePracticeDraft } from '../../utils/practiceDrafts'
import { isObjectiveType, practiceTypeLabel, toggleObjectiveAnswer } from '../../utils/practiceTypes'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const sessionId = route.params.sessionId
const ownerId = computed(() => String(store.userId || localStorage.getItem('userId') || ''))
const reportPath = `/learning/session/${sessionId}/report`

const session = ref(null)
const loading = ref(true)
const loadError = ref('')
const index = ref(0)
const drafts = reactive({})
const submitting = ref(false)
const finishing = ref(false)
const finishOpen = ref(false)
const favoriteBusy = ref(false)
const reviewBusy = ref(false)
const actionError = ref('')
const saveLabel = ref('草稿')
const saveError = ref('')
const feedbackEl = ref(null)
let saveTimer = null
let toastTimer = null
let disposed = false

const questions = computed(() => session.value?.questionList || [])
const question = computed(() => questions.value[index.value] || {})
const current = computed(() => drafts[question.value.id] || {})
const type = computed(() => String(question.value.type || '').toLowerCase())
const isCoding = computed(() => type.value === 'coding')
const isObjective = computed(() => isObjectiveType(type.value))
const isMultiple = computed(() => type.value === 'multiple_choice')
const isTrueFalse = computed(() => type.value === 'true_false')
const readOnly = computed(() => Boolean(session.value?.status && session.value.status !== 'ONGOING'))
const feedbackAvailable = computed(() => !session.value?.deferFeedback || session.value?.status === 'FINISHED')
const submitted = computed(() => Boolean(current.value.submitted || question.value.latestSubmission?.id || question.value.status === 'ANSWERED'))
const dirty = computed(() => {
  const value = isCoding.value ? current.value.code : current.value.answer
  return String(value || '').trim() !== String(current.value.submittedValue || '').trim()
})
const completed = computed(() => questions.value.filter(item => isDone(item)).length)
const unanswered = computed(() => Math.max(0, questions.value.length - completed.value))
const progress = computed(() => questions.value.length ? Math.round(completed.value / questions.value.length * 100) : 0)
const selectedKeys = computed(() => String(current.value.answer || '').split(',').filter(Boolean))
const typeLabel = computed(() => question.value.questionType === 'CULTURE' || question.value.subtype === 'CULTURE' ? '企业情境' : practiceTypeLabel(type.value))
const difficultyText = computed(() => ({ 1: '基础', 2: '中等', 3: '困难' })[question.value.difficulty] || question.value.difficulty || '')
const answerTitle = computed(() => isCoding.value ? '代码作答' : isObjective.value ? '选择答案' : '写下你的回答')
const submitLabel = computed(() => {
  if (submitting.value) return isCoding.value ? '判题中…' : '提交中…'
  if (submitted.value && !dirty.value) return '已提交'
  return submitted.value ? '重新提交' : isCoding.value ? '提交代码' : '提交答案'
})
const rightLabel = computed(() => {
  if (index.value < questions.value.length - 1) return '下一题'
  return readOnly.value ? '返回刷题' : '交卷'
})
const correctAnswer = computed(() => current.value.result?.correctAnswer || '')
const reference = computed(() => feedbackAvailable.value ? current.value.result?.referenceAnswer || current.value.referenceAnswer || question.value.referenceAnswer || '' : '')
const showFeedback = computed(() => Boolean(submitted.value || submitting.value || current.value.error))
const feedbackTone = computed(() => {
  if (current.value.error) return 'error'
  if (submitting.value) return 'pending'
  if (!feedbackAvailable.value) return 'done'
  const result = current.value.result || {}
  if (result.passed === false || ['OBJECTIVE_INCORRECT', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'SYSTEM_ERROR', 'SERVICE_UNAVAILABLE'].includes(result.status)) return 'failed'
  if (result.passed === true || ['OBJECTIVE_CORRECT', 'ACCEPTED'].includes(result.status)) return 'passed'
  if (pendingJudgeStatuses.has(result.status) || result.status === 'SELF_REVIEW' || result.status === 'SUBMITTED') return 'pending'
  return 'done'
})
const feedbackTitle = computed(() => {
  if (current.value.error) return '这次提交没有完成'
  if (submitting.value) return isCoding.value ? '正在判题' : '正在提交'
  if (!feedbackAvailable.value) return '已提交'
  const result = current.value.result || {}
  if (result.passed === true || result.status === 'ACCEPTED' || result.status === 'OBJECTIVE_CORRECT') return isCoding.value ? '代码通过' : '回答正确'
  if (result.passed === false || result.status === 'OBJECTIVE_INCORRECT') return isCoding.value ? '这次没通过' : '回答错误'
  if (result.status === 'COMPILE_ERROR') return '编译没通过'
  if (pendingJudgeStatuses.has(result.status)) return '判题处理中'
  return result.status ? judgeStatusLabel(result.status) : '已提交'
})

function isDone(item) {
  const draft = drafts[item.id]
  return Boolean(draft?.submitted || item.latestSubmission?.id || item.status === 'ANSWERED')
}

function displayAnswer(value) {
  return value === 'true' ? '正确' : value === 'false' ? '错误' : value
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
}

function toast(message) {
  actionError.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { actionError.value = '' }, 3200)
}

function snapshot() {
  return {
    index: index.value,
    questions: Object.fromEntries(Object.entries(drafts).map(([id, draft]) => [id, {
      answer: draft.answer,
      code: draft.code,
      submitted: draft.submitted,
      submittedValue: draft.submittedValue,
      result: draft.result,
      taskId: draft.taskId,
      referenceAnswer: draft.referenceAnswer,
      reviewState: draft.reviewState,
    }])),
  }
}

function persist() {
  clearTimeout(saveTimer)
  if (!ownerId.value) {
    saveLabel.value = '未保存'
    return false
  }
  try {
    writePracticeDraft(localStorage, ownerId.value, sessionId, snapshot())
    saveLabel.value = '已保存'
    saveError.value = ''
    return true
  } catch {
    saveLabel.value = '未保存'
    saveError.value = '保存失败，内容仍在当前页面。'
    return false
  }
}

watch([() => current.value.answer, () => current.value.code], () => {
  if (loading.value || disposed) return
  saveLabel.value = '保存中…'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(persist, 400)
})

async function loadSession() {
  loading.value = true
  loadError.value = ''
  try {
    const loaded = await getTrainingSession(sessionId)
    if (disposed) return
    session.value = loaded
    let stored = {}
    try {
      if (!ownerId.value) throw new Error('missing user')
      stored = readPracticeDraft(localStorage, ownerId.value, sessionId)
    } catch {
      saveError.value = '草稿暂时读取不到，本次作答仍可继续。'
    }
    for (const item of loaded.questionList || []) {
      const saved = stored.questions?.[item.id] || {}
      const latest = item.latestSubmission || {}
      const latestValue = latest.id ? (latest.sourceCode ?? latest.answerText ?? '') : ''
      const answer = saved.answer ?? latest.answerText ?? ''
      const code = saved.code ?? latest.sourceCode ?? item.starter ?? ''
      drafts[item.id] = {
        answer,
        code,
        submitted: Boolean(saved.submitted || latest.id || item.status === 'ANSWERED'),
        submittedValue: saved.submittedValue ?? latestValue,
        result: saved.result || latest.result || null,
        taskId: saved.taskId || null,
        referenceAnswer: saved.referenceAnswer || item.referenceAnswer || '',
        reviewState: saved.reviewState || item.reviewState || '',
        error: '',
      }
    }
    const list = loaded.questionList || []
    const requested = list.findIndex(item => String(item.id) === String(route.query.questionId))
    const restored = Number.isInteger(stored.index) ? stored.index : Number(loaded.currentIndex) || 0
    index.value = requested >= 0 ? requested : Math.max(0, Math.min(list.length - 1, restored))
    if (stored.questions) saveLabel.value = '已恢复草稿'
  } catch (error) {
    if (!disposed) loadError.value = error.message || '题单加载失败，请重试。'
  } finally {
    if (!disposed) loading.value = false
  }
}

function go(next) {
  if (submitting.value || next < 0 || next >= questions.value.length || next === index.value) return
  persist()
  index.value = next
  nextTick(scrollTop)
}

function rightAction() {
  if (index.value < questions.value.length - 1) return go(index.value + 1)
  if (readOnly.value) return router.push('/learning')
  openFinish()
}

function pick(key) {
  if (readOnly.value || submitting.value) return
  current.value.answer = toggleObjectiveAnswer(current.value.answer, key, type.value === 'multiple_choice')
}

function resetCode() {
  if (readOnly.value || submitting.value) return
  if (!confirm('恢复初始代码模板？当前代码会被替换。')) return
  current.value.code = question.value.starter || ''
}

function randomKey() {
  return crypto.randomUUID?.() || `mps-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function applyTask(q, draft, task) {
  if (!draft) return
  if (draft.taskId && Number(draft.taskId) > Number(task.id)) return
  draft.taskId = task.id
  draft.result = { ...(task.result || {}), status: task.status, taskId: task.id, submitted: Boolean(task.result?.submitted) }
  if (task.result?.submitted) {
    draft.submitted = true
    draft.submittedValue = draft.code
    if (q) q.status = 'ANSWERED'
  }
  if (!pendingJudgeStatuses.has(task.status)) {
    draft.pendingRequest = null
    if (['SYSTEM_ERROR', 'SERVICE_UNAVAILABLE'].includes(task.status)) {
      draft.error = task.result?.message || '判题服务异常，请稍后重试。'
    }
  }
}

async function waitTask(questionId, taskId) {
  for (let attempt = 0; attempt < 150 && !disposed; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 1000))
    if (disposed) return
    try {
      const task = await getJudgeTask(taskId)
      const q = questions.value.find(item => String(item.id) === String(questionId))
      applyTask(q, drafts[questionId], task)
      if (!pendingJudgeStatuses.has(task.status)) return
    } catch {
      // 单次轮询失败不中断，下一轮继续；超过上限再提示。
    }
  }
  if (!disposed && drafts[questionId]) drafts[questionId].error = '判题时间较长，可以稍后在练习报告里查看结果。'
}

async function submit() {
  if (readOnly.value || submitting.value || !question.value.id) return
  if (submitted.value && !dirty.value) return
  const q = question.value
  const draft = current.value
  const value = isCoding.value ? draft.code : draft.answer
  if (!String(value || '').trim()) {
    draft.error = isCoding.value ? '请先编写代码。' : isObjective.value ? '请先选择答案。' : '请先写下回答。'
    return
  }
  submitting.value = true
  draft.error = ''
  draft.submittedValue = draft.submittedValue || ''
  try {
    if (isCoding.value) {
      if (!draft.pendingRequest || draft.pendingRequest.sourceCode !== value || draft.pendingRequest.operation !== 'submit') {
        draft.pendingRequest = {
          requestKey: randomKey(),
          operation: 'submit',
          sourceCode: value,
          language: q.language || 'java',
        }
      }
      const task = await createJudgeTask(sessionId, q.id, draft.pendingRequest)
      if (disposed) return
      applyTask(q, draft, task)
      if (pendingJudgeStatuses.has(task.status)) await waitTask(q.id, task.id)
    } else {
      const result = await answerPracticeQuestion(sessionId, q.id, value)
      if (disposed) return
      draft.result = { ...result, submitted: true }
      draft.submitted = true
      draft.submittedValue = value
      q.status = 'ANSWERED'
      if (result.referenceAnswer) draft.referenceAnswer = result.referenceAnswer
      if (isObjective.value && feedbackAvailable.value) draft.reviewState = result.passed ? 'RESOLVED' : 'PENDING'
    }
    persist()
    await nextTick()
    feedbackEl.value?.scrollIntoView({
      behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'nearest',
    })
  } catch (error) {
    if (!disposed) draft.error = error.message || '提交失败，请重试。'
  } finally {
    if (!disposed) submitting.value = false
  }
}

async function favorite() {
  if (favoriteBusy.value || !question.value.id) return
  favoriteBusy.value = true
  try {
    const result = await togglePracticeFavorite(question.value.id)
    question.value.favorite = result.favorite
  } catch (error) {
    toast(error.message || '收藏失败，请重试。')
  } finally {
    favoriteBusy.value = false
  }
}

async function review() {
  if (reviewBusy.value || !question.value.id) return
  reviewBusy.value = true
  const q = question.value
  const draft = current.value
  try {
    const state = (draft.reviewState || q.reviewState) === 'PENDING' ? 'RESOLVED' : 'PENDING'
    const result = await setPracticeReview(q.id, state)
    q.reviewState = result.state
    draft.reviewState = result.state
    persist()
  } catch (error) {
    toast(error.message || '复习状态更新失败，请重试。')
  } finally {
    reviewBusy.value = false
  }
}

function openFinish() {
  if (finishing.value) return
  if (readOnly.value) return router.push(reportPath)
  persist()
  finishOpen.value = true
}

async function finish() {
  if (finishing.value) return
  if (!persist()) {
    toast('草稿保存失败，请先重试保存。')
    return
  }
  finishing.value = true
  try {
    await finishTrainingSession(sessionId)
    if (disposed) return
    finishOpen.value = false
    router.replace(reportPath)
  } catch (error) {
    toast(error.message || '交卷失败，请重试。')
  } finally {
    finishing.value = false
  }
}

function exit() {
  persist()
  router.push('/learning')
}

function onKeydown(event) {
  if (event.key === 'Escape' && finishOpen.value) finishOpen.value = false
}

watch(finishOpen, open => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  // 从长列表进入时浏览器会保留上一页滚动位置；练习页必须从第 1 题顶部开始。
  window.scrollTo(0, 0)
  loadSession()
  window.addEventListener('keydown', onKeydown)
})

onBeforeRouteLeave(() => {
  if (questions.value.length) persist()
  return true
})

onBeforeUnmount(() => {
  disposed = true
  clearTimeout(saveTimer)
  clearTimeout(toastTimer)
  if (questions.value.length) persist()
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.mps-shell :deep(.mobile-pagebar) { display: none; }

.mps-top {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 -20px;
  padding: max(12px, env(safe-area-inset-top)) 16px 10px;
  background: color-mix(in srgb, var(--m-bg) 92%, transparent);
  border-bottom: 1px solid transparent;
  backdrop-filter: blur(14px);
}

.mps-back {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: 50%;
  font-size: 19px;
  line-height: 1;
  place-items: center;
  transition: transform var(--m-motion) ease, background var(--m-motion) ease;
}
.mps-back:active { transform: scale(.92); background: var(--m-primary-soft); }

.mps-heading { min-width: 0; flex: 1; }
.mps-heading strong {
  display: block;
  overflow: hidden;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mps-heading small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 11.5px; font-variant-numeric: tabular-nums; }

.mps-finish {
  flex: 0 0 auto;
  min-width: 58px;
  min-height: 40px;
  padding: 0 14px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 800;
  transition: transform var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mps-finish:active:not(:disabled) { transform: scale(.94); }
.mps-finish:disabled { opacity: .55; }

.mps-progress {
  height: 4px;
  margin-top: 12px;
  overflow: hidden;
  background: var(--m-border);
  border-radius: 999px;
}
.mps-progress i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #3f8f6c, var(--m-primary));
  border-radius: inherit;
  transition: width var(--m-motion-slow);
}

.mps-dots {
  display: flex;
  gap: 7px;
  padding: 12px 0 4px;
  overflow-x: auto;
  scrollbar-width: none;
}
.mps-dots::-webkit-scrollbar { display: none; }
.mps-dots button {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  color: var(--m-text-secondary);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: 10px;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  place-items: center;
  transition: color var(--m-motion) ease, background var(--m-motion) ease, border-color var(--m-motion) ease, transform var(--m-motion) ease;
}
.mps-dots button:active { transform: scale(.92); }
.mps-dots button.done { color: var(--m-primary-dark); background: var(--m-primary-soft); border-color: #cfe6d8; }
.mps-dots button.active { color: #fff; background: var(--m-primary-dark); border-color: var(--m-primary-dark); box-shadow: 0 4px 12px rgba(23, 75, 57, .22); }

.mps-save-error {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 10px 12px;
  color: #a4352b;
  background: #fff0ee;
  border-radius: var(--m-radius-input);
  font-size: 12.5px;
  line-height: 1.5;
}
.mps-save-error button {
  min-height: 32px;
  margin-left: auto;
  padding: 0 10px;
  color: #a4352b;
  background: transparent;
  border: 1px solid #efc9c5;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.mps-question {
  margin-top: 12px;
  padding: 20px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-hero);
  box-shadow: 0 2px 12px rgba(32, 61, 48, .035);
  animation: mps-rise 360ms cubic-bezier(.16, 1, .3, 1) both;
}
.mps-meta { display: flex; align-items: center; gap: 7px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.5; }
.mps-meta > span { padding: 3px 7px; background: var(--m-surface-soft); border-radius: 5px; }
.mps-meta .mps-tag { color: var(--m-primary-dark); background: var(--m-primary-soft); font-weight: 700; }
.mps-fav {
  display: grid;
  min-width: 36px;
  min-height: 36px;
  margin-left: auto;
  color: var(--m-text-tertiary);
  background: transparent;
  border: 0;
  border-radius: 50%;
  font-size: 19px;
  line-height: 1;
  place-items: center;
  transition: color var(--m-motion) ease, transform var(--m-motion) ease;
}
.mps-fav[aria-pressed="true"] { color: #c47a1d; }
.mps-fav:active { transform: scale(.88); }

.mps-notice {
  margin-top: 12px;
  padding: 9px 11px;
  color: #5c4429;
  background: var(--m-accent-soft);
  border-radius: 9px;
  font-size: 12px;
  line-height: 1.6;
}

.mps-question h1 {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -.03em;
  line-height: 1.35;
}
.mps-question h2 { margin-top: 20px; font-size: 13px; font-weight: 800; letter-spacing: .01em; }
.mps-desc { margin-top: 10px; color: #31443a; font-size: 15px; line-height: 1.78; white-space: pre-wrap; overflow-wrap: anywhere; }
.mps-question h2 + .mps-desc { margin-top: 6px; }
.mps-subquestions,
.mps-constraints { display: grid; gap: 6px; margin-top: 8px; padding-left: 20px; color: #31443a; font-size: 14px; line-height: 1.7; }
.mps-signature { margin-top: 12px; }
.mps-question pre,
.mps-compile {
  max-width: 100%;
  margin-top: 8px;
  padding: 11px 12px;
  overflow-x: auto;
  color: #dce9e0;
  background: #17241e;
  border-radius: 9px;
  font: 12.5px/1.7 Consolas, 'SFMono-Regular', monospace;
  white-space: pre;
  -webkit-overflow-scrolling: touch;
}
.mps-example { margin-top: 16px; }
.mps-example h2 { margin-top: 0; }
.mps-example > p { margin-top: 6px; color: var(--m-text-tertiary); font-size: 11.5px; }
.mps-example pre { margin-top: 4px; }
.mps-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 16px; }
.mps-tags span { padding: 3px 8px; color: var(--m-text-secondary); background: var(--m-surface-soft); border-radius: 999px; font-size: 11.5px; }
.mps-details { margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--m-border); }
.mps-details summary { min-height: 36px; color: var(--m-primary); font-size: 12.5px; font-weight: 700; cursor: pointer; }
.mps-details p { margin-top: 6px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.7; overflow-wrap: anywhere; }
.mps-details a { color: var(--m-primary); text-decoration: underline; text-underline-offset: 2px; }

.mps-answer {
  margin-top: 12px;
  padding: 18px 16px 16px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-hero);
  box-shadow: 0 2px 12px rgba(32, 61, 48, .035);
  animation: mps-rise 360ms cubic-bezier(.16, 1, .3, 1) 60ms both;
}
.mps-answer-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.mps-answer-head h2 { font-size: 15px; font-weight: 800; }
.mps-answer-head span { color: var(--m-text-tertiary); font-size: 11.5px; font-variant-numeric: tabular-nums; }

.mps-options { display: grid; min-width: 0; margin-top: 12px; padding: 0; gap: 9px; border: 0; }
.mps-option {
  display: flex;
  width: 100%;
  min-height: 52px;
  padding: 12px 12px;
  align-items: center;
  gap: 10px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: 12px;
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  text-align: left;
  transition: border-color var(--m-motion) ease, background var(--m-motion) ease, transform var(--m-motion) ease;
}
.mps-option:active:not(:disabled) { transform: scale(.985); }
.mps-option.selected { background: var(--m-primary-soft); border-color: var(--m-primary); }
.mps-option-key {
  display: grid;
  flex: 0 0 auto;
  width: 27px;
  height: 27px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 800;
  place-items: center;
}
.mps-option.selected .mps-option-key { color: #fff; background: var(--m-primary); }
.mps-option-text { flex: 1; overflow-wrap: anywhere; }
.mps-option-check {
  display: grid;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border: 1.5px solid #cbd8d0;
  border-radius: 50%;
  place-items: center;
  transition: border-color var(--m-motion) ease, background var(--m-motion) ease;
}
.mps-option.selected .mps-option-check { background: var(--m-primary); border-color: var(--m-primary); }
.mps-option.selected .mps-option-check::after {
  width: 9px;
  height: 5px;
  border-bottom: 2px solid #fff;
  border-left: 2px solid #fff;
  content: '';
  transform: translateY(-1px) rotate(-45deg);
}
.mps-options:disabled .mps-option { opacity: .8; }

.mps-code { margin-top: 12px; overflow: hidden; background: #12211b; border-radius: 12px; }
.mps-code-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 12px; color: #a9c4b5; border-bottom: 1px solid rgba(255, 255, 255, .08); font-size: 11.5px; letter-spacing: .02em; }
.mps-code-bar button { min-height: 32px; padding: 0 10px; color: #cfe3d6; background: rgba(255, 255, 255, .08); border: 0; border-radius: 7px; font-size: 11.5px; }
.mps-code textarea {
  display: block;
  width: 100%;
  height: 280px;
  padding: 13px 14px calc(13px + env(safe-area-inset-bottom));
  color: #e8f3ec;
  background: transparent;
  border: 0;
  font: 13px/1.65 Consolas, 'SFMono-Regular', monospace;
  resize: vertical;
  tab-size: 2;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
  outline: none;
}

.mps-textarea {
  display: block;
  width: 100%;
  min-height: 190px;
  margin-top: 12px;
  padding: 13px 14px;
  color: var(--m-text);
  background: var(--m-surface-soft);
  border: 1px solid transparent;
  border-radius: 12px;
  font: inherit;
  font-size: 14.5px;
  line-height: 1.8;
  resize: vertical;
  transition: border-color var(--m-motion) ease, background var(--m-motion) ease, box-shadow var(--m-motion) ease;
}
.mps-textarea:focus { background: var(--m-surface); border-color: var(--m-primary); box-shadow: 0 0 0 3px var(--m-primary-soft); outline: none; }
.mps-textarea::placeholder,
.mps-code textarea::placeholder { color: var(--m-text-tertiary); }
.mps-code textarea::placeholder { color: #708a7b; }

.mps-hint { margin-top: 9px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.65; }

.mps-feedback {
  margin-top: 12px;
  padding: 16px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  animation: mps-rise 340ms cubic-bezier(.16, 1, .3, 1) 40ms both;
}
.mps-feedback.is-passed { background: var(--m-primary-soft); border-color: #cfe6d8; }
.mps-feedback.is-failed { background: #fff1ee; border-color: #f2d2cc; }
.mps-feedback.is-pending { background: var(--m-accent-soft); border-color: #f2ddc3; }
.mps-feedback-head { display: flex; align-items: center; gap: 8px; }
.mps-feedback-dot { width: 8px; height: 8px; flex: 0 0 auto; background: var(--m-primary); border-radius: 50%; }
.mps-feedback.is-failed .mps-feedback-dot { background: #b43b3b; }
.mps-feedback.is-pending .mps-feedback-dot { animation: mps-pulse 1.2s ease-in-out infinite; background: #b66b20; }
.mps-feedback-head strong { font-size: 14px; font-weight: 800; }
.mps-feedback-head small { margin-left: auto; color: var(--m-text-tertiary); font-size: 11px; }
.mps-feedback > p { margin-top: 8px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.7; }
.mps-feedback-error { color: #a4352b !important; }
.mps-correct { color: var(--m-primary-dark) !important; font-weight: 700; }
.mps-reference { margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(23, 75, 57, .13); }
.mps-reference strong { font-size: 12px; }
.mps-reference p { margin-top: 5px; color: #31443a; font-size: 13px; line-height: 1.75; }
.mps-pre { white-space: pre-wrap; overflow-wrap: anywhere; }
.mps-feedback-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.mps-feedback-actions button {
  min-height: 38px;
  padding: 0 13px;
  color: var(--m-primary-dark);
  background: rgba(255, 255, 255, .7);
  border: 1px solid rgba(23, 75, 57, .16);
  border-radius: 9px;
  font-size: 12.5px;
  font-weight: 700;
}
.mps-feedback-actions button:active { transform: scale(.96); }

.mps-bottom-space { height: 104px; }

.mps-actionbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  z-index: 24;
  display: flex;
  width: min(100%, 480px);
  align-items: center;
  gap: 8px;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  background: color-mix(in srgb, var(--m-surface) 96%, transparent);
  border-top: 1px solid var(--m-border);
  box-shadow: 0 -10px 28px rgba(16, 33, 26, .07);
  transform: translateX(-50%);
  backdrop-filter: blur(12px);
  animation: mps-bar-in 440ms cubic-bezier(.16, 1, .3, 1) 100ms both;
}
.mps-nav {
  min-width: 68px;
  min-height: 46px;
  padding: 0 10px;
  color: var(--m-text-secondary);
  background: transparent;
  border: 1px solid var(--m-border);
  border-radius: 11px;
  font-size: 12.5px;
  font-weight: 600;
  white-space: nowrap;
  transition: transform var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mps-nav:active:not(:disabled) { transform: scale(.95); }
.mps-nav:disabled { opacity: .4; }
.mps-primary {
  flex: 1;
  min-width: 0;
  min-height: 46px;
  padding: 0 12px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: -.01em;
  white-space: nowrap;
  transition: transform var(--m-motion) ease, background var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mps-primary:active:not(:disabled) { transform: scale(.98); background: var(--m-primary-dark); }
.mps-primary:disabled { opacity: .5; }
.mps-nav:disabled,
.mps-primary:disabled { cursor: default; }

.mps-sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(12, 26, 19, .34);
  backdrop-filter: blur(3px);
}
.mps-sheet {
  position: fixed;
  bottom: 0;
  left: 50%;
  width: min(100%, 480px);
  padding: 10px 20px calc(18px + env(safe-area-inset-bottom));
  background: var(--m-surface);
  border-radius: 22px 22px 0 0;
  box-shadow: 0 -18px 44px rgba(16, 33, 26, .2);
  transform: translateX(-50%);
}
.mps-sheet-grip { display: block; width: 38px; height: 4px; margin: 2px auto 16px; background: var(--m-border); border-radius: 999px; }
.mps-sheet h2 { font-size: 20px; font-weight: 800; letter-spacing: -.03em; }
.mps-sheet p { margin-top: 9px; color: var(--m-text-secondary); font-size: 13.5px; line-height: 1.75; }
.mps-sheet-actions { display: flex; gap: 10px; margin-top: 20px; }
.mps-sheet-actions button {
  flex: 1;
  min-height: 48px;
  color: var(--m-text);
  background: var(--m-surface-soft);
  border: 0;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  transition: transform var(--m-motion) ease;
}
.mps-sheet-actions button:active { transform: scale(.98); }
.mps-sheet-actions .mps-primary { color: #fff; background: var(--m-primary); }

.mps-toast {
  position: fixed;
  bottom: calc(82px + env(safe-area-inset-bottom));
  left: 50%;
  z-index: 48;
  width: max-content;
  max-width: min(calc(100% - 40px), 440px);
  padding: 10px 14px;
  color: #fff;
  background: rgba(19, 37, 28, .92);
  border-radius: 10px;
  font-size: 12.5px;
  line-height: 1.55;
  transform: translateX(-50%);
  box-shadow: 0 8px 24px rgba(16, 33, 26, .2);
}

.mps-sheet-enter-active,
.mps-sheet-leave-active { transition: opacity 240ms cubic-bezier(.16, 1, .3, 1); }
.mps-sheet-enter-active .mps-sheet,
.mps-sheet-leave-active .mps-sheet { transition: transform 300ms cubic-bezier(.16, 1, .3, 1); }
.mps-sheet-enter-from,
.mps-sheet-leave-to { opacity: 0; }
.mps-sheet-enter-from .mps-sheet,
.mps-sheet-leave-to .mps-sheet { transform: translate(-50%, 100%); }

.mps-toast-enter-active,
.mps-toast-leave-active { transition: opacity var(--m-motion) ease, transform var(--m-motion) ease; }
.mps-toast-enter-from,
.mps-toast-leave-to { opacity: 0; transform: translate(-50%, 6px); }

@keyframes mps-rise {
  from { opacity: 0; transform: translateY(9px); }
  to { opacity: 1; transform: none; }
}
@keyframes mps-bar-in {
  from { opacity: 0; transform: translate(-50%, 12px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}
@keyframes mps-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: .45; transform: scale(.8); }
}

@media (max-width: 359px) {
  .mps-question { padding: 16px; }
  .mps-question h1 { font-size: 20px; }
  .mps-nav { min-width: 56px; padding-inline: 6px; font-size: 12px; }
  .mps-primary { font-size: 13px; }
  .mps-actionbar { gap: 6px; padding-inline: 12px; }
  .mps-code textarea { height: 240px; }
}

@media (prefers-reduced-motion: reduce) {
  .mps-question,
  .mps-answer,
  .mps-feedback,
  .mps-actionbar,
  .mps-feedback.is-pending .mps-feedback-dot { animation: none; }
  .mps-sheet-enter-active,
  .mps-sheet-leave-active,
  .mps-sheet-enter-active .mps-sheet,
  .mps-sheet-leave-active .mps-sheet,
  .mps-toast-enter-active,
  .mps-toast-leave-active { transition: none; }
}

.mps-back:focus-visible,
.mps-finish:focus-visible,
.mps-dots button:focus-visible,
.mps-option:focus-visible,
.mps-textarea:focus-visible,
.mps-code textarea:focus-visible,
.mps-nav:focus-visible,
.mps-primary:focus-visible,
.mps-sheet-actions button:focus-visible,
.mps-fav:focus-visible,
.mps-feedback-actions button:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }
</style>
