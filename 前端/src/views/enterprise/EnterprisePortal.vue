<template>
  <AppLayout>
    <div class="portal-page">
      <header class="page-head">
        <h1 class="page-title">会议中心</h1>
        <p class="page-desc">开启一场视频面试，把会议号发给学生或教师，对方凭号进入；面对面交流后可留下书面建议。</p>
      </header>

      <!-- 会议接口不在离线演示数据覆盖范围，先说清楚比点下去报错强。
           出口指横幅上的「退出」：登录页对已登录账号是走不到的（守卫会弹回来）。 -->
      <div v-if="demoActive" class="notice notice-warn">
        离线演示模式已开启，会议功能需要连接服务器。请点屏幕上的横幅「退出」关闭离线演示模式后再使用。
      </div>

      <!-- 开启会议 -->
      <section class="card create-card">
        <div class="create-field">
          <label class="field-label" for="meeting-title">会议主题</label>
          <input
            id="meeting-title"
            v-model="createTitle"
            class="text-input"
            type="text"
            maxlength="80"
            placeholder="例如：前端实习生面试 / 作品点评"
            @keyup.enter="handleCreate"
          />
        </div>
        <button class="btn btn-primary" :disabled="creating || !createTitle.trim()" @click="handleCreate">
          {{ creating ? '创建中…' : '开启会议' }}
        </button>
        <p class="create-hint">创建后会生成 8 位会议号（如 <code>3F7K-2Q9A</code>），把它发给对方即可进入。</p>
        <p v-if="createError" class="inline-error">{{ createError }}</p>
      </section>

      <!-- 我的会议 -->
      <section class="meeting-section">
        <div class="section-head">
          <h2 class="section-title">我的会议</h2>
          <div class="tabs" role="tablist">
            <button
              v-for="tab in tabs"
              :key="tab.id"
              class="tab"
              :class="{ active: activeTab === tab.id }"
              role="tab"
              :aria-selected="activeTab === tab.id"
              @click="activeTab = tab.id"
            >
              {{ tab.label }}（{{ tab.count }}）
            </button>
          </div>
        </div>

        <p v-if="loadError" class="inline-error">
          {{ loadError }}
          <button class="btn btn-ghost btn-sm" @click="loadMine">重试</button>
        </p>
        <p v-else-if="loading" class="section-hint">加载中…</p>
        <p v-else-if="!currentList.length" class="section-hint">{{ emptyHint }}</p>

        <div v-else class="meeting-list">
          <div v-for="row in currentList" :key="row.id" class="meeting-row">
            <div class="row-main">
              <div class="row-title">{{ row.title }}</div>
              <div class="row-meta">
                <button class="code-chip" title="点击复制会议号" @click="copyCode(row)">
                  {{ row.codeDisplay || row.code }}
                  <span class="code-copy">{{ copiedId === row.id ? '已复制' : '复制' }}</span>
                </button>
                <span class="row-time">{{ formatTime(row.createdAt) }}</span>
                <span v-if="activeTab === 'hosted' && row.participantCount != null" class="row-extra">
                  {{ row.participantCount }} 人参与过
                </span>
                <span v-else-if="row.hostName" class="row-extra">发起人：{{ row.hostName }}</span>
              </div>
            </div>

            <div class="row-side">
              <span class="status-pill" :class="row.status === 'OPEN' ? 'open' : 'ended'">
                {{ row.status === 'OPEN' ? '进行中' : '已结束' }}
              </span>
              <button v-if="row.status === 'OPEN'" class="btn btn-primary btn-sm" @click="enterRoom(row)">进入</button>
              <button
                v-if="row.status === 'OPEN' && activeTab === 'hosted'"
                class="btn btn-danger btn-sm"
                :disabled="endingId === row.id"
                @click="handleEnd(row)"
              >
                {{ endingId === row.id ? '结束中…' : '结束' }}
              </button>
              <button class="btn btn-ghost btn-sm" @click="openAdvice(row)">建议</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 建议弹层：会议进行中可实时写、结束后可随时补写；学生端实时（会议中）或回看（结束后）都能看到 -->
    <Transition name="fade">
      <div v-if="adviceOpen" class="overlay" @click.self="adviceOpen = false">
        <div class="advice-modal" role="dialog" aria-label="面试建议">
          <div class="modal-head">
            <h3 class="modal-title">{{ adviceTitle }} · 面试建议</h3>
            <button class="modal-close" aria-label="关闭" @click="adviceOpen = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <AdvicePanel
              :list="adviceList"
              :loading="adviceLoading"
              :list-error="adviceError"
              :can-write="true"
              :on-submit="handleSubmitAdvice"
            />
          </div>
        </div>
      </div>
    </Transition>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../../components/layout/AppLayout.vue'
import AdvicePanel from '../../components/meeting/AdvicePanel.vue'
import { createMeeting, endMeeting, getMeetingAdvice, getMyMeetings, postMeetingAdvice } from '../../api/meeting'
import { formatTime } from '../../utils/time'
import { demoState } from '../../utils/offlineDemo'

const router = useRouter()

const demoActive = computed(() => demoState.active)

/* ---------- 开启会议 ---------- */
const createTitle = ref('')
const creating = ref(false)
const createError = ref('')

async function handleCreate() {
  const title = createTitle.value.trim()
  if (!title || creating.value) return
  creating.value = true
  createError.value = ''
  try {
    const meeting = await createMeeting({ title })
    // 创建成功即以发起人身份进房，会议号在房间里可复制
    router.push(`/meeting/${meeting.code}/room`)
  } catch (e) {
    createError.value = demoActive.value
      ? '离线演示模式不含会议功能，点横幅上的「退出」关闭离线演示后可用。'
      : (e.message || '创建会议失败，请重试')
  } finally {
    creating.value = false
  }
}

/* ---------- 我的会议 ---------- */
const loading = ref(true)
const loadError = ref('')
const hosted = ref([])
const joined = ref([])
const activeTab = ref('hosted')
const endingId = ref(null)
const copiedId = ref(null)

const tabs = computed(() => [
  { id: 'hosted', label: '我发起的', count: hosted.value.length },
  { id: 'joined', label: '我参与的', count: joined.value.length },
])
const currentList = computed(() => (activeTab.value === 'hosted' ? hosted.value : joined.value))
const emptyHint = computed(() =>
  activeTab.value === 'hosted'
    ? '还没有发起过会议。填个主题，开启第一场视频面试吧。'
    : '你还没参加过别人的会议。收到会议号后，在「加入会议」里输入即可进入。',
)

async function loadMine() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await getMyMeetings()
    hosted.value = data.hosted || []
    joined.value = data.joined || []
  } catch (e) {
    loadError.value = demoActive.value
      ? '离线演示模式不含会议数据，点横幅上的「退出」关闭离线演示后可用。'
      : (e.message || '加载会议列表失败')
  } finally {
    loading.value = false
  }
}

function enterRoom(row) {
  router.push(`/meeting/${row.code}/room`)
}

async function handleEnd(row) {
  if (endingId.value) return
  if (!window.confirm(`结束「${row.title}」？房间里的成员会被移出会议。`)) return
  endingId.value = row.id
  try {
    await endMeeting(row.id)
    row.status = 'ENDED' // 就地更新，避免整表重刷导致 tab 回到默认态
  } catch (e) {
    window.alert(e.message || '结束会议失败，请重试')
  } finally {
    endingId.value = null
  }
}

async function copyCode(row) {
  const text = row.codeDisplay || row.code
  try {
    await navigator.clipboard.writeText(text)
    copiedId.value = row.id
    setTimeout(() => {
      if (copiedId.value === row.id) copiedId.value = null
    }, 1500)
  } catch {
    // 非安全上下文里 clipboard 不可用：退化成手动选中
    window.prompt('复制会议号：', text)
  }
}

/* ---------- 建议：回看 + 随时补写 ---------- */
const adviceOpen = ref(false)
const adviceLoading = ref(false)
const adviceError = ref('')
const adviceList = ref([])
const adviceTitle = ref('')
const adviceMeetingId = ref(null)

async function openAdvice(row) {
  adviceOpen.value = true
  adviceLoading.value = true
  adviceError.value = ''
  adviceList.value = []
  adviceTitle.value = row.title
  adviceMeetingId.value = row.id
  try {
    adviceList.value = (await getMeetingAdvice(row.id)) || []
  } catch (e) {
    adviceError.value = e.message || '加载建议失败'
  } finally {
    adviceLoading.value = false
  }
}

/** AdvicePanel 的 onSubmit：落库成功即插入列表（返回值带 createdAt）；学生端会议中走 WS 实时收到，结束后回看 */
async function handleSubmitAdvice(content) {
  const created = await postMeetingAdvice(adviceMeetingId.value, { content })
  adviceList.value = [created, ...adviceList.value]
}

onMounted(loadMine)
</script>

<style scoped>
.portal-page {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-8) 0 var(--space-12);
}

/* === 页头 === */
.page-head { margin-bottom: var(--space-6); }
.page-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-2);
}
.page-desc { font-size: var(--text-sm); color: var(--neutral-500); line-height: 1.7; }

/* === 通用 === */
.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
  padding: var(--space-6);
}
.notice {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  margin-bottom: var(--space-4);
  line-height: 1.6;
}
.notice-warn { background: #fef7e6; border: 1px solid #f5d789; color: #8a6100; }
.inline-error {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-error);
}
.section-hint { font-size: var(--text-sm); color: var(--neutral-400); padding: var(--space-6) 0; }

/* === 开启会议 === */
.create-card {
  display: flex;
  align-items: flex-end;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-bottom: var(--space-8);
}
.create-field { flex: 1 1 320px; }
.field-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-700);
  margin-bottom: var(--space-2);
}
.text-input {
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-800);
  font-family: var(--font-body);
  font-size: var(--text-base);
  outline: none;
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.text-input:focus { border-color: var(--accent-400); box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1); }
.create-hint {
  flex-basis: 100%;
  margin: 0;
  font-size: var(--text-xs);
  color: var(--neutral-400);
  line-height: 1.6;
}
.create-hint code {
  font-family: var(--font-mono);
  background: var(--neutral-100);
  padding: 1px 6px;
  border-radius: 4px;
}

/* === 按钮 === */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  padding: 11px 22px;
  transition: all var(--duration-normal) var(--ease-out-expo);
  white-space: nowrap;
}
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-primary { background: var(--accent-500); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--accent-600); box-shadow: var(--shadow-accent); }
.btn-danger { background: transparent; border-color: #f3c1c1; color: var(--color-error); }
.btn-danger:hover:not(:disabled) { background: var(--color-error-bg); }
.btn-ghost { background: transparent; border-color: var(--neutral-300); color: var(--neutral-600); }
.btn-ghost:hover:not(:disabled) { border-color: var(--neutral-400); color: var(--neutral-800); }
.btn-sm { padding: 6px 14px; font-size: var(--text-xs); }

/* === 我的会议 === */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}
.section-title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
}
.tabs {
  display: flex;
  gap: var(--space-1);
  background: var(--neutral-100);
  border-radius: var(--radius-lg);
  padding: 3px;
}
.tab {
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  padding: 6px 16px;
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-500);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.tab.active {
  background: var(--surface-elevated);
  color: var(--accent-700);
  box-shadow: var(--shadow-sm);
}

.meeting-list { display: flex; flex-direction: column; gap: var(--space-3); }
.meeting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  transition: border-color var(--duration-fast);
}
.meeting-row:hover { border-color: var(--neutral-300); }
.row-main { min-width: 0; }
.row-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-800);
  margin-bottom: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  font-size: var(--text-xs);
  color: var(--neutral-400);
}
.code-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--neutral-200);
  background: var(--neutral-50);
  border-radius: var(--radius-sm);
  padding: 3px 8px;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--neutral-700);
  cursor: pointer;
  transition: border-color var(--duration-fast);
}
.code-chip:hover { border-color: var(--accent-400); color: var(--accent-700); }
.code-copy { font-family: var(--font-body); font-weight: 400; color: var(--neutral-400); }
.row-side { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }
.status-pill {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-full);
}
.status-pill.open { background: var(--accent-50); color: var(--accent-700); }
.status-pill.ended { background: var(--neutral-100); color: var(--neutral-500); }

/* === 建议弹层 === */
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  z-index: 300;
}
.advice-modal {
  width: 100%;
  max-width: 560px;
  max-height: 76vh;
  display: flex;
  flex-direction: column;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--neutral-100);
}
.modal-title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--neutral-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.modal-close {
  border: none;
  background: transparent;
  color: var(--neutral-400);
  cursor: pointer;
  padding: 4px;
  display: flex;
  border-radius: var(--radius-sm);
}
.modal-close:hover { color: var(--neutral-700); background: var(--neutral-100); }
.modal-body { padding: var(--space-4) var(--space-5); overflow-y: auto; }

.fade-enter-active, .fade-leave-active { transition: opacity var(--duration-fast); }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 720px) {
  .meeting-row { flex-direction: column; align-items: stretch; }
  .row-side { justify-content: flex-end; }
  .create-card { align-items: stretch; }
}
</style>
