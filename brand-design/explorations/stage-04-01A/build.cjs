const fs=require('fs'),path=require('path');
const {Resvg}=require('C:/Users/31318/.codex/skills/image-to-svg/node_modules/@resvg/resvg-js');
const dir=__dirname;
// Manually reconstructed from user-selected raster. Three independent editable paths.
const frame='M 0 183 C 0 146 13 125 44 105 L 93 76 C 135 51 160 64 203 86 C 245 108 281 126 319 104 C 343 90 358 62 381 30 C 412 -12 446 -6 479 16 L 573 83 C 606 106 622 134 622 167 L 622 565 C 622 599 598 623 569 624 C 556 624 550 619 550 609 L 550 202 C 550 164 521 133 485 144 C 422 164 363 184 297 184 C 237 184 190 169 134 151 C 103 141 72 165 72 202 L 72 607 C 72 619 66 624 55 624 C 23 624 0 600 0 566 Z';
function doors(gap=25){let a=72+gap,b=550-gap;return [`M ${a} 207 Q ${a} 190 ${a+13} 199 L 205 276 Q 215 284 215 300 L 215 512 Q 215 527 204 534 L ${a+13} 601 Q ${a} 610 ${a} 595 Z`,`M ${b} 207 Q ${b} 190 ${b-13} 199 L 417 276 Q 407 284 407 300 L 407 512 Q 407 527 418 534 L ${b-13} 601 Q ${b} 610 ${b} 595 Z`];}
function shape(gap=25,color='#000'){return [frame,...doors(gap)].map(d=>`<path fill="${color}" d="${d}"/>`).join('');}
function svg(scale=1,gap=25,color='#000'){return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${622*scale} 624" width="${622*scale}" height="624"><g transform="scale(${scale} 1)">${shape(gap,color)}</g></svg>`;}
function save(name,s){fs.writeFileSync(path.join(dir,name),s);}
function render(name,s,width,bg='white'){save(name,new Resvg(s,{fitTo:{mode:'width',value:width},background:bg}).render().asPng());}
const variants=[['01A 基线',1,25,'baseline'],['01A 更窄 4%',.96,25,'narrow'],['01A 更宽 4%',1.04,25,'wide']];
for(const [,scale,gap,id]of variants){let s=svg(scale,gap);save(`${id}.svg`,s);render(`${id}-512.png`,s,512);for(const size of[16,32,64])render(`${id}-${size}.png`,s,size);}
save('baseline-green.svg',svg(1,25,'#10b981'));save('baseline-white.svg',svg(1,25,'#fff'));
save('micro.svg',svg(1,40));for(const size of[16,32,64])render(`micro-${size}.png`,svg(1,40),size);
let elements=[];const text=(x,y,t,size=18,color='#24332d')=>elements.push(`<text x="${x}" y="${y}" font-size="${size}" fill="${color}" font-family="Microsoft YaHei, sans-serif">${t}</text>`);
const mark=(x,y,w,h,gap=25,color='#000')=>elements.push(`<g transform="translate(${x} ${y}) scale(${w/622} ${h/624})">${shape(gap,color)}</g>`);
elements.push('<rect width="1320" height="1160" fill="#f6f8f6"/>');text(60,65,'智面幻境 · 01A 比例精修与识别验证',30);text(60,102,'保留右高左低双肩、双门与开放入口｜绿色 #10B981｜候选稿',16);
variants.forEach(([label,s,g,id],i)=>{let x=60+i*420;elements.push(`<rect x="${x}" y="135" width="380" height="330" rx="12" fill="white"/>`);mark(x+(380-240*s)/2,166,240*s,240);text(x+24,441,label,18);});
text(60,510,'实际像素尺寸：基线与小尺寸补偿',23);text(60,540,'小尺寸补偿仅加宽门框侧缝：25 → 40 单位，保持外轮廓与中央通道。',16);
for(let row=0;row<2;row++){let y=578+row*100;text(60,y+28,row?'补偿版':'基线');[16,32,64].forEach((size,i)=>{let x=235+i*220;mark(x,y,size,size,row?40:25);text(x+90,y+28,`${size} px`,16);});}
text(60,820,'品牌色与使用场景',23);
elements.push('<rect x="60" y="845" width="380" height="220" rx="14" fill="#fff"/>');mark(85,920,40,40,25,'#10b981');text(142,947,'智面幻境',24);text(85,1035,'导航栏 · 40 px 标志',15);
elements.push('<rect x="470" y="845" width="240" height="220" rx="14" fill="#10b981"/>');mark(536,890,108,108,25,'#fff');text(488,1040,'应用图标 · 白色反白',15,'#fff');
elements.push('<rect x="740" y="845" width="240" height="220" rx="14" fill="#10251d"/>');mark(806,890,108,108,25,'#fff');text(760,1040,'深色背景 · 白色',15,'#fff');
elements.push('<rect x="1010" y="845" width="250" height="220" rx="14" fill="#fff"/>');mark(1081,890,108,108,25,'#000');text(1032,1040,'单色印刷 · 黑色',15);
text(60,1120,'设计判断：基线优先；16 px 使用补偿版。场景为示意，字标尚未设计。',16);
let board=`<svg xmlns="http://www.w3.org/2000/svg" width="1320" height="1160" viewBox="0 0 1320 1160">${elements.join('')}</svg>`;save('review-board.svg',board);render('review-board.png',board,1320);
const audit={};for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.svg'))){let s=fs.readFileSync(path.join(dir,name),'utf8');audit[name]={pathCount:(s.match(/<path\b/g)||[]).length,forbidden:/<image|data:|<script|<foreignObject|<filter|href=/i.test(s)};}save('svg-audit.json',JSON.stringify(audit,null,2));console.log('Generated editable symbols, 16/32/64 px exports and review board.');
