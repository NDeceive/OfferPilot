import {teacherDataset} from './teacherData.js'
import { abilities as legacyAbilities, growthFor, referenceDate } from './teacherModel.js'
import { getClassDemo as roster, classDefinitions, commonAbilities, pct } from './teacherRoster.js'
import { loadTasks, lifecycle, isOverdue } from './trainingTasks.js'
import { currentAbility, recordInScope, teacherNow } from './teacherContext.js'
export { classDefinitions, commonAbilities, pct, semesters, buildClassDemo, removeMembers, joinMember, addMembers } from './teacherRoster.js'
export function getClassDemo(){
 const base=roster(), unified=teacherDataset(), data={...base,records:unified.records.map(r=>({...r,source:r.source==='SELF'?'self':'teacher'}))}, tasks=unified.tasks.filter(t=>t.publishedAt)
 const executions=tasks.flatMap(t=>(t.executions||[]).map(e=>({...e,taskId:t.id,classId:t.classId,state:{COMPLETED:'completed',IN_PROGRESS:'ongoing',NOT_STARTED:'notStarted'}[e.completionStatus]})))
 return {...data,referenceDate:'2026-10-03',now:teacherNow,tasks:tasks.map(t=>({...t,name:t.title,position:t.targetRole,createdAt:t.publishedAt})),executions}
}
export const inRange=(date,period='30d')=>recordInScope({completedAt:date},{period})
const average=values=>values.length?values.reduce((a,b)=>a+b,0)/values.length:null
export function classStudents(data, classId, query = {}) {
  if (query.semester && query.semester !== '2026-1') return []
  const memberIds = new Set(data.members.filter(m => m.classId === classId && m.state === 'joined').map(m => m.studentId))
  return data.students.filter(s => memberIds.has(s.id) && (!query.position || s.position === query.position)).map(s => {
    const own = data.records.filter(r => r.studentId === s.id && r.valid && r.classId === classId).sort((a, b) => Date.parse(b.completedAt) - Date.parse(a.completedAt))
    const current = own.filter(r => recordInScope(r,query,teacherNow) && (!query.position || r.position === query.position) && (!query.trainingType || r.type === query.trainingType))
    const abilityQuery={...query,period:query.abilityPeriod||query.period||'30d'}
    const snapshots=commonAbilities.map(name=>currentAbility(own,s,name,'common',abilityQuery,teacherNow))
    const common=snapshots.every(v=>v.current!==null)?Object.fromEntries(commonAbilities.map((a,i)=>[a,snapshots[i].current])):null
    const specialtyValues=['专业知识','岗位实践'].map(name=>currentAbility(own,s,name,'role',abilityQuery,teacherNow,query.position))
    const specialty=query.position&&specialtyValues.every(v=>v.current!==null)?Object.fromEntries(['专业知识','岗位实践'].map((a,i)=>[a,specialtyValues[i].current])):null
    const abilityVersion=snapshots[0].version,specialtyVersion=specialtyValues[0].version
    const attainment = common ? average(Object.values(common).map(v => v / 80 * 100)) : null
    const execution = data.executions.filter(e => e.studentId === s.id && e.classId === classId)
    const overdue = execution.filter(e => e.state !== 'completed' && !data.tasks.find(t=>t.id===e.taskId)?.manuallyEndedAt&&Date.parse(data.tasks.find(t => t.id === e.taskId)?.deadline)<=teacherNow).length
    const growth = growthFor(own.filter(r => recordInScope(r,{...query,period:query.abilityPeriod||query.period||'30d'},teacherNow) && r.position === s.position))
    const count = current.length, low = count < 2, reached = attainment !== null && attainment >= 100
    const quadrant = attainment === null ? null : `${low ? 'low' : 'trained'}-${reached ? 'reached' : 'below'}`
    const reasons = [growth.status === '持续下降' && '持续下降', overdue > 0 && '当前任务逾期', count === 0 && '当前周期未训练', count === 1 && '当前周期仅1次训练', attainment !== null && attainment <= 90 && '通用能力距目标至少10个百分点', !common && '暂无完整能力数据'].filter(Boolean)
    return { ...s, common, specialty, abilityVersion, specialtyVersion, attainment, count, last:own.find(r=>recordInScope(r,{period:'all'},teacherNow))?.completedAt, growth, overdue, reasons, concern: reasons[0] || '无明显问题', quadrant, execution, current }
  })
}
export function taskRows(data, classId, query = {}) {
  const students = classStudents(data, classId, query), ids = new Set(students.map(s => s.id))
  return data.tasks.filter(t => t.classId === classId && recordInScope({completedAt:t.createdAt},query,teacherNow) && (!query.position || !t.position || t.position === query.position)).map(t => {
    const execution = data.executions.filter(e => e.taskId === t.id && ids.has(e.studentId))
    const completed = execution.filter(e => e.state === 'completed').length, ongoing = execution.filter(e => e.state === 'ongoing').length, notStarted = execution.filter(e => e.state === 'notStarted').length
    const overdue = !t.manuallyEndedAt&&Date.parse(t.deadline)<=teacherNow ? ongoing + notStarted : 0
    return { ...t, total: execution.length, completed, ongoing, notStarted, overdue, rate: pct(completed, execution.length), mutual: { completed, overdue, ongoing: overdue ? 0 : ongoing, notStarted: overdue ? 0 : notStarted } }
  })
}
export function classSummary(data, classId, query = {}) {
  const students = classStudents(data, classId, query), rows = taskRows(data, classId, query)
  const trained = students.filter(s => s.count > 0).length, records = students.flatMap(s => s.current)
  const total = rows.reduce((sum, t) => sum + t.total, 0), done = rows.reduce((sum, t) => sum + t.completed, 0)
  const groups=new Map();for(const s of students.filter(s=>s.common)){const g=groups.get(s.abilityVersion)||[];g.push(s);groups.set(s.abilityVersion,g)}const sample=[...groups.values()].sort((a,b)=>b.length-a.length)[0]||[]
  const ability = commonAbilities.map(name => ({ name, value: average(sample.map(s=>s.common[name])), target: 80 })).sort((a, b) => (a.value ?? 100) - (b.value ?? 100))
  return { students, records, rows, trained, participation: pct(trained, students.length), total, done, completion: total ? pct(done, total) : null, ability,
    insufficient: students.filter(s => s.count < 2).length, untrained: students.filter(s => s.count === 0).length, declining: students.filter(s => s.growth.status === '持续下降').length,
    samples: sample.length, completeSamples: students.filter(s=>s.common).length, abilityVersion: sample[0]?.abilityVersion||null }
}
export function validateImport(rows, data, classId) {
  const seen = new Set(), members = new Set(data.members.filter(m => m.classId === classId).map(m => m.studentId))
  return rows.map((row, i) => {
    const number = String(row[1] || '').trim(), name = String(row[0] || '').trim(), student = data.students.find(s => s.number === number)
    let error = !name || !number ? '姓名或学号为空' : !student ? '未找到已有学生账号' : student.name !== name ? '姓名与学号不匹配' : seen.has(number) ? '文件内学号重复' : members.has(student.id) ? '已在本班级名单' : ''
    seen.add(number)
    return { row: i + 2, name, number, studentId: student?.id, error }
  })
}
