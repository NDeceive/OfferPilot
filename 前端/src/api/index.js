import request from '../utils/request'
import downloadRequest from '../utils/download'

/* ==================== Auth ==================== */
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)

/* ==================== User ==================== */
export const getMe = () => request.get('/user/me')
export const getMyStats = () => request.get('/user/stats')
export const updateProfile = (data) => request.put('/user/profile', data)

/* ==================== Jobs ==================== */
export const getJobList = () => request.get('/job/list')
export const getJobDetail = (id) => request.get(`/job/${id}`)

/* ==================== Resume ==================== */
export const saveResume = (data) => request.post('/resume', data)
export const getMyResume = () => request.get('/resume/mine')
export const uploadResumeFile = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resume/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
export const getResumeFileProfile = () => request.get('/resume/file-profile')

/* ==================== Interview ==================== */
export const getInterviewRecords = () => request.get('/interview/records')
export const startInterview = (data) => request.post('/interview/start', data)
export const submitAnswer = (sessionId, data) => request.post(`/interview/${sessionId}/answer`, data)
export const getNextQuestion = (sessionId) => request.get(`/interview/${sessionId}/next`)
export const finishInterview = (sessionId) => request.post(`/interview/${sessionId}/finish`)
export const getReportStatus = (sessionId) => request.get(`/interview/${sessionId}/report-status`)
export const getSessionMessages = (sessionId) => request.get(`/interview/${sessionId}/messages`)
export const submitFollowUp = (data) => request.post('/interview/follow-up', data)

/* ==================== Follow-up Records ==================== */
export const getFollowUpRecords = (params) => request.get('/interview/follow-up-records', { params })
export const getFollowUpStats = () => request.get('/interview/follow-up-records/stats')

/* ==================== Reports ==================== */
export const getReportDetail = (reportId) => request.get(`/report/${reportId}`)
export const getImprovementPath = (reportId) => request.get(`/report/${reportId}/improvement-path`)
export const exportReport = (reportId, format = 'pdf') =>
  downloadRequest.get(`/report/${reportId}/export`, {
    params: { format },
    responseType: 'blob',
  })

/* ==================== AI 面试教练 ==================== */
// 仅 /status 走 axios；话术流用 utils/sse.js 的 fetch 版 SSE（axios 拿不到流式响应体）
export const getAiStatus = () => request.get('/ai/status')

/* ==================== Modules & Dashboard ==================== */
export const getModules = () => request.get('/modules')
export const getDashboardProfile = () => request.get('/user/dashboard/profile')

/* ==================== Teacher ==================== */
export const getTeacherOverview = () => request.get('/teacher/dashboard/overview')
