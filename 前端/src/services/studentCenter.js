import {teacherDataset} from './teacherData.js'
import {currentAbility,recordInScope,safeTeacherReturn} from './teacherContext.js'
import { classDefinitions, commonAbilities } from './teacherRoster.js'
import { abilities as legacyAbilities, growthFor } from './teacherModel.js'
import { lifecycle, isOverdue, resultEvidence } from './trainingTasks.js'

export const abilityDefinitions = [
  { id: 'logic', name: '逻辑表达', scope: 'common' },
  { id: 'project_expression', name: '项目经历表达', scope: 'common' },
  { id: 'communication', name: '沟通表达', scope: 'common' },
  { id: 'role_understanding', name: '岗位理解', scope: 'common' },
  { id: 'professional', name: '专业知识', scope: 'role' },
  { id: 'practice', name: '岗位实践', scope: 'role' },
]
export { commonAbilities, classDefinitions }
export const formatDate = value => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : '—'
export const decimal = value => Number.isFinite(value) ? value.toFixed(1) : '—'
const finiteScore = n => Number.isFinite(n) && n >= 0 && n <= 100

// Existing fixtures remain isolated from production. No report text is synthesized.
export function studentDataset(demo) { return demo ? teacherDataset() : {students:[],records:[],tasks:[],source:'unavailable',now:Date.now()} }

export function inPeriod(record,period,now){return recordInScope(record,typeof period==='object'?period:{period},now)}

export function studentTasks(data, studentId) {
  return data.tasks.filter(t => lifecycle(t, data.now) !== 'DRAFT').flatMap(task => (task.executions || []).filter(e => e.studentId === studentId).map(execution => ({ task, execution, overdue: isOverdue(task, execution, data.now), evidence: resultEvidence(task, execution) })))
}

export const pendingTask = (row, now) => !row.task.manuallyEndedAt && row.execution.completionStatus !== 'COMPLETED' && (row.overdue || ['ACTIVE', 'SCHEDULED'].includes(lifecycle(row.task, now)))
export function pendingAbilityTasks(data, studentId, name) {
  return studentTasks(data, studentId).filter(r => pendingTask(r,data.now) && r.task.type === 'ABILITY' && r.task.primaryAbility === name)
}
export function growthReason(growth, records, position) {
  if (growth.status !== '数据不足') return ''
  if (!records.length) return '所选范围内无有效评分记录'
  const latest = [...records].sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt)).at(-1)
  if (latest.position !== position) return '岗位已切换，当前岗位暂无可比记录'
  if (!latest.model || !latest.version) return '记录缺少评分模型或版本说明'
  if (!growth.count) return '记录缺少完整可用评分，暂不能判断变化'
  const changed = records.some(r=>r.position!==latest.position||r.model!==latest.model||r.version!==latest.version)
  return `${changed?'岗位或评分口径切换后，':''}仅${growth.count}条可比记录，至少需6条才能判断变化`
}

export function reportComparison(record, previous) {
  if (!record.valid || !finiteScore(record.score)) return {delta:null,reason:'本次评分无效或缺失'}
  if (!previous) return {delta:null,reason:'首次记录'}
  if (!previous.valid || !finiteScore(previous.score)) return {delta:null,reason:'上次评分无效或缺失'}
  if (!record.position || record.position!==previous.position) return {delta:null,reason:'目标岗位不同'}
  if (!record.model || !record.version || !previous.model || !previous.version) return {delta:null,reason:'评分口径未说明'}
  if (record.model!==previous.model || record.version!==previous.version) return {delta:null,reason:'评分口径不同'}
  return {delta:record.score-previous.score,reason:''}
}

export function studentSummary(data, student, query = {}) {
  const records = data.records.filter(r => r.studentId === student.id && inPeriod(r, query, data.now))
  const valid = records.filter(r => r.valid && finiteScore(r.score))
  const latest = [...valid].sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt)).at(-1)
  const growth = latest?.position === student.position ? growthFor(valid) : { status:'数据不足', count:0, records:[], delta:null }, tasks = studentTasks(data, student.id)
  const active = tasks.filter(r => pendingTask(r,data.now))
  const overdue = active.filter(r => r.overdue)
  const last = data.records.filter(r => r.studentId === student.id && r.valid && finiteScore(r.score) && Date.parse(r.completedAt) <= data.now).sort((a, b) => Date.parse(b.completedAt) - Date.parse(a.completedAt))[0]?.completedAt
  const concern = growth.status === '持续下降' ? '最近3次可比报告评分连续下降' : !valid.length ? '当前周期无有效训练' : valid.length === 1 ? '当前周期仅1次有效训练' : ''
  return { ...student, count: valid.length, growth, growthReason:growthReason(growth,valid,student.position), activeCount: active.length, overdueCount: overdue.length, last, concern, attentionRank: growth.status === '持续下降' ? 0 : overdue.length ? 1 : !valid.length ? 2 : valid.length === 1 ? 3 : 4 }
}

export function abilitySnapshot(data, student, ability, period = '30d', position = student.position) {
  const stage=currentAbility(data.records,student,ability.name,ability.scope,typeof period==='object'?period:{period},data.now,position)
  const {records,calculationRecords,current,latest}=stage
  const source=data.records.filter(r=>r.studentId===student.id&&inPeriod(r,period,data.now))
  const currentRoleChanged=source.length&&source.sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt)).at(-1)?.position!==position
  // Targets are explicitly illustrative configuration, not inferred production goals.
  const target = data.source === 'demo' && ability.scope === 'common' ? 80 : null
  const growthRecords = records.map(r => ({ ...r, score: r.abilityScore, abilityScores: legacyAbilities.map(() => r.abilityScore) }))
  const growth = growthFor(growthRecords)
  const reason = currentRoleChanged ? '岗位已切换，当前岗位暂无可比记录' : source.length&&!records.length ? '该能力评分无效、缺失或评分口径未说明' : growthReason(growth,growthRecords,position)
  return { ability, records, calculationRecords, current, target, gap: current === null || target === null ? null : current - target, growth, growthReason:reason, version: latest?.version ? `${ability.scope==='common' ? latest.commonVersion || latest.model : latest.model} / ${latest.version}` : null, position, updatedAt: records.at(-1)?.completedAt, excluded: source.length - records.length, sampleLabel: calculationRecords.length === 1 ? '最近一次评分' : calculationRecords.length ? `最近${calculationRecords.length}次有效评分平均` : '暂无有效评分' }
}

export function sourceReturn(query) {
  // Allow only known local destinations; never trust arbitrary redirect URLs.
  if(safeTeacherReturn(query.returnTo))return {label:query.returnTo.startsWith('/teacher/analytics')?'返回数据分析':query.returnTo.startsWith('/teacher/classes')?'返回班级分析':query.returnTo.startsWith('/teacher/tasks')?'返回来源任务':query.returnTo.startsWith('/teacher/students')?'返回学生中心':query.returnTo.startsWith('/teacher/training-records')?'返回训练明细':'返回来源页面',path:query.returnTo}
  if (query.sourceTaskId) return { label: '返回来源任务', path: `/teacher/tasks/${encodeURIComponent(query.sourceTaskId)}`, query: { tab: 'students' } }
  if (query.returnClass) return { label: '返回班级分析', path: `/teacher/classes/${encodeURIComponent(query.returnClass)}`, query: { tab: query.sourceTab || 'students' } }
  return { label: '返回学生中心', path: '/teacher/students' }
}
