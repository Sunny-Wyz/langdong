import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../store/auth'

// 懒加载组件定义
const Login = () => import('../views/Login.vue')
const Home = () => import('../views/Home.vue')
const SparePartList = () => import('../views/SparePartList.vue')
const LocationProfile = () => import('../views/LocationProfile.vue')
const EquipmentProfile = () => import('../views/EquipmentProfile.vue')
const SupplierProfile = () => import('../views/SupplierProfile.vue')
const SupplyCategory = () => import('../views/SupplyCategory.vue')
const UserManage = () => import('../views/sys/UserManage.vue')
const RoleManage = () => import('../views/sys/RoleManage.vue')

const StockInManage = () => import('../views/warehouse/StockInManage.vue')
const StockLedger = () => import('../views/warehouse/StockLedger.vue')
const LocationShelving = () => import('../views/warehouse/LocationShelving.vue')

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

// 报表与看板模块
const Dashboard = () => import('../views/report/Dashboard.vue')
const InventoryReport = () => import('../views/report/InventoryReport.vue')
const ConsumptionReport = () => import('../views/report/ConsumptionReport.vue')
const SupplierReport = () => import('../views/report/SupplierReport.vue')
const MaintenanceReport = () => import('../views/report/MaintenanceReport.vue')
const WarningCenter = () => import('../views/report/WarningCenter.vue')

// 备件智能分类模块
const ClassifyResult = () => import('../views/classify/ClassifyResult.vue')

// AI 智能分析模块
const AiForecastResult = () => import('../views/ai/AiForecastResult.vue')
const AiJobCenter = () => import('../views/ai/AiJobCenter.vue')
const AiTrainDataDashboard = () => import('../views/ai/AiTrainDataDashboard.vue')
const WeeklyForecastResult = () => import('../views/ai/WeeklyForecastResult.vue')
const AiTrainingProgress = () => import('../views/ai/AiTrainingProgress.vue')

// PHM 预测性维护模块
const HealthMonitor = () => import('../views/phm/HealthMonitor.vue')
const FaultPrediction = () => import('../views/phm/FaultPrediction.vue')
const MaintenanceSuggestion = () => import('../views/phm/MaintenanceSuggestion.vue')

const routes: Array<RouteRecordRaw> = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  {
    path: '/home',
    component: Home,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/home/spare-parts' },
      { path: 'spare-parts', component: SparePartList, meta: { requiresAuth: true } },
      { path: 'location-profiles', component: LocationProfile, meta: { requiresAuth: true } },
      { path: 'equipment-profiles', component: EquipmentProfile, meta: { requiresAuth: true } },
      { path: 'supplier-profiles', component: SupplierProfile, meta: { requiresAuth: true } },
      { path: 'supply-categories', component: SupplyCategory, meta: { requiresAuth: true } },
      { path: 'stock-in', component: StockInManage, meta: { requiresAuth: true } },
      { path: 'stock-ledger', component: StockLedger, meta: { requiresAuth: true } },
      { path: 'shelving', component: LocationShelving, meta: { requiresAuth: true } },
      
      // 领用管理模块
      { path: 'requisition-apply', component: RequisitionApply, meta: { requiresAuth: true } },
      { path: 'requisition-approval', component: RequisitionApproval, meta: { requiresAuth: true } },
      { path: 'requisition-outbound', component: RequisitionOutbound, meta: { requiresAuth: true } },
      { path: 'requisition-install', component: RequisitionInstall, meta: { requiresAuth: true } },
      { path: 'requisition-query', component: RequisitionQuery, meta: { requiresAuth: true } },
      
      // 维修工单管理模块
      { path: 'work-order-report', component: WorkOrderReport, meta: { requiresAuth: true } },
      { path: 'work-order-assign', component: WorkOrderAssign, meta: { requiresAuth: true } },
      { path: 'work-order-process', component: WorkOrderProcess, meta: { requiresAuth: true } },
      { path: 'work-order-complete', component: WorkOrderComplete, meta: { requiresAuth: true } },
      { path: 'work-order-query', component: WorkOrderQuery, meta: { requiresAuth: true } },
      
      // 采购管理模块
      { path: 'purchase-suggestions', component: PurchaseSuggestions, meta: { requiresAuth: true } },
      { path: 'purchase-apply', component: PurchaseApply, meta: { requiresAuth: true } },
      { path: 'purchase-quote', component: PurchaseQuote, meta: { requiresAuth: true } },
      { path: 'purchase-orders', component: PurchaseOrders, meta: { requiresAuth: true } },
      { path: 'purchase-acceptance', component: PurchaseAcceptance, meta: { requiresAuth: true } },
      
      // 报表与看板模块
      { path: 'report-dashboard', component: Dashboard, meta: { requiresAuth: true } },
      { path: 'report-inventory', component: InventoryReport, meta: { requiresAuth: true } },
      { path: 'report-consumption', component: ConsumptionReport, meta: { requiresAuth: true } },
      { path: 'report-supplier', component: SupplierReport, meta: { requiresAuth: true } },
      { path: 'report-maintenance', component: MaintenanceReport, meta: { requiresAuth: true } },
      { path: 'warning-center', component: WarningCenter, meta: { requiresAuth: true } }
    ]
  },
  {
    path: '/smart',
    component: Home,
    meta: { requiresAuth: true },
    children: [
      { path: 'classify-result', component: ClassifyResult, meta: { requiresAuth: true } },
      { path: 'health-monitor', component: HealthMonitor, meta: { requiresAuth: true } },
      { path: 'fault-prediction', component: FaultPrediction, meta: { requiresAuth: true } },
      { path: 'maintenance-suggestion', component: MaintenanceSuggestion, meta: { requiresAuth: true } }
    ]
  },
  {
    path: '/ai',
    component: Home,
    meta: { requiresAuth: true },
    children: [
      { path: 'forecast-result', component: AiForecastResult, meta: { requiresAuth: true } },
      { path: 'weekly-forecast', component: WeeklyForecastResult, meta: { requiresAuth: true } },
      { path: 'training-progress', component: AiTrainingProgress, meta: { requiresAuth: true } },
      { path: 'job-center', component: AiJobCenter, meta: { requiresAuth: true } },
      { path: 'train-data-dashboard', component: AiTrainDataDashboard, meta: { requiresAuth: true } }
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

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta?.requiresAuth) {
    if (!authStore.accessToken) {
      if (authStore.refreshToken) {
        const success = await authStore.refreshAccessToken()
        if (success) {
          next()
          return
        }
      }
      next('/login')
      return
    }
  }

  if (to.path === '/ai/job-center') {
    const permissions = authStore.permissions || []
    const username = authStore.username || localStorage.getItem('username') || ''
    if (permissions.length > 0 && username !== 'admin' && !permissions.includes('ai:forecast:list')) {
      next('/ai/forecast-result')
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
