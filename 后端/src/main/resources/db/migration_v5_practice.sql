-- V5：专项刷题的会话、题目快照与提交记录。
-- 请在已执行 seed_skill_bank.sql 的 zhimian 库中执行；本迁移不会改动已有面试数据。
USE zhimian;

CREATE TABLE IF NOT EXISTS practice_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  job_id BIGINT NULL,
  company VARCHAR(100) NOT NULL DEFAULT '不限公司',
  topic VARCHAR(100) NOT NULL,
  training_mode VARCHAR(20) NOT NULL,
  question_type VARCHAR(20) NOT NULL,
  difficulty TINYINT NOT NULL DEFAULT 2,
  requested_count INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ONGOING',
  current_index INT NOT NULL DEFAULT 0,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_practice_session_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_session_question (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  source_question_id BIGINT NULL,
  sequence_no INT NOT NULL,
  question_type VARCHAR(20) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  reference_answer MEDIUMTEXT NULL,
  answer_keywords TEXT NULL,
  difficulty TINYINT NOT NULL DEFAULT 2,
  language VARCHAR(30) NULL,
  starter_code MEDIUMTEXT NULL,
  tests_json MEDIUMTEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_practice_session_question (session_id, sequence_no),
  KEY idx_practice_question_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_submission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  session_question_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  submission_type VARCHAR(20) NOT NULL,
  answer_text MEDIUMTEXT NULL,
  source_code MEDIUMTEXT NULL,
  language VARCHAR(30) NULL,
  status VARCHAR(30) NOT NULL,
  is_correct TINYINT NULL,
  score INT NULL,
  judge_token VARCHAR(100) NULL,
  stdout MEDIUMTEXT NULL,
  stderr MEDIUMTEXT NULL,
  compile_output MEDIUMTEXT NULL,
  time_seconds VARCHAR(30) NULL,
  memory_kb INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_practice_submission_question (session_question_id, created_at),
  KEY idx_practice_submission_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS practice_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_question_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_practice_favorite (user_id, session_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
