import request from '../utils/request'
import downloadRequest from '../utils/download'

/* ==================== Auth ==================== */
export const login = (data) => request.post('/auth/login', data)
export const register = (data) => request.post('/auth/register', data)

/* ==================== User ==================== */
export const getMe = () => request.get('/user/me')
export const getMyStats = () => request.get('/user/stats')
export const getDashboardOverview = () => request.get('/dashboard/overview')
export const getDashboardProfile = () => request.get('/user/dashboard/profile')
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
export const updateResumeTags = (tags) => request.put('/resume/tags', { tags })
export const getSkillTags = () => request.get('/tags')

/* ==================== Interview ==================== */
export const getInterviewRecords = () => request.get('/interview/records')
export const startInterview = (data) => request.post('/interview/start', data)
export const submitAnswer = (sessionId, data) => request.post(`/interview/${sessionId}/answer`, data)
export const getNextQuestion = (sessionId) => request.get(`/interview/${sessionId}/next`)
export const finishInterview = (sessionId) => request.post(`/interview/${sessionId}/finish`)
export const getReportStatus = (sessionId) => request.get(`/interview/${sessionId}/report-status`)
export const getSessionMessages = (sessionId) => request.get(`/interview/${sessionId}/messages`)
export const deleteInterview = (sessionId) => request.delete(`/interview/${sessionId}`)
export const submitFollowUp = (data) => request.post('/interview/follow-up', data)

/* ==================== Speech ==================== */
export const transcribeSpeech = (audioBlob, sessionId, duration) => {
  const formData = new FormData()
  formData.append('file', audioBlob, `speech-${Date.now()}.wav`)
  formData.append('sessionId', String(sessionId))
  formData.append('duration', String(duration))
  return request.post('/speech/transcribe', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 65000,
  })
}

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

/* ==================== Training Modules ==================== */
export const getModules = () => request.get('/modules')

/* ==================== Teacher ==================== */
export const getTeacherOverview = () => request.get('/teacher/dashboard/overview')
