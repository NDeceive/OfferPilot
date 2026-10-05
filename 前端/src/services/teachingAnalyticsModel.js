import {versionKey,moduleOptions} from './teachingAnalysis.js'
export const truth=v=>v===true||v===1
export const time=v=>v?Date.parse(/(?:Z|[+-]\d\d:\d\d)$/.test(String(v))?v:String(v)+'+08:00'):NaN
export const dateKey=v=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date(v))
export function dateRange(from,to,now=Date.now()){
 const start=time(from+'T00:00:00'),end=Math.min(time(to+'T00:00:00')+86400000,now+1)
 const datesValid=Number.isFinite(start)&&Number.isFinite(time(to+'T00:00:00'))&&dateKey(start)===from&&dateKey(time(to+'T00:00:00'))===to
 return {start,end,error:!datesValid||!Number.isFinite(end)||start>=end?'请选择有效的起止日期。':end-start>366*86400000?'单次分析最多366天。':''}
}
export function analyticsFacts(tasks,reports){
 const assignments=tasks.filter(t=>t.publishedAt).flatMap(task=>(task.assignments||[]).map(a=>({...a,task})))
 const attempts=[...new Map(assignments.flatMap(a=>(a.attempts||[]).map((p,i)=>({...p,id:String(p.id??p.sessionId??a.id+'-'+i),assignmentId:a.id,studentId:a.studentId,taskId:a.task.id,classId:a.task.classId,jobId:a.task.jobId}))).map(p=>[p.id,p])).values()]
 return {assignments,attempts,reports:[...new Map(reports.map(r=>[String(r.reportId),r])).values()]}
}
const valid=p=>p.state==='READY'&&truth(p.valid)&&Number.isFinite(time(p.submittedAt))
const goodScore=m=>m&&Number.isFinite(m.rawScore)&&m.rawScore>=0&&m.rawScore<=100
export const gapGroups=[{key:'large',name:'距目标≥20分',test:g=>g>=20},{key:'short',name:'距目标10–不足20分',test:g=>g>=10&&g<20},{key:'near',name:'距目标不足10分',test:g=>g>0&&g<10},{key:'reached',name:'达到目标',test:g=>g<=0}]
export const evidenceNames={read:'评分读取失败',pending:'报告生成中',failed:'报告生成失败',unknown:'标准不同或不完整',ready:'已有可比评分',empty:'暂无有效维度评分'}
export const pairNames={read:'读取失败',observing:'观察中',before:'缺前记录',after:'缺后记录',standard:'评分或标准不足',other:'其他评价标准',paired:'有效配对'}
export function completion(a){const xs=[...new Map((a.attempts||[]).filter(valid).map(p=>[String(p.id??p.sessionId),p])).values()].sort((a,b)=>time(a.submittedAt)-time(b.submittedAt));return Number.isInteger(a.task.minAttempts)&&a.task.minAttempts>0?time(xs[a.task.minAttempts-1]?.submittedAt):NaN}
export function buildAnalytics({classes,people,facts,classIds,jobId='',code='',version='',start,end,now=Date.now(),taskId=''}){
 const cs=classes.filter(c=>classIds.includes(String(c.id))),ids=new Set(cs.map(c=>String(c.id))),tasks=facts.assignments.filter(a=>ids.has(String(a.task.classId))&&(!jobId||String(a.task.jobId)===jobId))
 const attempts=facts.attempts.filter(p=>ids.has(String(p.classId))&&(!jobId||String(p.jobId)===jobId)),allReports=facts.reports.filter(r=>ids.has(String(r.classId))&&(!jobId||String(r.jobId)===jobId)&&time(r.submittedAt)<end)
 const relations=people.filter(p=>ids.has(String(p.classId))),students=[...new Map(relations.map(p=>[String(p.id),{...p,id:String(p.id)}])).values()]
 const hasRelation=(studentId,classId)=>relations.some(p=>String(p.id)===String(studentId)&&String(p.classId)===String(classId))
 const records=attempts.filter(p=>valid(p)&&time(p.submittedAt)>=start&&time(p.submittedAt)<end&&hasRelation(p.studentId,p.classId))
 const history=allReports.filter(valid).sort((a,b)=>time(a.submittedAt)-time(b.submittedAt))
 const studentRows=students.map(s=>{
  const own=allReports.filter(r=>String(r.studentId)===s.id&&hasRelation(s.id,r.classId)),ownAttempts=attempts.filter(r=>String(r.studentId)===s.id&&hasRelation(s.id,r.classId)&&(time(r.submittedAt)<end||!r.submittedAt&&end>now))
  const latest=history.filter(r=>String(r.studentId)===s.id&&hasRelation(s.id,r.classId)&&r.detailReport?.moduleScores?.some(m=>m.moduleCode===code)).at(-1),m=latest?.detailReport.moduleScores.find(m=>m.moduleCode===code),key=goodScore(m)?versionKey(latest,m):null
  const read=own.some(r=>r.readFailed),comparable=!read&&key&&(!version||version===key),state=read?'read':ownAttempts.some(p=>p.state==='GENERATING')?'pending':ownAttempts.some(p=>p.state==='FAILED')?'failed':latest&&(!key||version&&version!==key)?'unknown':comparable?'ready':'empty'
  return {...s,classes:cs.filter(c=>hasRelation(s.id,c.id)),count:records.filter(r=>String(r.studentId)===s.id).length,score:comparable?m.rawScore:null,target:comparable?m.targetScore:null,gap:comparable?m.targetScore-m.rawScore:null,key,latest,state}
 })
 const versions=[...new Map(studentRows.filter(s=>s.key).map(s=>[s.key,{key:s.key,target:s.latest.detailReport.moduleScores.find(m=>m.moduleCode===code).targetScore,rule:s.latest.detailReport.trainingContext.provenance.ruleVersion}])).values()]
 const cohort=studentRows.filter(s=>s.score!=null),mean=xs=>xs.length>=5?xs.reduce((n,s)=>n+s.score,0)/xs.length:null
 const buckets=[];let at=time(dateKey(start)+'T00:00:00');const weekday=new Date(at+8*3600000).getUTCDay();at-=((weekday+6)%7)*86400000
 for(;at<end;at+=7*86400000){const lo=Math.max(at,start),hi=Math.min(at+7*86400000,end),rs=records.filter(p=>time(p.submittedAt)>=lo&&time(p.submittedAt)<hi);buckets.push({date:dateKey(lo),to:dateKey(hi-1),start:lo,end:hi,count:rs.length,active:new Set(rs.map(r=>String(r.studentId))).size,partial:hi-lo<7*86400000,records:rs})}
 function execution(as){const result={onTime:[],late:[],unfinished:[],unknown:[],excluded:[],exempt:[]};for(const a of as){const due=time(a.effectiveDeadline||a.task.deadline);if(!Number.isFinite(due)||due<start||due>=end||due>now)continue;if(truth(a.exempt)){result.exempt.push(a);continue}if(a.task.endedAt||truth(a.task.archived)){result.excluded.push(a);continue}const done=completion(a);result[Number.isFinite(done)&&done<end?(done<=due?'onTime':'late'):a.completionStatus==='COMPLETED'&&!Number.isFinite(done)?'unknown':'unfinished'].push(a)}return result}
 const due=execution(tasks),comparison=cs.map(c=>{const members=studentRows.filter(s=>s.classes.some(cl=>String(cl.id)===String(c.id))),rs=records.filter(r=>String(r.classId)===String(c.id)),participated=new Set(rs.map(r=>String(r.studentId)));return {...c,n:members.length,active:participated.size,records:rs,students:members.filter(s=>participated.has(s.id)),due:execution(tasks.filter(a=>String(a.task.classId)===String(c.id)))}})
 // Class capability uses that class's reports, not another class's score for a shared student.
 function matrix(c,mCode){const reference=history.find(r=>{const m=r.detailReport?.moduleScores?.find(m=>m.moduleCode===code);return m&&versionKey(r,m)===version}),referenceModule=reference?.detailReport.moduleScores.find(m=>m.moduleCode===mCode),matrixKey=referenceModule?versionKey(reference,referenceModule):null;const members=relations.filter(p=>String(p.classId)===String(c.id)),latest=new Map();for(const r of history.filter(r=>String(r.classId)===String(c.id)&&members.some(p=>String(p.id)===String(r.studentId)))){const m=r.detailReport?.moduleScores?.find(m=>m.moduleCode===mCode);if(m)latest.set(String(r.studentId),{r,m})}const rows=[...latest.values()].filter(({r,m})=>goodScore(m)&&matrixKey&&versionKey(r,m)===matrixKey&&!allReports.some(p=>p.readFailed&&String(p.classId)===String(c.id)&&String(p.studentId)===String(r.studentId)));return {n:rows.length,gap:rows.length>=5?rows.reduce((n,x)=>n+x.m.rawScore-x.m.targetScore,0)/rows.length:null,rows}}
 const pairedTask=tasks.find(a=>String(a.task.id)===taskId)?.task,candidates=[];let unknownCompletion=0
 if(pairedTask){for(const a of tasks.filter(a=>String(a.task.id)===taskId&&!truth(a.exempt))){const done=completion(a);if(!Number.isFinite(done)){if(a.completionStatus==='COMPLETED')unknownCompletion++;continue}if(done<start||done>=end)continue;const own=allReports.filter(r=>String(r.studentId)===String(a.studentId)&&String(r.jobId)===String(a.task.jobId)),xs=own.filter(valid).sort((a,b)=>time(a.submittedAt)-time(b.submittedAt)),before=xs.filter(r=>time(r.submittedAt)<time(a.task.startTime)).at(-1),after=xs.filter(r=>time(r.submittedAt)>done&&time(r.submittedAt)<=done+14*86400000).at(-1),bm=before?.detailReport?.moduleScores?.find(m=>m.moduleCode===code),am=after?.detailReport?.moduleScores?.find(m=>m.moduleCode===code),key=goodScore(bm)?versionKey(before,bm):null,hasFailure=own.some(r=>r.readFailed&&(time(r.submittedAt)<time(a.task.startTime)||time(r.submittedAt)>done&&time(r.submittedAt)<=done+14*86400000));const state=hasFailure?'read':end-1<done+14*86400000?'observing':!before?'before':!after?'after':!key||!goodScore(am)||versionKey(after,am)!==key?'standard':version&&version!==key?'other':'paired';candidates.push({id:String(a.studentId),name:a.name||String(a.studentId),assignment:a,done,before,after,beforeScore:bm?.rawScore,afterScore:am?.rawScore,delta:state==='paired'?am.rawScore-bm.rawScore:null,key,state})}}
 const pairs=candidates.filter(p=>p.state==='paired')
 return {classes:cs,students:studentRows,records,weekly:buckets,versions,cohort,mean:mean(cohort),modules:moduleOptions(history),comparison,due,matrix,candidates,pairs,unknownCompletion,pairedTask}
}
