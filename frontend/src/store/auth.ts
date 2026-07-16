import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

// 使用 Pinia Setup Store 语法定义全局认证存储（双 Token 机制）
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>(localStorage.getItem('accessToken') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const username = ref<string>(localStorage.getItem('username') || '')
  const menus = ref<any[]>([])
  const permissions = ref<string[]>([])

  // 存储新的双 Token 和登录用户名
  function setTokens(access: string, refresh: string, user: string) {
    accessToken.value = access
    refreshToken.value = refresh
    username.value = user
    localStorage.setItem('accessToken', access)
    localStorage.setItem('refreshToken', refresh)
    localStorage.setItem('username', user)
  }

  // 存储侧边菜单和按钮权限列表
  function setMenusAndPermissions(newMenus: any[], newPermissions: string[]) {
    menus.value = newMenus
    permissions.value = newPermissions
  }

  // 静默刷新 Access Token，通过原始的 axios 发送请求，防止陷入拦截器死循环
  async function refreshAccessToken(): Promise<boolean> {
    if (!refreshToken.value) {
      logout()
      return false
    }

    try {
      const response = await axios.post('/api/v1/auth/refresh', {
        refreshToken: refreshToken.value
      }, {
        timeout: 5000
      })

      if (response.status === 200 && response.data?.accessToken) {
        const newAccess = response.data.accessToken
        accessToken.value = newAccess
        localStorage.setItem('accessToken', newAccess)
        return true
      }
    } catch (error) {
      console.error('[Token 刷新失败] 可能是刷新令牌过期或网络异常:', error)
    }

    logout()
    return false
  }

  // 清理用户凭证与权限，登出系统
  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    username.value = ''
    menus.value = []
    permissions.value = []
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
  }

  return {
    accessToken,
    refreshToken,
    username,
    menus,
    permissions,
    setTokens,
    setMenusAndPermissions,
    refreshAccessToken,
    logout
  }
})
