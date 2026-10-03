<template>
  <div class="app-layout" :class="{ 'teacher-shell': isTeacherRoute, 'class-insights-shell': route.path.startsWith('/teacher/classes'), 'training-task-shell': route.path.startsWith('/teacher/tasks') || route.path.startsWith('/teacher/students') || route.path.startsWith('/teacher/analytics') || route.path.startsWith('/teacher/reports') || route.path.startsWith('/teacher/account') }">
    <!-- Top Navigation -->
    <header class="topnav" :class="{ scrolled: isScrolled }">
      <div class="topnav-inner">
        <!-- Logo -->
        <router-link to="/" class="nav-logo">
          <BrandLogo :variant="isTeacherRoute ? 'en' : 'cn'" :width="isTeacherRoute ? 185 : 180" />
          <span v-if="isTeacherRoute" class="brand-en">教师端</span>
        </router-link>

        <!-- Main Nav Links -->
        <nav class="nav-links">
          <router-link
            v-for="item in mainNav"
            :key="item.path"
            :to="navTarget(item.path)"
            class="nav-link"
            :class="{ active: isNavActive(item) }"
          >
            <span v-if="!isTeacherRoute" class="nav-link-icon" v-html="item.icon"></span>
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="nav-link-badge">{{ item.badge }}</span>
          </router-link>
        </nav>

        <!-- Right Actions -->
        <div class="nav-actions">
          <router-link :to="isTeacherRoute?'/teacher/messages':'/my/messages'" class="message-trigger" aria-label="消息中心" title="消息中心">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg>
            <span>消息中心</span>
          </router-link>
          <!-- User Menu -->
          <button
            ref="userTriggerRef"
            type="button"
            class="user-trigger"
            aria-label="打开账户菜单"
            aria-haspopup="menu"
            :aria-expanded="userMenuOpen"
            aria-controls="user-menu"
            @click="toggleUserMenu"
          >
            <div class="user-avatar-sm">
              <img v-if="userStore.avatar" :src="userStore.avatar" alt="" />
              <span v-else>{{ userName.charAt(0) }}</span>
            </div>
          </button>

          <!-- Dropdown -->
          <Transition name="dropdown">
            <div v-if="userMenuOpen" id="user-menu" class="user-dropdown" ref="dropdownRef" role="menu">
              <div class="dropdown-header">
                <div class="user-avatar-md"><img v-if="userStore.avatar" :src="userStore.avatar" alt="" /><span v-else>{{ userName.charAt(0) }}</span></div>
                <div>
                  <div class="dropdown-name">{{ userName }}</div>
                  <div class="dropdown-email">{{ userEmail }}</div>
                </div>
              </div>
              <div class="dropdown-divider"></div>
              <router-link v-if="!isTeacherRoute" to="/my/classes" class="dropdown-item" @click="userMenuOpen=false">我的班级</router-link>
              <router-link :to="isTeacherRoute?'/teacher/account':'/profile'" class="dropdown-item" @click="userMenuOpen = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                个人中心
              </router-link>
              <router-link :to="isTeacherRoute?'/teacher/settings':'/settings'" class="dropdown-item" @click="userMenuOpen = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.32 9c.26.46.81.77 1.4.77H21a2 2 0 1 1 0 4h-.09c-.59 0-1.14.31-1.4.77z"/></svg>
                设置
              </router-link>
              <div class="dropdown-divider"></div>
              <a class="dropdown-item logout" @click.prevent="handleLogout" href="#">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                退出登录
              </a>
            </div>
          </Transition>

          <!-- Mobile Hamburger -->
          <button
            type="button"
            class="mobile-menu-btn"
            :aria-label="mobileOpen ? '关闭导航菜单' : '打开导航菜单'"
            :aria-expanded="mobileOpen"
            aria-controls="mobile-navigation"
            @click="mobileOpen = !mobileOpen"
          >
            <svg v-if="!mobileOpen" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
            <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Mobile Nav -->
      <Transition name="mobile-nav">
        <div v-if="mobileOpen" id="mobile-navigation" class="mobile-nav">
          <router-link
            v-for="item in allNav"
            :key="item.path"
            :to="navTarget(item.path)"
            class="mobile-nav-link"
            :class="{ active: isNavActive(item) }"
            @click="mobileOpen = false"
          >
            <span v-html="item.icon"></span>
            {{ item.label }}
          </router-link>
        </div>
      </Transition>
    </header>

    <!-- Page Content -->
    <main class="main-content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import {teacherContext} from '../../services/teacherContext.js'
import { getMe } from '../../api'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLogo from '../ui/BrandLogo.vue'

import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const userMenuOpen = ref(false)
const mobileOpen = ref(false)
const isScrolled = ref(false)
const userTriggerRef = ref(null)
const dropdownRef = ref(null)

const isTeacherRoute = computed(() => route.path.startsWith('/teacher'))
const userName = computed(() => userStore.nickname || userStore.username || '用户')
const userEmail = computed(() => userStore.username || '')

const studentNav = [
  {
    path: '/home',
    label: '首页',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>',
  },
  {
    // 默认进 AI 对话入口；手动录入（/jobs）与它互为切换，都算「面试准备」这一项
    path: '/interview/ai',
    label: '面试准备',
    activePaths: ['/interview/ai', '/jobs', '/resume'],
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>',
  },
  {
    path: '/history',
    label: '面试记录',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
  },
  {
    path: '/learning',
    label: '学习资源',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z"/></svg>',
  },
  {path:'/my/tasks',label:'教学任务',icon:''},
]

const teacherNav = [
  { path: '/teacher/dashboard', label: '教学总览', activePaths: ['/teacher/activity', '/teacher/training-records', '/teacher/growth', '/teacher/reviews'] },
  { path: '/teacher/classes', label: '班级洞察' },
  { path: '/teacher/tasks', label: '训练任务' },
  { path: '/teacher/students', label: '学生中心' },
  { path: '/teacher/analytics', label: '数据分析' },
]
const navTarget = path => isTeacherRoute.value ? { path, query: Object.fromEntries(Object.entries(teacherContext(route.query)).filter(([k])=>!k.startsWith('return')&&!k.startsWith('source'))) } : path

const mainNav = computed(() => isTeacherRoute.value ? teacherNav : studentNav)
const allNav = computed(() => [...(isTeacherRoute.value ? teacherNav : studentNav)])

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')

/** 一个导航项可能对应多条路由；item.activePaths 里的任意一条命中即高亮 */
const isNavActive = (item) =>
  isActive(item.path) || (item.activePaths || []).some(p => isActive(p))

const toggleUserMenu = () => {
  userMenuOpen.value = !userMenuOpen.value
}

const handleClickOutside = (e) => {
  if (userMenuOpen.value && dropdownRef.value && !dropdownRef.value.contains(e.target) && userTriggerRef.value && !userTriggerRef.value.contains(e.target)) {
    userMenuOpen.value = false
  }
}

const handleScroll = () => {
  isScrolled.value = window.scrollY > 8
}

const handleKeydown = (event) => {
  if (event.key !== 'Escape') return
  if (userMenuOpen.value) {
    userMenuOpen.value = false
    userTriggerRef.value?.focus()
  }
  mobileOpen.value = false
}

function handleLogout() {
  userStore.logout()
  userMenuOpen.value = false
  router.push('/login')
}

onMounted(() => {
  if (!userStore.profileLoaded) getMe().then(user => { if (user) userStore.syncProfile(user) }).catch(() => {})
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.user-avatar-sm img,.user-avatar-md img{width:100%;height:100%;object-fit:cover;border-radius:inherit}
.app-layout {
  min-height: 100dvh;
  background: var(--surface-primary);
}

/* === Top Nav === */
.topnav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--nav-height);
  background: rgba(250, 250, 250, 0.82);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border-bottom: 1px solid var(--neutral-200);
  z-index: 100;
  transition: all var(--duration-normal) var(--ease-out-expo);
}

.topnav.scrolled {
  box-shadow: var(--shadow-sm);
}

.topnav-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-8);
}

/* Logo */
.nav-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  text-decoration: none;
  flex-shrink: 0;
}

.nav-logo-text {
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-family: var(--font-display);
  color: var(--neutral-900);
  line-height: 1;
}

.nav-logo-text .brand-cn {
  font-size: 1.0625rem;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.nav-logo-text .brand-en {
  margin-top: 3px;
  font-size: 0.5625rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--neutral-500);
}

/* Nav Links */
.nav-links {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-500);
  text-decoration: none;
  transition: all var(--duration-fast) var(--ease-out-expo);
  white-space: nowrap;
  position: relative;
}

.nav-link:hover {
  color: var(--neutral-800);
  background: var(--neutral-100);
}

.nav-link.active {
  color: var(--accent-700);
  background: var(--accent-50);
}

.nav-link-icon {
  display: none;
  align-items: center;
  opacity: 0.7;
}

.nav-link.active .nav-link-icon {
  opacity: 1;
}

.nav-link-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--color-error);
  color: white;
  line-height: 1.4;
}

/* Right Actions */
.nav-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
  position: relative;
}

/* User Trigger */
.message-trigger{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-width:44px;min-height:44px;padding:0 10px;border-radius:8px;color:var(--neutral-600);text-decoration:none;font-size:14px;white-space:nowrap}
.message-trigger:hover,.message-trigger.router-link-active{background:var(--neutral-100);color:var(--accent-600)}
.message-trigger:focus-visible{outline:2px solid var(--accent-600);outline-offset:2px}
@media(max-width:768px){.message-trigger{padding:0}.message-trigger span{display:none}}
.user-trigger {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  cursor: pointer;
  padding: 0;
  border: 0;
  background: transparent;
  border-radius: var(--radius-full);
  transition: all var(--duration-fast);
  margin-left: var(--space-1);
}

.user-trigger:hover {
  background: var(--neutral-100);
}

.user-avatar-sm {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: white;
}

.user-avatar-md {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  color: white;
  flex-shrink: 0;
}

/* Dropdown */
.user-dropdown {
  position: absolute;
  top: calc(100% + var(--space-2));
  right: 0;
  width: 220px;
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  padding: var(--space-2);
  z-index: 200;
}

.dropdown-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-2);
}

.dropdown-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
}

.dropdown-email {
  font-size: 12px;
  color: var(--neutral-600);
}

.dropdown-divider {
  height: 1px;
  background: var(--neutral-100);
  margin: var(--space-2) 0;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  color: var(--neutral-600);
  text-decoration: none;
  transition: all var(--duration-fast);
}

.dropdown-item:hover {
  background: var(--neutral-50);
  color: var(--neutral-800);
}

.dropdown-item.logout {
  color: var(--color-error);
}
.dropdown-item.logout:hover {
  background: var(--color-error-bg);
}

/* Dropdown Transition */
.dropdown-enter-active {
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.dropdown-leave-active {
  transition: all var(--duration-fast) ease-in;
}
.dropdown-enter-from {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

/* Mobile */
.mobile-menu-btn {
  display: none;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  color: var(--neutral-600);
  align-items: center;
  justify-content: center;
}

.mobile-nav {
  display: none;
  padding: var(--space-2) var(--space-4) var(--space-4);
  background: var(--surface-elevated);
  border-bottom: 1px solid var(--neutral-200);
}

.mobile-nav-link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--neutral-600);
  text-decoration: none;
  transition: all var(--duration-fast);
}

.mobile-nav-link:hover,
.mobile-nav-link.active {
  background: var(--accent-50);
  color: var(--accent-700);
}

.mobile-nav-enter-active {
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.mobile-nav-leave-active {
  transition: all var(--duration-fast) ease-in;
}
.mobile-nav-enter-from,
.mobile-nav-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* Main Content */
.main-content {
  padding-top: var(--nav-height);
  padding-left: var(--space-6);
  padding-right: var(--space-6);
  min-height: 100dvh;
}

@media (max-width: 768px) {
  .topnav-inner {
    padding-inline: 16px;
    gap: 12px;
  }
  .nav-links {
    display: none;
  }
  .mobile-menu-btn {
    display: flex;
  }
  .mobile-nav {
    display: block;
  }
}

@media (max-width: 340px) {
  .topnav-inner {
    padding-inline: 10px;
    gap: 8px;
  }
  .nav-logo {
    gap: 5px;
  }
  .nav-logo-text .brand-cn {
    font-size: .94rem;
  }
  .user-trigger {
    margin-left: 0;
  }
}
/* Teacher surface overrides only; student layout remains unchanged. */
.teacher-shell .topnav { background:#fff;backdrop-filter:none; }
.teacher-shell .topnav-inner { max-width:1920px;padding-inline:clamp(28px,3.75vw,72px); }
.teacher-shell .main-content { max-width:none;padding-inline:clamp(28px,3.75vw,72px);background:#f8f9f8; }
.teacher-shell .nav-links { gap:clamp(18px,2.7vw,48px); }
.teacher-shell .nav-link { font-size:15px; }
.teacher-shell .brand-cn { font-size:22px; }
.teacher-shell .brand-en { font-size:12px; }
.teacher-shell .nav-link.active { background:#e1f3e9;color:#087f60; }
.teacher-shell .nav-link.active::after { content:none; }
.class-insights-shell .main-content{background:#fff}
.training-task-shell .main-content{background:#fff}
.teacher-shell .nav-link:hover{background:#f0f7f3;color:#087f60}
</style>
