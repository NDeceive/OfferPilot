<template>
  <!--
    只在「连不上后端」时弹一次确认框（用户点了才切到离线模式）。刻意不再显示
    任何常驻横幅：离线演示要和正常使用看起来完全一样，进去之后看不出是离线。
    切换方式 = 杀掉 App 重进：登录能成功即在线；连不上时在登录页选「离线演示」
    进入（进去后无任何标识）。
  -->

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
          <li>想切回真实数据：杀掉 App 重新打开、重新登录即可（登录页也能随时退出离线演示）。</li>
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
import { getApiBase } from '../../utils/apiBase'
import { answerDemoPrompt, demoState } from '../../utils/offlineDemo'

// getApiBase() 读的是 localStorage，不是响应式的；弹框一次只弹一下，取了就够
const apiBase = computed(() => (demoState.asking ? getApiBase() : ''))

function answer(accepted) {
  answerDemoPrompt(accepted)
}
</script>

<style scoped>
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
