<template>
  <div class="teaching-insights">
    <div class="live-filterbar">
      <label>班级<select v-model="classFilter" aria-label="筛选教学班级"><option value="">全部班级</option><option v-for="c in classes" :key="c.id" :value="String(c.id)">{{c.name}}</option></select></label>
      <label>任务<select v-model="taskFilter" aria-label="筛选教学任务"><option value="">全部已发布任务（含历史）</option><option v-for="t in taskOptions" :key="t.id" :value="String(t.id)">{{t.title}} · {{t.className}}</option></select></label>
      <label>报告周期<select v-model.number="period" @change="router.replace({query:{...route.query,classId:classFilter||undefined,taskId:taskFilter||undefined,search:search||undefined,period,from:undefined,to:undefined,date:undefined}})"><option v-if="reviews" value="all">全部待点评报告</option><option :value="7">近 7 天</option><option :value="30">近 30 天</option><option :value="90">近 90 天</option></select></label>
      <span>执行截至当前；提交与报告按日期周期筛选。教师待办为全局。</span>
    </div>
    <p v-if="route.query.from||route.query.classIds||route.query.jobId" class="chart-caption">来源范围：{{route.query.from||'当前周期'}} ～ {{route.query.to||'今天'}}；{{route.query.classIds?'指定班级 '+route.query.classIds:'当前班级范围'}}{{route.query.jobId?' · 指定岗位':''}}。</p>

    <dl v-if="!recordsOnly" class="live-metrics">
      <div v-for="item in metrics" :key="item.label"><dt>{{item.label}}</dt><dd>{{item.value}}<small>{{item.unit}}</small></dd><p>{{item.note}}</p></div>
    </dl>

    <div v-if="!recordsOnly" class="live-chart-grid">
      <section class="live-panel live-trend">
        <header><div><h2>训练提交趋势</h2><p>近 {{period}} 天 · 有效与无效提交分别展示</p></div><RouterLink :to="{path:'/teacher/training-records',query:{classId:classFilter||undefined,taskId:taskFilter||undefined,period}}">查看记录 →</RouterLink></header>
        <TeachingTrendChart :points="trend" :has-data="!!datedReports.length" :selected="selectedDay" @select="selectDay"/>
        <button v-if="selectedDay" class="trend-record-jump" @click="recordsSection?.scrollIntoView()">查看 {{selectedDay}} 的报告 ↓</button>
        <p v-if="!datedReports.length" class="chart-caption">还没有训练提交。学生完成任务后，这里会显示真实的训练趋势。</p>
        <details class="chart-data"><summary>查看趋势数据</summary><div class="table-scroll"><table><thead><tr><th>日期</th><th>有效训练</th><th>无效提交</th></tr></thead><tbody><tr v-for="p in trend" :key="p.date"><td>{{p.date}}</td><td>{{p.valid}}</td><td>{{p.invalid}}</td></tr></tbody></table></div></details>
      </section>
      <section class="live-panel">
        <header><div><h2>任务完成分布</h2><p>{{selectedTask?.title||'全部已发布任务'}} · {{allocations.length}} 人次分配 · 截至当前（减免不计入）</p></div></header>
        <TeachingPieChart :items="distribution" :selected="selectedStatus" @select="selectedStatus=$event"/>
        <div v-if="selectedStatus" class="pie-drilldown"><h3>{{distribution.find(d=>d.key===selectedStatus)?.label}} · 分配明细</h3><ul class="chart-people"><li v-for="a in selectedAllocations.slice(0,6)" :key="a.id"><RouterLink :to="{path:'/teacher/students/'+a.studentId,query:{returnTo:contextReturn}}">{{a.name}}</RouterLink><RouterLink :to="{path:'/teacher/tasks/'+a.taskId,query:{returnTo:contextReturn}}">{{a.taskTitle}} →</RouterLink></li></ul><p v-if="selectedAllocations.length>6" class="chart-caption">共 {{selectedAllocations.length}} 人次，展示前 6 条；进入任务查看全部分配。</p></div>
        <div v-if="!allocations.length" class="panel-next-action"><strong>先建立你的教学任务</strong><p>创建班级、审核学生加入，再发布任务。图表会随真实记录更新。</p><RouterLink to="/teacher/classes">前往班级管理 →</RouterLink></div>
      </section>
      <section class="live-panel">
        <header><div><h2>班级执行对比</h2><p>完成分配 / 已发布任务分配</p></div></header>
        <div v-if="classRows.length" class="class-bars"><div v-for="c in classRows" :key="c.id" class="class-bar"><RouterLink :to="'/teacher/classes/'+c.id">{{c.name}}</RouterLink><div class="bar-track"><span :style="{width:(c.assigned?c.completed/c.assigned*100:0)+'%'}"/></div><strong>{{c.assigned?Math.round(c.completed/c.assigned*100)+'%':'—'}}</strong><small>{{c.completed}} / {{c.assigned}} 人次 · {{c.valid}} 次有效训练</small></div></div>
        <div v-else class="live-empty"><h3>班级数据等待建立</h3><p>创建教学班后，会在这里展示每个班级的任务执行情况。</p><RouterLink to="/teacher/classes">创建教学班 →</RouterLink></div>
      </section>
      <section class="live-panel">
        <header><div><h2>{{analytics?'岗位能力分析':'教师待办'}}</h2><p>{{analytics?'按岗位选择学生原始报告，查看模块表现':'汇总你管理的全部班级待办'}}</p></div></header>
        <template v-if="analytics">
          <div class="ability-filters"><label>岗位<select v-model="abilityJob"><option v-for="j in jobOptions" :key="j.id" :value="String(j.id)">{{j.name}}</option><option v-if="!jobOptions.length" value="">暂无岗位报告</option></select></label><label>评分来源<select v-model="scoreSource"><option v-for="s in sourceOptions" :key="s" :value="s">{{s==='AI'?'AI 评分':s==='RULE'?'规则评分':s}}</option><option v-if="!sourceOptions.length" value="">尚无评分</option></select></label></div>
          <label v-if="reportOptions.length" class="ability-report-select">学生报告<select v-model="selectedReport" aria-label="选择学生原始报告"><option v-for="r in reportOptions" :key="r.reportId" :value="String(r.reportId)">{{r.name}} · {{formatTime(r.submittedAt)}} · {{r.title}}</option></select></label>
          <div v-if="abilityRows.length" class="ability-bars"><div v-for="a in abilityRows" :key="a.code"><span>{{a.name}}</span><div class="bar-track ability-target-track"><span :style="{width:Math.min(100,a.value)+'%'}"/><b v-if="a.target!=null" :style="{left:Math.min(100,a.target)+'%'}" :title="'目标 '+a.target+' 分'"/></div><strong>{{a.value.toFixed(1)}}</strong><small>目标 {{a.target??'未提供'}} · 权重 {{a.weight==null?'未提供':(a.weight*100).toFixed(0)+'%'}} · 差距 {{a.gap??'未提供'}}</small></div></div>
          <div v-else class="live-empty"><h3>{{reportLoadError?'评分数据暂时无法加载':'暂无可分析的模块评分'}}</h3><p>{{reportLoadError?'执行统计仍可查看。请刷新页面重新读取原始报告。':'完整报告生成后，这里会按岗位展示真实能力数据。'}}</p><button v-if="reportLoadError" @click="$emit('reload')">重新读取报告</button></div>
          <p class="chart-caption">展示单份有效报告的原始分与目标线。历史报告未记录完整版本，暂不计算跨报告能力均值；评分来源也不等同于评分版本。</p>
        </template>
        <div v-else class="teacher-todos"><RouterLink to="/teacher/reviews"><span><strong>报告待点评</strong><small>查看学生回答与评分依据</small></span><b>{{summary.pendingReviewTotal||0}}<span>份 →</span></b></RouterLink><RouterLink to="/teacher/classes"><span><strong>加入申请待审核</strong><small>审核后学生才能接收教学任务</small></span><b>{{classes.reduce((n,c)=>n+(c.pendingCount||0),0)}}<span>条 →</span></b></RouterLink><RouterLink to="/teacher/tasks"><span><strong>进行中的任务</strong><small>跟进尚未完成的训练分配</small></span><b>{{tasks.filter(t=>t.lifecycle==='ACTIVE').length}}<span>项 →</span></b></RouterLink></div>
      </section>
    </div>

    <TeachingScatterChart v-if="!recordsOnly" :points="studentPoints" :task-minimum="selectedTask?.minAttempts||2" :focused-task="!!selectedTask" :return-to="contextReturn"/>

    <section ref="recordsSection" class="live-panel live-records">
      <header><div><h2>{{reviews?'待点评报告':'教学训练记录'}}</h2><p>{{reviews?'结合原始问答与报告，给学生具体的改进建议。':'保留有效与无效结果，打开报告查看评分依据和教师反馈。'}}</p></div><label class="record-search"><input v-model="search" type="search" aria-label="搜索学生或任务" placeholder="搜索学生或任务"/></label></header>
      <div v-if="selectedDay" class="chart-record-filter"><span>折点筛选：{{selectedDay}} · {{visibleReports.length}} 份报告</span><button @click="selectedDay=''">清除日期筛选</button></div>
      <div v-if="visibleReports.length" class="table-scroll"><table><thead><tr><th>学生</th><th>训练任务</th><th>提交时间</th><th>结果</th><th>操作</th></tr></thead><tbody><tr v-for="r in pagedReports" :key="r.id"><td><RouterLink :to="'/teacher/students/'+r.studentId">{{r.name}}</RouterLink></td><td><RouterLink :to="'/teacher/tasks/'+r.taskId">{{r.title}}</RouterLink><small>{{r.className}}</small></td><td>{{formatTime(r.submittedAt)}}</td><td><span class="status-tag" :class="r.state==='READY'?'positive':'warning'">{{r.state==='READY'?'有效结果':'无效提交'}}</span></td><td><RouterLink :to="{path:'/teacher/reports/'+r.reportId,query:{returnTo:contextReturn}}">查看报告{{reviews?'并点评':''}} →</RouterLink></td></tr></tbody></table></div>
      <div v-else class="live-empty"><h3>{{selectedDay?'当天没有匹配报告':search?'没有匹配的训练记录':reviews?'当前没有待点评报告':'还没有教学训练记录'}}</h3><p>{{selectedDay?'选择其他折点，或清除日期筛选查看整个周期。':search?'请尝试其他学生姓名或任务关键词。':reviews?'学生提交报告后，待点评内容会出现在这里。':'发布任务并由学生完成训练后，结果会自动汇总在这里。'}}</p><RouterLink v-if="!search&&!selectedDay" :to="reviews?'/teacher/tasks':'/teacher/tasks/create'">{{reviews?'查看任务进度':'创建训练任务'}} →</RouterLink><button v-else @click="search='';selectedDay=''">清除筛选</button></div>
      <footer v-if="visibleReports.length" class="live-pagination"><span>{{visibleReports.length}} 份报告</span><div><button :disabled="page<=1" @click="page--">上一页</button><span>{{page}} / {{pages}}</span><button :disabled="page>=pages" @click="page++">下一页</button></div></footer>
    </section>
  </div>
</template>

<script setup>
import {computed,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {inTeachingScope} from '../../services/teachingWorkspace.js'
import TeachingTrendChart from './TeachingTrendChart.vue'
import TeachingPieChart from './TeachingPieChart.vue'
import TeachingScatterChart from './TeachingScatterChart.vue'
const props=defineProps({classes:{type:Array,default:()=>[]},tasks:{type:Array,default:()=>[]},reports:{type:Array,default:()=>[]},summary:{type:Object,default:()=>({})},reportLoadError:Boolean})
defineEmits(['reload'])
const route=useRoute(),router=useRouter(),classFilter=ref(String(route.query.classId||'')),period=ref(route.query.period==='all'||route.path.includes('reviews')&&!route.query.period?'all':[7,30,90].includes(Number(route.query.period))?Number(route.query.period):30),search=ref(String(route.query.search||'')),page=ref(Math.max(1,Number(route.query.page)||1)),selectedDay=ref(/^\d{4}-\d{2}-\d{2}$/.test(String(route.query.date))?String(route.query.date):''),selectedStatus=ref(String(route.query.execution||'')),abilityJob=ref(String(route.query.abilityJob||'')),scoreSource=ref(String(route.query.scoreSource||'')),selectedReport=ref(String(route.query.reportId||''))
const recordsSection=ref(null)
const taskFilter=ref(String(route.query.taskId||'')),taskOptions=computed(()=>props.tasks.filter(t=>inTeachingScope(t,{...route.query,classId:classFilter.value,taskId:undefined},props.classes))),selectedTask=computed(()=>taskOptions.value.find(t=>String(t.id)===taskFilter.value))
const contextReturn=computed(()=>route.path+'?'+new URLSearchParams({...Object.fromEntries(['classIds','from','to','jobId','returnTo'].filter(k=>route.query[k]).map(k=>[k,String(route.query[k])])),...(classFilter.value?{classId:classFilter.value}:{}),...(taskFilter.value?{taskId:taskFilter.value}:{}),period:String(period.value),...(search.value?{search:search.value}:{}),...(selectedDay.value?{date:selectedDay.value}:{}),page:String(page.value),...(analytics.value&&abilityJob.value?{abilityJob:abilityJob.value}:{}),...(analytics.value&&scoreSource.value?{scoreSource:scoreSource.value}:{}),...(analytics.value&&selectedReport.value?{reportId:selectedReport.value}:{}),...(selectedStatus.value?{execution:selectedStatus.value}:{})}))
const analytics=computed(()=>route.path.includes('analytics')),reviews=computed(()=>route.path.includes('reviews')),recordsOnly=computed(()=>reviews.value||route.path.includes('training-records'))
const scopedTasks=computed(()=>taskOptions.value.filter(t=>!taskFilter.value||String(t.id)===taskFilter.value))
const allocations=computed(()=>scopedTasks.value.flatMap(t=>(t.assignments||[]).filter(a=>!a.exempt).map(a=>({...a,taskId:t.id,taskTitle:t.title,required:t.minAttempts}))))
const selectedAllocations=computed(()=>allocations.value.filter(a=>a.completionStatus===selectedStatus.value))
const studentPoints=computed(()=>{const students=new Map();for(const a of allocations.value){if(!students.has(a.studentId))students.set(a.studentId,{id:a.studentId,name:a.name,count:0,assigned:0,completed:0,required:0,credited:0});const p=students.get(a.studentId);p.count+=Number(a.validCount)||0;p.assigned++;p.required+=a.required;p.credited+=Math.min(Number(a.validCount)||0,a.required);p.completed+=a.completionStatus==='COMPLETED'?1:0}return [...students.values()].map(p=>({...p,rate:p.required?p.credited/p.required*100:0}))})
const scopedReports=computed(()=>props.reports.filter(r=>(!classFilter.value||String(r.classId)===classFilter.value)&&(!route.query.classIds||String(route.query.classIds).split(',').includes(String(r.classId)))&&(!route.query.jobId||String(r.jobId)===String(route.query.jobId))&&(!taskFilter.value||String(r.taskId)===taskFilter.value)))
const metrics=computed(()=>[{label:selectedTask.value?'本任务参与学生':'已加入学生',value:selectedTask.value?selectedTask.value.assignments.length:classFilter.value?props.classes.find(c=>String(c.id)===classFilter.value)?.joinedCount||0:props.summary.studentTotal||0,unit:'人',note:selectedTask.value?'包含减免学生；完成率排除减免分配':'当前班级成员，与历史任务参与名单分别统计'},
 {label:'已发布任务',value:scopedTasks.value.length,unit:'项',note:'包含进行中与已结束任务'},
 {label:taskFilter.value?'本任务次数完成率':'全部任务分配完成率',value:allocations.value.length?Math.round(allocations.value.filter(a=>a.completionStatus==='COMPLETED').length/allocations.value.length*100)+'%':'—',unit:'',note:allocations.value.filter(a=>a.completionStatus==='COMPLETED').length+' / '+allocations.value.length+' 人次分配'},
 {label:'有效训练',value:scopedTasks.value.flatMap(t=>t.assignments||[]).reduce((n,a)=>n+(a.validCount||0),0),unit:'次',note:'符合任务要求的就绪报告'}])
const distribution=computed(()=>[{key:'COMPLETED',label:'已完成',color:'#059669'},{key:'IN_PROGRESS',label:'尚未完成要求',color:'#94b8a8'},{key:'PENDING_VALIDATION',label:'报告校验中',color:'#d9ae68'},{key:'NOT_STARTED',label:'未开始',color:'#d4d4d8'}].map(d=>({...d,count:allocations.value.filter(a=>a.completionStatus===d.key).length})))
const classRows=computed(()=>props.classes.filter(c=>!classFilter.value||String(c.id)===classFilter.value).map(c=>{const tasks=scopedTasks.value.filter(t=>t.classId===c.id),items=tasks.flatMap(t=>t.assignments||[]);return {...c,assigned:items.filter(a=>!a.exempt).length,completed:items.filter(a=>a.completionStatus==='COMPLETED').length,valid:items.reduce((n,a)=>n+(a.validCount||0),0)}}))
function timestamp(value){return Date.parse(value&&/[Z+]/.test(value)?value:value+'+08:00')}
const dateKey=timestamp=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(timestamp))
const formatTime=v=>new Date(timestamp(v)).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false})
const datedReports=computed(()=>{const today=String(route.query.to||dateKey(Date.now())),first=String(route.query.from||(period.value==='all'?'0000-01-01':dateKey(Date.now()-(period.value-1)*86400000)));return scopedReports.value.filter(r=>r.submittedAt&&Number.isFinite(timestamp(r.submittedAt))&&timestamp(r.submittedAt)<=Date.now()&&dateKey(timestamp(r.submittedAt))>=first&&dateKey(timestamp(r.submittedAt))<=today)})
const trend=computed(()=>Array.from({length:period.value},(_,index)=>{const date=dateKey(Date.now()-(period.value-1-index)*86400000),rows=datedReports.value.filter(r=>dateKey(timestamp(r.submittedAt))===date);return {date,valid:rows.filter(r=>r.state==='READY').length,invalid:rows.filter(r=>r.state==='INVALID').length,students:new Set(rows.map(r=>r.studentId)).size}}))
const jobOptions=computed(()=>[...new Map(datedReports.value.filter(r=>r.state==='READY'&&r.detailReport).map(r=>[r.jobId,{id:r.jobId,name:r.jobName}])).values()])
const sourceOptions=computed(()=>[...new Set(datedReports.value.filter(r=>r.state==='READY'&&String(r.jobId)===abilityJob.value).flatMap(r=>r.detailReport?.moduleScores||[]).map(m=>m.scoreSource||'未提供来源'))])
const reportOptions=computed(()=>datedReports.value.filter(r=>r.state==='READY'&&String(r.jobId)===abilityJob.value&&r.detailReport?.moduleScores?.some(m=>(m.scoreSource||'未提供来源')===scoreSource.value)).sort((a,b)=>timestamp(b.submittedAt)-timestamp(a.submittedAt)))
const abilityRows=computed(()=>(reportOptions.value.find(r=>String(r.reportId)===selectedReport.value)?.detailReport?.moduleScores||[]).filter(m=>(m.scoreSource||'未提供来源')===scoreSource.value&&m.rawScore!=null&&Number.isFinite(Number(m.rawScore))).map(m=>({code:m.moduleCode,name:m.moduleName||m.moduleCode,value:Number(m.rawScore),count:1,target:m.targetScore,weight:m.baseWeight,gap:m.gapScore})))
const visibleReports=computed(()=>datedReports.value.filter(r=>(!selectedDay.value||dateKey(timestamp(r.submittedAt))===selectedDay.value)&&`${r.name} ${r.title}`.includes(search.value.trim())))
const pages=computed(()=>Math.max(1,Math.ceil(visibleReports.value.length/10))),pagedReports=computed(()=>visibleReports.value.slice((page.value-1)*10,page.value*10))
watch([classFilter,period,search],()=>{page.value=1;selectedDay.value=''})
watch(classFilter,()=>selectedStatus.value='')
watch(taskFilter,()=>{page.value=1;selectedDay.value='';selectedStatus.value=''})
watch(taskOptions,options=>{if(taskFilter.value&&!options.some(t=>String(t.id)===taskFilter.value))taskFilter.value=''})
watch(selectedDay,()=>page.value=1)
function selectDay(date){selectedDay.value=date}
watch(jobOptions,options=>{if(!options.some(j=>String(j.id)===abilityJob.value))abilityJob.value=options.length?String(options[0].id):''},{immediate:true})
watch(sourceOptions,options=>{if(!options.includes(scoreSource.value))scoreSource.value=options[0]||''},{immediate:true})
watch(reportOptions,options=>{if(!options.some(r=>String(r.reportId)===selectedReport.value))selectedReport.value=options[0]?String(options[0].reportId):''},{immediate:true})
watch(pages,n=>page.value=Math.min(page.value,n))
</script>
