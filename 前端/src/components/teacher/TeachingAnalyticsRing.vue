<template>
  <div class="la-ring-layout">
    <svg class="la-ring" viewBox="0 0 220 220" role="img" :aria-label="`${title}，合计${total}人`">
      <circle cx="110" cy="110" r="78" fill="none" stroke="#f0f4f1" stroke-width="26"/>
      <circle v-for="s in segments" :key="s.key" cx="110" cy="110" r="78" fill="none" :stroke="s.color" stroke-width="26"
        :stroke-dasharray="`${s.count/total*circumference} ${circumference}`" :stroke-dashoffset="-s.offset"
        transform="rotate(-90 110 110)"/>
      <text x="110" y="106" text-anchor="middle" class="la-ring-total">{{total}}</text>
      <text x="110" y="132" text-anchor="middle" class="la-chart-text">{{total?'学生总数':'暂无学生'}}</text>
    </svg>
    <div class="la-ring-legend">
      <button v-for="s in items" :key="s.key" :aria-pressed="selected===s.key" @click="$emit('select',s)">
        <i :style="{background:s.color}"/><span>{{s.name}}</span><b>{{s.count}}人</b><small>{{total?(s.count/total*100).toFixed(1)+'%':'—'}}</small>
      </button>
    </div>
  </div>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({items:{type:Array,default:()=>[]},title:String,selected:String})
defineEmits(['select'])
const circumference=2*Math.PI*78,total=computed(()=>props.items.reduce((n,s)=>n+s.count,0))
const segments=computed(()=>{let offset=0;return props.items.filter(s=>s.count>0).map(s=>{const result={...s,offset};offset+=s.count/total.value*circumference;return result})})
</script>
