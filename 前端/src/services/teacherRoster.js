import { abilities as legacyAbilities, positions, growthFor, referenceDate } from './teacherModel.js'
export const commonAbilities = ['逻辑表达', '项目经历表达', '沟通表达', '岗位理解']
export const semesters = [{ id: '2026-1', name: '2026–2027 第一学期' }, { id: '2025-2', name: '2025–2026 第二学期' }]
export const classDefinitions = [
  { id: 'software2502', name: '软件2502', size: 45 }, { id: 'software2504', name: '软件2504', size: 48 },
  { id: 'software2501', name: '软件2501', size: 47 }, { id: 'software2503', name: '软件2503', size: 46 },
].map(c => ({ ...c, grade: '2025级', major: '软件工程', semester: '2026-1' }))
const day = 86400000, end = Date.parse(referenceDate + 'T23:59:59+08:00')
const average = values => values.length ? values.reduce((a, b) => a + b, 0) / values.length : null
const dateBefore = days => new Date(end - days * day - 10 * 3600000).toISOString()
const STORE = 'offerpilot:teacher:class-insights:v1'
export const pct = (n, total) => total ? Math.round(n / total * 100) : 0
export const inRange = (date, period = '30d') => date && Date.parse(date) <= end && Date.parse(date) > end - Number(period.replace('d', '')) * day
export function buildClassDemo() {
  const students = [], records = [], members = [], tasks = [], executions = []
  for (const [ci, c] of classDefinitions.entries()) {
    const counts = ci === 0 ? [...Array(13).fill(0), ...Array(8).fill(1), ...Array(9).fill(2), ...Array(5).fill(3), ...Array(5).fill(4), 6, 7, 8, 9, 12] : Array.from({ length: c.size }, (_, i) => i < [13, 11, 8, 9][ci] ? 0 : 1 + (i * 3 + ci) % 6)
    for (let i = 0; i < c.size; i++) {
      const id = `ci-${c.id}-${i + 1}`, position = positions[i % positions.length]
      const s = { id, name: `${['张', '李', '王', '陈', '刘', '赵', '孙', '周', '吴', '郑'][i % 10]}同学${String(i + 1).padStart(2, '0')}`, number: `${20250000 + ci * 100 + i + 1}`, className: c.name, position }
      students.push(s)
      members.push({ id: `member-${id}`, classId: c.id, studentId: id, state: 'joined', account: i === 8 && ci === 2 ? 'disabled' : 'normal', method: i % 3 ? 'Excel导入' : '手动添加', joinedAt: '2026-09-01' })
      const missing = ci === 0 ? i >= 9 && i <= 12 : i === 2 || i === 3
      const above = ci === 0 ? i < 4 || (i >= 21 && i <= 27) : i % 4 === 0
      // 41 current common-ability samples: 11 reached target, 30 below target.
      const belowIndex = [...Array(45).keys()].filter(j => !(j >= 9 && j <= 12) && !(j < 4 || (j >= 21 && j <= 27))).indexOf(i)
      const common = Object.fromEntries(commonAbilities.map((a, k) => [a, above ? ci === 0 ? 86 + ([0,1,2,3,21,22,23,24,25,26,27].indexOf(i)-5) : 86 + i % 5 - 2 : ci === 0 ? ([65, 67, 70, 72][k] * 41 - 86 * 11) / 30 + (belowIndex - 14.5) * .35 : 57 + (i * 7 + k * 4 + ci) % 22]))
      const n = counts[i]
      for (let j = 0; j < Math.max(n, missing ? 0 : 1); j++) {
        const score = i >= 40 && i <= 42 && ci === 0 ? [78, 78, 78, 74, 71, 68, 65, 62][Math.min(j, 7)] : 65 + i % 12 + j
        const offset = n ? (n >= 6 ? Math.floor((n - 1 - j) * 27 / (n - 1)) : (i * 7 + j * 4) % 29) : 45 + i
        const recentOrdinal = ci === 0 ? counts.slice(0, i).reduce((a, b) => a + b, 0) + j : j + i
        const source = ci === 0 ? recentOrdinal < 61 ? 'self' : 'teacher' : (i + j) % 2 ? 'teacher' : 'self'
        records.push({ id: `ci-record-${id}-${j}`, studentId: id, classId: c.id, position, model: position, version: 'v1', commonVersion: 'common-v1', valid: true, score,
          completedAt: dateBefore(offset), common: missing ? null : common, specialty: missing ? null : { 专业知识: 64 + i % 18, 岗位实践: 60 + i % 20 },
          abilityScores: legacyAbilities.map(a => common[a] ?? 70), type: j % 2 ? '专项训练' : 'AI虚拟面试', source, taskId: source === 'teacher' ? `ci-task-${c.id}-1` : null,
          content: j % 2 ? '表达能力专项训练' : `${position}综合模拟`, duration: '15分00秒', result: j % 2 ? '训练完成' : '报告已生成' })
      }
    }
    for (let i = 0; i < 3; i++) {
      const id = `ci-pending-${c.id}-${i + 1}`
      students.push({ id, name: `待加入学生${i + 1}`, number: `${20259000 + ci * 10 + i}`, className: c.name, position: positions[i] })
      members.push({ id: `member-${id}`, classId: c.id, studentId: id, state: 'pending', account: 'inactive', method: 'Excel导入', joinedAt: null })
    }
    const names = ['Java面试专项训练', '项目经历表达训练', '综合模拟面试', '算法与数据结构基础']
    names.forEach((name, ti) => {
      const id = `ci-task-${c.id}-${ti}`, deadline = ti < 2 ? '2026-09-28' : `2026-10-${ti === 2 ? '11' : '15'}`
      tasks.push({ id, classId: c.id, name, type: ti === 1 ? '通用能力' : ti === 2 ? '综合训练' : '岗位专项', position: ti === 1 || ti === 2 ? '' : positions[ti], deadline, createdAt: '2026-09-10T09:00:00+08:00' })
      const targets = members.filter(m => m.classId === c.id && m.state === 'joined').filter((m, i) => ti === 1 || ti === 2 && i % 4 !== 0 || (ti === 0 || ti === 3) && students.find(s=>s.id===m.studentId).position === positions[ti])
      targets.forEach((m, i) => executions.push({ taskId: id, studentId: m.studentId, classId: c.id, state: i % 10 < Math.min(9, 6 + ci + ti % 2) ? 'completed' : i % 10 < 9 ? 'ongoing' : 'notStarted' }))
    })
  }
  return { source: 'demo', referenceDate, classes: classDefinitions, students, records, members, tasks, executions, imports: [] }
}
function readChanges() {
  try { const raw = JSON.parse(localStorage.getItem(STORE) || '{}'); if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return {}; return { removed: Array.isArray(raw.removed) ? raw.removed.filter(id=>typeof id==='string') : [], joined: Array.isArray(raw.joined) ? raw.joined.filter(id=>typeof id==='string') : [], added: Array.isArray(raw.added) ? raw.added.filter(m=>m && typeof m.id==='string' && typeof m.studentId==='string' && classDefinitions.some(c=>c.id===m.classId) && ['joined','pending'].includes(m.state)) : [], imports: Array.isArray(raw.imports) ? raw.imports.filter(i=>i && typeof i.filename==='string' && typeof i.classId==='string') : [] } } catch { return {} }
}
export function getClassDemo() {
  const data = buildClassDemo(), changes = readChanges()
  const removed = new Set(changes.removed || [])
  data.members = data.members.filter(m => !removed.has(m.id)).concat(changes.added || []).map(m => changes.joined?.includes(m.id) ? { ...m, state: 'joined', account: 'normal', joinedAt: referenceDate } : m)
  data.imports = changes.imports || []
  return data
}
export function saveMemberChanges(update) { const next = update(readChanges()); localStorage.setItem(STORE, JSON.stringify(next)); return getClassDemo() }
export function removeMembers(ids) { return saveMemberChanges(c => ({ ...c, removed: [...new Set([...(c.removed || []), ...ids])], added: (c.added || []).filter(m => !ids.includes(m.id)), joined: (c.joined || []).filter(id => !ids.includes(id)) })) }
export function joinMember(id) { return saveMemberChanges(c => ({ ...c, joined: [...new Set([...(c.joined || []), id])] })) }
export function addMembers(classId, studentIds, method = '手动添加', filename = '') {
  const data = getClassDemo(), existing = new Set(data.members.filter(m => m.classId === classId).map(m => m.studentId))
  const safeIds = [...new Set(studentIds)].filter(id => data.students.some(s => s.id === id) && !existing.has(id))
  return saveMemberChanges(c => ({ ...c, added: [...(c.added || []), ...safeIds.map(studentId => ({ id: `added-${classId}-${studentId}`, classId, studentId, state: 'pending', account: 'normal', method, joinedAt: null }))], imports: filename ? [{ id: Date.now().toString(), classId, filename, count: safeIds.length, at: new Date().toLocaleString('zh-CN') }, ...(c.imports || [])] : c.imports || [] }))
}
