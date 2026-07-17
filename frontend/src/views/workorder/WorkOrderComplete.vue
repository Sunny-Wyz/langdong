<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">✔</span>
          <div class="title">完工确认</div>
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
          <template #default="scope">
            <span :style="isOverdue(scope.row) ? 'color:#F56C6C' : ''">
              {{ formatTime(scope.row.planFinish) }}
              <el-tag v-if="isOverdue(scope.row)" type="danger" size="small" style="margin-left:4px">逾期</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openComplete(scope.row)">完工确认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer title="完工确认" v-model="drawerVisible" size="520px" :before-close="closeDrawer">
      <div style="padding: 24px">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="故障根因">{{ currentWo.faultCause }}</el-descriptions-item>
          <el-descriptions-item label="维修方案">{{ currentWo.repairMethod }}</el-descriptions-item>
        </el-descriptions>

        <el-form :model="completeForm" :rules="completeRules" ref="completeFormRef" label-width="120px">
          <el-form-item label="实际完成时间" prop="actualFinish">
            <el-date-picker
              v-model="completeForm.actualFinish"
              type="datetime"
              placeholder="请选择实际完成时间"
              style="width: 100%"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="人工费用(元)">
            <el-input-number
              v-model="completeForm.laborCost"
              :min="0"
              :precision="2"
              :step="100"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="外协费用(元)">
            <el-input-number
              v-model="completeForm.outsourceCost"
              :min="0"
              :precision="2"
              :step="100"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备件费用">
            <el-alert type="info" :closable="false" show-icon title="备件费用由系统根据关联领用单自动汇总，无需手动填写" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitComplete" :loading="submitting">确认完工</el-button>
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
const completeFormRef = ref<FormInstance | null>(null)
const completeForm = reactive({
  actualFinish: null as string | null,
  laborCost: 0,
  outsourceCost: 0
})
const completeRules: FormRules = {
  actualFinish: [{ required: true, message: '请选择实际完成时间', trigger: 'change' }]
}
const submitting = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/work-orders', { params: { orderStatus: '维修中' } })
    workOrders.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工单列表失败')
  } finally {
    loading.value = false
  }
}

function openComplete(row: any) {
  currentWo.value = row
  completeForm.actualFinish = null
  completeForm.laborCost = 0
  completeForm.outsourceCost = 0
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

function submitComplete() {
  completeFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.put(`/work-orders/${currentWo.value.id}/complete`, { ...completeForm })
      ElMessage.success('完工确认成功！工单已归档。')
      drawerVisible.value = false
      loadList()
    } catch (e: any) {
      const msg = e.response?.data?.message
      ElMessage.error(msg || '操作失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

function isOverdue(row: any) {
  if (!row.planFinish) return false
  return new Date(row.planFinish) < new Date()
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
