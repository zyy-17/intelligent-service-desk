import { defineStore } from 'pinia'
import api from '../api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null'),
    menus: JSON.parse(localStorage.getItem('menus') || '[]')
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
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      localStorage.setItem('menus', JSON.stringify(this.menus))
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      this.menus = []
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('menus')
    }
  }
})
