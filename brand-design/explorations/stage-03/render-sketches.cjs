const fs=require('node:fs'),path=require('node:path');
const sharp=require('../../../前端/node_modules/sharp');
const dir=__dirname, routes=JSON.parse(fs.readFileSync(path.join(dir,'routes.json'),'utf8'));
fs.mkdirSync(path.join(dir,'assets'),{recursive:true});
const svg=(body,color='#111111')=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96" fill="${color}">${body}</svg>`;
(async()=>{
const layers=[];
for(let ri=0;ri<routes.length;ri++)for(let di=0;di<routes[ri].drafts.length;di++){
 const draft=routes[ri].drafts[di],source=svg(draft.body);
 fs.writeFileSync(path.join(dir,'assets',draft.id+'.svg'),source);
 const left=20+di*550,top=30+ri*225;
 for(const [j,size]of [150,16,32,64].entries()){
 const image=await sharp(Buffer.from(source)).resize(size,size).png().toBuffer();
 layers.push({input:image,left:left+(j===0?0:170+(j-1)*95),top:top+(j===0?0:60)});
 }
 const white=await sharp(Buffer.from(svg(draft.body,'#ffffff'))).resize(96,96).png().toBuffer();
 const bg=await sharp({create:{width:130,height:160,channels:4,background:'#111111'}}).composite([{input:white,left:17,top:23}]).png().toBuffer();
 layers.push({input:bg,left:left+400,top});
}
await sharp({create:{width:1120,height:1160,channels:4,background:'#f7f7f3'}}).composite(layers).png().toFile(path.join(dir,'sketch-inspection.png'));
console.log('10 original SVG schematic drafts and black/white inspection sheet ready.');
})();
