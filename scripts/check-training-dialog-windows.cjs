const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path')
const root = process.env.TEACHER_WEB || 'http://127.0.0.1:5173', out = path.resolve('output/training-tasks-review')
fs.mkdirSync(out, { recursive: true })
;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(() => { localStorage.setItem('token','local-ui-test'); localStorage.setItem('role','TEACHER'); localStorage.setItem('userId','task-qa'); localStorage.setItem('nickname','王老师') })
  await context.route('**/api/**', route => new URL(route.request().url()).pathname.startsWith('/src/') ? route.continue() : route.fulfill({ json: { code:200,data:{ nickname:'王老师',role:'TEACHER' } } }))
  const page = await context.newPage(), errors = []
  page.on('pageerror', e => errors.push(e.message))
  const visit = async url => { await page.goto(root+url); await page.locator('h1').waitFor(); await page.waitForTimeout(350) }
  const noOverflow = async () => { if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1))console.log(await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({c:e.className,w:e.getBoundingClientRect().width,text:e.innerText?.slice(0,50)})).slice(0,15))); assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Page overflows: '+page.url()+' '+(await page.viewportSize()).width) }
  try {
const check=async(name,width)=>{const d=page.getByRole('dialog');await d.waitFor();await page.waitForTimeout(300);const box=await d.boundingBox();assert(Math.abs(box.x+box.width/2-width/2)<2);assert(box.width<=820&&box.y>=10);assert(await d.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await page.screenshot({path:path.join(out,'dialog-'+name+'-'+width+'.png')});await page.keyboard.press('Escape');await d.waitFor({state:'hidden'})};
for(const width of [1440,390,360]){await page.setViewportSize({width,height:1000});await visit('/teacher/tasks/create?demo=1&classId=software2502');await page.getByText('指定学生',{exact:false}).first().click();await page.getByRole('button',{name:'选择学生',exact:true}).click();await check('selector',width);
await visit('/teacher/tasks/task_logic_8?demo=1');await page.getByText('查看任务配置',{exact:true}).click();await page.getByRole('button',{name:'查看训练内容',exact:true}).click();await check('content',width);await page.getByRole('button',{name:'延长截止',exact:true}).click();await check('extend',width);await page.getByRole('button',{name:'提醒未完成',exact:true}).click();await check('remind',width);await visit('/teacher/tasks?demo=1&search='+encodeURIComponent('逻辑表达专项训练'));await page.getByRole('button',{name:'未开始 2',exact:true}).click();await check('students',width);
await visit('/teacher/tasks/templates?demo=1');await page.getByRole('button',{name:'查看模板',exact:true}).first().click();await check('template',width);
}assert.deepEqual(errors,[]);console.log('PASS training dialogs at 1440/390/360');
}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
