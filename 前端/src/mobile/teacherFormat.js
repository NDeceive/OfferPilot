/**
 * 教师手机页共用的状态映射与时间格式化（纯函数，可被 node --test 单测）。
 *
 * 文案基准 = TeachingPortal.vue 桌面端（memberLabel/lifeLabel/reportState/executionLabel），
 * 手机页不允许出现和桌面不一致的状态说法，所以统一收在这里。
 */

const MEMBER_STATES = { PENDING: '待审核', JOINED: '已加入', REJECTED: '已拒绝', REMOVED: '已移出' }
const LIFECYCLES = { DRAFT: '草稿', ACTIVE: '进行中', SCHEDULED: '待开始', ENDED: '已结束', CLOSED: '已截止' }
const COMPLETIONS = { NOT_STARTED: '未开始', IN_PROGRESS: '训练中', PENDING_VALIDATION: '待报告校验', COMPLETED: '已完成次数要求', EXEMPT: '教师已减免' }
const ATTEMPT_STATES = { ONGOING: '训练中', GENERATING: '报告生成中', READY: '有效结果', INVALID: '未达标', FAILED: '报告生成失败' }

export const memberStateLabel = (s) => MEMBER_STATES[s] || s || ''
export const lifecycleLabel = (s) => LIFECYCLES[s] || s || ''
export const completionLabel = (s) => COMPLETIONS[s] || s || ''
export const attemptStateLabel = (s) => ATTEMPT_STATES[s] || s || ''

/** 生命周期 → 列表卡片配色档位（CSS 类后缀） */
export function lifecycleTone(lifecycle) {
  if (lifecycle === 'ACTIVE') return 'active'
  if (lifecycle === 'DRAFT') return 'draft'
  return 'ended'
}

/** 后端时间无时区后缀时按东八区解析（与 TeachingPortal.parseTime 同规则）：
 *  带 Z / + 的按原样，否则补 +08:00。解析失败返回 null。 */
export function parseServerTime(value) {
  if (!value) return null
  const t = Date.parse(/[Z+]/.test(value) ? value : value + '+08:00')
  return Number.isNaN(t) ? null : t
}

export function formatDateTime(value) {
  const t = parseServerTime(value)
  if (t === null) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(t)
}

export function formatDate(value) {
  const t = parseServerTime(value)
  if (t === null) return '日期待记录'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit' }).format(t)
}

/** 截止时间相对文案：<0 已截止 / <24h 剩几小时 / 否则正常日期 */
export function deadlineHint(value, now = Date.now()) {
  const t = parseServerTime(value)
  if (t === null) return ''
  const diff = t - now
  if (diff < 0) return '已截止'
  if (diff < 24 * 3600 * 1000) return `剩 ${Math.max(1, Math.round(diff / 3600000))} 小时`
  return `截止 ${formatDateTime(value)}`
}

/** 任务的执行进度：已完成参与人数 / 非减免的参与人数；拿不到 assignments 时返回 null */
export function taskProgress(task) {
  if (!Array.isArray(task?.assignments)) return null
  const active = task.assignments.filter((a) => !a.exempt)
  if (!active.length) return null
  return { completed: active.filter((a) => a.completionStatus === 'COMPLETED').length, total: active.length }
}
