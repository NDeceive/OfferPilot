<template>
 <div ref="root" class="overview-combo">
  <svg :viewBox="`0 0 ${width} 260`" role="group" aria-label="有效训练次数柱状图与参与人数折线图">
   <text x="44" y="18">次数</text><text :x="width-42" y="18" text-anchor="end">人数</text>
   <g v-for="i in [0,1,2,3,4]" :key="i"><line x1="44" :x2="width-42" :y1="220-i*45" :y2="220-i*45" stroke="#e4e4e7"/><text x="35" :y="224-i*45" text-anchor="end">{{leftMax*i/4}}</text><text :x="width-33" :y="224-i*45">{{rightMax*i/4}}</text></g>
   <path v-if="hasData" :d="points.map((p,i)=>`${i?'L':'M'}${x(i)},${220-p.active/rightMax*180}`).join(' ')" fill="none" stroke="#7fa8d9" stroke-width="2"/>
   <g v-for="(p,i) in points" :key="p.date" role="button" tabindex="0" :aria-label="`${p.date}：有效训练 ${p.count} 次，参与 ${p.active} 人，查看记录`" @click="$emit('select',p.date)" @keydown.enter.prevent="$emit('select',p.date)" @keydown.space.prevent="$emit('select',p.date)">
    <rect :x="x(i)-barWidth/2" :y="220-p.count/leftMax*180" :width="barWidth" :height="p.count/leftMax*180" rx="2" fill="#34d399"/>
    <circle v-if="hasData" :cx="x(i)" :cy="220-p.active/rightMax*180" r="3" fill="#7fa8d9"/>
    <rect :x="x(i)-slot/2" y="32" :width="slot" height="190" fill="transparent" class="hit"/>
    <title>{{p.date}}：有效训练 {{p.count}} 次，参与 {{p.active}} 人</title>
    <text v-if="i%Math.ceil(points.length/(width<450?3:7))===0||i===points.length-1" :x="x(i)" y="244" text-anchor="middle">{{p.date.slice(5)}}</text>
   </g>
  </svg>
  <p class="combo-legend"><span><i/>有效训练次数（左轴）</span><span><i/>参与人数（右轴）</span></p>
  <label>查看日期明细<select aria-label="查看训练日期明细" :disabled="!hasData" @change="$emit('select',$event.target.value)"><option value="">选择日期</option><option v-for="p in points" :key="p.date" :value="p.date">{{p.date}} · {{p.count}} 次 / {{p.active}} 人</option></select></label>
  <p v-if="!hasData">当前周期尚无有效教学训练，收到记录后显示图表。</p>
 </div>
</template>
<script setup>
import {computed,ref,onMounted,onUnmounted} from 'vue'
const props=defineProps({points:{type:Array,default:()=>[]}})
defineEmits(['select'])
const root=ref(null),width=ref(640);let observer
onMounted(()=>{observer=new ResizeObserver(([e])=>width.value=Math.max(280,e.contentRect.width));observer.observe(root.value)})
onUnmounted(()=>observer?.disconnect())
const leftMax=computed(()=>Math.max(4,Math.ceil(Math.max(0,...props.points.map(p=>p.count))/4)*4)),rightMax=computed(()=>Math.max(4,Math.ceil(Math.max(0,...props.points.map(p=>p.active))/4)*4)),hasData=computed(()=>props.points.some(p=>p.count)),slot=computed(()=>(width.value-86)/Math.max(1,props.points.length)),barWidth=computed(()=>Math.min(24,slot.value*.65))
const x=i=>44+(i+.5)*slot.value
</script>
<style scoped>
svg{width:100%;display:block}text{font-size:12px;fill:#52525b}.combo-legend{display:flex;flex-wrap:wrap;gap:16px;font-size:13px;color:#52525b}.combo-legend span{display:flex;align-items:center;gap:6px}.combo-legend i{width:12px;height:8px;background:#34d399}.combo-legend span:last-child i{background:#7fa8d9;height:2px}label{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0;font-size:13px}select{max-width:100%;min-height:44px}.hit{cursor:pointer}g:focus-visible .hit{stroke:#27272a;stroke-width:2}p{color:#52525b;font-size:13px}
</style>
