const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path')
const root=process.env.TEACHER_WEB||'http://127.0.0.1:5173',out=path.resolve('output/student-center-review')
fs.mkdirSync(out,{recursive:true})
;(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}})
 await context.addInitScript(()=>{localStorage.setItem('token','local-ui-test');localStorage.setItem('role','TEACHER');localStorage.setItem('userId','student-qa')})
 await context.route('**/api/**',route=>new URL(route.request().url()).pathname.startsWith('/src/')?route.continue():route.fulfill({json:{code:200,data:{nickname:'王老师',role:'TEACHER'}}}))
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message))
 const visit=async url=>{await page.goto(root+url);await page.locator('h1').waitFor();await page.waitForTimeout(300)}
 const student='/teacher/students/ci-software2502-42'
 try{
  await visit('/teacher/students?demo=1')
  assert.equal(await page.locator('.student-list-table tbody tr').count(),15)
  const model=await page.evaluate(async()=>{
   const m=await import('/src/services/studentCenter.js'),data=m.studentDataset(true),s=data.students.find(s=>s.id==='ci-software2502-42'),a=m.abilityDefinitions[0],snapshot=m.abilitySnapshot(data,s,a)
   const r={id:'r1',studentId:'test',position:'Java',model:'Java',version:'v1',valid:true,completedAt:'2026-10-01T12:00:00+08:00',dimensions:{逻辑表达:60}},t={id:'test',position:'Java'}
   const fixture={source:'live',now:data.now,records:[r]},one=m.abilitySnapshot(fixture,t,a)
   const two=m.abilitySnapshot({...fixture,records:[r,{...r,id:'r2',completedAt:'2026-10-02T12:00:00+08:00',dimensions:{逻辑表达:70}}]},t,a)
   const changed=m.abilitySnapshot({...fixture,records:[r,{...r,id:'r2',version:'v2',completedAt:'2026-10-02T12:00:00+08:00',dimensions:{逻辑表达:80}}]},t,a)
   const missing=m.abilitySnapshot({...fixture,records:[{...r,dimensions:{}}]},t,a)
   const role=m.abilitySnapshot({...fixture,records:[r,{...r,id:'r2',position:'Frontend',model:'Frontend',completedAt:'2026-10-02T12:00:00+08:00'}]},t,a)
   return {count:data.students.filter(s=>!s.legacy).length,samples:snapshot.records.length,current:snapshot.current,calculation:snapshot.calculationRecords.length,one:{current:one.current,target:one.target,status:one.growth.status,label:one.sampleLabel},two:two.current,changed:changed.records.length,changedValue:changed.current,missing:missing.current,role:role.current,safe:m.sourceReturn({returnTo:'https://example.com'}).path,live:m.studentDataset(false).students.length}
  })
  assert(model.count>170);assert(model.samples>=6);assert.equal(model.calculation,3);assert.equal(model.one.current,60);assert.equal(model.one.target,null);assert.equal(model.one.status,'数据不足');assert.equal(model.one.label,'最近一次评分');assert.equal(model.two,65);assert.equal(model.changed,1);assert.equal(model.changedValue,80);assert.equal(model.missing,null);assert.equal(model.role,null);assert.equal(model.safe,'/teacher/students');assert.equal(model.live,0)
  const semantics=await page.evaluate(async()=>{
    const m=await import('/src/services/studentCenter.js'),now=Date.parse('2026-10-03T15:00:00+08:00'),student={id:'s',position:'Java'},r={id:'r',studentId:'s',position:'Java',model:'Java',version:'v1',valid:true,score:60,abilityScores:[60,60,60,60,60],completedAt:'2026-10-01T12:00:00+08:00'};
    const data={now,tasks:[],records:[r,{...r,id:'invalid',valid:false,score:80,completedAt:'2026-10-02T12:00:00+08:00'}]},summary=m.studentSummary(data,student);
    const switched=m.studentSummary({...data,records:[r,{...r,id:'v2',version:'v2',score:70,completedAt:'2026-10-02T12:00:00+08:00'}]},student);
    const task={id:'t',type:'ABILITY',primaryAbility:'逻辑表达',publishedAt:r.completedAt,startTime:r.completedAt,deadline:'2026-10-05T23:59:00+08:00',executions:[{studentId:'s',completionStatus:'NOT_STARTED',results:[]}]};
    const tasks=[task,{...task,id:'second'},{...task,id:'completed',executions:[{studentId:'s',completionStatus:'COMPLETED',results:[]}]},{...task,id:'ended',deadline:'2026-10-01T23:59:00+08:00',manuallyEndedAt:r.completedAt},{...task,id:'other',primaryAbility:'沟通表达'}];
    return {last:summary.last,count:summary.count,reason:summary.growthReason,switched:switched.growthReason,matching:m.pendingAbilityTasks({now,tasks},'s','逻辑表达').length,first:m.reportComparison(r),model:m.reportComparison({...r,version:'v2'},r),invalid:m.reportComparison({...r,score:120},r),delta:m.reportComparison({...r,score:60.5},r)};
  })
  assert.equal(semantics.last,'2026-10-01T12:00:00+08:00');assert.equal(semantics.count,1);assert.match(semantics.reason,/仅1条/);assert.match(semantics.switched,/切换后/);assert.equal(semantics.matching,2);assert.equal(semantics.first.reason,'首次记录');assert.equal(semantics.model.reason,'评分口径不同');assert.equal(semantics.invalid.delta,null);assert.equal(semantics.delta.delta,.5)
  await page.getByRole('button',{name:'更多筛选',exact:true}).click()
  await page.getByLabel('训练报告表现变化',{exact:true}).selectOption('持续下降')
  await page.reload();await page.locator('.student-filter-chips').waitFor();assert.equal(await page.locator('.student-advanced').count(),0)
  await page.getByRole('button',{name:'移除持续下降',exact:true}).click();await page.locator('.student-list-table tbody tr').first().waitFor()
  await page.getByLabel('搜索',{exact:true}).fill('20250042');await page.waitForTimeout(200)
  assert.equal(await page.locator('.student-list-table tbody tr').count(),1)
  await page.getByRole('link',{name:'查看学生',exact:true}).click();await page.locator('.student-status').waitFor()
  assert.equal(await page.locator('.student-tabs button').count(),3);assert.equal(await page.locator('.student-ability-row').count(),4)
  assert.equal(await page.locator('.student-main-concern').getByRole('link',{name:/布置/}).count(),0);assert.equal(await page.getByRole('link',{name:'布置训练',exact:true}).count(),1)
  await page.getByRole('link',{name:'返回学生中心',exact:true}).click();await page.locator('.student-list-table').waitFor();assert.equal(await page.getByLabel('搜索',{exact:true}).inputValue(),'20250042')
  await visit(student+'?demo=1&tab=ability&ability=project_expression')
  assert.equal(await page.getByLabel('能力',{exact:true}).inputValue(),'project_expression')
  await page.getByRole('button',{name:'查看能力证据',exact:true}).click();await page.getByRole('dialog').waitFor();await page.waitForTimeout(250)
  assert.equal(await page.getByRole('dialog').locator('.student-record-list').first().locator('li').count(),3)
  assert.match(await page.getByRole('dialog').innerText(),/未提供可追溯的表现证据原文/)
  assert.equal(await page.getByRole('dialog').locator('.student-record-source dl').first().isVisible(),false);await page.getByRole('dialog').locator('.student-record-source summary').first().click();assert.equal(await page.getByRole('dialog').locator('.student-record-source dl').first().isVisible(),true);await page.getByRole('dialog').locator('.student-record-source summary').first().click()
  await page.screenshot({path:path.join(out,'C06-1440.png'),fullPage:true})
  await page.getByRole('dialog').getByRole('link',{name:'查看报告',exact:true}).first().click();await page.locator('.teacher-report-detail').waitFor();assert.match(await page.locator('.teacher-report-detail').innerText(),/当前报告未提供/)
  await page.getByRole('link',{name:'← 返回学生详情',exact:true}).first().click();await page.locator('.student-center').waitFor()
  await page.getByRole('button',{name:'查看能力证据',exact:true}).click()
  await page.getByRole('dialog').getByRole('link',{name:'查看已有任务（1）',exact:true}).click();await page.locator('.task-tabs').waitFor();await page.getByRole('link',{name:'返回学生详情',exact:true}).click();await page.getByLabel('能力',{exact:true}).waitFor()
  await page.getByLabel('能力',{exact:true}).selectOption('communication');await page.getByRole('button',{name:'查看能力证据',exact:true}).click()
  await page.getByRole('dialog').getByRole('link',{name:'布置沟通表达训练',exact:true}).click();await page.locator('.wizard-work').waitFor()
  assert.equal(await page.getByLabel('所属班级',{exact:true}).inputValue(),'software2502');assert.match(await page.locator('.selection-summary').innerText(),/1/)
  await page.getByRole('button',{name:'下一步',exact:true}).click();assert.equal(await page.getByLabel('主要训练能力').inputValue(),'沟通表达')
  await page.getByRole('link',{name:'返回学生详情',exact:true}).click();await page.locator('.student-center').waitFor()
  await visit('/teacher/students/ci-software2502-1?demo=1&tab=training&history=tasks')
  assert.match(await page.locator('.student-task-table tbody tr').filter({hasText:'逻辑表达专项训练'}).innerText(),/2次/)
  assert.equal(await page.locator('.student-task-table').count(),1);assert.equal(await page.locator('.student-history-table').count(),0)
  await page.getByRole('link',{name:'查看任务',exact:true}).first().click();await page.locator('.task-tabs').waitFor()
  await page.locator('.task-tabs').getByRole('link',{name:'学生执行',exact:true}).click();await page.locator('.execution-table').waitFor()
  await page.locator('.execution-table tbody tr').filter({hasText:'20250001'}).getByRole('button',{name:'学生详情',exact:true}).click();await page.locator('.student-center').waitFor()
  assert.match(page.url(),/sourceTaskId=/);await page.getByRole('link',{name:'返回来源任务',exact:true}).click();await page.locator('.task-tabs').waitFor()
  await visit(student+'?demo=1&tab=training');assert.equal(await page.getByLabel('训练形式',{exact:true}).count(),0);await page.getByRole('button',{name:'更多筛选',exact:true}).click();await page.getByLabel('训练形式',{exact:true}).selectOption('专项训练');await page.reload();await page.getByRole('button',{name:'移除专项训练',exact:true}).waitFor();assert.equal(await page.getByLabel('训练形式',{exact:true}).count(),0);await page.getByRole('button',{name:'移除专项训练',exact:true}).click();assert.match(await page.locator('.student-history-table').innerText(),/首次记录/)
  await visit(student+'?demo=1&tab=ability&ability=project_expression');const ownerKey='offerpilot:teacher:training:v1:student-qa',originalStore=await page.evaluate(k=>localStorage.getItem(k),ownerKey);await page.evaluate(async key=>{const m=await import('/src/services/studentCenter.js'),base=m.studentDataset(true).tasks.find(t=>t.type==='ABILITY'&&t.primaryAbility==='项目经历表达'&&t.executions.some(e=>e.studentId==='ci-software2502-42'&&e.completionStatus==='NOT_STARTED'));const store=JSON.parse(localStorage.getItem(key));for(const id of ['duplicate-one','duplicate-two'])store.tasks[id]={...base,id,title:'同能力待完成任务 '+id};localStorage.setItem(key,JSON.stringify(store))},ownerKey);await page.reload();await page.getByRole('link',{name:'查看已有任务（3）',exact:true}).click();await page.locator('.student-task-table').waitFor();assert.equal(await page.locator('.student-task-table tbody tr').count(),3);await page.locator('.student-filter-chips button').click();assert(await page.locator('.student-task-table tbody tr').count()>3);await page.evaluate(({key,value})=>localStorage.setItem(key,value),{key:ownerKey,value:originalStore});
  for(const width of [1440,390]){
   await page.setViewportSize({width,height:1000})
   for(const [label,url] of [['T06','/teacher/students?demo=1'],['T07-01',student+'?demo=1'],['T07-02',student+'?demo=1&tab=ability'],['T07-03',student+'?demo=1&tab=training'],['T07-tasks','/teacher/students/ci-software2502-1?demo=1&tab=training&history=tasks']]){
    await visit(url);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Page overflow: '+url);await page.screenshot({path:path.join(out,label+'-'+width+'.png'),fullPage:true})
   }
  }
  await visit('/teacher/students?demo=1');assert.equal(await page.locator('.student-desktop-list').isVisible(),false);await page.locator('.student-mobile-list details summary').first().click();assert(await page.locator('.student-mobile-list details').first().innerText().then(t=>t.includes('目标岗位')));assert(await page.locator('.student-mobile-list').evaluate(e=>e.scrollWidth<=e.clientWidth+1))
  await visit(student+'?demo=1&tab=ability');await page.getByRole('button',{name:'查看能力证据',exact:true}).click();await page.getByRole('dialog').waitFor();await page.keyboard.press('Escape');await page.getByRole('dialog').waitFor({state:'hidden'})
  await page.getByRole('button',{name:'查看能力证据',exact:true}).click();await page.getByRole('dialog').waitFor();await page.waitForTimeout(250);assert(await page.getByRole('dialog').evaluate(e=>e.scrollWidth<=e.clientWidth+1));await page.screenshot({path:path.join(out,'C06-390.png'),fullPage:true});await page.keyboard.press('Escape');await page.getByRole('dialog').waitFor({state:'hidden'})
  await visit(student+'?demo=1&tab=ability&ability=professional');assert.equal(await page.getByLabel('能力',{exact:true}).inputValue(),'professional');assert.equal(await page.locator('.student-ability-chart svg text').filter({hasText:'目标'}).count(),0)
  await page.getByRole('button',{name:'查看能力证据',exact:true}).click();await page.getByRole('dialog').getByRole('link',{name:'查看报告',exact:true}).first().click();await page.locator('.teacher-report-detail').waitFor();assert.match(await page.locator('.report-table tbody tr').filter({hasText:'专业知识'}).innerText(),/69.0\/100/)
  await page.getByRole('link',{name:'← 返回学生详情',exact:true}).first().click();await page.getByLabel('能力',{exact:true}).waitFor();assert.equal(await page.getByLabel('能力',{exact:true}).inputValue(),'professional')
  await visit('/teacher/students/ci-software2501-9?demo=1');assert.equal(await page.getByRole('link',{name:'布置训练',exact:true}).count(),0);assert.match(await page.locator('.student-heading').innerText(),/暂无法/)
  await visit('/teacher/students?demo=1');const storeKey='offerpilot:teacher:training:v1:student-qa',backup=await page.evaluate(k=>localStorage.getItem(k),storeKey);await page.evaluate(k=>localStorage.setItem(k,'broken'),storeKey);await page.reload();await page.getByRole('alert').waitFor();await page.evaluate(({key,value})=>value===null?localStorage.removeItem(key):localStorage.setItem(key,value),{key:storeKey,value:backup});await page.getByRole('button',{name:'重试读取',exact:true}).click();await page.locator('.student-desktop-list:visible,.student-mobile-list:visible').waitFor()
  await visit('/teacher/students/missing?demo=1');assert.match(await page.locator('h1').innerText(),/不存在或不在可查看范围/)
  await visit('/teacher/students');assert.equal(await page.locator('.student-list-table').count(),0);assert.match(await page.locator('.student-empty').innerText(),/尚未接入真实学生数据/)
  await visit(student);assert.equal(await page.locator('.student-ability-row').count(),0)
  await page.evaluate(async()=>{localStorage.setItem('role','STUDENT');const {default:router}=await import('/src/router/index.js');await router.push('/teacher/students?demo=1')});await page.waitForURL('**/home');assert(!page.url().includes('/teacher/students'))
  assert.deepEqual(errors,[]);console.log('Student center: data boundaries, version/role stages, filters, evidence/report, intervention prefill, task return, empty states, mobile and role guard passed.')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
