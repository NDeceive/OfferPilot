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

const activeView = computed(() => {
  const surface = route.meta.mobileSurface
  return (isMobile.value ? mobileViews : desktopViews)[surface]
})
</script>
