# 学生端后端缺口审计

2026-10-03。依据当前 Vue 页面/API 调用、Spring Boot Controller/Service/DTO、数据库迁移及本地 MySQL 只读查询。此次只审计，不修改业务代码、不清理数据、不发送邮件。静态确认的问题未全部进行接口故障重放。

## 已有能力

注册/登录、本人资料和求职档案、头像、简历上传/文本分析/技能标签、岗位查询、自主面试出题/回答/追问、原始问答、报告/建议/PDF与Word导出、语音转写都有后端实现。班级加入、教师审核、任务分配、任务继续、有效次数、任务报告重试、教师反馈和站内消息已接入数据库，不能继续列为“完全缺失”。AI和ASR是否可用取决于部署配置；规则降级不是接口缺失。

## P0：先修数据正确性和可恢复性

### 1. 自主面试的会话和回答约束弱于教学任务

- `InterviewFlowService.start` 每次自主开始都新增会话，没有相同开始请求的幂等键；教学任务已有分配行锁。
- `answer` 只有教学分支校验当前问题、重复回答和超时。自主分支允许再次给之前提问的题作答，重复请求会新增回答；`next` 自主分支没有会话行锁和当前题回答检查。
- 体验题每次都用 `questionId=0`，而回答通过 sessionId+questionId+MAIN+LIMIT 1 定位。一次会话出现多道体验题时，后续回答可能绑定第一道体验题的轮次与参考答案。
- 自主恢复页面根据 query 或第一条 MAIN 消息恢复，缺少服务端返回当前问题/阶段/剩余时间的统一接口。只有教学任务恢复有剩余时间。

建议：用问题实例 ID（实际提问消息）而非仅题库 ID 绑定回答；按会话串行推进与幂等提交；增加统一会话详情/恢复接口和过期收尾。自主跳题如需保留应有明确 SKIPPED 事件。只读快照发现 **4个超过配置时长、仍为ONGOING的会话**；不能把这些场次一直视为可继续训练。

证据：`后端/src/main/java/com/zhimian/service/InterviewFlowService.java:113,214,286,346,816`；`前端/src/views/Interview.vue:222`。

### 2. 自主报告没有完整失败状态与重试；报告工作不能跨重启恢复

- `finish` 对自主会话重复调用仍会启动新的 CompletableFuture；报告的 session_id 唯一索引阻止重复报告行，但不阻止重复工作和评分并发。
- 自主 `reportState` 始终返回 GENERATING（ready另算），没有真实 FAILED 状态或自主重试接口。生成失败后前端不能可靠区分“还在处理”和“已经失败”。
- 教学报告虽有FAILED及手动重试，但正常工作仍运行在进程内。服务重启可能使持久化GENERATING记录失去执行者，尚无超时恢复/重新领取机制。本次查询长期GENERATING教学记录为0，说明是恢复能力缺口，并非已观察到挂起。

建议：统一报告工作状态、幂等领取、失败原因/重试次数、超时恢复；首先用现有数据库状态和受控后台执行器解决，不必先引入外部消息队列。

证据：`InterviewFlowService.java:346,364,399`；`TeachingService.java:109,111,112`；`controller/TeachingController.java:40`。

### 3. 删除自主面试不清理新评分数据

`InterviewFlowService.delete` 只删除旧报告维度、报告、追问、消息和会话，未删除 interview_module_score、interview_module_preference、interview_score_config_snapshot；单条删除本身也没有事务声明。

本地只读查询已发现：

| 关联缺失 | 条数 |
|---|---:|
| 无对应报告的模块评分 | 115 |
| 无对应会话的模块偏好 | 28 |
| 无对应会话的评分快照 | 28 |

这些是当前孤立记录数量，不据此推断它们全部由某一次操作造成。建议先补单会话事务删除、生成中删除的协调与所有关联表清理，再单独制定旧孤立数据清理步骤；教学记录继续保留。此次未删除任何数据。

证据：`InterviewFlowService.java:410`；`db/migration_scoring_v1.sql`；本地LEFT JOIN计数。

### 4. 登录凭证没有跟随账号变更失效

`AuthInterceptor` 只验JWT签名与到期，直接采用token里的role；不检查账号是否仍存在、已被禁用或角色已变。修改密码只更新密码散列，没有撤销原token；前端退出也只是清除本机token。

建议：每次鉴权校验当前账号状态/角色，使用凭证版本或密码变更时间使旧token失效；再补登录/找回的失败次数限制。不要把“登录时检查禁用”当成“所有请求都会检查禁用”。

证据：`config/AuthInterceptor.java:34`；`config/JwtUtil.java:30`；`controller/UserController.java:63`；`service/AuthService.java:52`。

## P1：补真正缺失的学生业务

### 5. 邮箱注册/绑定、找回密码尚未闭环

注册页面传入email，但RegisterRequest没有email、AuthService不保存email。数据库和SysUser虽有email/phone字段，尚无绑定与验证接口。忘记密码页handleSend只切换到“已发送”步骤；没有发送、重置token、过期/单次使用验证及新密码提交接口。

建议：先统一注册字段并存储经过验证的邮箱，补绑定验证和找回流程；没有邮件配置时明确不可发送，不显示成功。已有当前密码验证的“修改密码”是真实实现，应保留。

证据：`前端/src/views/Register.vue:183`；`dto/RegisterRequest.java:15`；`service/AuthService.java:37`；`前端/src/views/ForgotPassword.vue:111`。

### 6. 专项刷题后端、进度同步与题单缺失

learningResources.createTrainingSession用Date.now创建本地ID，所有配置只有一份固定demoQuestion，题单/进度/代码写到localStorage；服务端没有学生题单、练习会话、回答提交和完成记录API。选择10/20/30/50题并不对应真实题目获取。

固定键offerpilot.learning.session也不按用户区分，同浏览器换账号可能读取上一账号本地练习。需要后端按userId归属；过渡阶段也要隔离本地缓存。

建议：先提供知识问答题单、练习会话、题目实例、提交/跳题、云端进度和完成结果，再接首页/记录。复用已有岗位/标签题库，不另造重复岗位字典。

证据：`前端/src/services/learningResources.js:3,133,145,171`；当前controller目录及本地表清单。

### 7. 编程提交与真实判题后端缺失

runLocalDemo只用正则检查HashMap等文本，成功时直接把expected作为output，未执行用户代码；收藏、提示、复杂度追问和复盘主要是组件状态/固定文本。TrainingSession中的语音控件传的是本地Date.now会话ID，ASR服务却要求真实interview_session归属且ONGOING，二者未建立关联。

建议：有真实执行能力前维持明确的演示标记，不能计入教学有效次数。真正接入时要有编程提交、用例、受限执行、结果与资源限制，并统一编程会话的转写权限。该阶段不能通过在主业务服务里直接执行用户代码实现。

证据：`learningResources.js:158`；`前端/src/views/TrainingSession.vue`；`service/SpeechRecognitionService.java:42`。

### 8. 收藏、错题本、练习历史缺少后端

页面计数固定为12/8/5；收藏只修改favorite布尔值，入口为锚点。缺少用户题目收藏、错误记录、复练/掌握状态和真实计数，不会跨设备同步。

建议：收藏用用户+题目唯一关系；错题由实际提交结果生成；复练结果更新掌握状态。依赖第6项的提交记录，不先单独做一份无真实来源的“错题表”。

证据：`learningResources.js:117`；`TrainingSession.vue`；`LearningResources.vue:111`。

### 9. 首页、历史、报告和教学任务的统计口径未全部统一

教学任务已按READY且valid计算完成；学生首页completedCount仍只数FINISHED会话，生成失败/未完整作答的结束会话也会计入。记录DTO缺少教学assignment/task来源、有效性、报告阶段与删除能力，学生无法从普通历史中可靠区分自主与教学。

首页/历史主要用旧totalScore，报告详情已可用overallMatchScore；两者分别为基础分与目标匹配度，不应混为同一趋势。DashboardService趋势平均排除所有score>0之外的值，把真实零分也排除。StatsService与DashboardService没有按岗位、评分配置/版本限定可比数据。

建议：先定义会话结束、有效完成、报告就绪、教学任务完成四个口径；明确scoreType和单位，缺失值用null、保留零分；记录返回来源和canDelete；之后才做可比成长曲线。

证据：`service/DashboardService.java:80,120,143`；`StatsService.java:42`；`InterviewRecordService.java:73`；`TeachingService.progress`。

### 10. 面试材料/评分版本快照和参数验证不完整

后续自主题目仍读取resumeService.getMine，简历内容一份覆盖保存；中途修改资料可能改变后续题目。评分使用当前岗位配置，历史复算缺少完整岗位/简历/评分模型版本快照。已有权重快照只解决部分配置问题。

StartInterviewRequest.modulePreferences及其code/rank/level没有嵌套Valid/范围/去重/字典检查；异常排名可产生零权重，重复code可能被覆盖。回答只有NotBlank，没有后端字数上限。自主开始只验证岗位存在，不验证启用状态。

建议：开始时冻结训练材料和默认/自选评分配置；校验模块字典、数量、唯一性、等级/排名与权重；增加回答长度上限；创建新训练拒绝停用岗位，历史仍可读。

证据：`dto/StartInterviewRequest.java:31`；`dto/AnswerRequest.java:16`；`InterviewFlowService.java:126,309`；`ResumeService.saveAndAnalyze`；`entity/InterviewReport.java`。

## P2：补使用完整性和接口质量

- **AI准备聊天跨设备恢复**：已有SSE和降级，不是缺AI接口；但聊天和阶段主要在前端，后端刻意不写库。若要“返回继续上次准备”，补草稿与会话历史即可，不必把准备聊天误当正式训练报告。
- **简历文件管理与错误反馈**：已有上传、二进制保存、解析和本人查询；缺少原文件下载/删除/历史版本。多种可预期上传错误用RuntimeException，被统一转成系统繁忙；应改成明确业务错误并补实际文件类型识别。扫描件OCR是额外能力，不应视为当前文字简历必需功能。
- **历史服务端分页/筛选**：myRecords取全部记录并逐条查询岗位；需分页、日期/岗位/来源/报告状态筛选与批量岗位读取。已有追问记录分页不能代表普通面试历史已经分页。
- **消息中心补充**：已有本人最近200条、单条已读和业务跳转；缺未读计数、分页、全部已读，暂时无法可靠显示全局未读数量。
- **学习计划完成追踪**：报告已有真实提升建议接口，尚无建议→指定练习→复测→进步确认的持久化执行链；应在专项训练接入后补，避免重复新建一套任务系统。
- **回归覆盖**：现有12项后端测试与教学闭环脚本不能覆盖自主会话并发、体验题绑定、旧token失效、删除新评分关联、密码重置、刷题/判题等缺口。补功能时围绕这些失败场景测试。

## 建议实施顺序

1. 修自主面试问题实例绑定、会话恢复/幂等/时限，统一报告失败与恢复；补事务删除与新评分清理、旧凭证失效。
2. 保存和验证邮箱，接真正找回密码；修评分参数和首页/记录/报告口径，冻结必要快照。
3. 落地知识问答专项训练、云端进度、收藏/错题，把真实结果接入首页与历史。
4. 接受限编程判题和教师编程任务；随后补学习计划、可比成长、聊天草稿和消息完整性。

每阶段完成一条可验收闭环后再推进下一阶段。本次未对现有业务做修复；以上数据库数字是检查时的本地快照。
