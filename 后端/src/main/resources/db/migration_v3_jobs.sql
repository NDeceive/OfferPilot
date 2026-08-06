-- ============================================================================
-- V3 岗位族迁移：从 2 个扁平岗位 → 7 家族 × 32 细分方向
-- 依据《岗位能力知识库与岗位画像建模规范 V1.0》
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE job_position
    ADD COLUMN family VARCHAR(50) COMMENT '岗位族（后端开发/前端与客户端开发/全栈开发/算法与人工智能/产品经理/数据分析/软件测试）',
    ADD COLUMN code  VARCHAR(20) COMMENT '岗位编码（如 BE-JAVA, FE-WEB 等）';

-- 清理旧测试数据
DELETE FROM job_position;

-- =========================== 后端开发 (BE) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('Java后端开发工程师', 'BE-JAVA', '后端开发', '后端开发',
 '负责 Java 后端服务开发，熟悉 Java 核心、Spring 生态、数据库与分布式基础。',
 '["Java核心","JVM","并发编程","Spring框架","MySQL","Redis","分布式","微服务","设计模式"]',
 '["Java","JVM","并发","Spring","SpringBoot","MyBatis","MySQL","Redis","消息队列","微服务","Docker"]'),
('Python后端开发工程师', 'BE-PY', '后端开发', '后端开发',
 '负责 Python 后端服务与数据处理，熟悉 Django/Flask/FastAPI 及数据生态。',
 '["Python核心","Django/Flask/FastAPI","数据库","异步编程","数据处理","API设计"]',
 '["Python","Django","Flask","FastAPI","Celery","PostgreSQL","MongoDB","pandas","RESTful","GraphQL"]'),
('Go后端开发工程师', 'BE-GO', '后端开发', '后端开发',
 '负责 Go 语言高并发后端服务，熟悉微服务架构与云原生技术栈。',
 '["Go核心","并发编程","微服务","数据库","容器编排","性能调优"]',
 '["Go","Goroutine","gRPC","protobuf","Kubernetes","Docker","etcd","Redis","PostgreSQL","Kafka"]'),
('Node.js后端开发工程师', 'BE-NODE', '后端开发', '后端开发',
 '负责 Node.js 后端服务开发，熟悉异步 I/O、Express/NestJS 及 API 网关。',
 '["Node.js核心","异步编程","Express/NestJS","数据库","API设计","性能优化"]',
 '["Node.js","Express","NestJS","TypeScript","MongoDB","Redis","GraphQL","PM2","Jest","Docker"]'),
('C/C++高性能后端开发工程师', 'BE-CPP', '后端开发', '后端开发',
 '负责 C/C++ 高性能服务与基础架构开发，熟悉内存管理、网络编程与系统调优。',
 '["C/C++核心","内存管理","网络编程","多线程","系统调优","数据结构和算法"]',
 '["C++","STL","Boost","TCP/IP","epoll","多线程","锁","内存池","gRPC","Linux内核"]');

-- =========================== 前端与客户端开发 (FE) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('Web前端开发工程师', 'FE-WEB', '前端与客户端开发', '前端与客户端开发',
 '负责 Web 前端页面与交互开发，熟悉 HTML/CSS/JS、主流框架与工程化。',
 '["HTML/CSS","JavaScript/TypeScript","Vue/React","浏览器原理","工程化","性能优化"]',
 '["HTML","CSS","JavaScript","TypeScript","Vue","React","Next.js","webpack","Vite","浏览器","性能"]'),
('Android原生开发工程师', 'FE-ANDROID', '前端与客户端开发', '前端与客户端开发',
 '负责 Android 原生应用开发，熟悉 Kotlin/Java、Jetpack 及性能优化。',
 '["Kotlin/Java","Jetpack Compose","Android架构","性能优化","跨进程通信","安全与发布"]',
 '["Kotlin","Java","Jetpack","Compose","ViewModel","LiveData","Flutter","性能","内存泄漏","Gradle"]'),
('iOS原生开发工程师', 'FE-IOS', '前端与客户端开发', '前端与客户端开发',
 '负责 iOS 原生应用开发，熟悉 Swift/SwiftUI、AppKit 及 App Store 发布。',
 '["Swift/SwiftUI","UIKit","iOS架构","性能优化","内存管理","App Store"]',
 '["Swift","SwiftUI","UIKit","Combine","CoreData","GCD","Xcode","Metal","App Store","CI/CD"]'),
('跨端开发工程师（Flutter/React Native）', 'FE-CROSS', '前端与客户端开发', '前端与客户端开发',
 '负责跨平台移动应用开发，熟悉 Flutter 或 React Native 及原生桥接。',
 '["Flutter/RN","Dart/TypeScript","状态管理","原生桥接","性能调优","多端适配"]',
 '["Flutter","React Native","Dart","TypeScript","Redux","Riverpod","原生桥接","热更新","多端发布"]'),
('小程序与轻应用开发工程师', 'FE-MINI', '前端与客户端开发', '前端与客户端开发',
 '负责微信/支付宝等小程序及轻应用开发，熟悉各平台规范与性能优化。',
 '["小程序框架","前端基础","平台API","性能优化","多端适配","用户体验"]',
 '["微信小程序","支付宝小程序","uni-app","Taro","WXML","WXS","云开发","分包","性能","多平台"]'),
('桌面客户端开发工程师（Electron/Qt）', 'FE-DESKTOP', '前端与客户端开发', '前端与客户端开发',
 '负责桌面客户端开发，熟悉 Electron 或 Qt 框架及跨平台构建。',
 '["Electron/Qt","Chromium/WebKit","原生模块","进程模型","安装包构建","安全"]',
 '["Electron","Qt","Chromium","Node.js","C++","跨平台","NSIS","代码签名","进程通信","更新"]');

-- =========================== 全栈开发 (FS) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('Java Web全栈开发工程师', 'FS-JAVA', '全栈开发', '全栈开发',
 '负责 Java Web 全栈开发，同时掌握 Spring 后端与前端框架。',
 '["Java后端","前端框架","数据库","DevOps基础","系统设计","项目管理"]',
 '["Java","Spring","Vue","React","MySQL","Docker","CI/CD","Nginx","Linux","全栈架构"]'),
('Node.js全栈开发工程师', 'FS-NODE', '全栈开发', '全栈开发',
 '负责基于 Node.js 的全栈开发，熟悉后端 API 与前端框架。',
 '["Node.js后端","前端框架","数据库","API设计","云服务","工程化"]',
 '["Node.js","NestJS","React","Next.js","TypeScript","Prisma","PostgreSQL","AWS","Vercel","全栈"]'),
('Python Web全栈开发工程师', 'FS-PY', '全栈开发', '全栈开发',
 '负责 Python Web 全栈开发，熟悉 Django/Flask 后端与前端交互。',
 '["Python后端","前端基础","数据库","异步任务","部署运维","数据分析"]',
 '["Python","Django","Flask","Vue","HTMX","Celery","PostgreSQL","Docker","Nginx","全栈"]'),
('AI应用全栈开发工程师', 'FS-AI', '全栈开发', '全栈开发',
 '负责 AI 应用全栈开发，熟悉 LLM API 集成、RAG 管道与前端交互。',
 '["LLM集成","RAG管道","后端API","前端开发","Prompt工程","向量数据库"]',
 '["LangChain","OpenAI","VectorDB","Pinecone","Streamlit","Next.js","Python","TypeScript","RAG","Agent"]');

-- =========================== 算法与人工智能 (ALG) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('机器学习算法工程师', 'ALG-ML', '算法与人工智能', '算法与人工智能',
 '负责传统机器学习模型研发，熟悉特征工程、模型训练与评估。',
 '["数学基础","特征工程","经典算法","模型评估","Python/ML框架","工程部署"]',
 '["机器学习","XGBoost","LightGBM","scikit-learn","特征工程","Python","SQL","A/B测试","模型可解释性"]'),
('自然语言处理与大模型算法工程师', 'ALG-NLP', '算法与人工智能', '算法与人工智能',
 '负责 NLP 与大模型算法，熟悉 Transformer、LLM 微调与 RAG。',
 '["NLP基础","Transformer","LLM微调","RAG","Prompt工程","模型评估"]',
 '["NLP","Transformer","BERT","GPT","LoRA","RLHF","LangChain","向量检索","PyTorch","HuggingFace"]'),
('计算机视觉算法工程师', 'ALG-CV', '算法与人工智能', '算法与人工智能',
 '负责计算机视觉算法研发，熟悉图像分类、目标检测、分割等。',
 '["CV基础","CNN/Transformer","目标检测","图像分割","生成模型","模型部署"]',
 '["OpenCV","PyTorch","YOLO","ResNet","ViT","GAN","实例分割","TensorRT","ONNX","图像处理"]'),
('推荐/搜索/广告算法工程师', 'ALG-REC', '算法与人工智能', '算法与人工智能',
 '负责推荐系统、搜索排序与广告算法，熟悉召回/排序/重排全链路。',
 '["推荐系统","搜索排序","广告算法","特征工程","在线学习","AB实验"]',
 '["推荐","协同过滤","DSSM","DeepFM","多目标优化","向量检索","CTR预估","Learning to Rank"]'),
('语音算法工程师', 'ALG-SPEECH', '算法与人工智能', '算法与人工智能',
 '负责语音识别、合成与音频处理算法。',
 '["语音识别","语音合成","信号处理","声学模型","端到端模型","模型优化"]',
 '["ASR","TTS","Kaldi","Whisper","WaveNet","信号处理","MFCC","CTC","Transformer","PyTorch"]'),
('多模态与具身智能算法工程师', 'ALG-MM', '算法与人工智能', '算法与人工智能',
 '负责多模态理解与生成、具身智能相关算法研发。',
 '["多模态理解","视觉语言模型","视频理解","具身智能","生成模型","模型对齐"]',
 '["CLIP","GPT-4V","扩散模型","Sora","具身智能","3D视觉","VLM","RLHF","多模态对齐"]'),
('算法工程化与MLOps工程师', 'ALG-MLOPS', '算法与人工智能', '算法与人工智能',
 '负责算法工程化、MLOps 平台建设与模型交付。',
 '["MLOps","CI/CD/CT","模型部署","特征平台","监控告警","云原生"]',
 '["MLflow","Kubeflow","Docker","Kubernetes","TensorRT","特征存储","模型注册","A/B平台","监控"]');

-- =========================== 产品经理 (PM) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('C端产品经理', 'PM-C', '产品经理', '产品经理',
 '负责 to C 产品规划与迭代，熟悉用户研究、需求分析与增长运营。',
 '["用户研究","需求分析","数据分析","交互设计","增长运营","项目推进"]',
 '["用户调研","竞品分析","A/B测试","用户画像","留存","转化","MVP","PRD","敏捷","增长黑客"]'),
('B端/企业产品经理', 'PM-B', '产品经理', '产品经理',
 '负责 to B 企业产品规划，熟悉行业分析、客户需求与商业化。',
 '["行业分析","客户需求","商业化","系统设计","项目管理","数据驱动"]',
 '["B端产品","SaaS","企业服务","工作流","权限模型","API设计","私有化","SLA","定制","ROI"]'),
('AI产品经理', 'PM-AI', '产品经理', '产品经理',
 '负责 AI 产品规划，熟悉 LLM 能力边界、Prompt 工程与 AI 评估。',
 '["AI技术理解","LLM能力评估","Prompt工程","AI产品设计","数据策略","伦理合规"]',
 '["LLM","RAG","Agent","Prompt","AI产品","对话设计","评估体系","数据集","安全对齐","算力预估"]');

-- =========================== 数据分析 (DA) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('业务数据分析师', 'DA-BIZ', '数据分析', '数据分析',
 '负责业务数据分析，熟悉 SQL、指标体系与业务洞察。',
 '["SQL","指标体系","业务洞察","统计分析","可视化","报告沟通"]',
 '["SQL","Excel","Tableau","指标体系","漏斗分析","留存","归因","A/B测试","PySpark","业务报告"]'),
('产品数据分析师', 'DA-PROD', '数据分析', '数据分析',
 '负责产品数据分析与实验，熟悉用户行为分析、AB 实验与埋点。',
 '["产品分析","用户行为","AB实验","埋点设计","数据可视化","增长分析"]',
 '["用户行为","AB实验","埋点","留存分析","转化","归因","SQL","Python","Mixpanel","Amplitude"]'),
('BI与数据可视化工程师', 'DA-BI', '数据分析', '数据分析',
 '负责 BI 报表与数据可视化建设，熟悉数仓、ETL 与 BI 工具。',
 '["BI工具","数据仓库","ETL","SQL","可视化设计","数据治理"]',
 '["PowerBI","Tableau","Looker","SQL","dbt","数仓建模","ETL","数据质量","OLAP","数据看板"]');

-- =========================== 软件测试 (QA) ===========================
INSERT INTO job_position (name, code, family, category, description, abilities, keywords) VALUES
('功能测试工程师', 'QA-FUNC', '软件测试', '软件测试',
 '负责软件功能测试，熟悉用例设计、缺陷管理与测试流程。',
 '["用例设计","缺陷管理","测试流程","业务理解","文档撰写","回归测试"]',
 '["黑盒测试","测试用例","Bug管理","Jira","Charles","Postman","兼容性","回归","探索性测试"]'),
('自动化测试工程师', 'QA-AUTO', '软件测试', '软件测试',
 '负责自动化测试框架搭建与脚本开发，熟悉 CI/CD 集成。',
 '["自动化框架","脚本开发","CI/CD集成","接口测试","UI自动化","持续测试"]',
 '["Selenium","Playwright","Appium","pytest","Jenkins","CI/CD","接口自动化","PO模式","数据驱动"]'),
('性能测试工程师', 'QA-PERF', '软件测试', '软件测试',
 '负责性能测试，熟悉压测工具、瓶颈定位与调优建议。',
 '["性能测试","压测工具","瓶颈分析","容量规划","调优建议","监控"]',
 '["JMeter","Gatling","Locust","压测","TPS","响应时间","CPU分析","内存泄漏","Profiling","全链路"]'),
('测试开发/质量工程师', 'QA-SDET', '软件测试', '软件测试',
 '负责测试平台开发与质量保障体系，熟悉测试架构与工具链建设。',
 '["测试平台","工具链","质量度量","代码审查","安全测试","测试架构"]',
 '["测试框架","Mock","契约测试","代码覆盖率","SonarQube","安全测试","Pipeline","质量门禁"]');
