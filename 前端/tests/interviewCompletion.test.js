import test from 'node:test'
import assert from 'node:assert/strict'
import { pollInterviewReport } from '../src/utils/interviewCompletion.js'

test('report polling uses a wall-clock budget even when status requests fail slowly', async () => {
  let elapsed=0, calls=0
  const result=await pollInterviewReport(async config=>{
    calls++; elapsed+=config.timeout; throw new Error('timeout')
  },{now:()=>elapsed,wait:async ms=>{elapsed+=ms}})
  assert.equal(result,null);assert.equal(elapsed,10000);assert.equal(calls,3)
})
test('ready and failed reports exit immediately; leaving the interview cancels polling',async()=>{
  const ready={ready:true,reportId:9}
  assert.equal(await pollInterviewReport(async()=>ready),ready)
  assert.equal((await pollInterviewReport(async()=>({state:'FAILED'}))).state,'FAILED')
  let cancelled=false
  assert.equal(await pollInterviewReport(async()=>{cancelled=true;return ready},{cancelled:()=>cancelled}),null)
})
