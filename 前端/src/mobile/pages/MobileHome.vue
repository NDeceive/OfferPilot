<!--
  手机首页：练习是唯一主叙事，经验是第二叙事。
  一级：继续/开始练习；二级：四个练习入口与练习概览；三级：错题复盘、最近练习、经验精选。
  主操作只有一个：进入练习。经验入口用卡片承载，不和主按钮抢视觉重量。
-->
<template>
  <MobileShell brand :nav-links="[]">
    <section class="mhome-head">
      <p>{{ greeting }}，{{ displayName }}</p>
      <h1>今天补一个薄弱点</h1>
    </section>

    <MobileSkeleton v-if="loading" variant="card" :rows="2" label="正在整理练习记录" />
    <MobileState v-else-if="loadError" kind="error" title="练习概览暂时没加载出来" action="重新加载" @action="load" />
    <template v-else>
      <section class="mhome-hero" :class="{ 'has-session': ongoing }">
        <div class="mhome-hero-top">
          <span>{{ ongoing ? '继续上次' : '5 分钟查缺补漏' }}</span>
          <b>{{ ongoing ? '进行中' : '轻量练习' }}</b>
        </div>
        <h2>{{ ongoing ? (ongoing.topic || '上次练习') : '从一道错题开始' }}</h2>
        <p>{{ ongoing ? `${ongoing.role?.name || '当前岗位'} · 已提交 ${ongoing.completed || 0} / ${ongoing.questionCount || 0} 题` : '按岗位和知识点组一组小题，做完立刻看解析。' }}</p>
        <div v-if="ongoing" class="mhome-progress" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100" aria-label="上次练习进度"><i :style="{ width: `${progress}%` }"></i></div>
        <router-link v-if="ongoing" :to="`/learning/session/${ongoing.id}`" class="mobile-primary-button">
          继续练习 <span>→</span>
        </router-link>
        <button v-else type="button" class="mobile-primary-button" :disabled="starting" @click="startQuickPractice">
          {{ starting ? '正在组题…' : '开始快速练习' }} <span>→</span>
        </button>
        <p v-if="startError" class="mhome-start-error" role="alert">
          {{ startError }} <router-link to="/learning">去专项刷题</router-link>
        </p>
      </section>

      <section class="mhome-stats" aria-label="练习概览">
        <router-link to="/learning/library/recent"><strong>{{ counts.recent }}</strong><span>练习</span></router-link>
        <router-link to="/learning/library/mistakes"><strong>{{ counts.mistakes }}</strong><span>待复习</span></router-link>
        <router-link to="/learning/library/favorites"><strong>{{ counts.favorites }}</strong><span>收藏</span></router-link>
      </section>

      <section v-if="recentSessions.length" class="mobile-section">
        <header class="mobile-section-heading"><h2>最近练习</h2><router-link to="/learning/library/recent">查看全部 →</router-link></header>
        <div class="mhome-session-list">
          <router-link v-for="session in recentSessions" :key="session.id" :to="sessionLink(session)" class="mhome-session">
            <span class="mhome-session-mark">{{ session.status === 'FINISHED' ? '报' : '练' }}</span>
            <div class="mobile-grow">
              <strong>{{ session.topic || session.role?.name || '专项练习' }}</strong>
              <small>{{ session.role?.name || '历史岗位' }} · {{ session.completed || 0 }} / {{ session.questionCount || 0 }} 题</small>
            </div>
            <span class="mhome-session-state">{{ session.status === 'FINISHED' ? '看报告' : '继续' }}</span>
          </router-link>
        </div>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>经验精选</h2><router-link to="/experience">全部经验 →</router-link></header>
        <div class="mhome-exp-list">
          <router-link v-for="post in featuredPosts" :key="post.id" :to="`/experience/${post.id}`" class="mhome-exp">
            <div class="mhome-exp-meta"><span>{{ post.jobName }}</span><time>{{ post.readMinutes }} 分钟</time></div>
            <strong>{{ post.title }}</strong>
            <small>{{ post.author }} · ♥ {{ post.likeCount }}</small>
          </router-link>
        </div>
        <router-link to="/experience/new" class="mhome-exp-write">写下你的面试经验 <span>＋</span></router-link>
      </section>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTrainingSession, getPracticeOverview, loadLearningResources } from '../../services/learningResources'
import { listExperience } from '../../services/experienceDemo'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'

const router = useRouter()
const store = useUserStore()
const loading = ref(true)
const loadError = ref(false)
const starting = ref(false)
const startError = ref('')
const practice = ref({ libraryCounts: {}, recentSession: null, recentSessions: [] })

const displayName = computed(() => store.nickname || store.username || '同学')
const greeting = computed(() => new Date().getHours() < 12 ? '早上好' : new Date().getHours() < 18 ? '下午好' : '晚上好')
const counts = computed(() => ({
  recent: Number(practice.value.libraryCounts?.recent) || 0,
  mistakes: Number(practice.value.libraryCounts?.mistakes) || 0,
  favorites: Number(practice.value.libraryCounts?.favorites) || 0,
}))
const ongoing = computed(() => practice.value.recentSession || null)
const progress = computed(() => {
  const total = Number(ongoing.value?.questionCount) || 0
  return total ? Math.min(100, Math.round((Number(ongoing.value?.completed) || 0) / total * 100)) : 0
})
// 主卡已经展示的那条进行中题单，不再在「最近练习」里重复出现。
const recentSessions = computed(() => {
  const ongoingId = ongoing.value?.id
  return (practice.value.recentSessions || [])
    .filter(session => String(session.id) !== String(ongoingId))
    .slice(0, 3)
})
const featuredPosts = computed(() => listExperience({ sort: 'latest' }).slice(0, 2))

function sessionLink(session) {
  return session.status === 'FINISHED' ? `/learning/session/${session.id}/report` : `/learning/session/${session.id}`
}

/** 首页一键开练：直接组 5 道基础单选，不再让用户先过刷题配置页。 */
async function startQuickPractice() {
  if (starting.value) return
  starting.value = true
  startError.value = ''
  try {
    const data = await loadLearningResources()
    const role = data.preferredRole || data.currentRole || data.roles?.find(item => item.code === 'BE-JAVA') || data.roles?.[0]
    if (!role) {
      startError.value = '还没有可用岗位，请先选择目标岗位。'
      return
    }
    if (data.catalog && data.catalog.ready === false) {
      startError.value = '练习配置暂时不可用，可以先去专项刷题看看。'
      return
    }
    const session = await createTrainingSession({
      role,
      company: data.preferredCompany || '不限公司',
      topic: '综合高频',
      questionType: 'choice',
      questionCount: 5,
      difficulty: 'basic',
    })
    router.push(`/learning/session/${session.id}`)
  } catch (error) {
    startError.value = error?.message || '快速练习创建失败，请稍后重试。'
  } finally {
    starting.value = false
  }
}

async function load() {
  loading.value = true
  loadError.value = false
  try {
    const result = await getPracticeOverview()
    practice.value = {
      libraryCounts: result?.libraryCounts || {},
      recentSession: result?.recentSession || null,
      recentSessions: Array.isArray(result?.recentSessions) ? result.recentSessions : [],
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mhome-head { padding: 8px 0 0; }
.mhome-head p { color: var(--m-primary); font-size: 13px; font-weight: 700; }
.mhome-head h1 { margin-top: 8px; font-size: 28px; line-height: 1.25; font-weight: 800; letter-spacing: -.045em; }

.mhome-hero {
  position: relative;
  overflow: hidden;
  margin-top: 20px;
  padding: 20px;
  background: linear-gradient(150deg, #eef7f0 0%, #ffffff 68%);
  border: 1px solid #dcebe1;
  border-radius: var(--m-radius-hero);
  box-shadow: 0 10px 24px rgba(32, 61, 48, .05);
  animation: mhome-rise var(--m-motion-slow) 40ms backwards;
}
.mhome-hero::after {
  position: absolute;
  top: -70px;
  right: -56px;
  width: 190px;
  height: 190px;
  background: radial-gradient(circle at 32% 32%, rgba(238, 155, 69, .18), transparent 68%);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}
.mhome-hero > * { position: relative; z-index: 1; }
.mhome-hero.has-session { background: linear-gradient(150deg, #e4f2e8 0%, #ffffff 72%); }
.mhome-hero-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--m-primary); font-size: 11.5px; font-weight: 800; letter-spacing: .06em; }
.mhome-hero-top b { padding: 3px 8px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 999px; font-size: 10.5px; letter-spacing: 0; }
.mhome-hero h2 { margin-top: 13px; font-size: 23px; line-height: 1.3; font-weight: 800; letter-spacing: -.03em; }
.mhome-hero p { margin-top: 7px; color: #42574d; font-size: 13px; line-height: 1.65; }
.mhome-progress { height: 7px; margin-top: 15px; overflow: hidden; background: #d9e9de; border-radius: 999px; }
.mhome-progress i { display: block; height: 100%; background: var(--m-primary); border-radius: inherit; transition: width 700ms cubic-bezier(.16, 1, .3, 1); }
.mhome-hero .mobile-primary-button { margin-top: 18px; transition: transform var(--m-motion) ease, filter var(--m-motion) ease; }
.mhome-hero .mobile-primary-button:active { transform: scale(.98); filter: brightness(.96); }
.mhome-hero .mobile-primary-button:disabled { cursor: wait; opacity: .72; }
.mhome-hero .mhome-start-error { margin-top: 10px; color: #a33b2b; font-size: 12.5px; line-height: 1.6; }
.mhome-hero .mhome-start-error a { color: var(--m-primary); font-weight: 700; text-decoration: underline; }

/* 深色主题：首页主卡不能沿用浅色渐变，否则 h2 跟随 --m-text 变亮后落在白底上看不见。 */
:root[data-theme='dark'] .mhome-hero {
  background: linear-gradient(150deg, #16281f 0%, #171b19 68%);
  border-color: var(--m-border);
  box-shadow: var(--m-shadow);
}
:root[data-theme='dark'] .mhome-hero.has-session { background: linear-gradient(150deg, #1b3a2b 0%, #171b19 72%); }
:root[data-theme='dark'] .mhome-hero h2 { color: var(--m-text); }
:root[data-theme='dark'] .mhome-hero p { color: var(--m-text-secondary); }
:root[data-theme='dark'] .mhome-progress { background: #26352e; }
:root[data-theme='dark'] .mhome-hero .mhome-start-error { color: #f0a09a; }

.mhome-stats { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 14px; padding: 15px 0; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); animation: mhome-rise 380ms cubic-bezier(.16, 1, .3, 1) 230ms backwards; }
.mhome-stats a { display: flex; align-items: center; flex-direction: column; gap: 3px; color: var(--m-text); border-right: 1px solid var(--m-border); transition: transform var(--m-motion) ease; }
.mhome-stats a:active { transform: scale(.94); }
.mhome-stats a:last-child { border-right: 0; }
.mhome-stats strong { font-size: 22px; line-height: 1.15; font-weight: 800; font-variant-numeric: tabular-nums; }
.mhome-stats span { color: var(--m-text-tertiary); font-size: 11.5px; }

.mhome-session-list { display: flex; flex-direction: column; gap: 8px; }
.mhome-session { display: flex; min-height: 64px; padding: 11px 13px; align-items: center; gap: 10px; color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); transition: transform var(--m-motion) ease, box-shadow var(--m-motion) ease; }
.mhome-session:active { transform: scale(.985); box-shadow: 0 2px 8px rgba(32, 61, 48, .06); }
.mhome-session-mark { display: grid; flex: 0 0 auto; width: 34px; height: 34px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 9px; font-size: 12px; font-weight: 800; place-items: center; }
.mhome-session strong { display: block; font-size: 13.5px; line-height: 1.4; }
.mhome-session small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 11px; }
.mhome-session-state { flex: 0 0 auto; color: var(--m-primary); font-size: 11.5px; font-weight: 700; }

.mhome-exp-list { display: flex; flex-direction: column; gap: 8px; }
.mhome-exp { display: block; padding: 13px; color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); transition: transform var(--m-motion) ease, box-shadow var(--m-motion) ease; }
.mhome-exp:active { transform: scale(.985); box-shadow: 0 2px 8px rgba(32, 61, 48, .06); }
.mhome-exp-meta { display: flex; align-items: center; justify-content: space-between; color: var(--m-text-tertiary); font-size: 11px; }
.mhome-exp-meta span { color: var(--m-primary-dark); font-weight: 700; }
.mhome-exp strong { display: block; margin-top: 6px; font-size: 14px; line-height: 1.5; font-weight: 700; }
.mhome-exp small { display: block; margin-top: 6px; color: var(--m-text-tertiary); font-size: 11px; }
.mhome-exp-write { display: flex; min-height: 44px; margin-top: 9px; align-items: center; justify-content: center; gap: 5px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: var(--m-radius-button); font-size: 12.5px; font-weight: 700; transition: transform var(--m-motion) ease; }
.mhome-exp-write:active { transform: scale(.98); }

@keyframes mhome-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (hover: hover) {
  .mhome-session:hover,
  .mhome-exp:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(32, 61, 48, .07); }
}
.mhome-exp-write span { font-size: 16px; }

@media (max-width: 359px) {
  .mhome-head h1 { font-size: 25px; }
  .mhome-hero h2 { font-size: 21px; }
}

@media (prefers-reduced-motion: reduce) {
  .mhome-hero,
  .mhome-stats { animation: none; }
  .mhome-progress i { transition: none; }
}
</style>
