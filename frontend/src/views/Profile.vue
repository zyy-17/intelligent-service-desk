<template>
  <div class="profile-page">
    <div class="page-head">
      <h2>个人中心</h2>
      <p>管理您的账户信息与安全设置</p>
    </div>

    <!-- ① 资料卡：头像、昵称、账户状态、注册时间与四项统计 -->
    <section class="panel-card hero-card">
      <div class="hero-main">
        <div class="hero-avatar-wrap">
          <img v-if="avatarUrl" :src="avatarUrl" class="hero-avatar" alt="avatar" />
          <div v-else class="hero-avatar fallback" :style="{ background: form.avatarColor || '#6366f1' }">
            {{ initial }}
          </div>
        </div>

        <div class="hero-id">
          <div class="hero-name">{{ form.displayName || form.username }}</div>
          <div class="hero-username">@{{ form.username }}</div>
          <div class="hero-meta">
            <span class="dot" :class="{ off: !statusNormal }"></span>
            <span>账户状态</span>
            <b :class="statusNormal ? 'ok' : 'bad'">{{ statusNormal ? '正常' : '已禁用' }}</b>
            <span class="sep"></span>
            <el-icon><Calendar /></el-icon>
            <span>注册时间</span>
            <b>{{ fmtDate(form.createdAt) }}</b>
          </div>
        </div>

        <el-button class="hero-edit" :icon="EditPen" @click="focusEdit">编辑资料</el-button>
      </div>

      <!-- 四项统计：数量可点（知识收藏直达我的收藏） -->
      <div class="hero-stats">
        <div class="stat" v-for="s in statCards" :key="s.label"
             :class="{ clickable: !!s.to }" @click="s.to && goStat(s.to)">
          <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
            <el-icon><component :is="s.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-label">{{ s.label }}</div>
            <div class="stat-value">{{ s.value }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ② 资料编辑卡：基本资料 / 修改密码 -->
    <section class="panel-card edit-card" ref="editCardRef">
      <div class="card-head">
        <el-icon class="head-icon"><User /></el-icon>
        <span class="head-title">账户资料</span>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本资料" name="basic">
          <el-form :model="form" label-width="86px" class="edit-form">
            <div class="form-grid">
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
            </div>
            <div class="form-actions">
              <el-button type="primary" :loading="saving" @click="saveBasic">保存修改</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password">
          <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-width="86px" class="edit-form">
            <div class="form-grid">
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入原密码" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="pwd.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
              </el-form-item>
            </div>
            <div class="form-actions">
              <el-button type="primary" :loading="changing" @click="savePwd">修改密码</el-button>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- ③ 账户安全卡 -->
    <section class="panel-card security-card">
      <div class="card-head">
        <el-icon class="head-icon"><Lock /></el-icon>
        <span class="head-title">账户安全</span>
        <el-button class="head-action" size="small" :icon="Setting" @click="gotoTab('password')">安全设置</el-button>
      </div>

      <div class="sec-grid">
        <div class="sec-item">
          <div class="sec-icon" :class="{ ok: !!form.phone }"><el-icon><Iphone /></el-icon></div>
          <div class="sec-body">
            <div class="sec-title">手机号</div>
            <div class="sec-desc">{{ form.phone || '未填写' }}</div>
          </div>
          <span class="sec-badge" :class="form.phone ? 'ok' : 'muted'">{{ form.phone ? '已填写' : '未填写' }}</span>
        </div>

        <div class="sec-item">
          <div class="sec-icon" :class="{ ok: !!form.email }"><el-icon><Message /></el-icon></div>
          <div class="sec-body">
            <div class="sec-title">邮箱</div>
            <div class="sec-desc">{{ form.email || '未填写' }}</div>
          </div>
          <span class="sec-badge" :class="form.email ? 'ok' : 'muted'">{{ form.email ? '已填写' : '未填写' }}</span>
        </div>

        <div class="sec-item">
          <div class="sec-icon ok"><el-icon><Key /></el-icon></div>
          <div class="sec-body">
            <div class="sec-title">登录密码</div>
            <div class="sec-desc">建议定期更换密码，保障账户安全</div>
          </div>
          <span class="sec-badge ok">已设置</span>
        </div>

        <div class="sec-item">
          <div class="sec-icon" :class="{ ok: !!avatarUrl }"><el-icon><Camera /></el-icon></div>
          <div class="sec-body">
            <div class="sec-title">头像</div>
            <div class="sec-desc">{{ avatarUrl ? '已上传自定义头像' : '使用昵称首字作为头像' }}</div>
          </div>
          <el-upload class="sec-upload" :show-file-list="false" :http-request="doUpload"
                     accept="image/*" :before-upload="beforeUpload">
            <el-button size="small" plain>{{ avatarUrl ? '更换' : '上传' }}</el-button>
          </el-upload>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Camera, EditPen, Calendar, User, Lock, Iphone, Message, Key, Setting,
  Tickets, CircleCheck, Clock, StarFilled
} from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('basic')
const saving = ref(false)
const changing = ref(false)
const pwdRef = ref(null)
const editCardRef = ref(null)

const form = reactive({
  username: '', displayName: '', phone: '', email: '', company: '', department: '',
  avatarColor: '', avatarUrl: '', createdAt: '', status: 1
})
const avatarUrl = ref('')
const stats = ref({ total: 0, resolved: 0, pending: 0, favoriteCount: 0, scope: 'MINE_CUSTOMER' })

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

const showCompany = computed(() => ['ADMIN', 'SUPERVISOR', 'AGENT'].includes(auth.role))
const statusNormal = computed(() => form.status !== 0)
const initial = computed(() => (form.displayName || form.username || '?').charAt(0).toUpperCase())

// 统计卡文案随身份口径变化：我提交 / 我负责 / 全站
const scopeLabel = computed(() => ({
  MINE_CUSTOMER: '累计提交工单',
  MINE_AGENT: '我负责的工单',
  ALL: '全站工单'
}[stats.value.scope] || '累计提交工单'))

const statCards = computed(() => [
  { label: scopeLabel.value, value: stats.value.total ?? 0, icon: Tickets, color: '#3b82f6', bg: '#eff6ff' },
  { label: '已解决', value: stats.value.resolved ?? 0, icon: CircleCheck, color: '#22c55e', bg: '#f0fdf4' },
  { label: '待处理', value: stats.value.pending ?? 0, icon: Clock, color: '#f59e0b', bg: '#fffbeb' },
  { label: '知识收藏', value: stats.value.favoriteCount ?? 0, icon: StarFilled, color: '#8b5cf6', bg: '#f5f3ff', to: '/knowledge?fav=1' }
])

/* 时间格式化：只保留到分钟 */
function fmtDate(t) { return t ? String(t).replace('T', ' ').substring(0, 16) : '—' }

/* 点击统计卡跳转（仅知识收藏有目标页） */
function goStat(to) { router.push(to) }

/* 滚动到资料编辑卡并切到基本资料 */
async function focusEdit() {
  activeTab.value = 'basic'
  await nextTick()
  editCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/* 切到指定 Tab 并滚动到编辑卡 */
async function gotoTab(name) {
  activeTab.value = name
  await nextTick()
  editCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function load() {
  const data = await api.get('/auth/profile')
  Object.assign(form, data)
  avatarUrl.value = data.avatarUrl || ''
  stats.value = await api.get('/auth/profile/stats')
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
/* 三张卡垂直排列、一行一个、居中铺满 */
.profile-page {
  display: flex; flex-direction: column; gap: 18px;
  max-width: 1040px; margin: 0 auto; width: 100%;
}
.page-head h2 { margin: 0 0 4px; font-size: 20px; font-weight: 700; color: var(--text-main); }
.page-head p { margin: 0 0 2px; font-size: 13px; color: var(--text-muted); }

.panel-card {
  background: #fff; border-radius: 14px; padding: 22px 24px;
  border: 1px solid #eef2f7; box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
}
.card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.head-icon { color: var(--primary, #6366f1); font-size: 16px; }
.head-title { font-size: 15px; font-weight: 600; color: var(--text-main); }
.head-action { margin-left: auto; }

/* ===== 资料卡 ===== */
.hero-main { display: flex; align-items: center; gap: 18px; }
.hero-avatar-wrap { flex: none; }
.hero-avatar {
  width: 78px; height: 78px; border-radius: 50%; object-fit: cover;
  background: #eef2ff; box-shadow: 0 4px 14px rgba(99, 102, 241, 0.18);
}
.hero-avatar.fallback {
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 30px; font-weight: 700;
}
.hero-id { flex: 1; min-width: 0; }
.hero-name { font-size: 22px; font-weight: 700; color: var(--text-main); line-height: 1.3; }
.hero-username { font-size: 13px; color: var(--text-muted); margin: 2px 0 8px; }
.hero-meta {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  font-size: 12.5px; color: var(--text-muted);
}
.hero-meta b { font-weight: 600; color: var(--text-main); }
.hero-meta b.ok { color: #16a34a; }
.hero-meta b.bad { color: #dc2626; }
.hero-meta .dot { width: 7px; height: 7px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 3px rgba(34,197,94,.15); }
.hero-meta .dot.off { background: #ef4444; box-shadow: 0 0 0 3px rgba(239,68,68,.15); }
.hero-meta .sep { width: 1px; height: 11px; background: #e2e8f0; margin: 0 4px; }
.hero-edit { flex: none; }

.hero-stats {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px;
  margin-top: 18px; padding-top: 18px; border-top: 1px dashed #e8eef7;
}
.stat {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border-radius: 12px; background: #f8fafc;
  border: 1px solid transparent; transition: all 0.18s;
}
.stat.clickable { cursor: pointer; }
.stat.clickable:hover { background: #f5f3ff; border-color: #ddd6fe; transform: translateY(-1px); }
.stat-icon {
  width: 36px; height: 36px; border-radius: 10px; flex: none;
  display: flex; align-items: center; justify-content: center; font-size: 17px;
}
.stat-label { font-size: 12.5px; color: var(--text-sub); }
.stat-value { font-size: 20px; font-weight: 700; color: var(--text-main); line-height: 1.25; }

/* ===== 资料编辑卡 ===== */
.edit-card :deep(.el-tabs__header) { margin-bottom: 16px; }
.edit-form { max-width: 100%; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 24px; }
.form-actions { padding-left: 86px; margin-top: 4px; }

/* ===== 账户安全卡 ===== */
.sec-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.sec-item {
  display: flex; align-items: center; gap: 12px;
  padding: 14px 16px; border-radius: 12px;
  background: #f8fafc; border: 1px solid #eef2f7;
}
.sec-icon {
  width: 34px; height: 34px; border-radius: 9px; flex: none;
  display: flex; align-items: center; justify-content: center;
  background: #eef2ff; color: #94a3b8; font-size: 16px;
}
.sec-icon.ok { background: #ecfdf5; color: #10b981; }
.sec-body { flex: 1; min-width: 0; }
.sec-title { font-size: 13px; font-weight: 600; color: var(--text-main); }
.sec-desc {
  font-size: 12px; color: var(--text-muted); margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.sec-badge {
  flex: none; font-size: 11.5px; padding: 2px 9px; border-radius: 999px;
  background: #f1f5f9; color: #94a3b8;
}
.sec-badge.ok { background: #ecfdf5; color: #059669; }
.sec-badge.muted { background: #f1f5f9; color: #94a3b8; }
.sec-upload { flex: none; }

@media (max-width: 900px) {
  .hero-stats { grid-template-columns: 1fr 1fr; }
  .sec-grid { grid-template-columns: 1fr; }
  .form-grid { grid-template-columns: 1fr; }
  .hero-edit { align-self: flex-start; }
}
</style>
