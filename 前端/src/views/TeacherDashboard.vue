<template>
  <div class="teacher-dashboard">
    <header class="page-head">
      <div>
        <h1>教师端总览</h1>
        <p>Phase 5.1 占位页面 · 当前为静态示例数据，真实数据将在 Phase 5.3 接入</p>
      </div>
      <el-tag type="warning" effect="light" round>示例数据</el-tag>
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

    <section class="panel-grid">
      <article class="panel">
        <header class="panel-head">
          <h2>学生训练情况</h2>
          <el-tag size="small" type="info" effect="plain">近 7 天</el-tag>
        </header>
        <ul class="record-list">
          <li v-for="row in studentTraining" :key="row.name">
            <span class="record-name">{{ row.name }}</span>
            <el-progress
              :percentage="row.progress"
              :stroke-width="10"
              :color="progressColor"
              class="record-bar"
            />
            <span class="record-count">{{ row.sessions }} 次</span>
          </li>
        </ul>
      </article>

      <article class="panel">
        <header class="panel-head">
          <h2>共性短板分析</h2>
          <el-tag size="small" type="info" effect="plain">高频标签</el-tag>
        </header>
        <ul class="weak-list">
          <li v-for="weak in commonWeakness" :key="weak.tag">
            <span class="weak-tag">{{ weak.tag }}</span>
            <div class="weak-meter">
              <span class="weak-fill" :style="{ width: weak.ratio + '%' }"></span>
            </div>
            <span class="weak-ratio">{{ weak.ratio }}%</span>
          </li>
        </ul>
      </article>

      <article class="panel">
        <header class="panel-head">
          <h2>训练任务发布</h2>
          <el-button type="primary" size="small" plain disabled>新建任务</el-button>
        </header>
        <ul class="task-list">
          <li v-for="task in trainingTasks" :key="task.title">
            <div class="task-main">
              <strong>{{ task.title }}</strong>
              <small>截止 {{ task.deadline }} · {{ task.target }}</small>
            </div>
            <el-tag :type="task.statusType" size="small" effect="light">{{ task.status }}</el-tag>
          </li>
        </ul>
        <p class="panel-foot">任务发布功能将在 Phase 5.3 接入真实接口</p>
      </article>
    </section>
  </div>
</template>

<script setup>
import {
  Coordinate,
  DataLine,
  Trophy,
  UserFilled
} from '@element-plus/icons-vue'

// Phase 5.1：以下均为静态占位数据，Phase 5.3 替换为真实教师端接口返回
const statCards = [
  { label: '管理学生数', value: 128, icon: UserFilled, tint: 'rgba(39, 117, 255, 0.12)' },
  { label: '本周训练场次', value: 342, icon: DataLine, tint: 'rgba(9, 186, 107, 0.12)' },
  { label: '平均能力分', value: '78.5', icon: Trophy, tint: 'rgba(255, 159, 24, 0.14)' },
  { label: '待批阅报告', value: 16, icon: Coordinate, tint: 'rgba(124, 88, 255, 0.12)' }
]

const studentTraining = [
  { name: '张明', progress: 86, sessions: 12 },
  { name: '李婷', progress: 72, sessions: 9 },
  { name: '王浩', progress: 64, sessions: 7 },
  { name: '赵雪', progress: 53, sessions: 5 }
]

const commonWeakness = [
  { tag: '项目深挖追问', ratio: 68 },
  { tag: '系统设计', ratio: 54 },
  { tag: '算法复杂度分析', ratio: 47 },
  { tag: 'STAR 表达', ratio: 39 }
]

const trainingTasks = [
  { title: 'Java 后端专项模拟', deadline: '07-05', target: '2023 级 1 班', status: '进行中', statusType: 'success' },
  { title: '项目经历追问训练', deadline: '07-08', target: '全体学生', status: '未开始', statusType: 'info' },
  { title: '系统设计周测', deadline: '06-30', target: '2023 级 2 班', status: '即将截止', statusType: 'warning' }
]

const progressColor = '#1769ff'
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
  font-size: 26px;
  font-weight: 900;
}

.page-head p {
  margin-top: 8px;
  color: #5d6c86;
  font-size: 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border: 1px solid rgba(215, 225, 241, 0.92);
  border-radius: 16px;
  box-shadow: 0 14px 30px rgba(35, 83, 149, 0.05);
}

.stat-icon {
  display: grid;
  width: 52px;
  height: 52px;
  color: #1769ff;
  place-items: center;
  border-radius: 14px;
  font-size: 26px;
}

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-body strong {
  color: #07172f;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.1;
}

.stat-body span {
  margin-top: 4px;
  color: #6b7a93;
  font-size: 13px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px;
  background: #fff;
  border: 1px solid rgba(215, 225, 241, 0.92);
  border-radius: 16px;
  box-shadow: 0 14px 30px rgba(35, 83, 149, 0.05);
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

.record-list,
.weak-list,
.task-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.record-list li {
  display: grid;
  grid-template-columns: 56px 1fr 48px;
  align-items: center;
  gap: 12px;
}

.record-name {
  color: #314361;
  font-size: 14px;
  font-weight: 700;
}

.record-bar {
  min-width: 0;
}

.record-count {
  color: #6b7a93;
  font-size: 13px;
  text-align: right;
}

.weak-list li {
  display: grid;
  grid-template-columns: 110px 1fr 44px;
  align-items: center;
  gap: 12px;
}

.weak-tag {
  color: #314361;
  font-size: 14px;
  font-weight: 700;
}

.weak-meter {
  height: 10px;
  background: #eef2f9;
  border-radius: 999px;
  overflow: hidden;
}

.weak-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ff9f18, #ff6b6b);
  border-radius: 999px;
}

.weak-ratio {
  color: #6b7a93;
  font-size: 13px;
  text-align: right;
}

.task-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-main strong {
  color: #102140;
  font-size: 14px;
  font-weight: 700;
}

.task-main small {
  color: #6b7a93;
  font-size: 12px;
}

.panel-foot {
  margin-top: auto;
  color: #94a1b8;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
