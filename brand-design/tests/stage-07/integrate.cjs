const fs=require('fs'),path=require('path');const root=path.resolve(__dirname,'../../..'),front=path.join(root,'前端'),pack=path.join(root,'brand-design/finals/01A-A-bold/svg');
const assets=path.join(front,'src/assets/brand');fs.mkdirSync(assets,{recursive:true});for(const n of ['symbol-green','symbol-white','micro-green','micro-white','lockup-cn-green','lockup-en-green','lockup-bilingual-green','lockup-cn-white','lockup-en-white','lockup-bilingual-white'])fs.copyFileSync(path.join(pack,n+'.svg'),path.join(assets,n+'.svg'));
function write(file,s){fs.writeFileSync(path.join(front,file),s);}function modify(file,fn){let p=path.join(front,file);let s=fs.readFileSync(p,'utf8');fs.writeFileSync(p,fn(s));}
write('src/components/ui/LogoIcon.vue',`<template>
  <img class="logo-icon" :src="source" :width="size" :height="size" alt="" aria-hidden="true" />
</template>
<script setup>
import { computed } from 'vue'
import standard from '../../assets/brand/symbol-green.svg'
import micro from '../../assets/brand/micro-green.svg'
import white from '../../assets/brand/symbol-white.svg'
import microWhite from '../../assets/brand/micro-white.svg'
const props = defineProps({ size: { type: Number, default: 32 }, inverse: { type: Boolean, default: false } })
const source = computed(() => props.inverse ? (props.size < 32 ? microWhite : white) : (props.size < 32 ? micro : standard))
</script>
<style scoped>
.logo-icon { display: inline-block; flex-shrink: 0; object-fit: contain; }
</style>
`);
write('src/components/ui/BrandLogo.vue',`<template>
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
const props = defineProps({ variant: { type: String, default: 'cn' }, width: { type: Number, default: 180 }, inverse: { type: Boolean, default: false } })
const source = computed(() => (props.inverse ? { cn: cnWhite, en: enWhite, bilingual: bilingualWhite } : { cn, en, bilingual })[props.variant] || cn)
</script>
<style scoped>
.brand-logo-image { display: block; max-width: 100%; height: auto; flex-shrink: 0; }
</style>
`);
for(const view of ['Login','Register','ForgotPassword'])modify(`src/views/${view}.vue`,s=>s.replace(/<LogoIcon :size="36"\s*\/>\s*<span class="(?:stage-brand|brand-name)">[\s\S]*?<\/span>\s*<\/span>/,'<BrandLogo variant="bilingual" :width="210" />').replace("import LogoIcon from '../components/ui/LogoIcon.vue'","import BrandLogo from '../components/ui/BrandLogo.vue'"));
modify('src/components/layout/AppLayout.vue',s=>s.replace(/<LogoIcon :size="28"\s*\/>\s*<span class="nav-logo-text">[\s\S]*?<\/span>\s*<\/span>/,'<BrandLogo :variant="isTeacherRoute ? \'en\' : \'cn\'" :width="isTeacherRoute ? 185 : 180" />\n          <span v-if="isTeacherRoute" class="brand-en">教师端</span>').replace("import LogoIcon from '../ui/LogoIcon.vue'","import BrandLogo from '../ui/BrandLogo.vue'"));
modify('src/mobile/components/MobileShell.vue',s=>s.replace(/<LogoIcon :size="40"\s*\/>\s*<span><strong>OfferPilot<\/strong><small>智面幻境<\/small><\/span>/,'<BrandLogo :width="180" />').replace("import LogoIcon from '../../components/ui/LogoIcon.vue'","import BrandLogo from '../../components/ui/BrandLogo.vue'"));
modify('src/layout/MainLayout.vue',s=>s.replace("@/assets/generated/brand-mark-ui.png","@/assets/brand/symbol-green.svg"));
modify('src/components/layout/NavBar.vue',s=>s.replace(/<div class="logo-icon">[\s\S]*?<\/div>/,'<LogoIcon :size="32" />').replace('<script setup>','<script setup>\nimport LogoIcon from \'../ui/LogoIcon.vue\''));
write('src/components/ui/LogoAnimated.vue',`<template><LogoIcon :size="size" /></template>
<script setup>
import LogoIcon from './LogoIcon.vue'
defineProps({ size: { type: Number, default: 120 } })
</script>
`);
fs.copyFileSync(path.join(pack,'micro-green.svg'),path.join(front,'public/favicon.svg'));
fs.copyFileSync(path.join(pack,'symbol-green.svg'),path.join(front,'assets/logo.svg'));
console.log('Brand integrated.');
