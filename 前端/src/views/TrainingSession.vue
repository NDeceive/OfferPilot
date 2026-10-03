<!--
THESIS: The AI interviewer hosts the exercise, but the code remains the visual and operational center.
OWN-WORLD: OfferPilot's light mint shell wraps one deep charcoal Monaco plane, with compact factual status rows and no gamified chrome.
STORY: Understand the prompt, implement, run transparent local examples, submit, answer a code-linked follow-up, then review.
FIRST VIEWPORT: A narrow interviewer/question rail beside a dominant editor and console, under one continuous four-stage toolbar.
FORM: Interview-led coding cockpit derived from the supplied reference, preserving the product navigation and real voice/avatar components.
-->
<template>
  <AppLayout>
    <main v-if="session" class="training-page">
      <header class="training-header">
        <div class="training-title">
          <button type="button" aria-label="返回专项刷题" @click="router.push('/learning')">←</button>
          <JobLogo class="session-role-logo" v-bind="rolePresentation" />
          <div><h1>面试式编程训练</h1><p>{{ shortRole }}<span v-if="session.company !== '不限公司'"><CompanyLogo :name="session.company" />{{ session.company }}定向</span></p></div>
        </div>
        <ol class="stage-indicator" aria-label="当前训练阶段">
          <li v-for="(item,index) in stages" :key="item.key" :class="{ active: stage === item.key, done: stageIndex > index }"><b>{{ stageIndex > index ? '✓' : index + 1 }}</b><span>{{ item.label }}</span></li>
        </ol>
        <div class="training-status"><span class="saved"><i></i>{{ saveLabel }}</span><strong>◷ {{ elapsedLabel }}</strong><button type="button" @click="endTraining">结束训练</button></div>
      </header>

      <div class="training-grid">
        <aside class="interview-rail">
          <section class="avatar-window" :class="{ compact: stage !== 'ASK' }">
            <DigitalHumanStage :text="spokenPrompt" :speech-key="speechKey" />
            <p>{{ interviewerCaption }}</p>
          </section>

          <section class="question-panel">
            <header><div><strong>当前题目</strong><span>{{ question.type === 'coding' ? '编程题' : '知识问答' }}</span><span>{{ question.difficulty }}</span><span>高频</span></div><button type="button" :aria-pressed="favorite" @click="favorite = !favorite">{{ favorite ? '★ 已收藏' : '☆ 收藏' }}</button></header>
            <h2>{{ question.title }}</h2>
            <p>{{ question.description }}</p>
            <ul><li v-for="item in question.constraints" :key="item">{{ item }}</li></ul>
            <code>{{ question.signature }}</code>
            <details><summary>展开完整题面（示例、数据范围）</summary><p v-for="item in question.examples" :key="item.input">输入：{{ item.input }}；预期：{{ item.expected }}</p></details>
          </section>

          <section class="dialogue-panel">
            <header><h3>{{ stage === 'REVIEW' ? '本题复盘' : '面试对话' }}</h3><span v-if="stage !== 'REVIEW'">最近 {{ dialogue.length }} 轮</span></header>
            <template v-if="stage !== 'REVIEW'">
              <div class="dialogue-list">
                <article v-for="(message,index) in dialogue.slice(-3)" :key="index" :class="message.role"><b>{{ message.role === 'ai' ? '面试官' : '我' }}</b><p>{{ message.text }}</p></article>
              </div>
              <div v-if="showClarify" class="choice-box"><strong>选择要确认的题意</strong><button v-for="item in clarifyOptions" :key="item" type="button" @click="clarify(item)">{{ item }}</button></div>
              <div v-if="showHint" class="choice-box"><strong>渐进提示 · 已记录 {{ hintCount }} 次</strong><p>{{ currentHint }}</p><button type="button" @click="nextHint">{{ hintCount < hints.length ? '下一条提示' : '已显示完整思路' }}</button></div>
              <MicrophoneControl v-if="stage === 'FOLLOW_UP'" :session-id="session.id" :transcript="transcript" @transcript="appendTranscript" @submit="submitVoiceAnswer" @skip="finishReview" />
              <footer v-else><button class="voice-button" type="button" @click="addThought">◉ 说明思路</button><button type="button" @click="showClarify = !showClarify; showHint = false">确认题意</button><button type="button" @click="requestHint">请求提示</button></footer>
            </template>
            <section v-else class="review-panel">
              <p><b>代码结果</b><span>{{ runResult?.passed ? '本地演示用例已完成' : '未运行真实 Judge' }}</span></p>
              <p><b>做得好的</b><span>能够识别哈希表的空间换时间思路，并完成对应实现。</span></p>
              <p><b>可以加强</b><span>继续练习对平均复杂度和边界条件的完整说明。</span></p>
              <button type="button" @click="nextQuestion">下一题 →</button><button type="button" @click="restartSimilar">再练一道同类题</button>
            </section>
          </section>
        </aside>

        <section class="coding-column">
          <div class="editor-card">
            <header><select aria-label="编程语言"><option>Java 17</option></select><span>◇ 已载入代码模板</span><nav><button type="button" @click="resetCode">↶ 重置</button><button type="button" @click="toggleEditorHelp">编辑器设置</button></nav></header>
            <MonacoCodeEditor v-model="code" :highlighted-lines="highlightedLines" />
            <footer><button class="run-button" type="button" @click="runCode">▷ 运行代码</button><button class="submit-button" type="button" @click="submitCode">提交代码</button><button type="button" @click="consoleTab = 'input'">{ } 自定义输入</button></footer>
          </div>

          <section class="console-card" :class="{ collapsed: stage === 'FOLLOW_UP' }">
            <nav><button v-for="tab in consoleTabs" :key="tab.key" type="button" :class="{ active: consoleTab === tab.key }" @click="consoleTab = tab.key">{{ tab.label }}</button><span v-if="runResult" class="demo-badge">本地演示检查</span></nav>
            <div v-if="consoleTab === 'tests'" class="test-output">
              <p v-if="!runResult" class="console-empty">点击“运行代码”检查示例。项目尚未接入真实 Judge，不会伪造耗时、内存或隐藏测试结果。</p>
              <template v-else><p class="result-note">{{ runResult.message }}</p><article v-for="item in runResult.cases" :key="item.name"><b>✓ {{ item.name }}</b><span>输入：{{ item.input }}</span><span>输出：{{ item.output }}</span><strong>{{ item.status }}</strong></article></template>
            </div>
            <div v-else-if="consoleTab === 'input'" class="custom-input"><label for="customInput">自定义输入</label><textarea id="customInput" v-model="customInput" placeholder="nums = [2,7,11,15], target = 9"></textarea><p>未接入 Judge 时仅保存输入，不生成伪执行结果。</p></div>
            <div v-else class="submission-list"><p v-if="!submissions.length">暂无提交记录。</p><p v-for="item in submissions" :key="item.time"><span>{{ item.time }}</span><b>{{ item.label }}</b></p></div>
          </section>
        </section>
      </div>
    </main>
  </AppLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '../components/layout/AppLayout.vue'
import DigitalHumanStage from '../components/interview/DigitalHumanStage.vue'
import MicrophoneControl from '../components/interview/MicrophoneControl.vue'
import JobLogo from '../components/jobs/JobLogo.vue'
import CompanyLogo from '../components/learning/CompanyLogo.vue'
import MonacoCodeEditor from '../components/learning/MonacoCodeEditor.vue'
import { getTrainingSession, runLocalDemo, saveTrainingSession } from '../services/learningResources'
import { getJobPresentation } from '../utils/jobPresentation'

const route = useRoute()
const router = useRouter()
const session = ref(null)
const code = ref('')
const stage = ref('ASK')
const saveLabel = ref('已自动保存')
const elapsed = ref(0)
const favorite = ref(false)
const showClarify = ref(false)
const showHint = ref(false)
const hintCount = ref(0)
const consoleTab = ref('tests')
const runResult = ref(null)
const customInput = ref('')
const submissions = ref([])
const speechKey = ref(0)
const spokenPrompt = ref('')
const transcript = ref('')
const highlightedLines = ref([])
const dialogue = ref([{ role: 'ai', text: '给定一个整数数组和目标值，请返回和为 target 的两个整数下标。你可以先说一下思路。' }])
const stages = [{ key: 'ASK', label: '理解题目' }, { key: 'CODING', label: '现场编程' }, { key: 'FOLLOW_UP', label: '面试追问' }, { key: 'REVIEW', label: '本题完成' }]
const consoleTabs = [{ key: 'tests', label: '测试结果' }, { key: 'input', label: '自定义输入' }, { key: 'submissions', label: '提交记录' }]
const clarifyOptions = ['输入一定有解吗？', '可以使用额外空间吗？', '下标顺序有要求吗？']
const hints = ['遍历数组时，考虑如何快速查找 target - nums[i]。', '用 HashMap 记录已遍历元素的值与下标。', '先查找补数，再将当前元素放入 Map，可避免重复使用同一下标。']
let timer
let saveTimer

const question = computed(() => session.value.questionList[session.value.currentQuestionIndex])
const shortRole = computed(() => session.value.role.name.replace('开发工程师', '').trim())
const rolePresentation = computed(() => { const item = getJobPresentation(session.value?.role || {}); return { iconKey: item.iconKey, tone: item.themeKey } })
const stageIndex = computed(() => stages.findIndex(item => item.key === stage.value))
const elapsedLabel = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2,'0')}:${String(elapsed.value % 60).padStart(2,'0')}`)
const currentHint = computed(() => hints[Math.max(0, hintCount.value - 1)] || '')
const interviewerCaption = computed(() => stage.value === 'FOLLOW_UP' ? '请解释你的复杂度分析与技术取舍。' : '你可以先说一下思路，或者直接开始编写代码。')

onMounted(() => {
  session.value = getTrainingSession(route.params.sessionId)
  if (!session.value) { router.replace('/learning'); return }
  code.value = session.value.code || question.value.starter
  stage.value = session.value.stage || 'ASK'
  spokenPrompt.value = dialogue.value[0].text
  timer = setInterval(() => elapsed.value++, 1000)
})

watch(code, value => {
  if (!session.value) return
  saveLabel.value = '正在保存…'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { session.value.code = value; saveTrainingSession(session.value); saveLabel.value = '已自动保存' }, 500)
})

function speak(text) { spokenPrompt.value = text; speechKey.value++ }
function addThought() { dialogue.value.push({ role: 'me', text: '我准备使用 HashMap，在遍历时检查 target - nums[i] 是否已经出现。' }, { role: 'ai', text: '思路正确，可以开始实现。' }); stage.value = 'CODING'; speak('思路正确，可以开始实现。') }
function clarify(text) { dialogue.value.push({ role: 'me', text }, { role: 'ai', text: '输入保证有唯一解，可以使用额外空间，返回下标顺序不限。' }); showClarify.value = false; stage.value = 'CODING' }
function requestHint() { showHint.value = true; showClarify.value = false; nextHint() }
function nextHint() { if (hintCount.value < hints.length) hintCount.value++ }
function runCode() { stage.value = 'CODING'; consoleTab.value = 'tests'; runResult.value = runLocalDemo(code.value, question.value.examples) }
function submitCode() {
  runCode()
  submissions.value.unshift({ time: new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}), label: runResult.value.passed ? '本地演示检查完成' : '待继续修改' })
  if (!runResult.value.passed) { dialogue.value.push({ role: 'ai', text: '当前本地示例检查尚未完成，可以继续检查实现与边界情况。' }); return }
  stage.value = 'FOLLOW_UP'
  const followUp = question.value.followUps[0]
  highlightedLines.value = [followUp.lineStart, followUp.lineEnd]
  dialogue.value.push({ role: 'ai', text: followUp.text })
  speak(followUp.text)
  session.value.stage = stage.value; saveTrainingSession(session.value)
}
function appendTranscript(text) { transcript.value += text; dialogue.value.push({ role: 'me', text }) }
function submitVoiceAnswer() { dialogue.value.push({ role: 'ai', text: '回答已记录。哈希表的平均 O(1) 来自哈希定位与良好的冲突控制。' }); finishReview() }
function finishReview() { stage.value = 'REVIEW'; highlightedLines.value = []; session.value.stage = stage.value; session.value.completed = Math.min(session.value.questionCount, (session.value.completed || 0) + 1); saveTrainingSession(session.value) }
function resetCode() { if (confirm('确定恢复初始代码模板吗？')) code.value = question.value.starter }
function toggleEditorHelp() { alert('Monaco 已启用语法高亮、自动缩进、括号匹配、搜索与 Tab 缩进。') }
function nextQuestion() { router.push('/learning') }
function restartSimilar() { code.value = question.value.starter; stage.value = 'ASK'; runResult.value = null; submissions.value = []; dialogue.value = dialogue.value.slice(0,1) }
function endTraining() { if (confirm('结束当前训练并返回学习资源吗？')) router.push('/learning') }

onBeforeUnmount(() => { clearInterval(timer); clearTimeout(saveTimer) })
</script>

<style scoped>
.training-page{width:min(1510px,calc(100vw - 34px));margin:0 auto;padding:12px 0 18px;color:#17231f}.training-header{min-height:60px;display:grid;grid-template-columns:330px 1fr 320px;align-items:center;gap:16px}.training-title{display:flex;align-items:center;gap:8px}.training-title>button{width:34px;height:34px;border:1px solid #dfe6e3;border-radius:8px;background:#fff}:deep(.session-role-logo.job-logo){width:34px;height:34px;border-radius:9px}:deep(.session-role-logo.job-logo img),:deep(.session-role-logo.job-logo svg){width:22px;height:22px}.training-title h1{font-size:19px}.training-title p{display:flex;align-items:center;gap:6px;color:#75817c;font-size:10px}.training-title p span{margin-left:2px;display:inline-flex;align-items:center;gap:5px}.training-title p span :deep(.company-logo){width:20px;height:20px;border-radius:6px}.training-title p span :deep(.company-logo svg){width:13px;height:13px}.stage-indicator{display:flex;justify-content:center;list-style:none}.stage-indicator li{position:relative;min-width:108px;display:flex;align-items:center;gap:6px;color:#9aa39f;font-size:10px}.stage-indicator li:not(:last-child)::after{content:"";position:absolute;left:77px;right:6px;top:12px;height:1px;background:#dbe3e0}.stage-indicator b{display:grid;width:24px;height:24px;place-items:center;border-radius:50%;background:#e5e9e7}.stage-indicator .active,.stage-indicator .done{color:#178d63;font-weight:700}.stage-indicator .active b,.stage-indicator .done b{background:var(--accent-500);color:#fff}.training-status{display:flex;align-items:center;justify-content:flex-end;gap:12px}.training-status span,.training-status strong{font-size:10px}.training-status span{color:#5d6d67}.training-status i{display:inline-block;width:6px;height:6px;margin-right:5px;border-radius:50%;background:var(--accent-500)}.training-status button{padding:8px 13px;border:1px solid #ef8a86;border-radius:8px;background:#fff;color:#d74742;font-size:10px;font-weight:700}.training-grid{display:grid;grid-template-columns:minmax(335px,31%) minmax(0,69%);gap:12px;height:calc(100dvh - 154px);min-height:680px}.interview-rail,.coding-column{min-height:0;display:grid;gap:10px}.interview-rail{grid-template-rows:176px auto minmax(210px,1fr)}.avatar-window{position:relative;min-height:0}.avatar-window.compact{height:150px}.avatar-window>p{position:absolute;left:10px;right:10px;bottom:8px;margin:0;padding:6px 9px;border-radius:6px;background:rgba(8,18,15,.72);color:#fff;font-size:10px;text-align:center;z-index:6}.question-panel,.dialogue-panel,.editor-card,.console-card{overflow:hidden;border:1px solid #dde6e2;border-radius:12px;background:#fff}.question-panel{padding:13px 14px}.question-panel header,.dialogue-panel header,.editor-card>header,.console-card>nav{display:flex;align-items:center;justify-content:space-between}.question-panel header>div{display:flex;align-items:center;gap:6px}.question-panel header strong{font-size:11px}.question-panel header span{padding:3px 7px;border-radius:5px;background:#f0f5f3;color:#67756f;font-size:9px}.question-panel header span:first-of-type{background:#e8f2ff;color:#3470b8}.question-panel header button{border:0;background:transparent;color:#3e5149;font-size:10px}.question-panel h2{margin:8px 0 3px;font-size:16px}.question-panel>p,.question-panel li,.question-panel details{color:#5d6c67;font-size:10px;line-height:1.55}.question-panel ul{margin:5px 0 7px;padding-left:18px}.question-panel code{display:block;padding:6px 8px;overflow:auto;border:1px solid #e1e7e5;border-radius:6px;background:#f8faf9;color:#165bd0;font:10px var(--font-mono)}.question-panel details{margin-top:7px}.question-panel details p{margin-top:5px}.dialogue-panel{min-height:0;padding:12px;display:flex;flex-direction:column}.dialogue-panel h3{font-size:13px}.dialogue-panel header span{color:#82908a;font-size:9px}.dialogue-list{min-height:0;overflow:auto;margin-top:8px}.dialogue-list article{display:grid;grid-template-columns:48px 1fr;gap:6px;padding:7px 0;border-top:1px solid #edf0ef}.dialogue-list article.me{margin:2px 0;padding:7px;border:0;border-radius:7px;background:#edf6ff}.dialogue-list b{font-size:9px}.dialogue-list p{margin:0;color:#4f6059;font-size:10px;line-height:1.45}.dialogue-panel>footer{display:grid;grid-template-columns:1.35fr 1fr 1fr;gap:6px;margin-top:auto;padding-top:8px}.dialogue-panel>footer button,.choice-box button,.review-panel button{min-height:34px;border:1px solid #dce5e1;border-radius:7px;background:#fff;color:#34473f;font-size:10px;font-weight:650}.dialogue-panel>footer .voice-button,.review-panel button:first-of-type{border:0;background:var(--accent-500);color:#fff}.choice-box{margin-top:8px;padding:8px;display:grid;gap:5px;border-radius:8px;background:#f6faf8}.choice-box strong,.choice-box p{font-size:10px}.choice-box p{color:#52645d}.review-panel{display:grid;gap:8px;margin-top:10px}.review-panel p{display:grid;gap:3px;margin:0;padding:8px;border-radius:7px;background:#f7faf8}.review-panel b{font-size:10px}.review-panel span{color:#60716a;font-size:10px}.coding-column{grid-template-rows:minmax(420px,1.55fr) minmax(210px,.75fr)}.editor-card{display:grid;grid-template-rows:46px minmax(0,1fr) 54px}.editor-card>header{padding:0 12px;background:#263342;color:#dce8e4}.editor-card select{padding:7px 24px 7px 9px;border:1px solid #43515e;border-radius:7px;background:#344250;color:#fff}.editor-card header>span{margin-right:auto;margin-left:14px;color:#b9c9c4;font-size:10px}.editor-card nav{display:flex;gap:10px}.editor-card nav button{border:0;background:transparent;color:#c4d0cc;font-size:10px}.editor-card>footer{padding:9px 12px;display:flex;align-items:center;gap:8px}.editor-card>footer button{min-height:36px;padding:0 17px;border:1px solid #dce5e1;border-radius:7px;background:#fff;color:#31423b;font-size:11px;font-weight:700}.editor-card>footer .run-button{border-color:var(--accent-500);color:#07885b}.editor-card>footer .submit-button{border:0;background:var(--accent-500);color:#fff}.editor-card>footer button:last-child{margin-left:auto}.console-card{min-height:0;}.console-card>nav{height:43px;padding:0 14px;border-bottom:1px solid #e7ecea;justify-content:flex-start;gap:22px}.console-card nav button{align-self:stretch;border:0;border-bottom:2px solid transparent;background:transparent;color:#45574f;font-size:11px}.console-card nav button.active{border-bottom-color:#10a873;color:#07885b;font-weight:700}.demo-badge{margin-left:auto;padding:3px 7px;border-radius:5px;background:#fff4e3;color:#9c631a;font-size:9px}.test-output,.custom-input,.submission-list{height:calc(100% - 43px);padding:12px 16px;overflow:auto}.console-empty,.result-note,.custom-input p,.submission-list p{color:#687770;font-size:10px}.result-note{padding:6px 8px;border-radius:6px;background:#fff7e8;color:#8a611f}.test-output article{min-height:38px;display:grid;grid-template-columns:90px 1.2fr .8fr 90px;align-items:center;gap:8px;border-bottom:1px solid #edf0ef;font-size:10px}.test-output article b,.test-output article strong{color:#0a9564}.custom-input{display:grid;grid-template-rows:auto 1fr auto;gap:7px}.custom-input label{font-size:11px;font-weight:700}.custom-input textarea{padding:9px;resize:none;border:1px solid #dce5e1;border-radius:7px;font:11px var(--font-mono)}.submission-list p{display:flex;justify-content:space-between;padding:7px;border-bottom:1px solid #edf0ef}
@media(max-width:1100px){.training-header{grid-template-columns:260px 1fr}.training-status{grid-column:1/-1}.training-grid{height:auto;grid-template-columns:1fr}.interview-rail{grid-template-rows:180px auto auto}.coding-column{min-height:720px}.avatar-window.compact{height:180px}}
@media(max-width:700px){.training-page{width:calc(100vw - 20px)}.training-header{grid-template-columns:1fr}.stage-indicator{justify-content:flex-start;overflow:auto}.stage-indicator li{min-width:98px}.training-status{justify-content:flex-start;flex-wrap:wrap}.training-grid{min-height:0}.coding-column{min-height:680px}.editor-card>header span,.editor-card nav button:last-child{display:none}.test-output article{grid-template-columns:1fr}.dialogue-panel>footer{grid-template-columns:1fr}}
</style>
