const fs=require('fs'),path=require('path');
const {Resvg}=require('C:/Users/31318/.codex/skills/image-to-svg/node_modules/@resvg/resvg-js');
const dir=path.join(__dirname,'A-bold-v2');fs.mkdirSync(dir,{recursive:true});
function edit(s){return s.replace(/<text\b[^>]*font-family="Segoe UI"[^>]*>/g,t=>t.replace(/font-weight="\d+"/,'font-weight="700"'));}
for(const id of ['cn','en','bilingual']){let s=fs.readFileSync(path.join(__dirname,`A-${id}.svg`),'utf8');s=edit(s);fs.writeFileSync(path.join(dir,`A-${id}.svg`),s);fs.writeFileSync(path.join(dir,`A-${id}.png`),new Resvg(s,{fitTo:{mode:'width',value:id==='bilingual'?560:740}}).render().asPng());}
const p=fs.readFileSync(path.join(__dirname,'A-en.svg'),'utf8').match(/<g[^>]*>[\s\S]*?<\/g>/)[0];
const label=(x,y,t,size=18)=>`<text x="${x}" y="${y}" font-family="Microsoft YaHei" font-size="${size}" fill="#34473d">${t}</text>`;
const lock=(x,y,weight)=>`<g transform="translate(${x} ${y})">${p}<text x="105" y="60" font-family="Segoe UI" font-size="43" font-weight="${weight}" fill="#172b22">OfferPilot</text></g>`;
const dual=fs.readFileSync(path.join(dir,'A-bilingual.svg'),'utf8').replace(/<svg[^>]*>/,'').replace('</svg>','');
let board=`<svg xmlns="http://www.w3.org/2000/svg" width="1020" height="660" viewBox="0 0 1020 660"><rect width="1020" height="660" fill="#f6f8f6"/>${label(50,55,'A · 英文字重精修',28)}${label(50,90,'沿用 Segoe UI，英文主标 600 → 700；双语英文副标 400 → 700。',16)}<rect x="50" y="120" width="440" height="220" rx="12" fill="white"/><rect x="530" y="120" width="440" height="220" rx="12" fill="white"/>${label(74,159,'原版 · Semibold 600')}${label(554,159,'调整版 · Bold 700')}${lock(74,200,600)}${lock(554,200,700)}<rect x="50" y="375" width="920" height="220" rx="12" fill="white"/>${label(74,412,'双语组合 · 英文同步加粗')}<g transform="translate(100 447)">${dual}</g>${label(500,488,'中文与符号保持原版。',18)}${label(500,522,'英文增加重量，保留中文主标的层级。',16)}${label(50,633,'字重调整稿 · 文字可编辑 SVG · 尚未制作最终字标轮廓',15)}</svg>`;
fs.writeFileSync(path.join(dir,'comparison.svg'),board);fs.writeFileSync(path.join(dir,'comparison.png'),new Resvg(board,{font:{loadSystemFonts:true}}).render().asPng());
console.log('A Bold revision exported.');
