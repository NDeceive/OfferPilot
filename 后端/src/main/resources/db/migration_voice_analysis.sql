-- 语音分析与作答录音（只增不改，启动时幂等执行）
CREATE TABLE IF NOT EXISTS interview_answer_audio (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id      BIGINT NOT NULL COMMENT '会话ID',
    round_no        INT NOT NULL COMMENT '第几题',
    question_id     BIGINT COMMENT '题目ID',
    file_path       VARCHAR(255) NOT NULL COMMENT '音频相对路径',
    duration_ms     INT COMMENT '时长(毫秒)',
    byte_size       BIGINT COMMENT '字节数',
    status          VARCHAR(20) NOT NULL DEFAULT 'OK' COMMENT 'OK / FAILED',
    failure_reason  VARCHAR(40) COMMENT 'mic_denied / upload_failed / empty_audio',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_audio_session_round (session_id, round_no),
    KEY idx_audio_session (session_id)
) ENGINE=InnoDB COMMENT='作答音频';

CREATE TABLE IF NOT EXISTS interview_voice_analysis (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id      BIGINT NOT NULL,
    round_no        INT NOT NULL,
    metrics_json    TEXT COMMENT '逐题原始指标',
    fluency         DECIMAL(5,2) COMMENT '流畅度 0-100',
    volume_score    DECIMAL(5,2) COMMENT '音量控制 0-100',
    confidence_est  DECIMAL(5,2) COMMENT '表达状态(估算) 0-100',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_voice_session_round (session_id, round_no),
    KEY idx_voice_session (session_id)
) ENGINE=InnoDB COMMENT='逐题语音分析';

CREATE TABLE IF NOT EXISTS interview_voice_session (
    session_id      BIGINT PRIMARY KEY,
    enabled         TINYINT NOT NULL DEFAULT 1 COMMENT '本场是否开启语音分析',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ANALYZED / FAILED / DISABLED',
    failure_reason  VARCHAR(40),
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='会话语音开关与状态';
