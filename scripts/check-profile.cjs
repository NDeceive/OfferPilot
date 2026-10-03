const {chromium} = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.PROFILE_API || 'http://localhost:8080';
const web = process.env.PROFILE_WEB || 'http://localhost:5173';
async function api(url, token, method='GET', data) {
  const result = await fetch(base+'/api'+url,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(data?{'Content-Type':'application/json'}:{})},body:data?JSON.stringify(data):undefined});
  return result.json();
}
(async()=>{
  const username='profile_'+Date.now().toString(36), password='ProfileTest826!';
  const registration=await api('/auth/register',null,'POST',{username,password,nickname:'档案测试用户'});
  assert.equal(registration.code,200,registration.message);
  const login=await api('/auth/login',null,'POST',{username,password});
  assert.equal(login.code,200);const auth=login.data, token=auth.token;
  const jobs=(await api('/job/list',token)).data;
  const chosen=jobs.find(j=>j.code==='FE-WEB')||jobs[1];
  assert(chosen);
  assert.equal((await api('/user/career-profile')).code,401);
  assert.notEqual((await api('/user/career-profile',token,'PUT',{targetJobId:-1})).code,200);
  assert.notEqual((await api('/user/profile',token,'PUT',{newPassword:'Changed826!',currentPassword:'wrong'})).code,200);
  const invalid=new FormData();invalid.append('file',new Blob(['not an image'],{type:'image/png'}),'bad.png');
  assert.notEqual((await (await fetch(base+'/api/user/avatar',{method:'POST',headers:{Authorization:'Bearer '+token},body:invalid})).json()).code,200);
  const browser=await chromium.launch({channel:'msedge',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1100}});const errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(a=>{for(const k of ['token','userId','username','nickname','role']) if(a[k]!=null)localStorage.setItem(k,a[k]);},auth);
  const out=path.resolve(__dirname,'../output/profile-review');fs.mkdirSync(out,{recursive:true});
  try {
    await page.goto(web+'/profile');await page.getByText('从一份简历开始').waitFor();
    await page.waitForTimeout(400);await page.screenshot({path:path.join(out,'profile-empty.png'),fullPage:true});
    await page.getByRole('button',{name:'编辑昵称',exact:true}).click();
    await page.getByLabel('昵称',{exact:true}).fill('林同学');await page.getByRole('button',{name:'保存昵称',exact:true}).click();
    await page.getByText('昵称已保存。',{exact:true}).waitFor();
    await page.getByLabel('默认目标岗位').selectOption(String(chosen.id));
    await page.getByLabel('求职阶段').selectOption('校招');
    await page.getByLabel('目标公司').fill('测试目标企业');
    await page.getByText('教育背景',{exact:false}).click();
    await page.getByLabel('学校',{exact:true}).fill('测试大学');await page.getByLabel('专业',{exact:true}).fill('软件工程');await page.getByLabel('毕业年份').fill('2027');
    await page.getByRole('button',{name:'保存求职目标',exact:true}).click();await page.getByText('求职目标已保存，下次准备时自动带入。').waitFor();
    await page.route('**/api/user/career-profile', async route=>{
      if(route.request().method()==='PUT')return route.fulfill({json:{code:500,message:'测试保存失败，请重试。'}});
      await route.fallback();
    });
    await page.getByLabel('目标公司').fill('未保存的新目标');await page.getByRole('button',{name:'保存求职目标',exact:true}).click();await page.getByText('测试保存失败，请重试。').waitFor();
    assert.equal((await api('/user/career-profile',token)).data.targetCompany,'测试目标企业');
    await page.unroute('**/api/user/career-profile');await page.getByLabel('目标公司').fill('测试目标企业');
    await page.getByRole('button',{name:'填写简历内容',exact:true}).click();
    const resumeText='教育背景：测试大学软件工程专业，2027 年毕业。项目经历：独立开发校园活动管理系统，使用 Java、Spring Boot 与 MySQL，负责接口设计、权限管理和数据库查询优化。';
    await page.getByLabel('简历内容',{exact:true}).fill(resumeText);await page.getByRole('button',{name:'保存并分析',exact:true}).click();
    await page.getByText('简历内容已保存，技能标签已重新分析。').waitFor({timeout:90000});
    await page.locator('.resume-panel input[type=file]').setInputFiles(path.join(__dirname,'fixtures/test-resume.docx'));
    await page.getByText('简历已更新，请核对内容与技能标签。').waitFor({timeout:90000});
    assert.equal((await api('/resume/file-profile',token)).data.filename,'test-resume.docx');
    await page.getByRole('button',{name:'编辑标签',exact:true}).click();await page.getByLabel('用逗号分隔技能（最多 30 个）').fill('Java，SQL');await page.getByRole('button',{name:'保存标签',exact:true}).click();await page.getByText('技能标签已保存。').waitFor();
    assert.deepEqual((await api('/resume/file-profile',token)).data.skills,['Java','SQL']);
    await page.locator('.avatar-editor input[type=file]').setInputFiles(path.resolve(__dirname,'../前端/src/assets/generated/user-avatar-ui.png'));
    await page.getByRole('button',{name:'保存头像',exact:true}).click({timeout:15000});
    await page.getByText('头像已保存。').waitFor();
    await page.reload();await page.getByRole('heading',{name:'林同学',exact:true}).waitFor();
    assert.match((await api('/user/me',token)).data.avatar,/^data:image\/png;base64,/);
    assert.equal((await api('/user/career-profile',token)).data.targetJobId,chosen.id);
    assert.equal(await page.locator('.user-avatar-sm img').count(),1);
    await page.waitForTimeout(400);
    await page.screenshot({path:path.join(out,'profile-desktop.png'),fullPage:true});
    for(const width of [1024,768,390,360]){
      await page.setViewportSize({width,height:1000});await page.waitForTimeout(500);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false,'profile overflow '+width);
      if(width===390)await page.screenshot({path:path.join(out,'profile-mobile.png'),fullPage:true});
    }
    await page.setViewportSize({width:1440,height:1100});
    await page.goto(web+'/jobs');await page.locator('.job-detail__summary').waitFor();
    assert.equal(await page.locator('.job-detail h3').innerText(),chosen.name);
    const temporary = jobs.find(j=>j.id!==chosen.id);
    await page.goto(web+'/jobs?job='+encodeURIComponent(temporary.code));await page.locator('.job-detail h3').waitFor();
    assert.equal(await page.locator('.job-detail h3').innerText(),temporary.name);
    assert.equal((await api('/user/career-profile',token)).data.targetJobId,chosen.id);
    await page.goto(web+'/learning');await page.locator('.role-grid').waitFor();
    assert((await page.locator('.role-grid button.selected').innerText()).includes(chosen.name));
    await page.setViewportSize({width:390,height:1000});await page.locator('.mobile-role-card').waitFor();
    assert((await page.locator('body').innerText()).includes(chosen.name));
    await page.setViewportSize({width:1440,height:1100});
    await page.goto(web+'/settings');await page.getByLabel('当前密码').fill(password);await page.getByLabel('新密码',{exact:true}).fill('Changed826!');await page.getByLabel('确认新密码').fill('Changed826!');await page.getByRole('button',{name:'更新密码',exact:true}).click();await page.getByText('密码已更新，下次登录请使用新密码。').waitFor();
    assert.equal((await api('/auth/login',null,'POST',{username,password:'Changed826!'})).code,200);
    assert.deepEqual(errors,[]);
    const peerName='peer_'+Date.now().toString(36);
    await api('/auth/register',null,'POST',{username:peerName,password,nickname:'隔离测试'});
    const peer=(await api('/auth/login',null,'POST',{username:peerName,password})).data;
    assert.equal((await api('/user/career-profile',peer.token)).data.targetJobId,null);
    assert.equal((await api('/user/me',peer.token)).data.avatar,null);
    assert.equal((await api('/resume/mine',peer.token)).data,null);
    console.log('PASS: real API nickname, profile persistence/failure, avatar crop/upload/reload, DOCX upload and tags, account isolation, default job reuse, password, responsive 360–1440. Test account: '+username);
  } catch(e){await page.screenshot({path:path.join(out,'failure.png'),fullPage:true});throw e}
  finally {await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
