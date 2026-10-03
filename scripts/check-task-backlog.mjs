import assert from 'node:assert/strict'
import { taskBacklog } from '../前端/src/services/taskBacklog.js'
const assignments=[{studentId:1,name:'甲',completionStatus:'NOT_STARTED'},{studentId:2,name:'乙',completionStatus:'COMPLETED'},{studentId:3,name:'丙',exempt:true}]
const task={publishedAt:'2020-01-01',deadline:'2020-01-02T00:00:00',lifecycle:'ACTIVE',assignments}
const rows=taskBacklog([{...task,id:1},{...task,id:2},{...task,id:3},{...task,id:4,endedAt:'2020-01-03'},{...task,id:5,archived:true},{...task,id:6,lifecycle:'SCHEDULED'},{...task,id:7,publishedAt:null}])
assert.equal(rows.length,1)
assert.equal(rows[0].tasks.length,3)
assert.equal(rows[0].overdue,3)
assert.equal(taskBacklog([]).length,0)
assert.equal(taskBacklog([{...task,id:1,assignments:[...assignments,assignments[0]]}])[0].tasks.length,1)
console.log('PASS: backlog counts, exemptions, completed/ended/archived/scheduled/draft exclusions, deduplication and empty state.')

