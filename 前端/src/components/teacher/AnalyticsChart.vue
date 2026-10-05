<template>
 <div ref="container" class="analytics-chart">
  <svg :viewBox="`0 0 ${width} 245`" role="img" :aria-label="label">
   <g v-for="tick in [0,1,2,3,4]" :key="tick"><line x1="42" :x2="width-15" :y1="y(max*tick/4)" :y2="y(max*tick/4)" stroke="#e3e7e5"/><text x="34" :y="y(max*tick/4)+4" text-anchor="end">{{Math.round(max*tick/4)}}{{percent?'%':''}}</text></g>
   <template v-if="stack"><g v-for="(p,i) in points" :key="p.date"><rect v-for="(s,j) in series" :key="s.key" :x="x(i)-barWidth/2" :y="y(series.slice(0,j+1).reduce((sum,k)=>sum+(p[k.key]||0),0))" :width="barWidth" :height="(p[s.key]||0)/max*170" :fill="colors[j]" tabindex="0" role="button" :aria-label="tooltip(p,s)+'，查看训练记录'" @click="$emit('select',p)" @keydown.enter="$emit('select',p)" @keydown.space.prevent="$emit('select',p)"><title>{{tooltip(p,s)}}</title></rect></g></template>
   <template v-else><path v-for="(s,j) in series" :key="s.key" :d="path(s.key)" fill="none" :stroke="colors[j]" stroke-width="2.5"/><g v-for="(p,i) in points" :key="p.date"><circle v-for="(s,j) in series.filter(s=>Number.isFinite(p[s.key]))" :key="s.key" :cx="x(i)" :cy="y(p[s.key])" r="4" :fill="colors[j]" tabindex="0" role="button" :aria-label="tooltip(p,s)" @click="$emit('select',p)" @keydown.enter="$emit('select',p)" @keydown.space.prevent="$emit('select',p)"><title>{{tooltip(p,s)}}</title></circle></g></template>
   <text v-for="(p,i) in points.filter((p,i)=>i%Math.max(1,Math.ceil(points.length/(width<480?3:6)))===0||i===points.length-1)" :key="p.date" :x="x(points.indexOf(p))" y="230" text-anchor="middle">{{p.date.slice(5)}}</text>
  </svg>
  <div class="analytics-legend"><span v-for="(s,i) in series" :key="s.key"><i :style="{background:colors[i]}"></i>{{s.name}}</span></div>
  <p v-if="!hasValues" class="analytics-empty">{{sample?'同方案可比样本不足，暂无目标达成趋势。':'暂无可统计的周期数据。'}}</p>
  <details><summary>查看图表数据</summary><div class="analytics-table-scroll"><table><thead><tr><th>周期</th><th v-for="s in series" :key="s.key">{{s.name}}</th><th v-if="sample">可比样本／版本</th></tr></thead><tbody><tr v-for="p in points" :key="p.date"><td><button class="analytics-link" @click="$emit('select',p)">{{p.date}}～{{p.to}}{{p.partial?'（未满周）':''}}</button></td><td v-for="s in series" :key="s.key">{{Number.isFinite(p[s.key])?p[s.key].toFixed(1)+(percent?'%':''):'数据不足'}}</td><td v-if="sample">{{p.n}}人 · {{p.versionLabel||p.version||'暂无'}}</td></tr></tbody></table></div></details>
 </div>
</template>
<script setup>
import {computed,ref,onMounted,onUnmounted} from 'vue'
const props=defineProps({points:Array,series:Array,label:String,percent:Boolean,stack:Boolean,sample:Boolean})
defineEmits(['select'])
const container=ref(null),width=ref(680),colors=['#177b57','#9ddfc3'];let observer
const hasValues=computed(()=>props.points.some(p=>props.series.some(s=>Number.isFinite(p[s.key]))))
onMounted(()=>{observer=new ResizeObserver(e=>width.value=Math.max(280,e[0].contentRect.width));observer.observe(container.value)})
onUnmounted(()=>observer?.disconnect())
const max=computed(()=>props.percent?100:Math.max(4,...props.points.map(p=>props.series.reduce((v,s)=>props.stack?v+(p[s.key]||0):Math.max(v,p[s.key]||0),0))))
const y=v=>205-v/max.value*170,x=i=>55+(width.value-80)*(i+.5)/Math.max(1,props.points.length),barWidth=computed(()=>Math.min(44,(width.value-80)/Math.max(1,props.points.length)*.65))
const tooltip=(p,s)=>`${p.date}～${p.to} · ${s.name}：${p[s.key]?.toFixed(1)}${props.percent?'%':''}${props.sample?' · 可比样本'+p.n+'人 · '+(p.versionLabel||p.version):''}`
function path(key){let d='',previous=null;for(const [i,p] of props.points.entries()){if(!Number.isFinite(p[key])){previous=null;continue}const connected=previous&&(!props.sample||previous.version===p.version);d+=`${connected?'L':'M'}${x(i)},${y(p[key])} `;previous=p}return d}
</script>
