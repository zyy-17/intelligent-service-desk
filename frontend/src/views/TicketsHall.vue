<template>
  <div class="hall">
    <!-- 工具栏 -->
    <div class="toolbar panel-card">
      <div class="filters">
        <el-input v-model="query.keyword" placeholder="搜索工单编号 / 标题" clearable style="width: 240px"
                  :prefix-icon="Search" @keyup.enter="reload" @clear="reload" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-radio-group v-model="tab" @change="reload">
        <el-radio-button value="">全部工单</el-radio-button>
        <el-radio-button value="PENDING">未分配（可申请）</el-radio-button>
        <el-radio-button value="PENDING_REVIEW">待审核</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 说明条 -->
    <div class="tip-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>这里可以看到全团队的工单。未分配的工单可以申请负责处理，提交后进入「待审核」，由主管或管理员审核通过后归你处理。</span>
    </div>

    <!-- 列表 -->
    <div class="panel-card table-panel">
      <el-table :data="rows" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="ticket_no" label="工单编号" width="150" />
        <el-table-column prop="title" label="主题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="link" @click="openDetail(row)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category_name" label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :style="{ color: row.category_color, borderColor: row.category_color }">
              {{ row.category_name || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="85">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small" effect="light">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customer_name" label="客户" width="95" />
        <el-table-column label="处理人 / 申请人" width="150">
          <template #default="{ row }">
            <span v-if="row.status === 'PENDING_REVIEW'" class="applicant">
              {{ row.applicant_name || '—' }} 申请中
            </span>
            <span v-else-if="row.assignee_name">{{ row.assignee_name }}</span>
            <span v-else class="dim">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="145">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <!-- 客服：未分配工单可申请 -->
            <el-button v-if="row.canApply && isAgent" type="primary" size="small" @click="doApply(row)">申请处理</el-button>
            <!-- 申请中：自己的显示等待，他人的显示占位 -->
            <el-tag v-else-if="row.status === 'PENDING_REVIEW'" size="small" :type="row.applicant_id === me.id ? 'warning' : 'info'" effect="plain">
              {{ row.applicant_id === me.id ? '审核中' : '他人申请中' }}
            </el-tag>
            <!-- 主管/管理员：待审核可直接通过或驳回 -->
            <template v-if="row.status === 'PENDING_REVIEW' && isReviewer">
              <el-button type="success" size="small" @click="doReview(row, true)">通过</el-button>
              <el-button type="danger" size="small" plain @click="doReview(row, false)">驳回</el-button>
            </template>
            <el-button v-if="!row.canApply && row.status !== 'PENDING_REVIEW'" link type="primary" size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       :page-size="query.pageSize" :current-page="query.page" @current-change="onPage" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const router = useRouter()
const auth = useAuthStore()
const isAgent = computed(() => auth.role === 'AGENT')
const isReviewer = computed(() => ['ADMIN', 'SUPERVISOR'].includes(auth.role))
const me = computed(() => auth.user || {})

const statusMap = {
  PENDING: '待分配', PENDING_REVIEW: '待审核', ASSIGNED: '待受理', PROCESSING: '处理中',
  WAITING: '待客户确认', RESOLVED: '已解决', CLOSED: '已关闭'
}
const statusTypeMap = {
  PENDING: 'info', PENDING_REVIEW: 'warning', ASSIGNED: 'warning', PROCESSING: 'primary',
  WAITING: 'warning', RESOLVED: 'success', CLOSED: 'info'
}
const statusLabel = s => statusMap[s] || s
const statusType = s => statusTypeMap[s] || 'info'
const priorityMap = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityTypeMap = { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }
const priorityLabel = p => priorityMap[p] || p
const priorityType = p => priorityTypeMap[p] || 'info'

const tab = ref('')
const query = reactive({ keyword: '', page: 1, pageSize: 10 })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '' }

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, pageSize: query.pageSize }
    if (tab.value) params.status = tab.value
    if (query.keyword) params.keyword = query.keyword
    const data = await api.get('/tickets/hall', { params })
    rows.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() { query.page = 1; load() }
function onPage(p) { query.page = p; load() }
function openDetail(row) { if (row?.id) router.push(`/tickets/${row.id}`) }

/* 申请负责处理：提交后工单进入待审核 */
async function doApply(row) {
  try {
    await ElMessageBox.confirm(
      `确认申请负责处理「${row.title}」？申请后工单进入待审核，需主管或管理员审核通过后才会分配给你。`,
      '申请处理', { type: 'warning', confirmButtonText: '提交申请', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  await api.post(`/tickets/${row.id}/apply`)
  ElMessage.success('申请已提交，等待审核')
  load()
}

/* 主管/管理员审核：通过则工单归申请人，驳回则退回待分配 */
async function doReview(row, approve) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt(
      approve ? `确认将工单分配给「${row.applicant_name || '申请人'}」？可填写备注。` : `请填写驳回原因（该工单将退回待分配状态）`,
      approve ? '审核通过' : '审核驳回',
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

onMounted(load)
</script>

<style scoped>
.hall { display: flex; flex-direction: column; gap: 14px; max-width: 1400px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.filters { display: flex; gap: 12px; align-items: center; }
.tip-bar {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-radius: 10px;
  background: #eff6ff; border: 1px solid #dbeafe;
  font-size: 12.5px; color: #1e40af; line-height: 1.6;
}
.table-panel { padding: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.link { color: var(--primary); cursor: pointer; }
.link:hover { text-decoration: underline; }
.applicant { color: #b45309; font-size: 12.5px; }
.dim { color: var(--text-muted); font-size: 12.5px; }
</style>
