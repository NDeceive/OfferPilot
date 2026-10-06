# OfferPilot · 智面幻境 — AI 模拟面试系统

> 面向大学生求职训练的 AI 模拟面试系统
> 天津工业大学 · 软件学院 · 大创项目

## 项目简介

**OfferPilot / 智面幻境** 是一套面向大学生求职训练的 AI 模拟面试系统，提供「岗位选择 → 简历/面试准备 → 模拟面试 → 动态追问 → 多维评分报告 → 训练复盘」的完整闭环。

核心亮点：

- **岗位化模拟面试**：按目标岗位生成贴合岗位的面试题目与场景。
- **动态追问**：基于智谱 GLM 大模型对考生回答实时生成追问；不可用时自动回退到内置规则（RULE）追问，保证流程不中断。
- **语音回答**：支持微信通话式开麦/静音、自动分句，并通过 GLM-ASR 转换为可编辑文字。
- **多维评分报告**：面试结束自动生成五维能力评分（专业知识掌握 / 项目实践表达 / 逻辑表达能力 / 岗位匹配程度 / 动态追问应对），含雷达图与优势 / 不足 / 建议。
- **训练闭环**：历史记录可回看报告，支持针对性复训。
- **追问记录分析**：持久化每次追问，并提供 AI / RULE 来源占比等统计，便于评估追问质量。

## 核心功能

- 用户登录 / 注册（注册仅创建学生角色）
- 岗位选择
- 简历录入 / 面试准备
- 模拟面试流程（提问 → 作答 → 追问 → 下一题 → 结束）
- 智谱 GLM AI 动态追问
- GLM-ASR 语音回答转写
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
| AI 服务 | 智谱 GLM-4.7-Flash 动态追问 + GLM-ASR-2512 语音转写 + RULE 回退 |
| 鉴权 | JWT（无状态 Token 鉴权） |

## 目录结构

```
OfferPilot/
├── 后端/                                    Spring Boot 后端工程
│   ├── mvnw.cmd                             Maven Wrapper（无需本地安装 Maven）
│   └── src/main/resources/
│       ├── db/schema.sql                    建库建表脚本（含初始账号/岗位）
│       ├── db/seed_question.sql             题库种子数据（可选）
│       ├── application.yml                  默认（开发）配置
│       ├── application-prod.yml             生产配置（仅占位符，靠环境变量注入）
│       └── logback-spring.xml              日志配置（开发控制台 / 生产滚动文件）
├── 前端/                                    Vue 3 前端工程
├── 数字人/                                  WebRTC 数字人服务、模型和头像资源
├── docs/                                    项目文档（需求 / 设计 / 测试 / 接口）
└── README.md
```

## 环境要求

- **Java 17**（后端运行环境）
- **Node.js**（建议 18+，用于前端构建）
- **MySQL 8**
- **Python 3.10**（数字人首次启动时自动创建 `.venv`）
- **Maven Wrapper**：仓库内置 `后端/mvnw.cmd`，无需单独安装 Maven
- **智谱 API Key**：大模型追问与语音转写共用，通过后端环境变量注入；严禁写入前端或仓库

### 配置智谱 API Key（每位使用者自行输入）

1. 在智谱开放平台创建你自己的 API Key，并了解对应模型的计费规则。
2. Windows 用户运行项目根目录的 `一键启动网页.bat`。首次启动后端且未检测到密钥时，启动窗口会询问是否配置；选择 `Y` 或直接回车，再在隐藏输入框中粘贴密钥。输入不会显示，也不会写入项目文件。选择 `n` 可以跳过，但语音识别不可用。
3. 启动器会将密钥保存到**当前 Windows 用户环境变量**，供下次启动使用，并传给本次启动的后端。用户环境变量仍是本机可读取的凭据，请只在可信电脑上使用。更换密钥后须重启后端。
4. 如果手动启动后端，可在同一个 PowerShell 窗口用隐藏输入设置仅当前窗口有效的环境变量，再运行 `cd 后端` 和 `.\mvnw.cmd spring-boot:run`：

   ```powershell
   $secureKey = Read-Host '智谱 API Key' -AsSecureString
   $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
   try {
       $env:ZHIPU_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer)
   } finally {
       [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
       Remove-Variable secureKey, keyPointer -ErrorAction SilentlyContinue
   }
   ```

若启动器发现后端已经运行，它不能给正在运行的进程补充密钥；请关闭后端后重新启动。没有配置密钥时，动态追问会回退到 RULE，但语音转写无法使用。**不要把密钥填入前端 `.env`、提交到 GitHub，或发给他人。**

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

4. V3 面试流程必须初始化技能标签题库：

   ```powershell
   cmd /c "mysql --default-character-set=utf8mb4 -u root -p zhimian < 后端/src/main/resources/db/seed_skill_bank.sql"
   ```

   该脚本会创建技能标签、技能题目和题目标签关系表，并导入 57 个标签与 71 道题。
   重复执行会重建这三张技能题库表，但不会删除用户、简历或面试数据。

## 环境变量

后端通过环境变量注入配置，开发环境多数项有安全默认值，**生产环境必须显式注入敏感项**。

| 变量 | 说明 | 开发默认 | 生产 |
|------|------|----------|------|
| `DB_URL` | 数据库 JDBC 连接串 | 本地 `zhimian` 库 | 建议显式注入 |
| `DB_USERNAME` | 数据库用户名 | `root` | 建议显式注入 |
| `DB_PASSWORD` | 数据库密码 | `123456`（仅本地） | **必须注入，无默认值** |
| `JWT_SECRET` | JWT 签名密钥（HS256，≥32 字符） | 开发占位值 | **必须注入，无默认值** |
| `ZHIPU_API_KEY` | 智谱 API Key（LLM 与 ASR 共用） | 空（AI 走 RULE，语音转写不可用） | **必须注入，无默认值** |
| `ZHIPU_LLM_MODEL` | 智谱大模型名称 | `glm-4.7-flash` | 可覆盖 |
| `ZHIPU_ASR_MODEL` | 智谱语音识别模型 | `glm-asr-2512` | 可覆盖 |
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
- 若未配置 `ZHIPU_API_KEY` 或大模型调用失败，动态追问自动回退到 RULE；语音转写需要有效密钥。
- 后端会检查 `8010` 端口；数字人未运行时自动启动 `数字人/start_offerpilot_digital_human.bat`。
- 数字人首次启动会从项目 GitHub Release 下载 Wav2Lip 模型、基础头像和当前使用的 `offerpilot_interviewer_v2` 头像资源（这些大文件不进入 Git 历史），随后创建 Python 3.10 虚拟环境并安装依赖，耗时较长；进度见 `后端/logs/digital-human.log`。
- 后端关闭时，只关闭由本次后端启动的数字人实例。
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
$env:ZHIPU_API_KEY          = "<your-zhipu-api-key>"
$env:CORS_ALLOWED_ORIGINS   = "https://your-frontend-domain"
$env:LOG_PATH               = "logs"

cd 后端
.\mvnw.cmd spring-boot:run
```

> 生产 profile 对 `DB_PASSWORD` / `JWT_SECRET` / `ZHIPU_API_KEY` 不提供默认值——未注入则启动失败（快速失败），避免带弱默认值上线。

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

- **严禁提交真实 `ZHIPU_API_KEY`** 到仓库、前端代码、截图或聊天记录。
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
