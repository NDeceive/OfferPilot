import {stamp, versionKey} from './teachingAnalysis.js'
const flag=v=>v===true||v===1
export const supportGroups=[
 {key:'short',name:'共同能力短板',note:'最近可比评分距实际目标至少10分'},
 {key:'blocked',name:'执行受阻',note:'活跃分配存在报告失败、无效提交或次数耗尽；生成中不计为失败'},
 {key:'start',name:'尚未开始',note:'已开放且未截止、未完成、未减免的任务，没有尝试记录'},
 {key:'overdue',name:'逾期需核查',note:'未完成分配超过个人优先截止；结束、归档与减免任务不计'},
 {key:'invest',name:'已有投入仍有短板',note:'本周期同岗位至少3次有效训练，所选维度仍低于目标'},
 {key:'near',name:'接近或达到目标',note:'所选维度距目标不足10分或已达目标，不代表所有能力达标'},
 {key:'missing',name:'证据待补齐',note:'区分未评分、生成中、读取失败及未知评分口径'},
]
export function supportRows(people,tasks,reports,{classId='',classIds=[],from='',to='',jobId='',code='',version='',days=30,now=Date.now()}={}){
 const cutoff=to?Math.min(now+1,Date.parse(to+'T00:00:00+08:00')+86400000):now+1,start=from?Date.parse(from+'T00:00:00+08:00'):now-days*86400000
 const persons=new Map()
 for(const p of people){if(classId&&String(p.classId)!==classId||classIds.length&&!classIds.includes(String(p.classId)))continue;const id=String(p.id);const s=persons.get(id)||{...p,id,classes:[]};if(!s.classes.some(c=>String(c.id)===String(p.classId)))s.classes.push({id:p.classId,name:p.className});persons.set(id,s)}
 return [...persons.values()].map(s=>{
  const allocations=tasks.filter(t=>t.publishedAt&&(!classIds.length||classIds.includes(String(t.classId)))&&(!classId||String(t.classId)===classId)&&(!jobId||String(t.jobId)===jobId)&&s.classes.some(c=>String(c.id)===String(t.classId))).flatMap(t=>(t.assignments||[]).filter(a=>String(a.studentId)===s.id&&a.memberState!=='REMOVED').map(a=>({...a,task:t})))
  const own=reports.filter(r=>String(r.studentId)===s.id&&stamp(r.submittedAt)<cutoff&&allocations.some(a=>String(a.task.id)===String(r.taskId))).sort((a,b)=>stamp(a.submittedAt)-stamp(b.submittedAt))
  const attempts=[...new Map(allocations.flatMap(a=>(a.attempts||[]).map((p,i)=>[String(p.sessionId||p.id||a.id+'-'+i),p]))).values()]
  const valid=own.filter(r=>r.state==='READY'&&flag(r.valid))
  const count=attempts.filter(r=>r.state==='READY'&&flag(r.valid)&&stamp(r.submittedAt)>=start&&stamp(r.submittedAt)<cutoff).length
  const latest=valid.filter(r=>r.detailReport?.moduleScores?.some(m=>m.moduleCode===code&&Number.isFinite(m.rawScore))).at(-1)
  const module=latest?.detailReport.moduleScores.find(m=>m.moduleCode===code),key=module?versionKey(latest,module):null
  const failed=own.some(r=>r.readFailed),pending=attempts.some(r=>r.state==='GENERATING')
  const comparable=!failed&&key&&(!version||key===version)
  const score=comparable?module.rawScore:null,target=comparable?module.targetScore:null
  const active=allocations.filter(a=>!a.exempt&&!a.task.archived&&!a.task.endedAt&&!['CLOSED','ENDED'].includes(a.task.lifecycle)&&stamp(a.task.startTime)<=now&&a.completionStatus!=='COMPLETED')
  const reasons=[]
  for(const a of active){if((a.attempts||[]).some(p=>p.state==='FAILED'))reasons.push({a,text:'报告生成失败'});if((a.attempts||[]).some(p=>p.state==='READY'&&(p.valid===false||p.valid===0)))reasons.push({a,text:'提交无效，请核查回答'});if(!['ONGOING','GENERATING'].some(st=>(a.attempts||[]).some(p=>p.state===st))&&Number.isFinite(a.effectiveMaxAttempts)&&(a.attempts||[]).length>=a.effectiveMaxAttempts)reasons.push({a,text:'训练次数已耗尽'})}
  const gap=target!=null?target-score:null,groups=[]
  if(gap!=null&&gap>=10)groups.push('short')
  if(reasons.length)groups.push('blocked')
  if(active.some(a=>!(a.attempts||[]).length&&stamp(a.effectiveDeadline||a.task.deadline)>now))groups.push('start')
  if(active.some(a=>stamp(a.effectiveDeadline||a.task.deadline)<now))groups.push('overdue')
  if(count>=3&&gap>0)groups.push('invest')
  if(gap!=null&&gap<10)groups.push('near')
  if(score==null||pending)groups.push('missing')
  const evidence=failed?'评分读取失败':score!=null?'已有可比评分':pending?'报告正在生成':latest?'未知或未选中评分口径':'暂无有效维度评分'
  return {...s,count,score,target,gap,key,latest,groups,allocations,reasons,evidence,history:valid.filter(r=>{const m=r.detailReport?.moduleScores?.find(m=>m.moduleCode===code);return m&&key&&versionKey(r,m)===key})}
 })
}
