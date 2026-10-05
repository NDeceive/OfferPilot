<template>
  <div class="execution-filters">
    <nav class="status-filters" aria-label="学生执行状态">
      <button v-for="key in ['all','COMPLETED','IN_PROGRESS','NOT_STARTED']" :key="key" :class="{selected:status===key}" :aria-pressed="status===key" @click="$emit('change','status',key)">{{ statusNames[key] }} <strong>{{ count(key) }}人</strong></button>
    </nav>
    <label class="timing-filter">逾期情况<select :value="timing" @change="$emit('change','timing',$event.target.value)"><option value="">不限</option><option value="overdue">当前逾期未完成（{{ counts(task).overdue }}人）</option><option value="late">历史逾期完成（{{ counts(task).late }}人）</option></select></label>
  </div>
</template>
<script setup>
import { statusNames, counts, matchesStatus } from '../../services/trainingTasks.js'
const props=defineProps({task:{type:Object,required:true},status:{type:String,default:'all'},timing:{type:String,default:''}})
defineEmits(['change'])
function count(key){return(props.task.executions||[]).filter(r=>matchesStatus(props.task,r,key)).length}
</script>
