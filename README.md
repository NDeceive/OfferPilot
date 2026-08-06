# OfferPilot · 智面幻境 — AI 模拟面试系统

> 面向大学生求职训练的 AI 模拟面试系统
> 天津工业大学 · 软件学院 · 大创项目

## 项目简介

**OfferPilot / 智面幻境** 是一套面向大学生求职训练的 AI 模拟面试系统，提供「岗位选择 → 简历/面试准备 → 数字人模拟面试 → AI 动态追问 → 多维评分报告 → 训练复盘」的完整闭环。

核心亮点：

- **岗位化模拟面试**：7 大类 32+ 细分方向的目标岗位，按岗位要求匹配技能题库出题。
- **智能出题引擎**：基于简历画像 + 岗位标签双向模糊匹配题库，每 5 题插入 AI 实时生成的情景体验题。
- **AI 数字人面试官**：Wav2Lip 实时唇形同步 + EdgeTTS 语音合成 + WebRTC 低延迟推流，拟真面试场景。
- **语音交互**：浏览器麦克风录音 → 智谱 GLM-ASR-2512 语音转文字，实时识别作答内容。
- **AI 动态追问**：DeepSeek 大模型对比参考答案与考生回答，判断是否追问并生成追问问题；不可用时自动回退规则引擎（RULE），保证流程不中断。
- **多维评分报告**：可配置的多模块能力评分，支持雷达图展示「实际能力」与「目标能力」双线对比，含评分依据、模块建议和提升路径。
- **报告导出**：支持 PDF（Apache PDFBox）和 Word（Apache POI）格式导出，无需安装 Microsoft Word。
- **训练闭环**：历史记录可回看报告，薄弱项标签支持针对性复训。
- **教师端看板**：全局训练统计、7 天趋势、薄弱项分布、学生训练列表、常见问题分析。

## 核心功能

**学生端**
- 用户登录 / 注册
- 岗位选择（三栏：岗位族导航 → 岗位列表 → 岗位详情）
- 简历录入（文本粘贴 / 文件上传，自动解析生成技能画像）
- 训练目标配置（5 项能力模块 + 自定义权重 + 目标等级）
- 自定义面试时长（5 分钟 ~ 2 小时）
- 数字人模拟面试（出题 → 语音播报 → 录音作答 → AI 追问 → 下一题）
- 语音识别（智谱 ASR，支持 VAD 自动切分）
- 体验式情景题（每 5 题插入 1 道 AI 实时生成）
- 多模块能力评分报告（实际 vs 目标双线雷达图）
- 报告导出（PDF / Word）
- 面试历史记录（状态筛选 + 岗位分类筛选）
- 追问记录统计（AI / RULE 来源占比）

**教师端**
- 总览面板（学生总数、已训练数、完成率、平均分、AI 追问数）
- 7 天训练趋势图
- 薄弱项分布（四类：项目表达 / 追问应对 / 技术细节 / 逻辑结构）
- 学生训练列表（训练次数、均分、最近训练时间、状态）
- 常见问题分析（按严重度排序）

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Pinia + Vue Router + Axios + ECharts |
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| 数据库 | MySQL 8（16 张数据表） |
| LLM | DeepSeek（OpenAI 兼容接口）— 智能追问 + 体验题生成 |
| ASR | 智谱 GLM-ASR-2512 — 语音转文字 |
| 数字人 | Python + Flask + aiohttp + Wav2Lip + EdgeTTS + WebRTC |
| 报告导出 | Apache PDFBox + Apache POI（纯 Java，无外部依赖） |
| 鉴权 | JWT（无状态 Token 鉴权）+ 三级角色（STUDENT / TEACHER / ADMIN） |

## 目录结构

```
OfferPilot/
├── 后端/                                    Spring Boot 后端工程
│   ├── mvnw.cmd                             Maven Wrapper
│   ├── libs/                                documents4j 本地 JAR（已废弃，保留兼容）
│   ├── logs/                                运行时日志
│   └── src/main/resources/
│       ├── db/schema.sql                    建库建表脚本（含初始账号/岗位/技能题库）
│       ├── db/seed_question.sql             题库种子数据
│       ├── db/seed_skill_bank.sql           技能题库种子数据
│       ├── db/migration_v2.sql              V2 数据库迁移脚本
│       ├── db/migration_v3.sql              V3 简历表迁移脚本
│       ├── db/migration_v3_jobs.sql         岗位库迁移脚本
│       ├── db/migration_v3_job_icons.sql    岗位图标迁移脚本
│       ├── db/migration_scoring_v1.sql      评分模块迁移脚本
│       ├── application.yml                  默认（开发）配置
│       ├── application-prod.yml             生产配置（仅占位符，靠环境变量注入）
│       └── logback-spring.xml              日志配置
├── 前端/                                    Vue 3 前端工程
│   └── public/
│       ├── audio/                           录音 Processor 脚本
│       └── job-logos/                       岗位 Logo 静态资源
├── 数字人/                                  Python 数字人服务（LiveTalking）
│   ├── app.py                              服务入口
│   ├── server/                             WebRTC + API 路由 + 会话管理
│   ├── avatars/                            Wav2Lip / MuseTalk / UltraLight 模型
│   ├── tts/                                13 种 TTS 引擎插件
│   ├── web/                                offerpilot-embed.html + 静态资源
│   └── models/                             Wav2Lip 模型权重 + S3FD 人脸检测
├── docs/                                    项目文档
└── README.md
```

## 环境要求

- **Java 17**（后端运行环境）
- **Node.js**（建议 18+，用于前端构建）
- **MySQL 8**
- **Python 3.10+** + **PyTorch** + **CUDA GPU**（数字人服务，可选；无 GPU 时可纯文字模式运行）
- **DeepSeek API Key**（可选。未配置时使用 RULE 规则引擎，仍可正常演示）
- **智谱 API Key**（可选。未配置时语音识别不可用，可切换文字输入模式）

## 快速开始

### 1. 数据库初始化

```powershell
mysql -u root -p < 后端/src/main/resources/db/schema.sql
```

> ⚠️ `schema.sql` 包含 `DROP TABLE IF EXISTS`，**重复执行会删除已有数据**。

### 2. 后端启动

```powershell
cd 后端
.\mvnw.cmd spring-boot:run
```

后端启动时会自动检测并拉起数字人服务（开发环境默认开启，可通过 `DIGITAL_HUMAN_ENABLED=false` 关闭）。

### 3. 前端启动

```powershell
cd 前端
npm install
npm run dev        # 默认 http://localhost:5173
```

### 4. 数字人服务（可选，手动启动）

```powershell
cd 数字人
.\start_offerpilot_digital_human.bat
```

数字人服务监听 `http://127.0.0.1:8010`，面试页通过 iframe + postMessage 嵌入。

## 运行端口

| 服务 | 端口 |
|------|------|
| 前端 (Vite) | 5173 |
| 后端 (Spring Boot) | 8080 |
| 数字人 (Python) | 8010 |

## 环境变量

| 变量 | 说明 | 开发默认 | 生产 |
|------|------|----------|------|
| `DB_PASSWORD` | 数据库密码 | `123456`（仅本地） | **必须注入** |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | 开发占位值 | **必须注入** |
| `AI_API_KEY` | DeepSeek API Key | 占位值（走 RULE 兜底） | **必须注入** |
| `ZHIPU_API_KEY` | 智谱 API Key（LLM + ASR 共用） | 空（语音不可用） | 按需注入 |
| `AI_ENABLED` | 是否启用真实 AI 调用 | `true` | 按需 |
| `CORS_ALLOWED_ORIGINS` | 允许的前端跨域来源 | 本地 Vite 地址 | 固定白名单 |
| `SPRING_PROFILES_ACTIVE` | 激活配置 profile | 默认（dev） | 设为 `prod` |
| `DIGITAL_HUMAN_ENABLED` | 是否启用数字人 | `true` | `false` |
| `DIGITAL_HUMAN_BASE_URL` | 数字人服务地址 | `http://127.0.0.1:8010` | 按实际部署 |

## 测试账号

`schema.sql` 初始化时插入以下演示账号，**密码均为 `123456`（仅限本地演示）**：

| 用户名 | 角色 | 密码 |
|--------|------|------|
| `admin` | ADMIN | `123456` |
| `teacher` | TEACHER | `123456` |
| `student` | STUDENT | `123456` |

> 公开注册接口只会创建 **STUDENT** 角色，TEACHER / ADMIN 不能通过注册自助开通。

## 主要 API

> 除登录 / 注册外，接口均需携带 `Authorization: Bearer <token>`。

**登录**

```http
POST /api/auth/login
Content-Type: application/json

{ "username": "student", "password": "123456" }
```

**首页聚合**

```http
GET /api/dashboard/overview
Authorization: Bearer <token>
```

**开始面试**

```http
POST /api/interview/start
Authorization: Bearer <token>
Content-Type: application/json

{ "jobId": 1, "difficulty": 2, "durationSeconds": 1800 }
```

**语音转写**

```http
POST /api/speech/transcribe
Authorization: Bearer <token>
Content-Type: multipart/form-data

// file: audio.wav, sessionId: 123
```

**导出报告**

```http
GET /api/report/{reportId}/export?format=pdf
Authorization: Bearer <token>
```

**教师仪表盘**

```http
GET /api/teacher/dashboard/overview
Authorization: Bearer <token>    # 仅 TEACHER / ADMIN
```

返回结构统一为 `{ "code": ..., "message": ..., "data": ... }`。

## 安全注意事项

- **严禁提交真实 API Key**（DeepSeek / 智谱）到仓库。
- **严禁提交生产数据库密码** `DB_PASSWORD`。
- **严禁提交 `JWT_SECRET`**；生产环境必须使用足够强度的随机密钥（HS256 要求 ≥32 字符）。
- 公开注册接口仅创建 **STUDENT** 角色，无法自助提权。
- 所有数据查询已按当前登录用户隔离。
- 生产环境 CORS 必须使用固定白名单；默认空值不放行任何跨域来源。

## 日志与排障

- **开发环境**：日志输出到控制台。
- **生产环境**：滚动文件日志写入 `后端/logs/zhimian.log`，按天与大小切分、归档压缩，默认保留 30 天。
- **数字人日志**：写入 `后端/logs/digital-human.log`。
- **未知异常**：前端收到通用提示 + traceId，可在服务端日志中检索定位。

## 路线图

| 阶段 | 规划 |
|------|------|
| ✅ Phase 4-5 | 生产基线 + 教师端 + 报告导出 |
| ✅ Phase 8.0 | 完整功能联调：数字人 + 语音 + AI 追问 + 多模块评分 + 报告导出 |
| 🔄 当前 | 端到端回归测试 + 异常场景处理 + AI 回答质量调优 |
| ⏳ 下一阶段 | GPU 部署数字人 + 性能优化 + 正式部署准备 |
| 📋 长期 | 移动端适配 + 真实企业面试场景 + 多语言支持 |
