<template>
  <div class="placeholder-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><Tickets /></el-icon>
        训练档案
      </span>
      <h2>面试记录</h2>
      <p>查看所有历史面试记录，包含问答详情、追问内容和评分结果。</p>
    </div>
    <div class="placeholder-card glass-panel">
      <el-icon :size="48"><Tickets /></el-icon>
      <p>暂无面试记录</p>
      <el-button type="primary" @click="$router.push('/jobs')">开启第一次面试</el-button>
    </div>
  </div>
</template>

<script setup>
import { Tickets } from '@element-plus/icons-vue'
</script>

<style scoped>
.placeholder-page { padding-top: 12px; }

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

.placeholder-card .el-icon { color: #2563eb; }
</style>
