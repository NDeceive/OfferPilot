/**
 * 岗位数据层：JobSelect（表单向导）与 AiPrep（AI 对话入口）共用。
 *
 * 之所以必须共用而不是各自维护一份，关键在 READY_JOBS —— 它是「题库能支撑面试」的
 * 正确性白名单，不是展示偏好。两个入口都允许用户选岗位，任何一份漏掉某个 code，
 * 用户选中后 POST /interview/start 就会抛「未找到匹配的面试题目」，而且只在演示时暴露。
 */

/**
 * 可面试岗位白名单。每个分区各 2 个岗位，题库支撑见 后端/.../db/migration_v4_job_banks.sql。
 * ⚠ 岗位与题目没有外键，全靠运行时模糊匹配，改任何标签都可能影响别的岗位——
 * 加岗位或改标签后必须跑 db/check_pools.py 复算，要求每个岗位可抽题数 >= 25。
 */
export const READY_JOBS = new Set([
  // 后端开发
  'BE-JAVA',     // Java后端开发工程师
  'BE-PY',       // Python后端开发工程师
  // 前端与客户端开发
  'FE-WEB',      // Web前端开发工程师
  'FE-ANDROID',  // Android原生开发工程师
  // 全栈开发
  'FS-JAVA',     // Java Web全栈开发工程师
  'FS-PY',       // Python Web全栈开发工程师
  // 算法与人工智能
  'ALG-ML',      // 机器学习算法工程师
  'ALG-NLP',     // NLP与大模型算法工程师
  // 产品经理
  'PM-C',        // C端产品经理
  'PM-B',        // B端/企业产品经理
  // 数据分析
  'DA-BIZ',      // 业务数据分析师
  'DA-PROD',     // 产品数据分析师
  // 软件测试
  'QA-AUTO',     // 自动化测试工程师
  'QA-PERF',     // 性能测试工程师
])

/** 岗位族定义，code 与后端 job.family 字段一致 */
export const JOB_FAMILIES = [
  { code: '后端开发', name: '后端开发', icon: 'B' },
  { code: '前端与客户端开发', name: '前端与客户端', icon: 'F' },
  { code: '全栈开发', name: '全栈开发', icon: 'S' },
  { code: '算法与人工智能', name: '算法与AI', icon: 'A' },
  { code: '产品经理', name: '产品经理', icon: 'P' },
  { code: '数据分析', name: '数据分析', icon: 'D' },
  { code: '软件测试', name: '软件测试', icon: 'Q' },
]

export const familyColorMap = {
  '后端开发':          { iconBg: 'rgba(99,102,241,0.08)',  accentColor: '#6366f1' },
  '前端与客户端开发':  { iconBg: 'rgba(249,115,22,0.08)',  accentColor: '#f97316' },
  '全栈开发':          { iconBg: 'rgba(16,185,129,0.08)',  accentColor: '#10b981' },
  '算法与人工智能':    { iconBg: 'rgba(236,72,153,0.08)',  accentColor: '#ec4899' },
  '产品经理':          { iconBg: 'rgba(6,182,212,0.08)',   accentColor: '#06b6d4' },
  '数据分析':          { iconBg: 'rgba(168,85,247,0.08)',  accentColor: '#a855f7' },
  '软件测试':          { iconBg: 'rgba(234,179,8,0.08)',   accentColor: '#eab308' },
}

/** 后端 keywords / abilities 字段可能是 JSON 字符串，也可能是已解析的数组 */
export function parseJsonField(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try { return JSON.parse(raw) } catch { return [raw] }
  }
  return []
}

/** 后端 Job 实体 → 前端展示用的岗位对象 */
export function mapJobFromBackend(job) {
  const colors = familyColorMap[job.family] || familyColorMap['后端开发']
  return {
    id: job.id,
    title: job.name,
    code: job.code || '',
    family: job.family || '',
    tags: parseJsonField(job.keywords),
    focus: parseJsonField(job.abilities),
    iconBg: colors.iconBg,
    accentColor: colors.accentColor,
    category: job.category || '',
    pro: false,
  }
}

/** 该岗位是否可面试 */
export function isReadyJob(job) {
  return !!job && READY_JOBS.has(job.code)
}
