// Synthetic teacher data only. Never merge these records with live API results.
export const abilities = ['项目经历表达', '逻辑表达', '沟通表达', '专业知识', '岗位理解']
export const classes = ['软件2501', '软件2502', '软件2503', '软件2504']
export const positions = ['Java后端开发', '前端开发', '测试开发', '算法工程师', '产品经理']
export const referenceDate = '2026-10-03'
const day = 86400000
export const timestamp = value => Date.parse(value?.replace(' ', 'T') + (value?.includes('+') || value?.endsWith('Z') ? '' : '+08:00'))
const anchor = timestamp(referenceDate + 'T15:00:00')
export const daysAgo = value => value ? Math.max(0, Math.floor((anchor - timestamp(value)) / day)) : Infinity
const mean = values => values.reduce((a, b) => a + b, 0) / values.length

export function growthFor(records) {
  const sorted = records.filter(r => r.valid).sort((a, b) => timestamp(a.completedAt) - timestamp(b.completedAt))
  const latest = sorted.at(-1)
  if (!latest) return { status: '数据不足', count: 0, records: [], delta: null }
  const key = r => `${r.position}|${r.model}|${r.version}`
  // A position/model change begins a new contiguous stage; do not reuse an older stage.
  let start = sorted.length - 1
  while (start > 0 && key(sorted[start - 1]) === key(latest)) start--
  const comparable = sorted.slice(start).filter(r => r.position && r.model && r.version && Number.isFinite(r.score) && r.score >= 0 && r.score <= 100 && r.abilityScores?.length === abilities.length && r.abilityScores.every(v => Number.isFinite(v) && v >= 0 && v <= 100))
  if (comparable.length < 6) return { status: '数据不足', count: comparable.length, records: comparable, delta: null }
  const first = comparable.slice(0, 3), recent = comparable.slice(-3)
  const early = mean(first.map(r => r.score)), current = mean(recent.map(r => r.score))
  const delta = Math.round((current - early) * 10) / 10
  const loss = recent[0].score - recent[2].score
  const declining = recent[0].score > recent[1].score && recent[1].score > recent[2].score && loss >= 5
  const changes = abilities.map((name, i) => ({ name, delta: Math.round(mean(recent.map(r => r.abilityScores[i])) - mean(first.map(r => r.abilityScores[i]))) }))
  changes.sort((a, b) => Math.abs(b.delta) - Math.abs(a.delta))
  return { status: declining ? '持续下降' : delta >= 3 ? '有效提升' : delta <= -3 ? '轻微下降' : '基本稳定', count: comparable.length, records: comparable, early, current, delta, loss, changes }
}

export function activityFor(count, period = '30d') {
  const thresholds = period === '7d' ? [3, 2] : [5, 3]
  return count >= thresholds[0] ? '高活跃' : count >= thresholds[1] ? '正常训练' : count ? '低活跃' : '未训练'
}

export function filterData(data, query) {
  query={...query,classId:query.classId||data.classes?.find(c=>c.name===query.class)?.id}
  const length = query.period === '90d' ? 90 : query.period === '7d' ? 7 : 30
  const end = data.now||timestamp(data.referenceDate+'T23:59:59')
  let students = data.students.filter(s => (!query.classId || !s.classId || s.classId === query.classId) && (!query.classIds || String(query.classIds).split(',').includes(s.classId)) && (!query.class || s.className === query.class) && (!query.position || s.position === query.position) && (!query.student || s.id === query.student) && (!query.semester || query.semester === '2026-1'))
  const ids = new Set(students.map(s => s.id))
  const records = data.records.filter(r => ids.has(r.studentId) && r.valid && timestamp(r.completedAt) >= (query.from?Date.parse(query.from+'T00:00:00+08:00'):query.period==='semester'?Date.parse('2026-09-01T00:00:00+08:00'):end-length*day+1) && timestamp(r.completedAt) <= (query.to ? Date.parse(query.to+'T23:59:59.999+08:00') : end) && (!query.position || r.position === query.position))
  students = students.map(s => {
    const own = records.filter(r => r.studentId === s.id).sort((a,b)=>timestamp(b.completedAt)-timestamp(a.completedAt))
    const activeDates = [...new Set(own.map(r => r.completedAt.slice(0, 10)))].sort().reverse()
    let streak = activeDates.length ? 1 : 0
    while (streak < activeDates.length && Date.parse(activeDates[streak - 1]) - Date.parse(activeDates[streak]) === day) streak++
    return { ...s, count: own.length, last: own[0]?.completedAt, activity: activityFor(own.length, query.period), growth: growthFor(own), average: own.length ? mean(own.map(r => r.score)) : null, activeDays: activeDates.length, streak }
  })
  const tasks = data.tasks.filter(t => (!query.classId || !t.classId || t.classId === query.classId)&&(!query.classIds||String(query.classIds).split(',').includes(t.classId)) && (!query.position || !t.position || t.position === query.position))
  return { students, records, tasks, pending: records.filter(r => r.result === '待点评'), overdue: data.overdue.filter(o => ids.has(o.studentId) && tasks.some(t => t.id === o.taskId)), declining: students.filter(s => s.growth.status === '持续下降') }
}

export function dailyTrend(data,records,scope={}) {
 const q=typeof scope==='string'?{period:scope}:scope,now=data.now||anchor,end=Math.min(now,q.to?timestamp(q.to+'T23:59:59'):now)
 const from=q.from|| (q.period==='semester'?'2026-09-01':new Date(end+8*3600000-((q.period==='90d'?90:q.period==='7d'?7:30)-1)*day).toISOString().slice(0,10))
 const start=timestamp(from+'T12:00:00'),last=timestamp(new Date(end+8*3600000).toISOString().slice(0,10)+'T12:00:00')
 if(start>last||!Number.isFinite(start))return []
 return Array.from({length:Math.min(367,Math.floor((last-start)/day)+1)},(_,i)=>{const date=new Date(start+i*day+8*3600000).toISOString().slice(0,10),own=records.filter(r=>r.completedAt.slice(0,10)===date);return {date,count:own.length,active:new Set(own.map(r=>r.studentId)).size,average:own.length?mean(own.map(r=>r.score)):null}})
}
