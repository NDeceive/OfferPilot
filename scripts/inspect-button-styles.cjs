// Verify computed button styles and consent/step events with a fake camera; no real interview starts.
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const root = path.resolve(__dirname, '..'), frontend = path.join(root, '前端')
const out = path.join(root, 'artifacts/button-style-inspection')
fs.mkdirSync(out, { recursive: true })
;(async () => {
  let browser, server
  try {
    server = cp.spawn(process.execPath, [path.join(frontend, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5187', '--strictPort'], { cwd: frontend, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Vite did not start')), 10000)
      server.stdout.on('data', chunk => { if (chunk.toString().includes('Local:')) { clearTimeout(timer); resolve() } })
      server.once('exit', code => { clearTimeout(timer); reject(new Error('Vite exited: ' + code)) })
    })
    const { MOCK_JOBS } = await import(pathToFileURL(path.join(frontend, 'src/utils/demoData.js')))
    browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--no-proxy-server'] })
    const results = []
    for (const theme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce', colorScheme: theme })
    await page.addInitScript(mode => {
      localStorage.setItem('token', 'style-inspection'); localStorage.setItem('role', 'STUDENT'); localStorage.setItem('app-theme', mode)
      window.fakeCameraCalls = 0
      navigator.mediaDevices.getUserMedia = async () => {
        window.fakeCameraCalls++
        return document.createElement('canvas').captureStream(10)
      }
    }, theme)
    await page.route('**/api/**', route => {
      const url = new URL(route.request().url()).pathname
      if (!url.startsWith('/api/')) return route.continue()
      const data = url === '/api/job/list' ? MOCK_JOBS : url.endsWith('/me') ? { id: 999, nickname: '界面检查', role: 'STUDENT' } : url.includes('versions') || url === '/api/modules' ? [] : null
      return route.fulfill({ json: { code: 200, data } })
    })
    await page.goto('http://127.0.0.1:5187/jobs?step=2&job=BE-JAVA', { waitUntil: 'networkidle' })
    const next = page.getByRole('button', { name: '下一步：训练目标 →', exact: true })
    await next.waitFor()
    const inspect = element => {
      const css = getComputedStyle(element)
      return { text: element.textContent.trim(), classes: element.className, disabled: element.disabled, background: css.backgroundColor, color: css.color, fontSize: css.fontSize, fontWeight: css.fontWeight, padding: css.padding, radius: css.borderRadius, minHeight: css.minHeight }
    }
    const result = { theme, analysisNext: await next.evaluate(inspect) }
    const reference = await page.evaluate(() => {
      const button = document.createElement('button'); button.className = 'btn btn--primary'; document.body.append(button)
      const css = getComputedStyle(button)
      const values = { background: css.backgroundColor, color: css.color, fontSize: css.fontSize, fontWeight: css.fontWeight, padding: css.padding, radius: css.borderRadius, minHeight: css.minHeight }
      button.remove()
      const hover = document.createElement('span'); hover.style.background = 'var(--accent-600)'; document.body.append(hover)
      values.hover = getComputedStyle(hover).backgroundColor; hover.remove()
      return values
    })
    const check = value => {
      assert.equal(value.disabled, false)
      for (const property of ['background', 'color', 'fontSize', 'fontWeight', 'padding', 'radius', 'minHeight']) assert.equal(value[property], reference[property], `${theme} ${value.text}: ${property}`)
    }
    check(result.analysisNext)
    await next.hover(); await page.waitForTimeout(180)
    assert.equal((await next.evaluate(inspect)).background, reference.hover)
    const openConsent = () => page.evaluate(() => {
      window.consentResult = null
      import('/src/utils/interviewCamera.js').then(module => module.prepareInterviewCamera()
        .then(() => { window.consentResult = 'allowed' }).catch(error => { window.consentResult = error.code }))
    })
    await page.mouse.move(0, 0)
    await openConsent()
    const allow = page.getByRole('button', { name: '允许并进入面试', exact: true })
    await allow.waitFor()
    result.cameraAllow = await allow.evaluate(inspect)
    check(result.cameraAllow)
    await allow.hover()
    await page.waitForTimeout(180)
    result.cameraAllowHover = await allow.evaluate(inspect)
    assert.equal(result.cameraAllowHover.background, reference.hover)
    await page.screenshot({ path: path.join(out, `camera-dialog-${theme}-fixed.png`) })
    await page.getByRole('button', { name: '拒绝并返回', exact: true }).click()
    await page.waitForFunction(() => window.consentResult === 'CAMERA_CONSENT_DECLINED')
    assert.equal(await page.evaluate(() => window.fakeCameraCalls), 0)
    await page.mouse.move(0, 0)
    await page.screenshot({ path: path.join(out, `analysis-next-${theme}-fixed.png`), fullPage: true })
    await next.click()
    result.sharedPrimary = await page.getByRole('button', { name: '确认训练目标', exact: true }).evaluate(inspect)
    await page.getByRole('heading', { name: '选择训练目标', exact: true }).waitFor()
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 900 })
      await page.mouse.move(0, 0); await openConsent(); await allow.waitFor()
      check(await allow.evaluate(inspect))
      await page.keyboard.press('Tab')
      await allow.focus()
      assert.notEqual(await allow.evaluate(element => getComputedStyle(element).outlineStyle), 'none')
      if (width === 390) await page.screenshot({ path: path.join(out, `camera-mobile-${theme}-fixed.png`) })
      await page.keyboard.press('Enter')
      await page.waitForFunction(() => window.consentResult === 'allowed')
      await page.evaluate(() => import('/src/utils/interviewCamera.js').then(module => module.releaseInterviewCamera()))
    }
    result.refusalAndKeyboardAllow = true
    results.push(result)
    await page.close()
    }
    fs.writeFileSync(path.join(out, 'verified-fixed.json'), JSON.stringify(results, null, 2))
    console.log(JSON.stringify(results, null, 2))
  } finally { await browser?.close(); server?.kill() }
})().catch(error => { console.error(error); process.exitCode = 1 })
