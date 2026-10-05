<template>
  <MobileShell brand>
    <section class="mobile-greeting">
      <p>{{ greeting }}，{{ displayName }}</p>
      <h1>今天准备哪场面试？</h1>
    </section>

    <section class="mobile-hero">
      <div>
        <h2>准备新岗位</h2>
        <p>从岗位与简历开始，<br />创建一场新的虚拟面试</p>
        <router-link to="/interview/ai" class="mobile-primary-button">开始准备 <span>→</span></router-link>
      </div>
      <div class="mobile-document-art" aria-hidden="true"><i></i><i></i><i></i></div>
    </section>

    <section class="mobile-quick-grid" aria-label="快捷入口">
      <router-link to="/interview/ai" class="mobile-action-card is-warm">
        <span class="mobile-card-icon">问</span><strong>面试押题</strong><small>提前准备高频问题</small><b>→</b>
      </router-link>
      <router-link to="/learning" class="mobile-action-card">
        <span class="mobile-card-icon">练</span><strong>专项刷题</strong><small>针对薄弱点提升</small><b>→</b>
      </router-link>
    </section>

    <!-- 训练概览：把散在 summary 里的四个数收成一块，顺带用最近几场画出走势 -->
    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>训练概览</h2><router-link to="/history">全部记录 →</router-link></header>
      <MobileSkeleton v-if="overviewLoading" variant="stats" />
      <article v-else-if="summary.completedCount" class="mhome__overview">
        <div class="mhome__overview-top">
          <MobileScoreRing :value="summary.averageScore" :size="76" :stroke="6" caption="平均分" />
          <div class="mhome__overview-text">
            <strong>{{ summary.completedCount }} 场已完成</strong>
            <p>最高 {{ round(summary.bestScore) }} 分<template v-if="summary.bestJobName"> · {{ summary.bestJobName }}</template></p>
            <p class="mhome__trend-hint">{{ trendHint }}</p>
          </div>
        </div>

        <div v-if="trendPoints.length >= 2" class="mhome__trend">
          <h3>最近 {{ trendPoints.length }} 场</h3>
          <MobileMiniChart :points="trendPoints" :height="82" />
        </div>
      </article>
      <MobileState
        v-else
        title="还没有完成过面试"
        description="完整走完一场面试后，分数与趋势会出现在这里。"
        action="开始第一场面试"
        @action="$router.push('/interview/ai')"
      />
    </section>

    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>最近一次面试</h2><router-link to="/history">查看全部 →</router-link></header>
      <MobileSkeleton v-if="loading" variant="card" :rows="1" label="正在加载训练记录" />
      <MobileState v-else-if="error" kind="error" title="训练记录暂时无法加载" action="重新加载" @action="load" />
      <article v-else-if="recent" class="mobile-info-card mobile-recent-card">
        <div class="mobile-row">
          <span class="mobile-square-icon">职</span>
          <div class="mobile-grow"><h3>{{ recent.jobName || '面试训练' }}</h3><p>{{ formatDate(recent.startTime) }} · {{ formatDuration(recent.durationSeconds) }}</p></div>
          <MobileScoreRing v-if="hasScore" :value="recent.score" :size="56" :stroke="5" />
        </div>
        <p class="mobile-card-copy">{{ insightCopy }}</p>
        <router-link :to="reportRoute" class="mobile-soft-action">查看报告 <span>→</span></router-link>
      </article>
      <MobileState v-else title="还没有面试记录" description="完成第一场面试后，你的训练记录会出现在这里。" action="开始第一场面试" @action="$router.push('/interview/ai')" />
    </section>

    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>下一步建议</h2></header>
      <article class="mobile-suggestion">
        <span class="mobile-bulb">✦</span>
        <div class="mobile-grow"><h3>{{ suggestionTitle }}</h3><p>{{ suggestionDescription }}</p></div>
        <router-link :to="suggestionRoute" aria-label="开始建议训练">→</router-link>
      </article>
    </section>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverview, getInterviewRecords } from '../../api'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import MobileScoreRing from '../components/MobileScoreRing.vue'
import MobileMiniChart from '../components/MobileMiniChart.vue'

const store = useUserStore()
const loading = ref(true)
const overviewLoading = ref(true)
const error = ref(false)
const overview = ref({ recentInterviews: [], nextAction: {}, latestInsight: null, summary: {} })
/** 近几场的分数。dashboard 的 trend 是按最近 30 天切片的，演示数据都在 7~8 月，
 *  拿到的是一条全 0 的直线，所以这里另取一次记录，按「最近 N 场」而不是「最近 N 天」。 */
const records = ref([])

const displayName = computed(() => store.nickname || store.username || '同学')
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const recent = computed(() => overview.value.recentInterviews?.[0] || null)
const hasScore = computed(() => recent.value?.score !== null && recent.value?.score !== undefined)
const reportRoute = computed(() => recent.value?.reportId ? `/history/${recent.value.reportId}` : '/history')
const insightCopy = computed(() => overview.value.latestInsight?.suggestion || '完成报告后，这里会给出针对本次表现的改进建议。')
const suggestionTitle = computed(() => overview.value.nextAction?.title || '开始一次岗位训练')
const suggestionDescription = computed(() => overview.value.nextAction?.description || '选择目标岗位，用一次练习建立你的训练档案。')
const suggestionRoute = computed(() => overview.value.nextAction?.route || '/interview/ai')

const summary = computed(() => overview.value.summary || {})

const scored = computed(() =>
  records.value
    .filter((r) => r.status === 'FINISHED' && r.totalScore !== null && r.totalScore !== undefined)
    .slice()
    .sort((a, b) => new Date(b.startTime || 0) - new Date(a.startTime || 0))
)

const recentFive = computed(() => scored.value.slice(0, 5).slice().reverse())

const trendPoints = computed(() =>
  recentFive.value.map((r) => ({
    value: Number(r.totalScore),
    label: new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(r.startTime)),
  }))
)

const trendHint = computed(() => {
  const list = recentFive.value
  if (list.length < 2) return '再完成几场就能看到趋势'
  const delta = Math.round(Number(list[list.length - 1].totalScore) - Number(list[list.length - 2].totalScore))
  if (delta > 0) return `比上一场高 ${delta} 分`
  if (delta < 0) return `比上一场低 ${Math.abs(delta)} 分`
  return '和上一场持平'
})

function round(v) {
  const n = Number(v)
  return Number.isFinite(n) ? Math.round(n) : 0
}

async function load() {
  loading.value = true
  overviewLoading.value = true
  error.value = false
  // 概览是主内容，记录只喂趋势图：分开处理，记录拉失败不该让整页进错误态
  const [ov, rec] = await Promise.allSettled([getDashboardOverview(), getInterviewRecords()])
  if (ov.status === 'fulfilled') overview.value = ov.value
  else error.value = true
  if (rec.status === 'fulfilled') records.value = rec.value || []
  loading.value = false
  overviewLoading.value = false
}

function formatDate(value) {
  if (!value) return '日期待记录'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value)).replace('/', '月') + '日'
}
function formatDuration(seconds) { return seconds ? `${Math.max(1, Math.round(seconds / 60))} 分钟` : '时长待记录' }

onMounted(load)
</script>

<style scoped>
.mhome__overview {
  padding: 18px;
  background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%);
  border: 1px solid #dcebe1;
  border-radius: var(--m-radius-hero);
  box-shadow: 0 8px 22px rgba(32, 61, 48, .05);
}

.mhome__overview-top { display: flex; align-items: center; gap: 16px; }
.mhome__overview-text { flex: 1; min-width: 0; }
.mhome__overview-text strong { display: block; font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.mhome__overview-text p { margin-top: 4px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.5; overflow-wrap: anywhere; }
.mhome__trend-hint { color: var(--m-primary) !important; font-weight: 700; }

.mhome__trend { margin-top: 18px; padding-top: 16px; border-top: 1px dashed #cfe0d6; }
.mhome__trend h3 { margin-bottom: 12px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 700; }
</style>
