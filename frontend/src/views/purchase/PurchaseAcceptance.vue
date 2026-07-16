<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">到货验收</div>
          <div class="head-btn-group" />
        </div>
      </template>

      <div v-if="order" style="margin-bottom: 24px">
        <el-descriptions :column="4" border size="small" title="订单信息">
          <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="备件">
            {{ order.sparePartCode }} {{ order.sparePartName }}
          </el-descriptions-item>
          <el-descriptions-item label="采购数量">{{ order.orderQty }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ order.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="单价(元)">{{ order.unitPrice || '—' }}</el-descriptions-item>
          <el-descriptions-item label="期望到货">{{ order.expectedDate }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag type="primary" size="small">{{ order.orderStatus }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="130px" style="max-width: 600px">
        <el-form-item label="实际到货数量" prop="receivedQty">
          <el-input-number v-model="form.receivedQty" :min="0" style="width: 160px" />
        </el-form-item>

        <el-form-item label="质量是否合格" prop="qualified">
          <el-radio-group v-model="form.qualified">
            <el-radio :value="true">合格 — 验收通过，自动入库</el-radio>
            <el-radio :value="false">不合格 — 验收失败，退换货</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="验收意见/原因" prop="remark">
          <el-input
            type="textarea"
            :rows="3"
            v-model="form.remark"
            :placeholder="form.qualified === false ? '请填写退换货原因' : '选填'"
          />
        </el-form-item>

        <el-alert
          v-if="form.qualified === true"
          type="success"
          :closable="false"
          title="验收通过后将自动生成入库记录"
          style="margin-bottom: 16px"
        />
        <el-alert
          v-if="form.qualified === false"
          type="warning"
          :closable="false"
          title="验收失败将记录退换货流程"
          style="margin-bottom: 16px"
        />

        <el-form-item>
          <el-button type="primary" @click="submitAcceptance" :loading="submitting">提交验收</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '../../utils/request'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance | null>(null)
const order = ref<any>(null)
const submitting = ref(false)

const form = reactive({
  receivedQty: 0,
  qualified: true as boolean,
  remark: ''
})

const rules: FormRules = {
  receivedQty: [{ required: true, message: '请输入到货数量', trigger: 'blur' }],
  qualified: [{ required: true, message: '请选择是否合格', trigger: 'change' }]
}

async function loadOrder(id: string | number) {
  const res = await request.get(`/purchase-orders/${id}`)
  order.value = res.data
  form.receivedQty = order.value.orderQty
}

async function submitAcceptance() {
  if (!formRef.value || !order.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const confirmMsg = form.qualified
      ? '确认验收通过？系统将自动生成入库记录。'
      : '确认验收失败并发起退换货流程？'
    await ElMessageBox.confirm(confirmMsg, '二次确认', { type: 'warning' })
    submitting.value = true
    try {
      await request.put(`/purchase-orders/${order.value.id}/accept`, form)
      ElMessage.success(form.qualified ? '验收通过，入库已完成！' : '已记录验收失败，请跟进退换货。')
      router.push('/home/purchase-orders')
    } catch (e) {
      ElMessage.error('提交失败，请稍后重试')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(() => {
  const orderId = route.query.orderId
  if (orderId) loadOrder(orderId as string)
})
</script>
