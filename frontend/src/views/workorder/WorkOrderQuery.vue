<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">工单查询统计</div>
          <div class="head-btn-group"></div>
        </div>
      </template>

      <el-form :inline="true" :model="query" style="margin-bottom: 16px">
        <el-form-item label="工单状态">
          <el-select v-model="query.orderStatus" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="报修" value="报修" />
            <el-option label="已派工" value="已派工" />
            <el-option label="维修中" value="维修中" />
            <el-option label="完工" value="完工" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="query.faultLevel" placeholder="全部" clearable style="width: 100px">
            <el-option label="紧急" value="紧急" />
            <el-option label="一般" value="一般" />
            <el-option label="计划" value="计划" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联设备">
          <el-select v-model="query.deviceId" placeholder="全部设备" clearable filterable style="width: 180px">
            <el-option
              v-for="eq in equipmentList"
              :key="eq.id"
              :label="eq.name + ' (' + eq.code + ')'"
              :value="eq.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报修时间">
          <el-date-picker
            v-model="query.timeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="loadList">🔍 查询</el-button>
          <el-button size="small" @click="resetQuery">🔄 重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="workOrders" border stripe v-loading="loading">
        <el-table-column prop="workOrderNo" label="工单编号" width="200" />
        <el-table-column label="故障设备" min-width="150">
          <template #default="scope">
            {{ scope.row.deviceName }} <span style="color:#999">({{ scope.row.deviceCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="报修人" width="90" />
        <el-table-column prop="assigneeName" label="维修人员" width="90">
          <template #default="scope">{{ scope.row.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="紧急程度" width="90" align="center">
          <template #default="scope">
            <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工单状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.orderStatus)" size="small">{{ scope.row.orderStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报修时间" width="160">
          <template #default="scope">{{ formatTime(scope.row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="维修时长" width="100" align="center">
          <template #default="scope">
            <span v-if="scope.row.mttrMinutes">{{ formatMttr(scope.row.mttrMinutes) }}</span>
            <span v-else style="color:#ccc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="viewDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="工单详情" v-model="dialogVisible" width="700px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工单编号" :span="2">{{ detail.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="故障设备">{{ detail.deviceName }} ({{ detail.deviceCode }})</el-descriptions-item>
        <el-descriptions-item label="报修人">{{ detail.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <el-tag :type="levelType(detail.faultLevel)" size="small">{{ detail.faultLevel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工单状态">
          <el-tag :type="statusType(detail.orderStatus)" size="small">{{ detail.orderStatus }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="报修时间">{{ formatTime(detail.reportTime) }}</el-descriptions-item>
        <el-descriptions-item label="维修人员">{{ detail.assigneeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划完成时间">{{ formatTime(detail.planFinish) }}</el-descriptions-item>
        <el-descriptions-item label="实际完成时间">{{ formatTime(detail.actualFinish) }}</el-descriptions-item>
        <el-descriptions-item label="故障描述" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
        <el-descriptions-item label="故障根因分析" :span="2">{{ detail.faultCause || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修方案描述" :span="2">{{ detail.repairMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修时长">{{ detail.mttrMinutes ? formatMttr(detail.mttrMinutes) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="" />
        <el-descriptions-item label="备件费用">{{ detail.partCost != null ? '￥' + detail.partCost : '-' }}</el-descriptions-item>
        <el-descriptions-item label="人工费用">{{ detail.laborCost != null ? '￥' + detail.laborCost : '-' }}</el-descriptions-item>
        <el-descriptions-item label="外协费用">{{ detail.outsourceCost != null ? '￥' + detail.outsourceCost : '-' }}</el-descriptions-item>
        <el-descriptions-item label="费用合计">
          <strong style="color:#E6A23C">{{ calcTotal(detail) }}</strong>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const workOrders = ref<any[]>([])
const loading = ref(false)
const equipmentList = ref<any[]>([])
const query = reactive({
  orderStatus: '',
  faultLevel: '',
  deviceId: null as number | null,
  timeRange: [] as string[]
})
const dialogVisible = ref(false)
const detail = ref<any>({})

async function loadList() {
  loading.value = true
  const params: any = {}
  if (query.orderStatus) params.orderStatus = query.orderStatus
  if (query.faultLevel) params.faultLevel = query.faultLevel
  if (query.deviceId) params.deviceId = query.deviceId
  if (query.timeRange && query.timeRange.length === 2) {
    params.startTime = query.timeRange[0]
    params.endTime = query.timeRange[1]
  }
  try {
    const res = await request.get('/work-orders', { params })
    workOrders.value = res.data || []
  } catch (e) {
    ElMessage.error('加载工单列表失败')
  } finally {
    loading.value = false
  }
}

async function loadEquipments() {
  try {
    const res = await request.get('/equipments')
    equipmentList.value = res.data || []
  } catch (e) {
    // ignore
  }
}

function resetQuery() {
  query.orderStatus = ''
  query.faultLevel = ''
  query.deviceId = null
  query.timeRange = []
  loadList()
}

function viewDetail(row: any) {
  detail.value = row
  dialogVisible.value = true
}

function calcTotal(d: any) {
  if (!d || (d.partCost == null && d.laborCost == null && d.outsourceCost == null)) return '-'
  const total = (parseFloat(d.partCost) || 0) + (parseFloat(d.laborCost) || 0) + (parseFloat(d.outsourceCost) || 0)
  return '￥' + total.toFixed(2)
}

function formatMttr(minutes: number) {
  if (!minutes) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h}小时${m}分钟`
  return `${m}分钟`
}

function statusType(status: string) {
  if (status === '报修') return 'warning'
  if (status === '已派工') return 'primary'
  if (status === '维修中') return 'danger'
  if (status === '完工') return 'success'
  return 'info'
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
  loadEquipments()
})
</script>
