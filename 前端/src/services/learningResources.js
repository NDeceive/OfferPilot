import { getDashboardOverview, getJobList } from '../api'

const STORAGE_KEY = 'offerpilot.learning.session'

const roleSeed = [
  ['BE-JAVA', 'Java后端开发工程师', '后端开发', 'Java / JVM / Spring / 数据库 / 分布式'],
  ['BE-PY', 'Python后端开发工程师', '后端开发', 'Python / Django / FastAPI / 数据处理'],
  ['BE-GO', 'Go后端开发工程师', '后端开发', 'Go / 并发 / 微服务 / 云原生'],
  ['BE-NODE', 'Node.js后端开发工程师', '后端开发', 'Node.js / TypeScript / NestJS / API'],
  ['BE-CPP', 'C/C++高性能后端开发工程师', '后端开发', 'C/C++ / 内存管理 / 网络编程 / 高性能'],
  ['FE-WEB', 'Web前端开发工程师', '前端与客户端', 'JavaScript / TypeScript / Vue / React / 性能'],
  ['FE-ANDROID', 'Android原生开发工程师', '前端与客户端', 'Kotlin / Jetpack / Android 架构'],
  ['FE-IOS', 'iOS原生开发工程师', '前端与客户端', 'Swift / SwiftUI / UIKit / 内存管理'],
  ['FE-CROSS', '跨端开发工程师（Flutter/RN）', '前端与客户端', 'Flutter / React Native / 原生桥接'],
  ['FE-MINI', '小程序与轻应用开发工程师', '前端与客户端', '小程序 / 平台 API / 多端适配'],
  ['FE-DESKTOP', '桌面客户端开发工程师', '前端与客户端', 'Electron / Qt / Chromium / 跨平台'],
  ['FS-JAVA', 'Java Web全栈开发工程师', '全栈开发', 'Java / Vue / React / 数据库 / DevOps'],
  ['FS-NODE', 'Node.js全栈开发工程师', '全栈开发', 'Node.js / React / Next.js / 云服务'],
  ['FS-PY', 'Python Web全栈开发工程师', '全栈开发', 'Python / Vue / PostgreSQL / 部署'],
  ['FS-AI', 'AI应用全栈开发工程师', '全栈开发', 'LLM / RAG / Agent / 向量数据库'],
  ['ALG-ML', '机器学习算法工程师', '算法与人工智能', '机器学习 / 特征工程 / 模型评估'],
  ['ALG-NLP', 'NLP与大模型算法工程师', '算法与人工智能', 'NLP / Transformer / LLM / RAG'],
  ['ALG-CV', '计算机视觉算法工程师', '算法与人工智能', 'CV / CNN / 目标检测 / 分割'],
  ['ALG-REC', '推荐搜索广告算法工程师', '算法与人工智能', '召回 / 排序 / CTR / A/B 实验'],
  ['ALG-SPEECH', '语音算法工程师', '算法与人工智能', 'ASR / TTS / 信号处理 / 端到端模型'],
  ['ALG-MM', '多模态与具身智能算法工程师', '算法与人工智能', '多模态 / VLM / 生成模型 / 对齐'],
  ['ALG-MLOPS', '算法工程化与MLOps工程师', '算法与人工智能', 'MLOps / 模型部署 / 特征平台 / 监控'],
  ['PM-C', 'C端产品经理', '产品经理', '用户研究 / 需求分析 / 增长 / 项目推进'],
  ['PM-B', 'B端企业产品经理', '产品经理', '行业分析 / 客户需求 / 商业化 / 系统设计'],
  ['PM-AI', 'AI产品经理', '产品经理', 'LLM 评估 / Prompt / 数据策略 / AI 产品'],
  ['DA-BIZ', '业务数据分析师', '数据分析', 'SQL / 指标体系 / 业务洞察 / 统计'],
  ['DA-PROD', '产品数据分析师', '数据分析', '用户行为 / A/B 实验 / 埋点 / 转化'],
  ['DA-BI', 'BI与数据可视化工程师', '数据分析', 'BI / 数仓 / ETL / SQL / 数据治理'],
  ['QA-FUNC', '功能测试工程师', '软件测试', '用例设计 / 缺陷管理 / 回归测试'],
  ['QA-AUTO', '自动化测试工程师', '软件测试', '自动化框架 / 脚本 / CI/CD / 接口测试'],
  ['QA-PERF', '性能测试工程师', '软件测试', '压测 / 瓶颈分析 / 容量规划 / 监控'],
  ['QA-SDET', '测试开发质量工程师', '软件测试', '测试平台 / 工具链 / 质量度量 / 契约测试'],
]

export const roleFamilies = [
  { code: 'BE', name: '后端开发' }, { code: 'FE', name: '前端与客户端' },
  { code: 'FS', name: '全栈开发' }, { code: 'ALG', name: '算法与人工智能' },
  { code: 'PM', name: '产品经理' }, { code: 'DA', name: '数据分析' }, { code: 'QA', name: '软件测试' },
]

export const companies = [
  { name: '不限公司', industry: '全部' }, { name: '字节跳动', industry: '互联网' },
  { name: '美团', industry: '互联网' }, { name: '华为', industry: '通信与硬件' },
  { name: '阿里巴巴', industry: '电商零售' }, { name: '腾讯', industry: '互联网' },
  { name: '京东', industry: '电商零售' }, { name: '小米', industry: '通信与硬件' },
  { name: '百度', industry: '互联网' }, { name: '阿里云', industry: '云计算' },
]

const topicOverrides = {
  'BE-JAVA': ['综合高频', 'Java核心', 'JVM', '并发编程', 'Spring', 'MySQL', 'Redis', '分布式', '系统设计', '项目场景', '编程与算法'],
  'FE-WEB': ['综合高频', 'JavaScript', 'TypeScript', 'Vue / React', '浏览器原理', '工程化', '性能优化', '网络', '编程与算法'],
}

const demoQuestion = {
  id: 'coding-two-sum', type: 'coding', title: '两数之和', difficulty: '基础', frequency: '高频',
  tags: ['数组', '哈希表'],
  description: '给定一个整数数组 nums 和一个整数目标值 target，请返回和为 target 的两个整数下标。',
  constraints: ['每种输入只会对应一个答案', '同一元素不能重复使用', '答案顺序不限'],
  signature: 'public int[] twoSum(int[] nums, int target)',
  starter: `import java.util.*;\n\nclass Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // 在这里编写你的代码\n        return new int[0];\n    }\n}`,
  examples: [
    { input: 'nums = [2,7,11,15], target = 9', expected: '[0,1]' },
    { input: 'nums = [3,2,4], target = 6', expected: '[1,2]' },
    { input: 'nums = [3,3], target = 6', expected: '[0,1]' },
  ],
  followUps: [
    { text: '请说明这段实现的时间复杂度和空间复杂度。', lineStart: 5, lineEnd: 10 },
    { text: '为什么这里选择 HashMap？它的平均查找复杂度从何而来？', lineStart: 5, lineEnd: 10 },
  ],
}

function normalizeRole(job) {
  const code = job.code || job.directionCode
  return {
    id: job.id || code,
    code,
    name: job.name || job.title,
    family: job.family || job.category,
    summary: parseList(job.keywords).slice(0, 5).join(' / ') || job.description,
  }
}

function parseList(value) {
  if (Array.isArray(value)) return value
  try { return JSON.parse(value || '[]') } catch { return [] }
}

function fallbackRoles() {
  return roleSeed.map(([code, name, family, summary]) => ({ id: code, code, name, family, summary }))
}

export async function loadLearningResources() {
  let roles
  let overview = null
  try {
    const response = await getJobList()
    roles = Array.isArray(response) && response.length ? response.map(normalizeRole) : fallbackRoles()
  } catch {
    roles = fallbackRoles()
  }
  try { overview = await getDashboardOverview() } catch { /* Optional context. */ }
  return {
    roles,
    families: roleFamilies.map(family => ({ ...family, count: roles.filter(role => role.code?.startsWith(`${family.code}-`)).length })),
    companies,
    recentSession: getStoredSession(),
    currentRole: roles.find(role => role.name === overview?.latestInsight?.jobName || role.name === overview?.recentInterviews?.[0]?.jobName) || null,
    currentFocus: overview?.latestInsight?.weakestDimension || '',
    libraryCounts: { mistakes: 12, favorites: 8, recent: 5 },
  }
}

export function getTopics(role) {
  if (!role) return []
  if (topicOverrides[role.code]) return topicOverrides[role.code]
  const derived = String(role.summary || '').split('/').map(item => item.trim()).filter(Boolean)
  return ['综合高频', ...new Set(derived), '项目场景', '编程与算法'].slice(0, 11)
}

export function topicQuestionTypes(topic) {
  if (topic === '编程与算法') return ['coding']
  return ['all', 'knowledge']
}

export function createTrainingSession(config) {
  const session = {
    id: Date.now(),
    ...config,
    currentQuestionIndex: 0,
    questionList: [demoQuestion],
    stage: 'ASK',
    startedAt: new Date().toISOString(),
    completed: 0,
    judgeMode: 'local-demo',
    code: demoQuestion.starter,
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  return session
}

export function getTrainingSession(id) {
  const session = getStoredSession()
  return session && String(session.id) === String(id) ? session : null
}

export function saveTrainingSession(session) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function runLocalDemo(code, examples = demoQuestion.examples) {
  const implementsMap = /HashMap|Map\s*</.test(code) && /return\s+new\s+int\s*\[\]/.test(code)
  if (!implementsMap) {
    return { mode: 'local-demo', passed: false, message: '本地演示检查未找到完整的 HashMap 解法。它不代表真实 Judge 结果。', cases: [] }
  }
  return {
    mode: 'local-demo', passed: true,
    message: '以下为本地演示用例检查，非真实 Judge 隐藏测试。',
    cases: examples.map((item, index) => ({ ...item, name: `示例 ${index + 1}`, output: item.expected, status: '演示通过' })),
  }
}

function getStoredSession() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null') } catch { return null }
}
