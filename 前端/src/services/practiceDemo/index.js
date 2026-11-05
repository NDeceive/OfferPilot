// PRACTICE_DEMO: local engine used only while offline demo mode is active.
import { createDemoEngine } from './engine'

let engine
function getEngine(){
  return engine ||= createDemoEngine(localStorage,()=>localStorage.getItem('userId'))
}
export const practiceDemoResources=(roles,families,companies)=>getEngine().loadResources(roles,families,companies)
export const practiceDemoSelfAssessment=(sessionId,questionId,state)=>getEngine().selfAssess(sessionId,questionId,state)

// Shaped like a real Axios response so the shared request interceptor can keep
// its Result-envelope parsing identical for online and demo practice calls.
export async function practiceDemoAdapter(config){
  if(!config.url?.startsWith('/practice/'))throw new Error('请求暂不支持。')
  await new Promise(resolve=>setTimeout(resolve,180))
  const input=typeof config.data==='string'?JSON.parse(config.data):config.data || {}
  try{
    const data=await getEngine().handle(config.url,String(config.method||'get').toLowerCase(),input,config.params || {})
    return {data:{code:200,message:'success',data},status:200,statusText:'OK',headers:{},config}
  }catch(error){
    // 与通用 demoAdapter 一致：引擎错误也走「200 + 非 200 业务码」信封，
    // 让页面上看到真实原因，而不是被请求层误判成「连不上服务器」。
    return {data:{code:400,message:error?.message || '操作失败，请重试',data:null},status:200,statusText:'OK',headers:{},config}
  }
}
