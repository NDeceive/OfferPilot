/**
 * Axios instance for binary/file download requests.
 * Does NOT strip the Result envelope; returns the full response.
 */
import axios from 'axios'
import { useUserStore } from '../store/user'
import { getApiBase } from './apiBase'
import { demoState } from './offlineDemo'

const downloadRequest = axios.create({
  baseURL: getApiBase(),
  timeout: 60000,
  responseType: 'blob',
})

downloadRequest.interceptors.request.use((config) => {
  // 和 request.js 一样每次重取：服务器地址允许在运行时就地改
  config.baseURL = getApiBase()
  // 导出要靠后端现渲染 PDF/Word，离线演示模式没有后端可问。
  // 在这里挡住，报错信息才是人能看懂的一句；放它出门只会得到「Network Error」。
  if (demoState.active) {
    return Promise.reject(new Error('离线演示模式不支持导出报告'))
  }
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

downloadRequest.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const store = useUserStore()
      store.logout()
      // dynamic import to avoid circular dep
      import('../router').then(({ default: router }) => router.push('/login'))
    }
    return Promise.reject(error)
  }
)

export default downloadRequest
