<template>
  <div class="mobile-shell">
    <header v-if="brand" class="mobile-brandbar">
      <router-link to="/home" class="mobile-brand" aria-label="OfferPilot 首页">
        <LogoIcon :size="40" />
        <span><strong>OfferPilot</strong><small>智面幻境</small></span>
      </router-link>
      <router-link to="/profile" class="mobile-avatar" aria-label="个人中心">
        <img :src="userAvatar" alt="" />
      </router-link>
    </header>
    <header v-else class="mobile-pagebar">
      <div><h1>{{ title }}</h1><p v-if="subtitle">{{ subtitle }}</p></div>
      <slot name="header-action" />
    </header>

    <main class="mobile-main" :class="{ 'is-navless': !nav }"><slot /></main>

    <MobileTabBar v-if="nav" />
  </div>
</template>

<script setup>
import LogoIcon from '../../components/ui/LogoIcon.vue'
import userAvatar from '../../assets/generated/user-avatar-ui.png'
import MobileTabBar from './MobileTabBar.vue'

defineProps({
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  brand: { type: Boolean, default: false },
  // 全屏页面（如 AI 对话）传 false 关掉底栏，同时去掉内容区底部留白
  nav: { type: Boolean, default: true },
})
</script>
