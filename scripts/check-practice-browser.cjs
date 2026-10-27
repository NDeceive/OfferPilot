const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.PRACTICE_QA_URL || 'http://127.0.0.1:5184';
const out = path.resolve('output/practice-qa');
fs.mkdirSync(out, { recursive: true });
const longText = '请解释 HashMap 的工作原理，说明哈希冲突、扩容与线程安全的边界。'.repeat(24);
const questions = [
 { id:11,type:'knowledge',title:'HashMap 的工作原理',difficulty:2,description:longText,constraints:['说明关键概念与因果关系','结合实际使用场景讨论边界'],status:'PENDING',favorite:false },
 { id:12,type:'coding',title:'计算一行整数之和',difficulty:1,description:'从标准输入读取一行以空格分隔的整数，输出它们的总和。请使用 Java 17，并保证输出仅包含结果。',language:'java',starter:'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // 编写你的代码\n    }\n}',signature:'public class Main',constraints:['从标准输入读取','输出只包含答案','最多 3 秒'],examples:[{input:'1 2 3',expected:'6'},{input:'10 -4 7',expected:'13'}],status:'PENDING' },
 { id:13,type:'knowledge',title:'如何理解事务隔离？',difficulty:2,description:'请说明事务隔离级别与并发异常之间的关系。',status:'PENDING' }
];
(async () => {
 const server = process.env.PRACTICE_QA_URL ? null : require('node:child_process').spawn(process.execPath, [path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5184','--strictPort'], {cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});
 let browser;
 try {
  for(let n=0;n<60;n++){try{if((await fetch(base)).ok)break}catch{} if(n===59)throw new Error('Local preview unavailable');await new Promise(r=>setTimeout(r,250));}
  browser = await chromium.launch({channel:'msedge', headless:true});
 try {
  const context = await browser.newContext({viewport:{width:1440,height:900}});
  await context.addInitScript(() => { for(const [key,value] of Object.entries({token:'practice-qa-fixture',userId:'21',role:'STUDENT',username:'验收学生'})) localStorage.setItem(key,value) });
  const p = await context.newPage(); const errors=[]; p.on('pageerror',e=>{errors.push(e.message);console.error('PAGE ERROR',e.message)});
  let fail=false, delay=0, count=0, sessionMode='normal', task=null;
  await context.route('**/api/**', async route => {
   const url = new URL(route.request().url()).pathname;
   if (!url.startsWith('/api/')) return route.continue();
   let data = {};
   if(url.endsWith('/practice/catalog'))data={ready:true,companies:[],codingCoverage:[]};
   else if(url.endsWith('/judge/capability')) data={ready:true,languages:{java:'Java (OpenJDK 17)'},notice:''};
   else if(url.endsWith('/tasks') && route.request().method()==='GET')data=task?[task]:[];
   else if(url.endsWith('/tasks') && route.request().method()==='POST') {
    count++;if(fail)return route.fulfill({json:{code:500,message:'判题服务暂不可用，请稍后重试'}});
    task={id:count,questionId:12,status:'QUEUED',operation:route.request().postDataJSON().operation,result:{status:'QUEUED',submitted:false}};data=task;
   } else if(url.startsWith('/api/practice/tasks/')) {
    if(delay)await new Promise(r=>setTimeout(r,delay));
    const result={status:'COMPILE_ERROR',submitted:task.operation==='submit',compileOutput:'missing symbol',cases:[{name:'用例 1',input:'1 2 3',expected:'6',output:'',status:'编译错误'}]};task={...task,status:'COMPILE_ERROR',result};data=task;
    if(result.submitted){questions[1].status='ANSWERED';questions[1].latestSubmission={id:count,status:'COMPILE_ERROR',result};}
   } else if(url === '/api/practice/sessions/10' && sessionMode==='load-error') return route.fulfill({contentType:'application/json',body:JSON.stringify({code:500,message:'题单加载失败，请重试'})});
   else if(url === '/api/practice/sessions/10') data = {id:10,topic:'综合高频',role:{name:'Java 后端开发工程师'},trainingMode:sessionMode,currentIndex:0,status:'ONGOING',questionList:sessionMode==='empty' ? [] : questions,submissions:[]};
   else if(url.match(/\/questions\/\d+\/(run|submit|answers)$/)) {
    count++; if(delay) await new Promise(r=>setTimeout(r,delay));
    if(fail) return route.fulfill({contentType:'application/json',body:JSON.stringify({code:500,message:'判题服务暂不可用，请稍后重试'})});
    data = url.endsWith('/answers') ? {submissionId:count,score:60,passed:true,message:'回答已记录',referenceAnswer:'哈希表通过哈希定位，并处理冲突和扩容。',matchedKeywords:['哈希']} : {submissionId:url.endsWith('/submit') ? count : null,passed:false,status:'Compilation Error',message:'编译错误，请修改代码',compileOutput:'missing symbol',cases:[{name:'用例 1',input:'1 2 3',expected:'6',output:'',status:'Compilation Error'}]};
   } else if(url.endsWith('/favorite')) data={favorite:true};
   else if(url.endsWith('/me')) data={id:21,username:'验收学生',role:'STUDENT'};
   else if(url.endsWith('/sessions') && route.request().method()==='POST') data={id:10};
   else if(url.endsWith('/job/list')) data=[{id:1,code:'BE-JAVA',name:'Java后端开发工程师',family:'后端开发',keywords:'["Java","JVM"]'}];
   else if(url.endsWith('/practice/overview')) data={libraryCounts:{},recentSession:null};
   return route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})});
  });
  const btn = name => p.getByRole('button',{name,exact:true});
  const settle = () => p.waitForTimeout(120);
  const open = async () => {await p.goto(base+'/learning/session/10');try{await p.locator('.question-content h2').waitFor({timeout:10000})}catch(error){console.error('PAGE BODY',await p.locator('body').innerText());await p.screenshot({path:path.join(out,'failure.png')});throw error}await settle()};
  const bounds = async () => { const value={}; for(const key of ['.practice-header','.question-pane','.answer-pane','.practice-footer','.primary']) value[key]=await p.locator(key).boundingBox(); return value };
  const overflow = async () => assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'horizontal overflow');
  await open();
  assert(await btn('上一题').isDisabled());
  await p.getByRole('textbox',{name:'文字回答'}).fill('这是第一题的草稿：哈希表原理。');
  await settle();await p.screenshot({path:path.join(out,'knowledge-1440.png')});
  await p.locator('.question-content').evaluate(e=>e.scrollTop=180);
  const normalBounds=await bounds();
  await btn('下一题').click(); await p.locator('.monaco-editor').waitFor(); await settle();
  const codingBounds=await bounds();
  for(const selector of Object.keys(normalBounds)) for(const prop of ['x','y','width','height']) assert(Math.abs(normalBounds[selector][prop]-codingBounds[selector][prop])<2, selector+' '+prop+' changed across types');
  await p.screenshot({path:path.join(out,'coding-1440.png')});
  assert.equal(await p.locator('.example').count(),2);
  await p.locator('.monaco-editor textarea.inputarea').focus();await p.keyboard.press('Control+A');
  await p.keyboard.insertText('public class Main {\n' + '    // qa preserved long code\n'.repeat(50) + '}');await p.waitForTimeout(500);
  assert((await p.evaluate(()=>JSON.parse(localStorage.getItem('offerpilot:practice:21:10')).questions[12].code)).includes('qa preserved long code'));
  await btn('运行代码').click(); await p.locator('.result-heading').getByText('编译错误',{exact:true}).waitFor();
  assert((await p.locator('.progress').innerText()).includes('已提交 0 题'));
  await p.getByText('missing symbol',{exact:true}).waitFor();
  fail=true; await btn('提交答案').click(); await p.locator('.result-content .error').first().waitFor();
  assert((await p.locator('.progress').innerText()).includes('已提交 0 题'));
  fail=false; await btn('重试').click(); await p.getByText('已提交 · 编译错误',{exact:true}).waitFor();
  assert((await p.locator('.progress').innerText()).includes('已提交 1 题'));
  await btn('下一题').click(); await p.getByRole('textbox',{name:'文字回答'}).waitFor();
  assert.equal(await p.getByRole('textbox',{name:'文字回答'}).inputValue(),'');
  assert(await p.locator('.result-panel').evaluate(e=>e.classList.contains('expanded')));
  assert(await btn('完成练习').isVisible());
  await btn('上一题').click();await p.locator('.monaco-editor').waitFor();await p.waitForFunction(()=>/qa\s+preserved\s+long\s+code/.test(document.querySelector('.view-lines')?.textContent || ''));await btn('上一题').click();
  assert.equal(await p.getByRole('textbox',{name:'文字回答'}).inputValue(),'这是第一题的草稿：哈希表原理。');
  assert.equal(await p.locator('.question-content').evaluate(e=>e.scrollTop),180);
  delay=500; await btn('提交答案').click(); await btn('下一题').click(); await p.waitForTimeout(700);
  assert((await p.locator('.question-content h2').innerText()).includes('计算一行整数之和'));
  await btn('上一题').click(); await btn('解析').click(); assert(await p.getByText('哈希表通过哈希定位，并处理冲突和扩容。',{exact:true}).isVisible()); delay=0;
  await btn('题目').click(); await p.reload(); await p.locator('.question-content h2').waitFor();
  assert.equal(await p.getByRole('textbox',{name:'文字回答'}).inputValue(),'这是第一题的草稿：哈希表原理。');
  // Keyboard resize and persistent geometry.
  const separator=p.getByRole('separator',{name:'调整题目宽度'});await separator.focus();await p.keyboard.press('ArrowRight');
  assert.equal(await separator.getAttribute('aria-valuenow'),'46');
  await p.getByRole('separator',{name:'调整结果面板高度'}).focus();await p.keyboard.press('ArrowUp');
  assert.equal(await p.getByRole('separator',{name:'调整结果面板高度'}).getAttribute('aria-valuenow'),'31');
  await btn('恢复比例').click();
  // Storage quota failure must retain the text and expose retry.
  await p.evaluate(()=>{window.qaSetItem=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key.startsWith('offerpilot:practice:'))throw new Error('quota');return window.qaSetItem.call(this,key,value)}});
  await p.getByRole('textbox',{name:'文字回答'}).fill('保存失败仍保留内容');await p.locator('.save-error').waitFor();
  assert.equal(await p.getByRole('textbox',{name:'文字回答'}).inputValue(),'保存失败仍保留内容');
  await p.evaluate(()=>Storage.prototype.setItem=window.qaSetItem); await btn('重试保存').click(); assert.equal(await p.locator('.save-error').count(),0);
  // Responsive widths: knowledge and coding preserve operation positions.
  for(const [width,height] of [[1280,720],[820,1024],[390,844]]) {
   await p.setViewportSize({width,height});await settle();await overflow();
   await p.screenshot({path:path.join(out,`knowledge-${width}.png`)});
   if(width<1051){await p.locator('.mobile-tabs').getByRole('button',{name:'作答',exact:true}).click();assert(await p.getByRole('textbox',{name:'文字回答'}).isVisible())}
   await btn('下一题').click();await settle();assert(await p.locator('.question-content h2').isVisible());
   if(width<1051)await p.locator('.mobile-tabs').getByRole('button',{name:'作答',exact:true}).click();
   await p.locator('.monaco-editor').waitFor(); await overflow();await p.screenshot({path:path.join(out,`coding-${width}.png`)});
   assert((await p.locator('.primary').boundingBox()).y+44<=height+1,'submit clipped');
   if(width<1051){await p.locator('.mobile-tabs').getByRole('button',{name:'结果',exact:true}).click();assert(await p.getByText('missing symbol',{exact:true}).isVisible())}
   await btn('上一题').click();
  }
  await btn('题目目录').click();assert(await p.getByRole('dialog').isVisible());await p.keyboard.press('Escape');assert(await btn('题目目录').evaluate(e=>e===document.activeElement));
  // Corrupt draft must fail safely without overwriting it.
  await p.addInitScript(()=>localStorage.setItem('offerpilot:practice:21:10','{corrupt'));await p.reload();await p.getByRole('button',{name:'重新加载',exact:true}).waitFor();
  assert.equal(await p.evaluate(()=>localStorage.getItem('offerpilot:practice:21:10')),'{corrupt');
  await p.evaluate(()=>localStorage.removeItem('offerpilot:practice:21:10'));sessionMode='interview';await btn('重新加载').click();await p.locator('.question-content h2').waitFor();
  assert.equal(await p.locator('.avatar-window,.dialogue-panel,.stage-indicator').count(),0);
  // Empty and failed sessions offer recovery instead of redirecting or blanking.
  sessionMode='empty';await p.evaluate(()=>localStorage.removeItem('offerpilot:practice:21:10'));
  await p.goto(base+'/learning');await p.goto(base+'/learning/session/10');
  // The init script intentionally corrupts storage on navigation; remove it and retry.
  await btn('重新加载').waitFor();
  await p.evaluate(()=>localStorage.removeItem('offerpilot:practice:21:10'));await btn('重新加载').click();await p.getByText('这个题单暂无题目，请返回重新选择。',{exact:true}).waitFor();
  sessionMode='load-error';await p.goto(base+'/learning');await p.goto(base+'/learning/session/10');await btn('重新加载').waitFor();assert(await p.getByText('题单加载失败，请重试',{exact:true}).isVisible());
  sessionMode='normal';questions[0].description='请解释 HashMap 的工作原理，并说明它如何处理哈希冲突。\n\n请从元素定位、冲突处理、扩容和线程安全四个方面组织你的回答。';
  await p.evaluate(()=>localStorage.removeItem('offerpilot:practice:21:10'));await btn('重新加载').click();await p.locator('.question-content h2').waitFor();
  // Final screenshots use representative content; long-content stress tests ran above.
  for(const [width,height] of [[1440,900],[1280,720],[820,1024],[390,844]]) {
   await p.setViewportSize({width,height});await settle();
   await p.screenshot({path:path.join(out,`knowledge-${width}.png`)});
   await btn('下一题').click();if(width<1051)await p.locator('.mobile-tabs').getByRole('button',{name:'作答',exact:true}).click();await p.locator('.monaco-editor').waitFor();await settle();
   await p.screenshot({path:path.join(out,`coding-${width}.png`)});await btn('上一题').click();
  }
  // Mobile input remains usable when the viewport shrinks (keyboard approximation).
  await p.setViewportSize({width:390,height:430});await p.locator('.mobile-tabs').getByRole('button',{name:'作答',exact:true}).click();await settle();await overflow();
  assert((await p.locator('.primary').boundingBox()).y+44<=430,'compact viewport submit clipped');
  // Entry creation awaits the server response and preserves the chosen type.
  const entry=await context.newPage();entry.on('pageerror',e=>errors.push(e.message));await entry.setViewportSize({width:390,height:844});await entry.goto(base+'/learning');
  await entry.getByRole('button',{name:'知识问答',exact:true}).click();
  const created=entry.waitForRequest(r=>r.url().endsWith('/api/practice/sessions') && r.method()==='POST');
  await entry.locator('.mobile-topic-list button').first().click();const request=await created;assert.equal(request.postDataJSON().trainingMode,'normal');assert.equal(request.postDataJSON().questionType,'knowledge');
  await entry.waitForURL('**/learning/session/10');await entry.locator('.question-content h2').waitFor();await entry.close();
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({fixture:true,checks:'mixed layout, draft restore, run/submit distinction, error retry, late response, quota, corrupt drafts, keyboard resize, directory focus, old sessions, responsive widths',screenshots:fs.readdirSync(out).filter(s=>s.endsWith('.png') && s!=='failure.png'),errors},null,2));
  console.log('PASS mixed practice browser verification; screenshots: '+out);
 } finally {await browser.close()}
 } finally {server?.kill()}
})().catch(e=>{console.error(e);process.exitCode=1});
