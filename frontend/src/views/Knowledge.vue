<template>
  <div class="knowledge">
    <div class="toolbar panel-card">
      <div class="filters">
        <el-input v-model="keyword" placeholder="搜索知识标题 / 关键词" clearable style="width: 260px" :prefix-icon="Search" @keyup.enter="load" @clear="load" />
        <el-button type="primary" :icon="Search" @click="load">搜索</el-button>
      </div>
      <el-button v-if="canManage" type="primary" :icon="Plus" @click="openEdit()">新建文章</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" width="780px" top="6vh">
      <template #header>
        <div class="kb-dlg-head">
          <span>知识库</span>
          <span class="kb-crumb-sep">/</span>
          <span class="kb-crumb-cur">文章详情</span>
        </div>
      </template>

      <div v-if="current" class="kb-detail">
        <!-- 标题与标签 -->
        <h2 class="kb-title">{{ current.title }}</h2>
        <div class="kb-tags">
          <span class="kb-chip cat">{{ current.categoryName || '未分类' }}</span>
          <el-tag v-if="current.status !== 'PUBLISHED'" size="small" :type="statusType(current.status)" effect="light">
            {{ statusMap[current.status] }}
          </el-tag>
          <span class="kb-chip kw" v-for="k in keywordList" :key="k">{{ k }}</span>
        </div>

        <!-- 元信息行 -->
        <div class="kb-metarow">
          <span class="mi"><el-icon><View /></el-icon>{{ current.viewCount || 0 }} 次浏览</span>
          <span class="mi"><el-icon><Clock /></el-icon>更新于 {{ fmtTime(current.updatedAt || current.publishedAt || current.createdAt) }}</span>
          <span class="mi"><el-icon><UserFilled /></el-icon>{{ current.authorName || '系统知识库' }}</span>
        </div>

        <!-- 问题概述 -->
        <section class="kb-block" v-if="current.summary">
          <header class="kb-block-head"><el-icon><Document /></el-icon><span>问题概述</span></header>
          <p class="kb-summary">{{ current.summary }}</p>
        </section>

        <!-- 正文 -->
        <section class="kb-block">
          <header class="kb-block-head"><el-icon><Reading /></el-icon><span>正文内容</span></header>
          <div class="kb-body" v-html="renderMarkdown(current.body)"></div>
        </section>
      </div>

      <template #footer>
        <div class="kb-dlg-foot">
          <div class="kb-foot-left">
            <template v-if="canManage">
              <el-button type="primary" size="small" @click="openEdit(current)">编辑</el-button>
              <el-button type="danger" size="small" plain @click="doDelete">删除</el-button>
            </template>
          </div>
          <el-button size="small" @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, View, Clock, UserFilled, Document, Reading } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import api from '../api'

const auth = useAuthStore()
// 知识库维护权限：仅客服主管与系统管理员，客服与客户只读
const canManage = computed(() => ['ADMIN', 'SUPERVISOR'].includes(auth.role))

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

// 关键词按逗号拆分成标签
const keywordList = computed(() => {
  const raw = current.value?.keywords || ''
  return raw.split(/[,，;；]/).map(s => s.trim()).filter(Boolean)
})

// 行内 Markdown：转义 + 粗体 / 行内代码 / 链接
function renderInline(s) {
  return String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/\[(.+?)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
}

/**
 * 正文渲染：标题 / 段落 / 引用照常输出，
 * 连续的列表项（- 或 1.）合并成一个有序步骤块，配合样式渲染成带编号的步骤卡片
 */
function renderMarkdown(md) {
  if (!md) return '<p>暂无正文内容</p>'
  const lines = String(md).split(/\r?\n/)
  const html = []
  let listBuf = []
  let paraBuf = []

  const flushList = () => {
    if (!listBuf.length) return
    html.push('<ol class="step-list">' + listBuf.map(t => `<li><span>${t}</span></li>`).join('') + '</ol>')
    listBuf = []
  }
  const flushPara = () => {
    if (!paraBuf.length) return
    html.push('<p>' + paraBuf.join('<br/>') + '</p>')
    paraBuf = []
  }

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '')
    const item = line.match(/^\s*(?:[-*+]|\d+[.)、])\s+(.*)$/)
    if (item) { flushPara(); listBuf.push(renderInline(item[1])); continue }
    flushList()
    if (!line.trim()) { flushPara(); continue }
    if (/^#{1,6}\s+/.test(line)) {
      flushPara()
      const level = line.match(/^#+/)[0].length
      const text = renderInline(line.replace(/^#{1,6}\s+/, ''))
      html.push(level <= 2 ? `<h3>${text}</h3>` : `<h4>${text}</h4>`)
    } else if (/^>\s?/.test(line)) {
      flushPara()
      html.push(`<blockquote>${renderInline(line.replace(/^>\s?/, ''))}</blockquote>`)
    } else {
      paraBuf.push(renderInline(line))
    }
  }
  flushList()
  flushPara()
  return html.join('')
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
  // 编辑弹窗与详情弹窗互斥，避免两层叠加
  detailVisible.value = false
  editVisible.value = true
}

/* 删除文章（仅主管/管理员，后端同样校验并拒绝越权） */
async function doDelete() {
  if (!current.value) return
  try {
    await ElMessageBox.confirm(
      `确认删除文章「${current.value.title}」？删除后不可恢复。`,
      '删除文章',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  await api.delete(`/knowledge/${current.value.id}`)
  ElMessage.success('已删除')
  detailVisible.value = false
  load()
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

/* ===== 详情弹窗 ===== */
.kb-dlg-head { display: flex; align-items: center; gap: 6px; font-size: 12.5px; color: var(--text-muted); }
.kb-crumb-sep { color: #cbd5e1; }
.kb-crumb-cur { color: var(--text-main); font-weight: 600; }
.kb-detail { max-height: 64vh; overflow-y: auto; padding-right: 6px; }
.kb-title { margin: 0 0 10px; font-size: 20px; font-weight: 700; line-height: 1.45; color: #1e2c5c; }
.kb-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.kb-chip { font-size: 11.5px; padding: 2px 9px; border-radius: 999px; }
.kb-chip.cat { background: #eef2ff; color: #4f46e5; }
.kb-chip.kw { background: #f1f5f9; color: #64748b; }
.kb-metarow {
  display: flex; align-items: center; flex-wrap: wrap; gap: 16px;
  padding-bottom: 12px; margin-bottom: 14px;
  border-bottom: 1px solid #eef2f7;
}
.mi { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--text-muted); }
.kb-block { margin-bottom: 16px; }
.kb-block-head {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: #1e40af; margin-bottom: 8px;
}
.kb-summary {
  margin: 0; font-size: 13px; line-height: 1.8; color: #475569;
  background: #f8fafc; border-left: 3px solid #bfdbfe;
  padding: 10px 12px; border-radius: 0 8px 8px 0;
}
.kb-body { font-size: 13.5px; line-height: 1.85; color: #334155; }
.kb-body :deep(h3) { font-size: 14.5px; margin: 14px 0 8px; color: #1e2c5c; }
.kb-body :deep(h4) { font-size: 13.5px; margin: 12px 0 6px; color: #1e2c5c; }
.kb-body :deep(p) { margin: 0 0 10px; }
.kb-body :deep(strong) { color: #1e2c5c; }
.kb-body :deep(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.kb-body :deep(a) { color: var(--primary); }
.kb-body :deep(blockquote) {
  margin: 8px 0; padding: 8px 12px;
  border-left: 3px solid #c7d2fe; background: #f8faff;
  color: #475569; border-radius: 0 6px 6px 0;
}
/* 正文列表渲染为带编号的步骤卡片 */
.kb-body :deep(.step-list) {
  list-style: none; counter-reset: step;
  margin: 6px 0 12px; padding: 0;
  display: flex; flex-direction: column; gap: 8px;
}
.kb-body :deep(.step-list li) {
  counter-increment: step;
  position: relative;
  padding: 10px 12px 10px 42px;
  background: #f8fafc; border: 1px solid #eef2f7; border-radius: 10px;
  line-height: 1.7;
}
.kb-body :deep(.step-list li::before) {
  content: counter(step);
  position: absolute; left: 12px; top: 11px;
  width: 20px; height: 20px; border-radius: 50%;
  background: #2563eb; color: #fff;
  font-size: 11px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.kb-dlg-foot { display: flex; align-items: center; justify-content: space-between; }
.kb-foot-left { display: flex; gap: 8px; }
</style>
