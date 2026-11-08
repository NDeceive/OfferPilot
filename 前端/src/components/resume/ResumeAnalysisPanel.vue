<template>
  <section class="analysis" aria-labelledby="analysis-title">
    <header class="analysis__head">
      <div>
        <h2 id="analysis-title">简历分析</h2>
        <p>从简历原文出发，明确修改重点与面试准备方向。</p>
      </div>
      <button
        v-if="jobId && available"
        type="button"
        :disabled="busy"
        @click="run(true)"
      >
        {{ busy ? "分析中…" : report ? "重新分析" : "开始分析" }}
      </button>
    </header>
    <p v-if="!jobId" class="analysis__notice">
      请先选择目标岗位，才能分析岗位关联。
    </p>
    <p v-else-if="!available" class="analysis__notice">
      本轮没有简历，可以返回上传／选择版本，也可以跳过分析继续准备。
    </p>
    <p v-else-if="busy" role="status" class="analysis__notice">
      {{
        analyzing
          ? "正在对照简历与岗位要求，并根据技能标签从题库智能选题，请耐心等待。你可以离开此页，报告完成后会保存。"
          : "正在读取对应简历的报告…"
      }}
    </p>
    <p v-if="notice" class="analysis__notice" role="status">{{ notice }}</p>
    <p v-if="error" role="alert" class="analysis__error">
      {{ error }}
      <button type="button" :disabled="busy" @click="run(true)">重试</button>
    </p>
    <template v-if="report && !busy">
      <div class="analysis__meta">
        <strong>{{ report.snapshot.jobName }}</strong
        ><span>{{ report.snapshot.title }}</span
        ><span>{{ date(report.createdAt) }}</span
        ><span
          >{{ report.source === "AI" ? "AI 分析" : "基础规则检查" }} ·
          {{
            report.status === "COMPLETED"
              ? "已完成"
              : report.status === "PARTIAL"
              ? "部分完成"
              : "未完成"
          }}</span
        >
      </div>
      <div v-if="report.failureReason" class="analysis__notice analysis__notice--warning" role="status">
        <strong>{{ report.status === 'PARTIAL' ? '分析部分完成' : '分析未完整完成' }} · 当前{{ report.source === 'AI' ? '展示已校验的分析内容' : '展示基础规则检查' }}</strong>
        <p>{{ report.failureReason }}</p>
      </div>
      <p v-if="!report.applicable" class="analysis__notice">
        简历或岗位已在其他页面变化，此报告保留的是旧输入；请重新分析后再修改或采用训练建议。
      </p>
      <p v-if="report.status === 'PROCESSING'" class="analysis__notice">
        先前请求尚未完成。可以稍后重新进入；若请求中断，请重新分析。
      </p>
      <p
        v-else-if="report.status === 'FAILED'"
        class="analysis__error"
        role="alert"
      >
        本次报告未完成，简历未被修改。请使用上方“重新分析”重试。
      </p>
      <template v-else>
        <InterviewPredictions v-if="interviews.length" :items="interviews" :applicable="report.applicable" :job-name="report.snapshot.jobName" @practice="$emit('practice', report)" />
        <section class="analysis__results" aria-labelledby="analysis-results-title">
          <h3 id="analysis-results-title">本次结论</h3>
          <ol v-if="conclusions.length" class="analysis__results-list">
            <li v-for="(entry, index) in conclusions" :key="index">
              <div class="analysis__result-title"><span>{{ entry.category }}</span><strong>{{ entry.item.title }}</strong></div>
              <p v-if="entry.item.action">{{ entry.item.action }}</p>
            </li>
          </ol>
          <p v-else class="analysis__muted">{{ report.summary || '暂无明确结论，请重新分析。' }}</p>
        </section>

      </template>
    </template>
    <div
      v-else-if="jobId && available && !busy && !error"
      class="analysis__empty"
    >
      <h3>尚未分析当前简历</h3>
      <p>分析基于已保存内容，保留原文和修改依据，不会自动修改简历。</p>
      <button type="button" class="analysis__primary" @click="run(false)">开始分析</button>
    </div>
        <footer v-if="preparationFlow || (report && !busy && !['FAILED', 'PROCESSING'].includes(report.status))" class="analysis__actions">
          <button v-if="preparationFlow" type="button" @click="$emit('previous')">← 上一步</button>
          <span v-if="preparationFlow" class="analysis__action-hint">核对建议后，继续选择本轮训练目标</span>
          <button
            v-if="report && !['FAILED', 'PROCESSING'].includes(report.status)"
            type="button"
            :disabled="busy || !report.applicable"
            @click="edit()"
          >
            去修改简历</button
          ><button
            v-if="report && !['FAILED', 'PROCESSING'].includes(report.status)"
            type="button"
            :class="{ analysis__primary: !preparationFlow }"
            :disabled="busy || !report.applicable || !report.suggestedModules?.length"
            @click="$emit('recommend', report)"
          >
            按建议选择训练目标</button
          ><small v-if="!preparationFlow && !report.suggestedModules?.length"
            >暂无有效模块建议，可以手动选择。</small
          >
          <button v-if="preparationFlow" type="button" class="btn btn--primary analysis__primary" @click="$emit('next')">下一步：训练目标 →</button>
        </footer>

  </section>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from "vue";
import { computed } from "vue";
import InterviewPredictions from "./InterviewPredictions.vue";
import { getResumeAnalysis, createResumeAnalysis } from "../../api";
import { groupAnalysisItems } from "../../utils/resumeAnalysis";
const props = defineProps({
  jobId: Number,
  versionId: Number,
  available: Boolean,
  sourceKey: { type: [String, Number], default: "" },
  preparationFlow: Boolean,
});
const emit = defineEmits(["report", "edit", "recommend", "previous", "next", "practice"]);
const report = ref(null),
  busy = ref(false),
  analyzing = ref(false),
  error = ref(""),
  notice = ref("");
let sequence = 0;
const conclusions = computed(() => {
  const groups = groupAnalysisItems(report.value?.items || []);
  return [
    ["优势方面", groups.strengths],
    ["待提升", groups.improvements],
    ["岗位待补充", groups.jobGaps],
  ].flatMap(([category, items]) => items.map(item => ({ category, item })));
});
const interviews = computed(
  () => report.value?.items.filter((i) => i.kind === "INTERVIEW") || []
);
const date = (v) => (v ? new Date(v).toLocaleString("zh-CN") : "");
function edit(item) {
  if (!report.value?.applicable) {
    notice.value = "此报告输入已变化，请重新分析";
    return;
  }
  emit("edit", { report: report.value, item });
}
watch(
  () => [props.jobId, props.versionId, props.available, props.sourceKey],
  async () => {
    const request = ++sequence;
    notice.value = report.value
      ? "岗位或简历已变化，已移除旧报告，请核对当前对象。"
      : "";
    report.value = null;
    emit("report", null);
    error.value = "";
    busy.value = false;
    if (!props.jobId || !props.available) return;
    busy.value = true;
    analyzing.value = false;
    try {
      const value = await getResumeAnalysis({
        jobId: props.jobId,
        versionId: props.versionId || undefined,
      });
      if (request === sequence) {
        report.value = value;
        emit("report", value);
      }
    } catch (e) {
      if (request === sequence) error.value = e.message || "报告读取失败";
    } finally {
      if (request === sequence) busy.value = false;
    }
  },
  { immediate: true }
);
async function run(force) {
  if (busy.value || !props.jobId || !props.available) return;
  const request = ++sequence;
  busy.value = true;
  analyzing.value = true;
  error.value = "";
  notice.value = "";
  try {
    const value = await createResumeAnalysis({
      jobId: props.jobId,
      versionId: props.versionId || null,
      force,
    });
    if (request === sequence) {
      report.value = value;
      emit("report", value);
    }
  } catch (e) {
    if (request === sequence)
      error.value =
        e.response?.data?.message ||
        e.message ||
        "分析失败，原文未修改，请重试";
  } finally {
    if (request === sequence) {
      busy.value = false;
      analyzing.value = false;
    }
  }
}
onBeforeUnmount(() => { ++sequence; });
</script>

<style scoped>
.analysis {
  color: #20332c;
  min-width: 0;
  line-height: 1.65;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.analysis__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}
.analysis h2 {
  font-size: 24px;
  margin: 0;
}
.analysis h3 {
  font-size: 18px;
  margin: 0 0 14px;
}
.analysis h4 {
  font-size: 14px;
  margin: 0 0 8px;
}
.analysis p {
  margin: 8px 0;
}
.analysis__eyebrow {
  color: #16825e;
  font-size: 12px;
  letter-spacing: 0.04em;
}
.analysis__head p:not(.analysis__eyebrow),
.analysis__muted,
.analysis small {
  color: #66776f;
}
.analysis button:not(.btn) {
  font: inherit;
  min-height: 44px;
  padding: 8px 16px;
  border: 1px solid #d6e3dd;
  background: var(--surface-elevated);
  color: #167953;
  border-radius: 9px;
  cursor: pointer;
}
.analysis button:disabled {
  opacity: 0.55;
  cursor: default;
}
.analysis button:focus-visible,
.analysis summary:focus-visible {
  outline: 3px solid #9adfc3;
  outline-offset: 3px;
}
.analysis .analysis__primary {
  background: var(--accent-600, #059669);
  color: #ffffff;
  border-color: transparent;
}
.analysis__notice {
  padding: 12px 16px;
  background: var(--surface-soft);
  border-radius: 9px;
}
.analysis__error {
  padding: 12px;
  color: #a13927;
  background: var(--surface-soft);
  border-radius: 9px;
}
.analysis__notice--warning { margin-top: 20px; background: var(--surface-soft); color: #714516; padding: 16px 18px; }
.analysis__notice--warning p { color: #805424; font-size: 13px; margin-bottom: 0; }
.analysis button:hover:not(:disabled) { background: var(--surface-soft); }
.analysis .analysis__primary:hover:not(:disabled) { background: var(--accent-600, #059669); }
.analysis__meta {
  display: flex;
  gap: 8px 20px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #62756c;
  padding-bottom: 16px;
  border-bottom: 1px solid #e3ebe6;
}
.analysis__summary {
  padding: 24px 0;
}
.analysis__columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: stretch;
  gap: 20px;
  margin-bottom: 24px;
}
.analysis__card {
  background: var(--surface-elevated);
  border: 1px solid #e0e9e4;
  border-radius: 12px;
  padding: 24px;
  min-width: 0;
  min-height: 230px;
}
.analysis__category-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.analysis .analysis__category-head h3 { margin: 0; font-size: 18px; }
.analysis__count { flex-shrink: 0; font-size: 12px; background: var(--surface-soft); color: #087b57; padding: 3px 9px; border-radius: 5px; }
.analysis .analysis__category-description { font-size: 12px; color: #60716a; margin: 9px 0 18px; }
.analysis__category-empty { padding-top: 14px; border-top: 1px solid #e3ebe6; color: #60716a; }
.analysis__category-empty small { display: block; font-size: 12px; }
.analysis small.analysis__priority { display: block; width: fit-content; color: #805424; background: var(--surface-soft); padding: 2px 7px; margin-top: 5px; border-radius: 4px; font-size: 11px; }
.analysis h3 span {
  font-size: 13px;
  color: #6e7b73;
  font-weight: 400;
  margin-left: 8px;
}
.analysis__item + .analysis__item {
  border-top: 1px solid #e9eeeb;
  padding-top: 10px;
  margin-top: 10px;
}
.analysis blockquote {
  white-space: pre-wrap;
  margin: 10px 0;
  padding: 10px 14px;
  background: var(--surface-soft);
  border-left: 2px solid #a8c9b8;
  font-size: 13px;
  color: #43534a;
  max-height: 240px;
  overflow: auto;
}
.analysis__actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e3ebe6;
  align-items: center;
}
.analysis__action-hint { flex: 1; color: #60716a; font-size: 12px; }
.analysis__empty {
  text-align: center;
  padding: 56px 20px;
  background: var(--surface-soft);
  border: 1px dashed #d9e6de;
  border-radius: 12px;
}
.analysis__empty p {
  margin-bottom: 24px;
}
@media (max-width: 760px) {
  .analysis__head {
    align-items: flex-start;
    gap: 12px;
  }
  .analysis__head button {
    flex-shrink: 0;
    padding: 8px 10px;
  }
  .analysis__columns,
  .analysis__comparison {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .analysis__card {
    padding: 18px;
    min-height: 0;
  }
  .analysis__details {
    padding: 16px;
  }
  .analysis h2 {
    font-size: 21px;
  }
  .analysis__actions button {
    flex: 1;
  }
  .analysis__action-hint { flex-basis: 100%; order: -1; }
  .analysis__empty {
    padding: 32px 16px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .analysis * {
    transition: none !important;
  }
}

.analysis__card { min-width: 0; }
.analysis__list { margin: 0; padding: 12px 0 0 22px; list-style: decimal; border-top: 1px solid #e3ebe6; }
.analysis__list > li { padding-left: 4px; }
.analysis__list > li::marker { color: #168660; font-weight: 700; }
@media (min-width: 761px) and (max-width: 1000px) { .analysis__columns { gap: 12px; } .analysis__card { padding: 20px 14px; } .analysis .analysis__category-head h3 { font-size: 15px; } }
.analysis__row { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.analysis__row > span { min-width: 0; line-height: 1.6; }
.analysis .analysis__detail-button { flex: 0 0 auto; padding: 4px 6px; min-height: 44px; min-width: 44px; border: 0; border-radius: 4px; background: transparent; font-size: 13px; }
.analysis .analysis__detail-button:hover { color: #105f42; background: var(--surface-soft); }
.analysis__card h3 { margin-bottom: 16px; }
.analysis__interviews { padding: 18px 0; border-top: 1px solid #e3ebe6; }
.analysis__interview-list { list-style: none; margin: 0; padding: 0; }
.analysis__interview-list > li + li { margin-top: 8px; }
.analysis-detail { position: fixed; inset: 0; margin: auto; padding: 0; width: min(560px, calc(100vw - 32px)); max-height: calc(100dvh - 48px); border: 0; border-radius: 12px; background: var(--surface-elevated); color: #20332c; font: inherit; font-size: 14px; line-height: 1.65; overflow: auto; box-shadow: 0 18px 60px #20332c30; overflow-wrap: anywhere; }
.analysis-detail::backdrop { background: #14251d66; }
.analysis-detail__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 20px 24px 16px; border-bottom: 1px solid #e3ebe6; }
.analysis-detail__header h2 { margin: 0; font-size: 18px; line-height: 1.5; }
.analysis-detail button { flex-shrink: 0; padding: 8px 16px; min-height: 40px; border: 1px solid #d6e3dd; border-radius: 8px; background: var(--surface-elevated); color: #167953; font: inherit; cursor: pointer; }
.analysis-detail__header button { padding: 0; width: 36px; min-height: 36px; border: 0; font-size: 24px; line-height: 1; }
.analysis-detail button:focus-visible { outline: 3px solid #9adfc3; outline-offset: 2px; }
.analysis-detail button:disabled { opacity: .55; cursor: default; }
.analysis-detail__body { padding: 16px 24px 24px; }
.analysis-detail__body section + section { margin-top: 20px; }
.analysis-detail__body h3 { margin: 0 0 8px; font-size: 14px; }
.analysis-detail__body p { margin: 8px 0; white-space: pre-wrap; }
.analysis-detail__meta { color: #66776f; font-size: 12px; margin: 0 0 20px !important; }
.analysis-detail blockquote { margin: 0; padding: 12px; background: var(--surface-soft); border-radius: 6px; white-space: pre-wrap; }
.analysis-detail__note { padding: 12px; background: var(--surface-soft); border-radius: 6px; }
.analysis-detail__footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 24px; border-top: 1px solid #e3ebe6; }
.analysis-detail .analysis-detail__primary { background: var(--accent-500, #10b981); border-color: transparent; color: #10392b; }
@media(max-width: 480px) { .analysis-detail__header, .analysis-detail__body, .analysis-detail__footer { padding: 16px; } .analysis-detail__footer { flex-wrap: wrap; } }

/* Evidence and next actions stay visible; full details remain available on demand. */
.analysis__source { color: #21664e; font-size: 13px; }
.analysis__columns { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; align-items: start; }
.analysis__card { border: 1px solid #e0e9e4; border-radius: 12px; padding: 22px; min-height: 0; }
.analysis__list { list-style: none; padding-left: 0; }
.analysis__list > li { padding-left: 0; }
.analysis__item + .analysis__item { margin-top: 22px; padding-top: 22px; }
.analysis__row > span { font-weight: 600; color: #203c31; }
.analysis__reason { color: #465c51; margin: 8px 0 12px; line-height: 1.8; font-size: 14px; white-space: pre-wrap; }
.analysis__action { margin: 12px 0; font-size: 14px; line-height: 1.8; white-space: pre-wrap; }
.analysis__action strong, .analysis__job-evidence strong { display: block; font-size: 13px; margin-bottom: 4px; color: #23694e; }
.analysis__job-evidence { color: #52675c; font-size: 14px; line-height: 1.75; white-space: pre-wrap; }
.analysis blockquote { border-left-width: 1px; font-size: 14px; line-height: 1.75; max-height: 160px; overflow-wrap: anywhere; }
.analysis blockquote > span { display: block; font-size: 12px; color: #54685d; margin-bottom: 5px; }
.analysis__interviews { padding: 24px; background: var(--surface-soft); border: 1px solid #e0e9e4; border-radius: 12px; margin-bottom: 12px; }
.analysis__interview-item { padding: 20px 0; }
.analysis__interview-item + .analysis__interview-item { border-top: 1px solid #d8e5dd; }
.analysis__interview-item h4 { margin: 0; font-size: 17px; line-height: 1.6; color: #203c31; }
.analysis__category-description { line-height: 1.7; }
.analysis__row { align-items: flex-start; }
.analysis__item, .analysis__interview-item { overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .analysis__interviews { padding: 18px; }
  .analysis__columns { grid-template-columns: 1fr; gap: 16px; }
  .analysis__card { padding: 18px; }
  .analysis__row { gap: 8px; }
  .analysis .analysis__category-head h3 { font-size: 17px; }
}

.analysis__columns { align-items: stretch; }
.analysis__card { display: flex; flex-direction: column; height: 540px; box-sizing: border-box; overflow: hidden; }
.analysis__category-head, .analysis__category-description { flex-shrink: 0; }
.analysis__card .analysis__category-description { min-height: 44px; margin-bottom: 14px; }
.analysis__page-content { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior-y: contain; scrollbar-width: thin; scrollbar-color: #a8c9b8 transparent; padding-right: 10px; }
.analysis__page-content blockquote { max-height: none; overflow: visible; }
.analysis__page-content:focus-visible { outline: 2px solid #16825e; outline-offset: -2px; }
.analysis__category-empty { flex: 1; }
.analysis__pager { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding-top: 14px; margin-top: 12px; border-top: 1px solid #e3ebe6; flex-shrink: 0; }
.analysis__pager > span { font-size: 13px; color: #52675c; white-space: nowrap; }
.analysis .analysis__pager button { padding: 6px 10px; font-size: 13px; min-height: 44px; }
@media (max-width: 760px) { .analysis__card { height: 480px; } }


/* Restore the pre-sync light appearance; retain current dark-theme rules. */
:root:not([data-theme="dark"]) .analysis button:not(.analysis__primary) {
    background: white
}
:root:not([data-theme="dark"]) .analysis__notice {
    background: #f0f7f4
}
:root:not([data-theme="dark"]) .analysis__error {
    background: #fff3ee
}
:root:not([data-theme="dark"]) .analysis__notice--warning {
    background: #fcf5e9
}
:root:not([data-theme="dark"]) .analysis button:hover:not(:disabled) {
    background: #f0f7f4
}
:root:not([data-theme="dark"]) .analysis__card {
    background: white
}
:root:not([data-theme="dark"]) .analysis__count {
    background: #eef5f1
}
:root:not([data-theme="dark"]) .analysis small.analysis__priority {
    background: #fcf5e9
}
:root:not([data-theme="dark"]) .analysis blockquote {
    background: #f6f8f7
}
:root:not([data-theme="dark"]) .analysis__empty {
    background: #f7faf8
}
:root:not([data-theme="dark"]) .analysis .analysis__detail-button:hover {
    background: #f0f7f4
}
:root:not([data-theme="dark"]) .analysis-detail {
    background: white
}
:root:not([data-theme="dark"]) .analysis-detail button {
    background: white
}
:root:not([data-theme="dark"]) .analysis-detail blockquote {
    background: #f6f8f7
}
:root:not([data-theme="dark"]) .analysis-detail__note {
    background: #f0f7f4
}
:root:not([data-theme="dark"]) .analysis__interviews {
    background: #f3f8f5
}
.analysis__results { margin: 8px 0 24px; padding: 24px 28px 0; border-top: 1px solid #e3ebe6; }
.analysis .analysis__results h3 { margin: 0 0 18px; font-size: 18px; font-weight: 600; }
.analysis .analysis__results-list { margin: 0; padding-left: 24px; }
.analysis__results-list > li { padding-left: 4px; margin-bottom: 18px; }
.analysis__results-list > li::marker { font-weight: 600; color: #20332c; }
.analysis__result-title { display: flex; align-items: baseline; gap: 12px; font-size: 15px; line-height: 1.7; }
.analysis__result-title > span { flex-shrink: 0; color: #167953; font-size: 14px; font-weight: 600; }
.analysis .analysis__results-list p { margin: 6px 0 0; font-size: 14px; color: #66776f; line-height: 1.8; }
:root[data-theme='dark'] .analysis__results-list > li::marker { color: #e4e4e7; }
:root[data-theme='dark'] .analysis__result-title > span { color: #6ee7b7; }
@media(max-width: 767px) { .analysis__results { padding: 20px 0 0; } .analysis__result-title { display: block; } .analysis__result-title > span { margin-right: 10px; } }
</style>
