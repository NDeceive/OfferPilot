// Local API/media fixtures only: never creates or changes a real interview.
const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const root = path.resolve(__dirname, '..'), base = process.env.INTERVIEW_QA_URL || 'http://127.0.0.1:5187'
const preview = path.join(root, '前端/tests/stage-audio-check.html')
const out = path.join(root, 'artifacts/interview-flow-recovery')
fs.mkdirSync(out, { recursive: true })
fs.writeFileSync(preview, `<!doctype html><meta charset="UTF-8"><script type="module">
import {createPinia,setActivePinia} from 'pinia';
import request from '/src/utils/request.js';
import {useAnswerAudio} from '/src/composables/useAnswerAudio.js';
import {createApp,nextTick,h,ref} from 'vue';
import Microphone from '/src/components/interview/MicrophoneControl.vue';
setActivePinia(createPinia());
const check=(value,message)=>{if(!value)throw new Error(message)};
const device=()=>{const track={stopped:false,stop(){this.stopped=true}};return {track,getTracks:()=>[track],getAudioTracks:()=>[track]}};
class Recorder {constructor(stream){this.mimeType='audio/webm'}start(){}stop(){queueMicrotask(()=>{this.ondataavailable?.({data:new Blob(['final chunk'])});this.onstop?.()})}}
window.MediaRecorder=Recorder;
window.AudioContext=class {async decodeAudioData(){return {sampleRate:16000,numberOfChannels:1,getChannelData:()=>new Float32Array(16000).fill(.2)}}};
navigator.mediaDevices.getUserMedia=async()=>device();
let uploads=0;
request.defaults.adapter=async config=>{uploads++;if(uploads===1)throw new Error('intentional upload failure');check(config.data.get('file').size>44,'final recorder chunk lost');return {data:{code:200,data:{audioId:uploads}},status:200,headers:{},config}};
try {
 const audio=useAnswerAudio();await audio.start({sessionId:1,roundNo:1,questionId:-1});
 let failed=false;try{await audio.stopAndUpload({required:true})}catch(e){failed=e.code==='AUDIO_UPLOAD_FAILED'}
 check(failed,'required upload must not silently succeed');
 const saved=await audio.stopAndUpload({required:true});check(saved.audioId===2,'failed upload must retain recording for retry');
 check((await audio.stopAndUpload({required:true})).audioId===2 && uploads===2,'retry must reuse the recording acknowledgement');
 let releaseOld;const old=device(),fresh=device();navigator.mediaDevices.getUserMedia=()=>new Promise(resolve=>releaseOld=()=>resolve(old));
 const pending=audio.start({sessionId:1,roundNo:2,questionId:-2});
 navigator.mediaDevices.getUserMedia=async()=>fresh;await audio.start({sessionId:1,roundNo:3,questionId:-3});releaseOld();await pending;
 check(old.track.stopped && !fresh.track.stopped,'stale permission response must not stop the current recording');
 audio.discard();check(fresh.track.stopped,'discard must release the active stream');
 // Finish must await recording conversion/upload and retain failures for an explicit retry.
 navigator.mediaDevices.getUserMedia=async()=>device();
 let releaseUpload,attempts=0,finished=false;
 request.defaults.adapter=async config=>{attempts++;await new Promise(resolve=>releaseUpload=resolve);return {data:{code:200,data:{audioId:7}},status:200,headers:{},config}};
 await audio.start({sessionId:7,roundNo:1,questionId:3});audio.markSubmitted();
 const save=audio.stopAndUpload();const finish=audio.flushSubmitted(7).then(()=>finished=true);
 for(let n=0;n<20&&!releaseUpload;n++)await new Promise(resolve=>setTimeout(resolve,5));
 check(!finished&&attempts===1,'finish must wait for the upload acknowledgement without duplicate uploads');
 releaseUpload();await save;await finish;check(finished,'successful upload must release finish');
 let failUpload=true;
 request.defaults.adapter=async config=>{if(failUpload)throw new Error('intentional recording upload failure');return {data:{code:200,data:{audioId:8}},status:200,headers:{},config}};
 await audio.start({sessionId:8,roundNo:1,questionId:4});audio.markSubmitted();await audio.stopAndUpload();
 let blocked=false;try{await audio.flushSubmitted(8)}catch(e){blocked=e.code==='AUDIO_UPLOAD_FAILED'}
 check(blocked,'failed upload must block finish');failUpload=false;await audio.flushSubmitted(8);
 check((await audio.stopAndUpload()).audioId===8,'finish retry must save and reuse the retained recording');audio.discard();
 request.defaults.adapter=async config=>{check(config.data.get('questionId')==='0','experience question ID zero must be uploaded');return {data:{code:200,data:{audioId:9}},status:200,headers:{},config}};
 await audio.start({sessionId:9,roundNo:7,questionId:0});audio.markSubmitted();await audio.flushSubmitted(9);audio.discard();
 // Exercise the actual voice-control component: a failed/empty segment must block partial submission.
 window.AudioContext=class {constructor(){this.sampleRate=16000;this.destination={};this.audioWorklet={addModule:async()=>{}}}createMediaStreamSource(){return {connect(){},disconnect(){}}}createGain(){return {gain:{value:0},connect(){},disconnect(){}}}async resume(){}async close(){}};
 let port;window.AudioWorkletNode=class {constructor(){this.port={};port=this.port}connect(){}disconnect(){}};
 navigator.mediaDevices.getUserMedia=async()=>device();
 const sleep=()=>new Promise(resolve=>setTimeout(resolve,10));
 const segment=()=>{const savedNow=Date.now;let now=savedNow();Date.now=()=>now;port.onmessage({data:new Float32Array(8000).fill(.2).buffer});now+=2000;port.onmessage({data:new Float32Array(8000).buffer});Date.now=savedNow};
 window.SpeechRecognition=undefined;window.webkitSpeechRecognition=undefined;
 for(const mode of ['failure','empty']) {
   let submits=0,calls=0;const container=document.createElement('div');document.body.append(container);
   request.defaults.adapter=async config=>{calls++;if(mode==='failure'&&calls===1)throw new Error('intentional ASR failure');return {data:{code:200,data:{text:mode==='empty'?'':'后续识别文本'}},status:200,headers:{},config}};
   const app=createApp(Microphone,{sessionId:1,transcript:'已有部分转写',onSubmit:()=>submits++});app.mount(container);
   container.querySelector('.mic-control').click();for(let n=0;n<20&&!container.querySelector('.mic-control.open');n++)await sleep();
   segment();for(let n=0;n<20&&!container.querySelector('.mic-error');n++)await sleep();
   check(container.querySelector('.mic-error'),'ASR failure or empty output must remain visible');
   container.querySelector('.btn-send').click();await nextTick();check(submits===0,'partial transcript must not complete a failed answer');
   if(mode==='failure'){segment();await sleep();await sleep();check(container.querySelector('.mic-error'),'later successful segment must not erase an earlier failure');container.querySelector('.btn-send').click();await nextTick();check(submits===0,'later ASR success must not silently submit a partial answer')}
   app.unmount();container.remove();
 }
 // Browser ASR must receive final results before submitting, without calling backend ASR.
 let backendCalls=0;request.defaults.adapter=async()=>{backendCalls++;throw new Error('browser ASR must not upload transcription audio')};
 for(const mode of ['standard','prefixed','network','skip','cancel-submit']) {
   const instances=[];let submits=0,skips=0,exposed;const text=ref('');
   class BrowserSpeech {
     constructor(){instances.push(this)}
     start(){this.started=true}
     stop(){this.stopping=true}
     abort(){this.aborted=true}
     result(value){this.onresult?.({resultIndex:0,results:[Object.assign([{transcript:value}],{isFinal:true})]})}
   }
   window.SpeechRecognition=mode==='prefixed'?undefined:BrowserSpeech;
   window.webkitSpeechRecognition=mode==='prefixed'?BrowserSpeech:undefined;
   const container=document.createElement('div');document.body.append(container);
   const app=createApp({setup:()=>()=>h(Microphone,{ref:value=>exposed=value,sessionId:1,transcript:text.value,onTranscript:value=>text.value+=value,onSubmit:()=>submits++,onSkip:()=>skips++})});
   app.mount(container);container.querySelector('.mic-control').click();
   for(let n=0;n<20&&!instances.length;n++)await sleep();
   check(instances.length===1&&instances[0].started,'browser recognizer must start on opening microphone');
   check(container.textContent.includes('浏览器语音识别'),'browser recognition mode must be visible');
   let current=instances[0];check(current.lang==='zh-CN'&&current.continuous,'browser recognizer must support continuous Chinese speech');
   if(mode==='network') {
     current.onerror({error:'network'});current.onend();await nextTick();
     container.querySelector('.btn-send').click();await sleep();
     check(submits===0&&container.querySelector('.mic-error'),'browser service failure must be visible and prevent submission');
   } else if(mode==='skip') {
     container.querySelector('.btn-ghost').click();await sleep();current.result('过期回答');current.onend();await nextTick();
     check(skips===1&&submits===0&&text.value==='','skip must cancel recognition and discard late results');
   } else {
     current.result('第一句。');await nextTick();segment();await sleep();
     check(text.value==='第一句。','browser results must display immediately');
     current.onend();await nextTick();check(instances.length===2,'normal browser disconnect must restart while microphone is open');
     current=instances[1];container.querySelector('.btn-send').click();await nextTick();
     check(current.stopping&&submits===0,'submission must await browser final result and end');
     if(mode==='cancel-submit') {
       await exposed.stopMicrophone();current.result('过期尾句');current.onend();await sleep();
       check(submits===0&&text.value==='第一句。','cancel during pending submission must discard late results');
     } else {
       current.result('最后一句。');current.onend();await sleep();await nextTick();
       check(submits===1&&text.value==='第一句。最后一句。','submission must include the last browser result exactly once');
     }
   }
   check(backendCalls===0,'supported browser must never call backend ASR');app.unmount();container.remove();
 }
 window.checkResult={passed:true};
}catch(error){window.checkResult={passed:false,message:error.stack}}
</script>`)

async function stage(browser, width, phase) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, permissions: ['camera', 'microphone'] })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'stage-isolated-check'); localStorage.setItem('role', 'STUDENT')
    navigator.mediaDevices.getUserMedia = async () => { const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480; return canvas.captureStream(10) }
  })
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  let finishes = 0
  const intro = phase === 'SELF_INTRODUCTION'
  const question = { id: 103, questionId: intro ? -1 : -2, roundNo: intro ? 1 : 2, role: 'INTERVIEWER', msgType: 'MAIN', content: intro ? '首先，请你简单介绍一下自己。' : '你在校园选课项目负责的模块是如何实现的？', abilityTag: intro ? '自我介绍' : '项目实践表达' }
  await page.route('**/api/**', async route => {
    const uri = new URL(route.request().url()).pathname
    if (!uri.startsWith('/api/')) return route.continue()
    let data = {}
    if (uri.endsWith('/resume')) data = { status: 'ONGOING', interviewStage: phase, durationSeconds: 600, remainingSeconds: 11, paused: false, currentQuestion: question, messages: [question], answered: false }
    else if (uri.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '隔离检查' }
    else if (uri.endsWith('/expression-context')) data = { serverTime: Date.now() }
    else if (uri.endsWith('/finish')) { finishes++; data = 987654 }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.route('**/digital-human/**', route => route.fulfill({ contentType: 'text/html', body: '<html></html>' }))
  await page.goto(base + '/interview?sessionId=987654&jobName=阶段恢复检查&voice=0')
  await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
  const label = intro ? '自我介绍' : '项目经历与技术深挖'
  await page.locator('.q-row').filter({ hasText: '阶段' }).getByText(label, { exact: true }).waitFor({ timeout: 6000 }).catch(async error => {
    console.error('Stage URL:', page.url(), 'errors:', errors, 'body:', await page.locator('body').innerText())
    await page.screenshot({ path: path.join(out, 'stage-failure.png'), fullPage: true }); throw error
  })
  assert.equal(await page.getByRole('button', { name: '跳过', exact: true }).count(), intro ? 0 : 1)
  await page.waitForTimeout(1200)
  assert.equal(finishes, 0, 'a staged interview must retain its final thirty seconds')
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  assert.deepEqual(errors, [])
  await page.screenshot({ path: path.join(out, 'stage-' + width + '.png'), fullPage: true })
  await context.close()
}

async function questionLed(browser, width, protection = false) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, permissions: ['camera', 'microphone'] })
  await context.addInitScript(() => {
    localStorage.setItem('token', 'question-led-isolated-check'); localStorage.setItem('role', 'STUDENT')
    navigator.mediaDevices.getUserMedia = async () => { const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 480; return canvas.captureStream(10) }
  })
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  let finishes = 0, paused = false
  const plan = { mode: 'CORE_QUESTIONS', completedCoreQuestions: 2, totalCoreQuestions: 4, skippedCoreQuestions: 0,
    elapsedSeconds: protection ? 1810 : 650, estimatedDurationSeconds: protection ? 1800 : 1100,
    initialDurationSeconds: 600, hardLimitSeconds: 1800, answerGraceSeconds: 120,
    remainingSeconds: protection ? 110 : 1270, canFinish: false }
  const question = { id: 103, questionId: 3, questionInstanceId: 103, roundNo: 3, role: 'INTERVIEWER', msgType: 'MAIN',
    content: '请说明你如何定位接口响应缓慢的问题。', abilityTag: '技术诊断' }
  await page.route('**/api/**', async route => {
    const uri = new URL(route.request().url()).pathname
    if (!uri.startsWith('/api/')) return route.continue()
    let data = {}
    if (uri.endsWith('/resume')) data = { status: 'ONGOING', interviewStage: 'PROFESSIONAL_QUESTIONS', durationSeconds: 600,
      remainingSeconds: plan.remainingSeconds, paused, currentQuestion: question, messages: [question], answered: false, interviewPlan: plan }
    else if (uri.endsWith('/pause')) { paused = !paused; data = { paused, remainingSeconds: plan.remainingSeconds, interviewPlan: plan } }
    else if (uri.endsWith('/me')) data = { id: 999, role: 'STUDENT', nickname: '隔离检查' }
    else if (uri.endsWith('/expression-context')) data = { serverTime: Date.now() }
    else if (uri.endsWith('/finish')) { finishes++; data = 987654 }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.route('**/digital-human/**', route => route.fulfill({ contentType: 'text/html', body: '<html></html>' }))
  const enter = async () => {
    await page.goto(base + '/interview?sessionId=987654&jobName=问题优先检查&voice=0')
    await page.getByRole('button', { name: '允许并进入面试', exact: true }).click()
    await page.locator('.interview-plan-status').waitFor()
  }
  await enter()
  await page.locator('.interview-plan-status').getByText('核心问题 2/4', { exact: true }).waitFor()
  assert.match(await page.locator('.timer').innerText(), /已用/)
  assert.equal(await page.locator('.progress-fill').evaluate(element => element.style.width), '50%')
  await page.locator('.interview-plan-status p').filter({ hasText: protection ? '保护上限' : '已达参考时长' }).waitFor()
  if (!protection) {
    await page.getByRole('button', { name: '暂停面试', exact: true }).click()
    await page.getByRole('button', { name: '继续面试', exact: true }).waitFor()
    const frozen = await page.locator('.timer').innerText()
    await page.waitForTimeout(1100)
    assert.equal(await page.locator('.timer').innerText(), frozen, 'paused time must not advance the elapsed clock')
    await page.getByRole('button', { name: '继续面试', exact: true }).click()
    await enter()
    await page.locator('.interview-plan-status').getByText('核心问题 2/4', { exact: true }).waitFor()
  }
  assert.equal(finishes, 0, 'reference time or protection grace must not interrupt the current answer')
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  assert.deepEqual(errors, [])
  await page.screenshot({ path: path.join(out, 'question-led-' + width + (protection ? '-grace' : '') + '.png'), fullPage: true })
  await context.close()
}

;(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--no-proxy-server'] })
  try {
    const page = await browser.newPage()
    await page.goto(base + '/tests/stage-audio-check.html')
    await page.waitForFunction(() => window.checkResult)
    assert.deepEqual(await page.evaluate(() => window.checkResult), { passed: true })
    await page.close()
    await stage(browser, 1440, 'SELF_INTRODUCTION')
    await stage(browser, 390, 'PROJECT_DISCUSSION')
    await questionLed(browser, 1440)
    await questionLed(browser, 390)
    await questionLed(browser, 390, true)
    console.log('Passed: browser ASR, last result before submit, upload retry/acknowledgement and experience audio; question-led elapsed clock/core progress, reference-time extension without finish, pause/resume, refresh persistence and protection grace on desktop/mobile; legacy stage regression.')
  } finally { await browser.close(); fs.unlinkSync(preview) }
})().catch(error => { if (fs.existsSync(preview)) fs.unlinkSync(preview); console.error(error); process.exitCode = 1 })
