import test from 'node:test'
import assert from 'node:assert/strict'
import { parseIceServers } from '../src/utils/iceServers.js'

test('parseIceServers 空值回退默认 STUN', () => {
  const fallback = parseIceServers('')
  assert.equal(fallback.length, 1)
  assert.match(fallback[0].urls, /^stun:/)
  assert.equal(parseIceServers(null).length, 1)
  assert.equal(parseIceServers('   ').length, 1)
})

test('parseIceServers 逗号分隔注入并去空白', () => {
  const list = parseIceServers(' stun:a.example.com:3478 , turn:b.example.com:3478 ')
  assert.deepEqual(list, [
    { urls: 'stun:a.example.com:3478' },
    { urls: 'turn:b.example.com:3478' },
  ])
})

test('parseIceServers 单条注入不回落默认', () => {
  const list = parseIceServers('stun:only.example.com:3478')
  assert.equal(list.length, 1)
  assert.equal(list[0].urls, 'stun:only.example.com:3478')
})
