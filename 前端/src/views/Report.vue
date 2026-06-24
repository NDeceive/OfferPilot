<template>
  <div class="placeholder-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><DataAnalysis /></el-icon>
        能力评估
      </span>
      <h2>能力报告</h2>
      <p>基于面试表现的多维能力雷达图分析，包含沟通表达、技术深度、逻辑思维、项目经验等维度。</p>
    </div>
    <div class="placeholder-card glass-panel">
      <el-icon :size="48"><DataAnalysis /></el-icon>
      <p>完成面试后将自动生成能力报告</p>
      <el-button type="primary" @click="$router.push('/jobs')">去完成一次面试</el-button>
    </div>
  </div>
</template>

<script setup>
import { DataAnalysis } from '@element-plus/icons-vue'
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
