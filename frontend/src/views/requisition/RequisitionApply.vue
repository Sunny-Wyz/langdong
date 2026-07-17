<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📝</span>
          <div class="title">发起领用申请</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="applyFormRef" label-width="120px" style="max-width: 800px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="关联工单号" prop="workOrderNo">
              <el-input v-model="form.workOrderNo" placeholder="请输入关联维修工单号（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联设备" prop="deviceId">
              <el-select v-model="form.deviceId" placeholder="请选择关联设备（选填）" style="width: 100%" clearable>
                <el-option
                  v-for="eq in equipmentList"
                  :key="eq.id"
                  :label="eq.name + ' (' + eq.code + ')'"
                  :value="eq.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否紧急" prop="isUrgent">
              <el-switch v-model="form.isUrgent" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="申请备注" prop="remark">
          <el-input type="textarea" :rows="3" v-model="form.remark" placeholder="请输入用途或备注说明" />
        </el-form-item>

        <el-form-item label="申请物料明细" required>
          <div style="margin-bottom: 10px;">
            <el-button size="small" @click="showSparePartDialog">➕ 添加备件</el-button>
          </div>
          <el-table :data="form.items" border style="width: 100%">
            <el-table-column prop="sparePartCode" label="备件编码" width="150" sortable="custom" />
            <el-table-column prop="sparePartName" label="备件名称" />
            <el-table-column label="申请数量" width="180">
              <template #default="scope">
                <el-input-number v-model="scope.row.applyQty" :min="1" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button type="danger" link size="small" @click="removeItem(scope.$index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item>
          <el-button size="small" @click="submitApply" :loading="submitting">提交申请</el-button>
          <el-button size="small" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog title="选择备件" v-model="dialogVisible" width="60%">
      <el-input
        v-model="searchKey"
        placeholder="搜索备件名称或编码"
        style="width: 250px; margin-bottom: 15px;"
        clearable
      />
      <el-table
        :data="filteredSpareParts"
        border
        style="width: 100%"
        height="400"
        @selection-change="handleSelectionChange"
        ref="sparePartTableRef"
      >
        <el-table-column type="selection" width="55" :selectable="checkSelectable" />
        <el-table-column prop="code" label="备件编码" width="150" sortable="custom" />
        <el-table-column prop="name" label="备件名称" />
        <el-table-column prop="price" label="参考价格(元)" width="120" />
        <el-table-column prop="stockQuantity" label="当前总库存" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.quantity > 0 ? 'success' : 'danger'">{{ scope.row.quantity }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmSelection">确定引入</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'

const applyFormRef = ref<FormInstance | null>(null)
const sparePartTableRef = ref<any>(null)

const form = reactive({
  workOrderNo: '',
  deviceId: null as number | null,
  isUrgent: false,
  remark: '',
  items: [] as any[]
})
const rules = {}
const equipmentList = ref<any[]>([])
const sparePartList = ref<any[]>([])
const searchKey = ref('')
const dialogVisible = ref(false)
const selectedParts = ref<any[]>([])
const submitting = ref(false)

const filteredSpareParts = computed(() => {
  if (!searchKey.value) return sparePartList.value
  const k = searchKey.value.toLowerCase()
  return sparePartList.value.filter(s =>
    (s.name && s.name.toLowerCase().includes(k)) ||
    (s.code && s.code.toLowerCase().includes(k))
  )
})

async function loadEquipments() {
  try {
    const res = await request.get('/equipments')
    equipmentList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载设备列表失败')
  }
}

async function loadSpareParts() {
  try {
    const res = await request.get('/spare-parts')
    sparePartList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载备件列表失败')
  }
}

function showSparePartDialog() {
  selectedParts.value = []
  dialogVisible.value = true
  if (sparePartTableRef.value) {
    sparePartTableRef.value.clearSelection()
  }
}

function checkSelectable(row: any) {
  return !form.items.some(i => i.sparePartId === row.id)
}

function handleSelectionChange(val: any[]) {
  selectedParts.value = val
}

function confirmSelection() {
  if (selectedParts.value.length === 0) {
    ElMessage.warning('请至少选择一项')
    return
  }
  form.items = [
    ...form.items,
    ...selectedParts.value.map(p => ({
      sparePartId: p.id,
      sparePartCode: p.code,
      sparePartName: p.name,
      applyQty: 1
    }))
  ]
  dialogVisible.value = false
}

function removeItem(index: number) {
  form.items = form.items.filter((_, i) => i !== index)
}

function resetForm() {
  applyFormRef.value?.resetFields()
  form.items = []
}

function submitApply() {
  if (form.items.length === 0) {
    ElMessage.warning('请至少添加一条申请明细！')
    return
  }
  applyFormRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.post('/requisitions/apply', { ...form })
      ElMessage.success('领用申请提交成功！')
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
  loadSpareParts()
})
</script>
