<template>
  <div class="team">
    <!-- 顶部指标：一眼看清积压与超时 -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card">
        <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
          <el-icon><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 客服负载：在办/超时/今日解决，超时的卡片标红 -->
    <div class="panel-card">
      <div class="panel-head">
        <span class="panel-title">客服负载</span>
        <span class="panel-sub">在办工单与超时情况，红色表示该客服名下有工单已超 SLA</span>
        <el-button text :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
      <div class="agent-grid" v-loading="loadingOverview">
        <div v-for="a in agents" :key="a.id" class="agent-card" :class="{ warn: a.overdue > 0 }">
          <div class="agent-top">
            <span class="agent-avatar" :style="{ background: a.avatar_color || '#3b82f6' }">{{ initial(a.name) }}</span>
            <div class="agent-name-box">
              <div class="agent-name">{{ a.name }}</div>
              <div class="agent-dept">{{ a.department || '客服组' }}</div>
            </div>
            <span v-if="a.overdue > 0" class="agent-flag">{{ a.overdue }} 超时</span>
          </div>
          <div class="agent-metrics">
            <div class="metric">
              <div class="metric-value">{{ a.active || 0 }}</div>
              <div class="metric-label">在办</div>
            </div>
            <div class="metric">
              <div class="metric-value">{{ a.assigned_today || 0 }}</div>
              <div class="metric-label">今日分派</div>
            </div>
            <div class="metric">
              <div class="metric-value success">{{ a.resolved_today || 0 }}</div>
              <div class="metric-label">今日解决</div>
            </div>
            <div class="metric">
              <div class="metric-value">{{ a.avg_rating || '—' }}</div>
              <div class="metric-label">近评均分</div>
            </div>
          </div>
        </div>
        <el-empty v-if="!loadingOverview && !agents.length" description="暂无启用中的客服账号" :image-size="64" />
      </div>
    </div>

    <!-- 工单队列：待分配可直接派单，超时与临期供跟进 -->
    <div class="panel-card">
      <el-tabs v-model="tab" @tab-change="loadTickets">
        <el-tab-pane :label="`待分配 ${stats.unassigned || 0}`" name="unassigned" />
        <el-tab-pane :label="`已超时 ${stats.overdue || 0}`" name="overdue" />
        <el-tab-pane :label="`即将超时 ${stats.dueSoon || 0}`" name="dueSoon" />
      </el-tabs>

      <div v-if="tab === 'unassigned'" class="bulk-bar">
        <span class="bulk-count">已选 {{ selected.length }} 单</span>
        <el-select v-model="bulkAssignee" placeholder="选择客服" clearable style="width: 190px">
          <el-option v-for="a in agentOptions" :key="a.id" :label="`${a.name}（在办 ${a.active || 0}）`" :value="a.id" />
        </el-select>
        <el-button type="primary" :disabled="!selected.length || !bulkAssignee" :loading="assigning"
                   @click="doBulkAssign">批量指派</el-button>
        <el-button :disabled="!selected.length" @click="clearSelection">清空选择</el-button>
        <span class="bulk-tip">也可直接在行内选人指派</span>
      </div>

      <el-table ref="tableRef" :data="tickets" v-loading="loadingTickets" size="small" stripe
                row-key="id" @selection-change="onSelectionChange">
        <el-table-column v-if="tab === 'unassigned'" type="selection" width="44" reserve-selection />
        <el-table-column prop="ticket_no" label="工单号" width="146" />
        <el-table-column prop="title" label="工单主题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="104">
          <template #default="{ row }">
            <el-tag size="small" effect="light"
                    :style="{ color: row.category_color || '#64748b', borderColor: row.category_color || '#cbd5e1' }">
              {{ row.category_name || '未分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="86">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small" effect="light">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customer_name" label="客户" width="92" show-overflow-tooltip />
        <el-table-column v-if="tab !== 'unassigned'" prop="assignee_name" label="处理人" width="92">
          <template #default="{ row }">{{ row.assignee_name || '未分配' }}</template>
        </el-table-column>
        <el-table-column label="解决时限" width="132">
          <template #default="{ row }">
            <span class="remain" :class="remainClass(row.remain_minutes)">
              {{ remainText(row.remain_minutes) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="132">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="tab === 'unassigned' ? '224' : '96'" fixed="right">
          <template #default="{ row }">
            <div v-if="tab === 'unassigned'" class="row-assign">
              <el-select v-model="row.pick" placeholder="选客服" size="small" clearable style="width: 118px">
                <el-option v-for="a in agentOptions" :key="a.id"
                           :label="`${a.name}·${a.active || 0}`" :value="a.id" />
              </el-select>
              <el-button size="small" type="primary" :disabled="!row.pick" @click="assignOne(row)">指派</el-button>
            </div>
            <el-button v-else size="small" type="primary" link @click="openDetail(row)">去处理</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-box">
            <div class="empty-title">{{ emptyTitle }}</div>
            <div class="empty-desc">{{ emptyDesc }}</div>
          </div>
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Tickets, Warning, Clock, Timer, Finished, CircleCheck } from '@element-plus/icons-vue'
import api from '../api'

const router = useRouter()

const tableRef = ref()
const stats = ref({})
const agents = ref([])
const tickets = ref([])
const loadingOverview = ref(false)
const loadingTickets = ref(false)
const assigning = ref(false)
const tab = ref('unassigned')
const selected = ref([])
const bulkAssignee = ref('')

/** 可指派的客服下拉数据（带在办数量，方便均衡负载） */
const agentOptions = computed(() => agents.value)

/** 顶部指标卡 */
const statCards = computed(() => {
  const s = stats.value || {}
  return [
    { label: '待分配工单', value: s.unassigned ?? 0, icon: Tickets, color: '#f59e0b', bg: '#fef3c7' },
    { label: '已超时', value: s.overdue ?? 0, icon: Warning, color: '#ef4444', bg: '#fee2e2' },
    { label: `即将超时(${s.dueSoonHours ?? 4}h)`, value: s.dueSoon ?? 0, icon: Clock, color: '#f97316', bg: '#ffedd5' },
    { label: '待审申请', value: s.pendingReview ?? 0, icon: Finished, color: '#6366f1', bg: '#e0e7ff' },
    { label: '今日新增', value: s.todayCreated ?? 0, icon: Timer, color: '#0ea5e9', bg: '#e0f2fe' },
    { label: '今日解决', value: s.todayResolved ?? 0, icon: CircleCheck, color: '#22c55e', bg: '#dcfce7' }
  ]
})

const emptyTitle = computed(() => ({
  unassigned: '没有待分配的工单',
  overdue: '没有超时的工单',
  dueSoon: '没有即将超时的工单'
}[tab.value] || '暂无数据'))

const emptyDesc = computed(() => ({
  unassigned: '客户新建的工单会出现在这里，选客服即可直接派单',
  overdue: '所有在办工单都在 SLA 时限内，保持住',
  dueSoon: '临近解决时限的工单会提前在这里预警'
}[tab.value] || ''))

/** 拉取概览与工单列表 */
async function loadAll() {
  await Promise.all([loadOverview(), loadTickets()])
}

/** 概览：指标 + 客服负载 */
async function loadOverview() {
  loadingOverview.value = true
  try {
    const d = await api.get('/team/overview')
    stats.value = d.stats || {}
    agents.value = d.agents || []
  } finally {
    loadingOverview.value = false
  }
}

/** 当前 tab 的工单列表 */
async function loadTickets() {
  loadingTickets.value = true
  selected.value = []
  try {
    const rows = await api.get('/team/tickets', { params: { type: tab.value } })
    tickets.value = (rows || []).map(r => ({ ...r, pick: '' }))
  } finally {
    loadingTickets.value = false
  }
}

/** 勾选变化 */
function onSelectionChange(rows) {
  selected.value = rows
}

/** 清空勾选 */
function clearSelection() {
  tableRef.value?.clearSelection()
  selected.value = []
}

/** 行内指派单个工单 */
async function assignOne(row) {
  if (!row.pick) return
  await doAssign([row.id], row.pick, `工单 ${row.ticket_no} 已指派`)
}

/** 批量指派勾选的工单 */
async function doBulkAssign() {
  if (!selected.value.length || !bulkAssignee.value) return
  const ids = selected.value.map(r => r.id)
  await doAssign(ids, bulkAssignee.value, `已指派 ${ids.length} 张工单`)
  bulkAssignee.value = ''
}

/** 调用指派接口并刷新数据 */
async function doAssign(ids, assigneeId, tip) {
  assigning.value = true
  try {
    const d = await api.post('/team/assign', { ticketIds: ids, assigneeId })
    if (d && d.failed) {
      ElMessage.warning(`已指派 ${d.assigned} 单，${d.failed} 单失败`)
    } else {
      ElMessage.success(tip)
    }
    await loadAll()
  } catch (e) { /* 拦截器已提示 */ } finally {
    assigning.value = false
  }
}

/** 进入工单详情处理 */
function openDetail(row) {
  router.push(`/tickets/${row.id}`)
}

/** 优先级中文映射 */
function priorityLabel(p) {
  return { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p
}

/** 优先级标签颜色 */
function priorityType(p) {
  return { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }[p] || 'info'
}

/** 剩余分钟数转成可读文本，负数表示已超时 */
function remainText(minutes) {
  if (minutes === null || minutes === undefined) return '不限时'
  const m = Number(minutes)
  const abs = Math.abs(m)
  const text = abs >= 60 ? `${Math.floor(abs / 60)} 小时${abs % 60 ? ` ${abs % 60} 分` : ''}` : `${abs} 分钟`
  return m < 0 ? `已超时 ${text}` : `剩余 ${text}`
}

/** 时限文本配色：超时红、临期橙、正常灰 */
function remainClass(minutes) {
  if (minutes === null || minutes === undefined) return 'muted'
  const m = Number(minutes)
  if (m < 0) return 'over'
  if (m <= 240) return 'soon'
  return 'muted'
}

/** 头像文字 */
function initial(name) {
  const s = String(name || '?').trim()
  return s ? s.charAt(0) : '?'
}

/** 时间格式化到分钟 */
function fmtTime(t) {
  return t ? String(t).replace('T', ' ').substring(0, 16) : '—'
}

onMounted(loadAll)
</script>

<style scoped>
.team { display: flex; flex-direction: column; gap: 16px; max-width: 1400px; margin: 0 auto; width: 100%; }

.stat-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.stat-card {
  display: flex; align-items: center; gap: 12px;
  background: var(--card-bg); border: 1px solid var(--border);
  border-radius: 12px; padding: 14px 16px;
}
.stat-icon { width: 38px; height: 38px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 19px; }
.stat-value { font-size: 21px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 12px; color: var(--text-sub); margin-top: 2px; }

.panel-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
.panel-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.panel-title { font-size: 14px; font-weight: 600; color: var(--text-main); }
.panel-sub { font-size: 12px; color: var(--text-muted); flex: 1; }

.agent-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(232px, 1fr)); gap: 12px; }
.agent-card { border: 1px solid var(--border); border-radius: 10px; padding: 12px; background: #fafbfc; }
.agent-card.warn { border-color: #fecaca; background: #fef7f7; }
.agent-top { display: flex; align-items: center; gap: 10px; }
.agent-avatar {
  width: 32px; height: 32px; border-radius: 50%; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600;
}
.agent-name-box { flex: 1; min-width: 0; }
.agent-name { font-size: 13px; font-weight: 600; color: var(--text-main); }
.agent-dept { font-size: 11.5px; color: var(--text-muted); }
.agent-flag { font-size: 11px; color: #b91c1c; background: #fee2e2; border-radius: 8px; padding: 1px 7px; }
.agent-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin-top: 12px; text-align: center; }
.metric-value { font-size: 15px; font-weight: 600; color: var(--text-main); }
.metric-value.success { color: var(--success); }
.metric-label { font-size: 11px; color: var(--text-muted); }

.bulk-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; margin-bottom: 12px;
  background: #f8fafc; border: 1px dashed var(--border); border-radius: 10px;
}
.bulk-count { font-size: 12.5px; color: var(--text-sub); }
.bulk-tip { font-size: 12px; color: var(--text-muted); }

.row-assign { display: flex; align-items: center; gap: 6px; }
.remain { font-size: 12.5px; }
.remain.over { color: var(--danger); font-weight: 600; }
.remain.soon { color: var(--warning); font-weight: 600; }
.remain.muted { color: var(--text-muted); }

.empty-box { padding: 26px 0; text-align: center; }
.empty-title { font-size: 13px; color: var(--text-sub); }
.empty-desc { font-size: 12px; color: var(--text-muted); margin-top: 6px; }

@media (max-width: 1280px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
