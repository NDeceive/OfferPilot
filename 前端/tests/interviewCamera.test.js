import { test } from 'node:test'
import assert from 'node:assert/strict'
import { prepareInterviewCamera, takeInterviewCamera, releaseInterviewCamera, cameraConsentOpen, answerCameraConsent } from '../src/utils/interviewCamera.js'

test('permission denial blocks entry; granted camera is requested once, transferred once, and released on cancellation', async () => {
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const originalWindow = globalThis.window
  globalThis.window = {}
  let calls = 0, stops = 0, deny = true
  const track = { readyState: 'live', stop() { stops++ } }
  const stream = { getVideoTracks: () => [track], getTracks: () => [track] }
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { mediaDevices: { async getUserMedia() {
    calls++
    if (deny) throw Object.assign(new Error(), { name: 'NotAllowedError' })
    return stream
  } } } })
  try {
    let request = prepareInterviewCamera()
    assert.equal(cameraConsentOpen.value, true)
    assert.equal(calls, 0)
    answerCameraConsent(false)
    await assert.rejects(request, /已取消进入面试/)
    assert.equal(calls, 0)
    request = prepareInterviewCamera()
    answerCameraConsent(true)
    await assert.rejects(request, /权限被拒绝/)
    assert.equal(takeInterviewCamera(), null)
    deny = false
    const requests = [prepareInterviewCamera(), prepareInterviewCamera()]
    assert.equal(cameraConsentOpen.value, true)
    answerCameraConsent(true)
    const results = await Promise.all(requests)
    assert.equal(results[0], stream)
    assert.equal(results[1], stream)
    assert.equal(calls, 2)
    assert.equal(takeInterviewCamera(), stream)
    assert.equal(takeInterviewCamera(), null)
    assert.equal(stops, 0)
    request = prepareInterviewCamera()
    assert.equal(cameraConsentOpen.value, true)
    answerCameraConsent(true)
    await request
    releaseInterviewCamera()
    assert.equal(stops, 1)
  } finally {
    releaseInterviewCamera()
    if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
    else delete globalThis.navigator
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
  }
})
