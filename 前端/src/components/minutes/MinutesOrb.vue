<template>
  <Teleport to="body">
  <div class="minutes-orb" :class="{ active: animated, background: hidden, 'label-left': labelLeft }" :style="{ left: `${position.x}px`, top: `${position.y}px` }">
    <button ref="button" type="button" class="minutes-orb__button" :aria-label="`面试纪要：${label}`" :aria-expanded="open" :aria-controls="panelId" @click="click" @pointerdown="down" @pointermove="move" @pointerup="up" @pointercancel="cancel">
      <span class="minutes-orb__sphere" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M7 5h10v14H7zM10 9h4m-4 3h4m-4 3h2"/></svg></span>
      <span class="minutes-orb__label">{{ label }}</span>
    </button>
  </div>
  </Teleport>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { clampOrb } from '../../utils/minutes'
defineProps({ label: String, animated: Boolean, open: Boolean, panelId: String })
const emit = defineEmits(['open'])
const button = ref(null), position = ref({ x: 0, y: 160 }), hidden = ref(document.hidden), labelLeft = ref(true)
let xRatio = 1, yRatio = 0.25, drag = null, dragged = false
function viewport() { const v = window.visualViewport; return { left: v?.offsetLeft || 0, top: v?.offsetTop || 0, width: v?.width || innerWidth, height: v?.height || innerHeight } }
function place() {
  const v = viewport(), size = button.value?.offsetWidth || 56, p = clampOrb(xRatio, yRatio, v, size)
  labelLeft.value = p.x + size / 2 > v.left + v.width / 2
  position.value = p
}
function resetPosition() { xRatio = 1; yRatio = 0.25; save(); place() }
function focus() { button.value?.focus() }
defineExpose({ resetPosition, focus })
function save() { try { sessionStorage.setItem('offerpilot.minutes-orb', JSON.stringify({ xRatio, yRatio })) } catch { /* UI-only preference is optional. */ } }
function down(e) {
  if (e.button !== 0 || drag) return
  dragged = false
  drag = { x: e.clientX, y: e.clientY, originX: position.value.x, originY: position.value.y, pointerId: e.pointerId }
  e.currentTarget.setPointerCapture(e.pointerId)
}
function move(e) {
  if (!drag || e.pointerId !== drag.pointerId) return
  const dx = e.clientX - drag.x, dy = e.clientY - drag.y
  if (Math.hypot(dx, dy) > 8) dragged = true
  if (!dragged) return
  const v = viewport(), size = button.value?.offsetWidth || 56
  xRatio = Math.max(0, Math.min(1, (drag.originX + dx - v.left - 16) / Math.max(1, v.width - size - 32)))
  yRatio = Math.max(0, Math.min(1, (drag.originY + dy - v.top - 16) / Math.max(1, v.height - size - 32)))
  place()
}
function up(e) { if (!drag) return; if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId); drag = null; save() }
function cancel() { drag = null; dragged = true; place() }
function click(event) { if (dragged && event.detail !== 0) { dragged = false; return }; dragged = false; emit('open') }
function visibility() { hidden.value = document.hidden }
onMounted(() => { try { const p = JSON.parse(sessionStorage.getItem('offerpilot.minutes-orb')); if (p) { xRatio = Number.isFinite(p.xRatio) ? p.xRatio : p.edge === 'left' ? 0 : 1; yRatio = Number.isFinite(p.yRatio) ? p.yRatio : Number.isFinite(p.ratio) ? p.ratio : 0.25 } } catch { /* Use default. */ }; place(); window.addEventListener('resize', place); window.visualViewport?.addEventListener('resize', place); window.visualViewport?.addEventListener('scroll', place); document.addEventListener('visibilitychange', visibility) })
onBeforeUnmount(() => { window.removeEventListener('resize', place); window.visualViewport?.removeEventListener('resize', place); window.visualViewport?.removeEventListener('scroll', place); document.removeEventListener('visibilitychange', visibility); if (drag && button.value?.hasPointerCapture(drag.pointerId)) button.value.releasePointerCapture(drag.pointerId); drag = null })
</script>
<style scoped>
.minutes-orb{position:fixed;z-index:300;width:56px}.minutes-orb__button{display:flex;align-items:center;gap:8px;width:100%;padding:0;border:0;background:transparent;color:var(--neutral-800,#1f2937);font:inherit;cursor:grab;touch-action:none;text-align:left;user-select:none;-webkit-user-select:none}.minutes-orb__sphere{position:relative;display:grid;place-items:center;flex:0 0 56px;height:56px;border-radius:50%;background:#e4eee9;color:#38594c;isolation:isolate;overflow:hidden}.minutes-orb__sphere::before{content:'';position:absolute;inset:0;z-index:-1;background:conic-gradient(#10b981,#22d3ee,#10b981);opacity:0}.active .minutes-orb__sphere::before{opacity:1;animation:minutes-orbit 8s linear infinite}.minutes-orb__sphere svg{width:32px;height:32px;background:var(--surface-elevated,#fff);border-radius:50%;padding:5px;color:#047857;box-sizing:content-box}.minutes-orb__label{position:absolute;left:calc(100% + 8px);top:50%;transform:translateY(-50%);width:max-content;max-width:120px;box-sizing:border-box;pointer-events:none;font-size:12px;line-height:1.5;padding:6px 8px;background:var(--surface-elevated,#fff);border:1px solid var(--neutral-200,#dce5df);border-radius:8px}.label-left .minutes-orb__label{left:auto;right:calc(100% + 8px)}.minutes-orb__button:active{cursor:grabbing}.minutes-orb__button:focus-visible{outline:2px solid #047857;outline-offset:4px;border-radius:12px}.background .minutes-orb__sphere::before{animation-play-state:paused}@keyframes minutes-orbit{to{transform:rotate(360deg)}}@media(max-width:767px){.minutes-orb{width:48px}.minutes-orb__sphere{flex-basis:48px;height:48px}.minutes-orb__sphere svg{width:24px;height:24px}}@media(prefers-reduced-motion:reduce){.minutes-orb__sphere::before{animation:none!important}}
</style>
