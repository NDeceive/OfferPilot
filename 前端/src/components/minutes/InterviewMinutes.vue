<template>
  <section v-if="sessionId" class="minutes-workspace" :aria-label="detail ? '面试复盘导航' : '面试纪要'">
    <nav v-if="detail" class="minutes-tabs" aria-label="面试复盘内容">
      <button v-for="item in detailTabs" :key="item.id" type="button" :aria-current="activeTab === item.id ? 'page' : undefined" @click="select(item.id)">{{ item.label }}</button>
    </nav>
    <MinutesContent v-if="detail && activeTab !== 'report' && !opened" v-bind="contentProps" v-on="contentEvents" />
    <MinutesOrb v-if="!detail" ref="orb" :label="label" :animated="animated" :open="opened" :panel-id="panelId" @open="openPanel" />
    <Teleport to="body">
      <dialog ref="dialog" :id="panelId" class="minutes-dialog" aria-labelledby="minutes-panel-title" @cancel.prevent="closePanel" @click.self="closePanel">
        <header><div><h2 id="minutes-panel-title">面试纪要</h2><p v-if="detail" aria-live="polite">{{ label }}</p></div><div class="minutes-header-actions"><input v-if="!detail && meta?.canControl" type="checkbox" role="switch" aria-label="本场面试纪要" :checked="meta.enabled" :disabled="busy || !!error" @change="minutes.control($event.target.checked)" /><button type="button" aria-label="收起纪要面板" @click="closePanel">收起</button></div></header>
        <nav v-if="detail" class="minutes-tabs" aria-label="纪要内容"><button type="button" :aria-current="activeTab !== 'summary' ? 'page' : undefined" @click="select('minutes')">会议记录</button><button type="button" :aria-current="activeTab === 'summary' ? 'page' : undefined" @click="select('summary')">AI 总结</button></nav>
        <div class="minutes-dialog-body"><MinutesContent v-if="opened" v-bind="contentProps" v-on="contentEvents" /></div>
        <footer v-if="detail"><button type="button" @click="orb?.resetPosition()">恢复悬浮球默认位置</button><span>收起面板不会关闭记录</span></footer>
      </dialog>
    </Teleport>
  </section>
</template>
<script setup>
import { ref, computed, toRef, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInterviewMinutes } from '../../composables/useInterviewMinutes'
import MinutesOrb from './MinutesOrb.vue'
import MinutesContent from './MinutesContent.vue'
const props = defineProps({ sessionId: [Number, String], detail: Boolean })
const route = useRoute(), router = useRouter(), opened = ref(false), notice = ref(''), dialog = ref(null), orb = ref(null)
const panelId = `minutes-panel-${Math.random().toString(36).slice(2)}`
const detailTabs = [{ id: 'report', label: '能力报告' }, { id: 'minutes', label: '面试记录' }, { id: 'summary', label: 'AI 总结' }]
const activeTab = computed(() => props.detail ? (['minutes', 'summary'].includes(route.query.tab) ? route.query.tab : 'report') : 'minutes')
const wantContent = computed(() => opened.value || (props.detail && activeTab.value !== 'report'))
const wantSummary = computed(() => props.detail && wantContent.value && activeTab.value === 'summary')
const minutes = useInterviewMinutes(toRef(props, 'sessionId'), wantContent, wantSummary)
const { meta, entries, summary, label, error, actionError, busy, hasMore } = minutes
const animated = computed(() => ['正在记录', '正在整理总结'].includes(label.value))
const contentProps = computed(() => ({ meta: meta.value, entries: entries.value, summary: summary.value, tab: activeTab.value === 'summary' ? 'summary' : 'minutes', error: error.value, actionError: actionError.value, busy: busy.value, notice: notice.value, hasMore: hasMore.value, compact: !props.detail }))
const contentEvents = { refresh: minutes.refresh, generate: minutes.generate, more: minutes.more, delete: remove, export: exportContent }
function select(value) { if (props.detail) return router.replace({ query: { ...route.query, tab: value === 'report' ? undefined : value } }) }
async function openPanel() {
  if (opened.value) return
  if (activeTab.value === 'report') select(meta.value?.hasSummary ? 'summary' : 'minutes')
  opened.value = true; await nextTick(); dialog.value?.showModal(); minutes.refresh()
}
function closePanel() { dialog.value?.close(); opened.value = false; orb.value?.focus() }
defineExpose({ openPanel })
async function remove() {
  if (window.confirm('删除本场纪要与 AI 总结？本场不能重新开启。原面试问答、录音和能力报告按各自规则保留。')) await minutes.remove()
}
async function exportContent(kind, format, copy) { notice.value = (await minutes.exportContent(kind, format, copy)) || '' }
watch(() => props.sessionId, () => { if (opened.value) closePanel(); notice.value = '' })
onBeforeUnmount(() => dialog.value?.close())
</script>
<style scoped>
.minutes-tabs{display:flex;gap:4px;padding:8px 0;margin:12px 0;border-bottom:1px solid var(--neutral-200,#dce5df)}.minutes-tabs button,.minutes-text-entry,.minutes-dialog header button,.minutes-dialog footer button{min-height:44px;padding:9px 12px;border:0;border-radius:8px;background:transparent;font:inherit;color:var(--neutral-600,#53655b);cursor:pointer}.minutes-tabs button[aria-current=page]{background:var(--surface-soft,#eaf5ee);color:var(--accent-700,#047857);font-weight:600}.minutes-tabs button:focus-visible,.minutes-text-entry:focus-visible,.minutes-dialog button:focus-visible{outline:2px solid #047857;outline-offset:2px}.minutes-workspace{font-size:14px}.minutes-dialog{box-sizing:border-box;position:fixed;inset:16px 16px 16px auto;margin:0;width:min(440px,calc(100vw - 32px));max-width:none;height:calc(100dvh - 32px);max-height:none;padding:0;border:1px solid var(--neutral-200,#dce5df);border-radius:16px;background:var(--surface-elevated,#fff);color:var(--neutral-800,#25352f)}.minutes-dialog[open]{display:flex;flex-direction:column}.minutes-dialog::backdrop{background:rgba(20,35,28,.24)}.minutes-dialog header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:20px 24px 8px}.minutes-dialog h2{margin:0;font-size:20px}.minutes-dialog header p{margin:4px 0;font-size:13px;color:var(--neutral-600,#53655b)}.minutes-dialog>.minutes-tabs{margin:0 24px}.minutes-dialog-body{min-height:0;flex:1;overflow:auto;padding:0 24px 24px;overscroll-behavior:contain}.minutes-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 16px max(8px,env(safe-area-inset-bottom));border-top:1px solid var(--neutral-200,#dce5df);font-size:12px}.minutes-dialog footer span{color:var(--neutral-600,#53655b)}@media(max-width:767px){.minutes-dialog{inset:auto 0 0;width:100%;height:82dvh;max-height:82dvh;border-radius:16px 16px 0 0}.minutes-dialog header{padding:16px 20px 8px}.minutes-dialog>.minutes-tabs{margin:0 20px}.minutes-dialog-body{padding:0 20px 20px}.minutes-dialog footer{flex-wrap:wrap}}
</style>

<style scoped>
.minutes-workspace>.minutes-tabs{justify-content:center;gap:24px;margin:20px auto 0;padding:0;max-width:960px}
.minutes-workspace>.minutes-tabs button{position:relative;min-height:52px;padding:12px 4px;border-radius:0;font-size:14px}
.minutes-workspace>.minutes-tabs button[aria-current=page]{background:transparent}
.minutes-workspace>.minutes-tabs button[aria-current=page]::after{content:'';position:absolute;bottom:-1px;left:0;right:0;height:3px;border-radius:3px 3px 0 0;background:var(--accent-600,#059669)}
@media(max-width:767px){.minutes-workspace>.minutes-tabs{justify-content:space-between;gap:12px;margin-top:8px}.minutes-workspace>.minutes-tabs button{font-size:14px;min-height:48px}}
.minutes-header-actions{display:flex;align-items:center;gap:12px}.minutes-header-actions input{appearance:none;position:relative;flex:none;width:44px;height:26px;margin:0;border:1px solid #9caea4;border-radius:99px;background:#e1e8e4;cursor:pointer}.minutes-header-actions input::before{content:'';position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:#fff}.minutes-header-actions input:checked{background:#047857;border-color:#047857}.minutes-header-actions input:checked::before{left:21px}.minutes-header-actions input:focus-visible{outline:2px solid #047857;outline-offset:3px}.minutes-header-actions input:disabled{opacity:.5;cursor:wait}
</style>
