import baseline from './fixtures/training_task_mock_baseline.json'
import templateBaseline from './fixtures/task_templates_mock.json'
import canonicalResults from './fixtures/canonical_task_results.json'
import { getClassDemo, commonAbilities } from './teacherRoster.js'

export const taskTypes = { POSITION: '岗位专项', ABILITY: '能力专项', COMPREHENSIVE: '综合训练' }
export const modes = { AI_INTERVIEW: 'AI 虚拟面试', SPECIAL_PRACTICE: '专项训练' }
export const lifecycleNames = { DRAFT: '草稿', SCHEDULED: '未开始', ACTIVE: '进行中', ENDED: '已结束' }
export const statusNames = { all: '全部学生', COMPLETED: '已完成', IN_PROGRESS: '进行中', NOT_STARTED: '未开始', overdue: '已逾期', late: '逾期完成' }
const abilityMap = { LOGIC_EXPRESSION: '逻辑表达', PROJECT_EXPRESSION: '项目经历表达', COMMUNICATION: '沟通表达', ROLE_UNDERSTANDING: '岗位理解' }
export { commonAbilities }
export const demoNow = () => Date.parse('2026-10-03T15:00:00+08:00')
const clone = value => JSON.parse(JSON.stringify(value))
const owner = () => localStorage.getItem('userId') || localStorage.getItem('username') || 'local-teacher'
const storeKey = () => `offerpilot:teacher:training:v1:${owner()}`
export function readStore() {
  const text = localStorage.getItem(storeKey())
  if (!text) return { tasks: {}, templates: {}, deletedTemplates: [] }
  const data = JSON.parse(text)
  if (!data.tasks || !data.templates || !Array.isArray(data.deletedTemplates)) throw new Error('本地演示数据格式异常，请检查浏览器存储。')
  return data
}
export function membersFor(classId) {
  const data = getClassDemo()
  return data.members.filter(m => m.classId === classId).map(m => ({ ...data.students.find(s => s.id === m.studentId), eligible: m.state === 'joined' && m.account === 'normal', reason: m.state !== 'joined' ? '尚未加入班级' : m.account !== 'normal' ? '账号未激活或已停用' : '' }))
}
export function participantsFor(draft) {
  return membersFor(draft.classId).filter(s => draft.scope === 'students' ? draft.studentIds.includes(s.id) : s.eligible && (draft.scope !== 'role' || s.position === draft.participantRole))
}
export function lifecycle(task, now = demoNow()) {
  if (!task.publishedAt) return 'DRAFT'
  if (task.manuallyEndedAt || now >= Date.parse(task.deadline)) return 'ENDED'
  return now < Date.parse(task.startTime) ? 'SCHEDULED' : 'ACTIVE'
}
export function isOverdue(task, execution, now = demoNow()) {
  return !task.manuallyEndedAt && execution.completionStatus !== 'COMPLETED' && now >= Date.parse(task.deadline)
}
export function matchesStatus(task, row, status, now = demoNow()) {
  return status === 'all' || (status === 'overdue' ? isOverdue(task, row, now) : status === 'late' ? row.completionStatus === 'COMPLETED' && row.lateCompleted : row.completionStatus === status)
}
export function executionFilter(status = 'all', timing = '') {
  // Old status links remain valid; overdue and late are now independent time filters.
  return {
    status: ['all', 'COMPLETED', 'IN_PROGRESS', 'NOT_STARTED'].includes(status) ? status : 'all',
    timing: ['overdue', 'late'].includes(timing) ? timing : ['overdue', 'late'].includes(status) ? status : '',
  }
}
export function matchesExecution(task, row, status = 'all', timing = '', now = demoNow()) {
  const filters = executionFilter(status, timing)
  return matchesStatus(task, row, filters.status, now) && (!filters.timing || matchesStatus(task, row, filters.timing, now))
}
export function resultEvidence(task, row) {
  const results = (row.results || []).filter(r => r.taskId === task.id && r.valid !== false && Number.isFinite(r.score) && r.score >= 0 && r.score <= 100 && Number.isFinite(Date.parse(r.completedAt))).sort((a, b) => Date.parse(a.completedAt) - Date.parse(b.completedAt))
  const first = results[0], latest = results.at(-1)
  const dimension = task.type === 'ABILITY' ? task.primaryAbility : task.type === 'POSITION' ? task.targetRole : null
  const signature = r => r?.dimension && r.scoringVersion && r.scale === 100 && r.dimension === dimension ? `${r.dimension}:${r.scoringVersion}:${r.scale}` : null
  const comparable = results.length >= 2 && !!signature(first) && signature(first) === signature(latest)
  return { results, first, latest, signature: signature(latest), comparable, state: !results.length ? 'none' : results.length === 1 ? 'single' : comparable ? 'comparable' : 'incomparable', delta: comparable ? latest.score - first.score : null }
}
export function attentionReason(task, row) {
  if (row.completionStatus !== 'COMPLETED') return isOverdue(task, row) ? '逾期未完成' : row.completionStatus === 'NOT_STARTED' ? '尚未开始训练' : '尚未达到完成要求'
  return resultEvidence(task, row).state === 'none' ? '暂无有效结果，请检查报告' : ''
}
export function counts(task, now = demoNow()) {
  const rows = task.executions || []
  const completed = rows.filter(e => e.completionStatus === 'COMPLETED').length
  return { total: rows.length || task.participantCount || 0, completed, inProgress: rows.filter(e => e.completionStatus === 'IN_PROGRESS').length, notStarted: rows.filter(e => e.completionStatus === 'NOT_STARTED').length, overdue: rows.filter(e => isOverdue(task, e, now)).length, late: rows.filter(e => e.completionStatus === 'COMPLETED' && e.lateCompleted).length, rate: rows.length ? Math.round(completed / rows.length * 100) : null }
}
export function attentionRank(task, now = demoNow()) {
  const c = counts(task, now), state = lifecycle(task, now)
  if (c.overdue) return 0
  if (state === 'ACTIVE' && Date.parse(task.deadline) - now <= 48 * 3600000 && c.completed < c.total) return 1
  return state === 'ACTIVE' ? c.rate < 60 ? 2 : 3 : state === 'SCHEDULED' ? 4 : state === 'ENDED' ? 5 : 6
}
export function recommendedQuestions(draft) {
  const goal = draft.type === 'ABILITY' ? draft.primaryAbility : draft.targetRole || '综合能力'
  const questions = [
    `请结合具体经历介绍你的${goal}能力。`,
    '请描述一个有挑战的项目，以及你负责的部分。',
    '你如何识别问题的关键因素，并确定处理优先级？',
    '面对分歧时，你如何组织信息并推进沟通？',
    '请解释一次方案选择的依据，以及你考虑过的替代方案。',
    '项目执行出现意外时，你如何调整计划并协调资源？',
    '你用什么证据验证结果？请说明数据或反馈的来源。',
    '回顾这次经历，你认为哪些地方还能改进？',
  ]
  return Array.from({ length: Number(draft.mainQuestionCount) || 6 }, (_, i) => ({ id: `recommended-${i}`, text: questions[i % questions.length], focus: goal, followUp: draft.dynamicFollowUp }))
}
export function newDraft(context = {}) {
  const classId = context.classId || baseline.classes.find(c => c.name === context.class)?.id || ''
  return { id: crypto.randomUUID(), title: '', description: '', classId, scope: context.students ? 'students' : 'all', participantRole: '', studentIds: String(context.students || '').split(',').filter(Boolean), targetRole: context.students && !context.ability ? '' : context.position || '', type: context.ability ? 'ABILITY' : 'POSITION', primaryAbility: commonAbilities.includes(context.ability) ? context.ability : '', trainingMode: 'AI_INTERVIEW', durationMinutes: 15, difficulty: 'STANDARD', mainQuestionCount: 8, dynamicFollowUp: true, questions: [], startTime: '2026-10-03T15:00', immediate: true, deadline: '', minValidAttempts: 1, maxAttempts: 3, allowRepeat: true, allowLateCompletion: false, lastEditedStep: 1, version: 0, sourceContext: { type: context.returnStudent ? context.ability ? 'STUDENT_ABILITY' : 'STUDENT_OVERVIEW' : context.ability ? 'CLASS_ABILITY_ANALYSIS' : context.students ? 'CLASS_STUDENT_ANALYSIS' : classId ? 'CLASS_DETAIL' : 'TASK_CENTER', classId, returnClass: context.returnClass, returnStudent: context.returnStudent }, createdBy: owner() }
}
export function validateDraft(draft, now = demoNow()) {
  const errors = [], students = participantsFor(draft)
  if (!draft.classId || !students.length) errors.push({ step: 1, message: '请选择班级及至少一名有效学生。' })
  if (students.some(s => !s.eligible) || (draft.scope === 'students' && draft.studentIds.some(id => !students.some(s => s.id === id)))) errors.push({ step: 1, message: '所选学生已失效或无法训练，请重新选择。' })
  if (!taskTypes[draft.type] || !draft.title.trim() || (draft.type === 'ABILITY' ? !commonAbilities.includes(draft.primaryAbility) : draft.type === 'POSITION' && !draft.targetRole)) errors.push({ step: 2, message: '请填写任务名称和主要训练目标。' })
  if (!modes[draft.trainingMode] || !['BASIC', 'STANDARD', 'CHALLENGE'].includes(draft.difficulty) || ![10,15,20,30].includes(draft.durationMinutes) || !Number.isInteger(draft.mainQuestionCount) || draft.mainQuestionCount < 1 || draft.mainQuestionCount > 30 || draft.questions.length !== draft.mainQuestionCount || draft.questions.some(q => !q.text.trim())) errors.push({ step: 3, message: '请配置完整、有效的训练题目，题量须为1～30题。' })
  const start = draft.immediate ? now : Date.parse(draft.startTime + (draft.startTime.endsWith('+08:00') ? '' : ':00+08:00'))
  const deadline = Date.parse(draft.deadline + (draft.deadline.endsWith('+08:00') ? '' : ':00+08:00'))
  if (!Number.isFinite(start) || !Number.isFinite(deadline) || deadline - start < 1800000 || (!draft.immediate && start < now)) errors.push({ step: 4, message: '截止时间须晚于开始时间至少30分钟，定时开始不能早于当前时间。' })
  if (!Number.isInteger(draft.minValidAttempts) || !Number.isInteger(draft.maxAttempts) || draft.minValidAttempts < 1 || draft.minValidAttempts > 10 || draft.maxAttempts > 20 || draft.maxAttempts < draft.minValidAttempts || (!draft.allowRepeat && draft.maxAttempts !== 1)) errors.push({ step: 4, message: '训练次数有冲突，请检查最低完成次数与最多训练次数。' })
  return errors
}
function seedTasks() {
  const data = getClassDemo()
  const examples = [...baseline.taskCenterExamples, { ...baseline.taskDetailCanonical, ...baseline.taskDetailCanonical.execution, completed: 5, inProgress: 1, startTime: '2026-10-01T08:00:00+08:00', deadline: '2026-10-05T23:59:00+08:00' }]
  const seeded = examples.map(t => {
    const draft = { ...newDraft(), ...t, primaryAbility: abilityMap[t.primaryAbility] || '项目经历表达', targetRole: t.type === 'POSITION' ? t.title.includes('Java') ? 'Java后端开发' : t.title.includes('算法') ? '算法工程师' : '测试开发' : '', scope: 'students', startTime: t.startTime || '2026-10-01T08:00:00+08:00', deadline: t.deadline || (t.lifecycle === 'ENDED' ? '2026-10-01T23:59:00+08:00' : '2026-10-07T23:59:00+08:00'), publishedAt: t.lifecycle === 'DRAFT' ? null : '2026-10-01T08:00:00+08:00', updatedAt: '2026-10-03T10:00:00+08:00', version: 1 }
    draft.questions = recommendedQuestions(draft)
    const selected = membersFor(t.classId).filter(s => s.eligible).slice(0, t.participantCount)
    draft.studentIds = selected.map(s => s.id)
    draft.participantSnapshot = clone(selected)
    draft.executions = draft.publishedAt ? selected.map((s, i) => {
      const completed = i < (t.completed || 0)
      const pair = t.id === canonicalResults.taskId && completed ? canonicalResults.pairs[i] : null
      return {
        studentId: s.id,
        completionStatus: completed ? 'COMPLETED' : i < (t.completed || 0) + (t.inProgress || 0) ? 'IN_PROGRESS' : 'NOT_STARTED',
        validAttemptCount: completed ? pair ? 2 : 1 : 0,
        completedAt: completed ? '2026-10-02T14:20:00+08:00' : null,
        lastAttemptAt: i < (t.completed || 0) + (t.inProgress || 0) ? '2026-10-02T14:20:00+08:00' : null,
        lateCompleted: false,
        results: pair ? [
          { id: `${t.id}-${s.id}-first`, taskId: t.id, dimension: draft.primaryAbility, scoringVersion: 'demo-v1', scale: 100, score: pair.first, completedAt: '2026-10-01T14:20:00+08:00', durationMinutes: pair.durationMinutes },
          { id: `${t.id}-${s.id}-latest`, taskId: t.id, dimension: draft.primaryAbility, scoringVersion: 'demo-v1', scale: 100, score: pair.latest, completedAt: '2026-10-02T14:20:00+08:00', durationMinutes: pair.durationMinutes },
        ] : [],
      }
    }) : []
    if (draft.publishedAt) {
      draft.contentSnapshot = clone(draft.questions)
      draft.ruleSnapshot = ruleSnapshot(draft)
    }
    return draft
  })
  // Existing class-insight links retain their task IDs and actual demonstration executions.
  for (const t of data.tasks) {
    const selected = data.executions.filter(e => e.taskId === t.id)
    const draft = { ...newDraft(), ...t, title: t.name, type: t.type === '通用能力' ? 'ABILITY' : t.type === '综合训练' ? 'COMPREHENSIVE' : 'POSITION', primaryAbility: '项目经历表达', targetRole: t.position, startTime: t.createdAt, deadline: t.deadline + 'T23:59:00+08:00', publishedAt: t.createdAt, updatedAt: t.createdAt, participantSnapshot: selected.map(e => clone(data.students.find(s => s.id === e.studentId))), executions: selected.map(e => ({ studentId: e.studentId, completionStatus: { completed: 'COMPLETED', ongoing: 'IN_PROGRESS', notStarted: 'NOT_STARTED' }[e.state], validAttemptCount: e.state === 'completed' ? 1 : 0, lateCompleted: false, results: [] })), version: 1 }
    draft.questions = recommendedQuestions(draft); draft.contentSnapshot = clone(draft.questions); draft.ruleSnapshot = ruleSnapshot(draft)
    draft.legacyContext = true
    seeded.push(draft)
  }
  return seeded
}
function ruleSnapshot(draft) { return Object.fromEntries(['startTime', 'deadline', 'allowLateCompletion', 'allowRepeat', 'maxAttempts', 'minValidAttempts'].map(k => [k, draft[k]])) }
export function loadTasks() { return Object.values({ ...Object.fromEntries(seedTasks().map(t => [t.id, t])), ...readStore().tasks }) }
export function saveDraft(draft) {
  const store = readStore(), previous = store.tasks[draft.id]
  if (previous && previous.version !== draft.version) throw new Error('草稿已在另一页面更新，请刷新后再编辑。')
  const saved = { ...clone(draft), version: (draft.version || 0) + 1, updatedAt: new Date().toISOString(), createdBy: owner() }
  store.tasks[saved.id] = saved
  localStorage.setItem(storeKey(), JSON.stringify(store))
  return saved
}
export function publishDraft(draft, now = demoNow()) {
  const errors = validateDraft(draft, now)
  if (errors.length) throw new Error(errors[0].message)
  const participants = participantsFor(draft)
  return saveDraft({ ...draft, startTime: draft.immediate ? new Date(now).toISOString() : draft.startTime + ':00+08:00', deadline: draft.deadline + ':00+08:00', publishedAt: new Date(now).toISOString(), participantSnapshot: clone(participants), contentSnapshot: clone(draft.questions), ruleSnapshot: ruleSnapshot({ ...draft, startTime: draft.immediate ? new Date(now).toISOString() : draft.startTime + ':00+08:00', deadline: draft.deadline + ':00+08:00' }), executions: participants.map(s => ({ studentId: s.id, completionStatus: 'NOT_STARTED', validAttemptCount: 0, lateCompleted: false, results: [] })), events: [{ at: new Date(now).toISOString(), text: '任务已发布' }] })
}
export function extendDeadline(task, value, reason, now = demoNow()) {
  const deadline = value + ':00+08:00'
  if (!Number.isFinite(Date.parse(deadline)) || Date.parse(deadline) <= Math.max(now, Date.parse(task.deadline))) throw new Error('新截止时间须晚于当前截止时间及当前时间。')
  if (task.manuallyEndedAt) throw new Error('手动结束的任务不能延期。')
  return saveDraft({ ...task, deadline, deadlineRevisions: [...(task.deadlineRevisions || []), { oldDeadline: task.deadline, newDeadline: deadline, changedAt: new Date(now).toISOString(), changedBy: owner(), reason }], events: [...(task.events || []), { at: new Date(now).toISOString(), text: '教师延长了截止时间' }] })
}
export function loadTemplates() {
  const store = readStore()
  return [...templateBaseline.systemTemplates.map(t => ({ ...newDraft(), ...t, type: t.taskType, primaryAbility: abilityMap[t.primaryAbility] || '', ownerType: 'SYSTEM', questions: recommendedQuestions({ ...t, type: t.taskType, primaryAbility: abilityMap[t.primaryAbility] || '' }) })), ...Object.values(store.templates)].filter(t => !store.deletedTemplates.includes(t.id))
}
export function saveTemplate(draft, name, description, id) {
  if (!name.trim()) throw new Error('请填写模板名称。')
  const store = readStore()
  if (id && store.templates[id]?.ownerId !== owner()) throw new Error('只能修改本人创建的模板。')
  const template = { id: id || crypto.randomUUID(), name: name.trim(), description, ownerType: 'TEACHER', ownerId: owner(), ...Object.fromEntries(['type', 'primaryAbility', 'targetRole', 'trainingMode', 'durationMinutes', 'difficulty', 'mainQuestionCount', 'dynamicFollowUp', 'questions', 'minValidAttempts', 'maxAttempts', 'allowRepeat', 'allowLateCompletion'].map(k => [k, clone(draft[k] ?? null)])), updatedAt: new Date().toISOString() }
  store.templates[template.id] = template; localStorage.setItem(storeKey(), JSON.stringify(store)); return template
}
export function deleteTemplate(template) {
  if (template.ownerType !== 'TEACHER' || template.ownerId !== owner()) throw new Error('系统模板只读，只能删除本人创建的模板。')
  const store = readStore(); delete store.templates[template.id]; store.deletedTemplates.push(template.id); localStorage.setItem(storeKey(), JSON.stringify(store))
}
export function applyTemplate(template) {
  const draft = newDraft()
  for (const k of ['type', 'primaryAbility', 'targetRole', 'trainingMode', 'durationMinutes', 'difficulty', 'mainQuestionCount', 'dynamicFollowUp', 'questions', 'minValidAttempts', 'maxAttempts', 'allowRepeat', 'allowLateCompletion']) if (template[k] != null) draft[k] = clone(template[k])
  draft.title = template.name; draft.sourceContext = { type: 'TEMPLATE', templateId: template.id }; return draft
}
