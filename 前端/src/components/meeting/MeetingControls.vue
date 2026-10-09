<template>
  <div class="meeting-controls">
    <button
      type="button"
      class="ctrl-btn"
      :class="{ off: !micOn }"
      :disabled="!micAvailable"
      :title="micAvailable ? '' : '未检测到可用麦克风'"
      @click="$emit('toggle-mic')"
    >
      <svg v-if="micOn" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
        <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" />
      </svg>
      <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
        <path d="M19 10v2a7 7 0 0 1-14 0v-2" /><line x1="12" y1="19" x2="12" y2="23" />
        <line x1="1" y1="1" x2="23" y2="23" />
      </svg>
      <span>{{ micOn ? '麦克风' : '已静音' }}</span>
    </button>

    <button type="button" class="ctrl-btn" :class="{ off: !camOn }" @click="$emit('toggle-cam')">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="m23 7-7 5 7 5V7z" /><rect x="1" y="5" width="15" height="14" rx="2" />
        <line v-if="!camOn" x1="1" y1="1" x2="23" y2="23" />
      </svg>
      <span>{{ camOn ? '摄像头' : '已关闭' }}</span>
    </button>

    <div class="spacer"></div>

    <button type="button" class="ctrl-btn danger" @click="$emit('leave')">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
        <line x1="23" y1="1" x2="1" y2="23" />
      </svg>
      <span>挂断</span>
    </button>

    <button v-if="isHost" type="button" class="ctrl-btn danger-outline" @click="$emit('end')">
      <span>结束会议</span>
    </button>
  </div>
</template>

<script setup>
defineProps({
  micOn: { type: Boolean, default: true },
  camOn: { type: Boolean, default: true },
  micAvailable: { type: Boolean, default: true },
  isHost: { type: Boolean, default: false },
})

defineEmits(['toggle-mic', 'toggle-cam', 'leave', 'end'])
</script>

<style scoped>
.meeting-controls {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-xl);
}
.spacer { flex: 1; }
.ctrl-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  color: var(--neutral-700);
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  padding: 10px 16px;
  cursor: pointer;
  transition: all var(--duration-fast);
}
.ctrl-btn:hover:not(:disabled) { border-color: var(--neutral-300); background: var(--neutral-50); }
.ctrl-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ctrl-btn.off {
  background: var(--color-error-bg);
  border-color: #f3c1c1;
  color: var(--color-error);
}
.ctrl-btn.danger {
  background: var(--color-error);
  border-color: var(--color-error);
  color: #fff;
}
.ctrl-btn.danger:hover { background: #b91c1c; border-color: #b91c1c; }
.ctrl-btn.danger-outline {
  background: transparent;
  border-color: #f3c1c1;
  color: var(--color-error);
}
.ctrl-btn.danger-outline:hover { background: var(--color-error-bg); }
@media (max-width: 640px) {
  .ctrl-btn span { display: none; }
}
</style>
