<template>
  <div class="profile">
    <!-- 左侧：头像与身份 -->
    <div class="panel-card profile-card">
      <div class="avatar-wrap">
        <img v-if="avatarUrl" :src="avatarUrl" class="big-avatar" alt="avatar" />
        <div v-else class="big-avatar fallback" :style="{ background: form.avatarColor || '#6366f1' }">
          {{ (form.displayName || '?').charAt(0) }}
        </div>
        <el-upload
          class="avatar-upload"
          :show-file-list="false"
          :http-request="doUpload"
          accept="image/*"
          :before-upload="beforeUpload"
        >
          <el-button size="small" :icon="Camera">更换头像</el-button>
        </el-upload>
      </div>
      <div class="identity">
        <div class="display-name">{{ form.displayName }}</div>
        <div class="username">@{{ form.username }}</div>
        <el-tag :type="roleType" size="small" effect="light">{{ roleLabel }}</el-tag>
      </div>
      <div class="identity-desc">{{ roleDesc }}</div>
    </div>

    <!-- 右侧：资料编辑 -->
    <div class="panel-card form-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本资料" name="basic">
          <el-form :model="form" label-width="90px" class="edit-form">
            <el-form-item label="账号">
              <el-input v-model="form.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="form.displayName" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="公司" v-if="showCompany">
              <el-input v-model="form.company" disabled />
            </el-form-item>
            <el-form-item label="部门" v-if="showCompany">
              <el-input v-model="form.department" disabled />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveBasic">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="90px" class="edit-form">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="pwd.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changing" @click="savePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
const activeTab = ref('basic')
const saving = ref(false)
const changing = ref(false)
const pwdRef = ref(null)

const form = reactive({
  username: '', displayName: '', phone: '', email: '', company: '', department: '', avatarColor: ''
})
const avatarUrl = ref('')

const pwd = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (r, v, cb) => v === pwd.newPassword ? cb() : cb(new Error('两次密码不一致')),
      trigger: 'blur'
    }
  ]
}

const roleMap = { ADMIN: '系统管理员', SUPERVISOR: '客服主管', AGENT: '客服人员', CUSTOMER: '客户' }
const roleTypeMap = { ADMIN: 'danger', SUPERVISOR: 'warning', AGENT: 'primary', CUSTOMER: 'info' }
const roleLabel = computed(() => roleMap[auth.role] || auth.role)
const roleType = computed(() => roleTypeMap[auth.role] || 'info')
const roleDesc = computed(() => ({
  ADMIN: '系统管理员，拥有全部权限',
  SUPERVISOR: '客服主管，负责工单分配与团队监控',
  AGENT: '客服人员，负责工单受理与处理',
  CUSTOMER: '客户，可提交工单与自助服务'
}[auth.role] || ''))

const showCompany = computed(() => ['ADMIN', 'SUPERVISOR', 'AGENT'].includes(auth.role))

async function load() {
  const data = await api.get('/auth/profile')
  Object.assign(form, data)
  avatarUrl.value = data.avatarUrl || ''
}

async function saveBasic() {
  saving.value = true
  try {
    const data = await api.put('/auth/profile', {
      displayName: form.displayName, email: form.email, phone: form.phone
    })
    Object.assign(form, data)
    avatarUrl.value = data.avatarUrl || ''
    // 同步刷新本地缓存的用户信息
    auth.setUser(data)
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

function beforeUpload(file) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 5MB')
    return false
  }
  return true
}

async function doUpload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  const data = await api.post('/auth/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  Object.assign(form, data)
  avatarUrl.value = data.avatarUrl || ''
  auth.setUser(data)
  ElMessage.success('头像已更新')
}

async function savePwd() {
  await pwdRef.value.validate()
  changing.value = true
  try {
    await api.put('/auth/password', {
      oldPassword: pwd.oldPassword, newPassword: pwd.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    pwd.oldPassword = pwd.newPassword = pwd.confirmPassword = ''
  } finally {
    changing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.profile {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
}
.panel-card { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.profile-card { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 12px; height: fit-content; }
.avatar-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.big-avatar { width: 96px; height: 96px; border-radius: 50%; object-fit: cover; }
.big-avatar.fallback { color: #fff; display: flex; align-items: center; justify-content: center; font-size: 36px; font-weight: 700; }
.identity .display-name { font-size: 18px; font-weight: 700; }
.identity .username { color: var(--text-muted); font-size: 13px; margin: 4px 0 8px; }
.identity-desc { color: var(--text-sub); font-size: 12px; }
.form-card .edit-form { max-width: 480px; margin-top: 12px; }
@media (max-width: 800px) {
  .profile { grid-template-columns: 1fr; }
}
</style>
