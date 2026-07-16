<template>
  <el-container class="home-layout">
    <!-- 侧边栏 -->
    <el-aside width="210px" class="home-aside">
      <div class="logo">备件管理系统</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#0f3086"
        text-color="rgba(255,255,255,0.7)"
        active-text-color="#ffffff"
      >
        <template v-for="menu in menus" :key="'menu-' + menu.id">
          <!-- 有可导航子菜单：渲染为 el-sub-menu -->
          <el-sub-menu
            v-if="hasNavChildren(menu)"
            :index="String(menu.id)"
          >
            <template #title>
              <span class="menu-icon">{{ getMenuIconEmoji(menu.icon) }}</span>
              <span>{{ displayMenuName(menu) }}</span>
            </template>

            <template v-for="child in menu.children" :key="'child-' + child.id">
              <!-- 三级：子项自身还有可导航子菜单 -->
              <el-sub-menu
                v-if="child.type === 2 && hasNavChildren(child)"
                :index="String(child.id)"
              >
                <template #title>
                  <span class="menu-icon">{{ getMenuIconEmoji(child.icon) }}</span>
                  <span>{{ child.name }}</span>
                </template>
                <template v-for="grandchild in child.children" :key="'gc-' + grandchild.id">
                  <el-menu-item
                    v-if="grandchild.type === 2 && grandchild.path"
                    :index="normalizeMenuPath(grandchild.path)"
                  >
                    <span>{{ grandchild.name }}</span>
                  </el-menu-item>
                </template>
              </el-sub-menu>

              <!-- 二级：直接可导航菜单项 -->
              <el-menu-item
                v-else-if="child.type === 2 && child.path"
                :index="normalizeMenuPath(child.path)"
              >
                <span class="menu-icon">{{ getMenuIconEmoji(child.icon) }}</span>
                <span>{{ child.name }}</span>
              </el-menu-item>
            </template>
          </el-sub-menu>

          <!-- 无子菜单的根级可导航项 -->
          <el-menu-item
            v-else-if="menu.type === 2 && menu.path"
            :index="normalizeMenuPath(menu.path)"
          >
            <span class="menu-icon">{{ getMenuIconEmoji(menu.icon) }}</span>
            <span>{{ menu.name }}</span>
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

interface MenuNode {
  id: number | string
  name?: string
  path?: string | null
  icon?: string | null
  type?: number
  children?: MenuNode[]
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const username = computed(() => authStore.username)
const menus = computed<MenuNode[]>(() => authStore.menus || [])

/** hash 路由下 el-menu 的 active 索引使用完整 path */
const activeMenu = computed(() => route.path)

function hasNavChildren(menu: MenuNode | null | undefined): boolean {
  return Boolean(menu?.children?.some((c) => c.type === 2 && (c.path || hasNavChildren(c))))
}

/** 统一菜单 path：去掉 hash 前缀、补全前导斜杠 */
function normalizeMenuPath(path: string | null | undefined): string {
  if (!path) return ''
  let p = String(path).trim()
  if (p.startsWith('#')) p = p.slice(1)
  if (!p.startsWith('/')) p = `/${p}`
  return p
}

function displayMenuName(menu: MenuNode): string {
  if (menu?.path === '/ai') return '需求预测与辅助决策模块'
  return menu?.name || ''
}

/** 将 Element UI icon 类名映射为 emoji，保证 Vue 3 下一致渲染 */
function getMenuIconEmoji(iconClass: string | null | undefined): string {
  if (!iconClass) return '📄'
  if (iconClass.includes('setting')) return '⚙️'
  if (iconClass.includes('box')) return '📦'
  if (iconClass.includes('goods') || iconClass.includes('shopping')) return '🛒'
  if (iconClass.includes('data') || iconClass.includes('chart') || iconClass.includes('board')) return '📊'
  if (iconClass.includes('folder') || iconClass.includes('collection')) return '📁'
  if (iconClass.includes('user')) return '👤'
  if (iconClass.includes('lock') || iconClass.includes('key')) return '🔒'
  if (iconClass.includes('cpu')) return '🤖'
  if (iconClass.includes('tools') || iconClass.includes('odometer')) return '🔧'
  if (iconClass.includes('sell') || iconClass.includes('sold')) return '📤'
  if (iconClass.includes('truck')) return '🚚'
  return '📄'
}

async function fetchMenus() {
  try {
    const res = await request.get('/menus/my')
    const fetchedMenus: MenuNode[] = res.data || []

    // 解析按钮级权限 (type === 3)
    const permissions: string[] = []
    const extractPerms = (nodes: MenuNode[]) => {
      nodes.forEach((n) => {
        if (n.type === 3 && (n as any).permission) {
          permissions.push((n as any).permission)
        }
        if (n.children?.length) extractPerms(n.children)
      })
    }
    extractPerms(fetchedMenus)

    authStore.setMenusAndPermissions(fetchedMenus, permissions)
  } catch (e: any) {
    console.error('动态拉取菜单失败', e)
    if (e?.response?.status === 401) {
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
  overflow-y: auto;
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
