<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="logo">
        <div class="logo-icon">ISD</div>
        <div class="logo-text">
          <div class="logo-title">智能服务台</div>
          <div class="logo-sub">Intelligent Service Desk</div>
        </div>
      </div>

      <nav class="menu">
        <router-link v-for="item in menu" :key="item.path" :to="item.path" class="menu-item"
                     :class="{ active: isActive(item.path) }">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-box">
          <div class="avatar" :style="{ background: auth.user?.avatarColor || '#6366f1' }">
            {{ auth.displayName.charAt(0) }}
          </div>
          <div class="user-info">
            <div class="user-name">{{ auth.displayName }}</div>
            <div class="user-role">{{ roleLabel }}</div>
          </div>
          <el-icon class="logout" @click="handleLogout"><SwitchButton /></el-icon>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="page-title">{{ $route.meta.title }}</div>
        <div class="topbar-right">
          <el-badge :value="unread" :hidden="unread === 0" class="notify-badge">
            <el-icon class="topbar-icon" @click="showNotifications"><Bell /></el-icon>
          </el-badge>
          <span class="topbar-date">{{ today }}</span>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 通知抽屉 -->
    <el-drawer v-model="notifyVisible" title="通知中心" size="360px">
      <div v-if="notifications.length === 0" class="empty">暂无通知</div>
      <div v-for="n in notifications" :key="n.id" class="notify-item" :class="{ unread: n.isRead === 0 }">
        <div class="notify-title">{{ n.title }}</div>
        <div class="notify-content">{{ n.content }}</div>
        <div class="notify-time">{{ formatTime(n.createdAt) }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../store/auth'
import api from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const menu = [
  { path: '/dashboard', label: '工作台', icon: 'Odometer' },
  { path: '/tickets', label: '工单管理', icon: 'Tickets' },
  { path: '/customers', label: '客户管理', icon: 'User' },
  { path: '/knowledge', label: '知识库', icon: 'Collection' },
  { path: '/chat', label: '智能问答', icon: 'ChatDotRound' },
  { path: '/stats', label: '数据统计', icon: 'DataAnalysis' },
  { path: '/system', label: '系统管理', icon: 'Setting' }
]

const roleMap = { ADMIN: '系统管理员', SUPERVISOR: '客服主管', AGENT: '客服人员', CUSTOMER: '客户' }
const roleLabel = computed(() => roleMap[auth.role] || auth.role)

const isActive = (path) => route.path.startsWith(path)

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const unread = ref(0)
const notifications = ref([])
const notifyVisible = ref(false)

async function loadUnread() {
  try {
    unread.value = await api.get('/notifications/unread-count')
  } catch (e) { /* ignore */ }
}

async function showNotifications() {
  notifyVisible.value = true
  const data = await api.get('/notifications', { params: { page: 1, pageSize: 20 } })
  notifications.value = data.list || []
}

function formatTime(t) {
  return t ? t.replace('T', ' ').substring(0, 16) : ''
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

onMounted(loadUnread)
</script>

<style scoped>
.layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.sidebar {
  width: 230px;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.logo-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
}
.logo-title {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
.logo-sub {
  color: var(--sidebar-text);
  font-size: 10px;
  margin-top: 2px;
}

.menu {
  flex: 1;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 8px;
  color: var(--sidebar-text);
  font-size: 14px;
  transition: all 0.2s;
  cursor: pointer;
}
.menu-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.menu-item.active {
  background: var(--sidebar-active);
  color: #fff;
}

.sidebar-footer {
  padding: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.user-box {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.user-info {
  flex: 1;
  min-width: 0;
}
.user-name {
  color: #fff;
  font-size: 13px;
}
.user-role {
  color: var(--sidebar-text);
  font-size: 11px;
}
.logout {
  color: var(--sidebar-text);
  cursor: pointer;
  font-size: 18px;
}
.logout:hover {
  color: #fff;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--border);
}
.page-title {
  font-size: 17px;
  font-weight: 600;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.topbar-icon {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-sub);
}
.topbar-date {
  color: var(--text-sub);
  font-size: 13px;
}
.content {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
}

.notify-item {
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  background: #f9fafb;
}
.notify-item.unread {
  background: #eef2ff;
}
.notify-title {
  font-weight: 600;
  font-size: 14px;
}
.notify-content {
  color: var(--text-sub);
  font-size: 13px;
  margin-top: 4px;
}
.notify-time {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 6px;
}
.empty {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
}
</style>
