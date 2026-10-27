// Real Vue UI with mock transport; database scoring is covered separately by ObjectivePracticeDatabaseTest.
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const pack = JSON.parse(fs.readFileSync('后端/src/main/resources/practice-bank/objective.json','utf8'));
const out = path.resolve('output/objective-practice-qa'); fs.mkdirSync(out,{recursive:true});
const base = 'http://127.0.0.1:5198';

(async () => {
  const server = cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5198','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});
  let browser;
  try {
    for(let i=0;i<60;i++) { try { if((await fetch(base)).ok) break; } catch {} if(i===59) throw Error('preview unavailable'); await new Promise(r=>setTimeout(r,250)); }
    browser = await chromium.launch({channel:'msedge',headless:true});
    const context = await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});
    await context.addInitScript(() => { localStorage.setItem('token','objective-ui-fixture');localStorage.setItem('userId','21');localStorage.setItem('role','STUDENT'); });
    const errors=[], submissions=[], received=[];
    let closed=false;
    const originals=['CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'].map(type=>pack.questions.find(q=>q.type===type && q.canonicalId.startsWith('objective-java-01')));
    const questions=originals.map((q,n)=>({id:11+n,questionType:q.type,type:q.type.toLowerCase(),title:q.title,description:q.content,options:q.options,difficulty:q.difficulty,gradingMode:'OBJECTIVE',status:'PENDING',source:q.source,constraints:[q.type==='MULTIPLE_CHOICE'?'选择全部正确选项；少选、多选均判错':'请选择一个答案']}));
    const catalog={ready:true,companies:[],codingCoverage:[],objectiveCoverage:pack.questions.map(q=>({type:q.type,jobCodes:q.jobCodes,topics:q.topics,difficulty:q.difficulty}))};
    const session=()=>({id:10,status:closed?'FINISHED':'ONGOING',questionType:'ALL',topic:'综合高频',role:{id:1,code:'BE-JAVA',name:'Java后端开发工程师'},questionList:questions.map(q=>{const latest=submissions.find(s=>s.sessionQuestionId===q.id);return {...q,latestSubmission:latest,referenceAnswer:latest?.result.referenceAnswer}}),submissions});
    await context.route('**/api/**', async route => {
      const p=new URL(route.request().url()).pathname, method=route.request().method();let data={};
      if (!p.startsWith('/api/')) return route.continue();
      if(p==='/api/practice/catalog') data=catalog;
      else if(p==='/api/job/list') data=[{id:1,code:'BE-JAVA',name:'Java后端开发工程师',family:'后端开发',keywords:'["Java"]'}];
      else if(p==='/api/practice/overview') data={libraryCounts:{favorites:0,mistakes:1,recent:1},recentSession:null};
      else if(p==='/api/practice/judge/capability') data={ready:false,notice:'客观题不依赖编程执行器。'};
      else if(p==='/api/practice/sessions' && method==='POST') { received.push(route.request().postDataJSON());data={id:10}; }
      else if(p==='/api/practice/sessions/10') data=session();
      else if(p.endsWith('/tasks')) data=[];
      else if(p.endsWith('/answers')) {
        const qid=Number(p.split('/')[6]), n=qid-11, answer=route.request().postDataJSON().answer;
        received.push({qid,answer});const original=originals[n], passed=answer===original.correctAnswer;
        data={submissionId:100+submissions.length,submitted:true,status:passed?'OBJECTIVE_CORRECT':'OBJECTIVE_INCORRECT',passed,correctAnswer:original.correctAnswer,referenceAnswer:original.referenceAnswer};
        submissions.unshift({id:data.submissionId,sessionQuestionId:qid,answerText:answer,status:data.status,isCorrect:passed,result:data});questions[n].status='ANSWERED';
      } else if(p.endsWith('/submissions')) {const qid=Number(p.split('/')[6]),items=submissions.filter(s=>s.sessionQuestionId===qid);data={items,total:items.length,page:1};}
      else if(p.endsWith('/favorite')) data={favorite:true};
      else if(p.endsWith('/review')) data=route.request().postDataJSON();
      await route.fulfill({json:{code:200,data}});
    });
    const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));
    await page.goto(`${base}/learning/session/10`);
    try { await page.getByRole('radio').first().waitFor({timeout:10000}); }
    catch(error) { await page.screenshot({path:path.join(out,'failure.png')});console.error('UI state:',page.url(),await page.locator('body').innerText(),errors);throw error; }
    assert.equal(await page.getByRole('textbox',{name:'文字回答'}).count(),0);
    assert.equal(await page.getByText('正确答案：A',{exact:true}).count(),0);
    await page.getByRole('radio').nth(1).check();await page.reload();await page.getByRole('radio').nth(1).waitFor();assert.ok(await page.getByRole('radio').nth(1).isChecked());
    await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案错误',{exact:true}).waitFor();
    await page.getByRole('radio').first().check();await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案正确',{exact:true}).waitFor();
    await page.reload();await page.getByText('答案正确',{exact:true}).waitFor();assert.ok((await page.locator('.judge-result').innerText()).includes('正确答案'));
    await page.waitForTimeout(450);await page.screenshot({path:path.join(out,'choice-1440.png')});
    await page.getByRole('button',{name:'下一题',exact:true}).click();await page.getByRole('checkbox').first().check();
    await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案错误',{exact:true}).waitFor();
    await page.getByRole('checkbox').nth(2).check();await page.reload();await page.getByRole('checkbox').nth(2).waitFor();assert.ok(await page.getByRole('checkbox').nth(2).isChecked());
    await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案正确',{exact:true}).waitFor();
    assert.ok(received.some(v=>v.qid===12&&v.answer==='A,C'));
    await page.screenshot({path:path.join(out,'multiple-1440.png')});
    await page.getByRole('button',{name:'下一题',exact:true}).click();await page.getByRole('radio').nth(1).check();
    await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('答案正确',{exact:true}).waitFor();assert.ok(received.some(v=>v.qid===13&&v.answer==='false'));
    await page.setViewportSize({width:390,height:844});await page.getByRole('navigation',{name:'工作区'}).getByRole('button',{name:'作答',exact:true}).click();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'judgment-390.png')});
    await page.getByRole('radio').nth(1).focus();await page.keyboard.press('ArrowLeft');assert.ok(await page.getByRole('radio').first().isChecked());
    closed=true;await page.reload();await page.getByRole('navigation',{name:'工作区'}).getByRole('button',{name:'作答',exact:true}).click();assert.ok(await page.getByRole('radio').first().isDisabled());assert.equal(await page.getByRole('button',{name:'提交答案',exact:true}).count(),0);
    await page.setViewportSize({width:1440,height:900});await page.goto(`${base}/learning`);
    await page.locator('.role-grid').getByRole('button',{name:/Java后端开发工程师/}).click();
    await page.getByRole('button',{name:'不限公司',exact:true}).click();
    for(const name of ['单选题','多选题','判断题']) assert.ok(await page.getByRole('button',{name,exact:true}).isVisible());
    await page.getByRole('button',{name:'单选题',exact:true}).click();
    await page.locator('.topic-grid').getByRole('button',{name:/Java核心/}).click();
    await page.getByRole('button',{name:'开始专项刷题 →',exact:true}).click();await page.waitForURL('**/learning/session/10');
    assert.ok(received.some(v=>v.questionType==='choice'&&v.questionCount===10&&v.topic==='Java核心'));
    await page.setViewportSize({width:390,height:844});await page.goto(`${base}/learning`);
    await page.getByRole('button',{name:'判断题',exact:true}).click();
    await page.locator('.mobile-topic-list').getByRole('button',{name:/Java核心/}).click();await page.waitForURL('**/learning/session/10');
    assert.ok(received.some(v=>v.questionType==='true_false'&&v.questionCount===10&&v.topic==='Java核心'));
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({mockAPI:true,realUI:true,checks:['single choice wrong/correct','multi-select partial/full','false judgment submission','draft refresh','result refresh','390px no overflow','keyboard radios','finished read-only','desktop catalog and size cap','mobile catalog and size cap'],errors},null,2));
    console.log('PASS objective UI: single/multiple/judgment, drafts, restored results, mobile, keyboard, read-only.');
  } finally {await browser?.close();server.kill();}
})().catch(error=>{console.error(error);process.exitCode=1});
