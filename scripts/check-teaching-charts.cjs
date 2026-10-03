const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict');
const base='http://127.0.0.1:8080/api',web='http://127.0.0.1:5173';
async function api(token,path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});const result=await r.json();assert.equal(result.code,200,path);return result.data}
const dateKey=v=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(/[Z+]/.test(v)?v:v+'+08:00'));
(async()=>{
 const auth=await api(null,'/auth/login','POST',{username:'teacher',password:process.env.TEACHER_DEMO_PASSWORD||'123456'});
 const tasks=await api(auth.token,'/teaching/tasks'),details=await Promise.all(tasks.filter(t=>t.publishedAt).map(t=>api(auth.token,'/teaching/tasks/'+t.id)));
 const assignments=details.flatMap(t=>t.assignments),reports=assignments.flatMap(a=>a.attempts.filter(p=>p.reportId).map(p=>({...p,studentId:a.studentId}))),total=assignments.length;
 const browser=await chromium.launch({channel:'msedge',headless:true}),errors=[];
 async function context(width,user=auth){const c=await browser.newContext({viewport:{width,height:1000}});await c.addInitScript(a=>{for(const key of ['token','userId','username','nickname','role'])localStorage.setItem(key,String(a[key]));},user);return c}
 try{
  for(const width of [1440,1024,390,360]){
   const c=await context(width),page=await c.newPage();page.on('pageerror',e=>errors.push(e.message));await page.goto(web+'/teacher/activity');await page.waitForSelector('.teaching-pie-chart');
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2),'chart overflow '+width);
   const pie=page.locator('.teaching-pie-chart'),scatter=page.locator('.live-scatter');
   assert((await pie.innerText()).includes('共 '+total+' 人次分配'));
   const legend=pie.locator('.pie-legend button');assert.equal(await legend.count(),4);
   let pct=0;for(let i=0;i<4;i++)pct+=parseFloat(await legend.nth(i).locator('b').innerText());assert(Math.abs(pct-100)<.21,'sector percentages');
   const completed=assignments.filter(a=>a.completionStatus==='COMPLETED').length;
   assert((await legend.first().locator('strong').innerText()).startsWith(String(completed)));
   await pie.locator('svg g[role=button]').first().focus();await page.keyboard.press('Enter');assert(await page.locator('.pie-drilldown').isVisible());
   assert((await page.locator('.pie-drilldown').innerText()).includes('已完成'));assert(await page.locator('.pie-drilldown a').count()>0);
   const unique=new Set(assignments.map(a=>a.studentId)).size;assert((await scatter.locator('header').innerText()).includes(unique+' 名'));
   await scatter.locator('summary').click();assert.equal(await scatter.locator('tbody tr').count(),unique);
   const group=scatter.locator('svg g[role=button]').first();await group.focus();await page.keyboard.press('Space');assert(await scatter.locator('.chart-people a').count()>0);
   assert.equal(await scatter.locator('.scatter-regions button').count(),0,'all-task overview must not imply comparable ability regions');
   await page.getByLabel('筛选教学任务').selectOption(String(details.find(t=>t.assignments.length).id));await scatter.locator('.scatter-regions button').first().click();assert.equal(await scatter.locator('.scatter-regions button').first().getAttribute('aria-pressed'),'true');await page.getByLabel('筛选教学任务').selectOption('');
   const date=reports.find(r=>r.state==='READY').submittedAt,day=dateKey(date),rows=reports.filter(r=>dateKey(r.submittedAt)===day);
   await page.getByLabel('选择折点日期').selectOption(day);assert.equal(await page.locator('.live-records tbody tr').count(),rows.length);
   const reading=await page.locator('.trend-reading').innerText();assert(reading.includes('有效 '+rows.filter(r=>r.state==='READY').length+' 次'));assert(reading.includes('无效 '+rows.filter(r=>r.state==='INVALID').length+' 次'));
   const node=page.locator('.teaching-trend-chart g[role=button]').filter({has:page.locator('title')}).first();await node.focus();await page.keyboard.press('Enter');assert(await page.locator('.chart-record-filter').isVisible());
   await page.getByLabel('选择折点日期').selectOption(day);
   await page.locator('.live-records tbody tr').first().getByRole('link',{name:'查看报告 →'}).click();await page.locator('.report-scorebars').waitFor();await page.getByRole('link',{name:'← 返回来源页面'}).click();await page.waitForSelector('.teaching-pie-chart');assert.equal(await page.getByLabel('选择折点日期').inputValue(),day);
   await page.getByRole('button',{name:'清除日期筛选'}).click();assert.equal(await page.getByLabel('选择折点日期').inputValue(),'');
   await page.getByLabel('报告周期',{exact:false}).selectOption('90');await page.waitForSelector('.teaching-pie-chart');assert.equal(await page.locator('.teaching-trend-chart g[role=button]').count(),90);
   await page.getByLabel('筛选教学班级').selectOption(String(details[0].classId));const scoped=details.filter(t=>t.classId===details[0].classId).flatMap(t=>t.assignments);assert((await pie.innerText()).includes('共 '+scoped.length+' 人次分配'));
   assert((await scatter.locator('header').innerText()).includes(new Set(scoped.map(a=>a.studentId)).size+' 名'));
   await page.getByLabel('筛选教学班级').selectOption('');await page.getByLabel('报告周期',{exact:false}).selectOption('30');await page.waitForSelector('.teaching-pie-chart');
   await page.screenshot({path:`output/teacher-multicharts-${width}.png`,fullPage:true});
   if(width===1440||width===390){const captureStyle=await page.addStyleTag({content:'.topnav { display: none !important }'});await page.locator('.live-chart-grid').screenshot({path:`output/teacher-chart-overview-${width}.png`});await scatter.locator('svg g[role=button]').first().click();await scatter.screenshot({path:`output/teacher-scatter-detail-${width}.png`});await captureStyle.evaluate(e=>e.remove())}
   await c.close();
  }
  // A single populated sector must render a complete circle, not an empty SVG arc.
  const c=await context(1440);await c.route('**/api/teaching/tasks/*',async route=>{const url=new URL(route.request().url());if(!/^\/api\/teaching\/tasks\/\d+$/.test(url.pathname))return route.continue();const d=await api(auth.token,url.pathname.slice(4));d.assignments=d.assignments.map(a=>({...a,completionStatus:'COMPLETED'}));await route.fulfill({contentType:'application/json',body:JSON.stringify({code:200,data:d})})});const page=await c.newPage();await page.goto(web+'/teacher/activity');await page.waitForSelector('.teaching-pie-chart svg path');assert.equal(await page.locator('.teaching-pie-chart svg path').count(),1);assert((await page.locator('.teaching-pie-chart svg path').getAttribute('d')).includes('150,235'));await c.close();
  const admin=await api(null,'/auth/login','POST',{username:'admin',password:process.env.ADMIN_DEMO_PASSWORD||'123456'}),empty=await context(390,admin),ep=await empty.newPage();await ep.goto(web+'/teacher/activity');await ep.waitForSelector('.scatter-plot svg');assert.equal(await ep.locator('.teaching-trend-chart path,.teaching-pie-chart path,.scatter-plot g[role=button]').count(),0);assert(await ep.getByLabel('选择折点日期').isDisabled());await empty.close();
  assert.deepEqual(errors,[]);console.log('PASS: line values and keyboard drilldown, report return date, pie sectors and allocation links, scatter clustering/regions/student totals, class/90-day filters, single-sector and real empty states at four widths.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
