<template>
  <MobileShell v-bind="shell">
    <section class="mobile-greeting">
      <p>{{ greeting }}，{{ displayName }}</p>
      <h1>今天要跟进哪个班？</h1>
    </section>

    <MobileSkeleton v-if="loading" variant="stats" label="正在加载教学数据" />
    <MobileState v-else-if="error" kind="error" title="教学数据暂时无法加载" description="请检查网络后重试，已登录状态不受影响。" action="重新加载" @action="load" />
    <template v-else>
      <section class="mth__stats" aria-label="教学概览">
        <router-link to="/teacher/classes"><strong>{{ counts.classes }}</strong><small>教学班</small></router-link>
        <div><strong>{{ counts.students }}</strong><small>在册学生</small></div>
        <router-link to="/teacher/tasks"><strong>{{ counts.active }}</strong><small>进行中任务</small></router-link>
        <router-link to="/teacher/classes" :class="{ 'is-alert': counts.pending > 0 }"><strong>{{ counts.pending }}</strong><small>待审核申请</small></router-link>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>教学班</h2><router-link to="/teacher/classes">全部 →</router-link></header>
        <div v-if="topClasses.length" class="mth__list">
          <router-link v-for="c in topClasses" :key="c.id" :to="`/teacher/classes/${c.id}`" class="mobile-info-card mth__class">
            <div class="mobile-row">
              <span class="mobile-square-icon">{{ c.name.slice(0, 1) }}</span>
              <div class="mobile-grow">
                <h3>{{ c.name }}</h3>
                <p>{{ c.semester || '未设置学期' }}</p>
              </div>
              <span v-if="c.pendingCount" class="mth__badge">待审 {{ c.pendingCount }}</span>
              <span v-else-if="c.archived" class="mth__tag">已归档</span>
            </div>
            <p class="mth__class-meta">已加入 {{ c.joinedCount || 0 }} 人<template v-if="!c.archived"> · 邀请码 {{ c.inviteCode || '—' }}</template></p>
          </router-link>
        </div>
        <MobileState
          v-else
          title="还没有教学班"
          description="创建班级、分享邀请码并审核学生申请，就能发布训练任务并跟进报告。"
          action="去创建班级"
          @action="$router.push('/teacher/classes')"
        />
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>进行中任务</h2><router-link to="/teacher/tasks">全部 →</router-link></header>
        <div v-if="activeTasks.length" class="mth__list">
          <router-link v-for="t in activeTasks" :key="t.id" :to="`/teacher/tasks/${t.id}`" class="mobile-info-card mth__task">
            <div class="mobile-row">
              <div class="mobile-grow">
                <h3>{{ t.title }}</h3>
                <p>{{ t.className }} · {{ t.jobName || '目标岗位待设置' }}</p>
              </div>
              <span class="mth__tag is-active">{{ lifecycleLabel(t.lifecycle) }}</span>
            </div>
            <p class="mth__deadline" :class="{ 'is-urgent': urgent(t.deadline) }">{{ deadlineHint(t.deadline) }}</p>
            <div v-if="taskProgress(t)" class="mth__progress">
              <i :style="{ width: progressPct(t) + '%' }"></i>
            </div>
            <p v-if="taskProgress(t)" class="mth__progress-text">已完成 {{ taskProgress(t).completed }} / {{ taskProgress(t).total }} 人</p>
          </router-link>
        </div>
        <MobileState
          v-else
          title="当前没有进行中的任务"
          description="发布训练任务后，学生的执行进度会汇总在这里。"
          action="查看全部任务"
          @action="$router.push('/teacher/tasks')"
        />
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>快捷入口</h2></header>
        <section class="mobile-quick-grid">
          <router-link to="/teacher/classes" class="mobile-action-card">
            <span class="mobile-card-icon">班</span><strong>班级管理</strong><small>审核申请 · 邀请码</small><b>→</b>
          </router-link>
          <router-link to="/teacher/tasks" class="mobile-action-card is-warm">
            <span class="mobile-card-icon">务</span><strong>训练任务</strong><small>发布与跟进</small><b>→</b>
          </router-link>
        </section>
      </section>

      <p v-if="pendingReviews > 0" class="mth__review-note" role="status">
        有 {{ pendingReviews }} 份学生报告等待点评，请在电脑端「数据分析 / 报告点评」中处理。
      </p>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { teaching } from '../../services/teachingApi'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { TEACHER_TABS } from '../nav'
import { deadlineHint, lifecycleLabel, taskProgress } from '../teacherFormat'

const store = useUserStore()

const shell = computed(() => ({
  brand: true,
  homePath: '/teacher/dashboard',
  messagePath: '/teacher/messages',
  profilePath: '/teacher/account',
  navLinks: [],
  tabItems: TEACHER_TABS,
}))

const loading = ref(true)
const error = ref(false)
const classes = ref([])
const tasks = ref([])
const summary = ref({})

const displayName = computed(() => store.nickname || store.username || '老师')
const greeting = computed(() => {
  const h = new Date().getHours()
  return h < 12 ? '早上好' : h < 18 ? '下午好' : '晚上好'
})

const counts = computed(() => ({
  classes: classes.value.filter((c) => !c.archived).length,
  students: summary.value.studentTotal ?? 0,
  active: tasks.value.filter((t) => t.lifecycle === 'ACTIVE' && !t.archived).length,
  pending: classes.value.reduce((n, c) => n + (c.pendingCount || 0), 0),
}))

const topClasses = computed(() =>
  classes.value
    .slice()
    .sort((a, b) => (b.pendingCount || 0) - (a.pendingCount || 0))
    .slice(0, 3)
)

const activeTasks = computed(() =>
  tasks.value
    .filter((t) => t.lifecycle === 'ACTIVE' && !t.archived)
    .sort((a, b) => String(a.deadline || '').localeCompare(String(b.deadline || '')))
    .slice(0, 4)
)

const pendingReviews = computed(() => summary.value.pendingReviewTotal || 0)

function progressPct(t) {
  const p = taskProgress(t)
  return p && p.total ? Math.round((p.completed / p.total) * 100) : 0
}

function urgent(deadline) {
  const hint = deadlineHint(deadline)
  return hint.startsWith('剩') || hint === '已截止'
}

async function load() {
  loading.value = true
  error.value = false
  // 班级是主内容；任务/汇总失败只降级局部（进度条与数字消失）不进整页错误态
  const [cls, tsk, sum] = await Promise.allSettled([
    teaching.classes(),
    teaching.tasks({ includeAssignments: true }),
    teaching.summary(),
  ])
  if (cls.status === 'fulfilled') classes.value = cls.value || []
  else error.value = true
  if (tsk.status === 'fulfilled') tasks.value = tsk.value || []
  if (sum.status === 'fulfilled') summary.value = sum.value || {}
  loading.value = false
}

onMounted(load)
</script>

<style scoped>
.mth__stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.mth__stats a,
.mth__stats div {
  display: flex;
  min-height: 82px;
  padding: 12px 8px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
}
.mth__stats strong { color: var(--m-primary-dark); font-size: 22px; line-height: 1.1; }
.mth__stats small { color: var(--m-text-tertiary); font-size: 11px; }
.mth__stats .is-alert { border-color: #e8c9a4; background: var(--m-accent-soft); }
.mth__stats .is-alert strong { color: #b66b20; }

.mth__list { display: flex; flex-direction: column; gap: 12px; }
.mth__class-meta { margin-top: 12px; padding-top: 10px; color: var(--m-text-tertiary); border-top: 1px dashed var(--m-border); font-size: 12px; }

.mth__badge {
  padding: 4px 10px;
  color: #b66b20;
  background: var(--m-accent-soft);
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
}
.mth__tag { padding: 4px 10px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mth__tag.is-active { color: #1d6b4f; background: var(--surface-soft); }

.mth__deadline { margin-top: 10px; color: var(--m-text-secondary); font-size: 12.5px; }
.mth__deadline.is-urgent { color: #b66b20; font-weight: 700; }
.mth__progress { height: 6px; margin-top: 10px; overflow: hidden; background: var(--surface-soft); border-radius: 4px; }
.mth__progress i { display: block; height: 100%; background: var(--m-primary); border-radius: inherit; }
.mth__progress-text { margin-top: 6px; color: var(--m-text-tertiary); font-size: 12px; }

.mth__review-note { margin-top: 20px; padding: 12px 14px; color: #7c4315; background: var(--m-accent-soft); border-radius: 10px; font-size: 12.5px; line-height: 1.6; }
</style>
