// UI fixtures verify navigation and failure states; judging and data rules are tested separately.
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict'), path = require('node:path'), fs = require('node:fs'), cp = require('node:child_process');
(async () => {
  const server = cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5196','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});
  let browser;
  try {
    for(let i=0;i<60;i++){try{if((await fetch('http://127.0.0.1:5196')).ok) break;}catch{} if(i===59)throw Error('preview unavailable');await new Promise(r=>setTimeout(r,250));}
    browser = await chromium.launch({channel:'msedge',headless:true});
    const context = await browser.newContext({viewport:{width:1440,height:900}});
    await context.addInitScript(() => {localStorage.setItem('token','library-fixture');localStorage.setItem('role','STUDENT');localStorage.setItem('userId','21');});
    let fail = false, removed = false, overviewFailure = false; const errors=[];
    await context.route('**/api/**', async route => {
      const url = new URL(route.request().url());if(!url.pathname.startsWith('/api/'))return route.continue();
      const p=url.pathname; let data={};
      if(p==='/api/practice/overview' && overviewFailure)return route.fulfill({status:503,json:{message:'fixture unavailable'}});
      if(p.includes('/practice/library/') && fail)return route.fulfill({status:503,json:{message:'fixture unavailable'}});
      if(p==='/api/practice/sessions')data={items:[{id:10,role:{name:'Java后端开发工程师'},topic:'综合高频',company:'不限公司',status:'ONGOING',completed:2,questionCount:20,updatedAt:'2026-10-08T12:00:00'}],total:21};
      else if(p.includes('/practice/library/'))data={items:removed && p.endsWith('favorites') ? [] : [{id:11,sessionId:10,title:'测试题目：如何分析并发问题',jobName:'Java后端开发工程师',topic:'Java核心',company:'不限公司',questionType:'KNOWLEDGE'}],total:removed && p.endsWith('favorites') ? 0 : 1};
      else if(p.endsWith('/favorite')) {removed=true;data={favorite:false};}
      else if(p==='/api/practice/overview')data={recentSession:null,libraryCounts:{mistakes:0,favorites:0,recent:0}};
      else if(p==='/api/practice/catalog')data={ready:true,companies:[],codingCoverage:[]};
      await route.fulfill({json:{code:200,data}});
    });
    const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
    const out=path.resolve('output/practice-library-qa');fs.mkdirSync(out,{recursive:true});
    await page.goto('http://127.0.0.1:5196/learning/library/recent');await page.getByRole('link',{name:'继续练习 →',exact:true}).waitFor();
    await page.getByRole('button',{name:'下一页',exact:true}).click();await page.getByText('第 2 / 2 页').waitFor();
    await page.screenshot({path:path.join(out,'recent-1440.png')});
    await page.getByRole('link',{name:'我的收藏',exact:true}).click();await page.locator('.record').waitFor();
    assert.equal(await page.getByRole('link',{name:'查看原题与作答 →'}).getAttribute('href'),'/learning/session/10?questionId=11');
    page.on('dialog',d=>d.accept());await page.getByRole('button',{name:'取消收藏',exact:true}).click();await page.getByRole('heading',{name:'暂无收藏题目'}).waitFor();
    fail=true;await page.getByRole('link',{name:'错题本',exact:true}).click();await page.getByText('记录加载失败，请重试。').waitFor();
    fail=false;await page.getByRole('button',{name:'重新加载',exact:true}).click();await page.locator('.record').waitFor();
    await page.screenshot({path:path.join(out,'mistakes-1440.png')});
    await page.setViewportSize({width:390,height:844});await page.waitForTimeout(250);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    await page.screenshot({path:path.join(out,'mistakes-390.png')});
    await page.goto('http://127.0.0.1:5196/learning');await page.getByRole('navigation',{name:'个人题库'}).waitFor();
    assert.equal(await page.getByRole('navigation',{name:'个人题库'}).getByRole('link').count(),3);
    await page.setViewportSize({width:1440,height:900});overviewFailure=true;
    await page.goto('http://127.0.0.1:5196/learning');await page.getByText('练习记录暂时无法加载',{exact:true}).waitFor();
    assert.equal(await page.locator('.library-links').innerText().then(s=>s.includes('—')),true);
    overviewFailure=false;await page.getByRole('button',{name:'重新加载',exact:true}).click();await page.getByText('暂无待继续的练习',{exact:true}).waitFor();
    assert.equal(await page.locator('.role-grid .selected').count(),0);
    assert.deepEqual(errors,[]);console.log('PASS list navigation, paging, question link, favorite removal, empty/error/retry, 390px layout, mobile entries');
  } finally {await browser?.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1});
