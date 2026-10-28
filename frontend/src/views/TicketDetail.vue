<template>
  <div class="td-page" v-loading="loading">
    <!-- 顶部操作栏：返回 / 面包屑 / 状态徽章 / 工单操作 -->
    <div class="td-topbar">
      <div class="tb-left">
        <button class="tb-back" title="返回列表" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <div class="tb-crumb">
          <span class="crumb-link" @click="goBack">{{ isStaff ? '工单列表' : '我的工单' }}</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-cur">{{ ticketNo }}</span>
        </div>
        <span class="badge" :class="'st-' + status">{{ statusLabel }}</span>
        <span class="badge" :class="'pr-' + priority">{{ priorityLabel }}</span>
      </div>
      <div class="tb-right" v-if="isStaff && ticket">
        <template v-if="canReview">
          <el-button size="small" type="success" @click="doReview(true)">通过申请</el-button>
          <el-button size="small" type="danger" plain @click="doReview(false)">驳回</el-button>
        </template>
        <el-button size="small" :disabled="!canAssign" @click="assignVisible = true">指派 / 转接</el-button>
        <el-button size="small" type="success" :disabled="!canResolve" @click="openResolve">
          {{ status === 'RESOLVED' ? '关闭工单' : '解决并关闭' }}
        </el-button>
      </div>
    </div>

    <!-- 待审核提示：客服在工单大厅申请负责处理该工单 -->
    <div class="review-bar" v-if="status === 'PENDING_REVIEW' && isStaff">
      <el-icon class="rb-icon"><UserFilled /></el-icon>
      <span class="rb-text">
        <b>{{ applicantName || '有客服' }}</b> 申请负责处理该工单，等待审核
        <span class="rb-time" v-if="applyTime">（{{ fmtShort(applyTime) }}）</span>
      </span>
      <div class="rb-actions" v-if="isReviewer">
        <el-button size="small" type="success" @click="doReview(true)">同意分配</el-button>
        <el-button size="small" type="danger" plain @click="doReview(false)">驳回</el-button>
      </div>
    </div>

    <div class="td-grid" v-if="ticket">
      <!-- ==================== 左栏：客户 / 时效 / 属性 ==================== -->
      <div class="col col-left">
        <!-- 客户概览 -->
        <section class="card">
          <header class="card-head">
            <h3>客户概览</h3>
            <span class="tag" :class="'lv-' + customerLevel.key">{{ customerLevel.label }}</span>
          </header>
          <div class="cust-main">
            <img v-if="customerAvatarUrl" class="cust-avatar" :src="customerAvatarUrl" :alt="customerName" />
            <div v-else class="cust-avatar" :style="{ background: customerAvatarColor }">{{ (customerName || '?').charAt(0) }}</div>
            <div class="cust-info">
              <div class="cust-name">{{ customerName || '未知客户' }}</div>
              <div class="cust-sub">{{ customerOrg }}</div>
            </div>
          </div>
          <ul class="kv-list">
            <li>
              <span class="k">联系电话</span>
              <span class="v mono" :title="customerPhone || '未填写'">{{ displayPhone }}</span>
            </li>
            <li>
              <span class="k">电子邮箱</span>
              <span class="v mono ellipsis" :title="customerEmail || '未填写'">{{ customerEmail || '未填写' }}</span>
            </li>
            <li>
              <span class="k">服务响应</span>
              <span class="v strong" :class="'sla-' + slaLevel">{{ slaText }}</span>
            </li>
            <li>
              <span class="k">历史工单</span>
              <span class="v link" v-if="isStaff" @click="goCustomerTickets">{{ customerTicketCount }} 条记录</span>
              <span class="v" v-else>{{ customerTicketCount }} 条记录</span>
            </li>
          </ul>
        </section>

        <!-- 问题描述（原中栏，移至 SLA 位置） -->
        <section class="card">
          <header class="card-head">
            <h3>问题描述</h3>
          </header>
          <p class="desc-body">{{ val(ticket, 'description') || '暂无描述' }}</p>
        </section>

        <!-- AI 智能摘要（仅客服可见，原中栏，移至 SLA 位置） -->
        <section class="card" v-if="isStaff">
          <header class="card-head">
            <h3>AI 智能摘要</h3>
            <div class="head-tools">
              <span class="card-hint" v-if="summaryFallback">本地摘要</span>
              <button class="ais-refresh" :class="{ spin: summarizing }" @click="loadSummary" title="重新生成">
                <el-icon><Refresh /></el-icon>
              </button>
            </div>
          </header>
          <p class="ais-body">{{ summaryText }}</p>
        </section>

        <!-- 工单属性 -->
        <section class="card">
          <header class="card-head">
            <h3>工单属性</h3>
          </header>
          <ul class="kv-list">
            <li>
              <span class="k">工单编号</span>
              <span class="v mono">{{ ticketNo }}</span>
            </li>
            <li>
              <span class="k">工单状态</span>
              <span class="v"><span class="badge" :class="'st-' + status">{{ statusLabel }}</span></span>
            </li>
            <li>
              <span class="k">优先级</span>
              <span class="v"><span class="badge" :class="'pr-' + priority">{{ priorityLabel }}</span></span>
            </li>
            <li>
              <span class="k">工单分类</span>
              <span class="v">
                <span class="cat" :style="{ color: categoryColor, borderColor: categoryColor }">{{ categoryName }}</span>
              </span>
            </li>
            <li>
              <span class="k">负责人</span>
              <span class="v">{{ assigneeName || '待分配' }}</span>
            </li>
            <li>
              <span class="k">来源渠道</span>
              <span class="v">{{ sourceLabel }}</span>
            </li>
            <li>
              <span class="k">创建时间</span>
              <span class="v">{{ fmtTime(createdAt) }}</span>
            </li>
            <li v-if="satisfaction">
              <span class="k">客户评价</span>
              <span class="v strong">{{ '★'.repeat(satisfaction) }}</span>
            </li>
          </ul>
        </section>
      </div>

      <!-- ==================== 中栏：工单头 / 摘要 / 时间线 / 回复 ==================== -->
      <div class="col col-mid">
        <header class="mid-head">
          <div class="mid-title-row">
            <span class="cat" :style="{ color: categoryColor, borderColor: categoryColor }">{{ categoryName }}</span>
            <h1 class="mid-title">{{ title }}</h1>
          </div>
          <p class="mid-meta">
            来源：{{ sourceLabel }} · 创建于 {{ fmtTime(createdAt) }}
            <template v-if="assigneeName"> · 当前处理人 {{ assigneeName }}</template>
          </p>
        </header>

        <!-- 时间线（中栏已腾出全部空间给消息区） -->
        <div class="timeline" ref="timelineRef">
          <template v-for="(item, idx) in timeline" :key="idx">
            <!-- 系统事件 -->
            <div class="tl-sys" v-if="item.kind === 'sys'">
              <span class="tl-sys-pill">
                <el-icon><Connection /></el-icon>
                {{ item.content }} · {{ fmtShort(item.time) }}
              </span>
            </div>
            <!-- 消息气泡：我方消息头像在右，对方消息头像在左（客服端=客服右客户左，客户端自动反之） -->
            <div class="tl-msg" v-else :class="{ note: item.kind === 'note', mine: item.mine }">
              <div class="tl-avatar" :style="item.avatarUrl ? {} : { background: item.avatarColor }">
                <img v-if="item.avatarUrl" class="tl-avatar-img" :src="item.avatarUrl" :alt="item.sender" />
                <template v-else>{{ (item.sender || '?').charAt(0) }}</template>
              </div>
              <div class="tl-body">
                <div class="tl-meta">
                  <span class="tl-sender">{{ item.sender }}</span>
                  <span class="tl-role" v-if="item.kind === 'note'">内部备注</span>
                  <span class="tl-role public" v-else-if="item.mine && isStaff">公开回复</span>
                  <span class="tl-role" v-else-if="item.isCustomer && !item.mine">客户</span>
                  <span class="tl-role" v-else-if="!item.mine">客服</span>
                  <span class="tl-time">{{ fmtShort(item.time) }}</span>
                </div>
                <div class="tl-bubble">{{ item.content }}</div>
              </div>
            </div>
          </template>
          <div class="tl-empty" v-if="!timeline.length">暂无沟通记录</div>
        </div>

        <!-- 回复区 -->
        <div class="reply-box" v-if="canReply">
          <div class="reply-tabs">
            <div class="tabs" v-if="isStaff">
              <button :class="{ on: replyType === 'PUBLIC' }" @click="replyType = 'PUBLIC'">公开回复（发送给客户）</button>
              <button :class="{ on: replyType === 'INTERNAL', warn: replyType === 'INTERNAL' }" @click="replyType = 'INTERNAL'">内部备注（仅团队可见）</button>
            </div>
            <div class="tabs" v-else>
              <button class="on">公开回复</button>
            </div>
            <button class="ai-fill" v-if="isStaff" :disabled="drafting" @click="useDraft">
              <el-icon><MagicStick /></el-icon>
              {{ drafting ? '生成中…' : '一键套用 AI 推荐回复' }}
            </button>
          </div>
          <textarea
            v-model="replyContent"
            class="reply-input"
            :class="{ warn: replyType === 'INTERNAL' }"
            rows="3"
            :placeholder="replyType === 'INTERNAL' ? '输入内部备注，仅团队同事可见…' : '输入回复内容，发送给客户…'"
            @keydown.ctrl.enter="doReply"></textarea>
          <div class="reply-toolbar">
            <div class="toolbar-left">
              <button class="tool" title="引用知识库文章"
                      v-for="a in relatedKnowledge" :key="a.id" @click="useArticle(a)">《{{ shortTitle(a.title) }}》</button>
            </div>
            <div class="toolbar-right">
              <label class="resolve-check" v-if="isStaff && replyType === 'PUBLIC' && status !== 'WAITING'">
                <input type="checkbox" v-model="resolveAfter" />
                发送后提交解决方案
              </label>
              <span class="kbd-hint">Ctrl + Enter</span>
              <el-button type="primary" size="small" :loading="sending" @click="doReply">发送回复</el-button>
            </div>
          </div>
        </div>
        <div class="reply-locked" v-else>工单已{{ statusLabel }}，无法继续回复</div>
      </div>

      <!-- ==================== 右栏：AI Copilot / 知识库 / 模板 ==================== -->
      <div class="col col-right">
        <!-- AI Copilot -->
        <section class="copilot" v-if="isStaff">
          <header class="cp-head">
            <span class="cp-title">
              <el-icon><MagicStick /></el-icon>
              AI Copilot 处置建议
            </span>
            <span class="cp-badge" v-if="stepsFallback">规则模板</span>
            <span class="cp-badge" v-else-if="steps.length">实时生成</span>
          </header>
          <div class="cp-body">
            <div class="cp-loading" v-if="loadingSteps">正在分析工单…</div>
            <template v-else>
              <div class="cp-step" v-for="s in steps" :key="s.order" @click="useStep(s)">
                <span class="cp-order">{{ s.order }}</span>
                <div class="cp-text">
                  <div class="cp-step-title">{{ s.title }}</div>
                  <div class="cp-step-detail">{{ s.detail }}</div>
                </div>
              </div>
              <div class="cp-empty" v-if="!steps.length">暂无建议，点击下方重新生成</div>
            </template>
          </div>
          <button class="cp-refresh" :disabled="loadingSteps" @click="loadSteps">
            {{ steps.length ? '重新生成建议' : '生成处置建议' }}
          </button>
        </section>

        <!-- 推荐知识库 -->
        <section class="card kb-card">
          <header class="card-head">
            <h3>推荐知识库文章</h3>
            <span class="card-hint">{{ relatedKnowledge.length }} 篇</span>
          </header>
          <div class="kb-list">
            <div class="kb-item" v-for="a in relatedKnowledge" :key="a.id" @click="openArticle(a)">
              <div class="kb-title">{{ a.title }}</div>
              <div class="kb-meta">
                <span>{{ a.viewCount }} 次浏览</span>
                <span class="kb-score" :class="scoreClass(a.matchScore)">匹配度 {{ a.matchScore }}%</span>
              </div>
            </div>
            <div class="kb-empty" v-if="!relatedKnowledge.length">知识库暂无相关文章</div>
          </div>
        </section>

        <!-- 快捷模板 -->
        <section class="card" v-if="isStaff">
          <header class="card-head">
            <h3>快捷话术</h3>
          </header>
          <div class="tpl-grid">
            <button class="tpl" v-for="t in templates" :key="t.title" @click="useTemplate(t)">
              <span class="tpl-title">{{ t.title }}</span>
              <span class="tpl-sub">{{ t.sub }}</span>
            </button>
          </div>
        </section>
      </div>
    </div>

    <!-- 指派弹窗 -->
    <el-dialog v-model="assignVisible" title="指派 / 转接工单" width="420px">
      <el-select v-model="assignTarget" placeholder="选择处理人" style="width: 100%">
        <el-option v-for="a in agents" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="doAssign">确认指派</el-button>
      </template>
    </el-dialog>

    <!-- 解决方案弹窗 -->
    <el-dialog v-model="resolveVisible" title="提交解决方案" width="520px">
      <p class="dlg-tip">提交后工单进入「待客户确认」，客户确认后自动关闭。</p>
      <el-input v-model="solutionText" type="textarea" :rows="5" placeholder="请描述问题原因与处理结果，客户将看到这段内容" />
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="resolving" @click="doResolve">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, MagicStick, Refresh, Connection, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const ticketId = computed(() => route.params.id)
const me = computed(() => auth.user || {})
const isStaff = computed(() => ['ADMIN', 'SUPERVISOR', 'AGENT'].includes(auth.role))
// 接单申请审核仅主管与管理员可操作
const isReviewer = computed(() => ['ADMIN', 'SUPERVISOR'].includes(auth.role))

const loading = ref(true)
const detail = ref(null)
const agents = ref([])
const relatedKnowledge = ref([])
const customerTicketCount = ref(0)

const replyType = ref('PUBLIC')
const replyContent = ref('')
const resolveAfter = ref(false)
const sending = ref(false)
const drafting = ref(false)
const summarizing = ref(false)
const summaryText = ref('')
const summaryFallback = ref(false)

const steps = ref([])
const loadingSteps = ref(false)
const stepsFallback = ref(false)

const assignVisible = ref(false)
const assignTarget = ref(null)
const assigning = ref(false)
const resolveVisible = ref(false)
const solutionText = ref('')
const resolving = ref(false)
const timelineRef = ref(null)
const now = ref(Date.now())

let clockTimer = null
let pollTimer = null

/* 兼容后端两种命名：实体序列化为驼峰，JdbcTemplate 返回下划线 */
const val = (obj, key) => {
  if (!obj) return undefined
  const v = obj[key]
  if (v !== undefined && v !== null) return v
  return obj[key.replace(/[A-Z]/g, c => '_' + c.toLowerCase())]
}

const ticket = computed(() => detail.value?.ticket || null)
const ticketNo = computed(() => val(ticket.value, 'ticketNo') || '—')
const title = computed(() => val(ticket.value, 'title') || '')
const status = computed(() => val(ticket.value, 'status') || '')
const priority = computed(() => val(ticket.value, 'priority') || 'MEDIUM')
const categoryName = computed(() => val(ticket.value, 'categoryName') || '未分类')
const categoryColor = computed(() => val(ticket.value, 'categoryColor') || '#6366f1')
const assigneeName = computed(() => val(ticket.value, 'assigneeName') || '')
const assigneeId = computed(() => val(ticket.value, 'assigneeId'))
const customerName = computed(() => val(ticket.value, 'customerName') || '')
const customerPhone = computed(() => val(ticket.value, 'customerPhone') || '')
const customerEmail = computed(() => val(ticket.value, 'customerEmail') || '')
const customerCompany = computed(() => val(ticket.value, 'customerCompany') || '')
const customerDepartment = computed(() => val(ticket.value, 'customerDepartment') || '')
const satisfaction = computed(() => val(ticket.value, 'satisfaction') || 0)
const createdAt = computed(() => val(ticket.value, 'createdAt'))
/* SLA 卡已删，仅保留「服务响应」行所需：解决目标时间 + 实际解决时间换算剩余/超时 */
const slaResolveAt = computed(() => val(ticket.value, 'slaResolveAt'))
const resolvedAt = computed(() => val(ticket.value, 'resolvedAt'))

const customerOrg = computed(() => {
  const parts = [customerCompany.value, customerDepartment.value].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未填写所属公司'
})
const customerAvatarUrl = computed(() => val(ticket.value, 'customerAvatarUrl') || '')
const customerAvatarColor = computed(() => val(ticket.value, 'customerAvatarColor') || avatarColor.value)
const avatarColor = computed(() => categoryColor.value || '#6366f1')
/* 客服端手机号脱敏（悬停看完整），客户端本人查看显示完整号码 */
const displayPhone = computed(() => {
  if (!customerPhone.value) return '未填写'
  return isStaff.value ? maskPhone(customerPhone.value) : customerPhone.value
})

const statusMap = {
  PENDING: '待分配', PENDING_REVIEW: '待审核', ASSIGNED: '待受理', PROCESSING: '处理中',
  WAITING: '待客户确认', RESOLVED: '已解决', CLOSED: '已关闭'
}
const statusLabel = computed(() => statusMap[status.value] || status.value || '—')
const priorityMap = { URGENT: '紧急', HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }
const priorityLabel = computed(() => priorityMap[priority.value] || priority.value)
const sourceMap = { WEB: '网页提交', PHONE: '电话', EMAIL: '邮件', CHAT: '在线咨询' }
const sourceLabel = computed(() => sourceMap[val(ticket.value, 'source')] || val(ticket.value, 'source') || '网页提交')

const canReply = computed(() => status.value && !['CLOSED'].includes(status.value))
const canAssign = computed(() => ['PENDING', 'ASSIGNED', 'PROCESSING'].includes(status.value))
const canResolve = computed(() => ['ASSIGNED', 'PROCESSING', 'RESOLVED'].includes(status.value))
// 待审核工单只有主管与管理员能审核
const canReview = computed(() => isReviewer.value && status.value === 'PENDING_REVIEW')

/* ===== 接单申请信息：客服在工单大厅申请后由后端带出，供审核展示 ===== */
const applyInfo = computed(() => detail.value?.apply || null)
const applicantName = computed(() => val(applyInfo.value, 'applicantName') || '')
const applyTime = computed(() => val(applyInfo.value, 'applyAt') || '')

/* ===== 客户活跃度标签（库里没有 VIP 字段，按历史工单数分档） ===== */
const customerLevel = computed(() => {
  const n = customerTicketCount.value
  if (n > 20) return { key: 'high', label: '活跃客户' }
  if (n >= 5) return { key: 'mid', label: '常客' }
  return { key: 'low', label: '新客户' }
})

/* ===== SLA 剩余时长 ===== */
const slaRemainMs = computed(() => {
  const target = slaResolveAt.value ? new Date(slaResolveAt.value).getTime() : 0
  if (!target) return null
  if (resolvedAt.value) return new Date(resolvedAt.value).getTime() - target
  return target - now.value
})
const slaLevel = computed(() => {
  const ms = slaRemainMs.value
  if (ms === null) return 'none'
  if (ms < 0) return 'over'
  if (ms < 3600 * 1000) return 'danger'
  if (ms < 4 * 3600 * 1000) return 'warn'
  return 'ok'
})
const slaText = computed(() => {
  const ms = slaRemainMs.value
  if (ms === null) return '未设置'
  if (resolvedAt.value) return ms <= 0 ? '按时解决' : `提前 ${fmtDur(-ms)}`
  if (ms < 0) return `已超时 ${fmtDur(-ms)}`
  return fmtDur(ms)
})

/* ===== 时间线：消息与历史合并排序 ===== */
const actionMap = {
  CREATE: '创建工单', ASSIGN: '分配工单', ACCEPT: '客服接单', REPLY: '回复工单',
  RESOLVE: '提交解决方案', CONFIRM: '客户确认', CLOSE: '关闭工单',
  REOPEN: '重新打开', RATE: '客户评价', URGE: '客户催办',
  APPLY: '申请处理', REVIEW: '申请审核'
}
const timeline = computed(() => {
  const items = []
  for (const m of detail.value?.messages || []) {
    const type = val(m, 'messageType')
    const senderId = val(m, 'senderId')
    const mine = senderId === me.value.id
    items.push({
      kind: type === 'INTERNAL' ? 'note' : 'msg',
      sender: val(m, 'senderName') || (mine ? '我' : '用户'),
      senderRole: val(m, 'senderRole'),
      isCustomer: val(m, 'senderRole') === 'CUSTOMER',
      mine,
      avatarUrl: val(m, 'senderAvatarUrl'),
      avatarColor: val(m, 'senderAvatarColor') || (type === 'INTERNAL' ? '#f59e0b' : (mine ? '#2563eb' : '#94a3b8')),
      time: val(m, 'createdAt'),
      content: m.content
    })
  }
  for (const h of detail.value?.histories || []) {
    const action = val(h, 'action')
    const remark = val(h, 'remark')
    const opName = val(h, 'operatorName')
    const opRole = val(h, 'operatorRole')
    const opLabel = opName ? `${opName}${opRole ? `（${opRole === 'CUSTOMER' ? '客户' : '客服'}）` : ''}` : ''
    items.push({
      kind: 'sys',
      time: val(h, 'createdAt'),
      content: (opLabel ? opLabel + ' ' : '') + (actionMap[action] || action) + (remark ? `：${remark}` : '')
    })
  }
  return items.sort((a, b) => new Date(a.time || 0) - new Date(b.time || 0))
})

/* ===== 快捷话术 ===== */
const templates = [
  { title: '索要更多信息', sub: '复现步骤 + 截图', text: '您好，为了更快定位问题，麻烦您补充以下信息：\n1. 具体操作步骤\n2. 问题出现的时间\n3. 页面截图或报错信息\n\n收到后我们会立即跟进处理，感谢配合。' },
  { title: '安抚 + 预计时效', sub: '告知处理中', text: '您好，您的反馈我们已收到并正在处理中，预计 24 小时内给出初步方案，有进展会第一时间同步给您。' },
  { title: '提交解决方案', sub: '说明处理结果', text: '您好，您反馈的问题已处理完成：\n1. \n\n请您确认是否恢复正常，如有异常可随时回复本条工单，我们会继续跟进。' }
]

/* ===== 工具函数 ===== */
const fmtTime = (t) => {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
const fmtShort = (t) => {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
const fmtDur = (ms) => {
  if (ms <= 0) return '0 分钟'
  const m = Math.floor(ms / 60000)
  if (m < 60) return `${m} 分钟`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时 ${m % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}
const maskPhone = (p) => {
  if (!p) return '未填写'
  const s = String(p)
  return s.length >= 7 ? `${s.slice(0, 3)} **** ${s.slice(-4)}` : s
}
const shortTitle = (t) => (t || '').length > 8 ? (t || '').slice(0, 8) + '…' : (t || '')
const scoreClass = (s) => (s >= 85 ? 'high' : s >= 70 ? 'mid' : 'low')

/* ===== 数据加载 ===== */
async function load() {
  loading.value = true
  try {
    const data = await api.get(`/tickets/${ticketId.value}`)
    detail.value = data || {}
    relatedKnowledge.value = data?.relatedKnowledge || []
    customerTicketCount.value = data?.customerTicketCount || 0
    await nextTick()
    scrollToBottom()
    // 摘要与处置建议首次加载后缓存，回复刷新时不重复消耗模型调用
    if (isStaff.value) {
      if (!summaryText.value) loadSummary()
      if (!steps.value.length) loadSteps()
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function loadAgents() {
  if (!isStaff.value) return
  try {
    agents.value = (await api.get('/tickets/meta/agents')) || []
  } catch (e) { /* 忽略 */ }
}

/* 静默刷新：轮询与发送后用，不显示加载遮罩、不打断输入；仅在已贴近底部时自动滚动 */
async function refreshSilent() {
  if (!ticketId.value) return
  try {
    const data = await api.get(`/tickets/${ticketId.value}`)
    detail.value = data || {}
    relatedKnowledge.value = data?.relatedKnowledge || []
    customerTicketCount.value = data?.customerTicketCount || 0
    const el = timelineRef.value
    if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 80) {
      await nextTick()
      scrollToBottom()
    }
  } catch (e) { /* 静默失败，下轮再试 */ }
}

async function loadSummary() {
  if (!ticket.value || summarizing.value) return
  summarizing.value = true
  try {
    const r = await api.post('/ai/summarize', {
      title: title.value,
      description: val(ticket.value, 'description') || ''
    })
    summaryText.value = r?.summary || ''
    summaryFallback.value = !!r?.error
  } catch (e) {
    summaryText.value = '摘要生成失败，可稍后重试'
  } finally {
    summarizing.value = false
  }
}

async function loadSteps() {
  if (loadingSteps.value) return
  loadingSteps.value = true
  try {
    const r = await api.post('/ai/suggest-steps', { ticket_id: Number(ticketId.value) })
    steps.value = r?.steps || []
    stepsFallback.value = !!r?.fallback
  } catch (e) {
    steps.value = []
  } finally {
    loadingSteps.value = false
  }
}

/* ===== 交互 ===== */
function scrollToBottom() {
  if (timelineRef.value) timelineRef.value.scrollTop = timelineRef.value.scrollHeight
}

async function doReply() {
  const content = replyContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入回复内容')
    return
  }
  sending.value = true
  try {
    await api.post(`/tickets/${ticketId.value}/messages`, {
      content,
      messageType: isStaff.value ? replyType.value : 'PUBLIC'
    })
    if (resolveAfter.value && replyType.value === 'PUBLIC') {
      await api.post(`/tickets/${ticketId.value}/resolve`, { solution: content })
      ElMessage.success('回复已发送，解决方案已提交，等待客户确认')
    } else {
      ElMessage.success('回复已发送')
    }
    replyContent.value = ''
    resolveAfter.value = false
    await refreshSilent()
  } catch (e) { /* 拦截器已提示 */ } finally {
    sending.value = false
  }
}

async function useDraft() {
  drafting.value = true
  try {
    const r = await api.post('/ai/draft', {
      title: title.value,
      description: val(ticket.value, 'description') || '',
      knowledge: relatedKnowledge.value.map(a => ({ id: a.id, title: a.title, body: a.summary || '' }))
    })
    if (r?.draft) {
      replyContent.value = r.draft
      replyType.value = 'PUBLIC'
      ElMessage.success('已填入 AI 推荐回复')
    } else {
      ElMessage.warning('AI 暂时无法生成回复')
    }
  } catch (e) { /* 拦截器已提示 */ } finally {
    drafting.value = false
  }
}

function useStep(step) {
  const line = `${step.order}. ${step.title}：${step.detail}`
  replyContent.value = replyContent.value ? `${replyContent.value}\n${line}` : line
  replyType.value = 'INTERNAL'
  ElMessage.success('已填入内部备注')
}

function useTemplate(t) {
  replyContent.value = t.text
  replyType.value = 'PUBLIC'
}

function useArticle(a) {
  const text = `请参考知识库文章《${a.title}》中的处理方式：${a.summary || ''}`
  replyContent.value = replyContent.value ? `${replyContent.value}\n${text}` : text
}

function openArticle(a) {
  router.push({ path: '/knowledge', query: { keyword: a.title } })
}

function openResolve() {
  if (status.value === 'RESOLVED') {
    doClose()
    return
  }
  solutionText.value = ''
  resolveVisible.value = true
}

async function doResolve() {
  if (!solutionText.value.trim()) {
    ElMessage.warning('请填写解决方案')
    return
  }
  resolving.value = true
  try {
    await api.post(`/tickets/${ticketId.value}/resolve`, { solution: solutionText.value.trim() })
    ElMessage.success('解决方案已提交，等待客户确认')
    resolveVisible.value = false
    await load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    resolving.value = false
  }
}

async function doClose() {
  try {
    await api.post(`/tickets/${ticketId.value}/close`)
    ElMessage.success('工单已关闭')
    await load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function doAssign() {
  if (!assignTarget.value) {
    ElMessage.warning('请选择处理人')
    return
  }
  assigning.value = true
  try {
    await api.post(`/tickets/${ticketId.value}/assign`, { assigneeId: assignTarget.value })
    ElMessage.success('已指派')
    assignVisible.value = false
    await load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    assigning.value = false
  }
}

function goBack() {
  router.push('/tickets')
}

/* 审核客服的接单申请：通过则工单归申请人，驳回则退回待分配 */
async function doReview(approve) {
  let remark = ''
  try {
    const r = await ElMessageBox.prompt(
      approve
        ? `确认将工单分配给「${applicantName.value || '申请人'}」？通过后由其继续处理。`
        : '请填写驳回原因（该工单将退回待分配状态）',
      approve ? '审核通过' : '审核驳回',
      {
        inputPlaceholder: approve ? '备注（可选）' : '驳回原因',
        inputValidator: v => (approve || (v && v.trim())) ? true : '请填写驳回原因',
        type: approve ? 'info' : 'warning'
      }
    )
    remark = r.value || ''
  } catch (e) { return }
  await api.post(`/tickets/${ticketId.value}/review`, { approve, remark })
  ElMessage.success(approve ? '已通过，工单已分配给申请人' : '已驳回，工单退回待分配')
  await load()
}

function goCustomerTickets() {
  const name = customerName.value
  router.push({ path: '/tickets', query: name ? { keyword: name } : {} })
}

onMounted(() => {
  load()
  loadAgents()
  clockTimer = setInterval(() => { now.value = Date.now() }, 30000)
  // 15 秒轮询：对方（客服/客户）新发的消息无需手动刷新即可看到
  pollTimer = setInterval(refreshSilent, 15000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.td-page {
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  position: relative;
}
.td-page :deep(.el-loading-mask) { border-radius: 12px; }

/* ===== 顶部操作栏 ===== */
.td-topbar {
  height: 52px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.tb-left { display: flex; align-items: center; gap: 10px; min-width: 0; }
.tb-back {
  width: 30px; height: 30px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid #e2e8f0; border-radius: 8px;
  background: #fff; color: #475569; cursor: pointer;
}
.tb-back:hover { background: #f8fafc; color: #1e293b; }
.tb-crumb { display: flex; align-items: center; gap: 6px; font-size: 13px; white-space: nowrap; }
.crumb-link { color: #64748b; cursor: pointer; }
.crumb-link:hover { color: #2563eb; }
.crumb-sep { color: #cbd5e1; }
.crumb-cur { color: #0f172a; font-weight: 500; font-family: ui-monospace, monospace; }
.badge {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  border: 1px solid transparent; white-space: nowrap;
}
.st-PENDING { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
/* 待审核：客服已申请、等待主管审核 */
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
.tb-right { display: flex; gap: 8px; flex-shrink: 0; }

/* 待审核提示条 */
.review-bar {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 16px;
  background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px;
  font-size: 13px; color: #92400e;
}
.rb-icon { font-size: 16px; flex-shrink: 0; }
.rb-text { flex: 1; min-width: 0; }
.rb-time { color: #b45309; font-size: 12px; }
.rb-actions { display: flex; gap: 8px; flex-shrink: 0; }

/* ===== 三栏 ===== */
.td-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 288px minmax(0, 1fr) 308px;
  gap: 14px;
}
.col { min-height: 0; display: flex; flex-direction: column; gap: 12px; }
.col-left, .col-right { overflow-y: auto; padding-right: 2px; }

.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  flex-shrink: 0;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 10px; border-bottom: 1px solid #f1f5f9; margin-bottom: 10px;
}
.card-head h3 {
  font-size: 11px; font-weight: 600; letter-spacing: 0.05em;
  color: #94a3b8; text-transform: uppercase; margin: 0;
}
.card-hint { font-size: 11px; color: #94a3b8; }
.head-tools { display: flex; align-items: center; gap: 6px; }
.tag { font-size: 10px; padding: 1px 7px; border-radius: 999px; font-weight: 500; }
.lv-high { background: #fef3c7; color: #b45309; }
.lv-mid { background: #dbeafe; color: #1d4ed8; }
.lv-low { background: #f1f5f9; color: #64748b; }

/* 客户卡 */
.cust-main { display: flex; align-items: center; gap: 10px; }
.cust-avatar {
  width: 40px; height: 40px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 17px; font-weight: 600;
  object-fit: cover; border: 1px solid #e8eef5;
}
.cust-info { min-width: 0; }
.cust-name { font-size: 14px; font-weight: 600; color: #0f172a; }
.cust-sub { font-size: 11px; color: #64748b; margin-top: 2px; }

.kv-list { list-style: none; margin: 12px 0 0; padding: 0; }
.kv-list li {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 6px 0; border-bottom: 1px solid #f8fafc; font-size: 12px;
}
.kv-list li:last-child { border-bottom: none; }
.kv-list .k { color: #64748b; flex-shrink: 0; }
.kv-list .v { color: #334155; text-align: right; min-width: 0; }
.kv-list .v.strong { font-weight: 600; }
.kv-list .v.link { color: #2563eb; cursor: pointer; }
.kv-list .v.link:hover { text-decoration: underline; }
.kv-list .v.dim { color: #94a3b8; }
.mono { font-family: ui-monospace, monospace; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 170px; }
.cat {
  display: inline-block; font-size: 11px; padding: 0 6px; line-height: 18px;
  border: 1px solid; border-radius: 4px;
}

/* SLA 颜色（客户概览「服务响应」行仍在用） */
.sla-ok { background: #10b981; color: #059669; }
.sla-warn { background: #f59e0b; color: #b45309; }
.sla-danger { background: #ef4444; color: #dc2626; }
.sla-over { background: #dc2626; color: #dc2626; }
.sla-none { background: #cbd5e1; color: #94a3b8; }
.kv-list .v.sla-ok, .kv-list .v.sla-warn, .kv-list .v.sla-danger, .kv-list .v.sla-over { background: none; }

/* ===== 中栏 ===== */
.col-mid {
  background: #fff; border: 1px solid #e2e8f0; border-radius: 12px;
  overflow: hidden;
}
.mid-head { padding: 14px 16px; border-bottom: 1px solid #f1f5f9; }
.mid-title-row { display: flex; align-items: flex-start; gap: 8px; }
.mid-title { font-size: 16px; font-weight: 600; color: #0f172a; margin: 0; line-height: 1.4; }
.mid-meta { font-size: 11px; color: #94a3b8; margin: 6px 0 0; }

/* ===== 左栏：AI 摘要卡 / 问题描述卡 ===== */
.ais-refresh {
  border: none; background: transparent;
  color: #60a5fa; cursor: pointer; display: flex; padding: 2px;
}
.ais-refresh:hover { color: #2563eb; }
.ais-refresh.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ais-body { font-size: 12px; line-height: 1.7; color: #475569; margin: 0; }

/* 问题描述（左栏卡片） */
.desc-body {
  font-size: 12px; line-height: 1.75; color: #475569; margin: 0;
  white-space: pre-wrap; word-break: break-word;
}

.timeline {
  flex: 1; min-height: 0; overflow-y: auto;
  padding: 4px 16px 12px; background: #fcfdfe;
  display: flex; flex-direction: column; gap: 12px;
}
.tl-sys { display: flex; justify-content: center; }
.tl-sys-pill {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11px; color: #94a3b8; background: #f1f5f9;
  padding: 3px 10px; border-radius: 999px;
}
.tl-msg { display: flex; gap: 9px; }
.tl-msg.mine { flex-direction: row-reverse; }
.tl-msg.mine .tl-time { margin-left: 0; margin-right: auto; }
.tl-avatar {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 12px; font-weight: 600;
  overflow: hidden;
}
.tl-avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.tl-body { min-width: 0; flex: 1; }
.tl-meta { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.tl-sender { font-size: 12px; font-weight: 600; color: #334155; }
.tl-role {
  font-size: 10px; padding: 0 5px; border-radius: 3px;
  background: #fef3c7; color: #92400e;
}
.tl-role.public { background: #dbeafe; color: #1d4ed8; }
.tl-time { font-size: 10px; color: #94a3b8; margin-left: auto; }
.tl-bubble {
  font-size: 12px; line-height: 1.75; color: #334155;
  background: #fff; border: 1px solid #e8eef5;
  padding: 9px 12px; border-radius: 10px;
  white-space: pre-wrap; word-break: break-word;
}
.tl-msg.mine .tl-bubble { background: #2563eb; border-color: #2563eb; color: #fff; border-top-right-radius: 4px; }
.tl-msg.mine .tl-meta .tl-sender { color: #1d4ed8; }
.tl-msg:not(.mine):not(.note) .tl-bubble { border-top-left-radius: 4px; }
.tl-msg.note .tl-bubble { background: #fffbeb; border-color: #fde68a; color: #78350f; }
.tl-empty { text-align: center; font-size: 12px; color: #94a3b8; padding: 24px 0; }

.reply-box { border-top: 1px solid #e2e8f0; padding: 10px 14px 12px; background: #fff; }
.reply-tabs { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.tabs { display: flex; background: #f1f5f9; border-radius: 8px; padding: 2px; }
.tabs button {
  font-size: 11px; padding: 4px 10px; border: none; border-radius: 6px;
  background: transparent; color: #64748b; cursor: pointer;
}
.tabs button.on { background: #fff; color: #1e293b; box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06); }
.tabs button.warn { background: #f59e0b; color: #fff; }
.ai-fill {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; padding: 4px 9px; border-radius: 6px;
  border: 1px solid #e0e7ff; background: #eef2ff; color: #4f46e5;
  cursor: pointer; white-space: nowrap;
}
.ai-fill:hover:not(:disabled) { background: #e0e7ff; }
.ai-fill:disabled { opacity: 0.6; cursor: default; }
.reply-input {
  width: 100%; box-sizing: border-box; resize: none;
  font-size: 12px; line-height: 1.7; color: #1e293b;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px;
  padding: 9px 11px; font-family: inherit; outline: none;
}
.reply-input:focus { border-color: #93c5fd; background: #fff; }
.reply-input.warn { background: #fffbeb; border-color: #fde68a; }
.reply-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; margin-top: 8px; flex-wrap: wrap;
}
.toolbar-left { display: flex; gap: 5px; flex-wrap: wrap; min-width: 0; }
.tool {
  font-size: 10px; color: #4f46e5; background: #f5f3ff;
  border: 1px solid #ede9fe; border-radius: 5px;
  padding: 2px 7px; cursor: pointer; max-width: 150px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.tool:hover { background: #ede9fe; }
.toolbar-right { display: flex; align-items: center; gap: 10px; margin-left: auto; }
.resolve-check { display: flex; align-items: center; gap: 5px; font-size: 11px; color: #64748b; cursor: pointer; }
.kbd-hint { font-size: 10px; color: #cbd5e1; font-family: ui-monospace, monospace; }
.reply-locked {
  border-top: 1px solid #e2e8f0; padding: 14px; text-align: center;
  font-size: 12px; color: #94a3b8; background: #f8fafc;
}

/* ===== 右栏 ===== */
.copilot {
  background: linear-gradient(160deg, #312e81, #1e293b);
  border-radius: 12px; padding: 13px; color: #e0e7ff; flex-shrink: 0;
}
.cp-head {
  display: flex; align-items: center; gap: 6px;
  padding-bottom: 9px; border-bottom: 1px solid rgba(99, 102, 241, 0.35);
}
.cp-title { display: flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 600; color: #e0e7ff; }
.cp-badge {
  margin-left: auto; font-size: 9px; padding: 1px 6px; border-radius: 999px;
  background: rgba(99, 102, 241, 0.3); color: #c7d2fe;
}
.cp-body { margin-top: 9px; display: flex; flex-direction: column; gap: 6px; }
.cp-loading { font-size: 11px; color: #a5b4fc; padding: 8px 0; }
.cp-step {
  display: flex; gap: 7px; padding: 7px 8px;
  background: rgba(15, 23, 42, 0.45); border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: 8px; cursor: pointer; transition: border-color 0.15s;
}
.cp-step:hover { border-color: rgba(129, 140, 248, 0.7); }
.cp-order {
  width: 16px; height: 16px; flex-shrink: 0; margin-top: 1px;
  border-radius: 50%; background: rgba(59, 130, 246, 0.25); color: #bfdbfe;
  font-size: 9px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.cp-step-title { font-size: 11px; font-weight: 600; color: #fff; line-height: 1.4; }
.cp-step-detail { font-size: 10px; color: #a5b4fc; line-height: 1.55; margin-top: 2px; }
.cp-empty { font-size: 11px; color: #a5b4fc; padding: 6px 0; }
.cp-refresh {
  width: 100%; margin-top: 9px; padding: 5px;
  font-size: 11px; color: #c7d2fe; cursor: pointer;
  background: rgba(99, 102, 241, 0.18);
  border: 1px solid rgba(99, 102, 241, 0.4); border-radius: 7px;
}
.cp-refresh:hover:not(:disabled) { background: rgba(99, 102, 241, 0.3); }
.cp-refresh:disabled { opacity: 0.5; cursor: default; }

.kb-card { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.kb-list { display: flex; flex-direction: column; gap: 6px; overflow-y: auto; }
.kb-item {
  padding: 8px 9px; border: 1px solid #f1f5f9; border-radius: 8px; cursor: pointer;
  transition: all 0.15s;
}
.kb-item:hover { border-color: #bfdbfe; background: #f8fbff; }
.kb-title {
  font-size: 11px; font-weight: 500; color: #334155; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.kb-meta {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 5px; font-size: 10px; color: #94a3b8;
}
.kb-score { font-weight: 600; }
.kb-score.high { color: #059669; }
.kb-score.mid { color: #b45309; }
.kb-score.low { color: #94a3b8; }
.kb-empty { font-size: 11px; color: #94a3b8; text-align: center; padding: 14px 0; }

.tpl-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
.tpl {
  display: flex; flex-direction: column; gap: 2px; align-items: flex-start;
  padding: 7px 8px; background: #f8fafc; border: 1px solid #eef2f7;
  border-radius: 8px; cursor: pointer; text-align: left;
}
.tpl:hover { background: #f1f5f9; border-color: #dbeafe; }
.tpl-title { font-size: 11px; font-weight: 600; color: #334155; }
.tpl-sub { font-size: 9px; color: #94a3b8; }

.dlg-tip { font-size: 12px; color: #64748b; margin: 0 0 10px; }

@media (max-width: 1400px) {
  .td-grid { grid-template-columns: 260px minmax(0, 1fr) 280px; }
}
@media (max-width: 1200px) {
  .td-page { height: auto; }
  .td-grid { grid-template-columns: 1fr; }
  .col-left, .col-right { overflow: visible; }
  .timeline { max-height: 420px; }
}
</style>
