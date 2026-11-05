<template>
  <nav class="mtb" aria-label="移动端主导航" :style="{ gridTemplateColumns: `repeat(${list.length}, 1fr)` }">
    <router-link
      v-for="item in list"
      :key="item.path"
      :to="item.path"
      :aria-label="item.label"
    >
      <span class="mtb__icon" v-html="item.icon" aria-hidden="true"></span>
      <span class="mtb__label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

// 自包含：样式全部写在这个组件里，不依赖 mobile.css。
// 因为 AppLayout 会用它，而 AppLayout 会在「没走过 AdaptiveStudentView」的路由上直接渲染
// （比如直接打开 /settings），那时 mobile.css 根本没被注入，靠 --m-* 变量会全线失效。
//
// 学生端固定四个主入口：首页 / 刷题 / 经验 / 我的。
// 面试流程不再是手机端的一级入口，完整面试只保留在桌面端。
// items 省略时用这四项；教师/企业传入自己的项（见 ../nav.js），列数跟随 items.length。
const props = defineProps({
  items: { type: Array, default: null },
})

const studentItems = [
  { path: '/home', label: '首页', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-6h6v6"/></svg>' },
  { path: '/learning', label: '刷题', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 4h12a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z"/><path d="M8 8h8M8 12h6"/></svg>' },
  { path: '/experience', label: '经验', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 20.5A2.5 2.5 0 0 1 6.5 18H20"/><path d="M8 8h8M8 11.5h6"/></svg>' },
  { path: '/profile', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>' },
]

const list = computed(() => (Array.isArray(props.items) && props.items.length ? props.items : studentItems))
</script>

<style scoped>
.mtb {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  display: grid;
  height: calc(64px + env(safe-area-inset-bottom));
  padding: 6px max(12px, calc((100vw - 480px) / 2)) env(safe-area-inset-bottom);
  grid-template-columns: repeat(4, 1fr);
  background: color-mix(in srgb, var(--surface-elevated) 96%, transparent);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--m-border, #e6e9e5);
}

.mtb a {
  display: flex;
  min-width: 44px;
  min-height: 52px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 2px;
  color: #68736d;
  font-size: 11px;
  text-decoration: none;
  transition: color 200ms cubic-bezier(.16, 1, .3, 1);
}

.mtb__icon {
  display: grid;
  width: 40px;
  height: 28px;
  border-radius: 999px;
  place-items: center;
  transition: background 200ms cubic-bezier(.16, 1, .3, 1), transform 200ms cubic-bezier(.16, 1, .3, 1);
}
.mtb__icon :deep(svg) { display: block; width: 21px; height: 21px; }

.mtb a.router-link-active { color: #2f7458; font-weight: 700; }
.mtb a.router-link-active .mtb__icon { background: var(--m-primary-soft, #e4f2e8); transform: translateY(-1px); }
.mtb a:active .mtb__icon { transform: scale(.9); }

/* Restore the pre-sync light appearance; retain current dark-theme rules. */
:root:not([data-theme="dark"]) .mtb {
    background: rgba(255, 255, 255, .96)
}
</style>
