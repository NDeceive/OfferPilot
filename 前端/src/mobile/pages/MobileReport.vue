<template>
  <MobileShell title="面试报告" :subtitle="report?.jobName || '面试表现复盘'">
    <template v-if="loading">
      <MobileSkeleton variant="stats" label="正在加载本次得分" />
      <MobileSkeleton variant="card" :rows="3" label="正在加载能力明细" />
    </template>

    <MobileState
      v-else-if="loadError"
      kind="error"
      title="报告暂时无法加载"
      :description="loadError"
      action="返回面试记录"
      @action="$router.push('/history')"
    />

    <template v-else>
      <!-- 头部：岗位 + 分数环。桌面版把分数放在右侧小字里，手机屏窄，改成上下排。 -->
      <section class="mrep__hero">
        <div class="mrep__hero-top">
          <div class="mrep__logo"><JobLogo :icon-key="iconKey" :tone="themeKey" /></div>
          <div class="mrep__hero-text">
            <p class="mrep__eyebrow">{{ category || '专项模拟面试' }} · {{ directionCode || '岗位训练' }}</p>
            <h1>{{ jobName }}</h1>
            <p class="mrep__meta">{{ dateText }} · {{ durationText }} · {{ questionCount }} 道主问题</p>
          </div>
        </div>

        <div class="mrep__score">
          <MobileScoreRing
            :value="totalScore"
            :size="92"
            :stroke="7"
            :caption="displayLevel ? '匹配度' : '综合评分'"
          />
          <div class="mrep__score-text">
            <strong>{{ displayLevel || scoreRank }}</strong>
            <p>满分 100 分 · 定位表达与能力证据的完整度</p>
          </div>
        </div>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>本次结论</h2></header>
        <article class="mobile-info-card mrep__conclusion">
          <p>{{ summary || '报告已生成，请结合能力分布与逐题回顾制定下一步练习计划。' }}</p>
          <div v-if="profileTags.length" class="mrep__tags">
            <span>岗位画像</span><i v-for="tag in profileTags" :key="tag">{{ tag }}</i>
          </div>
        </article>
      </section>

      <!--
        能力拆解：桌面版「雷达图 + 维度条 + 模块明细」三块并列，但新报告的 dimensions 本来就是
        从 moduleScores 派生的，两套数字完全重复。手机上合并成一份，每行点开才展开评分依据。
      -->
      <section v-if="dimensions.length" class="mobile-section">
        <header class="mobile-section-heading"><h2>能力拆解</h2><span>{{ dimensions.length }} 项</span></header>

        <div class="mrep__radar">
          <RadarChart
            :labels="radarLabels"
            :values="radarValues"
            :target-values="radarTargets"
            :show-dual-layer="radarTargets.length > 0"
          />
        </div>

        <div class="mrep__abilities">
          <article v-for="(dim, index) in dimensions" :key="index" class="mrep__ability">
            <button
              type="button"
              class="mrep__ability-head"
              :aria-expanded="expandedAbilities.includes(index)"
              @click="toggle(expandedAbilities, index)"
            >
              <span class="mrep__ability-name">{{ dim.dimension }}</span>
              <span class="mrep__ability-score">{{ dim.score }}<small>/100</small></span>
              <span class="mrep__caret" aria-hidden="true">{{ expandedAbilities.includes(index) ? '−' : '+' }}</span>
            </button>

            <div class="mrep__track">
              <i :style="{ width: `${clamp(dim.score)}%` }"></i>
              <b v-if="dim.target != null" :style="{ left: `${clamp(dim.target)}%` }" aria-hidden="true"></b>
            </div>

            <p class="mrep__ability-meta">
              <template v-if="dim.target != null">
                <span>目标 {{ dim.target }} 分</span>
                <span :class="dim.gap > 0 ? 'is-gap' : 'is-ok'">
                  {{ dim.gap > 0 ? `差距 ${dim.gap} 分` : '已达到目标' }}
                </span>
              </template>
              <span v-else>满分 100 分</span>
            </p>

            <div v-if="expandedAbilities.includes(index)" class="mrep__ability-body">
              <p v-if="dim.weight != null" class="mrep__ability-weight">权重 {{ formatPercent(dim.weight) }}</p>
              <p v-if="dim.evidence" class="mrep__ability-copy"><b>评分依据</b>{{ dim.evidence }}</p>
              <p v-if="dim.suggestion" class="mrep__ability-copy"><b>练习建议</b>{{ dim.suggestion }}</p>
              <p v-if="!dim.evidence && !dim.suggestion" class="mrep__ability-copy">
                {{ dim.explanation || '结合逐题记录，继续补充更明确的事实与结果。' }}
              </p>
            </div>
          </article>
        </div>
      </section>

      <section v-if="priorities.length" class="mobile-section">
        <header class="mobile-section-heading"><h2>提升顺序</h2><span>先补影响最大的</span></header>
        <ol class="mrep__priority">
          <li v-for="(module, index) in priorities" :key="module.moduleCode || index">
            <span class="mrep__rank">{{ index + 1 }}</span>
            <div>
              <strong>{{ module.moduleName || module.moduleCode }}</strong>
              <small>差距 {{ Math.round(Number(module.gapScore) || 0) }} 分</small>
              <p>{{ module.suggestion || module.evidence || '结合逐题记录补充事实、行动与结果。' }}</p>
            </div>
          </li>
        </ol>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>表现与短板</h2></header>
        <article class="mrep__insight">
          <h3>值得保留</h3>
          <ul>
            <li v-for="item in strengths" :key="item">{{ item }}</li>
            <li v-if="!strengths.length">暂未形成稳定优势，建议通过更多练习积累有效样本。</li>
          </ul>
        </article>
        <article class="mrep__insight is-warn">
          <h3>下一轮重点</h3>
          <ul>
            <li v-for="item in weaknesses" :key="item">{{ item }}</li>
            <li v-if="!weaknesses.length">暂无明显短板，下一轮可提高回答难度。</li>
          </ul>
        </article>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>行动建议</h2></header>
        <ol class="mrep__actions">
          <li v-for="(item, index) in suggestions" :key="`${index}-${item}`">
            <span>{{ String(index + 1).padStart(2, '0') }}</span><p>{{ item }}</p>
          </li>
          <li v-if="!suggestions.length"><span>01</span><p>回看逐题记录，先重写一段证据不足的回答。</p></li>
        </ol>
      </section>

      <!-- 逐题回顾默认全部收起。展开态是这一页长度的主要来源，手机上先给目录、按需展开。 -->
      <section class="mobile-section">
        <header class="mobile-section-heading">
          <h2>逐题回顾</h2>
          <button v-if="followupRecords.length" type="button" class="mrep__toggle-all" @click="toggleAll">
            {{ allExpanded ? '收起全部' : '展开全部' }}
          </button>
        </header>

        <div v-if="followupRecords.length" class="mrep__questions">
          <article v-for="(record, index) in followupRecords" :key="index" class="mrep__q">
            <button
              type="button"
              class="mrep__q-head"
              :aria-expanded="expandedQuestions.includes(index)"
              @click="toggle(expandedQuestions, index)"
            >
              <span class="mrep__q-index">Q{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="mrep__q-copy">
                <small>{{ record.abilityTag || '综合能力' }}</small>
                <strong>{{ record.question }}</strong>
              </span>
              <span class="mrep__caret" aria-hidden="true">{{ expandedQuestions.includes(index) ? '−' : '+' }}</span>
            </button>

            <div v-if="expandedQuestions.includes(index)" class="mrep__q-body">
              <div class="mrep__dialogue">
                <span>你的回答</span>
                <p>{{ record.answer || '本题未记录到有效回答。' }}</p>
              </div>
              <template v-for="(followup, fi) in record.followups" :key="fi">
                <div class="mrep__dialogue is-followup">
                  <span>面试官追问 {{ fi + 1 }}</span>
                  <p>{{ followup }}</p>
                </div>
                <div v-if="record.followupAnswers[fi]" class="mrep__dialogue">
                  <span>你的补充回答</span>
                  <p>{{ record.followupAnswers[fi] }}</p>
                </div>
              </template>
            </div>
          </article>
        </div>
        <p v-else class="mrep__note">本次面试暂无可回顾的对话记录。</p>
      </section>

      <footer class="mrep__foot">
        <button type="button" class="mobile-secondary-button" :disabled="exporting" @click="handleExport('pdf')">
          {{ exporting ? '导出中…' : '导出 PDF' }}
        </button>
        <button type="button" class="mobile-secondary-button" :disabled="exporting" @click="handleExport('docx')">
          导出 Word
        </button>
        <router-link to="/interview/ai" class="mobile-primary-button">再次练习 <span>→</span></router-link>
      </footer>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { exportReport, getImprovementPath, getJobList, getReportDetail, getSessionMessages } from '../../api'
import JobLogo from '../../components/jobs/JobLogo.vue'
import RadarChart from '../../components/ui/RadarChart.vue'
import { getJobPresentation } from '../../utils/jobPresentation'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import MobileScoreRing from '../components/MobileScoreRing.vue'

const route = useRoute()
/** /report 没有 :id 段，退化成「没编号」而不是去请求 /report/undefined */
const reportId = computed(() => route.params.id || route.query.id || '')

const loading = ref(true)
const exporting = ref(false)
const loadError = ref('')
const report = ref(null)
const followupRecords = ref([])
const expandedAbilities = ref([])
const expandedQuestions = ref([])

/* -------- 字段派生：算法与 HistoryDetail.vue 保持一致，两边数字必须一样 -------- */
const moduleScores = computed(() => (Array.isArray(report.value?.moduleScores) ? report.value.moduleScores : []))
const isNewReport = computed(() => moduleScores.value.length > 0)

const totalScore = computed(() => Math.round(report.value?.overallMatchScore || report.value?.totalScore || 0))
const summary = computed(() => report.value?.summary || '')
const jobName = computed(() => report.value?.jobName || '岗位模拟面试')
const category = computed(() => report.value?.category || '')
const directionCode = computed(() => report.value?.directionCode || '')
const displayLevel = computed(() => report.value?.displayLevel || '')

const presentation = computed(() => getJobPresentation(report.value?.__matchedJob || report.value || {}))
const iconKey = computed(() => presentation.value.iconKey)
const themeKey = computed(() => presentation.value.themeKey)

const profileTags = computed(() =>
  String(report.value?.profileLabel || '').split(/[,，]/).map((item) => item.trim()).filter(Boolean)
)

const scoreRank = computed(() => {
  if (totalScore.value >= 85) return '表现突出'
  if (totalScore.value >= 70) return '基础稳健'
  if (totalScore.value >= 60) return '仍有提升空间'
  return '建议重点练习'
})

/**
 * 新报告用 moduleScores 派生维度，老报告回落到后端给的 dimensions。
 * 新格式额外带上 target/gap/evidence/suggestion，所以下面模板只需处理一种行结构。
 */
const dimensions = computed(() => {
  if (isNewReport.value) {
    return moduleScores.value.map((item) => {
      const target = Math.round(Number(item.targetScore) || 75)
      const gap = Math.max(0, Math.round(Number(item.gapScore) || 0))
      return {
        dimension: item.moduleName || item.moduleCode,
        score: Math.round(Number(item.rawScore) || 0),
        target,
        gap,
        weight: item.baseWeight,
        evidence: item.evidence || '',
        suggestion: item.suggestion || '',
        explanation: `目标 ${target} 分，当前差距 ${gap} 分。`,
      }
    })
  }
  return (report.value?.dimensions || []).map((item) => ({
    dimension: item.dimension,
    score: Math.round(Number(item.score) || 0),
    target: null,
    gap: null,
    weight: null,
    evidence: '',
    suggestion: '',
    explanation: item.explanation || '',
  }))
})

/**
 * 图上只写 4 个字。雷达图的左右两个标签是从顶点往外排的，「项目表达能力」这种
 * 六字名在 393px 下会被裁掉半个字，去掉「能力」后缀正好。完整名字在下面的列表里。
 */
const radarLabels = computed(() =>
  dimensions.value.map((item) => {
    const name = String(item.dimension || '')
    return name.length > 4 && name.endsWith('能力') ? name.slice(0, -2) : name
  })
)
const radarValues = computed(() => dimensions.value.map((item) => item.score))
const radarTargets = computed(() => moduleScores.value.map((item) => Math.round(Number(item.targetScore) || 75)))

const priorities = computed(() =>
  moduleScores.value
    .filter((item) => Number(item.gapScore) > 0)
    .slice()
    .sort((a, b) => Number(b.improvementPriority || 0) - Number(a.improvementPriority || 0))
)

const strengths = computed(() => normalizeTextList(report.value?.strengths))
const weaknesses = computed(() => normalizeTextList(report.value?.weaknesses))
const suggestions = computed(() => normalizeTextList(report.value?.suggestions))
const questionCount = computed(() => followupRecords.value.length)

const dateText = computed(() => {
  const value = report.value?.startTime
  if (!value) return '时间未记录'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '时间未记录'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(d)
})

const durationText = computed(() => {
  const total = Math.max(0, Number(report.value?.actualDurationSeconds || report.value?.durationSeconds) || 0)
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  if (!total) return '时长待记录'
  return minutes ? `${minutes} 分 ${rest} 秒` : `${rest} 秒`
})

const allExpanded = computed(
  () => followupRecords.value.length > 0 && expandedQuestions.value.length === followupRecords.value.length
)

/* -------- 纯函数：与 HistoryDetail.vue 逐字一致 -------- */
function clamp(value) {
  return Math.min(100, Math.max(0, Number(value) || 0))
}

function formatPercent(value) {
  const number = Number(value) || 0
  return `${Math.round(number <= 1 ? number * 100 : number)}%`
}

function normalizeTextList(value) {
  if (Array.isArray(value)) {
    return value.map((item) => (typeof item === 'string' ? item : item.description || item.dimension)).filter(Boolean)
  }
  if (!value) return []
  return String(value).split(/[；;\n]/).map((item) => item.trim()).filter(Boolean)
}

function groupMessages(messages) {
  const records = []
  let current = null
  for (const message of messages) {
    if (message.role === 'INTERVIEWER' && message.msgType === 'MAIN') {
      if (current) records.push(current)
      current = { question: message.content, abilityTag: message.abilityTag || '', answer: '', followups: [], followupAnswers: [] }
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

/** 注意：模板里传进来的 `expandedAbilities` 已经被 Vue 解包成数组本身，不是 ref，所以这里直接用 */
function toggle(list, index) {
  const found = list.indexOf(index)
  if (found >= 0) list.splice(found, 1)
  else list.push(index)
}

function toggleAll() {
  expandedQuestions.value = allExpanded.value ? [] : followupRecords.value.map((_, index) => index)
}

async function handleExport(format) {
  if (exporting.value) return
  exporting.value = true
  try {
    const response = await exportReport(reportId.value, format)
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `智面幻境-面试报告.${format === 'docx' ? 'docx' : 'pdf'}`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    window.alert(`导出失败：${error.response?.data?.message || error.message || '请稍后重试'}`)
  } finally {
    exporting.value = false
  }
}

async function loadReport() {
  loading.value = true
  loadError.value = ''
  if (!reportId.value) {
    loadError.value = '缺少报告编号，请从面试记录里打开某一场的「查看报告」。'
    loading.value = false
    return
  }
  try {
    const [data, jobs] = await Promise.all([getReportDetail(reportId.value), getJobList().catch(() => [])])
    const matchedJob = (jobs || []).find((job) => String(job.id) === String(data.jobId))
    report.value = {
      ...data,
      category: data.category || matchedJob?.category || '',
      directionCode: data.directionCode || matchedJob?.code || '',
      __matchedJob: matchedJob || null,
    }

    // 逐题记录与改进路径都是锦上添花，任一失败不该让整页进错误态
    if (isNewReport.value) {
      try {
        const paths = await getImprovementPath(reportId.value)
        const fromPath = Object.values(paths || {}).flatMap((item) => [item.diagnosis, item.actionPlan]).filter(Boolean)
        if (fromPath.length) report.value = { ...report.value, suggestions: fromPath }
      } catch (error) {
        console.warn('Failed to load improvement path:', error)
      }
    }
    if (data.sessionId) {
      try {
        const messages = await getSessionMessages(data.sessionId)
        followupRecords.value = groupMessages(Array.isArray(messages) ? messages : messages.data || [])
      } catch (error) {
        console.warn('Failed to load session messages:', error)
      }
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
.mrep__hero {
  margin-bottom: 20px;
  padding: 20px 18px;
  background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%);
  border: 1px solid #dcebe1;
  border-radius: var(--m-radius-hero);
  box-shadow: 0 8px 22px rgba(32, 61, 48, .05);
}

.mrep__hero-top { display: flex; align-items: center; gap: 14px; }
.mrep__logo :deep(.job-logo) { width: 52px; height: 52px; border-radius: 15px; }
.mrep__logo :deep(.job-logo svg) { width: 32px; height: 32px; }
.mrep__hero-text { flex: 1; min-width: 0; }
.mrep__eyebrow { color: #56806d; font-size: 11px; font-weight: 700; letter-spacing: .04em; }
.mrep__hero-text h1 { margin-top: 5px; font-size: 19px; font-weight: 800; line-height: 1.28; letter-spacing: -.02em; overflow-wrap: anywhere; }
.mrep__meta { margin-top: 6px; color: var(--m-text-tertiary); font-size: 12px; line-height: 1.5; }

.mrep__score {
  display: flex;
  margin-top: 18px;
  padding-top: 16px;
  align-items: center;
  gap: 16px;
  border-top: 1px dashed #cfe0d6;
}
.mrep__score-text { flex: 1; min-width: 0; }
.mrep__score-text strong { display: block; font-size: 16px; font-weight: 800; letter-spacing: -.02em; }
.mrep__score-text p { margin-top: 5px; color: var(--m-text-secondary); font-size: 12px; line-height: 1.5; }

.mrep__conclusion p { color: var(--m-text); font-size: 14px; line-height: 1.75; }
.mrep__tags { display: flex; margin-top: 14px; align-items: center; flex-wrap: wrap; gap: 7px; }
.mrep__tags span { color: var(--m-text-tertiary); font-size: 11.5px; }
.mrep__tags i { padding: 4px 10px; color: #39705a; background: var(--m-primary-soft); border-radius: 999px; font-size: 11.5px; font-style: normal; }

.mrep__note { color: var(--m-text-tertiary); font-size: 12.5px; line-height: 1.6; }

/* 雷达图的左右标签从顶点往外排、溢出 svg 盒子（overflow:visible），塞满宽度会被裁掉。
   收窄并居中，把两侧留给标签，图例由 RadarChart 自己画。 */
.mrep__radar { padding: 8px 0 4px; }
.mrep__radar :deep(.radar-container) { max-width: 260px; }

.mrep__abilities { display: flex; margin-top: 14px; flex-direction: column; gap: 10px; }
.mrep__ability { padding: 13px 14px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }

.mrep__ability-head { display: flex; width: 100%; min-height: 32px; padding: 0; align-items: center; gap: 10px; background: transparent; border: 0; text-align: left; }
.mrep__ability-name { flex: 1; min-width: 0; color: var(--m-text); font-size: 14px; font-weight: 700; overflow-wrap: anywhere; }
.mrep__ability-score { color: var(--m-primary-dark); font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.mrep__ability-score small { margin-left: 1px; color: var(--m-text-tertiary); font-size: 10px; font-weight: 600; }
.mrep__caret { flex: 0 0 auto; width: 22px; color: var(--m-text-tertiary); text-align: center; font-size: 15px; font-weight: 700; line-height: 1; }

.mrep__track { position: relative; height: 6px; margin: 9px 0 7px; background: #edf1ee; border-radius: 999px; }
.mrep__track i { display: block; height: 100%; background: linear-gradient(90deg, #8bb5a2, #4a826b); border-radius: inherit; }
.mrep__track b { position: absolute; top: -4px; width: 2px; height: 14px; background: var(--m-accent); border-radius: 2px; transform: translateX(-1px); }

.mrep__ability-meta { display: flex; gap: 12px; color: var(--m-text-tertiary); font-size: 11.5px; }
.mrep__ability-meta .is-gap { color: #986d37; font-weight: 700; }
.mrep__ability-meta .is-ok { color: var(--m-primary); font-weight: 700; }

.mrep__ability-body { margin-top: 11px; padding-top: 11px; border-top: 1px dashed var(--m-border); }
.mrep__ability-weight { color: var(--m-text-tertiary); font-size: 11.5px; }
.mrep__ability-copy { margin-top: 8px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.65; }
.mrep__ability-copy:first-child { margin-top: 0; }
.mrep__ability-copy b { margin-right: 7px; color: var(--m-primary-dark); font-weight: 700; }

.mrep__priority { display: flex; margin: 0; padding: 0; flex-direction: column; list-style: none; }
.mrep__priority li { display: grid; padding: 14px 0; grid-template-columns: 26px 1fr; gap: 12px; border-top: 1px solid var(--m-border); }
.mrep__priority li:first-child { padding-top: 0; border-top: 0; }
.mrep__rank { display: grid; width: 24px; height: 24px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 50%; place-items: center; font-size: 12px; font-weight: 800; }
.mrep__priority strong { display: block; font-size: 14px; font-weight: 700; }
.mrep__priority small { display: block; margin-top: 3px; color: #986d37; font-size: 11.5px; font-weight: 700; }
.mrep__priority p { margin-top: 6px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.65; }

.mrep__insight { padding: 16px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mrep__insight + .mrep__insight { margin-top: 12px; }
.mrep__insight.is-warn { background: #fdfaf5; border-color: #f0e4d0; }
.mrep__insight h3 { font-size: 13.5px; font-weight: 800; }
.mrep__insight.is-warn h3 { color: #8a6329; }
.mrep__insight ul { display: flex; margin: 12px 0 0; padding: 0; flex-direction: column; gap: 10px; list-style: none; }
.mrep__insight li { position: relative; padding-left: 16px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.65; }
.mrep__insight li::before { content: ''; position: absolute; top: .62em; left: 0; width: 6px; height: 6px; background: var(--m-primary); border-radius: 50%; }
.mrep__insight.is-warn li::before { background: var(--m-accent); }

.mrep__actions { margin: 0; padding: 0; list-style: none; }
.mrep__actions li { display: grid; padding: 13px 0; grid-template-columns: 34px 1fr; gap: 12px; border-top: 1px solid var(--m-border); }
.mrep__actions li:first-child { padding-top: 0; border-top: 0; }
.mrep__actions span { color: var(--m-primary); font-size: 12.5px; font-weight: 800; letter-spacing: .04em; }
.mrep__actions p { color: var(--m-text-secondary); font-size: 13px; line-height: 1.7; }

.mrep__toggle-all { padding: 6px 10px; color: var(--m-primary); background: var(--m-primary-soft); border: 0; border-radius: 999px; font-size: 12px; font-weight: 700; }
.mrep__questions { display: flex; flex-direction: column; gap: 10px; }
.mrep__q { overflow: hidden; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mrep__q-head { display: grid; width: 100%; padding: 14px; align-items: center; grid-template-columns: 38px 1fr 22px; gap: 10px; background: transparent; border: 0; text-align: left; }
.mrep__q-head:active { background: #f6f8f7; }
.mrep__q-index { color: var(--m-primary); font-size: 12px; font-weight: 800; }
.mrep__q-copy { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.mrep__q-copy small { color: var(--m-text-tertiary); font-size: 11px; }
.mrep__q-copy strong { color: var(--m-text); font-size: 13.5px; font-weight: 600; line-height: 1.55; overflow-wrap: anywhere; }
.mrep__q-body { display: flex; padding: 0 14px 14px; flex-direction: column; gap: 9px; }
.mrep__dialogue { padding: 12px 13px; background: #f6f8f7; border-radius: 10px; }
.mrep__dialogue.is-followup { background: #edf5f1; box-shadow: inset 0 0 0 1px #d8e8df; }
.mrep__dialogue span { color: var(--m-text-tertiary); font-size: 11px; font-weight: 700; }
.mrep__dialogue p { margin-top: 6px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }

.mrep__foot { display: grid; margin-top: 24px; grid-template-columns: 1fr 1fr; gap: 10px; }
.mrep__foot .mobile-primary-button { grid-column: 1 / -1; }
.mrep__foot .mobile-secondary-button { min-height: 44px; }
.mrep__foot button:disabled { opacity: .6; }
</style>
