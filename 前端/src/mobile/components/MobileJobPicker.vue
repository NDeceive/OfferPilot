<template>
  <div class="mpick">
    <div class="mobile-search mpick__search">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>
      </svg>
      <input
        :value="query"
        type="search"
        placeholder="搜索岗位，如「后端」「算法」"
        @input="$emit('update:query', $event.target.value)"
      />
    </div>

    <div class="mobile-chip-row mpick__families">
      <button
        type="button"
        :class="{ active: !family }"
        @click="$emit('update:family', '')"
      >
        全部
      </button>
      <button
        v-for="f in families"
        :key="f.code"
        type="button"
        :class="{ active: family === f.code }"
        @click="$emit('update:family', family === f.code ? '' : f.code)"
      >
        {{ f.name }}
      </button>
    </div>

    <p class="mpick__count">
      共 {{ list.length }} 个岗位
      <span v-if="readyCount">· {{ readyCount }} 个可立即面试</span>
    </p>

    <MobileSkeleton v-if="loading" variant="card" :rows="4" label="正在加载岗位" />
    <MobileState
      v-else-if="error"
      kind="error"
      title="岗位加载失败"
      :description="error"
    />
    <MobileState
      v-else-if="!list.length"
      kind="empty"
      title="没有匹配的岗位"
      description="换个关键词或切换岗位族试试。"
    />

    <div v-else class="mpick__list">
      <button
        v-for="j in list"
        :key="j.id"
        type="button"
        class="mjob-card"
        :class="{ 'is-selected': j.id === selectedId, 'is-off': !isReadyJob(j) }"
        :aria-pressed="j.id === selectedId"
        :disabled="!isReadyJob(j)"
        @click="$emit('select', j)"
      >
        <i class="mjob-card__accent" :style="{ background: j.accentColor }" />
        <span class="mjob-card__main">
          <strong>{{ j.title }}</strong>
          <small>{{ j.family || j.category }}</small>
          <span v-if="j.tags.length" class="mjob-card__tags">
            <em v-for="t in j.tags.slice(0, 3)" :key="t">{{ t }}</em>
          </span>
        </span>
        <span class="mjob-card__mark">
          <template v-if="!isReadyJob(j)">敬请期待</template>
          <template v-else-if="j.id === selectedId">✓</template>
          <template v-else>›</template>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MobileState from './MobileState.vue'
import MobileSkeleton from './MobileSkeleton.vue'
import { JOB_FAMILIES, isReadyJob } from '../../utils/jobs'

const props = defineProps({
  jobs: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  query: { type: String, default: '' },
  family: { type: String, default: '' },
  selectedId: { type: [Number, String], default: null },
})

defineEmits(['select', 'update:query', 'update:family'])

// 岗位族用 utils/jobs.js 的 JOB_FAMILIES：它的 code 就是后端 job.family 的原值，
// 32 个岗位一个不漏地落进这 7 个族，不必再维护一份 code 映射表。
const families = JOB_FAMILIES

const list = computed(() => {
  const kw = props.query.trim().toLowerCase()
  return props.jobs
    .filter((j) => !props.family || j.family === props.family)
    .filter((j) => {
      if (!kw) return true
      return (
        j.title.toLowerCase().includes(kw) ||
        (j.family || '').toLowerCase().includes(kw) ||
        j.tags.some((t) => String(t).toLowerCase().includes(kw))
      )
    })
    // 可面试的排前面；同组内保持后端返回的顺序
    .sort((a, b) => (isReadyJob(a) ? 0 : 1) - (isReadyJob(b) ? 0 : 1))
})

const readyCount = computed(() => list.value.filter(isReadyJob).length)
</script>

<style scoped>
.mpick { display: flex; flex-direction: column; }
.mpick__search { margin-bottom: 12px; }
.mpick__families { margin-bottom: 12px; }

.mpick__count { margin: 0 2px 12px; color: var(--m-text-tertiary); font-size: 12px; }
.mpick__count span { color: var(--m-primary); }

.mpick__list { display: flex; flex-direction: column; gap: 10px; }

.mjob-card {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 76px;
  padding: 14px 14px 14px 18px;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  color: var(--m-text);
  text-align: left;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  transition: border-color var(--m-motion), background var(--m-motion), transform var(--m-motion);
}
.mjob-card:active { transform: scale(.99); }
.mjob-card.is-selected { background: var(--m-primary-soft); border-color: #9bbda8; }
.mjob-card.is-off { color: var(--m-text-tertiary); background: #f5f6f3; border-style: dashed; }

.mjob-card__accent { position: absolute; top: 14px; bottom: 14px; left: 0; width: 3px; border-radius: 0 3px 3px 0; }
.mjob-card.is-off .mjob-card__accent { opacity: .35; }

.mjob-card__main { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 3px; }
.mjob-card__main strong { font-size: 15px; line-height: 1.3; overflow-wrap: anywhere; }
.mjob-card__main small { color: var(--m-text-tertiary); font-size: 12px; }
.mjob-card__tags { display: flex; margin-top: 3px; flex-wrap: wrap; gap: 5px; }
.mjob-card__tags em { padding: 2px 7px; color: var(--m-text-secondary); background: rgba(0,0,0,.035); border-radius: 999px; font-size: 11px; font-style: normal; }
.mjob-card.is-selected .mjob-card__tags em { background: rgba(255,255,255,.75); }

.mjob-card__mark { flex: 0 0 auto; color: var(--m-text-tertiary); font-size: 17px; font-weight: 700; }
.mjob-card.is-selected .mjob-card__mark { color: var(--m-primary); font-size: 19px; }
.mjob-card.is-off .mjob-card__mark { padding: 3px 9px; background: var(--m-border); border-radius: 999px; font-size: 11px; }
</style>
