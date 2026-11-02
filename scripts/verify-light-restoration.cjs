// Visual fixtures only; never signs into or writes to real accounts.
const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { pathToFileURL } = require('node:url')
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const root = path.resolve(__dirname, '..'), out = path.join(root, 'artifacts/light-restoration')
const before = process.argv.includes('--capture-before')
const stage = before ? 'before' : 'after'
const base = process.env.APPEARANCE_QA_URL || 'http://localhost:5173'
fs.mkdirSync(out, { recursive: true })
;(async () => {
  const { MOCK_JOBS, MOCK_USER, MOCK_MODULES, MOCK_OVERVIEW } = await import(pathToFileURL(path.join(root, '前端/src/utils/demoData.js')))
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--no-proxy-server'] })
  const checks = []
  try {
    for (const theme of ['light', 'dark']) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce', colorScheme: theme })
      await context.addInitScript(mode => {
        localStorage.setItem('token', 'appearance-fixture'); localStorage.setItem('role', 'STUDENT')
        localStorage.setItem('app-theme', mode)
      }, theme)
      await context.route('**/api/**', async route => {
        const url = new URL(route.request().url()).pathname
        if (!url.startsWith('/api/')) return route.continue()
        let data = []
        if (url === '/api/job/list') data = MOCK_JOBS
        else if (url === '/api/user/me') data = { ...MOCK_USER, nickname: '界面验收' }
        else if (url === '/api/user/career-profile') data = { targetJobId: 1 }
        else if (url === '/api/modules') data = MOCK_MODULES
        else if (url === '/api/dashboard/overview') data = MOCK_OVERVIEW
        else if (url.includes('unread')) data = { count: 0 }
        else if (url.includes('/resume')) data = url.includes('versions') ? [] : null
        await route.fulfill({ json: { code: 200, data } })
      })
      const page = await context.newPage(), errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.goto(base + '/jobs', { waitUntil: 'networkidle' })
      await page.locator('.job-list__item').first().waitFor()
      await page.evaluate(() => document.fonts.ready)
      await page.waitForTimeout(300)
      const colors = await page.evaluate(() => {
        const color = selector => getComputedStyle(document.querySelector(selector)).backgroundColor
        return { family: color('.family-nav'), detail: color('.job-detail'), logo: color('.job-list__item .job-logo'), body: color('body'),
          selected: document.querySelector('.job-list__item.is-selected strong')?.textContent,
          overflow: document.documentElement.scrollWidth > innerWidth + 1 }
      })
      assert.equal(colors.overflow, false)
      if (!before && theme === 'light') {
        assert.equal(colors.family, 'rgb(247, 250, 248)')
        assert.equal(colors.detail, 'rgb(248, 251, 249)')
        assert.notEqual(colors.logo, 'rgb(242, 244, 242)')
      }
      if (!before && theme === 'dark') {
        const previous = JSON.parse(fs.readFileSync(path.join(out, 'visual-before.json'), 'utf8')).find(item => item.theme === 'dark')
        assert.deepEqual(colors, previous.colors, 'restoring light appearance must preserve dark colors')
      }
      await page.screenshot({ path: path.join(out, `jobs-${theme}-${stage}.png`), fullPage: true })
      await page.getByPlaceholder('搜索岗位、技术栈或能力方向，例如 Java、RAG、性能测试').fill('Python')
      await page.locator('.job-list__item').filter({ hasText: 'Python后端开发工程师' }).click()
      assert.ok((await page.locator('.job-detail__identity h3').innerText()).includes('Python'))
      await page.locator('.job-detail__footer').getByRole('button', { name: /下一步/ }).click()
      await page.getByRole('heading', { name: /简历/, exact: false }).first().waitFor()
      checks.push({ theme, colors, searchAndNextStep: true, errors })
      if (!before) {
        await page.goto(base + '/home', { waitUntil: 'networkidle' })
        await page.locator('.dashboard .today-bar').waitFor()
        await page.screenshot({ path: path.join(out, `home-${theme}-after.png`), fullPage: true })
        for (const width of [360, 390, 430]) {
          await page.setViewportSize({ width, height: 844 })
          await page.goto(base + '/jobs', { waitUntil: 'networkidle' })
          await page.locator('.mjob-card').first().waitFor()
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `mobile overflow at ${width}`)
          await page.screenshot({ path: path.join(out, `jobs-${theme}-${width}-after.png`), fullPage: true })
          await page.locator('.mjob-card:not(:disabled)').first().click()
          await page.locator('.wiz__go').click()
          await page.waitForFunction(() => document.querySelector('.wiz__head strong')?.textContent.includes('简历'))
          await page.goto(base + '/home', { waitUntil: 'networkidle' })
          await page.getByRole('heading', { name: '今天准备哪场面试？' }).waitFor()
          assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
          checks.push({ theme, width, mobileSelectionAndNext: true, mobileHome: true })
        }
      }
      assert.deepEqual(errors, [])
      await context.close()
    }
    fs.writeFileSync(path.join(out, `visual-${stage}.json`), JSON.stringify(checks, null, 2))
    console.log(JSON.stringify(checks))
  } finally { await browser.close() }
})().catch(error => { console.error(error); process.exitCode = 1 })
