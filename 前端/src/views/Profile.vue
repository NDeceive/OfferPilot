<template>
  <component :is="mobile ? MobileShell : AppLayout" title="我的">
    <div class="profile-page" :class="{ 'is-mobile': mobile }">
      <header class="page-heading"><div><span class="eyebrow">个人中心</span><h1>我的求职档案</h1><p>整理你的经历与目标，让每一次训练更贴近自己。</p></div><router-link class="text-link" to="/settings">账号设置 →</router-link></header>
      <div v-if="loading" class="profile-loading" role="status">正在读取你的求职档案…</div>
      <template v-else>
        <div v-if="loadErrors.length" class="profile-error" role="alert">{{ loadErrors.join('；') }} <button type="button" @click="load">重新加载</button></div>
        <p v-if="notice" class="profile-notice" role="status">{{ notice }}</p>
        <section class="identity-panel" aria-label="个人资料">
          <AvatarEditor :model-value="userStore.avatar" :name="displayName" @update:model-value="avatarSaved" />
          <div class="identity-copy"><span class="eyebrow">你的训练档案</span><h2>{{ displayName }}</h2><p>{{ identitySubtitle }}</p><span class="account-name">账号：{{ user?.username || userStore.username || '—' }}</span></div>
          <button class="secondary" type="button" :disabled="!user" @click="nicknameDraft = displayName; editingName = !editingName">编辑昵称</button>
          <form v-if="editingName" class="name-form" @submit.prevent="saveName"><label for="profile-nickname">昵称</label><input id="profile-nickname" v-model="nicknameDraft" maxlength="30" required /><button class="primary" :disabled="nameSaving">{{ nameSaving ? '保存中…' : '保存昵称' }}</button><button class="secondary" type="button" :disabled="nameSaving" @click="editingName = false">取消</button><p v-if="nameError" class="field-error" role="alert">{{ nameError }}</p></form>
        </section>
        <div class="profile-columns">
          <div class="profile-main">
            <section class="profile-panel resume-panel" aria-labelledby="resume-title">
              <header class="section-heading"><div><span class="eyebrow">训练依据</span><h2 id="resume-title">我的简历</h2></div><span class="status-label">{{ !resumeLoaded ? '暂不可用' : resume?.rawText ? '已建立档案' : '待添加' }}</span></header>
              <p class="section-description">面试教练会结合你的项目与技能提问。更新资料用于后续训练，不改变历史报告。</p>
              <div v-if="resume?.rawText" class="resume-document"><span class="document-icon" aria-hidden="true">≡</span><div><strong>{{ fileProfile?.filename || '在线简历' }}</strong><p>{{ resume.updateTime ? '最近更新 ' + dateLabel(resume.updateTime) : '已保存简历内容' }}</p></div><button class="text-link" type="button" @click="showPreview = !showPreview">{{ showPreview ? '收起内容' : '查看内容' }}</button></div>
              <div v-else-if="resumeLoaded" class="resume-empty"><strong>从一份简历开始</strong><p>上传已有文件，或直接填写经历。你可以先检查内容，再开始面试。</p></div>
              <pre v-if="showPreview && resume?.rawText" class="resume-preview">{{ resume.rawText }}</pre>
              <div class="resume-actions"><button :class="resume?.rawText ? 'secondary' : 'primary'" type="button" :disabled="resumeBusy || !resumeLoaded" @click="resumeInput.click()">{{ resumeBusy ? '正在解析并保存…' : resume?.rawText ? '更换简历文件' : '上传简历' }}</button><button class="secondary" type="button" :disabled="resumeBusy || !resumeLoaded" @click="resumeText = resume?.rawText || ''; editingResume = !editingResume">{{ resume?.rawText ? '修正简历内容' : '填写简历内容' }}</button></div>
              <input ref="resumeInput" type="file" accept=".pdf,.doc,.docx" hidden @change="uploadResume" />
              <p class="helper">支持 PDF、Word，文件不超过 10 MB。更换时将重新提取技能标签。</p>
              <p v-if="resumeError" class="field-error" role="alert">{{ resumeError }}</p>
              <form v-if="editingResume" class="resume-form" @submit.prevent="saveResumeText"><label for="resume-text">简历内容</label><textarea id="resume-text" v-model="resumeText" rows="12" minlength="30" maxlength="30000" required placeholder="填写教育背景、实习或工作经历、项目经历与技能…"></textarea><p class="helper">保存后重新分析技能标签。修正的是训练使用的内容，不修改原上传文件。</p><div class="form-actions"><button class="primary" :disabled="resumeBusy">保存并分析</button><button class="secondary" type="button" :disabled="resumeBusy" @click="editingResume = false">取消</button></div></form>
              <div v-if="resume?.rawText" class="skills-section"><header class="section-heading"><h3>技能标签</h3><button class="text-link" type="button" :disabled="resumeBusy" @click="tagText = skills.join('，'); editingTags = !editingTags">编辑标签</button></header><p class="helper">来自简历或由你确认，用于匹配题目，不代表能力评分。</p><div v-if="skills.length" class="skill-tags"><span v-for="skill in skills" :key="skill">{{ skill }}</span></div><p v-else class="helper">尚未确认技能，可手动添加。</p><form v-if="editingTags" @submit.prevent="saveTags"><label for="skill-tags">用逗号分隔技能（最多 30 个）</label><textarea id="skill-tags" v-model="tagText" rows="3" maxlength="1500"></textarea><div class="form-actions"><button class="primary" :disabled="resumeBusy">保存标签</button><button class="secondary" type="button" :disabled="resumeBusy" @click="editingTags = false">取消</button></div></form></div>
            </section>
            <section class="profile-panel training-panel" aria-labelledby="progress-title"><header class="section-heading"><div><span class="eyebrow">持续积累</span><h2 id="progress-title">我的训练</h2></div><router-link class="text-link" to="/history">查看面试记录 →</router-link></header><div class="training-totals"><div><strong>{{ overview?.summary?.completedCount ?? '—' }}</strong><span>已完成面试</span></div><div><strong>{{ overview?.summary?.recentCount ?? '—' }}</strong><span>近 30 天完成</span></div><div><strong>{{ overview?.summary?.streakDays ?? '—' }}<small> 天</small></strong><span>连续训练</span></div></div><p class="helper">统计来自已完成的模拟面试，详细表现见每次报告。</p><div class="record-links"><router-link to="/history"><strong>面试记录与报告</strong><span>查看反馈，复盘每次面试 →</span></router-link><router-link to="/learning"><strong>专项刷题</strong><span>围绕目标岗位继续练习 →</span></router-link></div></section>
          </div>
          <aside class="profile-side">
            <section class="profile-panel career-panel" aria-labelledby="career-title"><header class="section-heading"><div><span class="eyebrow">长期准备方向</span><h2 id="career-title">求职目标</h2></div></header><p class="section-description">保存后带入面试准备与专项刷题，每次训练仍可临时调整。</p>
              <form @submit.prevent="saveCareer"><fieldset :disabled="!careerLoaded || careerSaving"><label for="target-job">默认目标岗位</label><select id="target-job" v-model="career.targetJobId" :disabled="!jobsLoaded"><option :value="null">暂未确定</option><option v-for="job in jobs" :key="job.id" :value="job.id">{{ job.name || job.title }}</option></select>
                  <label for="career-stage">求职阶段</label><select id="career-stage" v-model="career.stage"><option value="">暂未选择</option><option>实习</option><option>校招</option><option>社招</option></select>
                  <label for="target-company">目标公司 <span>选填</span></label><input id="target-company" v-model="career.targetCompany" maxlength="100" placeholder="例如：正在准备的目标企业" />
                  <details class="education-details"><summary>教育背景 <span>选填</span></summary><label for="profile-school">学校</label><input id="profile-school" v-model="career.school" maxlength="100" autocomplete="organization" /><label for="profile-major">专业</label><input id="profile-major" v-model="career.major" maxlength="100" /><label for="graduation-year">毕业年份</label><input id="graduation-year" v-model="career.graduationYear" inputmode="numeric" pattern="(?:19|20|21)[0-9]{2}" maxlength="4" placeholder="例如：2027" /></details>
                  <button class="primary career-save" :disabled="!jobsLoaded">{{ careerSaving ? '正在保存…' : '保存求职目标' }}</button>
                </fieldset><p v-if="careerError" class="field-error" role="alert">{{ careerError }}</p></form>
            </section>
            <section class="profile-note"><h3>资料准备好，训练更有针对性</h3><p>先核对简历中你真正参与的项目，再确认目标岗位。面试开始前，可以继续调整考察重点和训练时长。</p><router-link class="text-link" to="/interview/ai">进入 AI 面试教练 →</router-link></section>
          </aside>
        </div>
      </template>
    </div>
  </component>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import AppLayout from '../components/layout/AppLayout.vue'
import MobileShell from '../mobile/components/MobileShell.vue'
import AvatarEditor from '../components/profile/AvatarEditor.vue'
import { getMe, updateProfile, getMyResume, getResumeFileProfile, uploadResumeFile, saveResume, updateResumeTags, getCareerProfile, saveCareerProfile, getJobList, getDashboardOverview } from '../api'
import { useUserStore } from '../store/user'
defineProps({ mobile: Boolean })
const userStore = useUserStore()
const user = ref(null), resume = ref(null), fileProfile = ref(null), overview = ref(null), jobs = ref([])
const emptyCareer = () => ({ stage: '', school: '', major: '', graduationYear: '', targetJobId: null, targetCompany: '' })
const career = ref(emptyCareer()), savedCareer = ref(emptyCareer())
const loading = ref(true), loadErrors = ref([]), notice = ref(''), careerLoaded = ref(false), jobsLoaded = ref(false), resumeLoaded = ref(false)
const editingName = ref(false), nicknameDraft = ref(''), nameSaving = ref(false), nameError = ref('')
const resumeInput = ref(null), resumeBusy = ref(false), resumeError = ref(''), showPreview = ref(false), editingResume = ref(false), resumeText = ref(''), editingTags = ref(false), tagText = ref('')
const careerSaving = ref(false), careerError = ref('')
const displayName = computed(() => userStore.nickname || user.value?.username || '我的档案')
const identitySubtitle = computed(() => [savedCareer.value.stage && `${savedCareer.value.stage}准备中`, savedCareer.value.school, savedCareer.value.major].filter(Boolean).join(' · ') || '完善你的资料，从真实经历开始训练')
function parseList(value) { if (Array.isArray(value)) return value; try { const data = JSON.parse(value || '[]'); return Array.isArray(data) ? data : [] } catch { return [] } }
const skills = computed(() => parseList(resume.value?.skills).filter(item => typeof item === 'string'))
const dateLabel = value => { const date = new Date(value); return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('zh-CN').format(date) }
async function load() {
  loading.value = true; loadErrors.value = []
  const [me, cv, file, prefs, roles, stats] = await Promise.allSettled([getMe(), getMyResume(), getResumeFileProfile(), getCareerProfile(), getJobList(), getDashboardOverview()])
  if (me.status === 'fulfilled' && me.value) { user.value = me.value; userStore.syncProfile(me.value) } else loadErrors.value.push('个人资料读取失败')
  resumeLoaded.value = cv.status === 'fulfilled'
  if (resumeLoaded.value) resume.value = cv.value; else loadErrors.value.push('简历读取失败')
  if (file.status === 'fulfilled') fileProfile.value = file.value
  careerLoaded.value = prefs.status === 'fulfilled'
  if (careerLoaded.value) { career.value = { ...emptyCareer(), ...prefs.value }; savedCareer.value = { ...career.value } } else loadErrors.value.push('求职目标读取失败')
  jobsLoaded.value = roles.status === 'fulfilled'
  if (jobsLoaded.value) jobs.value = Array.isArray(roles.value) ? roles.value : []; else loadErrors.value.push('岗位列表读取失败')
  if (stats.status === 'fulfilled') overview.value = stats.value; else loadErrors.value.push('训练统计暂不可用')
  loading.value = false
}
async function saveName() {
  nameError.value = ''; nameSaving.value = true
  try {
    if (!nicknameDraft.value.trim()) throw new Error('请输入昵称。')
    const updated = await updateProfile({ nickname: nicknameDraft.value.trim() })
    user.value = { ...user.value, nickname: updated.nickname }
    userStore.syncProfile({ ...user.value, avatar: userStore.avatar })
    editingName.value = false; notice.value = '昵称已保存。'
  } catch (e) { nameError.value = e.message || '保存失败，请重试。' } finally { nameSaving.value = false }
}
function avatarSaved(value) { userStore.avatar = value; notice.value = '头像已保存。' }
async function uploadResume(event) {
  const file = event.target.files[0]; event.target.value = ''; if (!file) return
  resumeError.value = ''
  if (!/\.(pdf|docx?)$/i.test(file.name) || file.size > 10 * 1024 * 1024) { resumeError.value = '请选择 10 MB 以内的 PDF 或 Word 文件。'; return }
  resumeBusy.value = true
  try {
    fileProfile.value = await uploadResumeFile(file); resume.value = await getMyResume()
    editingResume.value = false; editingTags.value = false; notice.value = '简历已更新，请核对内容与技能标签。'
  } catch (e) { resumeError.value = e.message || '上传或读取失败，请重新加载后确认。' } finally { resumeBusy.value = false }
}
async function saveResumeText() {
  resumeBusy.value = true; resumeError.value = ''
  try {
    if (resumeText.value.trim().length < 30) throw new Error('请填写至少 30 个字的简历内容。')
    resume.value = await saveResume({ rawText: resumeText.value.trim() })
    editingResume.value = false; editingTags.value = false; notice.value = '简历内容已保存，技能标签已重新分析。'
  } catch (e) { resumeError.value = e.message || '保存失败，请重试。' } finally { resumeBusy.value = false }
}
async function saveTags() {
  resumeBusy.value = true; resumeError.value = ''
  try {
    const tags = [...new Set(tagText.value.split(/[,，\n]/).map(s => s.trim()).filter(Boolean))]
    if (tags.length > 30 || tags.some(s => s.length > 40)) throw new Error('最多保存 30 个技能，每个不超过 40 个字符。')
    await updateResumeTags(tags); resume.value = { ...resume.value, skills: tags }; editingTags.value = false; notice.value = '技能标签已保存。'
  } catch (e) { resumeError.value = e.message || '保存失败，请重试。' } finally { resumeBusy.value = false }
}
async function saveCareer() {
  careerSaving.value = true; careerError.value = ''
  try { const result = await saveCareerProfile(career.value); career.value = { ...emptyCareer(), ...result }; savedCareer.value = { ...career.value }; notice.value = '求职目标已保存，下次准备时自动带入。' }
  catch (e) { careerError.value = e.message || '保存失败，请重试。' } finally { careerSaving.value = false }
}
onMounted(load)
</script>
<style scoped>
.profile-page input[hidden]{display:none}
.profile-page{max-width:1180px;margin:0 auto;padding:30px 24px 48px;color:var(--neutral-900)}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:24px}.eyebrow{display:block;color:var(--accent-700);font-size:12px;font-weight:650}.page-heading h1{margin:5px 0 8px;font-size:30px;letter-spacing:-.02em}.page-heading p,.section-description{font-size:14px;color:var(--neutral-600);line-height:1.75}.identity-panel{display:flex;align-items:center;gap:22px;flex-wrap:wrap;padding:25px 28px;margin-bottom:20px;border:1px solid var(--neutral-200);border-radius:16px;background:var(--surface-elevated)}.identity-copy{flex:1;min-width:160px}.identity-copy h2{margin:3px 0 6px;font-size:25px;overflow-wrap:anywhere}.identity-copy p{font-size:14px;color:var(--neutral-600)}.account-name{display:block;margin-top:6px;color:var(--neutral-500);font-size:12px;overflow-wrap:anywhere}.profile-columns{display:grid;grid-template-columns:minmax(0,1.75fr) minmax(300px,1fr);gap:20px;align-items:start}.profile-main,.profile-side{display:grid;gap:20px;min-width:0}.profile-panel{padding:26px;border:1px solid var(--neutral-200);border-radius:16px;background:var(--surface-elevated)}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-heading h2{font-size:21px;margin-top:4px}.section-heading h3{font-size:16px}.section-description{margin-top:12px}.status-label{flex:none;background:var(--accent-50);color:var(--accent-700);padding:5px 10px;border-radius:20px;font-size:12px}.resume-document{display:flex;align-items:center;gap:14px;margin:23px 0 18px;padding:17px 0;border-top:1px solid var(--neutral-200);border-bottom:1px solid var(--neutral-200)}.resume-document>div{flex:1;min-width:0}.resume-document strong{font-size:15px;overflow-wrap:anywhere}.resume-document p{margin-top:5px;color:var(--neutral-600);font-size:12px}.document-icon{display:grid;place-items:center;width:42px;height:50px;border:1px solid var(--accent-200);border-radius:7px;background:var(--surface-mint);color:var(--accent-700);font-size:29px}.resume-empty{padding:28px 0 23px}.resume-empty strong{font-size:18px}.resume-empty p{margin-top:8px;color:var(--neutral-600);font-size:14px;line-height:1.7}.resume-actions,.form-actions{display:flex;flex-wrap:wrap;gap:10px}.helper{font-size:12px;color:var(--neutral-600);line-height:1.75;margin-top:10px}.skills-section{margin-top:24px;padding-top:20px;border-top:1px solid var(--neutral-200)}.skill-tags{display:flex;flex-wrap:wrap;gap:8px;margin-top:13px}.skill-tags span{padding:6px 11px;border-radius:7px;background:var(--neutral-100);font-size:13px;overflow-wrap:anywhere;max-width:100%}.profile-note{padding:4px 10px}.profile-note h3{font-size:14px}.profile-note p{margin:10px 0;color:var(--neutral-600);font-size:13px;line-height:1.8}.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;padding:10px 17px;border-radius:9px;font-size:14px;font-weight:650;cursor:pointer}.primary{border:1px solid transparent;background:var(--accent-500);color:white}.primary:hover{background:var(--accent-600)}.secondary{border:1px solid var(--neutral-200);background:white;color:var(--neutral-700)}.secondary:hover{background:var(--neutral-50)}.text-link{display:inline-flex;align-items:center;min-height:44px;border:0;background:transparent;color:var(--accent-700);font-size:13px;text-decoration:none;cursor:pointer;flex-shrink:0}.profile-page button:disabled{opacity:.5;cursor:not-allowed}.profile-page :is(button,a,input,select,textarea,summary):focus-visible{outline:2px solid var(--accent-700);outline-offset:3px}.profile-page label{display:block;margin:17px 0 7px;font-size:13px;font-weight:600}.profile-page label span,.education-details summary span{font-size:12px;color:var(--neutral-500);font-weight:400;margin-left:5px}.profile-page input,.profile-page select,.profile-page textarea{display:block;width:100%;min-width:0;min-height:44px;padding:10px 12px;border:1px solid var(--neutral-300);border-radius:8px;background:white;font:inherit;font-size:14px;color:var(--neutral-900)}.profile-page textarea{resize:vertical;line-height:1.7}.profile-page fieldset{padding:0;border:0;min-width:0}.education-details{margin-top:18px;border-top:1px solid var(--neutral-200);padding-top:8px}.education-details summary{min-height:44px;line-height:44px;cursor:pointer;font-size:13px}.career-save{width:100%;margin-top:20px}.resume-preview{max-height:360px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;font-size:13px;line-height:1.8;padding:16px;background:var(--neutral-50);margin-bottom:16px;border-radius:8px}.resume-form .form-actions,.skills-section .form-actions{margin-top:12px}.name-form{display:flex;flex-wrap:wrap;align-items:center;gap:10px;width:100%;border-top:1px solid var(--neutral-200);padding-top:18px}.name-form label{margin:0}.name-form input{flex:1;min-width:140px;max-width:360px}.field-error{margin-top:10px;color:#b42318;font-size:13px;line-height:1.7}.profile-error,.profile-notice{padding:12px 16px;margin-bottom:16px;border-radius:8px;font-size:13px;line-height:1.7}.profile-error{background:#fff4ed;color:#9c4218}.profile-error button{border:0;background:transparent;text-decoration:underline;color:inherit;cursor:pointer;min-height:44px}.profile-notice{background:var(--accent-50);color:var(--accent-800)}.profile-loading{padding:80px 0;text-align:center;color:var(--neutral-600)}.training-totals{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin:23px 0 8px}.training-totals strong{display:block;font-size:27px;font-weight:650}.training-totals small{font-size:13px}.training-totals span{display:block;margin-top:6px;font-size:12px;color:var(--neutral-600)}.record-links{margin-top:18px;border-top:1px solid var(--neutral-200)}.record-links a{display:flex;justify-content:space-between;gap:10px;padding:16px 0;text-decoration:none;font-size:13px;color:var(--neutral-800)}.record-links a+a{border-top:1px solid var(--neutral-200)}.record-links span{color:var(--neutral-600)}
@media(max-width:900px){.profile-columns{grid-template-columns:minmax(0,1fr)}.profile-main,.profile-side{display:contents}.resume-panel{order:1}.career-panel{order:2}.training-panel{order:3}.profile-note{display:none}.profile-page{padding:20px}.page-heading h1{font-size:25px}}
.is-mobile{padding:0 0 20px}.is-mobile .page-heading{align-items:flex-start}.is-mobile .page-heading h1{font-size:24px}.is-mobile .page-heading .eyebrow{display:none}.is-mobile .identity-panel,.is-mobile .profile-panel{padding:20px}.is-mobile .identity-panel{gap:14px}.is-mobile .identity-panel>.secondary{width:100%}.is-mobile .record-links a{flex-direction:column}.is-mobile .section-heading{flex-wrap:wrap}.is-mobile .resume-document{flex-wrap:wrap}.is-mobile .identity-copy h2{font-size:22px}
</style>
