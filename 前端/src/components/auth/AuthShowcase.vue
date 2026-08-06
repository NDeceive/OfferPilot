<template>
  <div class="showcase" @mouseenter="paused = true" @mouseleave="paused = false">
    <div class="orbit">
      <article
        v-for="(item, index) in slides"
        :key="item.title"
        class="orbit-card"
        :class="cardPosition(index)"
        :aria-hidden="index !== activeIndex"
      >
        <div v-if="index === 0" class="scene dialogue" aria-hidden="true">
          <div class="scene-head"><i></i>模拟面试进行中 <span>08:42</span></div>
          <div class="message"><b>面</b><p>请结合一个具体项目，说明你如何推进问题解决。</p></div>
          <div class="message mine"><p>我先梳理了影响范围，并与团队确认优先级……</p><b>我</b></div>
        </div>

        <div v-else-if="index === 1" class="scene video-scene" aria-label="VR 数字人面试演示片段">
          <video v-if="videoSrc" ref="videoRef" :src="videoSrc" :poster="videoPoster" muted loop playsinline preload="metadata"></video>
          <img v-else :src="videoPoster" alt="VR 数字人正在进行模拟面试的演示画面" />
          <div class="video-shade"></div>
          <div class="video-status"><i></i>VR 面试演示</div>
          <div class="video-caption">请介绍你在项目中承担的主要职责</div>
        </div>

        <div v-else class="scene report" aria-hidden="true">
          <div class="radar"><i></i><i></i><i></i><b></b></div>
          <div class="report-copy">
            <small>本次复盘</small><strong>表达结构更清晰</strong>
            <p><span>岗位匹配</span><b>86</b></p>
            <p><span>回答完整度</span><b>82</b></p>
            <p><span>逻辑表达</span><b>78</b></p>
          </div>
        </div>

        <div class="copy">
          <span>{{ item.eyebrow }}</span>
          <h2>{{ item.title }}</h2>
          <p>{{ item.description }}</p>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import videoPoster from '../../assets/generated/login-hero-ui.jpg'

const props = defineProps({
  initialSlide: { type: Number, default: 0 },
  videoSrc: { type: String, default: '' },
})

const slides = [
  { eyebrow: '针对岗位', title: '在真实问答中完善表达', description: '结合目标岗位与个人经历展开提问，逐步提升回答的完整性和说服力。' },
  { eyebrow: '沉浸场景', title: '提前熟悉正式面试节奏', description: '通过数字人场景完成一段可重复、可暂停的模拟练习。' },
  { eyebrow: '练后复盘', title: '让每次练习都有清晰反馈', description: '记录回答表现与能力变化，帮助你明确下一阶段的训练重点。' },
]

const activeIndex = ref(Math.min(Math.max(props.initialSlide, 0), 2))
const paused = ref(false)
const videoRef = ref(null)
let timer

function cardPosition(index) {
  const offset = (index - activeIndex.value + slides.length) % slides.length
  return offset === 0 ? 'is-active' : offset === 1 ? 'is-next' : 'is-previous'
}

function startTimer() {
  window.clearInterval(timer)
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  timer = window.setInterval(() => {
    if (!paused.value && document.visibilityState === 'visible') {
      activeIndex.value = (activeIndex.value + 1) % slides.length
    }
  }, 5600)
}

async function syncVideo() {
  await nextTick()
  const video = Array.isArray(videoRef.value) ? videoRef.value[0] : videoRef.value
  if (!video) return
  if (activeIndex.value === 1) video.play().catch(() => {})
  else video.pause()
}

watch(activeIndex, syncVideo)
onMounted(() => { startTimer(); syncVideo() })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.showcase {
  width: 100%;
  max-width: 580px;
  margin-top: 4px;
}

.orbit {
  position: relative;
  height: 390px;
  perspective: 1100px;
  transform-style: preserve-3d;
}

.orbit::after {
  position: absolute;
  right: 12%;
  bottom: 32px;
  left: 12%;
  height: 34px;
  border-radius: 50%;
  background: rgba(15, 118, 85, 0.1);
  filter: blur(22px);
  content: '';
  transform: rotateX(72deg);
}

.orbit-card {
  position: absolute;
  top: 0;
  left: 50%;
  width: 78%;
  transform-origin: 50% 78%;
  transition:
    transform 720ms cubic-bezier(.16, 1, .3, 1),
    opacity 520ms ease,
    filter 520ms ease;
}

.orbit-card.is-active {
  z-index: 3;
  opacity: 1;
  filter: none;
  transform: translate3d(-50%, 0, 80px) rotateY(0) scale(1);
}

.orbit-card.is-previous {
  z-index: 1;
  opacity: 0.42;
  filter: saturate(0.68) brightness(0.98);
  transform: translate3d(-84%, 28px, -150px) rotateY(18deg) scale(0.78);
}

.orbit-card.is-next {
  z-index: 2;
  opacity: 0.58;
  filter: saturate(0.76) brightness(0.99);
  transform: translate3d(-16%, 20px, -110px) rotateY(-18deg) scale(0.82);
}

.scene {
  height: 230px;
  overflow: hidden;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 60px rgba(13, 67, 51, 0.14);
}

.dialogue { padding: 20px; }
.scene-head, .video-status { display: flex; align-items: center; gap: 8px; color: #42665a; font-size: 12px; font-weight: 600; }
.scene-head i, .video-status i { width: 7px; height: 7px; border-radius: 50%; background: #10b981; box-shadow: 0 0 0 4px rgba(16,185,129,.1); }
.scene-head span { margin-left: auto; color: #7c978e; font-variant-numeric: tabular-nums; }
.message { display: flex; align-items: flex-start; gap: 10px; margin-top: 23px; }
.message p { max-width: 76%; margin: 0; padding: 12px 14px; border-radius: 13px; background: #f0f5f3; color: #405d54; font-size: 12px; line-height: 1.55; }
.message b { display: grid; flex: 0 0 28px; height: 28px; place-items: center; border-radius: 9px; background: #dff5ec; color: #0d8e68; font-size: 10px; }
.message.mine { justify-content: flex-end; }
.message.mine p { background: #0d8e68; color: white; }

.video-scene { position: relative; background: #0d2b24; }
.video-scene img, .video-scene video { width: 100%; height: 100%; object-fit: cover; animation: video-drift 9s ease-in-out infinite alternate; }
.video-shade { position: absolute; inset: 0; background: linear-gradient(180deg,rgba(7,34,27,.04),rgba(7,34,27,.5)); }
.video-status { position: absolute; top: 16px; left: 16px; padding: 8px 11px; border-radius: 999px; background: rgba(7,34,27,.72); color: white; }
.video-caption { position: absolute; right: 18px; bottom: 16px; left: 18px; color: white; font-size: 13px; font-weight: 600; text-align: center; }

.report { display: grid; grid-template-columns: 46% 1fr; align-items: center; padding: 26px; }
.radar { position: relative; width: 155px; height: 155px; }
.radar i, .radar > b { position: absolute; inset: 50%; border: 1px solid rgba(16,185,129,.2); transform: translate(-50%,-50%) rotate(45deg); }
.radar i:nth-child(1) { width: 140px; height: 140px; }.radar i:nth-child(2) { width: 96px; height: 96px; }.radar i:nth-child(3) { width: 52px; height: 52px; }
.radar > b { width: 96px; height: 108px; border: 0; background: rgba(16,185,129,.27); clip-path: polygon(50% 0,92% 32%,78% 90%,25% 100%,3% 38%); }
.report-copy { display: flex; flex-direction: column; gap: 10px; }
.report-copy small, .copy > span { color: #0d8e68; font-size: 11px; font-weight: 700; }
.report-copy strong { margin-bottom: 3px; color: #253f37; font-size: 16px; }
.report-copy p { display: flex; justify-content: space-between; margin: 0; color: #70877f; font-size: 12px; }.report-copy p b { color: #0d8e68; }

.copy {
  max-width: 390px;
  margin: 19px auto 0;
  text-align: center;
}

.copy h2 { margin: 5px 0 7px; color: #173f34; font-family: var(--font-display); font-size: 1.3rem; line-height: 1.25; letter-spacing: -.02em; }
.copy p { margin: 0 auto; color: #688079; font-size: 13px; line-height: 1.65; }
.orbit-card:not(.is-active) .copy { opacity: 0; }

@keyframes video-drift { from { transform: scale(1.01) translateX(-.5%); } to { transform: scale(1.06) translateX(.5%); } }

@media (max-width: 900px) { .showcase { display: none; } }

@media (prefers-reduced-motion: reduce) {
  .orbit-card { transition: none; }
  .video-scene img, .video-scene video { animation: none; }
}
</style>
