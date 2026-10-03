const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const base='http://127.0.0.1:8080/api',web='http://127.0.0.1:5173',fixture=JSON.parse(fs.readFileSync('output/teacher-demo-data.json','utf8'));
async function api(token,path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});const result=await r.json();assert.equal(result.code,200,path);return result.data}
(async()=>{
 const teacher=await api(null,'/auth/login','POST',{username:'teacher',password:process.env.TEACHER_DEMO_PASSWORD||'123456'}),emptyTeacher=await api(null,'/auth/login','POST',{username:'admin',password:process.env.ADMIN_DEMO_PASSWORD||'123456'});
 const browser=await chromium.launch({channel:'msedge',headless:true}),errors=[];
 let routes=0;
 try{
  for(const width of [1440,1024,390,360])for(const empty of [false,true]){
   const auth=empty?emptyTeacher:teacher,context=await browser.newContext({viewport:{width,height:1000}});
   await context.addInitScript(a=>{for(const key of ['token','userId','username','nickname','role'])localStorage.setItem(key,String(a[key]));},auth);
   await context.route('**/api/**',async route=>{const req=route.request(),url=new URL(req.url());if(!url.pathname.startsWith('/api/'))return route.continue();const response=await fetch(base+url.pathname.slice(4)+url.search,{method:req.method(),headers:{'Content-Type':'application/json',Authorization:'Bearer '+auth.token},...(req.postData()?{body:req.postData()}:{})});await route.fulfill({status:response.status,contentType:'application/json',body:await response.text()})});
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
   const paths=['/teacher/messages','/teacher/dashboard','/teacher/classes','/teacher/students','/teacher/tasks','/teacher/analytics','/teacher/reviews','/teacher/training-records','/teacher/account',...(empty?[]:['/teacher/classes/'+fixture.classes[0].id,'/teacher/classes/'+fixture.classes[0].id+'/members','/teacher/students/'+fixture.students[0].userId,'/teacher/tasks/'+fixture.tasks[0].id,'/teacher/reports/'+fixture.reports[0].reportId])];
   for(const path of paths){
    await page.goto(web+path);await page.locator('main h1').first().waitFor();await page.waitForTimeout(400);await page.waitForFunction(()=>!document.querySelector('.live-loading'));
    const body=await page.locator('body').innerText();assert(!body.includes('记录不存在或无访问权限'),path+' broken ownership');
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),path+' overflow '+width);
    if(path.includes('analytics')||path.includes('dashboard')){for(const selector of ['.teaching-trend-chart svg','.teaching-pie-chart svg','.scatter-plot svg'])assert.equal(await page.locator(selector).count(),1);assert.equal(await page.locator('.live-panel').count(),6);if(empty)assert(body.includes('还没有训练提交'));else assert(await page.locator('.class-bar').count()>=3);}
    if(path==='/teacher/messages'){
     assert.equal(await page.locator('.mail-folders').count(),1);
     if(empty)assert(body.includes('消息会出现在这里'));
     else{assert(await page.locator('.mail-row').count()>0);assert(await page.locator('.mail-row').first().evaluate(e=>getComputedStyle(e).borderBottomStyle==='solid'));}
    }
    if((width===1440||width===390)&&['/teacher/messages','/teacher/dashboard','/teacher/analytics'].includes(path))await page.screenshot({path:`output/teaching-refined-${path.split('/').pop()}-${empty?'empty':'data'}-${width}.png`,fullPage:true});
    routes++;
   }
   if(!empty&&width===1440){
    await page.goto(web+'/teacher/messages');await page.getByRole('searchbox',{name:'搜索消息'}).fill('演示数据已就绪');await page.waitForTimeout(300);assert.equal(await page.locator('.mail-row').count(),2);
    await page.getByRole('checkbox',{name:'选择本页消息'}).check();await page.getByRole('button',{name:/标记所选为已读/}).click();await page.waitForTimeout(400);assert((await api(teacher.token,'/teaching/messages')).filter(m=>m.title==='演示数据已就绪').every(m=>m.isRead));
    await page.locator('.mail-open').first().click();assert(await page.locator('.mail-preview').isVisible());await page.locator('.mail-preview .primary').click();await page.waitForURL('**/teacher/tasks/*');assert(new URL(page.url()).pathname.startsWith('/teacher/tasks/'));
    await page.goto(web+'/teacher/analytics');await page.waitForSelector('.ability-bars');await page.getByLabel('筛选教学班级').selectOption(String(fixture.classes[0].id));assert.equal(await page.locator('.class-bar').count(),1);
    const scoreRows=await page.locator('.ability-bars>div').count();assert(scoreRows>0);
    await page.locator('.live-records tbody tr').first().getByRole('link',{name:'查看报告 →'}).click();await page.locator('.report-scorebars').waitFor();await page.getByRole('link',{name:'← 返回来源页面'}).click();await page.waitForSelector('.ability-bars');assert.equal(await page.getByLabel('筛选教学班级').inputValue(),String(fixture.classes[0].id));
   }
   await context.close();
  }
  // The same inbox and teaching styles must preserve student access and report linkage.
  const student=await api(null,'/auth/login','POST',{username:fixture.students[0].username,password:process.env.STUDENT_DEMO_PASSWORD||'DemoStudy2026!'});
  const assignments=await api(student.token,'/teaching/assignments');const assignment=assignments.find(a=>a.attempts.some(p=>p.reportId));assert(assignment);
  for(const width of [1440,390]){
   const context=await browser.newContext({viewport:{width,height:1000}});await context.addInitScript(a=>{for(const key of ['token','userId','username','nickname','role'])localStorage.setItem(key,String(a[key]));},student);
   const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
   for(const path of ['/my/classes','/my/tasks','/my/messages','/my/tasks/'+assignment.id,'/history/'+assignment.attempts.find(p=>p.reportId).reportId+'?assignmentId='+assignment.id]){
    await page.goto(web+path);await page.waitForTimeout(500);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),path+' student overflow');
    assert(!(await page.locator('body').innerText()).includes('无权查看'),path+' student linkage');routes++;
   }
   await context.close();
  }
  // Partial report failures keep execution charts available and offer a retry action.
  const context=await browser.newContext();await context.addInitScript(a=>{for(const key of ['token','userId','username','nickname','role'])localStorage.setItem(key,String(a[key]));},teacher);
  await context.route('**/api/teaching/reports/*',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:503,message:'报告服务暂不可用'})}));
  const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(web+'/teacher/analytics');await page.getByText('评分数据暂时无法加载').waitFor();assert.equal(await page.locator('.teaching-trend-chart svg').count(),1);assert(await page.getByRole('button',{name:'重新读取报告'}).isVisible());await context.close();
  assert.deepEqual(errors,[]);console.log('PASS: '+routes+' populated/empty routes at 1440/1024/390/360 px, inbox read/search/preview, real charts, filter return, partial-error recovery.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e.message);process.exitCode=1});
