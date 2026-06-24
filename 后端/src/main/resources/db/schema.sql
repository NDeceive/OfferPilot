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

-- =====================================================================
-- 题库种子数据（Phase 1 规则化面试用）
--   job_id=1 Java 后端 15 题 / job_id=2 Web 前端 10 题
--   answer_points 用「、」分隔关键词（规则引擎据此判断回答是否覆盖要点）
--   difficulty: 1简单 2中等 3困难    type 全部为 MAIN
-- 说明：如需单独补种（不重建库），可直接执行本段 INSERT。
-- =====================================================================
INSERT INTO question (job_id, content, answer_points, ability_tag, difficulty, followup_hint, type) VALUES
-- ---- Java 后端（job_id = 1）----
(1, '请谈谈 Java 中 == 和 equals 的区别，以及为什么重写 equals 时通常要重写 hashCode？',
    '引用比较、值比较、Object默认实现、哈希一致性、equals相等则hashCode必须相等',
    'Java基础', 1,
    '能否举一个不重写 hashCode 导致 HashMap 行为异常的具体例子？', 'MAIN'),
(1, '说说 Java 的基本数据类型有哪些，以及自动装箱拆箱可能带来的问题。',
    '八种基本类型、包装类、装箱、拆箱、缓存池、空指针、性能开销',
    'Java基础', 1,
    'Integer 的缓存范围是多少？为什么 128 和 127 的比较结果不一样？', 'MAIN'),
(1, '请描述一下 JVM 的内存区域划分，以及哪些区域是线程私有的。',
    '堆、方法区、虚拟机栈、本地方法栈、程序计数器、线程私有、线程共享',
    'JVM', 2,
    '结合你的项目，遇到过 OOM 吗？你是如何定位到具体内存区域的？', 'MAIN'),
(1, '谈谈 JVM 的垃圾回收机制，常见的垃圾回收算法和你了解的收集器。',
    '可达性分析、引用计数、标记清除、复制算法、标记整理、分代收集、CMS、G1',
    'JVM', 3,
    '你在项目中调过 GC 参数吗？是怎么观察 GC 日志并做优化的？', 'MAIN'),
(1, '请比较 ArrayList 和 LinkedList 的区别及各自的适用场景。',
    '动态数组、双向链表、随机访问、插入删除、时间复杂度、内存占用',
    '集合', 1,
    '在频繁头部插入的场景下你会怎么选？为什么？', 'MAIN'),
(1, '说说 HashMap 的底层数据结构和扩容机制，JDK1.8 做了哪些改进。',
    '数组、链表、红黑树、哈希、扰动函数、负载因子、扩容、树化阈值',
    '集合', 2,
    '为什么链表长度超过 8 才转红黑树，而不是更小的值？', 'MAIN'),
(1, '请解释一下 Java 中线程的几种创建方式，以及线程池的核心参数。',
    '继承Thread、实现Runnable、Callable、线程池、核心线程数、最大线程数、队列、拒绝策略',
    '多线程', 2,
    '你在项目里用线程池处理过什么任务？拒绝策略选的哪种，为什么？', 'MAIN'),
(1, '谈谈 synchronized 和 ReentrantLock 的区别，以及 volatile 的作用。',
    '互斥、可重入、公平锁、可见性、有序性、内存屏障、CAS、锁升级',
    '多线程', 3,
    'volatile 能保证原子性吗？为什么 i++ 用 volatile 还是线程不安全？', 'MAIN'),
(1, '请说明 Spring Boot 自动配置的原理。',
    '约定优于配置、starter、@EnableAutoConfiguration、SPI、spring.factories、条件注解、@ConditionalOnClass',
    'Spring Boot', 2,
    '能否结合具体项目说明自动配置在你的项目中解决了什么问题？', 'MAIN'),
(1, '说说 Spring 中 Bean 的生命周期，以及如何解决循环依赖。',
    '实例化、属性填充、初始化、销毁、三级缓存、提前暴露、AOP代理',
    'Spring Boot', 3,
    '三级缓存中第三级缓存存的是什么？只有两级行不行？', 'MAIN'),
(1, '请谈谈数据库索引的原理，以及什么情况下索引会失效。',
    'B+树、聚簇索引、二级索引、回表、最左前缀、覆盖索引、隐式转换、函数操作',
    'MySQL', 2,
    '你在项目里遇到过慢查询吗？是怎么用 explain 分析并优化的？', 'MAIN'),
(1, '说说 MySQL 的事务隔离级别，以及如何解决幻读。',
    '读未提交、读已提交、可重复读、串行化、脏读、不可重复读、幻读、MVCC、间隙锁',
    'MySQL', 3,
    'InnoDB 在 RR 级别下是怎么用间隙锁来避免幻读的？', 'MAIN'),
(1, '谈谈 Redis 常见的数据类型及其典型应用场景。',
    'String、Hash、List、Set、ZSet、缓存、计数器、排行榜、分布式锁',
    'Redis', 1,
    '排行榜场景你会用哪种结构？为什么不用关系型数据库排序？', 'MAIN'),
(1, '请说明 Redis 缓存穿透、缓存击穿、缓存雪崩的区别和解决方案。',
    '缓存穿透、布隆过滤器、空值、缓存击穿、互斥锁、热点、缓存雪崩、过期时间打散',
    'Redis', 2,
    '你的项目中是怎么防止大量 key 同时过期导致雪崩的？', 'MAIN'),
(1, '请介绍一个你最有成就感的后端项目，重点说明你负责的模块和解决的关键技术难点。',
    '项目背景、个人职责、技术栈、难点、解决方案、量化成果',
    '项目经验', 2,
    '这个项目中遇到的最大性能瓶颈是什么？你是怎么定位并解决的？', 'MAIN'),
-- ---- Web 前端（job_id = 2）----
(2, '请谈谈 CSS 盒模型，以及标准盒模型和怪异盒模型的区别。',
    'content、padding、border、margin、box-sizing、content-box、border-box',
    'HTML/CSS', 1,
    '实际开发中你一般把 box-sizing 设成什么？为什么这样设置更省心？', 'MAIN'),
(2, '说说常见的 CSS 居中方案，以及 flex 布局的核心属性。',
    'flex、justify-content、align-items、absolute、transform、margin auto、grid',
    'HTML/CSS', 2,
    '一个未知宽高的元素要水平垂直居中，你会优先用哪种方案？', 'MAIN'),
(2, '请解释 JavaScript 中的闭包，以及它的常见应用和潜在问题。',
    '作用域、词法环境、变量私有化、引用、内存泄漏、防抖节流',
    'JavaScript', 2,
    '你能结合项目说说在哪用到过闭包，又是怎么避免内存泄漏的吗？', 'MAIN'),
(2, '谈谈 JavaScript 的事件循环机制，宏任务和微任务的执行顺序。',
    '单线程、调用栈、任务队列、宏任务、微任务、Promise、setTimeout、async await',
    'JavaScript', 3,
    '能口述一段含 Promise 和 setTimeout 的代码的输出顺序吗？', 'MAIN'),
(2, '说说 var、let、const 的区别，以及什么是变量提升和暂时性死区。',
    '函数作用域、块级作用域、变量提升、暂时性死区、重复声明、不可变引用',
    'JavaScript', 1,
    'const 声明的对象，其属性可以被修改吗？为什么？', 'MAIN'),
(2, '请说明 Vue 的响应式原理，Vue2 和 Vue3 在实现上有什么不同。',
    'Object.defineProperty、getter、setter、Proxy、依赖收集、派发更新、数组监听',
    'Vue', 3,
    'Vue3 用 Proxy 替代 defineProperty 主要解决了哪些痛点？', 'MAIN'),
(2, '谈谈 Vue 的生命周期钩子，以及 v-if 和 v-show 的区别。',
    'created、mounted、updated、destroyed、setup、DOM渲染、display、销毁重建',
    'Vue', 1,
    '发请求获取初始数据，你一般放在哪个钩子里？为什么不放更早？', 'MAIN'),
(2, '请说说 Vue 中组件之间通信的几种方式。',
    'props、emit、自定义事件、provide、inject、vuex、pinia、ref、eventBus',
    '组件通信', 2,
    '跨多层级的组件通信你会怎么处理？为什么不用层层 props 传递？', 'MAIN'),
(2, '请描述从浏览器输入 URL 到页面展示，中间发生了什么。',
    'DNS解析、TCP连接、HTTP请求、服务器响应、渲染、重排重绘、缓存',
    '浏览器', 2,
    '其中哪一步最容易成为性能瓶颈？前端能做哪些优化？', 'MAIN'),
(2, '请谈谈前端性能优化的常见手段，以及你在项目中实践过哪些。',
    '懒加载、代码分割、缓存、CDN、压缩、防抖节流、首屏、减少重排',
    '性能优化', 2,
    '你的项目首屏加载做过优化吗？优化前后大概提升了多少？', 'MAIN'),
(2, '请介绍一个你最有成就感的前端项目，重点说明你负责的部分和遇到的难点。',
    '项目背景、个人职责、技术栈、难点、解决方案、量化成果',
    '项目经验', 2,
    '这个项目里你解决过的最棘手的兼容性或性能问题是什么？', 'MAIN');
