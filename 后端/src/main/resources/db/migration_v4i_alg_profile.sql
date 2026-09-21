-- ============================================================
-- migration_v4i_alg_profile.sql
-- ALG-ML 画像跟随标签改名。
--
-- 标签改名本身在 migration_v4_job_banks.sql 的第 2 节执行：
--     经典算法        -> 机器学习模型   （「算法」不再被包含）
--     Python/ML框架   -> ML框架        （去掉裸 Python）
--
-- 原因：matchTagIds 是双向子串匹配，中文不做词边界，所以标签名里只要**包含**
-- 另一个岗位画像里的词，那个岗位就会把这个标签下的题全吸走。实测被吸走过：
--     BE-PY / FS-PY / DA-PROD 的画像里有 Python  -> 吸走 sklearn、模型上线两道题
--     BE-CPP / ALG-REC 的画像里有 算法           -> 会吸走 XGBoost、LightGBM 题
--
-- 改名后岗位画像必须同步，否则 ALG-ML 会因为找不到旧名字而丢掉整个能力维度
-- （画像项匹配不到任何 skill_tag，等于该维度无题可出）。
--
-- 执行顺序：先跑 migration_v4_job_banks.sql（改名 + 补题），再跑本文件。
-- 同步更新：migration_v3_jobs.sql（重建库时的源数据）。
-- ============================================================

SET NAMES utf8mb4;

UPDATE job_position SET
  abilities = '["数学基础","特征工程","机器学习模型","模型评估","ML框架","工程部署"]'
WHERE code = 'ALG-ML';

-- ---------- 追加：第 1232 题去掉「模型评估」标签 ----------
-- 该题问的是 XGBoost 调参（max_depth / min_child_weight 怎么调），主标签就是 XGBoost，
-- 但挂上「模型评估」后，ALG-NLP 会因为画像里有这个标签而抽到一道纯 XGBoost 题——
-- 调参细节对 NLP 岗没有价值。ALG-ML 本身直接命中 XGBoost，去掉不影响它。
-- 生成器的关联语句是 INSERT IGNORE（只增不删），所以删关联必须显式 DELETE。
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 1232 AND t.name = '模型评估';

-- ---------- 追加：第 1111 题去掉「A/B测试」标签 ----------
-- 该题问的是「数据和你的产品直觉冲突时听谁的」，讲决策框架，不涉及实验设计、
-- 分流或显著性，挂「A/B测试」属于挂错。而 ALG-ML 画像关键词里有 A/B测试，
-- 于是它会抽到这道 PM 味的问题（生成器的关联是 INSERT IGNORE，只增不删）。
-- 去掉后 PM-C / PM-B 与数据分析岗仍通过「数据分析方法」拿得到这题。
DELETE r FROM skill_question_tag_rel r
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE r.question_id = 1111 AND t.name = 'A/B测试';

-- ---------- 校验 ----------
-- 1) 两个新名字都存在，旧名字都已消失（应只返回 2 行）
SELECT id, name, category FROM skill_tag
 WHERE name IN ('机器学习模型', 'ML框架', '经典算法', 'Python/ML框架');

-- 2) ALG-ML 画像已同步
SELECT code, abilities FROM job_position WHERE code = 'ALG-ML';

-- 3) 第 1232 题应只剩 XGBoost，第 1111 题应只剩 数据分析方法
SELECT q.id, GROUP_CONCAT(t.name SEPARATOR ' + ') AS 标签
  FROM skill_question q
  JOIN skill_question_tag_rel r ON r.question_id = q.id
  JOIN skill_tag t ON t.id = r.tag_id
 WHERE q.id IN (1111, 1232) GROUP BY q.id ORDER BY q.id;
