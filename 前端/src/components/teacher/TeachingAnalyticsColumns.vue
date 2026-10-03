<template>
  <div class="la-columns">
    <svg viewBox="0 0 540 275" role="img" :aria-label="title+'，单位人数'">
      <text x="12" y="18" class="la-chart-text">人数</text>
      <g v-for="i in [0,1,2,3,4]" :key="i"><path :d="`M42 ${220-i*45}H520`" stroke="#edf1ed"/><text x="12" :y="224-i*45" class="la-chart-text">{{max*i/4}}</text></g>
      <g v-for="(s,i) in items" :key="s.key" role="button" tabindex="0" :aria-label="`${s.name} ${s.count}人，筛选学生`"
        @click="$emit('select',s)" @keydown.enter="$emit('select',s)" @keydown.space.prevent="$emit('select',s)">
        <rect :x="x(i)-width/2-8" y="35" :width="width+16" height="192" fill="transparent"/>
        <rect :x="x(i)-width/2" :y="220-s.count/max*180" :width="width" :height="s.count/max*180" :fill="s.color" rx="4" :stroke="selected===s.key?'#315a41':'none'" stroke-width="2"/>
        <text :x="x(i)" :y="208-s.count/max*180" text-anchor="middle" class="la-chart-number">{{s.count}}</text>
        <text :x="x(i)" y="250" text-anchor="middle" class="la-chart-text">{{s.shortName||s.name}}</text>
      </g>
    </svg>
    <div class="la-column-legend"><button v-for="s in items" :key="s.key" :aria-pressed="selected===s.key" @click="$emit('select',s)"><i :style="{background:s.color}"/>{{s.name}}<b>{{s.count}}人</b></button></div>
  </div>
</template>
<script setup>
import {computed} from 'vue'
const props=defineProps({items:{type:Array,default:()=>[]},title:String,selected:String});defineEmits(['select'])
const max=computed(()=>Math.ceil(Math.max(4,...props.items.map(s=>s.count))/4)*4),width=computed(()=>Math.min(64,460/Math.max(1,props.items.length)*.55)),x=i=>42+478/Math.max(1,props.items.length)*(i+.5)
</script>
