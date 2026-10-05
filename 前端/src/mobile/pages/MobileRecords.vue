<template>
  <MobileShell title="面试记录" subtitle="回看每一次训练与成长">
    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在加载面试记录" />
    <MobileState
      v-else-if="error"
      kind="error"
      title="面试记录加载失败"
      description="请检查网络后重试。"
      action="重新加载"
      @action="load"
    />

    <template v-else>
      <!-- 概览：把 442 条流水收成三个数，一眼看出练了多少、练得怎么样 -->
      <section v-if="finished.length" class="mrec__stats">
        <div class="mrec__stat-row">
          <MobileScoreRing :value="avgScore" :size="72" :stroke="6" caption="平均分" />
          <div class="mrec__stat-text">
            <strong>{{ finished.length }} 场已完成</strong>
            <p>最高 {{ maxScore }} 分 · 共 {{ records.length }} 条记录</p>
            <p class="mrec__stat-hint">{{ trendHint }}</p>
          </div>
        </div>

        <div v-if="recent.length >= 2" class="mrec__trend">
          <h3>最近 {{ recent.length }} 场</h3>
          <MobileMiniChart :points="trendPoints" :height="86" />
        </div>
      </section>

      <!-- 分段：默认落在「已完成」。442 条里 395 条是没做完的「进行中」，
           默认全量展示时它们会挡在最前面，看起来像一长条什么都没有的流水。 -->
      <div class="mobile-segments mrec__segments" role="tablist" aria-label="记录筛选">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          role="tab"
          :aria-selected="active === tab.value"
          :class="{ active: active === tab.value }"
          @click="switchTab(tab.value)"
        >
          {{ tab.label }}<em v-if="tab.count !== null">{{ tab.count }}</em>
        </button>
      </div>

      <MobileState
        v-if="!filtered.length"
        :title="emptyCopy.title"
        :description="emptyCopy.description"
        :action="emptyCopy.action"
        @action="onEmptyAction"
      />

      <template v-else>
        <section class="mobile-record-list">
          <article v-for="record in visible" :key="record.sessionId" class="mrec__card">
            <header class="mrec__head">
              <span class="mrec__icon" :style="{ background: iconBg(record) }">职</span>
              <div class="mrec__title">
                <h2>{{ record.jobName || '面试训练' }}</h2>
                <p>{{ formatDate(record.startTime) }} · {{ formatDuration(record.actualDurationSeconds || record.durationSeconds) }}</p>
              </div>
              <MobileScoreRing
                v-if="hasScore(record)"
                :value="record.totalScore"
                :size="52"
                :stroke="4"
              />
              <span v-else class="mrec__badge" :class="`is-${statusKey(record.status)}`">
                {{ statusLabel(record.status) }}
              </span>
            </header>

            <footer class="mrec__foot">
              <span>{{ statusLabel(record.status) }}</span>
              <button type="button" :disabled="!record.reportId" @click="view(record)">
                {{ record.reportId ? '查看报告 →' : '报告生成中' }}
              </button>
            </footer>
          </article>
        </section>

        <!-- 哨兵：滚到这儿再放出下一批，避免一次渲染 400+ 张卡 -->
        <p v-if="visibleCount < filtered.length" ref="sentinel" class="mrec__more">
          正在加载更多…（已显示 {{ visibleCount }} / {{ filtered.length }}）
        </p>
        <p v-else class="mrec__end">已经到底了 · 共 {{ filtered.length }} 条</p>
      </template>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getInterviewRecords } from '../../api'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import MobileScoreRing from '../components/MobileScoreRing.vue'
import MobileMiniChart from '../components/MobileMiniChart.vue'

/** 每次放出多少条。后端 /interview/records 返回全量且没有分页参数，所以分页在前端做。 */
const PAGE_SIZE = 20

const router = useRouter()
const records = ref([])
const loading = ref(true)
const error = ref(false)
const active = ref('finished')
const visibleCount = ref(PAGE_SIZE)
const sentinel = ref(null)
let observer = null

/* ---------------- 分组 ---------------- */
const finished = computed(() =>
  records.value
    .filter((r) => r.status === 'FINISHED')
    .slice()
    .sort((a, b) => new Date(b.startTime || 0) - new Date(a.startTime || 0))
)
const ongoing = computed(() => records.value.filter((r) => r.status === 'ONGOING'))

const tabs = computed(() => [
  { value: 'finished', label: '已完成', count: finished.value.length },
  { value: 'ongoing', label: '进行中', count: ongoing.value.length },
  { value: 'all', label: '全部', count: records.value.length },
])

const filtered = computed(() => {
  if (active.value === 'finished') return finished.value
  if (active.value === 'ongoing') return ongoing.value
  return records.value
})

const visible = computed(() => filtered.value.slice(0, visibleCount.value))

/* ---------------- 概览 ---------------- */
function hasScore(r) {
  return r.totalScore !== null && r.totalScore !== undefined && Number.isFinite(Number(r.totalScore))
}

/**
 * 只按后端给的 status 分组，不加任何自行发明的门槛。
 *
 * 试过按「时长 >= 60 秒」剔除误触，结果把 47 条里的 34 条都剔掉了，还漏掉了
 * 两条时长够长但 0 分的——说明时长切不干净。分数与时长都分不开真实的作答和
 * 点进去就退出的误触，那就不该由前端来猜：库里 21 条 FINISHED 时长 0~24 秒、
 * 分数 0~4，是后端把中途退出的会话标成了已完成，得在数据侧解决。
 */
const scored = computed(() => finished.value.filter(hasScore))

const avgScore = computed(() => {
  if (!scored.value.length) return null
  return scored.value.reduce((sum, r) => sum + Number(r.totalScore), 0) / scored.value.length
})

const maxScore = computed(() => {
  if (!scored.value.length) return null
  return Math.round(Math.max(...scored.value.map((r) => Number(r.totalScore))))
})

/** 时间正序的最近 5 场，用来画趋势 */
const recent = computed(() =>
  scored.value
    .slice(0, 5)
    .slice()
    .reverse()
)

const trendPoints = computed(() =>
  recent.value.map((r) => ({
    value: Number(r.totalScore),
    label: new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(
      new Date(r.startTime)
    ),
  }))
)

/** 拿最近两场比一比。只说趋势不说结论，样本量太小，讲"进步/退步"会误导。 */
const trendHint = computed(() => {
  const list = recent.value
  if (list.length < 2) return '再完成几场就能看到趋势'
  const last = Number(list[list.length - 1].totalScore)
  const prev = Number(list[list.length - 2].totalScore)
  const delta = Math.round(last - prev)
  if (delta > 0) return `比上一场高 ${delta} 分`
  if (delta < 0) return `比上一场低 ${Math.abs(delta)} 分`
  return '和上一场持平'
})

/* ---------------- 分页 ---------------- */
function disconnect() {
  if (observer) {
    observer.disconnect()
    observer = null
  }
}

function observeSentinel() {
  disconnect()
  if (!sentinel.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) visibleCount.value += PAGE_SIZE
    },
    // 提前 240px 触发，滚到底时下一批已经在渲染了
    { rootMargin: '240px 0px' }
  )
  observer.observe(sentinel.value)
}

watch([() => filtered.value.length, visibleCount, sentinel], () => nextTick(observeSentinel))
watch(active, () => {
  visibleCount.value = PAGE_SIZE
  window.scrollTo({ top: 0, behavior: 'smooth' })
})

function switchTab(value) {
  if (active.value !== value) active.value = value
}

/* ---------------- 展示 ---------------- */
const emptyCopy = computed(() => {
  if (active.value === 'ongoing') {
    return { title: '没有进行中的面试', description: '所有开始的面试都已收尾。', action: '开始新一场', to: '/interview/ai' }
  }
  if (active.value === 'finished') {
    return { title: '还没有完成的面试', description: '完整走完一场面试后，报告和分数会出现在这里。', action: '开始第一场面试', to: '/interview/ai' }
  }
  return { title: '还没有训练记录', description: '完成第一场面试后，你的训练记录会出现在这里。', action: '开始第一场面试', to: '/interview/ai' }
})

function onEmptyAction() {
  router.push(emptyCopy.value.to)
}

const STATUS_MAP = {
  FINISHED: { key: 'finished', label: '已完成' },
  ONGOING: { key: 'ongoing', label: '进行中' },
  ABORTED: { key: 'aborted', label: '已中断' },
}
const statusKey = (s) => STATUS_MAP[s]?.key || 'finished'
const statusLabel = (s) => STATUS_MAP[s]?.label || '已完成'

function iconBg(record) {
  return statusKey(record.status) === 'finished' ? 'var(--m-primary-soft)' : 'var(--m-accent-soft)'
}

function view(record) {
  if (record.reportId) router.push(`/history/${record.reportId}`)
}

function formatDate(value) {
  if (!value) return '日期待记录'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '日期待记录'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(d).replace('/', '月') + '日'
}

function formatDuration(seconds) {
  return seconds ? `${Math.max(1, Math.round(seconds / 60))} 分钟` : '时长待记录'
}

/* ---------------- 加载 ---------------- */
async function load() {
  loading.value = true
  error.value = false
  try {
    records.value = (await getInterviewRecords()) || []
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
onUnmounted(disconnect)
</script>

<style scoped>
.mrec__stats {
  margin-bottom: 20px;
  padding: 18px;
  background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%);
  border: 1px solid #dcebe1;
  border-radius: var(--m-radius-hero);
}

.mrec__stat-row { display: flex; align-items: center; gap: 16px; }
.mrec__stat-text { flex: 1; min-width: 0; }
.mrec__stat-text strong { display: block; font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.mrec__stat-text p { margin-top: 4px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.5; }
.mrec__stat-hint { color: var(--m-primary) !important; font-weight: 700; }

.mrec__trend { margin-top: 18px; padding-top: 16px; border-top: 1px dashed #cfe0d6; }
.mrec__trend h3 { margin-bottom: 12px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 700; }

.mrec__segments { margin-bottom: 16px; }
.mrec__segments em { margin-left: 5px; color: var(--m-text-tertiary); font-size: 11px; font-style: normal; }
.mrec__segments button.active em { color: var(--m-primary); }

.mrec__card {
  padding: 15px 16px 12px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  box-shadow: 0 2px 10px rgba(32, 61, 48, .035);
}

.mrec__head { display: flex; align-items: center; gap: 12px; }
.mrec__icon {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  color: var(--m-primary-dark);
  place-items: center;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 800;
}
.mrec__title { flex: 1; min-width: 0; }
.mrec__title h2 { font-size: 15px; line-height: 1.35; overflow-wrap: anywhere; }
.mrec__title p { margin-top: 4px; color: var(--m-text-tertiary); font-size: 12px; }

.mrec__badge { flex: 0 0 auto; padding: 5px 11px; border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mrec__badge.is-ongoing { color: #b66b20; background: var(--m-accent-soft); }
.mrec__badge.is-aborted { color: var(--m-text-secondary); background: #f0f1ed; }
.mrec__badge.is-finished { color: var(--m-primary-dark); background: var(--m-primary-soft); }

.mrec__foot {
  display: flex;
  min-height: 40px;
  margin-top: 12px;
  padding-top: 10px;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--m-border);
}
.mrec__foot span { color: var(--m-text-tertiary); font-size: 12px; }
.mrec__foot button { min-height: 40px; padding: 0 2px 0 14px; color: var(--m-primary); background: transparent; border: 0; font-size: 13px; font-weight: 700; }
.mrec__foot button:disabled { color: var(--m-text-tertiary); }

.mrec__more,
.mrec__end { padding: 20px 0 4px; color: var(--m-text-tertiary); text-align: center; font-size: 12px; }
</style>
