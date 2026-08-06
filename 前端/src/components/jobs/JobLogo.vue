<template>
  <span class="job-logo" :class="`job-logo--${tone}`" aria-hidden="true">
    <img v-if="officialSrc" :src="officialSrc" alt="" />
    <svg v-else viewBox="0 0 48 48" fill="none">
      <template v-if="iconKey === 'chip-speed'">
        <rect x="13" y="13" width="22" height="22" rx="5" />
        <path d="M19 19h10v10H19zM7 19h6M7 29h6M35 19h6M35 29h6M19 7v6M29 7v6M19 35v6M29 35v6" />
        <path class="accent" d="m21 31 7-14-1 10h5z" />
      </template>
      <template v-else-if="iconKey === 'web'">
        <rect x="6" y="8" width="36" height="31" rx="7" />
        <path d="M7 16h34M13 12h.1M18 12h.1M23 12h.1" />
        <path class="accent" d="m20 23-5 5 5 5M28 23l5 5-5 5" />
      </template>
      <template v-else-if="iconKey === 'ai-app'">
        <rect x="7" y="8" width="34" height="32" rx="8" />
        <path d="M8 17h32M14 12h.1M19 12h.1" />
        <circle class="accent" cx="24" cy="28" r="5" />
        <path class="accent" d="M24 19v4M24 33v4M15 28h4M29 28h4M18 22l3 3M30 22l-3 3" />
      </template>
      <template v-else-if="iconKey === 'machine-learning'">
        <circle cx="12" cy="14" r="4" /><circle cx="36" cy="13" r="4" />
        <circle cx="12" cy="35" r="4" /><circle cx="36" cy="35" r="4" />
        <circle class="accent" cx="24" cy="24" r="6" />
        <path d="m15 16 5 5M33 16l-5 5M15 33l5-5M33 33l-5-5" />
      </template>
      <template v-else-if="iconKey === 'nlp'">
        <path d="M7 10h26a7 7 0 0 1 7 7v8a7 7 0 0 1-7 7H21l-9 7v-7a7 7 0 0 1-5-7z" />
        <circle class="accent" cx="17" cy="21" r="2" />
        <circle class="accent" cx="24" cy="21" r="2" />
        <circle class="accent" cx="31" cy="21" r="2" />
      </template>
      <template v-else-if="iconKey === 'computer-vision'">
        <rect x="6" y="9" width="36" height="30" rx="8" />
        <circle class="accent" cx="24" cy="24" r="8" />
        <circle cx="24" cy="24" r="3" />
        <path d="M11 17v-3h4M33 14h4v4M37 31v3h-4M15 34h-4v-4" />
      </template>
      <template v-else-if="iconKey === 'recommendation'">
        <circle class="accent" cx="11" cy="24" r="4" />
        <circle cx="36" cy="11" r="4" /><circle cx="36" cy="24" r="4" /><circle cx="36" cy="37" r="4" />
        <path d="M15 24c10 0 10-13 17-13M15 24h17M15 24c10 0 10 13 17 13" />
      </template>
      <template v-else-if="iconKey === 'speech'">
        <path class="accent" d="M7 25h4l3-11 5 22 5-25 5 28 5-18 3 4h4" />
        <path d="M9 42h30M13 7h22" />
      </template>
      <template v-else-if="iconKey === 'multimodal'">
        <path d="m24 6 16 9v18l-16 9-16-9V15z" />
        <path d="m8 15 16 9 16-9M24 24v18" />
        <circle class="accent" cx="24" cy="24" r="5" />
      </template>
      <template v-else-if="iconKey === 'mlops'">
        <path d="M17 15c-6-6-12-1-12 6s6 12 12 6l14-12c6-6 12-1 12 6s-6 12-12 6z" />
        <circle class="accent" cx="17" cy="21" r="3" />
        <circle class="accent" cx="31" cy="21" r="3" />
      </template>
      <template v-else-if="iconKey === 'product-consumer'">
        <rect x="17" y="5" width="21" height="38" rx="7" />
        <circle class="accent" cx="13" cy="18" r="6" />
        <path d="M4 37c0-6 4-10 9-10s9 4 9 10M23 11h9M23 16h7" />
      </template>
      <template v-else-if="iconKey === 'product-enterprise'">
        <path d="M7 42V18l17-10 17 10v24M5 42h38" />
        <path class="accent" d="M14 22h6v6h-6zM28 22h6v6h-6zM14 32h6v6h-6zM28 32h6v10h-6z" />
      </template>
      <template v-else-if="iconKey === 'product-ai'">
        <rect x="6" y="8" width="36" height="32" rx="8" />
        <path d="M7 17h34M13 12h.1M18 12h.1" />
        <path class="accent" d="m24 21 2 5 5 2-5 2-2 5-2-5-5-2 5-2z" />
      </template>
      <template v-else-if="iconKey === 'data-business'">
        <path d="M7 41V9M7 41h35" />
        <path class="accent" d="m12 34 8-10 7 5 11-15" />
        <path d="M31 14h7v7" />
      </template>
      <template v-else-if="iconKey === 'data-product'">
        <path d="M8 8h32L29 23v12l-10 5V23z" />
        <circle class="accent" cx="24" cy="16" r="4" />
      </template>
      <template v-else-if="iconKey === 'data-bi'">
        <rect x="5" y="7" width="38" height="34" rx="8" />
        <path d="M6 17h36M13 12h.1M18 12h.1" />
        <path class="accent" d="M13 34V27M21 34V22M29 34V29M37 34V20" />
      </template>
      <template v-else-if="iconKey === 'qa-functional'">
        <rect x="9" y="6" width="30" height="36" rx="7" />
        <path class="accent" d="m15 18 3 3 6-7M15 30l3 3 6-7M27 19h6M27 31h6" />
      </template>
      <template v-else-if="iconKey === 'qa-automation'">
        <path d="M13 13a16 16 0 1 1-3 20" />
        <path d="M5 22v11h11" />
        <circle class="accent" cx="25" cy="25" r="7" />
        <path class="accent" d="M25 14v4M25 32v4M14 25h4M32 25h4" />
      </template>
      <template v-else-if="iconKey === 'qa-performance'">
        <path d="M7 35a18 18 0 1 1 34 0M12 35h24" />
        <path class="accent" d="m24 31 10-13" />
        <circle cx="24" cy="31" r="3" />
      </template>
      <template v-else>
        <path d="M24 5 40 11v12c0 10-6 17-16 21C14 40 8 33 8 23V11z" />
        <path class="accent" d="m16 24 5 5 11-12" />
      </template>
    </svg>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  iconKey: { type: String, default: 'qa-sdet' },
  tone: { type: String, default: 'jade' },
})

const officialIcons = {
  openjdk: '/job-logos/openjdk.svg',
  python: '/job-logos/python.svg',
  go: '/job-logos/go.svg',
  nodejs: '/job-logos/nodejs.svg',
  android: '/job-logos/android.svg',
  apple: '/job-logos/apple.svg',
  flutter: '/job-logos/flutter.svg',
  wechat: '/job-logos/wechat.svg',
  electron: '/job-logos/electron.svg',
}

const officialSrc = computed(() => officialIcons[props.iconKey] || '')
</script>

<style scoped>
.job-logo {
  display: inline-grid;
  width: 54px;
  height: 54px;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 16px;
  background: #f2f7f4;
  color: #235c4b;
}

.job-logo img {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.job-logo svg {
  width: 36px;
  height: 36px;
  stroke: currentColor;
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.job-logo svg .accent {
  stroke: #18a875;
}

.job-logo--blue { background: #eef5ff; color: #315f9f; }
.job-logo--cyan { background: #edf9fa; color: #267781; }
.job-logo--violet { background: #f5f0ff; color: #6952a4; }
.job-logo--orange { background: #fff5e8; color: #9b632c; }
.job-logo--amber { background: #fff7e6; color: #9a6a20; }
.job-logo--green { background: #eef8f1; color: #33704c; }
.job-logo--neutral { background: #f2f3f4; color: #25282b; }
</style>
