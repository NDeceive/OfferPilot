CREATE TABLE IF NOT EXISTS resume_analysis_report (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 job_id BIGINT NOT NULL,
 version_id BIGINT NULL,
 input_hash CHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL,
 result_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_analysis_input (user_id, job_id, input_hash, id)
);
