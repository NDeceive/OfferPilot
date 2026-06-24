<template>
  <div class="prep-page">
    <section class="prep-heading">
      <h1>面试准备</h1>
      <p>完成以下准备，系统将为你生成个性化面试方案</p>
    </section>

    <div class="prep-steps">
      <div
        v-for="(step, index) in steps"
        :key="step"
        class="step-item"
        :class="{ active: index === 0 }"
      >
        <span>{{ index + 1 }}</span>
        <strong>{{ step }}</strong>
      </div>
    </div>

    <div class="prep-grid">
      <main class="prep-main">
        <section class="form-panel">
          <h2>1. 候选人基本信息</h2>
          <div class="field-grid basic-grid">
            <label>
              <span>姓名</span>
              <el-input v-model="candidate.name" placeholder="请输入姓名" />
            </label>
            <label>
              <span>学校</span>
              <el-input v-model="candidate.school" placeholder="请输入学校" />
            </label>
            <label>
              <span>专业</span>
              <el-input v-model="candidate.major" placeholder="请输入专业" />
            </label>
            <label>
              <span>预计毕业时间</span>
              <el-input v-model="candidate.graduation" placeholder="请选择或输入毕业时间" :suffix-icon="Calendar" />
            </label>
          </div>
        </section>

        <section class="form-panel">
          <h2>2. 面试设置</h2>
          <div class="field-grid setting-grid">
            <label>
              <span>面试时长</span>
              <el-select v-model="interview.duration">
                <el-option label="20分钟" value="20分钟" />
                <el-option label="30分钟" value="30分钟" />
                <el-option label="45分钟" value="45分钟" />
              </el-select>
            </label>
            <label>
              <span>难度</span>
              <el-select v-model="interview.difficulty">
                <el-option label="简单" value="简单" />
                <el-option label="中等" value="中等" />
                <el-option label="困难" value="困难" />
              </el-select>
            </label>
            <label>
              <span>面试语言</span>
              <el-select v-model="interview.language">
                <el-option label="普通话" value="普通话" />
                <el-option label="英语" value="英语" />
              </el-select>
            </label>
          </div>
        </section>

        <section class="form-panel">
          <h2>3. 目标岗位选择 <small>（单选）</small></h2>
          <div class="target-grid">
            <button
              v-for="job in jobOptions"
              :key="job.id"
              type="button"
              class="target-card"
              :class="{ active: selectedId === job.id }"
              @click="selectedId = job.id"
            >
              <span class="target-icon" :class="job.theme">
                <el-icon><component :is="job.icon" /></el-icon>
              </span>
              <strong>{{ job.shortName }}</strong>
              <el-icon class="target-check">
                <CircleCheckFilled v-if="selectedId === job.id" />
                <CircleCheck v-else />
              </el-icon>
            </button>
          </div>

          <div class="skill-block">
            <span>相关技能 <small>（可多选）</small></span>
            <div class="skill-row">
              <button
                v-for="skill in skills"
                :key="skill"
                type="button"
                :class="{ active: selectedSkills.includes(skill) }"
                @click="toggleSkill(skill)"
              >
                {{ skill }}
                <el-icon v-if="selectedSkills.includes(skill)"><Check /></el-icon>
              </button>
              <button type="button" class="add-skill">
                <el-icon><Plus /></el-icon>
                添加技能
              </button>
            </div>
          </div>
        </section>

        <section class="form-panel">
          <h2>4. 简历 / 项目经验</h2>
          <el-upload class="resume-upload" action="#" :auto-upload="false" drag>
            <el-icon><Upload /></el-icon>
            <span>点击上传简历 <small>（PDF/DOC/DOCX，≤ 10MB）</small></span>
            <em>或拖拽文件到此处上传</em>
            <button type="button">从历史简历选择</button>
          </el-upload>

          <label class="project-field">
            <span>项目经验 <small>（选填）</small></span>
            <el-input
              v-model="projectExperience"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="请简要描述你参与过的项目，包括项目背景、你的角色、使用的技术栈、以及你解决的关键问题..."
            />
          </label>
        </section>
      </main>

      <aside class="prep-side">
        <section class="side-panel focus-panel">
          <h2>面试方案预览</h2>
          <div class="focus-card">
            <h3><el-icon><Aim /></el-icon>本次面试重点</h3>
            <div v-for="focus in focusList" :key="focus.title" class="focus-item">
              <span><el-icon><component :is="focus.icon" /></el-icon></span>
              <div>
                <strong>{{ focus.title }}</strong>
                <p>{{ focus.desc }}</p>
              </div>
            </div>
          </div>
        </section>

        <section class="side-panel summary-panel">
          <h2>面试设置汇总</h2>
          <dl>
            <div>
              <dt>目标岗位</dt>
              <dd>{{ selectedJob?.name || '待选择' }}</dd>
            </div>
            <div>
              <dt>面试时长</dt>
              <dd>{{ interview.duration }}</dd>
            </div>
            <div>
              <dt>难度等级</dt>
              <dd>{{ interview.difficulty }}</dd>
            </div>
            <div>
              <dt>面试语言</dt>
              <dd>{{ interview.language }}</dd>
            </div>
            <div>
              <dt>相关技能</dt>
              <dd>{{ selectedSkills.length ? selectedSkills.join('、') : '待选择' }}</dd>
            </div>
          </dl>
        </section>

        <button class="generate-button" type="button" :disabled="!selectedId" @click="startInterview">
          <el-icon><MagicStick /></el-icon>
          生成面试方案
        </button>
        <p class="generate-tip"><el-icon><Lock /></el-icon>方案生成后可直接开始模拟面试</p>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Aim,
  Calendar,
  ChatDotRound,
  Check,
  CircleCheck,
  CircleCheckFilled,
  Clock,
  Collection,
  Cpu,
  FolderChecked,
  Lock,
  MagicStick,
  Medal,
  Platform,
  Plus,
  Suitcase,
  Upload
} from '@element-plus/icons-vue'

const router = useRouter()

const steps = ['基本信息', '岗位选择', '简历上传', '准备完成']

const candidate = reactive({
  name: '',
  school: '',
  major: '',
  graduation: ''
})

const interview = reactive({
  duration: '30分钟',
  difficulty: '中等',
  language: '普通话'
})

const jobOptions = [
  { id: 1, name: 'Java后端开发工程师', shortName: 'Java后端开发', icon: Collection, theme: 'java' },
  { id: 2, name: 'Web前端开发工程师', shortName: 'Web前端开发', icon: Platform, theme: 'web' },
  { id: 3, name: 'AI算法工程师', shortName: 'AI算法工程师', icon: Cpu, theme: 'ai' },
  { id: 4, name: '产品经理', shortName: '产品经理', icon: Suitcase, theme: 'pm' }
]

const skills = ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Vue', 'Python']
const selectedId = ref(null)
const selectedSkills = ref([])
const projectExperience = ref('')

const selectedJob = computed(() => jobOptions.find((job) => job.id === selectedId.value) || null)

const focusList = [
  { title: '专业知识', desc: '考察相关技术栈的核心概念与应用', icon: Medal },
  { title: '项目表达', desc: '评估项目经验、问题解决与成果展示', icon: FolderChecked },
  { title: '动态追问', desc: '根据回答深度进行层层深入提问', icon: ChatDotRound },
  { title: '岗位匹配', desc: '综合评估与岗位要求的契合度', icon: Clock }
]

const toggleSkill = (skill) => {
  const index = selectedSkills.value.indexOf(skill)
  if (index >= 0) {
    selectedSkills.value.splice(index, 1)
  } else {
    selectedSkills.value.push(skill)
  }
}

const startInterview = () => {
  if (!selectedId.value) return
  router.push({
    path: '/resume',
    query: {
      jobId: selectedId.value,
      difficulty: interview.difficulty,
      duration: interview.duration
    }
  })
}
</script>

<style scoped>
.prep-page {
  width: min(1280px, 100%);
  margin: 0 auto;
}

.prep-heading {
  margin-bottom: 28px;
}

.prep-heading h1 {
  color: #061933;
  font-size: 32px;
  font-weight: 950;
  line-height: 1.1;
}

.prep-heading p {
  margin-top: 10px;
  color: #66758f;
  font-size: 15px;
  font-weight: 600;
}

.prep-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  max-width: 820px;
  margin-bottom: 22px;
}

.step-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #74829b;
}

.step-item:not(:last-child)::after {
  position: absolute;
  right: -18px;
  left: 78px;
  top: 20px;
  height: 1px;
  content: '';
  border-top: 1px dashed #c9d5e6;
}

.step-item span {
  position: relative;
  z-index: 1;
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  background: #eef3fb;
  border: 1px solid #d1dce9;
  border-radius: 50%;
  font-size: 16px;
  font-weight: 900;
}

.step-item.active {
  color: #10213d;
}

.step-item.active span {
  color: #fff;
  background: linear-gradient(180deg, #3b83ff, #156bff);
  border-color: transparent;
}

.step-item strong {
  font-size: 15px;
  font-weight: 900;
}

.prep-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: start;
}

.prep-main,
.prep-side {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.form-panel,
.side-panel {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #dbe4f2;
  border-radius: 8px;
  box-shadow: 0 14px 34px rgba(34, 74, 137, 0.05);
}

.form-panel {
  padding: 20px;
}

.side-panel {
  padding: 22px;
}

.form-panel h2,
.side-panel h2 {
  color: #1b2d49;
  font-size: 18px;
  font-weight: 950;
}

.form-panel h2 small {
  color: #7a879d;
  font-size: 13px;
  font-weight: 700;
}

.field-grid {
  display: grid;
  gap: 16px;
  margin-top: 14px;
}

.basic-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.setting-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

label span,
.skill-block > span,
.project-field > span {
  display: block;
  margin-bottom: 8px;
  color: #5c6b84;
  font-size: 14px;
  font-weight: 800;
}

label :deep(.el-input__wrapper),
label :deep(.el-select__wrapper) {
  min-height: 44px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d7e0ed inset;
}

.target-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.target-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 0 30px 0 14px;
  color: #182a47;
  text-align: left;
  background: #fff;
  border: 1px solid #d9e2ee;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.target-card.active {
  border-color: #7b58ff;
  box-shadow: 0 0 0 2px rgba(123, 88, 255, 0.1);
}

.target-icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  font-size: 24px;
}

.target-icon.java {
  color: #ed4d42;
}

.target-icon.web {
  color: #16a76a;
}

.target-icon.ai {
  color: #3477c8;
}

.target-icon.pm {
  color: #11b981;
}

.target-card strong {
  font-size: 14px;
  font-weight: 900;
  line-height: 1.25;
}

.target-check {
  position: absolute;
  right: 12px;
  top: 15px;
  color: #d0d8e5;
  font-size: 18px;
}

.target-card.active .target-check {
  color: #7558ff;
}

.skill-block {
  margin-top: 18px;
}

.skill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-row button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 14px;
  color: #334560;
  background: #fff;
  border: 1px solid #d9e2ee;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.skill-row button.active {
  color: #704cff;
  background: #f7f3ff;
  border-color: #8b6dff;
}

.skill-row .add-skill {
  color: #2674ff;
}

.resume-upload {
  margin-top: 18px;
}

.resume-upload :deep(.el-upload-dragger) {
  position: relative;
  display: grid;
  min-height: 72px;
  padding: 16px 176px 12px 20px;
  place-items: center;
  border: 1px dashed #83a5ff;
  border-radius: 8px;
  background: #fff;
}

.resume-upload :deep(.el-icon) {
  color: #2674ff;
  font-size: 18px;
}

.resume-upload span {
  color: #2674ff;
  font-size: 14px;
  font-weight: 900;
}

.resume-upload span small,
.resume-upload em {
  color: #7f8ca3;
  font-style: normal;
  font-weight: 700;
}

.resume-upload button {
  position: absolute;
  right: 18px;
  top: 18px;
  height: 38px;
  padding: 0 16px;
  color: #65738d;
  background: #fff;
  border: 1px solid #d7e0ed;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.project-field {
  display: block;
  margin-top: 18px;
}

.project-field :deep(.el-textarea__inner) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d7e0ed inset;
}

.focus-card {
  padding: 20px;
  margin-top: 18px;
  border: 1px solid #dfe7f1;
  border-radius: 8px;
}

.focus-card h3 {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #172944;
  font-size: 18px;
  font-weight: 950;
}

.focus-card h3 .el-icon {
  color: #7657ff;
  font-size: 22px;
}

.focus-item {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  margin-top: 18px;
}

.focus-item > span {
  display: grid;
  width: 34px;
  height: 34px;
  color: #407cff;
  place-items: center;
  background: #ecf2ff;
  border-radius: 10px;
  font-size: 19px;
}

.focus-item strong {
  color: #1e304d;
  font-size: 16px;
  font-weight: 950;
}

.focus-item p {
  margin-top: 7px;
  color: #728098;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.5;
}

.summary-panel dl {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.summary-panel div {
  display: grid;
  grid-template-columns: 94px 1fr;
  gap: 12px;
}

.summary-panel dt {
  color: #7b879c;
  font-weight: 800;
}

.summary-panel dd {
  min-width: 0;
  color: #51627e;
  font-weight: 800;
}

.generate-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 56px;
  color: #fff;
  background: linear-gradient(135deg, #7a38ff, #367fff);
  border: 0;
  border-radius: 8px;
  box-shadow: 0 16px 30px rgba(84, 90, 255, 0.22);
  cursor: pointer;
  font-size: 17px;
  font-weight: 950;
}

.generate-button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.generate-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #8a96aa;
  font-size: 14px;
  font-weight: 800;
}

@media (max-width: 1060px) {
  .prep-grid {
    grid-template-columns: 1fr;
  }

  .basic-grid,
  .target-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .prep-steps,
  .basic-grid,
  .setting-grid,
  .target-grid {
    grid-template-columns: 1fr;
  }

  .step-item:not(:last-child)::after {
    display: none;
  }

  .resume-upload :deep(.el-upload-dragger) {
    padding: 18px;
  }

  .resume-upload button {
    position: static;
    margin-top: 12px;
  }
}
</style>
