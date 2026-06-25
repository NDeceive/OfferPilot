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
      <!-- 总分 + 概览 -->
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
            <el-tag
              v-for="tag in weakTagList"
              :key="tag"
              type="warning"
              effect="plain"
              size="small"
              class="weak-tag"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 雷达图 + 维度卡 -->
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
            <el-progress
              :percentage="Number(dim.score)"
              :stroke-width="8"
              :color="barColor(dim.score)"
              :show-text="false"
            />
            <p class="dimension-explain">{{ dim.explanation }}</p>
          </div>
        </div>
      </div>

      <!-- 优势 / 不足 / 建议 -->
      <div class="insight-grid">
        <div class="insight-card glass-panel strengths">
          <h3><el-icon><CircleCheckFilled /></el-icon>表现优势</h3>
          <ul>
            <li v-for="(s, i) in report.strengths" :key="'s' + i">{{ s }}</li>
          </ul>
        </div>
        <div class="insight-card glass-panel weaknesses">
          <h3><el-icon><WarningFilled /></el-icon>待改进项</h3>
          <ul>
            <li v-for="(w, i) in report.weaknesses" :key="'w' + i">{{ w }}</li>
          </ul>
        </div>
        <div class="insight-card glass-panel suggestions">
          <h3><el-icon><MagicStick /></el-icon>提升建议</h3>
          <ul>
            <li v-for="(g, i) in report.suggestions" :key="'g' + i">{{ g }}</li>
          </ul>
        </div>
      </div>

      <!-- 操作 -->
      <div class="report-actions">
        <el-button :icon="HomeFilled" @click="router.push('/home')">返回首页</el-button>
        <el-button :icon="Tickets" @click="router.push('/history')">查看历史记录</el-button>
        <el-button type="primary" :icon="RefreshRight" @click="retrain">再次训练</el-button>
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
  CircleCheckFilled,
  DataAnalysis,
  HomeFilled,
  MagicStick,
  Memo,
  RefreshRight,
  Tickets,
  WarningFilled
} from '@element-plus/icons-vue'
import { getReportDetail, getInterviewRecords } from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const report = ref(null)

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

const loadReport = async (reportId) => {
  try {
    report.value = await getReportDetail(reportId)
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
        router.replace({ path: '/report', query: { reportId } })
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

/* 操作 */
.report-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 28px;
}

@media (max-width: 980px) {
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
