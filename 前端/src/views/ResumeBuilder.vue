<template>
  <AppLayout>
    <main class="builder">
      <header class="builder-head">
        <div>
          <p class="eyebrow">简历工作台</p>
          <h1>用真实经历，制作能经得起追问的简历</h1>
          <p>先核实事实，再按目标岗位组织内容；每次保存都会留下独立版本。</p>
        </div>
        <button class="quiet" type="button" @click="returnToPreparation">返回面试准备</button>
      </header>

      <div v-if="error" class="notice notice--error" role="alert">{{ error }}</div>
      <section v-if="resumeAiStatus?.provider === 'deepseek'" class="resume-ai-status" aria-label="简历 AI 状态">
        <div>
          <strong>简历 AI · DeepSeek</strong>
          <p v-if="resumeAiStatus.configured">已配置 {{ resumeAiStatus.model }}。仅简历生成使用 DeepSeek；面试追问和语音识别不变。</p>
          <p v-else>等待本机后端配置 DeepSeek API Key；配置前仍可手动填写简历，事例示例使用规则模板。</p>
        </div>
      </section>
      <nav class="builder-tabs" aria-label="简历制作步骤">
        <button v-for="item in tabs" :key="item.key" type="button" :class="{ active: tab === item.key }" @click="tab = item.key">
          <span>{{ item.number }}</span>{{ item.label }}
        </button>
      </nav>

      <section v-if="tab === 'claims'" class="builder-grid">
        <div class="panel">
          <div class="panel-head"><div><h2>资料与事实台账</h2><p>“已确认”的表述才能进入自动生成的正式简历。系统提取的内容默认待确认。</p></div></div>
          <div class="import-actions">
            <input ref="resumeFileInput" type="file" accept=".pdf,.doc,.docx" hidden @change="uploadResumeForClaims" />
            <button type="button" class="quiet" :disabled="uploadingResume" @click="resumeFileInput?.click()">{{ uploadingResume ? '解析中…' : uploadedResumeName ? '更换简历' : '上传简历' }}</button>
            <button type="button" class="quiet" :disabled="loadingSuggestions" @click="loadSuggestions">{{ loadingSuggestions ? '提取中…' : '从已上传简历提取待核实条目' }}</button>
            <span>{{ uploadedResumeName ? `当前：${uploadedResumeName}` : '请先上传 PDF / Word 简历，或手动添加事实。' }}</span>
          </div>
          <p v-if="suggestionsMessage" class="notice" role="status">{{ suggestionsMessage }}</p>
          <div v-if="suggestions.length" class="suggestions">
            <h3>提取到 {{ suggestions.length }} 条草稿</h3>
            <div v-for="(item, index) in suggestions" :key="index" class="suggestion">
              <span>{{ categoryLabel(item.category) }} · {{ item.resumeText.slice(0, 100) }}</span>
              <button type="button" @click="useSuggestion(item, index)">核对并编辑</button>
            </div>
          </div>
          <form class="claim-form" @submit.prevent="saveClaim">
            <h3>{{ editingClaimId ? '编辑事实条目' : '新增事实条目' }}</h3>
            <div class="example-tools">
              <label>事例线索（选填）<input v-model="exampleBrief" maxlength="1000" placeholder="例如：参与过校园交易项目的订单模块；不填则生成通用示例" /></label>
              <button type="button" class="quiet" :disabled="loadingExample" @click="generateExample">{{ loadingExample ? '生成中…' : 'AI 生成事例示例' }}</button>
            </div>
            <p class="fineprint">简历 AI 使用{{ resumeAiProvider }}；不可用时提供通用规则模板。AI 请求只发送当前分类、目标岗位及事例线索，不发送整份简历。示例不代表你的真实经历。</p>
            <div v-if="claimExample" class="example-card" role="status">
              <strong>{{ claimExample.source === 'RULE' ? '规则写法模板（AI 暂不可用）' : 'AI 写法示例' }} · {{ claimExample.title }}</strong>
              <p><b>事例：</b>{{ claimExample.scenario }}</p>
              <p><b>简历表述：</b>{{ claimExample.resumeLine }}</p>
              <small>请根据自己的真实经历填写下方表单；此示例不会自动填入、保存或确认。</small>
            </div>
            <div class="form-row">
              <label>分类<select v-model="claimForm.category"><option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
              <label>承担程度<select v-model="claimForm.responsibility"><option value="PARTICIPATED">参与</option><option value="MODULE">负责模块</option><option value="LED">主导方案或交付</option><option value="OWNER">项目负责人</option></select></label>
            </div>
            <label>标题<input v-model="claimForm.title" maxlength="120" required placeholder="例如：校园交易项目订单模块" /></label>
            <label>原始事实<textarea v-model="claimForm.factText" rows="3" required placeholder="如实写做过什么，以及可核对的依据" /></label>
            <label>准备写进简历的表述<textarea v-model="claimForm.resumeText" rows="3" required placeholder="只写能由原始事实支撑的内容" /></label>
            <div class="ai-wording">
              <button type="button" class="quiet" :disabled="loadingAiWording || !claimForm.factText.trim()" @click="loadAiWording">{{ loadingAiWording ? 'AI 起草中…' : 'AI 起草简历表述' }}</button>
              <span>仅发送此条的标题、原始事实、承担程度和个人边界至{{ resumeAiProvider }}；建议不会自动保存。</span>
            </div>
            <div v-if="wordingSuggestion" class="suggestions" role="status">
              <strong>AI 建议，请核对：</strong><p>{{ wordingSuggestion }}</p>
              <button type="button" class="quiet" @click="applyAiWording">采用此表述</button>
            </div>
            <label>个人与团队的工作边界<textarea v-model="claimForm.personalBoundary" rows="2" placeholder="主导项目时必填：团队完成了什么，你本人完成了什么" /></label>
            <label>来源摘录<input v-model="claimForm.sourceExcerpt" placeholder="简历原文或其他可核对的材料（选填）" /></label>
            <div class="form-row form-row--actions">
              <label>核实状态<select v-model="claimForm.verificationStatus"><option value="PENDING">待确认</option><option value="CONFIRMED">已确认</option><option value="EXPIRED">已过期</option><option value="REJECTED">不采用</option></select></label>
              <div><button type="button" class="quiet" @click="resetClaim">清空</button><button type="submit" class="primary" :disabled="savingClaim">{{ savingClaim ? '保存中…' : '保存条目' }}</button></div>
            </div>
          </form>
        </div>
        <aside class="panel ledger">
          <h2>已保存的事实</h2><p v-if="!claims.length">还没有事实条目。先写下你确实做过的事。</p>
          <article v-for="item in claims" :key="item.id" class="ledger-item">
            <div><strong>{{ item.title }}</strong><span :class="['status', item.verificationStatus.toLowerCase()]">{{ statusLabel(item.verificationStatus) }}</span></div>
            <p>{{ item.resumeText }}</p>
            <small>{{ categoryLabel(item.category) }} · {{ responsibilityLabel(item.responsibility) }}</small>
            <div class="ledger-actions"><button type="button" @click="editClaim(item)">编辑</button><button type="button" @click="removeClaim(item)">移除</button></div>
          </article>
          <button type="button" class="primary full" @click="tab = 'editor'">下一步：制作简历</button>
        </aside>
      </section>

      <section v-else-if="tab === 'editor'" class="builder-grid">
        <div class="panel">
          <div class="panel-head"><div><h2>AI 生成或手写简历</h2><p>选择岗位后，AI 可根据已确认事实改写初稿；你也可以直接编辑。AI 不会替你核实真实性。</p></div></div>
          <div class="form-row">
            <label>目标岗位<select v-model.number="selectedJobId"><option :value="null">通用简历</option><option v-for="job in jobs" :key="job.id" :value="job.id">{{ job.name }}</option></select></label>
            <label>版本名称<input v-model="versionTitle" maxlength="120" placeholder="例如：Java 后端定制简历" /></label>
          </div>
          <div class="form-row">
            <label>姓名<input v-model="editor.name" maxlength="100" /></label>
            <label>求职意向<input v-model="editor.targetJob" maxlength="120" /></label>
          </div>
          <div class="form-row"><label>电话<input v-model="editor.phone" maxlength="40" /></label><label>邮箱<input v-model="editor.email" maxlength="160" type="email" /></label></div>
          <label v-for="field in contentFields" :key="field.key">{{ field.label }}<textarea v-model="editor[field.key]" :rows="field.rows" :placeholder="field.hint" /></label>
          <div class="editor-actions">
            <button type="button" class="quiet" :disabled="generating || !selectedJobId || !confirmedClaimCount" @click="generate">{{ generating ? 'AI 生成中…' : 'AI 生成简历初稿' }}</button>
            <button type="button" class="primary" :disabled="savingVersion" @click="saveVersion">{{ savingVersion ? '保存中…' : '保存为新版本' }}</button>
          </div>
          <p class="fineprint">已确认事实 {{ confirmedClaimCount }} 条。点击 AI 生成时，这些事实文本及目标岗位会发送给{{ resumeAiProvider }}；不会上传原始简历文件。生成结果仍需逐条核对，编辑后请另存新版本。未完成的【待补】内容不能导出。</p>
        </div>
        <aside class="panel versions">
          <h2>简历版本</h2><p>每次保存产生新版本，旧版本可重新打开和对比。</p>
          <p v-if="!versions.length" class="empty">暂无已保存版本</p>
          <button v-for="item in versions" :key="item.id" type="button" class="version-card" :class="{ active: activeVersion?.id === item.id }" @click="openVersion(item.id)">
            <strong>{{ item.title }}</strong><small>{{ item.sourceKind === 'AI_GENERATED' ? 'AI 初稿' : item.sourceKind === 'GENERATED' ? '旧版事实整理' : '手动保存' }} · {{ formatDate(item.createdAt) }}</small>
          </button>
          <button v-if="activeVersion" type="button" class="primary full" @click="tab = 'preview'; loadPreview()">预览与导出当前版本</button>
        </aside>
      </section>

      <section v-else class="builder-grid builder-grid--preview">
        <div class="panel preview-panel">
          <div class="panel-head"><div><h2>A4 简历预览</h2><p>预览与 PDF 下载使用同一份服务端文件。</p></div>
            <button type="button" class="quiet" :disabled="!activeVersion || dirty" @click="loadPreview">刷新预览</button>
          </div>
          <p v-if="dirty" class="notice">内容已修改，请保存新版本后再预览或导出。</p>
          <p v-if="previewError" class="notice notice--error">{{ previewError }}</p>
          <iframe v-if="previewUrl" :src="previewUrl" title="简历 PDF 预览" class="pdf-frame" />
          <div v-else class="preview-empty">{{ activeVersion ? '点击“刷新预览”查看 PDF' : '先保存或选择一份简历版本' }}</div>
        </div>
        <aside class="panel versions">
          <h2>导出与对比</h2>
          <p>当前版本：{{ activeVersion?.title || '未选择' }}</p>
          <div class="export-actions"><button type="button" class="primary" :disabled="!activeVersion || dirty" @click="download('pdf')">下载 PDF</button><button type="button" class="quiet" :disabled="!activeVersion || dirty" @click="download('docx')">下载 DOCX</button></div>
          <button type="button" class="quiet full" :disabled="!activeVersion || dirty" @click="returnToPreparation(true)">用于本次面试 →</button>
          <label class="compare-select">与旧版本对比<select v-model.number="comparisonId"><option :value="null">不对比</option><option v-for="item in versions.filter(v => v.id !== activeVersion?.id)" :key="item.id" :value="item.id">{{ item.title }} · {{ formatDate(item.createdAt) }}</option></select></label>
          <div v-for="item in differences" :key="item.key" class="comparison"><strong>{{ item.label }}</strong><p>旧版：{{ item.before || '未填写' }}</p><p>当前：{{ item.after || '未填写' }}</p></div>
          <p v-if="comparisonId && !differences.length" class="empty">两版内容相同</p>
        </aside>
      </section>
    </main>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import { addResumeClaim, deleteResumeClaim, exportResumeVersion, generateResumeVersion, getJobList, getResumeFileProfile,
  generateResumeClaimExample, getAiResumeClaimWording,
  getResumeAiStatus, getResumeClaimSuggestions, getResumeClaims,
  getResumeVersion, getResumeVersions, saveResumeVersion, uploadResumeFile,
  updateResumeClaim } from '../api'

const router = useRouter()
const route = useRoute()
const tabs = [{ key: 'claims', label: '资料与事实', number: '01' }, { key: 'editor', label: '制作简历', number: '02' }, { key: 'preview', label: '预览与导出', number: '03' }]
const categories = [{ value: 'EDUCATION', label: '教育经历' }, { value: 'EXPERIENCE', label: '实习 / 工作' }, { value: 'PROJECT', label: '项目经历' }, { value: 'SKILL', label: '专业技能' }, { value: 'AWARD', label: '荣誉奖项' }]
const contentFields = [{ key: 'summary', label: '个人简介', rows: 3, hint: '概括真实优势，不编造业绩' },
  { key: 'education', label: '教育经历', rows: 3, hint: '学校、专业、时间、相关课程' },
  { key: 'experience', label: '实习 / 工作经历', rows: 5, hint: '公司、岗位、本人职责与结果' },
  { key: 'projects', label: '项目经历', rows: 6, hint: '项目背景、技术方案、个人贡献与可核实结果' },
  { key: 'skills', label: '专业技能', rows: 3, hint: '真实掌握的语言、框架和工具' },
  { key: 'awards', label: '荣誉奖项', rows: 2, hint: '选填' }]
const emptyClaim = () => ({ category: 'PROJECT', title: '', factText: '', resumeText: '', responsibility: 'PARTICIPATED', personalBoundary: '', verificationStatus: 'PENDING', sourceExcerpt: '' })
const emptyContent = () => ({ name: '', phone: '', email: '', targetJob: '', summary: '', education: '', experience: '', projects: '', skills: '', awards: '' })
const tab = ref('claims')
const error = ref('')
const resumeAiStatus = ref(null)
const previewError = ref('')
const previewUrl = ref('')
const jobs = ref([])
const selectedJobId = ref(Number(route.query.jobId) || null)
const claims = ref([])
const suggestions = ref([])
const suggestionsMessage = ref('')
const uploadedResumeName = ref('')
const resumeFileInput = ref(null)
const uploadingResume = ref(false)
const versions = ref([])
const activeVersion = ref(null)
const comparisonId = ref(null)
const comparison = ref(null)
const dirty = ref(false)
const loadingSuggestions = ref(false)
const loadingExample = ref(false)
const exampleBrief = ref('')
const claimExample = ref(null)
const loadingAiWording = ref(false)
const wordingSuggestion = ref('')
const wordingSourceSnapshot = ref('')
const savingClaim = ref(false)
const savingVersion = ref(false)
const generating = ref(false)
const editingClaimId = ref(null)
const claimForm = reactive(emptyClaim())
const editor = reactive(emptyContent())
const versionTitle = ref('我的简历')
let loadingVersion = false

const differences = computed(() => {
  if (!comparison.value || !activeVersion.value) return []
  const fields = [{ key: 'name', label: '姓名' }, { key: 'targetJob', label: '求职意向' }, ...contentFields]
  return fields.filter(field => comparison.value.content?.[field.key] !== activeVersion.value.content?.[field.key])
    .map(field => ({ ...field, before: comparison.value.content?.[field.key], after: activeVersion.value.content?.[field.key] }))
})
const confirmedClaimCount = computed(() => claims.value.filter(item => item.verificationStatus === 'CONFIRMED').length)
const resumeAiProvider = computed(() => resumeAiStatus.value?.provider === 'deepseek' ? 'DeepSeek 服务' : '项目配置的智谱服务')
watch(comparisonId, async id => {
  comparison.value = null
  if (!id) return
  try { comparison.value = await getResumeVersion(id) } catch (e) { error.value = e.message || '版本对比失败' }
})
watch(editor, () => { if (!loadingVersion) dirty.value = true }, { deep: true })
watch([versionTitle, selectedJobId], () => { if (!loadingVersion && activeVersion.value) dirty.value = true })
watch([() => claimForm.category, exampleBrief, selectedJobId], () => { claimExample.value = null })
onMounted(async () => {
  try { resumeAiStatus.value = await getResumeAiStatus() } catch { /* status is optional on an older backend */ }
  try {
    const [storedClaims, storedVersions, availableJobs] = await Promise.all([getResumeClaims(), getResumeVersions(), getJobList()])
    claims.value = storedClaims || []
    versions.value = storedVersions || []
    jobs.value = availableJobs || []
    try { uploadedResumeName.value = (await getResumeFileProfile())?.filename || '' } catch { /* optional status */ }
    const requested = Number(route.query.versionId)
    if (requested) {
      await openVersion(requested)
      tab.value = route.query.tab === 'editor' ? 'editor' : 'preview'
      if (tab.value === 'preview') await loadPreview()
    }
  } catch (e) { error.value = e.message || '简历工作台加载失败' }
})
onUnmounted(() => { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value) })

function statusLabel(value) { return ({ PENDING: '待确认', CONFIRMED: '已确认', EXPIRED: '已过期', REJECTED: '不采用' })[value] || value }
function categoryLabel(value) { return categories.find(item => item.value === value)?.label || value }
function responsibilityLabel(value) { return ({ PARTICIPATED: '参与', MODULE: '负责模块', LED: '主导方案或交付', OWNER: '项目负责人' })[value] || value }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '' }
function claimSnapshot() { return JSON.stringify({ ...claimForm, editingClaimId: editingClaimId.value }) }
function clearWordingSuggestion() { wordingSuggestion.value = ''; wordingSourceSnapshot.value = '' }
function resetClaim() { Object.assign(claimForm, emptyClaim()); editingClaimId.value = null; clearWordingSuggestion() }
function editClaim(item) { editingClaimId.value = item.id; Object.assign(claimForm, item); clearWordingSuggestion(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
function useSuggestion(item, index) {
  if (claimForm.title || claimForm.factText || claimForm.resumeText) {
    if (!window.confirm('当前表单有未保存内容。确定用此条草稿替换？')) return
  }
  resetClaim(); Object.assign(claimForm, item, { verificationStatus: 'PENDING' }); suggestions.value.splice(index, 1)
}
async function loadSuggestions() {
  if (loadingSuggestions.value) return
  loadingSuggestions.value = true; suggestionsMessage.value = ''; error.value = ''
  try {
    suggestions.value = await getResumeClaimSuggestions() || []
    suggestionsMessage.value = suggestions.value.length
      ? `已提取 ${suggestions.value.length} 条草稿，请逐条核对。`
      : '这份简历已读取，但规则未识别到项目或技能条目；可在下方手动添加。'
  } catch (e) { suggestionsMessage.value = e.message || '提取失败，请检查上传的简历' }
  finally { loadingSuggestions.value = false }
}
async function uploadResumeForClaims(event) {
  const file = event.target?.files?.[0]
  if (event.target) event.target.value = ''
  if (!file || uploadingResume.value) return
  if (!/\.(pdf|doc|docx)$/i.test(file.name) || file.size > 10 * 1024 * 1024) {
    suggestionsMessage.value = '仅支持不超过 10MB 的 PDF 或 Word 简历'; return
  }
  if (uploadedResumeName.value && !window.confirm('上传新简历会替换当前账号已上传的简历。确定继续？')) return
  uploadingResume.value = true; suggestionsMessage.value = ''; error.value = ''
  try {
    await uploadResumeFile(file)
    uploadedResumeName.value = file.name
    suggestions.value = []
    suggestionsMessage.value = '简历已解析。现在点击“从已上传简历提取待核实条目”。'
  } catch (e) { suggestionsMessage.value = e.message || '简历上传或解析失败' }
  finally { uploadingResume.value = false }
}
async function generateExample() {
  if (loadingExample.value) return
  loadingExample.value = true; claimExample.value = null; error.value = ''
  const category = claimForm.category
  const brief = exampleBrief.value.trim()
  const requestedJobId = selectedJobId.value
  const targetJob = jobs.value.find(item => item.id === requestedJobId || item.code === route.query.job)?.name || ''
  try {
    const example = await generateResumeClaimExample({ category, targetJob, brief })
    if (category !== claimForm.category || brief !== exampleBrief.value.trim() || requestedJobId !== selectedJobId.value) {
      error.value = '分类或线索已变化，请重新生成示例'; return
    }
    claimExample.value = example
  } catch (e) { error.value = e.message || 'AI 生成示例失败，请稍后重试' }
  finally { loadingExample.value = false }
}
async function loadAiWording() {
  if (loadingAiWording.value) return
  const before = claimSnapshot()
  loadingAiWording.value = true; error.value = ''; clearWordingSuggestion()
  try {
    const result = await getAiResumeClaimWording({ category: claimForm.category, title: claimForm.title,
      factText: claimForm.factText, responsibility: claimForm.responsibility, personalBoundary: claimForm.personalBoundary })
    if (claimSnapshot() !== before) { error.value = '表单已变化，旧的 AI 建议未采用；请重新生成'; return }
    wordingSuggestion.value = result?.text || ''
    wordingSourceSnapshot.value = before
  } catch (e) { error.value = e.message || 'AI 起草失败，请手动填写' }
  finally { loadingAiWording.value = false }
}
function applyAiWording() {
  if (!wordingSuggestion.value || claimSnapshot() !== wordingSourceSnapshot.value) {
    clearWordingSuggestion(); error.value = '表单已变化，请重新生成 AI 表述'; return
  }
  claimForm.resumeText = wordingSuggestion.value
  claimForm.verificationStatus = 'PENDING'
  clearWordingSuggestion()
}
async function saveClaim() {
  if (savingClaim.value) return
  savingClaim.value = true; error.value = ''
  try {
    if (editingClaimId.value) await updateResumeClaim(editingClaimId.value, { ...claimForm })
    else await addResumeClaim({ ...claimForm })
    claims.value = await getResumeClaims(); resetClaim()
  } catch (e) { error.value = e.message || '事实条目保存失败' }
  finally { savingClaim.value = false }
}
async function removeClaim(item) {
  if (!window.confirm(`移除“${item.title}”？已保存的简历版本不会改变。`)) return
  try { await deleteResumeClaim(item.id); claims.value = await getResumeClaims(); if (editingClaimId.value === item.id) resetClaim() }
  catch (e) { error.value = e.message || '移除失败' }
}
async function refreshVersions() { versions.value = await getResumeVersions() || [] }
async function openVersion(id) {
  error.value = ''
  try {
    const version = await getResumeVersion(id)
    loadingVersion = true
    activeVersion.value = version
    Object.assign(editor, emptyContent(), version.content)
    versionTitle.value = version.title
    selectedJobId.value = version.jobId || null
    dirty.value = false
    await nextTick()
    loadingVersion = false
    if (previewUrl.value) { URL.revokeObjectURL(previewUrl.value); previewUrl.value = '' }
  } catch (e) { loadingVersion = false; error.value = e.message || '读取简历版本失败' }
}
async function saveVersion() {
  if (savingVersion.value) return
  savingVersion.value = true; error.value = ''
  try {
    const created = await saveResumeVersion({ title: versionTitle.value, jobId: selectedJobId.value, content: { ...editor } })
    await refreshVersions(); await openVersion(created.id)
    tab.value = 'preview'; await loadPreview()
  } catch (e) { error.value = e.message || '保存简历失败' }
  finally { savingVersion.value = false }
}
async function generate() {
  if (generating.value || !selectedJobId.value) return
  generating.value = true; error.value = ''
  try {
    const created = await generateResumeVersion({ title: versionTitle.value, jobId: selectedJobId.value,
      name: editor.name, phone: editor.phone, email: editor.email })
    await refreshVersions(); await openVersion(created.id)
    tab.value = 'editor'
  } catch (e) { error.value = e.message || '生成失败' }
  finally { generating.value = false }
}
async function loadPreview() {
  if (!activeVersion.value || dirty.value) return
  previewError.value = ''
  try {
    const response = await exportResumeVersion(activeVersion.value.id, 'pdf')
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = URL.createObjectURL(response.data)
  } catch (e) { previewError.value = e.message || '预览失败，请检查待补内容和中文字体' }
}
async function download(format) {
  if (!activeVersion.value || dirty.value) return
  error.value = ''
  try {
    const response = await exportResumeVersion(activeVersion.value.id, format)
    const url = URL.createObjectURL(response.data)
    const a = document.createElement('a')
    a.href = url; a.download = `resume-${activeVersion.value.id}.${format}`; a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) { error.value = e.message || '导出失败' }
}
function returnToPreparation(useVersion = false) {
  router.push({ path: '/jobs', query: {
    job: route.query.job || undefined,
    jobId: selectedJobId.value || route.query.jobId || undefined,
    resumeVersionId: useVersion ? activeVersion.value?.id : undefined,
    step: useVersion ? '2' : '1',
    from: 'builder',
  } })
}
</script>

<style scoped>
.builder{max-width:1440px;margin:0 auto;padding:32px 32px 70px;color:#203c34}.builder-head{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:24px}.eyebrow{color:#16996d;font-size:13px;font-weight:800;letter-spacing:.16em}.builder h1{margin:5px 0 8px;font-size:30px;line-height:1.35}.builder-head p:last-child,.panel-head p,.versions>p,.fineprint,.import-actions span{color:#667a75;font-size:13px;line-height:1.65}.builder-tabs{display:flex;gap:8px;border-bottom:1px solid #e2eae6;margin-bottom:20px}.builder-tabs button{border:0;border-bottom:3px solid transparent;background:none;color:#697f76;padding:12px 18px;cursor:pointer;font-weight:700}.builder-tabs button.active{color:#078660;border-color:#10ad7b}.builder-tabs span{font-size:11px;margin-right:8px}.builder-grid{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(290px,.8fr);gap:20px;align-items:start}.panel{background:white;border:1px solid #dfe8e3;border-radius:18px;padding:24px;box-shadow:0 8px 26px #0f34250b}.panel h2{font-size:20px;margin:0 0 5px}.panel h3{font-size:15px}.panel-head{display:flex;justify-content:space-between;align-items:flex-start;gap:18px;margin-bottom:20px}.panel-head p{margin:0}.claim-form,.panel:not(.versions){display:grid;gap:14px}.claim-form{padding-top:12px;border-top:1px solid #e5ece8}.claim-form h3{margin:0}.panel label{display:grid;gap:6px;font-size:13px;font-weight:700}.panel input,.panel textarea,.panel select{font:inherit;color:#1d3b31;width:100%;min-width:0;border:1px solid #d6e3db;border-radius:9px;padding:10px 11px;background:#fff}.panel textarea{resize:vertical;line-height:1.6}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:12px}.form-row--actions{align-items:end}.form-row--actions>div,.editor-actions,.export-actions{display:flex;gap:9px;justify-content:flex-end}.primary,.quiet{min-height:39px;border-radius:9px;padding:9px 15px;cursor:pointer;font:inherit;font-weight:700}.primary{background:#079467;color:white;border:1px solid #079467}.quiet{background:#fff;color:#087f5b;border:1px solid #b9d7c9}.primary:disabled,.quiet:disabled{opacity:.45;cursor:not-allowed}.full{display:block;width:100%;margin-top:18px}.import-actions{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.suggestions{background:#f4faf7;padding:12px;border-radius:12px}.suggestions h3{margin:0 0 9px}.suggestion{display:flex;justify-content:space-between;gap:10px;border-top:1px solid #dfebe4;padding:8px 0;font-size:12px;line-height:1.5}.suggestion button,.ledger-actions button{border:0;background:none;color:#07865c;font-weight:700;cursor:pointer}.ledger-item{padding:14px 0;border-bottom:1px solid #e8eeea}.ledger-item>div:first-child{display:flex;align-items:center;justify-content:space-between;gap:8px}.ledger-item strong{font-size:14px}.ledger-item p{font-size:13px;line-height:1.6;white-space:pre-wrap;color:#526860}.ledger-item small{color:#819289}.ledger-actions{display:flex;gap:14px;margin-top:8px}.status{font-size:11px;color:#8a6837;background:#fff6e8;padding:3px 8px;border-radius:99px;white-space:nowrap}.status.confirmed{color:#087c5a;background:#e6f8ee}.status.rejected,.status.expired{color:#8c5460;background:#faebee}.version-card{display:grid;gap:4px;text-align:left;width:100%;border:1px solid #e1eae4;border-radius:10px;background:#fff;padding:11px;margin-top:9px;cursor:pointer}.version-card.active{border-color:#0ca574;background:#f1fbf6}.version-card small,.empty{color:#7a8983}.editor-actions{margin-top:8px}.fineprint{margin:0}.pdf-frame{width:100%;height:760px;border:1px solid #d8e4dc;border-radius:9px;background:#f6f8f7}.preview-empty{display:grid;place-items:center;height:440px;background:#f6f9f7;color:#788a7d;border-radius:9px}.export-actions{justify-content:flex-start;margin:18px 0}.compare-select{margin-top:18px}.comparison{border-top:1px solid #e5ebe7;padding:12px 0}.comparison p{font-size:12px;white-space:pre-wrap;line-height:1.5;color:#60756a;max-height:130px;overflow:auto}.notice{padding:11px 14px;border-radius:9px;background:#fff8e6;color:#835f19;font-size:13px;margin-bottom:14px}.notice--error{background:#fff0ee;color:#ad332a}@media(max-width:900px){.builder-grid{grid-template-columns:1fr}.builder-head{display:block}.builder-head>.quiet{margin-top:12px}}@media(max-width:620px){.builder{padding:18px 14px 50px}.builder h1{font-size:24px}.form-row{grid-template-columns:1fr}.builder-tabs button{padding:10px 8px;font-size:12px}.panel{padding:16px}}
.ai-wording{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.ai-wording span{color:#667a75;font-size:12px;line-height:1.5}.ai-wording+.suggestions p{white-space:pre-wrap;line-height:1.6;margin:8px 0 12px}
.example-tools{display:flex;align-items:end;gap:12px;flex-wrap:wrap}.example-tools label{flex:1;min-width:240px}.example-tools button{white-space:nowrap}.example-card{border:1px solid #b9d7c9;border-radius:12px;background:#f4faf7;padding:14px;color:#244b3b}.example-card p{margin:9px 0;line-height:1.6;white-space:pre-wrap}.example-card small{color:#9a6314}
.resume-ai-status{background:#f1faf6;border:1px solid #b9d7c9;border-radius:12px;padding:14px 18px;margin-bottom:16px}.resume-ai-status p{font-size:13px;color:#536c60;margin:6px 0 0}
</style>
