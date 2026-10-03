# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

求职准备中的学生与初入职场者，在手机或桌面浏览器中进行岗位准备、专项刷题、模拟面试和复盘。

## Product Purpose

OfferPilot（智面幻境）把岗位、简历、训练与面试反馈串成持续练习闭环。成功意味着用户能快速开始下一次有针对性的训练，并看见真实训练记录与改进方向。

## Positioning

围绕用户真实岗位、简历和历次面试反馈生成下一步训练，而不是提供孤立的通用题库。

## Operating Context

用户会在桌面端进行完整操作，也会在 360–430px 宽度的手机浏览器或未来 Android WebView 中使用一级入口。核心资料包括登录账号、岗位、简历、面试记录、报告与学习资源。

## Capabilities and Constraints

- Vue 3 Web 产品；移动端与桌面端共享 Router、Pinia Store、Axios API 与业务数据。
- 当前阶段只交付 M10 首页、M20 专项刷题、M30 面试记录、M40 我的及移动设计基础。
- 不改变现有接口，不复制 Desktop/Mobile 业务层，不提前实现 M11–M23 或 APK 打包。
- 移动端小于 768px 自动启用，桌面端原视觉与功能必须保持。
- 未来需兼容 Capacitor Android、麦克风、WebRTC、文件上传与安全区。

## Brand Commitments

- 正式名称为 OfferPilot / 智面幻境，复用项目现有 Logo 与用户资源。
- 产品语气务实、温和、鼓励行动，不使用夸张 AI 科技叙事。
- 用户提供的移动端预览图是布局、比例、层级和视觉基调的约束，不是业务数据来源。

## Evidence on Hand

- 真实 API：用户、简历、岗位、首页概览、面试记录、报告与模块偏好。
- 本地学习资源适配层：`前端/src/services/learningResources.js`，含 API 岗位回退与演示训练会话。
- 现有品牌和头像资源：`前端/src/assets/generated/`。
- 没有可作为生产数据的客户案例、成绩承诺或预览图示例人物；不得杜撰。

## Product Principles

- 下一步行动优先于信息堆叠。
- 真实数据优先，缺失时诚实展示空状态。
- 移动视觉独立，业务逻辑共享。
- 触控可达、中文可读、低认知负担。
- 为未来原生容器保留安全区与权限边界。

## Accessibility & Inclusion

交互目标不小于 44px，支持键盘焦点、减少动态效果、足够文本对比度，并在 360–430px 宽度下避免截断与横向滚动。
