<template>
  <AppLayout>
    <div class="detail-page">
    <router-link to="/history" class="back-link">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M19 12H5M12 19l-7-7 7-7"/>
      </svg>
      返回面试记录
    </router-link>

    <div v-if="loading" class="loading-state">
      <p>加载中...</p>
    </div>
    <template v-else>
    <!-- Score Hero -->
    <div class="score-hero">
      <div class="score-hero-left">
        <span class="score-label">综合评分</span>
        <div class="score-number">
          <span class="score-value">{{ totalScore }}</span>
          <span class="score-total">/100</span>
        </div>
        <span class="score-rank">{{ getScoreRank(totalScore) }}</span>
      </div>
      <div class="score-meta-grid">
        <div class="meta-item">
          <span class="meta-label">岗位</span>
          <span class="meta-value">{{ jobName }}</span>
        </div>
      </div>
    </div>

    <!-- Radar Chart -->
    <div class="analysis-grid">
      <div class="card radar-card">
        <h2 class="card-title">能力雷达图</h2>
        <RadarChart :labels="radarLabels" :values="radarValues" />
      </div>

      <div class="sw-column">
        <div class="card">
          <h3 class="card-title sw-title">
            <span class="sw-dot sw-dot-success"></span>
            表现优势
          </h3>
          <div class="sw-list">
            <div v-for="s in strengths" :key="s.name" class="sw-item">
              <span class="sw-name">{{ s.name }}</span>
              <div class="sw-bar-track"><div class="sw-bar sw-bar-success" :style="{ width: s.score + '%' }"></div></div>
              <span class="sw-score sw-score-success">{{ s.score }}</span>
            </div>
          </div>
        </div>
        <div class="card">
          <h3 class="card-title sw-title">
            <span class="sw-dot sw-dot-warning"></span>
            待改进项
          </h3>
          <div class="sw-list">
            <div v-for="w in weaknesses" :key="w.name" class="sw-item">
              <span class="sw-name">{{ w.name }}</span>
              <div class="sw-bar-track"><div class="sw-bar sw-bar-warning" :style="{ width: w.score + '%' }"></div></div>
              <span class="sw-score sw-score-warning">{{ w.score }}</span>
            </div>
          </div>
        </div>
        <div class="card">
          <h3 class="card-title sw-title">
            <span class="sw-dot sw-dot-accent"></span>
            提升建议
          </h3>
          <div class="suggestion-list">
            <div v-for="(s, i) in suggestions" :key="i" class="suggestion-item">
              <span class="suggestion-num">{{ i + 1 }}</span>
              <span>{{ s }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Follow-up Records -->
    <div class="card followup-card">
      <h2 class="card-title">逐题回顾</h2>
      <div class="followup-list">
        <div v-for="(record, i) in followupRecords" :key="i" class="followup-item" :class="{ expanded: expandedItems.includes(i) }">
          <button class="followup-header" @click="toggleExpand(i)">
            <div class="fh-left">
              <span class="fh-num">Q{{ i + 1 }}</span>
              <span class="fh-text">{{ record.question }}</span>
            </div>
            <div class="fh-right">
              <span v-if="record.abilityTag" class="fh-tag">{{ record.abilityTag }}</span>
              <span v-if="record.score != null" class="fh-score" :class="getScoreClass(record.score)">{{ record.score }}分</span>
              <svg :class="['expand-icon', { rotated: expandedItems.includes(i) }]" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
            </div>
          </button>
          <div v-if="expandedItems.includes(i)" class="followup-body">
            <div class="fb-section">
              <span class="fb-label">你的回答</span>
              <p v-if="record.answer" class="fb-text">{{ record.answer }}</p>
              <p v-else class="fb-text">{{ i === followupRecords.length - 1 ? '（此题已跳过，面试已结束）' : '（此题已跳过，直接进入下一题）' }}</p>
            </div>
            <div v-for="(fu, fi) in record.followups" :key="fi" class="fb-section fb-followup">
              <span class="fb-label">追问{{ record.followups.length > 1 ? (fi + 1) : '' }}</span>
              <p class="fb-text">{{ fu }}</p>
            </div>
            <div v-if="record.evaluation" class="fb-section fb-evaluation">
              <span class="fb-label">AI 评价</span>
              <p class="fb-text">{{ record.evaluation }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Export Actions -->
    <div class="export-actions">
      <button class="export-btn" @click="handleExport('pdf')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
        </svg>
        导出 PDF
      </button>
      <button class="export-btn" @click="handleExport('docx')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
        </svg>
        导出 Word
      </button>
      <router-link to="/jobs" class="btn-primary">
        再来一次
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
        </svg>
      </router-link>
    </div>
    </template>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import RadarChart from '../components/ui/RadarChart.vue'
import { getReportDetail, exportReport, getSessionMessages } from '../api'

const route = useRoute()
const reportId = route.params.id

const loading = ref(true)
const totalScore = ref(0)
const summary = ref('')
const jobName = ref('')
const weakTags = ref('')
const radarLabels = ref([])
const radarValues = ref([])
const strengths = ref([])
const weaknesses = ref([])
const suggestions = ref([])
const followupRecords = ref([])

function getScoreRank(score) {
  if (score >= 85) return '优秀'
  if (score >= 70) return '良好'
  return '中等'
}

function getScoreClass(score) {
  if (score >= 85) return 'score-high'
  if (score >= 70) return 'score-mid'
  return 'score-low'
}

function groupMessages(messages) {
  const records = []
  let current = null

  for (const msg of messages) {
    if (msg.role === 'INTERVIEWER' && msg.msgType === 'MAIN') {
      if (current) records.push(current)
      current = {
        question: msg.content,
        abilityTag: msg.abilityTag || '',
        answer: '',
        followups: [],
        evaluation: '',
        score: null,
      }
    } else if (msg.role === 'CANDIDATE' && current) {
      current.answer = msg.content
    } else if (msg.role === 'INTERVIEWER' && current) {
      if (msg.msgType === 'FOLLOWUP') {
        current.followups.push(msg.content)
      } else {
        current.evaluation = msg.content
      }
    }
  }
  if (current) records.push(current)
  return records
}

async function handleExport(format) {
  try {
    const res = await exportReport(reportId, format)
    const blob = res.data
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `interview-report.${format === 'docx' ? 'docx' : 'pdf'}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    alert('导出失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

onMounted(async () => {
  try {
    const data = await getReportDetail(reportId)

    totalScore.value = data.totalScore || 0
    summary.value = data.summary || ''
    jobName.value = data.jobName || ''
    weakTags.value = data.weakTags || ''

    const dims = data.dimensions || []
    radarLabels.value = dims.map((d) => d.dimension)
    radarValues.value = dims.map((d) => d.score)

    const sorted = [...dims].sort((a, b) => b.score - a.score)
    strengths.value = sorted.slice(0, 3).map((d) => ({ name: d.dimension, score: d.score }))
    weaknesses.value = sorted.slice(-2).reverse().map((d) => ({ name: d.dimension, score: d.score }))

    suggestions.value = data.suggestions || []

    if (data.sessionId) {
      const messages = await getSessionMessages(data.sessionId)
      followupRecords.value = groupMessages(Array.isArray(messages) ? messages : messages.data || [])
    }
  } catch (e) {
    console.error('Failed to load report:', e)
  } finally {
    loading.value = false
  }
})

const expandedItems = ref([0])

function toggleExpand(index) {
  const i = expandedItems.value.indexOf(index)
  if (i > -1) expandedItems.value.splice(i, 1)
  else expandedItems.value.push(index)
}
</script>

<style scoped>
.detail-page {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) 0 var(--space-16);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: var(--neutral-400);
  font-size: var(--text-sm);
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-6);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-out-expo);
}

.back-link:hover {
  color: var(--accent-600);
}

/* Score Hero */
.score-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-8) var(--space-10);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-6);
  position: relative;
  overflow: hidden;
  animation: fade-in-up 0.5s var(--ease-out-expo);
}

.score-hero::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--accent-400), var(--accent-600));
}

.score-hero-left {
  display: flex;
  flex-direction: column;
}

.score-label {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-2);
  font-weight: 500;
}

.score-number {
  display: flex;
  align-items: baseline;
  gap: var(--space-1);
}

.score-value {
  font-family: var(--font-mono);
  font-size: 4.5rem;
  font-weight: 700;
  color: var(--accent-500);
  line-height: 1;
}

.score-total {
  font-size: var(--text-2xl);
  color: var(--neutral-400);
}

.score-rank {
  display: inline-block;
  margin-top: var(--space-3);
  padding: var(--space-1) var(--space-4);
  background: var(--accent-50);
  color: var(--accent-600);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 600;
  width: fit-content;
}

.score-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5) var(--space-8);
}

.meta-item {
  text-align: right;
}

.meta-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--neutral-400);
  margin-bottom: 2px;
  font-weight: 500;
}

.meta-value {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
}

/* Cards */
.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  animation: fade-in-up 0.5s var(--ease-out-expo) 0.1s backwards;
}

.card-title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-5);
}

/* Analysis Grid */
.analysis-grid {
  display: grid;
  grid-template-columns: 350px 1fr;
  gap: var(--space-6);
  margin-bottom: var(--space-6);
}

.sw-column {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.sw-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.sw-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.sw-dot-success { background: var(--accent-500); }
.sw-dot-warning { background: #d97706; }
.sw-dot-accent { background: var(--accent-500); }

.sw-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.sw-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.sw-name {
  width: 72px;
  font-size: var(--text-sm);
  color: var(--neutral-600);
  flex-shrink: 0;
}

.sw-bar-track {
  flex: 1;
  height: 6px;
  background: var(--neutral-100);
  border-radius: 3px;
  overflow: hidden;
}

.sw-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 1s var(--ease-out-expo);
}

.sw-bar-success { background: var(--accent-500); }
.sw-bar-warning { background: #d97706; }

.sw-score {
  width: 32px;
  text-align: right;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 600;
}

.sw-score-success { color: var(--accent-500); }
.sw-score-warning { color: #d97706; }

/* Suggestions */
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.suggestion-item {
  display: flex;
  gap: var(--space-3);
  font-size: var(--text-sm);
  color: var(--neutral-600);
  line-height: 1.6;
}

.suggestion-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent-50);
  color: var(--accent-600);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

/* Follow-up */
.followup-card {
  margin-bottom: var(--space-6);
}

.followup-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.followup-item {
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-out-expo);
}

.followup-item:hover {
  border-color: var(--neutral-300);
}

.followup-item.expanded {
  border-color: var(--accent-300);
}

.followup-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4);
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
  font-family: var(--font-body);
  transition: background var(--duration-fast) var(--ease-out-expo);
}

.followup-header:hover {
  background: var(--neutral-50);
}

.fh-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
}

.fh-num {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-600);
  padding: 2px 8px;
  background: var(--accent-50);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.fh-text {
  font-size: var(--text-sm);
  color: var(--neutral-800);
  white-space: normal;
  word-break: break-word;
  line-height: 1.6;
}

.fh-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-shrink: 0;
}

.fh-score {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 700;
}

.score-high { color: var(--accent-500); }
.score-mid { color: #d97706; }
.score-low { color: #ef4444; }

.fh-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--neutral-100);
  color: var(--neutral-600);
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.expand-icon {
  color: var(--neutral-400);
  transition: transform var(--duration-normal) var(--ease-out-expo);
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.followup-body {
  padding: 0 var(--space-4) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  animation: fade-in 0.2s var(--ease-out-expo);
}

.fb-section {
  padding: var(--space-4);
  background: var(--neutral-50);
  border-radius: var(--radius-sm);
}

.fb-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--neutral-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--space-2);
}

.fb-text {
  font-size: var(--text-sm);
  color: var(--neutral-700);
  line-height: 1.7;
}

.fb-skipped {
  color: var(--neutral-400);
  font-style: italic;
}

.fb-evaluation {
  border-left: 3px solid var(--accent-400);
  background: var(--accent-50);
}

.fb-followup {
  border-left: 3px solid var(--accent-200);
  margin-left: var(--space-4);
}

/* Export */
.export-actions {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
  animation: fade-in-up 0.5s var(--ease-out-expo) 0.2s backwards;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-expo);
}

.export-btn:hover {
  border-color: var(--accent-300);
  color: var(--accent-600);
  background: var(--accent-50);
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  background: var(--accent-500);
  color: white;
  font-size: var(--text-sm);
  font-weight: 600;
  border-radius: var(--radius-md);
  text-decoration: none;
  transition: all var(--duration-normal) var(--ease-out-expo);
}

.btn-primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-accent);
  color: white;
}

/* Responsive */
@media (max-width: 768px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }

  .score-hero {
    flex-direction: column;
    gap: var(--space-6);
    text-align: center;
    padding: var(--space-6);
  }

  .score-hero-left {
    align-items: center;
  }

  .meta-item {
    text-align: center;
  }

  .export-actions {
    flex-wrap: wrap;
  }
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  .score-hero,
  .card,
  .export-actions {
    animation: none;
  }

  .followup-body {
    animation: none;
  }

  .expand-icon {
    transition: none;
  }

  .sw-bar {
    transition: none;
  }
}
</style>
