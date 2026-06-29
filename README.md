# OfferPilot · 智面幻境 — AI 模拟面试系统

> 面向大学生求职训练的 AI 模拟面试系统
> 天津工业大学 · 软件学院 · 大创项目

## 项目简介

**OfferPilot / 智面幻境** 是一套面向大学生求职训练的 AI 模拟面试系统，提供「岗位选择 → 简历/面试准备 → 模拟面试 → 动态追问 → 多维评分报告 → 训练复盘」的完整闭环。

核心亮点：

- **岗位化模拟面试**：按目标岗位生成贴合岗位的面试题目与场景。
- **动态追问**：基于 DeepSeek 大模型对考生回答实时生成追问；不可用时自动回退到内置规则（RULE）追问，保证流程不中断。
- **多维评分报告**：面试结束自动生成五维能力评分（专业知识掌握 / 项目实践表达 / 逻辑表达能力 / 岗位匹配程度 / 动态追问应对），含雷达图与优势 / 不足 / 建议。
- **训练闭环**：历史记录可回看报告，支持针对性复训。
- **追问记录分析**：持久化每次追问，并提供 AI / RULE 来源占比等统计，便于评估追问质量。

## 核心功能

- 用户登录 / 注册（注册仅创建学生角色）
- 岗位选择
- 简历录入 / 面试准备
- 模拟面试流程（提问 → 作答 → 追问 → 下一题 → 结束）
- DeepSeek AI 动态追问
- RULE 规则化追问回退（AI 不可用时自动启用）
- 多维能力评分报告
- 面试历史记录
- 追问记录持久化
- 追问记录查询与 AI / RULE 来源统计界面

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Element Plus + Vue Router + Pinia + Axios + ECharts |
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 |
| AI 服务 | DeepSeek（OpenAI 兼容接口）动态追问 + RULE 规则化回退 |
| 鉴权 | JWT（无状态 Token 鉴权） |

## 目录结构

```
大创/
├── 后端/                                    Spring Boot 后端工程
│   ├── mvnw.cmd                             Maven Wrapper（无需本地安装 Maven）
│   └── src/main/resources/
│       ├── db/schema.sql                    建库建表脚本（含初始账号/岗位）
│       ├── db/seed_question.sql             题库种子数据（可选）
│       ├── application.yml                  默认（开发）配置
│       ├── application-prod.yml             生产配置（仅占位符，靠环境变量注入）
│       └── logback-spring.xml              日志配置（开发控制台 / 生产滚动文件）
├── 前端/                                    Vue 3 前端工程
├── docs/                                    项目文档（需求 / 设计 / 测试 / 接口）
└── README.md
```

## 环境要求

- **Java 17**（后端运行环境）
- **Node.js**（建议 18+，用于前端构建）
- **MySQL 8**
- **Maven Wrapper**：仓库内置 `后端/mvnw.cmd`，无需单独安装 Maven
- **DeepSeek API Key**：可选。配置后启用真实 AI 动态追问；未配置时系统使用 RULE 规则化追问，仍可正常演示

## 数据库初始化

1. 确保本地 MySQL 8 已启动。
2. 首次初始化执行建表脚本（脚本内含 `CREATE DATABASE IF NOT EXISTS zhimian`，会自动创建并切换到 `zhimian` 库）：

   ```powershell
   mysql -u root -p < 后端/src/main/resources/db/schema.sql
   ```

   > ⚠️ **警告**：`schema.sql` 包含 `DROP TABLE IF EXISTS ...` 语句，**重复执行会删除已有表及其全部数据**。仅在首次本地初始化、或确认要重置数据时运行。

3. （可选）若需要导入题库种子数据，执行 `seed_question.sql`：

   ```powershell
   mysql -u root -p zhimian < 后端/src/main/resources/db/seed_question.sql
   ```

   该脚本仅插入题目数据，不删除表；用于补充演示用题库。

## 环境变量

后端通过环境变量注入配置，开发环境多数项有安全默认值，**生产环境必须显式注入敏感项**。

| 变量 | 说明 | 开发默认 | 生产 |
|------|------|----------|------|
| `DB_URL` | 数据库 JDBC 连接串 | 本地 `zhimian` 库 | 建议显式注入 |
| `DB_USERNAME` | 数据库用户名 | `root` | 建议显式注入 |
| `DB_PASSWORD` | 数据库密码 | `123456`（仅本地） | **必须注入，无默认值** |
| `JWT_SECRET` | JWT 签名密钥（HS256，≥32 字符） | 开发占位值 | **必须注入，无默认值** |
| `AI_API_KEY` | DeepSeek API Key | 占位值（AI 不可用时走 RULE） | **必须注入，无默认值** |
| `CORS_ALLOWED_ORIGINS` | 允许的前端跨域来源（逗号分隔） | 本地 Vite 地址 | 固定白名单（默认空 = 不放行） |
| `SPRING_PROFILES_ACTIVE` | 激活的配置 profile | 默认（dev） | 设为 `prod` |
| `LOG_PATH` | 生产日志输出目录 | 不适用（开发仅控制台） | 默认 `logs`，可覆盖 |

## 后端启动 — 开发环境

```powershell
cd 后端
.\mvnw.cmd spring-boot:run
```

开发环境默认行为：

- 使用默认 profile（`application.yml`），数据库密码等敏感项有本地默认值，开箱即用。
- 日志仅输出到控制台。
- AI 已启用，但若 `AI_API_KEY` 为占位值或调用失败，会自动回退到 RULE 追问。
- 后端服务地址：`http://localhost:8080`

## 后端启动 — 生产环境（prod profile）

生产环境必须激活 `prod` profile，并通过环境变量注入全部敏感项（以下仅为占位符示例，**请勿提交真实密钥**）：

```powershell
# PowerShell 示例（值均为占位符，请替换为真实值，且不要写入仓库）
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_URL                 = "jdbc:mysql://<db-host>:3306/zhimian?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true&allowPublicKeyRetrieval=true"
$env:DB_USERNAME            = "<db-user>"
$env:DB_PASSWORD            = "<db-password>"
$env:JWT_SECRET             = "<至少32字符的随机密钥>"
$env:AI_API_KEY             = "<your-deepseek-api-key>"
$env:CORS_ALLOWED_ORIGINS   = "https://your-frontend-domain"
$env:LOG_PATH               = "logs"

cd 后端
.\mvnw.cmd spring-boot:run
```

> 生产 profile 对 `DB_PASSWORD` / `JWT_SECRET` / `AI_API_KEY` 不提供默认值——未注入则启动失败（快速失败），避免带弱默认值上线。

## 前端启动

```powershell
cd 前端
npm install
npm run dev        # 开发模式，默认 http://localhost:5173
npm run build      # 生产构建，产物输出到 前端/dist
```

## 测试账号

`schema.sql` 初始化时会插入以下演示账号，**密码均为 `123456`（仅限本地演示，切勿用于生产）**：

| 用户名 | 角色 | 密码 |
|--------|------|------|
| `admin` | ADMIN | `123456`（本地演示） |
| `teacher` | TEACHER | `123456`（本地演示） |
| `student` | STUDENT | `123456`（本地演示） |

> 这些账号仅用于本地演示与联调。生产部署前请删除或重置为强密码，切勿暴露任何生产口令。注意：公开注册接口只会创建 **STUDENT** 角色，TEACHER / ADMIN 不能通过注册自助开通。

## 主要 API 示例

> 除登录 / 注册外，接口均需在请求头携带 `Authorization: Bearer <token>`。

**登录**

```http
POST /api/auth/login
Content-Type: application/json

{ "username": "student", "password": "123456" }
```

**提交面试回答**

```http
POST /api/interview/{sessionId}/answer
Authorization: Bearer <token>
Content-Type: application/json

{ "content": "我的回答内容……" }
```

**查询追问记录**

```http
GET /api/interview/follow-up-records
Authorization: Bearer <token>
```

**查询追问统计（AI / RULE 来源占比）**

```http
GET /api/interview/follow-up-records/stats
Authorization: Bearer <token>
```

返回结构统一为 `{ "code": ..., "message": ..., "data": ... }`。

## 安全注意事项

- **严禁提交真实 `AI_API_KEY`**（DeepSeek Key）到仓库。
- **严禁提交生产数据库密码 `DB_PASSWORD`**。
- **严禁提交 `JWT_SECRET`**；生产环境必须使用足够强度的随机密钥（HS256 要求 ≥32 字符）。
- 公开注册接口仅创建 **STUDENT** 角色，无法自助提权。
- 追问记录的查询与统计已按当前登录用户隔离，用户只能访问自己的数据。
- 生产环境 CORS 必须使用固定白名单（`CORS_ALLOWED_ORIGINS`），不使用通配符；默认空值表示不放行任何跨域来源。

## 日志与排障

- **开发环境**：日志输出到控制台。
- **生产环境**：滚动文件日志写入 `后端/logs/zhimian.log`（可通过 `LOG_PATH` 环境变量改变目录），按天与大小切分、归档压缩，默认保留 30 天。
- **未知异常**：前端只会收到通用提示「系统繁忙，请稍后重试 (traceId: ...)」，不会暴露原始异常栈。
- **用 traceId 排障**：拿到响应中的 `traceId` 后，在服务端日志中检索同一 `traceId`，即可定位对应的完整异常堆栈。

## 路线图（Roadmap）

| 阶段 | 规划 |
|------|------|
| Phase 4.2 | 多角色与组织（机构）基础 |
| Phase 4.3 | 学校侧数据看板 |
| Phase 4.4 | 企业侧岗位管理 |
| Phase 4.5 | 部署与内部 Beta |

## 开发进度

详见 [docs/进度看板.md](docs/进度看板.md)
