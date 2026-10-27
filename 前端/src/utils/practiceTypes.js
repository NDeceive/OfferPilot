export const practiceTypeNames = { all: '混合题单', knowledge: '简答题', coding: '编程题', choice: '单选题', multiple_choice: '多选题', true_false: '判断题' }
export const isObjectiveType = type => ['choice', 'multiple_choice', 'true_false'].includes(String(type).toLowerCase())
export const practiceTypeLabel = type => practiceTypeNames[String(type).toLowerCase()] || '简答题'

export function toggleObjectiveAnswer(answer, key, multiple) {
  if (!multiple) return key
  const values = new Set(String(answer || '').split(',').filter(Boolean))
  values.has(key) ? values.delete(key) : values.add(key)
  return [...values].sort().join(',')
}

export function objectiveQuestionCount(topic, role, catalog, type, difficulty = 'medium') {
  if (!isObjectiveType(type)) return null
  const limit = ({ basic: 1, medium: 2, advanced: 3, all: 3 })[difficulty] || 3
  const broad = ['综合高频', '项目场景'].includes(topic)
  return (catalog?.objectiveCoverage || []).filter(q => q.type?.toLowerCase() === type && q.jobCodes?.includes(role?.code) && (broad || q.topics?.includes(topic)) && Number(q.difficulty) <= limit).length
}
