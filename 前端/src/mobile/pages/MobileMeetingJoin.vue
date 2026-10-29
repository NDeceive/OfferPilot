<template>
  <MobileShell v-bind="shell" :title="pageTitle" :subtitle="pageSub">
    <!-- 会议号查找 -->
    <section class="mobile-info-card mmj__lookup">
      <label for="mmj-code">会议号</label>
      <div class="mmj__input-row">
        <input
          id="mmj-code"
          :value="codeInput"
          type="text"
          maxlength="9"
          autocomplete="off"
          spellcheck="false"
          placeholder="例如 3F7K-2Q9A"
          @input="onCodeInput"
          @keyup.enter="handleLookup"
        />
        <button type="button" :disabled="looking || !normalizedInput" @click="handleLookup">{{ looking ? '查找中…' : '查找' }}</button>
      </div>
      <p v-if="lookupError" class="mmj__error" role="alert">{{ lookupError }}</p>

      <div v-if="found" class="mmj__found">
        <h3>{{ found.title }}</h3>
        <p>主持人：{{ found.hostName || '—' }} · {{ found.participantCount }} 人在会中 · <b :class="found.joinable ? 'is-ok' : 'is-bad'">{{ foundStatusText }}</b></p>
        <router-link v-if="found.joinable" :to="`/meeting/${found.code}/room`" class="mmj__enter">进入会议室</router-link>
        <p class="mmj__enter-note">进入后会请求摄像头与麦克风权限，请点「允许」。</p>
      </div>
    </section>

    <!-- 我发起的会议：仅企业/教师/管理员有数据，学生天然为空 -->
    <section v-if="hostedList.length" class="mobile-section">
      <header class="mobile-section-heading"><h2>我发起的会议</h2></header>
      <div class="mmj__list">
        <article v-for="row in hostedList" :key="'h-' + row.id" class="mobile-info-card mmj__row">
          <div class="mobile-row">
            <div class="mobile-grow">
              <h3>{{ row.title }}</h3>
              <p>{{ row.codeDisplay || row.code }} · {{ formatTime(row.createdAt) }}<template v-if="row.participantCount != null"> · {{ row.participantCount }} 人参与过</template></p>
            </div>
            <span class="mmj__pill" :class="row.status === 'OPEN' ? 'is-open' : 'is-ended'">{{ row.status === 'OPEN' ? '进行中' : '已结束' }}</span>
          </div>
          <div class="mmj__row-actions">
            <router-link v-if="row.status === 'OPEN'" :to="`/meeting/${row.code}/room`" class="is-primary">进入</router-link>
            <button type="button" @click="toggleAdvice(row)">{{ adviceOpenId === row.id ? '收起建议' : '面试建议' }}</button>
          </div>
          <div v-if="adviceOpenId === row.id" class="mmj__advice">
            <AdviceList :list="adviceList" :loading="adviceLoading" :error="adviceError" empty-text="这次会议还没有留下建议。" />
          </div>
        </article>
      </div>
    </section>

    <!-- 我参与过的会议 -->
    <section class="mobile-section">
      <header class="mobile-section-heading"><h2>我参与过的会议</h2></header>
      <MobileSkeleton v-if="historyLoading" variant="card" :rows="2" label="正在加载参会记录" />
      <MobileState v-else-if="historyError" kind="error" title="参会记录暂时无法加载" :description="historyError" action="重新加载" @action="loadHistory" />
      <div v-else-if="joinedList.length" class="mmj__list">
        <article v-for="row in joinedList" :key="'j-' + row.id" class="mobile-info-card mmj__row">
          <div class="mobile-row">
            <div class="mobile-grow">
              <h3>{{ row.title }}</h3>
              <p>{{ row.codeDisplay || row.code }} · {{ formatTime(row.joinTime || row.createdAt) }} · 发起人：{{ row.hostName || '—' }}</p>
            </div>
            <span class="mmj__pill" :class="row.status === 'OPEN' ? 'is-open' : 'is-ended'">{{ row.status === 'OPEN' ? '进行中' : '已结束' }}</span>
          </div>
          <div class="mmj__row-actions">
            <router-link v-if="row.status === 'OPEN'" :to="`/meeting/${row.code}/room`" class="is-primary">再次进入</router-link>
            <button type="button" @click="toggleAdvice(row)">{{ adviceOpenId === row.id ? '收起建议' : '面试建议' }}</button>
          </div>
          <div v-if="adviceOpenId === row.id" class="mmj__advice">
            <AdviceList :list="adviceList" :loading="adviceLoading" :error="adviceError" empty-text="这次会议还没有留下建议。" />
          </div>
        </article>
      </div>
      <MobileState
        v-else
        title="还没有参加过的会议"
        :description="canHost ? '还没有参与过他人的会议。收到会议号后从上面输入进入。' : '收到企业或教师发来的会议号后，在上面输入即可进入视频面试。'"
      />
    </section>
  </MobileShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdviceList from '../../components/meeting/AdviceList.vue'
import { getMeetingAdvice, getMyMeetings, previewMeeting } from '../../api/meeting'
import { formatCodeInput, isValidCode, normalizeCode } from '../../utils/meetingProtocol'
import { formatTime } from '../../utils/time'
import { roleHome } from '../../router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { ENTERPRISE_TABS, TEACHER_TABS } from '../nav'

const ROLE = (localStorage.getItem('role') || '').toUpperCase()
const canHost = ROLE === 'ENTERPRISE' || ROLE === 'TEACHER' || ROLE === 'ADMIN'

// 一个页面服务三种角色的手机壳：企业走企业底栏，教师走教师底栏，学生用默认学生五项
const shell = computed(() => {
  if (ROLE === 'ENTERPRISE') {
    return { homePath: '/enterprise/dashboard', profilePath: '/enterprise/account', messages: false, navLinks: [], tabItems: ENTERPRISE_TABS }
  }
  if (canHost) {
    return { homePath: '/teacher/dashboard', messagePath: '/teacher/messages', profilePath: '/teacher/account', navLinks: [], tabItems: TEACHER_TABS }
  }
  return { homePath: roleHome(ROLE), navLinks: [] }
})

const pageTitle = computed(() => (ROLE === 'ENTERPRISE' ? '加入会议' : '视频会议'))
const pageSub = computed(() => {
  if (ROLE === 'ENTERPRISE') return '把会议号发给候选人，或凭收到的会议号加入他人的会议。'
  if (canHost) return '收到企业发来的会议号后，在这里输入即可进入视频面试。'
  return '收到企业或教师发来的会议号后，在这里输入即可进入视频面试。'
})

/* ---------- 会议号查找 ---------- */
const codeInput = ref('')
const normalizedInput = computed(() => normalizeCode(codeInput.value))
const looking = ref(false)
const lookupError = ref('')
const found = ref(null)

const foundStatusText = computed(() => {
  if (!found.value) return ''
  if (found.value.joinable) return '可进入'
  return found.value.status === 'OPEN' ? '房间已满' : '已结束'
})

function onCodeInput(event) {
  // 边打字边格式化（四位一组）；输入变化即清掉上一次的查找结果
  codeInput.value = formatCodeInput(event.target.value)
  found.value = null
  lookupError.value = ''
}

async function handleLookup() {
  const code = normalizedInput.value
  if (!isValidCode(code)) {
    lookupError.value = '会议号应为 8 位字母数字（如 3F7K-2Q9A），请核对后重试。'
    return
  }
  looking.value = true
  lookupError.value = ''
  found.value = null
  try {
    found.value = await previewMeeting(code)
  } catch (e) {
    lookupError.value = e.message || '没有找到这个会议号，请核对后重试。'
  } finally {
    looking.value = false
  }
}

/* ---------- 我的会议 ---------- */
const historyLoading = ref(true)
const historyError = ref('')
const hostedList = ref([])
const joinedList = ref([])

async function loadHistory() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const data = await getMyMeetings()
    hostedList.value = data?.hosted || []
    joinedList.value = data?.joined || []
  } catch (e) {
    historyError.value = e.message || '加载参会记录失败'
  } finally {
    historyLoading.value = false
  }
}

/* ---------- 建议回看（只读，与桌面端加入页一致） ---------- */
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

onMounted(loadHistory)
</script>

<style scoped>
.mmj__lookup > label { display: block; margin-bottom: 8px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 700; }
.mmj__input-row { display: flex; gap: 8px; }
.mmj__input-row input {
  flex: 1;
  min-width: 0;
  min-height: 48px;
  padding: 0 12px;
  color: var(--m-text);
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: .08em;
  text-transform: uppercase;
}
.mmj__input-row button { min-height: 48px; padding: 0 18px; color: #fff; background: var(--m-primary); border: 0; border-radius: var(--m-radius-button); font-size: 14px; font-weight: 700; white-space: nowrap; }
.mmj__input-row button:disabled { opacity: .5; }
.mmj__error { margin-top: 10px; color: #b42318; font-size: 12.5px; line-height: 1.6; }

.mmj__found { margin-top: 14px; padding: 14px; background: var(--m-primary-soft); border-radius: 12px; }
.mmj__found h3 { font-size: 15.5px; overflow-wrap: anywhere; }
.mmj__found p { margin-top: 6px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.6; }
.mmj__found .is-ok { color: #1d6b4f; }
.mmj__found .is-bad { color: #b42318; }
.mmj__enter { display: flex; min-height: 46px; margin-top: 12px; align-items: center; justify-content: center; color: #fff; background: var(--m-primary); border-radius: var(--m-radius-button); font-size: 14.5px; font-weight: 700; }
.mmj__enter-note { margin-top: 8px; color: #8a6100; font-size: 12px; line-height: 1.6; }

.mmj__list { display: flex; flex-direction: column; gap: 12px; }
.mmj__row h3 { font-size: 15px; overflow-wrap: anywhere; }
.mmj__pill { flex: 0 0 auto; padding: 4px 10px; border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mmj__pill.is-open { color: #1d6b4f; background: var(--surface-soft); }
.mmj__pill.is-ended { color: var(--m-text-tertiary); background: var(--surface-soft); }
.mmj__row-actions { display: flex; margin-top: 12px; padding-top: 10px; gap: 8px; flex-wrap: wrap; border-top: 1px dashed var(--m-border); }
.mmj__row-actions > * { display: inline-flex; min-height: 40px; padding: 0 14px; align-items: center; color: var(--m-text-secondary); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-button); font-size: 13px; font-weight: 700; }
.mmj__row-actions > .is-primary { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mmj__advice { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--m-border); }
</style>
