<template>
  <div class="layout-shell">
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <button class="sidebar-brand" @click="router.push('/home')">
        <img class="brand-mark" :src="brandMark" alt="智面幻境" />
        <span class="brand-copy" v-show="!sidebarCollapsed">
          <strong>智面幻境</strong>
          <small>AI模拟面试训练系统</small>
        </span>
      </button>

      <nav class="sidebar-nav" aria-label="主导航">
        <router-link
          v-for="item in navItems"
          :key="item.label"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label" v-show="!sidebarCollapsed">{{ item.label }}</span>
        </router-link>
      </nav>

      <section class="member-card" v-show="!sidebarCollapsed">
        <div class="member-gem">
          <el-icon><PriceTag /></el-icon>
        </div>
        <strong>会员中心</strong>
        <span>解锁更多功能与权益</span>
        <button type="button">立即升级 <el-icon><ArrowRight /></el-icon></button>
      </section>

      <button class="collapse-btn" type="button" @click="sidebarCollapsed = !sidebarCollapsed">
        <el-icon><component :is="sidebarCollapsed ? Expand : Fold" /></el-icon>
      </button>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="search-shell">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" type="search" placeholder="搜索岗位、知识点或面试题" />
          <kbd>⌘ K</kbd>
        </div>

        <div class="topbar-actions">
          <button class="icon-button" type="button" aria-label="通知">
            <el-badge v-if="notificationCount > 0" :value="notificationCount" :max="99">
              <el-icon><Bell /></el-icon>
            </el-badge>
            <el-icon v-else><Bell /></el-icon>
          </button>

          <el-dropdown @command="handleCommand" trigger="click">
            <button class="user-chip" type="button">
              <img :src="userAvatar" alt="" />
              <span>{{ displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ArrowDown,
  ArrowRight,
  Bell,
  Coordinate,
  DataAnalysis,
  EditPen,
  Expand,
  Fold,
  Histogram,
  HomeFilled,
  Notebook,
  PieChart,
  PriceTag,
  Promotion,
  Search,
  Setting,
  SwitchButton,
  Tickets,
  User,
  UserFilled,
  VideoCamera
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import brandMark from '@/assets/generated/brand-mark-ui.png'
import userAvatar from '@/assets/generated/user-avatar-ui.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const sidebarCollapsed = ref(false)
const keyword = ref('')
const notificationCount = ref(0)

// 学生端菜单（保持原样）
const studentNav = [
  { path: '/home', label: '首页', icon: HomeFilled },
  { path: '/jobs', label: '面试准备', icon: EditPen },
  { path: '/interview', label: '模拟面试', icon: VideoCamera },
  { path: '/history', label: '面试记录', icon: Tickets },
  { path: '/report', label: '能力报告', icon: DataAnalysis },
  { path: '/followup-records', label: '追问记录', icon: Histogram },
  { path: '/profile', label: '个人中心', icon: User },
  { path: '/profile?tab=settings', label: '设置', icon: Setting }
]

// 教师端菜单（Phase 5.1 仅总览页可用，其余为后续阶段占位）
const teacherNav = [
  { path: '/teacher/dashboard', label: '教师端总览', icon: HomeFilled },
  { path: '/teacher/dashboard', label: '学生训练', icon: UserFilled },
  { path: '/teacher/dashboard', label: '班级统计', icon: PieChart },
  { path: '/teacher/dashboard', label: '共性短板', icon: Coordinate },
  { path: '/teacher/dashboard', label: '任务发布', icon: Promotion },
  { path: '/teacher/dashboard', label: '报告分析', icon: DataAnalysis },
  { path: '/teacher/dashboard', label: '追问记录', icon: Histogram },
  { path: '/profile', label: '个人中心', icon: User }
]

// 按角色渲染菜单：TEACHER 看教师端，ADMIN 暂时也看教师端，其余（含 STUDENT）看学生端。
const navItems = computed(() => {
  const role = userStore.role
  return role === 'TEACHER' || role === 'ADMIN' ? teacherNav : studentNav
})

const displayName = computed(() => userStore.nickname || userStore.username || '用户')

const isActive = (path) => {
  const basePath = path.split('?')[0]
  if (basePath === '/home') return route.path === '/home'
  return route.path.startsWith(basePath)
}

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout-shell {
  display: flex;
  min-width: 320px;
  height: 100dvh;
  overflow: hidden;
  background: #f7faff;
}

.sidebar {
  position: relative;
  z-index: 20;
  display: flex;
  flex: 0 0 248px;
  flex-direction: column;
  width: 248px;
  min-width: 248px;
  padding: 44px 20px 24px;
  background: rgba(255, 255, 255, 0.94);
  border-right: 1px solid rgba(215, 225, 241, 0.92);
  box-shadow: 12px 0 34px rgba(35, 83, 149, 0.04);
  transition: width 0.22s ease, min-width 0.22s ease, flex-basis 0.22s ease;
}

.sidebar.collapsed {
  flex-basis: 76px;
  width: 76px;
  min-width: 76px;
  padding-inline: 14px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 0;
  margin-bottom: 38px;
  color: inherit;
  text-align: left;
  background: none;
  border: 0;
  cursor: pointer;
}

.brand-mark {
  width: 52px;
  height: 52px;
  object-fit: cover;
  border-radius: 18px;
  mix-blend-mode: multiply;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  white-space: nowrap;
}

.brand-copy strong {
  color: #07172f;
  font-size: 22px;
  font-weight: 900;
  line-height: 1.12;
}

.brand-copy small {
  margin-top: 6px;
  color: #596a86;
  font-size: 12px;
  font-weight: 500;
}

.sidebar-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 50px;
  padding: 0 18px;
  color: #506282;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 700;
  transition: color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
  white-space: nowrap;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding-inline: 0;
}

.nav-item:hover {
  color: #1769ff;
  background: rgba(39, 117, 255, 0.07);
}

.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, #347bff 0%, #1264ff 100%);
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.24);
}

.nav-icon {
  flex: 0 0 auto;
  font-size: 21px;
}

.nav-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.member-card {
  padding: 18px 16px 16px;
  color: #10213c;
  background: linear-gradient(180deg, #f7f9ff 0%, #ffffff 100%);
  border: 1px solid #cddafd;
  border-radius: 8px;
  box-shadow: 0 18px 34px rgba(61, 103, 190, 0.07);
}

.member-gem {
  display: grid;
  width: 44px;
  height: 44px;
  margin-bottom: 10px;
  color: #5d7cff;
  place-items: center;
  background: #eef3ff;
  border-radius: 12px;
  font-size: 23px;
}

.member-card strong,
.member-card span {
  display: block;
}

.member-card strong {
  font-size: 16px;
  font-weight: 900;
}

.member-card span {
  margin-top: 5px;
  color: #7c8aa3;
  font-size: 12px;
}

.member-card button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 44px;
  margin-top: 18px;
  color: #1c6dff;
  background: #fff;
  border: 1px solid #cbd7f5;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.collapse-btn {
  display: grid;
  width: 38px;
  height: 38px;
  margin: 16px auto 0;
  color: #627391;
  place-items: center;
  background: #fff;
  border: 1px solid #dbe4f3;
  border-radius: 10px;
  cursor: pointer;
}

.main-area {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  min-height: 72px;
  padding: 0 34px;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(215, 225, 241, 0.92);
  backdrop-filter: blur(18px);
}

.search-shell {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(420px, 43vw);
  height: 44px;
  padding: 0 12px 0 16px;
  color: #8a98b1;
  background: #fff;
  border: 1px solid #dbe4f3;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(35, 83, 149, 0.04);
}

.search-shell input {
  flex: 1;
  min-width: 0;
  height: 100%;
  color: #10213c;
  background: transparent;
  border: 0;
  outline: none;
  font-size: 14px;
}

.search-shell kbd {
  min-width: 50px;
  padding: 6px 8px;
  color: #687792;
  background: #f3f6fb;
  border-radius: 8px;
  font: 700 13px/1 var(--font-ui);
  text-align: center;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.icon-button,
.user-chip {
  display: inline-flex;
  align-items: center;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
}

.icon-button {
  color: #24375a;
  font-size: 24px;
}

.user-chip {
  gap: 10px;
  color: #0b1e39;
  font-size: 16px;
  font-weight: 700;
}

.user-chip img {
  width: 42px;
  height: 42px;
  object-fit: cover;
  border-radius: 50%;
  box-shadow: 0 8px 18px rgba(42, 83, 154, 0.12);
}

.main-content {
  flex: 1;
  overflow: auto;
  padding: 28px 44px 34px;
  background:
    radial-gradient(circle at 16% 5%, rgba(66, 129, 255, 0.08), transparent 25rem),
    linear-gradient(180deg, #f8fbff 0%, #f6f9ff 100%);
}

@media (max-width: 1280px) {
  .sidebar {
    flex-basis: 228px;
    width: 228px;
    min-width: 228px;
    padding-inline: 18px;
  }

  .topbar {
    padding-inline: 28px;
  }

  .main-content {
    padding-inline: 32px;
  }
}

@media (max-width: 900px) {
  .layout-shell {
    height: auto;
    min-height: 100dvh;
    overflow: visible;
  }

  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    transform: translateX(0);
  }

  .sidebar.collapsed {
    transform: translateX(-100%);
  }

  .main-area {
    min-height: 100dvh;
    margin-left: 76px;
  }

  .topbar {
    justify-content: space-between;
    min-height: 64px;
    padding-inline: 18px;
  }

  .search-shell {
    width: min(100%, 420px);
  }

  .user-chip span,
  .search-shell kbd {
    display: none;
  }

  .main-content {
    padding: 24px 18px;
  }
}

@media (max-width: 640px) {
  .main-area {
    margin-left: 0;
  }

  .sidebar {
    flex-basis: 224px;
    width: 224px;
    min-width: 224px;
  }

  .topbar-actions {
    gap: 12px;
  }

  .search-shell {
    height: 44px;
  }
}
</style>
