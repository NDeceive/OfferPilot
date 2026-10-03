# OfferPilot 手机端后续改造方案

## 1. 已确认目标

- 产品允许联网运行。
- 当前没有可用云服务器。
- 第一阶段采用内部 APK 分发，不上架应用商店。
- 桌面端和手机端共享业务后端、数据库、账号和数据模型。
- 手机端必须先解决真实登录与数据连接，再扩展完整 AI 面试链。

## 2. 目标架构

```text
Android APK（Vue 3 + Capacitor）
        │ HTTPS / JWT
        ▼
Nginx 或 Caddy
        │
        ▼
Spring Boot API ───── MySQL 8
        │              │
        ├── 简历文件存储与备份
        ├── 大模型 / ASR
        └── 数字人会话控制

Android APK ◀── WebRTC/TLS ──▶ GPU 数字人服务 + TURN/STUN
```

APK 不内置 MySQL、Spring Boot、AI Key 或数据库密码。所有业务数据统一保存到云端，APK 仅保存必要的安全凭据和少量可恢复缓存。

## 3. 基础设施

### 内部测试环境

第一版使用一台 Linux 云服务器，通过 Docker Compose 运行：

- HTTPS 反向代理。
- Spring Boot 后端。
- MySQL 8。
- 数据库定时备份。
- 简历文件持久化目录或兼容 S3 的对象存储。
- 健康检查、访问日志和日志轮转。

第一版不引入 Redis、Kubernetes 或微服务。数字人需要 GPU，应在核心业务稳定后部署到独立 GPU 服务器。

### 建议域名

```text
api.example.com       业务 API
files.example.com     简历与报告文件
human.example.com     数字人 / WebRTC
download.example.com  内部 APK 下载
```

### 客户端环境变量

```env
VITE_API_BASE_URL=https://api.example.com/api
VITE_DIGITAL_HUMAN_EMBED_URL=https://human.example.com/offerpilot-embed.html
```

开发、内部测试和正式环境必须使用不同配置，禁止把密钥写入 APK。

## 4. 登录与安全

目标登录链：

```text
账号密码 → HTTPS API → MySQL 验证 → Access Token + Refresh Token
→ Android Keystore 安全保存 → 自动恢复登录
```

需要完成：

- 新增 Refresh Token、`/auth/refresh` 和 `/auth/logout`。
- Access Token 短期有效，Refresh Token 可撤销并绑定设备。
- Token 从 localStorage 迁移到 Android 安全存储。
- 登录限流、失败审计、账号禁用和服务端主动失效。
- 忘记密码或管理员重置能力。
- 退出登录只清理 OfferPilot 自身数据，不再调用 `localStorage.clear()`。
- 密码继续使用 BCrypt；JWT、数据库密码和 AI Key 通过环境变量注入。

## 5. 数据库与文件

MySQL 至少覆盖：

- 用户、角色、登录设备和 Refresh Token。
- 岗位、技能标签、简历与文件元数据。
- 面试会话、问题、回答、评分和报告。
- 专项训练进度、错题、收藏和训练记录。
- APP 版本、强制升级策略和审计日志。

要求：

- 使用 Flyway 或等价方案管理版本化迁移。
- 数据库不开放公网端口。
- 每日自动备份并定期验证恢复。
- 简历文件不直接写入 MySQL，只保存文件地址、哈希和元数据。
- 上传时检查扩展名、MIME、文件头和大小。

## 6. 页面交付范围

### P1：账号与四个一级页

- 登录、注册、登录恢复和退出。
- M10 首页。
- M20 专项刷题。
- M30 面试记录。
- M40 我的。
- 简历上传和选择。
- 统一加载、错误、断网和空状态。
- APP 版本检查。

### P2：完整面试任务链

- M11 创建面试。
- M12 面试确认。
- M13 AI 虚拟面试。
- M14 报告生成。
- M15 面试报告。
- M16 面试押题。
- M17 押题结果。
- M21 专项详情。
- M22 模拟问答。
- M23 编程训练。

一级页显示 Bottom Navigation；任务链、详情、设置和面试页隐藏 Bottom Navigation。

## 7. 网络与离线策略

- 所有生产通信使用 HTTPS/WSS。
- 首页可缓存最近一次成功加载的数据用于只读展示。
- 网络错误必须说明原因并提供重试。
- 正在提交的回答只保留有限本地队列，恢复网络后由用户确认重试。
- 面试中断时保存题号、计时和已提交答案。
- 不缓存密码、完整录音或敏感简历正文。
- SSE 支持取消、超时、切后台中断和重新连接提示。

## 8. 麦克风、摄像头和文件权限

- 仅在用户点击相关功能时申请麦克风或摄像头权限。
- 权限拒绝后允许使用文字回答或纯语音模式。
- 永久拒绝时提供系统设置入口。
- 切后台和面试结束时立即释放音视频轨道。
- 简历上传使用 Android 系统文件选择器并兼容 `content://` URI。
- 支持 PDF、DOC、DOCX 和 10MB 限制。

## 9. AI、ASR 与数字人

### AI / ASR

- 模型和 ASR 只能由后端调用，APK 不保存供应商 Key。
- 录音按短片段上传，失败时允许文字输入。
- 报告生成采用状态查询，不让客户端无限等待。

### 数字人降级链

```text
WebRTC 数字人
→ 视频回退
→ 静态头像 + TTS
→ 纯文字面试
```

完整数字人环境需要 GPU、HTTPS/WSS、TURN/STUN、会话并发限制、超时回收和监控。数字人故障不得阻塞面试核心流程。

## 10. 内部 APK 更新

后端增加：

```text
GET /api/app/version?platform=android
```

返回版本号、最低版本、下载地址、SHA-256、是否强制更新和更新说明。APK 启动后执行版本检查：

- 无更新时静默进入。
- 可选更新时展示说明。
- 强制更新时阻止旧版本继续进入。
- 下载交给系统浏览器或下载器，安装由 Android 系统确认。

所有正式内部 APK 必须使用相同的 Release Keystore 和相同包名 `com.offerpilot.app`。Keystore 需离线备份；丢失后无法对现有安装执行覆盖升级。

## 11. 分阶段计划

| 阶段 | 主要结果 | 验收条件 |
|---|---|---|
| P0 | 云服务器、域名、HTTPS、后端、MySQL、备份 | 手机网络可访问健康检查 |
| P1 | 登录、刷新、退出、安全存储 | APK 重启后可恢复登录 |
| P2 | M10/M20/M30/M40 接真实数据 | 不使用生产 Mock，断网可恢复 |
| P3 | M11–M17、录音和报告 | 面试中断可恢复，权限拒绝可降级 |
| P4 | M21–M23 专项训练 | 训练进度和记录真实入库 |
| P5 | GPU 数字人、WebRTC、TURN | Wi-Fi和移动网络均可完成面试 |
| P6 | Release 签名、版本检查、下载页 | 新 APK 可覆盖安装旧版本 |

## 12. 验收矩阵

- Android 7、10、12、14及目标新版本。
- 360、375、390、412、430dp。
- Wi-Fi、移动网络、断网和网络切换。
- 首次权限、拒绝、永久拒绝和权限撤销。
- 登录过期、账号禁用、密码错误和服务器不可达。
- PDF/DOC/DOCX 上传。
- 切后台、锁屏、来电中断和进程恢复。
- API、AI、ASR、数字人分别故障时的降级。
- 旧版本覆盖安装和强制升级。
- 桌面 Web 端完整回归。

## 13. 推荐执行顺序

1. 先建设云端业务后端和 MySQL。
2. 将四个一级页全部接入真实数据。
3. 生成第一份正式签名的内部 APK。
4. 完成 M11–M17 核心面试链和权限处理。
5. 最后建设 GPU 数字人、TURN 和弱网恢复。

