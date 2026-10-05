import { createRouter, createWebHistory } from 'vue-router'

const AdaptiveStudentView = () => import('../mobile/AdaptiveStudentView.vue')

function currentRole() {
  return (localStorage.getItem('role') || '').trim().toUpperCase()
}

function isTeacherLike(role) {
  return role === 'TEACHER' || role === 'ADMIN'
}

/** 断点必须和 mobile/composables/useIsMobile.js 的 QUERY 保持一致。
 *  这里不能调 useIsMobile()——守卫是模块级函数，拿不到组件生命周期。 */
const MOBILE_QUERY = '(max-width: 767.98px)'
function isMobileViewport() {
  return typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches
}

const routes = [
  // Public
  { path: '/', name: 'Landing', component: () => import('../views/Landing.vue'), meta: { public: true } },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue'), meta: { public: true } },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('../views/ForgotPassword.vue'), meta: { public: true } },

  // Student portal
  { path: '/home', name: 'Dashboard', component: AdaptiveStudentView, meta: { mobileSurface: 'home' } },
  // AI 对话式面试入口（默认）：只覆盖「选岗位 + 传简历」，之后接力到 /jobs
  { path: '/interview/ai', name: 'AiPrep', component: AdaptiveStudentView, meta: { mobileSurface: 'aiPrep' } },
  { path: '/jobs', name: 'JobSelect', component: AdaptiveStudentView, meta: { mobileSurface: 'jobSetup' } },
  { path: '/resume', name: 'Resume', component: AdaptiveStudentView, meta: { mobileSurface: 'jobSetup' } },
  { path: '/interview', name: 'Interview', component: () => import('../views/Interview.vue') },
  { path: '/history', name: 'History', component: AdaptiveStudentView, meta: { mobileSurface: 'records' } },
  { path: '/history/:id', name: 'HistoryDetail', component: AdaptiveStudentView, meta: { mobileSurface: 'report' } },
  { path: '/report', name: 'Report', component: AdaptiveStudentView, meta: { mobileSurface: 'report' } },
  { path: '/followup-records', name: 'FollowupRecords', component: () => import('../views/History.vue') },
  { path: '/learning', name: 'LearningResources', component: AdaptiveStudentView, meta: { mobileSurface: 'practice' } },
  { path: '/learning/session/:sessionId', name: 'TrainingSession', component: () => import('../views/TrainingSession.vue') },
  { path: '/profile', name: 'Profile', component: AdaptiveStudentView, meta: { mobileSurface: 'profile' } },
  { path: '/settings', name: 'Settings', component: () => import('../views/Settings.vue') },
  { path: '/member', name: 'MemberCenter', component: () => import('../views/MemberCenter.vue') },

  // Teacher portal
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherOverview.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/class', name: 'TeacherClass', component: () => import('../views/teacher/TeacherClass.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/students/:id', name: 'TeacherStudent', component: () => import('../views/teacher/TeacherStudent.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/tasks', name: 'TeacherTask', component: () => import('../views/teacher/TeacherTask.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/reports', name: 'TeacherReport', component: () => import('../views/teacher/TeacherReport.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.public) {
    // 手机端不看着陆页：直接进登录（已登录则直接进主页）。
    // 桌面端不变——Landing 是给浏览器里的新访客看的营销页，APK 里没有它的意义。
    if (to.name === 'Landing' && isMobileViewport()) {
      if (!token) { next('/login'); return }
      next(isTeacherLike(currentRole()) ? '/teacher/dashboard' : '/home')
      return
    }

    // Redirect logged-in users away from login/register
    if (token && (to.name === 'Login' || to.name === 'Register' || to.name === 'ForgotPassword')) {
      const role = currentRole()
      next(isTeacherLike(role) ? '/teacher/dashboard' : '/home')
      return
    }
    next()
    return
  }

  if (!token) {
    next('/login')
    return
  }

  if (to.meta.roles) {
    const role = currentRole()
    if (to.meta.roles.includes(role)) {
      next()
    } else {
      next('/home')
    }
    return
  }

  next()
})

export default router
