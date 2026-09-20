-- ============================================================
-- migration_v4d_tag_fix.sql
-- 修正一处跨域挂标：测试题「一个功能拿到手，你怎么开始设计测试？」被同时挂上了
-- 「需求分析与PRD」，于是这道题会出现在产品经理岗位的面试里
-- （实测 PM-C 第 5 题被问「你怎么设计测试」）。
--
-- 该题只应属于测试域，已同步修正 gen_job_banks.py 的数据源。
-- 生成器对关联表用的是 INSERT IGNORE（只增不删），所以这里要显式删除旧关联。
-- ============================================================

SET NAMES utf8mb4;

DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag    t ON t.id = r.tag_id
  JOIN skill_question q ON q.id = r.question_id
 WHERE t.name = '需求分析与PRD'
   AND q.content LIKE '一个功能拿到手%';

-- 校验：该题现在只应剩「测试用例设计」一个标签
SELECT q.id, GROUP_CONCAT(t.name SEPARATOR ' + ') AS 剩余标签
  FROM skill_question q
  JOIN skill_question_tag_rel r ON r.question_id = q.id
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE q.content LIKE '一个功能拿到手%'
 GROUP BY q.id;
