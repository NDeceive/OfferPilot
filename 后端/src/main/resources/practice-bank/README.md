# 专项刷题题库与企业匹配 · 首批交付

2026-10-08。首批增量包为 40 道普通题、20 道 Java 17 编程题、18 道企业情境题，企业为字节跳动、阿里巴巴、腾讯。普通题覆盖 Java 后端与 Web 前端；编程题也可用于 Java/Node 全栈的通用算法练习。

## 实际行为

- 普通题复用 `skill_question`，新增 `practice_question_meta` 保存来源、岗位、专项、企业、审核状态和批次。企业情境不关联共享面试技能标签。
- 编程题保存在 `practice_coding_question`，不再使用 Java 服务内的固定数组。每题有题面、模板、公开示例、代表判题用例与参考解法。
- 组题先按岗位、专项、难度上限和审核状态筛选。综合练习覆盖岗位候选池；其他专项不借用无关知识题。混合题单编程题占 20%–30%；2–6 道短题单固定 1 道编程题。纯编程题单不再强制为 1 题，首批入口提供 5/10/20 题。
- 首批编程题是 Java 17 基础算法题；只匹配综合、项目场景、编程与算法和 Java 核心。其他专项没有真实编程覆盖时入口提供知识问答，接口直接请求不满足的配额会报错。
- 企业情境目标为总题量约 20%，但还需岗位、专项相关。每家企业每个试点岗位现有 3 道情境题，因此 20 题综合练习最多 3 道企业题，其余份额用相关知识题补齐并告知。
- 企业情境专项提供 3 题纯文字练习；不限公司或资料/题目不足时明确报错，不制造企业资料。
- 公司名称及别名映射到稳定 ID；没有资料的公司仍可选择，按岗位与专项组题并显示提示。不把阿里云自动当作阿里巴巴，也不推断企业技术栈、招聘频率或真实考题。
- 新普通题与企业情境采用参考分析和自评要点，不生成自动分数、文化匹配分或通过结论。既有题目的关键词反馈继续保留。
- 练习开始时保存题面、参考答案、来源、企业匹配情况和公开示例快照。题库更新、停用不会改写既有练习。旧练习没有新来源字段时仍可打开。
- 运行仅执行公开示例，正式提交执行最多 12 个代表用例。题面不返回完整判题测试与参考代码；判题用例反馈不公开输入输出。上游 canonical 数据本身是公开数据，不宣称这些是上游私密测试。

## 来源与许可

固定仓库版本、许可证哈希见 `sources/manifest.json`。所有下载文件及许可证保留在 `sources/`。题目逐条记录来源链接、版本、原题标识、改编说明、内容哈希与审核日期。

| 来源 | 许可 | 首批使用方式 |
| --- | --- | --- |
| [Exercism problem-specifications](https://github.com/exercism/problem-specifications) | MIT | 20 个练习的 canonical 测试数据；中文题面和标准输入输出包装由项目改编 |
| [JavaGuide](https://github.com/Snailclimb/JavaGuide) | Apache-2.0 | 参考知识主题，独立措辞编写 25 道普通题与自评要点；不是整篇文章转载 |
| [Front-end Interview Handbook](https://github.com/yangshun/front-end-interview-handbook) | MIT | 参考知识主题，独立措辞编写 15 道普通题与自评要点 |
| [JavaScript Questions](https://github.com/lydiahallie/javascript-questions) | MIT | 已保留来源与许可证，尚未作为本批题目来源 |

不导入牛客、力扣或 HackerRank 的未授权题面、付费资料和第三方转载。MIT 原始许可证与版权声明已保留；Apache-2.0 许可证和来源已保留，改编说明记录在每道题中。

企业依据为以下官方页面的事实摘要与链接，不复制整页材料或品牌资产：

- 字节跳动：[我们的文化](https://jobs.bytedance.com/experienced/m/culture)。使用坦诚清晰、求真务实等公开工作方法。
- 阿里巴巴：[集团介绍](https://www.alibabagroup.com/en-US/about-alibaba)。使用客户第一的公开价值观。
- 腾讯：[Who we are](https://www.tencent.com/who-we-are/)。使用用户价值与协作的公开理念。

企业情境是 OfferPilot 原创改编，明确标识“不代表企业真实面试题”。首批内容由项目审阅与自动检查，不宣称获得企业授权认证或外部专家认证。

## 维护命令

在仓库根目录执行，Python 仅用标准库，无新增 npm/Maven 依赖。

```powershell
python scripts/build-practice-pack.py
python scripts/test_practice_pack.py
python scripts/verify-practice-pack.py
python scripts/import-practice-pack.py          # 默认只校验
python scripts/import-practice-pack.py --apply  # 增量迁移与事务导入
```

导入前必须有当前题目包的参考解法验证报告，报告通过包文件 SHA256 绑定。导入使用 canonical ID 去重、数据库导入锁与事务；重复执行不会重复创建题目，不删除用户记录。失败时数据库连接关闭会回滚本批内容；新建表为幂等增量 DDL。

数据库连接读取 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`MYSQL_BIN`；默认与项目开发库一致，凭据不写入题目包或命令行参数。生产环境应显式设置连接变量。**不要重跑会 DROP TABLE 的 `seed_skill_bank.sql`。**

```powershell
python scripts/import-practice-pack.py --disable-batch pilot-2026-10-v1
python scripts/import-practice-pack.py --disable-batch pilot-2026-10-v1 --apply
```

停用仅影响后续专项刷题选题，保留题目和已有练习。重新导入审核后的包可恢复启用。更新上游资料需重新执行 `fetch-practice-sources.py`、审阅差异、构建验证和导入，不能把定期拉取直接连到学生题单。

## 企业 AI 候选题审核

复用现有 DeepSeekClient。学生不能维护题库，接口仅允许教师/管理员；教师仅能审核自己创建的候选，管理员可审核全部。

1. `POST /api/practice/company-candidates`，JSON：`{"company":"腾讯","jobCode":"BE-JAVA","topic":"项目场景","difficulty":2}`。资料未审核、AI 未配置或返回内容缺少真实 evidence ID 时失败；成功仅写 `PENDING`。
2. `GET /api/practice/company-candidates`，读取最近 100 条可访问候选及资料快照。
3. `POST /api/practice/company-candidates/{id}/review`，通过时显式提交 `{"approved":true,"title":"审阅后的标题","content":"完整题面","referenceAnswer":"参考分析与自评要点"}`；拒绝时提交 `{"approved":false}`。
4. 审核入库事务锁定候选，核对公司与岗位仍有效，写入元数据并记录审核人。重复审核不会再次创建题目。模型原始内容不会未经审阅直接入库。

首期提供维护接口和增量工具，未新增独立教师题库管理页面。企业资料后续也通过审核后的题目包更新。

## 验证与已知范围

- `verification.json`：20 道参考 Java 解法通过 243 个适用 canonical 用例；每题一个常量输出错误实现被真实执行后拒绝。
- 适配明确排除了非题目属性用例（如麦粒总量）、Gigasecond 的输入不可变性 API 断言，以及反转字符串的两个可选字素簇用例；未伪造这些测试结果。异常按题面映射为 `error`，二分未找到映射为 `-1`。
- 正式用例最多 12 个，采用公开示例加分散的代表用例，完整适用 canonical 用例用于离线参考验证。没有证明所有错误算法都能被检测。
- 后端单元检查覆盖筛选、配额、失败请求不提交、自评无分数、隐藏用例反馈、候选权限与依据校验。
- 真实 MySQL 检查覆盖筛选、快照、提交、企业别名、无资料回退和审核入库；测试写入全部回滚。通过 `RUN_PRACTICE_DB_TEST=true` 显式开启。
- 浏览器布局回归覆盖普通→编程→普通、草稿、异常及多种尺寸；`practice-bank-browser.cjs` 使用真实题目包的接口 fixture 检查来源、企业选择和自评刷新，并输出截图到 `output/practice-bank-qa/`。
- 未调用真实 DeepSeek 生成付费候选，未完成真实 Judge0 服务端端到端判题；前者已做失败/权限/依据校验测试，后者已做本地 Java 执行和接口 mock 测试。
- 首批尚未扩到 200/60 或增加 SQL、JavaScript 判题运行时；后续按真实岗位专项缺口扩充。没有发布、部署或修改其他模块的数字人流程。

本地开发数据库已执行 V6 并导入本批。现有运行中的后端需要重新启动才能加载新接口与组题逻辑。
