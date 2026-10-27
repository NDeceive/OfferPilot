// Report V2 acceptance using the installed demo engine and real Vue page.
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const base = 'http://127.0.0.1:5201', out = path.resolve('output/practice-report-v2-qa');
fs.mkdirSync(out, { recursive: true });
(async () => {
  const server = cp.spawn(process.execPath, [path.resolve('前端/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '5201', '--strictPort'], { cwd: path.resolve('前端'), stdio: 'ignore', windowsHide: true });
  let browser;
  try {
    for (let i = 0; i < 60; i++) { try { if ((await fetch(base)).ok) break; } catch {} if (i === 59) throw Error('Preview unavailable'); await new Promise(r => setTimeout(r, 250)); }
    browser = await chromium.launch({ channel: 'msedge', headless: true });
    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, reducedMotion: 'reduce' });
    await context.addInitScript(() => { localStorage.setItem('token', 'report-browser-fixture'); localStorage.setItem('userId', '21'); localStorage.setItem('role', 'STUDENT'); });
    let practiceNetwork = 0; const errors = [], checks = [];
    await context.route('**/api/**', route => { const p = new URL(route.request().url()).pathname; if (!p.startsWith('/api/')) return route.continue(); if (p.startsWith('/api/practice/')) practiceNetwork++; return route.fulfill({ json: { code: 200, data: {} } }); });
    const page = await context.newPage(); page.on('pageerror', e => errors.push(e.message));
    await page.goto(base + '/learning');
    const fixture = await page.evaluate(async () => {
      const api = await import('/src/services/learningResources.js'); const resources = await api.loadLearningResources();
      const session = await api.createTrainingSession({ role: resources.roles[0], company: '字节跳动', topic: '综合高频', questionType: 'all', questionCount: 20, difficulty: 'medium' });
      const { createDemoEngine, demoStoragePrefix } = await import('/src/services/practiceDemo/engine.js');
      const engine = createDemoEngine(localStorage, () => localStorage.getItem('userId'));
      const saved = JSON.parse(localStorage.getItem(demoStoragePrefix + '21')).sessions.find(s => s.id === session.id);
      let objective = 0, historicalQuestion;
      for (const q of saved.questionList) {
        if (q.type === 'CODING') await engine.handle(`/practice/sessions/${session.id}/questions/${q.id}/tasks`, 'post', { requestKey: crypto.randomUUID(), operation: 'submit', sourceCode: 'public class Main { public static void main(String[] args) { System.out.println(0); } }' });
        else {
          const answer = q.options ? objective++ % 2 ? q.options.find(o => !q.correctAnswer.split(',').includes(o.key)).key : q.correctAnswer : '首先明确输入与边界条件，分析方案，再通过实际数据验证结果。';
          if (q.type === 'MULTIPLE_CHOICE') { historicalQuestion = q.id; for (let i = 0; i < 20; i++) await engine.handle(`/practice/sessions/${session.id}/questions/${q.id}/answers`, 'post', { answer }); }
          await engine.handle(`/practice/sessions/${session.id}/questions/${q.id}/answers`, 'post', { answer });
        }
      }
      const report = await engine.handle(`/practice/sessions/${session.id}/finish`, 'post');
      return { id: session.id, report, historicalQuestion };
    });
    const reportUrl = `${base}/learning/session/${fixture.id}/report`;
    await page.goto(reportUrl); await page.getByText('本次练习已完成', { exact: true }).waitFor();
    assert.equal(await page.locator('.report-question').count(), 20);
    const evaluated = fixture.report.statistics.codingEvaluated + fixture.report.statistics.objectiveCorrect + fixture.report.statistics.objectiveIncorrect;
    assert.equal(await page.locator('.score').innerText(), `${Math.round((fixture.report.statistics.codingPassed + fixture.report.statistics.objectiveCorrect) / evaluated * 100)}%`);
    assert.ok((await page.locator('.report-question').nth(1).boundingBox()).y + 88 < 1080);
    const navBefore = await page.locator('.topnav').boundingBox();
    await page.screenshot({ path: path.join(out, 'report-1920.png') }); checks.push('dynamic score; two rows in first desktop viewport');
    const multi = fixture.report.questionList.find(q => q.id === fixture.historicalQuestion);
    const multiRow = page.locator(`[data-question-id="${multi.id}"]`);
    await multiRow.getByRole('button', { name: '查看作答与解析 →', exact: true }).click();
    const drawer = page.getByRole('dialog'); await drawer.waitFor();
    assert.equal((await drawer.boundingBox()).width, 520); assert.equal((await drawer.boundingBox()).y, navBefore.height);
    assert.deepEqual(await page.locator('.topnav').boundingBox(), navBefore);
    assert.equal(await drawer.locator('.readonly-options input').count(), 0);
    assert.equal(await drawer.locator('.readonly-options .chosen').count(), multi.latestSubmission.answerText.split(',').length);
    await page.screenshot({ path: path.join(out, 'drawer-1920.png') });
    await page.evaluate(async () => {
      const { practiceDemoRequest: request } = await import('/src/services/practiceDemo/index.js'); const original = request.defaults.adapter;
      request.defaults.adapter = async config => { if (config.url.endsWith('/submissions')) { request.defaults.adapter = original; throw Error('验收：提交历史临时不可用'); } return original(config); };
    });
    await drawer.getByRole('tab', { name: '提交历史', exact: true }).click(); await drawer.getByRole('alert').waitFor();
    await drawer.getByRole('button', { name: '重试', exact: true }).click(); await drawer.locator('.history-entry').first().waitFor();
    assert.equal(await drawer.locator('.history-entry').count(), 20);
    await drawer.getByRole('button', { name: '加载更多提交', exact: true }).click(); await page.waitForFunction(() => document.querySelectorAll('.history-entry').length === 21);
    await drawer.getByRole('tab', { name: '题目解析', exact: true }).click(); assert.ok((await drawer.innerText()).includes(multi.referenceAnswer));
    await drawer.getByRole('button', { name: '关闭题目详情', exact: true }).click(); checks.push('drawer preserves nav; accurate readonly options; explanation and paginated history');
    const wrong = fixture.report.questionList.find(q => q.latestSubmission.status === 'OBJECTIVE_INCORRECT');
    await page.getByRole('button', { name: '查看错误题目 →', exact: true }).click();
    assert.equal(await page.getByLabel('题型筛选', { exact: true }).inputValue(), 'objective');
    const wrongRow = page.locator(`[data-question-id="${wrong.id}"]`);
    await page.getByLabel('搜索题目名称或关键词', { exact: true }).fill(wrong.title);
    assert.ok(await page.locator('.report-question').count() > 0);
    assert.ok((await page.locator('.report-question h3').allTextContents()).every(title => title.includes(wrong.title)));
    await page.getByLabel('复习状态筛选', { exact: true }).selectOption('PENDING');
    await wrongRow.getByRole('button', { name: '标记复习完成', exact: true }).click(); await wrongRow.waitFor({ state: 'hidden' });
    await page.getByLabel('复习状态筛选', { exact: true }).selectOption('RESOLVED');
    await wrongRow.getByText('已标记复习完成', { exact: true }).waitFor(); assert.ok((await wrongRow.innerText()).includes('回答错误'));
    await page.evaluate(async () => {
      const { practiceDemoRequest: request } = await import('/src/services/practiceDemo/index.js'); const original = request.defaults.adapter;
      request.defaults.adapter = async config => { if (config.url.endsWith('/favorite')) { request.defaults.adapter = original; throw Error('验收：收藏保存失败'); } return original(config); };
    });
    await wrongRow.getByRole('button', { name: '☆ 收藏', exact: true }).click(); await page.getByRole('alert').waitFor();
    assert.ok(await wrongRow.getByRole('button', { name: '☆ 收藏', exact: true }).isVisible());
    await wrongRow.getByRole('button', { name: '☆ 收藏', exact: true }).click(); await wrongRow.getByRole('button', { name: '★ 已收藏', exact: true }).waitFor();
    const scrollBefore = await page.evaluate(() => scrollY); await wrongRow.getByRole('button', { name: '查看作答与解析 →', exact: true }).click();
    await drawer.getByRole('tab', { name: '本次作答', exact: true }).focus(); await page.keyboard.press('ArrowRight');
    assert.equal(await drawer.getByRole('tab', { name: '题目解析', exact: true }).getAttribute('aria-selected'), 'true');
    await page.keyboard.press('Escape'); await drawer.waitFor({ state: 'hidden' }); assert.equal(await page.evaluate(() => scrollY), scrollBefore);
    assert.equal(await page.getByLabel('搜索题目名称或关键词', { exact: true }).inputValue(), wrong.title);
    await page.reload(); await page.getByText('本次练习已完成', { exact: true }).waitFor();
    await wrongRow.getByRole('button', { name: '★ 已收藏', exact: true }).waitFor();
    assert.ok((await wrongRow.innerText()).includes('已标记复习完成')); checks.push('combined filters; review leaves wrong result unchanged; favorite/review survive reload; close restores scroll; keyboard tabs/Escape; failed history/favorite recovery');
    await page.getByRole('button', { name: '前往完成自评 →', exact: true }).click(); await drawer.getByRole('heading', { name: '对照参考答案完成自评', exact: true }).waitFor();
    const knowledge = fixture.report.questionList.find(q => q.latestSubmission?.status === 'SELF_REVIEW');
    const scoreBeforeAssessment = await page.locator('.score').innerText();
    await drawer.getByRole('button', { name: '需要巩固', exact: true }).click();
    await drawer.getByText('自评已保存：需要巩固。可重新选择。', { exact: true }).waitFor();
    await drawer.getByRole('button', { name: '已掌握', exact: true }).click();
    await drawer.getByText('自评已保存：已掌握。可重新选择。', { exact: true }).waitFor();
    await page.screenshot({ path: path.join(out, 'self-assessment-1920.png') });
    assert.equal(await page.locator('.score').innerText(), scoreBeforeAssessment);
    await drawer.getByRole('button', { name: '关闭题目详情', exact: true }).click();
    await page.reload(); await page.getByText('本次练习已完成', { exact: true }).waitFor();
    const knowledgeRow = page.locator(`[data-question-id="${knowledge.id}"]`);
    await knowledgeRow.getByText('自评：已掌握', { exact: true }).waitFor();
    await knowledgeRow.getByRole('button', { name: '修改自评', exact: true }).click();
    assert.equal(await drawer.getByRole('button', { name: '已掌握', exact: true }).getAttribute('aria-pressed'), 'true');
    const assessmentReport = await page.evaluate(async id => (await (await import('/src/services/learningResources.js')).getPracticeReport(id)), fixture.id);
    assert.equal(assessmentReport.statistics.selfReview, 0);
    assert.deepEqual(assessmentReport.questionList.find(q => q.id === knowledge.id).latestSubmission, knowledge.latestSubmission);
    await drawer.getByRole('button', { name: '关闭题目详情', exact: true }).click(); checks.push('visible self-assessment choices; saved/edited across reload; unchanged score and historical answer');
    await page.getByRole('button', { name: '查看未通过题 →', exact: true }).click(); assert.equal(await page.locator('.report-question').count(), fixture.report.statistics.codingFailed);
    await page.locator('.report-question').first().getByRole('button', { name: '查看作答与解析 →', exact: true }).click(); await drawer.locator('.monaco-editor').waitFor();
    assert.ok((await drawer.innerText()).includes('未调用真实编译器')); await drawer.getByRole('button', { name: '关闭题目详情', exact: true }).click(); checks.push('card drilldowns; self review reference; original code editor reused readonly');
    await page.reload(); await page.getByText('本次练习已完成', { exact: true }).waitFor();
    for (const width of [1440, 1366, 1024, 390, 360]) {
      await page.setViewportSize({ width, height: width < 768 ? 844 : 1080 }); await page.waitForTimeout(100);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `overflow at ${width}`);
      await page.locator('.report-question').first().getByRole('button', { name: '查看作答与解析 →', exact: true }).click(); await drawer.waitFor();
      assert((await drawer.boundingBox()).width <= width); assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      if (width === 390 || width === 1366) await page.screenshot({ path: path.join(out, `drawer-${width}.png`) });
      await drawer.getByRole('button', { name: '关闭题目详情', exact: true }).click(); await page.evaluate(() => scrollTo(0, 0));
      if (width === 390 || width === 1366) await page.screenshot({ path: path.join(out, `report-${width}.png`) });
    }
    checks.push('1920/1440/1366/1024/390/360 responsive with no overflow');
    await page.setViewportSize({ width: 1920, height: 1080 });
    const originalHistory = await page.evaluate(async id => (await (await import('/src/services/learningResources.js')).getPracticeReport(id)).questionList.map(q => q.latestSubmission), fixture.id);
    await page.locator('.report-question').nth(1).getByRole('button', { name: '重新作答', exact: true }).click(); await page.waitForURL('**/learning/session/demo-*?questionId=*');
    assert.ok((await page.locator('.progress').innerText()).includes('第 2 / 20 题')); assert.notEqual(new URL(page.url()).pathname.split('/').pop(), fixture.id);
    const retained = await page.evaluate(async id => (await (await import('/src/services/learningResources.js')).getPracticeReport(id)).questionList.map(q => q.latestSubmission), fixture.id);
    assert.deepEqual(retained, originalHistory); checks.push('new retry session targets selected question; original submissions unchanged');
    async function injectReport(mode) {
      await page.evaluate(async ({ mode, id }) => {
        const router = (await import('/src/router/index.js')).default;
        await router.push('/learning');
        const { practiceDemoRequest: request } = await import('/src/services/practiceDemo/index.js'); const original = request.defaults.adapter;
        request.defaults.adapter = async config => {
          if (!config.url.endsWith('/report')) return original(config);
          request.defaults.adapter = original;
          if (mode === 'error') throw Error('验收：报告临时不可用');
          const response = await original(config), data = response.data;
          if (mode === 'empty') { data.questionList = []; for (const key of Object.keys(data.statistics)) data.statistics[key] = 0; }
          if (mode === 'system') {
            const q = data.questionList[0]; q.latestSubmission = { ...q.latestSubmission, status: 'SYSTEM_ERROR', isCorrect: null, result: { status: 'SYSTEM_ERROR', message: '验收：判题服务异常' } };
            data.statistics.objectiveCorrect--; data.statistics.submitted--; data.statistics.unsubmitted++;
            data.systemFailureCount = 1; data.tasks = [{ questionId: q.id, operation: 'submit', status: 'SYSTEM_ERROR' }];
          }
          if (mode === 'legacy') {
            const q = data.questionList.find(q => q.type === 'knowledge');
            data.questionList = [{ ...q, latestSubmission: { status: 'NEEDS_REVIEW', isCorrect: false, answerText: '历史回答' }, reviewState: '' }];
            for (const key of Object.keys(data.statistics)) data.statistics[key] = 0;
            Object.assign(data.statistics, { total: 1, submitted: 1, legacyReview: 1 });
          }
          return response;
        };
        await router.push(`/learning/session/${id}/report`);
      }, { mode, id: fixture.id });
    }
    await injectReport('error'); await page.getByRole('heading', { name: '报告暂时无法加载', exact: true }).waitFor();
    await page.getByRole('button', { name: '重新加载', exact: true }).click(); await page.getByText('本次练习已完成', { exact: true }).waitFor();
    await injectReport('empty'); await page.getByRole('heading', { name: '本次练习暂无题目记录', exact: true }).waitFor(); assert.equal(await page.locator('.score').innerText(), '—');
    await injectReport('system'); await page.getByRole('alert').waitFor();
    assert.ok((await page.getByRole('alert').innerText()).includes('不计为学生答错'));
    assert.ok((await page.locator('.report-question').first().innerText()).includes('判题异常'));
    await injectReport('legacy'); await page.getByRole('button', { name: '重练未通过题（1 道）', exact: true }).waitFor();
    assert.ok(await page.getByRole('button', { name: '重练未通过题（1 道）', exact: true }).isEnabled());
    assert.equal(await page.locator('.score').innerText(), '—'); checks.push('report failure/reload; empty report; visible system failure; legacy retry without false correctness');
    assert.equal(practiceNetwork, 0); assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(out, 'verification.json'), JSON.stringify({ checks, practiceNetwork, errors }, null, 2));
    console.log('PASS report V2: ' + checks.join('; '));
  } finally { await browser?.close(); server.kill(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
