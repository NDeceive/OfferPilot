<template>
  <MobileShell v-bind="shell" :title="title" :subtitle="subtitle">
    <template v-if="loading">
      <MobileSkeleton variant="card" :rows="3" label="正在加载学生名单" />
    </template>
    <MobileState v-else-if="error" kind="error" title="学生数据暂时无法加载" description="请检查网络后重试。" action="重新加载" @action="load" />

    <!-- ============ 名单 ============ -->
    <template v-else-if="!studentId">
      <label class="mobile-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input v-model.trim="keyword" type="search" placeholder="搜索姓名或账号" />
      </label>
      <section v-if="classes.length > 1" class="mobile-chip-row mts__chips" aria-label="按班级筛选">
        <button :class="{ active: !classFilter }" @click="classFilter = ''">全部班级</button>
        <button v-for="c in classes" :key="c.id" :class="{ active: String(classFilter) === String(c.id) }" @click="classFilter = String(c.id)">{{ c.name }}</button>
      </section>
      <div v-if="visiblePeople.length" class="mts__list">
        <router-link v-for="p in visiblePeople" :key="p.classId + '-' + p.id" :to="`/teacher/students/${p.id}`" class="mobile-info-card mts__person">
          <div class="mobile-row">
            <span class="mobile-square-icon">{{ (p.name || '?').slice(0, 1) }}</span>
            <div class="mobile-grow"><h3>{{ p.name }}</h3><p>{{ p.username }} · {{ p.className }}</p></div>
            <b class="mts__arrow">›</b>
          </div>
        </router-link>
      </div>
      <MobileState
        v-else
        :title="people.length ? '没有匹配的学生' : '学生名单等待建立'"
        :description="people.length ? '换个关键词，或清除班级筛选试试。' : '创建班级并分享邀请码，审核通过后学生及其训练进展会显示在这里。'"
        :action="people.length ? '清除筛选' : '去管理班级'"
        @action="people.length ? clearFilters() : $router.push('/teacher/classes')"
      />
    </template>

    <!-- ============ 详情 ============ -->
    <template v-else>
      <router-link to="/teacher/students" class="mts__back">← 学生名单</router-link>
      <template v-if="person">
        <section class="mts__hero">
          <div class="mobile-row">
            <span class="mts__avatar">{{ (person.name || '?').slice(0, 1) }}</span>
            <div class="mobile-grow">
              <h2>{{ person.name }}</h2>
              <p>{{ person.username }}<template v-if="person.className"> · {{ person.className }}</template></p>
            </div>
          </div>
          <div class="mts__stats">
            <div><strong>{{ taskRows.length }}</strong><small>分配任务</small></div>
            <div><strong>{{ completedCount }}</strong><small>已完成要求</small></div>
            <div><strong>{{ validTotal }}</strong><small>有效训练</small></div>
          </div>
        </section>

        <section class="mobile-section">
          <header class="mobile-section-heading"><h2>该学生的教学任务与报告</h2></header>
          <div v-if="taskRows.length" class="mts__list">
            <article v-for="row in taskRows" :key="row.a.id" class="mobile-info-card mts__task">
              <router-link :to="`/teacher/tasks/${row.task.id}`" class="mts__task-link">
                <h3>{{ row.task.title }}</h3>
                <p>{{ row.task.className }} · {{ completionLabel(row.a.completionStatus) }} · 有效 {{ row.a.validCount ?? 0 }} / {{ row.task.minAttempts }} 次</p>
              </router-link>
              <div v-if="row.a.attempts?.length" class="mts__attempts">
                <div v-for="p in row.a.attempts" :key="p.id" class="mts__attempt">
                  <span>{{ attemptStateLabel(p.state) }}</span>
                  <small>{{ formatDateTime(p.submittedAt) }}</small>
                  <router-link v-if="p.reportId" :to="`/teacher/reports/${p.reportId}`">查看报告与点评 →</router-link>
                </div>
              </div>
              <p v-else class="mts__no-attempt">尚无训练结果</p>
            </article>
          </div>
          <MobileState v-else title="暂无分配给该学生的任务" description="发布训练任务并选择该学生后，执行情况会显示在这里。" />
        </section>
      </template>
      <MobileState v-else title="未找到该学生" description="学生不在你管理的班级名单中。" action="返回学生名单" @action="$router.push('/teacher/students')" />
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
import { attemptStateLabel, completionLabel, formatDateTime } from '../teacherFormat'

const route = useRoute()
const router = useRouter()

const shell = computed(() => ({
  homePath: '/teacher/dashboard',
  messagePath: '/teacher/messages',
  profilePath: '/teacher/account',
  navLinks: [],
  tabItems: TEACHER_TABS,
}))

const loading = ref(true)
const error = ref(false)
const people = ref([])
const classes = ref([])
const tasks = ref([])
const keyword = ref('')
const classFilter = ref('')

const studentId = computed(() => Number(route.params.id) || 0)
const title = computed(() => (studentId.value ? (person.value?.name || '学生详情') : '学生'))
const subtitle = computed(() => (studentId.value ? '教学任务与训练报告' : '你管理的班级成员'))

const person = computed(() => people.value.find((p) => String(p.id) === String(studentId.value)) || null)

const visiblePeople = computed(() => {
  let list = people.value
  if (classFilter.value) list = list.filter((p) => String(p.classId) === String(classFilter.value))
  const q = keyword.value.toLowerCase()
  if (q) list = list.filter((p) => `${p.name} ${p.username}`.toLowerCase().includes(q))
  return list
})

/** 该学生的任务行：逐任务找他的 assignment（与桌面端 studentTasks 同取法） */
const taskRows = computed(() => {
  const id = String(studentId.value)
  return tasks.value
    .filter((t) => t.publishedAt && Array.isArray(t.assignments))
    .flatMap((t) => {
      const a = t.assignments.find((x) => String(x.studentId) === id)
      return a ? [{ task: t, a }] : []
    })
})
const completedCount = computed(() => taskRows.value.filter((r) => r.a.completionStatus === 'COMPLETED').length)
const validTotal = computed(() =>
  taskRows.value.reduce((n, r) => n + (r.a.attempts || []).filter((p) => p.state === 'READY' && p.valid !== false).length, 0)
)

function clearFilters() {
  keyword.value = ''
  classFilter.value = ''
  if (Object.keys(route.query).length) router.replace({ path: route.path })
}

async function load() {
  loading.value = true
  error.value = false
  try {
    const [ppl, cls] = await Promise.all([teaching.people(), teaching.classes()])
    people.value = ppl || []
    classes.value = cls || []
    if (studentId.value) {
      tasks.value = (await teaching.tasks({ includeAssignments: true })) || []
    } else {
      tasks.value = []
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(() => [route.path, route.params.id].join('|'), load, { immediate: true })
</script>

<style scoped>
.mts__chips { margin: 14px 0 2px; }
.mts__list { display: flex; margin-top: 14px; flex-direction: column; gap: 12px; }
.mts__person h3 { font-size: 15.5px; }
.mts__arrow { color: var(--m-text-tertiary); font-size: 20px; }

.mts__back { display: inline-flex; min-height: 40px; align-items: center; margin: -6px 0 10px; color: var(--m-primary); font-size: 13.5px; font-weight: 700; }

.mts__hero { padding: 18px; background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%); border: 1px solid #dcebe1; border-radius: var(--m-radius-hero); }
.mts__avatar { display: grid; flex: 0 0 auto; width: 46px; height: 46px; color: var(--m-primary-dark); place-items: center; background: color-mix(in srgb, var(--surface-elevated) 80%, transparent); border: 1px solid #dcebe1; border-radius: 50%; font-size: 17px; font-weight: 800; }
.mts__hero h2 { font-size: 19px; }
.mts__hero .mobile-row > div > p { margin-top: 4px; color: var(--m-text-secondary); font-size: 13px; }
.mts__stats { display: grid; margin-top: 16px; padding-top: 14px; grid-template-columns: repeat(3, 1fr); gap: 8px; border-top: 1px dashed #cfe0d6; }
.mts__stats div { display: flex; align-items: center; flex-direction: column; gap: 3px; }
.mts__stats strong { color: var(--m-primary-dark); font-size: 20px; }
.mts__stats small { color: var(--m-text-tertiary); font-size: 11px; }

.mts__task-link { display: block; }
.mts__task-link h3 { font-size: 15px; overflow-wrap: anywhere; }
.mts__task-link p { margin-top: 5px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.6; }
.mts__attempts { margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--m-border); }
.mts__attempt { display: flex; padding: 5px 0; align-items: baseline; gap: 8px; flex-wrap: wrap; font-size: 12.5px; }
.mts__attempt > span { color: var(--m-text); font-weight: 700; }
.mts__attempt small { color: var(--m-text-tertiary); }
.mts__attempt a { color: var(--m-primary); font-weight: 700; }
.mts__no-attempt { margin-top: 10px; color: var(--m-text-tertiary); font-size: 12.5px; }
</style>
