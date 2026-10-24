<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-circle c1"></div>
      <div class="bg-circle c2"></div>
      <div class="bg-circle c3"></div>
    </div>

    <div class="login-card">
      <div class="login-logo">
        <div class="logo-icon">ISD</div>
      </div>
      <h1 class="login-title">智能客服与工单管理平台</h1>
      <p class="login-sub">Intelligent Service Desk</p>

      <el-form :model="form" @submit.prevent="handleLogin" class="login-form">
        <el-form-item>
          <el-input v-model="form.username" placeholder="账号" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <div class="demo-tips">
        <div class="tips-title">演示账号（密码均为 123456）</div>
        <div class="tip-row" @click="fill('admin')">系统管理员：admin</div>
        <div class="tip-row" @click="fill('supervisor')">客服主管：supervisor</div>
        <div class="tip-row" @click="fill('agent1')">客服人员：agent1</div>
        <div class="tip-row" @click="fill('customer1')">客户：customer1</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: '123456' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

function fill(username) {
  form.username = username
  form.password = '123456'
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e1f2b 0%, #2d2f45 100%);
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
}
.bg-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}
.c1 { width: 400px; height: 400px; background: #6366f1; top: -100px; left: -100px; }
.c2 { width: 300px; height: 300px; background: #8b5cf6; bottom: -80px; right: 10%; }
.c3 { width: 250px; height: 250px; background: #22c55e; top: 20%; right: -80px; opacity: 0.25; }

.login-card {
  position: relative;
  z-index: 2;
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}
.logo-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 1px;
}
.login-title {
  text-align: center;
  font-size: 20px;
  margin: 0 0 4px;
  color: var(--text-main);
}
.login-sub {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  margin: 0 0 28px;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
.demo-tips {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px dashed var(--border);
}
.tips-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}
.tip-row {
  font-size: 13px;
  color: var(--primary);
  padding: 4px 0;
  cursor: pointer;
}
.tip-row:hover {
  text-decoration: underline;
}
</style>
