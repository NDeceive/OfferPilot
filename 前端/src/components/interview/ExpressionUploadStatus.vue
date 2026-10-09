<template>
  <div v-if="state.pending || state.rejected || state.storageError || state.authRequired" class="expression-upload" role="status">
    <p v-if="state.pending">{{ state.saving ? '正在补传表情记录' : '待补传表情记录' }} · {{ state.pending }} 条。面试可以正常结束。</p>
    <p v-if="state.storageError">本地缓存不可用，请保持页面开启以便上传。</p>
    <p v-if="state.authRequired">登录已过期，重新登录后可继续补传。</p>
    <p v-else-if="state.error">{{ state.error }}，记录已保留。</p>
    <button v-if="state.pending && !state.authRequired" type="button" :disabled="state.saving" @click="flush">{{ state.saving ? '上传中' : '重试保存' }}</button>
    <details v-if="state.rejected"><summary>{{ state.rejected }} 条异常记录已隔离，不影响其他样本</summary>
      <ul><li v-for="(item,index) in state.rejectedSamples" :key="index">第 {{ item.sample?.roundNo || '—' }} 题：{{ item.reason }}</li></ul>
      <button type="button" @click="clearRejected">清除这些异常记录</button>
    </details>
  </div>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { saveExpressionSamples } from '../../api'
import { createExpressionQueue } from '../../utils/expressions'
const props = defineProps({ sessionId: Number })
const emit = defineEmits(['uploaded'])
const state = ref({ pending: 0, rejected: 0, rejectedSamples: [] })
let queue
watch(() => props.sessionId, sid => {
  if (!sid) return
  let ownerId
  try { ownerId = localStorage.getItem('userId') } catch {}
  queue = createExpressionQueue(sid, saveExpressionSamples, undefined, { ownerId, onState: value => { state.value = value } })
  flush()
}, { immediate: true })
async function flush() {
  const pending = queue?.size || 0
  try { await queue?.flush(); if (pending) emit('uploaded') } catch { /* status retains the error and cache */ }
}
function add(sample) { queue?.add(sample) }
function clearRejected() { queue?.clearRejected() }
const timer = setInterval(flush, 10000)
onBeforeUnmount(() => { clearInterval(timer); flush() })
defineExpose({ add, flush, state })
</script>
<style scoped>
.expression-upload { color:var(--neutral-700,#374151); font-size:14px; line-height:1.6; padding:12px 0; }
p { margin:4px 0; } ul { padding-left:20px; max-height:140px; overflow:auto; }
button { min-height:44px; padding:8px 12px; margin-top:8px; border:1px solid var(--neutral-300,#d1d5db); border-radius:8px; background:var(--surface-primary,#fff); color:inherit; font:inherit; cursor:pointer; }
button:disabled { opacity:.6; cursor:wait; } summary { padding:10px 0; min-height:44px; cursor:pointer; }
button:focus-visible,summary:focus-visible { outline:2px solid var(--accent-600,#059669); outline-offset:3px; }
</style>
