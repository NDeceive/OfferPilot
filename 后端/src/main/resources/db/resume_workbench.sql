-- Additive, repeatable startup schema for the multi-user resume workbench.
-- Existing resume and resume_file rows are deliberately left untouched.
CREATE TABLE IF NOT EXISTS resume_claim (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    fact_text TEXT NOT NULL,
    resume_text TEXT NOT NULL,
    responsibility VARCHAR(32) NOT NULL,
    personal_boundary TEXT NULL,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    source_excerpt TEXT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_resume_claim_user (user_id, deleted_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-verified resume facts';

CREATE TABLE IF NOT EXISTS resume_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id BIGINT NULL,
    title VARCHAR(120) NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    content_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_resume_version_user (user_id, id),
    KEY idx_resume_version_job (user_id, job_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable resume versions';
