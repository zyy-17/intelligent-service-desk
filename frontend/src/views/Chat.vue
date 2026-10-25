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
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Promotion, Plus, Delete, ChatDotRound } from '@element-plus/icons-vue'
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
    const history = (data.messages || []).map(m => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content
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

    if (doneMeta && doneMeta.session_id) {
      currentSessionId.value = doneMeta.session_id
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
    messages.value.push({ role: 'assistant', content: data.answer || '未获得有效回答' })
    if (data.session_id) currentSessionId.value = data.session_id
    loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，服务暂时不可用，请稍后重试。' })
  }
}

async function scrollBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

onMounted(() => {
  scrollBottom()
  loadSessions()
  loadGreeting()
  // 首页 Hero 搜索 / 常见问题点击会带上 ?q= 关键字，进入后自动提问
  const preset = route.query.q
  if (preset && String(preset).trim()) {
    router.replace({ path: '/chat' })
    send(String(preset).trim())
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
