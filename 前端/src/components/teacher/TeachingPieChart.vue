<template>
  <div class="teaching-pie-chart">
    <svg viewBox="0 0 300 270" role="group" aria-label="任务完成状态扇形图，按分配人次统计">
      <circle v-if="!total" cx="150" cy="130" r="105" fill="#f4f4f5" stroke="#e4e4e7"/>
      <g v-for="s in slices" :key="s.key" role="button" tabindex="0" :aria-label="`${s.label}，${s.count} 人次，占 ${percentage(s.count)}%，查看状态明细`" @mouseenter="hover=s.key" @mouseleave="hover=''" @focus="hover=s.key" @blur="hover=''" @click="select(s.key)" @keydown.enter.prevent="select(s.key)" @keydown.space.prevent="select(s.key)">
        <path :d="sector(s.start,s.end)" :fill="s.color" :stroke="selected===s.key?'#27272a':'white'" :stroke-width="selected===s.key?3:2"/>
        <text v-if="s.count/total>=.09" :x="position((s.start+s.end)/2,72).x" :y="position((s.start+s.end)/2,72).y+4" text-anchor="middle" :style="{fill:s.key==='COMPLETED'&&!donut?'white':'#27272a'}" class="pie-label">{{percentage(s.count)}}%</text>
        <title>{{s.label}}：{{s.count}} 人次，占 {{percentage(s.count)}}%</title>
      </g>
      <circle v-if="donut && total" cx="150" cy="130" r="48" fill="white" style="pointer-events:none"/><text v-if="donut && total" x="150" y="126" text-anchor="middle" style="pointer-events:none">{{total}} 人次</text><text v-if="donut && total" x="150" y="146" text-anchor="middle" style="pointer-events:none">任务分配</text>
      <text v-if="!total" x="150" y="135" text-anchor="middle">尚未分配任务</text>
      <text x="150" y="260" text-anchor="middle">共 {{total}} 人次分配</text>
    </svg>
    <div class="pie-legend"><button v-for="s in items" :key="s.key" :disabled="!s.count" :aria-pressed="selected===s.key" @mouseenter="hover=s.key" @mouseleave="hover=''" @focus="hover=s.key" @blur="hover=''" @click="select(s.key)"><i :style="{background:s.color}"/><span>{{s.label}}</span><strong>{{s.count}}<small> 人次</small></strong><b>{{percentage(s.count)}}%</b></button></div>
    <div class="pie-reading" aria-live="polite"><strong>{{active?active.label:'选择扇面查看分布'}}</strong><p>{{active?`${active.count} 人次，占全部分配的 ${percentage(active.count)}%。${explanations[active.key]}`:'同一学生接收多项任务会计入多个人次；完成状态与报告有效性分别统计。'}}</p></div>
  </div>
</template>
<script setup>
import {computed,ref} from 'vue'
const props=defineProps({items:{type:Array,default:()=>[]},selected:String,donut:Boolean})
const emit=defineEmits(['select']),hover=ref('')
const total=computed(()=>props.items.reduce((n,s)=>n+s.count,0)),percentage=n=>total.value?(n/total.value*100).toFixed(1):'0.0'
const slices=computed(()=>{let angle=-Math.PI/2;return props.items.filter(s=>s.count).map(s=>{const start=angle;angle+=s.count/total.value*Math.PI*2;return {...s,start,end:angle}})})
const position=(angle,r)=>({x:150+Math.cos(angle)*r,y:130+Math.sin(angle)*r})
function sector(start,end){const a=position(start,105),b=position(end,105);if(end-start>=Math.PI*2-.000001)return 'M150,25 A105,105 0 1 1 150,235 A105,105 0 1 1 150,25 Z';return `M150,130 L${a.x},${a.y} A105,105 0 ${end-start>Math.PI?1:0} 1 ${b.x},${b.y} Z`}
const active=computed(()=>props.items.find(s=>s.key===(hover.value||props.selected)))
const explanations={COMPLETED:'已达到任务要求的有效训练次数。',IN_PROGRESS:'已有训练，但尚未达到任务完成要求。',PENDING_VALIDATION:'报告仍在生成或校验，暂不计为已完成。',NOT_STARTED:'尚未产生训练记录，可前往任务详情跟进。'}
function select(key){emit('select',props.selected===key?'':key)}
</script>
