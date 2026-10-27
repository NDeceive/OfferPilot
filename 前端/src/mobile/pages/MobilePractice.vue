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
        <header class="mobile-section-heading"><h2>继续上次练习</h2><router-link to="/learning/library/recent?status=ONGOING">查看全部</router-link></header>
        <button class="mobile-progress-card" type="button" @click="continueTraining">
          <span class="mobile-card-icon">练</span>
          <div class="mobile-grow"><strong>{{ recentSession.topic || '上次训练' }}</strong><div class="mobile-progress"><i :style="{ width: progress + '%' }"></i></div></div>
          <small>已提交 {{ recentSession.completed || 0 }} / {{ recentSession.questionCount }}</small>
        </button>
      </section>
      <p v-if="!overviewAvailable" role="status">练习记录暂时无法加载。<button :disabled="refreshingOverview" @click="refreshOverview">重新加载</button></p>
      <nav class="mobile-chip-row" aria-label="个人题库"><router-link to="/learning/library/mistakes">错题本</router-link><router-link to="/learning/library/favorites">我的收藏</router-link><router-link to="/learning/library/recent">最近练习</router-link></nav>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>知识体系</h2><span>选择后开始训练</span></header>
        <label class="company-choice">目标公司<select v-model="preferredCompany"><option value="">不限公司</option><option v-for="item in companies.filter(c => c.name !== '不限公司')" :key="item.name" :value="item.name">{{ item.name }}</option></select></label>
        <p v-if="catalog && !catalog.ready" role="status">练习配置暂时无法加载，暂不能创建题单。<button :disabled="refreshingCatalog" @click="refreshCatalog">重新加载配置</button></p>
        <p v-else role="status">{{ companyNote }}</p>
        <div class="mobile-chip-row" role="list">
          <button v-for="topic in visibleTopics" :key="topic" type="button" :class="{ active: topic === selectedTopic }" @click="selectedTopic = topic">{{ topic }}</button>
        </div>
        <div class="mobile-chip-row" aria-label="练习题型">
          <button v-for="type in availableTypes" :key="type" type="button" :aria-pressed="questionType === type" :class="{ active: questionType === type }" @click="questionType = type">{{ typeNames[type] }}</button>
        </div>
        <p v-if="questionType === 'coding'">编程专项本次提供 10 道 Java 题。</p>
        <p v-if="questionType === 'all'">混合题单中编程题占 20%–30%，每次至少 2 道。</p>
        <div v-if="visibleTopics.length" class="mobile-topic-list">
          <button v-for="(topic, index) in visibleTopics.slice(0, 6)" :key="topic" type="button" :disabled="starting || !catalog?.ready" @click="startTopic(topic)">
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { practiceTypeNames, objectiveQuestionCount } from '../../utils/practiceTypes'
import { companies, createTrainingSession, getTopics, loadLearningResources, topicQuestionTypes, getPracticeOverview, getPracticeCatalog } from '../../services/learningResources'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'

const router = useRouter()
const loading = ref(true)
const starting = ref(false)
const query = ref('')
const roles = ref([])
const currentRole = ref(null)
const preferredCompany = ref('')
const recentSession = ref(null)
const overviewAvailable = ref(false), refreshingOverview = ref(false)
const catalog = ref(null)
const refreshingCatalog = ref(false)
const companyNote = computed(() => {
 const name = preferredCompany.value
 if (!name || name === '不限公司') return '不限公司时按岗位和专项组题。'
 const matched = catalog.value?.companies?.some(c => c.name === name || c.aliases?.includes(name))
 return matched ? `${name}：已有公开文化资料，按相关专项匹配情境题。` : `${name}：暂无已审核专项资料，按岗位和专项组题。`
})
const selectedTopic = ref('')
const questionType = ref('all')
const typeNames = practiceTypeNames
const availableTypes = computed(() => topicQuestionTypes(selectedTopic.value, currentRole.value, catalog.value))
watch(availableTypes, types => { if (!types.includes(questionType.value)) questionType.value = types[0] })
const topics = computed(() => getTopics(currentRole.value, catalog.value))
const visibleTopics = computed(() => topics.value.filter(topic => topic.toLowerCase().includes(query.value.toLowerCase())))
const progress = computed(() => Math.min(100, ((recentSession.value?.completed || 0) / (recentSession.value?.questionCount || 20)) * 100))

onMounted(async () => {
  const data = await loadLearningResources()
  roles.value = data.roles
  catalog.value = data.catalog
  currentRole.value = data.preferredRole || data.currentRole || roles.value.find(role => role.code === 'BE-JAVA') || roles.value[0] || null
  preferredCompany.value = data.preferredCompany || ''
  recentSession.value = data.recentSession
  overviewAvailable.value = data.overviewAvailable
  selectedTopic.value = getTopics(currentRole.value, catalog.value)[0] || ''
  loading.value = false
})

function continueTraining() { router.push(`/learning/session/${recentSession.value.id}`) }
async function refreshOverview() {
  if(refreshingOverview.value) return
  refreshingOverview.value = true
  try { const data = await getPracticeOverview(); recentSession.value = data.recentSession; overviewAvailable.value = true }
  catch { overviewAvailable.value = false }
  finally { refreshingOverview.value = false }
}
async function startTopic(topic) {
  if (starting.value || !catalog.value?.ready) return
  starting.value = true
  try {
    const type = topicQuestionTypes(topic, currentRole.value, catalog.value).includes(questionType.value) ? questionType.value : topicQuestionTypes(topic, currentRole.value, catalog.value)[0]
    const count = objectiveQuestionCount(topic, currentRole.value, catalog.value, type)
    if (count === 0) throw new Error('当前专项暂无此难度的题目，请选择其他题型或综合高频。')
    const session = await createTrainingSession({ role: currentRole.value, company: preferredCompany.value || '不限公司', topic, questionType: type, questionCount: count !== null ? Math.min(20,count) : topic === '企业情境' ? 3 : type === 'coding' ? 10 : 20, difficulty: 'medium' })
    router.push(`/learning/session/${session.id}`)
  } catch (error) { alert(error.message || '题单创建失败，请稍后重试') }
  finally { starting.value = false }
}
async function refreshCatalog() {
  if(refreshingCatalog.value) return
  refreshingCatalog.value = true
  try { catalog.value = await getPracticeCatalog() } catch { catalog.value = {ready:false} }
  finally { refreshingCatalog.value = false }
}
</script>

<style scoped>
.mobile-chip-row>a{display:inline-flex;align-items:center;min-height:44px;padding:8px;color:#087c55}.mobile-section button{min-height:44px}
.company-choice{display:flex;align-items:center;gap:12px;margin:12px 0;color:#34453e}.company-choice select{flex:1;min-width:0;min-height:44px;padding:8px 12px;border:1px solid #dce5e1;border-radius:8px;background:#fff;color:#25372e;font:inherit}.company-choice select:focus-visible{outline:2px solid #059669;outline-offset:2px}
</style>
