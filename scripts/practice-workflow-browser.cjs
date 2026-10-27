// Mock API UI verification only. Does not claim the real executor is installed.
const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const out=path.resolve('output/practice-workflow-qa');fs.mkdirSync(out,{recursive:true});
const questions=[
 {id:11,type:'knowledge',title:'如何设计接口的幂等性',description:'解释幂等键、事务与重复请求的处理边界。'.repeat(25),difficulty:2,gradingMode:'SELF_REVIEW',status:'PENDING'},
 {id:12,type:'coding',title:'计算整数之和',description:'读取两个整数，输出它们的和。Java 17，标准输入输出。',language:'java',starter:'import java.util.*;\npublic class Main {\n public static void main(String[] args) {\n  int n = ;\n }\n}',examples:[{input:'1 2',expected:'3'}],status:'PENDING'},
 {id:13,type:'knowledge',title:'如何权衡一致性与可用性',description:'结合具体故障情境解释设计取舍。',difficulty:2,gradingMode:'SELF_REVIEW',status:'PENDING'}];
(async()=>{
 const server=cp.spawn(process.execPath,[path.resolve('前端/node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5197','--strictPort'],{cwd:path.resolve('前端'),stdio:'ignore',windowsHide:true});let browser;
 try{
  for(let i=0;i<60;i++){try{if((await fetch('http://127.0.0.1:5197')).ok)break}catch{}if(i===59)throw Error('preview unavailable');await new Promise(r=>setTimeout(r,250));}
  browser=await chromium.launch({channel:'msedge',headless:true});const ctx=await browser.newContext({viewport:{width:1440,height:900}});
  await ctx.addInitScript(()=>{localStorage.setItem('token','workflow-fixture');localStorage.setItem('userId','21');localStorage.setItem('role','STUDENT');});
  let task=null,taskNumber=0,closed=false,systemFailure=false,apiFailures=false,reportProcessing=false;const submissions=[],errors=[];
  function report(){return {id:10,role:{name:'Java后端开发工程师'},company:'不限公司',topic:'综合专项',status:closed?(reportProcessing?'CLOSING':'FINISHED'):'ONGOING',questionType:'ALL',settlementStatus:reportProcessing?'PROCESSING':'READY',startedAt:'2026-10-09T10:00:00',finishedAt:'2026-10-09T10:12:00',statistics:{total:3,submitted:submissions.length,unsubmitted:3-submissions.length,codingPassed:0,codingFailed:1,codingEvaluated:1,selfReview:submissions.filter(s=>s.status==='SELF_REVIEW').length,objectiveCorrect:0,objectiveIncorrect:0,legacyReview:0},pendingCount:reportProcessing?1:0,systemFailureCount:0,questionList:questions.map(q=>({...q,latestSubmission:submissions.find(s=>s.sessionQuestionId===q.id),referenceAnswer:'参考分析：说明设计依据与边界。'}))};}
  await ctx.route('**/api/**',async route=>{
   const p=new URL(route.request().url()).pathname;if(!p.startsWith('/api/'))return route.continue();let data={};
   if(p==='/api/practice/judge/capability')data={ready:true,languages:{java:'Java (OpenJDK 17.0.20.1)'},notice:''};
   else if(p==='/api/practice/sessions/10')data={id:10,status:closed?'FINISHED':'ONGOING',topic:'综合专项',role:{name:'Java后端开发工程师'},questionList:questions.map(q=>({...q,latestSubmission:submissions.find(s=>s.sessionQuestionId===q.id)})),submissions};
   else if(p==='/api/practice/sessions/10/tasks')data=task?[task]:[];
   else if(p.endsWith('/tasks') && route.request().method()==='POST'){
    const input=route.request().postDataJSON();task={id:++taskNumber,questionId:12,status:'QUEUED',operation:input.operation,result:{status:'QUEUED',submitted:false}};data=task;
   }else if(p.startsWith('/api/practice/tasks/')){
    if(apiFailures)return route.fulfill({status:503,json:{message:'fixture outage'}});
    await new Promise(r=>setTimeout(r,250));
    const result=systemFailure?{status:'SYSTEM_ERROR',submitted:false,message:'执行器异常，未计入答错。'}:{status:'COMPILE_ERROR',submitted:task.operation==='submit',submissionId:task.operation==='submit'?20:null,message:'编译错误，请修改代码。',compileOutput:'Main.java:4: error: expected\n    int n = ;\n            ^',stderr:'',stdout:'',compilerVersion:'Java (OpenJDK 17.0.20.1)',executedCount:1,totalCount:4,cases:[{name:'用例 1',status:'编译错误',hidden:false,input:'1 2',expected:'3',output:''}]};
    task={...task,status:result.status,result};data=task;
    if(result.submitted && !submissions.some(s=>s.id===20))submissions.unshift({id:20,sessionQuestionId:12,status:result.status,isCorrect:0,sourceCode:'public class Main { int n = ; }',createdAt:'2026-10-09T10:02:00',result});
   }else if(p.endsWith('/answers')){const qid=Number(p.split('/')[6]);data={submissionId:qid+100,status:'SELF_REVIEW',submitted:true,referenceAnswer:'参考分析：幂等键与事务边界。',message:'已提交，请结合参考分析自评。'};submissions.unshift({id:qid+100,sessionQuestionId:qid,status:'SELF_REVIEW',answerText:route.request().postDataJSON().answer,createdAt:'2026-10-09T10:01:00',result:data});}
   else if(p.endsWith('/finish')){closed=true;data=report();}
   else if(p.endsWith('/report'))data=report();
   else if(p.endsWith('/submissions'))data={items:submissions,total:submissions.length,page:1};
   else if(p.endsWith('/review'))data={state:route.request().postDataJSON().state};
   else if(p.endsWith('/favorite'))data={favorite:true};
   await route.fulfill({json:{code:200,data}});
  });
  const page=await ctx.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.goto('http://127.0.0.1:5197/learning/session/10');await page.locator('.question-content h2').waitFor();await page.waitForTimeout(200);
  const submitBefore=await page.getByRole('button',{name:'提交答案',exact:true}).boundingBox();
  await page.getByRole('textbox',{name:'文字回答'}).fill('用稳定的请求标识和数据库唯一约束实现幂等，明确事务范围。');await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByText('已提交，请结合参考分析自评。').waitFor();
  await page.screenshot({path:path.join(out,'knowledge-1440.png')});
  await page.getByRole('button',{name:'下一题',exact:true}).click();await page.locator('.monaco-editor').waitFor();
  const submitAfter=await page.getByRole('button',{name:'提交答案',exact:true}).boundingBox();assert(Math.abs(submitBefore.x-submitAfter.x)<2 && Math.abs(submitBefore.y-submitAfter.y)<2);
  await page.getByRole('button',{name:'运行代码',exact:true}).click();await page.getByRole('button',{name:'下一题',exact:true}).click();await page.waitForTimeout(650);assert((await page.locator('.result-heading').innerText()).includes('暂无结果'));
  await page.getByRole('button',{name:'上一题',exact:true}).click();await page.getByText('编译未通过',{exact:false}).waitFor();assert.equal(submissions.filter(s=>s.sessionQuestionId===12).length,0);
  assert(await page.locator('.result-panel').evaluate(el=>el.getBoundingClientRect().height>100));
  await page.screenshot({path:path.join(out,'compile-error-1440.png')});
  apiFailures=true;await page.getByRole('button',{name:'运行代码',exact:true}).click();await page.getByRole('button',{name:'刷新任务结果',exact:true}).waitFor();
  apiFailures=false;await page.getByRole('button',{name:'刷新任务结果',exact:true}).click();await page.getByText('编译未通过',{exact:false}).waitFor();assert.equal(submissions.filter(s=>s.sessionQuestionId===12).length,0);
  systemFailure=true;await page.getByRole('button',{name:'运行代码',exact:true}).click();await page.getByText('执行器异常，未计入答错。',{exact:false}).waitFor();assert.equal(submissions.filter(s=>s.sessionQuestionId===12).length,0);assert.equal(await page.getByRole('navigation',{name:'结果详情',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'刷新任务结果',exact:true}).count(),0);systemFailure=false;
  await page.getByRole('button',{name:'提交答案',exact:true}).click();await page.getByRole('button',{name:'提交答案',exact:true}).waitFor({state:'visible'});await page.waitForTimeout(800);assert.equal(submissions.filter(s=>s.sessionQuestionId===12).length,1);
  await page.reload();await page.getByText('编译未通过',{exact:false}).waitFor();assert(await page.locator('.judge-result').innerText().then(s=>s.includes('Main.java:4')));
  await page.getByRole('button',{name:'收起 ▾',exact:true}).count();
  await page.locator('.result-heading').click();await page.getByRole('button',{name:'运行代码',exact:true}).click();await page.waitForTimeout(650);assert(!(await page.locator('.result-panel').getAttribute('class')).includes('expanded'));
  await page.locator('.result-heading').click();await page.locator('.result-resizer').focus();await page.keyboard.press('ArrowUp');assert.equal(await page.locator('.result-resizer').getAttribute('aria-valuenow'),'31');
  for(const [width,height] of [[1280,720],[820,1180],[390,844]]){
   await page.setViewportSize({width,height});await page.waitForTimeout(200);if(width<=1050)await page.getByRole('navigation',{name:'工作区'}).getByRole('button',{name:'结果',exact:true}).click();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,`coding-${width}.png`)});
  }
  await page.setViewportSize({width:1440,height:900});reportProcessing=true;await page.getByRole('button',{name:'结束练习',exact:true}).click();await page.waitForURL('**/report');await page.getByText('结算处理中，还有任务未结束；当前统计不是最终结果。',{exact:false}).waitFor();reportProcessing=false;await page.getByRole('button',{name:'刷新状态',exact:true}).click();await page.getByRole('heading',{name:'逐题复盘'}).waitFor();await page.screenshot({path:path.join(out,'report-1440.png')});
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(200);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'report-390.png')});
  await page.goto('http://127.0.0.1:5197/learning/session/10?questionId=12');await page.getByRole('navigation',{name:'工作区'}).getByRole('button',{name:'作答',exact:true}).click();await page.locator('.monaco-editor').waitFor();assert.equal(await page.getByRole('button',{name:'提交答案',exact:true}).count(),0);
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({mockAPI:true,realExecutor:false,checks:['mixed layout','auto-open and user preference','run vs submit','late response ownership','refresh restores diagnostics','keyboard resize','1440/1280/tablet/390','report and read-only history','poll failure and resume','system failure not submitted','processing settlement refresh'],errors},null,2));console.log('PASS workflow UI and report fixtures; real executor NOT tested');
 }finally{await browser?.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1});
