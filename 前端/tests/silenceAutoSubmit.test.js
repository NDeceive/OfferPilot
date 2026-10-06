import test from 'node:test'
import assert from 'node:assert/strict'
import { createSilenceAutoSubmit } from '../src/composables/silenceAutoSubmit.js'

test('submits once after seven seconds from the last voiced frame', () => {
  const endpoint = createSilenceAutoSubmit()
  for (let i = 0; i < 7; i++) {
    assert.equal(endpoint.observe({ voiced: true, frameDurationMs: 40, now: i * 40 }), false)
  }
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 7239 }), false)
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 7240 }), true)
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 9000 }), false)
})

test('intermediate ASR cuts do not reset the answer timer', () => {
  const endpoint = createSilenceAutoSubmit()
  for (let i = 0; i < 7; i++) endpoint.observe({ voiced: true, frameDurationMs: 40, now: i * 40 })
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 2000 }), false)
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 7240 }), true)
})

test('continued speech postpones submission and short noise cannot submit', () => {
  const endpoint = createSilenceAutoSubmit()
  assert.equal(endpoint.observe({ voiced: true, frameDurationMs: 40, now: 0 }), false)
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 8000 }), false)
  for (let i = 0; i < 7; i++) endpoint.observe({ voiced: true, frameDurationMs: 40, now: 9000 + i * 40 })
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 16000 }), false)
  assert.equal(endpoint.observe({ voiced: true, frameDurationMs: 40, now: 16010 }), false)
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 23010 }), true)
})

test('reset cancels an old answer and permits the next answer', () => {
  const endpoint = createSilenceAutoSubmit()
  for (let i = 0; i < 7; i++) endpoint.observe({ voiced: true, frameDurationMs: 40, now: i * 40 })
  endpoint.reset()
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 8000 }), false)
  for (let i = 0; i < 7; i++) endpoint.observe({ voiced: true, frameDurationMs: 40, now: 9000 + i * 40 })
  assert.equal(endpoint.observe({ voiced: false, frameDurationMs: 40, now: 16240 }), true)
})
