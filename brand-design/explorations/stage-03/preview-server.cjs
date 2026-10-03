const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=__dirname;
http.createServer((req,res)=>{
 try{
  let file=path.resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
  if(file!==root&&!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}
  if(file===root)file=path.join(root,'creative-routes.html');
  res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.json':'application/json'})[path.extname(file)]||'text/plain; charset=utf-8');
  fs.createReadStream(file).on('error',()=>{res.statusCode=404;res.end('Not found');}).pipe(res);
 }catch{res.writeHead(400);res.end();}
}).listen(4312,'127.0.0.1',()=>console.log('Stage 03 preview: http://127.0.0.1:4312/'));
