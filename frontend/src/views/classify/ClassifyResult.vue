<template>
  <div class="classify-result-page">
    <!-- ============================================================
         顶部操作栏：筛选条件 + 操作按钮
         ============================================================ -->
    <div class="toolbar-row">
      <div class="filter-area">
        <!-- ABC分类过滤 -->
        <el-select v-model="filter.abcClass" placeholder="ABC分类" clearable style="width:110px; margin-right:10px" @change="fetchList">
          <el-option label="A类（关键）" value="A" />
          <el-option label="B类（重要）" value="B" />
          <el-option label="C类（一般）" value="C" />
        </el-select>

        <!-- XYZ分类过滤 -->
        <el-select v-model="filter.xyzClass" placeholder="XYZ分类" clearable style="width:120px; margin-right:10px" @change="fetchList">
          <el-option label="X类（稳定）" value="X" />
          <el-option label="Y类（波动）" value="Y" />
          <el-option label="Z类（随机）" value="Z" />
        </el-select>

        <!-- 备件编码关键词 -->
        <el-input
          v-model="filter.partCode"
          placeholder="备件编码搜索"
          clearable
          style="width:160px; margin-right:10px"
          @clear="fetchList"
          @keyup.enter.native="fetchList"
        >
          <el-button slot="append" icon="el-icon-search" @click="fetchList" />
        </el-input>

        <!-- 月份选择 -->
        <el-date-picker
          v-model="filter.month"
          type="month"
          placeholder="选择月份"
          format="yyyy-MM"
          value-format="yyyy-MM"
          clearable
          style="width:160px; margin-right:10px"
          @change="fetchList"
        />
      </div>

      <div class="action-area">
        <!-- 导出Excel -->
        <el-button type="success" icon="el-icon-download" :loading="exporting" @click="handleExport">
          导出Excel
        </el-button>
        <!-- 手动触发重算（仅ADMIN可见） -->
        <el-button
          v-if="hasPermission('classify:trigger:run')"
          type="warning"
          icon="el-icon-refresh"
          :loading="triggering"
          @click="handleTrigger"
          style="margin-left:10px"
        >
          手动触发重算
        </el-button>
      </div>
    </div>

    <!-- ============================================================
         ABC×XYZ 热力矩阵图（ECharts）
         ============================================================ -->
    <el-card class="matrix-card" shadow="never">
      <div slot="header">
        <span>ABC × XYZ 分布热力矩阵</span>
        <span class="matrix-tip">（点击格子可过滤列表）</span>
      </div>
      <div v-if="matrixLoading" class="matrix-placeholder">
        <i class="el-icon-loading" /> 加载中...
      </div>
      <div v-else-if="!echartsAvailable" class="matrix-placeholder echarts-notice">
        <i class="el-icon-info" />
        ECharts 图表需先执行 <code>npm install</code> 安装依赖后才可显示。
        <div class="matrix-fallback">
          <table class="fallback-matrix">
            <thead>
              <tr>
                <th></th>
                <th v-for="xyz in ['X（稳定）','Y（波动）','Z（随机）']" :key="xyz">{{ xyz }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="abc in ['A','B','C']" :key="abc">
                <td class="row-label">{{ abc }}类</td>
                <td
                  v-for="xyz in ['X','Y','Z']"
                  :key="xyz"
                  class="matrix-cell"
                  :class="getCellClass(abc + xyz)"
                  @click="filterByCell(abc, xyz)"
                >
                  {{ matrixData[abc + xyz] || 0 }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-else ref="matrixChart" class="matrix-chart" />
    </el-card>

    <!-- ============================================================
         分类结果数据表格
         ============================================================ -->
    <el-card class="table-card" shadow="never">
      <div slot="header">
        <span>分类结果明细</span>
        <span class="table-tip">共 {{ total }} 条记录</span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe style="width:100%">
        <el-table-column prop="partCode"  label="备件编码"  width="110" />
        <el-table-column prop="partName"  label="备件名称"  min-width="150" show-overflow-tooltip />

        <!-- ABC类别：A=危险红，B=警告黄，C=成功绿 -->
        <el-table-column label="ABC类别" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag
              :type="row.abcClass === 'A' ? 'danger' : row.abcClass === 'B' ? 'warning' : 'success'"
              size="small"
            >
              {{ row.abcClass }}类
            </el-tag>
          </template>
        </el-table-column>

        <!-- XYZ类别 -->
        <el-table-column label="XYZ类别" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag
              :type="row.xyzClass === 'X' ? 'primary' : row.xyzClass === 'Y' ? 'info' : ''"
              size="small"
            >
              {{ row.xyzClass }}类
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="compositeScore" label="综合得分"  width="90"  align="right">
          <template slot-scope="{ row }">{{ formatDecimal(row.compositeScore) }}</template>
        </el-table-column>
        <el-table-column prop="annualCost"     label="年消耗金额" width="110" align="right">
          <template slot-scope="{ row }">¥ {{ formatDecimal(row.annualCost) }}</template>
        </el-table-column>
        <el-table-column prop="cv2"            label="CV²"       width="80"  align="right">
          <template slot-scope="{ row }">{{ formatDecimal(row.cv2, 4) }}</template>
        </el-table-column>
        <el-table-column prop="safetyStock"    label="安全库存(SS)" width="110" align="right" />
        <el-table-column prop="reorderPoint"   label="补货触发点(ROP)" width="130" align="right" />
        <el-table-column prop="strategyCode"   label="策略编码"  width="90"  align="center">
          <template slot-scope="{ row }">
            <el-tag size="small" effect="plain">{{ row.strategyCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="classifyMonth"  label="分类月份"  width="100" align="center" />
        <el-table-column prop="createTime"     label="记录时间"  width="160">
          <template slot-scope="{ row }">{{ formatTime(row.createTime) }}</template>
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
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'ClassifyResult',

  data() {
    return {
      // 筛选条件
      filter: {
        abcClass: '',
        xyzClass: '',
        partCode: '',
        month: ''
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

      // 矩阵热力图数据
      matrixData: {},
      matrixLoading: false,
      matrixChart: null,       // ECharts 实例
      echartsAvailable: false, // ECharts 是否已安装

      // 操作状态
      exporting: false,
      triggering: false
    }
  },

  computed: {
    // 从 Vuex store 读取当前用户的权限码列表
    permissions() {
      return this.$store.state.permissions || []
    }
  },

  created() {
    this.fetchList()
    this.fetchMatrix()
  },

  mounted() {
    // 异步加载 ECharts，如果未安装则降级显示
    this.initECharts()
    // 窗口大小变化时重绘图表
    window.addEventListener('resize', this.resizeChart)
  },

  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart)
    if (this.matrixChart) {
      this.matrixChart.dispose()
    }
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

    /** 加载分类结果列表 */
    async fetchList() {
      this.loading = true
      try {
        const res = await request.get('/classify/result', {
          params: {
            abcClass:  this.filter.abcClass  || undefined,
            xyzClass:  this.filter.xyzClass  || undefined,
            partCode:  this.filter.partCode  || undefined,
            month:     this.filter.month     || undefined,
            page:      this.pagination.page,
            pageSize:  this.pagination.pageSize
          }
        })
        this.tableData = res.data.list || []
        this.total     = res.data.total || 0
      } catch (e) {
        this.$message.error('获取分类结果失败')
      } finally {
        this.loading = false
      }
    },

    /** 加载9格矩阵数据 */
    async fetchMatrix() {
      this.matrixLoading = true
      try {
        const res = await request.get('/classify/matrix')
        this.matrixData = res.data || {}
        this.renderMatrix()
      } catch (e) {
        this.$message.error('获取矩阵数据失败')
      } finally {
        this.matrixLoading = false
      }
    },

    // ================================================================
    // ECharts 热力矩阵渲染
    // ================================================================

    /** 动态加载 ECharts（如果已安装） */
    async initECharts() {
      try {
        const echarts = await import('echarts')
        this.echarts = echarts
        this.echartsAvailable = true
        // 等待 DOM 渲染完成后初始化图表
        this.$nextTick(() => {
          if (this.$refs.matrixChart) {
            this.matrixChart = echarts.init(this.$refs.matrixChart)
            this.renderMatrix()
          }
        })
      } catch (e) {
        // ECharts 未安装，降级显示 HTML 表格
        this.echartsAvailable = false
      }
    },

    /** 渲染 ECharts 热力矩阵 */
    renderMatrix() {
      if (!this.matrixChart || !this.echartsAvailable) return

      const abcList = ['C', 'B', 'A']  // Y轴从下到上
      const xyzList = ['X', 'Y', 'Z']  // X轴从左到右

      // 组装 heatmap 数据：[xyzIndex, abcIndex, count]
      const heatmapData = []
      for (let xi = 0; xi < xyzList.length; xi++) {
        for (let ai = 0; ai < abcList.length; ai++) {
          const key = abcList[ai] + xyzList[xi]
          heatmapData.push([xi, ai, this.matrixData[key] || 0])
        }
      }

      const option = {
        tooltip: {
          formatter: (params) => {
            const abc = abcList[params.data[1]]
            const xyz = xyzList[params.data[0]]
            return `${abc}${xyz}：${params.data[2]} 个备件`
          }
        },
        grid: { top: '10%', left: '10%', right: '5%', bottom: '10%' },
        xAxis: {
          type: 'category',
          data: ['X（需求稳定）', 'Y（需求波动）', 'Z（需求随机）'],
          name: 'XYZ分类',
          nameLocation: 'middle',
          nameGap: 30
        },
        yAxis: {
          type: 'category',
          data: ['C类（一般）', 'B类（重要）', 'A类（关键）'],
          name: 'ABC分类',
          nameLocation: 'middle',
          nameGap: 40
        },
        visualMap: {
          min: 0,
          max: Math.max(...Object.values(this.matrixData), 1),
          calculable: true,
          orient: 'horizontal',
          left: 'center',
          bottom: '0%',
          inRange: { color: ['#f0f9ff', '#0369a1'] }  // 浅蓝到深蓝
        },
        series: [{
          type: 'heatmap',
          data: heatmapData,
          label: {
            show: true,
            formatter: (params) => params.data[2],
            fontSize: 16,
            fontWeight: 'bold'
          },
          emphasis: {
            itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' }
          }
        }]
      }

      this.matrixChart.setOption(option)

      // 点击格子：过滤列表
      this.matrixChart.off('click')
      this.matrixChart.on('click', (params) => {
        if (params.componentType === 'series') {
          const abc = abcList[params.data[1]]
          const xyz = xyzList[params.data[0]]
          this.filterByCell(abc, xyz)
        }
      })
    },

    /** 窗口尺寸变化时重绘 */
    resizeChart() {
      if (this.matrixChart) {
        this.matrixChart.resize()
      }
    },

    // ================================================================
    // 交互操作
    // ================================================================

    /** 点击矩阵格子，过滤列表 */
    filterByCell(abc, xyz) {
      this.filter.abcClass = abc
      this.filter.xyzClass = xyz
      this.pagination.page = 1
      this.fetchList()
      this.$message.info(`已过滤：${abc}类 × ${xyz}类`)
    },

    /** 表格行降级矩阵的背景色辅助方法 */
    getCellClass(key) {
      const val = this.matrixData[key] || 0
      if (val === 0) return 'cell-empty'
      if (val > 20) return 'cell-high'
      if (val > 5)  return 'cell-medium'
      return 'cell-low'
    },

    /** 手动触发分类重算（ADMIN专属） */
    handleTrigger() {
      this.$confirm(
        '确认重新触发分类计算？此操作将更新所有备件的分类结果和库存策略参数',
        '确认触发',
        { type: 'warning', confirmButtonText: '确认触发', cancelButtonText: '取消' }
      ).then(async () => {
        this.triggering = true
        try {
          const res = await request.post('/classify/trigger')
          this.$message.success(res.data.message || '任务已启动，请稍后刷新查看最新分类结果')
        } catch (e) {
          const msg = e.response?.data?.message || '触发失败，请检查权限或联系管理员'
          this.$message.error(msg)
        } finally {
          this.triggering = false
        }
      }).catch(() => {})
    },

    /** 导出 Excel */
    async handleExport() {
      if (this.tableData.length === 0) {
        this.$message.warning('暂无数据可导出')
        return
      }
      this.exporting = true
      try {
        // 动态导入 xlsx 和 file-saver
        const XLSX       = await import('xlsx')
        const { saveAs } = await import('file-saver')

        const headers = [
          '备件编码', '备件名称', 'ABC类别', 'XYZ类别', '综合得分',
          '年消耗金额', 'CV²', '安全库存', '补货触发点', '策略编码',
          '分类月份', '记录时间'
        ]
        const rows = this.tableData.map(row => [
          row.partCode,
          row.partName,
          row.abcClass,
          row.xyzClass,
          row.compositeScore,
          row.annualCost,
          row.cv2,
          row.safetyStock,
          row.reorderPoint,
          row.strategyCode,
          row.classifyMonth,
          this.formatTime(row.createTime)
        ])

        const wsData = [headers, ...rows]
        const ws     = XLSX.utils.aoa_to_sheet(wsData)
        const wb     = XLSX.utils.book_new()
        XLSX.utils.book_append_sheet(wb, ws, '备件分类结果')
        const buf  = XLSX.write(wb, { type: 'array', bookType: 'xlsx' })
        const blob = new Blob([buf], { type: 'application/octet-stream' })
        const month = this.filter.month || new Date().toISOString().slice(0, 7)
        saveAs(blob, `备件分类结果_${month}.xlsx`)
        this.$message.success('导出成功')
      } catch (e) {
        this.$message.error('导出失败，请确认已执行 npm install 安装 xlsx 和 file-saver')
      } finally {
        this.exporting = false
      }
    },

    // ================================================================
    // 分页
    // ================================================================
    handlePageChange(page) {
      this.pagination.page = page
      this.fetchList()
    },
    handleSizeChange(size) {
      this.pagination.pageSize = size
      this.pagination.page = 1
      this.fetchList()
    },

    // ================================================================
    // 格式化工具方法
    // ================================================================

    /** 格式化小数（默认2位） */
    formatDecimal(val, places = 2) {
      if (val === null || val === undefined) return '—'
      return Number(val).toFixed(places)
    },

    /** 格式化时间字符串（去除T符号） */
    formatTime(t) {
      if (!t) return '—'
      return String(t).replace('T', ' ').slice(0, 19)
    }
  }
}
</script>

<style scoped>
.classify-result-page {
  padding: 16px;
}

/* 顶部工具栏 */
.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-area {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}

/* 矩阵卡片 */
.matrix-card {
  margin-bottom: 16px;
}

.matrix-tip {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.matrix-chart {
  height: 280px;
  width: 100%;
}

.matrix-placeholder {
  height: 60px;
  display: flex;
  align-items: center;
  color: #999;
  font-size: 14px;
  gap: 6px;
}

.echarts-notice {
  flex-direction: column;
  align-items: flex-start;
  height: auto;
  padding: 10px 0;
}

/* 降级HTML矩阵表格 */
.matrix-fallback {
  margin-top: 12px;
}

.fallback-matrix {
  border-collapse: collapse;
  text-align: center;
}

.fallback-matrix th,
.fallback-matrix td {
  border: 1px solid #ddd;
  padding: 10px 20px;
  min-width: 80px;
}

.fallback-matrix th {
  background: #f5f7fa;
  font-weight: bold;
}

.row-label {
  background: #f5f7fa;
  font-weight: bold;
}

.matrix-cell {
  cursor: pointer;
  transition: background 0.2s;
}

.matrix-cell:hover {
  background: #e6f3ff !important;
}

.cell-empty  { background: #f9f9f9; color: #ccc; }
.cell-low    { background: #dbeafe; }
.cell-medium { background: #93c5fd; }
.cell-high   { background: #1d4ed8; color: #fff; }

/* 表格卡片 */
.table-card {
  margin-bottom: 16px;
}

.table-tip {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

/* 分页 */
.pagination-area {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
