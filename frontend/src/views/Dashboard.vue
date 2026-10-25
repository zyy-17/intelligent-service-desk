<template>
  <div class="dashboard">
    <!-- 顶部欢迎语 -->
    <div class="welcome">
      <div>
        <h2>您好，{{ auth.displayName }}，欢迎回来！</h2>
        <p>今天是 {{ today }}，以下是今日服务概况。</p>
      </div>
    </div>

    <!-- KPI 卡片 -->
    <div class="kpi-row">
      <div class="kpi-card" v-for="k in kpiCards" :key="k.label">
        <div class="kpi-icon" :style="{ background: k.bg, color: k.color }">
          <el-icon :size="22"><component :is="k.icon" /></el-icon>
        </div>
        <div class="kpi-info">
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
        </div>
        <div class="kpi-trend" v-if="k.trend" :class="k.trendType">
          {{ k.trend }}
        </div>
      </div>
    </div>

    <div class="grid-2">
      <!-- 工单趋势 -->
      <div class="panel-card chart-panel">
        <div class="panel-head">
          <span class="panel-title">工单趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">近7天</el-radio-button>
            <el-radio-button :value="30">近30天</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="trendRef" class="chart"></div>
      </div>

      <!-- 工单分类 -->
      <div class="panel-card chart-panel">
        <div class="panel-head">
          <span class="panel-title">工单分类占比</span>
        </div>
        <div ref="categoryRef" class="chart"></div>
      </div>
    </div>

    <div class="grid-3">
      <!-- 最新工单 -->
      <div class="panel-card list-panel">
        <div class="panel-head">
          <span class="panel-title">最新工单</span>
          <router-link to="/tickets" class="more">查看全部 →</router-link>
        </div>
        <div class="ticket-list">
          <div v-for="t in recentTickets" :key="t.id" class="ticket-item">
            <div class="ticket-main">
              <div class="ticket-no">{{ t.ticket_no }}</div>
              <div class="ticket-title" :title="t.title">{{ t.title }}</div>
            </div>
            <div class="ticket-meta">
              <el-tag :type="priorityType(t.priority)" size="small" effect="light">{{ priorityLabel(t.priority) }}</el-tag>
              <span class="ticket-time">{{ shortTime(t.created_at) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 热门知识 -->
      <div class="panel-card list-panel">
        <div class="panel-head">
          <span class="panel-title">热门知识</span>
          <router-link to="/knowledge" class="more">查看全部 →</router-link>
        </div>
        <div class="kb-list">
          <div v-for="(a, i) in hotArticles" :key="a.id" class="kb-item">
            <span class="kb-rank" :class="'r' + (i + 1)">{{ i + 1 }}</span>
            <div class="kb-main">
              <div class="kb-title">{{ a.title }}</div>
              <div class="kb-meta">{{ a.categoryName }} · {{ a.viewCount }} 次浏览</div>
            </div>
          </div>
        </div>
      </div>

      <!-- AI 助手入口 -->
      <div class="panel-card ai-panel">
        <div class="panel-head">
          <span class="panel-title">AI 智能助手</span>
        </div>
        <div class="ai-body">
          <div class="ai-bot">
            <img class="bot-avatar" :src="botAvatar" alt="AI" />
            <div class="bot-bubble">您好，我是智能客服助手，可以帮您解答常见问题。</div>
          </div>
          <el-input v-model="aiQuestion" placeholder="输入您的问题..." size="large" @keyup.enter="askAi">
            <template #append>
              <el-button :icon="Promotion" @click="askAi" />
            </template>
          </el-input>
          <div v-if="aiAnswer" class="ai-answer">{{ aiAnswer }}</div>
        </div>
      </div>
    </div>

    <!-- 服务概况 -->
    <div class="panel-card overview-panel">
      <div class="panel-head">
        <span class="panel-title">服务概况</span>
      </div>
      <div class="overview-grid">
        <div class="ov-item">
          <div class="ov-value">{{ overview.processingTickets }}</div>
          <div class="ov-label">处理中工单</div>
        </div>
        <div class="ov-item">
          <div class="ov-value warn">{{ overview.overdueTickets }}</div>
          <div class="ov-label">超时工单</div>
        </div>
        <div class="ov-item">
          <div class="ov-value">{{ overview.avgResponseMinutes }} 分钟</div>
          <div class="ov-label">平均响应时长</div>
        </div>
        <div class="ov-item">
          <div class="ov-value">{{ overview.avgResolveHours }} 小时</div>
          <div class="ov-label">平均解决时长</div>
        </div>
        <div class="ov-item">
          <div class="ov-value">{{ overview.satisfaction }} / 5</div>
          <div class="ov-label">客户满意度</div>
        </div>
        <div class="ov-item">
          <div class="ov-value">{{ overview.agentCount }}</div>
          <div class="ov-label">在线客服</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useAuthStore } from '../store/auth'
import api from '../api'
import * as echarts from 'echarts'
import { Promotion } from '@element-plus/icons-vue'
import botAvatar from '../assets/ai-bot-avatar.png'

const auth = useAuthStore()
const overview = ref({})
const recentTickets = ref([])
const hotArticles = ref([])
const trendDays = ref(7)
const trendRef = ref(null)
const categoryRef = ref(null)
let trendChart = null
let categoryChart = null

const aiQuestion = ref('')
const aiAnswer = ref('')

const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const kpiCards = computed(() => [
  { label: '工单总数', value: overview.value.totalTickets || 0, icon: 'Tickets', bg: '#eef2ff', color: '#6366f1' },
  { label: '待处理工单', value: overview.value.pendingTickets || 0, icon: 'Clock', bg: '#fff7ed', color: '#f59e0b' },
  { label: '处理中工单', value: overview.value.processingTickets || 0, icon: 'Loading', bg: '#eff6ff', color: '#3b82f6' },
  { label: '已解决工单', value: overview.value.resolvedTickets || 0, icon: 'CircleCheck', bg: '#f0fdf4', color: '#22c55e' },
  { label: '今日新增', value: overview.value.todayNew || 0, icon: 'TrendCharts', bg: '#fdf2f8', color: '#ec4899' }
])

const priorityMap = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityTypeMap = { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }
const priorityLabel = p => priorityMap[p] || p
const priorityType = p => priorityTypeMap[p] || 'info'
const shortTime = t => (t ? t.replace('T', ' ').substring(5, 16) : '')

async function loadOverview() {
  overview.value = await api.get('/stats/overview')
  recentTickets.value = (await api.get('/tickets', { params: { page: 1, pageSize: 6 } })).list || []
  hotArticles.value = await api.get('/knowledge/hot', { params: { limit: 5 } })
  await nextTick()
  renderTrend()
  renderCategory()
}

async function loadTrend() {
  const trend = await api.get('/stats/trend', { params: { days: trendDays.value } })
  renderTrend(trend)
}

function renderTrend(data) {
  if (!trendRef.value) return
  const trend = data || overview.value.trend || []
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增工单', '已解决'], top: 0, right: 0 },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.map(t => t.date), boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      { name: '新增工单', type: 'line', smooth: true, data: trend.map(t => t.created),
        areaStyle: { opacity: 0.15 }, itemStyle: { color: '#6366f1' }, lineStyle: { color: '#6366f1' } },
      { name: '已解决', type: 'line', smooth: true, data: trend.map(t => t.resolved),
        itemStyle: { color: '#22c55e' }, lineStyle: { color: '#22c55e' } }
    ]
  })
}

function renderCategory() {
  if (!categoryRef.value) return
  const data = overview.value.categoryDistribution || []
  if (!categoryChart) categoryChart = echarts.init(categoryRef.value)
  categoryChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['38%', '50%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: data.map(d => ({ name: d.name, value: d.value, itemStyle: { color: d.color } }))
    }]
  })
}

async function askAi() {
  if (!aiQuestion.value.trim()) return
  const data = await api.post('/ai/chat', { question: aiQuestion.value })
  aiAnswer.value = data.answer
}

function handleResize() {
  trendChart?.resize()
  categoryChart?.resize()
}

onMounted(() => {
  loadOverview()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  categoryChart?.dispose()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.welcome h2 { margin: 0 0 4px; font-size: 20px; }
.welcome p { margin: 0; color: var(--text-sub); font-size: 14px; }

.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  position: relative;
}
.kpi-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kpi-value { font-size: 24px; font-weight: 700; }
.kpi-label { color: var(--text-sub); font-size: 13px; margin-top: 2px; }
.kpi-trend { position: absolute; top: 12px; right: 12px; font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.kpi-trend.up { color: #dc2626; background: #fef2f2; }
.kpi-trend.down { color: #16a34a; background: #f0fdf4; }

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.grid-3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
}
.chart-panel { padding: 16px; }
.chart { height: 280px; }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-title { font-size: 15px; font-weight: 600; }
.more { font-size: 13px; color: var(--primary); }

.list-panel { padding: 16px; }
.ticket-list { display: flex; flex-direction: column; }
.ticket-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f3f4f6;
}
.ticket-item:last-child { border-bottom: none; }
.ticket-no { font-size: 11px; color: var(--text-muted); }
.ticket-title {
  font-size: 13px;
  margin-top: 2px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ticket-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.ticket-time { font-size: 11px; color: var(--text-muted); }

.kb-list { display: flex; flex-direction: column; }
.kb-item { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid #f3f4f6; }
.kb-item:last-child { border-bottom: none; }
.kb-rank {
  width: 22px; height: 22px; border-radius: 6px; font-size: 12px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
  background: #f3f4f6; color: var(--text-sub);
}
.kb-rank.r1 { background: #fef2f2; color: #dc2626; }
.kb-rank.r2 { background: #fff7ed; color: #ea580c; }
.kb-rank.r3 { background: #fefce8; color: #ca8a04; }
.kb-title { font-size: 13px; }
.kb-meta { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

.ai-panel { padding: 16px; display: flex; flex-direction: column; }
.ai-body { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.ai-bot { display: flex; gap: 10px; align-items: flex-start; }
.bot-avatar {
  width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0;
}
.bot-bubble {
  background: #f3f4f6; border-radius: 4px 12px 12px 12px; padding: 10px 12px;
  font-size: 13px; color: var(--text-sub);
}
.ai-answer {
  background: #eef2ff; border-radius: 8px; padding: 10px 12px;
  font-size: 13px; color: var(--text-main); max-height: 140px; overflow-y: auto; white-space: pre-wrap;
}

.overview-panel { padding: 16px; }
.overview-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.ov-item { text-align: center; padding: 14px 0; }
.ov-value { font-size: 22px; font-weight: 700; }
.ov-value.warn { color: #dc2626; }
.ov-label { color: var(--text-sub); font-size: 12px; margin-top: 4px; }

@media (max-width: 1200px) {
  .kpi-row { grid-template-columns: repeat(3, 1fr); }
  .grid-3 { grid-template-columns: 1fr; }
  .overview-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
