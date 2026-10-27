// Browser fixtures use the shipped, audited content pack; no fabricated judging result.
const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),cp=require('node:child_process');
const pack=JSON.parse(fs.readFileSync('后端/src/main/resources/practice-bank/pilot.json','utf8'));
const out=path.resolve('output/practice-bank-qa');fs.mkdirSync(out,{recursive:true});
const source=[pack.questions.find(q=>q.type==='KNOWLEDGE'),pack.questions.find(q=>q.type==='CODING'),pack.questions.find(q=>q.type==='CULTURE'&&q.companyCode==='tencent'&&q.jobCodes.includes('BE-JAVA'))];
const questions=source.map((q,i)=>({id:i+11,type:q.type==='CODING'?'coding':'knowledge',subtype:q.type,title:q.title,description:q.content,difficulty:q.difficulty,source:q.source,status:'PENDING',gradingMode:q.type==='CODING'?null:'SELF_REVIEW',language:q.language,starter:q.starter,examples:q.examples||[]}));
(async()=>{
 const server=cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5194','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});
 let browser;
 try {
  for(let n=0;n<60;n++){try{if((await fetch('http://127.0.0.1:5194')).ok)break}catch{}if(n===59)throw Error('Preview unavailable');await new Promise(r=>setTimeout(r,250));}
  browser=await chromium.launch({channel:'msedge',headless:true});const ctx=await browser.newContext({viewport:{width:1440,height:900}});
  await ctx.addInitScript(()=>{for(const[k,v]of Object.entries({token:'practice-content-fixture',userId:'21',role:'STUDENT',username:'验收学生'}))localStorage.setItem(k,v)});
  const page=await ctx.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));let latest=null;
  const catalog={ready:true,companies:pack.companies.map(c=>({...c,id:c.code})),codingCoverage:[{jobCodes:['BE-JAVA','FE-WEB'],topics:['编程与算法','Java核心']}]};
  await ctx.route('**/api/**',async route=>{
   const url=new URL(route.request().url()).pathname;if(!url.startsWith('/api/'))return route.continue();let data={};
   if(url.endsWith('/tasks'))data=[];
   else if(url.endsWith('/judge/capability'))data={ready:false,languages:{},notice:'浏览器验收未接入真实执行器'};
   else if(url==='/api/practice/catalog')data=catalog;
   else if(url==='/api/practice/sessions/10')data={id:10,topic:'综合高频',role:{name:'Java后端开发工程师'},matching:{notice:'依据腾讯公开资料改编的企业情境，不代表企业真实面试题。'},questionList:questions.map(q=>({...q,...(q.id===13&&latest?{latestSubmission:latest,referenceAnswer:source[2].referenceAnswer}:{})})),submissions:latest?[{...latest,sessionQuestionId:13}]:[]};
   else if(url.endsWith('/questions/13/answers')){latest={id:101,status:'SELF_REVIEW',answerText:route.request().postDataJSON().answer,isCorrect:null,score:null};data={submissionId:101,submitted:true,status:'SELF_REVIEW',message:'回答已提交，请结合左侧参考分析自评。',referenceAnswer:source[2].referenceAnswer};}
   else if(url.endsWith('/job/list'))data=[{id:1,name:'Java后端开发工程师',code:'BE-JAVA',family:'后端开发',keywords:'["Java"]'}];
   else if(url.endsWith('/me'))data={id:21,username:'验收学生',role:'STUDENT'};
   else if(url.endsWith('/practice/overview'))data={libraryCounts:{},recentSession:null};
   await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data})});
  });
  await page.goto('http://127.0.0.1:5194/learning/session/10');try{await page.locator('.question-content h2').waitFor({timeout:10000})}catch(e){console.error(await page.locator('body').innerText(),errors);await page.screenshot({path:path.join(out,'failure.png')});throw e;}
  const selectors=['.practice-header','.question-pane','.answer-pane','.practice-footer','.primary'];
  const bounds=async()=>Promise.all(selectors.map(s=>page.locator(s).boundingBox()));
  await page.waitForTimeout(350);const initial=await bounds();await page.screenshot({path:path.join(out,'knowledge-1440.png')});
  await page.getByRole('button',{name:'下一题',exact:true}).click();await page.locator('.monaco-editor').waitFor();
  await page.waitForTimeout(350);const code=await bounds();for(let i=0;i<initial.length;i++)for(const key of ['x','y','width','height'])assert(Math.abs(initial[i][key]-code[i][key])<2,selectors[i]+' '+key+' '+initial[i][key]+' -> '+code[i][key]);
  await page.screenshot({path:path.join(out,'coding-1440.png')});
  await page.getByRole('button',{name:'下一题',exact:true}).click();await page.getByRole('textbox',{name:'文字回答'}).fill('先确认用户影响，收集事实，再协调责任与行动并验证结果。');
  await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByRole('button',{name:'解析',exact:true}).click();
  await page.getByText('本题采用参考分析与自评要点，不提供自动分数或通过结论。').waitFor();
  assert(!(await page.locator('.result-heading').innerText()).includes('需完善'));
  await page.getByRole('button',{name:'题目',exact:true}).click();await page.screenshot({path:path.join(out,'culture-1440.png')});
  await page.reload();await page.locator('.question-content h2').waitFor();assert(!(await page.locator('.result-heading').innerText()).includes('需完善'));
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'culture-390.png')});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.goto('http://127.0.0.1:5194/learning');await page.locator('.company-choice select').waitFor();
  await page.locator('.company-choice select').selectOption('腾讯');await page.getByText('腾讯：已有公开文化资料，按相关专项匹配情境题。').waitFor();
  await page.locator('.company-choice select').selectOption('美团');await page.getByText('美团：暂无已审核专项资料，按岗位和专项组题。').waitFor();
  await page.getByRole('button',{name:'JVM',exact:true}).click();assert.equal(await page.getByRole('button',{name:'混合题单',exact:true}).count(),0);
  await page.screenshot({path:path.join(out,'company-picker-390.png')});assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({fixture:true,checks:['shipped pack rendering','stable mixed layout','self review reload','company availability','real coding coverage','390px overflow'],errors},null,2));
  console.log('PASS bank content, company matching UI and honest self-review browser checks.');
 }finally{await browser?.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1});
