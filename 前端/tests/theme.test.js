import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveTheme, normalizeMode, THEME_MODES } from '../src/composables/useTheme.js'

test('浅色/深色是硬选择，不理会系统偏好', () => {
  assert.equal(resolveTheme('light', true), 'light')
  assert.equal(resolveTheme('light', false), 'light')
  assert.equal(resolveTheme('dark', true), 'dark')
  assert.equal(resolveTheme('dark', false), 'dark')
})

test('跟随系统按系统偏好取反', () => {
  assert.equal(resolveTheme('system', true), 'dark')
  assert.equal(resolveTheme('system', false), 'light')
})

test('未知取值一律回落跟随系统', () => {
  assert.equal(normalizeMode('dark'), 'dark')
  assert.equal(normalizeMode('light'), 'light')
  assert.equal(normalizeMode('system'), 'system')
  assert.equal(normalizeMode('weird'), 'system')
  assert.equal(normalizeMode(null), 'system')
  assert.equal(normalizeMode(undefined), 'system')
  assert.deepEqual(THEME_MODES, ['light', 'dark', 'system'])
})
