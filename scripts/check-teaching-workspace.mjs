import assert from 'node:assert/strict'
import {inTeachingScope,isOverdue,executionStats,assignmentMatches,reportEvidence,validateTaskDraft} from '../前端/src/services/teachingWorkspace.js'
const task={id:3,classId:12,jobId:2,publishedAt:'2026-01-01T00:00:00',deadline:'2026-01-02T00:00:00'}
const a={id:8,completionStatus:'NOT_STARTED',validCount:0,attempts:[]},future={...a,id:9,effectiveDeadline:'2099-01-01T00:00:00'},exempt={...a,id:10,exempt:true},done={...a,id:11,completionStatus:'COMPLETED'}
assert(inTeachingScope(task,{classId:'12',jobId:'2',semester:'秋季'},[{id:12,semester:'秋季'}]))
assert(!inTeachingScope(task,{classId:13}));assert(!inTeachingScope(task,{classIds:'13,14'}))
assert(isOverdue(a,task));assert(!isOverdue(future,task));assert(!isOverdue(exempt,task));assert(!isOverdue(done,task));assert(!isOverdue(a,{...task,endedAt:'2026-01-03'}));assert(!isOverdue(a,{...task,publishedAt:null}))
assert.deepEqual(executionStats({...task,assignments:[a,future,exempt,done]}),{total:3,completed:1,overdue:1,exempt:1,failed:0})
assert(assignmentMatches(a,task,'completion'));assert(!assignmentMatches(exempt,task,'unfinished'))
assert(!reportEvidence({messages:[{role:'CANDIDATE',msgType:'SKIPPED',content:'跳过'}]}))
assert(reportEvidence({messages:[{role:'CANDIDATE',msgType:'ANSWER',content:'实际回答'}]}))
assert(!assignmentMatches({...exempt,attempts:[{state:'FAILED'}]},task,'failed'))
const draft={classId:12,title:'训练',jobId:2,startTime:'2026-01-01T00:00',deadline:'2026-01-02T00:00',recipientMode:'ALL',studentIds:[],minAttempts:1,maxAttempts:3,durationSeconds:900,difficulty:2,scoreModules:['a','b','c','d','e'].map(code=>({code,rank:1,level:2}))},members=[{studentId:7,state:'JOINED',accountStatus:1}]
assert.equal(validateTaskDraft(draft,['问题'],members),null)
assert.equal(validateTaskDraft({...draft,recipientMode:'SELECTED'},['问题'],members).step,0)
assert.equal(validateTaskDraft({...draft,recipientMode:'SELECTED',studentIds:[7]},['问题'],members),null)
assert.equal(validateTaskDraft({...draft,maxAttempts:0},['问题'],members).step,2)
assert.equal(validateTaskDraft({...draft,scoreModules:draft.scoreModules.slice(0,4)},['问题'],members).step,1)
assert.equal(validateTaskDraft({...draft,deadline:draft.startTime},['问题'],members).step,0)
console.log('PASS: entity scope, per-student deadlines, exemption, completion queue, answer evidence.')
