-- =====================================================================
-- 评分系统改造 · 数据库迁移（第1步）
-- 基于模块化匹配评价体系，4 新表 + 2 旧表扩展
-- =====================================================================

USE zhimian;

-- -------------------------------------------------------------------
-- 1. 评价模块字典表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS score_module;
CREATE TABLE score_module (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '模块ID',
    code         VARCHAR(40)  NOT NULL UNIQUE COMMENT '模块编码',
    name         VARCHAR(40)  NOT NULL COMMENT '模块中文名',
    description  VARCHAR(200) COMMENT '评估重点说明',
    sort_order   INT          NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='评价模块字典';

INSERT INTO score_module (code, name, description, sort_order) VALUES
('technical_base',        '技术基础能力',  '概念、术语、知识点准确度', 1),
('technical_depth',       '技术深度能力',  '底层原理、机制、优缺点、边界场景', 2),
('engineering_practice',  '工程实践能力',  '真实开发场景意识（调试/部署/监控）', 3),
('problem_analysis',      '问题分析能力',  '故障拆解、定位、方案制定', 4),
('project_expression',    '项目表达能力',  '项目背景-职责-方案-难点-结果的结构化表达', 5),
('followup_adaptability', '追问应变能力',  '连续追问时的补充、纠偏、展开能力', 6),
('logical_structure',     '逻辑结构能力',  '回答条理性、分点展开', 7),
('expression_clarity',    '表达清晰能力',  '清楚、准确、简洁、有重点', 8),
('position_cognition',    '岗位认知能力',  '对目标岗位职责/技术栈/发展方向的理解', 9),
('project_review',        '项目复盘能力',  '总结不足、优化方向、个人成长', 10);

-- -------------------------------------------------------------------
-- 2. 用户模块选择配置表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS interview_module_preference;
CREATE TABLE interview_module_preference (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    session_id    BIGINT       NOT NULL UNIQUE COMMENT '面试会话ID',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    modules_json  TEXT         NOT NULL COMMENT '模块配置JSON: [{code, rank, level}]',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='用户模块选择配置';

-- -------------------------------------------------------------------
-- 3. 评分配置快照表（保证历史报告可复现）
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS interview_score_config_snapshot;
CREATE TABLE interview_score_config_snapshot (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    session_id    BIGINT       NOT NULL UNIQUE COMMENT '面试会话ID',
    weights_json  TEXT         NOT NULL COMMENT '权重快照JSON: {code: weight}',
    targets_json  TEXT         NOT NULL COMMENT '目标线快照JSON: {code: targetScore}',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='评分配置快照（保证历史报告可复现）';

-- -------------------------------------------------------------------
-- 4. 模块评分明细表
-- -------------------------------------------------------------------
DROP TABLE IF EXISTS interview_module_score;
CREATE TABLE interview_module_score (
    id                   BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    report_id            BIGINT        NOT NULL COMMENT '报告ID',
    module_code          VARCHAR(40)   NOT NULL COMMENT '模块编码',
    raw_score            DECIMAL(5,2)  NOT NULL COMMENT '原始得分(0-100)',
    target_score         DECIMAL(5,2)  NOT NULL COMMENT '目标线',
    module_match         DECIMAL(5,2)  NOT NULL COMMENT '单模块匹配度',
    base_weight          DECIMAL(5,4)  NOT NULL COMMENT '该模块权重',
    gap_score            DECIMAL(5,2)  NOT NULL COMMENT '差距分(目标-实际,≥0)',
    improvement_priority DECIMAL(7,2)  NOT NULL COMMENT '提升优先级(权重×差距)',
    evidence             TEXT          COMMENT '评分证据(来自AI)',
    suggestion           TEXT          COMMENT '改进建议(来自AI)',
    ai_confidence        DECIMAL(3,2)  COMMENT 'AI置信度(0-1)',
    score_source         VARCHAR(10)   NOT NULL DEFAULT 'AI' COMMENT '评分来源: AI / RULE',
    KEY idx_report (report_id)
) ENGINE=InnoDB COMMENT='模块评分明细';

-- -------------------------------------------------------------------
-- 5. interview_report 扩展字段（MySQL 8.0 用存储过程安全加列）
-- -------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(
    IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_def VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'zhimian' AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN ', col, ' ', col_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_missing('interview_report', 'overall_match_score',  'DECIMAL(5,2) COMMENT ''整体匹配度(0-120+)''');
CALL add_column_if_missing('interview_report', 'display_level',        'VARCHAR(30)  COMMENT ''匹配度等级''');
CALL add_column_if_missing('interview_report', 'profile_label',        'VARCHAR(255) COMMENT ''匹配画像标签(逗号分隔)''');
CALL add_column_if_missing('interview_report', 'report_json',          'MEDIUMTEXT   COMMENT ''完整报告JSON''');
CALL add_column_if_missing('interview_report', 'visualization_json',   'MEDIUMTEXT   COMMENT ''可视化专用JSON''');

-- -------------------------------------------------------------------
-- 6. interview_session 扩展字段
-- -------------------------------------------------------------------
CALL add_column_if_missing('interview_session', 'has_module_preference', 'TINYINT NOT NULL DEFAULT 0 COMMENT ''是否已配置模块偏好''');

DROP PROCEDURE IF EXISTS add_column_if_missing;
