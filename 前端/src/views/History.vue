<template>
  <div class="history-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><Tickets /></el-icon>
        训练档案
      </span>
      <h2>面试记录</h2>
      <p>查看你的历史模拟面试。报告详情将在后续版本开放。</p>
    </div>

    <div class="history-card">
      <div v-loading="loading" class="table-wrap">
        <el-empty v-if="!loading && !records.length" description="暂无面试记录">
          <el-button type="primary" @click="$router.push('/jobs')">开启第一次面试</el-button>
        </el-empty>

        <el-table v-else :data="records" stripe>
          <el-table-column label="目标岗位" min-width="160">
            <template #default="{ row }">
              <span class="job-name">{{ row.jobName || '未知岗位' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="难度" width="90">
            <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
          </el-table-column>

          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
          </el-table-column>

          <el-table-column label="用时 / 结束时间" min-width="170">
            <template #default="{ row }">{{ durationText(row) }}</template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" effect="light" size="small">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="得分" width="100">
            <template #default="{ row }">
              <span v-if="row.totalScore != null" class="score">{{ row.totalScore }}</span>
              <span v-else class="score-muted">待生成</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="140" align="right">
            <template #default="{ row }">
              <el-button
                v-if="row.reportId"
                type="primary"
                size="small"
                @click="viewReport(row.reportId)"
              >
                查看报告
              </el-button>
              <el-button v-else size="small" disabled>报告生成中</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Tickets } from '@element-plus/icons-vue'
import { getInterviewRecords } from '@/api'

const router = useRouter()
const records = ref([])
const loading = ref(false)

const viewReport = (reportId) => {
  router.push({ path: '/report', query: { reportId } })
}

const difficultyLabel = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || '中等')

const statusLabel = (s) => ({ ONGOING: '进行中', FINISHED: '已完成', ABORTED: '已中断' }[s] || s || '未知')
const statusType = (s) => ({ ONGOING: 'warning', FINISHED: 'success', ABORTED: 'info' }[s] || 'info')

const pad = (n) => String(n).padStart(2, '0')
const formatTime = (t) => {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 有结束时间则展示用时（mm:ss / hh:mm:ss），否则展示结束时间占位
const durationText = (row) => {
  if (!row.startTime || !row.endTime) return formatTime(row.endTime)
  const ms = new Date(row.endTime) - new Date(row.startTime)
  if (Number.isNaN(ms) || ms < 0) return formatTime(row.endTime)
  const total = Math.floor(ms / 1000)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

onMounted(async () => {
  loading.value = true
  try {
    records.value = await getInterviewRecords()
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.history-page {
  padding-top: 12px;
}

.history-card {
  margin-top: 20px;
  padding: 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.table-wrap {
  min-height: 220px;
}

.job-name {
  color: var(--text);
  font-weight: 700;
}

.score {
  color: var(--primary-dark);
  font-size: 16px;
  font-weight: 800;
}

.score-muted {
  color: var(--text-muted);
  font-size: 13px;
}
</style>
