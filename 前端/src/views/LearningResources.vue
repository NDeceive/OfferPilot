<!--
THESIS: Learning resources is a target-setting workspace, not a browsable content warehouse.
OWN-WORLD: Existing OfferPilot warm-white canvas, mint selection states, restrained orange difficulty signal, and dense operational rows.
STORY: Reuse the current role when available, choose role/company/topic progressively, confirm training mode, then start or resume.
FIRST VIEWPORT: Context shortcut above a 70/30 target builder and live summary, with compact personal-library links at the foot.
FORM: Reference-led product workspace; one expanding step at a time preserves focus without splitting the flow across pages.
-->
<template>
  <AppLayout>
    <main class="learning-page">
      <header class="page-head">
        <div><h1>专项刷题</h1><p>围绕目标岗位、目标公司与专项知识方向，建立更有针对性的练习目标。</p></div>
        <span class="role-count">已接入 <b>{{ roles.length }}</b> 个标准岗位</span>
      </header>

      <section v-if="currentContext" class="context-bar">
        <JobLogo class="context-job-logo" v-bind="rolePresentation(currentContext)" />
        <div><small>根据当前训练岗位快速开始</small><strong>{{ currentContext.name }}</strong><span>最近重点：{{ currentFocus || '岗位知识与技术深度' }}</span></div>
        <nav aria-label="快速开始">
          <router-link to="/jobs">面试押题</router-link>
          <router-link to="/jobs">准备新岗位</router-link>
          <button type="button" @click="useCurrentContext">开始岗位专项 →</button>
        </nav>
      </section>

      <section v-if="loading" class="loading" aria-live="polite">正在载入标准岗位与练习配置…</section>
      <div v-else class="workspace">
        <section class="builder-panel">
          <h2>构建刷题目标</h2>

          <article class="builder-step" :class="{ compact: activeStep > 1 }">
            <header>
              <b>1</b><div><strong>选择岗位</strong><small>从岗位族选择目标岗位</small></div>
              <button v-if="activeStep > 1" type="button" @click="activeStep = 1">修改</button>
            </header>
            <p v-if="activeStep > 1" class="step-summary"><JobLogo v-bind="rolePresentation(selectedRole)" />✓ {{ selectedRole?.name }}</p>
            <div v-else class="role-picker">
              <nav class="family-list" aria-label="岗位族">
                <button v-for="family in families" :key="family.code" type="button" :class="{ active: selectedFamily === family.code }" @click="selectFamily(family.code)">
                  <span>{{ family.name }}</span><small>{{ family.count }}</small>
                </button>
              </nav>
              <div class="role-grid">
                <button v-for="role in visibleRoles" :key="role.code" type="button" :class="{ selected: selectedRole?.code === role.code }" @click="selectRole(role)">
                  <JobLogo v-bind="rolePresentation(role)" /><span class="role-copy"><strong>{{ role.name }}</strong><span>{{ role.summary }}</span></span><i v-if="selectedRole?.code === role.code">✓</i>
                </button>
              </div>
            </div>
          </article>

          <article class="builder-step" :class="{ compact: activeStep > 2, locked: !selectedRole }">
            <header>
              <b>2</b><div><strong>目标公司（可选）</strong><small>公司只影响题目排序与高频标记</small></div>
              <button v-if="activeStep > 2" type="button" @click="activeStep = 2">修改</button>
            </header>
            <p v-if="activeStep > 2" class="step-summary"><CompanyLogo :name="selectedCompany" />✓ {{ selectedCompany }}</p>
            <div v-else-if="activeStep === 2" class="company-picker">
              <button v-if="selectedCompany" class="saved-company" type="button" @click="selectCompany(selectedCompany)">继续使用目标公司：{{ selectedCompany }} →</button>
              <label><span aria-hidden="true">⌕</span><input v-model="companyQuery" type="search" placeholder="搜索目标公司" /></label>
              <div class="company-list">
                <button v-for="company in filteredCompanies" :key="company.name" type="button" :class="{ selected: selectedCompany === company.name }" @click="selectCompany(company.name)"><CompanyLogo :name="company.name" />{{ company.name }}</button>
              </div>
            </div>
          </article>

          <article class="builder-step" :class="{ locked: !selectedCompany }">
            <header><b>3</b><div><strong>选择专项</strong><small>专项由当前标准岗位动态生成</small></div></header>
            <div v-if="activeStep >= 3" class="topic-grid">
              <button v-for="topic in topics" :key="topic" type="button" :class="{ selected: selectedTopic === topic }" @click="selectedTopic = topic">
                <strong>{{ topic }}</strong><small>{{ topic === '综合高频' ? '面试高频题目精选' : topicHint(topic) }}</small><i v-if="selectedTopic === topic">✓</i>
              </button>
            </div>
          </article>

          <section class="training-options" v-if="selectedTopic">
            <div><strong>训练方式</strong><div class="segmented"><button type="button" :class="{ active: trainingMode === 'normal' }" @click="trainingMode = 'normal'">普通刷题</button><button type="button" :class="{ active: trainingMode === 'interview' }" @click="trainingMode = 'interview'">面试式训练</button></div></div>
            <div><strong>题型</strong><div class="segmented"><button v-for="type in questionTypes" :key="type" type="button" :class="{ active: questionType === type }" @click="questionType = type">{{ typeLabel(type) }}</button></div></div>
          </section>
        </section>

        <aside class="summary-column">
          <section class="summary-card">
            <header><span aria-hidden="true">◎</span><h2>当前刷题目标</h2></header>
            <dl>
              <div><dt>岗位</dt><dd><JobLogo v-if="selectedRole" v-bind="rolePresentation(selectedRole)" />{{ selectedRole?.name || '待选择' }}</dd></div>
              <div><dt>公司</dt><dd><CompanyLogo v-if="selectedCompany" :name="selectedCompany" />{{ selectedCompany || '待选择' }}</dd></div>
              <div><dt>专项</dt><dd>{{ selectedTopic || '待选择' }}</dd></div>
              <div><dt>训练方式</dt><dd>{{ trainingMode === 'interview' ? '面试式训练' : '普通刷题' }}</dd></div>
              <div><dt>题型</dt><dd>{{ typeLabel(questionType) }}</dd></div>
              <div><dt>预计题量</dt><dd>{{ questionCount }} 题</dd></div>
              <div><dt>难度</dt><dd><em>{{ difficultyLabel }}</em></dd></div>
            </dl>
            <button class="start-button" type="button" :disabled="!canStart" @click="startTraining">开始{{ trainingMode === 'interview' ? '面试式训练' : '专项刷题' }} →</button>
            <button class="settings-button" type="button" :aria-expanded="showSettings" @click="showSettings = !showSettings">调整题量 / 难度</button>
            <div v-if="showSettings" class="settings-panel">
              <span>题量</span><div><button v-for="count in [10,20,30,50]" :key="count" type="button" :class="{ active: questionCount === count }" @click="questionCount = count">{{ count }}</button></div>
              <span>难度</span><div><button v-for="item in difficulties" :key="item.value" type="button" :class="{ active: difficulty === item.value }" @click="difficulty = item.value">{{ item.label }}</button></div>
            </div>
          </section>

          <section v-if="recentSession" class="resume-card">
            <header><h3>继续上次练习</h3><router-link to="/learning">查看全部 ›</router-link></header>
            <strong>{{ recentSession.role?.name }} · {{ recentSession.topic }}专项</strong><span>{{ recentSession.trainingMode === 'interview' ? '面试式训练' : '普通刷题' }}</span>
            <div><p>已完成 <b>{{ recentSession.completed || 0 }}</b> / {{ recentSession.questionCount }}</p><router-link :to="`/learning/session/${recentSession.id}`">继续 →</router-link></div>
            <progress :value="recentSession.completed || 0" :max="recentSession.questionCount"></progress>
          </section>
        </aside>
      </div>

      <nav class="library-links" aria-label="个人题库">
        <a href="#mistakes"><strong>错题本</strong><span>{{ libraryCounts.mistakes }} 题 ›</span></a>
        <a href="#favorites"><strong>我的收藏</strong><span>{{ libraryCounts.favorites }} 题 ›</span></a>
        <a href="#recent"><strong>最近练习</strong><span>{{ libraryCounts.recent }} 个题单 ›</span></a>
      </nav>
    </main>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import JobLogo from '../components/jobs/JobLogo.vue'
import CompanyLogo from '../components/learning/CompanyLogo.vue'
import { getJobPresentation } from '../utils/jobPresentation'
import { companies, createTrainingSession, getTopics, loadLearningResources, topicQuestionTypes } from '../services/learningResources'

const router = useRouter()
const loading = ref(true)
const roles = ref([])
const families = ref([])
const currentContext = ref(null)
const currentFocus = ref('')
const recentSession = ref(null)
const libraryCounts = ref({ mistakes: 0, favorites: 0, recent: 0 })
const selectedFamily = ref('BE')
const selectedRole = ref(null)
const selectedCompany = ref('')
const selectedTopic = ref('')
const activeStep = ref(1)
const companyQuery = ref('')
const trainingMode = ref('interview')
const questionType = ref('knowledge')
const questionCount = ref(20)
const difficulty = ref('medium')
const showSettings = ref(false)
const difficulties = [{ value: 'all', label: '全部' }, { value: 'basic', label: '基础' }, { value: 'medium', label: '中等' }, { value: 'advanced', label: '进阶' }]

const visibleRoles = computed(() => roles.value.filter(role => role.code?.startsWith(`${selectedFamily.value}-`)))
const filteredCompanies = computed(() => companies.filter(item => item.name.includes(companyQuery.value.trim())).slice(0, 8))
const topics = computed(() => getTopics(selectedRole.value))
const questionTypes = computed(() => topicQuestionTypes(selectedTopic.value))
const canStart = computed(() => selectedRole.value && selectedCompany.value && selectedTopic.value)
const difficultyLabel = computed(() => difficulties.find(item => item.value === difficulty.value)?.label + (difficulty.value === 'medium' ? '为主' : ''))

watch(selectedTopic, topic => { questionType.value = topic === '编程与算法' ? 'coding' : 'knowledge' })

onMounted(async () => {
  const data = await loadLearningResources()
  roles.value = data.roles
  families.value = data.families
  currentContext.value = data.currentRole
  currentFocus.value = data.currentFocus
  recentSession.value = data.recentSession
  libraryCounts.value = data.libraryCounts
  selectedRole.value = data.preferredRole || roles.value.find(role => role.code === 'BE-JAVA') || roles.value[0]
  selectedFamily.value = selectedRole.value?.code?.split('-')[0] || 'BE'
  selectedCompany.value = data.preferredCompany || ''
  loading.value = false
})

function selectFamily(code) { selectedFamily.value = code; selectedRole.value = null }
function selectRole(role) { selectedRole.value = role; activeStep.value = 2 }
function selectCompany(name) { selectedCompany.value = name; activeStep.value = 3; if (!selectedTopic.value) selectedTopic.value = topics.value[0] }
function useCurrentContext() { selectedRole.value = currentContext.value; selectedFamily.value = currentContext.value.code.split('-')[0]; selectedCompany.value = '不限公司'; selectedTopic.value = getTopics(currentContext.value)[0]; activeStep.value = 3 }
function typeLabel(value) { return ({ all: '全部', knowledge: '知识问答', coding: '编程题' })[value] || '知识问答' }
function topicHint(topic) { return topic === '编程与算法' ? '算法、数据结构与现场实现' : `聚焦 ${topic} 核心考点` }
function rolePresentation(role) { const item = getJobPresentation(role || {}); return { iconKey: item.iconKey, tone: item.themeKey } }
function startTraining() {
  if (!canStart.value) return
  const session = createTrainingSession({ role: selectedRole.value, company: selectedCompany.value, topic: selectedTopic.value, trainingMode: trainingMode.value, questionType: questionType.value, questionCount: questionCount.value, difficulty: difficulty.value })
  router.push(`/learning/session/${session.id}`)
}
</script>

<style scoped>
.saved-company{min-height:44px;padding:8px 14px;margin-bottom:10px;border:1px solid var(--accent-200);border-radius:8px;background:var(--accent-50);color:var(--accent-700);cursor:pointer}
.learning-page{width:min(1370px,calc(100vw - 48px));margin:0 auto;padding:16px 0 28px;color:#17231f}.page-head{display:flex;align-items:end;justify-content:space-between;margin-bottom:14px}.page-head h1{font-size:27px;line-height:1.2}.page-head p{margin-top:5px;color:#6d7a75;font-size:13px}.role-count{padding:7px 12px;border:1px solid #e0e7e4;border-radius:999px;background:#fff;color:#6d7a75;font-size:11px}.role-count b{color:#12855c}.context-bar{min-height:88px;margin-bottom:14px;padding:16px 18px;display:grid;grid-template-columns:48px 1fr auto;align-items:center;gap:14px;border:1px solid #cfe9df;border-radius:13px;background:var(--surface-mint)}:deep(.context-job-logo.job-logo){width:44px;height:44px;border-radius:11px}:deep(.context-job-logo.job-logo img),:deep(.context-job-logo.job-logo svg){width:28px;height:28px}.context-bar>div:nth-child(2){display:grid}.context-bar small,.context-bar span{color:#708079;font-size:10px}.context-bar strong{font-size:16px}.context-bar nav{display:flex;gap:10px}.context-bar a,.context-bar button{padding:10px 17px;border:1px solid #dce6e2;border-radius:8px;background:#fff;color:#34453f;font-size:11px;font-weight:650}.context-bar button{min-width:170px;border-color:var(--accent-500);background:var(--accent-500);color:#fff}.workspace{display:grid;grid-template-columns:minmax(0,2.25fr) minmax(310px,.78fr);gap:14px}.builder-panel,.summary-card,.resume-card{border:1px solid #dde6e2;border-radius:14px;background:#fff}.builder-panel{padding:16px 18px}.builder-panel>h2,.summary-card h2{font-size:18px}.builder-step{margin-top:12px;padding-top:12px;border-top:1px solid #e7ecea}.builder-step>header{display:flex;align-items:center;gap:10px}.builder-step>header>b{display:grid;width:26px;height:26px;place-items:center;border-radius:50%;background:var(--accent-500);color:#fff;font-size:12px}.builder-step>header>div{display:grid}.builder-step>header strong{font-size:13px}.builder-step>header small{color:#85908c;font-size:10px}.builder-step>header button{margin-left:auto;border:0;background:transparent;color:#168e64;font-size:11px;font-weight:700}.step-summary{margin:8px 0 0 36px;display:flex;align-items:center;gap:7px;color:#178d63;font-size:12px;font-weight:700}.step-summary:deep(.job-logo),.summary-card dd:deep(.job-logo){width:28px;height:28px;border-radius:8px}.step-summary:deep(.job-logo img),.step-summary:deep(.job-logo svg),.summary-card dd:deep(.job-logo img),.summary-card dd:deep(.job-logo svg){width:18px;height:18px}.builder-step.compact{padding-bottom:2px}.builder-step.locked{opacity:.46}.role-picker{display:grid;grid-template-columns:165px 1fr;gap:18px;margin-top:12px}.family-list{display:grid;align-content:start;gap:3px;padding-right:12px;border-right:1px solid #e6ebe9}.family-list button{min-height:34px;padding:0 10px;display:flex;align-items:center;justify-content:space-between;border:0;border-radius:7px;background:transparent;color:#52615c;font-size:11px}.family-list button.active{background:#eaf8f2;color:#0f8c60;font-weight:700}.role-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.role-grid button,.topic-grid button{position:relative;min-height:64px;padding:8px 10px;text-align:left;border:1px solid #dfe6e3;border-radius:8px;background:#fff;color:#1d2b26}.role-grid button{display:flex;align-items:center;gap:9px}.role-grid button:deep(.job-logo){width:38px;height:38px;border-radius:10px}.role-grid button:deep(.job-logo img),.role-grid button:deep(.job-logo svg){width:25px;height:25px}.role-copy{min-width:0;display:grid}.role-grid button.selected,.topic-grid button.selected{border-color:#24b984;background:#f5fcf9}.role-grid strong,.topic-grid strong{font-size:12px}.role-grid .role-copy>span,.topic-grid small{margin-top:3px;color:#77847f;font-size:9px}.role-grid i,.topic-grid i{position:absolute;right:9px;top:9px;display:grid;width:17px;height:17px;place-items:center;border-radius:50%;background:#18a873;color:#fff;font-size:9px;font-style:normal}.company-picker{margin:12px 0 2px 36px}.company-picker label{height:38px;padding:0 12px;display:flex;align-items:center;gap:9px;border:1px solid #dbe4e0;border-radius:8px}.company-picker input{width:100%;border:0;outline:0;font:inherit;font-size:11px}.company-list{display:flex;flex-wrap:wrap;gap:7px;margin-top:10px}.company-list button,.segmented button,.settings-panel button{padding:5px 10px;border:1px solid #dce5e1;border-radius:999px;background:#fff;color:#53625c;font-size:10px}.company-list button{display:inline-flex;align-items:center;gap:7px;padding-left:6px}.company-list button.selected,.segmented button.active,.settings-panel button.active{border-color:#22b47f;background:#eaf8f2;color:#087b53}.topic-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:12px}.topic-grid button{display:grid;align-content:center}.training-options{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:14px;padding:13px;border-radius:10px;background:#f7faf8}.training-options>div{display:flex;align-items:center;justify-content:space-between;gap:12px}.training-options strong{font-size:11px}.segmented{display:flex;gap:5px}.summary-column{display:grid;align-content:start;gap:12px}.summary-card,.resume-card{padding:17px}.summary-card>header{display:flex;align-items:center;gap:8px;padding-bottom:12px;border-bottom:1px solid #e5ebe8}.summary-card>header span{color:#10a873;font-size:21px}.summary-card dl{display:grid;gap:0;margin:10px 0}.summary-card dl>div{display:grid;grid-template-columns:90px 1fr;align-items:center;min-height:38px}.summary-card dt{color:#78857f;font-size:11px}.summary-card dd{display:flex;align-items:center;gap:7px;font-size:11px;font-weight:650}.summary-card em{padding:3px 8px;border-radius:999px;background:#fff1dd;color:#ad6614;font-style:normal}.start-button,.settings-button{width:100%;min-height:40px;border-radius:8px;font-weight:700}.start-button{border:0;background:var(--accent-500);color:#fff}.start-button:disabled{cursor:not-allowed;opacity:.42}.settings-button{margin-top:8px;border:1px solid #dce5e1;background:#fff;color:#4d5f58}.settings-panel{display:grid;grid-template-columns:50px 1fr;gap:8px;margin-top:10px;padding-top:10px;border-top:1px solid #e5ebe8}.settings-panel>span{font-size:10px;color:#718079}.settings-panel>div{display:flex;gap:5px}.resume-card header,.resume-card>div{display:flex;align-items:center;justify-content:space-between}.resume-card h3{font-size:14px}.resume-card header a,.resume-card>span{color:#7a8782;font-size:10px}.resume-card>strong{display:block;margin-top:15px;font-size:12px}.resume-card>span{display:block;margin-top:3px}.resume-card>div{margin-top:12px}.resume-card p,.resume-card a{font-size:10px}.resume-card b{color:#0a9464}.resume-card progress{width:100%;height:5px;margin-top:8px;accent-color:var(--accent-500)}.library-links{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:12px}.library-links a{padding:13px 16px;display:flex;justify-content:space-between;border:1px solid #dfe6e3;border-radius:11px;background:#fff;color:#23312c}.library-links strong{font-size:12px}.library-links span{color:#168d63;font-size:10px}.loading{padding:80px;text-align:center;color:#73817c}
@media(max-width:1050px){.workspace{grid-template-columns:1fr}.summary-column{grid-template-columns:1fr 1fr}.role-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:720px){.learning-page{width:calc(100vw - 24px);padding-top:12px}.page-head{align-items:start}.role-count{display:none}.context-bar{grid-template-columns:42px 1fr}.context-bar nav{grid-column:1/-1;display:grid;grid-template-columns:1fr 1fr}.context-bar nav button{grid-column:1/-1}.role-picker{grid-template-columns:1fr}.family-list{grid-template-columns:repeat(2,1fr);border-right:0}.role-grid,.topic-grid,.summary-column,.library-links,.training-options{grid-template-columns:1fr}.training-options>div{align-items:flex-start;flex-direction:column}}
.start-button:not(:disabled):hover,.context-bar button:hover{background:var(--accent-600)}
.start-button:focus-visible,.context-bar button:focus-visible{outline:2px solid var(--accent-700);outline-offset:3px}
</style>
