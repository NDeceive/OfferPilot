# OfferPilot / 智面幻境 — AI 接手文档

> 生成时间：2026-10-27
> 适用对象：接手本工作区的 AI 编程助手
> 阅读顺序：**先读第 0 节「当前工作区状态」**，再读其余部分。第 0 节写的是「现在这一刻」的现场，其他章节是长期有效的项目说明。

---

## 0. 当前工作区状态（最重要，先读）

### 0.1 仓库位置与分支

- 项目根目录：`C:\Users\LENOVO\Desktop\self\competition\bigbed\offerpilot`
- Git 仓库就在 `offerpilot/` 下（父目录 `bigbed/` 不是仓库）
- 当前分支：`main`
- 当前 HEAD：`bffc928`（`Merge pull request #29 from NDeceive/codex/update-20261027`）
- 与远端关系：`ahead 0 / behind 0`，已是最新 `origin/main`

### 0.2 工作区里有未提交的用户改动（绝对不能丢）

`git status` 当前是 **33 项未提交改动**：

- 19 个已修改文件
- 14 个新增（untracked）文件

这些是用户正在做的功能，主要是：

- 手机端教师端 / 企业端：`前端/src/mobile/AdaptiveTeacherView.vue`、`AdaptiveEnterpriseView.vue`、`nav.js`、`teacherFormat.js`、`pages/MobileTeacher*.vue`、`pages/MobileEnterprisePortal.vue`、`pages/MobileMeetingJoin.vue`、`pages/MobileWorkAccount.vue`
- 离线演示增强：`前端/src/utils/demoAdapter.js`、`demoData.js`、`offlineDemo.js`、`Login.vue`、`OfflineDemoOverlay.vue`
- 手机端面试布局与语音作答：`前端/src/views/Interview.vue`、`AiPrep.vue`、`mobile/pages/MobileAiPrep.vue`
- 会议模块手机端适配：`MeetingRoom.vue`、`MeetingJoin.vue`、`EnterprisePortal.vue`、`useMeetingMedia.js`
- 两个新测试：`前端/tests/demoAdapter.test.js`、`前端/tests/mobileNav.test.js`

**禁止操作：**

- 不要 `git reset --hard`
- 不要 `git checkout -- .` / `git restore .`
- 不要 `git clean -fd`
- 不要在未征得用户同意前 `git add` / `commit` / `push`
- 不要把工作区改动当成「脏数据」清理掉

### 0.3 安全备份

拉取最新 main 之前，用户改动被保存在 stash 里：

```
stash@{0}: On feature/mobile-offline-demo: wip-before-pull-20261027
```

合并冲突已手工解决，stash 仍保留作为保险。**确认用户满意之前不要 `git stash drop`。**

### 0.4 最近一次拉取解决了哪些冲突

3 个文件有冲突，已解决：

1. `前端/src/views/Interview.vue`
   - 保留用户方案：手机端自拍画面（240×150）放在 AI 面试官下方，移除文字作答入口（只允许语音）
   - 接入上游新增的表情采样 props/events（`session-id`、`question-id`、`round-no`、`paused`、`server-offset`、`clock-ready`、`@expression-sample`、`@expression-state`、`@retry-clock`）
   - 删除上游重复的小摄像头 chip，避免手机端同时开两路摄像头
   - 桌面端摄像头模块保持上游原样
2. `前端/src/views/AiPrep.vue`
   - 保留上游「AI 只提议、必须用户确认」的逻辑与注释
   - 保留用户的 `scrollToTurnStart()` 滚动修复
3. `前端/src/mobile/pages/MobileAiPrep.vue`
   - 同上（手机端版本）

### 0.5 接手后先跑这三条命令确认现场

```bash
cd 前端 && npm test          # 应为 72/72 通过
cd 前端 && npm run build     # 应成功（只有 chunk 体积提示）
cd 后端 && JAVA_HOME="C:/Users/LENOVO/apk-builder/jdk21" ./mvnw -q -DskipTests compile
```

---

## 1. 这个项目是干什么的

**OfferPilot / 智面幻境**：面向大学生求职训练的 AI 模拟面试系统。天津工业大学软件学院大创项目，也用于比赛答辩。

一句话闭环：

> 选岗位 → 简历准备/核实 → 模拟面试（AI 提问 + 动态追问 + 语音作答 + 表情采集）→ 多维评分报告 → 弱项复盘/专项刷题

覆盖三类角色：

- **学生端**：选岗位、简历工作台、AI 面试、报告、历史、专项刷题
- **教师端**：班级/任务/学情看板/学生分析/教学联动
- **企业端**：视频会议面试（会议号进会）、写建议、学生回看
- **管理员**：后台入口（角色 `ADMIN`，注册不可创建）

核心卖点（答辩口径）：

- 岗位化组卷：按岗位能力标签从题库匹配题目，不靠 LLM 逐题现编
- 动态追问：DeepSeek 兼容大模型判断并生成追问，失败自动回落规则追问
- 证据化评分：10 个能力模块一次 LLM 评分，带 evidence / gap / suggestion，失败走规则兜底
- 三端联动：学生练、教师看学情、企业面谈给建议
- 多端：桌面 Web、手机响应式、Capacitor Android、HBuilderX 5+App 壳、离线演示

---

## 2. 技术栈（以代码为准）

> ⚠️ 根目录 `README.md` 有过时信息：它写 Java 17 / Element Plus / 五维评分，实际代码是 **JDK21 工具链 + 无 Element Plus + 10 模块评分**。以本文和代码为准。

| 层 | 实际使用 |
|----|----------|
| 前端 | Vue 3.5 + Vite 8 + Vue Router + Pinia + Axios + ECharts + Monaco Editor + CropperJS + simple-icons |
| 移动端 | Capacitor 8（Android 壳）+ HBuilderX 5+App 壳（`build:app` → `dist-app/`） |
| 后端 | Spring Boot 3.2.5（源码级别 Java 17，**本机必须用 JDK21 编译/测试**）、Spring Web/Validation/WebSocket、MyBatis-Plus 3.5.7、JWT（JJWT）、Apache Tika、PDFBox、POI、Lombok、Guava |
| 数据库 | MySQL 8，库名 `zhimian`，localhost:3306 |
| 大模型 | 自研 `DeepSeekClient`，走 OpenAI 兼容 `/chat/completions`；默认配置是智谱 `glm-4.7-flash`，可换 DeepSeek 官方。**没有 Spring AI / LangChain4j / Ollama** |
| 语音识别 | 智谱 GLM-ASR（`glm-asr-2512`），云 API，单段 WAV ≤30s |
| 表情识别 | 前端 `face-api.js`，模型在 `前端/public/models/face-api/`，采样上传后端 |
| 数字人 | LiveTalking（Wav2Lip + EdgeTTS），Python 服务，默认 8010 端口 |
| 判题 | 可选自部署 Judge0 CE，默认关闭（`judge.enabled=false`） |

---

## 3. 目录地图

```
offerpilot/
├── 前端/                         Vue 3 前端
│   ├── src/
│   │   ├── views/                桌面端页面（Interview、Report、JobSelect、Login…）
│   │   ├── mobile/               手机端双表适配层（Adaptive*View + Mobile* 页面）
│   │   ├── components/           通用组件（interview/meeting/layout/jobs…）
│   │   ├── composables/          useMicrophone、useFaceExpressions、useMeetingRoom…
│   │   ├── utils/                request、demoAdapter、offlineDemo、meetingProtocol…
│   │   ├── services/             learningResources、practiceDemo、resume…
│   │   ├── router/index.js       全部路由 + 角色 meta + 手机 surface meta
│   │   └── api/                  接口封装
│   ├── public/models/face-api/   face-api 模型权重
│   ├── tests/                    node --test 单测（72 个）
│   ├── capacitor.config.json     Android 壳配置
│   └── vite.config.js            代理 /api、/ws、/digital-human
│
├── 后端/                         Spring Boot 后端
│   ├── src/main/java/com/zhimian/
│   │   ├── controller/           接口层
│   │   ├── service/              业务层（InterviewFlowService、ModuleScoreService…）
│   │   ├── service/ai/           DeepSeekClient、PromptBuilder
│   │   ├── ws/                   会议 WebSocket 信令
│   │   ├── config/               AiProperties、JudgeProperties、拦截器
│   │   ├── entity/ mapper/ dto/  数据层
│   │   └── service/StudentBackendSchema.java  启动时自动执行增量建表 SQL
│   └── src/main/resources/db/    建表/迁移/题库 SQL
│
├── 数字人/                       LiveTalking 数字人服务（Python）
├── hbuilder-app/                 HBuilderX 5+App 壳工程
├── scripts/                      启动、打包、浏览器探针、题库构建脚本
├── docs/                         进度看板等
├── 一键启动网页.bat              一键启动（后端 + 前端）
├── HANDOFF-AI.md                 本文件
└── README.md                     项目介绍（部分内容过时）
```

---

## 4. 核心业务链路（按代码真实实现）

### 4.1 登录 / 角色 / 鉴权

- 后端：JWT 无状态 Token；`AuthInterceptor` 解析；接口用 `@RequireRole` 注解做角色校验；`UserContext` 取当前用户。
- 角色：`STUDENT` / `TEACHER` / `ENTERPRISE` / `ADMIN`。
- 注册白名单：只允许 `STUDENT`、`ENTERPRISE`；非法值静默降级为 `STUDENT`；`TEACHER` / `ADMIN` 不能注册。
- 前端：`router/index.js` 里有 `roleHome(role)` 决定登录落点；企业端落 `/enterprise/dashboard`，教师落 `/teacher/dashboard`，学生落 `/home`。
- 前端路由的角色限制主要是展示层，真正鉴权在接口层。

### 4.2 模拟面试主流程

入口：`前端/src/views/Interview.vue`；后端核心：`后端/.../service/InterviewFlowService.java`（近千行，是主流程中枢）。

流程：

1. 创建会话（岗位、时长、偏好）
2. 取题（岗位 tags → 简历 tags → 题库）
3. 逐题：展示题目 → 语音/文字作答 → 保存 `interview_message` → 判断是否追问
4. 追问：每题最多 1 次
5. 下一题 / 结束
6. 结束触发报告生成（`ModuleScoreService`）

关键实现细节（答辩容易被追问）：

- **追问每题最多 1 次**：`followupExists = count(sessionId, roundNo, MSG_FOLLOWUP) > 0`
- `shouldFollowUp()` 恒返回 `true`，**没有概率衰减/多层追问**
- 追问调用 **1 次** `DeepSeekClient.chatJson`
- Prompt 只带当前轮四要素（岗位、原题、参考答案、考生回答），**不带整场历史**
- 答案 <15 字走规则追问，不调 AI
- AI 失败/JSON 解析失败 → `followup_guide` 规则模板兜底
- 多轮记忆存 MySQL `interview_message`，**没有 Redis / 内存 Map**

### 4.3 大模型接入

- 类：`后端/.../service/ai/DeepSeekClient.java`
- 协议：OpenAI 兼容 `POST /chat/completions`，`RestClient` + `SimpleClientHttpRequestFactory`
- 方法：`chat()` / `chatStream()`（SSE）/ `chatJson()`
- 鉴权：`Authorization: Bearer ${ai.api-key}`
- 失败策略：任何异常返回 `null`，上层规则兜底
- 配置：`AiProperties`（`ai.provider`、`ai.base-url`、`ai.model`、`ai.api-key`、`ai.enabled`）
- 本地默认：智谱 `glm-4.7-flash`，`https://open.bigmodel.cn/api/paas/v4`
- 环境变量：`ZHIPU_API_KEY`、`ZHIPU_LLM_BASE_URL`、`ZHIPU_LLM_MODEL`

### 4.4 题库与组卷

- 表：`skill_question`（题）、`skill_tag`（能力标签）、`skill_question_tag_rel`（多对多）
- 岗位：`job_position`，`abilities` / `keywords` 是 TEXT(JSON)
- 7 大方向 = `job_position.category` 的 7 个取值：后端开发、前端与客户端开发、算法与人工智能、软件测试、全栈开发、数据分析、产品经理
- 组卷：岗位 tags 优先 → 简历技能 tags 第二 → 打乱 → 难度上限 → 去重
- 标签匹配：模糊匹配 `skill_tag.name`，ASCII 词边界（避免 Java 命中 JavaScript）
- **没有 job→question 外键**，没有向量检索/embedding
- LLM 只用于「经历题槽位」和题库冷启动，不逐题生成

### 4.5 评分与报告

- 入口：`ModuleScoreService.scoreAllModules()`
- 一次 LLM 调用评 **10 个模块**：`technical_base`、`technical_depth`、`engineering_practice`、`problem_analysis`、`project_expression`、`followup_adaptability`、`logical_structure`、`expression_clarity`、`position_cognition`、`project_review`
- 返回 JSON：`modules[{code, score, confidence, evidence[], gap_analysis, suggestion}]` + `overall_comment`
- 返回不足 10 个模块 → 整体降级规则评分
- 规则兜底：`fallbackRuleScoring()`，文本信号驱动（题数、追问数、长度、空话、技术词、项目词、STAR、反思等）
- `validateAndCapScores()` 按内容质量设分数上限（垃圾回答会被压到很低）
- 匹配度：`moduleMatch = min(rawScore / target, 1.2)`；`priority = weight × gap`；总匹配度按权重加权
- 权重来自 `ModulePreferenceService`：用户排序 → 名次池 → 全并列时均分
- evidence 是模型生成的短句，Java 用「；」拼成字符串存库，前端整段展示，**不可点击、不定位原文**
- 报告页：`前端/src/views/Report.vue`（ECharts 雷达图 + 模块卡 + QA 历史 + 改进清单）

### 4.6 简历

- 上传解析：`ResumeFileService`，Apache Tika 抽文本，PDF/DOC/DOCX ≤10MB
- **没有 OCR**：抽取 <30 字符判定扫描件并拒绝
- 画像：`resume`（raw_text / projects / skills / keywords）+ `resume_file` + `resume_paragraph` + `resume_skill`
- 分析器：`RuleBasedResumeAnalyzer`，正则 + 技术词典，**不是 LLM**
- 新增简历工作台：`ResumeBuilder.vue`、`ResumeWorkbenchService`、`pdfjs-dist`

### 4.7 语音与数字人

- 采集：`前端/src/composables/useMicrophone.js`
  - AudioWorklet 采 PCM
  - RMS 能量 VAD
  - 静音超时 / 超长自动切段
  - 每段编码 WAV → `POST /api/speech/transcribe`
  - 长时间静音 `autoSubmit` 自动交卷
- 后端：`SpeechRecognitionService` → `ZhipuAsrClient`（`glm-asr-2512`）
- **没有语速/停顿统计**；WAV 时长只用于 ≤30s 校验
- 数字人：`数字人/`（LiveTalking），默认 `--model wav2lip --tts edgetts`，8010 端口
- 后端 `DigitalHumanProcessManager` 可自动拉起 `数字人/start_offerpilot_digital_human.bat`
- 前端 `DigitalHumanStage.vue` 用 iframe 嵌 `http://host:8010/offerpilot-embed.html`

### 4.8 表情分析（上游新增）

- 前端：`useFaceExpressions.js` + `face-api.js`，模型 `前端/public/models/face-api/`
- 采样：`CameraPreview.vue` 触发 `expression-sample` / `expression-state`
- 上传：`ExpressionService.save()` → 表 `interview_expression_sample`
- 校验：采样时间必须落在面试时间窗内；题目/轮次必须属于该会话；单场最多 10000 条
- 报告：`ReportService` 把 `expressions` 放进报告响应
- **注意：表情数据目前不参与 10 模块评分**，只做展示/报告

### 4.9 专项刷题 / Judge0

- 前端：`LearningResources`、`TrainingSession.vue`、`PracticeLibrary.vue`、`PracticeReport.vue`
- `services/learningResources.js` 是**前端本地演示数据**（localStorage），`runLocalDemo()` 是正则判题，不接后端
- 后端另有一套真实练习：`PracticeController`、`PracticeService`、`PracticeWorkflowService`、`PracticeJudging`、`Judge0Client`
- Judge0 可选：`judge.enabled` / `judge.base-url` / `judge.auth-token`，默认关闭
- 不要混淆「前端演示版刷题」和「后端真实判题」

### 4.10 教师端

- 页面：`前端/src/views/teacher/*`、`TeacherAnalytics.vue`、手机端 `AdaptiveTeacherView.vue`
- 后端：`TeacherDashboardService`、`TeachingService`
- 看板数据是 **SQL/Mapper 聚合**（教学任务/作业/作答 + 面试报告 + 追问记录），**没有 LLM 生成**
- 含 AI/RULE 追问来源占比、7 日趋势、弱项分布、常见问题

### 4.11 企业端视频会议

- 形态：会议中心，不是招聘平台
- 功能：建会（8 位会议号）→ 按码进会 → WebRTC mesh 视频 → 写建议落库 → 学生回看 → 结束会议
- 后端：`MeetingController`、`MeetingService`、`ws/MeetingSignalHandler`、`ws/MeetingRoomRegistry`
- 信令：原生 WebSocket `/ws/meeting`，自研 JSON（join/offer/answer/ice/mic/cam/leave）
- 前端：`useMeetingRoom.js`、`useMeetingMedia.js`、`MeetingRoom.vue`、`MeetingJoin.vue`、`EnterprisePortal.vue`
- **没有 JD 发布、简历投递、候选人匹配、ATS**
- 仅局域网演示，STUN 兜底，无 TURN

### 4.12 移动端与离线演示

两条独立路线：

1. **响应式 Vue + 双表切换**
   - `AdaptiveStudentView.vue`：按路由 meta `mobileSurface` 切桌面/手机页面
   - `AdaptiveTeacherView.vue`：按 `teacherMobile`
   - `AdaptiveEnterpriseView.vue`：按 `enterpriseMobile`
   - 手机页面在 `前端/src/mobile/pages/`
2. **打包壳**
   - Capacitor：`npm run android:apk`（`dist/`）
   - HBuilderX 5+App：`npm run build:app`（`dist-app/`）+ `npm run app:sync`（铺到 `hbuilder-app/`）

离线演示：

- 登录页有「没有网络？进入离线演示」入口
- `demoAdapter.js` 拦截 axios，用内置假数据模拟整个后端
- `demoData.js` 提供演示数据
- 演示模式内禁止整页跳转；退出演示要回登录页
- 看到「假数据」时先确认是不是开了离线演示

---

## 5. 启动与验证

### 5.1 环境要求

- **JDK21**（本机路径 `C:/Users/LENOVO/apk-builder/jdk21`）——后端测试/编译必须用它，否则 Mockito 可能挂
- Node.js（前端已带 `package-lock.json`）
- MySQL 8，库 `zhimian`
- Python 环境（数字人，见 `数字人/requirements.txt`）

### 5.2 启动命令

```bash
# 后端（8080）
cd 后端
JAVA_HOME="C:/Users/LENOVO/apk-builder/jdk21" ./mvnw spring-boot:run

# 前端（5173）
cd 前端
npm install
npm run dev

# 数字人（8010，可选；后端也可能自动拉起）
cd 数字人
start_offerpilot_digital_human.bat
```

- 后端端口：8080（`application.yml` 的 `server.port`）
- 前端默认端口：5173（Vite 默认）
- 数字人：8010
- Vite 代理：`/api`、`/ws`（WebSocket）、`/digital-human` 等 → 后端/数字人
- `一键启动网页.bat` 可一键起前后端

### 5.3 测试与构建

```bash
cd 前端
npm test                 # node --test，72 个用例
npm run build            # 生产构建
npm run build:app        # HBuilderX 5+App 壳构建（dist-app/）
npm run app:sync         # 构建并同步到 hbuilder-app/

cd 后端
JAVA_HOME="C:/Users/LENOVO/apk-builder/jdk21" ./mvnw test
```

### 5.4 项目内的 `/dev-start` 技能

项目根下的 `.claude/skills/dev-start/` 是一份**可移植的**启动技能（已被 `.gitignore` 忽略，只在本机生效）：

- `SKILL.md`：先复盘再启动的步骤
- `PROJECT.md`：端口、启动命令、探活地址、测试命令

如果接手方是 Claude Code，在项目目录内可直接用 `/dev-start`；如果是其他 AI 工具，让它读 `.claude/skills/dev-start/SKILL.md` 并照做即可。换机器时需要手动把整个 `dev-start/` 文件夹复制过去。

### 5.5 配置与密钥

- 所有密钥只走环境变量，仓库里只有占位符
- 大模型：`ZHIPU_API_KEY`、`ZHIPU_LLM_BASE_URL`、`ZHIPU_LLM_MODEL`
- ASR：`ZHIPU_API_KEY`、`ZHIPU_ASR_BASE_URL`、`ZHIPU_ASR_MODEL`
- Judge0：`JUDGE_ENABLED`、`JUDGE_BASE_URL`、`JUDGE_AUTH_TOKEN`
- **不要**把真实 key 写进任何文件、日志、回复；不要在文档/代码块里回显
- 数据库密码请从本机已有配置/环境读取，不要写进文档，也不要反复试错导致锁号

---

## 6. 数据库与迁移

- 库名：`zhimian`
- 启动时 `StudentBackendSchema` 会自动执行这些**增量** SQL（`CREATE TABLE IF NOT EXISTS` 风格）：
  - `db/migration_expressions.sql`
  - `db/student_backend.sql`
  - `db/migration_teaching_linkage.sql`
  - `db/teaching_alignment.sql`
  - `db/resume_workbench.sql`
  - `db/resume_analysis.sql`
  - `db/migration_meeting.sql`
- 其他 `migration_*.sql` 需要按需手工执行
- **绝对不要重跑 `db/seed_skill_bank.sql`**：里面有 `DROP TABLE`，会清空题库

主要表：

- 用户/岗位/题库：`sys_user`、`job_position`、`skill_tag`、`skill_question`、`skill_question_tag_rel`
- 面试：`interview_session`、`interview_message`、`interview_report`、`interview_module_score`、`interview_followup_record`、`interview_expression_sample`
- 简历：`resume`、`resume_file`、`resume_paragraph`、`resume_skill`（以及工作台相关表）
- 教学：`teaching_*`
- 会议：`meeting`、`meeting_participant`、`meeting_advice`

---

## 7. 铁律与雷区

1. **JDK21**：后端编译/测试必须 `JAVA_HOME=C:/Users/LENOVO/apk-builder/jdk21`
2. **不要重跑 `seed_skill_bank.sql`**（DROP TABLE）
3. **不要整文件 `cat` 任何可能含密钥的 bat/ps1/yml**；要查配置用定点 `grep`，不要把密码/Key 打印到终端
4. **不要在命令里内嵌真实凭据**；连库优先用环境变量
5. **没有用户明确要求，不要 commit / push**；推送前必须 `git fetch`，**永远不要 force-push main**
6. 公开仓库有协作者，改动前先看 `git status` 和 `git log`
7. Windows 下不要用 `start` 启动浏览器/程序（Git Bash 会把 `/c` 当路径），用 `powershell Start-Process`
8. CDP/浏览器探针会受窗口遮挡影响，可能造出「假 bug」；验证前先确保窗口聚焦
9. README 有过时内容（Java 17、Element Plus、五维评分），以代码为准
10. 手机端打包有两条路线，产物目录不同：Capacitor 用 `dist/`，HBuilderX 用 `dist-app/`，不要互相覆盖
11. 离线演示（`demoAdapter`）是假后端，调试接口问题时先确认演示模式是否开启
12. 表情数据已接入报告，但**不在 10 模块评分里**；答辩/文档不要写成「表情参与评分」

---

## 8. 建议的接手阅读顺序

1. 本文第 0 节（现场状态）
2. `README.md`（了解产品故事，但注意过时点）
3. `前端/src/router/index.js`（看清所有页面、角色、手机 surface）
4. `后端/.../service/InterviewFlowService.java`（面试主流程）
5. `后端/.../service/FollowUpService.java` + `service/ai/FollowUpPromptBuilder.java`（追问）
6. `后端/.../service/ModuleScoreService.java` + `service/ai/ScorePromptBuilder.java`（评分）
7. `前端/src/views/Interview.vue` + `composables/useMicrophone.js`（作答与语音）
8. `后端/.../service/ExpressionService.java` + `前端/src/composables/useFaceExpressions.js`（表情）
9. `后端/.../ws/MeetingSignalHandler.java` + `前端/src/composables/useMeetingRoom.js`（会议）
10. `前端/src/utils/demoAdapter.js` + `offlineDemo.js`（离线演示）

---

## 9. 术语表

| 术语 | 含义 |
|------|------|
| 追问 / follow-up | 针对考生当前回答生成的下一问，每题最多 1 次 |
| RULE | 规则兜底（AI 不可用/失败时） |
| 模块 / module | 评分体系的 10 个能力维度 |
| 匹配度 / moduleMatch | `min(score/target, 1.2)`，按权重加权成总匹配度 |
| 优先级 / priority | `weight × gap`，报告改进清单按它排序 |
| 岗位 tags | `job_position.abilities` / `keywords` 解析出的标签名 |
| 手机双表 | `Adaptive*View` 按断点切桌面组件 / 手机页面 |
| 离线演示 | `demoAdapter` 拦截 axios 的假后端模式 |
| 专项刷题 | `/learning` 前端演示版 + 后端 Practice/Judge0 真实判题两套 |
| 数字人 | LiveTalking 驱动的 AI 面试官形象 |

---

## 10. 已知差异与待办候选（供接手 AI 参考，不要擅自改）

这些是「讲稿/宣传」与「本地代码」的差异，或者明显可继续完善的点：

1. **追问**：讲稿有「概率双重衰减 / 多层追问」，代码是每题 1 次、`shouldFollowUp` 恒 true
2. **四类证据**：讲稿提到项目细节/技术取舍/量化结果/反思四类，Prompt 实际是覆盖度/准确性/深度
3. **表情**：已采集、已入库、已进报告，但未参与 10 模块评分
4. **专项复训**：报告页把 `weakTags` 拼进 `/jobs?weakTags=...`，但 `JobSelect.vue` 还没消费这个参数
5. **教师看板**：SQL 聚合，不是 LLM 学情分析
6. **企业端**：只有会议 + 建议，没有 JD/候选人匹配
7. **简历解析**：Tika + 正则规则，不是 LLM / OCR
8. **开源**：仓库公开但没有根目录 LICENSE；LiveTalking 是 Apache-2.0，Wav2Lip 权重有单独限制
9. **README**：技术栈与评分维度描述过时

---

*本文档由当前会话生成，描述的是 2026-10-27 的工作区状态。后续改动请同步更新第 0 节。*
