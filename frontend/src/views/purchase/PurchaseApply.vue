<template>
  <div class="page-container">
    <el-card class="apply-card" shadow="never">
      <template #header>
        <div class="phead header">
          <span class="title-icon" aria-hidden="true">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
              <rect x="1" y="9" width="3" height="6" rx="0.5" />
              <rect x="6.5" y="5" width="3" height="10" rx="0.5" />
              <rect x="12" y="1" width="3" height="14" rx="0.5" />
            </svg>
          </span>
          <div class="title">发起采购申请</div>
          <div class="head-btn-group" />
        </div>
      </template>

      <el-form
        ref="formRef"
        class="apply-form"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="right"
      >
        <el-form-item label="关联设备（可选）">
          <el-select
            v-model="form.equipmentId"
            placeholder="选择设备后仅显示配套备件"
            filterable
            clearable
            class="field-full"
            @change="onEquipmentChange"
          >
            <el-option
              v-for="e in equipments"
              :key="e.id"
              :label="`${e.code}  ${e.name}`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="备件" prop="sparePartId">
          <el-select
            v-model="form.sparePartId"
            placeholder="请选择备件"
            filterable
            class="field-full"
            @change="onPartChange"
          >
            <el-option
              v-for="p in filteredSpareParts"
              :key="p.id"
              :label="`${p.code}  ${p.name}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="供应商" prop="supplierId">
          <el-select
            v-model="form.supplierId"
            placeholder="请选择供应商"
            filterable
            class="field-full"
          >
            <el-option
              v-for="s in suppliers"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="采购数量" prop="orderQty">
          <el-input-number
            v-model="form.orderQty"
            :min="1"
            :step="1"
            class="field-qty"
          />
        </el-form-item>

        <el-form-item label="期望到货日期" prop="expectedDate">
          <el-date-picker
            v-model="form.expectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            class="field-date"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入采购原因或备注"
            class="field-full"
            resize="both"
          />
        </el-form-item>

        <el-alert
          v-if="suggestId"
          type="info"
          :closable="false"
          title="本次采购由系统补货建议触发，提交后将自动更新建议状态为「已采购」"
          class="suggest-alert"
        />

        <el-form-item class="form-actions">
          <el-button size="small" :loading="submitting" @click="submit">提交申请</el-button>
          <el-button size="small" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '../../utils/request'

const route = useRoute()
const formRef = ref<FormInstance | null>(null)
const spareParts = ref<any[]>([])
const equipments = ref<any[]>([])
const equipmentParts = ref<any[]>([])
const suppliers = ref<any[]>([])
const submitting = ref(false)
const suggestId = ref<string | number | null>(null)

const form = reactive({
  equipmentId: null as number | null,
  sparePartId: null as number | null,
  supplierId: null as number | null,
  orderQty: 1,
  expectedDate: '',
  remark: ''
})

const filteredSpareParts = computed(() => {
  if (form.equipmentId && equipmentParts.value.length > 0) {
    return equipmentParts.value
  }
  return spareParts.value
})

const rules: FormRules = {
  sparePartId: [{ required: true, message: '请选择备件', trigger: 'change' }],
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderQty: [{ required: true, message: '请输入采购数量', trigger: 'blur' }],
  expectedDate: [{ required: true, message: '请选择期望到货日期', trigger: 'change' }]
}

async function loadOptions() {
  const [p, s, e] = await Promise.all([
    request.get('/spare-parts'),
    request.get('/suppliers'),
    request.get('/equipments')
  ])
  spareParts.value = p.data || []
  suppliers.value = s.data || []
  equipments.value = e.data || []

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
  if (route.query.equipmentId) {
    form.equipmentId = Number(route.query.equipmentId)
    await onEquipmentChange()
  }
}

async function onEquipmentChange() {
  form.sparePartId = null
  equipmentParts.value = []
  if (!form.equipmentId) return
  try {
    const res = await request.get(`/equipments/${form.equipmentId}/spare-parts`)
    equipmentParts.value = res.data || []
    if (equipmentParts.value.length === 0) {
      ElMessage.warning('该设备暂无配套备件，请先在设备档案中配置')
    }
  } catch {
    equipmentParts.value = []
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
  form.equipmentId = null
  form.orderQty = 1
  form.remark = ''
  equipmentParts.value = []
}

onMounted(() => {
  loadOptions()
})
</script>

<style scoped>
.apply-card {
  border: 1px solid #e8eaee;
  border-radius: 5px;
  box-shadow: 0 0 5px #ecedf2;
}

.apply-card :deep(.el-card__header) {
  padding: 0;
  border-bottom: 0;
}

.apply-card :deep(.el-card__body) {
  padding: 20px 24px 28px;
}

.title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 1px;
  border: 1px solid #0f3086;
  border-radius: 4px;
  color: #0f3086;
  flex-shrink: 0;
}

.apply-form {
  max-width: 640px;
  padding-top: 8px;
}

.apply-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.apply-form :deep(.el-form-item__label) {
  color: #606266;
  font-weight: 400;
}

.field-full {
  width: 100%;
}

.field-qty {
  width: 160px;
}

.field-date {
  width: 200px;
}

.suggest-alert {
  margin: 0 0 16px 120px;
  max-width: 520px;
}

.form-actions {
  margin-bottom: 0;
  margin-top: 4px;
}

.form-actions :deep(.el-form-item__content) {
  display: flex;
  gap: 12px;
}

/* button style unified globally */
</style>
