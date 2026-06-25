import request from '@/utils/request'

export const login = (data) => request.post('/auth/login', data)

export const register = (data) => request.post('/auth/register', data)

export const getMe = () => request.get('/user/me')

export const getJobList = () => request.get('/job/list')

export const getJobDetail = (id) => request.get(`/job/${id}`)

export const saveResume = (data) => request.post('/resume', data)

export const getMyResume = () => request.get('/resume/mine')

/** 当前用户训练统计 */
export const getMyStats = () => request.get('/user/stats')

/** 修改个人资料（昵称/密码） */
export const updateProfile = (data) => request.put('/user/profile', data)

/** 面试记录列表 */
export const getInterviewRecords = () => request.get('/interview/records')

/** 开始面试：{ jobId, difficulty } → { sessionId, jobName, question } */
export const startInterview = (data) => request.post('/interview/start', data)

/** 提交回答：{ questionId, answer } → { nextAction, followupQuestion } */
export const submitAnswer = (sessionId, data) => request.post(`/interview/${sessionId}/answer`, data)

/** 获取下一题 → { nextAction, question } */
export const getNextQuestion = (sessionId) => request.get(`/interview/${sessionId}/next`)

/** 结束面试 → reportId（用于跳转报告页） */
export const finishInterview = (sessionId) => request.post(`/interview/${sessionId}/finish`)

/** 能力报告详情 → { reportId, jobName, totalScore, summary, strengths, weaknesses, suggestions, weakTags, dimensions } */
export const getReportDetail = (reportId) => request.get(`/report/${reportId}`)
