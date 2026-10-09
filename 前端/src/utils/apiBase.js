/**
 * API 根地址的解析。
 *
 * 取值的优先级：
 *   1. localStorage 的 'offerpilot.apiBase' —— 运行时可改
 *   2. 构建时注入的 VITE_API_BASE_URL
 *   3. '/api' —— Web 端走 Vite 代理
 *
 * 为什么需要第 1 条：VITE_API_BASE_URL 是构建时**写死进产物**的。手机 APK 靠局域网
 * 连开发机时，IP 换了就得重新打包，而比赛现场不可能现装一遍 Android SDK 重新构建。
 * 留一个能改的口子，现场换 WiFi 网段只要在登录页填一下就行。
 *
 * 注意这个键必须扛得住退出登录——store/user.js 的 logout() 以前调 localStorage.clear()，
 * 会连带清掉它（已改成只删自己的账号键）。
 */
const RUNTIME_KEY = 'offerpilot.apiBase'

/** 取当前生效的 API 根地址，结尾不带斜杠 */
export function getApiBase() {
  try {
    const runtime = localStorage.getItem(RUNTIME_KEY)
    if (runtime) return runtime.replace(/\/+$/, '')
  } catch {
    /* 存储被禁用，退回构建时配置 */
  }
  return import.meta.env.VITE_API_BASE_URL || '/api'
}

/** 运行时覆盖 API 根地址；传空字符串表示清除覆盖、退回构建时配置 */
export function setApiBase(value) {
  const trimmed = (value || '').trim().replace(/\/+$/, '')
  if (trimmed) localStorage.setItem(RUNTIME_KEY, trimmed)
  else localStorage.removeItem(RUNTIME_KEY)
}

/** 只读运行时覆盖值（回填输入框用），没设过返回空串 */
export function getRuntimeApiBase() {
  try {
    return localStorage.getItem(RUNTIME_KEY) || ''
  } catch {
    return ''
  }
}
