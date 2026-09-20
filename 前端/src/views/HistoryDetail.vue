<template>
  <AppLayout>
    <div class="detail-page">
    <router-link to="/history" class="back-link">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M19 12H5M12 19l-7-7 7-7"/>
      </svg>
      返回面试记录
    </router-link>

    <div v-if="loading" class="loading-state">
      <p>加载中...</p>
    </div>

    <template v-else>
      <!-- ================================================================ -->
      <!-- 🆕 新版报告：匹配度 + 画像 + 警报 + 双层雷达 + 模块卡片 + 提升路径  -->
      <!-- ================================================================ -->
      <template v-if="isNewReport">
        <!-- Session info bar -->
        <div class="session-info-bar" v-if="startTime">
          <span class="session-info-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
            开始 {{ fmtDate(startTime) }}
          </span>
          <span class="session-info-divider">|</span>
          <span class="session-info-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
            时长 {{ fmtDuration(actualDurationSeconds ?? durationSeconds) }}
          </span>
        </div>

        <!-- Match Hero -->
        <div class="match-hero">
          <div class="match-hero__score">
            <span class="match-hero__label">训练目标匹配度</span>
            <div class="match-hero__num">{{ Math.round(overallMatchScore) }}<i>%</i></div>
            <el-tag :type="matchTagType(displayLevel)" effect="light" size="large">
              {{ displayLevel || '—' }}
            </el-tag>
          </div>
          <div class="match-hero__info">
            <span class="match-hero__job">目标岗位：<strong>{{ jobName }}</strong></span>
            <span v-if="summary" class="match-hero__summary">{{ summary }}</span>
          </div>
        </div>

        <!-- Alert Tags -->
        <div class="alert-tags" v-if="alerts.length">
          <div v-for="(alert, i) in alerts" :key="'alert-' + i" class="alert-tag">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            <span>{{ alert }}</span>
          </div>
        </div>

        <!-- Profile Tags -->
        <div class="profile-tags" v-if="profileLabel">
          <span class="profile-tags__label">匹配画像</span>
          <span v-for="tag in profileTagList" :key="tag" class="profile-tag-chip">{{ tag }}</span>
        </div>

        <!-- Module Radar Grid -->
        <div class="module-radar-grid">
          <div class="radar-panel card">
            <h3 class="card-title">能力雷达</h3>
            <RadarChart
              :labels="moduleLabels"
              :values="moduleValues"
              :targetValues="moduleTargets"
              :showDualLayer="true"
              :size="400"
            />
          </div>

          <div class="module-card-list">
            <div
              v-for="(ms, i) in moduleScores"
              :key="ms.moduleCode"
              class="module-detail-card"
              :style="{ animationDelay: i * 0.06 + 's' }"
            >
              <div class="module-detail-card__head">
                <span class="module-detail-card__name">{{ ms.moduleName }}</span>
                <span class="module-detail-card__weight" v-if="ms.baseWeight">
                  权重 {{ (ms.baseWeight * 100).toFixed(0) }}%
                </span>
              </div>
              <div class="module-detail-card__bar-row">
                <span class="module-detail-card__target">目标 {{ ms.targetScore }}分</span>
                <div class="module-detail-card__track">
                  <div
                    class="module-detail-card__fill"
                    :style="{ width: Math.min(ms.rawScore, 100) + '%', background: moduleBarColor(ms.rawScore) }"
                  />
                  <div
                    class="module-detail-card__marker"
                    :style="{ left: ms.targetScore + '%' }"
                  />
                </div>
                <span class="module-detail-card__actual">{{ ms.rawScore }}分</span>
              </div>
              <div class="module-detail-card__meta">
                <span class="module-detail-card__match">
                  匹配度 <strong>{{ (ms.moduleMatch * 100).toFixed(0) }}%</strong>
                </span>
                <span v-if="ms.gapScore > 0" class="module-detail-card__gap">差距 {{ ms.gapScore }}分</span>
                <span v-else class="module-detail-card__reached">✓ 已达标</span>
              </div>
              <div v-if="ms.evidence" class="module-detail-card__evidence">
                📝 {{ ms.evidence }}
              </div>
              <div v-if="ms.suggestion" class="module-detail-card__suggestion">
                💡 {{ ms.suggestion }}
              </div>
            </div>
          </div>
        </div>

        <!-- Improvement Path -->
        <div class="improvement-path card" v-if="sortedPriorities.length">
          <h3 class="card-title">提升路径</h3>
          <p class="improvement-path__intro">以下是基于你本次面试表现分析出的各模块评估，按优先级排列。</p>
          <div
            v-for="(p, i) in sortedPriorities"
            :key="p.moduleCode"
            class="improvement-item"
            :class="{ 'improvement-item--done': Number(p.gapScore) <= 0 }"
          >
            <!-- 头部：排名 + 名称 + 数据 -->
            <div class="improvement-item__head">
              <span class="improvement-item__idx" :class="{ 'idx-done': Number(p.gapScore) <= 0 }">{{ i + 1 }}</span>
              <span class="improvement-item__name">{{ p.moduleName }}</span>
              <span class="improvement-item__stats">
                <span v-if="Number(p.gapScore) > 0" class="improvement-item__gap">差距 {{ p.gapScore }}分</span>
                <span v-else class="improvement-item__reached-tag">✓ 已达标</span>
              </span>
            </div>
            <!-- 建议文案 -->
            <div class="improvement-item__body">
              <template v-if="Number(p.gapScore) <= 0">
                <p class="improvement-item__text" style="color: #16a76a;">本轮面试中该模块已达到你设定的目标线，表现优秀。继续保持这个水平，可以在下一次面试中设定更高的目标。</p>
              </template>
              <template v-else-if="improvementPathData[p.moduleCode]">
                <p class="improvement-item__text">{{ improvementPathData[p.moduleCode].diagnosis }}</p>
                <p class="improvement-item__text">{{ improvementPathData[p.moduleCode].actionPlan }}</p>
              </template>
              <p v-else class="improvement-item__text">
                {{ p.suggestion || getFallbackSuggestion(p) }}
              </p>
            </div>
          </div>
        </div>
      </template>

      <!-- ================================================================ -->
      <!-- 旧版报告（兼容历史：无 moduleScores 时回退）                        -->
      <!-- ================================================================ -->
      <template v-else>
        <div class="score-hero">
          <div class="score-hero-left">
            <span class="score-label">综合评分</span>
            <div class="score-number">
              <span class="score-value">{{ totalScore }}</span>
              <span class="score-total">/100</span>
            </div>
            <span class="score-rank">{{ getScoreRank(totalScore) }}</span>
          </div>
          <div class="score-meta-grid">
            <div class="meta-item">
              <span class="meta-label">岗位</span>
              <span class="meta-value">{{ jobName }}</span>
            </div>
          </div>
        </div>

        <div class="analysis-grid">
          <div class="card radar-card">
            <h2 class="card-title">能力雷达图</h2>
            <RadarChart :labels="radarLabels" :values="radarValues" />
          </div>
          <div class="sw-column">
            <div class="card">
              <h3 class="card-title sw-title">
                <span class="sw-dot sw-dot-success"></span>表现优势
              </h3>
              <div class="sw-list">
                <div v-for="s in strengths" :key="s.name" class="sw-item">
                  <span class="sw-name">{{ s.name }}</span>
                  <div class="sw-bar-track"><div class="sw-bar sw-bar-success" :style="{ width: s.score + '%' }"></div></div>
                  <span class="sw-score sw-score-success">{{ s.score }}</span>
                </div>
              </div>
            </div>
            <div class="card">
              <h3 class="card-title sw-title">
                <span class="sw-dot sw-dot-warning"></span>待改进项
              </h3>
              <div class="sw-list">
                <div v-for="w in weaknesses" :key="w.name" class="sw-item">
                  <span class="sw-name">{{ w.name }}</span>
                  <div class="sw-bar-track"><div class="sw-bar sw-bar-warning" :style="{ width: w.score + '%' }"></div></div>
                  <span class="sw-score sw-score-warning">{{ w.score }}</span>
                </div>
              </div>
            </div>
            <div class="card">
              <h3 class="card-title sw-title">
                <span class="sw-dot sw-dot-accent"></span>提升建议
              </h3>
              <div class="suggestion-list">
                <div v-for="(s, i) in suggestions" :key="i" class="suggestion-item">
                  <span class="suggestion-num">{{ i + 1 }}</span>
                  <span>{{ s }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>

      <!-- ================================================================ -->
      <!-- 逐题回顾（保持不动）                                               -->
      <!-- ================================================================ -->
      <div class="card followup-card">
        <h2 class="card-title">逐题回顾</h2>
        <div class="followup-list">
          <div v-for="(record, i) in followupRecords" :key="i" class="followup-item" :class="{ expanded: expandedItems.includes(i) }">
            <button class="followup-header" @click="toggleExpand(i)">
              <div class="fh-left">
                <span class="fh-num">Q{{ i + 1 }}</span>
                <span class="fh-text">{{ record.question }}</span>
              </div>
              <div class="fh-right">
                <span v-if="record.abilityTag" class="fh-tag">{{ record.abilityTag }}</span>
                <span v-if="record.score != null" class="fh-score" :class="getScoreClass(record.score)">{{ record.score }}分</span>
                <svg :class="['expand-icon', { rotated: expandedItems.includes(i) }]" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </div>
            </button>
            <div v-if="expandedItems.includes(i)" class="followup-body">
              <div class="fb-section">
                <span class="fb-label">你的回答</span>
                <p v-if="record.answer" class="fb-text">{{ record.answer }}</p>
                <p v-else class="fb-text">{{ i === followupRecords.length - 1 ? '（此题已跳过，面试已结束）' : '（此题已跳过，直接进入下一题）' }}</p>
              </div>
              <div v-for="(fu, fi) in record.followups" :key="fi" class="fb-section fb-followup">
                <span class="fb-label">追问{{ record.followups.length > 1 ? (fi + 1) : '' }}</span>
                <p class="fb-text">{{ fu }}</p>
              </div>
              <div v-if="record.evaluation" class="fb-section fb-evaluation">
                <span class="fb-label">AI 评价</span>
                <p class="fb-text">{{ record.evaluation }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Export Actions -->
      <div class="export-actions">
        <button class="export-btn" @click="handleExport('pdf')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
          </svg>
          导出 PDF
        </button>
        <button class="export-btn" @click="handleExport('docx')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
          </svg>
          导出 Word
        </button>
        <router-link to="/jobs" class="btn-primary">
          再来一次
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
        </router-link>
      </div>
    </template>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import RadarChart from '../components/ui/RadarChart.vue'
import { getReportDetail, getImprovementPath, exportReport, getSessionMessages } from '../api'

const route = useRoute()
const reportId = route.params.id

const loading = ref(true)

// --- Raw response data ---
const totalScore = ref(0)
const summary = ref('')
const jobName = ref('')
const weakTags = ref('')
const sessionId = ref(null)
const startTime = ref(null)
const endTime = ref(null)
const durationSeconds = ref(null)
const actualDurationSeconds = ref(null)

// --- Old report data ---
const radarLabels = ref([])
const radarValues = ref([])
const strengths = ref([])
const weaknesses = ref([])
const suggestions = ref([])

// --- 🆕 New report data ---
const overallMatchScore = ref(0)
const displayLevel = ref('')
const profileLabel = ref('')
const moduleScores = ref([])
const improvementPathData = ref({})  // moduleCode → { diagnosis, actionPlan, tone }

// --- Q&A ---
const followupRecords = ref([])

/* ---------------------------------------------------------------- */
/*  🆕 Computed: is this a new-style report?                         */
/* ---------------------------------------------------------------- */
const isNewReport = computed(() => moduleScores.value.length > 0)

const moduleLabels = computed(() =>
  moduleScores.value.map(m => m.moduleName || m.moduleCode)
)
const moduleValues = computed(() =>
  moduleScores.value.map(m => Number(m.rawScore) || 0)
)
const moduleTargets = computed(() =>
  moduleScores.value.map(m => Number(m.targetScore) || 75)
)

const profileTagList = computed(() => {
  if (!profileLabel.value) return []
  return profileLabel.value.split(',').filter(Boolean)
})

const sortedPriorities = computed(() => {
  // 全部5个模块，有差距的排前面，已达标排后面
  return [...moduleScores.value]
    .sort((a, b) => {
      const aGap = Number(a.gapScore) || 0
      const bGap = Number(b.gapScore) || 0
      if (aGap > 0 && bGap === 0) return -1
      if (aGap === 0 && bGap > 0) return 1
      return (Number(b.improvementPriority) || 0) - (Number(a.improvementPriority) || 0)
    })
})

/**
 * 🆕 警报标记（文档 Section 5.4）：
 * 1. 核心训练目标差距明显：权重最高的模块且 moduleMatch < 0.75
 * 2. 核心突破目标未达成：目标线=85（requirement_level=3）且 moduleMatch < 0.85
 */
const alerts = computed(() => {
  const result = []
  if (!moduleScores.value.length) return result

  // 找权重最高的模块
  const maxWeight = Math.max(...moduleScores.value.map(m => Number(m.baseWeight) || 0))
  const topModule = moduleScores.value.find(m => Number(m.baseWeight) === maxWeight)
  if (topModule && Number(topModule.moduleMatch) < 0.75) {
    result.push('核心训练目标差距明显：' + topModule.moduleName + '模块匹配度不足75%，这是你本轮最关注的维度')
  }

  // 找目标线为85（核心突破）的模块
  const breakthroughModules = moduleScores.value.filter(m => Number(m.targetScore) === 85)
  for (const m of breakthroughModules) {
    if (Number(m.moduleMatch) < 0.85) {
      result.push('核心突破目标未达成：' + m.moduleName + '设为"核心突破"但匹配度未达85%')
    }
  }
  return result
})

/* ---------------------------------------------------------------- */
/*  Helpers                                                          */
/* ---------------------------------------------------------------- */
function getScoreRank(score) {
  if (score >= 85) return '优秀'
  if (score >= 70) return '良好'
  return '中等'
}

function getScoreClass(score) {
  if (score >= 85) return 'score-high'
  if (score >= 70) return 'score-mid'
  return 'score-low'
}

function matchTagType(level) {
  if (!level) return ''
  if (level.includes('已达')) return 'success'
  if (level.includes('接近')) return ''
  if (level.includes('部分')) return 'warning'
  return 'danger'
}

function moduleBarColor(score) {
  const v = Number(score)
  if (v >= 85) return '#16a76a'
  if (v >= 70) return '#2563eb'
  if (v >= 60) return '#f59e0b'
  return '#ef4444'
}

function fmtDate(d) {
  if (!d) return '未记录'
  const dt = new Date(d)
  if (isNaN(dt.getTime())) return '未记录'
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const day = String(dt.getDate()).padStart(2, '0')
  const hh = String(dt.getHours()).padStart(2, '0')
  const mm = String(dt.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${day} ${hh}:${mm}`
}

/** 为无 AI 建议的模块生成兜底提升建议 */
function getFallbackSuggestion(p) {
  const tips = {
    'technical_base': '建议系统复习本岗位核心技术栈的基础知识，重点关注面试中被问及但你未能准确回答的概念。可以通过阅读官方文档、刷 LeetCode 对应专题来巩固。',
    'technical_depth': '建议深入学习相关技术的底层原理和源码实现，不要停留在"会用"的层面。尝试从架构设计、性能优化、边界条件等角度重新审视已掌握的技术。',
    'engineering_practice': '建议多参与实际项目开发，关注工程全链路——从需求分析、编码规范、测试策略到部署运维。可以尝试给开源项目提交 PR 来积累实战经验。',
    'problem_analysis': '建议练习结构化分析问题的能力。面对故障或难题时，遵循"复现→定位→根因→方案→验证"的思路，用文档记录每一次排查过程。',
    'project_expression': '建议使用 STAR 法则（背景-任务-行动-结果）来组织项目描述，提前准备好 2-3 个核心项目的量化成果数据，练习用简洁清晰的语言讲述技术难点和你的贡献。',
    'followup_adaptability': '建议进行模拟追问训练：每次答完一个问题后，预设面试官可能追问的 2-3 个方向，提前准备应对思路。回答被质疑时先承认不足再补充观点。',
    'logical_structure': '建议在回答问题前先花 5 秒搭建框架——明确你要分几点、每点的核心论点是什么。可以使用"首先…其次…最后…"等逻辑连接词来增强条理性。',
    'expression_clarity': '建议用简洁直白的语言表达技术观点，避免使用模糊词汇（"可能""应该""大概"）。可以先写下来再口头练习，录音回听找出表达冗余的地方。',
    'position_cognition': '建议深入研究目标岗位的 JD 和技术栈要求，了解行业趋势和该岗位的核心挑战。关注目标公司的技术博客和公开分享，理解他们的技术选型思路。',
    'project_review': '建议养成定期复盘的习惯——每个项目/迭代结束后，记录做得好的、做得不好的、学到了什么、下次怎么改进。用数据量化复盘结论，而非只凭感觉。',
  }
  return tips[p.moduleCode] || `建议针对"${p.moduleName}"维度进行专项训练，结合本次面试中的反馈，制定一个为期 2 周的学习计划，每天投入 30 分钟进行针对性提升。`
}

function fmtDuration(s) {
  if (s == null) return '未记录'
  const min = Math.floor(s / 60)
  const sec = s % 60
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

/* ---------------------------------------------------------------- */
/*  Q&A grouping (unchanged)                                         */
/* ---------------------------------------------------------------- */
function groupMessages(messages) {
  const records = []
  let current = null

  for (const msg of messages) {
    if (msg.role === 'INTERVIEWER' && msg.msgType === 'MAIN') {
      if (current) records.push(current)
      current = {
        question: msg.content,
        abilityTag: msg.abilityTag || '',
        answer: '',
        followups: [],
        evaluation: '',
        score: null,
      }
    } else if (msg.role === 'CANDIDATE' && current) {
      current.answer = msg.content
    } else if (msg.role === 'INTERVIEWER' && current) {
      if (msg.msgType === 'FOLLOWUP') {
        current.followups.push(msg.content)
      } else {
        current.evaluation = msg.content
      }
    }
  }
  if (current) records.push(current)
  return records
}

/* ---------------------------------------------------------------- */
/*  Export                                                           */
/* ---------------------------------------------------------------- */
async function handleExport(format) {
  try {
    const res = await exportReport(reportId, format)
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `interview-report.${format === 'docx' ? 'docx' : 'pdf'}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    alert('导出失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

/* ---------------------------------------------------------------- */
/*  Mount                                                            */
/* ---------------------------------------------------------------- */
onMounted(async () => {
  try {
    const data = await getReportDetail(reportId)

    // Common fields
    totalScore.value = data.totalScore || 0
    summary.value = data.summary || ''
    jobName.value = data.jobName || ''
    weakTags.value = data.weakTags || ''
    sessionId.value = data.sessionId
    startTime.value = data.startTime
    endTime.value = data.endTime
    durationSeconds.value = data.durationSeconds
    actualDurationSeconds.value = data.actualDurationSeconds

    // 🆕 New report fields
    overallMatchScore.value = data.overallMatchScore || 0
    displayLevel.value = data.displayLevel || ''
    profileLabel.value = data.profileLabel || ''
    moduleScores.value = data.moduleScores || []

    // Old report fields (fallback)
    const dims = data.dimensions || []
    radarLabels.value = dims.map((d) => d.dimension)
    radarValues.value = dims.map((d) => d.score)

    const sorted = [...dims].sort((a, b) => b.score - a.score)
    strengths.value = sorted.slice(0, 3).map((d) => ({ name: d.dimension, score: d.score }))
    weaknesses.value = sorted.slice(-2).reverse().map((d) => ({ name: d.dimension, score: d.score }))
    suggestions.value = data.suggestions || []

    // Q&A messages
    if (data.sessionId) {
      const messages = await getSessionMessages(data.sessionId)
      followupRecords.value = groupMessages(Array.isArray(messages) ? messages : messages.data || [])
    }

    // 🆕 个性化提升建议
    if (reportId && moduleScores.value.length > 0) {
      try {
        const ipData = await getImprovementPath(reportId)
        improvementPathData.value = ipData || {}
      } catch (e) {
        console.warn('Failed to load improvement path:', e)
      }
    }
  } catch (e) {
    console.error('Failed to load report:', e)
  } finally {
    loading.value = false
  }
})

const expandedItems = ref([0])

function toggleExpand(index) {
  const i = expandedItems.value.indexOf(index)
  if (i > -1) expandedItems.value.splice(i, 1)
  else expandedItems.value.push(index)
}
</script>

<style scoped>
.detail-page {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) 0 var(--space-16);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: var(--neutral-400);
  font-size: var(--text-sm);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-6);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-out-expo);
}

.back-link:hover {
  color: var(--accent-600);
}

/* ===================================================================
   🆕 NEW REPORT STYLES (v2 matching evaluation)
   =================================================================== */

/* Session Info Bar */
.session-info-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: var(--space-2) var(--space-4);
  background: var(--neutral-50);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--neutral-500);
}
.session-info-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.session-info-divider {
  color: var(--neutral-300);
}

/* Match Hero */
.match-hero {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  margin-bottom: var(--space-4);
  animation: fade-in-up 0.5s var(--ease-out-expo);
}

.match-hero__score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 22px;
  color: #fff;
  background: linear-gradient(135deg, #10b981, #059669);
  border-radius: 14px;
  box-shadow: 0 16px 36px rgba(16, 185, 129, 0.28);
}

.match-hero__label {
  font-size: 13px;
  font-weight: 700;
  opacity: 0.9;
}

.match-hero__num {
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
}

.match-hero__num i {
  margin-left: 2px;
  font-size: 18px;
  font-style: normal;
  opacity: 0.85;
}

.match-hero__info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 20px 24px;
  background: var(--surface-elevated);
  border-radius: 14px;
  border: 1px solid var(--neutral-200);
}

.match-hero__job {
  font-size: 15px;
  color: var(--neutral-700);
}

.match-hero__summary {
  font-size: 14px;
  color: var(--neutral-500);
  line-height: 1.7;
}

/* Alert Tags */
.alert-tags {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: var(--space-3);
}

.alert-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 10px;
  color: #dc2626;
  font-size: 13px;
  font-weight: 500;
  animation: fade-in-up 0.4s var(--ease-out-expo);
}

.alert-tag svg {
  flex-shrink: 0;
  color: #ef4444;
}

/* Profile Tags */
.profile-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--space-5);
  padding: 14px 20px;
  background: var(--surface-elevated);
  border-radius: 12px;
  border: 1px solid var(--neutral-200);
  flex-wrap: wrap;
}

.profile-tags__label {
  font-size: 13px;
  font-weight: 700;
  color: var(--neutral-500);
  margin-right: 6px;
}

.profile-tag-chip {
  font-size: 13px;
  font-weight: 500;
  padding: 4px 14px;
  border-radius: 20px;
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
}

/* Module Radar Grid */
.module-radar-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: var(--space-5);
}

.radar-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* Module Detail Cards */
.module-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow-y: auto;
}

.module-detail-card {
  padding: 16px 18px;
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: 12px;
  animation: card-fade-in 0.4s var(--ease-out) backwards;
}

@keyframes card-fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.module-detail-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.module-detail-card__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--neutral-900);
}

.module-detail-card__weight {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--accent-600);
  background: var(--accent-50);
  padding: 2px 8px;
  border-radius: 10px;
}

.module-detail-card__bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.module-detail-card__target {
  font-size: 11px;
  color: var(--neutral-400);
  min-width: 60px;
}

.module-detail-card__track {
  flex: 1;
  height: 8px;
  background: var(--neutral-100);
  border-radius: 4px;
  position: relative;
  overflow: visible;
}

.module-detail-card__fill {
  height: 100%;
  border-radius: 4px;
  transition: width 1s var(--ease-out-expo);
}

.module-detail-card__marker {
  position: absolute;
  top: -4px;
  width: 3px;
  height: 16px;
  background: #f59e0b;
  border-radius: 2px;
}

.module-detail-card__actual {
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--neutral-900);
  min-width: 40px;
  text-align: right;
}

.module-detail-card__meta {
  display: flex;
  gap: 16px;
  margin-bottom: 6px;
}

.module-detail-card__match {
  font-size: 13px;
  color: var(--neutral-600);
}

.module-detail-card__match strong {
  color: var(--accent-600);
}

.module-detail-card__gap {
  font-size: 13px;
  color: #f59e0b;
}

.module-detail-card__reached {
  font-size: 13px;
  color: var(--accent-600);
  font-weight: 600;
}

.module-detail-card__evidence,
.module-detail-card__suggestion {
  font-size: 12px;
  color: var(--neutral-500);
  line-height: 1.6;
  margin-top: 4px;
}

/* Improvement Path */
.improvement-path {
  margin-bottom: var(--space-5);
  padding: 24px 28px;
  border-radius: 14px;
}

.improvement-path h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-800);
  margin-bottom: 6px;
}

.improvement-path__intro {
  font-size: 13px;
  color: var(--neutral-500);
  margin-bottom: 18px;
  line-height: 1.6;
}

.improvement-item {
  padding: 16px 0;
  border-top: 1px solid var(--neutral-100);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.improvement-item:first-child { border-top: none; padding-top: 0; }

.improvement-item__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.improvement-item__idx {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--accent-50);
  color: var(--accent-700);
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.improvement-item__name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-800);
}

.improvement-item__stats {
  display: flex;
  gap: 14px;
  flex-shrink: 0;
}

.improvement-item__gap {
  font-size: 12px;
  color: #f59e0b;
  font-family: var(--font-mono);
  font-weight: 500;
}

.improvement-item__priority {
  font-size: 12px;
  color: var(--neutral-400);
  font-family: var(--font-mono);
}

.improvement-item__reached-tag {
  font-size: 12px;
  font-weight: 600;
  color: #16a76a;
}

.idx-done {
  background: #d1fae5 !important;
  color: #059669 !important;
}

.improvement-item--done {
  background: rgba(16, 185, 129, 0.03);
}

.improvement-item__body {
  padding-left: 38px;  /* 对齐到排名圈右侧 */
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.improvement-item__text {
  font-size: 13px;
  color: var(--neutral-600);
  line-height: 1.7;
  margin: 0;
}

/* ===================================================================
   OLD REPORT STYLES (fallback for historical data)
   =================================================================== */

.score-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-8) var(--space-10);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-6);
  position: relative;
  overflow: hidden;
  animation: fade-in-up 0.5s var(--ease-out-expo);
}

.score-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--accent-400), var(--accent-600));
}

.score-hero-left {
  display: flex;
  flex-direction: column;
}

.score-label {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-2);
  font-weight: 500;
}

.score-number {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
}

.score-value {
  font-family: var(--font-mono);
  font-size: 4.5rem;
  font-weight: 700;
  color: var(--accent-500);
  line-height: 1;
}

.score-total {
  font-size: var(--text-2xl);
  color: var(--neutral-400);
}

.score-rank {
  display: inline-block;
  margin-top: var(--space-3);
  padding: var(--space-1) var(--space-4);
  background: var(--accent-50);
  color: var(--accent-600);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 600;
  width: fit-content;
}

.score-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5) var(--space-8);
}

.meta-item {
  text-align: right;
}

.meta-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--neutral-400);
  margin-bottom: 2px;
  font-weight: 500;
}

.meta-value {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
}

/* Cards */
.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  animation: fade-in-up 0.5s var(--ease-out-expo) 0.1s backwards;
}

.card-title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-5);
}

/* Analysis Grid (old) */
.analysis-grid {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: var(--space-6);
  margin-bottom: var(--space-6);
}

.sw-column {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.sw-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.sw-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.sw-dot-success { background: var(--accent-500); }
.sw-dot-warning { background: #d97706; }
.sw-dot-accent { background: var(--accent-500); }

.sw-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.sw-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.sw-name {
  width: 72px;
  font-size: var(--text-sm);
  color: var(--neutral-600);
  flex-shrink: 0;
}

.sw-bar-track {
  flex: 1;
  height: 6px;
  background: var(--neutral-100);
  border-radius: 3px;
  overflow: hidden;
}

.sw-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 1s var(--ease-out-expo);
}

.sw-bar-success { background: var(--accent-500); }
.sw-bar-warning { background: #d97706; }

.sw-score {
  width: 32px;
  text-align: right;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 600;
}

.sw-score-success { color: var(--accent-500); }
.sw-score-warning { color: #d97706; }

/* Suggestions (old) */
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.suggestion-item {
  display: flex;
  gap: var(--space-3);
  font-size: var(--text-sm);
  color: var(--neutral-600);
  line-height: 1.6;
}

.suggestion-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-50);
  color: var(--accent-600);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

/* ===================================================================
   FOLLOW-UP RECORDS (KEPT UNCHANGED)
   =================================================================== */

.followup-card {
  margin-bottom: var(--space-6);
}

.followup-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.followup-item {
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-out-expo);
}

.followup-item:hover {
  border-color: var(--neutral-300);
}

.followup-item.expanded {
  border-color: var(--accent-300);
}

.followup-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4);
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
  font-family: var(--font-body);
  transition: background var(--duration-fast) var(--ease-out-expo);
}

.followup-header:hover {
  background: var(--neutral-50);
}

.fh-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
}

.fh-num {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-600);
  padding: 2px 8px;
  background: var(--accent-50);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.fh-text {
  font-size: var(--text-sm);
  color: var(--neutral-800);
  white-space: normal;
  word-break: break-word;
  line-height: 1.6;
}

.fh-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.fh-score {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 700;
}

.score-high { color: var(--accent-500); }
.score-mid { color: #d97706; }
.score-low { color: #ef4444; }

.fh-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--neutral-100);
  color: var(--neutral-600);
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.expand-icon {
  color: var(--neutral-400);
  transition: transform var(--duration-normal) var(--ease-out-expo);
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.followup-body {
  padding: 0 var(--space-4) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  animation: fade-in 0.2s var(--ease-out-expo);
}

.fb-section {
  padding: var(--space-4);
  background: var(--neutral-50);
  border-radius: var(--radius-sm);
}

.fb-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--neutral-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-2);
}

.fb-text {
  font-size: var(--text-sm);
  color: var(--neutral-700);
  line-height: 1.7;
}

.fb-skipped {
  color: var(--neutral-400);
  font-style: italic;
}

.fb-evaluation {
  border-left: 3px solid var(--accent-400);
  background: var(--accent-50);
}

.fb-followup {
  border-left: 3px solid var(--accent-200);
  margin-left: var(--space-4);
}

/* ===================================================================
   EXPORT ACTIONS (KEPT UNCHANGED)
   =================================================================== */

.export-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
  animation: fade-in-up 0.5s var(--ease-out-expo) 0.2s backwards;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-expo);
}

.export-btn:hover {
  border-color: var(--accent-300);
  color: var(--accent-600);
  background: var(--accent-50);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  background: var(--accent-500);
  color: white;
  font-size: var(--text-sm);
  font-weight: 600;
  border-radius: var(--radius-md);
  text-decoration: none;
  transition: all var(--duration-normal) var(--ease-out-expo);
}

.btn-primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-accent);
  color: white;
}

/* ===================================================================
   KEYFRAMES
   =================================================================== */

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

/* ===================================================================
   RESPONSIVE
   =================================================================== */

@media (max-width: 980px) {
  .module-radar-grid {
    grid-template-columns: 1fr;
  }
  .match-hero {
    grid-template-columns: 1fr;
  }
  .analysis-grid {
    grid-template-columns: 1fr;
  }

  .score-hero {
    flex-direction: column;
    gap: var(--space-6);
    text-align: center;
    padding: var(--space-6);
  }

  .score-hero-left {
    align-items: center;
  }

  .meta-item {
    text-align: center;
  }

  .export-actions {
    flex-wrap: wrap;
  }
}

@media (max-width: 768px) {
  .export-actions {
    flex-wrap: wrap;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .score-hero,
  .card,
  .export-actions,
  .module-detail-card {
    animation: none;
  }

  .followup-body {
    animation: none;
  }

  .expand-icon {
    transition: none;
  }

  .sw-bar,
  .module-detail-card__fill {
    transition: none;
  }
}
</style>
