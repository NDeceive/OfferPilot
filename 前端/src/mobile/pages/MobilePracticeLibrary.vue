<!--
  手机个人题库：最近练习 / 错题本 / 我的收藏。
  一级：三个题库入口；二级：状态筛选与记录列表；三级：单条记录的查看操作。
  主操作只有一个：继续练习或查看原题。取消收藏走「撤销条」，不弹系统确认框。
-->
<template>
  <MobileShell :title="title" :subtitle="subtitle">
    <div v-if="kind === 'recent'" class="mpl-filter-row">
      <div class="mpl-filter" role="group" aria-label="练习状态">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          type="button"
          :class="{ active: status === option.value }"
          :aria-pressed="status === option.value"
          @click="changeStatus(option.value)"
        >{{ option.label }}</button>
      </div>
      <span class="mpl-total">{{ loading ? '读取中' : `共 ${total} 个` }}</span>
    </div>

    <p v-if="kind === 'mistakes'" class="mpl-note">
      <router-link to="/learning/weak" class="mpl-note-link">按知识点补漏 →</router-link>
    </p>
    <p v-if="actionError" class="mpl-action-error" role="alert">{{ actionError }}</p>

    <MobileSkeleton v-if="loading" class="mpl-loading" variant="card" :rows="5" label="正在加载个人题库" />

    <MobileState
      v-else-if="error"
      class="mpl-empty"
      kind="error"
      title="记录暂时打不开"
      :description="error"
      action="重新加载"
      @action="reload"
    />

    <MobileState
      v-else-if="!items.length"
      class="mpl-empty"
      :title="emptyCopy.title"
      :description="emptyCopy.description"
      :action="emptyCopy.action"
      @action="emptyAction"
    />

    <template v-else>
      <section class="mpl-list" :aria-busy="loadingMore">
        <template v-for="(item, index) in items" :key="cardKey(item)">
          <router-link
            v-if="kind === 'recent'"
            class="mpl-card mpl-session"
            :to="recentLink(item)"
            :style="{ '--i': Math.min(index, 8) }"
          >
            <header class="mpl-session-head">
              <span class="mpl-mark is-live" aria-hidden="true">{{ hasReport(item) ? '报' : '练' }}</span>
              <div class="mobile-grow">
                <h2>{{ item.topic || '专项练习' }}</h2>
                <p>{{ item.role?.name || '历史岗位' }} · {{ item.company || '不限公司' }}</p>
              </div>
              <span class="mpl-state" :class="recentStateClass(item)">{{ recentStateLabel(item) }}</span>
            </header>

            <div class="mpl-progress-line">
              <div
                class="mpl-progress"
                role="progressbar"
                :aria-valuenow="progressOf(item)"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-label="`已提交 ${item.completed || 0} / ${item.questionCount || 0} 题`"
              >
                <i :style="{ width: `${progressOf(item)}%` }"></i>
              </div>
              <span>{{ item.completed || 0 }} / {{ item.questionCount || 0 }} 题</span>
            </div>

            <footer class="mpl-session-foot">
              <time v-if="dateOnly(item.updatedAt)">更新于 {{ dateOnly(item.updatedAt) }}</time>
              <span v-else>共 {{ item.questionCount || 0 }} 题</span>
              <strong>{{ hasReport(item) ? '查看报告' : '继续练习' }} <span aria-hidden="true">→</span></strong>
            </footer>
          </router-link>

          <article v-else class="mpl-card mpl-question" :style="{ '--i': Math.min(index, 8) }">
            <router-link class="mpl-question-main" :to="questionLink(item)">
              <header class="mpl-question-head">
                <span class="mpl-mark" :class="kind === 'favorites' ? 'is-fav' : 'is-mistake'" aria-hidden="true">{{ kind === 'favorites' ? '藏' : '错' }}</span>
                <div class="mobile-grow">
                  <h2>{{ item.title || '练习题目' }}</h2>
                  <p>{{ item.jobName || '历史岗位' }} · {{ item.topic || '专项练习' }}</p>
                </div>
                <span class="mpl-chevron" aria-hidden="true">›</span>
              </header>

              <div class="mpl-tags">
                <span class="mpl-tag mpl-type">{{ practiceTypeLabel(item.questionType) }}</span>
                <span v-if="item.submissionStatus" class="mpl-tag" :class="outcomeTone(item.submissionStatus)">{{ outcomeLabel(item.submissionStatus) }}</span>
                <span v-if="item.reviewState === 'PENDING'" class="mpl-tag is-review">待复习</span>
                <span v-else-if="item.reviewState === 'RESOLVED'" class="mpl-tag is-done">已复习</span>
              </div>
            </router-link>

            <footer class="mpl-question-foot">
              <span class="mpl-company">{{ item.company || '不限公司' }}</span>
              <div class="mpl-question-actions">
                <button
                  v-if="kind === 'favorites'"
                  type="button"
                  class="mpl-remove"
                  :disabled="removingId !== null"
                  :aria-label="`取消收藏：${item.title || '这道题'}`"
                  @click="removeFavorite(item, index)"
                >取消收藏</button>
                <router-link :to="questionLink(item)">查看原题 <span aria-hidden="true">→</span></router-link>
              </div>
            </footer>
          </article>
        </template>
      </section>

      <div class="mpl-more">
        <button v-if="hasMore" type="button" class="mpl-more-btn" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '正在加载…' : `加载更多（还有 ${total - items.length} 条）` }}
        </button>
        <p v-else class="mpl-end">共 {{ total }} {{ totalUnit }}</p>
        <p v-if="loadMoreError" class="mpl-load-error" role="alert">
          {{ loadMoreError }}
          <button type="button" @click="loadMore">重试</button>
        </p>
      </div>
    </template>

    <Transition name="mpl-toast">
      <div v-if="undo" class="mpl-toast" role="status" aria-live="polite">
        <span>已取消收藏</span>
        <button type="button" @click="undoFavorite">撤销</button>
      </div>
    </Transition>
  </MobileShell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import {
  getPracticeLibrary,
  getPracticeSessions,
  removePracticeFavorite,
  togglePracticeFavorite,
} from '../../services/learningResources'
import { practiceTypeLabel } from '../../utils/practiceTypes'
import { judgeStatusLabel, pendingJudgeStatuses } from '../../utils/practiceJudging'
import { formatPracticeDate } from '../../utils/practiceReport'

const route = useRoute()
const router = useRouter()

const sections = { recent: '最近练习', mistakes: '错题本', favorites: '我的收藏' }
const subtitles = {
  recent: '继续作答，或回看已结束的练习',
  mistakes: '按最新提交整理，集中复盘薄弱题',
  favorites: '收藏的题都在这里，随时回到原题',
}
const statusOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'ONGOING', label: '进行中' },
  { value: 'FINISHED', label: '已结束' },
]
const emptyCopies = {
  recent: { title: '还没有练习记录', description: '选一个知识点做一组题，记录会出现在这里。', action: '去刷题' },
  mistakes: { title: '错题本还是空的', description: '做错的题和标记待复习的题会集中在这里。', action: '去做一组题' },
  favorites: { title: '还没有收藏题目', description: '在练习报告里点「☆ 收藏」，想回顾的题就会留在这里。', action: '去刷题' },
}
const PAGE_SIZE = 20
const UNDO_MS = 6000

const kind = computed(() => Object.hasOwn(sections, route.params.kind) ? route.params.kind : 'recent')
const title = computed(() => sections[kind.value])
const subtitle = computed(() => subtitles[kind.value])
const emptyCopy = computed(() => {
  if (kind.value !== 'recent') return emptyCopies[kind.value]
  if (status.value === 'ONGOING') {
    return { title: '没有进行中的练习', description: '进行中的题单会出现在这里；也可以看看全部记录。', action: '看全部' }
  }
  if (status.value === 'FINISHED') {
    return { title: '还没有已结束的练习', description: '做完一组题并结算后，记录会归到这里。', action: '看全部' }
  }
  return emptyCopies.recent
})
const totalUnit = computed(() => kind.value === 'recent' ? '个题单' : '道题')

const status = ref(statusFromQuery())
const items = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const loadMoreError = ref('')
const actionError = ref('')
const removingId = ref(null)
const undo = ref(null)
const hasMore = computed(() => items.value.length < total.value)

let requestSeq = 0
let listGeneration = 0
let undoTimer = null
let disposed = false

function statusFromQuery() {
  return ['ONGOING', 'FINISHED'].includes(route.query.status) ? route.query.status : 'ALL'
}

async function fetchPage(target, append) {
  const seq = ++requestSeq
  if (append) {
    loadingMore.value = true
    loadMoreError.value = ''
  } else {
    loading.value = true
    error.value = ''
    loadMoreError.value = ''
  }

  try {
    const result = kind.value === 'recent'
      ? await getPracticeSessions({ status: status.value, page: target, size: PAGE_SIZE })
      : await getPracticeLibrary(kind.value, { page: target, size: PAGE_SIZE })
    if (disposed || seq !== requestSeq) return false

    const list = Array.isArray(result?.items) ? result.items : []
    items.value = append ? [...items.value, ...list] : list
    total.value = Math.max(0, Number(result?.total) || 0)
    page.value = target
    return true
  } catch (e) {
    if (disposed || seq !== requestSeq) return false
    if (append) {
      loadMoreError.value = e?.message || '加载更多失败，请重试。'
    } else {
      error.value = e?.message || '记录加载失败，请重试。'
      items.value = []
      total.value = 0
    }
    return false
  } finally {
    if (!disposed && seq === requestSeq) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

function emptyAction() {
  if (kind.value === 'recent' && status.value !== 'ALL') {
    changeStatus('ALL')
    return
  }
  router.push('/learning')
}

function reload() {
  actionError.value = ''
  fetchPage(1, false)
}

function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  actionError.value = ''
  fetchPage(page.value + 1, true)
}

function scrollTop() {
  if (typeof window === 'undefined' || window.scrollY <= 0) return
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })
}

function changeStatus(next) {
  if (next === status.value) return
  status.value = next
  actionError.value = ''
  const query = { ...route.query }
  if (next === 'ALL') delete query.status
  else query.status = next
  router.replace({ path: route.path, query })
  scrollTop()
  fetchPage(1, false)
}

function recentLink(item) {
  const base = `/learning/session/${item.id}`
  return hasReport(item) ? `${base}/report` : base
}

function questionLink(item) {
  return { path: `/learning/session/${item.sessionId}`, query: { questionId: item.id } }
}

function cardKey(item) {
  return kind.value === 'recent' ? `s-${item.id}` : `q-${item.sessionId || 'x'}-${item.id}`
}

function hasReport(item) {
  return ['FINISHED', 'CLOSING'].includes(item.status)
}

function recentStateLabel(item) {
  if (item.status === 'FINISHED') return '已结束'
  if (item.status === 'CLOSING') return '结算中'
  return '进行中'
}

function recentStateClass(item) {
  if (item.status === 'FINISHED') return 'is-finished'
  if (item.status === 'CLOSING') return 'is-closing'
  return 'is-ongoing'
}

function progressOf(item) {
  const totalQuestions = Number(item.questionCount) || 0
  if (!totalQuestions) return 0
  return Math.min(100, Math.round((Number(item.completed) || 0) / totalQuestions * 100))
}

function dateOnly(value) {
  const text = formatPracticeDate(value)
  return text === '—' ? '' : text.split(' ')[0]
}

const passedStatuses = ['OBJECTIVE_CORRECT', 'ACCEPTED']
const failedStatuses = [
  'OBJECTIVE_INCORRECT', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED',
]
const systemStatuses = ['SYSTEM_ERROR', 'SERVICE_UNAVAILABLE']
const warningStatuses = ['SELF_REVIEW', 'SUBMITTED', 'REVIEWED', 'NEEDS_REVIEW']

function outcomeTone(status) {
  if (passedStatuses.includes(status)) return 'is-pass'
  if (failedStatuses.includes(status)) return 'is-fail'
  if (systemStatuses.includes(status)) return 'is-system'
  if (pendingJudgeStatuses.has(status) || warningStatuses.includes(status)) return 'is-warn'
  return 'is-neutral'
}

function outcomeLabel(status) {
  if (!status) return ''
  if (pendingJudgeStatuses.has(status)) return '判题中'
  return ({
    OBJECTIVE_CORRECT: '正确',
    OBJECTIVE_INCORRECT: '错误',
    ACCEPTED: '通过',
    WRONG_ANSWER: '未通过',
    COMPILE_ERROR: '编译错误',
    RUNTIME_ERROR: '运行错误',
    TIME_LIMIT_EXCEEDED: '运行超时',
    MEMORY_LIMIT_EXCEEDED: '内存超限',
    OUTPUT_LIMIT_EXCEEDED: '输出超限',
    SYSTEM_ERROR: '判题异常',
    SERVICE_UNAVAILABLE: '服务不可用',
    SELF_REVIEW: '待自评',
    SUBMITTED: '待结算',
    REVIEWED: '建议复习',
    NEEDS_REVIEW: '建议复习',
  })[status] || judgeStatusLabel(status)
}

function clearUndo() {
  if (undoTimer) clearTimeout(undoTimer)
  undoTimer = null
  undo.value = null
}

async function removeFavorite(item, index) {
  if (removingId.value) return
  clearUndo()
  actionError.value = ''
  removingId.value = item.id
  const activeKind = kind.value
  const generation = listGeneration

  const at = index >= 0 && items.value[index]?.id === item.id
    ? index
    : items.value.findIndex(entry => entry.id === item.id && entry.sessionId === item.sessionId)
  if (at < 0) {
    removingId.value = null
    return
  }
  const restoreAt = at
  items.value = items.value.filter((entry, i) => i !== restoreAt)
  total.value = Math.max(0, total.value - 1)

  try {
    await removePracticeFavorite(item.id)
    if (disposed) return
    // 请求期间切走标签页时，不再往当前列表里塞撤销条。
    if (kind.value !== activeKind || listGeneration !== generation) return
    undo.value = { item, index: restoreAt }
    undoTimer = setTimeout(() => { undoTimer = null; undo.value = null }, UNDO_MS)
    if (!items.value.length && total.value > 0 && !loading.value && !loadingMore.value) fetchPage(1, false)
  } catch (e) {
    if (disposed) return
    if (kind.value !== activeKind || listGeneration !== generation) return
    items.value.splice(Math.min(restoreAt, items.value.length), 0, item)
    total.value += 1
    actionError.value = e?.message || '取消收藏失败，请重试。'
  } finally {
    if (!disposed) removingId.value = null
  }
}

async function undoFavorite() {
  const snapshot = undo.value
  if (!snapshot) return
  const activeKind = kind.value
  const generation = listGeneration
  clearUndo()
  const { item, index } = snapshot
  const same = entry => entry.id === item.id && entry.sessionId === item.sessionId

  if (activeKind === 'favorites' && !items.value.some(same)) {
    items.value.splice(Math.max(0, Math.min(index, items.value.length)), 0, item)
    total.value += 1
  }

  try {
    await togglePracticeFavorite(item.id)
  } catch (e) {
    if (disposed) return
    // 请求期间切走标签页：不动别的列表，也不在别的标签页弹收藏错误。
    if (kind.value !== activeKind || listGeneration !== generation) return
    actionError.value = e?.message || '恢复收藏失败，请重试。'
    // 失败时按服务端重新对齐，避免并发刷新后手工回滚出重复项。
    fetchPage(1, false)
  }
}

watch(() => route.params.kind, () => {
  listGeneration++
  clearUndo()
  actionError.value = ''
  if (kind.value === 'recent') status.value = statusFromQuery()
  scrollTop()
  fetchPage(1, false)
})

watch(() => route.query.status, value => {
  if (kind.value !== 'recent') return
  const next = ['ONGOING', 'FINISHED'].includes(value) ? value : 'ALL'
  if (next === status.value) return
  listGeneration++
  status.value = next
  scrollTop()
  fetchPage(1, false)
})

onMounted(() => {
  // 从首页的长列表进来时浏览器会保留上一页滚动位置；题库必须从顶部开始。
  window.scrollTo(0, 0)
  fetchPage(1, false)
})

onBeforeUnmount(() => {
  disposed = true
  requestSeq++
  clearUndo()
})
</script>

<style scoped>
.mpl-filter-row {
  display: flex;
  min-height: 44px;
  margin-top: 12px;
  align-items: center;
  gap: 10px;
}
.mpl-filter {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 6px;
}
.mpl-filter button {
  min-height: 44px;
  padding: 0 12px;
  color: var(--m-text-secondary);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: 999px;
  font: inherit;
  font-size: 12.5px;
  transition: color var(--m-motion), background var(--m-motion), border-color var(--m-motion);
}
.mpl-filter button.active {
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border-color: color-mix(in srgb, var(--m-primary) 38%, var(--m-border));
  font-weight: 700;
}
.mpl-total {
  flex: 0 0 auto;
  color: var(--m-text-tertiary);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.mpl-note {
  margin-top: 12px;
  padding: 10px 12px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border-radius: 10px;
  font-size: 11.5px;
  line-height: 1.6;
}
.mpl-note-link { display: inline-flex; min-height: 32px; margin-left: 4px; align-items: center; color: var(--m-primary); font-weight: 700; }

.mpl-loading { margin-top: 16px; }
.mpl-empty { margin-top: 16px; }
.mpl-action-error {
  margin-top: 14px;
  padding: 10px 12px;
  color: #8a2f2a;
  background: color-mix(in srgb, #b0443f 10%, var(--m-surface));
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.55;
}
:global(:root[data-theme='dark']) .mpl-action-error,
:global(:root[data-theme='dark']) .mpl-load-error { color: #f0a09a; }

.mpl-list {
  display: flex;
  margin-top: 14px;
  flex-direction: column;
  gap: 10px;
}
.mpl-card {
  --mpl-ok: #2f7458;
  --mpl-danger: #b0443f;
  --mpl-warn: #9a5a18;
  position: relative;
  overflow: hidden;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  box-shadow: 0 2px 10px rgba(32, 61, 48, .035);
  animation: mpl-rise 380ms cubic-bezier(.16, 1, .3, 1) backwards;
  animation-delay: calc(var(--i, 0) * 28ms);
  transition: transform var(--m-motion), border-color var(--m-motion), box-shadow var(--m-motion);
}
:global(:root[data-theme='dark']) .mpl-card {
  --mpl-ok: #7fd0aa;
  --mpl-danger: #f0a09a;
  --mpl-warn: #f0b06a;
}
.mpl-session {
  display: block;
  padding: 16px;
  text-decoration: none;
}
.mpl-session-head,
.mpl-question-head {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}
.mpl-mark {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 800;
}
.mpl-mark.is-live {
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
}
.mpl-mark.is-mistake {
  color: var(--mpl-danger);
  background: color-mix(in srgb, var(--mpl-danger) 12%, transparent);
}
.mpl-mark.is-fav {
  color: var(--mpl-warn);
  background: var(--m-accent-soft);
}
.mpl-card h2 {
  display: -webkit-box;
  overflow: hidden;
  color: var(--m-text);
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -.01em;
  line-height: 1.45;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.mpl-card h2 + p {
  overflow: hidden;
  margin-top: 3px;
  color: var(--m-text-tertiary);
  font-size: 11.5px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mpl-state {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 10.5px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}
.mpl-state.is-finished {
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
}
.mpl-state.is-ongoing {
  color: var(--mpl-warn);
  background: var(--m-accent-soft);
}
.mpl-state.is-closing {
  color: var(--mpl-warn);
  background: color-mix(in srgb, var(--mpl-warn) 12%, transparent);
}
.mpl-progress-line {
  display: flex;
  margin-top: 14px;
  align-items: center;
  gap: 10px;
}
.mpl-progress {
  flex: 1;
  height: 6px;
  overflow: hidden;
  background: var(--m-surface-soft);
  border-radius: 999px;
}
.mpl-progress i {
  display: block;
  height: 100%;
  background: var(--m-primary);
  border-radius: inherit;
  transition: width 700ms cubic-bezier(.16, 1, .3, 1);
}
.mpl-progress-line > span {
  flex: 0 0 auto;
  color: var(--m-text-tertiary);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.mpl-session-foot {
  display: flex;
  min-height: 30px;
  margin-top: 12px;
  padding-top: 10px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-top: 1px solid var(--m-border);
}
.mpl-session-foot time,
.mpl-session-foot > span {
  overflow: hidden;
  color: var(--m-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mpl-session-foot strong {
  flex: 0 0 auto;
  color: var(--m-primary);
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
}

.mpl-question-main {
  display: block;
  padding: 16px;
  color: inherit;
  text-decoration: none;
}
.mpl-chevron {
  flex: 0 0 auto;
  margin-left: auto;
  color: var(--m-text-tertiary);
  font-size: 20px;
  line-height: 1;
}
.mpl-tags {
  display: flex;
  margin-top: 12px;
  flex-wrap: wrap;
  gap: 6px;
}
.mpl-tag {
  padding: 3px 8px;
  color: var(--m-text-secondary);
  background: var(--m-surface-soft);
  border-radius: 999px;
  font-size: 10.5px;
  line-height: 1.5;
}
.mpl-tag.mpl-type {
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  font-weight: 700;
}
.mpl-tag.is-pass {
  color: var(--mpl-ok);
  background: color-mix(in srgb, var(--mpl-ok) 12%, transparent);
  font-weight: 700;
}
.mpl-tag.is-fail {
  color: var(--mpl-danger);
  background: color-mix(in srgb, var(--mpl-danger) 11%, transparent);
  font-weight: 700;
}
.mpl-tag.is-system {
  color: var(--mpl-danger);
  background: color-mix(in srgb, var(--mpl-danger) 8%, transparent);
}
.mpl-tag.is-warn,
.mpl-tag.is-review {
  color: var(--mpl-warn);
  background: var(--m-accent-soft);
}
.mpl-tag.is-done {
  color: var(--mpl-ok);
  background: color-mix(in srgb, var(--mpl-ok) 10%, transparent);
}
.mpl-question-foot {
  display: flex;
  min-height: 44px;
  padding: 0 16px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  background: color-mix(in srgb, var(--m-surface-soft) 52%, transparent);
  border-top: 1px solid var(--m-border);
}
.mpl-company {
  overflow: hidden;
  color: var(--m-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mpl-question-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
}
.mpl-question-actions a,
.mpl-remove {
  display: inline-flex;
  min-height: 44px;
  padding: 0 8px;
  align-items: center;
  color: var(--m-primary);
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}
.mpl-remove {
  color: var(--m-text-tertiary);
  font-weight: 600;
}
.mpl-remove:disabled { opacity: .45; }

.mpl-more {
  display: flex;
  min-height: 56px;
  margin-top: 16px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
}
.mpl-more-btn {
  min-height: 44px;
  padding: 0 20px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 999px;
  font: inherit;
  font-size: 12.5px;
  font-weight: 700;
  transition: transform var(--m-motion), filter var(--m-motion);
}
.mpl-more-btn:active { transform: scale(.97); }
.mpl-more-btn:disabled { opacity: .55; }
.mpl-end {
  color: var(--m-text-tertiary);
  font-size: 11.5px;
}
.mpl-load-error {
  color: #8a2f2a;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
.mpl-load-error button {
  min-height: 32px;
  padding: 0 6px;
  color: var(--m-primary);
  background: transparent;
  border: 0;
  font: inherit;
  font-weight: 700;
}

.mpl-toast {
  position: fixed;
  bottom: calc(var(--m-nav-height) + 16px + env(safe-area-inset-bottom));
  left: 50%;
  z-index: 90;
  display: flex;
  width: min(calc(100vw - 40px), 420px);
  min-height: 48px;
  padding: 0 8px 0 16px;
  align-items: center;
  gap: 12px;
  color: #fff;
  background: #1d2b24;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(16, 33, 26, .24);
  transform: translateX(-50%);
}
.mpl-toast span {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
}
.mpl-toast button {
  min-height: 44px;
  padding: 0 12px;
  color: #a7e3c4;
  background: transparent;
  border: 0;
  font: inherit;
  font-size: 12.5px;
  font-weight: 700;
}
.mpl-toast-enter-active,
.mpl-toast-leave-active {
  transition: opacity var(--m-motion), transform var(--m-motion);
}
.mpl-toast-enter-from,
.mpl-toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 12px);
}

.mpl-filter button:focus-visible,
.mpl-question-main:focus-visible,
.mpl-session:focus-visible,
.mpl-question-actions a:focus-visible,
.mpl-remove:focus-visible,
.mpl-more-btn:focus-visible,
.mpl-load-error button:focus-visible,
.mpl-toast button:focus-visible {
  outline: 2px solid var(--m-primary);
  outline-offset: 2px;
}

@media (hover: hover) {
  .mpl-session:hover,
  .mpl-question:hover {
    border-color: color-mix(in srgb, var(--m-primary) 34%, var(--m-border));
    box-shadow: 0 8px 22px rgba(32, 61, 48, .07);
    transform: translateY(-2px);
  }
  .mpl-question-actions a:hover { color: var(--m-primary-dark); }
  .mpl-remove:hover { color: var(--mpl-danger); }
}
.mpl-card:active { transform: scale(.995); }

@keyframes mpl-rise {
  from { opacity: 0; transform: translateY(9px); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 374px) {
  .mpl-session { padding: 14px; }
  .mpl-question-main { padding: 14px; }
  .mpl-question-foot { padding-inline: 14px; }
  .mpl-filter button { padding-inline: 10px; }
  .mpl-company { max-width: 84px; }
}

@media (prefers-reduced-motion: reduce) {
  .mpl-card { animation: none; }
  .mpl-progress i,
  .mpl-toast-enter-active,
  .mpl-toast-leave-active { transition: none; }
}
</style>
