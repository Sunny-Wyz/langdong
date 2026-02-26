<template>
  <div class="dashboard-container">
    <el-card>
      <div slot="header" class="clearfix">
        <span>备件智能分类看板 (ABC-XYZ)</span>
        <el-button style="float: right; padding: 3px 0" type="primary" @click="triggerCalculation" :loading="triggering">
          手动触发当月分类重算
        </el-button>
        <el-button style="float: right; padding: 3px 20px" @click="fetchResults" :loading="loading">
          刷新数据
        </el-button>
      </div>

      <el-row :gutter="20">
        <el-col :span="24">
          <el-table :data="results" style="width: 100%" border v-loading="loading">
            <el-table-column prop="sparePartId" label="备件ID" width="80" align="center"></el-table-column>
            
            <el-table-column label="智能分类" align="center" width="120">
              <template slot-scope="scope">
                <el-tag :type="scope.row.abcCategory === 'A' ? 'danger' : (scope.row.abcCategory === 'B' ? 'warning' : 'info')" effect="dark">
                  {{ scope.row.combinationCode }}
                </el-tag>
                <el-tooltip v-if="scope.row.isManualAdjusted === 1" content="该分类由人工修改" placement="top">
                  <i class="el-icon-info" style="color: #E6A23C; margin-left: 5px;"></i>
                </el-tooltip>
              </template>
            </el-table-column>
            
            <el-table-column prop="predictedDemand" label="下月预测需求" width="120" align="center"></el-table-column>
            <el-table-column prop="abcScore" label="ABC综合得分" width="120" align="center"></el-table-column>
            <el-table-column prop="xyzCv" label="需求变异系数(CV)" width="150" align="center"></el-table-column>
            
            <el-table-column label="操作" align="center" fixed="right" min-width="120">
              <template slot-scope="scope">
                <el-button size="mini" type="warning" @click="openAdjustDialog(scope.row)">人工调整申请</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <!-- 调整申请对话框 -->
    <el-dialog title="备件分类人工调整申请" :visible.sync="adjustDialogVisible" width="500px">
      <el-form :model="adjustForm" :rules="adjustRules" ref="adjustForm" label-width="100px">
        <el-form-item label="当前分类">
          <el-tag type="info">{{ currentRow ? currentRow.combinationCode : '' }}</el-tag>
        </el-form-item>
        <el-form-item label="目标分类" prop="newCombination">
          <el-select v-model="adjustForm.newCombination" placeholder="请选择调整后的分类(AX-CZ)">
            <el-option v-for="c in ['AX', 'AY', 'AZ', 'BX', 'BY', 'BZ', 'CX', 'CY', 'CZ']" :key="c" :label="c" :value="c"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input type="textarea" v-model="adjustForm.reason" rows="4" placeholder="请详细说明调整该备件分类的业务原因..."></el-input>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust" :loading="submitting">提交审批</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'ClassificationDashboard',
  data() {
    return {
      results: [],
      loading: false,
      triggering: false,
      adjustDialogVisible: false,
      submitting: false,
      currentRow: null,
      adjustForm: {
        newCombination: '',
        reason: ''
      },
      adjustRules: {
        newCombination: [{ required: true, message: '请选择目标分类', trigger: 'change' }],
        reason: [{ required: true, message: '请填写调整原因', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.fetchResults()
  },
  methods: {
    fetchResults() {
      this.loading = true
      request.get('/smart-classification/results')
        .then(res => {
          this.results = res.data
        })
        .catch(err => {
          this.$message.error('获取分类结果失败')
        })
        .finally(() => {
          this.loading = false
        })
    },
    triggerCalculation() {
      this.$confirm('确定要手动触发一次全量分类重算吗？这可能会覆盖未被人工锁定的数据。', '提示', {
        confirmButtonText: '确定触发',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.triggering = true
        request.post('/smart-classification/trigger')
          .then(res => {
            this.$message.success('分类重算任务已成功执行')
            this.fetchResults()
          })
          .catch(err => {
            this.$message.error('触发计算失败')
          })
          .finally(() => {
            this.triggering = false
          })
      }).catch(() => {})
    },
    openAdjustDialog(row) {
      this.currentRow = row
      this.adjustForm.newCombination = row.combinationCode
      this.adjustForm.reason = ''
      this.adjustDialogVisible = true
      this.$nextTick(() => {
        this.$refs.adjustForm.clearValidate()
      })
    },
    submitAdjust() {
      this.$refs.adjustForm.validate(valid => {
        if (valid) {
          if (this.adjustForm.newCombination === this.currentRow.combinationCode) {
            this.$message.warning('目标分类与当前分类相同，无需调整');
            return;
          }
          this.submitting = true
          // 假设当前操作员ID是 1，实际应该从 Vuex 或 localStorage token 中解析
          const applicantId = 1 
          
          request.post('/smart-classification/adjust', {
            sparePartId: this.currentRow.sparePartId,
            newCombination: this.adjustForm.newCombination,
            reason: this.adjustForm.reason,
            applicantId: applicantId
          }).then(res => {
            this.$message.success('调整申请已提交，等待管理层审批')
            this.adjustDialogVisible = false
          }).catch(err => {
            this.$message.error('提交申请失败')
          }).finally(() => {
            this.submitting = false
          })
        }
      })
    }
  }
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
}
</style>
