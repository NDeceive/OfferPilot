<template>
  <div class="mchat">
    <!-- 顶栏：返回 + 身份 + 切手动录入 -->
    <header class="mchat__bar">
      <button type="button" class="mchat__back" aria-label="返回" @click="goBack">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <div class="mchat__who">
        <strong>AI 面试教练</strong>
        <span class="mchat__mode" :title="modeHint">
          <i :class="aiMode === 'AI' ? 'is-live' : 'is-demo'" />
          {{ aiMode === 'AI' ? 'AI 实时生成' : '演示模式' }}
        </span>
      </div>
      <button type="button" class="mchat__manual" @click="goManual">手动录入</button>
    </header>

    <!-- 对话区 -->
    <div ref="scroller" class="mchat__body">
      <template v-for="m in messages" :key="m.id">
        <div class="mrow" :class="`mrow--${m.role}`">
          <span v-if="m.role === 'ai'" class="mrow__avatar">面</span>
          <div class="mbubble" :class="`mbubble--${m.role}`">
            <span v-if="m.role === 'ai' && !m.text" class="mdots"><i /><i /><i /></span>
            <span v-else>{{ m.text }}</span>
            <i v-if="m.id === typingId && m.text" class="mcaret" />
          </div>
        </div>

        <!-- 控件贴着最后一条 AI 气泡出现，且只在话术吐完之后 -->
        <div v-if="controlsFor === m.id" class="mctl">
          <!-- AI 听懂了但没把握时的确认条，不擅自改状态 -->
          <div v-if="confirmAct" class="mconfirm">
            <span>你刚说的是这个意思吗？{{ confirmAct.label }}</span>
            <div>
              <button type="button" class="mconfirm__yes" @click="runConfirm">是的</button>
              <button type="button" class="mconfirm__no" @click="confirmAct = null">不是</button>
            </div>
          </div>

          <!-- ① 开始 -->
          <button v-if="stage === 'GREET'" type="button" class="mbig-btn" @click="beginJobPick">
            开始
          </button>

          <!-- ② 选岗位 -->
          <template v-else-if="stage === 'PICK_JOB'">
            <div class="mobile-chip-row mchat__families">
              <button
                v-for="f in families"
                :key="f.code"
                type="button"
                :class="{ active: activeFamily === f.code }"
                @click="toggleFamily(f.code)"
              >
                {{ f.name }}
              </button>
            </div>
            <div class="mchat__jobs">
              <button
                v-for="j in visibleJobs"
                :key="j.id"
                type="button"
                class="mjob"
                :class="{ 'is-off': !isReadyJob(j) }"
                :disabled="!isReadyJob(j)"
                @click="pickJob(j)"
              >
                <i class="mjob__bar" :style="{ background: j.accentColor }" />
                <span class="mjob__name">{{ j.title }}</span>
                <span v-if="!isReadyJob(j)" class="mjob__soon">敬请期待</span>
                <span v-else class="mjob__go">→</span>
              </button>
            </div>
          </template>

          <!-- ③ 传简历（移动端没有拖拽，纯点击） -->
          <template v-else-if="stage === 'UPLOAD'">
            <div
              class="mdrop"
              :class="{ 'is-busy': uploading }"
              role="button"
              tabindex="0"
              @click="fileInput?.click()"
              @keydown.enter.prevent="fileInput?.click()"
              @keydown.space.prevent="fileInput?.click()"
            >
              <input
                ref="fileInput"
                type="file"
                accept=".pdf,.doc,.docx"
                hidden
                @change="onFileChange"
              />
              <svg class="mdrop__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <path d="M14 2v6h6"/><path d="M12 18v-6"/><path d="m9 15 3-3 3 3"/>
              </svg>
              <template v-if="uploading">
                <p class="mdrop__title">正在解析简历…</p>
                <p class="mdrop__hint">通常只需要几秒</p>
              </template>
              <template v-else>
                <p class="mdrop__title">点击上传简历</p>
                <p class="mdrop__hint">支持 PDF / Word · 不超过 10MB</p>
              </template>
            </div>
            <p v-if="uploadError" class="merr">{{ uploadError }}</p>
            <button
              v-if="savedResume"
              type="button"
              class="msaved"
              :disabled="uploading || busy"
              @click="useSavedResume"
            >
              <span class="msaved__text">
                <strong>用上次那份简历</strong>
                <small>
                  <template v-if="savedResume.filename">{{ savedResume.filename }} · </template>
                  已识别 {{ savedResume.skills.length }} 个技能
                </small>
              </span>
              <span class="msaved__go">使用 →</span>
            </button>
            <button type="button" class="mlink-btn" @click="openOnlineResume">
              没有简历文件？在线填一份 →
            </button>
          </template>

          <!-- ④ 收尾 -->
          <template v-else-if="stage === 'DONE'">
            <div class="mskills">
              <p class="mskills__cap">
                识别到 {{ extractedSkills.length }} 个技能
                <span>（下一步可以增删）</span>
              </p>
              <span v-for="s in extractedSkills" :key="s" class="mskill">{{ s }}</span>
            </div>
            <button type="button" class="mbig-btn" @click="goTargets">设置训练目标 →</button>
          </template>
        </div>
      </template>
    </div>

    <!-- 输入条 -->
    <form class="mchat__composer" @submit.prevent="send">
      <input
        v-model="draft"
        type="text"
        :placeholder="composerHint"
        maxlength="500"
        :disabled="typing || busy"
      />
      <button type="submit" :disabled="typing || busy || !draft.trim()">发送</button>
    </form>

    <!-- 内部是 Teleport to="body"，能跳出 .mchat 的 overflow:hidden -->
    <OnlineResumeDialog v-model="onlineOpen" @saved="onOnlineResumeSaved" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import OnlineResumeDialog from '../../components/resume/OnlineResumeDialog.vue'
import { getJobList, getAiStatus, uploadResumeFile, getMyResume, getResumeFileProfile } from '../../api'
import { postSse } from '../../utils/sse'
import { JOB_FAMILIES, mapJobFromBackend, isReadyJob } from '../../utils/jobs'

/* ------------------------------------------------------------------ *
 *  这一页的逻辑与桌面版 views/AiPrep.vue 完全一致，只换了外壳。
 *  状态机 / 打字机 / SSE 五事件 / turnId 令牌 / 8s 看门狗 / 意图时序
 *  都是与模板无关的纯逻辑，故意保持逐行同构 —— 改这里之前先看那边。
 * ------------------------------------------------------------------ */

/* 预置话术：AI 不可用时的兜底。与真流式走同一条管线（都只是往 queue 塞字符串）。 */
const SCRIPT = {
  GREET: '你好，我是你的面试教练。接下来两步：先挑一个目标岗位，再传一份简历，然后就能开始模拟面试了。',
  PICK_JOB: '先选一个你想投的方向吧，选完我再给你说说准备重点。',
  UPLOAD: '点下面的框选一份简历就行，我只用它提取技能标签，不会外传。',
  DONE: '简历收到。接下来设置 5 个训练目标，就能开始面试了。',
}

const FALLBACK_HINT = {
  AI_DISABLED: '未配置 AI 密钥',
  AI_ERROR: 'AI 调用失败',
  BUSY: '当前排队较多',
  TIMEOUT: 'AI 响应超时',
}

const ALLOWED_EXT = ['pdf', 'doc', 'docx']
const MAX_SIZE = 10 * 1024 * 1024
/** 首字节看门狗：fetch 在服务端长时间不吐字节时不会自己报错 */
const FIRST_BYTE_MS = 8000

const router = useRouter()

const scroller = ref(null)
const fileInput = ref(null)

const stage = ref('GREET')
const messages = ref([])
const aiMode = ref('RULE')
const fallbackReason = ref('')
const typingId = ref(0)
const busy = ref(true)
const controlsVisible = ref(false)

const draft = ref('')
const onlineOpen = ref(false)
/** 低置信意图的确认条；null 表示不显示 */
const confirmAct = ref(null)

const jobs = ref([])
const activeFamily = ref('')
const selectedJob = ref(null)
const extractedSkills = ref([])
const uploadedName = ref('')
const uploadError = ref('')
const uploading = ref(false)
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
 * 轮次令牌。一轮可能被下一轮「就地打断」——意图自动跳转就是在上一轮还没收尾时
 * 直接 startTurn 的，此时旧连接 abort 后 await 才返回，它带着的那次 settle 会把
 * 新轮的气泡清掉、看门狗干掉。所有回调都先比对这个令牌，过期的一律不认。
 */
let turnId = 0

const families = JOB_FAMILIES

/** 正在逐字吐字。这期间禁掉输入框，免得用户插话把上一句截断 */
const typing = computed(() => typingId.value !== 0)

const COMPOSER_HINT = {
  GREET: '也可以直接打字，比如「我想面 Java 后端」',
  PICK_JOB: '说出方向就行，比如「算法吧」',
  UPLOAD: '没有简历就说「我没有简历」',
  DONE: '还有什么想问的？',
}
const composerHint = computed(() => COMPOSER_HINT[stage.value] || '说点什么…')

const modeHint = computed(() => {
  if (aiMode.value === 'AI') return '话术由 AI 实时生成'
  return `演示模式：${FALLBACK_HINT[fallbackReason.value] || 'AI 未启用'}`
})

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
    typingId.value = 0
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
/**
 * 可面试岗位清单，喂给模型当「口语 ↔ code」的对照表。
 * 只给 READY_JOBS 里的：模型照着这份清单说话，说出来的 code 后端还会再核一遍。
 */
function jobOptions() {
  return jobs.value
    .filter(isReadyJob)
    .slice(0, 30)
    .map(j => ({ code: j.code, name: j.title, family: j.family }))
}

function buildPayload(s, userText) {
  const payload = { stage: s }
  if (userText) payload.message = userText
  if (selectedJob.value) payload.jobName = selectedJob.value.title
  if (s === 'GREET' || s === 'PICK_JOB') payload.families = families.map(f => f.name)
  const opts = jobOptions()
  if (opts.length) payload.jobOptions = opts
  if (extractedSkills.value.length) payload.skills = extractedSkills.value.slice(0, 20)
  if (uploadedName.value) payload.resumeFilename = uploadedName.value
  return payload
}

async function startTurn(s, userText) {
  const mine = ++turnId
  // 先把上一轮彻底收干净：可能还挂着打字机、看门狗和一个没关的 SSE 连接。
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
  queue = ''

  const msg = reactive({ id: ++seq, role: 'ai', text: '' })
  messages.value.push(msg)
  currentMsg = msg
  typingId.value = msg.id
  scrollToEnd()

  abortCtrl = new AbortController()
  watchdog = setTimeout(() => {
    if (mine !== turnId) return
    fallbackReason.value = 'TIMEOUT'
    try { abortCtrl.abort() } catch { /* 已结束 */ }
    settle(mine)
  }, FIRST_BYTE_MS)

  try {
    await postSse('/ai/prep/stream', buildPayload(s, userText), {
      meta: (d) => { if (mine === turnId && d.mode) aiMode.value = d.mode },
      delta: (d) => {
        if (mine !== turnId) return
        gotDelta = true
        pushText(d.t)
      },
      // 意图比正文先到，但这里只记下来不执行：得等这句话说完、用户看完了再动界面
      act: (d) => { if (mine === turnId) pendingAct = d },
      fallback: (d) => { if (mine === turnId && d.reason) fallbackReason.value = d.reason },
      done: () => settle(mine),
    }, { signal: abortCtrl.signal })
    // 流正常结束但没等到 done（被中途截断）也要收尾
    settle(mine)
  } catch {
    settle(mine)
  }
}

/**
 * 收尾。三条路（done / 出错 / 超时）都会汇到这里，靠 turnSettled 保证只执行一次。
 *
 * @param mine 发起方的轮次令牌，对不上说明这一轮已被下一轮顶掉，什么都不做
 */
function settle(mine) {
  if (mine !== turnId || turnSettled) return
  turnSettled = true
  clearTimeout(watchdog)
  watchdog = null
  if (abortCtrl) {
    try { abortCtrl.abort() } catch { /* 已结束 */ }
  }

  // 一个字都没收到 → 换成预置文案。说了一半又失败也算：半句话后面接另一句会拼出怪句。
  if (!gotDelta || fallbackReason.value) {
    aiMode.value = 'RULE'
    queue = SCRIPT[stage.value] || SCRIPT.GREET
    if (currentMsg) currentMsg.text = ''
    ensureTimer()
  }
  // 必须先把 busy 放掉再决定要不要露控件：revealControls 可能顺手推进一步，
  // 而 advance 会被 busy 挡住。顺序反了会留下「流还在跑但界面已解锁」的窗口。
  busy.value = false
  if (!queue) revealControls()
}

/** 话术吐完：露出本轮的控件，再执行 AI 听出来的意图 */
function revealControls() {
  typingId.value = 0
  controlsVisible.value = true
  // 控件挂上去时不会自己滚动（吐字结束时算的滚动位置是旧内容的），手机屏更矮，
  // 整块上传区会被顶到折叠线以下——补一次，让新入口真的看得见。
  scrollToEnd()
  applyAct()
}

/**
 * 执行意图。高置信直接跳，低置信出一条确认栏。
 * 后端的 confidence 只在「模型给出了清单里真实存在的 jobCode」时才是 HIGH。
 */
function applyAct() {
  if (actApplied) return
  actApplied = true
  const plan = actPlan(pendingAct)
  if (!plan) return

  // 降级时这句回复是预置文案，压根没读用户那句话，这时候再自动跳会很突兀
  if (pendingAct.confidence === 'HIGH' && !fallbackReason.value) {
    plan.run(false)
  } else {
    confirmAct.value = { label: plan.label, run: plan.run }
  }
}

/**
 * 把一个意图翻译成界面上的一次操作。返回 null = 这条意图在当前阶段无事可做。
 * 每个分支都先卡当前 stage，防止模型在错误的阶段把流程推回去。
 */
function actPlan(act) {
  if (!act) return null
  const job = findReadyJob(act.jobCode)

  switch (act.intent) {
    case 'PICK_JOB':
      if (job && stage.value === 'GREET') {
        return {
          label: `帮你选「${job.title}」，然后传简历？`,
          run: (bubble) => selectJob(job, bubble ? job.title : ''),
        }
      }
      if (job && stage.value === 'PICK_JOB') {
        return {
          label: `选「${job.title}」？`,
          run: (bubble) => selectJob(job, bubble ? job.title : ''),
        }
      }
      // 只说得出方向（后端因此给不出 HIGH）：切到对应岗位族，让卡片替他收敛
      if (!job && act.family && stage.value === 'GREET') {
        return {
          label: `想看「${act.family}」方向的岗位？`,
          run: (bubble) => goFamily(act.family, bubble ? act.family : ''),
        }
      }
      return null

    case 'READY':
    case 'CONFIRM':
      if (stage.value === 'GREET') {
        return { label: '开始挑岗位？', run: (bubble) => advance('PICK_JOB', bubble ? '好' : '') }
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
  if (c?.run) c.run(true)
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
  advance('PICK_JOB', '好')
}

function toggleFamily(code) {
  activeFamily.value = activeFamily.value === code ? '' : code
}

function pickJob(job) {
  // 手点的卡片：用户没说话，补一条气泡记下他选了什么
  selectJob(job, job.title)
}

/** @param bubbleText 空串表示不落用户气泡（AI 听出来的选择，用户的原话已经在上面了） */
function selectJob(job, bubbleText) {
  if (!isReadyJob(job) || busy.value) return
  selectedJob.value = job
  advance('UPLOAD', bubbleText)
}

/** 只识别出方向时用：先把岗位族筛出来，再让用户在卡片里收敛到具体岗位 */
function goFamily(familyName, bubbleText) {
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
  messages.value.push(reactive({ id: ++seq, role: 'user', text }))
  scrollToEnd()
  // 停在当前阶段发一句：算不算推进流程交给 AI 判断，前端只认它给出的意图
  startTurn(stage.value, text)
}

/* ------------------------------------------------------------------ */
/*  在线简历                                                           */
/* ------------------------------------------------------------------ */
function openOnlineResume() {
  onlineOpen.value = true
}

function onOnlineResumeSaved(payload) {
  extractedSkills.value = payload?.skills || []
  uploadedName.value = '在线简历'
  uploadError.value = ''
  // 没有文件也照常推进：出题读的是 resume 表的 skills，在线简历走的是同一个 POST /api/resume
  advance('DONE', '用在线简历')
}

/* ------------------------------------------------------------------ */
/*  已有简历：上次传过就不必再传一遍（与桌面版同构）                    */
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
 * 在线简历没有 resume_file 行，那个接口会返回空画像。两个都失败就当作没有简历。
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
  // step=2 是「训练目标」；from=ai 让向导知道前两步已经由对话完成
  router.push({
    path: '/jobs',
    query: { job: selectedJob.value.code, step: '2', from: 'ai' },
  })
}

function goManual() {
  router.push('/jobs')
}

/** 聊天是全屏页，没挂底部导航，得自己给条退路 */
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/home')
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

/** 气泡吐完后才挂控件，避免用户还没读完就能点 */
const controlsFor = computed(() => {
  if (!controlsVisible.value) return 0
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'ai') return messages.value[i].id
  }
  return 0
})

async function fetchJobs() {
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
  } catch (e) {
    console.error('Failed to load jobs:', e)
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
/* 全屏三段式：顶栏 / 可滚动对话区 / 输入条。
   高度用 dvh——移动浏览器地址栏收放和软键盘弹起时 vh 不会跟着变，输入条会被顶出屏幕。 */
.mchat {
  display: flex;
  height: 100vh;
  height: 100dvh;
  flex-direction: column;
  overflow: hidden;
  color: var(--m-text);
  background: var(--m-bg);
}

/* ---- 顶栏 ---- */
.mchat__bar {
  display: flex;
  flex: 0 0 auto;
  min-height: 58px;
  padding: max(8px, env(safe-area-inset-top)) 12px 8px;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, .94);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--m-border);
}
.mchat__back {
  display: grid;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  color: var(--m-text);
  background: transparent;
  border: 0;
  border-radius: 50%;
  place-items: center;
}
.mchat__back svg { width: 22px; height: 22px; }
.mchat__back:active { background: var(--m-primary-soft); }

.mchat__who { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 1px; }
.mchat__who strong { font-size: 15px; line-height: 1.2; }
.mchat__mode { display: inline-flex; align-items: center; gap: 5px; color: var(--m-text-tertiary); font-size: 11px; }
.mchat__mode i { display: block; width: 6px; height: 6px; border-radius: 50%; }
.mchat__mode i.is-live { background: var(--m-primary); }
.mchat__mode i.is-demo { background: var(--m-accent); }

.mchat__manual {
  flex: 0 0 auto;
  min-height: 36px;
  padding: 0 12px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

/* ---- 对话区 ---- */
.mchat__body {
  flex: 1 1 auto;
  padding: 16px 16px 12px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.mrow { display: flex; margin-bottom: 12px; align-items: flex-start; gap: 8px; }
.mrow--user { justify-content: flex-end; }

.mrow__avatar {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  color: #fff;
  place-items: center;
  background: var(--m-primary);
  border-radius: 50%;
  font-size: 13px;
  font-weight: 800;
}

.mbubble {
  max-width: 84%;
  padding: 11px 14px;
  border-radius: 16px;
  font-size: 14.5px;
  line-height: 1.62;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.mbubble--ai { color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-top-left-radius: 6px; }
.mbubble--user { color: #fff; background: var(--m-primary); border-top-right-radius: 6px; }

.mdots { display: inline-flex; gap: 4px; padding: 3px 0; }
.mdots i { display: block; width: 6px; height: 6px; background: #9fb3a8; border-radius: 50%; animation: mdot 1.1s ease-in-out infinite; }
.mdots i:nth-child(2) { animation-delay: .15s; }
.mdots i:nth-child(3) { animation-delay: .3s; }
@keyframes mdot { 0%, 60%, 100% { opacity: .35; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-3px); } }

.mcaret { display: inline-block; width: 2px; height: 15px; margin-left: 2px; vertical-align: -2px; background: var(--m-primary); animation: mcaret .9s step-end infinite; }
@keyframes mcaret { 50% { opacity: 0; } }

/* ---- 控件 ---- */
.mctl { display: flex; margin: 0 0 18px 38px; flex-direction: column; align-items: flex-start; gap: 10px; }

.mbig-btn {
  min-height: 46px;
  padding: 0 24px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 14.5px;
  font-weight: 700;
  box-shadow: 0 8px 18px rgba(23, 75, 57, .18);
}
.mbig-btn:active { transform: scale(.98); background: var(--m-primary-dark); }

.mconfirm {
  display: flex;
  width: 100%;
  padding: 12px 14px;
  flex-direction: column;
  gap: 10px;
  background: var(--m-accent-soft);
  border-radius: var(--m-radius-card);
  font-size: 13px;
  line-height: 1.5;
}
.mconfirm > div { display: flex; gap: 8px; }
.mconfirm button { min-height: 36px; padding: 0 18px; border: 0; border-radius: 999px; font-size: 13px; font-weight: 700; }
.mconfirm__yes { color: #fff; background: var(--m-primary); }
.mconfirm__no { color: var(--m-text-secondary); background: rgba(255,255,255,.8); }

/* 岗位族横滑条：复用 mobile.css 的 .mobile-chip-row，只补个下边距 */
.mchat__families { width: 100%; margin-bottom: 2px; }

.mchat__jobs { display: flex; width: 100%; flex-direction: column; gap: 8px; }
.mjob {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 58px;
  padding: 12px 14px 12px 18px;
  align-items: center;
  gap: 10px;
  overflow: hidden;
  color: var(--m-text);
  text-align: left;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
}
.mjob__bar { position: absolute; top: 10px; bottom: 10px; left: 0; width: 3px; border-radius: 0 3px 3px 0; }
.mjob__name { flex: 1; min-width: 0; font-size: 14.5px; font-weight: 700; overflow-wrap: anywhere; }
.mjob__go { color: var(--m-primary); font-size: 17px; }
.mjob:active { transform: scale(.99); background: var(--m-surface-soft); }
.mjob.is-off { color: var(--m-text-tertiary); background: #f4f4f1; }
.mjob.is-off .mjob__bar { opacity: .35; }
.mjob__soon { flex: 0 0 auto; padding: 3px 8px; color: var(--m-text-tertiary); background: var(--m-border); border-radius: 999px; font-size: 11px; }

/* 上传区：移动端没有拖拽，做成整块可点 */
.mdrop {
  display: flex;
  width: 100%;
  min-height: 168px;
  padding: 24px 20px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  text-align: center;
  background: var(--m-surface);
  border: 1.5px dashed #b9d2c3;
  border-radius: var(--m-radius-card);
  transition: border-color var(--m-motion), background var(--m-motion);
}
.mdrop:active { background: var(--m-surface-soft); border-color: var(--m-primary); }
.mdrop.is-busy { border-style: solid; border-color: var(--m-primary); }
.mdrop__icon { width: 38px; height: 38px; margin-bottom: 4px; color: var(--m-primary); }
.mdrop__title { font-size: 14.5px; font-weight: 700; }
.mdrop__hint { color: var(--m-text-tertiary); font-size: 12px; }

.merr { width: 100%; padding: 9px 12px; color: #7c4315; background: var(--m-accent-soft); border-radius: 9px; font-size: 12px; line-height: 1.5; }
.mlink-btn { min-height: 40px; padding: 0; color: var(--m-primary); background: transparent; border: 0; font-size: 13px; font-weight: 700; }

/* 已有简历：与上传区并列的第二条路。比文字链重、比上传区轻 */
.msaved {
  display: flex;
  width: 100%;
  min-height: 56px;
  margin-top: 12px;
  padding: 12px 14px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  transition: border-color var(--m-motion), background var(--m-motion);
}
.msaved:active:not(:disabled) { background: var(--m-surface-soft); border-color: var(--m-primary); }
.msaved:disabled { opacity: .55; }
.msaved__text { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.msaved__text strong { color: var(--m-text); font-size: 14px; font-weight: 700; }
.msaved__text small {
  overflow: hidden;
  color: var(--m-text-tertiary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msaved__go { flex-shrink: 0; color: var(--m-primary); font-size: 13px; font-weight: 700; }

.mskills {
  display: flex;
  width: 100%;
  padding: 14px;
  flex-wrap: wrap;
  gap: 6px;
  background: var(--m-primary-soft);
  border-radius: var(--m-radius-card);
}
.mskills__cap { width: 100%; margin-bottom: 4px; color: var(--m-text-secondary); font-size: 12.5px; }
.mskills__cap span { color: var(--m-text-tertiary); }
.mskill { padding: 4px 10px; color: var(--m-primary-dark); background: rgba(255,255,255,.8); border-radius: 999px; font-size: 12px; }

/* ---- 输入条 ---- */
.mchat__composer {
  display: flex;
  flex: 0 0 auto;
  padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, .96);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--m-border);
}
.mchat__composer input {
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 16px;
  color: var(--m-text);
  background: #f2f4f0;
  border: 1px solid transparent;
  border-radius: 999px;
  outline: 0;
  font-size: 14px;
}
.mchat__composer input:focus { background: #fff; border-color: var(--m-primary); }
.mchat__composer input::placeholder { color: #7c8882; }
.mchat__composer input:disabled { color: var(--m-text-tertiary); }
.mchat__composer button {
  flex: 0 0 auto;
  min-height: 44px;
  padding: 0 20px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
}
.mchat__composer button:disabled { color: #fff; background: #b6c9bd; }

@media (prefers-reduced-motion: reduce) {
  .mdots i, .mcaret { animation: none; }
}
</style>
