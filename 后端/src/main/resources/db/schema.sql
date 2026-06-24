-- =====================================================================
-- 智面幻境 · 智能模拟面试系统 — 数据库表结构
-- MySQL 8.0+
-- 设计依据：项目计划书 3.3 业务流程 / 3.4 功能需求 / 3.6 数据需求
-- =====================================================================

CREATE DATABASE IF NOT EXISTS zhimian
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE zhimian;

-- ---------------------------------------------------------------------
-- 1. 用户表（学生 / 教师 / 管理员）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username     VARCHAR(50)  NOT NULL UNIQUE             COMMENT '登录账号',
    password     VARCHAR(100) NOT NULL                    COMMENT '密码(BCrypt加密)',
    nickname     VARCHAR(50)                              COMMENT '昵称',
    role         VARCHAR(20)  NOT NULL DEFAULT 'STUDENT'  COMMENT '角色: STUDENT/TEACHER/ADMIN',
    email        VARCHAR(100)                             COMMENT '邮箱',
    phone        VARCHAR(20)                              COMMENT '手机号(脱敏存储)',
    avatar       VARCHAR(255)                             COMMENT '头像URL',
    status       TINYINT      NOT NULL DEFAULT 1          COMMENT '状态: 1正常 0禁用',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_role (role)
) ENGINE=InnoDB COMMENT='系统用户表';

-- ---------------------------------------------------------------------
-- 2. 岗位表（Java后端 / Web前端 等）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS job_position;
CREATE TABLE job_position (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '岗位ID',
    name         VARCHAR(50)  NOT NULL                    COMMENT '岗位名称',
    category     VARCHAR(50)                              COMMENT '岗位类别(后端/前端/算法等)',
    description  TEXT                                     COMMENT '岗位描述',
    abilities    TEXT                                     COMMENT '岗位能力要求(JSON数组)',
    keywords     TEXT                                     COMMENT '岗位关键词(JSON数组,用于RAG匹配)',
    status       TINYINT      NOT NULL DEFAULT 1          COMMENT '状态: 1启用 0停用',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='岗位表';

-- ---------------------------------------------------------------------
-- 3. 题库表（题干/答案要点/能力标签/追问条件）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS question;
CREATE TABLE question (
    id              BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '题目ID',
    job_id          BIGINT      NOT NULL                   COMMENT '所属岗位ID',
    content         TEXT        NOT NULL                   COMMENT '题干',
    answer_points   TEXT                                   COMMENT '答案要点(用于评分参考)',
    ability_tag     VARCHAR(100)                           COMMENT '能力标签(如:并发/JVM/数据结构)',
    difficulty      TINYINT     NOT NULL DEFAULT 2         COMMENT '难度: 1简单 2中等 3困难',
    followup_hint   TEXT                                   COMMENT '追问条件/方向提示',
    type            VARCHAR(20) NOT NULL DEFAULT 'MAIN'    COMMENT '类型: MAIN主问 FOLLOWUP追问样例',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_job (job_id),
    KEY idx_ability (ability_tag),
    KEY idx_difficulty (difficulty)
) ENGINE=InnoDB COMMENT='题库表';

-- ---------------------------------------------------------------------
-- 4. 知识库表（RAG 检索的知识片段）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS knowledge_doc;
CREATE TABLE knowledge_doc (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '知识ID',
    job_id       BIGINT                                   COMMENT '关联岗位ID(NULL为通用)',
    title        VARCHAR(200)                             COMMENT '知识点标题',
    content      TEXT         NOT NULL                    COMMENT '知识内容片段',
    ability_tag  VARCHAR(100)                             COMMENT '能力标签',
    embedding    MEDIUMTEXT                               COMMENT '向量(JSON数组,起步阶段存文本)',
    source       VARCHAR(200)                             COMMENT '来源',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_job (job_id),
    KEY idx_ability (ability_tag)
) ENGINE=InnoDB COMMENT='知识库表(RAG)';

-- ---------------------------------------------------------------------
-- 5. 简历 / 个人画像表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS resume;
CREATE TABLE resume (
    id           BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '简历ID',
    user_id      BIGINT       NOT NULL                    COMMENT '用户ID',
    raw_text     TEXT                                     COMMENT '简历原文',
    projects     TEXT                                     COMMENT '提取的项目经历(JSON)',
    skills       TEXT                                     COMMENT '提取的技能关键词(JSON)',
    keywords     TEXT                                     COMMENT '画像关键词(JSON)',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='简历/个人画像表';

-- ---------------------------------------------------------------------
-- 6. 面试会话表（一次完整模拟面试）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS interview_session;
CREATE TABLE interview_session (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '会话ID',
    user_id       BIGINT      NOT NULL                   COMMENT '用户ID',
    job_id        BIGINT      NOT NULL                   COMMENT '岗位ID',
    resume_id     BIGINT                                 COMMENT '使用的简历ID',
    difficulty    TINYINT     NOT NULL DEFAULT 2         COMMENT '面试难度',
    status        VARCHAR(20) NOT NULL DEFAULT 'ONGOING' COMMENT '状态: ONGOING/FINISHED/ABORTED',
    is_retrain    TINYINT     NOT NULL DEFAULT 0         COMMENT '是否复训: 0否 1是',
    weak_tags     VARCHAR(255)                           COMMENT '复训针对的薄弱标签',
    start_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time      DATETIME                               COMMENT '结束时间',
    KEY idx_user (user_id),
    KEY idx_job (job_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='面试会话表';

-- ---------------------------------------------------------------------
-- 7. 面试问答记录表（含主问与追问）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS interview_message;
CREATE TABLE interview_message (
    id            BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    session_id    BIGINT      NOT NULL                   COMMENT '会话ID',
    question_id   BIGINT                                 COMMENT '关联题库ID(追问可为空)',
    round_no      INT         NOT NULL DEFAULT 1         COMMENT '第几轮',
    role          VARCHAR(20) NOT NULL                   COMMENT '角色: INTERVIEWER面试官 CANDIDATE考生',
    msg_type      VARCHAR(20) NOT NULL DEFAULT 'MAIN'    COMMENT '类型: OPENING开场 MAIN主问 FOLLOWUP追问 ANSWER回答 SUMMARY总结',
    content       TEXT        NOT NULL                   COMMENT '内容',
    ability_tag   VARCHAR(100)                           COMMENT '能力标签',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session (session_id),
    KEY idx_round (session_id, round_no)
) ENGINE=InnoDB COMMENT='面试问答记录表';

-- ---------------------------------------------------------------------
-- 8. 评估报告表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS interview_report;
CREATE TABLE interview_report (
    id              BIGINT      PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    session_id      BIGINT      NOT NULL UNIQUE            COMMENT '会话ID',
    user_id         BIGINT      NOT NULL                   COMMENT '用户ID',
    total_score     DECIMAL(5,2)                           COMMENT '综合得分',
    summary         TEXT                                   COMMENT '综合评价',
    strengths       TEXT                                   COMMENT '优势(JSON)',
    weaknesses      TEXT                                   COMMENT '不足(JSON)',
    suggestions     TEXT                                   COMMENT '改进建议(JSON)',
    weak_tags       VARCHAR(255)                           COMMENT '薄弱能力标签(供复训)',
    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='评估报告表';

-- ---------------------------------------------------------------------
-- 9. 报告评分维度明细表（雷达图数据源）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS report_dimension;
CREATE TABLE report_dimension (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    report_id     BIGINT       NOT NULL                   COMMENT '报告ID',
    dimension     VARCHAR(50)  NOT NULL                   COMMENT '维度名(专业知识/项目表达/逻辑表达/岗位匹配/追问应对)',
    score         DECIMAL(5,2) NOT NULL                   COMMENT '该维度得分(0-100)',
    level         VARCHAR(20)                             COMMENT '等级描述',
    explanation   TEXT                                    COMMENT '评分解释',
    KEY idx_report (report_id)
) ENGINE=InnoDB COMMENT='报告评分维度明细表';

-- =====================================================================
-- 初始化数据：默认账号 + 2个岗位（密码均为 123456 的 BCrypt 值）
-- =====================================================================
INSERT INTO sys_user (username, password, nickname, role) VALUES
('admin',   '$2a$10$ZK6vA7aDfR0wYwEgu1Iv.ezL1U97zMKw/WqDjNq3.ux.0n7nUq4om', '系统管理员', 'ADMIN'),
('teacher', '$2a$10$ZK6vA7aDfR0wYwEgu1Iv.ezL1U97zMKw/WqDjNq3.ux.0n7nUq4om', '就业指导老师', 'TEACHER'),
('student', '$2a$10$ZK6vA7aDfR0wYwEgu1Iv.ezL1U97zMKw/WqDjNq3.ux.0n7nUq4om', '测试学生', 'STUDENT');

INSERT INTO job_position (name, category, description, abilities, keywords) VALUES
('Java 后端开发', '后端',
 '负责后端服务开发，熟悉 Java 核心、Spring 生态、数据库与分布式基础。',
 '["Java核心","JVM","并发编程","Spring框架","MySQL","Redis","分布式"]',
 '["Java","JVM","并发","Spring","SpringBoot","MySQL","Redis","锁","事务","微服务"]'),
('Web 前端开发', '前端',
 '负责前端页面与交互开发，熟悉 HTML/CSS/JS、主流框架与工程化。',
 '["HTML/CSS","JavaScript","Vue/React","浏览器原理","工程化","性能优化"]',
 '["HTML","CSS","JavaScript","Vue","React","浏览器","webpack","性能","ES6","HTTP"]');
