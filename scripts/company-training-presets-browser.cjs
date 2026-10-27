// Isolated UI verification: API responses are fixtures; no user data is changed.
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')
const root = path.resolve(__dirname, '..')
const out = path.join(root, 'output/company-training-presets')
fs.mkdirSync(out, { recursive: true })

;(async () => {
  const { companyTrainingPresets: presets } = await import(pathToFileURL(path.join(root, '前端/src/utils/companyTrainingPresets.js')))
  const sql = fs.readFileSync(path.join(root, '后端/src/main/resources/db/migration_scoring_v1.sql'), 'utf8')
  const modules = [...sql.matchAll(/\('([^']+)',\s*'([^']+)',\s*'([^']+)',\s*(\d+)\)/g)]
    .map((m, i) => ({ id: i + 1, code: m[1], name: m[2], description: m[3] }))
  assert.equal(modules.length, 10)
  assert.deepEqual(presets.map(p => p.name), ['字节跳动', '美团', '华为', '阿里巴巴', '腾讯', '京东', '小米'])
  for (const preset of presets) {
    assert.equal(new Set(preset.codes).size, 5)
    assert(preset.codes.every(code => modules.some(m => m.code === code)))
  }
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'company-preset-ui-test')
    localStorage.setItem('role', 'STUDENT')
    localStorage.setItem('userId', '1')
    localStorage.setItem('nickname', '界面验收')
  })
  let availableModules = modules
  let submitted
  await context.route('**/api/**', route => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    let data = null
    if (pathname.endsWith('/modules')) data = availableModules
    else if (pathname.endsWith('/job/list')) data = [{ id: 1, code: 'BE-JAVA', name: 'Java后端开发工程师', family: '后端开发', keywords: ['Java'], abilities: ['工程实践'] }]
    else if (pathname.endsWith('/resume-workbench/versions')) data = []
    else if (pathname.endsWith('/user/profile')) data = { nickname: '界面验收', role: 'STUDENT' }
    else if (pathname.endsWith('/interview/start')) {
      submitted = route.request().postDataJSON()
      return route.fulfill({ json: { code: 500, message: '隔离验收：已捕获配置，未创建面试' } })
    }
    return route.fulfill({ json: { code: 200, data } })
  })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  const visit = async () => {
    await page.goto((process.env.COMPANY_QA_URL || 'http://localhost:5173') + '/jobs?step=3')
    await page.locator('.company-preset__options button:not(:disabled)').first().waitFor()
    await page.waitForTimeout(400)
  }
  const orderNames = () => page.locator('.priority-list li:not(.priority-leave-active) .priority-info strong').allTextContents()
  const expectedNames = preset => preset.codes.map(code => modules.find(m => m.code === code).name)
  try {
    await visit()
    await page.locator('.module-option').nth(4).click()
    await page.locator('.module-option').nth(9).click()
    await page.locator('.drag-handle').first().focus()
    await page.keyboard.press('Alt+ArrowDown')
    const customNames = await orderNames()
    await page.locator('.priority-list li').first().click()
    await page.locator('.strategy-level button').last().click()
    for (const preset of presets) {
      await page.locator('.company-preset__options button').filter({ hasText: preset.name }).click()
      await page.waitForTimeout(300)
      assert.deepEqual(await orderNames(), expectedNames(preset))
      assert.equal(await page.locator('.module-option input:checked').count(), 5)
      assert.equal(await page.locator('.module-option input:disabled').count(), 10)
      assert.equal(await page.locator('.drag-handle:disabled').count(), 5)
      assert.equal(await page.locator('.company-preset__options [aria-pressed="true"]').count(), 1)
      assert((await page.locator('.company-preset__detail').innerText()).includes(preset.culture))
      assert.equal(await page.locator('.company-preset__detail a').getAttribute('href'), preset.source)
    }
    await page.getByRole('button', { name: '切换自定义', exact: true }).click()
    await page.waitForTimeout(300)
    assert.deepEqual(await orderNames(), customNames)
    assert.equal(await page.locator('.strategy-level button.is-active').innerText(), '挑战')
    assert.equal(await page.locator('.module-option input:disabled').count(), 0)
    await page.locator('.company-preset__options button').filter({ hasText: '字节跳动' }).click()
    await page.waitForTimeout(400)
    for (const width of [1920, 1440, 1024, 800]) {
      await page.setViewportSize({ width, height: 1080 })
      await page.mouse.move(0, 0)
      await page.waitForTimeout(200)
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), `Overflow at ${width}`)
      const boxes = await page.locator('.company-preset__options button').evaluateAll(buttons => buttons.map(b => ({ h: b.getBoundingClientRect().height, clipped: b.scrollWidth > b.clientWidth + 1 })))
      assert(boxes.every(b => b.h >= 44 && !b.clipped))
      await page.screenshot({ path: path.join(out, `training-goals-${width}.png`), fullPage: true })
    }
    await page.setViewportSize({ width: 1440, height: 1080 })
    await page.getByRole('button', { name: '确认训练目标', exact: true }).click()
    assert((await page.locator('.config-company').innerText()).includes('字节跳动'))
    assert.deepEqual(await page.locator('.config-priorities li > span').allTextContents(), expectedNames(presets[0]))
    await page.getByRole('button', { name: '返回修改', exact: true }).click()
    assert.equal(await page.locator('.company-preset__options [aria-pressed="true"]').innerText(), '字节跳动')
    await page.getByRole('button', { name: '确认训练目标', exact: true }).click()
    // Feed a local canvas stream through the real camera-consent flow.
    await page.evaluate(() => {
      navigator.mediaDevices.getUserMedia = async () => {
        const canvas = document.createElement('canvas')
        canvas.width = 640; canvas.height = 480
        canvas.getContext('2d').fillRect(0, 0, 640, 480)
        return canvas.captureStream(1)
      }
    })
    await page.getByRole('button', { name: '开始 AI 虚拟面试', exact: true }).click()
    await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
    await page.locator('.start-error').waitFor()
    assert.deepEqual(submitted.modulePreferences.map(m => m.code), presets[0].codes)
    assert.deepEqual(submitted.modulePreferences.map(m => m.rank), [1, 2, 3, 4, 5])
    assert(submitted.modulePreferences.every(m => [1, 2, 3].includes(m.level)))
    availableModules = modules.slice(0, 5)
    await page.reload()
    await page.getByRole('button', { name: '返回修改', exact: true }).waitFor({ state: 'hidden' })
    await page.locator('.company-preset__options button').first().waitFor()
    assert.equal(await page.locator('.company-preset__options button:disabled').count(), 7)
    assert((await page.locator('.company-preset').innerText()).includes('部分企业预设所需的维度暂未提供'))
    assert.equal(await page.locator('.module-option input:disabled').count(), 0)
    assert.deepEqual(errors, [])
    console.log('PASS: 7 presets, fixed dimensions/order, custom restoration, depth, confirmation, submitted ranks, missing dimensions, 1920/1440/1024/800 layout.')
    console.log('Screenshots: ' + out)
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
