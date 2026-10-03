<template>
  <AppLayout>
    <main class="account-settings"><router-link :to="teacher?'/teacher/account':'/profile'">← {{teacher?'返回教师个人中心':'返回我的求职档案'}}</router-link><h1>账号设置</h1><p class="intro">管理登录信息与账号安全。</p>
      <section><h2>账号信息</h2><p v-if="loadError" class="error" role="alert">{{ loadError }}</p><dl><div><dt>用户名</dt><dd>{{ me?.username || '—' }}</dd></div><div><dt>昵称</dt><dd>{{ me?.nickname || '—' }}</dd></div><div><dt>邮箱</dt><dd>{{ me?.email || '未绑定' }}</dd></div><div><dt>手机</dt><dd>{{ maskedPhone }}</dd></div></dl><router-link :to="teacher?'/teacher/account':'/profile'">修改个人资料 →</router-link></section>
      <section><h2>修改密码</h2><form @submit.prevent="save"><label for="current-password">当前密码</label><input id="current-password" v-model="currentPassword" type="password" autocomplete="current-password" required /><label for="new-password">新密码</label><input id="new-password" v-model="newPassword" type="password" autocomplete="new-password" minlength="6" maxlength="20" required /><p class="hint">6–20 位，建议结合字母、数字与符号。</p><label for="confirm-password">确认新密码</label><input id="confirm-password" v-model="confirmPassword" type="password" autocomplete="new-password" required /><p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="message" class="success" role="status">{{ message }}</p><button class="primary" :disabled="saving">{{ saving ? '正在更新…' : '更新密码' }}</button></form></section>
      <button class="logout" type="button" @click="logout">退出登录</button>
    </main>
  </AppLayout>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMe, updateProfile } from '../api'
import { useUserStore } from '../store/user'
import AppLayout from '../components/layout/AppLayout.vue'
const me = ref(null), loadError = ref(''), currentPassword = ref(''), newPassword = ref(''), confirmPassword = ref(''), saving = ref(false), error = ref(''), message = ref('')
const route=useRoute(),teacher=computed(()=>route.path.startsWith('/teacher'));
const store = useUserStore(), router = useRouter()
const maskedPhone = computed(() => me.value?.phone ? me.value.phone.replace(/^(\d{3})\d+(\d{4})$/, '$1****$2') : '未绑定')
onMounted(async () => { try { me.value = await getMe() } catch { loadError.value = '账号信息暂时无法读取，请刷新重试。' } })
async function save() {
  error.value = ''; message.value = ''
  if (newPassword.value !== confirmPassword.value) { error.value = '两次输入的新密码不一致。'; return }
  saving.value = true
  try { await updateProfile({ currentPassword: currentPassword.value, newPassword: newPassword.value }); currentPassword.value = ''; newPassword.value = ''; confirmPassword.value = ''; message.value = '密码已更新，下次登录请使用新密码。' }
  catch (e) { error.value = e.message || '更新失败，请重试。' } finally { saving.value = false }
}
function logout() { store.logout(); router.push('/login') }
</script>
<style scoped>
.account-settings{max-width:760px;margin:0 auto;padding:32px 24px 60px;color:var(--neutral-900)}a{display:inline-flex;align-items:center;min-height:44px;color:var(--accent-700);font-size:14px;text-decoration:none}h1{margin-top:12px;font-size:28px}.intro{margin:8px 0 24px;color:var(--neutral-600);font-size:14px}section{padding:26px;margin:18px 0;border:1px solid var(--neutral-200);border-radius:16px;background:white}h2{font-size:19px;margin-bottom:16px}dl>div{display:flex;gap:20px;padding:12px 0;font-size:14px}dt{width:75px;color:var(--neutral-600)}dd{margin:0;overflow-wrap:anywhere}label{display:block;margin:16px 0 8px;font-size:14px}input{width:100%;min-height:44px;padding:10px 12px;border:1px solid var(--neutral-300);border-radius:8px;background:white;font:inherit}.hint{font-size:12px;color:var(--neutral-600);margin-top:6px}button{min-height:44px;padding:10px 20px;border-radius:8px;font:inherit;font-size:14px;cursor:pointer}.primary{margin-top:20px;border:0;background:var(--accent-500);color:white}.primary:hover{background:var(--accent-600)}.primary:disabled{opacity:.5;cursor:wait}.logout{background:white;border:1px solid var(--neutral-300);color:var(--neutral-700)}.error,.success{margin-top:12px;font-size:14px;line-height:1.6}.error{color:#b42318}.success{color:var(--accent-700)}:is(button,a,input):focus-visible{outline:2px solid var(--accent-700);outline-offset:3px}@media(max-width:600px){.account-settings{padding:20px 16px}section{padding:20px}}
</style>
