-- ============================================================
-- migration_v4b_job_profiles.sql
-- 9 个新开岗位的能力画像对齐（配合 migration_v4_job_banks.sql）
--
-- 背景：job_position.abilities + keywords 是「岗位 → 题目」的唯一桥梁，靠
-- InterviewFlowService.matchTagIds 做双向子串匹配。原来的画像里大量能力项
-- 在 skill_tag 里没有对应标签（如「交互设计」「行业分析」「压测工具」），
-- 等于这些维度永远抽不到题；另有几个宽泛词会误伤（见下）。
--
-- 修改原则：
--   1. abilities 每一项都能匹配到 ≥1 个 skill_tag
--   2. 剔除会命中无关标签的宽泛词：
--        Java         ⊂ 命中标签 Java → 会把 24 道 Java 语言题拉进 Android 岗位
--        Kotlin/Java  同上
--        性能优化/性能  命中「性能」标签 → 所有前端岗都会拿到它
--        监控          ⊂ 崩溃监控与线上诊断 → 会把 Android 题拉进性能测试岗
--        BI            ⊂ RabbitMQ → 会把消息队列题拉进数据分析岗
--        CI/CD         ⊂ 标签 C → 会把 C 语言题拉进自动化测试岗
--        TPS           ⊂ HTTP/HTTPS → 会把 HTTP 题拉进性能测试岗
--        RAG/LLM       保留，它们分别是「RAG检索增强」「大语言模型」的子串，属正常匹配
--   3. 同步更新 migration_v3_jobs.sql，保证重建数据库结果一致
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 前端与客户端开发 ----------
UPDATE job_position SET
  abilities = '["Kotlin","Jetpack Compose","Android架构","协程","Retrofit","四大组件","Android线程与消息机制","Android网络与存储","启动优化与卡顿","崩溃监控与线上诊断","Android权限模型","内存泄漏","跨进程通信","ViewModel"]',
  keywords  = '["Kotlin","Jetpack","Compose","ViewModel","LiveData","协程","Retrofit","Gradle","内存泄漏","MVVM","Activity","Handler","ANR","Room","DataStore","OkHttp"]'
WHERE code = 'FE-ANDROID';

-- ---------- 全栈开发 ----------
UPDATE job_position SET
  abilities = '["Python","Django","Flask","FastAPI","Django ORM","asyncio","Celery","部署运维","HTMX","pandas","Vue","SQL"]',
  keywords  = '["Python","Django","Flask","FastAPI","Celery","asyncio","HTMX","Vue","PostgreSQL","Redis","Nginx","Docker","RESTful","pandas"]'
WHERE code = 'FS-PY';

-- ---------- 算法与人工智能 ----------
UPDATE job_position SET
  abilities = '["NLP基础","Transformer","注意力机制","大语言模型","LLM微调","LoRA","RLHF","RAG检索增强","Prompt工程","向量检索","模型评估","PyTorch","HuggingFace"]',
  keywords  = '["NLP","Transformer","注意力机制","BERT","GPT","大语言模型","LoRA","RLHF","RAG","Prompt工程","向量检索","PyTorch","HuggingFace","微调"]'
WHERE code = 'ALG-NLP';

-- ---------- 产品经理 ----------
UPDATE job_position SET
  abilities = '["用户研究","需求分析与PRD","竞品分析","用户画像","增长运营","需求管理与敏捷","项目管理","商业模式与商业化","数据分析方法","A/B测试"]',
  keywords  = '["用户调研","用户研究","需求分析","PRD","竞品分析","用户画像","留存","转化","增长运营","A/B测试","敏捷","需求管理与敏捷","商业模式"]'
WHERE code = 'PM-C';

UPDATE job_position SET
  abilities = '["SaaS与B端产品","B端权限模型","需求分析与PRD","需求管理与敏捷","项目管理","商业模式与商业化","数据分析方法","竞品分析","用户研究"]',
  keywords  = '["B端产品","SaaS","企业服务","工作流","B端权限模型","API设计","私有化","SLA","定制","商业模式","竞品分析"]'
WHERE code = 'PM-B';

-- ---------- 数据分析 ----------
UPDATE job_position SET
  abilities = '["SQL","指标体系","漏斗分析","留存分析","归因分析","数据分析方法","数据可视化与BI","数仓与ETL","业务报告与汇报","用户行为分析"]',
  keywords  = '["SQL","Excel","Tableau","指标体系","漏斗分析","留存分析","归因分析","A/B实验设计","埋点设计","数仓","ETL","业务报告"]'
WHERE code = 'DA-BIZ';

UPDATE job_position SET
  abilities = '["A/B实验设计","埋点设计","用户行为分析","指标体系","漏斗分析","留存分析","数据可视化与BI","数据分析方法","归因分析","用户画像"]',
  keywords  = '["用户行为","A/B实验设计","埋点","留存分析","漏斗分析","归因分析","SQL","Python","指标体系","数据可视化"]'
WHERE code = 'DA-PROD';

-- ---------- 软件测试 ----------
UPDATE job_position SET
  abilities = '["测试用例设计","缺陷管理","接口测试","自动化框架","Selenium","Playwright","pytest","持续测试","质量度量"]',
  keywords  = '["Selenium","Playwright","pytest","接口测试","自动化框架","PO模式","数据驱动","持续测试","缺陷管理","测试用例设计"]'
WHERE code = 'QA-AUTO';

UPDATE job_position SET
  abilities = '["压测方案设计","JMeter","瓶颈分析与调优","容量规划","质量度量","接口测试","测试用例设计"]',
  keywords  = '["JMeter","压测","响应时间","瓶颈分析","调优","容量规划","全链路","接口测试","质量度量"]'
WHERE code = 'QA-PERF';

-- ---------- 校验：以上 9 个岗位必须都更新到 ----------
SELECT code FROM job_position
 WHERE code IN ('FE-ANDROID','FS-PY','ALG-NLP','PM-C','PM-B','DA-BIZ','DA-PROD','QA-AUTO','QA-PERF')
 ORDER BY code;   -- 应返回 9 行
