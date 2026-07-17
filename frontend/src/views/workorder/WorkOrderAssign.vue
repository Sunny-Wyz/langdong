<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">👷</span>
          <div class="title">在线派工</div>
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
        <el-table-column prop="reporterName" label="报修人" width="100" />
        <el-table-column label="紧急程度" width="90" align="center">
          <template #default="scope">
            <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reportTime" label="报修时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.reportTime) }}</template>
        </el-table-column>
        <el-table-column prop="faultDesc" label="故障描述" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="openAssign(scope.row)">派工</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer title="在线派工" v-model="drawerVisible" size="480px" :before-close="closeDrawer">
      <div style="padding: 24px">
        <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="工单编号">{{ currentWo.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="故障设备">{{ currentWo.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="故障描述">{{ currentWo.faultDesc }}</el-descriptions-item>
          <el-descriptions-item label="紧急程度">
            <el-tag :type="levelType(currentWo.faultLevel)" size="small">{{ currentWo.faultLevel }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-form :model="assignForm" :rules="assignRules" ref="assignFormRef" label-width="110px">
          <el-form-item label="维修人员" prop="assigneeId">
            <el-select v-model="assignForm.assigneeId" placeholder="请选择维修人员" style="width: 100%" filterable>
              <el-option
                v-for="u in userList"
                :key="u.id"
                :label="u.name || u.username"
                :value="u.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="计划完成时间" prop="planFinish">
            <el-date-picker
              v-model="assignForm.planFinish"
              type="datetime"
              placeholder="请选择计划完成时间"
              style="width: 100%"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitAssign" :loading="submitting">确认派工</el-button>
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
const userList = ref<any[]>([])
const assignFormRef = ref<FormInstance | null>(null)
const assignForm = reactive({
  assigneeId: null as number | null,
  planFinish: null as string | null
})
const assignRules: FormRules = {
  assigneeId: [{ required: true, message: '请选择维修人员', trigger: 'change' }],
  planFinish: [{ required: true, message: '请选择计划完成时间', trigger: 'change' }]
}
const submitting = ref(false)

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/work-orders', { params: { orderStatus: '报修' } })
    workOrders.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工单列表失败')
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const res = await request.get('/users')
    userList.value = res.data || []
  } catch (e) {
    // ignore
  }
}

function openAssign(row: any) {
  currentWo.value = row
  assignForm.assigneeId = null
  assignForm.planFinish = null
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

function submitAssign() {
  assignFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.put(`/work-orders/${currentWo.value.id}/assign`, { ...assignForm })
      ElMessage.success('派工成功！')
      drawerVisible.value = false
      loadList()
    } catch (e: any) {
      const msg = e.response?.data?.message
      ElMessage.error(msg || '派工失败，请稍后重试')
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
  loadUsers()
})
</script>
