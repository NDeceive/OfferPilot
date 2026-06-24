# 智面幻境 · 智能模拟面试系统

> 基于沉浸式数字人交互与动态追问的智能模拟面试系统
> 天津工业大学 · 软件学院 · 大创项目

## 项目简介

面向大学生求职面试训练，提供「岗位选择 → 简历录入 → 数字人面试官提问 → 动态追问 → 多维评估报告 → 专项复训」的完整闭环。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 后端 | Java 17 + Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8 |
| AI | DeepSeek（OpenAI 兼容接口）+ RAG 检索 |

## 目录结构

```
大创/
├── 后端/             后端 Spring Boot 工程
├── 前端/             前端 Vue 3 工程
├── docs/             项目文档（需求/设计/测试/接口）
└── README.md
```

## 快速启动

### 1. 数据库

确保本地 MySQL 8 已启动，然后执行建表脚本：

```bash
mysql -u root -p < 后端/src/main/resources/db/schema.sql
```

### 2. 后端

```bash
cd 后端
# 在 application.yml 中填写你的数据库密码（或设置环境变量 DB_PASSWORD）
./mvnw spring-boot:run
```

后端启动在 http://localhost:8080

### 3. 前端

```bash
cd 前端
npm install
npm run dev
```

前端启动在 http://localhost:5173

## 团队分工

| 成员 | 负责 |
|------|------|
| 周祉佑 | 项目负责人 / 后端 / 系统集成 |
| 邹宜辰 | 前端 / 数字人交互 |
| 秦佳梦 | 题库 / 测试 |
| 林志鹏 | 语音模块 |
| 蔡云龙 | AI 算法（动态追问 / 评分）|

## 开发进度

详见 [docs/进度看板.md](docs/进度看板.md)
