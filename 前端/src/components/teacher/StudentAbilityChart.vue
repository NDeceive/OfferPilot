<template>
  <div ref="container" class="student-ability-chart">
    <svg v-if="records.length" :viewBox="`0 0 ${width} 260`" role="img" :aria-label="`${name}历次有效评分，满分100分`">
      <g v-for="tick in [0,25,50,75,100]" :key="tick"><line x1="45" :x2="width-20" :y1="y(tick)" :y2="y(tick)" stroke="#e3e7e5"/><text x="35" :y="y(tick)+4" text-anchor="end">{{tick}}</text></g>
      <template v-if="target!==null"><line x1="45" :x2="width-20" :y1="y(target)" :y2="y(target)" stroke="#6b7280" stroke-dasharray="5 5"/><text :x="width-25" :y="y(target)-8" text-anchor="end">目标 {{target}}</text></template>
      <path :d="line" fill="none" stroke="#2ab783" stroke-width="2.5"/>
      <g v-for="(r,i) in records" :key="r.id"><circle :cx="x(r)" :cy="y(r.abilityScore)" r="4" fill="#177b57" tabindex="0" role="button" :aria-label="`${formatDate(r.completedAt)}，${r.abilityScore.toFixed(1)}分，查看证据`" @click="$emit('select',r.id)" @keydown.enter="$emit('select',r.id)" @keydown.space.prevent="$emit('select',r.id)"><title>{{formatDate(r.completedAt)}} · {{r.abilityScore.toFixed(1)}}分</title></circle><text v-if="i===0||i===records.length-1||i%Math.max(1,Math.ceil(records.length/(width<500?3:5)))===0" :x="x(r)" y="244" text-anchor="middle">{{formatDate(r.completedAt).slice(0,5)}}</text></g>
    </svg>
    <p v-else class="student-empty">当前没有足够有效记录生成能力判断。</p>
    <p class="student-muted">{{target!==null?'绿色线为历次评分，灰色虚线为已配置目标。':'目标尚未设置，仅展示历次评分。'}}仅展示同岗位、同评分口径的当前连续阶段。</p>
  </div>
</template>
<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { formatDate } from '../../services/studentCenter.js'
const props=defineProps({records:{type:Array,default:()=>[]},target:{type:Number,default:null},name:String})
defineEmits(['select'])
const container=ref(null),width=ref(720)
let observer
onMounted(()=>{observer=new ResizeObserver(entries=>{width.value=Math.max(280,Math.min(720,entries[0].contentRect.width))});observer.observe(container.value)})
onUnmounted(()=>observer?.disconnect())
const y=n=>220-n*1.85
const extent=computed(()=>{const times=props.records.map(r=>Date.parse(r.completedAt));return [Math.min(...times),Math.max(...times)]})
const x=r=>extent.value[0]===extent.value[1]?width.value/2:45+(Date.parse(r.completedAt)-extent.value[0])/(extent.value[1]-extent.value[0])*(width.value-65)
const line=computed(()=>props.records.map((r,i)=>`${i?'L':'M'}${x(r)},${y(r.abilityScore)}`).join(' '))
</script>
