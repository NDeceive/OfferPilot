import assert from 'node:assert/strict'
import {supportRows} from '../前端/src/services/studentSupport.js'
const now=Date.now(),time=offset=>new Date(now+offset*86400000).toISOString()
const people=[{id:1,name:'甲',classId:1},{id:1,name:'甲',classId:2},{id:2,name:'乙',classId:1}]
const assignment={id:1,studentId:1,completionStatus:'IN_PROGRESS',memberState:'JOINED',effectiveMaxAttempts:3,effectiveDeadline:time(2),attempts:[{id:1,state:'READY',valid:1,submittedAt:time(-1)}]}
const task={id:1,classId:1,jobId:1,publishedAt:time(-5),startTime:time(-4),deadline:time(2),assignments:[assignment,{id:2,studentId:2,completionStatus:'NOT_STARTED',attempts:[]}]}
const report={studentId:1,taskId:1,jobId:1,reportId:1,state:'READY',valid:1,submittedAt:time(-1),detailReport:{moduleScores:[{moduleCode:'expression',rawScore:60,targetScore:80,baseWeight:1}],trainingContext:{provenance:{ruleVersion:'v2',modelVersion:'m1',promptVersion:'p1'}}}}
const options={jobId:'1',code:'expression',now}
let rows=supportRows(people,[task],[report],options)
assert.equal(rows.length,2);assert.equal(rows[0].classes.length,2);assert.equal(rows[0].count,1);assert(rows[0].groups.includes('short'));assert(rows[1].groups.includes('start'))
rows=supportRows(people,[task],[report],{...options,classId:'2'});assert.equal(rows.length,1);assert.equal(rows[0].count,0);assert.equal(rows[0].score,null)
for(const change of [{startTime:time(2)},{archived:true},{endedAt:time(-1)}])assert(!supportRows(people,[{...task,...change}],[report],options)[1].groups.includes('start'))
assert(!supportRows(people,[{...task,assignments:[{...assignment,exempt:true}]}],[report],options)[0].groups.includes('blocked'))
rows=supportRows(people,[{...task,assignments:[{...assignment,attempts:[1,2,3].map(id=>({id,state:'READY',valid:1,submittedAt:time(-1)}))}]}],[report],options);assert.equal(rows[0].count,3);assert(rows[0].groups.includes('invest'));assert(rows[0].groups.includes('blocked'))
rows=supportRows(people,[{...task,assignments:[{...assignment,attempts:[{id:1,state:'GENERATING',valid:1}],effectiveMaxAttempts:1}]}],[],options);assert(!rows[0].groups.includes('blocked'));assert(rows[0].evidence.includes('生成'))
rows=supportRows(people,[task],[{...report,readFailed:true}],options);assert.equal(rows[0].score,null);assert(rows[0].evidence.includes('失败'))
rows=supportRows(people,[{...task,assignments:[{...assignment,effectiveDeadline:time(-1)}]}],[report],options);assert(rows[0].groups.includes('overdue'))
rows=supportRows(people,[task],[{...report,detailReport:{moduleScores:report.detailReport.moduleScores}}],options);assert.equal(rows[0].score,null)
console.log('PASS student deduplication, scoped evidence, numeric validity, task exclusions, pending/failure distinction and personal deadlines')
