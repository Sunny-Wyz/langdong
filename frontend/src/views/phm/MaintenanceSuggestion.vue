<template>
  <div class="maintenance-suggestion-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card
          class="stat-card stat-pending"
          shadow="hover"
          :class="{ 'stat-active': filter.status === 'PENDING' }"
          @click.native="filterByStatus('PENDING')"
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
          @click.native="filterByStatus('ACCEPTED')"
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
          @click.native="filterByStatus('REJECTED')"
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
          @click.native="filterByStatus('COMPLETED')"
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
          @keyup.enter.native="fetchList"
        >
          <el-button slot="append" icon="el-icon-search" @click="fetchList" />
        </el-input>

        <!-- 重置按钮 -->
        <el-button icon="el-icon-refresh-left" @click="resetFilter">重置</el-button>
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
      <div slot="header">
        <span>维护建议列表</span>
        <span class="table-tip">共 {{ total }} 条记录</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />

        <el-table-column label="优先级" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="getPriorityTagType(row.priorityLevel)" size="small">
              {{ getPriorityText(row.priorityLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="维护类型" width="110" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="getTypeTagType(row.maintenanceType)" size="small" effect="plain">
              {{ getMaintenanceTypeText(row.maintenanceType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="健康评分" width="90" align="center">
          <template slot-scope="{ row }">
            <span :style="{ color: getScoreColor(row.healthScore) }">
              {{ formatDecimal(row.healthScore) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="故障概率" width="90" align="center">
          <template slot-scope="{ row }">
            {{ formatPercent(row.failureProbability) }}
          </template>
        </el-table-column>

        <el-table-column prop="suggestedStartDate" label="建议开始日期" width="120" align="center" />
        <el-table-column prop="suggestedEndDate" label="建议结束日期" width="120" align="center" />

        <el-table-column prop="estimatedCost" label="预估成本" width="100" align="right">
          <template slot-scope="{ row }">
            ¥ {{ formatDecimal(row.estimatedCost, 0) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING' && hasPermission('phm:suggestion:approve')"
              type="text"
              size="small"
              style="color: #67c23a"
              @click="approveSuggestion(row)"
            >
              采纳
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' && hasPermission('phm:suggestion:reject')"
              type="text"
              size="small"
              style="color: #f56c6c"
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
      :visible.sync="detailDialogVisible"
      :title="'维护建议详情 - ID: ' + (selectedSuggestion?.id || '')"
      width="800px"
    >
      <div v-if="selectedSuggestion">
        <el-descriptions :column="2" border size="medium">
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
            <el-tag :type="getStatusTagType(selectedSuggestion.status)" size="medium">
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

      <div slot="footer">
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- ============================================================
         拒绝建议对话框
         ============================================================ -->
    <el-dialog
      :visible.sync="rejectDialogVisible"
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

      <div slot="footer">
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="confirmReject">确认拒绝</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'MaintenanceSuggestion',

  data() {
    return {
      // Dashboard数据
      dashboard: {
        statusDistribution: {},
        acceptanceRate: 0
      },

      // 筛选条件
      filter: {
        status: '',
        priorityLevel: '',
        maintenanceType: '',
        deviceCode: ''
      },

      // 表格数据
      tableData: [],
      total: 0,
      loading: false,

      // 分页
      pagination: {
        page: 1,
        pageSize: 20
      },

      // 详情对话框
      detailDialogVisible: false,
      selectedSuggestion: null,

      // 拒绝对话框
      rejectDialogVisible: false,
      rejectReason: '',
      rejectingSuggestion: null,
      submitting: false
    }
  },

  computed: {
    permissions() {
      return this.$store.state.permissions || []
    },

    // 当前用户ID（从store获取）
    currentUserId() {
      return this.$store.state.user?.id || 1
    }
  },

  created() {
    this.fetchDashboard()
    this.fetchList()
  },

  methods: {
    // ================================================================
    // 权限判断
    // ================================================================
    hasPermission(perm) {
      return this.permissions.includes(perm)
    },

    // ================================================================
    // 数据加载
    // ================================================================

    /** 加载Dashboard数据 */
    async fetchDashboard() {
      try {
        const res = await request.get('/phm/suggestion/dashboard')
        if (res.data.code === 200) {
          this.dashboard = res.data.data || {}
        }
      } catch (e) {
        console.error('获取Dashboard失败', e)
      }
    },

    /** 加载建议列表 */
    async fetchList() {
      this.loading = true
      try {
        const res = await request.get('/phm/suggestion/list', {
          params: {
            status: this.filter.status || undefined,
            priorityLevel: this.filter.priorityLevel || undefined,
            maintenanceType: this.filter.maintenanceType || undefined,
            deviceCode: this.filter.deviceCode || undefined,
            page: this.pagination.page,
            pageSize: this.pagination.pageSize
          }
        })
        if (res.data.code === 200) {
          this.tableData = res.data.data || []
          this.total = res.data.total || 0
        }
      } catch (e) {
        this.$message.error('获取建议列表失败')
      } finally {
        this.loading = false
      }
    },

    // ================================================================
    // 交互操作
    // ================================================================

    /** 按状态筛选 */
    filterByStatus(status) {
      if (this.filter.status === status) {
        this.filter.status = ''
      } else {
        this.filter.status = status
      }
      this.pagination.page = 1
      this.fetchList()
    },

    /** 重置筛选 */
    resetFilter() {
      this.filter = {
        status: '',
        priorityLevel: '',
        maintenanceType: '',
        deviceCode: ''
      }
      this.pagination.page = 1
      this.fetchList()
    },

    /** 查看详情 */
    viewDetail(row) {
      this.selectedSuggestion = row
      this.detailDialogVisible = true
    },

    /** 采纳建议 */
    approveSuggestion(row) {
      this.$confirm(
        `确认采纳此维护建议？将自动创建工单和领用单。`,
        '确认采纳',
        { type: 'warning' }
      ).then(async () => {
        const loading = this.$loading({ lock: true, text: '正在处理...' })
        try {
          const res = await request.post(`/phm/suggestion/${row.id}/approve`, null, {
            params: { handledBy: this.currentUserId }
          })
          if (res.data.code === 200) {
            this.$message.success(res.data.message || '建议已采纳')
            this.fetchList()
            this.fetchDashboard()
          } else {
            this.$message.error(res.data.message || '采纳失败')
          }
        } catch (e) {
          this.$message.error('采纳失败：' + (e.response?.data?.message || '未知错误'))
        } finally {
          loading.close()
        }
      }).catch(() => {})
    },

    /** 拒绝建议 */
    rejectSuggestion(row) {
      this.rejectingSuggestion = row
      this.rejectReason = ''
      this.rejectDialogVisible = true
    },

    /** 确认拒绝 */
    async confirmReject() {
      if (!this.rejectReason || !this.rejectReason.trim()) {
        this.$message.warning('请输入拒绝原因')
        return
      }

      this.submitting = true
      try {
        const res = await request.post(
          `/phm/suggestion/${this.rejectingSuggestion.id}/reject`,
          null,
          {
            params: {
              rejectReason: this.rejectReason,
              handledBy: this.currentUserId
            }
          }
        )
        if (res.data.code === 200) {
          this.$message.success('建议已拒绝')
          this.rejectDialogVisible = false
          this.fetchList()
          this.fetchDashboard()
        } else {
          this.$message.error(res.data.message || '拒绝失败')
        }
      } catch (e) {
        this.$message.error('拒绝失败：' + (e.response?.data?.message || '未知错误'))
      } finally {
        this.submitting = false
      }
    },

    /** 分页大小变化 */
    handleSizeChange(newSize) {
      this.pagination.pageSize = newSize
      this.pagination.page = 1
      this.fetchList()
    },

    /** 页码变化 */
    handlePageChange(newPage) {
      this.pagination.page = newPage
      this.fetchList()
    },

    // ================================================================
    // 辅助方法
    // ================================================================

    formatDecimal(val, digits = 2) {
      return val != null ? Number(val).toFixed(digits) : '0.00'
    },

    formatPercent(val) {
      return val != null ? (Number(val) * 100).toFixed(1) + '%' : '0.0%'
    },

    getScoreColor(score) {
      if (score >= 80) return '#67c23a'
      if (score >= 60) return '#409eff'
      if (score >= 40) return '#e6a23c'
      return '#f56c6c'
    },

    getStatusTagType(status) {
      const map = {
        PENDING: 'warning',
        ACCEPTED: 'success',
        REJECTED: 'danger',
        COMPLETED: 'info'
      }
      return map[status] || ''
    },

    getStatusText(status) {
      const map = {
        PENDING: '待处理',
        ACCEPTED: '已采纳',
        REJECTED: '已拒绝',
        COMPLETED: '已完成'
      }
      return map[status] || status
    },

    getPriorityTagType(priority) {
      const map = {
        HIGH: 'danger',
        MEDIUM: 'warning',
        LOW: 'info'
      }
      return map[priority] || ''
    },

    getPriorityText(priority) {
      const map = {
        HIGH: '高',
        MEDIUM: '中',
        LOW: '低'
      }
      return map[priority] || priority
    },

    getTypeTagType(type) {
      const map = {
        EMERGENCY: 'danger',
        PREDICTIVE: 'warning',
        PREVENTIVE: 'primary'
      }
      return map[type] || ''
    },

    getMaintenanceTypeText(type) {
      const map = {
        EMERGENCY: '紧急维护',
        PREDICTIVE: '预测性维护',
        PREVENTIVE: '预防性维护'
      }
      return map[type] || type
    }
  }
}
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
