<template>
  <AppLayout>
    <div class="dashboard">
      <!-- Greeting Strip -->
      <section class="hero-strip">
        <div class="strip-left">
          <div class="bubble-wrap" @mouseenter="cycleQuote">
            <div class="ai-bubble">
              <div class="bubble-avatar">AI</div>
              <div class="bubble-body">
                <p class="bubble-text">{{ currentQuote }}</p>
                <span class="bubble-tag">面试官寄语</span>
              </div>
            </div>
          </div>

          <div class="hero-info">
            <p class="hero-time">{{ greeting }}</p>
            <h1 class="hero-name">{{ displayName }}</h1>
          </div>

          <router-link to="/jobs" class="hero-cta">
            <span class="cta-ring">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="5 3 19 12 5 21 5 3"/></svg>
            </span>
            <span class="cta-label">开始面试</span>
          </router-link>
        </div>

        <div class="strip-right">
          <!-- Today's Todo -->
          <div class="todo-card">
            <span class="mini-label">今日待办</span>
            <div class="todo-list">
              <label v-for="(t, i) in todos" :key="i" class="todo-item" :class="{ done: t.done }">
                <input type="checkbox" v-model="t.done" />
                <span class="todo-check"></span>
                <span class="todo-text">{{ t.text }}</span>
              </label>
            </div>
          </div>
        </div>
      </section>

      <!-- Stats Row -->
      <section class="stats-row">
        <div
          v-for="(stat, i) in stats"
          :key="i"
          class="stat-card reveal-item"
          :style="{ '--delay': i * 80 + 'ms', '--depth': stat.depth }"
        >
          <div class="stat-top">
            <span class="stat-label">{{ stat.label }}</span>
            <span class="stat-trend" :class="stat.trendType">{{ stat.trend }}</span>
          </div>
          <span class="stat-num">{{ stat.display }}</span>

          <!-- Progress bar -->
          <div v-if="stat.progress !== undefined" class="stat-bar">
            <div class="stat-bar-fill" :style="{ width: stat.progress + '%' }"></div>
          </div>

          <!-- Trend chart -->
          <div class="trend-chart">
            <svg viewBox="0 0 200 60" preserveAspectRatio="none" class="trend-svg">
              <defs>
                <linearGradient :id="'tg' + i" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" :stop-color="stat.depth" stop-opacity="0.3"/>
                  <stop offset="100%" :stop-color="stat.depth" stop-opacity="0"/>
                </linearGradient>
              </defs>
              <path :d="trendArea(stat.trendData)" :fill="`url(#tg${i})`"/>
              <polyline :points="trendLine(stat.trendData)" fill="none" :stroke="stat.depth" stroke-width="2" stroke-linecap="round"/>
              <circle v-for="(p, j) in trendPoints(stat.trendData)" :key="j" :cx="p.x" :cy="p.y" r="2.5" :fill="stat.depth"/>
            </svg>
            <div class="trend-labels">
              <span v-for="(d, j) in stat.trendLabels" :key="j">{{ d }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Main Grid -->
      <div class="main-grid">
        <section class="card recent-section">
          <div class="card-head">
            <h2 class="card-title">最近面试</h2>
            <router-link to="/history" class="link-more">查看全部</router-link>
          </div>
          <div class="interview-list">
            <div v-if="recentInterviews.length === 0" class="empty-state">
              <svg class="empty-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10 9 9 9 8 9"/>
              </svg>
              <p class="empty-text">暂时还没有面试记录，尝试一次面试吧</p>
            </div>
            <template v-else>
            <div v-for="(item, i) in recentInterviews" :key="i" class="interview-row reveal-item" :style="{ '--delay': i * 60 + 'ms' }">
              <div class="interview-avatar" :style="{ background: item.avatarBg }">{{ item.position.charAt(0) }}</div>
              <div class="interview-info">
                <span class="interview-pos">{{ item.position }}</span>
                <span class="interview-meta">{{ item.company }} - {{ item.date }}</span>
              </div>
              <div class="interview-right">
                <span v-if="item.status === 'completed'" class="score-badge" :class="scoreLevel(item.score)">{{ item.score }}</span>
                <span v-else-if="item.status === 'in_progress'" class="status-tag progress">进行中</span>
                <span v-else class="status-tag interrupted">已中断</span>
                <router-link :to="item.status === 'completed' ? `/history/${item.id}` : '/interview'" class="interview-action" :class="{ 'action-primary': item.status !== 'completed' }">
                  {{ item.status === 'completed' ? '查看' : '继续' }}
                </router-link>
              </div>
            </div>
            </template>
          </div>
        </section>

        <div class="side-stack">
          <section class="card actions-section">
            <h2 class="card-title">快捷入口</h2>
            <div class="action-list">
              <router-link v-for="(a, i) in quickActions" :key="i" :to="a.to" class="action-link reveal-item" :style="{ '--delay': i * 60 + 'ms' }">
                <div class="action-icon" :style="{ color: a.color, background: a.bg }"><span v-html="a.icon"></span></div>
                <div class="action-text"><span class="action-name">{{ a.title }}</span><span class="action-desc">{{ a.desc }}</span></div>
                <svg class="action-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
              </router-link>
            </div>
          </section>

          <section class="card radar-section">
            <h2 class="card-title" style="margin-bottom: var(--space-5);">能力雷达</h2>
            <div class="radar-full-wrap">
              <!-- Empty state -->
              <div v-if="radarProfile.empty" class="radar-empty">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--neutral-300)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <p>完成首次面试后解锁能力雷达</p>
              </div>
              <!-- Radar chart -->
              <RadarChart
                v-else
                :labels="radarLabels"
                :values="radarValues"
                :size="260"
              />
              <div v-if="!radarProfile.empty" class="radar-legend">
                <span v-for="(lbl, i) in radarLabels" :key="i" class="radar-legend-item">{{ lbl }}</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import { useUserStore } from '../store/user'
import { getMe, getMyStats, getInterviewRecords, getDashboardProfile } from '../api'
import RadarChart from '../components/ui/RadarChart.vue'

const userStore = useUserStore()
const loading = ref(true)

const displayName = computed(() => userStore.nickname || userStore.username || '同学')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// AI Quotes
const quotes = [
  '上次的技术深度回答不错，下次试试用 STAR 法则展开项目经验',
  '你的逻辑表达在进步，注意控制每段回答在 2 分钟内',
  '系统设计题是你的强项，行为面试可以多准备具体案例',
  '建议今天练一道算法 + 一道行为面试，保持手感',
  '连续练习让你的表达分数提升了 12%，继续保持',
]
const quoteIdx = ref(0)
const currentQuote = computed(() => quotes[quoteIdx.value])
function cycleQuote() { quoteIdx.value = (quoteIdx.value + 1) % quotes.length }

// Radar data from API
const radarProfile = ref({ labels: [], values: [], empty: true })
const radarLabels = computed(() => radarProfile.value.labels || [])
const radarValues = computed(() => radarProfile.value.values || [])

// Todos
const todos = reactive([
  { text: '完成 1 次前端面试', done: false },
  { text: '复习 Vue 3 组合式 API', done: true },
  { text: '更新简历项目经历', done: false },
])

// Stats
const stats = ref([
  { label: '面试次数', display: '--', value: 0, trend: '--', trendType: 'neutral', depth: '#059669', spark: [0], trendData: [0], trendLabels: ['--'] },
  { label: '平均得分', display: '--', value: 0, trend: '--', trendType: 'neutral', depth: '#10b981', spark: [0], trendData: [0], trendLabels: ['--'] },
  { label: '最高得分', display: '--', value: 0, trend: '--', trendType: 'neutral', depth: '#34d399', spark: [0], trendData: [0], trendLabels: ['--'] },
  { label: '连续练习', display: '--', value: 0, trend: '--', trendType: 'neutral', depth: '#6ee7b7', spark: [0], trendData: [0], trendLabels: ['--'] },
])

const expandedStat = ref(-1)
const hoveredStat = ref(-1)
function toggleStat(i) { expandedStat.value = expandedStat.value === i ? -1 : i }

// Sparkline SVG
function sparklinePoints(data) {
  if (!data || !data.length) return ''
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  return data.map((v, i) => `${(i / (data.length - 1)) * 100},${28 - ((v - min) / range) * 24}`).join(' ')
}

// Trend chart SVG
function trendPoints(data) {
  if (!data || !data.length) return []
  const max = Math.max(...data) * 1.1
  const min = Math.min(...data) * 0.9
  const range = max - min || 1
  return data.map((v, i) => ({ x: (i / (data.length - 1)) * 200, y: 55 - ((v - min) / range) * 45 }))
}

function trendLine(data) {
  return trendPoints(data).map(p => `${p.x},${p.y}`).join(' ')
}

function trendArea(data) {
  const pts = trendPoints(data)
  if (!pts.length) return ''
  const line = pts.map(p => `${p.x},${p.y}`).join(' L')
  return `M${line} L200,60 L0,60 Z`
}

// Recent Interviews
const recentInterviews = ref([])
const avatarColors = [
  'rgba(16,185,129,0.1)',
  'rgba(59,130,246,0.1)',
  'rgba(245,158,11,0.1)',
  'rgba(139,92,246,0.1)',
  'rgba(239,68,68,0.1)',
]

function formatInterviewDate(createTime) {
  if (!createTime) return ''
  const now = new Date()
  const then = new Date(createTime)
  const diffMs = now - then
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}小时前`
  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 30) return `${diffDay}天前`
  return then.toLocaleDateString('zh-CN')
}

const quickActions = [
  { to: '/job-select', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>', color: '#10b981', bg: 'rgba(16,185,129,0.08)', title: '面试准备', desc: '选岗位、上传简历' },
  { to: '/interview', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>', color: '#3b82f6', bg: 'rgba(59,130,246,0.08)', title: '快速面试', desc: '直接进入模拟' },
  { to: '/history', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', color: '#f59e0b', bg: 'rgba(245,158,11,0.08)', title: '历史记录', desc: '查看报告与详情' },
]

const trendData = [
  { label: '表达', value: 88, color: '#10b981' },
  { label: '逻辑', value: 72, color: '#3b82f6' },
  { label: '技术', value: 85, color: '#8b5cf6' },
  { label: '匹配', value: 78, color: '#f59e0b' },
  { label: '抗压', value: 65, color: '#ef4444' },
]

function scoreLevel(s) { if (s >= 85) return 'high'; if (s >= 70) return 'mid'; return 'low' }

// Number counter animation
function animateNumbers() {
  const currentStats = stats.value
  document.querySelectorAll('.stat-num').forEach((el, i) => {
    const stat = currentStats[i]
    if (!stat) return
    const target = stat.value
    const duration = 1200
    const start = performance.now()
    const suffix = stat.display.replace(/[\d.]/g, '')
    const isDecimal = String(target).includes('.')
    function tick(now) {
      const p = Math.min((now - start) / duration, 1)
      const eased = 1 - Math.pow(1 - p, 3)
      const v = target * eased
      el.textContent = (isDecimal ? v.toFixed(1) : Math.floor(v)) + suffix
      if (p < 1) requestAnimationFrame(tick)
      else el.textContent = stat.display
    }
    requestAnimationFrame(tick)
  })
}

let observer = null

async function fetchDashboardData() {
  loading.value = true
  try {
    const [meRes, statsRes, recordsRes, radarRes] = await Promise.allSettled([
      getMe(),
      getMyStats(),
      getInterviewRecords(),
      getDashboardProfile(),
    ])

    // Update user info
    if (meRes.status === 'fulfilled' && meRes.value) {
      const me = meRes.value
      if (me.nickname) userStore.nickname = me.nickname
      if (me.username) userStore.username = me.username
    }

    // Update stats
    if (statsRes.status === 'fulfilled' && statsRes.value) {
      const s = statsRes.value
      const totalInterviews = s.finishedInterviews ?? 0
      const averageScore = s.averageScore ?? 0
      const highestScore = s.highestScore ?? 0
      const streakDays = s.streakDays ?? 0

      stats.value = [
        {
          label: '面试次数',
          display: String(totalInterviews),
          value: totalInterviews,
          trend: totalInterviews > 0 ? `共 ${totalInterviews} 次` : '--',
          trendType: totalInterviews > 0 ? 'up' : 'neutral',
          depth: '#059669',
          spark: [totalInterviews],
          trendData: [totalInterviews],
          trendLabels: ['总计'],
        },
        {
          label: '平均得分',
          display: averageScore > 0 ? averageScore.toFixed(1) : '--',
          value: averageScore,
          trend: averageScore >= 80 ? '优秀' : averageScore >= 60 ? '良好' : '--',
          trendType: averageScore >= 80 ? 'up' : 'neutral',
          depth: '#10b981',
          spark: [averageScore],
          trendData: [averageScore],
          trendLabels: ['平均'],
        },
        {
          label: '最高得分',
          display: highestScore > 0 ? String(highestScore) : '--',
          value: highestScore,
          trend: highestScore > 0 ? '最佳' : '--',
          trendType: 'neutral',
          depth: '#34d399',
          spark: [highestScore],
          trendData: [highestScore],
          trendLabels: ['最高'],
        },
        {
          label: '连续练习',
          display: `${streakDays}天`,
          value: streakDays,
          trend: streakDays > 0 ? `已连续 ${streakDays} 天` : '--',
          trendType: streakDays > 0 ? 'up' : 'neutral',
          depth: '#6ee7b7',
          spark: [streakDays],
          trendData: [streakDays],
          trendLabels: ['天数'],
        },
      ]
    }

    // Update recent interviews (top 3)
    if (recordsRes.status === 'fulfilled' && Array.isArray(recordsRes.value)) {
      const records = recordsRes.value.slice(0, 3)
      recentInterviews.value = records.map((r, i) => ({
        id: r.sessionId,
        position: r.jobName || '未知岗位',
        company: '',
        score: r.totalScore ?? 0,
        date: formatInterviewDate(r.createTime),
        status: r.status === 'FINISHED' ? 'completed' : r.status === 'IN_PROGRESS' ? 'in_progress' : 'interrupted',
        avatarBg: avatarColors[i % avatarColors.length],
      }))
    }

    // Update radar profile
    if (radarRes.status === 'fulfilled' && radarRes.value) {
      radarProfile.value = radarRes.value
    }
  } catch (err) {
    console.warn('Dashboard: failed to fetch data', err)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchDashboardData()

  // Wait for DOM to update with new data before observing
  await nextTick()

  observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible')
        observer.unobserve(entry.target)
      }
    })
  }, { threshold: 0.1 })
  document.querySelectorAll('.reveal-item').forEach(el => observer.observe(el))

  // Animate numbers when stats visible
  const statsEl = document.querySelector('.stats-row')
  if (statsEl) {
    const numObs = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) { animateNumbers(); numObs.unobserve(statsEl) }
    }, { threshold: 0.3 })
    numObs.observe(statsEl)
  }
})

onUnmounted(() => { observer?.disconnect() })
</script>

<style scoped>
.dashboard {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-6) var(--space-6) var(--space-16);
}

.reveal-item { opacity: 0; transform: translateY(14px); transition: opacity var(--duration-slow) var(--ease-out-expo), transform var(--duration-slow) var(--ease-out-expo); transition-delay: var(--delay, 0ms); }
.reveal-item.visible { opacity: 1; transform: translateY(0); }

/* ==================== HERO STRIP ==================== */
.hero-strip {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-6);
  padding: var(--space-6);
  background: var(--surface-elevated);
  border: 1.5px solid rgba(16, 185, 129, 0.18);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-8);
  animation: fade-in-up var(--duration-slow) var(--ease-out-expo);
}


.strip-left {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: var(--space-4);
}

.strip-right {
  display: flex;
  gap: var(--space-4);
}

/* AI Bubble */
.bubble-wrap { cursor: pointer; }

.ai-bubble {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
  max-width: 420px;
  transition: transform var(--duration-normal) var(--ease-spring);
}

.ai-bubble:hover { transform: translateY(-2px); }

.bubble-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--accent-50);
  color: var(--accent-600);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
}

.bubble-body {
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  border-top-left-radius: var(--radius-xs);
  padding: var(--space-3) var(--space-4);
  position: relative;
}

.bubble-text {
  font-size: 13px;
  color: var(--neutral-600);
  line-height: 1.6;
}

.bubble-tag {
  font-size: 10px;
  color: var(--neutral-400);
  margin-top: 4px;
  display: block;
}

.hero-info {}

.hero-time {
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--neutral-400);
  margin-bottom: var(--space-1);
}

.hero-name {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 3.5vw, 2.5rem);
  font-weight: 700;
  color: var(--neutral-900);
  letter-spacing: -0.03em;
  line-height: 1.1;
}

/* CTA Button */
.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: var(--space-3);
  text-decoration: none;
  width: fit-content;
  transition: transform var(--duration-normal) var(--ease-spring);
}

.hero-cta:hover { transform: translateX(4px); }

.cta-ring {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--accent-500);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all var(--duration-normal) var(--ease-spring);
  box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.3);
}

.hero-cta:hover .cta-ring {
  background: var(--accent-400);
  box-shadow: 0 0 0 8px rgba(16, 185, 129, 0.08);
  transform: scale(1.05);
}

.cta-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-700);
}

/* Mini Radar */
.mini-radar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  width: 140px;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out-expo);
}

.mini-radar-card:hover { border-color: var(--accent-300); }

.mini-label { font-size: 11px; color: var(--neutral-400); font-weight: 500; }

.mini-radar-svg { width: 90px; height: 90px; }

.radar-poly { transition: fill var(--duration-normal); }
.radar-dot { transition: fill var(--duration-fast); }

/* Full Radar */
.radar-full-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-4);
}

.radar-full-svg {
  width: 100%;
  max-width: 260px;
  height: auto;
}

.radar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) var(--space-4);
  color: var(--neutral-400);
  font-size: var(--text-sm);
  text-align: center;
}

.radar-legend {
  display: flex;
  justify-content: center;
  gap: var(--space-5);
  flex-wrap: wrap;
}

.radar-legend-item {
  font-size: var(--text-xs);
  color: var(--neutral-500);
  font-weight: 500;
}

/* Todo Card */
.todo-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  width: 180px;
}

.todo-list { display: flex; flex-direction: column; gap: var(--space-1); }

.todo-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--neutral-600);
  cursor: pointer;
  padding: 3px 0;
  transition: color var(--duration-fast);
}

.todo-item.done .todo-text { text-decoration: line-through; color: var(--neutral-400); }

.todo-item input { display: none; }

.todo-check {
  width: 14px;
  height: 14px;
  border: 1.5px solid var(--neutral-300);
  border-radius: 3px;
  transition: all var(--duration-fast);
  flex-shrink: 0;
  position: relative;
}

.todo-item.done .todo-check {
  background: var(--accent-500);
  border-color: var(--accent-500);
}

.todo-item.done .todo-check::after {
  content: '';
  position: absolute;
  left: 3px; top: 1px;
  width: 4px; height: 7px;
  border: solid white;
  border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg);
}

.todo-text { line-height: 1.3; }

/* ==================== STATS ROW ==================== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.stat-card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  position: relative;
  overflow: hidden;
  transition: all var(--duration-normal) var(--ease-out-expo);
  min-height: 90px;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--depth, var(--accent-500));
  opacity: 0;
  transition: opacity var(--duration-fast);
}

.stat-card:hover::before,
.stat-card.expanded::before { opacity: 1; }

.stat-card:hover {
  border-color: var(--neutral-300);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  transform: translateY(-2px);
}

.stat-card.expanded {
  border-color: var(--accent-300);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label { font-size: 13px; color: var(--neutral-500); font-weight: 500; }

.stat-num {
  font-family: var(--font-mono);
  font-size: var(--text-3xl);
  font-weight: 700;
  color: var(--neutral-900);
  line-height: 1;
  letter-spacing: -0.03em;
}

.stat-trend {
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-mono);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.stat-trend.up { color: var(--accent-700); background: var(--accent-50); }
.stat-trend.neutral { color: var(--neutral-500); background: var(--neutral-100); }

.stat-bar {
  height: 3px;
  background: var(--neutral-100);
  border-radius: 2px;
  overflow: hidden;
  margin-top: auto;
}

.stat-bar-fill {
  height: 100%;
  background: var(--depth, var(--accent-500));
  border-radius: 2px;
  transition: width 1s var(--ease-out-expo);
}

/* Sparkline */
.sparkline {
  width: 100%;
  height: 24px;
  margin-top: var(--space-1);
  animation: fade-in 0.3s var(--ease-out-expo);
}

/* Expanded Trend Chart */
.trend-chart {
  margin-top: var(--space-3);
  animation: fade-in-up 0.3s var(--ease-out-expo);
}

.trend-svg { width: 100%; height: 60px; }

.trend-labels {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: var(--neutral-400);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.expand-enter-active { transition: all 0.3s var(--ease-out-expo); }
.expand-leave-active { transition: all 0.2s ease-in; }
.expand-enter-from, .expand-leave-to { opacity: 0; max-height: 0; margin-top: 0; }
.expand-enter-to { max-height: 120px; }

/* ==================== MAIN GRID ==================== */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-6);
  background: var(--accent-600);
  color: white;
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  border-radius: var(--radius-full);
  text-decoration: none;
  border: none;
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.btn-primary:hover { background: var(--accent-500); box-shadow: var(--shadow-accent); transform: translateY(-2px); color: white; }
.btn-primary:active { transform: translateY(0) scale(0.98); }

.main-grid { display: grid; grid-template-columns: 1fr 380px; gap: var(--space-6); }
.side-stack { display: flex; flex-direction: column; gap: var(--space-6); }

.card { background: var(--surface-elevated); border: 1px solid var(--neutral-200); border-radius: var(--radius-lg); padding: var(--space-6); }
.card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-5); }
.card-title { font-family: var(--font-display); font-size: var(--text-base); font-weight: 600; color: var(--neutral-800); letter-spacing: -0.01em; }
.card-hint { font-size: var(--text-xs); color: var(--neutral-400); font-family: var(--font-mono); }
.link-more { font-size: var(--text-sm); color: var(--accent-600); font-weight: 500; transition: color var(--duration-fast); }
.link-more:hover { color: var(--accent-500); }

.interview-list { display: flex; flex-direction: column; }
.interview-row { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-4) var(--space-3); border-radius: var(--radius-md); transition: background var(--duration-fast); }
.interview-row:hover { background: var(--neutral-50); }
.interview-avatar { width: 40px; height: 40px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; color: var(--accent-700); flex-shrink: 0; }
.interview-info { flex: 1; min-width: 0; }
.interview-pos { display: block; font-size: var(--text-sm); font-weight: 500; color: var(--neutral-800); }
.interview-meta { font-size: var(--text-xs); color: var(--neutral-400); font-family: var(--font-mono); }
.interview-right { display: flex; align-items: center; gap: var(--space-3); }
.score-badge { font-family: var(--font-mono); font-size: var(--text-base); font-weight: 700; }
.score-badge.high { color: var(--accent-600); }
.score-badge.mid { color: var(--color-warning); }
.score-badge.low { color: var(--color-error); }
.status-tag { font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: var(--radius-full); }
.status-tag.progress { color: var(--accent-700); background: var(--accent-50); }
.status-tag.interrupted { color: var(--neutral-500); background: var(--neutral-100); }
.interview-action { font-size: 12px; font-weight: 500; color: var(--accent-600); padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); text-decoration: none; transition: all var(--duration-fast); }
.interview-action:hover { background: var(--accent-50); }
.interview-action.action-primary { background: var(--accent-600); color: white; }
.interview-action.action-primary:hover { background: var(--accent-500); }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
  text-align: center;
}

.empty-icon {
  color: var(--neutral-300);
  margin-bottom: var(--space-4);
}

.empty-text {
  font-size: var(--text-sm);
  color: var(--neutral-400);
  margin: 0;
}

.action-list { display: flex; flex-direction: column; gap: var(--space-1); margin-top: var(--space-4); }
.action-link { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3); border-radius: var(--radius-md); text-decoration: none; transition: all var(--duration-normal) var(--ease-out-expo); }
.action-link:hover { background: var(--neutral-50); transform: translateX(2px); }
.action-icon { width: 36px; height: 36px; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.action-text { flex: 1; }
.action-name { display: block; font-size: var(--text-sm); font-weight: 500; color: var(--neutral-800); }
.action-desc { font-size: var(--text-xs); color: var(--neutral-400); }
.action-arrow { color: var(--neutral-300); transition: all var(--duration-fast); flex-shrink: 0; }
.action-link:hover .action-arrow { color: var(--accent-500); transform: translateX(3px); }

.trend-bars { display: flex; justify-content: space-between; align-items: flex-end; height: 180px; gap: var(--space-4); padding-top: var(--space-4); margin-top: var(--space-4); }
.trend-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: var(--space-2); height: 100%; }
.trend-track { flex: 1; width: 100%; background: var(--neutral-100); border-radius: var(--radius-sm); display: flex; align-items: flex-end; overflow: hidden; }
.trend-fill { width: 100%; background: var(--color, var(--accent-500)); border-radius: var(--radius-sm); transition: height 1s var(--ease-out-expo); display: flex; align-items: flex-start; justify-content: center; padding-top: var(--space-2); min-height: 8px; opacity: 0.85; }
.trend-num { font-family: var(--font-mono); font-size: 10px; font-weight: 700; color: white; }
.trend-name { font-size: var(--text-xs); color: var(--neutral-500); font-weight: 500; }

/* Responsive */
@media (max-width: 1200px) {
  .main-grid { grid-template-columns: 1fr; }
}

@media (max-width: 900px) {
  .hero-strip { grid-template-columns: 1fr; }
  .strip-right { flex-direction: row; justify-content: flex-start; }
  .mini-radar-card, .todo-card { width: auto; flex: 1; }
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .strip-right { flex-direction: column; }
  .hero-name { font-size: 1.5rem; }
}

@media (prefers-reduced-motion: reduce) {
  .reveal-item { opacity: 1; transform: none; transition: none; }
  .stat-card:hover { transform: none; }
  .ai-bubble:hover { transform: none; }
  .hero-cta:hover { transform: none; }
}
</style>
