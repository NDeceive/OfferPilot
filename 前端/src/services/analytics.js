import { studentDataset, abilityDefinitions, abilitySnapshot } from './studentCenter.js'
import { getClassDemo, classDefinitions } from './teacherRoster.js'
import { growthFor } from './teacherModel.js'
export { classDefinitions }
export const common = abilityDefinitions.filter(a => a.scope === 'common')
export const metricsVersion = 'analytics-v1'
export const rules = { minimum: 5, small: 10, observationDays: 14 }
const day = 86400000
export const validRecord = r => r.valid && Number.isFinite(r.score) && r.score >= 0 && r.score <= 100 && Number.isFinite(Date.parse(r.completedAt))
const avg = xs => xs.length ? xs.reduce((a,b)=>a+b,0)/xs.length : null
export const ratio = (n,d) => d ? n/d*100 : null
const date = ms => new Date(ms+8*3600000).toISOString().slice(0,10)
export function analyticsDataset(demo) {
  const data = studentDataset(demo)
  if (!demo) return data
  const members = getClassDemo().members
  data.students = data.students.filter(s=>!s.legacy && members.some(m=>m.studentId===s.id&&m.state==='joined'&&m.account==='normal'))
  const ids = new Set(data.students.map(s=>s.id))
  data.records = data.records.filter(r=>ids.has(r.studentId))
  return data
}
export function contextFor(query, now) {
  const today=date(now), period=query.from&&query.to?'custom':['30d','90d','semester','custom'].includes(query.timeRange||query.period)?(query.timeRange||query.period):'semester'
  const from=period==='custom'?(query.from||date(now-29*day)):period==='semester'?'2026-09-01':date(now-(period==='90d'?89:29)*day)
  const to=period==='custom'?(query.to||today):today
  const start=Date.parse(`${from}T00:00:00+08:00`), end=Math.min(now,Date.parse(`${to}T23:59:59.999+08:00`))
  const error=!Number.isFinite(start)||!Number.isFinite(end)||start>end?'请选择有效的起止日期，开始日期不能晚于结束日期。':end-start>366*day?'单次分析范围最多366天。':''
  return {from,to:Number.isFinite(end)?date(end):today,start,end,error,period,classIds:String(query.classIds||query.classId||'').split(',').filter(Boolean),grade:query.grade||'',major:query.major||''}
}
export function scopeData(data,c) {
  const students=data.students.filter(s=>(!c.classIds.length||c.classIds.includes(s.classId))&&(!c.grade||classDefinitions.find(k=>k.id===s.classId)?.grade===c.grade)&&(!c.major||s.major===c.major))
  const ids=new Set(students.map(s=>s.id))
  return {...data,now:c.end,students,historyRecords:data.records.filter(r=>ids.has(r.studentId)&&validRecord(r)&&Date.parse(r.completedAt)<=c.end),records:data.records.filter(r=>ids.has(r.studentId)&&validRecord(r)&&Date.parse(r.completedAt)>=c.start&&Date.parse(r.completedAt)<=c.end),tasks:data.tasks.filter(t=>t.publishedAt&&(t.executions||[]).some(e=>ids.has(e.studentId)))}
}
export function dueRows(data,c) {
  const ids=new Set(data.students.map(s=>s.id))
  return data.tasks.filter(t=>t.publishedAt&&Date.parse(t.publishedAt)<=c.end&&Date.parse(t.deadline)>=c.start&&Date.parse(t.deadline)<=c.end&&!t.manuallyEndedAt).flatMap(t=>(t.executions||[]).filter(e=>ids.has(e.studentId)).map(e=>{
    const completed=e.completionStatus==='COMPLETED'&&Number.isFinite(Date.parse(e.completedAt))&&Date.parse(e.completedAt)<=c.end
    return {task:t,execution:e,state:completed?(Date.parse(e.completedAt)<=Date.parse(t.deadline)?'onTime':'late'):'unfinished',unknown:e.completionStatus==='COMPLETED'&&!e.completedAt}
  }))
}
export function executionStats(rows) {
  return {total:rows.filter(r=>!r.unknown).length,onTime:rows.filter(r=>r.state==='onTime').length,late:rows.filter(r=>r.state==='late').length,unfinished:rows.filter(r=>r.state==='unfinished'&&!r.unknown).length,unknown:rows.filter(r=>r.unknown).length}
}
export function aggregate(data,ability) {
  const samples=data.students.map(s=>abilitySnapshot(data,s,ability,'all')).filter(s=>s.current!==null&&(ability.scope==='role'||s.target!==null))
  const groups=new Map()
  for(const s of samples){const group=groups.get(s.version)||[];group.push(s);groups.set(s.version,group)}
  const [version,chosen]=[...groups].sort((a,b)=>b[1].length-a[1].length)[0]||[null,[]]
  const n=chosen.length, current=n>=rules.minimum?avg(chosen.map(s=>s.current)):null,target=n>=rules.minimum&&chosen.every(s=>Number.isFinite(s.target))?avg(chosen.map(s=>s.target)):null
  return {ability:ability.name,id:ability.id,n,version,excluded:samples.length-n,current,target,gap:current===null||target===null?null:current-target,achievement:n>=rules.minimum&&target!==null?ratio(chosen.filter(s=>s.current>=s.target).length,n):null}
}
export function weeks(c) {
  const result=[]
  for(let start=c.start;start<=c.end;start+=7*day){const end=Math.min(start+7*day-1,c.end);result.push({start,end,date:date(start),to:date(end),partial:end-start<7*day-1})}
  return result
}
export function trends(data,c,ability) {
  return weeks(c).map(w=>{
    const records=data.records.filter(r=>Date.parse(r.completedAt)>=w.start&&Date.parse(r.completedAt)<=w.end)
    const sample=aggregate({...data,now:w.end,records:data.records.filter(r=>Date.parse(r.completedAt)<=w.end)},ability)
    const execution=executionStats(dueRows(data,{...c,start:w.start,end:w.end}))
    return {...w,coverage:ratio(new Set(records.map(r=>r.studentId)).size,data.students.length),self:records.filter(r=>r.source==='SELF').length,teacherTask:records.filter(r=>r.source==='TEACHER_TASK').length,onTime:ratio(execution.onTime,execution.total),due:execution.total,achievement:sample.achievement,n:sample.n,version:sample.version}
  })
}
export function classComparison(data,c,selected) {
  return selected.map(id=>{
    const own={...data,students:data.students.filter(s=>s.classId===id),records:data.records.filter(r=>data.students.some(s=>s.id===r.studentId&&s.classId===id))}
    return {id,name:classDefinitions.find(k=>k.id===id)?.name||id,n:own.students.length,participation:ratio(new Set(own.records.map(r=>r.studentId)).size,own.students.length),frequency:own.students.length?own.records.length/own.students.length:null,execution:executionStats(dueRows(own,c)),self:own.records.filter(r=>r.source==='SELF').length,teacherTask:own.records.filter(r=>r.source==='TEACHER_TASK').length,abilities:common.map(a=>aggregate(own,a))}
  })
}
export function interventions(data,c,ability,type='') {
  const tasks=data.tasks.filter(t=>(!type||t.type===type)&&(t.type!=='ABILITY'||t.primaryAbility===ability.name))
  const ids=new Set(data.students.map(s=>s.id)), completed=new Map(),pairs=new Map(),outcomes=new Map()
  for(const t of tasks)for(const e of t.executions||[]){
    const at=Date.parse(e.completedAt)
    if(!ids.has(e.studentId)||e.completionStatus!=='COMPLETED'||!Number.isFinite(at)||at<c.start||at>c.end)continue
    if((completed.get(e.studentId)||0)>at)continue
    completed.set(e.studentId,at);pairs.delete(e.studentId);outcomes.delete(e.studentId)
    const own=(data.historyRecords||data.records).filter(r=>validRecord(r)&&r.studentId===e.studentId&&(t.type!=='POSITION'||!t.targetRole||r.position===t.targetRole)&&Number.isFinite((ability.scope==='role'?r.roleDimensions:r.dimensions)?.[ability.name])&&(ability.scope==='role'?r.roleDimensions:r.dimensions)[ability.name]>=0&&(ability.scope==='role'?r.roleDimensions:r.dimensions)[ability.name]<=100&&r.model&&r.version).sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt))
    const before=own.filter(r=>Date.parse(r.completedAt)<Date.parse(t.startTime)).at(-1)
    const after=own.filter(r=>Date.parse(r.completedAt)>at&&Date.parse(r.completedAt)<=Math.min(c.end,at+rules.observationDays*day)).at(-1)
    if(!before||!after||before.position!==after.position||before.model!==after.model||before.version!==after.version||(ability.scope==='common'&&before.commonVersion!==after.commonVersion))continue
    pairs.set(e.studentId,{studentId:e.studentId,taskId:t.id,beforeRecordId:before.id,afterRecordId:after.id,beforeAt:before.completedAt,afterAt:after.completedAt,before:(ability.scope==='role'?before.roleDimensions:before.dimensions)[ability.name],after:(ability.scope==='role'?after.roleDimensions:after.dimensions)[ability.name],version:`${after.model}/${after.version}`})
    const subsequent=own.filter(r=>Date.parse(r.completedAt)>at&&Date.parse(r.completedAt)<=Math.min(c.end,at+rules.observationDays*day)).map(r=>({...r,score:(ability.scope==='role'?r.roleDimensions:r.dimensions)[ability.name],abilityScores:Array(5).fill((ability.scope==='role'?r.roleDimensions:r.dimensions)[ability.name])}))
    outcomes.set(e.studentId,growthFor(subsequent).status)
  }
  // Different role models remain separate; display the largest comparable cohort explicitly.
  const groups=new Map();for(const p of pairs.values()){const g=groups.get(p.version)||[];g.push(p);groups.set(p.version,g)}
  const [version,chosen]=[...groups].sort((a,b)=>b[1].length-a[1].length)[0]||[null,[]]
  const n=chosen.length, before=n>=rules.minimum?avg(chosen.map(p=>p.before)):null,after=n>=rules.minimum?avg(chosen.map(p=>p.after)):null
  const outcomesChosen=chosen.map(p=>outcomes.get(p.studentId))
  return {tasks,execution:executionStats(dueRows({...data,tasks},c)),completed:completed.size,n,excluded:pairs.size-n,version,before,after,diff:before===null?null:after-before,pairs:chosen,outcomes:{improved:outcomesChosen.filter(s=>s==='有效提升').length,stable:outcomesChosen.filter(s=>s==='基本稳定').length,declined:outcomesChosen.filter(s=>['轻微下降','持续下降'].includes(s)).length,insufficient:completed.size-outcomesChosen.filter(s=>s&&s!=='数据不足').length}}
}
