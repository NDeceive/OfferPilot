// Real local services + real providers; only this script's disposable account/session is modified.
const fs = require('node:fs')
const path = require('node:path')
const cp = require('node:child_process')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const root = path.resolve(__dirname, '..')
const out = path.join(root, 'artifacts/interview-live-debug')
fs.mkdirSync(out, { recursive: true })
const result = { startedAt: new Date().toISOString(), errors: [], blockers: [], phases: {}, httpFailures: [] }
const sessions = []
let auth, browser, context, selectedJob
const wait = ms => new Promise(resolve => setTimeout(resolve, ms))
async function api(endpoint, body, method = body ? 'POST' : 'GET') {
  const response = await fetch('http://127.0.0.1:8080/api' + endpoint, {
    method, headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth.token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(90000),
  })
  const data = await response.json()
  if (data.code !== 200) throw new Error(`${endpoint}: ${data.code} ${data.message}`)
  return data.data
}
function sql(statement) {
  return cp.execFileSync(process.env.MYSQL_BIN || 'C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe', [
    '--host=' + (process.env.DB_HOST || '127.0.0.1'), '--user=' + (process.env.DB_USER || 'root'),
    '--database=' + (process.env.DB_NAME || 'zhimian'), '--default-character-set=utf8mb4', '--batch', '--skip-column-names',
  ], { env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || '123456' }, input: statement, encoding: 'utf8', windowsHide: true }).trim()
}
async function start(duration = 600) {
  const session = await api('/interview/start', { jobId: selectedJob.id, difficulty: 2, durationSeconds: duration, skipResume: true, voiceEnabled: true, requestId: crypto.randomUUID() })
  sessions.push(session.sessionId)
  return session.sessionId
}
async function main() {
  for (const endpoint of ['http://127.0.0.1:8080/api/dashboard/overview', 'http://127.0.0.1:8010/offerpilot-embed.html']) {
    let ready = false
    for (let i = 0; i < 60; i++) {
      try { const response = await fetch(endpoint, { signal: AbortSignal.timeout(2000) }); ready = response.ok || response.status === 401 } catch {}
      if (ready) break
      await wait(1000)
    }
    assert.ok(ready, 'service must be ready: ' + endpoint)
  }
  const username = 'dbg' + crypto.randomBytes(6).toString('hex')
  const password = crypto.randomBytes(9).toString('hex')
  await api('/auth/register', { username, password, nickname: '联调临时账号' })
  auth = await api('/auth/login', { username, password })
  result.testUserId = auth.userId
  const jobs = await api('/job/list')
  selectedJob = jobs.find(job => /java/i.test(job.name)) || jobs[0]
  assert.ok(selectedJob, 'at least one enabled job is required')
  result.job = { id: selectedJob.id, name: selectedJob.name }
  const sid = await start()
  result.sessionId = sid
  sql(`UPDATE interview_message m JOIN interview_session s ON s.id=m.session_id SET m.question_id=0,m.content='请介绍 HashMap 的 put、get 流程以及哈希冲突处理。',m.reference_answer='哈希定位桶，比较键，处理冲突；链表达到阈值且容量足够时树化，必要时扩容。',m.ability_tag='Java' WHERE s.id=${sid} AND s.user_id=${auth.userId} AND m.msg_type='MAIN' AND m.round_no=1;`)
  browser = await chromium.launch({ channel: 'msedge', headless: true, ignoreDefaultArgs: ['--mute-audio'], args: [
    '--no-proxy-server', '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
    '--use-file-for-fake-audio-capture=' + path.join(out, 'microphone.wav') + '%noloop',
  ] })
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, permissions: ['camera', 'microphone'] })
  await context.addInitScript(user => {
    localStorage.setItem('token', user.token); localStorage.setItem('role', 'STUDENT'); localStorage.setItem('userId', String(user.userId))
    const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
    navigator.mediaDevices.getUserMedia = async constraints => {
      if (!constraints.video) return original(constraints)
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480
      return canvas.captureStream(10)
    }
    window.addEventListener('message', event => {
      if (event.data?.type === 'offerpilot.embed.speechEnded') window.debugSpeechEnded(event.data)
    })
  }, { token: auth.token, userId: auth.userId })
  const audioChunks = []
  await context.exposeBinding('debugAudioChunk', (_, base64) => audioChunks.push(Buffer.from(base64, 'base64')))
  await context.exposeBinding('debugSpeechEnded', (_, data) => { result.closingCompletionEvent = data })
  const page = await context.newPage()
  page.on('pageerror', error => result.errors.push(error.message))
  page.on('response', async response => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/') && response.status() >= 400) result.httpFailures.push({ path: url.pathname, status: response.status() })
    if (url.pathname === '/api/speech/transcribe') {
      try { const payload = await response.json(); result.transcription = payload; console.log('ASR response:', payload.code, payload.data?.text || payload.message) } catch {}
    }
  })
  await page.goto(`http://localhost:5173/interview?sessionId=${sid}&jobName=Java&voice=0`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
  await page.locator('.session-tag').waitFor()
  const frame = page.frameLocator('iframe.digital-human-frame')
  await frame.locator('#video').waitFor({ state: 'attached' })
  const embedded = page.frames().find(item => item.url().includes('offerpilot-embed.html'))
  assert.ok(embedded)
  await embedded.waitForFunction(() => document.querySelector('#audio')?.srcObject?.getAudioTracks().length > 0 && pc.connectionState === 'connected', { timeout: 45000 })
  await embedded.evaluate(async () => {
    const audio = document.querySelector('#audio')
    const ctx = new AudioContext(); await ctx.resume()
    const source = ctx.createMediaStreamSource(audio.srcObject)
    const analyser = ctx.createAnalyser(); analyser.fftSize = 2048; source.connect(analyser)
    window.debugAudio = { ctx, analyser, peak: 0, nonSilentSamples: 0, samples: 0 }
    setInterval(() => {
      const data = new Float32Array(analyser.fftSize); analyser.getFloatTimeDomainData(data)
      const peak = Math.max(...data.map(Math.abs))
      debugAudio.peak = Math.max(debugAudio.peak, peak); debugAudio.samples++
      if (peak > 0.001) debugAudio.nonSilentSamples++
    }, 100)
    const recorder = new MediaRecorder(new MediaStream(audio.srcObject.getAudioTracks()), { mimeType: 'audio/webm;codecs=opus' })
    recorder.ondataavailable = async event => {
      if (!event.data.size) return
      const bytes = new Uint8Array(await event.data.arrayBuffer())
      let value = ''; for (let i = 0; i < bytes.length; i += 8192) value += String.fromCharCode(...bytes.subarray(i, i + 8192))
      await window.debugAudioChunk(btoa(value))
    }
    recorder.start(500); window.debugRecorder = recorder
  })
  if (await frame.locator('#unlockBtn').isVisible()) await frame.locator('#unlockBtn').click()
  await page.locator('iframe.digital-human-frame.visible').waitFor({ timeout: 60000 })
  await embedded.waitForFunction(() => debugAudio.nonSilentSamples >= 5, { timeout: 45000 })
  result.phases.main = await embedded.evaluate(() => ({ peak: debugAudio.peak, nonSilentSamples: debugAudio.nonSilentSamples, audioPaused: document.querySelector('#audio').paused, muted: document.querySelector('#audio').muted, volume: document.querySelector('#audio').volume, videoWidth: document.querySelector('#video').videoWidth }))
  console.log('Main question audio:', JSON.stringify(result.phases.main))
  await page.screenshot({ path: path.join(out, 'main.png') })
  await wait(6000)
  await embedded.evaluate(() => { debugAudio.peak = 0; debugAudio.nonSilentSamples = 0 })
  await page.locator('.mic-control').click()
  console.log('Microphone opened:', await page.locator('.mic-status').innerText())
  for (let i = 0; i < 180; i++) {
    if (result.transcription) break
    await wait(250)
  }
  if (result.transcription?.code !== 200) {
    const blocker = result.transcription?.message || 'ASR did not return a transcript'
    result.blockers.push('Real ASR: ' + blocker)
    console.log('Continuing core checks with the known synthetic test transcript; real ASR is blocked:', blocker)
    // Emit exactly the transcript event used by the ASR component; no AI/TTS response is mocked.
    await page.evaluate(text => document.querySelector('.voice-answer').__vueParentComponent.emit('transcript', text),
      '哈希表会先计算键的哈希值定位数组桶，如果有冲突就比较键。键相同则覆盖值，不同则加入链表；链表较长时可以转成红黑树，降低查询开销。')
    result.syntheticTranscriptInjected = true
    await page.getByRole('button', { name: '提交', exact: true }).click()
  }
  await page.locator('.followup-pill').first().waitFor({ timeout: 90000 }).catch(async error => {
    result.microphoneStatus = await page.locator('.voice-answer').innerText()
    await page.screenshot({ path: path.join(out, 'failure.png') })
    console.log('Microphone failure state:', result.microphoneStatus)
    throw error
  })
  result.followup = await page.locator('.conversation-entry').filter({ has: page.locator('.followup-pill') }).first().innerText()
  if (await page.locator('.mic-control.open').isVisible()) await page.locator('.mic-control.open').click()
  await embedded.waitForFunction(() => debugAudio.nonSilentSamples >= 5, { timeout: 45000 })
  result.phases.followup = await embedded.evaluate(() => ({ peak: debugAudio.peak, nonSilentSamples: debugAudio.nonSilentSamples, paused: document.querySelector('#audio').paused, muted: document.querySelector('#audio').muted }))
  result.followupRecords = await api(`/interview/follow-up-records?sessionId=${sid}`)
  assert.equal(result.followupRecords.list[0]?.source, 'AI', 'follow-up must come from the real model')
  if (!result.syntheticTranscriptInjected) assert.ok(result.transcription?.data?.text?.length > 15, 'real ASR must recognize the synthetic microphone input')
  const audioForm = new FormData()
  audioForm.set('file', new Blob([fs.readFileSync(path.join(out, 'candidate.wav'))], { type: 'audio/wav' }), 'candidate.wav')
  const audioUpload = await fetch(`http://127.0.0.1:8080/api/interview/${sid}/answers/1/audio`, { method: 'POST', headers: { Authorization: `Bearer ${auth.token}` }, body: audioForm })
  result.audioUpload = await audioUpload.json()
  assert.equal(result.audioUpload.code, 200)
  const storedAudio = await fetch(`http://127.0.0.1:8080/api/interview/audio/${result.audioUpload.data.audioId}`, { headers: { Authorization: `Bearer ${auth.token}` } })
  assert.equal(storedAudio.status, 200)
  assert.equal((await storedAudio.arrayBuffer()).byteLength, fs.readFileSync(path.join(out, 'candidate.wav')).length)
  result.roundAudioSavedAndReadable = true
  await page.screenshot({ path: path.join(out, 'followup.png') })
  console.log('AI follow-up:', result.followup)
  await wait(5000)
  await embedded.evaluate(() => { debugAudio.peak = 0; debugAudio.nonSilentSamples = 0 })
  const beforeClosing = Date.now()
  await page.getByRole('button', { name: '结束面试', exact: true }).click()
  await page.getByText('好，那我们今天就先聊到这里。谢谢你的分享，辛苦了。', { exact: true }).waitFor()
  await embedded.waitForFunction(() => debugAudio.nonSilentSamples >= 5, { timeout: 18000 })
  result.phases.closing = await embedded.evaluate(() => ({ peak: debugAudio.peak, nonSilentSamples: debugAudio.nonSilentSamples }))
  await page.screenshot({ path: path.join(out, 'closing.png') })
  await page.waitForURL('**/history/session/' + sid, { timeout: 40000 })
  result.closingDurationMs = Date.now() - beforeClosing
  assert.equal(result.closingCompletionEvent?.completed, true, 'closing must finish its own tagged audio before navigation')
  assert.ok(result.closingDurationMs >= 9000, 'the full closing takes over nine seconds to synthesize and play')
  const finished = await api(`/interview/${sid}/resume`)
  assert.equal(finished.status, 'FINISHED')
  assert.equal(finished.messages.filter(item => item.msgType === 'SUMMARY').length, 1)
  result.closingPersisted = true
  await wait(1000)
  fs.writeFileSync(path.join(out, 'received-audio.webm'), Buffer.concat(audioChunks))
  result.receivedAudioBytes = Buffer.concat(audioChunks).length
  await context.close(); context = null
  console.log('Closing audio:', JSON.stringify(result.phases.closing), 'navigation after', result.closingDurationMs, 'ms')
  // Verify actual next endpoint honors the quota with plenty of time still left.
  const quotaSid = await start(300)
  sql(`INSERT INTO interview_message(session_id,question_id,round_no,role,msg_type,content,ability_tag) VALUES (${quotaSid},0,2,'INTERVIEWER','MAIN','测试主问二','Java'),(${quotaSid},0,2,'CANDIDATE','ANSWER','测试回答二','Java'),(${quotaSid},0,3,'INTERVIEWER','MAIN','测试主问三','Java'),(${quotaSid},0,3,'CANDIDATE','ANSWER','测试回答三','Java');`)
  const quota = await api(`/interview/${quotaSid}/next`)
  assert.equal(quota.nextAction, 'FINISHABLE')
  result.quotaEndsEarly = true
  await api(`/interview/${quotaSid}/finish`, {})
  const cutoffSid = await start(300)
  sql(`UPDATE interview_session SET start_time=DATE_SUB(NOW(),INTERVAL 271 SECOND) WHERE id=${cutoffSid} AND user_id=${auth.userId};`)
  const cutoff = await api(`/interview/${cutoffSid}/resume`)
  assert.equal(cutoff.status, 'FINISHED')
  result.cutoffEndsBeforeDeadline = true
  for (let i = 0; i < 45; i++) {
    const report = await api(`/interview/${sid}/report-status`)
    result.reportState = report.state
    if (report.state === 'READY' || report.state === 'FAILED') break
    await wait(2000)
  }
  assert.equal(result.reportState, 'READY')
  assert.deepEqual(result.errors, [])
  result.corePassed = true
  result.passed = result.blockers.length === 0
}

main().catch(error => { result.failure = error.message; console.error(error); process.exitCode = 1 }).finally(async () => {
  await context?.close().catch(() => {}); await browser?.close().catch(() => {})
  if (auth) {
    let sessionsRemoved = true
    for (const sid of sessions) {
      try { await api(`/interview/${sid}`, null, 'DELETE') } catch (error) { sessionsRemoved = false; result.errors.push('test session cleanup: ' + error.message) }
    }
    try {
      if (sessionsRemoved) {
        sql(`DELETE FROM account_token_version WHERE user_id=${auth.userId}; DELETE FROM sys_user WHERE id=${auth.userId} AND username='${auth.username}';`)
        result.testAccountRemoved = true
      }
    } catch (error) { result.errors.push('test account cleanup: ' + error.message) }
  }
  result.completedAt = new Date().toISOString()
  fs.writeFileSync(path.join(out, 'result.json'), JSON.stringify(result, null, 2))
  console.log('Debug result:', JSON.stringify({ corePassed: result.corePassed || false, passed: result.passed || false, blockers: result.blockers, failure: result.failure, errors: result.errors, httpFailures: result.httpFailures }))
})
