<template>
  <section class="prediction" aria-label="面试押题">
    <header class="prediction__header">
      <h3 class="prediction__title">面试押题</h3>
      <button type="button" class="prediction__download" @click="download">下载</button>
    </header>
    <section v-for="group in groups" :key="group.key" class="prediction__section" :aria-label="group.title">
      <h4 class="prediction__section-title">{{ group.title }}</h4>
      <p v-if="!group.items.length" class="prediction__empty">{{ group.key === 'skills' ? '暂无技能题，请确认上一步的技能标签后重新分析。' : '暂无项目题，可补充真实项目经历后重新分析。' }}</p>
      <ol v-else class="prediction__list">
        <li v-for="(item, index) in group.items" :key="index" class="prediction__item">
          <p class="prediction__question">{{ index + 1 }}、{{ item.question || item.title }}</p>
          <p class="prediction__answer">回答思路：{{ item.action || '暂无回答思路，请重新分析。' }}</p>
          <div v-if="group.key === 'projects'" class="prediction__followup">
            <span class="prediction__followup-label">追问：</span>
            <div class="prediction__preview" aria-hidden="true"><i></i><i></i></div>
            <button type="button" class="prediction__practice" :disabled="!applicable" @click="$emit('practice')">进入模拟面试 体验完整追问 &gt;</button>
          </div>
        </li>
      </ol>
    </section>
    <p v-if="!applicable" class="prediction__empty">简历或岗位已变化，请重新分析后进入模拟面试。</p>
    <p v-if="downloaded" class="prediction__status" role="status">题单已下载</p>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { groupInterviewQuestions, interviewQuestionText } from '../../utils/resumeAnalysis';

const props = defineProps({ items: { type: Array, default: () => [] }, applicable: Boolean, jobName: String });
defineEmits(['practice']);
const groups = computed(() => groupInterviewQuestions(props.items));
const downloaded = ref(false);
function download() {
  const url = URL.createObjectURL(new Blob(['\uFEFF', interviewQuestionText(groups.value, props.jobName)], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = '面试押题.txt';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  downloaded.value = true;
}
</script>

<style scoped>
.prediction { margin: 24px 0 12px; padding: 0 28px 28px; background: var(--surface-elevated, #fff); color: #404040; font-family: 'Microsoft YaHei UI', 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif; }
.prediction .prediction__header { display: grid; grid-template-columns: 92px 1fr 92px; align-items: center; gap: 12px; margin-bottom: 24px; }
.prediction .prediction__title { grid-column: 2; margin: 0; text-align: center; font-size: 20px; font-weight: 600; }
.prediction .prediction__download { grid-column: 3; min-height: 44px; padding: 4px 22px; border: 1px solid #34cfa4; border-radius: 24px; color: inherit; background: transparent; font-size: 14px; }
.prediction .prediction__section + .prediction__section { margin-top: 28px; }
.prediction .prediction__section-title { position: relative; margin: 0 0 16px; padding-bottom: 12px; border-bottom: 1px solid #b0ead9; font-size: 18px; font-weight: 600; color: inherit; }
.prediction__section-title::after { content: ''; position: absolute; left: 0; bottom: 0; width: 48px; height: 4px; background: #38c89b; }
.prediction .prediction__list { margin: 0; padding: 0; list-style: none; }
.prediction .prediction__item + .prediction__item { margin-top: 22px; }
.prediction .prediction__question { margin: 0 0 8px; font-size: 16px; font-weight: 500; line-height: 1.75; white-space: pre-line; }
.prediction .prediction__answer { margin: 0; color: var(--text-secondary, #737373); font-size: 14px; font-weight: 400; line-height: 1.8; white-space: pre-line; }
.prediction .prediction__followup { position: relative; display: flex; align-items: flex-start; gap: 12px; min-height: 80px; margin-top: 8px; padding: 14px 16px; overflow: hidden; border-radius: 18px; background: var(--surface-soft, #f9f9fa); }
.prediction .prediction__followup-label { flex-shrink: 0; color: var(--text-secondary, #737373); font-size: 14px; }
.prediction__preview { flex: 1; padding-top: 8px; filter: blur(4px); opacity: .35; }
.prediction__preview i { display: block; height: 12px; margin-bottom: 17px; background: repeating-linear-gradient(90deg, #c5c9cc 0 10px, transparent 10px 14px); }
.prediction__preview i + i { width: 82%; }
.prediction .prediction__practice { position: absolute; inset: 0; width: 100%; padding: 16px 20px 16px 76px; border: 0; border-radius: 18px; color: #3455f6; background: transparent; font-size: 14px; font-weight: 500; text-align: center; }
.prediction .prediction__empty, .prediction .prediction__status { color: var(--text-secondary, #737373); font-size: 14px; }
:root:not([data-theme='dark']) .prediction .prediction__followup { background: #fafafa; }
:root[data-theme='dark'] .prediction { color: #e4e4e7; }
:root[data-theme='dark'] .prediction .prediction__answer,
:root[data-theme='dark'] .prediction .prediction__followup-label,
:root[data-theme='dark'] .prediction .prediction__empty,
:root[data-theme='dark'] .prediction .prediction__status { color: #a1a1aa; }
:root[data-theme='dark'] .prediction .prediction__practice { color: #9aafff; }
@media (max-width: 767px) {
  .prediction { padding: 0 0 20px; }
  .prediction .prediction__header { grid-template-columns: 64px 1fr 64px; gap: 8px; margin-bottom: 24px; }
  .prediction .prediction__title, .prediction .prediction__section-title { font-size: 18px; }
  .prediction .prediction__download { padding: 4px 12px; font-size: 14px; }
  .prediction .prediction__question { font-size: 16px; }
  .prediction .prediction__answer, .prediction .prediction__followup-label, .prediction .prediction__practice { font-size: 14px; }
  .prediction .prediction__practice { padding-left: 56px; }
  .prediction .prediction__followup { padding: 14px 12px; min-height: 80px; }
}
</style>
