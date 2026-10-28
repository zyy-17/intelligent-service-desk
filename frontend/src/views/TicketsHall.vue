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
        <el-table-column prop="ticket_no" label="工单编号" width="140" />
        <el-table-column prop="title" label="主题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="link" @click="openDetail(row)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category_name" label="分类" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :style="{ color: row.category_color, borderColor: row.category_color }">
              {{ row.category_name || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="78">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small" effect="light">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="92">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customer_name" label="客户" width="88" show-overflow-tooltip />
        <el-table-column label="处理人 / 申请人" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.status === 'PENDING_REVIEW'" class="applicant">
              {{ row.applicant_name || '—' }} 申请中
            </span>
            <span v-else-if="row.assignee_name">{{ row.assignee_name }}</span>
            <span v-else class="dim">未分配</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="138">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <!-- 操作列不做 fixed 固定：横向滚动时与其他列一起滚动，宽度按最长按钮组预留不换行 -->
        <el-table-column label="操作" width="176">
          <template #default="{ row }">
            <div class="row-actions">
              <!-- 客服：未分配工单可申请 -->
              <el-button v-if="row.canApply && isAgent" type="primary" size="small" @click="doApply(row)">申请处理</el-button>
              <!-- 申请中：自己的显示等待，他人的显示占位 -->
              <el-tag v-else-if="row.status === 'PENDING_REVIEW'" size="small" :type="row.applicant_id === me.id ? 'warning' : 'info'" effect="plain">
                {{ row.applicant_id === me.id ? '审核中' : '他人申请中' }}
              </el-tag>
              <!-- 主管/管理员：待审核可直接同意分配或驳回 -->
              <template v-if="row.status === 'PENDING_REVIEW' && isReviewer">
                <el-button type="success" size="small" @click="doReview(row, true)">同意分配</el-button>
                <el-button type="danger" size="small" plain @click="doReview(row, false)">驳回</el-button>
              </template>
              <!-- 所有行都能查看只读详情（弹窗形式，不跳离大厅） -->
              <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       :page-size="query.pageSize" :current-page="query.page" @current-change="onPage" />
      </div>
    </div>

    <!-- 只读详情弹窗：查看他人负责或未分配的工单，不能改状态、不能发消息 -->
    <el-dialog v-model="detailVisible" class="hall-detail-dialog" top="12.5vh" :close-on-click-modal="false">
      <template #header>
        <div class="hd-crumb">
          <span class="hd-crumb-muted">工单大厅</span>
          <span class="hd-crumb-sep">/</span>
          <span class="hd-crumb-cur">工单详情</span>
        </div>
      </template>

      <div class="hd-body" v-loading="detailLoading">
        <template v-if="dTicket">
          <div class="hd-title-row">
            <span class="hd-no">{{ dNo }}</span>
            <span class="hd-tag" :class="'st-' + dStatus">{{ statusLabel(dStatus) }}</span>
            <span class="hd-tag" :class="'pr-' + dPriority">{{ priorityLabel(dPriority) }}</span>
            <span class="hd-cat" :style="{ color: dCategoryColor, borderColor: dCategoryColor }">{{ dCategoryName }}</span>
          </div>
          <h2 class="hd-title">{{ dTitle }}</h2>
          <p class="hd-meta">
            创建于 {{ fmtTime(dCreatedAt) }} · 来源 {{ dSourceLabel }} · 处理人 {{ dAssigneeName || '未分配' }}
          </p>

          <div class="hd-grid">
            <!-- 左栏：客户 / 属性 / 描述 / 解决方案 -->
            <div class="hd-col hd-left">
              <section class="hd-sec">
                <h4>客户信息</h4>
                <div class="hd-cust">
                  <img v-if="dCustomerAvatarUrl" class="hd-avatar-img" :src="dCustomerAvatarUrl" :alt="dCustomerName" />
                  <div v-else class="hd-avatar" :style="{ background: dCustomerAvatarColor }">
                    {{ (dCustomerName || '?').charAt(0) }}
                  </div>
                  <div class="hd-cust-info">
                    <div class="hd-cust-name">{{ dCustomerName || '未知客户' }}</div>
                    <div class="hd-cust-sub">{{ dCustomerOrg }}</div>
                  </div>
                </div>
                <ul class="hd-kv">
                  <li><span class="k">联系电话</span><span class="v mono">{{ maskPhone(dCustomerPhone) }}</span></li>
                  <li><span class="k">电子邮箱</span><span class="v ellipsis" :title="dCustomerEmail">{{ dCustomerEmail || '未填写' }}</span></li>
                </ul>
              </section>

              <section class="hd-sec">
                <h4>工单属性</h4>
                <ul class="hd-kv">
                  <li><span class="k">工单编号</span><span class="v mono">{{ dNo }}</span></li>
                  <li><span class="k">当前状态</span><span class="v">{{ statusLabel(dStatus) }}</span></li>
                  <li><span class="k">优先级</span><span class="v">{{ priorityLabel(dPriority) }}</span></li>
                  <li><span class="k">工单分类</span><span class="v">{{ dCategoryName }}</span></li>
                  <li><span class="k">负责人</span><span class="v">{{ dAssigneeName || '未分配' }}</span></li>
                  <li><span class="k">服务时效</span><span class="v strong">{{ slaText }}</span></li>
                  <li v-if="dSatisfaction"><span class="k">客户评价</span><span class="v strong">{{ '★'.repeat(dSatisfaction) }}</span></li>
                </ul>
              </section>

              <section class="hd-sec">
                <h4>问题描述</h4>
                <p class="hd-text">{{ dDescription || '暂无描述' }}</p>
              </section>

              <section class="hd-sec" v-if="dSolution">
                <h4>解决方案</h4>
                <p class="hd-text">{{ dSolution }}</p>
              </section>
            </div>

            <!-- 右栏：沟通记录 + 处理时间线 -->
            <div class="hd-col hd-right">
              <div class="hd-talk-head">
                <span>沟通记录与处理时间线</span>
                <span class="hd-count">{{ timeline.length }} 条</span>
              </div>
              <div class="hd-timeline">
                <template v-for="(it, i) in timeline" :key="i">
                  <div class="hd-sys" v-if="it.kind === 'sys'">
                    <span class="hd-sys-pill">{{ it.content }} · {{ fmtShort(it.time) }}</span>
                  </div>
                  <div class="hd-msg" v-else>
                    <div class="hd-mavatar" :style="it.avatarUrl ? {} : { background: it.avatarColor }">
                      <img v-if="it.avatarUrl" class="hd-mavatar-img" :src="it.avatarUrl" :alt="it.sender" />
                      <template v-else>{{ (it.sender || '?').charAt(0) }}</template>
                    </div>
                    <div class="hd-mbody">
                      <div class="hd-mmeta">
                        <span class="hd-sender">{{ it.sender }}</span>
                        <span class="hd-role" :class="it.isCustomer ? 'cust' : 'staff'">{{ it.isCustomer ? '客户' : '客服' }}</span>
                        <span class="hd-mtime">{{ fmtShort(it.time) }}</span>
                      </div>
                      <div class="hd-bubble">{{ it.content }}</div>
                    </div>
                  </div>
                </template>
                <div class="hd-empty" v-if="!timeline.length">暂无沟通记录</div>
              </div>
            </div>
          </div>
        </template>
        <div class="hd-loading-text" v-else-if="!detailLoading">工单不存在或无权查看</div>
      </div>

      <template #footer>
        <div class="hd-foot">
          <span class="hd-foot-tip">只读视图 · 内部备注仅本工单处理人与主管可见</span>
          <div class="hd-foot-actions">
            <el-button v-if="detailRow?.canApply && isAgent" type="primary" @click="applyFromDetail">申请处理</el-button>
            <el-button @click="detailVisible = false">关闭</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

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

/* ===== 只读详情弹窗：大厅里都是他人工单或未分配工单，只能看，不能改状态/发消息 ===== */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailRow = ref(null)

/* 打开弹窗并拉取完整详情（后端会按角色过滤内部备注） */
async function openDetail(row) {
  if (!row?.id) return
  detailRow.value = row
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await api.get(`/tickets/${row.id}`)
  } catch (e) {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/* 兼容后端两种命名：实体序列化为驼峰，JdbcTemplate 返回下划线 */
const val = (obj, key) => {
  if (!obj) return undefined
  const v = obj[key]
  if (v !== undefined && v !== null) return v
  return obj[key.replace(/[A-Z]/g, c => '_' + c.toLowerCase())]
}

const dTicket = computed(() => detail.value?.ticket || null)
const dId = computed(() => val(dTicket.value, 'id'))
const dNo = computed(() => val(dTicket.value, 'ticketNo') || '—')
const dTitle = computed(() => val(dTicket.value, 'title') || '')
const dStatus = computed(() => val(dTicket.value, 'status') || '')
const dPriority = computed(() => val(dTicket.value, 'priority') || 'MEDIUM')
const dDescription = computed(() => val(dTicket.value, 'description') || '')
const dSolution = computed(() => val(dTicket.value, 'solution') || '')
const dCategoryName = computed(() => val(dTicket.value, 'categoryName') || '未分类')
const dCategoryColor = computed(() => val(dTicket.value, 'categoryColor') || '#6366f1')
const dAssigneeName = computed(() => val(dTicket.value, 'assigneeName') || '')
const dCustomerName = computed(() => val(dTicket.value, 'customerName') || '')
const dCustomerPhone = computed(() => val(dTicket.value, 'customerPhone') || '')
const dCustomerEmail = computed(() => val(dTicket.value, 'customerEmail') || '')
const dCustomerCompany = computed(() => val(dTicket.value, 'customerCompany') || '')
const dCustomerDept = computed(() => val(dTicket.value, 'customerDepartment') || '')
const dCustomerAvatarUrl = computed(() => val(dTicket.value, 'customerAvatarUrl') || '')
const dCustomerAvatarColor = computed(() => val(dTicket.value, 'customerAvatarColor') || dCategoryColor.value)
const dCreatedAt = computed(() => val(dTicket.value, 'createdAt'))
const dSatisfaction = computed(() => val(dTicket.value, 'satisfaction') || 0)
const dCustomerOrg = computed(() => {
  const parts = [dCustomerCompany.value, dCustomerDept.value].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未填写所属公司'
})
const sourceMap = { WEB: '网页提交', PHONE: '电话', EMAIL: '邮件', CHAT: '在线咨询' }
const dSourceLabel = computed(() => {
  const s = val(dTicket.value, 'source')
  return sourceMap[s] || s || '网页提交'
})

/* 服务时效：已解决看是否超时，未解决看剩余时长 */
const dSlaResolveAt = computed(() => val(dTicket.value, 'slaResolveAt'))
const dResolvedAt = computed(() => val(dTicket.value, 'resolvedAt'))
const slaText = computed(() => {
  if (!dSlaResolveAt.value) return '未设置'
  const target = new Date(dSlaResolveAt.value).getTime()
  if (Number.isNaN(target)) return '未设置'
  const done = dResolvedAt.value ? new Date(dResolvedAt.value).getTime() : 0
  if (done) return done <= target ? '按时解决' : `超时 ${fmtDur(done - target)}`
  const left = target - Date.now()
  return left < 0 ? `已超时 ${fmtDur(-left)}` : `剩余 ${fmtDur(left)}`
})

const actionMap = {
  CREATE: '创建工单', ASSIGN: '分配工单', ACCEPT: '客服接单', REPLY: '回复工单',
  RESOLVE: '提交解决方案', CONFIRM: '客户确认', CLOSE: '关闭工单',
  REOPEN: '重新打开', RATE: '客户评价', URGE: '客户催办',
  APPLY: '申请处理', REVIEW: '申请审核'
}

/* 会话消息与处理时间线合并成一条按时间排序的流水，便于看清这个工单经历了什么 */
const timeline = computed(() => {
  const items = []
  for (const m of detail.value?.messages || []) {
    items.push({
      kind: 'msg',
      sender: val(m, 'senderName') || '用户',
      isCustomer: val(m, 'senderRole') === 'CUSTOMER',
      avatarUrl: val(m, 'senderAvatarUrl'),
      avatarColor: val(m, 'senderAvatarColor') || '#94a3b8',
      time: val(m, 'createdAt'),
      content: m.content
    })
  }
  for (const h of detail.value?.histories || []) {
    const opName = val(h, 'operatorName')
    const opRole = val(h, 'operatorRole')
    const remark = val(h, 'remark')
    const action = val(h, 'action')
    const opLabel = opName ? `${opName}（${opRole === 'CUSTOMER' ? '客户' : '客服'}）` : ''
    items.push({
      kind: 'sys',
      time: val(h, 'createdAt'),
      content: (opLabel ? opLabel + ' ' : '') + (actionMap[action] || action) + (remark ? `：${remark}` : '')
    })
  }
  return items.sort((a, b) => new Date(a.time || 0) - new Date(b.time || 0))
})

/* 时长格式化：分钟 / 小时分 / 天小时 */
function fmtDur(ms) {
  if (ms <= 0) return '0 分钟'
  const m = Math.floor(ms / 60000)
  if (m < 60) return `${m} 分钟`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时 ${m % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}

/* 客服端手机号脱敏，避免在公共大厅里暴露完整号码 */
function maskPhone(p) {
  if (!p) return '未填写'
  const s = String(p)
  return s.length >= 7 ? `${s.slice(0, 3)} **** ${s.slice(-4)}` : s
}

function fmtShort(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/* 弹窗内申请：成功则关闭弹窗并刷新列表 */
async function applyFromDetail() {
  const ok = await doApply({ id: dId.value, title: dTitle.value })
  if (ok) detailVisible.value = false
}

/* 申请负责处理：提交后工单进入待审核；返回是否申请成功 */
async function doApply(row) {
  try {
    await ElMessageBox.confirm(
      `确认申请负责处理「${row.title}」？申请后工单进入待审核，需主管或管理员审核通过后才会分配给你。`,
      '申请处理', { type: 'warning', confirmButtonText: '提交申请', cancelButtonText: '取消' }
    )
  } catch (e) { return false }
  await api.post(`/tickets/${row.id}/apply`)
  ElMessage.success('申请已提交，等待审核')
  load()
  return true
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
/* 操作列按钮横向排列不换行，间距由 gap 统一控制 */
.row-actions { display: flex; align-items: center; gap: 6px; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.link { color: var(--primary); cursor: pointer; }
.link:hover { text-decoration: underline; }
.applicant { color: #b45309; font-size: 12.5px; }
.dim { color: var(--text-muted); font-size: 12.5px; }

/* ===== 只读详情弹窗（弹窗本体的尺寸与居中在下方非 scoped 块里） ===== */
.hd-crumb { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.hd-crumb-muted { color: #94a3b8; }
.hd-crumb-sep { color: #cbd5e1; }
.hd-crumb-cur { color: #1e293b; font-weight: 600; }
.hd-body { position: relative; display: flex; flex-direction: column; flex: 1; min-height: 0; }
.hd-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; flex: none; }
.hd-no { font-family: ui-monospace, monospace; font-size: 12px; color: #64748b; }
.hd-title { font-size: 19px; font-weight: 600; color: #0f172a; margin: 10px 0 0; line-height: 1.45; flex: none; }
.hd-meta { font-size: 12px; color: #94a3b8; margin: 6px 0 0; flex: none; }
.hd-tag {
  font-size: 11px; padding: 2px 9px; border-radius: 999px;
  border: 1px solid transparent; white-space: nowrap;
}
.hd-cat { font-size: 11px; padding: 0 7px; line-height: 19px; border: 1px solid; border-radius: 4px; }

.hd-grid {
  flex: 1; min-height: 0; margin-top: 14px;
  display: grid; grid-template-columns: 34% minmax(0, 1fr); gap: 16px;
  border-top: 1px solid #eef2f7; padding-top: 14px;
}
.hd-col { min-height: 0; }
.hd-left { overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding-right: 4px; }
.hd-right { display: flex; flex-direction: column; min-height: 0; }

.hd-sec { border: 1px solid #eef2f7; border-radius: 10px; padding: 12px 13px; background: #fcfdff; flex: none; }
.hd-sec h4 {
  font-size: 11px; font-weight: 600; letter-spacing: 0.05em;
  color: #94a3b8; margin: 0 0 9px; text-transform: uppercase;
}
.hd-cust { display: flex; align-items: center; gap: 10px; }
.hd-avatar, .hd-avatar-img { width: 42px; height: 42px; border-radius: 50%; flex: none; }
.hd-avatar {
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 17px; font-weight: 600;
}
.hd-avatar-img { object-fit: cover; border: 1px solid #e8eef5; }
.hd-cust-name { font-size: 14px; font-weight: 600; color: #0f172a; }
.hd-cust-sub { font-size: 11px; color: #64748b; margin-top: 2px; }

.hd-kv { list-style: none; margin: 10px 0 0; padding: 0; }
.hd-kv li {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 5px 0; font-size: 12px; border-bottom: 1px solid #f4f7fb;
}
.hd-kv li:last-child { border-bottom: none; }
.hd-kv .k { color: #64748b; flex: none; }
.hd-kv .v { color: #334155; text-align: right; min-width: 0; }
.hd-kv .v.strong { font-weight: 600; }
.mono { font-family: ui-monospace, monospace; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 190px; }
.hd-text { font-size: 12.5px; line-height: 1.8; color: #475569; margin: 0; white-space: pre-wrap; word-break: break-word; }

.hd-talk-head {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 12px; color: #64748b; padding: 0 2px 8px;
  border-bottom: 1px solid #eef2f7; flex: none;
}
.hd-count { font-size: 11px; color: #94a3b8; }
.hd-timeline {
  flex: 1; min-height: 0; overflow-y: auto;
  padding: 12px 4px 4px 0; display: flex; flex-direction: column; gap: 11px;
}
.hd-sys { display: flex; justify-content: center; }
.hd-sys-pill {
  font-size: 11px; color: #94a3b8; background: #f1f5f9;
  padding: 3px 10px; border-radius: 999px; text-align: center;
}
.hd-msg { display: flex; gap: 9px; }
.hd-mavatar {
  width: 30px; height: 30px; border-radius: 50%; flex: none;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 12px; font-weight: 600; overflow: hidden;
}
.hd-mavatar-img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.hd-mbody { min-width: 0; flex: 1; }
.hd-mmeta { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.hd-sender { font-size: 12px; font-weight: 600; color: #334155; }
.hd-role { font-size: 10px; padding: 0 5px; border-radius: 3px; }
.hd-role.cust { background: #dbeafe; color: #1d4ed8; }
.hd-role.staff { background: #dcfce7; color: #15803d; }
.hd-mtime { font-size: 10px; color: #94a3b8; margin-left: auto; }
.hd-bubble {
  font-size: 12.5px; line-height: 1.75; color: #334155;
  background: #f8fafc; border: 1px solid #eef2f7;
  padding: 9px 12px; border-radius: 10px; border-top-left-radius: 4px;
  white-space: pre-wrap; word-break: break-word;
}
.hd-empty { text-align: center; font-size: 12px; color: #94a3b8; padding: 36px 0; }
.hd-loading-text { text-align: center; font-size: 13px; color: #94a3b8; padding: 60px 0; }

.hd-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.hd-foot-tip { font-size: 12px; color: #94a3b8; }
.hd-foot-actions { display: flex; gap: 8px; }

/* 状态与优先级徽章 */
.st-PENDING { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
.st-PENDING_REVIEW { background: #f5f3ff; color: #6d28d9; border-color: #ddd6fe; }
.st-ASSIGNED { background: #fef3c7; color: #92400e; border-color: #fde68a; }
.st-PROCESSING { background: #dbeafe; color: #1d4ed8; border-color: #bfdbfe; }
.st-WAITING { background: #fef3c7; color: #92400e; border-color: #fde68a; }
.st-RESOLVED { background: #d1fae5; color: #047857; border-color: #a7f3d0; }
.st-CLOSED { background: #f1f5f9; color: #64748b; border-color: #e2e8f0; }
.pr-URGENT { background: #fee2e2; color: #b91c1c; border-color: #fecaca; font-weight: 600; }
.pr-HIGH { background: #ffedd5; color: #c2410c; border-color: #fed7aa; font-weight: 600; }
.pr-MEDIUM { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
.pr-LOW { background: #f8fafc; color: #64748b; border-color: #e2e8f0; }

@media (max-width: 1280px) {
  .hd-grid { grid-template-columns: 300px minmax(0, 1fr); }
}
</style>

<style>
/* 工单详情弹窗：宽高各取主内容区（左侧导航 230px 之外）的 75%，并在右侧区域水平垂直居中 */
.hall-detail-dialog {
  width: calc((100vw - 230px) * 0.75) !important;
  max-width: none;
  height: 75vh;
  display: flex;
  flex-direction: column;
  margin-left: calc(230px + (100vw - 230px) * 0.125) !important;
  margin-right: 0 !important;
  padding: 0;
  border-radius: 16px;
  overflow: hidden;
}
.hall-detail-dialog .el-dialog__header {
  flex: none; margin: 0; padding: 16px 24px 12px;
  border-bottom: 1px solid #eef2f7;
}
.hall-detail-dialog .el-dialog__body {
  flex: 1; min-height: 0; overflow: hidden;
  padding: 14px 24px 0;
  display: flex; flex-direction: column;
}
.hall-detail-dialog .el-dialog__footer {
  flex: none; padding: 12px 24px 16px;
  border-top: 1px solid #eef2f7;
}
@media (max-width: 1280px) {
  .hall-detail-dialog {
    width: calc((100vw - 230px) * 0.94) !important;
    margin-left: calc(230px + (100vw - 230px) * 0.03) !important;
  }
}
</style>
