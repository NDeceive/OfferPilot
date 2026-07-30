<template>
  <div class="radar-container">
    <svg :viewBox="`0 0 ${size} ${size}`" class="radar-svg">
      <!-- Grid lines -->
      <polygon
        v-for="(level, i) in levels"
        :key="'level-' + i"
        :points="getPolygonPoints(level)"
        fill="none"
        :stroke="'rgba(16,185,129,' + (0.06 + i * 0.03) + ')'"
        stroke-width="1"
      />
      <!-- Axis lines -->
      <line
        v-for="(axis, i) in axes"
        :key="'axis-' + i"
        :x1="center"
        :y1="center"
        :x2="axis.x"
        :y2="axis.y"
        stroke="rgba(16,185,129,0.08)"
        stroke-width="1"
      />

      <!-- 🆕 Target polygon (dashed, only in dual-layer mode) -->
      <polygon
        v-if="showDualLayer && targetDataPoints.length"
        :points="targetPolygonPoints"
        fill="rgba(245,158,11,0.06)"
        stroke="#f59e0b"
        stroke-width="2"
        stroke-dasharray="6,4"
        class="radar-target"
      />
      <!-- 🆕 Target dots -->
      <circle
        v-for="(point, i) in targetDataPoints"
        v-if="showDualLayer"
        :key="'tdot-' + i"
        :cx="point.x"
        :cy="point.y"
        r="3"
        fill="#f59e0b"
        opacity="0.6"
        class="radar-target-dot"
        :style="{ animationDelay: i * 0.1 + 's' }"
      />

      <!-- Data polygon -->
      <polygon
        :points="dataPolygonPoints"
        fill="url(#radarGradient)"
        stroke="url(#radarStroke)"
        stroke-width="2"
        class="radar-data"
      />
      <!-- Data points -->
      <circle
        v-for="(point, i) in dataPoints"
        :key="'point-' + i"
        :cx="point.x"
        :cy="point.y"
        r="4"
        fill="#10b981"
        class="radar-dot"
        :style="{ animationDelay: i * 0.1 + 's' }"
      />
      <!-- Labels -->
      <text
        v-for="(label, i) in labelPositions"
        :key="'label-' + i"
        :x="label.x"
        :y="label.y"
        :text-anchor="label.anchor"
        fill="#71717a"
        font-size="12"
        font-family="var(--font-body)"
      >{{ labels[i] }}</text>

      <!-- 🆕 Legend for dual-layer mode -->
      <g v-if="showDualLayer && targetDataPoints.length" transform="translate(10, 10)">
        <line x1="0" y1="0" x2="20" y2="0" stroke="#f59e0b" stroke-width="2" stroke-dasharray="4,3" />
        <text x="25" y="4" fill="#71717a" font-size="10" font-family="var(--font-body)">目标线</text>
        <line x1="70" y1="0" x2="90" y2="0" stroke="#059669" stroke-width="2" />
        <text x="95" y="4" fill="#71717a" font-size="10" font-family="var(--font-body)">实际</text>
      </g>

      <!-- Gradient defs -->
      <defs>
        <linearGradient id="radarGradient" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="rgba(16,185,129,0.15)" />
          <stop offset="100%" stop-color="rgba(52,211,153,0.1)" />
        </linearGradient>
        <linearGradient id="radarStroke" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#059669" />
          <stop offset="100%" stop-color="#34d399" />
        </linearGradient>
      </defs>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => ['专业知识', '逻辑思维', '沟通表达', '问题解决', '抗压能力', '学习能力'] },
  values: { type: Array, default: () => [85, 72, 90, 78, 65, 88] },
  /** 🆕 目标线数值（双层模式） */
  targetValues: { type: Array, default: () => [] },
  /** 🆕 是否显示双层（目标+实际） */
  showDualLayer: { type: Boolean, default: false },
  size: { type: Number, default: 300 },
})

const center = computed(() => props.size / 2)
const radius = computed(() => (props.size / 2) - 40)
const levels = [0.2, 0.4, 0.6, 0.8, 1.0]

const axes = computed(() => {
  const count = props.labels.length
  return Array.from({ length: count }, (_, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    return {
      x: center.value + radius.value * Math.cos(angle),
      y: center.value + radius.value * Math.sin(angle),
    }
  })
})

function getPolygonPoints(level) {
  const count = props.labels.length
  return Array.from({ length: count }, (_, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const r = radius.value * level
    return `${center.value + r * Math.cos(angle)},${center.value + r * Math.sin(angle)}`
  }).join(' ')
}

const dataPoints = computed(() => {
  const count = props.labels.length
  return props.values.map((val, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const r = radius.value * (val / 100)
    return {
      x: center.value + r * Math.cos(angle),
      y: center.value + r * Math.sin(angle),
    }
  })
})

const dataPolygonPoints = computed(() =>
  dataPoints.value.map(p => `${p.x},${p.y}`).join(' ')
)

/** 🆕 目标线数据点 */
const targetDataPoints = computed(() => {
  if (!props.showDualLayer || !props.targetValues.length) return []
  const count = props.labels.length
  return props.targetValues.map((val, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const r = radius.value * (val / 100)
    return {
      x: center.value + r * Math.cos(angle),
      y: center.value + r * Math.sin(angle),
    }
  })
})

/** 🆕 目标线多边形 */
const targetPolygonPoints = computed(() =>
  targetDataPoints.value.map(p => `${p.x},${p.y}`).join(' ')
)

const labelPositions = computed(() => {
  const count = props.labels.length
  return props.labels.map((_, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const lr = radius.value + 25
    let anchor = 'middle'
    if (Math.cos(angle) > 0.1) anchor = 'start'
    if (Math.cos(angle) < -0.1) anchor = 'end'
    return {
      x: center.value + lr * Math.cos(angle),
      y: center.value + lr * Math.sin(angle) + 4,
      anchor,
    }
  })
})
</script>

<style scoped>
.radar-container {
  width: 100%;
  max-width: 300px;
  margin: 0 auto;
}

.radar-svg {
  width: 100%;
  height: auto;
}

.radar-data {
  opacity: 0;
  animation: fade-in 0.8s var(--ease-out) 0.3s forwards;
}

.radar-dot {
  opacity: 0;
  animation: fade-in 0.4s var(--ease-out) forwards;
}

/* 🆕 target layer animations */
.radar-target {
  opacity: 0;
  animation: fade-in 0.6s var(--ease-out) 0.1s forwards;
}

.radar-target-dot {
  opacity: 0;
  animation: fade-in 0.3s var(--ease-out) forwards;
}
</style>
