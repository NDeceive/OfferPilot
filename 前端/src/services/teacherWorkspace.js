import {workspaceDataset} from './teacherData.js'
import { getTeacherOverview } from '../api'

// The current endpoint has lifetime session counts and a seven-day creation trend,
// but no valid-record IDs, classes, comparable scoring models, tasks or reviews.
// Keep it intact and expose missing metrics as unavailable instead of inventing them.
export async function loadTeacherWorkspace(mode, context = {}) {
  if (mode === 'demo') return getDemoWorkspace(context)
  const overview = await getTeacherOverview()
  return { source: 'live', overview, students: [], records: [], tasks: [], overdue: [], pending: [], referenceDate: new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }) }
}

export function getDemoWorkspace(context={}) {const data=workspaceDataset();if(context.analyticsActive||context.analyticsFrom){const ids=new Set(data.students.filter(s=>s.account==='normal').map(s=>s.id));data.students=data.students.filter(s=>ids.has(s.id));data.records=data.records.filter(r=>ids.has(r.studentId))}return data}
