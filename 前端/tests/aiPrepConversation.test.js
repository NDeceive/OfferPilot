import test from 'node:test'
import assert from 'node:assert/strict'
import { buildPrepHistory, isExplicitPrepChoice, prepUnavailableReply, prepFailureReason } from '../src/utils/aiPrepConversation.js'

test('history preserves both speakers and excludes the current user message and empty reply', () => {
  const messages = [
    { role: 'ai', text: '你做过什么项目？' },
    { role: 'user', text: '我做过一个商城，主要写页面。' },
    { role: 'ai', text: '你更喜欢交互还是业务逻辑？' },
    { role: 'user', text: '前者，适合什么岗位？' },
    { role: 'ai', text: '' },
  ]
  assert.deepEqual(buildPrepHistory(messages, '前者，适合什么岗位？'), [
    { role: 'assistant', content: '你做过什么项目？' },
    { role: 'user', content: '我做过一个商城，主要写页面。' },
    { role: 'assistant', content: '你更喜欢交互还是业务逻辑？' },
  ])
  assert.equal(messages.length, 5)
})

test('history stays within backend limits and retains button confirmations', () => {
  const messages = Array.from({ length: 30 }, (_, i) => ({ role: i % 2 ? 'ai' : 'user', text: `${i}:` + '长'.repeat(1600) }))
  const history = buildPrepHistory(messages)
  assert.equal(history.length, 20)
  assert.equal(history[0].content.startsWith('10:'), true)
  assert.ok(history.every(m => m.content.length === 1500))
  assert.deepEqual(buildPrepHistory([{ role: 'user', text: '确认选择 Web 前端' }]), [{ role: 'user', content: '确认选择 Web 前端' }])
})

test('offline keyword matches cannot turn exploration, denial or hesitation into choices', () => {
  for (const message of [
    'Java 后端怎么样', '我不会 Java', '前端和后端怎么选', '我想做算法但还没想好',
    '我不想做后端', '不要选 Java', '别换成前端', '我想了解 Java 后端', '先聊聊',
    '好', '继续', '你推荐吧', '我想选前端还是后端', '我想选算法但不确定适合不适合',
  ]) assert.equal(isExplicitPrepChoice(message), false, message)
  for (const message of ['就选 Java 后端', '换成 Web 前端', '我想面 Java 后端', '选择 BE-JAVA']) {
    assert.equal(isExplicitPrepChoice(message), true, message)
  }
})

test('AI outage answers the failure honestly instead of repeating the workflow instruction', () => {
  assert.match(prepUnavailableReply({ stage: 'UPLOAD', message: '我想再比较一下前后端' }), /不必现在做决定/)
  assert.doesNotMatch(prepUnavailableReply({ stage: 'UPLOAD', message: '我想再比较一下前后端' }), /上传|先选/)
  assert.match(prepUnavailableReply({ stage: 'GREET' }), /还没想好方向也没关系/)
})

test('retries keep the original user message once and omit failed assistant output', () => {
  assert.deepEqual(buildPrepHistory([
    { role: 'user', text: '前端做什么？' },
    { role: 'ai', text: '对话暂时无法回复', failed: true },
  ], '前端做什么？'), [])
  assert.equal(prepFailureReason({ code: 401 }), 'AUTH_REQUIRED')
  assert.equal(prepFailureReason({ code: 400 }), 'INVALID_REQUEST')
  assert.equal(prepFailureReason({ code: 503 }), 'SERVER_ERROR')
  assert.equal(prepFailureReason(new TypeError('Failed to fetch')), 'NETWORK_ERROR')
  assert.match(prepUnavailableReply({ reason: 'NETWORK_ERROR' }), /后端已启动.*重试回复/)
  assert.match(prepUnavailableReply({ reason: 'AUTH_REQUIRED' }), /重新登录/)
})
