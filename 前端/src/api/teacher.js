import request from '@/utils/request'

/**
 * 教师端总览数据（Phase 5.4）。
 * GET /api/teacher/dashboard/overview
 * 仅 TEACHER / ADMIN 可访问，STUDENT 访问返回 403。
 * @returns {Promise<{
 *   summary: Object,
 *   trainingTrend: Array,
 *   weaknessDistribution: Array,
 *   studentTrainingList: Array,
 *   commonProblems: Array
 * }>}
 */
export const getTeacherDashboardOverview = () =>
  request.get('/teacher/dashboard/overview')
