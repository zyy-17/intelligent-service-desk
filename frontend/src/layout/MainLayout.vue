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
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar" alt="avatar" />
          <div v-else class="avatar" :style="{ background: auth.user?.avatarColor || '#6366f1' }">
            {{ auth.displayName.charAt(0) }}
          </div>
          <div class="user-info">
            <div class="user-name">{{ auth.displayName }}</div>
            <div class="user-role">{{ roleLabel }}</div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar">
        <div class="page-title">{{ $route.meta.title }}</div>
        <!-- 全局搜索 -->
        <div class="global-search" ref="searchBoxRef">
          <el-icon class="search-icon"><Search /></el-icon>
          <input v-model="keyword" class="search-input" placeholder="搜索工单、客户、知识..."
                 @input="onSearch" @focus="showResult = resultsLoaded" @keydown.enter="onSearch"
                 @keyup.ctrl.k.prevent />
          <span class="search-kbd">Ctrl + K</span>
          <div v-if="showResult" class="search-panel">
            <div v-if="searchEmpty" class="search-empty">未找到与「{{ lastKeyword }}」相关的内容</div>
            <template v-else>
              <div class="s-group" v-if="results.tickets.length">
                <div class="s-group-title">工单</div>
                <div class="s-item" v-for="t in results.tickets" :key="'t' + t.id" @click="goTicket(t)">
                  <span class="s-no">{{ t.ticket_no }}</span>
                  <span class="s-text">{{ t.title }}</span>
                  <span class="s-customer" v-if="t.customerName">{{ t.customerName }}</span>
                </div>
              </div>
              <div class="s-group" v-if="results.customers.length">
                <div class="s-group-title">客户</div>
                <div class="s-item" v-for="c in results.customers" :key="'c' + c.id" @click="go('/customers')">
                  <span class="s-no">{{ c.username }}</span>
                  <span class="s-text">{{ c.display_name }}</span>
                  <span class="s-customer" v-if="c.company">{{ c.company }}</span>
                </div>
              </div>
              <div class="s-group" v-if="results.articles.length">
                <div class="s-group-title">知识库</div>
                <div class="s-item" v-for="a in results.articles" :key="'a' + a.id" @click="go('/knowledge')">
                  <span class="s-no">知识</span>
                  <span class="s-text">{{ a.title }}</span>
                  <span class="s-customer">{{ a.view_count }} 次浏览</span>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="topbar-right">
          <el-badge :value="unread" :hidden="unread === 0" class="notify-badge">
            <el-icon class="topbar-icon" @click="showNotifications"><Bell /></el-icon>
          </el-badge>

          <!-- 右上角用户头像 -->
          <el-dropdown trigger="click" @command="onUserCommand">
            <div class="user-chip">
              <img v-if="avatarUrl" :src="avatarUrl" class="chip-avatar" alt="avatar" />
              <div v-else class="chip-avatar chip-fallback"
                   :style="{ background: auth.user?.avatarColor || '#6366f1' }">
                {{ auth.displayName.charAt(0) }}
              </div>
              <div class="chip-info">
                <div class="chip-name">{{ auth.displayName }}</div>
                <div class="chip-role">{{ roleLabel }}</div>
              </div>
              <el-icon class="chip-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
                <el-dropdown-item command="switch" :icon="SwitchButton">切换账号</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="CircleClose" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ArrowDown, SwitchButton, CircleClose, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 菜单由后端按角色下发，前端直接渲染（无需维护角色映射）
const menu = computed(() => auth.menus || [])

const isActive = (path) => route.path.startsWith(path)

const roleMap = { ADMIN: '系统管理员', SUPERVISOR: '客服主管', AGENT: '客服人员', CUSTOMER: '客户' }
const roleLabel = computed(() => roleMap[auth.role] || auth.role)
const avatarUrl = computed(() => auth.user?.avatarUrl || '')

/* ===== 全局搜索 ===== */
const keyword = ref('')
const lastKeyword = ref('')
const results = ref({ tickets: [], customers: [], articles: [] })
const showResult = ref(false)
const resultsLoaded = ref(false)
const searchBoxRef = ref(null)
let searchTimer = null

const searchEmpty = computed(() =>
  !results.value.tickets.length && !results.value.customers.length && !results.value.articles.length)

function onSearch() {
  clearTimeout(searchTimer)
  const q = keyword.value.trim()
  if (!q) {
    showResult.value = false
    resultsLoaded.value = false
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      results.value = await api.get('/search', { params: { q } })
      lastKeyword.value = q
      resultsLoaded.value = true
      showResult.value = true
    } catch (e) { /* 拦截器已提示 */ }
  }, 300)
}

function go(path) {
  showResult.value = false
  router.push(path)
}
function goTicket(t) {
  showResult.value = false
  router.push({ path: '/tickets', query: { keyword: t.ticket_no } })
}

function onDocClick(e) {
  if (searchBoxRef.value && !searchBoxRef.value.contains(e.target)) {
    showResult.value = false
  }
}
function onKeydown(e) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchBoxRef.value?.querySelector('.search-input')?.focus()
  }
}

/* ===== 用户菜单 ===== */
function onUserCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'switch') {
    ElMessageBox.confirm('切换账号将退出当前登录，是否继续？', '切换账号', {
      confirmButtonText: '切换账号',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      auth.logout()
      ElMessage.success('已退出，请选择其他账号登录')
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      auth.logout()
      ElMessage.success('已安全退出')
      router.push('/login')
    }).catch(() => {})
  }
}

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

onMounted(() => {
  loadUnread()
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})
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
  object-fit: cover;
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
  gap: 24px;
  padding: 0 24px;
  border-bottom: 1px solid var(--border);
}
.page-title {
  font-size: 17px;
  font-weight: 600;
  flex-shrink: 0;
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

/* 全局搜索 */
.global-search {
  position: relative;
  flex: 1;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 0 12px;
  height: 38px;
  transition: box-shadow 0.2s, background 0.2s;
}
.global-search:focus-within {
  background: #fff;
  box-shadow: 0 0 0 2px #3b82f6 inset;
}
.search-icon { color: #9ca3af; }
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: var(--text-main);
}
.search-kbd {
  font-size: 11px;
  color: #9ca3af;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  padding: 1px 6px;
  white-space: nowrap;
}
.search-panel {
  position: absolute;
  top: 46px;
  left: 0;
  right: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
  padding: 10px;
  max-height: 420px;
  overflow-y: auto;
  z-index: 50;
}
.search-empty {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 18px 0;
}
.s-group { margin-bottom: 6px; }
.s-group-title {
  font-size: 11px;
  color: #9ca3af;
  padding: 6px 8px 4px;
}
.s-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.s-item:hover { background: #f3f4f6; }
.s-no {
  color: #2563eb;
  font-size: 12px;
  background: #eff6ff;
  border-radius: 5px;
  padding: 1px 6px;
  white-space: nowrap;
}
.s-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.s-customer { color: #9ca3af; font-size: 12px; white-space: nowrap; }

/* 右上角用户头像 */
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 10px;
  transition: background 0.2s;
}
.user-chip:hover { background: #f3f4f6; }
.chip-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}
.chip-fallback {
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}
.chip-info { line-height: 1.2; }
.chip-name { font-size: 13px; font-weight: 600; }
.chip-role { font-size: 11px; color: var(--text-sub); }
.chip-arrow { font-size: 12px; color: #9ca3af; }

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
