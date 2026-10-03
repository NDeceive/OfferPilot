// Local development fixture. Adds labelled demo records through real teaching APIs.
// Existing accounts/classes/tasks are never reset, replaced, or deleted.
const fs=require('node:fs'),cp=require('node:child_process'),assert=require('node:assert/strict');
const base=process.env.TEACHING_API||'http://127.0.0.1:8080/api';
if(!['localhost','127.0.0.1'].includes(new URL(base).hostname))throw Error('Demo seeding is restricted to localhost.');
const username=process.env.TEACHER_DEMO_USER||'teacher';
const teacherPassword=process.env.TEACHER_DEMO_PASSWORD||'123456';
const studentPassword=process.env.STUDENT_DEMO_PASSWORD||'DemoStudy2026!';
const manifestFile='output/teacher-demo-data.json';
const quote=value=>"'"+String(value).replaceAll("'","''")+"'";
function sql(query){return cp.execFileSync(process.env.MYSQL_BIN||'C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe',[
 '--host=127.0.0.1','--user='+(process.env.DB_USER||'root'),'--database='+(process.env.DB_NAME||'zhimian'),
 '--batch','--skip-column-names','--default-character-set=utf8mb4'
],{env:{...process.env,MYSQL_PWD:process.env.DB_PASSWORD||'123456'},input:query,encoding:'utf8'}).trim();}
async function api(token,path,method='GET',body){const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body!==undefined?{body:JSON.stringify(body)}:{})});const result=await response.json();assert.equal(result.code,200,path+': '+result.message);return result.data;}
const local=date=>new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,19);
const days=n=>local(new Date(Date.now()+n*86400000));
const names=['陈思远','林雨桐','王子涵','赵亦辰','周若宁','许嘉言','刘书瑶','张皓宇','李欣然','黄景行','吴梦琪','孙以安','郑星辰','何语晴','宋知夏','杨逸轩','谢沐阳','蒋清越','徐乐宁','唐嘉禾'];
const javaQuestions=['请介绍 Java 中 HashMap 的工作原理，以及在并发场景中的注意事项。','面对接口响应变慢，你会如何定位数据库、缓存与应用层的瓶颈？','请结合一个项目说明如何保证接口幂等性，并验证你的方案。'];
const projectQuestions=['请介绍你最熟悉的项目，以及你承担的具体职责。','项目中最困难的一次技术决策是什么？请说明方案比较和选择依据。','如果重新实现这个项目，你会优化哪些地方，如何验证改进效果？'];
const webQuestions=['请说明 Vue 组件之间如何传递数据，以及如何控制状态变化。','一个页面加载较慢时，你会从哪些方面定位并改善性能？','请举例说明如何设计表单校验、加载和失败后的重试体验。'];
const answers=[
 ['HashMap 使用哈希定位桶，出现冲突时比较键并使用链表或树结构。并发读写会带来可见性与结构安全问题，应根据业务使用 ConcurrentHashMap，并用并发测试验证。','我会先看接口耗时分布与请求链路，结合慢 SQL、执行计划和索引定位数据库瓶颈，再检查缓存命中率、线程池和外部服务。优化前后比较 P95 延迟与错误率，防止只看平均耗时。','我们给订单请求增加业务幂等键，并用数据库唯一约束保证同一业务只生成一份订单。事务内记录处理结果，重复请求返回原结果；通过并发请求和失败重试验证，不依赖仅在内存中加锁。'],
 ['我负责核心接口和数据库设计，先梳理需求、业务约束与异常路径，再通过单元测试和集成测试核对结果。','比较过数据库唯一约束与分布式锁。最终根据一致性要求和运维成本采用唯一约束，并在事务中保证更新原子性，减少依赖。','下一次会补齐监控和边界测试，记录关键链路耗时，设置性能基线，并通过回归测试、灰度验证和失败重试确认改动有效。'],
 ['组件通过 props 传递输入，用事件反馈变化，共享状态放入 Pinia。避免直接修改父级数据，异步更新设置明确的加载和失败状态。','先看网络瀑布和性能记录，分析资源体积、接口耗时与渲染开销，按需加载资源并避免无效重渲染。用相同设备和网络条件比较优化前后的指标。','我会在提交前校验必填项，提交中禁用重复操作，成功后展示确认结果，失败时保留用户输入并允许重试。服务端也校验权限与请求幂等性，防止只依赖前端。'],
 ['我知道大概原理，但对具体实现还不熟悉。','一般先检查日志，再看数据库是否有慢查询。','我会多做测试，发现问题后继续优化。']
];
(async()=>{
 const teacher=await api(null,'/auth/login','POST',{username,password:teacherPassword});
 assert(['TEACHER','ADMIN'].includes(teacher.role),'Configured owner must be a teacher.');
 const tokens=new Map(),students=[];
 for(let i=0;i<names.length;i++){
  const user='opdemo_s'+String(i+1).padStart(2,'0');
  const existing=sql('SELECT id FROM sys_user WHERE username='+quote(user)+';');
  if(!existing)await api(null,'/auth/register','POST',{username:user,password:studentPassword,nickname:names[i]});
  const student=await api(null,'/auth/login','POST',{username:user,password:studentPassword});
  assert.equal(student.role,'STUDENT');tokens.set(student.userId,student.token);students.push({userId:student.userId,username:user,name:names[i]});
 }
 const definitions=[{name:'演示 · 软件工程就业班',indexes:[0,1,2,3,4,5,6,7],pending:[18]},
  {name:'演示 · 前端开发提升班',indexes:[8,9,10,11,12,13],pending:[19]},
  {name:'演示 · 项目面试冲刺班',indexes:[14,15,16,17],pending:[]}];
 const classes=[];
 for(const definition of definitions){
  let row=(await api(teacher.token,'/teaching/classes')).find(c=>c.name===definition.name);
  if(!row){const id=await api(teacher.token,'/teaching/classes','POST',{name:definition.name,semester:'2026 秋季 · 演示'});row=(await api(teacher.token,'/teaching/classes')).find(c=>c.id===id);}
  for(const i of [...definition.indexes,...definition.pending]){
   let member=(await api(teacher.token,'/teaching/classes/'+row.id+'/members')).find(m=>m.studentId===students[i].userId);
   if(!member){await api(tokens.get(students[i].userId),'/teaching/classes/join','POST',{inviteCode:row.inviteCode});member=(await api(teacher.token,'/teaching/classes/'+row.id+'/members')).find(m=>m.studentId===students[i].userId);}
   if(definition.indexes.includes(i)&&member.state!=='JOINED')await api(teacher.token,`/teaching/classes/${row.id}/members/${member.id}/JOINED`,'PUT');
  }
  classes.push({...row,indexes:definition.indexes});
 }
 const jobs=await api(teacher.token,'/job/list'),java=jobs.find(j=>j.code==='BE-JAVA')||jobs[0],web=jobs.find(j=>j.code==='FE-WEB')||java;
 assert(java,'An active job bank is required.');
 const specs=[
  {title:'演示 · Java 基础与接口设计',class:0,job:java,questions:javaQuestions,min:1},
  {title:'演示 · 项目经历复盘（两次训练）',class:0,job:java,questions:projectQuestions,min:2},
  {title:'演示 · Vue 与前端性能优化',class:1,job:web,questions:webQuestions,min:1},
  {title:'演示 · 已截止的前端专项',class:1,job:web,questions:webQuestions,min:1,closed:true},
  {title:'演示 · 下周项目模拟面试',class:2,job:java,questions:projectQuestions,min:1,scheduled:true},
  {title:'演示 · 待发布的综合训练',class:2,job:java,questions:projectQuestions,min:1,draft:true},
  {title:'演示 · 已结束的阶段测评',class:0,job:java,questions:javaQuestions,min:1,ended:true}
 ];
 const tasks=[];
 for(const spec of specs){
  let task=(await api(teacher.token,'/teaching/tasks')).find(t=>t.title===spec.title&&t.classId===classes[spec.class].id);
  if(!task){const id=await api(teacher.token,'/teaching/tasks','POST',{classId:classes[spec.class].id,title:spec.title,
   description:'演示数据：用于展示教学任务、学生训练、有效次数、原始报告和教师点评的完整联动。不会代表真实学生成绩。',
   jobId:spec.job.id,questions:spec.questions,durationSeconds:900,difficulty:2,minAttempts:spec.min,maxAttempts:3,
   allowLate:false,startTime:days(spec.scheduled?3:-20),deadline:days(spec.scheduled?10:7),
   studentIds:classes[spec.class].indexes.map(i=>students[i].userId)});task=await api(teacher.token,'/teaching/tasks/'+id);}
  if(!spec.draft&&!task.publishedAt)await api(teacher.token,'/teaching/tasks/'+task.id+'/publish','POST');
  tasks.push({...task,spec});
 }
 const generated=[];
 async function complete(taskIndex,studentIndex,desiredCount,answerSet,invalid=false){
  const task=tasks[taskIndex],student=students[studentIndex],token=tokens.get(student.userId);
  const allocation=(await api(token,'/teaching/assignments')).find(a=>a.task.id===task.id);assert(allocation);
  for(let n=allocation.attempts.length;n<desiredCount;n++){
   const started=await api(token,'/interview/start','POST',{assignmentId:allocation.id,jobId:task.spec.job.id});
   let question=started.question;
   const count=invalid?1:task.spec.questions.length;
   for(let r=0;r<count;r++){
    await api(token,`/interview/${started.sessionId}/answer`,'POST',{questionId:question.id,answer:answers[answerSet][r]});
    if(r<count-1)question=(await api(token,`/interview/${started.sessionId}/next`)).question;
   }
   await api(token,`/interview/${started.sessionId}/finish`,'POST');
   let result;
   for(let wait=0;wait<90;wait++){
    result=await api(token,'/teaching/assignments/'+allocation.id);
    const attempt=result.attempts.find(a=>a.sessionId===started.sessionId);
    if(attempt&&['READY','INVALID','FAILED'].includes(attempt.state)){assert.notEqual(attempt.state,'FAILED');break;}
    await new Promise(resolve=>setTimeout(resolve,500));
   }
   const attempt=result.attempts.find(a=>a.sessionId===started.sessionId);assert(attempt.reportId,'Report was not generated');
   generated.push({sessionId:started.sessionId,reportId:attempt.reportId,taskId:task.id,studentId:student.userId,invalid});
  }
 }
 for(let i=0;i<5;i++)await complete(0,i,1,i<3?0:3);
 await complete(1,0,2,1);await complete(1,1,2,1);await complete(1,2,1,1);await complete(1,3,1,3,true);
 for(let i=8;i<12;i++)await complete(2,i,1,i<10?2:3);
 await complete(2,12,1,3,true);
 for(const i of [8,9])await complete(3,i,1,2);
 for(const i of [0,4])await complete(6,i,1,0);
 // Published task boundaries are changed only through the local fixture's exact IDs.
 for(const task of tasks){
  sql('UPDATE teaching_task SET created_at=DATE_SUB(NOW(),INTERVAL 22 DAY),published_at='+
   (task.spec.draft?'NULL':'DATE_SUB(NOW(),INTERVAL 20 DAY)')+' WHERE id='+task.id+' AND title='+quote(task.title)+';');
 }
 for(const row of classes){
  sql('UPDATE teaching_class SET created_at=DATE_SUB(NOW(),INTERVAL 30 DAY) WHERE id='+row.id+' AND name='+quote(row.name)+'; '+
   'UPDATE teaching_member SET joined_at=DATE_SUB(NOW(),INTERVAL 25 DAY) WHERE class_id='+row.id+" AND state='JOINED';");
 }
 const closed=tasks[3];assert(closed.spec.closed);sql('UPDATE teaching_task SET deadline=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id='+closed.id+' AND title='+quote(closed.title)+';');
 const ended=await api(teacher.token,'/teaching/tasks/'+tasks[6].id);if(!ended.endedAt)await api(teacher.token,'/teaching/tasks/'+ended.id+'/end','POST');
 // Spread fixture report dates over two weeks for useful historical views; no score edits.
 for(let i=0;i<generated.length;i++){
  const item=generated[i],ago=1+(i%14);
  const stamp=`DATE_SUB(NOW(),INTERVAL ${ago} DAY)`;
  sql(`UPDATE interview_session SET start_time=DATE_SUB(${stamp},INTERVAL 9 MINUTE),end_time=${stamp} WHERE id=${item.sessionId} AND user_id=${item.studentId}; UPDATE teaching_attempt SET started_at=DATE_SUB(${stamp},INTERVAL 9 MINUTE),submitted_at=${stamp} WHERE session_id=${item.sessionId}; UPDATE interview_report SET create_time=${stamp} WHERE id=${item.reportId} AND user_id=${item.studentId}; UPDATE interview_message SET create_time=DATE_SUB(${stamp},INTERVAL 5 MINUTE) WHERE session_id=${item.sessionId};`);
 }
 const reviews=['逻辑清晰，能说明方案选择依据。下一次请补充并发测试结果和性能指标。','能够结合实际项目解释技术取舍。建议进一步展开异常处理与回滚策略。','已完成本次训练，基础知识仍需巩固。建议先整理核心概念，再尝试给出具体例子。'];
 const allReports=[];
 for(const task of tasks.filter(t=>!t.spec.draft&&!t.spec.scheduled)){
  const detail=await api(teacher.token,'/teaching/tasks/'+task.id);
  for(const a of detail.assignments)for(const attempt of a.attempts)if(attempt.reportId)allReports.push({reportId:attempt.reportId,sessionId:attempt.sessionId,state:attempt.state,taskId:task.id});
 }
 for(let i=0;i<allReports.length;i++)if(i%3===0){const row=allReports[i];if(!(await api(teacher.token,`/teaching/reports/${row.reportId}/reviews`)).length)await api(teacher.token,`/teaching/reports/${row.reportId}/reviews`,'POST',{text:reviews[i%reviews.length]});}
 // Leave one resumable live attempt and one task entirely unstarted.
 const activeToken=tokens.get(students[5].userId),active=(await api(activeToken,'/teaching/assignments')).find(a=>a.task.id===tasks[1].id);
 if(active.attempts.length===0)await api(activeToken,'/interview/start','POST',{assignmentId:active.id,jobId:java.id});
 for(const task of [tasks[0],tasks[2]]){const prior=await api(teacher.token,'/teaching/messages');
  if(!prior.some(m=>m.link==='/teacher/tasks/'+task.id&&m.title==='演示数据已就绪'))sql('INSERT INTO teaching_message(user_id,title,body,link) VALUES ('+teacher.userId+",'演示数据已就绪','班级成员、训练报告与点评已准备，可以查看任务执行情况。',"+quote('/teacher/tasks/'+task.id)+');');
 }
 const summary=await api(teacher.token,'/teaching/summary');
 fs.mkdirSync('output',{recursive:true});
 fs.writeFileSync(manifestFile,JSON.stringify({owner:{userId:teacher.userId,username},classes:classes.map(({indexes,...c})=>c),students,tasks:tasks.map(t=>({id:t.id,title:t.title,classId:t.classId})),reports:allReports,summary},null,2));
 console.log(JSON.stringify({owner:username,demoClasses:classes.length,joinedStudents:18,pendingStudents:2,tasks:tasks.length,reports:allReports.length,summary,manifest:manifestFile},null,2));
})().catch(error=>{console.error(error.message);process.exitCode=1;});
