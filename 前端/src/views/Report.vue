<template>
  <div class="report-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><DataAnalysis /></el-icon>
        能力评估
      </span>
      <h2>能力报告</h2>
      <p>基于本次模拟面试表现的多维能力分析，含五维雷达图、优势与改进建议。</p>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" v-loading="loading" class="state-card glass-panel">
      <p>报告加载中…</p>
    </div>

    <!-- 错误 / 空态 -->
    <div v-else-if="error" class="state-card glass-panel">
      <el-icon :size="46"><WarningFilled /></el-icon>
      <p>{{ error }}</p>
      <div class="state-actions">
        <el-button @click="router.push('/history')">查看历史记录</el-button>
        <el-button type="primary" @click="router.push('/jobs')">去完成一次面试</el-button>
      </div>
    </div>

    <!-- 报告正文 -->
    <template v-else-if="report">

      <!-- ================================================================ -->
      <!-- 🆕 新版报告：匹配度+画像+双层雷达+模块卡片+提升路径               -->
      <!-- ================================================================ -->
      <template v-if="isNewReport">
        <!-- 匹配度横幅 -->
        <div class="match-hero">
          <div class="match-hero__score">
            <span class="match-hero__label">训练目标匹配度</span>
            <div class="match-hero__num">{{ Math.round(report.overallMatchScore) }}<i>%</i></div>
            <el-tag :type="matchTagType(report.displayLevel)" effect="light" size="large">
              {{ report.displayLevel || '—' }}
            </el-tag>
          </div>
          <div class="match-hero__info">
            <span class="match-hero__job">目标岗位：<strong>{{ report.jobName }}</strong></span>
            <span v-if="report.summary" class="match-hero__summary">{{ report.summary }}</span>
          </div>
        </div>

        <!-- 匹配画像标签 -->
        <div class="profile-tags" v-if="report.profileLabel">
          <span class="profile-tags__label">匹配画像</span>
          <span v-for="tag in profileTagList" :key="tag" class="profile-tag-chip">{{ tag }}</span>
        </div>

        <!-- 双层雷达 + 模块卡片 -->
        <div class="module-radar-grid">
          <div class="radar-panel glass-panel">
            <h3>能力雷达</h3>
            <RadarChart
              :labels="moduleLabels"
              :values="moduleValues"
              :targetValues="moduleTargets"
              :showDualLayer="true"
              :size="340"
            />
          </div>

          <div class="module-card-list">
            <div
              v-for="(ms, i) in report.moduleScores"
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

        <!-- 提升路径 -->
        <div class="improvement-path glass-panel" v-if="sortedPriorities.length">
          <h3>提升路径</h3>
          <div
            v-for="(p, i) in sortedPriorities"
            :key="p.moduleCode"
            class="improvement-item"
          >
            <span class="improvement-item__idx">{{ i + 1 }}</span>
            <span class="improvement-item__name">{{ p.moduleName }}</span>
            <span class="improvement-item__gap">差距 {{ p.gapScore }}分</span>
            <span class="improvement-item__priority">优先级 {{ (p.improvementPriority || 0).toFixed(1) }}</span>
          </div>
        </div>
      </template>

      <!-- ================================================================ -->
      <!-- 旧版报告（兼容历史）                                              -->
      <!-- ================================================================ -->
      <template v-else>
        <div class="overview-grid">
          <div class="score-card">
            <span class="score-label">综合得分</span>
            <div class="score-value">{{ formatScore(report.totalScore) }}<i>分</i></div>
            <el-tag :type="scoreTagType(report.totalScore)" effect="light" size="large">
              {{ scoreBand(report.totalScore) }}
            </el-tag>
            <p class="score-job">目标岗位：<strong>{{ report.jobName }}</strong></p>
          </div>
          <div class="summary-card glass-panel">
            <h3><el-icon><Memo /></el-icon>综合评价</h3>
            <p class="summary-text">{{ report.summary }}</p>
            <div v-if="report.weakTags" class="weak-tags">
              <span class="weak-tags-label">薄弱维度：</span>
              <el-tag v-for="tag in weakTagList" :key="tag" type="warning" effect="plain" size="small" class="weak-tag">{{ tag }}</el-tag>
            </div>
          </div>
        </div>
        <div class="radar-grid">
          <div class="radar-card glass-panel">
            <h3><el-icon><Aim /></el-icon>五维能力雷达</h3>
            <div ref="radarRef" class="radar-chart"></div>
          </div>
          <div class="dimension-list">
            <div v-for="dim in report.dimensions" :key="dim.dimension" class="dimension-card">
              <div class="dimension-head">
                <span class="dimension-name">{{ dim.dimension }}</span>
                <div class="dimension-score">
                  <strong>{{ formatScore(dim.score) }}</strong>
                  <el-tag :type="levelTagType(dim.level)" effect="light" size="small">{{ dim.level }}</el-tag>
                </div>
              </div>
              <el-progress :percentage="Number(dim.score)" :stroke-width="8" :color="barColor(dim.score)" :show-text="false" />
              <p class="dimension-explain">{{ dim.explanation }}</p>
            </div>
          </div>
        </div>
        <div class="insight-grid">
          <div class="insight-card glass-panel strengths">
            <h3><el-icon><CircleCheckFilled /></el-icon>表现优势</h3>
            <ul><li v-for="(s, i) in report.strengths" :key="'s' + i">{{ s }}</li></ul>
          </div>
          <div class="insight-card glass-panel weaknesses">
            <h3><el-icon><WarningFilled /></el-icon>待改进项</h3>
            <ul><li v-for="(w, i) in report.weaknesses" :key="'w' + i">{{ w }}</li></ul>
          </div>
          <div class="insight-card glass-panel suggestions">
            <h3><el-icon><MagicStick /></el-icon>提升建议</h3>
            <ul><li v-for="(g, i) in report.suggestions" :key="'g' + i">{{ g }}</li></ul>
          </div>
        </div>
      </template>

      <!-- 问答记录 -->
      <div class="qa-section" v-if="report?.sessionId">
        <div class="insight-card glass-panel">
          <h3><el-icon><ChatLineRound /></el-icon>面试问答记录（共 {{ qaRounds.length }} 题）</h3>
          <div v-loading="qaLoading">
            <div v-if="qaRounds.length" class="qa-rounds">
              <el-collapse v-model="activeRounds">
                <el-collapse-item v-for="(r, i) in qaRounds" :key="r.roundNo" :name="String(r.roundNo)">
                  <template #title>
                    <div class="round-header">
                      <strong>第 {{ r.roundNo }} 题</strong>
                      <el-tag v-if="r.abilityTag" size="small" effect="plain" type="primary">{{ r.abilityTag }}</el-tag>
                      <el-tag v-if="r.questionType === 'EXPERIENCE'" size="small" type="warning" effect="dark">经历题</el-tag>
                      <span v-if="r.hasFollowup" class="round-followup-badge">含追问</span>
                    </div>
                  </template>
                  <div class="round-body">
                    <div class="round-msg interviewer">
                      <span class="round-msg-label">面试官提问</span>
                      <p>{{ r.question?.content }}</p>
                    </div>
                    <div v-if="r.mainAnswer?.content" class="round-msg candidate">
                      <span class="round-msg-label">你的回答</span>
                      <p>{{ r.mainAnswer.content }}</p>
                    </div>
                    <div v-else class="round-msg candidate skipped">
                      <span class="round-msg-label">你的回答</span>
                      <p>{{ i === qaRounds.length - 1 ? '（此题已跳过，面试已结束）' : '（此题已跳过，直接进入下一题）' }}</p>
                    </div>
                    <template v-if="r.followup">
                      <div class="round-msg interviewer followup">
                        <span class="round-msg-label">追问</span>
                        <p>{{ r.followup.content }}</p>
                      </div>
                      <div v-if="r.followupAnswer" class="round-msg candidate followup">
                        <span class="round-msg-label">追问回答</span>
                        <p>{{ r.followupAnswer.content }}</p>
                      </div>
                    </template>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
            <el-empty v-else-if="!qaLoading" description="暂无问答记录" />
          </div>
        </div>
      </div>

      <!-- 操作 -->
      <div class="report-actions">
        <el-button :icon="HomeFilled" @click="router.push('/home')">返回首页</el-button>
        <el-button :icon="Tickets" @click="router.push('/history')">查看历史记录</el-button>
        <el-button type="primary" :icon="RefreshRight" @click="retrain">再次训练</el-button>
        <el-divider direction="vertical" style="height: 28px;" />
        <el-button :icon="Download" @click="handleExport('pdf')" :loading="exportingPdf">
          导出 PDF
        </el-button>
        <el-button :icon="Download" @click="handleExport('docx')" :loading="exportingDocx">
          导出 Word
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import {
  Aim,
  ChatLineRound,
  CircleCheckFilled,
  DataAnalysis,
  Download,
  HomeFilled,
  MagicStick,
  Memo,
  RefreshRight,
  Tickets,
  WarningFilled
} from '@element-plus/icons-vue'
import { getReportDetail, getInterviewRecords, exportReport, getSessionMessages } from '@/api'
import RadarChart from '@/components/ui/RadarChart.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const report = ref(null)

/* ---------------------------------------------------------------- */
/*  🆕 New report helpers                                           */
/* ---------------------------------------------------------------- */
const isNewReport = computed(() => report.value?.moduleScores?.length > 0)

const moduleLabels = computed(() =>
  (report.value?.moduleScores || []).map(m => m.moduleName || m.moduleCode)
)
const moduleValues = computed(() =>
  (report.value?.moduleScores || []).map(m => Number(m.rawScore) || 0)
)
const moduleTargets = computed(() =>
  (report.value?.moduleScores || []).map(m => Number(m.targetScore) || 75)
)

const profileTagList = computed(() => {
  if (!report.value?.profileLabel) return []
  return report.value.profileLabel.split(',').filter(Boolean)
})

const sortedPriorities = computed(() => {
  const list = (report.value?.moduleScores || [])
    .filter(m => Number(m.gapScore) > 0)
    .sort((a, b) => (Number(b.improvementPriority) || 0) - (Number(a.improvementPriority) || 0))
  return list
})

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

// Q&A 历史
const qaMessages = ref([])
const qaLoading = ref(false)
const activeRounds = ref([])

// 将平铺消息按题目轮次分组
const qaRounds = computed(() => {
  const rounds = []
  let cur = null
  for (const msg of qaMessages.value) {
    if (msg.role === 'INTERVIEWER' && msg.msgType === 'MAIN') {
      cur = {
        roundNo: msg.roundNo,
        abilityTag: msg.abilityTag,
        questionType: msg.questionType,
        question: msg,
        mainAnswer: null,
        followup: null,
        followupAnswer: null,
        hasFollowup: false
      }
      rounds.push(cur)
    } else if (cur) {
      if (msg.role === 'INTERVIEWER' && msg.msgType === 'FOLLOWUP') {
        cur.followup = msg
        cur.hasFollowup = true
      } else if (msg.role === 'CANDIDATE' && msg.msgType === 'ANSWER') {
        if (cur.followup && !cur.followupAnswer) {
          cur.followupAnswer = msg
        } else if (!cur.mainAnswer) {
          cur.mainAnswer = msg
        }
      }
    }
  }
  return rounds
})

const radarRef = ref(null)
let chart = null

const weakTagList = computed(() =>
  report.value?.weakTags ? report.value.weakTags.split(',').filter(Boolean) : []
)

const formatScore = (s) => (s == null ? '—' : Number(s).toFixed(1))

const scoreBand = (s) => {
  const v = Number(s)
  if (v >= 85) return '优秀'
  if (v >= 70) return '良好'
  if (v >= 60) return '合格'
  return '待提升'
}
const scoreTagType = (s) => {
  const v = Number(s)
  if (v >= 85) return 'success'
  if (v >= 70) return ''
  if (v >= 60) return 'warning'
  return 'danger'
}
const levelTagType = (level) =>
  ({ 优秀: 'success', 良好: '', 合格: 'warning', 待提升: 'danger' }[level] || 'info')
const barColor = (s) => {
  const v = Number(s)
  if (v >= 85) return '#16a76a'
  if (v >= 70) return '#2563eb'
  if (v >= 60) return '#f59e0b'
  return '#ef4444'
}

const renderRadar = () => {
  if (!radarRef.value || !report.value) return
  const dims = report.value.dimensions || []
  chart = echarts.init(radarRef.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      indicator: dims.map((d) => ({ name: d.dimension, max: 100 })),
      radius: '66%',
      axisName: { color: '#667085', fontSize: 12 },
      splitLine: { lineStyle: { color: 'rgba(37, 99, 235, 0.12)' } },
      splitArea: { areaStyle: { color: ['rgba(37,99,235,0.03)', 'rgba(37,99,235,0.07)'] } },
      axisLine: { lineStyle: { color: 'rgba(37, 99, 235, 0.18)' } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: dims.map((d) => Number(d.score)),
            name: '能力得分',
            symbol: 'circle',
            symbolSize: 5,
            lineStyle: { color: '#2563eb', width: 2 },
            itemStyle: { color: '#2563eb' },
            areaStyle: { color: 'rgba(37, 99, 235, 0.22)' }
          }
        ]
      }
    ]
  })
}

const resizeChart = () => chart && chart.resize()

const retrain = () => {
  // 复训：带上薄弱标签回到岗位选择（Phase 1 流程不变，仅多带参数）
  const query = {}
  if (report.value?.weakTags) query.weakTags = report.value.weakTags
  router.push({ path: '/jobs', query })
}

// ---------- 报告导出 ----------
import { ElMessage } from 'element-plus'

const exportingPdf = ref(false)
const exportingDocx = ref(false)

const handleExport = async (format) => {
  const loadingRef = format === 'pdf' ? exportingPdf : exportingDocx
  if (loadingRef.value) return // 防重复点击
  loadingRef.value = true
  try {
    const blob = await exportReport(report.value.reportId, format)
    const ext = format === 'pdf' ? 'pdf' : 'docx'
    const safeJobName = (report.value.jobName || '报告').replace(/[\\/:*?"<>|]/g, '_')
    const fileName = `面试报告_${safeJobName}_${report.value.reportId}.${ext}`
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    // 错误已在 download.js 拦截器中处理（ElMessage.error），此处仅防重复点击解锁
  } finally {
    loadingRef.value = false
  }
}

const loadReport = async (reportId) => {
  try {
    report.value = await getReportDetail(reportId)
    // 同步加载问答历史
    if (report.value?.sessionId) {
      qaLoading.value = true
      try {
        qaMessages.value = await getSessionMessages(report.value.sessionId)
      } finally {
        qaLoading.value = false
      }
    }
    await nextTick()
    renderRadar()
    window.addEventListener('resize', resizeChart)
  } catch (e) {
    error.value = '报告加载失败，可能不存在或无权访问。'
  }
}

onMounted(async () => {
  try {
    let reportId = route.query.reportId
    // 直接打开 /report（无 reportId）时，回退到最近一次「已完成且已生成报告」的记录
    if (!reportId) {
      const records = await getInterviewRecords()
      const finished = (records || [])
        .filter((r) => r.status === 'FINISHED' && r.reportId)
        .sort((a, b) => new Date(b.startTime) - new Date(a.startTime))
      if (finished.length) {
        reportId = finished[0].reportId
        // 直接加载最新报告，不修改 URL（避免挂载期间导航导致路由异常）
      } else {
        error.value = '还没有可查看的报告，先去完成一次面试吧。'
        return
      }
    }
    await loadReport(reportId)
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<style scoped>
.report-page {
  padding-top: 12px;
  padding-bottom: 24px;
}

.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 56px 32px;
  margin-top: 20px;
  text-align: center;
  color: var(--text-muted);
  border-radius: var(--radius);
}

.state-card .el-icon {
  color: var(--warning);
}

.state-actions {
  display: flex;
  gap: 12px;
}

/* 总分 + 概览 */
.overview-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  margin-top: 20px;
}

.score-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 28px 22px;
  color: #fff;
  background: linear-gradient(135deg, #2563eb, #6d4aff);
  border-radius: 14px;
  box-shadow: 0 16px 36px rgba(37, 99, 235, 0.28);
}

.score-label {
  font-size: 13px;
  font-weight: 700;
  opacity: 0.9;
}

.score-value {
  font-size: 56px;
  font-weight: 800;
  line-height: 1;
}

.score-value i {
  margin-left: 4px;
  font-size: 18px;
  font-style: normal;
  opacity: 0.9;
}

.score-job {
  margin-top: 4px;
  font-size: 13px;
  opacity: 0.92;
}

.summary-card {
  padding: 24px;
  border-radius: 14px;
}

.summary-card h3,
.radar-card h3,
.insight-card h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--text);
  font-size: 16px;
}

.summary-text {
  color: var(--text);
  font-size: 15px;
  line-height: 1.9;
}

.weak-tags {
  margin-top: 16px;
}

.weak-tags-label {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.weak-tag {
  margin-right: 8px;
}

/* 雷达图 + 维度卡 */
.radar-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
  margin-top: 20px;
}

.radar-card {
  padding: 22px 24px;
  border-radius: 14px;
}

.radar-chart {
  width: 100%;
  height: 360px;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dimension-card {
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.dimension-name {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.dimension-score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dimension-score strong {
  color: var(--primary-dark);
  font-size: 18px;
  font-weight: 800;
}

.dimension-explain {
  margin-top: 10px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
}

/* 优势 / 不足 / 建议 */
.insight-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.insight-card {
  padding: 22px;
  border-radius: 14px;
}

.insight-card ul {
  padding-left: 2px;
  list-style: none;
}

.insight-card li {
  position: relative;
  padding: 8px 0 8px 18px;
  color: var(--text);
  font-size: 14px;
  line-height: 1.7;
  border-top: 1px dashed rgba(220, 230, 242, 0.95);
}

.insight-card li:first-child {
  border-top: none;
}

.insight-card li::before {
  position: absolute;
  top: 14px;
  left: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  content: '';
}

.strengths h3 .el-icon { color: #16a76a; }
.strengths li::before { background: #16a76a; }
.weaknesses h3 .el-icon { color: var(--warning); }
.weaknesses li::before { background: var(--warning); }
.suggestions h3 .el-icon { color: var(--primary); }
.suggestions li::before { background: var(--primary); }

/* Q&A 历史 */
.qa-section {
  margin-top: 20px;
}

.qa-rounds {
  /* 不设 max-height，让页面自身滚动，避免截断长内容 */
}

/* 折叠项悬浮效果 */
.qa-rounds :deep(.el-collapse-item__header) {
  cursor: pointer;
  padding: 12px 16px;
  border-radius: 10px;
  transition: background 0.2s, box-shadow 0.2s;
  font-weight: 600;
  user-select: none;
}

.qa-rounds :deep(.el-collapse-item__header:hover) {
  background: rgba(37, 99, 235, 0.06);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.qa-rounds :deep(.el-collapse-item__arrow) {
  font-size: 16px;
  color: var(--primary);
  margin-right: 6px;
}

.qa-rounds :deep(.el-collapse-item__wrap) {
  border: none;
}

.qa-rounds :deep(.el-collapse-item) {
  margin-bottom: 6px;
  border: 1px solid rgba(220, 230, 242, 0.8);
  border-radius: 10px;
  overflow: hidden;
}

.round-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.round-header strong {
  color: var(--text);
  font-size: 15px;
}

.round-followup-badge {
  color: #8c57ff;
  font-size: 12px;
  font-weight: 700;
}

.round-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 4px;
}

.round-msg {
  padding: 12px 16px;
  border-radius: 10px;
}

.round-msg.interviewer {
  background: rgba(37, 99, 235, 0.05);
  border: 1px solid rgba(37, 99, 235, 0.12);
}

.round-msg.candidate {
  background: rgba(22, 167, 106, 0.05);
  border: 1px solid rgba(22, 167, 106, 0.12);
}

.round-msg.followup {
  border-left: 3px solid #8c57ff;
}

.round-msg-label {
  display: inline-block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
}

.round-msg p {
  color: var(--text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.round-msg.skipped {
  opacity: 0.55;
  border-style: dashed;
}

.round-msg.skipped p {
  color: var(--text-muted);
  font-style: italic;
}

/* 操作 */
.report-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 28px;
}

/* ===================================================================
   🆕 NEW REPORT STYLES (v2 matching evaluation)
   =================================================================== */

/* Match Hero */
.match-hero {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  margin-top: 20px;
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

/* Profile Tags */
.profile-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
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
  margin-top: 20px;
}

.radar-panel {
  padding: 22px 24px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.radar-panel h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-800);
  margin-bottom: 8px;
  align-self: flex-start;
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
  margin-top: 20px;
  padding: 22px 24px;
  border-radius: 14px;
}

.improvement-path h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-800);
  margin-bottom: 14px;
}

.improvement-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--neutral-100);
}

.improvement-item:first-child { border-top: none; }

.improvement-item__idx {
  width: 24px;
  height: 24px;
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
  font-weight: 500;
  color: var(--neutral-800);
}

.improvement-item__gap {
  font-size: 12px;
  color: #f59e0b;
  font-family: var(--font-mono);
}

.improvement-item__priority {
  font-size: 12px;
  color: var(--neutral-400);
  font-family: var(--font-mono);
}

@media (max-width: 980px) {
  .module-radar-grid {
    grid-template-columns: 1fr;
  }
  .match-hero {
    grid-template-columns: 1fr;
  }
  .overview-grid,
  .radar-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }

  .report-actions {
    flex-direction: column;
  }

  .report-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
