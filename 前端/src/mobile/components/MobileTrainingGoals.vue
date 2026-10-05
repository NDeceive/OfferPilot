<template>
  <div class="mgoal">
    <MobileSkeleton v-if="loading" variant="card" :rows="3" label="正在加载能力维度" />
    <MobileState v-else-if="error" kind="error" title="能力维度加载失败" :description="error" />

    <template v-else>
      <p class="mgoal__lead">
        本轮面试围绕这 <strong>5</strong> 项能力出题。顺序就是权重，
        第 1 项占 {{ weights[0] }}%，往后依次递减。
      </p>

      <!-- 已选：可排序、可调深度 -->
      <section class="mgoal__chosen">
        <h3 class="mgoal__cap">我的训练目标</h3>
        <ul class="mgoal__list">
          <li
            v-for="(code, index) in order"
            :key="code"
            class="mgoal__row"
            :class="{ 'is-active': code === activeCode }"
          >
            <button type="button" class="mgoal__row-main" @click="$emit('set-active', code)">
              <span class="mgoal__rank">{{ index + 1 }}</span>
              <span class="mgoal__name">
                <strong>{{ nameOf(code) }}</strong>
                <small>{{ levelLabel(levels[code]) }}</small>
              </span>
              <span class="mgoal__weight">{{ weights[index] }}%</span>
            </button>
            <span class="mgoal__arrows">
              <button
                type="button"
                :disabled="index === 0"
                :aria-label="`把${nameOf(code)}上移一位`"
                @click="$emit('move', index, -1)"
              >↑</button>
              <button
                type="button"
                :disabled="index === order.length - 1"
                :aria-label="`把${nameOf(code)}下移一位`"
                @click="$emit('move', index, 1)"
              >↓</button>
            </span>
          </li>
        </ul>

        <!-- 选中项的能力说明 + 深度三选 -->
        <div v-if="activeModule" class="mgoal__detail">
          <h4>{{ activeModule.name }}</h4>
          <p>{{ activeModule.description || '围绕该能力组织本轮问题与复盘重点。' }}</p>
          <div class="mobile-segments mgoal__levels">
            <button
              v-for="opt in levelOptions"
              :key="opt.value"
              type="button"
              :class="{ active: (levels[activeModule.code] || 2) === opt.value }"
              @click="$emit('set-level', activeModule.code, opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
          <p class="mgoal__hint">{{ activeLevelHint }}</p>
        </div>
      </section>

      <!-- 待选：全部维度 -->
      <section class="mgoal__pool">
        <h3 class="mgoal__cap">
          能力维度库
          <span>{{ order.length }} / 5</span>
        </h3>
        <div class="mgoal__grid">
          <button
            v-for="m in modules"
            :key="m.code"
            type="button"
            class="mgoal__chip"
            :class="{ 'is-on': order.includes(m.code) }"
            :aria-pressed="order.includes(m.code)"
            @click="$emit('toggle', m.code)"
          >
            <span class="mgoal__tick">{{ order.includes(m.code) ? '✓' : '+' }}</span>
            {{ m.name }}
          </button>
        </div>
        <p v-if="feedback" class="mgoal__feedback">{{ feedback }}</p>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MobileState from './MobileState.vue'
import MobileSkeleton from './MobileSkeleton.vue'

const props = defineProps({
  modules: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  /** 已选模块的 code，顺序即权重顺序（父组件的 moduleOrder，与选中集合恒等） */
  order: { type: Array, default: () => [] },
  /** code → 1(基础) / 2(进阶) / 3(挑战) */
  levels: { type: Object, default: () => ({}) },
  activeCode: { type: String, default: '' },
  feedback: { type: String, default: '' },
})

defineEmits(['toggle', 'move', 'set-active', 'set-level'])

// 权重与 JobSelect.vue:691 保持一致——两端的 modulePreferences 只是 rank，
// 这个百分比是给人看的换算，改一处必须改另一处。
const weights = [30, 25, 20, 15, 10]

const levelOptions = [
  { value: 1, label: '基础' },
  { value: 2, label: '进阶' },
  { value: 3, label: '挑战' },
]

const activeModule = computed(
  () => props.modules.find((m) => m.code === props.activeCode) || null
)

const activeLevelHint = computed(() => {
  const level = props.levels[activeModule.value?.code] ?? 2
  return {
    1: '关注概念理解与基本应用',
    2: '关注方案设计与项目实践',
    3: '关注复杂场景与技术取舍',
  }[level]
})

function nameOf(code) {
  return props.modules.find((m) => m.code === code)?.name || code
}

function levelLabel(value) {
  return levelOptions.find((o) => o.value === (value ?? 2))?.label || '进阶'
}
</script>

<style scoped>
.mgoal { display: flex; flex-direction: column; gap: 20px; }

.mgoal__lead { color: var(--m-text-secondary); font-size: 13px; line-height: 1.65; }
.mgoal__lead strong { color: var(--m-primary); }

.mgoal__cap { display: flex; margin-bottom: 10px; align-items: center; justify-content: space-between; font-size: 15px; font-weight: 800; }
.mgoal__cap span { color: var(--m-text-tertiary); font-size: 12px; font-weight: 600; }

.mgoal__list { display: flex; flex-direction: column; gap: 8px; }
.mgoal__row {
  display: flex;
  align-items: stretch;
  overflow: hidden;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
}
.mgoal__row.is-active { border-color: var(--m-primary); box-shadow: 0 0 0 1px var(--m-primary) inset; }

.mgoal__row-main {
  display: flex;
  flex: 1;
  min-width: 0;
  padding: 12px 8px 12px 12px;
  align-items: center;
  gap: 10px;
  color: var(--m-text);
  text-align: left;
  background: transparent;
  border: 0;
}
.mgoal__rank { display: grid; flex: 0 0 auto; width: 26px; height: 26px; color: var(--m-primary-dark); place-items: center; background: var(--m-primary-soft); border-radius: 50%; font-size: 12px; font-weight: 800; }
.mgoal__name { flex: 1; min-width: 0; }
.mgoal__name strong { display: block; font-size: 14px; line-height: 1.3; overflow-wrap: anywhere; }
.mgoal__name small { display: block; margin-top: 2px; color: var(--m-text-tertiary); font-size: 11px; }
.mgoal__weight { flex: 0 0 auto; color: var(--m-primary-dark); font-size: 15px; font-weight: 800; }

.mgoal__arrows { display: flex; flex: 0 0 auto; flex-direction: column; border-left: 1px solid var(--m-border); }
.mgoal__arrows button {
  width: 44px;
  flex: 1;
  min-height: 30px;
  color: var(--m-text-secondary);
  background: #fafbf9;
  border: 0;
  font-size: 13px;
}
.mgoal__arrows button:first-child { border-bottom: 1px solid var(--m-border); }
.mgoal__arrows button:active:not(:disabled) { background: var(--m-primary-soft); color: var(--m-primary-dark); }
.mgoal__arrows button:disabled { color: #cfd6d1; background: #f6f7f5; }

.mgoal__detail { margin-top: 12px; padding: 14px; background: var(--m-surface-soft); border-radius: var(--m-radius-card); }
.mgoal__detail h4 { font-size: 14px; }
.mgoal__detail p { margin-top: 5px; color: var(--m-text-secondary); font-size: 12.5px; line-height: 1.6; }
.mgoal__levels { margin-top: 12px; background: rgba(255,255,255,.8); }
.mgoal__hint { color: var(--m-text-tertiary) !important; font-size: 11.5px !important; }

.mgoal__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.mgoal__chip {
  display: flex;
  min-height: 46px;
  padding: 8px 12px;
  align-items: center;
  gap: 8px;
  color: var(--m-text-secondary);
  text-align: left;
  background: var(--m-surface);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-input);
  font-size: 13px;
  line-height: 1.3;
}
.mgoal__chip.is-on { color: var(--m-primary-dark); background: var(--m-primary-soft); border-color: #9bbda8; font-weight: 700; }
.mgoal__tick { display: grid; flex: 0 0 auto; width: 20px; height: 20px; color: var(--m-text-tertiary); place-items: center; background: #f0f1ed; border-radius: 6px; font-size: 12px; font-weight: 800; }
.mgoal__chip.is-on .mgoal__tick { color: #fff; background: var(--m-primary); }

.mgoal__feedback { margin-top: 10px; padding: 10px 12px; color: #7c4315; background: var(--m-accent-soft); border-radius: 10px; font-size: 12px; line-height: 1.5; }
</style>
