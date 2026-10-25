import { defineStore } from 'pinia'
import api from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    menus: JSON.parse(localStorage.getItem('menus') || '[]'),
    // 本次会话是否已从后端同步过菜单（内存态，刷新页面后重置，用于启动时拉取最新菜单）
    menusLoaded: false
  }),
  getters: {
    isLoggedIn: state => !!state.token,
    role: state => state.user?.role || '',
    displayName: state => state.user?.displayName || '',
    menuPaths: state => state.menus.map(m => m.path)
  },
  actions: {
    async login(username, password) {
      const data = await api.post('/auth/login', { username, password })
      this._applyAuth(data)
      return data.user
    },
    async register(payload) {
      const data = await api.post('/auth/register', payload)
      this._applyAuth(data)
      return data.user
    },
    _applyAuth(data) {
      this.token = data.token
      this.user = data.user
      this.menus = data.menus || []
      this.menusLoaded = true
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      localStorage.setItem('menus', JSON.stringify(this.menus))
    },
    // 从后端重新拉取菜单并覆盖本地缓存（后端改菜单后无需退出重登）
    async refreshMenus() {
      if (!this.token) {
        this.menusLoaded = true
        return
      }
      try {
        const list = await api.get('/auth/menus')
        this.menus = list || []
        localStorage.setItem('menus', JSON.stringify(this.menus))
      } catch (e) {
        // 拉取失败时沿用本地缓存，不阻断页面
      } finally {
        this.menusLoaded = true
      }
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      this.menus = []
      this.menusLoaded = false
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('menus')
    }
  }
})
