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
  MOCK_IMPROVEMENT_PATH,
  MOCK_JOBS,
  MOCK_MODULES,
  MOCK_OVERVIEW,
  MOCK_QUESTIONS,
  MOCK_RECORDS,
  MOCK_REPORT,
  MOCK_RESUME,
  MOCK_TRANSCRIPT,
  MOCK_USER,
} from './demoData'

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
    throw Object.assign(new Error('演示会话不存在（页面刷新过？），请重新发起一场面试'), { demoCode: 404 })
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
      content: `（演示数据）第 ${index + 1} 题的回答要点：先给结论，再补原理，最后落到项目里的一次实践。`,
      abilityTag: question.abilityTag, questionId: question.id,
    })
    if (index === 0) {
      log.push({
        role: 'INTERVIEWER', msgType: 'FOLLOWUP',
        content: question.followup, abilityTag: question.abilityTag, questionId: question.id,
      })
      log.push({
        role: 'CANDIDATE', msgType: 'ANSWER',
        content: '（演示数据）追问的回答：举了一个实际踩过的例子说明差别。',
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
/*  路由表                                                             */
/* ------------------------------------------------------------------ */
/**
 * 每条：[方法, 路径正则, 处理函数, 延迟]。
 * 正则里的捕获组按顺序传给处理函数，处理函数返回 data（信封由 respond 套）。
 */
const ROUTES = [
  // ---- 认证与用户 ----
  ['post', /^\/auth\/login$/, (_, config) => {
    const body = parseBody(config)
    const account = String(body.username || 'student').trim() || 'student'
    return {
      token: DEMO_TOKEN,
      userId: MOCK_USER.id,
      username: account,
      nickname: account === 'student' ? MOCK_USER.nickname : account,
      role: 'STUDENT',
    }
  }],

  ['post', /^\/auth\/register$/, () => ({ id: MOCK_USER.id, username: MOCK_USER.username })],

  ['get', /^\/user\/me$/, () => MOCK_USER],

  ['get', /^\/user\/stats$/, () => ({
    totalInterviews: 7 + sessions.size,
    averageScore: MOCK_OVERVIEW.summary.averageScore,
    streakDays: MOCK_OVERVIEW.summary.streakDays,
    bestScore: MOCK_OVERVIEW.summary.bestScore,
  })],

  ['get', /^\/user\/dashboard\/profile$/, () => ({
    nickname: MOCK_USER.nickname,
    username: MOCK_USER.username,
    avatar: MOCK_USER.avatar,
    role: MOCK_USER.role,
  })],

  ['put', /^\/user\/profile$/, (_, config) => ({ ...MOCK_USER, ...parseBody(config) })],

  // ---- 岗位 / 模块 ----
  ['get', /^\/job\/list$/, () => MOCK_JOBS],
  ['get', /^\/job\/(\d+)$/, (m) => MOCK_JOBS.find((job) => job.id === Number(m[1])) || null],
  ['get', /^\/modules$/, () => MOCK_MODULES],

  // ---- 简历 ----
  ['get', /^\/resume\/mine$/, () => MOCK_RESUME],
  ['post', /^\/resume$/, (_, config) => ({ ...MOCK_RESUME, ...parseBody(config) })],
  ['get', /^\/resume\/file-profile$/, () => ({
    filename: '演示简历.pdf',
    skills: JSON.parse(MOCK_RESUME.skills),
    projects: JSON.parse(MOCK_RESUME.projects),
    projectCount: JSON.parse(MOCK_RESUME.projects).length,
  })],
  ['post', /^\/resume\/upload$/, () => ({
    filename: '演示简历.pdf',
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
    content: '（演示数据）这是根据你的回答追加的一道追问。',
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
    return sleep(LATENCY).then(() => respond(config, ERR(`离线演示模式暂未覆盖该接口：${path}`, 404)))
  }

  const [, pattern, handler, delay = LATENCY] = hit
  const match = pattern.exec(path)

  return sleep(delay).then(() => {
    if (import.meta.env.DEV) {
      console.info(`[离线演示] ${method.toUpperCase()} ${path} → 本地假数据`)
    }
    try {
      return respond(config, OK(handler(match, config)))
    } catch (error) {
      // 会话丢了之类的情况，当成服务端 404 返回，让调用方按正常错误路径处理
      return respond(config, ERR(error.message || '演示数据异常', error.demoCode || 500))
    }
  })
}
