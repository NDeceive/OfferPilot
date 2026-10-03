-- Additive migration. Existing interviews and reports are preserved.
CREATE TABLE IF NOT EXISTS teaching_class (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, teacher_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL, semester VARCHAR(80) NOT NULL,
 invite_code VARCHAR(32) NOT NULL UNIQUE, archived BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_teacher(teacher_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_member (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, class_id BIGINT NOT NULL, student_id BIGINT NOT NULL,
 state VARCHAR(20) NOT NULL DEFAULT 'PENDING', joined_at DATETIME NULL,
 UNIQUE KEY uq_member(class_id,student_id), KEY idx_student(student_id),
 FOREIGN KEY(class_id) REFERENCES teaching_class(id), FOREIGN KEY(student_id) REFERENCES sys_user(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_task (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, class_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL,
 description TEXT, job_id BIGINT NOT NULL, questions_json JSON NOT NULL,
 duration_seconds INT NOT NULL, difficulty INT NOT NULL DEFAULT 2,
 min_attempts INT NOT NULL DEFAULT 1, max_attempts INT NOT NULL DEFAULT 3,
 allow_late BOOLEAN NOT NULL DEFAULT FALSE, start_time DATETIME NOT NULL,
 deadline DATETIME NOT NULL, published_at DATETIME NULL, ended_at DATETIME NULL,
 version INT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(class_id) REFERENCES teaching_class(id), FOREIGN KEY(job_id) REFERENCES job_position(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_assignment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL, student_id BIGINT NOT NULL,
 UNIQUE KEY uq_assignment(task_id,student_id), KEY idx_student(student_id),
 FOREIGN KEY(task_id) REFERENCES teaching_task(id), FOREIGN KEY(student_id) REFERENCES sys_user(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_attempt (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, assignment_id BIGINT NOT NULL,
 session_id BIGINT NOT NULL UNIQUE, state VARCHAR(20) NOT NULL DEFAULT 'ONGOING',
 valid BOOLEAN NOT NULL DEFAULT FALSE, submitted_at DATETIME NULL, deadline_snapshot DATETIME NOT NULL,
 report_id BIGINT NULL, failure_reason VARCHAR(255) NULL, started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(assignment_id) REFERENCES teaching_assignment(id), FOREIGN KEY(session_id) REFERENCES interview_session(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_review (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, report_id BIGINT NOT NULL, teacher_id BIGINT NOT NULL,
 text TEXT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_report(report_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS teaching_message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(160) NOT NULL,
 body TEXT NOT NULL, link VARCHAR(255) NOT NULL, is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_inbox(user_id,is_read)
) ENGINE=InnoDB;
