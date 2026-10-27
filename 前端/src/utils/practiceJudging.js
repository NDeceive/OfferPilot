export const pendingJudgeStatuses = new Set(['QUEUED','COMPILING','RUNNING'])
const labels = { QUEUED:'排队中', COMPILING:'编译中', RUNNING:'执行中', ACCEPTED:'测试通过', WRONG_ANSWER:'输出错误', COMPILE_ERROR:'编译错误', RUNTIME_ERROR:'运行错误', TIME_LIMIT_EXCEEDED:'运行超时', MEMORY_LIMIT_EXCEEDED:'超出内存限制', OUTPUT_LIMIT_EXCEEDED:'超出输出限制', SYSTEM_ERROR:'判题系统异常', SERVICE_UNAVAILABLE:'判题服务不可用', SELF_REVIEW:'已提交，待自评', OBJECTIVE_CORRECT:'答案正确', OBJECTIVE_INCORRECT:'答案错误', REVIEWED:'历史关键词评价', NEEDS_REVIEW:'历史关键词评价：建议复习' }
export function judgeStatusLabel(status) { return ({SUBMITTED:'已提交，待统一结算',DEMO_RUN:'样例运行已完成'})[status] || labels[status] || '未知状态，待核实' }
export function compileLocations(log) {
  return [...String(log || '').matchAll(/(?:^|\n)(?:[^\n]*[/\\])?Main\.java:(\d+):(?:\s*(\d+):)?\s*(?:error|错误):/g)].map(m=>({line:Number(m[1]),column:Number(m[2] || 1)})).filter(p=>p.line>0 && p.line<100000)
}
export function restoreJudgeResult(latest,saved) {
  if(latest?.result) return {...latest.result,submitted:true}
  if(latest?.id && (!saved?.submissionId || Number(saved.submissionId)<Number(latest.id))) return {submissionId:latest.id,submitted:true,status:latest.status,score:latest.score,compileOutput:latest.compileOutput,stderr:latest.stderr,stdout:latest.stdout,passed:latest.isCorrect==null?null:Boolean(latest.isCorrect)}
  return saved || null
}
