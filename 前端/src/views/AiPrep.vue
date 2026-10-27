<template>
  <AppLayout>
    <div class="ai-page">
      <section class="chat">
        <!-- 顶栏：身份 + 当前话术来源 + 切回手动录入 -->
        <header class="chat__bar">
          <div class="chat__who">
            <span class="chat__brand">面</span>
            <span>
              <strong class="chat__title">就业指导老师</strong>
              <span class="chat__mode" :title="modeHint">
                <i class="chat__dot" :class="aiMode === 'AI' ? 'chat__dot--live' : 'chat__dot--demo'" />
                {{ aiMode === 'AI' ? 'AI 实时生成' : '演示模式' }}
              </span>
            </span>
          </div>
          <PrepModeSwitch mode="chat" :job="selectedJob?.code || preferredJob?.code" />
        </header>

        <!-- 对话区 -->
        <div ref="scroller" class="chat__body">
          <template v-for="m in messages" :key="m.id">
            <div class="row" :class="`row--${m.role}`">
              <span v-if="m.role === 'ai'" class="row__avatar">面</span>
              <div class="bubble" :class="`bubble--${m.role}`">
                <span v-if="m.role === 'ai' && !m.text" class="dots"><i /><i /><i /></span>
                <span v-else class="bubble__text">{{ m.text }}</span>
                <i v-if="m.id === typingId && m.text" class="caret" />
              </div>
            </div>

            <!-- 交互控件贴着这条气泡出现，且只在话术吐完之后 -->
            <div v-if="controlsFor === m.id" class="ctl">
              <button v-if="failedTurn" type="button" class="link-btn" @click="retryReply">{{ fallbackReason === 'AUTH_REQUIRED' ? '重新登录' : '重试回复' }}</button>
              <!-- AI 只提供选择建议，用户确认后才改变岗位 -->
              <div v-if="confirmAct" class="confirm">
                <span class="confirm__text">{{ confirmAct.label }}</span>
                <button class="confirm__yes" @click="runConfirm">确认</button>
                <button class="confirm__no" @click="confirmAct = null">继续聊聊</button>
              </div>

              <!-- ① 开始 -->
              <button v-if="stage === 'GREET' && !confirmAct" class="primary-btn" @click="beginJobPick">
                浏览岗位
              </button>

              <!-- ② 选岗位 -->
              <template v-else-if="stage === 'PICK_JOB' && !confirmAct">
                <button v-if="!showJobOptions" type="button" class="primary-btn" @click="showJobOptions = true">浏览岗位，也可以继续聊</button>
                <template v-else>
                  <button v-if="preferredJob && isReadyJob(preferredJob)" class="primary-btn" @click="pickJob(preferredJob)">使用默认岗位：{{ preferredJob.title }}</button>
                  <p v-if="jobsLoading" role="status">正在加载岗位…</p>
                  <p v-else-if="jobsError" role="alert">{{ jobsError }} <button type="button" class="link-btn" @click="fetchJobs">重新加载岗位</button></p>
                  <div class="chips">
                    <button
                      v-for="f in families"
                      :key="f.code"
                      class="chip"
                      :class="{ 'chip--on': activeFamily === f.code }"
                      @click="toggleFamily(f.code)"
                    >
                      {{ f.name }}
                    </button>
                  </div>
                  <p v-if="!jobsLoading && !jobsError && !visibleJobs.length" role="status">这个方向暂时没有可展示岗位，可以继续聊聊或看看其他方向。</p>
                  <div class="jobs">
                    <button
                      v-for="j in visibleJobs"
                      :key="j.id"
                      class="job"
                      :class="{ 'job--off': !isReadyJob(j) }"
                      :disabled="!isReadyJob(j)"
                      @click="pickJob(j)"
                    >
                      <i class="job__bar" :style="{ background: j.accentColor }" />
                      <span class="job__name">{{ j.title }}</span>
                      <span v-if="!isReadyJob(j)" class="job__soon">敬请期待</span>
                    </button>
                  </div>

                </template>
              </template>

              <!-- ③ 传简历 -->
              <template v-else-if="stage === 'UPLOAD'">
                <div
                  class="drop"
                  :class="{ 'drop--over': isDragging, 'drop--busy': uploading }"
                  @click="fileInput?.click()"
                  @dragover.prevent="isDragging = true"
                  @dragleave.prevent="isDragging = false"
                  @drop.prevent="onDrop"
                >
                  <input
                    ref="fileInput"
                    type="file"
                    accept=".pdf,.doc,.docx"
                    hidden
                    @change="onFileChange"
                  />
                  <template v-if="uploading">
                    <p class="drop__title">正在解析简历…</p>
                  </template>
                  <template v-else>
                    <p class="drop__title">把简历拖到这里</p>
                    <p class="drop__hint">或点击选择文件 · 支持 PDF / Word · 不超过 10MB</p>
                  </template>
                </div>
                <p v-if="uploadError" class="ctl__err">{{ uploadError }}</p>
                <button
                  v-if="savedResume"
                  type="button"
                  class="saved-pick"
                  :disabled="uploading || busy"
                  @click="useSavedResume"
                >
                  <span class="saved-pick__text">
                    <strong>用上次那份简历</strong>
                    <small>
                      <template v-if="savedResume.filename">{{ savedResume.filename }} · </template>
                      已识别 {{ savedResume.skills.length }} 个技能
                    </small>
                  </span>
                  <span class="saved-pick__go">使用 →</span>
                </button>
                <button class="link-btn" @click="openOnlineResume">
                  没有简历文件？在线填一份 →
                </button>
              </template>

              <!-- ④ 收尾 -->
              <template v-else-if="stage === 'DONE'">
                <div class="skills">
                  <p class="skills__cap">
                    识别到 {{ extractedSkills.length }} 个技能
                    <span class="skills__note">（下一步可以增删）</span>
                  </p>
                  <span v-for="s in extractedSkills" :key="s" class="skill">{{ s }}</span>
                </div>
                <button class="primary-btn" @click="goTargets">查看简历分析 →</button>
              </template>
            </div>
          </template>
        </div>

        <!-- 输入区：用户可以自己打字，AI 在回复时顺带判断他这句话想干什么 -->
        <form class="composer" @submit.prevent="send">
          <input
            v-model="draft"
            class="composer__input"
            type="text"
            maxlength="500"
            :disabled="typing || busy"
            :placeholder="composerHint"
          />
          <button class="composer__send" type="submit" :disabled="typing || busy || !draft.trim()">
            发送
          </button>
        </form>
      </section>

    </div>
  </AppLayout>
</template>

<script setup>
import PrepModeSwitch from '../components/PrepModeSwitch.vue'
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import { getJobList, getCareerProfile, getAiStatus, uploadResumeFile, getMyResume, getResumeFileProfile } from '../api'
import { postSse } from '../utils/sse'
import { useUserStore } from '../store/user'
import { buildPrepHistory, prepUnavailableReply, prepFailureReason } from '../utils/aiPrepConversation'
import { JOB_FAMILIES, mapJobFromBackend, isReadyJob } from '../utils/jobs'

/* ------------------------------------------------------------------ */
/*  预置话术：AI 不可用时的兜底                                        */
/* ------------------------------------------------------------------ */
// 关键设计：AI 真流式和预置文案走的是同一条管线（都只是往 queue 里塞字符串，
// 由打字机匀速吐字），所以「降级」不需要第二套渲染代码。

const FALLBACK_HINT = {
  AI_DISABLED: '未配置 AI 密钥',
  AI_ERROR: 'AI 调用失败',
  BUSY: '当前排队较多',
  TIMEOUT: 'AI 响应超时',
  AUTH_REQUIRED: '登录已过期',
  NETWORK_ERROR: '无法连接对话服务',
  SERVER_ERROR: '对话服务出错',
  INVALID_REQUEST: '对话请求校验失败',
}

const ALLOWED_EXT = ['pdf', 'doc', 'docx']
const MAX_SIZE = 10 * 1024 * 1024
/** 首字节看门狗：fetch 在服务端长时间不吐字节时不会自己报错 */
const FIRST_BYTE_MS = 20000

/* ------------------------------------------------------------------ */
/*  State                                                              */
/* ------------------------------------------------------------------ */
const router = useRouter()
const route = useRoute()

const scroller = ref(null)
const fileInput = ref(null)

const stage = ref('GREET')
const messages = ref([])
const aiMode = ref('RULE')
const fallbackReason = ref('')
const failedTurn = ref(null)
const typingId = ref(0)
const busy = ref(true)
const controlsVisible = ref(false)

const draft = ref('')
/** 用户明确表达选择后的确认入口；null 表示继续聊天。 */
const confirmAct = ref(null)

const jobs = ref([])
const jobsLoading = ref(false)
const jobsError = ref('')
const activeFamily = ref('')
const showJobOptions = ref(false)
const preferredJob = ref(null)
const selectedJob = ref(null)
const extractedSkills = ref([])
const uploadedName = ref('')
const uploadError = ref('')
const uploading = ref(false)
const isDragging = ref(false)
/** 上次保存的简历；有值时上传区多给一条「直接用它」的路 */
const savedResume = ref(null)

let seq = 0
let currentMsg = null
let queue = ''
let timer = null
let turnSettled = false
let gotDelta = false
let abortCtrl = null
let watchdog = null
/** 本轮 SSE 回传的意图，只在当前轮内有效 */
let pendingAct = null
/** 意图只执行一次，避免同样一帧 act 被 tick 和 settle 各跑一遍 */
let actApplied = false
/**
 * 轮次令牌。一轮可能被下一轮「就地打断」——用户操作可能在上一轮还没收尾时
 * 直接 startTurn 的，此时旧连接 abort 后 await 才返回，它带着的那次 settle 会把
 * 新轮的气泡清掉、看门狗干掉。所有回调都先比对这个令牌，过期的一律不认。
 */
let turnId = 0

const families = JOB_FAMILIES

/** 正在逐字吐字。这期间禁掉输入框，免得用户插话把上一句截断 */
const typing = computed(() => typingId.value !== 0)

const COMPOSER_HINT = {
  GREET: '聊聊你的经历，或问问感兴趣的岗位…',
  PICK_JOB: '可以比较岗位、聊聊顾虑，不用急着选…',
  UPLOAD: '还可以继续聊岗位，或说说简历准备的疑问…',
  DONE: '还有什么想问的？',
}
const composerHint = computed(() => COMPOSER_HINT[stage.value] || '说点什么…')

/* ------------------------------------------------------------------ */
/*  打字机                                                             */
/* ------------------------------------------------------------------ */
function ensureTimer() {
  if (!timer) timer = setInterval(tick, 18)
}

function stopTimer() {
  clearInterval(timer)
  timer = null
}

function tick() {
  if (!queue) {
    stopTimer()
    // 分片间隙仍在回复中；只有整轮结束且文字显示完才解锁输入。
    if (turnSettled) revealControls()
    return
  }
  // 积压越多吐得越快：降级时是一整句灌进来，按 1 字/18ms 会打三秒
  const n = queue.length > 80 ? 4 : queue.length > 30 ? 2 : 1
  currentMsg.text += queue.slice(0, n)
  queue = queue.slice(n)
  scrollToEnd()
}

function pushText(text) {
  if (!text) return
  // 头之后若空了一行，正文开头会带上换行——气泡里第一行就空了，去掉
  if (!currentMsg?.text && !queue) text = text.replace(/^[\r\n]+/, '')
  if (!text) return
  queue += text
  ensureTimer()
}

function scrollToEnd() {
  nextTick(() => {
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/* ------------------------------------------------------------------ */
/*  一轮话术                                                           */
/* ------------------------------------------------------------------ */
const modeHint = computed(() => {
  if (aiMode.value === 'AI') return '话术由 AI 实时生成'
  const why = FALLBACK_HINT[fallbackReason.value] || 'AI 未启用'
  return `演示模式：${why}`
})

/**
 * 可面试岗位清单，喂给模型当「口语 ↔ code」的对照表。
 * 只给 READY_JOBS 里的：模型照着这份清单说话，说出来的 code 后端还会再核一遍，
 * 不在清单里的一律丢掉——它编一个岗位出来，前端拿去查题库就是一场空。
 */
function jobOptions() {
  return jobs.value
    .filter(isReadyJob)
    .slice(0, 30)
    .map(j => ({ code: j.code, name: j.title, family: j.family }))
}

function buildPayload(s, userText) {
  const payload = { stage: s, history: buildPrepHistory(messages.value, userText) }
  if (userText) payload.message = userText
  if (selectedJob.value) payload.jobName = selectedJob.value.title
  if (s === 'GREET' || s === 'PICK_JOB') payload.families = families.map(f => f.name)
  const opts = jobOptions()
  if (opts.length) payload.jobOptions = opts
  if (extractedSkills.value.length) payload.skills = extractedSkills.value.slice(0, 20)
  if (uploadedName.value) payload.resumeFilename = uploadedName.value
  return payload
}

/** 等待首个模型响应或后续分片；meta 只说明连接成功，不算模型已回复。 */
function refreshWatchdog(mine) {
  clearTimeout(watchdog)
  watchdog = setTimeout(() => {
    if (mine !== turnId) return
    fallbackReason.value = 'TIMEOUT'
    settle(mine)
  }, FIRST_BYTE_MS)
}

async function startTurn(s, userText) {
  const mine = ++turnId
  // 先把上一轮彻底收干净：可能还挂着打字机、看门狗和一个没关的 SSE 连接。
  // 令牌换掉之后，旧连接后续的回调会全部认作过期，见 turnId 的注释。
  stopTimer()
  clearTimeout(watchdog)
  watchdog = null
  if (abortCtrl) {
    try { abortCtrl.abort() } catch { /* 已结束 */ }
  }

  busy.value = true
  controlsVisible.value = false
  confirmAct.value = null
  pendingAct = null
  actApplied = false
  gotDelta = false
  turnSettled = false
  // 上一轮失败过不代表这一轮也会失败，后端每轮都会重新发 meta
  fallbackReason.value = ''
  failedTurn.value = null
  queue = ''

  const payload = buildPayload(s, userText)
  const msg = reactive({ id: ++seq, role: 'ai', text: '', userText, stage: s })
  messages.value.push(msg)
  currentMsg = msg
  typingId.value = msg.id
  scrollToEnd()

  abortCtrl = new AbortController()
  refreshWatchdog(mine)

  try {
    await postSse('/ai/prep/stream', payload, {
      meta: (d) => { if (mine === turnId && d.mode) aiMode.value = d.mode },
      delta: (d) => {
        if (mine !== turnId) return
        refreshWatchdog(mine)
        gotDelta = true
        pushText(d.t)
      },
      // 意图比正文先到（首行就回传），但这里只记下来不执行：
      // 得等这句话说完、用户看完了再动界面，否则状态跳走了话还在往外吐
      act: (d) => { if (mine === turnId) { refreshWatchdog(mine); pendingAct = d } },
      fallback: (d) => { if (mine === turnId && d.reason) fallbackReason.value = d.reason },
      done: () => settle(mine),
    }, { signal: abortCtrl.signal })
    // 流正常结束但没等到 done（被中途截断）也要收尾
    settle(mine)
  } catch (error) {
    if (mine === turnId && !turnSettled && error.name !== 'AbortError') fallbackReason.value = prepFailureReason(error)
    settle(mine)
  }
}

/**
 * 收尾。三条路（done / 出错 / 超时）都会汇到这里，靠 turnSettled 保证只执行一次。
 *
 * @param mine 发起方的轮次令牌，对不上说明这一轮已经被下一轮顶掉了，什么都不做
 */
function settle(mine) {
  if (mine !== turnId || turnSettled) return
  turnSettled = true
  clearTimeout(watchdog)
  watchdog = null
  if (abortCtrl) {
    try { abortCtrl.abort() } catch { /* 已结束 */ }
  }

  // 一个字都没收到 → 后端未启用 AI / 调用失败 / 超时，统一换成预置文案。
  // 说了一半又失败（fallback 晚于 delta）也算：半句话后面接另一句会拼出怪句，整段丢弃重打。
  if (!gotDelta || fallbackReason.value) {
    aiMode.value = 'RULE'
    pendingAct = null
    if (!fallbackReason.value) fallbackReason.value = 'AI_ERROR'
    failedTurn.value = { stage: currentMsg?.stage || stage.value, message: currentMsg?.userText }
    if (currentMsg) currentMsg.failed = true
    queue = prepUnavailableReply({ stage: currentMsg?.stage || stage.value, message: currentMsg?.userText, reason: fallbackReason.value })
    if (currentMsg) currentMsg.text = ''
    ensureTimer()
  }
  // 必须先把 busy 放掉再决定要不要露控件：revealControls 可能顺手推进一步，
  // 而 advance 会被 busy 挡住；startTurn 随后会把 busy 重新置 true，
  // 顺序反了就会留下「流还在跑但界面已解锁」的窗口。
  busy.value = false
  if (!queue) revealControls()
}

/** 话术吐完：露出本轮的控件，再执行 AI 听出来的意图 */
function revealControls() {
  typingId.value = 0
  controlsVisible.value = true
  // 控件是挂上去了，但吐字结束那一刻的滚动位置是按旧内容算的——整块控件（拖拽区
  // 一百多像素）会把新入口顶到折叠线以下。补一次滚动，让它们真的露出来。
  scrollToEnd()
  applyAct()
}

/** AI 只提出操作建议；无论置信度多高，都等用户确认。 */
function applyAct() {
  if (actApplied) return
  actApplied = true
  const plan = actPlan(pendingAct)
  if (!plan) return

  if (fallbackReason.value) return
  confirmAct.value = { label: plan.label, run: plan.run }
}

/**
 * 把一个意图翻译成界面上的一次操作。返回 null = 这条意图在当前阶段无事可做。
 *
 * 大多数消息都会落到 null：AI 是在聊天，不是每条消息都要改状态。
 * 已选岗位后仍可讨论换岗，只有确认操作才改变岗位。
 */
function actPlan(act) {
  if (!act) return null
  const job = findReadyJob(act.jobCode)

  switch (act.intent) {
    case 'PICK_JOB':
      if (job && job.code !== selectedJob.value?.code) {
        return {
          label: `使用「${job.title}」作为目标岗位？`,
          run: () => selectJob(job, `确认选择 ${job.title}`),
        }
      }
      if (!job && act.family && (stage.value === 'GREET' || stage.value === 'PICK_JOB')) {
        return {
          label: `浏览「${act.family}」方向的岗位？`,
          run: () => goFamily(act.family, `看看${act.family}方向`),
        }
      }
      return null

    case 'NO_RESUME':
      return stage.value === 'UPLOAD'
        ? { label: '打开在线简历？', run: () => openOnlineResume() }
        : null

    default:
      // QUESTION / OTHER：正常聊天，不动界面
      return null
  }
}

function runConfirm() {
  const c = confirmAct.value
  confirmAct.value = null
  if (c?.run) c.run()
}

function findReadyJob(code) {
  if (!code) return null
  const hit = jobs.value.find(j => j.code === code)
  return hit && isReadyJob(hit) ? hit : null
}

/** 推进到下一阶段：先落一条用户气泡，再让 AI 说话 */
function advance(next, userText) {
  if (busy.value) return
  if (userText) messages.value.push(reactive({ id: ++seq, role: 'user', text: userText }))
  stage.value = next
  scrollToEnd()
  startTurn(next)
}

/* ------------------------------------------------------------------ */
/*  各阶段的用户动作                                                   */
/* ------------------------------------------------------------------ */
function beginJobPick() {
  showJobOptions.value = true
  advance('PICK_JOB', '我想先看看有哪些岗位')
}

function toggleFamily(code) {
  if (busy.value || typing.value) return
  activeFamily.value = code
  showJobOptions.value = true
  const name = families.find(f => f.code === code)?.name || code
  const text = `我想先了解${name}方向，请介绍工作内容和能力要求，暂时不确定具体岗位。`
  messages.value.push(reactive({ id: ++seq, role: 'user', text }))
  startTurn(stage.value, text)
}

function retryReply() {
  if (busy.value || typing.value || !failedTurn.value) return
  if (fallbackReason.value === 'AUTH_REQUIRED') {
    useUserStore().logout()
    router.push('/login')
    return
  }
  const turn = failedTurn.value
  startTurn(turn.stage, turn.message)
}

function pickJob(job) {
  // 手点的卡片：用户没说话，补一条气泡记下他选了什么
  selectJob(job, job.title)
}

/** @param bubbleText 空串表示不落用户气泡（AI 听出来的选择，用户的原话已经在上面了） */
function selectJob(job, bubbleText) {
  if (!isReadyJob(job) || busy.value) return
  selectedJob.value = job
  advance(uploadedName.value ? 'DONE' : 'UPLOAD', bubbleText)
}

/** 只识别出方向时用：先把岗位族筛出来，再让用户在卡片里收敛到具体岗位 */
function goFamily(familyName, bubbleText) {
  showJobOptions.value = true
  const hit = families.find(f => f.name === familyName || f.code === familyName)
  if (hit) activeFamily.value = hit.code
  advance('PICK_JOB', bubbleText)
}

/* ------------------------------------------------------------------ */
/*  用户打字                                                           */
/* ------------------------------------------------------------------ */
function send() {
  const text = draft.value.trim()
  if (!text || busy.value || typing.value) return
  draft.value = ''
  showJobOptions.value = false
  messages.value.push(reactive({ id: ++seq, role: 'user', text }))
  scrollToEnd()
  // 停在当前阶段发一句：这句话算不算推进流程，交给 AI 判断，前端只认它给出的意图
  startTurn(stage.value, text)
}

/* ------------------------------------------------------------------ */
/*  在线简历                                                           */
/* ------------------------------------------------------------------ */
function openOnlineResume() {
  router.push({ path: '/resume/builder', query: {
    job: selectedJob.value?.code || undefined,
    jobId: selectedJob.value?.id || undefined,
  } })
}

/* ------------------------------------------------------------------ */
/*  已有简历：上次传过就不必再传一遍                                    */
/* ------------------------------------------------------------------ */
/** skills 在 /resume/mine 里是 JSON 数组字符串，在 file-profile 里已经是数组 */
function parseResumeSkills(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(String).filter(Boolean)
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
  } catch { /* 不是 JSON 就按分隔符切 */ }
  return String(raw).split(/[,，、\n]/).map((s) => s.trim()).filter(Boolean)
}

/**
 * 技能以 /resume/mine 为准 —— 它才是出题真正读的那份。file-profile 只用来补文件名：
 * 在线简历没有 resume_file 行，那个接口会返回空画像（见记忆 ai-prep-online-resume）。
 * 两个都失败就当作没有简历，上传区少一条路而已，不影响流程。
 */
async function loadSavedResume() {
  const [mine, profile] = await Promise.allSettled([getMyResume(), getResumeFileProfile()])
  const skills = parseResumeSkills(mine.status === 'fulfilled' ? mine.value?.skills : null)
  if (!skills.length) { savedResume.value = null; return }
  savedResume.value = {
    skills,
    filename: profile.status === 'fulfilled' ? (profile.value?.filename || '') : '',
  }
}

/** 与在线简历同一条推进路径：出题读的是 resume 表的 skills，两条路同构 */
function useSavedResume() {
  if (!savedResume.value) return
  extractedSkills.value = [...savedResume.value.skills]
  uploadedName.value = savedResume.value.filename || '已保存的简历'
  uploadError.value = ''
  advance('DONE', '用上次那份简历')
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = '' // 允许重复选择同一个文件
  if (file) handleFile(file)
}

function onDrop(e) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

async function handleFile(file) {
  if (uploading.value || busy.value) return
  uploadError.value = ''
  // 前端先拦一道，省一次注定失败的往返
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_EXT.includes(ext)) {
    uploadError.value = '仅支持 PDF、Word 格式的简历'
    return
  }
  if (file.size > MAX_SIZE) {
    uploadError.value = '文件大小不能超过 10MB'
    return
  }

  uploading.value = true
  const name = file.name
  try {
    // 与手动录入页调的是同一个接口、同一个字段名，解析结果完全一致
    const data = await uploadResumeFile(file)
    const skills = []
    for (const s of (data?.skills || [])) {
      if (s && !skills.includes(s)) skills.push(s)
    }
    for (const k of (data?.keywords || [])) {
      if (k && !skills.includes(k)) skills.push(k)
    }
    extractedSkills.value = skills
    uploadedName.value = name
    uploading.value = false
    // 失败时停在 UPLOAD 不推进——这一步不能糊弄过去
    advance('DONE', name)
  } catch (e) {
    uploading.value = false
    uploadError.value = e?.message || '简历解析失败，请重试'
  }
}

function goTargets() {
  if (!selectedJob.value) return
  // step=2 是简历评价；训练目标现为第 4 步。
  router.push({
    path: '/jobs',
    query: { job: selectedJob.value.code, step: '2', from: 'ai' },
  })
}


/* ------------------------------------------------------------------ */
/*  数据加载                                                           */
/* ------------------------------------------------------------------ */
const jobsByFamily = computed(() => {
  const map = {}
  jobs.value.forEach(j => {
    if (!map[j.family]) map[j.family] = []
    map[j.family].push(j)
  })
  return map
})

const visibleJobs = computed(() => {
  const pool = activeFamily.value
    ? (jobsByFamily.value[activeFamily.value] || [])
    : jobs.value
  // 可面试的排前面，与手动录入页一致
  return [...pool].sort(
    (a, b) => (isReadyJob(a) ? 0 : 1) - (isReadyJob(b) ? 0 : 1)
  )
})

/** GREET 的气泡吐完后才挂控件，避免用户还没读完就能点 */
const controlsFor = computed(() => {
  if (!controlsVisible.value) return 0
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'ai') return messages.value[i].id
  }
  return 0
})

async function fetchJobs() {
  jobsLoading.value = true
  jobsError.value = ''
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
    const profile = await getCareerProfile().catch(() => null)
    preferredJob.value = jobs.value.find(job => job.code === route.query.job) || jobs.value.find(job => job.id === profile?.targetJobId) || null
    if (preferredJob.value && !selectedJob.value) activeFamily.value = preferredJob.value.family
  } catch (e) {
    jobsError.value = '岗位暂未加载成功，请重试。'
    console.error('Failed to load jobs:', e)
  } finally {
    jobsLoading.value = false
  }
}

async function fetchStatus() {
  // 只为让徽标在首帧就有值；SSE 的 meta 事件随后会覆盖它
  try {
    const data = await getAiStatus()
    if (data?.mode) aiMode.value = data.mode
  } catch (e) {
    console.warn('Failed to load AI status:', e)
  }
}

onMounted(() => {
  fetchJobs()
  fetchStatus()
  loadSavedResume()
  startTurn('GREET')
})

onUnmounted(() => {
  // 离开路由后定时器还在改状态会报警告，也会白白占着 abort
  stopTimer()
  clearTimeout(watchdog)
  if (abortCtrl) {
    try { abortCtrl.abort() } catch { /* 已结束 */ }
  }
})
</script>

<style scoped>
.ai-page {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) var(--space-6) var(--space-10);
}

.chat {
  display: flex;
  flex-direction: column;
  height: min(calc(100vh - 200px), 780px);
  min-height: 520px;
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

/* ---------------- 顶栏 ---------------- */
.chat__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--neutral-200);
  flex-shrink: 0;
}

.chat__who {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.chat__brand {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 700;
  flex-shrink: 0;
}

.chat__title {
  display: block;
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--neutral-900);
  line-height: 1.3;
}

.chat__mode {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--text-xs);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

.chat__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
}

.chat__dot--live {
  background: var(--accent-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
}

.chat__dot--demo {
  background: var(--neutral-400);
}

.ghost-btn {
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--neutral-600);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
  flex-shrink: 0;
}

.ghost-btn:hover {
  border-color: var(--accent-500);
  color: var(--accent-600);
  background: var(--accent-50);
}

/* ---------------- 对话区 ---------------- */
.chat__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6) var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  scroll-behavior: smooth;
}

.row {
  display: flex;
  gap: var(--space-3);
  align-items: flex-start;
}

.row--user {
  justify-content: flex-end;
}

.row__avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs);
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble {
  max-width: min(560px, 78%);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  font-size: var(--text-sm);
  line-height: 1.75;
  font-family: var(--font-body);
  word-break: break-word;
}

.bubble--ai {
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  color: var(--neutral-800);
  border-top-left-radius: var(--radius-xs);
}

.bubble--user {
  background: var(--accent-50);
  color: var(--accent-800);
  border-top-right-radius: var(--radius-xs);
}

.bubble__text {
  white-space: pre-wrap;
}

/* 等待首字节时的三点，避免气泡是个空壳 */
.dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 20px;
}

.dots i {
  width: 5px;
  height: 5px;
  border-radius: var(--radius-full);
  background: var(--neutral-400);
  animation: ai-pulse 1.2s infinite ease-in-out;
}

.dots i:nth-child(2) { animation-delay: 0.15s; }
.dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes ai-pulse {
  0%, 60%, 100% { opacity: 0.4; }
  30%           { opacity: 1; }
}

.caret {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent-500);
  animation: ai-blink 0.9s steps(1) infinite;
}

@keyframes ai-blink {
  0%, 50%   { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ---------------- 气泡内的交互控件 ---------------- */
.ctl {
  margin-left: calc(30px + var(--space-3));
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  align-items: flex-start;
  animation: ctl-in var(--duration-normal) var(--ease-out-quart);
}

@keyframes ctl-in {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.ctl__err {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-error);
  font-family: var(--font-body);
}

.primary-btn {
  padding: var(--space-3) var(--space-6);
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent-500);
  color: #fff;
  font-size: var(--text-sm);
  font-weight: 600;
  font-family: var(--font-body);
  cursor: pointer;
  box-shadow: none;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.primary-btn:hover {
  background: var(--accent-600);
  transform: translateY(-1px);
}

/* 岗位族 chips */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.chip {
  padding: 5px var(--space-3);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-full);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-xs);
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.chip:hover {
  border-color: var(--accent-400);
  color: var(--accent-600);
}

.chip--on {
  border-color: var(--accent-500);
  background: var(--accent-50);
  color: var(--accent-700);
  font-weight: 600;
}

/* 紧凑岗位卡片：聊天气泡里放不下整页的 3 列网格 */
.jobs {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: var(--space-2);
  width: 100%;
  max-width: 620px;
}

.job {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.job:hover:not(:disabled) {
  border-color: var(--accent-400);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.job__bar {
  width: 3px;
  height: 16px;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

.job__name {
  font-size: var(--text-sm);
  color: var(--neutral-800);
  font-family: var(--font-body);
  line-height: 1.4;
}

.job--off {
  opacity: 0.5;
  cursor: not-allowed;
}

.job__soon {
  margin-left: auto;
  font-size: 10px;
  color: var(--neutral-400);
  white-space: nowrap;
}

/* 拖拽上传区（直接嵌在气泡下） */
.drop {
  width: 100%;
  max-width: 460px;
  padding: var(--space-6) var(--space-5);
  border: 2px dashed var(--neutral-300);
  border-radius: var(--radius-lg);
  background: var(--neutral-50);
  text-align: center;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.drop:hover,
.drop--over {
  border-color: var(--accent-500);
  background: var(--accent-50);
}

.drop--busy {
  cursor: wait;
  opacity: 0.75;
}

.drop__title {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-700);
  font-family: var(--font-body);
}

.drop__hint {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

/* 已有简历：与拖拽区并列的第二条路。比下面的文字链重、比上传区轻 */
.saved-pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  width: 100%;
  max-width: 460px;
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--neutral-50);
  font-family: var(--font-body);
  text-align: left;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.saved-pick:hover:not(:disabled) {
  border-color: var(--accent-500);
  background: var(--accent-50);
}

.saved-pick:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.saved-pick__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.saved-pick__text strong {
  color: var(--neutral-800);
  font-size: var(--text-sm);
  font-weight: 600;
}

.saved-pick__text small {
  overflow: hidden;
  color: var(--neutral-500);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.saved-pick__go {
  flex-shrink: 0;
  color: var(--accent-600);
  font-size: var(--text-xs);
  font-weight: 600;
}

/* 解析结果 */
.skills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
  max-width: 620px;
}

.skills__cap {
  width: 100%;
  margin: 0;
  font-size: var(--text-xs);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

.skills__note {
  color: var(--neutral-400);
}

.skill {
  padding: 4px var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-size: var(--text-xs);
  font-family: var(--font-body);
}

/* ---------------- 意图确认条 ---------------- */
.confirm {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  max-width: 620px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--accent-200);
  border-radius: var(--radius-md);
  background: var(--accent-50);
}

.confirm__text {
  font-size: var(--text-xs);
  color: var(--accent-700);
  font-family: var(--font-body);
}

.confirm__yes,
.confirm__no {
  padding: 3px var(--space-3);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast);
}

.confirm__yes {
  border: none;
  background: var(--accent-500);
  color: #fff;
  font-weight: 600;
}

.confirm__yes:hover {
  background: var(--accent-600);
}

.confirm__no {
  border: 1px solid var(--accent-200);
  background: transparent;
  color: var(--accent-700);
}

.confirm__no:hover {
  border-color: var(--accent-400);
}

/* 次要入口（在线简历），比主按钮低一级 */
.link-btn {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--accent-600);
  font-size: var(--text-xs);
  font-family: var(--font-body);
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.link-btn:hover {
  color: var(--accent-700);
}

/* ---------------- 输入区 ---------------- */
.composer {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--neutral-200);
  flex-shrink: 0;
}

.composer__input {
  flex: 1;
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-full);
  background: var(--neutral-50);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  color: var(--neutral-800);
  transition: border-color var(--duration-fast), background var(--duration-fast);
}

.composer__input:focus {
  outline: none;
  border-color: var(--accent-500);
  background: var(--surface-elevated);
}

.composer__input:disabled {
  color: var(--neutral-400);
  cursor: not-allowed;
}

.composer__send {
  flex-shrink: 0;
  padding: 0 var(--space-5);
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent-500);
  color: #fff;
  font-size: var(--text-sm);
  font-weight: 600;
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.composer__send:hover:not(:disabled) {
  background: var(--accent-600);
}

.composer__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ---------------- 窄屏 ---------------- */
@media (max-width: 640px) {
  .ai-page {
    padding: var(--space-4) var(--space-3) var(--space-6);
  }

  .chat {
    height: calc(100vh - 160px);
  }

  .bubble {
    max-width: 86%;
  }

  .ctl {
    margin-left: 0;
  }

  .jobs {
    grid-template-columns: 1fr;
  }

  .composer {
    padding: var(--space-3) var(--space-3);
  }
}
</style>
