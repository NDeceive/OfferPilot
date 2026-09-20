-- ============================================================
-- migration_v4c_word_boundary.sql
-- 配合 InterviewFlowService.matchTagIds 的「拉丁词边界」规则，补齐被该规则
-- 顺带砍掉的真实能力项。
--
-- 背景：原匹配是纯双向子串，短 ASCII 标签会命中一堆无关内容。实测踩到的：
--     标签 C        ⊂ "Spring Cloud" / "css" / "Celery" / "scikit-learn"
--     标签 Go       ⊂ "django" / "MongoDB"
--     标签 Java     ⊂ "JavaScript"      → 前端岗位池里混进 24 道 Java 题
--     标签 Boost    → 反过来 "Boost" ⊂ "XGBoost"，C++ 岗位拿到 XGBoost 题
--     标签 Activity ⊂ "ViT"（a-c-t-i-v-i-t-y 里有 "vit"）
--     标签 SQL      ⊂ "MySQL" / "PostgreSQL"
--  前五类是纯误伤，必须砍；最后一类（SQL/MySQL/PostgreSQL）是**真实能力项**，
--  只是原先靠子串侥幸命中。本文件把这些能力显式写进岗位画像，让匹配回到
--  「画像里写了什么能力，就问什么题」的正轨。
--
-- 同步更新 migration_v3_jobs.sql，保证重建数据库结果一致。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 后端开发：画像里只有「数据库」这类泛词，显式补上 SQL ----------
UPDATE job_position SET
  keywords = '["Java","JVM","并发","Spring","SpringBoot","MyBatis","MySQL","Redis","消息队列","微服务","Docker","SQL"]'
WHERE code = 'BE-JAVA';

UPDATE job_position SET
  keywords = '["Python","Django","Flask","FastAPI","Celery","PostgreSQL","MongoDB","pandas","RESTful","GraphQL","SQL"]'
WHERE code = 'BE-PY';

UPDATE job_position SET
  keywords = '["Go","Goroutine","gRPC","protobuf","Kubernetes","Docker","etcd","Redis","PostgreSQL","Kafka","SQL"]'
WHERE code = 'BE-GO';

-- ---------- 全栈开发 ----------
-- FS-JAVA 关键词只有 "Spring"，词边界后不再命中标签「SpringBoot」，显式补上
UPDATE job_position SET
  keywords = '["Java","Spring","SpringBoot","Vue","React","MySQL","Docker","CI/CD","Nginx","Linux","全栈架构","SQL"]'
WHERE code = 'FS-JAVA';

UPDATE job_position SET
  keywords = '["Node.js","NestJS","React","Next.js","TypeScript","Prisma","PostgreSQL","AWS","Vercel","全栈","SQL"]'
WHERE code = 'FS-NODE';

-- FS-PY 的 SQL 写在 abilities 里，具体数据库要显式列
UPDATE job_position SET
  keywords = '["Python","Django","Flask","FastAPI","Celery","asyncio","HTMX","Vue","PostgreSQL","MySQL","Redis","Nginx","Docker","RESTful","pandas"]'
WHERE code = 'FS-PY';

-- ---------- 算法与人工智能 / 数据分析 ----------
-- 注意：ALG-ML 这里**故意不加** MySQL/PostgreSQL。加过一版，题池从 28 涨到 32，
-- 但 SQL⊂MySQL 断掉后必须显式写数据库名，一写进去就有 16/32 道题变成
-- 「MySQL InnoDB 索引原理」「MyBatis 一级缓存」这类 DBA/ORM 题混进算法岗面试。
-- 算法岗只要 SQL 就够，宁可题池小一点也不要跑题。
UPDATE job_position SET
  keywords = '["机器学习","XGBoost","LightGBM","scikit-learn","特征工程","Python","SQL","A/B测试","模型可解释性"]'
WHERE code = 'ALG-ML';

-- 这 3 个数据分析岗位写 SQL 是日常，把具体数据库显式列出来

UPDATE job_position SET
  keywords = '["SQL","MySQL","PostgreSQL","Excel","Tableau","指标体系","漏斗分析","留存分析","归因分析","A/B实验设计","埋点设计","数仓","ETL","业务报告"]'
WHERE code = 'DA-BIZ';

UPDATE job_position SET
  keywords = '["用户行为","A/B实验设计","埋点","留存分析","漏斗分析","归因分析","SQL","MySQL","PostgreSQL","Python","指标体系","数据可视化"]'
WHERE code = 'DA-PROD';

UPDATE job_position SET
  keywords = '["PowerBI","Tableau","Looker","SQL","MySQL","PostgreSQL","dbt","数仓建模","ETL","数据质量","OLAP","数据看板"]'
WHERE code = 'DA-BI';

-- ---------- 校验：以上 10 个岗位必须都更新到 ----------
SELECT code FROM job_position
 WHERE code IN ('BE-JAVA','BE-PY','BE-GO','FS-JAVA','FS-NODE','FS-PY',
                'ALG-ML','DA-BIZ','DA-PROD','DA-BI')
 ORDER BY code;   -- 应返回 10 行
