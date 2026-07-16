<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">智能补货建议</div>
          <div class="head-btn-group">
            <el-tag type="danger" style="margin-left: 12px" size="small">紧急优先显示</el-tag>
          </div>
        </div>
      </template>

      <el-table :data="suggestions" border v-loading="loading" :row-class-name="urgencyRowClass">
        <el-table-column prop="partCode" label="备件编码" width="120" sortable="custom" />
        <el-table-column prop="sparePartName" label="备件名称" />
        <el-table-column prop="suggestMonth" label="建议月份" width="100" />
        <el-table-column prop="currentStock" label="当前库存" width="90" align="center" />
        <el-table-column prop="reorderPoint" label="ROP补货点" width="100" align="center" />
        <el-table-column prop="suggestQty" label="建议采购量" width="100" align="center" />
        <el-table-column prop="forecastQty" label="月预测量" width="95" align="center" />
        <el-table-column label="置信区间" width="130" align="center">
          <template #default="scope">
            <span>{{ scope.row.lowerBound }} ~ {{ scope.row.upperBound }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="urgency" label="紧急程度" width="95" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.urgency === '紧急' ? 'danger' : 'success'" size="small">
              {{ scope.row.urgency }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" @click="launchPurchase(scope.row)">发起采购</el-button>
            <el-button type="warning" size="small" @click="ignoreSuggest(scope.row)">忽略</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const suggestions = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await request.get('/reorder-suggests', { params: { status: '待处理' } })
    suggestions.value = res.data || []
  } catch (e) {
    ElMessage.error('加载补货建议失败')
  } finally {
    loading.value = false
  }
}

function launchPurchase(row: any) {
  router.push({
    path: '/home/purchase-apply',
    query: {
      partCode: row.partCode,
      suggestId: String(row.id),
      qty: String(row.suggestQty)
    }
  })
}

async function ignoreSuggest(row: any) {
  await ElMessageBox.confirm('确认忽略该补货建议？', '提示', { type: 'warning' })
  await request.put(`/reorder-suggests/${row.id}/ignore`)
  ElMessage.success('已忽略')
  load()
}

function urgencyRowClass({ row }: { row: any }) {
  return row.urgency === '紧急' ? 'urgency-row' : ''
}

onMounted(() => {
  load()
})
</script>

<style scoped>
:deep(.urgency-row) {
  background-color: #fff5f5 !important;
}
</style>
