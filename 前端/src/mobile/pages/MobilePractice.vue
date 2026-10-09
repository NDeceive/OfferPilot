<template>
  <MobileShell title="专项刷题" subtitle="围绕岗位和薄弱点，选择下一项训练">
    <label class="mobile-search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg><span class="sr-only">搜索知识点</span>
      <input v-model.trim="query" type="search" placeholder="搜索知识点，如：JVM、Redis、MySQL" />
    </label>

    <MobileSkeleton v-if="loading" variant="card" :rows="3" label="正在加载学习资源" />
    <template v-else>
      <section class="mobile-role-card">
        <span class="mobile-square-icon">岗</span>
        <div class="mobile-grow"><small>当前岗位</small><strong>{{ currentRole?.name || '尚未选择岗位' }}</strong></div>
        <router-link to="/jobs">切换 →</router-link>
      </section>

      <section v-if="recentSession" class="mobile-section">
        <header class="mobile-section-heading"><h2>继续训练</h2></header>
        <button class="mobile-progress-card" type="button" @click="continueTraining">
          <span class="mobile-card-icon">练</span>
          <div class="mobile-grow"><strong>{{ recentSession.topic || '上次训练' }}</strong><div class="mobile-progress"><i :style="{ width: progress + '%' }"></i></div></div>
          <small>{{ recentSession.completed || 0 }} / {{ recentSession.questionCount || 20 }}</small>
        </button>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>知识体系</h2><span>选择后开始训练</span></header>
        <div class="mobile-chip-row" role="list">
          <button v-for="topic in visibleTopics" :key="topic" type="button" :class="{ active: topic === selectedTopic }" @click="selectedTopic = topic">{{ topic }}</button>
        </div>
        <div v-if="visibleTopics.length" class="mobile-topic-list">
          <button v-for="(topic, index) in visibleTopics.slice(0, 6)" :key="topic" type="button" @click="startTopic(topic)">
            <span class="mobile-topic-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="mobile-grow"><strong>{{ topic }}</strong><small>围绕 {{ topic }} 核心考点练习</small></div><b>›</b>
          </button>
        </div>
        <MobileState v-else title="没有匹配的知识点" description="换个关键词试试。" />
      </section>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTrainingSession, getTopics, loadLearningResources } from '../../services/learningResources'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'

const router = useRouter()
const loading = ref(true)
const query = ref('')
const roles = ref([])
const currentRole = ref(null)
const preferredCompany = ref('')
const recentSession = ref(null)
const selectedTopic = ref('')
const topics = computed(() => getTopics(currentRole.value))
const visibleTopics = computed(() => topics.value.filter(topic => topic.toLowerCase().includes(query.value.toLowerCase())))
const progress = computed(() => Math.min(100, ((recentSession.value?.completed || 0) / (recentSession.value?.questionCount || 20)) * 100))

onMounted(async () => {
  const data = await loadLearningResources()
  roles.value = data.roles
  currentRole.value = data.preferredRole || data.currentRole || roles.value.find(role => role.code === 'BE-JAVA') || roles.value[0] || null
  preferredCompany.value = data.preferredCompany || ''
  recentSession.value = data.recentSession
  selectedTopic.value = getTopics(currentRole.value)[0] || ''
  loading.value = false
})

function continueTraining() { router.push(`/learning/session/${recentSession.value.id}`) }
function startTopic(topic) {
  const session = createTrainingSession({ role: currentRole.value, company: preferredCompany.value || '不限公司', topic, trainingMode: 'interview', questionType: topic === '编程与算法' ? 'coding' : 'knowledge', questionCount: 20, difficulty: 'medium' })
  router.push(`/learning/session/${session.id}`)
}
</script>
