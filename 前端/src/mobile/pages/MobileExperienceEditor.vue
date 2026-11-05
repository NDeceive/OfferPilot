<!--
  写经验：一个主操作（发布），其余字段按必要性排序。
  正文允许用空行分段，发布后按段落渲染；不引入富文本编辑器，手机输入更稳。
-->
<template>
  <MobileShell :nav="false" :messages="false" title="写经验">
    <router-link to="/experience" class="mexe-back">← 返回经验</router-link>

    <form class="mexe-form" @submit.prevent="submit">
      <label class="mexe-field">
        <span>标题</span>
        <input v-model.trim="form.title" maxlength="60" placeholder="这次面试最值得记录的一件事" />
        <small>{{ form.title.length }} / 60</small>
      </label>

      <div class="mexe-grid">
        <label class="mexe-field">
          <span>方向</span>
          <select v-model="form.jobCode" @change="syncJobName">
            <option v-for="job in jobOptions" :key="job.code" :value="job.code">{{ job.name }}</option>
          </select>
        </label>
        <label class="mexe-field">
          <span>公司（选填）</span>
          <input v-model.trim="form.company" maxlength="30" placeholder="例如：字节跳动" />
        </label>
      </div>

      <label class="mexe-field">
        <span>标签（选填）</span>
        <input v-model.trim="form.tags" maxlength="40" placeholder="用空格或逗号分隔，例如：并发 项目深挖" />
      </label>

      <label class="mexe-field">
        <span>正文</span>
        <textarea v-model="form.content" rows="12" maxlength="6000" placeholder="面试问了什么，你怎么答，后来怎么补的"></textarea>
        <small>{{ form.content.length }} / 6000</small>
      </label>

      <p v-if="error" class="mexe-error" role="alert">{{ error }}</p>

      <button type="submit" class="mexe-submit" :disabled="saving">{{ saving ? '正在发布…' : '发布经验' }}</button>
    </form>
  </MobileShell>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import '../styles/mobile.css'
import { createExperiencePost } from '../../services/experienceDemo'
import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const saving = ref(false)
const error = ref('')

const jobOptions = [
  { code: 'BE-JAVA', name: 'Java后端开发' },
  { code: 'BE-GO', name: 'Go后端开发' },
  { code: 'FE-WEB', name: 'Web前端开发' },
  { code: 'ALG-NLP', name: 'NLP与大模型算法' },
  { code: 'PM-AI', name: 'AI产品经理' },
  { code: 'DA-BIZ', name: '业务数据分析师' },
  { code: 'QA-SDET', name: '测试开发质量工程师' },
]

// 从练习报告点「写心得」会带 query 进来；没有 query 时保持原来的空白表单。
function queryText(value) {
  return Array.isArray(value) ? String(value[0] || '') : String(value || '')
}
const presetJob = jobOptions.find(job => job.code === queryText(route.query.jobCode)) || jobOptions[0]
const form = reactive({
  title: queryText(route.query.title).slice(0, 60),
  jobCode: presetJob.code,
  jobName: presetJob.name,
  company: '',
  tags: queryText(route.query.tags).slice(0, 40),
  content: queryText(route.query.content).slice(0, 6000),
})

function syncJobName() {
  form.jobName = jobOptions.find(job => job.code === form.jobCode)?.name || '求职经验'
}

function submit() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    const post = createExperiencePost(form, { nickname: store.nickname, username: store.username })
    router.replace(`/experience/${post.id}`)
  } catch (e) {
    error.value = e.message || '发布失败，请检查内容后重试。'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.mexe-back { display: inline-flex; min-height: 44px; align-items: center; color: var(--m-primary); font-size: 13px; font-weight: 600; transition: opacity var(--m-motion) ease; }
.mexe-back:active { opacity: .6; }
.mexe-form { padding-bottom: 24px; }
.mexe-head { margin-top: 4px; animation: mexe-rise 420ms cubic-bezier(.16, 1, .3, 1) 20ms backwards; }
.mexe-head p { color: var(--m-primary); font-size: 12px; font-weight: 800; letter-spacing: .1em; }
.mexe-head h1 { margin-top: 8px; font-size: 25px; line-height: 1.35; font-weight: 800; letter-spacing: -.035em; }
.mexe-head span { display: block; margin-top: 10px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.7; }
.mexe-field { display: grid; margin-top: 20px; gap: 8px; animation: mexe-rise 380ms cubic-bezier(.16, 1, .3, 1) 90ms backwards; }
.mexe-field > span { color: var(--m-text); font-size: 13px; font-weight: 700; }
.mexe-field input,
.mexe-field select,
.mexe-field textarea {
  width: 100%;
  min-height: 48px;
  padding: 0 13px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font: inherit;
  font-size: 14px;
  transition: border-color var(--m-motion) ease, box-shadow var(--m-motion) ease;
}
.mexe-field input:focus,
.mexe-field select:focus,
.mexe-field textarea:focus { border-color: var(--m-primary); box-shadow: 0 0 0 3px var(--m-primary-soft); outline: none; }
.mexe-field textarea { padding-block: 12px; resize: vertical; line-height: 1.75; }
.mexe-field input::placeholder,
.mexe-field textarea::placeholder { color: var(--m-text-tertiary); }
.mexe-field small { color: var(--m-text-tertiary); font-size: 11.5px; text-align: right; }
.mexe-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; animation: mexe-rise 380ms cubic-bezier(.16, 1, .3, 1) 150ms backwards; }
.mexe-grid .mexe-field { animation: none; }
.mexe-error { margin-top: 16px; padding: 10px 12px; color: #b42318; background: #fff0f1; border-radius: var(--m-radius-input); font-size: 13px; line-height: 1.6; }
.mexe-submit { width: 100%; min-height: 50px; margin-top: 24px; color: #fff; background: var(--m-primary); border: 0; border-radius: var(--m-radius-button); font-size: 15px; font-weight: 700; transition: transform var(--m-motion) ease, filter var(--m-motion) ease, opacity var(--m-motion) ease; }
.mexe-submit:active:not(:disabled) { transform: scale(.985); filter: brightness(.95); }
.mexe-submit:disabled { opacity: .55; }
.mexe-hint { margin-top: 12px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.6; text-align: center; }
.mexe-field input:focus-visible,
.mexe-field select:focus-visible,
.mexe-field textarea:focus-visible,
.mexe-submit:focus-visible,
.mexe-back:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }

@keyframes mexe-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 359px) {
  .mexe-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .mexe-head,
  .mexe-field,
  .mexe-grid { animation: none; }
  .mexe-submit,
  .mexe-back { transition: none; }
}
</style>
