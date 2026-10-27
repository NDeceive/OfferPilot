import test from 'node:test'
import assert from 'node:assert/strict'
import { createDemoEngine, roughCodeResult } from '../src/services/practiceDemo/engine.js'
import fixtures from '../src/services/practiceDemo/questions.json' with { type: 'json' }

function fixture(){const values=new Map();let owner='21';const storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)};const engine=createDemoEngine(storage,()=>owner);engine.setRoles([{id:1,code:'BE-JAVA',name:'Java后端开发工程师'}]);return {engine,values,switchOwner:value=>owner=value}}
test('self assessment persists separately from historical grading and only applies to finished self review submissions',async()=>{
  const {engine,values,switchOwner}=fixture()
  const s=await engine.handle('/practice/sessions','post',{jobId:1,topic:'综合高频',questionType:'knowledge',questionCount:1,difficulty:'basic'}),q=s.questionList[0]
  await engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/answers`,'post',{answer:'我的作答'})
  assert.throws(()=>engine.selfAssess(s.id,q.id,'MASTERED'))
  const initial=await engine.handle(`/practice/sessions/${s.id}/finish`,'post'),submission=initial.questionList[0].latestSubmission
  const assessed=engine.selfAssess(s.id,q.id,'NEEDS_REVIEW')
  assert.equal(assessed.reviewState,'PENDING')
  const reloaded=createDemoEngine({getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)},()=> '21')
  const report=await reloaded.handle(`/practice/sessions/${s.id}/report`,'get')
  assert.equal(report.statistics.selfReview,0);assert.equal(report.questionList[0].selfAssessment.state,'NEEDS_REVIEW')
  assert.deepEqual(report.questionList[0].latestSubmission,submission);assert.equal(report.settlement.evaluated,0)
  engine.selfAssess(s.id,q.id,'MASTERED');assert.equal((await engine.handle(`/practice/sessions/${s.id}/report`,'get')).questionList[0].reviewState,'RESOLVED')
  const retry=await engine.handle(`/practice/sessions/${s.id}/retry`,'post',{}, {failedOnly:false})
  assert.equal(retry.questionList[0].selfAssessment,null)
  assert.throws(()=>engine.selfAssess(s.id,q.id,'invalid'))
  switchOwner('22');assert.throws(()=>engine.selfAssess(s.id,q.id,'MASTERED'))
})
test('ongoing submissions, history, report and tasks defer all correctness; finish reveals complete settlement',async()=>{
  const {engine}=fixture()
  const s=await engine.handle('/practice/sessions','post',{jobId:1,topic:'综合高频',questionType:'all',questionCount:10,difficulty:'basic'})
  assert.equal(s.questionList.length,10)
  assert.equal(s.questionList.filter(q=>q.type==='coding').length,3)
  const q=s.questionList.find(q=>q.type==='choice'),original=fixtures.find(item=>item.canonicalId===q.canonicalId)
  const result=await engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/answers`,'post',{answer:original.correctAnswer})
  assert.equal(result.status,'SUBMITTED');assert.ok(!('passed' in result));assert.ok(!('referenceAnswer' in result))
  const ongoing=await engine.handle(`/practice/sessions/${s.id}`,'get')
  assert.ok(ongoing.questionList.every(q=>!q.correctAnswer&&!q.referenceAnswer))
  const history=await engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/submissions`,'get')
  assert.ok(!('isCorrect' in history.items[0]));assert.equal(history.items[0].status,'SUBMITTED')
  const coding=s.questionList.find(q=>q.type==='coding')
  const run=await engine.handle(`/practice/sessions/${s.id}/questions/${coding.id}/tasks`,'post',{requestKey:'run1',operation:'run',sourceCode:'invalid'})
  assert.equal(run.result.submitted,false)
  assert.equal((await engine.handle(`/practice/sessions/${s.id}`,'get')).completed,1)
  const task=await engine.handle(`/practice/sessions/${s.id}/questions/${coding.id}/tasks`,'post',{requestKey:'submit1',operation:'submit',sourceCode:'public class Main { static void main(String[] args) { System.out.println(0); } }'})
  assert.equal(task.status,'SUBMITTED');assert.ok(!('passed' in task.result))
  assert.equal((await engine.handle(`/practice/tasks/${task.id}`,'get')).status,'SUBMITTED')
  const early=await engine.handle(`/practice/sessions/${s.id}/report`,'get')
  assert.equal(early.settlementStatus,'DEFERRED');assert.equal(early.statistics.objectiveCorrect,0)
  const report=await engine.handle(`/practice/sessions/${s.id}/finish`,'post')
  assert.equal(report.status,'FINISHED');assert.equal(report.statistics.submitted,2);assert.equal(report.statistics.unsubmitted,8)
  assert.equal(report.statistics.objectiveCorrect,1);assert.equal(report.statistics.codingFailed,1)
  assert.equal(report.settlement.score,50)
  assert.equal(report.questionList.find(item=>item.id===q.id).latestSubmission.status,'OBJECTIVE_CORRECT')
  assert.equal((await engine.handle(`/practice/tasks/${task.id}`,'get')).status,'WRONG_ANSWER')
  await assert.rejects(engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/answers`,'post',{answer:original.correctAnswer}))
  const retry=await engine.handle(`/practice/sessions/${s.id}/retry`,'post',{}, {failedOnly:true})
  assert.equal(retry.questionList.length,1);assert.equal(retry.questionList[0].type,'coding')
})
test('demo state persists per user and task request keys prevent duplicate submissions',async()=>{
  const {engine,values,switchOwner}=fixture(),s=await engine.handle('/practice/sessions','post',{jobId:1,topic:'编程与算法',questionType:'coding',questionCount:1,difficulty:'basic'})
  const q=s.questionList[0],input={requestKey:'one',operation:'submit',sourceCode:'bad code'}
  const t=await engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/tasks`,'post',input)
  assert.equal((await engine.handle(`/practice/sessions/${s.id}/questions/${q.id}/tasks`,'post',input)).id,t.id)
  assert.equal((await engine.handle(`/practice/sessions/${s.id}`,'get')).submissions.length,1)
  const restored=createDemoEngine({getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)},()=>21)
  assert.equal((await restored.handle(`/practice/sessions/${s.id}`,'get')).completed,1)
  switchOwner('22');assert.equal((await engine.handle('/practice/overview','get')).libraryCounts.recent,0)
  await assert.rejects(engine.handle(`/practice/sessions/${s.id}`,'get'))
})
test('rough code rules reject placeholders, distinguish algorithm structure and never execute submitted code',()=>{
  const q={canonicalId:'exercism:leap'}
  assert.equal(roughCodeResult('public class Main { static void main(String[] args){ int x = ; } }',q).status,'COMPILE_ERROR')
  assert.equal(roughCodeResult('public class Main { static void main(String[] args){ System.out.println(0); } }',q).passed,false)
  assert.equal(roughCodeResult('public class Main { static void main(String[] args){ int n=2024; System.out.println(n%4==0 && n%100!=0 || n%400==0); } }',q).passed,true)
})
