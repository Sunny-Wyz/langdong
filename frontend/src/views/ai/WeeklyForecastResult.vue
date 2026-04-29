<template>
  <div class="weekly-forecast-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="备件编码">
          <el-input
            v-model="searchForm.partCode"
            placeholder="请输入备件编码"
            clearable
            @keyup.enter.native="handleSearch"
          ></el-input>
        </el-form-item>
        <el-form-item label="起始周">
          <el-date-picker
            v-model="searchForm.weekStart"
            type="week"
            placeholder="选择周"
            value-format="yyyy-MM-dd"
            clearable
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="算法">
          <el-select v-model="searchForm.algoType" placeholder="全部" clearable>
            <el-option label="TFT (规律需求)" value="TFT"></el-option>
            <el-option label="DeepAR (间歇需求)" value="DeepAR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <div slot="header" class="card-header">
        <span>周粒度深度学习预测</span>
        <div class="header-actions">
          <el-button
            type="success"
            size="small"
            icon="el-icon-cpu"
            :loading="trainLoading"
            @click="triggerTrain"
          >训练模型</el-button>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-loading"
            @click="$router.push('/ai/training-progress')"
          >训练进度</el-button>
          <el-button
            type="warning"
            size="small"
            icon="el-icon-s-promotion"
            :loading="predictLoading"
            @click="triggerPredict"
          >触发预测</el-button>
        </div>
      </div>
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
        @row-click="showDetail"
        highlight-current-row
      >
        <el-table-column prop="partCode" label="备件编码" width="130" fixed>
          <template slot-scope="scope">
            <el-tag size="small" type="info">{{ scope.row.partCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="partName" label="备件名称" width="180"></el-table-column>
        <el-table-column prop="weekStart" label="预测周" width="120" align="center"></el-table-column>
        <el-table-column prop="predictQty" label="预测量(p50)" width="120" align="right">
          <template slot-scope="scope">
            <span class="predict-value">{{ scope.row.predictQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分位数区间" width="200" align="center">
          <template slot-scope="scope">
            <span class="quantile-range">
              [{{ scope.row.p10 || '-' }}, {{ scope.row.p25 || '-' }}]
              <b>{{ scope.row.predictQty }}</b>
              [{{ scope.row.p75 || '-' }}, {{ scope.row.p90 || '-' }}]
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="algoType" label="算法" width="100" align="center">
          <template slot-scope="scope">
            <el-tag
              :type="scope.row.algoType === 'TFT' ? 'success' : 'warning'"
              size="mini"
            >{{ scope.row.algoType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modelVersion" label="版本" width="90" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" type="info">{{ scope.row.modelVersion }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="预测时间" width="170"></el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template slot-scope="scope">
            <el-button
              type="text"
              size="small"
              icon="el-icon-data-analysis"
              @click.stop="showDetail(scope.row)"
            >趋势</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        :current-page="pagination.page"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="pagination.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
      ></el-pagination>
    </el-card>

    <!-- 趋势图弹窗 -->
    <el-dialog :title="detailTitle" :visible.sync="detailVisible" width="75%">
      <div v-if="detailData.length > 0">
        <div ref="chartContainer" style="width:100%;height:400px;"></div>
        <el-table :data="detailData" stripe border size="small" style="margin-top: 16px;">
          <el-table-column prop="weekStart" label="周起始" width="120"></el-table-column>
          <el-table-column prop="predictQty" label="p50" width="100" align="right"></el-table-column>
          <el-table-column prop="p10" label="p10" width="80" align="right"></el-table-column>
          <el-table-column prop="p25" label="p25" width="80" align="right"></el-table-column>
          <el-table-column prop="p75" label="p75" width="80" align="right"></el-table-column>
          <el-table-column prop="p90" label="p90" width="80" align="right"></el-table-column>
          <el-table-column prop="algoType" label="算法" width="80"></el-table-column>
        </el-table>
      </div>
      <div v-else>
        <el-empty description="暂无预测数据"></el-empty>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'WeeklyForecastResult',
  data() {
    return {
      searchForm: {
        partCode: '',
        weekStart: '',
        algoType: ''
      },
      tableData: [],
      loading: false,
      trainLoading: false,
      predictLoading: false,
      pagination: {
        page: 1,
        pageSize: 20,
        total: 0
      },
      detailVisible: false,
      detailTitle: '',
      detailData: [],
      chartInstance: null
    }
  },
  mounted() {
    this.fetchList()
  },
  beforeDestroy() {
    if (this.chartInstance) {
      this.chartInstance.dispose()
    }
  },
  methods: {
    fetchList() {
      this.loading = true
      const params = {
        page: this.pagination.page,
        pageSize: this.pagination.pageSize,
        ...this.searchForm
      }
      this.$http.get('/ai/weekly/list', { params })
        .then(res => {
          if (res.data.code === 200) {
            this.tableData = res.data.data || []
            this.pagination.total = res.data.total || 0
          }
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleSearch() {
      this.pagination.page = 1
      this.fetchList()
    },
    resetSearch() {
      this.searchForm = { partCode: '', weekStart: '', algoType: '' }
      this.handleSearch()
    },
    handleSizeChange(size) {
      this.pagination.pageSize = size
      this.fetchList()
    },
    handlePageChange(page) {
      this.pagination.page = page
      this.fetchList()
    },
    triggerTrain() {
      this.$confirm('将使用合成数据训练 TFT/DeepAR 模型，是否继续？', '训练确认', {
        type: 'info'
      }).then(() => {
        this.trainLoading = true
        this.$http.post('/ai/weekly/train', { use_synthetic: true })
          .then(res => {
            this.$message.success('训练任务已提交后台，请等待完成后触发预测')
            this.$router.push('/ai/training-progress')
          })
          .catch(err => {
            this.$message.error(err.response?.data?.message || '训练请求失败或已有训练正在进行')
          })
          .finally(() => {
            this.trainLoading = false
          })
      }).catch(() => {})
    },
    triggerPredict() {
      this.predictLoading = true
      this.$http.post('/ai/weekly/predict', {})
        .then(res => {
          this.$message.success('预测完成，正在刷新数据')
          setTimeout(() => this.fetchList(), 2000)
        })
        .catch(err => {
          this.$message.error(err.response?.data?.message || '预测失败，请确认模型已训练')
        })
        .finally(() => {
          this.predictLoading = false
        })
    },
    showDetail(row) {
      this.detailTitle = `${row.partCode} - ${row.partName || ''} 12周预测趋势`
      this.$http.get(`/ai/weekly/${row.partCode}`, { params: { weeks: 12 } })
        .then(res => {
          if (res.data.code === 200) {
            this.detailData = res.data.data || []
            this.detailVisible = true
            this.$nextTick(() => this.renderChart())
          }
        })
    },
    renderChart() {
      if (!this.$refs.chartContainer) return
      if (this.chartInstance) this.chartInstance.dispose()
      this.chartInstance = echarts.init(this.$refs.chartContainer)

      const weeks = this.detailData.map(d => d.weekStart)
      const p50 = this.detailData.map(d => d.predictQty)
      const p10 = this.detailData.map(d => d.p10)
      const p90 = this.detailData.map(d => d.p90)
      const p25 = this.detailData.map(d => d.p25)
      const p75 = this.detailData.map(d => d.p75)

      this.chartInstance.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['p50 (预测量)', 'p10-p90 区间', 'p25-p75 区间'] },
        xAxis: { type: 'category', data: weeks, name: '周' },
        yAxis: { type: 'value', name: '件数' },
        series: [
          {
            name: 'p10-p90 区间',
            type: 'line',
            data: p90,
            lineStyle: { opacity: 0 },
            areaStyle: { color: 'rgba(64,158,255,0.15)' },
            stack: 'confidence-outer',
            symbol: 'none'
          },
          {
            name: 'p10-p90 下界',
            type: 'line',
            data: p10,
            lineStyle: { opacity: 0 },
            areaStyle: { color: 'rgba(64,158,255,0.15)' },
            stack: 'confidence-outer',
            symbol: 'none'
          },
          {
            name: 'p25-p75 区间',
            type: 'line',
            data: p75,
            lineStyle: { opacity: 0 },
            areaStyle: { color: 'rgba(64,158,255,0.3)' },
            stack: 'confidence-inner',
            symbol: 'none'
          },
          {
            name: 'p25-p75 下界',
            type: 'line',
            data: p25,
            lineStyle: { opacity: 0 },
            areaStyle: { color: 'rgba(64,158,255,0.3)' },
            stack: 'confidence-inner',
            symbol: 'none'
          },
          {
            name: 'p50 (预测量)',
            type: 'line',
            data: p50,
            smooth: true,
            lineStyle: { width: 3, color: '#409EFF' },
            itemStyle: { color: '#409EFF' }
          }
        ]
      })
    }
  }
}
</script>

<style scoped>
.weekly-forecast-container {
  padding: 20px;
}
.search-card {
  margin-bottom: 20px;
}
.search-form {
  display: flex;
  flex-wrap: wrap;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions .el-button {
  margin-left: 8px;
}
.predict-value {
  font-weight: bold;
  color: #409EFF;
}
.quantile-range {
  font-size: 12px;
  color: #909399;
}
.pagination {
  margin-top: 20px;
  text-align: right;
}
</style>
