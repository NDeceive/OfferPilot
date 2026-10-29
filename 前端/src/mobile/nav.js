/**
 * 教师端 / 企业端手机壳的导航数据（纯数据，可被 node --test 单测）。
 *
 * 学生手机端五项导航写死在 MobileTabBar 里作为默认值；教师/企业通过 items 覆盖。
 * 路径必须与 router/index.js 中带 teacherMobile / mobileSurface meta 的移动端路由一致，
 * 否则底栏点了会落到桌面页（手机上一屏放不下）。
 */

/** 教师端底部导航：总览 / 班级 / 任务 / 学生 / 我的 */
export const TEACHER_TABS = [
  { path: '/teacher/dashboard', label: '总览', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-6h6v6"/></svg>' },
  { path: '/teacher/classes', label: '班级', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3.2"/><path d="M3 20a6 6 0 0 1 12 0"/><path d="M16 5.5a3 3 0 0 1 0 5.8M17.5 20a5.5 5.5 0 0 0-2.2-4.4"/></svg>' },
  { path: '/teacher/tasks', label: '任务', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 4h12a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z"/><path d="M9 9h6M9 13h4"/></svg>' },
  { path: '/teacher/students', label: '学生', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>' },
  { path: '/teacher/account', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="10" r="3"/><path d="M6.5 19a6 6 0 0 1 11 0"/></svg>' },
]

/** 企业端底部导航：会议中心 / 加入会议 / 我的 */
export const ENTERPRISE_TABS = [
  { path: '/enterprise/dashboard', label: '会议中心', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="6" width="14" height="12" rx="2"/><path d="m16 10 5-3v10l-5-3"/></svg>' },
  { path: '/meeting', label: '加入会议', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M7 9h.01M11 9h.01M15 9h.01M7 13h.01M11 13h.01M15 13h.01"/></svg>' },
  // 自己的移动账号页：/profile 保持桌面企业版个人中心（既有取舍），这里开一条移动专属路由
  { path: '/enterprise/account', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>' },
]
