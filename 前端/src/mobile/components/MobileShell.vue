<template>
  <div class="mobile-shell">
    <header v-if="brand" class="mobile-brandbar">
      <router-link :to="homePath" class="mobile-brand" aria-label="OfferPilot 首页">
        <BrandLogo :width="180" />
      </router-link>
      <div class="mobile-header-actions">
        <router-link v-if="messages" :to="messagePath" class="mobile-message-trigger" aria-label="消息中心" title="消息中心">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg>
        </router-link>
        <router-link :to="profilePath" class="mobile-avatar" aria-label="个人中心">
          <img :src="store.avatar || userAvatar" alt="" />
        </router-link>
      </div>
    </header>
    <header v-else class="mobile-pagebar">
      <div><h1>{{ title }}</h1><p v-if="subtitle">{{ subtitle }}</p></div>
      <div class="mobile-header-actions">
        <router-link v-if="messages" :to="messagePath" class="mobile-message-trigger" aria-label="消息中心" title="消息中心">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg>
        </router-link>
        <slot name="header-action" />
      </div>
    </header>

    <!-- 教学联动入口：全屏页（nav=false，如 AI 对话）不加，避免挤占输入区。
         教师/企业壳不需要（管理入口都在底部导航里），传空数组即隐藏。 -->
    <nav v-if="nav && links.length" class="mobile-teaching-links" aria-label="教学联动">
      <router-link v-for="item in links" :key="item.path" :to="item.path">{{ item.label }}</router-link>
    </nav>

    <main class="mobile-main" :class="{ 'is-navless': !nav }"><slot /></main>

    <MobileTabBar v-if="nav" :items="tabItems" />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { getMe } from '../../api'
import { useUserStore } from '../../store/user'
import BrandLogo from '../../components/ui/BrandLogo.vue'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import MobileTabBar from './MobileTabBar.vue'

const store = useUserStore()
// 头像可能是后换的：进页面时同步一次，已有档案就不重复拉
onMounted(() => {
  if (!store.profileLoaded) {
    getMe().then((user) => { if (user) store.syncProfile(user) }).catch(() => {})
  }
})

const props = defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  brand: { type: Boolean, default: false },
  // 全屏页面（如 AI 对话）传 false 关掉底栏和教学联动条，同时去掉内容区底部留白
  nav: { type: Boolean, default: true },
  // 以下四项默认值 = 学生壳行为，教师/企业页传自己的值覆盖
  homePath: { type: String, default: '/home' },
  messagePath: { type: String, default: '/my/messages' },
  profilePath: { type: String, default: '/profile' },
  // null = 学生默认「我的班级 / 教学任务」两条；传 [] 隐藏；传数组则按数组渲染
  navLinks: { type: Array, default: null },
  // 透传给 MobileTabBar；null 时它就是学生默认五项
  tabItems: { type: Array, default: null },
  // 企业壳没有消息中心页（AppLayout 里也对学生/企业/教师分别收过口），传 false 隐藏铃铛
  messages: { type: Boolean, default: true },
})

const studentLinks = [
  { path: '/my/classes', label: '我的班级' },
  { path: '/my/tasks', label: '教学任务' },
]
const links = computed(() => props.navLinks === null ? studentLinks : props.navLinks)
</script>

<style scoped>
.mobile-header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.mobile-message-trigger {
  display: grid;
  min-width: 44px;
  min-height: 44px;
  color: var(--m-text-secondary, #526159);
  place-items: center;
  border-radius: 8px;
}
.mobile-message-trigger:hover,
.mobile-message-trigger.router-link-active {
  color: var(--m-primary, #2f7458);
  background: var(--m-primary-soft, #e8f3ec);
}
.mobile-message-trigger:focus-visible { outline: 2px solid var(--m-primary, #2f7458); outline-offset: 2px; }

.mobile-teaching-links { display: flex; gap: 8px; padding: 12px 20px 0; }
.mobile-teaching-links a {
  display: inline-flex;
  min-height: 34px;
  padding: 0 14px;
  align-items: center;
  color: var(--m-primary, #2f7458);
  background: var(--m-primary-soft, #e8f3ec);
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 700;
  text-decoration: none;
}
.mobile-teaching-links a.router-link-active { color: #fff; background: var(--m-primary, #2f7458); }
.mobile-teaching-links a:focus-visible { outline: 2px solid var(--m-primary, #2f7458); outline-offset: 2px; }
</style>
