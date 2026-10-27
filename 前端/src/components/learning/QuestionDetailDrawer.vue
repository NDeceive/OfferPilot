<template>
  <Teleport to="body">
    <Transition name="report-drawer" appear>
      <aside v-if="row" ref="panel" class="question-drawer" role="dialog" :aria-labelledby="'report-question-title'" tabindex="-1" @keydown.esc.stop.prevent="$emit('close')">
        <header class="drawer-heading">
          <div><p>第 {{ String(row.index + 1).padStart(2, '0') }} 题 · {{ practiceTypeLabel(row.q.type) }}</p><h2 id="report-question-title">{{ row.q.title }}</h2></div>
          <button ref="closeButton" class="close-drawer" aria-label="关闭题目详情" @click="$emit('close')">×</button>
          <span class="outcome" :class="row.outcome.key">{{ row.outcome.label }}</span>
        </header>
        <div class="detail-tabs" role="tablist" aria-label="题目详情">
          <button v-for="(label, i) in tabs" :id="`report-detail-tab-${i}`" :key="label" role="tab" :aria-selected="tab === label" :aria-controls="`report-detail-panel-${i}`" :tabindex="tab === label ? 0 : -1" @click="tab = label" @keydown="switchTab($event, i)">{{ label }}</button>
        </div>
        <div :id="`report-detail-panel-${tabs.indexOf(tab)}`" class="drawer-content" role="tabpanel" :aria-labelledby="`report-detail-tab-${tabs.indexOf(tab)}`" tabindex="0">
          <template v-if="tab === '本次作答'">
            <section class="question-statement"><h3>题目内容</h3><p class="pre-wrap">{{ row.q.description || row.q.content || '题干暂未提供。' }}</p>
              <p v-if="row.q.background" class="pre-wrap">{{ row.q.background }}</p>
              <ol v-if="row.q.subQuestions?.length"><li v-for="item in row.q.subQuestions" :key="item">{{ item }}</li></ol>
              <ul v-if="row.q.constraints?.length"><li v-for="item in row.q.constraints" :key="item">{{ item }}</li></ul>
              <ul v-if="row.q.options?.length" class="readonly-options" aria-label="只读选项与作答对照"><li v-for="option in row.q.options" :key="option.key" :class="{ chosen: chosenKeys.includes(option.key), correct: correctKeys.includes(option.key), wrong: chosenKeys.includes(option.key) && correctKeys.length && !correctKeys.includes(option.key) }"><span class="choice-box" aria-hidden="true">{{ chosenKeys.includes(option.key) ? '✓' : '' }}</span><span>{{ option.key }}. {{ option.text }}<small v-if="correctKeys.includes(option.key)">正确选项{{ !chosenKeys.includes(option.key) ? ' · 未选' : '' }}</small><small v-else-if="chosenKeys.includes(option.key)">{{ correctKeys.length ? '我的选择 · 错选' : '我的选择' }}</small></span></li></ul>
              <div v-for="(example, i) in row.q.examples || []" :key="i" class="example"><h4>示例 {{ i + 1 }}</h4><pre>输入：{{ example.input }}
输出：{{ example.expected }}</pre></div>
            </section>
            <section><h3>{{ coding ? '我的提交代码' : '我的答案' }}</h3>
              <div v-if="coding && latest?.sourceCode" class="code-view"><MonacoCodeEditor ref="codeEditor" :model-value="latest.sourceCode" :language="row.q.language || 'java'" read-only /></div>
              <p v-else class="pre-wrap">{{ latest?.answerText || '本题尚未提交。' }}</p>
              <p v-if="latest?.createdAt" class="muted">提交于 {{ formatPracticeDate(latest.createdAt) }}</p>
            </section>
            <section><h3>参考答案</h3><p v-if="correctKeys.length">{{ correctKeys.join('、') }}</p><p class="pre-wrap">{{ row.q.referenceAnswer || latest?.result?.referenceAnswer || '题库暂未提供参考答案。' }}</p></section>
            <section><h3>判定反馈</h3><PracticeResult v-if="latest?.result" :key="latest.id" :result="latest.result" :coding="coding" @reveal="revealDiagnostic" /><p v-else>{{ latest ? '历史记录仅保留原提交状态，未提供完整判题明细。' : '尚无提交记录，因此没有判定结果。' }}</p>
              <p v-if="row.systemIssue" class="detail-error">最近一次判题请求异常；上次有效提交结果仍保留，不计为学生答错。</p>
            </section>
          </template>
          <template v-else-if="tab === '题目解析'">
            <section><h3>参考分析</h3><p class="pre-wrap">{{ row.q.referenceAnswer || latest?.result?.referenceAnswer || '题库暂未提供解析。可以查看题面和本次判题反馈，或重新练习。' }}</p></section>
            <section v-if="row.q.tags?.length"><h3>相关知识点</h3><p>{{ row.q.tags.join(' · ') }}</p></section>
            <section v-if="['self','self_done'].includes(row.outcome.key)"><h3>对照参考答案完成自评</h3><p>检查关键概念、推理过程与边界条件，再选择下方自评结果。</p></section>
            <a v-if="row.q.source?.url" :href="row.q.source.url" target="_blank" rel="noopener noreferrer">查看题目来源 ↗</a>
          </template>
          <template v-else>
            <p v-if="historyLoading && !history.length" role="status">正在加载提交历史…</p>
            <p v-if="historyError" class="detail-error" role="alert">{{ historyError }} <button @click="loadHistory(requestedPage)">重试</button></p>
            <p v-if="!historyLoading && !historyError && !history.length">本题还没有提交记录。</p>
            <article v-for="(item, i) in history" :key="item.id" class="history-entry"><header><strong>提交版本 {{ historyTotal - i }}</strong><span>{{ judgeStatusLabel(item.status) }}</span></header><p class="muted">{{ formatPracticeDate(item.createdAt) }}</p><div v-if="historyDiagnostic?.id === item.id && item.sourceCode" class="code-view"><MonacoCodeEditor ref="historyEditor" :model-value="item.sourceCode" :language="row.q.language || 'java'" :highlighted-lines="[historyDiagnostic.line, historyDiagnostic.line]" read-only /></div><pre v-else>{{ item.sourceCode || item.answerText || '该记录未提供原始作答。' }}</pre><PracticeResult v-if="item.result" :result="item.result" :coding="coding" @reveal="revealHistoryDiagnostic(item, $event)" /><p v-else class="muted">未提供完整判题明细。</p></article>
            <button v-if="history.length < historyTotal && !historyError" :disabled="historyLoading" @click="loadHistory(historyPage + 1)">{{ historyLoading ? '加载中…' : '加载更多提交' }}</button>
          </template>
        </div>
        <section v-if="canSelfAssess && latest?.status === 'SELF_REVIEW' && tab !== '提交历史'" class="self-assessment"><h3>自评结果</h3><p>自评不计入正确率。“需要巩固”会加入待复习。</p><div class="self-assessment-actions"><button :disabled="busy" :aria-pressed="row.q.selfAssessment?.state === 'MASTERED'" @click="$emit('self-assess', 'MASTERED')">已掌握</button><button :disabled="busy" :aria-pressed="row.q.selfAssessment?.state === 'NEEDS_REVIEW'" @click="$emit('self-assess', 'NEEDS_REVIEW')">需要巩固</button></div><p v-if="row.q.selfAssessment" role="status">自评已保存：{{ row.q.selfAssessment.state === 'MASTERED' ? '已掌握' : '需要巩固' }}。可重新选择。</p><p class="muted">结果保存在当前浏览器的样例练习中，刷新后仍保留。</p></section>
        <p v-if="actionError" class="detail-error action-message" role="alert">{{ actionError }}</p>
        <footer class="drawer-actions"><button :disabled="busy" @click="$emit('review', row.q)">{{ row.reviewState === 'PENDING' ? '标记复习完成' : '加入待复习' }}</button><button class="primary" :disabled="busy || !canRetry" title="新建原题练习并定位本题，历史报告保留" @click="$emit('retry', row.q)">{{ busy ? '处理中…' : '重新作答' }}</button></footer>
      </aside>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import PracticeResult from './PracticeResult.vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import { getQuestionSubmissions } from '../../services/learningResources'
import { practiceTypeLabel } from '../../utils/practiceTypes'
import { judgeStatusLabel } from '../../utils/practiceJudging'
import { formatPracticeDate } from '../../utils/practiceReport'
const props = defineProps({ row: Object, sessionId: [String, Number], busy: Boolean, canRetry: Boolean, canSelfAssess: Boolean, actionError: String, initialTab: { type: String, default: '本次作答' } })
defineEmits(['close', 'retry', 'review', 'self-assess'])
const tabs = ['本次作答', '题目解析', '提交历史'], tab = ref('本次作答'), panel = ref(null), closeButton = ref(null), codeEditor = ref(null), historyEditor = ref(null), historyDiagnostic = ref(null)
const latest = computed(() => props.row?.q.latestSubmission), coding = computed(() => props.row?.kind === 'coding')
const chosenKeys = computed(() => String(latest.value?.answerText || '').split(',').map(s => s.trim()))
const correctKeys = computed(() => String(latest.value?.result?.correctAnswer || '').split(',').map(s => s.trim()).filter(Boolean))
const history = ref([]), historyLoading = ref(false), historyError = ref(''), historyTotal = ref(0), historyPage = ref(0)
let sequence = 0, disposed = false, previousFocus, requestedPage = 1
watch(() => props.row?.q.id, async (id, old) => {
  sequence++; history.value = []; historyTotal.value = 0; historyPage.value = 0; historyError.value = ''; historyLoading.value = false; historyDiagnostic.value = null
  tab.value = props.initialTab
  if (id) { if (!old || !panel.value?.contains(document.activeElement)) previousFocus = document.activeElement; await nextTick(); closeButton.value?.focus() }
  else if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
}, { immediate: true })
watch(tab, value => { if (value === '提交历史' && props.row && !historyPage.value) loadHistory(1) })
watch(() => props.initialTab, value => { tab.value = value })
async function loadHistory(page) {
  const id = ++sequence, questionId = props.row.q.id
  requestedPage = page
  historyLoading.value = true; historyError.value = ''
  try {
    const result = await getQuestionSubmissions(props.sessionId, questionId, page)
    if (disposed || id !== sequence || props.row?.q.id !== questionId) return
    history.value = page === 1 ? result.items : [...history.value, ...result.items]
    historyTotal.value = result.total; historyPage.value = result.page
  } catch (error) { if (!disposed && id === sequence) historyError.value = error.message || '提交历史加载失败，请重试。' }
  finally { if (id === sequence) historyLoading.value = false }
}
async function revealDiagnostic(location) { tab.value = '本次作答'; await nextTick(); codeEditor.value?.reveal(location.line, location.column) }
async function revealHistoryDiagnostic(item, location) { historyDiagnostic.value = { id: item.id, ...location }; await nextTick(); historyEditor.value?.[0]?.reveal(location.line, location.column) }
function switchTab(event, index) {
  const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
  if (!step && !['Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + step + tabs.length) % tabs.length
  tab.value = tabs[next]; panel.value?.querySelectorAll('[role=tab]')[next]?.focus()
}
onBeforeUnmount(() => { disposed = true; sequence++; if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true }) })
</script>

<style scoped>
.question-drawer{position:fixed;top:var(--nav-height,64px);right:0;bottom:0;z-index:90;width:min(520px,100vw);display:flex;flex-direction:column;background:var(--surface-elevated,#fff);border-left:1px solid #e3eae7;box-shadow:-12px 12px 32px rgba(20,40,30,.06);padding:24px 28px;color:#172d28;font-size:14px;box-sizing:border-box}
.drawer-heading{display:grid;grid-template-columns:minmax(0,1fr) 44px;gap:4px 12px;align-items:start}.drawer-heading p{margin:0 0 8px;color:#62716c;font-size:13px}.drawer-heading h2{font-size:24px;line-height:1.4;margin:0;overflow-wrap:anywhere}.close-drawer{font-size:28px!important;border:0!important;min-width:44px;color:#62716c!important;padding:0!important}.outcome{grid-column:1/-1;justify-self:start;font-size:12px;font-weight:600;border-radius:5px;padding:4px 8px;margin-top:8px;background:#f0f3f2;color:#53625c}.outcome.passed{background:var(--accent-50);color:var(--accent-700)}.outcome.failed,.outcome.system{background:#fff0f1;color:#b43b49}.outcome.self{background:#fff7e9;color:#825819}
.detail-tabs{display:flex;margin-top:20px;border-block:1px solid #e6ece8;flex-shrink:0}.question-drawer .detail-tabs button{flex:1;border:0;border-bottom:2px solid transparent;border-radius:0;font-size:15px;color:#62716c;padding:12px 0}.question-drawer .detail-tabs button[aria-selected=true]{border-bottom-color:var(--accent-500);color:var(--accent-700);font-weight:600}
.drawer-content{flex:1;overflow:auto;min-height:0;padding:20px 0;overscroll-behavior:contain;scrollbar-gutter:stable}.drawer-content section{padding:0 0 20px;margin:0 0 20px;border-bottom:1px solid #e8eeeb}.drawer-content section:last-child{border:0;margin:0}.drawer-content h3{font-size:16px;margin:0 0 10px}.drawer-content h4{font-size:14px;margin:12px 0 6px}.drawer-content p{margin:8px 0;line-height:1.8;overflow-wrap:anywhere}.pre-wrap{white-space:pre-wrap}.muted{color:#62716c;font-size:13px}.question-statement{background:#f8faf9;padding:18px!important;border:1px solid #e8eeeb!important;border-radius:10px}.drawer-content ul,.drawer-content ol{padding-left:20px;line-height:1.8}.readonly-options{list-style:none;padding:0!important;margin:16px 0 0}.readonly-options li{display:flex;align-items:flex-start;gap:10px;margin:10px 0;color:#52615b}.choice-box{width:18px;height:18px;border:1px solid #8e9e97;border-radius:4px;flex-shrink:0;display:grid;place-items:center;line-height:1;margin-top:3px}.readonly-options .chosen .choice-box{background:#e3f5ed;border-color:var(--accent-600);color:var(--accent-700)}.readonly-options .correct{color:var(--accent-800)}.readonly-options .wrong{color:#b43b49}.readonly-options .wrong .choice-box{border-color:#b43b49;background:#fff0f1;color:#b43b49}.readonly-options small{display:block;font-size:12px}
.code-view{height:260px;border-radius:8px;overflow:hidden}.drawer-content pre{max-width:100%;overflow:auto;white-space:pre;font:13px/1.75 var(--font-mono,Consolas,monospace);padding:12px;background:#f4f7f5;border-radius:6px}.history-entry{padding:0 0 20px;margin-bottom:20px;border-bottom:1px solid #e8eeeb}.history-entry header{display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px}.history-entry header span{font-size:12px;color:#62716c}.detail-error{background:#fff0f1;color:#a42f3d;padding:10px 12px;border-radius:6px;line-height:1.7}.action-message{margin:0 0 10px;flex-shrink:0}.drawer-actions{display:grid;grid-template-columns:1fr 1fr;gap:14px;padding-top:16px;border-top:1px solid #e8eeeb;flex-shrink:0}
.question-drawer button{background:white;color:#233b31;border:1px solid #dce5e0;border-radius:8px;min-height:44px;padding:8px 12px;font:inherit;cursor:pointer;transition:background 150ms}.question-drawer button:hover{background:#f0f8f4}.question-drawer .primary{background:var(--accent-500);border-color:var(--accent-500);color:#fff;font-weight:600}.question-drawer .primary:hover{background:var(--accent-600)}.question-drawer button:disabled{opacity:.5;cursor:not-allowed}.question-drawer a{color:var(--accent-700);display:inline-flex;align-items:center;min-height:44px;text-decoration:none}.question-drawer :is(button,a,[tabindex]):focus-visible{outline:2px solid var(--accent-600);outline-offset:2px}
.report-drawer-enter-active,.report-drawer-leave-active{transition:transform 200ms cubic-bezier(.16,1,.3,1)}.report-drawer-enter-from,.report-drawer-leave-to{transform:translateX(100%)}
.self-assessment-actions{display:flex;gap:12px;flex-wrap:wrap}.self-assessment-actions button{flex:1}.question-drawer .self-assessment-actions button[aria-pressed=true]{background:var(--accent-50);border-color:var(--accent-600);color:var(--accent-800)}
.self-assessment{flex-shrink:0;padding:12px 0;border-top:1px solid #e8eeeb}.self-assessment h3{font-size:15px;margin:0 0 6px}.self-assessment p{font-size:12px;line-height:1.7;margin:6px 0;color:#52615b}
@media(max-width:767px){.question-drawer{padding:18px 20px;bottom:calc(68px + env(safe-area-inset-bottom));width:100%;top:var(--nav-height,64px)}.drawer-heading h2{font-size:20px}.detail-tabs{margin-top:14px}.drawer-content{padding:16px 0}}
@media(prefers-reduced-motion:reduce){.report-drawer-enter-active,.report-drawer-leave-active,.question-drawer button{transition:none}}
</style>
