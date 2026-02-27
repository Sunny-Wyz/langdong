import Vue from 'vue'
import VueRouter from 'vue-router'
import Login from '../views/Login.vue'
import Home from '../views/Home.vue'
import SparePartList from '../views/SparePartList.vue'
import LocationProfile from '../views/LocationProfile.vue'
import EquipmentProfile from '../views/EquipmentProfile.vue'
import SupplierProfile from '../views/SupplierProfile.vue'
import SupplyCategory from '../views/SupplyCategory.vue'
import UserManage from '../views/sys/UserManage.vue'
import RoleManage from '../views/sys/RoleManage.vue'

import StockInManage from '../views/warehouse/StockInManage.vue'
import StockLedger from '../views/warehouse/StockLedger.vue'

Vue.use(VueRouter)

// Fix 'Avoided redundant navigation to current location' warning
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => Object.assign({}, err, { isNavigationFailure: true }))
}
const originalReplace = VueRouter.prototype.replace
VueRouter.prototype.replace = function replace(location) {
  return originalReplace.call(this, location).catch(err => Object.assign({}, err, { isNavigationFailure: true }))
}

const router = new VueRouter({
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login },
    {
      path: '/home',
      component: Home,
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: 'spare-parts' },
        { path: 'spare-parts', component: SparePartList, meta: { requiresAuth: true } },
        { path: 'location-profiles', component: LocationProfile, meta: { requiresAuth: true } },
        { path: 'equipment-profiles', component: EquipmentProfile, meta: { requiresAuth: true } },
        { path: 'supplier-profiles', component: SupplierProfile, meta: { requiresAuth: true } },
        { path: 'supply-categories', component: SupplyCategory, meta: { requiresAuth: true } },
        { path: 'stock-in', component: StockInManage, meta: { requiresAuth: true } },
        { path: 'stock-ledger', component: StockLedger, meta: { requiresAuth: true } }
      ]
    },
    {
      path: '/sys',
      component: Home,
      meta: { requiresAuth: true },
      children: [
        { path: 'users', component: UserManage, meta: { requiresAuth: true } },
        { path: 'roles', component: RoleManage, meta: { requiresAuth: true } }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) {
    next('/login')
  } else {
    next()
  }
})

export default router
