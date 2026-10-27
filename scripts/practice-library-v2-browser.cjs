const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const base='http://127.0.0.1:5203',out=path.resolve('output/practice-library-v2-qa');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const server=cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5203','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});let browser;
 try{
  for(let i=0;i<60;i++){try{if((await fetch(base)).ok)break}catch{}if(i===59)throw Error('preview unavailable');await new Promise(r=>setTimeout(r,250))}
  browser=await chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  await context.addInitScript(()=>{localStorage.setItem('token','library-browser-fixture');localStorage.setItem('userId','21');localStorage.setItem('role','STUDENT')});
  let practiceNetwork=0;const errors=[];
  await context.route('**/api/**',route=>{const p=new URL(route.request().url()).pathname;if(!p.startsWith('/api/'))return route.continue();if(p.startsWith('/api/practice/'))practiceNetwork++;return route.fulfill({json:{code:200,data:{}}})});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto(base+'/learning');
  await page.evaluate(async()=>{
   const api=await import('/src/services/learningResources.js'),resources=await api.loadLearningResources();
   const {createDemoEngine,demoStoragePrefix}=await import('/src/services/practiceDemo/engine.js'),engine=createDemoEngine(localStorage,()=> '21');engine.setRoles(resources.roles);
   for(let i=0;i<22;i++){
    const session=await engine.handle('/practice/sessions','post',{jobId:resources.roles[0].id,topic:'综合高频',questionType:'choice',questionCount:1,difficulty:'basic'}),q=session.questionList[0];
    if(i<20){const raw=JSON.parse(localStorage.getItem(demoStoragePrefix+'21')).sessions.find(s=>s.id===session.id).questionList[0];await engine.handle(`/practice/sessions/${session.id}/questions/${q.id}/answers`,'post',{answer:raw.options.find(o=>o.key!==raw.correctAnswer).key});await engine.handle(`/practice/sessions/${session.id}/finish`,'post')}
    if(i<21)await engine.handle(`/practice/questions/${q.id}/favorite`,'post');
   }
  });
  await page.goto(base+'/learning/library/recent');await page.locator('.record').first().waitFor();assert.equal(await page.locator('.record').count(),20);
  await page.screenshot({path:path.join(out,'recent-1440.png')});
  await page.getByRole('button',{name:'下一页',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.record').length===2);
  assert.equal(await page.locator('.record-number').first().innerText(),'21');
  await page.getByRole('combobox').selectOption('ONGOING');await page.waitForFunction(()=>document.querySelectorAll('.record').length===2&&document.querySelector('.record-number')?.textContent==='01');
  assert.equal(await page.getByRole('link',{name:'继续练习 →',exact:true}).count(),2);
  await page.getByRole('link',{name:'继续练习 →',exact:true}).first().click();await page.waitForURL('**/learning/session/demo-*');await page.getByRole('radio').first().waitFor();
  await page.goto(base+'/learning/library/recent?status=FINISHED');await page.locator('.record').first().waitFor();assert.equal(await page.getByRole('link',{name:'查看报告 →',exact:true}).count(),20);
  await page.getByRole('link',{name:'查看报告 →',exact:true}).first().click();await page.waitForURL('**/report');await page.getByText('本次练习已完成',{exact:true}).waitFor();
  await page.goto(base+'/learning/library/mistakes');await page.locator('.record').first().waitFor();assert.equal(await page.locator('.record').count(),20);
  assert.ok((await page.locator('.record').first().innerText()).includes('答案错误'));assert.ok((await page.locator('.record').first().innerText()).includes('待复习'));
  await page.screenshot({path:path.join(out,'mistakes-1440.png')});
  const originalHref=await page.getByRole('link',{name:'查看原题与作答 →',exact:true}).first().getAttribute('href');assert.ok(originalHref.includes('questionId='));
  await page.getByRole('link',{name:'查看原题与作答 →',exact:true}).first().click();await page.waitForURL('**/learning/session/demo-*?questionId=*');await page.getByRole('button',{name:'查看练习报告',exact:true}).waitFor();
  await page.goto(base+'/learning/library/favorites');await page.locator('.record').first().waitFor();await page.screenshot({path:path.join(out,'favorites-1440.png')});
  await page.getByRole('button',{name:'下一页',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.record').length===1);
  await page.getByRole('button',{name:'☆ 取消收藏',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.record').length===20&&document.querySelector('.record-number')?.textContent==='01');
  await page.reload();await page.locator('.record').first().waitFor();assert.ok((await page.locator('.total-count').innerText()).includes('20'));
  for(const width of [1920,1366,1024,390,360]){
   await page.setViewportSize({width,height:width<768?844:1080});
   for(const kind of ['recent','mistakes','favorites']){await page.goto(base+'/learning/library/'+kind);await page.locator('.record').first().waitFor();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`${kind} overflow at ${width}`);if(width===390)await page.screenshot({path:path.join(out,`${kind}-390.png`)})}
  }
  assert.equal(practiceNetwork,0);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({checks:['unified three lists','recent status filter and pagination','report/continue/original question navigation','favorite cancellation on last page and persistence','1920/1440/1366/1024/390/360 no overflow'],practiceNetwork,errors},null,2));console.log('PASS unified library lists, paging, navigation, favorites and responsive layouts');
 }finally{await browser?.close();server.kill()}
})().catch(e=>{console.error(e);process.exitCode=1});
