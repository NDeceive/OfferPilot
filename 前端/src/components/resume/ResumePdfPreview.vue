<template>
  <div class="pdf-preview">
    <div class="page-controls">
      <button type="button" :disabled="page <= 1 || loading" @click="page--">
        上一页</button
      ><span>第 {{ page }} 页 / 共 {{ pageCount || "—" }} 页</span
      ><button
        type="button"
        :disabled="page >= pageCount || loading"
        @click="page++"
      >
        下一页
      </button>
    </div>
    <p v-if="error" role="alert">{{ error }}，请刷新预览重试。</p>
    <p v-if="loading" role="status">正在渲染 PDF…</p>
    <div ref="surface" class="page-surface">
      <canvas ref="canvas" role="img" :aria-label="`简历 PDF 第 ${page} 页`" />
    </div>
    <details v-if="text">
      <summary>读取本页文字</summary>
      <p class="page-text">{{ text }}</p>
    </details>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from "vue";
import { getDocument, GlobalWorkerOptions } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";
GlobalWorkerOptions.workerSrc = workerUrl;
const props = defineProps({
  src: String,
  zoom: { type: Number, default: 100 },
});
const page = ref(1),
  pageCount = ref(0),
  loading = ref(false),
  error = ref(""),
  text = ref("");
const surface = ref(null),
  canvas = ref(null);
let document = null,
  task = null,
  renderTask = null,
  observer = null,
  revision = 0,
  renderRevision = 0;
async function render() {
  if (!document || !canvas.value || !surface.value) return;
  const token = ++renderRevision;
  const previous = renderTask;
  previous?.cancel();
  if (previous) {
    try {
      await previous.promise;
    } catch {
      /* cancelled render has released the canvas */
    }
  }
  if (token !== renderRevision) return;
  loading.value = true;
  error.value = "";
  try {
    const pdfPage = await document.getPage(page.value);
    if (token !== renderRevision) return;
    const natural = pdfPage.getViewport({ scale: 1 });
    const width = Math.max(200, surface.value.clientWidth - 32);
    const scale = ((width / natural.width) * props.zoom) / 100;
    const viewport = pdfPage.getViewport({ scale });
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    const target = canvas.value;
    target.width = Math.ceil(viewport.width * ratio);
    target.height = Math.ceil(viewport.height * ratio);
    target.style.width = `${viewport.width}px`;
    target.style.height = `${viewport.height}px`;
    renderTask = pdfPage.render({
      canvasContext: target.getContext("2d"),
      viewport,
      transform: [ratio, 0, 0, ratio, 0, 0],
    });
    await renderTask.promise;
    const content = await pdfPage.getTextContent();
    if (token === renderRevision)
      text.value = content.items
        .map((item) => item.str + (item.hasEOL ? "\n" : " "))
        .join("");
  } catch (e) {
    if (token === renderRevision && e.name !== "RenderingCancelledException")
      error.value = "PDF 渲染失败";
  } finally {
    if (token === renderRevision) loading.value = false;
  }
}
async function load() {
  const token = ++revision;
  ++renderRevision;
  renderTask?.cancel();
  task?.destroy();
  document = null;
  page.value = 1;
  pageCount.value = 0;
  text.value = "";
  loading.value = true;
  error.value = "";
  try {
    task = getDocument({ url: props.src, isEvalSupported: false });
    const result = await task.promise;
    if (token !== revision) {
      result.destroy();
      return;
    }
    document = result;
    pageCount.value = result.numPages;
    await nextTick();
    await render();
  } catch (e) {
    if (token === revision) {
      error.value = "PDF 加载失败";
      loading.value = false;
    }
  }
}
watch(() => props.src, load);
watch([page, () => props.zoom], render);
onMounted(() => {
  observer = new ResizeObserver(() => render());
  observer.observe(surface.value);
  load();
});
onUnmounted(() => {
  ++revision;
  ++renderRevision;
  observer?.disconnect();
  renderTask?.cancel();
  task?.destroy();
});
</script>

<style scoped>
.pdf-preview {
  width: 100%;
  min-width: 0;
}
.page-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #4f655a;
}
.page-controls button {
  min-height: 36px;
  padding: 7px 10px;
  border: 1px solid #bdddcf;
  border-radius: 6px;
  background: white;
  color: #087f5b;
  cursor: pointer;
}
.page-controls button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.page-controls button:focus-visible {
  outline: 2px solid #078660;
  outline-offset: 3px;
}
.page-surface {
  width: 100%;
  overflow: auto;
  padding: 16px 0 24px;
  display: block;
  text-align: center;
  max-height: 820px;
}
.page-surface canvas {
  display: block;
  margin: 0 auto;
  background: white;
  box-shadow: 0 8px 24px #203c341c;
}
.pdf-preview > p {
  font-size: 13px;
  color: #5b7064;
}
.pdf-preview details {
  margin-top: 12px;
  font-size: 12px;
  color: #4f655a;
}
.pdf-preview summary {
  cursor: pointer;
}
.page-text {
  white-space: pre-wrap;
  line-height: 1.65;
}
@media (max-width: 620px) {
  .page-controls {
    gap: 8px;
  }
  .page-surface {
    max-height: 650px;
  }
}
</style>
