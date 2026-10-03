import {stamp} from './teachingAnalysis.js'

export function inTeachingScope(item, query = {}, classes = []) {
  return (!query.classId || String(item.classId) === String(query.classId)) &&
    (!query.classIds || String(query.classIds).split(',').includes(String(item.classId))) &&
    (!query.semester || classes.some(c => String(c.id) === String(item.classId) && c.semester === query.semester)) &&
    (!query.jobId || String(item.jobId) === String(query.jobId)) &&
    (!query.taskId || String(item.id) === String(query.taskId))
}
export function isOverdue(assignment, task, now = Date.now()) {
  return !!task.publishedAt && !task.endedAt && !task.archived && !assignment.exempt &&
    assignment.completionStatus !== 'COMPLETED' && stamp(assignment.effectiveDeadline || task.deadline) < now
}
export function executionStats(task, now = Date.now()) {
  const all = task.assignments || [], counted = all.filter(a => !a.exempt)
  return {total: counted.length, completed: counted.filter(a => a.completionStatus === 'COMPLETED').length,
    overdue: counted.filter(a => isOverdue(a, task, now)).length, exempt: all.length - counted.length,
    failed: counted.filter(a => (a.attempts || []).some(p => p.state === 'FAILED')).length}
}
export function assignmentMatches(a, task, filter) {
  if (filter === 'overdue' || filter === 'completion') return isOverdue(a, task)
  if (filter === 'unfinished') return !a.exempt && a.completionStatus !== 'COMPLETED'
  if (filter === 'failed') return !a.exempt && (a.attempts || []).some(p => p.state === 'FAILED')
  return !filter || a.completionStatus === filter
}
export const executionStates = [
  {key:'COMPLETED',label:'已完成',color:'#047857'},
  {key:'IN_PROGRESS',label:'待完成要求',color:'#557c92'},
  {key:'PENDING_VALIDATION',label:'报告校验中',color:'#a66b26'},
  {key:'NOT_STARTED',label:'未开始',color:'#d4d4d8'},
  {key:'EXEMPT',label:'已减免',color:'#71717a'}
]
export function reportEvidence(data) {
  return (data?.messages || []).some(m => m.role === 'CANDIDATE' && m.msgType === 'ANSWER' && String(m.content || '').trim())
}
export function validateTaskDraft(task, questions, recipients) {
  const eligible = recipients.filter(m => m.state === 'JOINED' && m.accountStatus === 1)
  if (!task.classId || !String(task.title || '').trim()) return {step:0,message:'请选择班级并填写任务名称。'}
  if (!eligible.length) return {step:0,message:'请先加入至少一名正常学生。'}
  if (task.recipientMode === 'SELECTED' && (!task.studentIds.length || task.studentIds.some(id => !eligible.some(m => String(m.studentId) === String(id))))) return {step:0,message:'指定学生模式至少选择一名当前正常成员；零人不会自动改为全班。'}
  if (!Number.isFinite(stamp(task.startTime)) || !Number.isFinite(stamp(task.deadline)) || stamp(task.deadline) <= stamp(task.startTime)) return {step:0,message:'截止时间必须晚于开始时间。'}
  if (!task.jobId || !questions.length || questions.length > 20 || questions.some(q => q.length > 2000)) return {step:1,message:'请选择岗位，并填写1～20道问题，每道不超过2000字。'}
  const modules=task.scoreModules||[]
  if (modules.length !== 5 || new Set(modules.map(m => m.code)).size !== 5 || modules.some(m => !m.code || ![1,2,3].includes(m.level)) || !(modules.every(m=>m.rank===1) || [...modules].sort((a,b)=>a.rank-b.rank).every((m,i)=>m.rank===i+1))) return {step:1,message:'请选择五个不同维度及有效目标；权重采用均衡或完整优先级。'}
  if (!Number.isInteger(task.minAttempts) || task.minAttempts < 1 || task.minAttempts > 10 || !Number.isInteger(task.maxAttempts) || task.maxAttempts < task.minAttempts || task.maxAttempts > 20 || ![600,900,1200,1800].includes(task.durationSeconds) || ![1,2,3].includes(task.difficulty)) return {step:2,message:'请核对次数、时长与难度，最大次数不得小于最低有效次数。'}
  return null
}
