<template>
  <MobileShell v-bind="shell" :title="title" :subtitle="subtitle">
    <template v-if="loading">
      <MobileSkeleton variant="card" :rows="3" label="正在加载班级" />
    </template>
    <MobileState v-else-if="error" kind="error" title="班级数据暂时无法加载" description="请检查网络后重试。" action="重新加载" @action="load" />
    <MobileState
      v-else-if="classId && !currentClass"
      title="未找到该班级"
      description="班级不存在，或你没有访问权限。"
      action="返回班级列表"
      @action="$router.push('/teacher/classes')"
    />

    <!-- ============ 列表模式：/teacher/classes 与 /teacher/classes/manage ============ -->
    <template v-else-if="!classId">
      <p v-if="success" class="mtc__notice" role="status">{{ success }}</p>
      <p v-if="actionError" class="mtc__notice is-error" role="alert">{{ actionError }}</p>

      <section class="mobile-info-card mtc__create">
        <button v-if="!formOpen" type="button" class="mtc__create-toggle" @click="formOpen = true">＋ 创建班级</button>
        <form v-else @submit.prevent="createClass">
          <h2>创建教学班</h2>
          <label>班级名称<input v-model.trim="form.name" required maxlength="80" placeholder="例如：软件2501" :disabled="working" /></label>
          <label>学期<input v-model.trim="form.semester" required maxlength="80" placeholder="例如：2026–2027 第一学期" :disabled="working" /></label>
          <div class="mtc__form-actions">
            <button type="button" :disabled="working" @click="formOpen = false">取消</button>
            <button type="submit" class="is-primary" :disabled="working">{{ working ? '正在创建…' : '创建班级' }}</button>
          </div>
        </form>
      </section>

      <label v-if="classes.length > 3" class="mobile-search mtc__search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <input v-model.trim="keyword" type="search" placeholder="搜索班级名称或学期" />
      </label>

      <div v-if="filteredClasses.length" class="mtc__list">
        <router-link v-for="c in filteredClasses" :key="c.id" :to="`/teacher/classes/${c.id}`" class="mobile-info-card mtc__class">
          <div class="mobile-row">
            <span class="mobile-square-icon">{{ (c.name || '班').slice(0, 1) }}</span>
            <div class="mobile-grow">
              <h3>{{ c.name }}</h3>
              <p>{{ c.semester || '未设置学期' }}</p>
            </div>
            <span v-if="c.pendingCount" class="mtc__badge">待审 {{ c.pendingCount }}</span>
            <span v-else class="mtc__tag" :class="{ 'is-archived': c.archived }">{{ c.archived ? '已归档' : '使用中' }}</span>
          </div>
          <p class="mtc__class-meta">已加入 {{ c.joinedCount || 0 }} 人 · 待审核 {{ c.pendingCount || 0 }} 人</p>
        </router-link>
      </div>
      <MobileState
        v-else-if="!classes.length"
        title="从第一个教学班开始"
        description="创建班级、分享邀请码并审核学生申请，就能发布训练任务并跟进报告。"
        action="创建教学班"
        @action="formOpen = true"
      />
      <MobileState v-else title="没有匹配的班级" description="换个关键词试试。" action="清除搜索" @action="keyword = ''" />
    </template>

    <!-- ============ 详情模式：/teacher/classes/:classId（含 /members） ============ -->
    <template v-else>
      <router-link to="/teacher/classes" class="mtc__back">← 班级列表</router-link>
      <p v-if="success" class="mtc__notice" role="status">{{ success }}</p>
      <p v-if="actionError" class="mtc__notice is-error" role="alert">{{ actionError }}</p>

      <section class="mtc__hero">
        <div class="mobile-row">
          <div class="mobile-grow">
            <h2>{{ currentClass.name }}</h2>
            <p>{{ currentClass.semester || '未设置学期' }}</p>
          </div>
          <span class="mtc__tag" :class="{ 'is-archived': currentClass.archived }">{{ currentClass.archived ? '已归档' : '使用中' }}</span>
        </div>
        <div v-if="!currentClass.archived" class="mtc__invite">
          <span>邀请码</span>
          <code>{{ currentClass.inviteCode || '—' }}</code>
          <button type="button" @click="copyInvite">{{ copied ? '已复制' : '复制' }}</button>
        </div>
        <p v-else class="mtc__archived-note">班级已归档，历史训练与报告仍可查看。</p>
      </section>

      <section class="mobile-menu-group" aria-label="班级操作">
        <button type="button" class="mtc__action" @click="editOpen = !editOpen"><span>✎</span><strong>编辑班级资料</strong><b>›</b></button>
        <form v-if="editOpen" class="mtc__edit" @submit.prevent="saveEdit">
          <label>班级名称<input v-model.trim="editForm.name" required maxlength="80" :disabled="working" /></label>
          <label>学期<input v-model.trim="editForm.semester" required maxlength="80" :disabled="working" /></label>
          <div class="mtc__form-actions">
            <button type="button" :disabled="working" @click="editOpen = false">取消</button>
            <button type="submit" class="is-primary" :disabled="working">保存</button>
          </div>
        </form>
        <router-link :to="`/teacher/tasks?classId=${classId}`"><span>▤</span><div class="mobile-grow"><strong>班级任务</strong><small>查看该班的训练任务</small></div><b>›</b></router-link>
        <router-link v-if="!currentClass.archived" :to="`/teacher/tasks/create?classId=${classId}`"><span>＋</span><div class="mobile-grow"><strong>发布训练任务</strong><small>任务创建需在电脑端完成</small></div><b>›</b></router-link>
        <button v-if="!currentClass.archived" type="button" class="mtc__action" @click="resetInvite"><span>↻</span><strong>重置邀请码</strong><b>›</b></button>
        <button v-if="!currentClass.archived" type="button" class="mtc__action is-danger" @click="archiveClass"><span>▣</span><strong>归档班级</strong><b>›</b></button>
      </section>

      <section class="mobile-section">
        <header class="mobile-section-heading"><h2>成员申请与名单</h2><span>{{ members.length }} 条</span></header>
        <div class="mobile-segments" role="tablist">
          <button :class="{ active: memberTab === 'joined' }" @click="memberTab = 'joined'">已加入 {{ joinedMembers.length }}</button>
          <button :class="{ active: memberTab === 'pending' }" @click="memberTab = 'pending'">待审核 {{ pendingMembers.length }}</button>
          <button :class="{ active: memberTab === 'all' }" @click="memberTab = 'all'">全部</button>
        </div>
        <div v-if="visibleMembers.length" class="mtc__members">
          <div v-for="m in visibleMembers" :key="m.id" class="mobile-info-card mtc__member">
            <div class="mobile-row">
              <span class="mobile-square-icon">{{ (m.name || '?').slice(0, 1) }}</span>
              <div class="mobile-grow">
                <h3>{{ m.name }}</h3>
                <p>{{ m.username }} · {{ memberStateLabel(m.state) }}</p>
              </div>
            </div>
            <div v-if="!currentClass.archived && (m.state === 'PENDING' || m.state === 'JOINED')" class="mtc__member-actions">
              <template v-if="m.state === 'PENDING'">
                <button type="button" class="is-primary" :disabled="working" @click="setMembership(m, 'JOINED')">通过申请</button>
                <button type="button" :disabled="working" @click="setMembership(m, 'REJECTED')">拒绝</button>
              </template>
              <button v-else type="button" :disabled="working" @click="setMembership(m, 'REMOVED')">移出班级</button>
            </div>
          </div>
        </div>
        <MobileState
          v-else
          :title="members.length ? '该分组没有成员' : '等待学生加入'"
          :description="members.length ? '切换到其他分组看看。' : '把邀请码分享给学生，提交申请后可在这里审核。'"
          :action="!members.length && currentClass.inviteCode ? '复制邀请码' : ''"
          @action="copyInvite"
        />
      </section>
    </template>
  </MobileShell>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teaching } from '../../services/teachingApi'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import MobileSkeleton from '../components/MobileSkeleton.vue'
import { TEACHER_TABS } from '../nav'
import { memberStateLabel } from '../teacherFormat'

const route = useRoute()
const router = useRouter()

const shell = computed(() => ({
  homePath: '/teacher/dashboard',
  messagePath: '/teacher/messages',
  profilePath: '/teacher/account',
  navLinks: [],
  tabItems: TEACHER_TABS,
}))

const classId = computed(() => Number(route.params.classId) || 0)
const classes = ref([])
const members = ref([])
const loading = ref(true)
const error = ref(false)
const working = ref(false)
const success = ref('')
const actionError = ref('')

const formOpen = ref(false)
const form = ref({ name: '', semester: '' })
const editOpen = ref(false)
const editForm = ref({ name: '', semester: '' })
const keyword = ref('')
const copied = ref(false)
const memberTab = ref('joined')

const currentClass = computed(() => classes.value.find((c) => Number(c.id) === classId.value))
const title = computed(() => (classId.value ? (currentClass.value?.name || '班级详情') : '班级'))
const subtitle = computed(() => (classId.value
  ? (currentClass.value?.archived ? '已归档 · 历史训练与报告保留' : '成员审核 · 邀请码 · 班级任务')
  : '管理教学班与学生审核'))

const filteredClasses = computed(() => {
  const q = keyword.value.toLowerCase()
  if (!q) return classes.value
  return classes.value.filter((c) => `${c.name} ${c.semester || ''}`.toLowerCase().includes(q))
})

const joinedMembers = computed(() => members.value.filter((m) => m.state === 'JOINED'))
const pendingMembers = computed(() => members.value.filter((m) => m.state === 'PENDING'))
const visibleMembers = computed(() => {
  if (memberTab.value === 'joined') return joinedMembers.value
  if (memberTab.value === 'pending') return pendingMembers.value
  return members.value
})

async function load() {
  loading.value = true
  error.value = false
  try {
    classes.value = (await teaching.classes()) || []
    if (classId.value && currentClass.value) {
      members.value = (await teaching.members(classId.value)) || []
      // 待审核有人时默认停在待审核分组：老师进来最常干的就是审批
      memberTab.value = pendingMembers.value.length ? 'pending' : 'joined'
      editForm.value = { name: currentClass.value.name, semester: currentClass.value.semester }
    } else {
      members.value = []
    }
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function act(action, message) {
  if (working.value) return
  working.value = true
  success.value = ''
  actionError.value = ''
  try {
    await action()
    success.value = message
    await load()
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message || '操作失败，请重试'
  } finally {
    working.value = false
  }
}

async function createClass() {
  await act(async () => {
    const id = await teaching.createClass({ name: form.value.name, semester: form.value.semester })
    formOpen.value = false
    form.value = { name: '', semester: '' }
    // 建完直接进详情页拿邀请码，和桌面端行为一致
    router.push(`/teacher/classes/${id}`)
  }, '班级已创建，请把邀请码发给学生。')
}

async function saveEdit() {
  await act(() => teaching.editClass(classId.value, { ...editForm.value }), '班级资料已更新。')
  editOpen.value = false
}

function resetInvite() {
  if (!window.confirm('重置后原邀请码将失效，已提交的申请不受影响。')) return
  act(() => teaching.invite(classId.value), '邀请码已重置。')
}

function archiveClass() {
  if (!window.confirm('归档将结束该班级已发布任务，历史报告保留。')) return
  act(() => teaching.archive(classId.value), '班级已归档。')
}

function setMembership(member, state) {
  if (state === 'REMOVED' && !window.confirm(`移出「${member.name}」？移出后不能开始新的班级任务，历史保留。`)) return
  const messages = { JOINED: '学生已加入班级。', REJECTED: '申请已拒绝。', REMOVED: '成员已移出。' }
  act(() => teaching.membership(classId.value, member.id, state), messages[state])
}

async function copyInvite() {
  const code = currentClass.value?.inviteCode
  if (!code) return
  try {
    await navigator.clipboard.writeText(code)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch {
    // 非安全上下文里 clipboard 不可用：退化成手动选中（与桌面端同款兜底）
    window.prompt('复制班级邀请码：', code)
  }
}

watch(() => [route.path, route.params.classId].join('|'), () => {
  success.value = ''
  actionError.value = ''
  formOpen.value = false
  editOpen.value = false
  load()
}, { immediate: true })
</script>

<style scoped>
.mtc__notice { margin-bottom: 12px; padding: 11px 14px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 10px; font-size: 13px; line-height: 1.6; }
.mtc__notice.is-error { color: #7c4315; background: var(--m-accent-soft); }

.mtc__create-toggle { width: 100%; min-height: 48px; color: var(--m-primary-dark); background: var(--m-primary-soft); border: 0; border-radius: var(--m-radius-card); font-size: 15px; font-weight: 700; }
.mtc__create h2 { margin-bottom: 14px; font-size: 16px; }
.mtc__create label,
.mtc__edit label { display: grid; margin-top: 10px; gap: 6px; color: var(--m-text-secondary); font-size: 12.5px; font-weight: 600; }
.mtc__create input,
.mtc__edit input { min-height: 44px; padding: 0 12px; color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-input); font: inherit; font-size: 14px; }
.mtc__form-actions { display: flex; margin-top: 14px; gap: 10px; }
.mtc__form-actions button { flex: 1; min-height: 44px; color: var(--m-text-secondary); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-button); font-size: 14px; font-weight: 700; }
.mtc__form-actions button.is-primary { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mtc__form-actions button:disabled { opacity: .55; }

.mtc__search { margin: 14px 0 2px; }

.mtc__list { display: flex; margin-top: 14px; flex-direction: column; gap: 12px; }
.mtc__class-meta { margin-top: 12px; padding-top: 10px; color: var(--m-text-tertiary); border-top: 1px dashed var(--m-border); font-size: 12px; }
.mtc__badge { padding: 4px 10px; color: #b66b20; background: var(--m-accent-soft); border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mtc__tag { padding: 4px 10px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.mtc__tag.is-archived { color: var(--m-text-tertiary); background: var(--surface-soft); }

.mtc__back { display: inline-flex; min-height: 40px; align-items: center; margin: -6px 0 10px; color: var(--m-primary); font-size: 13.5px; font-weight: 700; }

.mtc__hero { padding: 18px; background: linear-gradient(160deg, #eef7f0 0%, #ffffff 62%); border: 1px solid #dcebe1; border-radius: var(--m-radius-hero); }
.mtc__hero h2 { font-size: 20px; letter-spacing: -.02em; }
.mtc__hero .mobile-row > p { margin-top: 4px; color: var(--m-text-secondary); font-size: 13px; }
.mtc__invite { display: flex; margin-top: 16px; padding-top: 14px; align-items: center; gap: 10px; border-top: 1px dashed #cfe0d6; }
.mtc__invite span { color: var(--m-text-secondary); font-size: 12.5px; }
.mtc__invite code { flex: 1; min-width: 0; padding: 8px 10px; overflow-wrap: anywhere; color: var(--m-primary-dark); background: color-mix(in srgb, var(--surface-elevated) 75%, transparent); border: 1px solid #dcebe1; border-radius: 8px; font-size: 15px; font-weight: 800; letter-spacing: .08em; }
.mtc__invite button { min-height: 40px; padding: 0 16px; color: #fff; background: var(--m-primary); border: 0; border-radius: var(--m-radius-button); font-size: 13px; font-weight: 700; }
.mtc__archived-note { margin-top: 12px; color: var(--m-text-secondary); font-size: 13px; line-height: 1.6; }

.mtc__action,
.mobile-menu-group a { display: flex; min-height: 60px; padding: 10px 16px; align-items: center; gap: 12px; color: var(--m-text); background: transparent; border: 0; border-bottom: 1px solid var(--m-border); font: inherit; text-align: left; }
.mtc__action:last-of-type { border-bottom: 0; }
.mtc__action > span,
.mobile-menu-group a > span { display: grid; flex: 0 0 auto; width: 32px; height: 32px; color: var(--m-primary); place-items: center; background: var(--m-primary-soft); border-radius: 9px; font-size: 14px; font-weight: 700; }
.mtc__action strong { flex: 1; font-size: 14px; }
.mtc__action b { color: var(--m-text-tertiary); font-size: 20px; }
.mtc__action.is-danger { color: #b42318; }
.mtc__action.is-danger > span { color: #b42318; background: var(--surface-soft); }

.mtc__edit { padding: 4px 16px 16px; border-bottom: 1px solid var(--m-border); }

.mtc__members { display: flex; margin-top: 14px; flex-direction: column; gap: 10px; }
.mtc__member-actions { display: flex; margin-top: 12px; padding-top: 10px; gap: 8px; border-top: 1px dashed var(--m-border); }
.mtc__member-actions button { flex: 1; min-height: 42px; color: var(--m-text-secondary); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-button); font-size: 13.5px; font-weight: 700; }
.mtc__member-actions button.is-primary { color: #fff; background: var(--m-primary); border-color: var(--m-primary); }
.mtc__member-actions button:disabled { opacity: .55; }

.mobile-segments { margin-bottom: 4px; }
</style>
