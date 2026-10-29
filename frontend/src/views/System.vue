<template>
  <div class="system">
    <el-tabs v-model="activeTab">
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <!-- 角色分布概览：只展示人数，不做占比 -->
        <div class="stat-row">
          <div class="stat-card" v-for="s in userStats" :key="s.key">
            <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
              <el-icon :size="19"><component :is="s.icon" /></el-icon>
            </div>
            <div class="stat-body">
              <div class="stat-label">{{ s.label }}</div>
              <div class="stat-value">{{ s.value }}</div>
            </div>
          </div>
        </div>

        <div class="panel-card user-panel">
          <!-- 工具栏 -->
          <div class="toolbar">
            <div class="toolbar-left">
              <el-button type="primary" :icon="Plus" @click="openUserCreate">新增用户</el-button>
              <template v-if="selectedIds.length">
                <el-button size="small" @click="batchStatus(1)">批量启用</el-button>
                <el-button size="small" @click="batchStatus(0)">批量禁用</el-button>
                <span class="sel-tip">已选 {{ selectedIds.length }} 项</span>
              </template>
            </div>
            <div class="toolbar-right">
              <el-input v-model="userQuery.keyword" placeholder="搜索用户名/姓名/手机号" :prefix-icon="Search"
                        clearable style="width: 210px" />
              <el-select v-model="userQuery.role" placeholder="角色" clearable style="width: 116px">
                <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
              </el-select>
              <el-select v-model="userQuery.status" placeholder="状态" clearable style="width: 104px">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
              <el-button :icon="Refresh" @click="resetUserFilter">重置</el-button>
            </div>
          </div>

          <el-table :data="pagedUsers" v-loading="loadingUsers" row-key="id"
                    @selection-change="onSelectionChange" style="width: 100%">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="id" label="ID" width="62" />
            <el-table-column label="头像" width="68">
              <template #default="{ row }">
                <div class="avatar" :style="{ background: avatarBg(row) }">{{ initialOf(row) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" width="128" show-overflow-tooltip />
            <el-table-column prop="display_name" label="姓名" width="104" show-overflow-tooltip />
            <el-table-column label="角色" width="132">
              <template #default="{ row }">
                <span class="role-tag" :style="{ color: roleOf(row.role).color, background: roleOf(row.role).bg }">
                  <el-icon :size="12"><component :is="roleOf(row.role).icon" /></el-icon>
                  {{ roleOf(row.role).label }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="phone" label="手机号" width="126">
              <template #default="{ row }">{{ row.phone || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <el-switch :model-value="row.status === 1" size="small"
                           @change="v => toggleStatus(row, v)" />
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="156">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="188">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" size="small" @click="openUserEdit(row)">编辑</el-button>
                  <el-dropdown trigger="click" @command="r => changeRole(row, r)">
                    <el-button link type="primary" size="small">角色</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="r in ROLES" :key="r.value" :command="r.value"
                                          :disabled="row.role === r.value">{{ r.label }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small"
                             @click="toggleStatus(row, row.status !== 1)">
                    {{ row.status === 1 ? '禁用' : '启用' }}
                  </el-button>
                  <el-dropdown trigger="click" @command="cmd => onMore(row, cmd)">
                    <el-button link size="small" :icon="MoreFilled" class="more-btn" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="password">重置密码</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <span class="pager-total">共 {{ filteredUsers.length }} 条</span>
            <el-pagination background layout="prev, pager, next, sizes, jumper"
                           :total="filteredUsers.length" :page-size="userPageSize"
                           :current-page="userPage" :page-sizes="[10, 20, 50]"
                           @current-change="p => userPage = p"
                           @size-change="s => { userPageSize = s; userPage = 1 }" />
          </div>
        </div>
      </el-tab-pane>

      <!-- 工单分类 -->
      <el-tab-pane label="工单分类" name="categories">
        <div class="panel-card cat-panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-button type="primary" :icon="Plus" @click="openCatEdit()">新建分类</el-button>
            </div>
            <div class="toolbar-right">
              <el-input v-model="catQuery.keyword" placeholder="输入分类名称 / 说明关键词"
                        :prefix-icon="Search" clearable style="width: 220px" />
              <el-select v-model="catQuery.status" placeholder="状态：全部" clearable style="width: 128px">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
              <el-select v-model="catQuery.sort" style="width: 136px">
                <el-option label="默认排序" value="default" />
                <el-option label="SLA 从短到长" value="slaAsc" />
                <el-option label="SLA 从长到短" value="slaDesc" />
                <el-option label="按名称排列" value="name" />
              </el-select>
              <el-button :icon="Refresh" @click="loadCategories">刷新</el-button>
            </div>
          </div>

          <el-table :data="pagedCategories" v-loading="loadingCategories" row-key="id" style="width: 100%">
            <el-table-column label="分类名称" min-width="280">
              <template #default="{ row }">
                <div class="cat-name">
                  <span class="cat-icon" :style="{ background: row.color || '#6366f1' }">
                    <el-icon :size="15"><component :is="catIcon(row)" /></el-icon>
                  </span>
                  <div class="cat-text">
                    <div class="cat-title">{{ row.name }}</div>
                    <div class="cat-desc">{{ row.description || '暂无说明' }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="sla_hours" label="SLA 时限(小时)" width="150" align="center" />
            <el-table-column prop="sort" label="排序" width="110" align="center" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <span class="status-text" :class="{ 'is-off': row.status !== 1 }">
                  <i class="status-dot"></i>{{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="196">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button link type="primary" size="small" :icon="Edit" @click="openCatEdit(row)">编辑</el-button>
                  <el-button link :type="row.status === 1 ? 'info' : 'success'" size="small"
                             @click="toggleCatStatus(row)">
                    {{ row.status === 1 ? '停用' : '启用' }}
                  </el-button>
                  <el-dropdown trigger="click" @command="dir => moveCategory(row, dir)">
                    <el-button link size="small" :icon="MoreFilled" class="more-btn" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="-1">上移一位</el-dropdown-item>
                        <el-dropdown-item command="1">下移一位</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <span class="pager-total">共 {{ filteredCategories.length }} 条</span>
            <el-pagination background layout="prev, pager, next, sizes, jumper"
                           :total="filteredCategories.length" :page-size="catPageSize"
                           :current-page="catPage" :page-sizes="[10, 20, 50]"
                           @current-change="p => catPage = p"
                           @size-change="s => { catPageSize = s; catPage = 1 }" />
          </div>
        </div>
      </el-tab-pane>

      <!-- 审计日志 -->
      <el-tab-pane label="操作日志" name="logs">
        <!-- 概览：总条数、今日操作、失败次数、活跃操作人 -->
        <div class="stat-row log-stats">
          <div class="stat-card" v-for="s in logStats" :key="s.key">
            <div class="stat-icon" :style="{ background: s.bg, color: s.color }">
              <el-icon :size="19"><component :is="s.icon" /></el-icon>
            </div>
            <div class="stat-body">
              <div class="stat-label">{{ s.label }}</div>
              <div class="stat-value">{{ s.value }}</div>
            </div>
          </div>
        </div>

        <div class="panel-card log-panel">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-date-picker v-model="logQuery.range" type="daterange" unlink-panels
                              value-format="YYYY-MM-DD" range-separator="至"
                              start-placeholder="开始日期" end-placeholder="结束日期"
                              style="width: 250px" />
              <el-select v-model="logQuery.category" placeholder="操作类型" clearable style="width: 138px">
                <el-option v-for="c in LOG_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
              <el-select v-model="logQuery.result" placeholder="执行结果" clearable style="width: 112px">
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
              </el-select>
              <el-input v-model="logQuery.operator" placeholder="操作人 / 姓名 / 账号"
                        :prefix-icon="Search" clearable style="width: 190px" @keyup.enter="searchLogs" />
              <el-button type="primary" :icon="Search" @click="searchLogs">查询</el-button>
              <el-button :icon="Refresh" @click="resetLogFilter">重置</el-button>
            </div>
            <div class="toolbar-right">
              <el-button :icon="Download" :loading="exporting" @click="exportLogs">导出 CSV</el-button>
            </div>
          </div>

          <el-table :data="logs" v-loading="loadingLogs" style="width: 100%">
            <el-table-column prop="id" label="ID" width="66" />
            <el-table-column label="操作人" width="184">
              <template #default="{ row }">
                <div class="op-cell">
                  <div class="avatar" :style="{ background: logAvatarBg(row) }">{{ logInitial(row) }}</div>
                  <div class="op-text">
                    <div class="op-name">{{ row.operator_name || '—' }}</div>
                    <div class="op-role">{{ roleOf(row.role || row.current_role).label }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作类型" width="132">
              <template #default="{ row }">
                <span class="act-tag" :style="{ color: actionOf(row.action).color, background: actionOf(row.action).bg }">
                  {{ actionOf(row.action).label }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作对象" width="168" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.target_id || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="对象类型" width="104">
              <template #default="{ row }">
                <span class="obj-type">{{ targetTypeLabel(row.target_type) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="detail" label="操作内容" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">{{ row.detail || '—' }}</template>
            </el-table-column>
            <el-table-column label="执行结果" width="98">
              <template #default="{ row }">
                <span class="result-tag" :class="row.status === 'FAILED' ? 'is-fail' : 'is-ok'">
                  <i class="result-dot"></i>{{ row.status === 'FAILED' ? '失败' : '成功' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP 地址" width="132">
              <template #default="{ row }">
                <span class="mono">{{ row.ip || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作时间" width="158">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <span class="pager-total">共 {{ logTotal }} 条</span>
            <el-pagination background layout="prev, pager, next, sizes, jumper"
                           :total="logTotal" :page-size="logPageSize"
                           :current-page="logPage" :page-sizes="[10, 20, 50]"
                           @current-change="loadLogs"
                           @size-change="s => { logPageSize = s; loadLogs(1) }" />
          </div>
        </div>
      </el-tab-pane>

      <!-- AI 调用日志 -->
      <el-tab-pane label="AI 日志" name="aiLogs">
        <div class="panel-card table-panel">
          <el-table :data="aiLogs" stripe v-loading="loadingAiLogs" style="width: 100%">
            <el-table-column prop="task_type" label="任务类型" width="120" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="question" label="问题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="answer_length" label="回答长度" width="90" align="center" />
            <el-table-column prop="duration_ms" label="耗时(ms)" width="100" align="center" />
            <el-table-column prop="model" label="模型" width="120" show-overflow-tooltip />
            <el-table-column prop="source" label="引用知识" width="120" show-overflow-tooltip />
            <el-table-column prop="error_summary" label="错误" min-width="140" show-overflow-tooltip />
            <el-table-column prop="created_at" label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next" :total="aiLogTotal"
                           :page-size="20" :current-page="aiLogPage" @current-change="loadAiLogs" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增 / 编辑用户 -->
    <el-dialog v-model="userVisible" :title="userForm.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="userForm" label-width="86px">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" :disabled="!!userForm.id"
                    placeholder="字母、数字或下划线，3~32 位" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="userForm.displayName" placeholder="显示在工单与聊天中" />
        </el-form-item>
        <el-form-item label="角色" required v-if="!userForm.id">
          <el-select v-model="userForm.role" style="width: 100%">
            <el-option v-for="r in ROLES" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" maxlength="11" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.department" placeholder="选填，如 客服部" />
        </el-form-item>
        <el-form-item label="初始密码" v-if="!userForm.id">
          <el-input v-model="userForm.password" placeholder="留空则使用默认密码 123456" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类编辑 -->
    <el-dialog v-model="catVisible" :title="catForm.id ? '编辑分类' : '新建分类'" width="480px">
      <el-form :model="catForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="catForm.name" placeholder="如：账户问题" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="catForm.description" type="textarea" :rows="2" maxlength="200" show-word-limit
                    placeholder="一句话说明这类工单涵盖什么，会显示在分类名称下方" />
        </el-form-item>
        <el-form-item label="SLA 时限">
          <el-input-number v-model="catForm.slaHours" :min="1" :max="720" />
          <span class="form-hint">小时</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="catForm.sort" :min="0" />
          <span class="form-hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="图标颜色">
          <el-color-picker v-model="catForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCat" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, MoreFilled, Edit, Download,
  UserFilled, Medal, Avatar, Headset, User,
  Promotion, Setting, Money, ChatDotRound, Warning, Collection
} from '@element-plus/icons-vue'
import api from '../api'

const activeTab = ref('users')
const users = ref([])
const loadingUsers = ref(false)
const categories = ref([])
const loadingCategories = ref(false)
const logs = ref([])
const loadingLogs = ref(false)
const logPage = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)
const logStatData = ref({})
const exporting = ref(false)
const aiLogs = ref([])
const loadingAiLogs = ref(false)
const aiLogPage = ref(1)
const aiLogTotal = ref(0)

// 用户列表的筛选与分页（数据量小，全部在前端过滤）
const userQuery = reactive({ keyword: '', role: '', status: '' })
const userPage = ref(1)
const userPageSize = ref(10)
const selectedIds = ref([])

// 分类列表的筛选与分页
const catQuery = reactive({ keyword: '', status: '', sort: 'default' })
const catPage = ref(1)
const catPageSize = ref(10)

// 操作日志的筛选条件（服务端过滤，改动后点「查询」生效）
const logQuery = reactive({ range: null, category: '', result: '', operator: '' })

const userVisible = ref(false)
const savingUser = ref(false)
const userForm = reactive({
  id: null, username: '', displayName: '', role: 'CUSTOMER',
  phone: '', email: '', department: '', password: ''
})

const catVisible = ref(false)
const savingCat = ref(false)
const catForm = reactive({ id: null, name: '', description: '', slaHours: 24, sort: 0, color: '#6366f1' })

/** 角色元数据：文案、图标与配色，表格标签和统计卡共用 */
const ROLES = [
  { value: 'ADMIN', label: '管理员', icon: Medal, color: '#3b82f6', bg: 'rgba(59,130,246,.10)' },
  { value: 'SUPERVISOR', label: '客服主管', icon: Avatar, color: '#8b5cf6', bg: 'rgba(139,92,246,.10)' },
  { value: 'AGENT', label: '客服人员', icon: Headset, color: '#f59e0b', bg: 'rgba(245,158,11,.12)' },
  { value: 'CUSTOMER', label: '客户', icon: User, color: '#14b8a6', bg: 'rgba(20,184,166,.12)' }
]

/** 取角色元数据，未知角色回落到客户样式 */
function roleOf(role) {
  return ROLES.find(r => r.value === role) || ROLES[3]
}

/** 操作类型元数据：中文标签 + 标签配色，未收录的英文码原样显示 */
const ACTIONS = {
  // 登录认证
  LOGIN: { label: '登录系统', color: '#2563eb', bg: 'rgba(37,99,235,.10)' },
  LOGIN_FAILED: { label: '登录失败', color: '#dc2626', bg: 'rgba(220,38,38,.10)' },
  REGISTER: { label: '注册账号', color: '#0d9488', bg: 'rgba(13,148,136,.10)' },
  CHANGE_PASSWORD: { label: '修改密码', color: '#7c3aed', bg: 'rgba(124,58,237,.10)' },
  UPDATE_PROFILE: { label: '修改资料', color: '#64748b', bg: 'rgba(100,116,139,.12)' },
  UPDATE_AVATAR: { label: '更新头像', color: '#64748b', bg: 'rgba(100,116,139,.12)' },
  // 工单流转
  CREATE_TICKET: { label: '创建工单', color: '#2563eb', bg: 'rgba(37,99,235,.10)' },
  APPLY_TICKET: { label: '申请接单', color: '#d97706', bg: 'rgba(217,119,6,.12)' },
  REVIEW_TICKET: { label: '审核申请', color: '#8b5cf6', bg: 'rgba(139,92,246,.10)' },
  ASSIGN_TICKET: { label: '分配工单', color: '#4f46e5', bg: 'rgba(79,70,229,.10)' },
  ACCEPT_TICKET: { label: '受理工单', color: '#0891b2', bg: 'rgba(8,145,178,.10)' },
  REPLY_TICKET: { label: '回复工单', color: '#16a34a', bg: 'rgba(22,163,74,.10)' },
  RESOLVE_TICKET: { label: '提交方案', color: '#16a34a', bg: 'rgba(22,163,74,.10)' },
  CONFIRM_TICKET: { label: '确认结果', color: '#0d9488', bg: 'rgba(13,148,136,.10)' },
  CLOSE_TICKET: { label: '关闭工单', color: '#64748b', bg: 'rgba(100,116,139,.12)' },
  REOPEN_TICKET: { label: '重开工单', color: '#ea580c', bg: 'rgba(234,88,12,.10)' },
  RATE_TICKET: { label: '客户评价', color: '#ca8a04', bg: 'rgba(202,138,4,.12)' },
  URGE_TICKET: { label: '客户催办', color: '#dc2626', bg: 'rgba(220,38,38,.10)' },
  // 知识库
  CREATE_ARTICLE: { label: '新建文章', color: '#2563eb', bg: 'rgba(37,99,235,.10)' },
  UPDATE_ARTICLE: { label: '更新文章', color: '#4f46e5', bg: 'rgba(79,70,229,.10)' },
  PUBLISH_ARTICLE: { label: '发布文章', color: '#16a34a', bg: 'rgba(22,163,74,.10)' },
  DELETE_ARTICLE: { label: '删除文章', color: '#dc2626', bg: 'rgba(220,38,38,.10)' },
  // 用户管理
  CREATE_USER: { label: '新增用户', color: '#2563eb', bg: 'rgba(37,99,235,.10)' },
  UPDATE_USER: { label: '编辑用户', color: '#4f46e5', bg: 'rgba(79,70,229,.10)' },
  UPDATE_USER_STATUS: { label: '启停账号', color: '#d97706', bg: 'rgba(217,119,6,.12)' },
  BATCH_USER_STATUS: { label: '批量启停', color: '#d97706', bg: 'rgba(217,119,6,.12)' },
  UPDATE_USER_ROLE: { label: '调整角色', color: '#8b5cf6', bg: 'rgba(139,92,246,.10)' },
  RESET_PASSWORD: { label: '重置密码', color: '#dc2626', bg: 'rgba(220,38,38,.10)' },
  // 分类管理
  CREATE_CATEGORY: { label: '新建分类', color: '#2563eb', bg: 'rgba(37,99,235,.10)' },
  UPDATE_CATEGORY: { label: '更新分类', color: '#4f46e5', bg: 'rgba(79,70,229,.10)' },
  UPDATE_CATEGORY_STATUS: { label: '启停分类', color: '#d97706', bg: 'rgba(217,119,6,.12)' },
  DELETE_CATEGORY: { label: '删除分类', color: '#dc2626', bg: 'rgba(220,38,38,.10)' }
}

/** 取操作类型元数据，未收录的英文码原样降级显示 */
function actionOf(action) {
  return ACTIONS[action] || { label: action || '—', color: '#64748b', bg: 'rgba(100,116,139,.12)' }
}

/** 操作日志类型筛选项（对应后端 ACTION_GROUPS） */
const LOG_CATEGORIES = [
  { value: 'AUTH', label: '登录认证' },
  { value: 'TICKET', label: '工单流转' },
  { value: 'KNOWLEDGE', label: '知识库' },
  { value: 'USER', label: '用户管理' },
  { value: 'CATEGORY', label: '分类管理' }
]

/** 对象类型中文名 */
const TARGET_TYPES = { USER: '用户', TICKET: '工单', ARTICLE: '知识文章', CATEGORY: '工单分类' }

/** 取对象类型中文名 */
function targetTypeLabel(type) {
  return TARGET_TYPES[type] || (type || '—')
}

/** 日志概览统计卡 */
const logStats = computed(() => {
  const d = logStatData.value || {}
  return [
    { key: 'total', label: '日志总数', value: d.total || 0, icon: Collection, color: '#3b82f6', bg: 'rgba(59,130,246,.10)' },
    { key: 'today', label: '今日操作', value: d.today || 0, icon: Promotion, color: '#10b981', bg: 'rgba(16,185,129,.10)' },
    { key: 'failed', label: '失败操作', value: d.failed || 0, icon: Warning, color: '#ef4444', bg: 'rgba(239,68,68,.10)' },
    { key: 'operators', label: '活跃操作人', value: d.operators || 0, icon: UserFilled, color: '#8b5cf6', bg: 'rgba(139,92,246,.10)' }
  ]
})

/** 日志操作人头像底色：优先当前用户头像色，其次按角色色 */
function logAvatarBg(row) {
  return row.avatar_color || roleOf(row.role || row.current_role).color
}

/** 日志操作人头像文字 */
function logInitial(row) {
  const name = String(row.operator_name || '?').trim()
  return name ? name.charAt(0).toUpperCase() : '?'
}

/** 统计卡：总数 + 各角色人数 */
const userStats = computed(() => {
  const count = role => users.value.filter(u => u.role === role).length
  return [
    { key: 'total', label: '用户总数', value: users.value.length, icon: UserFilled, color: '#3b82f6', bg: 'rgba(59,130,246,.10)' },
    { key: 'admin', label: '管理员', value: count('ADMIN'), icon: Medal, color: '#10b981', bg: 'rgba(16,185,129,.10)' },
    { key: 'supervisor', label: '客服主管', value: count('SUPERVISOR'), icon: Avatar, color: '#8b5cf6', bg: 'rgba(139,92,246,.10)' },
    { key: 'agent', label: '客服人员', value: count('AGENT'), icon: Headset, color: '#f59e0b', bg: 'rgba(245,158,11,.12)' },
    { key: 'customer', label: '客户', value: count('CUSTOMER'), icon: User, color: '#14b8a6', bg: 'rgba(20,184,166,.12)' }
  ]
})

/** 按关键字、角色、状态过滤后的用户 */
const filteredUsers = computed(() => {
  const kw = userQuery.keyword.trim().toLowerCase()
  return users.value.filter(u => {
    if (userQuery.role && u.role !== userQuery.role) return false
    // 状态取值范围只有 0/1，清空下拉时值可能是 undefined，这里显式判断
    if (userQuery.status === 0 || userQuery.status === 1) {
      if (u.status !== userQuery.status) return false
    }
    if (!kw) return true
    return [u.username, u.display_name, u.phone, u.email]
      .filter(Boolean)
      .some(v => String(v).toLowerCase().includes(kw))
  })
})

/** 当前页显示的用户 */
const pagedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize.value
  return filteredUsers.value.slice(start, start + userPageSize.value)
})

// 筛选条件变化后回到第一页
watch(() => [userQuery.keyword, userQuery.role, userQuery.status], () => { userPage.value = 1 })
watch(() => [catQuery.keyword, catQuery.status, catQuery.sort], () => { catPage.value = 1 })

/** 分类图标：按名称匹配语义图标，自定义分类按 id 在图标池里轮换 */
const CAT_ICONS = {
  账户问题: User,
  产品咨询: Promotion,
  技术故障: Setting,
  账单与支付: Money,
  意见建议: ChatDotRound,
  其他: MoreFilled
}
const CAT_ICON_POOL = [Collection, Warning, Avatar, Promotion, ChatDotRound, Setting]

/** 取分类对应图标 */
function catIcon(row) {
  return CAT_ICONS[row.name] || CAT_ICON_POOL[Math.abs(Number(row.id) || 0) % CAT_ICON_POOL.length]
}

/** 按「排序」字段升序（相同则按 id），作为所有筛选的基础顺序 */
const sortedCategories = computed(() =>
  [...categories.value].sort((a, b) => (a.sort - b.sort) || (a.id - b.id))
)

/** 分类筛选：关键字匹配名称或说明，状态可筛 */
const filteredCategories = computed(() => {
  const kw = catQuery.keyword.trim().toLowerCase()
  let list = sortedCategories.value.filter(c => {
    if (catQuery.status === 0 || catQuery.status === 1) {
      if (c.status !== catQuery.status) return false
    }
    if (!kw) return true
    return [c.name, c.description].filter(Boolean).some(v => String(v).toLowerCase().includes(kw))
  })
  if (catQuery.sort === 'slaAsc') {
    list = [...list].sort((a, b) => a.sla_hours - b.sla_hours)
  } else if (catQuery.sort === 'slaDesc') {
    list = [...list].sort((a, b) => b.sla_hours - a.sla_hours)
  } else if (catQuery.sort === 'name') {
    list = [...list].sort((a, b) => String(a.name).localeCompare(String(b.name), 'zh-CN'))
  }
  return list
})

/** 当前页显示的分类 */
const pagedCategories = computed(() => {
  const start = (catPage.value - 1) * catPageSize.value
  return filteredCategories.value.slice(start, start + catPageSize.value)
})

function fmtTime(t) { return t ? String(t).replace('T', ' ').substring(0, 19) : '' }

/** 头像底色：优先用后端存的颜色，没有则按角色取色 */
function avatarBg(row) {
  return row.avatar_color || roleOf(row.role).color
}

/** 头像文字：取显示名首字 */
function initialOf(row) {
  const name = String(row.display_name || row.username || '?').trim()
  return name ? name.charAt(0).toUpperCase() : '?'
}

async function loadUsers() {
  loadingUsers.value = true
  try { users.value = await api.get('/admin/users') } finally { loadingUsers.value = false }
}

async function loadCategories() {
  loadingCategories.value = true
  try { categories.value = await api.get('/admin/categories') } finally { loadingCategories.value = false }
}

/** 把当前日志筛选条件转成请求参数 */
function logParams(extra = {}) {
  const [start, end] = logQuery.range || []
  return {
    category: logQuery.category || undefined,
    result: logQuery.result || undefined,
    operator: logQuery.operator.trim() || undefined,
    start: start || undefined,
    end: end || undefined,
    ...extra
  }
}

/** 分页加载操作日志 */
async function loadLogs(page = 1) {
  loadingLogs.value = true
  logPage.value = page
  try {
    const data = await api.get('/admin/logs', { params: logParams({ page, pageSize: logPageSize.value }) })
    logs.value = data.list || []
    logTotal.value = data.total || 0
  } finally { loadingLogs.value = false }
}

/** 加载日志概览统计 */
async function loadLogStats() {
  try { logStatData.value = await api.get('/admin/logs/stats') } catch (e) { /* 统计失败不影响列表 */ }
}

/** 点击查询：回到第一页并刷新统计 */
function searchLogs() {
  loadLogs(1)
  loadLogStats()
}

/** 重置筛选条件 */
function resetLogFilter() {
  logQuery.range = null
  logQuery.category = ''
  logQuery.result = ''
  logQuery.operator = ''
  searchLogs()
}

/** 导出当前筛选结果为 CSV（UTF-8 BOM，Excel 双击打开不乱码） */
async function exportLogs() {
  exporting.value = true
  try {
    // 一次最多取 2000 条（与后端上限一致），覆盖演示与常规审计场景
    const data = await api.get('/admin/logs', { params: logParams({ page: 1, pageSize: 2000 }) })
    const rows = data.list || []
    if (!rows.length) {
      ElMessage.warning('当前筛选条件下没有可导出的日志')
      return
    }
    const header = ['ID', '操作人', '角色', '操作类型', '对象类型', '操作对象', '操作内容', '执行结果', 'IP 地址', '操作时间']
    const lines = [header]
    rows.forEach(r => {
      lines.push([
        r.id,
        r.operator_name || '',
        roleOf(r.role || r.current_role).label,
        actionOf(r.action).label,
        targetTypeLabel(r.target_type),
        r.target_id || '',
        r.detail || '',
        r.status === 'FAILED' ? '失败' : '成功',
        r.ip || '',
        fmtTime(r.created_at)
      ])
    })
    const csv = lines.map(line => line.map(csvCell).join(',')).join('\r\n')
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
    const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `操作日志_${stamp}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${rows.length} 条日志`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    exporting.value = false
  }
}

/** CSV 单元格转义：含逗号/引号/换行时用双引号包裹，内部引号翻倍 */
function csvCell(value) {
  const text = value == null ? '' : String(value)
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

async function loadAiLogs(page = 1) {
  loadingAiLogs.value = true
  aiLogPage.value = page
  try {
    const data = await api.get('/admin/ai-logs', { params: { page, pageSize: 20 } })
    aiLogs.value = data.list || []
    aiLogTotal.value = data.total
  } finally { loadingAiLogs.value = false }
}

/** 勾选变化时记录待批量操作用户 */
function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

/** 清空筛选条件 */
function resetUserFilter() {
  userQuery.keyword = ''
  userQuery.role = ''
  userQuery.status = ''
  userPage.value = 1
}

/** 打开新增用户弹窗 */
function openUserCreate() {
  Object.assign(userForm, {
    id: null, username: '', displayName: '', role: 'CUSTOMER',
    phone: '', email: '', department: '', password: ''
  })
  userVisible.value = true
}

/** 打开编辑用户弹窗，只回填可编辑字段 */
function openUserEdit(row) {
  Object.assign(userForm, {
    id: row.id, username: row.username, displayName: row.display_name || '',
    role: row.role, phone: row.phone || '', email: row.email || '',
    department: row.department || '', password: ''
  })
  userVisible.value = true
}

/** 保存新增或编辑结果 */
async function saveUser() {
  if (!userForm.id && !userForm.username.trim()) { ElMessage.warning('请输入用户名'); return }
  if (!userForm.displayName.trim()) { ElMessage.warning('请输入姓名'); return }
  savingUser.value = true
  try {
    if (userForm.id) {
      await api.put(`/admin/users/${userForm.id}`, {
        displayName: userForm.displayName,
        phone: userForm.phone,
        email: userForm.email,
        department: userForm.department
      })
      ElMessage.success('资料已更新')
    } else {
      const data = await api.post('/admin/users', {
        username: userForm.username.trim(),
        displayName: userForm.displayName,
        role: userForm.role,
        phone: userForm.phone,
        email: userForm.email,
        department: userForm.department,
        password: userForm.password
      })
      ElMessage.success(`已创建，初始密码 ${data.defaultPassword || '123456'}`)
    }
    userVisible.value = false
    await loadUsers()
  } catch (e) { /* 拦截器已提示 */ } finally { savingUser.value = false }
}

/** 切换单个用户启用状态 */
async function toggleStatus(row, enabled) {
  const status = enabled ? 1 : 0
  try {
    await api.put(`/admin/users/${row.id}/status`, { status })
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
    await loadUsers()
  } catch (e) { /* 拦截器已提示 */ }
}

/** 修改用户角色 */
async function changeRole(row, role) {
  if (!role || role === row.role) return
  try {
    await api.put(`/admin/users/${row.id}/role`, { role })
    ElMessage.success('角色已更新')
    await loadUsers()
  } catch (e) { /* 拦截器已提示 */ }
}

/** 更多操作入口 */
function onMore(row, cmd) {
  if (cmd === 'password') resetPwd(row)
}

/** 重置指定用户密码 */
async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt(`为 ${row.display_name} 设置新密码（至少 6 位）`, '重置密码', {
    inputPattern: /^.{6,}$/,
    inputErrorMessage: '密码至少 6 位'
  })
  await api.put(`/admin/users/${row.id}/password`, { password: value })
  ElMessage.success('密码已重置')
}

/** 批量启用或禁用选中用户 */
async function batchStatus(status) {
  if (!selectedIds.value.length) return
  const action = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定要批量${action}选中的 ${selectedIds.value.length} 个用户吗？`, `批量${action}`, { type: 'warning' })
  } catch (e) { return }
  const data = await api.post('/admin/users/batch-status', { ids: selectedIds.value, status })
  const skipped = data.skipped ? `，跳过 ${data.skipped} 个（含自己的账号）` : ''
  ElMessage.success(`已${action} ${data.updated} 个用户${skipped}`)
  selectedIds.value = []
  await loadUsers()
}

function openCatEdit(row) {
  if (row) {
    Object.assign(catForm, {
      id: row.id, name: row.name, description: row.description || '',
      slaHours: row.sla_hours, sort: row.sort, color: row.color || '#6366f1'
    })
  } else {
    // 新建时排到末尾，避免和管理员手工排好的顺序打架
    const nextSort = categories.value.reduce((max, c) => Math.max(max, Number(c.sort) || 0), -1) + 1
    Object.assign(catForm, {
      id: null, name: '', description: '', slaHours: 24, sort: nextSort, color: '#6366f1'
    })
  }
  catVisible.value = true
}

/** 保存分类：统一按后端约定的驼峰键提交 */
async function saveCat() {
  if (!catForm.name.trim()) { ElMessage.warning('请输入分类名称'); return }
  savingCat.value = true
  try {
    await api.post('/admin/categories', {
      id: catForm.id,
      name: catForm.name.trim(),
      description: catForm.description,
      slaHours: catForm.slaHours,
      sort: catForm.sort,
      color: catForm.color
    })
    ElMessage.success('保存成功')
    catVisible.value = false
    await loadCategories()
  } catch (e) { /* 拦截器已提示 */ } finally { savingCat.value = false }
}

/** 启用或停用分类 */
async function toggleCatStatus(row) {
  const status = row.status === 1 ? 0 : 1
  try {
    await api.put(`/admin/categories/${row.id}/status`, { status })
    ElMessage.success(status === 1 ? '已启用' : '已停用')
    await loadCategories()
  } catch (e) { /* 拦截器已提示 */ }
}

/** 上移/下移分类：交换位置后按新顺序重写 sort，只提交有变化的项 */
async function moveCategory(row, dir) {
  const list = [...sortedCategories.value]
  const idx = list.findIndex(c => c.id === row.id)
  const target = idx + Number(dir)
  if (idx < 0 || target < 0 || target >= list.length) return
  ;[list[idx], list[target]] = [list[target], list[idx]]
  const tasks = []
  list.forEach((c, i) => {
    if (Number(c.sort) !== i) {
      tasks.push(api.post('/admin/categories', {
        id: c.id, name: c.name, description: c.description || '',
        slaHours: c.sla_hours, sort: i, color: c.color
      }))
    }
  })
  if (!tasks.length) return
  await Promise.all(tasks)
  ElMessage.success('排序已更新')
  await loadCategories()
}

onMounted(() => {
  loadUsers()
  loadCategories()
  loadLogs(1)
  loadLogStats()
  loadAiLogs(1)
})
</script>

<style scoped>
.system { display: flex; flex-direction: column; gap: 16px; width: 100%; }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 10px; }
.sel-tip { font-size: 12px; color: var(--text-sub); }
.table-panel { padding: 16px; }

/* 用户管理：统计卡 + 表格放同一张卡里，视觉更整体 */
.user-panel { padding: 18px 18px 8px; }
.stat-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--card-bg);
  box-shadow: 0 1px 2px rgba(31, 41, 55, .04);
  transition: box-shadow .18s, border-color .18s;
}
.stat-card:hover { border-color: #d7d9e6; box-shadow: 0 4px 14px rgba(31, 41, 55, .06); }
.stat-icon {
  width: 38px; height: 38px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center; flex: none;
}
.stat-label { font-size: 12px; color: var(--text-sub); margin-bottom: 2px; }
.stat-value { font-size: 22px; font-weight: 700; line-height: 1.2; color: var(--text-main); }

.avatar {
  width: 30px; height: 30px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; font-weight: 600;
}
.role-tag {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 6px;
  font-size: 12px; line-height: 18px;
}
.row-actions { display: flex; align-items: center; gap: 2px; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.more-btn { padding: 4px; }

.pager {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; padding: 14px 0 10px;
}
.pager-total { font-size: 13px; color: var(--text-sub); }

/* 工单分类：图标 + 名称 + 说明的三段式单元格 */
.cat-panel { padding: 18px 18px 8px; }
.cat-name { display: flex; align-items: center; gap: 12px; }
.cat-icon {
  width: 32px; height: 32px; border-radius: 50%; flex: none;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.cat-text { min-width: 0; }
.cat-title { font-size: 14px; color: var(--text-main); line-height: 1.4; }
.cat-desc { font-size: 12px; color: var(--text-muted); line-height: 1.4; margin-top: 2px; }

.status-text { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--success); }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.status-text.is-off { color: var(--text-muted); }
.status-text.is-off .status-dot { background: var(--text-muted); }

.form-hint { margin-left: 8px; font-size: 12px; color: var(--text-muted); }

/* 操作日志：概览 4 张卡 + 表格卡 */
.log-stats { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.log-panel { padding: 18px 18px 8px; }
.op-cell { display: flex; align-items: center; gap: 10px; }
.op-text { min-width: 0; }
.op-name { font-size: 13px; color: var(--text-main); line-height: 1.35; }
.op-role { font-size: 11px; color: var(--text-muted); line-height: 1.35; }
.act-tag {
  display: inline-flex; align-items: center;
  padding: 2px 9px; border-radius: 6px;
  font-size: 12px; line-height: 18px; white-space: nowrap;
}
.obj-type { font-size: 12px; color: var(--text-sub); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; color: var(--text-sub); }
.result-tag { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.result-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.result-tag.is-ok { color: var(--success); }
.result-tag.is-fail { color: var(--danger); }

@media (max-width: 1280px) {
  .stat-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .log-stats { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 960px) {
  .stat-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .log-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
