<template>
  <fieldset class="teaching-score-editor"><legend>统一评分方案</legend><p>选择五个维度与目标。发布后所有学生使用同一方案，自主训练偏好不会覆盖任务要求。</p><p v-if="error" role="alert">{{error}} <button type="button" @click="load">重新读取维度</button></p><label class="check"><input type="checkbox" :checked="balanced" @change="setBalanced($event.target.checked)"/>五个维度均衡评价（各 20%）</label><div v-for="(m,i) in rows" :key="i" class="score-editor-row"><span>{{i+1}}</span><label>维度<select :value="m.code" required @change="update(i,'code',$event.target.value)"><option v-for="d in dictionary" :key="d.code" :value="d.code">{{d.name}}</option></select></label><label>目标<select :value="m.level" @change="update(i,'level',Number($event.target.value))"><option :value="1">基础 · 65 分</option><option :value="2">进阶 · 75 分</option><option :value="3">突破 · 85 分</option></select></label><strong>{{balanced?20:[30,25,20,15,10][i]}}%</strong></div><small>非均衡模式按上方顺序确定优先级。次数完成与能力目标达成分别判断。</small></fieldset>
</template>
<script setup>
import {computed,ref,onMounted} from 'vue'
import {getModules} from '../../api'
const props=defineProps({modelValue:{type:Array,default:()=>[]}}),emit=defineEmits(['update:modelValue'])
const defaults=['technical_base','project_expression','logical_structure','position_cognition','followup_adaptability'].map(code=>({code,rank:1,level:2})),dictionary=ref([]),error=ref('')
const rows=computed(()=>props.modelValue.length===5?props.modelValue:defaults),balanced=computed(()=>rows.value.every(m=>m.rank===1))
function update(i,key,value){const next=rows.value.map(m=>({...m}));next[i][key]=value;emit('update:modelValue',next)}
function setBalanced(value){emit('update:modelValue',rows.value.map((m,i)=>({...m,rank:value?1:i+1})))}
async function load(){error.value='';try{dictionary.value=await getModules();if(!props.modelValue.length)emit('update:modelValue',defaults.map(m=>({...m})))}catch(e){error.value='评分维度读取失败，请重试。'}}
onMounted(load)
</script>
