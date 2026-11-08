import test from 'node:test'
import assert from 'node:assert/strict'
import { clampOrb, minutesLabel, summaryParagraph } from '../src/utils/minutes.js'

test('server uncertainty never appears as recording or disabled confirmation', () => {
  const on = { lifecycle: 'ACTIVE', enabled: true }
  assert.equal(minutesLabel(on, false, 'timeout'), '状态待确认')
  assert.equal(minutesLabel(on, true, ''), '设置同步中')
  assert.equal(minutesLabel(on, false, ''), '正在记录')
  assert.equal(minutesLabel({ ...on, waitingForQuestion: true }, false, ''), '等待下一问记录')
  assert.equal(minutesLabel({ ...on, paused: true }, false, ''), '纪要随面试暂停')
})
test('summary status is distinct from recording and preserves failure visibility', () => {
  assert.equal(minutesLabel({ lifecycle: 'FINALIZED', summaryState: 'RUNNING' }, false, ''), '正在整理总结')
  assert.equal(minutesLabel({ lifecycle: 'FINALIZED', summaryState: 'FAILED', hasSummary: true }, false, ''), '总结生成失败')
  assert.equal(minutesLabel({ lifecycle: 'DELETED' }, false, ''), '纪要已删除')
})
test('orb moves freely on both axes and remains visible in a keyboard-sized viewport', () => {
  const v = { left: 0, top: 180, width: 360, height: 240 }
  for (const x of [-10, 0, .3, .5, 1, 100, NaN]) for (const y of [-10, 0, .5, 100, NaN]) {
    const p = clampOrb(x, y, v)
    assert.ok(p.x >= 16 && p.x + 56 <= 344)
    assert.ok(p.y >= 196 && p.y + 56 <= 404)
  }
  assert.equal(clampOrb(.5, .5, v).x, 152, 'horizontal midpoint must not snap to an edge')
  assert.equal(clampOrb(.5, .5, v).y, 272)
  assert.equal(clampOrb(1, 1, v, 48).x, 296, 'mobile ball can reach the right edge')
})

test('plain summaries display as one paragraph and old summaries remain readable', () => {
  assert.equal(summaryParagraph({ text: '第一句话。\n第二句话。' }), '第一句话。 第二句话。')
  assert.equal(summaryParagraph({ overview: [{ text: '旧版概括' }], topics: [{ text: '不重复展开分类' }] }), '旧版概括')
  assert.equal(summaryParagraph(null), '')
})
