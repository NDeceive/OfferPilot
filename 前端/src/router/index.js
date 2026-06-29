import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'jobs',
        name: 'JobSelect',
        component: () => import('@/views/JobSelect.vue'),
        meta: { title: '面试准备' }
      },
      {
        path: 'resume',
        name: 'Resume',
        component: () => import('@/views/Resume.vue'),
        meta: { title: '简历画像' }
      },
      {
        path: 'interview',
        name: 'Interview',
        component: () => import('@/views/Interview.vue'),
        meta: { title: '模拟面试' }
      },
      {
        path: 'history',
        name: 'History',
        component: () => import('@/views/History.vue'),
        meta: { title: '面试记录' }
      },
      {
        path: 'report',
        name: 'Report',
        component: () => import('@/views/Report.vue'),
        meta: { title: '能力报告' }
      },
      {
        path: 'followup-records',
        name: 'FollowupRecords',
        component: () => import('@/views/FollowupRecords.vue'),
        meta: { title: '追问记录' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心' }
      },
      {
        path: 'teacher/dashboard',
        name: 'TeacherDashboard',
        component: () => import('@/views/TeacherDashboard.vue'),
        // 仅 TEACHER / ADMIN 可访问，由下方 beforeEach 守卫校验
        meta: { title: '教师端总览', roles: ['TEACHER', 'ADMIN'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.public) {
    next()
  } else if (!token) {
    next('/login')
  } else if (to.meta.roles) {
    // 页面刷新后 Pinia 状态会重置，故从 localStorage 读取角色，保证守卫可靠。
    const role = localStorage.getItem('role') || ''
    if (to.meta.roles.includes(role)) {
      next()
    } else {
      // 学生等无权限角色访问教师端 → 拦回学生端首页
      next('/home')
    }
  } else {
    next()
  }
})

export default router
