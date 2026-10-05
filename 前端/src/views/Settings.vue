<template>
  <AppLayout>
    <div class="settings-layout">
      <!-- Left Nav -->
      <div class="settings-nav" ref="navEl">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="['nav-item', { active: activeTab === tab.id }]"
          @click="switchTab(tab.id)"
        >
          <span class="nav-icon" v-html="tab.icon"></span>
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <!-- Right Content -->
      <div class="settings-content">
        <p v-if="flashText" class="flash-text" :class="{ 'is-error': flashIsError }" role="status">
          {{ flashText }}
        </p>

        <!-- General -->
        <div v-if="activeTab === 'general'" class="card">
          <h2 class="card-title">通用设置</h2>

          <p v-if="meError" class="inline-alert" role="alert">
            {{ meError }}，下面显示的是本机缓存的信息。
          </p>

          <div class="setting-group">
            <label class="setting-label">头像</label>
            <div class="avatar-row">
              <div class="avatar-preview"><span>{{ avatarLetter }}</span></div>
              <div class="avatar-side">
                <button class="btn-outline" disabled>更换头像</button>
                <span class="setting-note">头像上传接口尚未开通，先按昵称首字显示。</span>
              </div>
            </div>
          </div>

          <div class="setting-row">
            <div class="setting-group">
              <label class="setting-label" for="settings-nickname">昵称</label>
              <input
                id="settings-nickname"
                v-model="profileForm.nickname"
                type="text"
                class="setting-input"
                maxlength="20"
                placeholder="怎么称呼你"
                :disabled="meLoading"
              />
            </div>
            <div class="setting-group">
              <label class="setting-label">登录账号</label>
              <input type="text" class="setting-input" :value="me?.username || store.username || '—'" disabled />
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">通知偏好</label>
            <div class="toggle-list">
              <div class="toggle-item" v-for="t in toggles" :key="t.key">
                <div>
                  <span class="toggle-name">{{ t.name }}</span>
                  <span class="toggle-desc">{{ t.desc }}</span>
                </div>
                <button
                  :class="['toggle-switch', { on: t.value }]"
                  role="switch"
                  :aria-checked="t.value"
                  :aria-label="t.name"
                  @click="t.value = !t.value"
                >
                  <span class="toggle-knob"></span>
                </button>
              </div>
            </div>
            <p class="setting-note">偏好保存在本机浏览器，推送通道接入后按此设置生效。</p>
          </div>

          <button class="btn-primary" :disabled="saving || meLoading" @click="saveSettings">
            {{ saving ? '保存中…' : '保存更改' }}
          </button>
        </div>

        <!-- Digital Human -->
        <div v-if="activeTab === 'digital'" class="card">
          <h2 class="card-title">数字人面试官</h2>

          <div class="setting-group">
            <label class="setting-label">面试官形象</label>
            <div class="digital-preview">
              <video
                class="digital-preview__video"
                src="/assets/interview-avatar/interviewer-v3.mp4"
                poster="/assets/interview-avatar/closed-door-j0.png"
                autoplay
                loop
                muted
                playsinline
              ></video>
              <div class="digital-preview__meta">
                <strong>AI 数字人面试官</strong>
                <span>开场、念题与追问都由数字人播报，面试中实时驱动。</span>
              </div>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">语音偏好</label>
            <div class="toggle-item is-disabled">
              <div>
                <span class="toggle-name">音色与语速</span>
                <span class="toggle-desc">由数字人服务侧统一配置，暂未开放用户自定义</span>
              </div>
              <span class="soon-tag">即将开放</span>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">连接方式</label>
            <div class="toggle-item">
              <div>
                <span class="toggle-name">独立数字人服务</span>
                <span class="toggle-desc">服务未就绪时自动降级为静态面试官，面试流程照常进行</span>
              </div>
              <span class="soon-tag is-on">已启用</span>
            </div>
          </div>
        </div>

        <!-- Account -->
        <div v-if="activeTab === 'account'" class="card">
          <h2 class="card-title">账号安全</h2>

          <div class="setting-group">
            <label class="setting-label">修改密码</label>
            <div class="password-fields">
              <input
                v-model="pwdForm.next"
                type="password"
                class="setting-input"
                autocomplete="new-password"
                placeholder="新密码（6-20 位）"
              />
              <input
                v-model="pwdForm.confirm"
                type="password"
                class="setting-input"
                autocomplete="new-password"
                placeholder="确认新密码"
              />
            </div>
            <button class="btn-outline" style="margin-top: var(--space-3)" :disabled="pwdSaving" @click="changePassword">
              {{ pwdSaving ? '提交中…' : '更新密码' }}
            </button>
          </div>

          <div class="setting-group">
            <label class="setting-label">绑定手机</label>
            <div class="bind-row">
              <span class="bind-value" :class="{ 'is-empty': !boundPhone }">{{ boundPhone || '未绑定' }}</span>
              <span class="soon-tag">即将开放</span>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">绑定邮箱</label>
            <div class="bind-row">
              <span class="bind-value" :class="{ 'is-empty': !boundEmail }">{{ boundEmail || '未绑定' }}</span>
              <span class="soon-tag">即将开放</span>
            </div>
          </div>

          <div class="danger-zone">
            <h3 class="danger-title">危险操作</h3>
            <div class="danger-item">
              <div>
                <span class="danger-name">注销账号</span>
                <span class="danger-desc">自助注销尚未开放，需要删除账号请联系管理员</span>
              </div>
              <button class="btn-danger" disabled>注销账号</button>
            </div>
          </div>
        </div>

        <!-- Appearance -->
        <div v-if="activeTab === 'appearance'" class="card">
          <h2 class="card-title">外观设置</h2>

          <div class="setting-group">
            <label class="setting-label">主题</label>
            <p class="setting-desc">深色主题尚在开发中，当前版本为浅色界面。</p>
            <div class="theme-options">
              <button class="theme-btn active" disabled>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
                </svg>
                浅色
              </button>
              <button class="theme-btn" disabled>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
                </svg>
                深色
              </button>
              <button class="theme-btn" disabled>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>
                </svg>
                跟随系统
              </button>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">语言</label>
            <select class="setting-select" disabled>
              <option>简体中文</option>
            </select>
            <p class="setting-note">当前版本仅提供简体中文。</p>
          </div>
        </div>

        <!-- Data -->
        <div v-if="activeTab === 'data'" class="card">
          <h2 class="card-title">数据管理</h2>

          <div class="setting-group">
            <label class="setting-label">数据导出</label>
            <p class="setting-desc">把面试记录导出成本地文件，可直接用表格工具打开。</p>
            <div class="export-row">
              <button class="btn-outline" :disabled="exporting" @click="exportData('json')">
                {{ exporting ? '导出中…' : '导出 JSON' }}
              </button>
              <button class="btn-outline" :disabled="exporting" @click="exportData('csv')">
                {{ exporting ? '导出中…' : '导出 CSV' }}
              </button>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">训练记录</label>
            <div class="bind-row">
              <span class="bind-value">
                共 {{ recordCount === null ? '—' : recordCount }} 条
                <template v-if="finishedCount !== null">，已完成 {{ finishedCount }} 条</template>
              </span>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">清除本地缓存</label>
            <p class="setting-desc">清除本机保存的通知偏好与专项刷题进度，不影响登录状态和个人资料。</p>
            <button class="btn-outline btn-warning" :disabled="clearing" @click="clearCache">清除缓存</button>
          </div>
        </div>

        <!-- Help -->
        <div v-if="activeTab === 'help'" class="card">
          <h2 class="card-title">帮助与反馈</h2>

          <div class="setting-group">
            <label class="setting-label">常见问题</label>
            <div class="faq-list">
              <div v-for="(item, index) in faqs" :key="item.q" class="faq-item">
                <button class="faq-q" :aria-expanded="openFaq === index" @click="toggleFaq(index)">
                  <span>{{ item.q }}</span>
                  <b aria-hidden="true">{{ openFaq === index ? '−' : '+' }}</b>
                </button>
                <p v-if="openFaq === index" class="faq-a">{{ item.a }}</p>
              </div>
            </div>
          </div>

          <div class="setting-group">
            <label class="setting-label">问题反馈</label>
            <p class="setting-desc">在线反馈通道尚未接入，遇到问题可先在「常见问题」里查找，或联系管理员。</p>
            <div class="toggle-item is-disabled">
              <div>
                <span class="toggle-name">提交问题与建议</span>
                <span class="toggle-desc">支持附当前页面截图，接入后可直接提交</span>
              </div>
              <span class="soon-tag">即将开放</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import { getInterviewRecords, getMe, updateProfile } from '../api'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

/* ==================== 分栏 ==================== */
// 六个 tab。手机端「我的」里五个入口各自深链到对应分栏（?tab=…），
// 不这么做的话点「帮助与反馈」和点「通用设置」落在同一屏，入口名就全是假的。
const TAB_IDS = ['general', 'digital', 'account', 'appearance', 'data', 'help']

const tabs = [
  { id: 'general', label: '通用设置', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68V3a2 2 0 0 1 4 0v.09A1.65 1.65 0 0 0 14 4.6a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>' },
  { id: 'digital', label: '数字人', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="4" width="18" height="14" rx="2"/><circle cx="12" cy="10" r="2.5"/><path d="M7.5 18a4.5 4.5 0 0 1 9 0"/><path d="M8 21h8"/></svg>' },
  { id: 'account', label: '账号安全', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>' },
  { id: 'appearance', label: '外观', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="5"/><path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/></svg>' },
  { id: 'data', label: '数据管理', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>' },
  { id: 'help', label: '帮助与反馈', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="9"/><path d="M9.2 9a2.8 2.8 0 0 1 5.5.8c0 1.9-2.7 2.2-2.7 4"/><circle cx="12" cy="17.5" r=".6" fill="currentColor"/></svg>' },
]

const activeTab = ref(
  typeof route.query.tab === 'string' && TAB_IDS.includes(route.query.tab) ? route.query.tab : 'general'
)

// 浏览器的前进/后退也要跟着换栏，否则地址栏是 ?tab=help、内容是通用设置
watch(
  () => route.query.tab,
  (tab) => {
    if (typeof tab === 'string' && TAB_IDS.includes(tab) && tab !== activeTab.value) {
      activeTab.value = tab
    }
  }
)

function switchTab(id) {
  if (activeTab.value === id) return
  activeTab.value = id
  router.replace({ query: { ...route.query, tab: id } })
}

// 手机上六个标签是一行横滑的，从「我的」深链进「帮助与反馈」时
// 导航条停在最左边，高亮项在屏幕外——人看不到自己在哪一栏。
// 直接改 scrollLeft 而不是 scrollIntoView：后者会连带滚动祖先元素。
const navEl = ref(null)

async function centerActiveTab() {
  await nextTick()
  const nav = navEl.value
  if (!nav || nav.scrollWidth <= nav.clientWidth) return // 桌面是竖排列表，不需要动
  const btn = nav.children[TAB_IDS.indexOf(activeTab.value)]
  if (!btn) return
  nav.scrollLeft = btn.offsetLeft - (nav.clientWidth - btn.offsetWidth) / 2
}

/* ==================== 提示条 ==================== */
// 全站点没有 toast 组件，这里用一个自消失的行内提示，不引入新依赖。
const flashText = ref('')
const flashIsError = ref(false)
let flashTimer = null

function flash(message, isError = false) {
  flashText.value = message
  flashIsError.value = isError
  window.clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => { flashText.value = '' }, 3200)
}

/* ==================== 个人资料（真数据） ==================== */
const me = ref(null)
const meLoading = ref(true)
const meError = ref('')
const profileForm = reactive({ nickname: '' })

const displayName = computed(
  () => me.value?.nickname || store.nickname || me.value?.username || store.username || '未登录'
)
// 后端 SysUser.avatar 目前恒为空，没有上传接口，所以头像位显示昵称首字。
const avatarLetter = computed(() => (displayName.value.trim().charAt(0) || '用').toUpperCase())

async function loadMe() {
  meLoading.value = true
  meError.value = ''
  try {
    me.value = await getMe()
    profileForm.nickname = me.value?.nickname || ''
  } catch (e) {
    meError.value = e.message || '个人资料加载失败'
  } finally {
    meLoading.value = false
  }
}

/* ---------- 通知偏好：存在本机 ---------- */
const PREFS_KEY = 'offerpilot.settings.prefs'
const toggles = reactive([
  { key: 'reminder', name: '面试提醒', desc: '在预约面试前 30 分钟提醒你', value: true },
  { key: 'report', name: '每周报告', desc: '每周发送练习总结和能力趋势', value: true },
  { key: 'features', name: '新功能通知', desc: '产品更新和新功能上线通知', value: false },
])

function loadPrefs() {
  let saved = {}
  try {
    saved = JSON.parse(localStorage.getItem(PREFS_KEY) || '{}')
  } catch {
    saved = {} // 存坏了就当没存过，不要因为一条偏好让整页打不开
  }
  toggles.forEach((t) => {
    if (typeof saved[t.key] === 'boolean') t.value = saved[t.key]
  })
}

function persistPrefs() {
  const data = {}
  toggles.forEach((t) => { data[t.key] = t.value })
  localStorage.setItem(PREFS_KEY, JSON.stringify(data))
}

const saving = ref(false)

async function saveSettings() {
  const nickname = profileForm.nickname.trim()
  if (!nickname) {
    flash('昵称不能为空', true)
    return
  }
  persistPrefs() // 偏好只有本机一份，无论昵称改没改都要落盘

  if (nickname === (me.value?.nickname || '')) {
    flash('已保存本机偏好')
    return
  }

  saving.value = true
  try {
    // updateProfile 只接受 nickname / newPassword，改动立刻写库
    const updated = await updateProfile({ nickname })
    me.value = updated || { ...me.value, nickname }
    // 顶栏问候、首页都用 store 里的昵称，这里同步过去，不然要刷新才变
    store.nickname = nickname
    localStorage.setItem('nickname', nickname)
    flash('昵称已保存')
  } catch (e) {
    flash(e.message || '保存失败，请重试', true)
  } finally {
    saving.value = false
  }
}

/* ==================== 账号安全 ==================== */
const boundPhone = computed(() => me.value?.phone || '')
const boundEmail = computed(() => me.value?.email || '')

const pwdForm = reactive({ next: '', confirm: '' })
const pwdSaving = ref(false)

async function changePassword() {
  // 后端只接收新密码（ProfileUpdateRequest 里没有当前密码字段），
  // 所以这里不摆一个填了也不起作用的「当前密码」输入框。
  if (pwdForm.next.length < 6 || pwdForm.next.length > 20) {
    flash('密码长度需为 6-20 位', true)
    return
  }
  if (pwdForm.next !== pwdForm.confirm) {
    flash('两次输入的新密码不一致', true)
    return
  }
  pwdSaving.value = true
  try {
    await updateProfile({ newPassword: pwdForm.next })
    pwdForm.next = ''
    pwdForm.confirm = ''
    flash('密码已更新')
  } catch (e) {
    flash(e.message || '密码更新失败，请重试', true)
  } finally {
    pwdSaving.value = false
  }
}

/* ==================== 数据管理 ==================== */
const records = ref([])
const recordsLoaded = ref(false)
const exporting = ref(false)
const clearing = ref(false)

async function ensureRecords() {
  if (recordsLoaded.value) return records.value
  records.value = (await getInterviewRecords()) || []
  recordsLoaded.value = true
  return records.value
}

watch(activeTab, (tab) => {
  centerActiveTab()
  // 记录只在真正打开「数据管理」时才拉，别让设置页平白多一次全量请求
  if (tab === 'data' && !recordsLoaded.value) ensureRecords().catch(() => {})
})

function downloadBlob(filename, content, mime) {
  const url = URL.createObjectURL(new Blob([content], { type: mime }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const EXPORT_COLUMNS = ['id', 'jobName', 'status', 'totalScore', 'startTime', 'durationSeconds', 'actualDurationSeconds']

async function exportData(format) {
  exporting.value = true
  try {
    const list = await ensureRecords()
    if (!list.length) {
      flash('还没有可导出的面试记录')
      return
    }
    const stamp = new Date().toISOString().slice(0, 10)
    if (format === 'csv') {
      const cell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`
      const lines = [EXPORT_COLUMNS.join(','), ...list.map((r) => EXPORT_COLUMNS.map((k) => cell(r[k])).join(','))]
      // 前置 BOM，否则 Excel 打开中文列名是乱码
      downloadBlob(`offerpilot-records-${stamp}.csv`, '﻿' + lines.join('\r\n'), 'text/csv;charset=utf-8')
    } else {
      const payload = { exportedAt: new Date().toISOString(), total: list.length, records: list }
      downloadBlob(`offerpilot-records-${stamp}.json`, JSON.stringify(payload, null, 2), 'application/json;charset=utf-8')
    }
    flash(`已导出 ${list.length} 条记录`)
  } catch (e) {
    flash(e.message || '导出失败，请重试', true)
  } finally {
    exporting.value = false
  }
}

// 只清这两样，正是下面说明文字里承诺的「通知偏好 + 刷题进度」。
// 用白名单而不是「除账号键外全清」是有意的：'offerpilot.apiBase' 是 APK 连局域网
// 后端的运行时地址、'offerpilot.offlineDemo' 是用户手动开的演示开关——被顺手清掉
// 的话现场得重新填服务器地址，属于帮倒忙。token 同理，清了等于把人踢下线。
const CLEARABLE_KEYS = [PREFS_KEY, 'offerpilot.learning.session'] // 后者见 services/learningResources.js

function clearCache() {
  clearing.value = true
  let removed = 0
  CLEARABLE_KEYS.forEach((key) => {
    if (localStorage.getItem(key) !== null) {
      localStorage.removeItem(key)
      removed += 1
    }
  })
  clearing.value = false
  flash(removed ? `已清除 ${removed} 项本地缓存` : '没有需要清除的本地缓存')
}

const recordCount = computed(() => (recordsLoaded.value ? records.value.length : null))
const finishedCount = computed(() =>
  recordsLoaded.value ? records.value.filter((r) => r.status === 'FINISHED').length : null
)

/* ==================== 帮助与反馈 ==================== */
// 内容按当前实现的真实行为写，不写「即将支持」当承诺。
const faqs = [
  {
    q: '面试记录里为什么有那么多「进行中」？',
    a: '每次点「开始面试」都会立刻建一条记录。中途关掉页面或直接退出，这场面试不会被自动结束，就一直留在「进行中」。记录页可以切到「进行中」逐条查看或删除。',
  },
  {
    q: '报告什么时候生成？',
    a: '面试结束提交后由后台异步生成。生成期间记录会显示为处理中，完成后即可在记录页点进报告详情；若生成失败，系统会自动重试。',
  },
  {
    q: '简历支持哪些格式？',
    a: '支持 PDF、DOC、DOCX 三种，上传后自动解析出技能标签，用于岗位匹配与出题。也可以不传文件，直接在「在线简历」里填写。',
  },
  {
    q: '专项刷题是怎么判题的？',
    a: '当前版本用本机规则快速校验解题思路，结果仅供参考，不代表真实评测机的完整用例判定。题目与训练进度保存在本机浏览器，清除浏览器数据会一并丢失。',
  },
  {
    q: '数字人面试官连不上怎么办？',
    a: '数字人服务独立部署，未启动时面试会自动降级为静态面试官形象，出题、作答、评分等环节不受影响，可以正常走完整场面试。',
  },
  {
    q: '我的数据存在哪里？',
    a: '账号、简历、面试记录与报告都存在服务端数据库，换设备登录同一账号即可看到。通知偏好、专项刷题进度这类设置只保存在当前设备。',
  },
]

const openFaq = ref(-1)

function toggleFaq(index) {
  openFaq.value = openFaq.value === index ? -1 : index
}

onMounted(() => {
  loadPrefs()
  loadMe()
  centerActiveTab()
})
</script>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-6);
  max-width: 900px;
  margin: 0 auto;
  padding: var(--space-8) 0 var(--space-16);
}

.settings-nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--neutral-600);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast);
  text-align: left;
  position: relative;
}

.nav-item:hover {
  background: var(--neutral-100);
  color: var(--neutral-900);
}

.nav-item.active {
  background: var(--accent-50);
  color: var(--accent-700);
  font-weight: 500;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: var(--accent-500);
  border-radius: var(--radius-full);
}

.nav-icon {
  display: flex;
  align-items: center;
}

.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-8);
  animation: fade-in-up 0.3s var(--ease-out-expo);
}

.card-title {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-6);
}

.setting-group {
  margin-bottom: var(--space-6);
}

.setting-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-700);
  margin-bottom: var(--space-2);
}

.setting-desc {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-3);
}

.setting-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.setting-input {
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-900);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  outline: none;
  transition: border-color var(--duration-normal), box-shadow var(--duration-normal);
}

.setting-input:focus {
  border-color: var(--accent-400);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.setting-input::placeholder {
  color: var(--neutral-400);
}

.setting-select {
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-900);
  font-size: var(--text-sm);
  outline: none;
  transition: border-color var(--duration-normal);
}

.setting-select:focus {
  border-color: var(--accent-400);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

/* Avatar */
.avatar-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.avatar-preview {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--accent-500), var(--accent-600));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xl);
  font-weight: 700;
  color: white;
}

/* Password */
.password-fields {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* Bind */
.bind-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--surface-primary);
  border-radius: var(--radius-md);
}

.bind-value {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--neutral-600);
}

/* Toggle */
.toggle-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--surface-primary);
  border-radius: var(--radius-md);
}

.toggle-name {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-800);
}

.toggle-desc {
  font-size: var(--text-xs);
  color: var(--neutral-500);
}

.toggle-switch {
  width: 44px;
  height: 24px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--neutral-300);
  cursor: pointer;
  position: relative;
  transition: background var(--duration-normal);
  flex-shrink: 0;
}

.toggle-switch.on {
  background: var(--accent-500);
}

.toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
  transition: transform var(--duration-normal);
  box-shadow: var(--shadow-sm);
}

.toggle-switch.on .toggle-knob {
  transform: translateX(20px);
}

/* Theme */
.theme-options {
  display: flex;
  gap: var(--space-3);
}

.theme-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-normal);
}

.theme-btn:hover {
  border-color: var(--neutral-300);
}

.theme-btn.active {
  border-color: var(--accent-500);
  background: var(--accent-50);
  color: var(--accent-700);
}

/* Danger */
.danger-zone {
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid rgba(239, 68, 68, 0.2);
}

.danger-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: #ef4444;
  margin-bottom: var(--space-4);
}

.danger-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4);
  background: rgba(239, 68, 68, 0.03);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: var(--radius-md);
}

.danger-name {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
  margin-bottom: 2px;
}

.danger-desc {
  font-size: var(--text-xs);
  color: var(--neutral-500);
}

/* Buttons */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  background: var(--accent-500);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-normal);
  box-shadow: var(--shadow-accent);
}

.btn-primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-outline:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
}

.btn-warning {
  color: #d97706;
  border-color: rgba(217, 119, 6, 0.3);
}

.btn-warning:hover {
  background: rgba(217, 119, 6, 0.05);
  border-color: rgba(217, 119, 6, 0.5);
}

.btn-danger {
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid rgba(239, 68, 68, 0.3);
  border-radius: var(--radius-md);
  background: rgba(239, 68, 68, 0.05);
  color: #ef4444;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
}

.btn-danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
}

/* 未实装 / 进行中的按钮统一置灰，光标也一并禁掉，避免看起来还能点 */
.btn-primary:disabled,
.btn-outline:disabled,
.btn-danger:disabled,
.theme-btn:disabled,
.setting-select:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

.btn-primary:disabled:hover,
.btn-outline:disabled:hover,
.theme-btn:disabled:hover {
  background: var(--surface-elevated);
  border-color: var(--neutral-200);
}

.btn-primary:disabled:hover { background: var(--accent-500); }
.theme-btn.active:disabled { background: var(--accent-50); border-color: var(--accent-500); }
.setting-select:disabled { background: var(--neutral-50); }

/* Export */
.export-row {
  display: flex;
  gap: var(--space-3);
}

/* Flash · 保存/导出结果的行内反馈，3.2s 后自消失 */
.flash-text {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  color: var(--accent-700);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 500;
  animation: fade-in-up 0.2s var(--ease-out-expo);
}

.flash-text.is-error {
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.06);
  border-color: rgba(239, 68, 68, 0.25);
}

.inline-alert {
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  color: #92400e;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.25);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.setting-note {
  margin-top: var(--space-2);
  color: var(--neutral-500);
  font-size: var(--text-xs);
  line-height: 1.6;
}

.avatar-side {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
}
.avatar-side .setting-note { margin-top: 0; }

/* 「即将开放」标记：明确告诉人这块没实装，而不是给个点了没反应的按钮 */
.soon-tag {
  flex-shrink: 0;
  padding: 3px 10px;
  color: var(--neutral-500);
  background: var(--neutral-100);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 500;
}

.soon-tag.is-on {
  color: var(--accent-700);
  background: var(--accent-50);
  border-color: rgba(16, 185, 129, 0.25);
}

.toggle-item.is-disabled { background: var(--surface-elevated); border: 1px dashed var(--neutral-200); }

.bind-value.is-empty { color: var(--neutral-400); font-family: var(--font-body); }

/* Digital human */
.digital-preview {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--surface-primary);
  border-radius: var(--radius-lg);
}

.digital-preview__video {
  width: 108px;
  height: 108px;
  flex-shrink: 0;
  object-fit: cover;
  background: var(--neutral-900);
  border-radius: var(--radius-md);
}

.digital-preview__meta { display: flex; flex-direction: column; gap: var(--space-1); }
.digital-preview__meta strong { color: var(--neutral-900); font-size: var(--text-base); }
.digital-preview__meta span { color: var(--neutral-500); font-size: var(--text-xs); line-height: 1.6; }

/* FAQ */
.faq-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.faq-item + .faq-item { border-top: 1px solid var(--neutral-200); }

.faq-q {
  display: flex;
  width: 100%;
  padding: var(--space-3) var(--space-4);
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--neutral-800);
  text-align: left;
  background: var(--surface-elevated);
  border: none;
  font-family: var(--font-body);
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background var(--duration-fast);
}

.faq-q:hover { background: var(--neutral-50); }
.faq-q b { flex-shrink: 0; color: var(--accent-600); font-size: var(--text-base); }

.faq-a {
  padding: 0 var(--space-4) var(--space-4);
  color: var(--neutral-500);
  font-size: var(--text-sm);
  line-height: 1.75;
  background: var(--surface-elevated);
}

@media (max-width: 768px) {
  .settings-layout { grid-template-columns: 1fr; }
  .settings-nav {
    flex-direction: row;
    overflow-x: auto;
    gap: var(--space-1);
    padding-bottom: var(--space-2);
    /* 与 .mobile-chip-row 一致：横滑但不出滚动条，否则标签条下面挂一条灰杠 */
    scrollbar-width: none;
  }
  .settings-nav::-webkit-scrollbar { display: none; }
  .nav-item { white-space: nowrap; }
  .nav-item.active::before { display: none; }
  .setting-row { grid-template-columns: 1fr; }
  .theme-options { flex-direction: column; }
  .export-row { flex-direction: column; }
}
</style>
