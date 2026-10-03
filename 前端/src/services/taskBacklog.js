import { isOverdue } from './teachingWorkspace.js'
export function taskBacklog(tasks) {
  const students = new Map()
  for (const task of tasks.filter(t => t.publishedAt && !t.endedAt && !t.archived && t.lifecycle !== 'SCHEDULED')) {
    for (const assignment of task.assignments || []) {
      if (assignment.exempt || assignment.completionStatus === 'COMPLETED') continue
      const student = students.get(assignment.studentId) || { id: assignment.studentId, name: assignment.name || '未提供姓名', tasks: [], overdue: 0 }
      if (student.tasks.some(row => row.task.id === task.id)) continue
      const overdue = isOverdue(assignment, task)
      student.tasks.push({ task, assignment, overdue })
      student.overdue += Number(overdue)
      students.set(student.id, student)
    }
  }
  return [...students.values()].sort((a, b) => b.tasks.length - a.tasks.length || b.overdue - a.overdue)
}
