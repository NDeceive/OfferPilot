<template>
  <AppLayout>
    <div class="page-container">
      <!-- Page Header -->
      <header class="page-header reveal">
        <h1 class="page-title">面试准备</h1>
        <p class="page-desc">选择目标岗位，上传简历，AI 将为你定制专属面试方案</p>
        <!-- 与 AI 对话入口互为切换：两条路最终落在同一个第 4 步，流程完全一致 -->
        <button class="mode-switch" @click="router.push('/interview/ai')">
          <span aria-hidden="true">⇄</span> 切换为 AI 对话
        </button>
      </header>

      <!-- Step Indicator -->
      <nav class="stepper reveal" aria-label="面试准备步骤">
        <div
          v-for="(step, i) in stepsInfo"
          :key="i"
          class="stepper__item"
          :class="{
            'stepper__item--active': currentStep === i,
            'stepper__item--done': isStepDone(i),
            'stepper__item--skipped': isStepSkipped(i)
          }"
        >
          <div class="stepper__dot" :aria-current="currentStep === i ? 'step' : undefined">
            <svg v-if="isStepDone(i)" class="stepper__check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <span v-else class="stepper__num">{{ i + 1 }}</span>
          </div>
          <div class="stepper__text-col">
            <span class="stepper__label">{{ step }}</span>
            <span v-if="isStepSkipped(i)" class="stepper__warn">⚠️ 未上传简历</span>
          </div>
        </div>
        <div class="stepper__track">
          <div class="stepper__track-fill" :style="{ width: (currentStep / 3 * 100) + '%' }" />
        </div>
      </nav>

      <!-- ========== Step 1: Select Family → Position ========== -->
        <section v-show="currentStep === 0" key="step0" class="step-panel">
          <!-- Search -->
          <div class="search-area reveal">
            <div class="search-box">
              <svg class="search-box__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                class="search-box__input"
                :placeholder="activeFamily ? '搜索' + activeFamily + '岗位...' : '搜索岗位名称或技能标签...'"
              />
            </div>
          </div>

          <!-- Family Chips -->
          <div class="family-row reveal">
            <button
              v-for="fam in familyStats"
              :key="fam.code"
              class="family-chip"
              :class="{ 'family-chip--active': activeFamily === fam.code }"
              :style="{ '--chip-accent': fam.color.accentColor }"
              @click="selectFamily(fam.code)"
            >
              <span class="family-chip__icon" :style="{ background: fam.color.iconBg, color: fam.color.accentColor }">
                {{ fam.icon }}
              </span>
              <span class="family-chip__name">{{ fam.name }}</span>
              <span class="family-chip__count">{{ fam.count }}</span>
            </button>
          </div>

          <!-- Position Grid (visible when family or search is active) -->
          <div v-if="activeFamily || searchQuery" class="job-grid" :style="gridMinHeight ? { minHeight: gridMinHeight + 'px' } : {}">
            <article
              v-for="(job, idx) in filteredJobs"
              :key="job.id"
              class="job-card reveal"
              :class="{
                'job-card--selected': selectedJob?.id === job.id,
                'job-card--disabled': !READY_JOBS.has(job.code)
              }"
              :style="{ '--card-accent': job.accentColor, '--reveal-delay': idx * 0.04 + 's' }"
              @click="selectPosition(job, $event)"
              role="button"
              :tabindex="READY_JOBS.has(job.code) ? 0 : -1"
              @keydown.enter="selectPosition(job)"
              @keydown.space.prevent="selectPosition(job)"
            >
              <div class="job-card__accent" />

              <div class="job-card__head">
                <div class="job-card__icon" :style="{ background: job.iconBg }">
                  {{ job.title.charAt(0) }}
                </div>
                <span v-if="!READY_JOBS.has(job.code)" class="job-card__soon">敬请期待</span>
                <span v-else class="job-card__code">{{ job.code }}</span>
              </div>

              <h3 class="job-card__title">{{ job.title }}</h3>

              <div class="job-card__tags">
                <span v-for="tag in job.tags.slice(0, 3)" :key="tag" class="job-card__tag">{{ tag }}</span>
              </div>

              <!-- Select indicator circle -->
              <div class="job-card__select" :class="{ 'job-card__select--on': selectedJob?.id === job.id }">
                <svg v-if="selectedJob?.id === job.id" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
            </article>
          </div>

          <!-- Empty prompt -->
          <div v-else class="family-prompt reveal">
            <div class="family-prompt__icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--neutral-300)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </div>
            <p class="family-prompt__text">请选择一个岗位族，查看其下具体岗位</p>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <div />
            <button class="btn btn--primary" :disabled="!selectedJob" @click="currentStep = 1">
              下一步：上传简历
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </section>

        <!-- ========== Step 2: Upload Resume ========== -->
        <section v-show="currentStep === 1" key="step1" class="step-panel">
          <div class="upload-layout">
            <div class="upload-card card reveal">
              <h2 class="card__title">上传简历</h2>
              <p class="card__desc">AI 将自动提取你的技能标签和项目经历，用于个性化面试方案</p>

              <div
                class="upload-zone"
                :class="{
                  'upload-zone--dragging': isDragging,
                  'upload-zone--filled': uploadedFile
                }"
                @dragover.prevent="isDragging = true"
                @dragleave="isDragging = false"
                @drop.prevent="handleDrop"
                @click="triggerUpload"
              >
                <input
                  ref="fileInput"
                  type="file"
                  accept=".pdf,.doc,.docx"
                  hidden
                  @change="handleFileChange"
                />

                <template v-if="!uploadedFile">
                  <div class="upload-zone__icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                  </div>
                  <p class="upload-zone__text">拖拽简历到此处，或 <span class="upload-zone__link">点击上传</span></p>
                  <p class="upload-zone__hint">支持 PDF / Word 格式，最大 10MB</p>
                </template>

                <template v-else>
                  <div class="file-chip">
                    <div class="file-chip__icon">
                      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--accent-600)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                      </svg>
                    </div>
                    <div class="file-chip__info">
                      <span class="file-chip__name">{{ uploadedFile.name }}</span>
                      <span class="file-chip__size">{{ formatSize(uploadedFile.size) }}</span>
                    </div>
                    <button class="file-chip__remove" @click.stop="removeFile" aria-label="删除文件">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                </template>
              </div>

              <p class="online-hint">
                没有简历文件？<button class="online-hint__btn" @click="showOnlineResume = true">在线填一份</button>
                也一样能开始面试
              </p>

              <!-- Extracted Skills -->
              <Transition name="slide-up">
                <div v-if="extractedSkills.length" class="extracted-skills">
                  <h3 class="extracted-skills__title">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="22 4 12 14.01 9 11.01" />
                    </svg>
                    AI 提取的技能标签
                  </h3>
                  <div class="extracted-skills__grid">
                    <span
                      v-for="(skill, i) in extractedSkills"
                      :key="skill"
                      class="skill-pill"
                      :style="{ animationDelay: i * 0.04 + 's' }"
                    >
                      {{ skill }}
                      <button class="skill-pill__remove" @click.stop="removeSkill(i)" title="移除标签">&times;</button>
                    </span>
                  </div>
                  <button class="add-tag-btn" @click="openTagDialog">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    自行增加
                  </button>
                </div>
              </Transition>

              <!-- Tag Selection Dialog -->
              <Transition name="modal">
                <div v-if="showTagDialog" class="modal-overlay" @click.self="showTagDialog = false">
                  <div class="tag-dialog">
                    <h3 class="tag-dialog__title">选择技能标签</h3>
                    <p class="tag-dialog__hint">勾选需要添加到个人画像的标签，这些标签会影响面试出题方向</p>
                    <div v-if="allTags.length" class="tag-dialog__grid">
                      <label
                        v-for="tag in allTags"
                        :key="tag.id"
                        class="tag-dialog__item"
                        :class="{ checked: tagChecked(tag.name) }"
                      >
                        <input type="checkbox" :checked="tagChecked(tag.name)" @change="toggleTag(tag.name)" />
                        <span class="tag-dialog__name">{{ tag.name }}</span>
                        <span v-if="tag.category" class="tag-dialog__cat">{{ tag.category }}</span>
                      </label>
                    </div>
                    <div v-else class="tag-dialog__loading">加载中…</div>
                    <div class="tag-dialog__actions">
                      <button class="modal-btn modal-btn-cancel" @click="showTagDialog = false">完成</button>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Job Summary Sidebar -->
            <aside v-if="selectedJob" class="job-summary card reveal">
              <h3 class="card__title">已选岗位</h3>
              <div class="summary-card">
                <div class="summary-card__icon" :style="{ background: selectedJob.iconBg }">
                  {{ selectedJob.title.charAt(0) }}
                </div>
                <div class="summary-card__body">
                  <span class="summary-card__title">{{ selectedJob.title }}</span>
                  <span class="summary-card__code-label">{{ selectedJob.code }}</span>
                </div>
              </div>
              <div class="summary-section">
                <h4 class="summary-section__label">面试重点</h4>
                <div class="summary-section__tags">
                  <span v-for="t in jobTags" :key="t" class="focus-pill">{{ t }}</span>
                </div>
              </div>
            </aside>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 0">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              上一步
            </button>
            <div class="step-actions__right">
              <button class="btn btn--ghost" @click="currentStep = 2">跳过，直接开始</button>
              <button class="btn btn--primary" @click="currentStep = 2">
                确认并开始面试
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </div>
        </section>

        <!-- ========== Step 3: Module Selection ========== -->
        <section v-show="currentStep === 2" key="step2" class="step-panel">
          <div class="module-layout reveal">
            <div class="module-select-card card">
              <h2 class="card__title">选择训练目标</h2>
              <p class="card__desc">选择 5 个评价维度，设定排序和期望目标。系统将在面试后评估你离目标还有多远。</p>

              <!-- Module grid: 10 cards -->
              <div v-if="modulesLoading" class="module-loading">加载中…</div>
              <div v-else class="module-grid">
                <label
                  v-for="m in allModules"
                  :key="m.code"
                  class="module-check-card"
                  :class="{ 'module-check-card--on': selectedModuleCodes.has(m.code) }"
                >
                  <input
                    type="checkbox"
                    :checked="selectedModuleCodes.has(m.code)"
                    :disabled="!selectedModuleCodes.has(m.code) && selectedModuleCodes.size >= 5"
                    @change="(e) => toggleModule(m.code, e.target.checked)"
                    class="module-check-input"
                  />
                  <span class="module-check-name">{{ m.name }}</span>
                  <span class="module-check-desc">{{ m.description }}</span>
                  <span class="module-check-mark" v-if="selectedModuleCodes.has(m.code)">✓</span>
                </label>
              </div>
            </div>

            <!-- Sort area -->
            <div class="module-sort-card card" v-if="selectedModuleCodes.size > 0">
              <h3 class="card__title">排序与目标</h3>
              <p class="card__desc">拖拽调整顺序，或通过下拉框设定排位（相同数字=并列）</p>

              <TransitionGroup name="sort-tr" tag="div" class="sort-list">
                <div
                  v-for="(m, idx) in sortedSelectedModules"
                  :key="m.code"
                  class="sort-item"
                  :class="{ 'sort-item--dragging': dragIndex === idx, 'sort-item--over': dragOverIndex === idx && dragIndex !== idx }"
                  draggable="true"
                  @dragstart="onDragStart($event, idx)"
                  @dragover.prevent="onDragOver($event, idx)"
                  @drop="onDrop($event, idx)"
                  @dragend="onDragEnd"
                >
                  <!-- 拖拽手柄 -->
                  <div class="sort-item__grip">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                      <line x1="8" y1="6" x2="16" y2="6"/><line x1="8" y1="12" x2="16" y2="12"/><line x1="8" y1="18" x2="16" y2="18"/>
                    </svg>
                  </div>

                  <!-- 模块名 -->
                  <span class="sort-item__name">{{ m.name }}</span>

                  <!-- 排位 + 目标 -->
                  <div class="sort-item__ctrls">
                    <label class="sort-label">排位</label>
                    <select
                      :value="m._rank"
                      @change="(e) => onRankChange(m.code, Number(e.target.value))"
                      class="sort-select"
                    >
                      <option v-for="r in [1,2,3,4,5]" :key="r" :value="r">第{{ r }}位</option>
                    </select>
                    <label class="sort-label">目标</label>
                    <select
                      :value="m._level"
                      @change="(e) => { m._level = Number(e.target.value) }"
                      class="sort-select"
                    >
                      <option v-for="opt in LEVEL_OPTIONS" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                      </option>
                    </select>
                  </div>
                </div>
              </TransitionGroup>

              <!-- Weight preview -->
              <div class="weight-preview" v-if="selectedModuleCodes.size === 5">
                <h4 class="weight-preview__title">权重预览</h4>
                <div class="weight-bars">
                  <div
                    v-for="m in sortedSelectedModules"
                    :key="'w-' + m.code"
                    class="weight-bar"
                  >
                    <span class="weight-bar__label">{{ m.name }}</span>
                    <div class="weight-bar__track">
                      <div
                        class="weight-bar__fill"
                        :style="{ width: ((calcWeights()[m.code] || 0) * 100) + '%' }"
                      />
                    </div>
                    <span class="weight-bar__num">{{ ((calcWeights()[m.code] || 0) * 100).toFixed(0) }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 1">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              上一步
            </button>
            <button class="btn btn--primary" :disabled="selectedModuleCodes.size !== 5" @click="currentStep = 3">
              确认训练目标
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </section>

        <!-- ========== Step 4: Confirm & Start ========== -->
        <section v-show="currentStep === 3" key="step3" class="step-panel">
          <div class="confirm-wrap reveal">
            <div class="confirm-card card">
              <div class="confirm-card__header">
                <div class="confirm-card__icon-ring">
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
                  </svg>
                </div>
                <h2 class="confirm-card__title">准备就绪</h2>
                <p class="confirm-card__subtitle">即将开始你的 AI 模拟面试</p>
              </div>

              <div class="confirm-card__details">
                <div class="detail-row">
                  <span class="detail-row__label">目标岗位</span>
                  <span class="detail-row__value">{{ selectedJob?.title || '快速面试' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">简历</span>
                  <span class="detail-row__value">{{ uploadedFile ? uploadedFile.name : '未上传' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">训练模块</span>
                  <span class="detail-row__value">
                    <span v-if="selectedModuleCodes.size === 5" class="module-tags-inline">
                      <span
                        v-for="m in allModules.filter(x => selectedModuleCodes.has(x.code))"
                        :key="'tag-' + m.code"
                        class="module-tag-chip"
                      >{{ m.name }}</span>
                    </span>
                    <span v-else class="detail-row__value--muted">未选择</span>
                  </span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">预计时长</span>
                  <span class="detail-row__value">20-30 分钟</span>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">题目数量</span>
                  <span class="detail-row__value">6-10 题（含追问）</span>
                </div>
              </div>

              <div class="confirm-card__tip">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10" /><line x1="12" y1="16" x2="12" y2="12" /><line x1="12" y1="8" x2="12.01" y2="8" />
                </svg>
                <span>面试过程中请保持网络稳定，建议使用安静的环境</span>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 2">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 12H5M12 19l-7-7 7-7" />
              </svg>
              返回修改
            </button>
            <button
              class="btn btn--primary btn--lg"
              :disabled="startingInterview"
              @click="handleStartInterview"
            >
              {{ startingInterview ? '正在启动...' : '开始面试' }}
              <svg v-if="!startingInterview" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </button>
          </div>
        </section>

      <!-- 在线简历：与 AI 对话入口共用同一个组件，两条路产出同一份画像 -->
      <OnlineResumeDialog v-model="showOnlineResume" @saved="onOnlineResumeSaved" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import OnlineResumeDialog from '../components/resume/OnlineResumeDialog.vue'
import { getJobList, uploadResumeFile, startInterview, getModules, getResumeFileProfile, getMyResume } from '../api'
import request from '../utils/request'
import { READY_JOBS, JOB_FAMILIES as families, familyColorMap, mapJobFromBackend, parseJsonField } from '../utils/jobs'

/* ------------------------------------------------------------------ */
/*  State                                                              */
/* ------------------------------------------------------------------ */
const router = useRouter()
const route = useRoute()
const currentStep = ref(0)
const searchQuery = ref('')
const selectedJob = ref(null)
const jobTags = computed(() => {
  if (!selectedJob.value) return []
  return [...new Set([...selectedJob.value.tags, ...selectedJob.value.focus])]
})
const isDragging = ref(false)
const uploadedFile = ref(null)
const fileInput = ref(null)
const extractedSkills = ref([])
const allTags = ref([])
const showTagDialog = ref(false)
const showOnlineResume = ref(false)

const jobsLoading = ref(false)
const jobsError = ref('')
const resumeUploading = ref(false)
const resumeError = ref('')
const startingInterview = ref(false)
const startError = ref('')
const activeFamily = ref('')

const stepsInfo = ['选择岗位', '上传简历', '训练目标', '确认信息']

/** 每一步是否真正完成（而非仅被跳过） */
function isStepDone(i) {
  switch (i) {
    case 0: // 选择岗位 — 走到了下一步且确实选了岗位
      return currentStep.value > 0 && selectedJob.value !== null
    case 1: // 上传简历 — 必须确实上传了文件才算完成
      return uploadedFile.value !== null
    case 2: // 训练目标 — 走到了确认页且选了5个模块
      return currentStep.value > 2 && selectedModuleCodes.value.size === 5
    case 3: // 确认信息 — 最后一步永远不显示"完成"
      return false
    default:
      return false
  }
}

/** 步骤是否被跳过（走过但未完成） */
function isStepSkipped(i) {
  // 仅"上传简历"步骤可跳过：currentStep 已越过它 且 确实没上传文件
  if (i === 1) return currentStep.value > 1 && uploadedFile.value === null
  return false
}

/** 已完成步骤数（控制进度条填充宽度） */
const doneCount = computed(() => {
  let count = 0
  for (let i = 0; i < stepsInfo.length; i++) {
    if (isStepDone(i)) count++
  }
  return count
})

/* ------------------------------------------------------------------ */
/*  Module selection state (Step 3)                                     */
/* ------------------------------------------------------------------ */
const allModules = ref([])
const modulesLoading = ref(false)
const selectedModuleCodes = ref(new Set())  // user-selected module codes (max 5)

/** 拖拽排序后的模块顺序（code 数组，位置=排位） */
const sortOrder = ref([])

// Level labels and their target scores
const LEVEL_OPTIONS = [
  { value: 1, label: '简单关注', target: 65 },
  { value: 2, label: '重点提升', target: 75 },
  { value: 3, label: '核心突破', target: 85 },
]

// Weight pool
const RANK_WEIGHT = { 1: 0.30, 2: 0.25, 3: 0.20, 4: 0.15, 5: 0.10 }

/** 按 sortOrder 排序后的选中模块列表 */
const sortedSelectedModules = computed(() => {
  return sortOrder.value.map(code => {
    const m = allModules.value.find(x => x.code === code)
    return m || { code, name: code, _level: 2 }
  })
})

// --- 拖拽状态 ---
const dragIndex = ref(null)
const dragOverIndex = ref(null)

function onDragStart(e, idx) {
  dragIndex.value = idx
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(idx))
  // 让拖拽时的半透明预览生效
  if (e.target.closest('.sort-item')) {
    e.dataTransfer.setDragImage(e.target.closest('.sort-item'), 0, 0)
  }
}

function onDragOver(e, idx) {
  if (dragIndex.value === null) return
  dragOverIndex.value = idx
}

function onDrop(e, idx) {
  if (dragIndex.value === null || dragIndex.value === idx) return
  const fromIdx = dragIndex.value
  const toIdx = idx

  // 移动模块
  const items = [...sortOrder.value]
  const [moved] = items.splice(fromIdx, 1)
  items.splice(toIdx, 0, moved)
  sortOrder.value = items

  // 只交换两个被拖拽项的 _rank，其他项完全不动
  const fromCode = sortOrder.value[toIdx]
  const displacedCode = fromIdx < toIdx
    ? sortOrder.value[toIdx - 1]
    : sortOrder.value[toIdx + 1]

  if (fromCode && displacedCode) {
    const fromMod = allModules.value.find(x => x.code === fromCode)
    const dispMod = allModules.value.find(x => x.code === displacedCode)
    if (fromMod && dispMod) {
      const tmp = fromMod._rank
      fromMod._rank = dispMod._rank
      dispMod._rank = tmp
    }
  }
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

/** 下拉框改变排位 → 重新排序并压缩空位（不出现 1 1 1 1 5 这种跳号） */
function onRankChange(code, newRank) {
  const m = allModules.value.find(x => x.code === code)
  if (m) m._rank = newRank

  // 1. 先按用户选择的 _rank 排序
  const selected = allModules.value.filter(x => selectedModuleCodes.value.has(x.code))
  selected.sort((a, b) => {
    const ra = a._rank || 1
    const rb = b._rank || 1
    if (ra !== rb) return ra - rb
    const ia = sortOrder.value.indexOf(a.code)
    const ib = sortOrder.value.indexOf(b.code)
    return ia - ib
  })

  // 2. 消除空位：去重排序后映射（1→1, 3→2, 5→3，保留同排位）
  const uniqueRanks = [...new Set(selected.map(m => m._rank))].sort((a, b) => a - b)
  const rankMap = {}
  uniqueRanks.forEach((r, i) => { rankMap[r] = i + 1 })
  selected.forEach(m => { m._rank = rankMap[m._rank] })

  sortOrder.value = selected.map(x => x.code)
}

/** 勾选/取消模块 */
function toggleModule(code, checked) {
  if (checked) {
    if (selectedModuleCodes.value.size >= 5) return
    selectedModuleCodes.value.add(code)
  } else {
    selectedModuleCodes.value.delete(code)
  }
  selectedModuleCodes.value = new Set(selectedModuleCodes.value)
  syncSortOrder()
}

// 当选中的模块变化时，同步 sortOrder
function syncSortOrder() {
  const selected = [...selectedModuleCodes.value]
  // 移除已取消选择的
  sortOrder.value = sortOrder.value.filter(c => selected.includes(c))
  // 新选中的追加到末尾，并自动分配顺序排位（不再全挤在第1位）
  for (const code of selected) {
    if (!sortOrder.value.includes(code)) {
      sortOrder.value.push(code)
    }
  }
  // 按当前顺序自动分配排位 1,2,3,4,5（之后用户可拖拽或下拉修改）
  sortOrder.value.forEach((code, idx) => {
    const mod = allModules.value.find(x => x.code === code)
    if (mod) mod._rank = idx + 1
  })
}

/** Calculate weights from sortOrder position */
function calcWeights() {
  const weights = {}
  sortOrder.value.forEach((code, idx) => {
    weights[code] = RANK_WEIGHT[idx + 1] || 0.10
  })
  return weights
}

/** Get module preferences for API（直接从 sortOrder 位置算排位，不依赖 _rank） */
function buildModulePreferences() {
  return sortOrder.value.map((code, idx) => {
    const m = allModules.value.find(x => x.code === code)
    return {
      code,
      rank: idx + 1,         // 位置即排位，不受 _rank 重置影响
      level: m?._level || 2,
    }
  })
}

/* ------------------------------------------------------------------ */
/*  Job data - loaded from API                                         */
/* ------------------------------------------------------------------ */
// READY_JOBS / families / familyColorMap / mapJobFromBackend 已抽到 utils/jobs.js：
// AI 对话入口（AiPrep.vue）要用同一份。尤其 READY_JOBS 是「题库撑得住」的正确性白名单
// （题库支撑见 后端/src/main/resources/db/migration_v4_job_banks.sql，⚠ 岗位与题目没有外键，
// 全靠运行时模糊匹配，加岗位或改标签后必须跑 db/check_pools.py 复算，要求每个岗位可抽题数 >= 25），
// 复制一份迟早会漏掉某个岗位，用户选中后要到启动面试才报「未找到匹配的面试题目」。
const jobs = ref([])

async function fetchJobs() {
  jobsLoading.value = true
  jobsError.value = ''
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
  } catch (e) {
    console.error('Failed to load jobs:', e)
    jobsError.value = '加载岗位列表失败，请刷新重试'
  } finally {
    jobsLoading.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  Computed                                                           */
/* ------------------------------------------------------------------ */
const jobsByFamily = computed(() => {
  const map = {}
  jobs.value.forEach(job => {
    if (!map[job.family]) map[job.family] = []
    map[job.family].push(job)
  })
  return map
})

const familyStats = computed(() => {
  return families.map(f => ({
    ...f,
    count: (jobsByFamily.value[f.code] || []).length,
    color: familyColorMap[f.code] || familyColorMap['后端开发'],
  }))
})

const filteredJobs = computed(() => {
  const pool = activeFamily.value
    ? (jobsByFamily.value[activeFamily.value] || [])
    : jobs.value
  // 可面试的岗位排前面：按接口返回顺序，分区里「敬请期待」的卡片有时恰好排在第一张，
  // 点进去第一眼看到的是点不开的岗位。filter 已经返回新数组，这里 sort 不会改动原列表；
  // Array.sort 自 ES2019 起稳定，所以两组内部仍保持接口返回的次序。
  return pool
    .filter(j => {
      const matchSearch =
        !searchQuery.value ||
        j.title.includes(searchQuery.value) ||
        j.tags.some(t => t.includes(searchQuery.value))
      return matchSearch
    })
    .sort((a, b) => (READY_JOBS.has(a.code) ? 0 : 1) - (READY_JOBS.has(b.code) ? 0 : 1))
})

/* Ensure grid stays as tall as the largest family's grid */
const gridMinHeight = computed(() => {
  if (!activeFamily.value && !searchQuery.value) return null
  const maxCount = Math.max(...families.map(f => (jobsByFamily.value[f.code] || []).length), 0)
  // 3 columns → rows = ceil(count/3), each row ≈ 170px
  return Math.ceil(maxCount / 3) * 170
})

/* ------------------------------------------------------------------ */
/*  Family selector                                                    */
/* ------------------------------------------------------------------ */
function selectFamily(code) {
  activeFamily.value = activeFamily.value === code ? '' : code
  selectedJob.value = null
  searchQuery.value = ''
}

function selectPosition(job, event) {
  if (!READY_JOBS.has(job.code)) {
    // 点击未就绪岗位：卡片晃动 + 角标闪烁
    const card = event?.currentTarget
    if (card) {
      card.classList.add('job-card--shake')
      setTimeout(() => card.classList.remove('job-card--shake'), 500)
      const badge = card.querySelector('.job-card__soon')
      if (badge) {
        badge.classList.add('job-card__soon--flash')
        setTimeout(() => badge.classList.remove('job-card__soon--flash'), 600)
      }
    }
    return
  }
  selectedJob.value = job
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */
function triggerUpload() {
  if (!uploadedFile.value) fileInput.value?.click()
}

function handleFileChange(e) {
  const file = e.target.files[0]
  if (file) {
    uploadedFile.value = file
    simulateExtract()
  }
}

function handleDrop(e) {
  isDragging.value = false
  const file = e.dataTransfer.files[0]
  if (file) {
    uploadedFile.value = file
    simulateExtract()
  }
}

function removeFile() {
  uploadedFile.value = null
  extractedSkills.value = []
}

function simulateExtract() {
  resumeUploading.value = true
  resumeError.value = ''
  uploadResumeFile(uploadedFile.value)
    .then((data) => {
      const skills = []
      if (data.skills) skills.push(...data.skills)
      if (data.keywords) {
        data.keywords.forEach(k => { if (!skills.includes(k)) skills.push(k) })
      }
      extractedSkills.value = skills
    })
    .catch((e) => {
      console.error('Resume upload failed:', e)
      resumeError.value = '简历解析失败，请重试'
    })
    .finally(() => {
      resumeUploading.value = false
    })
}

// ---- 标签管理 ----
function removeSkill(index) {
  extractedSkills.value.splice(index, 1)
  syncTagsToServer()
}

async function openTagDialog() {
  showTagDialog.value = true
  if (!allTags.value.length) {
    try {
      const data = await request.get('/tags')
      allTags.value = data || []
    } catch (e) {
      console.error('Failed to load tags', e)
    }
  }
}

function tagChecked(name) {
  return extractedSkills.value.includes(name)
}

function toggleTag(name) {
  const idx = extractedSkills.value.indexOf(name)
  if (idx >= 0) {
    extractedSkills.value.splice(idx, 1)
  } else {
    extractedSkills.value.push(name)
  }
  syncTagsToServer()
}

function syncTagsToServer() {
  request.put('/resume/tags', { tags: extractedSkills.value }).catch(e => {
    console.error('Failed to sync tags', e)
  })
}

/** 在线简历填完：当作「简历已就绪」处理，后续出题读的是同一张 resume 表 */
function onOnlineResumeSaved(payload) {
  extractedSkills.value = payload?.skills || []
  uploadedFile.value = { name: '在线简历', size: 0 }
  resumeError.value = ''
}

function formatSize(bytes) {
  // AI 入口接力过来时只回填一个占位对象（没有真实文件、size 为 0），
  // 没有这道守卫会把它显示成「0 B」
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleStartInterview() {
  if (!selectedJob.value || startingInterview.value) return
  startingInterview.value = true
  startError.value = ''
  try {
    const payload = { jobId: selectedJob.value.id }
    if (selectedModuleCodes.value.size === 5) {
      payload.modulePreferences = buildModulePreferences()
      console.log('发送模块偏好:', JSON.stringify(payload.modulePreferences))
    }
    const res = await startInterview(payload)
    router.push({
      path: '/interview',
      query: {
        sessionId: String(res.sessionId),
        jobId: String(selectedJob.value.id),
        jobName: res.jobName || '',
        duration: String(res.durationSeconds || 1800),
      },
    })
  } catch (e) {
    console.error('Failed to start interview:', e)
    startError.value = '启动面试失败，请重试'
  } finally {
    startingInterview.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  Scroll-reveal via IntersectionObserver                             */
/* ------------------------------------------------------------------ */
let observer = null

function initObserver() {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed')
          observer.unobserve(entry.target)
        }
      })
    },
    { threshold: 0.08, rootMargin: '0px 0px -40px 0px' }
  )
  document.querySelectorAll('.reveal').forEach(el => {
    if (!el.classList.contains('revealed')) observer.observe(el)
  })
}

function scheduleObserve() {
  nextTick(() => {
    requestAnimationFrame(initObserver)
  })
}

watch(currentStep, scheduleObserve)
async function fetchModules() {
  modulesLoading.value = true
  try {
    const data = await getModules()
    allModules.value = (Array.isArray(data) ? data : []).map((m, i) => ({
      ...m,
      _rank: 1,
      _level: (i % 3) + 1,  // 默认轮换：1=简单关注, 2=重点提升, 3=核心突破
    }))
    // Default: select first 5
    if (allModules.value.length >= 5 && selectedModuleCodes.value.size === 0) {
      allModules.value.slice(0, 5).forEach(m => selectedModuleCodes.value.add(m.code))
    }
    // Initialize sort order with sequential ranks
    if (sortOrder.value.length === 0 && selectedModuleCodes.value.size > 0) {
      sortOrder.value = allModules.value
        .filter(m => selectedModuleCodes.value.has(m.code))
        .map(m => m.code)
      syncSortOrder() // 分配 1,2,3,4,5 排位
    }
  } catch (e) {
    console.warn('Failed to load modules:', e)
  } finally {
    modulesLoading.value = false
  }
}

/* ------------------------------------------------------------------ */
/*  承接 AI 对话入口的接力（/jobs?job=CODE&step=2&from=ai）              */
/* ------------------------------------------------------------------ */
/**
 * AiPrep.vue 已经收完岗位和简历，这里只做「接着往下走」。
 *
 * 回读简历画像不是为了展示好看：第 2 步增删标签会调 syncTagsToServer()，用本地数组
 * **整体覆盖** resume.skills。不回读的话，用户一删标签就会把出题依据替换成一份不完整的
 * 列表 —— 只影响展示的部分可以偷懒，这里不行。
 */
async function applyQueryPrefill() {
  const q = route.query

  // 岗位：按 code 命中（两端一致的稳定标识），未就绪的不认，避免选了个没题的岗位
  const code = typeof q.job === 'string' ? q.job : ''
  if (code) {
    const hit = jobs.value.find(j => j.code === code)
    if (hit && READY_JOBS.has(hit.code)) {
      selectedJob.value = hit
      // 不设岗位族的话，第 1 步会停在「请选择一个岗位族」的空态
      activeFamily.value = hit.family
    }
  }

  if (q.from === 'ai') {
    // 文件名只有上传过文件才有，在线简历这条路拿不到（也不需要有）
    let filename = ''
    try {
      const profile = await getResumeFileProfile()
      filename = profile?.filename || ''
    } catch (e) {
      console.warn('Failed to preload resume file profile:', e)
    }

    // 标签以 resume 表为准：InterviewFlowService#extractTagsFromResume 读的就是这张表的
    // skills + keywords，而在线简历只有这张表有数据 —— 只看 file-profile 的话，
    // 在线填的简历到这里会变成「一个标签都没有」，甚至被判成没传过简历。
    // 两个字段后端都存成 JSON 字符串，用 parseJsonField 解一层。
    let skills = []
    try {
      const mine = await getMyResume()
      skills = [...new Set([...parseJsonField(mine?.skills), ...parseJsonField(mine?.keywords)])]
    } catch (e) {
      console.warn('Failed to preload resume tags:', e)
    }

    if (filename || skills.length) {
      // 占位对象：第 1 步靠 uploadedFile !== null 判定「已上传」。这里没有真实文件，
      // size 给 0 并由 formatSize 的守卫显示成空串，而不是「0 B」
      uploadedFile.value = { name: filename || '在线简历', size: 0 }
      extractedSkills.value = skills
    }
  }

  const step = Number(q.step)
  if (Number.isInteger(step) && step >= 0 && step <= 3) {
    currentStep.value = step
  }
}

onMounted(async () => {
  scheduleObserve()
  // 必须等岗位加载完再预选：jobs 还是空数组时按 code 找不到任何岗位，
  // 用户会看到「明明从 AI 页选了岗位，这里却没选上」
  await Promise.all([fetchJobs(), fetchModules()])
  await applyQueryPrefill()
  scheduleObserve()
})
onUnmounted(() => { if (observer) observer.disconnect() })
</script>

<style scoped>
/* ===================================================================
   DESIGN TOKENS (local aliases)
   =================================================================== */
.page-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: var(--space-8) var(--space-6) var(--space-12);
}

/* ===================================================================
   PAGE HEADER
   =================================================================== */
.page-header {
  margin-bottom: var(--space-8);
  text-align: center;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 800;
  color: var(--neutral-900);
  letter-spacing: -0.025em;
  line-height: 1.2;
}

.page-desc {
  margin-top: var(--space-2);
  font-size: var(--text-base);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

/* 与 AI 对话入口的切换按钮：做成低调的胶囊，不抢「面试准备」标题的视觉重心 */
.mode-switch {
  margin-top: var(--space-4);
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-full);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.mode-switch:hover {
  border-color: var(--accent-500);
  color: var(--accent-600);
  background: var(--accent-50);
}

/* ===================================================================
   STEP INDICATOR (Stepper)
   =================================================================== */
.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-10);
  position: relative;
  margin-bottom: var(--space-10);
  padding: var(--space-4) 0;
}

.stepper__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  z-index: 1;
}

.stepper__dot {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-full);
  border: 2px solid var(--neutral-300);
  background: var(--surface-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--neutral-400);
  transition:
    background var(--duration-normal) var(--ease-out-expo),
    border-color var(--duration-normal) var(--ease-out-expo),
    color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo);
}

.stepper__item--active .stepper__dot {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
  box-shadow: var(--shadow-accent);
}

.stepper__item--done .stepper__dot {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
}

.stepper__check {
  display: block;
}

.stepper__num {
  display: block;
  line-height: 1;
}

.stepper__label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-400);
  font-family: var(--font-body);
  transition: color var(--duration-normal);
}

.stepper__item--active .stepper__label {
  color: var(--neutral-900);
  font-weight: 600;
}

.stepper__item--done .stepper__label {
  color: var(--neutral-600);
}

/* Skipped (e.g. 未上传简历) */
.stepper__item--skipped .stepper__dot {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.08);
  color: #f59e0b;
}

.stepper__item--skipped .stepper__label {
  color: #92400e;
}

.stepper__text-col {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
}

.stepper__warn {
  font-size: 10px;
  color: #d97706;
  font-weight: 500;
  white-space: nowrap;
}

/* Track */
.stepper__track {
  position: absolute;
  top: 50%;
  left: 22%;
  right: 22%;
  height: 2px;
  background: var(--neutral-200);
  border-radius: 1px;
  transform: translateY(-50%);
  z-index: 0;
}

.stepper__track-fill {
  height: 100%;
  background: var(--accent-500);
  border-radius: 1px;
  transition: width 0.6s var(--ease-out-expo);
}

/* ===================================================================
   STEP PANEL (container with transition)
   =================================================================== */
.step-panel {
  outline: none;
  animation: step-enter 0.3s var(--ease-out-expo);
}

@keyframes step-enter {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.step-fade-enter-active {
  transition: opacity 0.25s var(--ease-out-expo), transform 0.25s var(--ease-out-expo);
}
.step-fade-leave-active {
  transition: opacity 0.15s ease-in, transform 0.15s ease-in;
}
.step-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}
.step-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ===================================================================
   STEP 1: SEARCH & FILTER
   =================================================================== */
.search-area {
  margin-bottom: var(--space-6);
}

.search-box {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo);
  margin-bottom: var(--space-4);
}

.search-box:focus-within {
  border-color: var(--accent-400);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.search-box__icon {
  color: var(--neutral-400);
  flex-shrink: 0;
}

.search-box__input {
  flex: 1;
  border: none;
  background: none;
  color: var(--neutral-900);
  font-family: var(--font-body);
  font-size: var(--text-base);
  outline: none;
}

.search-box__input::placeholder {
  color: var(--neutral-400);
}

/* Family selector row */
.family-row {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-6);
}

.family-chip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  background: var(--surface-elevated);
  cursor: pointer;
  transition:
    background var(--duration-fast),
    border-color var(--duration-fast),
    box-shadow var(--duration-fast),
    transform var(--duration-fast);
}

.family-chip:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
  transform: translateY(-1px);
}

.family-chip--active {
  border-color: var(--chip-accent, var(--accent-500));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--chip-accent, var(--accent-500)) 15%, transparent);
}

.family-chip__icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: 700;
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.family-chip--active .family-chip__icon {
  background: var(--chip-accent, var(--accent-500)) !important;
  color: #fff !important;
}

.family-chip__name {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--neutral-700);
  white-space: nowrap;
}

.family-chip--active .family-chip__name {
  font-weight: 600;
}

.family-chip__count {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--neutral-400);
  padding: 1px 7px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
}

/* Job card code badge */
.job-card__code {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-500);
  letter-spacing: 0.03em;
}

/* "敬请期待" badge */
.job-card__soon {
  font-size: 10px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-400);
  letter-spacing: 0.02em;
  transition: all 0.15s ease;
}

.job-card__soon--flash {
  background: var(--accent-500);
  color: #fff;
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.5);
}

/* Disabled job card */
.job-card--disabled {
  opacity: 0.45;
  cursor: not-allowed;
  filter: grayscale(0.6);
}

.job-card--disabled:hover {
  transform: none;
  box-shadow: none;
  border-color: var(--neutral-200);
}

.job-card--shake,
.job-card--shake:hover {
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10% { transform: translateX(-6px); }
  30% { transform: translateX(6px); }
  50% { transform: translateX(-4px); }
  70% { transform: translateX(4px); }
  90% { transform: translateX(-2px); }
}

/* Family prompt (empty state) */
.family-prompt {
  text-align: center;
  padding: var(--space-12) 0;
  margin-bottom: var(--space-6);
}

.family-prompt__icon {
  margin-bottom: var(--space-4);
}

.family-prompt__text {
  font-size: var(--text-base);
  color: var(--neutral-400);
  font-family: var(--font-body);
}

/* ===================================================================
   JOB GRID
   =================================================================== */
.job-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  align-content: start;
}

/* ===================================================================
   JOB CARD
   =================================================================== */
.job-card {
  position: relative;
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-5) var(--space-5) var(--space-4);
  cursor: pointer;
  overflow: hidden;
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    transform var(--duration-normal) var(--ease-out-expo);
}

.job-card:hover {
  border-color: var(--neutral-300);
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}

/* Accent bar at top */
.job-card__accent {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--card-accent, var(--neutral-300));
  opacity: 0.5;
  transition: opacity var(--duration-normal);
}

.job-card:hover .job-card__accent {
  opacity: 1;
}

/* Selected state */
.job-card--selected {
  border-color: var(--accent-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12), var(--shadow-md);
}

.job-card--selected .job-card__accent {
  opacity: 1;
  background: var(--accent-500);
}

/* Featured (Pro) card */
.job-card--featured {
  border-color: var(--accent-300);
  background: linear-gradient(
    180deg,
    rgba(16, 185, 129, 0.02) 0%,
    var(--surface-elevated) 40%
  );
}

.job-card--featured .job-card__accent {
  opacity: 0.8;
}

/* Card header */
.job-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-3);
}

.job-card__icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-700);
  flex-shrink: 0;
}

.pro-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  color: #fff;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  line-height: 1.3;
  box-shadow: var(--shadow-accent);
}

/* Card title */
.job-card__title {
  font-family: var(--font-display);
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: var(--space-3);
  line-height: 1.3;
}

/* Tags */
.job-card__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: var(--space-4);
}

.job-card__tag {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-600);
  font-family: var(--font-mono);
  letter-spacing: 0.01em;
}

/* Select indicator circle */
.job-card__select {
  position: absolute;
  bottom: 14px;
  right: 14px;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  border: 2px solid var(--neutral-300);
  background: var(--surface-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  color: transparent;
  transition: all 0.25s var(--ease-out-expo);
}

.job-card__select--on {
  border-color: var(--accent-500);
  background: var(--accent-500);
  color: #fff;
  box-shadow: var(--shadow-accent);
}

/* ===================================================================
   STEP 2: UPLOAD
   =================================================================== */
.upload-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: var(--space-6);
  align-items: start;
}

.card {
  background: var(--surface-elevated);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
}

.card__title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-1);
}

.card__desc {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  margin-bottom: var(--space-6);
  line-height: 1.6;
}

/* Upload zone */
.upload-zone {
  border: 2px dashed var(--neutral-300);
  border-radius: var(--radius-lg);
  padding: var(--space-12) var(--space-6);
  text-align: center;
  cursor: pointer;
  transition:
    border-color var(--duration-normal) var(--ease-out-expo),
    background var(--duration-normal) var(--ease-out-expo),
    padding var(--duration-normal) var(--ease-out-expo);
}

.upload-zone:hover,
.upload-zone--dragging {
  border-color: var(--accent-400);
  background: var(--accent-50);
}

.upload-zone--dragging {
  border-style: solid;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.upload-zone--filled {
  border-style: solid;
  border-color: var(--accent-200);
  cursor: default;
  padding: var(--space-5);
  text-align: left;
}

.upload-zone__icon {
  color: var(--neutral-400);
  margin-bottom: var(--space-4);
  transition: color var(--duration-normal);
}

.upload-zone:hover .upload-zone__icon {
  color: var(--accent-500);
}

.upload-zone__text {
  font-size: var(--text-base);
  color: var(--neutral-600);
  margin-bottom: var(--space-2);
}

.upload-zone__link {
  color: var(--accent-600);
  font-weight: 600;
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 2px;
}

.upload-zone__hint {
  font-size: var(--text-sm);
  color: var(--neutral-400);
}

/* 没有简历文件时的第二条路 */
.online-hint {
  margin: var(--space-4) 0 0;
  font-size: var(--text-sm);
  color: var(--neutral-500);
  text-align: center;
}

.online-hint__btn {
  border: none;
  background: transparent;
  padding: 0 2px;
  color: var(--accent-600);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.online-hint__btn:hover {
  color: var(--accent-700);
}

/* File chip (after upload) */
.file-chip {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.file-chip__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--accent-50);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-chip__info {
  flex: 1;
  min-width: 0;
}

.file-chip__name {
  display: block;
  font-weight: 600;
  color: var(--neutral-900);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-chip__size {
  font-size: var(--text-sm);
  color: var(--neutral-500);
  font-family: var(--font-mono);
}

.file-chip__remove {
  background: none;
  border: none;
  color: var(--neutral-400);
  cursor: pointer;
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast);
  flex-shrink: 0;
}

.file-chip__remove:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

/* Extracted skills */
.extracted-skills {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 1px solid var(--neutral-200);
}

.extracted-skills__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--accent-600);
  margin-bottom: var(--space-3);
  font-family: var(--font-body);
}

.extracted-skills__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.skill-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: var(--space-1) var(--space-2) var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-size: var(--text-sm);
  font-family: var(--font-mono);
  animation: skill-pop 0.3s var(--ease-out-expo) backwards;
}

.skill-pill__remove {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--accent-400);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--duration-fast);
  flex-shrink: 0;
  margin-left: 2px;
}
.skill-pill__remove:hover {
  background: var(--accent-200);
  color: var(--accent-700);
}

.add-tag-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: var(--space-3);
  padding: var(--space-1) var(--space-3);
  border: 1px dashed var(--neutral-300);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--neutral-500);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast);
}
.add-tag-btn:hover {
  border-color: var(--accent-400);
  color: var(--accent-600);
  background: var(--accent-50);
}

/* Tag Selection Dialog */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
}

.tag-dialog {
  background: var(--surface-elevated);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  max-width: 520px;
  width: 90%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-xl);
}
.tag-dialog__title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
  margin-bottom: var(--space-2);
}
.tag-dialog__hint {
  font-size: var(--text-xs);
  color: var(--neutral-500);
  margin-bottom: var(--space-4);
  line-height: 1.5;
}
.tag-dialog__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  overflow-y: auto;
  flex: 1;
  padding-bottom: var(--space-2);
}
.tag-dialog__item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  cursor: pointer;
  transition: all var(--duration-fast);
  user-select: none;
}
.tag-dialog__item:hover {
  border-color: var(--accent-300);
  background: var(--accent-50);
}
.tag-dialog__item.checked {
  border-color: var(--accent-400);
  background: var(--accent-50);
  color: var(--accent-700);
}
.tag-dialog__item input[type="checkbox"] {
  accent-color: var(--color-primary);
  width: 14px;
  height: 14px;
  cursor: pointer;
}
.tag-dialog__name {
  font-weight: 500;
  color: var(--neutral-800);
}
.tag-dialog__cat {
  font-size: var(--text-xs);
  color: var(--neutral-400);
}
.tag-dialog__loading {
  text-align: center;
  color: var(--neutral-400);
  padding: var(--space-6);
}
.tag-dialog__actions {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

.modal-btn {
  padding: var(--space-2) var(--space-5);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: all var(--duration-fast);
}
.modal-btn-cancel {
  background: var(--neutral-100);
  color: var(--neutral-600);
}
.modal-btn-cancel:hover { background: var(--neutral-200); }

.modal-enter-active,
.modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from,
.modal-leave-to { opacity: 0; }

.slide-up-enter-active {
  transition: all 0.4s var(--ease-out-expo);
}
.slide-up-leave-active {
  transition: all 0.2s ease-in;
}
.slide-up-enter-from {
  opacity: 0;
  transform: translateY(16px);
}
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@keyframes skill-pop {
  from {
    opacity: 0;
    transform: scale(0.8) translateY(4px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* Job summary sidebar */
.job-summary {
  position: sticky;
  top: calc(var(--nav-height) + var(--space-8));
}

.summary-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--neutral-50);
  border-radius: var(--radius-md);
  margin: var(--space-4) 0;
}

.summary-card__icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: var(--neutral-700);
  flex-shrink: 0;
}

.summary-card__body {
  min-width: 0;
}

.summary-card__title {
  display: block;
  font-weight: 600;
  color: var(--neutral-900);
  font-size: var(--text-sm);
  margin-bottom: 2px;
}

.summary-card__code-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--accent-600);
}

.summary-section {
  margin-top: var(--space-4);
}

.summary-section__label {
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--neutral-500);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: var(--space-2);
}

.summary-section__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.focus-pill {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--neutral-100);
  color: var(--neutral-700);
  font-size: var(--text-xs);
  font-family: var(--font-body);
}

/* ===================================================================
   STEP 3: CONFIRM
   =================================================================== */
.confirm-wrap {
  max-width: 540px;
  margin: 0 auto;
}

.confirm-card {
  text-align: center;
}

.confirm-card__header {
  margin-bottom: var(--space-6);
}

.confirm-card__icon-ring {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 2px solid var(--accent-200);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--space-4);
}

.confirm-card__title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  color: var(--neutral-900);
  margin-bottom: var(--space-1);
  letter-spacing: -0.02em;
}

.confirm-card__subtitle {
  color: var(--neutral-500);
  font-size: var(--text-base);
}

.confirm-card__details {
  text-align: left;
  background: var(--neutral-50);
  border-radius: var(--radius-md);
  padding: var(--space-2) var(--space-4);
  margin-bottom: var(--space-4);
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) 0;
}

.detail-row:not(:last-child) {
  border-bottom: 1px solid var(--neutral-200);
}

.detail-row__label {
  font-size: var(--text-sm);
  color: var(--neutral-500);
}

.detail-row__value {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-900);
}

.confirm-card__tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--accent-700);
  padding: var(--space-3) var(--space-4);
  background: var(--accent-50);
  border-radius: var(--radius-md);
  border: 1px solid var(--accent-100);
}

/* ===================================================================
   STEP ACTIONS (bottom nav)
   =================================================================== */
.step-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 1px solid var(--neutral-200);
}

.step-actions__right {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}

/* ===================================================================
   BUTTONS
   =================================================================== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-display);
  font-size: var(--text-sm);
  font-weight: 600;
  border-radius: var(--radius-md);
  border: none;
  cursor: pointer;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background var(--duration-normal) var(--ease-out-expo),
    border-color var(--duration-normal) var(--ease-out-expo),
    box-shadow var(--duration-normal) var(--ease-out-expo),
    transform var(--duration-fast) var(--ease-out-expo),
    color var(--duration-normal) var(--ease-out-expo);
}

.btn--primary {
  padding: var(--space-3) var(--space-6);
  background: var(--accent-500);
  color: #fff;
}

.btn--primary:hover {
  background: var(--accent-600);
  box-shadow: var(--shadow-accent);
  transform: translateY(-1px);
}

.btn--primary:active {
  transform: translateY(0);
}

.btn--primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
  background: var(--accent-500);
}

.btn--lg {
  padding: var(--space-4) var(--space-8);
  font-size: var(--text-base);
}

.btn--ghost {
  padding: var(--space-3) var(--space-5);
  background: var(--surface-elevated);
  border: 1.5px solid var(--neutral-200);
  color: var(--neutral-700);
}

.btn--ghost:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
}

/* ===================================================================
   SCROLL REVEAL
   =================================================================== */
.reveal {
  opacity: 0;
  transform: translateY(24px);
  transition:
    opacity 0.6s var(--ease-out-expo),
    transform 0.6s var(--ease-out-expo);
  transition-delay: var(--reveal-delay, 0s);
}

.reveal.revealed {
  opacity: 1;
  transform: translateY(0);
}

.job-card.reveal {
  opacity: 1;
  transform: translateY(0);
}

/* ===================================================================
   REDUCED MOTION
   =================================================================== */
@media (prefers-reduced-motion: reduce) {
  .reveal {
    opacity: 1;
    transform: none;
    transition: none;
  }

  .step-fade-enter-active,
  .step-fade-leave-active {
    transition: none;
  }

  .check-pop-enter-active,
  .check-pop-leave-active,
  .slide-up-enter-active,
  .slide-up-leave-active {
    transition: none;
  }

  .skill-pill {
    animation: none;
  }

  .stepper__track-fill,
  .stepper__dot,
  .match-bar__fill,
  .btn,
  .job-card,
  .filter-chip,
  .search-box {
    transition: none;
  }
}

/* ===================================================================
   STEP 3: MODULE SELECTION
   =================================================================== */
.module-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-6);
  align-items: start;
}

.module-loading {
  text-align: center;
  padding: var(--space-10);
  color: var(--neutral-400);
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.module-check-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out-expo);
  background: var(--surface-elevated);
}

.module-check-card:hover {
  border-color: var(--accent-300);
  background: var(--accent-50);
}

.module-check-card--on {
  border-color: var(--accent-500);
  background: rgba(16, 185, 129, 0.05);
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.12);
}

.module-check-input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.module-check-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-900);
}

.module-check-card--on .module-check-name {
  color: var(--accent-700);
}

.module-check-desc {
  font-size: 11px;
  color: var(--neutral-400);
  line-height: 1.4;
}

.module-check-mark {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--accent-500);
}

/* Sort area */
.module-sort-card {
  position: sticky;
  top: calc(var(--nav-height) + var(--space-8));
}

.sort-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin: var(--space-4) 0;
  position: relative;
}

.sort-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-2);
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  cursor: default;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.sort-item:hover {
  border-color: var(--neutral-300);
}

.sort-item--dragging {
  opacity: 0.4;
  background: var(--accent-50);
}

.sort-item--over {
  border-color: var(--accent-400);
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.15);
  background: rgba(16, 185, 129, 0.04);
}

/* Drag grip */
.sort-item__grip {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--neutral-300);
  cursor: grab;
  border-radius: var(--radius-sm);
  transition: color 0.15s, background 0.15s;
}

.sort-item__grip:hover {
  color: var(--neutral-500);
  background: var(--neutral-100);
}

.sort-item__grip:active {
  cursor: grabbing;
}

.sort-item__name {
  font-weight: 600;
  font-size: var(--text-sm);
  color: var(--neutral-800);
  flex: 1;
  min-width: 0;
}

.sort-item__ctrls {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.sort-label {
  font-size: 11px;
  color: var(--neutral-400);
  font-weight: 500;
}

.sort-select {
  padding: 4px 8px;
  border: 1px solid var(--neutral-300);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-family: var(--font-body);
  background: var(--surface-elevated);
  color: var(--neutral-800);
  cursor: pointer;
}

/* ===================================================================
   TransitionGroup: sort list animations
   =================================================================== */
.sort-tr-enter-active {
  transition: all 0.4s var(--ease-out-expo);
}

.sort-tr-leave-active {
  transition: all 0.25s ease-in;
  position: absolute;
}

.sort-tr-enter-from {
  opacity: 0;
  transform: translateX(40px) scale(0.95);
}

.sort-tr-leave-to {
  opacity: 0;
  transform: translateX(-30px) scale(0.9);
}

.sort-tr-move {
  transition: transform 0.35s var(--ease-out-expo);
}

/* Weight preview */
.weight-preview {
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--neutral-200);
}

.weight-preview__title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--neutral-700);
  margin-bottom: var(--space-3);
}

.weight-bars {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.weight-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.weight-bar__label {
  font-size: 11px;
  color: var(--neutral-500);
  width: 75px;
  flex-shrink: 0;
  text-align: right;
}

.weight-bar__track {
  flex: 1;
  height: 6px;
  background: var(--neutral-200);
  border-radius: 3px;
  overflow: hidden;
}

.weight-bar__fill {
  height: 100%;
  background: var(--accent-500);
  border-radius: 3px;
  transition: width 0.3s var(--ease-out-expo);
}

.weight-bar__num {
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--neutral-600);
  width: 35px;
  flex-shrink: 0;
}

/* Module tags in confirm step */
.module-tags-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.module-tag-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-weight: 500;
}

.detail-row__value--muted {
  color: var(--neutral-400);
  font-style: italic;
}

/* ===================================================================
   RESPONSIVE: 1024px
   =================================================================== */
@media (max-width: 1024px) {
  .job-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .upload-layout {
    grid-template-columns: 1fr;
  }

  .job-summary {
    position: static;
  }

  .stepper {
    gap: var(--space-6);
  }
}

/* ===================================================================
   RESPONSIVE: 768px
   =================================================================== */
@media (max-width: 768px) {
  .page-container {
    padding: var(--space-6) var(--space-4) var(--space-10);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .stepper {
    gap: var(--space-3);
  }

  .stepper__label {
    display: none;
  }

  .stepper__track {
    left: 18%;
    right: 18%;
  }

  .family-row {
    gap: var(--space-2);
  }

  .family-chip {
    padding: var(--space-2) var(--space-3);
  }

  .family-chip__name {
    font-size: 12px;
  }

  .job-grid {
    grid-template-columns: 1fr;
    gap: var(--space-3);
  }

  .step-actions {
    flex-direction: column-reverse;
    gap: var(--space-3);
    align-items: stretch;
  }

  .step-actions__right {
    flex-direction: column;
    align-items: stretch;
  }

  .btn {
    justify-content: center;
    width: 100%;
  }
}
</style>
