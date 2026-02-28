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

// --- Smart Classification Views ---
import StrategyConfig from '../views/smart/StrategyConfig.vue'
import ClassificationDashboard from '../views/smart/ClassificationDashboard.vue'
import AdjustmentApproval from '../views/smart/AdjustmentApproval.vue'

// --- Warehouse Views ---
import StockInManage from '../views/warehouse/StockInManage.vue'
import LocationShelving from '../views/warehouse/LocationShelving.vue'
import StockLedger from '../views/warehouse/StockLedger.vue'

// --- 延迟按需加载的远端模块组件 ---
const RequisitionApply = () => import('../views/requisition/RequisitionApply.vue')
const RequisitionApproval = () => import('../views/requisition/RequisitionApproval.vue')
const RequisitionOutbound = () => import('../views/requisition/RequisitionOutbound.vue')
const RequisitionInstall = () => import('../views/requisition/RequisitionInstall.vue')
const RequisitionQuery = () => import('../views/requisition/RequisitionQuery.vue')

const WorkOrderReport = () => import('../views/workorder/WorkOrderReport.vue')
const WorkOrderAssign = () => import('../views/workorder/WorkOrderAssign.vue')
const WorkOrderProcess = () => import('../views/workorder/WorkOrderProcess.vue')
const WorkOrderComplete = () => import('../views/workorder/WorkOrderComplete.vue')
const WorkOrderQuery = () => import('../views/workorder/WorkOrderQuery.vue')

const PurchaseSuggestions = () => import('../views/purchase/PurchaseSuggestions.vue')
const PurchaseApply = () => import('../views/purchase/PurchaseApply.vue')
const PurchaseQuote = () => import('../views/purchase/PurchaseQuote.vue')
const PurchaseOrders = () => import('../views/purchase/PurchaseOrders.vue')
const PurchaseAcceptance = () => import('../views/purchase/PurchaseAcceptance.vue')

const Dashboard = () => import('../views/report/Dashboard.vue')
const InventoryReport = () => import('../views/report/InventoryReport.vue')
const ConsumptionReport = () => import('../views/report/ConsumptionReport.vue')
const SupplierReport = () => import('../views/report/SupplierReport.vue')
const MaintenanceReport = () => import('../views/report/MaintenanceReport.vue')
const WarningCenter = () => import('../views/report/WarningCenter.vue')

const AiForecastResult = () => import('../views/ai/AiForecastResult.vue')

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

        // --- Smart Classification Routes ---
        { path: 'smart/strategies', component: StrategyConfig, meta: { requiresAuth: true } },
        { path: 'smart/dashboard', component: ClassificationDashboard, meta: { requiresAuth: true } },
        { path: 'smart/approvals', component: AdjustmentApproval, meta: { requiresAuth: true } },

        // --- Warehouse Routes ---
        { path: 'warehouse/stock-in', component: StockInManage, meta: { requiresAuth: true } },
        { path: 'warehouse/shelving', component: LocationShelving, meta: { requiresAuth: true } },
        { path: 'warehouse/ledger', component: StockLedger, meta: { requiresAuth: true } },

        // --- Requisition Routes ---
        { path: 'requisition-apply', component: RequisitionApply, meta: { requiresAuth: true } },
        { path: 'requisition-approval', component: RequisitionApproval, meta: { requiresAuth: true } },
        { path: 'requisition-outbound', component: RequisitionOutbound, meta: { requiresAuth: true } },
        { path: 'requisition-install', component: RequisitionInstall, meta: { requiresAuth: true } },
        { path: 'requisition-query', component: RequisitionQuery, meta: { requiresAuth: true } },

        // --- Work Order Routes ---
        { path: 'work-order-report', component: WorkOrderReport, meta: { requiresAuth: true } },
        { path: 'work-order-assign', component: WorkOrderAssign, meta: { requiresAuth: true } },
        { path: 'work-order-process', component: WorkOrderProcess, meta: { requiresAuth: true } },
        { path: 'work-order-complete', component: WorkOrderComplete, meta: { requiresAuth: true } },
        { path: 'work-order-query', component: WorkOrderQuery, meta: { requiresAuth: true } },

        // --- Purchase Routes ---
        { path: 'purchase-suggestions', component: PurchaseSuggestions, meta: { requiresAuth: true } },
        { path: 'purchase-apply', component: PurchaseApply, meta: { requiresAuth: true } },
        { path: 'purchase-quote', component: PurchaseQuote, meta: { requiresAuth: true } },
        { path: 'purchase-orders', component: PurchaseOrders, meta: { requiresAuth: true } },
        { path: 'purchase-acceptance', component: PurchaseAcceptance, meta: { requiresAuth: true } },

        // --- Report Routes ---
        { path: 'report-dashboard', component: Dashboard, meta: { requiresAuth: true } },
        { path: 'report-inventory', component: InventoryReport, meta: { requiresAuth: true } },
        { path: 'report-consumption', component: ConsumptionReport, meta: { requiresAuth: true } },
        { path: 'report-supplier', component: SupplierReport, meta: { requiresAuth: true } },
        { path: 'report-maintenance', component: MaintenanceReport, meta: { requiresAuth: true } },
        { path: 'warning-center', component: WarningCenter, meta: { requiresAuth: true } }
      ]
    },
    // AI 智能分析模块 (在 /ai 目录下展示为独立的根菜单)
    {
      path: '/ai',
      component: Home,
      meta: { requiresAuth: true },
      children: [
        { path: 'forecast-result', component: AiForecastResult, meta: { requiresAuth: true } }
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
