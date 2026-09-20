-- ============================================================
-- migration_v4h_tag_precision.sql
-- 收紧几个「挂得太宽」的标签，让岗位只拿到自己域内的题。
--
-- 判定方法：对每道题取其**主标签**（rel.id 最小的那个，也就是报告页显示的能力标签），
-- 再看哪些岗位的题池里有它、但该岗位的命中标签里没有它的主标签 ——
-- 这些题就是搭着次要标签混进来的。逐条核对后，下面几条是明确的挂错：
--
--   ① 第 1032 题「Python 的深拷贝和浅拷贝」挂着 `数据分析方法`
--      → PM-C / PM-B / DA-BIZ / DA-PROD 四个岗位都会抽到一道 Python 题
--        （实测 PM-B 第 2 题被问「copy.deepcopy 有哪些坑」）。这题与数据分析无关。
--
--   ② 第 1033 题「Python 的内存管理和垃圾回收」挂着 `内存泄漏`
--      → FE-ANDROID 画像里有 `内存泄漏`，Android 面试抽到一道纯 Python 题
--        （实测 FE-ANDROID 第 6 题）。Android 的内存泄漏另有第 1010 题。
--
--   ③ PM-B 画像里写了 `指标体系`
--      → `指标体系` 名下的题绝大多数是数据分析域的（数仓分层 ODS/DWD/DWS/ADS、
--        取数 SQL、BI 看板设计、留存率 SQL），共 12 道挤进 B端产品经理的题池。
--        B端 PM 画像保留 `数据分析方法` 足够覆盖指标相关的能力。
--
--   ④⑤⑥ 三道老题的次要标签挂错，导致数据分析/算法岗抽到 Java、ORM 题：
--      第 1  题「Java 的 == 和 equals」       挂着 `MySQL`
--      第 13 题「MyBatis 的 #{} 和 ${}」      挂着 `SQL`
--      第 14 题「MyBatis 一级/二级缓存」      挂着 `Redis`
--      它们各自的主标签（Java / MyBatis）不受影响，BE-JAVA 照常抽得到。
--
-- 同步更新 gen_job_banks.py（① ②）与 migration_v3_jobs.sql / v4b（③）。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- ①② 新题挂错的次要标签 ----------
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 1032 AND t.name = '数据分析方法';

DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 1033 AND t.name = '内存泄漏';

-- ---------- ③ PM-B 去掉 `指标体系` ----------
UPDATE job_position SET
  abilities = '["SaaS与B端产品","B端权限模型","需求分析与PRD","需求管理与敏捷","项目管理","商业模式与商业化","数据分析方法","竞品分析","用户研究"]',
  keywords  = '["B端产品","SaaS","企业服务","工作流","B端权限模型","API设计","私有化","SLA","定制","商业模式","竞品分析"]'
WHERE code = 'PM-B';

-- ---------- ④⑤⑥ 老题的次要标签 ----------
-- 第 1 题是 Java 语言题，与 MySQL 无关
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id IN (1) AND t.name = 'MySQL';

-- 第 13 题是 ORM 参数绑定/注入题，第 14 题是 ORM 缓存题，都不是 SQL 本身
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 13 AND t.name = 'SQL';

-- 第 14 题讲的是 MyBatis 一二级缓存（进程内），和 Redis 无关
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 14 AND t.name = 'Redis';

-- ---------- ⑦ 第 129 题：JavaScript 的 async/await 挂成了 `异步编程` ----------
-- `异步编程` 是个泛词，Python 后端岗（BE-PY / FS-PY 画像里都有 `异步编程`）
-- 会抽到一道纯 JavaScript 题（实测 BE-PY 第 1 题）。它的正确归属是前端，
-- 而且 `JavaScript` 标签已存在，直接改过去。
INSERT IGNORE INTO skill_question_tag_rel (question_id, tag_id)
  SELECT 129, id FROM skill_tag WHERE name = 'JavaScript';

DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 129 AND t.name = '异步编程';

-- ---------- 校验 ----------
SELECT q.id, GROUP_CONCAT(t.name SEPARATOR ' + ') AS 剩余标签
  FROM skill_question q
  JOIN skill_question_tag_rel r ON r.question_id = q.id
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE q.id IN (1, 13, 14, 129, 1032, 1033)
 GROUP BY q.id ORDER BY q.id;

SELECT code, abilities FROM job_position WHERE code = 'PM-B';
