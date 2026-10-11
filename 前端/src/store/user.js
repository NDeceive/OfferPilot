import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: Number(localStorage.getItem('userId')) || null,
    username: localStorage.getItem('username') || '',
    nickname: localStorage.getItem('nickname') || '',
    role: localStorage.getItem('role') || '',
    avatar: '',
    profileLoaded: false,
  }),

  getters: {
    isLogin: (state) => !!state.token,
    isTeacher: (state) => state.role === 'TEACHER' || state.role === 'ADMIN',
  },

  actions: {
    syncProfile(user) {
      this.nickname = user.nickname || user.username || ''
      this.avatar = user.avatar || ''
      this.profileLoaded = true
      localStorage.setItem('nickname', this.nickname)
    },
    setAuth(data) {
      this.avatar = ''
      this.profileLoaded = false
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.nickname = data.nickname || ''
      this.role = data.role
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('nickname', data.nickname || '')
      localStorage.setItem('role', data.role)
    },

    logout() {
      this.avatar = ''
      this.profileLoaded = false
      this.token = ''
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.role = ''
      // 只删自己这五个键，不用 localStorage.clear()——clear() 会把别人的数据一起清掉：
      // 专项训练的本地会话（offerpilot.learning.session）、运行时服务器地址、
      // 离线演示开关，退出登录后全都消失了。
      const authKeys = ['token', 'userId', 'username', 'nickname', 'role']
      authKeys.forEach((key) => localStorage.removeItem(key))
    },
  },
})
