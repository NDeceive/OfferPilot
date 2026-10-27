import test from 'node:test'
import assert from 'node:assert/strict'
import {compileLocations,restoreJudgeResult,judgeStatusLabel} from '../src/utils/practiceJudging.js'
test('only real Java diagnostic locations are extracted',()=>{
  assert.deepEqual(compileLocations('Main.java:12: error: expected\n/tmp/Main.java:21:3: error: bad'),[{line:12,column:1},{line:21,column:3}])
  assert.deepEqual(compileLocations('runtime error at 100'),[])
})
test('server formal results override local results and preserve unknown correctness',()=>{
  assert.equal(restoreJudgeResult({id:5,result:{status:'COMPILE_ERROR'}},{status:'ACCEPTED'}).status,'COMPILE_ERROR')
  assert.equal(restoreJudgeResult({id:6,status:'SELF_REVIEW',isCorrect:null},{submissionId:5}).passed,null)
  assert.equal(judgeStatusLabel('unrecognized'),'未知状态，待核实')
})
