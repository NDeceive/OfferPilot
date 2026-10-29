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

// 与 AdaptiveStudentView 同款双表机制，key 由 router 的 meta.teacherMobile 提供。
// 桌面列 = 这些路由改造前直连的组件，一个不换，保证桌面端零回归。
// 注意 classes / classesManage / classMembers 三个 surface 共用 MobileTeacherClasses：
// 桌面端本来就是「列表（TeachingPortal）/ 详情洞察（ClassInsights）/ 成员管理（ClassMembers）」
// 三个页面，手机端合成一条路径（列表→详情→成员），由路由参数决定渲染哪一段。
const desktopViews = {
  overview: defineAsyncComponent(() => import('../views/teacher/TeacherOverview.vue')),
  classes: defineAsyncComponent(() => import('../views/teacher/ClassInsights.vue')),
  classesManage: defineAsyncComponent(() => import('../views/TeachingPortal.vue')),
  classMembers: defineAsyncComponent(() => import('../views/teacher/ClassMembers.vue')),
  tasks: defineAsyncComponent(() => import('../views/teacher/TrainingTasks.vue')),
  students: defineAsyncComponent(() => import('../views/teacher/StudentCenter.vue')),
  messages: defineAsyncComponent(() => import('../views/TeachingPortal.vue')),
  account: defineAsyncComponent(() => import('../views/teacher/TeacherAccount.vue')),
}

const mobileViews = {
  overview: defineAsyncComponent(() => import('./pages/MobileTeacherHome.vue')),
  classes: defineAsyncComponent(() => import('./pages/MobileTeacherClasses.vue')),
  classesManage: defineAsyncComponent(() => import('./pages/MobileTeacherClasses.vue')),
  classMembers: defineAsyncComponent(() => import('./pages/MobileTeacherClasses.vue')),
  tasks: defineAsyncComponent(() => import('./pages/MobileTeacherTasks.vue')),
  students: defineAsyncComponent(() => import('./pages/MobileTeacherStudents.vue')),
  messages: defineAsyncComponent(() => import('./pages/MobileTeacherMessages.vue')),
  account: defineAsyncComponent(() => import('./pages/MobileWorkAccount.vue')),
}

// ?demo=1 是给评委看的桌面演示壳（AppLayout + 合成数据），手机端不重做一套：
// 带 demo=1 时一律走桌面组件，保证既有演示链路在任何窗口宽度下行为不变。
// 非移动端（桌面）同样走桌面组件——这条是零回归的生命线。
const activeView = computed(() => {
  const surface = route.meta.teacherMobile
  const useMobile = isMobile.value && route.query.demo !== '1'
  return (useMobile ? mobileViews : desktopViews)[surface]
})
</script>
