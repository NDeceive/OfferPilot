import test from 'node:test'
import assert from 'node:assert/strict'
import { pollInterviewReport, interviewReportRoute, presentInterviewClosing, INTERVIEW_CLOSING_TEXT } from '../src/utils/interviewCompletion.js'

test('closing stays visible until both minimum reading time and playback finish', async () => {
  const events = []
  let finishPlayback, finishReading
  const closing = presentInterviewClosing(text => events.push(text), () => new Promise(resolve => { finishPlayback = resolve }),
    ms => { assert.equal(ms, 5000); return new Promise(resolve => { finishReading = resolve }) })
  let complete = false
  closing.then(() => { complete = true })
  await Promise.resolve()
  assert.deepEqual(events, [INTERVIEW_CLOSING_TEXT])
  finishReading()
  await Promise.resolve()
  assert.equal(complete, false)
  finishPlayback()
  await closing
  assert.equal(complete, true)
})

test('unavailable avatar still shows the closing before the report', async () => {
  const events = []
  await presentInterviewClosing(text => events.push(text), () => { throw new Error('offline') }, async () => {})
  assert.deepEqual(events, [INTERVIEW_CLOSING_TEXT])
})

test('report polling uses a wall-clock budget even when status requests fail slowly', async () => {
  let elapsed=0, calls=0
  const result=await pollInterviewReport(async config=>{
    calls++; elapsed+=config.timeout; throw new Error('timeout')
  },{now:()=>elapsed,wait:async ms=>{elapsed+=ms}})
  assert.equal(result,null);assert.equal(elapsed,10000);assert.equal(calls,3)
})

test('slow report generation remains pollable beyond the old ten-second limit', async () => {
  let elapsed = 0
  const ready = { state: 'READY', ready: true, reportId: 42 }
  const status = await pollInterviewReport(async () => elapsed >= 15000 ? ready : { state: 'GENERATING' }, {
    budgetMs: 120000, now: () => elapsed, wait: async ms => { elapsed += ms },
  })
  assert.equal(status, ready)
  assert.equal(elapsed, 15000)
  assert.deepEqual(interviewReportRoute(9), { path: '/history/session/9', query: {} })
  assert.deepEqual(interviewReportRoute(9, 42, '3'), { path: '/history/42', query: { assignmentId: '3' } })
})

test('unauthorized report access stops polling and exposes the error', async () => {
  await assert.rejects(pollInterviewReport(async () => { throw Object.assign(new Error('forbidden'), { response: { status: 403 } }) }), /forbidden/)
})
test('ready and failed reports exit immediately; leaving the interview cancels polling',async()=>{
  const ready={ready:true,reportId:9}
  assert.equal(await pollInterviewReport(async()=>ready),ready)
  assert.equal((await pollInterviewReport(async()=>({state:'FAILED'}))).state,'FAILED')
  let cancelled=false
  assert.equal(await pollInterviewReport(async()=>{cancelled=true;return ready},{cancelled:()=>cancelled}),null)
})
