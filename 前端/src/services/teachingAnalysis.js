// Production analysis uses authorized teaching reports only. Unknown versions never join a cohort.
import {growthFor} from './teacherModel.js'
export const stamp=v=>v?Date.parse(/[Z+]/.test(v)?v:v+'+08:00'):NaN
export const dayKey=v=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date(v))
export const pct=(n,d)=>d?n/d*100:null
const mean=xs=>xs.length?xs.reduce((a,b)=>a+b,0)/xs.length:null
export function allocations(tasks){return tasks.flatMap(t=>(t.assignments||[]).filter(a=>!a.exempt).map(a=>({...a,task:t})))}
export function completionAt(a){return (a.attempts||[]).filter(p=>p.state==='READY'&&p.valid!==false).sort((a,b)=>stamp(a.submittedAt)-stamp(b.submittedAt))[a.task.minAttempts-1]?.submittedAt}
export function execution(tasks,from,to){
 const rows=allocations(tasks).filter(a=>!a.task.endedAt&&stamp(a.effectiveDeadline||a.task.deadline)>=from&&stamp(a.effectiveDeadline||a.task.deadline)<=to)
 return rows.reduce((s,a)=>{const at=completionAt(a),done=a.completionStatus==='COMPLETED'&&stamp(at)<=to;if(a.completionStatus==='COMPLETED'&&!at){s.unknown++;return s}s.total++;s[done?(stamp(at)<=stamp(a.effectiveDeadline||a.task.deadline)?'onTime':'late'):'unfinished']++;return s},{total:0,onTime:0,late:0,unfinished:0,unknown:0})
}
export function versionKey(r,m){
 const c=r.detailReport?.trainingContext,p=c?.provenance
 if(!p?.ruleVersion||p.ruleVersion==='LEGACY_UNKNOWN'||!p.modelVersion||!p.promptVersion||m.targetScore==null||m.baseWeight==null)return null
 return JSON.stringify([r.jobId,p.ruleVersion,p.modelVersion,p.promptVersion,m.scoreSource,m.moduleCode,m.targetScore,m.baseWeight,c.scorePlan?.modules?.map(v=>[v.code,v.rank,v.target,v.weight])])
}
export function abilityAggregate(reports,code){
 const latest=new Map()
 for(const r of reports.filter(r=>r.state==='READY').sort((a,b)=>stamp(a.submittedAt)-stamp(b.submittedAt))){const m=r.detailReport?.moduleScores?.find(m=>m.moduleCode===code&&Number.isFinite(m.rawScore));if(m)latest.set(r.studentId,{r,m,key:versionKey(r,m)})}
 const groups=new Map();for(const x of latest.values())if(x.key){const group=groups.get(x.key)||[];group.push(x);groups.set(x.key,group)}
 const chosen=[...groups.values()].sort((a,b)=>b.length-a.length)[0]||[],n=chosen.length
 return {n,excluded:latest.size-n,version:chosen.length?chosen[0].key:null,versionLabel:chosen.length?chosen[0].r.detailReport.trainingContext.provenance.ruleVersion:null,current:n>=5?mean(chosen.map(x=>x.m.rawScore)):null,target:n>=5?mean(chosen.map(x=>x.m.targetScore)):null,gap:n>=5?mean(chosen.map(x=>x.m.rawScore-x.m.targetScore)):null,achievement:n>=5?pct(chosen.filter(x=>x.m.rawScore>=x.m.targetScore).length,n):null}
}
export function weeks(from,to){const result=[];for(let at=from;at<=to;at+=7*86400000){const end=Math.min(to,at+7*86400000-1);result.push({date:dayKey(at),to:dayKey(end),start:at,end,partial:end-at<7*86400000-1})}return result}
export function weekly(reports,tasks,students,from,to,code){return weeks(from,to).map(w=>{const rows=reports.filter(r=>r.state==='READY'&&stamp(r.submittedAt)>=w.start&&stamp(r.submittedAt)<=w.end),e=execution(tasks,w.start,w.end),a=abilityAggregate(reports.filter(r=>stamp(r.submittedAt)<=w.end),code);return {...w,coverage:pct(new Set(rows.filter(r=>students.some(s=>s.id===r.studentId)).map(r=>r.studentId)).size,students.length),teacherTask:rows.length,onTime:pct(e.onTime,e.total),achievement:a.achievement,n:a.n,version:a.version,versionLabel:a.versionLabel}})}
export function moduleOptions(reports){return [...new Map(reports.flatMap(r=>r.detailReport?.moduleScores||[]).map(m=>[m.moduleCode,{id:m.moduleCode,name:m.moduleName||m.moduleCode}])).values()]}
export function studentGrowth(reports){return growthFor(reports.filter(r=>r.state==='READY').map(r=>{const modules=[...(r.detailReport?.moduleScores||[])].sort((a,b)=>a.moduleCode.localeCompare(b.moduleCode)),keys=modules.map(m=>versionKey(r,m)),scores=modules.map(m=>m.rawScore);return {valid:true,completedAt:r.submittedAt,position:String(r.jobId),model:r.detailReport?.trainingContext?.provenance?.modelVersion,version:keys.length===5&&keys.every(Boolean)?JSON.stringify(keys):null,score:mean(scores),abilityScores:scores}}))}
