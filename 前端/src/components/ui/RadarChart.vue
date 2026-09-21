<template>
  <div class="radar-container">
    <svg :viewBox="`0 0 ${size} ${size}`" class="radar-svg" overflow="visible">
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

      <!-- Target polygon (dashed, only in dual-layer mode) -->
      <polygon
        v-if="showDualLayer && targetDataPoints.length"
        :points="targetPolygonPoints"
        fill="rgba(245,158,11,0.06)"
        stroke="#f59e0b"
        stroke-width="2"
        stroke-dasharray="6,4"
        class="radar-target"
      />
      <!-- Target dots -->
      <template v-if="showDualLayer">
        <circle
          v-for="(point, i) in targetDataPoints"
          :key="'tdot-' + i"
          :cx="point.x"
          :cy="point.y"
          r="3"
          fill="#f59e0b"
          opacity="0.6"
          class="radar-target-dot"
          :style="{ animationDelay: i * 0.1 + 's' }"
        />
      </template>

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
        :r="hoveredIndex === i ? 6 : 4"
        fill="#10b981"
        class="radar-dot"
        :class="{ 'radar-dot--hover': hoveredIndex === i }"
        :style="{ animationDelay: i * 0.1 + 's' }"
        @mouseenter="hoveredIndex = i"
        @mouseleave="hoveredIndex = null"
      />

      <!-- Tooltip on hover -->
      <g v-if="hoveredIndex !== null" class="radar-tooltip">
        <rect
          :x="tooltipBox.x"
          :y="tooltipBox.y"
          :width="tooltipBox.w"
          :height="tooltipBox.h"
          rx="4"
          fill="rgba(0,0,0,0.78)"
        />
        <text
          :x="tooltipText.x"
          :y="tooltipText.y"
          fill="#fff"
          font-size="13"
          font-weight="700"
          font-family="var(--font-mono, monospace)"
          text-anchor="middle"
        >{{ values[hoveredIndex] }}分</text>
      </g>

      <!-- Labels -->
      <text
        v-for="(label, i) in labelPositions"
        :key="'label-' + i"
        :x="label.x"
        :y="label.y"
        :text-anchor="label.anchor"
        fill="#52525b"
        font-size="12"
        font-weight="500"
        font-family="var(--font-body)"
      >{{ labels[i] }}</text>

      <!-- Legend for dual-layer mode -->
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
import { ref, computed } from 'vue'

const props = defineProps({
  labels: { type: Array, default: () => ['专业知识', '逻辑思维', '沟通表达', '问题解决', '抗压能力', '学习能力'] },
  values: { type: Array, default: () => [] },
  /** 目标线数值（双层模式） */
  targetValues: { type: Array, default: () => [] },
  /** 是否显示双层（目标+实际） */
  showDualLayer: { type: Boolean, default: false },
  size: { type: Number, default: 300 },
})

const hoveredIndex = ref(null)

const center = computed(() => props.size / 2)
const radius = computed(() => (props.size / 2) - 48)  // balanced padding for labels
const levels = [0.2, 0.4, 0.6, 0.8, 1.0]
const normalizedValues = computed(() => props.labels.map((_, index) => Math.min(100, Math.max(0, Number(props.values[index]) || 0))))
const normalizedTargets = computed(() => props.labels.map((_, index) => Math.min(100, Math.max(0, Number(props.targetValues[index]) || 0))))

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
  return normalizedValues.value.map((val, i) => {
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

/** 目标线数据点 */
const targetDataPoints = computed(() => {
  if (!props.showDualLayer || !props.targetValues.length) return []
  const count = props.labels.length
  return normalizedTargets.value.map((val, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const r = radius.value * (val / 100)
    return {
      x: center.value + r * Math.cos(angle),
      y: center.value + r * Math.sin(angle),
    }
  })
})

/** 目标线多边形 */
const targetPolygonPoints = computed(() =>
  targetDataPoints.value.map(p => `${p.x},${p.y}`).join(' ')
)

/** Tooltip positioning */
const TOOLTIP_W = 48
const TOOLTIP_H = 24
const tooltipBox = computed(() => {
  if (hoveredIndex.value === null) return { x: 0, y: 0, w: 0, h: 0 }
  const pt = dataPoints.value[hoveredIndex.value]
  return {
    x: pt.x - TOOLTIP_W / 2,
    y: pt.y - TOOLTIP_H - 12,
    w: TOOLTIP_W,
    h: TOOLTIP_H,
  }
})
const tooltipText = computed(() => {
  if (hoveredIndex.value === null) return { x: 0, y: 0 }
  const pt = dataPoints.value[hoveredIndex.value]
  return {
    x: pt.x,
    y: pt.y - TOOLTIP_H / 2 - 12 + 5,  // vertical center of the rect + baseline offset
  }
})

/** Label positions — close to pentagon vertices */
const labelPositions = computed(() => {
  const count = props.labels.length
  return props.labels.map((_, i) => {
    const angle = (Math.PI * 2 * i) / count - Math.PI / 2
    const lr = radius.value + 18  // tight to vertices
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
  max-width: 400px;
  margin: 0 auto;
  position: relative;
}

.radar-svg {
  width: 100%;
  height: auto;
}

.radar-data {
  opacity: 0;
  animation: radar-fade-in 0.8s ease 0.3s forwards;
}

.radar-dot {
  opacity: 0;
  animation: radar-fade-in 0.4s ease forwards;
  cursor: pointer;
  transition: r 0.15s ease;
}

.radar-dot--hover {
  filter: drop-shadow(0 0 6px rgba(16, 185, 129, 0.5));
}

.radar-target {
  opacity: 0;
  animation: radar-fade-in 0.6s ease 0.1s forwards;
}

.radar-target-dot {
  opacity: 0;
  animation: radar-fade-in 0.3s ease forwards;
}

.radar-tooltip {
  pointer-events: none;
  animation: radar-fade-in 0.15s ease forwards;
}

@keyframes radar-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
