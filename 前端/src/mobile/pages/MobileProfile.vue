<template>
  <MobileShell title="我的">
    <section class="mobile-profile-head">
      <img :src="userAvatar" alt="" />
      <div class="mobile-grow"><h2>{{ displayName }}</h2><p>{{ subtitle }}</p></div>
      <router-link :to="{ path: '/settings', query: { tab: 'general' } }" aria-label="编辑个人资料">›</router-link>
    </section>

    <section class="mobile-menu-group" aria-label="求职资料">
      <router-link to="/resume"><span>▤</span><div class="mobile-grow"><strong>我的简历</strong><small>{{ resumeLabel }}</small></div><b>›</b></router-link>
      <router-link to="/jobs"><span>◎</span><div class="mobile-grow"><strong>目标岗位</strong><small>管理面试方向</small></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'digital' } }"><span>◉</span><div class="mobile-grow"><strong>数字人设置</strong><small>面试官与语音偏好</small></div><b>›</b></router-link>
    </section>

    <section class="mobile-menu-group" aria-label="账号与帮助">
      <router-link :to="{ path: '/settings', query: { tab: 'account' } }"><span>♙</span><div class="mobile-grow"><strong>账号与安全</strong></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'general' } }"><span>⚙</span><div class="mobile-grow"><strong>设置</strong></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'help' } }"><span>?</span><div class="mobile-grow"><strong>帮助与反馈</strong></div><b>›</b></router-link>
    </section>

    <!-- 求职目标：保存后带入面试准备与专项刷题（preferredRole / preferredCompany / 预选岗位） -->
    <section class="mobile-menu-group" aria-label="求职目标">
      <div class="mprofile-career">
        <header><strong>求职目标</strong><small>保存后带入面试准备与专项刷题，每次训练仍可临时调整</small></header>
        <label>默认目标岗位
          <select v-model="career.targetJobId" :disabled="!jobsLoaded">
            <option :value="null">暂未确定</option>
            <option v-for="job in jobs" :key="job.id" :value="job.id">{{ job.name || job.title }}</option>
          </select>
        </label>
        <label>目标公司（选填）
          <input v-model.trim="career.targetCompany" maxlength="100" placeholder="例如：正在准备的目标企业" />
        </label>
        <label>求职阶段
          <select v-model="career.stage">
            <option value="">暂未选择</option>
            <option>实习</option>
            <option>校招</option>
            <option>社招</option>
          </select>
        </label>
        <button type="button" :disabled="careerSaving || !careerLoaded" @click="saveCareer">
          {{ careerSaving ? '正在保存…' : '保存求职目标' }}
        </button>
        <p v-if="careerNotice" class="mprofile-career-note" role="status">{{ careerNotice }}</p>
      </div>
    </section>

    <p v-if="loadError" class="mobile-inline-error" role="alert">部分个人资料暂时无法加载，已显示本地账号信息。</p>

    <button type="button" class="mobile-logout" @click="handleLogout">退出登录</button>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, getMyResume, getCareerProfile, saveCareerProfile, getJobList } from '../../api'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'

const router = useRouter()
const store = useUserStore()
const user = ref(null)
const resume = ref(null)
const loadError = ref(false)
const displayName = computed(() => user.value?.nickname || store.nickname || user.value?.username || store.username || '用户')
const subtitle = computed(() => user.value?.username || '求职训练用户')
const resumeLabel = computed(() => resume.value ? '已添加，可用于岗位匹配' : '尚未添加')

/* ---------------- 求职目标 ---------------- */
const jobs = ref([])
const jobsLoaded = ref(false)
const careerLoaded = ref(false)
const careerSaving = ref(false)
const careerNotice = ref('')
const emptyCareer = () => ({ stage: '', school: '', major: '', graduationYear: '', targetJobId: null, targetCompany: '' })
const career = ref(emptyCareer())

async function saveCareer() {
  careerSaving.value = true
  careerNotice.value = ''
  try {
    const saved = await saveCareerProfile(career.value)
    career.value = { ...emptyCareer(), ...saved }
    careerNotice.value = '求职目标已保存，下次准备时自动带入。'
  } catch (e) {
    careerNotice.value = e.message || '保存失败，请重试。'
  } finally {
    careerSaving.value = false
  }
}

function handleLogout() {
  store.logout()
  router.push('/login')
}

onMounted(async () => {
  const [meResult, resumeResult, careerResult, jobsResult] = await Promise.allSettled([
    getMe(), getMyResume(), getCareerProfile(), getJobList(),
  ])
  if (meResult.status === 'fulfilled') user.value = meResult.value
  if (resumeResult.status === 'fulfilled') resume.value = resumeResult.value
  careerLoaded.value = careerResult.status === 'fulfilled'
  if (careerLoaded.value) career.value = { ...emptyCareer(), ...careerResult.value }
  jobsLoaded.value = jobsResult.status === 'fulfilled'
  if (jobsLoaded.value) jobs.value = Array.isArray(jobsResult.value) ? jobsResult.value : []
  loadError.value = meResult.status === 'rejected' || resumeResult.status === 'rejected'
})
</script>

<style scoped>
.mprofile-career { display: grid; padding: 12px 4px 8px; }
.mprofile-career header { display: flex; padding: 0 4px 4px; flex-direction: column; gap: 3px; }
.mprofile-career header strong { font-size: 14px; }
.mprofile-career header small { color: var(--m-text-secondary); font-size: 12px; line-height: 1.6; }
.mprofile-career label { display: grid; margin-top: 10px; gap: 6px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 600; }
.mprofile-career select,
.mprofile-career input {
  min-height: 44px;
  padding: 0 12px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font: inherit;
  font-size: 14px;
}
.mprofile-career button {
  min-height: 44px;
  margin-top: 14px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 14px;
  font-weight: 700;
}
.mprofile-career button:disabled { opacity: .5; }
.mprofile-career-note { margin-top: 9px; color: var(--m-primary); font-size: 12.5px; }

.mobile-logout {
  width: 100%;
  min-height: 48px;
  margin-top: 16px;
  color: #b42318;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  font-size: 14px;
  font-weight: 700;
}

.mobile-logout:focus-visible,
.mprofile-career select:focus-visible,
.mprofile-career input:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }
</style>
