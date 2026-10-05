/**
 * Axios instance for JSON API requests.
 * Intercepts JWT, parses Result<T> envelope, handles 401.
 */
import axios from 'axios'
import router from '../router'
import { useUserStore } from '../store/user'
import { getApiBase } from './apiBase'
import demoAdapter from './demoAdapter'
import { demoState, requestDemoFallback } from './offlineDemo'

const request = axios.create({
  baseURL: getApiBase(),
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  // 每次请求都重取一次：baseURL 允许在运行时就地改（登录页的「服务器设置」），
  // 只在 create 时取一次的话，改完得刷新页面才生效。
  config.baseURL = getApiBase()
  // 离线演示模式：整个请求交给本地假后端，一步都不出网
  if (demoState.active) config.adapter = demoAdapter
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

/**
 * 这次失败是不是「后端根本没连上」。
 *
 * 刻意不用 `!error.response` 一把梭：超时（ECONNABORTED）也会没有 response，
 * 但那说明地址是通的、只是后端慢，这时候劝人切假数据是帮倒忙。主动取消同理。
 */
function isOfflineFailure(error) {
  if (error.response) return false
  if (axios.isCancel(error)) return false
  return error.code !== 'ECONNABORTED' && error.code !== 'ETIMEDOUT'
}

/**
 * 把 axios 的 "Network Error" 换成人话。
 *
 * 几个页面直接把这个 message 显示给用户（比如登录页的红字），照着念给评委听
 * 很尴尬；带上实际用的地址还有个实际好处——IP 变了能一眼看出来。
 */
function describeOfflineFailure(error) {
  // 不同环境的原话不一样（浏览器 "Network Error"、Capacitor "Network request failed"），
  // 一律换掉，反正它们对用户都没有信息量
  error.message = `连不上服务器：${getApiBase()}`
  return error
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async (error) => {
    if (error.response?.status === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
      return Promise.reject(error)
    }

    // 连不上后端时问一次要不要切到离线演示，用户点了确认才切——静默切换会让
    // 演示的人照着假数据往下讲。
    //
    // 这里 await 挡住：首屏几个请求会一起失败，会合并成同一次询问（见 offlineDemo
    // 的 requestDemoFallback），每个请求各等这个结果，确认后各自重放自己那份。
    if (isOfflineFailure(error)) {
      // 用户点了「先不切」，或者已经拒绝过：把错误说清楚再抛出去
      describeOfflineFailure(error)
      if (error.config) {
        const accepted = await requestDemoFallback()
        if (accepted) {
          // 请求拦截器会重新跑一遍，把 adapter 换成假后端；body 已经是 JSON 字符串，
          // 再过一次 transformRequest 是原样返回，不会二次序列化。
          return request(error.config)
        }
      }
    }

    return Promise.reject(error)
  }
)

export default request
