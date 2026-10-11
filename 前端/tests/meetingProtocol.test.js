import test from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeCode,
  formatCodeInput,
  isValidCode,
  meetingWsUrl,
} from '../src/utils/meetingProtocol.js'

test('normalizeCode 大写并去掉空格与连字符', () => {
  assert.equal(normalizeCode(' 3f7k-2q9a '), '3F7K2Q9A')
  assert.equal(normalizeCode('abcd 2345'), 'ABCD2345')
  assert.equal(normalizeCode(null), '')
})

test('formatCodeInput 四位一组插连字符且打字过程不跳动', () => {
  assert.equal(formatCodeInput('3f7k2q9a'), '3F7K-2Q9A')
  assert.equal(formatCodeInput('3f7k'), '3F7K')
  assert.equal(formatCodeInput('3f7k-2q'), '3F7K-2Q')
  assert.equal(formatCodeInput('3F7K-2Q9A'), '3F7K-2Q9A')
})

test('isValidCode 拒绝歧义字符与长度不符', () => {
  assert.equal(isValidCode('3F7K-2Q9A'), true)
  assert.equal(isValidCode('iiii oooo'), false, 'I/O 不在字母表内')
  assert.equal(isValidCode('3F7K-2Q9'), false, '7 位不合法')
  assert.equal(isValidCode(''), false)
})

test('meetingWsUrl 相对 base 指向页面同源（dev 走 Vite /ws 代理）', () => {
  const url = meetingWsUrl('tok en/1', {
    base: '/api',
    location: { protocol: 'http:', host: 'localhost:5173' },
  })
  assert.equal(url, 'ws://localhost:5173/ws/meeting?token=tok%20en%2F1')
})

test('meetingWsUrl 在 https 页面把相对 base 升级为 wss', () => {
  const url = meetingWsUrl('t', {
    base: '/api',
    location: { protocol: 'https:', host: 'demo.example.com' },
  })
  assert.equal(url, 'wss://demo.example.com/ws/meeting?token=t')
})

test('meetingWsUrl 绝对 base 直连目标后端（运行时改过服务器地址时）', () => {
  assert.equal(
    meetingWsUrl('t', {
      base: 'http://192.168.1.5:8080/api',
      location: { protocol: 'http:', host: 'localhost:5173' },
    }),
    'ws://192.168.1.5:8080/ws/meeting?token=t',
  )
  assert.equal(
    meetingWsUrl('t', { base: 'https://api.example.com/api', location: null }),
    'wss://api.example.com/ws/meeting?token=t',
  )
})
