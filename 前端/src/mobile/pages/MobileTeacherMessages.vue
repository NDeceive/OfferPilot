<template>
  <MobileShell v-bind="shell" title="教学消息" subtitle="班级通知、任务进展与报告反馈">
    <div class="mobile-segments mmsg__tabs" role="tablist">
      <button :class="{ active: tab === 'all' }" @click="tab = 'all'">全部 {{ messages.length }}</button>
      <button :class="{ active: tab === 'unread' }" @click="tab = 'unread'">未读 {{ unreadCount }}</button>
    </div>

    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在加载消息" />
    <MobileState v-else-if="error" kind="error" title="消息暂时无法加载" description="请检查网络后重试。" action="重新加载" @action="load" />
    <div v-else-if="visible.length" class="mmsg__list">
      <article
        v-for="m in visible"
        :key="m.id"
        class="mobile-info-card mmsg__item"
        :class="{ 'is-open': openId === m.id, 'is-unread': !m.isRead }"
      >
        <button type="button" class="mmsg__head" @click="toggle(m)">
          <span class="mmsg__dot" :class="{ 'is-on': !m.isRead }" aria-hidden="true"></span>
          <span class="mobile-grow">
            <strong>{{ m.title }}</strong>
            <small>{{ formatDateTime(m.createdAt) }}</small>
          </span>
          <b class="mmsg__chevron" :class="{ 'is-open': openId === m.id }">›</b>
        </button>
        <p v-if="openId === m.id" class="mmsg__body">{{ m.body }}</p>
      </article>
    </div>
    <MobileState
      v-else
      :title="messages.length ? '没有未读消息' : '暂无教学消息'"
      :description="messages.length ? '全部消息都已处理完毕。' : '班级通知、任务发布与报告反馈会出现在这里。'"
      :action="messages.length ? '查看全部' : ''"
      @action="tab = 'all'"
    />
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { teaching } from '../../services/teachingApi'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { TEACHER_TABS } from '../nav'
import { formatDateTime } from '../teacherFormat'

const shell = {
  homePath: '/teacher/dashboard',
  messagePath: '/teacher/messages',
  profilePath: '/teacher/account',
  navLinks: [],
  tabItems: TEACHER_TABS,
}

const loading = ref(true)
const error = ref(false)
const messages = ref([])
const tab = ref('all')
const openId = ref(null)

const unreadCount = computed(() => messages.value.filter((m) => !m.isRead).length)
const visible = computed(() => (tab.value === 'unread' ? messages.value.filter((m) => !m.isRead) : messages.value))

async function load() {
  loading.value = true
  error.value = false
  try {
    messages.value = (await teaching.messages()) || []
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function toggle(m) {
  openId.value = openId.value === m.id ? null : m.id
  // 展开即视为已读：本地先翻，再回写服务端；回写失败不回滚（下次刷新会纠正）
  if (openId.value === m.id && !m.isRead) {
    m.isRead = true
    try { await teaching.read(m.id) } catch { /* 静默：已读状态下次加载时以服务端为准 */ }
  }
}

onMounted(load)
</script>

<style scoped>
.mmsg__tabs { margin-bottom: 14px; }
.mmsg__list { display: flex; flex-direction: column; gap: 10px; }
.mmsg__item { padding: 0; overflow: hidden; }
.mmsg__head { display: flex; width: 100%; min-height: 64px; padding: 12px 16px; align-items: center; gap: 10px; color: var(--m-text); text-align: left; background: transparent; border: 0; font: inherit; }
.mmsg__dot { flex: 0 0 auto; width: 8px; height: 8px; background: transparent; border-radius: 50%; }
.mmsg__dot.is-on { background: #d97706; }
.mmsg__head strong { display: block; font-size: 14px; line-height: 1.45; }
.mmsg__head small { display: block; margin-top: 3px; color: var(--m-text-tertiary); font-size: 11.5px; }
.mmsg__chevron { color: var(--m-text-tertiary); font-size: 20px; transition: transform .18s ease; }
.mmsg__chevron.is-open { transform: rotate(90deg); }
.mmsg__body { padding: 0 16px 14px 34px; color: var(--m-text-secondary); border-top: 1px dashed var(--m-border); padding-top: 12px; font-size: 13.5px; line-height: 1.75; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
