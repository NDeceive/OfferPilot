import request from '@/utils/request'

export const login = (data) => request.post('/auth/login', data)

export const register = (data) => request.post('/auth/register', data)

export const getMe = () => request.get('/user/me')

export const getJobList = () => request.get('/job/list')

export const getJobDetail = (id) => request.get(`/job/${id}`)

export const saveResume = (data) => request.post('/resume', data)

export const getMyResume = () => request.get('/resume/mine')
