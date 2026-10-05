<template>
  <div class="msk" aria-busy="true" :aria-label="label">
    <!-- 概览块：分数环 + 两行文字 -->
    <div v-if="variant === 'stats'" class="msk__stats">
      <span class="msk__circle" />
      <span class="msk__col">
        <i style="width: 62%" /><i style="width: 44%" />
      </span>
    </div>

    <!-- 卡片列表 -->
    <div v-else class="msk__list">
      <div v-for="n in rows" :key="n" class="msk__card">
        <span class="msk__square" />
        <span class="msk__col">
          <i style="width: 58%" /><i style="width: 36%" />
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  /** stats = 概览块；card = 卡片列表 */
  variant: { type: String, default: 'card' },
  rows: { type: Number, default: 3 },
  label: { type: String, default: '正在加载' },
})
</script>

<style scoped>
.msk { display: flex; flex-direction: column; gap: 12px; }

.msk__stats { display: flex; padding: 18px; align-items: center; gap: 16px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-hero); }
.msk__circle { flex: 0 0 auto; width: 72px; height: 72px; border-radius: 50%; }
.msk__col { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 9px; }
.msk__col i { display: block; height: 12px; border-radius: 6px; }
.msk__col i:first-child { height: 16px; }

.msk__list { display: flex; flex-direction: column; gap: 12px; }
.msk__card { display: flex; padding: 16px; align-items: center; gap: 12px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.msk__square { flex: 0 0 auto; width: 40px; height: 40px; border-radius: 11px; }

/* 微光扫过。用 background-position 而不是伪元素位移：一张背景图就能拉通所有色块，
   不必给每个元素各写一条动画。 */
.msk__circle,
.msk__square,
.msk__col i {
  background: linear-gradient(100deg, #eef1ee 30%, #f7f9f7 50%, #eef1ee 70%);
  background-size: 220% 100%;
  animation: msk-shimmer 1.35s ease-in-out infinite;
}

@keyframes msk-shimmer {
  from { background-position: 180% 0; }
  to { background-position: -80% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .msk__circle, .msk__square, .msk__col i { animation: none; }
}
</style>
