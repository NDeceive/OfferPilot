<template>
  <div class="avatar-editor">
    <button class="avatar-trigger" type="button" aria-label="更换头像" @click="input.click()">
      <img v-if="modelValue" :src="modelValue" alt="当前头像" @error="imageFailed = true" v-show="!imageFailed" />
      <span v-if="!modelValue || imageFailed">{{ name?.charAt(0) || '我' }}</span>
      <small>更换头像</small>
    </button>
    <input ref="input" type="file" accept="image/jpeg,image/png" hidden @change="choose" />
    <p v-if="error && !source" class="avatar-error" role="alert">{{ error }}</p>
    <dialog ref="dialog" aria-labelledby="avatar-title" @cancel="cancel" @close="release">
      <h2 id="avatar-title">调整头像</h2><p>拖动图片或缩放，选取你希望展示的区域。</p>
      <VuePictureCropper v-if="source" ref="picture" :img="source" :box-style="{ width: '100%', height: '280px' }" :options="{ aspectRatio: 1, viewMode: 1, dragMode: 'move', autoCropArea: 0.85, ready: () => ready = true }" />
      <div class="crop-tools"><button type="button" @click="picture?.cropper?.zoom(-0.1)">缩小</button><button type="button" @click="picture?.cropper?.zoom(0.1)">放大</button><button type="button" @click="picture?.cropper?.rotate(90)">旋转</button></div>
      <p v-if="error" class="avatar-error" role="alert">{{ error }}</p>
      <footer><button type="button" :disabled="saving" @click="dialog.close()">取消</button><button class="save-avatar" type="button" :disabled="saving || !ready" @click="save">{{ saving ? '正在保存…' : '保存头像' }}</button></footer>
    </dialog>
  </div>
</template>
<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import VuePictureCropper from 'vue-picture-cropper'
import 'cropperjs/dist/cropper.css'
import 'vue-picture-cropper/style.css'
import { uploadAvatar } from '../../api'
defineProps({ modelValue: String, name: String })
const emit = defineEmits(['update:modelValue'])
const input = ref(null), dialog = ref(null), picture = ref(null)
const source = ref(''), error = ref(''), saving = ref(false), ready = ref(false), imageFailed = ref(false)
watch(source, () => { ready.value = false })
async function choose(event) {
  const file = event.target.files[0]
  event.target.value = ''
  if (!file) return
  error.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
    error.value = '请选择 5 MB 以内的 JPG 或 PNG 图片。'; return
  }
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.src = url
  try {
    await image.decode()
    if (image.width > 4096 || image.height > 4096) throw new Error('图片尺寸不能超过 4096 像素。')
    source.value = url
    await nextTick()
    dialog.value.showModal()
  } catch (e) { URL.revokeObjectURL(url); error.value = e.message.includes('4096') ? e.message : '无法读取图片，请更换文件。' }
}
async function save() {
  saving.value = true; error.value = ''
  try {
    const canvas = picture.value?.cropper?.getCroppedCanvas({ width: 256, height: 256, fillColor: '#fff' })
    if (!canvas) throw new Error('图片尚未准备好，请稍后重试。')
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('图片处理失败，请重新选择。')
    const avatar = await uploadAvatar(new File([blob], 'avatar.png', { type: 'image/png' }))
    emit('update:modelValue', avatar); imageFailed.value = false; dialog.value.close()
  } catch (e) { error.value = e.message || '头像保存失败，请重试。' }
  finally { saving.value = false }
}
function cancel(event) { if (saving.value) event.preventDefault() }
function release() { if (source.value) URL.revokeObjectURL(source.value); source.value = '' }
onUnmounted(release)
</script>
<style scoped>
.avatar-trigger{position:relative;display:grid;place-items:center;width:88px;height:88px;overflow:hidden;border:1px solid var(--accent-200);border-radius:24px;background:var(--accent-50);color:var(--accent-700);cursor:pointer;font-size:30px;font-weight:700}.avatar-trigger img{width:100%;height:100%;object-fit:cover}.avatar-trigger small{position:absolute;bottom:0;left:0;right:0;padding:5px 0;background:rgba(0,0,0,.58);color:#fff;font-size:10px;font-weight:400}.avatar-error{color:#b42318;font-size:13px;max-width:300px}dialog{width:min(480px,calc(100vw - 32px));max-height:90dvh;margin:auto;padding:24px;border:1px solid var(--neutral-200);border-radius:16px;background:white;color:var(--neutral-900)}dialog::backdrop{background:rgba(24,24,27,.45)}dialog h2{font-size:21px}dialog p{margin:8px 0 18px;font-size:13px;color:var(--neutral-600)}.crop-tools,footer{display:flex;gap:8px;margin-top:16px}footer{justify-content:flex-end}button:not(.avatar-trigger){min-height:44px;padding:8px 16px;border:1px solid var(--neutral-200);border-radius:8px;background:white;cursor:pointer}.save-avatar{background:var(--accent-500)!important;color:white;border-color:transparent!important}button:disabled{opacity:.5;cursor:wait}button:focus-visible{outline:2px solid var(--accent-700);outline-offset:3px}
</style>
