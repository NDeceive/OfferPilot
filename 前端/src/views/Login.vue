<template>
  <div class="login-page">
    <section class="login-brand">
      <header class="brand-header">
        <img :src="brandMark" alt="智面幻境" />
        <div>
          <strong>智面幻境</strong>
          <span>AI模拟面试训练系统</span>
        </div>
      </header>

      <div class="brand-main">
        <h1>智面幻境</h1>
        <h2>AI模拟面试训练系统</h2>
        <p>沉浸式 AI 模拟面试，提升面试能力，赢得理想 Offer</p>

        <div class="portal-grid">
          <button
            v-for="portal in portals"
            :key="portal.key"
            type="button"
            class="portal-card"
            :class="portal.key"
            @click="selectedRole = portal.role"
          >
            <span class="portal-icon">
              <el-icon><component :is="portal.icon" /></el-icon>
            </span>
            <span class="portal-copy">
              <strong>{{ portal.title }}</strong>
              <small>{{ portal.desc }}</small>
            </span>
            <span class="portal-arrow">
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
        </div>
      </div>

      <img class="hero-image" :src="loginHero" alt="AI 模拟面试场景" />
    </section>

    <section class="login-panel">
      <a class="help-link" href="#" @click.prevent>
        <el-icon><QuestionFilled /></el-icon>
        帮助中心
      </a>

      <div class="auth-card">
        <div class="role-tabs" role="tablist" aria-label="登录角色">
          <button
            v-for="role in roles"
            :key="role.key"
            type="button"
            role="tab"
            :aria-selected="selectedRole === role.key"
            :class="{ active: selectedRole === role.key }"
            @click="selectedRole = role.key"
          >
            {{ role.label }}
          </button>
        </div>

        <el-form
          v-if="activeMode === 'login'"
          ref="loginRef"
          :model="loginForm"
          :rules="rules"
          class="auth-form"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              :prefix-icon="User"
              placeholder="账号/学号"
              class="auth-input"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              :type="showPassword ? 'text' : 'password'"
              :prefix-icon="Lock"
              placeholder="密码"
              class="auth-input"
            >
              <template #suffix>
                <button class="password-toggle" type="button" @click="showPassword = !showPassword">
                  <el-icon><component :is="showPassword ? View : Hide" /></el-icon>
                </button>
              </template>
            </el-input>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <a href="#" @click.prevent>忘记密码？</a>
          </div>

          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            size="large"
            @click="handleLogin"
          >
            登录系统
          </el-button>
        </el-form>

        <el-form
          v-else
          ref="registerRef"
          :model="registerForm"
          :rules="regRules"
          class="auth-form"
          size="large"
        >
          <el-form-item prop="username">
            <el-input
              v-model="registerForm.username"
              :prefix-icon="User"
              placeholder="用户名（3-20 位）"
              class="auth-input"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              :prefix-icon="Lock"
              placeholder="密码（6-20 位）"
              show-password
              class="auth-input"
            />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input
              v-model="registerForm.nickname"
              :prefix-icon="Avatar"
              placeholder="昵称（选填）"
              class="auth-input"
            />
          </el-form-item>

          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            size="large"
            @click="handleRegister"
          >
            创建账号
          </el-button>
        </el-form>

        <p class="register-line">
          {{ activeMode === 'login' ? '没有账号？' : '已有账号？' }}
          <button type="button" @click="activeMode = activeMode === 'login' ? 'register' : 'login'">
            {{ activeMode === 'login' ? '立即注册' : '返回登录' }}
          </button>
        </p>
      </div>

      <footer class="login-footer">
        <div class="trust-row">
          <span><el-icon><CircleCheck /></el-icon>安全保障</span>
          <span><el-icon><Lock /></el-icon>数据加密</span>
          <span><el-icon><CircleCheck /></el-icon>隐私保护</span>
        </div>
        <p>© 2024 智面幻境 AI 模拟面试训练系统. 保留所有权利。</p>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  Avatar,
  Briefcase,
  CircleCheck,
  Hide,
  Lock,
  QuestionFilled,
  School,
  User,
  View
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import brandMark from '@/assets/generated/brand-mark-ui.png'
import loginHero from '@/assets/generated/login-hero-ui.jpg'

const router = useRouter()
const userStore = useUserStore()

const activeMode = ref('login')
const selectedRole = ref('STUDENT')
const loading = ref(false)
const rememberMe = ref(true)
const showPassword = ref(false)
const loginRef = ref()
const registerRef = ref()

const roles = [
  { key: 'STUDENT', label: '学生登录' },
  { key: 'TEACHER', label: '教师登录' },
  { key: 'ADMIN', label: '管理端' }
]

const portals = [
  { key: 'student', role: 'STUDENT', title: '学生端', desc: '提升面试能力', icon: School },
  { key: 'teacher', role: 'TEACHER', title: '教师端', desc: '高效教学管理', icon: Briefcase }
]

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入账号/学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  await loginRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(loginForm)
      ElMessage.success('登录成功')
      router.push('/home')
    } catch (e) {
      // 错误由请求拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}

const handleRegister = async () => {
  await registerRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.register({ ...registerForm, role: selectedRole.value })
      ElMessage.success('注册成功，请登录')
      activeMode.value = 'login'
      loginForm.username = registerForm.username
    } catch (e) {
      // 错误由请求拦截器统一处理
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(440px, 0.72fr);
  min-width: 320px;
  min-height: 100dvh;
  overflow: hidden;
  color: #0b1e39;
  background: #f6f9ff;
}

.login-brand {
  position: relative;
  min-height: 100dvh;
  padding: 30px 54px 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 98% 24%, rgba(117, 139, 255, 0.18), transparent 20rem),
    radial-gradient(circle at 3% 82%, rgba(30, 112, 255, 0.16), transparent 22rem),
    linear-gradient(135deg, #f8fbff 0%, #eef5ff 100%);
}

.login-brand::before,
.login-brand::after {
  position: absolute;
  content: '';
  pointer-events: none;
  border: 6px solid rgba(78, 124, 255, 0.12);
  border-radius: 50%;
}

.login-brand::before {
  width: 520px;
  height: 520px;
  right: -240px;
  top: 0;
}

.login-brand::after {
  width: 560px;
  height: 560px;
  left: -380px;
  bottom: 20px;
}

.brand-header {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-header img {
  width: 58px;
  height: 58px;
  object-fit: cover;
  border-radius: 16px;
  mix-blend-mode: multiply;
}

.brand-header strong,
.brand-header span {
  display: block;
}

.brand-header strong {
  font-size: 22px;
  font-weight: 900;
  line-height: 1.1;
}

.brand-header span {
  margin-top: 5px;
  color: #34435f;
  font-size: 14px;
}

.brand-main {
  position: relative;
  z-index: 2;
  width: min(620px, 100%);
  margin: 64px auto 0;
}

.brand-main h1 {
  color: #2547f4;
  font-size: clamp(44px, 4vw, 68px);
  font-weight: 950;
  line-height: 0.98;
  text-shadow: 0 10px 24px rgba(37, 83, 255, 0.12);
}

.brand-main h2 {
  margin-top: 16px;
  color: #162844;
  font-size: clamp(30px, 2.8vw, 44px);
  font-weight: 950;
  line-height: 1.08;
}

.brand-main p {
  margin-top: 18px;
  color: #40506d;
  font-size: clamp(15px, 1.1vw, 18px);
  font-weight: 500;
  letter-spacing: 0;
}

.portal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 26px;
  margin-top: 34px;
}

.portal-card {
  display: grid;
  grid-template-columns: 58px 1fr;
  align-items: center;
  min-height: 132px;
  padding: 24px;
  text-align: left;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(119, 143, 255, 0.28);
  border-radius: 22px;
  box-shadow: 0 18px 40px rgba(64, 104, 180, 0.07);
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.portal-card:hover {
  border-color: rgba(35, 102, 255, 0.42);
  box-shadow: 0 22px 48px rgba(35, 102, 255, 0.12);
  transform: translateY(-2px);
}

.portal-card.teacher {
  border-color: rgba(32, 191, 136, 0.28);
  background: rgba(244, 255, 250, 0.5);
}

.portal-icon {
  display: grid;
  width: 50px;
  height: 50px;
  color: #fff;
  place-items: center;
  background: linear-gradient(135deg, #5757ff, #271cff);
  border-radius: 16px;
  font-size: 30px;
  box-shadow: 0 18px 34px rgba(56, 76, 255, 0.2);
}

.portal-card.teacher .portal-icon {
  background: linear-gradient(135deg, #62dfa7, #09ba6b);
  box-shadow: 0 18px 34px rgba(9, 186, 107, 0.18);
}

.portal-copy strong,
.portal-copy small {
  display: block;
}

.portal-copy strong {
  color: #1c38ff;
  font-size: 22px;
  font-weight: 900;
}

.portal-card.teacher .portal-copy strong {
  color: #07b76d;
}

.portal-copy small {
  margin-top: 10px;
  color: #253653;
  font-size: 15px;
  font-weight: 700;
}

.portal-arrow {
  display: grid;
  grid-column: 2;
  width: 34px;
  height: 34px;
  margin-top: 14px;
  color: #6058ff;
  place-items: center;
  border: 2px solid rgba(96, 88, 255, 0.36);
  border-radius: 50%;
  font-size: 18px;
}

.portal-card.teacher .portal-arrow {
  color: #09ba6b;
  border-color: rgba(9, 186, 107, 0.34);
}

.hero-image {
  position: absolute;
  right: -6%;
  bottom: 0;
  z-index: 1;
  width: min(88%, 960px);
  max-height: 47%;
  object-fit: cover;
  object-position: center bottom;
  border-top-left-radius: 34px;
  filter: saturate(1.03);
  mask-image: linear-gradient(90deg, transparent 0%, #000 9%, #000 100%);
}

.login-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100dvh;
  padding: 76px 68px 36px;
  background:
    radial-gradient(circle at 14% 4%, rgba(68, 136, 255, 0.1), transparent 22rem),
    linear-gradient(180deg, #f8fbff 0%, #f1f6ff 100%);
}

.help-link {
  position: absolute;
  top: 36px;
  right: 46px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #31415d;
  font-size: 15px;
  font-weight: 600;
}

.auth-card {
  width: min(100%, 560px);
  margin: 0 auto;
  padding: 40px 38px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 233, 246, 0.9);
  border-radius: 18px;
  box-shadow: 0 28px 70px rgba(53, 83, 139, 0.12);
}

.role-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid #dce5f2;
}

.role-tabs button {
  position: relative;
  height: 48px;
  color: #223553;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 16px;
  font-weight: 800;
}

.role-tabs button.active {
  color: #1769ff;
}

.role-tabs button.active::after {
  position: absolute;
  right: 25%;
  bottom: -2px;
  left: 25%;
  height: 5px;
  content: '';
  background: #1769ff;
  border-radius: 999px;
}

.auth-form {
  margin-top: 34px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.auth-input :deep(.el-input__wrapper) {
  min-height: 58px;
  padding-inline: 18px;
  border-radius: 14px;
  box-shadow: 0 0 0 1px #cfd9e8 inset;
}

.auth-input :deep(.el-input__inner) {
  color: #142540;
  font-size: 16px;
}

.auth-input :deep(.el-input__prefix) {
  color: #8290aa;
  font-size: 21px;
}

.password-toggle {
  display: grid;
  color: #8b98af;
  background: transparent;
  border: 0;
  cursor: pointer;
  place-items: center;
  font-size: 22px;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 24px;
  color: #31415d;
  font-size: 15px;
}

.form-options a {
  color: #31415d;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  height: 64px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(180deg, #2d80ff 0%, #0666f8 100%);
  box-shadow: 0 18px 30px rgba(11, 104, 248, 0.28);
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 0.04em;
}

.register-line {
  margin-top: 24px;
  color: #52617a;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.register-line button {
  color: #0666f8;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: inherit;
  font-weight: 900;
}

.login-footer {
  width: min(100%, 560px);
  margin: 46px auto 0;
  color: #697895;
  text-align: center;
}

.trust-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-bottom: 22px;
}

.trust-row span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #4d5d79;
  font-size: 14px;
  font-weight: 600;
}

.trust-row .el-icon {
  font-size: 24px;
}

.login-footer p {
  font-size: 14px;
}

@media (max-width: 1040px) {
  .login-page {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .login-brand {
    min-height: 560px;
    padding: 28px 44px 0;
  }

  .login-panel {
    min-height: auto;
    padding: 56px 32px 34px;
  }
}

@media (max-width: 900px) {
  .login-brand {
    display: none;
  }

  .login-panel {
    justify-content: flex-start;
    min-height: 100dvh;
    padding: 22px 14px 28px;
  }

  .help-link {
    position: static;
    justify-content: flex-end;
    margin-bottom: 14px;
    font-size: 14px;
  }

  .auth-card {
    padding: 22px 16px;
    border-radius: 12px;
  }

  .auth-form {
    margin-top: 24px;
  }

  .trust-row {
    gap: 10px;
    margin-bottom: 14px;
  }

  .role-tabs button {
    height: 44px;
    font-size: 14px;
  }

  .auth-input :deep(.el-input__wrapper),
  .submit-btn {
    min-height: auto;
    height: 50px;
  }

  .login-footer {
    margin-top: 24px;
  }

  .form-options,
  .register-line,
  .login-footer p,
  .trust-row span {
    font-size: 14px;
  }
}

@media (max-width: 560px) {
  .trust-row {
    grid-template-columns: 1fr;
  }

  .form-options {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
}
</style>
