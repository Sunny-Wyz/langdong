<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">供应商询价比价</div>
          <div class="head-btn-group" />
        </div>
      </template>

      <el-form :inline="true" style="margin-bottom: 16px">
        <el-form-item label="选择采购订单">
          <el-select v-model="selectedOrderId" placeholder="请选择订单" filterable style="width: 280px" @change="loadQuotes">
            <el-option v-for="o in orders" :key="o.id" :label="`${o.orderNo}  ${o.sparePartName}`" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-if="currentOrder" style="margin-bottom: 20px">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="备件">
            {{ currentOrder.sparePartCode }} {{ currentOrder.sparePartName }}
          </el-descriptions-item>
          <el-descriptions-item label="采购量">{{ currentOrder.orderQty }}</el-descriptions-item>
          <el-descriptions-item label="参考价(元)">{{ refPrice || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ currentOrder.orderStatus }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <el-card shadow="never" style="margin-bottom: 20px" v-if="selectedOrderId">
        <template #header>
          <div class="phead header">
            <span class="title-icon">📊</span>
            <div class="title">录入询价</div>
            <div class="head-btn-group" />
          </div>
        </template>
        <el-form :model="quoteForm" :inline="true">
          <el-form-item label="供应商">
            <el-select v-model="quoteForm.supplierId" placeholder="选择供应商" style="width: 180px" filterable>
              <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="报价(元)">
            <el-input-number v-model="quoteForm.quotePrice" :min="0" :precision="2" style="width: 140px" />
          </el-form-item>
          <el-form-item label="交期(天)">
            <el-input-number v-model="quoteForm.deliveryDays" :min="1" style="width: 120px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="quoteForm.remark" placeholder="选填" style="width: 160px" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="addQuote">录入</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-table :data="quotes" border v-loading="loading" v-if="selectedOrderId">
        <el-table-column prop="supplierName" label="供应商" />
        <el-table-column prop="quotePrice" label="报价(元)" width="120" align="right" />
        <el-table-column label="偏离参考价" width="130" align="center">
          <template #default="scope">
            <span :style="{ color: Math.abs(deviation(scope.row.quotePrice)) > 15 ? '#f56c6c' : '' }">
              {{ deviation(scope.row.quotePrice).toFixed(1) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDays" label="交期(天)" width="100" align="center" />
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="isSelected" label="中标" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isSelected" type="success" size="small">已中标</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button v-if="!scope.row.isSelected" size="small" @click="selectWinner(scope.row)">
              选中中标
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const orders = ref<any[]>([])
const suppliers = ref<any[]>([])
const quotes = ref<any[]>([])
const selectedOrderId = ref<number | null>(null)
const loading = ref(false)
const refPrice = ref(0)
const priceWarning = ref(false)

const quoteForm = reactive({
  supplierId: null as number | null,
  quotePrice: 0,
  deliveryDays: 7,
  remark: ''
})

const currentOrder = computed(() => orders.value.find((o: any) => o.id === selectedOrderId.value) || null)

async function loadOrders() {
  const res = await request.get('/purchase-orders')
  orders.value = res.data || []
}

async function loadSuppliers() {
  const res = await request.get('/suppliers')
  suppliers.value = res.data || []
}

async function loadQuotes() {
  if (!selectedOrderId.value) return
  loading.value = true
  try {
    const [qRes, pRes] = await Promise.all([
      request.get(`/purchase-orders/${selectedOrderId.value}/quotes`),
      request.get(`/purchase-orders/${selectedOrderId.value}/ref-price`).catch(() => ({ data: 0 }))
    ])
    quotes.value = qRes.data || []
    refPrice.value = pRes.data || 0
    quoteForm.supplierId = null
    quoteForm.quotePrice = 0
    quoteForm.deliveryDays = 7
    quoteForm.remark = ''
  } finally {
    loading.value = false
  }
}

function deviation(price: number) {
  if (!refPrice.value) return 0
  return ((price - refPrice.value) / refPrice.value) * 100
}

async function addQuote() {
  if (!quoteForm.supplierId || !quoteForm.quotePrice) {
    ElMessage.warning('请填写供应商和报价')
    return
  }
  await request.post(`/purchase-orders/${selectedOrderId.value}/quotes`, quoteForm)
  ElMessage.success('询价录入成功')
  loadQuotes()
}

async function selectWinner(row: any) {
  const dev = deviation(row.quotePrice)
  if (dev > 15) {
    priceWarning.value = true
    try {
      await ElMessageBox.confirm(
        `报价偏离历史均价 ${dev.toFixed(1)}%，超过15%警戒线，确认中标？`,
        '价格异常警告',
        { type: 'warning', confirmButtonText: '主管确认中标' }
      )
    } catch {
      return
    }
  }
  await request.put(`/purchase-orders/${selectedOrderId.value}/quotes/${row.id}/select`)
  ElMessage.success('已选中中标供应商，订单金额已更新')
  priceWarning.value = false
  loadQuotes()
}

onMounted(() => {
  loadOrders()
  loadSuppliers()
})
</script>
