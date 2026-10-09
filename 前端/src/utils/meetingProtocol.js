/**
 * 会议信令协议常量与纯工具（与后端 MeetingSignalHandler 一一对应）。
 *
 * 本文件保持零依赖纯函数：node --test 直接跑源码做单测。
 * 因此 WS 地址推导不从 apiBase.js 取值（那个模块用了 import.meta.env，
 * node 下 import.meta.env 是 undefined，一碰就炸）——base 与 location
 * 全部由调用方注入，见 meetingWsUrl 的签名。
 */

/** 消息类型（顶层 type 字段） */
export const MSG = Object.freeze({
  // C→S
  JOIN: 'join',
  OFFER: 'offer',
  ANSWER: 'answer',
  ICE: 'ice',
  MIC: 'mic',
  CAM: 'cam',
  LEAVE: 'leave',
  // S→C
  JOINED: 'joined',
  PEER_JOINED: 'peer-joined',
  PEER_LEFT: 'peer-left',
  PEER_STATE: 'peer-state',
  ADVICE_NEW: 'advice-new',
  MEETING_ENDED: 'meeting-ended',
  ERROR: 'error',
})

/** 关闭码：与后端 MeetingSignalHandler 的常量保持一致 */
export const CLOSE = Object.freeze({
  NORMAL: 1000,
  KICKED: 4001,        // 同账号在其他窗口进入
  MEETING_ENDED: 4004, // 会议被发起人结束
  ROOM_FULL: 4005,     // 房间已满
})

/** 会议号字母表（与后端同款）：无 I/O/0/1，口头报号不易听错 */
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
export const CODE_LENGTH = 8

/** 归一化：大写、去空格与连字符（合法性不在这里判，交给 isValidCode / 后端） */
export function normalizeCode(raw) {
  return String(raw ?? '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]/g, '')
}

/** 输入框友好格式化：四位一组插连字符；不足 8 位时不强插（避免打字过程跳动） */
export function formatCodeInput(raw) {
  const code = normalizeCode(raw)
  return code.length > 4 ? `${code.slice(0, 4)}-${code.slice(4, CODE_LENGTH)}` : code
}

/** 会议号是否成形：长度正确且全部字符都在字母表内 */
export function isValidCode(raw) {
  const code = normalizeCode(raw)
  return code.length === CODE_LENGTH && [...code].every((c) => CODE_ALPHABET.includes(c))
}

/**
 * 由 API 根地址推导会议信令 WS 地址。
 *
 * - 相对 base（'/api'）：ws 指向当前页面同源——dev 下走 Vite 的 /ws 代理，
 *   生产同域直连 nginx，两端都不需要知道后端真实地址；
 * - 绝对 base（'http://ip:8080/api'，登录页「服务器设置」或 APK 运行时改过）：
 *   直连该后端的 /ws/meeting，http→ws、https→wss。
 *
 * @param {string} token JWT（浏览器 WS 构造器带不了请求头，只能走 query）
 * @param {{base?: string, location?: {protocol?: string, host?: string}|null}} [options]
 */
export function meetingWsUrl(token, options = {}) {
  const base = options.base ?? '/api'
  const loc = options.location ?? (typeof window !== 'undefined' ? window.location : null)
  let wsOrigin
  if (/^[a-z]+:\/\//i.test(base)) {
    const url = new URL(base)
    wsOrigin = `${url.protocol === 'https:' ? 'wss:' : 'ws:'}//${url.host}`
  } else {
    const protocol = loc?.protocol === 'https:' ? 'wss:' : 'ws:'
    wsOrigin = `${protocol}//${loc?.host || 'localhost'}`
  }
  const params = token ? `?token=${encodeURIComponent(token)}` : ''
  return `${wsOrigin}/ws/meeting${params}`
}
