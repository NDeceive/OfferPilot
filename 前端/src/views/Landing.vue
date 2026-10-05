<!--
THESIS: OfferPilot turns a resume and target role into a focused rehearsal path, shown as one continuous journey rather than a generic AI feature grid.
OWN-WORLD: Deep forest green, mint linework, warm-white product surfaces, and one restrained orange feedback note.
STORY: Pick a role, add a resume, set training goals, rehearse, and leave with clearer answers.
FIRST VIEWPORT: A direct promise and primary action at left; the product actively preparing a Java interview at right.
FORM: A continuous editorial product journey, closely following the supplied V3 reference rather than introducing a new visual system.
-->
<template>
  <div class="landing-page">
    <div class="ambient-lines" aria-hidden="true">
      <span class="ambient-line line-one"></span>
      <span class="ambient-line line-two"></span>
      <span class="ambient-line line-three"></span>
    </div>

    <header class="site-header" :class="{ 'nav-hidden': !navVisible, 'nav-scrolled': isScrolled }">
      <a class="brand" href="#top" aria-label="OfferPilot 首页">
        <BrandLogo variant="cn-on-dark" :width="180" />
      </a>
      <nav class="main-nav" aria-label="主导航">
        <a href="#features">功能</a>
        <a href="#process">课程</a>
        <a href="#result">数据</a>
      </nav>
      <div class="nav-actions">
        <router-link to="/login" class="login-link">登录</router-link>
        <router-link to="/register" class="register-link">免费注册</router-link>
      </div>
    </header>

    <main id="top">
      <section class="hero section-shell">
        <div class="hero-landscape" aria-hidden="true"><svg viewBox="0 0 1440 240" preserveAspectRatio="none" focusable="false"><g class="landscape-back"><path d="M-80 160C140 45 330 230 600 150S1080 30 1520 130"/><path d="M-80 178C160 64 340 248 610 168S1090 48 1520 148"/></g><g class="landscape-front"><path d="M-80 180C210 260 360 80 660 160S1100 250 1520 115"/><path d="M-80 198C210 278 360 98 660 178S1100 268 1520 133"/><path class="landscape-fill" d="M-80 216C210 296 360 116 660 196S1100 286 1520 151V280H-80Z"/></g></svg></div>
        <div class="hero-copy">
          <p class="pill"><span></span>AI 驱动，下一代面试训练</p>
          <h1 class="reveal-title hero-title is-visible">
            <span>和 AI 面试官</span>
            <span>练出你的 Offer</span>
          </h1>
          <p class="hero-description">让每一次面试，都更有底气。AI 真实的历年大厂多维评估报告，每一次模拟练习都在接近你理想的 Offer。</p>
          <div class="hero-actions">
            <router-link to="/login" class="button button-primary">免费开始 <span aria-hidden="true">→</span></router-link>
            <a href="#features" class="button button-secondary">了解更多</a>
          </div>
          <ul class="proof-list" aria-label="产品特点">
            <li>✓ 免费试用</li><li>✓ 无需下载</li><li>✓ 高效体验</li>
          </ul>
        </div>

        <div class="hero-demo reveal-card is-visible" style="--delay: 180ms">
          <div class="demo-window">
            <div class="window-bar">
              <div class="traffic"><i></i><i></i><i></i></div>
              <strong>面试押题</strong>
              <span></span>
            </div>
            <div class="chat-list">
              <div class="chat-row">
                <span class="avatar">AI</span>
                <p>我想了解一下你遇到的目标岗位？<br>请告诉我岗位名称，以及招聘要求、技术要求或岗位 JD。</p>
              </div>
              <div class="chat-row user-row">
                <span class="avatar">我</span>
                <p><strong>Java 后端开发工程师</strong><br>主要负责 Java 后端开发，要求熟悉 Spring Boot、MySQL、Redis、JVM、并发编程等。</p>
              </div>
              <div class="chat-row">
                <span class="avatar">AI</span>
                <p>我将为你生成专属的面试题库，并提供针对性的训练方案。<br><em>✓ 岗位匹配&nbsp;&nbsp; 86</em><br><em>✓ 覆盖知识点&nbsp;&nbsp; 72</em></p>
              </div>
            </div>
          </div>
          <aside class="score-note"><strong>92</strong><span>综合得分</span></aside>
          <aside class="improve-note">↗ <strong>能力提升 +15%</strong></aside>
        </div>
      </section>

      <section id="features" class="prep section-shell">
        <div class="prep-copy">
          <p class="section-pill">面试准备</p>
          <h2 class="reveal-title"><span>先了解你，再开始训练</span></h2>
          <p class="section-lede">除应对外，个人经历和职业重点，共同决定本次面试内容。</p>
          <ul class="prep-list">
            <li class="reveal-item"><i></i><div><strong>匹配真实岗位</strong><span>基于目标岗位，智能推荐面试题型与考察方向。</span></div></li>
            <li class="reveal-item"><i></i><div><strong>解析个人经历</strong><span>AI 自动提取简历中的关键业绩和经验亮点。</span></div></li>
            <li class="reveal-item"><i></i><div><strong>聚焦训练重点</strong><span>根据你的岗位，定制本次面试的能力评估维度。</span></div></li>
          </ul>
        </div>

        <div class="prep-board" aria-label="个性化面试准备流程">
          <article class="role-card reveal-card">
            <span class="card-label">目标岗位</span>
            <div class="role-row"><span class="code-mark">&lt;/&gt;</span><strong>Java 后端开发工程师</strong></div>
            <div class="tag-row"><span>Java核心</span><span>JVM</span><span>分布式系统</span><span>Spring生态</span></div>
          </article>
          <article class="resume-card reveal-card">
            <span class="file-icon">PDF</span>
            <div><strong>Resume.pdf · <em>已解析</em></strong><small>提取技能与履历</small><div class="tag-row"><span>Spring Boot</span><span>Redis</span><span>MySQL</span><span>…</span></div></div>
            <b aria-hidden="true">→</b>
          </article>
          <span class="flow-arrow" aria-hidden="true">→</span>
          <article class="goals-card reveal-card">
            <div class="goals-head"><strong>训练目标</strong><span>已设置 3/3</span></div>
            <ol>
              <li><b>1</b><span><strong>技术基础能力</strong><small>Java 核心、JVM、集合</small></span><i>✓</i></li>
              <li><b>2</b><span><strong>拓展应用能力</strong><small>系统设计、并发、数据库</small></span><i>✓</i></li>
              <li><b>3</b><span><strong>面试表达能力</strong><small>逻辑表达、方案、项目经验</small></span><i>✓</i></li>
            </ol>
          </article>
          <aside class="orange-note">为你定制<br><strong>项目面试</strong><span>◎</span></aside>
          <p class="board-tip">✦ 不断拆解 AI 的题库与能力维度，你是从你的简历中挖掘方向。</p>
        </div>
      </section>

      <section id="process" class="process section-shell">
        <p class="section-pill">四步走，更接近 Offer</p>
        <h2 class="reveal-title"><span>四步开启面试之旅</span></h2>
        <p class="section-lede">从设定岗位，到开启一次完整训练。</p>

        <div class="journey" :class="{ 'journey-visible': journeyVisible }" ref="journeyRef">
          <svg class="journey-curve" viewBox="0 0 1120 140" preserveAspectRatio="none" aria-hidden="true">
            <path pathLength="1" d="M20 78 C130 10 220 128 315 74 S500 10 600 72 S790 132 880 75 S1010 18 1100 68" />
          </svg>
          <article v-for="(step, index) in steps" :key="step.title" class="journey-step" :style="{ '--index': index }">
            <div class="step-copy"><b>0{{ index + 1 }}</b><strong>{{ step.title }}</strong><span>{{ step.desc }}</span></div>
            <div class="step-preview" :class="`preview-${index + 1}`">
              <template v-if="index === 0"><p>&lt;/&gt;&nbsp; Java 后端开发工程师⌄</p><small>前端开发工程师</small><small>产品经理</small></template>
              <template v-else-if="index === 1"><p><span class="file-icon">PDF</span> Resume.pdf <b>↑</b></p></template>
              <template v-else-if="index === 2"><p>☑ Java 核心</p><p>☑ 分布式系统</p><p>☑ 项目经验</p></template>
              <template v-else><p>● ━━━━━</p><p>━━━</p><b class="play">▶</b></template>
            </div>
          </article>
        </div>
      </section>

      <section id="result" class="final-cta section-shell">
        <div class="cta-copy">
          <p class="section-pill">下一场面试，从这里开始</p>
          <h2 class="reveal-title"><span>把每一次练习，</span><span>变成更有把握的回答</span></h2>
          <p>从简历出发，经历一次次追问后，再带着一份清晰的能力画像。在这里，你可以反复练习，持续进步。</p>
          <router-link to="/login" class="button button-primary">立即开始，免费体验 <span aria-hidden="true">→</span></router-link>
        </div>

        <div class="result-board">
          <div class="mini-steps">
            <article v-for="(item, index) in miniSteps" :key="item.title" class="reveal-card" :style="{ '--delay': `${index * 90}ms` }"><b>{{ index + 1 }}</b><span><strong>{{ item.title }}</strong><small>{{ item.desc }}</small></span></article>
          </div>
          <article class="interview-card reveal-card">
            <header><strong>●&nbsp; AI 面试进行中</strong><span>目标岗位 · Java 后端</span></header>
            <div class="interview-main">
              <div class="java-mark" role="img" aria-label="Java">
                <svg viewBox="0 0 64 64" aria-hidden="true">
                  <path class="java-steam" d="M33 8c8 7-8 10-3 18M41 4c8 9-10 13-4 22M25 14c5 5-5 8-2 13" />
                  <path class="java-cup" d="M16 31c8 5 29 5 37 0-1 12-5 19-18 20-12-1-17-8-19-20Z" />
                  <path class="java-handle" d="M51 34c9-2 10 8 2 11-3 1-6 1-9 0" />
                  <path class="java-swoosh" d="M12 50c10 7 34 9 45 1M17 56c10 4 28 5 38 1" />
                </svg>
              </div>
              <div><strong>Java 后端开发工程师</strong><p></p><p></p></div>
            </div>
            <div class="feedback-grid"><div><small>面试方向</small><strong>技术实力</strong></div><div><small>训练阶段</small><strong>项目经验</strong></div></div>
          </article>
          <aside class="result-note reveal-card">本次面试<br><strong>稳步提升</strong><span>↗</span></aside>
        </div>
      </section>
    </main>

    <footer class="site-footer section-shell">
      <div class="brand"><BrandLogo :width="180" /></div>
      <nav><a href="#">隐私政策</a><a href="#">使用条款</a><a href="#">联系我们</a></nav>
      <p>© 2026 OfferPilot. 让你的路，走更远。</p>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import BrandLogo from '../components/ui/BrandLogo.vue'

const journeyRef = ref(null)
const journeyVisible = ref(false)
const navVisible = ref(true)
const isScrolled = ref(false)
const steps = [
  { title: '选择岗位', desc: '确定目标方向' },
  { title: '上传简历', desc: '补充个人项目经历' },
  { title: '设置目标', desc: '选择重点训练能力' },
  { title: '开始面试', desc: '进入正式模拟问答' },
]
const miniSteps = [
  { title: '上传简历', desc: '完成简历解析' },
  { title: '进入面试', desc: 'AI 实时模拟提问' },
  { title: '获得报告', desc: '评估中 · 一步步提升' },
]

let observer
let lastScrollY = 0
function handleScroll() {
  const currentY = Math.max(window.scrollY, 0)
  const delta = currentY - lastScrollY
  isScrolled.value = currentY > 16
  if (currentY < 24 || delta < -3) navVisible.value = true
  else if (currentY > 96 && delta > 3) navVisible.value = false
  lastScrollY = currentY
}

onMounted(() => {
  lastScrollY = Math.max(window.scrollY, 0)
  handleScroll()
  window.addEventListener('scroll', handleScroll, { passive: true })
  const revealEls = document.querySelectorAll('.reveal-title, .reveal-item, .reveal-card')
  if (!('IntersectionObserver' in window)) {
    revealEls.forEach((el) => el.classList.add('is-visible'))
    journeyVisible.value = true
    return
  }
  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-visible')
      if (entry.target === journeyRef.value) journeyVisible.value = true
      observer.unobserve(entry.target)
    })
  }, { threshold: 0.18 })
  revealEls.forEach((el) => observer.observe(el))
  if (journeyRef.value) observer.observe(journeyRef.value)
})
onUnmounted(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.landing-page { --ink:#f3fbf7; --muted:#b0cbc0; --mint:#51e4b2; --mint-strong:#00c98d; --forest:#003d31; --paper:#f3f5ee; position:relative; min-width:320px; overflow:hidden; color:var(--ink); background:radial-gradient(circle at 76% 16%,rgba(0,139,101,.18),transparent 26%),radial-gradient(circle at 18% 50%,rgba(0,117,89,.15),transparent 32%),linear-gradient(180deg,#003b30 0%,#00382e 48%,#003428 100%); font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif }
.section-shell,.site-header{width:min(1180px,calc(100% - 48px));margin-inline:auto}.ambient-lines{position:absolute;inset:0;pointer-events:none;overflow:hidden}.ambient-line{position:absolute;display:block;border:1px solid rgba(81,228,178,.22);border-radius:50%}.line-one{width:780px;height:410px;top:280px;left:-370px;transform:rotate(24deg)}.line-two{width:900px;height:560px;top:40px;right:-390px;transform:rotate(24deg)}.line-three{width:980px;height:520px;bottom:70px;right:-420px;transform:rotate(-16deg)}
.site-header{position:relative;z-index:10;height:74px;display:grid;grid-template-columns:1fr auto 1fr;align-items:center}.brand{display:inline-flex;align-items:center;gap:10px;color:#fff}.brand span{display:flex;flex-direction:column;line-height:1}.brand strong{font-size:16px;letter-spacing:-.02em}.brand small{margin-top:5px;font-size:10px;letter-spacing:.04em;opacity:.75}.main-nav{display:flex;gap:46px}.main-nav a,.login-link{color:#c2d7cf;font-size:14px;transition:color .2s ease}.main-nav a:hover,.login-link:hover{color:#fff}.nav-actions{display:flex;justify-content:flex-end;align-items:center;gap:34px}.register-link{padding:12px 28px;border-radius:7px;background:#d9fff3;color:#003b30;font-size:13px;font-weight:700;transition:transform .2s ease,background .2s ease}.register-link:hover{transform:translateY(-2px);background:#fff}
.hero{position:relative;z-index:1;min-height:570px;padding-block:78px 86px;display:grid;grid-template-columns:.92fr 1.08fr;align-items:center;gap:72px}.hero>*{min-width:0}.pill,.section-pill{display:inline-flex;align-items:center;gap:8px;width:fit-content;padding:7px 14px;margin:0 0 22px;border:1px solid rgba(81,228,178,.35);border-radius:999px;color:#6af0c2;font-size:13px}.pill span{width:7px;height:7px;border-radius:50%;background:var(--mint)}.hero-title,.reveal-title{margin:0;font-size:clamp(36px,4.4vw,60px);line-height:1.1;letter-spacing:-.035em;font-weight:750}.hero-title span,.reveal-title span{display:block}.hero-description{max-width:560px;margin:24px 0 0;color:var(--muted);font-size:17px;line-height:1.9}.hero-actions{display:flex;gap:16px;margin-top:30px}.button{min-height:50px;padding:0 28px;display:inline-flex;align-items:center;justify-content:center;gap:12px;border-radius:7px;font-size:16px;font-weight:700;transition:transform .2s ease,border-color .2s ease,background .2s ease}.button:hover{transform:translateY(-2px)}.button-primary{background:var(--mint-strong);color:#fff;box-shadow:0 13px 30px rgba(0,201,141,.13)}.button-primary:hover{background:#00d79a}.button-secondary{border:1px solid rgba(218,255,242,.28);color:#fff}.button-secondary:hover{border-color:rgba(218,255,242,.6)}.proof-list{display:flex;gap:30px;margin:25px 0 0;padding:0;list-style:none;color:#a9c9bd;font-size:13px}
.hero-demo{position:relative;padding:28px 0 20px}.demo-window{overflow:hidden;border:1px solid rgba(177,225,208,.25);border-radius:22px;background:#15231f;box-shadow:0 28px 70px rgba(0,18,14,.28)}.window-bar{height:54px;padding:0 18px;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;background:#1a2925;color:#c8d4d0;font-size:14px}.traffic{display:flex;gap:7px}.traffic i{width:9px;height:9px;border-radius:50%;background:#ff6259}.traffic i:nth-child(2){background:#f8be34}.traffic i:nth-child(3){background:#1dc88a}.chat-list{padding:18px 20px 20px;display:grid;gap:14px}.chat-row{display:grid;grid-template-columns:34px minmax(0,1fr);align-items:start;gap:12px}.avatar{width:32px;height:32px;display:grid;place-items:center;border-radius:50%;background:#164b3f;color:#63e7ba;font-size:12px;font-weight:700}.chat-row p{min-width:0;margin:0;padding:13px 16px;overflow-wrap:anywhere;border-radius:7px;background:rgba(255,255,255,.045);color:#c3cfcb;font-size:12px;line-height:1.65}.chat-row p strong{color:#fff}.chat-row p em{color:#4ee3b0;font-style:normal}.user-row p{background:linear-gradient(100deg,#04b67d,#049b73);color:#fff}.score-note,.improve-note{position:absolute;padding:13px 15px;border-radius:10px;background:#fff;color:#24483e;box-shadow:0 14px 26px rgba(0,20,15,.2)}.score-note{top:0;right:-14px;display:flex;flex-direction:column;text-align:center}.score-note strong{color:#0abb82;font-size:28px;line-height:1}.score-note span{margin-top:4px;font-size:10px}.improve-note{left:-66px;bottom:8px;font-size:12px}
.prep{position:relative;z-index:1;min-height:660px;padding-block:85px 80px;display:grid;grid-template-columns:.85fr 1.15fr;align-items:center;gap:74px}.section-pill{margin-bottom:17px;padding:6px 14px}.prep h2,.process h2,.final-cta h2{font-size:clamp(32px,3.2vw,47px)}.section-lede{margin:16px 0 0;color:#abc8bd;line-height:1.8}.prep-list{margin:34px 0 0;padding:0;display:grid;gap:29px;list-style:none}.prep-list li{display:grid;grid-template-columns:42px 1fr;align-items:center;gap:14px}.prep-list li>i{width:10px;height:10px;border-radius:50%;background:#67edc2;box-shadow:0 0 0 6px rgba(103,237,194,.06)}.prep-list strong,.prep-list span{display:block}.prep-list strong{margin-bottom:5px;font-size:17px}.prep-list span{color:#9dbeb2;font-size:14px;line-height:1.6}.prep-board{position:relative;min-height:500px}.prep-board article{position:absolute;color:#23463c;background:var(--paper);box-shadow:0 20px 38px rgba(0,22,16,.22)}.role-card{top:16px;left:34px;width:360px;padding:21px;border-radius:12px;transform:rotate(-1.5deg)}.card-label{color:#0a775c;font-size:14px;font-weight:700}.role-row{margin-top:14px;display:flex;align-items:center;gap:13px}.role-row strong{font-size:14px}.code-mark{width:44px;height:44px;display:grid;place-items:center;border-radius:50%;background:#e0f4eb;color:#009a70;font-weight:800}.tag-row{display:flex;flex-wrap:wrap;gap:6px;margin-top:13px}.tag-row span{padding:5px 8px;border-radius:6px;background:rgba(10,119,92,.05);font-size:10px;white-space:nowrap}.resume-card{top:218px;left:0;width:340px;padding:17px;border-radius:11px;display:grid;grid-template-columns:38px 1fr auto;gap:12px;align-items:start}.file-icon{width:36px;height:40px;display:grid;place-items:center;border-radius:5px;background:#ff6854;color:#fff;font-size:9px;font-weight:800}.resume-card strong{font-size:13px}.resume-card em{color:#07906b;font-style:normal}.resume-card small{display:block;margin-top:5px;color:#849c94;font-size:10px}.resume-card>b{color:#00a879}.goals-card{top:142px;right:0;width:310px;padding:20px;border-radius:12px;transform:rotate(3deg)}.goals-head{display:flex;justify-content:space-between}.goals-head span{color:#889f97;font-size:11px}.goals-card ol{margin:18px 0 0;padding:0;display:grid;gap:16px;list-style:none}.goals-card li{display:grid;grid-template-columns:28px 1fr auto;align-items:center;gap:10px}.goals-card li>b{width:27px;height:27px;display:grid;place-items:center;border-radius:50%;background:#075d4a;color:#fff;font-size:11px}.goals-card li strong,.goals-card li small{display:block}.goals-card li strong{font-size:12px}.goals-card li small{margin-top:3px;color:#8aa098;font-size:9px}.goals-card li i{color:#00af7c;font-style:normal;font-weight:800}.flow-arrow{position:absolute;top:242px;left:358px;color:#d5fff1;font-size:30px}.orange-note,.result-note{position:absolute;padding:15px 22px;border-radius:10px;background:#ffc66d;color:#705018;transform:rotate(-7deg);box-shadow:0 16px 28px rgba(0,20,14,.2)}.orange-note{right:-18px;bottom:46px;font-size:11px}.orange-note strong{font-size:15px}.orange-note span{position:absolute;right:14px;top:15px;color:#eb5c34;font-size:26px}.board-tip{position:absolute;left:0;bottom:0;width:525px;margin:0;padding:12px 18px;border:1px solid rgba(86,230,181,.26);border-radius:8px;color:#afcfc3;font-size:12px}
.process{position:relative;z-index:1;min-height:560px;padding-block:80px}.process .section-lede{margin-top:13px}.journey{position:relative;height:300px;margin-top:38px;display:grid;grid-template-columns:repeat(4,1fr);gap:58px}.journey-curve{position:absolute;top:72px;left:0;width:100%;height:140px;overflow:visible}.journey-curve path{fill:none;stroke:#68e5ba;stroke-width:1.5;stroke-linecap:round;stroke-dasharray:1;stroke-dashoffset:1}.journey-visible .journey-curve path{animation:draw-path 1.25s .15s cubic-bezier(.22,1,.36,1) forwards}.journey-step{position:relative;z-index:2;opacity:0;transform:translateY(15px)}.journey-visible .journey-step{animation:show-step .65s calc(.3s + var(--index)*.17s) cubic-bezier(.22,1,.36,1) forwards}.step-copy{min-height:104px;padding-left:24px;display:flex;flex-direction:column}.step-copy b{color:#aff3d9;font-size:18px}.step-copy strong{margin-top:4px;font-size:18px}.step-copy span{margin-top:5px;color:#9bbcaf;font-size:12px}.step-preview{width:178px;min-height:104px;margin-top:28px;padding:13px;border:8px solid rgba(18,101,80,.76);border-radius:12px;background:#edf5ef;color:#275247;box-shadow:0 16px 30px rgba(0,20,14,.17);font-size:10px}.step-preview p{margin:0 0 8px}.step-preview small{display:block;margin-top:6px;color:#9bb0a8}.step-preview p:first-child{font-weight:700}.preview-2 p{padding:10px;display:flex;align-items:center;justify-content:space-between}.preview-2 .file-icon{width:28px;height:30px}.preview-3{padding-left:20px}.preview-4{position:relative;color:#70b8a1}.play{position:absolute;right:14px;bottom:13px;width:40px;height:40px;display:grid;place-items:center;border-radius:50%;background:#04ab79;color:#fff}
.final-cta{position:relative;z-index:1;min-height:550px;padding-block:82px 60px;display:grid;grid-template-columns:.88fr 1.12fr;align-items:center;gap:74px}.cta-copy>p:not(.section-pill){max-width:520px;margin:20px 0 28px;color:#abc8bd;line-height:1.8}.result-board{position:relative;min-height:360px}.interview-card{position:absolute;inset:18px 0 0 96px;padding:24px 26px;border:1px solid rgba(126,230,193,.24);border-radius:14px;background:rgba(8,74,58,.86);box-shadow:0 24px 45px rgba(0,20,14,.22)}.interview-card header{display:flex;justify-content:space-between;color:#a8e6cf;font-size:11px}.interview-main{margin-top:28px;padding:18px;display:flex;gap:18px;align-items:center;border-radius:10px;background:#f0f5ee;color:#23453c}.java-mark{width:54px;height:54px;display:grid;place-items:center;border-radius:50%;background:#e2f2eb;font-size:30px}.interview-main p{width:230px;height:7px;margin:10px 0 0;border-radius:5px;background:#c9e5da}.interview-main p:last-child{width:160px}.feedback-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:12px}.feedback-grid div{padding:14px;border-radius:7px;background:rgba(199,241,226,.13)}.feedback-grid small,.feedback-grid strong{display:block}.feedback-grid small{color:#9cc5b6}.feedback-grid strong{margin-top:5px;color:#62e4b5;font-size:13px}.mini-steps{position:absolute;left:0;top:76px;z-index:3;display:grid;gap:10px}.mini-steps article{width:160px;padding:11px;display:grid;grid-template-columns:26px 1fr;gap:9px;align-items:center;border-radius:8px;background:#eef3eb;color:#315348;box-shadow:0 12px 24px rgba(0,20,14,.22)}.mini-steps article>b{width:25px;height:25px;display:grid;place-items:center;border-radius:50%;background:#075d4a;color:#fff;font-size:10px}.mini-steps strong,.mini-steps small{display:block}.mini-steps strong{font-size:11px}.mini-steps small{margin-top:2px;color:#819991;font-size:8px}.result-note{right:-20px;bottom:3px;z-index:4;transform:rotate(-6deg)}.result-note span{margin-left:18px;font-size:22px}
.site-footer{position:relative;z-index:1;min-height:92px;display:grid;grid-template-columns:1fr auto 1fr;align-items:center}.site-footer nav{display:flex;gap:35px}.site-footer nav a,.site-footer p{color:#9bbcaf;font-size:12px}.site-footer p{justify-self:end}
.reveal-title span,.reveal-item,.reveal-card{opacity:0;transform:translateY(24px);filter:blur(2px);transition:opacity .7s cubic-bezier(.22,1,.36,1),transform .7s cubic-bezier(.22,1,.36,1),filter .7s cubic-bezier(.22,1,.36,1);transition-delay:var(--delay,0ms)}.reveal-title span:nth-child(2){transition-delay:120ms}.reveal-title.is-visible span,.reveal-item.is-visible,.reveal-card.is-visible{opacity:1;transform:translateY(0);filter:blur(0)}.prep-list .reveal-item:nth-child(2){--delay:90ms}.prep-list .reveal-item:nth-child(3){--delay:180ms}
@keyframes draw-path{to{stroke-dashoffset:0}}@keyframes show-step{to{opacity:1;transform:translateY(0)}}
@media(max-width:1000px){.hero,.prep,.final-cta{grid-template-columns:1fr;gap:58px}.hero{padding-top:64px}.hero-copy{max-width:690px}.hero-demo{width:min(680px,94%);margin:auto}.prep-board{width:min(680px,100%);margin:auto}.journey{gap:20px}.step-preview{width:150px}.result-board{width:min(690px,100%);margin:auto}.final-cta{padding-top:70px}}
@media(max-width:700px){.section-shell,.site-header{width:min(100% - 32px,1180px)}.site-header{grid-template-columns:1fr auto;height:66px}.main-nav,.login-link{display:none}.register-link{padding:10px 15px}.hero{min-height:auto;padding-block:55px 70px}.hero-title{font-size:41px}.hero-description{font-size:15px}.button{padding-inline:18px}.proof-list{flex-wrap:wrap;gap:12px 22px}.hero-demo{width:100%}.demo-window{border-radius:14px}.chat-row p{font-size:10px}.score-note{right:-7px}.improve-note{left:-6px}.prep{padding-block:70px}.prep-board{min-height:760px}.role-card{left:0;width:100%}.resume-card{top:205px;width:92%}.goals-card{top:390px;right:0;width:92%;transform:rotate(1deg)}.flow-arrow{display:none}.orange-note{bottom:42px;right:0}.board-tip{width:100%;font-size:10px}.process{min-height:1030px;padding-block:65px}.journey{height:810px;margin-top:42px;padding-left:54px;display:grid;grid-template-columns:1fr;gap:18px}.journey::before{content:'';position:absolute;left:22px;top:18px;bottom:20px;width:1px;background:rgba(104,229,186,.7);transform-origin:top;transform:scaleY(0)}.journey-visible::before{animation:grow-line 1.2s ease forwards}.journey-curve{display:none}.journey-step{display:grid;grid-template-columns:1fr 160px;align-items:center;gap:12px}.journey-step::before{content:'';position:absolute;left:-38px;top:26px;width:9px;height:9px;border:2px solid #baffea;border-radius:50%;background:#056b52}.step-copy{min-height:auto;padding:0}.step-preview{margin:0;width:145px}.final-cta{padding-block:70px 42px}.result-board{min-height:470px}.interview-card{inset:0 0 auto 0}.mini-steps{top:270px}.result-note{bottom:4px;right:4px}.site-footer{padding-block:30px;grid-template-columns:1fr;gap:22px;text-align:center}.site-footer .brand,.site-footer p{justify-self:center}.site-footer nav{justify-content:center}.line-one,.line-two,.line-three{opacity:.6}@keyframes grow-line{to{transform:scaleY(1)}}}
@media(max-width:480px){.hero-title{font-size:36px}.hero-actions{flex-direction:column}.button{width:100%}.window-bar{font-size:12px}.chat-list{padding-inline:12px}.prep-board{min-height:790px}.journey-step{grid-template-columns:1fr}.step-preview{width:190px}.journey{height:920px}.process{min-height:1140px}.interview-card{padding:18px 14px}.interview-main p{width:150px}.feedback-grid{grid-template-columns:1fr}.mini-steps{top:325px}.result-board{min-height:550px}.result-note{bottom:6px}.site-footer nav{flex-wrap:wrap;gap:14px 24px}}
@media(prefers-reduced-motion:reduce){*,*::before,*::after{scroll-behavior:auto!important;animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important}.reveal-title span,.reveal-item,.reveal-card,.journey-step{opacity:1;transform:none;filter:none}.journey-curve path{stroke-dashoffset:0}.journey::before{transform:scaleY(1)}}

/* Visual tightening: keep the four chapters connected as one continuous journey. */
.landing-page::before,.landing-page::after{content:'';position:absolute;pointer-events:none;border-radius:50%;filter:blur(2px)}
.landing-page::before{top:160px;right:-8%;width:720px;height:720px;background:radial-gradient(circle,rgba(12,147,108,.2),rgba(0,88,67,.07) 46%,transparent 72%)}
.landing-page::after{top:820px;left:-18%;width:760px;height:980px;background:radial-gradient(ellipse,rgba(9,119,91,.15),transparent 70%)}
.site-header{position:fixed;top:0;left:50%;z-index:50;height:64px;isolation:isolate;transform:translateX(-50%);transition:transform .32s cubic-bezier(.22,1,.36,1)}
.site-header::before{content:'';position:absolute;z-index:-1;inset:0 auto 0 50%;width:100vw;transform:translateX(-50%);background:rgba(0,54,43,.88);box-shadow:0 10px 30px rgba(0,20,15,.12);backdrop-filter:blur(14px);opacity:0;transition:opacity .24s ease}
.site-header.nav-scrolled::before{opacity:1}
.site-header.nav-hidden{transform:translate(-50%,-110%)}
.hero{min-height:100svh;padding-block:86px 58px}
.hero::before{content:'';position:absolute;z-index:-1;right:-130px;top:-70px;width:700px;height:540px;background:radial-gradient(ellipse,rgba(33,190,140,.16),rgba(5,97,74,.06) 45%,transparent 72%);pointer-events:none}
.hero-demo{isolation:isolate;transform:translateY(-4px)}
.hero-demo::before{content:'';position:absolute;z-index:-1;inset:10% -8% -5% 8%;border-radius:46%;background:radial-gradient(ellipse,rgba(44,219,160,.16),transparent 68%);filter:blur(18px)}
.demo-window{box-shadow:0 36px 74px rgba(0,18,14,.4),0 12px 28px rgba(29,194,141,.08)}
.score-note{transform:translate(5px,-4px);box-shadow:0 18px 36px rgba(0,23,17,.3)}
.improve-note{transform:translate(-3px,2px);box-shadow:0 17px 34px rgba(0,23,17,.28)}

.prep{min-height:0;padding-block:52px 42px}
.prep::before{content:'';position:absolute;z-index:-1;right:-100px;top:20px;width:670px;height:500px;background:radial-gradient(ellipse,rgba(21,151,111,.14),transparent 70%);pointer-events:none}
.prep-board{min-height:432px;transform:translateY(-4px)}
.prep-board::before{content:'';position:absolute;inset:5% -4% 7% 4%;border-radius:45%;background:radial-gradient(ellipse,rgba(40,173,129,.12),transparent 68%);filter:blur(16px)}
.prep-board article{transition:transform .22s cubic-bezier(.22,1,.36,1),box-shadow .22s ease;box-shadow:0 28px 54px rgba(0,22,16,.26),0 8px 18px rgba(0,22,16,.12)}
.prep-board article:hover{box-shadow:0 34px 62px rgba(0,22,16,.32),0 10px 22px rgba(30,163,122,.1)}
.role-card{top:0;left:48px;transform:rotate(-3deg)}
.role-card:hover{transform:rotate(-2deg) translateY(-3px)}
.resume-card{top:186px;left:-12px;transform:rotate(1.5deg)}
.resume-card:hover{transform:rotate(.5deg) translateY(-3px)}
.goals-card{top:104px;right:-12px;transform:rotate(4deg)}
.goals-card:hover{transform:rotate(3deg) translateY(-3px)}
.flow-arrow{top:205px;left:344px;transform:rotate(-8deg);font-size:34px;text-shadow:0 8px 18px rgba(53,231,175,.24)}
.orange-note{right:-24px;bottom:42px;z-index:4;transform:rotate(-8deg);box-shadow:0 24px 40px rgba(0,22,16,.3)}
.board-tip{bottom:4px;background:rgba(2,67,52,.72);box-shadow:0 12px 28px rgba(0,22,16,.16)}

.process{min-height:0;padding-block:50px 38px}
.process::before{content:'';position:absolute;z-index:-1;left:5%;right:5%;top:35%;height:280px;background:radial-gradient(ellipse,rgba(16,132,99,.12),transparent 70%);pointer-events:none}
.journey{height:262px;margin-top:26px}
.journey-curve{top:60px;filter:drop-shadow(0 7px 9px rgba(63,229,176,.22))}
.journey-curve path{stroke:#78ecc4;stroke-width:2.1}
.journey-step::after{content:'';position:absolute;z-index:-1;top:125px;left:22px;width:9px;height:9px;border:2px solid #baffea;border-radius:50%;background:#0fb27f;box-shadow:0 0 0 6px rgba(80,227,178,.1),0 4px 14px rgba(66,236,183,.32)}
.step-copy{min-height:92px}
.step-preview{margin-top:18px;box-shadow:0 21px 38px rgba(0,22,16,.23)}
.preview-1{transform:rotate(-1.8deg)}
.preview-2{transform:translateY(8px) rotate(1.2deg)}
.preview-3{transform:translateY(-3px) rotate(-1deg)}
.preview-4{transform:translateY(4px) rotate(2deg);border-color:rgba(36,157,118,.9);box-shadow:0 26px 44px rgba(0,22,16,.3),0 8px 20px rgba(20,191,139,.12)}
.preview-4 .play{box-shadow:0 10px 20px rgba(0,151,106,.3)}

.final-cta{min-height:0;padding-block:54px 42px}
.final-cta::before{content:'';position:absolute;z-index:-1;right:-130px;bottom:-100px;width:720px;height:560px;background:radial-gradient(ellipse,rgba(16,136,102,.17),transparent 68%);pointer-events:none}
.result-board{min-height:338px;transform:translateY(-4px)}
.interview-card{inset:10px -5px 0 100px;transform:rotate(.8deg);box-shadow:0 34px 64px rgba(0,20,14,.34),0 10px 22px rgba(30,178,132,.08)}
.mini-steps{top:60px;transform:translateX(-8px)}
.mini-steps article:nth-child(1){transform:rotate(-1deg)}
.mini-steps article:nth-child(2){transform:translateX(7px) rotate(.7deg)}
.mini-steps article:nth-child(3){transform:translateX(-3px) rotate(-.5deg)}
.result-note{right:-30px;bottom:-3px;transform:rotate(-7deg);box-shadow:0 23px 40px rgba(0,20,14,.3)}
.site-footer{min-height:78px}
.java-mark{overflow:hidden;background:#eef6f2}
.java-mark svg{width:48px;height:48px;overflow:visible}
.java-mark path{fill:none;stroke-linecap:round;stroke-linejoin:round}
.java-steam{stroke:#ef5a43;stroke-width:3.3}
.java-cup,.java-handle,.java-swoosh{stroke:#1789b8;stroke-width:3}
.java-cup{fill:rgba(23,137,184,.08)!important}

@media(max-width:1000px){
  .hero{padding-block:54px 68px}.prep{padding-block:58px 46px}.process{padding-block:55px 46px}.final-cta{padding-block:58px 44px}
  .prep-board{min-height:448px}.result-board{min-height:350px}
}
@media(max-width:700px){
  .site-header{height:60px}.hero{min-height:100svh;padding-block:82px 52px}.prep{padding-block:52px 38px}.prep-board{min-height:680px}
  .role-card{top:0;left:0;transform:rotate(-1.5deg)}.resume-card{top:178px;left:2px;transform:rotate(1deg)}.goals-card{top:342px;right:0;transform:rotate(1.8deg)}.orange-note{bottom:38px;right:2px}.board-tip{bottom:0}
  .process{min-height:0;padding-block:48px 34px}.journey{height:650px;margin-top:30px;gap:8px}.journey-step{grid-template-columns:minmax(0,1fr) minmax(124px,42%)}.journey-step>*{min-width:0}.journey-step::after{display:none}.step-preview{width:100%;margin:0}.final-cta{padding-block:50px 36px}.result-board{min-height:450px}.interview-card{inset:0 0 auto 0;transform:rotate(.4deg)}.mini-steps{top:258px}.site-footer{min-height:0}
}
@media(max-width:480px){
  .prep-board{min-height:700px}.role-card{width:96%}.resume-card,.goals-card{width:88%}.orange-note{right:8px}.process{min-height:0}.journey{height:760px}.journey-step{grid-template-columns:minmax(0,1fr) 132px}.step-preview{width:132px;justify-self:end}.final-cta{padding-top:46px}.result-board{min-height:520px}.mini-steps{top:310px}
}

/* A quiet moving landscape occupies the lower hero space without adding content. */
.hero-landscape{position:absolute;left:50%;bottom:0;width:100vw;height:clamp(100px,20svh,240px);transform:translateX(-50%);pointer-events:none;z-index:-1;overflow:hidden}
.hero-landscape svg{display:block;width:110%;height:100%;margin-left:-5%;overflow:visible}
.hero-landscape path{fill:none;stroke:#51e4b2;stroke-width:1;opacity:.28}
.hero-landscape .landscape-fill{fill:#51e4b2;stroke:none;opacity:.045}
.landscape-back{animation:landscape-drift 22s ease-in-out infinite alternate;transform-origin:center}
.landscape-front{animation:landscape-drift 17s ease-in-out -8s infinite alternate-reverse;transform-origin:center}
@keyframes landscape-drift{from{transform:translate(-18px,8px) rotate(-.6deg)}to{transform:translate(18px,-8px) rotate(.6deg)}}
@media(max-width:1000px){.hero-landscape{height:110px;opacity:.65}}
@media(prefers-reduced-motion:reduce){.landscape-back,.landscape-front{animation:none}}
</style>
