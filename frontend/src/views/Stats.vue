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
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
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
  await nextTick()
  renderCharts()
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
</style>
