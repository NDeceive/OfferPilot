-- Additive, repeatable migration. Objective items stay outside the interview bank.
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS practice_objective_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  canonical_id VARCHAR(180) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  subtype VARCHAR(20) NOT NULL,
  difficulty TINYINT NOT NULL,
  job_codes_json TEXT NOT NULL,
  topics_json TEXT NOT NULL,
  tags_json TEXT NOT NULL,
  options_json TEXT NOT NULL,
  correct_answer VARCHAR(30) NOT NULL,
  reference_answer MEDIUMTEXT NOT NULL,
  source_json MEDIUMTEXT NOT NULL,
  review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  batch_id VARCHAR(100) NOT NULL,
  KEY idx_objective_review (review_status,subtype,difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
