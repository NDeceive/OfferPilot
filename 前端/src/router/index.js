import { createRouter, createWebHistory } from 'vue-router'

function currentRole() {
  return (localStorage.getItem('role') || '').trim().toUpperCase()
}

function isTeacherLike(role) {
  return role === 'TEACHER' || role === 'ADMIN'
}

const routes = [
  // Public
  { path: '/', name: 'Landing', component: () => import('../views/Landing.vue'), meta: { public: true } },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('../views/Register.vue'), meta: { public: true } },
  { path: '/forgot-password', name: 'ForgotPassword', component: () => import('../views/ForgotPassword.vue'), meta: { public: true } },

  // Student portal
  { path: '/home', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
  // AI 对话式面试入口（默认）：只覆盖「选岗位 + 传简历」，之后接力到 /jobs
  { path: '/interview/ai', name: 'AiPrep', component: () => import('../views/AiPrep.vue') },
  { path: '/jobs', name: 'JobSelect', component: () => import('../views/JobSelect.vue') },
  { path: '/resume', name: 'Resume', component: () => import('../views/JobSelect.vue') },
  { path: '/interview', name: 'Interview', component: () => import('../views/Interview.vue') },
  { path: '/history', name: 'History', component: () => import('../views/History.vue') },
  { path: '/history/:id', name: 'HistoryDetail', component: () => import('../views/HistoryDetail.vue') },
  { path: '/report', name: 'Report', component: () => import('../views/HistoryDetail.vue') },
  { path: '/followup-records', name: 'FollowupRecords', component: () => import('../views/History.vue') },
  { path: '/learning', name: 'LearningResources', component: () => import('../views/LearningResources.vue') },
  { path: '/learning/session/:sessionId', name: 'TrainingSession', component: () => import('../views/TrainingSession.vue') },
  { path: '/profile', name: 'Profile', component: () => import('../views/Profile.vue') },
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
