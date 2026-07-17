<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">🛠</span>
          <div class="title">故障报修</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="reportFormRef" label-width="120px" style="max-width: 800px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="故障设备" prop="deviceId">
              <el-select v-model="form.deviceId" placeholder="请选择故障设备" style="width: 100%" filterable>
                <el-option
                  v-for="eq in equipmentList"
                  :key="eq.id"
                  :label="eq.name + ' (' + eq.code + ')'"
                  :value="eq.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急程度" prop="faultLevel">
              <el-radio-group v-model="form.faultLevel">
                <el-radio-button label="紧急">
                  <span style="color: #F56C6C">紧急</span>
                </el-radio-button>
                <el-radio-button label="一般">一般</el-radio-button>
                <el-radio-button label="计划">计划</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="故障描述" prop="faultDesc">
          <el-input
            type="textarea"
            :rows="4"
            v-model="form.faultDesc"
            placeholder="请详细描述故障现象、发生时间及影响范围"
          />
        </el-form-item>

        <el-form-item>
          <el-button size="small" @click="submitReport" :loading="submitting">提交报修</el-button>
          <el-button size="small" @click="resetForm">🔄 重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const reportFormRef = ref<FormInstance | null>(null)
const form = reactive({
  deviceId: null as number | null,
  faultLevel: '一般',
  faultDesc: ''
})
const rules: FormRules = {
  deviceId: [{ required: true, message: '请选择故障设备', trigger: 'change' }],
  faultLevel: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
  faultDesc: [
    { required: true, message: '请填写故障描述', trigger: 'blur' },
    { min: 5, message: '描述不少于5个字符', trigger: 'blur' }
  ]
}
const equipmentList = ref<any[]>([])
const submitting = ref(false)

async function loadEquipments() {
  try {
    const res = await request.get('/equipments')
    equipmentList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载设备列表失败')
  }
}

function resetForm() {
  reportFormRef.value?.resetFields()
  form.faultLevel = '一般'
}

function submitReport() {
  reportFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.post('/work-orders/report', { ...form })
      ElMessage.success('报修工单提交成功！')
      resetForm()
    } catch (e) {
      ElMessage.error('提交失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  loadEquipments()
})
</script>
