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

// 桌面表保留全部 surface；移动表只保留手机端实际承载的页面。
// 面试/旧记录/旧报告已从手机端移除，由 router 的 desktopOnly 守卫在进入前拦截；
// activeView 仍对缺失的移动 surface 回退桌面组件，避免运行中缩放窗口时白屏。
const desktopViews = {
  home: defineAsyncComponent(() => import('../views/Dashboard.vue')),
  practice: defineAsyncComponent(() => import('../views/LearningResources.vue')),
  records: defineAsyncComponent(() => import('../views/History.vue')),
  profile: defineAsyncComponent(() => import('../views/Profile.vue')),
  aiPrep: defineAsyncComponent(() => import('../views/AiPrep.vue')),
  jobSetup: defineAsyncComponent(() => import('../views/JobSelect.vue')),
  report: defineAsyncComponent(() => import('../views/HistoryDetail.vue')),
  practiceSession: defineAsyncComponent(() => import('../views/TrainingSession.vue')),
  practiceReport: defineAsyncComponent(() => import('../views/PracticeReport.vue')),
  practiceLibrary: defineAsyncComponent(() => import('../views/PracticeLibrary.vue')),
}

const mobileViews = {
  home: defineAsyncComponent(() => import('./pages/MobileHome.vue')),
  practice: defineAsyncComponent(() => import('./pages/MobilePractice.vue')),
  profile: defineAsyncComponent(() => import('./pages/MobileProfile.vue')),
  practiceSession: defineAsyncComponent(() => import('./pages/MobilePracticeSession.vue')),
  practiceReport: defineAsyncComponent(() => import('./pages/MobilePracticeReport.vue')),
  practiceLibrary: defineAsyncComponent(() => import('./pages/MobilePracticeLibrary.vue')),
}

// 企业账号没有移动学生页：/profile 一律走桌面视图（Profile.vue 里已是企业版个人中心），
// 否则窄窗口/手机上「我的」会变成学生手机版（我的简历/目标岗位/数字人设置 + 学生底部导航）。
// 其余 surface（home/records/aiPrep…）不动：企业手敲学生路径属已知越权面，本次不补。
const isEnterprise = (localStorage.getItem('role') || '').toUpperCase() === 'ENTERPRISE'

const activeView = computed(() => {
  const surface = route.meta.mobileSurface
  if (!surface) return undefined
  const useMobile = isMobile.value && !(isEnterprise && surface === 'profile')
  // 移动表缺 key 时回退桌面表：桌面页面运行中缩到手机宽度不会渲染空白。
  return (useMobile ? mobileViews[surface] : null) || desktopViews[surface]
})
</script>
