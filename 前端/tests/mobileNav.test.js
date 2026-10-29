import test from 'node:test'
import assert from 'node:assert/strict'
import { TEACHER_TABS, ENTERPRISE_TABS } from '../src/mobile/nav.js'
import {
  memberStateLabel,
  lifecycleLabel,
  completionLabel,
  attemptStateLabel,
  lifecycleTone,
  parseServerTime,
  deadlineHint,
  taskProgress,
} from '../src/mobile/teacherFormat.js'

test('教师底部导航：5 项，路径唯一且都在 /teacher 下', () => {
  assert.equal(TEACHER_TABS.length, 5)
  const paths = TEACHER_TABS.map((t) => t.path)
  assert.equal(new Set(paths).size, paths.length, '路径必须唯一')
  for (const t of TEACHER_TABS) {
    assert.ok(t.path.startsWith('/teacher/'), `${t.path} 应在 /teacher 下`)
    assert.ok(t.label.trim().length > 0, `${t.path} 缺少 label`)
    assert.match(t.icon, /^<svg/, `${t.path} 缺少 svg 图标`)
  }
  // 底栏第一项必须对应总览页（登录后落点），否则点「总览」会跳空
  assert.equal(TEACHER_TABS[0].path, '/teacher/dashboard')
})

test('企业底部导航：3 项（会议中心/加入会议/我的），路径唯一', () => {
  assert.equal(ENTERPRISE_TABS.length, 3)
  const paths = ENTERPRISE_TABS.map((t) => t.path)
  assert.equal(new Set(paths).size, paths.length)
  assert.deepEqual(paths, ['/enterprise/dashboard', '/meeting', '/enterprise/account'])
  for (const t of ENTERPRISE_TABS) {
    assert.ok(t.label.trim().length > 0)
    assert.match(t.icon, /^<svg/)
  }
})

test('状态映射与桌面端文案一致', () => {
  assert.equal(memberStateLabel('PENDING'), '待审核')
  assert.equal(memberStateLabel('JOINED'), '已加入')
  assert.equal(memberStateLabel('REMOVED'), '已移出')
  assert.equal(lifecycleLabel('DRAFT'), '草稿')
  assert.equal(lifecycleLabel('ACTIVE'), '进行中')
  assert.equal(completionLabel('COMPLETED'), '已完成次数要求')
  assert.equal(attemptStateLabel('READY'), '有效结果')
  // 未知值兜底：原样返回，不显示 undefined
  assert.equal(memberStateLabel('WHATEVER'), 'WHATEVER')
  assert.equal(lifecycleLabel(null), '')
})

test('lifecycleTone：进行中=active，草稿=draft，其余=ended', () => {
  assert.equal(lifecycleTone('ACTIVE'), 'active')
  assert.equal(lifecycleTone('DRAFT'), 'draft')
  assert.equal(lifecycleTone('ENDED'), 'ended')
  assert.equal(lifecycleTone('CLOSED'), 'ended')
})

test('parseServerTime：无时区后缀按东八区，带 Z 按原样', () => {
  // 东八区 2026-07-08 08:00 == UTC 00:00
  assert.equal(parseServerTime('2026-07-08T08:00:00'), Date.UTC(2026, 6, 8, 0, 0, 0))
  assert.equal(parseServerTime('2026-07-08T00:00:00Z'), Date.UTC(2026, 6, 8, 0, 0, 0))
  assert.equal(parseServerTime(''), null)
  assert.equal(parseServerTime('not-a-date'), null)
})

test('deadlineHint：已截止 / 24 小时内剩几小时 / 正常日期', () => {
  const now = Date.UTC(2026, 6, 8, 0, 0, 0) // 东八区 08:00
  assert.equal(deadlineHint('2026-07-08T07:00:00', now), '已截止')
  assert.equal(deadlineHint('2026-07-08T15:00:00', now), '剩 7 小时')
  assert.match(deadlineHint('2026-07-20T08:00:00', now), /^截止/)
  assert.equal(deadlineHint('', now), '')
})

test('taskProgress：只数非减免成员，拿不到 assignments 时返回 null', () => {
  assert.equal(taskProgress({}), null)
  assert.equal(taskProgress({ assignments: [] }), null)
  const task = {
    assignments: [
      { completionStatus: 'COMPLETED' },
      { completionStatus: 'IN_PROGRESS' },
      { completionStatus: 'COMPLETED', exempt: true },
    ],
  }
  assert.deepEqual(taskProgress(task), { completed: 1, total: 2 })
})
