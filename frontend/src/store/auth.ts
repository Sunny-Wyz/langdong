import { defineStore } from 'pinia'
import { ref } from 'vue'

// 使用 Pinia 管理用户凭证与权限菜单状态
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const username = ref<string>(localStorage.getItem('username') || '')
  const menus = ref<any[]>([])
  const permissions = ref<string[]>([])

  function setToken(newToken: string, user: string) {
    token.value = newToken
    username.value = user
    localStorage.setItem('token', newToken)
    localStorage.setItem('username', user)
  }

  function setMenusAndPermissions(newMenus: any[], newPermissions: string[]) {
    menus.value = newMenus
    permissions.value = newPermissions
  }

  function logout() {
    token.value = ''
    username.value = ''
    menus.value = []
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  return {
    token,
    username,
    menus,
    permissions,
    setToken,
    setMenusAndPermissions,
    logout
  }
})
