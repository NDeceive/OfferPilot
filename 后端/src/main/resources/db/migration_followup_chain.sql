-- 多层追问链：只增不改，启动时幂等执行（不要放 DROP / seed）
CREATE TABLE IF NOT EXISTS interview_followup_chain (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id       BIGINT NOT NULL COMMENT '会话ID',
    question_id      BIGINT NOT NULL COMMENT '主问题ID',
    round_no         INT NOT NULL COMMENT '轮次',
    layer_no         INT NOT NULL COMMENT '第几层追问，从1开始',
    dimension        VARCHAR(20) COMMENT '方向: CLARIFY/MECHANISM/TRADEOFF/BOUNDARY/TRANSFER/CORRECT',
    gate             VARCHAR(20) COMMENT '闸门: ASK/STOP_NO_NEED/STOP_NOT_SAMPLED/STOP_NO_TIME/STOP_DEPTH',
    probability      DECIMAL(5,4) COMMENT '当层条件触发概率',
    question         TEXT COMMENT '追问问题',
    answer           TEXT COMMENT '考生回答',
    uncovered_count  INT COMMENT '模型回报的未覆盖要点数，-1 表示未拿到',
    source           VARCHAR(10) COMMENT 'AI / RULE',
    reason           VARCHAR(255) COMMENT '触发原因',
    create_time      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chain_session (session_id),
    KEY idx_chain_question (session_id, question_id, layer_no)
) ENGINE=InnoDB COMMENT='多层追问链';
