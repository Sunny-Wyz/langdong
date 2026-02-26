import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || '',
    menus: [],
    permissions: []
  },
  mutations: {
    SET_TOKEN(state, { token, username }) {
      state.token = token
      state.username = username
      localStorage.setItem('token', token)
      localStorage.setItem('username', username)
    },
    SET_MENUS_AND_PERMISSIONS(state, { menus, permissions }) {
      state.menus = menus
      state.permissions = permissions
    },
    LOGOUT(state) {
      state.token = ''
      state.username = ''
      state.menus = []
      state.permissions = []
      localStorage.removeItem('token')
      localStorage.removeItem('username')
    }
  }
})
