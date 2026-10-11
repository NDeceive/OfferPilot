<template>
  <div class="wiz">
    <!-- 顶栏：返回 + 标题 + 进度 -->
    <header class="wiz__bar">
      <button type="button" class="wiz__back" :aria-label="currentStep === 0 ? '退出' : '上一步'" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div class="wiz__head">
        <strong>{{ STEPS[currentStep].title }}</strong>
        <small>{{ currentStep + 1 }} / {{ STEPS.length }} · {{ STEPS[currentStep].hint }}</small>
      </div>
      <button v-if="currentStep > 0" type="button" class="wiz__step-back" @click="currentStep -= 1">上一步</button>
    </header>

    <div class="wiz__track" aria-hidden="true">
      <i v-for="(s, i) in STEPS" :key="s.title" :class="{ 'is-done': i < currentStep, 'is-now': i === currentStep }" />
    </div>

    <div class="wiz__mode">
      <button type="button" @click="goAiPrep">对话录入 ⇄</button>
    </div>

    <!-- 内容 -->
    <div class="wiz__body">
      <p v-if="fromAi && currentStep === 2" class="wiz__tip">
        已从 AI 对话带入岗位和简历，直接挑训练目标就行。
      </p>

      <MobileJobPicker
        v-if="currentStep === 0"
        v-model:query="jobQuery"
        v-model:family="jobFamily"
        :jobs="jobs"
        :loading="jobsLoading"
        :error="jobsError"
        :selected-id="selectedJob?.id ?? null"
        @select="selectedJob = $event"
      />

      <MobileResumeStep
        v-else-if="currentStep === 1"
        :skills="extractedSkills"
        :file-name="uploadedName"
        :project-count="resumeProjectCount"
        :uploading="resumeUploading"
        :error="resumeError"
        :saved-resume="savedResume"
        :all-tags="allTags"
        :tags-loading="tagsLoading"
        :tags-error="tagsError"
        @file="selectResumeFile"
        @use-saved="useSavedResume"
        @remove="removeFile"
        @remove-skill="removeSkill"
        @toggle-tag="toggleTag"
        @open-taglib="fetchSkillTags"
        @saved-online="onOnlineResumeSaved"
      />

      <MobileTrainingGoals
        v-else-if="currentStep === 2"
        :modules="allModules"
        :loading="modulesLoading"
        :error="modulesError"
        :order="moduleOrder"
        :levels="moduleLevels"
        :active-code="activeModuleCode"
        :feedback="moduleFeedback"
        @toggle="toggleModule"
        @move="moveModule"
        @set-active="activeModuleCode = $event"
        @set-level="setLevel"
      />

      <MobileConfirmLaunch
        v-else
        :job="selectedJob"
        :skills="extractedSkills"
        :goals="confirmGoals"
        :duration-seconds="durationSeconds"
        :error="startError"
        @update:duration-seconds="durationSeconds = $event"
      />
    </div>

    <!-- 底部操作条 -->
    <footer class="wiz__cta">
      <button
        v-if="currentStep === 1 && !uploadedName"
        type="button"
        class="wiz__skip"
        @click="currentStep = 2"
      >
        跳过
      </button>
      <button
        type="button"
        class="wiz__go"
        :disabled="!canAdvance || startingInterview"
        @click="onPrimary"
      >
        {{ primaryLabel }}
      </button>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileJobPicker from '../components/MobileJobPicker.vue'
import MobileResumeStep from '../components/MobileResumeStep.vue'
import MobileTrainingGoals from '../components/MobileTrainingGoals.vue'
import MobileConfirmLaunch from '../components/MobileConfirmLaunch.vue'
import {
  getJobList, getModules, getResumeFileProfile, getSkillTags,
  startInterview, updateResumeTags, uploadResumeFile,
} from '../../api'
import { mapJobFromBackend, isReadyJob } from '../../utils/jobs'

/* ------------------------------------------------------------------ *
 *  这一页替换桌面版 views/JobSelect.vue 在手机端的渲染。
 *  业务规则逐条对齐 JobSelect：模块最多 5 个、权重 [30,25,20,15,10]、
 *  模块默认选前 5 个且 level=2、startInterview 的入参与回跳 query 完全一致。
 *  差异只在交互形态：拖拽排序 → 上/下箭头；52px 滚轮 → 预设 + 5 分钟步进。
 * ------------------------------------------------------------------ */

const STEPS = [
  { title: '选择岗位', hint: '挑一个目标岗位' },
  { title: '上传简历', hint: '不传也能继续' },
  { title: '训练目标', hint: '选 5 项能力' },
  { title: '确认信息', hint: '核对后开始' },
]

const route = useRoute()
const router = useRouter()

const currentStep = ref(0)
const fromAi = ref(false)

/* ---- 岗位 ---- */
const jobs = ref([])
const jobsLoading = ref(false)
const jobsError = ref('')
const selectedJob = ref(null)
const jobQuery = ref('')
const jobFamily = ref('')
/** URL 带过来的 job code，等岗位列表回来后再落实 */
let pendingJobCode = ''

/* ---- 简历 ---- */
const uploadedFile = ref(null)
const savedResume = ref(null)
const extractedSkills = ref([])
const resumeProjectCount = ref(0)
const resumeUploading = ref(false)
const resumeError = ref('')
const allTags = ref([])
const tagsLoading = ref(false)
const tagsError = ref('')

/* ---- 训练目标 ---- */
const allModules = ref([])
const modulesLoading = ref(false)
const modulesError = ref('')
const selectedModuleCodes = ref(new Set())
const moduleOrder = ref([])
const moduleLevels = ref({})
const activeModuleCode = ref('')
const moduleFeedback = ref('')

/* ---- 时长与提交 ---- */
const durationSeconds = ref(1800)
const startingInterview = ref(false)
const startError = ref('')

const uploadedName = computed(() => uploadedFile.value?.name || '')

const canAdvance = computed(() => {
  if (currentStep.value === 0) return Boolean(selectedJob.value)
  // 训练目标必须是 5 项：后端按 rank 1-5 权重出题，少了会拿到不满的权重表
  if (currentStep.value === 2) return moduleOrder.value.length === 5
  return true
})

const primaryLabel = computed(() => {
  if (currentStep.value === 3) return startingInterview.value ? '正在创建面试…' : '开始面试'
  return '下一步'
})

/** 确认页要的 [{code, name, levelLabel, weight}]，顺序即权重顺序 */
const confirmGoals = computed(() =>
  moduleOrder.value.map((code, index) => ({
    code,
    name: allModules.value.find((m) => m.code === code)?.name || code,
    levelLabel: ['基础', '进阶', '挑战'][(moduleLevels.value[code] ?? 2) - 1],
    weight: [30, 25, 20, 15, 10][index],
  }))
)

/* ------------------------------------------------------------------ */
/*  步骤流转                                                           */
/* ------------------------------------------------------------------ */
function goBack() {
  if (currentStep.value > 0) {
    currentStep.value -= 1
    return
  }
  // 全屏向导没挂底部导航，自己给条退路
  if (window.history.length > 1) router.back()
  else router.push('/home')
}

/** 与对话页右上角的「手动录入」互为出口：这边是手动那条路，给条回对话的口子 */
function goAiPrep() {
  router.push('/interview/ai')
}

function onPrimary() {
  if (!canAdvance.value || startingInterview.value) return
  if (currentStep.value < 3) {
    currentStep.value += 1
    return
  }
  handleStartInterview()
}

/* ------------------------------------------------------------------ */
/*  岗位                                                               */
/* ------------------------------------------------------------------ */
async function fetchJobs() {
  jobsLoading.value = true
  jobsError.value = ''
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
    applyPendingJob()
  } catch (error) {
    console.error('Failed to load jobs:', error)
    jobsError.value = '请检查网络后重试'
  } finally {
    jobsLoading.value = false
  }
}

/** 落实 URL 里的 job=<code>。列表是异步的，所以放在这里而不是 onMounted */
function applyPendingJob() {
  if (!pendingJobCode) return
  const hit = jobs.value.find((j) => j.code === pendingJobCode)
  pendingJobCode = ''
  if (hit && isReadyJob(hit)) selectedJob.value = hit
}

/* ------------------------------------------------------------------ */
/*  简历                                                               */
/* ------------------------------------------------------------------ */
function selectResumeFile(file) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'doc', 'docx'].includes(extension)) {
    uploadedFile.value = null
    resumeError.value = '请上传 PDF、DOC 或 DOCX 格式的简历'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    uploadedFile.value = null
    resumeError.value = '简历文件不能超过 10MB'
    return
  }
  uploadedFile.value = file
  resumeError.value = ''
  simulateExtract()
}

/** 与 JobSelect.vue:963 同一个接口、同一套 skills/keywords 合并规则 */
function simulateExtract() {
  resumeUploading.value = true
  resumeError.value = ''
  uploadResumeFile(uploadedFile.value)
    .then((data) => {
      const skills = []
      if (data.skills) skills.push(...data.skills)
      if (data.keywords) {
        data.keywords.forEach((k) => { if (!skills.includes(k)) skills.push(k) })
      }
      extractedSkills.value = skills
      resumeProjectCount.value = Array.isArray(data.projects) ? data.projects.length : 0
      savedResume.value = data
    })
    .catch((e) => {
      console.error('Resume upload failed:', e)
      resumeError.value = '简历解析失败，请重试'
    })
    .finally(() => {
      resumeUploading.value = false
    })
}

async function fetchSavedResume() {
  try {
    const data = await getResumeFileProfile()
    if (data?.filename) savedResume.value = data
  } catch (_) {
    // 没有历史简历是正常情况
  }
}

function useSavedResume() {
  if (!savedResume.value?.filename) return
  uploadedFile.value = { name: savedResume.value.filename, size: 0, existing: true }
  extractedSkills.value = [...(savedResume.value.skills || [])]
  resumeProjectCount.value = savedResume.value.projects?.length || 0
  resumeError.value = ''
}

function removeFile() {
  uploadedFile.value = null
  extractedSkills.value = []
  resumeProjectCount.value = 0
  resumeError.value = ''
}

/** 在线简历与上传文件同构：都落到同一张 resume 表 */
function onOnlineResumeSaved(payload) {
  uploadedFile.value = { name: '在线简历', size: 0, existing: true }
  extractedSkills.value = [...(payload?.skills || [])]
  resumeProjectCount.value = 0
  resumeError.value = ''
}

function removeSkill(index) {
  extractedSkills.value.splice(index, 1)
  // 同步回后端，否则删掉的标签只活在界面上，出题仍然会用到
  syncTags(extractedSkills.value)
}

function toggleTag(name) {
  if (!name) return
  const next = [...extractedSkills.value]
  const index = next.indexOf(name)
  if (index >= 0) next.splice(index, 1)
  else next.push(name)
  extractedSkills.value = next
  syncTags(next)
}

function syncTags(tags) {
  updateResumeTags(tags).catch((error) => {
    console.error('Failed to sync resume tags:', error)
    resumeError.value = '技能标签保存失败，请重试'
  })
}

async function fetchSkillTags() {
  if (allTags.value.length || tagsLoading.value) return
  tagsLoading.value = true
  tagsError.value = ''
  try {
    const data = await getSkillTags()
    allTags.value = Array.isArray(data) ? data.filter((tag) => tag?.name) : []
  } catch (error) {
    console.error('Failed to load skill tags:', error)
    tagsError.value = '标签库加载失败，请重试'
  } finally {
    tagsLoading.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  训练目标                                                           */
/* ------------------------------------------------------------------ */
async function fetchModules() {
  modulesLoading.value = true
  modulesError.value = ''
  try {
    const data = await getModules()
    allModules.value = Array.isArray(data) ? data : []
    // 与 JobSelect.vue:1143 一致：默认选前 5 个，深度默认「进阶」
    if (!selectedModuleCodes.value.size) {
      const defaults = allModules.value.slice(0, 5).map((m) => m.code)
      selectedModuleCodes.value = new Set(defaults)
      moduleOrder.value = defaults
      moduleLevels.value = Object.fromEntries(defaults.map((code) => [code, 2]))
      activeModuleCode.value = defaults[0] || ''
    }
  } catch (error) {
    console.error('Failed to load modules:', error)
    modulesError.value = '请检查网络后重试'
  } finally {
    modulesLoading.value = false
  }
}

function toggleModule(code) {
  moduleFeedback.value = ''
  const next = new Set(selectedModuleCodes.value)
  if (next.has(code)) {
    next.delete(code)
    moduleOrder.value = moduleOrder.value.filter((item) => item !== code)
  } else if (next.size < 5) {
    next.add(code)
    moduleOrder.value.push(code)
    moduleLevels.value = { ...moduleLevels.value, [code]: moduleLevels.value[code] || 2 }
    activeModuleCode.value = code
  } else {
    moduleFeedback.value = '最多选择 5 项，请先取消一个已选目标。'
  }
  selectedModuleCodes.value = next
  if (!next.has(activeModuleCode.value)) activeModuleCode.value = moduleOrder.value[0] || ''
}

/** 与 JobSelect.vue:1079 的 moveModule 同一份纯数组交换，只是触发源从拖拽换成箭头 */
function moveModule(index, direction) {
  const target = index + direction
  if (target < 0 || target >= moduleOrder.value.length) return
  const next = [...moduleOrder.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  moduleOrder.value = next
}

function setLevel(code, value) {
  if (!code) return
  moduleLevels.value = { ...moduleLevels.value, [code]: value }
}

/* ------------------------------------------------------------------ */
/*  提交                                                               */
/* ------------------------------------------------------------------ */
async function handleStartInterview() {
  if (!selectedJob.value || startingInterview.value) return
  startingInterview.value = true
  startError.value = ''
  try {
    const res = await startInterview({
      jobId: selectedJob.value.id,
      durationSeconds: durationSeconds.value,
      modulePreferences: moduleOrder.value.map((code, index) => ({
        code,
        rank: index + 1,
        level: moduleLevels.value[code] || 2,
      })),
    })
    // 回跳 query 与 JobSelect.vue:1168 逐字段一致，Interview.vue 读的是同一批 key
    router.push({
      path: '/interview',
      query: {
        sessionId: String(res.sessionId),
        jobId: String(selectedJob.value.id),
        jobName: res.jobName || selectedJob.value.title,
        durationSeconds: String(res.durationSeconds || durationSeconds.value),
        questionId: res.question?.id ? String(res.question.id) : undefined,
        question: res.question?.content || undefined,
        questionType: res.question?.type || undefined,
        questionDifficulty: res.question?.difficulty || undefined,
        questionSkill: res.question?.abilityTag || undefined,
      },
    })
  } catch (error) {
    console.error('Failed to start interview:', error)
    startError.value = '面试创建失败，请检查网络后重试'
  } finally {
    startingInterview.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  入口参数                                                           */
/* ------------------------------------------------------------------ */
onMounted(() => {
  // AI 对话页跳过来时带的三个参数。桌面版 JobSelect.vue 至今没读它们，
  // 所以那边的「接力到训练目标」其实是回到第一步重来——这里补上。
  const code = String(route.query.job || '').trim()
  if (code) pendingJobCode = code

  const step = Number(route.query.step)
  if (Number.isInteger(step) && step >= 0 && step < STEPS.length) currentStep.value = step

  fromAi.value = route.query.from === 'ai'

  fetchJobs()
  fetchModules()
  // 从 AI 页过来时简历刚落库，这一步能把刚解析的结果直接读出来
  if (fromAi.value) fetchSavedResume()
})
</script>

<style scoped>
/* 全屏四段式：顶栏 / 步骤条 / 内容 / 操作条。同 MobileAiPrep 用 dvh。 */
.wiz {
  display: flex;
  height: 100vh;
  height: 100dvh;
  flex-direction: column;
  overflow: hidden;
  color: var(--m-text);
  background: var(--m-bg);
}

.wiz__bar {
  display: flex;
  flex: 0 0 auto;
  min-height: 58px;
  padding: max(8px, env(safe-area-inset-top)) 12px 8px;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, .94);
  backdrop-filter: blur(12px);
}
.wiz__back {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  color: var(--m-text);
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 50%;
}
.wiz__back svg { width: 22px; height: 22px; }
.wiz__back:active { background: var(--m-primary-soft); }

.wiz__head { flex: 1; min-width: 0; }
.wiz__head strong { display: block; font-size: 16px; line-height: 1.2; }
.wiz__head small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 11.5px; }

.wiz__step-back { flex: 0 0 auto; min-height: 36px; padding: 0 12px; color: var(--m-text-secondary); background: #f2f4f0; border: 0; border-radius: 999px; font-size: 12px; font-weight: 600; }

.wiz__track { display: flex; flex: 0 0 auto; padding: 0 14px 10px; gap: 5px; background: rgba(255, 255, 255, .94); }
.wiz__track i { height: 3px; flex: 1; background: var(--m-border); border-radius: 3px; transition: background var(--m-motion); }
.wiz__track i.is-done { background: #9bc4aa; }
.wiz__track i.is-now { background: var(--m-primary); }

/* 手动 ⇄ 对话：常驻在进度条下，四步里随时能切回对话录入 */
.wiz__mode {
  display: flex;
  flex: 0 0 auto;
  justify-content: center;
  padding: 0 14px 6px;
  background: rgba(255, 255, 255, .94);
}
.wiz__mode button {
  min-height: 28px;
  padding: 0 14px;
  color: var(--m-primary);
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.wiz__mode button:active { opacity: .75; }

.wiz__body {
  flex: 1 1 auto;
  padding: 16px 16px 20px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.wiz__tip {
  margin-bottom: 14px;
  padding: 11px 13px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border-radius: 10px;
  font-size: 12.5px;
  line-height: 1.55;
}

.wiz__cta {
  display: flex;
  flex: 0 0 auto;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--m-border);
}
.wiz__go {
  flex: 1;
  min-height: 48px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 8px 18px rgba(23, 75, 57, .16);
}
.wiz__go:active:not(:disabled) { transform: scale(.99); background: var(--m-primary-dark); }
.wiz__go:disabled { color: #fff; background: #b6c9bd; box-shadow: none; }
.wiz__skip {
  flex: 0 0 auto;
  min-height: 48px;
  padding: 0 20px;
  color: var(--m-text-secondary);
  background: #f2f4f0;
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 14px;
  font-weight: 600;
}
</style>
