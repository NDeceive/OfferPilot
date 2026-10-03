# 第七阶段：产品接入与真实场景验收

日期：2026-10-04。采用用户选择的 01A 主体、A 字标及英文 Bold 700。已接入本地前端，不涉及发布。

## 接入记录

- 新增 src/components/ui/BrandLogo.vue，使用轮廓 SVG 提供中文、英文、双语和反白组合。登录/注册/找回密码使用双语宽 210 px；桌面学生导航、移动导航、落地页使用中文宽 180 px；教师导航使用英文宽 185 px 并保留教师端标签。
- LogoIcon.vue 共用标准与小尺寸资源，size < 32 使用 micro，其余 standard；inverse 控制反白。标志旁有文字时独立符号为空 alt，组合图片有品牌名称 alt。
- 原 LogoAnimated.vue 收敛为 LogoIcon，避免旧造型在其他入口继续出现；当前没有启用新动画。
- MainLayout 的历史 PNG 引用改为 SVG；历史 NavBar 使用共享符号。两处属于兼容接入，未在当前路由截图中单独覆盖。
- public/favicon.svg 使用绿色 micro；assets/logo.svg 更新为标准绿色。主入口 HTML 原 favicon 引用继续有效。
- src/assets/brand 存放十个项目纯路径资产，不分发字体文件。其他既有未提交业务改动未回退。

## 实际验证

Edge headless + Playwright，在 Vite http://127.0.0.1:5185 上运行。

browser-results.json：落地页、登录、注册、找回密码、390 px 登录、1440 px 学生首页、360/390/430 px 移动首页及教师首页。全部检查组合图片加载成功、页面无横向溢出，运行期 pageerror 为零。已查看桌面首页、移动首页和登录截图，标志与文字保持比例，无裁切。导航场景接口使用测试响应，目的是显示验收，未验证真实账号/后端业务。

favicon 资源 HTTP 200，无 text 或 image。small-dark-results.json：16/24/32/64 px，DPR 1/2/3，共 24 个浅底/深底资源渲染。已查看 DPR1 图，16 px 补偿版可辨双门和入口，但细部仍小，不能宣称所有平台原生标签页已验收。深色为测试容器，不是新增产品深色模式。

npm run build：通过。既有编辑器大块警告仍存在，与此次品牌接入无关。Impeccable 机械检测已运行，针对主要变更组件结果为空数组。git diff --check 针对此轮主要文件通过。

## 边界与后续

本轮完成产品接入和内部显示验证，用户视觉验收尚待确认。未执行发布、原生平台安装图标更新、真实用户识别/记忆实验或商标核验。没有新增产品深色模式。最终资源包沿用 finals/offerpilot-01A-A-bold-v1.zip，使用规范在该包 README 中。

浏览器脚本可复跑；integrate.cjs 是本轮一次性修改记录，不应作为常规构建步骤重跑。截图与结果均保留在本目录。
