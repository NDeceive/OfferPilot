<template>
  <MobileShell v-bind="shell" title="我的" :subtitle="isTeacher ? '教师账号与常用入口' : '企业账号与会议入口'">
    <section class="mobile-profile-head">
      <img :src="userAvatar" alt="" />
      <div class="mobile-grow">
        <h2>{{ displayName }}</h2>
        <p>{{ user?.username || '—' }} · {{ roleLabel }}</p>
      </div>
      <router-link :to="{ path: '/settings', query: { tab: 'general' } }" aria-label="通用设置">›</router-link>
    </section>

    <p v-if="loadError" class="mobile-inline-error" role="alert">账号资料暂时无法加载，已显示本地信息。</p>

    <section v-if="isTeacher" class="mobile-menu-group" aria-label="教学入口">
      <router-link to="/teacher/messages"><span>✉</span><div class="mobile-grow"><strong>教学消息</strong><small>班级通知 · 任务 · 报告反馈</small></div><b>›</b></router-link>
      <router-link to="/teacher/tasks"><span>▤</span><div class="mobile-grow"><strong>训练任务</strong><small>发布与跟进</small></div><b>›</b></router-link>
      <router-link to="/teacher/classes"><span>◫</span><div class="mobile-grow"><strong>班级管理</strong><small>成员审核 · 邀请码</small></div><b>›</b></router-link>
    </section>
    <section v-else class="mobile-menu-group" aria-label="会议入口">
      <router-link to="/enterprise/dashboard"><span>▷</span><div class="mobile-grow"><strong>会议中心</strong><small>开启会议 · 我的会议</small></div><b>›</b></router-link>
      <router-link to="/meeting"><span>⌨</span><div class="mobile-grow"><strong>加入会议</strong><small>输入会议号进入</small></div><b>›</b></router-link>
    </section>

    <section class="mobile-menu-group" aria-label="账号与设置">
      <router-link :to="{ path: '/settings', query: { tab: 'account' } }"><span>♙</span><div class="mobile-grow"><strong>账号与安全</strong></div><b>›</b></router-link>
      <router-link :to="isTeacher ? '/teacher/settings' : { path: '/settings', query: { tab: 'general' } }"><span>⚙</span><div class="mobile-grow"><strong>设置</strong></div><b>›</b></router-link>
      <router-link :to="{ path: '/settings', query: { tab: 'help' } }"><span>?</span><div class="mobile-grow"><strong>帮助与反馈</strong></div><b>›</b></router-link>
    </section>

    <button type="button" class="mobile-logout mwa__logout" @click="handleLogout">退出登录</button>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMe } from '../../api'
import { useUserStore } from '../../store/user'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import MobileShell from '../components/MobileShell.vue'
import { ENTERPRISE_TABS, TEACHER_TABS } from '../nav'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 同一页服务两种壳：路径在 /teacher 下就是教师，否则按企业（唯一使用方是 /enterprise/account）
const isTeacher = computed(() => route.path.startsWith('/teacher'))
const ROLE = (localStorage.getItem('role') || '').toUpperCase()
const roleLabel = computed(() => ({ TEACHER: '教师', ADMIN: '管理员', ENTERPRISE: '企业' }[ROLE] || '用户'))
const displayName = computed(() => user.value?.nickname || store.nickname || user.value?.username || store.username || '用户')

const shell = computed(() => (isTeacher.value
  ? { homePath: '/teacher/dashboard', messagePath: '/teacher/messages', profilePath: '/teacher/account', navLinks: [], tabItems: TEACHER_TABS }
  : { homePath: '/enterprise/dashboard', profilePath: '/enterprise/account', messages: false, navLinks: [], tabItems: ENTERPRISE_TABS }))

const user = ref(null)
const loadError = ref(false)

function handleLogout() {
  store.logout()
  router.push('/login')
}

onMounted(async () => {
  try {
    user.value = await getMe()
  } catch {
    loadError.value = true
  }
})
</script>

<style scoped>
.mwa__logout {
  width: 100%;
  min-height: 48px;
  margin-top: 20px;
  color: #b42318;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  font-size: 14px;
  font-weight: 700;
}
.mwa__logout:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }
</style>
