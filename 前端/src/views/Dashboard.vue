<template>
  <AppLayout>
    <div class="dashboard">
      <div class="ambient-glows" aria-hidden="true">
        <span class="ambient-glow glow-jade"></span>
        <span class="ambient-glow glow-teal"></span>
        <span class="ambient-glow glow-amber"></span>
      </div>
      <div class="ambient-signal" aria-hidden="true">
        <svg viewBox="0 0 1200 340" preserveAspectRatio="none">
          <path class="signal-line signal-line-a" d="M0 185 C90 185 92 118 156 118 S228 238 296 238 366 82 438 82 510 204 578 204 650 134 716 134 784 226 850 226 922 104 992 104 1060 185 1200 185"/>
          <path class="signal-line signal-line-b" d="M0 214 C112 214 118 170 190 170 S280 260 350 260 442 138 520 138 602 235 680 235 762 164 840 164 924 244 1004 244 1080 194 1200 194"/>
        </svg>
        <span class="signal-listening">VOICE SIGNAL · 30 DAYS</span>
      </div>

      <section v-if="loading" class="loading-state" role="status" aria-live="polite" aria-label="正在加载首页数据">
        <span v-for="i in 6" :key="i"></span>
      </section>

      <section v-else-if="errorMessage" class="error-state" role="alert">
        <div class="error-mark">!</div>
        <h1>首页数据暂时无法加载</h1>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="loadOverview">重新加载</button>
      </section>

      <template v-else>
        <header class="page-heading">
          <div>
            <p>{{ greeting }}，{{ displayName }}</p>
            <h1>今天从哪里继续？</h1>
          </div>
          <span class="updated-at">数据更新于 {{ updatedAt }}</span>
        </header>

        <section class="overview-panel">
          <div class="next-action">
            <span class="action-kicker">下一步建议</span>
            <h2>{{ overview.nextAction.title }}</h2>
            <p>{{ overview.nextAction.description }}</p>
            <router-link to="/jobs" class="primary-action">
              <span>开始面试</span>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>
            </router-link>
            <div class="action-orbit" aria-hidden="true">
              <span></span><span></span><span></span>
            </div>
          </div>

          <div class="summary-grid">
            <article v-for="item in summaryItems" :key="item.label" class="summary-item">
              <div class="summary-icon" v-html="item.icon"></div>
              <div>
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
                <small>{{ item.detail }}</small>
              </div>
            </article>
          </div>
        </section>

        <section class="trend-section surface">
          <div class="section-heading">
            <div>
              <span class="section-kicker">近 30 天</span>
              <h2>训练节奏</h2>
              <span v-if="scoreChangeLabel" class="trend-change" :class="{ positive: scoreChange > 0, negative: scoreChange < 0 }">
                {{ scoreChangeLabel }}
              </span>
            </div>
            <div class="trend-summary">
              <strong>{{ overview.summary.recentCount }}</strong>
              <span>场已完成</span>
            </div>
          </div>

          <div v-if="hasTrend" class="trend-chart" role="img" :aria-label="trendDescription">
            <div class="chart-grid" aria-hidden="true"><span></span><span></span><span></span></div>
            <svg viewBox="0 0 900 170" preserveAspectRatio="none">
              <polyline v-for="segment in scoreSegments" :key="segment" :points="segment" fill="none" stroke="#0d916a" stroke-width="3"
                stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
              <g v-for="point in chartPoints" :key="point.date">
                <circle v-if="point.score > 0" :cx="point.x" :cy="point.y" r="4" fill="#fff"
                  stroke="#0d916a" stroke-width="2" vector-effect="non-scaling-stroke" />
              </g>
            </svg>
            <div class="score-axis" aria-hidden="true"><span>100</span><span>50</span><span>0</span></div>
            <div v-if="peakPoint" class="trend-peak" :style="{ left: `${peakPoint.left}%`, top: `${peakPoint.top}%` }" aria-hidden="true">
              <strong>本月最佳</strong>
              <span>{{ formatScore(peakPoint.score) }} 分 · {{ formatMonthDay(peakPoint.date) }}</span>
            </div>
            <div class="volume-bars" aria-hidden="true">
              <span v-for="day in overview.trend" :key="day.date"
                :style="{ height: `${Math.max(4, (day.count / maxDailyCount) * 44)}px` }"
                :class="{ active: day.count > 0 }"></span>
            </div>
            <div class="chart-labels">
              <span v-for="label in trendLabels" :key="label">{{ label }}</span>
            </div>
            <div class="chart-legend">
              <span><i class="line-key"></i>平均得分</span>
              <span><i class="bar-key"></i>完成场次</span>
            </div>
          </div>

          <div v-else class="trend-empty">
            <div class="empty-bars" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
            <div>
              <strong>完成一次面试后，这里会形成你的训练趋势</strong>
              <span>趋势只使用真实完成记录，不展示模拟数据。</span>
            </div>
          </div>
        </section>

        <div class="content-grid">
          <section class="recent-section surface">
            <div class="section-heading">
              <div>
                <span class="section-kicker">训练记录</span>
                <h2>最近面试</h2>
              </div>
              <router-link to="/history" class="text-link">查看全部</router-link>
            </div>

            <div v-if="overview.recentInterviews.length" class="interview-list">
              <article v-for="item in overview.recentInterviews" :key="item.sessionId" class="interview-row">
                <JobLogo :icon-key="item.iconKey" :tone="item.themeKey" />
                <div class="interview-copy">
                  <div>
                    <h3>{{ item.jobName }}</h3>
                    <span class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
                  </div>
                  <p>{{ interviewMeta(item) }}</p>
                </div>
                <strong v-if="item.score !== null && item.score !== undefined" class="interview-score">{{ formatScore(item.score) }}</strong>
                <router-link :to="recordRoute(item)" class="row-action" :aria-label="`${recordAction(item)}${item.jobName}`">
                  {{ recordAction(item) }}
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>
                </router-link>
              </article>
            </div>

            <div v-else class="empty-state">
              <div class="empty-icon">
                <svg viewBox="0 0 48 48" aria-hidden="true"><path d="M11 13h26v22H11zM17 9h14v8H17zM17 23h14M17 29h9"/></svg>
              </div>
              <div>
                <strong>还没有面试记录</strong>
                <p>选择一个目标岗位，完成第一场可复盘的模拟面试。</p>
              </div>
              <router-link to="/jobs">选择岗位</router-link>
            </div>
          </section>

          <section class="insight-section surface">
            <template v-if="overview.latestInsight">
              <div class="section-heading">
                <div>
                  <span class="section-kicker">最近一次</span>
                  <h2>复盘摘要</h2>
                </div>
                <div class="report-score">
                  <span>本次得分</span>
                  <div><strong>{{ formatScore(overview.latestInsight.totalScore) }}</strong><small>/ 100</small></div>
                  <em v-if="reportDeltaLabel">{{ reportDeltaLabel }}</em>
                </div>
              </div>
              <p class="insight-job">{{ overview.latestInsight.jobName }}</p>
              <div class="dimension-list">
                <div v-for="dimension in overview.latestInsight.dimensions" :key="dimension.dimension" class="dimension-row">
                  <div><span>{{ shortDimension(dimension.dimension) }}</span><b>{{ formatScore(dimension.score) }}</b></div>
                  <span class="dimension-track"><i :style="{ transform: `scaleX(${Number(dimension.score) / 100})` }"></i></span>
                </div>
              </div>
              <div class="insight-note">
                <span>优先改进</span>
                <strong>{{ overview.latestInsight.weakestDimension }}</strong>
                <p>{{ overview.latestInsight.suggestion || '进入完整报告查看本次训练的改进建议。' }}</p>
              </div>
              <router-link :to="`/history/${overview.latestInsight.reportId}`" class="secondary-action">查看完整报告</router-link>
            </template>

            <template v-else>
              <span class="section-kicker">能力反馈</span>
              <h2>完成训练后查看真实复盘</h2>
              <div class="insight-placeholder" aria-hidden="true">
                <i style="--value:.74"></i><i style="--value:.58"></i><i style="--value:.82"></i><i style="--value:.66"></i>
              </div>
              <p class="placeholder-copy">报告将依据回答内容，从专业知识、项目表达、逻辑与岗位匹配等维度生成。</p>
              <router-link to="/jobs" class="secondary-action">开始一次面试</router-link>
            </template>
          </section>
        </div>

      </template>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import JobLogo from '../components/jobs/JobLogo.vue'
import { getDashboardOverview } from '../api'
import { useUserStore } from '../store/user'

const userStore = useUserStore()
const loading = ref(true)
const errorMessage = ref('')
const updatedAt = ref('')
const overview = ref({
  summary: { completedCount: 0, recentCount: 0, averageScore: 0, bestScore: 0, bestJobName: '', streakDays: 0 },
  nextAction: { type: '', title: '', description: '', route: '/jobs' },
  trend: [],
  recentInterviews: [],
  latestInsight: null,
})

const displayName = computed(() => userStore.nickname || userStore.username || '同学')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const summaryItems = computed(() => [
  {
    label: '累计完成',
    value: `${overview.value.summary.completedCount} 场`,
    detail: `近 30 天 ${overview.value.summary.recentCount} 场`,
    icon: '<svg viewBox="0 0 24 24"><path d="M8 4h8M9 3h6v4H9zM6 5H4v16h16V5h-2M8 12l3 3 5-6"/></svg>',
  },
  {
    label: '平均得分',
    value: overview.value.summary.averageScore ? scoreWithScale(overview.value.summary.averageScore) : '—',
    detail: overview.value.summary.averageScore ? '基于全部有效报告' : '暂无评分记录',
    icon: '<svg viewBox="0 0 24 24"><path d="M4 19V9M10 19V5M16 19v-7M22 19H2"/></svg>',
  },
  {
    label: '最佳成绩',
    value: overview.value.summary.bestScore ? scoreWithScale(overview.value.summary.bestScore) : '—',
    detail: overview.value.summary.bestJobName || '等待首次训练',
    icon: '<svg viewBox="0 0 24 24"><path d="m12 3 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/></svg>',
  },
  {
    label: '连续练习',
    value: `${overview.value.summary.streakDays} 天`,
    detail: overview.value.summary.streakDays ? '保持当前训练节奏' : '从今天开始记录',
    icon: '<svg viewBox="0 0 24 24"><path d="M12 22c5 0 8-3.5 8-8 0-4-2-7-5-10 0 4-2 6-4 7 0-3-1-5-3-7 0 4-4 6-4 11 0 4 3 7 8 7z"/></svg>',
  },
])

const hasTrend = computed(() => overview.value.trend.some(day => day.count > 0))
const maxDailyCount = computed(() => Math.max(1, ...overview.value.trend.map(day => day.count)))
const chartPoints = computed(() => {
  const days = overview.value.trend
  return days.map((day, index) => ({
    date: day.date,
    score: Number(day.averageScore || 0),
    x: days.length > 1 ? (index / (days.length - 1)) * 900 : 0,
    y: 150 - (Number(day.averageScore || 0) / 100) * 125,
  }))
})
const scoreSegments = computed(() => {
  const segments = []
  for (let index = 1; index < chartPoints.value.length; index += 1) {
    const previous = chartPoints.value[index - 1]
    const current = chartPoints.value[index]
    if (previous.score > 0 && current.score > 0) {
      segments.push(`${previous.x},${previous.y} ${current.x},${current.y}`)
    }
  }
  return segments
})
const trendLabels = computed(() => {
  const days = overview.value.trend
  if (!days.length) return []
  return [0, 7, 14, 21, 29].filter(index => days[index]).map(index => formatMonthDay(days[index].date))
})
const trendDescription = computed(() => `近 30 天完成 ${overview.value.summary.recentCount} 场面试，折线表示每日平均得分，柱形表示完成场次。`)
const scoredTrendPoints = computed(() => chartPoints.value.filter(point => point.score > 0))
const scoreChange = computed(() => {
  const points = scoredTrendPoints.value
  if (points.length < 2) return 0
  return Number((points.at(-1).score - points.at(-2).score).toFixed(1))
})
const scoreChangeLabel = computed(() => {
  if (!scoreChange.value) return ''
  return `最近一次较上次 ${scoreChange.value > 0 ? '+' : ''}${formatScore(scoreChange.value)} 分`
})
const peakPoint = computed(() => {
  if (!scoredTrendPoints.value.length) return null
  const point = scoredTrendPoints.value.reduce((best, current) => current.score > best.score ? current : best)
  return {
    ...point,
    left: (point.x / 900) * 100,
    top: 4 + (point.y / 170) * 72,
  }
})
const reportDeltaLabel = computed(() => {
  const reportScore = Number(overview.value.latestInsight?.totalScore || 0)
  const average = Number(overview.value.summary.averageScore || 0)
  if (!reportScore || !average) return ''
  const delta = Number((reportScore - average).toFixed(1))
  if (!delta) return '与历史平均持平'
  return `较历史平均 ${delta > 0 ? '+' : ''}${formatScore(delta)}`
})

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await getDashboardOverview()
    updatedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date())
  } catch (error) {
    errorMessage.value = error.response?.data?.message || (error.request ? '暂时无法连接服务，请稍后重试。' : '首页数据处理失败，请重新加载。')
  } finally {
    loading.value = false
  }
}

function formatScore(value) {
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(1)
}

function scoreWithScale(value) {
  return `${formatScore(value)} / 100`
}

function statusText(status) {
  return { FINISHED: '已完成', ONGOING: '进行中', ABORTED: '已中断' }[status] || status
}

function statusClass(status) {
  return { FINISHED: 'finished', ONGOING: 'ongoing', ABORTED: 'aborted' }[status] || 'aborted'
}

function difficultyText(value) {
  return { 1: '基础', 2: '进阶', 3: '挑战' }[value] || '标准'
}

function formatDuration(seconds) {
  const minutes = Math.max(1, Math.round(Number(seconds || 0) / 60))
  return `${minutes} 分钟`
}

function interviewMeta(item) {
  const duration = item.status === 'ONGOING' ? '训练进行中' : formatDuration(item.durationSeconds)
  return `${difficultyText(item.difficulty)} · ${duration} · ${formatDate(item.startTime)}`
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  const diff = Date.now() - date.getTime()
  if (diff < 86400000) return '今天'
  if (diff < 172800000) return '昨天'
  return formatMonthDay(value)
}

function formatMonthDay(value) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value))
}

function recordRoute(item) {
  if (item.status === 'ONGOING') return '/interview'
  return item.reportId ? `/history/${item.reportId}` : '/history'
}

function recordAction(item) {
  return item.status === 'ONGOING' ? '继续' : '查看'
}

function shortDimension(value) {
  return String(value || '').replace('能力', '').replace('程度', '').replace('掌握', '')
}

onMounted(loadOverview)
</script>

<style scoped>
.dashboard {
  position: relative;
  isolation: isolate;
  max-width: 1240px;
  margin: 0 auto;
  padding: 38px 0 72px;
  color: #29473f;
}

.dashboard::before {
  position: absolute;
  z-index: -3;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 100vw;
  content: '';
  transform: translateX(-50%);
  background: linear-gradient(180deg, rgba(235,246,241,.42), transparent 38rem);
  mask-image: linear-gradient(to bottom, #000 0, rgba(0,0,0,.82) 34rem, transparent 76rem);
  pointer-events: none;
}

.ambient-glows {
  position: absolute;
  z-index: -2;
  inset: -40px calc(50% - 50vw);
  overflow: hidden;
  pointer-events: none;
}

.ambient-glow {
  position: absolute;
  width: 560px;
  height: 560px;
  border-radius: 50%;
  filter: blur(18px);
  opacity: .44;
  will-change: transform;
}

.glow-jade {
  top: -80px;
  left: -120px;
  background: radial-gradient(circle, rgba(36,148,108,.2), rgba(36,148,108,.07) 42%, transparent 70%);
  animation: driftJade 18s ease-in-out infinite alternate;
}

.glow-teal {
  top: 34%;
  right: -180px;
  width: 640px;
  height: 640px;
  background: radial-gradient(circle, rgba(47,157,151,.17), rgba(47,157,151,.055) 45%, transparent 72%);
  animation: driftTeal 24s ease-in-out infinite alternate;
}

.glow-amber {
  top: 68%;
  left: 12%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(218,153,87,.15), rgba(218,153,87,.045) 46%, transparent 72%);
  animation: driftAmber 30s ease-in-out infinite alternate;
}

.ambient-signal {
  position: absolute;
  z-index: -2;
  top: 42px;
  right: -7vw;
  left: 28%;
  height: 360px;
  overflow: hidden;
  opacity: .58;
  pointer-events: none;
  mask-image: linear-gradient(90deg, transparent, #000 20%, #000 78%, transparent);
}

.ambient-signal svg { width: 100%; height: 100%; }
.signal-line {
  fill: none;
  stroke: #23896c;
  stroke-width: 1.15;
  vector-effect: non-scaling-stroke;
  stroke-dasharray: 8 14;
  animation: signalTravel 18s linear infinite;
}
.signal-line-b { stroke: #d99a5b; stroke-width: .8; opacity: .46; animation-duration: 26s; animation-direction: reverse; }
.signal-listening { position: absolute; right: 13%; bottom: 48px; color: #477369; font-size: 10px; font-weight: 700; letter-spacing: .13em; }

@keyframes signalTravel { to { stroke-dashoffset: -220; } }
@keyframes driftJade {
  0% { transform: translate3d(0,0,0) scale(.92); }
  45% { transform: translate3d(34vw,260px,0) scale(1.08); }
  100% { transform: translate3d(62vw,560px,0) scale(.96); }
}
@keyframes driftTeal {
  0% { transform: translate3d(0,0,0) scale(1); }
  50% { transform: translate3d(-42vw,340px,0) scale(.9); }
  100% { transform: translate3d(-70vw,-120px,0) scale(1.12); }
}
@keyframes driftAmber {
  0% { transform: translate3d(0,0,0) scale(.9); }
  52% { transform: translate3d(48vw,-280px,0) scale(1.08); }
  100% { transform: translate3d(12vw,-620px,0) scale(.98); }
}

.page-heading, .section-heading, .summary-item, .interview-row, .quick-actions a {
  display: flex;
  align-items: center;
}

.page-heading {
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-heading p, .section-kicker, .action-kicker {
  margin: 0 0 5px;
  color: #168061;
  font-size: 12px;
  font-weight: 700;
}

.page-heading h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.8rem, 3vw, 2.55rem);
  letter-spacing: -.035em;
}

.updated-at { color: #536b63; font-size: 13px; }

.overview-panel {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.08fr 1.45fr;
  min-height: 310px;
  overflow: hidden;
  border-radius: 24px;
  background: #edf6f2;
  box-shadow: 0 24px 70px rgba(45, 91, 76, .06);
}

.next-action {
  position: relative;
  overflow: hidden;
  padding: 44px;
  background: #e5f2ec;
}

.next-action h2 {
  position: relative;
  z-index: 1;
  max-width: 14ch;
  margin: 12px 0;
  font-family: var(--font-display);
  font-size: clamp(1.7rem, 3vw, 2.5rem);
  line-height: 1.17;
  letter-spacing: -.035em;
}

.next-action > p {
  position: relative;
  z-index: 1;
  max-width: 40ch;
  margin: 0;
  color: #5b746b;
  font-size: 14px;
  line-height: 1.7;
}

.primary-action, .secondary-action {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
  border-radius: 12px;
  text-decoration: none;
  font-size: 14px;
  font-weight: 700;
}

.primary-action {
  padding: 13px 18px;
  background: #258065;
  color: #fff;
  box-shadow: 0 12px 28px rgba(37, 128, 101, .16);
  transition: transform 220ms ease, background 220ms ease;
}

.primary-action:hover { background: #1d7058; transform: translateY(-2px); }
.primary-action svg, .row-action svg, .quick-arrow { width: 18px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }

.action-orbit { position: absolute; right: -90px; bottom: -110px; width: 330px; height: 330px; border: 1px solid rgba(12,121,90,.13); border-radius: 50%; }
.action-orbit span { position: absolute; border: 1px solid rgba(12,121,90,.11); border-radius: 50%; }
.action-orbit span:nth-child(1) { inset: 42px; }.action-orbit span:nth-child(2) { inset: 92px; }.action-orbit span:nth-child(3) { inset: 135px; background: rgba(12,121,90,.08); }

.summary-grid { display: grid; grid-template-columns: 1fr 1fr; }
.summary-item { gap: 16px; min-width: 0; padding: 32px; border-bottom: 1px solid rgba(20,75,59,.09); border-left: 1px solid rgba(20,75,59,.09); }
.summary-item:nth-child(n+3) { border-bottom: 0; }
.summary-icon { display: grid; width: 43px; height: 43px; flex: 0 0 43px; place-items: center; border-radius: 13px; background: rgba(255,255,255,.7); color: #167b5e; }
.summary-icon :deep(svg) { width: 22px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.summary-item div:last-child { display: flex; min-width: 0; flex-direction: column; }
.summary-item span { color: #526b62; font-size: 13px; }
.summary-item strong { margin: 5px 0 3px; color: #2a4c42; font-family: var(--font-display); font-size: clamp(1.3rem, 2.2vw, 1.65rem); line-height: 1; white-space: nowrap; }
.summary-item small { overflow: hidden; color: #587067; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }

.surface { position: relative; z-index: 1; border: 1px solid #dde7e2; border-radius: 20px; background: rgba(255,255,255,.94); box-shadow: 0 18px 55px rgba(30,75,61,.045); }
.trend-section { margin-top: 20px; padding: 28px 30px 24px; }
.section-heading { justify-content: space-between; }
.section-heading h2, .insight-section > h2 { margin: 0; font-family: var(--font-display); font-size: 1.3rem; letter-spacing: -.02em; }
.trend-change { display: inline-flex; margin-top: 9px; padding: 4px 8px; border-radius: 7px; background: #eef3f1; color: #536b63; font-size: 11px; font-weight: 700; }
.trend-change.positive { background: #e5f4ed; color: #0c7456; }
.trend-change.negative { background: #f7ebe6; color: #9a5138; }
.trend-summary { text-align: right; }.trend-summary strong { display: block; color: #0c795a; font-size: 1.35rem; }.trend-summary span { color: #587067; font-size: 12px; }

.trend-chart { position: relative; height: 245px; margin-top: 18px; padding: 8px 0 50px; }
.trend-chart > svg { position: absolute; inset: 8px 0 55px; z-index: 2; width: 100%; height: calc(100% - 63px); overflow: visible; }
.trend-chart polyline { stroke-dasharray: 1000; stroke-dashoffset: 1000; animation: drawScore 1.15s cubic-bezier(.16,1,.3,1) forwards; }
.trend-chart circle { opacity: 0; animation: revealPoint 280ms ease-out 740ms forwards; }
.chart-grid { position: absolute; inset: 8px 0 55px; display: flex; flex-direction: column; justify-content: space-between; }
.chart-grid span { border-top: 1px dashed #e4ebe7; }
.score-axis { position: absolute; top: 7px; bottom: 54px; left: -27px; display: flex; flex-direction: column; justify-content: space-between; color: #60766e; font-size: 11px; }
.trend-peak {
  position: absolute;
  z-index: 4;
  display: flex;
  min-width: 110px;
  flex-direction: column;
  padding: 8px 10px;
  transform: translate(-50%, calc(-100% - 9px));
  border: 1px solid rgba(13,145,106,.18);
  border-radius: 10px;
  background: rgba(248,252,250,.96);
  box-shadow: 0 10px 30px rgba(22,85,65,.12);
  pointer-events: none;
  animation: peakArrive 520ms cubic-bezier(.16,1,.3,1) 700ms both;
}
.trend-peak::after { position: absolute; bottom: -5px; left: calc(50% - 4px); width: 8px; height: 8px; content: ''; transform: rotate(45deg); border-right: 1px solid rgba(13,145,106,.18); border-bottom: 1px solid rgba(13,145,106,.18); background: #f8fcfa; }
.trend-peak strong { color: #0c7456; font-size: 11px; }
.trend-peak span { margin-top: 2px; color: #526b62; font-size: 10px; }
.volume-bars { position: absolute; right: 0; bottom: 55px; left: 0; display: flex; align-items: flex-end; justify-content: space-between; height: 44px; opacity: .9; }
.volume-bars span { width: max(4px, 1.25%); min-height: 4px; transform-origin: bottom; border-radius: 3px 3px 0 0; background: #e3ebe7; animation: riseBar 620ms cubic-bezier(.16,1,.3,1) both; }.volume-bars span.active { background: #b8d9cc; }
.chart-labels { position: absolute; right: 0; bottom: 28px; left: 0; display: flex; justify-content: space-between; color: #60766e; font-size: 12px; }
.chart-legend { position: absolute; right: 0; bottom: 0; display: flex; gap: 18px; color: #526b62; font-size: 12px; }
.chart-legend span { display: flex; align-items: center; gap: 6px; }.line-key { width: 18px; border-top: 2px solid #0d916a; }.bar-key { width: 12px; height: 7px; border-radius: 2px; background: #b8d9cc; }

.trend-empty { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: 32px; }
.empty-bars { display: flex; height: 80px; align-items: flex-end; gap: 8px; }.empty-bars i { width: 9px; border-radius: 4px 4px 0 0; background: #dfeae5; }.empty-bars i:nth-child(1) { height: 25%; }.empty-bars i:nth-child(2) { height: 48%; }.empty-bars i:nth-child(3) { height: 35%; }.empty-bars i:nth-child(4) { height: 72%; }.empty-bars i:nth-child(5) { height: 55%; }
.trend-empty strong, .trend-empty span { display: block; }.trend-empty strong { margin-bottom: 8px; }.trend-empty span { color: #71847d; font-size: 13px; }

.content-grid { display: grid; grid-template-columns: 1.55fr .85fr; gap: 20px; margin-top: 20px; }
.recent-section, .insight-section { padding: 28px; }
.text-link { color: #187b60; font-size: 12px; font-weight: 700; text-decoration: none; }
.interview-list { margin-top: 18px; }
.interview-row { gap: 16px; min-height: 83px; border-bottom: 1px solid #edf1ef; }
.interview-row:last-child { border-bottom: 0; }
.interview-copy { min-width: 0; flex: 1; }.interview-copy > div { display: flex; align-items: center; gap: 9px; }.interview-copy h3 { overflow: hidden; margin: 0; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.interview-copy p { margin: 7px 0 0; color: #526b62; font-size: 12px; }
.status { padding: 3px 7px; border-radius: 999px; font-size: 11px; font-weight: 700; }.status.finished { background: #e7f4ee; color: #0d7959; }.status.ongoing { background: #fff2d8; color: #80510d; }.status.aborted { background: #f1f2f2; color: #596964; }
.interview-score { min-width: 38px; color: #173f34; font-family: var(--font-display); font-size: 1.22rem; text-align: right; }
.row-action { display: flex; min-height: 44px; align-items: center; gap: 2px; padding: 8px 9px; color: #526b62; font-size: 13px; font-weight: 650; text-decoration: none; }.row-action:hover { color: #0c795a; }

.empty-state { display: flex; min-height: 250px; align-items: center; gap: 18px; }.empty-icon { display: grid; width: 58px; height: 58px; flex: 0 0 58px; place-items: center; border-radius: 16px; background: #eaf4ef; color: #2b8168; }.empty-icon svg { width: 32px; fill: none; stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-linejoin: round; }
.empty-state div:nth-child(2) { flex: 1; }.empty-state strong { font-size: 14px; }.empty-state p { margin: 6px 0 0; color: #71847d; font-size: 12px; }.empty-state a { color: #0c795a; font-size: 12px; font-weight: 700; text-decoration: none; }

.insight-section { overflow: hidden; }
.insight-section::before { position: absolute; top: -110px; right: -105px; width: 270px; height: 270px; content: ''; border: 1px solid rgba(20,121,89,.1); border-radius: 50%; box-shadow: inset 0 0 0 42px rgba(25,137,105,.025), inset 0 0 0 84px rgba(25,137,105,.022); pointer-events: none; }
.report-score { position: relative; z-index: 1; min-width: 118px; text-align: right; }
.report-score > span { display: block; color: #526b62; font-size: 11px; }
.report-score div { display: flex; align-items: baseline; justify-content: flex-end; gap: 4px; }
.report-score strong { color: #0c795a; font-family: var(--font-display); font-size: 2.15rem; line-height: 1; }
.report-score small { color: #526b62; font-size: 11px; }
.report-score em { display: inline-block; margin-top: 5px; color: #0c7456; font-size: 10px; font-style: normal; font-weight: 700; }
.insight-job { margin: 12px 0 20px; color: #526b62; font-size: 13px; }
.dimension-list { display: flex; flex-direction: column; gap: 12px; }.dimension-row > div { display: flex; justify-content: space-between; margin-bottom: 5px; color: #526b62; font-size: 12px; }.dimension-row b { color: #25483e; }.dimension-track { display: block; height: 5px; overflow: hidden; border-radius: 4px; background: #edf2f0; }.dimension-track i { display: block; width: 100%; height: 100%; transform-origin: left; border-radius: inherit; background: linear-gradient(90deg,#1b8366,#42a487); animation: dimensionReveal 760ms cubic-bezier(.16,1,.3,1) both; }
.dimension-row:nth-child(2) .dimension-track i { animation-delay: 70ms; }.dimension-row:nth-child(3) .dimension-track i { animation-delay: 140ms; }.dimension-row:nth-child(4) .dimension-track i { animation-delay: 210ms; }.dimension-row:nth-child(5) .dimension-track i { animation-delay: 280ms; }
.insight-note { margin-top: 22px; padding: 16px; border-radius: 14px; background: #f2f7f5; }.insight-note span { color: #526b62; font-size: 12px; }.insight-note strong { display: block; margin: 4px 0 7px; font-size: 13px; }.insight-note p { margin: 0; color: #526b62; font-size: 12px; line-height: 1.65; }
.secondary-action { width: 100%; padding: 12px 0; border: 1px solid #cfe0d9; color: #0d7959; }
.insight-placeholder { display: flex; height: 145px; align-items: flex-end; justify-content: center; gap: 16px; margin: 24px 0 16px; }.insight-placeholder i { width: 22px; height: calc(var(--value) * 100%); border-radius: 8px 8px 2px 2px; background: #d4e8df; }
.placeholder-copy { color: #6e817a; font-size: 12px; line-height: 1.7; }

.loading-state { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; padding-top: 38px; }.loading-state span { min-height: 190px; border-radius: 20px; background: linear-gradient(100deg,#edf2f0 25%,#f7f9f8 45%,#edf2f0 65%); background-size: 220% 100%; animation: loading 1.4s ease infinite; }.loading-state span:first-child { grid-column: 1 / -1; min-height: 310px; }
@keyframes loading { to { background-position-x: -220%; } }
@keyframes drawScore { to { stroke-dashoffset: 0; } }
@keyframes revealPoint { to { opacity: 1; } }
@keyframes riseBar { from { transform: scaleY(.12); opacity: .3; } }
@keyframes peakArrive { from { opacity: 0; transform: translate(-50%, calc(-100% - 2px)); filter: blur(4px); } }
@keyframes dimensionReveal { from { transform: scaleX(0); } }
.error-state { max-width: 520px; margin: 120px auto 0; text-align: center; }.error-mark { display: grid; width: 54px; height: 54px; margin: 0 auto 18px; place-items: center; border-radius: 16px; background: #f5e7e3; color: #a94f38; font-size: 24px; font-weight: 800; }.error-state h1 { margin: 0 0 8px; }.error-state p { color: #71847d; }.error-state button { margin-top: 16px; padding: 11px 17px; border: 0; border-radius: 10px; background: #0c795a; color: #fff; font-weight: 700; cursor: pointer; }

@media (max-width: 1020px) {
  .overview-panel { grid-template-columns: 1fr; }
  .next-action { min-height: 270px; }
  .summary-item:first-child, .summary-item:nth-child(3) { border-left: 0; }
  .content-grid { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .dashboard { padding: 24px 0 48px; }
  .dashboard::before { mask-image: linear-gradient(to bottom,#000 0,rgba(0,0,0,.7) 42rem,transparent 80rem); }
  .ambient-glow { width: 390px; height: 390px; opacity: .34; filter: blur(14px); }
  .glow-teal { width: 430px; height: 430px; }
  .glow-amber { width: 360px; height: 360px; opacity: .28; }
  .ambient-signal { top: 80px; right: -24px; left: -24px; height: 300px; opacity: .34; }
  .signal-listening { display: none; }
  .page-heading { align-items: flex-end; }.updated-at { display: none; }
  .overview-panel { border-radius: 20px; }.next-action { min-height: 300px; padding: 30px 24px; }
  .summary-grid { grid-template-columns: 1fr 1fr; }.summary-item { min-height: 148px; align-items: flex-start; flex-direction: column; gap: 10px; padding: 18px; border-bottom: 1px solid rgba(20,75,59,.09) !important; }.summary-item:nth-child(odd) { border-left: 0; }.summary-item:nth-child(even) { border-left: 1px solid rgba(20,75,59,.09); }.summary-item:nth-child(n+3) { border-bottom: 0 !important; }
  .summary-icon { width: 38px; height: 38px; flex-basis: 38px; }
  .trend-section, .recent-section, .insight-section { padding: 22px 18px; }
  .trend-chart { height: 220px; }
  .trend-peak { min-width: 96px; padding: 6px 8px; }
  .trend-peak span { font-size: 9px; }
  .interview-row { flex-wrap: wrap; padding: 15px 0; }.interview-copy { min-width: calc(100% - 72px); }.interview-score { margin-left: 68px; }.row-action { margin-left: auto; }
}

@media (prefers-reduced-motion: reduce) {
  .loading-state span { animation: none; }
  .ambient-glow, .signal-line, .trend-chart polyline, .trend-chart circle, .volume-bars span, .trend-peak, .dimension-track i { animation: none; }
  .glow-jade { transform: translate3d(22vw,180px,0); }
  .glow-teal { transform: translate3d(-28vw,120px,0); }
  .glow-amber { transform: translate3d(20vw,-180px,0); }
  .trend-chart polyline { stroke-dashoffset: 0; }
  .trend-chart circle { opacity: 1; }
  .dimension-track i, .primary-action { transition: none; }
}
</style>
