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
      <article class="chart-panel">
        <header class="panel-head">
          <h2>近7天训练趋势</h2>
          <el-tag size="small" type="info" effect="plain">训练场次</el-tag>
        </header>
        <div ref="trendChartRef" class="chart-box"></div>
      </article>

      <article class="chart-panel">
        <header class="panel-head">
          <h2>能力短板分布</h2>
          <el-tag size="small" type="warning" effect="plain">高频问题</el-tag>
        </header>
        <ul class="weakness-list">
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
      </article>
    </section>

    <section class="content-grid">
      <article class="content-panel full">
        <header class="panel-head">
          <h2>学生训练情况</h2>
          <el-tag size="small" type="info" effect="plain">最近更新</el-tag>
        </header>
        <el-table :data="studentTable" stripe style="width: 100%">
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

      <article class="content-panel">
        <header class="panel-head">
          <h2>共性问题分析</h2>
          <el-tag size="small" type="danger" effect="plain">需关注</el-tag>
        </header>
        <ul class="problem-list">
          <li v-for="(problem, idx) in commonProblems" :key="idx" class="problem-card">
            <div class="problem-icon" :style="{ background: problem.color }">
              <el-icon><component :is="problem.icon" /></el-icon>
            </div>
            <div class="problem-body">
              <strong>{{ problem.title }}</strong>
              <p>{{ problem.desc }}</p>
              <span class="problem-rate">{{ problem.rate }}% 学生出现</span>
            </div>
          </li>
        </ul>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
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

const statCards = [
  { label: '学生总数', value: 128, icon: UserFilled, tint: 'rgba(39, 117, 255, 0.12)' },
  { label: '已训练人数', value: 96, icon: Checked, tint: 'rgba(9, 186, 107, 0.12)' },
  { label: '训练完成率', value: '75%', icon: TrendCharts, tint: 'rgba(16, 195, 185, 0.12)' },
  { label: '平均面试分', value: '78.5', icon: Medal, tint: 'rgba(255, 159, 24, 0.14)' },
  { label: 'AI追问次数', value: 1247, icon: ChatDotRound, tint: 'rgba(124, 88, 255, 0.12)' },
  { label: 'RULE追问次数', value: 583, icon: Flag, tint: 'rgba(255, 77, 109, 0.12)' }
]

const studentTable = [
  { student: '张明', direction: 'Java 后端', sessions: 12, avgScore: 86, lastTrain: '2026-06-28 14:30', status: '活跃', statusType: 'success' },
  { student: '李婷', direction: '前端开发', sessions: 9, avgScore: 72, lastTrain: '2026-06-27 09:15', status: '活跃', statusType: 'success' },
  { student: '王浩', direction: 'Java 后端', sessions: 7, avgScore: 64, lastTrain: '2026-06-26 16:45', status: '正常', statusType: '' },
  { student: '赵雪', direction: '算法岗', sessions: 5, avgScore: 53, lastTrain: '2026-06-24 11:20', status: '待提升', statusType: 'warning' },
  { student: '陈刚', direction: 'Java 后端', sessions: 11, avgScore: 81, lastTrain: '2026-06-28 10:05', status: '活跃', statusType: 'success' },
  { student: '刘芳', direction: '前端开发', sessions: 6, avgScore: 69, lastTrain: '2026-06-25 15:30', status: '正常', statusType: '' }
]

const commonProblems = [
  { title: '项目表达笼统', desc: '项目描述缺乏具体数据和技术细节', rate: 68, icon: DocumentCopy, color: 'rgba(255, 77, 109, 0.15)' },
  { title: '追问应对不足', desc: '面对深度追问时逻辑断层明显', rate: 54, icon: Connection, color: 'rgba(255, 159, 24, 0.15)' },
  { title: '技术细节不清', desc: '无法清晰解释技术选型与实现', rate: 47, icon: DataLine, color: 'rgba(124, 88, 255, 0.15)' },
  { title: '逻辑结构不完整', desc: 'STAR 结构缺失，表达跳跃', rate: 39, icon: Warning, color: 'rgba(16, 195, 185, 0.15)' }
]

const weaknessData = [
  { label: '项目表达笼统', value: 68, color: 'linear-gradient(90deg, #ff6b6b, #ff9f18)' },
  { label: '追问应对不足', value: 54, color: 'linear-gradient(90deg, #ff9f18, #ffd93d)' },
  { label: '技术细节不清', value: 47, color: 'linear-gradient(90deg, #7c58ff, #a78bfa)' },
  { label: '逻辑结构不完整', value: 39, color: 'linear-gradient(90deg, #10c3b9, #06b6d4)' }
]

const taskForm = ref({
  name: '',
  direction: '',
  deadline: '',
  target: '',
  description: ''
})

const trendChartRef = ref(null)
let trendChart = null

onMounted(() => {
  initTrendChart()
})

onBeforeUnmount(() => {
  if (trendChart) trendChart.dispose()
})

function initTrendChart() {
  trendChart = echarts.init(trendChartRef.value)
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: ['06-23', '06-24', '06-25', '06-26', '06-27', '06-28', '06-29'],
      axisLine: { lineStyle: { color: '#e0e6ed' } },
      axisLabel: { color: '#6b7a93' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f0f3f8', type: 'dashed' } },
      axisLabel: { color: '#6b7a93' }
    },
    series: [
      {
        name: '训练场次',
        type: 'line',
        smooth: true,
        data: [42, 56, 48, 67, 72, 81, 65],
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
