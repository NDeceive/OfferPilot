<template>
  <MobileShell title="面试记录" subtitle="回看每一次训练与成长">
    <label class="mobile-record-source">训练来源<select v-model="active" aria-label="训练记录来源"><option value="all">全部训练来源</option><option value="SELF">自主训练</option><option value="TEACHING">教学任务</option></select></label>

    <MobileState v-if="loading" kind="loading" title="正在加载面试记录" />
    <MobileState v-else-if="error" kind="error" title="面试记录加载失败" description="请检查网络后重试。" action="重新加载" @action="load" />
    <MobileState v-else-if="!filtered.length" :title="records.length?'暂无这类训练记录':'还没有训练记录'" :description="records.length?'切换来源查看其他记录。':'完成训练后，你的记录会出现在这里。'" :action="records.length?'查看全部记录':'开始第一场面试'" @action="records.length?active='all':$router.push('/jobs')" />
    <section v-else class="mobile-record-list">
      <article v-for="record in filtered" :key="record.sessionId" class="mobile-record-card">
        <div class="mobile-row">
          <span class="mobile-square-icon">职</span>
          <div class="mobile-grow"><h2>{{ record.jobName || '面试训练' }}</h2><p>{{ formatDate(record.startTime) }} · {{ formatDuration(record.actualDurationSeconds || record.durationSeconds) }}</p></div>
          <strong v-if="record.totalScore !== null && record.totalScore !== undefined" class="mobile-record-score">{{ Math.round(record.totalScore) }}<small>基础分 / 100</small></strong>
        </div>
        <p class="mobile-record-origin">{{record.trainingSource==='TEACHING'?'教学任务 · '+record.taskTitle:'自主训练'}}</p>
        <div class="mobile-record-footer"><span>{{ statusLabel(record.status) }}</span><button type="button" :disabled="!record.reportId&&!record.assignmentId" @click="view(record)">{{ record.reportId ? '查看详情 →' : record.assignmentId?'查看任务 →':record.status==='ONGOING'?'训练进行中':'报告尚未就绪' }}</button></div>
      </article>
    </section>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getInterviewRecords } from '../../api'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'

const router = useRouter()
const active = ref('all')
const records = ref([])
const loading = ref(true)
const error = ref(false)
const filtered = computed(() => records.value.filter(r=>active.value==='all'||r.trainingSource===active.value))

async function load() {
  loading.value = true
  error.value = false
  try { records.value = await getInterviewRecords() || [] }
  catch { error.value = true }
  finally { loading.value = false }
}
function view(record) { if (record.reportId) router.push({path:`/history/${record.reportId}`,query:record.assignmentId?{assignmentId:record.assignmentId}:{}});else if(record.assignmentId)router.push('/my/tasks/'+record.assignmentId) }
function formatDate(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value)).replace('/', '月') + '日' : '日期待记录' }
function formatDuration(seconds) { return seconds ? `${Math.max(1, Math.round(seconds / 60))} 分钟` : '时长待记录' }
function statusLabel(status) { return ({ FINISHED: '训练已结束', ONGOING: '进行中', ABORTED: '已中断' })[status] || status }

onMounted(load)
</script>
<style scoped>.mobile-record-source{display:flex;gap:16px;align-items:center;font-size:14px;margin-bottom:24px}.mobile-record-source select{min-width:0;flex:1;min-height:44px;border:1px solid #d4d4d8;border-radius:8px;padding:10px;background:white;font:inherit;color:#27272a}.mobile-record-origin{font-size:13px;line-height:1.8;margin-top:16px;color:#52525b}.mobile-record-score small{display:block;font-size:11px;font-weight:400;margin-top:4px;color:#52525b}.mobile-record-source select:focus-visible{outline:2px solid #047857;outline-offset:3px}</style>
