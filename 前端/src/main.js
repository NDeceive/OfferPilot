import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { DEMO_TOKEN } from './utils/demoAdapter'
import { initTheme } from './composables/useTheme'
import './assets/styles/main.css'
import './assets/styles/select.css'
import './assets/styles/controls.css'
import './assets/styles/dark-overrides.css'

/*
 * 冷启动清掉上次离线演示留下的假登录态。
 *
 * 离线演示的登录是假的（token 固定为 DEMO_TOKEN），而且刻意不跨重启保留：
 * 杀掉 App 重进 = 一次干净的重新选择——回登录页，登录成功即在线，连不上时
 * 在登录页选「离线演示」再进离线。不清这个假 token 的话，路由守卫会误以为
 * 「还登录着」直接放进主界面，再拿假 token 请求真后端一路 401。
 */
try {
  if (localStorage.getItem('token') === DEMO_TOKEN) {
    ['token', 'userId', 'username', 'nickname', 'role'].forEach((key) => localStorage.removeItem(key))
  }
} catch {
  /* 存储被禁用：登录态本来也存不下来，无需清理 */
}

// 先把主题落到 <html>，再挂载，避免深色用户看到一帧白屏
initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
