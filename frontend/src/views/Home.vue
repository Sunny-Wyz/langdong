<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background-color: #304156">
      <div class="logo">备件管理系统</div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/home/spare-parts">
          <i class="el-icon-s-order" />
          <span>备件管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: flex-end; padding: 0 24px">
        <span style="margin-right: 12px; color: #606266">{{ username }}</span>
        <el-button type="text" icon="el-icon-switch-button" @click="logout">退出登录</el-button>
      </el-header>

      <!-- 主内容区 -->
      <el-main style="background: #f0f2f5; padding: 0">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
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

<style scoped>
.logo {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}
</style>
