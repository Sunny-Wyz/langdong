<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">✅</span>
          <div class="title">审批领用申请</div>
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
            <el-button size="small" @click="openDrawer(row)">查阅审批</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无待审批的领用申请" />
    </el-card>

    <el-drawer title="领用申请单审批" v-model="drawerVisible" size="50%">
      <div style="padding: 20px" v-loading="detailLoading" v-if="currentReq">
        <el-descriptions title="单据基本信息" :column="2" border>
          <el-descriptions-item label="领用单号">{{ currentReq.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ currentReq.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="关联工单">{{ currentReq.workOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联设备">{{ currentReq.deviceName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否加急">
            <el-tag :type="currentReq.isUrgent ? 'danger' : 'info'">
              {{ currentReq.isUrgent ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatTime(currentReq.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="事由说明" :span="2">{{ currentReq.remark || '无' }}</el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 20px">
          <h4>申请物料明细</h4>
          <el-table :data="currentItems" border style="width: 100%">
            <el-table-column prop="sparePartCode" label="备件编码" width="150" sortable="custom" />
            <el-table-column prop="sparePartName" label="备件名称" />
            <el-table-column prop="applyQty" label="申请数量" width="100" align="center" />
          </el-table>
        </div>

        <div style="margin-top: 30px">
          <h4>审批意见批注</h4>
          <el-input type="textarea" :rows="3" v-model="approveRemark" placeholder="如驳回，请填写驳回理由" />

          <div style="margin-top: 20px; text-align: center">
            <el-button type="danger" @click="submitApproval('REJECT')" :loading="submitting">驳回重填</el-button>
            <el-button type="primary" @click="submitApproval('APPROVE')" :loading="submitting">同意放行</el-button>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const list = ref<any[]>([])
const loading = ref(false)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const currentReq = ref<any>(null)
const currentItems = ref<any[]>([])
const approveRemark = ref('')
const submitting = ref(false)

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await request.get('/requisitions?status=PENDING')
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('获取待审批列表失败')
  } finally {
    loading.value = false
  }
}

async function openDrawer(row: any) {
  currentReq.value = row
  currentItems.value = []
  approveRemark.value = ''
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await request.get(`/requisitions/${row.id}`)
    if (res.data) {
      currentReq.value = res.data.info
      currentItems.value = res.data.items || []
    }
  } catch (e) {
    ElMessage.error('明细加载失败')
  } finally {
    detailLoading.value = false
  }
}

async function submitApproval(action: string) {
  if (action === 'REJECT' && !approveRemark.value) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  submitting.value = true
  try {
    await request.put(`/requisitions/${currentReq.value.id}/approve`, {
      action,
      remark: approveRemark.value
    })
    ElMessage.success(action === 'APPROVE' ? '已同意申请' : '已驳回申请')
    drawerVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('操作失败，请重试')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
