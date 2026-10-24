<template>
  <div class="chat-page">
    <div class="chat-container panel-card">
      <div class="chat-header">
        <div class="bot-avatar">AI</div>
        <div>
          <div class="bot-name">智能客服助手</div>
          <div class="bot-desc">基于知识库的智能问答，随时为您服务</div>
        </div>
      </div>

      <div class="chat-body" ref="chatBody">
        <div v-for="(m, i) in messages" :key="i" class="chat-row" :class="m.role">
          <div v-if="m.role === 'assistant'" class="bot-avatar small">AI</div>
          <div class="bubble">{{ m.content }}</div>
          <div v-if="m.role === 'user'" class="user-avatar">{{ meName.charAt(0) }}</div>
        </div>
        <div v-if="loading" class="chat-row assistant">
          <div class="bot-avatar small">AI</div>
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
import { Promotion } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
const meName = auth.displayName || '我'
const messages = ref([
  { role: 'assistant', content: '您好！我是智能客服助手，可以为您解答平台使用、账号、账单等常见问题。请输入您的问题。' }
])
const input = ref('')
const loading = ref(false)
const chatBody = ref(null)

const quickQuestions = ['如何重置密码', '如何申请退款', '如何导出报表', '如何联系人工客服']

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
    const data = await api.post('/ai/chat', { question: q })
    messages.value.push({ role: 'assistant', content: data.answer || '未获得有效回答' })
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

onMounted(scrollBottom)
</script>

<style scoped>
.chat-page { height: 100%; display: flex; }
.chat-container { flex: 1; display: flex; flex-direction: column; max-width: 860px; margin: 0 auto; width: 100%; overflow: hidden; }
.chat-header { display: flex; align-items: center; gap: 12px; padding: 18px 20px; border-bottom: 1px solid var(--border); }
.bot-avatar { width: 42px; height: 42px; border-radius: 50%; background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.bot-avatar.small { width: 32px; height: 32px; font-size: 12px; flex-shrink: 0; }
.bot-name { font-weight: 600; font-size: 15px; }
.bot-desc { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.chat-body { flex: 1; padding: 20px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }
.chat-row { display: flex; align-items: flex-start; gap: 10px; }
.chat-row.user { justify-content: flex-end; }
.user-avatar { width: 32px; height: 32px; border-radius: 50%; background: #22c55e; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 13px; flex-shrink: 0; }
.bubble { max-width: 70%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.chat-row.assistant .bubble { background: #f3f4f6; border-top-left-radius: 2px; }
.chat-row.user .bubble { background: #6366f1; color: #fff; border-top-right-radius: 2px; }
.bubble.typing { color: var(--text-muted); font-style: italic; }

.chat-input { padding: 14px 20px; border-top: 1px solid var(--border); }
.quick-qs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.quick-q { font-size: 12px; color: var(--primary); background: #eef2ff; padding: 4px 12px; border-radius: 14px; cursor: pointer; }
.quick-q:hover { background: #e0e7ff; }
.input-row { display: flex; gap: 10px; align-items: flex-end; }
</style>
