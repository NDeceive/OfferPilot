-- ============================================================
-- migration_v4e_retag.sql
-- 重建本次新增题目（id 1001-1226）的标签关联，使其与 gen_job_banks.py 完全一致。
--
-- 背景：岗位 → 题目是纯标签匹配。B端产品题原先除了挂「SaaS与B端产品」，
-- 还顺手挂了一堆 PM 通用标签（需求分析与PRD / 商业模式与商业化 / 需求管理与敏捷 /
-- 数据分析方法 / 竞品分析 / 用户画像）。PM-C（C端产品经理）的画像里也有这些通用标签，
-- 于是 C端面试会问到「客户要求私有化部署」「多租户数据隔离」这类纯 B端题目
-- （实测 PM-C 一场 8 题里有 8 道题的主标签是 SaaS与B端产品）。
--
-- PM-B 画像本来就有「SaaS与B端产品」「B端权限模型」，靠主标签就能覆盖这些题，
-- 那些通用标签对 PM-B 是冗余的、对 PM-C 是有害的。已在生成器里改成只挂 B端标签。
--
-- 生成器的关联语句是 INSERT IGNORE（只增不删），所以这里先清空这些题的关联，
-- 再由 migration_v4_job_banks.sql 按生成器内容重建。
-- 只动 id 1001-1226（本次新增），不碰原有 AI 题库。
-- ============================================================

SET NAMES utf8mb4;

DELETE FROM skill_question_tag_rel WHERE question_id BETWEEN 1001 AND 1226;

SELECT COUNT(*) AS `清空后应剩` FROM skill_question_tag_rel WHERE question_id BETWEEN 1001 AND 1226;
