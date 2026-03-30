<template>
  <div class="page-container ai-job-center-container">
    <el-card shadow="hover">
      <div slot="header" class="phead header">
        <i class="el-icon-s-operation" />
        <div class="title">AI 任务中心</div>
        <div class="head-btn-group">
          <el-button type="text" @click="$router.push('/ai/forecast-result')">返回预测结果</el-button>
        </div>
      </div>

      <el-alert
        title="适合批量任务：提交后后台执行，可在本页持续查看状态"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-alert
        v-if="!hasListPermission"
        title="当前账号缺少任务查询权限（ai:forecast:list），请联系管理员授权"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" inline size="small" class="submit-form">
        <el-form-item label="备件ID/编码" prop="rawIds">
          <el-input
            v-model.trim="submitForm.rawIds"
            style="width: 420px"
            placeholder="例如: 1001,C0100002,C0020001"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" :disabled="!hasTriggerPermission" @click="submitJob">提交补货任务</el-button>
          <el-button @click="refreshAllRunning" :loading="refreshing">刷新进行中任务</el-button>
          <el-button @click="clearFinished">清理已完成</el-button>
        </el-form-item>
      </el-form>

      <el-form :inline="true" :model="filters" size="small" class="search-form">
        <el-form-item label="任务ID">
          <el-input v-model.trim="filters.taskId" placeholder="精确筛选" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 160px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="STARTED" value="STARTED" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILURE" value="FAILURE" />
            <el-option label="NOT_FOUND" value="NOT_FOUND" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="filteredTasks" border style="width: 100%">
        <el-table-column prop="taskId" label="任务ID" min-width="260" show-overflow-tooltip />
        <el-table-column prop="sparePartIdsText" label="提交参数" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template slot-scope="scope">
            <el-tag :type="statusTagType(scope.row.status)" size="small">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultSummary" label="结果摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="error" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="small" @click="queryTask(scope.row.taskId)">刷新</el-button>
            <el-button type="text" size="small" @click="copyTaskId(scope.row.taskId)">复制ID</el-button>
            <el-button
              v-if="isRunning(scope.row.status)"
              type="text"
              size="small"
              @click="stopPolling(scope.row.taskId)"
            >暂停轮询</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import request from '@/utils/request'

const STORAGE_KEY = 'ai_job_center_tasks_v1'

export default {
  name: 'AiJobCenter',
  data() {
    return {
      submitForm: {
        rawIds: ''
      },
      submitRules: {
        rawIds: [
          { required: true, message: '请输入至少一个备件ID', trigger: 'blur' },
          { validator: (_, value, callback) => this.validateIds(value, callback), trigger: 'blur' }
        ]
      },
      submitting: false,
      refreshing: false,
      filters: {
        taskId: '',
        status: ''
      },
      tasks: [],
      pollers: {},
      pollFailureCount: {},
      pollInFlight: {}
    }
  },
  computed: {
    isAdmin() {
      return this.$store.state.username === 'admin'
    },
    permissions() {
      return this.$store.state.permissions || []
    },
    hasListPermission() {
      return this.isAdmin || this.permissions.includes('ai:forecast:list')
    },
    hasTriggerPermission() {
      return this.isAdmin || this.permissions.includes('ai:forecast:trigger')
    },
    filteredTasks() {
      return this.tasks.filter(task => {
        const taskIdOk = !this.filters.taskId || task.taskId === this.filters.taskId
        const statusOk = !this.filters.status || task.status === this.filters.status
        return taskIdOk && statusOk
      })
    }
  },
  created() {
    if (!this.hasListPermission) {
      return
    }
    this.restoreTasks()
    this.tasks
      .filter(task => this.isRunning(task.status))
      .forEach(task => this.startPolling(task.taskId))
  },
  beforeDestroy() {
    Object.keys(this.pollers).forEach(taskId => this.stopPolling(taskId))
  },
  methods: {
    validateIds(value, callback) {
      const tokens = this.parseTokens(value)
      if (tokens.length === 0) {
        callback(new Error('请输入备件ID或编码，多个用英文逗号分隔'))
        return
      }
      callback()
    },
    parseTokens(raw) {
      if (!raw) {
        return []
      }
      const tokenSet = new Set(
        raw
          .split(',')
          .map(item => item.trim())
          .filter(item => item.length > 0)
      )
      return Array.from(tokenSet)
    },
    async submitJob() {
      if (!this.hasTriggerPermission) {
        this.$message.warning('当前账号缺少任务提交权限（ai:forecast:trigger）')
        return
      }
      if (this.submitting) {
        return
      }
      const valid = await new Promise(resolve => {
        this.$refs.submitFormRef.validate(ok => resolve(ok))
      })
      if (!valid) {
        return
      }

      this.submitting = true
      const sparePartTokens = this.parseTokens(this.submitForm.rawIds)

      try {
        const res = await request.post('/ai/forecast/jobs/replenishment', {
          spare_part_ids: sparePartTokens
        })
        const payload = res.data || {}
        const taskId = payload.task_id || payload.taskId
        if (!taskId) {
          throw new Error('任务提交成功但未返回 task_id')
        }

        const nextTask = {
          taskId,
          sparePartIdsText: sparePartTokens.join(','),
          status: (payload.status || 'PENDING').toUpperCase(),
          resultSummary: '-',
          error: '-',
          updatedAt: this.formatNow(),
          submitAt: Date.now()
        }
        this.tasks = [nextTask, ...this.tasks.filter(task => task.taskId !== taskId)]
        this.persistTasks()
        this.startPolling(taskId)
        this.$message.success('任务已提交，已开始自动轮询')
      } catch (error) {
        this.$message.error(this.extractError(error, '任务提交失败'))
      } finally {
        this.submitting = false
      }
    },
    async queryTask(taskId) {
      if (this.pollInFlight[taskId]) {
        return
      }
      if (!this.hasListPermission) {
        this.stopPolling(taskId)
        return
      }
      this.pollInFlight = {
        ...this.pollInFlight,
        [taskId]: true
      }
      try {
        const res = await request.get(`/ai/forecast/jobs/${taskId}`)
        const payload = res.data || {}
        const status = (payload.status || 'UNKNOWN').toUpperCase()
        const resultSummary = this.buildResultSummary(payload.payload)
        const nextTask = {
          taskId,
          status,
          resultSummary,
          error: payload.error || '-',
          updatedAt: this.formatNow()
        }
        this.mergeTask(nextTask)
        this.pollFailureCount = {
          ...this.pollFailureCount,
          [taskId]: 0
        }
        if (!this.isRunning(status)) {
          this.stopPolling(taskId)
        }
      } catch (error) {
        const statusCode = error?.response?.status
        if (statusCode === 404) {
          this.mergeTask({
            taskId,
            status: 'NOT_FOUND',
            error: '任务不存在或已过期',
            resultSummary: '-',
            updatedAt: this.formatNow()
          })
          this.stopPolling(taskId)
          return
        }
        const nextFailCount = (this.pollFailureCount[taskId] || 0) + 1
        this.pollFailureCount = {
          ...this.pollFailureCount,
          [taskId]: nextFailCount
        }
        if (statusCode === 401 || statusCode === 403 || nextFailCount >= 3) {
          this.stopPolling(taskId)
          this.mergeTask({
            taskId,
            status: 'FAILURE',
            error: statusCode === 403 ? '权限不足，已停止自动轮询' : '连续失败，已停止自动轮询',
            updatedAt: this.formatNow()
          })
        }
        this.$message.error(this.extractError(error, `任务 ${taskId} 状态刷新失败`))
      } finally {
        const nextInFlight = { ...this.pollInFlight }
        delete nextInFlight[taskId]
        this.pollInFlight = nextInFlight
      }
    },
    async refreshAllRunning() {
      const runningTasks = this.tasks.filter(task => this.isRunning(task.status))
      if (runningTasks.length === 0) {
        this.$message.info('当前没有进行中的任务')
        return
      }
      this.refreshing = true
      try {
        await Promise.all(runningTasks.map(task => this.queryTask(task.taskId)))
      } finally {
        this.refreshing = false
      }
    },
    startPolling(taskId) {
      if (this.pollers[taskId]) {
        return
      }
      this.pollers = {
        ...this.pollers,
        [taskId]: setInterval(() => {
          this.queryTask(taskId)
        }, 3000)
      }
    },
    stopPolling(taskId) {
      const timer = this.pollers[taskId]
      if (timer) {
        clearInterval(timer)
        const nextPollers = { ...this.pollers }
        delete nextPollers[taskId]
        this.pollers = nextPollers
      }
      if (this.pollFailureCount[taskId] !== undefined) {
        const nextFailCount = { ...this.pollFailureCount }
        delete nextFailCount[taskId]
        this.pollFailureCount = nextFailCount
      }
      if (this.pollInFlight[taskId] !== undefined) {
        const nextInFlight = { ...this.pollInFlight }
        delete nextInFlight[taskId]
        this.pollInFlight = nextInFlight
      }
    },
    isRunning(status) {
      return status === 'PENDING' || status === 'STARTED' || status === 'RETRY'
    },
    statusTagType(status) {
      if (status === 'SUCCESS') return 'success'
      if (status === 'FAILURE' || status === 'NOT_FOUND') return 'danger'
      if (this.isRunning(status)) return 'warning'
      return 'info'
    },
    mergeTask(nextTask) {
      this.tasks = this.tasks.map(task => {
        if (task.taskId !== nextTask.taskId) {
          return task
        }
        return {
          ...task,
          ...nextTask
        }
      })
      this.persistTasks()
    },
    buildResultSummary(payload) {
      if (!payload) {
        return '-'
      }
      const items = payload.result
      if (Array.isArray(items)) {
        return `共 ${items.length} 条建议`
      }
      return '任务完成'
    },
    clearFinished() {
      const running = this.tasks.filter(task => this.isRunning(task.status))
      this.tasks
        .filter(task => !this.isRunning(task.status))
        .forEach(task => this.stopPolling(task.taskId))
      this.tasks = running
      this.persistTasks()
    },
    copyTaskId(taskId) {
      const el = document.createElement('textarea')
      el.value = taskId
      document.body.appendChild(el)
      el.select()
      document.execCommand('copy')
      document.body.removeChild(el)
      this.$message.success('任务ID已复制')
    },
    persistTasks() {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.tasks.slice(0, 100)))
    },
    restoreTasks() {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) {
        return
      }
      try {
        const parsed = JSON.parse(raw)
        if (Array.isArray(parsed)) {
          this.tasks = parsed
        }
      } catch (e) {
        localStorage.removeItem(STORAGE_KEY)
      }
    },
    extractError(error, fallback) {
      const message =
        error?.response?.data?.message ||
        error?.response?.data?.detail ||
        error?.message
      return message || fallback
    },
    formatNow() {
      const now = new Date()
      const pad = n => (n < 10 ? `0${n}` : `${n}`)
      return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
    }
  }
}
</script>

<style scoped>
.ai-job-center-container {
  padding: 20px;
}

.submit-form,
.search-form {
  margin-bottom: 14px;
}
</style>
