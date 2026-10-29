<template>
  <component :is="isMobile ? MobileShell : AppLayout" :title="title" class="library-shell">
    <main class="personal-practice">
      <router-link class="return-link" to="/learning">← 返回专项刷题</router-link>
      <header class="library-heading"><div><h1 v-if="!isMobile">{{ title }}</h1><p>{{ descriptions[kind] }}</p></div><router-link class="create-link" to="/learning">创建新的专项题单 →</router-link></header>
      <nav class="tabs" aria-label="个人练习">
        <router-link v-for="(label,key) in sections" :key="key" :to="`/learning/library/${key}`" :aria-current="kind === key ? 'page' : undefined">{{ label }}</router-link>
      </nav>
      <p v-if="kind === 'mistakes'" class="note">按最新正式提交显示待复习题；历史普通题的关键词评价仅供参考。普通自评题不会自动判错。</p>
      <div class="library-toolbar"><label v-if="kind === 'recent'" class="filter">练习状态 <select v-model="status"><option value="ALL">全部状态</option><option value="ONGOING">进行中</option><option value="FINISHED">已结束</option></select></label><span class="total-count">{{ loading ? '正在读取记录…' : `共 ${total} ${kind === 'recent' ? '个题单' : '道题'}` }}</span></div>
      <div class="record-list" :aria-busy="loading">
        <div v-if="loading" class="list-state" role="status"><h2>正在加载{{ title }}…</h2><p>正在整理你的练习记录。</p></div>
        <div v-else-if="error" class="list-state" role="alert"><h2>记录暂时无法加载</h2><p>{{ error }}</p><button @click="load">重新加载</button></div>
        <div v-else-if="!items.length" class="list-state"><h2>{{ emptyText }}</h2><p>{{ kind === 'favorites' ? '收藏想要巩固的题目，之后可以在这里继续练习。' : kind === 'mistakes' ? '未通过或加入待复习的题目会集中展示在这里。' : '可以调整练习状态，或创建新的专项题单。' }}</p><router-link to="/learning">去创建专项练习 →</router-link></div>
        <article v-else v-for="(item,n) in items" :key="item.id" class="record">
          <span class="record-number">{{ String((page - 1) * size + n + 1).padStart(2,'0') }}</span>
          <div class="record-copy">
            <h2>{{ kind === 'recent' ? `${item.role?.name || '历史岗位'} · ${item.topic || '专项练习'}` : item.title }}</h2>
            <div class="record-meta">
              <template v-if="kind === 'recent'"><span class="status-mark" :class="item.status === 'FINISHED' ? 'finished' : 'ongoing'">{{ item.status === 'FINISHED' ? '已结束' : item.status === 'CLOSING' ? '结算处理中' : '进行中' }}</span><span>{{ item.company || '不限公司' }}</span><span>已提交 {{ item.completed }} / {{ item.questionCount }}</span><time v-if="item.updatedAt">{{ formatPracticeDate(item.updatedAt) }}</time></template>
              <template v-else><span>{{ practiceTypeLabel(item.questionType) }}</span><span v-if="item.submissionStatus" class="status-mark" :class="outcomeClass(item)">{{ judgeStatusLabel(item.submissionStatus) }}</span><span v-if="item.reviewState === 'PENDING' || item.reviewState === 'RESOLVED'" class="review-mark">{{ item.reviewState === 'PENDING' ? '待复习' : '已标记复习完成' }}</span><span v-if="kind === 'favorites'" class="favorite-mark">已收藏</span></template>
            </div>
            <p v-if="kind !== 'recent'" class="record-context">{{ item.jobName || '历史岗位' }} · {{ item.topic || '专项练习' }} · {{ item.company || '不限公司' }}</p>
            <progress v-else :value="item.completed || 0" :max="item.questionCount || 1" :aria-label="`已提交 ${item.completed} / ${item.questionCount}`"></progress>
          </div>
          <div class="actions">
            <router-link :to="questionLink(item)">{{ kind === 'recent' ? (item.status === 'FINISHED' || item.status === 'CLOSING' ? '查看报告' : '继续练习') : '查看原题与作答' }} →</router-link>
            <button v-if="kind === 'favorites'" :disabled="removing !== null" @click="removeFavorite(item)">{{ removing === item.id ? '正在取消…' : '☆ 取消收藏' }}</button>
          </div>
        </article>
      </div>
      <nav v-if="!loading && !error && items.length" class="pagination" aria-label="分页"><button :disabled="page === 1" @click="page--">上一页</button><span>第 {{ page }} / {{ Math.max(1,Math.ceil(total / size)) }} 页</span><button :disabled="page * size >= total" @click="page++">下一页</button></nav>
      <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>
    </main>
  </component>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { practiceTypeLabel } from '../utils/practiceTypes'
import { judgeStatusLabel } from '../utils/practiceJudging'
import { formatPracticeDate } from '../utils/practiceReport'
import AppLayout from '../components/layout/AppLayout.vue'
import MobileShell from '../mobile/components/MobileShell.vue'
import { useIsMobile } from '../mobile/composables/useIsMobile'
import '../mobile/styles/mobile.css'
import { getPracticeSessions, getPracticeLibrary, removePracticeFavorite } from '../services/learningResources'

const route = useRoute(), isMobile = useIsMobile()
const sections = { recent:'最近练习', mistakes:'错题本', favorites:'我的收藏' }
const descriptions = { recent:'回顾练习进度，继续作答或查看结算报告', mistakes:'集中复盘薄弱题目，逐步完成巩固训练', favorites:'收集值得回顾的题目，随时返回原题练习' }
const kind = computed(() => Object.hasOwn(sections,route.params.kind) ? route.params.kind : 'recent')
const title = computed(() => sections[kind.value])
const emptyText = computed(() => ({recent:'暂无符合条件的练习',mistakes:'暂无待复习题目',favorites:'暂无收藏题目'})[kind.value])
const status = ref(['ONGOING','FINISHED'].includes(route.query.status) ? route.query.status : 'ALL')
const page = ref(1), size = 20, items = ref([]), total = ref(0), loading = ref(false), error = ref(''), actionError = ref(''), removing = ref(null)
let requestId = 0, disposed = false
async function load() {
  const id = ++requestId
  loading.value = true; error.value = ''; actionError.value = ''
  try {
    const result = kind.value === 'recent' ? await getPracticeSessions({status:status.value,page:page.value,size}) : await getPracticeLibrary(kind.value,{page:page.value,size})
    if (disposed || id !== requestId) return
    items.value = result.items; total.value = result.total
  } catch (e) { if(!disposed && id === requestId) error.value = e.message || '记录加载失败，请重试。' }
  finally { if(id === requestId) loading.value = false }
}
watch([kind,status], () => { if(page.value !== 1) page.value = 1; else load() })
watch(page,load)
load()
function questionLink(item) { return kind.value === 'recent' ? `/learning/session/${item.id}${item.status === 'FINISHED' || item.status === 'CLOSING' ? '/report' : ''}` : {path:`/learning/session/${item.sessionId}`,query:{questionId:item.id}} }
function outcomeClass(item) { return ['OBJECTIVE_CORRECT','ACCEPTED'].includes(item.submissionStatus) ? 'passed' : ['SYSTEM_ERROR','SERVICE_UNAVAILABLE'].includes(item.submissionStatus) ? 'system' : ['OBJECTIVE_INCORRECT','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED','MEMORY_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED'].includes(item.submissionStatus) ? 'failed' : item.submissionStatus === 'SELF_REVIEW' ? 'self' : '' }
async function removeFavorite(item) {
  if(removing.value !== null || !confirm('确认取消收藏这道题？')) return
  const activeKind = kind.value
  removing.value = item.id; actionError.value = ''
  try { await removePracticeFavorite(item.id); if(disposed || kind.value !== activeKind) return; if(items.value.length === 1 && page.value > 1) page.value--; else await load() }
  catch(e) { if(!disposed && kind.value === activeKind) actionError.value = e.message || '取消失败，请重试。' }
  finally { removing.value = null }
}
onBeforeUnmount(() => { disposed = true; requestId++ })
</script>

<style scoped>
.personal-practice{max-width:1248px;margin:0 auto;padding:24px 24px 64px;color:#172d28;font-size:14px}
.personal-practice :is(h1,h2,p){margin:0}.personal-practice a{color:var(--accent-700);text-decoration:none}.return-link{display:inline-flex;align-items:center;min-height:32px}.library-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:4px 0 22px}.library-heading h1{font-size:28px;line-height:1.4;margin-bottom:8px}.library-heading p{font-size:13px;color:#62716c;line-height:1.7}.create-link{display:inline-flex;align-items:center;min-height:44px;flex-shrink:0}
.tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px}.tabs a{display:inline-flex;align-items:center;min-height:44px;padding:8px 16px;background:var(--surface-elevated);border:1px solid #dce5e0;border-radius:8px;color:#233b31;font-size:14px;transition:background 150ms,border-color 150ms}.tabs a:hover{background:var(--surface-soft);border-color:#a9d8c2}.tabs a[aria-current=page]{background:var(--accent-50);border-color:#a3d8bd;color:var(--accent-800)}
.library-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:44px;margin-bottom:14px}.filter{display:flex;align-items:center;gap:12px;font-size:13px;color:#62716c}.filter select{min-width:150px}.total-count{font-size:13px;color:#62716c;margin-left:auto;white-space:nowrap}.note{padding:10px 16px;background:var(--surface-soft);color:#325d4b;font-size:13px;line-height:1.7;border-radius:8px;margin-bottom:16px!important}
.record-list{border:1px solid #e3ebe6;border-radius:10px;overflow:hidden;background:var(--surface-elevated)}.record{display:grid;grid-template-columns:34px minmax(0,1fr) auto;align-items:center;gap:16px;padding:14px 18px;min-height:100px;border-bottom:1px solid #e8eeeb}.record:last-child{border-bottom:0}.record:hover{background:var(--surface-soft)}.record-number{display:grid;place-items:center;align-self:start;margin-top:4px;min-width:30px;min-height:28px;border-radius:5px;background:var(--surface-soft);color:#52615b;font-size:13px;font-variant-numeric:tabular-nums}.record-copy{min-width:0}.record h2{font-size:15px;line-height:1.5;font-weight:600;margin-bottom:6px;overflow-wrap:anywhere}.record-meta{display:flex;align-items:center;gap:8px 12px;flex-wrap:wrap;font-size:12px;color:#62716c;line-height:1.6}.status-mark,.review-mark,.favorite-mark{display:inline-flex;align-items:center;padding:3px 6px;border-radius:4px;background:var(--surface-soft);color:#53625c;font-size:12px;line-height:1.4}.status-mark.finished,.status-mark.passed,.favorite-mark{background:var(--accent-50);color:var(--accent-700)}.status-mark.ongoing{background:var(--surface-soft);color:#526d60}.status-mark.failed,.status-mark.system{background:var(--surface-soft);color:#b43b49}.status-mark.self{background:var(--surface-soft);color:#825819}.review-mark{background:var(--surface-soft);color:#526d60}.record-context{font-size:12px;line-height:1.7;color:#62716c;margin-top:6px!important;overflow-wrap:anywhere}.record progress{display:block;width:240px;max-width:100%;height:6px;margin-top:10px;accent-color:var(--accent-500);border:0;border-radius:6px;overflow:hidden;background:var(--surface-soft)}.record progress::-webkit-progress-bar{background:var(--surface-soft);border-radius:6px}.record progress::-webkit-progress-value{background:var(--accent-500);border-radius:6px}.record progress::-moz-progress-bar{background:var(--accent-500);border-radius:6px}
.actions{display:flex;align-items:center;gap:18px;flex-wrap:wrap}.actions a{display:inline-flex;align-items:center;min-height:44px;font-size:13px}.personal-practice .actions button{border:0;background:transparent;padding:0;font-size:12px;color:#62716c;white-space:nowrap}.actions a:hover,.personal-practice .actions button:hover{color:var(--accent-700);text-decoration:underline}.personal-practice button{min-height:44px;padding:8px 16px;border:1px solid #dce5e0;border-radius:8px;background:var(--surface-elevated);color:#233b31;font:inherit;cursor:pointer;transition:background 150ms}.personal-practice button:hover{background:var(--surface-soft)}.personal-practice button:disabled{opacity:.5;cursor:not-allowed}.personal-practice :is(a,button,select):focus-visible{outline:2px solid var(--accent-600);outline-offset:3px}
.pagination{display:flex;justify-content:center;align-items:center;gap:16px;margin-top:20px;color:#62716c;font-size:13px}.list-state{padding:32px 24px;line-height:1.8}.list-state h2{font-size:18px}.list-state p{color:#62716c;margin:8px 0 16px}.list-state a{display:inline-flex;align-items:center;min-height:44px}.action-error{padding:12px 16px;background:var(--surface-soft);color:#a42f3d;border-radius:8px;line-height:1.7;margin-top:16px!important}
@media(max-width:1100px){.record{grid-template-columns:30px minmax(0,1fr);gap:8px 14px}.record-number{grid-row:1/3}.actions{grid-column:2;gap:16px}.record{padding-block:12px}}
@media(max-width:767px){.library-shell :deep(.mobile-pagebar){display:flex;align-items:center;justify-content:space-between;min-height:90px}.library-shell :deep(.mobile-teaching-links){display:flex;gap:16px;padding:0 20px 16px}.library-shell :deep(.mobile-teaching-links a){display:inline-flex;align-items:center;min-height:44px}.personal-practice{padding:0 0 30px}.return-link{min-height:44px;margin-bottom:8px}.library-heading{align-items:flex-start;flex-direction:column;gap:4px;margin:0 0 16px}.library-toolbar{gap:10px}.filter{gap:8px}.filter select{min-width:130px;max-width:160px}.record{padding:14px 12px;gap:8px 10px}.actions{gap:8px 16px}.pagination{gap:10px;font-size:12px}.pagination button{padding:8px 12px}.list-state{padding:24px 16px}}
@media(prefers-reduced-motion:reduce){.personal-practice button,.tabs a{transition:none}}
</style>
