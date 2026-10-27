<template>
  <div ref="host" class="editor-host" :aria-label="`${language} 代码编辑器`"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: String, default: '' }, language: { type: String, default: 'java' }, readOnly:Boolean, initialState: { type: Object, default: null }, highlightedLines: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const host = ref(null)
let monaco
let editor
let decorations = []
let disposed = false

onMounted(async () => {
  ;[monaco] = await Promise.all([
    import('monaco-editor/esm/vs/editor/editor.api'),
    import('monaco-editor/esm/vs/basic-languages/java/java.contribution'),
  ])
  if (disposed || !host.value) return
  editor = monaco.editor.create(host.value, {
    value: props.modelValue,
    language: props.language,
    theme: 'vs-dark',
    automaticLayout: true,
    readOnly: props.readOnly,
    minimap: { enabled: false },
    fontFamily: 'Cascadia Mono, Consolas, monospace',
    fontSize: 14,
    lineHeight: 23,
    padding: { top: 14 },
    scrollBeyondLastLine: false,
    smoothScrolling: true,
    tabSize: 4,
  })
  editor.onDidChangeModelContent(() => emit('update:modelValue', editor.getValue()))
  if (props.initialState) editor.restoreViewState(props.initialState)
  syncHighlights()
})

watch(() => props.modelValue, value => {
  if (editor && value !== editor.getValue()) editor.setValue(value)
})
watch(() => props.highlightedLines, syncHighlights, { deep: true })
watch(() => props.readOnly, value => editor?.updateOptions({readOnly:value}))

function syncHighlights() {
  if (!editor || !monaco) return
  decorations = editor.deltaDecorations(decorations, props.highlightedLines.length ? [{
    range: new monaco.Range(props.highlightedLines[0], 1, props.highlightedLines[1], 1),
    options: { isWholeLine: true, className: 'follow-up-line' },
  }] : [])
}

defineExpose({ getViewState: () => editor?.saveViewState() || null, layout: () => editor?.layout(), reveal: (line, column = 1) => { if(!editor) return; editor.setPosition({lineNumber:line,column}); editor.revealLineInCenter(line); editor.focus() } })
onBeforeUnmount(() => { disposed = true; editor?.dispose() })
</script>

<style scoped>
.editor-host{width:100%;height:100%;min-height:0;background:#17202a}
:deep(.follow-up-line){background:rgba(16,185,129,.11)}
</style>
