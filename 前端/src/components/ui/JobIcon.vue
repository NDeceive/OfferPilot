<template>
  <span class="job-icon" :class="`theme-${themeKey}`" aria-hidden="true">
    <svg v-if="iconKey === 'frontend'" class="job-main" viewBox="0 0 48 48" fill="none">
      <rect x="8" y="10" width="32" height="27" rx="5" />
      <path d="M8 17h32M14 13.5h.1M18 13.5h.1" />
      <path d="m19 24-5 4 5 4M29 24l5 4-5 4M26 22l-4 12" />
    </svg>
    <svg v-else-if="iconKey === 'product'" class="job-main" viewBox="0 0 48 48" fill="none">
      <rect x="8" y="9" width="32" height="30" rx="5" />
      <path d="M15 17h18M15 24h8M27 24h6M15 31h12" />
      <circle cx="34" cy="31" r="3" />
    </svg>
    <svg v-else class="job-main" viewBox="0 0 48 48" fill="none">
      <rect x="9" y="9" width="30" height="12" rx="4" />
      <rect x="9" y="27" width="30" height="12" rx="4" />
      <path d="M15 15h.1M19 15h10M15 33h.1M19 33h10M34 15h.1M34 33h.1" />
    </svg>

    <span class="specialty">
      <svg v-if="specialtyKey === 'java'" viewBox="0 0 24 24" fill="none">
        <path d="M7 9h8v5a4 4 0 0 1-4 4 4 4 0 0 1-4-4V9Z" />
        <path d="M15 11h1.5a2 2 0 0 1 0 4H15M8 21h8M10 6c-2-2 3-2 1-4M14 7c-2-2 3-2 1-4" />
      </svg>
      <svg v-else-if="specialtyKey === 'spring'" viewBox="0 0 24 24" fill="none">
        <path d="M19 4C10 5 5 9 5 15c0 3 2 5 5 5 6 0 9-7 9-16Z" />
        <path d="M6 19c3-5 6-7 10-10" />
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none">
        <path d="m9 7-5 5 5 5M15 7l5 5-5 5" />
      </svg>
    </span>
  </span>
</template>

<script setup>
defineProps({
  iconKey: { type: String, default: 'backend' },
  specialtyKey: { type: String, default: 'code' },
  themeKey: { type: String, default: 'jade' },
})
</script>

<style scoped>
.job-icon {
  position: relative;
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border-radius: 15px;
  background: var(--job-soft, #e8f5ef);
  color: var(--job-ink, #087a5a);
}

.job-main {
  width: 38px;
  height: 38px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.specialty {
  position: absolute;
  right: -4px;
  bottom: -4px;
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border: 3px solid #fff;
  border-radius: 8px;
  background: var(--job-accent, #0f9f76);
  color: #fff;
}

.specialty svg {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.theme-ember { --job-soft: #fff0e8; --job-ink: #b64b22; --job-accent: #dc6b32; }
.theme-sky { --job-soft: #e9f5fc; --job-ink: #21739b; --job-accent: #3a91bb; }
.theme-violet { --job-soft: #f1edfb; --job-ink: #6550a4; --job-accent: #8068bd; }
</style>
