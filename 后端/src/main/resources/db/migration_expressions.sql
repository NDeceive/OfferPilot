CREATE TABLE IF NOT EXISTS interview_expression_sample (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  sample_id VARCHAR(36) NOT NULL,
  question_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  captured_at BIGINT NOT NULL,
  face_detected BOOLEAN NOT NULL,
  probabilities JSON NULL,
  UNIQUE KEY uq_expression_sample (session_id, sample_id),
  KEY idx_expression_timeline (session_id, captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_pause_state (
  session_id BIGINT PRIMARY KEY,
  paused_at DATETIME NULL,
  paused_seconds BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
