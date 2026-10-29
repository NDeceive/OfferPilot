/**
 * 离线演示模式的假后端（axios 自定义 adapter）。
 *
 * 为什么拦在 adapter 而不是响应拦截器：
 * adapter 是 axios 分发链上「真正去发请求」的那一步，在这里返回假响应，请求
 * 根本不出网，也完全不抛错——`request.js` 的响应拦截器照常解信封、照常判 401，
 * 一行都不用改。拦在响应拦截器就得先造一个网络错误再去伪造 response 对象，
 * 假错误会在 console 里刷一片红，排查真问题时很干扰。
 *
 * 两条硬约束（都是前端实际读的路径，写错的表现是「页面某块空白」）：
 *   1. 每个响应都要包 `{code:200, message:'操作成功', data}` 信封；
 *   2. 面试链的 `nextAction` 只能是 FOLLOWUP / NEXT / FINISHABLE。
 *      `FINISHED` 是前端 skipQuestion 分支专用的值，那条路会跳
 *      `/history/[object Object]`——mock 永远不能吐它。
 */
import {
  MOCK_AI_STATUS,
  MOCK_CAREER,
  MOCK_IMPROVEMENT_PATH,
  MOCK_JOBS,
  MOCK_MEETING_ADVICE,
  MOCK_MEETINGS_HOSTED,
  MOCK_MEETINGS_JOINED,
  MOCK_MODULES,
  MOCK_OVERVIEW,
  MOCK_QUESTIONS,
  MOCK_RECORDS,
  MOCK_REPORT,
  MOCK_RESUME,
  MOCK_TEACHING_CLASSES,
  MOCK_TEACHING_MEMBERS,
  MOCK_TEACHING_MESSAGES,
  MOCK_TEACHING_TASKS,
  MOCK_TRANSCRIPT,
  MOCK_USER,
} from './demoData.js'

/* ------------------------------------------------------------------ */
/*  信封与响应                                                         */
/* ------------------------------------------------------------------ */
/**
 * 演示模式签发的 token。
 *
 * 特意用一个一眼能认出来的固定串，而不是随便糊一个假 JWT：退出演示模式时
 * 要靠它判断「手里这个登录态是假的」，好把人送回登录页重新登录真后端。
 */
export const DEMO_TOKEN = 'demo-offline-token'

/* ------------------------------------------------------------------ */
/*  演示登录角色                                                       */
/* ------------------------------------------------------------------ */
/**
 * 登录页选的「学生端/教师端/企业端」tab 决定假登录返回什么角色——
 * 演示模式要能在手机上把三种端都走一遍，这是唯一的入口。
 *
 * 顺手存一份到 localStorage 当默认值（真正的选择每次进离线演示时都会重设，
 * 这里只是让模块重载后到重设之前有个稳定的值）。
 */
const DEMO_ROLE_KEY = 'offerpilot.demoRole'
const DEMO_ROLES = ['STUDENT', 'TEACHER', 'ENTERPRISE']

const readDemoRole = () => {
  try {
    const value = String(localStorage.getItem(DEMO_ROLE_KEY) || '').toUpperCase()
    return DEMO_ROLES.includes(value) ? value : 'STUDENT'
  } catch {
    return 'STUDENT'
  }
}
let demoLoginRole = readDemoRole()

/** 由登录页在提交前调用（demoState.active 时才有意义） */
export function setDemoLoginRole(role) {
  const next = String(role || '').toUpperCase()
  demoLoginRole = DEMO_ROLES.includes(next) ? next : 'STUDENT'
  try {
    localStorage.setItem(DEMO_ROLE_KEY, demoLoginRole)
  } catch {
    /* 持久化失败只影响刷新后的 /user/me，不影响本次演示 */
  }
}

const DEMO_ACCOUNTS = {
  STUDENT: { id: 3, username: 'student', nickname: '林晓' },
  TEACHER: { id: 2, username: 'teacher', nickname: '王老师' },
  ENTERPRISE: { id: 7, username: 'enterprise', nickname: '陈经理' },
}
const demoAccount = () => DEMO_ACCOUNTS[demoLoginRole]

const OK = (data) => ({ code: 200, message: '操作成功', data })
const ERR = (message, code = 500) => ({ code, message, data: null })

function respond(config, body) {
  return {
    data: body,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
    request: { demo: true },
  }
}

/** 统一的假延迟——太快没有任何加载态，太快反而显得假；太慢演示又难受 */
const LATENCY = 180
const LATENCY_SLOW = 420
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/* ------------------------------------------------------------------ */
/*  面试会话：一个按 sessionId 索引的内存状态机                        */
/* ------------------------------------------------------------------ */
const sessions = new Map()
const deletedSessions = new Set()
let sessionSeq = 9100
let reportSeq = 6100

/** 报告「生成中」的时长。故意留出几秒，前端那段轮询才是真的在轮询 */
const REPORT_DELAY_MS = 2600

const EVAL_TEMPLATE = [
  { name: '表达能力', color: '#10b981' },
  { name: '逻辑性', color: '#3b82f6' },
  { name: '技术深度', color: '#8b5cf6' },
]

/** 让评分条每轮小幅变化，看着像在实时评估，而不是三条僵住的柱子 */
function evalItems(seed) {
  return EVAL_TEMPLATE.map((item, i) => ({ ...item, value: 62 + ((seed * 7 + i * 11) % 29) }))
}

/** 从真实题库里挑，按 jobId 错开起手题，换个岗位演示时题目不会一模一样 */
function pickQuestions(jobId) {
  const offset = Math.abs(Number(jobId) || 1) % MOCK_QUESTIONS.length
  return Array.from({ length: MOCK_QUESTIONS.length }, (_, i) => MOCK_QUESTIONS[(offset + i) % MOCK_QUESTIONS.length])
}

/** QuestionView。type / difficulty 真实后端没有，补上纯粹为了面试页标签好看 */
function questionView(question, roundNo) {
  return {
    id: question.id,
    content: question.content,
    abilityTag: question.abilityTag,
    roundNo,
    questionType: 'MAIN',
    type: 'MAIN',
    difficulty: question.difficulty,
  }
}

function createSession(config) {
  const jobId = Number(config?.jobId) || 1
  const job = MOCK_JOBS.find((item) => item.id === jobId) || MOCK_JOBS[0]
  const sid = ++sessionSeq
  const session = {
    sid,
    job,
    durationSeconds: Math.min(7200, Math.max(300, Number(config?.durationSeconds) || 1800)),
    questions: pickQuestions(jobId),
    /** 当前在第几题（0 基） */
    idx: 0,
    /** 当前题是否已经追问过 */
    followedUp: false,
    /**
     * 「已经推进到下一题、但客户端可能还没取走」。
     *
     * 前端有两条取题路径：正常答题后会【再发一次】GET /{sid}/next，跳过本题则
     * 直接读 submitAnswer 响应里带的那道题。同一个 NEXT 响应要同时伺候这两条路，
     * 靠这个标记区分：/next 只认领一次，认领过就清掉，避免正常路径被多推一题。
     */
    pendingDelivery: false,
    /** 原始流水，GET /{sid}/messages 直接吐它 */
    log: [],
    startedAt: Date.now(),
    finishedAt: 0,
    reportId: 0,
  }
  session.log.push({
    role: 'INTERVIEWER', msgType: 'MAIN',
    content: session.questions[0].content,
    abilityTag: session.questions[0].abilityTag,
    questionId: session.questions[0].id,
  })
  sessions.set(sid, session)
  return session
}

function requireSession(sid) {
  const session = sessions.get(Number(sid))
  if (!session) {
    throw Object.assign(new Error('会话不存在（可能刷新过页面），请重新发起一场面试'), { demoCode: 404 })
  }
  return session
}

/**
 * 隔一题追问一次：节奏有变化，演示时也不至于要走十几轮才结束。
 *
 * 空答案不追问——前端「跳过本题」发过来的就是空字符串，人家都跳过了还追一句
 * 「能举个例子吗」很荒唐。
 */
function wantsFollowup(session, answer) {
  return !session.followedUp
    && !!String(answer).trim()
    && session.idx % 2 === 0
    && !!session.questions[session.idx].followup
}

/** 记录一段候选人回答 */
function logAnswer(session, content) {
  session.log.push({
    role: 'CANDIDATE',
    msgType: 'ANSWER',
    content: String(content ?? ''),
    abilityTag: session.questions[session.idx].abilityTag,
    questionId: session.questions[session.idx].id,
  })
}

/** 推进到下一题；没有下一题就返回 null */
function advance(session) {
  if (session.idx + 1 >= session.questions.length) return null
  session.idx += 1
  session.followedUp = false
  session.pendingDelivery = true
  const question = session.questions[session.idx]
  session.log.push({
    role: 'INTERVIEWER', msgType: 'MAIN',
    content: question.content, abilityTag: question.abilityTag, questionId: question.id,
  })
  return question
}

/* ------------------------------------------------------------------ */
/*  报告                                                               */
/* ------------------------------------------------------------------ */
/** 会话结束后拿真实会话信息套一份报告，别让报告里的岗位名和刚面的对不上 */
function buildReport(session) {
  return {
    ...MOCK_REPORT,
    reportId: session.reportId,
    sessionId: session.sid,
    jobId: session.job.id,
    jobName: session.job.name,
    durationSeconds: session.durationSeconds,
    actualDurationSeconds: Math.max(60, Math.round((session.finishedAt - session.startedAt) / 1000)),
    startTime: new Date(session.startedAt).toISOString().slice(0, 19),
    endTime: new Date(session.finishedAt).toISOString().slice(0, 19),
  }
}

/** 会话已经被刷新清掉时，用题库现攒一段流水，别让报告页空着 */
function cannedTranscript() {
  const log = []
  MOCK_QUESTIONS.slice(0, 3).forEach((question, index) => {
    log.push({
      role: 'INTERVIEWER', msgType: 'MAIN',
      content: question.content, abilityTag: question.abilityTag, questionId: question.id,
    })
    log.push({
      role: 'CANDIDATE', msgType: 'ANSWER',
      content: `第 ${index + 1} 题的回答要点：先给结论，再补原理，最后落到项目里的一次实践。`,
      abilityTag: question.abilityTag, questionId: question.id,
    })
    if (index === 0) {
      log.push({
        role: 'INTERVIEWER', msgType: 'FOLLOWUP',
        content: question.followup, abilityTag: question.abilityTag, questionId: question.id,
      })
      log.push({
        role: 'CANDIDATE', msgType: 'ANSWER',
        content: '这个点我实际踩过坑：两种方案都试过，差别主要在数据量上来之后的响应时间，当时做过一版压测对比。',
        abilityTag: question.abilityTag, questionId: question.id,
      })
    }
  })
  return log
}

/* ------------------------------------------------------------------ */
/*  记录 / 首页：把刚跑完的那场面试并进去，演示才连得上                */
/* ------------------------------------------------------------------ */
function liveRecords() {
  const extra = []
  for (const session of sessions.values()) {
    if (!session.finishedAt || deletedSessions.has(session.sid)) continue
    const spent = Math.max(60, Math.round((session.finishedAt - session.startedAt) / 1000))
    extra.push({
      sessionId: session.sid,
      jobId: session.job.id,
      jobName: session.job.name,
      difficulty: 2,
      status: 'FINISHED',
      totalScore: MOCK_REPORT.totalScore,
      reportId: session.reportId,
      startTime: new Date(session.startedAt).toISOString().slice(0, 19),
      endTime: new Date(session.finishedAt).toISOString().slice(0, 19),
      durationSeconds: session.durationSeconds,
      actualDurationSeconds: Math.min(session.durationSeconds, spent),
    })
  }
  // 新的排前面，跟后端按开始时间倒序的行为一致
  return [...extra.reverse(), ...MOCK_RECORDS.filter((r) => !deletedSessions.has(r.sessionId))]
}

function overview() {
  const recent = liveRecords().slice(0, 3).map((record) => ({
    sessionId: record.sessionId,
    reportId: record.reportId,
    jobId: record.jobId,
    jobName: record.jobName,
    status: record.status,
    difficulty: record.difficulty,
    score: record.totalScore,
    durationSeconds: record.actualDurationSeconds,
    startTime: record.startTime,
  }))
  return {
    ...MOCK_OVERVIEW,
    summary: { ...MOCK_OVERVIEW.summary, completedCount: MOCK_OVERVIEW.summary.completedCount + sessions.size },
    recentInterviews: recent.length ? recent : MOCK_OVERVIEW.recentInterviews,
  }
}

/* ------------------------------------------------------------------ */
/*  教学管理：内存假数据 + 轻量状态机                                  */
/* ------------------------------------------------------------------ */
/**
 * 教师端的班/成员/任务/消息。刷新页面即回到种子状态（和面试会话同一个约定），
 * 但单次演示里的「建班 → 审核 → 发布任务 → 结束任务」能真的把状态推下去，
 * 页面上的数字和徽标跟着变，才像在操作真系统。
 */
const teachingStore = {
  classes: MOCK_TEACHING_CLASSES.map((c) => ({ ...c })),
  members: MOCK_TEACHING_MEMBERS.map((m) => ({ ...m })),
  tasks: MOCK_TEACHING_TASKS.map((t) => ({
    ...t,
    questions: [...(t.questions || [])],
    assignments: (t.assignments || []).map((a) => ({ ...a, attempts: (a.attempts || []).map((p) => ({ ...p })) })),
  })),
  messages: MOCK_TEACHING_MESSAGES.map((m) => ({ ...m })),
  /** reportId → 点评数组（写点评后当场追加，报告页刷新可见） */
  reviews: {},
  seq: { class: 2200, task: 7200, assignment: 8200, review: 9600 },
}

const TEACHER_NAME = '王老师'
/** 邀请码照后端来：UUID 去掉横杠后的 12 位十六进制 */
const INVITE_ALPHABET = '0123456789ABCDEF'

function randomCode(length, alphabet) {
  let out = ''
  for (let i = 0; i < length; i += 1) out += alphabet[Math.floor(Math.random() * alphabet.length)]
  return out
}

/** 后端存的是「本地时间」短格式，演示数据的当前时间也对齐这个形状 */
function nowStamp(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
}

function findClass(id) {
  const cls = teachingStore.classes.find((c) => c.id === Number(id))
  if (!cls) throw Object.assign(new Error('教学班不存在'), { demoCode: 404 })
  return cls
}

function findTask(id) {
  const task = teachingStore.tasks.find((t) => t.id === Number(id))
  if (!task) throw Object.assign(new Error('训练任务不存在'), { demoCode: 404 })
  return task
}

/** 演示数据里的时间戳都是「本地时间」短格式，直接 new Date 按本地时区解析 */
const stampMs = (value) => {
  const time = new Date(String(value || '').replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : 0
}

/** 生命周期和真实后端一样现算：发布、截止、结束、待开始会让标签跟着变 */
function lifecycleOf(task) {
  if (!task.publishedAt) return 'DRAFT'
  if (task.endedAt) return 'ENDED'
  if (stampMs(task.deadline) <= Date.now()) return 'CLOSED'
  return stampMs(task.startTime) > Date.now() ? 'SCHEDULED' : 'ACTIVE'
}

/** 列表/详情共用的班级视图：人数从成员表现算，改成员状态后列表数字立刻跟着变 */
function classView(cls) {
  const rows = teachingStore.members.filter((m) => m.classId === cls.id)
  return {
    ...cls,
    teacherName: TEACHER_NAME,
    joinedCount: rows.filter((m) => m.state === 'JOINED').length,
    pendingCount: rows.filter((m) => m.state === 'PENDING').length,
  }
}

function taskView(task, includeAssignments) {
  const { assignments = [], ...rest } = task
  const cls = teachingStore.classes.find((c) => c.id === task.classId)
  const view = {
    ...rest,
    // 班级名与归档标记取自班级当前状态（改名/归档后任务页跟着变，和后端 JOIN 一致）
    className: cls ? cls.name : task.className,
    archived: cls ? cls.archived : !!task.archived,
    startTime: task.startTime || task.publishedAt || task.deadline,
    lifecycle: lifecycleOf(task),
  }
  // 和真实后端一致：只有已发布任务才带 assignments 字段
  if (!includeAssignments || !task.publishedAt) return view
  return {
    ...view,
    assignments: assignments.map((a) => ({ ...a, attempts: (a.attempts || []).map((p) => ({ ...p })) })),
  }
}

function newAssignment(member) {
  return {
    id: (teachingStore.seq.assignment += 1),
    studentId: member.studentId,
    name: member.name,
    username: member.username,
    exempt: false,
    completionStatus: 'NOT_STARTED',
    validCount: 0,
    attempts: [],
  }
}

/** 按「全班 / 指定学生」分配任务，和后端 allocate 同一套规则：只发给已加入成员 */
function allocate(task, body) {
  const joined = teachingStore.members.filter((m) => m.classId === task.classId && m.state === 'JOINED')
  const all = body.recipientMode === 'ALL' || !Array.isArray(body.studentIds) || !body.studentIds.length
  const selected = all ? null : new Set(body.studentIds.map(Number))
  task.assignments = joined.filter((m) => !selected || selected.has(m.studentId)).map(newAssignment)
  if (!task.assignments.length) {
    throw Object.assign(new Error('请先加入至少一名正常学生'), { demoCode: 400 })
  }
}

/** 发布：置发布时间；草稿没分配过就按当前已加入名单补上 */
function publishTask(task) {
  if (task.publishedAt) return
  if (stampMs(task.deadline) <= Date.now()) {
    throw Object.assign(new Error('任务已过截止时间，请重新创建'), { demoCode: 400 })
  }
  task.publishedAt = nowStamp()
  if (!task.assignments.length) {
    task.assignments = teachingStore.members
      .filter((m) => m.classId === task.classId && m.state === 'JOINED')
      .map(newAssignment)
  }
  if (!task.assignments.length) {
    throw Object.assign(new Error('请先加入至少一名正常学生'), { demoCode: 400 })
  }
}

/** /teaching/students：已加入成员按班级展开（和后端 people() 同一个形状） */
function peopleView() {
  const rows = []
  for (const cls of [...teachingStore.classes].sort((a, b) => a.id - b.id)) {
    for (const m of teachingStore.members) {
      if (m.classId !== cls.id || m.state !== 'JOINED') continue
      rows.push({ id: m.studentId, name: m.name, username: m.username, classId: cls.id, className: cls.name })
    }
  }
  return rows
}

/** 统计口径照抄后端 summary()：减免不算任务数，待点评 = 有报告且还没有点评 */
function summaryOf() {
  const studentTotal = new Set(teachingStore.members.filter((m) => m.state === 'JOINED').map((m) => m.studentId)).size
  let assignmentTotal = 0
  let completedTotal = 0
  let validAttemptTotal = 0
  let pendingReviewTotal = 0
  for (const task of teachingStore.tasks) {
    if (!task.publishedAt) continue
    for (const a of task.assignments) {
      if (!a.exempt) assignmentTotal += 1
      if (a.completionStatus === 'COMPLETED') completedTotal += 1
      for (const attempt of a.attempts) {
        if (attempt.state === 'READY') validAttemptTotal += 1
        if (attempt.reportId && !(teachingStore.reviews[attempt.reportId] || []).length) pendingReviewTotal += 1
      }
    }
  }
  return {
    studentTotal,
    taskTotal: teachingStore.tasks.length,
    assignmentTotal,
    completedTotal,
    validAttemptTotal,
    pendingReviewTotal,
  }
}

const reviewsFor = (reportId) => teachingStore.reviews[Number(reportId)] || []

/** 报告页要的三件套：报告本体 + 原始问答 + 点评。问答现攒一段并补 id/roundNo 供渲染 */
function reportBundle(reportId) {
  let roundNo = 0
  const messages = cannedTranscript().map((entry, index) => {
    if (entry.msgType === 'MAIN') roundNo += 1
    return { id: Number(reportId) * 100 + index, roundNo, ...entry }
  })
  return {
    report: { ...MOCK_REPORT, reportId: Number(reportId) },
    messages,
    reviews: reviewsFor(reportId),
  }
}

/* ------------------------------------------------------------------ */
/*  会议：内存假数据                                                   */
/* ------------------------------------------------------------------ */
const meetingStore = {
  hosted: MOCK_MEETINGS_HOSTED.map((m) => ({ ...m })),
  joined: MOCK_MEETINGS_JOINED.map((m) => ({ ...m })),
  advice: Object.fromEntries(
    Object.entries(MOCK_MEETING_ADVICE).map(([id, list]) => [id, list.map((a) => ({ ...a }))]),
  ),
  seq: { meeting: 380, advice: 9700 },
}

const MEETING_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const meetingCode = () => randomCode(8, MEETING_ALPHABET)
const meetingCodeDisplay = (code) => `${code.slice(0, 4)}-${code.slice(4)}`

/** 和后端 normalizeCode 一致：去横杠、转大写，用户大小写/带不带横杠都能进 */
const normalizeCode = (raw) => String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '')

function findMeeting(id) {
  return [...meetingStore.hosted, ...meetingStore.joined].find((m) => m.id === Number(id)) || null
}

function eachMeeting(id, fn) {
  for (const list of [meetingStore.hosted, meetingStore.joined]) {
    for (const m of list) if (m.id === Number(id)) fn(m)
  }
}

function findByCode(raw) {
  const code = normalizeCode(raw)
  if (code.length !== 8) return null
  return [...meetingStore.hosted, ...meetingStore.joined].find((m) => m.code === code) || null
}

/** 提示词里的「我的会议」两份列表：都按新到旧排，字段直接给列表页渲染 */
const hostedView = () => [...meetingStore.hosted].sort((a, b) => b.id - a.id)
const joinedView = () => [...meetingStore.joined].sort((a, b) => b.id - a.id)

/** 学生只看面向全体 + 点名给自己的，和后端 adviceRows 的过滤一致 */
function adviceView(meetingId) {
  const list = [...(meetingStore.advice[meetingId] || [])].sort((a, b) => a.id - b.id)
  if (demoLoginRole === 'STUDENT') {
    return list.filter((a) => !a.targetUserId || a.targetUserId === demoAccount().id)
  }
  return list
}

/* ------------------------------------------------------------------ */
/*  求职目标（「我的」页 / 准备入口的表单回填）                        */
/* ------------------------------------------------------------------ */
const careerState = { ...MOCK_CAREER }

function saveCareer(body) {
  for (const key of ['stage', 'school', 'major', 'graduationYear', 'targetJobId', 'targetCompany']) {
    if (key in body) careerState[key] = body[key]
  }
  return { ...careerState }
}

/* ------------------------------------------------------------------ */
/*  路由表                                                             */
/* ------------------------------------------------------------------ */
/**
 * 每条：[方法, 路径正则, 处理函数, 延迟]。
 * 正则里的捕获组按顺序传给处理函数，处理函数返回 data（信封由 respond 套）。
 */
const ROUTES = [
  // ---- 认证与用户 ----
  // 登录返回什么角色，由登录页的端 tab 通过 setDemoLoginRole 提前定好——
  // 这是演示模式在手机上走通教师端/企业端的唯一入口。
  ['post', /^\/auth\/login$/, (_, config) => {
    const body = parseBody(config)
    const account = demoAccount()
    const username = String(body.username || '').trim() || account.username
    return {
      token: DEMO_TOKEN,
      userId: account.id,
      username,
      nickname: username === account.username ? account.nickname : username,
      role: demoLoginRole,
    }
  }],

  ['post', /^\/auth\/register$/, () => ({ id: MOCK_USER.id, username: MOCK_USER.username })],

  ['get', /^\/user\/me$/, () => ({
    ...MOCK_USER,
    id: demoAccount().id,
    username: demoAccount().username,
    nickname: demoAccount().nickname,
    role: demoLoginRole,
  })],

  ['get', /^\/user\/stats$/, () => ({
    totalInterviews: 7 + sessions.size,
    averageScore: MOCK_OVERVIEW.summary.averageScore,
    streakDays: MOCK_OVERVIEW.summary.streakDays,
    bestScore: MOCK_OVERVIEW.summary.bestScore,
  })],

  ['get', /^\/user\/dashboard\/profile$/, () => ({
    nickname: demoAccount().nickname,
    username: demoAccount().username,
    avatar: MOCK_USER.avatar,
    role: demoLoginRole,
  })],

  ['put', /^\/user\/profile$/, (_, config) => ({
    ...MOCK_USER,
    id: demoAccount().id,
    username: demoAccount().username,
    nickname: demoAccount().nickname,
    role: demoLoginRole,
    ...parseBody(config),
  })],

  ['get', /^\/user\/career-profile$/, () => ({ ...careerState })],
  ['put', /^\/user\/career-profile$/, (_, config) => saveCareer(parseBody(config))],

  // ---- 岗位 / 模块 ----
  ['get', /^\/job\/list$/, () => MOCK_JOBS],
  ['get', /^\/job\/(\d+)$/, (m) => MOCK_JOBS.find((job) => job.id === Number(m[1])) || null],
  ['get', /^\/modules$/, () => MOCK_MODULES],

  // ---- 简历 ----
  ['get', /^\/resume\/mine$/, () => MOCK_RESUME],
  ['post', /^\/resume$/, (_, config) => ({ ...MOCK_RESUME, ...parseBody(config) })],
  ['get', /^\/resume\/file-profile$/, () => ({
    filename: '林晓-简历.pdf',
    skills: JSON.parse(MOCK_RESUME.skills),
    projects: JSON.parse(MOCK_RESUME.projects),
    projectCount: JSON.parse(MOCK_RESUME.projects).length,
  })],
  ['post', /^\/resume\/upload$/, () => ({
    filename: '林晓-简历.pdf',
    skills: JSON.parse(MOCK_RESUME.skills),
    projects: JSON.parse(MOCK_RESUME.projects),
    projectCount: JSON.parse(MOCK_RESUME.projects).length,
  })],
  ['put', /^\/resume\/tags$/, (_, config) => ({ tags: parseBody(config).tags || [] })],
  ['get', /^\/tags$/, () => MOCK_JOBS[0] ? JSON.parse(MOCK_JOBS[0].keywords) : []],

  // ---- 首页 / 记录 ----
  ['get', /^\/dashboard\/overview$/, () => overview()],
  ['get', /^\/interview\/records$/, () => liveRecords()],

  ['delete', /^\/interview\/(\d+)$/, (m) => {
    deletedSessions.add(Number(m[1]))
    return Number(m[1])
  }],

  ['post', /^\/interview\/batch-delete$/, (_, config) => {
    const ids = parseBody(config).sessionIds || []
    ids.forEach((id) => deletedSessions.add(Number(id)))
    return ids.length
  }],

  // ---- 面试链 ----
  ['post', /^\/interview\/start$/, (_, config) => {
    const session = createSession(parseBody(config))
    return {
      sessionId: session.sid,
      jobId: session.job.id,
      jobName: session.job.name,
      durationSeconds: session.durationSeconds,
      question: questionView(session.questions[0], 1),
    }
  }, LATENCY_SLOW],

  ['post', /^\/interview\/(\d+)\/answer$/, (m, config) => {
    const session = requireSession(m[1])
    const body = parseBody(config)

    // 前端「跳过本题」发的就是空字符串。真实后端 @NotBlank 会 400，mock 不能跟着拒，
    // 否则演示时点一下跳过整场就断了。
    logAnswer(session, body.answer || '')

    if (wantsFollowup(session, body.answer)) {
      session.followedUp = true
      const followup = session.questions[session.idx].followup
      session.log.push({
        role: 'INTERVIEWER', msgType: 'FOLLOWUP',
        content: followup,
        abilityTag: session.questions[session.idx].abilityTag,
        questionId: session.questions[session.idx].id,
      })
      return { nextAction: 'FOLLOWUP', followupQuestion: followup, evalItems: evalItems(session.log.length) }
    }

    const next = advance(session)
    if (!next) {
      // 题答完了。前端收到 FINISHABLE 会立刻调 finish，所以这里不打时间戳，
      // 真正的结束时间以 finish 那次为准。
      return { nextAction: 'FINISHABLE', evalItems: evalItems(session.log.length) }
    }
    return { nextAction: 'NEXT', question: questionView(next, session.idx + 1), evalItems: evalItems(session.log.length) }
  }, LATENCY_SLOW],

  ['get', /^\/interview\/(\d+)\/next$/, (m) => {
    const session = requireSession(m[1])
    // 正常答题路径：submitAnswer 已经推进过了，这里只把题认领给前端
    if (session.pendingDelivery) {
      session.pendingDelivery = false
      return { nextAction: 'NEXT', question: questionView(session.questions[session.idx], session.idx + 1) }
    }
    const next = advance(session)
    if (!next) return { nextAction: 'FINISHABLE' }
    return { nextAction: 'NEXT', question: questionView(next, session.idx + 1) }
  }],

  ['post', /^\/interview\/(\d+)\/finish$/, (m) => {
    const session = requireSession(m[1])
    if (!session.finishedAt) {
      session.finishedAt = Date.now()
      session.reportId = ++reportSeq
    }
    // 真实后端返回的是裸数字（sessionId），不是信封里的对象
    return session.sid
  }],

  ['get', /^\/interview\/(\d+)\/report-status$/, (m) => {
    const session = requireSession(m[1])
    const ready = !!session.finishedAt && Date.now() - session.finishedAt >= REPORT_DELAY_MS
    // 未就绪时 reportId 必须是 0（不是 null）——前端判的是 status?.ready && status.reportId
    return { ready, reportId: ready ? session.reportId : 0 }
  }],

  ['get', /^\/interview\/(\d+)\/messages$/, (m) => {
    const sid = Number(m[1])
    return sessions.has(sid) ? sessions.get(sid).log : cannedTranscript()
  }],

  ['post', /^\/interview\/follow-up$/, () => ({
    id: Date.now(),
    content: '能具体讲讲你在项目里遇到的最大挑战，以及你是怎么解决的吗？',
    abilityTag: '综合能力',
  })],

  ['get', /^\/interview\/follow-up-records$/, (_, config) => ({
    records: [],
    total: 0,
    page: Number(config?.params?.page) || 1,
  })],

  ['get', /^\/interview\/follow-up-records\/stats$/, () => ({ total: 0, average: 0, byTag: [] })],

  // ---- 报告 ----
  ['get', /^\/report\/(\d+)\/improvement-path$/, () => MOCK_IMPROVEMENT_PATH],

  ['get', /^\/report\/(\d+)$/, (m) => {
    const reportId = Number(m[1])
    const session = [...sessions.values()].find((item) => item.reportId === reportId)
    return session ? buildReport(session) : { ...MOCK_REPORT, reportId }
  }],

  // ---- 教学：班级 / 成员 / 学生 / 任务 / 消息（教师端移动页 + 报告页） ----
  ['get', /^\/teaching\/summary$/, () => summaryOf()],

  ['get', /^\/teaching\/classes$/, () => [...teachingStore.classes].sort((a, b) => b.id - a.id).map(classView)],

  ['post', /^\/teaching\/classes$/, (_, config) => {
    const body = parseBody(config)
    const cls = {
      id: (teachingStore.seq.class += 1),
      name: String(body.name || '').trim() || '未命名教学班',
      semester: String(body.semester || '').trim() || '2026–2027 第一学期',
      inviteCode: randomCode(12, INVITE_ALPHABET),
      archived: false,
      createdAt: nowStamp(),
    }
    teachingStore.classes.unshift(cls)
    return cls.id
  }],

  ['put', /^\/teaching\/classes\/(\d+)$/, (m, config) => {
    const cls = findClass(m[1])
    const body = parseBody(config)
    cls.name = String(body.name || cls.name).trim()
    cls.semester = String(body.semester || cls.semester).trim()
    return null
  }],

  ['post', /^\/teaching\/classes\/(\d+)\/invite$/, (m) => {
    findClass(m[1]).inviteCode = randomCode(12, INVITE_ALPHABET)
    return null
  }],

  ['post', /^\/teaching\/classes\/(\d+)\/archive$/, (m) => {
    const cls = findClass(m[1])
    cls.archived = true
    // 和后端一致：归档连带结束该班所有已发布任务，历史结果保留
    for (const task of teachingStore.tasks) {
      if (task.classId === cls.id && task.publishedAt) task.endedAt = task.endedAt || nowStamp()
    }
    return null
  }],

  ['get', /^\/teaching\/classes\/(\d+)\/members$/, (m) => {
    const cls = findClass(m[1])
    return teachingStore.members.filter((row) => row.classId === cls.id).sort((a, b) => a.id - b.id)
  }],

  ['put', /^\/teaching\/classes\/(\d+)\/members\/(\d+)\/([A-Z]+)$/, (m) => {
    const cls = findClass(m[1])
    const member = teachingStore.members.find((row) => row.classId === cls.id && row.id === Number(m[2]))
    const state = m[3]
    if (!member) throw Object.assign(new Error('成员记录不存在'), { demoCode: 404 })
    const allowedFrom = { JOINED: ['PENDING'], REJECTED: ['PENDING'], REMOVED: ['JOINED'] }
    const hints = { JOINED: '只能审核待加入申请', REJECTED: '只能拒绝待审核申请', REMOVED: '只能移出已加入成员' }
    if (!allowedFrom[state]) throw Object.assign(new Error('成员状态无效'), { demoCode: 400 })
    if (!allowedFrom[state].includes(member.state)) {
      throw Object.assign(new Error(hints[state]), { demoCode: 400 })
    }
    member.state = state
    if (state === 'JOINED') member.joinedAt = nowStamp()
    return null
  }],

  ['get', /^\/teaching\/students$/, () => peopleView()],

  ['get', /^\/teaching\/tasks$/, (_, config) => {
    // includeAssignments 走查询参数：GET 的 params 不在 config.url 里，只能读 config.params
    const include = String(config?.params?.includeAssignments) === 'true'
    return [...teachingStore.tasks].sort((a, b) => b.id - a.id).map((task) => taskView(task, include))
  }],

  ['post', /^\/teaching\/tasks$/, (_, config) => {
    const body = parseBody(config)
    const cls = findClass(body.classId)
    const job = MOCK_JOBS.find((item) => item.id === Number(body.jobId)) || MOCK_JOBS[0]
    const task = {
      id: (teachingStore.seq.task += 1),
      title: String(body.title || '').trim() || '未命名训练任务',
      classId: cls.id,
      className: cls.name,
      jobId: job.id,
      jobName: job.name,
      publishedAt: null,
      endedAt: null,
      archived: false,
      description: String(body.description || ''),
      minAttempts: Number(body.minAttempts) || 1,
      maxAttempts: Number(body.maxAttempts) || 3,
      durationSeconds: Number(body.durationSeconds) || 1800,
      difficulty: Number(body.difficulty) || 2,
      allowLate: !!body.allowLate,
      startTime: body.startTime || nowStamp(),
      deadline: body.deadline || nowStamp(new Date(Date.now() + 7 * 86400000)),
      questions: Array.isArray(body.questions) && body.questions.length
        ? body.questions.map((q) => String(q))
        : DEMO_QUESTION_TEXT.slice(0, 3),
      scorePlan: {},
      assignments: [],
    }
    allocate(task, body)
    teachingStore.tasks.unshift(task)
    return task.id
  }],

  ['get', /^\/teaching\/tasks\/(\d+)$/, (m) => taskView(findTask(m[1]), true)],

  ['put', /^\/teaching\/tasks\/(\d+)$/, (m, config) => {
    const task = findTask(m[1])
    if (task.publishedAt) {
      throw Object.assign(new Error('已发布任务的内容与次数要求已冻结，只能延期或结束'), { demoCode: 400 })
    }
    const body = parseBody(config)
    if (body.title != null) task.title = String(body.title).trim() || task.title
    if (body.description != null) task.description = String(body.description)
    if (body.jobId != null) {
      task.jobId = Number(body.jobId) || task.jobId
      task.jobName = (MOCK_JOBS.find((item) => item.id === task.jobId) || {}).name || task.jobName
    }
    if (body.durationSeconds != null) task.durationSeconds = Number(body.durationSeconds) || task.durationSeconds
    if (body.difficulty != null) task.difficulty = Number(body.difficulty) || task.difficulty
    if (body.minAttempts != null) task.minAttempts = Number(body.minAttempts) || task.minAttempts
    if (body.maxAttempts != null) task.maxAttempts = Number(body.maxAttempts) || task.maxAttempts
    if (body.allowLate != null) task.allowLate = !!body.allowLate
    if (body.startTime) task.startTime = body.startTime
    if (body.deadline) task.deadline = body.deadline
    if (Array.isArray(body.questions) && body.questions.length) task.questions = body.questions.map((q) => String(q))
    allocate(task, body)
    return null
  }],

  ['delete', /^\/teaching\/tasks\/(\d+)$/, (m) => {
    const task = findTask(m[1])
    if (task.publishedAt) {
      throw Object.assign(new Error('只能删除未发布草稿，已发布任务与历史记录需保留'), { demoCode: 400 })
    }
    teachingStore.tasks = teachingStore.tasks.filter((item) => item.id !== task.id)
    return null
  }],

  ['post', /^\/teaching\/tasks\/(\d+)\/publish$/, (m) => {
    publishTask(findTask(m[1]))
    return null
  }],

  ['put', /^\/teaching\/tasks\/(\d+)\/deadline$/, (m, config) => {
    const task = findTask(m[1])
    const deadline = String(parseBody(config).deadline || '')
    const valid = task.publishedAt && !task.endedAt
      && stampMs(deadline) > stampMs(task.deadline) && stampMs(deadline) > Date.now()
    if (!valid) throw Object.assign(new Error('只能延长已发布且未手动结束任务的截止时间'), { demoCode: 400 })
    task.deadline = deadline
    return null
  }],

  ['post', /^\/teaching\/tasks\/(\d+)\/end$/, (m) => {
    const task = findTask(m[1])
    if (!task.publishedAt) throw Object.assign(new Error('草稿不能结束'), { demoCode: 400 })
    task.endedAt = task.endedAt || nowStamp()
    return null
  }],

  ['post', /^\/teaching\/tasks\/(\d+)\/remind$/, () => null],

  ['get', /^\/teaching\/messages$/, () => [...teachingStore.messages].sort((a, b) => b.id - a.id)],

  ['put', /^\/teaching\/messages\/(\d+)\/read$/, (m) => {
    const message = teachingStore.messages.find((row) => row.id === Number(m[1]))
    if (message) message.isRead = true
    return null
  }],

  // 消息链接落到的教师报告页（/teacher/reports/:id）
  ['get', /^\/teaching\/reports\/(\d+)\/reviews$/, (m) => reviewsFor(m[1])],

  ['post', /^\/teaching\/reports\/(\d+)\/reviews$/, (m, config) => {
    const reportId = Number(m[1])
    const item = {
      id: (teachingStore.seq.review += 1),
      reportId,
      teacherId: DEMO_ACCOUNTS.TEACHER.id,
      teacherName: DEMO_ACCOUNTS.TEACHER.nickname,
      text: String(parseBody(config).text || '').trim() || '表现不错，继续保持。',
      createdAt: nowStamp(),
    }
    ;(teachingStore.reviews[reportId] = teachingStore.reviews[reportId] || []).push(item)
    return null
  }],

  ['get', /^\/teaching\/reports\/(\d+)$/, (m) => reportBundle(m[1])],

  // ---- 会议：企业端移动页 / 加入会议页 ----
  ['post', /^\/meeting$/, (_, config) => {
    const body = parseBody(config)
    const code = meetingCode()
    const account = demoAccount()
    const meeting = {
      id: (meetingStore.seq.meeting += 1),
      title: String(body.title || '').trim() || '视频面试',
      code,
      codeDisplay: meetingCodeDisplay(code),
      hostId: account.id,
      hostName: account.nickname,
      jobId: body.jobId ?? null,
      status: 'OPEN',
      participantCount: 0,
      createdAt: nowStamp(),
    }
    meetingStore.hosted.unshift(meeting)
    return {
      id: meeting.id,
      code: meeting.code,
      codeDisplay: meeting.codeDisplay,
      title: meeting.title,
      jobId: meeting.jobId,
      hostId: meeting.hostId,
      status: meeting.status,
      participantCount: 0,
    }
  }],

  ['get', /^\/meeting\/code\/([^/]+)$/, (m) => {
    const meeting = findByCode(decodeURIComponent(m[1]))
    if (!meeting) throw Object.assign(new Error('会议号不存在'), { demoCode: 404 })
    const count = meeting.participantCount || 0
    return {
      id: meeting.id,
      code: meeting.code,
      codeDisplay: meeting.codeDisplay || meetingCodeDisplay(meeting.code),
      title: meeting.title,
      status: meeting.status,
      hostId: meeting.hostId,
      hostName: meeting.hostName,
      jobId: meeting.jobId ?? null,
      participantCount: count,
      joinable: meeting.status === 'OPEN' && count < 6,
    }
  }],

  ['get', /^\/meeting\/mine$/, () => ({ hosted: hostedView(), joined: joinedView() })],

  ['get', /^\/meeting\/(\d+)\/advice$/, (m) => adviceView(Number(m[1]))],

  ['post', /^\/meeting\/(\d+)\/advice$/, (m, config) => {
    const meetingId = Number(m[1])
    if (!findMeeting(meetingId)) throw Object.assign(new Error('会议不存在'), { demoCode: 404 })
    const body = parseBody(config)
    const account = demoAccount()
    const item = {
      id: (meetingStore.seq.advice += 1),
      meetingId,
      authorId: account.id,
      authorName: account.nickname,
      targetUserId: body.targetUserId ?? null,
      content: String(body.content || '').trim() || '面试表现良好，继续保持。',
      createdAt: nowStamp(),
    }
    ;(meetingStore.advice[meetingId] = meetingStore.advice[meetingId] || []).push(item)
    return item
  }],

  ['post', /^\/meeting\/(\d+)\/end$/, (m) => {
    const meetingId = Number(m[1])
    if (!findMeeting(meetingId)) throw Object.assign(new Error('会议不存在'), { demoCode: 404 })
    eachMeeting(meetingId, (meeting) => {
      meeting.status = 'ENDED'
      meeting.endedAt = nowStamp()
    })
    return null
  }],

  ['get', /^\/meeting\/(\d+)$/, (m) => {
    const meeting = findMeeting(m[1])
    if (!meeting) throw Object.assign(new Error('会议不存在'), { demoCode: 404 })
    return {
      ...meeting,
      codeDisplay: meeting.codeDisplay || meetingCodeDisplay(meeting.code),
      participants: [],
      advice: adviceView(Number(m[1])),
    }
  }],

  // ---- 语音 ----
  ['post', /^\/speech\/transcribe$/, () => MOCK_TRANSCRIPT],

  // ---- AI 状态 ----
  // 演示模式说自己是 AI：顶部徽标就不会挂「演示模式：未配置 AI 密钥」，
  // 那句话出现在评委面前很难解释。
  ['get', /^\/ai\/status$/, () => MOCK_AI_STATUS],
]

/* ------------------------------------------------------------------ */
/*  adapter                                                            */
/* ------------------------------------------------------------------ */
/** 请求体：axios 在进 adapter 之前就跑完了 transformRequest，JSON 请求到这里已经是字符串 */
function parseBody(config) {
  const raw = config?.data
  if (!raw) return {}
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw)
    } catch {
      return {}
    }
  }
  return raw instanceof FormData ? {} : raw
}

/** 把 config.url 归一成路径：去掉 baseURL 前缀、query 和末尾斜杠 */
function normalizePath(config) {
  let url = String(config.url || '')
  const base = String(config.baseURL || '')
  if (base && url.startsWith(base)) url = url.slice(base.length)
  // 绝对地址（理论上不会走到这里）也兜一下
  try {
    if (/^https?:\/\//.test(url)) url = new URL(url).pathname
  } catch {
    /* 解析不了就按原样匹配 */
  }
  const queryAt = url.indexOf('?')
  if (queryAt >= 0) url = url.slice(0, queryAt)
  if (!url.startsWith('/')) url = `/${url}`
  return url.replace(/\/+$/, '') || '/'
}

/**
 * axios 自定义 adapter。命中路由就返回假响应，没命中返回 404 信封。
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export default function demoAdapter(config) {
  const method = String(config.method || 'get').toLowerCase()
  const path = normalizePath(config)
  const hit = ROUTES.find(([m, pattern]) => m === method && pattern.test(path))

  if (!hit) {
    // 不静默成功：没覆盖到的接口要能被看见，否则只是页面某块空着，很难倒查到是 mock 缺项
    console.warn(`[离线演示] 未覆盖的接口：${method.toUpperCase()} ${path}`)
    return sleep(LATENCY).then(() => respond(config, ERR(`该功能暂不可用：${path}`, 404)))
  }

  const [, pattern, handler, delay = LATENCY] = hit
  const match = pattern.exec(path)

  return sleep(delay).then(() => {
    if (import.meta.env?.DEV) {
      console.info(`[离线演示] ${method.toUpperCase()} ${path} → 本地假数据`)
    }
    try {
      return respond(config, OK(handler(match, config)))
    } catch (error) {
      // 会话丢了之类的情况，当成服务端 404 返回，让调用方按正常错误路径处理
      return respond(config, ERR(error.message || '数据异常，请重试', error.demoCode || 500))
    }
  })
}
