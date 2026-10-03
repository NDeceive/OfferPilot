<template>
  <div class="mobile-shell">
    <header v-if="brand" class="mobile-brandbar">
      <router-link to="/home" class="mobile-brand" aria-label="OfferPilot 首页">
        <BrandLogo :width="180" />
      </router-link>
      <div class="mobile-header-actions">
      <router-link to="/my/messages" class="mobile-message-trigger" aria-label="消息中心" title="消息中心"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg></router-link>
      <router-link to="/profile" class="mobile-avatar" aria-label="个人中心">
        <img :src="store.avatar || userAvatar" alt="" />
      </router-link>
      </div>
    </header>
    <header v-else class="mobile-pagebar">
      <div><h1>{{ title }}</h1><p v-if="subtitle">{{ subtitle }}</p></div>
      <div class="mobile-header-actions"><router-link to="/my/messages" class="mobile-message-trigger" aria-label="消息中心" title="消息中心"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z"/><path d="M10 21h4"/></svg></router-link><slot name="header-action" /></div>
    </header>

    <nav class="mobile-teaching-links" aria-label="教学联动"><router-link to="/my/classes">我的班级</router-link><router-link to="/my/tasks">教学任务</router-link></nav><main class="mobile-main"><slot /></main>

    <nav class="mobile-bottom-nav" aria-label="移动端主导航">
      <router-link v-for="item in nav" :key="item.path" :to="item.path" :aria-label="item.label">
        <span class="mobile-nav-icon" v-html="item.icon" aria-hidden="true"></span>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { getMe } from '../../api'
import { useUserStore } from '../../store/user'
import BrandLogo from '../../components/ui/BrandLogo.vue'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
const store = useUserStore()
onMounted(() => { if (!store.profileLoaded) getMe().then(user => { if (user) store.syncProfile(user) }).catch(() => {}) })

defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  brand: { type: Boolean, default: false },
})

const nav = [
  { path: '/home', label: '首页', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-6h6v6"/></svg>' },
  { path: '/learning', label: '刷题', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 4h12a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z"/><path d="M8 8h8M8 12h6"/></svg>' },
  { path: '/history', label: '记录', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>' },
  { path: '/profile', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>' },
]
</script>
<style scoped>
.mobile-header-actions{display:flex;align-items:center;gap:8px;flex-shrink:0}.mobile-message-trigger{display:grid;place-items:center;min-width:44px;min-height:44px;border-radius:8px;color:var(--m-text-secondary,#526159)}.mobile-message-trigger:hover,.mobile-message-trigger.router-link-active{background:var(--m-surface,#f2f7f4);color:var(--accent-600,#059669)}.mobile-message-trigger:focus-visible{outline:2px solid var(--accent-600,#059669);outline-offset:2px}
</style>
