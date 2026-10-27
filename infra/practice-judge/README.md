# 专项刷题判题环境与迁移

本次选择 Judge0，复用现有适配层；不引入另一个 OJ 的用户、题库或前端。官方仓库 https://github.com/judge0/judge0 ，API https://ce.judge0.com/ 。Judge0 为 GPLv3，1.13.1 修复官方公布的 1.13.0 及以下三项关键漏洞。当前官方 release 仍为 1.13.1；这不等于所有依赖都已完成安全审计。部署前需重新检查官方公告和镜像扫描。

备选 https://github.com/criyle/go-judge （MIT，执行沙箱/API，需要新的语言编译流程适配）。https://github.com/QingdaoU/OnlineJudge （MIT，完整平台）仅作流程参考，未复制其代码。Temurin JDK https://github.com/adoptium/temurin17-binaries ，GPLv2 with Classpath Exception；固定 17.0.20.1+1，发布附件 SHA256 已从官方校验文件核对。第三方源码和镜像分发应保留对应许可证与源码提供说明。

## 本机状态

本机未找到 Docker，WSL 未安装可用发行版，因此没有真实部署或验证 Judge0。提交的 Compose/Dockerfile 是待 Linux 联调的测试部署配置，不能声称编程题已能在线判题。不要自动安装 WSL、修改 GRUB 或启动特权服务。使用独立 Linux 测试 VM；Judge0 upstream 要求的宿主配置见官方 1.13.1 release。需要更改宿主系统时由负责人确认。

## 数据库迁移

先执行 V5、V6，再执行 `后端/src/main/resources/db/migration_v7_practice_workflow.sql`。V7 只新增任务、提交明细、结算快照及复习状态四表，不重写历史数据。本地 V7 已执行。备份数据库后迁移；回退优先回退应用，保留新表及记录，不用 DROP 清理用户数据。

## Linux 测试部署

1. `python scripts/prepare-practice-judge.py --prepare` 下载校验过的 x64 JDK，生成仅本机保存的密码与认证 token；不启动服务。不要提交 judge0.conf。
2. 在独立 Linux VM 的 `infra/practice-judge` 中，验证 `docker manifest inspect judge0/judge0:1.13.1`，执行 `docker compose build`。记录实际镜像 digest，若标签不存在或镜像安全扫描未通过则停止，不替换为 latest。
3. 执行 `docker compose up -d db redis`，确认服务就绪，再执行 `docker compose up -d server workers`。官方服务启动器初始化 Judge0 专用数据库。
4. 执行 `docker compose exec server bash -lc 'RAILS_ENV=production bundle exec rails runner register-java.rb'`，记录返回的语言 ID。检查 workers 也包含 `/usr/local/openjdk17`。
5. 后端环境设置 `JUDGE_ENABLED=false`、`JUDGE_BASE_URL=http://127.0.0.1:2358`、`JUDGE_AUTH_TOKEN=<本机 AUTHN_TOKEN>`、`JUDGE_LANGUAGE_JAVA=<实际 ID>`。Windows 开发机可使用负责人配置的 SSH 隧道访问 VM 的 loopback，不能把端口开放到公网。
6. 执行 `python scripts/check-practice-judge.py`。它真实编译并运行版本程序，核验元数据和实际 Java 版本，并写出不含密钥的 verification.json。若 JVM 无法在配置资源限制内启动，先在隔离测试环境验证并调整统一题目资源配置，不能将故障标为学生错误。
7. 完成正确解、错误解、编译错误、运行异常、超时、大输出和边界用例验收后，才设置 `JUDGE_ENABLED=true` 并重启本地后端。未通过版本核验时前端仍显示环境未验证。

Compose 仅绑定 loopback，Judge 专用数据库/Redis 位于内部网络。privileged 是 upstream sandbox 的运行条件，必须放在独立 VM，不能与业务数据库和用户凭据共享宿主。容器资源和内部网络限制需在实际 Linux 环境验收。没有自动部署生产环境。

## 业务限制与统计

任务持久化、用户+requestKey 幂等、原题关联、终态保护、15 分钟排队/执行失联恢复。每个应用实例一个工作线程，单用户最多三个待处理任务；暂不引入外部队列。多实例提高并发前需增加全局配额。

执行器目前只公开 QUEUED/RUNNING 可观测阶段，不制造 COMPILING。Judge0 的通用运行错误不能可靠区分内存和输出限制时保留 RUNTIME_ERROR，不猜测。输出比较支持 EXACT、LINE_ENDING、TOKENS；其他规则明确拒绝，不自动降级。默认 LINE_ENDING 保留行内空白，仅统一 CRLF、行尾空白和最后一个换行。题目快照记录规则版本，专用/浮点校验器须有实际题目与测试后再启用。

普通题无已审核的客观规则时保留自评和手动待复习，不新增未经校准的 AI 打分。OBJECTIVE 题支持审核后的 correctAnswer 精确判定，标准答案不发给未提交的前端。历史关键词评分仅作原评价记录，不参与客观正确率。

结算以最新有效正式提交为依据，自评不计正确率；系统故障不计答错。结束可进入 CLOSING，任务全部终态后读取报告生成 FINISHED 快照。已结束题单只读。复练复制原快照与题序，不修改旧提交；通过复练可解决原待复习条目。草稿仍为按用户隔离的本机存储，不声称云同步。

## 本次修改与本地启动

主要修改文件：
- 前端 TrainingSession.vue：稳定工作台、结果区首次展开、异步任务恢复、只读历史、结束进入报告。
- 前端 PracticeResult.vue / MonacoCodeEditor.vue：编译、运行、用例展示，日志复制与真实行号定位，编辑器尺寸和只读支持。
- 前端 PracticeReport.vue / PracticeLibrary.vue / learningResources.js / router：结算、历史、收藏、手动复习及原题序复练。
- 后端 PracticeWorkflowService.java / PracticeController.java：持久化任务、幂等、权限、独立工作线程、15 分钟失联恢复、分页历史和冻结结算。
- 后端 PracticeService.java / PracticeJudging.java / Judge0Client.java / JudgeProperties.java：确定性客观题、自评兼容、输出规则、隐藏数据过滤、真实运行环境校验与错误分类。
- migration_v7_practice_workflow.sql：新增四张业务表；题目与执行规则版本写入已有快照与任务 JSON。
- infra/practice-judge 与 prepare/check-practice-judge.py：隔离测试环境配置、密码生成、版本验证与回退说明。

在已配置的本地数据库执行 V7 后，后端目录运行 `.\mvnw.cmd spring-boot:run`，前端目录运行 `npm run dev`。沿用仓库现有环境配置和启动脚本；没有 Judge0 时保持 JUDGE_ENABLED=false，文字提交、草稿、历史和报告仍可使用，代码可编辑但不会伪造判题结果。

## 验证记录（2026-10-09）

- 前端单元测试 19 项通过；Vite 构建通过。保留已有 Monaco / face-api 打包体积和浏览器 fs 外置提示，本次没有扩大到无关模块优化。
- 后端全量测试通过后，对最终修改的练习模块重新执行相关测试；练习/Judge 相关 28 项通过，包含 8 项真实 MySQL 用例。数据库用例事务回滚，不保留测试题单。
- MySQL 用例中的执行器为 mock，不代表真实编译服务：验证幂等、系统故障与不可用的区别、排队失联、并发任务、CLOSING→FINISHED、冻结统计、客观/自评、用户隔离与复练解决原错题。
- `node scripts/check-practice-browser.cjs`：固定布局、长内容、草稿/滚动恢复、配额保存失败、损坏缓存、异步切题、键盘调整、目录焦点、旧练习、空题单和加载失败、手机软键盘高度近似。
- `node scripts/practice-workflow-browser.cjs`：首次 30% 展开、用户收起偏好、普通/代码穿插、编译日志、刷新与查询失败恢复、系统故障不提交、结算处理中、报告和历史只读；1440×900、1280×720、820px 平板、390px 手机。
- `node scripts/practice-library-browser.cjs` 与 `node scripts/practice-bank-browser.cjs`：分页、收藏取消、错误重试、手机入口、企业匹配资料及普通自评。
- `python scripts/verify-practice-pack.py`：20 道编程题、243 个参考用例通过；每题常量输出与格式错误解均被拒绝。使用本机 JDK 21 的 `--release 17`，这是可信题库参考代码校验，不是 Java 17 沙箱验证。比较规则已与正式 LINE_ENDING 一致。
- 所有浏览器截图使用模拟 API，分别输出 output/practice-qa、output/practice-workflow-qa、output/practice-library-qa、output/practice-bank-qa。verification.json 标明模拟与真实执行器区别。
- 模拟面试共享组件及数字人/语音实现未因本次练习改造修改；项目构建与现有相关测试通过，未执行数字人服务的现场音视频联调。

## 尚未通过真实环境验证的项目

真实 Judge0 启动、镜像构建、安全扫描、Java 17 编译运行、资源限制、运行异常、超时、超内存和大输出的真实执行验收尚未完成。需要提供独立 Linux 测试 VM；若新增机器或调整宿主系统，先按用户要求获取对应授权。不把 API mock、状态映射测试或本机参考程序执行当作这部分验收。

目前只有 Java 17 适配；实际支持语言必须以实例版本探测通过后的接口为准。没有开放自定义输入、浮点/专用校验器、自动 AI 评分或云端草稿。缺少已审核评价规则的普通题保留参考分析与自评。发生执行器故障可重试；失联任务 15 分钟后归为系统故障，不计学生错误。回退时关闭判题开关并回退应用，保留业务新增表与用户历史记录。
