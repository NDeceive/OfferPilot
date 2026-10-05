<template>
  <section class="teaching-inbox" aria-label="教学收件箱">
    <aside class="mail-folders" aria-label="消息分类">
      <h2>收件箱</h2>
      <button v-for="folder in folders" :key="folder.key" :class="{active:category===folder.key}" :aria-pressed="category===folder.key" @click="setCategory(folder.key)">
        <span>{{folder.label}}</span><span class="folder-count">{{count(folder.key)}}</span>
      </button>
      <p>任务发布、班级申请与报告反馈，集中在这里处理。</p>
    </aside>
    <div class="mail-workspace">
      <header class="mail-toolbar">
        <div><h2>{{folders.find(f=>f.key===category)?.label}}</h2><span>{{filtered.length}} 条消息 · {{unread}} 条未读</span></div>
        <label class="mail-search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></svg><input v-model="search" type="search" aria-label="搜索消息" placeholder="搜索标题或消息内容"/></label>
      </header>
      <div class="mail-actions">
        <label class="mail-checkbox"><input type="checkbox" :checked="pageItems.length>0&&pageItems.every(m=>selected.includes(m.id))" :disabled="!pageItems.length" aria-label="选择本页消息" @change="selectPage($event.target.checked)"/>本页</label>
        <button :disabled="!selected.length||busy" @click="markSelected">{{busy?'正在标记…':'标记所选为已读'}}{{selected.length?'（'+selected.length+'）':''}}</button>
        <label class="mail-sort">排序<select v-model="order" aria-label="消息排序"><option value="newest">最新优先</option><option value="unread">未读优先</option></select></label>
      </div>
      <p v-if="operationError" class="mail-operation-error" role="alert">{{operationError}}</p>
      <div class="mail-content" :class="{'has-preview':opened}">
        <div class="mail-list" :class="{'mobile-hidden':opened}">
          <div class="mail-columns" aria-hidden="true"><span></span><span>类型</span><span>主题与摘要</span><span>时间</span></div>
          <article v-for="message in pageItems" :key="message.id" class="mail-row" :class="{unread:!message.isRead,selected:selected.includes(message.id),opened:opened?.id===message.id}">
            <label class="mail-checkbox"><input type="checkbox" :value="message.id" v-model="selected" :aria-label="'选择消息：'+message.title"/></label>
            <button class="mail-open" @click="open(message)">
              <span class="mail-source">{{typeLabel(message)}}<small>{{message.isRead?'已读':'未读'}}</small></span>
              <span class="mail-subject"><strong>{{message.title}}</strong><span>{{message.body}}</span></span>
              <time>{{shortDate(message.createdAt)}}</time>
            </button>
          </article>
          <div v-if="!filtered.length" class="mail-empty">
            <svg viewBox="0 0 48 48" aria-hidden="true"><rect x="7" y="12" width="34" height="25" rx="4"/><path d="m9 15 15 11 15-11"/></svg>
            <h3>{{search?'没有找到匹配的消息':category==='unread'?'未读消息已处理完':'消息会出现在这里'}}</h3>
            <p>{{search?'试试其他关键词，或清除搜索查看全部消息。':'有新的班级申请、任务进展或报告反馈时，会在这里通知你。'}}</p>
            <button v-if="search||category!=='all'" @click="search='';setCategory('all')">查看全部消息</button>
            <RouterLink v-else :to="teacher?'/teacher/classes':'/my/classes'">{{teacher?'前往班级管理':'查看我的班级'}} →</RouterLink>
          </div>
        </div>
        <section v-if="opened" class="mail-preview" aria-label="消息详情">
          <div class="mail-preview-toolbar"><button @click="opened=null">← 返回列表</button><span>{{opened.isRead?'已读':'未读'}}</span></div>
          <span class="mail-category">{{typeLabel(opened)}}</span>
          <h3>{{opened.title}}</h3>
          <dl><dt>来源</dt><dd>OfferPilot 教学通知</dd><dt>时间</dt><dd>{{fullDate(opened.createdAt)}}</dd></dl>
          <p class="mail-text">{{opened.body}}</p>
          <RouterLink :to="safeLink(opened.link)" class="primary" @click="open(opened)">{{destinationLabel(opened)}} →</RouterLink>
          <p class="mail-preview-note">打开关联页面查看最新进展，通知内容保留发送时的状态。</p>
        </section>
      </div>
      <footer class="mail-footer"><span>显示最近 200 条通知 · {{filtered.length?'第 '+((page-1)*pageSize+1)+'–'+Math.min(page*pageSize,filtered.length)+' 条':'暂无消息'}}</span><div><button :disabled="page<=1" @click="page--">上一页</button><span>{{page}} / {{pageCount}}</span><button :disabled="page>=pageCount" @click="page++">下一页</button></div></footer>
    </div>
  </section>
</template>

<script setup>
import {computed,ref,watch} from 'vue'
import {teaching} from '../../services/teachingApi'
const props=defineProps({messages:{type:Array,default:()=>[]},teacher:Boolean})
const category=ref('all'),search=ref(''),order=ref('newest'),selected=ref([]),opened=ref(null),page=ref(1),busy=ref(false),operationError=ref(''),pageSize=12
const folders=[{key:'all',label:'全部消息'},{key:'unread',label:'未读消息'},{key:'class',label:'班级与成员'},{key:'task',label:'任务通知'},{key:'report',label:'报告与点评'}]
function type(message){return /报告|点评|反馈/.test(message.title)?'report':/班级|成员|申请|邀请/.test(message.title)?'class':'task'}
const typeLabel=m=>({report:'报告反馈',class:'班级通知',task:'任务通知'}[type(m)])
function matches(m,key){return key==='all'||key==='unread'&&!m.isRead||type(m)===key}
const count=key=>props.messages.filter(m=>matches(m,key)).length
const unread=computed(()=>count('unread'))
const filtered=computed(()=>props.messages.filter(m=>matches(m,category.value)&&`${m.title} ${m.body}`.toLowerCase().includes(search.value.trim().toLowerCase())).sort((a,b)=>order.value==='unread'&&a.isRead!==b.isRead?Number(a.isRead)-Number(b.isRead):timestamp(b.createdAt)-timestamp(a.createdAt)))
const pageCount=computed(()=>Math.max(1,Math.ceil(filtered.value.length/pageSize)))
const pageItems=computed(()=>filtered.value.slice((page.value-1)*pageSize,page.value*pageSize))
function timestamp(value){return Date.parse(value&&/[Z+]/.test(value)?value:value+'+08:00')}
const fullDate=v=>new Date(timestamp(v)).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
const shortDate=v=>new Date(timestamp(v)).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false})
function setCategory(key){category.value=key;opened.value=null;selected.value=[];page.value=1}
function selectPage(checked){selected.value=checked?[...new Set([...selected.value,...pageItems.value.map(m=>m.id)])]:selected.value.filter(id=>!pageItems.value.some(m=>m.id===id))}
function safeLink(link){return typeof link==='string'&&/^\/(teacher|my)\//.test(link)&&!link.includes('//')?link:props.teacher?'/teacher/dashboard':'/my/tasks'}
const destinationLabel=m=>/reports|history/.test(m.link||'')?'查看报告与点评':/classes/.test(m.link||'')?'查看班级与成员':'查看任务详情'
async function open(message){opened.value=message;operationError.value='';if(message.isRead)return;try{await teaching.read(message.id);message.isRead=true}catch(e){operationError.value=e.message||'已读状态保存失败，请重试。'}}
async function markSelected(){busy.value=true;operationError.value='';const items=props.messages.filter(m=>selected.value.includes(m.id)&&!m.isRead);const results=await Promise.allSettled(items.map(async m=>{await teaching.read(m.id);m.isRead=true}));const failed=results.filter(r=>r.status==='rejected').length;if(failed)operationError.value=`${failed} 条消息未能保存已读状态，请重试。`;else selected.value=[];busy.value=false}
watch([search,order],()=>{page.value=1;selected.value=[]})
watch(pageCount,n=>{page.value=Math.min(page.value,n)})
watch(()=>props.messages,()=>{selected.value=[];if(opened.value)opened.value=props.messages.find(m=>m.id===opened.value.id)||null})
</script>
