import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router'

const AdaptiveStudentView = () => import('../mobile/AdaptiveStudentView.vue')

function currentRole() {
  return (localStorage.getItem('role') || '').trim().toUpperCase()
}

function isTeacherLike(role) {
  return role === 'TEACHER' || role === 'ADMIN'
}

/** 登录后 / 被守卫弹回时的「归属首页」：企业走企业端，教师类走教师端，其余回学生端。
 *  Login.vue 也 import 这里，避免登录成功分流和守卫兜底两处各写一份角色判断。 */
export function roleHome(role) {
  if (role === 'ENTERPRISE') return '/enterprise/dashboard'
  return isTeacherLike(role) ? '/teacher/dashboard' : '/home'
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

  // Enterprise portal & meeting：企业端开会生成会议号，教师/学生凭码进会视频面试。
  // /enterprise 仅企业/教师/管理员；/meeting 不加 meta.roles——学生、教师都要能被拉进会。
  // 全部懒加载且不带 meta.mobileSurface：视频会议为桌面端功能，不走手机端自适应壳。
  { path: '/enterprise', redirect: to => ({ path: '/enterprise/dashboard', query: to.query }) },
  { path: '/enterprise/dashboard', name: 'EnterprisePortal', component: () => import('../views/enterprise/EnterprisePortal.vue'), meta: { roles: ['ENTERPRISE', 'TEACHER', 'ADMIN'] } },
  { path: '/meeting', name: 'MeetingJoin', component: () => import('../views/meeting/MeetingJoin.vue') },
  { path: '/meeting/:code/room', name: 'MeetingRoom', component: () => import('../views/meeting/MeetingRoom.vue') },

  // 未知路径兜底：按当前角色回各自首页（未登录再由守卫转登录页），不再白屏
  { path: '/:pathMatch(.*)*', redirect: () => roleHome(currentRole()) },
]

/**
 * HBuilderX 5+App 里页面是 file:// 加载的：Chromium 对 file: URL 只允许
 * pushState/replaceState 改 fragment（改 path 会抛 SecurityError），history 模式
 * 首屏之后就断。hash 模式只动 fragment，在 file:// 下合法。
 *
 * hash 模式不传 base：file:// 下 location.host 为空，vue-router 会忽略 base。
 * 桌面/nginx 构建 VITE_APP_SHELL 未定义 → 仍是 createWebHistory()，URL 形状零变化。
 */
const APP_SHELL = import.meta.env.VITE_APP_SHELL === 'true'

const router = createRouter({
  history: APP_SHELL ? createWebHashHistory() : createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.public) {
    // 手机端不看着陆页：直接进登录（已登录则直接进主页）。
    // 桌面端不变——Landing 是给浏览器里的新访客看的营销页，APK 里没有它的意义。
    if (to.name === 'Landing' && isMobileViewport()) {
      if (!token) { next('/login'); return }
      next(roleHome(currentRole()))
      return
    }

    // Redirect logged-in users away from login/register
    if (token && (to.name === 'Login' || to.name === 'Register' || to.name === 'ForgotPassword')) {
      const role = currentRole()
      next(roleHome(role))
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
      // 角色不匹配该路由：弹回自己的归属首页（企业敲教师页 → 企业端，不再掉进学生页）
      next(roleHome(currentRole()))
    }
    return
  }

  next()
})

export default router
