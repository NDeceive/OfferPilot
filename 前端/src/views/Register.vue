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

        <h1 class="brand-title">从一次完整练习<br /><span class="title-accent">开始准备</span></h1>
        <p class="brand-desc">建立岗位、简历与回答之间的联系，让每一步准备都有记录可循。</p>
        <AuthShowcase :initial-slide="1" />

      </div>
    </div>

    <!-- Right: Register Form -->
    <div class="form-panel">
      <div class="form-card">
        <router-link to="/login" class="back-link">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          返回首页
        </router-link>

        <div class="form-header">
          <h1 class="form-title">创建账号</h1>
          <p class="form-sub">创建账号，保存每次练习与复盘</p>
        </div>

        <form class="auth-form" @submit.prevent="handleRegister">
          <div class="field">
            <label class="field-label">用户名</label>
            <div class="field-input">
              <svg class="field-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              <input type="text" placeholder="请输入用户名" v-model="form.username" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">邮箱</label>
            <div class="field-input">
              <svg class="field-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/>
              </svg>
              <input type="email" placeholder="请输入邮箱" v-model="form.email" />
            </div>
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="field-input">
              <svg class="field-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input :type="showPwd ? 'text' : 'password'" placeholder="至少 8 位，含字母和数字" v-model="form.password" />
              <button type="button" class="pwd-toggle" @click="showPwd = !showPwd">
                <svg v-if="!showPwd" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                </svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              </button>
            </div>
            <!-- Password Strength -->
            <div class="pwd-strength" v-if="form.password">
              <div class="strength-bars">
                <div v-for="i in 4" :key="i" class="strength-bar" :class="{ active: pwdStrength >= i, [strengthClass]: pwdStrength >= i }"></div>
              </div>
              <span class="strength-label" :class="strengthClass">{{ strengthText }}</span>
            </div>
          </div>

          <div class="field">
            <label class="field-label">确认密码</label>
            <div class="field-input">
              <svg class="field-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <input type="password" placeholder="再次输入密码" v-model="form.confirmPwd" />
            </div>
            <span v-if="form.confirmPwd && form.password !== form.confirmPwd" class="field-error">两次密码不一致</span>
          </div>

          <label class="checkbox-label">
            <input type="checkbox" v-model="form.agree" />
            <span class="cb-box"></span>
            我已阅读并同意 <a href="#" class="inline-link">服务协议</a> 和 <a href="#" class="inline-link">隐私政策</a>
          </label>

          <button type="submit" class="submit-btn" :disabled="!canSubmit">
            注 册
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M5 12h14"/><path d="m12 5 7 7-7 7"/>
            </svg>
          </button>
        </form>

        <p class="form-foot">
          已有账号?<router-link to="/login" class="reg-link">立即登录</router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { register as registerApi } from '../api'
import LogoIcon from '../components/ui/LogoIcon.vue'
import AuthShowcase from '../components/auth/AuthShowcase.vue'

const router = useRouter()
const showPwd = ref(false)
const errorMsg = ref('')
const loading = ref(false)
const mouse = reactive({ x: -200, y: -200 })

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPwd: '',
  agree: false,
})

const pwdStrength = computed(() => {
  const p = form.password
  if (!p) return 0
  let score = 0
  if (p.length >= 8) score++
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^a-zA-Z0-9]/.test(p)) score++
  return score
})

const strengthClass = computed(() => {
  if (pwdStrength.value <= 1) return 'weak'
  if (pwdStrength.value === 2) return 'fair'
  if (pwdStrength.value === 3) return 'good'
  return 'strong'
})

const strengthText = computed(() => {
  if (pwdStrength.value <= 1) return '弱'
  if (pwdStrength.value === 2) return '一般'
  if (pwdStrength.value === 3) return '良好'
  return '强'
})

const canSubmit = computed(() =>
  form.username && form.email && form.password.length >= 8 &&
  form.password === form.confirmPwd && form.agree
)

function onMouseMove(e) {
  mouse.x = e.clientX
  mouse.y = e.clientY
}

async function handleRegister() {
  if (!canSubmit.value || loading.value) return
  errorMsg.value = ''
  loading.value = true
  try {
    await registerApi({ username: form.username, email: form.email, password: form.password })
    router.push('/login')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || e.message || '注册失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100dvh;
  display: flex;
  position: relative;
  overflow: hidden;
}

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

/* Feature Steps */
.feature-steps { display: flex; flex-direction: column; gap: var(--space-1); }
.fstep { display: flex; align-items: center; gap: var(--space-4); padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); cursor: pointer; transition: all var(--duration-normal) var(--ease-out-expo); position: relative; }
.fstep:hover, .fstep.active { background: rgba(16, 185, 129, 0.06); }
.fstep-num { font-family: var(--font-mono); font-size: 13px; font-weight: 700; color: var(--neutral-300); width: 24px; transition: color var(--duration-normal); }
.fstep.active .fstep-num { color: var(--accent-500); }
.fstep-text { display: flex; flex-direction: column; gap: 2px; }
.fstep-title { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-700); }
.fstep-desc { font-size: var(--text-xs); color: var(--neutral-400); }
.fstep-line { position: absolute; left: 28px; top: 100%; width: 1px; height: 8px; background: var(--neutral-200); }
.fstep:last-child .fstep-line { display: none; }

/* Form Panel */
.form-panel { position: relative; z-index: 1; flex: 1; display: flex; align-items: center; justify-content: center; padding: var(--space-6); }
.form-card { width: 100%; max-width: 440px; background: var(--surface-elevated); border: 1px solid var(--neutral-200); border-radius: var(--radius-xl); padding: var(--space-8); box-shadow: var(--shadow-lg); }
.back-link { display: inline-flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); color: var(--neutral-400); text-decoration: none; margin-bottom: var(--space-6); transition: color var(--duration-fast); }
.back-link:hover { color: var(--accent-600); }
.form-header { margin-bottom: var(--space-6); }
.form-title { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 700; color: var(--neutral-900); margin-bottom: var(--space-1); }
.form-sub { font-size: var(--text-sm); color: var(--neutral-500); }

/* Form */
.auth-form { display: flex; flex-direction: column; gap: var(--space-4); }
.field-label { display: block; font-size: var(--text-sm); font-weight: 500; color: var(--neutral-700); margin-bottom: var(--space-2); }
.field-input { position: relative; display: flex; align-items: center; }
.field-icon { position: absolute; left: 12px; color: var(--neutral-400); pointer-events: none; z-index: 1; }
.field-input input { width: 100%; padding: 12px 14px 12px 42px; border: 1.5px solid var(--neutral-200); border-radius: var(--radius-md); background: var(--surface-elevated); color: var(--neutral-800); font-family: var(--font-body); font-size: var(--text-base); outline: none; transition: all var(--duration-normal) var(--ease-out-expo); }
.field-input input::placeholder { color: var(--neutral-400); }
.field-input input:focus { border-color: var(--accent-400); box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1); }
.pwd-toggle { position: absolute; right: 10px; background: none; border: none; color: var(--neutral-400); cursor: pointer; padding: 4px; transition: color var(--duration-fast); }
.pwd-toggle:hover { color: var(--accent-600); }
.field-error { font-size: 12px; color: var(--color-error); margin-top: 4px; display: block; }

/* Password Strength */
.pwd-strength { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-2); }
.strength-bars { display: flex; gap: 3px; }
.strength-bar { width: 28px; height: 3px; border-radius: 2px; background: var(--neutral-200); transition: background var(--duration-fast); }
.strength-bar.active.weak { background: #ef4444; }
.strength-bar.active.fair { background: #f59e0b; }
.strength-bar.active.good { background: #3b82f6; }
.strength-bar.active.strong { background: var(--accent-500); }
.strength-label { font-size: 11px; font-weight: 600; }
.strength-label.weak { color: #ef4444; }
.strength-label.fair { color: #f59e0b; }
.strength-label.good { color: #3b82f6; }
.strength-label.strong { color: var(--accent-500); }

/* Checkbox */
.checkbox-label { display: flex; align-items: flex-start; gap: var(--space-2); font-size: var(--text-sm); color: var(--neutral-600); cursor: pointer; line-height: 1.5; }
.checkbox-label input { display: none; }
.cb-box { width: 16px; height: 16px; border: 1.5px solid var(--neutral-300); border-radius: 4px; transition: all var(--duration-fast); position: relative; flex-shrink: 0; margin-top: 2px; }
.checkbox-label input:checked + .cb-box { background: var(--accent-500); border-color: var(--accent-500); }
.checkbox-label input:checked + .cb-box::after { content: ''; position: absolute; left: 4px; top: 1px; width: 5px; height: 9px; border: solid white; border-width: 0 2px 2px 0; transform: rotate(45deg); }
.inline-link { color: var(--accent-600); text-decoration: none; font-weight: 500; }
.inline-link:hover { color: var(--accent-700); text-decoration: underline; }

/* Submit */
.submit-btn { width: 100%; display: flex; align-items: center; justify-content: center; gap: var(--space-2); padding: 14px; border: none; border-radius: var(--radius-md); background: var(--accent-500); color: white; font-family: var(--font-display); font-size: var(--text-base); font-weight: 600; cursor: pointer; transition: all var(--duration-normal) var(--ease-out-expo); margin-top: var(--space-2); }
.submit-btn:hover:not(:disabled) { background: var(--accent-600); box-shadow: var(--shadow-accent-lg); transform: translateY(-2px); }
.submit-btn:active { transform: scale(0.98); }
.submit-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.submit-btn svg { transition: transform 0.3s var(--ease-spring); }
.submit-btn:hover:not(:disabled) svg { transform: translateX(4px); }

.form-foot { text-align: center; margin-top: var(--space-6); font-size: var(--text-sm); color: var(--neutral-500); }
.reg-link { color: var(--accent-600); font-weight: 600; margin-left: 4px; text-decoration: none; }
.reg-link:hover { color: var(--accent-700); }

/* Responsive */
@media (max-width: 900px) {
  .auth-page { flex-direction: column; }
  .brand-panel { flex: 0 0 auto; padding: var(--space-8) var(--space-6) var(--space-4); }
  .brand-title { font-size: var(--text-2xl); }
  .feature-steps { display: none; }
  .form-panel { padding: var(--space-4) var(--space-6) var(--space-8); }
  .form-card { padding: var(--space-6); box-shadow: none; }
}

@media (prefers-reduced-motion: reduce) {
  .bg-glow { display: none; }
}
</style>
