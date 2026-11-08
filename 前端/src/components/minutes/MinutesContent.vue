<template>
  <section class="minutes-content" :class="{ 'minutes-content--report': !compact }" aria-label="面试纪要内容">
    <p v-if="error" class="minutes-error" role="alert">{{ error }} <button type="button" @click="$emit('refresh')">重试</button></p>
    <p v-if="actionError" class="minutes-error" role="alert">{{ actionError }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="!meta" role="status">正在加载…</p>
    <p v-else-if="meta.lifecycle === 'DELETED'">纪要已删除。</p>
    <p v-else-if="meta.lifecycle === 'UNAVAILABLE'">本场没有纪要记录。</p>
    <template v-else>
      <div v-if="!compact" class="minutes-toolbar">
        <header class="minutes-heading">
          <h2>{{ tab === 'summary' ? 'AI 总结' : '面试记录' }}</h2>
          <span v-if="meta.coverage === 'PARTIAL' && tab !== 'summary'" class="minutes-scope">部分记录</span>
        </header>
        <div class="minutes-actions">
        <button class="minutes-copy" type="button" :disabled="!canExport" @click="$emit('export', kind, 'txt', true)">{{ tab === 'summary' ? '复制总结' : '复制对话' }}</button>
        <details class="minutes-menu" name="minutes-actions" @toggle="positionMenu"><summary>导出</summary><div>
          <button type="button" :disabled="!canExport" @click="$emit('export', kind, 'txt')">下载 TXT</button>
          <button type="button" :disabled="!canExport" @click="$emit('export', kind, 'md')">下载 Markdown</button>
        </div></details>
        <button v-if="tab === 'summary' && meta.lifecycle === 'FINALIZED' && meta.recordedEntryCount > 0" type="button" :aria-label="paragraph ? '重新生成总结' : '生成总结'" :disabled="busy || generating" @click="$emit('generate')">{{ generating ? '生成中…' : paragraph ? '重新生成' : '生成总结' }}</button>
        <details class="minutes-menu" name="minutes-actions" @toggle="positionMenu"><summary>更多</summary><div><button type="button" class="minutes-delete" :disabled="busy" @click="$emit('delete')">删除纪要与总结</button></div></details>
        </div>
      </div>
      <template v-if="!compact && tab === 'summary'">
        <p v-if="meta.lifecycle === 'ACTIVE'" class="minutes-muted">面试结束后生成总结。</p>
        <p v-else-if="generating" class="minutes-muted" role="status">DeepSeek 正在整理总结…</p>
        <p v-else-if="meta.summaryState === 'FAILED'" class="minutes-error">{{ summaryFailure }}</p>
        <p v-else-if="!paragraph" class="minutes-muted">暂无总结。</p>
        <p v-if="paragraph" class="minutes-summary-text">{{ paragraph }}</p>
        <p v-if="paragraph && meta.summaryState === 'FAILED'" class="minutes-muted">已保留上一份总结。</p>
      </template>
      <template v-else>
        <p v-if="!dialogueEntries.length" class="minutes-muted">暂无对话记录。</p>
        <article v-for="entry in dialogueEntries" :key="entry.id" :data-minutes-entry="entry.id" class="minutes-entry" :class="{ 'minutes-entry--candidate': entry.role === 'CANDIDATE' }">
          <header><strong>{{ speaker(entry) }}</strong></header>
          <p>{{ entry.text }}</p>
        </article>
        <button v-if="hasMore" type="button" @click="$emit('more')">加载更多对话</button>
      </template>
    </template>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { useUserStore } from '../../store/user'
import { summaryParagraph } from '../../utils/minutes'
const props = defineProps({ meta: Object, entries: Array, summary: Object, tab: String, error: String, actionError: String, notice: String, busy: Boolean, hasMore: Boolean, compact: Boolean })
defineEmits(['refresh', 'export', 'generate', 'more', 'delete'])
const user = useUserStore()
const kind = computed(() => props.tab === 'summary' ? 'summary' : 'record')
const dialogueEntries = computed(() => (props.entries || []).filter(e => ['MAIN', 'FOLLOWUP', 'ANSWER'].includes(e.entryType) && e.role !== 'SYSTEM'))
const paragraph = computed(() => summaryParagraph(props.summary))
const canExport = computed(() => !props.busy && (props.tab === 'summary' ? !!paragraph.value : dialogueEntries.value.length > 0))
const generating = computed(() => ['QUEUED', 'RUNNING'].includes(props.meta?.summaryState))
const summaryFailure = computed(() => ({ NOT_CONFIGURED: '请先在后端配置 DeepSeek API Key。', AUTH_FAILED: 'DeepSeek 认证失败，请检查密钥。', RATE_LIMITED: 'DeepSeek 服务繁忙，请稍后重试。' }[props.meta?.summaryError] || '总结生成失败，请重试。'))
function speaker(entry) {
  return entry.role === 'CANDIDATE' ? entry.speakerName || props.meta?.candidateName || user.nickname || '考生' : '面试官'
}
function positionMenu(event) {
  const detail = event.currentTarget, menu = detail.querySelector('div')
  if (!detail.open || !menu) return
  menu.style.transform = ''
  const rect = menu.getBoundingClientRect()
  menu.style.transform = `translateX(${Math.max(0, 16 - rect.left) - Math.max(0, rect.right - innerWidth + 16)}px)`
}
</script>
<style scoped>
.minutes-content{color:var(--neutral-800,#25352f);font-size:14px;line-height:1.8;overflow-wrap:anywhere}
.minutes-content--report{display:flow-root;position:relative;box-sizing:border-box;max-width:960px;margin:24px auto 0;padding:28px 36px 36px;background:var(--surface-elevated,#fff);border:1px solid var(--neutral-200,#dce5df);border-radius:16px}
.minutes-toolbar{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;padding-bottom:24px;margin-bottom:8px;border-bottom:1px solid var(--neutral-200,#dce5df)}
.minutes-heading{display:flex;align-items:center;gap:12px;min-height:44px}
.minutes-heading h2{margin:0;color:var(--neutral-900,#172d25);font-size:20px;font-weight:650;line-height:1.4;letter-spacing:-.02em}
.minutes-content button,.minutes-menu summary{min-height:44px;padding:8px 12px;border:1px solid var(--neutral-200,#dce5df);border-radius:8px;background:var(--surface-elevated,#fff);color:var(--accent-700,#047857);font:inherit;cursor:pointer;box-sizing:border-box}
.minutes-content button:hover,.minutes-menu summary:hover{background:var(--surface-soft,#f2f7f4)}
.minutes-content button:focus-visible,.minutes-menu summary:focus-visible{outline:2px solid #047857;outline-offset:2px}
.minutes-content button:disabled{opacity:.5;cursor:not-allowed}
.minutes-actions{display:flex;align-items:center;flex-wrap:wrap;gap:4px}
.minutes-actions>button,.minutes-actions>.minutes-menu>summary{border-color:transparent;background:transparent;color:var(--neutral-600,#53655b);font-size:13px;padding:8px 10px}
.minutes-actions>.minutes-copy{margin-right:4px;border-color:var(--neutral-200,#dce5df);color:var(--accent-700,#047857);background:var(--surface-elevated,#fff)}
.minutes-scope{color:var(--neutral-600,#53655b);font-size:12px;font-weight:400}
.minutes-menu{position:relative}.minutes-menu summary{list-style:none}.minutes-menu summary::-webkit-details-marker{display:none}
.minutes-menu>div{position:absolute;top:calc(100% + 4px);right:0;z-index:2;display:flex;flex-direction:column;gap:4px;min-width:150px;padding:6px;border:1px solid var(--neutral-200,#dce5df);border-radius:10px;background:var(--surface-elevated,#fff);box-shadow:0 8px 24px #183b2d12}
.minutes-menu>div button{border:0;white-space:nowrap;text-align:left}
.minutes-entry{padding:20px 0;border-bottom:1px solid var(--neutral-200,#e5e9e7)}
.minutes-entry:last-of-type{border-bottom:0;padding-bottom:0}
.minutes-entry header{font-size:13px;color:var(--accent-700,#047857)}
.minutes-entry p{white-space:pre-wrap;margin:8px 0 0}
.minutes-content--report .minutes-entry{display:grid;grid-template-columns:88px minmax(0,1fr);gap:24px;padding:24px 0}
.minutes-content--report .minutes-entry header{position:relative;padding-left:12px;line-height:1.9}
.minutes-content--report .minutes-entry header::before{content:'';position:absolute;left:0;top:7px;width:3px;height:14px;border-radius:2px;background:var(--accent-600,#059669)}
.minutes-content--report .minutes-entry--candidate header{color:var(--neutral-600,#53655b)}
.minutes-content--report .minutes-entry--candidate header::before{background:var(--neutral-300,#b9c7bf)}
.minutes-content--report .minutes-entry p{margin:0;font-size:15px;line-height:1.95}
.minutes-summary-text{max-width:68ch;margin:28px 0 8px;font-size:16px;line-height:2.1;text-align:start}
.minutes-muted{color:var(--neutral-600,#53655b);font-size:13px}.minutes-error,.minutes-content .minutes-delete{color:var(--color-error,#a33229)}
@media(max-width:767px){.minutes-content--report{margin-top:16px;padding:20px 18px 24px;border-radius:12px}.minutes-toolbar{flex-direction:column;align-items:flex-start;padding-bottom:16px;margin-bottom:0}.minutes-heading{min-height:32px}.minutes-heading h2{font-size:18px}.minutes-actions{gap:2px}.minutes-actions>button,.minutes-actions>.minutes-menu>summary{padding:8px;font-size:13px}.minutes-content--report .minutes-entry{grid-template-columns:minmax(0,1fr);gap:8px;padding:20px 0}.minutes-content--report .minutes-entry p{font-size:14px;line-height:1.9}.minutes-summary-text{margin-top:22px;font-size:15px;line-height:2}}
</style>
