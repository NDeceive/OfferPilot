// Real local integration test. Creates uniquely named QA accounts and records.
// Run only against a local development database after applying the additive migration.
const assert=require('node:assert/strict'),cp=require('node:child_process'),fs=require('node:fs'),path=require('node:path');
const root=process.env.TEACHING_API||'http://127.0.0.1:8080/api';
if(!['127.0.0.1','localhost'].includes(new URL(root).hostname))throw Error('This fixture runner is limited to local development.');
const mysql=process.env.MYSQL_BIN||'C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe';
const sql=q=>cp.execFileSync(mysql,['--host=127.0.0.1','--user=root','--database=zhimian','--batch','--skip-column-names','--default-character-set=utf8mb4'],{env:{...process.env,MYSQL_PWD:process.env.DB_PASSWORD||'123456'},input:q,encoding:'utf8'}).trim();
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function api(token,url,method='GET',data,denied=false){const response=await fetch(root+url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(data!==undefined?{body:JSON.stringify(data)}:{})});const result=await response.json();if(denied){assert.notEqual(result.code,200,url+' unexpectedly allowed');return result}assert.equal(result.code,200,url+': '+result.message);return result.data}
async function ready(token,id){for(let n=0;n<45;n++){const a=await api(token,'/teaching/assignments/'+id);if(a.attempts[0]&&['READY','INVALID','FAILED'].includes(a.attempts[0].state)){assert.notEqual(a.attempts[0].state,'FAILED','Report generation failed');return a}await pause(500)}throw Error('Report generation timed out')}
const local=date=>new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,19);
(async()=>{
 const prefix='qa'+Date.now().toString(36),password='QaLinkage_'+Date.now().toString(36);
 const accounts=[];
 for(const suffix of ['t','s','x']){const username=prefix+suffix;await api(null,'/auth/register','POST',{username,password,nickname:'联动验收'+suffix});accounts.push({username})}
 // Only accounts created above are promoted; public registration cannot set roles.
 for(const index of [0,2]){const username=accounts[index].username;assert(/^qa[a-z0-9]+[tx]$/.test(username));sql("UPDATE sys_user SET role='TEACHER' WHERE username='"+username+"' AND role='STUDENT';")}
 for(const a of accounts)Object.assign(a,await api(null,'/auth/login','POST',{username:a.username,password}));
 const [teacher,student,stranger]=accounts;
 const classId=await api(teacher.token,'/teaching/classes','POST',{name:prefix+'联动班级',semester:'2026验收'});
 const classRow=(await api(teacher.token,'/teaching/classes')).find(c=>c.id===classId);
 await api(student.token,'/teaching/classes/join','POST',{inviteCode:classRow.inviteCode});
 await api(student.token,'/teaching/classes/join','POST',{inviteCode:classRow.inviteCode});
 const members=await api(teacher.token,'/teaching/classes/'+classId+'/members');assert.equal(members.length,1);assert.equal(members[0].state,'PENDING');
 await api(stranger.token,'/teaching/classes/'+classId+'/members','GET',undefined,true);
 await api(teacher.token,'/teaching/classes/'+classId+'/members/'+members[0].id+'/JOINED','PUT');
 const job=(await api(student.token,'/job/list'))[0];assert(job,'Existing job required');
 const spec={classId,title:prefix+'完整训练',description:'仅用于本地接口联动验收',jobId:job.id,questions:['请介绍你在项目中的职责。','请解释方案选择依据。'],durationSeconds:600,difficulty:2,minAttempts:1,maxAttempts:1,allowLate:false,startTime:local(new Date(Date.now()-60000)),deadline:local(new Date(Date.now()+3600000)),studentIds:[student.userId]};
 const taskId=await api(teacher.token,'/teaching/tasks','POST',spec);
 await api(teacher.token,'/teaching/tasks/'+taskId,'PUT',{...spec,description:'草稿编辑验收'});
 assert.equal((await api(teacher.token,'/teaching/tasks/'+taskId)).description,'草稿编辑验收');
 const discarded=await api(teacher.token,'/teaching/tasks','POST',{...spec,title:'仅用于删除草稿验收'});await api(teacher.token,'/teaching/tasks/'+discarded,'DELETE');await api(teacher.token,'/teaching/tasks/'+discarded,'GET',undefined,true);
 assert.equal((await api(student.token,'/teaching/assignments')).filter(a=>a.task.id===taskId).length,0,'Draft leaked to student');
 await api(teacher.token,'/teaching/tasks/'+taskId+'/remind','POST',undefined,true);
 await api(teacher.token,'/teaching/tasks/'+taskId+'/publish','POST');
 await api(teacher.token,'/teaching/tasks/'+taskId,'PUT',spec,true);await api(teacher.token,'/teaching/tasks/'+taskId,'DELETE',undefined,true);
 await api(teacher.token,'/teaching/tasks/'+taskId+'/publish','POST');
 const assignment=(await api(student.token,'/teaching/assignments')).find(a=>a.task.id===taskId);assert(assignment);
 const starts=await Promise.all([0,1].map(()=>api(student.token,'/interview/start','POST',{assignmentId:assignment.id,jobId:999999,durationSeconds:3600,difficulty:1})));assert.equal(starts[0].sessionId,starts[1].sessionId);assert.equal(starts[0].durationSeconds,600);assert.equal(starts[0].question.content,spec.questions[0]);
 const sessionId=starts[0].sessionId,answer='我负责需求拆解、接口设计和验证，依据约束选择方案，并通过测试核对结果。';
 await api(stranger.token,'/interview/'+sessionId+'/report-status','GET',undefined,true);
 const before=await api(student.token,'/interview/'+sessionId+'/next');assert.equal(before.question.id,-1,'Unanswered question skipped');
 await Promise.all([0,1].map(()=>api(student.token,'/interview/'+sessionId+'/answer','POST',{questionId:-1,answer})));
 const next=await api(student.token,'/interview/'+sessionId+'/next');assert.equal(next.question.id,-2);
 const resumed=await api(student.token,'/interview/start','POST',{assignmentId:assignment.id,jobId:job.id});assert.equal(resumed.sessionId,sessionId);assert.equal(resumed.question.id,-2);assert(resumed.remainingSeconds<=600);
 await api(student.token,'/interview/'+sessionId+'/answer','POST',{questionId:-1,answer},true);
 await api(student.token,'/interview/'+sessionId+'/answer','POST',{questionId:-2,answer});
 assert.equal((await api(student.token,'/interview/start','POST',{assignmentId:assignment.id,jobId:job.id})).finishable,true);
 await api(student.token,'/interview/'+sessionId+'/finish','POST');
 await api(student.token,'/interview/'+sessionId+'/finish','POST');
 const completed=await ready(student.token,assignment.id);assert.equal(completed.validCount,1);assert.equal(completed.completionStatus,'COMPLETED');assert.equal(completed.attempts.length,1);
 const reportId=completed.attempts[0].reportId;
 const report=await api(teacher.token,'/teaching/reports/'+reportId);assert.equal(report.messages.filter(m=>m.role==='CANDIDATE').length,2);
 await api(stranger.token,'/teaching/reports/'+reportId,'GET',undefined,true);
 await api(stranger.token,'/report/'+reportId,'GET',undefined,true);
 await api(teacher.token,'/teaching/reports/'+reportId+'/reviews','POST',{text:'请补充方案比较的具体依据。'});
 assert.equal((await api(student.token,'/teaching/reports/'+reportId+'/reviews')).length,1);
 await api(student.token,'/interview/start','POST',{assignmentId:assignment.id,jobId:job.id},true);
 await api(student.token,'/interview/'+sessionId,'DELETE',undefined,true);
 const notifications=await api(student.token,'/teaching/messages');assert.equal(notifications.filter(m=>m.title==='新的教学任务').length,1);assert(notifications.some(m=>m.title==='收到教师点评'));
 await api(student.token,'/teaching/messages/'+notifications[0].id+'/read','PUT');assert.equal((await api(student.token,'/teaching/messages'))[0].isRead,true);
 const summary=await api(teacher.token,'/teaching/summary');assert.equal(summary.completedTotal,1);assert.equal(summary.validAttemptTotal,1);assert.equal((await api(stranger.token,'/teaching/summary')).assignmentTotal,0);
 const legacy=await api(teacher.token,'/teacher/dashboard/overview');assert.equal(legacy.summary.trainedStudentCount,1);assert.equal((await api(stranger.token,'/teacher/dashboard/overview')).summary.studentTotal,0);
 const incompleteTask=await api(teacher.token,'/teaching/tasks','POST',{...spec,title:prefix+'提前结束',maxAttempts:2});await api(teacher.token,'/teaching/tasks/'+incompleteTask+'/publish','POST');const incomplete=(await api(student.token,'/teaching/assignments')).find(a=>a.task.id===incompleteTask);const short=await api(student.token,'/interview/start','POST',{assignmentId:incomplete.id,jobId:job.id});await api(student.token,'/interview/'+short.sessionId+'/finish','POST');const invalid=await ready(student.token,incomplete.id);assert.equal(invalid.validCount,0);assert.equal(invalid.attempts[0].state,'INVALID');
 // Simulate a persisted failure on this exact QA attempt, then exercise the real worker retry.
 assert(Number.isSafeInteger(short.sessionId));sql("UPDATE teaching_attempt SET state='FAILED' WHERE session_id="+short.sessionId+" AND state='INVALID';");await api(student.token,'/teaching/sessions/'+short.sessionId+'/retry','POST');const retried=await ready(student.token,incomplete.id);assert.equal(retried.attempts.length,1);assert.equal(retried.validCount,0);assert.equal(retried.attempts[0].state,'INVALID');
 assert.equal(sql('SELECT COUNT(*)-COUNT(DISTINCT module_code) FROM interview_module_score WHERE report_id='+retried.attempts[0].reportId), '0','Retry duplicated module scores');
 await api(teacher.token,'/teaching/tasks/'+incompleteTask+'/deadline','PUT',{deadline:local(new Date(Date.now()+7200000))});assert.equal((await api(student.token,'/teaching/assignments/'+incomplete.id)).task.version,3);
 await api(teacher.token,'/teaching/tasks/'+incompleteTask+'/end','POST');await api(student.token,'/interview/start','POST',{assignmentId:incomplete.id,jobId:job.id},true);
 const lateTask=await api(teacher.token,'/teaching/tasks','POST',{...spec,title:prefix+'截止边界'});await api(teacher.token,'/teaching/tasks/'+lateTask+'/publish','POST');const lateAssignment=(await api(student.token,'/teaching/assignments')).find(a=>a.task.id===lateTask);assert(Number.isSafeInteger(lateTask));sql('UPDATE teaching_task SET deadline=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id='+lateTask+';');await api(student.token,'/interview/start','POST',{assignmentId:lateAssignment.id,jobId:job.id},true);await api(teacher.token,'/teaching/tasks/'+lateTask+'/deadline','PUT',{deadline:local(new Date(Date.now()+3600000))});await api(student.token,'/interview/start','POST',{assignmentId:lateAssignment.id,jobId:job.id});await api(teacher.token,'/teaching/classes/'+classId+'/members/'+members[0].id+'/REMOVED','PUT');await api(student.token,'/interview/start','POST',{assignmentId:lateAssignment.id,jobId:job.id},true);await api(student.token,'/teaching/classes/join','POST',{inviteCode:classRow.inviteCode});await api(teacher.token,'/teaching/classes/'+classId+'/members/'+members[0].id+'/JOINED','PUT');
 await api(teacher.token,'/teaching/classes/'+classId+'/invite','POST');await api(student.token,'/teaching/classes/join','POST',{inviteCode:classRow.inviteCode},true);
 const archived=await api(teacher.token,'/teaching/classes','POST',{name:prefix+'归档验收',semester:'2026'});await api(teacher.token,'/teaching/classes/'+archived+'/archive','POST');await api(teacher.token,'/teaching/classes/'+archived,'PUT',{name:'无效修改',semester:'2027'},true);await api(teacher.token,'/teaching/classes/'+archived+'/invite','POST',undefined,true);
 fs.mkdirSync('output',{recursive:true});fs.writeFileSync('output/teaching-linkage-result.json',JSON.stringify({prefix,classId,taskId,assignmentId:assignment.id,sessionId,reportId,incompleteTask,accounts:accounts.map(({token,...a})=>a),checks:'join/approval, ownership, publication, concurrent start/answer, required questions, report/feedback, notifications, invalid results, maximum attempts, extension/end, invitation reset'},null,2));
 // Browser runner may load tokens from this transient fixture. Never print tokens.
 fs.writeFileSync('output/teaching-qa-auth.local.json',JSON.stringify({teacher,student,stranger}));
 console.log('PASS: real teaching/student linkage and access boundaries; fixture '+prefix);
})().catch(e=>{console.error(e.message);process.exitCode=1});
