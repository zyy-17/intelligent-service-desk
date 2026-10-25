<template>
  <div class="system">
    <el-tabs v-model="activeTab">
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="panel-card table-panel">
          <el-table :data="users" stripe v-loading="loadingUsers" style="width: 100%">
            <el-table-column prop="username" label="账号" width="130" />
            <el-table-column prop="display_name" label="姓名" width="120" />
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }">
                <el-select :model-value="row.role" size="small" style="width: 110px" @change="v => changeRole(row, v)">
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="主管" value="SUPERVISOR" />
                  <el-option label="客服" value="AGENT" />
                  <el-option label="客户" value="CUSTOMER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
                  {{ row.status === 1 ? '禁用' : '启用' }}
                </el-button>
                <el-button size="small" @click="resetPwd(row)">重置密码</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 工单分类 -->
      <el-tab-pane label="工单分类" name="categories">
        <div class="toolbar panel-card">
          <el-button type="primary" :icon="Plus" @click="openCatEdit()">新建分类</el-button>
        </div>
        <div class="panel-card table-panel">
          <el-table :data="categories" stripe style="width: 100%">
            <el-table-column prop="name" label="分类名称" width="160">
              <template #default="{ row }">
                <span class="cat-dot" :style="{ background: row.color }"></span>{{ row.name }}
              </template>
            </el-table-column>
            <el-table-column prop="sla_hours" label="SLA 时限(小时)" width="140" />
            <el-table-column prop="sort" label="排序" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="openCatEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteCat(row)">停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 审计日志 -->
      <el-tab-pane label="操作日志" name="logs">
        <div class="panel-card table-panel">
          <el-table :data="logs" stripe v-loading="loadingLogs" style="width: 100%">
            <el-table-column prop="operator_name" label="操作人" width="120" />
            <el-table-column prop="action" label="操作类型" width="160" />
            <el-table-column prop="target_type" label="对象类型" width="110" />
            <el-table-column prop="target_id" label="对象" width="120" />
            <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
            <el-table-column prop="created_at" label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next" :total="logTotal"
                           :page-size="10" :current-page="logPage" @current-change="loadLogs" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 分类编辑 -->
    <el-dialog v-model="catVisible" :title="catForm.id ? '编辑分类' : '新建分类'" width="420px">
      <el-form :model="catForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="catForm.name" /></el-form-item>
        <el-form-item label="SLA 时限">
          <el-input-number v-model="catForm.sla_hours" :min="1" :max="720" />
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="catForm.sort" :min="0" /></el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="catForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'

const activeTab = ref('users')
const users = ref([])
const loadingUsers = ref(false)
const categories = ref([])
const logs = ref([])
const loadingLogs = ref(false)
const logPage = ref(1)
const logTotal = ref(0)

const catVisible = ref(false)
const catForm = reactive({ id: null, name: '', sla_hours: 24, sort: 0, color: '#6366f1' })

function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 19) : '' }

async function loadUsers() {
  loadingUsers.value = true
  try { users.value = await api.get('/admin/users') } finally { loadingUsers.value = false }
}

async function loadCategories() {
  categories.value = await api.get('/admin/categories')
}

async function loadLogs(page = 1) {
  loadingLogs.value = true
  logPage.value = page
  try {
    const data = await api.get('/admin/logs', { params: { page, pageSize: 10 } })
    logs.value = data.list || []
    logTotal.value = data.total
  } finally { loadingLogs.value = false }
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await api.put(`/admin/users/${row.id}/status`, { status: newStatus })
  ElMessage.success('操作成功')
  loadUsers()
}

async function changeRole(row, role) {
  await api.put(`/admin/users/${row.id}/role`, { role })
  ElMessage.success('角色已更新')
  loadUsers()
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt(`为 ${row.display_name} 设置新密码`, '重置密码')
  await api.put(`/admin/users/${row.id}/password`, { password: value })
  ElMessage.success('密码已重置')
}

function openCatEdit(row) {
  if (row) {
    Object.assign(catForm, { id: row.id, name: row.name, sla_hours: row.sla_hours, sort: row.sort, color: row.color })
  } else {
    Object.assign(catForm, { id: null, name: '', sla_hours: 24, sort: 0, color: '#6366f1' })
  }
  catVisible.value = true
}

async function saveCat() {
  if (!catForm.name) { ElMessage.warning('请输入名称'); return }
  await api.post('/admin/categories', catForm)
  ElMessage.success('保存成功')
  catVisible.value = false
  loadCategories()
}

async function deleteCat(row) {
  await api.delete(`/admin/categories/${row.id}`)
  ElMessage.success('已停用')
  loadCategories()
}

onMounted(() => {
  loadUsers()
  loadCategories()
  loadLogs(1)
})
</script>

<style scoped>
.system { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; margin-bottom: 16px; }
.table-panel { padding: 16px; }
.cat-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
