<template>
  <AppLayout>
    <div class="page-container">
      <!-- Page Header -->
      <header class="page-header reveal">
        <h1 class="page-title">面试准备</h1>
        <p class="page-desc">选择目标岗位，上传简历，AI 将为你定制专属面试方案</p>
      </header>

      <!-- Step Indicator -->
      <nav class="stepper reveal" aria-label="面试准备步骤">
        <div
          v-for="(step, i) in stepsInfo"
          :key="i"
          class="stepper__item"
          :class="{
            'stepper__item--active': currentStep === i,
            'stepper__item--done': currentStep > i
          }"
        >
          <div class="stepper__dot" :aria-current="currentStep === i ? 'step' : undefined">
            <svg v-if="currentStep > i" class="stepper__check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <span v-else class="stepper__num">{{ i + 1 }}</span>
          </div>
          <span class="stepper__label">{{ step }}</span>
        </div>
        <div class="stepper__track">
          <div class="stepper__track-fill" :style="{ width: (currentStep / 2 * 100) + '%' }" />
        </div>
      </nav>

      <!-- ========== Step 1: Select Family → Position ========== -->
        <section v-show="currentStep === 0" key="step0" class="step-panel">
          <!-- Search -->
          <div class="search-area reveal">
            <div class="search-box">
              <svg class="search-box__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                class="search-box__input"
                :placeholder="activeFamily ? '搜索' + activeFamily + '岗位...' : '搜索岗位名称或技能标签...'"
              />
            </div>
          </div>

          <!-- Family Chips -->
          <div class="family-row reveal">
            <button
              v-for="fam in familyStats"
              :key="fam.code"
              class="family-chip"
              :class="{ 'family-chip--active': activeFamily === fam.code }"
              :style="{ '--chip-accent': fam.color.accentColor }"
              @click="selectFamily(fam.code)"
            >
              <span class="family-chip__icon" :style="{ background: fam.color.iconBg, color: fam.color.accentColor }">
                {{ fam.icon }}
              </span>
              <span class="family-chip__name">{{ fam.name }}</span>
              <span class="family-chip__count">{{ fam.count }}</span>
            </button>
          </div>

          <!-- Position Grid (visible when family or search is active) -->
          <div v-if="activeFamily || searchQuery" class="job-grid" :style="gridMinHeight ? { minHeight: gridMinHeight + 'px' } : {}">
            <article
              v-for="(job, idx) in filteredJobs"
              :key="job.id"
              class="job-card reveal"
              :class="{
                'job-card--selected': selectedJob?.id === job.id,
                'job-card--disabled': !READY_JOBS.has(job.code)
              }"
              :style="{ '--card-accent': job.accentColor, '--reveal-delay': idx * 0.04 + 's' }"
              @click="selectPosition(job, $event)"
              role="button"
              :tabindex="READY_JOBS.has(job.code) ? 0 : -1"
              @keydown.enter="selectPosition(job)"
              @keydown.space.prevent="selectPosition(job)"
            >
              <div class="job-card__accent" />

              <div class="job-card__head">
                <div class="job-card__icon" :style="{ background: job.iconBg }">
                  {{ job.title.charAt(0) }}
                </div>
                <span v-if="!READY_JOBS.has(job.code)" class="job-card__soon">敬请期待</span>
                <span v-else class="job-card__code">{{ job.code }}</span>
              </div>

              <h3 class="job-card__title">{{ job.title }}</h3>

              <div class="job-card__tags">
                <span v-for="tag in job.tags.slice(0, 3)" :key="tag" class="job-card__tag">{{ tag }}</span>
              </div>

              <!-- Select indicator circle -->
              <div class="job-card__select" :class="{ 'job-card__select--on': selectedJob?.id === job.id }">
                <svg v-if="selectedJob?.id === job.id" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
            </article>
          </div>

          <!-- Empty prompt -->
          <div v-else class="family-prompt reveal">
            <div class="family-prompt__icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--neutral-300)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </div>
            <p class="family-prompt__text">请选择一个岗位族，查看其下具体岗位</p>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <div />
            <button class="btn btn--primary" :disabled="!selectedJob" @click="currentStep = 1">
              下一步：上传简历
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </section>

        <!-- ========== Step 2: Upload Resume ========== -->
        <section v-show="currentStep === 1" key="step1" class="step-panel">
          <div class="upload-layout">
            <div class="upload-card card reveal">
              <h2 class="card__title">上传简历</h2>
              <p class="card__desc">AI 将自动提取你的技能标签和项目经历，用于个性化面试方案</p>

              <div
                class="upload-zone"
                :class="{
                  'upload-zone--dragging': isDragging,
                  'upload-zone--filled': uploadedFile
                }"
                @dragover.prevent="isDragging = true"
                @dragleave="isDragging = false"
                @drop.prevent="handleDrop"
                @click="triggerUpload"
              >
                <input
                  ref="fileInput"
                  type="file"
                  accept=".pdf,.doc,.docx"
                  hidden
                  @change="handleFileChange"
                />

                <template v-if="!uploadedFile">
                  <div class="upload-zone__icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                  </div>
                  <p class="upload-zone__text">拖拽简历到此处，或 <span class="upload-zone__link">点击上传</span></p>
                  <p class="upload-zone__hint">支持 PDF / Word 格式，最大 10MB</p>
                </template>

                <template v-else>
                  <div class="file-chip">
                    <div class="file-chip__icon">
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--accent-600)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                      </svg>
                    </div>
                    <div class="file-chip__info">
                      <span class="file-chip__name">{{ uploadedFile.name }}</span>
                      <span class="file-chip__size">{{ formatSize(uploadedFile.size) }}</span>
                    </div>
                    <button class="file-chip__remove" @click.stop="removeFile" aria-label="删除文件">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                </template>
              </div>

              <!-- Extracted Skills -->
              <Transition name="slide-up">
                <div v-if="extractedSkills.length" class="extracted-skills">
                  <h3 class="extracted-skills__title">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="22 4 12 14.01 9 11.01" />
                    </svg>
                    AI 提取的技能标签
                  </h3>
                  <div class="extracted-skills__grid">
                    <span
                      v-for="(skill, i) in extractedSkills"
                      :key="skill"
                      class="skill-pill"
                      :style="{ animationDelay: i * 0.04 + 's' }"
                    >{{ skill }}</span>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Job Summary Sidebar -->
            <aside v-if="selectedJob" class="job-summary card reveal">
              <h3 class="card__title">已选岗位</h3>
              <div class="summary-card">
                <div class="summary-card__icon" :style="{ background: selectedJob.iconBg }">
                  {{ selectedJob.title.charAt(0) }}
                </div>
                <div class="summary-card__body">
                  <span class="summary-card__title">{{ selectedJob.title }}</span>
                  <span class="summary-card__code-label">{{ selectedJob.code }}</span>
                </div>
              </div>
              <div class="summary-section">
                <h4 class="summary-section__label">面试重点</h4>
                <div class="summary-section__tags">
                  <span v-for="focus in selectedJob.focus" :key="focus" class="focus-pill">{{ focus }}</span>
                </div>
              </div>
            </aside>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 0">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              上一步
            </button>
            <div class="step-actions__right">
              <button class="btn btn--ghost" @click="currentStep = 2">跳过，直接开始</button>
              <button class="btn btn--primary" @click="currentStep = 2">
                确认并开始面试
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </div>
        </section>

        <!-- ========== Step 3: Confirm & Start ========== -->
        <section v-show="currentStep === 2" key="step2" class="step-panel">
          <div class="confirm-wrap reveal">
            <div class="confirm-card card">
              <div class="confirm-card__header">
                <div class="confirm-card__icon-ring">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
                  </svg>
                </div>
                <h2 class="confirm-card__title">准备就绪</h2>
                <p class="confirm-card__subtitle">即将开始你的 AI 模拟面试</p>
              </div>

              <div class="confirm-card__details">
                <div class="detail-row">
                  <span class="detail-row__label">目标岗位</span>
                  <span class="detail-row__value">{{ selectedJob?.title || '快速面试' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">简历</span>
                  <span class="detail-row__value">{{ uploadedFile ? uploadedFile.name : '未上传' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">预计时长</span>
                  <span class="detail-row__value">20-30 分钟</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">题目数量</span>
                  <span class="detail-row__value">6-10 题（含追问）</span>
                </div>
              </div>

              <div class="confirm-card__tip">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
                <span>面试过程中请保持网络稳定，建议使用安静的环境</span>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 1">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              返回修改
            </button>
            <button
              class="btn btn--primary btn--lg"
              :disabled="startingInterview"
              @click="handleStartInterview"
            >
              {{ startingInterview ? '正在启动...' : '开始面试' }}
              <svg v-if="!startingInterview" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </button>
          </div>
        </section>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import { getJobList, uploadResumeFile, startInterview } from '../api'

/* ------------------------------------------------------------------ */
/*  State                                                              */
/* ------------------------------------------------------------------ */
const router = useRouter()
const currentStep = ref(0)
const searchQuery = ref('')
const selectedJob = ref(null)
const isDragging = ref(false)
const uploadedFile = ref(null)
const fileInput = ref(null)
const extractedSkills = ref([])

const jobsLoading = ref(false)
const jobsError = ref('')
const resumeUploading = ref(false)
const resumeError = ref('')
const startingInterview = ref(false)
const startError = ref('')
const activeFamily = ref('')

const stepsInfo = ['选择岗位', '上传简历', '确认信息']

/* ------------------------------------------------------------------ */
/*  Jobs ready for interview                                           */
/* ------------------------------------------------------------------ */
const READY_JOBS = new Set([
  'BE-JAVA',   // Java后端开发工程师
  'BE-PY',     // Python后端开发工程师
  'FE-WEB',    // Web前端开发工程师
  'FS-JAVA',   // Java Web全栈开发工程师
  'ALG-ML',    // 机器学习算法工程师
])

/* ------------------------------------------------------------------ */
/*  Family definitions                                                  */
/* ------------------------------------------------------------------ */
const families = [
  { code: '后端开发', name: '后端开发', icon: 'B' },
  { code: '前端与客户端开发', name: '前端与客户端', icon: 'F' },
  { code: '全栈开发', name: '全栈开发', icon: 'S' },
  { code: '算法与人工智能', name: '算法与AI', icon: 'A' },
  { code: '产品经理', name: '产品经理', icon: 'P' },
  { code: '数据分析', name: '数据分析', icon: 'D' },
  { code: '软件测试', name: '软件测试', icon: 'Q' },
]

const familyColorMap = {
  '后端开发':          { iconBg: 'rgba(99,102,241,0.08)',  accentColor: '#6366f1' },
  '前端与客户端开发':  { iconBg: 'rgba(249,115,22,0.08)',  accentColor: '#f97316' },
  '全栈开发':          { iconBg: 'rgba(16,185,129,0.08)',  accentColor: '#10b981' },
  '算法与人工智能':    { iconBg: 'rgba(236,72,153,0.08)',  accentColor: '#ec4899' },
  '产品经理':          { iconBg: 'rgba(6,182,212,0.08)',   accentColor: '#06b6d4' },
  '数据分析':          { iconBg: 'rgba(168,85,247,0.08)',  accentColor: '#a855f7' },
  '软件测试':          { iconBg: 'rgba(234,179,8,0.08)',   accentColor: '#eab308' },
}

/* ------------------------------------------------------------------ */
/*  Job data - loaded from API                                         */
/* ------------------------------------------------------------------ */
const jobs = ref([])

/* ------------------------------------------------------------------ */
/*  Mappers                                                            */
/* ------------------------------------------------------------------ */
function mapJobFromBackend(job) {
  const colors = familyColorMap[job.family] || familyColorMap['后端开发']
  return {
    id: job.id,
    title: job.name,
    code: job.code || '',
    family: job.family || '',
    tags: parseJsonField(job.keywords),
    focus: parseJsonField(job.abilities),
    iconBg: colors.iconBg,
    accentColor: colors.accentColor,
    category: job.category || '',
    pro: false,
  }
}

function parseJsonField(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try { return JSON.parse(raw) } catch { return [raw] }
  }
  return []
}

async function fetchJobs() {
  jobsLoading.value = true
  jobsError.value = ''
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
  } catch (e) {
    console.error('Failed to load jobs:', e)
    jobsError.value = '加载岗位列表失败，请刷新重试'
  } finally {
    jobsLoading.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  Computed                                                           */
/* ------------------------------------------------------------------ */
const jobsByFamily = computed(() => {
  const map = {}
  jobs.value.forEach(job => {
    if (!map[job.family]) map[job.family] = []
    map[job.family].push(job)
  })
  return map
})

const familyStats = computed(() => {
  return families.map(f => ({
    ...f,
    count: (jobsByFamily.value[f.code] || []).length,
    color: familyColorMap[f.code] || familyColorMap['后端开发'],
  }))
})

const filteredJobs = computed(() => {
  const pool = activeFamily.value
    ? (jobsByFamily.value[activeFamily.value] || [])
    : jobs.value
  return pool.filter(j => {
    const matchSearch =
      !searchQuery.value ||
      j.title.includes(searchQuery.value) ||
      j.tags.some(t => t.includes(searchQuery.value))
    return matchSearch
  })
})

/* Ensure grid stays as tall as the largest family's grid */
const gridMinHeight = computed(() => {
  if (!activeFamily.value && !searchQuery.value) return null
  const maxCount = Math.max(...families.map(f => (jobsByFamily.value[f.code] || []).length), 0)
  // 3 columns → rows = ceil(count/3), each row ≈ 170px
  return Math.ceil(maxCount / 3) * 170
})

/* ------------------------------------------------------------------ */
/*  Family selector                                                    */
/* ------------------------------------------------------------------ */
function selectFamily(code) {
  activeFamily.value = activeFamily.value === code ? '' : code
  selectedJob.value = null
  searchQuery.value = ''
}

function selectPosition(job, event) {
  if (!READY_JOBS.has(job.code)) {
    // 点击未就绪岗位：卡片晃动 + 角标闪烁
    const card = event?.currentTarget
    if (card) {
      card.classList.add('job-card--shake')
      setTimeout(() => card.classList.remove('job-card--shake'), 500)
      const badge = card.querySelector('.job-card__soon')
      if (badge) {
        badge.classList.add('job-card__soon--flash')
        setTimeout(() => badge.classList.remove('job-card__soon--flash'), 600)
      }
    }
    return
  }
  selectedJob.value = job
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */
function triggerUpload() {
  if (!uploadedFile.value) fileInput.value?.click()
}

function handleFileChange(e) {
  const file = e.target.files[0]
  if (file) {
    uploadedFile.value = file
    simulateExtract()
  }
}

function handleDrop(e) {
  isDragging.value = false
  const file = e.dataTransfer.files[0]
  if (file) {
    uploadedFile.value = file
    simulateExtract()
  }
}

function removeFile() {
  uploadedFile.value = null
  extractedSkills.value = []
}

function simulateExtract() {
  resumeUploading.value = true
  resumeError.value = ''
  uploadResumeFile(uploadedFile.value)
    .then((data) => {
      const skills = []
      if (data.skills) skills.push(...data.skills)
      if (data.keywords) {
        data.keywords.forEach(k => { if (!skills.includes(k)) skills.push(k) })
      }
      extractedSkills.value = skills
    })
    .catch((e) => {
      console.error('Resume upload failed:', e)
      resumeError.value = '简历解析失败，请重试'
    })
    .finally(() => {
      resumeUploading.value = false
    })
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleStartInterview() {
  if (!selectedJob.value) return
  startingInterview.value = true
  startError.value = ''
  try {
    const res = await startInterview({ jobId: selectedJob.value.id })
    router.push({
      path: '/interview',
      query: {
        sessionId: String(res.sessionId),
        jobId: String(selectedJob.value.id),
      },
    })
  } catch (e) {
    console.error('Failed to start interview:', e)
    startError.value = '启动面试失败，请重试'
  } finally {
    startingInterview.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  Scroll-reveal via IntersectionObserver                             */
/* ------------------------------------------------------------------ */
let observer = null

function initObserver() {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.08, rootMargin: '0px 0px -40px 0px' }
  )
  document.querySelectorAll('.reveal').forEach(el => {
    if (!el.classList.contains('revealed')) observer.observe(el)
  })
}

function scheduleObserve() {
  nextTick(() => {
    requestAnimationFrame(initObserver)
  })
}

watch(currentStep, scheduleObserve)
onMounted(() => {
  scheduleObserve()
  fetchJobs()
})
onUnmounted(() => { if (observer) observer.disconnect() })
</script>

<style scoped>
/* ===================================================================
   DESIGN TOKENS (local aliases)
   =================================================================== */
.page-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) var(--space-6) var(--space-12);
}

/* ===================================================================
   PAGE HEADER
   =================================================================== */
.page-header {
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 800;
  color: var(--neutral-900);
  letter-spacing: -0.025em;
  line-height: 1.2;
}

.page-desc {
  margin-top: var(--space-2);
  font-size: var(--text-base);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

/* ===================================================================
   STEP INDICATOR (Stepper)
   =================================================================== */
.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-10);
  position: relative;
  margin-bottom: var(--space-10);
  padding: var(--space-4) 0;
}

.stepper__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  z-index: 1;
}

.stepper__dot {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  border: 2px solid var(--neutral-300);
  background: var(--surface-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--neutral-400);
  transition:
    background var(--duration-normal) var(--ease-out-expo),
    border-color var(--duration-normal) var(--ease-out-expo),
    color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo);
}

.stepper__item--active .stepper__dot {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
  box-shadow: var(--shadow-accent);
}

.stepper__item--done .stepper__dot {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
}

.stepper__check {
  display: block;
}

.stepper__num {
  display: block;
  line-height: 1;
}

.stepper__label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-400);
  font-family: var(--font-body);
  transition: color var(--duration-normal);
}

.stepper__item--active .stepper__label {
  color: var(--neutral-900);
  font-weight: 600;
}

.stepper__item--done .stepper__label {
  color: var(--neutral-600);
}

/* Track */
.stepper__track {
  position: absolute;
  top: 50%;
  left: 22%;
  right: 22%;
  height: 2px;
  background: var(--neutral-200);
  border-radius: 1px;
  transform: translateY(-50%);
  z-index: 0;
}

.stepper__track-fill {
  height: 100%;
  background: var(--accent-500);
  border-radius: 1px;
  transition: width 0.6s var(--ease-out-expo);
}

/* ===================================================================
   STEP PANEL (container with transition)
   =================================================================== */
.step-panel {
  outline: none;
  animation: step-enter 0.3s var(--ease-out-expo);
}

@keyframes step-enter {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.step-fade-enter-active {
  transition: opacity 0.25s var(--ease-out-expo), transform 0.25s var(--ease-out-expo);
}
.step-fade-leave-active {
  transition: opacity 0.15s ease-in, transform 0.15s ease-in;
}
.step-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.step-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ===================================================================
   STEP 1: SEARCH & FILTER
   =================================================================== */
.search-area {
  margin-bottom: var(--space-6);
}

.search-box {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo);
  margin-bottom: var(--space-4);
}

.search-box:focus-within {
  border-color: var(--accent-400);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.search-box__icon {
  color: var(--neutral-400);
  flex-shrink: 0;
}

.search-box__input {
  flex: 1;
  border: none;
  background: none;
  color: var(--neutral-900);
  font-family: var(--font-body);
  font-size: var(--text-base);
  outline: none;
}

.search-box__input::placeholder {
  color: var(--neutral-400);
}

/* Family selector row */
.family-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-6);
}

.family-chip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  background: var(--surface-elevated);
  cursor: pointer;
  transition:
    background var(--duration-fast),
    border-color var(--duration-fast),
    box-shadow var(--duration-fast),
    transform var(--duration-fast);
}

.family-chip:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
  transform: translateY(-1px);
}

.family-chip--active {
  border-color: var(--chip-accent, var(--accent-500));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--chip-accent, var(--accent-500)) 15%, transparent);
}

.family-chip__icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: 700;
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.family-chip--active .family-chip__icon {
  background: var(--chip-accent, var(--accent-500)) !important;
  color: #fff !important;
}

.family-chip__name {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-700);
  white-space: nowrap;
}

.family-chip--active .family-chip__name {
  font-weight: 600;
}

.family-chip__count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--neutral-400);
  padding: 1px 7px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
}

/* Job card code badge */
.job-card__code {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-500);
  letter-spacing: 0.03em;
}

/* "敬请期待" badge */
.job-card__soon {
  font-size: 10px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-400);
  letter-spacing: 0.02em;
  transition: all 0.15s ease;
}

.job-card__soon--flash {
  background: var(--accent-500);
  color: #fff;
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.5);
}

/* Disabled job card */
.job-card--disabled {
  opacity: 0.45;
  cursor: not-allowed;
  filter: grayscale(0.6);
}

.job-card--disabled:hover {
  transform: none;
  box-shadow: none;
  border-color: var(--neutral-200);
}

.job-card--shake,
.job-card--shake:hover {
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10% { transform: translateX(-6px); }
  30% { transform: translateX(6px); }
  50% { transform: translateX(-4px); }
  70% { transform: translateX(4px); }
  90% { transform: translateX(-2px); }
}

/* Family prompt (empty state) */
.family-prompt {
  text-align: center;
  padding: var(--space-12) 0;
  margin-bottom: var(--space-6);
}

.family-prompt__icon {
  margin-bottom: var(--space-4);
}

.family-prompt__text {
  font-size: var(--text-base);
  color: var(--neutral-400);
  font-family: var(--font-body);
}

/* ===================================================================
   JOB GRID
   =================================================================== */
.job-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  align-content: start;
}

/* ===================================================================
   JOB CARD
   =================================================================== */
.job-card {
  position: relative;
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-5) var(--space-5) var(--space-4);
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    transform var(--duration-normal) var(--ease-out-expo);
}

.job-card:hover {
  border-color: var(--neutral-300);
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

/* Accent bar at top */
.job-card__accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--card-accent, var(--neutral-300));
  opacity: 0.5;
  transition: opacity var(--duration-normal);
}

.job-card:hover .job-card__accent {
  opacity: 1;
}

/* Selected state */
.job-card--selected {
  border-color: var(--accent-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12), var(--shadow-md);
}

.job-card--selected .job-card__accent {
  opacity: 1;
  background: var(--accent-500);
}

/* Featured (Pro) card */
.job-card--featured {
  border-color: var(--accent-300);
  background: linear-gradient(
    180deg,
    rgba(16, 185, 129, 0.02) 0%,
    var(--surface-elevated) 40%
  );
}

.job-card--featured .job-card__accent {
  opacity: 0.8;
}

/* Card header */
.job-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-3);
}

.job-card__icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-700);
  flex-shrink: 0;
}

.pro-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  color: #fff;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  line-height: 1.3;
  box-shadow: var(--shadow-accent);
}

/* Card title */
.job-card__title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-3);
  line-height: 1.3;
}

/* Tags */
.job-card__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}

.job-card__tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-600);
  font-family: var(--font-mono);
  letter-spacing: 0.01em;
}

/* Select indicator circle */
.job-card__select {
  position: absolute;
  bottom: 14px;
  right: 14px;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  border: 2px solid var(--neutral-300);
  background: var(--surface-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  color: transparent;
  transition: all 0.25s var(--ease-out-expo);
}

.job-card__select--on {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
  box-shadow: var(--shadow-accent);
}

/* ===================================================================
   STEP 2: UPLOAD
   =================================================================== */
.upload-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--space-6);
  align-items: start;
}

.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
}

.card__title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-1);
}

.card__desc {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-6);
  line-height: 1.6;
}

/* Upload zone */
.upload-zone {
  border: 2px dashed var(--neutral-300);
  border-radius: var(--radius-lg);
  padding: var(--space-12) var(--space-6);
  text-align: center;
  cursor: pointer;
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    background var(--duration-normal) var(--ease-out-expo),
    padding var(--duration-normal) var(--ease-out-expo);
}

.upload-zone:hover,
.upload-zone--dragging {
  border-color: var(--accent-400);
  background: var(--accent-50);
}

.upload-zone--dragging {
  border-style: solid;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.upload-zone--filled {
  border-style: solid;
  border-color: var(--accent-200);
  cursor: default;
  padding: var(--space-5);
  text-align: left;
}

.upload-zone__icon {
  color: var(--neutral-400);
  margin-bottom: var(--space-4);
  transition: color var(--duration-normal);
}

.upload-zone:hover .upload-zone__icon {
  color: var(--accent-500);
}

.upload-zone__text {
  font-size: var(--text-base);
  color: var(--neutral-600);
  margin-bottom: var(--space-2);
}

.upload-zone__link {
  color: var(--accent-600);
  font-weight: 600;
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 2px;
}

.upload-zone__hint {
  font-size: var(--text-sm);
  color: var(--neutral-400);
}

/* File chip (after upload) */
.file-chip {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.file-chip__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--accent-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-chip__info {
  flex: 1;
  min-width: 0;
}

.file-chip__name {
  display: block;
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-chip__size {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  font-family: var(--font-mono);
}

.file-chip__remove {
  background: none;
  border: none;
  color: var(--neutral-400);
  cursor: pointer;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast);
  flex-shrink: 0;
}

.file-chip__remove:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

/* Extracted skills */
.extracted-skills {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--neutral-200);
}

.extracted-skills__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--accent-600);
  margin-bottom: var(--space-3);
  font-family: var(--font-body);
}

.extracted-skills__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.skill-pill {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-size: var(--text-sm);
  font-family: var(--font-mono);
  animation: skill-pop 0.3s var(--ease-out-expo) backwards;
}

.slide-up-enter-active {
  transition: all 0.4s var(--ease-out-expo);
}
.slide-up-leave-active {
  transition: all 0.2s ease-in;
}
.slide-up-enter-from {
  opacity: 0;
  transform: translateY(16px);
}
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes skill-pop {
  from {
    opacity: 0;
    transform: scale(0.8) translateY(4px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* Job summary sidebar */
.job-summary {
  position: sticky;
  top: calc(var(--nav-height) + var(--space-8));
}

.summary-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--neutral-50);
  border-radius: var(--radius-md);
  margin: var(--space-4) 0;
}

.summary-card__icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: var(--neutral-700);
  flex-shrink: 0;
}

.summary-card__body {
  min-width: 0;
}

.summary-card__title {
  display: block;
  font-weight: 600;
  color: var(--neutral-900);
  font-size: var(--text-sm);
  margin-bottom: 2px;
}

.summary-card__code-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-600);
}

.summary-section {
  margin-top: var(--space-4);
}

.summary-section__label {
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--neutral-500);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: var(--space-2);
}

.summary-section__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.focus-pill {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-700);
  font-size: var(--text-xs);
  font-family: var(--font-body);
}

/* ===================================================================
   STEP 3: CONFIRM
   =================================================================== */
.confirm-wrap {
  max-width: 540px;
  margin: 0 auto;
}

.confirm-card {
  text-align: center;
}

.confirm-card__header {
  margin-bottom: var(--space-6);
}

.confirm-card__icon-ring {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 2px solid var(--accent-200);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--space-4);
}

.confirm-card__title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  color: var(--neutral-900);
  margin-bottom: var(--space-1);
  letter-spacing: -0.02em;
}

.confirm-card__subtitle {
  color: var(--neutral-500);
  font-size: var(--text-base);
}

.confirm-card__details {
  text-align: left;
  background: var(--neutral-50);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-4);
  margin-bottom: var(--space-4);
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) 0;
}

.detail-row:not(:last-child) {
  border-bottom: 1px solid var(--neutral-200);
}

.detail-row__label {
  font-size: var(--text-sm);
  color: var(--neutral-500);
}

.detail-row__value {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-900);
}

.confirm-card__tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--accent-700);
  padding: var(--space-3) var(--space-4);
  background: var(--accent-50);
  border-radius: var(--radius-md);
  border: 1px solid var(--accent-100);
}

/* ===================================================================
   STEP ACTIONS (bottom nav)
   =================================================================== */
.step-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid var(--neutral-200);
}

.step-actions__right {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

/* ===================================================================
   BUTTONS
   =================================================================== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  border-radius: var(--radius-md);
  border: none;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background var(--duration-normal) var(--ease-out-expo),
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    transform var(--duration-fast) var(--ease-out-expo),
    color var(--duration-normal) var(--ease-out-expo);
}

.btn--primary {
  padding: var(--space-3) var(--space-6);
  background: var(--accent-500);
  color: #fff;
}

.btn--primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-accent);
  transform: translateY(-1px);
}

.btn--primary:active {
  transform: translateY(0);
}

.btn--primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
  background: var(--accent-500);
}

.btn--lg {
  padding: var(--space-4) var(--space-8);
  font-size: var(--text-base);
}

.btn--ghost {
  padding: var(--space-3) var(--space-5);
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  color: var(--neutral-700);
}

.btn--ghost:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
}

/* ===================================================================
   SCROLL REVEAL
   =================================================================== */
.reveal {
  opacity: 0;
  transform: translateY(24px);
  transition:
    opacity 0.6s var(--ease-out-expo),
    transform 0.6s var(--ease-out-expo);
  transition-delay: var(--reveal-delay, 0s);
}

.reveal.revealed {
  opacity: 1;
  transform: translateY(0);
}

.job-card.reveal {
  opacity: 1;
  transform: translateY(0);
}

/* ===================================================================
   REDUCED MOTION
   =================================================================== */
@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .step-fade-enter-active,
  .step-fade-leave-active {
    transition: none;
  }

  .check-pop-enter-active,
  .check-pop-leave-active,
  .slide-up-enter-active,
  .slide-up-leave-active {
    transition: none;
  }

  .skill-pill {
    animation: none;
  }

  .stepper__track-fill,
  .stepper__dot,
  .match-bar__fill,
  .btn,
  .job-card,
  .filter-chip,
  .search-box {
    transition: none;
  }
}

/* ===================================================================
   RESPONSIVE: 1024px
   =================================================================== */
@media (max-width: 1024px) {
  .job-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .upload-layout {
    grid-template-columns: 1fr;
  }

  .job-summary {
    position: static;
  }

  .stepper {
    gap: var(--space-6);
  }
}

/* ===================================================================
   RESPONSIVE: 768px
   =================================================================== */
@media (max-width: 768px) {
  .page-container {
    padding: var(--space-6) var(--space-4) var(--space-10);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .stepper {
    gap: var(--space-3);
  }

  .stepper__label {
    display: none;
  }

  .stepper__track {
    left: 18%;
    right: 18%;
  }

  .family-row {
    gap: var(--space-2);
  }

  .family-chip {
    padding: var(--space-2) var(--space-3);
  }

  .family-chip__name {
    font-size: 12px;
  }

  .job-grid {
    grid-template-columns: 1fr;
    gap: var(--space-3);
  }

  .step-actions {
    flex-direction: column-reverse;
    gap: var(--space-3);
    align-items: stretch;
  }

  .step-actions__right {
    flex-direction: column;
    align-items: stretch;
  }

  .btn {
    justify-content: center;
    width: 100%;
  }
}
</style>
