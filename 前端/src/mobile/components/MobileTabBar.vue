<template>
  <nav class="mtb" aria-label="移动端主导航">
    <router-link
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      :aria-label="item.label"
      :class="{ 'is-center': item.center }"
    >
      <span class="mtb__icon" v-html="item.icon" aria-hidden="true"></span>
      <span class="mtb__label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
// 自包含：样式全部写在这个组件里，不依赖 mobile.css。
// 因为 AppLayout 会用它，而 AppLayout 会在「没走过 AdaptiveStudentView」的路由上直接渲染
// （比如直接打开 /settings），那时 mobile.css 根本没被注入，靠 --m-* 变量会全线失效。
//
// 中间「面试」做成浮起主入口。只靠位移，列数不变，仍与其余四项等分。

const items = [
  { path: '/home', label: '首页', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-6h6v6"/></svg>' },
  { path: '/learning', label: '刷题', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 4h12a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2z"/><path d="M8 8h8M8 12h6"/></svg>' },
  { path: '/interview/ai', label: '面试', center: true, icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-7a8 8 0 0 1 8-8h2a8 8 0 0 1 8 4z"/><path d="M9 12h.01M13 12h.01M17 12h.01"/></svg>' },
  { path: '/history', label: '记录', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>' },
  { path: '/profile', label: '我的', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>' },
]
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
  grid-template-columns: repeat(5, 1fr);
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(12px);
  border-top: 1px solid #e6e9e5;
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
}

.mtb__icon { display: block; width: 21px; height: 21px; }
.mtb__icon :deep(svg) { display: block; width: 21px; height: 21px; }

.mtb a.router-link-active { color: #2f7458; font-weight: 700; }

/* 浮起的中间主入口 */
.mtb a.is-center { position: relative; color: #174b39; font-weight: 700; }
.mtb a.is-center .mtb__icon {
  display: grid;
  width: 46px;
  height: 46px;
  margin-top: -22px;
  color: #fff;
  background: #2f7458;
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 8px 18px rgba(23, 75, 57, .28);
  place-items: center;
}
.mtb a.is-center .mtb__icon :deep(svg) { width: 22px; height: 22px; }
.mtb a.is-center.router-link-active .mtb__icon { background: #174b39; }
.mtb a.is-center:active .mtb__icon { transform: scale(.94); }
</style>
