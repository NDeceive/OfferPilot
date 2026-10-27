-- Apply after V5/V6. Additive only; no historical records are changed.
USE zhimian;
CREATE TABLE IF NOT EXISTS practice_judge_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL, session_id BIGINT NOT NULL, session_question_id BIGINT NOT NULL,
 request_key VARCHAR(80) NOT NULL, operation VARCHAR(10) NOT NULL,
 source_code MEDIUMTEXT NOT NULL, language VARCHAR(30) NOT NULL,
 status VARCHAR(40) NOT NULL DEFAULT 'QUEUED', result_json MEDIUMTEXT NULL,
 started_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at DATETIME NULL,
 UNIQUE KEY uk_task_request(user_id,request_key),
 KEY idx_task_queue(status,id), KEY idx_task_session(session_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_submission_detail (
 submission_id BIGINT PRIMARY KEY, result_json MEDIUMTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_settlement (
 session_id BIGINT PRIMARY KEY, report_json MEDIUMTEXT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS practice_review (
 user_id BIGINT NOT NULL, session_question_id BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,session_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
