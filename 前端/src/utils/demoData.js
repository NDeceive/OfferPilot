/**
 * 离线演示模式的假数据。
 *
 * 两条自我约束：
 *   1. 字段名严格照后端 DTO 抄（`DashboardOverviewResponse`、`InterviewStep`、
 *      `ReportDetailResponse` 等），不自己发明——前端读的就是这些路径，
 *      编错一个字段名的表现是"页面某块空白"，很难当场排查。
 *   2. 内容尽量用**真数据**：岗位、评分模块、面试题和追问都取自项目自己的
 *      MySQL 题库，不是编的。编出来的题一眼假，演示时经不起追问。
 */

/** 相对今天的日期，让演示数据看起来是新鲜的 */
function daysAgo(n, hour = 10) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  d.setHours(hour, 30, 0, 0)
  return d.toISOString().slice(0, 19)
}

function daysFromNow(n) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d.toISOString().slice(0, 10)
}

// ---------------------------------------------------------------------------
// 岗位（取自 job_position 表，keywords 在后端是 JSON 数组字符串）
// ---------------------------------------------------------------------------
const JOB_ROWS = [
  ['BE-JAVA', 'Java后端开发工程师', '后端开发', ['Java', 'JVM', '并发', 'Spring', 'SpringBoot', 'MyBatis', 'MySQL', 'Redis', '消息队列', '微服务']],
  ['BE-PY', 'Python后端开发工程师', '后端开发', ['Python', 'Django', 'Flask', 'FastAPI', 'Celery', 'PostgreSQL', 'MongoDB', 'RESTful']],
  ['BE-GO', 'Go后端开发工程师', '后端开发', ['Go', 'Goroutine', 'gRPC', 'protobuf', 'Kubernetes', 'Docker', 'Redis']],
  ['BE-NODE', 'Node.js后端开发工程师', '后端开发', ['Node.js', 'Express', 'NestJS', 'TypeScript', 'MongoDB', 'Redis', 'GraphQL']],
  ['BE-CPP', 'C/C++高性能后端开发工程师', '后端开发', ['C++', 'STL', 'TCP/IP', 'epoll', '多线程', '内存池', 'gRPC', 'Linux内核']],
  ['FE-WEB', 'Web前端开发工程师', '前端与客户端开发', ['HTML', 'CSS', 'JavaScript', 'TypeScript', 'Vue', 'React', 'webpack', 'Vite', '浏览器', '性能优化']],
  ['FE-ANDROID', 'Android原生开发工程师', '前端与客户端开发', ['Kotlin', 'Jetpack', 'Compose', 'ViewModel', '协程', 'Retrofit', 'MVVM']],
  ['FE-IOS', 'iOS原生开发工程师', '前端与客户端开发', ['Swift', 'SwiftUI', 'UIKit', 'Combine', 'CoreData', 'GCD', 'Xcode']],
  ['FE-CROSS', '跨端开发工程师（Flutter/React Native）', '前端与客户端开发', ['Flutter', 'React Native', 'Dart', 'TypeScript', 'Redux', '原生桥接', '多端发布']],
  ['FE-MINI', '小程序与轻应用开发工程师', '前端与客户端开发', ['微信小程序', '支付宝小程序', 'uni-app', 'Taro', '云开发', '分包', '性能']],
  ['FE-DESKTOP', '桌面客户端开发工程师', '前端与客户端开发', ['Electron', 'Qt', 'Chromium', 'Node.js', 'C++', '跨平台', '代码签名']],
  ['FS-JAVA', 'Java Web全栈开发工程师', '全栈开发', ['Java', 'Spring', 'SpringBoot', 'Vue', 'React', 'MySQL', 'Docker', 'CI/CD', 'Nginx']],
  ['FS-NODE', 'Node.js全栈开发工程师', '全栈开发', ['Node.js', 'NestJS', 'React', 'Next.js', 'TypeScript', 'Prisma', 'PostgreSQL']],
  ['FS-PY', 'Python Web全栈开发工程师', '全栈开发', ['Python', 'Django', 'Flask', 'FastAPI', 'Celery', 'asyncio', 'Vue', 'PostgreSQL']],
  ['FS-AI', 'AI应用全栈开发工程师', '全栈开发', ['LangChain', 'VectorDB', 'Pinecone', 'Streamlit', 'Next.js', 'Python', 'TypeScript', 'RAG']],
  ['ALG-ML', '机器学习算法工程师', '算法与人工智能', ['机器学习', 'XGBoost', 'LightGBM', 'scikit-learn', '特征工程', 'Python', 'SQL', 'A/B测试']],
  ['ALG-NLP', 'NLP与大模型算法工程师', '算法与人工智能', ['NLP', 'Transformer', '注意力机制', 'BERT', '大语言模型', 'LoRA', 'RLHF', 'RAG', 'Prompt工程']],
  ['ALG-CV', '计算机视觉算法工程师', '算法与人工智能', ['OpenCV', 'PyTorch', 'YOLO', 'ResNet', 'ViT', 'GAN', 'TensorRT', '图像处理']],
  ['ALG-REC', '推荐搜索广告算法工程师', '算法与人工智能', ['推荐', '协同过滤', 'DSSM', 'DeepFM', '多目标优化', '向量检索', 'CTR预估']],
  ['ALG-SPEECH', '语音算法工程师', '算法与人工智能', ['ASR', 'TTS', 'Kaldi', 'Whisper', '信号处理', 'MFCC', 'CTC', 'PyTorch']],
  ['ALG-MM', '多模态与具身智能算法工程师', '算法与人工智能', ['CLIP', '扩散模型', '具身智能', '3D视觉', 'VLM', 'RLHF', '多模态对齐']],
  ['ALG-MLOPS', '算法工程化与MLOps工程师', '算法与人工智能', ['MLflow', 'Kubeflow', 'Docker', 'Kubernetes', 'TensorRT', '特征存储', '监控']],
  ['PM-C', 'C端产品经理', '产品经理', ['用户调研', '需求分析', 'PRD', '竞品分析', '用户画像', '留存', '转化', '增长运营', 'A/B测试']],
  ['PM-B', 'B端企业产品经理', '产品经理', ['B端产品', 'SaaS', '企业服务', '工作流', 'B端权限模型', 'API设计', '私有化', 'SLA']],
  ['PM-AI', 'AI产品经理', '产品经理', ['LLM', 'RAG', 'Agent', 'Prompt', 'AI产品', '对话设计', '评估体系', '安全对齐']],
  ['DA-BIZ', '业务数据分析师', '数据分析', ['SQL', 'MySQL', 'PostgreSQL', 'Excel', 'Tableau', '指标体系', '漏斗分析', '归因分析']],
  ['DA-PROD', '产品数据分析师', '数据分析', ['用户行为', 'A/B实验设计', '埋点', '留存分析', '漏斗分析', 'SQL', 'Python']],
  ['DA-BI', 'BI与数据可视化工程师', '数据分析', ['PowerBI', 'Tableau', 'Looker', 'SQL', 'dbt', '数仓建模', 'ETL', 'OLAP']],
  ['QA-FUNC', '功能测试工程师', '软件测试', ['黑盒测试', '测试用例', 'Bug管理', 'Jira', 'Charles', 'Postman', '回归测试']],
  ['QA-AUTO', '自动化测试工程师', '软件测试', ['Selenium', 'Playwright', 'pytest', '接口测试', '自动化框架', 'PO模式', '数据驱动']],
  ['QA-PERF', '性能测试工程师', '软件测试', ['JMeter', '压测', '响应时间', '瓶颈分析', '调优', '容量规划', '质量度量']],
  ['QA-SDET', '测试开发质量工程师', '软件测试', ['测试框架', 'Mock', '契约测试', '代码覆盖率', 'SonarQube', '安全测试', 'Pipeline']],
]

export const MOCK_JOBS = JOB_ROWS.map(([code, name, family, keywords], i) => ({
  id: i + 1,
  code,
  name,
  family,
  category: family,
  description: `面向 ${name} 岗位的综合能力评估与模拟面试训练。`,
  // 同 keywords，前端 parseJsonField 先试 JSON.parse，给纯逗号串会退回成「一个长标签」
  abilities: JSON.stringify(keywords.slice(0, 4)),
  // 后端这个字段存的是 JSON 数组字符串，learningResources.js 的 parseList 依赖它
  keywords: JSON.stringify(keywords),
  status: 1,
  createTime: daysAgo(120),
  updateTime: daysAgo(120),
}))

// ---------------------------------------------------------------------------
// 账号 / 简历
// ---------------------------------------------------------------------------
export const MOCK_USER = {
  id: 3,
  username: 'student',
  password: null,
  nickname: '演示同学',
  role: 'STUDENT',
  email: 'student@example.com',
  phone: '13800000000',
  avatar: null,
  status: 1,
  createTime: daysAgo(90),
  updateTime: daysAgo(3),
}

export const MOCK_RESUME = {
  id: 1,
  userId: 3,
  rawText: '软件工程专业本科，主修 Java 后端方向。熟悉 Spring Boot 与 MySQL，参与过校内实训项目。',
  projects: JSON.stringify([
    { name: '校园二手交易平台', role: '后端开发', desc: '基于 Spring Boot + MyBatis 实现商品与订单模块' },
    { name: '在线判题系统', role: '全栈', desc: 'Docker 沙箱执行用户代码并返回评测结果' },
  ]),
  skills: JSON.stringify(['Java', 'Spring Boot', 'MyBatis', 'MySQL', 'Redis', 'Git', 'Linux', 'Docker']),
  keywords: JSON.stringify(['后端开发', '微服务', '数据库优化']),
  createTime: daysAgo(30),
  updateTime: daysAgo(3),
}

// ---------------------------------------------------------------------------
// 首页
// ---------------------------------------------------------------------------
export const MOCK_OVERVIEW = {
  summary: {
    completedCount: 7,
    recentCount: 3,
    averageScore: 74.6,
    bestScore: 82.0,
    bestJobName: 'Java后端开发工程师',
    streakDays: 4,
  },
  nextAction: {
    type: 'TARGETED_PRACTICE',
    title: '针对薄弱项补一轮训练',
    description: '上次报告里「问题分析能力」得分偏低，建议做一轮专项训练。',
    route: '/learning',
  },
  trend: Array.from({ length: 30 }, (_, i) => ({
    date: daysFromNow(i - 29),
    count: i % 5 === 0 ? 1 : 0,
    averageScore: i % 5 === 0 ? 68 + (i % 12) : 0,
  })),
  recentInterviews: [
    {
      sessionId: 9001, reportId: 5001, jobId: 1, jobName: 'Java后端开发工程师',
      status: 'FINISHED', difficulty: 2, score: 82.0, durationSeconds: 1680,
      startTime: daysAgo(2, 14), iconKey: 'java', specialtyKey: 'backend', themeKey: 'default',
    },
    {
      sessionId: 9002, reportId: 5002, jobId: 15, jobName: 'AI应用全栈开发工程师',
      status: 'FINISHED', difficulty: 2, score: 71.0, durationSeconds: 1500,
      startTime: daysAgo(6, 20), iconKey: 'ai', specialtyKey: 'fullstack', themeKey: 'default',
    },
    {
      sessionId: 9003, reportId: null, jobId: 6, jobName: 'Web前端开发工程师',
      status: 'ABORTED', difficulty: 1, score: null, durationSeconds: 420,
      startTime: daysAgo(9, 16), iconKey: 'web', specialtyKey: 'frontend', themeKey: 'default',
    },
  ],
  latestInsight: {
    reportId: 5001,
    jobName: 'Java后端开发工程师',
    totalScore: 82.0,
    strongestDimension: '技术基础能力',
    strongestScore: 86.0,
    weakestDimension: '问题分析能力',
    weakestScore: 64.0,
    suggestion: '回答技术问题时先给结论、再补推理过程，最后落到一个具体项目场景，能让面试官更快抓住你的思路。',
    dimensions: [
      { dimension: '技术基础能力', score: 86.0, level: '优秀', explanation: '基础概念掌握扎实' },
      { dimension: '技术深度能力', score: 79.0, level: '良好', explanation: '能说到原理，但边界场景覆盖不足' },
      { dimension: '工程实践能力', score: 81.0, level: '良好', explanation: '有真实项目经验支撑' },
      { dimension: '问题分析能力', score: 64.0, level: '待提升', explanation: '分析过程跳跃，缺少拆解步骤' },
      { dimension: '项目表达能力', score: 76.0, level: '良好', explanation: '能说清做了什么，亮点不够突出' },
    ],
  },
}

// ---------------------------------------------------------------------------
// 面试记录
// ---------------------------------------------------------------------------
export const MOCK_RECORDS = [
  {
    sessionId: 9001, jobId: 1, jobName: 'Java后端开发工程师', difficulty: 2,
    status: 'FINISHED', totalScore: 82.0, reportId: 5001,
    startTime: daysAgo(2, 14), endTime: daysAgo(2, 14),
    durationSeconds: 1800, actualDurationSeconds: 1680,
  },
  {
    sessionId: 9002, jobId: 15, jobName: 'AI应用全栈开发工程师', difficulty: 2,
    status: 'FINISHED', totalScore: 71.0, reportId: 5002,
    startTime: daysAgo(6, 20), endTime: daysAgo(6, 20),
    durationSeconds: 1800, actualDurationSeconds: 1500,
  },
  {
    sessionId: 9003, jobId: 6, jobName: 'Web前端开发工程师', difficulty: 1,
    status: 'ABORTED', totalScore: null, reportId: null,
    startTime: daysAgo(9, 16), endTime: daysAgo(9, 16),
    durationSeconds: 1800, actualDurationSeconds: 420,
  },
  {
    sessionId: 9004, jobId: 1, jobName: 'Java后端开发工程师', difficulty: 3,
    status: 'FINISHED', totalScore: 68.0, reportId: 5004,
    startTime: daysAgo(15, 11), endTime: daysAgo(15, 11),
    durationSeconds: 2700, actualDurationSeconds: 2410,
  },
]

// ---------------------------------------------------------------------------
// 评分模块（取自 score_module 表）
// ---------------------------------------------------------------------------
export const MOCK_MODULES = [
  { id: 1, code: 'technical_base', name: '技术基础能力', description: '基础概念与原理的掌握程度', sortOrder: 1, createTime: daysAgo(120) },
  { id: 2, code: 'technical_depth', name: '技术深度能力', description: '能否深入到实现原理与边界场景', sortOrder: 2, createTime: daysAgo(120) },
  { id: 3, code: 'engineering_practice', name: '工程实践能力', description: '真实项目中的工程取舍与落地能力', sortOrder: 3, createTime: daysAgo(120) },
  { id: 4, code: 'problem_analysis', name: '问题分析能力', description: '面对问题的拆解与推理过程', sortOrder: 4, createTime: daysAgo(120) },
  { id: 5, code: 'project_expression', name: '项目表达能力', description: '把项目讲清楚、讲出亮点的能力', sortOrder: 5, createTime: daysAgo(120) },
]

// ---------------------------------------------------------------------------
// 面试题（取自 skill_question 表，difficulty 与 followup_guide 都是库里的原值）
//
// 只放题目内容本身。QuestionView 的组装（roundNo / type / questionType）在
// demoAdapter 里做，这里不掺展示层字段。
// ---------------------------------------------------------------------------
export const MOCK_QUESTIONS = [
  {
    id: 1, abilityTag: 'Java', difficulty: 1,
    content: '请谈谈Java中==和equals()的区别，以及为什么重写equals时必须重写hashCode？',
    followup: '能举一个不重写hashCode导致HashMap行为异常的具体例子吗？',
  },
  {
    id: 3, abilityTag: 'Java', difficulty: 2,
    content: 'Java中String为什么设计为不可变？从常量池、安全性和线程安全角度说明。',
    followup: 'StringBuilder和StringBuffer的区别是什么？底层怎么扩容的？',
  },
  {
    id: 9, abilityTag: 'Spring Boot', difficulty: 2,
    content: 'Spring IoC容器和依赖注入(DI)的原理是什么？构造器注入和字段注入的优劣？',
    followup: '构造器注入vs字段注入，Spring官方推荐哪种？为什么？',
  },
  {
    id: 10, abilityTag: 'Spring Boot', difficulty: 2,
    content: '@Transactional声明式事务的原理、失效场景及解决方法？',
    followup: 'REQUIRED和REQUIRES_NEW分别适合什么场景？REQUIRES_NEW有什么风险？',
  },
  {
    id: 15, abilityTag: 'MySQL', difficulty: 2,
    content: 'MySQL InnoDB中B+Tree索引原理是什么？为什么不用B-Tree或二叉树？',
    followup: '什么是覆盖索引？如何利用覆盖索引避免回表？请举例说明。',
  },
  {
    id: 16, abilityTag: 'MySQL', difficulty: 3,
    content: 'MySQL四种事务隔离级别是什么？InnoDB默认用什么？各解决了什么/未解决什么？',
    followup: 'Oracle和PostgreSQL默认用RC，而MySQL默认用RR，设计哲学有什么不同？',
  },
]

// ---------------------------------------------------------------------------
// 报告
// ---------------------------------------------------------------------------
const REPORT_MODULES = [
  {
    moduleCode: 'technical_base', moduleName: '技术基础能力',
    rawScore: 86.0, targetScore: 80.0, moduleMatch: 92.0, baseWeight: 0.25,
    gapScore: 6.0, improvementPriority: 5, aiConfidence: 0.88, scoreSource: 'AI',
    evidence: '对 == 与 equals、String 不可变性等基础概念回答准确，能主动提及常量池与线程安全。',
    suggestion: '保持当前基础题的正确率，可以开始向 JVM 调优等更深的主题延伸。',
  },
  {
    moduleCode: 'technical_depth', moduleName: '技术深度能力',
    rawScore: 79.0, targetScore: 80.0, moduleMatch: 84.0, baseWeight: 0.25,
    gapScore: 1.0, improvementPriority: 3, aiConfidence: 0.82, scoreSource: 'AI',
    evidence: '能说到 B+Tree 的结构优势，但对覆盖索引、回表代价的解释停在概念层面。',
    suggestion: '每个原理都往下再追一层：「为什么这么设计」「不这样会怎样」。',
  },
  {
    moduleCode: 'engineering_practice', moduleName: '工程实践能力',
    rawScore: 81.0, targetScore: 78.0, moduleMatch: 88.0, baseWeight: 0.2,
    gapScore: 3.0, improvementPriority: 6, aiConfidence: 0.8, scoreSource: 'AI',
    evidence: '提到项目里用 Docker 做沙箱、用 Redis 做缓存，属于真实落地经验。',
    suggestion: '补充量化结果（QPS 提升多少、耗时降了多少），说服力会强很多。',
  },
  {
    moduleCode: 'problem_analysis', moduleName: '问题分析能力',
    rawScore: 64.0, targetScore: 80.0, moduleMatch: 68.0, baseWeight: 0.2,
    gapScore: 16.0, improvementPriority: 1, aiConfidence: 0.85, scoreSource: 'AI',
    evidence: '回答缺少拆解步骤，直接从结论跳到结论，中间推理链条不完整。',
    suggestion: '养成「先分层、再定位、后验证」的答题习惯，把分析过程显式说出来。',
  },
  {
    moduleCode: 'project_expression', moduleName: '项目表达能力',
    rawScore: 76.0, targetScore: 80.0, moduleMatch: 84.0, baseWeight: 0.1,
    gapScore: 4.0, improvementPriority: 2, aiConfidence: 0.78, scoreSource: 'AI',
    evidence: '能说清项目做了什么，但缺少技术难点和取舍的展开。',
    suggestion: '每个项目准备一个「最难的点 + 当时怎么权衡 + 最后结果」的小故事。',
  },
]

export const MOCK_REPORT = {
  reportId: 5001,
  sessionId: 9001,
  jobId: 1,
  jobName: 'Java后端开发工程师',
  totalScore: 78.5,
  overallMatchScore: 82.0,
  displayLevel: 'B+',
  profileLabel: '基础扎实，分析过程待加强',
  summary: '整体表现中上，技术基础与工程实践是明显优势；问题分析能力是当前最短板，也是提分空间最大的一项。',
  startTime: daysAgo(2, 14),
  endTime: daysAgo(2, 14),
  durationSeconds: 1800,
  actualDurationSeconds: 1680,
  strengths: ['基础概念回答准确', '有真实项目经验支撑', '对数据库索引有一定理解'],
  weaknesses: ['分析过程跳跃，缺少拆解步骤', '技术深度停在概念层', '项目亮点表达不突出'],
  suggestions: [
    '回答技术问题时按「结论 → 原理 → 例子 → 边界」四段展开。',
    '每个项目准备一个技术难点故事，带上量化结果。',
    '针对问题分析能力做 2 到 3 轮专项训练。',
  ],
  weakTags: ['问题分析能力', '技术深度能力'],
  dimensions: REPORT_MODULES.map((m) => ({
    dimension: m.moduleName,
    score: m.rawScore,
    level: m.rawScore >= 85 ? '优秀' : m.rawScore >= 75 ? '良好' : '待提升',
    explanation: m.evidence,
  })),
  moduleScores: REPORT_MODULES,
}

/** 改进路径：Map<String, SuggestionItem>，key 是 moduleCode */
export const MOCK_IMPROVEMENT_PATH = {
  problem_analysis: {
    diagnosis: '回答时习惯直接给结论，缺少把问题拆开、逐层排除的过程展示。',
    actionPlan: '每天挑 1 道系统设计类问题，先用文字写下拆解步骤，再录一段 3 分钟口述。',
    tone: 'warning',
  },
  technical_depth: {
    diagnosis: '对原理的掌握停在"是什么"，缺少"为什么这么设计"和"换一种会怎样"。',
    actionPlan: '每个高频原理追问三层：设计动机、替代方案、极端场景下的失效表现。',
    tone: 'info',
  },
  project_expression: {
    diagnosis: '项目描述偏功能罗列，技术难点和个人取舍没有展开。',
    actionPlan: '用 STAR 结构重写两个主力项目，每个都带上量化结果。',
    tone: 'info',
  },
}

// ---------------------------------------------------------------------------
// 语音识别（离线模式下不可能真的转写，回一段合理的话）
// ---------------------------------------------------------------------------
export const MOCK_TRANSCRIPT = {
  text: '这个问题我的理解是，它主要考察对底层原理的掌握程度，实际项目中我也遇到过类似场景。',
  requestId: 'demo-offline',
  model: 'offline-demo',
  duration: 5.2,
}

/** AI 状态：演示模式下说自己是 AI，好让顶部徽标不显示"演示模式（规则）" */
export const MOCK_AI_STATUS = { mode: 'AI' }

/* ---------------------------------------------------------------------------
 * AI 面试教练的话术（/ai/prep/stream 用）
 *
 * 四个阶段各一段。act 是「意图」——前端拿到后要么直接推进流程（HIGH），
 * 要么在输入框上方挂一条确认栏（其余一律要用户点一下才动）。
 * ------------------------------------------------------------------------- */
export const MOCK_AI_PREP = {
  GREET: {
    text: '你好，我是你的面试教练。先挑一个目标岗位，我按那个岗位的题库给你出题；选完再传一份简历，题目会更贴近你的真实经历。',
    act: { intent: 'READY', confidence: 'LOW' },
  },
  PICK_JOB: {
    text: '你想投哪个方向？后端、前端、算法、产品、数据分析、测试都可以，直接说岗位名或者方向都行。定下来之后我先给你过一遍这个岗位的高频考点。',
  },
  UPLOAD: {
    text: '简历拖到下面的框里就行，我只用它提取技能标签、调整出题侧重，不会外传。手上没有现成文件的话，也可以直接在线填一份。',
    act: { intent: 'NO_RESUME', confidence: 'LOW' },
  },
  DONE: {
    text: '简历收到。接下来设置 5 个训练目标——也就是这轮面试重点考察的能力项，按你的目标排个序，就可以开始了。',
  },
}

/** 猜中岗位后补的一句岗位相关的话，让回复看起来真的读懂了用户那句 */
export const MOCK_AI_PREP_FOCUS = {
  'BE-JAVA': '这个岗位重点看 Java 基础、JVM 和并发，Spring 那一套基本是必问，数据库索引和事务也躲不掉。',
  'BE-PY': '这个岗位重点看 Python 语言特性、异步和 Web 框架，数据库与缓存的实际使用经验问得比较多。',
  'FE-WEB': '这个岗位重点看 JavaScript 基础、框架原理和浏览器渲染，性能优化和工程化配置也常被追着问。',
  'FE-ANDROID': '这个岗位重点看 Kotlin、Jetpack 组件和生命周期，内存泄漏与性能问题基本每轮都会碰到。',
  'FS-JAVA': '这个岗位两头都会问，后端看 Spring 与数据库，前端看框架使用和接口联调，还要能讲清整体架构。',
  'FS-PY': '这个岗位两头都会问，后端看 Django/FastAPI 与数据库，前端看框架和接口设计，工程化经验是加分项。',
  'ALG-ML': '这个岗位重点看特征工程与模型选型，项目里怎么定义指标、怎么做离线评估，几乎一定会被追问。',
  'ALG-NLP': '这个岗位重点看 Transformer 与注意力机制，大模型的微调与评测也是现在的必问题。',
  'PM-C': '这个岗位重点看需求判断和用户理解，讲清楚一个你主导过的功能、以及当时怎么权衡的，比背方法论有用。',
  'PM-B': '这个岗位重点看业务流程抽象与权限设计，企业客户的交付和私有化经验会被重点关注。',
  'DA-BIZ': '这个岗位重点看 SQL 和指标体系，业务归因的分析思路比工具熟练度更关键。',
  'DA-PROD': '这个岗位重点看埋点设计与 A/B 实验，能不能从数据里说出一个可执行的产品结论是核心。',
  'QA-AUTO': '这个岗位重点看自动化框架设计和用例分层，PO 模式和稳定性治理是常考点。',
  'QA-PERF': '这个岗位重点看压测方案与瓶颈定位，能说清一次真实的调优过程最有说服力。',
}
