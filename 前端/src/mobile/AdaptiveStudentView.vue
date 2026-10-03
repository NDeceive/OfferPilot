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

const desktopViews = {
  home: defineAsyncComponent(() => import('../views/Dashboard.vue')),
  practice: defineAsyncComponent(() => import('../views/LearningResources.vue')),
  records: defineAsyncComponent(() => import('../views/History.vue')),
  profile: defineAsyncComponent(() => import('../views/Profile.vue')),
}

const mobileViews = {
  home: defineAsyncComponent(() => import('./pages/MobileHome.vue')),
  practice: defineAsyncComponent(() => import('./pages/MobilePractice.vue')),
  records: defineAsyncComponent(() => import('./pages/MobileRecords.vue')),
  profile: defineAsyncComponent(() => import('./pages/MobileProfile.vue')),
}

const activeView = computed(() => {
  const surface = route.meta.mobileSurface
  return (isMobile.value ? mobileViews : desktopViews)[surface]
})
</script>
