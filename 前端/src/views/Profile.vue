<template>
  <AppLayout>
    <div class="profile-page">
      <!-- Profile Header -->
      <div class="profile-header card">
        <div class="profile-cover"></div>
        <div class="profile-info">
          <div class="profile-avatar">
            <span>{{ avatarChar }}</span>
          </div>
          <div class="profile-details">
            <h1 class="profile-name">{{ nickname }}</h1>
            <p class="profile-bio">{{ accountLine }}</p>
            <div class="profile-stats-row">
              <div class="profile-stat">
                <span class="stat-num">{{ stats.finishedInterviews }}</span>
                <span class="stat-text">已完成面试</span>
              </div>
              <div class="profile-stat">
                <span class="stat-num">{{ stats.averageScore }}</span>
                <span class="stat-text">平均得分</span>
              </div>
              <div class="profile-stat">
                <span class="stat-num">{{ stats.highestScore }}</span>
                <span class="stat-text">最高得分</span>
              </div>
            </div>
          </div>
          <router-link class="edit-btn" :to="{ path: '/settings', query: { tab: 'general' } }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑资料
          </router-link>
        </div>
      </div>

      <p v-if="error" class="profile-alert" role="alert">{{ error }}</p>

      <!-- Content Grid -->
      <div class="profile-grid">
        <!-- Skills & Tags -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">技能标签</h2>
            <router-link class="card-action" to="/resume?step=1">编辑</router-link>
          </div>
          <template v-if="loading">
            <div class="profile-loading">正在加载简历…</div>
          </template>
          <template v-else-if="skills.length">
            <div class="skills-grid">
              <span v-for="skill in skills" :key="skill" class="skill-item">{{ skill }}</span>
            </div>
            <p class="card-foot">共 {{ skills.length }} 项，由简历解析得到</p>
          </template>
          <div v-else class="profile-empty">
            还没有解析出技能标签，<router-link to="/resume?step=1">上传简历</router-link>后自动生成。
          </div>
        </div>

        <!-- Activity Calendar -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">练习日历</h2>
            <!-- 说「35 天」而不是「5 周」：格子按行铺 7 个，
                 每行是连续 7 天，并没有按周一到周日对齐 -->
            <span class="card-subtitle">最近 35 天</span>
          </div>
          <div class="calendar-grid">
            <div
              v-for="day in calendarDays"
              :key="day.key"
              class="calendar-cell"
              :class="'level-' + day.level"
              :title="day.key + '：' + day.count + ' 场'"
            ></div>
          </div>
          <div class="calendar-legend">
            <span class="legend-label">少</span>
            <div class="legend-cell level-0"></div>
            <div class="legend-cell level-1"></div>
            <div class="legend-cell level-2"></div>
            <div class="legend-cell level-3"></div>
            <div class="legend-cell level-4"></div>
            <span class="legend-label">多</span>
          </div>
        </div>

        <!-- Per-job performance -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">岗位表现</h2>
            <span class="card-subtitle">仅统计已完成</span>
          </div>
          <div v-if="jobStats.length" class="jobperf-list">
            <div v-for="item in jobStats" :key="item.name" class="jobperf-row">
              <span class="jobperf-name" :title="item.name">{{ item.name }}</span>
              <div class="jobperf-track">
                <div class="jobperf-bar" :style="{ width: item.avg + '%' }"></div>
              </div>
              <span class="jobperf-count">{{ item.count }}场</span>
              <span class="jobperf-score">{{ item.avg }}</span>
            </div>
          </div>
          <div v-else class="profile-empty">还没有完成的面试，成绩出来后会按岗位汇总。</div>
        </div>

        <!-- Account overview -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">数据概览</h2>
          </div>
          <div class="stat-grid">
            <div class="stat-cell">
              <strong>{{ stats.ongoingInterviews }}</strong>
              <span>进行中面试</span>
            </div>
            <div class="stat-cell">
              <strong>{{ stats.reportCount }}</strong>
              <span>已生成报告</span>
            </div>
            <div class="stat-cell">
              <strong>{{ stats.skillCount }}</strong>
              <span>简历技能</span>
            </div>
            <div class="stat-cell">
              <strong>{{ finishedRate }}</strong>
              <span>完成率</span>
            </div>
          </div>
          <p class="card-foot">每次点「开始面试」都会建一条记录，中途退出不会自动结束，因此「进行中」会比实际练习次数多。</p>
        </div>

        <!-- Career target -->
        <div class="card">
          <div class="card-header">
            <h2 class="card-title">求职目标</h2>
            <span class="card-subtitle">带入面试准备与专项刷题</span>
          </div>
          <form class="career-form" @submit.prevent="saveCareer">
            <label>
              <span>默认目标岗位</span>
              <select v-model="career.targetJobId" :disabled="!jobsLoaded">
                <option :value="null">暂未确定</option>
                <option v-for="job in jobs" :key="job.id" :value="job.id">{{ job.name || job.title }}</option>
              </select>
            </label>
            <label>
              <span>求职阶段</span>
              <select v-model="career.stage">
                <option value="">暂未选择</option>
                <option>实习</option>
                <option>校招</option>
                <option>社招</option>
              </select>
            </label>
            <label>
              <span>目标公司（选填）</span>
              <input v-model.trim="career.targetCompany" maxlength="100" placeholder="例如：正在准备的目标企业" />
            </label>
            <details class="career-education">
              <summary>教育背景（选填）</summary>
              <label>
                <span>学校</span>
                <input v-model.trim="career.school" maxlength="100" autocomplete="organization" />
              </label>
              <label>
                <span>专业</span>
                <input v-model.trim="career.major" maxlength="100" />
              </label>
              <label>
                <span>毕业年份</span>
                <input v-model.trim="career.graduationYear" inputmode="numeric" pattern="(?:19|20|21)[0-9]{2}" maxlength="4" placeholder="例如：2027" />
              </label>
            </details>
            <div class="career-actions">
              <button type="submit" class="career-save" :disabled="careerSaving || !careerLoaded">
                {{ careerSaving ? '正在保存…' : '保存求职目标' }}
              </button>
              <span v-if="careerNotice" class="career-notice" role="status">{{ careerNotice }}</span>
            </div>
          </form>
          <p class="card-foot">保存后，面试准备与专项刷题会默认带入这个方向，每次训练仍可临时调整。</p>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getCareerProfile, getInterviewRecords, getJobList, getMe, getMyResume, getMyStats, saveCareerProfile } from '../api'
import { useUserStore } from '../store/user'
import AppLayout from '../components/layout/AppLayout.vue'

const userStore = useUserStore()

const loading = ref(true)
const error = ref('')
const me = ref(null)
const records = ref([])

// 字段名对齐后端 UserStats（finishedInterviews / ongoingInterviews / averageScore /
// highestScore / reportCount / skillCount）。此前读的是 totalInterviews 和 streakDays，
// 这两个后端根本没有，于是「面试次数」和「连续练习」恒为 0。
const stats = ref({
  finishedInterviews: 0,
  ongoingInterviews: 0,
  averageScore: 0,
  highestScore: 0,
  reportCount: 0,
  skillCount: 0,
})

const nickname = computed(
  () => me.value?.nickname || userStore.nickname || me.value?.username || userStore.username || '用户'
)
const avatarChar = computed(() => (nickname.value.trim().charAt(0) || '用').toUpperCase())
const accountLine = computed(() => {
  const username = me.value?.username || userStore.username
  return username ? `@${username}` : '求职训练用户'
})
const finishedRate = computed(() => {
  const total = stats.value.finishedInterviews + stats.value.ongoingInterviews
  return total ? `${Math.round((stats.value.finishedInterviews / total) * 100)}%` : '—'
})

/* ---------- 技能标签：来自简历解析结果，不再写死 ---------- */
function parseSkillList(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(String).filter(Boolean)
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
  } catch {
    // 后端也可能把 skills 存成逗号分隔的纯文本，退回按分隔符切
  }
  return String(raw).split(/[,，、\n]/).map((s) => s.trim()).filter(Boolean)
}

const skills = computed(() => parseSkillList(me.value?.skillsRaw))

/* ---------- 练习日历：最近 35 天，按真实面试记录计数 ---------- */
const CALENDAR_DAYS = 35

function toDayKey(value) {
  const d = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// 2 场封顶到 level 4，是因为单日跑十几场的极端值会把整张图压成一片浅色
function levelOf(count) {
  if (count <= 0) return 0
  if (count === 1) return 1
  if (count === 2) return 2
  if (count <= 4) return 3
  return 4
}

const calendarDays = computed(() => {
  const counts = new Map()
  records.value.forEach((r) => {
    const key = toDayKey(r.startTime)
    if (key) counts.set(key, (counts.get(key) || 0) + 1)
  })

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days = []
  for (let i = CALENDAR_DAYS - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const key = toDayKey(d)
    const count = counts.get(key) || 0
    days.push({ key, count, level: levelOf(count) })
  }
  return days
})

/* ---------- 岗位表现：按岗位汇总已完成场次与平均分 ---------- */
const jobStats = computed(() => {
  const map = new Map()
  records.value.forEach((r) => {
    if (r.status !== 'FINISHED') return
    if (r.totalScore === null || r.totalScore === undefined) return
    const score = Number(r.totalScore)
    if (!Number.isFinite(score)) return
    const name = r.jobName || '未命名岗位'
    const cur = map.get(name) || { name, count: 0, total: 0 }
    cur.count += 1
    cur.total += score
    map.set(name, cur)
  })
  return [...map.values()]
    .map((j) => ({ name: j.name, count: j.count, avg: Math.round(j.total / j.count) }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6) // 岗位族有 32 个，列太长得翻页，取场次最多的前 6 个
})

/* ---------- 求职目标：保存后喂给 preferredRole / preferredCompany / 预选岗位 ---------- */
const jobs = ref([])
const jobsLoaded = ref(false)
const careerLoaded = ref(false)
const careerSaving = ref(false)
const careerNotice = ref('')
const emptyCareer = () => ({ stage: '', school: '', major: '', graduationYear: '', targetJobId: null, targetCompany: '' })
const career = ref(emptyCareer())

async function saveCareer() {
  careerSaving.value = true
  careerNotice.value = ''
  try {
    const saved = await saveCareerProfile(career.value)
    career.value = { ...emptyCareer(), ...saved }
    careerNotice.value = '已保存，下次准备时自动带入。'
  } catch (e) {
    careerNotice.value = e.message || '保存失败，请重试。'
  } finally {
    careerSaving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  // 各请求分开结算：记录只喂日历和岗位表现，它失败不该让整页变成错误态
  const [meRes, statsRes, resumeRes, recordsRes, careerRes, jobsRes] = await Promise.allSettled([
    getMe(),
    getMyStats(),
    getMyResume(),
    getInterviewRecords(),
    getCareerProfile(),
    getJobList(),
  ])

  if (meRes.status === 'fulfilled') {
    me.value = meRes.value
    userStore.$patch({
      userId: meRes.value?.id,
      username: meRes.value?.username,
      nickname: meRes.value?.nickname || '',
      role: meRes.value?.role,
    })
  } else {
    error.value = '个人信息加载失败，显示的是本机缓存的账号信息。'
  }

  if (statsRes.status === 'fulfilled') {
    const s = statsRes.value || {}
    stats.value = {
      finishedInterviews: s.finishedInterviews ?? 0,
      ongoingInterviews: s.ongoingInterviews ?? 0,
      // 平均分/最高分接口给的是小数，页面上按整数看更清楚
      averageScore: Math.round(s.averageScore ?? 0),
      highestScore: Math.round(s.highestScore ?? 0),
      reportCount: s.reportCount ?? 0,
      skillCount: s.skillCount ?? 0,
    }
  }

  if (resumeRes.status === 'fulfilled') {
    // /resume/mine 的 skills 是一段 JSON 字符串，原样挂在 me 上给 computed 解析
    me.value = { ...(me.value || {}), skillsRaw: resumeRes.value?.skills }
  }

  if (recordsRes.status === 'fulfilled') records.value = recordsRes.value || []

  if (careerRes.status === 'fulfilled') {
    careerLoaded.value = true
    career.value = { ...emptyCareer(), ...careerRes.value }
  }

  if (jobsRes.status === 'fulfilled') {
    jobsLoaded.value = true
    jobs.value = Array.isArray(jobsRes.value) ? jobsRes.value : []
  }

  loading.value = false
})
</script>

<style scoped>
.profile-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-8) 0 var(--space-16);
}

.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  animation: fade-in-up 0.4s var(--ease-out-expo);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}

.card-title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
}

.card-subtitle {
  font-size: var(--text-xs);
  color: var(--neutral-400);
}

.card-action {
  font-size: var(--text-sm);
  color: var(--accent-600);
  background: none;
  border: none;
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
  transition: color var(--duration-fast);
}

.card-action:hover {
  color: var(--accent-500);
}

.card-foot {
  margin-top: var(--space-3);
  font-size: var(--text-xs);
  color: var(--neutral-500);
  line-height: 1.6;
}

/* Career target */
.career-form {
  display: grid;
  gap: var(--space-3);
}

.career-form label {
  display: grid;
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--neutral-600);
}

.career-form select,
.career-form input {
  min-height: 42px;
  padding: 0 var(--space-3);
  color: var(--neutral-900);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  font: inherit;
  font-size: var(--text-sm);
}

.career-form select:focus-visible,
.career-form input:focus-visible {
  outline: 2px solid var(--accent-500);
  outline-offset: 2px;
}

.career-education {
  padding-top: var(--space-3);
  border-top: 1px dashed var(--neutral-200);
}

.career-education summary {
  min-height: 32px;
  color: var(--neutral-600);
  font-size: var(--text-sm);
  cursor: pointer;
}

.career-education summary + label {
  margin-top: var(--space-3);
}

.career-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.career-save {
  min-height: 42px;
  padding: 0 var(--space-4);
  color: #fff;
  background: var(--accent-600);
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
}

.career-save:hover { background: var(--accent-500); }
.career-save:disabled { opacity: .5; cursor: not-allowed; }
.career-notice { font-size: var(--text-sm); color: var(--accent-600); }

.profile-alert {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  color: #92400e;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.profile-loading,
.profile-empty {
  padding: var(--space-6) 0;
  color: var(--neutral-500);
  font-size: var(--text-sm);
  text-align: center;
}

.profile-empty a {
  color: var(--accent-600);
  font-weight: 500;
}

/* Profile Header */
.profile-header {
  margin-bottom: var(--space-6);
  padding: 0;
  overflow: hidden;
}

.profile-cover {
  height: 120px;
  background: linear-gradient(135deg, var(--accent-500), var(--accent-600));
}

.profile-info {
  padding: var(--space-6);
  display: flex;
  align-items: flex-start;
  gap: var(--space-5);
}

.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--accent-500), var(--accent-600));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-2xl);
  font-weight: 700;
  color: white;
  margin-top: -40px;
  border: 4px solid var(--surface-elevated);
  box-shadow: var(--shadow-sm);
}

.profile-details {
  flex: 1;
}

.profile-name {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--neutral-900);
}

.profile-bio {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-top: var(--space-1);
}

.profile-stats-row {
  display: flex;
  gap: var(--space-8);
  margin-top: var(--space-4);
}

.profile-stat {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-family: var(--font-mono);
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--neutral-900);
}

.stat-text {
  font-size: var(--text-xs);
  color: var(--neutral-500);
}

.edit-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition: all var(--duration-normal);
}

.edit-btn:hover {
  border-color: var(--accent-300);
  background: var(--accent-50);
  color: var(--accent-700);
}

/* Profile Grid */
.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
  align-items: start;
}

/* Skills */
.skills-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* 技能不再按 high/mid/low 上色：后端没有难度分级字段，
   之前那三档是写死在模板里的假数据，统一一种样式反而诚实。 */
.skill-item {
  padding: var(--space-2) var(--space-3);
  color: var(--accent-700);
  background: var(--accent-50);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 500;
  transition: transform var(--duration-fast);
}

.skill-item:hover {
  transform: scale(1.05);
}

/* Calendar */
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 3px;
}

.calendar-cell {
  aspect-ratio: 1;
  border-radius: var(--radius-xs);
  transition: transform var(--duration-fast);
}

.calendar-cell:hover {
  transform: scale(1.2);
}

.level-0 { background: var(--neutral-100); }
.level-1 { background: rgba(16, 185, 129, 0.15); }
.level-2 { background: rgba(16, 185, 129, 0.35); }
.level-3 { background: rgba(16, 185, 129, 0.6); }
.level-4 { background: var(--accent-500); }

.calendar-legend {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  justify-content: flex-end;
  margin-top: var(--space-3);
}

.legend-label {
  font-size: 10px;
  color: var(--neutral-400);
}

.legend-cell {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-xs);
}

/* Job performance */
.jobperf-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.jobperf-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.jobperf-name {
  width: 104px;
  flex-shrink: 0;
  overflow: hidden;
  color: var(--neutral-700);
  font-size: var(--text-sm);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.jobperf-track {
  flex: 1;
  height: 8px;
  background: var(--neutral-100);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.jobperf-bar {
  height: 100%;
  background: var(--accent-500);
  border-radius: var(--radius-full);
  transition: width 1s var(--ease-out-expo);
}

.jobperf-count {
  width: 40px;
  flex-shrink: 0;
  text-align: right;
  color: var(--neutral-500);
  font-size: var(--text-xs);
}

.jobperf-score {
  width: 30px;
  flex-shrink: 0;
  text-align: right;
  color: var(--accent-600);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 600;
}

/* Account overview */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.stat-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-3) var(--space-4);
  background: var(--surface-primary);
  border-radius: var(--radius-md);
}

.stat-cell strong {
  color: var(--neutral-900);
  font-family: var(--font-mono);
  font-size: var(--text-lg);
  font-weight: 700;
}

.stat-cell span {
  color: var(--neutral-500);
  font-size: var(--text-xs);
}

@media (max-width: 768px) {
  .profile-grid { grid-template-columns: 1fr; }
  .profile-info { flex-direction: column; }
  .profile-stats-row { gap: var(--space-4); }
  .edit-btn { align-self: flex-start; }
}
</style>
