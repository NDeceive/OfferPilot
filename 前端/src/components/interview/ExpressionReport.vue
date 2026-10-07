<template>
  <section class="expression-report" aria-labelledby="expression-heading">
    <header class="expression-heading">
      <div class="report-title"><h2 id="expression-heading">表情回顾</h2><button ref="helpButton" type="button" class="help-button" aria-label="表情回顾说明" @click="helpDialog.showModal()">?</button></div>
    </header>
    <div class="expression-toolbar">
      <label>统计范围<select v-model="selectedRound"><option value="all">整场面试</option><option v-for="q in data?.questions || []" :key="q.roundNo" :value="String(q.roundNo)">第 {{ q.roundNo }} 题</option></select></label>
    <div class="view-switch" role="group" aria-label="显示内容">
      <button type="button" :aria-pressed="viewMode === 'major'" @click="viewMode = 'major'">主要表情</button>
      <button type="button" :aria-pressed="viewMode === 'all'" @click="viewMode = 'all'">七类概率</button>
    </div>
    </div>
    <div v-if="!timeline.length" class="expression-empty"><strong>{{ selectedRound === 'all' ? '本场没有表情记录' : '本题没有表情记录' }}</strong><p>可能未开启摄像头、识别未成功或记录尚待补传。旧报告不会补造表情数据。</p></div>
    <template v-else>
      <div class="expression-overview">
        <p><span>{{ selectedRound === 'all' ? '本场最常出现' : '本题最常出现' }}</span><strong :title="leaders.map(key=>labels[key]).join('、')">{{ leaders.length > 2 ? leaders.length+' 类并列' : leadingLabel }}</strong><small v-if="leaders.length === 2">并列</small></p>

      </div>
      <p v-if="selectedQuestion" class="question-context">第 {{ selectedQuestion.roundNo }} 题 · {{ selectedQuestion.question }}</p>

      <p v-if="!summary.detectedCount" class="expression-empty">已有 {{ summary.sampleCount }} 次采样，但均未检测到人脸，无法判断主要表情。未检测到人脸不会计入表情占比。</p>
      <template v-else>
        <div class="expression-visuals">
          <div class="timeline-panel">
            <h3>{{ viewMode === 'major' ? '主要表情的分类概率' : '七类表情概率时间线' }}</h3>
            <div v-if="viewMode === 'all'" class="expression-filters" role="group" aria-label="选择显示的表情类别">
              <button v-for="key in EXPRESSION_KEYS" :key="key" type="button" :aria-pressed="visibleKeys.includes(key)" :style="{ '--expression-color': colors[key] }" @click="toggleKey(key)"><i></i>{{ EXPRESSION_LABELS[key] }}</button>
            </div>
            <div ref="chartRef" class="expression-chart" role="img" :aria-label="viewMode === 'major' ? '各次有效采样的最高表情分类概率，颜色表示主要类别，空白为未采集或无脸' : '七类表情概率折线图，空白为未采集或无脸'"></div>
          </div>
          <aside class="distribution-panel">
            <h3>主要表情占比</h3>
            <div ref="pieRef" class="expression-pie" role="img" aria-label="当前统计范围的主要表情样本占比扇形图"></div>
            <div class="distribution-legend"><div v-for="key in distributionKeys" :key="key" class="distribution-row">
              <div><span class="distribution-label"><i :style="{ background: colors[key] }"></i>{{ labels[key] }}</span><strong>{{ percent(countFor(key) / summary.detectedCount) }}</strong></div>

            </div>
            </div>
          </aside>
        </div>
      </template>
      <details class="expression-changes"><summary>变化片段 · {{ changes.length }} 次</summary><div><strong>记录到 {{ changes.length }} 次相邻有效样本类别变化</strong><p>仅定位画面分类变化，需结合回答内容回看。</p><ul><li v-for="(change,index) in changes" :key="index"><span>{{ formatTime((change.capturedAt-scopeStart)/1000) }} · 第 {{ change.roundNo }} 题 · {{ labels[change.category] }}</span><button v-if="canReview" type="button" @click="emit('review-question',change.roundNo)">查看对应回答</button></li></ul><button v-if="canReview && selectedQuestion" type="button" @click="emit('review-question',selectedQuestion.roundNo)">查看本题回答</button></div></details>
      <details class="expression-details"><summary>七类明细</summary>
        <div class="probability-table"><table><caption>当前范围的原始概率统计</caption><thead><tr><th scope="col">表情</th><th scope="col">平均概率</th><th scope="col">主要表情占比</th></tr></thead><tbody><tr v-for="key in EXPRESSION_KEYS" :key="key"><th scope="row">{{ EXPRESSION_LABELS[key] }}</th><td>{{ summary.detectedCount ? percent(summary.averageProbabilities[key]) : '—' }}</td><td>{{ summary.detectedCount ? percent(summary.dominantCounts[key]/summary.detectedCount) : '—' }}</td></tr></tbody></table></div>
      </details>
    </template>
    <details v-if="data?.questions?.length" class="expression-details"><summary>按题回顾 · {{ data.questions.length }} 道主问题</summary>
      <div class="question-list"><div v-for="q in data.questions" :key="q.roundNo" class="question-summary"><strong>第 {{ q.roundNo }} 题</strong><span>{{ q.question }}</span><small>{{ summaryLabel(q.summary) }} · {{ q.summary.detectedCount }}/{{ q.summary.sampleCount }} 次有效采样</small></div></div>
    </details>
    <dialog ref="helpDialog" aria-labelledby="expression-help-title" class="expression-help" @close="helpButton?.focus()"><div class="help-heading"><h3 id="expression-help-title">表情回顾说明</h3><button type="button" aria-label="关闭说明" @click="helpDialog.close()">×</button></div>
        <p class="expression-summary">{{ summary.detectedCount }} / {{ summary.sampleCount }} 次检出人脸 · 人脸检出率 {{ percent(summary.detectedCount / summary.sampleCount) }}<span>未检测到人脸 {{ summary.missingCount }} 次 · 分类不明确 {{ summary.uncertainCount }} 次</span></p>
      <p class="chart-caption">记录范围 {{ recordingSpan }}；空白表示未采集。人脸检出率不代表分类准确率或整场覆盖率。</p>
<p>曲线表示每次采样的最高分类概率；悬停查看类别、最高与次高概率及题目。竖向虚线表示切题，连接采样点的虚线表示无脸或采集中断，期间没有实际采样值。</p>
<p>最高概率不足45%，或前两类差值不足12个百分点时，标为分类不明确。该展示规则不代表模型准确率。</p>
<p>扇形占比按检出人脸的采样次数计算，包含分类不明确样本；不等于面试时长占比。原始七类概率完整保留。</p>
<p>摄像头画面仅在浏览器处理，仅保存概率与时间。结果用于表达复盘，不推断真实情绪，不参与能力评分。PDF／Word包含统计摘要与逐题结果，交互时间线在网页查看。</p>
<button type="button" class="help-close" @click="helpDialog.close()">知道了</button></dialog>
  </section>
</template>
<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, DataZoomComponent, AriaComponent, MarkLineComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { EXPRESSION_KEYS, EXPRESSION_LABELS, classifyExpression, dominantExpression, leadingExpressions, expressionSeries } from '../../utils/expressions'
echarts.use([LineChart, PieChart, GridComponent, TooltipComponent, DataZoomComponent, AriaComponent, MarkLineComponent, CanvasRenderer])
const props = defineProps({ data: Object, startTime: String, canReview: Boolean })
const emit=defineEmits(['review-question'])
const helpDialog=ref(null), helpButton=ref(null)
const selectedRound=ref('all'), viewMode=ref('major'), visibleKeys=ref([...EXPRESSION_KEYS]), chartRef=ref(null), pieRef=ref(null)
const selectedQuestion=computed(()=>props.data?.questions?.find(q=>String(q.roundNo)===selectedRound.value))
const summary=computed(()=>{const base=selectedQuestion.value?.summary || props.data?.summary || {sampleCount:0,detectedCount:0,missingCount:0,averageProbabilities:{}}; const counts=Object.fromEntries(EXPRESSION_KEYS.map(k=>[k,0]));let uncertainCount=0;for(const sample of timeline.value){if(!sample.faceDetected)continue;const key=classifyExpression(sample.probabilities).key;if(key==='uncertain')uncertainCount++;else counts[key]++}return {...base,dominantCounts:counts,uncertainCount}})
const timeline=computed(()=>(props.data?.timeline || []).filter(s=>selectedRound.value==='all'||String(s.roundNo)===selectedRound.value).slice().sort((a,b)=>a.capturedAt-b.capturedAt))
const scopeStart=computed(()=>Number(selectedQuestion.value?.startedAt) || (selectedRound.value !== 'all' ? timeline.value[0]?.capturedAt : 0) || Number(props.data?.startedAt) || new Date(props.startTime?.replace(' ','T')).getTime() || timeline.value[0]?.capturedAt || 0)
const leaders=computed(()=>leadingExpressions(summary.value))
const leadingLabel=computed(()=>leaders.value.length ? leaders.value.map(k=>labels[k]).join('、') : summary.value.detectedCount ? '分类不明确' : '无有效样本')
const percent=value=>Number.isFinite(value) ? (value*100).toFixed(1)+'%' : '—'
const formatTime=seconds=>{const s=Math.max(0,Math.floor(seconds)); return Math.floor(s/60).toString().padStart(2,'0')+':'+(s%60).toString().padStart(2,'0')}
const scopeEnd=computed(()=>Math.max(Number(selectedQuestion.value?.endedAt) || Number(props.data?.endedAt) || scopeStart.value, timeline.value.at(-1)?.capturedAt || scopeStart.value))
const recordingSpan=computed(()=>timeline.value.length ? formatTime((timeline.value[0].capturedAt-scopeStart.value)/1000)+'—'+formatTime((timeline.value.at(-1).capturedAt-scopeStart.value)/1000)+' / 当前范围 '+formatTime((scopeEnd.value-scopeStart.value)/1000) : '无样本')
const summaryLabel=s=>leadingExpressions(s).map(k=>labels[k]).join('、') || (s.detectedCount ? '分类不明确' : '无有效样本')
const labels={...EXPRESSION_LABELS,uncertain:'分类不明确'}
const colors={uncertain:'#dce1de',neutral:'#82b5a3',happy:'#c4d99d',sad:'#a9bdcf',angry:'#d7b9ab',fearful:'#c4bbd4',disgusted:'#afbfb0',surprised:'#e0d19f'}
const distributionKeys=computed(()=>[...EXPRESSION_KEYS,'uncertain'].filter(k=>countFor(k)>0).sort((a,b)=>countFor(b)-countFor(a)))
const countFor=key=>key==='uncertain'?summary.value.uncertainCount:summary.value.dominantCounts[key]
const changes=computed(()=>{const result=[];let previous,previousTime=0;for(const sample of timeline.value){if(!sample.faceDetected){previous=null;continue}const category=classifyExpression(sample.probabilities).key;if(previous && sample.capturedAt-previousTime<=6000 && category!==previous)result.push({...sample,category});previous=category;previousTime=sample.capturedAt}return result})
const escapeHtml=text=>String(text).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')
function questionText(round){return props.data?.questions?.find(q=>Number(q.roundNo)===Number(round))?.question || '题目内容未提供'}
function toggleKey(key){if(visibleKeys.value.length===1 && visibleKeys.value[0]===key)return;visibleKeys.value=visibleKeys.value.includes(key)?visibleKeys.value.filter(k=>k!==key):[...visibleKeys.value,key]}
let chart,pie,observer,generation=0
function dispose(){observer?.disconnect(); observer=null; chart?.dispose(); chart=null; pie?.dispose(); pie=null}
async function render(){
  const token=++generation
  await nextTick()
  if(token!==generation)return
  if(!chartRef.value){dispose();return}
  if(chart && chart.getDom()!==chartRef.value)dispose()
  if(!chart){
    chart=echarts.init(chartRef.value)
    observer=new ResizeObserver(()=>{chart?.resize();pie?.resize()})
    observer.observe(chartRef.value)
  }
  if(pie && pie.getDom()!==pieRef.value){pie.dispose();pie=null}
  if(pieRef.value && !pie){pie=echarts.init(pieRef.value);observer.observe(pieRef.value)}
  pie?.setOption({animation:false,aria:{enabled:true},tooltip:{trigger:'item',confine:true,formatter:p=>p.name+'：'+p.value+' 次（'+percent(p.value/summary.value.detectedCount)+'）'},series:[{type:'pie',radius:'76%',center:['50%','50%'],label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:2},emphasis:{scale:false},data:distributionKeys.value.map(key=>({name:labels[key],value:countFor(key),itemStyle:{color:colors[key]}}))}]},true)
  const start=scopeStart.value
  const samplesByTime=new Map(timeline.value.map(s=>[(s.capturedAt-start)/1000,s]))
  const majorPoints=expressionSeries(timeline.value,'neutral',start).map(point=>{
    const s=samplesByTime.get(point[0])
    if(!s?.faceDetected)return {value:[point[0],null]}
    const classification=classifyExpression(s.probabilities), key=classification.key
    return {value:[point[0],s.probabilities[classification.first]*100],itemStyle:{color:colors[key]},symbol:key==='uncertain'?'diamond':'circle',symbolSize:key==='uncertain'?7:4,label:{show:false}}
  })
  const tooltip=params=>{
    const items=Array.isArray(params)?params:[params]
    const seconds=items[0]?.value?.[0]
    const s=samplesByTime.get(seconds)
    if(!s)return '采集中断'
    const classification=s.faceDetected?classifyExpression(s.probabilities):null
    const key=classification?.key
    const top='第 '+s.roundNo+' 题 · '+formatTime(seconds)+'（全场 '+formatTime((s.capturedAt-(props.data?.startedAt||start))/1000)+'）'
    return top+'<br/>'+escapeHtml(questionText(s.roundNo))+'<br/>'+(key ? labels[key]+'<br/>最高：'+EXPRESSION_LABELS[classification.first]+' '+percent(s.probabilities[classification.first])+' · 次高：'+EXPRESSION_LABELS[classification.second]+' '+percent(s.probabilities[classification.second]) : '未检测到人脸')+
      (viewMode.value==='all'?items.filter(p=>p.value?.[1]!=null).map(p=>'<br/>'+p.seriesName+' '+Number(p.value[1]).toFixed(1)+'%').join(''):'')
  }
  const markers=selectedRound.value==='all'?(props.data?.questions||[]).filter(q=>q.startedAt).map(q=>({xAxis:(q.startedAt-start)/1000,name:'第'+q.roundNo+'题'})):[]
  const markLine={silent:true,symbol:'none',lineStyle:{color:'#94a3b8',type:'dashed'},label:{show:false},data:markers}
  const series=viewMode.value==='major'?[{name:'最高分类概率',type:'line',data:majorPoints,showSymbol:true,symbolSize:4,labelLayout:{hideOverlap:true},lineStyle:{color:colors.neutral,width:2},connectNulls:false,markLine}]:
    visibleKeys.value.map((key,index)=>({name:EXPRESSION_LABELS[key],type:'line',data:expressionSeries(timeline.value,key,start),showSymbol:timeline.value.filter(s=>s.faceDetected).length<=1,symbolSize:5,connectNulls:false,lineStyle:{color:colors[key],width:2,type:index%3===0?'solid':index%3===1?'dashed':'dotted'},itemStyle:{color:colors[key]},...(index===0?{markLine}: {})}))
  for (const line of series) {
    const bridges=[]
    let previous, missing=false
    for (const point of line.data) {
      const value=point.value || point
      if (value[1] == null) { missing=true; continue }
      if (missing && previous) bridges.push([{coord:previous,lineStyle:{type:'dashed',color:line.lineStyle.color,width:1.5}},{coord:value}])
      previous=value; missing=false
    }
    line.markLine={...markLine,data:[...(line.markLine?.data || []),...bridges]}
  }
  chart.setOption({animation:false,textStyle:{color:'#6b7280'},aria:{enabled:true},tooltip:{trigger:'axis',formatter:tooltip,confine:true},
    grid:{left:48,right:16,top:30,bottom:(scopeEnd.value-start)>180000?68:28},
    xAxis:{type:'value',min:0,max:Math.max(1,(scopeEnd.value-start)/1000),axisLabel:{formatter:formatTime},splitLine:{lineStyle:{color:'#eef1ef'}},axisLine:{lineStyle:{color:'#a5afa9'}}},
    yAxis:{type:'value',min:0,max:100,axisLabel:{formatter:'{value}%'},splitLine:{lineStyle:{color:'#eef1ef'}}},
    dataZoom:(scopeEnd.value-start)>180000?[{type:'inside'},{type:'slider',bottom:8,height:20,labelFormatter:formatTime,borderColor:'#dce4df',backgroundColor:'#f6f8f7',fillerColor:'rgba(36,118,95,.10)',handleStyle:{color:'#fff',borderColor:'#849b8f'},moveHandleStyle:{color:'#849b8f'},dataBackground:{lineStyle:{color:'#9caaa3'},areaStyle:{color:'#e0e7e3'}},selectedDataBackground:{lineStyle:{color:'#6d9b89'},areaStyle:{color:'#c6d9d0'}}}]:[],
    series},true)
}
watch(()=>[props.data,props.startTime,selectedRound.value,viewMode.value,visibleKeys.value,chartRef.value,pieRef.value],render,{immediate:true,flush:'post'})
watch(()=>props.data?.questions,()=>{if(selectedRound.value!=='all'&&!selectedQuestion.value)selectedRound.value='all'})
onBeforeUnmount(()=>{generation++;dispose()})
</script>
<style scoped>
.expression-report { margin:28px 0; padding:28px 0; border-top:1px solid var(--neutral-200,#e5e7eb); color:var(--neutral-800,#1f2937); }
.expression-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; }
h2 { margin:0 0 8px; font-size:24px; font-weight:600; } h3 { margin:0 0 8px; font-size:16px; font-weight:600; }
p { margin:6px 0; line-height:1.7; } .expression-heading p,.chart-caption,.chart-note { font-size:14px; color:var(--neutral-600,#4b5563); }
label { display:flex; align-items:center; gap:10px; font-size:14px; white-space:nowrap; }
select { min-height:44px; padding:8px 12px; font:inherit; color:inherit; background:var(--surface-primary,#fff); border:1px solid var(--neutral-300,#d1d5db); border-radius:8px; }
button { font:inherit; color:inherit; cursor:pointer; } button:focus-visible,select:focus-visible,summary:focus-visible { outline:2px solid var(--accent-600,#059669); outline-offset:3px; }
.view-switch { display:inline-flex; gap:4px; padding:4px; margin-top:0; background:var(--neutral-100,#f3f4f6); border-radius:10px; }
.view-switch button { min-height:44px; padding:8px 18px; border:0; border-radius:7px; background:transparent; font-size:14px; }
.view-switch button[aria-pressed=true] { background:var(--surface-primary,#fff); color:var(--accent-700,#047857); font-weight:600; }
.expression-overview { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:16px 0; padding-bottom:16px; border-bottom:1px solid var(--neutral-200,#e5e7eb); }
.expression-overview>p:first-child { display:flex; flex-wrap:wrap; gap:12px; align-items:baseline; font-size:14px; color:var(--neutral-600,#4b5563); }
.expression-overview strong { color:var(--accent-700,#047857); font-size:22px; font-weight:600; }
.expression-summary { font-size:14px; color:var(--neutral-600,#4b5563); } .expression-summary span { display:block; }
.question-context { margin:16px 0 24px; font-size:14px; }
.expression-visuals { display:grid; grid-template-columns:minmax(0,2.1fr) minmax(240px,1fr); gap:24px; }
.timeline-panel { min-width:0; }
.expression-chart { width:100%; height:280px; }
.expression-filters { display:flex; gap:8px; margin:14px 0 8px; }
.expression-filters { flex-wrap:wrap; }
.expression-filters button { min-height:44px; flex:none; display:flex; align-items:center; gap:6px; padding:8px 10px; border:1px solid var(--neutral-200,#e5e7eb); border-radius:8px; background:var(--surface-primary,#fff); font-size:14px; }
.expression-filters i { width:8px; height:8px; flex:none; border-radius:50%; background:var(--expression-color); }
.expression-filters button[aria-pressed=false] { opacity:.5; }
.distribution-panel { padding-left:24px; border-left:1px solid var(--neutral-200,#e5e7eb); }
.distribution-row { margin:10px 0; font-size:14px; }
.distribution-row>div:first-child { display:flex; justify-content:space-between; gap:12px; }
.distribution-row strong { font-weight:600; font-variant-numeric:tabular-nums; }
.distribution-row small { display:block; margin-left:16px; color:var(--neutral-600,#4b5563); font-size:12px; }
.expression-empty { padding:24px 0; font-size:14px; color:var(--neutral-600,#4b5563); }
.expression-details { border-top:1px solid var(--neutral-200,#e5e7eb); margin-top:18px; }
summary { padding:14px 0; min-height:44px; cursor:pointer; font-size:14px; }
.probability-table { overflow-x:auto; margin:8px 0 20px; }
table { width:100%; border-collapse:collapse; text-align:left; font-size:14px; } caption { text-align:left; margin:8px 0 12px; color:var(--neutral-600,#4b5563); }
th,td { padding:12px 10px; border-bottom:1px solid var(--neutral-200,#e5e7eb); }
.question-list .question-summary { display:grid; grid-template-columns:80px minmax(0,1fr); width:100%; text-align:left; padding:14px 0; min-height:44px; border:0; border-bottom:1px solid var(--neutral-100,#f3f4f6); background:transparent; gap:8px; font-size:14px; }
.question-list small { grid-column:2; color:var(--neutral-600,#4b5563); line-height:1.6; }
.question-list span { overflow-wrap:anywhere; line-height:1.6; }
.report-boundary { margin-top:24px; }
@media(max-width:767px) { .expression-heading,.expression-overview { flex-direction:column; align-items:start; gap:12px; } h2 { font-size:22px; } .expression-visuals { grid-template-columns:minmax(0,1fr); gap:20px; } .distribution-panel { border-left:0; padding:20px 0 0; border-top:1px solid var(--neutral-200,#e5e7eb); } .expression-chart { height:240px; } .question-list .question-summary { grid-template-columns:64px minmax(0,1fr); } }
.expression-toolbar { display:flex; flex-wrap:wrap; align-items:center; gap:12px 20px; margin:18px 0 16px; }
.expression-heading { justify-content:flex-start; }
.expression-overview { justify-content:flex-start; flex-wrap:wrap; }
.expression-summary { margin-left:12px; }
.expression-pie { height:220px; width:100%; }
.distribution-label { display:flex; align-items:center; gap:8px; }
.distribution-label i { width:8px; height:8px; border-radius:2px; flex:none; }
@media(max-width:767px) { .expression-toolbar { gap:12px; } .expression-summary { margin-left:0; } .expression-pie { height:240px; } }
.expression-changes { margin:16px 0; font-size:14px; } .expression-changes p { color:var(--neutral-600); } .expression-changes ul { padding:0; list-style:none; max-height:220px; overflow:auto; } .expression-changes li { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:8px; border-bottom:1px solid var(--neutral-100); } .expression-changes button { min-height:44px; padding:8px 12px; border:0; background:transparent; color:var(--accent-700); font-size:14px; }
.report-title { display:flex; gap:10px; align-items:center; } .report-title h2 { margin:0; }
.help-button { min-width:44px; min-height:44px; border:0; background:transparent; color:var(--neutral-500,#6b7280); font-size:18px; }
.expression-help { width:min(540px,calc(100vw - 32px)); max-height:80dvh; overflow:auto; border:1px solid var(--neutral-200,#e5e7eb); border-radius:14px; padding:24px; color:var(--neutral-700,#374151); font-size:14px; }
.expression-help::backdrop { background:rgba(20,35,28,.3); } .expression-help p { margin:14px 0; } .help-heading { display:flex; align-items:center; justify-content:space-between; } .help-heading button,.help-close { min-height:44px; min-width:44px; border:0; background:var(--neutral-100,#f3f4f6); border-radius:8px; } .help-close { width:100%; margin-top:12px; }
.distribution-legend { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:8px 14px; } .distribution-row { margin:0; font-size:13px; } .distribution-row>div { align-items:center; gap:6px; } .distribution-label { gap:6px; } .distribution-row strong { font-size:12px; font-weight:500; } .distribution-panel { padding-left:20px; } .expression-overview { margin:12px 0; padding-bottom:12px; } .expression-chart { height:300px; } .expression-pie { height:230px; }
@media(max-width:767px) { .distribution-panel { padding-left:0; } .distribution-legend { gap:10px 16px; } }
.expression-help .expression-summary { margin-left:0; } .distribution-row>div { flex-wrap:wrap; } </style>
