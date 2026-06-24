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
            <h2>可选岗位</h2>
            <router-link to="/jobs">查看全部 <el-icon><ArrowRight /></el-icon></router-link>
          </header>

          <div v-loading="jobsLoading" class="job-cards">
            <article
              v-for="job in jobs"
              :key="job.id"
              class="job-card"
              @click="router.push({ path: '/jobs', query: { select: job.id } })"
            >
              <div class="job-brand">
                <el-icon><CollectionTag /></el-icon>
              </div>
              <h3>{{ job.name }}</h3>
              <p>{{ job.category || '岗位训练' }}</p>
              <div class="tag-row">
                <span v-for="tag in parseTags(job.keywords)" :key="tag">{{ tag }}</span>
              </div>
            </article>
            <el-empty v-if="!jobsLoading && jobs.length === 0" description="暂无岗位" :image-size="60" />
          </div>
        </section>

        <section class="panel records-panel">
          <header class="panel-header">
            <h2>最近面试记录</h2>
            <router-link v-if="records.length" to="/history">全部 <el-icon><ArrowRight /></el-icon></router-link>
          </header>

          <div v-if="records.length" class="record-table">
            <div class="record-row record-head">
              <span>日期</span>
              <span>岗位</span>
              <span>得分</span>
              <span>状态</span>
            </div>
            <div v-for="record in records" :key="record.sessionId" class="record-row">
              <span>{{ formatDate(record.startTime) }}</span>
              <span>{{ record.jobName }}</span>
              <span>{{ record.totalScore != null ? record.totalScore + ' 分' : '—' }}</span>
              <span><em :class="statusClass(record.status)">{{ statusLabel(record.status) }}</em></span>
            </div>
          </div>

          <el-empty
            v-else
            description="还没有面试记录，去完成第一次模拟面试吧"
            :image-size="90"
          >
            <el-button type="primary" @click="router.push('/jobs')">开始第一次面试</el-button>
          </el-empty>
        </section>
      </div>

      <aside class="dashboard-side">
        <section class="panel stats-panel">
          <header class="panel-header">
            <h2>我的训练数据</h2>
            <router-link to="/report">能力报告 <el-icon><ArrowRight /></el-icon></router-link>
          </header>

          <div class="stat-list">
            <div class="stat-item">
              <span class="stat-icon blue"><el-icon><VideoCamera /></el-icon></span>
              <div>
                <strong>{{ stats.finishedInterviews }}</strong>
                <span>完成面试（场）</span>
              </div>
            </div>
            <div class="stat-item">
              <span class="stat-icon green"><el-icon><DataAnalysis /></el-icon></span>
              <div>
                <strong>{{ stats.averageScore || '—' }}</strong>
                <span>平均得分</span>
              </div>
            </div>
            <div class="stat-item">
              <span class="stat-icon orange"><el-icon><Aim /></el-icon></span>
              <div>
                <strong>{{ stats.highestScore || '—' }}</strong>
                <span>最高得分</span>
              </div>
            </div>
            <div class="stat-item">
              <span class="stat-icon purple"><el-icon><Reading /></el-icon></span>
              <div>
                <strong>{{ stats.skillCount }}</strong>
                <span>画像技能（个）</span>
              </div>
            </div>
          </div>
        </section>

        <section class="panel profile-panel">
          <header class="panel-header">
            <h2>个人画像</h2>
            <router-link to="/resume">去完善 <el-icon><ArrowRight /></el-icon></router-link>
          </header>
          <div v-if="skills.length" class="skill-tags">
            <span v-for="s in skills" :key="s">{{ s }}</span>
          </div>
          <el-empty v-else description="还没有简历画像，填写简历后自动生成" :image-size="70" />
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Aim,
  ArrowRight,
  CollectionTag,
  DataAnalysis,
  Document,
  Reading,
  VideoCamera
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getJobList, getMyStats, getInterviewRecords, getMyResume } from '@/api'
import dashboardDeco from '@/assets/generated/dashboard-deco-ui.jpg'

const router = useRouter()
const userStore = useUserStore()

const hour = new Date().getHours()
const greeting = computed(() => {
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const displayName = computed(() => userStore.nickname || userStore.username || '同学')

const quickActions = [
  { title: '开始面试', desc: '选择岗位，开启 AI 模拟面试', cta: '立即开始', path: '/jobs', icon: VideoCamera, theme: 'purple' },
  { title: '面试准备', desc: '查看岗位，填写简历画像', cta: '去准备', path: '/jobs', icon: Document, theme: 'blue' },
  { title: '能力报告', desc: '查看历次面试评估结果', cta: '去查看', path: '/report', icon: DataAnalysis, theme: 'green' }
]

// 真实数据
const jobs = ref([])
const jobsLoading = ref(false)
const records = ref([])
const stats = ref({
  finishedInterviews: 0,
  ongoingInterviews: 0,
  averageScore: 0,
  highestScore: 0,
  reportCount: 0,
  skillCount: 0
})
const skills = ref([])

const parseTags = (keywords) => {
  try {
    return JSON.parse(keywords || '[]').slice(0, 3)
  } catch {
    return []
  }
}

const formatDate = (dt) => {
  if (!dt) return '—'
  return String(dt).replace('T', ' ').slice(0, 16)
}

const statusLabel = (s) => ({ FINISHED: '已完成', ONGOING: '进行中', ABORTED: '已中断' }[s] || s)
const statusClass = (s) => ({ FINISHED: 'good', ONGOING: 'ok', ABORTED: 'bad' }[s] || 'ok')

onMounted(async () => {
  jobsLoading.value = true
  try {
    jobs.value = (await getJobList()).slice(0, 3)
  } finally {
    jobsLoading.value = false
  }

  try {
    records.value = (await getInterviewRecords()).slice(0, 5)
  } catch {
    records.value = []
  }

  try {
    stats.value = await getMyStats()
  } catch {
    /* 保持默认 0 */
  }

  try {
    const resume = await getMyResume()
    if (resume && resume.skills) {
      skills.value = JSON.parse(resume.skills).slice(0, 12)
    }
  } catch {
    skills.value = []
  }
})
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

.panel-header a {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #6c7a92;
  font-size: 14px;
  font-weight: 700;
}

.job-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 120px;
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
  color: #3477c8;
  font-size: 31px;
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

.record-table {
  overflow: hidden;
  border: 1px solid #e0e7f1;
  border-radius: 8px;
}

.record-row {
  display: grid;
  grid-template-columns: 1.4fr 1.7fr 0.8fr 0.9fr;
  align-items: center;
  min-height: 64px;
  padding: 0 24px;
  color: #263a5a;
  border-top: 1px solid #e8eef6;
  font-size: 15px;
}

.record-row:first-child {
  border-top: 0;
}

.record-head {
  min-height: 50px;
  color: #63728e;
  background: #f8faff;
  font-weight: 800;
}

.record-row em {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 8px;
  font-style: normal;
  font-weight: 800;
  font-size: 13px;
}

.record-row em.good {
  color: #09a878;
  background: #dbfbef;
}

.record-row em.ok {
  color: #2874ef;
  background: #e7f1ff;
}

.record-row em.bad {
  color: #e8743b;
  background: #fdeee4;
}

.stat-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.stat-item {
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 12px;
  align-items: center;
  padding: 14px;
  background: #f8faff;
  border-radius: 8px;
}

.stat-icon {
  display: grid;
  width: 44px;
  height: 44px;
  color: #fff;
  place-items: center;
  border-radius: 12px;
  font-size: 22px;
}

.stat-icon.blue { background: linear-gradient(135deg, #4a93ff, #196eff); }
.stat-icon.green { background: linear-gradient(135deg, #46dc95, #09bd76); }
.stat-icon.orange { background: linear-gradient(135deg, #ffb347, #ff9a24); }
.stat-icon.purple { background: linear-gradient(135deg, #8c57ff, #5a34ec); }

.stat-item strong {
  display: block;
  color: #122747;
  font-size: 24px;
  font-weight: 900;
}

.stat-item span {
  color: #66758e;
  font-size: 12px;
  font-weight: 700;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tags span {
  padding: 7px 12px;
  color: #3b6ef5;
  background: #eef4ff;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
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
  .stat-list {
    grid-template-columns: 1fr;
  }

  .action-card {
    grid-template-columns: 64px 1fr;
    gap: 18px;
    min-height: auto;
    padding: 22px;
  }

  .record-table {
    overflow-x: auto;
  }

  .record-row {
    min-width: 600px;
  }
}
</style>
