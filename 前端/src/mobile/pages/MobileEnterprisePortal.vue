<template>
  <MobileShell v-bind="shell">
    <section class="mobile-greeting">
      <p>企业会议中心</p>
      <h1>开启一场视频面试</h1>
    </section>

    <!-- 开启会议：手机上创建后不直接跳房间（房间是电脑端功能），先亮出会议号让用户复制转发 -->
    <section class="mobile-info-card mep__create">
      <template v-if="!created">
        <h2>开启会议</h2>
        <label>会议主题<input v-model.trim="createTitle" type="text" maxlength="80" placeholder="例如：前端实习生面试 / 作品点评" @keyup.enter="handleCreate" /></label>
        <button type="button" class="is-primary" :disabled="creating || !createTitle.trim()" @click="handleCreate">
          {{ creating ? '创建中…' : '开启会议' }}
        </button>
        <p v-if="createError" class="mep__error" role="alert">{{ createError }}</p>
        <p class="mep__hint">创建后生成 8 位会议号（如 3F7K-2Q9A），发给学生或教师即可凭号进入。</p>
      </template>
      <template v-else>
        <h2>会议已创建</h2>
        <p class="mep__created-title">{{ created.title }}</p>
        <div class="mep__code">
          <code>{{ created.codeDisplay || created.code }}</code>
          <button type="button" @click="copyText(created.codeDisplay || created.code, 'created')">{{ copiedId === 'created' ? '已复制' : '复制会议号' }}</button>
        </div>
        <router-link :to="`/meeting/${created.code}/room`" class="mep__enter">进入会议室 →</router-link>
        <button type="button" class="mep__again" @click="created = null">再开一场</button>
      </template>
    </section>

    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>我的会议</h2><span>{{ hosted.length + joined.length }} 场</span></header>
      <div class="mobile-segments mep__tabs" role="tablist">
        <button :class="{ active: tab === 'hosted' }" @click="tab = 'hosted'">我发起的 {{ hosted.length }}</button>
        <button :class="{ active: tab === 'joined' }" @click="tab = 'joined'">我参与的 {{ joined.length }}</button>
      </div>

      <MobileSkeleton v-if="loading" variant="card" :rows="2" label="正在加载会议列表" />
      <MobileState v-else-if="loadError" kind="error" title="会议列表暂时无法加载" :description="loadError" action="重新加载" @action="loadMine" />
      <div v-else-if="currentList.length" class="mep__list">
        <article v-for="row in currentList" :key="row.id" class="mobile-info-card mep__row">
          <div class="mobile-row">
            <div class="mobile-grow">
              <h3>{{ row.title }}</h3>
              <p>{{ formatTime(row.joinTime || row.createdAt) }}<template v-if="tab === 'hosted' && row.participantCount != null"> · {{ row.participantCount }} 人参与过</template><template v-else-if="row.hostName"> · 发起人：{{ row.hostName }}</template></p>
            </div>
            <span class="mep__pill" :class="row.status === 'OPEN' ? 'is-open' : 'is-ended'">{{ row.status === 'OPEN' ? '进行中' : '已结束' }}</span>
          </div>
          <div class="mep__row-actions">
            <button type="button" class="mep__code-chip" @click="copyText(row.codeDisplay || row.code, row.id)">{{ copiedId === row.id ? '已复制' : (row.codeDisplay || row.code) }}</button>
            <router-link v-if="row.status === 'OPEN'" :to="`/meeting/${row.code}/room`" class="is-primary">进入</router-link>
            <button v-if="row.status === 'OPEN' && tab === 'hosted'" type="button" :disabled="endingId === row.id" @click="handleEnd(row)">{{ endingId === row.id ? '结束中…' : '结束' }}</button>
            <button type="button" @click="toggleAdvice(row)">{{ adviceOpenId === row.id ? '收起建议' : '建议' }}</button>
          </div>
          <div v-if="adviceOpenId === row.id" class="mep__advice">
            <AdvicePanel
              :list="adviceList"
              :loading="adviceLoading"
              :list-error="adviceError"
              :can-write="true"
              :on-submit="handleSubmitAdvice"
            />
          </div>
        </article>
      </div>
      <MobileState
        v-else
        :title="tab === 'hosted' ? '还没有发起过会议' : '还没有参与过他人的会议'"
        :description="tab === 'hosted' ? '填个主题，开启第一场视频面试吧。' : '收到会议号后，到「加入会议」输入即可进入。'"
      />
    </section>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdvicePanel from '../../components/meeting/AdvicePanel.vue'
import { createMeeting, endMeeting, getMeetingAdvice, getMyMeetings, postMeetingAdvice } from '../../api/meeting'
import { formatTime } from '../../utils/time'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { ENTERPRISE_TABS } from '../nav'

const shell = {
  brand: true,
  homePath: '/enterprise/dashboard',
  profilePath: '/enterprise/account',
  messages: false,
  navLinks: [],
  tabItems: ENTERPRISE_TABS,
}

/* ---------- 开启会议 ---------- */
const createTitle = ref('')
const creating = ref(false)
const createError = ref('')
const created = ref(null)

async function handleCreate() {
  const title = createTitle.value.trim()
  if (!title || creating.value) return
  creating.value = true
  createError.value = ''
  try {
    created.value = await createMeeting({ title })
    createTitle.value = ''
    loadMine()
  } catch (e) {
    createError.value = e.message || '创建会议失败，请重试'
  } finally {
    creating.value = false
  }
}

/* ---------- 我的会议 ---------- */
const loading = ref(true)
const loadError = ref('')
const hosted = ref([])
const joined = ref([])
const tab = ref('hosted')
const endingId = ref(null)
const copiedId = ref(null)

const currentList = computed(() => (tab.value === 'hosted' ? hosted.value : joined.value))

async function loadMine() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await getMyMeetings()
    hosted.value = data?.hosted || []
    joined.value = data?.joined || []
  } catch (e) {
    loadError.value = e.message || '加载会议列表失败'
  } finally {
    loading.value = false
  }
}

async function handleEnd(row) {
  if (endingId.value) return
  if (!window.confirm(`结束「${row.title}」？房间里的成员会被移出会议。`)) return
  endingId.value = row.id
  try {
    await endMeeting(row.id)
    row.status = 'ENDED'
  } catch (e) {
    window.alert(e.message || '结束会议失败，请重试')
  } finally {
    endingId.value = null
  }
}

async function copyText(text, key) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copiedId.value = key
    setTimeout(() => { if (copiedId.value === key) copiedId.value = null }, 1500)
  } catch {
    // 非安全上下文里 clipboard 不可用：退化成手动选中（与桌面端同款兜底）
    window.prompt('复制会议号：', text)
  }
}

/* ---------- 建议：展开即拉取，写建议实时插到最前 ---------- */
const adviceOpenId = ref(null)
const adviceList = ref([])
const adviceLoading = ref(false)
const adviceError = ref('')

async function toggleAdvice(row) {
  if (adviceOpenId.value === row.id) {
    adviceOpenId.value = null
    return
  }
  adviceOpenId.value = row.id
  adviceList.value = []
  adviceError.value = ''
  adviceLoading.value = true
  try {
    adviceList.value = (await getMeetingAdvice(row.id)) || []
  } catch (e) {
    adviceError.value = e.message || '加载建议失败'
  } finally {
    adviceLoading.value = false
  }
}

async function handleSubmitAdvice(content) {
  const item = await postMeetingAdvice(adviceOpenId.value, { content })
  adviceList.value = [item, ...adviceList.value]
}

onMounted(loadMine)
</script>

<style scoped>
.mep__create h2 { font-size: 16px; }
.mep__create label { display: grid; margin-top: 14px; gap: 6px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 600; }
.mep__create input {
  min-height: 46px;
  padding: 0 12px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font: inherit;
  font-size: 14px;
}
.mep__create button.is-primary {
  width: 100%;
  min-height: 46px;
  margin-top: 14px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 14.5px;
  font-weight: 700;
}
.mep__create button.is-primary:disabled { opacity: .55; }
.mep__hint { margin-top: 10px; color: var(--m-text-tertiary); font-size: 12px; line-height: 1.6; }
.mep__error { margin-top: 10px; color: #b42318; font-size: 12.5px; }

.mep__created-title { margin-top: 6px; color: var(--m-text-secondary); font-size: 13.5px; }
.mep__code { display: flex; margin-top: 14px; align-items: center; gap: 10px; }
.mep__code code { flex: 1; padding: 12px 14px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 10px; font-size: 20px; font-weight: 800; letter-spacing: .1em; text-align: center; }
.mep__code button { min-height: 46px; padding: 0 16px; color: #fff; background: var(--m-primary); border: 0; border-radius: var(--m-radius-button); font-size: 13px; font-weight: 700; white-space: nowrap; }
.mep__enter { display: flex; min-height: 46px; margin-top: 12px; align-items: center; justify-content: center; color: var(--m-text-secondary); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-button); font-size: 13.5px; font-weight: 700; }
.mep__again { width: 100%; min-height: 44px; margin-top: 10px; color: var(--m-primary); background: none; border: 0; font-size: 13.5px; font-weight: 700; }

.mep__tabs { margin-bottom: 2px; }
.mep__list { display: flex; margin-top: 14px; flex-direction: column; gap: 12px; }
.mep__row h3 { font-size: 15px; overflow-wrap: anywhere; }
.mep__pill { flex: 0 0 auto; padding: 4px 10px; border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mep__pill.is-open { color: #1d6b4f; background: var(--surface-soft); }
.mep__pill.is-ended { color: var(--m-text-tertiary); background: var(--surface-soft); }
.mep__row-actions { display: flex; margin-top: 12px; padding-top: 10px; gap: 8px; flex-wrap: wrap; border-top: 1px dashed var(--m-border); }
.mep__row-actions > * { display: inline-flex; min-height: 40px; padding: 0 14px; align-items: center; color: var(--m-text-secondary); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-button); font-size: 13px; font-weight: 700; }
.mep__row-actions > .is-primary { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mep__row-actions > button:disabled { opacity: .55; }
.mep__code-chip { font-family: inherit; letter-spacing: .06em; }
.mep__advice { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--m-border); }
</style>
