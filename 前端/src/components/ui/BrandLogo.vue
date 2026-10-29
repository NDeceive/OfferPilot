<template>
  <img class="brand-logo-image" :src="source" :width="width" :alt="variant === 'en' ? 'OfferPilot' : '智面幻境 OfferPilot'" />
</template>
<script setup>
import { computed } from 'vue'
import { useTheme } from '../../composables/useTheme'
import cn from '../../assets/brand/lockup-cn-green.svg'
import en from '../../assets/brand/lockup-en-green.svg'
import bilingual from '../../assets/brand/lockup-bilingual-green.svg'
import cnWhite from '../../assets/brand/lockup-cn-white.svg'
import enWhite from '../../assets/brand/lockup-en-white.svg'
import bilingualWhite from '../../assets/brand/lockup-bilingual-white.svg'
import cnOnDark from '../../assets/brand/lockup-cn-on-dark.svg'
import cnLight from '../../assets/brand/lockup-cn-light.svg'
import enLight from '../../assets/brand/lockup-en-light.svg'
import bilingualLight from '../../assets/brand/lockup-bilingual-light.svg'
const props = defineProps({ variant: { type: String, default: 'cn' }, width: { type: Number, default: 180 }, inverse: { type: Boolean, default: false } })
const { resolved } = useTheme()
const source = computed(() => {
  const white = { cn: cnWhite, en: enWhite, bilingual: bilingualWhite }
  if (props.inverse) return white[props.variant] || cn
  // 深色主题下绿色字标几乎看不见，换成白色版
  if (resolved.value === 'dark') return { cn: cnLight, en: enLight, bilingual: bilingualLight, 'cn-on-dark': cnOnDark }[props.variant] || cnLight
  return { cn, en, bilingual, 'cn-on-dark': cnOnDark }[props.variant] || cn
})
</script>
<style scoped>
.brand-logo-image { display: block; max-width: 100%; height: auto; flex-shrink: 0; }
</style>
