-- Existing sessions remain on their original flow; only new formal sessions get a stage row.
CREATE TABLE IF NOT EXISTS interview_flow_state (
 session_id BIGINT PRIMARY KEY,
 stage VARCHAR(40) NOT NULL,
 project_questions INT NOT NULL DEFAULT 0
);
ALTER TABLE interview_message MODIFY COLUMN question_type VARCHAR(40) DEFAULT 'SKILL';
