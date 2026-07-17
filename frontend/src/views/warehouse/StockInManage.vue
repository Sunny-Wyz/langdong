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
        <el-form-item label="采购单号">
          <el-input v-model="poCode" placeholder="请输入采购单号 (例: PO202602270001)" />
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="fetchPendingItems">加载待收明细</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="items.length > 0" :data="items" border style="width: 100%">
        <el-table-column prop="sparePartCode" label="备件编码" width="120" sortable="custom" />
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
            @click="submitStockIn"
            :disabled="hasOverReceiving && !allowOverReceive"
          >
            确认入库
          </el-button>
        </div>
      </div>

      <el-empty v-else description="请输入合法的采购单号以查询待入库明细" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const poCode = ref('PO202602270001')
const items = ref<any[]>([])
const allowOverReceive = ref(false)

const hasOverReceiving = computed(() => items.value.some(item => isOverReceiving(item)))

function isOverReceiving(row: any) {
  const expected = row.quantity - row.receivedQuantity
  return row.actualQuantity > expected
}

async function fetchPendingItems() {
  if (!poCode.value) {
    ElMessage.warning('请输入单号')
    return
  }
  try {
    const res = await request.get(`/stock-in/po/${poCode.value}`)
    items.value = res.data.map((item: any) => ({
      ...item,
      actualQuantity: item.quantity - item.receivedQuantity > 0 ? item.quantity - item.receivedQuantity : 0,
      remark: ''
    }))
    allowOverReceive.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data || '查询采购单失败')
    items.value = []
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

  try {
    await request.post('/stock-in', payload)
    ElMessage.success('入库成功，库存台账已更新！')
    items.value = []
    poCode.value = ''
    allowOverReceive.value = false
  } catch (e: any) {
    ElMessage.error(e.response?.data || '入库失败')
  }
}
</script>
