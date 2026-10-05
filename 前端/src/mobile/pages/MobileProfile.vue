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

    <p v-if="loadError" class="mobile-inline-error" role="alert">部分个人资料暂时无法加载，已显示本地账号信息。</p>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMe, getMyResume } from '../../api'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import { useUserStore } from '../../store/user'
import MobileShell from '../components/MobileShell.vue'

const store = useUserStore()
const user = ref(null)
const resume = ref(null)
const loadError = ref(false)
const displayName = computed(() => user.value?.nickname || store.nickname || user.value?.username || store.username || '用户')
const subtitle = computed(() => user.value?.username || '求职训练用户')
const resumeLabel = computed(() => resume.value ? '已添加，可用于岗位匹配' : '尚未添加')

onMounted(async () => {
  const [meResult, resumeResult] = await Promise.allSettled([getMe(), getMyResume()])
  if (meResult.status === 'fulfilled') user.value = meResult.value
  if (resumeResult.status === 'fulfilled') resume.value = resumeResult.value
  loadError.value = meResult.status === 'rejected' || resumeResult.status === 'rejected'
})
</script>
