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
      { path: 'customers', name: 'customers', component: () => import('../views/Customers.vue'), meta: { title: '客户管理' } },
      { path: 'knowledge', name: 'knowledge', component: () => import('../views/Knowledge.vue'), meta: { title: '知识库' } },
      { path: 'chat', name: 'chat', component: () => import('../views/Chat.vue'), meta: { title: '智能问答' } },
      { path: 'stats', name: 'stats', component: () => import('../views/Stats.vue'), meta: { title: '数据统计' } },
      { path: 'system', name: 'system', component: () => import('../views/System.vue'), meta: { title: '系统管理' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!auth.isLoggedIn) {
    next('/login')
    return
  }
  next()
})

export default router
