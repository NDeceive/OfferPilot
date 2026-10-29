<template>
  <MobileShell v-bind="shell" :title="title" :subtitle="subtitle">
    <!-- ============ 编辑器（create / edit / templates）：桌面专属，手机端给出路 ============ -->
    <template v-if="mode === 'editor'">
      <section class="mtt__hint">
        <span class="mtt__hint-icon" aria-hidden="true">▦</span>
        <h2>任务创建与编辑请在电脑端完成</h2>
        <p>题目批量录入、能力评价方案与参与学生勾选需要更大的屏幕。手机端可以查看任务详情、发布草稿，并在任务进行中提醒、延期或结束。</p>
      </section>
      <router-link v-if="editingId" :to="`/teacher/tasks/${editingId}`" class="mobile-primary-button mtt__hint-action">查看任务详情 <span>→</span></router-link>
      <router-link to="/teacher/tasks" class="mobile-secondary-button mtt__hint-action is-block">返回任务列表</router-link>
    </template>

    <template v-else-if="loading">
      <MobileSkeleton variant="card" :rows="3" label="正在加载任务" />
    </template>
    <MobileState v-else-if="error" kind="error" title="任务数据暂时无法加载" description="请检查网络后重试。" action="重新加载" @action="load" />

    <!-- ============ 列表 ============ -->
    <template v-else-if="mode === 'list'">
      <p v-if="classFilterName" class="mtt__notice" role="status">
        仅显示「{{ classFilterName }}」的任务 <button type="button" @click="clearClassFilter">查看全部</button>
      </p>
      <section class="mobile-chip-row mtt__chips" aria-label="任务状态筛选">
        <button v-for="c in chips" :key="c.id" :class="{ active: chip === c.id }" @click="chip = c.id">{{ c.label }} {{ c.count }}</button>
      </section>
      <div v-if="visibleTasks.length" class="mtt__list">
        <router-link v-for="t in visibleTasks" :key="t.id" :to="`/teacher/tasks/${t.id}`" class="mobile-info-card mtt__task">
          <div class="mobile-row">
            <div class="mobile-grow">
              <h3>{{ t.title }}</h3>
              <p>{{ t.className }} · {{ t.jobName || '目标岗位待设置' }}</p>
            </div>
            <span class="mtt__tag" :class="'is-' + lifecycleTone(t.lifecycle)">{{ lifecycleLabel(t.lifecycle) }}</span>
          </div>
          <p class="mtt__deadline" :class="{ 'is-urgent': urgent(t) }">{{ t.deadline ? deadlineHint(t.deadline) : '未设置截止时间' }}</p>
          <template v-if="taskProgress(t)">
            <div class="mtt__progress"><i :style="{ width: progressPct(t) + '%' }"></i></div>
            <p class="mtt__progress-text">已完成 {{ taskProgress(t).completed }} / {{ taskProgress(t).total }} 人</p>
          </template>
        </router-link>
      </div>
      <MobileState v-else-if="!tasks.length" title="还没有训练任务" description="发布任务后，学生的执行进度与报告会汇总在这里。任务创建在电脑端完成。" action="查看任务列表" @action="load" />
      <MobileState v-else title="该状态下没有任务" description="切换到其他状态看看。" action="查看全部" @action="chip = 'all'" />
    </template>

    <!-- ============ 详情 ============ -->
    <template v-else>
      <router-link to="/teacher/tasks" class="mtt__back">← 任务列表</router-link>
      <p v-if="success" class="mtt__notice" role="status">{{ success }}</p>
      <p v-if="actionError" class="mtt__notice is-error" role="alert">{{ actionError }}</p>

      <section class="mtt__hero">
        <div class="mobile-row">
          <div class="mobile-grow"><h2>{{ task.title }}</h2><p>{{ task.className }} · {{ task.jobName || '目标岗位待设置' }}</p></div>
          <span class="mtt__tag" :class="'is-' + lifecycleTone(task.lifecycle)">{{ lifecycleLabel(task.lifecycle) }}</span>
        </div>
        <dl class="mtt__facts">
          <div><dt>截止时间</dt><dd>{{ formatDateTime(task.deadline) }}</dd></div>
          <div><dt>完成要求</dt><dd>有效 {{ task.minAttempts }} 次 / 最多 {{ task.maxAttempts }} 次</dd></div>
          <div><dt>单次时长</dt><dd>{{ Math.round((task.durationSeconds || 0) / 60) }} 分钟</dd></div>
        </dl>
        <p v-if="task.description" class="mtt__desc">{{ task.description }}</p>
      </section>

      <section v-if="task.lifecycle === 'DRAFT'" class="mtt__actions">
        <button type="button" class="is-primary" :disabled="working" @click="publishTask">发布任务</button>
        <router-link :to="`/teacher/tasks/${task.id}/edit`" class="mtt__action-link">编辑草稿（电脑端）</router-link>
        <button type="button" class="is-danger" :disabled="working" @click="discardTask">删除草稿</button>
      </section>
      <section v-else-if="task.publishedAt && !task.endedAt && !task.archived" class="mtt__actions">
        <button type="button" @click="remindTask" :disabled="working">提醒未完成学生</button>
        <button type="button" @click="extendOpen = !extendOpen">延长截止时间</button>
        <button type="button" class="is-danger" :disabled="working" @click="endTask">结束任务</button>
        <form v-if="extendOpen" class="mtt__extend" @submit.prevent="extendTask">
          <label>新截止时间<input v-model="newDeadline" type="datetime-local" required /></label>
          <button type="submit" class="is-primary" :disabled="working">保存延期</button>
        </form>
      </section>

      <section v-if="task.questions?.length" class="mobile-section">
        <header class="mobile-section-heading"><h2>训练问题</h2><span>{{ task.questions.length }} 道</span></header>
        <ol class="mtt__questions">
          <li v-for="(q, i) in task.questions" :key="i">{{ q }}</li>
        </ol>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>学生执行与报告</h2><span>{{ assignments.length }} 人</span></header>
        <div v-if="assignments.length" class="mtt__list">
          <article v-for="a in assignments" :key="a.id" class="mobile-info-card mtt__assignment">
            <div class="mobile-row">
              <div class="mobile-grow">
                <h3>{{ a.name }}</h3>
                <p>{{ a.username }} · {{ completionLabel(a.completionStatus) }}</p>
              </div>
              <span class="mtt__valid">有效 {{ a.validCount ?? 0 }}/{{ task.minAttempts }}</span>
            </div>
            <div v-if="a.attempts?.length" class="mtt__attempts">
              <div v-for="p in a.attempts" :key="p.id" class="mtt__attempt">
                <span>{{ attemptStateLabel(p.state) }}</span>
                <small>{{ p.submittedAt ? formatDateTime(p.submittedAt) : '尚未提交' }}</small>
                <router-link v-if="p.reportId" :to="`/teacher/reports/${p.reportId}`">查看报告与点评 →</router-link>
                <small v-if="p.failureReason" class="is-error">{{ p.failureReason }}</small>
              </div>
            </div>
            <p v-else class="mtt__no-attempt">尚无训练结果</p>
          </article>
        </div>
        <MobileState v-else title="还没有参与学生" description="任务发布后，学生的训练进展会出现在这里。" />
      </section>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teaching } from '../../services/teachingApi'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { TEACHER_TABS } from '../nav'
import { attemptStateLabel, completionLabel, deadlineHint, formatDateTime, lifecycleLabel, lifecycleTone, taskProgress } from '../teacherFormat'

const route = useRoute()
const router = useRouter()

const shell = computed(() => ({
  homePath: '/teacher/dashboard',
  messagePath: '/teacher/messages',
  profilePath: '/teacher/account',
  navLinks: [],
  tabItems: TEACHER_TABS,
}))

const mode = computed(() => {
  const p = route.path
  if (p.endsWith('/create') || p.endsWith('/edit') || p.endsWith('/templates')) return 'editor'
  return route.params.id ? 'detail' : 'list'
})
const editingId = computed(() => (mode.value === 'editor' && route.path.endsWith('/edit') ? Number(route.params.id) : 0))

const title = computed(() => {
  if (mode.value === 'editor') {
    if (route.path.endsWith('/edit')) return '编辑任务草稿'
    if (route.path.endsWith('/templates')) return '任务模板'
    return '创建训练任务'
  }
  if (mode.value === 'detail') return task.value?.title || '任务详情'
  return '训练任务'
})
const subtitle = computed(() => {
  if (mode.value === 'editor') return '手机端可查看与跟进，创建编辑用电脑端'
  if (mode.value === 'detail') return '任务要求 · 学生执行与报告'
  return '发布后跟进执行、提醒与截止时间'
})

const loading = ref(true)
const error = ref(false)
const working = ref(false)
const success = ref('')
const actionError = ref('')
const tasks = ref([])
const task = ref(null)
const chip = ref('all')
const extendOpen = ref(false)
const newDeadline = ref('')

const assignments = computed(() => task.value?.assignments || [])
const classFilterId = computed(() => Number(route.query.classId) || 0)
const classFilterName = computed(() => {
  if (!classFilterId.value) return ''
  return tasks.value.find((t) => Number(t.classId) === classFilterId.value)?.className || ''
})

const counts = computed(() => ({
  all: tasks.value.length,
  DRAFT: tasks.value.filter((t) => t.lifecycle === 'DRAFT').length,
  ACTIVE: tasks.value.filter((t) => t.lifecycle === 'ACTIVE' || t.lifecycle === 'SCHEDULED').length,
  ended: tasks.value.filter((t) => t.lifecycle === 'ENDED' || t.lifecycle === 'CLOSED').length,
}))
const chips = computed(() => [
  { id: 'all', label: '全部', count: counts.value.all },
  { id: 'DRAFT', label: '草稿', count: counts.value.DRAFT },
  { id: 'ACTIVE', label: '进行中', count: counts.value.ACTIVE },
  { id: 'ended', label: '已结束', count: counts.value.ended },
])

const visibleTasks = computed(() => {
  let list = tasks.value
  if (classFilterId.value) list = list.filter((t) => Number(t.classId) === classFilterId.value)
  if (chip.value === 'DRAFT') list = list.filter((t) => t.lifecycle === 'DRAFT')
  else if (chip.value === 'ACTIVE') list = list.filter((t) => t.lifecycle === 'ACTIVE' || t.lifecycle === 'SCHEDULED')
  else if (chip.value === 'ended') list = list.filter((t) => t.lifecycle === 'ENDED' || t.lifecycle === 'CLOSED')
  return list.slice().sort((a, b) => String(b.deadline || '').localeCompare(String(a.deadline || '')))
})

function progressPct(t) {
  const p = taskProgress(t)
  return p && p.total ? Math.round((p.completed / p.total) * 100) : 0
}
function urgent(t) {
  if (t.lifecycle !== 'ACTIVE') return false
  const hint = deadlineHint(t.deadline)
  return hint.startsWith('剩') || hint === '已截止'
}
function clearClassFilter() {
  router.replace({ path: '/teacher/tasks' })
}

async function load() {
  loading.value = true
  error.value = false
  try {
    if (mode.value === 'list') {
      tasks.value = (await teaching.tasks({ includeAssignments: true })) || []
    } else if (mode.value === 'detail') {
      task.value = await teaching.task(Number(route.params.id))
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function act(action, message) {
  if (working.value) return
  working.value = true
  success.value = ''
  actionError.value = ''
  try {
    await action()
    success.value = message
    await load()
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message || '操作失败，请重试'
  } finally {
    working.value = false
  }
}

function publishTask() {
  act(() => teaching.publish(task.value.id), '任务已发布，学生已收到站内消息。')
}
function remindTask() {
  act(() => teaching.remind(task.value.id), '提醒已发送给未完成且未减免的学生。')
}
function extendTask() {
  // 成功才收表单：失败时保留输入，老师不用重新选时间（act 会展示错误）
  act(async () => {
    await teaching.extend(task.value.id, newDeadline.value)
    extendOpen.value = false
  }, '任务截止时间已更新。')
}
function endTask() {
  if (!window.confirm('结束后不能开始新训练，历史报告保留。')) return
  act(() => teaching.end(task.value.id), '任务已结束。')
}
function discardTask() {
  if (!window.confirm('删除未发布草稿后无法恢复。')) return
  act(async () => {
    await teaching.discard(task.value.id)
    router.replace('/teacher/tasks')
  }, '草稿已删除。')
}

watch(() => route.fullPath, () => {
  success.value = ''
  actionError.value = ''
  extendOpen.value = false
  if (mode.value !== 'editor') load()
  else loading.value = false
}, { immediate: true })
</script>

<style scoped>
.mtt__notice { margin-bottom: 12px; padding: 11px 14px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 10px; font-size: 13px; line-height: 1.7; }
.mtt__notice.is-error { color: #7c4315; background: var(--m-accent-soft); }
.mtt__notice button { margin-left: 6px; color: var(--m-primary-dark); background: none; border: 0; font-size: 13px; font-weight: 700; text-decoration: underline; }

.mtt__hint { padding: 24px 20px; text-align: center; background: var(--m-surface); border: 1px dashed #d9dfda; border-radius: var(--m-radius-card); }
.mtt__hint-icon { display: grid; width: 46px; height: 46px; margin: 0 auto 14px; color: var(--m-primary); place-items: center; background: var(--m-primary-soft); border-radius: 50%; font-size: 18px; }
.mtt__hint h2 { font-size: 16.5px; }
.mtt__hint p { margin-top: 9px; color: var(--m-text-secondary); font-size: 13.5px; line-height: 1.7; text-align: left; }
.mtt__hint-action { width: 100%; margin-top: 16px; }
.mtt__hint-action.is-block { display: flex; }

.mtt__chips { margin-bottom: 14px; }
.mtt__list { display: flex; flex-direction: column; gap: 12px; }
.mtt__task h3 { overflow-wrap: anywhere; }
.mtt__deadline { margin-top: 10px; color: var(--m-text-secondary); font-size: 12.5px; }
.mtt__deadline.is-urgent { color: #b66b20; font-weight: 700; }
.mtt__progress { height: 6px; margin-top: 10px; overflow: hidden; background: var(--surface-soft); border-radius: 4px; }
.mtt__progress i { display: block; height: 100%; background: var(--m-primary); border-radius: inherit; }
.mtt__progress-text { margin-top: 6px; color: var(--m-text-tertiary); font-size: 12px; }

.mtt__tag { flex: 0 0 auto; padding: 4px 10px; border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mtt__tag.is-active { color: #1d6b4f; background: var(--surface-soft); }
.mtt__tag.is-draft { color: #b66b20; background: var(--m-accent-soft); }
.mtt__tag.is-ended { color: var(--m-text-tertiary); background: var(--surface-soft); }

.mtt__back { display: inline-flex; min-height: 40px; align-items: center; margin: -6px 0 10px; color: var(--m-primary); font-size: 13.5px; font-weight: 700; }

.mtt__hero { padding: 18px; background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%); border: 1px solid #dcebe1; border-radius: var(--m-radius-hero); }
.mtt__hero h2 { font-size: 19px; letter-spacing: -.02em; }
.mtt__hero .mobile-row > div > p { margin-top: 4px; color: var(--m-text-secondary); font-size: 13px; }
.mtt__facts { display: grid; margin-top: 16px; padding-top: 14px; gap: 8px; border-top: 1px dashed #cfe0d6; }
.mtt__facts div { display: flex; gap: 10px; font-size: 13px; }
.mtt__facts dt { flex: 0 0 68px; color: var(--m-text-tertiary); }
.mtt__facts dd { color: var(--m-text); font-weight: 600; }
.mtt__desc { margin-top: 14px; color: var(--m-text-secondary); font-size: 13.5px; line-height: 1.7; white-space: pre-wrap; }

.mtt__actions { display: flex; margin-top: 16px; flex-direction: column; gap: 10px; }
.mtt__actions > button,
.mtt__action-link {
  display: flex;
  min-height: 46px;
  align-items: center;
  justify-content: center;
  color: var(--m-text-secondary);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-button);
  font-size: 14px;
  font-weight: 700;
}
.mtt__actions > button.is-primary { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mtt__actions > button.is-danger { color: #b42318; border-color: #f0d1ce; }
.mtt__actions > button:disabled { opacity: .55; }
.mtt__extend { display: grid; padding: 14px; gap: 10px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mtt__extend label { display: grid; gap: 6px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 600; }
.mtt__extend input { min-height: 44px; padding: 0 12px; color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-input); font: inherit; font-size: 14px; }

.mtt__questions { padding: 4px 0 4px 20px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); list-style: decimal; }
.mtt__questions li { padding: 10px 14px 10px 4px; color: var(--m-text); border-bottom: 1px solid var(--m-border); font-size: 13.5px; line-height: 1.7; }
.mtt__questions li:last-child { border-bottom: 0; }

.mtt__assignment h3 { font-size: 15px; }
.mtt__valid { flex: 0 0 auto; padding-left: 12px; color: var(--m-primary-dark); border-left: 1px solid var(--m-border); font-size: 13px; font-weight: 700; }
.mtt__attempts { margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--m-border); }
.mtt__attempt { display: flex; padding: 5px 0; align-items: baseline; gap: 8px; flex-wrap: wrap; font-size: 12.5px; }
.mtt__attempt > span { color: var(--m-text); font-weight: 700; }
.mtt__attempt small { color: var(--m-text-tertiary); }
.mtt__attempt a { color: var(--m-primary); font-weight: 700; }
.mtt__attempt small.is-error { flex-basis: 100%; color: #b42318; }
.mtt__no-attempt { margin-top: 10px; color: var(--m-text-tertiary); font-size: 12.5px; }
</style>
