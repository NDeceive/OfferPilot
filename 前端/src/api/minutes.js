import request from '../utils/request'
import download from '../utils/download'
const base = sid => `/interview/${sid}/minutes`
export const getMinutes = (sid, signal) => request.get(base(sid), { signal })
export const getReportMinutesSession = (id, signal) => request.get(`/interview/minutes/by-report/${id}`, { signal })
export const getMinutesEntries = (sid, afterSequence, signal) => request.get(`${base(sid)}/entries`, { params: { afterSequence, limit: 100 }, signal })
export const getMinutesSummary = (sid, signal) => request.get(`${base(sid)}/summary`, { signal })
export const controlMinutes = (sid, data, signal) => request.put(`${base(sid)}/control`, data, { signal })
export const generateMinutesSummary = (sid, requestId, signal) => request.post(`${base(sid)}/summary-jobs`, { requestId }, { signal })
export const deleteMinutes = (sid, signal) => request.delete(base(sid), { signal })
export async function exportMinutes(sid, kind, format = 'md', signal) {
  const response = await download.get(`${base(sid)}/export`, { params: { kind, format }, signal })
  if (response.data.type?.includes('json')) {
    const result = JSON.parse(await response.data.text())
    throw new Error(result.message || '导出失败，请重试')
  }
  return response.data
}
