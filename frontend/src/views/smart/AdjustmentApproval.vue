<template>
  <div class="approval-container">
    <el-card>
      <div slot="header" class="clearfix">
        <span>分类调整审批记录</span>
        <el-radio-group v-model="filterStatus" style="float: right; margin-top: -5px;" size="small" @change="fetchRecords">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="PENDING">待审批</el-radio-button>
          <el-radio-button label="APPROVED">已通过</el-radio-button>
          <el-radio-button label="REJECTED">已驳回</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="records" style="width: 100%" border v-loading="loading">
        <el-table-column prop="id" label="审批单号" width="100" align="center"></el-table-column>
        <el-table-column prop="sparePartId" label="备件ID" width="100" align="center"></el-table-column>
        <el-table-column label="调整对比" align="center" width="180">
          <template slot-scope="scope">
            <span style="color: #909399">{{ scope.row.originalCombination }}</span>
            <i class="el-icon-right" style="margin: 0 10px; font-weight: bold;"></i>
            <span style="color: #409EFF; font-weight: bold">{{ scope.row.newCombination }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="200"></el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="160" align="center"></el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.status === 'PENDING'" type="warning">待审批</el-tag>
            <el-tag v-else-if="scope.row.status === 'APPROVED'" type="success">已通过</el-tag>
            <el-tag v-else type="danger">已驳回</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template slot-scope="scope">
            <div v-if="scope.row.status === 'PENDING'">
              <el-button size="mini" type="success" @click="handleApprove(scope.row, true)">同意</el-button>
              <el-button size="mini" type="danger" @click="handleApprove(scope.row, false)">驳回</el-button>
            </div>
            <span v-else style="color: #909399">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 审批意见对话框 -->
    <el-dialog :title="approveDialog.isApproved ? '同意申请' : '驳回申请'" :visible.sync="approveDialog.visible" width="500px">
      <el-form label-width="80px">
        <el-form-item label="审批意见">
          <el-input type="textarea" v-model="approveDialog.remark" rows="4" placeholder="（选填）请输入审批意见..."></el-input>
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="approveDialog.visible = false">取消</el-button>
        <el-button :type="approveDialog.isApproved ? 'success' : 'danger'" @click="submitApprove" :loading="submitting">确认</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import request from '@/utils/request'

export default {
  name: 'AdjustmentApproval',
  data() {
    return {
      records: [],
      loading: false,
      filterStatus: 'PENDING',
      submitting: false,
      approveDialog: {
        visible: false,
        rowId: null,
        isApproved: true,
        remark: ''
      }
    }
  },
  created() {
    this.fetchRecords()
  },
  methods: {
    fetchRecords() {
      this.loading = true
      request.get('/smart-classification/adjustments', { params: { status: this.filterStatus } })
        .then(res => {
          this.records = res.data
        })
        .catch(err => {
          this.$message.error('获取审批记录失败')
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleApprove(row, isApproved) {
      this.approveDialog.rowId = row.id
      this.approveDialog.isApproved = isApproved
      this.approveDialog.remark = ''
      this.approveDialog.visible = true
    },
    submitApprove() {
      this.submitting = true
      // 假设当前审核员ID是 2，实际从 token 中取
      const approverId = 2
      
      request.post(`/smart-classification/approve/${this.approveDialog.rowId}`, {
        isApproved: this.approveDialog.isApproved,
        remark: this.approveDialog.remark,
        approverId: approverId
      }).then(res => {
        this.$message.success('处理完成')
        this.approveDialog.visible = false
        this.fetchRecords()
      }).catch(err => {
        this.$message.error('处理失败')
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style scoped>
.approval-container {
  padding: 20px;
}
</style>
