<template>
  <AppLayout>
    <main class="report-page">
      <router-link to="/history" class="back-link" aria-label="返回面试记录">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m15 18-6-6 6-6" /></svg>
        返回面试记录
      </router-link>

      <div v-if="loading" class="state-panel">正在整理本次面试报告…</div>
      <div v-else-if="loadError" class="state-panel error-state">
        <strong>报告暂时无法加载</strong>
        <span>{{ loadError }}</span>
        <button type="button" @click="loadReport">重新加载</button>
      </div>

      <template v-else>
        <header class="report-hero">
          <div class="role-block">
            <JobLogo :icon-key="iconKey" :tone="themeKey" class="job-logo" />
            <div>
              <div class="eyebrow">{{ category || '专项模拟面试' }} · {{ directionCode || '岗位训练' }}</div>
              <h1>{{ jobName }}</h1>
              <div class="meta-line">
                <span>{{ formatDate(startTime) }}</span>
                <span>{{ formatDuration(durationSeconds) }}</span>
                <span>{{ questionCount }} 道主问题</span>
              </div>
            </div>
          </div>
          <div class="score-block">
            <span>{{ displayLevel ? '训练目标匹配度' : '综合评分' }}</span>
            <strong>{{ totalScore }}</strong>
            <small>/ 100 · {{ displayLevel || scoreRank }}</small>
          </div>
        </header>

        <section class="conclusion-panel">
          <div class="section-kicker">本次结论</div>
          <p>{{ summary || '报告已生成，请结合能力分布与逐题回顾制定下一步练习计划。' }}</p>
          <div v-if="profileTags.length" class="profile-tags"><span>岗位画像</span><i v-for="tag in profileTags" :key="tag">{{ tag }}</i></div>
        </section>

        <section class="analysis-section">
          <div class="section-heading">
            <div>
              <span class="section-kicker">能力拆解</span>
              <h2>先看差距，再决定练什么</h2>
            </div>
            <p>评分用于定位表达与能力证据的完整度，不代表岗位录用结果。</p>
          </div>

          <div class="ability-layout">
            <div class="radar-wrap">
              <RadarChart :labels="radarLabels" :values="radarValues" :target-values="radarTargets" :show-dual-layer="radarTargets.length > 0" />
            </div>
            <div class="dimension-list">
              <article v-for="dimension in dimensions" :key="dimension.dimension" class="dimension-row">
                <div class="dimension-score">{{ dimension.score }}</div>
                <div>
                  <div class="dimension-title">
                    <strong>{{ dimension.dimension }}</strong>
                    <span>/ 100</span>
                  </div>
                  <div class="score-track"><i :style="{ width: `${dimension.score}%` }"></i></div>
                  <p>{{ dimension.explanation || '结合逐题记录，继续补充更明确的事实与结果。' }}</p>
                </div>
              </article>
            </div>
          </div>

          <div v-if="isNewReport" class="module-details">
            <article v-for="module in moduleScores" :key="module.moduleCode" class="module-detail">
              <div class="module-detail__head">
                <strong>{{ module.moduleName || module.moduleCode }}</strong>
                <span v-if="module.baseWeight != null">权重 {{ formatPercent(module.baseWeight) }}</span>
              </div>
              <div class="module-detail__scores">
                <span>实际 {{ formatModuleScore(module.rawScore) }}</span>
                <div class="module-track">
                  <i :style="{ width: `${clampScore(module.rawScore)}%` }"></i>
                  <b :style="{ left: `${clampScore(module.targetScore)}%` }" aria-hidden="true"></b>
                </div>
                <span>目标 {{ formatModuleScore(module.targetScore) }}</span>
              </div>
              <div class="module-detail__meta">
                <span>匹配度 {{ formatPercent(module.moduleMatch) }}</span>
                <span :class="Number(module.gapScore) > 0 ? 'module-gap' : 'module-reached'">
                  {{ Number(module.gapScore) > 0 ? `差距 ${formatModuleScore(module.gapScore)} 分` : '已达到目标' }}
                </span>
              </div>
              <p v-if="module.evidence"><b>评分依据</b>{{ module.evidence }}</p>
              <p v-if="module.suggestion"><b>练习建议</b>{{ module.suggestion }}</p>
            </article>
          </div>
        </section>

        <section v-if="isNewReport && improvementPriorities.length" class="priority-section">
          <div class="section-heading compact-heading">
            <div>
              <span class="section-kicker">提升顺序</span>
              <h2>优先补齐影响最大的能力差距</h2>
            </div>
          </div>
          <ol class="priority-list">
            <li v-for="(module, index) in improvementPriorities" :key="module.moduleCode">
              <span>{{ index + 1 }}</span>
              <strong>{{ module.moduleName || module.moduleCode }}</strong>
              <small>差距 {{ formatModuleScore(module.gapScore) }} 分</small>
              <p>{{ module.suggestion || module.evidence || '结合逐题记录补充事实、行动与结果。' }}</p>
            </li>
          </ol>
        </section>

        <section class="insight-grid">
          <article class="insight-column strength-column">
            <div class="section-kicker">值得保留</div>
            <h2>本次表现优势</h2>
            <ul>
              <li v-for="item in strengths" :key="item">{{ item }}</li>
              <li v-if="!strengths.length">暂未形成稳定优势，建议通过更多练习积累有效样本。</li>
            </ul>
          </article>
          <article class="insight-column weakness-column">
            <div class="section-kicker">优先改进</div>
            <h2>下一轮重点</h2>
            <ul>
              <li v-for="item in weaknesses" :key="item">{{ item }}</li>
              <li v-if="!weaknesses.length">暂无明显短板，下一轮可提高回答难度。</li>
            </ul>
          </article>
        </section>

        <section class="action-section">
          <div class="section-heading compact-heading">
            <div>
              <span class="section-kicker">行动建议</span>
              <h2>把反馈落实到下一次回答</h2>
            </div>
          </div>
          <ol class="action-list">
            <li v-for="(item, index) in suggestions" :key="`${index}-${item}`">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <p>{{ item }}</p>
            </li>
            <li v-if="!suggestions.length"><span>01</span><p>回看逐题记录，先重写一段证据不足的回答。</p></li>
          </ol>
        </section>

        <section class="review-section">
          <div class="section-heading">
            <div>
              <span class="section-kicker">逐题回顾</span>
              <h2>问题、回答与追问上下文</h2>
            </div>
            <p>共 {{ questionCount }} 组对话</p>
          </div>

          <div v-if="followupRecords.length" class="question-list">
            <article
              v-for="(record, index) in followupRecords"
              :key="index"
              class="question-item"
              :class="{ expanded: expandedItems.includes(index) }"
            >
              <button type="button" class="question-trigger" :aria-expanded="expandedItems.includes(index)" @click="toggleExpand(index)">
                <span class="question-index">Q{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="question-copy">
                  <small>{{ record.abilityTag || '综合能力' }}</small>
                  <strong>{{ record.question }}</strong>
                </span>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
              </button>
              <div v-if="expandedItems.includes(index)" class="answer-body">
                <div class="dialogue answer">
                  <span>你的回答</span>
                  <p>{{ record.answer || '本题未记录到有效回答。' }}</p>
                </div>
                <template v-for="(followup, followupIndex) in record.followups" :key="followupIndex">
                  <div class="dialogue followup">
                    <span>面试官追问 {{ followupIndex + 1 }}</span>
                    <p>{{ followup }}</p>
                  </div>
                  <div v-if="record.followupAnswers[followupIndex]" class="dialogue answer">
                    <span>你的补充回答</span>
                    <p>{{ record.followupAnswers[followupIndex] }}</p>
                  </div>
                </template>
              </div>
            </article>
          </div>
          <div v-else class="empty-review">本次面试暂无可回顾的对话记录。</div>
        </section>

        <footer class="report-actions">
          <div>
            <button type="button" class="secondary-btn" @click="handleExport('pdf')">导出 PDF</button>
            <button type="button" class="secondary-btn" @click="handleExport('docx')">导出 Word</button>
          </div>
          <router-link to="/jobs" class="primary-btn">再次练习</router-link>
        </footer>
      </template>
    </main>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { exportReport, getImprovementPath, getJobList, getReportDetail, getSessionMessages } from '../api'
import JobLogo from '../components/jobs/JobLogo.vue'
import AppLayout from '../components/layout/AppLayout.vue'
import RadarChart from '../components/ui/RadarChart.vue'
import { getJobPresentation } from '../utils/jobPresentation'

const route = useRoute()
const reportId = route.params.id
const loading = ref(true)
const loadError = ref('')
const totalScore = ref(0)
const summary = ref('')
const jobName = ref('')
const category = ref('')
const directionCode = ref('')
const iconKey = ref('')
const themeKey = ref('')
const startTime = ref('')
const durationSeconds = ref(0)
const dimensions = ref([])
const strengths = ref([])
const weaknesses = ref([])
const suggestions = ref([])
const followupRecords = ref([])
const expandedItems = ref([0])
const displayLevel = ref('')
const profileLabel = ref('')
const radarTargets = ref([])
const moduleScores = ref([])

const radarLabels = computed(() => dimensions.value.map(item => item.dimension))
const radarValues = computed(() => dimensions.value.map(item => item.score))
const isNewReport = computed(() => moduleScores.value.length > 0)
const improvementPriorities = computed(() => moduleScores.value
  .filter(item => Number(item.gapScore) > 0)
  .slice()
  .sort((a, b) => Number(b.improvementPriority || 0) - Number(a.improvementPriority || 0)))
const profileTags = computed(() => profileLabel.value.split(/[,，]/).map(item => item.trim()).filter(Boolean))
const questionCount = computed(() => followupRecords.value.length)
const scoreRank = computed(() => {
  if (totalScore.value >= 85) return '表现突出'
  if (totalScore.value >= 70) return '基础稳健'
  if (totalScore.value >= 60) return '仍有提升空间'
  return '建议重点练习'
})

function clampScore(value) {
  return Math.min(100, Math.max(0, Number(value) || 0))
}

function formatModuleScore(value) {
  return Math.round(Number(value) || 0)
}

function formatPercent(value) {
  const number = Number(value) || 0
  return `${Math.round(number <= 1 ? number * 100 : number)}%`
}

function formatDate(value) {
  if (!value) return '时间未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function formatDuration(seconds) {
  const total = Math.max(0, Number(seconds) || 0)
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  return minutes ? `${minutes} 分 ${rest} 秒` : `${rest} 秒`
}

function normalizeTextList(value) {
  if (Array.isArray(value)) return value.map(item => typeof item === 'string' ? item : item.description || item.dimension).filter(Boolean)
  if (!value) return []
  return String(value).split(/[；;\n]/).map(item => item.trim()).filter(Boolean)
}

function groupMessages(messages) {
  const records = []
  let current = null
  for (const message of messages) {
    if (message.role === 'INTERVIEWER' && message.msgType === 'MAIN') {
      if (current) records.push(current)
      current = {
        question: message.content,
        abilityTag: message.abilityTag || '',
        answer: '',
        followups: [],
        followupAnswers: [],
      }
    } else if (message.role === 'CANDIDATE' && current) {
      if (!current.answer) current.answer = message.content
      else current.followupAnswers.push(message.content)
    } else if (message.role === 'INTERVIEWER' && message.msgType === 'FOLLOWUP' && current) {
      current.followups.push(message.content)
    }
  }
  if (current) records.push(current)
  return records
}

function toggleExpand(index) {
  const found = expandedItems.value.indexOf(index)
  if (found >= 0) expandedItems.value.splice(found, 1)
  else expandedItems.value.push(index)
}

async function handleExport(format) {
  try {
    const response = await exportReport(reportId, format)
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `智面幻境-面试报告.${format === 'docx' ? 'docx' : 'pdf'}`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    window.alert(`导出失败：${error.response?.data?.message || error.message || '请稍后重试'}`)
  }
}

async function loadReport() {
  loading.value = true
  loadError.value = ''
  try {
    const [data, jobs] = await Promise.all([
      getReportDetail(reportId),
      getJobList().catch(() => []),
    ])
    const matchedJob = (jobs || []).find(job => String(job.id) === String(data.jobId))
    const presentation = getJobPresentation(matchedJob || data)
    totalScore.value = Math.round(data.overallMatchScore || data.totalScore || 0)
    summary.value = data.summary || ''
    jobName.value = data.jobName || '岗位模拟面试'
    category.value = data.category || matchedJob?.category || ''
    directionCode.value = data.directionCode || matchedJob?.code || ''
    iconKey.value = presentation.iconKey
    themeKey.value = presentation.themeKey
    startTime.value = data.startTime || ''
    durationSeconds.value = data.actualDurationSeconds || data.durationSeconds || 0
    displayLevel.value = data.displayLevel || ''
    profileLabel.value = data.profileLabel || ''
    moduleScores.value = Array.isArray(data.moduleScores) ? data.moduleScores : []
    dimensions.value = isNewReport.value
      ? moduleScores.value.map(item => ({
          dimension: item.moduleName || item.moduleCode,
          score: Math.round(Number(item.rawScore) || 0),
          explanation: item.suggestion || `目标 ${Math.round(Number(item.targetScore) || 75)} 分，当前差距 ${Math.max(0, Math.round(Number(item.gapScore) || 0))} 分。`,
        }))
      : (data.dimensions || [])
    radarTargets.value = moduleScores.value.map(item => Math.round(Number(item.targetScore) || 75))
    strengths.value = normalizeTextList(data.strengths)
    weaknesses.value = normalizeTextList(data.weaknesses)
    suggestions.value = normalizeTextList(data.suggestions)
    if (isNewReport.value) {
      try {
        const paths = await getImprovementPath(reportId)
        const pathSuggestions = Object.values(paths || {}).flatMap(item => [item.diagnosis, item.actionPlan]).filter(Boolean)
        if (pathSuggestions.length) suggestions.value = pathSuggestions
      } catch (error) {
        console.warn('Failed to load improvement path:', error)
      }
    }
    if (data.sessionId) {
      const messages = await getSessionMessages(data.sessionId)
      followupRecords.value = groupMessages(Array.isArray(messages) ? messages : messages.data || [])
    }
  } catch (error) {
    loadError.value = error.response?.data?.message || '请检查网络连接后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(loadReport)
</script>

<style scoped>
.report-page { max-width: 1180px; margin: 0 auto; padding: 32px 0 72px; color: var(--neutral-900); }
.back-link { display: inline-flex; align-items: center; gap: 6px; margin-bottom: 22px; color: var(--neutral-500); font-size: 14px; text-decoration: none; }
.back-link:hover { color: var(--accent-600); }
.back-link svg, .question-trigger svg { width: 18px; fill: none; stroke: currentColor; stroke-width: 2; }
.state-panel { display: grid; place-items: center; min-height: 360px; color: var(--neutral-500); }
.error-state { gap: 10px; align-content: center; }
.error-state strong { color: var(--neutral-900); font-size: 20px; }
.error-state button { border: 0; border-radius: 10px; padding: 10px 18px; color: white; background: var(--accent-600); cursor: pointer; }
.report-hero { display: flex; align-items: center; justify-content: space-between; gap: 36px; padding: 30px 34px; border: 1px solid #e0eae4; border-radius: 24px 24px 8px 8px; background: linear-gradient(120deg, #fbfdfc 0%, #f3f8f5 100%); }
.role-block { display: flex; align-items: center; gap: 20px; min-width: 0; }
.job-logo { width: 68px; height: 68px; flex: 0 0 68px; }
.eyebrow, .section-kicker { color: #56806d; font-size: 11px; font-weight: 600; letter-spacing: .06em; }
.role-block h1 { margin: 5px 0 8px; font-size: clamp(23px, 2.4vw, 31px); font-weight: 600; line-height: 1.2; }
.meta-line { display: flex; flex-wrap: wrap; gap: 8px 18px; color: var(--neutral-500); font-size: 13px; }
.score-block { display: grid; grid-template-columns: auto auto; align-items: baseline; column-gap: 8px; min-width: 150px; padding-left: 28px; border-left: 1px solid #d8e4dd; }
.score-block span { grid-column: 1 / -1; color: var(--neutral-500); font-size: 13px; }
.score-block strong { color: #2e6b55; font: 600 46px/1 var(--font-display); }
.score-block small { color: var(--neutral-500); }
.conclusion-panel { padding: 34px; border: 1px solid #e0eae4; border-top: 0; border-radius: 0 0 24px 24px; background: #fefefe; }
.conclusion-panel p { max-width: 900px; margin: 10px 0 0; color: #3a554b; font-size: clamp(16px, 1.5vw, 18px); font-weight: 400; line-height: 1.8; }
.profile-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin-top: 20px; }
.profile-tags > span { margin-right: 3px; color: var(--neutral-500); font-size: 12px; }
.profile-tags i { padding: 4px 10px; border: 1px solid #d7e6de; border-radius: 999px; background: #f3f8f5; color: #39705a; font-size: 12px; font-style: normal; }
.analysis-section, .action-section, .review-section { margin-top: 24px; padding: 30px 34px; border: 1px solid var(--neutral-200); border-radius: 20px; background: white; }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 28px; margin-bottom: 26px; }
.section-heading h2, .insight-column h2 { margin: 5px 0 0; font-size: 19px; font-weight: 600; }
.section-heading > p { max-width: 390px; margin: 0; color: var(--neutral-500); font-size: 13px; text-align: right; line-height: 1.6; }
.ability-layout { display: grid; grid-template-columns: minmax(360px, .9fr) 1.1fr; gap: 36px; align-items: center; }
.radar-wrap { min-height: 380px; display: grid; place-items: center; padding: 4px; background: radial-gradient(circle, #f2f7f4 0%, transparent 68%); }
.dimension-list { display: grid; gap: 16px; }
.dimension-row { display: grid; grid-template-columns: 48px 1fr; gap: 14px; }
.dimension-score { color: #3c755f; font: 600 21px/1.2 var(--font-display); }
.dimension-title { display: flex; align-items: baseline; justify-content: space-between; font-size: 13px; }
.dimension-title strong { font-weight: 600; }
.dimension-title span { color: var(--neutral-400); font-size: 11px; }
.score-track { height: 5px; margin: 7px 0 6px; overflow: hidden; border-radius: 9px; background: #edf1ee; }
.score-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #8bb5a2, #4a826b); }
.dimension-row p { margin: 0; color: var(--neutral-500); font-size: 12px; line-height: 1.55; }
.module-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 28px; padding-top: 24px; border-top: 1px solid var(--neutral-100); }
.module-detail { padding: 18px; border-radius: 14px; background: #f8faf9; }
.module-detail__head, .module-detail__scores, .module-detail__meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.module-detail__head strong { font-size: 14px; font-weight: 600; }
.module-detail__head span, .module-detail__scores span, .module-detail__meta { color: var(--neutral-500); font-size: 11px; }
.module-detail__scores { margin-top: 14px; }
.module-track { position: relative; flex: 1; height: 6px; border-radius: 999px; background: #e6ece8; }
.module-track i { display: block; height: 100%; border-radius: inherit; background: #4a826b; }
.module-track b { position: absolute; top: -4px; width: 2px; height: 14px; border-radius: 2px; background: #a7773f; transform: translateX(-1px); }
.module-detail__meta { justify-content: flex-start; margin-top: 10px; }
.module-gap { color: #986d37; }
.module-reached { color: #39705a; }
.module-detail p { margin: 12px 0 0; color: var(--neutral-600); font-size: 12px; line-height: 1.65; }
.module-detail p b { margin-right: 8px; color: #3a554b; font-weight: 600; }
.priority-section { margin-top: 24px; padding: 30px 34px; border: 1px solid var(--neutral-200); border-radius: 20px; background: #fcfaf6; }
.priority-list { display: grid; margin: 0; padding: 0; list-style: none; }
.priority-list li { display: grid; grid-template-columns: 32px minmax(140px, .35fr) 90px 1fr; align-items: center; gap: 14px; padding: 15px 0; border-top: 1px solid #eee7da; }
.priority-list li > span { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; color: #39705a; background: #e7f0eb; font-size: 12px; font-weight: 700; }
.priority-list strong { font-size: 14px; font-weight: 600; }
.priority-list small { color: #986d37; }
.priority-list p { margin: 0; color: var(--neutral-600); font-size: 13px; line-height: 1.6; }
.insight-grid { display: grid; grid-template-columns: 1fr 1fr; margin-top: 24px; overflow: hidden; border: 1px solid var(--neutral-200); border-radius: 20px; background: white; }
.insight-column { padding: 30px 34px; }
.insight-column + .insight-column { border-left: 1px solid var(--neutral-200); }
.insight-column ul { display: grid; gap: 13px; margin: 22px 0 0; padding: 0; list-style: none; }
.insight-column li { position: relative; padding-left: 19px; color: var(--neutral-700); font-size: 14px; line-height: 1.65; }
.insight-column li::before { content: ''; position: absolute; top: .65em; left: 0; width: 7px; height: 7px; border-radius: 50%; background: #4f876e; }
.weakness-column { background: #fcfaf6; }
.weakness-column .section-kicker { color: #986d37; }
.weakness-column li::before { background: #b8884d; }
.compact-heading { margin-bottom: 18px; }
.action-list { margin: 0; padding: 0; list-style: none; }
.action-list li { display: grid; grid-template-columns: 48px 1fr; gap: 16px; padding: 18px 0; border-top: 1px solid var(--neutral-100); }
.action-list span { color: #4f806b; font: 700 14px var(--font-mono); }
.action-list p { margin: 0; color: var(--neutral-700); line-height: 1.7; }
.question-list { display: grid; gap: 10px; }
.question-item { overflow: hidden; border: 1px solid var(--neutral-200); border-radius: 13px; }
.question-item.expanded { border-color: #b7d0c3; }
.question-trigger { display: grid; grid-template-columns: 52px 1fr 20px; align-items: center; gap: 14px; width: 100%; padding: 17px 18px; border: 0; color: inherit; background: white; text-align: left; cursor: pointer; }
.question-trigger:hover { background: #f8faf9; }
.question-index { color: #347058; font: 700 12px var(--font-mono); }
.question-copy { display: grid; gap: 4px; }
.question-copy small { color: var(--neutral-400); font-size: 11px; }
.question-copy strong { font-size: 14px; font-weight: 500; line-height: 1.55; }
.question-trigger svg { color: var(--neutral-400); transition: transform .2s ease; }
.expanded .question-trigger svg { transform: rotate(180deg); }
.answer-body { display: grid; gap: 10px; padding: 4px 18px 18px 84px; background: white; }
.dialogue { padding: 15px 17px; border-radius: 10px; background: #f6f8f7; }
.dialogue.followup { background: #edf5f1; box-shadow: inset 0 0 0 1px #d8e8df; }
.dialogue span { color: var(--neutral-500); font-size: 11px; font-weight: 700; }
.dialogue p { margin: 6px 0 0; color: var(--neutral-700); font-size: 14px; line-height: 1.7; white-space: pre-wrap; }
.empty-review { padding: 40px; color: var(--neutral-400); text-align: center; }
.report-actions { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding-top: 24px; }
.report-actions > div { display: flex; gap: 10px; }
.secondary-btn, .primary-btn { display: inline-flex; align-items: center; justify-content: center; min-height: 42px; padding: 0 18px; border-radius: 10px; font-size: 14px; font-weight: 600; text-decoration: none; cursor: pointer; }
.secondary-btn { border: 1px solid var(--neutral-200); color: var(--neutral-700); background: white; }
.secondary-btn:hover { border-color: #7fa58f; color: #275c46; }
.primary-btn { color: white; background: #3b7b62; }
.primary-btn:hover { color: white; background: #306b55; }
@media (max-width: 860px) {
  .report-page { padding: 20px 0 48px; }
  .report-hero, .section-heading { align-items: flex-start; }
  .report-hero { flex-direction: column; }
  .score-block { width: 100%; padding: 20px 0 0; border-top: 1px solid #d8e4dd; border-left: 0; }
  .ability-layout, .insight-grid, .module-details { grid-template-columns: 1fr; }
  .priority-list li { grid-template-columns: 32px 1fr auto; }
  .priority-list p { grid-column: 2 / -1; }
  .insight-column + .insight-column { border-top: 1px solid var(--neutral-200); border-left: 0; }
  .section-heading { flex-direction: column; }
  .section-heading > p { text-align: left; }
}
@media (max-width: 560px) {
  .report-hero, .conclusion-panel, .analysis-section, .priority-section, .action-section, .review-section, .insight-column { padding: 22px 18px; }
  .role-block { align-items: flex-start; }
  .job-logo { width: 52px; height: 52px; flex-basis: 52px; }
  .meta-line { flex-direction: column; gap: 4px; }
  .radar-wrap { min-height: 300px; }
  .question-trigger { grid-template-columns: 40px 1fr 18px; padding: 15px 12px; }
  .answer-body { padding: 4px 12px 14px; }
  .report-actions { align-items: stretch; flex-direction: column-reverse; }
  .report-actions > div, .primary-btn { width: 100%; }
  .secondary-btn { flex: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .question-trigger svg { transition: none; }
}
</style>
