/**
 * 离线演示假后端（utils/demoAdapter.js）的行为测试。
 *
 * 直接调 adapter、传假 config，不需要浏览器：adapter 只是「路径 → 处理函数」，
 * 拦住的就是 request.js 真实发出的那批调用。重点盯三件事：
 *   1. 登录/用户接口按登录页选的角色返回（教师端/企业端演示的唯一入口）；
 *   2. 教学与会议的操作会真的推内存状态（审核后人数变、发布后 assignments 生成）；
 *   3. 未覆盖接口必须回 404 信封而不是静默成功。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import demoAdapter, { DEMO_TOKEN, setDemoLoginRole } from '../src/utils/demoAdapter.js'
import { isValidCode } from '../src/utils/meetingProtocol.js'

const call = (method, url, { params, data } = {}) =>
  demoAdapter({ method, url, baseURL: '/api', params, data })

/** 调一次并断言信封是 200，返回 data 本体 */
async function ok(method, url, options) {
  const res = await call(method, url, options)
  assert.equal(res.data.code, 200, `${method} ${url} 应为成功信封：${JSON.stringify(res.data)}`)
  return res.data.data
}

test('登录按端 tab 签发角色；退出重置后回学生', async () => {
  setDemoLoginRole('student')
  let login = await ok('post', '/auth/login', { data: { username: '', password: '' } })
  assert.equal(login.role, 'STUDENT')
  assert.equal(login.token, DEMO_TOKEN)
  assert.equal(login.username, 'student')

  setDemoLoginRole('teacher')
  login = await ok('post', '/auth/login', { data: { username: '', password: '' } })
  assert.equal(login.role, 'TEACHER')
  assert.equal(login.username, 'teacher')
  assert.equal(login.nickname, '王老师')
  // /user/me 必须和登录返回同一个角色，否则刷新后「我的」页身份对不上
  const me = await ok('get', '/user/me')
  assert.equal(me.role, 'TEACHER')
  assert.equal(me.nickname, '王老师')

  setDemoLoginRole('enterprise')
  login = await ok('post', '/auth/login', { data: { username: '', password: '' } })
  assert.equal(login.role, 'ENTERPRISE')
  assert.equal(login.nickname, '陈经理')

  setDemoLoginRole('student')
})

test('用户自己输入账号时用输入值，密码任意', async () => {
  setDemoLoginRole('student')
  const login = await ok('post', '/auth/login', { data: { username: 'zhangsan', password: 'x' } })
  assert.equal(login.username, 'zhangsan')
  assert.equal(login.nickname, 'zhangsan')
  assert.equal(login.role, 'STUDENT')
})

test('教学：班级列表带人数，成员/学生名单形状对齐后端', async () => {
  const classes = await ok('get', '/teaching/classes')
  assert.ok(Array.isArray(classes) && classes.length >= 3)
  const cls201 = classes.find((c) => c.id === 201)
  assert.ok(cls201, '种子数据里有 201 班')
  assert.equal(cls201.joinedCount, 3)
  assert.equal(cls201.pendingCount, 1)
  assert.equal(typeof cls201.inviteCode, 'string')

  const members = await ok('get', '/teaching/classes/201/members')
  assert.equal(members.length, 4)
  const pending = members.find((m) => m.state === 'PENDING')
  assert.equal(pending.studentId, 33)

  const people = await ok('get', '/teaching/students')
  // 只收已加入成员，且每人一行带班级名（同一学生跨班会多行）
  assert.ok(people.length >= 7)
  assert.ok(people.every((p) => p.classId && p.className && p.id && p.name))
  const demoStudent = people.find((p) => p.id === 3)
  assert.equal(demoStudent.className, '软件工程 2024 级 · 1 班')
})

test('教学：summary 统计口径（减免不算任务数、待点评=有报告无点评）', async () => {
  const summary = await ok('get', '/teaching/summary')
  assert.equal(summary.studentTotal, 5)
  assert.equal(summary.taskTotal, 4)
  assert.equal(summary.assignmentTotal, 7) // 7101(3) + 7102(2) + 7104(3-1 减免)
  assert.equal(summary.completedTotal, 3)
  assert.equal(summary.validAttemptTotal, 5)
  // 待点评按后端口径数「有报告且无点评」的 attempt：6101/6102(INVALID 也有报告)/6103/6104/6105/6106
  assert.equal(summary.pendingReviewTotal, 6)
})

test('教学：建班返回数字 id 并出现在列表最前', async () => {
  const id = await ok('post', '/teaching/classes', { data: { name: '测试新建班', semester: '2026–2027 第一学期' } })
  assert.equal(typeof id, 'number')
  const classes = await ok('get', '/teaching/classes')
  assert.equal(classes[0].id, id)
  assert.match(classes[0].inviteCode, /^[0-9A-F]{12}$/)
  assert.equal(classes[0].pendingCount, 0)
})

test('教学：审核 PENDING→JOINED 后班级待审归零；非法流转被拒', async () => {
  await ok('put', '/teaching/classes/201/members/5204/JOINED')
  const classes = await ok('get', '/teaching/classes')
  assert.equal(classes.find((c) => c.id === 201).pendingCount, 0)
  assert.equal(classes.find((c) => c.id === 201).joinedCount, 4)

  // 已 JOINED 的成员再走 JOINED 审核应报错
  const res = await call('put', '/teaching/classes/201/members/5201/JOINED')
  assert.equal(res.data.code, 400)
  assert.match(res.data.message, /只能审核/)
})

test('教学：任务创建为草稿、发布生成全班 assignments、结束后不可再删', async () => {
  const id = await ok('post', '/teaching/tasks', {
    data: {
      classId: 201,
      title: '单测任务',
      jobId: 1,
      questions: ['问题一？', '问题二？'],
      minAttempts: 1,
      maxAttempts: 2,
      durationSeconds: 1800,
      difficulty: 2,
      startTime: '2026-10-01T09:00:00',
      deadline: '2026-12-31T22:00:00',
      recipientMode: 'ALL',
    },
  })
  assert.equal(typeof id, 'number')

  let detail = await ok('get', `/teaching/tasks/${id}`)
  assert.equal(detail.lifecycle, 'DRAFT')
  assert.deepEqual(detail.questions, ['问题一？', '问题二？'])
  assert.equal(detail.publishedAt, null)

  await ok('post', `/teaching/tasks/${id}/publish`)
  detail = await ok('get', `/teaching/tasks/${id}`)
  assert.equal(detail.lifecycle, 'ACTIVE')
  assert.ok(detail.publishedAt)
  // 发布按当前已加入成员生成 assignments（审核测试之后 201 班有 4 个 JOINED）
  const members = await ok('get', '/teaching/classes/201/members')
  const joined = members.filter((m) => m.state === 'JOINED').length
  assert.equal(detail.assignments.length, joined)
  assert.ok(detail.assignments.every((a) => a.completionStatus === 'NOT_STARTED'))

  await ok('post', `/teaching/tasks/${id}/end`)
  detail = await ok('get', `/teaching/tasks/${id}`)
  assert.equal(detail.lifecycle, 'ENDED')

  // 已发布任务不允许删除（草稿才可以）
  const res = await call('delete', `/teaching/tasks/${id}`)
  assert.equal(res.data.code, 400)
})

test('教学：includeAssignments 只对已发布任务挂 assignments', async () => {
  const list = await ok('get', '/teaching/tasks', { params: { includeAssignments: true } })
  const draft = list.find((t) => t.id === 7103)
  assert.equal(draft.lifecycle, 'DRAFT')
  assert.equal(draft.assignments, undefined, '草稿不应带 assignments（与后端一致）')
  const active = list.find((t) => t.id === 7101)
  assert.ok(Array.isArray(active.assignments) && active.assignments.length === 3)
})

test('教学：消息列表与已读标记', async () => {
  // 与后端 ORDER BY id DESC 一致：新的（3103）排前面
  const messages = await ok('get', '/teaching/messages')
  assert.equal(messages[0].id, 3103)
  const unread = messages.find((m) => !m.isRead)
  assert.ok(unread, '种子里有未读消息')
  await ok('put', `/teaching/messages/${unread.id}/read`)
  const after = await ok('get', '/teaching/messages')
  assert.equal(after.find((m) => m.id === unread.id).isRead, true)
})

test('教学：报告三件套带可渲染的问答与点评', async () => {
  const bundle = await ok('get', '/teaching/reports/6103')
  assert.equal(bundle.report.reportId, 6103)
  assert.ok(bundle.messages.length > 0)
  // 报告页按 roundNo 分组渲染、按 id 做 key
  assert.ok(bundle.messages.every((m) => Number.isInteger(m.id) && Number.isInteger(m.roundNo)))
  assert.ok(bundle.report.summary)

  await ok('post', '/teaching/reports/6103/reviews', { data: { text: '单测点评' } })
  const reviews = await ok('get', '/teaching/reports/6103/reviews')
  assert.equal(reviews.length, 1)
  assert.equal(reviews[0].text, '单测点评')
  assert.equal(reviews[0].teacherName, '王老师')
})

test('会议：种子会议号必须能通过前端格式校验（无 O/I/0/1）', async () => {
  // 查号页在用假后端之前先做 isValidCode 校验：种子里若混进 O/0 这类歧义字符，
  // 演示会议号会连查都查不了（曾经 DEMO2026 就栽在这），这里把它钉死。
  const mine = await ok('get', '/meeting/mine')
  for (const row of [...mine.hosted, ...mine.joined]) {
    assert.ok(isValidCode(row.code), `种子会议号 ${row.code} 通不过 isValidCode`)
    assert.equal(row.codeDisplay, `${row.code.slice(0, 4)}-${row.code.slice(4)}`)
  }
})

test('会议：创建→预检（归一化会议号）→建议→结束，全链路状态推进', async () => {
  const created = await ok('post', '/meeting', { data: { title: '单测会议' } })
  assert.match(created.code, /^[A-Z2-9]{8}$/)
  assert.equal(created.codeDisplay, `${created.code.slice(0, 4)}-${created.code.slice(4)}`)
  assert.equal(created.status, 'OPEN')

  // 小写 + 带横杠的输入也能预检成功（normalizeCode 与后端一致）
  const preview = await ok('get', `/meeting/code/${encodeURIComponent(created.codeDisplay.toLowerCase())}`)
  assert.equal(preview.joinable, true)
  assert.equal(preview.title, '单测会议')
  assert.equal(preview.hostName, '林晓')

  const mine = await ok('get', '/meeting/mine')
  assert.equal(mine.hosted[0].id, created.id)

  const advice = await ok('post', `/meeting/${created.id}/advice`, { data: { content: '表达可以更结构化' } })
  assert.equal(advice.authorName, '林晓')
  const list = await ok('get', `/meeting/${created.id}/advice`)
  assert.equal(list.length, 1)

  await ok('post', `/meeting/${created.id}/end`)
  const ended = await ok('get', `/meeting/code/${created.code}`)
  assert.equal(ended.status, 'ENDED')
  assert.equal(ended.joinable, false)
  // 结束后建议仍可回看
  assert.equal((await ok('get', `/meeting/${created.id}/advice`)).length, 1)
})

test('会议：不存在的会议号报 404 且文案明确', async () => {
  const res = await call('get', '/meeting/code/ZZZZZZZZ')
  assert.equal(res.data.code, 404)
  assert.equal(res.data.message, '会议号不存在')
})

test('求职目标：读取种子、保存后回读', async () => {
  const before = await ok('get', '/user/career-profile')
  assert.equal(before.school, '云麓大学')
  const saved = await ok('put', '/user/career-profile', { data: { ...before, targetCompany: '单测公司' } })
  assert.equal(saved.targetCompany, '单测公司')
  assert.equal((await ok('get', '/user/career-profile')).targetCompany, '单测公司')
})

test('未覆盖接口返回 404 信封而不是静默成功', async () => {
  const res = await call('get', '/definitely/not/covered')
  assert.equal(res.data.code, 404)
  assert.match(res.data.message, /该功能暂不可用/)
})
