-- 作答录音保留策略（只增不改，幂等）
CREATE TABLE IF NOT EXISTS interview_voice_retention (
    session_id   BIGINT PRIMARY KEY,
    retain_days  INT COMMENT '0=永久，NULL=未设置（按默认30天）',
    expire_at    DATETIME NULL COMMENT '到点清理；永久为 NULL',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='作答录音保留期限';
