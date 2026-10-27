<template>
  <div class="tickets">
    <!-- 工具栏 -->
    <div class="toolbar panel-card">
      <div class="filters">
        <el-input v-model="query.keyword" placeholder="搜索工单编号 / 标题" clearable style="width: 240px"
                  :prefix-icon="Search" @keyup.enter="load" @clear="load" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px" @change="load">
          <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建工单</el-button>
    </div>

    <!-- 列表：点击任意行进入工单详情页 -->
    <div class="panel-card table-panel">
      <el-table :data="rows" stripe v-loading="loading" @row-click="openDetail" style="width: 100%; cursor: pointer">
        <el-table-column prop="ticket_no" label="工单编号" width="150" />
        <el-table-column prop="title" label="主题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="category_name" label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :style="{ color: row.category_color, borderColor: row.category_color }">
              {{ row.category_name || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small" effect="light">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customer_name" label="客户" width="100" />
        <el-table-column prop="assignee_name" label="处理人" width="100">
          <template #default="{ row }">{{ row.assignee_name || '—' }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       :page-size="query.pageSize" :current-page="query.page" @current-change="onPage" />
      </div>
    </div>

    <!-- 新建工单 -->
    <el-dialog v-model="createVisible" title="新建工单" width="520px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="主题" required>
          <el-input v-model="createForm.title" placeholder="请输入工单主题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="createForm.categoryId" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="紧急" value="URGENT" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="请详细描述您遇到的问题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import api from '../api'

const route = useRoute()
const router = useRouter()

const statusMap = {
  PENDING: '待分配', PENDING_REVIEW: '待审核', ASSIGNED: '待受理', PROCESSING: '处理中',
  WAITING: '待客户确认', RESOLVED: '已解决', CLOSED: '已关闭'
}
const statusTypeMap = {
  PENDING: 'info', PENDING_REVIEW: 'warning', ASSIGNED: 'warning', PROCESSING: 'primary',
  WAITING: 'warning', RESOLVED: 'success', CLOSED: 'info'
}
const statusType = s => statusTypeMap[s] || 'info'
const priorityMap = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityTypeMap = { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }
const priorityLabel = p => priorityMap[p] || p
const priorityType = p => priorityTypeMap[p] || 'info'

const query = reactive({ keyword: '', status: '', page: 1, pageSize: 10 })
let createRequestId = ''
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const createVisible = ref(false)
const categories = ref([])
const createForm = reactive({ title: '', categoryId: null, priority: 'MEDIUM', description: '' })

function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 19) : '' }

async function load() {
  loading.value = true
  try {
    const data = await api.get('/tickets', { params: query })
    rows.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  query.page = p
  load()
}

/* 点击行进入独立的三栏详情页 */
function openDetail(row) {
  if (!row || !row.id) return
  router.push(`/tickets/${row.id}`)
}

async function loadCategories() {
  try {
    categories.value = await api.get('/tickets/meta/categories')
  } catch (e) { /* 拦截器已提示 */ }
}

function openCreate() {
  createForm.title = ''
  createForm.description = ''
  createForm.categoryId = null
  createForm.priority = 'MEDIUM'
  createVisible.value = true
}

async function doCreate() {
  if (!createForm.title || !createForm.description) {
    ElMessage.warning('请填写主题和描述')
    return
  }
  // 幂等标识：防止重复点击/网络重试导致重复建单
  if (!createRequestId) createRequestId = crypto.randomUUID()
  const r = await api.post('/tickets', createForm, { headers: { 'Idempotency-Key': createRequestId } })
  createRequestId = ''
  ElMessage.success(`工单创建成功，编号 ${r.ticketNo}`)
  createVisible.value = false
  load()
}

onMounted(() => {
  // 支持从全局搜索或首页跳转过来并带入关键字
  const kw = route.query.keyword
  if (kw) query.keyword = String(kw)
  load()
  loadCategories()
})
</script>

<style scoped>
.tickets { display: flex; flex-direction: column; gap: 16px; max-width: 1400px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; display: flex; align-items: center; justify-content: space-between; }
.filters { display: flex; gap: 12px; align-items: center; }
.table-panel { padding: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
