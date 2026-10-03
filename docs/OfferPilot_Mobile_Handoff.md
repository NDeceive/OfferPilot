# OfferPilot Mobile 交接说明

## 1. 当前交付状态

当前仓库已经完成：

- Vue 3 移动端独立视觉层。
- `< 768px` 自动进入移动端，桌面端保持原页面。
- M10 首页、M20 专项刷题、M30 面试记录、M40 我的。
- 移动端 Design Tokens、Bottom Navigation、安全区和公共状态组件。
- Capacitor 8 Android 工程。
- Android API 36 构建环境验证。
- 网络、相机和录音 Manifest 权限。
- OfferPilot Android 图标和启动页。
- Debug APK 构建与 APK Signature Scheme v2 校验。

## 2. 当前 APK

```text
文件：OfferPilot-Mobile-debug.apk
包名：com.offerpilot.app
versionCode：1
versionName：1.0
minSdk：24
targetSdk：36
签名：Android Debug
```

该 APK 是前端 Demo，不内置 Spring Boot 或 MySQL。由于尚未配置手机可访问的 `VITE_API_BASE_URL`，登录请求会指向 APK 自身的 `/api`，因此当前无法完成真实登录。

## 3. 重要限制

- 当前 APK 不能作为后续正式升级链的签名基线。
- 正式内部测试前必须生成并永久保存 Release Keystore。
- 当前数据库配置指向开发机 `localhost:3306/zhimian`。
- 手机中的 localhost 不是开发电脑。
- 数字人默认依赖开发机 `127.0.0.1:8010`，真机不可直接使用。
- 当前 Token 保存在 localStorage，需要迁移到 Android 安全存储。
- Android 权限已声明，但仍需实现明确的运行时权限体验和拒绝降级。

## 4. 关键代码位置

```text
前端/src/mobile/                         手机端页面与组件
前端/src/mobile/styles/mobile.css        Mobile Design Tokens
前端/src/mobile/AdaptiveStudentView.vue  Desktop/Mobile 自动切换
前端/src/router/index.js                 路由入口
前端/src/utils/request.js                Axios API Base URL
前端/src/utils/sse.js                    SSE API Base URL
前端/capacitor.config.json               Capacitor 配置
前端/android/                            Android 原生工程
前端/assets/logo.svg                     Android 图标源文件
后端/src/main/resources/application.yml  开发数据库与服务配置
后端/src/main/resources/application-prod.yml 生产配置
```

## 5. 构建依赖

已验证组合：

```text
Node.js 24
npm 11
Java 21
Capacitor 8.5.2
Android SDK Platform 36
Android Build Tools 36.0.0 / 35.0.0
Gradle 8.14.3
```

项目路径包含中文时，Android 工程已配置：

```properties
android.overridePathCheck=true
```

更稳妥的做法仍是将项目放到纯 ASCII 路径。

## 6. 重建 APK

进入 `前端`：

```powershell
npm install
npm run android:sync
cd android
./gradlew.bat assembleDebug
```

输出：

```text
前端/android/app/build/outputs/apk/debug/app-debug.apk
```

Gradle 必须使用 Java 21。Android SDK 路径通过本机 `android/local.properties` 或 `ANDROID_HOME` 配置，交接包不包含机器专属 `local.properties`。

## 7. 接通测试服务器

在构建前配置：

```env
VITE_API_BASE_URL=https://你的域名/api
VITE_DIGITAL_HUMAN_EMBED_URL=https://你的数字人域名/offerpilot-embed.html
```

然后重新执行 `npm run android:sync` 和 Gradle 构建。生产环境不要启用全局明文 HTTP。

## 8. 正式签名

第一份可持续升级的内部版本必须：

1. 生成 Release Keystore。
2. 将 Keystore 密码放在本机安全配置或 CI Secret，禁止提交 Git。
3. 配置 Android release signingConfig。
4. 执行 `assembleRelease`。
5. 使用 `apksigner verify` 校验。
6. 记录 APK SHA-256。
7. 离线备份 Keystore、别名和恢复说明。

## 9. 交接包内容

```text
01_APK/           当前 Demo APK
02_Documents/     本交接说明与后续路线图
03_Source_Changes 本轮新增和修改的核心源代码
CHECKSUMS.sha256  APK 与压缩包外部校验依据
```

## 10. 接手后的第一项工作

不要先继续画页面。第一项工作应是：部署可由手机访问的 HTTPS Spring Boot + MySQL 测试环境，配置 `VITE_API_BASE_URL`，验证注册、登录、Token 恢复和退出完整闭环。
