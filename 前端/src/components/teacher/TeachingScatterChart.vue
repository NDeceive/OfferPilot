<template>
  <section class="live-panel live-scatter">
    <header><div><h2>学生训练分布</h2><p>横轴为有效训练次数，纵轴为有效次数要求进度 · 全任务周期</p></div><span>{{points.length}} 名已分配任务的学生</span></header>
    <div class="scatter-layout">
      <div ref="root" class="scatter-plot">
        <svg :viewBox="`0 0 ${width} 330`" role="group" aria-label="学生有效训练次数与次数要求进度散点分布图">
          <rect v-if="focusedTask" x="52" y="30" :width="x(taskMinimum)-52" :height="y(50)-30" fill="#f4f4f5"/><rect v-if="focusedTask" :x="x(taskMinimum)" y="30" :width="width-22-x(taskMinimum)" :height="y(50)-30" fill="#ecfdf5"/>
          <rect v-if="focusedTask" x="52" :y="y(50)" :width="x(taskMinimum)-52" :height="270-y(50)" fill="#fafaf9"/><rect v-if="focusedTask" :x="x(taskMinimum)" :y="y(50)" :width="width-22-x(taskMinimum)" :height="270-y(50)" fill="#f0fdf4"/>
          <text x="52" y="18">次数要求进度 (%)</text>
          <g v-for="n in [0,25,50,75,100]" :key="n"><line x1="52" :x2="width-22" :y1="y(n)" :y2="y(n)" stroke="#e4e4e7"/><text x="42" :y="y(n)+4" text-anchor="end">{{n}}</text></g>
          <line v-if="focusedTask" :x1="x(taskMinimum)" :x2="x(taskMinimum)" y1="30" y2="270" stroke="#a1a1aa" stroke-dasharray="5 4"/><line v-if="focusedTask" x1="52" :x2="width-22" :y1="y(50)" :y2="y(50)" stroke="#a1a1aa" stroke-dasharray="5 4"/>
          <g v-for="group in clusters" :key="group.key" role="button" tabindex="0" :aria-label="`${group.people.map(p=>p.name).join('、')}，${group.count} 次有效训练，次数进度 ${group.rate.toFixed(1)}%，共 ${group.people.length} 人`" @mouseenter="hover=group.key" @mouseleave="hover=''" @focus="hover=group.key" @blur="hover=''" @click="selected=selected===group.key?'':group.key" @keydown.enter.prevent="selected=group.key" @keydown.space.prevent="selected=group.key">
            <circle :cx="x(group.count)" :cy="y(group.rate)" r="20" fill="transparent"/>
            <circle :cx="x(group.count)" :cy="y(group.rate)" :r="Math.min(16,6+Math.sqrt(group.people.length)*2)" :fill="region&&group.region!==region?'#a1a1aa':'#059669'" :opacity="region&&group.region!==region ? 0.3 : 0.85" :stroke="active?.key===group.key?'#18181b':'white'" stroke-width="2"/>
            <text v-if="group.people.length>1" :x="x(group.count)" :y="y(group.rate)+4" text-anchor="middle" class="cluster-count">{{group.people.length}}</text>
          </g>
          <text v-for="n in xTicks" :key="n" :x="x(n)" y="291" text-anchor="middle">{{n}}</text><text :x="width-22" y="318" text-anchor="end">有效训练次数 (次)</text>
        </svg>
        <p class="chart-caption">同坐标学生合并显示，点内数字为人数。{{focusedTask?'参考线来自本任务要求：'+taskMinimum+' 次、50% 次数进度。用于执行跟进，不代表能力评级。':'全部任务仅展示执行总量；请选择具体任务进行分部分析。'}}</p>
        <p v-if="!points.length" class="chart-caption">暂无可绘制学生。发布任务后，将展示真实训练次数与次数进度。</p>
      </div>
      <div class="scatter-reading">
        <h3>{{focusedTask?'分部分析':'学生执行明细'}}</h3>
        <div v-if="focusedTask" class="scatter-regions"><button v-for="r in regions" :key="r.key" :aria-pressed="region===r.key" @click="region=region===r.key?'':r.key;selected=''">{{r.label}}<strong>{{points.filter(p=>regionKey(p)===r.key).length}} 人</strong><small>{{r.rule}}</small></button></div>
        <div class="scatter-selection" aria-live="polite"><strong>{{active?`${active.people.length} 人位于此点`:region?regions.find(r=>r.key===region).label:'点击散点或选择分部'}}</strong><p>{{active?`${active.count} 次有效训练 · ${active.rate.toFixed(1)}% 次数要求进度`:region?'下方展示这一分部的学生，可打开详情跟进。':'查看学生实际任务分配、次数进度与有效训练次数。'}}</p></div>
        <ul v-if="selectedPeople.length" class="chart-people"><li v-for="p in selectedPeople" :key="p.id"><RouterLink :to="{path:'/teacher/students/'+p.id,query:{returnTo}}">{{p.name}} →</RouterLink><span>{{p.count}} 次有效训练 · 完成 {{p.completed}} / {{p.assigned}} 项</span></li></ul>
        <p v-else-if="region" class="chart-caption">这一分部暂无学生。</p>
      </div>
    </div>
    <details class="chart-data"><summary>查看学生分布数据</summary><div class="table-scroll"><table><thead><tr><th>学生</th><th>有效训练</th><th>已完成 / 已分配任务</th><th>次数进度</th></tr></thead><tbody><tr v-for="p in points" :key="p.id"><td><RouterLink :to="'/teacher/students/'+p.id">{{p.name}}</RouterLink></td><td>{{p.count}} 次</td><td>{{p.completed}} / {{p.assigned}}</td><td>{{p.rate.toFixed(1)}}%</td></tr></tbody></table></div></details>
  </section>
</template>
<script setup>
import {computed,ref,watch,onMounted,onUnmounted} from 'vue'
const props=defineProps({points:{type:Array,default:()=>[]},taskMinimum:{type:Number,default:2},focusedTask:Boolean,returnTo:String})
const root=ref(null),width=ref(640),selected=ref(''),hover=ref(''),region=ref('');let observer
onMounted(()=>{observer=new ResizeObserver(([entry])=>width.value=Math.max(280,Math.round(entry.contentRect.width)));observer.observe(root.value)})
onUnmounted(()=>observer?.disconnect())
const max=computed(()=>Math.max(4,props.taskMinimum*2,...props.points.map(p=>p.count))),x=n=>52+n/max.value*(width.value-74),y=n=>270-n/100*240
const xTicks=computed(()=>[...new Set([0,...Array.from({length:4},(_,i)=>Math.round(max.value*(i+1)/4)),props.taskMinimum])].sort((a,b)=>a-b))
const regionKey=p=>(p.count>=props.taskMinimum?'more':'less')+(p.rate>=50?'High':'Low')
const regions=computed(()=>[{key:'lessLow',label:'训练较少 · 进度不足',rule:'< '+props.taskMinimum+' 次 / 进度 < 50%'},{key:'moreLow',label:'已有训练 · 进度不足',rule:'≥ '+props.taskMinimum+' 次 / 进度 < 50%'},{key:'lessHigh',label:'训练较少 · 进度较高',rule:'< '+props.taskMinimum+' 次 / 进度 ≥ 50%'},{key:'moreHigh',label:'持续训练 · 进度较高',rule:'≥ '+props.taskMinimum+' 次 / 进度 ≥ 50%'}])
const clusters=computed(()=>{const groups=new Map();for(const p of props.points){
 const coordinate=`${p.count}:${p.rate}`;if(!groups.has(coordinate))groups.set(coordinate,{key:coordinate,count:p.count,rate:p.rate,region:regionKey(p),people:[]});groups.get(coordinate).people.push(p)}return [...groups.values()]})
const active=computed(()=>clusters.value.find(g=>g.key===(hover.value||selected.value)))
const selectedPeople=computed(()=>active.value?.people|| (region.value?props.points.filter(p=>regionKey(p)===region.value):[]))
watch(()=>props.points,()=>{selected.value='';hover.value='';region.value=''})
</script>
