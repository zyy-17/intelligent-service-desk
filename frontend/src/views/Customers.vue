<template>
  <div class="customers">
    <div class="toolbar panel-card">
      <el-input v-model="keyword" placeholder="搜索客户姓名 / 账号" clearable style="width: 240px" :prefix-icon="Search" />
      <el-select v-model="role" placeholder="全部角色" clearable style="width: 150px" @change="load">
        <el-option label="客户" value="CUSTOMER" />
        <el-option label="客服" value="AGENT" />
        <el-option label="主管" value="SUPERVISOR" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
    </div>

    <div class="panel-card table-panel">
      <el-table :data="filtered" stripe v-loading="loading" style="width: 100%">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="avatar" :style="{ background: row.avatar_color || '#6366f1' }">{{ (row.display_name || '?').charAt(0) }}</div>
              <div>
                <div class="uname">{{ row.display_name }}</div>
                <div class="uaccount">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)" size="small">{{ roleMap[row.role] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="110">
          <template #default="{ row }">{{ row.department || '—' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_login_at" label="最后登录" width="150">
          <template #default="{ row }">{{ fmtTime(row.last_login_at) || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import api from '../api'

const rows = ref([])
const loading = ref(false)
const keyword = ref('')
const role = ref('')

const roleMap = { ADMIN: '系统管理员', SUPERVISOR: '客服主管', AGENT: '客服人员', CUSTOMER: '客户' }
const roleTypeMap = { ADMIN: 'danger', SUPERVISOR: 'warning', AGENT: 'primary', CUSTOMER: 'info' }
const roleType = r => roleTypeMap[r] || 'info'

const filtered = computed(() => {
  if (!keyword.value) return rows.value
  const k = keyword.value.toLowerCase()
  return rows.value.filter(u =>
    (u.display_name || '').toLowerCase().includes(k) || (u.username || '').toLowerCase().includes(k))
})

function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

async function load() {
  loading.value = true
  try {
    rows.value = await api.get('/admin/users', { params: role.value ? { role: role.value } : {} })
  } catch (e) {
    // 客户等无权限角色忽略
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.customers { display: flex; flex-direction: column; gap: 16px; }
.toolbar { padding: 16px; display: flex; gap: 12px; align-items: center; }
.table-panel { padding: 16px; }
.user-cell { display: flex; align-items: center; gap: 10px; }
.avatar { width: 34px; height: 34px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; }
.uname { font-size: 13px; font-weight: 600; }
.uaccount { font-size: 11px; color: var(--text-muted); }
</style>
