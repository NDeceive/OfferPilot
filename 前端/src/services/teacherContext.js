import {classDefinitions} from './teacherRoster.js'
export const teacherNow=Date.parse('2026-10-03T15:00:00+08:00')
export const teacherOwner=()=>localStorage.getItem('userId')||localStorage.getItem('username')||'local-teacher'
export function normalizeTeacherQuery(query={}){
 const q={...query},id=q.classId||classDefinitions.find(c=>c.name===q.class)?.id
 if(id){q.classId=id;q.class=classDefinitions.find(c=>c.id===id)?.name||q.class}
 if(q.timeRange&&!q.period)q.period=['30d','90d'].includes(q.timeRange)?q.timeRange:'semester'
 if(q.analyticsFrom){q.from=q.from||q.analyticsFrom;q.to=q.to||q.analyticsTo}
 return q
}
export function recordInScope(r,q={},now=teacherNow){
 const at=Date.parse(r.completedAt),start=q.from?Date.parse(q.from+'T00:00:00+08:00'):q.period==='all'?-Infinity:q.period==='semester'?Date.parse('2026-09-01T00:00:00+08:00'):now-Number((q.period||'30d').replace('d',''))*86400000
 const end=q.to?Math.min(now,Date.parse(q.to+'T23:59:59.999+08:00')):now
 return Number.isFinite(at)&&at>=start&&at<=end
}
export function currentAbility(records,student,name,scope='common',query={},now=teacherNow,position=student.position){
 const own=records.filter(r=>r.studentId===student.id&&recordInScope(r,query,now)).sort((a,b)=>Date.parse(a.completedAt)-Date.parse(b.completedAt))
 const latest=own.at(-1),key=r=>`${r.position}|${scope==='common'?r.commonVersion||r.model:r.model}|${r.version}`
 if(!latest||latest.position!==position||!latest.model||!latest.version)return {records:[],calculationRecords:[],current:null,latest,version:null}
 let start=own.length-1;while(start>0&&key(own[start-1])===key(latest))start--
 const value=r=>(scope==='common'?r.dimensions||r.common:r.roleDimensions||r.specialty)?.[name]
 const rows=own.slice(start).filter(r=>r.valid&&Number.isFinite(value(r))&&value(r)>=0&&value(r)<=100).map((r,i,stage)=>({...r,abilityScore:value(r),previousScore:i?value(stage[i-1]):null,delta:i?value(r)-value(stage[i-1]):null}))
 const calculationRecords=rows.slice(-3)
 return {records:rows,calculationRecords,current:calculationRecords.length?calculationRecords.reduce((n,r)=>n+r.abilityScore,0)/calculationRecords.length:null,latest,version:`${scope==='common'?latest.commonVersion||latest.model:latest.model} / ${latest.version}`}
}
export const safeTeacherReturn=value=>typeof value==='string'&&/^\/teacher\/(dashboard|classes|students|tasks|training-records|reviews|activity|growth|analytics|reports)(\/[^?#]*)?(\?|$)/.test(value)&&!value.includes('\\')?value:null
export function teacherContext(query){const q=normalizeTeacherQuery(query);return Object.fromEntries(['demo','classId','classIds','class','position','period','abilityPeriod','semester','from','to','analyticsFrom','analyticsTo','analyticsActive','grade','major','timeRange','returnTo','returnClass','sourceTab','returnStudent'].filter(k=>q[k]).map(k=>[k,q[k]]))}
