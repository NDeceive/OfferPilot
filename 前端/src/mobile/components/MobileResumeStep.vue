<template>
  <div class="mres">
    <!-- 已解析 / 已保存 -->
    <section v-if="fileName" class="mres__done">
      <div class="mres__file">
        <span class="mres__file-icon">📄</span>
        <span class="mres__file-name">
          <strong>{{ fileName }}</strong>
          <small>
            <template v-if="projectCount">{{ projectCount }} 段项目经历 · </template>
            识别到 {{ skills.length }} 个技能标签
          </small>
        </span>
        <button type="button" class="mres__remove" @click="$emit('remove')">重选</button>
      </div>

      <div v-if="skills.length" class="mres__skills">
        <span v-for="(s, i) in skills" :key="s" class="mres__skill">
          {{ s }}
          <button type="button" :aria-label="`删除 ${s}`" @click="$emit('remove-skill', i)">×</button>
        </span>
      </div>
      <p v-else class="mres__warn">没识别到技能标签，可以去下面手动补几个，出题会用到。</p>

      <button type="button" class="mres__toggle" @click="toggleTags">
        {{ tagsOpen ? '收起标签库' : '从标签库补充技能' }}
        <span>{{ tagsOpen ? '⌃' : '⌄' }}</span>
      </button>

      <div v-if="tagsOpen" class="mres__taglib">
        <p v-if="tagsLoading" class="mres__taglib-hint">正在加载标签库…</p>
        <p v-else-if="tagsError" class="mres__taglib-hint">{{ tagsError }}</p>
        <template v-else>
          <button
            v-for="t in allTags"
            :key="t.id || t.name"
            type="button"
            class="mres__tag"
            :class="{ 'is-on': skills.includes(t.name) }"
            @click="$emit('toggle-tag', t.name)"
          >
            {{ t.name }}
          </button>
        </template>
      </div>
    </section>

    <!-- 未上传：点击上传 -->
    <template v-else>
      <div
        class="mdrop"
        :class="{ 'is-busy': uploading }"
        role="button"
        tabindex="0"
        @click="input?.click()"
        @keydown.enter.prevent="input?.click()"
        @keydown.space.prevent="input?.click()"
      >
        <input ref="input" type="file" accept=".pdf,.doc,.docx" hidden @change="onChange" />
        <svg class="mdrop__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <path d="M14 2v6h6"/><path d="M12 18v-6"/><path d="m9 15 3-3 3 3"/>
        </svg>
        <template v-if="uploading">
          <p class="mdrop__title">正在解析简历…</p>
          <p class="mdrop__hint">通常只需要几秒</p>
        </template>
        <template v-else>
          <p class="mdrop__title">点击上传简历</p>
          <p class="mdrop__hint">支持 PDF / Word · 不超过 10MB</p>
        </template>
      </div>

      <button
        v-if="savedResume?.filename"
        type="button"
        class="mres__saved"
        @click="$emit('use-saved')"
      >
        <span class="mres__saved-icon">↻</span>
        <span>
          <strong>用上次那份「{{ savedResume.filename }}」</strong>
          <small>已解析 {{ (savedResume.skills || []).length }} 个技能标签</small>
        </span>
      </button>

      <button type="button" class="mres__link" @click="onlineOpen = true">
        没有简历文件？在线填一份 →
      </button>
    </template>

    <p v-if="error" class="merr">{{ error }}</p>

    <!-- 内部是 Teleport to="body"，能跳出容器 -->
    <OnlineResumeDialog v-model="onlineOpen" @saved="onOnlineSaved" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import OnlineResumeDialog from '../../components/resume/OnlineResumeDialog.vue'

const props = defineProps({
  skills: { type: Array, default: () => [] },
  fileName: { type: String, default: '' },
  projectCount: { type: Number, default: 0 },
  uploading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  savedResume: { type: Object, default: null },
  allTags: { type: Array, default: () => [] },
  tagsLoading: { type: Boolean, default: false },
  tagsError: { type: String, default: '' },
})

const emit = defineEmits([
  'file', 'use-saved', 'remove', 'remove-skill', 'toggle-tag', 'saved-online', 'open-taglib',
])

const input = ref(null)
const onlineOpen = ref(false)
const tagsOpen = ref(false)

function toggleTags() {
  tagsOpen.value = !tagsOpen.value
  // 标签库是懒加载的：不展开就不请求，展开时才让父组件去拉
  if (tagsOpen.value) emit('open-taglib')
}

function onChange(e) {
  const file = e.target.files?.[0]
  e.target.value = '' // 允许重复选同一个文件
  if (file) emit('file', file)
}

function onOnlineSaved(payload) {
  emit('saved-online', payload)
}
</script>

<style scoped>
.mres { display: flex; flex-direction: column; gap: 12px; }

/* 上传区：移动端没有拖拽，整块可点 */
.mdrop {
  display: flex;
  width: 100%;
  min-height: 190px;
  padding: 26px 20px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 6px;
  text-align: center;
  background: var(--m-surface);
  border: 1.5px dashed #b9d2c3;
  border-radius: var(--m-radius-card);
  transition: border-color var(--m-motion), background var(--m-motion);
}
.mdrop:active { background: var(--m-surface-soft); border-color: var(--m-primary); }
.mdrop.is-busy { border-style: solid; border-color: var(--m-primary); }
.mdrop__icon { width: 40px; height: 40px; margin-bottom: 4px; color: var(--m-primary); }
.mdrop__title { font-size: 15px; font-weight: 700; }
.mdrop__hint { color: var(--m-text-tertiary); font-size: 12px; }

.mres__saved {
  display: flex;
  width: 100%;
  min-height: 68px;
  padding: 12px 14px;
  align-items: center;
  gap: 12px;
  color: var(--m-text);
  text-align: left;
  background: var(--m-surface-soft);
  border: 1px solid transparent;
  border-radius: var(--m-radius-card);
}
.mres__saved:active { border-color: var(--m-primary); }
.mres__saved-icon { display: grid; flex: 0 0 auto; width: 34px; height: 34px; color: var(--m-primary); place-items: center; background: rgba(255,255,255,.75); border-radius: 50%; font-size: 17px; }
.mres__saved strong { display: block; font-size: 14px; overflow-wrap: anywhere; }
.mres__saved small { display: block; margin-top: 3px; color: var(--m-text-tertiary); font-size: 12px; }

.mres__link { min-height: 44px; color: var(--m-primary); background: transparent; border: 0; font-size: 13px; font-weight: 700; }

/* ---- 已上传态 ---- */
.mres__done { display: flex; flex-direction: column; gap: 12px; }
.mres__file {
  display: flex;
  padding: 14px;
  align-items: center;
  gap: 12px;
  background: var(--m-surface-soft);
  border-radius: var(--m-radius-card);
}
.mres__file-icon { flex: 0 0 auto; font-size: 22px; }
.mres__file-name { flex: 1; min-width: 0; }
.mres__file-name strong { display: block; font-size: 14px; overflow-wrap: anywhere; }
.mres__file-name small { display: block; margin-top: 3px; color: var(--m-text-secondary); font-size: 12px; }
.mres__remove { flex: 0 0 auto; min-height: 36px; padding: 0 12px; color: var(--m-primary-dark); background: rgba(255,255,255,.8); border: 0; border-radius: 999px; font-size: 12px; font-weight: 700; }

.mres__skills { display: flex; flex-wrap: wrap; gap: 7px; }
.mres__skill {
  display: inline-flex;
  padding: 5px 6px 5px 11px;
  align-items: center;
  gap: 4px;
  color: var(--m-primary-dark);
  background: var(--m-primary-soft);
  border-radius: 999px;
  font-size: 12.5px;
}
.mres__skill button { width: 18px; height: 18px; color: var(--m-primary-dark); background: rgba(255,255,255,.7); border: 0; border-radius: 50%; font-size: 13px; line-height: 1; }

.mres__warn { padding: 10px 12px; color: #7c4315; background: var(--m-accent-soft); border-radius: 10px; font-size: 12px; line-height: 1.5; }

.mres__toggle {
  display: flex;
  width: 100%;
  min-height: 44px;
  padding: 0 14px;
  align-items: center;
  justify-content: space-between;
  color: var(--m-primary-dark);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font-size: 13px;
  font-weight: 600;
}
.mres__taglib { display: flex; padding: 12px; flex-wrap: wrap; gap: 7px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mres__taglib-hint { color: var(--m-text-tertiary); font-size: 12px; }
.mres__tag { min-height: 34px; padding: 0 12px; color: var(--m-text-secondary); background: #f2f4f0; border: 1px solid transparent; border-radius: 999px; font-size: 12.5px; }
.mres__tag.is-on { color: #fff; background: var(--m-primary); }

.merr { padding: 10px 12px; color: #7c4315; background: var(--m-accent-soft); border-radius: 10px; font-size: 12px; line-height: 1.5; }
</style>
