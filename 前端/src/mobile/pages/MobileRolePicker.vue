<!--
  选择目标岗位：手机端不再跳桌面 /jobs。
  点击一个岗位就保存到求职档案，返回刷题页后按新岗位重新组题。
  信息层级：当前目标 > 搜索 > 岗位分组列表；整行可点，没有第二个主按钮。
-->
<template>
  <MobileShell title="选择目标岗位" subtitle="选好后，专项练习会按新岗位重新组题">
    <router-link to="/learning" class="mrpp-back">← 返回刷题</router-link>

    <section class="mrpp-current" aria-label="当前目标岗位">
      <span>当前目标</span>
      <strong>{{ currentJobName }}</strong>
      <small>点击下方任意岗位即可切换并保存</small>
    </section>

    <label class="mobile-search mrpp-search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg>
      <input v-model.trim="query" type="search" aria-label="搜索岗位" placeholder="搜岗位名称或技术方向…" />
    </label>

    <p v-if="error" class="mrpp-error" role="alert">{{ error }}</p>

    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在加载岗位列表" />

    <template v-else>
      <section v-for="group in groups" :key="group.name" class="mrpp-group" :aria-label="group.name">
        <header><h2>{{ group.name }}</h2><span>{{ group.roles.length }}</span></header>
        <div class="mrpp-list">
          <button
            v-for="job in group.roles"
            :key="job.id"
            type="button"
            class="mrpp-row"
            :class="{ active: String(job.id) === String(currentJobId) }"
            :aria-pressed="String(job.id) === String(currentJobId)"
            :disabled="savingId !== null"
            @click="choose(job)"
          >
            <span class="mrpp-check" aria-hidden="true">{{ savingId === job.id ? '…' : String(job.id) === String(currentJobId) ? '✓' : '' }}</span>
            <span class="mobile-grow">
              <strong>{{ job.name }}</strong>
              <small>{{ job.summary }}</small>
            </span>
            <span class="mrpp-state">{{ savingId === job.id ? '保存中…' : String(job.id) === String(currentJobId) ? '当前' : '选择' }}</span>
          </button>
        </div>
      </section>

      <MobileState
        v-if="!groups.length"
        :title="query ? '没有找到匹配的岗位' : '岗位列表暂时为空'"
        :description="query ? `没有和「${query}」相关的岗位，换个关键词试试。` : '请稍后重试，或检查网络后重新加载。'"
        :action="query ? '清除搜索' : '重新加载'"
        @action="handleEmptyAction"
      />
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getCareerProfile, saveCareerProfile } from '../../api'
import { loadLearningResources } from '../../services/learningResources'
import { JOB_FAMILIES } from '../../utils/jobs'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const query = ref('')
const jobs = ref([])
const currentJobId = ref(null)
const savingId = ref(null)
// PUT 是全量覆盖，切换岗位前必须拿到原档案，否则会把学校和公司意向一起清空。
const career = ref(null)

const currentJobName = computed(() => {
  const job = jobs.value.find(item => String(item.id) === String(currentJobId.value))
  return job?.name || '尚未设置'
})

const filteredJobs = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return jobs.value
  return jobs.value.filter(job => [job.name, job.family, job.summary]
    .filter(Boolean).join(' ').toLowerCase().includes(q))
})

// 后端 job.family 存的是 JOB_FAMILIES.code（如「前端与客户端开发」）；兜底岗位
// 用的是 roleSeed 的展示名（「前端与客户端」「算法与AI」）。两种都归一到 code。
const FAMILY_ALIASES = {
  '前端与客户端': '前端与客户端开发',
  '算法与AI': '算法与人工智能',
}
function canonicalFamily(value) {
  const raw = FAMILY_ALIASES[value] || value
  return JOB_FAMILIES.find(family => family.code === raw || family.name === raw)?.code || '其他'
}

const groups = computed(() => {
  const grouped = new Map()
  for (const job of filteredJobs.value) {
    const code = canonicalFamily(job.family)
    if (!grouped.has(code)) grouped.set(code, [])
    grouped.get(code).push(job)
  }
  return [...JOB_FAMILIES, { code: '其他', name: '其他' }]
    .filter(family => grouped.has(family.code))
    .map(family => ({ name: family.name, roles: grouped.get(family.code) }))
})

async function choose(job) {
  if (savingId.value !== null) return
  savingId.value = job.id
  error.value = ''
  try {
    const base = career.value || await getCareerProfile().catch(() => null)
    if (!base) throw new Error('求职档案加载失败，请稍后重试。')
    const saved = await saveCareerProfile({
      stage: '', school: '', major: '', graduationYear: '', targetCompany: '',
      ...base,
      targetJobId: job.id,
    })
    career.value = saved
    currentJobId.value = saved?.targetJobId ?? job.id
    router.replace('/learning')
  } catch (e) {
    error.value = e.message || '岗位保存失败，请稍后重试。'
  } finally {
    savingId.value = null
  }
}

function handleEmptyAction() {
  if (query.value) query.value = ''
  else load()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [data, profile] = await Promise.all([
      loadLearningResources(),
      getCareerProfile().catch(() => null),
    ])
    career.value = profile
    jobs.value = data.roles
    const matched = data.roles.find(role => String(role.id) === String(profile?.targetJobId))
    currentJobId.value = matched?.id ?? data.preferredRole?.id ?? null
  } catch {
    error.value = '岗位列表暂时无法加载，请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mrpp-back { display: inline-flex; min-height: 44px; align-items: center; color: var(--m-primary); font-size: 13px; font-weight: 600; }
.mrpp-current { display: grid; padding: 16px; gap: 5px; background: linear-gradient(145deg, var(--m-primary-soft), #fff); border: 1px solid #dcebe1; border-radius: var(--m-radius-card); }
.mrpp-current span { color: var(--m-primary); font-size: 11.5px; font-weight: 800; letter-spacing: .08em; }
.mrpp-current strong { font-size: 19px; line-height: 1.35; font-weight: 800; letter-spacing: -.02em; }
.mrpp-current small { color: var(--m-text-secondary); font-size: 12px; line-height: 1.55; }
.mrpp-search { margin-top: 16px; transition: border-color var(--m-motion) ease, box-shadow var(--m-motion) ease; }
.mrpp-search:focus-within { border-color: var(--m-primary); box-shadow: 0 0 0 3px var(--m-primary-soft); }
.mrpp-error { margin-top: 14px; padding: 10px 12px; color: #b42318; background: #fff0f1; border-radius: var(--m-radius-input); font-size: 13px; line-height: 1.6; }
.mrpp-group { margin-top: 22px; }
.mrpp-group header { display: flex; padding: 0 2px 8px; align-items: center; justify-content: space-between; }
.mrpp-group h2 { font-size: 14px; font-weight: 800; }
.mrpp-group header span { color: var(--m-text-tertiary); font-size: 11.5px; font-variant-numeric: tabular-nums; }
.mrpp-list { display: flex; flex-direction: column; gap: 8px; }
.mrpp-row {
  display: flex;
  width: 100%;
  min-height: 64px;
  padding: 12px 13px;
  align-items: center;
  gap: 10px;
  color: var(--m-text);
  text-align: left;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  transition: transform var(--m-motion) ease, border-color var(--m-motion) ease, background var(--m-motion) ease;
}
.mrpp-row:active { transform: scale(.985); }
.mrpp-row.active { background: var(--m-primary-soft); border-color: #b9dac8; }
.mrpp-row:disabled { cursor: default; opacity: .78; }
.mrpp-check {
  display: grid;
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  color: var(--m-primary-dark);
  background: var(--m-surface-soft);
  border: 1px solid #d7e8dd;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 800;
  place-items: center;
}
.mrpp-row.active .mrpp-check { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mrpp-row strong { display: block; font-size: 14px; line-height: 1.4; }
.mrpp-row small { display: block; margin-top: 3px; overflow: hidden; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.mrpp-state { flex: 0 0 auto; color: var(--m-text-tertiary); font-size: 11.5px; font-weight: 600; white-space: nowrap; }
.mrpp-row.active .mrpp-state { color: var(--m-primary); }
.mrpp-row:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }

@media (hover: hover) {
  .mrpp-row:hover { border-color: #cfe2d6; }
}

@media (prefers-reduced-motion: reduce) {
  .mrpp-row { transition: none; }
}
</style>
