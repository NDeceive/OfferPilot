/**
 * 离线演示模式的开关与询问。
 *
 * 背景：比赛现场手机要么连开发机后端（局域网），要么完全没网。后者需要一个
 * 内置假数据的兜底形态，好让演示不至于当场挂掉。
 *
 * 设计上刻意**不静默切换**：连不上后端时弹框问一次，用户点了确认才切。
 * 理由是演示时假数据比没数据更危险——一旦界面无声无息换成假数字，讲的人
 * 未必察觉，就会照着编。所以切过去之后还有一条常驻横幅提醒。
 */
import { reactive } from 'vue'

const STORAGE_KEY = 'offerpilot.offlineDemo'

export const demoState = reactive({
  /** 当前是否处于离线演示模式 */
  active: false,
  /** 是否正在显示「要不要切到演示模式」的确认框 */
  asking: false,
  /**
   * 用户是否已经就这次「连不上后端」做过选择（同意或拒绝都算）。
   *
   * 没有它的话，用户在弹框上点了「不用」，下一个失败请求会立刻再弹一次——
   * 首屏就有好几个请求，会弹到人崩溃。拒绝一次就闭嘴，想切可以从横幅或
   * 登录页手动开。
   */
  answered: false,
})

try {
  demoState.active = localStorage.getItem(STORAGE_KEY) === '1'
} catch {
  /* 存储被禁用，那就每次重来，不影响使用 */
}

/** 开关演示模式并持久化 */
export function setDemoActive(value) {
  demoState.active = !!value
  // 关掉的时候把「已问过」一并复位：下次连不上后端应该重新问一次
  if (!value) demoState.answered = false
  try {
    if (value) localStorage.setItem(STORAGE_KEY, '1')
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* 忽略：持久化失败只影响下次启动的记忆 */
  }
}

// 询问的 promise 放在模块作用域而不是 reactive 对象里——放进 reactive 会被 Vue
// 包一层代理，promise 的身份就不稳定了。
let pendingPromise = null
let pendingResolve = null

/**
 * 询问用户是否切到离线演示模式。
 *
 * 并发调用会合并成同一次询问：页面首屏往往同时发好几个请求，全失败时会一起
 * 走到这里，不合并的话会叠出好几个确认框。
 *
 * @returns {Promise<boolean>} 用户是否同意切换
 */
export function askSwitchToDemo() {
  if (pendingPromise) return pendingPromise
  demoState.asking = true
  pendingPromise = new Promise((resolve) => {
    pendingResolve = resolve
  })
  return pendingPromise
}

/** 用户对确认框做出选择（由全局弹框组件调用） */
export function answerDemoPrompt(accepted) {
  demoState.asking = false
  demoState.answered = true
  const resolve = pendingResolve
  pendingPromise = null
  pendingResolve = null
  if (accepted) setDemoActive(true)
  if (resolve) resolve(accepted)
}

/**
 * 请求失败时的统一入口，决定「现在能不能用假数据重放这一条」。
 *
 * 三种情形分开处理，是这几个状态位存在的全部理由：
 *   - 已经在演示模式       → 直接可以重放
 *   - 用户已经拒绝过       → 不可以，也不再打扰
 *   - 正在问 / 还没问过    → 去问（已经在问就等那一次的结果）
 *
 * @returns {Promise<boolean>}
 */
export function requestDemoFallback() {
  if (demoState.active) return Promise.resolve(true)
  if (demoState.answered) return Promise.resolve(false)
  return askSwitchToDemo()
}
