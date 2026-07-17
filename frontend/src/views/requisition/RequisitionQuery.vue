<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">🔍</span>
          <div class="title">全量领用记录查询</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <div style="margin-bottom: 20px; display: flex; gap: 15px">
        <el-select
          v-model="filterStatus"
          clearable
          placeholder="请选择单据状态"
          @change="loadData"
          style="width: 200px"
        >
          <el-option label="待审批 (PENDING)" value="PENDING" />
          <el-option label="待出库 (APPROVED)" value="APPROVED" />
          <el-option label="待安装 (OUTBOUND)" value="OUTBOUND" />
          <el-option label="已结案 (INSTALLED)" value="INSTALLED" />
          <el-option label="已驳回 (REJECTED)" value="REJECTED" />
        </el-select>
        <el-button size="small" @click="loadData">🔍 查询/刷新过滤</el-button>
      </div>

      <el-table :data="list" border stripe style="width: 100%" v-loading="loading">
        <el-table-column prop="reqNo" label="单号" width="180" />
        <el-table-column prop="applicantName" label="申请人" width="120" />
        <el-table-column prop="reqStatus" label="生命周期流转状态" width="180" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.reqStatus === 'PENDING'" type="warning" effect="dark">① 待审批</el-tag>
            <el-tag v-else-if="row.reqStatus === 'APPROVED'" type="primary" effect="dark">② 待出库</el-tag>
            <el-tag v-else-if="row.reqStatus === 'OUTBOUND'" type="success" effect="plain">③ 待安装登记</el-tag>
            <el-tag v-else-if="row.reqStatus === 'INSTALLED'" type="success" effect="dark">④ 已闭环结案</el-tag>
            <el-tag v-else-if="row.reqStatus === 'REJECTED'" type="danger" effect="dark">驳回作废</el-tag>
            <span v-else>{{ row.reqStatus }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deviceName" label="关联设备" width="160">
          <template #default="{ row }">
            {{ row.deviceName ? `${row.deviceName}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="初始发起时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.applyTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="事由" show-overflow-tooltip />
        <el-table-column label="详情查阅" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">追溯明细档案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="领用档案时光机追溯" v-model="dialogVisible" width="60%">
      <div v-loading="detailLoading" v-if="currentReq">
        <el-descriptions title="基础元数据" :column="2" border style="margin-bottom: 25px">
          <el-descriptions-item label="领用单号">{{ currentReq.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag>{{ currentReq.reqStatus }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ currentReq.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="工单编号">{{ currentReq.workOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请时刻">{{ formatTime(currentReq.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注事由">{{ currentReq.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ currentReq.approverName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批回复">{{ currentReq.approveRemark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4>出库与安装资产明细跟踪清单</h4>
        <el-table :data="currentItems" border style="width: 100%">
          <el-table-column prop="sparePartName" label="下发备件资产名称" />
          <el-table-column prop="applyQty" label="诉求量" width="80" align="center" />
          <el-table-column prop="outQty" label="实发量" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.outQty !== null" style="color: #67C23A; font-weight: bold">{{ row.outQty }}</span>
              <span v-else style="color: #909399">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="installLoc" label="现场安装机位追溯" show-overflow-tooltip />
          <el-table-column prop="installerName" label="装配负责人" width="120" />
          <el-table-column prop="installTime" label="安装合拢时刻" width="160">
            <template #default="{ row }">
              {{ formatTime(row.installTime) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭窗口</el-button>
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
const filterStatus = ref('')
const dialogVisible = ref(false)
const detailLoading = ref(false)
const currentReq = ref<any>(null)
const currentItems = ref<any[]>([])

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

async function loadData() {
  loading.value = true
  try {
    const url = filterStatus.value ? `/requisitions?status=${filterStatus.value}` : '/requisitions'
    const res = await request.get(url)
    list.value = res.data || []
  } catch (e) {
    ElMessage.error('获取台账查询失败')
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
      currentItems.value = res.data.items || []
    }
  } catch (e) {
    ElMessage.error('明细档案加载失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>
