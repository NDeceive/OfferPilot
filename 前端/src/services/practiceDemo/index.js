// PRACTICE_DEMO: isolated temporary Axios transport.
import axios from 'axios'
import { createDemoEngine } from './engine'

let engine
function getEngine(){
  return engine ||= createDemoEngine(localStorage,()=>localStorage.getItem('userId'))
}
export const practiceDemoResources=(roles,families,companies)=>getEngine().loadResources(roles,families,companies)
export const practiceDemoSelfAssessment=(sessionId,questionId,state)=>getEngine().selfAssess(sessionId,questionId,state)
// Bind at the service boundary, including direct links and hot-reloaded modules.
export const practiceDemoRequest=axios.create({
  adapter: async config=>{
    if(!config.url?.startsWith('/practice/'))throw new Error('样例请求仅支持练习接口。')
    await new Promise(resolve=>setTimeout(resolve,180))
    const input=typeof config.data==='string'?JSON.parse(config.data):config.data || {}
    const data=await getEngine().handle(config.url,config.method,input,config.params || {})
    return {data,status:200,statusText:'OK',headers:{},config}
  }
})
practiceDemoRequest.interceptors.response.use(response=>response.data)
