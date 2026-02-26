<template>
  <el-container style="height: 100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background-color: #304156">
      <div class="logo">备件管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#304156" text-color="#bfcbd9"
        active-text-color="#409EFF">
        <template v-for="menu in menus">

          <!-- 有子菜单的呈现为 el-submenu -->
          <el-submenu v-if="menu.children && menu.children.length > 0" :index="menu.id.toString()"
            :key="'sub-' + menu.id">
            <template slot="title">
              <i :class="menu.icon || 'el-icon-folder'"></i>
              <span>{{ menu.name }}</span>
            </template>
            <!-- 第二级 -->
            <template v-for="child in menu.children">
              <el-menu-item v-if="child.type === 2" :index="child.path" :key="'child-' + child.id">
                <i :class="child.icon || 'el-icon-document'"></i>
                <span>{{ child.name }}</span>
              </el-menu-item>
            </template>
          </el-submenu>

          <!-- 没子菜单的是直接的 el-menu-item 根节点 -->
          <el-menu-item v-else-if="menu.type === 2" :index="menu.path" :key="'menu-' + menu.id">
            <i :class="menu.icon || 'el-icon-document'"></i>
            <span slot="title">{{ menu.name }}</span>
          </el-menu-item>

          <!-- 目录类型但暂无子菜单（模块待开发）-->
          <el-menu-item v-else-if="menu.type === 1" :index="menu.id.toString()" :key="'dir-' + menu.id" disabled>
            <i :class="menu.icon || 'el-icon-folder'"></i>
            <span slot="title">{{ menu.name }} <el-tag size="mini" type="info">开发中</el-tag></span>
          </el-menu-item>

        </template>
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
    },
    menus() {
      return this.$store.state.menus
    }
  },
  created() {
    this.fetchMenus()
  },
  methods: {
    async fetchMenus() {
      try {
        const res = await this.$http.get('/menus/my')
        const menus = res.data || []

        // 解析所有的按钮级别权限 (type === 3) 提取 permission 字符串注入到 local state
        const permissions = []
        const extractPerms = (nodes) => {
          nodes.forEach(n => {
            if (n.type === 3 && n.permission) {
              permissions.push(n.permission)
            }
            if (n.children && n.children.length > 0) {
              extractPerms(n.children)
            }
          })
        }
        extractPerms(menus)

        this.$store.commit('SET_MENUS_AND_PERMISSIONS', { menus, permissions })
      } catch (e) {
        console.error('动态拉取菜单失败', e)
        if (e.response && e.response.status === 401) {
          this.logout()
        }
      }
    },
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
