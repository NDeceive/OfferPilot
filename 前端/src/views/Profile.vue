<template>
  <div class="placeholder-page page-shell">
    <div class="page-title">
      <span class="eyebrow">
        <el-icon><User /></el-icon>
        账户管理
      </span>
      <h2>个人中心</h2>
      <p>管理个人信息、查看训练统计、修改密码和系统偏好设置。</p>
    </div>

    <div class="profile-grid">
      <div class="profile-card glass-panel">
        <div class="profile-avatar">
          <el-icon :size="40"><Avatar /></el-icon>
        </div>
        <h3>{{ userStore.nickname || userStore.username }}</h3>
        <el-tag>{{ roleLabel }}</el-tag>
        <el-divider />
        <div class="profile-info">
          <div class="info-item">
            <span class="info-label">用户名</span>
            <span class="info-value">{{ userStore.username }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">昵称</span>
            <span class="info-value">{{ userStore.nickname || '未设置' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">角色</span>
            <span class="info-value">{{ roleLabel }}</span>
          </div>
        </div>
      </div>

      <div class="settings-card glass-panel">
        <h3>账户设置</h3>
        <el-form label-position="top" size="large">
          <el-form-item label="昵称">
            <el-input placeholder="设置昵称" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input type="password" placeholder="留空则不修改" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input type="password" placeholder="再次输入新密码" show-password />
          </el-form-item>
          <el-button type="primary">保存修改</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { User, Avatar } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const roleLabel = computed(() => {
  return { STUDENT: '学生', TEACHER: '教师', ADMIN: '企业' }[userStore.role] || '用户'
})
</script>

<style scoped>
.placeholder-page { padding-top: 12px; }

.profile-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 20px;
  margin-top: 24px;
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

.profile-card, .settings-card {
  padding: 28px;
  border-radius: 12px;
}

.profile-card {
  text-align: center;
}

.profile-avatar {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin: 0 auto 14px;
  background: linear-gradient(135deg, rgba(37,99,235,0.1), rgba(124,58,237,0.08));
  border-radius: 50%;
  color: #2563eb;
}

.profile-card h3 {
  font-size: 18px;
  color: #172033;
  margin-bottom: 8px;
}

.profile-info {
  text-align: left;
}

.info-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
}

.info-label {
  font-size: 13px;
  color: #667085;
}

.info-value {
  font-size: 13px;
  font-weight: 600;
  color: #172033;
}

.settings-card h3 {
  font-size: 16px;
  color: #172033;
  margin-bottom: 18px;
}
</style>
