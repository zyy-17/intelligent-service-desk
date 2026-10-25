<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-blob b1"></div>
      <div class="bg-blob b2"></div>
    </div>

    <div class="login-wrap">
      <!-- 左侧品牌区 -->
      <div class="brand-side">
        <div class="brand-logo">
          <div class="brand-icon">ISD</div>
          <div>
            <div class="brand-name">Intelligent-Service-Desk</div>
            <div class="brand-sub">智能客户服务与工单管理平台</div>
          </div>
        </div>

        <h1 class="brand-slogan">让客户服务更智能</h1>
        <p class="brand-desc">
          基于 AI 的智能客户服务与工单管理平台，<br />
          为企业提供高效、专业、智能的服务解决方案。
        </p>

        <div class="feature-grid">
          <div class="feature-card" v-for="f in features" :key="f.title">
            <div class="feature-icon" :style="{ background: f.bg, color: f.color }">
              <el-icon :size="20"><component :is="f.icon" /></el-icon>
            </div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-text">{{ f.text }}</div>
          </div>
        </div>

        <div class="brand-footer">© 2026 Intelligent-Service-Desk · 让服务更简单 · 让客户更满意</div>
      </div>

      <!-- 右侧登录/注册卡片 -->
      <div class="auth-card">
        <div class="auth-tabs">
          <div class="auth-tab" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</div>
          <div class="auth-tab" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</div>
          <div class="tab-indicator" :class="{ right: mode === 'register' }"></div>
        </div>

        <!-- 登录 -->
        <template v-if="mode === 'login'">
          <h2 class="auth-title">欢迎回来</h2>
          <p class="auth-sub">请登录您的账号，开始使用智能客户服务平台</p>

          <el-form :model="loginForm" class="auth-form">
            <el-form-item>
              <el-input v-model="loginForm.username" placeholder="请输入用户名 / 邮箱 / 手机号" size="large"
                        :prefix-icon="User" @keyup.enter="handleLogin" />
            </el-form-item>
            <el-form-item>
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large"
                        :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
            </el-form-item>
            <div class="form-row">
              <el-checkbox v-model="remember">记住账号</el-checkbox>
              <span class="forgot">忘记密码?</span>
            </div>
            <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleLogin">
              登 录
            </el-button>
          </el-form>

          <div class="auth-switch">还没有账号? <span class="link" @click="mode = 'register'">立即注册</span></div>

          <div class="demo-tips">
            <div class="tips-title">演示账号（密码均为 123456）</div>
            <div class="tip-row" @click="fill('admin')">系统管理员：admin</div>
            <div class="tip-row" @click="fill('supervisor')">客服主管：supervisor</div>
            <div class="tip-row" @click="fill('agent1')">客服人员：agent1</div>
            <div class="tip-row" @click="fill('customer1')">客户：customer1</div>
          </div>
        </template>

        <!-- 注册 -->
        <template v-else>
          <h2 class="auth-title">创建账号</h2>
          <p class="auth-sub">填写以下信息，快速注册您的账号</p>

          <el-form :model="regForm" class="auth-form" ref="regFormRef" :rules="regRules">
            <el-form-item prop="username">
              <el-input v-model="regForm.username" placeholder="请输入账号（至少 3 位）" size="large" :prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="displayName">
              <el-input v-model="regForm.displayName" placeholder="请输入昵称 / 姓名" size="large" :prefix-icon="Postcard" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="regForm.password" type="password" placeholder="请输入密码（至少 6 位）" size="large"
                        :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="confirm">
              <el-input v-model="regForm.confirm" type="password" placeholder="请再次输入密码" size="large"
                        :prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
            </el-form-item>
            <el-form-item prop="role">
              <el-select v-model="regForm.role" size="large" placeholder="请选择您的身份" class="role-select">
                <template #prefix><el-icon><Avatar /></el-icon></template>
                <el-option label="客户" value="CUSTOMER" />
                <el-option label="客服人员" value="AGENT" />
                <el-option label="客服主管" value="SUPERVISOR" />
              </el-select>
            </el-form-item>
            <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleRegister">
              注 册
            </el-button>
          </el-form>

          <div class="auth-switch">已有账号? <span class="link" @click="mode = 'login'">立即登录</span></div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Postcard, Avatar, ChatDotRound, Tickets, Collection, DataAnalysis } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()

const mode = ref('login')
const loading = ref(false)

const loginForm = reactive({ username: '', password: '' })
const remember = ref(true)

const regFormRef = ref(null)
const regForm = reactive({ username: '', displayName: '', password: '', confirm: '', role: 'CUSTOMER' })
const regRules = {
  username: [{ required: true, min: 3, message: '账号至少 3 位', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirm: [{
    validator: (rule, value, cb) => value === regForm.password ? cb() : cb(new Error('两次输入的密码不一致')),
    trigger: 'blur'
  }],
  role: [{ required: true, message: '请选择身份', trigger: 'change' }]
}

const features = [
  { title: 'AI 智能助手', text: '智能问答·精准解答', icon: ChatDotRound, bg: '#eef2ff', color: '#6366f1' },
  { title: '工单管理', text: '全流程跟踪·高效处理', icon: Tickets, bg: '#eff6ff', color: '#3b82f6' },
  { title: '知识库', text: '沉淀经验·快速响应', icon: Collection, bg: '#f0fdf4', color: '#22c55e' },
  { title: '数据统计', text: '多维分析·运营优化', icon: DataAnalysis, bg: '#fff7ed', color: '#f59e0b' }
]

onMounted(() => {
  const saved = localStorage.getItem('rememberUsername')
  if (saved) {
    loginForm.username = saved
  } else {
    loginForm.username = 'admin'
    loginForm.password = '123456'
  }
})

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    if (remember.value) {
      localStorage.setItem('rememberUsername', loginForm.username)
    } else {
      localStorage.removeItem('rememberUsername')
    }
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  try {
    await regFormRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: regForm.username,
      password: regForm.password,
      displayName: regForm.displayName,
      role: regForm.role
    })
    ElMessage.success('注册成功，已自动登录')
    router.push('/dashboard')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function fill(username) {
  mode.value = 'login'
  loginForm.username = username
  loginForm.password = '123456'
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef4ff 0%, #f5f8ff 45%, #e8f1ff 100%);
  overflow: hidden;
}
.bg-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}
.b1 { width: 480px; height: 480px; background: #c3d8ff; top: -160px; left: -120px; }
.b2 { width: 400px; height: 400px; background: #d9e8ff; bottom: -140px; right: -100px; }

.login-wrap {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: stretch;
  gap: 60px;
  width: min(1080px, 94vw);
}

/* 左侧品牌 */
.brand-side {
  flex: 1.2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #1f2937;
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
}
.brand-icon {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-name { font-size: 18px; font-weight: 700; }
.brand-sub { font-size: 12px; color: #64748b; margin-top: 2px; }

.brand-slogan {
  font-size: 34px;
  font-weight: 800;
  margin: 0 0 14px;
  color: #111827;
  letter-spacing: 1px;
}
.brand-desc {
  font-size: 14px;
  color: #4b5563;
  line-height: 1.9;
  margin: 0 0 34px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.feature-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 14px;
  padding: 16px 12px;
  box-shadow: 0 4px 18px rgba(59, 130, 246, 0.08);
}
.feature-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}
.feature-title { font-size: 13px; font-weight: 700; }
.feature-text { font-size: 11px; color: #6b7280; margin-top: 4px; }

.brand-footer {
  margin-top: 36px;
  font-size: 12px;
  color: #94a3b8;
}

/* 右侧卡片 */
.auth-card {
  flex: 0 0 420px;
  background: #fff;
  border-radius: 18px;
  padding: 32px 36px 24px;
  box-shadow: 0 20px 60px rgba(37, 99, 235, 0.12);
  align-self: center;
  max-height: 92vh;
  overflow-y: auto;
}

.auth-tabs {
  position: relative;
  display: flex;
  border-bottom: 1px solid #eef0f4;
  margin-bottom: 22px;
}
.auth-tab {
  flex: 1;
  text-align: center;
  font-size: 15px;
  padding: 10px 0 14px;
  color: #9ca3af;
  cursor: pointer;
  transition: color 0.2s;
}
.auth-tab.active { color: #2563eb; font-weight: 700; }
.tab-indicator {
  position: absolute;
  bottom: -1px;
  left: 25%;
  width: 50%;
  transform: translateX(-50%);
  height: 3px;
  border-radius: 3px;
  background: #2563eb;
  transition: left 0.25s;
}
.tab-indicator.right { left: 75%; }

.auth-title { font-size: 20px; margin: 0 0 6px; color: #111827; }
.auth-sub { font-size: 13px; color: #9ca3af; margin: 0 0 20px; }

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.forgot { font-size: 13px; color: #9ca3af; cursor: pointer; }
.forgot:hover { color: #2563eb; }

.auth-btn { width: 100%; letter-spacing: 4px; }

.auth-switch {
  text-align: center;
  font-size: 13px;
  color: #6b7280;
  margin-top: 16px;
}
.auth-switch .link { color: #2563eb; cursor: pointer; font-weight: 600; }

.demo-tips {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e5e7eb;
}
.tips-title { font-size: 12px; color: #9ca3af; margin-bottom: 6px; }
.tip-row {
  font-size: 13px;
  color: #2563eb;
  padding: 3px 0;
  cursor: pointer;
}
.tip-row:hover { text-decoration: underline; }

.role-select { width: 100%; }

@media (max-width: 900px) {
  .brand-side { display: none; }
  .auth-card { flex: 1; max-width: 420px; }
}
</style>
