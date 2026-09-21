<template>
  <div ref="host" class="editor-host" aria-label="Java 代码编辑器"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: String, default: '' }, highlightedLines: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue'])
const host = ref(null)
let monaco
let editor
let decorations = []

onMounted(async () => {
  ;[monaco] = await Promise.all([
    import('monaco-editor/esm/vs/editor/editor.api'),
    import('monaco-editor/esm/vs/basic-languages/java/java.contribution'),
  ])
  editor = monaco.editor.create(host.value, {
    value: props.modelValue,
    language: 'java',
    theme: 'vs-dark',
    automaticLayout: true,
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
  syncHighlights()
})

watch(() => props.modelValue, value => {
  if (editor && value !== editor.getValue()) editor.setValue(value)
})
watch(() => props.highlightedLines, syncHighlights, { deep: true })

function syncHighlights() {
  if (!editor || !monaco) return
  decorations = editor.deltaDecorations(decorations, props.highlightedLines.length ? [{
    range: new monaco.Range(props.highlightedLines[0], 1, props.highlightedLines[1], 1),
    options: { isWholeLine: true, className: 'follow-up-line' },
  }] : [])
}

onBeforeUnmount(() => editor?.dispose())
</script>

<style scoped>
.editor-host{width:100%;height:100%;min-height:320px;background:#17202a}
:deep(.follow-up-line){background:rgba(16,185,129,.11)}
</style>
