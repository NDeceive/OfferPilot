<template>
  <AppLayout>
    <div class="page-container">
      <!-- Page Header -->
      <header class="page-header reveal">
        <h1 class="page-title">面试准备</h1>
        <p class="page-desc">选择目标岗位并上传简历，系统将结合岗位要求与简历内容生成面试方案</p>
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
            <span v-if="isStepSkipped(i)" class="stepper__status">已跳过</span>
          </div>
        </div>
        <div class="stepper__track">
          <div class="stepper__track-fill" :style="{ transform: `scaleX(${currentStep / (stepsInfo.length - 1)})` }" />
        </div>
      </nav>

      <!-- ========== Step 1: Select Job ========== -->
        <section v-show="currentStep === 0" key="step0" class="step-panel">
          <div class="job-catalog reveal">
            <header class="job-catalog__top">
              <div>
                <h2>选择本次练习岗位</h2>
                <p>从 7 个岗位族、32 个细分方向中找到最接近目标职位的一项</p>
              </div>
              <span class="job-catalog__count">{{ filteredJobs.length }} 个岗位</span>
            </header>

            <div class="search-box">
              <svg class="search-box__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8" /><path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                class="search-box__input"
                placeholder="搜索岗位、技术栈或能力方向，例如 Java、RAG、性能测试"
              />
              <button v-if="searchQuery" class="search-box__clear" type="button" aria-label="清除搜索" @click="searchQuery = ''">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m7 7 10 10M17 7 7 17" /></svg>
              </button>
            </div>

            <div class="job-catalog__layout">
              <aside class="family-nav" aria-label="岗位族">
                <button
                  v-for="family in familyOptions"
                  :key="family.code"
                  type="button"
                  :class="{ 'is-active': activeFamily === family.code }"
                  @click="selectFamily(family.code)"
                >
                  <span class="family-nav__icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path :d="family.iconPath" />
                    </svg>
                  </span>
                  <span class="family-nav__text">{{ family.name }}</span>
                  <span class="family-nav__count">{{ family.count }}</span>
                </button>
              </aside>

              <main class="job-list-panel" aria-live="polite">
                <div v-if="jobsLoading" class="catalog-state">
                  <span class="catalog-state__spinner" />
                  <p>正在加载岗位目录…</p>
                </div>
                <div v-else-if="jobsError" class="catalog-state catalog-state--error">
                  <p>{{ jobsError }}</p>
                  <button type="button" @click="fetchJobs">重新加载</button>
                </div>
                <div v-else-if="!filteredJobs.length" class="catalog-state">
                  <p>没有找到相关岗位</p>
                  <button type="button" @click="searchQuery = ''">清除搜索条件</button>
                </div>
                <div v-else class="job-list">
                  <button
                    v-for="job in filteredJobs"
                    :key="job.id"
                    type="button"
                    class="job-list__item"
                    :class="{ 'is-selected': selectedJob?.id === job.id }"
                    @click="selectedJob = job"
                  >
                    <JobLogo :icon-key="job.iconKey" :tone="job.themeKey" />
                    <span class="job-list__content">
                      <span class="job-list__heading">
                        <strong>{{ job.title }}</strong>
                        <span>{{ job.directionCode }}</span>
                      </span>
                      <span class="job-list__summary">{{ job.summary }}</span>
                      <span class="job-list__tags">
                        <span v-for="tag in job.focus.slice(0, 3)" :key="tag">{{ tag }}</span>
                      </span>
                    </span>
                    <svg class="job-list__arrow" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                      <path d="m9 18 6-6-6-6" />
                    </svg>
                  </button>
                </div>
              </main>

              <aside class="job-detail" :class="{ 'job-detail--empty': !selectedJob }">
                <template v-if="selectedJob">
                  <div class="job-detail__identity">
                    <JobLogo :icon-key="selectedJob.iconKey" :tone="selectedJob.themeKey" />
                    <div>
                      <span>{{ selectedJob.familyName }} · {{ selectedJob.directionCode }}</span>
                      <h3>{{ selectedJob.title }}</h3>
                    </div>
                  </div>
                  <p class="job-detail__summary">{{ selectedJob.summary }}</p>

                  <section class="job-detail__section">
                    <h4>重点考察</h4>
                    <div class="job-detail__abilities">
                      <span v-for="ability in selectedJob.focus" :key="ability">{{ ability }}</span>
                    </div>
                  </section>

                  <section class="job-detail__section">
                    <h4>面试依据</h4>
                    <p>{{ selectedJob.evidence }}</p>
                  </section>

                  <div class="job-detail__selected">
                    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m5 12 4 4L19 6" /></svg>
                    已选择该岗位
                  </div>
                </template>
                <template v-else>
                  <div class="job-detail__empty-icon">
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 11h6M12 8v6M5 20h14a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-4l-2-2h-2L9 5H5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2Z" /></svg>
                  </div>
                  <p>选择一个岗位后，这里会显示具体考察方向。</p>
                </template>
              </aside>
            </div>
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
              <p class="card__desc">系统将识别简历中的技能关键词和项目经历，用于匹配更合适的面试题目</p>

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

              <!-- Extracted Skills -->
              <Transition name="slide-up">
                <div v-if="uploadedFile" class="extracted-skills">
                  <h3 class="extracted-skills__title">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-500)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="22 4 12 14.01 9 11.01" />
                    </svg>
                    从简历中识别的技能标签
                  </h3>
                  <div v-if="extractedSkills.length" class="extracted-skills__grid">
                    <span
                      v-for="(skill, i) in extractedSkills"
                      :key="skill"
                      class="skill-pill"
                      :style="{ animationDelay: i * 0.04 + 's' }"
                    >
                      {{ skill }}
                      <button
                        type="button"
                        class="skill-pill__remove"
                        :aria-label="`移除技能标签 ${skill}`"
                        title="移除标签"
                        @click.stop="removeSkill(i)"
                      >&times;</button>
                    </span>
                  </div>
                  <p v-else class="extracted-skills__empty">暂未识别到技能标签，你可以根据实际经历自行补充。</p>
                  <button class="add-tag-btn" type="button" @click="openTagDialog">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
                    添加技能标签
                  </button>
                </div>
              </Transition>

              <Teleport to="body">
                <Transition name="modal">
                  <div v-if="showTagDialog" class="tag-modal" @click.self="closeTagDialog">
                    <section class="tag-dialog" role="dialog" aria-modal="true" aria-labelledby="tag-dialog-title">
                      <header class="tag-dialog__header">
                        <div>
                          <h3 id="tag-dialog-title">选择技能标签</h3>
                          <p>补充与你经历相符的技能，用于匹配更合适的出题方向。</p>
                        </div>
                        <button type="button" class="tag-dialog__close" aria-label="关闭技能标签选择" @click="closeTagDialog">&times;</button>
                      </header>
                      <div v-if="tagsLoading" class="tag-dialog__state">正在加载技能标签…</div>
                      <div v-else-if="tagsError" class="tag-dialog__state tag-dialog__state--error">
                        <span>{{ tagsError }}</span>
                        <button type="button" @click="fetchSkillTags">重新加载</button>
                      </div>
                      <div v-else-if="allTags.length" class="tag-dialog__grid">
                        <label v-for="tag in allTags" :key="tag.id ?? tag.name" class="tag-dialog__item" :class="{ 'is-checked': tagChecked(tag.name) }">
                          <input type="checkbox" :checked="tagChecked(tag.name)" @change="toggleTag(tag.name)" />
                          <span>{{ tag.name }}</span>
                          <small v-if="tag.category">{{ tag.category }}</small>
                        </label>
                      </div>
                      <div v-else class="tag-dialog__state">暂无可选技能标签</div>
                      <footer class="tag-dialog__footer">
                        <span>已选择 {{ extractedSkills.length }} 项</span>
                        <button class="btn btn--primary" type="button" @click="closeTagDialog">完成</button>
                      </footer>
                    </section>
                  </div>
                </Transition>
              </Teleport>
            </div>

              <!-- Job Summary Sidebar -->
            <aside v-if="selectedJob" class="job-summary card reveal">
              <h3 class="card__title">已选岗位</h3>
              <div class="summary-card">
                <JobLogo :icon-key="selectedJob.iconKey" :tone="selectedJob.themeKey" />
                <div class="summary-card__body">
                  <span class="summary-card__title">{{ selectedJob.title }}</span>
                  <span class="summary-card__match">{{ selectedJob.familyName }} · {{ selectedJob.directionCode }}</span>
                </div>
              </div>
              <div class="summary-section">
                <h4 class="summary-section__label">面试重点</h4>
                <div class="summary-section__tags">
                  <span v-for="focus in selectedJob.focus" :key="focus" class="focus-pill">{{ focus }}</span>
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
              <button class="btn btn--ghost" @click="currentStep = 2">暂不上传</button>
              <button class="btn btn--primary" @click="currentStep = 2">
                下一步：训练目标
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </div>
        </section>

        <!-- ========== Step 3: Training goals ========== -->
        <section v-show="currentStep === 2" key="step2" class="step-panel">
          <div class="module-layout reveal">
            <div class="module-select card">
              <div class="module-heading">
                <div>
                  <h2 class="card__title">选择训练目标</h2>
                  <p class="card__desc">选择 5 个本次重点评价维度，系统会按优先级生成岗位面试与能力报告。</p>
                </div>
                <span class="module-count" :class="{ 'is-ready': selectedModuleCodes.size === 5 }">{{ selectedModuleCodes.size }}/5</span>
              </div>
              <div v-if="modulesLoading" class="catalog-state"><span class="catalog-state__spinner" /><p>正在加载能力维度…</p></div>
              <div v-else-if="modulesError" class="catalog-state catalog-state--error"><p>{{ modulesError }}</p><button type="button" @click="fetchModules">重新加载</button></div>
              <div v-else class="module-grid">
                <label v-for="module in allModules" :key="module.code" class="module-option" :class="{ 'is-selected': selectedModuleCodes.has(module.code), 'is-disabled': !selectedModuleCodes.has(module.code) && selectedModuleCodes.size >= 5 }">
                  <input type="checkbox" :checked="selectedModuleCodes.has(module.code)" :disabled="!selectedModuleCodes.has(module.code) && selectedModuleCodes.size >= 5" @change="toggleModule(module.code)" />
                  <span class="module-option__check">✓</span>
                  <strong>{{ module.name }}</strong>
                  <small>{{ module.description || '岗位能力评价维度' }}</small>
                </label>
              </div>
            </div>

            <aside class="module-priority card">
              <p class="module-priority__eyebrow">评价优先级</p>
              <h3>把最想提升的能力放在前面</h3>
              <p>顺序会影响报告权重；目标档位决定本次评价基准。</p>
              <div v-if="!sortedSelectedModules.length" class="module-priority__empty">从左侧选择训练目标</div>
              <TransitionGroup v-else tag="ol" name="priority" class="priority-list">
                <li
                  v-for="(module, index) in sortedSelectedModules"
                  :key="module.code"
                  :class="{ 'is-dragging': draggedModuleCode === module.code, 'is-drop-target': dragOverModuleCode === module.code && draggedModuleCode !== module.code }"
                  @dragover.prevent
                  @dragenter.prevent="setModuleDropTarget(module.code)"
                  @drop.prevent="dropModule(module.code)"
                >
                  <button
                    type="button"
                    class="drag-handle"
                    draggable="true"
                    :aria-label="`拖动${module.name}调整优先级；按 Alt 加上下方向键也可排序`"
                    @dragstart="startModuleDrag(module.code, $event)"
                    @dragend="finishModuleDrag"
                    @keydown="handleModuleSortKey(index, module.name, $event)"
                  >
                    <span v-for="dot in 6" :key="dot" />
                  </button>
                  <span class="priority-index"><small>优先级</small>{{ index + 1 }}</span>
                  <span class="priority-info">
                    <strong>{{ module.name }}</strong>
                    <small>报告权重 <b>{{ moduleWeights[index] }}%</b></small>
                  </span>
                  <span class="level-picker" role="radiogroup" :aria-label="`${module.name}目标难度`">
                    <button
                      v-for="level in moduleLevelOptions"
                      :key="level.value"
                      type="button"
                      role="radio"
                      :aria-checked="moduleLevels[module.code] === level.value"
                      :class="{ 'is-active': moduleLevels[module.code] === level.value }"
                      :title="level.hint"
                      @click="moduleLevels[module.code] = level.value"
                    ><strong>{{ level.label }}</strong><small>{{ level.hint }}</small></button>
                  </span>
                </li>
              </TransitionGroup>
              <span class="sr-only" aria-live="polite">{{ sortAnnouncement }}</span>
            </aside>
          </div>
          <div class="step-actions">
            <button class="btn btn--ghost" @click="currentStep = 1">上一步</button>
            <button class="btn btn--primary" :disabled="selectedModuleCodes.size !== 5" @click="currentStep = 3">确认训练目标</button>
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
                  <span class="detail-row__label">面试时长</span>
                  <button
                    class="duration-trigger"
                    type="button"
                    aria-haspopup="dialog"
                    @click="openDurationPicker"
                  >
                    <span>{{ formattedDuration }}</span>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <path d="M9 18l6-6-6-6" />
                    </svg>
                  </button>
                </div>
                <div class="detail-row">
                  <span class="detail-row__label">面试节奏</span>
                  <span class="detail-row__value">按所选时长持续进行（含动态追问）</span>
                </div>
                <div class="detail-row detail-row--modules">
                  <span class="detail-row__label">训练目标</span>
                  <span class="module-tags-inline"><span v-for="module in sortedSelectedModules" :key="module.code">{{ module.name }}</span></span>
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
              {{ startingInterview ? '正在创建面试…' : '开始面试' }}
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </button>
          </div>
          <p v-if="startError" class="start-error" role="alert">{{ startError }}</p>
        </section>

        <Teleport to="body">
          <Transition name="duration-modal">
            <div
              v-if="durationPickerOpen"
              class="duration-modal"
              role="presentation"
              @mousedown.self="closeDurationPicker"
            >
              <section
                ref="durationDialog"
                class="duration-dialog"
                role="dialog"
                aria-modal="true"
                aria-labelledby="duration-title"
                tabindex="-1"
                @keydown.esc="closeDurationPicker"
              >
                <header class="duration-dialog__header">
                  <div>
                    <p class="duration-dialog__eyebrow">面试时长</p>
                    <h2 id="duration-title">安排一段专注的练习时间</h2>
                  </div>
                  <button class="duration-dialog__close" type="button" aria-label="关闭时间选择" @click="closeDurationPicker">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
                      <path d="M18 6 6 18M6 6l12 12" />
                    </svg>
                  </button>
                </header>

                <div class="duration-presets" aria-label="常用时长">
                  <button
                    v-for="preset in durationPresets"
                    :key="preset"
                    type="button"
                    :class="{ 'duration-preset--active': draftDurationSeconds === preset * 60 }"
                    @click="selectPreset(preset)"
                  >
                    {{ preset }} 分钟
                  </button>
                </div>

                <div class="duration-wheel" aria-label="滚动选择面试时长">
                  <div class="duration-wheel__highlight" aria-hidden="true" />
                  <div class="duration-wheel__fade duration-wheel__fade--top" aria-hidden="true" />
                  <div class="duration-wheel__fade duration-wheel__fade--bottom" aria-hidden="true" />
                  <div
                    v-for="unit in durationUnits"
                    :key="unit.key"
                    class="duration-wheel__column-wrap"
                  >
                    <div
                      :ref="el => setWheelRef(unit.key, el)"
                      class="duration-wheel__column"
                      :aria-label="unit.label"
                      tabindex="0"
                      @scroll.passive="handleWheelScroll(unit.key)"
                      @keydown.up.prevent="nudgeDuration(unit.key, -1)"
                      @keydown.down.prevent="nudgeDuration(unit.key, 1)"
                    >
                      <button
                        v-for="value in unit.values"
                        :key="value"
                        type="button"
                        :class="{ 'is-selected': draftDuration[unit.key] === value }"
                        :aria-label="`${value}${unit.label}`"
                        @click="setDurationUnit(unit.key, value)"
                      >
                        {{ String(value).padStart(2, '0') }}
                      </button>
                    </div>
                    <span>{{ unit.label }}</span>
                  </div>
                </div>

                <p class="duration-dialog__range">可选范围为 5 分钟至 2 小时，精确到秒</p>

                <footer class="duration-dialog__footer">
                  <button class="btn btn--ghost" type="button" @click="closeDurationPicker">取消</button>
                  <button class="btn btn--primary" type="button" @click="confirmDuration">
                    使用 {{ formattedDraftDuration }}
                  </button>
                </footer>
              </section>
            </div>
          </Transition>
        </Teleport>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import JobLogo from '../components/jobs/JobLogo.vue'
import { getJobList, getModules, getSkillTags, startInterview, updateResumeTags, uploadResumeFile } from '../api'

/* ------------------------------------------------------------------ */
/*  State                                                              */
/* ------------------------------------------------------------------ */
const router = useRouter()
const currentStep = ref(0)
const searchQuery = ref('')
const activeFamily = ref('BE')
const selectedJob = ref(null)
const isDragging = ref(false)
const uploadedFile = ref(null)
const fileInput = ref(null)
const extractedSkills = ref([])
const allTags = ref([])
const tagsLoading = ref(false)
const tagsError = ref('')
const showTagDialog = ref(false)
const allModules = ref([])
const modulesLoading = ref(false)
const modulesError = ref('')
const selectedModuleCodes = ref(new Set())
const moduleOrder = ref([])
const moduleLevels = ref({})
const draggedModuleCode = ref('')
const dragOverModuleCode = ref('')
const sortAnnouncement = ref('')
const startingInterview = ref(false)
const startError = ref('')
const durationSeconds = ref(1800)
const draftDuration = ref({ hours: 0, minutes: 30, seconds: 0 })
const durationPickerOpen = ref(false)
const durationDialog = ref(null)
const wheelRefs = {}
const wheelScrollTimers = {}

const MIN_DURATION_SECONDS = 5 * 60
const MAX_DURATION_SECONDS = 2 * 60 * 60
const WHEEL_ITEM_HEIGHT = 52
const durationPresets = [15, 30, 45, 60]
const durationUnits = [
  { key: 'hours', label: '时', values: [0, 1, 2] },
  { key: 'minutes', label: '分', values: Array.from({ length: 60 }, (_, i) => i) },
  { key: 'seconds', label: '秒', values: Array.from({ length: 60 }, (_, i) => i) },
]

const jobsLoading = ref(false)
const jobsError = ref('')
const resumeUploading = ref(false)
const resumeError = ref('')

const stepsInfo = ['选择岗位', '上传简历', '训练目标', '确认信息']
const moduleWeights = [30, 25, 20, 15, 10]
const moduleLevelOptions = [
  { value: 1, label: '基础', hint: '关注概念理解与基本应用' },
  { value: 2, label: '进阶', hint: '关注方案设计与项目实践' },
  { value: 3, label: '挑战', hint: '关注复杂场景与技术取舍' },
]

function isStepDone(index) {
  if (index === 0) return currentStep.value > 0 && Boolean(selectedJob.value)
  if (index === 1) return currentStep.value > 1 && Boolean(uploadedFile.value)
  if (index === 2) return currentStep.value > 2 && selectedModuleCodes.value.size === 5
  return false
}

function isStepSkipped(index) {
  return index === 1 && currentStep.value > 1 && !uploadedFile.value
}

/* ------------------------------------------------------------------ */
/*  Job data - loaded from API                                         */
/* ------------------------------------------------------------------ */
const jobs = ref([])

const familyDefinitions = [
  { code: 'ALL', name: '全部岗位', iconPath: 'M4 7h16M4 12h16M4 17h16' },
  { code: 'BE', name: '后端开发', iconPath: 'M5 5h14v5H5zM5 14h14v5H5zM8 7.5h.1M8 16.5h.1' },
  { code: 'FE', name: '前端与客户端', iconPath: 'M4 5h16v12H4zM8 21h8M12 17v4' },
  { code: 'FS', name: '全栈开发', iconPath: 'm12 3 8 4-8 4-8-4zM4 12l8 4 8-4M4 17l8 4 8-4' },
  { code: 'ALG', name: '算法与人工智能', iconPath: 'M9 4a3 3 0 0 0-3 3v2a3 3 0 0 0-2 3 3 3 0 0 0 2 3v2a3 3 0 0 0 3 3M15 4a3 3 0 0 1 3 3v2a3 3 0 0 1 2 3 3 3 0 0 1-2 3v2a3 3 0 0 1-3 3M9 4v16M15 4v16M9 9h6M9 15h6' },
  { code: 'PM', name: '产品经理', iconPath: 'M5 5h14v14H5zM8 9h8M8 13h5M8 17h3' },
  { code: 'DA', name: '数据分析', iconPath: 'M5 19V9M10 19V5M15 19v-7M20 19V3' },
  { code: 'QA', name: '软件测试', iconPath: 'M12 3 20 6v6c0 5-3 8-8 10-5-2-8-5-8-10V6zM8 12l3 3 5-6' },
]

function mapJobFromBackend(job) {
  const familyCode = resolveFamilyCode(job)
  return {
    id: job.id,
    title: job.name,
    tags: parseJsonField(job.keywords),
    focus: parseJsonField(job.abilities),
    category: job.category || '',
    familyCode,
    familyName: job.family || job.category || familyDefinitions.find(item => item.code === familyCode)?.name || '综合岗位',
    directionCode: job.code || job.directionCode || '',
    summary: job.description || job.directionSummary || '围绕岗位核心能力开展结构化模拟面试。',
    evidence: job.evidenceSummary || `重点结合${parseJsonField(job.keywords).slice(0, 3).join('、') || '项目经历与岗位技能'}进行判断。`,
    iconKey: resolveIconKey(job.code) || job.iconKey || 'qa-sdet',
    themeKey: job.themeKey || resolveThemeKey(familyCode),
  }
}

function resolveFamilyCode(job) {
  const prefix = String(job.code || '').split('-')[0].toUpperCase()
  if (familyDefinitions.some(item => item.code === prefix)) return prefix
  const family = String(job.family || job.category || '')
  return familyDefinitions.find(item => family.includes(item.name.replace('与客户端', '')))?.code || 'ALL'
}

function resolveIconKey(code = '') {
  return ({
    'BE-JAVA': 'openjdk', 'BE-PY': 'python', 'BE-GO': 'go', 'BE-NODE': 'nodejs', 'BE-CPP': 'chip-speed',
    'FE-WEB': 'web', 'FE-ANDROID': 'android', 'FE-IOS': 'apple', 'FE-CROSS': 'flutter', 'FE-MINI': 'wechat', 'FE-DESKTOP': 'electron',
    'FS-JAVA': 'openjdk', 'FS-NODE': 'nodejs', 'FS-PY': 'python', 'FS-AI': 'ai-app',
    'ALG-ML': 'machine-learning', 'ALG-NLP': 'nlp', 'ALG-CV': 'computer-vision', 'ALG-REC': 'recommendation',
    'ALG-SPEECH': 'speech', 'ALG-MM': 'multimodal', 'ALG-MLOPS': 'mlops',
    'PM-C': 'product-consumer', 'PM-B': 'product-enterprise', 'PM-AI': 'product-ai',
    'DA-BIZ': 'data-business', 'DA-PROD': 'data-product', 'DA-BI': 'data-bi',
    'QA-FUNC': 'qa-functional', 'QA-AUTO': 'qa-automation', 'QA-PERF': 'qa-performance', 'QA-SDET': 'qa-sdet',
  })[String(code).toUpperCase()] || ''
}

function resolveThemeKey(familyCode) {
  return ({ BE: 'amber', FE: 'sky', FS: 'violet', ALG: 'indigo', PM: 'rose', DA: 'cyan', QA: 'jade' })[familyCode] || 'jade'
}

function parseJsonField(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try { return JSON.parse(raw) } catch { return [raw] }
  }
  return []
}

async function fetchJobs() {
  jobsLoading.value = true
  jobsError.value = ''
  try {
    const data = await getJobList()
    jobs.value = (Array.isArray(data) ? data : []).map(mapJobFromBackend)
    if (!selectedJob.value && jobs.value.length) {
      selectedJob.value = jobs.value.find(job => job.familyCode === activeFamily.value) || jobs.value[0]
    }
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
const filteredJobs = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  return jobs.value.filter(j => {
    const matchSearch =
      !query ||
      [j.title, j.summary, j.directionCode, ...j.tags, ...j.focus]
        .some(value => String(value).toLowerCase().includes(query))
    const matchFamily = query || activeFamily.value === 'ALL' || j.familyCode === activeFamily.value
    return matchSearch && matchFamily
  })
})

const familyOptions = computed(() =>
  familyDefinitions.map(family => ({
    ...family,
    count: family.code === 'ALL'
      ? jobs.value.length
      : jobs.value.filter(job => job.familyCode === family.code).length,
  }))
)

const draftDurationSeconds = computed(() =>
  draftDuration.value.hours * 3600 +
  draftDuration.value.minutes * 60 +
  draftDuration.value.seconds
)

const formattedDuration = computed(() => formatDuration(durationSeconds.value))
const formattedDraftDuration = computed(() => formatDuration(draftDurationSeconds.value))
const sortedSelectedModules = computed(() => moduleOrder.value
  .map(code => allModules.value.find(module => module.code === code))
  .filter(Boolean))

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */
function selectFamily(code) {
  activeFamily.value = code
  searchQuery.value = ''
  selectedJob.value = jobs.value.find(job => code === 'ALL' || job.familyCode === code) || null
}

function formatDuration(totalSeconds) {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  const parts = []
  if (hours) parts.push(`${hours} 小时`)
  if (minutes) parts.push(`${minutes} 分钟`)
  if (seconds) parts.push(`${seconds} 秒`)
  return parts.join(' ') || '0 秒'
}

function splitDuration(totalSeconds) {
  return {
    hours: Math.floor(totalSeconds / 3600),
    minutes: Math.floor((totalSeconds % 3600) / 60),
    seconds: totalSeconds % 60,
  }
}

function setWheelRef(key, el) {
  if (el) wheelRefs[key] = el
}

function syncWheel(key, behavior = 'smooth') {
  const el = wheelRefs[key]
  if (!el) return
  el.scrollTo({ top: draftDuration.value[key] * WHEEL_ITEM_HEIGHT, behavior })
}

function syncAllWheels(behavior = 'smooth') {
  durationUnits.forEach(unit => syncWheel(unit.key, behavior))
}

function setDurationUnit(key, value) {
  draftDuration.value = { ...draftDuration.value, [key]: value }
  syncWheel(key)
}

function nudgeDuration(key, direction) {
  const unit = durationUnits.find(item => item.key === key)
  const next = Math.min(unit.values.length - 1, Math.max(0, draftDuration.value[key] + direction))
  setDurationUnit(key, next)
}

function handleWheelScroll(key) {
  clearTimeout(wheelScrollTimers[key])
  wheelScrollTimers[key] = setTimeout(() => {
    const unit = durationUnits.find(item => item.key === key)
    const index = Math.min(unit.values.length - 1, Math.max(0, Math.round(wheelRefs[key].scrollTop / WHEEL_ITEM_HEIGHT)))
    draftDuration.value = { ...draftDuration.value, [key]: unit.values[index] }
    syncWheel(key)
  }, 90)
}

function selectPreset(minutes) {
  draftDuration.value = splitDuration(minutes * 60)
  nextTick(() => syncAllWheels())
}

function openDurationPicker() {
  draftDuration.value = splitDuration(durationSeconds.value)
  durationPickerOpen.value = true
  nextTick(() => {
    syncAllWheels('auto')
    durationDialog.value?.focus()
  })
}

function closeDurationPicker() {
  durationPickerOpen.value = false
}

function confirmDuration() {
  const clamped = Math.min(MAX_DURATION_SECONDS, Math.max(MIN_DURATION_SECONDS, draftDurationSeconds.value))
  durationSeconds.value = clamped
  draftDuration.value = splitDuration(clamped)
  closeDurationPicker()
}

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

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function toggleModule(code) {
  const next = new Set(selectedModuleCodes.value)
  if (next.has(code)) {
    next.delete(code)
    moduleOrder.value = moduleOrder.value.filter(item => item !== code)
  } else if (next.size < 5) {
    next.add(code)
    moduleOrder.value.push(code)
    moduleLevels.value = { ...moduleLevels.value, [code]: moduleLevels.value[code] || 2 }
  }
  selectedModuleCodes.value = next
}

function removeSkill(index) {
  extractedSkills.value.splice(index, 1)
  updateResumeTags(extractedSkills.value).catch((error) => {
    console.error('Failed to sync resume tags:', error)
    resumeError.value = '技能标签保存失败，请重试'
  })
}

async function fetchSkillTags() {
  tagsLoading.value = true
  tagsError.value = ''
  try {
    const data = await getSkillTags()
    allTags.value = Array.isArray(data) ? data.filter(tag => tag?.name) : []
  } catch (error) {
    console.error('Failed to load skill tags:', error)
    tagsError.value = '技能标签加载失败，请重试'
  } finally {
    tagsLoading.value = false
  }
}

function openTagDialog() {
  showTagDialog.value = true
  if (!allTags.value.length && !tagsLoading.value) fetchSkillTags()
}

function closeTagDialog() {
  showTagDialog.value = false
}

function tagChecked(name) {
  return extractedSkills.value.includes(name)
}

function toggleTag(name) {
  if (!name) return
  const next = [...extractedSkills.value]
  const index = next.indexOf(name)
  if (index >= 0) next.splice(index, 1)
  else next.push(name)
  extractedSkills.value = next
  updateResumeTags(next).catch((error) => {
    console.error('Failed to sync resume tags:', error)
    resumeError.value = '技能标签保存失败，请重试'
  })
}

function moveModule(index, direction) {
  const target = index + direction
  if (target < 0 || target >= moduleOrder.value.length) return
  const next = [...moduleOrder.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  moduleOrder.value = next
}

function startModuleDrag(code, event) {
  draggedModuleCode.value = code
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', code)
  const card = event.currentTarget.closest('li')
  if (card) event.dataTransfer.setDragImage(card, 20, 28)
}

function setModuleDropTarget(targetCode) {
  if (!draggedModuleCode.value || draggedModuleCode.value === targetCode) return
  dragOverModuleCode.value = targetCode
}

function dropModule(targetCode) {
  const sourceCode = draggedModuleCode.value
  if (!sourceCode || sourceCode === targetCode) return
  const next = [...moduleOrder.value]
  const sourceIndex = next.indexOf(sourceCode)
  const targetIndex = next.indexOf(targetCode)
  if (sourceIndex < 0 || targetIndex < 0) return
  next.splice(sourceIndex, 1)
  next.splice(targetIndex, 0, sourceCode)
  moduleOrder.value = next
  sortAnnouncement.value = `已调整到第 ${targetIndex + 1} 位`
  finishModuleDrag()
}

function finishModuleDrag() {
  if (draggedModuleCode.value) {
    const index = moduleOrder.value.indexOf(draggedModuleCode.value)
    sortAnnouncement.value = `已调整到第 ${index + 1} 位`
  }
  draggedModuleCode.value = ''
  dragOverModuleCode.value = ''
}

function handleModuleSortKey(index, moduleName, event) {
  if (!event.altKey || !['ArrowUp', 'ArrowDown'].includes(event.key)) return
  event.preventDefault()
  const direction = event.key === 'ArrowUp' ? -1 : 1
  const target = index + direction
  if (target < 0 || target >= moduleOrder.value.length) return
  moveModule(index, direction)
  sortAnnouncement.value = `${moduleName}已移动到第 ${target + 1} 位`
}

function buildModulePreferences() {
  return moduleOrder.value.map((code, index) => ({ code, rank: index + 1, level: moduleLevels.value[code] || 2 }))
}

async function fetchModules() {
  modulesLoading.value = true
  modulesError.value = ''
  try {
    const data = await getModules()
    allModules.value = Array.isArray(data) ? data : []
    if (!selectedModuleCodes.value.size) {
      const defaults = allModules.value.slice(0, 5).map(module => module.code)
      selectedModuleCodes.value = new Set(defaults)
      moduleOrder.value = defaults
      moduleLevels.value = Object.fromEntries(defaults.map(code => [code, 2]))
    }
  } catch (error) {
    console.error('Failed to load modules:', error)
    modulesError.value = '能力维度加载失败，请重试'
  } finally {
    modulesLoading.value = false
  }
}

async function handleStartInterview() {
  if (!selectedJob.value || startingInterview.value) return
  startingInterview.value = true
  startError.value = ''
  try {
    const res = await startInterview({
      jobId: selectedJob.value.id,
      durationSeconds: durationSeconds.value,
      modulePreferences: buildModulePreferences(),
    })
    router.push({
      path: '/interview',
      query: {
        sessionId: String(res.sessionId),
        jobId: String(selectedJob.value.id),
        jobName: res.jobName || selectedJob.value.title,
        durationSeconds: String(res.durationSeconds || durationSeconds.value),
        questionId: res.question?.id ? String(res.question.id) : undefined,
        question: res.question?.content || undefined,
        questionType: res.question?.type || undefined,
        questionDifficulty: res.question?.difficulty || undefined,
        questionSkill: res.question?.abilityTag || undefined,
      },
    })
  } catch (error) {
    console.error('Failed to start interview:', error)
    startError.value = '面试创建失败，请检查网络后重试'
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
onMounted(() => {
  scheduleObserve()
  fetchJobs()
  fetchModules()
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

/* ===================================================================
   STEP INDICATOR (Stepper)
   =================================================================== */
.stepper {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: var(--space-10);
  position: relative;
  margin-bottom: var(--space-10);
  padding: var(--space-4) 0;
}

.stepper__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  min-width: 92px;
  z-index: 1;
}

/* Training goals */
.module-layout { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(320px, .75fr); gap: var(--space-6); align-items: start; }
.module-select, .module-priority { padding: var(--space-6); }
.module-heading { display: flex; justify-content: space-between; gap: var(--space-4); align-items: flex-start; margin-bottom: var(--space-5); }
.module-count { flex: none; display: grid; place-items: center; min-width: 52px; height: 32px; padding: 0 10px; border-radius: var(--radius-full); background: var(--neutral-100); color: var(--neutral-500); font-size: var(--text-sm); font-weight: 700; }
.module-count.is-ready { background: var(--accent-100); color: var(--accent-700); }
.module-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-3); }
.module-option { position: relative; min-height: 106px; padding: var(--space-4); border: 1px solid var(--neutral-200); border-radius: var(--radius-lg); background: rgba(255,255,255,.74); cursor: pointer; transition: border-color .2s ease, background .2s ease, transform .2s ease; }
.module-option:hover { border-color: var(--accent-300); transform: translateY(-1px); }
.module-option.is-selected { border-color: var(--accent-400); background: linear-gradient(145deg, rgba(236,253,245,.95), rgba(255,255,255,.92)); box-shadow: 0 8px 24px rgba(5, 72, 57, .07); }
.module-option.is-disabled { opacity: .48; cursor: not-allowed; transform: none; }
.module-option input { position: absolute; opacity: 0; pointer-events: none; }
.module-option__check { position: absolute; right: 12px; top: 12px; width: 22px; height: 22px; display: grid; place-items: center; border-radius: 50%; background: var(--neutral-100); color: transparent; font-size: 12px; }
.module-option.is-selected .module-option__check { background: var(--accent-600); color: white; }
.module-option strong, .module-option small { display: block; padding-right: 28px; }
.module-option strong { color: var(--neutral-900); font-size: var(--text-sm); }
.module-option small { margin-top: 8px; color: var(--neutral-500); line-height: 1.55; }
.module-priority.card {
  position: sticky;
  top: calc(var(--nav-height) + var(--space-6));
  overflow: hidden;
  border-color: #c9ddd4;
  background: linear-gradient(155deg, #edf6f1 0%, #e2f0e9 100%);
  color: #173f34;
  box-shadow: 0 18px 44px rgba(29, 78, 62, .09);
}
.module-priority::after { content: ''; position: absolute; width: 220px; height: 220px; right: -100px; top: -110px; border-radius: 50%; background: rgba(83, 157, 126, .13); filter: blur(8px); pointer-events: none; }
.module-priority__eyebrow { color: #397b65 !important; font-size: 11px !important; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
.module-priority h3 { position: relative; margin: 7px 0 8px; color: #173f34; font-size: var(--text-lg); font-weight: 650; }
.module-priority > p:not(.module-priority__eyebrow) { max-width: 42ch; color: #567267; font-size: var(--text-sm); line-height: 1.6; }
.module-priority__empty { margin-top: var(--space-6); padding: var(--space-8); border: 1px dashed #b3cec2; border-radius: var(--radius-lg); background: rgba(255,255,255,.36); color: #688279; text-align: center; }
.priority-list { position: relative; display: grid; gap: 11px; margin: var(--space-5) 0 0; padding: 0; list-style: none; }
.priority-list li {
  position: relative;
  display: grid;
  grid-template-columns: 24px 44px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #c9ddd4;
  border-radius: 14px;
  background: rgba(255,255,255,.82);
  box-shadow: 0 7px 18px rgba(29, 78, 62, .055);
  transition: border-color .2s ease, box-shadow .28s cubic-bezier(.2,.8,.2,1), transform .28s cubic-bezier(.2,.8,.2,1), opacity .2s ease;
}
.priority-list li:hover { border-color: #a8c9ba; box-shadow: 0 10px 24px rgba(29, 78, 62, .09); }
.priority-list li.is-dragging { z-index: 3; opacity: .68; transform: scale(1.018) rotate(.25deg); box-shadow: 0 20px 40px rgba(29, 78, 62, .2); }
.priority-list li.is-drop-target::before { content: ''; position: absolute; inset-inline: 10px; top: -7px; height: 3px; border-radius: 3px; background: #38866b; box-shadow: 0 2px 8px rgba(56,134,107,.3); }
.priority-index { display: grid; min-height: 42px; place-items: center; align-content: center; border-radius: 11px; background: #e2f1e9; color: #24684f; font-size: 18px; font-weight: 700; line-height: 1; }
.priority-index small { margin-bottom: 4px; color: #6d8d80; font-size: 8px; font-weight: 600; }
.priority-info { min-width: 0; }
.priority-info strong, .priority-info small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.priority-info strong { color: #183f34; font-size: 14px; font-weight: 650; }
.priority-info small { margin-top: 5px; color: #668078; font-size: 11px; }
.priority-info b { color: #286f57; font-weight: 750; }
.level-picker { grid-column: 2 / -1; display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 5px; padding: 4px; border: 1px solid #d3e4dc; border-radius: 12px; background: #f3f8f5; }
.level-picker button { min-width: 0; padding: 8px 7px 7px; border: 0; border-radius: 9px; background: transparent; color: #527066; text-align: left; cursor: pointer; transition: background .18s ease, color .18s ease, box-shadow .18s ease, transform .18s ease; }
.level-picker button:hover { background: rgba(255,255,255,.72); color: #245a48; }
.level-picker button:focus-visible { outline: 2px solid #3f8d70; outline-offset: 2px; }
.level-picker button.is-active { background: #fff; color: #1e5c47; box-shadow: 0 3px 10px rgba(38, 98, 75, .12); transform: translateY(-1px); }
.level-picker strong, .level-picker small { display: block; }
.level-picker strong { font-size: 12px; font-weight: 700; }
.level-picker small { overflow: hidden; margin-top: 3px; color: #789087; font-size: 9px; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }
.level-picker button.is-active small { color: #57786c; }
.drag-handle { display: grid; grid-template-columns: repeat(2, 4px); grid-auto-rows: 4px; gap: 3px; width: 24px; height: 38px; place-content: center; padding: 0; border: 0; border-radius: 8px; background: transparent; cursor: grab; touch-action: none; }
.drag-handle:hover { background: #e4f0ea; }
.drag-handle:active { cursor: grabbing; }
.drag-handle:focus-visible { outline: 2px solid #3f8d70; outline-offset: 2px; }
.drag-handle span { width: 4px; height: 4px; border-radius: 50%; background: #6f9184; }
.sr-only { position: absolute !important; width: 1px !important; height: 1px !important; padding: 0 !important; margin: -1px !important; overflow: hidden !important; clip: rect(0, 0, 0, 0) !important; white-space: nowrap !important; border: 0 !important; }
.priority-move { transition: transform .34s cubic-bezier(.2,.8,.2,1); }
.priority-enter-active, .priority-leave-active { transition: opacity .2s ease, transform .24s cubic-bezier(.2,.8,.2,1); }
.priority-enter-from, .priority-leave-to { opacity: 0; transform: translateY(8px) scale(.98); }
.priority-leave-active { position: absolute; width: 100%; }
.module-tags-inline { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 5px; }
.module-tags-inline span { padding: 3px 8px; border-radius: var(--radius-full); background: var(--accent-50); color: var(--accent-700); font-size: 11px; }
.detail-row--modules { align-items: flex-start; }
.start-error { margin-top: var(--space-3); color: var(--danger-600, #b42318); font-size: var(--text-sm); text-align: right; }

@media (max-width: 960px) { .module-layout { grid-template-columns: 1fr; } .module-priority { position: static; } }
@media (max-width: 640px) { .module-grid { grid-template-columns: 1fr; } .level-picker small { white-space: normal; } }

@media (prefers-reduced-motion: reduce) {
  .priority-list li,
  .priority-move,
  .priority-enter-active,
  .priority-leave-active { transition: none; }
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

.stepper__text-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stepper__item--skipped .stepper__dot {
  border-color: #d89a2b;
  background: #fff8e8;
  color: #9a6715;
}

.stepper__item--skipped .stepper__label {
  color: #79531a;
}

.stepper__status {
  color: #9a6715;
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
}

/* Track */
.stepper__track {
  position: absolute;
  top: 34px;
  left: 22%;
  right: 22%;
  height: 2px;
  background: var(--neutral-200);
  border-radius: 1px;
  transform: translateY(-50%);
  z-index: 0;
}

/* ===================================================================
   DURATION PICKER
   =================================================================== */
.duration-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 38px;
  padding: 0 var(--space-3);
  border: 1px solid color-mix(in srgb, var(--accent-500) 28%, var(--neutral-200));
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--accent-500) 5%, var(--surface-elevated));
  color: var(--accent-700);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
  transition: border-color var(--duration-fast), background var(--duration-fast), box-shadow var(--duration-fast);
}

.duration-trigger:hover {
  border-color: var(--accent-500);
  background: color-mix(in srgb, var(--accent-500) 9%, var(--surface-elevated));
}

.duration-trigger:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--accent-500) 25%, transparent);
  outline-offset: 2px;
}

.duration-modal {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(8, 22, 17, 0.48);
  backdrop-filter: blur(8px);
}

.duration-dialog {
  width: min(520px, 100%);
  padding: 28px;
  border-radius: 24px;
  background: var(--surface-elevated);
  color: var(--neutral-900);
  box-shadow: 0 28px 70px rgba(9, 38, 28, 0.22);
  outline: none;
}

.duration-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.duration-dialog__eyebrow {
  margin: 0 0 5px;
  color: var(--accent-700);
  font-size: var(--text-sm);
  font-weight: 650;
}

.duration-dialog__header h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3vw, 1.7rem);
  letter-spacing: -0.025em;
}

.duration-dialog__close {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--neutral-100);
  color: var(--neutral-600);
  cursor: pointer;
}

.duration-dialog__close:hover {
  background: var(--neutral-200);
  color: var(--neutral-900);
}

.duration-presets {
  display: flex;
  gap: 8px;
  margin: 24px 0 18px;
  overflow-x: auto;
  scrollbar-width: none;
}

.duration-presets::-webkit-scrollbar {
  display: none;
}

.duration-presets button {
  flex: 1 0 auto;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--neutral-200);
  border-radius: 999px;
  background: transparent;
  color: var(--neutral-600);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
  transition: color var(--duration-fast), border-color var(--duration-fast), background var(--duration-fast);
}

.duration-presets button:hover,
.duration-presets .duration-preset--active {
  border-color: color-mix(in srgb, var(--accent-500) 45%, transparent);
  background: color-mix(in srgb, var(--accent-500) 9%, transparent);
  color: var(--accent-700);
}

.duration-wheel {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 260px;
  overflow: hidden;
  border-radius: 18px;
  background: color-mix(in srgb, var(--accent-500) 3%, var(--neutral-50));
}

.duration-wheel__column-wrap {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
}

.duration-wheel__column-wrap > span {
  width: 22px;
  color: var(--neutral-500);
  font-size: var(--text-sm);
}

.duration-wheel__column {
  width: 64px;
  height: 260px;
  padding: 104px 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scroll-snap-type: y mandatory;
  scrollbar-width: none;
}

.duration-wheel__column::-webkit-scrollbar {
  display: none;
}

.duration-wheel__column button {
  display: block;
  width: 100%;
  height: 52px;
  padding: 0;
  scroll-snap-align: center;
  border: 0;
  background: transparent;
  color: var(--neutral-400);
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: color var(--duration-fast), font-size var(--duration-fast);
}

.duration-wheel__column button.is-selected {
  color: var(--neutral-900);
  font-size: 1.65rem;
  font-weight: 700;
}

.duration-wheel__column:focus-visible {
  outline: 2px solid var(--accent-500);
  outline-offset: -4px;
  border-radius: 12px;
}

.duration-wheel__highlight {
  position: absolute;
  z-index: 1;
  top: 104px;
  right: 14px;
  left: 14px;
  height: 52px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--accent-500) 10%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--accent-500) 14%, transparent);
}

.duration-wheel__fade {
  position: absolute;
  z-index: 3;
  right: 0;
  left: 0;
  height: 78px;
  pointer-events: none;
}

.duration-wheel__fade--top {
  top: 0;
  background: linear-gradient(to bottom, color-mix(in srgb, var(--accent-500) 3%, var(--neutral-50)) 20%, transparent);
}

.duration-wheel__fade--bottom {
  bottom: 0;
  background: linear-gradient(to top, color-mix(in srgb, var(--accent-500) 3%, var(--neutral-50)) 20%, transparent);
}

.duration-dialog__range {
  margin: 14px 0 0;
  color: var(--neutral-500);
  font-size: var(--text-sm);
  text-align: center;
}

.duration-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.duration-modal-enter-active,
.duration-modal-leave-active {
  transition: opacity 180ms ease;
}

.duration-modal-enter-active .duration-dialog,
.duration-modal-leave-active .duration-dialog {
  transition: transform 280ms var(--ease-out-expo), opacity 180ms ease;
}

.duration-modal-enter-from,
.duration-modal-leave-to {
  opacity: 0;
}

.duration-modal-enter-from .duration-dialog,
.duration-modal-leave-to .duration-dialog {
  opacity: 0;
  transform: translateY(18px) scale(0.98);
}

@media (max-width: 640px) {
  .stepper {
    gap: 8px;
  }

  .stepper__item {
    min-width: 84px;
  }

  .stepper__track {
    left: 17%;
    right: 17%;
  }

  .duration-modal {
    align-items: end;
    padding: 12px;
  }

  .duration-dialog {
    padding: 22px 18px 18px;
    border-radius: 24px;
  }

  .duration-dialog__footer .btn--primary {
    flex: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .duration-modal-enter-active,
  .duration-modal-leave-active,
  .duration-modal-enter-active .duration-dialog,
  .duration-modal-leave-active .duration-dialog {
    transition-duration: 1ms;
  }
}

.stepper__track-fill {
  height: 100%;
  background: var(--accent-500);
  border-radius: 1px;
  transform-origin: left center;
  transition: transform 0.6s var(--ease-out-expo);
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

/* Filter chips */
.filter-row {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.filter-chip {
  padding: var(--space-2) var(--space-4);
  border: 1.5px solid var(--neutral-200);
  border-radius: var(--radius-full);
  background: var(--surface-elevated);
  color: var(--neutral-600);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  font-weight: 500;
  cursor: pointer;
  transition:
    background var(--duration-fast) var(--ease-out-expo),
    border-color var(--duration-fast) var(--ease-out-expo),
    color var(--duration-fast) var(--ease-out-expo),
    box-shadow var(--duration-fast) var(--ease-out-expo);
}

.filter-chip:hover {
  border-color: var(--neutral-300);
  background: var(--neutral-50);
}

.filter-chip--active {
  border-color: var(--accent-500);
  background: var(--accent-50);
  color: var(--accent-700);
  font-weight: 600;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.08);
}

/* ===================================================================
   JOB GRID
   =================================================================== */
.job-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
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

/* Match bar */
.job-card__match {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.match-bar {
  flex: 1;
  height: 4px;
  background: var(--neutral-100);
  border-radius: 2px;
  overflow: hidden;
}

.match-bar__fill {
  height: 100%;
  border-radius: 2px;
  background: var(--accent-500);
  transform-origin: left center;
  transition: transform 0.8s var(--ease-out-expo);
}

.match-label {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  min-width: 36px;
  text-align: right;
}

.match--high {
  color: var(--accent-600);
}
.match--mid {
  color: #d97706;
}
.match--low {
  color: var(--neutral-500);
}

/* Checkmark overlay */
.job-card__check {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: var(--accent-500);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-accent);
}

.check-pop-enter-active {
  transition: all 0.3s var(--ease-spring);
}
.check-pop-leave-active {
  transition: all 0.15s ease-in;
}
.check-pop-enter-from {
  opacity: 0;
  transform: scale(0.3);
}
.check-pop-leave-to {
  opacity: 0;
  transform: scale(0.5);
}

/* Three-column job catalog */
.job-catalog {
  overflow: hidden;
  border: 1px solid var(--neutral-200);
  border-radius: 22px;
  background: var(--surface-elevated);
  box-shadow: 0 18px 50px rgba(20, 54, 42, 0.07);
}

.job-catalog__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-6) var(--space-6) var(--space-4);
}

.job-catalog__top h2 {
  margin: 0 0 6px;
  color: var(--neutral-900);
  font-family: var(--font-display);
  font-size: var(--text-xl);
  letter-spacing: -0.025em;
}

.job-catalog__top p {
  margin: 0;
  color: var(--neutral-500);
  font-size: var(--text-sm);
}

.job-catalog__count {
  flex: 0 0 auto;
  padding: 7px 12px;
  border-radius: var(--radius-full);
  background: var(--accent-50);
  color: var(--accent-700);
  font-size: var(--text-xs);
  font-weight: 650;
}

.job-catalog > .search-box {
  margin: 0 var(--space-6) var(--space-5);
}

.search-box__clear {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--neutral-100);
  color: var(--neutral-500);
  cursor: pointer;
}

.job-catalog__layout {
  display: grid;
  grid-template-columns: 190px minmax(360px, 1fr) minmax(280px, 0.78fr);
  min-height: 570px;
  border-top: 1px solid var(--neutral-200);
}

.family-nav {
  padding: var(--space-4) var(--space-3);
  border-right: 1px solid var(--neutral-200);
  background: #f7faf8;
}

.family-nav button {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 8px 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--neutral-600);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-fast), color var(--duration-fast);
}

.family-nav button + button {
  margin-top: 4px;
}

.family-nav button:hover {
  background: rgba(255, 255, 255, 0.75);
  color: var(--neutral-900);
}

.family-nav button.is-active {
  background: #fff;
  color: var(--accent-700);
  box-shadow: 0 5px 18px rgba(25, 67, 51, 0.08);
}

.family-nav__icon {
  display: grid;
  place-items: center;
}

.family-nav__text {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.family-nav__count {
  color: var(--neutral-400);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.job-list-panel {
  min-width: 0;
  border-right: 1px solid var(--neutral-200);
  background: #fff;
}

.job-list {
  height: 570px;
  padding: var(--space-3);
  overflow-y: auto;
  scrollbar-color: var(--neutral-300) transparent;
}

.job-list__item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: var(--space-3);
  padding: 14px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--duration-fast), border-color var(--duration-fast), box-shadow var(--duration-fast);
}

.job-list__item + .job-list__item {
  margin-top: 6px;
}

.job-list__item:hover {
  background: #f8fbf9;
}

.job-list__item.is-selected {
  border-color: color-mix(in srgb, var(--accent-500) 28%, transparent);
  background: color-mix(in srgb, var(--accent-500) 6%, #fff);
  box-shadow: 0 8px 22px rgba(22, 83, 61, 0.07);
}

.job-list__content {
  min-width: 0;
  flex: 1;
}

.job-list__heading {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.job-list__heading strong {
  overflow: hidden;
  color: var(--neutral-900);
  font-size: var(--text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-list__heading > span {
  flex: 0 0 auto;
  color: var(--neutral-400);
  font-size: 10px;
  font-weight: 700;
}

.job-list__summary {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: var(--neutral-500);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.job-list__tags {
  display: flex;
  gap: 5px;
  margin-top: 8px;
  overflow: hidden;
}

.job-list__tags span {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 6px;
  background: var(--neutral-100);
  color: var(--neutral-600);
  font-size: 10px;
}

.job-list__arrow {
  flex: 0 0 auto;
  color: var(--neutral-300);
}

.job-list__item.is-selected .job-list__arrow {
  color: var(--accent-600);
}

.job-detail {
  align-self: start;
  min-width: 0;
  padding: var(--space-6);
  background: linear-gradient(180deg, #fbfdfc 0%, #f4f9f6 100%);
}

.job-detail__identity {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.job-detail__identity > div {
  min-width: 0;
}

.job-detail__identity span {
  color: var(--accent-700);
  font-size: 11px;
  font-weight: 650;
}

.job-detail__identity h3 {
  margin: 4px 0 0;
  color: var(--neutral-900);
  font-family: var(--font-display);
  font-size: var(--text-lg);
  line-height: 1.3;
}

.job-detail__summary {
  margin: var(--space-5) 0 0;
  color: var(--neutral-600);
  font-size: var(--text-sm);
  line-height: 1.75;
}

.job-detail__section {
  margin-top: var(--space-6);
}

.job-detail__section h4 {
  margin: 0 0 var(--space-3);
  color: var(--neutral-800);
  font-size: var(--text-sm);
}

.job-detail__section p {
  margin: 0;
  color: var(--neutral-500);
  font-size: 12px;
  line-height: 1.75;
}

.job-detail__abilities {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.job-detail__abilities span {
  padding: 6px 9px;
  border: 1px solid color-mix(in srgb, var(--accent-500) 14%, var(--neutral-200));
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--neutral-700);
  font-size: 11px;
}

.job-detail__selected {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 1px solid var(--neutral-200);
  color: var(--accent-700);
  font-size: var(--text-sm);
  font-weight: 650;
}

.job-detail--empty {
  display: grid;
  place-items: center;
  align-self: stretch;
  color: var(--neutral-400);
  text-align: center;
}

.job-detail--empty p {
  max-width: 20ch;
  line-height: 1.65;
}

.job-detail__empty-icon {
  display: grid;
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  place-items: center;
  border-radius: 20px;
  background: #fff;
}

.catalog-state {
  display: grid;
  height: 570px;
  place-content: center;
  justify-items: center;
  color: var(--neutral-500);
  text-align: center;
}

.catalog-state button {
  border: 0;
  background: transparent;
  color: var(--accent-700);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.catalog-state__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--neutral-200);
  border-top-color: var(--accent-500);
  border-radius: 50%;
  animation: catalog-spin 700ms linear infinite;
}

@keyframes catalog-spin {
  to { transform: rotate(360deg); }
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
  gap: 2px;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-size: var(--text-sm);
  font-family: var(--font-mono);
  animation: skill-pop 0.3s var(--ease-out-expo) backwards;
}

.extracted-skills__empty {
  margin: 0;
  color: var(--neutral-500);
  font-size: var(--text-sm);
}

.skill-pill__remove {
  display: inline-grid;
  place-items: center;
  width: 18px;
  height: 18px;
  margin-inline-end: -4px;
  padding: 0;
  color: var(--accent-400);
  font-size: 14px;
  line-height: 1;
  background: transparent;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  transition: color var(--duration-fast), background var(--duration-fast);
}

.skill-pill__remove:hover {
  color: var(--accent-700);
  background: var(--accent-200);
}

.skill-pill__remove:focus-visible {
  outline: 2px solid var(--accent-500);
  outline-offset: 2px;
}

.add-tag-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--space-3);
  padding: 7px 12px;
  border: 1px dashed var(--neutral-300);
  border-radius: 9px;
  background: transparent;
  color: var(--neutral-600);
  font: inherit;
  font-size: var(--text-sm);
  cursor: pointer;
}

.add-tag-btn:hover,
.add-tag-btn:focus-visible {
  border-color: var(--accent-400);
  background: var(--accent-50);
  color: var(--accent-700);
}

.add-tag-btn:focus-visible,
.tag-dialog button:focus-visible,
.tag-dialog__item:focus-within {
  outline: 2px solid var(--accent-500);
  outline-offset: 2px;
}

.tag-modal {
  position: fixed;
  inset: 0;
  z-index: 1300;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(8, 22, 17, 0.48);
  backdrop-filter: blur(8px);
}

.tag-dialog {
  display: flex;
  width: min(620px, 100%);
  max-height: min(680px, calc(100vh - 48px));
  flex-direction: column;
  padding: 26px;
  border-radius: 20px;
  background: var(--surface-elevated);
  box-shadow: 0 24px 64px rgba(8, 38, 27, 0.24);
}

.tag-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.tag-dialog__header h3 { margin: 0; color: var(--neutral-900); font-size: var(--text-lg); }
.tag-dialog__header p { margin: 6px 0 0; color: var(--neutral-500); font-size: var(--text-sm); line-height: 1.6; }
.tag-dialog__close { flex: 0 0 auto; width: 34px; height: 34px; border: 0; border-radius: 9px; background: var(--neutral-100); color: var(--neutral-600); font-size: 22px; cursor: pointer; }

.tag-dialog__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  margin-top: var(--space-5);
  padding: 2px;
  overflow-y: auto;
}

.tag-dialog__item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 11px;
  border: 1px solid var(--neutral-200);
  border-radius: 10px;
  color: var(--neutral-700);
  font-size: var(--text-sm);
  cursor: pointer;
}

.tag-dialog__item:hover,
.tag-dialog__item.is-checked { border-color: var(--accent-300); background: var(--accent-50); color: var(--accent-700); }
.tag-dialog__item input { width: 15px; height: 15px; accent-color: var(--accent-600); }
.tag-dialog__item small { color: var(--neutral-400); font-size: 10px; }
.tag-dialog__state { display: grid; min-height: 180px; place-content: center; gap: 12px; color: var(--neutral-500); text-align: center; }
.tag-dialog__state--error { color: #a43d35; }
.tag-dialog__state button { border: 0; background: transparent; color: var(--accent-700); font: inherit; font-weight: 650; cursor: pointer; }
.tag-dialog__footer { display: flex; align-items: center; justify-content: space-between; gap: var(--space-4); margin-top: var(--space-5); padding-top: var(--space-4); border-top: 1px solid var(--neutral-200); color: var(--neutral-500); font-size: var(--text-sm); }

.modal-enter-active,
.modal-leave-active { transition: opacity 180ms ease; }
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

.summary-card__match {
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
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
   RESPONSIVE: 1024px
   =================================================================== */
@media (max-width: 1024px) {
  .job-catalog__layout {
    grid-template-columns: 170px minmax(0, 1fr);
  }

  .job-list-panel {
    border-right: 0;
  }

  .job-detail {
    grid-column: 1 / -1;
    border-top: 1px solid var(--neutral-200);
  }

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
    display: block;
    font-size: 11px;
  }

  .stepper__track {
    left: 18%;
    right: 18%;
  }

  .job-grid {
    grid-template-columns: 1fr;
    gap: var(--space-3);
  }

  .job-catalog__top {
    display: block;
    padding: var(--space-5) var(--space-4) var(--space-3);
  }

  .job-catalog__count {
    display: inline-block;
    margin-top: var(--space-3);
  }

  .job-catalog > .search-box {
    margin: 0 var(--space-4) var(--space-4);
  }

  .job-catalog__layout {
    display: block;
  }

  .family-nav {
    display: flex;
    gap: 6px;
    padding: 10px var(--space-4);
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--neutral-200);
    scrollbar-width: none;
  }

  .family-nav::-webkit-scrollbar {
    display: none;
  }

  .family-nav button {
    display: inline-flex;
    width: auto;
    min-height: 40px;
    flex: 0 0 auto;
    gap: 7px;
  }

  .family-nav button + button {
    margin-top: 0;
  }

  .family-nav__count {
    margin-left: 2px;
  }

  .job-list,
  .catalog-state {
    height: 470px;
  }

  .job-detail {
    border-top: 1px solid var(--neutral-200);
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
