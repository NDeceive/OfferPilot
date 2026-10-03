const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const base=process.env.TEACHING_API||'http://127.0.0.1:8080/api',web=process.env.TEACHER_WEB||'http://127.0.0.1:5173';
const fixture=JSON.parse(fs.readFileSync('output/teacher-demo-data.json','utf8'));
async function api(token,path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});const result=await r.json();assert.equal(result.code,200,path+': '+result.message);return result.data;}
(async()=>{
 const teacher=await api(null,'/auth/login','POST',{username:fixture.owner.username,password:process.env.TEACHER_DEMO_PASSWORD||'123456'});
 const classes=await api(teacher.token,'/teaching/classes'),tasks=await api(teacher.token,'/teaching/tasks');
 assert.equal(classes.filter(c=>fixture.classes.some(f=>f.id===c.id)).length,3);
 assert.equal(tasks.filter(t=>fixture.tasks.some(f=>f.id===t.id)).length,7);
 for(const phase of ['ACTIVE','SCHEDULED','DRAFT','CLOSED','ENDED'])assert(tasks.some(t=>t.lifecycle===phase),'Missing lifecycle '+phase);
 for(const report of fixture.reports){const row=await api(teacher.token,'/teaching/reports/'+report.reportId);assert(row.report.reportId);assert(row.messages.length>0);}
 const student=await api(null,'/auth/login','POST',{username:fixture.students[0].username,password:process.env.STUDENT_DEMO_PASSWORD||'DemoStudy2026!'});
 const allocations=await api(student.token,'/teaching/assignments');assert(allocations.some(a=>a.validCount===2));assert(allocations.some(a=>a.attempts.some(p=>p.reportId)));
 const browser=await chromium.launch({channel:'msedge',headless:true});const errors=[];
 try{
  for(const width of [1440,390]){
   const context=await browser.newContext({viewport:{width,height:1000}});
   await context.addInitScript(auth=>{for(const key of ['token','userId','username','nickname','role'])localStorage.setItem(key,String(auth[key]));},teacher);
   await context.route('**/api/**',async route=>{const req=route.request(),url=new URL(req.url());if(!url.pathname.startsWith('/api/'))return route.continue();const response=await fetch(base+url.pathname.slice(4)+url.search,{method:req.method(),headers:{'Content-Type':'application/json',Authorization:'Bearer '+teacher.token},...(req.postData()?{body:req.postData()}:{})});await route.fulfill({status:response.status,contentType:'application/json',body:await response.text()});});
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
   for(const path of ['/teacher/dashboard','/teacher/classes','/teacher/students','/teacher/tasks','/teacher/analytics','/teacher/training-records','/teacher/reviews','/teacher/messages','/teacher/tasks/'+fixture.tasks[0].id,'/teacher/reports/'+fixture.reports[0].reportId]){
    await page.goto(web+path);await page.waitForTimeout(600);
    assert(!await page.locator('.error').count()||!(await page.locator('.error').first().isVisible()),path+' showed an error');
    const body=await page.locator('body').innerText();assert(!body.includes('无权查看')&&!body.includes('记录不存在'),path+' broken linkage');
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2),path+' overflow');
    if(path==='/teacher/classes')assert(body.includes('演示 · 软件工程就业班'),body.slice(-1800));
    if(path==='/teacher/tasks')assert(body.includes('演示 · Java 基础与接口设计'));
    if(path==='/teacher/classes'||path==='/teacher/analytics')await page.screenshot({path:'output/teacher-demo-'+path.split('/').pop()+'-'+width+'.png',fullPage:true});
   }
   await context.close();
  }
 }finally{await browser.close();}
 assert.deepEqual(errors,[]);console.log('PASS: demo API ownership, five task lifecycles, report/student linkage, and 20 desktop/mobile routes.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
