import test from 'node:test'
import assert from 'node:assert/strict'
import { readPracticeDraft, writePracticeDraft, submissionState } from '../src/utils/practiceDrafts.js'

test('drafts remain isolated by user, session and question across reload', () => {
  const values = new Map()
  const storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) }
  writePracticeDraft(storage, 1, 10, { questions: { 11: { answer: '原理说明' }, 12: { code: 'class Main {}' } } })
  writePracticeDraft(storage, 2, 10, { questions: { 11: { answer: '其他用户' } } })
  assert.equal(readPracticeDraft(storage, 1, 10).questions[11].answer, '原理说明')
  assert.equal(readPracticeDraft(storage, 1, 10).questions[12].code, 'class Main {}')
  assert.equal(readPracticeDraft(storage, 2, 10).questions[11].answer, '其他用户')
  assert.deepEqual(readPracticeDraft(storage, 1, 20), {})
  assert.throws(() => readPracticeDraft(storage, null, 10))
})

test('storage failures surface instead of claiming saved; failed submissions count as submitted', () => {
  assert.throws(() => writePracticeDraft({ setItem() { throw new Error('quota') } }, 1, 10, {}))
  assert.throws(() => readPracticeDraft({ getItem: () => '{invalid' }, 1, 10))
  assert.throws(() => readPracticeDraft({ getItem: () => '{"questions":{"11":{"answer":42}}}' }, 1, 10))
  assert.equal(submissionState({ latestSubmission: { isCorrect: false } }, {}), '已提交')
  assert.equal(submissionState({ starter: 'template' }, { code: 'template' }), '未作答')
  assert.equal(submissionState({}, { answer: '回答' }), '草稿')
})
