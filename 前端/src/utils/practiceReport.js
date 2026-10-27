import { isObjectiveType } from './practiceTypes.js'
import { judgeStatusLabel, pendingJudgeStatuses } from './practiceJudging.js'

export function reportQuestionKind(q) {
  return isObjectiveType(q.type || q.questionType) ? 'objective' : String(q.type || q.questionType).toLowerCase() === 'coding' ? 'coding' : 'knowledge'
}

// Review marks are personal study state; they never change historical grading.
export function reportQuestionOutcome(q) {
  const s = q.latestSubmission
  if (!s) return { key: 'unsubmitted', label: '未提交' }
  const status = s.status || s.result?.status
  if (['SYSTEM_ERROR', 'SERVICE_UNAVAILABLE', 'Internal Error', 'Exec Format Error'].includes(status)) return { key: 'system', label: '判题异常' }
  if (pendingJudgeStatuses.has(status) || status === 'SUBMITTED') return { key: 'pending', label: judgeStatusLabel(status) }
  if (status === 'SELF_REVIEW') return q.selfAssessment ? { key: 'self_done', label: q.selfAssessment.state === 'MASTERED' ? '自评：已掌握' : '自评：需要巩固' } : { key: 'self', label: '待自评' }
  if (reportQuestionKind(q) === 'objective') {
    if (status === 'OBJECTIVE_CORRECT') return { key: 'passed', label: '回答正确' }
    if (status === 'OBJECTIVE_INCORRECT') return { key: 'failed', label: '回答错误' }
  }
  if (reportQuestionKind(q) === 'coding') {
    if (s.isCorrect === true || s.isCorrect === 1 || s.result?.passed === true || status === 'ACCEPTED') return { key: 'passed', label: '已通过' }
    if (s.isCorrect === false || s.isCorrect === 0 || s.result?.passed === false || ['WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED'].includes(status)) return { key: 'failed', label: '未通过' }
  }
  return { key: 'unknown', label: judgeStatusLabel(status) }
}

export function filterReportQuestions(rows, filters) {
  const query = filters.search.trim().toLocaleLowerCase()
  const selected = rows.filter(row =>
    (!query || [row.q.title, row.q.description, row.q.content, ...(row.q.tags || [])].join(' ').toLocaleLowerCase().includes(query)) &&
    (filters.result === 'all' || row.outcome.key === filters.result) &&
    (filters.kind === 'all' || row.kind === filters.kind) &&
    (filters.review === 'all' || (filters.review === 'NONE' ? !['PENDING', 'RESOLVED'].includes(row.reviewState) : row.reviewState === filters.review))
  )
  return filters.sort === 'failed' ? selected.sort((a, b) => Number(b.outcome.key === 'failed') - Number(a.outcome.key === 'failed') || a.index - b.index) : selected
}

export function canRetryReportQuestion(q, reviewState = q.reviewState) {
  return reviewState === 'PENDING' || reviewState !== 'RESOLVED' &&
    (q.latestSubmission?.isCorrect === false || q.latestSubmission?.isCorrect === 0 || reportQuestionOutcome(q).key === 'failed')
}

export function reportMetrics(report) {
  const questions = report.questionList || [], stats = report.statistics || {}
  const count = (kind, outcome) => questions.filter(q => (!kind || reportQuestionKind(q) === kind) && (!outcome || reportQuestionOutcome(q).key === outcome)).length
  const objectiveCorrect = stats.objectiveCorrect ?? count('objective', 'passed')
  const objectiveIncorrect = stats.objectiveIncorrect ?? count('objective', 'failed')
  const codingPassed = stats.codingPassed ?? count('coding', 'passed')
  const codingFailed = stats.codingFailed ?? count('coding', 'failed')
  const evaluated = objectiveCorrect + objectiveIncorrect + codingPassed + codingFailed
  return { total: stats.total ?? questions.length, submitted: stats.submitted ?? questions.filter(q => q.latestSubmission && !['system', 'pending'].includes(reportQuestionOutcome(q).key)).length,
    objectiveTotal: count('objective'), codingTotal: count('coding'), knowledgeTotal: count('knowledge'),
    objectiveCorrect, objectiveIncorrect, codingPassed, codingFailed, evaluated, correct: objectiveCorrect + codingPassed,
    selfReview: stats.selfReview ?? count('knowledge', 'self'), selfReviewed: count('knowledge', 'self_done'), legacyReview: stats.legacyReview ?? count('knowledge', 'unknown'),
    score: evaluated ? Math.round((objectiveCorrect + codingPassed) / evaluated * 100) : null }
}

function parsePracticeDate(value) {
  const normalized = String(value).replace(' ', 'T')
  // SQL timestamps have no offset; existing server dates are Beijing local time.
  return new Date(normalized.includes('T') && !/(Z|[+-]\d{2}:?\d{2})$/i.test(normalized) ? `${normalized}+08:00` : normalized)
}

export function formatPracticeDate(value) {
  if (!value) return '—'
  const date = parsePracticeDate(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(date).replaceAll('/', '-')
}

export function practiceElapsed(start, finish) {
  if (!start || !finish) return ''
  const seconds = Math.floor((parsePracticeDate(finish) - parsePracticeDate(start)) / 1000)
  if (!Number.isFinite(seconds) || seconds < 0) return ''
  const hours = Math.floor(seconds / 3600)
  return `${hours ? `${hours} 小时 ` : ''}${Math.floor(seconds % 3600 / 60)} 分 ${seconds % 60} 秒`
}
