import {getClassDemo, classDefinitions} from './teacherRoster.js'
import {loadTasks,lifecycle,isOverdue} from './trainingTasks.js'
import {abilities} from './teacherModel.js'
import {teacherNow,teacherOwner} from './teacherContext.js'
export function reviewsFor(reportId){const raw=localStorage.getItem(`offerpilot:teacher:reviews:v2:${teacherOwner()}:${reportId}`);if(!raw)return [];const data=JSON.parse(raw);if(!Array.isArray(data))throw new Error('点评历史格式异常');return data}
export function saveReportReview(reportId,text){if(!text.trim())throw new Error('请填写点评内容');const entry={id:crypto.randomUUID(),text:text.trim(),author:localStorage.getItem('nickname')||teacherOwner(),at:new Date().toISOString()},history=reviewsFor(reportId);localStorage.setItem(`offerpilot:teacher:reviews:v2:${teacherOwner()}:${reportId}`,JSON.stringify([...history,entry]));return entry}
export function teacherDataset(){
 const roster=getClassDemo(),joined=new Map(roster.members.filter(m=>m.state==='joined').map(m=>[m.studentId,m])),students=roster.students.filter(s=>joined.has(s.id)).map(s=>({...s,classId:joined.get(s.id).classId,className:classDefinitions.find(c=>c.id===joined.get(s.id).classId)?.name,account:joined.get(s.id).account,major:classDefinitions.find(c=>c.id===joined.get(s.id).classId)?.major}))
 const ids=new Set(students.map(s=>s.id)),tasks=loadTasks(),records=roster.records.filter(r=>ids.has(r.studentId)).map(r=>({...r,reportId:r.reportId||r.id,reportAvailable:r.reportAvailable!==false,dimensions:r.common||{},roleDimensions:r.specialty||{},source:r.source==='teacher'?'TEACHER_TASK':'SELF'}))
 const recordIds=new Set(records.map(r=>r.id))
 for(const task of tasks.filter(t=>t.publishedAt))for(const execution of task.executions||[]){
  const student=students.find(s=>s.id===execution.studentId);if(!student)continue
  for(const result of execution.results||[]){if(recordIds.has(result.id)||!result.id||!Number.isFinite(result.score)||result.score<0||result.score>100||result.scale!==100||!result.dimension||!result.scoringVersion)continue
   recordIds.add(result.id);const common=['逻辑表达','项目经历表达','沟通表达','岗位理解'].includes(result.dimension)
   const dimensions=common?{[result.dimension]:result.score}:{},roleDimensions=common?{}:{[result.dimension]:result.score}
   records.push({...result,studentId:student.id,classId:task.classId,position:student.position,model:`task:${result.dimension}`,version:result.scoringVersion,commonVersion:common?`task:${result.dimension}`:undefined,valid:true,reportId:result.reportId||result.id,reportAvailable:true,source:'TEACHER_TASK',taskId:task.id,dimensions,roleDimensions,common:dimensions,specialty:roleDimensions,abilityScores:abilities.map(a=>dimensions[a]??roleDimensions[a]),type:task.trainingMode==='AI_INTERVIEW'?'AI虚拟面试':'专项训练',content:task.title,duration:result.durationMinutes?result.durationMinutes+'分钟':'未提供',scoreScope:result.dimension})
  }
 }
 for(const r of records){r.reviewHistory=reviewsFor(r.reportId);r.reviewStatus=r.reviewHistory.length?'REVIEWED':'UNREVIEWED';r.result=r.reviewHistory.length?'已点评':r.type==='AI虚拟面试'?'待点评':r.result||'训练完成'}
 return {source:'demo',now:teacherNow,referenceDate:'2026-10-03',students,records,tasks,members:roster.members,classes:classDefinitions}
}
export function workspaceDataset(){
 const data=teacherDataset(),tasks=data.tasks.filter(t=>lifecycle(t,data.now)!=='DRAFT').map(t=>{
  const rows=t.executions||[],completed=rows.filter(e=>e.completionStatus==='COMPLETED').length,overdue=rows.filter(e=>!t.manuallyEndedAt&&isOverdue(t,e,data.now)).length
  return {...t,name:t.title,position:t.targetRole,completed,overdue,ongoing:rows.filter(e=>e.completionStatus==='IN_PROGRESS'&&(!isOverdue(t,e,data.now)||t.manuallyEndedAt)).length,notStarted:rows.filter(e=>e.completionStatus==='NOT_STARTED'&&(!isOverdue(t,e,data.now)||t.manuallyEndedAt)).length}
 })
 const overdue=tasks.flatMap(t=>(t.executions||[]).filter(e=>!t.manuallyEndedAt&&isOverdue(t,e,data.now)&&data.students.some(s=>s.id===e.studentId)).map(e=>({id:t.id+'-'+e.studentId,studentId:e.studentId,taskId:t.id,deadline:t.deadline,days:Math.max(0,Math.floor((data.now-Date.parse(t.deadline))/86400000)),status:'未完成'})))
 return {...data,records:data.records.map(r=>({...r,source:r.source==='SELF'?'学生自主':'教师任务'})),tasks,overdue,pending:data.records.filter(r=>r.result==='待点评')}
}
