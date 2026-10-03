// Adds teaching tables only; never runs the legacy schema.sql reset script.
const fs=require('node:fs'),cp=require('node:child_process'),path=require('node:path');
const file=path.resolve(__dirname,'../后端/src/main/resources/db/migration_teaching_linkage.sql');
cp.execFileSync(process.env.MYSQL_BIN||'C:/Program Files/MySQL/MySQL Server 9.7/bin/mysql.exe',[
 '--host='+ (process.env.DB_HOST||'127.0.0.1'),'--user='+(process.env.DB_USER||'root'),
 '--database='+(process.env.DB_NAME||'zhimian'),'--default-character-set=utf8mb4'
],{env:{...process.env,MYSQL_PWD:process.env.DB_PASSWORD||'123456'},input:fs.readFileSync(file),stdio:['pipe','inherit','inherit']});
console.log('Teaching tables ready. Existing users, interviews and reports preserved.');
