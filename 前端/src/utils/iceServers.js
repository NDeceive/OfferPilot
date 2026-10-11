/**
 * WebRTC ICE 服务器配置。
 *
 * 局域网演示其实不需要 STUN/TURN——同网段主机候选（host candidate）直接互通；
 * 这里默认带一个公共 STUN，只为「不同网段/挂代理出网」时多拿一个 srflx 候选兜底。
 *
 * 将来要跨严格 NAT 上线时：自建 coturn，把地址经 VITE_ICE_SERVERS 注入
 * （逗号分隔，如 "stun:turn.example.com:3478"）。若 TURN 需要账号密码，
 * 把本文件改为返回 [{ urls, username, credential }] 即可，调用方不用动。
 */

const DEFAULT_ICE_SERVERS = [{ urls: 'stun:stun.l.google.com:19302' }]

/**
 * 解析注入的服务器列表（纯函数，可单测）。
 * 空/全空白 → 回退默认 STUN；解析出的每一项包成 { urls } 结构。
 */
export function parseIceServers(raw) {
  const urls = String(raw ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  return urls.length ? urls.map((url) => ({ urls: url })) : DEFAULT_ICE_SERVERS
}

let cached = null

/** 会话级只解析一次（VITE_ICE_SERVERS 是构建期常量） */
export function getIceServers() {
  if (!cached) cached = parseIceServers(import.meta.env.VITE_ICE_SERVERS)
  return cached
}
