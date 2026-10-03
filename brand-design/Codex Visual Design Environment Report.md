# Codex Visual Design Environment Report

检查日期：2026-09-28  
环境：Codex Desktop / Windows / Node.js 24 / Python 3.10  
结论：环境装配成功；当前产品 Logo 未被修改。

## 1. 已安装与已接入的 Skills

| Skill | 作用 | 路径 | 状态 |
|---|---|---|---|
| `brand-designer` | 品牌定位、人格、关键词、视觉识别、字体/色彩/图像策略 | `~/.codex/skills/brand-designer` | 新建；验证通过 |
| `logo-design` | 17 步 Logo 工作流、分歧探索、黑白筛选、SVG、字标、配色、场景测试 | `~/.codex/skills/logo-design` | 新建；验证通过 |
| `logo-visual-review` | 16px、黑白、轮廓、AI/SaaS 套路、相似性、长期性与 SVG 卫生检查 | `~/.codex/skills/logo-visual-review` | 新建；验证通过 |
| `image-to-svg` | PNG/JPG 到真实 SVG path、优化、检查和预览 | `~/.codex/skills/image-to-svg` | 新建；可执行测试通过 |
| `visual-resource-library` | Iconify 图标检索、Fontsource 字体检索、参考库和许可证边界 | `~/.codex/skills/visual-resource-library` | 新建；在线检索测试通过 |
| `base-logo-generator` | Logo 概念、符号方向、提示词和品牌板生成 | `~/.codex/skills/base-logo-generator` | 从 SanbaoAI 安装；MIT；验证通过 |
| `logo-colorway-generator` | 已通过黑白审查的 Logo 配色与场景色板 | `~/.codex/skills/logo-colorway-generator` | 从 SanbaoAI 安装；MIT；验证通过 |
| `imagegen` | Logo/品牌位图概念探索和图像编辑 | 内置系统 Skill | 已存在 |
| `impeccable` | UI 层级、排版、间距、Token、响应式、无障碍和视觉 QA | `~/.codex/skills/impeccable` | 已存在；覆盖 frontend-design / ui-ux 核心需求 |
| `baoyu-design` | 设计系统、品牌板、交互原型和 HTML 设计产物 | `~/.codex/skills/baoyu-design` | 已存在 |
| `offerpilot-brand-design` | 本项目产品事实、审美边界、禁用套路、Logo/UI 约束 | `.codex/skills/offerpilot-brand-design` | 新建；验证通过 |

所有新增/安装 Skill 均通过 OpenAI `skill-creator` 的 `quick_validate.py`。Windows 中文路径验证时启用了 `PYTHONUTF8=1`；验证器依赖的 PyYAML 6.0.3 已安装。

## 2. 统一工作流

默认链路为：

品牌事实与定位 → 3-5 个关键词 → 参考规律研究 → 至少 5-6 个差异化路线 → 16-30 个黑白草案 → 强制视觉审查 → 5-6 个方向 → 2-3 个深化方向 → 最终候选 → SVG path 化 → 几何/光学校正 → Wordmark → 字体组合 → Color System → 产品场景压力测试 → 最终资产与品牌指南。

每轮进入下一阶段前必须通过 `logo-visual-review`。出现关键失败项时回退一轮。

## 3. 项目工作区

`brand-design/` 已建立以下内容：

- `brief/`：产品、受众、定位和竞品研究框架。
- `references/`：liked、disliked、geometric、monogram、negative-space、wordmark、abstract。
- `assets/`：fonts、icons、illustrations。
- `explorations/`：round-01、round-02、round-03。
- `tests/`：favicon、navbar、app-icon、dark-background、light-background。
- `final/`：只允许放置通过审查的生产资产；当前为空。
- `ASSET_LICENSES.md`：素材来源、许可证、商业使用、修改、再分发和商标风险台账。

## 4. 图标库

通过 Iconify Search API 限定检索：

- Lucide — ISC
- Tabler — MIT
- Phosphor — MIT
- Iconoir — MIT
- IconPark Outline — Apache-2.0
- Remix Icon — Apache-2.0
- Heroicons — MIT

检索脚本：

`node ~/.codex/skills/visual-resource-library/scripts/search-icons.mjs <query> [limit]`

脚本返回具体图标名、作者、集合许可证和许可证 URL。图标只用于 UI 与几何/线宽/视觉重量研究，不得轻微修改后作为产品 Logo。

## 5. 字体库

Fontsource API 已接入为元数据检索层，不进行无目的批量下载：

`node ~/.codex/skills/visual-resource-library/scripts/search-fonts.mjs <query> [limit]`

返回 family、分类、字重、样式、可变字体、语言 subset、包名和详情页。工作流明确区分 Symbol Mark、Wordmark、Display Typography 和 UI Typography；不会默认使用 Inter。任何字体落地前仍需核验该 family 的 SIL OFL / Apache 或其他具体许可证，并把许可证文件与字体一同保存。

## 6. Logo Reference Library

已建立分类目录与使用规则，未批量抓取商标素材。

- `gilbarbara/logos`：仓库许可证标记 CC0-1.0；只用于构图/几何/字标/负空间研究。
- Simple Icons：数据 CC0-1.0；项目已存在 `simple-icons` 依赖，但品牌名称与图形仍有商标和品牌规范风险。

版权许可不等于商标许可。任何参考图均不得被复制、描摹或轻微变形为新 Logo。

## 7. 插画资源

已批准为“按需评估、未下载”的候选：

- Open Doodles — 官方说明为 CC0。
- Humaaans — 官方站说明为 CC0，可个人与商业使用。

下载包若出现新增条款，必须以包内许可证复核并更新台账。

## 8. Image Generation 能力

内置 `imagegen` 可用于：

- 黑白概念草案与多方向探索。
- 品牌板、App Icon、Favicon 和场景概念。
- 基于参考图的风格/结构研究。

限制：

- 图像生成结果默认是位图概念，不代表可交付矢量。
- 不用 Mockup、渐变、发光或 3D 掩盖结构缺陷。
- 不把生成图当成原创性或商标安全证明。

## 9. SVG 能力

`image-to-svg` 使用本地工具链：

- `@neplex/vectorizer 0.1.0` — 基于 VTracer，MIT。
- `svgo 4.1.0` — MIT。
- `@resvg/resvg-js 2.6.2` — MPL-2.0，仅用于预览渲染。

能力包括真实 SVG path、黑白/海报式描摹、SVGO 优化、自动补齐 viewBox、路径/命令计数、嵌入位图与脚本检查、PNG 预览。最终几何仍必须人工检查曲线、圆角、节点、对齐和 optical compensation。

## 10. Visual Review 能力

强制检查：

- 16px、32px、64px 识别度。
- 黑白、深浅背景和单色印刷。
- 是否依赖渐变、光效、阴影、3D。
- 是否像通用 SaaS、Crypto、Web3、AI Startup 或图库图标。
- 细节、负空间、轮廓、平衡、记忆点、品牌关联。
- 与知名品牌的混淆风险。
- SVG 是否含嵌入位图、脚本或节点垃圾。

评分低于 75/100 或存在关键失败项时不得进入下一轮。

## 11. 许可证情况

- SanbaoAI 两个安装 Skill：MIT；上游许可文本已分别保存为 `LICENSE.upstream.txt`。
- 自建 Skills：本地项目工作流说明，未引入第三方素材内容。
- VTracer Node 封装与 SVGO：MIT。
- Resvg JS：MPL-2.0。
- Iconify：平台软件许可证不覆盖每套图标；脚本返回集合级许可证。
- Fontsource：平台代码 MIT，字体逐 family 核验。
- Logo 数据集：即使数据为 CC0，商标权仍独立存在。
- Open Doodles / Humaaans：CC0 候选，当前未下载。

详细资产状态见 `brand-design/ASSET_LICENSES.md`。该记录不是法律意见。

## 12. 未接入或仍缺失的能力

- 未安装 `ui-ux-pro-max`：与现有 `impeccable` 高度重叠，避免路由冲突和冗余。
- 未安装无明确许可证的 `brand-design-skill`：仓库未声明许可证，且其流程偏向位图终稿，不符合最终 SVG path 要求。
- 未安装完整 `imagetosvg-mcp`：依赖 MCP、MuPDF、Sharp 等较重；已用其核心同类 VTracer + SVGO + Resvg 组成更轻的本地方案。
- 未安装 Mascot Logo Skill：当前目标不依赖吉祥物，按需再装。
- 尚无字体 outline/字偶距专用 GUI、专业曲线编辑器或自动商标清查数据库。
- 商标清查和法律可注册性仍需专业工具与法律审查。
- 自动描摹不能替代高质量几何重建。

## 13. 安装是否成功

成功。8 个本轮新增/安装 Skill 全部通过结构验证；图标与字体检索通过；SVG 依赖安装、转换、检查和渲染通过；项目目录和许可证台账已建立。新安装的全局 Skill 会在后续 Codex turn/session 中被发现。

## 14. 简单工作流测试

测试素材为与 OfferPilot 无关的“圆形 + 右箭头”几何样本，不是 Logo 方案。

测试产物位于 `brand-design/tests/workflow-smoke/`：

- `source-shapes.svg`
- `source-shapes.png`
- `traced-shapes.svg`
- `traced-shapes-preview.png`

检查结果：

- 1 条真实 path，9 个路径命令。
- 有 viewBox。
- 0 个嵌入 image、0 个 data URI、0 个 script、0 个 foreignObject、0 个 filter。
- 轮廓总体保持，但箭头硬角被描摹为略圆曲线；因此工具链通过，Visual Review 正确判定“需要人工几何精修后才能作为最终资产”。
- Iconify 检索成功返回图标与 MIT / ISC / Apache-2.0 元数据。
- Fontsource 检索成功返回 Latin 与中文候选的 family、字重、subset 和包信息。

本次到此停止，未开始重新设计 OfferPilot Logo。

## 参考来源

- OpenAI Skills：https://developers.openai.com/plugins/concepts/skills
- OpenAI Build Skills：https://developers.openai.com/plugins/build/skills
- SanbaoAI Logo Generator：https://github.com/SanbaoAI/logo-generator-skill
- VTracer：https://github.com/visioncortex/vtracer
- Neplex Vectorizer：https://github.com/neplextech/vectorizer
- SVGO：https://svgo.dev/
- Iconify collections：https://icon-sets.iconify.design/
- Fontsource API：https://fontsource.org/docs/api/introduction
- Open Doodles license：https://www.opendoodles.com/about
- Humaaans license：https://www.humaaans.com/
