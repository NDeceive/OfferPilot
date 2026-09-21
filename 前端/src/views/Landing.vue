<template>
  <div
    class="landing"
    :style="{
      '--journey-shift': `${ambientShift}px`,
      '--journey-shift-reverse': `${ambientShift * -0.65}px`,
      '--journey-shift-soft': `${ambientShift * 0.45}px`,
    }"
    @mousemove="onGlobalMouseMove"
  >
    <!-- Scroll Progress -->
    <div class="scroll-progress"><div class="scroll-progress-bar" :style="{ transform: `scaleX(${scrollPercent / 100})` }"></div></div>

    <!-- Navbar -->
    <nav class="nav" :class="{ 'nav-hidden': !navVisible, 'nav-scrolled': isScrolled }">
      <div class="nav-inner">
        <div class="nav-logo">
          <LogoIcon :size="26" />
          <span class="nav-brand">
            <span class="brand-cn">智面幻境</span>
            <span class="brand-en">OfferPilot</span>
          </span>
        </div>
        <div class="nav-links">
          <a href="#features">功能</a>
          <a href="#process">流程</a>
          <a href="#stats">数据</a>
        </div>
        <div class="nav-actions">
          <router-link to="/login" class="btn-nav-ghost">登录</router-link>
          <router-link to="/login" class="btn-nav-accent">免费注册</router-link>
        </div>
      </div>
    </nav>

    <!-- Hero -->
    <section
      ref="heroRef"
      class="hero"
      @pointermove="onHeroPointerMove"
      @pointerenter="onHeroPointerEnter"
      @pointerleave="onHeroPointerLeave"
    >
      <div class="hero-bg">
        <div class="hero-gradient"></div>
        <div class="hero-dots"></div>
      </div>

      <div class="hero-insight-layer" :class="{ visible: spotlightVisible }" :style="spotlightMaskStyle" aria-hidden="true">
        <div class="hero-insight-wash"></div>
        <svg class="hero-insight-map" viewBox="0 0 1440 900" preserveAspectRatio="xMidYMid slice">
          <g class="insight-links">
            <path d="M90 650 C260 510 360 600 505 430 S770 260 925 380 S1170 590 1370 280"/>
            <path d="M160 250 C350 330 410 190 590 300 S870 560 1060 480 S1250 300 1390 420"/>
            <path d="M310 820 C430 700 575 760 700 620 S920 700 1100 610"/>
          </g>

          <g class="insight-blueprint" transform="translate(930 150)">
            <rect width="360" height="214" rx="18"/>
            <line x1="0" y1="48" x2="360" y2="48"/>
            <circle cx="24" cy="24" r="4"/>
            <circle cx="40" cy="24" r="4"/>
            <circle cx="56" cy="24" r="4"/>
            <rect x="28" y="75" width="198" height="11" rx="5.5"/>
            <rect x="28" y="98" width="272" height="7" rx="3.5"/>
            <rect x="28" y="116" width="238" height="7" rx="3.5"/>
            <rect x="28" y="153" width="82" height="34" rx="9"/>
            <rect x="120" y="153" width="82" height="34" rx="9"/>
            <rect x="212" y="153" width="116" height="34" rx="9"/>
          </g>

          <g class="insight-blueprint insight-blueprint-report" transform="translate(130 460)">
            <rect width="300" height="176" rx="16"/>
            <circle cx="83" cy="88" r="45"/>
            <path d="M83 45 L112 78 L102 120 L61 124 L39 82 Z"/>
            <rect x="154" y="48" width="112" height="7" rx="3.5"/>
            <rect x="154" y="74" width="92" height="7" rx="3.5"/>
            <rect x="154" y="100" width="124" height="7" rx="3.5"/>
            <rect x="154" y="126" width="78" height="7" rx="3.5"/>
          </g>

          <g class="insight-node" transform="translate(215 280)">
            <circle r="9"/><circle r="3"/><text x="18" y="5">简历技能</text>
          </g>
          <g class="insight-node" transform="translate(505 430)">
            <circle r="11"/><circle r="3.5"/><text x="20" y="5">项目表达</text>
          </g>
          <g class="insight-node" transform="translate(700 620)">
            <circle r="9"/><circle r="3"/><text x="18" y="5">逻辑结构</text>
          </g>
          <g class="insight-node" transform="translate(925 380)">
            <circle r="12"/><circle r="4"/><text x="22" y="5">追问路径</text>
          </g>
          <g class="insight-node insight-node-warm" transform="translate(1100 610)">
            <circle r="12"/><circle r="4"/><text x="22" y="5">岗位匹配</text>
          </g>
          <g class="insight-node" transform="translate(1290 320)">
            <circle r="9"/><circle r="3"/><text x="-98" y="5">能力反馈</text>
          </g>
        </svg>
        <span class="insight-hint">移动光标，查看回答背后的能力结构</span>
      </div>

      <div class="hero-wrap">
        <div class="hero-content">
          <div class="hero-text">
            <div class="hero-badge">
              <span class="badge-pulse"></span>
              AI 驱动, 下一代面试训练
            </div>

            <h1 class="hero-title">
              <span class="typewriter-line">{{ typedLine1 }}<span class="tw-cursor" v-if="twPhase === 0">|</span></span><br />
              <span class="typewriter-line">{{ typedLine2 }}<span class="tw-cursor" v-if="twPhase === 1">|</span></span>
            </h1>

            <p class="hero-desc">
              上传简历, 匹配目标岗位, AI 实时追问并生成多维评估报告。每一次模拟练习都在缩短你和真实 Offer 的距离。
            </p>

            <div class="hero-cta">
              <router-link to="/login" class="btn btn-primary btn-lg">
                免费开始
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
              </router-link>
              <a href="#features" class="btn btn-ghost btn-lg">了解更多</a>
            </div>

            <div class="hero-proof">
              <span class="proof-item">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2.5"><path d="M20 6 9 17l-5-5"/></svg>
                免费使用
              </span>
              <span class="proof-dot"></span>
              <span class="proof-item">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2.5"><path d="M20 6 9 17l-5-5"/></svg>
                无需下载
              </span>
              <span class="proof-dot"></span>
              <span class="proof-item">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2.5"><path d="M20 6 9 17l-5-5"/></svg>
                即刻体验
              </span>
            </div>
          </div>

          <div class="hero-visual">
            <!-- Simulated Interview Interface -->
            <div class="mock-window" ref="mockWindowRef" @mousemove="onMockMouseMove" @mouseleave="onMockMouseLeave" :style="mockTiltStyle">
              <div class="mock-titlebar">
                <div class="mock-dots">
                  <span class="dot-r"></span>
                  <span class="dot-y"></span>
                  <span class="dot-g"></span>
                </div>
                <span class="mock-title">AI 模拟面试</span>
                <span class="mock-timer">24:36</span>
              </div>
              <div class="mock-body">
                <div class="mock-chat">
                  <!-- AI Message -->
                  <div class="mock-msg mock-ai">
                    <div class="mock-avatar-ai">AI</div>
                    <div class="mock-bubble-ai">
                      <span class="mock-followup">追问</span>
                      <p>你提到使用了 Vue 3 的 Composition API, 能具体说说和 Options API 相比, 在这个项目中它带来了哪些优势吗?</p>
                    </div>
                  </div>
                  <!-- User Message -->
                  <div class="mock-msg mock-user">
                    <div class="mock-bubble-user">
                      <p>好的, Composition API 让我可以把相关逻辑组织在一起, 比如把所有的表格搜索、分页、排序逻辑抽成一个 useTable 的 composable 函数...</p>
                    </div>
                    <div class="mock-avatar-user">张</div>
                  </div>
                  <!-- AI Evaluation -->
                  <div class="mock-msg mock-ai">
                    <div class="mock-avatar-ai">AI</div>
                    <div class="mock-bubble-ai mock-eval">
                      <div class="mock-eval-row">
                        <span class="mock-eval-label">技术深度</span>
                        <div class="mock-eval-bar"><div class="mock-eval-fill" style="width: 85%"></div></div>
                        <span class="mock-eval-val">85</span>
                      </div>
                      <div class="mock-eval-row">
                        <span class="mock-eval-label">逻辑表达</span>
                        <div class="mock-eval-bar"><div class="mock-eval-fill" style="width: 72%"></div></div>
                        <span class="mock-eval-val">72</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- Floating Score Card -->
            <div class="float-card float-card-score">
              <span class="float-value">92</span>
              <span class="float-label">综合得分</span>
            </div>
            <!-- Floating Improvement Card -->
            <div class="float-card float-card-improve">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2.5"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
              <span class="float-text">能力提升 +15%</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Scroll-linked interview journey -->
    <div class="journey-layer" aria-hidden="true">
      <div class="ambient-orb ambient-orb-one"></div>
      <div class="ambient-orb ambient-orb-two"></div>
      <div class="ambient-orb ambient-orb-three"></div>

      <svg class="journey-path" viewBox="0 0 1200 2400" preserveAspectRatio="none">
        <path class="journey-path-base" pathLength="1" d="M1060 0 C940 260 1100 430 900 650 C690 880 1010 1030 780 1270 C570 1490 870 1690 650 1890 C520 2010 620 2220 470 2400"/>
        <path class="journey-path-active" pathLength="1" :style="{ strokeDashoffset: 1 - journeyProgress }" d="M1060 0 C940 260 1100 430 900 650 C690 880 1010 1030 780 1270 C570 1490 870 1690 650 1890 C520 2010 620 2220 470 2400"/>
        <g class="journey-node" :class="{ active: journeyProgress > 0.18 }" transform="translate(916 630)">
          <circle r="13"/><circle r="4"/>
        </g>
        <g class="journey-node" :class="{ active: journeyProgress > 0.42 }" transform="translate(806 1250)">
          <circle r="13"/><circle r="4"/>
        </g>
        <g class="journey-node" :class="{ active: journeyProgress > 0.68 }" transform="translate(668 1870)">
          <circle r="13"/><circle r="4"/>
        </g>
        <g class="journey-node journey-node-final" :class="{ active: journeyProgress > 0.88 }" transform="translate(470 2390)">
          <circle r="17"/><circle r="5"/>
        </g>
      </svg>

      <div class="journey-fragment fragment-role" :class="{ visible: journeyProgress > 0.14 }">
        <span>目标岗位</span><strong>Java 后端</strong>
      </div>
      <div class="journey-fragment fragment-skill" :class="{ visible: journeyProgress > 0.4 }">
        <span>技能画像</span><strong>项目表达 · 已识别</strong>
      </div>
      <div class="journey-fragment fragment-report" :class="{ visible: journeyProgress > 0.66 }">
        <span>训练反馈</span><strong>提升建议已生成</strong>
      </div>
    </div>

    <!-- Features (Bento) -->
    <section id="features" class="features-section">
      <div class="section-inner">
        <div class="section-header">
          <h2 class="section-title">不只是问答, 是完整的训练闭环</h2>
          <p class="section-desc">从简历分析到能力评估, OfferPilot 覆盖面试准备的每个环节</p>
        </div>

        <div class="bento-grid">
          <div class="bento-left">
            <div class="bento-card bento-card-ai" data-reveal @mousemove="onCardSpotlight" @mouseleave="onCardLeave">
              <div class="bento-icon bento-icon-ai">
                <svg aria-hidden="true"><use href="/icons.svg#offerpilot-interview"></use></svg>
              </div>
              <h3 class="bento-title">AI 追问引擎</h3>
              <p class="bento-desc">不是简单的一问一答。AI 实时分析你的回答, 动态生成深度追问, 帮你训练临场应变能力。</p>
              <div class="bento-tag-row">
                <span class="bento-tag">追问分析</span>
                <span class="bento-tag">实时生成</span>
              </div>
            </div>

            <div class="bento-card bento-card-resume" data-reveal @mousemove="onCardSpotlight" @mouseleave="onCardLeave">
              <div class="bento-icon bento-icon-resume">
                <svg aria-hidden="true"><use href="/icons.svg#offerpilot-resume"></use></svg>
              </div>
              <h3 class="bento-title">智能简历分析</h3>
              <p class="bento-desc">上传简历, AI 自动提取技能标签和项目经历, 为你生成个性化面试方案。</p>
            </div>
          </div>

          <div class="bento-card bento-card-report" data-reveal @mousemove="onCardSpotlight" @mouseleave="onCardLeave">
            <div class="report-card-head">
              <div>
                <div class="bento-icon bento-icon-report">
                  <svg aria-hidden="true"><use href="/icons.svg#offerpilot-report"></use></svg>
                </div>
                <h3 class="bento-title">多维能力报告</h3>
                <p class="bento-desc">五维评估不只给出分数，更帮你看清优势与下一步提升方向。</p>
              </div>
              <span class="report-demo-badge">报告示意</span>
            </div>

            <div class="report-preview">
              <div class="bento-img-wrap">
                <svg viewBox="0 0 160 160" class="bento-radar" role="img" aria-label="五维能力雷达图示意">
                <!-- Grid -->
                  <polygon v-for="s in [0.3,0.55,0.8]" :key="s" :points="radarGridPoints(s)" fill="none" :stroke="s===0.8?'#84cbb0':'#b9dfd0'" stroke-width="1"/>
                <!-- Axis -->
                  <line v-for="(_, i) in 5" :key="i" :x1="80" :y1="80" :x2="radarAngles[i].x*60+80" :y2="radarAngles[i].y*60+80" stroke="#b9dfd0" stroke-width="0.8"/>
                <!-- Data -->
                  <polygon :points="landingRadarData" fill="rgba(11,107,82,0.18)" stroke="#0b6b52" stroke-width="2" stroke-linejoin="round"/>
                <!-- Dots -->
                  <circle v-for="(p, i) in landingRadarDots" :key="i" :cx="p.x" :cy="p.y" r="3.5" fill="#0b6b52"/>
                <!-- Labels -->
                  <text v-for="(l, i) in ['表达','逻辑','技术','匹配','抗压']" :key="i" :x="radarAngles[i].x*78+80" :y="radarAngles[i].y*78+80+4" text-anchor="middle" font-size="10" font-weight="600" fill="#426c60" font-family="var(--font-body)">{{ l }}</text>
                </svg>
              </div>

              <div class="report-summary">
                <div class="report-dimensions">
                  <div v-for="item in reportDimensions" :key="item.label" class="report-dimension">
                    <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
                    <div class="report-bar"><i :style="{ width: `${item.value}%` }"></i></div>
                  </div>
                </div>
                <div class="report-insight">
                  <span>本轮洞察</span>
                  <strong>技术表达是你的优势</strong>
                  <p>继续补充岗位场景与结果量化，回答会更有说服力。</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Process -->
    <section id="process" class="process-section">
      <div class="section-inner">
        <div class="section-header section-header-left">
          <h2 class="section-title">四步开启面试之旅</h2>
        </div>

        <div class="process-grid">
          <div v-for="(step, i) in steps" :key="i" class="step-card" data-reveal>
            <div class="step-num">{{ String(i + 1).padStart(2, '0') }}</div>
            <div class="step-connector" v-if="i < steps.length - 1">
              <svg width="48" height="2" viewBox="0 0 48 2"><line x1="0" y1="1" x2="48" y2="1" stroke="var(--neutral-300)" stroke-width="2" stroke-dasharray="6 4"/></svg>
            </div>
            <h4 class="step-title">{{ step.title }}</h4>
            <p class="step-desc">{{ step.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Stats -->
    <section id="stats" class="stats-section">
      <div class="section-inner">
        <div class="stats-grid">
          <div v-for="(stat, i) in stats" :key="i" class="stat-card" data-reveal>
            <span class="stat-value">{{ animatedStats[i] || stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="cta-section">
      <div class="section-inner">
        <div class="cta-card" :class="{ 'journey-arrived': journeyProgress > 0.88 }">
          <div class="cta-copy">
            <span class="cta-kicker">下一场面试，从这里预演</span>
            <h2 class="cta-title">把每一次练习，变成更有把握的回答</h2>
            <p class="cta-desc">从简历出发，经历一次真实追问，再带走一份清晰的能力报告。</p>
            <router-link to="/login" class="btn btn-primary btn-lg magnetic-btn" ref="ctaRef" @mousemove="onCtaMouseMove" @mouseleave="onCtaMouseLeave" :style="ctaMagnetStyle">
              立即开始，免费体验
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
            </router-link>
          </div>

          <div class="cta-stage" aria-label="OfferPilot 面试训练流程预览">
            <div class="cta-flow">
              <div v-for="(item, i) in ctaFlow" :key="item.title" class="cta-flow-item">
                <span class="cta-flow-num">{{ i + 1 }}</span>
                <span>
                  <strong>{{ item.title }}</strong>
                  <small>{{ item.desc }}</small>
                </span>
              </div>
            </div>

            <div class="cta-preview">
              <div class="cta-preview-head">
                <span class="cta-preview-status"><i></i> AI 面试进行中</span>
                <span>08:42</span>
              </div>
              <p class="cta-question">请结合你的项目经历，说明一次你如何定位并解决性能问题。</p>
              <div class="cta-answer-lines"><span></span><span></span><span></span></div>
              <div class="cta-feedback">
                <div>
                  <small>表达完整度</small>
                  <strong>清晰</strong>
                </div>
                <div>
                  <small>追问方向</small>
                  <strong>技术决策</strong>
                </div>
              </div>
            </div>

            <div class="cta-score-card">
              <span>本轮表现</span>
              <strong>稳步提升</strong>
              <div><i style="width: 78%"></i></div>
            </div>
            <div class="cta-role-chip">目标岗位 · Java 后端</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="site-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <LogoIcon :size="22" />
          <span class="footer-brand-name">
            <span class="brand-cn">智面幻境</span>
            <span class="brand-en">OfferPilot</span>
          </span>
        </div>
        <div class="footer-links">
          <a href="#">隐私政策</a>
          <a href="#">使用条款</a>
          <a href="#">联系我们</a>
        </div>
        <span class="footer-copy">&copy; 2026 OfferPilot.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, reactive, computed } from 'vue'
import LogoIcon from '../components/ui/LogoIcon.vue'

// Landing page radar chart data
const radarAngles = [0, 1, 2, 3, 4].map(i => {
  const a = (Math.PI * 2 * i) / 5 - Math.PI / 2
  return { x: Math.cos(a), y: Math.sin(a) }
})

const radarValues = [85, 72, 88, 68, 79]
const reportDimensions = [
  { label: '技术深度', value: 88 },
  { label: '表达结构', value: 85 },
  { label: '逻辑分析', value: 72 },
  { label: '岗位匹配', value: 68 },
  { label: '临场应变', value: 79 },
]

const radarGridPoints = (scale) =>
  radarValues.map((_, i) => {
    const r = 56 * scale
    return `${80 + r * radarAngles[i].x},${80 + r * radarAngles[i].y}`
  }).join(' ')

const landingRadarDots = computed(() =>
  radarValues.map((v, i) => ({
    x: 80 + radarAngles[i].x * 56 * (v / 100),
    y: 80 + radarAngles[i].y * 56 * (v / 100),
  }))
)

const landingRadarData = computed(() =>
  landingRadarDots.value.map(p => `${p.x},${p.y}`).join(' ')
)

// === Hero insight spotlight ===
const heroRef = ref(null)
const spotlightVisible = ref(false)
const spotlightRaw = reactive({ x: 0, y: 0 })
const spotlightSmooth = reactive({ x: 0, y: 0 })
let spotlightRaf = null

const spotlightMaskStyle = computed(() => {
  const mask = `radial-gradient(circle 260px at ${spotlightSmooth.x}px ${spotlightSmooth.y}px, #000 0%, #000 38%, rgba(0,0,0,.86) 56%, rgba(0,0,0,.42) 74%, rgba(0,0,0,.1) 88%, transparent 100%)`
  return {
    maskImage: mask,
    WebkitMaskImage: mask,
  }
})

function animateSpotlight() {
  spotlightSmooth.x += (spotlightRaw.x - spotlightSmooth.x) * 0.11
  spotlightSmooth.y += (spotlightRaw.y - spotlightSmooth.y) * 0.11

  const settled =
    Math.abs(spotlightRaw.x - spotlightSmooth.x) < 0.25 &&
    Math.abs(spotlightRaw.y - spotlightSmooth.y) < 0.25

  if (settled) {
    spotlightSmooth.x = spotlightRaw.x
    spotlightSmooth.y = spotlightRaw.y
    spotlightRaf = null
    return
  }

  spotlightRaf = requestAnimationFrame(animateSpotlight)
}

function onHeroPointerEnter() {
  if (!window.matchMedia('(hover: hover)').matches) return
  spotlightVisible.value = true
}

function onHeroPointerMove(event) {
  if (!window.matchMedia('(hover: hover)').matches) return
  const rect = heroRef.value?.getBoundingClientRect()
  if (!rect) return

  spotlightRaw.x = event.clientX - rect.left
  spotlightRaw.y = event.clientY - rect.top
  spotlightVisible.value = true

  if (spotlightSmooth.x === 0 && spotlightSmooth.y === 0) {
    spotlightSmooth.x = spotlightRaw.x
    spotlightSmooth.y = spotlightRaw.y
  }
  if (!spotlightRaf) spotlightRaf = requestAnimationFrame(animateSpotlight)
}

function onHeroPointerLeave() {
  if (!window.matchMedia('(hover: hover)').matches) return
  spotlightVisible.value = false
  if (spotlightRaf) cancelAnimationFrame(spotlightRaf)
  spotlightRaf = null
}

const features = [
  { title: 'AI 追问引擎', desc: '不是简单的一问一答。AI 实时分析你的回答, 动态生成深度追问, 帮你训练临场应变能力。' },
  { title: '智能简历分析', desc: '上传简历, AI 自动提取技能标签和项目经历, 为你生成个性化面试方案。' },
  { title: '多维能力报告', desc: '五维雷达图、优劣势分析、提升建议, 用数据驱动你的成长。' },
]

const steps = [
  { title: '上传简历', desc: 'PDF 或图片, AI 自动提取技能画像' },
  { title: '匹配岗位', desc: '智能推荐目标岗位和面试题库' },
  { title: 'AI 面试', desc: '沉浸式模拟, 实时追问与互动' },
  { title: '获得报告', desc: '五维评估 + 精准提升建议' },
]

const ctaFlow = [
  { title: '上传简历', desc: '识别经历与技能' },
  { title: '进入面试', desc: '根据回答实时追问' },
  { title: '获得报告', desc: '找到下一步提升方向' },
]

const stats = [
  { value: '10,000+', target: 10000, suffix: '+', label: '模拟面试完成' },
  { value: '50+', target: 50, suffix: '+', label: '覆盖岗位' },
  { value: '95%', target: 95, suffix: '%', label: '用户满意度' },
  { value: '4.9/5', target: 4.9, suffix: '/5', label: '平均评分', decimal: true },
]

// === Scroll Progress ===
const scrollPercent = ref(0)
const navVisible = ref(true)
const isScrolled = ref(false)
const journeyProgress = ref(0)
const ambientShift = ref(0)
let lastScrollY = 0
let journeyStart = 0
let journeyEnd = 1

function updateJourneyMetrics() {
  const featuresEl = document.querySelector('.features-section')
  const ctaEl = document.querySelector('.cta-section')
  if (!featuresEl || !ctaEl) return
  journeyStart = featuresEl.offsetTop - window.innerHeight * 0.7
  journeyEnd = ctaEl.offsetTop + ctaEl.offsetHeight - window.innerHeight * 0.45
}

function onScroll() {
  const h = document.documentElement
  scrollPercent.value = (h.scrollTop / (h.scrollHeight - h.clientHeight)) * 100
  const currentY = Math.max(window.scrollY, 0)
  const delta = currentY - lastScrollY

  isScrolled.value = currentY > 24
  if (currentY < 24 || delta < -4) navVisible.value = true
  if (currentY > 96 && delta > 4) navVisible.value = false

  const progress = Math.min(Math.max((currentY - journeyStart) / (journeyEnd - journeyStart), 0), 1)
  journeyProgress.value = progress
  ambientShift.value = (progress - 0.5) * 90

  lastScrollY = currentY
}

// === Typewriter ===
const twPhase = ref(0)
const typedLine1 = ref('')
const typedLine2 = ref('')
const line1Text = '和 AI 面试官'
const line2Text = '练出你的 Offer'
let twTimer = null

function startTypewriter() {
  let i = 0
  twPhase.value = 0
  twTimer = setInterval(() => {
    if (i < line1Text.length) {
      typedLine1.value = line1Text.slice(0, i + 1)
      i++
    } else {
      clearInterval(twTimer)
      setTimeout(() => {
        twPhase.value = 1
        let j = 0
        twTimer = setInterval(() => {
          if (j < line2Text.length) {
            typedLine2.value = line2Text.slice(0, j + 1)
            j++
          } else {
            clearInterval(twTimer)
            setTimeout(() => { twPhase.value = -1 }, 600)
          }
        }, 70)
      }, 300)
    }
  }, 80)
}

// === Mock Window 3D Tilt ===
const mockWindowRef = ref(null)
const mockTilt = reactive({ x: 0, y: 0 })
const mockTiltStyle = computed(() => ({
  transform: `perspective(800px) rotateY(${mockTilt.x}deg) rotateX(${-mockTilt.y}deg) translateY(16px)`,
}))

function onMockMouseMove(e) {
  const el = mockWindowRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width - 0.5
  const y = (e.clientY - rect.top) / rect.height - 0.5
  mockTilt.x = x * 8
  mockTilt.y = y * 6
}

function onMockMouseLeave() {
  mockTilt.x = 0
  mockTilt.y = 0
}

// === Bento Card Spotlight ===
function onCardSpotlight(e) {
  const card = e.currentTarget
  const rect = card.getBoundingClientRect()
  card.style.setProperty('--spot-x', (e.clientX - rect.left) + 'px')
  card.style.setProperty('--spot-y', (e.clientY - rect.top) + 'px')
  card.classList.add('spotlight')
}

function onCardLeave(e) {
  e.currentTarget.classList.remove('spotlight')
}

// === Stats Counter ===
const animatedStats = reactive({})
let statsAnimated = false

function animateStats() {
  if (statsAnimated) return
  statsAnimated = true
  stats.forEach((stat, i) => {
    const duration = 1500
    const start = performance.now()
    const target = stat.target
    const isDecimal = stat.decimal
    function tick(now) {
      const elapsed = now - start
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      const current = target * eased
      if (isDecimal) {
        animatedStats[i] = current.toFixed(1) + stat.suffix
      } else if (target >= 1000) {
        animatedStats[i] = Math.floor(current).toLocaleString() + stat.suffix
      } else {
        animatedStats[i] = Math.floor(current) + stat.suffix
      }
      if (progress < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  })
}

// === Global Mouse (for potential future use) ===
function onGlobalMouseMove() {}

// === CTA Magnetic Button ===
const ctaRef = ref(null)
const ctaMagnet = reactive({ x: 0, y: 0 })
const ctaMagnetStyle = computed(() => ({
  transform: `translate(${ctaMagnet.x}px, ${ctaMagnet.y}px)`,
}))

function onCtaMouseMove(e) {
  const el = ctaRef.value?.$el || ctaRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  ctaMagnet.x = (e.clientX - cx) * 0.15
  ctaMagnet.y = (e.clientY - cy) * 0.15
}

function onCtaMouseLeave() {
  ctaMagnet.x = 0
  ctaMagnet.y = 0
}

// === Observer + Mount ===
const observerRef = ref(null)
const statsObserverRef = ref(null)

onMounted(() => {
  if (!window.matchMedia('(hover: hover)').matches && heroRef.value) {
    spotlightSmooth.x = heroRef.value.clientWidth * 0.68
    spotlightSmooth.y = heroRef.value.clientHeight * 0.52
    spotlightVisible.value = true
  }

  // Scroll listener
  updateJourneyMetrics()
  lastScrollY = Math.max(window.scrollY, 0)
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', updateJourneyMetrics, { passive: true })

  // Typewriter
  setTimeout(startTypewriter, 500)

  // Scroll reveal
  const els = document.querySelectorAll('[data-reveal]')
  if (!('IntersectionObserver' in window) || els.length === 0) {
    els.forEach((el) => el.classList.add('is-visible'))
    return
  }

  observerRef.value = new IntersectionObserver(
    (entries) => {
      const entering = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => a.target.getBoundingClientRect().top - b.target.getBoundingClientRect().top)
      entering.forEach((entry, i) => {
        entry.target.style.transitionDelay = `${i * 80}ms`
        entry.target.classList.add('is-visible')
        observerRef.value.unobserve(entry.target)
      })
    },
    { threshold: 0.15 }
  )
  els.forEach((el) => observerRef.value.observe(el))

  // Stats counter observer
  const statsEl = document.querySelector('.stats-section')
  if (statsEl) {
    statsObserverRef.value = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          animateStats()
          statsObserverRef.value?.unobserve(statsEl)
        }
      },
      { threshold: 0.3 }
    )
    statsObserverRef.value.observe(statsEl)
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', updateJourneyMetrics)
  clearInterval(twTimer)
  if (spotlightRaf) cancelAnimationFrame(spotlightRaf)
  observerRef.value?.disconnect()
  statsObserverRef.value?.disconnect()
})
</script>

<style scoped>
.landing {
  position: relative;
  overflow: clip;
  background: #0d2b24;
}

/* === Interview journey atmosphere === */
.journey-layer {
  position: absolute;
  z-index: 0;
  top: 100dvh;
  right: 0;
  bottom: 74px;
  left: 0;
  overflow: hidden;
  pointer-events: none;
}

.journey-path {
  position: absolute;
  inset: 2% max(1rem, calc((100vw - var(--container-max)) / 2)) 3% auto;
  width: min(84vw, 1180px);
  height: 94%;
  opacity: 0.78;
}

.journey-path-base,
.journey-path-active {
  fill: none;
  vector-effect: non-scaling-stroke;
  stroke-linecap: round;
}

.journey-path-base {
  stroke: rgba(167, 243, 208, 0.1);
  stroke-width: 1;
}

.journey-path-active {
  stroke: #53c896;
  stroke-width: 1.6;
  stroke-dasharray: 1;
  transition: stroke-dashoffset 180ms linear;
  filter: drop-shadow(0 5px 9px rgba(44, 190, 132, 0.16));
}

.journey-node circle:first-child {
  fill: #123d33;
  stroke: rgba(167, 243, 208, 0.2);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
  transition:
    fill 420ms var(--ease-out-expo),
    stroke 420ms var(--ease-out-expo);
}

.journey-node circle:last-child {
  fill: #668b7f;
  transition: fill 420ms var(--ease-out-expo);
}

.journey-node.active circle:first-child {
  fill: #1b5b49;
  stroke: #77dbb1;
}

.journey-node.active circle:last-child {
  fill: #b8f1d8;
}

.journey-node.active {
  filter: drop-shadow(0 6px 10px rgba(44, 190, 132, 0.24));
}

.ambient-orb {
  position: absolute;
  width: clamp(360px, 46vw, 680px);
  aspect-ratio: 1;
  border-radius: 50%;
  opacity: 0.2;
  transform: translate3d(0, var(--journey-shift), 0);
  transition: transform 220ms linear;
  will-change: transform;
}

.ambient-orb-one {
  top: 15%;
  right: -18%;
  background: radial-gradient(circle, rgba(73, 183, 137, 0.24), rgba(73, 183, 137, 0) 68%);
}

.ambient-orb-two {
  top: 48%;
  left: -20%;
  background: radial-gradient(circle, rgba(104, 160, 140, 0.2), rgba(104, 160, 140, 0) 70%);
  transform: translate3d(0, var(--journey-shift-reverse), 0);
}

.ambient-orb-three {
  right: -14%;
  bottom: 2%;
  background: radial-gradient(circle, rgba(225, 164, 81, 0.14), rgba(225, 164, 81, 0) 66%);
  transform: translate3d(0, var(--journey-shift-soft), 0);
}

.journey-fragment {
  position: absolute;
  min-width: 156px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid rgba(183, 242, 215, 0.13);
  border-radius: var(--radius-md);
  background: rgba(18, 61, 51, 0.88);
  box-shadow: 0 14px 30px rgba(3, 20, 16, 0.2);
  opacity: 0;
  transform: translate3d(0, 18px, 0);
  transition:
    opacity 520ms var(--ease-out-expo),
    transform 520ms var(--ease-out-expo);
}

.journey-fragment.visible {
  opacity: 0.82;
  transform: translate3d(0, 0, 0);
}

.journey-fragment span,
.journey-fragment strong {
  display: block;
}

.journey-fragment span {
  color: #83aa9d;
  font-size: 0.66rem;
}

.journey-fragment strong {
  margin-top: 2px;
  color: #d9f5e8;
  font-size: var(--text-xs);
}

.fragment-role {
  top: 24%;
  right: max(2rem, calc((100vw - var(--container-max)) / 2 - 4rem));
}

.fragment-skill {
  top: 48%;
  left: max(1.5rem, calc((100vw - var(--container-max)) / 2 - 5rem));
}

.fragment-report {
  top: 69%;
  right: max(1.5rem, calc((100vw - var(--container-max)) / 2 - 3rem));
}

.features-section,
.process-section,
.stats-section,
.cta-section {
  position: relative;
  z-index: 1;
}

/* === Scroll Progress === */
.scroll-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  z-index: 200;
  background: transparent;
}
.scroll-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-400), var(--accent-600));
  transform-origin: left center;
  transition: transform 0.1s linear;
  border-radius: 0 2px 2px 0;
}

/* === Typewriter === */
.tw-cursor {
  color: var(--accent-500);
  animation: blink 0.6s step-end infinite;
  font-weight: 300;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* === Mock Window Tilt === */
.mock-window {
  transition: transform 0.15s ease-out;
  will-change: transform;
}

/* === Bento Spotlight === */
.bento-card.spotlight::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(
    300px circle at var(--spot-x) var(--spot-y),
    rgba(16, 185, 129, 0.08) 0%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 0;
  opacity: 1;
  transition: opacity 0.3s;
}
.bento-card {
  position: relative;
  overflow: hidden;
}

/* === Magnetic Button === */
.magnetic-btn {
  transition: transform 0.2s var(--ease-spring), background var(--duration-normal) var(--ease-out-expo), box-shadow var(--duration-normal) var(--ease-out-expo);
  will-change: transform;
}

/* === Nav === */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(9, 30, 26, 0.34);
  border-bottom: 1px solid rgba(209, 250, 229, 0.1);
  transition:
    transform 420ms var(--ease-out-expo),
    background 280ms var(--ease-out-expo),
    border-color 280ms var(--ease-out-expo),
    box-shadow 280ms var(--ease-out-expo);
}

.nav.nav-scrolled {
  background: rgba(10, 43, 36, 0.9);
  backdrop-filter: blur(18px) saturate(1.25);
  -webkit-backdrop-filter: blur(18px) saturate(1.25);
  border-bottom-color: rgba(209, 250, 229, 0.14);
  box-shadow: 0 12px 34px rgba(4, 24, 20, 0.16);
}

.nav.nav-hidden {
  transform: translateY(calc(-100% - 4px));
}

.nav-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  height: var(--nav-height);
  padding: 0 var(--space-8);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: #f0fdf8;
}

.nav-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  font-family: var(--font-display);
  color: inherit;
  line-height: 1;
}

.nav-brand .brand-cn {
  font-size: 1.0625rem;
  font-weight: 750;
  letter-spacing: -0.02em;
}

.nav-brand .brand-en {
  margin-top: 3px;
  font-size: 0.5625rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  opacity: 0.68;
}

.nav-links {
  display: flex;
  gap: var(--space-8);
}

.nav-links a {
  font-size: var(--text-sm);
  font-weight: 500;
  color: rgba(236, 253, 245, 0.72);
  transition: color var(--duration-fast) var(--ease-out-expo);
}

.nav-links a:hover {
  color: #fff;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.btn-nav-ghost {
  font-size: var(--text-sm);
  font-weight: 500;
  color: rgba(236, 253, 245, 0.84);
  padding: var(--space-2) var(--space-4);
  transition: color var(--duration-fast) var(--ease-out-expo);
}

.btn-nav-ghost:hover {
  color: #fff;
}

.btn-nav-accent {
  font-size: var(--text-sm);
  font-weight: 600;
  color: #073d31;
  padding: var(--space-2) var(--space-5);
  background: #b7f2d7;
  border-radius: var(--radius-sm);
  transition:
    background var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    transform var(--duration-normal) var(--ease-spring);
}

.btn-nav-accent:hover {
  background: #d9faea;
  box-shadow: 0 8px 24px rgba(4, 24, 20, 0.22);
  transform: translateY(-1px);
  color: #052e25;
}

/* === Hero === */
.hero {
  position: relative;
  min-height: 100dvh;
  padding-top: var(--nav-height);
  display: flex;
  align-items: center;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  z-index: 0;
  inset: 0;
  pointer-events: none;
}

.hero-gradient {
  position: absolute;
  inset: 0;
  background: #0d2b24;
}

.hero-dots {
  position: absolute;
  inset: 0;
  opacity: 0.38;
  background:
    radial-gradient(circle at 78% 42%, rgba(52, 211, 153, 0.15), transparent 28%),
    radial-gradient(circle at 14% 82%, rgba(16, 185, 129, 0.08), transparent 24%);
}

.hero-insight-layer {
  position: absolute;
  z-index: 1;
  inset: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transition: opacity 320ms var(--ease-out-expo);
}

.hero-insight-layer.visible {
  opacity: 1;
}

.hero-insight-wash {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 62% 42%, rgba(46, 153, 111, 0.26), transparent 36%),
    linear-gradient(145deg, #123c32, #0f352c 54%, #183d33);
}

.hero-insight-map {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.insight-links path {
  fill: none;
  stroke: rgba(139, 224, 186, 0.42);
  stroke-width: 1.2;
  vector-effect: non-scaling-stroke;
}

.insight-blueprint {
  fill: rgba(7, 31, 26, 0.34);
  stroke: rgba(183, 242, 215, 0.38);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.insight-blueprint line {
  stroke: rgba(183, 242, 215, 0.24);
}

.insight-blueprint:not(.insight-blueprint-report) circle {
  fill: #5ad49f;
  stroke: none;
}

.insight-blueprint rect:not(:first-child) {
  fill: rgba(183, 242, 215, 0.14);
  stroke: none;
}

.insight-blueprint-report path {
  fill: rgba(77, 207, 151, 0.12);
  stroke: rgba(126, 224, 182, 0.52);
}

.insight-blueprint-report > circle {
  fill: rgba(77, 207, 151, 0.06);
  stroke: rgba(126, 224, 182, 0.38);
}

.insight-node > circle:first-child {
  fill: #164c3e;
  stroke: #71d8ad;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.insight-node > circle:nth-child(2) {
  fill: #b7f2d7;
}

.insight-node text {
  fill: rgba(229, 250, 241, 0.78);
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
}

.insight-node-warm > circle:first-child {
  fill: #5c441f;
  stroke: #f0b85d;
}

.insight-node-warm > circle:nth-child(2) {
  fill: #ffd28a;
}

.insight-hint {
  position: absolute;
  right: var(--space-10);
  bottom: var(--space-8);
  color: rgba(220, 246, 235, 0.62);
  font-size: var(--text-xs);
  letter-spacing: 0.02em;
}

.hero-wrap {
  position: relative;
  z-index: 2;
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-16) var(--space-8);
  width: 100%;
}

.hero-content {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  align-items: center;
  gap: var(--space-16);
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--accent-300);
  margin-bottom: var(--space-6);
}

.badge-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-400);
  animation: breathe 2.5s ease-in-out infinite;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(2.5rem, 5vw, 3.75rem);
  font-weight: 700;
  line-height: 1.15;
  color: var(--neutral-50);
  letter-spacing: -0.03em;
  margin-bottom: var(--space-6);
}

.hero-desc {
  font-size: var(--text-lg);
  color: var(--neutral-400);
  line-height: 1.75;
  margin-bottom: var(--space-8);
  max-width: 480px;
}

.hero-cta {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.hero-proof {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.proof-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--neutral-400);
}

.proof-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--neutral-600);
}

/* === Buttons === */
.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-display);
  font-weight: 600;
  border-radius: var(--radius-sm);
  border: none;
  cursor: pointer;
  text-decoration: none;
  transition:
    transform var(--duration-normal) var(--ease-spring),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    background var(--duration-normal) var(--ease-out-expo),
    border-color var(--duration-normal) var(--ease-out-expo);
}

.btn:active {
  transform: scale(0.97);
}

.btn-lg {
  padding: var(--space-4) var(--space-8);
  font-size: var(--text-lg);
}

.btn-primary {
  background: var(--accent-500);
  color: white;
}

.btn-primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-accent-lg);
  transform: translateY(-2px);
  color: white;
}

.btn-ghost {
  background: transparent;
  color: var(--neutral-300);
  border: 1px solid var(--neutral-600);
  padding: var(--space-4) var(--space-8);
  font-size: var(--text-lg);
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: var(--neutral-400);
  color: var(--neutral-100);
  transform: translateY(-2px);
}

/* === Hero Visual === */
.hero-visual {
  position: relative;
  display: flex;
  justify-content: flex-end;
}

/* === Mock Interview Window === */
.mock-window {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow:
    0 32px 80px rgba(0, 0, 0, 0.35),
    0 0 0 1px rgba(255, 255, 255, 0.06);
  max-width: 520px;
  width: 100%;
  background: var(--neutral-900);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.mock-titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.mock-dots {
  display: flex;
  gap: 6px;
}
.dot-r, .dot-y, .dot-g {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot-r { background: #ef4444; opacity: 0.7; }
.dot-y { background: #f59e0b; opacity: 0.7; }
.dot-g { background: var(--accent-500); opacity: 0.7; }

.mock-title {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.5);
}

.mock-timer {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-400);
}

.mock-body {
  padding: 16px;
}

.mock-chat {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mock-msg {
  display: flex;
  gap: 10px;
  max-width: 90%;
}
.mock-msg.mock-ai { align-self: flex-start; }
.mock-msg.mock-user { align-self: flex-end; flex-direction: row-reverse; }

.mock-avatar-ai, .mock-avatar-user {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.mock-avatar-ai {
  background: rgba(16, 185, 129, 0.15);
  color: var(--accent-400);
}
.mock-avatar-user {
  background: linear-gradient(135deg, var(--accent-500), var(--accent-600));
  color: white;
}

.mock-bubble-ai {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  border-top-left-radius: 4px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.8);
}

.mock-followup {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  color: var(--accent-400);
  background: rgba(16, 185, 129, 0.12);
  padding: 1px 8px;
  border-radius: 20px;
  margin-bottom: 6px;
}

.mock-bubble-user {
  background: var(--accent-600);
  border-radius: 12px;
  border-bottom-right-radius: 4px;
  padding: 10px 14px;
  font-size: 13px;
  line-height: 1.6;
  color: white;
}

.mock-eval {
  padding: 12px;
}
.mock-eval-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.mock-eval-row:last-child { margin-bottom: 0; }
.mock-eval-label {
  width: 56px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}
.mock-eval-bar {
  flex: 1;
  height: 5px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;
}
.mock-eval-fill {
  height: 100%;
  background: var(--accent-500);
  border-radius: 3px;
}
.mock-eval-val {
  width: 24px;
  text-align: right;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: var(--accent-400);
}

/* Floating Cards */
.float-card {
  position: absolute;
  background: var(--surface-elevated);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  box-shadow:
    var(--shadow-lg),
    0 0 0 1px rgba(0, 0, 0, 0.04);
  animation: float 5s ease-in-out infinite;
  z-index: 2;
}

.float-card-score {
  top: -8px;
  right: -16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  animation-delay: -1.5s;
}

.float-value {
  font-family: var(--font-mono);
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1;
  color: var(--accent-500);
}

.float-label {
  font-size: var(--text-xs);
  color: var(--neutral-500);
}

.float-card-improve {
  bottom: 20px;
  left: -28px;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-800);
  animation-delay: -3s;
}

.float-text {
  white-space: nowrap;
}

/* === Section Commons === */
.section-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 var(--space-8);
}

.section-header {
  text-align: center;
  margin-bottom: var(--space-16);
}

.section-header-left {
  text-align: left;
}

.section-title {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 3.5vw, var(--text-3xl));
  font-weight: 700;
  color: #f1faf6;
  letter-spacing: -0.02em;
  margin-bottom: var(--space-3);
}

.section-header-left .section-title {
  margin-bottom: 0;
}

.section-desc {
  font-size: var(--text-lg);
  color: #9dbbb1;
  max-width: 540px;
  margin: 0 auto;
}

/* === Features (Bento) === */
.features-section {
  padding: var(--space-32) 0 var(--space-24);
  background: transparent;
}

.bento-grid {
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: var(--space-6);
  align-items: stretch;
}

.bento-left {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.bento-card {
  background: #f5f1e9;
  border: 1px solid rgba(236, 253, 245, 0.08);
  border-radius: var(--radius-lg);
  padding: var(--space-8);
  position: relative;
  overflow: hidden;
  transition:
    transform var(--duration-slow) var(--ease-spring),
    box-shadow var(--duration-slow) var(--ease-out-expo),
    border-color var(--duration-slow) var(--ease-out-expo);
}

.bento-card:hover {
  transform: translateY(-4px) scale(1.005);
  box-shadow: 0 20px 42px rgba(2, 18, 15, 0.24);
  border-color: rgba(183, 242, 215, 0.22);
}

.bento-card-ai {
  flex: 1;
}

.bento-card-resume {
  flex: 1;
}

.bento-card-report {
  display: flex;
  flex-direction: column;
  background: #234c41;
}

.report-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
}

.report-card-head > div {
  max-width: 31rem;
}

.report-card-head .bento-icon {
  margin-bottom: var(--space-4);
}

.report-demo-badge {
  flex: 0 0 auto;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-full);
  background: rgba(214, 235, 225, 0.12);
  color: #b9ded0;
  font-size: var(--text-xs);
  font-weight: 600;
}

.bento-icon {
  width: 58px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-5);
}

.bento-icon svg {
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 7px 10px rgba(11, 107, 82, 0.12));
}

.bento-title {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-3);
  letter-spacing: -0.01em;
}

.bento-desc {
  font-size: var(--text-sm);
  color: #58736b;
  line-height: 1.7;
}

.bento-card-report .bento-title {
  color: #f1faf6;
}

.bento-card-report .bento-desc {
  color: #a9c8bd;
}

.bento-tag-row {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-4);
  flex-wrap: wrap;
}

.bento-tag {
  font-size: var(--text-xs);
  font-weight: 500;
  color: var(--accent-700);
  padding: var(--space-1) var(--space-3);
  background: var(--accent-50);
  border: 1px solid var(--accent-100);
  border-radius: var(--radius-full);
}

.report-preview {
  min-height: 300px;
  margin-top: var(--space-8);
  padding: var(--space-5);
  display: grid;
  grid-template-columns: minmax(230px, 1.08fr) minmax(190px, 0.92fr);
  align-items: center;
  gap: var(--space-5);
  background: #d6e3dc;
  border-radius: var(--radius-md);
}

.bento-img-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
}

.bento-radar {
  width: min(100%, 280px);
  height: auto;
  overflow: visible;
}

.report-summary {
  display: grid;
  gap: var(--space-5);
}

.report-dimensions {
  display: grid;
  gap: var(--space-3);
}

.report-dimension > div:first-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  color: #4b7066;
  font-size: var(--text-xs);
}

.report-dimension strong {
  color: #164d3e;
  font-size: var(--text-xs);
}

.report-bar {
  height: 5px;
  overflow: hidden;
  border-radius: 3px;
  background: #d3e7de;
}

.report-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #269a70;
}

.report-insight {
  padding: var(--space-4);
  background: #0f5b47;
  border-radius: var(--radius-sm);
  color: #eaf9f2;
}

.report-insight span,
.report-insight strong {
  display: block;
}

.report-insight span {
  color: #9ed7c1;
  font-size: var(--text-xs);
}

.report-insight strong {
  margin: 3px 0 var(--space-2);
  font-size: var(--text-sm);
}

.report-insight p {
  color: #c6e5d9;
  font-size: 0.7rem;
  line-height: 1.6;
}

/* === Process === */
.process-section {
  padding: var(--space-24) 0;
  background: transparent;
}

.process-section .section-title {
  color: #effcf6;
}

.process-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-6);
}

.step-card {
  position: relative;
  padding: var(--space-8) var(--space-6);
  background: rgba(243, 252, 247, 0.08);
  border: 1px solid rgba(209, 250, 229, 0.13);
  border-radius: var(--radius-lg);
  text-align: center;
  transition:
    transform var(--duration-slow) var(--ease-spring),
    box-shadow var(--duration-slow) var(--ease-out-expo),
    border-color var(--duration-slow) var(--ease-out-expo);
}

.step-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 28px rgba(3, 24, 19, 0.18);
  border-color: rgba(167, 243, 208, 0.3);
}

.step-num {
  font-family: var(--font-mono);
  font-size: var(--text-3xl);
  font-weight: 700;
  color: #65d6a7;
  line-height: 1;
  margin-bottom: var(--space-4);
  letter-spacing: -0.04em;
}

.step-connector {
  position: absolute;
  top: 50%;
  right: -30px;
  transform: translateY(-50%);
  z-index: 1;
  pointer-events: none;
}

.step-title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: #effcf6;
  margin-bottom: var(--space-2);
}

.step-desc {
  font-size: var(--text-sm);
  color: #a8c8bd;
  line-height: 1.6;
}

/* === Stats === */
.stats-section {
  padding: var(--space-20) 0;
  background: transparent;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-8);
  text-align: center;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: clamp(var(--text-3xl), 4vw, var(--text-4xl));
  font-weight: 700;
  color: #73d8ae;
  line-height: 1.1;
  letter-spacing: -0.03em;
}

.stat-label {
  font-size: var(--text-sm);
  color: #a3c0b6;
}

/* === CTA === */
.cta-section {
  padding: var(--space-24) 0 var(--space-32);
  background: transparent;
}

.cta-card {
  min-height: 560px;
  background: #071f1a;
  border-radius: var(--radius-xl);
  padding: clamp(2rem, 5vw, 4.5rem);
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(420px, 1.18fr);
  align-items: center;
  gap: clamp(2rem, 5vw, 5rem);
  box-shadow: 0 30px 70px rgba(9, 48, 40, 0.16);
  transition: box-shadow 700ms var(--ease-out-expo);
}

.cta-card.journey-arrived {
  box-shadow:
    0 34px 76px rgba(2, 17, 14, 0.36),
    0 12px 38px rgba(45, 183, 129, 0.12);
}

.cta-card::before {
  content: '';
  position: absolute;
  inset: 0;
  width: 480px;
  height: 480px;
  inset: auto -160px -220px auto;
  border: 100px solid rgba(102, 215, 170, 0.07);
  border-radius: 50%;
  pointer-events: none;
}

.cta-copy {
  position: relative;
  z-index: 2;
}

.cta-kicker {
  display: inline-block;
  margin-bottom: var(--space-5);
  color: #7ee0b8;
  font-size: var(--text-sm);
  font-weight: 600;
}

.cta-title {
  font-family: var(--font-display);
  font-size: clamp(2rem, 4vw, 3.6rem);
  font-weight: 700;
  color: var(--neutral-50);
  max-width: 9.5em;
  margin-bottom: var(--space-5);
  letter-spacing: -0.035em;
  line-height: 1.12;
  position: relative;
}

.cta-desc {
  max-width: 36rem;
  font-size: var(--text-base);
  color: #a8c8bd;
  line-height: 1.8;
  margin-bottom: var(--space-8);
  position: relative;
}

.cta-section .btn-primary {
  position: relative;
}

.cta-stage {
  position: relative;
  min-height: 430px;
  z-index: 1;
}

.cta-flow {
  position: absolute;
  inset: 0 auto 0 0;
  width: 158px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-4);
  z-index: 3;
}

.cta-flow-item {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding: var(--space-3);
  background: #e7e5dc;
  color: #16483b;
  border-radius: var(--radius-md);
  box-shadow: 0 14px 30px rgba(3, 27, 22, 0.24);
}

.cta-flow-num {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #0b6b52;
  color: #fff;
  font-size: var(--text-xs);
  font-weight: 700;
}

.cta-flow-item strong,
.cta-flow-item small {
  display: block;
}

.cta-flow-item strong {
  font-size: var(--text-sm);
}

.cta-flow-item small {
  margin-top: 2px;
  color: #66857c;
  font-size: 0.66rem;
}

.cta-preview {
  position: absolute;
  top: 34px;
  left: 112px;
  right: 0;
  padding: var(--space-6);
  background: #e7e9e2;
  color: #173e34;
  border-radius: var(--radius-lg);
  box-shadow: 0 28px 54px rgba(2, 23, 18, 0.3);
  transform: rotate(1.6deg);
}

.cta-preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--space-4);
  border-bottom: 1px solid #dcebe4;
  color: #718c84;
  font-size: var(--text-xs);
}

.cta-preview-status {
  color: #225c4c;
  font-weight: 600;
}

.cta-preview-status i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: #24b77e;
}

.cta-question {
  max-width: 31rem;
  padding: var(--space-6) 0;
  font-size: var(--text-base);
  font-weight: 600;
  line-height: 1.75;
}

.cta-answer-lines {
  display: grid;
  gap: 9px;
}

.cta-answer-lines span {
  height: 8px;
  border-radius: 4px;
  background: #cfddd6;
}

.cta-answer-lines span:nth-child(2) { width: 88%; }
.cta-answer-lines span:nth-child(3) { width: 64%; }

.cta-feedback {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  margin-top: var(--space-6);
}

.cta-feedback > div {
  padding: var(--space-4);
  background: #d5e2dc;
  border-radius: var(--radius-sm);
}

.cta-feedback small,
.cta-feedback strong {
  display: block;
}

.cta-feedback small {
  color: #6d8b81;
  font-size: var(--text-xs);
}

.cta-feedback strong {
  margin-top: var(--space-1);
  color: #0b6b52;
  font-size: var(--text-sm);
}

.cta-score-card {
  position: absolute;
  right: -18px;
  bottom: 24px;
  width: 180px;
  padding: var(--space-4);
  background: #ffc66f;
  color: #50330c;
  border-radius: var(--radius-md);
  box-shadow: 0 18px 34px rgba(3, 27, 22, 0.24);
  transform: rotate(-3deg);
  z-index: 3;
}

.cta-score-card span,
.cta-score-card strong {
  display: block;
}

.cta-score-card span {
  font-size: var(--text-xs);
  opacity: 0.72;
}

.cta-score-card strong {
  margin: 2px 0 var(--space-3);
  font-size: var(--text-lg);
}

.cta-score-card > div {
  height: 5px;
  overflow: hidden;
  border-radius: 3px;
  background: rgba(80, 51, 12, 0.16);
}

.cta-score-card i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #67420f;
}

.cta-role-chip {
  position: absolute;
  top: 10px;
  right: 24px;
  z-index: 3;
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-full);
  background: #d3e4da;
  color: #155944;
  font-size: var(--text-xs);
  font-weight: 600;
  box-shadow: 0 10px 24px rgba(3, 27, 22, 0.2);
}

/* === Footer === */
.site-footer {
  padding: var(--space-8) 0;
  background: #061914;
  border-top: 1px solid rgba(209, 250, 229, 0.1);
}

.footer-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 var(--space-8);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-display);
  color: #effcf6;
}

.footer-brand-name {
  display: flex;
  flex-direction: column;
  line-height: 1;
}

.footer-brand .brand-cn {
  font-size: var(--text-sm);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.footer-brand .brand-en {
  margin-top: 3px;
  font-size: 0.5rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  opacity: 0.62;
}

.footer-links {
  display: flex;
  gap: var(--space-6);
}

.footer-links a {
  font-size: var(--text-sm);
  color: #9cbbb1;
  transition: color var(--duration-fast) var(--ease-out-expo);
}

.footer-links a:hover {
  color: #fff;
}

.footer-copy {
  font-size: var(--text-xs);
  color: #799d91;
  font-family: var(--font-mono);
}

/* === Scroll Reveal === */
[data-reveal] {
  opacity: 0;
  transform: translateY(24px);
  transition:
    opacity var(--duration-slower) var(--ease-out-expo),
    transform var(--duration-slower) var(--ease-out-expo);
}

[data-reveal].is-visible {
  opacity: 1;
  transform: translateY(0);
}

/* === Responsive === */
@media (max-width: 1024px) {
  .hero-insight-map {
    width: 126%;
    transform: translateX(-12%);
  }

  .insight-hint {
    display: none;
  }

  .journey-fragment {
    display: none;
  }

  .journey-path {
    right: -12%;
    width: 108%;
    opacity: 0.5;
  }

  .ambient-orb {
    opacity: 0.14;
  }

  .hero-content {
    grid-template-columns: 1fr;
    text-align: center;
    gap: var(--space-12);
  }

  .hero-text {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .hero-desc {
    max-width: 520px;
  }

  .hero-cta {
    justify-content: center;
  }

  .hero-proof {
    justify-content: center;
  }

  .hero-visual {
    justify-content: center;
  }

  .mock-window {
    max-width: 460px;
  }

  .float-card-score {
    right: 0;
  }

  .float-card-improve {
    left: 0;
  }

  .bento-grid {
    grid-template-columns: 1fr;
  }

  .process-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .step-connector {
    display: none;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--space-6);
  }

  .cta-card {
    min-height: auto;
    grid-template-columns: 1fr;
  }

  .cta-title {
    max-width: 14em;
  }

  .cta-stage {
    width: min(100%, 680px);
    margin: 0 auto;
  }
}

@media (max-width: 640px) {
  .hero-insight-layer {
    mask-image: radial-gradient(circle 165px at 72% 68%, #000 0%, #000 40%, rgba(0,0,0,.62) 68%, transparent 100%) !important;
    -webkit-mask-image: radial-gradient(circle 165px at 72% 68%, #000 0%, #000 40%, rgba(0,0,0,.62) 68%, transparent 100%) !important;
    opacity: 0.46;
  }

  .hero-insight-map {
    width: 190%;
    transform: translateX(-42%);
  }

  .journey-path {
    right: -52%;
    width: 140%;
    opacity: 0.34;
  }

  .ambient-orb {
    width: 430px;
    opacity: 0.1;
  }

  .nav-links {
    display: none;
  }

  .nav-inner {
    padding: 0 var(--space-4);
  }

  .btn-nav-ghost {
    display: none;
  }

  .btn-nav-accent {
    padding-inline: var(--space-4);
  }

  .hero-wrap {
    padding: var(--space-10) var(--space-4);
  }

  .hero-title {
    font-size: var(--text-3xl);
  }

  .hero-cta {
    flex-direction: column;
    width: 100%;
  }

  .hero-cta .btn {
    width: 100%;
    justify-content: center;
  }

  .float-card {
    display: none;
  }

  .mock-window {
    transform: none !important;
  }

  .section-inner {
    padding: 0 var(--space-4);
  }

  .report-card-head {
    display: block;
  }

  .report-demo-badge {
    display: inline-block;
    margin-top: var(--space-4);
  }

  .report-preview {
    grid-template-columns: 1fr;
    padding: var(--space-4);
  }

  .bento-radar {
    width: min(100%, 250px);
  }

  .process-grid {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .cta-section {
    padding: var(--space-16) 0 var(--space-20);
  }

  .cta-card {
    padding: var(--space-8) var(--space-5);
    border-radius: var(--radius-lg);
  }

  .cta-title {
    font-size: var(--text-3xl);
  }

  .cta-stage {
    min-height: 540px;
  }

  .cta-flow {
    inset: auto 0 0;
    width: auto;
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--space-2);
  }

  .cta-flow-item {
    box-shadow: none;
  }

  .cta-preview {
    top: 28px;
    left: 0;
    padding: var(--space-5);
    transform: none;
  }

  .cta-score-card {
    right: 8px;
    bottom: 190px;
  }

  .cta-role-chip {
    top: 8px;
    right: 8px;
    transform: translateY(-50%);
  }

  .footer-inner {
    flex-direction: column;
    gap: var(--space-4);
    text-align: center;
  }
}

/* === Reduced Motion === */
@media (prefers-reduced-motion: reduce) {
  .hero-insight-layer {
    mask-image: radial-gradient(circle 240px at 68% 52%, #000 0%, rgba(0,0,0,.72) 64%, transparent 100%) !important;
    -webkit-mask-image: radial-gradient(circle 240px at 68% 52%, #000 0%, rgba(0,0,0,.72) 64%, transparent 100%) !important;
    opacity: 0.5;
    transition: none;
  }

  .journey-path-active {
    display: none;
  }

  .journey-node {
    filter: none !important;
  }

  .ambient-orb {
    transform: none !important;
    transition: none;
  }

  .journey-fragment {
    display: none;
  }

  .cta-card.journey-arrived {
    box-shadow: 0 30px 70px rgba(9, 48, 40, 0.16);
  }

  [data-reveal] {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .float-card {
    animation: none;
  }

  .badge-pulse {
    animation: none;
  }

  .scroll-progress { display: none; }
  .tw-cursor { display: none; }
  .mock-window { transition: none !important; transform: translateY(16px) !important; }
  .magnetic-btn { transition: background var(--duration-normal), box-shadow var(--duration-normal) !important; }
  .bento-card.spotlight::before { display: none; }
  .nav { transition: background var(--duration-normal), border-color var(--duration-normal); }
  .nav.nav-hidden { transform: none; }
}
</style>
