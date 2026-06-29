import request from '@/utils/request'

/**
 * 分页查询追问记录（实验分析 / 论文写作用）。
 * @param {Object} params - { pageNo, pageSize, source, sessionId, jobId }
 *   - pageNo 页码，默认 1
 *   - pageSize 每页条数，默认 10
 *   - source 可选：AI / RULE
 *   - sessionId 可选：按会话过滤
 *   - jobId 可选：按岗位过滤
 * @returns { pageNo, pageSize, total, list }
 */
export const getFollowupRecords = (params) =>
  request.get('/interview/follow-up-records', { params })

/**
 * 追问记录统计：AI / RULE 数量与占比。
 * @returns { totalCount, aiCount, ruleCount, aiRatio, ruleRatio }
 */
export const getFollowupRecordStats = () =>
  request.get('/interview/follow-up-records/stats')
