<template>
  <div class="chat-page">
    <!-- 会话侧栏（新建会话 + 会话列表合并为一块） -->
    <div class="session-panel panel-card">
      <div class="session-head">
        <div class="session-title">会话</div>
        <el-button type="primary" class="new-btn" :icon="Plus" size="small" @click="newSession">新建会话</el-button>
      </div>
      <div class="session-list">
        <div v-for="s in sessions" :key="s.session_id" class="session-item"
             :class="{ active: s.session_id === currentSessionId }" @click="loadSession(s)">
          <el-icon class="s-icon"><ChatDotRound /></el-icon>
          <div class="s-main">
            <div class="s-title">{{ s.title }}</div>
            <div class="s-preview">{{ s.preview || '暂无消息' }}</div>
          </div>
          <el-icon class="s-del" @click.stop="removeSession(s)"><Delete /></el-icon>
        </div>
        <div v-if="sessions.length === 0" class="s-empty">暂无历史会话</div>
      </div>
    </div>

    <!-- 聊天主区 -->
    <div class="chat-container panel-card">
      <div class="chat-header">
        <img class="bot-avatar" :src="botAvatar" alt="AI" />
        <div>
          <div class="bot-name">小智 · AI 智能助手</div>
          <div class="bot-desc">已知您的身份与工单，结合知识库为您解答</div>
        </div>
      </div>

      <div class="chat-body" ref="chatBody">
        <div v-for="(m, i) in messages" :key="i" class="chat-row" :class="m.role">
          <img v-if="m.role === 'assistant'" class="bot-avatar small" :src="botAvatar" alt="AI" />
          <div class="bubble-wrap">
            <div class="bubble markdown-body" :class="{ 'is-user': m.role === 'user' }"
                 v-html="m.role === 'assistant' ? renderMd(m.content) : escapeHtml(m.content)"></div>

            <!-- 回答依据：命中的知识库文章，可点击查看详情 -->
            <div v-if="m.references && m.references.length" class="refs">
              <div class="refs-head">
                <el-icon><Link /></el-icon>
                <span>回答依据 · 引用 {{ m.references.length }} 篇知识库文章</span>
              </div>
              <div v-for="r in m.references" :key="r.id" class="ref-item" @click="openRef(r)">
                <span class="ref-rank">{{ r.rank }}</span>
                <div class="ref-main">
                  <div class="ref-title">{{ r.title }}</div>
                  <div class="ref-sub">
                    <span class="ref-cat">{{ r.category_name || '未分类' }}</span>
                    <span v-if="r.score != null" class="ref-score">相关度 {{ Math.round(r.score * 100) }}%</span>
                  </div>
                </div>
                <el-icon class="ref-go"><ArrowRight /></el-icon>
              </div>
            </div>

            <!-- 无可靠依据：明确告知并引导建单 / 转人工 -->
            <div v-else-if="m.notGrounded" class="noground">
              <div class="ng-head"><el-icon><WarningFilled /></el-icon><span>知识库暂无可靠依据</span></div>
              <p class="ng-text">没有检索到与该问题足够匹配的已发布知识，以上回答可能不够准确，建议由人工客服进一步确认。</p>
              <div class="ng-actions">
                <el-button size="small" type="primary" @click="goCreateTicket">创建工单</el-button>
                <el-button size="small" plain @click="goHuman">联系人工客服</el-button>
              </div>
            </div>

            <!-- 本轮实际使用的模型：用于确认云端/Ollama 兜底是否生效 -->
            <div v-if="m.role === 'assistant' && m.model" class="msg-model">
              <el-icon><Cpu /></el-icon><span>{{ m.model }}</span>
            </div>

            <div v-if="m.meta" class="msg-meta">{{ m.meta }}</div>
          </div>
          <div v-if="m.role === 'user'" class="user-avatar" :style="{ background: myColor }">{{ meName.charAt(0) }}</div>
        </div>
        <div v-if="loading && !hasStreaming" class="chat-row assistant">
          <img class="bot-avatar small" :src="botAvatar" alt="AI" />
          <div class="bubble typing">正在思考...</div>
        </div>
      </div>

      <div class="chat-input">
        <div class="quick-qs">
          <span v-for="q in quickQuestions" :key="q" class="quick-q" @click="send(q)">{{ q }}</span>
        </div>
        <div class="input-row">
          <el-input v-model="input" type="textarea" :rows="2" placeholder="请输入您的问题，回车发送..."
                    @keydown.enter.prevent="handleSend" />
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="handleSend">发送</el-button>
        </div>
      </div>
    </div>

    <!-- 回答依据详情弹窗：点击引用卡片打开，正文按 markdown 渲染 -->
    <el-dialog v-model="refVisible" class="ref-detail-dialog" width="min(860px, 84vw)" top="8vh">
      <template #header>
        <div class="rd-head">
          <span>知识库</span>
          <span class="rd-sep">/</span>
          <span class="rd-cur">回答依据详情</span>
        </div>
      </template>
      <div v-loading="refLoading" class="rd-body">
        <template v-if="refDetail">
          <h2 class="rd-title">{{ refDetail.title }}</h2>
          <div class="rd-tags">
            <span class="rd-chip">{{ refDetail.category_name || refDetail.categoryName || '未分类' }}</span>
            <span v-if="refDetail.score != null" class="rd-chip score">
              相关度 {{ Math.round(refDetail.score * 100) }}%
            </span>
            <span v-if="refDetail.viewCount" class="rd-chip plain">{{ refDetail.viewCount }} 次浏览</span>
          </div>
          <div v-if="refDetail.summary" class="rd-summary">{{ refDetail.summary }}</div>
          <div class="markdown-body rd-content" v-html="renderMd(refDetail.body || '暂无正文内容')"></div>
        </template>
      </div>
      <template #footer>
        <div class="rd-foot">
          <el-button size="small" @click="goKnowledge">去知识库查看</el-button>
          <el-button size="small" type="primary" @click="refVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Promotion, Plus, Delete, ChatDotRound, Link, ArrowRight, WarningFilled, Cpu } from '@element-plus/icons-vue'
import botAvatar from '../assets/ai-bot-avatar.png'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import api from '../api'
import { renderMarkdown } from '../utils/markdown'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const meName = auth.displayName || '我'
const myColor = auth.user?.avatarColor || '#6366f1'

const sessions = ref([])
const currentSessionId = ref('')

// 上次打开的会话：按用户区分存在本地，切到别的页面再回来 / 刷新都能接上，退出登录时清空
const LAST_SESSION_PREFIX = 'isd:chat:last-session'

// 当前用户的本地记忆 key（带用户 ID，避免同浏览器切换账号时串会话）
function lastSessionKey() {
  return `${LAST_SESSION_PREFIX}:${auth.user?.id ?? 'anon'}`
}

// 记住当前会话，供下次进入页面恢复
function rememberSession(sid) {
  if (sid) localStorage.setItem(lastSessionKey(), sid)
}

// 清除记住的会话（新建会话、删除会话、退出登录时调用）
function forgetSession() {
  localStorage.removeItem(lastSessionKey())
}
// 欢迎语由后端按用户身份生成（含 AI 名字小智与工单概况），拿不到时用默认文案
const DEFAULT_WELCOME = '您好！我是 AI 智能助手小智 😊\n账号登录、账单支付、产品使用等问题都可以问我，也可以让我帮您提交工单。'
const welcome = ref(DEFAULT_WELCOME)
const messages = ref([
  { role: 'assistant', content: DEFAULT_WELCOME }
])
const input = ref('')
const loading = ref(false)
const streamingText = ref('')
const hasStreaming = ref(false)
const chatBody = ref(null)

const quickQuestions = ['如何重置密码', '如何申请退款', '如何导出报表', '如何联系人工客服']

// 回答依据详情弹窗
const refVisible = ref(false)
const refLoading = ref(false)
const refDetail = ref(null)

// 打开引用文章的详情：先用卡片信息占位，再拉正文
async function openRef(r) {
  refVisible.value = true
  refLoading.value = true
  refDetail.value = { ...r }
  try {
    const data = await api.get(`/knowledge/${r.id}`, { params: { incrView: true } })
    refDetail.value = { ...r, ...data }
  } catch (e) {
    // 拉取失败时保留卡片里的标题与摘要，不阻断阅读
  } finally {
    refLoading.value = false
  }
}

// 跳转知识库并定位到该分类，便于继续查找相关文章
function goKnowledge() {
  refVisible.value = false
  router.push('/knowledge')
}

// 无可靠依据时：带着问题直接去新建工单
function goCreateTicket() {
  router.push({ path: '/tickets', query: { create: '1' } })
}

// 无可靠依据时：展示人工客服联系方式
function goHuman() {
  ElMessageBox.alert(
    '客服热线 400-800-8888（7×24 小时值守），也可发邮件至 support@example.com，1 个工作日内回复。\n如需留痕跟进，建议同时创建一张工单。',
    '联系人工客服',
    { confirmButtonText: '我知道了', type: 'info' }
  )
}

function renderMd(text) {
  return renderMarkdown(text)
}

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
}

async function loadSessions() {
  try {
    sessions.value = await api.get('/ai/sessions', { params: { limit: 50 } }) || []
  } catch (e) { /* 拦截器已提示 */ }
}

// 拉取个性化欢迎语：后端结合当前用户身份与工单概况生成，突出 AI 名字
async function loadGreeting() {
  try {
    const data = await api.get('/ai/greeting')
    if (data && data.text) {
      welcome.value = data.text
      // 仅在尚未开始对话时替换首条欢迎消息
      if (messages.value.length === 1 && messages.value[0].role === 'assistant') {
        messages.value = [{ role: 'assistant', content: data.text }]
      }
    }
  } catch (e) { /* 使用默认欢迎语 */ }
}

function newSession() {
  currentSessionId.value = ''
  // 手动新建会话即放弃上次会话的记忆
  forgetSession()
  messages.value = [
    { role: 'assistant', content: welcome.value }
  ]
  scrollBottom()
}

async function loadSession(s) {
  if (s.session_id === currentSessionId.value) return
  try {
    const data = await api.get(`/ai/sessions/${s.session_id}`)
    currentSessionId.value = s.session_id
    rememberSession(s.session_id)
    // 历史消息带回当轮的引用与可信标记，重新打开会话时「回答依据」不会消失
    const history = (data.messages || []).map(m => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content,
      references: Array.isArray(m.references) ? m.references : [],
      notGrounded: m.grounded === false,
      model: m.model || ''
    }))
    messages.value = history.length
      ? history
      : [{ role: 'assistant', content: welcome.value }]
    scrollBottom()
  } catch (e) { /* 拦截器已提示 */ }
}

function removeSession(s) {
  ElMessageBox.confirm(`确定删除会话「${s.title}」吗？删除后不可恢复。`, '删除会话', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await api.delete(`/ai/sessions/${s.session_id}`)
    ElMessage.success('会话已删除')
    if (s.session_id === currentSessionId.value) newSession()
    loadSessions()
  }).catch(() => {})
}

async function handleSend() {
  const q = input.value.trim()
  if (!q || loading.value) return
  input.value = ''
  await send(q)
}

async function send(q) {
  messages.value.push({ role: 'user', content: q })
  loading.value = true
  streamingText.value = ''
  hasStreaming.value = false
  await scrollBottom()

  const sendStart = Date.now()
  let firstTokenAt = null

  try {
    const payload = { question: q }
    if (currentSessionId.value) payload.session_id = currentSessionId.value

    const token = localStorage.getItem('token')
    const resp = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(payload)
    })

    if (!resp.ok || !resp.body) {
      throw new Error('流式请求失败')
    }

    // 在消息列表末尾预留一个 assistant 消息占位
    const assistantMsg = { role: 'assistant', content: '', meta: '' }
    messages.value.push(assistantMsg)

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let doneMeta = null

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 按 SSE 事件块切分（每个事件以空行分隔）
      let idx
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        // 解析 event: 和 data: 行（Java SseEmitter 格式）
        let eventName = ''
        let dataStr = ''
        for (const l of rawEvent.split('\n')) {
          if (l.startsWith('event:')) eventName = l.slice(6).trim()
          else if (l.startsWith('data:')) dataStr = (dataStr ? dataStr + '\n' : '') + l.slice(5).trim()
        }
        if (!eventName && !dataStr) continue

        if (eventName === 'delta') {
          if (firstTokenAt === null) firstTokenAt = Date.now()
          assistantMsg.content += dataStr
          streamingText.value = assistantMsg.content
          hasStreaming.value = true
          scrollBottom()
        } else if (eventName === 'done') {
          try { doneMeta = JSON.parse(dataStr) } catch (e) { doneMeta = {} }
        } else if (eventName === 'error') {
          assistantMsg.content += dataStr || '服务异常'
        }
      }
    }

    // 生成耗时信息
    const doneAt = Date.now()
    const thinkMs = firstTokenAt !== null ? (firstTokenAt - sendStart) : (doneAt - sendStart)
    const answerMs = doneAt - (firstTokenAt !== null ? firstTokenAt : sendStart)
    assistantMsg.meta = `思考 ${(thinkMs / 1000).toFixed(1)}s · 回答 ${(answerMs / 1000).toFixed(1)}s` +
      (doneMeta && doneMeta.duration_ms ? ` · 服务端 ${(doneMeta.duration_ms / 1000).toFixed(1)}s` : '')
    streamingText.value = ''

    // 回答依据：命中知识文章则展示引用卡片；没命中则提示无可靠依据
    if (doneMeta) {
      assistantMsg.references = Array.isArray(doneMeta.references) ? doneMeta.references : []
      assistantMsg.notGrounded = doneMeta.grounded === false
      // 实际使用的模型（云端 / Ollama 兜底 / 本地知识库检索）
      assistantMsg.model = doneMeta.model || ''
    }

    if (doneMeta && doneMeta.session_id) {
      currentSessionId.value = doneMeta.session_id
      rememberSession(doneMeta.session_id)
    }
    loadSessions()
  } catch (e) {
    // 流式失败，回退到普通接口
    await fallbackSend(q)
  } finally {
    loading.value = false
    hasStreaming.value = false
    await scrollBottom()
  }
}

async function fallbackSend(q) {
  try {
    const payload = { question: q }
    if (currentSessionId.value) payload.session_id = currentSessionId.value
    const data = await api.post('/ai/chat', payload)
    messages.value.push({
      role: 'assistant',
      content: data.answer || '未获得有效回答',
      references: Array.isArray(data.references) ? data.references : [],
      notGrounded: data.grounded === false,
      model: data.model || ''
    })
    if (data.session_id) {
      currentSessionId.value = data.session_id
      rememberSession(data.session_id)
    }
    loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，服务暂时不可用，请稍后重试。' })
  }
}

async function scrollBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

onMounted(async () => {
  scrollBottom()
  await Promise.all([loadSessions(), loadGreeting()])
  // 首页 Hero 搜索 / 常见问题点击会带上 ?q= 关键字，进入后自动提问
  const preset = route.query.q
  if (preset && String(preset).trim()) {
    router.replace({ path: '/chat' })
    send(String(preset).trim())
    return
  }
  // 恢复上次会话：从工单等页面切回来、或刷新页面都接着刚才那段对话
  const lastSid = localStorage.getItem(lastSessionKey())
  if (!lastSid) return
  const hit = sessions.value.find(s => s.session_id === lastSid)
  if (hit) {
    await loadSession(hit)
  } else {
    // 会话已被删除或过期，清掉本地记忆以免每次进来都找一遍
    forgetSession()
  }
})
</script>

<style scoped>
.chat-page { height: 100%; display: flex; gap: 16px; }

/* 会话侧栏：新建会话 + 会话列表合并为一块 */
.session-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--border);
}
.session-title { font-size: 15px; font-weight: 700; }
.new-btn { flex-shrink: 0; }
.session-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; padding: 10px; }
.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.session-item:hover { background: #f3f4f6; }
.session-item.active { background: #eef2ff; }
.s-icon { color: var(--primary); flex-shrink: 0; }
.s-main { flex: 1; min-width: 0; }
.s-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.s-preview {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
.s-del { color: #c0c4cc; flex-shrink: 0; }
.s-del:hover { color: #ef4444; }
.s-empty { text-align: center; color: var(--text-muted); font-size: 12px; padding: 24px 0; }

/* 聊天区：占满下方区域，无留白 */
.chat-container { flex: 1; display: flex; flex-direction: column; min-width: 0; overflow: hidden; }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.bot-avatar { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
.bot-avatar.small { width: 32px; height: 32px; flex-shrink: 0; }
.bot-name { font-weight: 600; font-size: 15px; }
.bot-desc { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.chat-body { flex: 1; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }
.chat-row { display: flex; align-items: flex-start; gap: 10px; }
.chat-row.user { justify-content: flex-end; }
.user-avatar { width: 32px; height: 32px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 13px; flex-shrink: 0; }
.bubble-wrap { max-width: 70%; display: flex; flex-direction: column; }
.bubble { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; word-break: break-word; background: #f3f4f6; }
.chat-row.user .bubble { background: #6366f1; color: #fff; }
.chat-row.assistant .bubble { border-top-left-radius: 2px; }
.chat-row.user .bubble { border-top-right-radius: 2px; }
.bubble.typing { color: var(--text-muted); font-style: italic; }
.streaming { white-space: pre-wrap; }

.msg-meta { font-size: 11px; color: var(--text-muted); margin-top: 4px; padding-left: 4px; }

/* 实际使用的模型标签：贴在气泡下方，便于确认兜底是否生效 */
.msg-model {
  display: flex; align-items: center; gap: 4px;
  font-size: 11px; color: #94a3b8; margin-top: 4px; padding-left: 4px;
}

/* 回答依据：知识库引用卡片 */
.refs { margin-top: 8px; background: #f8fafc; border: 1px solid var(--border); border-radius: 10px; padding: 8px 10px; }
.refs-head { display: flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; }
.ref-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.ref-item:hover { background: #eef2ff; }
.ref-rank {
  width: 18px; height: 18px; flex-shrink: 0; border-radius: 50%;
  background: #e0e7ff; color: #4338ca; font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.ref-main { flex: 1; min-width: 0; }
.ref-title { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ref-sub { display: flex; align-items: center; gap: 8px; margin-top: 2px; font-size: 11px; color: var(--text-muted); }
.ref-cat { background: #eef2ff; color: var(--primary); padding: 0 6px; border-radius: 8px; }
.ref-score { color: #047857; }
.ref-go { color: #c0c4cc; flex-shrink: 0; }

/* 无可靠依据提示 */
.noground { margin-top: 8px; background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px; padding: 10px 12px; }
.ng-head { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #b45309; }
.ng-text { margin: 6px 0 10px; font-size: 12px; line-height: 1.6; color: #92400e; }
.ng-actions { display: flex; gap: 8px; }

/* 引用详情弹窗内容 */
.rd-head { font-size: 14px; font-weight: 600; }
.rd-sep { margin: 0 6px; color: #c0c4cc; }
.rd-cur { color: var(--primary); }
.rd-body { min-height: 140px; }
.rd-title { font-size: 20px; font-weight: 700; margin: 0 0 10px; line-height: 1.4; }
.rd-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.rd-chip { font-size: 12px; padding: 2px 10px; border-radius: 10px; background: #eef2ff; color: var(--primary); }
.rd-chip.score { background: #ecfdf5; color: #047857; }
.rd-chip.plain { background: #f3f4f6; color: var(--text-muted); }
.rd-summary {
  background: #f8fafc; border-left: 3px solid var(--primary); padding: 10px 12px;
  border-radius: 6px; font-size: 13px; line-height: 1.7; color: #334155; margin-bottom: 14px;
}
.rd-content { font-size: 14px; line-height: 1.8; }
.rd-foot { display: flex; justify-content: flex-end; gap: 8px; }

.chat-input { padding: 14px 20px; border-top: 1px solid var(--border); flex-shrink: 0; }
.quick-qs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.quick-q { font-size: 12px; color: var(--primary); background: #eef2ff; padding: 4px 12px; border-radius: 14px; cursor: pointer; }
.quick-q:hover { background: #e0e7ff; }
.input-row { display: flex; gap: 10px; align-items: flex-end; }

/* markdown 内容样式（作用于 AI 回复气泡内部） */
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3), .markdown-body :deep(h4) { margin: 12px 0 8px; font-weight: 700; line-height: 1.4; }
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) { background: #f0f0f0; padding: 1px 5px; border-radius: 4px; font-family: monospace; font-size: 13px; }
.markdown-body :deep(pre) { background: #f6f8fa; padding: 12px; border-radius: 8px; overflow-x: auto; margin: 0 0 8px; }
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(blockquote) { border-left: 3px solid #d0d7de; margin: 0 0 8px; padding-left: 12px; color: #57606a; }
.markdown-body :deep(table) { border-collapse: collapse; margin: 0 0 8px; width: 100%; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #d0d7de; padding: 6px 10px; text-align: left; }
.markdown-body :deep(th) { background: #f6f8fa; font-weight: 600; }
.markdown-body :deep(a) { color: #2563eb; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid #d0d7de; margin: 12px 0; }
.markdown-body :deep(strong) { font-weight: 700; }

@media (max-width: 900px) {
  .session-panel { display: none; }
}
</style>

<style>
/* 引用详情弹窗：宽度自适应，正文区可滚动（弹窗内部类名不受 scoped 限制） */
.ref-detail-dialog { padding: 0; border-radius: 14px; overflow: hidden; }
.ref-detail-dialog .el-dialog__header { margin: 0; padding: 16px 22px 12px; border-bottom: 1px solid #eef2f7; }
.ref-detail-dialog .el-dialog__body { padding: 16px 22px; max-height: 66vh; overflow-y: auto; }
.ref-detail-dialog .el-dialog__footer { padding: 12px 22px 16px; border-top: 1px solid #eef2f7; }
</style>
