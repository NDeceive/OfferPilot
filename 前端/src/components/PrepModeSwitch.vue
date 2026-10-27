<template>
  <button class="prep-mode-switch" type="button" @click="router.push({ path: mode === 'chat' ? '/jobs' : '/interview/ai', query: job ? { job } : {} })">
    {{ mode === 'chat' ? '切换到手动录入' : '切换到对话填写' }} <span aria-hidden="true">⇄</span>
  </button>
</template>

<script setup>
import { useRouter } from 'vue-router'
defineProps({ mode: String, job: String })
const router = useRouter()
</script>

<style scoped>
.prep-mode-switch{display:inline-flex;align-items:center;justify-content:center;gap:8px;flex-shrink:0;min-height:44px;padding:0 16px;border:1px solid var(--neutral-200);border-radius:8px;background:var(--surface-elevated);color:var(--accent-700);font:inherit;font-size:14px;font-weight:600;cursor:pointer}
.prep-mode-switch:hover{background:var(--surface-mint)}
.prep-mode-switch:focus-visible{outline:2px solid var(--accent-700);outline-offset:2px}
</style>
