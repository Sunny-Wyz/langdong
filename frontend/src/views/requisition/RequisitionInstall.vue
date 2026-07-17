<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">🔧</span>
          <div class="title">领用安装登记</div>
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
            <el-button size="small" @click="openDialog(row)">登记安装</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无待安装登记的单据（需先出库完成）" />
    </el-card>

    <el-dialog title="安装位置登记" v-model="dialogVisible" width="60%">
      <div v-loading="detailLoading" v-if="currentReq">
        <el-descriptions :column="3" border style="margin-bottom: 20px">
          <el-descriptions-item label="领用单号">{{ currentReq.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="关联设备">{{ currentReq.deviceName || '独立领用' }}</el-descriptions-item>
          <el-descriptions-item label="出库审批人">{{ currentReq.approverName || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-table :data="currentItems" border style="width: 100%">
          <el-table-column prop="sparePartCode" label="备件编码" width="150" sortable="custom" />
          <el-table-column prop="sparePartName" label="备件名称" />
          <el-table-column prop="outQty" label="实领数量" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="success">{{ row.outQty }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="安装位置说明 (如：3号电机左后盖)" min-width="200">
            <template #default="{ row }">
              <el-input v-model="row.installLoc" placeholder="请输入实际安装部位" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitInstall" :loading="submitting">确认安装结案</el-button>
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
    const res = await request.get('/requisitions?status=OUTBOUND')
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('获取待安装登记列表失败')
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
      currentItems.value = (res.data.items || []).filter((i: any) => i.outQty > 0)
    }
  } catch (e) {
    ElMessage.error('明细加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function submitInstall() {
  submitting.value = true
  try {
    const payload = {
      items: currentItems.value.map(i => ({
        itemId: i.id,
        installLoc: i.installLoc || '机台默认位置'
      }))
    }
    await request.put(`/requisitions/${currentReq.value.id}/install`, payload)
    ElMessage.success('安装登记成功！工单全流程闭环！')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('安装登记失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
