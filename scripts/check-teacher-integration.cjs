const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path')
const root=process.env.TEACHER_WEB||'http://127.0.0.1:5173',out=path.resolve('output/teacher-integration-review')
fs.mkdirSync(out,{recursive:true})
;(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true}),context=await browser.newContext({viewport:{width:1440,height:1000}})
 await context.addInitScript(()=>{localStorage.setItem('token','integration-qa');localStorage.setItem('role','TEACHER');localStorage.setItem('userId','integration-qa')})
 await context.route('**/api/**',r=>new URL(r.request().url()).pathname.startsWith('/src/')?r.continue():r.fulfill({json:{code:200,data:{nickname:'王老师',role:'TEACHER',username:'teacher'}}}))
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message))
 const visit=async url=>{await page.goto(root+url);await page.locator('h1').waitFor();await page.waitForTimeout(200)}
 try{
  await visit('/teacher/dashboard?demo=1')
  const result=await page.evaluate(async()=>{
   const d=await import('/src/services/teacherData.js'),w=await import('/src/services/teacherWorkspace.js'),s=await import('/src/services/studentCenter.js'),a=await import('/src/services/analytics.js'),c=await import('/src/services/classInsights.js'),t=await import('/src/services/trainingTasks.js'),q=await import('/src/services/teacherContext.js'),m=await import('/src/services/teacherModel.js')
   const all=d.teacherDataset(),workspace=w.getDemoWorkspace(),student=s.studentDataset(true),analytics=a.analyticsDataset(true),classData=c.getClassDemo(),ids=all.students.map(s=>s.id).sort()
   const target=all.students.find(s=>s.account==='normal'),records=all.records.filter(r=>r.studentId===target.id)
   const fixture=[{id:'old',studentId:target.id,position:target.position,model:'model',version:'v1',valid:true,completedAt:'2026-10-01T12:00:00+08:00',dimensions:{逻辑表达:40},common:{逻辑表达:40}},{id:'new',studentId:target.id,position:target.position,model:'model',version:'v2',valid:true,completedAt:'2026-10-02T12:00:00+08:00',dimensions:{逻辑表达:90},common:{逻辑表达:90}}]
   const snap=s.abilitySnapshot({...all,records:fixture},target,s.abilityDefinitions[0]),shared=q.currentAbility(fixture,target,'逻辑表达')
   const draft=t.newDraft({classId:target.classId,students:target.id,ability:'逻辑表达'});Object.assign(draft,{title:'统一实体联动验收任务',deadline:'2026-10-10T18:00',mainQuestionCount:1,questions:[{id:'q',text:'请阐述你的项目思路'}]});const task=t.publishDraft(draft)
   const after=d.teacherDataset(),classAfter=c.getClassDemo(),classTask=c.taskRows(classAfter,target.classId,{period:'30d'}).find(r=>r.id===task.id),workspaceTask=w.getDemoWorkspace().tasks.find(r=>r.id===task.id)
   const overdue=after.tasks.find(t=>!t.manuallyEndedAt&&t.executions.some(e=>t.deadline<new Date(after.now).toISOString()&&e.completionStatus!=='COMPLETED'))
   if(overdue)t.extendDeadline(overdue,'2026-10-20T18:00','联动延期验证')
   const scope={class:target.className,period:'7d'},filtered=m.filterData(workspace,q.normalizeTeacherQuery(scope)),report=records.find(r=>r.type==='AI虚拟面试'&&r.result==='待点评')
   return {ids,workspaceIds:workspace.students.map(s=>s.id).sort(),studentIds:student.students.map(s=>s.id).sort(),analyticsIds:analytics.students.map(s=>s.id),uniqueRecords:new Set(after.records.map(r=>r.id)).size===after.records.length,missingTasks:workspace.tasks.filter(t=>!all.tasks.some(a=>a.id===t.id)).length,shared:shared.current,snapshot:snap.current,taskId:task.id,target:target.id,classId:target.classId,className:target.className,classTaskTotal:classTask.total,workspaceTaskTotal:workspaceTask.completed+workspaceTask.ongoing+workspaceTask.notStarted+workspaceTask.overdue,studentHasTask:s.studentTasks(after,target.id).some(r=>r.task.id===task.id),overdueCleared:!overdue||!w.getDemoWorkspace().overdue.some(r=>r.taskId===overdue.id),scoped:filtered.students.every(s=>s.classId===target.classId),reportId:report.reportId,pending:workspace.pending.length}
  })
  assert.deepEqual(result.ids,result.workspaceIds);assert.deepEqual(result.ids,result.studentIds);assert(result.analyticsIds.every(id=>result.ids.includes(id)));assert(result.uniqueRecords);assert.equal(result.missingTasks,0);assert.equal(result.shared,90);assert.equal(result.snapshot,90);assert.equal(result.classTaskTotal,1);assert.equal(result.workspaceTaskTotal,1);assert(result.studentHasTask&&result.overdueCleared&&result.scoped)
  await visit('/teacher/dashboard?demo=1&class='+encodeURIComponent(result.className));await page.getByRole('link',{name:/统一实体联动验收任务/}).waitFor();await page.getByRole('link',{name:/统一实体联动验收任务/}).click();await page.locator('.task-tabs').waitFor();assert(page.url().includes(result.taskId))
  const origin='/teacher/training-records?demo=1&classId='+result.classId+'&period=7d&sort=oldest&page=2'
  await visit('/teacher/reports/'+result.reportId+'?demo=1&returnTo='+encodeURIComponent(origin));await page.getByRole('heading',{name:'教师点评',exact:true}).waitFor();await page.getByLabel('新增点评',{exact:true}).fill('请结合已有报告原文梳理项目决策依据。');await page.getByRole('button',{name:'保存演示点评',exact:true}).click();await page.getByText('点评已保存到本机演示资料；待点评状态已更新，真实报告未修改。').waitFor()
  const reviewed=await page.evaluate(async id=>{const d=await import('/src/services/teacherData.js');const data=d.workspaceDataset();return {pending:data.pending.length,history:data.records.find(r=>r.reportId===id).reviewHistory.length}},result.reportId);assert.equal(reviewed.pending,result.pending-1);assert.equal(reviewed.history,1)
  await page.getByRole('link',{name:'← 返回来源页面',exact:true}).click();await page.waitForURL('**/teacher/training-records?**');assert.equal(new URL(page.url()).pathname+new URL(page.url()).search,origin)
  for(const width of [1440,390]){await page.setViewportSize({width,height:1000});for(const [name,url] of [['dashboard','/teacher/dashboard?demo=1'],['report','/teacher/reports/'+result.reportId+'?demo=1'],['class','/teacher/classes/'+result.classId+'?demo=1'],['account','/teacher/account']]){await visit(url);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),url+' overflow');await page.screenshot({path:path.join(out,name+'-'+width+'.png'),fullPage:true})}}
  await visit('/teacher/reports?demo=1');assert.equal(new URL(page.url()).pathname,'/teacher/analytics')
  await visit('/teacher/reports/missing?demo=1');await page.getByRole('heading',{name:'未找到报告',exact:true}).waitFor();await visit('/teacher/reports/'+result.reportId);await page.getByRole('heading',{name:'报告暂不可访问',exact:true}).waitFor()
  assert.deepEqual(errors,[]);console.log('Teacher integration: canonical IDs, version stages, publication, deadline propagation, report review/queue, exact return, legacy redirect, mobile and unavailable states passed.')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
