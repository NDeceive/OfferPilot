<!--
  手机端练习报告：先给结论（正确率、提交情况），再给题型分布，最后逐题展开。
  与桌面端共用 practiceReport 工具函数，保证正确率、结果标签和复习状态口径一致。
  题目详情默认收起：手机上一次只展开一道，避免长报告失去节奏。
-->
<template>
  <MobileShell title="练习报告" class="mpr-shell">
    <router-link to="/learning" class="mpr-back">← 返回专项刷题</router-link>

    <MobileSkeleton v-if="loading" variant="stats" label="正在整理练习报告" />
    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在整理逐题记录" />

    <MobileState
      v-else-if="error"
      kind="error"
      title="报告暂时打不开"
      :description="error"
      action="重新加载"
      @action="load"
    />

    <MobileState
      v-else-if="!report"
      title="这次练习还没有报告"
      description="返回专项刷题，继续作答或重新组一套题。"
      action="返回刷题"
      @action="router.push('/learning')"
    />

    <template v-else>
      <p v-if="report.settlementStatus === 'PROCESSING'" class="mpr-notice is-live" role="status">
        还有 {{ report.pendingCount || '部分' }} 个判题任务没结束，当前不是最终结果。
        <button type="button" @click="load">刷新</button>
      </p>
      <p v-if="systemFailures" class="mpr-notice is-error" role="alert">
        {{ systemFailures }} 个判题请求异常，不计入答错。可稍后重新练习。
      </p>

      <section class="mpr-hero" aria-labelledby="mpr-hero-title">
        <div class="mpr-hero-copy">
          <span class="mpr-kicker">{{ statusText }}<template v-if="finishedText"> · <span class="mpr-date">{{ finishedText }}</span></template></span>
          <h1 id="mpr-hero-title">{{ report.topic || '专项练习' }}</h1>
          <p>{{ report.role?.name || '历史岗位' }} · {{ report.company || '不限公司' }}</p>
          <div class="mpr-hero-meta">
            <span>已提交 {{ metrics.submitted }} / {{ metrics.total }}</span>
            <span v-if="elapsed">用时 {{ elapsed }}</span>
          </div>
          <p v-if="score != null" class="mpr-hero-accuracy">已判 {{ metrics.evaluated }} 题 · 正确率 {{ score }}%</p>
        </div>

        <div class="mpr-score" role="img" :aria-label="`完成进度 ${completion}%`">
          <svg viewBox="0 0 120 120" aria-hidden="true">
            <circle class="mpr-score-track" cx="60" cy="60" r="52" />
            <circle class="mpr-score-value" cx="60" cy="60" r="52" :stroke-dasharray="`${completionDash} 326.73`" />
          </svg>
          <div class="mpr-score-text">
            <strong>{{ metrics.submitted }}<small>/ {{ metrics.total }}</small></strong>
            <span>{{ completionCaption }}</span>
          </div>
        </div>
      </section>

      <p v-if="report.status === 'ONGOING'" class="mpr-ongoing">
        这轮还没交卷，当前只是阶段记录。
        <router-link :to="sessionPath">继续练习 →</router-link>
      </p>

      <section class="mpr-stats" aria-label="题型表现">
        <article class="mpr-stat">
          <h2>客观题</h2>
          <strong>{{ metrics.objectiveCorrect }}<small>/ {{ metrics.objectiveTotal }}</small></strong>
          <div class="mpr-bar" aria-hidden="true"><i :style="{ width: `${ratio(metrics.objectiveCorrect, metrics.objectiveTotal)}%` }"></i></div>
          <p>正确 {{ metrics.objectiveCorrect }} · 错误 {{ metrics.objectiveIncorrect }}</p>
        </article>
        <article class="mpr-stat">
          <h2>编程题</h2>
          <strong>{{ metrics.codingPassed }}<small>/ {{ metrics.codingTotal }}</small></strong>
          <div class="mpr-bar" aria-hidden="true"><i :style="{ width: `${ratio(metrics.codingPassed, metrics.codingTotal)}%` }"></i></div>
          <p>通过 {{ metrics.codingPassed }} · 未通过 {{ metrics.codingFailed }}</p>
        </article>
        <article class="mpr-stat is-wide">
          <h2>简答题</h2>
          <strong>{{ metrics.knowledgeTotal }}<small> 道</small></strong>
          <p>待自评 {{ metrics.selfReview }} · 已自评 {{ metrics.selfReviewed }}</p>
        </article>
      </section>

      <section v-if="canRetry" class="mpr-retry" aria-label="继续练习">
        <div>
          <h2>把没答好的再练一遍</h2>
        </div>
        <div class="mpr-retry-actions">
          <button type="button" class="mpr-primary" :disabled="busy || !retryCount" @click="retry(true)">
            重练未通过题<template v-if="retryCount">（{{ retryCount }}）</template>
          </button>
          <button type="button" :disabled="busy" @click="retry(false)">按原题再练</button>
        </div>
      </section>

      <section class="mpr-review" aria-labelledby="mpr-review-title">
        <header class="mpr-review-head">
          <div>
            <h2 id="mpr-review-title">逐题复盘</h2>
          </div>
          <span>{{ rows.length }} 题</span>
        </header>

        <nav class="mpr-filters" aria-label="筛选题目">
          <button
            v-for="item in filterOptions"
            :key="item.key"
            type="button"
            :class="{ active: filter === item.key }"
            :aria-pressed="filter === item.key"
            @click="filter = item.key"
          >{{ item.label }} <span>{{ item.count }}</span></button>
        </nav>

        <p v-if="actionError" class="mpr-action-error" role="alert">{{ actionError }}</p>
        <p v-if="actionNotice" class="mpr-action-notice" role="status">{{ actionNotice }}</p>

        <div class="mpr-list">
          <article v-for="row in visibleRows" :key="row.q.id" class="mpr-row-card" :class="{ open: expandedId === row.q.id }">
            <button
              type="button"
              class="mpr-row"
              :aria-expanded="expandedId === row.q.id"
              @click="toggle(row)"
            >
              <span class="mpr-index">{{ String(row.index + 1).padStart(2, '0') }}</span>
              <span class="mpr-row-copy">
                <strong>{{ row.q.title }}</strong>
                <small>{{ practiceTypeLabel(row.q.type) }}<template v-if="row.favorite"> · ★ 已收藏</template></small>
              </span>
              <span class="mpr-outcome" :class="row.outcome.key">{{ row.outcome.label }}</span>
              <span class="mpr-chevron" aria-hidden="true">›</span>
            </button>

            <Transition name="mpr-expand">
              <div v-if="expandedId === row.q.id" class="mpr-detail">
                <template v-if="feedbackAvailable">
                  <div class="mpr-detail-block">
                    <h3>你的作答</h3>
                    <pre v-if="answerOf(row)" :class="{ 'is-code': row.kind === 'coding' }">{{ answerOf(row) }}</pre>
                    <p v-else class="mpr-muted">这道题没有提交作答。</p>
                  </div>

                  <p v-if="messageOf(row)" class="mpr-message">{{ messageOf(row) }}</p>

                  <div v-if="row.kind === 'objective' && correctOf(row)" class="mpr-detail-block">
                    <h3>正确答案</h3>
                    <p class="mpr-correct">{{ correctOf(row) }}</p>
                  </div>

                  <div v-if="referenceOf(row)" class="mpr-detail-block">
                    <h3>参考解析</h3>
                    <p class="mpr-pre">{{ referenceOf(row) }}</p>
                  </div>

                  <div v-if="canSelfAssess(row)" class="mpr-self">
                    <p>{{ row.q.selfAssessment ? '已自评，可以随时修改。' : '对照参考解析，这道题掌握了吗？' }}</p>
                    <div>
                      <button
                        type="button"
                        :class="{ active: row.q.selfAssessment?.state === 'MASTERED' }"
                        :disabled="busy"
                        @click="assess(row, 'MASTERED')"
                      >已掌握</button>
                      <button
                        type="button"
                        :class="{ active: row.q.selfAssessment?.state === 'NEEDS_REVIEW' }"
                        :disabled="busy"
                        @click="assess(row, 'NEEDS_REVIEW')"
                      >需要巩固</button>
                    </div>
                  </div>

                  <div class="mpr-detail-actions">
                    <button type="button" :disabled="busy" :aria-pressed="Boolean(row.q.favorite)" @click="favorite(row)">
                      {{ row.q.favorite ? '★ 已收藏' : '☆ 收藏' }}
                    </button>
                    <button type="button" :disabled="busy" @click="review(row)">
                      {{ row.q.reviewState === 'PENDING' ? '标记复习完成' : '加入待复习' }}
                    </button>
                  </div>
                </template>
                <p v-else class="mpr-muted">交卷后展示判定与解析。</p>
              </div>
            </Transition>
          </article>

          <div v-if="!visibleRows.length" class="mpr-empty">
            <strong>{{ rows.length ? '这个筛选下没有题目' : '这次练习没有题目记录' }}</strong>
            <p>{{ rows.length ? '换一个筛选看看。' : '返回专项刷题，重新组一套题。' }}</p>
            <button v-if="rows.length" type="button" @click="filter = 'all'">看全部题目</button>
          </div>
        </div>
      </section>

      <section class="mpr-share">
        <router-link :to="noteLink">写一条练习心得 →</router-link>
      </section>

      <div class="mpr-end-space" aria-hidden="true"></div>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import {
  getPracticeReport, retryPractice, setPracticeReview, setPracticeSelfAssessment,
  togglePracticeFavorite,
} from '../../services/learningResources'
import { practiceTypeLabel } from '../../utils/practiceTypes'
import {
  canRetryReportQuestion, formatPracticeDate, practiceElapsed, reportMetrics,
  reportQuestionKind, reportQuestionOutcome,
} from '../../utils/practiceReport'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.sessionId
const sessionPath = `/learning/session/${sessionId}`

const report = ref(null)
const loading = ref(true)
const error = ref('')
const busy = ref(false)
const expandedId = ref(null)
const filter = ref('all')
const actionError = ref('')
const actionNotice = ref('')
let timer = null
let noticeTimer = null
let disposed = false
let sequence = 0

const metrics = computed(() => reportMetrics(report.value || {}))
const feedbackAvailable = computed(() => !report.value?.deferFeedback || report.value?.status === 'FINISHED')
const canRetry = computed(() => report.value?.settlementStatus === 'READY' && report.value?.status === 'FINISHED')
const elapsed = computed(() => practiceElapsed(report.value?.startedAt, report.value?.finishedAt))
// 日期就够了，时间会单独占一行并把 hero 的标题挤下去；用时在下方单独展示。
const finishedText = computed(() => report.value?.finishedAt ? formatPracticeDate(report.value.finishedAt).split(' ')[0] : '')
const systemFailures = computed(() => Math.max(0, report.value?.systemFailureCount || 0))
const score = computed(() => feedbackAvailable.value && metrics.value.score != null ? metrics.value.score : null)
const completion = computed(() => {
  const { submitted, total } = metrics.value
  return total ? Math.round(submitted / total * 100) : 0
})
const completionDash = computed(() => Math.round(completion.value / 100 * 326.73))
const completionCaption = computed(() => report.value?.status === 'FINISHED' ? '已完成' : '进行中')
const statusText = computed(() => report.value?.status === 'FINISHED' ? '本次练习已完成' : report.value?.status === 'ONGOING' ? '练习进行中' : '正在结算')

const rows = computed(() => (report.value?.questionList || []).map((q, index) => ({
  q,
  index,
  kind: reportQuestionKind(q),
  outcome: reportQuestionOutcome(q),
  favorite: Boolean(q.favorite),
})))

const counts = computed(() => ({
  all: rows.value.length,
  failed: rows.value.filter(row => row.outcome.key === 'failed').length,
  passed: rows.value.filter(row => row.outcome.key === 'passed').length,
  self: rows.value.filter(row => ['self', 'self_done'].includes(row.outcome.key)).length,
}))

const filterOptions = computed(() => [
  { key: 'all', label: '全部', count: counts.value.all },
  { key: 'failed', label: '没答好', count: counts.value.failed },
  { key: 'passed', label: '已通过', count: counts.value.passed },
  { key: 'self', label: '待自评', count: counts.value.self },
])

const visibleRows = computed(() => {
  if (filter.value === 'all') return rows.value
  if (filter.value === 'self') return rows.value.filter(row => ['self', 'self_done'].includes(row.outcome.key))
  return rows.value.filter(row => row.outcome.key === filter.value)
})

const retryCount = computed(() => rows.value.filter(row => canRetryReportQuestion(row.q, row.q.reviewState)).length)

// 报告 → 心得：把这次练习的结论和没把握住的题带进编辑页，用户只补「当时怎么想、下一步怎么改」。
const noteLink = computed(() => {
  const topic = report.value?.topic || '专项练习'
  const missed = rows.value.filter(row => ['failed', 'self'].includes(row.outcome.key)).slice(0, 3)
  const resultLine = score.value == null
    ? `已提交 ${metrics.value.submitted} / ${metrics.value.total} 题`
    : `正确率 ${score.value}%（${metrics.value.correct} / ${metrics.value.evaluated}）`
  const content = [
    `刚做完一组「${topic}」练习，${resultLine}。`,
    missed.length
      ? `这次没把握住的是：\n${missed.map(row => `· ${row.q.title || `第 ${row.index + 1} 题`}`).join('\n')}`
      : '这次整体比较顺，把有效的做法和临场判断记下来。',
    '下一步：回到错题本重做一遍，再把这组题背后的知识点补上。',
  ].join('\n\n')
  return {
    path: '/experience/new',
    query: {
      title: `《${topic}》练习复盘：${score.value == null ? '记录与下一步' : `${score.value}% 正确率`}`.slice(0, 60),
      content,
      tags: `练习心得 ${topic}`.slice(0, 40),
      jobCode: report.value?.role?.code || '',
    },
  }
})

function ratio(n, d) {
  return d ? Math.round(n / d * 100) : 0
}

function answerOf(row) {
  const submission = row.q.latestSubmission
  return submission?.sourceCode || submission?.answerText || ''
}

function messageOf(row) {
  const result = row.q.latestSubmission?.result
  return result?.message || ''
}

function correctOf(row) {
  const value = row.q.latestSubmission?.result?.correctAnswer
  if (value == null || value === '') return ''
  return value === 'true' ? '正确' : value === 'false' ? '错误' : value
}

function referenceOf(row) {
  return row.q.referenceAnswer || row.q.latestSubmission?.result?.referenceAnswer || ''
}

function canSelfAssess(row) {
  return Boolean(report.value?.demoMode) && row.kind === 'knowledge' && ['self', 'self_done'].includes(row.outcome.key)
}

function toggle(row) {
  expandedId.value = expandedId.value === row.q.id ? null : row.q.id
}

function notify(message) {
  actionNotice.value = message
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { actionNotice.value = '' }, 3200)
}

async function load() {
  const id = ++sequence
  clearTimeout(timer)
  if (!report.value) loading.value = true
  error.value = ''
  try {
    const result = await getPracticeReport(sessionId)
    if (disposed || id !== sequence) return
    report.value = result
    if (result.settlementStatus === 'PROCESSING') timer = setTimeout(load, 2000)
  } catch (e) {
    if (!disposed && id === sequence) error.value = e.message || '报告加载失败，请重试。'
  } finally {
    if (id === sequence) loading.value = false
  }
}

async function retry(failedOnly) {
  if (busy.value || !canRetry.value) return
  busy.value = true
  actionError.value = ''
  try {
    const result = await retryPractice(sessionId, failedOnly)
    if (disposed) return
    await router.push(`/learning/session/${result.id}`)
  } catch (e) {
    if (!disposed) actionError.value = e.message || '复练创建失败，请重试。'
  } finally {
    busy.value = false
  }
}

async function favorite(row) {
  if (busy.value) return
  busy.value = true
  actionError.value = ''
  try {
    const result = await togglePracticeFavorite(row.q.id)
    if (disposed) return
    row.q.favorite = result.favorite
    notify(`${row.q.title}：${result.favorite ? '已收藏' : '已取消收藏'}。`)
  } catch (e) {
    if (!disposed) actionError.value = e.message || '收藏操作失败，请重试。'
  } finally {
    busy.value = false
  }
}

async function review(row) {
  if (busy.value) return
  busy.value = true
  actionError.value = ''
  try {
    const state = row.q.reviewState === 'PENDING' ? 'RESOLVED' : 'PENDING'
    const result = await setPracticeReview(row.q.id, state)
    if (disposed) return
    row.q.reviewState = result.state
    notify(`${row.q.title}：${result.state === 'PENDING' ? '已加入待复习' : '已标记复习完成'}。`)
  } catch (e) {
    if (!disposed) actionError.value = e.message || '复习状态更新失败，请重试。'
  } finally {
    busy.value = false
  }
}

async function assess(row, state) {
  if (busy.value || !report.value?.demoMode) return
  busy.value = true
  actionError.value = ''
  try {
    const result = await setPracticeSelfAssessment(sessionId, row.q.id, state)
    if (disposed) return
    row.q.selfAssessment = result.selfAssessment
    row.q.reviewState = result.reviewState
    notify(`${row.q.title}：自评已保存。`)
  } catch (e) {
    if (!disposed) actionError.value = e.message || '自评保存失败，请重试。'
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  // 交卷后是同路由替换，不重置滚动会直接落在报告中部、看不到标题和结论。
  window.scrollTo(0, 0)
  load()
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
  clearTimeout(noticeTimer)
  sequence++
})
</script>

<style scoped>
.mpr-back { display: inline-flex; min-height: 44px; align-items: center; color: var(--m-primary); font-size: 13px; font-weight: 600; }

.mpr-notice {
  margin-bottom: 12px;
  padding: 10px 12px;
  color: #5c4429;
  background: var(--m-accent-soft);
  border-radius: var(--m-radius-input);
  font-size: 12.5px;
  line-height: 1.65;
}
.mpr-notice.is-error { color: #a4352b; background: #fff0ee; }
.mpr-notice button {
  min-height: 30px;
  margin-left: 4px;
  padding: 0 8px;
  color: inherit;
  background: rgba(255, 255, 255, .6);
  border: 1px solid currentColor;
  border-radius: 7px;
  font-size: 11.5px;
  font-weight: 700;
}

.mpr-hero {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 4px;
  padding: 20px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(145deg, #174b39 0%, #2f7458 100%);
  border-radius: var(--m-radius-hero);
  box-shadow: 0 16px 34px rgba(23, 75, 57, .2);
  animation: mpr-rise 460ms cubic-bezier(.16, 1, .3, 1) both;
}
.mpr-hero::after {
  position: absolute;
  top: -58px;
  right: -46px;
  width: 160px;
  height: 160px;
  background: rgba(255, 255, 255, .07);
  border-radius: 50%;
  content: '';
}
.mpr-hero-copy { position: relative; z-index: 1; min-width: 0; flex: 1; }
.mpr-kicker { display: inline-flex; max-width: 100%; color: #cfe3d6; font-size: 11px; font-weight: 700; letter-spacing: .04em; line-height: 1.5; }
/* 320px 宽度下日期会被拆成「2026-11- / 05」；整段不换行，必要时整体落到下一行。 */
.mpr-date { white-space: nowrap; }
.mpr-hero h1 { margin-top: 7px; color: #fff; font-size: 22px; font-weight: 800; letter-spacing: -.035em; line-height: 1.32; overflow-wrap: anywhere; }
.mpr-hero-copy > p { margin-top: 6px; color: #cfe3d6; font-size: 12.5px; line-height: 1.6; }
.mpr-hero-meta { display: flex; flex-wrap: wrap; gap: 6px 12px; margin-top: 12px; color: #e2efe7; font-size: 11.5px; line-height: 1.5; }
.mpr-hero-meta span { display: inline-flex; align-items: center; gap: 4px; }
.mpr-hero-accuracy { margin-top: 8px; color: #fff; font-size: 12px; font-weight: 700; }

.mpr-score { position: relative; z-index: 1; flex: 0 0 auto; width: 112px; height: 112px; }
.mpr-score svg { display: block; width: 100%; height: 100%; transform: rotate(-90deg); }
.mpr-score circle { fill: none; stroke-width: 8; }
.mpr-score-track { stroke: rgba(255, 255, 255, .16); }
.mpr-score-value { stroke: var(--m-accent); stroke-linecap: round; transition: stroke-dasharray 720ms cubic-bezier(.16, 1, .3, 1); }
.mpr-score-text { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.mpr-score-text strong { color: #fff; font-size: 29px; font-weight: 800; letter-spacing: -.05em; line-height: 1; font-variant-numeric: tabular-nums; }
.mpr-score-text strong small { margin-left: 1px; color: #cfe3d6; font-size: 12px; font-weight: 700; }
.mpr-score-text span { margin-top: 6px; color: #cfe3d6; font-size: 10px; line-height: 1.2; text-align: center; }

.mpr-ongoing {
  margin-top: 12px;
  padding: 12px 14px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border-radius: var(--m-radius-card);
  font-size: 13px;
  line-height: 1.65;
}
.mpr-ongoing a { color: var(--m-primary-dark); font-weight: 800; text-decoration: underline; text-underline-offset: 2px; }

.mpr-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
.mpr-stat {
  padding: 14px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  box-shadow: 0 2px 10px rgba(32, 61, 48, .03);
}
.mpr-stat.is-wide { grid-column: 1 / -1; }
.mpr-stat h2 { color: var(--m-text-secondary); font-size: 11.5px; font-weight: 700; }
.mpr-stat > strong { display: block; margin-top: 6px; color: var(--m-text); font-size: 22px; font-weight: 800; letter-spacing: -.03em; font-variant-numeric: tabular-nums; }
.mpr-stat > strong small { color: var(--m-text-tertiary); font-size: 12px; font-weight: 600; letter-spacing: 0; }
.mpr-bar { height: 5px; margin-top: 9px; overflow: hidden; background: var(--m-border); border-radius: 999px; }
.mpr-bar i { display: block; height: 100%; background: var(--m-primary); border-radius: inherit; transition: width var(--m-motion-slow); }
.mpr-stat p { margin-top: 8px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.6; }
.mpr-stat.is-wide p { margin-top: 6px; }

.mpr-retry {
  display: flex;
  margin-top: 12px;
  padding: 16px;
  flex-direction: column;
  gap: 12px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
}
.mpr-retry h2 { font-size: 15px; font-weight: 800; }
.mpr-retry p { margin-top: 4px; color: var(--m-text-tertiary); font-size: 12px; line-height: 1.6; }
.mpr-retry-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.mpr-retry-actions button {
  min-height: 42px;
  padding: 0 14px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 700;
  transition: transform var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mpr-retry-actions button:active:not(:disabled) { transform: scale(.97); }
.mpr-retry-actions button:disabled { opacity: .45; }
.mpr-retry-actions .mpr-primary { color: #fff; background: var(--m-primary); }

.mpr-review { margin-top: 24px; }
.mpr-review-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.mpr-review-head h2 { font-size: 20px; font-weight: 800; letter-spacing: -.03em; }
.mpr-review-head p { margin-top: 4px; color: var(--m-text-tertiary); font-size: 12px; line-height: 1.6; }
.mpr-review-head > span { flex: 0 0 auto; padding-top: 4px; color: var(--m-text-tertiary); font-size: 12px; font-variant-numeric: tabular-nums; }

.mpr-filters { display: flex; gap: 7px; margin-top: 14px; overflow-x: auto; padding-bottom: 2px; scrollbar-width: none; }
.mpr-filters::-webkit-scrollbar { display: none; }
.mpr-filters button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 11px;
  align-items: center;
  gap: 5px;
  color: var(--m-text-secondary);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  transition: color var(--m-motion) ease, background var(--m-motion) ease, border-color var(--m-motion) ease, transform var(--m-motion) ease;
}
.mpr-filters button:active { transform: scale(.95); }
.mpr-filters button span { color: var(--m-text-tertiary); font-size: 10.5px; font-variant-numeric: tabular-nums; }
.mpr-filters button.active { color: var(--m-primary-dark); background: var(--m-primary-soft); border-color: #cfe6d8; }
.mpr-filters button.active span { color: var(--m-primary); }

.mpr-action-error,
.mpr-action-notice {
  margin-top: 12px;
  padding: 9px 11px;
  border-radius: var(--m-radius-input);
  font-size: 12.5px;
  line-height: 1.6;
}
.mpr-action-error { color: #a4352b; background: #fff0ee; }
.mpr-action-notice { color: var(--m-primary-dark); background: var(--m-primary-soft); }

.mpr-list { display: flex; margin-top: 12px; flex-direction: column; gap: 9px; }
.mpr-row-card {
  overflow: hidden;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  transition: border-color var(--m-motion) ease, box-shadow var(--m-motion) ease;
}
.mpr-row-card.open { border-color: #cfe6d8; box-shadow: 0 6px 18px rgba(32, 61, 48, .06); }
.mpr-row { display: flex; width: 100%; min-height: 62px; padding: 12px 12px; align-items: center; gap: 9px; color: var(--m-text); background: transparent; border: 0; text-align: left; }
.mpr-index {
  display: grid;
  flex: 0 0 auto;
  width: 31px;
  height: 31px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border-radius: 9px;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  place-items: center;
}
.mpr-row-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }
.mpr-row-copy strong { overflow: hidden; font-size: 13.5px; font-weight: 700; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.mpr-row-copy small { color: var(--m-text-tertiary); font-size: 11px; line-height: 1.4; }
.mpr-outcome {
  flex: 0 0 auto;
  padding: 3px 7px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border-radius: 6px;
  font-size: 10.5px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}
.mpr-outcome.passed { color: var(--m-primary-dark); background: var(--m-primary-soft); }
.mpr-outcome.failed { color: #a4352b; background: #fff0ee; }
.mpr-outcome.pending { color: #825819; background: var(--m-accent-soft); }
.mpr-outcome.self,
.mpr-outcome.self_done { color: #825819; background: var(--m-accent-soft); }
.mpr-outcome.system { color: #a4352b; background: #fff0ee; }
.mpr-chevron { flex: 0 0 auto; color: var(--m-text-tertiary); font-size: 20px; line-height: 1; transition: transform var(--m-motion) ease; }
.mpr-row-card.open .mpr-chevron { transform: rotate(90deg); }

.mpr-detail { padding: 2px 12px 14px; border-top: 1px dashed var(--m-border); }
.mpr-detail-block { margin-top: 13px; }
.mpr-detail-block h3 { color: var(--m-text-tertiary); font-size: 11px; font-weight: 700; letter-spacing: .03em; }
.mpr-detail-block pre {
  max-height: 230px;
  margin-top: 6px;
  padding: 10px 11px;
  overflow: auto;
  color: var(--m-text);
  background: var(--m-surface-soft);
  border-radius: 9px;
  font: 12.5px/1.7 Consolas, 'SFMono-Regular', monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.mpr-detail-block pre.is-code { color: #dce9e0; background: #17241e; white-space: pre; }
.mpr-detail-block .mpr-correct { margin-top: 5px; color: var(--m-primary-dark); font-size: 13.5px; font-weight: 800; }
.mpr-detail-block .mpr-pre { margin-top: 5px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
.mpr-muted { margin-top: 5px; color: var(--m-text-tertiary); font-size: 12.5px; line-height: 1.65; }
.mpr-message { margin-top: 10px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.7; }

.mpr-self { margin-top: 14px; padding: 12px; background: var(--m-accent-soft); border-radius: 10px; }
.mpr-self p { color: #5c4429; font-size: 12px; line-height: 1.6; }
.mpr-self > div { display: flex; gap: 8px; margin-top: 9px; }
.mpr-self button {
  flex: 1;
  min-height: 38px;
  color: #6b4a26;
  background: rgba(255, 255, 255, .66);
  border: 1px solid #eed8bd;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
}
.mpr-self button.active { color: #fff; background: #b66b20; border-color: #b66b20; }

.mpr-detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 15px; }
.mpr-detail-actions button {
  min-height: 38px;
  padding: 0 12px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border: 0;
  border-radius: 9px;
  font-size: 12px;
  font-weight: 700;
}
.mpr-detail-actions button:active { transform: scale(.96); }
.mpr-detail-actions button[aria-pressed="true"] { color: #a86212; }

.mpr-empty { padding: 24px 16px; text-align: center; }
.mpr-empty strong { font-size: 14px; }
.mpr-empty p { margin-top: 6px; color: var(--m-text-tertiary); font-size: 12.5px; line-height: 1.6; }
.mpr-empty button { min-height: 38px; margin-top: 12px; padding: 0 14px; color: var(--m-primary-dark); background: var(--m-primary-soft); border: 0; border-radius: 9px; font-size: 12.5px; font-weight: 700; }

.mpr-expand-enter-active,
.mpr-expand-leave-active { transition: opacity 180ms ease, transform 180ms ease; }
.mpr-expand-enter-from,
.mpr-expand-leave-to { opacity: 0; transform: translateY(-4px); }

.mpr-note { margin-top: 20px; padding: 14px 16px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mpr-note summary { min-height: 32px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 700; cursor: pointer; }
.mpr-note p { margin-top: 6px; color: var(--m-text-tertiary); font-size: 12px; line-height: 1.7; }

.mpr-share { display: flex; margin-top: 16px; justify-content: flex-end; }
.mpr-share a {
  display: inline-flex;
  min-height: 40px;
  padding: 0 13px;
  align-items: center;
  color: #7a4a16;
  background: #fbefdf;
  border-radius: 10px;
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
  transition: transform var(--m-motion) ease;
}
.mpr-share a:active { transform: scale(.96); }

.mpr-end-space { height: 8px; }

@keyframes mpr-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 359px) {
  .mpr-hero { padding: 16px; gap: 10px; }
  .mpr-hero h1 { font-size: 19px; }
  .mpr-score { width: 96px; height: 96px; }
  .mpr-score-text strong { font-size: 25px; }
  .mpr-row { padding-inline: 10px; gap: 7px; }
  .mpr-index { width: 28px; height: 28px; }
  .mpr-outcome { font-size: 10px; padding-inline: 5px; }
  .mpr-share { justify-content: stretch; }
  .mpr-share a { flex: 1 1 auto; justify-content: center; }
}

@media (prefers-reduced-motion: reduce) {
  .mpr-hero { animation: none; }
  .mpr-score-value,
  .mpr-bar i { transition: none; }
  .mpr-expand-enter-active,
  .mpr-expand-leave-active { transition: none; }
}

.mpr-back:focus-visible,
.mpr-notice button:focus-visible,
.mpr-retry-actions button:focus-visible,
.mpr-filters button:focus-visible,
.mpr-row:focus-visible,
.mpr-self button:focus-visible,
.mpr-detail-actions button:focus-visible,
.mpr-empty button:focus-visible,
.mpr-share a:focus-visible,
.mpr-note summary:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }
</style>
