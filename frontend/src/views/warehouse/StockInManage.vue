<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📦</span>
          <div class="title">收货入库 (按采购单)</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <el-form :inline="true" style="margin-bottom: 20px;">
        <el-form-item label="待收货采购单">
          <el-select
            v-model="poCode"
            filterable
            clearable
            placeholder="选择或搜索可收货采购单"
            style="width: 420px"
            @change="onPoSelect"
          >
            <el-option
              v-for="o in receivableOrders"
              :key="o.orderNo"
              :label="`${o.orderNo} | ${o.sparePartCode || ''} ${o.sparePartName || ''} | 待收${pendingQty(o)} | ${o.orderStatus}`"
              :value="o.orderNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="或手输单号">
          <el-input v-model="poCode" placeholder="采购单号" style="width: 200px" clearable />
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="fetchPendingItems" :loading="loading">加载待收明细</el-button>
          <el-button size="small" @click="loadReceivableOrders">刷新可收列表</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="items.length > 0" :data="items" border style="width: 100%">
        <el-table-column prop="sparePartCode" label="备件编码" width="120" />
        <el-table-column prop="sparePartName" label="备件名称" />
        <el-table-column prop="quantity" label="采购总量" width="100" />
        <el-table-column prop="receivedQuantity" label="历史已收" width="100" />
        <el-table-column label="本次实收数量" width="150">
          <template #default="scope">
            <el-input-number v-model="scope.row.actualQuantity" :min="0" :step="1" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <el-tag :type="isOverReceiving(scope.row) ? 'danger' : 'success'">
              {{ isOverReceiving(scope.row) ? '超收' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注(存放位置等)">
          <template #default="scope">
            <el-input v-model="scope.row.remark" placeholder="如库区分区" size="small" />
          </template>
        </el-table-column>
      </el-table>

      <div v-if="items.length > 0" style="margin-top: 20px;">
        <el-checkbox v-model="allowOverReceive" :disabled="!hasOverReceiving">
          允许超收并确认差异 (出现红牌警告时必须勾选)
        </el-checkbox>
        <div style="margin-top: 15px; text-align: center;">
          <el-button
            type="primary"
            @click="submitStockIn"
            :disabled="hasOverReceiving && !allowOverReceive"
            :loading="submitting"
          >
            确认入库
          </el-button>
        </div>
      </div>

      <el-empty v-else description="请选择可收货采购单（状态为「到货/验收通过」且未收满）并加载明细" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const poCode = ref('')
const items = ref<any[]>([])
const allowOverReceive = ref(false)
const receivableOrders = ref<any[]>([])
const loading = ref(false)
const submitting = ref(false)

const hasOverReceiving = computed(() => items.value.some(item => isOverReceiving(item)))

function pendingQty(o: any) {
  const q = Number(o.orderQty || 0)
  const r = Number(o.receivedQty || 0)
  return Math.max(0, q - r)
}

function isOverReceiving(row: any) {
  const expected = (row.quantity || 0) - (row.receivedQuantity || 0)
  return row.actualQuantity > expected
}

function onPoSelect() {
  if (poCode.value) fetchPendingItems()
}

async function loadReceivableOrders() {
  try {
    const res = await request.get('/stock-in/receivable-orders')
    receivableOrders.value = res.data?.data || res.data || []
  } catch (e) {
    receivableOrders.value = []
  }
}

async function fetchPendingItems() {
  if (!poCode.value) {
    ElMessage.warning('请输入或选择采购单号')
    return
  }
  loading.value = true
  try {
    const res = await request.get(`/stock-in/po/${encodeURIComponent(poCode.value)}`)
    const list = Array.isArray(res.data) ? res.data : (res.data?.data || [])
    if (!list.length) {
      ElMessage.warning('该采购单无可收明细')
      items.value = []
      return
    }
    items.value = list.map((item: any) => ({
      ...item,
      actualQuantity: Math.max(0, (item.quantity || 0) - (item.receivedQuantity || 0)),
      remark: item.remark || ''
    }))
    allowOverReceive.value = false
  } catch (e: any) {
    const msg = e.response?.data?.message || e.response?.data || '查询采购单失败'
    ElMessage.error(typeof msg === 'string' ? msg : '查询采购单失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

async function submitStockIn() {
  const submitItems = items.value
    .map(i => ({
      poItemId: i.id,
      sparePartId: i.sparePartId,
      actualQuantity: i.actualQuantity,
      remark: i.remark
    }))
    .filter(i => i.actualQuantity > 0)

  if (submitItems.length === 0) {
    ElMessage.warning('本次入库没有大于0的明细，无法提交')
    return
  }

  const payload = {
    purchaseOrderCode: poCode.value,
    remark: '系统界面入库',
    allowOverReceive: allowOverReceive.value,
    items: submitItems
  }

  submitting.value = true
  try {
    await request.post('/stock-in', payload)
    ElMessage.success('入库成功，库存台账已更新！')
    items.value = []
    poCode.value = ''
    allowOverReceive.value = false
    loadReceivableOrders()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.response?.data || '入库失败'
    ElMessage.error(typeof msg === 'string' ? msg : '入库失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadReceivableOrders()
})
</script>
