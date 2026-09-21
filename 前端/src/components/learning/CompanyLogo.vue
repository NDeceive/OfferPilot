<template>
  <span class="company-logo" :style="{ '--brand': logo?.hex ? `#${logo.hex}` : fallback.color }" aria-hidden="true">
    <svg v-if="logo" viewBox="0 0 24 24" role="img"><path :d="logo.path" /></svg>
    <span v-else>{{ fallback.mark }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { siAlibabacloud, siAlibabadotcom, siBaidu, siBytedance, siHuawei, siMeituan, siXiaomi } from 'simple-icons'

const props = defineProps({ name: { type: String, default: '' } })
const logos = {
  '字节跳动': siBytedance,
  '美团': siMeituan,
  '华为': siHuawei,
  '阿里巴巴': siAlibabadotcom,
  '阿里云': siAlibabacloud,
  '小米': siXiaomi,
  '百度': siBaidu,
}
const fallbacks = {
  '不限公司': { mark: '▦', color: '#168d63' },
  '腾讯': { mark: '腾', color: '#1769aa' },
  '京东': { mark: 'JD', color: '#e1251b' },
}
const logo = computed(() => logos[props.name])
const fallback = computed(() => fallbacks[props.name] || { mark: props.name.slice(0, 1), color: '#52615c' })
</script>

<style scoped>
.company-logo{display:inline-grid;width:28px;height:28px;place-items:center;flex:0 0 auto;border-radius:8px;background:color-mix(in srgb,var(--brand) 9%,white);color:var(--brand)}
.company-logo svg{width:17px;height:17px;fill:currentColor}
.company-logo>span{font-size:10px;font-weight:800;letter-spacing:-.04em}
</style>
