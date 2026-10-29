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
  nickname: '林晓',
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

/** 离线演示使用预置回复，与实时 AI 明确区分。 */
export const MOCK_AI_STATUS = { mode: 'RULE' }

/* ---------------------------------------------------------------------------
 * AI 面试教练的话术（/ai/prep/stream 用）
 *
 * 四个阶段各一段。用户明确选择后才提供待确认意图，初始化不触发操作。
 * ------------------------------------------------------------------------- */
export const MOCK_AI_PREP = {
  GREET: {
    text: '你好，我是小面，可以陪你了解岗位、梳理经历。还没方向也没关系，你最近学过什么，或者做过什么项目？',
  },
  PICK_JOB: {
    text: '岗位卡片可以随时浏览，不必马上做决定。也可以先聊你的兴趣和项目，或比较几个方向的工作内容。',
  },
  UPLOAD: {
    text: '简历拖到下面的框里就行，我只用它提取技能标签、调整出题侧重，不会外传。手上没有现成文件的话，也可以直接在线填一份。',
  },
  DONE: {
    text: '简历已就绪，可以查看简历分析、继续配置面试。若还想讨论岗位适配或准备重点，我们也可以接着聊。',
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

/* ---------------------------------------------------------------------------
 * 教学班 / 训练任务 / 站内消息 / 会议（教师端与企业端移动页）
 *
 * 字段名照后端 TeachingService / MeetingService 的 DTO 抄：成员行的 state 用
 * PENDING/JOINED/REJECTED/REMOVED，任务的 lifecycle 用 DRAFT/ACTIVE/ENDED/CLOSED。
 * 数量刻意是「看得到过程」的样子：有进行中任务、有等审核的申请、有等点评的
 * 报告、有开着和已结束的会议，演示时随手点哪里都有东西可讲。
 * ------------------------------------------------------------------------- */

/** 后端返回的是本地时间（无时区后缀），前端按 +08:00 解析——演示数据对齐这个约定 */
function localStamp(offsetDays, hour = 10, minute = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  d.setHours(hour, minute, 0, 0)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`
}

export const MOCK_TEACHING_CLASSES = [
  { id: 201, name: '软件工程 2024 级 · 1 班', semester: '2026–2027 第一学期', inviteCode: '7C4B29E8A1F3', archived: false, createdAt: localStamp(-21, 9, 0) },
  { id: 202, name: 'Java 后端方向 · 冲刺训练班', semester: '2026–2027 第一学期', inviteCode: 'B8E14A6C2D90', archived: false, createdAt: localStamp(-14, 15, 30) },
  { id: 203, name: '算法与 AI 兴趣小组（上学期）', semester: '2025–2026 第二学期', inviteCode: '5D3F7A9C1E42', archived: true, createdAt: localStamp(-120, 10, 0) },
]

export const MOCK_TEACHING_MEMBERS = [
  { id: 5201, classId: 201, studentId: 3, name: '林晓', username: 'student', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5202, classId: 201, studentId: 31, name: '林一鸣', username: 'linyiming', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5203, classId: 201, studentId: 32, name: '周雨桐', username: 'zhouyutong', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5204, classId: 201, studentId: 33, name: '陈子昂', username: 'chenziang', state: 'PENDING', accountStatus: 'ACTIVE' },
  { id: 5205, classId: 202, studentId: 34, name: '苏晚晴', username: 'suwanqing', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5206, classId: 202, studentId: 35, name: '何嘉树', username: 'hejiashu', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5207, classId: 203, studentId: 31, name: '林一鸣', username: 'linyiming', state: 'JOINED', accountStatus: 'ACTIVE' },
  { id: 5208, classId: 203, studentId: 34, name: '苏晚晴', username: 'suwanqing', state: 'JOINED', accountStatus: 'ACTIVE' },
]

/** 训练问题直接用真题库的题干，任务详情里能看到「真问题」而不是摆设 */
const DEMO_QUESTION_TEXT = MOCK_QUESTIONS.map((q) => q.content)

export const MOCK_TEACHING_TASKS = [
  {
    id: 7101,
    title: 'Java 集合与并发 · 第 1 次模拟面试',
    classId: 201,
    className: '软件工程 2024 级 · 1 班',
    jobId: 1,
    jobName: 'Java后端开发工程师',
    lifecycle: 'ACTIVE',
    publishedAt: localStamp(-2, 9, 0),
    deadline: localStamp(2, 22, 0),
    archived: false,
    endedAt: null,
    description: '围绕集合、并发与 JVM 的高频考点做一轮完整模拟，建议先自主完成一次、看过报告再约复盘。',
    minAttempts: 1,
    maxAttempts: 3,
    durationSeconds: 1800,
    allowLate: false,
    questions: DEMO_QUESTION_TEXT.slice(0, 3),
    scorePlan: {},
    assignments: [
      {
        id: 8101, studentId: 3, name: '林晓', username: 'student', exempt: false,
        completionStatus: 'COMPLETED', validCount: 1,
        attempts: [{ id: 9101, state: 'READY', submittedAt: localStamp(-1, 20, 12), reportId: 6101, failureReason: null }],
      },
      {
        id: 8102, studentId: 31, name: '林一鸣', username: 'linyiming', exempt: false,
        completionStatus: 'IN_PROGRESS', validCount: 0,
        attempts: [{ id: 9102, state: 'INVALID', submittedAt: localStamp(-1, 15, 40), reportId: 6102, failureReason: '回答时长不足，未达到有效标准' }],
      },
      {
        id: 8103, studentId: 32, name: '周雨桐', username: 'zhouyutong', exempt: false,
        completionStatus: 'PENDING_VALIDATION', validCount: 1,
        attempts: [{ id: 9103, state: 'READY', submittedAt: localStamp(0, 9, 24), reportId: 6103, failureReason: null }],
      },
    ],
  },
  {
    id: 7102,
    title: 'MySQL 索引与事务 · 专项训练',
    classId: 202,
    className: 'Java 后端方向 · 冲刺训练班',
    jobId: 1,
    jobName: 'Java后端开发工程师',
    lifecycle: 'ACTIVE',
    publishedAt: localStamp(-1, 17, 5),
    deadline: localStamp(6, 20, 0),
    archived: false,
    endedAt: null,
    description: '复盘索引失效的常见场景与隔离级别，练完再看报告里的「技术深度」维度。',
    minAttempts: 2,
    maxAttempts: 4,
    durationSeconds: 2400,
    allowLate: true,
    questions: DEMO_QUESTION_TEXT.slice(3, 6),
    scorePlan: {},
    assignments: [
      {
        id: 8104, studentId: 34, name: '苏晚晴', username: 'suwanqing', exempt: false,
        completionStatus: 'IN_PROGRESS', validCount: 1,
        attempts: [{ id: 9104, state: 'ONGOING', submittedAt: null, reportId: null, failureReason: null }],
      },
      {
        id: 8105, studentId: 35, name: '何嘉树', username: 'hejiashu', exempt: false,
        completionStatus: 'NOT_STARTED', validCount: 0,
        attempts: [],
      },
    ],
  },
  {
    id: 7103,
    title: 'Spring 框架原理 · 模拟面试（草稿）',
    classId: 201,
    className: '软件工程 2024 级 · 1 班',
    jobId: 1,
    jobName: 'Java后端开发工程师',
    lifecycle: 'DRAFT',
    publishedAt: null,
    deadline: localStamp(9, 22, 0),
    archived: false,
    endedAt: null,
    description: 'IoC、AOP 与事务传播，发布前请先确认题目顺序。',
    minAttempts: 1,
    maxAttempts: 2,
    durationSeconds: 1800,
    allowLate: false,
    questions: DEMO_QUESTION_TEXT.slice(6, 9),
    scorePlan: {},
    assignments: [],
  },
  {
    id: 7104,
    title: '计算机基础 · 摸底训练',
    classId: 201,
    className: '软件工程 2024 级 · 1 班',
    jobId: 1,
    jobName: 'Java后端开发工程师',
    lifecycle: 'CLOSED',
    publishedAt: localStamp(-21, 9, 0),
    deadline: localStamp(-14, 22, 0),
    archived: false,
    endedAt: localStamp(-13, 10, 0),
    description: '开学摸底，已结束并完成点评。',
    minAttempts: 1,
    maxAttempts: 2,
    durationSeconds: 1200,
    allowLate: false,
    questions: DEMO_QUESTION_TEXT.slice(9, 12),
    scorePlan: {},
    assignments: [
      {
        id: 8106, studentId: 3, name: '林晓', username: 'student', exempt: false,
        completionStatus: 'COMPLETED', validCount: 2,
        attempts: [
          { id: 9105, state: 'READY', submittedAt: localStamp(-16, 19, 0), reportId: 6104, failureReason: null },
          { id: 9106, state: 'READY', submittedAt: localStamp(-15, 20, 30), reportId: 6105, failureReason: null },
        ],
      },
      {
        id: 8107, studentId: 31, name: '林一鸣', username: 'linyiming', exempt: true,
        completionStatus: 'EXEMPT', validCount: 0, attempts: [],
      },
      {
        id: 8108, studentId: 32, name: '周雨桐', username: 'zhouyutong', exempt: false,
        completionStatus: 'COMPLETED', validCount: 1,
        attempts: [{ id: 9107, state: 'READY', submittedAt: localStamp(-15, 14, 10), reportId: 6106, failureReason: null }],
      },
    ],
  },
]

export const MOCK_TEACHING_MESSAGES = [
  {
    id: 3101, title: '周雨桐提交了训练报告',
    body: '「Java 集合与并发 · 第 1 次模拟面试」收到一份新报告，去报告点评看看她在并发题上的展开。',
    link: '/teacher/reports/6103', isRead: false, createdAt: localStamp(0, 9, 24),
  },
  {
    id: 3102, title: '新的班级加入申请',
    body: '陈子昂 申请加入「软件工程 2024 级 · 1 班」，去成员审核里处理。',
    link: '/teacher/classes/201/members', isRead: false, createdAt: localStamp(0, 8, 15),
  },
  {
    id: 3103, title: '任务已发布',
    body: '「MySQL 索引与事务 · 专项训练」已发布到「Java 后端方向 · 冲刺训练班」，2 名学生已收到通知。',
    link: '/teacher/tasks/7102', isRead: true, createdAt: localStamp(-1, 17, 5),
  },
]

export const MOCK_MEETINGS_HOSTED = [
  {
    // 会议号必须落在无歧义字母表内（没有 O/I/0/1）——查号页的格式校验拦在前端，
    // 「DEMO2026」这种带 O/0 的号根本递不到假后端，演示时会被当成 bug。
    id: 301, title: '星屿科技 · Java 后端岗一面', code: 'TECH2749', codeDisplay: 'TECH-2749',
    hostName: '陈经理', participantCount: 1, status: 'OPEN', createdAt: localStamp(0, 9, 30), joinTime: null,
  },
  {
    id: 302, title: '前端实习生 · 群面点评', code: 'TECH8888', codeDisplay: 'TECH-8888',
    hostName: '陈经理', participantCount: 2, status: 'ENDED', createdAt: localStamp(-3, 14, 0), joinTime: null,
  },
]

export const MOCK_MEETINGS_JOINED = [
  {
    id: 302, title: '前端实习生 · 群面点评', code: 'TECH8888', codeDisplay: 'TECH-8888',
    hostName: '陈经理', participantCount: 2, status: 'ENDED', createdAt: localStamp(-3, 14, 0), joinTime: localStamp(-3, 14, 5),
  },
]

export const MOCK_MEETING_ADVICE = {
  301: [
    {
      id: 9501, authorName: '王老师', authorId: 2, targetUserId: null,
      content: '自我介绍信息密度不错。项目部分建议把「我负责什么」和「团队做了什么」分开讲，面试官更容易判断你的贡献。',
      createdAt: localStamp(0, 9, 50),
    },
  ],
  302: [
    {
      id: 9502, authorName: '陈经理', authorId: 7, targetUserId: null,
      content: '整体表达清晰，但简历里的两个项目都集中在后台管理类，建议补一个高并发场景的案例，和岗位更贴。',
      createdAt: localStamp(-3, 15, 10),
    },
    {
      id: 9503, authorName: '陈经理', authorId: 7, targetUserId: null,
      content: '被追问「为什么这么设计」时的回答比较依赖模板，多用具体数字（QPS、耗时改善）会更有说服力。',
      createdAt: localStamp(-3, 15, 32),
    },
  ],
}

export const MOCK_CAREER = {
  stage: '校招',
  school: '云麓大学',
  major: '软件工程',
  graduationYear: '2027',
  targetJobId: 1,
  targetCompany: '星屿科技',
}
