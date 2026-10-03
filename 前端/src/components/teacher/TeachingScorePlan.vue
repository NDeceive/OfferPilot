<template>
  <section class="teaching-score-plan">
    <header><h2>本任务评分要求</h2><span>{{plan?.source==='TEACHER_TASK'?'教师统一方案':'旧版默认方案'}}{{plan?.version?' · 版本 '+plan.version:''}}</span></header>
    <p>教学任务采用此处的统一标准。自主训练的自选维度不会改变本任务评分。</p>
    <div v-if="plan?.modules?.length" class="table-scroll"><table><thead><tr><th>评价维度</th><th>优先级</th><th>权重</th><th>目标分</th></tr></thead><tbody><tr v-for="m in plan.modules" :key="m.code"><td>{{m.name||m.code}}</td><td data-label="优先级">{{m.rank}}</td><td data-label="权重">{{(m.weight*100).toFixed(0)}}%</td><td data-label="目标分">{{m.target}} / 100</td></tr></tbody></table></div>
    <p v-else>该历史任务未记录完整评分配置，请以原始报告的实际维度与目标为准。</p>
    <small>次数完成与能力达标分别统计；有效的低分训练仍可计入次数，原始分与目标匹配度分别展示。</small>
  </section>
</template>
<script setup>
defineProps({plan:Object})
</script>
