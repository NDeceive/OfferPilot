---
version: 1
slug: "src-views-teacher-trainingtasks-vue"
primary_target: "前端/src/views/teacher/TrainingTasks.vue"
related_targets: ["前端/src/assets/styles/training-tasks.css","前端/src/components/teacher/TaskExecutionTable.vue"]
---

# 教师端训练任务

Operate 模式；服务教师配置任务、跟进执行与再次干预。按用户 V1 包确定的 T09–T12 / C01–C05 布局扩展现有教师壳。

入口为紧凑任务宽行，唯一主操作创建训练任务；创建流程五步配右摘要；工作台用三 Tab 与学生执行表；模板用表格复用目标、内容、规则，不复制训练对象。

文字及 FINAL_OVERRIDES 优先于图片。状态靠文字、数字、边框与浅选中底色，最近动态按日期组织；不恢复装饰圆点、图标或条纹。

后端暂无任务契约。真实模式显示不可用说明；演示模式使用统一 JSON 基线和按账号隔离的本地保存。未提供的数据不伪造结论。用户应能从结果选择学生回到新任务，模板复用仍要求重新选择班级。

后续接入服务器事务、权限校验、通知、题库失效检查。交付与验证记录见 docs/TrainingTasksFrontend.md。

用户确认精简顺序（2026-10-03）：状态和时间属性分离；结果比较只显示同任务同目标同评分口径的原始分差；未知和不可比较须解释；删除重复统计与摘要；高级筛选、配置、重复规则按需展开；选中学生后再显示批量操作。保持现有白底、绿色品牌、无彩色状态圆点。
