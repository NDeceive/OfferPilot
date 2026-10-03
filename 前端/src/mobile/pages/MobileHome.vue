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
        <router-link to="/jobs" class="mobile-primary-button">开始准备 <span>→</span></router-link>
      </div>
      <div class="mobile-document-art" aria-hidden="true"><i></i><i></i><i></i></div>
    </section>

    <section class="mobile-quick-grid" aria-label="快捷入口">
      <router-link to="/jobs" class="mobile-action-card is-warm">
        <span class="mobile-card-icon">问</span><strong>面试押题</strong><small>提前准备高频问题</small><b>→</b>
      </router-link>
      <router-link to="/learning" class="mobile-action-card">
        <span class="mobile-card-icon">练</span><strong>专项刷题</strong><small>针对薄弱点提升</small><b>→</b>
      </router-link>
    </section>

    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>最近一次面试</h2><router-link to="/history">查看全部 →</router-link></header>
      <MobileState v-if="loading" kind="loading" title="正在加载训练记录" />
      <MobileState v-else-if="error" kind="error" title="训练记录暂时无法加载" action="重新加载" @action="load" />
      <article v-else-if="recent" class="mobile-info-card mobile-recent-card">
        <div class="mobile-row">
          <span class="mobile-square-icon">职</span>
          <div class="mobile-grow"><h3>{{ recent.jobName || '面试训练' }}</h3><p>{{ formatDate(recent.startTime) }} · {{ formatDuration(recent.durationSeconds) }}</p></div>
          <div v-if="hasScore" class="mobile-score"><small>综合表现</small><strong>{{ formatScore(recent.score) }}</strong></div>
        </div>
        <p class="mobile-card-copy">{{ insightCopy }}</p>
        <router-link :to="reportRoute" class="mobile-soft-action">查看报告 <span>→</span></router-link>
      </article>
      <MobileState v-else title="还没有面试记录" description="完成第一场面试后，你的训练记录会出现在这里。" action="开始第一场面试" @action="$router.push('/jobs')" />
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
import { getDashboardOverview } from '../../api'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'

const store = useUserStore()
const loading = ref(true)
const error = ref(false)
const overview = ref({ recentInterviews: [], nextAction: {}, latestInsight: null })

const displayName = computed(() => store.nickname || store.username || '同学')
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const recent = computed(() => overview.value.recentInterviews?.[0] || null)
const hasScore = computed(() => recent.value?.score !== null && recent.value?.score !== undefined)
const reportRoute = computed(() => recent.value?.reportId ? `/history/${recent.value.reportId}` : '/history')
const insightCopy = computed(() => overview.value.latestInsight?.suggestion || '完成报告后，这里会给出针对本次表现的改进建议。')
const suggestionTitle = computed(() => overview.value.nextAction?.title || '开始一次岗位训练')
const suggestionDescription = computed(() => overview.value.nextAction?.description || '选择目标岗位，用一次练习建立你的训练档案。')
const suggestionRoute = computed(() => overview.value.nextAction?.route || '/jobs')

async function load() {
  loading.value = true
  error.value = false
  try { overview.value = await getDashboardOverview() }
  catch { error.value = true }
  finally { loading.value = false }
}

function formatDate(value) {
  if (!value) return '日期待记录'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value)).replace('/', '月') + '日'
}
function formatDuration(seconds) { return seconds ? `${Math.max(1, Math.round(seconds / 60))} 分钟` : '时长待记录' }
function formatScore(score) { return Math.round(Number(score) || 0) }

onMounted(load)
</script>
