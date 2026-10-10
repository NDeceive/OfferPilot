-- Read-only preflight for a chosen TEST database. Does not create or change objects.
SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;

SELECT expected.table_name,
       IF(t.table_name IS NULL, 'MISSING', 'PRESENT') AS status
FROM (
  SELECT 'teaching_class' AS table_name UNION ALL SELECT 'teaching_member'
  UNION ALL SELECT 'teaching_task' UNION ALL SELECT 'teaching_assignment'
  UNION ALL SELECT 'teaching_attempt' UNION ALL SELECT 'teaching_review'
  UNION ALL SELECT 'teaching_message' UNION ALL SELECT 'teaching_score_plan'
  UNION ALL SELECT 'interview_training_context'
  UNION ALL SELECT 'interview_score_provenance'
  UNION ALL SELECT 'teaching_assignment_override'
  UNION ALL SELECT 'teaching_change_log'
) expected
LEFT JOIN information_schema.tables t
  ON t.table_schema = DATABASE() AND t.table_name = expected.table_name
ORDER BY expected.table_name;

SELECT c.table_name, c.column_name, c.column_type, c.is_nullable,
       c.character_set_name, c.collation_name
FROM information_schema.columns c
WHERE c.table_schema = DATABASE()
  AND c.table_name IN ('teaching_class','teaching_member','teaching_task',
       'teaching_assignment','teaching_attempt','teaching_review',
       'teaching_message','teaching_score_plan','interview_training_context',
       'interview_score_provenance','teaching_assignment_override',
       'teaching_change_log')
ORDER BY c.table_name, c.ordinal_position;

SELECT s.table_name, s.index_name, s.non_unique, s.seq_in_index, s.column_name
FROM information_schema.statistics s
WHERE s.table_schema = DATABASE()
  AND s.table_name IN ('teaching_class','teaching_member','teaching_task',
       'teaching_assignment','teaching_attempt','teaching_review','teaching_message')
ORDER BY s.table_name, s.index_name, s.seq_in_index;

-- Any rows below signal historical duplicates or a missing expected unique index.
-- These scans never delete, merge, or modify historical data.
SELECT 'teaching_member(class_id,student_id)' AS constraint_name,
       class_id AS first_id, student_id AS second_id, COUNT(*) AS duplicate_count
FROM teaching_member GROUP BY class_id, student_id HAVING COUNT(*) > 1;
SELECT 'teaching_assignment(task_id,student_id)' AS constraint_name,
       task_id AS first_id, student_id AS second_id, COUNT(*) AS duplicate_count
FROM teaching_assignment GROUP BY task_id, student_id HAVING COUNT(*) > 1;
SELECT 'teaching_attempt(session_id)' AS constraint_name,
       session_id, COUNT(*) AS duplicate_count
FROM teaching_attempt GROUP BY session_id HAVING COUNT(*) > 1;
