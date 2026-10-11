<template>
  <AppLayout>
    <div class="join-page">
      <header class="page-head">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="page-sub">{{ pageSub }}</p>
      </header>

      <div v-if="demoActive" class="notice-bar">
        离线演示模式已开启：会议功能需要连接服务器，点横幅上的「退出」关闭离线演示后再使用。
      </div>

      <!-- 会议号查找 -->
      <section class="card">
        <label class="field-label" for="meeting-code">会议号</label>
        <div class="lookup-row">
          <input
            id="meeting-code"
            v-model="codeInput"
            class="code-input"
            type="text"
            maxlength="9"
            autocomplete="off"
            spellcheck="false"
            placeholder="例如 3F7K-2Q9A"
            @input="onCodeInput"
            @keyup.enter="handleLookup"
          />
          <button class="btn primary" :disabled="looking || !normalizedInput" @click="handleLookup">
            {{ looking ? '查找中…' : '查找会议' }}
          </button>
        </div>
        <p v-if="lookupError" class="inline-error">{{ lookupError }}</p>

        <div v-if="found" class="found-card">
          <div class="found-main">
            <h3 class="found-title">{{ found.title }}</h3>
            <p class="found-meta">
              主持人：{{ found.hostName || '—' }}
              <span class="sep">·</span>{{ found.participantCount }} 人在会中
              <span class="sep">·</span>
              <span :class="found.joinable ? 'text-ok' : 'text-bad'">{{ foundStatusText }}</span>
            </p>
          </div>
          <button class="btn primary" :disabled="!found.joinable" @click="enterRoom">进入会议室</button>
        </div>
      </section>

      <!-- 设备预览 -->
      <section class="card">
        <div class="card-head">
          <h2 class="card-title">设备预览</h2>
          <div class="head-actions" v-if="mediaStatus === 'active'">
            <button class="btn small" :class="{ off: !micEnabled }" :disabled="!micAvailable" @click="media.toggleMic()">
              {{ micEnabled ? '麦克风开' : '已静音' }}
            </button>
            <button class="btn small" :class="{ off: !camEnabled }" @click="media.toggleCam()">
              {{ camEnabled ? '摄像头开' : '摄像头关' }}
            </button>
            <button class="btn small ghost" @click="media.stop()">关闭设备</button>
          </div>
          <button v-else-if="mediaStatus !== 'requesting'" class="btn primary small" @click="media.start()">
            开启摄像头预览
          </button>
          <span v-else class="requesting-text">正在开启…</span>
        </div>

        <div v-if="mediaStatus === 'active' || mediaStatus === 'requesting'" class="preview-stage">
          <VideoTile
            local
            :stream="previewStream"
            :name="selfName"
            :mic-on="micEnabled"
            :cam-on="camEnabled"
          />
        </div>
        <p v-else class="preview-idle">进入会议室时会自动开启摄像头与麦克风，也可以先在这里预览效果。</p>

        <p v-if="mediaError" class="inline-error">{{ mediaError }}</p>
        <p v-else-if="mediaStatus === 'active' && !micAvailable" class="preview-note">
          未检测到可用麦克风，进入会议后将仅开启视频。
        </p>
      </section>

      <!-- 我发起的会议：企业/教师/管理员才有数据；学生天然为空，整段不出现 -->
      <section v-if="hostedList.length" class="history-section">
        <h2 class="section-title">我发起的会议</h2>
        <div class="meeting-list">
          <div v-for="row in hostedList" :key="'hosted-' + row.id" class="meeting-row">
            <div class="row-main">
              <div class="row-title">{{ row.title }}</div>
              <div class="row-meta">
                <span class="code-text">{{ row.codeDisplay || row.code }}</span>
                <span class="sep">·</span>
                <span>{{ formatTime(row.createdAt) }}</span>
                <template v-if="row.participantCount != null">
                  <span class="sep">·</span>
                  <span>{{ row.participantCount }} 人参与过</span>
                </template>
              </div>
            </div>
            <div class="row-side">
              <span class="status-pill" :class="row.status === 'OPEN' ? 'open' : 'ended'">
                {{ row.status === 'OPEN' ? '进行中' : '已结束' }}
              </span>
              <button v-if="row.status === 'OPEN'" class="btn small ghost" @click="rejoin(row)">进入</button>
              <button class="btn small" @click="openAdvice(row)">面试建议</button>
            </div>
          </div>
        </div>
      </section>

      <!-- 我参与过的会议 -->
      <section class="history-section">
        <h2 class="section-title">我参与过的会议</h2>
        <p v-if="historyLoading" class="section-hint">加载中…</p>
        <p v-else-if="historyError" class="inline-error">{{ historyError }}</p>
        <p v-else-if="!joinedList.length" class="section-hint">{{ joinedHint }}</p>
        <div v-else class="meeting-list">
          <div v-for="row in joinedList" :key="row.id" class="meeting-row">
            <div class="row-main">
              <div class="row-title">{{ row.title }}</div>
              <div class="row-meta">
                <span class="code-text">{{ row.codeDisplay || row.code }}</span>
                <span class="sep">·</span>
                <span>{{ formatTime(row.joinTime || row.createdAt) }}</span>
                <span class="sep">·</span>
                <span>发起人：{{ row.hostName || '—' }}</span>
              </div>
            </div>
            <div class="row-side">
              <span class="status-pill" :class="row.status === 'OPEN' ? 'open' : 'ended'">
                {{ row.status === 'OPEN' ? '进行中' : '已结束' }}
              </span>
              <button v-if="row.status === 'OPEN'" class="btn small ghost" @click="rejoin(row)">再次进入</button>
              <button class="btn small" @click="openAdvice(row)">面试建议</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 建议回看弹层 -->
    <Transition name="fade">
      <div v-if="adviceOpen" class="overlay" @click.self="adviceOpen = false">
        <div class="advice-modal">
          <div class="modal-head">
            <h3 class="modal-title">{{ adviceTitle }} · 面试建议</h3>
            <button class="modal-close" aria-label="关闭" @click="adviceOpen = false">×</button>
          </div>
          <div class="modal-body">
            <AdviceList
              :list="adviceList"
              :loading="adviceLoading"
              :error="adviceError"
              empty-text="这次会议还没有留下建议。"
            />
          </div>
        </div>
      </div>
    </Transition>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../../components/layout/AppLayout.vue'
import VideoTile from '../../components/meeting/VideoTile.vue'
import AdviceList from '../../components/meeting/AdviceList.vue'
import { useMeetingMediaShared } from '../../composables/useMeetingMedia'
import { getMeetingAdvice, getMyMeetings, previewMeeting } from '../../api/meeting'
import { formatCodeInput, isValidCode, normalizeCode } from '../../utils/meetingProtocol'
import { formatTime } from '../../utils/time'
import { demoState } from '../../utils/offlineDemo'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()
const demoActive = computed(() => demoState.active)
const selfName = computed(() => userStore.nickname || userStore.username || '我')

/* ---------- 角色文案：学生凭码进场；教师收到企业发的码；企业是发码方 ---------- */
const ROLE = (localStorage.getItem('role') || '').toUpperCase()
const canHost = ROLE === 'ENTERPRISE' || ROLE === 'TEACHER' || ROLE === 'ADMIN'
const pageTitle = computed(() => (ROLE === 'ENTERPRISE' ? '加入会议' : '视频会议'))
const pageSub = computed(() => {
  if (ROLE === 'ENTERPRISE') return '把会议号发给候选人，或凭收到的会议号加入他人的会议。'
  if (canHost) return '收到企业发来的会议号后，在这里输入即可进入视频面试。'
  return '收到企业或教师发来的会议号后，在这里输入即可进入视频面试。'
})

/* ---------- 设备预览（与会议室共享同一条流；进房时不停，未进房离开则停） ---------- */
const media = useMeetingMediaShared()
const {
  stream: previewStream,
  status: mediaStatus,
  errorMessage: mediaError,
  micEnabled,
  camEnabled,
  micAvailable,
} = media
const enteringRoom = ref(false)

onUnmounted(() => {
  // 真的进了会议室：流由 useMeetingRoom.leave/fail 管；只是浏览后离开：这里收尾
  if (!enteringRoom.value) media.stop()
})

/* ---------- 会议号查找 ---------- */
const codeInput = ref('')
const normalizedInput = computed(() => normalizeCode(codeInput.value))
const looking = ref(false)
const lookupError = ref('')
const found = ref(null)

const foundStatusText = computed(() => {
  if (!found.value) return ''
  if (found.value.joinable) return '可进入'
  return found.value.status === 'OPEN' ? '房间已满' : '已结束'
})

function onCodeInput(event) {
  // 边打字边格式化（四位一组）；输入变化即清掉上一次的查找结果
  codeInput.value = formatCodeInput(event.target.value)
  found.value = null
  lookupError.value = ''
}

async function handleLookup() {
  const code = normalizedInput.value
  if (!isValidCode(code)) {
    lookupError.value = '会议号应为 8 位字母数字（如 3F7K-2Q9A），请核对后重试。'
    return
  }
  looking.value = true
  lookupError.value = ''
  found.value = null
  try {
    found.value = await previewMeeting(code)
    // 查到会议顺手开预览：用户在查找时大概率就是要入会，省一次点击
    if (mediaStatus.value === 'idle') media.start()
  } catch (e) {
    lookupError.value = e.message || '没有找到这个会议号，请核对后重试。'
  } finally {
    looking.value = false
  }
}

function enterRoom() {
  if (!found.value?.joinable) return
  enteringRoom.value = true
  router.push(`/meeting/${found.value.code}/room`)
}

/* ---------- 我的会议：发起的 + 参与过的 ---------- */
const hostedList = ref([])
const joinedList = ref([])
const historyLoading = ref(false)
const historyError = ref('')

/** 参与列表空态：发起方（企业/教师）不说「收到会议号后进入」这种收件人话 */
const joinedHint = computed(() =>
  canHost ? '还没有参与过他人的会议。收到会议号后从上面输入进入。' : '还没有参加过的会议。收到会议号后从上面输入进入。',
)

onMounted(async () => {
  historyLoading.value = true
  try {
    const data = await getMyMeetings()
    hostedList.value = data?.hosted || []
    joinedList.value = data?.joined || []
  } catch (e) {
    historyError.value = demoActive.value
      ? '离线演示模式不含会议数据，点横幅上的「退出」关闭离线演示后可用。'
      : (e.message || '加载参会记录失败')
  } finally {
    historyLoading.value = false
  }
})

function rejoin(row) {
  enteringRoom.value = true
  router.push(`/meeting/${row.code}/room`)
}

/* ---------- 建议回看弹层 ---------- */
const adviceOpen = ref(false)
const adviceTitle = ref('')
const adviceList = ref([])
const adviceLoading = ref(false)
const adviceError = ref('')

async function openAdvice(row) {
  adviceOpen.value = true
  adviceTitle.value = row.title || '会议'
  adviceList.value = []
  adviceError.value = ''
  adviceLoading.value = true
  try {
    adviceList.value = await getMeetingAdvice(row.id)
  } catch (e) {
    adviceError.value = e.message || '加载建议失败'
  } finally {
    adviceLoading.value = false
  }
}
</script>

<style scoped>
.join-page {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding-bottom: var(--space-6);
}

.page-head { padding-top: var(--space-2); }
.page-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-2);
}
.page-sub { font-size: var(--text-sm); color: var(--neutral-500); line-height: 1.7; }

.notice-bar {
  font-size: var(--text-sm);
  color: #8a6100;
  background: #fef7e6;
  border: 1px solid #f5e2b8;
  border-radius: var(--radius-lg);
  padding: 10px 14px;
  line-height: 1.6;
}

.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
  padding: var(--space-5);
}
.field-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-700);
  margin-bottom: var(--space-2);
}

.lookup-row { display: flex; gap: var(--space-3); }
.code-input {
  flex: 1;
  min-width: 0;
  padding: 11px 14px;
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--neutral-800);
  outline: none;
  transition: border-color var(--duration-fast);
}
.code-input:focus { border-color: var(--accent-400); }
.code-input::placeholder { font-weight: 400; letter-spacing: 0.04em; }

.btn {
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  padding: 11px 20px;
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--duration-fast);
}
.btn.small { padding: 7px 14px; }
.btn.primary {
  background: var(--accent-500);
  border-color: var(--accent-500);
  color: #fff;
}
.btn.primary:hover:not(:disabled) { background: var(--accent-600); border-color: var(--accent-600); }
.btn.ghost { background: transparent; }
.btn:hover:not(:disabled):not(.primary) { border-color: var(--neutral-300); background: var(--neutral-50); }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn.off {
  background: var(--color-error-bg);
  border-color: #f3c1c1;
  color: var(--color-error);
}

.inline-error { font-size: var(--text-sm); color: var(--color-error); margin-top: var(--space-3); line-height: 1.6; }

.found-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-top: var(--space-4);
  padding: var(--space-4);
  background: var(--accent-50, #f2f8f6);
  border: 1px solid var(--accent-200);
  border-radius: var(--radius-lg);
}
.found-main { min-width: 0; }
.found-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.found-meta { font-size: var(--text-sm); color: var(--neutral-500); }
.sep { margin: 0 2px; color: var(--neutral-300); }
.text-ok { color: var(--accent-600, #1d7a5f); font-weight: 600; }
.text-bad { color: var(--color-error); font-weight: 600; }

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}
.card-title { font-size: var(--text-lg); font-weight: 700; color: var(--neutral-900); }
.head-actions { display: flex; gap: var(--space-2); }
.requesting-text { font-size: var(--text-sm); color: var(--neutral-400); }

.preview-stage {
  width: 100%;
  max-width: 480px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: #101214;
}
.preview-idle { font-size: var(--text-sm); color: var(--neutral-400); line-height: 1.7; }
.preview-note {
  margin-top: var(--space-3);
  font-size: var(--text-xs);
  color: #8a6100;
  background: #fef7e6;
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  line-height: 1.6;
}

.history-section { display: flex; flex-direction: column; gap: var(--space-3); }
.section-title { font-size: var(--text-lg); font-weight: 700; color: var(--neutral-900); }
.section-hint { font-size: var(--text-sm); color: var(--neutral-400); line-height: 1.7; }

.meeting-list { display: flex; flex-direction: column; gap: var(--space-2); }
.meeting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
}
.row-main { min-width: 0; }
.row-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 2px;
  font-size: var(--text-xs);
  color: var(--neutral-400);
}
.code-text { font-family: var(--font-mono); font-weight: 700; letter-spacing: 0.05em; color: var(--neutral-500); }
.row-side { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }
.status-pill {
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: 999px;
  padding: 3px 10px;
}
.status-pill.open { color: var(--accent-700, #15604b); background: var(--accent-100, #dcefe9); }
.status-pill.ended { color: var(--neutral-500); background: var(--neutral-100); }

/* === 建议弹层 === */
.overlay {
  position: fixed;
  inset: 0;
  z-index: 400;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(3px);
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
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--neutral-100);
}
.modal-title {
  font-size: var(--text-base, 1rem);
  font-weight: 700;
  color: var(--neutral-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--neutral-400);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}
.modal-close:hover { color: var(--neutral-600); background: var(--neutral-100); }
.modal-body { padding: var(--space-5); overflow-y: auto; }

.fade-enter-active,
.fade-leave-active { transition: opacity var(--duration-normal, 0.18s) ease; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }

@media (max-width: 640px) {
  .lookup-row { flex-direction: column; }
  .found-card { flex-direction: column; align-items: stretch; }
  .meeting-row { flex-direction: column; align-items: stretch; gap: var(--space-3); }
}
</style>
