<template>
  <div class="strategy-config-container">
    <el-card>
      <div slot="header" class="clearfix">
        <span>差异化策略配置 (AX-CZ矩阵)</span>
        <el-button style="float: right; padding: 3px 0" type="primary" size="small" @click="fetchStrategies" :loading="loading">
          刷新数据
        </el-button>
      </div>

      <el-table
        :data="strategies"
        style="width: 100%"
        border
        v-loading="loading"
        :row-class-name="tableRowClassName">
        
        <el-table-column prop="combinationCode" label="组合策略" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="getTagType(scope.row.combinationCode)" effect="dark">
              {{ scope.row.combinationCode }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="abcCategory" label="ABC分类" align="center" width="100"></el-table-column>
        <el-table-column prop="xyzCategory" label="XYZ分类" align="center" width="100"></el-table-column>
        
        <el-table-column prop="safetyStockMultiplier" label="安全库存系数" align="center">
          <template slot-scope="scope">
            <span v-if="!scope.row.editing">{{ scope.row.safetyStockMultiplier }}</span>
            <el-input-number v-else v-model="scope.row.safetyStockMultiplier" :precision="2" :step="0.1" :min="0" size="small"></el-input-number>
          </template>
        </el-table-column>
        
        <el-table-column prop="replenishmentCycle" label="补货周期(天)" align="center">
          <template slot-scope="scope">
            <span v-if="!scope.row.editing">{{ scope.row.replenishmentCycle }}</span>
            <el-input-number v-else v-model="scope.row.replenishmentCycle" :min="1" :step="1" size="small"></el-input-number>
          </template>
        </el-table-column>
        
        <el-table-column prop="approvalLevel" label="审批等级" align="center">
          <template slot-scope="scope">
            <span v-if="!scope.row.editing">{{ scope.row.approvalLevel }}</span>
            <el-select v-else v-model="scope.row.approvalLevel" size="small">
              <el-option label="系统自动" value="系统自动"></el-option>
              <el-option label="主管审批" value="主管审批"></el-option>
              <el-option label="经理审批" value="经理审批"></el-option>
              <el-option label="总监审批" value="总监审批"></el-option>
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button v-if="!scope.row.editing" size="mini" type="primary" icon="el-icon-edit" @click="handleEdit(scope.$index, scope.row)">配置</el-button>
            <div v-else>
              <el-button size="mini" type="success" icon="el-icon-check" @click="handleSave(scope.$index, scope.row)"></el-button>
              <el-button size="mini" type="warning" icon="el-icon-close" @click="handleCancel(scope.$index, scope.row)"></el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'StrategyConfig',
  data() {
    return {
      strategies: [],
      loading: false,
      originalRow: null
    }
  },
  created() {
    this.fetchStrategies()
  },
  methods: {
    fetchStrategies() {
      this.loading = true
      request.get('/smart-classification/strategies')
        .then(res => {
          this.strategies = res.data.map(item => {
            return {
              ...item,
              editing: false
            }
          })
        })
        .catch(err => {
          this.$message.error('获取策略配置失败')
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleEdit(index, row) {
      this.originalRow = { ...row }
      row.editing = true
    },
    handleCancel(index, row) {
      Object.assign(row, this.originalRow)
      row.editing = false
      this.originalRow = null
    },
    handleSave(index, row) {
      this.loading = true
      request.put(`/smart-classification/strategies/${row.id}`, {
        safetyStockMultiplier: row.safetyStockMultiplier,
        replenishmentCycle: row.replenishmentCycle,
        approvalLevel: row.approvalLevel
      })
      .then(res => {
        this.$message.success('配置保存成功')
        row.editing = false
        this.originalRow = null
      })
      .catch(err => {
        this.$message.error('配置保存失败')
      })
      .finally(() => {
        this.loading = false
      })
    },
    getTagType(code) {
      if (code.startsWith('A')) return 'danger'
      if (code.startsWith('B')) return 'warning'
      return 'info'
    },
    tableRowClassName({row, rowIndex}) {
      if (row.abcCategory === 'A') return 'row-level-a'
      if (row.abcCategory === 'C') return 'row-level-c'
      return ''
    }
  }
}
</script>

<style scoped>
.strategy-config-container {
  padding: 20px;
}
.el-table .row-level-a {
  background: #fffcfc;
}
.el-table .row-level-c {
  background: #fdfdfd;
}
</style>
