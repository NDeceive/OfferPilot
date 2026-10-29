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

// key 由 router 的 meta.enterpriseMobile 提供。
// 房间页（/meeting/:code/room）不在这里——MeetingRoom 自带手机布局：手机端也能进房
// 开摄像头/麦克风（2026-10-10 放开电脑端边界）；离线演示模式则退化为本机设备预览。
const desktopViews = {
  entPortal: defineAsyncComponent(() => import('../views/enterprise/EnterprisePortal.vue')),
  meetingJoin: defineAsyncComponent(() => import('../views/meeting/MeetingJoin.vue')),
  // 桌面企业「我的」= 学生壳里的 Profile.vue（内部按角色渲染企业版个人中心）
  account: defineAsyncComponent(() => import('../views/Profile.vue')),
}

const mobileViews = {
  entPortal: defineAsyncComponent(() => import('./pages/MobileEnterprisePortal.vue')),
  meetingJoin: defineAsyncComponent(() => import('./pages/MobileMeetingJoin.vue')),
  account: defineAsyncComponent(() => import('./pages/MobileWorkAccount.vue')),
}

const activeView = computed(() => (isMobile.value ? mobileViews : desktopViews)[route.meta.enterpriseMobile])
</script>
