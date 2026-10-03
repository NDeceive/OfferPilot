<template>
  <section class="execution-overview" aria-label="任务执行概况">
    <header><h2>任务执行概况</h2><p>{{activeAssignments.length}} 人次计入统计 · {{assignments.length-activeAssignments.length}} 人次减免 · 按有效次数要求计算完成</p></header>
    <div class="execution-stack" role="img" :aria-label="states.map(s=>s.label+' '+s.count+' 人次').join('，')"><span v-for="s in states" :key="s.key" :style="{width:(activeAssignments.length?s.count/activeAssignments.length*100:0)+'%',background:s.color}"/></div>
    <dl><div v-for="s in states" :key="s.key"><dt>{{s.label}}</dt><dd>{{s.count}}<small>人次</small></dd></div></dl>
    <p v-if="!assignments.length">发布班级任务后，这里会显示学生的执行进度。</p>
  </section>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({assignments:{type:Array,default:()=>[]}})
const activeAssignments=computed(()=>props.assignments.filter(a=>!a.exempt))
const states=computed(()=>[{key:'COMPLETED',label:'已完成',color:'#059669'},{key:'IN_PROGRESS',label:'尚未完成要求',color:'#94b8a8'},{key:'PENDING_VALIDATION',label:'报告校验中',color:'#d9ae68'},{key:'NOT_STARTED',label:'未开始',color:'#d4d4d8'}].map(s=>({...s,count:props.assignments.filter(a=>!a.exempt&&a.completionStatus===s.key).length})))
</script>
<style scoped>
.execution-overview{margin:28px 0;padding:24px 0;border-top:1px solid var(--neutral-200);border-bottom:1px solid var(--neutral-200)}.execution-overview header p{margin-top:6px}.execution-overview .execution-stack{margin:24px 0}.execution-overview dl{display:grid;grid-template-columns:repeat(4,1fr);gap:20px}.execution-overview dt{font-size:13px;color:var(--neutral-600)}.execution-overview dd{font-size:24px;font-weight:600;margin:8px 0 0}.execution-overview dd small{margin-left:8px;font-weight:400;font-size:13px}@media(max-width:600px){.execution-overview dl{grid-template-columns:1fr 1fr;gap:24px}}
</style>
