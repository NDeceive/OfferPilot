<template>
  <div class="class-chart" @mouseleave="tip = null">
    <svg v-if="kind === 'ring' || kind === 'semi'" viewBox="0 0 300 240" :aria-label="label" role="img" class="ring-chart">
      <circle v-if="kind === 'ring'" cx="150" cy="118" r="82" fill="none" stroke="#f0f2f1" stroke-width="38" />
      <path v-else d="M55 175 A95 95 0 0 1 245 175" fill="none" stroke="#f0f2f1" stroke-width="32" />
      <path v-for="(item, i) in slices" :key="item.key" :d="arc(item.start, item.end)" :stroke="colors[item.colorIndex % colors.length]" :stroke-width="kind === 'semi' ? 32 : 38" fill="none" tabindex="0" role="button" :aria-label="`${item.name}：${item.value}`" @mouseenter="show(`${item.name}：${item.value}（${percent(item.value)}%）`)" @focus="show(`${item.name}：${item.value}（${percent(item.value)}%）`)" @blur="tip = null" @click="$emit('select', item.key)" @keydown.enter.prevent="$emit('select', item.key)" @keydown.space.prevent="$emit('select', item.key)" />
      <text x="150" :y="kind === 'semi' ? 155 : 118" class="ring-total" text-anchor="middle">{{ total }}</text>
      <text x="150" :y="kind === 'semi' ? 178 : 145" text-anchor="middle">{{ unit }}</text>
    </svg>
    <svg v-else :viewBox="`0 0 ${width} 235`" role="img" :aria-label="label">
      <template v-for="tick in ticks" :key="tick"><line x1="46" x2="705" :y1="y(tick)" :y2="y(tick)" stroke="#eef0ef"/><text x="37" :y="y(tick) + 4" text-anchor="end">{{ Math.round(tick) }}{{ kind === 'scatter' ? '%' : '' }}</text></template>
      <template v-if="kind === 'scatter'">
        <line :x1="x(2)" :x2="x(2)" y1="35" y2="195" stroke="#b8c3bd" stroke-dasharray="4 4"/><line x1="46" x2="705" :y1="y(100)" :y2="y(100)" stroke="#b8c3bd" stroke-dasharray="4 4"/>
        <text x="54" y="27">低训练 · 已达目标</text><text x="560" y="27">有训练 · 已达目标</text>
        <text x="54" y="188">低训练 · 未达目标</text><text x="550" y="188">有训练 · 未达目标</text>
        <circle v-for="p in scatterPoints" :key="p.id" :cx="p.plotX" :cy="y(p.attainment)" :r="p.id === selected ? 7 : 4.5" fill="#2ab783" :stroke="p.id === selected || p.growth?.status === '持续下降' ? '#d9685c' : '#fff'" :stroke-width="p.id === selected ? 2 : 1" tabindex="0" role="button" :aria-label="`${p.name}，${p.count}次训练，达成度${p.attainment.toFixed(1)}%`" @mouseenter="show(`${p.name} · ${p.number}\n${p.position}\n${p.count}次训练 · 达成度${p.attainment.toFixed(1)}%\n${p.growth?.status} · ${p.overdue}项任务逾期`)" @focus="show(`${p.name}：${p.count}次，${p.attainment.toFixed(1)}%`)" @blur="tip = null" @click="$emit('select', p.id)" @keydown.enter.prevent="$emit('select', p.id)" @keydown.space.prevent="$emit('select', p.id)" />
        <text v-for="tick in [0,1,2,3,4]" :key="`x${tick}`" :x="x(maxX * tick / 4)" y="216" text-anchor="middle">{{ Math.round(maxX * tick / 4) }}</text>
      </template>
      <template v-else>
        <path :d="line" fill="none" stroke="#2ab783" stroke-width="2.5" />
        <g v-for="(p, i) in points" :key="p.date"><circle :cx="px(i)" :cy="y(p.active)" r="4" fill="#2ab783" tabindex="0" role="button" :aria-label="`${p.date}，${p.active}名学生参与训练`" @mouseenter="show(`${p.date}\n参与训练学生 ${p.active}人`)" @focus="show(`${p.date}：${p.active}人`)" @blur="tip = null" @click="$emit('select', p.date)" @keydown.enter.prevent="$emit('select', p.date)" @keydown.space.prevent="$emit('select', p.date)"/><text v-if="(i < points.length - 2 && i % Math.max(1,Math.ceil(points.length / 7)) === 0) || i === points.length - 1" :x="px(i)" y="216" text-anchor="middle">{{ p.date.slice(5) }}</text></g>
      </template>
    </svg>
    <div v-if="tip" class="chart-tooltip" role="status">{{ tip }}</div>
    <p v-if="!total && (kind === 'ring' || kind === 'semi') || !points.length && (kind === 'line' || kind === 'scatter')" class="chart-empty">暂无符合条件的数据</p>
  </div>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ kind: { type: String, default: 'line' }, points: { type: Array, default: () => [] }, items: { type: Array, default: () => [] }, label: String, unit: String, selected: String })
defineEmits(['select'])
const tip = ref(null), width = 720
const colors = computed(() => props.kind === 'semi' ? ['#2ab783','#b6e8d5'] : ['#c5cbc8', '#e4ae69', '#b6e8d5', '#69cdaa', '#2ab783'])
const total = computed(() => props.items.reduce((sum, i) => sum + i.value, 0))
const percent = v => total.value ? (v / total.value * 100).toFixed(1) : '0'
const slices = computed(() => { let start = 0; return props.items.map((i,colorIndex)=>({...i,colorIndex})).filter(i => i.value > 0).map(i => { const item = { ...i, start, end: start + i.value / total.value }; start = item.end; return item }) })
function arc(start, end) { const semi = props.kind === 'semi', r = semi ? 95 : 82, cy = semi ? 175 : 118, offset = semi ? Math.PI : -Math.PI / 2, scale = semi ? Math.PI : Math.PI * 2; const a = offset + start * scale, b = offset + Math.min(end, start + .99999) * scale; return `M${150 + r * Math.cos(a)},${cy + r * Math.sin(a)} A${r},${r} 0 ${b - a > Math.PI ? 1 : 0} 1 ${150 + r * Math.cos(b)},${cy + r * Math.sin(b)}` }
const maxY = computed(() => props.kind === 'scatter' ? Math.max(125, ...props.points.map(p => Math.ceil(p.attainment / 25) * 25)) : Math.max(4, ...props.points.map(p => p.active)))
const maxX = computed(() => Math.max(6, ...props.points.map(p => p.count)))
const minY = computed(() => props.kind === 'scatter' ? Math.min(50, ...props.points.map(p => Math.floor(p.attainment / 25) * 25)) : 0)
const ticks = computed(() => props.kind === 'scatter' ? Array.from({length:Math.round((maxY.value-minY.value)/25)+1},(_,i)=>minY.value+i*25) : [0,1,2,3,4].map(i=>maxY.value*i/4))
const x = v => 46 + v / maxX.value * 659, y = v => 195 - (v-minY.value) / (maxY.value-minY.value) * 160
const scatterPoints = computed(() => {
  const placed = []
  for (const p of props.points) {
    const origin = x(p.count), py = y(p.attainment)
    const offsets = [0, ...Array.from({length:12}, (_,i)=>[(i+1)*10,-(i+1)*10]).flat()]
    const offset = offsets.find(dx => origin+dx >= 46 && origin+dx <= 705 && placed.every(other => Math.hypot(origin+dx-other.plotX,py-y(other.attainment)) >= 11)) ?? 0
    placed.push({...p,plotX:origin+offset})
  }
  return placed
})
const px = i => 46 + i / Math.max(1,props.points.length - 1) * 659
const line = computed(() => props.points.map((p,i) => `${i ? 'L' : 'M'}${px(i)},${y(p.active)}`).join(' '))
function show(text) { tip.value = text }
watch(() => [props.points, props.items, props.selected], () => { tip.value = null })
</script>
<style scoped>
.class-chart{position:relative;min-width:0}.class-chart svg{display:block;width:100%;height:auto;max-height:245px}.class-chart text{font:12px var(--font-body);fill:#6b7280}.class-chart .ring-total{font-size:30px;font-weight:600;fill:#111827}.class-chart .ring-chart{max-height:225px}.class-chart [role=button]{cursor:pointer}.class-chart [role=button]:focus-visible{outline:none;stroke:#111827;stroke-width:3}.chart-tooltip{position:absolute;top:20px;right:20px;max-width:260px;padding:12px 16px;background:#fff;border:1px solid #e3e7e5;border-radius:8px;box-shadow:0 6px 18px #11182712;font-size:12px;line-height:1.8;white-space:pre-line;pointer-events:none;color:#374151;z-index:3}.chart-empty{text-align:center;color:#6b7280;font-size:13px;padding:8px}.class-chart path{transition:stroke 160ms}.class-chart circle{transition:r 180ms}.class-chart [role=button]:hover{stroke:#047857;stroke-width:2}
@media(prefers-reduced-motion:reduce){.class-chart path,.class-chart circle{transition:none}}
</style>
