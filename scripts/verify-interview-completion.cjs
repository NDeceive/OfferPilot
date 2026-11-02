// Run: node scripts/verify-interview-completion.cjs
// Isolated API fixtures; never submits answers or finishes a real user's interview.
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')
const path = require('node:path')
const { spawn } = require('node:child_process')
const base = process.env.INTERVIEW_QA_URL || 'http://127.0.0.1:5186'
const closing = '好，那我们今天就先聊到这里。谢谢你的分享，辛苦了。'

async function verify(browser, mode) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, permissions: ['camera', 'microphone'] })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'isolated-test'); localStorage.setItem('role', 'STUDENT')
    navigator.mediaDevices.getUserMedia = async () => {
      const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480
      return canvas.captureStream(10)
    }
  })
  const page = await context.newPage()
  page.on('pageerror', error => console.error('Page error:', error.message))
  let finishes = 0
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (!path.startsWith('/api/')) return route.continue()
    let data = {}
    if (path.endsWith('/start')) data = { sessionId: 987654, jobName: '测试岗位', durationSeconds: 300, remainingSeconds: ['deadline', 'pending'].includes(mode) ? 31 : 300, question: { id: 1, roundNo: 1, content: '请介绍一下你的技术经验。' } }
    else if (path.endsWith('/answer')) {
      if (mode === 'pending') { await new Promise(resolve => setTimeout(resolve, 2000)); data = { nextAction: 'FOLLOWUP', followupQuestion: '这是一条迟到的追问。' } }
      else data = { nextAction: 'NEXT' }
    }
    else if (path.endsWith('/next')) data = { nextAction: 'FINISHABLE' }
    else if (path.endsWith('/finish')) { finishes++; data = 987654 }
    else if (path.endsWith('/expression/context')) data = { serverTime: Date.now() }
    else if (path.endsWith('/report-status')) data = { state: 'GENERATING', ready: false }
    else if (path.endsWith('/resume')) data = { status: 'FINISHED', messages: [] }
    else if (path.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '测试用户' }
    await route.fulfill({ json: { code: 200, data } })
  })
  // Make the avatar unavailable: the closing must still be shown before navigation.
  await page.route('**/digital-human/**', route => route.fulfill({ contentType: 'text/html', body: '<html></html>' }))
  await page.goto(`${base}/interview?jobId=1&durationSeconds=300&voice=0`)
  await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
  await page.locator('.session-tag').filter({ hasText: '测试岗位' }).waitFor({ timeout: 10000 }).catch(async error => {
    console.error(page.url(), await page.locator('body').innerText()); throw error
  })
  if (mode === 'manual') await page.getByRole('button', { name: '结束面试', exact: true }).click()
  if (['quota', 'pending'].includes(mode)) await page.getByRole('button', { name: '跳过', exact: true }).click()
  await page.getByText(closing, { exact: true }).waitFor()
  assert.equal(finishes, 1)
  assert.ok(page.url().includes('/interview?'), 'closing must be visible on the interview page')
  await page.waitForTimeout(1000)
  assert.ok(page.url().includes('/interview?'), 'must not jump straight to the report')
  assert.equal(await page.getByText('这是一条迟到的追问。', { exact: true }).count(), 0)
  await page.waitForURL('**/history/session/987654', { timeout: 10000 })
  assert.equal(finishes, 1)
  await context.close()
  console.log(`${mode}: closing shown before report; one finish request`)
}

;(async () => {
  let browser, server
  try {
    if (!process.env.INTERVIEW_QA_URL) {
      const frontend = path.resolve(__dirname, '../前端')
      server = spawn(process.execPath, [path.join(frontend, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5186', '--strictPort'], { cwd: frontend, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Vite startup timeout')), 10000)
        server.stdout.on('data', chunk => { if (chunk.toString().includes('Local:')) { clearTimeout(timer); resolve() } })
        server.once('exit', code => { clearTimeout(timer); reject(new Error(`Vite exited: ${code}`)) })
      })
    }
    browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] })
    for (const mode of ['manual', 'deadline', 'quota', 'pending']) await verify(browser, mode)
  } finally { await browser?.close(); server?.kill() }
})().catch(error => { console.error(error); process.exitCode = 1 })
