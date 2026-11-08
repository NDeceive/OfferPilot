-- Additive only. Message timestamps are added idempotently by StudentBackendSchema.
CREATE TABLE IF NOT EXISTS minutes_document (
 session_id BIGINT PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 paused BOOLEAN NOT NULL DEFAULT FALSE, control_version BIGINT NOT NULL DEFAULT 0,
 lifecycle VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', coverage VARCHAR(16) NOT NULL DEFAULT 'PARTIAL',
 initialized_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 finalized_at DATETIME(6) NULL, active_summary_job_id BIGINT NULL
);
CREATE TABLE IF NOT EXISTS minutes_window (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL,
 opened_at DATETIME(6) NOT NULL, closed_at DATETIME(6) NULL,
 KEY idx_minutes_window(session_id,opened_at)
);
CREATE TABLE IF NOT EXISTS minutes_entry (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL,
 source_message_id BIGINT NULL, window_id BIGINT NULL, parent_entry_id BIGINT NULL,
 round_no INT NULL, role VARCHAR(24) NOT NULL, entry_type VARCHAR(24) NOT NULL,
 original_text MEDIUMTEXT NOT NULL, submitted_at DATETIME(6) NOT NULL,
 request_id VARCHAR(80) NULL, control_value BOOLEAN NULL, control_version BIGINT NULL,
 UNIQUE KEY uk_minutes_source(session_id,source_message_id),
 UNIQUE KEY uk_minutes_request(session_id,request_id), KEY idx_minutes_entries(session_id,id)
);
CREATE TABLE IF NOT EXISTS minutes_summary_job (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT NOT NULL, request_id VARCHAR(80) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'QUEUED', attempts INT NOT NULL DEFAULT 0,
 lease_token VARCHAR(64) NULL, lease_until DATETIME NULL,
 available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 output_json MEDIUMTEXT NULL, error_code VARCHAR(64) NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME NULL,
 UNIQUE KEY uk_minutes_job(session_id,request_id), KEY idx_minutes_jobs(state,available_at)
);
