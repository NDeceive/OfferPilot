<template>
  <AppLayout>
    <main ref="page" class="practice-page" :class="{ 'compact-height': pageHeight < 460 }" :style="{ height: `${pageHeight}px` }">
      <div v-if="loading || loadError || !questions.length" class="page-state">
        <h1>专项刷题</h1>
        <p>{{ loading ? '正在加载题单…' : loadError || '这个题单暂无题目，请返回重新选择。' }}</p>
        <button v-if="loadError" @click="loadSession">重新加载</button>
        <button @click="router.push('/learning')">返回学习资源</button>
      </div>
      <template v-else>
        <header class="practice-header">
          <button aria-label="返回题单" @click="router.push('/learning')">←</button>
          <div class="heading"><h1>{{ session.topic || '专项刷题' }}</h1><p>{{ session.role?.name }} · 专项刷题<span v-if="session.status === 'FINISHED'"> · 已结束，只读复盘</span></p></div>
          <div class="progress"><strong>第 {{ index + 1 }} / {{ questions.length }} 题</strong><span>已提交 {{ completed }} 题</span></div>
          <button v-if="readOnly" @click="router.push(`/learning/session/${sessionId}/report`)">查看练习报告</button><button v-else :disabled="finishing" @click="finish">结束练习</button>
        </header>

        <nav class="mobile-tabs" aria-label="工作区">
          <button v-for="tab in ['题目','作答','结果']" :key="tab" :aria-pressed="mobileTab === tab" @click="mobileTab = tab">{{ tab }}</button>
        </nav>
        <div v-if="saveError" class="save-error" role="alert">{{ saveError }} <button @click="persist">重试保存</button></div>

        <div ref="workspace" class="workspace" :style="{ gridTemplateColumns: `minmax(0,${split}fr) 12px minmax(0,${100-split}fr)` }">
          <section class="question-pane" :class="{ 'mobile-hidden': mobileTab !== '题目' }">
            <nav class="pane-tabs" aria-label="题目内容">
              <button v-for="tab in ['题目','解析','提交记录']" :key="tab" :aria-pressed="leftTab === tab" @click="leftTab = tab">{{ tab }}</button>
            </nav>
            <div ref="questionScroll" class="question-content" @scroll="captureScroll">
              <template v-if="leftTab === '题目'">
                <div class="question-meta"><span>{{ question.subtype === 'CULTURE' ? '企业情境' : practiceTypeLabel(question.type) }}</span><span v-if="question.difficulty">{{ difficultyLabel(question.difficulty) }}</span><span v-if="question.frequency">{{ question.frequency }}</span><button :disabled="favoriteBusy" :aria-pressed="Boolean(question.favorite)" @click="favorite">{{ question.favorite ? '★ 已收藏' : '☆ 收藏' }}</button></div>
                <p v-if="session.matching?.notice" class="muted">{{ session.matching.notice }}</p>
                <h2>{{ question.title }}</h2>
                <p v-if="question.source?.url" class="muted"><a :href="question.source.url" target="_blank" rel="noopener noreferrer">{{ question.subtype === 'CULTURE' ? '企业公开资料依据' : '题目来源' }}</a> · {{ question.source.license === 'OfferPilot-original' ? '原创题目' : question.source.license }}<template v-if="question.source.author"> · {{ question.source.author }}</template><br />{{ question.source.adaptation }}</p>
                <p class="pre-wrap">{{ question.description || question.content }}</p>
                <template v-if="question.background"><h3>背景材料</h3><p class="pre-wrap">{{ question.background }}</p></template>
                <ol v-if="question.subQuestions?.length"><li v-for="item in question.subQuestions" :key="item">{{ item }}</li></ol>
                <template v-if="question.constraints?.length"><h3>{{ isCoding ? '要求与约束' : isObjective ? '作答说明' : '回答要求' }}</h3><ul><li v-for="item in question.constraints" :key="item">{{ item }}</li></ul></template>
                <pre v-if="isCoding && question.signature"><code>{{ question.signature }}</code></pre>
                <article v-for="(example,n) in question.examples || []" :key="n" class="example"><h3>示例 {{ n + 1 }}</h3><p>输入</p><pre>{{ example.input }}</pre><p>输出</p><pre>{{ example.expected }}</pre><p v-if="example.explanation">{{ example.explanation }}</p></article>
                <div v-if="question.tags?.length" class="tags"><span v-for="tag in question.tags" :key="tag">{{ tag }}</span></div>
                <details v-if="question.hints?.length" @toggle="current.hintViewed = true; persist()"><summary>查看提示</summary><p v-for="hint in question.hints" :key="hint">{{ hint }}</p></details>
              </template>
              <template v-else-if="leftTab === '解析'">
                <h2>参考答案与解析</h2>
                <p v-if="referenceAnswer" class="pre-wrap">{{ referenceAnswer }}</p>
                <p v-else>{{ !feedbackAvailable ? '练习结束后统一展示正确答案与解析。' : hasSubmission ? '当前题目暂无可用解析。' : '提交答案后，可查看题目提供的参考答案。' }}</p>
                <p v-if="!isCoding && hasSubmission && feedbackAvailable" class="muted">{{ isObjective ? '本题按标准答案自动判分；多选题少选、多选均判错。' : question.gradingMode === 'SELF_REVIEW' ? '本题采用参考分析与自评要点，不提供自动分数或通过结论。' : '当前知识题反馈依据关键词覆盖生成，可结合参考答案自行检查完整性。' }}</p>
              </template>
              <template v-else>
                <h2>我的提交</h2><p v-if="historyError" role="alert">{{ historyError }} <button @click="loadHistory">重新加载</button></p><p v-else-if="!questionSubmissions.length">本题暂无正式提交记录，运行代码不计入提交。</p>
                <article v-for="item in questionSubmissions" :key="item.id" class="submission"><strong>{{ feedbackAvailable ? judgeStatusLabel(item.status) : '已提交，练习结束后统一结算' }}</strong><span>{{ formatDate(item.createdAt) }}</span><p v-if="feedbackAvailable && item.score != null">关键词覆盖得分：{{ item.score }}</p><pre v-if="item.answerText || item.sourceCode">{{ item.answerText || item.sourceCode }}</pre></article>
                <button v-if="historyTotal > historyItems.length" :disabled="historyLoading" @click="loadHistory(true)">加载更多提交</button>
              </template>
            </div>
          </section>

          <div class="splitter" role="separator" aria-label="调整题目宽度" aria-orientation="vertical" tabindex="0" :aria-valuenow="Math.round(split)" :aria-valuemin="Math.ceil(splitMin)" :aria-valuemax="Math.floor(splitMax)" @pointerdown="startResize($event,'split')" @keydown="resizeKey($event,'split')" @dblclick="split = 45; persist()"></div>

          <section class="answer-pane" :class="{ 'mobile-hidden': mobileTab === '题目', 'mobile-result': mobileTab === '结果' }" :style="{ '--result-height': `${resultHeight}%` }">
            <header class="answer-toolbar">
              <strong>{{ isCoding ? languageLabel : isObjective ? practiceTypeLabel(question.type) : '文字回答' }}</strong>
              <span class="save-status" role="status">{{ saveLabel }}</span>
              <button v-if="isCoding && !readOnly" @click="resetCode">重置代码</button>
              <button class="reset-layout" @click="split = 45; persist()">恢复比例</button>
            </header>
            <div class="answer-editor">
              <MonacoCodeEditor v-if="isCoding" :key="question.id" ref="codeEditor" v-model="current.code" :initial-state="current.editorState" :language="question.language || 'java'" :read-only="readOnly" />
              <fieldset v-else-if="isObjective" class="objective-options" :disabled="readOnly || anyBusy">
                <legend>{{ question.type === 'multiple_choice' ? '选择全部正确选项' : question.type === 'true_false' ? '判断下列说法是否正确' : '选择一个正确答案' }}</legend>
                <p class="objective-prompt">{{ question.description || question.content }}</p>
                <label v-for="option in question.options || []" :key="option.key" :class="{ selected: current.answer?.split(',').includes(option.key) }">
                  <input :type="question.type === 'multiple_choice' ? 'checkbox' : 'radio'" :name="`answer-${question.id}`" :value="option.key" :checked="current.answer?.split(',').includes(option.key)" @change="current.answer = toggleObjectiveAnswer(current.answer, option.key, question.type === 'multiple_choice')" />
                  <span class="option-copy"><strong v-if="question.type !== 'true_false'">{{ option.key }}.</strong> {{ option.text }}</span>
                </label>
                <p class="muted">{{ !feedbackAvailable ? '答案已选好后点击提交，结束练习后统一查看结果。' : question.type === 'multiple_choice' ? '少选、多选均判错。' : '提交后显示正确答案与解析。' }}</p>
              </fieldset>
              <textarea v-else ref="answerInput" v-model="current.answer" :readonly="readOnly" aria-label="文字回答" maxlength="10000" placeholder="说明关键概念、原理和边界条件。支持分段、列表及文字代码片段。" @scroll="captureAnswer" @keyup="captureAnswer" @click="captureAnswer"></textarea>
            </div>
            <div v-if="resultOpen" class="result-resizer" role="separator" aria-label="调整结果面板高度" aria-orientation="horizontal" tabindex="0" :aria-valuemin="15" :aria-valuemax="50" :aria-valuenow="Math.round(resultHeight)" @pointerdown="startResize($event,'result')" @keydown="resizeKey($event,'result')"></div>
            <section class="result-panel" :class="{ expanded: resultOpen }">
              <button class="result-heading" :aria-expanded="resultOpen" @click="toggleResult"><strong>{{ isCoding ? '运行与提交结果' : '提交反馈' }}</strong><span role="status">{{ resultSummary }}</span><span>{{ resultOpen ? '收起 ▾' : '展开 ▴' }}</span></button>
              <div ref="resultScroll" class="result-content" @scroll="captureScroll">
                <p v-if="current.error" class="error" role="alert">{{ current.error }} <button v-if="!isCoding || judgeCapability.ready" :disabled="anyBusy" @click="execute(current.lastAction)">重试</button><button v-else @click="refreshCapability">检查判题服务</button></p>
                <p v-if="!current.result && !current.error">{{ isCoding ? '本题尚未运行或提交。运行代码只检查用例，提交答案才计入练习进度。' : '本题尚未提交。你的回答会作为本机草稿保留。' }}</p>
                <PracticeResult v-if="current.result" :result="current.result" :coding="isCoding" :deferred="!feedbackAvailable" :show-message="!current.error" @reveal="revealDiagnostic" />
                <button v-if="isCoding" @click="restoreResultSize">恢复结果区高度</button>
                <p v-if="isCoding && !judgeCapability.ready && !current.error && !['SYSTEM_ERROR','SERVICE_UNAVAILABLE'].includes(current.result?.status)" role="status">{{ judgeCapability.notice }} <button @click="refreshCapability">检查判题服务</button></p>
                <button v-if="current.taskId && !current.busy && pendingJudgeStatuses.has(current.result?.status)" @click="resumeTask(question.id,current.taskId)">刷新任务结果</button>
                <button v-if="hasSubmission && !isCoding && feedbackAvailable" @click="reviewQuestion">{{ question.reviewState === 'PENDING' ? '标记复习完成' : '加入待复习' }}</button>
              </div>
            </section>
          </section>
        </div>

        <footer class="practice-footer">
          <button @click="openDirectory">题目目录</button>
          <nav aria-label="切换题目"><button :disabled="index === 0" @click="go(index-1)">上一题</button><button v-if="index < questions.length-1" @click="go(index+1)">下一题</button><button v-else-if="!readOnly" :disabled="finishing" @click="finish">完成练习</button></nav>
          <div v-if="!readOnly" class="answer-actions"><button v-if="isCoding" :disabled="anyBusy" @click="execute('run')">{{ current.busy === 'run' ? '运行中…' : '运行代码' }}</button><button class="primary" :disabled="anyBusy || finishing" @click="execute('submit')">{{ current.busy === 'submit' ? '提交中…' : '提交答案' }}</button></div>
        </footer>

        <dialog ref="directory" class="directory" @click="closeDirectoryOnBackdrop" @close="directoryTrigger?.focus()">
          <header><h2>题目目录</h2><button aria-label="关闭题目目录" @click="directory.close()">×</button></header>
          <p>已提交 {{ completed }} / {{ questions.length }} 题</p>
          <button v-for="(item,n) in questions" :key="item.id" class="directory-item" :aria-current="n === index ? 'step' : undefined" @click="go(n); directory.close()"><span>{{ n+1 }}. {{ item.title }}</span><small>{{ item.subtype === 'CULTURE' ? '企业情境' : practiceTypeLabel(item.type) }} · {{ submissionState(item,drafts[item.id]) }}</small></button>
        </dialog>
      </template>
    </main>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import PracticeResult from '../components/learning/PracticeResult.vue'
import { judgeStatusLabel, pendingJudgeStatuses, restoreJudgeResult } from '../utils/practiceJudging'
import { getJudgeCapability, createJudgeTask, getJudgeTask, getSessionTasks, setPracticeReview, getQuestionSubmissions } from '../services/learningResources'
import MonacoCodeEditor from '../components/learning/MonacoCodeEditor.vue'
import { useUserStore } from '../store/user'
import { answerPracticeQuestion, finishTrainingSession, getTrainingSession, runJudgeCode, submitPracticeCode, togglePracticeFavorite } from '../services/learningResources'
import { readPracticeDraft, writePracticeDraft, submissionState } from '../utils/practiceDrafts'
import { isObjectiveType, practiceTypeLabel, toggleObjectiveAnswer } from '../utils/practiceTypes'

const router = useRouter(), route = useRoute(), user = useUserStore()
const sessionId = route.params.sessionId, ownerId = user.userId
const session = ref(null), loading = ref(true), loadError = ref(''), index = ref(0)
const drafts = reactive({}), leftTab = ref('题目'), mobileTab = ref('题目')
const split = ref(45), resultOpen = ref(false), resultHeight = ref(30), workspaceWidth = ref(1200)
const resultTouched = ref(false), autoOpened = ref(false), judgeCapability = ref({ready:false,notice:'判题服务尚未确认'})
const readOnly = computed(() => session.value?.status && session.value.status !== 'ONGOING')
const splitMin = computed(() => Math.max(35,360 / Math.max(1,workspaceWidth.value-12) * 100))
const splitMax = computed(() => Math.min(55,100-420 / Math.max(1,workspaceWidth.value-12) * 100))
function clampSplit(value) { return Math.max(splitMin.value,Math.min(splitMax.value,value)) }
const saveLabel = ref('尚未保存'), saveError = ref(''), finishing = ref(false), favoriteBusy = ref(false)
const page = ref(null), workspace = ref(null), questionScroll = ref(null), resultScroll = ref(null), answerInput = ref(null), codeEditor = ref(null), directory = ref(null)
const directoryTrigger = ref(null), pageHeight = ref(720)
let timer, resizeObserver, restoring = false, resizeCleanup, leaving = false, disposed = false
const questions = computed(() => session.value?.questionList || [])
const question = computed(() => questions.value[index.value] || {})
const current = computed(() => drafts[question.value.id] || {})
const isCoding = computed(() => question.value.type === 'coding')
const isObjective = computed(() => isObjectiveType(question.value.type))
const languageLabel = computed(() => judgeCapability.value.languages?.[question.value.language] || `${question.value.language || '代码'} · 运行环境未验证`)
const completed = computed(() => questions.value.filter(q => submissionState(q,drafts[q.id]) === '已提交').length)
const anyBusy = computed(() => Object.values(drafts).some(d => d.busy))
const hasSubmission = computed(() => submissionState(question.value,current.value) === '已提交')
const feedbackAvailable = computed(() => !session.value?.deferFeedback || session.value.status === 'FINISHED')
const referenceAnswer = computed(() => feedbackAvailable.value && hasSubmission.value ? current.value.referenceAnswer || question.value.referenceAnswer : '')
const historyItems=ref([]), historyTotal=ref(0), historyError=ref(''), historyLoading=ref(false)
let historyPage=1, historyRequest=0
const questionSubmissions = computed(() => historyItems.value)
async function loadHistory(more=false) {
  if(more && historyLoading.value) return
  const qid=question.value.id, sequence=++historyRequest
  if(!more) { historyPage=1;historyItems.value=[];historyTotal.value=0 }
  historyLoading.value=true;historyError.value=''
  try { const result=await getQuestionSubmissions(sessionId,qid,more?historyPage+1:1); if(disposed || sequence!==historyRequest || qid!==question.value.id) return; historyItems.value=more?[...historyItems.value,...result.items]:result.items;historyPage=result.page;historyTotal.value=result.total }
  catch { if(sequence===historyRequest) historyError.value='提交记录加载失败。' }
  finally { if(sequence===historyRequest) historyLoading.value=false }
}
watch([leftTab,() => question.value.id],()=>{historyRequest++;if(leftTab.value==='提交记录')loadHistory();else {historyItems.value=[];historyTotal.value=0}})
const resultSummary = computed(() => current.value.result ? !feedbackAvailable.value ? current.value.result.submitted ? '已提交，练习结束后统一结算' : '样例运行已完成，结束后查看结果' : `${current.value.result.submitted ? '已提交 · ' : ''}${judgeStatusLabel(current.value.result.status)}` : current.value.busy ? '请求处理中…' : current.value.error ? '操作失败，请重试' : '暂无结果')
function difficultyLabel(value) { return ({1:'基础',2:'中等',3:'困难'})[value] || value }
function formatDate(value) { return String(value || '').replace('T',' ').slice(0,19) }

async function loadSession() {
  loading.value = true; loadError.value = ''
  try {
    const loaded = await getTrainingSession(sessionId)
    let stored = {}
    try { stored = readPracticeDraft(localStorage,ownerId,sessionId) } catch { throw new Error('本机草稿无法读取。请检查浏览器存储后重新加载；已有草稿未被覆盖。') }
    session.value = loaded
    session.value.submissions ||= []
    split.value = Math.max(35,Math.min(55,Number(stored.split) || 45))
    resultHeight.value = Math.max(15,Math.min(50,Number(stored.resultHeight) || 30))
    resultOpen.value = stored.resultOpen === true; resultTouched.value = Boolean(stored.resultTouched); autoOpened.value = Boolean(stored.autoOpened)
    for (const q of questions.value) {
      const saved = stored.questions?.[q.id] || {}, latest = q.latestSubmission || {}
      drafts[q.id] = { answer: saved.answer ?? latest.answerText ?? '', code: saved.code ?? latest.sourceCode ?? q.starter ?? '', submitted: Boolean(latest.id || q.status === 'ANSWERED'), result: restoreJudgeResult(latest,saved.result), taskId:saved.taskId || null, pendingRequest:saved.pendingRequest || null, questionTop: saved.questionTop || 0, resultTop:saved.resultTop || 0, answerTop:saved.answerTop || 0, cursor:saved.cursor || 0, editorState:saved.editorState || null, hintViewed:Boolean(saved.hintViewed), referenceAnswer:saved.referenceAnswer || q.referenceAnswer || '', busy:'', error:'' }
    }
    index.value = Math.max(0, Math.min(questions.value.length-1, Number.isInteger(stored.index) ? stored.index : Number(session.value.currentIndex) || 0))
    const requestedIndex = questions.value.findIndex(q => String(q.id) === String(route.query.questionId))
    if (requestedIndex >= 0) index.value = requestedIndex
    refreshCapability()
    getSessionTasks(sessionId).then(tasks => { for(const task of tasks) { if(!drafts[task.questionId]) continue; applyTask(task.questionId,task); if(pendingJudgeStatuses.has(task.status)) resumeTask(task.questionId,task.id) } }).catch(() => { saveError.value = '任务状态未能加载，可重试加载题单；本机草稿保留。' })
    saveLabel.value = saveError.value ? '草稿未保存' : stored.questions ? '已保存到本机' : '本机草稿'
  } catch (error) { loadError.value = error.message || '题单加载失败，请重试。' }
  finally { loading.value = false; await nextTick(); measure(); restorePosition() }
}

function snapshot() {
  return { index:index.value, split:split.value, resultOpen:resultOpen.value, resultHeight:resultHeight.value, resultTouched:resultTouched.value, autoOpened:autoOpened.value,
    questions:Object.fromEntries(Object.entries(drafts).map(([id,d]) => [id,{ answer:d.answer,code:d.code,submitted:d.submitted,result:d.result,questionTop:d.questionTop,resultTop:d.resultTop,answerTop:d.answerTop,cursor:d.cursor,editorState:d.editorState,hintViewed:d.hintViewed,taskId:d.taskId,pendingRequest:d.pendingRequest,referenceAnswer:d.referenceAnswer }])) }
}
function persist() {
  clearTimeout(timer)
  try {
    if (user.userId !== ownerId) throw new Error('用户已切换')
    writePracticeDraft(localStorage,ownerId,sessionId,snapshot()); saveLabel.value = '已保存到本机'; saveError.value = ''; return true
  } catch { saveLabel.value = '草稿未保存'; saveError.value = '本机保存失败，内容仍在当前页面。请重试；离开或刷新可能丢失修改。'; return false }
}
watch(() => [current.value.answer,current.value.code], () => {
  if (restoring || loading.value) return
  saveLabel.value = '保存中…'; clearTimeout(timer); timer = setTimeout(persist,400)
})
function captureScroll() {
  if (restoring) return
  if (leftTab.value === '题目') current.value.questionTop = questionScroll.value?.scrollTop || 0
  current.value.resultTop = resultScroll.value?.scrollTop || 0
}
function captureAnswer() {
  if (!answerInput.value) return
  current.value.answerTop = answerInput.value.scrollTop; current.value.cursor = answerInput.value.selectionStart
}
function capturePosition() {
  captureScroll(); captureAnswer()
  if (codeEditor.value) current.value.editorState = codeEditor.value.getViewState()
}
async function restorePosition() {
  restoring = true; await nextTick()
  if (questionScroll.value) questionScroll.value.scrollTop = current.value.questionTop || 0
  if (resultScroll.value) resultScroll.value.scrollTop = current.value.resultTop || 0
  if (answerInput.value) { answerInput.value.scrollTop = current.value.answerTop || 0; answerInput.value.setSelectionRange(current.value.cursor || 0,current.value.cursor || 0) }
  restoring = false
}
async function go(nextIndex) {
  if (nextIndex < 0 || nextIndex >= questions.value.length || nextIndex === index.value) return
  capturePosition()
  if (!persist() && !confirm('草稿未保存，只能暂存在当前页面。仍要切题吗？')) return
  restoring = true; index.value = nextIndex; leftTab.value = '题目'; mobileTab.value = '题目'
  await restorePosition(); persist()
}
watch(mobileTab, async (_, previous) => {
  if (restoring) return
  if (previous === '作答') { captureAnswer(); if (codeEditor.value) current.value.editorState = codeEditor.value.getViewState() }
  await nextTick(); codeEditor.value?.layout(); restorePosition()
})
watch(leftTab, async (_, previous) => { if (restoring) return; if (previous === '题目') current.value.questionTop = questionScroll.value?.scrollTop || 0; await nextTick(); if (leftTab.value === '题目') restorePosition() })

async function refreshCapability() {
  try { judgeCapability.value = await getJudgeCapability() } catch { judgeCapability.value = {ready:false,notice:'判题服务状态加载失败，请重试。'} }
}
function toggleResult() { resultTouched.value = true; resultOpen.value = !resultOpen.value; persist() }
function restoreResultSize() { resultTouched.value = true; resultHeight.value = 30; resultOpen.value = true; persist() }
async function revealDiagnostic(location) { mobileTab.value = '作答'; await nextTick(); codeEditor.value?.reveal(location.line,location.column) }
watch([resultHeight,resultOpen,split], async () => { await nextTick(); codeEditor.value?.layout() })
function openFirstResult() { if(!resultTouched.value && !autoOpened.value) { resultOpen.value = true; resultHeight.value = 30; autoOpened.value = true } }
function applyTask(questionId,task) {
  const draft = drafts[questionId]; if(!draft || disposed) return
  if(draft.taskId && Number(draft.taskId) > Number(task.id)) return
  if(Number(draft.result?.taskId) === Number(task.id) && !pendingJudgeStatuses.has(draft.result.status) && pendingJudgeStatuses.has(task.status)) return
  draft.taskId = task.id; draft.result = {...task.result,status:task.status,taskId:task.id}
  if(task.result?.submitted) { draft.submitted=true; const q=questions.value.find(q=>String(q.id)===String(questionId)); if(q) q.status='ANSWERED' }
  if(!pendingJudgeStatuses.has(task.status)) { draft.pendingRequest=null; draft.busy=''; if(['SYSTEM_ERROR','SERVICE_UNAVAILABLE'].includes(task.status)) draft.error=task.result?.message || '判题服务异常，请重试。' }
  persist()
}
async function resumeTask(questionId,id) {
  const draft=drafts[questionId]; if(!draft || draft.polling) return
  draft.polling=true; draft.error=''; draft.busy='judge'
  try {
    for(let n=0;n<900 && !disposed;n++) {
      const task=await getJudgeTask(id); if(disposed) return
      applyTask(questionId,task)
      if(!pendingJudgeStatuses.has(task.status)) {
        if(task.result?.submitted) {
          const loaded=await getTrainingSession(sessionId); if(disposed) return
          session.value.submissions=loaded.submissions
          const updated=loaded.questionList.find(q=>String(q.id)===String(questionId)); const q=questions.value.find(q=>String(q.id)===String(questionId)); if(q && updated) Object.assign(q,updated)
        }
        return
      }
      await new Promise(resolve=>setTimeout(resolve,1000))
    }
    if(!disposed) draft.error='任务仍在处理，可刷新任务结果；不要重复提交。'
  } catch { if(!disposed) draft.error='任务状态暂时无法获取，可刷新任务结果；代码保留。' }
  finally { draft.polling=false; draft.busy='' }
}
async function execute(action = 'submit') {
  if (anyBusy.value || readOnly.value || user.userId !== ownerId) return
  const q=question.value, draft=current.value, submittedIndex=index.value, value=q.type === 'coding' ? draft.code : draft.answer
  if(!value?.trim()) { draft.error=q.type === 'coding' ? '请先编写代码。' : isObjectiveType(q.type) ? '请先选择答案。' : '请先填写回答。'; openFirstResult(); mobileTab.value = '结果'; return }
  capturePosition(); persist(); openFirstResult(); draft.busy=action; draft.error=''; draft.lastAction=action
  try {
    if(q.type === 'coding') {
      if(draft.pendingRequest && (draft.pendingRequest.sourceCode!==value || draft.pendingRequest.operation!==action)) throw new Error('上次创建任务结果尚未确认，请先用原代码重试，避免重复提交。')
      draft.pendingRequest ||= {requestKey:crypto.randomUUID(),operation:action,sourceCode:value,language:q.language || 'java'}
      persist()
      const task=await createJudgeTask(sessionId,q.id,draft.pendingRequest); if(disposed) return
      draft.taskId=task.id; applyTask(q.id,task)
      if(pendingJudgeStatuses.has(task.status)) await resumeTask(q.id,task.id)
    } else {
      const result=await answerPracticeQuestion(sessionId,q.id,value); if(disposed) return
      draft.result={...result,submitted:true}; draft.submitted=true; q.status='ANSWERED'
      if(isObjectiveType(q.type) && feedbackAvailable.value) q.reviewState = result.passed ? 'RESOLVED' : 'PENDING'
      if(result.referenceAnswer) draft.referenceAnswer=result.referenceAnswer
      session.value.submissions.unshift({id:result.submissionId,sessionQuestionId:q.id,status:result.status || 'NEEDS_REVIEW',score:result.score,createdAt:new Date().toISOString(),answerText:value})
    }
    persist()
    if(action === 'submit' && draft.result?.submitted && !disposed && question.value.id === q.id) {
      draft.busy=''
      if(submittedIndex === questions.value.length-1) {
        alert('最后一题已提交，请点击右上方“结束练习”按钮结束刷题并查看结算。')
      } else {
        await go(submittedIndex+1)
      }
    }
  } catch(e) { draft.error=e.message || '操作失败，请重试。' }
  finally { draft.busy='' }
}
async function reviewQuestion() {
  const q=question.value
  try { const result=await setPracticeReview(q.id,q.reviewState==='PENDING'?'RESOLVED':'PENDING'); q.reviewState=result.state } catch(e) { alert(e.message || '复习状态更新失败') }
}

function resetCode() { if (confirm('恢复初始代码模板？当前代码将被替换。')) current.value.code = question.value.starter || '' }
async function favorite() {
  if (favoriteBusy.value) return
  const q = question.value; favoriteBusy.value = true
  try { const result = await togglePracticeFavorite(q.id); q.favorite = result.favorite }
  catch (error) { alert(error.message || '收藏失败，请重试。') }
  finally { favoriteBusy.value = false }
}
async function finish() {
  if (finishing.value || readOnly.value) return
  capturePosition()
  if (!persist()) { alert('草稿保存失败，请先重试保存，再结束练习。'); return }
  if (!confirm(`已提交 ${completed.value} / ${questions.value.length} 题。未提交 ${questions.value.length-completed.value} 题，处理中 ${Object.values(drafts).filter(d=>d.busy).length} 题。结束后查看报告？未提交内容已保留在本机。`)) return
  finishing.value = true
  try { await finishTrainingSession(sessionId); leaving = true; router.push(`/learning/session/${sessionId}/report`) }
  catch (error) { alert(error.message || '结束练习失败，请重试。') }
  finally { finishing.value = false }
}
function openDirectory(event) { directoryTrigger.value = event.currentTarget; directory.value.showModal() }
function closeDirectoryOnBackdrop(event) { if (event.target === directory.value && event.clientX < directory.value.getBoundingClientRect().left) directory.value.close() }
function resizeKey(event, kind) {
  const target = kind === 'split' ? split : resultHeight
  const delta = ({ ArrowLeft:-1,ArrowRight:1,ArrowUp:1,ArrowDown:-1 })[event.key]
  if(kind === 'result') resultTouched.value=true
  if (event.key === 'Home') target.value = kind === 'split' ? 45 : 30
  else if (delta) target.value = kind === 'split' ? clampSplit(target.value+delta) : Math.max(15,Math.min(50,target.value+delta))
  else return
  event.preventDefault(); persist()
}
function startResize(event, kind) {
  event.preventDefault(); if(kind === 'result') resultTouched.value=true; resizeCleanup?.()
  const rect = kind === 'split' ? workspace.value.getBoundingClientRect() : workspace.value.querySelector('.answer-pane').getBoundingClientRect()
  const move = e => { if (kind === 'split') split.value = clampSplit((e.clientX-rect.left-6)/(rect.width-12)*100); else resultHeight.value = Math.max(15,Math.min(50,(rect.bottom-e.clientY)/rect.height*100)) }
  const stop = () => { window.removeEventListener('pointermove',move); window.removeEventListener('pointerup',stop); window.removeEventListener('pointercancel',stop); persist(); resizeCleanup = null }
  resizeCleanup = stop; window.addEventListener('pointermove',move); window.addEventListener('pointerup',stop); window.addEventListener('pointercancel',stop)
}
function measure() {
  if (page.value) pageHeight.value = Math.max(240,(window.visualViewport?.height || window.innerHeight)-page.value.getBoundingClientRect().top-8)
  if (workspace.value && window.innerWidth > 1050) { workspaceWidth.value = workspace.value.clientWidth; split.value = clampSplit(split.value) }
}
function beforeUnload(event) { if (!questions.value.length) return; capturePosition(); if (!persist() || anyBusy.value) { event.preventDefault(); event.returnValue = '' } }
onBeforeRouteLeave(() => {
  if (leaving || !questions.value.length) return true
  capturePosition(); const saved = persist()
  if (anyBusy.value && !confirm('有运行或提交正在进行，离开后请从提交记录检查结果。仍要离开吗？')) return false
  return saved || confirm('草稿保存失败，离开将丢失当前修改。仍要离开吗？')
})
onMounted(() => { loadSession(); window.addEventListener('resize',measure); window.visualViewport?.addEventListener('resize',measure); window.addEventListener('beforeunload',beforeUnload); resizeObserver = new ResizeObserver(measure); if (page.value) resizeObserver.observe(page.value) })
onBeforeUnmount(() => { disposed = true; capturePosition(); if (questions.value.length) persist(); clearTimeout(timer); resizeCleanup?.(); resizeObserver?.disconnect(); window.removeEventListener('resize',measure); window.visualViewport?.removeEventListener('resize',measure); window.removeEventListener('beforeunload',beforeUnload) })
</script>

<style scoped>
.objective-options{margin:0;border:0;padding:24px 28px;box-sizing:border-box;height:100%;overflow:auto;min-width:0}.objective-options legend{float:left;width:100%;margin-bottom:18px;font-weight:600}.objective-options label{clear:both;display:flex;align-items:flex-start;gap:12px;min-height:44px;padding:14px;margin:10px 0;border:1px solid #d9e3de;border-radius:8px;cursor:pointer;box-sizing:border-box}.objective-options label.selected{background:var(--surface-soft);border-color:#059669}.objective-options input{flex-shrink:0;margin:4px 0 0;accent-color:#087c55;width:18px;height:18px}.objective-options input:focus-visible{outline:2px solid #059669;outline-offset:3px}.objective-options .option-copy{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6;font-size:16px}.objective-options:disabled label{cursor:default}.objective-options:not(:disabled) label:hover{background:var(--surface-soft)}
.practice-page{width:min(1560px,calc(100% - 40px));margin:0 auto;display:flex;flex-direction:column;gap:12px;padding:12px 0 0;color:#20352c;box-sizing:border-box;min-height:0}
.practice-page button{min-height:44px;padding:8px 14px;border:1px solid #d9e3de;border-radius:8px;background:var(--surface-elevated);color:#334c40;font:inherit;font-size:14px;cursor:pointer}.practice-page button:hover:not(:disabled){background:var(--surface-soft)}.practice-page button:disabled{opacity:.45;cursor:not-allowed}.practice-page :is(button,textarea,[role=separator]):focus-visible{outline:2px solid #059669;outline-offset:2px}.practice-page .primary{background:#10b981;color:#fff;border-color:#10b981}.practice-page .primary:hover:not(:disabled){background:#059669}
.practice-header{display:flex;align-items:center;gap:14px;flex-shrink:0}.heading{min-width:0;flex:1}.heading h1{font-size:21px;line-height:1.3;margin:0}.heading p{font-size:13px;color:#52665c;margin:4px 0 0}.progress{display:flex;gap:12px;align-items:center;font-size:14px}.progress span{color:#52665c}.workspace{display:grid;flex:1;min-height:0}.question-pane,.answer-pane{min-width:0;min-height:0;border:1px solid #dce5e0;border-radius:12px;overflow:hidden;background:var(--surface-elevated);display:flex;flex-direction:column}.pane-tabs,.answer-toolbar{height:52px;min-height:52px;padding:4px 12px;display:flex;align-items:center;gap:8px;border-bottom:1px solid #e2e9e5;box-sizing:border-box}.pane-tabs button{border:0;border-radius:0;min-height:44px}.pane-tabs button[aria-pressed=true]{color:#087c55;box-shadow:inset 0 -2px #10b981;font-weight:700}.answer-toolbar strong{white-space:nowrap;font-size:14px}.save-status{font-size:12px;color:#52665c;margin-right:auto}.answer-toolbar button{font-size:12px;padding:6px 8px;white-space:nowrap}.question-content{flex:1;overflow:auto;min-height:0;padding:24px 28px;font-size:16px;line-height:1.7;overflow-wrap:anywhere}.question-content h2{font-size:22px;line-height:1.4;margin:18px 0}.question-content h3{font-size:16px;margin:24px 0 8px}.question-content p{margin:10px 0}.pre-wrap{white-space:pre-wrap}.question-meta{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:13px}.question-meta>span,.tags span{background:var(--surface-soft);padding:3px 8px;border-radius:5px}.question-meta button{margin-left:auto;border:0}.question-content ul,.question-content ol{padding-left:22px}.practice-page pre{white-space:pre;overflow:auto;max-width:100%;padding:12px;background:var(--surface-soft);border-radius:7px;font:14px/1.65 Consolas,monospace}.tags{display:flex;gap:8px;flex-wrap:wrap;font-size:13px;margin-top:20px}.question-content details{margin-top:24px}.question-content summary{min-height:44px;cursor:pointer;color:#087c55}.splitter{cursor:col-resize;position:relative;touch-action:none}.splitter:after{content:'';position:absolute;left:5px;top:44%;height:44px;width:2px;border-radius:2px;background:#b5c7bc}.splitter:hover:after{background:#059669}.answer-editor{flex:1;min-height:0;overflow:hidden}.answer-editor textarea{width:100%;height:100%;box-sizing:border-box;resize:none;border:0;background:var(--surface-elevated);padding:24px 28px;font-family:inherit;font-size:16px;line-height:1.8;color:#20352c;outline-offset:-3px}.answer-editor textarea::placeholder{color:#63786b}.result-panel{min-height:44px;flex:0 0 44px;display:flex;flex-direction:column;border-top:1px solid #e2e9e5;overflow:hidden}.result-panel.expanded{flex-basis:var(--result-height)}.result-heading{display:flex;align-items:center;gap:12px;flex:0 0 44px;width:100%;text-align:left;border:0!important;border-radius:0!important;font-size:12px!important}.result-heading strong{white-space:nowrap}.result-heading span:first-of-type{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.result-content{min-height:0;flex:1;overflow:auto;padding:12px 18px;font-size:14px;line-height:1.65}.result-content p{margin:6px 0}.result-resizer{height:8px;flex-shrink:0;background:var(--surface-soft);cursor:row-resize;touch-action:none}.result-resizer:hover{background:var(--surface-soft)}.test-case,.submission{padding:12px 0;border-bottom:1px solid #e2e9e5}.submission span{display:block;color:#52665c;font-size:13px}.error,.save-error{color:#a02d29}.save-error{background:var(--surface-soft);padding:4px 12px;font-size:13px;flex-shrink:0}.muted{color:#52665c;font-size:14px}.practice-footer{display:flex;align-items:center;gap:12px;padding-bottom:env(safe-area-inset-bottom);flex-shrink:0}.practice-footer nav{display:flex;gap:8px}.answer-actions{margin-left:auto;display:flex;gap:8px}.directory{position:fixed;inset:0 0 0 auto;margin:0;height:100dvh;max-height:100dvh;width:min(420px,100vw);max-width:100vw;border:0;border-left:1px solid #dce5e0;padding:24px;color:#20352c;box-sizing:border-box}.directory::backdrop{background:rgba(20,40,30,.28)}.directory header{display:flex;justify-content:space-between;align-items:center}.directory h2{font-size:22px}.directory-item{width:100%;text-align:left;display:grid;gap:5px;margin:8px 0}.directory-item[aria-current=step]{background:var(--surface-soft);border-color:#10b981}.directory-item small{color:#52665c}.page-state{margin:auto;text-align:center}.page-state button{margin:8px}.mobile-tabs{display:none}
.compact-height{overflow:auto}.compact-height .workspace{flex-shrink:0;min-height:180px}.compact-height .practice-footer{position:sticky;bottom:0;background:var(--surface-soft);padding-top:8px;z-index:2}
@media(max-width:1050px){.practice-page{width:calc(100% - 24px);gap:8px;padding-top:8px}.mobile-tabs{display:flex;gap:4px;flex-shrink:0}.mobile-tabs button{flex:1}.mobile-tabs button[aria-pressed=true]{background:var(--surface-soft);border-color:#10b981;color:#087c55}.workspace{display:flex!important}.question-pane,.answer-pane{width:100%}.mobile-hidden{display:none}.splitter{display:none}.answer-pane:not(.mobile-result) .result-panel,.answer-pane:not(.mobile-result) .result-resizer{display:none}.answer-pane.mobile-result .answer-toolbar,.answer-pane.mobile-result .answer-editor,.answer-pane.mobile-result .result-resizer{display:none}.answer-pane.mobile-result .result-panel{flex:1}.mobile-result .result-heading{pointer-events:none}.mobile-result .result-heading>span:last-child{display:none}.reset-layout{display:none}.progress{display:grid;gap:2px;font-size:13px}}
@media(max-width:600px){.practice-page{width:calc(100% - 20px)}.practice-header{gap:8px;flex-wrap:wrap}.heading h1{font-size:18px}.heading p{font-size:12px}.practice-header>button:last-child{padding:8px;font-size:12px}.progress{width:100%;display:flex;justify-content:space-between;order:4}.question-content{padding:16px 18px}.answer-editor textarea{padding:18px}.practice-footer{gap:6px;flex-wrap:wrap}.practice-footer>button{font-size:12px;padding:6px 8px}.practice-footer nav{gap:4px}.practice-footer nav button{padding:6px 8px;font-size:12px}.answer-actions{width:100%;justify-content:flex-end}.answer-actions .primary{min-width:120px}.answer-toolbar{padding:4px 10px}.answer-toolbar strong{font-size:13px}}
.objective-prompt{display:none}
@media(max-width:1050px){.objective-prompt{display:block;clear:both;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;margin:0 0 20px}}
</style>
