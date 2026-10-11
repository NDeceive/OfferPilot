<template>
  <div class="advice-wrap">
    <p v-if="loading" class="advice-hint">加载中…</p>
    <p v-else-if="error" class="advice-hint is-error">{{ error }}</p>
    <p v-else-if="!list.length" class="advice-hint">{{ emptyText }}</p>
    <ul v-else class="advice-list">
      <li v-for="item in list" :key="item.id" class="advice-item">
        <div class="advice-head">
          <b>{{ item.authorName || '面试官' }}</b>
          <span class="advice-time">{{ formatTime(item.createdAt) }}</span>
        </div>
        <p class="advice-content">{{ item.content }}</p>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { formatTime } from '../../utils/time'

defineProps({
  list: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyText: { type: String, default: '还没有留下建议。' },
})
</script>

<style scoped>
.advice-hint { font-size: var(--text-sm); color: var(--neutral-400); padding: var(--space-3) 0; }
.advice-hint.is-error { color: var(--color-error); }
.advice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.advice-item { border-left: 3px solid var(--accent-200); padding-left: var(--space-3); }
.advice-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-1);
  font-size: var(--text-xs);
  color: var(--neutral-400);
}
.advice-head b { color: var(--neutral-700); font-size: var(--text-sm); }
.advice-content {
  font-size: var(--text-sm);
  color: var(--neutral-700);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>
