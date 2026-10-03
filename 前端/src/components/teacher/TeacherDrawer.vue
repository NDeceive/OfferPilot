<template>
  <Teleport to="body"><Transition name="drawer" appear><div v-if="open" v-bind="$attrs" class="teacher-drawer-overlay" :class="{ 'centered-modal': modal }" @click.self="$emit('close')"><aside ref="panel" class="teacher-drawer" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1" @keydown="handleKey"><header><h2>{{ title }}</h2><button type="button" aria-label="关闭详情" @click="$emit('close')">关闭 ×</button></header><slot /></aside></div></Transition></Teleport>
</template>
<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
defineOptions({ inheritAttrs: false })
const props = defineProps({ title: String, open: { type: Boolean, default: true }, modal: { type: Boolean, default: true } })
const emit = defineEmits(['close']), panel = ref(null)
let previous, previousOverflow
function restore() { if (previousOverflow !== undefined) { document.body.style.overflow = previousOverflow; previousOverflow = undefined; previous?.focus() } }
watch(() => props.open, async open => { if (open) { previous = document.activeElement; previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; await nextTick(); if (props.open) panel.value?.focus() } else restore() }, { immediate: true })
onUnmounted(restore)
function handleKey(event) {
  if (event.key === 'Escape') emit('close')
  if (event.key !== 'Tab') return
  const items = [...panel.value.querySelectorAll('a[href],button:not([disabled]),input,select,textarea,[tabindex="0"]')]
  const first = items[0], last = items.at(-1)
  if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
</script>
<style scoped>
.teacher-drawer-overlay{position:fixed;inset:0;z-index:150;background:rgba(17,24,39,.3);display:flex;justify-content:flex-end}.teacher-drawer{width:min(560px,100vw);height:100%;overflow:auto;background:white;padding:28px;color:#111827}.teacher-drawer header{display:flex;justify-content:space-between;align-items:center;margin-bottom:28px;gap:16px}.teacher-drawer h2{font-size:20px}.teacher-drawer button{background:white;border:1px solid #e7e9e8;border-radius:8px;padding:10px;color:#047857;cursor:pointer}.teacher-drawer :deep(p){line-height:1.8;margin:12px 0}.teacher-drawer :deep(a){color:#047857}.teacher-drawer :deep(dl){display:grid;grid-template-columns:120px 1fr;gap:14px;margin:24px 0}.teacher-drawer :deep(dt){color:#6b7280}.teacher-drawer :deep(h3){margin:24px 0 12px}.teacher-drawer :deep(.drawer-actions){display:flex;gap:16px;flex-wrap:wrap;margin-top:28px}

.drawer-enter-active,.drawer-leave-active{transition:opacity 220ms ease-out}.drawer-enter-active .teacher-drawer,.drawer-leave-active .teacher-drawer{transition:transform 220ms cubic-bezier(.16,1,.3,1)}.drawer-enter-from,.drawer-leave-to{opacity:0}.drawer-enter-from .teacher-drawer,.drawer-leave-to .teacher-drawer{transform:translateX(28px)}
@media(prefers-reduced-motion:reduce){.drawer-enter-active,.drawer-leave-active,.drawer-enter-active .teacher-drawer,.drawer-leave-active .teacher-drawer{transition:none}}
.centered-modal{align-items:center;justify-content:center;padding:20px}.centered-modal .teacher-drawer{height:auto;max-height:85vh;width:min(520px,100%);border-radius:12px}.centered-modal.drawer-enter-from .teacher-drawer,.centered-modal.drawer-leave-to .teacher-drawer{transform:translateY(8px)}

/* Shared independent detail windows. */
.teacher-drawer-overlay.centered-modal{padding:24px;box-sizing:border-box}
.centered-modal .teacher-drawer{box-sizing:border-box;width:min(680px,100%);height:auto;max-height:calc(100dvh - 48px);padding:24px;border-radius:16px;font-size:14px;line-height:1.65;box-shadow:0 16px 48px rgba(17,24,39,.16);overscroll-behavior:contain;scrollbar-gutter:stable}
.centered-modal .teacher-drawer>header{position:sticky;top:-24px;z-index:1;background:white;padding:0 0 16px;margin-bottom:18px;border-bottom:1px solid #e5e9e7;align-items:center}
.teacher-drawer h2{margin:0;font-size:20px;line-height:1.45;font-weight:650;overflow-wrap:anywhere}
.teacher-drawer>header button{font:inherit;min-height:44px;padding:8px 14px;border-radius:10px;white-space:nowrap;flex-shrink:0}
.teacher-drawer button:hover{background:#f3f8f5}
.teacher-drawer :deep(p){font-size:14px;color:#526159;line-height:1.75;overflow-wrap:anywhere}
.teacher-drawer :deep(.la-drill){display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px 18px;align-items:center;padding:16px 0;border-top:1px solid #e5e9e7}
.teacher-drawer :deep(.la-drill>b){font-size:15px;font-weight:600;grid-column:1}
.teacher-drawer :deep(.la-drill>p){margin:0;grid-column:1;font-size:13px}
.teacher-drawer :deep(.la-button){display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:8px 12px;box-sizing:border-box;border:1px solid #dce5df;border-radius:8px;color:#047857;background:white;text-decoration:none;font-size:13px}
.teacher-drawer :deep(.la-drill>.la-button){grid-column:2}
.teacher-drawer :deep(.la-drill>.la-button:first-of-type){grid-row:1/3}
.teacher-drawer :deep(.la-button:hover){background:#ecfdf5}
.teacher-drawer :deep(.drawer-actions a){display:inline-flex;align-items:center;min-height:44px;padding:8px 12px;border:1px solid #dce5df;border-radius:8px;text-decoration:none;box-sizing:border-box}
.teacher-drawer :deep(:is(button,a,input,select,textarea):focus-visible){outline:2px solid #059669;outline-offset:3px}
@media(max-width:600px){.teacher-drawer-overlay.centered-modal{padding:12px}.centered-modal .teacher-drawer{padding:20px;max-height:calc(100dvh - 24px)}.centered-modal .teacher-drawer>header{top:-20px;gap:12px}.teacher-drawer h2{font-size:18px}.teacher-drawer :deep(dl){grid-template-columns:1fr;gap:8px}.teacher-drawer :deep(dd){margin:0 0 12px}.teacher-drawer :deep(.la-drill){grid-template-columns:1fr}.teacher-drawer :deep(.la-drill>.la-button),.teacher-drawer :deep(.la-drill>.la-button:first-of-type){grid-column:1;grid-row:auto;justify-self:start;margin-top:4px}}
</style>
