<template>
  <component :is="activeView" />
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useIsMobile } from './composables/useIsMobile'
import './styles/mobile.css'

const route = useRoute()
const isMobile = useIsMobile()

// 两张表的 key 必须一一对应：桌面命中的是 desktopViews，漏一个 key 桌面就渲染空白。
const desktopViews = {
  home: defineAsyncComponent(() => import('../views/Dashboard.vue')),
  practice: defineAsyncComponent(() => import('../views/LearningResources.vue')),
  records: defineAsyncComponent(() => import('../views/History.vue')),
  profile: defineAsyncComponent(() => import('../views/Profile.vue')),
  aiPrep: defineAsyncComponent(() => import('../views/AiPrep.vue')),
  jobSetup: defineAsyncComponent(() => import('../views/JobSelect.vue')),
  report: defineAsyncComponent(() => import('../views/HistoryDetail.vue')),
}

const mobileViews = {
  home: defineAsyncComponent(() => import('./pages/MobileHome.vue')),
  practice: defineAsyncComponent(() => import('./pages/MobilePractice.vue')),
  records: defineAsyncComponent(() => import('./pages/MobileRecords.vue')),
  profile: defineAsyncComponent(() => import('./pages/MobileProfile.vue')),
  aiPrep: defineAsyncComponent(() => import('./pages/MobileAiPrep.vue')),
  jobSetup: defineAsyncComponent(() => import('./pages/MobileJobSetup.vue')),
  report: defineAsyncComponent(() => import('./pages/MobileReport.vue')),
}

// 企业账号没有移动学生页：/profile 一律走桌面视图（Profile.vue 里已是企业版个人中心），
// 否则窄窗口/手机上「我的」会变成学生手机版（我的简历/目标岗位/数字人设置 + 学生底部导航）。
// 其余 surface（home/records/aiPrep…）不动：企业手敲学生路径属已知越权面，本次不补。
const isEnterprise = (localStorage.getItem('role') || '').toUpperCase() === 'ENTERPRISE'

const activeView = computed(() => {
  const surface = route.meta.mobileSurface
  const useMobile = isMobile.value && !(isEnterprise && surface === 'profile')
  return (useMobile ? mobileViews : desktopViews)[surface]
})
</script>
