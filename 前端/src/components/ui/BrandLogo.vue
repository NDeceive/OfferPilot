<template>
  <img class="brand-logo-image" :src="source" :width="width" :alt="variant === 'en' ? 'OfferPilot' : '智面幻境 OfferPilot'" />
</template>
<script setup>
import { computed } from 'vue'
import cn from '../../assets/brand/lockup-cn-green.svg'
import en from '../../assets/brand/lockup-en-green.svg'
import bilingual from '../../assets/brand/lockup-bilingual-green.svg'
import cnWhite from '../../assets/brand/lockup-cn-white.svg'
import enWhite from '../../assets/brand/lockup-en-white.svg'
import bilingualWhite from '../../assets/brand/lockup-bilingual-white.svg'
import cnOnDark from '../../assets/brand/lockup-cn-on-dark.svg'
const props = defineProps({ variant: { type: String, default: 'cn' }, width: { type: Number, default: 180 }, inverse: { type: Boolean, default: false } })
const source = computed(() => (props.inverse ? { cn: cnWhite, en: enWhite, bilingual: bilingualWhite } : { cn, en, bilingual, 'cn-on-dark': cnOnDark })[props.variant] || cn)
</script>
<style scoped>
.brand-logo-image { display: block; max-width: 100%; height: auto; flex-shrink: 0; }
</style>
