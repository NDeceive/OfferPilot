<!--
  经验列表：一级信息是「值得点开的经验」，二级是方向和热度，三级是作者与互动。
  主操作只有一个：写经验。筛选只改变列表，不抢主操作的视觉权重。
  值全部取自 mobile.css 的 --m-* token；搜索框和 chip 复用现有移动端样式。
-->
<template>
  <MobileShell brand :nav-links="[]">
    <section class="mexp-head">
      <p class="mexp-kicker">面试经验与诀窍</p>
      <div class="mexp-head-row">
        <h1>把别人的踩坑，<br />变成你的准备</h1>
        <router-link to="/experience/new" class="mexp-write">写经验 <span>＋</span></router-link>
      </div>
      <p class="mexp-sub">来自不同岗位的复盘与临场技巧。先挑一篇现在就能用上的。</p>
    </section>

    <label class="mobile-search mexp-search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg>
      <input v-model.trim="query" type="search" aria-label="搜索经验标题、岗位或标签" placeholder="搜岗位、公司或关键词…" />
    </label>

    <nav class="mexp-views" aria-label="经验范围">
      <button
        v-for="item in viewOptions"
        :key="item.key"
        type="button"
        :class="{ active: view === item.key }"
        :aria-pressed="view === item.key"
        @click="setView(item.key)"
      >{{ item.label }}</button>
    </nav>

    <nav class="mobile-chip-row mexp-topics" aria-label="按方向筛选经验">
      <button v-for="item in experienceTopics" :key="item.key" type="button" :class="{ active: topic === item.key }" :aria-pressed="topic === item.key" @click="topic = item.key">{{ item.label }}</button>
    </nav>

    <template v-if="posts.length">
      <section v-if="featured" class="mexp-feature" aria-label="精选经验">
        <div class="mexp-feature-top"><span>精选</span><time>{{ featured.publishedAt }}</time></div>
        <router-link :to="`/experience/${featured.id}`" class="mexp-feature-link">
          <h2>{{ featured.title }}</h2>
          <p>{{ featured.excerpt }}</p>
          <div class="mexp-feature-meta">
            <span class="mexp-avatar">{{ featured.author.slice(0, 1) }}</span>
            <span>{{ featured.author }} · {{ featured.jobName }}</span>
            <span class="mexp-feature-read">{{ featured.readMinutes }} 分钟</span>
          </div>
        </router-link>
      </section>

      <section class="mexp-list" aria-label="经验列表">
        <article v-for="(post, index) in rest" :key="post.id" class="mexp-card" :style="{ '--i': index }">
          <router-link :to="`/experience/${post.id}`" class="mexp-card-link">
            <div class="mexp-card-meta">
              <span class="mexp-tag">{{ post.jobName }}</span>
              <span v-if="post.company">{{ post.company }}</span>
              <time>{{ post.publishedAt }}</time>
            </div>
            <h2>{{ post.title }}</h2>
            <p>{{ post.excerpt }}</p>
            <div class="mexp-card-foot">
              <span>{{ post.author }}</span>
              <span>{{ post.readMinutes }} 分钟</span>
              <span>♥ {{ post.likeCount }}</span>
            </div>
          </router-link>
        </article>
      </section>

      <p class="mexp-end">已经看到这里了 · 去写一篇你的经验</p>
    </template>

    <MobileState
      v-else
      :title="emptyTitle"
      :description="emptyDescription"
      :action="emptyAction"
      @action="handleEmpty"
    />
  </MobileShell>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import '../styles/mobile.css'
import {
  experienceTopics, listExperience, listFavoriteExperiencePosts, listMyExperiencePosts,
} from '../../services/experienceDemo'

const route = useRoute()
const router = useRouter()
const query = ref('')
const topic = ref('all')

const VIEWS = ['all', 'mine', 'favorites']
const viewOptions = [
  { key: 'all', label: '全部' },
  { key: 'mine', label: '我的发布' },
  { key: 'favorites', label: '我的收藏' },
]
const view = ref(VIEWS.includes(String(route.query.view || '')) ? String(route.query.view) : 'all')

function matches(post) {
  const inTopic = topic.value === 'all' || String(post.jobCode || '').startsWith(`${topic.value}-`)
  const q = query.value.trim().toLowerCase()
  if (!inTopic) return false
  if (!q) return true
  return [post.title, post.excerpt, post.author, post.jobName, post.company, ...(post.tags || [])]
    .filter(Boolean).join(' ').toLowerCase().includes(q)
}

const filtered = computed(() => {
  if (view.value === 'all') return listExperience({ topic: topic.value, query: query.value })
  const pool = view.value === 'mine' ? listMyExperiencePosts() : listFavoriteExperiencePosts()
  return pool.filter(matches)
})
const featured = computed(() => view.value === 'all' ? filtered.value.find(post => post.featured) || null : null)
const rest = computed(() => featured.value ? filtered.value.filter(post => post.id !== featured.value.id) : filtered.value)
const posts = computed(() => filtered.value)

const emptyTitle = computed(() => {
  if (view.value === 'mine') return '还没有发布过经验'
  if (view.value === 'favorites') return '还没有收藏经验'
  return '没有找到匹配的经验'
})
const emptyDescription = computed(() => {
  if (view.value === 'mine') return '把一次面试或练习复盘写下来，会出现在这里。'
  if (view.value === 'favorites') return '看到有用的经验，点收藏就会收进这里。'
  if (query.value) return `没有和「${query.value}」相关的经验，换个关键词试试。`
  return '这个方向还没有经验，来写第一篇吧。'
})
const emptyAction = computed(() => view.value === 'mine' ? '写一篇' : view.value === 'favorites' ? '看全部经验' : '清除筛选')

function setView(key) {
  if (!VIEWS.includes(key) || view.value === key) return
  view.value = key
  router.replace({ query: { ...route.query, view: key === 'all' ? undefined : key } })
}

function handleEmpty() {
  if (view.value === 'mine') { router.push('/experience/new'); return }
  resetFilters()
}

watch(() => route.query.view, value => {
  const next = VIEWS.includes(String(value || '')) ? String(value) : 'all'
  if (next !== view.value) view.value = next
})

function resetFilters() {
  query.value = ''
  topic.value = 'all'
  setView('all')
}
</script>

<style scoped>
.mexp-head { padding: 8px 0 2px; }
.mexp-kicker { color: var(--m-primary); font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.mexp-head-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-top: 8px; }
.mexp-head h1 { font-size: 27px; line-height: 1.28; font-weight: 800; letter-spacing: -.045em; }
.mexp-write {
  display: inline-flex;
  min-height: 44px;
  padding: 0 14px;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
  transition: transform var(--m-motion) ease, background var(--m-motion) ease;
}
.mexp-write:active { transform: scale(.94); background: #d8ebe0; }
.mexp-write span { font-size: 17px; line-height: 1; }
.mexp-sub { margin-top: 12px; color: var(--m-text-secondary); font-size: 13.5px; line-height: 1.7; }

.mexp-search { margin-top: 20px; transition: border-color var(--m-motion) ease, box-shadow var(--m-motion) ease; }
.mexp-search:focus-within { border-color: var(--m-primary); box-shadow: 0 0 0 3px var(--m-primary-soft); }
.mexp-views { display: flex; gap: 4px; margin-top: 16px; padding: 4px; background: var(--m-surface-soft); border-radius: 12px; }
.mexp-views button {
  flex: 1;
  min-height: 44px;
  padding: 0 8px;
  color: var(--m-text-secondary);
  background: transparent;
  border: 0;
  border-radius: 9px;
  font-size: 12.5px;
  font-weight: 700;
  transition: background var(--m-motion) ease, color var(--m-motion) ease, box-shadow var(--m-motion) ease;
}
.mexp-views button.active { color: var(--m-primary-dark); background: var(--m-surface); box-shadow: 0 1px 5px rgba(32, 61, 48, .08); }
.mexp-views button:active { transform: scale(.97); }
.mexp-topics { margin-top: 14px; }
.mexp-topics button { transition: transform var(--m-motion) ease, background var(--m-motion) ease, color var(--m-motion) ease; }
.mexp-topics button:active { transform: scale(.94); }

.mexp-feature {
  margin-top: 20px;
  padding: 18px;
  color: #fff;
  background: linear-gradient(145deg, #174b39 0%, #2f7458 100%);
  border-radius: var(--m-radius-hero);
  box-shadow: 0 14px 28px rgba(23, 75, 57, .18);
  animation: mexp-rise 460ms cubic-bezier(.16, 1, .3, 1) 60ms backwards;
}
.mexp-feature-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #d7e7dd; font-size: 11.5px; }
.mexp-feature-top span { padding: 3px 8px; color: #3b2a12; background: var(--m-accent); border-radius: 999px; font-weight: 800; letter-spacing: .06em; }
.mexp-feature-link { display: block; margin-top: 14px; color: #fff; text-decoration: none; transition: transform var(--m-motion) ease, opacity var(--m-motion) ease; }
.mexp-feature-link:active { transform: scale(.99); opacity: .92; }
.mexp-feature h2 { font-size: 21px; line-height: 1.4; font-weight: 800; letter-spacing: -.025em; }
.mexp-feature p { margin-top: 10px; color: #d7e7dd; font-size: 13.5px; line-height: 1.7; }
.mexp-feature-meta { display: flex; align-items: center; gap: 8px; margin-top: 16px; color: #d7e7dd; font-size: 12px; }
.mexp-avatar { display: grid; width: 26px; height: 26px; color: #174b39; background: #d7e7dd; border-radius: 50%; font-size: 12px; font-weight: 800; place-items: center; }
.mexp-feature-read { margin-left: auto; white-space: nowrap; }

.mexp-list { display: flex; margin-top: 12px; flex-direction: column; gap: 10px; }
.mexp-card {
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  box-shadow: 0 2px 10px rgba(32, 61, 48, .035);
  transition: transform var(--m-motion) ease, box-shadow var(--m-motion) ease, border-color var(--m-motion) ease;
  animation: mexp-rise 400ms cubic-bezier(.16, 1, .3, 1) backwards;
  animation-delay: calc(90ms + var(--i, 0) * 45ms);
}
.mexp-card:active { transform: scale(.985); border-color: #d5e6db; box-shadow: 0 1px 5px rgba(32, 61, 48, .05); }
.mexp-card-link { display: block; padding: 16px; color: var(--m-text); text-decoration: none; }
.mexp-card-meta { display: flex; align-items: center; gap: 8px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.5; }
.mexp-card-meta time { margin-left: auto; white-space: nowrap; }
.mexp-tag { padding: 3px 7px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 5px; font-weight: 700; }
.mexp-card h2 { margin-top: 9px; font-size: 17px; line-height: 1.45; font-weight: 700; letter-spacing: -.015em; }
.mexp-card p { display: -webkit-box; margin-top: 6px; overflow: hidden; color: var(--m-text-secondary); font-size: 13px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.mexp-card-foot { display: flex; align-items: center; gap: 12px; margin-top: 12px; color: var(--m-text-tertiary); font-size: 11.5px; }
.mexp-card-foot span:first-child { color: var(--m-text-secondary); font-weight: 600; }

.mexp-end { margin-top: 22px; padding-bottom: 8px; color: var(--m-text-tertiary); font-size: 12px; text-align: center; }

@keyframes mexp-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (hover: hover) {
  .mexp-card:hover { transform: translateY(-2px); border-color: #d5e6db; box-shadow: 0 8px 20px rgba(32, 61, 48, .07); }
}

@media (max-width: 359px) {
  .mexp-head h1 { font-size: 24px; }
  .mexp-feature h2 { font-size: 19px; }
}

@media (prefers-reduced-motion: reduce) {
  .mexp-write,
  .mexp-card,
  .mexp-feature,
  .mexp-topics button { animation: none; transition: none; }
}
</style>
