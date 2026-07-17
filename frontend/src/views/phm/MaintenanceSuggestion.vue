<template>
  <div class="page-container maintenance-suggestion-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card
          class="stat-card stat-pending"
          shadow="hover"
          :class="{ 'stat-active': filter.status === 'PENDING' }"
          @click="filterByStatus('PENDING')"
        >
          <div class="stat-content">
            <div class="stat-label">待处理</div>
            <div class="stat-value">{{ dashboard.statusDistribution?.PENDING || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card
          class="stat-card stat-accepted"
          shadow="hover"
          :class="{ 'stat-active': filter.status === 'ACCEPTED' }"
          @click="filterByStatus('ACCEPTED')"
        >
          <div class="stat-content">
            <div class="stat-label">已采纳</div>
            <div class="stat-value">{{ dashboard.statusDistribution?.ACCEPTED || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card
          class="stat-card stat-rejected"
          shadow="hover"
          :class="{ 'stat-active': filter.status === 'REJECTED' }"
          @click="filterByStatus('REJECTED')"
        >
          <div class="stat-content">
            <div class="stat-label">已拒绝</div>
            <div class="stat-value">{{ dashboard.statusDistribution?.REJECTED || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card
          class="stat-card stat-completed"
          shadow="hover"
          :class="{ 'stat-active': filter.status === 'COMPLETED' }"
          @click="filterByStatus('COMPLETED')"
        >
          <div class="stat-content">
            <div class="stat-label">已完成</div>
            <div class="stat-value">{{ dashboard.statusDistribution?.COMPLETED || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================
         筛选条件和操作按钮
         ============================================================ -->
    <div class="toolbar-row">
      <div class="filter-area">
        <!-- 优先级过滤 -->
        <el-select v-model="filter.priorityLevel" placeholder="优先级" clearable style="width:120px; margin-right:10px" @change="fetchList">
          <el-option label="高优先级" value="HIGH" />
          <el-option label="中优先级" value="MEDIUM" />
          <el-option label="低优先级" value="LOW" />
        </el-select>

        <!-- 维护类型过滤 -->
        <el-select v-model="filter.maintenanceType" placeholder="维护类型" clearable style="width:140px; margin-right:10px" @change="fetchList">
          <el-option label="紧急维护" value="EMERGENCY" />
          <el-option label="预测性维护" value="PREDICTIVE" />
          <el-option label="预防性维护" value="PREVENTIVE" />
        </el-select>

        <!-- 设备编码搜索 -->
        <el-input
          v-model="filter.deviceCode"
          placeholder="设备编码搜索"
          clearable
          style="width:160px; margin-right:10px"
          @clear="fetchList"
          @keyup.enter="fetchList"
        >
          <template #append>
            <el-button @click="fetchList">搜索</el-button>
          </template>
        </el-input>

        <!-- 重置按钮 -->
        <el-button size="small" @click="resetFilter">重置</el-button>
      </div>

      <div class="action-area">
        <!-- 采纳率 -->
        <span class="acceptance-rate">
          建议采纳率：<el-tag type="success">{{ formatPercent(dashboard.acceptanceRate) }}</el-tag>
        </span>
      </div>
    </div>

    <!-- ============================================================
         建议列表表格
         ============================================================ -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-data" />
          <div class="title">维护建议列表</div>
          <div class="head-btn-group">
            <span class="table-tip">共 {{ total }} 条记录</span>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" border stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable="custom" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />

        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getPriorityTagType(row.priorityLevel)" size="small">
              {{ getPriorityText(row.priorityLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="维护类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.maintenanceType)" size="small" effect="plain">
              {{ getMaintenanceTypeText(row.maintenanceType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="健康评分" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: getScoreColor(row.healthScore) }">
              {{ formatDecimal(row.healthScore) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="故障概率" width="90" align="center">
          <template #default="{ row }">
            {{ formatPercent(row.failureProbability) }}
          </template>
        </el-table-column>

        <el-table-column prop="suggestedStartDate" label="建议开始日期" width="120" align="center" />
        <el-table-column prop="suggestedEndDate" label="建议结束日期" width="120" align="center" />

        <el-table-column prop="estimatedCost" label="预估成本" width="100" align="right">
          <template #default="{ row }">
            ¥ {{ formatDecimal(row.estimatedCost, 0) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING' && hasPermission('phm:suggestion:approve')"
              size="small"
              @click="approveSuggestion(row)"
            >
              采纳
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' && hasPermission('phm:suggestion:reject')"
              type="danger"
              link
              size="small"
              @click="rejectSuggestion(row)"
            >
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-area">
        <el-pagination
          :current-page="pagination.page"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="pagination.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- ============================================================
         建议详情对话框
         ============================================================ -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="'维护建议详情 - ID: ' + (selectedSuggestion?.id || '')"
      width="800px"
    >
      <div v-if="selectedSuggestion">
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="设备编码">{{ selectedSuggestion.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ selectedSuggestion.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{ selectedSuggestion.deviceModel }}</el-descriptions-item>
          <el-descriptions-item label="建议日期">{{ selectedSuggestion.suggestionDate }}</el-descriptions-item>

          <el-descriptions-item label="健康评分">
            <span :style="{ color: getScoreColor(selectedSuggestion.healthScore) }">
              {{ formatDecimal(selectedSuggestion.healthScore) }} 分
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="故障概率">
            <span :style="{ color: selectedSuggestion.failureProbability > 0.7 ? '#f56c6c' : '#e6a23c' }">
              {{ formatPercent(selectedSuggestion.failureProbability) }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="维护类型">
            <el-tag :type="getTypeTagType(selectedSuggestion.maintenanceType)">
              {{ getMaintenanceTypeText(selectedSuggestion.maintenanceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="getPriorityTagType(selectedSuggestion.priorityLevel)">
              {{ getPriorityText(selectedSuggestion.priorityLevel) }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="建议开始日期">{{ selectedSuggestion.suggestedStartDate }}</el-descriptions-item>
          <el-descriptions-item label="建议结束日期">{{ selectedSuggestion.suggestedEndDate }}</el-descriptions-item>

          <el-descriptions-item label="预估成本" :span="2">
            <span style="font-size: 16px; color: #f56c6c; font-weight: bold;">
              ¥ {{ formatDecimal(selectedSuggestion.estimatedCost, 0) }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="状态" :span="2">
            <el-tag :type="getStatusTagType(selectedSuggestion.status)" size="default">
              {{ getStatusText(selectedSuggestion.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 20px;">
          <h4>建议原因</h4>
          <p style="padding: 10px; background-color: #f4f4f5; border-radius: 4px; line-height: 1.8;">
            {{ selectedSuggestion.reason }}
          </p>
        </div>

        <div v-if="selectedSuggestion.relatedSpareParts && selectedSuggestion.relatedSpareParts.length > 0" style="margin-top: 20px;">
          <h4>关联备件需求</h4>
          <el-table :data="selectedSuggestion.relatedSpareParts" border size="small">
            <el-table-column prop="sparePartId" label="备件ID" width="80" align="center" />
            <el-table-column prop="name" label="备件名称" />
            <el-table-column prop="quantity" label="建议数量" width="100" align="center" />
            <el-table-column prop="category" label="类别" width="120" align="center" />
          </el-table>
        </div>

        <div v-if="selectedSuggestion.status === 'REJECTED' && selectedSuggestion.rejectReason" style="margin-top: 20px;">
          <h4>拒绝原因</h4>
          <p style="padding: 10px; background-color: #fef0f0; border-radius: 4px; color: #f56c6c;">
            {{ selectedSuggestion.rejectReason }}
          </p>
        </div>

        <div v-if="selectedSuggestion.status === 'ACCEPTED'" style="margin-top: 20px;">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="关联工单ID">
              <router-link v-if="selectedSuggestion.workorderId" :to="`/workorder/${selectedSuggestion.workorderId}`">
                {{ selectedSuggestion.workorderId }}
              </router-link>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="关联领用单ID">
              <router-link v-if="selectedSuggestion.requisitionId" :to="`/requisition/${selectedSuggestion.requisitionId}`">
                {{ selectedSuggestion.requisitionId }}
              </router-link>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ selectedSuggestion.handledByName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理时间">{{ selectedSuggestion.handledAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ============================================================
         拒绝建议对话框
         ============================================================ -->
    <el-dialog
      v-model="rejectDialogVisible"
      title="拒绝维护建议"
      width="500px"
    >
      <el-form label-width="100px">
        <el-form-item label="拒绝原因" required>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="4"
            placeholder="请输入拒绝原因..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'

const authStore = useAuthStore()

const dashboard = ref<any>({
  statusDistribution: {},
  acceptanceRate: 0
})

const filter = reactive({
  status: '',
  priorityLevel: '',
  maintenanceType: '',
  deviceCode: ''
})

const tableData = ref<any[]>([])
const total = ref(0)
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 20
})

const detailDialogVisible = ref(false)
const selectedSuggestion = ref<any>(null)

const rejectDialogVisible = ref(false)
const rejectReason = ref('')
const rejectingSuggestion = ref<any>(null)
const submitting = ref(false)

const permissions = computed(() => authStore.permissions || [])
// auth store 无 user.id 字段，保留原 Vuex 默认回退值 1
const currentUserId = computed(() => 1)

function hasPermission(perm: string) {
  return permissions.value.includes(perm)
}

async function fetchDashboard() {
  try {
    const res = await request.get('/phm/suggestion/dashboard')
    if (res.data.code === 200) {
      dashboard.value = res.data.data || {}
    }
  } catch (e) {
    // ignore dashboard error
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await request.get('/phm/suggestion/list', {
      params: {
        status: filter.status || undefined,
        priorityLevel: filter.priorityLevel || undefined,
        maintenanceType: filter.maintenanceType || undefined,
        deviceCode: filter.deviceCode || undefined,
        page: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    if (res.data.code === 200) {
      tableData.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('获取建议列表失败')
  } finally {
    loading.value = false
  }
}

function filterByStatus(status: string) {
  if (filter.status === status) {
    filter.status = ''
  } else {
    filter.status = status
  }
  pagination.page = 1
  fetchList()
}

function resetFilter() {
  filter.status = ''
  filter.priorityLevel = ''
  filter.maintenanceType = ''
  filter.deviceCode = ''
  pagination.page = 1
  fetchList()
}

function viewDetail(row: any) {
  selectedSuggestion.value = row
  detailDialogVisible.value = true
}

function approveSuggestion(row: any) {
  ElMessageBox.confirm(
    `确认采纳此维护建议？将自动创建工单和领用单。`,
    '确认采纳',
    { type: 'warning' }
  ).then(async () => {
    const loadingInstance = ElLoading.service({ lock: true, text: '正在处理...' })
    try {
      const res = await request.post(`/phm/suggestion/${row.id}/approve`, null, {
        params: { handledBy: currentUserId.value }
      })
      if (res.data.code === 200) {
        ElMessage.success(res.data.message || '建议已采纳')
        fetchList()
        fetchDashboard()
      } else {
        ElMessage.error(res.data.message || '采纳失败')
      }
    } catch (e: any) {
      ElMessage.error('采纳失败：' + (e.response?.data?.message || '未知错误'))
    } finally {
      loadingInstance.close()
    }
  }).catch(() => {})
}

function rejectSuggestion(row: any) {
  rejectingSuggestion.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value || !rejectReason.value.trim()) {
    ElMessage.warning('请输入拒绝原因')
    return
  }

  submitting.value = true
  try {
    const res = await request.post(
      `/phm/suggestion/${rejectingSuggestion.value.id}/reject`,
      null,
      {
        params: {
          rejectReason: rejectReason.value,
          handledBy: currentUserId.value
        }
      }
    )
    if (res.data.code === 200) {
      ElMessage.success('建议已拒绝')
      rejectDialogVisible.value = false
      fetchList()
      fetchDashboard()
    } else {
      ElMessage.error(res.data.message || '拒绝失败')
    }
  } catch (e: any) {
    ElMessage.error('拒绝失败：' + (e.response?.data?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

function handleSizeChange(newSize: number) {
  pagination.pageSize = newSize
  pagination.page = 1
  fetchList()
}

function handlePageChange(newPage: number) {
  pagination.page = newPage
  fetchList()
}

function formatDecimal(val: any, digits = 2) {
  return val != null ? Number(val).toFixed(digits) : '0.00'
}

function formatPercent(val: any) {
  return val != null ? (Number(val) * 100).toFixed(1) + '%' : '0.0%'
}

function getScoreColor(score: number) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#409eff'
  if (score >= 40) return '#e6a23c'
  return '#f56c6c'
}

function getStatusTagType(status: string) {
  const map: Record<string, string> = {
    PENDING: 'warning',
    ACCEPTED: 'success',
    REJECTED: 'danger',
    COMPLETED: 'info'
  }
  return map[status] || ''
}

function getStatusText(status: string) {
  const map: Record<string, string> = {
    PENDING: '待处理',
    ACCEPTED: '已采纳',
    REJECTED: '已拒绝',
    COMPLETED: '已完成'
  }
  return map[status] || status
}

function getPriorityTagType(priority: string) {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
  return map[priority] || ''
}

function getPriorityText(priority: string) {
  const map: Record<string, string> = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  }
  return map[priority] || priority
}

function getTypeTagType(type: string) {
  const map: Record<string, string> = {
    EMERGENCY: 'danger',
    PREDICTIVE: 'warning',
    PREVENTIVE: 'primary'
  }
  return map[type] || ''
}

function getMaintenanceTypeText(type: string) {
  const map: Record<string, string> = {
    EMERGENCY: '紧急维护',
    PREDICTIVE: '预测性维护',
    PREVENTIVE: '预防性维护'
  }
  return map[type] || type
}

onMounted(() => {
  fetchDashboard()
  fetchList()
})
</script>

<style scoped>
.maintenance-suggestion-page {
  padding: 20px;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
  padding: 20px;
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.stat-card.stat-active {
  border: 2px solid #409eff;
}

.stat-content {
  text-align: center;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
}

.stat-pending .stat-value { color: #e6a23c; }
.stat-accepted .stat-value { color: #67c23a; }
.stat-rejected .stat-value { color: #f56c6c; }
.stat-completed .stat-value { color: #909399; }

/* 工具栏 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.acceptance-rate {
  font-size: 14px;
  color: #606266;
  margin-right: 10px;
}

/* 表格 */
.table-card {
  margin-bottom: 20px;
}

.table-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}

.pagination-area {
  margin-top: 20px;
  text-align: right;
}
</style>
