<template>
  <div class="mcfm">
    <!-- 岗位 -->
    <section class="mcfm__card">
      <h3 class="mcfm__cap">目标岗位</h3>
      <div class="mcfm__job">
        <i :style="{ background: job?.accentColor }" />
        <span>
          <strong>{{ job?.title || '未选择' }}</strong>
          <small>{{ job?.family }}</small>
        </span>
      </div>
    </section>

    <!-- 简历 -->
    <section class="mcfm__card">
      <h3 class="mcfm__cap">简历依据</h3>
      <p v-if="!skills.length" class="mcfm__muted">
        没有简历依据，题目将完全按岗位通用能力抽取。
      </p>
      <template v-else>
        <p class="mcfm__muted">出题会结合这 {{ skills.length }} 个技能标签：</p>
        <div class="mcfm__skills">
          <span v-for="s in skills.slice(0, 12)" :key="s">{{ s }}</span>
          <span v-if="skills.length > 12" class="is-more">+{{ skills.length - 12 }}</span>
        </div>
      </template>
    </section>

    <!-- 训练目标 -->
    <section class="mcfm__card">
      <h3 class="mcfm__cap">训练目标<span>按权重出题</span></h3>
      <ol class="mcfm__goals">
        <li v-for="(m, i) in goals" :key="m.code">
          <span class="mcfm__rank">{{ i + 1 }}</span>
          <span class="mcfm__goal-name">
            <strong>{{ m.name }}</strong>
            <small>{{ m.levelLabel }}</small>
          </span>
          <span class="mcfm__weight">{{ m.weight }}%</span>
        </li>
      </ol>
      <div class="mcfm__bar" aria-hidden="true">
        <i
          v-for="(m, i) in goals"
          :key="m.code"
          :style="{ flex: m.weight, background: barColor(i) }"
        />
      </div>
    </section>

    <!-- 时长 -->
    <section class="mcfm__card">
      <h3 class="mcfm__cap">面试时长</h3>
      <div class="mcfm__presets">
        <button
          v-for="p in presets"
          :key="p"
          type="button"
          :class="{ active: durationSeconds === p * 60 }"
          @click="$emit('update:durationSeconds', p * 60)"
        >
          {{ p }} 分钟
        </button>
      </div>

      <div class="mcfm__stepper">
        <button type="button" :disabled="durationSeconds <= min" aria-label="减少 5 分钟" @click="step(-300)">−</button>
        <span class="mcfm__time">
          <strong>{{ formatDuration(durationSeconds) }}</strong>
          <small>{{ durationSeconds >= 3600 ? '较长，建议分次完成' : '可随时提前结束' }}</small>
        </span>
        <button type="button" :disabled="durationSeconds >= max" aria-label="增加 5 分钟" @click="step(300)">＋</button>
      </div>
      <p class="mcfm__muted mcfm__range">可调范围：5 分钟 ~ 2 小时</p>
    </section>

    <p v-if="error" class="mcfm__err">{{ error }}</p>
  </div>
</template>

<script setup>
const props = defineProps({
  job: { type: Object, default: null },
  skills: { type: Array, default: () => [] },
  /** [{ code, name, levelLabel, weight }]，顺序即权重顺序 */
  goals: { type: Array, default: () => [] },
  durationSeconds: { type: Number, default: 1800 },
  error: { type: String, default: '' },
})

const emit = defineEmits(['update:durationSeconds', 'start'])

// 与 JobSelect.vue:669-672 同一组边界。那边用滚轮选到秒，移动端改成 5 分钟步进——
// 52px 行高的滚轮在触屏上要对齐刻度很容易歪，按钮不会。
const min = 5 * 60
const max = 2 * 60 * 60
const presets = [15, 30, 45, 60]

const BAR_COLORS = ['#2f7458', '#3f8a67', '#5aa37c', '#86bb9b', '#b6d5c1']

function barColor(i) {
  return BAR_COLORS[i] || BAR_COLORS[BAR_COLORS.length - 1]
}

function step(delta) {
  const next = Math.min(max, Math.max(min, props.durationSeconds + delta))
  if (next !== props.durationSeconds) emit('update:durationSeconds', next)
}

/** 与 JobSelect.vue:839 的 formatDuration 同样式，两处措辞要一致 */
function formatDuration(totalSeconds) {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const parts = []
  if (hours) parts.push(`${hours} 小时`)
  if (minutes) parts.push(`${minutes} 分钟`)
  return parts.join(' ') || '0 分钟'
}
</script>

<style scoped>
.mcfm { display: flex; flex-direction: column; gap: 12px; }

.mcfm__card { padding: 16px; background: var(--m-surface); border: 1px solid var(--m-border); border-radius: var(--m-radius-card); }
.mcfm__cap { display: flex; margin-bottom: 12px; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 800; }
.mcfm__cap span { color: var(--m-text-tertiary); font-size: 11px; font-weight: 600; }
.mcfm__muted { color: var(--m-text-tertiary); font-size: 12.5px; line-height: 1.6; }

.mcfm__job { display: flex; align-items: center; gap: 10px; }
.mcfm__job i { flex: 0 0 auto; width: 4px; height: 34px; border-radius: 3px; }
.mcfm__job strong { display: block; font-size: 15px; }
.mcfm__job small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 12px; }

.mcfm__skills { display: flex; margin-top: 10px; flex-wrap: wrap; gap: 6px; }
.mcfm__skills span { padding: 4px 10px; color: var(--m-primary-dark); background: var(--m-primary-soft); border-radius: 999px; font-size: 12px; }
.mcfm__skills .is-more { color: var(--m-text-secondary); background: #f0f1ed; }

.mcfm__goals { display: flex; flex-direction: column; gap: 9px; }
.mcfm__goals li { display: flex; align-items: center; gap: 10px; }
.mcfm__rank { display: grid; flex: 0 0 auto; width: 22px; height: 22px; color: var(--m-primary-dark); place-items: center; background: var(--m-primary-soft); border-radius: 50%; font-size: 11px; font-weight: 800; }
.mcfm__goal-name { flex: 1; min-width: 0; }
.mcfm__goal-name strong { display: block; font-size: 13.5px; line-height: 1.3; }
.mcfm__goal-name small { display: block; margin-top: 1px; color: var(--m-text-tertiary); font-size: 11px; }
.mcfm__weight { flex: 0 0 auto; color: var(--m-primary-dark); font-size: 14px; font-weight: 800; }

.mcfm__bar { display: flex; height: 6px; margin-top: 14px; gap: 2px; overflow: hidden; border-radius: 4px; }
.mcfm__bar i { display: block; height: 100%; }

.mcfm__presets { display: grid; margin-bottom: 12px; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 7px; }
.mcfm__presets button { min-height: 40px; color: var(--m-text-secondary); background: #f2f4f0; border: 1px solid transparent; border-radius: 10px; font-size: 12.5px; }
.mcfm__presets button.active { color: var(--m-primary-dark); background: var(--m-primary-soft); border-color: #9bbda8; font-weight: 700; }

.mcfm__stepper { display: flex; align-items: center; gap: 10px; }
.mcfm__stepper > button {
  display: grid;
  flex: 0 0 auto;
  width: 48px;
  height: 48px;
  color: var(--m-primary-dark);
  place-items: center;
  background: var(--m-primary-soft);
  border: 0;
  border-radius: 50%;
  font-size: 20px;
  font-weight: 700;
}
.mcfm__stepper > button:disabled { color: #c3cdc6; background: #f2f4f0; }
.mcfm__stepper > button:active:not(:disabled) { background: #cfe6d7; }
.mcfm__time { flex: 1; min-width: 0; text-align: center; }
.mcfm__time strong { display: block; color: var(--m-primary-dark); font-size: 20px; font-weight: 800; }
.mcfm__time small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 11px; }
.mcfm__range { margin-top: 10px; text-align: center; }

.mcfm__err { padding: 11px 13px; color: #7c4315; background: var(--m-accent-soft); border-radius: 10px; font-size: 12.5px; line-height: 1.5; }
</style>
