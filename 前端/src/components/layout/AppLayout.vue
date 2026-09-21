<template>
  <div class="app-layout">
    <!-- Top Navigation -->
    <header class="topnav" :class="{ scrolled: isScrolled }">
      <div class="topnav-inner">
        <!-- Logo -->
        <router-link to="/" class="nav-logo">
          <LogoIcon :size="28" />
          <span class="nav-logo-text">
            <span class="brand-cn">智面幻境</span>
            <span class="brand-en">OfferPilot</span>
          </span>
        </router-link>

        <!-- Main Nav Links -->
        <nav class="nav-links">
          <router-link
            v-for="item in mainNav"
            :key="item.path"
            :to="item.path"
            class="nav-link"
            :class="{ active: isActive(item.path) }"
          >
            <span class="nav-link-icon" v-html="item.icon"></span>
            <span>{{ item.label }}</span>
            <span v-if="item.badge" class="nav-link-badge">{{ item.badge }}</span>
          </router-link>
        </nav>

        <!-- Right Actions -->
        <div class="nav-actions">
          <router-link to="/member" class="upgrade-pill">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            Pro
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
              <span>{{ userName.charAt(0) }}</span>
            </div>
          </button>

          <!-- Dropdown -->
          <Transition name="dropdown">
            <div v-if="userMenuOpen" id="user-menu" class="user-dropdown" ref="dropdownRef" role="menu">
              <div class="dropdown-header">
                <div class="user-avatar-md"><span>{{ userName.charAt(0) }}</span></div>
                <div>
                  <div class="dropdown-name">{{ userName }}</div>
                  <div class="dropdown-email">{{ userEmail }}</div>
                </div>
              </div>
              <div class="dropdown-divider"></div>
              <router-link to="/profile" class="dropdown-item" @click="userMenuOpen = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                个人中心
              </router-link>
              <router-link to="/settings" class="dropdown-item" @click="userMenuOpen = false">
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
            :to="item.path"
            class="mobile-nav-link"
            :class="{ active: isActive(item.path) }"
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
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LogoIcon from '../ui/LogoIcon.vue'

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
    path: '/jobs',
    label: '面试准备',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>',
  },
  {
    path: '/history',
    label: '面试记录',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
  },
]

const teacherNav = [
  {
    path: '/teacher',
    label: '班级概览',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>',
  },
  {
    path: '/teacher/class',
    label: '班级管理',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  },
  {
    path: '/teacher/tasks',
    label: '任务发布',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
  },
  {
    path: '/teacher/reports',
    label: '报告分析',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M6 20V4M18 20v-6"/></svg>',
  },
]

const mainNav = computed(() => isTeacherRoute.value ? teacherNav : studentNav)
const allNav = computed(() => [...(isTeacherRoute.value ? teacherNav : studentNav)])

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')

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
  display: flex;
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

.upgrade-pill {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  color: var(--accent-700);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  transition: all var(--duration-fast) var(--ease-out-expo);
  border: 1px solid var(--accent-200);
}

.upgrade-pill:hover {
  background: var(--accent-100);
  color: var(--accent-800);
  transform: translateY(-1px);
  box-shadow: var(--shadow-accent);
}

/* User Trigger */
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
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
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
  background: linear-gradient(135deg, var(--accent-400), var(--accent-600));
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
  .upgrade-pill {
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
</style>
