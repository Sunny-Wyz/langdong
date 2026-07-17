<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📤</span>
          <div class="title">领用出库确认</div>
          <div class="head-btn-group">
            <el-button size="small" @click="loadData" style="float: right">🔄 刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="reqNo" label="单号" width="180" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column prop="deviceName" label="关联设备" width="180">
          <template #default="{ row }">
            {{ row.deviceName ? `${row.deviceName}(${row.deviceCode})` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="isUrgent" label="加急" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isUrgent ? 'danger' : 'info'" size="small">
              {{ row.isUrgent ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.applyTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="事由" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">发料出库</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无待出库的申请单" />
    </el-card>

    <el-dialog title="领用出库清单确认" v-model="dialogVisible" width="60%">
      <div v-loading="detailLoading" v-if="currentReq">
        <p style="margin-bottom: 15px; color: #606266">
          <strong>单号：</strong> {{ currentReq.reqNo }} &nbsp;&nbsp;|&nbsp;&nbsp;
          <strong>申请人：</strong> {{ currentReq.applicantName }}
        </p>

        <el-table :data="currentItems" border style="width: 100%">
          <el-table-column prop="sparePartCode" label="备件编码" width="150" sortable="custom" />
          <el-table-column prop="sparePartName" label="备件名称" />
          <el-table-column prop="applyQty" label="申请数量" width="100" align="center" />
          <el-table-column label="实发数量" width="150" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.outQty" :min="0" :max="row.applyQty" size="small" />
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 10px; color: #E6A23C; font-size: 13px">
          ⚠ 提示：若库存不足，可修改实发数量。确认出库后将同步扣减系统总库台账。
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitOutbound" :loading="submitting">确认出库</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const list = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const detailLoading = ref(false)
const currentReq = ref<any>(null)
const currentItems = ref<any[]>([])
const submitting = ref(false)

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/requisitions?status=APPROVED')
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('获取待出库列表失败')
  } finally {
    loading.value = false
  }
}

async function openDialog(row: any) {
  currentReq.value = row
  currentItems.value = []
  dialogVisible.value = true
  detailLoading.value = true
  try {
    const res = await request.get(`/requisitions/${row.id}`)
    if (res.data) {
      currentReq.value = res.data.info
      currentItems.value = (res.data.items || []).map((item: any) => ({
        ...item,
        outQty: item.applyQty
      }))
    }
  } catch (e) {
    ElMessage.error('明细加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function submitOutbound() {
  submitting.value = true
  try {
    const payload = {
      items: currentItems.value.map(i => ({
        itemId: i.id,
        outQty: i.outQty
      }))
    }
    await request.put(`/requisitions/${currentReq.value.id}/outbound`, payload)
    ElMessage.success('出库成功，库存台账已扣除！')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('出库失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
