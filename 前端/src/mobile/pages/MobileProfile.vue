<template>
  <MobileShell title="我的">
    <section class="mobile-profile-head">
      <img :src="userAvatar" alt="" />
      <div class="mobile-grow"><h2>{{ displayName }}</h2><p>{{ subtitle }}</p></div>
      <router-link :to="{ path: '/settings', query: { tab: 'general' } }" aria-label="编辑个人资料">›</router-link>
    </section>

    <!-- 只放底部 tab 之外的「个人记录」；刷题、经验本身走底部导航，不再重复。 -->
    <section class="mobile-menu-group" aria-label="学习记录">
      <router-link to="/learning/library/recent"><span>历</span><div class="mobile-grow"><strong>题单历史</strong><small>做过的练习与报告</small></div><b>›</b></router-link>
      <router-link to="/learning/library/mistakes"><span>错</span><div class="mobile-grow"><strong>错题本</strong><small>回看易错题</small></div><b>›</b></router-link>
      <router-link to="/learning/library/favorites"><span>藏</span><div class="mobile-grow"><strong>我的收藏</strong><small>收藏的题目</small></div><b>›</b></router-link>
    </section>

    <section class="mobile-menu-group" aria-label="我的经验">
      <router-link :to="{ path: '/experience', query: { view: 'mine' } }"><span>我</span><div class="mobile-grow"><strong>我的经验</strong><small>我发布的经验</small></div><b>›</b></router-link>
    </section>

    <!-- 学生也会加入班级、接收教师任务；两件事合并成一个入口，不按教师工作台的口径拆开。 -->
    <section class="mobile-menu-group" aria-label="班级与任务">
      <router-link to="/my/classes"><span>班</span><div class="mobile-grow"><strong>班级与任务</strong><small>加入班级，完成教师任务</small></div><b>›</b></router-link>
    </section>

    <section class="mobile-menu-group" aria-label="求职目标">
      <router-link to="/practice/role" class="mprofile-target"><span>岗</span><div class="mobile-grow"><strong>目标岗位</strong><small>{{ currentJobName }}</small></div><b>›</b></router-link>
      <!-- 岗位选择交给 /practice/role；这里只补充公司意向与求职阶段，避免两处重复维护 targetJobId。 -->
      <div class="mprofile-career">
        <header><strong>求职补充</strong></header>
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

    <section class="mobile-menu-group" aria-label="视频会议">
      <router-link to="/meeting"><span>会</span><div class="mobile-grow"><strong>视频会议</strong><small>会议号入会</small></div><b>›</b></router-link>
    </section>

    <section class="mobile-menu-group" aria-label="账号与帮助">
      <router-link :to="{ path: '/settings', query: { tab: 'account' } }"><span>♙</span><div class="mobile-grow"><strong>账号与安全</strong></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'general' } }"><span>⚙</span><div class="mobile-grow"><strong>设置</strong></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'help' } }"><span>?</span><div class="mobile-grow"><strong>帮助与反馈</strong></div><b>›</b></router-link>
    </section>

    <p v-if="loadError" class="mobile-inline-error" role="alert">部分个人资料暂时无法加载，已显示本地账号信息。</p>

    <button type="button" class="mobile-logout" @click="handleLogout">退出登录</button>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, getCareerProfile, saveCareerProfile, getJobList } from '../../api'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'

const router = useRouter()
const store = useUserStore()
const user = ref(null)
const loadError = ref(false)
const displayName = computed(() => user.value?.nickname || store.nickname || user.value?.username || store.username || '用户')
const subtitle = computed(() => {
  const name = user.value?.username || store.username || ''
  return !name || name === 'student' ? '学生' : name
})

/* ---------------- 求职目标 ---------------- */
const jobs = ref([])
const careerLoaded = ref(false)
const careerSaving = ref(false)
const careerNotice = ref('')
const emptyCareer = () => ({ stage: '', school: '', major: '', graduationYear: '', targetJobId: null, targetCompany: '' })
const career = ref(emptyCareer())
const currentJobName = computed(() => {
  const job = jobs.value.find(item => String(item.id) === String(career.value.targetJobId))
  return job?.name || job?.title || '未选择'
})

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
  const [meResult, careerResult, jobsResult] = await Promise.allSettled([
    getMe(), getCareerProfile(), getJobList(),
  ])
  if (meResult.status === 'fulfilled') user.value = meResult.value
  careerLoaded.value = careerResult.status === 'fulfilled'
  if (careerLoaded.value) career.value = { ...emptyCareer(), ...careerResult.value }
  if (jobsResult.status === 'fulfilled') jobs.value = Array.isArray(jobsResult.value) ? jobsResult.value : []
  loadError.value = meResult.status === 'rejected' || careerResult.status === 'rejected'
})
</script>

<style scoped>
.mprofile-target small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mprofile-career { display: grid; padding: 14px 16px 16px; }
.mprofile-career header { display: flex; padding: 0 0 4px; flex-direction: column; gap: 3px; }
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
