const fs=require('node:fs'),path=require('node:path');
const {chromium}=require('C:/Users/31318/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 try{
 const page=await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:4312/',{waitUntil:'networkidle'});
 const initial=await page.evaluate(()=>({routes:document.querySelectorAll('.route').length,drafts:document.querySelectorAll('.drafts figure').length,selected:document.querySelectorAll('[data-priority]:checked').length,overflow:document.documentElement.scrollWidth>innerWidth}));
 await page.screenshot({path:path.join(__dirname,'route-board-preview.png')});
 await page.screenshot({path:path.join(__dirname,'route-board-full.png'),fullPage:true});
 await page.locator('.overview').screenshot({path:path.join(__dirname,'route-overview.png')});
 await page.getByRole('button',{name:'只看图形',exact:true}).click();
 const clean=await page.locator('body').evaluate(el=>el.classList.contains('clean'));
 await page.getByRole('button',{name:'显示路线说明',exact:true}).click();
 await page.getByRole('button',{name:'临时绿色预览',exact:true}).click();
 const green=await page.locator('body').evaluate(el=>el.classList.contains('green'));
 await page.getByRole('button',{name:'回到黑白判断',exact:true}).click();
 await page.locator('[data-priority="A"]').check();await page.locator('[data-priority="C"]').check();
 await page.locator('#reason').fill('QA temporary choice');
 await page.reload({waitUntil:'networkidle'});
 const persisted=await page.locator('[data-priority="A"]').isChecked()&&await page.locator('[data-priority="C"]').isChecked()&&await page.locator('#reason').inputValue()==='QA temporary choice';
 const downloading=page.waitForEvent('download');await page.getByRole('button',{name:'导出选择',exact:true}).click();const downloaded=await downloading;
 const data=JSON.parse(fs.readFileSync(await downloaded.path(),'utf8'));
 const exported=data.routes.join(',')==='A,C'&&data.reason==='QA temporary choice';
 await page.evaluate(()=>localStorage.removeItem('offerpilot-logo-stage03-selection-v1'));await page.reload({waitUntil:'networkidle'});
 const responsive=[];
 for(const width of [390,360]){await page.setViewportSize({width,height:844});responsive.push(await page.evaluate(()=>({width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth,miniSizes:[...document.querySelectorAll('#route-A .mini-sample svg')].map(el=>el.getBoundingClientRect().width)})));}
 await page.screenshot({path:path.join(__dirname,'route-board-mobile.png'),fullPage:true});
 const assets=fs.readdirSync(path.join(__dirname,'assets')).filter(f=>f.endsWith('.svg')).map(file=>{const s=fs.readFileSync(path.join(__dirname,'assets',file),'utf8');return{file,paths:(s.match(/<path\b/g)||[]).length,raster:/<image\b|data:image|<foreignObject\b/i.test(s),effects:/gradient|filter=|opacity=/i.test(s)}});
 const result={initial,clean,green,persisted,exported,responsive,errors,assets,checkedAt:new Date().toISOString()};fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
 if(initial.routes!==5||initial.drafts!==10||initial.selected||initial.overflow||!clean||!green||!persisted||!exported||responsive.some(r=>r.overflow||r.miniSizes.join(',')!=='16,32,64,16,32,64')||errors.length||assets.some(a=>a.raster||a.effects))process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
