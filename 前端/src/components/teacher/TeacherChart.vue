<template>
  <div class="teacher-chart" :class="{ initial: firstRender }">
    <svg :viewBox="`0 0 ${width} 235`" role="img" :aria-label="label">
      <template v-for="tick in [0, 1, 2, 3, 4]" :key="tick">
        <line x1="42" :x2="width - 15" :y1="195 - tick * 40" :y2="195 - tick * 40" stroke="#e7e9e8" />
        <text x="33" :y="199 - tick * 40" text-anchor="end">{{ Math.round(max * tick / 4) }}</text>
      </template>
      <template v-if="kind === 'scatter'">
        <line :x1="x(maxX / 2)" :x2="x(maxX / 2)" y1="35" y2="195" stroke="#d1d5db" />
        <line x1="42" :x2="width - 15" :y1="y(70)" :y2="y(70)" stroke="#d1d5db" />
        <text x="55" y="48">基础较好</text><text :x="width - 120" y="48">表现稳定</text>
        <text x="55" y="185">需要关注</text><text :x="width - 140" y="185">建议调整训练方式</text>
        <a v-for="point in points" :key="point.id" href="#" @click.prevent="$emit('select', point.id)">
          <circle :cx="x(point.count)" :cy="y(point.average)" r="5" :fill="point.average < 70 ? '#ff9a3d' : '#10b981'" tabindex="0" @keydown.enter.prevent="$emit('select', point.id)"><title>{{ point.name }}：{{ point.count }}次，{{ point.average.toFixed(1) }}</title></circle>
        </a>
        <text v-for="tick in [0, 1, 2, 3, 4]" :key="`x${tick}`" :x="x(maxX * tick / 4)" y="216" text-anchor="middle">{{ Math.round(maxX * tick / 4) }}</text>
      </template>
      <template v-else>
        <path v-for="(line, index) in series" :key="line.key" :d="linePath(line.key)" :style="{ d: `path('${linePath(line.key)}')` }" fill="none" :stroke="colors[index]" stroke-width="2.5" pathLength="1" />
        <template v-for="(point, index) in points" :key="point.date">
          <a href="#" @click.prevent="$emit('select', point.date)">
            <circle v-for="(line, lineIndex) in series.filter(s => point[s.key] !== null)" :key="line.key" :cx="px(index)" :cy="y(point[line.key])" r="3.5" :fill="colors[lineIndex]" tabindex="0" @keydown.enter.prevent="$emit('select', point.date)"><title>{{ point.date }} · {{ line.name }}：{{ Number(point[line.key]).toFixed(1) }}</title></circle>
          </a>
          <text v-if="(index < points.length - 2 && index % Math.max(1, Math.ceil(points.length / 7)) === 0) || index === points.length - 1" :x="px(index)" y="216" text-anchor="middle">{{ point.date.slice(5) }}</text>
        </template>
      </template>
    </svg>
    <div class="chart-legend"><span v-for="(line, index) in series" :key="line.key"><i :style="{ background: colors[index] }"></i>{{ line.name }}</span><span v-if="kind === 'scatter'">近周期训练次数 / 同岗位表现指数</span></div>
    <p v-if="!points.length" class="empty">暂无可展示数据</p>
  </div>
</template>
<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
const firstRender = ref(true)
let drawTimer
onMounted(() => { drawTimer = setTimeout(() => { firstRender.value = false }, 650) })
onUnmounted(() => clearTimeout(drawTimer))
const props = defineProps({ points: { type: Array, default: () => [] }, series: { type: Array, default: () => [] }, kind: { type: String, default: 'line' }, label: String, colors:{type:Array,default:()=>['#059669','#6ee7b7']} })
defineEmits(['select'])
const width = 720, colors = computed(()=>props.colors)
const max = computed(() => props.kind === 'scatter' ? 100 : Math.max(4, ...props.points.flatMap(p => props.series.map(s => Number(p[s.key]) || 0))))
const maxX = computed(() => Math.max(8, ...props.points.map(p => p.count)))
const x = value => 42 + value / maxX.value * (width - 57)
const px = index => 42 + index / Math.max(1, props.points.length - 1) * (width - 57)
const y = value => 195 - Number(value) / max.value * 160
const linePoints = key => props.points.filter(p => p[key] !== null).map(p => `${px(props.points.indexOf(p))},${y(p[key])}`).join(' ')
const linePath = key => { const points = linePoints(key).split(' ').filter(Boolean); return points.length ? 'M' + points.join(' L') : '' }
</script>
<style scoped>
.teacher-chart{position:relative}.teacher-chart svg{display:block;width:100%;height:auto;max-height:260px}.teacher-chart text{font:12px var(--font-body);fill:#6b7280}.chart-legend{display:flex;justify-content:center;gap:24px;font-size:12px;color:#6b7280}.chart-legend span{display:flex;align-items:center;gap:6px}.chart-legend i{width:7px;height:7px;border-radius:50%}.empty{padding:16px;text-align:center;color:#6b7280}a:focus-visible circle,circle:focus-visible{stroke:#111827;stroke-width:3}

.teacher-chart path{transition:d 250ms ease-out;stroke-dasharray:1}.teacher-chart.initial path{animation:chart-draw 600ms cubic-bezier(.16,1,.3,1) both}.teacher-chart circle{transition:cx 250ms ease-out,cy 250ms ease-out}.empty{line-height:1.7}
@keyframes chart-draw{from{stroke-dashoffset:1}to{stroke-dashoffset:0}}
@media(prefers-reduced-motion:reduce){.teacher-chart path,.teacher-chart.initial path,.teacher-chart circle{animation:none;transition:none}}
</style>
