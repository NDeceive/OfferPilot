<template>
  <AppLayout>
    <main class="builder" :aria-busy="loadingWorkbench">
      <p v-if="loadingWorkbench" class="info" role="status">
        正在读取事实、简历版本与服务状态…
      </p>
      <header class="builder-head">
        <div>
          <p class="eyebrow">简历工作台</p>
          <h1>用真实经历，制作能经得起追问的简历</h1>
          <p>先核实事实，再按目标岗位组织内容；每次保存都会留下独立版本。</p>
        </div>
        <button class="quiet" type="button" @click="returnToPreparation()">
          返回面试准备
        </button>
      </header>
      <div v-if="error" class="notice notice--error" role="alert">
        {{ error }}
      </div>
      <div v-if="message" class="notice" role="status">{{ message }}</div>
      <section class="resume-ai-status" aria-label="简历 AI 状态">
        <strong
          >简历 AI ·
          {{
            resumeAiStatus?.provider === "deepseek" ? "DeepSeek" : "服务状态"
          }}</strong
        ><span>{{
          resumeAiStatus
            ? resumeAiStatus.configured
              ? `已配置 ${resumeAiStatus.model || ""} · 仅辅助整理和表达`
              : "尚未配置 · 手动编辑照常可用"
            : "状态暂不可用 · 手动编辑照常可用"
        }}</span>
        <details>
          <summary>服务说明</summary>
          <p>
            仅发送用户提供的文本和已确认事实。AI
            建议需人工核对；不会替你核实真实性。
          </p>
        </details>
      </section>
      <nav class="builder-tabs" aria-label="简历制作步骤">
        <button
          v-for="item in tabs"
          :key="item.key"
          type="button"
          :class="{ active: tab === item.key }"
          :aria-current="tab === item.key ? 'step' : undefined"
          @click="switchTab(item.key)"
        >
          <span>{{ item.number }}</span
          >{{ item.label }}
        </button>
      </nav>
      <section v-if="tab === 'claims'" class="builder-grid">
        <div class="column">
          <section class="panel source-panel">
            <h2>当前资料来源</h2>
            <div class="import-actions">
              <div class="file-source">
                <strong>{{ uploadedResumeName || "尚未上传简历" }}</strong
                ><small>{{
                  uploadedResumeName
                    ? "已解析文件 · 提取内容需要人工核对"
                    : "支持 PDF / Word，或直接手动填写真实经历"
                }}</small>
              </div>
              <input
                ref="resumeFileInput"
                type="file"
                accept=".pdf,.doc,.docx"
                hidden
                @change="uploadResumeForClaims"
              /><button
                type="button"
                class="quiet"
                :disabled="uploadingResume || loadingSuggestions || batchVerifying"
                @click="resumeFileInput?.click()"
              >
                {{
                  uploadingResume
                    ? "解析中…"
                    : uploadedResumeName
                      ? "更换简历"
                      : "上传简历"
                }}</button
              ><button
                type="button"
                class="primary"
                :disabled="
                  loadingSuggestions || uploadingResume || batchVerifying || !uploadedResumeName
                "
                @click="loadSuggestions"
              >
                {{ loadingSuggestions ? "提取中…" : "提取候选事实" }}
              </button>
            </div>
            <p v-if="suggestionsMessage" class="info" role="status">
              {{ suggestionsMessage }}
            </p>
          </section>
          <section v-if="suggestions.length" class="panel suggestion-panel">
            <div class="panel-head">
              <h2>待核对候选事实 · {{ suggestions.length }}</h2>
              <button type="button" class="quiet" :aria-expanded="suggestionsExpanded" aria-controls="candidate-facts" @click="suggestionsExpanded = !suggestionsExpanded">
                {{ suggestionsExpanded ? "收起列表" : "展开列表" }}
              </button>
            </div>
            <template v-if="suggestionsExpanded">
              <div class="suggestion-toolbar">
                <label class="suggestion-check">
                  <input type="checkbox" :checked="selectedSuggestions.length === suggestions.length" :indeterminate="selectedSuggestions.length > 0 && selectedSuggestions.length < suggestions.length" :disabled="batchVerifying" @change="selectedSuggestions = $event.target.checked ? [...suggestions] : []" />
                  全选
                </label>
                <span>已选 {{ selectedSuggestions.length }} 条</span>
                <button type="button" class="primary" :disabled="!selectedSuggestions.length || batchVerifying || loadingWorkbench" @click="verifySelectedSuggestions">
                  {{ batchVerifying ? "保存中…" : "批量核对" }}
                </button>
              </div>
              <p class="info">请核对原始事实后勾选；批量核对将保存为已确认事实。</p>
              <div id="candidate-facts" class="suggestion-list" role="region" aria-label="待核对候选事实列表" tabindex="0" :aria-busy="batchVerifying">
                <article v-for="(item, index) in suggestions" :key="item" class="suggestion">
                  <label class="suggestion-check suggestion-content">
                    <input v-model="selectedSuggestions" type="checkbox" :value="item" :disabled="batchVerifying" />
                    <span><strong>{{ categoryLabel(item.category) }} · {{ item.title || item.resumeText }}</strong><small>{{ item.factText || item.sourceExcerpt || item.resumeText }}</small></span>
                  </label>
                  <div>
                    <button type="button" class="quiet" :disabled="batchVerifying" @click="useSuggestion(item, index)">核对并编辑</button>
                    <button type="button" class="quiet" :disabled="batchVerifying" @click="discardSuggestion(item)">舍弃</button>
                  </div>
                </article>
              </div>
            </template>
          </section>
          <section class="panel">
            <form class="claim-form" @submit.prevent="saveClaim">
              <div class="panel-head">
                <div>
                  <h2>
                    {{ editingClaimId ? "编辑真实经历" : "新增真实经历" }}
                  </h2>
                  <p>先记录发生过的事情，再完善细节与表达</p>
                </div>
                <button
                  type="button"
                  class="quiet"
                  :disabled="loadingExample"
                  @click="generateExample"
                >
                  {{ loadingExample ? "生成中…" : "获取填写参考" }}
                </button>
              </div>
              <p class="info">
                所有内容以本人真实经历为准；AI 示例不会直接存入事实库。
              </p>
              <div v-if="claimExample" class="example-card" role="status">
                <strong
                  >{{
                    claimExample.source === "RULE"
                      ? "规则写法模板（AI 暂不可用）"
                      : "AI 写法示例"
                  }}
                  · {{ claimExample.title }}</strong
                >
                <p><b>事例：</b>{{ claimExample.scenario }}</p>
                <p><b>简历表述：</b>{{ claimExample.resumeLine }}</p>
                <small
                  >请根据自己的真实经历填写下方表单；此示例不会自动填入、保存或确认。</small
                >
              </div>
              <div class="form-row">
                <label
                  >经历类型<select v-model="claimForm.category">
                    <option
                      v-for="item in categories"
                      :key="item.value"
                      :value="item.value"
                    >
                      {{ item.label }}
                    </option>
                  </select></label
                >
                <label
                  >承担角色<select v-model="claimForm.responsibility">
                    <option value="PARTICIPATED">参与</option>
                    <option value="MODULE">负责模块</option>
                    <option value="LED">主导方案或交付</option>
                    <option value="OWNER">项目负责人</option>
                  </select></label
                >
              </div>
              <label
                >经历名称<input
                  ref="claimTitleInput"
                  v-model="claimForm.title"
                  maxlength="120"
                  required
                  placeholder="例如：校园交易项目订单模块"
              /></label>
              <label
                >原始事实描述<textarea
                  v-model="claimForm.factText"
                  maxlength="4000"
                  rows="3"
                  required
                  placeholder="如实写做过什么，以及可核对的依据"
                />
              </label>
              <details :open="!!editingClaimId" class="more-details">
                <summary>补充更多细节 <small>推荐完善</small></summary>
                <div class="example-tools">
                  <label
                    >事例线索（选填）<input
                      v-model="exampleBrief"
                      maxlength="1000"
                      placeholder="例如：参与过校园交易项目的订单模块；不填则生成通用示例"
                  /></label>
                  <button
                    type="button"
                    class="quiet"
                    :disabled="loadingExample"
                    @click="generateExample"
                  >
                    {{ loadingExample ? "生成中…" : "获取填写参考" }}
                  </button>
                </div>
                <p class="fineprint">
                  简历 AI 使用{{
                    resumeAiProvider
                  }}；不可用时提供通用规则模板。AI
                  请求只发送当前分类、目标岗位及事例线索，不发送整份简历。示例不代表你的真实经历。
                </p>
                <label
                  >准备写进简历的表述<textarea
                    v-model="claimForm.resumeText"
                    maxlength="4000"
                    rows="3"
                    placeholder="只写能由原始事实支撑的内容"
                  />
                </label>
                <div class="ai-wording">
                  <button
                    type="button"
                    class="quiet"
                    :disabled="loadingAiWording || !claimForm.factText.trim()"
                    @click="loadAiWording"
                  >
                    {{ loadingAiWording ? "AI 起草中…" : "AI 起草简历表述" }}
                  </button>
                  <span
                    >仅发送此条的标题、原始事实、承担程度和个人边界至{{
                      resumeAiProvider
                    }}；建议不会自动保存。</span
                  >
                </div>
                <div v-if="wordingSuggestion" class="suggestions" role="status">
                  <strong>AI 建议，请核对：</strong>
                  <p>{{ wordingSuggestion }}</p>
                  <button type="button" class="quiet" @click="applyAiWording">
                    采用此表述
                  </button>
                </div>
                <label
                  >个人与团队的工作边界<textarea
                    v-model="claimForm.personalBoundary"
                    maxlength="2000"
                    rows="2"
                    placeholder="主导项目时必填：团队完成了什么，你本人完成了什么"
                  />
                </label>
                <label
                  >来源摘录<input
                    v-model="claimForm.sourceExcerpt"
                    maxlength="2000"
                    placeholder="简历原文或其他可核对的材料（选填）"
                /></label>
                <label
                  >核实状态<select v-model="claimForm.verificationStatus">
                    <option value="PENDING">待确认</option>
                    <option value="CONFIRMED">已确认</option>
                    <option value="EXPIRED">已过期</option>
                    <option value="REJECTED">不采用</option>
                  </select></label
                >
              </details>
              <div class="form-row form-row--actions">
                <span class="fineprint">{{
                  claimDirty ? "当前内容尚未保存" : "填写真实经历后保存"
                }}</span>
                <div>
                  <button
                    type="button"
                    class="quiet"
                    @click="requestClearClaim"
                  >
                    清空</button
                  ><button
                    type="submit"
                    class="primary"
                    :disabled="savingClaim || loadingWorkbench"
                  >
                    {{ savingClaim ? "保存中…" : "保存事实" }}
                  </button>
                </div>
              </div>
            </form>
          </section>
        </div>
        <aside class="column">
          <section class="panel">
            <h2>事实资料库概览</h2>
            <div class="stats">
              <div>
                <small>已确认</small><strong>{{ confirmedClaimCount }}</strong>
              </div>
              <div>
                <small>待确认</small
                ><strong>{{
                  claims.filter((c) => c.verificationStatus === "PENDING")
                    .length
                }}</strong>
              </div>
              <div>
                <small>全部事实</small><strong>{{ claims.length }}</strong>
              </div>
            </div>
          </section>
          <section class="panel ledger">
            <div class="panel-head">
              <h2>事实记录 · {{ claims.length }}</h2>
              <button
                type="button"
                class="quiet"
                :aria-expanded="ledgerExpanded"
                aria-controls="fact-records"
                @click="ledgerExpanded = !ledgerExpanded"
              >
                {{ ledgerExpanded ? "收起列表" : "展开列表" }}
              </button>
            </div>
            <div id="fact-records" v-show="ledgerExpanded" class="ledger-list" role="region" aria-label="事实记录列表" tabindex="0">
            <div v-if="!claims.length && !loadingWorkbench" class="empty-state">
              <span class="document-symbol" aria-hidden="true">▤</span>
              <h3>还没有真实经历记录</h3>
              <p>
                可以从已有简历提取候选事实，<br />或先填写一条自己真正做过的经历。
              </p>
              <button
                type="button"
                class="quiet"
                @click="claimTitleInput?.focus()"
              >
                开始填写第一条经历
              </button>
            </div>
            <article v-for="item in claims" :key="item.id" class="ledger-item">
              <div>
                <strong>{{ item.title }}</strong
                ><span
                  :class="['status', item.verificationStatus.toLowerCase()]"
                  >{{ statusLabel(item.verificationStatus) }}</span
                >
              </div>
              <p>{{ item.resumeText }}</p>
              <small
                >{{ categoryLabel(item.category) }} ·
                {{ responsibilityLabel(item.responsibility) }} ·
                {{
                  item.sourceExcerpt ? "有来源依据" : "手动记录 / 未填写来源"
                }}</small
              >
              <div class="ledger-actions">
                <button type="button" @click="editClaim(item)">
                  查看 / 编辑</button
                ><button
                  type="button"
                  :disabled="deletingClaimId === item.id"
                  @click="removeClaim(item)"
                >
                  {{ deletingClaimId === item.id ? "移除中…" : "移除" }}
                </button>
              </div>
            </article>
            </div>
          </section>
          <section class="panel">
            <h2>准备制作简历</h2>
            <p class="fineprint">
              {{
                confirmedClaimCount
                  ? "已确认的真实经历可用于后续简历编写"
                  : "可以手动制作简历；AI 不会引用未确认内容。"
              }}
            </p>
            <button
              type="button"
              class="primary full"
              @click="switchTab('editor')"
            >
              下一步：制作简历 →
            </button>
          </section>
        </aside>
      </section>
      <section v-else-if="tab === 'editor'" class="builder-grid">
        <div class="column">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>当前简历设置</h2>
                <p>选择求职方向，开始整理简历内容</p>
              </div>
              <span class="status">{{
                dirty ? "未保存草稿" : activeVersion ? "已保存版本" : "编辑草稿"
              }}</span>
            </div>
            <div class="form-row">
              <label
                >目标岗位<select v-model.number="selectedJobId">
                  <option :value="null">通用简历</option>
                  <option v-for="job in jobs" :key="job.id" :value="job.id">
                    {{ job.name }}
                  </option>
                </select></label
              ><label
                >版本名称<input v-model="versionTitle" maxlength="120"
              /></label>
            </div>
          </section>
          <form class="panel editor-panel" @submit.prevent="saveVersion">
            <div class="panel-head">
              <div>
                <h2>简历内容编辑器</h2>
                <p>按模块完善简历内容，也可以使用右侧 AI 助手优化表达。</p>
              </div>
              <small
                >第
                {{
                  editorSections.findIndex((s) => s.key === editorSection) + 1
                }}
                / 6 部分</small
              >
            </div>
            <nav class="section-tabs" aria-label="简历分区">
              <button
                v-for="section in editorSections"
                :key="section.key"
                type="button"
                :class="{ active: editorSection === section.key }"
                :aria-pressed="editorSection === section.key"
                @click="editorSection = section.key"
              >
                {{ section.label }}
              </button>
            </nav>
            <h3>
              {{ editorSections.find((s) => s.key === editorSection)?.label }}
            </h3>
            <template v-if="editorSection === 'basic'"
              ><div class="form-row">
                <label
                  >姓名<input v-model="editor.name" maxlength="100" /></label
                ><label
                  >求职意向<input v-model="editor.targetJob" maxlength="120"
                /></label>
              </div>
              <div class="form-row">
                <label
                  >电话<input
                    v-model="editor.phone"
                    maxlength="40"
                    type="tel" /></label
                ><label
                  >邮箱<input
                    v-model="editor.email"
                    maxlength="160"
                    type="email"
                /></label>
              </div>
              <label
                >个人简介<textarea
                  v-model="editor.summary"
                  rows="3"
                  placeholder="概括真实优势，不夸大业绩"
                  maxlength="2500"
                /></label
            ></template>
            <ResumeEntryEditor
              v-else-if="
                ['education', 'projects', 'experience'].includes(editorSection)
              "
              :key="editorSection"
              v-model="editor[editorSection]"
              :label="
                editorSections.find((s) => s.key === editorSection)?.label
              "
            />
            <label v-else
              >{{ editorSections.find((s) => s.key === editorSection)?.label
              }}<textarea
                v-model="editor[editorSection]"
                rows="8"
                maxlength="4000"
                placeholder="只填写可以核实的内容"
              />
            </label>
            <p class="fineprint">
              分区切换保留全部草稿。多条经历以空行分隔存入现有文本接口。
            </p>
            <div class="editor-footer">
              <span class="fineprint">{{
                dirty
                  ? "当前编辑草稿 · 修改尚未保存为正式版本"
                  : "保存创建独立快照，保留全部历史版本"
              }}</span
              ><button
                type="submit"
                class="primary"
                :disabled="savingVersion || generating || loadingWorkbench"
              >
                {{ savingVersion ? "保存中…" : "保存为新版本" }}
              </button>
            </div>
          </form>
        </div>
        <aside class="column">
          <section class="panel">
            <div class="panel-head">
              <div>
                <h2>真实事实引用</h2>
                <small>已确认 {{ confirmedClaimCount }} 条</small>
              </div>
              <button
                type="button"
                class="quiet"
                :aria-expanded="referencesExpanded"
                aria-controls="fact-references"
                @click="referencesExpanded = !referencesExpanded"
              >
                {{ referencesExpanded ? "收起列表" : "展开列表" }}
              </button>
            </div>
            <div id="fact-references" v-show="referencesExpanded" class="ledger-list" role="region" aria-label="真实事实引用列表" tabindex="0">
            <div
              v-if="!confirmedClaimCount && !loadingWorkbench"
              class="empty-state compact"
            >
              <span class="document-symbol" aria-hidden="true">▤</span>
              <h3>尚无可引用的真实经历</h3>
              <p>可返回第一阶段核实经历，<br />也可以继续手动填写简历。</p>
              <button type="button" class="quiet" @click="switchTab('claims')">
                返回整理事实 →
              </button>
            </div>
            <article
              v-for="item in confirmedClaims"
              :key="item.id"
              class="ledger-item"
            >
              <strong>{{ item.title }}</strong>
              <p>{{ item.resumeText }}</p>
              <button type="button" class="quiet" @click="referenceClaim(item)">
                追加到{{ categoryLabel(item.category) }}
              </button>
            </article>
            </div>
          </section>
          <section class="panel">
            <h2>AI表达助手</h2>
            <button type="button" class="quiet" :disabled="!activeVersion || !(activeVersion.jobId || selectedJobId)" @click="openAnalysis">分析已保存版本</button>
            <p v-if="!activeVersion" class="fineprint">保存一个版本后可分析；未保存草稿不会参与分析。</p>
            <p class="fineprint">
              根据已确认事实与用户提供的文本优化表达，建议经审核后才进入草稿。
            </p>
            <p class="info">当前可用事实：{{ confirmedClaimCount }} 条</p>
            <div class="export-actions">
              <button
                type="button"
                class="quiet"
                :disabled="generating || !currentEditorText.trim()"
                @click="optimizeContent"
              >
                {{ generating ? "请求中…" : "优化当前内容" }}</button
              ><button
                type="button"
                class="primary"
                :disabled="generating || !selectedJobId || !confirmedClaimCount"
                @click="generate"
              >
                生成初稿
              </button>
            </div>
            <details class="ai-limits">
              <summary>初稿保存与服务限制</summary>
              <p class="fineprint">
                现有初稿接口会保存一份 AI
                候选版本；当前手写草稿保持到你接受建议为止。优化接口暂不支持目标岗位参数。
              </p>
            </details>
          </section>
          <section class="panel versions">
            <div class="panel-head">
              <h2>简历版本</h2>
              <small>{{ versions.length }} 个已保存版本</small>
            </div>
            <div
              v-if="!versions.length && !loadingWorkbench"
              class="empty-state compact"
            >
              <span class="document-symbol" aria-hidden="true">▤</span>
              <h3>还没有已保存的简历</h3>
              <p>完成编辑后保存一个版本，<br />即可进入预览与导出。</p>
            </div>
            <button
              v-for="item in versions"
              :key="item.id"
              type="button"
              class="version-card"
              :class="{ active: activeVersion?.id === item.id }"
              :disabled="openingVersion || generating || savingVersion"
              @click="selectVersion(item.id)"
            >
              <strong>{{ item.title }}</strong
              ><small
                >{{
                  item.sourceKind === "AI_GENERATED"
                    ? "AI 候选 · 需核对 · "
                    : ""
                }}{{ formatDate(item.createdAt) }}</small
              ></button
            ><button
              type="button"
              class="quiet full"
              @click="switchTab('preview')"
            >
              查看预览与导出 →
            </button>
          </section>
        </aside>
      </section>
      <section v-else class="builder-grid preview-stage">
        <section class="panel preview-panel">
          <div class="panel-head">
            <div>
              <h2>A4 简历预览</h2>
              <p>预览与 PDF 下载使用同一份服务端文件。</p>
            </div>
            <div class="preview-tools">
              <button
                type="button"
                class="quiet"
                :disabled="zoom <= 50"
                aria-label="缩小预览"
                @click="zoom -= 10"
              >
                −</button
              ><span>{{ zoom }}%</span
              ><button
                type="button"
                class="quiet"
                :disabled="zoom >= 150"
                aria-label="放大预览"
                @click="zoom += 10"
              >
                +</button
              ><button
                type="button"
                class="quiet"
                :disabled="!activeVersion || previewLoading"
                @click="loadPreview"
              >
                {{ previewLoading ? "生成中…" : "刷新预览" }}
              </button>
            </div>
          </div>
          <p v-if="dirty" class="info">
            草稿尚未保存；这里预览、导出和用于面试的均为最后选定的已保存版本。
          </p>
          <p v-if="previewError" class="notice notice--error" role="alert">
            {{ previewError }}
          </p>
          <div class="document-canvas">
            <ResumePdfPreview
              v-if="previewUrl"
              :src="previewUrl"
              :zoom="zoom"
            />
            <div v-else class="a4-paper">
              <div class="empty-state">
                <span class="document-symbol" aria-hidden="true">▤</span>
                <h3>
                  {{
                    activeVersion
                      ? previewLoading
                        ? "正在生成预览…"
                        : "暂未加载预览"
                      : "还没有可预览的简历"
                  }}
                </h3>
                <p>
                  {{
                    activeVersion
                      ? "刷新预览可重试；可在预览中切换页码和缩放。"
                      : "请先在「制作简历」中编辑并保存一个版本，即可在这里查看 A4 排版效果。"
                  }}
                </p>
                <button
                  type="button"
                  class="primary"
                  @click="activeVersion ? loadPreview() : switchTab('editor')"
                  :disabled="previewLoading"
                >
                  {{ activeVersion ? "刷新预览" : "返回制作简历 →" }}
                </button>
              </div>
            </div>
          </div>
        </section>
        <aside class="column">
          <section class="panel">
            <div class="panel-head">
              <h2>当前简历版本</h2>
              <small>{{ versions.length }} 个已保存版本</small>
            </div>
            <label
              >选择已保存版本<select
                :value="activeVersion?.id || ''"
                :disabled="!versions.length || openingVersion"
                @change="selectVersion(Number($event.target.value))"
              >
                <option value="" disabled>
                  {{ versions.length ? "请选择版本" : "暂无可选择的版本" }}
                </option>
                <option
                  v-for="item in versions"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.title
                  }}{{
                    item.sourceKind === "AI_GENERATED"
                      ? "（AI 候选，请核对）"
                      : ""
                  }}
                </option>
              </select></label
            >
            <p class="info">
              {{
                activeVersion
                  ? `${formatDate(activeVersion.createdAt)} · ${activeVersion.content.targetJob || "通用简历"} · 已保存`
                  : "请先在第二阶段保存简历版本，才可以预览和导出。"
              }}
            </p>
          </section>
          <section class="panel">
            <h2>导出简历</h2>
            <button type="button" class="quiet" :disabled="!activeVersion || !(activeVersion.jobId || selectedJobId)" @click="openAnalysis">查看简历分析</button>
            <p class="fineprint">下载当前选定的已保存版本。</p>
            <div class="export-actions">
              <button
                type="button"
                class="primary"
                :disabled="!activeVersion || !!exporting || previewLoading"
                @click="download('pdf')"
              >
                {{ exporting === "pdf" ? "生成 PDF…" : "下载 PDF" }}</button
              ><button
                type="button"
                class="quiet"
                :disabled="!activeVersion || !!exporting"
                @click="download('docx')"
              >
                {{ exporting === "docx" ? "生成 DOCX…" : "下载 DOCX" }}
              </button>
            </div>
            <p class="info" role="status">
              {{
                exportMessage ||
                (activeVersion
                  ? "PDF 与预览一致；DOCX 会随本机字体略有排版差异。"
                  : "暂无可导出的简历版本")
              }}
            </p>
          </section>
          <section class="panel">
            <h2>与历史版本对比</h2>
            <label
              >历史版本<select
                v-model.number="comparisonId"
                :disabled="versions.length < 2 || !activeVersion"
              >
                <option :value="null">请选择要对比的历史版本</option>
                <option
                  v-for="item in versions.filter(
                    (v) => v.id !== activeVersion?.id,
                  )"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.title }}
                </option>
              </select></label
            >
            <p v-if="versions.length < 2" class="info">
              至少需要两个不同版本才能进行对比。
            </p>
            <p v-if="comparisonLoading" role="status">读取差异中…</p>
            <div v-for="item in differences" :key="item.key" class="comparison">
              <strong>{{ item.label }}</strong>
              <p>旧版：{{ item.before || "未填写" }}</p>
              <p>当前：{{ item.after || "未填写" }}</p>
            </div>
            <p v-if="comparison && !differences.length" class="info">
              两版内容相同
            </p>
          </section>
          <section class="panel">
            <h2>用于本次面试</h2>
            <p class="fineprint">
              将选定版本传回面试准备，继续岗位分析与训练设置。
            </p>
            <button
              type="button"
              class="quiet full"
              :disabled="!activeVersion || binding"
              @click="returnToPreparation(true)"
            >
              使用当前简历 →
            </button>
            <p v-if="!activeVersion" class="info">
              请先保存并选择一个简历版本。
            </p>
          </section>
        </aside>
      </section>
      <dialog ref="analysisDialog" class="analysis-dialog">
        <button type="button" class="quiet analysis-close" @click="analysisDialog.close()">关闭分析</button>
        <ResumeAnalysisPanel v-if="analysisOpen" :job-id="analysisJobId" :version-id="analysisVersionId" :available="Boolean(analysisVersionId)" @edit="editFromAnalysis" @recommend="goAnalysisTargets" />
      </dialog>
      <dialog ref="analysisReviewDialog" class="review-dialog">
        <template v-if="analysisEdit">
          <h2>审核表达修改建议</h2><p>{{ analysisEdit.item.reason }}</p>
          <h3>原文</h3><pre>{{ analysisEdit.item.quote }}</pre><h3>建议</h3><pre>{{ analysisEdit.item.suggestion }}</pre>
          <p class="fineprint">只调整对应草稿文本，不保存、不修改历史版本；请确认删减没有改变原意。</p>
          <p v-if="analysisEditError" role="alert">{{ analysisEditError }}</p>
          <div class="export-actions"><button type="button" class="quiet" @click="analysisReviewDialog.close()">取消</button><button type="button" class="primary" :disabled="acceptingAnalysis" @click="acceptAnalysis">{{ acceptingAnalysis ? '核对中…' : '确认并应用到草稿' }}</button></div>
        </template>
      </dialog>
      <dialog ref="leaveDialog" class="review-dialog" @cancel="cancelLeave">
        <h2>有未保存的修改</h2>
        <p>可以保存后继续、放弃当前修改，或取消并继续编辑。</p>
        <p v-if="error" class="notice notice--error" role="alert">
          {{ error }}
        </p>
        <div class="editor-actions">
          <button
            type="button"
            class="quiet"
            :disabled="savingClaim || savingVersion"
            @click="cancelLeave"
          >
            取消</button
          ><button
            type="button"
            class="quiet"
            :disabled="savingClaim || savingVersion"
            @click="finishLeave(false)"
          >
            放弃修改</button
          ><button
            type="button"
            class="primary"
            :disabled="savingClaim || savingVersion"
            @click="finishLeave(true)"
          >
            {{ savingClaim || savingVersion ? "保存中…" : "保存并继续" }}
          </button>
        </div>
      </dialog>
      <dialog
        ref="aiDialog"
        class="review-dialog"
        @cancel="aiSuggestion = null"
      >
        <h2>审核 AI 建议</h2>
        <p>核对事实、职责与结果后再接受；接受仅更新编辑草稿。</p>
        <div
          v-for="field in aiReviewFields"
          :key="field.key"
          class="comparison"
        >
          <strong>{{ field.label }}</strong>
          <div class="form-row">
            <div>
              <small>当前原文</small>
              <p>{{ editor[field.key] || "未填写" }}</p>
            </div>
            <div>
              <small>AI 建议</small>
              <p>{{ aiSuggestion?.[field.key] || "未填写" }}</p>
            </div>
          </div>
        </div>
        <div class="editor-actions">
          <button
            type="button"
            class="quiet"
            @click="
              aiSuggestion = null;
              aiDialog.close();
            "
          >
            保留原文</button
          ><button type="button" class="primary" @click="acceptAi">
            核对并接受建议
          </button>
        </div>
      </dialog>
    </main>
  </AppLayout>
</template>

<script setup>
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import ResumeAnalysisPanel from "../components/resume/ResumeAnalysisPanel.vue";
import { applyAnalysisSuggestion } from "../utils/resumeAnalysis";
import ResumePdfPreview from "../components/resume/ResumePdfPreview.vue";
import ResumeEntryEditor from "../components/resume/ResumeEntryEditor.vue";
import AppLayout from "../components/layout/AppLayout.vue";
import {
  addResumeClaim,
  deleteResumeClaim,
  exportResumeVersion,
  generateResumeVersion,
  getJobList,
  getResumeAnalysisReport,
  getResumeFileProfile,
  generateResumeClaimExample,
  getAiResumeClaimWording,
  getResumeAiStatus,
  getResumeClaimSuggestions,
  getResumeClaims,
  getResumeVersion,
  getResumeVersions,
  saveResumeVersion,
  uploadResumeFile,
  updateResumeClaim,
} from "../api";

const router = useRouter();
const route = useRoute();
const tabs = [
  { key: "claims", label: "资料与事实", number: "01" },
  { key: "editor", label: "制作简历", number: "02" },
  { key: "preview", label: "预览与导出", number: "03" },
];
const categories = [
  { value: "EDUCATION", label: "教育经历" },
  { value: "EXPERIENCE", label: "实习 / 工作" },
  { value: "PROJECT", label: "项目经历" },
  { value: "SKILL", label: "专业技能" },
  { value: "AWARD", label: "荣誉奖项" },
];
const contentFields = [
  {
    key: "summary",
    label: "个人简介",
    rows: 3,
    hint: "概括真实优势，不编造业绩",
  },
  {
    key: "education",
    label: "教育经历",
    rows: 3,
    hint: "学校、专业、时间、相关课程",
  },
  {
    key: "experience",
    label: "实习 / 工作经历",
    rows: 5,
    hint: "公司、岗位、本人职责与结果",
  },
  {
    key: "projects",
    label: "项目经历",
    rows: 6,
    hint: "项目背景、技术方案、个人贡献与可核实结果",
  },
  {
    key: "skills",
    label: "专业技能",
    rows: 3,
    hint: "真实掌握的语言、框架和工具",
  },
  { key: "awards", label: "荣誉奖项", rows: 2, hint: "选填" },
];
const emptyClaim = () => ({
  category: "PROJECT",
  title: "",
  factText: "",
  resumeText: "",
  responsibility: "PARTICIPATED",
  personalBoundary: "",
  verificationStatus: "PENDING",
  sourceExcerpt: "",
});
const emptyContent = () => ({
  name: "",
  phone: "",
  email: "",
  targetJob: "",
  summary: "",
  education: "",
  experience: "",
  projects: "",
  skills: "",
  awards: "",
});
const editorSections = [
  { key: "basic", label: "基本信息" },
  { key: "education", label: "教育背景" },
  { key: "projects", label: "项目经历" },
  { key: "experience", label: "实习 / 工作" },
  { key: "skills", label: "专业技能" },
  { key: "awards", label: "荣誉奖项" },
];
const editorSection = ref("basic");
const claimTitleInput = ref(null);
const message = ref("");
const deletingClaimId = ref(null);
const openingVersion = ref(false);
const previewLoading = ref(false);
const exporting = ref("");
const exportMessage = ref("");
const binding = ref(false);
const comparisonLoading = ref(false);
const zoom = ref(100);
const analysisDialog = ref(null);
const analysisReviewDialog = ref(null);
const analysisOpen = ref(false);
const analysisJobId = ref(null);
const analysisVersionId = ref(null);
const analysisEdit = ref(null);
const analysisEditError = ref("");
const acceptingAnalysis = ref(false);
const aiDialog = ref(null);
const aiSuggestion = ref(null);
const aiReviewFields = ref([]);
let aiSnapshot = "";
let previewBlob = null;
let previewRequest = 0;
let comparisonRequest = 0;
const claimBaseline = ref(JSON.stringify(emptyClaim()));
const claimDirty = computed(
  () =>
    JSON.stringify(
      Object.fromEntries(
        Object.keys(emptyClaim()).map((key) => [key, claimForm[key]]),
      ),
    ) !== claimBaseline.value,
);
const confirmedClaims = computed(() =>
  claims.value.filter((item) => item.verificationStatus === "CONFIRMED"),
);
const currentEditorKey = computed(() =>
  editorSection.value === "basic" ? "summary" : editorSection.value,
);
const currentEditorText = computed(() => editor[currentEditorKey.value] || "");
const leaveDialog = ref(null);
let leaveScope = "all";
let editorBaseline = null;
let leaveAction = null;
let leaveResolve = null;
let routeApproved = false;
const tab = ref("claims");
const loadingWorkbench = ref(true);
const error = ref("");
const resumeAiStatus = ref(null);
const previewError = ref("");
const previewUrl = ref("");
const jobs = ref([]);
const selectedJobId = ref(Number(route.query.jobId) || null);
const claims = ref([]);
const ledgerExpanded = ref(true);
const referencesExpanded = ref(true);
const suggestions = ref([]);
const suggestionsExpanded = ref(true);
const selectedSuggestions = ref([]);
const batchVerifying = ref(false);
watch(suggestions, () => {
  selectedSuggestions.value = selectedSuggestions.value.filter((item) => suggestions.value.includes(item));
}, { deep: true });
const suggestionsMessage = ref("");
const uploadedResumeName = ref("");
const resumeFileInput = ref(null);
const uploadingResume = ref(false);
const versions = ref([]);
const activeVersion = ref(null);
const editorVersionId = ref(null);
const comparisonId = ref(null);
const comparison = ref(null);
const dirty = ref(false);
const loadingSuggestions = ref(false);
const loadingExample = ref(false);
const exampleBrief = ref("");
const claimExample = ref(null);
const loadingAiWording = ref(false);
const wordingSuggestion = ref("");
const wordingSourceSnapshot = ref("");
const savingClaim = ref(false);
const savingVersion = ref(false);
const generating = ref(false);
const editingClaimId = ref(null);
const claimForm = reactive(emptyClaim());
const editor = reactive(emptyContent());
const versionTitle = ref("我的简历");
let loadingVersion = false;

const differences = computed(() => {
  if (!comparison.value || !activeVersion.value) return [];
  const fields = [
    { key: "name", label: "姓名" },
    { key: "phone", label: "电话" },
    { key: "email", label: "邮箱" },
    { key: "targetJob", label: "求职意向" },
    ...contentFields,
  ];
  return fields
    .filter(
      (field) =>
        comparison.value.content?.[field.key] !==
        activeVersion.value.content?.[field.key],
    )
    .map((field) => ({
      ...field,
      before: comparison.value.content?.[field.key],
      after: activeVersion.value.content?.[field.key],
    }));
});
const confirmedClaimCount = computed(
  () =>
    claims.value.filter((item) => item.verificationStatus === "CONFIRMED")
      .length,
);
const resumeAiProvider = computed(() =>
  resumeAiStatus.value?.provider === "deepseek"
    ? "DeepSeek 服务"
    : resumeAiStatus.value?.provider === "zhipu"
      ? "智谱服务"
      : "当前简历 AI 服务",
);
watch(comparisonId, async (id) => {
  const request = ++comparisonRequest;
  comparison.value = null;
  if (!id) {
    comparisonLoading.value = false;
    return;
  }
  comparisonLoading.value = true;
  try {
    const result = await getResumeVersion(id);
    if (request === comparisonRequest) comparison.value = result;
  } catch (e) {
    if (request === comparisonRequest)
      error.value = e.message || "版本对比失败";
  } finally {
    if (request === comparisonRequest) comparisonLoading.value = false;
  }
});
watch(
  editor,
  () => {
    if (!loadingVersion) dirty.value = true;
  },
  { deep: true },
);
watch([versionTitle, selectedJobId], () => {
  if (!loadingVersion) dirty.value = true;
});
watch([() => claimForm.category, exampleBrief, selectedJobId], () => {
  claimExample.value = null;
});
onMounted(async () => {
  window.addEventListener("beforeunload", beforeUnload);

  try {
    const results = await Promise.allSettled([
      getResumeClaims(),
      getResumeVersions(),
      getJobList(),
      getResumeAiStatus(),
      getResumeFileProfile(),
    ]);
    const targets = [claims, versions, jobs];
    results.slice(0, 3).forEach((result, index) => {
      if (result.status === "fulfilled")
        targets[index].value = result.value || [];
      else
        error.value = result.reason?.message || "部分数据加载失败，请刷新重试";
    });
    if (results[3].status === "fulfilled")
      resumeAiStatus.value = results[3].value;
    if (results[4].status === "fulfilled")
      uploadedResumeName.value = results[4].value?.filename || "";
    const requested = Number(route.query.versionId);
    if (requested && !dirty.value && !claimDirty.value) {
      await openVersion(requested);
      tab.value = route.query.tab === "editor" ? "editor" : "preview";
      if (tab.value === "preview") await loadPreview();
    }
    if (route.query.analysisId) {
      const report = await getResumeAnalysisReport(Number(route.query.analysisId));
      const index = Number(route.query.analysisItem);
      await editFromAnalysis({ report, item: Number.isInteger(index) ? report.items[index] : null });
    }
  } catch (e) {
    error.value = e.message || "简历工作台加载失败";
  } finally {
    loadingWorkbench.value = false;
  }
});
onUnmounted(() => {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
  window.removeEventListener("beforeunload", beforeUnload);
});

function statusLabel(value) {
  return (
    {
      PENDING: "待确认",
      CONFIRMED: "已确认",
      EXPIRED: "已过期",
      REJECTED: "不采用",
    }[value] || value
  );
}
function categoryLabel(value) {
  return categories.find((item) => item.value === value)?.label || value;
}
function responsibilityLabel(value) {
  return (
    {
      PARTICIPATED: "参与",
      MODULE: "负责模块",
      LED: "主导方案或交付",
      OWNER: "项目负责人",
    }[value] || value
  );
}
function formatDate(value) {
  return value ? String(value).replace("T", " ").slice(0, 16) : "";
}
function claimSnapshot() {
  return JSON.stringify({ ...claimForm, editingClaimId: editingClaimId.value });
}
function clearWordingSuggestion() {
  wordingSuggestion.value = "";
  wordingSourceSnapshot.value = "";
}
function resetClaim() {
  Object.assign(claimForm, emptyClaim());
  editingClaimId.value = null;
  clearWordingSuggestion();
  claimBaseline.value = JSON.stringify(emptyClaim());
}
function editClaim(item) {
  guardLeave(() => {
    resetClaim();
    editingClaimId.value = item.id;
    Object.assign(
      claimForm,
      Object.fromEntries(
        Object.keys(emptyClaim()).map((key) => [
          key,
          item[key] || emptyClaim()[key],
        ]),
      ),
    );
    claimBaseline.value = JSON.stringify({ ...claimForm });
    nextTick(() => claimTitleInput.value?.focus());
  }, "claim");
}
function requestClearClaim() {
  guardLeave(resetClaim, "claim");
}
function useSuggestion(item, index) {
  guardLeave(() => {
    resetClaim();
    Object.assign(claimForm, item, { verificationStatus: "PENDING" });
    suggestions.value.splice(index, 1);
    nextTick(() => claimTitleInput.value?.focus());
  }, "claim");
}
function discardSuggestion(item) {
  suggestions.value = suggestions.value.filter((candidate) => candidate !== item);
}
async function verifySelectedSuggestions() {
  if (batchVerifying.value || !selectedSuggestions.value.length) return;
  const selected = [...selectedSuggestions.value];
  if (!window.confirm(`确认已核对所选 ${selected.length} 条原始事实，且均为本人真实经历？确认后将保存为已确认事实。`)) return;
  batchVerifying.value = true;
  error.value = "";
  let savedCount = 0;
  try {
    for (const item of selected) {
      if (!item.title?.trim() || !item.factText?.trim()) {
        throw new Error("部分条目缺少经历名称或原始事实，请使用“核对并编辑”补充后保存。");
      }
      const saved = await addResumeClaim({
        ...emptyClaim(), ...item,
        resumeText: item.resumeText?.trim() || item.factText,
        verificationStatus: "CONFIRMED",
      });
      claims.value = [saved, ...claims.value.filter((claim) => claim.id !== saved.id)];
      discardSuggestion(item);
      savedCount++;
    }
  } catch (e) {
    error.value = e.message || "批量核对保存失败，请重试剩余条目";
  } finally {
    message.value = `已确认并保存 ${savedCount} 条事实${savedCount < selected.length ? "，其余条目已保留，可继续核对或重试" : ""}。`;
    batchVerifying.value = false;
  }
}
async function loadSuggestions() {
  if (loadingSuggestions.value) return;
  loadingSuggestions.value = true;
  suggestionsMessage.value = "";
  error.value = "";
  try {
    suggestions.value = (await getResumeClaimSuggestions()) || [];
    suggestionsMessage.value = suggestions.value.length
      ? `已提取 ${suggestions.value.length} 条草稿，请逐条核对。`
      : "这份简历已读取，但规则未识别到项目或技能条目；可在下方手动添加。";
  } catch (e) {
    suggestionsMessage.value = e.message || "提取失败，请检查上传的简历";
  } finally {
    loadingSuggestions.value = false;
  }
}
async function uploadResumeForClaims(event) {
  const file = event.target?.files?.[0];
  if (event.target) event.target.value = "";
  if (!file || uploadingResume.value) return;
  if (!/\.(pdf|doc|docx)$/i.test(file.name) || file.size > 10 * 1024 * 1024) {
    suggestionsMessage.value = "仅支持不超过 10MB 的 PDF 或 Word 简历";
    return;
  }
  if (
    uploadedResumeName.value &&
    !window.confirm("上传新简历会替换当前账号已上传的简历。确定继续？")
  )
    return;
  uploadingResume.value = true;
  suggestionsMessage.value = "";
  error.value = "";
  try {
    await uploadResumeFile(file);
    uploadedResumeName.value = file.name;
    suggestions.value = [];
    suggestionsMessage.value =
      "简历已解析。现在点击“从已上传简历提取待核实条目”。";
  } catch (e) {
    suggestionsMessage.value = e.message || "简历上传或解析失败";
  } finally {
    uploadingResume.value = false;
  }
}
async function generateExample() {
  if (loadingExample.value) return;
  loadingExample.value = true;
  claimExample.value = null;
  error.value = "";
  const category = claimForm.category;
  const brief = exampleBrief.value.trim();
  const requestedJobId = selectedJobId.value;
  const targetJob =
    jobs.value.find(
      (item) => item.id === requestedJobId || item.code === route.query.job,
    )?.name || "";
  try {
    const example = await generateResumeClaimExample({
      category,
      targetJob,
      brief,
    });
    if (
      category !== claimForm.category ||
      brief !== exampleBrief.value.trim() ||
      requestedJobId !== selectedJobId.value
    ) {
      error.value = "分类或线索已变化，请重新生成示例";
      return;
    }
    claimExample.value = example;
  } catch (e) {
    error.value = e.message || "AI 生成示例失败，请稍后重试";
  } finally {
    loadingExample.value = false;
  }
}
async function loadAiWording() {
  if (loadingAiWording.value) return;
  const before = claimSnapshot();
  loadingAiWording.value = true;
  error.value = "";
  clearWordingSuggestion();
  try {
    const result = await getAiResumeClaimWording({
      category: claimForm.category,
      title: claimForm.title,
      factText: claimForm.factText,
      responsibility: claimForm.responsibility,
      personalBoundary: claimForm.personalBoundary,
    });
    if (claimSnapshot() !== before) {
      error.value = "表单已变化，旧的 AI 建议未采用；请重新生成";
      return;
    }
    wordingSuggestion.value = result?.text || "";
    wordingSourceSnapshot.value = before;
  } catch (e) {
    error.value = e.message || "AI 起草失败，请手动填写";
  } finally {
    loadingAiWording.value = false;
  }
}
function applyAiWording() {
  if (
    !wordingSuggestion.value ||
    claimSnapshot() !== wordingSourceSnapshot.value
  ) {
    clearWordingSuggestion();
    error.value = "表单已变化，请重新生成 AI 表述";
    return;
  }
  claimForm.resumeText = wordingSuggestion.value;
  claimForm.verificationStatus = "PENDING";
  clearWordingSuggestion();
}
async function saveClaim() {
  if (savingClaim.value || loadingWorkbench.value) return false;
  if (!claimForm.title.trim() || !claimForm.factText.trim()) {
    error.value = "请填写经历名称和原始事实";
    return false;
  }
  // The existing API requires resumeText. A verbatim fact is safer than invented wording.
  const submitted = JSON.stringify({ ...claimForm });
  const payload = {
    ...claimForm,
    resumeText: claimForm.resumeText.trim() || claimForm.factText,
  };
  savingClaim.value = true;
  error.value = "";
  try {
    const saved = editingClaimId.value
      ? await updateResumeClaim(editingClaimId.value, payload)
      : await addResumeClaim(payload);
    claims.value = [
      saved,
      ...claims.value.filter((item) => item.id !== saved.id),
    ];
    if (JSON.stringify({ ...claimForm }) === submitted) resetClaim();
    else {
      editingClaimId.value = saved.id;
      claimBaseline.value = submitted;
    }
    message.value =
      "事实已保存；旧简历版本不会改变。制作中的草稿如引用过该事实，请重新核查。";
    return true;
  } catch (e) {
    error.value = e.message || "事实条目保存失败";
  } finally {
    savingClaim.value = false;
  }
}
async function removeClaim(item) {
  if (deletingClaimId.value) return;
  if (!window.confirm(`移除“${item.title}”？已保存的简历版本不会改变。`))
    return;
  const before = claimSnapshot();
  deletingClaimId.value = item.id;
  try {
    await deleteResumeClaim(item.id);
    claims.value = await getResumeClaims();
    if (editingClaimId.value === item.id) {
      if (claimSnapshot() === before) resetClaim();
      else {
        editingClaimId.value = null;
        claimBaseline.value = JSON.stringify(emptyClaim());
        claimForm.verificationStatus = "PENDING";
        message.value =
          "原事实已移除；删除期间新增的输入仍保留为待保存的新事实";
      }
    }
  } catch (e) {
    error.value = e.message || "移除失败";
  } finally {
    deletingClaimId.value = null;
  }
}
async function adoptVersion(version, loadEditor = true) {
  activeVersion.value = version;
  if (loadEditor) {
    loadingVersion = true;
    editorVersionId.value = version.id;
    Object.assign(editor, emptyContent(), version.content);
    versionTitle.value = version.title;
    selectedJobId.value = version.jobId || null;
    editorBaseline = {
      content: { ...editor },
      title: versionTitle.value,
      jobId: selectedJobId.value,
    };
    await nextTick();
    dirty.value = false;
    loadingVersion = false;
  }
  comparisonId.value = null;
  previewBlob = null;
  ++previewRequest;
  previewLoading.value = false;
  exportMessage.value = "";
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = "";
  }
}
async function openVersion(id, loadEditor = true) {
  if (openingVersion.value) return;
  openingVersion.value = true;
  error.value = "";
  const before = editorSignature();
  try {
    const version = await getResumeVersion(id);
    const unchanged = before === editorSignature();
    await adoptVersion(version, loadEditor && unchanged);
    if (loadEditor && !unchanged)
      message.value =
        "版本已选中；载入期间新增的输入仍保留在草稿中，请核对后保存";
  } catch (e) {
    error.value = e.message || "读取简历版本失败";
  } finally {
    openingVersion.value = false;
    loadingVersion = false;
  }
}
async function saveVersion() {
  if (savingVersion.value || loadingWorkbench.value) return false;
  if (editor.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(editor.email)) {
    error.value = "请填写有效的邮箱地址";
    return false;
  }
  const limits = {
    summary: 2500,
    education: 6000,
    experience: 8000,
    projects: 10000,
    skills: 4000,
    awards: 4000,
  };
  for (const [key, limit] of Object.entries(limits))
    if (editor[key].length > limit) {
      error.value = `${contentFields.find((f) => f.key === key).label}超过 ${limit} 字，请缩短后保存`;
      return false;
    }
  const submitted = {
    title: versionTitle.value,
    jobId: selectedJobId.value,
    content: { ...editor },
  };
  savingVersion.value = true;
  error.value = "";
  try {
    const created = await saveResumeVersion(submitted);
    versions.value = [
      created,
      ...versions.value.filter((item) => item.id !== created.id),
    ];
    const unchanged =
      JSON.stringify(submitted) ===
      JSON.stringify({
        title: versionTitle.value,
        jobId: selectedJobId.value,
        content: { ...editor },
      });
    await adoptVersion(created, unchanged);
    if (!unchanged) {
      editorBaseline = {
        content: { ...created.content },
        title: created.title,
        jobId: created.jobId,
      };
      dirty.value = true;
    }
    message.value = unchanged
      ? "新版本已保存，历史版本保持不变"
      : "提交时的内容已保存为新版本；保存期间的后续修改仍保留在草稿中";
    return unchanged;
  } catch (e) {
    error.value = e.message || "保存简历失败";
    return false;
  } finally {
    savingVersion.value = false;
  }
}
async function generate() {
  if (generating.value || !selectedJobId.value || !confirmedClaimCount.value)
    return;
  if (
    !window.confirm(
      "现有接口会立即保存一份 AI 候选版本。生成后仍需审核，你的当前草稿不会被自动覆盖。继续？",
    )
  )
    return;
  generating.value = true;
  error.value = "";
  aiSnapshot = editorSignature();
  try {
    const created = await generateResumeVersion({
      title: versionTitle.value,
      jobId: selectedJobId.value,
      name: editor.name,
      phone: editor.phone,
      email: editor.email,
    });
    versions.value = [
      created,
      ...versions.value.filter((item) => item.id !== created.id),
    ];
    aiSuggestion.value = created.content;
    aiReviewFields.value = [
      { key: "targetJob", label: "求职意向" },
      ...contentFields,
    ].filter((field) => created.content?.[field.key]);
    aiDialog.value.showModal();
  } catch (e) {
    error.value = e.message || "生成失败，原文已保留，请重试";
  } finally {
    generating.value = false;
  }
}
async function optimizeContent() {
  if (generating.value || !currentEditorText.value.trim()) return;
  if (currentEditorText.value.length > 4000) {
    error.value = "当前分区超过 AI 优化接口的 4000 字限制，请分段整理后重试";
    return;
  }
  generating.value = true;
  error.value = "";
  aiSnapshot = editorSignature();
  const key = currentEditorKey.value;
  try {
    const category = {
      education: "EDUCATION",
      experience: "EXPERIENCE",
      projects: "PROJECT",
      skills: "SKILL",
      awards: "AWARD",
      summary: "SKILL",
    }[key];
    const result = await getAiResumeClaimWording({
      category,
      title: editorSections.find((s) => s.key === editorSection.value)?.label,
      factText: editor[key],
      responsibility: "PARTICIPATED",
      personalBoundary: "",
    });
    aiSuggestion.value = { [key]: result.text };
    aiReviewFields.value = contentFields.filter((f) => f.key === key);
    aiDialog.value.showModal();
  } catch (e) {
    error.value = e.message || "优化失败，原文已保留，请重试";
  } finally {
    generating.value = false;
  }
}
function editorSignature() {
  return JSON.stringify({
    content: { ...editor },
    jobId: selectedJobId.value,
    title: versionTitle.value,
  });
}
function acceptAi() {
  if (editorSignature() !== aiSnapshot) {
    error.value = "草稿已经变化，请重新请求 AI 建议";
    aiDialog.value.close();
    aiSuggestion.value = null;
    return;
  }
  for (const field of aiReviewFields.value)
    editor[field.key] = aiSuggestion.value[field.key];
  aiSuggestion.value = null;
  aiDialog.value.close();
  message.value = "建议已进入草稿，请核实后保存新版本";
}
function referenceClaim(item) {
  const key = {
    EDUCATION: "education",
    EXPERIENCE: "experience",
    PROJECT: "projects",
    SKILL: "skills",
    AWARD: "awards",
  }[item.category];
  if (!key) return;
  editor[key] = [editor[key], item.resumeText].filter(Boolean).join("\n\n");
  editorSection.value = key;
  message.value = `已追加“${item.title}”，请核对并保存新版本`;
}
async function selectVersion(id) {
  if (!id || openingVersion.value || exporting.value || savingVersion.value)
    return;
  if (tab.value === "preview") {
    await openVersion(id, false);
    await loadPreview();
  } else guardLeave(() => openVersion(id), "editor");
}
async function loadPreview() {
  if (!activeVersion.value || previewLoading.value) return;
  const id = activeVersion.value.id;
  const request = ++previewRequest;
  previewLoading.value = true;
  previewError.value = "";
  try {
    const response = await exportResumeVersion(id, "pdf");
    if (request !== previewRequest || activeVersion.value?.id !== id) return;
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewBlob = response.data;
    previewUrl.value = URL.createObjectURL(previewBlob);
  } catch (e) {
    if (request === previewRequest)
      previewError.value =
        e.message || "预览失败，请检查待补内容和中文字体，然后重试";
  } finally {
    if (request === previewRequest) previewLoading.value = false;
  }
}
async function download(format) {
  if (!activeVersion.value || exporting.value) return;
  const id = activeVersion.value.id;
  exporting.value = format;
  exportMessage.value = "";
  error.value = "";
  try {
    const blob =
      format === "pdf" && previewBlob
        ? previewBlob
        : (await exportResumeVersion(id, format)).data;
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `resume-${id}.${format}`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    exportMessage.value = `${format.toUpperCase()} 已生成并发送至浏览器下载`;
  } catch (e) {
    exportMessage.value = "导出失败，可点击下载重试";
    error.value = e.message || "导出失败";
  } finally {
    exporting.value = "";
  }
}
function openAnalysis() {
  if (!activeVersion.value) return;
  analysisJobId.value = activeVersion.value.jobId || selectedJobId.value;
  analysisVersionId.value = activeVersion.value.id;
  if (!analysisJobId.value) { error.value = "请先选择目标岗位"; return; }
  analysisOpen.value = true;
  analysisDialog.value.showModal();
}
function goAnalysisTargets(report) {
  analysisDialog.value.close();
  router.push({ path: "/jobs", query: { jobId: report.snapshot.jobId, resumeVersionId: report.snapshot.versionId, analysisId: report.id, applyAnalysis: "1", step: "2", from: "builder" } });
}
async function editFromAnalysis({ report, item }) {
  if (!report?.applicable) { error.value = "报告对象已变化，请重新分析后修改"; return; }
  analysisDialog.value?.close();
  const action = async () => {
    if (!report.snapshot.versionId) {
      tab.value = "claims";
      message.value = "原始文件无法直接编辑。请提取候选事实并逐条核实，或依据原文手动制作新版本；分析不会自动确认事实。";
      return;
    }
    if (editorVersionId.value !== report.snapshot.versionId) await openVersion(report.snapshot.versionId);
    if (editorVersionId.value !== report.snapshot.versionId) return;
    tab.value = "editor";
    const key = item?.field || route.query.section;
    editorSection.value = editorSections.some(s => s.key === key) ? key : "basic";
    if (item?.kind === "SUGGESTION") {
      analysisEdit.value = { report, item }; analysisEditError.value = "";
      await nextTick(); analysisReviewDialog.value.showModal();
    } else message.value = item ? `${item.title}：${item.action}` : "已定位对应版本草稿，修改后请保存为新版本。";
  };
  if (report.snapshot.versionId && editorVersionId.value === report.snapshot.versionId && !claimDirty.value) await action();
  else guardLeave(action);
}
async function acceptAnalysis() {
  if (acceptingAnalysis.value || !analysisEdit.value) return;
  acceptingAnalysis.value = true; analysisEditError.value = "";
  const { report, item } = analysisEdit.value;
  try {
    const fresh = await getResumeAnalysisReport(report.id);
    if (!fresh.applicable || editorVersionId.value !== report.snapshot.versionId || (selectedJobId.value && selectedJobId.value !== report.snapshot.jobId)) throw new Error("岗位或版本已变化，请重新核查");
    const next = applyAnalysisSuggestion(editor, item);
    editor[item.field] = next[item.field];
    analysisReviewDialog.value.close();
    message.value = "建议已应用到对应草稿，尚未保存；旧版本和事实记录保持原样。";
  } catch (e) { analysisEditError.value = e.message || "核查失败，草稿未修改"; }
  finally { acceptingAnalysis.value = false; }
}
function beforeUnload(event) {
  if (dirty.value || claimDirty.value) {
    event.preventDefault();
    event.returnValue = "";
  }
}
function guardLeave(action, scope = "all") {
  if (
    savingClaim.value ||
    savingVersion.value ||
    generating.value ||
    openingVersion.value
  ) {
    error.value = "请等待当前请求完成后再继续";
    return;
  }
  if (
    (scope !== "editor" && claimDirty.value) ||
    (scope !== "claim" && dirty.value)
  ) {
    leaveScope = scope;
    leaveAction = action;
    leaveDialog.value.showModal();
  } else action();
}
function cancelLeave() {
  leaveDialog.value.close();
  leaveAction = null;
  leaveResolve?.(false);
  leaveResolve = null;
}
async function finishLeave(save) {
  if (save) {
    if (leaveScope !== "editor" && claimDirty.value && !(await saveClaim()))
      return;
    if (leaveScope !== "claim" && dirty.value && !(await saveVersion())) return;
  } else {
    if (leaveScope !== "editor" && claimDirty.value) resetClaim();
    if (leaveScope !== "claim" && dirty.value) {
      loadingVersion = true;
      Object.assign(editor, emptyContent(), editorBaseline?.content || {});
      versionTitle.value = editorBaseline?.title || "我的简历";
      selectedJobId.value =
        editorBaseline?.jobId || Number(route.query.jobId) || null;
      await nextTick();
      loadingVersion = false;
      dirty.value = false;
    }
  }
  leaveDialog.value.close();
  const action = leaveAction;
  leaveAction = null;
  if (leaveResolve) {
    routeApproved = true;
    leaveResolve(true);
    leaveResolve = null;
  } else await action?.();
}
function switchTab(key) {
  if (key === tab.value) return;
  guardLeave(async () => {
    if (
      key === "editor" &&
      activeVersion.value &&
      editorVersionId.value !== activeVersion.value.id &&
      !dirty.value
    )
      await openVersion(activeVersion.value.id);
    tab.value = key;
    message.value = "";
    if (key === "preview" && activeVersion.value && !previewUrl.value)
      await loadPreview();
  });
}
onBeforeRouteLeave(() => {
  if (routeApproved) {
    routeApproved = false;
    return true;
  }
  if (
    savingClaim.value ||
    savingVersion.value ||
    generating.value ||
    openingVersion.value
  )
    return false;
  if (!dirty.value && !claimDirty.value) return true;
  return new Promise((resolve) => {
    leaveResolve = resolve;
    leaveScope = "all";
    leaveAction = null;
    leaveDialog.value.showModal();
  });
});
function returnToPreparation(useVersion = false) {
  if (useVersion && !activeVersion.value) return;
  if (
    useVersion &&
    !window.confirm(
      "将当前已保存版本用于本次面试准备；若原先选择了其他简历，将替换该选择。继续？",
    )
  )
    return;
  binding.value = true;
  router
    .push({
      path: "/jobs",
      query: {
        job: route.query.job || undefined,
        jobId:
          (useVersion ? activeVersion.value?.jobId : selectedJobId.value) ||
          route.query.jobId ||
          undefined,
        resumeVersionId: useVersion ? activeVersion.value?.id : undefined,
        step: useVersion ? "2" : "1",
        from: "builder",
      },
    })
    .finally(() => {
      binding.value = false;
    });
}
</script>

<style scoped>
.builder {
  box-sizing: border-box;
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 0 64px;
  color: #203c34;
  font-size: 14px;
}
.builder *,
.builder *::before,
.builder *::after {
  box-sizing: border-box;
}
.builder-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 16px;
}
.eyebrow {
  color: var(--accent-700, #078660);
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 6px;
}
.builder h1 {
  font-size: 28px;
  line-height: 1.4;
  margin: 0 0 6px;
}
.builder-head p:last-child {
  color: #61736d;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
}
.builder h2 {
  font-size: 19px;
  line-height: 1.5;
  margin: 0 0 12px;
}
.builder h3 {
  font-size: 16px;
  margin: 0 0 10px;
}
.builder p {
  line-height: 1.65;
}
.resume-ai-status {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 48px;
  padding: 10px 18px;
  border: 1px solid #d3e8de;
  background: #f1faf6;
  border-radius: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}
.resume-ai-status strong {
  font-size: 14px;
}
.resume-ai-status > span {
  color: #5d7168;
}
.resume-ai-status details {
  margin-left: auto;
  position: relative;
}
.resume-ai-status summary {
  cursor: pointer;
  color: #087f5b;
  white-space: nowrap;
}
.resume-ai-status details p {
  position: absolute;
  right: 0;
  top: 24px;
  width: 300px;
  max-width: 75vw;
  padding: 16px;
  background: white;
  border: 1px solid #d3e8de;
  border-radius: 10px;
  z-index: 2;
  color: #40594d;
}
.builder-tabs {
  display: flex;
  gap: 14px;
  border-bottom: 1px solid #e1e9e4;
  margin-bottom: 18px;
}
.builder-tabs button {
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: #64766e;
  padding: 14px 16px;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  min-height: 44px;
}
.builder-tabs button.active {
  color: #078660;
  border-color: #078660;
}
.builder-tabs span {
  margin-right: 12px;
  font-size: 13px;
}
.builder-grid {
  display: grid;
  grid-template-columns: minmax(0, 832fr) minmax(0, 424fr);
  gap: 24px;
  align-items: start;
}
.column {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.panel {
  background: white;
  border: 1px solid #dfe8e3;
  border-radius: 12px;
  padding: 20px;
  min-width: 0;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 16px;
}
.panel-head h2 {
  margin-bottom: 4px;
}
.panel-head p {
  margin: 0;
  font-size: 13px;
  color: #61736d;
}
.panel-head > small {
  white-space: nowrap;
  color: #61736d;
  font-size: 12px;
}
.panel label {
  display: grid;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}
.panel label > input,
.panel label > textarea,
.panel label > select {
  font-weight: 400;
}
.panel input,
.panel textarea,
.panel select {
  width: 100%;
  min-width: 0;
  font: inherit;
  color: #203c34;
  border: 1px solid #d6e3db;
  border-radius: 8px;
  padding: 10px 12px;
  background: white;
}
.panel input,
.panel select {
  min-height: 41px;
}
.panel textarea {
  resize: vertical;
  line-height: 1.6;
}
.panel input::placeholder,
.panel textarea::placeholder {
  color: #6b7b73;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-row--actions {
  align-items: center;
}
.form-row--actions > div,
.editor-actions,
.export-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
.primary,
.quiet {
  min-height: 42px;
  border-radius: 8px;
  padding: 9px 16px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}
.primary {
  background: var(--accent-500, #09976b);
  color: white;
  border: 1px solid var(--accent-500, #09976b);
}
.quiet {
  background: white;
  color: #087f5b;
  border: 1px solid #bdddcf;
}
.primary:hover:not(:disabled) {
  filter: brightness(0.94);
}
.quiet:hover:not(:disabled) {
  background: #f1faf6;
}
.builder button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.builder :focus-visible {
  outline: 2px solid #078660;
  outline-offset: 3px;
}
.full {
  width: 100%;
  margin-top: 16px;
}
.import-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.file-source {
  flex: 1;
  min-width: 180px;
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  background: #f8faf9;
  border: 1px solid #e7ede9;
  border-radius: 8px;
  overflow-wrap: anywhere;
}
.file-source small {
  font-size: 12px;
  color: #63766b;
}
.source-panel {
  min-height: 116px;
}
.claim-form {
  display: grid;
  gap: 16px;
}
.claim-form .panel-head {
  margin-bottom: 0;
}
.fineprint {
  color: #61736d;
  font-size: 12px;
  line-height: 1.65;
  margin: 0;
}
.info {
  padding: 9px 12px;
  background: #f5f8fa;
  border-radius: 7px;
  color: #5b6d74;
  font-size: 12px;
  line-height: 1.6;
  margin: 10px 0;
}
.claim-form > .info {
  margin: 0;
}
.more-details {
  border: 1px solid #e0e9e4;
  border-radius: 8px;
  padding: 12px;
}
.more-details summary {
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
  min-height: 24px;
}
.more-details summary small {
  margin-left: 12px;
  color: #087f5b;
  background: #eaf7f0;
  padding: 3px 6px;
}
.more-details[open] > :not(summary) {
  margin-top: 14px;
}
.example-tools,
.ai-wording {
  display: flex;
  gap: 12px;
  align-items: end;
  flex-wrap: wrap;
}
.example-tools label {
  flex: 1;
  min-width: 150px;
}
.ai-wording span {
  font-size: 12px;
  color: #61736d;
  line-height: 1.6;
}
.example-card,
.suggestions {
  padding: 14px;
  border: 1px solid #d3e8de;
  border-radius: 8px;
  background: #f4faf7;
}
.example-card p,
.suggestions p {
  white-space: pre-wrap;
  font-size: 13px;
}
.example-card small {
  color: #715725;
}
.suggestion {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid #e3ebe6;
}
.suggestion-panel > .panel-head { margin-bottom: 8px; }
.suggestion-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.suggestion-toolbar > span { color: #546b5e; font-size: 14px; }
.suggestion-toolbar > button { margin-left: auto; }
.suggestion-check { display: flex; align-items: center; gap: 10px; min-height: 44px; cursor: pointer; }
.suggestion-check input { width: 18px; height: 18px; flex-shrink: 0; accent-color: var(--accent-500, #10b981); }
.suggestion-list { max-height: min(360px, 50dvh); overflow-y: auto; overscroll-behavior-y: contain; scrollbar-gutter: stable; }
.suggestion-content { min-width: 0; flex: 1; }
.suggestion-content span { min-width: 0; overflow-wrap: anywhere; }
.suggestion-content strong { font-size: 14px; font-weight: 600; }
.suggestion-content small { display: block; white-space: pre-wrap; color: #546b5e; font-size: 13px; margin-top: 4px; }
.suggestion-list .quiet { min-height: 44px; padding: 8px 10px; font-size: 13px; }
.suggestion > span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.suggestion > div {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 20px;
  text-align: center;
}
.stats > div + div {
  border-left: 1px solid #e6ece8;
}
.stats small {
  display: block;
  color: #63766b;
  font-size: 12px;
}
.stats strong {
  display: block;
  font-size: 26px;
  line-height: 1.6;
}
.stats > div:first-child strong {
  color: #078660;
}
.empty-state {
  text-align: center;
  padding: 32px 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.empty-state h3,
.empty-state p {
  margin: 0;
}
.empty-state p {
  color: #63766b;
  font-size: 13px;
  max-width: 32ch;
}
.document-symbol {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #f3f8fb;
  color: #7897ae;
  font-size: 34px;
  margin-bottom: 8px;
}
.compact {
  padding: 0;
  gap: 8px;
}
.compact .document-symbol {
  margin-bottom: 0;
}
.ai-limits {
  font-size: 12px;
  color: #61736d;
}
.ai-limits summary {
  cursor: pointer;
}
.compact .document-symbol {
  width: 52px;
  height: 52px;
  font-size: 28px;
}
.ledger > .panel-head { margin-bottom: 0; }
.ledger-list {
  max-height: min(480px, 60dvh);
  overflow-y: auto;
  overscroll-behavior-y: contain;
  scrollbar-gutter: stable;
}
.ledger-item {
  padding: 14px 0;
  border-bottom: 1px solid #e6ece8;
  overflow-wrap: anywhere;
}
.ledger-item > div:first-child {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.ledger-item strong {
  font-size: 14px;
}
.ledger-item p {
  font-size: 13px;
  white-space: pre-wrap;
  color: #546b5e;
  margin: 8px 0;
}
.ledger-item small {
  color: #63766b;
  font-size: 12px;
}
.ledger-actions {
  display: flex;
  gap: 16px;
  margin-top: 10px;
}
.ledger-actions button {
  border: 0;
  background: none;
  color: #087f5b;
  font: inherit;
  cursor: pointer;
  min-height: 36px;
  padding: 0;
}
.status {
  font-size: 12px;
  color: #725a31;
  background: #fff7e9;
  padding: 4px 8px;
  border-radius: 6px;
  white-space: nowrap;
}
.status.confirmed {
  color: #087c5a;
  background: #e6f8ee;
}
.status.rejected,
.status.expired {
  color: #8c5460;
  background: #faebee;
}
.editor-panel {
  display: grid;
  gap: 12px;
}
.editor-panel > .panel-head {
  margin-bottom: 0;
}
.section-tabs {
  display: flex;
  background: #f5f8f7;
  border-radius: 8px;
  overflow-x: auto;
}
.section-tabs button {
  flex: 1;
  white-space: nowrap;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  min-height: 42px;
  padding: 8px;
  color: #65776c;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.section-tabs button.active {
  background: white;
  color: #078660;
  border-color: #078660;
  border-radius: 8px;
  font-weight: 700;
}
.editor-panel h3 {
  margin: 0;
}
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid #e7ede9;
}
.editor-footer .primary {
  flex-shrink: 0;
}
.version-card {
  display: grid;
  gap: 5px;
  text-align: left;
  width: 100%;
  border: 1px solid #e1eae4;
  border-radius: 8px;
  background: white;
  padding: 12px;
  margin-top: 10px;
  cursor: pointer;
  color: #203c34;
  overflow-wrap: anywhere;
}
.version-card.active {
  border-color: #0ca574;
  background: #f1fbf6;
}
.version-card small {
  color: #61736d;
  font-size: 12px;
}
.export-actions {
  justify-content: flex-start;
  margin: 14px 0;
}
.export-actions > button {
  flex: 1;
  padding-left: 8px;
  padding-right: 8px;
}
.preview-stage > .column .panel {
  padding: 16px;
}
.preview-stage > .column .panel-head {
  margin-bottom: 10px;
}
.preview-stage > .column h2 {
  margin-bottom: 8px;
}
.preview-stage > .column .info {
  margin: 8px 0;
}
.preview-panel {
  padding: 20px;
}
.preview-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
}
.preview-tools .quiet {
  padding: 8px 10px;
  min-height: 36px;
}
.document-canvas {
  background: #eef3f1;
  padding: 24px;
  border-radius: 8px;
  display: flex;
  justify-content: center;
  min-width: 0;
}
.a4-paper {
  background: white;
  width: min(100%, 430px);
  aspect-ratio: 210/297;
  display: grid;
  place-items: center;
  box-shadow: 0 10px 28px #203c3415;
  border-radius: 3px;
}
.a4-paper .empty-state {
  padding: 24px;
}
.a4-paper .document-symbol {
  background: none;
  border-radius: 0;
  font-size: 52px;
  margin-bottom: 16px;
}
.comparison {
  border-top: 1px solid #e5ebe7;
  padding: 12px 0;
  overflow-wrap: anywhere;
}
.comparison p {
  font-size: 12px;
  white-space: pre-wrap;
  line-height: 1.6;
  color: #52695d;
  max-height: 220px;
  overflow: auto;
}
.notice {
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff8e6;
  color: #785712;
  font-size: 13px;
  margin-bottom: 14px;
}
.notice--error {
  background: #fff0ee;
  color: #ad332a;
}
.review-dialog {
  margin: auto;
  width: min(720px, calc(100vw - 32px));
  max-height: 85vh;
  overflow: auto;
  border: 1px solid #d3e2d9;
  border-radius: 12px;
  padding: 24px;
  color: #203c34;
  background: white;
}
.review-dialog::backdrop {
  background: #15342966;
}
.review-dialog h2 {
  font-size: 20px;
  margin: 0 0 12px;
}
@media (max-width: 1340px) {
  .builder {
    margin: 0 24px;
  }
  .builder-grid {
    grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
    gap: 20px;
  }
  .preview-panel > .panel-head {
    flex-wrap: wrap;
  }
}
@media (max-width: 960px) {
  .builder-grid {
    grid-template-columns: 1fr;
  }
  .builder-head h1 {
    font-size: 24px;
  }
  .builder-head > .quiet {
    flex-shrink: 0;
  }
  .column {
    gap: 16px;
  }
  .resume-ai-status {
    flex-wrap: wrap;
  }
  .a4-paper {
    width: min(100%, 430px);
  }
}
@media (max-width: 620px) {
  .builder {
    margin: 0;
    padding: 20px 16px 60px;
  }
  .builder-head {
    flex-direction: column;
    gap: 14px;
  }
  .builder h1 {
    font-size: 22px;
  }
  .form-row {
    grid-template-columns: 1fr;
  }
  .panel {
    padding: 16px;
  }
  .builder-tabs {
    gap: 0;
  }
  .builder-tabs button {
    flex: 1;
    padding: 12px 4px;
    font-size: 12px;
  }
  .builder-tabs span {
    margin-right: 5px;
  }
  .resume-ai-status {
    gap: 8px;
    padding: 12px;
  }
  .resume-ai-status > span {
    width: 100%;
    order: 3;
  }
  .resume-ai-status details {
    margin-left: auto;
  }
  .panel-head {
    flex-wrap: wrap;
  }
  .editor-footer {
    flex-direction: column;
    align-items: stretch;
  }
  .document-canvas {
    padding: 12px;
  }
  .a4-paper .empty-state {
    padding: 12px;
    gap: 8px;
  }
  .a4-paper .document-symbol {
    font-size: 36px;
    margin: 0;
  }
  .a4-paper h3 {
    font-size: 15px;
  }
  .a4-paper p {
    font-size: 12px;
  }
  .suggestion {
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .builder * {
    scroll-behavior: auto !important;
    animation: none !important;
    transition: none !important;
  }
}
</style>

<style scoped>
.analysis-dialog{margin:auto;width:min(1100px,calc(100vw - 32px));max-height:90dvh;border:1px solid #dce7e1;border-radius:12px;padding:24px;overflow:auto}.analysis-dialog::backdrop{background:rgba(18,37,28,.32)}.analysis-close{display:block;margin:0 0 16px auto}.review-dialog pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:220px;overflow:auto;background:#f5f8f6;padding:12px;font:inherit}@media(max-width:760px){.analysis-dialog{padding:16px}}
</style>
