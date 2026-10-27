import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'tickets', name: 'tickets', component: () => import('../views/Tickets.vue'), meta: { title: '工单管理' } },
      // 必须排在 tickets/:id 之前，否则 hall 会被当成工单 id 匹配
      { path: 'tickets/hall', name: 'ticket-hall', component: () => import('../views/TicketsHall.vue'), meta: { title: '工单大厅' } },
      { path: 'tickets/:id', name: 'ticket-detail', component: () => import('../views/TicketDetail.vue'), meta: { title: '工单详情' } },
      { path: 'customers', name: 'customers', component: () => import('../views/Customers.vue'), meta: { title: '客户管理' } },
      { path: 'knowledge', name: 'knowledge', component: () => import('../views/Knowledge.vue'), meta: { title: '知识库' } },
      { path: 'chat', name: 'chat', component: () => import('../views/Chat.vue'), meta: { title: '智能问答' } },
      { path: 'stats', name: 'stats', component: () => import('../views/Stats.vue'), meta: { title: '数据统计' } },
      { path: 'system', name: 'system', component: () => import('../views/System.vue'), meta: { title: '系统管理' } },
      { path: 'profile', name: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!auth.isLoggedIn) {
    next('/login')
    return
  }
  // 本次会话首次导航时向后端同步菜单，避免沿用浏览器里缓存的旧菜单
  if (!auth.menusLoaded) {
    await auth.refreshMenus()
  }
  // 角色权限守卫：菜单由后端按角色下发，手输越权路径时拦截（个人中心所有角色可用）
  // 用前缀匹配，让 /tickets/123 这类子路径跟随父级菜单放行
  if (to.name !== 'profile' && to.path !== '/') {
    const allowed = auth.menuPaths || []
    const permitted = allowed.some(p => to.path === p || to.path.startsWith(p + '/'))
    if (allowed.length && !permitted) {
      next('/dashboard')
      return
    }
  }
  next()
})

export default router
