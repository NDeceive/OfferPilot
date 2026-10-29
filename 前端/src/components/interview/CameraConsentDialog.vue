<template>
  <Teleport to="body">
    <dialog ref="dialogRef" class="camera-consent" aria-labelledby="camera-consent-title" aria-describedby="camera-consent-description" @cancel.prevent="answerCameraConsent(false)">
      <form method="dialog" @submit.prevent>
        <span class="camera-consent__icon" aria-hidden="true">
          <!-- Heroicons video-camera (solid), MIT © Tailwind Labs, Inc.; see brand-design/licenses/heroicons-MIT.txt. -->
          <svg width="32" height="32" viewBox="0 0 24 24" fill="currentColor"><path d="M4.5 4.5a3 3 0 0 0-3 3v9a3 3 0 0 0 3 3h8.25a3 3 0 0 0 3-3v-9a3 3 0 0 0-3-3H4.5ZM19.94 18.75l-2.69-2.69V7.94l2.69-2.69c.944-.945 2.56-.276 2.56 1.06v11.38c0 1.336-1.616 2.005-2.56 1.06Z"/></svg>
        </span>
        <h2 id="camera-consent-title">允许打开摄像头并进入面试？</h2>
        <p id="camera-consent-description">本次面试需要使用摄像头。允许后将自动开启摄像头并进入面试；拒绝则无法进入。</p>
        <p class="camera-consent__note">画面在本地处理，用于表情分析，仅保存分类概率与时间。首次使用时，浏览器或系统还会请求摄像头权限。</p>
        <div class="camera-consent__actions">
          <button type="button" autofocus @click="answerCameraConsent(false)">拒绝并返回</button>
          <button type="button" class="camera-consent__allow" @click="answerCameraConsent(true)">允许并进入面试</button>
        </div>
      </form>
    </dialog>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { cameraConsentOpen, answerCameraConsent } from '../../utils/interviewCamera'

const dialogRef = ref(null)
watch([cameraConsentOpen, dialogRef], ([open, dialog]) => {
  if (!dialog) return
  if (open && !dialog.open) dialog.showModal()
  else if (!open && dialog.open) dialog.close()
}, { flush: 'post' })
</script>

<style scoped>
.camera-consent { width:min(480px,calc(100vw - 32px)); max-height:calc(100dvh - 32px); overflow:auto; margin:auto; padding:28px; border:0; border-radius:16px; background:var(--surface-elevated); color:#20342e; box-shadow:0 20px 64px #142a2233; font:inherit; }
.camera-consent::backdrop { background:#142a2266; }
.camera-consent__icon { display:flex; align-items:center; width:32px; height:32px; color:#4b6056; margin-bottom:20px; }
.camera-consent h2 { margin:0 0 12px; font-size:21px; line-height:1.5; }
.camera-consent p { margin:0 0 16px; font-size:14px; line-height:1.8; color:#4b6056; }
.camera-consent .camera-consent__note { padding:12px 14px; border-radius:8px; background:var(--surface-soft); font-size:12px; }
.camera-consent__actions { display:flex; justify-content:flex-end; gap:12px; margin-top:24px; }
.camera-consent button { min-height:44px; padding:10px 16px; border:1px solid #d6e3dd; border-radius:8px; background:var(--surface-elevated); color:#334b3e; font:inherit; font-size:14px; cursor:pointer; }
.camera-consent button:hover { background:var(--surface-soft); }
.camera-consent .camera-consent__allow { background:var(--accent-500,#10b981); border-color:transparent; color:#fff; font-weight:600; }
.camera-consent .camera-consent__allow:hover { background:var(--accent-600,#059669); }
.camera-consent button:focus-visible { outline:3px solid #059669; outline-offset:3px; }
@media(max-width:480px) { .camera-consent { padding:24px 20px; } .camera-consent h2 { font-size:19px; } .camera-consent__actions { flex-direction:column-reverse; } .camera-consent button { width:100%; } }
</style>
