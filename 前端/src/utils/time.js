/**
 * 后端 LocalDateTime 序列化为 ISO（2026-10-09T13:05:22.123），
 * 截到分钟展示。空值给占位符，避免页面出现 undefined。
 */
export function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
