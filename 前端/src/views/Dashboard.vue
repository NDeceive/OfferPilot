<template>
  <div class="dashboard">
    <section class="hero-strip">
      <div class="hero-copy">
        <h1>{{ greeting }}，{{ displayName }} <span>👋</span></h1>
        <p>持续训练，提升面试能力，离理想 Offer 更进一步！</p>
      </div>
      <div class="hero-quote">
        <p>每一次模拟，都是你更从容的开始。</p>
        <img :src="dashboardDeco" alt="" />
      </div>
    </section>

    <section class="quick-actions">
      <article
        v-for="action in quickActions"
        :key="action.title"
        class="action-card"
        @click="router.push(action.path)"
      >
        <span class="action-icon" :class="action.theme">
          <el-icon><component :is="action.icon" /></el-icon>
        </span>
        <div>
          <h2>{{ action.title }}</h2>
          <p>{{ action.desc }}</p>
          <button type="button">
            {{ action.cta }}
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </article>
    </section>

    <div class="dashboard-grid">
      <div class="dashboard-main">
        <section class="panel jobs-panel">
          <header class="panel-header">
            <h2>推荐岗位</h2>
            <router-link to="/jobs">查看全部 <el-icon><ArrowRight /></el-icon></router-link>
          </header>

          <div class="job-cards">
            <article
              v-for="job in recommendedJobs"
              :key="job.id"
              class="job-card"
              @click="router.push({ path: '/jobs', query: { select: job.id } })"
            >
              <div class="job-brand" :class="job.theme">
                <el-icon><component :is="job.icon" /></el-icon>
              </div>
              <h3>{{ job.name }}</h3>
              <p>{{ job.meta }}</p>
              <div class="tag-row">
                <span v-for="tag in job.tags" :key="tag">{{ tag }}</span>
              </div>
              <footer>
                <span><el-icon><User /></el-icon>{{ job.learners }} 人在练习</span>
                <el-icon><CollectionTag /></el-icon>
              </footer>
            </article>
          </div>
        </section>

        <section class="panel records-panel">
          <header class="panel-header">
            <h2>最近面试记录</h2>
          </header>

          <div class="record-table">
            <div class="record-row record-head">
              <span>日期</span>
              <span>岗位</span>
              <span>得分</span>
              <span>表现</span>
              <span>操作</span>
            </div>
            <div v-for="record in records" :key="record.date + record.time" class="record-row">
              <span>{{ record.date }} <small>{{ record.time }}</small></span>
              <span>{{ record.job }}</span>
              <span>{{ record.score }} 分</span>
              <span>
                <em :class="record.level">{{ record.label }}</em>
              </span>
              <span class="record-actions">
                <button type="button">查看报告</button>
                <el-icon><MoreFilled /></el-icon>
              </span>
            </div>
          </div>

          <router-link class="all-records" to="/history">
            查看全部记录 <el-icon><ArrowRight /></el-icon>
          </router-link>
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel progress-panel">
          <header class="panel-header">
            <h2>今日训练进度</h2>
            <router-link to="/report">更多数据 <el-icon><ArrowRight /></el-icon></router-link>
          </header>

          <div class="progress-body">
            <div class="progress-ring" :style="progressStyle">
              <div>
                <strong>75%</strong>
                <span>每日目标 60 分钟</span>
              </div>
            </div>
            <div class="progress-list">
              <p><el-icon><Clock /></el-icon><span>训练时长</span><strong>45 / 60 分钟</strong></p>
              <p><el-icon><VideoCamera /></el-icon><span>完成面试</span><strong>2 / 3 场</strong></p>
              <p><el-icon><Aim /></el-icon><span>掌握知识点</span><strong>18 / 25 个</strong></p>
            </div>
          </div>
        </section>

        <section class="panel streak-panel">
          <div>
            <span class="flame"><el-icon><Lightning /></el-icon></span>
            <strong>连续训练天数</strong>
            <b>7 天</b>
            <small>继续加油，保持状态！</small>
          </div>
          <div class="bars">
            <span v-for="day in streakDays" :key="day.label" :style="{ height: day.height + 'px' }"></span>
          </div>
        </section>

        <section class="panel knowledge-panel">
          <header class="panel-header">
            <h2>今日知识点推荐</h2>
            <button type="button">换一换</button>
          </header>
          <div class="knowledge-card">
            <span><el-icon><Reading /></el-icon></span>
            <div>
              <h3>Spring Boot 核心注解原理</h3>
              <p>@SpringBootApplication 注解包含了哪些注解？它的自动配置原理是什么？</p>
              <button type="button">
                去学习 <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Aim,
  ArrowRight,
  Clock,
  Collection,
  CollectionTag,
  Cpu,
  DataAnalysis,
  Document,
  Lightning,
  MoreFilled,
  Platform,
  Reading,
  User,
  VideoCamera
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import dashboardDeco from '@/assets/generated/dashboard-deco-ui.jpg'

const router = useRouter()
const userStore = useUserStore()

const now = new Date()
const hour = now.getHours()
const greeting = computed(() => {
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const displayName = computed(() => userStore.nickname || userStore.username || '同学')

const quickActions = [
  { title: '开始面试', desc: '选择岗位，开启 AI 模拟面试', cta: '立即开始', path: '/jobs', icon: VideoCamera, theme: 'purple' },
  { title: '面试准备', desc: '刷题练习，掌握面试知识点', cta: '去准备', path: '/jobs', icon: Document, theme: 'blue' },
  { title: '能力测评', desc: '全面评估，定位能力短板', cta: '去测评', path: '/report', icon: DataAnalysis, theme: 'green' }
]

const recommendedJobs = [
  {
    id: 1,
    name: 'Java后端开发工程师',
    meta: '互联网 · 中级',
    learners: '12456',
    tags: ['Java', 'Spring Boot', 'MySQL'],
    icon: Collection,
    theme: 'java'
  },
  {
    id: 2,
    name: 'Web前端开发工程师',
    meta: '互联网 · 中级',
    learners: '9821',
    tags: ['HTML/CSS/JS', 'Vue', 'TypeScript'],
    icon: Platform,
    theme: 'web'
  },
  {
    id: 3,
    name: 'AI算法工程师',
    meta: '人工智能 · 高级',
    learners: '7563',
    tags: ['Python', '机器学习', '深度学习'],
    icon: Cpu,
    theme: 'ai'
  }
]

const records = [
  { date: '2024-05-18', time: '14:30', job: 'Java后端开发工程师', score: 86, label: '优秀', level: 'good' },
  { date: '2024-05-17', time: '10:15', job: 'Web前端开发工程师', score: 78, label: '良好', level: 'ok' },
  { date: '2024-05-16', time: '16:45', job: 'AI算法工程师', score: 92, label: '优秀', level: 'good' }
]

const streakDays = [
  { label: '一', height: 32 },
  { label: '三', height: 43 },
  { label: '四', height: 41 },
  { label: '五', height: 42 },
  { label: '六', height: 30 },
  { label: '日', height: 58 }
]

const progressStyle = computed(() => ({
  background: 'conic-gradient(#4f7cff 0 75%, #edf3ff 75% 100%)'
}))
</script>

<style scoped>
.dashboard {
  width: min(1280px, 100%);
  margin: 0 auto;
}

.hero-strip {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: center;
  min-height: 112px;
  margin-bottom: 22px;
}

.hero-copy h1 {
  color: #061933;
  font-size: 30px;
  font-weight: 950;
  line-height: 1.15;
}

.hero-copy p {
  margin-top: 8px;
  color: #62718c;
  font-size: 15px;
  font-weight: 600;
}

.hero-quote {
  position: relative;
  min-height: 112px;
  overflow: hidden;
}

.hero-quote p {
  position: relative;
  z-index: 1;
  margin: 26px 130px 0 0;
  color: #52617e;
  font-size: 14px;
  font-weight: 700;
  text-align: center;
}

.hero-quote p::before,
.hero-quote p::after {
  color: #7ba8ff;
  font-size: 44px;
  font-weight: 900;
  line-height: 0;
}

.hero-quote p::before {
  content: '“';
  margin-right: 12px;
}

.hero-quote p::after {
  content: '”';
  margin-left: 12px;
}

.hero-quote img {
  position: absolute;
  right: -64px;
  top: -28px;
  width: 220px;
  height: 156px;
  object-fit: cover;
  object-position: right center;
  border-radius: 28px;
  mix-blend-mode: multiply;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-bottom: 22px;
}

.action-card,
.panel {
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #dbe4f2;
  border-radius: 8px;
  box-shadow: 0 14px 34px rgba(34, 74, 137, 0.06);
}

.action-card {
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 24px;
  align-items: center;
  min-height: 136px;
  padding: 24px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.action-card:hover {
  box-shadow: 0 18px 42px rgba(34, 92, 184, 0.11);
  transform: translateY(-2px);
}

.action-icon {
  display: grid;
  width: 64px;
  height: 64px;
  color: #fff;
  place-items: center;
  border-radius: 18px;
  font-size: 30px;
}

.action-icon.purple {
  background: linear-gradient(135deg, #8c57ff, #5a34ec);
  box-shadow: 0 18px 34px rgba(105, 64, 244, 0.28);
}

.action-icon.blue {
  background: linear-gradient(135deg, #4a93ff, #196eff);
  box-shadow: 0 18px 34px rgba(25, 110, 255, 0.24);
}

.action-icon.green {
  background: linear-gradient(135deg, #46dc95, #09bd76);
  box-shadow: 0 18px 34px rgba(9, 189, 118, 0.22);
}

.action-card h2 {
  color: #10213c;
  font-size: 20px;
  font-weight: 900;
}

.action-card p {
  margin-top: 7px;
  color: #6c7a92;
  font-size: 14px;
  font-weight: 600;
}

.action-card button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  margin-top: 12px;
  padding: 0 15px;
  color: #fff;
  background: linear-gradient(135deg, #2f80ff, #1067f9);
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 800;
}

.action-card:first-child button {
  background: linear-gradient(135deg, #8058ff, #5a30ee);
}

.action-card:last-child button {
  background: linear-gradient(135deg, #27ce82, #08b66d);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 22px;
}

.dashboard-main {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.dashboard-side {
  display: grid;
  align-content: start;
  gap: 22px;
  min-width: 0;
}

.panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-header h2 {
  color: #10213c;
  font-size: 18px;
  font-weight: 900;
}

.panel-header a,
.panel-header button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6c7a92;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
}

.job-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.job-card {
  min-width: 0;
  padding: 18px 16px 14px;
  border: 1px solid #dbe4f2;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.job-card:hover {
  border-color: rgba(38, 118, 255, 0.35);
  box-shadow: 0 14px 30px rgba(38, 118, 255, 0.09);
  transform: translateY(-2px);
}

.job-brand {
  display: grid;
  width: 50px;
  height: 50px;
  margin-bottom: 14px;
  place-items: center;
  font-size: 31px;
}

.job-brand.java {
  color: #ed4d42;
}

.job-brand.web {
  color: #16a76a;
}

.job-brand.ai {
  color: #3477c8;
}

.job-card h3 {
  color: #13243f;
  font-size: 18px;
  font-weight: 900;
}

.job-card p {
  margin-top: 6px;
  color: #6b7a94;
  font-size: 14px;
  font-weight: 600;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 32px;
  margin-top: 16px;
}

.tag-row span {
  padding: 7px 10px;
  color: #3b6ef5;
  background: #eef4ff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}

.job-card:nth-child(3) .tag-row span {
  color: #078e6a;
  background: #e9fbf4;
}

.job-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 15px;
  margin-top: 16px;
  color: #5b6a86;
  border-top: 1px solid #e2e9f3;
  font-size: 13px;
  font-weight: 600;
}

.job-card footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.record-table {
  overflow: hidden;
  border: 1px solid #e0e7f1;
  border-radius: 8px;
}

.record-row {
  display: grid;
  grid-template-columns: 1.1fr 1.7fr 0.7fr 0.8fr 1.1fr;
  align-items: center;
  min-height: 74px;
  padding: 0 28px;
  color: #263a5a;
  border-top: 1px solid #e8eef6;
  font-size: 15px;
}

.record-row:first-child {
  border-top: 0;
}

.record-head {
  min-height: 54px;
  color: #63728e;
  background: #f8faff;
  font-weight: 800;
}

.record-row small {
  margin-left: 14px;
  color: #2f4364;
}

.record-row em {
  display: inline-flex;
  padding: 8px 13px;
  border-radius: 8px;
  font-style: normal;
  font-weight: 800;
}

.record-row em.good {
  color: #09a878;
  background: #dbfbef;
}

.record-row em.ok {
  color: #2874ef;
  background: #e7f1ff;
}

.record-actions {
  display: inline-flex;
  align-items: center;
  gap: 18px;
}

.record-actions button {
  height: 38px;
  padding: 0 18px;
  color: #2674ff;
  background: #fff;
  border: 1px solid #d8e4f4;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.all-records {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  margin-top: 20px;
  color: #2674ff;
  font-weight: 800;
}

.progress-body {
  display: grid;
  grid-template-columns: 142px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.progress-ring {
  display: grid;
  width: 132px;
  height: 132px;
  place-items: center;
  border-radius: 50%;
}

.progress-ring > div {
  display: grid;
  width: 102px;
  height: 102px;
  color: #122747;
  place-items: center;
  background: #fff;
  border-radius: 50%;
  text-align: center;
}

.progress-ring strong,
.progress-ring span {
  display: block;
}

.progress-ring strong {
  font-size: 28px;
  font-weight: 900;
}

.progress-ring span {
  color: #66758e;
  font-size: 11px;
  font-weight: 700;
}

.progress-list {
  display: grid;
  gap: 16px;
}

.progress-list p {
  display: grid;
  grid-template-columns: 34px 1fr;
  column-gap: 12px;
  margin: 0;
  color: #70809b;
}

.progress-list .el-icon {
  grid-row: span 2;
  display: grid;
  width: 34px;
  height: 34px;
  color: #4f7cff;
  place-items: center;
  background: #eef4ff;
  border-radius: 50%;
}

.progress-list strong {
  color: #243756;
  font-size: 20px;
}

.streak-panel {
  display: grid;
  grid-template-columns: 1fr 178px;
  align-items: center;
}

.streak-panel strong,
.streak-panel b,
.streak-panel small {
  display: block;
}

.flame {
  display: inline-grid;
  width: 32px;
  height: 32px;
  margin-right: 12px;
  color: #ff9a24;
  place-items: center;
  background: #fff3e4;
  border-radius: 50%;
  vertical-align: middle;
}

.streak-panel strong {
  display: inline-block;
  color: #50617d;
  font-size: 16px;
  font-weight: 900;
}

.streak-panel b {
  margin: 8px 0 5px 45px;
  color: #101f39;
  font-size: 24px;
  font-weight: 950;
}

.streak-panel small {
  margin-left: 45px;
  color: #7a889f;
  font-size: 13px;
  font-weight: 700;
}

.bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 70px;
}

.bars span {
  width: 14px;
  background: linear-gradient(180deg, #557fff, #b3c3ff);
  border-radius: 4px 4px 0 0;
}

.knowledge-panel {
  background: linear-gradient(135deg, #fff 0%, #fbf7ff 100%);
  border-color: #eadfff;
}

.knowledge-card {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 18px;
}

.knowledge-card > span {
  display: grid;
  width: 70px;
  height: 70px;
  color: #8c5eff;
  place-items: center;
  background: #f1eaff;
  border-radius: 16px;
  font-size: 34px;
}

.knowledge-card h3 {
  color: #162843;
  font-size: 17px;
  font-weight: 900;
}

.knowledge-card p {
  margin-top: 10px;
  color: #5e6d85;
  font-size: 14px;
  line-height: 1.7;
}

.knowledge-card button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  margin-top: 18px;
  padding: 0 24px;
  color: #6d45f7;
  background: #fff;
  border: 1px solid #d9dff1;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 900;
}

@media (max-width: 1060px) {
  .hero-strip,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .hero-quote {
    display: none;
  }

  .quick-actions,
  .job-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .quick-actions,
  .job-cards,
  .progress-body,
  .streak-panel {
    grid-template-columns: 1fr;
  }

  .action-card {
    grid-template-columns: 64px 1fr;
    gap: 18px;
    min-height: auto;
    padding: 22px;
  }

  .action-icon {
    width: 64px;
    height: 64px;
  }

  .record-table {
    overflow-x: auto;
  }

  .record-row {
    min-width: 760px;
  }
}
</style>
