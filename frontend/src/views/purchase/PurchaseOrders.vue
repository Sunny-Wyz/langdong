<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">采购订单管理</div>
          <div class="head-btn-group" />
        </div>
      </template>

      <el-form :inline="true" style="margin-bottom: 16px">
        <el-form-item label="状态">
          <el-select v-model="filter.orderStatus" clearable placeholder="全部" style="width: 130px" @change="load">
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="filter.supplierId" clearable placeholder="全部" filterable style="width: 160px" @change="load">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">🔍 查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="orders" border v-loading="loading" :row-class-name="overdueClass">
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="sparePartName" label="备件名称" />
        <el-table-column prop="sparePartCode" label="备件编码" width="110" sortable="custom" />
        <el-table-column prop="supplierName" label="供应商" width="150" />
        <el-table-column prop="orderQty" label="数量" width="70" align="center" />
        <el-table-column prop="unitPrice" label="单价(元)" width="90" align="right" />
        <el-table-column prop="totalAmount" label="总额(元)" width="100" align="right" />
        <el-table-column prop="orderStatus" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.orderStatus)" size="small">
              {{ scope.row.orderStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expectedDate" label="期望到货" width="120" />
        <el-table-column prop="actualDate" label="实际到货" width="120" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.orderStatus === '已下单'"
              size="small"
              type="warning"
              @click="advance(scope.row, '已发货')"
            >
              标记发货
            </el-button>
            <el-button
              v-if="scope.row.orderStatus === '已发货'"
              size="small"
              type="primary"
              @click="advance(scope.row, '到货')"
            >
              确认到货
            </el-button>
            <el-button
              v-if="scope.row.orderStatus === '到货'"
              size="small"
              type="success"
              @click="goAccept(scope.row)"
            >
              去验收
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const orders = ref<any[]>([])
const suppliers = ref<any[]>([])
const loading = ref(false)
const statusOptions = ['已下单', '已发货', '到货', '验收通过', '验收失败']

const filter = reactive({
  orderStatus: '',
  supplierId: null as number | null
})

async function load() {
  loading.value = true
  try {
    const params: any = {}
    if (filter.orderStatus) params.orderStatus = filter.orderStatus
    if (filter.supplierId) params.supplierId = filter.supplierId
    const res = await request.get('/purchase-orders', { params })
    orders.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadSuppliers() {
  const res = await request.get('/suppliers')
  suppliers.value = res.data || []
}

async function advance(row: any, nextStatus: string) {
  await request.put(`/purchase-orders/${row.id}/status`, null, {
    params: { orderStatus: nextStatus }
  })
  ElMessage.success(`状态已更新为：${nextStatus}`)
  load()
}

function goAccept(row: any) {
  router.push(`/home/purchase-acceptance?orderId=${row.id}`)
}

function statusType(s: string) {
  const map: Record<string, string> = {
    已下单: 'info',
    已发货: 'warning',
    到货: 'primary',
    验收通过: 'success',
    验收失败: 'danger'
  }
  return map[s] || 'info'
}

function overdueClass({ row }: { row: any }) {
  if (!row.expectedDate || row.actualDate) return ''
  return new Date(row.expectedDate) < new Date() ? 'overdue-row' : ''
}

onMounted(() => {
  load()
  loadSuppliers()
})
</script>

<style scoped>
:deep(.overdue-row) {
  background-color: #fffbf0 !important;
}
</style>
