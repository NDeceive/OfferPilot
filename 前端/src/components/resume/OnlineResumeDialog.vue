<template>
  <Teleport to="body">
    <Transition name="onl">
      <div v-if="modelValue" class="onl-mask" @click.self="close">
        <section class="onl" role="dialog" aria-modal="true" aria-label="在线简历">
          <header class="onl__head">
            <div>
              <h3 class="onl__title">在线简历</h3>
              <p class="onl__sub">
                <template v-if="phase === 'form'">没有简历文件？填一份，和上传文件效果一样</template>
                <template v-else>解析出 {{ skills.length }} 个技能标签，将用于出题</template>
              </p>
            </div>
            <button class="onl__x" aria-label="关闭" @click="close">&times;</button>
          </header>

          <div class="onl__body">
            <!-- ============ ① 填写 ============ -->
            <template v-if="phase === 'form'">
              <div class="onl__tip">
                不知道怎么写？<button class="onl__tip-btn" @click="fillSample">载入示例简历</button>，直接拿去测试也行。
              </div>

              <div class="grid2">
                <label class="fld">
                  <span class="fld__label">姓名</span>
                  <input v-model="form.name" class="fld__input" placeholder="张三" />
                </label>
                <label class="fld">
                  <span class="fld__label">求职意向</span>
                  <input v-model="form.target" class="fld__input" placeholder="Java 后端开发工程师" />
                </label>
              </div>

              <label class="fld">
                <span class="fld__label">教育经历</span>
                <input v-model="form.education" class="fld__input" placeholder="2022.09 - 2026.06  某某大学  计算机科学与技术  本科" />
              </label>

              <label class="fld">
                <span class="fld__label">专业技能 <span class="fld__hint">用顿号或逗号分隔</span></span>
                <input v-model="form.skills" class="fld__input" placeholder="Java、Spring Boot、MySQL、Redis" />
              </label>

              <div class="fld">
                <span class="fld__label">项目经历 <span class="fld__hint">出题会重点参考这一段</span></span>
                <div v-for="(p, i) in form.projects" :key="i" class="proj">
                  <input v-model="p.name" class="fld__input" placeholder="项目名称，如：校园二手交易平台" />
                  <textarea
                    v-model="p.desc"
                    class="fld__area"
                    rows="3"
                    placeholder="你负责了什么、用了哪些技术、解决了什么问题"
                  />
                  <button v-if="form.projects.length > 1" class="proj__del" @click="form.projects.splice(i, 1)">
                    删除这个项目
                  </button>
                </div>
                <button class="mini-btn" @click="form.projects.push({ name: '', desc: '' })">+ 再加一个项目</button>
              </div>

              <label class="fld">
                <span class="fld__label">实习 / 工作经历 <span class="fld__hint">选填</span></span>
                <textarea
                  v-model="form.experience"
                  class="fld__area"
                  rows="3"
                  placeholder="2025.07 - 2025.09  某某科技  后端开发实习生"
                />
              </label>

              <p v-if="error" class="onl__err">{{ error }}</p>
            </template>

            <!-- ============ ② 解析结果 ============ -->
            <template v-else>
              <p class="res__cap">
                识别到 <strong>{{ skills.length }}</strong> 个技能标签
              </p>
              <div class="res__tags">
                <span v-for="(s, i) in skills" :key="s" class="res__tag">
                  {{ s }}
                  <button class="res__tag-x" title="移除" @click="skills.splice(i, 1)">&times;</button>
                </span>
                <span v-if="!skills.length" class="res__empty">一个都没识别出来</span>
              </div>

              <div class="res__add">
                <input
                  v-model="draft"
                  class="fld__input"
                  placeholder="没识别到？手动补一个，如 Kafka"
                  @keyup.enter="addSkill"
                />
                <button class="mini-btn" @click="addSkill">添加</button>
              </div>

              <p class="res__note">
                技能识别靠本地技术词典，只认计算机常见术语（Java、MySQL、Docker 这类）。
                「沟通能力」「责任心」这种词识别不了，需要自己补——这些标签会直接影响出题方向。
              </p>
            </template>
          </div>

          <footer class="onl__foot">
            <template v-if="phase === 'form'">
              <button class="btn btn--ghost" @click="close">取消</button>
              <button class="btn btn--primary" :disabled="saving" @click="save">
                {{ saving ? '解析中…' : '保存并解析' }}
              </button>
            </template>
            <template v-else>
              <button class="btn btn--ghost" @click="phase = 'form'">返回修改</button>
              <button class="btn btn--primary" @click="finish">用这份简历</button>
            </template>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { saveResume } from '../../api'
import request from '../../utils/request'

/**
 * 在线简历：给没有简历文件的用户用的。
 *
 * 关键点是**不新增后端接口**——表单拼成一段简历风格的纯文本后走 POST /api/resume，
 * 由后端同一个 RuleBasedResumeAnalyzer 解析。这样得到的 skills / keywords / projects
 * 与上传文件那条路完全同构，出题、评分、报告整条链路一行都不用改。
 *
 * 代价是识别能力受限于本地技术词典（约 80 个术语），所以第二阶段允许手动补标签——
 * 用户补完要走 PUT /api/resume/tags 写回，否则补的标签只活在界面上。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const phase = ref('form')
const saving = ref(false)
const error = ref('')
const skills = ref([])
const draft = ref('')

const form = reactive({
  name: '',
  target: '',
  education: '',
  skills: '',
  experience: '',
  projects: [{ name: '', desc: '' }],
})

/* ------------------------------------------------------------------ */
/*  示例简历                                                           */
/* ------------------------------------------------------------------ */
// 用词刻意全部落在 RuleBasedResumeAnalyzer.TECH_DICT 里，保证点一下就能看到
// 「识别出 N 个技能」的完整效果。也顺带避开几个词典陷阱：
// 不写 JavaScript（会被 "Java" 子串命中）、不写 MongoDB（会被 "Go" 子串命中）。
const SAMPLE = {
  name: '张明',
  target: 'Java 后端开发工程师',
  education: '2022.09 - 2026.06  华中科技大学  计算机科学与技术  本科',
  skills: 'Java、Spring Boot、Spring Cloud、MyBatis、MySQL、Redis、Kafka、Docker、Linux、Git、Maven',
  experience:
    '2025.07 - 2025.09  某互联网公司  后端开发实习生\n' +
    '参与短信服务重构，引入消息队列做异步化，并对接口做了限流与熔断，超时率从 3% 降到 0.2%。',
  projects: [
    {
      name: '校园二手交易平台（后端负责人）',
      desc:
        '负责订单与支付模块。用 Spring Boot + MyBatis 搭建服务，MySQL 做持久化并对慢查询加了索引；\n' +
        'Redis 缓存热点商品，接口平均响应从 320ms 降到 90ms；\n' +
        '下单与库存扣减用本地事务加乐观锁保证一致性；\n' +
        'Kafka 异步处理订单通知，应付高峰期的高并发写入。',
    },
    {
      name: '分布式文件服务（主要开发）',
      desc:
        '基于 Spring Cloud 与 Nginx 做微服务拆分和多实例部署，用 Redis 做分布式锁与会话共享，Docker 打包上线；\n' +
        '做过一轮 JVM 调优，把 Full GC 频率从每小时 6 次降到 1 次以内。',
    },
  ],
}

function fillSample() {
  form.name = SAMPLE.name
  form.target = SAMPLE.target
  form.education = SAMPLE.education
  form.skills = SAMPLE.skills
  form.experience = SAMPLE.experience
  // 复制一份而不是直接引用：用户改完再点一次「载入示例」要能回到原样
  form.projects = SAMPLE.projects.map(p => ({ ...p }))
  error.value = ''
}

/* ------------------------------------------------------------------ */
/*  拼装 rawText                                                       */
/* ------------------------------------------------------------------ */
/**
 * 拼成简历风格的分段文本。分段不是排版需要——后端按 PROJECT_SPLIT
 * （项目经历 / 实习经历 / 工作经历…）切项目段落，标题词必须出现，否则 projects 会是空的。
 */
function buildRawText() {
  const parts = []
  if (form.name.trim()) parts.push(form.name.trim())
  if (form.target.trim()) parts.push('求职意向：' + form.target.trim())
  if (form.education.trim()) parts.push('教育经历\n' + form.education.trim())
  if (form.skills.trim()) parts.push('专业技能\n' + form.skills.trim())

  const projects = form.projects.filter(p => p.name.trim() || p.desc.trim())
  if (projects.length) {
    const body = projects
      .map((p, i) => {
        const head = `${i + 1}. ${p.name.trim() || '项目' + (i + 1)}`
        return p.desc.trim() ? head + '\n' + p.desc.trim() : head
      })
      .join('\n')
    parts.push('项目经历\n' + body)
  }

  if (form.experience.trim()) parts.push('实习经历\n' + form.experience.trim())
  return parts.join('\n\n')
}

/** 后端把 skills / keywords 以 JSON 字符串存在单列里，取回来得先解一层 */
function toList(v) {
  if (Array.isArray(v)) return v
  if (typeof v === 'string') {
    try {
      const parsed = JSON.parse(v)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function uniq(list) {
  const out = []
  for (const s of list) {
    if (s && !out.includes(s)) out.push(s)
  }
  return out
}

/* ------------------------------------------------------------------ */
/*  动作                                                               */
/* ------------------------------------------------------------------ */
async function save() {
  if (saving.value) return
  const rawText = buildRawText()
  // 后端只校验非空，写两个字也能存进去，但那对出题没有任何帮助，这里先拦一道
  if (rawText.replace(/\s/g, '').length < 20) {
    error.value = '内容太少了，至少填上姓名和一段项目经历'
    return
  }

  saving.value = true
  error.value = ''
  try {
    const data = await saveResume({ rawText })
    skills.value = uniq([...toList(data?.skills), ...toList(data?.keywords)])
    draft.value = ''
    phase.value = 'result'
  } catch (e) {
    error.value = e?.message || '解析失败，请重试'
  } finally {
    saving.value = false
  }
}

function addSkill() {
  const name = draft.value.trim()
  if (!name) return
  if (!skills.value.includes(name)) skills.value.push(name)
  draft.value = ''
}

async function finish() {
  if (saving.value) return
  saving.value = true
  try {
    // 手动补的标签只改了本地数组，必须写回——resume.skills 才是出题读的那份
    // （InterviewFlowService#extractTagsFromResume）。失败不拦着用户往下走，
    // 顶多是补的那几个标签不参与出题，本地展示仍然对得上。
    await request.put('/resume/tags', { tags: skills.value })
  } catch (e) {
    console.error('Failed to sync online resume tags', e)
  } finally {
    saving.value = false
  }
  emit('saved', { skills: [...skills.value] })
  close()
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.onl-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
}

.onl {
  width: min(680px, 100%);
  max-height: min(88vh, 860px);
  display: flex;
  flex-direction: column;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.onl__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--neutral-200);
  flex-shrink: 0;
}

.onl__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--neutral-900);
}

.onl__sub {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  color: var(--neutral-500);
  font-family: var(--font-body);
}

.onl__x {
  border: none;
  background: transparent;
  color: var(--neutral-400);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
  transition: color var(--duration-fast);
}

.onl__x:hover {
  color: var(--neutral-700);
}

.onl__body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-5) var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.onl__tip {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  font-size: var(--text-xs);
  color: var(--accent-700);
  font-family: var(--font-body);
}

.onl__tip-btn {
  border: none;
  background: transparent;
  padding: 0 2px;
  color: var(--accent-600);
  font-size: var(--text-xs);
  font-weight: 700;
  font-family: var(--font-body);
  text-decoration: underline;
  cursor: pointer;
}

.onl__err {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--color-error);
  font-family: var(--font-body);
}

/* ---------------- 表单 ---------------- */
.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.fld {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.fld__label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--neutral-700);
  font-family: var(--font-body);
}

.fld__hint {
  font-weight: 400;
  color: var(--neutral-400);
}

.fld__input,
.fld__area {
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--neutral-200);
  border-radius: var(--radius-md);
  background: var(--surface-elevated);
  font-size: var(--text-sm);
  font-family: var(--font-body);
  color: var(--neutral-800);
  line-height: 1.6;
  box-sizing: border-box;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
}

.fld__input:focus,
.fld__area:focus {
  outline: none;
  border-color: var(--accent-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.fld__area {
  resize: vertical;
  min-height: 72px;
}

.proj {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px dashed var(--neutral-200);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-2);
}

.proj__del {
  align-self: flex-start;
  border: none;
  background: transparent;
  padding: 0;
  font-size: var(--text-xs);
  color: var(--neutral-400);
  font-family: var(--font-body);
  cursor: pointer;
}

.proj__del:hover {
  color: var(--color-error);
}

.mini-btn {
  align-self: flex-start;
  padding: 5px var(--space-3);
  border: 1px dashed var(--accent-400);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--accent-600);
  font-size: var(--text-xs);
  font-family: var(--font-body);
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--duration-fast);
}

.mini-btn:hover {
  background: var(--accent-50);
}

/* ---------------- 解析结果 ---------------- */
.res__cap {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--neutral-600);
  font-family: var(--font-body);
}

.res__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.res__tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px var(--space-3);
  border-radius: var(--radius-full);
  background: var(--accent-50);
  border: 1px solid var(--accent-200);
  color: var(--accent-700);
  font-size: var(--text-xs);
  font-family: var(--font-body);
}

.res__tag-x {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--accent-400);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.res__tag-x:hover {
  color: var(--color-error);
}

.res__empty {
  font-size: var(--text-xs);
  color: var(--neutral-400);
  font-family: var(--font-body);
}

.res__add {
  display: flex;
  gap: var(--space-2);
}

.res__note {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--neutral-50);
  border: 1px solid var(--neutral-200);
  font-size: var(--text-xs);
  line-height: 1.7;
  color: var(--neutral-500);
  font-family: var(--font-body);
}

/* ---------------- 底栏 ---------------- */
.onl__foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--neutral-200);
  flex-shrink: 0;
}

.btn {
  padding: var(--space-3) var(--space-6);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  font-weight: 600;
  font-family: var(--font-body);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out-quart);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn--ghost {
  border: 1px solid var(--neutral-200);
  background: transparent;
  color: var(--neutral-600);
}

.btn--ghost:hover:not(:disabled) {
  border-color: var(--accent-500);
  color: var(--accent-600);
}

.btn--primary {
  border: none;
  background: var(--accent-500);
  color: #fff;
  box-shadow: var(--shadow-accent);
}

.btn--primary:hover:not(:disabled) {
  background: var(--accent-600);
}

/* ---------------- 进出场 ---------------- */
.onl-enter-active,
.onl-leave-active {
  transition: opacity var(--duration-normal) var(--ease-out-quart);
}

.onl-enter-active .onl,
.onl-leave-active .onl {
  transition: transform var(--duration-normal) var(--ease-out-quart);
}

.onl-enter-from,
.onl-leave-to {
  opacity: 0;
}

.onl-enter-from .onl,
.onl-leave-to .onl {
  transform: translateY(12px) scale(0.98);
}

@media (max-width: 640px) {
  .grid2 {
    grid-template-columns: 1fr;
  }

  .onl__body,
  .onl__foot,
  .onl__head {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }
}
</style>
