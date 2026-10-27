import test from 'node:test'
import assert from 'node:assert/strict'
import { canRetryReportQuestion, filterReportQuestions, formatPracticeDate, practiceElapsed, reportMetrics, reportQuestionKind, reportQuestionOutcome } from '../src/utils/practiceReport.js'

test('report outcomes exclude self review, system errors and pending tasks from correctness; review never rewrites grades', () => {
  const q = (type, status, isCorrect, reviewState) => ({ type, latestSubmission: { status, isCorrect }, reviewState })
  const questions = [q('CHOICE', 'OBJECTIVE_CORRECT'), q('multiple_choice', 'OBJECTIVE_INCORRECT', false, 'RESOLVED'), q('coding', 'ACCEPTED', true), q('coding', 'WRONG_ANSWER', false), q('coding', 'SYSTEM_ERROR', false), q('coding', 'RUNNING', false), q('knowledge', 'SELF_REVIEW'), q('knowledge', 'REVIEWED', true), { type: 'true_false' }]
  assert.deepEqual(questions.map(item => reportQuestionOutcome(item).key), ['passed', 'failed', 'passed', 'failed', 'system', 'pending', 'self', 'unknown', 'unsubmitted'])
  const metrics = reportMetrics({ questionList: questions })
  assert.equal(metrics.evaluated, 4); assert.equal(metrics.correct, 2); assert.equal(metrics.score, 50)
  assert.equal(metrics.selfReview, 1); assert.equal(metrics.legacyReview, 1); assert.equal(metrics.submitted, 6)
  assert.equal(reportMetrics({ questionList: [] }).score, null)
  assert.equal(reportMetrics({ questionList: questions, statistics: { codingPassed: 0, codingFailed: 1 } }).correct, 1)
  const legacy = q('knowledge', 'NEEDS_REVIEW', false)
  assert.equal(canRetryReportQuestion(legacy), true)
  assert.equal(canRetryReportQuestion(legacy, 'RESOLVED'), false)
  assert.equal(reportQuestionOutcome(legacy).key, 'unknown')
  assert.equal(reportMetrics({ questionList: [legacy] }).evaluated, 0)
})

test('combined report filters preserve order, classify all objective types and sort failed rows without mutating input', () => {
  const rows = [{ q: { title: 'HashMap 空值', type: 'MULTIPLE_CHOICE', tags: ['Java'] }, index: 0, reviewState: 'RESOLVED', outcome: { key: 'failed' } }, { q: { title: 'HashMap 并发', type: 'choice' }, index: 1, reviewState: 'PENDING', outcome: { key: 'passed' } }, { q: { title: 'HashMap 顺序', type: 'true_false' }, index: 2, outcome: { key: 'failed' } }].map(row => ({ ...row, kind: reportQuestionKind(row.q) }))
  const filters = { search: 'HASHMAP', kind: 'objective', result: 'all', review: 'all', sort: 'original' }
  assert.deepEqual(filterReportQuestions(rows, filters).map(r => r.index), [0, 1, 2])
  assert.deepEqual(filterReportQuestions(rows, { ...filters, result: 'failed', review: 'RESOLVED' }).map(r => r.index), [0])
  assert.deepEqual(filterReportQuestions(rows, { ...filters, review: 'NONE' }).map(r => r.index), [2])
  assert.deepEqual(filterReportQuestions(rows, { ...filters, search: 'java' }).map(r => r.index), [0])
  assert.deepEqual(filterReportQuestions(rows, { ...filters, sort: 'failed' }).map(r => r.index), [0, 2, 1])
  assert.deepEqual(rows.map(r => r.index), [0, 1, 2])
})

test('report dates use Beijing time and elapsed time retains long durations and excludes invalid times', () => {
  assert.equal(formatPracticeDate('2026-10-09T14:07:00Z'), '2026-10-09 22:07')
  assert.equal(formatPracticeDate('2026-10-09 22:07:00'), '2026-10-09 22:07')
  assert.equal(formatPracticeDate(null), '—')
  assert.equal(formatPracticeDate('invalid'), '—')
  assert.equal(practiceElapsed('2026-10-09T12:00:00Z', '2026-10-09T13:02:03Z'), '1 小时 2 分 3 秒')
  assert.equal(practiceElapsed(null, null), '')
})
