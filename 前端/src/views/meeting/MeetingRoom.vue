<template>
  <AppLayout>
    <div class="meeting-page">
      <!-- 手机端不做会议适配：明确引导到电脑端，也避免白耗资源去连 WS -->
      <div v-if="isMobile" class="mobile-card">
        <h1 class="mobile-title">视频会议</h1>
        <p class="mobile-desc">视频面试需要摄像头与麦克风，请使用电脑端浏览器打开本页面。</p>
      </div>

      <template v-else>
        <!-- 房间顶栏 -->
        <div class="room-head">
          <div class="head-left">
            <h1 class="room-title">{{ meetingTitle }}</h1>
            <button class="code-chip" title="点击复制会议号" @click="copyCode">
              {{ displayCode }}
              <span class="code-copy">{{ copied ? '已复制' : '复制' }}</span>
            </button>
          </div>
          <div class="head-right">
            <span class="live-dot" :class="{ off: room.status.value !== 'joined' }"></span>
            <span class="head-count">{{ peers.length }}/6 人</span>
          </div>
        </div>

        <div class="room-body">
          <!-- 视频区 -->
          <div class="stage">
            <div v-if="!remotePeers.length" class="waiting">
              <p class="waiting-title">等待成员加入</p>
              <p class="waiting-code">{{ displayCode }}</p>
              <p class="waiting-hint">把会议号发给需要参会的人，对方在「视频会议」页输入即可进入。</p>
              <button class="waiting-copy" @click="copyCode">{{ copied ? '已复制 ✓' : '复制会议号' }}</button>
            </div>

            <div v-else class="tile-grid">
              <VideoTile
                v-for="p in remotePeers"
                :key="p.id"
                :stream="p.stream"
                :name="p.name"
                :mic-on="p.micOn"
                :cam-on="p.camOn"
                :connection-state="p.connectionState"
              />
            </div>

            <!-- 本地画面（右下角画中画） -->
            <div class="pip">
              <VideoTile
                local
                :stream="localStream"
                :name="selfName"
                :mic-on="micEnabled"
                :cam-on="camEnabled"
              />
            </div>
          </div>

          <!-- 侧栏：成员 / 建议 -->
          <aside class="side">
            <div class="side-tabs">
              <button class="side-tab" :class="{ active: sideTab === 'members' }" @click="sideTab = 'members'">
                成员（{{ peers.length }}）
              </button>
              <button class="side-tab" :class="{ active: sideTab === 'advice' }" @click="sideTab = 'advice'">
                建议（{{ room.adviceList.value.length }}）
              </button>
            </div>

            <div v-if="sideTab === 'members'" class="member-list">
              <div v-for="p in peers" :key="p.id" class="member-row">
                <span class="member-avatar">{{ initialOf(p.name) }}</span>
                <div class="member-main">
                  <span class="member-name">{{ p.name }}<em v-if="p.id === room.selfId.value">（我）</em></span>
                  <span class="member-role">{{ roleLabel(p.role) }}</span>
                </div>
                <span class="member-icons">
                  <span class="mi" :class="{ off: !memberMicOn(p) }" :title="memberMicOn(p) ? '麦克风开' : '已静音'">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
                      <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" />
                      <line v-if="!memberMicOn(p)" x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  </span>
                  <span class="mi" :class="{ off: !memberCamOn(p) }" :title="memberCamOn(p) ? '摄像头开' : '已关闭摄像头'">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="m23 7-7 5 7 5V7z" /><rect x="1" y="5" width="15" height="14" rx="2" />
                      <line v-if="!memberCamOn(p)" x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  </span>
                </span>
                <span class="conn-dot" :class="connClass(p)" :title="connTitle(p)"></span>
              </div>

              <p v-if="mediaStatus === 'active' && !micAvailable" class="member-note">未检测到可用麦克风，当前仅开启视频。</p>
            </div>

            <div v-else class="side-advice">
              <AdvicePanel
                :list="room.adviceList.value"
                :loading="adviceLoading"
                :list-error="adviceError"
                :can-write="canWrite"
                :on-submit="handleSubmitAdvice"
              />
            </div>
          </aside>
        </div>

        <!-- 底部控制条 -->
        <MeetingControls
          :mic-on="micEnabled"
          :cam-on="camEnabled"
          :mic-available="micAvailable"
          :is-host="isHost"
          @toggle-mic="room.toggleMic()"
          @toggle-cam="room.toggleCam()"
          @leave="handleLeave"
          @end="handleEnd"
        />
      </template>

      <!-- 全屏遮罩：连接中 / 结束 / 被移出 / 出错 -->
      <div v-if="overlayVisible" class="room-mask">
        <div class="mask-card">
          <template v-if="!finalStatus">
            <div class="spinner"></div>
            <p class="mask-text">正在进入会议…</p>
          </template>

          <template v-else-if="room.status.value === 'ended'">
            <h2 class="mask-title">会议已结束</h2>
            <p class="mask-text">{{ room.errorMessage.value || '本次会议已由发起人结束。' }}</p>
            <button class="mask-btn primary" @click="goBack">返回</button>
          </template>

          <template v-else-if="room.status.value === 'kicked'">
            <h2 class="mask-title">连接已被移出</h2>
            <p class="mask-text">{{ room.errorMessage.value }}</p>
            <button class="mask-btn primary" @click="goBack">返回</button>
          </template>

          <template v-else>
            <h2 class="mask-title">无法进入会议</h2>
            <p class="mask-text">{{ room.errorMessage.value || '请稍后重试' }}</p>
            <div class="mask-actions">
              <button class="mask-btn ghost" @click="goBack">返回</button>
              <button class="mask-btn primary" @click="retry">重试</button>
            </div>
          </template>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import AppLayout from '../../components/layout/AppLayout.vue'
import VideoTile from '../../components/meeting/VideoTile.vue'
import AdvicePanel from '../../components/meeting/AdvicePanel.vue'
import MeetingControls from '../../components/meeting/MeetingControls.vue'
import { useMeetingRoom } from '../../composables/useMeetingRoom'
import { endMeeting, getMeetingAdvice } from '../../api/meeting'
import { formatCodeInput } from '../../utils/meetingProtocol'
import { useUserStore } from '../../store/user'
import { useIsMobile } from '../../mobile/composables/useIsMobile'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isMobile = useIsMobile()

const room = useMeetingRoom()
const { status, errorMessage, meeting, selfId, peers, remotePeers, localStream, micEnabled, camEnabled, micAvailable, mediaStatus, adviceList, isHost } = room

const code = computed(() => String(route.params.code || ''))
const displayCode = computed(() => formatCodeInput(code.value))
const meetingTitle = computed(() => meeting.value?.title || '视频会议')
const selfName = computed(() => userStore.nickname || userStore.username || '我')
const canWrite = computed(() => ['ENTERPRISE', 'TEACHER', 'ADMIN'].includes((userStore.role || '').toUpperCase()))
// 终态才需要用户看遮罩决策；idle 也算「进行中」（onMounted 首帧还没跑 join，防止闪一下错误卡）
const finalStatus = computed(() => ['ended', 'kicked', 'error'].includes(status.value))
const overlayVisible = computed(() => !isMobile.value && status.value !== 'joined')

const sideTab = ref('members')
const copied = ref(false)

/* ---------- 历史建议种子（进房成功拉一次；重连后 status 再走 joined 会自动重拉，
   setAdviceSeed 按 id 去重，与 WS 增量不冲突） ---------- */
const adviceLoading = ref(false)
const adviceError = ref('')

async function loadSeed(id) {
  if (!id) return
  adviceLoading.value = true
  adviceError.value = ''
  try {
    room.setAdviceSeed(await getMeetingAdvice(id))
  } catch (e) {
    adviceError.value = e.message || '加载历史建议失败'
  } finally {
    adviceLoading.value = false
  }
}

watch(status, (next) => {
  if (next === 'joined') loadSeed(meeting.value?.id)
})

/* ---------- 生命周期 ---------- */
onMounted(() => {
  if (isMobile.value) return
  room.join(code.value)
})

// 顶栏导航离开时挂断（否则摄像头灯不灭、其他成员一直看着你的画面）。
// leave() 幂等，挂断按钮自己 push 时重复调用无副作用。
onBeforeRouteLeave(() => {
  room.leave()
})

/* ---------- 操作 ---------- */
async function copyCode() {
  try {
    await navigator.clipboard.writeText(displayCode.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch {
    window.prompt('复制会议号：', displayCode.value)
  }
}

function handleLeave() {
  room.leave()
  router.push('/meeting')
}

async function handleEnd() {
  const id = meeting.value?.id
  if (!id) return
  if (!window.confirm('结束会议？房间里的成员会被移出。')) return
  try {
    // 服务端广播 meeting-ended 并关闭全部连接，本端状态由信令/关闭码驱动
    await endMeeting(id)
  } catch (e) {
    window.alert(e.message || '结束会议失败，请重试')
  }
}

/** AdvicePanel 的 onSubmit：抛错由面板展示 */
async function handleSubmitAdvice(content) {
  await room.postAdvice(content)
}

function goBack() {
  router.push('/meeting')
}

function retry() {
  room.join(code.value)
}

/* ---------- 成员展示 ---------- */
function initialOf(name) {
  return String(name || '?').trim().charAt(0) || '?'
}

const ROLE_LABELS = { STUDENT: '学生', TEACHER: '教师', ENTERPRISE: '企业', ADMIN: '管理员' }
function roleLabel(role) {
  return ROLE_LABELS[String(role || '').toUpperCase()] || ''
}

function memberMicOn(p) {
  return p.id === selfId.value ? micEnabled.value : p.micOn
}
function memberCamOn(p) {
  return p.id === selfId.value ? camEnabled.value : p.camOn
}

const CONN_LABELS = {
  self: '本机',
  connected: '已连接',
  connecting: '连接中',
  new: '连接中',
  disconnected: '重连中',
  failed: '连接中断',
  closed: '已关闭',
}
function connClass(p) {
  if (p.id === selfId.value) return 'ok'
  const state = p.connectionState || 'connecting'
  if (state === 'connected') return 'ok'
  if (state === 'failed') return 'bad'
  return 'warn'
}
function connTitle(p) {
  if (p.id === selfId.value) return CONN_LABELS.self
  return CONN_LABELS[p.connectionState] || '连接中'
}
</script>

<style scoped>
.meeting-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 1440px;
  margin: 0 auto;
  padding-bottom: var(--space-6);
}

/* === 移动端引导 === */
.mobile-card {
  max-width: 480px;
  margin: 18vh auto 0;
  padding: var(--space-8);
  text-align: center;
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
}
.mobile-title { font-family: var(--font-display); font-size: var(--text-2xl); font-weight: 700; color: var(--neutral-900); margin-bottom: var(--space-3); }
.mobile-desc { font-size: var(--text-sm); color: var(--neutral-500); line-height: 1.7; }

/* === 顶栏 === */
.room-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}
.head-left { display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
.room-title {
  font-family: var(--font-display);
  font-size: var(--text-xl, 1.25rem);
  font-weight: 700;
  color: var(--neutral-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.code-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--neutral-200);
  background: var(--neutral-50);
  border-radius: var(--radius-sm);
  padding: 4px 10px;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--neutral-700);
  cursor: pointer;
  transition: border-color var(--duration-fast);
}
.code-chip:hover { border-color: var(--accent-400); color: var(--accent-700); }
.code-copy { font-family: var(--font-body); font-size: var(--text-xs); font-weight: 400; color: var(--neutral-400); }
.head-right { display: flex; align-items: center; gap: var(--space-2); }
.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-500);
  animation: pulse 1.6s ease-in-out infinite;
}
.live-dot.off { background: var(--neutral-300); animation: none; }
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
.head-count { font-size: var(--text-sm); color: var(--neutral-500); }

/* === 主区 === */
.room-body { display: flex; gap: var(--space-4); align-items: stretch; }
.stage {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 58vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: #101214;
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.waiting { text-align: center; color: rgba(255, 255, 255, 0.85); padding: var(--space-6); }
.waiting-title { font-size: var(--text-lg); font-weight: 600; margin-bottom: var(--space-4); }
.waiting-code {
  font-family: var(--font-mono);
  font-size: 2.6rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #fff;
  margin-bottom: var(--space-3);
}
.waiting-hint { font-size: var(--text-sm); color: rgba(255, 255, 255, 0.5); margin-bottom: var(--space-5); line-height: 1.7; }
.waiting-copy {
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: transparent;
  color: #fff;
  border-radius: var(--radius-md);
  padding: 8px 20px;
  font-family: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: background var(--duration-fast);
}
.waiting-copy:hover { background: rgba(255, 255, 255, 0.1); }

.tile-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--space-3);
}

.pip {
  position: absolute;
  right: var(--space-4);
  bottom: var(--space-4);
  width: 220px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  z-index: 2;
}

/* === 侧栏 === */
.side {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
  overflow: hidden;
  max-height: 78vh;
}
.side-tabs {
  display: flex;
  gap: var(--space-1);
  background: var(--neutral-100);
  padding: 3px;
  margin: var(--space-3);
  border-radius: var(--radius-lg);
  flex-shrink: 0;
}
.side-tab {
  flex: 1;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  padding: 7px 8px;
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-500);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out-expo);
}
.side-tab.active { background: var(--surface-elevated); color: var(--accent-700); box-shadow: var(--shadow-sm); }

.member-list { overflow-y: auto; padding: 0 var(--space-3) var(--space-3); display: flex; flex-direction: column; gap: var(--space-1); }
.member-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2);
  border-radius: var(--radius-md);
}
.member-row:hover { background: var(--neutral-50); }
.member-avatar {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--accent-500);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}
.member-main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.member-name { font-size: var(--text-sm); font-weight: 600; color: var(--neutral-800); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.member-name em { font-style: normal; font-weight: 400; color: var(--neutral-400); }
.member-role { font-size: 11px; color: var(--neutral-400); }
.member-icons { display: flex; gap: 6px; flex-shrink: 0; }
.mi { display: inline-flex; color: var(--neutral-500); }
.mi.off { color: var(--color-error); }
.conn-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; background: var(--neutral-300); }
.conn-dot.ok { background: var(--accent-500); }
.conn-dot.warn { background: #f59e0b; animation: pulse 1.4s ease-in-out infinite; }
.conn-dot.bad { background: var(--color-error); }
.member-note { font-size: var(--text-xs); color: #8a6100; background: #fef7e6; border-radius: var(--radius-sm); padding: 8px 10px; margin-top: var(--space-2); line-height: 1.6; }

.side-advice { overflow-y: auto; padding: 0 var(--space-4) var(--space-4); }

/* === 遮罩 === */
.room-mask {
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
.mask-card {
  width: 100%;
  max-width: 420px;
  padding: var(--space-8);
  text-align: center;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}
.mask-title { font-family: var(--font-display); font-size: var(--text-xl, 1.25rem); font-weight: 700; color: var(--neutral-900); margin-bottom: var(--space-3); }
.mask-text { font-size: var(--text-sm); color: var(--neutral-500); line-height: 1.7; margin-bottom: var(--space-5); }
.mask-actions { display: flex; gap: var(--space-3); justify-content: center; }
.mask-btn {
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  padding: 10px 22px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all var(--duration-fast);
}
.mask-btn.primary { background: var(--accent-500); color: #fff; }
.mask-btn.primary:hover { background: var(--accent-600); }
.mask-btn.ghost { background: transparent; border-color: var(--neutral-300); color: var(--neutral-600); }
.mask-btn.ghost:hover { border-color: var(--neutral-400); }

.spinner {
  width: 36px;
  height: 36px;
  margin: 0 auto var(--space-4);
  border-radius: 50%;
  border: 3px solid var(--neutral-200);
  border-top-color: var(--accent-500);
  animation: spin 0.9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1100px) {
  .room-body { flex-direction: column; }
  .side { width: 100%; max-height: none; }
}
</style>
