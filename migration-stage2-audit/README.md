# OfferPilot 2.0 后端迁入审计

第二阶段已将 `offerpilot2.0` 当前磁盘中的后端迁入本项目，并只通过新增文件恢复数字人、智谱语音识别和首页聚合能力。

## 不可变基线

- 2.0 基线文件：153
- 正式后端基线不匹配：0
- 2.0 源目录基线不匹配：0
- 2.0 原有文件没有被修改

## 新增能力

- `/api/dashboard/overview`：首页聚合数据
- `/api/speech/transcribe`：智谱语音识别
- 数字人服务检测、自动启动和关闭
- 9 个岗位 Logo 静态资源
- `application.properties`：以新增配置文件映射运行环境变量，不修改 2.0 的 `application.yml`

运行时环境变量：

- `AI_API_KEY`：智能追问、评分和提升建议
- `AI_ENABLED`：是否启用 AI，默认 `true`
- `ZHIPU_API_KEY`：智谱语音识别
- `DB_PASSWORD`：MySQL 密码

## 验证

- Java 源文件编译：124 个
- 测试：2 个通过，0 失败
- 新增文件：21 个
- 原后端回滚目录：`migration-stage2-rollback-backend`

在前端接口联调和数据库迁移完成前，不删除回滚目录。
