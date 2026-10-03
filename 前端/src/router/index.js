import { createRouter, createWebHistory } from 'vue-router'

const AdaptiveStudentView = () => import('../mobile/AdaptiveStudentView.vue')

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
  { path: '/home', name: 'Dashboard', component: AdaptiveStudentView, meta: { mobileSurface: 'home' } },
  // AI 对话式面试入口（默认）：只覆盖「选岗位 + 传简历」，之后接力到 /jobs
  { path: '/interview/ai', name: 'AiPrep', component: () => import('../views/AiPrep.vue') },
  { path: '/jobs', name: 'JobSelect', component: () => import('../views/JobSelect.vue') },
  { path: '/resume', name: 'Resume', component: () => import('../views/JobSelect.vue') },
  { path: '/interview', name: 'Interview', component: () => import('../views/Interview.vue') },
  { path: '/history', name: 'History', component: AdaptiveStudentView, meta: { mobileSurface: 'records' } },
  { path: '/history/:id', name: 'HistoryDetail', component: () => import('../views/HistoryDetail.vue') },
  { path: '/report', name: 'Report', component: () => import('../views/HistoryDetail.vue') },
  { path: '/followup-records', name: 'FollowupRecords', component: () => import('../views/History.vue') },
  { path: '/learning', name: 'LearningResources', component: AdaptiveStudentView, meta: { mobileSurface: 'practice' } },
  { path: '/learning/session/:sessionId', name: 'TrainingSession', component: () => import('../views/TrainingSession.vue') },
  { path: '/profile', name: 'Profile', component: AdaptiveStudentView, meta: { mobileSurface: 'profile' } },
  { path: '/settings', name: 'Settings', component: () => import('../views/Settings.vue') },
  { path: '/member', redirect: '/home' },

  ...['/my/classes','/my/tasks','/my/tasks/:id','/my/messages'].map(path=>({path,component:()=>import('../views/TeachingPortal.vue'),meta:{roles:['STUDENT']}})),
  {path:'/teacher/messages',component:()=>import('../views/TeachingPortal.vue'),meta:{roles:['TEACHER','ADMIN']}},

  // Teacher portal: all new pages retain the existing role guard.
  { path: '/teacher', redirect: to => ({ path: '/teacher/dashboard', query: to.query }) },
  { path: '/teacher/dashboard', name: 'TeacherDashboard', component: () => import('../views/teacher/TeacherOverview.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  ...['activity', 'training-records', 'growth', 'reviews'].map(surface => ({ path: '/teacher/' + surface, component: () => import('../views/teacher/TeacherWorkspace.vue'), meta: { roles: ['TEACHER', 'ADMIN'], teacherSurface: surface } })),
  { path: '/teacher/class', redirect: to => ({ path: '/teacher/classes', query: to.query }), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/classes', component: () => import('../views/teacher/ClassInsights.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/classes/manage', component: () => import('../views/TeachingPortal.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/classes/:classId', component: () => import('../views/teacher/ClassInsights.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/classes/:classId/members', component: () => import('../views/teacher/ClassMembers.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path:'/teacher/reports/:id',component:()=>import('../views/teacher/TeacherTrainingReport.vue'),meta:{roles:['TEACHER','ADMIN']} },
  { path: '/teacher/analytics', component: () => import('../views/teacher/TeacherAnalytics.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } },
  { path: '/teacher/analytics/abilities', redirect: to => ({ path: '/teacher/analytics', query: { ...to.query, tab: 'roles', roleId: to.query.position || undefined, timeRange: to.query.period || undefined, classIds: to.query.classId || undefined } }), meta: { roles: ['TEACHER', 'ADMIN'] } },
  ...['/teacher/students', '/teacher/students/:id'].map(path => ({ path, component: () => import('../views/teacher/StudentCenter.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } })),
  ...['/teacher/tasks', '/teacher/tasks/create', '/teacher/tasks/templates', '/teacher/tasks/:id/edit', '/teacher/tasks/:id'].map(path => ({ path, component: () => import('../views/teacher/TrainingTasks.vue'), meta: { roles: ['TEACHER', 'ADMIN'] } })),
  { path:'/teacher/reports',redirect:to=>({path:'/teacher/analytics',query:{...to.query,tab:'overview'}}),meta:{roles:['TEACHER','ADMIN']} },
  { path:'/teacher/account',component:()=>import('../views/teacher/TeacherAccount.vue'),meta:{roles:['TEACHER','ADMIN']} },
  { path:'/teacher/settings',component:()=>import('../views/Settings.vue'),meta:{roles:['TEACHER','ADMIN']} },
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
      const liveAliases={'/teacher/growth':'/teacher/training-records','/teacher/tasks/templates':'/teacher/tasks/create'}
      if(to.query.demo!=='1'&&liveAliases[to.path]){next({path:liveAliases[to.path],query:to.query,replace:true});return}
      next()
    } else {
      next('/home')
    }
    return
  }

  next()
})

export default router
