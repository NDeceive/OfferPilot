import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: Number(localStorage.getItem('userId')) || null,
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    role: localStorage.getItem('role') || ''
  }),
  getters: {
    isLogin: (state) => !!state.token
  },
  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.nickname = data.nickname
      this.role = data.role
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('nickname', data.nickname || '')
      localStorage.setItem('role', data.role)
      return data
    },
    async register(form) {
      return registerApi(form)
    },
    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.role = ''
      localStorage.clear()
    }
  }
})
