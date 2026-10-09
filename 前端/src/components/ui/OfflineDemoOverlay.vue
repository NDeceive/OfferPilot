<template>
  <!--
    常驻横幅。刻意不做成「一个小图标」，因为它是整个离线演示模式的唯一保险：
    假数据比没数据更危险——界面无声无息换成假数字，讲的人未必察觉，就会照着编。

    位置按平台各挑一边「没被固定元素占住」的边：
      · 手机：底部被 tab 栏钉死了，走顶部（移动端的标题栏是普通文档流，压不着谁）
      · 桌面：顶部被固定顶栏占着，走底部
  -->
  <div v-if="demoState.active" class="demo-banner" role="status">
    <span class="demo-banner-dot" aria-hidden="true"></span>
    <p><b>离线演示模式</b><span class="demo-banner-sep">·</span>当前是内置演示数据，不是真实面试结果</p>
    <button type="button" @click="turnOff">退出</button>
  </div>

  <!-- 确认框：连不上后端时问一次，用户点了才切 -->
  <Teleport to="body">
    <div v-if="demoState.asking" class="demo-mask" role="dialog" aria-modal="true" aria-labelledby="demo-ask-title">
      <div class="demo-dialog">
        <h2 id="demo-ask-title">连不上后端，要切到离线演示吗？</h2>
        <p class="demo-dialog-sub">
          当前服务器地址：<code>{{ apiBase }}</code>
        </p>
        <ul class="demo-dialog-list">
          <li>切过去之后，岗位、面试题、评分报告都会换成<b>内置的演示数据</b>，不是真实结果。</li>
          <li>顶部（手机）/ 底部（桌面）会一直挂着一条横幅提醒，不会悄悄换回来。</li>
          <li>想连真后端，检查手机和电脑是否在同一个 WiFi，或用登录页的「服务器设置」改地址。</li>
        </ul>
        <div class="demo-dialog-actions">
          <button type="button" class="demo-btn demo-btn--ghost" @click="answer(false)">先不切</button>
          <button type="button" class="demo-btn demo-btn--primary" @click="answer(true)">切到离线演示</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { DEMO_TOKEN } from '../../utils/demoAdapter'
import { getApiBase } from '../../utils/apiBase'
import { answerDemoPrompt, demoState, setDemoActive } from '../../utils/offlineDemo'
import { useUserStore } from '../../store/user'

const userStore = useUserStore()

// getApiBase() 读的是 localStorage，不是响应式的；弹框一次只弹一下，取了就够
const apiBase = computed(() => (demoState.asking ? getApiBase() : ''))

function answer(accepted) {
  answerDemoPrompt(accepted)
}

/**
 * 退出演示模式。
 *
 * 演示模式下拿到的登录态是假的（DEMO_TOKEN），直接带着它去连真后端只会一路 401，
 * 所以先把它清掉，让人回登录页重新登。
 *
 * 最后整页重载：演示模式下攒的东西——内存里的面试会话、报告、走过的路由——本来就
 * 该随模式一起丢掉，逐个复位反而容易漏。
 */
function turnOff() {
  setDemoActive(false)
  if (userStore.token === DEMO_TOKEN) userStore.logout()
  window.location.reload()
}
</script>

<style scoped>
/* === 横幅 === */
.demo-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  color: #7c4a03;
  background: #fff7e6;
  border-bottom: 1px solid #f5d9a8;
  font-family: var(--font-ui);
  font-size: 13px;
  line-height: 1.4;
}

.demo-banner p {
  flex: 1;
  margin: 0;
  min-width: 0;
}

.demo-banner b {
  font-weight: 700;
}

.demo-banner-sep {
  margin: 0 5px;
  opacity: 0.5;
}

.demo-banner-dot {
  flex: none;
  width: 8px;
  height: 8px;
  background: var(--warning, #f59e0b);
  border-radius: 50%;
}

.demo-banner button {
  flex: none;
  padding: 4px 10px;
  color: #7c4a03;
  background: transparent;
  border: 1px solid #e2b96b;
  border-radius: 999px;
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;
}

.demo-banner button:hover {
  background: #fdedcd;
}

/* 手机：钉在视口顶部，滚到哪儿都看得见 */
@media (max-width: 767.98px) {
  .demo-banner {
    position: sticky;
    top: 0;
    z-index: 999;
    padding-top: max(8px, env(safe-area-inset-top));
  }
}

/* 桌面：底部完全没被固定元素占着，钉在这儿不会压住顶栏和侧栏 */
@media (min-width: 768px) {
  .demo-banner {
    position: fixed;
    right: 20px;
    bottom: 20px;
    left: auto;
    z-index: 999;
    max-width: 520px;
    padding: 10px 12px 10px 16px;
    border: 1px solid #f5d9a8;
    border-radius: var(--radius, 8px);
    box-shadow: var(--shadow-md, 0 16px 40px rgba(37, 99, 235, 0.13));
  }
}

/* === 确认框 === */
.demo-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.45);
}

.demo-dialog {
  width: 100%;
  max-width: 440px;
  padding: 24px;
  color: var(--text, #172033);
  background: var(--surface, #fff);
  border-radius: 14px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.28);
  font-family: var(--font-ui);
}

.demo-dialog h2 {
  margin: 0 0 10px;
  font-size: 17px;
  line-height: 1.4;
}

.demo-dialog-sub {
  margin: 0 0 14px;
  color: var(--text-muted, #667085);
  font-size: 13px;
  word-break: break-all;
}

.demo-dialog-sub code {
  padding: 2px 6px;
  background: var(--surface-soft, #f8fbff);
  border: 1px solid var(--border, #dce6f2);
  border-radius: 4px;
  font-size: 12px;
}

.demo-dialog-list {
  margin: 0 0 20px;
  padding-left: 18px;
  color: var(--text-muted, #667085);
  font-size: 13px;
  line-height: 1.7;
}

.demo-dialog-list b {
  color: var(--text, #172033);
}

.demo-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.demo-btn {
  padding: 9px 18px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.demo-btn--ghost {
  color: var(--text-muted, #667085);
  background: transparent;
  border: 1px solid var(--border, #dce6f2);
}

.demo-btn--ghost:hover {
  background: var(--surface-soft, #f8fbff);
}

.demo-btn--primary {
  color: #fff;
  background: var(--primary, #2563eb);
  border: 1px solid var(--primary, #2563eb);
}

.demo-btn--primary:hover {
  background: var(--primary-dark, #1d4ed8);
}
</style>
