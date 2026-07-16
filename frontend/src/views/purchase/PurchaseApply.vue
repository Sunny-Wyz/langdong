<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">发起采购申请</div>
          <div class="head-btn-group" />
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" style="max-width: 700px">
        <el-form-item label="备件" prop="sparePartId">
          <el-select v-model="form.sparePartId" placeholder="请选择备件" style="width: 100%" filterable @change="onPartChange">
            <el-option
              v-for="p in spareParts"
              :key="p.id"
              :label="`${p.code}  ${p.name}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" placeholder="请选择供应商" style="width: 100%" filterable>
            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="采购数量" prop="orderQty">
          <el-input-number v-model="form.orderQty" :min="1" style="width: 180px" />
        </el-form-item>

        <el-form-item label="期望到货日期" prop="expectedDate">
          <el-date-picker
            v-model="form.expectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input type="textarea" :rows="3" v-model="form.remark" placeholder="请输入采购原因或备注" />
        </el-form-item>

        <el-alert
          v-if="suggestId"
          type="info"
          :closable="false"
          title="本次采购由系统补货建议触发，提交后将自动更新建议状态为「已采购」"
          style="margin-bottom: 16px"
        />

        <el-form-item>
          <el-button type="primary" @click="submit" :loading="submitting">提交申请</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '../../utils/request'

const route = useRoute()
const formRef = ref<FormInstance | null>(null)
const spareParts = ref<any[]>([])
const suppliers = ref<any[]>([])
const submitting = ref(false)
const suggestId = ref<string | number | null>(null)

const form = reactive({
  sparePartId: null as number | null,
  supplierId: null as number | null,
  orderQty: 1,
  expectedDate: '',
  remark: ''
})

const rules: FormRules = {
  sparePartId: [{ required: true, message: '请选择备件', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderQty: [{ required: true, message: '请输入采购数量', trigger: 'blur' }],
  expectedDate: [{ required: true, message: '请选择期望到货日期', trigger: 'change' }]
}

async function loadOptions() {
  const [p, s] = await Promise.all([
    request.get('/spare-parts'),
    request.get('/suppliers')
  ])
  spareParts.value = p.data || []
  suppliers.value = s.data || []

  if (route.query.partCode) {
    const part = spareParts.value.find((x: any) => x.code === route.query.partCode)
    if (part) form.sparePartId = part.id
  }
  if (route.query.qty) {
    form.orderQty = Number(route.query.qty) || 1
  }
  if (route.query.suggestId) {
    suggestId.value = route.query.suggestId as string
  }
}

function onPartChange() {
  // reserved for future auto-fill
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const payload = { ...form, reorderSuggestId: suggestId.value }
      await request.post('/purchase-orders', payload)
      ElMessage.success('采购申请提交成功！')
      reset()
      suggestId.value = null
    } catch (e) {
      ElMessage.error('提交失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

function reset() {
  formRef.value?.resetFields()
}

onMounted(() => {
  loadOptions()
})
</script>
