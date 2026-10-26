<template>
  <div class="knowledge">
    <div class="toolbar panel-card">
      <div class="filters">
        <el-input v-model="keyword" placeholder="搜索知识标题 / 关键词" clearable style="width: 260px" :prefix-icon="Search" @keyup.enter="load" @clear="load" />
        <el-button type="primary" :icon="Search" @click="load">搜索</el-button>
      </div>
      <el-button v-if="isStaff" type="primary" :icon="Plus" @click="openEdit()">新建文章</el-button>
    </div>

    <div class="kb-grid">
      <div v-for="a in rows" :key="a.id" class="panel-card kb-card" @click="openDetail(a)">
        <div class="kb-card-head">
          <el-tag size="small" effect="light" :type="statusType(a.status)">{{ statusMap[a.status] }}</el-tag>
          <span class="kb-cat">{{ a.categoryName || '未分类' }}</span>
        </div>
        <div class="kb-card-title">{{ a.title }}</div>
        <div class="kb-card-summary">{{ a.summary }}</div>
        <div class="kb-card-foot">
          <span>{{ a.viewCount }} 次浏览</span>
          <span>{{ fmtTime(a.publishedAt || a.createdAt) }}</span>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && rows.length === 0" description="暂无知识文章" />

    <div class="pager" v-if="total > query.pageSize">
      <el-pagination background layout="total, prev, pager, next" :total="total"
                     :page-size="query.pageSize" :current-page="query.page" @current-change="onPage" />
    </div>

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" size="560px">
      <template #header>
        <div class="drawer-title">{{ current?.title }}</div>
      </template>
      <div v-if="current" class="article-body">
        <div class="article-meta">
          <el-tag size="small" effect="light">{{ current.categoryName || '未分类' }}</el-tag>
          <span class="meta-text">{{ current.authorName }} · {{ fmtTime(current.publishedAt) }}</span>
          <span class="meta-text">{{ current.viewCount }} 次浏览</span>
        </div>
        <div class="article-content" v-html="renderMarkdown(current.body)"></div>
        <div class="article-actions" v-if="isStaff">
          <el-button type="primary" size="small" @click="openEdit(current)">编辑</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑文章' : '新建文章'" width="640px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="标题" required><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.categoryId" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要"><el-input v-model="editForm.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="editForm.keywords" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="正文" required><el-input v-model="editForm.body" type="textarea" :rows="8" placeholder="支持 Markdown" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审核" value="REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下架" value="OFFLINE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
const isStaff = computed(() => ['ADMIN', 'SUPERVISOR', 'AGENT'].includes(auth.role))

const statusMap = { DRAFT: '草稿', REVIEW: '待审核', PUBLISHED: '已发布', OFFLINE: '已下架' }
const statusTypeMap = { DRAFT: 'info', REVIEW: 'warning', PUBLISHED: 'success', OFFLINE: 'info' }
const statusType = s => statusTypeMap[s] || 'info'

const keyword = ref('')
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, pageSize: 12 })
const categories = ref([])

const detailVisible = ref(false)
const current = ref(null)
const editVisible = ref(false)
const editForm = reactive({ id: null, title: '', summary: '', body: '', categoryId: null, keywords: '', status: 'DRAFT' })

function fmtTime(t) { return t ? t.replace('T', ' ').substring(0, 16) : '' }

function renderMarkdown(md) {
  if (!md) return ''
  return md
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/^### (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^# (.*)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/^> (.*)$/gm, '<blockquote>$1</blockquote>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/\n/g, '<br/>')
}

async function load() {
  loading.value = true
  try {
    const params = { page: query.page, pageSize: query.pageSize }
    if (keyword.value) params.keyword = keyword.value
    const data = await api.get('/knowledge', { params })
    rows.value = data.list || []
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onPage(p) { query.page = p; load() }

async function loadCategories() {
  categories.value = await api.get('/knowledge/categories')
}

async function openDetail(a) {
  current.value = await api.get(`/knowledge/${a.id}`)
  detailVisible.value = true
}

function openEdit(a) {
  if (a) {
    Object.assign(editForm, {
      id: a.id, title: a.title, summary: a.summary, body: a.body,
      categoryId: a.categoryId, keywords: a.keywords, status: a.status
    })
  } else {
    Object.assign(editForm, { id: null, title: '', summary: '', body: '', categoryId: null, keywords: '', status: 'DRAFT' })
  }
  editVisible.value = true
}

async function doSave() {
  if (!editForm.title || !editForm.body) { ElMessage.warning('请填写标题和正文'); return }
  await api.post('/knowledge', editForm)
  ElMessage.success('保存成功')
  editVisible.value = false
  load()
}

onMounted(() => {
  // 支持从工单详情页「推荐知识库」带关键字跳转过来
  const kw = useRoute().query.keyword
  if (kw) keyword.value = String(kw)
  load()
  loadCategories()
})
</script>

<style scoped>
.knowledge { display: flex; flex-direction: column; gap: 16px; max-width: 1280px; margin: 0 auto; width: 100%; }
.toolbar { padding: 16px; display: flex; align-items: center; justify-content: space-between; }
.filters { display: flex; gap: 12px; }
.kb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.kb-card { padding: 16px; cursor: pointer; transition: all 0.2s; }
.kb-card:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,0.1); }
.kb-card-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.kb-cat { font-size: 12px; color: var(--text-muted); }
.kb-card-title { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.kb-card-summary { font-size: 13px; color: var(--text-sub); line-height: 1.5; min-height: 40px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.kb-card-foot { display: flex; justify-content: space-between; margin-top: 12px; font-size: 12px; color: var(--text-muted); }
.pager { display: flex; justify-content: flex-end; }
.drawer-title { font-size: 17px; font-weight: 600; }
.article-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.meta-text { font-size: 12px; color: var(--text-muted); }
.article-content { font-size: 14px; line-height: 1.7; }
.article-content :deep(blockquote) { border-left: 3px solid var(--primary); padding-left: 12px; color: var(--text-sub); margin: 8px 0; }
.article-content :deep(li) { margin-left: 20px; }
.article-actions { margin-top: 20px; }
</style>
