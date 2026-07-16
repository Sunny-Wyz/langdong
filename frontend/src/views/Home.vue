<template>
  <el-container class="home-layout">
    <!-- 侧边栏 -->
    <el-aside width="210px" class="home-aside">
      <div class="logo">备件管理系统</div>
      <el-menu :default-active="route.path" router background-color="#0f3086" text-color="rgba(255,255,255,0.7)"
        active-text-color="#ffffff">
        <template v-for="menu in menus">

          <!-- 有子菜单的呈现为 el-sub-menu -->
          <el-sub-menu v-if="menu.children && menu.children.some((c: any) => c.type === 2)" :index="menu.id.toString()"
            :key="'sub-' + menu.id">
            <template #title>
              <span class="menu-icon">{{ getMenuIconEmoji(menu.icon) }}</span>
              <span>{{ displayMenuName(menu) }}</span>
            </template>
            <!-- 第二级 -->
            <template v-slot:default>
              <template v-for="child in menu.children">
                <!-- 如果子项还有可导航的三级菜单，渲染为二级submenu -->
                <el-sub-menu v-if="child.type === 2 && child.children && child.children.some((c: any) => c.type === 2)"
                  :index="child.id.toString()" :key="'child-sub-' + child.id">
                  <template #title>
                    <span class="menu-icon">{{ getMenuIconEmoji(child.icon) }}</span>
                    <span>{{ child.name }}</span>
                  </template>
                    <template v-for="grandchild in child.children" :key="'gc-' + grandchild.id">
                      <el-menu-item v-if="grandchild.type === 2" :index="grandchild.path">
                        <span>{{ grandchild.name }}</span>
                      </el-menu-item>
                    </template>
                </el-sub-menu>
                <!-- 否则直接渲染为菜单项 -->
                <el-menu-item v-else-if="child.type === 2" :index="child.path" :key="'child-' + child.id">
                  <span class="menu-icon">{{ getMenuIconEmoji(child.icon) }}</span>
                  <span>{{ child.name }}</span>
                </el-menu-item>
              </template>
            </template>
          </el-sub-menu>

          <!-- 没子菜单的是直接的 el-menu-item 根节点 -->
          <el-menu-item v-else-if="menu.type === 2" :index="menu.path" :key="'menu-' + menu.id">
            <span class="menu-icon">{{ getMenuIconEmoji(menu.icon) }}</span>
            <template #title>{{ menu.name }}</template>
          </el-menu-item>

        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="home-header">
        <span class="header-user">{{ username }}</span>
        <el-button type="primary" link @click="logout">🚪 退出登录</el-button>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="home-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = computed(() => authStore.username)
const menus = computed(() => authStore.menus)

function displayMenuName(menu: any) {
  if (menu && menu.path === '/ai') {
    return '需求预测与辅助决策模块'
  }
  return menu && menu.name ? menu.name : ''
}

// 辅助方法，将 Element UI icon 类名映射为美观的 emoji 图标，保证 Vue 3 下一致渲染
function getMenuIconEmoji(iconClass: string | null) {
  if (!iconClass) return '📄'
  if (iconClass.includes('setting')) return '⚙️'
  if (iconClass.includes('box')) return '📦'
  if (iconClass.includes('goods') || iconClass.includes('shopping')) return '🛒'
  if (iconClass.includes('data') || iconClass.includes('chart')) return '📊'
  if (iconClass.includes('folder')) return '📁'
  if (iconClass.includes('user')) return '👤'
  if (iconClass.includes('lock')) return '🔒'
  return '📄'
}

async function fetchMenus() {
  try {
    const res = await request.get('/menus/my')
    const fetchedMenus = res.data || []

    // 解析所有的按钮级别权限 (type === 3) 提取 permission 字符串并写入 Pinia Store
    const permissions: string[] = []
    const extractPerms = (nodes: any[]) => {
      nodes.forEach(n => {
        if (n.type === 3 && n.permission) {
          permissions.push(n.permission)
        }
        if (n.children && n.children.length > 0) {
          extractPerms(n.children)
        }
      })
    }
    extractPerms(fetchedMenus)

    authStore.setMenusAndPermissions(fetchedMenus, permissions)
  } catch (e: any) {
    console.error('动态拉取菜单失败', e)
    if (e.response && e.response.status === 401) {
      logout()
    }
  }
}

function logout() {
  authStore.logout()
  router.push('/login')
}

onMounted(() => {
  fetchMenus()
})
</script>

<style scoped>
.home-layout {
  height: 100vh;
}

.home-aside {
  background-color: #0f3086;
}

.logo {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-align: center;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.menu-icon {
  margin-right: 8px;
  font-size: 14px;
}

.home-header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 24px;
}

.header-user {
  margin-right: 12px;
  color: #606266;
}

.home-main {
  background: #f1f2f6;
  padding: 0;
}
</style>
