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
          ? "正在检查简历与岗位资料，AI 正在对照原文和岗位要求，必要时会复核一次，请耐心等待。你可以离开此页，报告完成后会保存。"
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
        <div class="analysis__summary">
          <span class="analysis__source">{{ report.source === "AI" ? "基于原文与岗位的 AI 分析" : "基础检查 · 尚未获得完整 AI 分析" }}</span>
          <h3>本次结论</h3>
          <p>{{ report.summary }}</p>
          <p v-if="report.stillOpen?.length" class="analysis__muted">
            与上一份报告有同名待核查项：{{
              report.stillOpen.join("、")
            }}。此处只比较问题标题，不推断已解决的事项。
          </p>
        </div>

        <div class="analysis__columns">
          <article v-for="group in groups" :key="group.key" class="analysis__card">
            <header class="analysis__category-head"><h3>{{ group.title }}</h3><span class="analysis__count">{{ group.items.length }} 项</span></header>
            <p class="analysis__category-description">{{ group.description }}</p>
            <div v-if="!group.items.length" class="analysis__category-empty"><p>暂无明确建议</p><small>{{ group.empty }}</small></div>
            <ol v-else class="analysis__list analysis__page-content" tabindex="0" :aria-label="`${group.title}建议内容，可滚动查看`">
              <li v-for="(item, i) in group.items.slice(groupPages[group.key] || 0, (groupPages[group.key] || 0) + 1)" :key="`${groupPages[group.key] || 0}-${i}`" class="analysis__item">
                <div class="analysis__row">
                  <span>{{ item.title }}<small v-if="item.priority === 'HIGH' && group.key !== 'strengths'" class="analysis__priority">优先核对</small></span>
                  <button type="button" class="analysis__detail-button" :aria-label="`${item.title}：详情`" @click="openDetail(item)">详情</button>
                </div>
                <p class="analysis__reason">{{ item.reason }}</p>
                <blockquote v-if="item.quote"><span>原文依据</span>{{ item.quote }}</blockquote>
                <p v-if="item.action" class="analysis__action"><strong>{{ group.key === 'strengths' ? '面试中如何展开' : '建议行动' }}</strong>{{ item.action }}</p>
                <p v-if="item.jobEvidence" class="analysis__job-evidence"><strong>岗位要求</strong>{{ item.jobEvidence }}</p>
              </li>
            </ol>
            <nav v-if="group.items.length > 1" class="analysis__pager" :aria-label="`${group.title}翻页`">
              <button type="button" :disabled="!(groupPages[group.key] || 0)" :aria-label="`${group.title}：上一条`" @click="changeGroupPage(group.key, -1, $event)">← 上一条</button>
              <span aria-live="polite">{{ (groupPages[group.key] || 0) + 1 }} / {{ group.items.length }}</span>
              <button type="button" :disabled="(groupPages[group.key] || 0) >= group.items.length - 1" :aria-label="`${group.title}：下一条`" @click="changeGroupPage(group.key, 1, $event)">下一条 →</button>
            </nav>
          </article>
        </div>
        <section v-if="interviews.length" class="analysis__interviews" aria-label="面试推测">
          <header class="analysis__category-head"><h3>面试推测</h3><span class="analysis__count">{{ interviews.length }} 类题型</span></header>
          <p class="analysis__category-description">结合简历经历与目标岗位，推测可能会出的题型及示例提问。</p>
          <ul class="analysis__interview-list">
            <li v-for="(item, i) in interviews" :key="i" class="analysis__interview-item">
              <div class="analysis__row"><h4>{{ item.title }}</h4><button type="button" class="analysis__detail-button" :aria-label="`${item.title}：详情`" @click="openDetail(item)">完整依据</button></div>
              <p v-if="item.question" class="analysis__action"><strong>示例提问</strong>{{ item.question }}</p>
              <p class="analysis__reason"><strong>推测依据：</strong>{{ item.reason }}</p>
              <p v-if="item.action" class="analysis__action"><strong>考察重点</strong>{{ item.action }}</p>
              <blockquote v-if="item.quote"><span>来自简历</span>{{ item.quote }}</blockquote>
              <p v-if="item.moduleCode" class="analysis__muted">可练习：{{ moduleName(item.moduleCode) }}</p>
            </li>
          </ul>
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
          <button v-if="preparationFlow" type="button" class="analysis__primary" @click="$emit('next')">下一步：训练目标 →</button>
        </footer>
    <Teleport to="body">
      <dialog ref="detailDialog" class="analysis-detail" aria-labelledby="analysis-detail-title" @close="selectedDetail = null" @click="closeOnBackdrop">
        <template v-if="selectedDetail">
          <header class="analysis-detail__header">
            <h2 id="analysis-detail-title">{{ selectedDetail.title }}</h2>
            <button type="button" aria-label="关闭详情" @click="closeDetail">×</button>
          </header>
          <div class="analysis-detail__body">
            <p class="analysis-detail__meta">{{ field(selectedDetail.field) }} · {{ priority(selectedDetail.priority) }}优先级</p>
            <section><h3>{{ selectedDetail.kind === 'INTERVIEW' ? '推测依据' : '问题说明' }}</h3><p>{{ selectedDetail.reason }}</p></section>
            <section v-if="selectedDetail.action"><h3>{{ selectedDetail.kind === 'INTERVIEW' ? '考察重点' : '具体建议' }}</h3><p>{{ selectedDetail.action }}</p></section>
            <section><h3>简历原文</h3><blockquote v-if="selectedDetail.quote">{{ selectedDetail.quote }}</blockquote><p v-else>该分区未提供内容，没有可引用的原文。</p></section>
            <section v-if="selectedDetail.jobEvidence"><h3>岗位依据</h3><blockquote>{{ selectedDetail.jobEvidence }}</blockquote></section>
            <section v-if="selectedDetail.suggestion"><h3>建议修改后的内容</h3><blockquote>{{ selectedDetail.suggestion }}</blockquote><p>需审核后采用，不会自动修改简历。</p></section>
            <section v-if="selectedDetail.question"><h3>{{ selectedDetail.kind === 'INTERVIEW' ? '示例提问' : '可能追问' }}</h3><p>{{ selectedDetail.question }}</p></section>
            <p v-if="selectedDetail.moduleCode">关联训练：{{ moduleName(selectedDetail.moduleCode) }}</p>
            <p v-if="selectedDetail.requiresFacts" class="analysis-detail__note">请按真实经历补充或核实；简历未体现不代表没有相关能力。</p>
            <p v-if="!report?.applicable" class="analysis-detail__note">报告输入已变化，请重新分析后再修改。</p>
          </div>
          <footer class="analysis-detail__footer">
            <button type="button" @click="closeDetail">关闭</button>
            <button v-if="selectedDetail.kind !== 'STRENGTH'" type="button" class="analysis-detail__primary" :disabled="!report?.applicable" @click="editDetail">{{ selectedDetail.kind === 'SUGGESTION' ? '审核建议并修改草稿' : '去核对并修改' }}</button>
          </footer>
        </template>
      </dialog>
    </Teleport>
  </section>
</template>

<script setup>
import { ref, watch, onBeforeUnmount, nextTick } from "vue";
import { computed } from "vue";
import { getResumeAnalysis, createResumeAnalysis, getModules } from "../../api";
import { analysisFields, groupAnalysisItems } from "../../utils/resumeAnalysis";
const props = defineProps({
  jobId: Number,
  versionId: Number,
  available: Boolean,
  sourceKey: { type: [String, Number], default: "" },
  preparationFlow: Boolean,
});
const emit = defineEmits(["report", "edit", "recommend", "previous", "next"]);
const report = ref(null),
  busy = ref(false),
  analyzing = ref(false),
  error = ref(""),
  notice = ref(""),
  modules = ref([]);
let sequence = 0;
const detailDialog = ref(null);
const selectedDetail = ref(null);
async function openDetail(item) {
  selectedDetail.value = item;
  await nextTick();
  if (selectedDetail.value === item && !detailDialog.value?.open) detailDialog.value?.showModal();
}
function closeDetail() {
  detailDialog.value?.close();
  selectedDetail.value = null;
}
function closeOnBackdrop(event) {
  if (event.target === detailDialog.value) {
    const box = detailDialog.value.getBoundingClientRect();
    if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) closeDetail();
  }
}
function editDetail() {
  const item = selectedDetail.value;
  closeDetail();
  edit(item);
}
const groupPages = ref({});
function changeGroupPage(key, direction, event) {
  const group = groups.value.find((entry) => entry.key === key);
  if (!group) return;
  groupPages.value[key] = Math.max(0, Math.min(group.items.length - 1, (groupPages.value[key] || 0) + direction));
  const content = event.currentTarget.closest('.analysis__card')?.querySelector('.analysis__page-content');
  if (content) content.scrollTop = 0;
}
watch(report, () => { closeDetail(); groupPages.value = {}; });
const groups = computed(() => {
  const items = groupAnalysisItems(report.value?.items || []);
  return [
    { key: "strengths", title: "优势方面", description: "原文中已体现、与岗位相关的优势。", empty: "当前证据不足以形成明确优势，补充真实案例后可重新分析。", items: items.strengths },
    { key: "improvements", title: "待提升", description: "简历内容、表达及事实依据需要完善的事项。", empty: "暂未识别到明确改进项，仍需人工核查。", items: items.improvements },
    { key: "jobGaps", title: "对应岗位仍需补充", description: "对照目标岗位，简历尚未体现或需要核实的信息。未体现不代表没有能力。", empty: "暂未识别到有明确岗位依据的补充项，不代表已满足全部岗位要求。", items: items.jobGaps },
  ];
});
const interviews = computed(
  () => report.value?.items.filter((i) => i.kind === "INTERVIEW") || []
);
const field = (k) => analysisFields[k] || k;
const priority = (k) => ({ HIGH: "高", MEDIUM: "中", LOW: "低" }[k]);
const date = (v) => (v ? new Date(v).toLocaleString("zh-CN") : "");
const moduleName = (k) => modules.value.find((m) => m.code === k)?.name || k;
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
getModules()
  .then((v) => (modules.value = v || []))
  .catch(() => {});
async function run(force) {
  if (busy.value || !props.jobId || !props.available) return;
  const request = ++sequence;
  closeDetail();
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
onBeforeUnmount(() => { ++sequence; closeDetail(); });
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
.analysis button {
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
</style>
