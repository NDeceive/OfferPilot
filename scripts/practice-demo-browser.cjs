// Exercises the installed demo adapter, not mocked practice responses.
const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),assert=require('node:assert/strict');
const out=path.resolve('output/practice-demo-qa');fs.mkdirSync(out,{recursive:true});
const base='http://127.0.0.1:5199';
(async()=>{
 const server=cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5199','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});let browser;
 try{
  for(let i=0;i<60;i++){try{if((await fetch(base)).ok)break}catch{}if(i===59)throw Error('preview unavailable');await new Promise(r=>setTimeout(r,250))}
  browser=await chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});
  await context.addInitScript(()=>{localStorage.setItem('token','demo-browser-fixture');localStorage.setItem('userId','21');localStorage.setItem('role','STUDENT')});
  let practiceNetwork=0;const errors=[];
  await context.route('**/api/**',async route=>{const p=new URL(route.request().url()).pathname;if(!p.startsWith('/api/'))return route.continue();if(p.startsWith('/api/practice/')){practiceNetwork++;return route.fulfill({status:503,json:{code:503,message:'real practice backend unavailable'}})}return route.fulfill({json:{code:200,data:{}}})});
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',dialog=>dialog.accept());
  await page.goto(base+'/learning');await page.getByRole('button',{name:'开始岗位专项 →',exact:true}).click();
  await page.getByRole('button',{name:'调整题量 / 难度',exact:true}).click();await page.getByRole('button',{name:'10',exact:true}).click();
  await page.getByRole('button',{name:'开始专项刷题 →',exact:true}).click();await page.waitForURL('**/learning/session/demo-*');
  const sid=new URL(page.url()).pathname.split('/').pop();await page.getByRole('radio').first().waitFor();
  // A fresh transport module must load a saved session without main.js installing it.
  const directSession=await page.evaluate(async id=>{
    const {practiceDemoRequest}=await import('/src/services/practiceDemo/index.js?transport-regression');
    const realRequest=(await import('/src/utils/request.js')).default;
    realRequest.defaults.adapter='fetch';
    return practiceDemoRequest.get(`/practice/sessions/${id}`);
  },sid);
  assert.equal(directSession.id,sid);
  await page.getByRole('radio').first().check();await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('第 2 / 10 题',{exact:true}).waitFor();
  await page.getByRole('button',{name:'上一题',exact:true}).click();await page.getByText('答案已记录，练习结束后统一查看结果。',{exact:true}).waitFor();
  assert(!/答案正确|答案错误|正确答案|通过|未通过/.test(await page.locator('.judge-result').innerText()));
  await page.getByRole('navigation',{name:'题目内容'}).getByRole('button',{name:'解析',exact:true}).click();await page.getByText('练习结束后统一展示正确答案与解析。',{exact:true}).waitFor();
  await page.getByRole('navigation',{name:'题目内容'}).getByRole('button',{name:'提交记录',exact:true}).click();await page.locator('.submission').waitFor();assert(!/OBJECTIVE_CORRECT|OBJECTIVE_INCORRECT|答案正确|答案错误/.test(await page.locator('.submission').innerText()));
  await page.reload();await page.getByText('答案已记录，练习结束后统一查看结果。',{exact:true}).waitFor();
  await page.screenshot({path:path.join(out,'ongoing-1440.png')});
  const session=await page.evaluate(async id=>{const api=await import('/src/services/learningResources.js');return api.getTrainingSession(id)},sid);
  const coding=session.questionList.find(q=>q.type==='coding');assert(coding);
  await page.getByRole('button',{name:'题目目录',exact:true}).click();await page.locator('.directory-item').filter({hasText:coding.title}).click();await page.locator('.monaco-editor').waitFor();
  await page.locator('.monaco-editor textarea').focus();await page.keyboard.press('Control+A');await page.keyboard.insertText('public class Main { public static void main(String[] args) { System.out.println(0); } }');
  await page.getByRole('button',{name:'运行代码',exact:true}).click();await page.getByText('样例运行已完成，判定与解析在练习结束后统一展示。',{exact:true}).waitFor();
  assert.equal(await page.locator('.question-content h2').innerText(),coding.title);
  await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText(`第 ${coding.sequenceNo+1} / 10 题`,{exact:true}).waitFor();
  await page.getByRole('button',{name:'上一题',exact:true}).click();await page.getByText('答案已记录，练习结束后统一查看结果。',{exact:true}).waitFor();
  assert(!/测试通过|输出错误|编译错误|正确答案/.test(await page.locator('.judge-result').innerText()));
  await page.goto(`${base}/learning/session/${sid}/report`);await page.getByText('本次练习采用统一结算。请先完成或结束练习，再查看判定与解析。',{exact:true}).waitFor();assert.equal(await page.locator('.statistics').count(),0);
  await page.getByRole('link',{name:'继续练习 →',exact:true}).click();await page.getByRole('button',{name:'结束练习',exact:true}).click();await page.waitForURL('**/report');await page.getByText('本次练习已完成',{exact:true}).waitFor();
  assert.equal(await page.locator('.report-question').count(),10);assert.ok((await page.locator('.report-overview').innerText()).includes('2 / 10'));
  await page.locator('.report-question').filter({hasText:coding.title}).getByRole('button',{name:'查看作答与解析 →',exact:true}).click();assert.ok((await page.locator('.question-drawer').innerText()).includes('本地文本规则'));
  await page.waitForTimeout(350);await page.screenshot({path:path.join(out,'settlement-1440.png'),fullPage:true});
  await page.reload();await page.getByText('本次练习已完成',{exact:true}).waitFor();
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(200);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'settlement-390.png'),fullPage:true});
  await page.getByRole('button',{name:/重练未通过题/}).click();await page.waitForURL('**/learning/session/demo-*');assert.notEqual(new URL(page.url()).pathname.split('/').pop(),sid);
  const single=await page.evaluate(async role=>{const api=await import('/src/services/learningResources.js');await api.loadLearningResources();return api.createTrainingSession({role,topic:'综合高频',questionType:'choice',questionCount:1,difficulty:'basic'})},session.role);
  await page.setViewportSize({width:1440,height:900});await page.goto(`${base}/learning/session/${single.id}`);await page.getByRole('radio').first().waitFor();
  await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('请先选择答案。',{exact:false}).waitFor();assert.ok((await page.locator('.progress').innerText()).includes('第 1 / 1 题'));
  await page.getByRole('radio').first().check();await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案已记录，练习结束后统一查看结果。',{exact:true}).waitFor();
  assert.ok((await page.locator('.progress').innerText()).includes('第 1 / 1 题'));assert.ok(await page.getByRole('button',{name:'完成练习',exact:true}).isVisible());
  assert.equal(practiceNetwork,0);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({checks:['fixed demo mode','no practice network','hidden submission outcome','locked explanations/history/early report','refresh persistence','local code run and submit','complete settlement','unsubmitted counts','desktop/mobile','failed-only retry','submit advances objective and code','run/empty answer do not advance','last submission stays on final question'],practiceNetwork,errors},null,2));console.log('PASS demo practice and settlement; no real practice network calls');
 }finally{await browser?.close();server.kill()}
})().catch(e=>{console.error(e);process.exitCode=1});
