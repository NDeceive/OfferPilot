import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const source = readFileSync(new URL('../src/views/ResumeBuilder.vue', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const body = source.match(/async function verifySelectedSuggestions\(\) \{([\s\S]*?)\n\}\nasync function loadSuggestions/)[1];
const ref = (value) => ({ value });
async function run(failAt = 0, confirm = true) {
  const items = [1, 2, 3].map(n => ({ title: `事实${n}`, factText: `原始事实${n}` }));
  const state = { batchVerifying: ref(false), selectedSuggestions: ref([...items]), error: ref(''), message: ref(''), claims: ref([]) };
  let calls = 0;
  const fn = new Function(...Object.keys(state), 'window', 'emptyClaim', 'addResumeClaim', 'discardSuggestion', `return (async () => {${body}})()`);
  await fn(...Object.values(state), { confirm: () => confirm }, () => ({}), async payload => {
    calls++;
    if (calls === failAt) throw Error('网络中断');
    assert.equal(payload.verificationStatus, 'CONFIRMED');
    assert.equal(payload.resumeText, payload.factText);
    return { ...payload, id: calls };
  }, item => items.splice(items.indexOf(item), 1));
  assert.equal(state.batchVerifying.value, false);
  return { state, items, calls };
}
test('批量核对保存全部选中事实', async () => {
  const { state, items } = await run();
  assert.equal(state.claims.value.length, 3);
  assert.equal(items.length, 0);
});
test('部分失败保留未保存事实，成功事实不重复', async () => {
  const { state, items, calls } = await run(2);
  assert.equal(state.claims.value.length, 1);
  assert.equal(items.length, 2);
  assert.equal(calls, 2);
  assert.equal(state.error.value, '网络中断');
});
test('取消核对不保存事实', async () => {
  const { items, calls } = await run(0, false);
  assert.equal(calls, 0);
  assert.equal(items.length, 3);
});

