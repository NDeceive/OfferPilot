<template>
  <div class="mchart" :style="{ '--mchart-h': `${height}px` }">
    <div v-for="(p, i) in points" :key="i" class="mchart__col">
      <span class="mchart__track">
        <i :style="{ height: `${barHeight(p.value)}%`, background: barColor(p.value) }">
          <b class="mchart__val">{{ p.value === null ? '—' : Math.round(p.value) }}</b>
        </i>
      </span>
      <span class="mchart__label">{{ p.label }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** [{ value: number|null, label: string }]，按时间从左到右 */
  points: { type: Array, default: () => [] },
  height: { type: Number, default: 96 },
})

// 用满分 100 做基准而不是最大值：几场 20 分的柱子在「相对最高分」下会顶到天花板，
// 看着像发挥不错，这是图表最容易骗人的地方。
const barHeight = (v) => {
  const n = Number(v)
  if (!Number.isFinite(n)) return 3
  return Math.max(3, Math.min(100, n))
}

function barColor(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 'var(--m-border)'
  if (n >= 80) return 'var(--m-primary)'
  if (n >= 60) return '#5aa37c'
  return 'var(--m-accent)'
}
</script>

<style scoped>
/* padding-top 是给柱顶那行数字留的位置：数字挂在柱子上方（bottom:100%），
   柱子涨到 100% 时正好落进这块留白，不会被裁掉。 */
.mchart { display: flex; height: var(--mchart-h); padding-top: 15px; align-items: flex-end; gap: 8px; }
.mchart__col { display: flex; flex: 1; min-width: 0; height: 100%; flex-direction: column; align-items: center; gap: 4px; }
.mchart__track { display: flex; width: 100%; flex: 1; min-height: 0; align-items: flex-end; }
.mchart__track i {
  position: relative;
  display: block;
  width: 100%;
  min-height: 3px;
  background: var(--m-primary);
  border-radius: 5px 5px 2px 2px;
  transition: height 600ms cubic-bezier(.16, 1, .3, 1);
}
/* 贴着柱顶。之前数字固定在列首，0 分的柱子在底部，数字飘在几十像素之外看着像坏了。 */
.mchart__val {
  position: absolute;
  bottom: 100%;
  left: 50%;
  margin-bottom: 3px;
  color: var(--m-text-secondary);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
  transform: translateX(-50%);
}
.mchart__label { color: var(--m-text-tertiary); font-size: 10px; line-height: 1; white-space: nowrap; }

@media (prefers-reduced-motion: reduce) {
  .mchart__track i { transition: none; }
}
</style>
