<template>
  <div class="page-container health-monitor-page">
    <!-- ============================================================
         顶部统计卡片区域
         ============================================================ -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card stat-total" shadow="hover">
          <div class="stat-icon"><i class="el-icon-s-data" /></div>
          <div class="stat-content">
            <div class="stat-label">设备总数</div>
            <div class="stat-value">{{ dashboard.totalDevices || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-healthy" shadow="hover">
          <div class="stat-icon"><i class="el-icon-success" /></div>
          <div class="stat-content">
            <div class="stat-label">健康设备</div>
            <div class="stat-value">{{ dashboard.riskDistribution?.LOW || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-warning" shadow="hover">
          <div class="stat-icon"><i class="el-icon-warning" /></div>
          <div class="stat-content">
            <div class="stat-label">预警设备</div>
            <div class="stat-value">{{ (dashboard.riskDistribution?.MEDIUM || 0) + (dashboard.riskDistribution?.HIGH || 0) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card stat-critical" shadow="hover">
          <div class="stat-icon"><i class="el-icon-error" /></div>
          <div class="stat-content">
            <div class="stat-label">严重风险</div>
            <div class="stat-value">{{ dashboard.riskDistribution?.CRITICAL || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============================================================
         操作栏：手动触发评估 + 自动刷新
         ============================================================ -->
    <div class="toolbar-row">
      <div class="toolbar-left">
        <span class="avg-score-label">平均健康分：</span>
        <el-tag type="info" size="medium">{{ formatDecimal(dashboard.avgHealthScore) }} 分</el-tag>
      </div>
      <div class="toolbar-right">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text=""
          style="margin-right: 10px"
        />
        <el-button
          v-if="hasPermission('phm:health:evaluate')"
          type="primary"
          icon="el-icon-refresh"
          :loading="triggering"
          @click="handleTriggerEvaluation"
        >
          手动触发评估
        </el-button>
      </div>
    </div>

    <!-- ============================================================
         风险设备排行榜
         ============================================================ -->
    <el-card class="ranking-card" shadow="never">
      <div slot="header" class="phead header">
                <i class="el-icon-s-data" />
                <div class="title">风险设备排行榜</div>
                <div class="head-btn-group"><span class="card-tip">（按健康评分升序，最多显示20台）</span>
      
                </div>
            </div>

      <el-table v-loading="rankingLoading" :data="rankingData" border stripe style="width:100%">
        <el-table-column type="index" label="排名" width="60" align="center" />
        <el-table-column prop="deviceCode" label="设备编码" width="120" sortable="custom" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="deviceModel" label="设备型号" width="120" show-overflow-tooltip />

        <el-table-column label="健康评分" width="100" align="center">
          <template slot-scope="{ row }">
            <span :style="{ color: getScoreColor(row.healthScore) }">
              {{ formatDecimal(row.healthScore) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="风险等级" width="100" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="getRiskTagType(row.riskLevel)" size="small">
              {{ getRiskLevelText(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="recordDate" label="评估日期" width="120" align="center" />

        <el-table-column label="操作" width="150" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" size="small" @click="viewTrend(row.deviceId)">
              查看趋势
            </el-button>
            <el-button type="text" size="small" @click="viewDetails(row.deviceId)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============================================================
         健康趋势图（对话框）
         ============================================================ -->
    <el-dialog
      :visible.sync="trendDialogVisible"
      :title="'设备健康趋势 - ' + selectedDeviceName"
      width="900px"
      @close="closeTrendDialog"
    >
      <div v-if="trendLoading" class="chart-placeholder">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="!echartsAvailable" class="chart-placeholder">
        <i class="el-icon-info" /> ECharts 未安装，请执行 npm install
      </div>
      <div v-else ref="trendChart" class="trend-chart" />
    </el-dialog>

    <!-- ============================================================
         设备详情对话框
         ============================================================ -->
    <el-dialog
      :visible.sync="detailDialogVisible"
      :title="'设备健康详情 - ' + selectedDeviceName"
      width="600px"
    >
      <div v-if="detailLoading" class="dialog-loading">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="deviceDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备编码">{{ deviceDetail.deviceCode }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ deviceDetail.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{ deviceDetail.deviceModel }}</el-descriptions-item>
          <el-descriptions-item label="设备重要性">
            <el-tag :type="deviceDetail.importanceLevel === 'CRITICAL' ? 'danger' : deviceDetail.importanceLevel === 'IMPORTANT' ? 'warning' : 'info'" size="small">
              {{ getImportanceLevelText(deviceDetail.importanceLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="健康评分">
            <span :style="{ color: getScoreColor(deviceDetail.healthScore), fontSize: '18px', fontWeight: 'bold' }">
              {{ formatDecimal(deviceDetail.healthScore) }} 分
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="getRiskTagType(deviceDetail.riskLevel)" size="medium">
              {{ getRiskLevelText(deviceDetail.riskLevel) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="运行时长评分">{{ formatDecimal(deviceDetail.runtimeScore) }}</el-descriptions-item>
          <el-descriptions-item label="故障评分">{{ formatDecimal(deviceDetail.faultScore) }}</el-descriptions-item>
          <el-descriptions-item label="工单评分">{{ formatDecimal(deviceDetail.workorderScore) }}</el-descriptions-item>
          <el-descriptions-item label="换件评分">{{ formatDecimal(deviceDetail.replacementScore) }}</el-descriptions-item>
          <el-descriptions-item label="评估日期">{{ deviceDetail.recordDate }}</el-descriptions-item>
          <el-descriptions-item label="算法版本">{{ deviceDetail.algorithmVersion }}</el-descriptions-item>
        </el-descriptions>

        <!-- 雷达图：维度评分 -->
        <div v-if="echartsAvailable" style="margin-top: 20px;">
          <h4 style="margin-bottom: 10px;">维度评分雷达图</h4>
          <div ref="radarChart" class="radar-chart" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'HealthMonitor',

  data() {
    return {
      // Dashboard数据
      dashboard: {
        totalDevices: 0,
        riskDistribution: {},
        avgHealthScore: 0,
        recentAlerts: []
      },

      // 风险设备排行榜
      rankingData: [],
      rankingLoading: false,

      // 操作状态
      triggering: false,
      autoRefresh: false,
      refreshTimer: null,

      // ECharts
      echartsAvailable: false,
      echarts: null,
      trendChart: null,
      radarChart: null,

      // 趋势对话框
      trendDialogVisible: false,
      trendData: [],
      trendLoading: false,
      selectedDeviceId: null,
      selectedDeviceName: '',

      // 详情对话框
      detailDialogVisible: false,
      deviceDetail: null,
      detailLoading: false
    }
  },

  computed: {
    permissions() {
      return this.$store.state.permissions || []
    }
  },

  watch: {
    autoRefresh(val) {
      if (val) {
        this.startAutoRefresh()
      } else {
        this.stopAutoRefresh()
      }
    },

    detailDialogVisible(val) {
      if (val && this.echartsAvailable && this.deviceDetail) {
        this.$nextTick(() => {
          this.renderRadarChart()
        })
      }
    }
  },

  created() {
    this.fetchDashboard()
    this.fetchRanking()
  },

  mounted() {
    this.initECharts()
    window.addEventListener('resize', this.resizeCharts)
  },

  beforeDestroy() {
    this.stopAutoRefresh()
    window.removeEventListener('resize', this.resizeCharts)
    if (this.trendChart) this.trendChart.dispose()
    if (this.radarChart) this.radarChart.dispose()
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
        const res = await request.get('/phm/health/dashboard')
        if (res.data.code === 200) {
          this.dashboard = res.data.data || {}
        }
      } catch (e) {
        this.$message.error('获取Dashboard数据失败')
      }
    },

    /** 加载风险设备排行榜 */
    async fetchRanking() {
      this.rankingLoading = true
      try {
        const res = await request.get('/phm/health/ranking', {
          params: { limit: 20 }
        })
        if (res.data.code === 200) {
          this.rankingData = res.data.data || []
        }
      } catch (e) {
        this.$message.error('获取风险设备排行榜失败')
      } finally {
        this.rankingLoading = false
      }
    },

    /** 加载设备健康趋势 */
    async fetchTrend(deviceId) {
      this.trendLoading = true
      try {
        const endDate = new Date().toISOString().split('T')[0]
        const startDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

        const res = await request.get(`/phm/health/trend/${deviceId}`, {
          params: { startDate, endDate }
        })
        if (res.data.code === 200) {
          this.trendData = res.data.data || []
          this.$nextTick(() => {
            this.renderTrendChart()
          })
        }
      } catch (e) {
        this.$message.error('获取健康趋势失败')
      } finally {
        this.trendLoading = false
      }
    },

    /** 加载设备详情 */
    async fetchDeviceDetail(deviceId) {
      this.detailLoading = true
      try {
        const res = await request.get(`/phm/health/device/${deviceId}/latest`)
        if (res.data.code === 200) {
          this.deviceDetail = res.data.data
        }
      } catch (e) {
        this.$message.error('获取设备详情失败')
      } finally {
        this.detailLoading = false
      }
    },

    // ================================================================
    // ECharts 初始化和渲染
    // ================================================================

    /** 动态加载 ECharts */
    async initECharts() {
      try {
        const echarts = await import('echarts')
        this.echarts = echarts
        this.echartsAvailable = true
      } catch (e) {
        this.echartsAvailable = false
      }
    },

    /** 渲染健康趋势图 */
    renderTrendChart() {
      if (!this.echartsAvailable || !this.$refs.trendChart) return

      if (!this.trendChart) {
        this.trendChart = this.echarts.init(this.$refs.trendChart)
      }

      const dates = this.trendData.map(item => item.recordDate)
      const scores = this.trendData.map(item => item.healthScore)

      const option = {
        title: { text: '健康评分趋势', left: 'center' },
        tooltip: { trigger: 'axis' },
        grid: { top: '15%', left: '10%', right: '10%', bottom: '15%' },
        xAxis: {
          type: 'category',
          data: dates,
          name: '日期'
        },
        yAxis: {
          type: 'value',
          name: '健康评分',
          min: 0,
          max: 100
        },
        series: [{
          type: 'line',
          data: scores,
          smooth: true,
          lineStyle: { width: 3 },
          areaStyle: { opacity: 0.3 },
          markLine: {
            data: [
              { yAxis: 40, name: 'CRITICAL阈值', lineStyle: { color: '#f56c6c' } },
              { yAxis: 60, name: 'HIGH阈值', lineStyle: { color: '#e6a23c' } },
              { yAxis: 80, name: 'MEDIUM阈值', lineStyle: { color: '#409eff' } }
            ]
          }
        }]
      }

      this.trendChart.setOption(option)
    },

    /** 渲染雷达图 */
    renderRadarChart() {
      if (!this.echartsAvailable || !this.$refs.radarChart || !this.deviceDetail) return

      if (this.radarChart) {
        this.radarChart.dispose()
      }
      this.radarChart = this.echarts.init(this.$refs.radarChart)

      const option = {
        tooltip: {},
        radar: {
          indicator: [
            { name: '运行时长评分', max: 100 },
            { name: '故障评分', max: 100 },
            { name: '工单评分', max: 100 },
            { name: '换件评分', max: 100 }
          ]
        },
        series: [{
          type: 'radar',
          data: [{
            value: [
              this.deviceDetail.runtimeScore || 0,
              this.deviceDetail.faultScore || 0,
              this.deviceDetail.workorderScore || 0,
              this.deviceDetail.replacementScore || 0
            ],
            name: '维度评分'
          }],
          areaStyle: { opacity: 0.3 }
        }]
      }

      this.radarChart.setOption(option)
    },

    /** 窗口大小变化时重绘 */
    resizeCharts() {
      if (this.trendChart) this.trendChart.resize()
      if (this.radarChart) this.radarChart.resize()
    },

    // ================================================================
    // 交互操作
    // ================================================================

    /** 手动触发批量评估 */
    handleTriggerEvaluation() {
      this.$confirm(
        '确认手动触发批量健康评估？将对所有设备进行评估',
        '确认触发',
        { type: 'warning' }
      ).then(async () => {
        this.triggering = true
        try {
          const res = await request.post('/phm/health/batch-evaluate')
          if (res.data.code === 200) {
            this.$message.success(res.data.message || '评估任务已启动')
            setTimeout(() => {
              this.fetchDashboard()
              this.fetchRanking()
            }, 2000)
          }
        } catch (e) {
          this.$message.error('触发失败：' + (e.response?.data?.message || '未知错误'))
        } finally {
          this.triggering = false
        }
      }).catch(() => {})
    },

    /** 查看健康趋势 */
    viewTrend(deviceId) {
      const device = this.rankingData.find(d => d.deviceId === deviceId)
      this.selectedDeviceId = deviceId
      this.selectedDeviceName = device ? `${device.deviceCode} - ${device.deviceName}` : ''
      this.trendDialogVisible = true
      this.fetchTrend(deviceId)
    },

    /** 关闭趋势对话框 */
    closeTrendDialog() {
      if (this.trendChart) {
        this.trendChart.dispose()
        this.trendChart = null
      }
    },

    /** 查看设备详情 */
    viewDetails(deviceId) {
      const device = this.rankingData.find(d => d.deviceId === deviceId)
      this.selectedDeviceId = deviceId
      this.selectedDeviceName = device ? `${device.deviceCode} - ${device.deviceName}` : ''
      this.detailDialogVisible = true
      this.fetchDeviceDetail(deviceId)
    },

    /** 启动自动刷新 */
    startAutoRefresh() {
      this.refreshTimer = setInterval(() => {
        this.fetchDashboard()
        this.fetchRanking()
      }, 5 * 60 * 1000) // 5分钟刷新一次
    },

    /** 停止自动刷新 */
    stopAutoRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },

    // ================================================================
    // 辅助方法
    // ================================================================

    formatDecimal(val, digits = 2) {
      return val != null ? Number(val).toFixed(digits) : '0.00'
    },

    getScoreColor(score) {
      if (score >= 80) return '#67c23a'
      if (score >= 60) return '#409eff'
      if (score >= 40) return '#e6a23c'
      return '#f56c6c'
    },

    getRiskTagType(riskLevel) {
      const map = {
        CRITICAL: 'danger',
        HIGH: 'warning',
        MEDIUM: 'info',
        LOW: 'success'
      }
      return map[riskLevel] || ''
    },

    getRiskLevelText(riskLevel) {
      const map = {
        CRITICAL: '严重',
        HIGH: '高风险',
        MEDIUM: '中等',
        LOW: '健康'
      }
      return map[riskLevel] || riskLevel
    },

    getImportanceLevelText(level) {
      const map = {
        CRITICAL: '关键设备',
        IMPORTANT: '重要设备',
        NORMAL: '一般设备'
      }
      return map[level] || level
    }
  }
}
</script>

<style scoped>
.health-monitor-page {
  padding: 20px;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-total .stat-icon { color: #409eff; }
.stat-total .stat-value { color: #409eff; }

.stat-healthy .stat-icon { color: #67c23a; }
.stat-healthy .stat-value { color: #67c23a; }

.stat-warning .stat-icon { color: #e6a23c; }
.stat-warning .stat-value { color: #e6a23c; }

.stat-critical .stat-icon { color: #f56c6c; }
.stat-critical .stat-value { color: #f56c6c; }

/* 工具栏 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.avg-score-label {
  font-size: 14px;
  color: #606266;
  margin-right: 10px;
}

/* 卡片 */
.ranking-card {
  margin-bottom: 20px;
}

.card-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
}

/* 图表 */
.trend-chart {
  width: 100%;
  height: 400px;
}

.radar-chart {
  width: 100%;
  height: 300px;
}

.chart-placeholder {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 16px;
}

.dialog-loading {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}
</style>
