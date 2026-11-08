// Uses installed Playwright and its bundled Chromium by default. Optional overrides:
// PLAYWRIGHT_PATH, INTERVIEW_BROWSER_PATH, INTERVIEW_QA_URL, INTERVIEW_QA_OUT.
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.INTERVIEW_QA_URL || 'http://127.0.0.1:5187';
const out = path.resolve(__dirname, process.env.INTERVIEW_QA_OUT || '../artifacts/interview-predictions');
fs.mkdirSync(out, { recursive: true });
const items = [
 {kind:'INTERVIEW',title:'技术原理题 · Spark Streaming',field:'skills',question:'Spark Streaming处理实时大数据的基本原理是什么？',action:'解释 Spark Streaming 如何将实时数据流按时间片分割成小批处理作业，以及 DStream 和 RDD 的关系。'},
 {kind:'INTERVIEW',title:'技术原理题 · 机器学习',field:'skills',question:'在机器学习中，什么是过拟合？',action:'说明训练集与测试集表现的差异，并解释交叉验证、正则化等常用处理思路。'},
 {kind:'INTERVIEW',title:'项目深挖题 · 算法平台',field:'projects',question:'若要构建一个适用于业务的高效率算法平台，在数据处理环节，你认为可以采用哪些大数据技术，如何实现？',action:'结合岗位要求与真实项目背景，说明技术选择、数据处理流程及本人负责的环节。'},
 {kind:'INTERVIEW',title:'项目深挖题 · 特征工程',field:'raw',question:'在优化用户体验的算法项目中，若使用机器学习算法，你会如何进行数据特征的选择和提取？',action:'依据业务需求与数据特性说明特征选择和提取方法，并用真实验证结果说明取舍。'}
];
items.splice(2,0,
 {kind:'INTERVIEW',title:'技术原理题 · SQL',field:'skillTags',question:'如何分析 SQL 执行计划？',action:'先分析索引与扫描行数，再验证性能。'},
 {kind:'INTERVIEW',title:'技术原理题 · Redis',field:'skillTags',question:'不应显示的第四道技术题',action:'旧报告超过三题时截断。'});
items.push(
 {kind:'STRENGTH',title:'体现了数据处理与算法实践',action:'面试时重点说明本人负责的实现环节。',quote:'不应展示的原文依据'},
 {kind:'IMPROVEMENT',priority:'HIGH',title:'补充方案选择与验证方法',action:'说明方案取舍，并补充真实验证结果。',quote:'不应展示的原文依据'},
 {kind:'JOB_GAP',title:'补充岗位要求的性能分析经历',action:'如有真实经历，补充性能分析方法；没有则不要编造。',quote:'不应展示的原文依据'});
const report = {id:1, snapshot:{jobName:'算法工程师',title:'自动化验收材料'},createdAt:'2026-11-05T12:00:00',source:'AI',status:'COMPLETED',applicable:true,summary:'此处使用隔离验收数据。',items,suggestedModules:[]};
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.INTERVIEW_BROWSER_PATH});
 const page=await browser.newPage({viewport:{width:1158,height:1141},acceptDownloads:true});
 const errors=[]; page.on('pageerror',e=>{errors.push(e.message); console.log('PAGEERROR',e.message)}); page.on('console',m=>{if(m.type()==='error' && !m.text().includes('vite') && !m.text().includes('WebSocket')) console.log('BROWSER',m.text())}); page.on('response',r=>{if(r.status()>=400)console.log('HTTP',r.status(),r.url())});
 await page.route('**/api/**',route=>new URL(route.request().url()).pathname.startsWith('/api/') ? route.fulfill({json:{code:200,data:route.request().url().includes('/modules')?[]:report}}) : route.continue());
 await page.route('**/src/api/index.js*',route=>route.fulfill({contentType:'application/javascript',body:`export const getResumeAnalysis=async()=>(${JSON.stringify(report)});export const createResumeAnalysis=getResumeAnalysis;export const getModules=async()=>[];`}));
 await page.route('**/__predictions_qa',route=>route.fulfill({contentType:'text/html',body:`<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{margin:0;padding:20px;background:white}#app{max-width:1100px;margin:auto}</style></head><body><div id="app"></div><script type="module">import {createApp,h} from '/node_modules/.vite/deps/vue.js';import {createPinia} from '/node_modules/.vite/deps/pinia.js';import Panel from '/src/components/resume/ResumeAnalysisPanel.vue';import '/src/assets/styles/main.css';createApp({render(){return h(Panel,{jobId:1,available:true,onPractice(){window.practiceEntered=true}})}}).use(createPinia()).mount('#app');</script></body></html>`}));
 await page.goto(base + '/__predictions_qa');
 await page.locator('.prediction__question').first().waitFor({timeout:10000}).catch(async e=>{console.log('BODY',await page.locator('body').innerText());throw e});
 assert.equal(await page.locator('.prediction__section[aria-label="技能题"] .prediction__question').count(),3);
 assert.equal(await page.locator('.analysis__results-list > li').count(),3);
 assert.equal(await page.locator('.analysis blockquote').count(),0);
 assert.equal(await page.getByRole('button',{name:'详情',exact:true}).count(),0);
 assert.equal(await page.locator('.analysis__pager').count(),0);
 assert.equal(await page.evaluate(()=>document.querySelector('.prediction').compareDocumentPosition(document.querySelector('.analysis__results')) & Node.DOCUMENT_POSITION_FOLLOWING),4);
 await page.locator('.analysis').screenshot({path:path.join(out,'full-results-desktop.png')});
 await page.locator('.prediction').screenshot({path:path.join(out,'desktop.png')});
 const downloadEvent=page.waitForEvent('download'); await page.getByRole('button',{name:'下载',exact:true}).click();
 const download=await downloadEvent; const file=path.join(out,'questions.txt'); await download.saveAs(file);
 assert.match(fs.readFileSync(file,'utf8'),/技能题[\s\S]*Spark Streaming[\s\S]*项目题/); assert.equal(fs.readFileSync(file,'utf8').includes('第四道'),false);
 await page.getByRole('button',{name:'进入模拟面试 体验完整追问 >'}).first().click();
 assert.equal(await page.evaluate(()=>window.practiceEntered),true);
 for(const width of [360,390,430]){
  await page.setViewportSize({width,height:950});
  await page.locator('.prediction').screenshot({path:path.join(out,`mobile-${width}.png`)});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`overflow at ${width}`);
  assert.equal(await page.locator('.prediction__download').evaluate(el=>el.getBoundingClientRect().height>=44),true);
 }
 report.items=items.filter(item=>item.field==='projects'||item.field==='raw');
 await page.setViewportSize({width:1158,height:1141});
 await page.reload(); await page.locator('.prediction__empty').first().waitFor();
 const typography=await page.evaluate(()=>Object.fromEntries(['section-title','question','answer','empty','practice'].map(name=>[name,getComputedStyle(document.querySelector('.prediction__'+name)).fontSize])));
 assert.deepEqual(typography,{'section-title':'18px',question:'16px',answer:'14px',empty:'14px',practice:'14px'});
 await page.locator('.prediction').screenshot({path:path.join(out,'empty-skills-desktop.png')});
 report.applicable=false;
 await page.reload(); await page.locator('.prediction__practice').first().waitFor();
 assert.equal(await page.locator('.prediction__practice').first().isDisabled(),true);
 assert.deepEqual(errors,[]);
 await browser.close();console.log('PASS: desktop/mobile 360/390/430, download contents, practice event, stale report disabled, no page errors');
})().catch(e=>{console.error(e);process.exit(1)});
