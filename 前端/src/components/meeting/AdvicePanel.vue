<template>
  <div class="advice-panel">
    <form v-if="canWrite" class="advice-form" @submit.prevent="send">
      <textarea
        v-model="draft"
        class="advice-input"
        rows="3"
        maxlength="2000"
        placeholder="写下对本次表现的建议…（对方会实时看到）"
      ></textarea>
      <div class="form-foot">
        <span class="counter">{{ draft.length }}/2000</span>
        <button type="submit" class="submit-btn" :disabled="!draft.trim() || submitting">
          {{ submitting ? '发送中…' : '发送建议' }}
        </button>
      </div>
      <p v-if="submitError" class="advice-hint is-error">{{ submitError }}</p>
    </form>
    <p v-else class="advice-hint">建议由面试官（企业 / 教师）填写，这里会实时出现。</p>

    <AdviceList
      :list="list"
      :loading="loading"
      :error="listError"
      empty-text="还没有建议。"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AdviceList from './AdviceList.vue'

const props = defineProps({
  list: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  listError: { type: String, default: '' },
  canWrite: { type: Boolean, default: false },
  /** (content: string) => Promise —— 抛错即在此面板展示 */
  onSubmit: { type: Function, required: true },
})

const draft = ref('')
const submitting = ref(false)
const submitError = ref('')

async function send() {
  const content = draft.value.trim()
  if (!content || submitting.value) return
  submitting.value = true
  submitError.value = ''
  try {
    await props.onSubmit(content)
    draft.value = ''
  } catch (e) {
    submitError.value = e.message || '发送失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.advice-panel { display: flex; flex-direction: column; gap: var(--space-4); }
.advice-form { display: flex; flex-direction: column; gap: var(--space-2); }
.advice-input {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  min-height: 64px;
  padding: 10px 12px;
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--neutral-800);
  line-height: 1.6;
  outline: none;
  transition: border-color var(--duration-fast);
}
.advice-input:focus { border-color: var(--accent-400); }
.form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.counter { font-size: var(--text-xs); color: var(--neutral-300); }
.submit-btn {
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent-500);
  color: #fff;
  font-family: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  padding: 8px 18px;
  cursor: pointer;
  transition: background var(--duration-fast);
}
.submit-btn:hover:not(:disabled) { background: var(--accent-600); }
.submit-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.advice-hint { font-size: var(--text-sm); color: var(--neutral-400); line-height: 1.6; }
.advice-hint.is-error { color: var(--color-error); }
</style>
