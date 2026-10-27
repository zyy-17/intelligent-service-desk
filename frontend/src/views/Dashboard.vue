<template>
  <div class="dashboard">
    <!-- ================= 客户首页（Hero + 快捷入口 + 三栏） ================= -->
    <template v-if="isCustomer">
      <!-- Hero 横幅 -->
      <section class="hero">
        <svg class="hero-deco" viewBox="0 0 600 220" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 220 L110 96 L190 148 L300 62 L400 138 L470 92 L600 168 L600 220 Z" fill="rgba(96,165,250,0.20)" />
          <path d="M0 220 L80 148 L170 184 L280 118 L380 172 L465 132 L600 196 L600 220 Z" fill="rgba(59,130,246,0.14)" />
        </svg>

        <div class="hero-main">
          <h1 class="hero-title">高效解决问题<br />让服务更简单</h1>
          <p class="hero-sub">您好，{{ auth.displayName }}！有任何疑问，随时告诉我们</p>
          <div class="hero-search">
            <el-icon class="hs-icon"><Search /></el-icon>
            <input v-model="heroQuery" class="hs-input" placeholder="请输入问题关键字，如：登录失败、密码重置等"
                   @keyup.enter="askFromHero" />
            <button class="hs-btn" @click="askFromHero">搜索</button>
          </div>
        </div>

        <div class="hero-stats">
          <div class="hs-cell" v-for="s in heroStats" :key="s.label">
            <div class="hs-val">{{ s.val }}</div>
            <div class="hs-label">{{ s.label }}</div>
          </div>
        </div>
      </section>

      <!-- 四个快捷入口 -->
      <section class="quick-row">
        <div class="quick-card" v-for="q in quickCards" :key="q.title" @click="q.onClick">
          <div class="quick-icon" :style="{ background: q.bg, color: q.color }">
            <el-icon :size="24"><component :is="q.icon" /></el-icon>
          </div>
          <div class="quick-body">
            <div class="quick-title">{{ q.title }}</div>
            <div class="quick-desc">{{ q.desc }}</div>
          </div>
        </div>
      </section>

      <!-- 三栏：我的工单 / 常见问题 / AI 助手 -->
      <section class="home-cols">
        <div class="panel-card col-block">
          <div class="panel-head">
            <span class="panel-title">我的工单</span>
            <router-link to="/tickets" class="more">查看全部 ›</router-link>
          </div>
          <div class="tk-list">
            <div class="tk-row" v-for="t in myRecent" :key="t.id" @click="goTicketDetail(t)">
              <span class="tk-no">#{{ t.ticket_no }}</span>
              <span class="tk-title" :title="t.title">{{ t.title }}</span>
              <el-tag :type="statusType(t.status)" size="small" effect="light" class="tk-tag">
                {{ statusLabel(t.status) }}
              </el-tag>
              <span class="tk-time">{{ shortTime(t.created_at) }}</span>
            </div>
            <div v-if="myRecent.length === 0" class="block-empty">还没有工单，点上方「提交工单」试试</div>
          </div>
        </div>

        <div class="panel-card col-block">
          <div class="panel-head">
            <span class="panel-title">常见问题</span>
            <router-link to="/knowledge" class="more">查看更多 ›</router-link>
          </div>
          <div class="faq-list">
            <div class="faq-row" v-for="a in hotArticles" :key="a.id" @click="askQuestion(a.title)">
              <span class="faq-dot"></span>
              <span class="faq-text" :title="a.title">{{ a.title }}</span>
              <el-icon class="faq-arrow"><ArrowRight /></el-icon>
            </div>
            <div v-if="hotArticles.length === 0" class="block-empty">暂无常见问题</div>
          </div>
        </div>

        <div class="ai-card">
          <div class="ai-card-body">
            <div class="ai-badge">AI 智能助手</div>
            <div class="ai-slogan">为您解答常见问题<br />24 小时在线</div>
            <button class="ai-go" @click="router.push('/chat')">立即提问</button>
          </div>
          <img class="ai-figure" :src="botAvatar" alt="AI 助手" />
        </div>
      </section>

      <!-- 提交工单弹窗 -->
      <el-dialog v-model="createVisible" title="提交工单" width="540px" :close-on-click-modal="false">
        <el-form :model="createForm" label-width="80px">
          <el-form-item label="主题" required>
            <el-input v-model="createForm.title" placeholder="一句话概括您遇到的问题" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="createForm.categoryId" placeholder="请选择问题分类" style="width: 100%" clearable>
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
          <el-form-item label="问题描述" required>
            <el-input v-model="createForm.description" type="textarea" :rows="5"
                      placeholder="请详细描述您遇到的问题、操作步骤和报错信息，便于我们快速定位" maxlength="1000" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="createVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="doCreate">提交工单</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ================= 管理端 / 客服工作台（原有布局） ================= -->
    <template v-else>
      <!-- 客服工作台横幅 -->
      <section class="agent-hero" v-if="isAgent">
        <div class="ah-main">
          <h2 class="ah-title">你好，{{ auth.displayName }}！<span class="ah-wave">👋</span></h2>
          <p class="ah-sub">用专业的服务，解决每一个问题，让客户更满意。</p>
          <p class="ah-date">今天是 {{ today }}，以下是今日服务概况</p>
        </div>
        <img class="ah-illus" :src="agentHero" alt="" aria-hidden="true" />
      </section>

      <!-- 管理端欢迎语 -->
      <div class="welcome" v-if="!isAgent">
        <div>
          <h2>您好，{{ auth.displayName }}，欢迎回来！</h2>
          <p>今天是 {{ today }}，以下是今日服务概况。</p>
        </div>
      </div>

      <!-- KPI 卡片 -->
      <div class="kpi-row" :class="{ 'kpi-cols-4': isAgent }">
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

      <div class="grid-2" v-if="!isPersonal">
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

      <!-- 个人视角：我的工单列表 -->
      <div class="panel-card list-panel" v-if="isPersonal">
        <div class="panel-head">
          <span class="panel-title">{{ isCustomer ? '我的工单' : '我的待办' }}</span>
          <router-link to="/tickets" class="more">查看全部 →</router-link>
        </div>
        <div class="ticket-list">
          <div v-for="t in personalList" :key="t.id" class="ticket-item">
            <div class="ticket-main">
              <div class="ticket-no">{{ t.ticket_no }}</div>
              <div class="ticket-title" :title="t.title">{{ t.title }}</div>
            </div>
            <div class="ticket-meta">
              <el-tag :type="priorityType(t.priority)" size="small" effect="light">{{ priorityLabel(t.priority) }}</el-tag>
              <el-tag :type="statusType(t.status)" size="small" effect="plain">{{ statusLabel(t.status) }}</el-tag>
              <span class="ticket-time">{{ shortTime(t.created_at) }}</span>
            </div>
          </div>
          <div v-if="personalList.length === 0" class="empty-tip">暂无工单</div>
        </div>
      </div>

      <div class="grid-3" v-if="!isPersonal">
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
              <div class="bot-bubble">您好，我是 AI 智能助手小智，可以帮您查工单、解答常见问题。</div>
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

      <!-- 个人视角：热门知识 + AI 助手（自助服务） -->
      <div class="grid-2" v-if="isPersonal">
        <div class="panel-card list-panel">
          <div class="panel-head">
            <span class="panel-title">热门知识</span>
            <router-link to="/chat" class="more">去问答 →</router-link>
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
        <div class="panel-card ai-panel">
          <div class="panel-head">
            <span class="panel-title">AI 智能助手</span>
          </div>
          <div class="ai-body">
            <div class="ai-bot">
              <img class="bot-avatar" :src="botAvatar" alt="AI" />
              <div class="bot-bubble">您好，我是 AI 智能助手小智，可以帮您查工单、解答常见问题。</div>
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
      <div class="panel-card overview-panel" v-if="!isPersonal">
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
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, Search, ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'
import * as echarts from 'echarts'
import botAvatar from '../assets/ai-bot-avatar.png'
import agentHero from '../assets/agent-hero.png'

const auth = useAuthStore()
const router = useRouter()
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

// 角色差异化：客户走独立首页，客服展示个人待办，主管/管理员展示全站
const isCustomer = computed(() => auth.role === 'CUSTOMER')
const isAgent = computed(() => auth.role === 'AGENT')
const isPersonal = computed(() => isCustomer.value || isAgent.value)
const myData = ref({})

/* ================= 客户端首页 ================= */
const heroQuery = ref('')

// Hero 右上角的四个数据胶囊
const heroStats = computed(() => {
  const d = myData.value
  return [
    { label: '我的工单', val: d.total || 0 },
    { label: '处理中', val: d.processing || 0 },
    { label: '待确认', val: d.waiting || 0 },
    { label: '已解决', val: d.resolved || 0 }
  ]
})

// 四个快捷入口：提交工单在页内弹窗，其余跳转对应页面
const quickCards = computed(() => [
  { title: '提交工单', desc: '快速提交您的问题', icon: 'EditPen', bg: '#eef2ff', color: '#6366f1', onClick: openCreate },
  { title: '我的工单', desc: '查看工单进度', icon: 'Tickets', bg: '#eff6ff', color: '#3b82f6', onClick: () => router.push('/tickets') },
  { title: '知识库', desc: '查找解决方案', icon: 'Collection', bg: '#ecfdf5', color: '#10b981', onClick: () => router.push('/knowledge') },
  { title: 'AI 助手', desc: '智能问答，快速解决', icon: 'ChatDotRound', bg: '#f5f3ff', color: '#8b5cf6', onClick: () => router.push('/chat') }
])

const myRecent = computed(() => myData.value.recent || [])

// Hero 搜索：带着关键字跳到 AI 问答页并自动提问
function askFromHero() {
  const q = heroQuery.value.trim()
  if (!q) {
    ElMessage.warning('请输入您想咨询的问题')
    return
  }
  router.push({ path: '/chat', query: { q } })
}

// 常见问题点击：同样带着标题跳去 AI 问答
function askQuestion(title) {
  router.push({ path: '/chat', query: { q: title } })
}

// 我的工单行点击：到工单页并按工单号过滤
function goTicketDetail(t) {
  router.push({ path: '/tickets', query: { keyword: t.ticket_no } })
}

/* ================= 提交工单弹窗 ================= */
const createVisible = ref(false)
const submitting = ref(false)
const categories = ref([])
const createRequestId = ref('')
const createForm = reactive({ title: '', categoryId: null, priority: 'MEDIUM', description: '' })

// 打开弹窗：重置表单、生成幂等键、按需加载分类
async function openCreate() {
  createForm.title = ''
  createForm.description = ''
  createForm.categoryId = null
  createForm.priority = 'MEDIUM'
  createRequestId.value = (window.crypto?.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(16).slice(2)}`)
  createVisible.value = true
  if (categories.value.length === 0) {
    try {
      categories.value = await api.get('/tickets/meta/categories')
    } catch (e) { /* 拦截器已提示 */ }
  }
}

// 提交工单：成功后关闭弹窗并刷新首页数据
async function doCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入工单主题')
    return
  }
  if (!createForm.description.trim()) {
    ElMessage.warning('请描述您遇到的问题')
    return
  }
  submitting.value = true
  try {
    await api.post('/tickets', createForm, { headers: { 'Idempotency-Key': createRequestId.value } })
    ElMessage.success('工单提交成功，我们会尽快处理')
    createVisible.value = false
    await loadMyOverview()
  } catch (e) { /* 拦截器已提示 */ } finally {
    submitting.value = false
  }
}

/* ================= 管理端 / 客服工作台 ================= */
const kpiCards = computed(() => {
  if (isCustomer.value) {
    const d = myData.value
    return [
      { label: '我的工单', value: d.total || 0, icon: 'Tickets', bg: '#eef2ff', color: '#6366f1' },
      { label: '处理中', value: d.processing || 0, icon: 'Loading', bg: '#eff6ff', color: '#3b82f6' },
      { label: '待确认', value: d.waiting || 0, icon: 'Clock', bg: '#fff7ed', color: '#f59e0b' },
      { label: '已解决', value: d.resolved || 0, icon: 'CircleCheck', bg: '#f0fdf4', color: '#22c55e' }
    ]
  }
  if (isAgent.value) {
    const d = myData.value
    return [
      { label: '待接单', value: d.assigned || 0, icon: 'Clock', bg: '#fff7ed', color: '#f59e0b' },
      { label: '处理中', value: d.processing || 0, icon: 'Loading', bg: '#eff6ff', color: '#3b82f6' },
      { label: '待客户确认', value: d.waiting || 0, icon: 'Tickets', bg: '#fdf2f8', color: '#ec4899' },
      { label: '已解决', value: d.resolved || 0, icon: 'CircleCheck', bg: '#f0fdf4', color: '#22c55e' }
    ]
  }
  return [
    { label: '工单总数', value: overview.value.totalTickets || 0, icon: 'Tickets', bg: '#eef2ff', color: '#6366f1' },
    { label: '待处理工单', value: overview.value.pendingTickets || 0, icon: 'Clock', bg: '#fff7ed', color: '#f59e0b' },
    { label: '处理中工单', value: overview.value.processingTickets || 0, icon: 'Loading', bg: '#eff6ff', color: '#3b82f6' },
    { label: '已解决工单', value: overview.value.resolvedTickets || 0, icon: 'CircleCheck', bg: '#f0fdf4', color: '#22c55e' },
    { label: '今日新增', value: overview.value.todayNew || 0, icon: 'TrendCharts', bg: '#fdf2f8', color: '#ec4899' }
  ]
})

const personalList = computed(() => {
  if (isCustomer.value) return myData.value.recent || []
  if (isAgent.value) return myData.value.todo || []
  return []
})

const priorityMap = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityTypeMap = { URGENT: 'danger', HIGH: 'warning', MEDIUM: 'primary', LOW: 'info' }
const priorityLabel = p => priorityMap[p] || p
const priorityType = p => priorityTypeMap[p] || 'info'
const shortTime = t => (t ? t.replace('T', ' ').substring(5, 16) : '')

const statusMap = { PENDING: '待分配', ASSIGNED: '待受理', PROCESSING: '处理中', WAITING: '待确认', RESOLVED: '已解决', CLOSED: '已关闭' }
const statusTypeMap = { PENDING: 'info', ASSIGNED: 'warning', PROCESSING: 'primary', WAITING: 'warning', RESOLVED: 'success', CLOSED: 'info' }
const statusLabel = s => statusMap[s] || s
const statusType = s => statusTypeMap[s] || 'info'

async function loadOverview() {
  overview.value = await api.get('/stats/overview')
  recentTickets.value = (await api.get('/tickets', { params: { page: 1, pageSize: 6 } })).list || []
  hotArticles.value = await api.get('/knowledge/hot', { params: { limit: 5 } })
  await nextTick()
  renderTrend()
  renderCategory()
}

// 个人视角（客户 / 客服）数据：概览 + 热门知识
async function loadMyOverview() {
  myData.value = await api.get('/stats/my-overview')
  hotArticles.value = await api.get('/knowledge/hot', { params: { limit: 5 } })
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
  if (isPersonal.value) {
    loadMyOverview()
  } else {
    loadOverview()
  }
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
/* ================= 客服工作台横幅 ================= */
.agent-hero {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 182px;
  padding: 24px 250px 24px 34px;
  border-radius: 18px;
  overflow: hidden;
  /* 光晕用径向渐变（不会产生硬边）；右侧约 25% 保持纯渐变，避免插画 multiply 显出色块 */
  background:
    radial-gradient(430px 250px at 54% -70px, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0) 72%),
    radial-gradient(320px 210px at 30% 132%, rgba(147, 197, 253, 0.38), rgba(147, 197, 253, 0) 70%),
    linear-gradient(112deg, #eef4ff 0%, #dbe9ff 46%, #c8ddfd 100%);
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.10);
}
/* 插画白底在浅蓝渐变上用 multiply 融合，等同透明 */
.ah-main { position: relative; z-index: 2; min-width: 0; }
.ah-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.3;
  font-weight: 700;
  color: #16265c;
  letter-spacing: 0.5px;
}
.ah-wave { margin-left: 2px; font-size: 23px; }
.ah-sub { margin: 9px 0 0; font-size: 13.5px; color: #47598a; }
.ah-date { margin: 6px 0 0; font-size: 12px; color: #7d8cb8; }
/* 插画白底在浅蓝渐变上用 multiply 融合，等同透明 */
.ah-illus {
  position: absolute;
  right: 26px;
  bottom: 0;
  height: 182px;
  width: auto;
  z-index: 1;
  pointer-events: none;
  user-select: none;
  mix-blend-mode: multiply;
}

.welcome h2 { margin: 0 0 4px; font-size: 20px; }
.welcome p { margin: 0; color: var(--text-sub); font-size: 14px; }

/* ================= 客户端首页 ================= */
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 30px 34px;
  border-radius: 18px;
  overflow: hidden;
  background: linear-gradient(115deg, #eef2ff 0%, #e0eaff 42%, #cfe0fe 100%);
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.06);
}
.hero-deco {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 52%;
  height: 100%;
  pointer-events: none;
}
.hero-main {
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.hero-title {
  margin: 0;
  font-size: 27px;
  line-height: 1.35;
  font-weight: 700;
  color: #1e2c5c;
  letter-spacing: 0.5px;
}
.hero-sub {
  margin: 10px 0 18px;
  font-size: 13px;
  color: #4b5b86;
}
.hero-search {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 520px;
  height: 46px;
  padding: 0 6px 0 16px;
  background: #fff;
  border-radius: 23px;
  box-shadow: 0 6px 18px rgba(59, 130, 246, 0.16);
}
.hs-icon { color: #9ca3af; font-size: 17px; }
.hs-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: var(--text-main);
}
.hs-input::placeholder { color: #9ca3af; }
.hs-btn {
  height: 34px;
  padding: 0 22px;
  border: none;
  border-radius: 17px;
  background: linear-gradient(135deg, #4d7cf6, #6b5cf6);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.2s;
}
.hs-btn:hover { opacity: 0.9; }

.hero-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  flex-shrink: 0;
}
.hs-cell {
  min-width: 106px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(4px);
  text-align: center;
}
.hs-val { font-size: 21px; font-weight: 700; color: #1e2c5c; line-height: 1.1; }
.hs-label { margin-top: 4px; font-size: 12px; color: #5b6b96; }

.quick-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.quick-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;
}
.quick-card:hover {
  transform: translateY(-2px);
  border-color: #dbe4ff;
  box-shadow: 0 8px 22px rgba(99, 102, 241, 0.12);
}
.quick-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.quick-body { min-width: 0; }
.quick-title { font-size: 15px; font-weight: 600; }
.quick-desc { margin-top: 3px; font-size: 12px; color: var(--text-muted); }

.home-cols {
  display: grid;
  grid-template-columns: 1.5fr 1fr 0.85fr;
  gap: 16px;
  align-items: stretch;
}
.col-block { padding: 16px; display: flex; flex-direction: column; }

.tk-list { display: flex; flex-direction: column; }
.tk-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
}
.tk-row:last-child { border-bottom: none; }
.tk-row:hover { background: #f8f9ff; }
.tk-no { font-size: 11px; color: var(--text-muted); white-space: nowrap; }
.tk-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tk-tag { flex-shrink: 0; }
.tk-time { font-size: 11px; color: var(--text-muted); white-space: nowrap; }

.faq-list { display: flex; flex-direction: column; }
.faq-row {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-bottom: 1px solid #f3f4f6;
}
.faq-row:last-child { border-bottom: none; }
.faq-row:hover { background: #f8f9ff; }
.faq-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #93b4fb;
  flex-shrink: 0;
}
.faq-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.faq-arrow { font-size: 12px; color: #c4c9d4; }

.block-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 28px 0;
}

.ai-card {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  padding: 20px;
  background: linear-gradient(150deg, #4d7cf6 0%, #6a5cf0 60%, #8360ee 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 210px;
}
.ai-card-body { position: relative; z-index: 1; }
.ai-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 11px;
}
.ai-slogan {
  margin: 12px 0 16px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}
.ai-go {
  padding: 8px 20px;
  border: none;
  border-radius: 18px;
  background: #fff;
  color: #5b5ff0;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.18s;
}
.ai-go:hover { transform: scale(1.04); }
.ai-figure {
  position: absolute;
  right: -14px;
  bottom: -16px;
  width: 108px;
  height: 108px;
  object-fit: cover;
  border-radius: 50%;
  opacity: 0.9;
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.18));
  pointer-events: none;
}

@media (max-width: 1400px) {
  .home-cols { grid-template-columns: 1fr 1fr; }
  .ai-card { grid-column: span 2; min-height: 160px; }
}
@media (max-width: 1100px) {
  .hero { flex-direction: column; align-items: flex-start; }
  .quick-row { grid-template-columns: repeat(2, 1fr); }
  .home-cols { grid-template-columns: 1fr; }
  .ai-card { grid-column: auto; }
}

/* ================= 管理端 / 客服工作台 ================= */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
/* 客服视角只有 4 张卡，按 4 列铺满（否则会右侧空出 1/5） */
.kpi-row.kpi-cols-4 { grid-template-columns: repeat(4, 1fr); }
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
.empty-tip { text-align: center; color: var(--text-muted); font-size: 13px; padding: 24px 0; }

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
  .kpi-row.kpi-cols-4 { grid-template-columns: repeat(2, 1fr); }
  .grid-3 { grid-template-columns: 1fr; }
  .overview-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 900px) {
  .agent-hero { padding-right: 34px; }
  .ah-illus { display: none; }
}
</style>
