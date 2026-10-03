const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const browser = await chromium.launch({channel:'msedge', headless:true});
  const page = await browser.newPage({viewport:{width:1440,height:1000}});
  const errors=[];
  page.on('pageerror', e=>errors.push(e.message));
  await page.addInitScript(()=>{localStorage.setItem('token','theme-test');localStorage.setItem('role','STUDENT');});
  let empty = false;
  await page.route('**/api/**', async route=>{
    const url=route.request().url();
    if (!new URL(url).pathname.startsWith('/api/')) return route.continue();
    let data=[];
    if(url.includes('/dashboard/overview')) data={summary:{completedCount:3,recentCount:3,bestScore:82,streakDays:1},recentInterviews:[],trend:[],nextAction:empty?{type:'FIRST_INTERVIEW',title:'开始第一次模拟面试',description:'选择目标岗位，建立第一份可复盘的训练记录。',route:'/jobs'}:{type:'TARGETED_PRACTICE',title:'针对“项目表达”再练一次',description:'结合最近一次报告选择岗位与难度，继续积累可比较的表现数据。',route:'/jobs'},latestInsight:empty?null:{reportId:1,jobName:'Java后端开发工程师',weakestDimension:'项目表达',weakestScore:62,strongestDimension:'专业知识',strongestScore:85,suggestion:'回答项目问题时，先说明个人负责的工作，再结合具体决策与结果展开。',dimensions:[{dimension:'项目表达',score:62}]}};
    if(url.includes('/resume/mine')) data={skills:['Java','Spring']};
    if(url.includes('/ai/status')) data={mode:'DEMO',available:false};
    await route.fulfill({json:{code:200,data}});
  });
  const out=path.resolve(__dirname,'../output/theme-review');fs.mkdirSync(out,{recursive:true});
  for(const [route,name,selector] of [['/home','home','.advice-copy .primary-btn'],['/interview/ai','ai-coach','.chat__brand'],['/learning','learning','.start-button']]){
    await page.goto('http://127.0.0.1:5182'+route);await page.waitForTimeout(1000);
    const color=await page.locator(selector).evaluate(el=>({background:getComputedStyle(el).backgroundColor,image:getComputedStyle(el).backgroundImage}));
    assert.equal(color.background,'rgb(16, 185, 129)',name);assert.equal(color.image,'none',name);
    if(name==='home'){
      assert.equal(await page.locator('.advice-copy .primary-btn').innerText(),'开始针对性训练');
      await page.locator('.advice-copy .primary-btn').hover();
      await page.waitForTimeout(250);
      assert.equal(await page.locator('.advice-copy .primary-btn').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(5, 150, 105)');
      await page.mouse.move(0,0);
      await page.waitForTimeout(250);
    }
    await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});
    for(const width of [1024,768,390]){
      await page.setViewportSize({width,height:1000});await page.waitForTimeout(250);
      const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
      assert.equal(overflow,false,`${name} overflow at ${width}`);
    }
    await page.setViewportSize({width:1440,height:1000});
  }
  empty=true;await page.goto('http://127.0.0.1:5182/home');await page.waitForTimeout(400);
  assert.equal(await page.locator('.advice-copy .primary-btn').innerText(),'开始首次面试');
  assert.equal(await page.locator('.coach-feedback').count(),0);
  assert.equal(await page.locator('.advice-copy .secondary-btn').count(),0);
  assert.deepEqual(errors,[]);
  await browser.close();console.log('PASS: colors, hover, report/empty state, responsive overflow, runtime errors');
})().catch(e=>{console.error(e);process.exit(1)});
