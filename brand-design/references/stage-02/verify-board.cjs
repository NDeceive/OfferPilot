const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
  const browser = await chromium.launch({headless:true, executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
    const errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto('http://127.0.0.1:4311/',{waitUntil:'networkidle'});
    const initial = await page.evaluate(()=>({cards:document.querySelectorAll('.reference').length,images:[...document.images].filter(i=>!i.complete||i.naturalWidth===0).length,overflow:document.documentElement.scrollWidth>innerWidth}));
    await page.screenshot({path:path.join(__dirname,'reference-board-preview.png')});
    await page.screenshot({path:path.join(__dirname,'reference-board-full.png'),fullPage:true});
    await page.getByRole('button',{name:'只看图形',exact:true}).click();
    const toggle = await page.locator('body').evaluate(el=>el.classList.contains('clean'));
    await page.getByRole('button',{name:'显示设计注释',exact:true}).click();
    await page.locator('#pref-01').selectOption('partial');
    await page.locator('input[data-note="01"]').fill('QA temporary preference');
    await page.reload({waitUntil:'networkidle'});
    const persisted = await page.locator('#pref-01').inputValue()==='partial';
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button',{name:'导出我的偏好',exact:true}).click();
    const download = await downloadPromise;
    const exported = download.suggestedFilename()==='offerpilot-stage02-preferences.json';
    await page.evaluate(()=>localStorage.removeItem('offerpilot-logo-stage02-preferences-v1'));
    await page.reload({waitUntil:'networkidle'});
    await page.setViewportSize({width:390,height:844});
    const mobile = await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,brokenImages:[...document.images].filter(i=>!i.complete||i.naturalWidth===0).length}));
    await page.screenshot({path:path.join(__dirname,'reference-board-mobile.png'),fullPage:true});
    await page.setViewportSize({width:360,height:800});
    const narrowOverflow = await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
    const result={initial,toggle,persisted,exported,mobile,narrowOverflow,errors,checkedAt:new Date().toISOString()};
    fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify(result,null,2));
    console.log(JSON.stringify(result,null,2));
    if(initial.cards!==7||initial.images||initial.overflow||!toggle||!persisted||!exported||mobile.overflow||mobile.brokenImages||narrowOverflow||errors.length)process.exitCode=1;
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1});
