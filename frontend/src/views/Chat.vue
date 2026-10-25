<template>
  <div class="chat-page">
    <!-- 会话侧栏 -->
    <div class="session-panel panel-card">
      <el-button type="primary" class="new-btn" :icon="Plus" @click="newSession">新建会话</el-button>
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
          <div class="bot-name">智能客服助手</div>
          <div class="bot-desc">基于知识库的智能问答，随时为您服务</div>
        </div>
      </div>

      <div class="chat-body" ref="chatBody">
        <div v-for="(m, i) in messages" :key="i" class="chat-row" :class="m.role">
          <img v-if="m.role === 'assistant'" class="bot-avatar small" :src="botAvatar" alt="AI" />
          <div class="bubble">{{ m.content }}</div>
          <div v-if="m.role === 'user'" class="user-avatar" :style="{ background: myColor }">{{ meName.charAt(0) }}</div>
        </div>
        <div v-if="loading" class="chat-row assistant">
          <img class="bot-avatar small" :src="botAvatar" alt="AI" />
          <div class="bubble typing">正在思考...</div>
        </div>
      </div>

      <div class="chat-input">
        <div class="quick-qs">
          <span v-for="q in quickQuestions" :key="q" class="quick-q" @click="send(q)">{{ q }}</span>
        </div>
        <div class="input-row">
          <el-input v-model="input" type="textarea" :rows="2" placeholder="请输入您的问题，回车发送..." @keydown.enter.prevent="handleSend" />
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="handleSend">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Promotion, Plus, Delete, ChatDotRound } from '@element-plus/icons-vue'
import botAvatar from '../assets/ai-bot-avatar.png'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
const meName = auth.displayName || '我'
const myColor = auth.user?.avatarColor || '#6366f1'

const sessions = ref([])
const currentSessionId = ref('')
const messages = ref([
  { role: 'assistant', content: '您好！我是智能客服助手，可以为您解答平台使用、账号、账单等常见问题。请输入您的问题。' }
])
const input = ref('')
const loading = ref(false)
const chatBody = ref(null)

const quickQuestions = ['如何重置密码', '如何申请退款', '如何导出报表', '如何联系人工客服']

async function loadSessions() {
  try {
    sessions.value = await api.get('/ai/sessions', { params: { limit: 50 } }) || []
  } catch (e) { /* 拦截器已提示 */ }
}

function newSession() {
  currentSessionId.value = ''
  messages.value = [
    { role: 'assistant', content: '您好！我是智能客服助手，可以为您解答平台使用、账号、账单等常见问题。请输入您的问题。' }
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
      : [{ role: 'assistant', content: '您好！我是智能客服助手，请问有什么可以帮您？' }]
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
  await scrollBottom()
  try {
    const payload = { question: q }
    if (currentSessionId.value) payload.session_id = currentSessionId.value
    const data = await api.post('/ai/chat', payload)
    messages.value.push({ role: 'assistant', content: data.answer || '未获得有效回答' })
    // 首条消息后刷新会话列表（后端已自动创建会话并记录）
    if (!currentSessionId.value && data.session_id) {
      currentSessionId.value = data.session_id
    }
    loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '抱歉，服务暂时不可用，请稍后重试。' })
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

async function scrollBottom() {
  await nextTick()
  if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
}

onMounted(() => {
  scrollBottom()
  loadSessions()
})
</script>

<style scoped>
.chat-page { height: 100%; display: flex; gap: 16px; }

/* 会话侧栏 */
.session-panel {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 14px 10px;
  overflow: hidden;
}
.new-btn { width: 100%; margin-bottom: 12px; }
.session-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
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

/* 聊天区 */
.chat-container { flex: 1; display: flex; flex-direction: column; max-width: 860px; margin: 0 auto; width: 100%; overflow: hidden; }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid var(--border); }
.bot-avatar { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
.bot-avatar.small { width: 32px; height: 32px; flex-shrink: 0; }
.bot-name { font-weight: 600; font-size: 15px; }
.bot-desc { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.chat-body { flex: 1; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }
.chat-row { display: flex; align-items: flex-start; gap: 10px; }
.chat-row.user { justify-content: flex-end; }
.user-avatar { width: 32px; height: 32px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 13px; flex-shrink: 0; }
.bubble { max-width: 70%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.chat-row.assistant .bubble { background: #f3f4f6; border-top-left-radius: 2px; }
.chat-row.user .bubble { background: #6366f1; color: #fff; border-top-right-radius: 2px; }
.bubble.typing { color: var(--text-muted); font-style: italic; }

.chat-input { padding: 14px 20px; border-top: 1px solid var(--border); }
.quick-qs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.quick-q { font-size: 12px; color: var(--primary); background: #eef2ff; padding: 4px 12px; border-radius: 14px; cursor: pointer; }
.quick-q:hover { background: #e0e7ff; }
.input-row { display: flex; gap: 10px; align-items: flex-end; }

@media (max-width: 900px) {
  .session-panel { display: none; }
}
</style>
