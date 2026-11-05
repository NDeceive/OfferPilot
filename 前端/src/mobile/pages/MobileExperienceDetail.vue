<!--
  经验详情：标题是唯一一级信息，正文按小节分层，互动固定在底部。
  点赞和收藏是同级次操作，不做成两个抢眼的主按钮；评论是阅读后的自然延伸。
-->
<template>
  <MobileShell :nav="false" :messages="false" title="经验详情">
    <router-link to="/experience" class="mexd-back">← 返回经验</router-link>

    <MobileState v-if="error" kind="error" title="这篇经验暂时打不开" :description="error" action="返回经验列表" @action="$router.push('/experience')" />

    <article v-else-if="post" class="mexd-article">
      <div class="mexd-meta">
        <span class="mexd-tag">{{ post.jobName }}</span>
        <span v-if="post.company">{{ post.company }}</span>
        <time>{{ post.publishedAt }}</time>
      </div>

      <h1>{{ post.title }}</h1>

      <div class="mexd-author">
        <span class="mexd-avatar">{{ post.author.slice(0, 1) }}</span>
        <div class="mobile-grow">
          <strong>{{ post.author }}</strong>
          <p>{{ post.authorTitle }}</p>
        </div>
        <span>{{ post.readMinutes }} 分钟读完</span>
      </div>

      <p class="mexd-excerpt">{{ post.excerpt }}</p>

      <section v-for="(section, index) in post.sections" :key="index" class="mexd-section">
        <h2 v-if="section.heading">{{ section.heading }}</h2>
        <p v-for="(paragraph, pIndex) in section.paragraphs" :key="pIndex">{{ paragraph }}</p>
      </section>

      <section v-if="post.tips?.length" class="mexd-tips" aria-label="可以直接带走的技巧">
        <h2>可以直接带走的技巧</h2>
        <ul>
          <li v-for="tip in post.tips" :key="tip">{{ tip }}</li>
        </ul>
      </section>

      <section class="mexd-practice" aria-label="相关练习">
        <div>
          <h2>用 5 道题练一下</h2>
        </div>
        <button type="button" :disabled="startingPractice" @click="startRelatedPractice">
          {{ startingPractice ? '正在组题…' : '开始 5 题 →' }}
        </button>
        <p v-if="practiceError" class="mexd-error" role="alert">{{ practiceError }}</p>
      </section>

      <section class="mexd-comments" aria-labelledby="mexd-comments-title">
        <header class="mexd-comments-head">
          <h2 id="mexd-comments-title">讨论</h2>
          <span>{{ comments.length }} 条</span>
        </header>
        <p v-if="!comments.length" class="mexd-comments-empty">还没有人留言。看完有共鸣，就说一句吧。</p>
        <article v-for="comment in comments" :key="comment.id" class="mexd-comment">
          <strong>{{ comment.author }}</strong>
          <p>{{ comment.content }}</p>
        </article>
        <form class="mexd-comment-form" @submit.prevent="submitComment">
          <label class="sr-only" for="experience-comment">写评论</label>
          <textarea id="experience-comment" v-model.trim="commentText" rows="2" maxlength="500" placeholder="写下你的补充或疑问…"></textarea>
          <button type="submit" :disabled="commentSaving || !commentText">{{ commentSaving ? '发送中…' : '发送' }}</button>
        </form>
        <p v-if="commentError" class="mexd-error" role="alert">{{ commentError }}</p>
      </section>
    </article>

    <div v-if="post" class="mexd-actionbar">
      <button type="button" class="mexd-action" :class="{ active: post.liked, pulsing: likePulse }" :aria-pressed="post.liked" @click="like">
        <span aria-hidden="true">♥</span><b>{{ post.likeCount }}</b>
      </button>
      <button type="button" class="mexd-action" :class="{ active: post.favorite }" :aria-pressed="post.favorite" @click="favorite">
        <span aria-hidden="true">{{ post.favorite ? '★' : '☆' }}</span><b>{{ post.favorite ? '已收藏' : '收藏' }}</b>
      </button>
    </div>
  </MobileShell>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MobileShell from '../components/MobileShell.vue'
import MobileState from '../components/MobileState.vue'
import '../styles/mobile.css'
import { addExperienceComment, getExperiencePost, toggleExperienceFavorite, toggleExperienceLike } from '../../services/experienceDemo'
import { createTrainingSession, loadLearningResources } from '../../services/learningResources'
import { useUserStore } from '../../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const post = ref(null)
const comments = ref([])
const error = ref('')
const commentText = ref('')
const commentSaving = ref(false)
const commentError = ref('')
const likePulse = ref(false)
const startingPractice = ref(false)
const practiceError = ref('')
let likePulseTimer = null

function load() {
  error.value = ''
  try {
    const result = getExperiencePost(String(route.params.id))
    post.value = result
    comments.value = result.comments
  } catch (e) {
    post.value = null
    error.value = e.message || '这篇经验暂时找不到了。'
  }
}

function like() {
  if (!post.value) return
  try {
    const result = toggleExperienceLike(post.value.id)
    post.value.liked = result.liked
    post.value.likeCount = result.likeCount
    likePulse.value = false
    nextTick(() => {
      requestAnimationFrame(() => {
        likePulse.value = true
        clearTimeout(likePulseTimer)
        likePulseTimer = setTimeout(() => { likePulse.value = false }, 380)
      })
    })
  } catch (e) { commentError.value = e.message }
}

function favorite() {
  if (!post.value) return
  try { post.value.favorite = toggleExperienceFavorite(post.value.id).favorite } catch (e) { commentError.value = e.message }
}

async function submitComment() {
  if (commentSaving.value || !commentText.value || !post.value) return
  commentSaving.value = true
  commentError.value = ''
  try {
    const comment = addExperienceComment(post.value.id, commentText.value, { nickname: store.nickname, username: store.username })
    comments.value.push(comment)
    post.value.commentCount = comments.value.length
    commentText.value = ''
  } catch (e) {
    commentError.value = e.message || '评论发送失败，请重试。'
  } finally {
    commentSaving.value = false
  }
}

/** 经验 → 5 题：按这篇经验的岗位直接组一组基础单选，形成「读—练」闭环。 */
async function startRelatedPractice() {
  if (startingPractice.value || !post.value) return
  startingPractice.value = true
  practiceError.value = ''
  try {
    const data = await loadLearningResources()
    const role = data.roles?.find(item => item.code === post.value.jobCode) || data.preferredRole || data.currentRole || data.roles?.[0]
    if (!role) {
      practiceError.value = '还没有可用岗位，请先到刷题页选择目标岗位。'
      return
    }
    const session = await createTrainingSession({
      role,
      company: post.value.company || '不限公司',
      topic: '综合高频',
      questionType: 'choice',
      questionCount: 5,
      difficulty: 'basic',
    })
    router.push(`/learning/session/${session.id}`)
  } catch (e) {
    practiceError.value = e?.message || '组题失败，可以先到专项刷题手动作一组。'
  } finally {
    startingPractice.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => clearTimeout(likePulseTimer))
</script>

<style scoped>
.mexd-back { display: inline-flex; min-height: 44px; align-items: center; color: var(--m-primary); font-size: 13px; font-weight: 600; }
.mexd-article { padding-bottom: 92px; animation: mexd-rise 440ms cubic-bezier(.16, 1, .3, 1) 20ms backwards; }
.mexd-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; color: var(--m-text-tertiary); font-size: 11.5px; line-height: 1.5; }
.mexd-meta time { margin-left: auto; white-space: nowrap; }
.mexd-tag { padding: 3px 7px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 5px; font-weight: 700; }
.mexd-article h1 { margin-top: 10px; font-size: 25px; line-height: 1.4; font-weight: 800; letter-spacing: -.035em; }
.mexd-author { display: flex; align-items: center; gap: 10px; margin-top: 18px; padding-bottom: 18px; border-bottom: 1px solid var(--m-border); }
.mexd-avatar { display: grid; width: 38px; height: 38px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 50%; font-size: 14px; font-weight: 800; place-items: center; }
.mexd-author strong { display: block; font-size: 13px; }
.mexd-author p { margin-top: 2px; color: var(--m-text-tertiary); font-size: 11.5px; }
.mexd-author > span:last-child { color: var(--m-text-tertiary); font-size: 11.5px; white-space: nowrap; }
.mexd-excerpt { margin-top: 18px; padding: 14px 16px; color: #3d5549; background: var(--m-surface-soft); border-radius: var(--m-radius-card); font-size: 14px; line-height: 1.75; }
.mexd-section { margin-top: 24px; }
.mexd-section h2 { margin-bottom: 8px; font-size: 18px; line-height: 1.45; font-weight: 800; letter-spacing: -.02em; }
.mexd-section p { color: #31443a; font-size: 15px; line-height: 1.9; }
.mexd-section p + p { margin-top: 12px; }
.mexd-tips { margin-top: 26px; padding: 16px; background: var(--m-accent-soft); border-radius: var(--m-radius-card); }
.mexd-tips h2 { font-size: 16px; font-weight: 800; }
.mexd-tips ul { display: grid; margin-top: 10px; gap: 8px; }
.mexd-tips li { position: relative; padding-left: 16px; color: #5c4429; font-size: 13.5px; line-height: 1.7; }
.mexd-tips li::before { position: absolute; left: 0; color: #b66b20; content: '·'; font-weight: 800; }
.mexd-practice {
  display: grid;
  margin-top: 24px;
  padding: 16px;
  gap: 12px;
  background: var(--m-surface-soft);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
}
.mexd-practice h2 { font-size: 16px; font-weight: 800; }
.mexd-practice p { margin-top: 4px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.65; }
.mexd-practice button {
  min-height: 44px;
  color: #fff;
  background: var(--m-primary);
  border: 0;
  border-radius: var(--m-radius-button);
  font-size: 13px;
  font-weight: 700;
  transition: transform var(--m-motion) ease, opacity var(--m-motion) ease;
}
.mexd-practice button:active { transform: scale(.98); }
.mexd-practice button:disabled { opacity: .6; }
.mexd-mine { margin-top: 18px; color: var(--m-text-tertiary); font-size: 12px; }
.mexd-comments { margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--m-border); }
.mexd-comments-head { display: flex; align-items: center; justify-content: space-between; }
.mexd-comments-head h2 { font-size: 18px; font-weight: 800; }
.mexd-comments-head span { color: var(--m-text-tertiary); font-size: 12px; }
.mexd-comments-empty { margin-top: 10px; color: var(--m-text-tertiary); font-size: 13px; line-height: 1.7; }
.mexd-comment { margin-top: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--m-border); }
.mexd-comment strong { font-size: 12.5px; }
.mexd-comment p { margin-top: 4px; color: var(--m-text-secondary); font-size: 13.5px; line-height: 1.75; }
.mexd-comment-form { display: flex; align-items: flex-end; gap: 8px; margin-top: 14px; }
.mexd-comment-form textarea { flex: 1; min-height: 48px; padding: 10px 12px; resize: vertical; color: var(--m-text); background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-input); font: inherit; font-size: 14px; line-height: 1.6; transition: border-color var(--m-motion) ease, box-shadow var(--m-motion) ease; }
.mexd-comment-form textarea:focus { border-color: var(--m-primary); box-shadow: 0 0 0 3px var(--m-primary-soft); outline: none; }
.mexd-comment-form button { flex: 0 0 auto; min-width: 64px; min-height: 44px; color: #fff; background: var(--m-primary); border: 0; border-radius: var(--m-radius-button); font-size: 13px; font-weight: 700; transition: transform var(--m-motion) ease, opacity var(--m-motion) ease; }
.mexd-comment-form button:active { transform: scale(.94); }
.mexd-comment-form button:disabled { opacity: .45; }
.mexd-error { margin-top: 8px; color: #b42318; font-size: 12.5px; }
.mexd-actionbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  z-index: 20;
  display: flex;
  width: min(100%, 480px);
  align-items: center;
  gap: 8px;
  padding: 10px 20px calc(10px + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  background: color-mix(in srgb, var(--m-surface) 96%, transparent);
  border-top: 1px solid var(--m-border);
  box-shadow: 0 -8px 24px rgba(16, 33, 26, .06);
  backdrop-filter: blur(10px);
  animation: mexd-bar-in 460ms cubic-bezier(.16, 1, .3, 1) 120ms backwards;
}
.mexd-action { display: inline-flex; min-width: 64px; min-height: 44px; padding: 0 10px; align-items: center; justify-content: center; gap: 5px; color: var(--m-text-secondary); background: transparent; border: 0; font-size: 13px; transition: color var(--m-motion) ease, transform var(--m-motion) ease; }
.mexd-action span { display: inline-block; font-size: 18px; line-height: 1; }
.mexd-action b { font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; }
.mexd-action.active { color: var(--m-primary); }
.mexd-action:active { transform: scale(.92); }
.mexd-action.pulsing span { animation: mexd-pop 380ms cubic-bezier(.2, .9, .3, 1.4); }
.mexd-actionbar :is(button, textarea):focus-visible, .mexd-back:focus-visible { outline: 2px solid var(--m-primary); outline-offset: 2px; }

@keyframes mexd-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}
@keyframes mexd-bar-in {
  from { opacity: 0; transform: translate(-50%, 10px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}
@keyframes mexd-pop {
  0% { transform: scale(1); }
  35% { transform: scale(1.35); }
  100% { transform: scale(1); }
}

@media (max-width: 359px) {
  .mexd-article h1 { font-size: 22px; }
  .mexd-action { min-width: 54px; padding-inline: 6px; }
}

@media (prefers-reduced-motion: reduce) {
  .mexd-article,
  .mexd-actionbar,
  .mexd-action.pulsing span { animation: none; }
  .mexd-action,
  .mexd-comment-form button { transition: none; }
}
</style>
