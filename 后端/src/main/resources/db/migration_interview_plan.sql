-- New autonomous sessions opt into question-led completion. Existing sessions are not rewritten.
CREATE TABLE IF NOT EXISTS interview_session_plan (
 session_id BIGINT PRIMARY KEY,
 professional_question_ids TEXT NOT NULL,
 project_target INT NOT NULL DEFAULT 2,
 estimated_duration_seconds INT NOT NULL,
 completed_core INT NOT NULL DEFAULT 0,
 total_core INT NOT NULL,
 hard_limit_seconds INT NOT NULL,
 end_reason VARCHAR(40) NULL
);
