<template>
  <component :is="isMobile ? MobileShell : AppLayout" title="练习报告" class="report-shell">
    <main class="practice-report">
      <router-link class="return-link" to="/learning/library/recent">← 返回练习记录</router-link>
      <div class="page-heading"><h1 v-if="!isMobile">练习报告</h1><span v-if="report?.finishedAt">完成于 {{ formatPracticeDate(report.finishedAt) }}</span></div>
      <div v-if="loading" class="report-state" role="status"><h2>正在加载练习报告…</h2><p>正在整理本次作答与判题记录。</p></div>
      <div v-else-if="error" class="report-state" role="alert"><h2>报告暂时无法加载</h2><p>{{ error }}</p><button @click="load">重新加载</button></div>
      <template v-else-if="report">
        <p v-if="report.demoMode" class="notice">样例演示：编程题采用本地粗略检查，结算结果仅用于功能展示。</p>
        <p v-if="report.settlementStatus === 'PROCESSING'" class="notice" role="status">结算处理中，还有 {{ report.pendingCount || '部分' }} 个任务未结束；当前统计不是最终结果。<button class="text-button" @click="load">刷新状态</button></p>
        <p v-if="systemFailures" class="system-notice" role="alert">{{ systemFailures }} 个判题请求异常，不计为学生答错。已保留可用作答，可在题目详情中核实并重新练习。</p>
        <p v-if="report.status === 'ONGOING'" class="notice">练习仍在进行，当前为阶段记录。<router-link :to="`/learning/session/${sessionId}`">继续练习 →</router-link></p>
        <section class="report-overview" aria-label="练习结算">
          <div class="overview-info">
            <span class="session-state">{{ report.status === 'FINISHED' ? '本次练习已完成' : report.status === 'ONGOING' ? '本次练习进行中' : '本次练习正在结算' }}</span>
            <h2>{{ report.role?.name || '历史岗位' }} · {{ report.topic || '专项练习' }}</h2>
            <p class="muted">{{ report.company || '不限公司' }} · {{ practiceTypeLabel(report.questionType) }}</p>
            <p class="submission-summary">{{ metrics.submitted }} / {{ metrics.total }} 题已提交<span v-if="elapsed">｜用时 {{ elapsed }}</span></p>
            <div class="overview-actions"><button class="primary" :disabled="busy || !canRetry || !retryCount" title="复练未通过或历史建议复习且尚未完成复习的题目，以及加入待复习的题目" @click="retry(true)">重练未通过题（{{ retryCount }} 道）</button><button :disabled="busy || !canRetry" @click="retry(false)">按原题再练一次</button></div>
            <p v-if="legacyRetryCount" class="muted legacy-retry-note">包含 {{ legacyRetryCount }} 道历史关键词建议复习题，不计入正确率。</p>
          </div>
          <div class="overview-score">
            <p class="muted">{{ feedbackAvailable ? '本次判定正确率' : '练习结束后统一结算' }}</p>
            <strong class="score">{{ feedbackAvailable ? metrics.score ?? '—' : '—' }}<small v-if="feedbackAvailable && metrics.score != null">%</small></strong>
            <div class="progress-track" :role="metrics.score != null && feedbackAvailable ? 'progressbar' : undefined" aria-label="本次判定正确率" :aria-valuenow="feedbackAvailable ? metrics.score : undefined" :aria-valuemin="0" :aria-valuemax="100"><span :style="{ width: `${feedbackAvailable ? metrics.score || 0 : 0}%` }"></span></div>
            <b v-if="feedbackAvailable">{{ metrics.correct }} / {{ metrics.evaluated }} 道题判定正确</b>
            <p v-if="feedbackAvailable" class="muted">{{ metrics.selfReview ? `另有 ${metrics.selfReview} 道普通题待自评（不计入正确率）` : '仅统计客观题与编程题的有效自动判定' }}</p><p v-else class="muted">作答期间不显示判定与解析。</p>
          </div>
        </section>
        <template v-if="feedbackAvailable">
          <section class="type-statistics" aria-label="题型表现">
            <article><h3>客观题</h3><strong>{{ metrics.objectiveCorrect }} / {{ metrics.objectiveTotal }}</strong><div class="progress-track" aria-hidden="true"><span :style="{ width: `${ratio(metrics.objectiveCorrect, metrics.objectiveTotal)}%` }"></span></div><p>正确 {{ metrics.objectiveCorrect }} 题 · 错误 {{ metrics.objectiveIncorrect }} 题<template v-if="objectiveOther"> · 未判定 {{ objectiveOther }} 题</template></p><button class="text-button" @click="drill('objective', 'failed')">查看错误题目 →</button></article>
            <article><h3>编程题</h3><strong>{{ metrics.codingPassed }} / {{ metrics.codingTotal }}</strong><div class="progress-track" aria-hidden="true"><span :style="{ width: `${ratio(metrics.codingPassed, metrics.codingTotal)}%` }"></span></div><p>通过 {{ metrics.codingPassed }} 题 · 未通过 {{ metrics.codingFailed }} 题<template v-if="codingOther"> · 未判定 {{ codingOther }} 题</template></p><button class="text-button" @click="drill('coding', 'failed')">查看未通过题 →</button></article>
            <article><h3>普通题</h3><strong>{{ metrics.knowledgeTotal }} 道</strong><div class="self-review-note">对照参考答案检查思路与关键概念</div><p>待自评 {{ metrics.selfReview }} 题 · 已自评 {{ metrics.selfReviewed }} 题 · 不计入正确率</p><button class="text-button" @click="selfReview">{{ metrics.selfReview ? '前往完成自评 →' : '查看自评 →' }}</button></article>
          </section>
          <section ref="reviewSection" class="review-section" aria-labelledby="review-title">
            <header class="review-heading"><div><h2 id="review-title">逐题复盘</h2><p>回顾本次作答，针对薄弱题目进行巩固训练</p></div><router-link to="/learning">创建新的专项题单 →</router-link></header>
            <nav class="result-filters" aria-label="作答结果筛选"><button v-for="filter in resultFilters" :key="filter.key" :aria-pressed="filters.result === filter.key" @click="filters.result = filter.key">{{ filter.label }} <span>{{ filter.count }}</span></button></nav>
            <div class="filter-toolbar">
              <label class="search-field"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg><input v-model="filters.search" type="search" aria-label="搜索题目名称或关键词" placeholder="搜索题目名称或关键词…" /></label>
              <select v-model="filters.kind" aria-label="题型筛选"><option value="all">全部题型</option><option value="objective">客观题</option><option value="coding">编程题</option><option value="knowledge">普通题</option></select>
              <select v-model="filters.review" aria-label="复习状态筛选"><option value="all">全部复习状态</option><option value="PENDING">待复习</option><option value="RESOLVED">已复习</option><option value="NONE">未加入复习</option></select>
              <select v-model="filters.sort" aria-label="题目排序"><option value="original">按原题顺序</option><option value="failed">未通过优先</option></select><span class="filtered-count" role="status">共 {{ filteredRows.length }} 道题</span>
            </div>
            <p v-if="actionError" class="system-notice" role="alert">{{ actionError }}</p><p v-if="actionNotice" class="action-notice" role="status">{{ actionNotice }}</p>
            <div class="question-list">
              <article v-for="row in filteredRows" :key="row.q.id" :data-question-id="row.q.id" class="report-question" :class="{ selected: selectedId === row.q.id }">
                <span class="question-number">{{ String(row.index + 1).padStart(2, '0') }}</span>
                <div class="question-info"><h3>{{ row.q.title }}</h3><div class="question-meta"><span>{{ practiceTypeLabel(row.q.type) }}</span><span class="outcome" :class="row.outcome.key">{{ row.outcome.label }}</span><span v-if="row.reviewState === 'PENDING' || row.reviewState === 'RESOLVED'" class="review-mark">{{ row.reviewState === 'PENDING' ? '待复习' : '已标记复习完成' }}</span><span v-if="row.systemIssue" class="outcome system">最近判题异常</span></div></div>
                <div class="row-actions"><button class="text-button view-answer" @click="openQuestion(row)">查看作答与解析 →</button><button v-if="report.demoMode && ['self','self_done'].includes(row.outcome.key)" class="text-button" :disabled="busy" @click="openQuestion(row)">{{ row.q.selfAssessment ? '修改自评' : '开始自评' }}</button><button :disabled="busy || !canRetry" title="新建原题练习并定位本题，历史报告保留" @click="retry(false, row.q)">重新作答</button><button :disabled="busy" :aria-pressed="row.favorite" @click="favorite(row.q)">{{ row.favorite ? '★ 已收藏' : '☆ 收藏' }}</button><button :disabled="busy" @click="review(row.q)">{{ row.reviewState === 'PENDING' ? '标记复习完成' : row.reviewState === 'RESOLVED' ? '重新加入待复习' : '加入待复习' }}</button></div>
              </article>
              <div v-if="!filteredRows.length" class="report-state"><h3>{{ rows.length ? '没有符合筛选条件的题目' : '本次练习暂无题目记录' }}</h3><p>{{ rows.length ? '可以调整关键词、题型或复习状态，查看其他题目。' : '返回学习资源创建新的专项题单。' }}</p><button v-if="rows.length" @click="resetFilters">清除筛选</button><router-link v-else to="/learning">创建专项题单 →</router-link></div>
            </div>
          </section>
          <details class="statistics"><summary>报告统计说明</summary><div><p>提交率 {{ metrics.total ? `${ratio(metrics.submitted,metrics.total)}%` : '—' }} · 未提交 {{ Math.max(0,metrics.total-metrics.submitted) }} 题 · 历史关键词评价 {{ metrics.legacyReview }} 题</p><p>判题中 {{ report.pendingCount || 0 }} 个请求 · 系统判题异常 {{ systemFailures }} 个请求</p><p>正确率 = 判定正确的客观题与编程题 ÷ 这两类题目的有效判定数。未提交、判题中、系统异常、普通题自评及历史关键词评价均不计入分母。题型卡片展示全部题目数量；“未判定”包含未提交、判题中与判题异常。</p><p>复习状态与历史判题结果独立。重练范围沿用现有规则：未通过且尚未完成复习的题目，以及所有待复习题。重新作答会新建原题练习并定位所选题目，不覆盖本报告。</p><p>开始于 {{ formatPracticeDate(report.startedAt) }} · {{ elapsed ? `总历时 ${elapsed}，包含离开页面的时间。` : '练习结束后显示总历时。' }} 所有时间按北京时间显示。</p><p>普通题通过对照参考答案自评；当前接口不记录自评分数。{{ report.demoMode ? '编程结果为本地文本规则粗判，未执行真实编译或测试用例。' : '' }}</p></div></details>
        </template>
        <p v-else class="notice">本次练习采用统一结算。请先完成或结束练习，再查看判定与解析。</p>
      </template>
      <div v-else class="report-state"><h2>暂无练习报告</h2><router-link to="/learning">返回学习资源 →</router-link></div>
    </main>
    <QuestionDetailDrawer :row="selectedRow" :session-id="sessionId" :initial-tab="drawerTab" :busy="busy" :can-retry="canRetry" :can-self-assess="report?.demoMode && report?.status === 'FINISHED'" :action-error="actionError" @close="selectedId = null" @review="review" @self-assess="assess" @retry="retry(false, $event)" />
  </component>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import MobileShell from '../mobile/components/MobileShell.vue'
import { useIsMobile } from '../mobile/composables/useIsMobile'
import '../mobile/styles/mobile.css'
import QuestionDetailDrawer from '../components/learning/QuestionDetailDrawer.vue'
import { getPracticeReport, getTrainingSession, retryPractice, setPracticeReview, setPracticeSelfAssessment, togglePracticeFavorite } from '../services/learningResources'
import { practiceTypeLabel } from '../utils/practiceTypes'
import { canRetryReportQuestion, filterReportQuestions, formatPracticeDate, practiceElapsed, reportMetrics, reportQuestionKind, reportQuestionOutcome } from '../utils/practiceReport'
const route = useRoute(), router = useRouter(), isMobile = useIsMobile(), sessionId = route.params.sessionId
const report = ref(null), loading = ref(true), error = ref(''), busy = ref(false), actionError = ref(''), actionNotice = ref(''), reviews = ref({}), favorites = ref({})
const selectedId = ref(null), drawerTab = ref('本次作答'), reviewSection = ref(null)
const filters = reactive({ result: 'all', search: '', kind: 'all', review: 'all', sort: 'original' })
const metrics = computed(() => reportMetrics(report.value || {}))
const feedbackAvailable = computed(() => !report.value?.deferFeedback || report.value.status === 'FINISHED')
const canRetry = computed(() => report.value?.settlementStatus === 'READY' && report.value?.status === 'FINISHED')
const elapsed = computed(() => practiceElapsed(report.value?.startedAt, report.value?.finishedAt))
const ratio = (n, d) => d ? Math.round(n / d * 100) : 0
const systemFailures = computed(() => Math.max(report.value?.systemFailureCount || 0, rows.value.filter(row => row.outcome.key === 'system' || row.systemIssue).length))
const rows = computed(() => (report.value?.questionList || []).map((q, index) => ({ q, index, kind: reportQuestionKind(q), outcome: reportQuestionOutcome(q), reviewState: reviews.value[q.id] ?? q.reviewState, favorite: favorites.value[q.id] ?? q.favorite,
  systemIssue: (report.value?.tasks || []).some(task => String(task.questionId) === String(q.id) && task.operation === 'submit' && ['SYSTEM_ERROR', 'SERVICE_UNAVAILABLE', 'Internal Error', 'Exec Format Error'].includes(task.status)) })))
const filteredRows = computed(() => filterReportQuestions(rows.value, filters))
const selectedRow = computed(() => feedbackAvailable.value ? rows.value.find(row => row.q.id === selectedId.value) || null : null)
const retryCount = computed(() => rows.value.filter(row => canRetryReportQuestion(row.q, row.reviewState)).length)
const legacyRetryCount = computed(() => rows.value.filter(row => row.kind === 'knowledge' && row.outcome.key === 'unknown' && canRetryReportQuestion(row.q, row.reviewState)).length)
const objectiveOther = computed(() => Math.max(0, metrics.value.objectiveTotal - metrics.value.objectiveCorrect - metrics.value.objectiveIncorrect))
const codingOther = computed(() => Math.max(0, metrics.value.codingTotal - metrics.value.codingPassed - metrics.value.codingFailed))
const resultFilters = computed(() => [{ key: 'all', label: '全部题目', count: rows.value.length }, ...[{ key: 'failed', label: '未通过' }, { key: 'passed', label: '已通过' }, { key: 'self', label: '待自评' }].map(filter => ({ ...filter, count: rows.value.filter(row => row.outcome.key === filter.key).length }))])
let timer, disposed = false, sequence = 0
async function load() {
  const id = ++sequence; clearTimeout(timer); if (!report.value) loading.value = true; error.value = ''
  try { const result = await getPracticeReport(sessionId); if (disposed || id !== sequence) return; report.value = result; if (result.settlementStatus === 'PROCESSING') timer = setTimeout(load, 2000) }
  catch (e) { if (!disposed && id === sequence) error.value = e.message || '报告加载失败，请重试。' }
  finally { if (id === sequence) loading.value = false }
}
load()
function openQuestion(row, tab = '本次作答') { drawerTab.value = tab; selectedId.value = row.q.id }
async function drill(kind, result) { filters.kind = kind; filters.result = result; await nextTick(); reviewSection.value?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' }) }
async function selfReview() { await drill('knowledge', metrics.value.selfReview ? 'self' : 'all'); const row = filteredRows.value.find(row => row.q.latestSubmission?.status === 'SELF_REVIEW'); if (row) openQuestion(row, '题目解析') }
async function assess(state) {
  const q = selectedRow.value?.q
  if (busy.value || !q || !report.value.demoMode) return
  busy.value = true; actionError.value = ''; actionNotice.value = ''
  try {
    const result = await setPracticeSelfAssessment(sessionId, q.id, state)
    if (disposed) return
    q.selfAssessment = result.selfAssessment; reviews.value[q.id] = result.reviewState
    report.value.statistics.selfReview = report.value.questionList.filter(q => q.latestSubmission?.status === 'SELF_REVIEW' && !q.selfAssessment).length
    actionNotice.value = `${q.title}：自评已保存，不计入自动判定正确率。`
  } catch (e) { if (!disposed) actionError.value = e.message || '自评保存失败，请重试。' }
  finally { busy.value = false }
}
function resetFilters() { Object.assign(filters, { result: 'all', search: '', kind: 'all', review: 'all', sort: 'original' }) }
async function retry(failedOnly, q) {
  if (busy.value || !canRetry.value) return
  busy.value = true; actionError.value = ''; actionNotice.value = ''
  try { const result = await retryPractice(sessionId, failedOnly); let questionId
    if (q) { const created = result.questionList ? result : await getTrainingSession(result.id); questionId = created.questionList?.[rows.value.find(row => row.q.id === q.id)?.index]?.id }
    await router.push({ path: `/learning/session/${result.id}`, query: questionId ? { questionId } : {} })
  } catch (e) { actionError.value = e.message || '复练创建失败，请重试。' }
  finally { busy.value = false }
}
async function review(q) {
  if (busy.value) return
  busy.value = true; actionError.value = ''; actionNotice.value = ''
  try { const state = (reviews.value[q.id] ?? q.reviewState) === 'PENDING' ? 'RESOLVED' : 'PENDING'; const result = await setPracticeReview(q.id, state); if (disposed) return; reviews.value[q.id] = result.state; actionNotice.value = `${q.title}：${result.state === 'PENDING' ? '已加入待复习' : '已标记复习完成'}，历史判题结果保持不变。` }
  catch (e) { if (!disposed) actionError.value = e.message || '复习状态更新失败，请重试。' }
  finally { busy.value = false }
}
async function favorite(q) {
  if (busy.value) return
  busy.value = true; actionError.value = ''; actionNotice.value = ''
  try { const result = await togglePracticeFavorite(q.id); if (disposed) return; favorites.value[q.id] = result.favorite; actionNotice.value = `${q.title}：${result.favorite ? '已收藏' : '已取消收藏'}。` }
  catch (e) { if (!disposed) actionError.value = e.message || '收藏操作失败，请重试。' }
  finally { busy.value = false }
}
onBeforeUnmount(() => { disposed = true; clearTimeout(timer); sequence++ })
</script>
<style scoped src="./practice-report.css"></style>
