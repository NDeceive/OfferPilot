<template>
<div class="table-scroll"><table class="execution-table"><thead><tr><th v-if="selectable"><input type="checkbox" aria-label="选择当前显示学生" :checked="rows.length>0&&rows.every(r=>selected.includes(r.studentId))" @change="selectAll($event.target.checked)" /></th><th>学生</th><th>学号</th><th>目标岗位</th><th>执行状态</th><th>有效完成次数</th><th>最近训练</th><th>逾期情况</th><th v-if="showReasons">关注原因</th><th>操作</th></tr></thead><tbody><tr v-for="r in rows" :key="r.studentId"><td v-if="selectable"><input type="checkbox" :checked="selected.includes(r.studentId)" :aria-label="`选择${r.name}`" @change="selectOne(r.studentId,$event.target.checked)" /></td><td><strong>{{r.name}}</strong></td><td>{{r.number}}</td><td>{{r.position||'—'}}</td><td>{{statusNames[r.completionStatus]}}</td><td>{{r.validAttemptCount}}次</td><td>{{time(r.lastAttemptAt||r.completedAt)}}</td><td :class="{danger:isOverdue(task,r)||r.lateCompleted}">{{isOverdue(task,r)?'当前逾期未完成':r.lateCompleted?'历史逾期完成':'—'}}</td><td v-if="showReasons">{{attentionReason(task,r)}}</td><td><button class="text" @click="$emit('student',r,task)">学生详情</button><button v-if="resultEvidence(task,r).latest" class="text" @click="$emit('result',r,task)">最近结果</button></td></tr></tbody></table></div>
</template>
<script setup>
import { statusNames,isOverdue,resultEvidence,attentionReason } from '../../services/trainingTasks.js'
const props=defineProps({rows:{type:Array,default:()=>[]},task:{type:Object,required:true},selected:{type:Array,default:()=>[]},selectable:{type:Boolean,default:true},showReasons:{type:Boolean,default:false}})
const emit=defineEmits(['select','student','result'])
function selectOne(id,checked){emit('select',checked?[...new Set([...props.selected,id])]:props.selected.filter(s=>s!==id))}
function selectAll(checked){const ids=props.rows.map(r=>r.studentId);emit('select',checked?[...new Set([...props.selected,...ids])]:props.selected.filter(id=>!ids.includes(id)))}
function time(value){return value?new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(value)):'—'}
</script>
