<template>
  <div class="interview-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><VideoCamera /></el-icon>
        核心功能
      </span>
      <h2>模拟面试</h2>
      <p>AI 面试官将根据你的回答进行规则化追问，请像真实面试一样作答。</p>
    </div>

    <!-- 启动失败兜底 -->
    <div v-if="stage === 'error'" class="placeholder-card glass-panel">
      <el-icon :size="46"><WarningFilled /></el-icon>
      <p>面试未能开始，请先从「面试准备」选择岗位并完善简历。</p>
      <el-button type="primary" @click="$router.push('/jobs')">前往选择岗位</el-button>
    </div>

    <template v-else>
      <!-- 状态条 -->
      <div class="status-strip">
        <div>
          <span>目标岗位</span>
          <strong>{{ jobName || '加载中…' }}</strong>
        </div>
        <div>
          <span>面试状态</span>
          <strong>
            <i class="dot" :class="statusClass"></i>{{ statusLabel }}
          </strong>
        </div>
        <div>
          <span>已用时长</span>
          <strong>{{ timeText }}</strong>
        </div>
        <div>
          <span>当前轮次</span>
          <strong>第 {{ roundNo }} 轮</strong>
        </div>
      </div>

      <div class="room-grid">
        <!-- 面试官卡片 -->
        <aside class="interviewer-card glass-panel">
          <div class="avatar">
            <el-icon :size="38"><Avatar /></el-icon>
          </div>
          <h3>AI 面试官</h3>
          <p class="role-tag">{{ jobName || '智面幻境' }}</p>
          <ul class="tips">
            <li><el-icon><ChatLineRound /></el-icon>回答尽量结合具体项目</li>
            <li><el-icon><Aim /></el-icon>覆盖关键技术点更易得分</li>
            <li><el-icon><MagicStick /></el-icon>回答含糊会触发追问</li>
          </ul>
        </aside>

        <!-- 对话区 -->
        <main class="dialog-card">
          <div v-loading="stage === 'loading'" class="dialog-body">
            <!-- 主问题 -->
            <div v-if="currentQuestion" class="bubble interviewer">
              <span class="bubble-role">面试官 · 第 {{ currentQuestion.roundNo }} 轮</span>
              <p class="bubble-text">{{ currentQuestion.content }}</p>
              <el-tag v-if="currentQuestion.abilityTag" size="small" effect="plain" class="ability">
                {{ currentQuestion.abilityTag }}
              </el-tag>
            </div>

            <!-- 追问 -->
            <div v-if="followupText" class="bubble followup">
              <span class="bubble-role"><el-icon><Connection /></el-icon>追问</span>
              <p class="bubble-text">{{ followupText }}</p>
            </div>

            <!-- 结束提示 -->
            <div v-if="stage === 'finishable'" class="finish-hint">
              <el-icon><CircleCheckFilled /></el-icon>
              本轮题目已全部作答，可以结束面试查看记录。
            </div>
          </div>

          <!-- 作答区 -->
          <div class="answer-area">
            <label>你的回答</label>
            <el-input
              v-model="answer"
              type="textarea"
              :rows="6"
              :disabled="!canAnswer"
              maxlength="1000"
              show-word-limit
              :placeholder="answerPlaceholder"
              @keydown.ctrl.enter="handleSubmit"
            />
            <p class="answer-tip"><el-icon><InfoFilled /></el-icon>Ctrl + Enter 快速提交</p>
          </div>
        </main>
      </div>

      <!-- 底部操作栏 -->
      <div class="action-bar">
        <div class="action-content">
          <el-button :icon="CircleClose" :loading="finishing" :disabled="stage === 'finished'" @click="handleFinish">
            结束面试
          </el-button>
          <div class="action-right">
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="submitting"
              :disabled="!canSubmit"
              @click="handleSubmit"
            >
              提交回答
            </el-button>
            <el-button
              type="success"
              :icon="ArrowRight"
              :loading="loadingNext"
              :disabled="stage !== 'canNext'"
              @click="handleNext"
            >
              下一题
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Aim,
  ArrowRight,
  Avatar,
  ChatLineRound,
  CircleCheckFilled,
  CircleClose,
  Connection,
  InfoFilled,
  MagicStick,
  Promotion,
  VideoCamera,
  WarningFilled
} from '@element-plus/icons-vue'
import { startInterview, submitAnswer, getNextQuestion, finishInterview } from '@/api'

const route = useRoute()
const router = useRouter()

// ------- 会话状态 -------
const sessionId = ref(null)
const jobName = ref('')
const currentQuestion = ref(null) // { id, content, abilityTag, roundNo }
const followupText = ref('')
const answer = ref('')
const roundNo = ref(1)

// stage: loading / answering / followup / canNext / finishable / finished / error
const stage = ref('loading')

// 请求中标记（防重复点击，缓解快速连点 next 的并发）
const submitting = ref(false)
const loadingNext = ref(false)
const finishing = ref(false)

// ------- 计时器 -------
const elapsed = ref(0)
let timer = null
const startTimer = () => {
  stopTimer()
  timer = setInterval(() => {
    elapsed.value += 1
  }, 1000)
}
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
const timeText = computed(() => {
  const m = String(Math.floor(elapsed.value / 60)).padStart(2, '0')
  const s = String(elapsed.value % 60).padStart(2, '0')
  return `${m}:${s}`
})

// ------- 派生状态 -------
const canAnswer = computed(() => stage.value === 'answering' || stage.value === 'followup')
const canSubmit = computed(() => canAnswer.value && answer.value.trim().length > 0 && !submitting.value)

const statusLabel = computed(() => {
  if (stage.value === 'finished') return '已结束'
  if (stage.value === 'loading') return '准备中'
  return '进行中'
})
const statusClass = computed(() => {
  if (stage.value === 'finished') return 'done'
  if (stage.value === 'loading') return 'pending'
  return 'live'
})
const answerPlaceholder = computed(() => {
  if (stage.value === 'followup') return '请针对上面的追问继续作答…'
  if (stage.value === 'canNext') return '本题已作答，点击「下一题」继续。'
  if (stage.value === 'finishable') return '题目已全部作答，可结束面试。'
  return '请输入你的回答，建议结合项目经历并覆盖关键技术点…'
})

// ------- 流程动作 -------
const handleSubmit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const step = await submitAnswer(sessionId.value, {
      questionId: currentQuestion.value.id,
      answer: answer.value.trim()
    })
    answer.value = ''
    applyStep(step)
  } finally {
    submitting.value = false
  }
}

const applyStep = (step) => {
  const action = step?.nextAction
  if (action === 'FOLLOWUP') {
    followupText.value = step.followupQuestion || '能否再补充一些细节？'
    stage.value = 'followup'
    ElMessage.info('面试官追问了一个问题')
  } else if (action === 'NEXT') {
    followupText.value = ''
    stage.value = 'canNext'
    ElMessage.success('回答已记录，可进入下一题')
  } else {
    // FINISHABLE
    followupText.value = ''
    stage.value = 'finishable'
    ElMessage.success('回答已记录，本轮题目已完成')
  }
}

const handleNext = async () => {
  if (stage.value !== 'canNext') return
  loadingNext.value = true
  try {
    const step = await getNextQuestion(sessionId.value)
    if (step?.nextAction === 'NEXT' && step.question) {
      currentQuestion.value = step.question
      roundNo.value = step.question.roundNo
      followupText.value = ''
      stage.value = 'answering'
    } else {
      stage.value = 'finishable'
      ElMessage.success('题目已全部完成，可结束面试')
    }
  } finally {
    loadingNext.value = false
  }
}

const handleFinish = async () => {
  finishing.value = true
  try {
    await finishInterview(sessionId.value)
    stopTimer()
    stage.value = 'finished'
    ElMessage.success('面试已结束，正在前往面试记录')
    router.push('/history')
  } finally {
    finishing.value = false
  }
}

// ------- 初始化 -------
onMounted(async () => {
  const jobId = route.query.jobId
  const difficulty = route.query.difficulty
  if (!jobId) {
    ElMessage.warning('请先从「面试准备」选择岗位')
    router.replace('/jobs')
    return
  }
  try {
    const data = await startInterview({
      jobId: Number(jobId),
      difficulty: Number(difficulty) || 2
    })
    sessionId.value = data.sessionId
    jobName.value = data.jobName
    currentQuestion.value = data.question
    roundNo.value = data.question?.roundNo || 1
    stage.value = 'answering'
    startTimer()
  } catch {
    // 错误信息已由请求拦截器统一提示
    stage.value = 'error'
  }
})

onUnmounted(stopTimer)
</script>

<style scoped>
.interview-page {
  padding-top: 12px;
}

.placeholder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 64px 32px;
  margin-top: 20px;
  text-align: center;
  color: var(--text-muted);
  border-radius: var(--radius);
}

.placeholder-card .el-icon {
  color: var(--warning);
}

/* 状态条 */
.status-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.status-strip div {
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(220, 230, 242, 0.88);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.status-strip span {
  display: block;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.status-strip strong {
  display: flex;
  align-items: center;
  margin-top: 6px;
  color: var(--text);
  font-size: 19px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}

.dot.live {
  background: #16a76a;
  box-shadow: 0 0 0 4px rgba(22, 167, 106, 0.16);
}

.dot.pending {
  background: var(--warning);
}

.dot.done {
  background: var(--primary);
}

/* 房间布局 */
.room-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  margin-bottom: 88px;
}

/* 面试官卡片 */
.interviewer-card {
  padding: 26px 22px;
  text-align: center;
  border-radius: 14px;
}

.interviewer-card .avatar {
  display: grid;
  place-items: center;
  width: 76px;
  height: 76px;
  margin: 0 auto 14px;
  color: #fff;
  background: linear-gradient(135deg, #8c57ff, #5a34ec);
  border-radius: 50%;
  box-shadow: 0 12px 26px rgba(124, 88, 255, 0.32);
}

.interviewer-card h3 {
  color: var(--text);
  font-size: 18px;
}

.role-tag {
  margin-top: 4px;
  color: var(--primary);
  font-size: 13px;
  font-weight: 700;
}

.interviewer-card .tips {
  margin-top: 20px;
  text-align: left;
  list-style: none;
}

.interviewer-card .tips li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 0;
  color: var(--text-muted);
  font-size: 13px;
  border-top: 1px dashed rgba(220, 230, 242, 0.95);
}

.interviewer-card .tips li .el-icon {
  color: var(--primary);
}

/* 对话卡片 */
.dialog-card {
  display: flex;
  flex-direction: column;
  min-height: 460px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.dialog-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.bubble {
  padding: 16px 18px;
  border-radius: 12px;
}

.bubble-role {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 800;
}

.bubble-text {
  margin-top: 8px;
  font-size: 16px;
  line-height: 1.8;
}

.bubble.interviewer {
  background: rgba(37, 99, 235, 0.06);
  border: 1px solid rgba(37, 99, 235, 0.14);
}

.bubble.interviewer .bubble-role {
  color: var(--primary-dark);
}

.bubble.interviewer .bubble-text {
  color: var(--text);
}

.bubble.interviewer .ability {
  margin-top: 12px;
  --el-tag-bg-color: rgba(37, 99, 235, 0.08);
  --el-tag-border-color: rgba(37, 99, 235, 0.16);
  --el-tag-text-color: var(--primary-dark);
}

.bubble.followup {
  background: rgba(140, 87, 255, 0.07);
  border: 1px solid rgba(140, 87, 255, 0.2);
}

.bubble.followup .bubble-role {
  color: #6a35e0;
}

.bubble.followup .bubble-text {
  color: #3a2a63;
}

.finish-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  color: #0f9d63;
  background: rgba(22, 167, 106, 0.08);
  border: 1px solid rgba(22, 167, 106, 0.2);
  border-radius: 12px;
  font-weight: 700;
}

/* 作答区 */
.answer-area {
  padding: 18px 24px 22px;
  border-top: 1px solid var(--border);
  background: var(--surface-soft);
}

.answer-area label {
  display: block;
  margin-bottom: 10px;
  color: var(--text);
  font-size: 14px;
  font-weight: 800;
}

.answer-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

/* 底部操作栏（与 Resume.vue 一致） */
.action-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 15;
  padding: 14px 24px;
  background: rgba(255, 255, 255, 0.9);
  border-top: 1px solid rgba(220, 230, 242, 0.95);
  box-shadow: 0 -12px 28px rgba(37, 99, 235, 0.08);
  backdrop-filter: blur(14px);
}

.action-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: min(1180px, calc(100vw - 48px));
  margin: 0 auto;
}

.action-right {
  display: flex;
  gap: 12px;
}

@media (max-width: 880px) {
  .status-strip {
    grid-template-columns: repeat(2, 1fr);
  }

  .room-grid {
    grid-template-columns: 1fr;
  }

  .action-content {
    width: 100%;
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .action-right {
    flex-direction: column;
  }

  .action-right .el-button {
    width: 100%;
    margin-left: 0;
  }
}
</style>
