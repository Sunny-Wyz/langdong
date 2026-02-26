<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background-color: #304156">
      <div class="logo">备件管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#304156" text-color="#bfcbd9"
        active-text-color="#409EFF">
        <el-submenu index="base-data">
          <template slot="title">
            <i class="el-icon-suitcase-1"></i>
            <span>基础数据管理</span>
          </template>
          <el-menu-item index="/home/spare-parts">
            <i class="el-icon-s-order"></i>
            <span>备件管理</span>
          </el-menu-item>
        </el-submenu>

        <el-submenu index="smart-classification">
          <template slot="title">
            <i class="el-icon-collection"></i>
            <span>备件智能分类模块</span>
          </template>
        </el-submenu>

        <el-submenu index="warehouse-management">
          <template slot="title">
            <i class="el-icon-box"></i>
            <span>仓储管理模块</span>
          </template>
        </el-submenu>

        <el-submenu index="requisition-management">
          <template slot="title">
            <i class="el-icon-sell"></i>
            <span>领用管理模块</span>
          </template>
        </el-submenu>

        <el-submenu index="maintenance-orders">
          <template slot="title">
            <i class="el-icon-s-tools"></i>
            <span>维修工单管理模块</span>
          </template>
        </el-submenu>

        <el-submenu index="procurement-management">
          <template slot="title">
            <i class="el-icon-shopping-cart-full"></i>
            <span>采购管理模块</span>
          </template>
        </el-submenu>

        <el-submenu index="ai-analysis">
          <template slot="title">
            <i class="el-icon-cpu"></i>
            <span>AI智能分析模块</span>
          </template>
        </el-submenu>

        <el-submenu index="reports-dashboard">
          <template slot="title">
            <i class="el-icon-data-board"></i>
            <span>报表与看板模块</span>
          </template>
        </el-submenu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header
        style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: flex-end; padding: 0 24px">
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
