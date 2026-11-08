// Browser verification against actual routes/components with explicitly isolated API fixtures.
// Does not read user recordings or create real interviews.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict')
// Uses installed Playwright and its bundled Chromium by default. Optional overrides:
// PLAYWRIGHT_PATH, INTERVIEW_BROWSER_PATH, INTERVIEW_QA_URL, INTERVIEW_QA_OUT.
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright')
const base = process.env.INTERVIEW_QA_URL || 'http://127.0.0.1:5187'
const out = path.resolve(__dirname, process.env.INTERVIEW_QA_OUT || '../artifacts/interview-minutes')
fs.mkdirSync(out, { recursive: true })
const points = { text: '本次面试围绕接口性能排查展开，小林介绍了通过链路日志定位问题、结合 SQL 执行计划分析的处理思路，已记录的回答涵盖了主要排查步骤。' }
const fixtureEntries = [
  { id: 11, role: 'SYSTEM', entryType: 'EVENT', text: '纪要已开启', submittedAt: '2026-11-04T10:00:00' },
  { id: 12, role: 'INTERVIEWER', roundNo: 1, entryType: 'MAIN', text: '你是如何排查慢接口的？', submittedAt: '2026-11-04T10:00:01' },
  { id: 13, role: 'CANDIDATE', roundNo: 1, entryType: 'ANSWER', text: '先查看链路日志，再看 SQL 执行计划。这里是正式提交文本。', submittedAt: '2026-11-04T10:01:00' },
]
const reportEntries = [...fixtureEntries,
  { id: 14, role: 'INTERVIEWER', entryType: 'FOLLOWUP', text: '如果查询已经命中索引，接口依然很慢，你会怎样继续定位？请结合一次实际排查说明。' },
  { id: 15, role: 'CANDIDATE', entryType: 'ANSWER', text: '我会继续检查扫描行数、回表和排序开销，再区分数据库执行时间与连接等待时间。\n之前排查过一个分页接口，除了分析 SQL，也核对了连接池等待和下游调用耗时，最后通过压测对比修改前后的表现。' },
  { id: 16, role: 'INTERVIEWER', entryType: 'FOLLOWUP', text: '你会如何验证优化结果，并避免对其他查询造成影响？' },
  { id: 17, role: 'CANDIDATE', entryType: 'ANSWER', text: '使用相同的数据规模和并发条件进行压测，记录响应时间与错误率，同时检查相关接口的执行计划。上线后持续观察慢查询日志，保留回滚方案。' },
]
async function check(browser, width, mode = 'ready') {
  const context = await browser.newContext({ viewport: { width, height: 900 }, acceptDownloads: true, reducedMotion: width < 768 ? 'reduce' : 'no-preference', permissions: ['clipboard-read', 'clipboard-write'] })
  await context.addInitScript(() => { localStorage.setItem('token', 'minutes-isolated-check'); localStorage.setItem('role', 'STUDENT'); localStorage.setItem('userId', '999'); localStorage.setItem('offerpilot.theme', 'light') })
  const page = await context.newPage(), errors = []
  page.on('pageerror', e => errors.push(e.message))
  let enabled = false, version = 0, controls = 0, generated = 0, deleted = false
  await page.route('**/api/**', async route => {
    const req = route.request(), uri = new URL(req.url()).pathname, method = req.method()
    if (!uri.startsWith('/api/')) return route.continue()
    let data = {}
    if (uri.includes('/minutes') && method !== 'OPTIONS') assert.equal(req.headers().authorization, 'Bearer minutes-isolated-check')
    if (uri === '/api/interview/211/minutes') {
      if (method === 'DELETE') deleted = true
      data = { sessionId: 211, candidateName: '小林', enabled, controlVersion: version, lifecycle: deleted ? 'DELETED' : mode === 'active' ? 'ACTIVE' : 'FINALIZED', coverage: 'PARTIAL', recordedEntryCount: deleted ? 0 : 2, sourceStatus: mode === 'active' ? 'ONGOING' : 'FINISHED', canControl: mode === 'active' && !deleted, summaryState: mode === 'failed' && !generated ? 'FAILED' : 'READY', summaryError: 'NOT_CONFIGURED', hasSummary: !deleted && mode !== 'active', waitingForQuestion: enabled, windows: [] }
    } else if (uri.endsWith('/minutes/control')) { const body = req.postDataJSON(); assert.equal(body.expectedVersion, version); assert.ok(body.requestId); enabled = body.enabled; version++; controls++; data = {} }
    else if (uri.endsWith('/minutes/entries')) data = deleted ? [] : (new URL(req.url()).searchParams.get('afterSequence') === '0' ? reportEntries : [])
    else if (uri.endsWith('/minutes/summary')) data = deleted ? {} : { outputJson: JSON.stringify(points) }
    else if (uri.endsWith('/minutes/summary-jobs')) { generated++; data = { id: 44, state: 'QUEUED' } }
    else if (uri.endsWith('/minutes/export')) return route.fulfill({ contentType: 'text/plain; charset=utf-8', body: '隔离验收导出内容\n正式问答与记录范围' })
    else if (uri === '/api/report/7001') data = { reportId: 7001, sessionId: 211, jobId: 1, jobName: 'Java 后端开发', totalScore: 75, summary: '隔离浏览器验收', dimensions: [], moduleScores: [], strengths: [], weaknesses: [], suggestions: [] }
    else if (uri.endsWith('/report-status')) data = { state: 'FAILED', failureReason: '隔离验收：报告生成失败' }
    else if (uri.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '隔离验收' }
    else if (uri.endsWith('/messages') || uri.endsWith('/feedback') || uri.endsWith('/recordings') || uri.endsWith('/job/list')) data = []
    await route.fulfill({ json: { code: 200, data } })
  })
  const routePath = mode === 'report-failed' ? '/history/session/211?tab=minutes' : mode === 'active' ? '/history/7001?tab=minutes' : '/history/7001?tab=summary'
  await page.goto(base + routePath)
  if (!['report-failed', 'active'].includes(mode)) await page.locator('.minutes-summary-text').waitFor()
  else await page.getByText('这里是正式提交文本', { exact: false }).waitFor()
  if (mode === 'ready') {
    assert.equal(await page.locator('.minutes-summary-text').count(), 1, 'summary is a single paragraph')
    assert.equal(await page.locator('.minutes-content h3').count(), 0, 'summary has no category sections')
    const more = page.locator('.minutes-content details').last()
    await more.locator('summary').click()
    await page.waitForFunction(() => { const r = document.querySelector('.minutes-content details[open]>div')?.getBoundingClientRect(); return r && r.left >= 15 && r.right <= innerWidth - 15 })
    await more.locator('summary').click()
    await page.screenshot({ path: path.join(out, `${width}-ready-summary.png`), fullPage: true })
  }
  if (mode === 'failed') {
    await page.getByText('已保留上一份总结。').waitFor()
    await page.getByRole('button', { name: '重新生成总结', exact: true }).click()
    await page.getByText('已保留上一份总结。').waitFor({ state: 'hidden' })
    assert.equal(generated, 1)
  }
  if (!['report-failed', 'active'].includes(mode)) {
    await page.getByRole('button', { name: '面试记录', exact: true }).click()
    await page.locator('.minutes-summary-text').waitFor({ state: 'hidden' })
    await page.waitForFunction(() => document.querySelectorAll('.minutes-content').length === 1)
    await page.locator('[data-minutes-entry="13"]').waitFor()
    await page.locator('.minutes-entry').getByText('小林', { exact: true }).first().waitFor()
  }
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, 'no horizontal overflow')
  await page.screenshot({ path: path.join(out, `${width}-${mode}-record.png`), fullPage: true })
  assert.equal(await page.locator('.minutes-orb__button').count(), 0, 'report uses tabs without a duplicate floating entry')
  const panel = page.locator('.minutes-content').last()
  assert.ok((await panel.boundingBox()).width <= 960, 'report reading width is bounded')
  for (const control of await panel.locator('.minutes-actions > button, .minutes-actions > details > summary').all()) {
    assert.ok((await control.boundingBox()).height >= 44, 'toolbar supports touch targets')
  }
  assert.equal(await panel.locator('time').count(), 0, 'report dialogue has no per-message time')
  assert.equal(await panel.getByText('纪要已开启', { exact: true }).count(), 0, 'report hides control events')
  await panel.locator('summary').filter({ hasText: /^导出$/ }).click()
  const downloadPromise = page.waitForEvent('download')
  await panel.getByRole('button', { name: '下载 TXT', exact: true }).click()
  const download = await downloadPromise
  assert.ok(download.suggestedFilename().endsWith('.txt'))
  await page.screenshot({ path: path.join(out, `${width}-${mode}-panel.png`), fullPage: true })
  assert.equal(controls, 0, 'report navigation never toggles recording')
  assert.deepEqual(errors, [], 'no browser runtime errors')
  await context.close()
  return { width, mode, controls, generated, errors }
}
async function checkInterview(browser, width) {
  const height = width >= 2000 ? 1400 : 900
  const context = await browser.newContext({ viewport: { width, height }, permissions: ['camera', 'microphone'], reducedMotion: 'reduce' })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'minutes-interview-check'); localStorage.setItem('role', 'STUDENT'); localStorage.setItem('userId', '999'); localStorage.setItem('offerpilot.theme', 'light')
    window.SpeechRecognition = undefined; window.webkitSpeechRecognition = undefined
    // An explicit synthetic camera source: no dependency on Windows camera privacy.
    window.minutesSyntheticMedia = []
    navigator.mediaDevices.getUserMedia = async constraints => {
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480
      const ctx = canvas.getContext('2d'); ctx.fillStyle = '#eaf5ee'; ctx.fillRect(0, 0, 640, 480)
      window.minutesSyntheticMedia.push(canvas)
      if (constraints.video) return canvas.captureStream(1)
      const audio = new AudioContext(), destination = audio.createMediaStreamDestination()
      window.minutesSyntheticMedia.push(audio)
      return destination.stream
    }
  })
  const page = await context.newPage(), errors = []; let enabled = false, controls = 0
  page.on('pageerror', e => errors.push(e.message))
  // Use the shipped embed stylesheet and a local video, without starting WebRTC.
  // This checks both the embed's own layout and the parent's same-origin override.
  const embed = fs.readFileSync(path.resolve(__dirname, '../数字人/web/offerpilot-embed.html'), 'utf8')
  const style = embed.match(/<style>([\s\S]*?)<\/style>/)[1]
  await page.route('**/digital-human/offerpilot-embed.html*', route => route.fulfill({ contentType: 'text/html', body: `<!doctype html><style>${style}</style><div class="stage"><video id="video" autoplay loop muted playsinline src="/assets/interview-avatar/interviewer-v3.mp4"></video></div><script>video.onloadeddata=()=>{parent.postMessage({type:'offerpilot.embed.ready'},location.origin);parent.postMessage({type:'offerpilot.embed.videoReady'},location.origin)}</script>` }))
  await page.route('**/api/**', async route => {
    const req = route.request(), uri = new URL(req.url()).pathname
    if (!uri.startsWith('/api/')) return route.continue()
    let data = {}
    if (uri.endsWith('/resume')) data = { sessionId: 211, status: 'ONGOING', durationSeconds: 600, remainingSeconds: 540, paused: false, messages: [{ id: 41, questionId: -1, roundNo: 1, role: 'INTERVIEWER', msgType: 'MAIN', content: '请介绍一下你最近完成的项目。' }], currentQuestion: { id: 41, questionId: -1, roundNo: 1, content: '请介绍一下你最近完成的项目。' } }
    else if (uri.endsWith('/minutes/control')) { enabled = req.postDataJSON().enabled; controls++ }
    else if (uri.endsWith('/minutes/entries')) data = new URL(req.url()).searchParams.get('afterSequence') === '0' ? fixtureEntries : []
    else if (uri.endsWith('/minutes')) data = { sessionId: 211, candidateName: '小林', lifecycle: 'ACTIVE', coverage: 'PARTIAL', enabled, controlVersion: controls, waitingForQuestion: enabled, canControl: true, recordedEntryCount: 0, summaryState: 'NONE' }
    else if (uri.endsWith('/expression-context')) data = { serverTime: Date.now(), startTime: Date.now(), enabled: false }
    else if (uri.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '隔离验收' }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto(base + '/interview?sessionId=211&voice=0')
  await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
  const orb = page.locator('.minutes-orb__button')
  try { await orb.waitFor({ timeout: 10000 }) }
  catch (e) { await page.screenshot({ path: path.join(out, `${width}-interview-debug.png`), fullPage: true }); console.error(JSON.stringify({ url: page.url(), errors, body: (await page.locator('body').innerText()).slice(0, 2000) })); throw e }
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
  assert.equal(await page.locator('.topbar-right').getByRole('button', { name: '纪要', exact: true }).count(), 0, 'duplicate topbar button is removed')
  const frame = page.frameLocator('.digital-human-frame'), video = frame.locator('#video')
  await video.waitFor({ state: 'attached' })
  await page.waitForFunction(() => { const v = document.querySelector('.digital-human-frame')?.contentDocument?.querySelector('#video'); return v?.videoWidth > 0 })
  await page.locator('.waiting-video').evaluate(v => v.dispatchEvent(new Event('ended')))
  await page.locator('.opening-video').evaluate(v => { v.dispatchEvent(new Event('playing')); v.dispatchEvent(new Event('ended')) })
  await page.waitForFunction(() => document.querySelector('.digital-human-frame')?.classList.contains('visible'))
  const videoLayout = await video.evaluate(v => {
    const r = v.getBoundingClientRect(), fit = getComputedStyle(v).objectFit
    const scale = Math.max(r.width / v.videoWidth, r.height / v.videoHeight)
    return { fit, width: r.width, height: r.height, mediaWidth: v.videoWidth * scale, mediaHeight: v.videoHeight * scale }
  })
  assert.equal(videoLayout.fit, 'cover')
  assert.ok(videoLayout.mediaWidth >= videoLayout.width - .1 && videoLayout.mediaHeight >= videoLayout.height - .1, 'video covers both dimensions without bars or stretching')
  await video.evaluate(() => document.getElementById('offerpilot-layout-override')?.remove())
  assert.equal(await video.evaluate(v => getComputedStyle(v).objectFit), 'cover', 'embed also fills correctly without same-origin injection')
  await page.screenshot({ path: path.join(out, `${width}-interview.png`), fullPage: true })
  // Cross the iframe with pointer capture and drop in the middle of the screen.
  const box = await orb.boundingBox(), targetX = width * .4, targetY = height * .4
  await page.mouse.move(box.x + 24, box.y + 24); await page.mouse.down(); await page.mouse.move(targetX + 24, targetY + 24, { steps: 16 }); await page.mouse.up()
  await page.waitForFunction(({ x, y }) => { const r = document.querySelector('.minutes-orb__button').getBoundingClientRect(); return Math.abs(r.x - x) < 2 && Math.abs(r.y - y) < 2 }, { x: targetX, y: targetY })
  const moved = await orb.boundingBox()
  assert.ok(Math.abs(moved.x - targetX) < 2 && Math.abs(moved.y - targetY) < 2, 'orb freely drags across the iframe')
  assert.equal(await page.locator('dialog[open]').count(), 0)
  assert.equal(await orb.evaluate(e => document.elementFromPoint(e.getBoundingClientRect().x + 24, e.getBoundingClientRect().y + 24)?.closest('.minutes-orb__button') === e), true, 'orb stays above iframe')
  await orb.click()
  const dialog = page.locator('dialog[open]'); await dialog.getByRole('switch').check()
  await page.waitForFunction(() => document.querySelector('.minutes-orb__label')?.textContent === '等待下一问记录')
  await dialog.getByText('先查看链路日志，再看 SQL 执行计划。这里是正式提交文本。').waitFor()
  assert.equal(await dialog.getByRole('navigation').count(), 0, 'live panel has no record/AI tabs')
  assert.equal(await dialog.getByRole('button', { name: /复制|下载|生成|删除|默认/ }).count(), 0, 'exports and management belong to report only')
  assert.equal(await dialog.getByText('纪要已开启', { exact: true }).count(), 0, 'system events are hidden in the live dialogue')
  assert.equal(await dialog.locator('time').count(), 0, 'live dialogue has no timestamps')
  assert.equal(await dialog.locator('.minutes-entry').count(), 2, 'live panel contains only both speakers')
  await page.screenshot({ path: path.join(out, `${width}-interview-conversation.png`), fullPage: true })
  await page.keyboard.press('Escape')
  assert.equal(enabled, true); assert.equal(controls, 1)
  await page.screenshot({ path: path.join(out, `${width}-interview-enabled.png`), fullPage: true })
  assert.deepEqual(errors, [])
  await context.close()
  return { width, mode: 'actual-interview-route', controls, videoLayout, errors }
}
async function checkMobileRedirects(browser, width) {
  const context = await browser.newContext({ viewport: { width, height: 900 } })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'minutes-mobile-check'); localStorage.setItem('role', 'STUDENT')
    window.mediaRequests = 0
    navigator.mediaDevices.getUserMedia = async () => { window.mediaRequests++; throw new Error('Mobile interview must not request media') }
  })
  const page = await context.newPage(), errors = [], interviewRequests = []
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/api/**', route => {
    const uri = new URL(route.request().url()).pathname
    if (!uri.startsWith('/api/')) return route.continue()
    if (uri.startsWith('/api/interview/')) interviewRequests.push(uri)
    return route.fulfill({ json: { code: 200, data: uri.endsWith('/me') ? { id: 999, role: 'STUDENT', nickname: '隔离验收' } : {} } })
  })
  for (const routePath of ['/interview/ai', '/jobs', '/resume', '/resume/builder', '/interview?sessionId=211', '/history', '/history/7001?tab=summary', '/history/session/211?tab=minutes', '/report', '/followup-records']) {
    await page.goto(base + routePath)
    await page.waitForURL(base + '/home?notice=desktop-only')
    assert.equal(await page.evaluate(() => window.mediaRequests), 0)
    assert.equal(await page.locator('.minutes-orb__button, .minutes-content, dialog[open]').count(), 0)
  }
  assert.deepEqual(interviewRequests, [], 'mobile redirects never start interviews or load minutes')
  assert.deepEqual(errors, [])
  await page.screenshot({ path: path.join(out, `${width}-mobile-redirect.png`), fullPage: true })
  await context.close()
  return { width, mode: 'mobile-desktop-only-redirects', errors }
}
;(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.INTERVIEW_BROWSER_PATH || process.env.MINUTES_BROWSER_PATH, args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
  try {
    const results = []
    for (const width of [1440, 768]) results.push(await check(browser, width))
    results.push(await check(browser, 1440, 'failed'))
    results.push(await check(browser, 1440, 'active'))
    results.push(await check(browser, 1440, 'report-failed'))
    for (const width of [2560, 1440]) results.push(await checkInterview(browser, width))
    for (const width of [360, 390, 430]) results.push(await checkMobileRedirects(browser, width))
    fs.writeFileSync(path.join(out, 'browser-results.json'), JSON.stringify({ evidence: 'isolated API fixtures on actual application routes; no real ASR/LLM recording', results }, null, 2))
    console.log(JSON.stringify(results))
  } finally { await browser.close() }
})().catch(e => { console.error(e); process.exit(1) })
