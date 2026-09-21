-- ============================================================
-- migration_v4f_substr_fix.sql
-- 修掉两处「中文子串扩张」跨域污染（与 v4c 的拉丁词边界是同一类问题的中文版）。
--
-- 中文匹配走的是双向子串（为了 `性能` 能命中 `性能优化与卡顿`），
-- 于是比能力词长的标签会把能力词吞进来：
--
--   ① FS-JAVA 能力词 `项目管理` ⊂ 标签 `项目管理与敏捷`（6 道题）
--      整场 Java 全栈面试会问到——
--        「需求优先级怎么排？KANO 模型和 RICE 分别适合什么情况？」
--        「一份合格的 PRD 应该包含哪些内容？」
--        「敏捷开发中产品经理的职责是什么？」
--      这些是 PM 岗的题。修法：把 PM 标签改名成 `需求管理与敏捷`
--      （6 道题本来就是需求/流程方向，这个名字更准），标签名不再包含 `项目管理`。
--      FS-JAVA 想要的 `项目管理`（第 174 题：敏捷 vs 瀑布，全栈团队更适合哪种）
--      是另一个独立标签，改名后照常命中。
--
--   ② BE-PY / FS-JAVA 能力词 `数据库` ⊂ 标签 `向量数据库`（1 道题）
--      第 117 题「向量数据库的基本工作原理」是 AI 方向的题，却进了普通后端岗。
--      修法：`向量检索` 标签已存在且语义相同，把这题并过去，删掉 `向量数据库`，
--      FS-AI 画像改指 `向量检索`。
--
-- 同步更新 gen_job_banks.py / migration_v3_jobs.sql / migration_v4b_job_profiles.sql。
-- ============================================================

SET NAMES utf8mb4;

-- ---------- ① 项目管理与敏捷 → 需求管理与敏捷 ----------
-- 关联表按 tag_id 引用，改名后自动跟随，只需改标签名和两个岗位画像。
UPDATE skill_tag SET name = '需求管理与敏捷' WHERE name = '项目管理与敏捷' AND id = 222;

UPDATE job_position SET
  abilities = '["用户研究","需求分析与PRD","竞品分析","用户画像","增长运营","需求管理与敏捷","商业模式与商业化","数据分析方法","A/B测试"]',
  keywords  = '["用户调研","用户研究","需求分析","PRD","竞品分析","用户画像","留存","转化","增长运营","A/B测试","敏捷","需求管理与敏捷","商业模式"]'
WHERE code = 'PM-C';

UPDATE job_position SET
  abilities = '["SaaS与B端产品","B端权限模型","需求分析与PRD","需求管理与敏捷","商业模式与商业化","数据分析方法","竞品分析","用户研究","指标体系"]'
WHERE code = 'PM-B';

-- ---------- ② 向量数据库 → 向量检索 ----------
-- 第 117 题先挪到 `向量检索`，再删掉孤立的 `向量数据库` 标签。
INSERT IGNORE INTO skill_question_tag_rel (question_id, tag_id)
  SELECT 117, id FROM skill_tag WHERE name = '向量检索';

DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE t.name = '向量数据库';

DELETE FROM skill_tag WHERE name = '向量数据库';

UPDATE job_position SET
  abilities = '["LLM集成","RAG管道","后端API","前端开发","Prompt工程","向量检索"]'
WHERE code = 'FS-AI';

-- ---------- 校验 ----------
SELECT '应是 0' AS 说明, COUNT(*) AS 残留_项目管理与敏捷
  FROM skill_tag WHERE name = '项目管理与敏捷';
SELECT '应是 0' AS 说明, COUNT(*) AS 残留_向量数据库
  FROM (SELECT 1 FROM skill_tag WHERE name = '向量数据库') x;
SELECT q.id, q.content, GROUP_CONCAT(t.name SEPARATOR ' + ') AS 标签
  FROM skill_question q
  JOIN skill_question_tag_rel r ON r.question_id = q.id
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE q.id IN (117, 174, 1089)
 GROUP BY q.id, q.content;
