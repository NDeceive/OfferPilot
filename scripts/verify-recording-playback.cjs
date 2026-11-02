// Actual desktop/mobile report routes with synthetic WAV and isolated API responses.
const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const base = process.env.INTERVIEW_QA_URL || 'http://127.0.0.1:5187'
const out = path.resolve(__dirname, '../artifacts/recording-playback')
fs.mkdirSync(out, { recursive: true })
const wav = Buffer.alloc(44 + 16000 * 2)
wav.write('RIFF', 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8)
wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22)
wav.writeUInt32LE(16000, 24); wav.writeUInt32LE(32000, 28); wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34)
wav.write('data', 36); wav.writeUInt32LE(wav.length - 44, 40)
for (let i = 0; i < 16000; i++) wav.writeInt16LE(Math.round(6000 * Math.sin(2 * Math.PI * 180 * i / 16000)), 44 + i * 2)

async function check(browser, width, mode = 'normal') {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, acceptDownloads: true })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'recording-isolated-check'); localStorage.setItem('role', 'STUDENT')
    localStorage.setItem('userId', '999'); localStorage.setItem('offerpilot.theme', 'light')
    window.revokedAudioUrls = []
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.revokeObjectURL = url => { window.revokedAudioUrls.push(url); revoke(url) }
  })
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  let listRequests = 0, binaryRequests = 0
  await page.route('**/api/**', async route => {
    const request = route.request(), uri = new URL(request.url()).pathname
    if (!uri.startsWith('/api/')) return route.continue()
    let data = {}
    if (uri === '/api/report/7001') data = { reportId: 7001, sessionId: 211, jobId: 1, jobName: 'Java后端开发', totalScore: 75,
      summary: '隔离回归检查。', strengths: [], weaknesses: [], suggestions: [], dimensions: [], moduleScores: [],
      actualDurationSeconds: 120, startTime: '2026-11-02T10:00:00', voice: { status: 'FAILED', rounds: [] } }
    else if (uri === '/api/interview/211/recordings') {
      listRequests++
      if (mode === 'list-error' && listRequests === 1) return route.fulfill({ json: { code: 500, message: '录音列表暂时不可读取，请重试。' } })
      data = mode === 'empty' ? [] : [
        { audioId: 7007, roundNo: 1, durationMs: 1000, available: true },
        { audioId: 7008, roundNo: 2, durationMs: 1000, available: true },
        { audioId: 7009, roundNo: 3, durationMs: 1000, available: false },
        { audioId: 7010, roundNo: 4, durationMs: 1000, available: true },
      ]
    } else if (uri.startsWith('/api/interview/audio/')) {
      binaryRequests++
      assert.equal(request.headers().authorization, 'Bearer recording-isolated-check', 'audio fetch must retain authentication')
      if (uri.endsWith('/7010')) return route.fulfill({ json: { code: 500, message: '录音文件已丢失' } })
      return route.fulfill({ contentType: 'audio/wav', body: wav })
    } else if (uri.endsWith('/messages') || uri.endsWith('/feedback') || uri.endsWith('/job/list')) data = []
    else if (uri.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '隔离检查' }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto(base + '/history/7001')
  const panel = page.getByRole('region', { name: '面试录音', exact: true })
  await panel.getByRole('heading', { name: '面试录音', exact: true }).waitFor()
  if (mode === 'empty') {
    await panel.getByText(/本场暂无已保存录音/).waitFor()
    assert.equal(binaryRequests, 0)
  } else {
    if (mode === 'list-error') {
      await panel.getByRole('alert').waitFor()
      await panel.getByRole('button', { name: '重新加载录音', exact: true }).click()
    }
    await panel.getByText('第 1 题录音', { exact: true }).waitFor()
    assert.equal(binaryRequests, 0, 'opening a report must not eagerly download all recordings')
    const rows = panel.locator('li')
    await rows.nth(0).getByRole('button', { name: '播放第1题录音', exact: true }).click()
    await page.waitForFunction(() => { const audio = document.querySelector('.interview-recordings audio'); return audio && audio.readyState >= 1 && audio.currentTime > 0 })
    assert.equal(await panel.locator('audio').first().evaluate(audio => Math.round(audio.duration)), 1, 'native player must decode the WAV')
    const downloadEvent = page.waitForEvent('download')
    await rows.nth(0).getByRole('button', { name: '下载第1题录音 WAV', exact: true }).click()
    const download = await downloadEvent
    assert.equal(download.suggestedFilename(), '面试-211-第1题.wav')
    await download.saveAs(path.join(out, 'synthetic-download-' + width + '.wav'))
    assert.deepEqual(fs.readFileSync(path.join(out, 'synthetic-download-' + width + '.wav')), wav)
    await rows.nth(1).getByRole('button', { name: '播放第2题录音', exact: true }).click()
    await page.waitForFunction(() => { const audio = [...document.querySelectorAll('.interview-recordings audio')]; return audio.length === 2 && audio[0].paused && !audio[1].paused })
    assert.equal(await rows.nth(2).getByRole('button', { name: '播放第3题录音', exact: true }).isDisabled(), true)
    await rows.nth(3).getByRole('button', { name: '播放第4题录音', exact: true }).click()
    await rows.nth(3).getByRole('alert').filter({ hasText: '录音文件已丢失' }).waitFor()
    assert.equal(await panel.locator('audio').count(), 2, 'JSON error responses must not become fake audio blobs')
    await panel.locator('audio').evaluateAll(elements => elements.forEach(audio => audio.pause()))
  }
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  assert.deepEqual(errors, [])
  await page.screenshot({ path: path.join(out, 'report-' + width + '-' + mode + '.png'), fullPage: true })
  await page.evaluate(() => document.querySelector('#app').__vue_app__.unmount())
  if (mode !== 'empty') assert.ok(await page.evaluate(() => window.revokedAudioUrls.length >= 2), 'leaving report must release audio object URLs')
  await context.close()
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--no-proxy-server', '--autoplay-policy=no-user-gesture-required'] })
  try {
    await check(browser, 1440)
    await check(browser, 390)
    await check(browser, 390, 'empty')
    await check(browser, 1440, 'list-error')
    console.log('Passed: actual desktop/mobile report routes; playback independent of analysis, authenticated WAV decode/playback, download bytes, one active player, missing/empty/error/retry states and URL cleanup.')
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
