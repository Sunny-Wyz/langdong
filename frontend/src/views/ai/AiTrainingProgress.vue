<template>
  <div class="training-progress-container">
    <el-card shadow="hover" class="header-card">
      <div slot="header" class="card-header">
        <div>
          <i class="el-icon-loading" />
          <span class="title">AI训练进度</span>
        </div>
        <div class="header-actions">
          <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="fetchStatus">刷新</el-button>
          <el-button size="small" type="text" @click="$router.push('/ai/weekly-forecast')">返回周粒度预测</el-button>
        </div>
      </div>

      <el-alert
        title="本页仅展示周粒度预测训练进度；训练状态保存在 Python 服务内存中，服务重启后不会保留历史。"
        type="info"
        :closable="false"
        show-icon
        class="page-alert"
      />

      <el-row :gutter="16" class="summary-row">
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">训练状态</div>
            <el-tag :type="statusTagType(status.status)" size="medium">{{ statusText(status.status) }}</el-tag>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">模型可用</div>
            <el-tag :type="status.trained ? 'success' : 'info'" size="medium">{{ status.trained ? '已训练' : '未训练' }}</el-tag>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">模型版本</div>
            <div class="summary-value">{{ status.model_version || '-' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">耗时</div>
            <div class="summary-value">{{ formatElapsed(status.elapsed_seconds) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="progress-card">
      <div slot="header" class="card-header">
        <span>当前训练任务</span>
        <div class="header-actions">
          <el-button
            type="primary"
            size="small"
            icon="el-icon-cpu"
            :loading="submitting"
            :disabled="isRunning"
            @click="startTraining"
          >开始训练</el-button>
        </div>
      </div>

      <el-empty v-if="!status.task_id && !serviceError" description="暂无训练任务" />

      <div v-else>
        <el-alert
          v-if="serviceError"
          :title="serviceError"
          type="error"
          :closable="false"
          show-icon
          class="page-alert"
        />

        <div class="progress-main">
          <div class="progress-title">
            <span>{{ status.message || '暂无状态信息' }}</span>
            <span>{{ normalizedProgress }}%</span>
          </div>
          <el-progress
            :percentage="normalizedProgress"
            :status="progressStatus"
            :stroke-width="18"
          />
        </div>

        <el-steps :active="activeStep" finish-status="success" align-center class="stage-steps">
          <el-step title="提交任务" description="进入后台队列" />
          <el-step title="准备数据" description="加载训练样本" />
          <el-step title="特征处理" description="构造时间特征" />
          <el-step title="模型训练" description="训练 TFT/DeepAR" />
          <el-step title="保存模型" description="写入模型目录" />
          <el-step title="完成" description="可触发预测" />
        </el-steps>

        <el-descriptions :column="2" border class="detail-desc">
          <el-descriptions-item label="任务ID">{{ status.task_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段">{{ stageText(status.stage) }}</el-descriptions-item>
          <el-descriptions-item label="数据来源">{{ status.use_synthetic === false ? '真实业务数据' : '合成数据' }}</el-descriptions-item>
          <el-descriptions-item label="模型名称">{{ status.model_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(status.started_at) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(status.ended_at) }}</el-descriptions-item>
          <el-descriptions-item label="模型目录" :span="2">{{ status.model_dir || '-' }}</el-descriptions-item>
          <el-descriptions-item label="训练指标" :span="2">{{ metricsText }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="status.error"
          :title="status.error"
          type="error"
          show-icon
          class="page-alert"
        />

        <div class="footer-actions">
          <el-button type="success" icon="el-icon-s-promotion" :disabled="!status.trained || isRunning" @click="$router.push('/ai/weekly-forecast')">
            去触发预测
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'AiTrainingProgress',
  data() {
    return {
      status: {},
      loading: false,
      submitting: false,
      timer: null,
      serviceError: ''
    }
  },
  computed: {
    isRunning() {
      return this.status.status === 'RUNNING'
    },
    normalizedProgress() {
      const value = Number(this.status.progress || 0)
      if (Number.isNaN(value)) return 0
      return Math.max(0, Math.min(100, Math.round(value)))
    },
    activeStep() {
      const stage = this.status.stage
      const stageMap = {
        SUBMITTED: 1,
        LOAD_MODEL: 1,
        LOAD_DATA: 2,
        FEATURE_ENGINEERING: 3,
        TRAINING: 4,
        SAVE_MODEL: 5,
        SUCCESS: 6,
        FAILED: 6
      }
      return stageMap[stage] || 0
    },
    progressStatus() {
      if (this.status.status === 'SUCCESS') return 'success'
      if (this.status.status === 'FAILED') return 'exception'
      return undefined
    },
    metricsText() {
      const metrics = this.status.metrics || {}
      const entries = Object.keys(metrics).map(key => `${key}: ${metrics[key]}`)
      return entries.length > 0 ? entries.join('，') : '-'
    }
  },
  mounted() {
    this.fetchStatus()
    this.timer = setInterval(this.fetchStatus, 3000)
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    fetchStatus() {
      this.loading = true
      this.$http.get('/ai/weekly/train/status')
        .then(res => {
          this.status = res.data || {}
          this.serviceError = ''
        })
        .catch(err => {
          const message = err.response?.data?.message || 'AI服务未连接，请检查 Python 服务'
          this.serviceError = message
        })
        .finally(() => {
          this.loading = false
        })
    },
    startTraining() {
      this.$confirm('将使用合成数据训练 TFT/DeepAR 模型，是否继续？', '训练确认', {
        type: 'info'
      }).then(() => {
        this.submitting = true
        this.$http.post('/ai/weekly/train', { use_synthetic: true })
          .then(() => {
            this.$message.success('训练任务已提交后台')
            this.fetchStatus()
          })
          .catch(err => {
            this.$message.error(err.response?.data?.message || '训练请求失败')
            this.fetchStatus()
          })
          .finally(() => {
            this.submitting = false
          })
      }).catch(() => {})
    },
    statusText(status) {
      const map = {
        IDLE: '空闲',
        RUNNING: '训练中',
        SUCCESS: '已完成',
        FAILED: '失败'
      }
      return map[status] || '未知'
    },
    statusTagType(status) {
      const map = {
        IDLE: 'info',
        RUNNING: 'warning',
        SUCCESS: 'success',
        FAILED: 'danger'
      }
      return map[status] || 'info'
    },
    stageText(stage) {
      const map = {
        IDLE: '空闲',
        SUBMITTED: '任务已提交',
        LOAD_MODEL: '加载模型',
        LOAD_DATA: '准备训练数据',
        FEATURE_ENGINEERING: '特征处理',
        TRAINING: '模型训练中',
        SAVE_MODEL: '保存模型',
        SUCCESS: '训练完成',
        FAILED: '训练失败'
      }
      return map[stage] || '-'
    },
    formatElapsed(seconds) {
      if (seconds === null || seconds === undefined || seconds === '') return '-'
      const value = Number(seconds)
      if (Number.isNaN(value)) return '-'
      if (value < 60) return `${value.toFixed(1)} 秒`
      const minutes = Math.floor(value / 60)
      const rest = Math.round(value % 60)
      return `${minutes} 分 ${rest} 秒`
    },
    formatTime(value) {
      if (!value) return '-'
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return value
      const pad = n => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    }
  }
}
</script>

<style scoped>
.training-progress-container {
  padding: 20px;
}
.header-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  margin-left: 8px;
  font-weight: 600;
}
.header-actions .el-button {
  margin-left: 8px;
}
.page-alert {
  margin-bottom: 16px;
}
.summary-row {
  margin-top: 16px;
}
.summary-card {
  min-height: 80px;
  padding: 16px;
  border-radius: 8px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
}
.summary-label {
  margin-bottom: 10px;
  color: #909399;
  font-size: 13px;
}
.summary-value {
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}
.progress-card {
  margin-bottom: 20px;
}
.progress-main {
  margin-bottom: 28px;
}
.progress-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #303133;
  font-weight: 600;
}
.stage-steps {
  margin: 24px 0;
}
.detail-desc {
  margin-bottom: 16px;
}
.footer-actions {
  text-align: right;
}
</style>
