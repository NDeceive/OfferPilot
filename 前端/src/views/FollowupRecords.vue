<template>
  <div class="followup-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><DataAnalysis /></el-icon>
        实验分析
      </span>
      <h2>追问记录与统计</h2>
      <p>查看面试中的追问明细，对比 AI 智能追问与规则兜底追问的占比，供演示与论文写作使用。</p>
    </div>

    <!-- 统计卡片 -->
    <div v-loading="statsLoading" class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">总追问数</span>
        <strong class="stat-value">{{ stats.totalCount }}</strong>
      </div>
      <div class="stat-card ai">
        <span class="stat-label">AI 追问数</span>
        <strong class="stat-value">{{ stats.aiCount }}</strong>
      </div>
      <div class="stat-card rule">
        <span class="stat-label">规则追问数</span>
        <strong class="stat-value">{{ stats.ruleCount }}</strong>
      </div>
      <div class="stat-card ai">
        <span class="stat-label">AI 占比</span>
        <strong class="stat-value">{{ percent(stats.aiRatio) }}</strong>
      </div>
      <div class="stat-card rule">
        <span class="stat-label">规则占比</span>
        <strong class="stat-value">{{ percent(stats.ruleRatio) }}</strong>
      </div>
    </div>

    <!-- 过滤器 -->
    <div class="filter-bar">
      <div class="filter-item">
        <span class="filter-label">追问来源</span>
        <el-select v-model="filters.source" placeholder="全部" clearable style="width: 130px">
          <el-option label="全部" value="" />
          <el-option label="AI" value="AI" />
          <el-option label="RULE" value="RULE" />
        </el-select>
      </div>
      <div class="filter-item">
        <span class="filter-label">会话ID</span>
        <el-input
          v-model="filters.sessionId"
          placeholder="sessionId"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <span class="filter-label">岗位ID</span>
        <el-input
          v-model="filters.jobId"
          placeholder="jobId"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && !records.length" description="暂无追问记录" />

        <el-table v-else :data="records" stripe border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="sessionId" label="会话ID" width="90" />
          <el-table-column prop="jobId" label="岗位ID" width="80" />
          <el-table-column prop="questionId" label="题目ID" width="80" />
          <el-table-column prop="position" label="岗位" min-width="120" show-overflow-tooltip />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag :type="row.source === 'AI' ? 'success' : 'info'" effect="light" size="small">
                {{ row.source || '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="triggerReason"
            label="触发原因"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="原始问题" min-width="200">
            <template #default="{ row }">
              <span class="cell-ellipsis clickable" @click="showDetail('原始问题', row.originalQuestion)">
                {{ row.originalQuestion || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="考生回答" min-width="200">
            <template #default="{ row }">
              <span class="cell-ellipsis clickable" @click="showDetail('考生回答', row.userAnswer)">
                {{ row.userAnswer || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="追问问题" min-width="200">
            <template #default="{ row }">
              <span class="cell-ellipsis clickable" @click="showDetail('追问问题', row.followUpQuestion)">
                {{ row.followUpQuestion || '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="fetchRecords"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 长文本详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="560px">
      <p class="detail-text">{{ detailContent || '—' }}</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import { getFollowupRecords, getFollowupRecordStats } from '@/api/followupRecord'

const stats = reactive({ totalCount: 0, aiCount: 0, ruleCount: 0, aiRatio: 0, ruleRatio: 0 })
const statsLoading = ref(false)

const records = ref([])
const loading = ref(false)
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

const filters = reactive({ source: '', sessionId: '', jobId: '' })

const detailVisible = ref(false)
const detailTitle = ref('')
const detailContent = ref('')

// 占比为 0~1 的小数，展示为百分比
const percent = (v) => `${((Number(v) || 0) * 100).toFixed(2)}%`

const pad = (n) => String(n).padStart(2, '0')
const formatTime = (t) => {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const showDetail = (title, content) => {
  detailTitle.value = title
  detailContent.value = content
  detailVisible.value = true
}

const fetchStats = async () => {
  statsLoading.value = true
  try {
    const data = await getFollowupRecordStats()
    Object.assign(stats, data || {})
  } catch {
    // 错误已由 request 拦截器提示，这里保持页面可用
  } finally {
    statsLoading.value = false
  }
}

const fetchRecords = async () => {
  loading.value = true
  try {
    const params = { pageNo: pageNo.value, pageSize: pageSize.value }
    if (filters.source) params.source = filters.source
    if (filters.sessionId) params.sessionId = filters.sessionId
    if (filters.jobId) params.jobId = filters.jobId
    const data = await getFollowupRecords(params)
    records.value = data?.list || []
    total.value = Number(data?.total) || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNo.value = 1
  fetchRecords()
}

const handleReset = () => {
  filters.source = ''
  filters.sessionId = ''
  filters.jobId = ''
  pageNo.value = 1
  fetchRecords()
}

const handleSizeChange = () => {
  pageNo.value = 1
  fetchRecords()
}

onMounted(() => {
  fetchStats()
  fetchRecords()
})
</script>

<style scoped>
.followup-page {
  padding-top: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.stat-card.ai {
  background: linear-gradient(180deg, #f1f8f3 0%, #ffffff 100%);
  border-color: #cdeacf;
}

.stat-card.rule {
  background: linear-gradient(180deg, #f4f6fb 0%, #ffffff 100%);
  border-color: #d6deec;
}

.stat-label {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 600;
}

.stat-value {
  color: var(--text);
  font-size: 26px;
  font-weight: 800;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 18px;
  padding: 18px;
  margin-bottom: 20px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-label {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.table-card {
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.table-wrap {
  min-height: 220px;
}

.cell-ellipsis {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.clickable {
  color: var(--primary-dark);
  cursor: pointer;
}

.clickable:hover {
  text-decoration: underline;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.detail-text {
  margin: 0;
  color: var(--text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    margin-left: 0;
  }
}
</style>
