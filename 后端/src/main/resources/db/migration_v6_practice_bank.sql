-- Additive only. Never execute seed_skill_bank.sql against an existing database.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS practice_company (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  aliases_json TEXT NOT NULL,
  evidence_json MEDIUMTEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Metadata for existing skill_question; no alteration to the shared interview bank.
CREATE TABLE IF NOT EXISTS practice_question_meta (
  question_id BIGINT PRIMARY KEY,
  canonical_id VARCHAR(180) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  subtype VARCHAR(20) NOT NULL DEFAULT 'KNOWLEDGE',
  company_id BIGINT NULL,
  job_codes_json TEXT NOT NULL,
  topics_json TEXT NOT NULL,
  tags_json TEXT NOT NULL,
  source_json MEDIUMTEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  batch_id VARCHAR(100) NOT NULL,
  KEY idx_practice_meta_review (review_status,company_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_coding_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  canonical_id VARCHAR(180) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  difficulty TINYINT NOT NULL,
  job_codes_json TEXT NOT NULL,
  topics_json TEXT NOT NULL,
  tags_json TEXT NOT NULL,
  language VARCHAR(30) NOT NULL DEFAULT 'java',
  starter_code MEDIUMTEXT NOT NULL,
  examples_json MEDIUMTEXT NOT NULL,
  tests_json MEDIUMTEXT NOT NULL,
  reference_code MEDIUMTEXT NOT NULL,
  source_json MEDIUMTEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  batch_id VARCHAR(100) NOT NULL,
  KEY idx_practice_coding_review (review_status,difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Immutable provenance and public examples copied when a practice begins.
CREATE TABLE IF NOT EXISTS practice_question_detail (
  session_question_id BIGINT PRIMARY KEY,
  detail_json MEDIUMTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_session_detail (
  session_id BIGINT PRIMARY KEY,
  detail_json MEDIUMTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_company_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  job_code VARCHAR(30) NOT NULL,
  topic VARCHAR(100) NOT NULL,
  payload_json MEDIUMTEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
