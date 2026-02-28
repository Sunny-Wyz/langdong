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
import LocationShelving from '../views/warehouse/LocationShelving.vue'

import RequisitionApply from '../views/requisition/RequisitionApply.vue'
import RequisitionApproval from '../views/requisition/RequisitionApproval.vue'
import RequisitionOutbound from '../views/requisition/RequisitionOutbound.vue'
import RequisitionInstall from '../views/requisition/RequisitionInstall.vue'
import RequisitionQuery from '../views/requisition/RequisitionQuery.vue'

import WorkOrderReport from '../views/workorder/WorkOrderReport.vue'
import WorkOrderAssign from '../views/workorder/WorkOrderAssign.vue'
import WorkOrderProcess from '../views/workorder/WorkOrderProcess.vue'
import WorkOrderComplete from '../views/workorder/WorkOrderComplete.vue'
import WorkOrderQuery from '../views/workorder/WorkOrderQuery.vue'

import PurchaseSuggestions from '../views/purchase/PurchaseSuggestions.vue'
import PurchaseApply from '../views/purchase/PurchaseApply.vue'
import PurchaseQuote from '../views/purchase/PurchaseQuote.vue'
import PurchaseOrders from '../views/purchase/PurchaseOrders.vue'
import PurchaseAcceptance from '../views/purchase/PurchaseAcceptance.vue'

// 备件智能分类模块
import ClassifyResult from '../views/classify/ClassifyResult.vue'

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
        { path: 'stock-ledger', component: StockLedger, meta: { requiresAuth: true } },
        { path: 'shelving', component: LocationShelving, meta: { requiresAuth: true } },
        // 领用管理模块 - 5个子模块
        { path: 'requisition-apply', component: RequisitionApply, meta: { requiresAuth: true } },
        { path: 'requisition-approval', component: RequisitionApproval, meta: { requiresAuth: true } },
        { path: 'requisition-outbound', component: RequisitionOutbound, meta: { requiresAuth: true } },
        { path: 'requisition-install', component: RequisitionInstall, meta: { requiresAuth: true } },
        { path: 'requisition-query', component: RequisitionQuery, meta: { requiresAuth: true } },
        // 维修工单管理模块 - 5个子模块
        { path: 'work-order-report', component: WorkOrderReport, meta: { requiresAuth: true } },
        { path: 'work-order-assign', component: WorkOrderAssign, meta: { requiresAuth: true } },
        { path: 'work-order-process', component: WorkOrderProcess, meta: { requiresAuth: true } },
        { path: 'work-order-complete', component: WorkOrderComplete, meta: { requiresAuth: true } },
        { path: 'work-order-query', component: WorkOrderQuery, meta: { requiresAuth: true } },
        // 采购管理模块 - 5个子模块
        { path: 'purchase-suggestions', component: PurchaseSuggestions, meta: { requiresAuth: true } },
        { path: 'purchase-apply', component: PurchaseApply, meta: { requiresAuth: true } },
        { path: 'purchase-quote', component: PurchaseQuote, meta: { requiresAuth: true } },
        { path: 'purchase-orders', component: PurchaseOrders, meta: { requiresAuth: true } },
        { path: 'purchase-acceptance', component: PurchaseAcceptance, meta: { requiresAuth: true } }
      ]
    },
    // 备件智能分类模块（对应菜单 id=11，path=/smart）
    {
      path: '/smart',
      component: Home,
      meta: { requiresAuth: true },
      children: [
        { path: 'classify-result', component: ClassifyResult, meta: { requiresAuth: true } }
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
