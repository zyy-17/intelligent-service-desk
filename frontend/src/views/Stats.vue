<template>
  <div class="stats-page">
    <!-- KPI -->
    <div class="kpi-row">
      <div class="kpi-card" v-for="k in kpi" :key="k.label">
        <div class="kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
        <div class="kpi-label">{{ k.label }}</div>
      </div>
    </div>

    <div class="grid-2">
      <div class="panel-card chart-panel">
        <div class="panel-head"><span class="panel-title">工单状态分布</span></div>
        <div ref="statusRef" class="chart"></div>
      </div>
      <div class="panel-card chart-panel">
        <div class="panel-head"><span class="panel-title">客服工作量</span></div>
        <div ref="agentRef" class="chart"></div>
      </div>
    </div>

    <div class="grid-2">
      <div class="panel-card chart-panel">
        <div class="panel-head"><span class="panel-title">优先级分布</span></div>
        <div ref="priorityRef" class="chart"></div>
      </div>
      <div class="panel-card chart-panel">
        <div class="panel-head"><span class="panel-title">客户满意度</span></div>
        <div ref="satRef" class="chart"></div>
      </div>
    </div>

    <!-- 客户评价：概览指标 + 星级分布 + 客服排行 + 评价明细 -->
    <div class="panel-card eval-panel">
      <div class="panel-head">
        <span class="panel-title">客户评价</span>
        <span class="panel-sub">工单解决后由客户打分，满分 5 星</span>
      </div>

      <div class="eval-kpi">
        <div class="eval-kpi-item" v-for="k in evalKpi" :key="k.label">
          <div class="eval-kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
          <div class="eval-kpi-label">{{ k.label }}</div>
        </div>
      </div>

      <div class="eval-body">
        <div class="eval-dist">
          <div class="dist-title">星级分布</div>
          <div class="dist-row" v-for="d in evalDist" :key="d.rating">
            <span class="dist-star">{{ d.rating }} 星</span>
            <div class="dist-bar">
              <i :style="{ width: distWidth(d.value), background: starColor(d.rating) }"></i>
            </div>
            <span class="dist-num">{{ d.value }}</span>
          </div>
        </div>
        <div class="eval-agents">
          <div class="dist-title">客服评价排行</div>
          <el-table :data="evalAgents" size="small" stripe :max-height="212">
            <el-table-column prop="name" label="客服" min-width="96" show-overflow-tooltip />
            <el-table-column prop="rated" label="评价数" width="80" align="center" sortable />
            <el-table-column prop="avg_rating" label="平均分" width="86" align="center" sortable>
              <template #default="{ row }"><span class="star-text">{{ row.avg_rating }}</span></template>
            </el-table-column>
            <el-table-column prop="good_rate" label="好评率" width="86" align="center" sortable>
              <template #default="{ row }">{{ row.good_rate }}%</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!evalAgents.length" description="暂无评价数据" :image-size="60" />
        </div>
      </div>

      <div class="toolbar">
        <div class="toolbar-left">
          <el-date-picker v-model="evalQuery.range" type="daterange" unlink-panels
                          value-format="YYYY-MM-DD" range-separator="至"
                          start-placeholder="开始日期" end-placeholder="结束日期" style="width: 238px" />
          <el-select v-model="evalQuery.rating" placeholder="星级：全部" clearable style="width: 118px">
            <el-option v-for="s in [5, 4, 3, 2, 1]" :key="s" :label="s + ' 星'" :value="s" />
          </el-select>
          <el-select v-model="evalQuery.agentId" placeholder="处理客服" clearable style="width: 132px">
            <el-option v-for="a in evalAgentOptions" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
          <el-select v-model="evalQuery.followStatus" placeholder="跟进状态" clearable style="width: 128px">
            <el-option label="待跟进" value="PENDING" />
            <el-option label="跟进中" value="DOING" />
            <el-option label="已闭环" value="DONE" />
          </el-select>
          <el-input v-model="evalQuery.keyword" placeholder="工单号 / 主题 / 客户"
                    :prefix-icon="Search" clearable style="width: 190px" @keyup.enter="searchEvals" />
          <el-button type="primary" :icon="Search" @click="searchEvals">查询</el-button>
          <el-button :icon="Refresh" @click="resetEvalFilter">重置</el-button>
        </div>
      </div>

      <el-table :data="evalList" v-loading="loadingEvals" size="small" stripe>
        <el-table-column prop="ticket_no" label="工单号" width="146" />
        <el-table-column prop="title" label="工单主题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="customer_name" label="客户" width="96" show-overflow-tooltip />
        <el-table-column label="处理客服" width="98" show-overflow-tooltip>
          <template #default="{ row }">{{ row.agent_name || '—' }}</template>
        </el-table-column>
        <el-table-column label="评分" width="132">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.comment }">{{ row.comment || '未填写评价内容' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="跟进" width="132">
          <template #default="{ row }">
            <template v-if="row.follow_status">
              <el-tag :type="followTagType(row.follow_status)" size="small" effect="light">
                {{ followLabel(row.follow_status) }}
              </el-tag>
              <el-button link type="primary" size="small" class="follow-btn" @click="openFollow(row)">
                {{ row.follow_status === 'DONE' ? '改' : '跟进' }}
              </el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" width="146" align="center">
          <template #default="{ row }">{{ fmtDate(row.rated_at) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingEvals && !evalList.length" description="暂无评价记录" :image-size="70" />

      <div class="pager">
        <span class="pager-total">共 {{ evalTotal }} 条</span>
        <el-pagination background layout="prev, pager, next, sizes, jumper"
                       :total="evalTotal" :page-size="evalPageSize" :current-page="evalPage"
                       :page-sizes="[10, 20, 50]"
                       @current-change="loadEvals"
                       @size-change="s => { evalPageSize = s; loadEvals(1) }" />
      </div>
    </div>

    <!-- 低分评价跟进弹窗 -->
    <el-dialog v-model="followVisible" title="低分评价跟进" width="486px">
      <div v-if="followRow" class="follow-head">
        <div class="follow-line">
          <span class="follow-k">工单</span>
          <span class="follow-v">{{ followRow.ticket_no }} · {{ followRow.title }}</span>
        </div>
        <div class="follow-line">
          <span class="follow-k">客户评分</span>
          <el-rate :model-value="followRow.rating" disabled size="small" />
        </div>
        <div class="follow-line">
          <span class="follow-k">评价内容</span>
          <span class="follow-v">{{ followRow.comment || '客户未填写评价内容' }}</span>
        </div>
        <div v-if="followRow.follow_by_name" class="follow-line">
          <span class="follow-k">上次跟进</span>
          <span class="follow-v">{{ followRow.follow_by_name }} · {{ fmtDate(followRow.follow_at) }}</span>
        </div>
      </div>
      <el-form label-width="76px" style="margin-top: 6px">
        <el-form-item label="跟进状态">
          <el-radio-group v-model="followForm.status">
            <el-radio-button value="PENDING">待跟进</el-radio-button>
            <el-radio-button value="DOING">跟进中</el-radio-button>
            <el-radio-button value="DONE">已闭环</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="跟进备注">
          <el-input v-model="followForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="例如：已电话回访，客户接受补偿方案，问题已闭环" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followVisible = false">取消</el-button>
        <el-button type="primary" :loading="followSaving" @click="submitFollow">保存</el-button>
      </template>
    </el-dialog>

    <!-- 团队绩效看板 -->
    <div class="panel-card team-panel">
      <div class="panel-head"><span class="panel-title">团队绩效</span></div>
      <el-table :data="team" size="small" stripe>
        <el-table-column prop="name" label="客服姓名" min-width="120" />
        <el-table-column prop="total" label="工单总数" width="100" align="center" sortable />
        <el-table-column prop="resolved" label="已解决" width="90" align="center" sortable />
        <el-table-column prop="overdue" label="超时" width="80" align="center" sortable>
          <template #default="{ row }">
            <span :style="{ color: row.overdue > 0 ? '#ef4444' : '#22c55e', fontWeight: 600 }">{{ row.overdue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="satisfaction" label="满意度" width="90" align="center" sortable>
          <template #default="{ row }">
            <span v-if="row.satisfaction != null">{{ row.satisfaction }} / 5</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="avg_response_min" label="平均响应(分)" width="110" align="center" sortable />
        <el-table-column prop="avg_resolve_hours" label="平均解决(时)" width="110" align="center" sortable />
      </el-table>
    </div>
    <!-- 知识库表现 -->
    <div class="panel-card kb-panel">
      <div class="panel-head">
        <span class="panel-title">知识库表现</span>
        <span class="panel-sub">收藏量与浏览量排行榜（Top 10）</span>
      </div>
      <div class="kb-kpi">
        <div class="kb-kpi-item" v-for="k in kbKpi" :key="k.label">
          <div class="kb-kpi-value" :style="{ color: k.color }">{{ k.value }}</div>
          <div class="kb-kpi-label">{{ k.label }}</div>
        </div>
      </div>
      <el-table :data="kbTop" size="small" stripe>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="title" label="文章标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="130">
          <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览量" width="110" align="center" sortable />
        <el-table-column prop="favoriteCount" label="收藏量" width="110" align="center" sortable>
          <template #default="{ row }">
            <span :style="{ color: row.favoriteCount > 0 ? '#f59e0b' : '#94a3b8', fontWeight: 600 }">
              {{ row.favoriteCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="publishedAt" label="发布时间" width="160" align="center" sortable>
          <template #default="{ row }">{{ fmtDate(row.publishedAt || row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!kbTop.length" description="暂无知识文章" :image-size="70" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import api from '../api'

const overview = ref({})
const statusRef = ref(null)
const agentRef = ref(null)
const priorityRef = ref(null)
const satRef = ref(null)
const charts = []

const statusName = { PENDING: '待分配', ASSIGNED: '待受理', PROCESSING: '处理中', WAITING: '待确认', RESOLVED: '已解决', CLOSED: '已关闭' }
const priorityName = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }

const kpi = ref([])
const team = ref([])
const kbKpi = ref([])
const kbTop = ref([])

// 客户评价
const evalStats = ref({})
const evalList = ref([])
const evalTotal = ref(0)
const evalPage = ref(1)
const evalPageSize = ref(10)
const loadingEvals = ref(false)
const evalAgentOptions = ref([])
const evalQuery = reactive({ range: null, rating: '', agentId: '', keyword: '', followStatus: '' })

// 低分评价跟进
const followVisible = ref(false)
const followSaving = ref(false)
const followRow = ref(null)
const followForm = reactive({ status: 'DOING', note: '' })

/** 跟进状态中文文案 */
function followLabel(s) {
  return { PENDING: '待跟进', DOING: '跟进中', DONE: '已闭环' }[s] || s
}

/** 跟进状态标签配色 */
function followTagType(s) {
  return { PENDING: 'danger', DOING: 'warning', DONE: 'success' }[s] || 'info'
}

/** 打开跟进弹窗，带出上次状态与备注 */
function openFollow(row) {
  followRow.value = row
  followForm.status = row.follow_status || 'DOING'
  followForm.note = row.follow_note || ''
  followVisible.value = true
}

/** 保存跟进状态与备注 */
async function submitFollow() {
  followSaving.value = true
  try {
    await api.put(`/evaluations/${followRow.value.ticket_id}/follow`, {
      status: followForm.status,
      note: followForm.note.trim()
    })
    ElMessage.success('跟进状态已更新')
    followVisible.value = false
    await Promise.all([loadEvalStats(), loadEvalList(evalPage.value)])
  } catch (e) { /* 拦截器已提示 */ } finally {
    followSaving.value = false
  }
}

/** 评价概览指标卡 */
const evalKpi = computed(() => {
  const d = evalStats.value || {}
  return [
    { label: '评价总数', value: d.total ?? 0, color: '#6366f1' },
    { label: '平均评分', value: (d.avgRating ?? 0) + ' 分', color: '#f59e0b' },
    { label: '好评率(4星+)', value: (d.goodRate ?? 0) + '%', color: '#22c55e' },
    { label: '评价覆盖率', value: (d.coverRate ?? 0) + '%', color: '#06b6d4' },
    { label: '低分待跟进', value: d.pendingFollow ?? 0, color: '#ef4444' }
  ]
})

/** 星级分布（后端已按 5→1 排好，缺档补 0） */
const evalDist = computed(() => evalStats.value.distribution || [])
/** 客服评价排行 */
const evalAgents = computed(() => evalStats.value.agents || [])

/** 星级进度条宽度：以最高档为基准 */
function distWidth(value) {
  const max = Math.max(...evalDist.value.map(d => Number(d.value) || 0), 1)
  return Math.round((Number(value) || 0) * 100 / max) + '%'
}

/** 星级对应颜色：5 星金、4 星绿、3 星蓝、2 星橙、1 星红 */
function starColor(star) {
  return { 5: '#f59e0b', 4: '#22c55e', 3: '#3b82f6', 2: '#fb923c', 1: '#ef4444' }[star] || '#94a3b8'
}

/* 时间格式化：只保留到分钟 */
function fmtDate(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '—' }

async function load() {
  overview.value = await api.get('/stats/overview')
  const d = overview.value
  kpi.value = [
    { label: '工单总数', value: d.totalTickets, color: '#6366f1' },
    { label: '待处理', value: d.pendingTickets, color: '#f59e0b' },
    { label: '处理中', value: d.processingTickets, color: '#3b82f6' },
    { label: '已解决', value: d.resolvedTickets, color: '#22c55e' },
    { label: '超时工单', value: d.overdueTickets, color: '#ef4444' },
    { label: '客户数', value: d.customerCount, color: '#06b6d4' }
  ]
  team.value = await api.get('/stats/team-performance')
  await loadKnowledge()
  await loadEvaluations()
  await nextTick()
  renderCharts()
}

/** 加载客户评价：概览统计 + 明细列表 + 客服下拉 */
async function loadEvaluations() {
  await Promise.all([loadEvalStats(), loadEvalList(1)])
  if (!evalAgentOptions.value.length) {
    evalAgentOptions.value = await api.get('/evaluations/agents')
  }
}

/** 评价概览统计 */
async function loadEvalStats() {
  try { evalStats.value = await api.get('/evaluations/stats') } catch (e) { /* 统计失败不影响明细 */ }
}

/** 评价明细分页 */
async function loadEvalList(page = 1) {
  loadingEvals.value = true
  evalPage.value = page
  try {
    const [start, end] = evalQuery.range || []
    const data = await api.get('/evaluations', {
      params: {
        page,
        pageSize: evalPageSize.value,
        rating: evalQuery.rating || undefined,
        agentId: evalQuery.agentId || undefined,
        keyword: evalQuery.keyword.trim() || undefined,
        start: start || undefined,
        end: end || undefined,
        followStatus: evalQuery.followStatus || undefined
      }
    })
    evalList.value = data.list || []
    evalTotal.value = data.total || 0
  } finally {
    loadingEvals.value = false
  }
}

/** 点击查询 */
function searchEvals() { loadEvalList(1); loadEvalStats() }

/** 重置评价筛选 */
function resetEvalFilter() {
  evalQuery.range = null
  evalQuery.rating = ''
  evalQuery.agentId = ''
  evalQuery.keyword = ''
  evalQuery.followStatus = ''
  searchEvals()
}

/* 加载知识库表现数据（浏览/收藏汇总 + Top 文章） */
async function loadKnowledge() {
  const kb = await api.get('/stats/knowledge', { params: { top: 10 } })
  kbKpi.value = [
    { label: '知识文章', value: kb.total, color: '#6366f1' },
    { label: '总浏览量', value: kb.totalViews, color: '#3b82f6' },
    { label: '总收藏量', value: kb.totalFavorites, color: '#f59e0b' },
    { label: '篇均收藏', value: kb.avgFavorites, color: '#22c55e' }
  ]
  kbTop.value = kb.top || []
}

function renderCharts() {
  const d = overview.value

  const statusData = (d.statusDistribution || []).map(s => ({ name: statusName[s.name] || s.name, value: s.value }))
  initPie(statusRef.value, statusData, '#6366f1')

  const agentData = (d.agentWorkload || [])
  initBar(agentRef.value, agentData.map(a => a.name), agentData.map(a => a.value), '工单数')

  const priorityData = (d.priorityDistribution || []).map(s => ({ name: priorityName[s.name] || s.name, value: s.value }))
  initPie(priorityRef.value, priorityData, '#f59e0b')

  const satData = (d.satisfactionDistribution || []).map(s => ({ name: s.rating + ' 星', value: s.value }))
  initBar(satRef.value, satData.map(s => s.name), satData.map(s => s.value), '评价数')
}

function initPie(el, data, color) {
  if (!el) return
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{ type: 'pie', radius: ['40%', '68%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false }, data }]
  })
  charts.push(chart)
}

function initBar(el, names, values, unit) {
  if (!el) return
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: values, barMaxWidth: 40, itemStyle: { color: '#6366f1', borderRadius: [4, 4, 0, 0] } }]
  })
  charts.push(chart)
}

function handleResize() { charts.forEach(c => c.resize()) }

onMounted(() => {
  load()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(c => c.dispose())
})
</script>

<style scoped>
.stats-page { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; margin: 0 auto; width: 100%; }
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; }
.kpi-card { background: #fff; border-radius: 12px; padding: 18px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.kpi-value { font-size: 26px; font-weight: 700; }
.kpi-label { color: var(--text-sub); font-size: 13px; margin-top: 4px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.chart-panel { padding: 16px; }
.team-panel { padding: 16px; }
.muted { color: #c0c4cc; }
.panel-head { margin-bottom: 12px; display: flex; align-items: baseline; gap: 10px; }
.panel-title { font-size: 15px; font-weight: 600; }
.panel-sub { font-size: 12px; color: var(--text-muted); }
.chart { height: 300px; }
/* 知识库表现：四宫格 KPI + 排行榜表格 */
.kb-panel { padding: 16px; }
.kb-kpi { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 14px; }
.kb-kpi-item {
  background: #f8fafc; border: 1px solid #eef2f7; border-radius: 10px;
  padding: 14px; text-align: center;
}
.kb-kpi-value { font-size: 22px; font-weight: 700; line-height: 1.2; }
.kb-kpi-label { font-size: 12px; color: var(--text-sub); margin-top: 4px; }

/* 客户评价：四宫格指标 + 星级分布 + 客服排行 + 明细表 */
.eval-panel { padding: 16px; }
.eval-kpi { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 14px; }
.eval-kpi-item {
  background: #f8fafc; border: 1px solid #eef2f7; border-radius: 10px;
  padding: 14px; text-align: center;
}
.eval-kpi-value { font-size: 22px; font-weight: 700; line-height: 1.2; }
.eval-kpi-label { font-size: 12px; color: var(--text-sub); margin-top: 4px; }
.eval-body { display: grid; grid-template-columns: 1fr 1.2fr; gap: 22px; margin-bottom: 16px; }
.dist-title { font-size: 13px; font-weight: 600; color: var(--text-sub); margin-bottom: 10px; }
.dist-row { display: flex; align-items: center; gap: 10px; margin-bottom: 9px; }
.dist-star { width: 38px; font-size: 12px; color: var(--text-sub); flex: none; }
.dist-bar { flex: 1; height: 8px; background: #eef2f7; border-radius: 4px; overflow: hidden; }
.dist-bar i { display: block; height: 100%; border-radius: 4px; transition: width .3s; }
.dist-num { width: 36px; text-align: right; font-size: 12px; color: var(--text-sub); flex: none; }
.star-text { color: #f59e0b; font-weight: 600; }

/* 低分评价跟进 */
.follow-head { background: #f8fafc; border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; }
.follow-line { display: flex; align-items: flex-start; gap: 10px; font-size: 12.5px; line-height: 1.7; }
.follow-k { width: 62px; flex: none; color: var(--text-muted); }
.follow-v { color: var(--text-main); word-break: break-all; }
.follow-btn { margin-left: 6px; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.toolbar-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pager { display: flex; align-items: center; justify-content: space-between; padding: 12px 0 2px; }
.pager-total { font-size: 13px; color: var(--text-sub); }
</style>
