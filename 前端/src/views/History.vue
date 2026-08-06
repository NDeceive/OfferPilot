<template>
  <AppLayout>
    <div class="history-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">面试记录</h1>
        <p class="page-desc">查看你的所有面试历史与能力报告</p>
      </div>
      <div class="header-actions">
        <div class="filter-tabs">
          <button
            v-for="tab in statusTabs"
            :key="tab"
            :class="['filter-tab', { active: activeStatus === tab }]"
            @click="activeStatus = tab"
          >{{ tab }}</button>
        </div>
        <select v-model="filterJob" class="filter-select">
          <option value="all">全部岗位</option>
          <option v-for="category in jobCategories" :key="category" :value="category">{{ category }}</option>
        </select>
      </div>
    </div>

    <!-- Records List -->
    <div class="records-list">
      <div
        v-for="(record, i) in filteredRecords"
        :key="record.id"
        class="record-card"
        :style="{ '--reveal-delay': i * 60 + 'ms' }"
      >
        <div class="record-main" @click="viewReport(record)">
          <JobLogo :icon-key="record.iconKey" :tone="record.themeKey" />
          <div class="record-info">
            <div class="record-top">
              <span class="record-position">{{ record.position }}</span>
              <span :class="['record-status', record.status]">
                {{ statusMap[record.status] }}
              </span>
            </div>
            <div class="record-meta">
              <span class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                {{ record.date }}
              </span>
              <span v-if="record.duration" class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
                {{ record.duration }}
              </span>
            </div>
          </div>
          <div class="record-score" v-if="record.score">
            <span class="score-value" :class="getScoreClass(record.score)">{{ record.score }}</span>
            <span class="score-unit">分</span>
          </div>
          <span class="record-action">
            查看详情
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7"/>
            </svg>
          </span>
        </div>
        <!-- Delete button - only for records with score -->
        <button
          class="delete-btn"
          title="删除此记录"
          @click.stop="confirmDelete(record)"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="filteredRecords.length === 0" class="empty-state">
      <div class="empty-icon">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--neutral-300)" stroke-width="1.5">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
      </div>
      <p class="empty-text">暂无面试记录</p>
      <p class="empty-hint">开始你的第一场模拟面试吧</p>
    </div>

    <!-- Pagination -->
    <div class="pagination" v-if="filteredRecords.length > 0">
      <span class="page-info">共 {{ records.length }} 条记录</span>
      <div class="page-buttons">
        <button class="page-btn" :disabled="currentPage === 1" @click="currentPage--">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <button v-for="p in totalPages" :key="p" :class="['page-btn', { active: currentPage === p }]" @click="currentPage = p">{{ p }}</button>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="currentPage++">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <Transition name="modal">
      <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
        <div class="modal-card">
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-body">确定要删除「{{ deleteTarget.position }}」的面试记录吗？此操作不可撤销。</p>
          <div class="modal-actions">
            <button class="modal-btn modal-btn-cancel" @click="deleteTarget = null">取消</button>
            <button class="modal-btn modal-btn-danger" @click="doDelete">确认删除</button>
          </div>
        </div>
      </div>
    </Transition>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import JobLogo from '../components/jobs/JobLogo.vue'
import { deleteInterview, getInterviewRecords, getJobList } from '../api'
import { getJobPresentation } from '../utils/jobPresentation'

const router = useRouter()
const activeStatus = ref('全部')
const filterJob = ref('all')
const currentPage = ref(1)
const statusTabs = ['全部', '已完成', '进行中', '已中断']
const statusMap = { completed: '已完成', in_progress: '进行中', interrupted: '已中断' }
const statusApiMap = { FINISHED: 'completed', ONGOING: 'in_progress', ABORTED: 'interrupted' }

const records = ref([])
const loading = ref(true)
const loadError = ref(false)
const deleteTarget = ref(null)

function formatDuration(seconds) {
  if (!seconds) return ''
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

async function fetchRecords() {
  loading.value = true
  loadError.value = false
  try {
    const [data, jobs] = await Promise.all([getInterviewRecords(), getJobList()])
    const jobsById = new Map((jobs || []).map(job => [String(job.id), job]))
    records.value = (data || []).map(item => ({
      ...getJobPresentation(jobsById.get(String(item.jobId))),
      id: item.sessionId,
      reportId: item.reportId,
      date: formatDate(item.startTime),
      position: item.jobName || '未知岗位',
      category: item.category || '其他岗位',
      directionCode: item.directionCode || '',
      score: item.totalScore,
      duration: formatDuration(item.actualDurationSeconds || item.durationSeconds),
      status: statusApiMap[item.status] || 'completed',
    }))
  } catch (e) {
    console.error('Failed to load interview records:', e)
    loadError.value = true
    records.value = []
  } finally {
    loading.value = false
  }
}

const filteredRecords = computed(() => {
  return records.value.filter(r => {
    const matchStatus = activeStatus.value === '全部' ||
      (activeStatus.value === '已完成' && r.status === 'completed') ||
      (activeStatus.value === '进行中' && r.status === 'in_progress') ||
      (activeStatus.value === '已中断' && r.status === 'interrupted')
    const matchJob = filterJob.value === 'all' || r.category === filterJob.value
    return matchStatus && matchJob
  })
})

const jobCategories = computed(() => [...new Set(records.value.map(record => record.category))])

const totalPages = computed(() => Math.max(1, Math.ceil(filteredRecords.value.length / 10)))

function getScoreClass(score) {
  if (score >= 85) return 'score-high'
  if (score >= 70) return 'score-mid'
  return 'score-low'
}

function viewReport(record) {
  if (record.reportId) {
    router.push(`/history/${record.reportId}`)
  }
}

function confirmDelete(record) {
  deleteTarget.value = record
}

async function doDelete() {
  const record = deleteTarget.value
  if (!record) return
  try {
    await deleteInterview(record.id)
    records.value = records.value.filter(r => r.id !== record.id)
  } catch (e) {
    console.error('Delete failed:', e)
  } finally {
    deleteTarget.value = null
  }
}

onMounted(async () => {
  await fetchRecords()
})
</script>

<style scoped>
.history-page {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) var(--space-6) var(--space-16);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-6);
  animation: fade-in-up 0.5s var(--ease-out-expo);
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--neutral-900);
  letter-spacing: -0.02em;
}

.page-desc {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-top: var(--space-1);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.filter-tabs {
  display: flex;
  gap: 2px;
  background: var(--neutral-100);
  border-radius: var(--radius-md);
  padding: 3px;
}

.filter-tab {
  padding: var(--space-2) var(--space-3);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--neutral-500);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-expo);
}

.filter-tab:hover {
  color: var(--neutral-700);
}

.filter-tab.active {
  background: var(--surface-elevated);
  color: var(--accent-600);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

.filter-select {
  padding: var(--space-2) var(--space-4);
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  outline: none;
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out-expo);
}

.filter-select:focus {
  border-color: var(--accent-500);
}

/* Records List */
.records-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.record-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  position: relative;
  padding: var(--space-4) var(--space-5);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  text-decoration: none;
  color: inherit;
  transition: box-shadow var(--duration-normal) var(--ease-out-expo),
              transform var(--duration-normal) var(--ease-out-expo),
              border-color var(--duration-normal) var(--ease-out-expo);
  animation: reveal-card 0.5s var(--ease-out-expo) both;
  animation-delay: var(--reveal-delay, 0ms);
}

@keyframes reveal-card {
  from { opacity: 0; transform: translateY(20px); }
  to   { opacity: 1; transform: translateY(0); }
}

.record-main {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.delete-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--neutral-400);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast);
}

.delete-btn:hover {
  color: var(--color-error);
  background: var(--color-error-bg);
  border-color: rgba(239, 68, 68, 0.2);
}

.record-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
  border-color: var(--accent-200);
}

.record-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-lg);
  font-weight: 700;
  flex-shrink: 0;
  background: var(--accent-50);
  color: var(--accent-600);
}

.record-icon.icon-completed {
  background: var(--accent-50);
  color: var(--accent-600);
}

.record-icon.icon-in_progress {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.record-icon.icon-interrupted {
  background: var(--neutral-100);
  color: var(--neutral-500);
}

.record-info {
  flex: 1;
  min-width: 0;
}

.record-top {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-1);
}

.record-position {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
}

.record-status {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  letter-spacing: 0.02em;
}

.record-status.completed {
  background: rgba(16, 185, 129, 0.1);
  color: var(--accent-600);
}

.record-status.in_progress {
  background: rgba(245, 158, 11, 0.1);
  color: #b45309;
}

.record-status.interrupted {
  background: var(--neutral-100);
  color: var(--neutral-500);
}

.record-meta {
  display: flex;
  gap: var(--space-4);
  font-size: var(--text-sm);
  color: var(--neutral-500);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-item svg {
  color: var(--neutral-400);
  flex-shrink: 0;
}

.record-score {
  display: flex;
  align-items: baseline;
  gap: 2px;
  flex-shrink: 0;
}

.score-value {
  font-family: var(--font-mono);
  font-size: var(--text-2xl);
  font-weight: 700;
}

.score-unit {
  font-size: var(--text-sm);
  color: var(--neutral-500);
}

.score-high { color: var(--accent-500); }
.score-mid { color: #d97706; }
.score-low { color: #ef4444; }

.record-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-600);
  flex-shrink: 0;
  transition: all var(--duration-fast) var(--ease-out-expo);
}

.record-card:hover .record-action {
  border-color: var(--accent-400);
  color: var(--accent-600);
  background: var(--accent-50);
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-16) var(--space-4);
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-lg);
  background: var(--neutral-50);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-2);
}

.empty-text {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-700);
}

.empty-hint {
  font-size: var(--text-sm);
  color: var(--neutral-400);
}

/* Pagination */
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--neutral-100);
}

.page-info {
  font-size: var(--text-sm);
  color: var(--neutral-500);
}

.page-buttons {
  display: flex;
  gap: var(--space-1);
}

.page-btn {
  min-width: 36px;
  height: 36px;
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-sm);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-expo);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--accent-400);
  color: var(--accent-600);
}

.page-btn.active {
  border-color: var(--accent-500);
  background: var(--accent-50);
  color: var(--accent-600);
  font-weight: 600;
}

.page-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* Responsive */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: var(--space-4);
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .record-card {
    flex-wrap: wrap;
    gap: var(--space-3);
    padding: var(--space-4);
  }

  .record-score {
    order: -1;
  }

  .record-meta {
    flex-wrap: wrap;
    gap: var(--space-3);
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .page-header {
    animation: none;
  }

  .record-card {
    animation: none;
    transition: none;
  }

  .record-card:hover {
    transform: none;
  }
}

/* === Delete Modal === */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-card {
  background: var(--surface-elevated);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  max-width: 400px;
  width: 90%;
  box-shadow: var(--shadow-xl);
}

.modal-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-3);
}

.modal-body {
  font-size: var(--text-sm);
  color: var(--neutral-600);
  line-height: 1.6;
  margin-bottom: var(--space-6);
}

.modal-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
}

.modal-btn {
  padding: var(--space-2) var(--space-5);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all var(--duration-fast);
}

.modal-btn-cancel {
  background: var(--neutral-100);
  color: var(--neutral-600);
}
.modal-btn-cancel:hover { background: var(--neutral-200); }

.modal-btn-danger {
  background: var(--color-error);
  color: white;
}
.modal-btn-danger:hover { background: #dc2626; }

.modal-enter-active,
.modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from,
.modal-leave-to { opacity: 0; }
</style>
