<template>
  <div class="resume-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><EditPen /></el-icon>
        第二步
      </span>
      <h2>填写简历 / 项目经历</h2>
      <p>粘贴简历或描述项目经历，系统会提取技能关键词与项目片段，形成后续个性化追问所需的个人画像。</p>
    </div>

    <div class="context-strip">
      <div>
        <span>岗位 ID</span>
        <strong>{{ jobId || '未指定' }}</strong>
      </div>
      <div>
        <span>面试难度</span>
        <strong>{{ difficultyLabel }}</strong>
      </div>
      <div>
        <span>画像状态</span>
        <strong>{{ analyzed ? '已生成' : '待提取' }}</strong>
      </div>
    </div>

    <el-row :gutter="20" class="resume-grid">
      <el-col :xs="24" :lg="13">
        <el-card class="editor-card">
          <template #header>
            <div class="card-header">
              <div>
                <span class="card-kicker">Resume Input</span>
                <h3>简历内容</h3>
              </div>
              <el-button link type="primary" :icon="Notebook" @click="fillSample">填入示例</el-button>
            </div>
          </template>

          <el-input
            v-model="rawText"
            type="textarea"
            :rows="18"
            :placeholder="resumePlaceholder"
            maxlength="5000"
            show-word-limit
          />

          <div class="btns">
            <el-button type="primary" :icon="MagicStick" :loading="loading" @click="handleAnalyze">
              提取并保存
            </el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="11">
        <el-card class="profile-card">
          <template #header>
            <div class="card-header">
              <div>
                <span class="card-kicker">Profile</span>
                <h3>个人画像</h3>
              </div>
              <el-tag v-if="analyzed" type="success" size="small" effect="plain">已生成</el-tag>
            </div>
          </template>

          <div v-if="!analyzed" class="empty-state">
            <div class="empty-icon">
              <el-icon><DocumentChecked /></el-icon>
            </div>
            <h4>等待提取简历信息</h4>
            <p>保存后，这里会展示识别出的技能关键词和项目经历片段。</p>
          </div>

          <template v-else>
            <section class="section">
              <div class="section-title">
                <span class="section-icon">
                  <el-icon><Star /></el-icon>
                </span>
                <div>
                  <h4>技能关键词</h4>
                  <p>{{ skills.length }} 个关键词</p>
                </div>
              </div>

              <div v-if="skills.length" class="tags">
                <el-tag v-for="s in skills" :key="s" class="skill-tag" effect="light">{{ s }}</el-tag>
              </div>
              <el-text v-else type="info" size="small">未识别到技能关键词，可以补充技术栈描述。</el-text>
            </section>

            <section class="section">
              <div class="section-title">
                <span class="section-icon">
                  <el-icon><Files /></el-icon>
                </span>
                <div>
                  <h4>项目经历</h4>
                  <p>{{ projects.length }} 段经历</p>
                </div>
              </div>

              <div v-if="projects.length" class="project-list">
                <article v-for="(p, i) in projects" :key="i" class="project-item">
                  <span class="project-index">{{ i + 1 }}</span>
                  <p>{{ p }}</p>
                </article>
              </div>
              <el-text v-else type="info" size="small">未识别到项目段落，可用“项目经历：”开头分段。</el-text>
            </section>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <div class="action-bar">
      <div class="action-content">
        <el-button :icon="ArrowLeft" @click="$router.back()">返回岗位选择</el-button>
        <el-button
          type="primary"
          size="large"
          :disabled="!analyzed || entering"
          :loading="entering"
          :icon="VideoCamera"
          @click="goInterview"
        >
          进入模拟面试
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  DocumentChecked,
  EditPen,
  Files,
  MagicStick,
  Notebook,
  Star,
  VideoCamera
} from '@element-plus/icons-vue'
import { saveResume, getMyResume } from '@/api'

const route = useRoute()
const router = useRouter()

const rawText = ref('')
const skills = ref([])
const projects = ref([])
const analyzed = ref(false)
const loading = ref(false)
const entering = ref(false)

const jobId = route.query.jobId
const difficulty = route.query.difficulty

const resumePlaceholder =
  '例如：\n熟悉 Java 核心、JVM、并发编程，掌握 Spring Boot、MyBatis、Redis。\n\n项目经历：基于 Spring Boot + Redis 的高并发秒杀系统，使用消息队列削峰，解决高并发下的超卖问题。'

const difficultyLabel = computed(() => {
  // difficulty 可能是中文字符串（来自 JobSelect）或数字
  if (['简单', '中等', '困难'].includes(difficulty)) return difficulty
  return { 1: '简单', 2: '中等', 3: '困难' }[Number(difficulty)] || '未指定'
})

const parseList = (str) => {
  try {
    return JSON.parse(str || '[]')
  } catch {
    return []
  }
}

const applyResume = (resume) => {
  if (!resume) return
  rawText.value = resume.rawText || ''
  skills.value = parseList(resume.skills)
  projects.value = parseList(resume.projects)
  analyzed.value = skills.value.length > 0 || projects.value.length > 0 || !!resume.rawText
}

const handleAnalyze = async () => {
  if (!rawText.value.trim()) {
    ElMessage.warning('请先填写简历内容')
    return
  }
  loading.value = true
  try {
    const resume = await saveResume({ rawText: rawText.value })
    applyResume(resume)
    ElMessage.success('已提取并保存')
  } finally {
    loading.value = false
  }
}

const fillSample = () => {
  rawText.value =
    '熟悉 Java 核心、JVM 内存模型与垃圾回收、并发编程（多线程、锁、线程池），掌握 Spring Boot、Spring Cloud、MyBatis，熟练使用 MySQL、Redis，了解 Kafka 消息队列与分布式微服务。\n\n' +
    '项目经历：基于 Spring Boot + Redis 的高并发秒杀系统。使用 Redis 预减库存、Kafka 削峰，通过分布式锁解决超卖问题，QPS 从 200 提升到 3000。\n\n' +
    '项目经历：校园二手交易平台。Vue3 + Element Plus 前端，Spring Boot 后端，实现商品发布、即时聊天与订单管理。'
}

const difficultyMap = { 简单: 1, 中等: 2, 困难: 3 }

const goInterview = () => {
  if (entering.value) return // 防重复点击：避免连点进入两次而创建多个会话
  if (!jobId) {
    ElMessage.warning('请先从「面试准备」选择岗位')
    return
  }
  entering.value = true
  // JobSelect 传来的 difficulty 是中文字符串，这里统一归一为 1/2/3
  const d = difficultyMap[difficulty] || Number(difficulty) || 2
  router.push({ path: '/interview', query: { jobId, difficulty: d, resumeReady: 1 } })
}

onMounted(async () => {
  const resume = await getMyResume()
  applyResume(resume)
})
</script>

<style scoped>
.context-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.context-strip div {
  padding: 16px 18px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(220, 230, 242, 0.88);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.context-strip span,
.context-strip strong {
  display: block;
}

.context-strip span {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.context-strip strong {
  margin-top: 6px;
  color: var(--text);
  font-size: 20px;
}

.resume-grid {
  align-items: stretch;
}

.editor-card,
.profile-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.card-kicker {
  color: var(--accent);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.card-header h3 {
  margin-top: 4px;
  color: var(--text);
  font-size: 18px;
}

.editor-card :deep(.el-textarea__inner) {
  min-height: 420px !important;
  padding: 16px;
  color: var(--text);
  line-height: 1.8;
  resize: vertical;
}

.btns {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.profile-card {
  min-height: 560px;
}

.empty-state {
  display: flex;
  min-height: 390px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: var(--text-muted);
  text-align: center;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin-bottom: 18px;
  color: var(--primary);
  background: rgba(37, 99, 235, 0.09);
  border-radius: 50%;
  font-size: 32px;
}

.empty-state h4 {
  color: var(--text);
  font-size: 18px;
}

.empty-state p {
  max-width: 300px;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.8;
}

.section {
  padding: 4px 0 24px;
}

.section + .section {
  padding-top: 22px;
  border-top: 1px solid rgba(220, 230, 242, 0.85);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.section-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  border-radius: var(--radius);
}

.section-title h4 {
  color: var(--text);
  font-size: 16px;
}

.section-title p {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  margin: 0;
  --el-tag-bg-color: rgba(37, 99, 235, 0.08);
  --el-tag-border-color: rgba(37, 99, 235, 0.14);
  --el-tag-text-color: var(--primary-dark);
}

.project-list {
  display: grid;
  gap: 10px;
}

.project-item {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 10px;
  padding: 12px;
  background: rgba(248, 251, 255, 0.95);
  border: 1px solid rgba(220, 230, 242, 0.9);
  border-radius: var(--radius);
}

.project-index {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--primary-dark);
  background: rgba(37, 99, 235, 0.09);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 800;
}

.project-item p {
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.8;
}

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

@media (max-width: 768px) {
  .context-strip {
    grid-template-columns: 1fr;
  }

  .profile-card {
    margin-top: 18px;
    min-height: auto;
  }

  .action-content {
    width: 100%;
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .action-content .el-button {
    width: 100%;
  }
}
</style>
