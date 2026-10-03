import assert from 'node:assert/strict'
import {filterData,growthFor,activityFor,dailyTrend} from '../前端/src/services/teacherModel.js'
const now=Date.parse('2026-10-03T15:00:00+08:00'),student={id:'s',classId:'software2501',className:'软件2501',position:'Java'},scores=[78,78,78,74,71,68]
const records=scores.map((score,i)=>({id:'r'+i,studentId:'s',classId:student.classId,position:'Java',model:'Java',version:'v1',valid:true,score,abilityScores:Array(5).fill(score),completedAt:'2026-09-'+String(10+i*3).padStart(2,'0')+'T12:00:00+08:00'}))
const data={students:[student],records,tasks:[],overdue:[],now,referenceDate:'2026-10-03',classes:[{id:student.classId,name:student.className}]},full=filterData(data,{})
assert.equal(full.records.length,6);assert.equal(full.declining.length,1);assert.equal(filterData(data,{period:'7d'}).records.length,0)
assert.equal(filterData(data,{classId:'software2502'}).students.length,0)
const dates=filterData(data,{from:'2026-09-13',to:'2026-09-19'});assert.equal(dates.records.length,3)
const trend=dailyTrend(data,dates.records,{from:'2026-09-13',to:'2026-09-19'});assert.equal(trend.length,7);assert.equal(trend.reduce((n,r)=>n+r.count,0),3)
assert.equal(activityFor(3,'7d'),'高活跃');assert.equal(activityFor(3,'30d'),'正常训练');assert.equal(growthFor(records.slice(0,5)).status,'数据不足')
for(const key of ['position','model','version']){const changed=records.map((r,i)=>({...r,[key]:i===5?'changed':r[key]}));assert.equal(growthFor(changed).count,1)}
assert.equal(growthFor(records.map(r=>({...r,abilityScores:[]}))).status,'数据不足')
console.log('Teacher model: ID/class and date filters, custom trend dates, activity thresholds, six-record and version-stage rules passed.')
