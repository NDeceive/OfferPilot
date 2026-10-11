<template>
  <div class="mring" :style="{ width: `${size}px`, height: `${size}px` }" role="img" :aria-label="`${label} ${display} 分`">
    <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size">
      <!-- 转 -90° 让 0 点落在 12 点方向，否则进度从右侧起画 -->
      <g :transform="`rotate(-90 ${size / 2} ${size / 2})`">
        <circle
          :cx="size / 2" :cy="size / 2" :r="radius"
          fill="none" stroke="var(--m-border)" :stroke-width="stroke"
        />
        <circle
          class="mring__arc"
          :cx="size / 2" :cy="size / 2" :r="radius"
          fill="none" :stroke="color" :stroke-width="stroke" stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="offset"
        />
      </g>
    </svg>
    <span class="mring__text" :style="{ color }">
      <strong v-if="display !== null">{{ display }}</strong>
      <small v-else>—</small>
      <em v-if="caption">{{ caption }}</em>
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 0-100；null/undefined 表示还没有分数 */
  value: { type: [Number, String], default: null },
  size: { type: Number, default: 60 },
  stroke: { type: Number, default: 5 },
  label: { type: String, default: '得分' },
  caption: { type: String, default: '' },
})

const display = computed(() => {
  const n = Number(props.value)
  return Number.isFinite(n) ? Math.round(n) : null
})

const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => {
  const pct = Math.max(0, Math.min(100, display.value ?? 0))
  return circumference.value * (1 - pct / 100)
})

// 分数带和报告页的观感保持一致：60 以下偏暖，60-79 中性，80 以上品牌绿
const color = computed(() => {
  const n = display.value
  if (n === null) return 'var(--m-text-tertiary)'
  if (n >= 80) return 'var(--m-primary)'
  if (n >= 60) return '#3f8a67'
  return 'var(--m-accent)'
})
</script>

<style scoped>
.mring { position: relative; flex: 0 0 auto; }
.mring svg { display: block; }
.mring__arc { transition: stroke-dashoffset 700ms cubic-bezier(.16, 1, .3, 1); }
.mring__text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  line-height: 1;
}
.mring__text strong { font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.mring__text small { color: var(--m-text-tertiary); font-size: 15px; font-weight: 700; }
.mring__text em { margin-top: 2px; color: var(--m-text-tertiary); font-size: 9px; font-style: normal; }

@media (prefers-reduced-motion: reduce) {
  .mring__arc { transition: none; }
}
</style>
