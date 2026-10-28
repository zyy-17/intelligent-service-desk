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
      <div class="toolbar-right">
        <!-- 待审核入口：仅主管与管理员可见，再次点击取消筛选 -->
        <el-button v-if="isReviewer"
                   :type="query.status === 'PENDING_REVIEW' ? 'warning' : 'default'"
                   :icon="Finished" @click="togglePendingReview">
          待审核
          <span v-if="pendingCount > 0" class="pending-badge">{{ pendingCount }}</span>
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建工单</el-button>
      </div>
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
        <el-table-column prop="status" label="状态" width="126">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            <div v-if="row.status === 'PENDING_REVIEW' && row.applicant_name" class="apply-tip">
              {{ row.applicant_name }} 申请中
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="customer_name" label="客户" width="100" />
        <el-table-column prop="assignee_name" label="处理人" width="100">
          <template #default="{ row }">{{ row.assignee_name || '—' }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <!-- 待审核工单在列表内直接审核，不用进详情页 -->
        <el-table-column v-if="isReviewer" label="操作" width="150">
          <template #default="{ row }">
            <div v-if="row.status === 'PENDING_REVIEW'" class="row-actions" @click.stop>
              <el-button size="small" type="primary" @click="doReview(row, true)">同意</el-button>
              <el-button size="small" @click="doReview(row, false)">驳回</el-button>
            </div>
            <span v-else class="muted">—</span>
          </template>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Finished } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 接单申请审核仅主管与管理员可操作
const isReviewer = computed(() => ['ADMIN', 'SUPERVISOR'].includes(auth.role))
const pendingCount = ref(0)

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
    loadPendingCount()
  } finally {
    loading.value = false
  }
}

/* 待审核数量角标：仅主管与管理员有数据，其他角色接口直接返回 0 */
async function loadPendingCount() {
  if (!isReviewer.value) return
  try {
    pendingCount.value = await api.get('/tickets/pending-review/count')
  } catch (e) { /* 角标失败不影响列表 */ }
}

/* 待审核筛选开关：再点一次回到全部状态 */
function togglePendingReview() {
  query.status = query.status === 'PENDING_REVIEW' ? '' : 'PENDING_REVIEW'
  query.page = 1
  load()
}

/* 审核接单申请：通过则工单归申请人，驳回退回待分配 */
async function doReview(row, approve) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt(
      approve ? `确认将该工单分配给「${row.applicant_name || '申请人'}」？可填写备注。`
              : '请填写驳回原因（该工单将退回待分配状态）',
      approve ? '同意分配' : '审核驳回',
      {
        inputPlaceholder: approve ? '备注（可选）' : '驳回原因',
        inputValidator: v => (approve || (v && v.trim())) ? true : '请填写驳回原因',
        type: approve ? 'info' : 'warning'
      }
    )
    remark = r.value || ''
  } catch (e) { return }
  await api.post(`/tickets/${row.id}/review`, { approve, remark })
  ElMessage.success(approve ? '已通过，工单已分配给申请人' : '已驳回，工单退回待分配')
  load()
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
  // 支持 AI 助手「没有可靠依据」时一键跳转过来直接打开新建工单弹窗
  if (route.query.create) openCreate()
})
</script>

<style scoped>
.tickets { display: flex; flex-direction: column; gap: 16px; max-width: 1400px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.filters { display: flex; gap: 12px; align-items: center; }
.toolbar-right { display: flex; align-items: center; gap: 10px; }
.pending-badge {
  display: inline-block;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  padding: 0 5px;
  margin-left: 4px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  text-align: center;
}
.apply-tip { font-size: 11.5px; color: #b45309; margin-top: 4px; }
.row-actions { display: flex; align-items: center; gap: 6px; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.muted { color: var(--text-muted); }
.table-panel { padding: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
