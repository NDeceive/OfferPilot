<template>
  <div ref="root" class="teaching-trend-chart">
    <svg :viewBox="`0 0 ${width} 270`" role="group" aria-label="每日有效训练与无效提交折线图">
      <text x="46" y="18">提交次数</text>
      <g v-for="tick in ticks" :key="tick"><line x1="46" :x2="width-20" :y1="y(tick)" :y2="y(tick)" stroke="#e4e4e7"/><text x="36" :y="y(tick)+4" text-anchor="end">{{tick}}</text></g>
      <template v-if="hasData">
        <path v-for="s in series" :key="s.key" :d="path(s.key)" fill="none" :stroke="s.color" stroke-width="2.5"/>
        <line v-if="active" :x1="x(points.indexOf(active))" :x2="x(points.indexOf(active))" y1="35" y2="225" stroke="#71717a" stroke-dasharray="4 4"/>
        <g v-for="(p,i) in points" :key="p.date" role="button" tabindex="0" :aria-label="`${p.date}，有效 ${p.valid} 次，无效 ${p.invalid} 次，查看当天报告`" @mouseenter="hover=p.date" @mouseleave="hover=''" @focus="hover=p.date" @blur="hover=''" @click="$emit('select',p.date)" @keydown.enter.prevent="$emit('select',p.date)" @keydown.space.prevent="$emit('select',p.date)" @keydown.esc="hover=''">
          <rect :x="x(i)-Math.min(15,(width-66)/points.length/2)" y="30" :width="Math.min(30,(width-66)/points.length)" height="200" fill="transparent"/>
          <circle v-for="s in series" :key="s.key" :cx="x(i)" :cy="y(p[s.key])" :r="active?.date===p.date?5:3" :fill="s.color" stroke="white" stroke-width="1.5"/>
          <title>{{p.date}}：有效 {{p.valid}} 次，无效 {{p.invalid}} 次</title>
        </g>
      </template>
      <text v-for="(p,i) in points.filter((p,i)=>i===0||i===points.length-1||i%Math.ceil(points.length/(width<450?3:6))===0)" :key="p.date" :x="x(points.indexOf(p))" y="249" :text-anchor="p===points[0]?'start':p===points.at(-1)?'end':'middle'">{{p.date.slice(5)}}</text>
    </svg>
    <div class="chart-series"><span v-for="s in series" :key="s.key"><i :style="{background:s.color}"/>{{s.name}}</span></div>
    <label class="trend-date-picker">日期明细<select :value="selected||''" :disabled="!hasData" aria-label="选择折点日期" @change="$emit('select',$event.target.value)"><option value="">全部日期</option><option v-for="p in points" :key="p.date" :value="p.date">{{p.date}} · 有效 {{p.valid}} / 无效 {{p.invalid}}</option></select></label>
    <div class="trend-reading" aria-live="polite"><template v-if="active"><strong>{{active.date}}</strong><span>有效 {{active.valid}} 次 · 无效 {{active.invalid}} 次 · 共 {{active.valid+active.invalid}} 次提交</span><small>来自 {{active.students}} 名学生；点击折点查看当天报告。</small></template><template v-else><strong>{{hasData?'选择一个日期折点':'尚无提交记录'}}</strong><span>{{hasData?'悬停或用键盘聚焦查看数值，点击筛选下方报告。':'保留日期与次数坐标，收到真实提交后绘制折线。'}}</span></template></div>
  </div>
</template>
<script setup>
import {computed,ref,onMounted,onUnmounted} from 'vue'
const props=defineProps({points:{type:Array,default:()=>[]},hasData:Boolean,selected:String})
defineEmits(['select'])
const root=ref(null),width=ref(640),hover=ref('');let observer
onMounted(()=>{observer=new ResizeObserver(([entry])=>width.value=Math.max(280,Math.round(entry.contentRect.width)));observer.observe(root.value)})
onUnmounted(()=>observer?.disconnect())
const series=[{key:'valid',name:'有效训练',color:'#059669'},{key:'invalid',name:'无效提交',color:'#b88740'}]
const ceiling=computed(()=>Math.max(4,...props.points.flatMap(p=>[p.valid,p.invalid])))
const step=computed(()=>Math.max(1,Math.ceil(ceiling.value/4))),max=computed(()=>step.value*4),ticks=computed(()=>[0,1,2,3,4].map(i=>i*step.value))
const x=i=>46+i/Math.max(1,props.points.length-1)*(width.value-66),y=v=>225-v/max.value*190
const path=key=>props.points.map((p,i)=>`${i?'L':'M'}${x(i)},${y(p[key])}`).join(' ')
const active=computed(()=>props.points.find(p=>p.date===(hover.value||props.selected)))
</script>
