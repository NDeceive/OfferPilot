/**
 * 企业端会议 REST 接口（与后端 /api/meeting/* 一一对应）。
 * 信令走 WebSocket（见 utils/meetingProtocol.js），这里只管建会/预检/落库/建议。
 */
import request from '../utils/request'

/* ==================== Meeting ==================== */

/** 创建会议（ENTERPRISE/TEACHER/ADMIN）→ {id, code, codeDisplay, title, status, ...} */
export const createMeeting = (data) => request.post('/meeting', data)

/** 凭会议号预检（任意登录角色）：joinable / participantCount / hostName，不含建议 */
export const previewMeeting = (code) => request.get(`/meeting/code/${encodeURIComponent(code)}`)

/** 我的会议：{hosted: [], joined: []}，各取最近 50 条 */
export const getMyMeetings = () => request.get('/meeting/mine')

/** 会议详情（发起人/参会者/ADMIN）：基本信息 + 参会记录 + 建议 */
export const getMeetingDetail = (id) => request.get(`/meeting/${id}`)

/** 结束会议（发起人或 ADMIN）→ 房间成员会被服务端移出 */
export const endMeeting = (id) => request.post(`/meeting/${id}/end`)

/** 建议列表（学生只会看到面向全体的和自己的） */
export const getMeetingAdvice = (id) => request.get(`/meeting/${id}/advice`)

/** 写建议（ENTERPRISE/TEACHER/ADMIN）：{content, targetUserId?} */
export const postMeetingAdvice = (id, data) => request.post(`/meeting/${id}/advice`, data)
