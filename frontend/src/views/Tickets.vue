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

    <!-- 列表 -->
    <div class="panel-card table-panel">
      <el-table :data="rows" stripe v-loading="loading" @row-click="openDetail" style="width: 100%; cursor: pointer">
        <el-table-column prop="ticket_no" label="工单编号" width="120" />
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="620px">
      <div v-if="detail" class="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编号">{{ detail.ticket.ticket_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.ticket.status)" size="small">{{ statusMap[detail.ticket.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">{{ priorityLabel(detail.ticket.priority) }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.ticket.category_name || '未分类' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.ticket.customer_name }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.ticket.assignee_name || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ fmtTime(detail.ticket.created_at) }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ detail.ticket.description }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.ticket.solution" label="解决方案" :span="2">{{ detail.ticket.solution }}</el-descriptions-item>
        </el-descriptions>

        <!-- 操作区 -->
        <div class="action-bar">
          <el-button v-if="isStaff" size="small" :icon="MagicStick" :loading="summarizing" @click="doSummarize">
            AI 摘要
          </el-button>
          <template v-if="isStaff && detail.ticket.status === 'PENDING'">
            <el-select v-model="assignTarget" placeholder="选择处理人" size="small" style="width: 140px">
              <el-option v-for="a in agents" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
            <el-button type="primary" size="small" @click="doAssign">分配</el-button>
          </template>
          <template v-if="isStaff && detail.ticket.status === 'ASSIGNED' && detail.ticket.assignee_id === me.id">
            <el-button type="primary" size="small" @click="doAccept">接单</el-button>
          </template>
          <template v-if="isStaff && detail.ticket.status === 'PROCESSING'">
            <el-button type="primary" size="small" @click="resolveVisible = true">提交解决方案</el-button>
          </template>
          <template v-if="isCustomer && detail.ticket.status === 'WAITING'">
            <el-button type="success" size="small" @click="doConfirm(true)">确认已解决</el-button>
            <el-button type="warning" size="small" @click="doConfirm(false)">未解决</el-button>
          </template>
          <template v-if="isCustomer && ['PENDING','ASSIGNED','PROCESSING'].includes(detail.ticket.status)">
            <el-button type="warning" size="small" :icon="Bell" @click="doUrge">催办</el-button>
          </template>
          <template v-if="isStaff && detail.ticket.status === 'RESOLVED'">
            <el-button type="success" size="small" @click="doClose">关闭工单</el-button>
          </template>
        </div>

        <!-- AI 摘要 -->
        <el-alert v-if="aiSummary" type="info" :closable="false" class="ai-summary">
          <template #title><span class="ai-summary-title">AI 摘要</span></template>
          {{ aiSummary }}
        </el-alert>

        <!-- 消息记录 -->
        <div class="msg-title">沟通记录</div>
        <div class="msg-list">
          <div v-for="m in detail.messages" :key="m.id" class="msg-item"
               :class="m.message_type === 'INTERNAL' ? 'internal' : (m.sender_id === me.id ? 'mine' : '')">
            <div class="msg-head">
              <span class="msg-sender">{{ m.sender_name }}</span>
              <el-tag v-if="m.message_type === 'INTERNAL'" size="small" type="warning">内部备注</el-tag>
              <span class="msg-time">{{ fmtTime(m.created_at) }}</span>
            </div>
            <div class="msg-content">{{ m.content }}</div>
          </div>
        </div>

        <!-- 回复 -->
        <div class="reply-box" v-if="detail.ticket.status !== 'CLOSED'">
          <el-radio-group v-if="isStaff" v-model="replyType" size="small" style="margin-bottom: 8px">
            <el-radio-button value="PUBLIC">公开回复</el-radio-button>
            <el-radio-button value="INTERNAL">内部备注</el-radio-button>
          </el-radio-group>
          <el-input v-model="replyContent" type="textarea" :rows="3" placeholder="输入回复内容..." />
          <div style="margin-top: 8px; display: flex; gap: 8px; justify-content: flex-end;">
            <el-button v-if="isStaff" size="small" :icon="MagicStick" :loading="drafting" @click="doDraft">AI 草稿</el-button>
            <el-button type="primary" @click="doReply">发送</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

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

    <!-- 解决方案 -->
    <el-dialog v-model="resolveVisible" title="提交解决方案" width="480px">
      <el-input v-model="solution" type="textarea" :rows="4" placeholder="请输入解决方案..." />
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" @click="doResolve">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, MagicStick, Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
const me = computed(() => auth.user || {})

const statusMap = {
  PENDING: '待分配', ASSIGNED: '待受理', PROCESSING: '处理中',
  WAITING: '待客户确认', RESOLVED: '已解决', CLOSED: '已关闭'
}
const statusTypeMap = { PENDING: 'info', ASSIGNED: 'warning', PROCESSING: 'primary', WAITING: 'warning', RESOLVED: 'success', CLOSED: 'info' }
const statusType = s => statusTypeMap[s] || 'info'
const priorityMap = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityTypeMap = { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }
const priorityLabel = p => priorityMap[p] || p
const priorityType = p => priorityTypeMap[p] || 'info'
const isStaff = computed(() => ['ADMIN', 'SUPERVISOR', 'AGENT'].includes(auth.role))
const isCustomer = computed(() => auth.role === 'CUSTOMER')

const query = reactive({ keyword: '', status: '', page: 1, pageSize: 10 })
let createRequestId = ''
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)
const detailTitle = ref('')
const agents = ref([])
const categories = ref([])
const assignTarget = ref(null)
const replyType = ref('PUBLIC')
const replyContent = ref('')

const createVisible = ref(false)
const createForm = reactive({ title: '', categoryId: null, priority: 'MEDIUM', description: '' })
const resolveVisible = ref(false)
const solution = ref('')

// AI 摘要 / 草稿
const aiSummary = ref('')
const summarizing = ref(false)
const drafting = ref(false)

function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 19) : '' }

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

async function openDetail(row) {
  detail.value = await api.get(`/tickets/${row.id}`)
  detailTitle.value = `工单 ${row.ticket_no}`
  detailVisible.value = true
  replyContent.value = ''
}

async function loadMeta() {
  agents.value = await api.get('/tickets/meta/agents')
  categories.value = await api.get('/tickets/meta/categories')
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

async function doAssign() {
  if (!assignTarget.value) { ElMessage.warning('请选择处理人'); return }
  await api.post(`/tickets/${detail.value.ticket.id}/assign`, { assigneeId: assignTarget.value })
  ElMessage.success('分配成功')
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doAccept() {
  await api.post(`/tickets/${detail.value.ticket.id}/accept`)
  ElMessage.success('已接单')
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doResolve() {
  if (!solution.value) { ElMessage.warning('请输入解决方案'); return }
  await api.post(`/tickets/${detail.value.ticket.id}/resolve`, { solution: solution.value })
  ElMessage.success('已提交解决方案')
  resolveVisible.value = false
  solution.value = ''
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doConfirm(solved) {
  if (!solved) {
    const { value } = await ElMessageBox.prompt('请说明未解决的原因', '重新打开工单')
    await api.post(`/tickets/${detail.value.ticket.id}/confirm`, { solved: false, feedback: value })
  } else {
    await api.post(`/tickets/${detail.value.ticket.id}/confirm`, { solved: true })
    ElMessage.success('已确认解决')
  }
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doClose() {
  await api.post(`/tickets/${detail.value.ticket.id}/close`)
  ElMessage.success('工单已关闭')
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doUrge() {
  let value = ''
  try {
    const r = await ElMessageBox.prompt('可填写催办原因（可选）', '催办工单', {
      confirmButtonText: '催办', cancelButtonText: '取消', inputPlaceholder: '请输入催办原因'
    })
    value = r.value || ''
  } catch (e) {
    return // 用户取消
  }
  await api.post(`/tickets/${detail.value.ticket.id}/urge`, { reason: value })
  ElMessage.success('已催办，客服将尽快处理')
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
  load()
}

async function doReply() {
  if (!replyContent.value.trim()) { ElMessage.warning('请输入回复内容'); return }
  await api.post(`/tickets/${detail.value.ticket.id}/messages`, {
    content: replyContent.value, messageType: replyType.value
  })
  replyContent.value = ''
  detail.value = await api.get(`/tickets/${detail.value.ticket.id}`)
}

async function doSummarize() {
  const t = detail.value.ticket
  summarizing.value = true
  aiSummary.value = ''
  try {
    const r = await api.post('/ai/summarize', { title: t.title, description: t.description })
    aiSummary.value = r.summary || '（未能生成摘要）'
  } catch (e) {
    ElMessage.warning('AI 摘要生成失败，请确认 AI 服务已启动')
  } finally {
    summarizing.value = false
  }
}

async function doDraft() {
  const t = detail.value.ticket
  drafting.value = true
  try {
    const r = await api.post('/ai/draft', { title: t.title, description: t.description })
    replyContent.value = r.draft || replyContent.value
  } catch (e) {
    ElMessage.warning('AI 草稿生成失败，请确认 AI 服务已启动')
  } finally {
    drafting.value = false
  }
}

onMounted(() => {
  // 支持从全局搜索跳转过来并带入关键字
  const kw = useRoute().query.keyword
  if (kw) query.keyword = String(kw)
  load()
  loadMeta()
})
</script>

<style scoped>
.tickets { display: flex; flex-direction: column; gap: 16px; max-width: 1400px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; display: flex; align-items: center; justify-content: space-between; }
.filters { display: flex; gap: 12px; align-items: center; }
.table-panel { padding: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }

.detail { display: flex; flex-direction: column; gap: 16px; }
.action-bar { display: flex; gap: 10px; flex-wrap: wrap; }
.msg-title { font-size: 15px; font-weight: 600; margin-top: 8px; }
.msg-list { display: flex; flex-direction: column; gap: 10px; max-height: 320px; overflow-y: auto; }
.msg-item { background: #f9fafb; border-radius: 8px; padding: 10px 12px; }
.msg-item.internal { background: #fffbeb; border: 1px dashed #f59e0b; }
.msg-item.mine { background: #eef2ff; }
.msg-head { display: flex; align-items: center; gap: 8px; }
.msg-sender { font-weight: 600; font-size: 13px; }
.msg-time { color: var(--text-muted); font-size: 11px; margin-left: auto; }
.msg-content { margin-top: 6px; font-size: 13px; white-space: pre-wrap; }
.reply-box { margin-top: 8px; }
.ai-summary { margin-top: 4px; }
.ai-summary-title { font-weight: 600; margin-right: 4px; }
</style>
