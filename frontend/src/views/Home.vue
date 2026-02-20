<template>
  <div style="padding: 40px; text-align: center">
    <h1>欢迎回来，{{ username }}</h1>
    <p style="color: #999; margin-top: 16px">备件管理系统</p>
    <el-button type="danger" style="margin-top: 32px" @click="logout">退出登录</el-button>
  </div>
</template>

<script>
export default {
  computed: {
    username() {
      return this.$store.state.username
    }
  },
  methods: {
    logout() {
      this.$store.commit('LOGOUT')
      this.$router.push('/login')
    }
  }
}
</script>
