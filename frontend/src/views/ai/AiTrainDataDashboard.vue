<template>
  <div class="page-container ai-train-data-container">
    <el-card shadow="hover">
      <div slot="header" class="phead header">
        <i class="el-icon-data-analysis" />
        <div class="title">训练数据看板</div>
      </div>

      <el-form :inline="true" :model="searchForm" class="search-form" size="small">
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            value-format="yyyy-MM-dd"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
          />
        </el-form-item>
        <el-form-item label="备件编码">
          <el-input v-model.trim="searchForm.partCode" placeholder="如 C0100002" clearable />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="searchForm.sourceLevel" clearable placeholder="全部" style="width: 140px">
            <el-option label="TRACE" value="TRACE" />
            <el-option label="REQ_OUT" value="REQ_OUT" />
            <el-option label="TRACE_REQ" value="TRACE_REQ" />
            <el-option label="NONE" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="插补">
          <el-select v-model="searchForm.isImputed" clearable placeholder="全部" style="width: 100px">
            <el-option label="是" :value="1" />
            <el-option label="否" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh-left" @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border v-loading="loading" style="width: 100%">
        <el-table-column prop="bizDate" label="业务日期" width="110" />
        <el-table-column prop="partCode" label="备件编码" width="120" />
        <el-table-column prop="dailyOutboundQty" label="日出库量" width="95" />
        <el-table-column prop="dailyRequisitionApplyQty" label="领用申请量" width="105" />
        <el-table-column prop="dailyRequisitionOutQty" label="领用出库量" width="105" />
        <el-table-column prop="dailyInstallQty" label="安装量" width="85" />
        <el-table-column prop="dailyWorkOrderCnt" label="工单数" width="85" />
        <el-table-column prop="dailyPurchaseArrivalQty" label="到货量" width="85" />
        <el-table-column prop="sourceLevel" label="来源" width="95">
          <template slot-scope="scope">
            <el-tag size="small" :type="sourceTagType(scope.row.sourceLevel)">{{ scope.row.sourceLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isImputed" label="插补" width="70">
          <template slot-scope="scope">
            <span>{{ scope.row.isImputed === 1 ? '是' : '否' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" />
      </el-table>

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="page"
          :page-sizes="[20, 50, 100]"
          :page-size="size"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
        />
      </div>
    </el-card>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'AiTrainDataDashboard',
  data() {
    return {
      searchForm: {
        dateRange: [],
        partCode: '',
        sourceLevel: '',
        isImputed: null
      },
      tableData: [],
      loading: false,
      page: 1,
      size: 20,
      total: 0
    }
  },
  created() {
    this.fetchData()
  },
  methods: {
    fetchData() {
      this.loading = true
      const [startDate, endDate] = this.searchForm.dateRange || []
      request
        .get('/ai/train-data/list', {
          params: {
            page: this.page,
            size: this.size,
            startDate,
            endDate,
            partCode: this.searchForm.partCode,
            sourceLevel: this.searchForm.sourceLevel,
            isImputed: this.searchForm.isImputed
          }
        })
        .then(res => {
          const data = res.data || {}
          this.tableData = data.list || []
          this.total = data.total || 0
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleSearch() {
      this.page = 1
      this.fetchData()
    },
    resetSearch() {
      this.searchForm = {
        dateRange: [],
        partCode: '',
        sourceLevel: '',
        isImputed: null
      }
      this.page = 1
      this.fetchData()
    },
    handleSizeChange(val) {
      this.size = val
      this.page = 1
      this.fetchData()
    },
    handleCurrentChange(val) {
      this.page = val
      this.fetchData()
    },
    sourceTagType(sourceLevel) {
      if (sourceLevel === 'TRACE') return 'success'
      if (sourceLevel === 'REQ_OUT') return 'warning'
      if (sourceLevel === 'TRACE_REQ') return 'primary'
      return 'info'
    }
  }
}
</script>

<style scoped>
.ai-train-data-container {
  padding: 20px;
}

.search-form {
  margin-bottom: 16px;
}

.pagination-container {
  margin-top: 20px;
  text-align: right;
}
</style>
