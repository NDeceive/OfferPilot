<template>
  <div class="la-scatter">
    <svg viewBox="0 0 620 330" role="img" :aria-label="title">
      <text x="16" y="18" class="la-chart-text">{{yLabel}}</text>
      <g v-for="i in [0,1,2,3,4,5]" :key="i"><path :d="`M50 ${270-i*44}H580`" stroke="#edf1ed"/><text x="16" :y="274-i*44" class="la-chart-text">{{i*20}}</text><path :d="`M${50+i*106} 50V270`" stroke="#f4f6f4"/><text :x="50+i*106" y="294" text-anchor="middle" class="la-chart-text">{{xMax*i/5}}</text></g>
      <path d="M50 50V270H580" fill="none" stroke="#cbd7cf"/>
      <path v-if="identity" d="M50 270L580 50" stroke="#78929f" stroke-dasharray="6 5" fill="none"/>
      <template v-else-if="Number.isFinite(target)"><path :d="`M50 ${y(target)}H580`" stroke="#b28c54" stroke-dasharray="6 5"/><text x="576" :y="y(target)-8" text-anchor="end" class="la-chart-text">目标{{target}}分</text></template>
      <g v-for="s in groups" :key="s.key" role="button" tabindex="0" :aria-label="label(s)+'，核查学生'" @click="$emit('select',s.points)" @keydown.enter="$emit('select',s.points)" @keydown.space.prevent="$emit('select',s.points)">
        <title>{{label(s)}}</title><circle :cx="x(s.x)" :cy="y(s.y)" r="22" fill="transparent"/>
        <circle :cx="x(s.x)" :cy="y(s.y)" :r="Math.min(18,5+Math.sqrt(s.points.length)*2)" :fill="identity?(s.y>s.x?'#83c9aa':s.y<s.x?'#e6c28c':'#9fb4c5'):(s.y>=target?'#83c9aa':'#e6c28c')" fill-opacity=".85" stroke="white" stroke-width="2"/>
        <text v-if="s.points.length>1" :x="x(s.x)" :y="y(s.y)+4" text-anchor="middle" class="la-scatter-count">{{s.points.length}}</text>
      </g>
      <text x="580" y="320" text-anchor="end" class="la-chart-text">{{xLabel}}</text>
    </svg>
    <p>{{points.length}}名学生 · 同一坐标合并，圆内数字为人数；点击圆点核查。{{identity?'虚线表示前后同分，上方为升高、下方为降低。':'横虚线表示同标准目标，次数与评分的关系不代表因果。'}}</p>
    <details><summary>查看散点坐标与学生</summary><ul class="la-coordinate-list"><li v-for="s in groups" :key="s.key"><button @click="$emit('select',s.points)">{{label(s)}}</button></li></ul></details>
  </div>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({points:{type:Array,default:()=>[]},title:String,xLabel:String,yLabel:String,identity:Boolean,target:Number})
defineEmits(['select'])
const xMax=computed(()=>props.identity?100:Math.ceil(Math.max(5,...props.points.map(p=>p.x))/5)*5),x=v=>50+v/xMax.value*530,y=v=>270-v/100*220
const groups=computed(()=>{const values=new Map();for(const p of props.points){const key=p.x+':'+p.y;if(!values.has(key))values.set(key,{key,x:p.x,y:p.y,points:[]});values.get(key).points.push(p)}return [...values.values()]})
const label=s=>`${props.xLabel} ${s.x}，${props.yLabel} ${s.y}；${s.points.length}人（${s.points.slice(0,3).map(p=>p.name).join('、')}${s.points.length>3?'等':''}）`
</script>
