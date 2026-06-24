<template>
  <div class="placeholder-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><VideoCamera /></el-icon>
        核心功能
      </span>
      <h2>模拟面试</h2>
      <p>选择岗位后即可开启沉浸式 AI 模拟面试，系统将根据你的简历和岗位要求生成个性化追问。</p>
    </div>
    <div class="placeholder-card glass-panel">
      <el-icon :size="48"><VideoCamera /></el-icon>
      <p>请先从「面试准备」中选择岗位并填写简历</p>
      <el-button type="primary" @click="$router.push('/jobs')">前往选择岗位</el-button>
    </div>
  </div>
</template>

<script setup>
import { VideoCamera } from '@element-plus/icons-vue'
</script>

<style scoped>
.placeholder-page {
  padding-top: 12px;
}

.placeholder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  padding: 64px 32px;
  margin-top: 20px;
  text-align: center;
  color: #667085;
  border-radius: 12px;
}

.placeholder-card .el-icon {
  color: #2563eb;
}
</style>
