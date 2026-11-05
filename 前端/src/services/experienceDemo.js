/**
 * 手机端「面试经验」离线数据层。
 *
 * 比赛演示以离线为主，所以经验模块先做成 localStorage 本地优先：
 * 列表、详情、点赞、收藏、发布、评论都不出网。后续接后端时，
 * 只需要把这几个导出函数换成 request 调用，页面不用动。
 */
const STORAGE_KEY = 'offerpilot:experience-demo:v1'
const MAX_TITLE = 60
const MAX_CONTENT = 6000

const SEED_POSTS = [
  {
    id: 'exp-java-concurrency',
    title: 'Java 后端二面：别急着背八股，先把项目里的并发讲圆',
    excerpt: '从「你项目里哪里用了线程池」开始，面试官连续追问了 6 层。把这次踩过的坑和回答顺序整理出来了。',
    author: '阿泽',
    authorTitle: 'Java 后端 · 三年经验',
    jobCode: 'BE-JAVA',
    jobName: 'Java后端开发',
    company: '字节跳动',
    tags: ['Java', '并发', '项目深挖'],
    likeCount: 186,
    commentCount: 3,
    publishedAt: '2026-10-29',
    featured: true,
    sections: [
      { heading: '一面：基础题只占十分钟', paragraphs: ['一面前半段问了 HashMap、JVM 内存分区和 MySQL 索引，都是常规题。真正拉开差距的是后面：面试官让我挑一个项目，说清楚「哪里会出问题、你怎么发现、最后怎么验证」。', '我讲了订单导出功能里的线程池。重点不是背参数，而是说清楚为什么核心线程数是 4、队列为什么有界、拒绝策略为什么选 CallerRuns。面试官接着问：如果下游突然变慢，你的系统会先从哪里报警？'] },
      { heading: '二面：连续追问的 6 层', paragraphs: ['二面几乎全在深挖：线程池满了之后请求去哪里、调用方超时会不会导致重复提交、幂等键放在哪一层、Redis 挂了怎么办、数据库唯一索引能不能兜底、最后怎么复盘。', '我的经验是：每回答一层，主动补一句「这一层的代价是什么」。比如用 Redis 做幂等会引入依赖，用唯一索引兜底会增加写失败率。面试官要听的是取舍，不是标准答案。'] },
      { heading: '复盘', paragraphs: ['项目深挖题不要等对方问。先给出背景、目标、约束，再讲方案和验证。把「我做了什么」改成「我为什么这么做、怎么证明它有效」，表达会立刻不一样。'] },
    ],
    tips: ['项目里至少准备一个能讲 10 分钟的复杂点', '每个技术选型都准备一句代价说明', '被问住了先说思路，再承认边界，不要硬编'],
  },
  {
    id: 'exp-fe-performance',
    title: '前端一面被问懵的性能题：从白屏到首屏，我这样拆',
    excerpt: '不是背 Lighthouse 指标，而是把「用户觉得慢」拆成可观测的链路。附上我现场画的分析顺序。',
    author: '小满',
    authorTitle: 'Web 前端 · 校招',
    jobCode: 'FE-WEB',
    jobName: 'Web前端开发',
    company: '美团',
    tags: ['性能优化', '浏览器', '校招'],
    likeCount: 142,
    commentCount: 2,
    publishedAt: '2026-10-25',
    featured: false,
    sections: [
      { heading: '先定位，再优化', paragraphs: ['面试官问「首屏 3 秒怎么优化」，直接答懒加载和 CDN 很容易被打断。我先反问：是哪个端、什么网络、首屏的定义是什么。然后把链路拆成 DNS、连接、TTFB、资源下载、JS 执行、首屏渲染。', '接着才说：如果 TTFB 高，先查服务端和缓存；如果 JS 执行时间长，再做代码分割和依赖治理。不同瓶颈对应的手段完全不一样。'] },
      { heading: '我现场画的顺序', paragraphs: ['1. 看指标：LCP、INP、CLS 分别对应什么体验；2. 看瀑布图：哪个资源阻塞了首屏；3. 看主线程：长任务在哪里；4. 做实验：一次只改一个变量；5. 回收数据：确认没有把别的指标弄坏。', '面试官后来追问「代码分割会不会让请求变多」，我说会，所以按路由和首屏优先级拆，不要为了分数把所有包都拆碎。'] },
      { heading: '复盘', paragraphs: ['性能题的高分回答不是列手段，而是展示定位过程。能说清楚「怎么证明有效」，比背十个优化点更有说服力。'] },
    ],
    tips: ['先问清首屏定义和网络条件', '按链路定位瓶颈，不要直接背手段', '优化后一定补一句验证指标'],
  },
  {
    id: 'exp-alg-llm',
    title: '大模型算法面：RAG 项目被追问到评测集怎么造',
    excerpt: '从召回、重排到幻觉评测，面试官最在意的不是模型多大，而是你怎么知道它变好了。',
    author: 'Kai',
    authorTitle: 'NLP 算法 · 硕士',
    jobCode: 'ALG-NLP',
    jobName: 'NLP与大模型算法',
    company: '阿里云',
    tags: ['RAG', 'LLM', '评测'],
    likeCount: 231,
    commentCount: 4,
    publishedAt: '2026-10-31',
    featured: true,
    sections: [
      { heading: '项目介绍只给了三分钟', paragraphs: ['我按「业务问题 → 基线 → 失败案例 → 改造 → 指标」讲，没有先报模型参数。面试官立刻追问：你的评测集从哪里来，多少条，谁标注的，覆盖哪些失败类型。', '这一步很关键。很多人能讲 RAG 架构，但说不清评测集，项目就像没有闭环。'] },
      { heading: '被追问的四个点', paragraphs: ['召回率提升但答案没变好，为什么？——因为重排和上下文拼接才是瓶颈。幻觉怎么衡量？——分事实错误、引用缺失、答非所问三类，人工抽检加规则校验。线上怎么监控？——记录无引用回答、用户追问率和拒答率。为什么不用更大模型？——延迟和成本约束下，先解决检索质量。'] },
      { heading: '复盘', paragraphs: ['算法岗的项目深挖，最后都会落到「你怎么验证」。提前准备一张评测表：指标、数据来源、基线、提升幅度、失败案例。被问到时直接讲表，可信度会高很多。'] },
    ],
    tips: ['项目介绍先讲问题和约束，再讲模型', '评测集要能说清来源、规模和标注方式', '准备至少两个失败案例和对应改法'],
  },
  {
    id: 'exp-pm-ai',
    title: 'AI 产品经理面试：如何证明一个功能值得做',
    excerpt: '没有真实数据也能讲清楚价值。我的方法是把「我觉得」换成可验证的假设和最小实验。',
    author: '林一',
    authorTitle: 'AI 产品 · 转岗',
    jobCode: 'PM-AI',
    jobName: 'AI产品经理',
    company: '百度',
    tags: ['AI产品', '需求分析', '转岗'],
    likeCount: 97,
    commentCount: 1,
    publishedAt: '2026-10-22',
    featured: false,
    sections: [
      { heading: '别从功能清单开始', paragraphs: ['面试官给了一个「帮销售写跟进邮件」的题目。我没有直接列功能，而是先问用户是谁、现在怎么做、最耗时的是哪一步、失败一次的成本是多少。', '拆完发现真正的问题不是写不快，而是不知道客户处于哪个阶段。于是方案重点变成「从 CRM 提取阶段 + 生成对应话术」，而不是做一个通用写作框。'] },
      { heading: '怎么讲验证', paragraphs: ['我给了三层验证：先离线看 50 条历史记录，人工评估可用率；再找 5 个销售做任务测试，看节省时间；最后小流量上线，看采纳率和二次编辑率。每一层都有明确的通过标准。', '面试官追问「如果采纳率低怎么办」，我说先看是生成质量还是信任问题，前者改 Prompt 和检索，后者要加引用和可编辑入口。'] },
      { heading: '复盘', paragraphs: ['AI 产品题最忌讳只讲模型能力。把用户任务、失败成本、验证标准讲清楚，比说「接入大模型」更有产品感。'] },
    ],
    tips: ['先拆用户任务，不要先列功能', '每个方案配一个最小验证实验', '准备一个「效果不好」的应对方案'],
  },
  {
    id: 'exp-qa-automation',
    title: '测试开发面经：自动化不是把手工用例翻译一遍',
    excerpt: '面试官问我自动化覆盖率为什么上不去，我从用例分层、稳定性、维护成本三个角度回答。',
    author: '老周',
    authorTitle: '测试开发 · 五年经验',
    jobCode: 'QA-SDET',
    jobName: '测试开发质量工程师',
    company: '京东',
    tags: ['自动化测试', '质量度量', '社招'],
    likeCount: 76,
    commentCount: 0,
    publishedAt: '2026-10-18',
    featured: false,
    sections: [
      { heading: '覆盖率不是越高越好', paragraphs: ['我先区分了接口、UI、单元三层。接口层稳定、反馈快，应该覆盖核心链路；UI 层只保留少量关键路径；单元测试交给开发。把所有手工用例都自动化，维护成本会把收益吃掉。', '面试官追问怎么衡量价值，我说看三个数：拦截缺陷数、平均反馈时间、脚本维护工时。覆盖率只是过程指标。'] },
      { heading: '稳定性怎么治', paragraphs: ['先分类失败：环境、数据、脚本、真实缺陷。环境问题做隔离和健康检查，数据问题做独立造数和清理，脚本问题加显式等待和幂等。最后给每条失败打标签，持续看噪音率。'] },
      { heading: '复盘', paragraphs: ['测试岗的面试很像排查问题。不要只说自己会什么工具，要展示怎么定位、怎么取舍、怎么证明质量在变好。'] },
    ],
    tips: ['按层讲自动化，不要一刀切', '准备稳定性治理的具体例子', '用缺陷拦截和维护成本说明价值'],
  },
  {
    id: 'exp-data-analysis',
    title: '数据分析面试：业务题没有标准答案，但有回答顺序',
    excerpt: '指标掉了 20%，先拆维度还是先查埋点？我总结了一套不会慌的答题框架。',
    author: 'Mia',
    authorTitle: '业务数据分析 · 校招',
    jobCode: 'DA-BIZ',
    jobName: '业务数据分析师',
    company: '腾讯',
    tags: ['业务分析', '指标体系', '校招'],
    likeCount: 118,
    commentCount: 2,
    publishedAt: '2026-10-15',
    featured: false,
    sections: [
      { heading: '先确认问题是否真实', paragraphs: ['看到「指标掉了 20%」，我先问口径有没有变、埋点有没有发版、统计周期是否一致。确认数据可信后，再拆新老用户、端、渠道、地区、版本。', '面试官会故意给一个假下跌，如果直接开始拆维度，就会掉进陷阱。先验证数据，是数据分析师的基本功。'] },
      { heading: '拆完要能落到动作', paragraphs: ['定位到某个渠道新用户转化下降后，我继续拆注册、激活、首单三步，发现是激活页加载失败。最后给出短期修复和长期监控方案。', '只告诉面试官「哪个维度掉了」不够，还要说「接下来谁做什么、怎么验证」。'] },
      { heading: '复盘', paragraphs: ['业务题考的是结构化，不是猜到标准答案。把「确认口径 → 拆维度 → 找原因 → 给动作 → 设监控」说顺，基本不会减分。'] },
    ],
    tips: ['先验数据，再拆维度', '每个结论都落到一个可执行动作', '最后补一句怎么监控复发'],
  },
]

const SEED_COMMENTS = {
  'exp-java-concurrency': [
    { id: 'seed-c-java-1', author: '石头', content: '「每层补一句代价」这个说法很有用，我之前只讲方案，面试官追问就露怯。', createdAt: '2026-10-29T20:10:00+08:00' },
    { id: 'seed-c-java-2', author: 'Nina', content: '线程池那道追问我也遇到过，幂等键放大 Repository 层确实更容易讲清楚。', createdAt: '2026-10-30T09:22:00+08:00' },
  ],
  'exp-fe-performance': [
    { id: 'seed-c-fe-1', author: '阿树', content: '先问首屏定义这一步很关键，不然答得再多也不在点上。', createdAt: '2026-10-26T10:05:00+08:00' },
  ],
  'exp-alg-llm': [
    { id: 'seed-c-alg-1', author: 'Luna', content: '评测集那张表能展开讲讲吗？尤其是人工抽检的样本量怎么定。', createdAt: '2026-11-01T08:40:00+08:00' },
    { id: 'seed-c-alg-2', author: 'Kai', content: '我们按失败类型分层抽，每类至少 30 条，先保证能看出差异，再谈统计显著。', createdAt: '2026-11-01T12:15:00+08:00' },
  ],
  'exp-pm-ai': [
    { id: 'seed-c-pm-1', author: '小北', content: '把「我觉得」换成假设和实验，这个转岗面试特别适用。', createdAt: '2026-10-23T18:30:00+08:00' },
  ],
  'exp-qa-automation': [
    { id: 'seed-c-qa-1', author: '木木', content: '失败分类打标签这一招我们团队也在用，噪音率降下来之后大家才愿意看报告。', createdAt: '2026-10-19T14:20:00+08:00' },
  ],
  'exp-data-analysis': [
    { id: 'seed-c-da-1', author: 'Yuki', content: '先验口径真的能救命，我就因为埋点发版背过一次锅。', createdAt: '2026-10-16T11:00:00+08:00' },
  ],
}

const TOPICS = [
  { key: 'all', label: '全部' },
  { key: 'BE', label: '后端' },
  { key: 'FE', label: '前端' },
  { key: 'ALG', label: '算法' },
  { key: 'PM', label: '产品' },
  { key: 'DA', label: '数据' },
  { key: 'QA', label: '测试' },
]

const clone = value => JSON.parse(JSON.stringify(value))

function safeStorage() {
  try {
    return typeof localStorage !== 'undefined' ? localStorage : null
  } catch {
    return null
  }
}

function load() {
  const storage = safeStorage()
  let saved = null
  try { saved = JSON.parse(storage?.getItem(STORAGE_KEY) || 'null') } catch { saved = null }
  if (!saved || !Array.isArray(saved.posts)) {
    return { posts: clone(SEED_POSTS), comments: clone(SEED_COMMENTS), version: 1 }
  }
  // 种子更新时只补缺失的帖子，不覆盖用户已经点过的赞/收藏。
  const byId = new Map(saved.posts.map(post => [post.id, post]))
  for (const seed of SEED_POSTS) {
    if (!byId.has(seed.id)) saved.posts.push(clone(seed))
  }
  if (!saved.comments || typeof saved.comments !== 'object') saved.comments = {}
  for (const [postId, list] of Object.entries(SEED_COMMENTS)) {
    if (!saved.comments[postId]?.length) saved.comments[postId] = clone(list)
  }
  for (const post of saved.posts) post.commentCount = (saved.comments[post.id] || []).length
  return saved
}

function save(data) {
  safeStorage()?.setItem(STORAGE_KEY, JSON.stringify(data))
}

function publicPost(post) {
  const bodyChars = [
    post.excerpt,
    ...(post.tips || []),
    ...post.sections.flatMap(section => [section.heading, ...section.paragraphs]),
  ].filter(Boolean).join('').length
  return {
    ...clone(post),
    liked: !!post.liked,
    favorite: !!post.favorite,
    likeCount: Number(post.likeCount) || 0,
    commentCount: Number(post.commentCount) || 0,
    // 中文按 200 字/分钟估算；种子长文落在 3 分钟左右，用户短文仍从 1 分钟起。
    readMinutes: Math.max(1, Math.round(bodyChars / 200)),
  }
}

function matchesTopic(post, topic) {
  return !topic || topic === 'all' || String(post.jobCode || '').startsWith(`${topic}-`)
}

function matchesQuery(post, query) {
  const q = String(query || '').trim().toLowerCase()
  if (!q) return true
  return [post.title, post.excerpt, post.author, post.jobName, post.company, ...(post.tags || [])]
    .filter(Boolean).join(' ').toLowerCase().includes(q)
}

export const experienceTopics = TOPICS

export function listExperience({ topic = 'all', query = '', sort = 'latest' } = {}) {
  const data = load()
  const posts = data.posts
    .filter(post => matchesTopic(post, topic) && matchesQuery(post, query))
    .map(publicPost)
  if (sort === 'popular') posts.sort((a, b) => b.likeCount - a.likeCount)
  else posts.sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)))
  return posts
}

export function getExperiencePost(id) {
  const data = load()
  const post = data.posts.find(item => item.id === id)
  if (!post) throw new Error('这篇经验暂时找不到了。')
  return { ...publicPost(post), comments: clone(data.comments[id] || []) }
}

export function toggleExperienceLike(id) {
  const data = load()
  const post = data.posts.find(item => item.id === id)
  if (!post) throw new Error('这篇经验暂时找不到了。')
  post.liked = !post.liked
  post.likeCount = Math.max(0, (Number(post.likeCount) || 0) + (post.liked ? 1 : -1))
  save(data)
  return { liked: post.liked, likeCount: post.likeCount }
}

export function toggleExperienceFavorite(id) {
  const data = load()
  const post = data.posts.find(item => item.id === id)
  if (!post) throw new Error('这篇经验暂时找不到了。')
  post.favorite = !post.favorite
  save(data)
  return { favorite: post.favorite }
}

export function createExperiencePost(input = {}, author = {}) {
  const title = String(input.title || '').trim()
  const content = String(input.content || '').trim()
  if (title.length < 4) throw new Error('标题至少写 4 个字。')
  if (content.length < 30) throw new Error('正文至少写 30 个字，才能让别人看懂你的经验。')
  if (title.length > MAX_TITLE) throw new Error(`标题最多 ${MAX_TITLE} 个字。`)
  if (content.length > MAX_CONTENT) throw new Error(`正文最多 ${MAX_CONTENT} 个字。`)

  const jobCode = String(input.jobCode || 'BE-JAVA')
  const jobName = String(input.jobName || '求职经验')
  const paragraphs = content.split(/\n{2,}/).map(item => item.trim()).filter(Boolean)
  const data = load()
  const post = {
    id: `exp-user-${Date.now()}`,
    title,
    excerpt: paragraphs[0].slice(0, 80) + (paragraphs[0].length > 80 ? '…' : ''),
    author: author.nickname || author.username || '我',
    authorTitle: input.authorTitle || '求职训练用户',
    jobCode,
    jobName,
    company: String(input.company || '').trim(),
    tags: String(input.tags || '').split(/[,，\s]+/).map(tag => tag.trim()).filter(Boolean).slice(0, 4),
    likeCount: 0,
    commentCount: 0,
    publishedAt: new Date().toISOString().slice(0, 10),
    featured: false,
    mine: true,
    sections: paragraphs.map(paragraph => ({ heading: '', paragraphs: [paragraph] })),
    tips: [],
  }
  data.posts.unshift(post)
  save(data)
  return publicPost(post)
}

export function listMyExperiencePosts() {
  return load().posts.filter(post => post.mine || String(post.id).startsWith('exp-user-')).map(publicPost)
}

export function listFavoriteExperiencePosts() {
  return load().posts.filter(post => post.favorite).map(publicPost)
}

export function addExperienceComment(id, content, author = {}) {
  const text = String(content || '').trim()
  if (!text) throw new Error('先写点内容再发送。')
  if (text.length > 500) throw new Error('评论最多 500 个字。')
  const data = load()
  const post = data.posts.find(item => item.id === id)
  if (!post) throw new Error('这篇经验暂时找不到了。')
  const comment = {
    id: `comment-${Date.now()}`,
    author: author.nickname || author.username || '我',
    content: text,
    createdAt: new Date().toISOString(),
  }
  data.comments[id] = [...(data.comments[id] || []), comment]
  post.commentCount = data.comments[id].length
  save(data)
  return clone(comment)
}
