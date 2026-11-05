<!--
  查缺补漏：把错题按知识点聚合成可行动的入口。
  一级是「哪个知识点欠得最多」，二级是题量与样例题，操作只有一个：去练这个方向。
-->
<template>
  <MobileShell title="查缺补漏">
    <router-link to="/learning/library/mistakes" class="mwp-back">← 全部错题</router-link>

    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在整理待复习题" />

    <MobileState
      v-else-if="error"
      kind="error"
      title="待复习题暂时打不开"
      :description="error"
      action="重新加载"
      @action="load"
    />

    <MobileState
      v-else-if="!groups.length"
      title="还没有待补的知识点"
      description="错题会按知识点聚到这里。"
      action="去刷题"
      @action="router.push('/learning')"
    />

    <template v-else>
      <p class="mwp-note">共 {{ total }} 道待复习题</p>
      <section v-for="group in groups" :key="group.topic" class="mwp-card">
        <header>
          <h2>{{ group.topic }}</h2>
          <span>{{ group.items.length }} 题</span>
        </header>
        <ul>
          <li v-for="item in group.items.slice(0, 2)" :key="item.id">{{ item.title }}</li>
        </ul>
        <button type="button" :disabled="startingTopic === group.topic" @click="start(group)">
          {{ startingTopic === group.topic ? '正在组题…' : '去练这个方向 →' }}
        </button>
      </section>
      <p v-if="actionError" class="mwp-error" role="alert">{{ actionError }}</p>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTrainingSession, getPracticeLibrary, loadLearningResources } from '../../services/learningResources'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const items = ref([])
const startingTopic = ref('')
const actionError = ref('')

const total = computed(() => items.value.length)
const groups = computed(() => {
  const map = new Map()
  for (const item of items.value) {
    const topic = String(item.topic || '未分类')
    if (!map.has(topic)) map.set(topic, [])
    map.get(topic).push(item)
  }
  return [...map.entries()]
    .map(([topic, list]) => ({ topic, items: list }))
    .sort((a, b) => b.items.length - a.items.length)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await getPracticeLibrary('mistakes', { page: 1, size: 100 })
    items.value = Array.isArray(result?.items) ? result.items : []
  } catch (e) {
    error.value = e?.message || '待复习题暂时打不开，请重试。'
  } finally {
    loading.value = false
  }
}

async function start(group) {
  if (startingTopic.value) return
  startingTopic.value = group.topic
  actionError.value = ''
  try {
    const data = await loadLearningResources()
    const role = data.preferredRole || data.currentRole || data.roles?.[0]
    if (!role) throw new Error('还没有可用岗位，请先选择目标岗位。')
    const session = await createTrainingSession({
      role,
      company: data.preferredCompany || '不限公司',
      topic: group.topic,
      questionType: 'choice',
      questionCount: 5,
      difficulty: 'medium',
    })
    router.push(`/learning/session/${session.id}`)
  } catch (e) {
    actionError.value = e?.message || '这个方向暂时组不出题，可以先去专项刷题手动作一组。'
  } finally {
    startingTopic.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.mwp-back { display: inline-flex; min-height: 44px; align-items: center; color: var(--m-primary); font-size: 13px; font-weight: 600; }
.mwp-note { margin: 4px 0 14px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.7; }
.mwp-card {
  padding: 16px;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  box-shadow: 0 2px 10px rgba(32, 61, 48, .035);
  animation: mwp-rise 400ms cubic-bezier(.16, 1, .3, 1) backwards;
}
.mwp-card + .mwp-card { margin-top: 12px; }
.mwp-card header { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.mwp-card h2 { font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.mwp-card header span { flex: 0 0 auto; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 999px; padding: 3px 9px; font-size: 11px; font-weight: 700; }
.mwp-card ul { display: grid; margin: 12px 0 0; padding: 0; gap: 6px; list-style: none; }
.mwp-card li { position: relative; padding-left: 14px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.65; }
.mwp-card li::before { position: absolute; left: 0; color: var(--m-accent); content: '·'; font-weight: 800; }
.mwp-card button {
  width: 100%;
  min-height: 44px;
  margin-top: 14px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 13px;
  font-weight: 700;
  transition: transform var(--m-motion) ease, filter var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mwp-card button:active { transform: scale(.98); filter: brightness(.96); }
.mwp-card button:disabled { cursor: wait; opacity: .72; }
.mwp-error { margin-top: 14px; color: #b42318; font-size: 12.5px; line-height: 1.6; }
@keyframes mwp-rise {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .mwp-card { animation: none; }
  .mwp-card button { transition: none; }
}
</style>
