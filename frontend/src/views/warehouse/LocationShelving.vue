<template>
  <div class="page-container shelving-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📍</span>
          <div class="title">货位上架工作台 (Pending Shelving)</div>
          <div class="head-btn-group">
            <el-button size="small" @click="fetchPendingItems">🔄 刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="pendingItems" border style="width: 100%" v-loading="loading">
        <el-table-column prop="receiptCode" label="收货入库单号" width="180" sortable="custom" />
        <el-table-column prop="sparePartCode" label="备件编码" width="120" sortable="custom" />
        <el-table-column prop="sparePartName" label="备件名称" />
        <el-table-column label="上架进度" width="200" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round((row.shelvedQuantity || 0) / row.actualQuantity * 100)"
              :format="() => `${row.shelvedQuantity || 0} / ${row.actualQuantity}`"
              :status="(row.shelvedQuantity || 0) === row.actualQuantity ? 'success' : 'exception'"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              @click="handleShelving(row)"
              :disabled="(row.shelvedQuantity || 0) >= row.actualQuantity"
            >
              一键分配
            </el-button>
            <el-button size="small" @click="handlePrintLabel(row)">🖨 打标签</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="`分配货位: ${currentItem.sparePartName}`" v-model="dialogVisible" width="600px">
      <div style="margin-bottom: 15px;">
        <span style="font-weight: bold;">入库总数：</span>{{ currentItem.actualQuantity }} 件
        <span style="margin-left: 20px; font-weight: bold;">未分配剩余数：</span>
        <span style="color: #F56C6C;">{{ remainingQty - totalDistributing }}</span> 件
      </div>

      <el-table :data="distributions" border size="small">
        <el-table-column label="选择目标货位">
          <template #default="scope">
            <el-select v-model="scope.row.locationId" filterable placeholder="选择货位" style="width: 100%">
              <el-option
                v-for="loc in locationOptions"
                :key="loc.id"
                :label="`${loc.zone} - ${loc.name} (容量:${loc.capacity})`"
                :value="loc.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="分配数量" width="150">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.putQty"
              :min="1"
              :max="remainingQty"
              size="small"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="scope">
            <el-button type="danger" link size="small" @click="removeDistLine(scope.$index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 15px;">
        <el-button size="small" @click="addDistLine">➕ 增加货位</el-button>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            @click="submitDistribution"
            :disabled="distributions.length === 0 || remainingQty - totalDistributing < 0"
          >
            确认上架
          </el-button>
        </div>
      </template>
    </el-dialog>

    <div id="print-area" v-show="false">
      <div class="label-container">
        <div class="label-title">货位标签</div>
        <div class="label-row"><strong>备件编码:</strong> {{ printData.sparePartCode }}</div>
        <div class="label-row"><strong>备件名称:</strong> {{ printData.sparePartName }}</div>
        <div class="label-row"><strong>入库批次:</strong> {{ printData.receiptCode }}</div>
        <div class="label-qrcode">
          <img
            src="https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=example"
            alt="QR"
            width="80"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const pendingItems = ref<any[]>([])
const locationOptions = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const currentItem = ref<any>({})
const distributions = ref<any[]>([])
const printData = ref<any>({})

const remainingQty = computed(() => {
  return (currentItem.value.actualQuantity || 0) - (currentItem.value.shelvedQuantity || 0)
})

const totalDistributing = computed(() => {
  return distributions.value.reduce((sum, item) => sum + (item.putQty || 0), 0)
})

async function fetchPendingItems() {
  loading.value = true
  try {
    const res = await request.get('/shelving/pending')
    pendingItems.value = res.data
  } catch (e) {
    ElMessage.error('加载待上架列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchLocations() {
  try {
    const res = await request.get('/locations')
    locationOptions.value = res.data
  } catch (e) {
    // ignore
  }
}

function handleShelving(row: any) {
  currentItem.value = row
  distributions.value = [
    { locationId: null, putQty: remainingQty.value }
  ]
  dialogVisible.value = true
}

function addDistLine() {
  const left = remainingQty.value - totalDistributing.value
  distributions.value = [
    ...distributions.value,
    { locationId: null, putQty: left > 0 ? left : 1 }
  ]
}

function removeDistLine(idx: number) {
  distributions.value = distributions.value.filter((_, i) => i !== idx)
}

async function submitDistribution() {
  const invalid = distributions.value.some(d => !d.locationId || !d.putQty)
  if (invalid) {
    ElMessage.warning('请完善每次分配的货位与数量')
    return
  }

  const payload = [{
    stockInItemId: currentItem.value.id,
    distributions: distributions.value
  }]

  try {
    await request.post('/shelving/submit', payload)
    ElMessage.success('已成功上架！')
    dialogVisible.value = false
    fetchPendingItems()
  } catch (e: any) {
    ElMessage.error(e.response?.data || '上架失败')
  }
}

function handlePrintLabel(row: any) {
  printData.value = row
  nextTick(() => {
    const printEl = document.getElementById('print-area')
    if (!printEl) return
    const printContent = printEl.innerHTML
    const originalContent = document.body.innerHTML

    document.body.innerHTML = `
      <html>
        <head><title>Print Label</title>
          <style>
            body { padding: 0; margin: 0; }
            .label-container { width: 300px; padding: 15px; border: 2px solid #000; font-family: sans-serif; }
            .label-title { text-align: center; font-size: 20px; font-weight: bold; margin-bottom: 10px; border-bottom: 2px solid #000; padding-bottom: 5px; }
            .label-row { font-size: 14px; margin-bottom: 8px; }
            .label-qrcode { text-align: center; margin-top: 10px; }
          </style>
        </head>
        <body>${printContent}</body>
      </html>
    `

    window.print()
    document.body.innerHTML = originalContent
    window.location.reload()
  })
}

onMounted(() => {
  fetchPendingItems()
  fetchLocations()
})
</script>
