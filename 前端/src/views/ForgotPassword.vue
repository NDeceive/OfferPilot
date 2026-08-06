<template>
  <div class="auth-page" @mousemove="onMouseMove">
    <!-- Unified Background -->
    <div class="bg-layer">
      <div class="bg-gradient"></div>
      <div class="bg-grid"></div>
      <div class="bg-glow" :style="{ left: mouse.x + 'px', top: mouse.y + 'px' }"></div>
    </div>

    <!-- Left: Brand Panel -->
    <div class="brand-panel">
      <div class="brand-content">
        <router-link to="/" class="brand-logo">
          <LogoIcon :size="36" />
          <span class="brand-name">
            <span class="brand-cn">智面幻境</span>
            <span class="brand-en">OfferPilot</span>
          </span>
        </router-link>

        <h1 class="brand-title">找回账号<br /><span class="title-accent">继续准备</span></h1>
        <p class="brand-desc">验证注册邮箱后即可重设密码，你的练习记录不会受到影响。</p>
        <AuthShowcase :initial-slide="2" />

      </div>
    </div>

    <!-- Right: Reset Form -->
    <div class="form-panel">
      <div class="form-card">
        <router-link to="/login" class="back-link">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回登录
        </router-link>

        <!-- Step 1: Enter Email -->
        <div v-if="step === 1">
          <div class="form-header">
            <h1 class="form-title">忘记密码</h1>
            <p class="form-sub">输入注册邮箱，我们会向该地址发送重置链接</p>
          </div>

          <form class="auth-form" @submit.prevent="handleSend">
            <div class="field">
              <label class="field-label">注册邮箱</label>
              <div class="field-input">
                <svg class="field-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/>
                </svg>
                <input type="email" placeholder="请输入注册邮箱" v-model="email" required />
              </div>
            </div>

            <button type="submit" class="submit-btn" :disabled="!email">
              发送重置链接
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4 20-7z"/>
              </svg>
            </button>
          </form>
        </div>

        <!-- Step 2: Sent Confirmation -->
        <div v-else>
          <div class="form-header" style="text-align: center;">
            <div class="success-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
            </div>
            <h1 class="form-title" style="margin-top: var(--space-4);">邮件已发送</h1>
            <p class="form-sub">重置链接已发送至 <strong>{{ email }}</strong>，请查收邮件并点击链接重置密码</p>
          </div>

          <div class="info-box">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            <span>没有收到邮件? 请检查垃圾邮件文件夹，或 <button class="text-btn" @click="step = 1">重新发送</button></span>
          </div>

          <router-link to="/login" class="submit-btn" style="text-decoration: none; justify-content: center; margin-top: var(--space-6);">
            返回登录
          </router-link>
        </div>

        <p class="form-foot">
          还没有账号?<router-link to="/register" class="reg-link">立即注册</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import LogoIcon from '../components/ui/LogoIcon.vue'
import AuthShowcase from '../components/auth/AuthShowcase.vue'

const step = ref(1)
const email = ref('')
const mouse = reactive({ x: -200, y: -200 })

function onMouseMove(e) {
  mouse.x = e.clientX
  mouse.y = e.clientY
}

function handleSend() {
  if (!email.value) return
  step.value = 2
}
</script>

<style scoped>
.auth-page { min-height: 100dvh; display: flex; position: relative; overflow: hidden; }

.bg-layer { position: fixed; inset: 0; z-index: 0; pointer-events: none; }
.bg-gradient { position: absolute; inset: 0; background: linear-gradient(135deg, #f8faf9 0%, #f0faf5 30%, #fafafa 60%, #f5f5f5 100%); }
.bg-grid { position: absolute; inset: 0; background-image: radial-gradient(circle at 1px 1px, rgba(16, 185, 129, 0.06) 1px, transparent 0); background-size: 40px 40px; }
.bg-glow { position: fixed; width: 400px; height: 400px; border-radius: 50%; background: radial-gradient(circle, rgba(16, 185, 129, 0.08) 0%, transparent 70%); transform: translate(-50%, -50%); transition: left 0.3s ease-out, top 0.3s ease-out; pointer-events: none; }

/* Brand Panel */
.brand-panel { position: relative; z-index: 1; flex: 0 0 52%; display: flex; align-items: center; padding: var(--space-10); }
.brand-content { max-width: 580px; width: 100%; }
.brand-logo { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-10); text-decoration: none; }
.brand-name { display: flex; flex-direction: column; font-family: var(--font-display); color: var(--neutral-800); line-height: 1; }
.brand-name .brand-cn { font-size: 1.25rem; font-weight: 750; letter-spacing: -0.02em; }
.brand-name .brand-en { margin-top: 4px; font-size: 0.625rem; font-weight: 600; letter-spacing: 0.1em; color: var(--neutral-500); }
.brand-title { font-family: var(--font-display); font-size: clamp(1.75rem, 3.5vw, 2.5rem); font-weight: 700; color: var(--neutral-900); line-height: 1.25; letter-spacing: -0.03em; margin-bottom: var(--space-4); }
.title-accent { color: var(--accent-600); }
.brand-desc { font-size: var(--text-base); color: var(--neutral-500); line-height: 1.7; margin-bottom: var(--space-10); }

/* Lock Scene */
.lock-scene { display: flex; flex-direction: column; align-items: center; gap: var(--space-3); padding: var(--space-8); cursor: pointer; }
.lock-body { position: relative; color: var(--neutral-300); transition: color var(--duration-normal); }
.lock-scene:hover .lock-body { color: var(--accent-500); }
.lock-shackle { transition: all 0.5s var(--ease-spring); transform-origin: 12px 7px; }
.lock-shackle.open { transform: rotate(-30deg); }
.lock-base { transition: fill var(--duration-normal); }
.lock-scene:hover .lock-base { fill: rgba(16, 185, 129, 0.05); }
.lock-hint { font-size: 12px; color: var(--neutral-400); transition: color var(--duration-normal); }
.lock-scene:hover .lock-hint { color: var(--accent-500); }

/* Sparkles */
.lock-sparkles { position: absolute; inset: -10px; pointer-events: none; }
.sparkle { position: absolute; width: 4px; height: 4px; border-radius: 50%; background: var(--accent-400); opacity: 0; transition: opacity 0.3s; }
.lock-sparkles.active .sparkle { animation: sparkle-pop 0.6s var(--ease-out-expo) forwards; }
.s1 { top: 0; left: 50%; animation-delay: 0s; }
.s2 { top: 30%; right: 0; animation-delay: 0.1s; }
.s3 { bottom: 20%; left: 0; animation-delay: 0.2s; }
.s4 { top: 10%; right: 10%; animation-delay: 0.15s; }
@keyframes sparkle-pop {
  0% { opacity: 0; transform: scale(0); }
  50% { opacity: 1; transform: scale(1.5); }
  100% { opacity: 0; transform: scale(0); }
}

/* Form Panel */
.form-panel { position: relative; z-index: 1; flex: 1; display: flex; align-items: center; justify-content: center; padding: var(--space-6); }
.form-card { width: 100%; max-width: 440px; background: var(--surface-elevated); border: 1px solid var(--neutral-200); border-radius: var(--radius-xl); padding: var(--space-8); box-shadow: var(--shadow-lg); }
.back-link { display: inline-flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); color: var(--neutral-400); text-decoration: none; margin-bottom: var(--space-6); transition: color var(--duration-fast); }
.back-link:hover { color: var(--accent-600); }
.form-header { margin-bottom: var(--space-6); }
.form-title { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 700; color: var(--neutral-900); margin-bottom: var(--space-1); }
.form-sub { font-size: var(--text-sm); color: var(--neutral-500); line-height: 1.6; }
.form-sub strong { color: var(--neutral-700); font-weight: 600; }

/* Form */
.auth-form { display: flex; flex-direction: column; gap: var(--space-4); }
.field-label { display: block; font-size: var(--text-sm); font-weight: 500; color: var(--neutral-700); margin-bottom: var(--space-2); }
.field-input { position: relative; display: flex; align-items: center; }
.field-icon { position: absolute; left: 12px; color: var(--neutral-400); pointer-events: none; z-index: 1; }
.field-input input { width: 100%; padding: 12px 14px 12px 42px; border: 1.5px solid var(--neutral-200); border-radius: var(--radius-md); background: var(--surface-elevated); color: var(--neutral-800); font-family: var(--font-body); font-size: var(--text-base); outline: none; transition: all var(--duration-normal) var(--ease-out-expo); }
.field-input input::placeholder { color: var(--neutral-400); }
.field-input input:focus { border-color: var(--accent-400); box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1); }

/* Success Icon */
.success-icon { width: 72px; height: 72px; border-radius: 50%; background: var(--accent-50); display: flex; align-items: center; justify-content: center; margin: 0 auto; animation: scale-in 0.4s var(--ease-out-expo); }
@keyframes scale-in { from { transform: scale(0); opacity: 0; } to { transform: scale(1); opacity: 1; } }

/* Info Box */
.info-box { display: flex; align-items: flex-start; gap: var(--space-3); padding: var(--space-4); background: var(--neutral-50); border: 1px solid var(--neutral-200); border-radius: var(--radius-md); font-size: var(--text-sm); color: var(--neutral-600); line-height: 1.6; margin-top: var(--space-6); }
.info-box svg { flex-shrink: 0; color: var(--neutral-400); margin-top: 2px; }
.text-btn { background: none; border: none; color: var(--accent-600); font-weight: 600; cursor: pointer; font-size: inherit; padding: 0; }
.text-btn:hover { color: var(--accent-700); text-decoration: underline; }

/* Submit */
.submit-btn { width: 100%; display: flex; align-items: center; justify-content: center; gap: var(--space-2); padding: 14px; border: none; border-radius: var(--radius-md); background: var(--accent-500); color: white; font-family: var(--font-display); font-size: var(--text-base); font-weight: 600; cursor: pointer; transition: all var(--duration-normal) var(--ease-out-expo); margin-top: var(--space-2); }
.submit-btn:hover:not(:disabled) { background: var(--accent-600); box-shadow: var(--shadow-accent-lg); transform: translateY(-2px); }
.submit-btn:active { transform: scale(0.98); }
.submit-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.submit-btn svg { transition: transform 0.3s var(--ease-spring); }
.submit-btn:hover:not(:disabled) svg { transform: translateX(4px) translateY(-2px); }

.form-foot { text-align: center; margin-top: var(--space-6); font-size: var(--text-sm); color: var(--neutral-500); }
.reg-link { color: var(--accent-600); font-weight: 600; margin-left: 4px; text-decoration: none; }
.reg-link:hover { color: var(--accent-700); }

/* Responsive */
@media (max-width: 900px) {
  .auth-page { flex-direction: column; }
  .brand-panel { flex: 0 0 auto; padding: var(--space-8) var(--space-6) var(--space-4); }
  .brand-title { font-size: var(--text-2xl); }
  .lock-scene { display: none; }
  .form-panel { padding: var(--space-4) var(--space-6) var(--space-8); }
  .form-card { padding: var(--space-6); box-shadow: none; }
}

@media (prefers-reduced-motion: reduce) {
  .bg-glow { display: none; }
  .sparkle { animation: none !important; }
}
</style>
