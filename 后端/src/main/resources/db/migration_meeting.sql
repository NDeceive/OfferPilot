-- Additive tables: enterprise meeting rooms, live participants and persisted advice.
-- 与会话/报告等既有数据完全解耦；不建外键，跟随 teaching_alignment.sql 的迁移风格。
CREATE TABLE IF NOT EXISTS meeting (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(8) NOT NULL, title VARCHAR(80) NOT NULL,
 host_id BIGINT NOT NULL, job_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, started_at DATETIME NULL, ended_at DATETIME NULL,
 UNIQUE KEY uq_meeting_code (code), INDEX idx_meeting_host (host_id, status, created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS meeting_participant (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, meeting_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 display_name VARCHAR(60) NOT NULL, role VARCHAR(20) NOT NULL,
 join_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, leave_time DATETIME NULL,
 UNIQUE KEY uq_meeting_participant (meeting_id, user_id), INDEX idx_meeting_participant_user (user_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS meeting_advice (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, meeting_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
 target_user_id BIGINT NULL, content VARCHAR(2000) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_meeting_advice_meeting (meeting_id, id)
) ENGINE=InnoDB;
