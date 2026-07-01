<template>
  <div class="teacher-dashboard">
    <header class="page-head">
      <div>
        <h1>教师端总览</h1>
        <p>查看学生训练情况、追问来源分布与班级共性短板</p>
      </div>
      <div class="head-actions">
        <el-button type="primary" plain>本周数据</el-button>
        <el-button type="success" plain>导出报告</el-button>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="load-error"
      type="error"
      :closable="false"
      show-icon
      title="数据加载失败"
    >
      <template #default>
        <span>教师端总览数据加载失败，请检查登录状态或稍后重试。</span>
        <el-button link type="primary" @click="loadOverview">重新加载</el-button>
      </template>
    </el-alert>

    <section class="stat-grid">
      <article v-for="card in statCards" :key="card.label" class="stat-card">
        <span class="stat-icon" :style="{ background: card.tint }">
          <el-icon><component :is="card.icon" /></el-icon>
        </span>
        <div class="stat-body">
          <strong>{{ card.value }}</strong>
          <span>{{ card.label }}</span>
        </div>
      </article>
    </section>

    <section class="chart-grid">
      <article v-loading="loading" class="chart-panel">
        <header class="panel-head">
          <h2>近7天训练趋势</h2>
          <el-tag size="small" type="info" effect="plain">训练场次</el-tag>
        </header>
        <div v-show="trendData.length" ref="trendChartRef" class="chart-box"></div>
        <div v-if="!loading && !trendData.length" class="empty-box">暂无数据</div>
      </article>

      <article v-loading="loading" class="chart-panel">
        <header class="panel-head">
          <h2>能力短板分布</h2>
          <el-tag size="small" type="warning" effect="plain">高频问题</el-tag>
        </header>
        <ul v-if="weaknessData.length" class="weakness-list">
          <li v-for="item in weaknessData" :key="item.label" class="weakness-item">
            <div class="weakness-icon">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="weakness-content">
              <div class="weakness-header">
                <span class="weakness-label">{{ item.label }}</span>
                <span class="weakness-value">{{ item.value }}%</span>
              </div>
              <div class="weakness-bar">
                <div class="weakness-fill" :style="{ width: item.value + '%', background: item.color }"></div>
              </div>
            </div>
          </li>
        </ul>
        <div v-else-if="!loading" class="empty-box">暂无数据</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="content-panel full">
        <header class="panel-head">
          <h2>学生训练情况</h2>
          <el-tag size="small" type="info" effect="plain">最近更新</el-tag>
        </header>
        <el-table v-loading="loading" :data="studentTable" stripe style="width: 100%" empty-text="暂无数据">
          <el-table-column prop="student" label="学生" width="100" />
          <el-table-column prop="direction" label="岗位方向" width="140" />
          <el-table-column prop="sessions" label="训练次数" width="100" align="center" />
          <el-table-column prop="avgScore" label="平均分" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.avgScore >= 80 ? 'success' : row.avgScore >= 60 ? '' : 'warning'" size="small">
                {{ row.avgScore }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastTrain" label="最近训练" width="180" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.statusType" size="small" effect="light">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article v-loading="loading" class="content-panel">
        <header class="panel-head">
          <h2>共性问题分析</h2>
          <el-tag size="small" type="danger" effect="plain">需关注</el-tag>
        </header>
        <ul v-if="commonProblems.length" class="problem-list">
          <li v-for="(problem, idx) in commonProblems" :key="idx" class="problem-card">
            <div class="problem-icon" :style="{ background: problem.color }">
              <el-icon><component :is="problem.icon" /></el-icon>
            </div>
            <div class="problem-body">
              <strong>{{ problem.title }}</strong>
              <p>{{ problem.desc }}</p>
              <span class="problem-rate">{{ problem.rate }}% 学生出现 · {{ problem.level }}</span>
            </div>
          </li>
        </ul>
        <div v-else-if="!loading" class="empty-box">暂无数据</div>
      </article>

      <article class="content-panel">
        <header class="panel-head">
          <h2>训练任务发布</h2>
          <el-tag size="small" type="success" effect="plain">模拟发布</el-tag>
        </header>
        <div class="task-form">
          <el-input v-model="taskForm.name" placeholder="任务名称" clearable />
          <el-select v-model="taskForm.direction" placeholder="岗位方向" clearable style="width: 100%">
            <el-option label="Java 后端" value="java" />
            <el-option label="前端开发" value="frontend" />
            <el-option label="算法岗" value="algorithm" />
            <el-option label="产品经理" value="product" />
          </el-select>
          <el-date-picker v-model="taskForm.deadline" type="date" placeholder="截止时间" style="width: 100%" />
          <el-select v-model="taskForm.target" placeholder="目标人群" clearable style="width: 100%">
            <el-option label="计算机一班" value="cs1" />
            <el-option label="计算机二班" value="cs2" />
            <el-option label="软件工程一班" value="se1" />
            <el-option label="全体学生" value="all" />
          </el-select>
          <el-input
            v-model="taskForm.description"
            type="textarea"
            placeholder="任务描述"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
          <div class="task-actions">
            <el-button @click="clearTask">清空</el-button>
            <el-button type="primary" @click="publishTask">立即发布</el-button>
          </div>
        </div>
        <p class="panel-foot">任务发布功能将在 Phase 5.3 接入真实接口</p>
      </article>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  UserFilled,
  Checked,
  TrendCharts,
  Medal,
  ChatDotRound,
  Flag,
  DocumentCopy,
  Connection,
  DataLine,
  Warning
} from '@element-plus/icons-vue'
import { getTeacherDashboardOverview } from '@/api/teacher'

/* ---------- 页面状态 ---------- */
const loading = ref(false)
const loadError = ref(false)
const overview = ref(null)

/* ---------- 训练任务发布（模拟，非本期联调范围，保持原有静态行为） ---------- */
const taskForm = ref({
  name: '',
  direction: '',
  deadline: '',
  target: '',
  description: ''
})

function clearTask() {
  taskForm.value = {
    name: '',
    direction: '',
    deadline: '',
    target: '',
    description: ''
  }
  ElMessage.info('表单已清空')
}

function publishTask() {
  if (!taskForm.value.name || !taskForm.value.direction || !taskForm.value.deadline || !taskForm.value.target) {
    ElMessage.warning('请填写完整任务信息')
    return
  }
  ElMessage.success('训练任务已生成模拟发布记录')
  clearTask()
}

/* ---------- 展示用静态配置（图标/配色，与数据无关） ---------- */
const statCardMeta = [
  { key: 'studentTotal', label: '学生总数', icon: UserFilled, tint: 'rgba(39, 117, 255, 0.12)', format: 'int' },
  { key: 'trainedStudentCount', label: '已训练人数', icon: Checked, tint: 'rgba(9, 186, 107, 0.12)', format: 'int' },
  { key: 'completionRate', label: '训练完成率', icon: TrendCharts, tint: 'rgba(16, 195, 185, 0.12)', format: 'percent' },
  { key: 'averageScore', label: '平均面试分', icon: Medal, tint: 'rgba(255, 159, 24, 0.14)', format: 'score' },
  { key: 'aiFollowupCount', label: 'AI追问次数', icon: ChatDotRound, tint: 'rgba(124, 88, 255, 0.12)', format: 'int' },
  { key: 'ruleFollowupCount', label: 'RULE追问次数', icon: Flag, tint: 'rgba(255, 77, 109, 0.12)', format: 'int' }
]

const weaknessColors = [
  'linear-gradient(90deg, #ff6b6b, #ff9f18)',
  'linear-gradient(90deg, #ff9f18, #ffd93d)',
  'linear-gradient(90deg, #7c58ff, #a78bfa)',
  'linear-gradient(90deg, #10c3b9, #06b6d4)'
]

const problemIcons = [DocumentCopy, Connection, DataLine, Warning]
const problemColors = [
  'rgba(255, 77, 109, 0.15)',
  'rgba(255, 159, 24, 0.15)',
  'rgba(124, 88, 255, 0.15)',
  'rgba(16, 195, 185, 0.15)'
]

/* ---------- 工具函数 ---------- */
const num = (v) => (typeof v === 'number' && !Number.isNaN(v) ? v : 0)

// 后端占比为 0~1（保留 2 位小数）；做兼容：若已是 >1 的百分数则原样取整
const ratioToPercent = (v) => {
  const n = num(v)
  return Math.round(n <= 1 ? n * 100 : n)
}

const statusTypeOf = (status) => {
  switch (status) {
    case '已完成':
      return 'success'
    case '进行中':
      return 'warning'
    case '未开始':
      return 'info'
    default:
      return ''
  }
}

/* ---------- 计算属性：由接口数据派生 ---------- */
const summary = computed(() => overview.value?.summary || {})

const statCards = computed(() =>
  statCardMeta.map((meta) => {
    const s = summary.value
    let value = 0
    if (meta.format === 'percent') {
      // 兼容 trainingCompletionRate / completionRate 两种字段名
      const rate = s.trainingCompletionRate ?? s.completionRate
      value = `${ratioToPercent(rate)}%`
    } else if (meta.format === 'score') {
      value = num(s.averageScore)
    } else {
      value = num(s[meta.key])
    }
    return { label: meta.label, value, icon: meta.icon, tint: meta.tint }
  })
)

const trendData = computed(() => overview.value?.trainingTrend || [])

const weaknessData = computed(() =>
  (overview.value?.weaknessDistribution || []).map((item, i) => ({
    label: item.name,
    value: ratioToPercent(item.percent),
    color: weaknessColors[i % weaknessColors.length]
  }))
)

const studentTable = computed(() =>
  (overview.value?.studentTrainingList || []).map((row) => ({
    student: row.studentName,
    direction: row.position || '-',
    sessions: num(row.trainingCount),
    avgScore: num(row.averageScore),
    lastTrain: row.lastTrainingTime || '-',
    status: row.status || '-',
    statusType: statusTypeOf(row.status)
  }))
)

const commonProblems = computed(() =>
  (overview.value?.commonProblems || []).map((p, i) => ({
    title: p.title,
    desc: p.description,
    rate: ratioToPercent(p.percent),
    level: p.level || '',
    icon: problemIcons[i % problemIcons.length],
    color: problemColors[i % problemColors.length]
  }))
)

/* ---------- 训练趋势图 ---------- */
const trendChartRef = ref(null)
let trendChart = null

function renderTrendChart() {
  const data = trendData.value
  if (!data.length) return
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      // date 形如 2026-06-23，展示取 MM-DD
      data: data.map((d) => (d.date ? String(d.date).slice(5) : '')),
      axisLine: { lineStyle: { color: '#e0e6ed' } },
      axisLabel: { color: '#6b7a93' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f0f3f8', type: 'dashed' } },
      axisLabel: { color: '#6b7a93' }
    },
    series: [
      {
        name: '训练场次',
        type: 'line',
        smooth: true,
        data: data.map((d) => num(d.count)),
        lineStyle: { width: 3, color: '#1769ff' },
        itemStyle: { color: '#1769ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(23, 105, 255, 0.3)' },
            { offset: 1, color: 'rgba(23, 105, 255, 0.05)' }
          ])
        }
      }
    ]
  }
  trendChart.setOption(option)
}

function handleResize() {
  if (trendChart) trendChart.resize()
}

/* ---------- 数据加载 ---------- */
async function loadOverview() {
  loading.value = true
  loadError.value = false
  try {
    const data = await getTeacherDashboardOverview()
    overview.value = data || {}
    await nextTick()
    renderTrendChart()
  } catch (e) {
    // 401 已由 request 拦截器处理并跳登录；此处兜底防止页面崩溃
    loadError.value = true
    overview.value = null
    ElMessage.error('教师端数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadOverview()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
})
</script>

<style scoped>
.teacher-dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-head h1 {
  color: #07172f;
  font-size: 28px;
  font-weight: 900;
}

.page-head p {
  margin-top: 8px;
  color: #5d6c86;
  font-size: 14px;
}

.head-actions {
  display: flex;
  gap: 12px;
}

.load-error {
  border-radius: 12px;
}

.empty-box {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: #94a1b8;
  font-size: 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: linear-gradient(135deg, #fff 0%, #f8fafd 100%);
  border: 1px solid rgba(215, 225, 241, 0.92);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(35, 83, 149, 0.06);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(35, 83, 149, 0.12);
}

.stat-icon {
  display: grid;
  width: 48px;
  height: 48px;
  color: #1769ff;
  place-items: center;
  border-radius: 12px;
  font-size: 24px;
}

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-body strong {
  color: #07172f;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.1;
}

.stat-body span {
  margin-top: 4px;
  color: #6b7a93;
  font-size: 12px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.chart-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border: 1px solid rgba(215, 225, 241, 0.92);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(35, 83, 149, 0.06);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-head h2 {
  color: #102140;
  font-size: 17px;
  font-weight: 800;
}

.chart-box {
  width: 100%;
  height: 280px;
}

.weakness-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 8px 0;
}

.weakness-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.weakness-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  place-items: center;
  color: #ff9f18;
  font-size: 18px;
  background: rgba(255, 159, 24, 0.12);
  border-radius: 10px;
}

.weakness-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.weakness-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.weakness-label {
  color: #314361;
  font-size: 14px;
  font-weight: 700;
}

.weakness-value {
  color: #6b7a93;
  font-size: 14px;
  font-weight: 600;
  min-width: 48px;
  text-align: right;
}

.weakness-bar {
  height: 12px;
  background: #f0f3f8;
  border-radius: 999px;
  overflow: hidden;
}

.weakness-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.6s ease;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border: 1px solid rgba(215, 225, 241, 0.92);
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(35, 83, 149, 0.06);
}

.content-panel.full {
  grid-column: span 2;
}

.problem-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.problem-card {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #fafbfd 0%, #f5f7fa 100%);
  border-radius: 12px;
  border: 1px solid rgba(215, 225, 241, 0.6);
}

.problem-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  color: #ff4d6d;
  place-items: center;
  border-radius: 10px;
  font-size: 20px;
}

.problem-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.problem-body strong {
  color: #102140;
  font-size: 14px;
  font-weight: 700;
}

.problem-body p {
  color: #6b7a93;
  font-size: 12px;
  line-height: 1.5;
}

.problem-rate {
  color: #ff4d6d;
  font-size: 12px;
  font-weight: 600;
}

.task-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 4px;
}

.panel-foot {
  margin-top: auto;
  padding-top: 12px;
  color: #94a1b8;
  font-size: 12px;
  border-top: 1px dashed #e0e6ed;
}

@media (max-width: 1400px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .content-panel.full {
    grid-column: span 1;
  }

  .problem-list {
    grid-template-columns: 1fr;
  }
}
</style>
