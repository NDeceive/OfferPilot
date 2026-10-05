<template><div ref="root" class="search-choice" @focusout="leave" @keydown.esc.stop.prevent="close"><span class="choice-label">{{label}}</span><button type="button" :aria-label="label" :aria-expanded="open" @click="toggle"><span>{{options.find(o=>String(o.value)===String(modelValue))?.label||placeholder}}</span><span aria-hidden="true">⌄</span></button><div v-if="open" class="choice-menu" @keydown.esc.stop.prevent="close"><input ref="input" v-model="search" type="search" :aria-label="'搜索'+label" :placeholder="'搜索'+label"/><p>{{filtered.length}}项匹配</p><div class="choice-options"><button v-for="o in filtered" :key="o.value" type="button" :aria-pressed="String(o.value)===String(modelValue)" @click="emit('update:modelValue',String(o.value));close()">{{o.label}}</button><p v-if="!filtered.length">没有匹配项，请更换关键词。</p></div></div></div></template>
<script setup>
import {ref,computed,nextTick,onMounted,onUnmounted} from 'vue'
const props=defineProps({modelValue:String,label:String,placeholder:String,options:{type:Array,default:()=>[]}}),emit=defineEmits(['update:modelValue'])
const root=ref(null),input=ref(null),open=ref(false),search=ref(''),filtered=computed(()=>props.options.filter(o=>o.label.toLowerCase().includes(search.value.trim().toLowerCase())))
async function toggle(){open.value=!open.value;search.value='';if(open.value){await nextTick();input.value?.focus()}}
function close(){open.value=false;root.value?.querySelector('button')?.focus()}
function leave(e){if(e.relatedTarget&&!root.value?.contains(e.relatedTarget))open.value=false}
function outside(e){if(open.value&&!root.value?.contains(e.target))open.value=false}
onMounted(()=>document.addEventListener('pointerdown',outside));onUnmounted(()=>document.removeEventListener('pointerdown',outside))
</script>
