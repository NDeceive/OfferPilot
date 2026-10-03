import assert from 'node:assert/strict'
import {analyticsFacts,buildAnalytics,dateRange,completion,time} from '../前端/src/services/teachingAnalyticsModel.js'
const classes=[{id:1},{id:2}],people=[{id:1,classId:1},{id:2,classId:1},{id:2,classId:2},{id:3,classId:2}],now=time('2026-10-04T18:00:00'),range=dateRange('2026-09-01','2026-10-04',now)
const task={id:1,classId:1,jobId:1,publishedAt:'2026-09-01T00:00:00',startTime:'2026-09-10T00:00:00',deadline:'2026-09-20T00:00:00',minAttempts:2,assignments:[{id:1,studentId:1,completionStatus:'COMPLETED',effectiveDeadline:'2026-09-21T00:00:00',attempts:[{id:1,state:'READY',valid:1,submittedAt:'2026-09-11T00:00:00'},{id:2,state:'READY',valid:0,submittedAt:'2026-09-12T00:00:00'},{id:3,state:'READY',valid:true,submittedAt:'2026-09-15T00:00:00'}]},{id:2,studentId:2,completionStatus:'COMPLETED',attempts:[]}]}
const options={classes,people,facts:analyticsFacts([task],[]),classIds:['1','2'],jobId:'1',code:'expression',...range,now}
let m=buildAnalytics(options);assert.equal(m.students.length,3);assert.equal(m.records.length,2);assert.equal(m.students[0].count,2);assert.equal(m.due.onTime.length,1);assert.equal(m.due.unknown.length,1);assert.equal(completion({...task.assignments[0],task}),time('2026-09-15T00:00:00'));assert.equal(m.weekly.reduce((n,w)=>n+w.count,0),2)
m=buildAnalytics({...options,classIds:['2']});assert.equal(m.records.length,0);assert.equal(m.students.length,2)
const report=(id,at,score)=>({reportId:id,studentId:1,taskId:1,classId:1,jobId:1,state:'READY',valid:1,submittedAt:at,detailReport:{moduleScores:[{moduleCode:'expression',rawScore:score,targetScore:80,baseWeight:1}],trainingContext:{provenance:{ruleVersion:'v2',modelVersion:'m',promptVersion:'p'}}}})
const reports=[report(1,'2026-09-05T00:00:00',0),report(2,'2026-09-25T00:00:00',70)]
m=buildAnalytics({...options,facts:analyticsFacts([task],reports),taskId:'1'});assert.equal(m.pairs.length,1);assert.equal(m.pairs[0].beforeScore,0);assert.equal(m.pairs[0].delta,70);assert.equal(m.mean,null)
m=buildAnalytics({...options,...dateRange('2026-09-01','2026-09-20',now),facts:analyticsFacts([task],reports),taskId:'1'});assert.equal(m.candidates[0].state,'observing')
m=buildAnalytics({...options,facts:analyticsFacts([task],[{...reports[0],readFailed:true},reports[1]]),taskId:'1'});assert.equal(m.pairs.length,0);assert.equal(m.candidates[0].state,'read');assert.equal(m.records.length,2)
const boundary=dateRange('2026-10-01','2026-10-01',now);assert(time('2026-10-01T23:59:59.999')<boundary.end);assert.equal(time('2026-10-02T00:00:00'),boundary.end);assert.equal(time('2026-10-01T00:00:00-05:00'),Date.parse('2026-10-01T00:00:00-05:00'))
assert(dateRange('2026-02-30','2026-03-01',now).error)
assert(dateRange('2026-10-03','2026-10-01',now).error)
m=buildAnalytics({...options,classIds:[]});assert.equal(m.students.length,0);assert.equal(m.records.length,0)
console.log('PASS live analytics model: deduplication, membership scopes, numeric validity, zero score, completion, pending window, partial failure, empty scope and date boundaries')
