<template>
  <div class="fault-prediction-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card stat-total" shadow="hover">
          <div class="stat-icon"><i class="el-icon-s-data" /></div>
          <div class="stat-content">
            <div class="stat-label">预测设备数</div>
            <div class="stat-value">{{ dashboard.totalDevices || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-critical" shadow="hover">
          <div class="stat-icon"><i class="el-icon-warning-outline" /></div>
          <div class="stat-content">
            <div class="stat-label">高风险设备</div>
            <div class="stat-value">{{ dashboard.highRiskCount || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-warning" shadow="hover">
          <div class="stat-icon"><i class="el-icon-data-line" /></div>
          <div class="stat-content">
            <div class="stat-label">平均故障概率</div>
            <div class="stat-value">{{ formatPercent(dashboard.avgProbability) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-info" shadow="hover">
          <div class="stat-icon"><i class="el-icon-message-solid" /></div>
          <div class="stat-content">
            <div class="stat-label">预测故障总数</div>
            <div class="stat-value">{{ dashboard.totalPredictedFaults || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================
         操作栏：筛选 + 手动触发预测
         ============================================================ -->
    <div class="toolbar-row">
      <div class="toolbar-left">
        <el-input
          v-model="queryParams.deviceCode"
          placeholder="设备编码/名称"
          clearable
          style="width: 200px; margin-right: 10px"
          @clear="handleQuery"
        />
        <el-select
          v-model="queryParams.minProbability"
          placeholder="风险等级"
          clearable
          style="width: 150px; margin-right: 10px"
          @change="handleQuery"
        >
          <el-option label="全部" :value="null" />
          <el-option label="高风险 (>70%)" :value="0.7" />
          <el-option label="中风险 (50-70%)" :value="0.5" />
          <el-option label="低风险 (<50%)" :value="0" />
        </el-select>
        <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
        <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text=""
          style="margin-right: 10px"
        />
      </div>
    </div>

    <!-- ============================================================
         预测结果列表
         ============================================================ -->
    <el-card class="table-card" shadow="never">
      <div slot="header">
        <span>故障预测结果</span>
        <span class="card-tip">（显示最新预测记录）</span>
      </div>

      <el-table v-loading="tableLoading" :data="predictionList" border stripe style="width:100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip sortable />

        <el-table-column label="故障概率" width="110" align="center" sortable sort-by="failureProbability">
          <template slot-scope="{ row }">
            <el-tag :type="getProbabilityTagType(row.failureProbability)" size="small">
              {{ formatPercent(row.failureProbability) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="预测故障数" width="110" align="center" sortable sort-by="predictedFaultCount">
          <template slot-scope="{ row }">
            <span style="font-weight: bold; color: #E6A23C">{{ row.predictedFaultCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="置信区间" width="120" align="center">
          <template slot-scope="{ row }">
            <span style="color: #909399; font-size: 12px">
              [{{ row.faultCountLower }}-{{ row.faultCountUpper }}]
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" sortable />
        <el-table-column prop="predictionDate" label="预测日期" width="110" align="center" sortable />
        <el-table-column prop="modelType" label="模型类型" width="150" show-overflow-tooltip />

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template slot-scope="{ row }">
            <el-button
              v-if="hasPermission('phm:prediction:predict')"
              type="text"
              size="small"
              icon="el-icon-refresh-right"
              @click="handleRePredict(row)"
            >
              重新预测
            </el-button>
            <el-button
              type="text"
              size="small"
              icon="el-icon-view"
              @click="handleViewHistory(row)"
            >
              历史记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        :current-page="queryParams.page"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="queryParams.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; text-align: right"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <!-- ============================================================
         高风险设备提示卡片
         ============================================================ -->
    <el-card v-if="highRiskDevices.length > 0" class="high-risk-card" shadow="never">
      <div slot="header">
        <i class="el-icon-warning" style="color: #F56C6C; margin-right: 5px" />
        <span>高风险设备警告</span>
        <span class="card-tip">（故障概率 > 70%）</span>
      </div>

      <el-table :data="highRiskDevices" border size="small" style="width:100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="故障概率" width="110" align="center">
          <template slot-scope="{ row }">
            <el-tag type="danger" size="small">{{ formatPercent(row.failureProbability) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="predictedFaultCount" label="预测故障数" width="110" align="center" />
        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" />
      </el-table>
    </el-card>

    <!-- ============================================================
         预测历史对话框
         ============================================================ -->
    <el-dialog
      :visible.sync="historyDialogVisible"
      :title="'预测历史 - ' + selectedDevice.deviceCode"
      width="900px"
      @close="handleHistoryDialogClose"
    >
      <el-table v-loading="historyLoading" :data="historyList" border size="small" max-height="400">
        <el-table-column prop="predictionDate" label="预测日期" width="110" align="center" />
        <el-table-column prop="targetMonth" label="目标月份" width="100" align="center" />
        <el-table-column label="故障概率" width="110" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="getProbabilityTagType(row.failureProbability)" size="mini">
              {{ formatPercent(row.failureProbability) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="predictedFaultCount" label="预测故障数" width="110" align="center" />
        <el-table-column label="置信区间" width="120" align="center">
          <template slot-scope="{ row }">
            [{{ row.faultCountLower }}-{{ row.faultCountUpper }}]
          </template>
        </el-table-column>
        <el-table-column prop="modelType" label="模型类型" min-width="150" show-overflow-tooltip />
      </el-table>

      <div slot="footer">
        <el-button @click="historyDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'FaultPrediction',
  data() {
    return {
      // Dashboard 数据
      dashboard: {
        totalDevices: 0,
        highRiskCount: 0,
        avgProbability: 0,
        totalPredictedFaults: 0
      },

      // 查询参数
      queryParams: {
        deviceCode: '',
        minProbability: null,
        page: 1,
        pageSize: 20
      },

      // 表格数据
      predictionList: [],
      total: 0,
      tableLoading: false,

      // 高风险设备
      highRiskDevices: [],

      // 预测历史
      historyDialogVisible: false,
      historyLoading: false,
      historyList: [],
      selectedDevice: {},

      // 自动刷新
      autoRefresh: false,
      refreshTimer: null
    }
  },

  computed: {
    permissions() {
      return this.$store.state.permissions || []
    }
  },

  mounted() {
    this.loadDashboard()
    this.loadPredictionList()
    this.loadHighRiskDevices()
  },

  beforeDestroy() {
    this.stopAutoRefresh()
  },

  watch: {
    autoRefresh(val) {
      if (val) {
        this.startAutoRefresh()
      } else {
        this.stopAutoRefresh()
      }
    }
  },

  methods: {
    // ============================================================
    // 数据加载
    // ============================================================

    /**
     * 加载 Dashboard 统计数据
     */
    async loadDashboard() {
      try {
        // TODO: 调用 Dashboard API
        // const res = await this.$http.get('/api/phm/prediction/dashboard')
        // this.dashboard = res.data

        // 临时使用模拟数据
        this.dashboard = {
          totalDevices: 10,
          highRiskCount: 3,
          avgProbability: 0.65,
          totalPredictedFaults: 85
        }
      } catch (error) {
        console.error('加载Dashboard失败:', error)
      }
    },

    /**
     * 加载预测结果列表（分页）
     */
    async loadPredictionList() {
      this.tableLoading = true
      try {
        const res = await this.$http.get('/phm/prediction/latest', {
          params: {
            deviceCode: this.queryParams.deviceCode || null,
            minProbability: this.queryParams.minProbability,
            page: this.queryParams.page,
            pageSize: this.queryParams.pageSize
          }
        })

        if (res.data.code === 200) {
          this.predictionList = res.data.data || []
          this.total = res.data.total || 0
        } else {
          this.$message.error(res.data.message || '加载预测列表失败')
        }
      } catch (error) {
        console.error('加载预测列表失败:', error)
        this.$message.error('加载预测列表失败：' + error.message)
      } finally {
        this.tableLoading = false
      }
    },

    /**
     * 加载高风险设备列表
     */
    async loadHighRiskDevices() {
      try {
        const res = await this.$http.get('/phm/prediction/high-risk', {
          params: { threshold: 0.7, limit: 10 }
        })

        if (res.data.code === 200) {
          this.highRiskDevices = res.data.data || []
        }
      } catch (error) {
        console.error('加载高风险设备失败:', error)
      }
    },

    /**
     * 查询设备预测历史
     */
    async loadPredictionHistory(deviceId, months = 12) {
      this.historyLoading = true
      try {
        const res = await this.$http.get(`/phm/prediction/history/${deviceId}`, {
          params: { months }
        })

        if (res.data.code === 200) {
          this.historyList = res.data.data || []
        } else {
          this.$message.error(res.data.message || '加载预测历史失败')
        }
      } catch (error) {
        console.error('加载预测历史失败:', error)
        this.$message.error('加载预测历史失败：' + error.message)
      } finally {
        this.historyLoading = false
      }
    },

    // ============================================================
    // 事件处理
    // ============================================================

    /**
     * 重新预测
     */
    async handleRePredict(row) {
      this.$confirm(`确定要重新预测设备 ${row.deviceCode} 的故障情况吗？`, '确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        const loading = this.$loading({ text: '正在预测...' })
        try {
          const res = await this.$http.post(`/phm/prediction/predict/${row.deviceId}`, null, {
            params: { predictionDays: 90 }
          })

          if (res.data.code === 200) {
            this.$message.success('预测成功')
            this.loadPredictionList()
            this.loadHighRiskDevices()
          } else {
            this.$message.error(res.data.message || '预测失败')
          }
        } catch (error) {
          console.error('预测失败:', error)
          this.$message.error('预测失败：' + error.message)
        } finally {
          loading.close()
        }
      })
    },

    /**
     * 查看预测历史
     */
    handleViewHistory(row) {
      this.selectedDevice = row
      this.historyDialogVisible = true
      this.loadPredictionHistory(row.deviceId)
    },

    /**
     * 关闭历史对话框
     */
    handleHistoryDialogClose() {
      this.historyList = []
      this.selectedDevice = {}
    },

    /**
     * 查询
     */
    handleQuery() {
      this.queryParams.page = 1
      this.loadPredictionList()
    },

    /**
     * 重置
     */
    handleReset() {
      this.queryParams = {
        deviceCode: '',
        minProbability: null,
        page: 1,
        pageSize: 20
      }
      this.loadPredictionList()
    },

    /**
     * 分页 - 改变每页大小
     */
    handleSizeChange(val) {
      this.queryParams.pageSize = val
      this.queryParams.page = 1
      this.loadPredictionList()
    },

    /**
     * 分页 - 改变当前页
     */
    handlePageChange(val) {
      this.queryParams.page = val
      this.loadPredictionList()
    },

    // ============================================================
    // 自动刷新
    // ============================================================

    startAutoRefresh() {
      this.refreshTimer = setInterval(() => {
        this.loadPredictionList()
        this.loadHighRiskDevices()
      }, 30000) // 每30秒刷新
    },

    stopAutoRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 格式化百分比
     */
    formatPercent(value) {
      if (value == null || isNaN(value)) return '0%'
      return (value * 100).toFixed(1) + '%'
    },

    /**
     * 获取故障概率标签类型
     */
    getProbabilityTagType(probability) {
      if (probability >= 0.7) return 'danger'
      if (probability >= 0.5) return 'warning'
      return 'success'
    },

    /**
     * 权限检查
     */
    hasPermission(permission) {
      return this.permissions.includes(permission)
    }
  }
}
</script>

<style scoped>
.fault-prediction-page {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: 100vh;
}

/* ============================================================
   统计卡片样式
   ============================================================ */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
  opacity: 0.8;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-critical {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-warning {
  background: linear-gradient(135deg, #fbc2eb 0%, #f6a192 100%);
  color: white;
}

.stat-info {
  background: linear-gradient(135deg, #a8edea 0%, #7ec9c4 100%);
  color: white;
}

/* ============================================================
   工具栏样式
   ============================================================ */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
}

/* ============================================================
   表格卡片样式
   ============================================================ */
.table-card {
  margin-bottom: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.card-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

/* ============================================================
   高风险卡片样式
   ============================================================ */
.high-risk-card {
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: 2px solid #F56C6C;
}

.high-risk-card >>> .el-card__header {
  background-color: #FEF0F0;
  border-bottom: 1px solid #F56C6C;
}
</style>
