CREATE TABLE IF NOT EXISTS student_report_job (
 session_id BIGINT PRIMARY KEY,
 state VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
 attempts INT NOT NULL DEFAULT 0,
 report_id BIGINT NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX ix_student_report_job_state (state,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_answer_receipt (
 session_id BIGINT NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 response_json TEXT NOT NULL,
 PRIMARY KEY(session_id,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS account_token_version (
 user_id BIGINT PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_interview_snapshot (
 session_id BIGINT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 start_request_id VARCHAR(64) NULL,
 request_hash VARCHAR(64) NULL,
 job_json JSON NOT NULL,
 resume_json JSON NULL,
 UNIQUE KEY uq_student_start (user_id,start_request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
