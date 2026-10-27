<template>
  <div class="entry-editor">
    <p>添加多条{{ label }}，每条独立编辑；保存时使用现有文本字段，空行分隔。</p>
    <article v-for="(entry, index) in entries" :key="`${revision}-${index}`">
      <label
        >{{ label }} {{ index + 1
        }}<textarea
          :value="entry"
          rows="5"
          @input="update(index, $event.target.value)"
        />
      </label>
      <div>
        <button type="button" :disabled="index === 0" @click="move(index, -1)">
          上移</button
        ><button
          type="button"
          :disabled="index === entries.length - 1"
          @click="move(index, 1)"
        >
          下移</button
        ><button type="button" @click="remove(index)">删除此条</button>
      </div>
    </article>
    <button type="button" class="add-entry" @click="add">
      ＋ 添加{{ label }}
    </button>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
const props = defineProps({
  modelValue: { type: String, default: "" },
  label: String,
});
const emit = defineEmits(["update:modelValue"]);
// Legacy text is a single entry until the user explicitly splits it by blank lines.
const split = (value) => (value ? value.split(/\n\s*\n/) : [""]);
const entries = ref(split(props.modelValue));
const revision = ref(0);
let lastEmitted = props.modelValue;
watch(
  () => props.modelValue,
  (value) => {
    if (value !== lastEmitted) {
      entries.value = split(value);
      revision.value++;
      lastEmitted = value;
    }
  },
);
function sync() {
  lastEmitted = entries.value.join("\n\n");
  emit("update:modelValue", lastEmitted);
}
function update(index, value) {
  entries.value[index] = value;
  sync();
}
function add() {
  entries.value.push("");
  sync();
}
function move(index, delta) {
  const entry = entries.value.splice(index, 1)[0];
  entries.value.splice(index + delta, 0, entry);
  revision.value++;
  sync();
}
function remove(index) {
  if (
    entries.value[index].trim() &&
    !window.confirm("删除这条草稿经历？保存的历史版本不会改变。")
  )
    return;
  entries.value.splice(index, 1);
  if (!entries.value.length) entries.value.push("");
  revision.value++;
  sync();
}
</script>

<style scoped>
.entry-editor {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.entry-editor p {
  font-size: 13px;
  color: #60736b;
  margin: 0;
  line-height: 1.6;
}
.entry-editor article {
  display: grid;
  gap: 8px;
}
.entry-editor label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.entry-editor textarea {
  box-sizing: border-box;
  width: 100%;
  resize: vertical;
  border: 1px solid #d6e3db;
  border-radius: 8px;
  padding: 12px;
  font: inherit;
  line-height: 1.6;
  color: #203c34;
  background: white;
}
.entry-editor article > div {
  display: flex;
  gap: 8px;
}
.entry-editor button {
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #c4dbd0;
  background: white;
  color: #087f5b;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.entry-editor button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.entry-editor :focus-visible {
  outline: 2px solid #078660;
  outline-offset: 3px;
}
.add-entry {
  justify-self: start;
}
</style>
