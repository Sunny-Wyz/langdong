<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📋</span>
          <div class="title">维修过程记录</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <el-table :data="workOrders" border stripe v-loading="loading">
        <el-table-column prop="workOrderNo" label="工单编号" width="200" />
        <el-table-column label="故障设备">
          <template #default="scope">
            {{ scope.row.deviceName }} <span style="color:#999">({{ scope.row.deviceCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="维修人员" width="100" />
        <el-table-column label="紧急程度" width="90" align="center">
          <template #default="scope">
            <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计划完成时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.planFinish) }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障描述" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button type="warning" size="small" @click="openProcess(scope.row)">填写记录</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer title="维修过程记录" v-model="drawerVisible" size="520px" :before-close="closeDrawer">
      <div style="padding: 24px">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="故障描述">{{ currentWo.faultDesc }}</el-descriptions-item>
          <el-descriptions-item label="维修人员">{{ currentWo.assigneeName }}</el-descriptions-item>
        </el-descriptions>

        <el-form :model="processForm" :rules="processRules" ref="processFormRef" label-width="110px">
          <el-form-item label="故障根因分析" prop="faultCause">
            <el-input
              type="textarea"
              :rows="4"
              v-model="processForm.faultCause"
              placeholder="请填写故障的根本原因分析"
            />
          </el-form-item>
          <el-form-item label="维修方案描述" prop="repairMethod">
            <el-input
              type="textarea"
              :rows="4"
              v-model="processForm.repairMethod"
              placeholder="请填写本次维修的具体方案和操作步骤"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitProcess" :loading="submitting">保存记录</el-button>
            <el-button @click="closeDrawer">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const workOrders = ref<any[]>([])
const loading = ref(false)
const drawerVisible = ref(false)
const currentWo = ref<any>({})
const processFormRef = ref<FormInstance | null>(null)
const processForm = reactive({
  faultCause: '',
  repairMethod: ''
})
const processRules: FormRules = {
  faultCause: [{ required: true, message: '请填写故障根因分析', trigger: 'blur' }],
  repairMethod: [{ required: true, message: '请填写维修方案描述', trigger: 'blur' }]
}
const submitting = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/work-orders', { params: { orderStatus: '已派工' } })
    workOrders.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工单列表失败')
  } finally {
    loading.value = false
  }
}

function openProcess(row: any) {
  currentWo.value = row
  processForm.faultCause = row.faultCause || ''
  processForm.repairMethod = row.repairMethod || ''
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

function submitProcess() {
  processFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.put(`/work-orders/${currentWo.value.id}/process`, { ...processForm })
      ElMessage.success('维修记录保存成功，工单状态已更新为"维修中"')
      drawerVisible.value = false
      loadList()
    } catch (e: any) {
      const msg = e.response?.data?.message
      ElMessage.error(msg || '保存失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

function levelType(level: string) {
  if (level === '紧急') return 'danger'
  if (level === '一般') return 'warning'
  return 'info'
}

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  loadList()
})
</script>
