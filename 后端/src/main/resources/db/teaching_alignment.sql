-- Additive tables: historical scores and teaching records remain untouched.
CREATE TABLE IF NOT EXISTS teaching_score_plan (
 task_id BIGINT PRIMARY KEY, config_json JSON NOT NULL, config_version INT NOT NULL DEFAULT 1,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS interview_training_context (
 session_id BIGINT PRIMARY KEY, context_json JSON NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS interview_score_provenance (
 report_id BIGINT PRIMARY KEY, rule_version VARCHAR(80) NOT NULL, model_version VARCHAR(120) NOT NULL,
 prompt_version VARCHAR(80) NOT NULL, score_source VARCHAR(20) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_assignment_override (
 assignment_id BIGINT PRIMARY KEY, extra_attempts INT NOT NULL DEFAULT 0,
 deadline DATETIME NULL, exempt BOOLEAN NOT NULL DEFAULT FALSE, reason VARCHAR(500) NOT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_change_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, teacher_id BIGINT NOT NULL, task_id BIGINT NOT NULL,
 assignment_id BIGINT NULL, action VARCHAR(40) NOT NULL, detail_json JSON NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;
