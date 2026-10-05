<template>
  <AppLayout>
    <div
      class="dashboard"
      :class="{ ready: pageReady }"
    >

      <section v-if="loading" class="loading-state" role="status" aria-live="polite" aria-label="正在加载首页数据">
        <span v-for="i in 7" :key="i"></span>
      </section>

      <section v-else-if="errorMessage" class="error-state" role="alert">
        <div class="error-mark">!</div>
        <h1>首页数据暂时无法加载</h1>
        <p>{{ errorMessage }}</p>
        <button type="button" @click="loadOverview">重新加载</button>
      </section>

      <template v-else>
        <header class="today-bar reveal" style="--delay:0ms">
          <div><strong>今日训练</strong><span>{{ todayLabel }}</span></div>
          <p><i></i>数据已同步&nbsp;&nbsp;{{ updatedAt }}</p>
        </header>

        <div class="top-grid">
          <section
            class="advice-card card-light reveal"
            style="--delay:0ms"
          >
            <div class="coach-portrait">
              <span>AI 面试教练</span>
              <img :src="coachPortrait" alt="身穿浅绿色西装的虚拟面试教练" width="1024" height="1536" fetchpriority="high" />
            </div>
            <div class="advice-copy">
              <span class="advice-eyebrow">你的下一步训练</span>
              <h1>{{ overview.nextAction.title }}</h1>
              <p>{{ overview.nextAction.description }}</p>
              <div v-if="overview.latestInsight" class="coach-feedback">
                <p class="feedback-source">依据最近报告 · {{ currentJob }}</p>
                <div v-if="overview.latestInsight.weakestDimension" class="feedback-focus">
                  <span>优先加强</span><strong>{{ weaknessLabel }}</strong>
                  <span v-if="overview.latestInsight.weakestScore != null">{{ formatScore(overview.latestInsight.weakestScore) }} 分</span>
                </div>
                <p>{{ overview.latestInsight.suggestion || '查看报告中的维度反馈，选择下一次训练重点。' }}</p>
              </div>
              <p v-else class="coach-empty">完成首次面试后，这里会显示你的薄弱维度和改进建议。</p>
              <div class="action-row">
                <router-link
                  :to="overview.nextAction.route || '/interview/ai'"
                  class="primary-btn"
                >
                  {{ nextActionLabel }}
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"/></svg>
                </router-link>
                <router-link v-if="overview.latestInsight?.reportId" :to="latestReportRoute" class="secondary-btn">查看完整报告</router-link>
              </div>
            </div>
          </section>

          <section
            class="review-card panel card-light reveal"
            style="--delay:60ms"
          >
            <header class="panel-head">
              <div><h2>最近一次复盘</h2><strong>{{ currentJob }}</strong></div>
              <router-link :to="latestReportRoute">查看完整报告 <span>→</span></router-link>
            </header>

            <div v-if="overview.latestInsight" class="review-list">
              <article class="review-item needs">
                <span>需要加强</span>
                <strong>{{ weaknessLabel }}</strong>
                <p>{{ overview.latestInsight.suggestion || '建议围绕薄弱维度进行一次专项训练。' }}</p>
              </article>
              <article class="review-item good">
                <span>做得好的</span>
                <strong>{{ strengthLabel }}</strong>
                <p>最近一次训练中，该维度表现相对更稳定。</p>
              </article>
              <article class="review-item next">
                <span>下一步</span>
                <strong>针对高频考点进行专项练习</strong>
                <p>继续围绕最近报告中的真实反馈安排训练。</p>
              </article>
            </div>
            <div v-else class="review-empty">
              <strong>完成一次训练后生成复盘</strong>
              <p>报告会展示优势、薄弱维度和下一步练习建议。</p>
              <router-link to="/interview/ai">开始训练</router-link>
            </div>
          </section>
        </div>

        <div class="middle-grid">
          <section class="quick-panel panel reveal" style="--delay:100ms">
            <header class="quick-heading"><h2>快速开始</h2><p>选择一种方式进入下一次训练。</p></header>
            <div class="quick-grid">
              <router-link to="/interview/ai" class="quick-card prediction">
                <header><strong>面试押题</strong><span>›</span></header>
                <div class="mini-ui">
                  <p><span>目标企业</span><b>字节跳动</b></p>
                  <p><span>岗位</span><b>{{ shortJob }}</b></p>
                  <p><span>个人简历</span><b>{{ resumeLabel }}</b></p>
                </div>
                <div class="sorting"><i></i><span>正在整理岗位要求…</span></div>
              </router-link>

              <router-link to="/interview/ai" class="quick-card prepare">
                <header><strong>准备新岗位</strong><span>›</span></header>
                <div class="mini-ui">
                  <p><span>目标岗位</span><b>{{ shortJob }}</b><i>⌄</i></p>
                  <p><span>简历</span><b>{{ resumeLabel }}</b><i>⌄</i></p>
                  <p><span>训练目标</span><b>{{ weaknessLabel }}</b><i>⌄</i></p>
                </div>
              </router-link>

              <router-link to="/learning" class="quick-card drill">
                <header><strong>刷题训练</strong><span>›</span></header>
                <div class="question-list">
                  <p v-for="(question, index) in practiceQuestions" :key="question"><b>Q{{ index + 1 }}</b><span>{{ question }}</span></p>
                </div>
                <em>开始 →</em>
              </router-link>
            </div>
          </section>

          <section
            class="profile-card panel card-light reveal"
            style="--delay:140ms"
          >
            <span class="profile-paper profile-paper-one" aria-hidden="true"></span>
            <span class="profile-paper profile-paper-two" aria-hidden="true"></span>
            <header class="panel-head">
              <h2>当前训练档案</h2>
              <router-link to="/jobs">管理档案 <span>→</span></router-link>
            </header>
            <div class="profile-title">
              <strong>{{ currentJob }}</strong>
              <span>{{ resume ? '已准备' : '待完善' }}</span>
            </div>
            <div v-if="profileTags.length" class="tag-list"><span v-for="tag in profileTags" :key="tag">{{ tag }}</span></div>
            <div v-else class="tag-list"><span>完成简历后生成技能标签</span></div>
            <div class="resume-row">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 2h8l4 4v16H6zM14 2v5h5M9 12h6M9 16h6"/></svg>
              <div><strong>{{ resumeLabel }}</strong><small>{{ resumeUpdatedLabel }}</small></div>
            </div>
            <div class="profile-actions">
              <router-link to="/interview/ai" class="primary-btn">继续准备</router-link>
              <router-link to="/jobs" class="secondary-btn">更换岗位</router-link>
            </div>
          </section>
        </div>

        <section class="recent-panel panel reveal" style="--delay:180ms">
          <header class="panel-head">
            <h2>最近训练</h2>
            <router-link to="/history">查看全部 <span>→</span></router-link>
          </header>
          <div v-if="recentRows.length" class="training-list">
            <article v-for="item in recentRows" :key="item.sessionId" class="training-row">
              <i :class="statusClass(item.status)"></i>
              <strong>{{ item.jobName }}</strong>
              <span>{{ formatDate(item.startTime) }} · {{ formatDuration(item.durationSeconds) }}</span>
              <b v-if="item.score !== null && item.score !== undefined">{{ formatScore(item.score) }}</b>
              <span v-else class="generating">报告生成中<em></em></span>
              <router-link :to="recordRoute(item)">{{ recordAction(item) }} <span>→</span></router-link>
            </article>
          </div>
          <div v-else class="recent-empty"><span>暂无训练记录</span><router-link to="/interview/ai">开始第一次训练 →</router-link></div>
        </section>

        <section class="overview-strip reveal" style="--delay:220ms">
          <div class="overview-label"><strong>训练概览</strong><span>累计训练，稳步提升</span></div>
          <div class="metric"><strong>{{ overview.summary.completedCount }}</strong><span>累计训练</span></div>
          <div class="metric"><strong>{{ overview.summary.recentCount }}</strong><span>近 30 天</span></div>
          <div class="metric accent"><strong>{{ bestScoreLabel }}</strong><span>最佳表现</span></div>
          <div class="metric"><strong>{{ overview.summary.streakDays }} 天</strong><span>连续训练</span></div>
          <p>每一次模拟面试，都是更好的自己。</p>
        </section>
      </template>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import coachPortrait from '../assets/generated/interview-coach.png'
import AppLayout from '../components/layout/AppLayout.vue'
import { getDashboardOverview, getMyResume } from '../api'

const loading = ref(true)
const errorMessage = ref('')
const updatedAt = ref('')
const pageReady = ref(false)
const resume = ref(null)
const overview = ref({
  summary: { completedCount: 0, recentCount: 0, averageScore: 0, bestScore: 0, bestJobName: '', streakDays: 0 },
  nextAction: { type: '', title: '', description: '', route: '/interview/ai' },
  trend: [],
  recentInterviews: [],
  latestInsight: null,
})

const todayLabel = computed(() => new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', weekday: 'short' }).format(new Date()))
const currentJob = computed(() => overview.value.latestInsight?.jobName || overview.value.recentInterviews?.[0]?.jobName || '尚未选择目标岗位')
const shortJob = computed(() => currentJob.value.replace('开发工程师', '').trim())
const latestReportRoute = computed(() => overview.value.latestInsight?.reportId ? `/history/${overview.value.latestInsight.reportId}` : '/history')
const weaknessLabel = computed(() => shortDimension(overview.value.latestInsight?.weakestDimension) || '专项能力')
const strengthLabel = computed(() => shortDimension(overview.value.latestInsight?.strongestDimension) || '等待训练反馈')
const nextActionLabel = computed(() => ({
  COMPLETE_RESUME: '完善简历', FIRST_INTERVIEW: '开始首次面试',
  TARGETED_PRACTICE: '开始针对性训练', START_INTERVIEW: '开始新面试',
}[overview.value.nextAction.type] || '开始训练'))
const paperItems = computed(() => {
  const dimensions = overview.value.latestInsight?.dimensions?.map(item => shortDimension(item.dimension)).filter(Boolean) || []
  return [...new Set(dimensions)].slice(0, 3).length ? [...new Set(dimensions)].slice(0, 3) : ['岗位知识', '项目表达', '逻辑结构']
})
const recentRows = computed(() => overview.value.recentInterviews.slice(0, 3))
const bestScoreLabel = computed(() => overview.value.summary.bestScore ? formatScore(overview.value.summary.bestScore) : '—')
const resumeLabel = computed(() => resume.value ? '个人简历' : '尚未上传')
const resumeUpdatedLabel = computed(() => resume.value?.updateTime ? `上次更新距今${daysAgo(resume.value.updateTime)}` : '完善简历后匹配真实经历')
const profileTags = computed(() => parseList(resume.value?.skills || resume.value?.keywords).slice(0, 4))
const practiceQuestions = computed(() => {
  const source = [...paperItems.value, ...profileTags.value]
  return [...new Set(source)].slice(0, 3).map(item => `${item}专项练习`)
})

async function loadOverview() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [overviewData, resumeData] = await Promise.all([
      getDashboardOverview(),
      getMyResume().catch(() => null),
    ])
    overview.value = overviewData
    resume.value = resumeData
    updatedAt.value = new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date())
    await nextTick()
    requestAnimationFrame(() => { pageReady.value = true })
  } catch (error) {
    errorMessage.value = error.response?.data?.message || (error.request ? '暂时无法连接服务，请稍后重试。' : '首页数据处理失败，请重新加载。')
  } finally {
    loading.value = false
  }
}

function parseList(value) {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter(Boolean) : []
  } catch {
    return String(value).split(/[,，、]/).map(item => item.trim()).filter(Boolean)
  }
}

function shortDimension(value) {
  return String(value || '').replace('能力', '').replace('程度', '').replace('掌握', '')
}

function formatScore(value) {
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(1)
}

function formatDuration(seconds) {
  return `${Math.max(1, Math.round(Number(seconds || 0) / 60))} 分钟`
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  const diff = Date.now() - date.getTime()
  if (diff < 86400000) return '今天'
  if (diff < 172800000) return '昨天'
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(date)
}

function daysAgo(value) {
  const days = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 86400000))
  return days ? `${days} 天` : '不到 1 天'
}

function statusClass(status) {
  return { FINISHED: 'finished', ONGOING: 'ongoing', ABORTED: 'aborted' }[status] || 'aborted'
}

function recordRoute(item) {
  if (item.status === 'ONGOING') return '/interview'
  return item.reportId ? `/history/${item.reportId}` : '/history'
}

function recordAction(item) {
  return item.status === 'ONGOING' ? '继续训练' : item.reportId ? '查看复盘' : '查看'
}

onMounted(loadOverview)
</script>

<style scoped>
.dashboard{--pointer-x:70%;--pointer-y:20%;--slow-x:25%;--slow-y:70%;--line-x:0px;--line-y:0px;position:relative;isolation:isolate;max-width:1300px;margin:0 auto;padding:18px 0 26px;color:#17201d;font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif}.dashboard::before{content:"";position:absolute;z-index:-5;top:0;bottom:0;left:50%;width:100vw;transform:translateX(-50%);background:var(--surface-primary)}.today-bar{display:flex;align-items:center;justify-content:space-between;min-height:42px}.today-bar>div{display:flex;align-items:baseline;gap:13px}.today-bar strong{font-size:16px}.today-bar span,.today-bar p{color:#64716c;font-size:12px}.today-bar p{margin:0}.today-bar p i{display:inline-block;width:8px;height:8px;margin-right:8px;border-radius:50%;background:#1bb276}
.top-grid,.middle-grid{display:grid;grid-template-columns:minmax(0,1.75fr) minmax(330px,1fr);gap:12px;margin-bottom:12px}.panel,.advice-card,.overview-strip{border:1px solid rgba(28,82,63,.1);border-radius:12px;background:rgba(255,255,255,.92);box-shadow:0 16px 38px rgba(30,75,61,.055)}.card-light{--card-x:50%;--card-y:50%;background:var(--surface-elevated);transition:border-color .2s ease,box-shadow .2s ease}.card-light:hover{border-color:rgba(47,186,136,.17);box-shadow:0 19px 44px rgba(30,75,61,.075)}
.advice-card{position:relative;min-height:292px;padding:0 24px 0 0;display:grid;grid-template-columns:minmax(180px,.8fr) minmax(0,1.8fr);align-items:center;gap:22px;overflow:hidden;transition:border-color .2s ease,box-shadow .2s ease}.advice-copy h1{max-width:16ch;margin:14px 0 11px;font-size:25px;line-height:1.25;letter-spacing:-.025em}.advice-copy>p{max-width:44ch;margin:0;color:#67736f;font-size:14px;line-height:1.75}.action-row,.profile-actions{display:flex;gap:10px;margin-top:25px}.primary-btn,.secondary-btn{min-height:44px;padding:0 22px;display:inline-flex;align-items:center;justify-content:center;gap:10px;border-radius:8px;font-size:14px;font-weight:700;text-decoration:none}.primary-btn{background:var(--accent-500);color:#fff;box-shadow:none;transition:background .2s ease,box-shadow .2s ease}.primary-btn:hover{background:var(--accent-600);box-shadow:none}.primary-btn svg{width:17px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}.secondary-btn{border:1px solid #dbe4e0;color:#273a34;background:#fff;transition:border-color .2s ease,background .2s ease}.secondary-btn:hover{border-color:#b8cec5;background:#fbfdfc}


.review-card,.profile-card{min-height:292px;padding:20px 22px;overflow:hidden}.panel-head{position:relative;z-index:2;display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-head h2,.quick-heading h2{margin:0;font-size:17px}.panel-head>div>strong{display:block;margin-top:10px;font-size:15px}.panel-head>a{color:#5d6b66;font-size:12px;font-weight:600;text-decoration:none}.review-list{margin-top:12px}.review-item{padding:7px 6px;transition:opacity .2s ease,background .2s ease}.review-list:hover .review-item{opacity:.7}.review-list .review-item:hover{opacity:1;background:rgba(47,186,136,.026)}.review-item.needs:hover{background:rgba(243,166,90,.04)}.review-item span{color:#71807a;font-size:10px}.review-item.needs span{color:#ef6c2f}.review-item.good span{color:#179c69}.review-item strong{display:block;margin:2px 0;font-size:13px}.review-item p{margin:0;color:#71807a;font-size:11px;line-height:1.45}.review-empty{min-height:205px;display:flex;flex-direction:column;align-items:flex-start;justify-content:center;color:#66746f}.review-empty strong{color:#24342e}.review-empty p{font-size:12px}.review-empty a{color:#168d61;font-size:12px;font-weight:700}
.quick-panel{padding:14px 18px}.quick-heading{display:flex;align-items:baseline;gap:14px;margin-bottom:10px}.quick-heading p{margin:0;color:#7a8581;font-size:11px}.quick-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.quick-card{position:relative;min-width:0;height:182px;padding:14px;overflow:hidden;border:1px solid #dfe8e4;border-radius:9px;background:#fbfdfc;color:#17201d;text-decoration:none;box-shadow:0 8px 18px rgba(30,75,61,.035);transition:border-color .2s ease,box-shadow .2s ease}.quick-card:hover{border-color:rgba(47,186,136,.3);box-shadow:0 13px 26px rgba(30,75,61,.07)}.quick-card>header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.quick-card>header strong{font-size:15px}.quick-card>header span{font-size:21px}.mini-ui,.question-list{padding:8px 10px;border:1px solid #e8edeb;border-radius:7px;background:rgba(255,255,255,.8)}.mini-ui p,.question-list p{min-width:0;margin:0;padding:7px 0;display:flex;align-items:center;gap:8px;border-bottom:1px solid #edf0ef;font-size:10px}.mini-ui p:last-child,.question-list p:last-child{border-bottom:0}.mini-ui span{color:#74817c}.mini-ui b,.question-list span{margin-left:auto;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mini-ui i{font-style:normal}.prepare:hover .mini-ui p:first-child{box-shadow:inset 0 0 0 1px rgba(47,186,136,.35);border-radius:5px}.sorting{margin-top:8px;opacity:0;transition:opacity .3s ease}.prediction:hover .sorting{opacity:1}.sorting span{color:#6d7c76;font-size:9px}.sorting i{display:inline-block;width:38%;height:2px;margin-right:6px;background:#20aa75;vertical-align:middle;transform:scaleX(0);transform-origin:left;transition:transform .5s ease}.prediction:hover .sorting i{transform:scaleX(1)}.question-list b{color:#657872}.question-list span{display:block}.drill em{position:absolute;right:15px;bottom:12px;color:#178e63;font-size:11px;font-style:normal;font-weight:700;opacity:0;transition:opacity .2s ease}.drill:hover em{opacity:1}.drill:hover .question-list p:first-child{border-radius:4px;background:rgba(47,186,136,.05)}
.profile-card{position:relative}.profile-paper{position:absolute;z-index:0;right:-20px;bottom:6px;width:120px;height:175px;border:1px solid rgba(38,121,91,.08);border-radius:7px;background:rgba(248,252,250,.72);transition:transform .45s cubic-bezier(.16,1,.3,1)}.profile-paper-one{transform:rotate(8deg) translateX(6px)}.profile-paper-two{right:10px;bottom:-3px;transform:rotate(13deg) translateX(18px);opacity:.65}.profile-card:hover .profile-paper-one{transform:rotate(8.4deg) translateX(9px)}.profile-card:hover .profile-paper-two{transform:rotate(13.8deg) translateX(24px)}.profile-title{position:relative;z-index:2;display:flex;align-items:center;gap:12px;margin-top:18px}.profile-title>strong{font-size:17px}.profile-title>span{padding:4px 9px;border-radius:999px;background:#e7f6ef;color:#168d61;font-size:10px;font-weight:700}.tag-list{position:relative;z-index:2;display:flex;flex-wrap:wrap;gap:7px;margin:12px 0}.tag-list span{padding:5px 9px;border-radius:7px;background:#f1f5f3;color:#5d6f68;font-size:10px}.resume-row{position:relative;z-index:2;width:72%;padding:10px 12px;display:flex;align-items:center;gap:10px;border:1px solid #dde6e2;border-radius:8px;background:rgba(255,255,255,.84)}.resume-row svg{width:25px;fill:none;stroke:#71839b;stroke-width:1.7}.resume-row strong,.resume-row small{display:block}.resume-row strong{font-size:12px}.resume-row small{margin-top:2px;color:#84908b;font-size:9px}.profile-actions{position:relative;z-index:2;margin-top:12px}.profile-actions .primary-btn,.profile-actions .secondary-btn{min-height:36px;padding-inline:18px;font-size:12px}
.recent-panel{padding:12px 20px;margin-bottom:12px}.training-list{margin-top:8px}.training-row{min-height:34px;display:grid;grid-template-columns:7px minmax(230px,1.2fr) minmax(160px,1fr) 110px 100px;align-items:center;gap:10px;border-top:1px solid #e8ecea;font-size:11px;transition:background .18s ease}.training-row:hover{background:rgba(47,186,136,.026)}.training-row>i{width:3px;height:18px;border-radius:2px;background:#94cdb8}.training-row>i.ongoing{background:#ff791f}.training-row>i.aborted{background:#c8ced0}.training-row>strong{transition:transform .18s ease}.training-row:hover>strong{transform:translateX(2px)}.training-row>span{color:#6d7975}.training-row>b{color:#168d61;font-size:13px}.training-row>a{justify-self:end;color:#586a64;font-weight:700;text-decoration:none}.training-row>a span{display:inline-block;transition:transform .18s ease}.training-row:hover>a span{transform:translateX(3px)}.generating{position:relative}.generating em{position:absolute;left:0;right:20px;bottom:-5px;height:2px;overflow:hidden;background:#edf1ef}.generating em::after{content:"";display:block;width:45%;height:100%;background:#a8cfc0;animation:reportLoading 1.2s ease-in-out infinite}
.overview-strip{min-height:62px;padding:10px 20px;display:grid;grid-template-columns:1.35fr repeat(4,.72fr) 1.35fr;align-items:center}.overview-label strong,.overview-label span{display:block}.overview-label strong{font-size:15px}.overview-label span{margin-top:3px;color:#71807a;font-size:10px}.metric{padding:0 18px;text-align:center;border-left:1px solid #dfe6e3}.metric strong,.metric span{display:block}.metric strong{font-size:15px}.metric span{margin-top:3px;color:#6d7975;font-size:10px}.metric.accent strong{color:#12875d}.overview-strip>p{margin:0;padding-left:22px;border-left:1px solid #dfe6e3;color:#5c6c67;font-size:11px}
.reveal{opacity:0;transform:translateY(14px)}.ready .reveal{animation:reveal .58s var(--delay) cubic-bezier(.22,1,.36,1) forwards}.loading-state{display:grid;grid-template-columns:1.75fr 1fr;gap:12px;padding-top:42px}.loading-state span{height:128px;border-radius:12px;background:linear-gradient(100deg,#edf2f0 25%,#f8faf9 45%,#edf2f0 65%);background-size:220% 100%;animation:loading 1.4s ease infinite}.loading-state span:first-child{height:292px}.loading-state span:nth-child(2){height:292px}.loading-state span:nth-child(n+6){grid-column:1/-1;height:64px}.error-state{max-width:520px;margin:120px auto;text-align:center}.error-mark{display:grid;width:52px;height:52px;margin:0 auto 16px;place-items:center;border-radius:14px;background:#f5e7e3;color:#a94f38;font-size:22px;font-weight:800}.error-state h1{margin:0 0 8px}.error-state p{color:#71847d}.error-state button{margin-top:14px;padding:11px 17px;border:0;border-radius:8px;background:var(--accent-500);color:#fff;font-weight:700;cursor:pointer}
@keyframes reveal{to{opacity:1;transform:translateY(0)}}@keyframes paperIn{from{opacity:0;transform:translateY(8px) scale(.99)}to{opacity:1;transform:none}}@keyframes orbitSpin{to{transform:rotate(360deg)}}@keyframes orbitSpinReverse{to{transform:rotate(-360deg)}}@keyframes loading{to{background-position-x:-220%}}@keyframes reportLoading{0%{transform:translateX(-100%)}100%{transform:translateX(260%)}}
@media(max-width:1050px){.top-grid,.middle-grid{grid-template-columns:1fr}.advice-card{grid-template-columns:minmax(180px,.8fr) minmax(0,1.8fr)}.review-card,.profile-card{min-height:auto}.quick-card{height:175px}.overview-strip{grid-template-columns:1fr repeat(4,.7fr)}.overview-strip>p{display:none}}
@media(max-width:760px){.dashboard{padding:10px 0 28px}.today-bar{align-items:flex-end}.today-bar p{display:none}.top-grid,.middle-grid{gap:10px;margin-bottom:10px}.advice-card{min-height:auto;padding:20px;grid-template-columns:1fr}.action-row{flex-wrap:wrap}.review-card,.profile-card,.quick-panel{padding:18px}.quick-grid{grid-template-columns:1fr}.quick-card{height:auto;min-height:164px}.training-row{padding:9px 0;grid-template-columns:5px minmax(0,1fr) auto}.training-row>span:not(.generating){grid-column:2}.training-row>b,.generating{grid-column:3;grid-row:1}.training-row>a{grid-column:3;grid-row:2}.overview-strip{grid-template-columns:1fr 1fr;padding:16px}.overview-label{grid-column:1/-1;margin-bottom:10px}.metric{padding:10px;border-left:0;border-top:1px solid #e4e9e7}.metric:nth-of-type(even){border-left:1px solid #e4e9e7}.profile-paper{opacity:.45}}

@media(prefers-reduced-motion:reduce){.profile-paper{transform:none!important}.reveal{opacity:1;transform:none}.ready .reveal,.generating em::after,.loading-state span{animation:none}}

.advice-card{gap:24px;min-height:360px;background:var(--surface-elevated)}
.coach-portrait{align-self:stretch;position:relative;display:flex;align-items:flex-end;justify-content:center;background:var(--surface-mint);overflow:hidden}
.coach-portrait>span{position:absolute;top:20px;left:20px;z-index:1;color:var(--accent-700);font-size:13px;font-weight:700}
.coach-portrait img{display:block;width:100%;height:320px;object-fit:contain;object-position:center bottom}
.advice-copy{min-width:0;padding:28px 0}
.advice-eyebrow{color:var(--accent-700);font-size:13px;font-weight:700}
.advice-copy h1{max-width:none;font-size:25px;line-height:1.4}
.coach-feedback{margin-top:18px;padding-top:14px;border-top:1px solid var(--neutral-200)}
.coach-feedback p,.coach-empty{margin:8px 0 0;font-size:13px;line-height:1.7;color:var(--neutral-600)}
.coach-feedback .feedback-source{font-size:12px;color:var(--neutral-500)}
.feedback-focus{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin-top:9px;font-size:13px}
.feedback-focus>span:first-child{color:var(--neutral-600)}
.feedback-focus strong{color:var(--accent-700)}
.advice-copy .action-row{flex-wrap:wrap;margin-top:20px}
.primary-btn:focus-visible,.secondary-btn:focus-visible{outline:2px solid var(--accent-700);outline-offset:3px}
.profile-actions .primary-btn,.profile-actions .secondary-btn{min-height:44px}
@media(max-width:760px){.advice-card{grid-template-columns:1fr;padding:0;gap:0}.coach-portrait{height:230px}.coach-portrait img{height:250px;width:auto}.advice-copy{padding:22px}.advice-copy h1{font-size:23px}}
</style>
