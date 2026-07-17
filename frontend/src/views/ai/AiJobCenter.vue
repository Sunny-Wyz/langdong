<template>
  <div class="page-container ai-job-center-container">
    <el-card shadow="hover">
      <template #header>
        <div class="phead header">
          <i class="el-icon-s-operation" />
          <div class="title">AI 任务中心</div>
          <div class="head-btn-group">
            <el-button size="small" @click="router.push('/ai/forecast-result')">返回预测结果</el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="补货任务 = 两阶段 Hurdle-Gamma（预测下个月）+ 安全库存 + 补货建议；状态会落库，服务重启后仍可按任务ID查询"
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
          <el-button :loading="submitting" :disabled="!hasTriggerPermission" @click="submitJob">提交补货任务</el-button>
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
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)" size="small">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultSummary" label="结果摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="error" label="错误信息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="queryTask(scope.row.taskId)">刷新</el-button>
            <el-button
              size="small"
              :disabled="!scope.row.payloadData"
              @click="openResultDialog(scope.row)"
            >查看结果</el-button>
            <el-button size="small" @click="copyTaskId(scope.row.taskId)">复制ID</el-button>
            <el-button
              v-if="isRunning(scope.row.status)"
              size="small"
              @click="stopPolling(scope.row.taskId)"
            >暂停轮询</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="resultDialogVisible" title="任务结果详情" width="70%">
        <div v-if="selectedTask" style="margin-bottom: 12px; font-size: 13px; color: #606266;">
          <div>任务ID：{{ selectedTask.taskId }}</div>
          <div>状态：{{ selectedTask.status }}</div>
          <div>更新时间：{{ selectedTask.updatedAt }}</div>
        </div>

        <el-table
          v-if="selectedResultItems.length > 0"
          :data="selectedResultItems"
          border
          style="width: 100%; margin-bottom: 12px"
        >
          <el-table-column prop="spare_part_id" label="备件ID" width="90" />
          <el-table-column prop="spare_part_code" label="备件编码" width="110" />
          <el-table-column prop="spare_part_name" label="备件名称" min-width="130" />
          <el-table-column prop="algo_name" label="算法" width="150" show-overflow-tooltip />
          <el-table-column label="预测需求" width="100">
            <template #default="scope">
              {{ readThreeMonthDemand(scope.row) }}
            </template>
          </el-table-column>
          <el-table-column label="ROP" width="80">
            <template #default="scope">
              {{ scope.row.reorder_point ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="SS" width="70">
            <template #default="scope">
              {{ scope.row.safety_stock ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="建议采购量" width="110">
            <template #default="scope">
              {{ scope.row.suggestion ? scope.row.suggestion.suggested_qty : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="90">
            <template #default="scope">
              <el-tag size="small" :type="priorityTagType(scope.row.priority)">{{ scope.row.priority || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="alert_message" label="提示信息" min-width="200" show-overflow-tooltip />
          <el-table-column label="错误" min-width="160" show-overflow-tooltip>
            <template #default="scope">
              {{ scope.row.error || '-' }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-else description="暂无可展示结果" :image-size="80" />

        <div v-if="selectedTask && selectedTask.payloadData">
          <div style="font-weight: 600; margin-bottom: 8px;">原始返回(JSON)</div>
          <el-input
            type="textarea"
            :rows="10"
            resize="none"
            :model-value="formatPayload(selectedTask.payloadData)"
            readonly
          />
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'ai_job_center_tasks_v1'

interface JobTask {
  taskId: string
  sparePartIdsText?: string
  status: string
  resultSummary: string
  payloadData: any
  error: string
  updatedAt: string
  submitAt?: number
}

const authStore = useAuthStore()
const router = useRouter()

const submitFormRef = ref<FormInstance>()
const submitForm = reactive({ rawIds: '' })
const submitRules: FormRules = {
  rawIds: [
    { required: true, message: '请输入至少一个备件ID', trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: (err?: Error) => void) => {
        validateIds(value, callback)
      },
      trigger: 'blur'
    }
  ]
}

const submitting = ref(false)
const refreshing = ref(false)
const filters = reactive({
  taskId: '',
  status: ''
})
const tasks = ref<JobTask[]>([])
const resultDialogVisible = ref(false)
const selectedTask = ref<JobTask | null>(null)
const pollers = ref<Record<string, ReturnType<typeof setInterval>>>({})
const pollFailureCount = ref<Record<string, number>>({})
const pollInFlight = ref<Record<string, boolean>>({})

const isAdmin = computed(() => authStore.username === 'admin')
const permissions = computed(() => authStore.permissions || [])
const hasListPermission = computed(() => isAdmin.value || permissions.value.includes('ai:forecast:list'))
const hasTriggerPermission = computed(() => isAdmin.value || permissions.value.includes('ai:forecast:trigger'))

const filteredTasks = computed(() => {
  return tasks.value.filter(task => {
    const taskIdOk = !filters.taskId || task.taskId === filters.taskId
    const statusOk = !filters.status || task.status === filters.status
    return taskIdOk && statusOk
  })
})

const selectedResultItems = computed(() => {
  if (!selectedTask.value || !selectedTask.value.payloadData) {
    return []
  }
  const result = selectedTask.value.payloadData.result
  return Array.isArray(result) ? result : []
})

function validateIds(value: string, callback: (err?: Error) => void) {
  const tokens = parseTokens(value)
  if (tokens.length === 0) {
    callback(new Error('请输入备件ID或编码，多个用英文逗号分隔'))
    return
  }
  callback()
}

function parseTokens(raw: string) {
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
}

async function submitJob() {
  if (!hasTriggerPermission.value) {
    ElMessage.warning('当前账号缺少任务提交权限（ai:forecast:trigger）')
    return
  }
  if (submitting.value) {
    return
  }
  const valid = await new Promise<boolean>(resolve => {
    if (!submitFormRef.value) {
      resolve(false)
      return
    }
    submitFormRef.value.validate(ok => resolve(!!ok))
  })
  if (!valid) {
    return
  }

  submitting.value = true
  const sparePartTokens = parseTokens(submitForm.rawIds)

  try {
    const res = await request.post('/ai/forecast/jobs/replenishment', {
      spare_part_ids: sparePartTokens
    })
    const payload = res.data || {}
    const taskId = payload.task_id || payload.taskId
    if (!taskId) {
      throw new Error('任务提交成功但未返回 task_id')
    }

    const nextTask: JobTask = {
      taskId,
      sparePartIdsText: sparePartTokens.join(','),
      status: (payload.status || 'PENDING').toUpperCase(),
      resultSummary: '-',
      payloadData: null,
      error: '-',
      updatedAt: formatNow(),
      submitAt: Date.now()
    }
    tasks.value = [nextTask, ...tasks.value.filter(task => task.taskId !== taskId)]
    persistTasks()
    startPolling(taskId)
    ElMessage.success('任务已提交，已开始自动轮询')
  } catch (error: any) {
    ElMessage.error(extractError(error, '任务提交失败'))
  } finally {
    submitting.value = false
  }
}

async function queryTask(taskId: string) {
  if (pollInFlight.value[taskId]) {
    return
  }
  if (!hasListPermission.value) {
    stopPolling(taskId)
    return
  }
  pollInFlight.value = {
    ...pollInFlight.value,
    [taskId]: true
  }
  try {
    const res = await request.get(`/ai/forecast/jobs/${taskId}`)
    const payload = res.data || {}
    const status = (payload.status || 'UNKNOWN').toUpperCase()
    const resultSummary = buildResultSummary(payload.payload)
    const nextTask = {
      taskId,
      status,
      resultSummary,
      payloadData: payload.payload || null,
      error: payload.error || '-',
      updatedAt: formatNow()
    }
    mergeTask(nextTask)
    pollFailureCount.value = {
      ...pollFailureCount.value,
      [taskId]: 0
    }
    if (!isRunning(status)) {
      stopPolling(taskId)
    }
  } catch (error: any) {
    const statusCode = error?.response?.status
    if (statusCode === 404) {
      mergeTask({
        taskId,
        status: 'NOT_FOUND',
        error: '任务不存在或已过期',
        resultSummary: '-',
        payloadData: null,
        updatedAt: formatNow()
      })
      stopPolling(taskId)
      return
    }
    const nextFailCount = (pollFailureCount.value[taskId] || 0) + 1
    pollFailureCount.value = {
      ...pollFailureCount.value,
      [taskId]: nextFailCount
    }
    if (statusCode === 401 || statusCode === 403 || nextFailCount >= 3) {
      stopPolling(taskId)
      mergeTask({
        taskId,
        status: 'FAILURE',
        error: statusCode === 403 ? '权限不足，已停止自动轮询' : '连续失败，已停止自动轮询',
        payloadData: null,
        updatedAt: formatNow()
      })
    }
    ElMessage.error(extractError(error, `任务 ${taskId} 状态刷新失败`))
  } finally {
    const nextInFlight = { ...pollInFlight.value }
    delete nextInFlight[taskId]
    pollInFlight.value = nextInFlight
  }
}

async function refreshAllRunning() {
  const runningTasks = tasks.value.filter(task => isRunning(task.status))
  if (runningTasks.length === 0) {
    ElMessage.info('当前没有进行中的任务')
    return
  }
  refreshing.value = true
  try {
    await Promise.all(runningTasks.map(task => queryTask(task.taskId)))
  } finally {
    refreshing.value = false
  }
}

function startPolling(taskId: string) {
  if (pollers.value[taskId]) {
    return
  }
  pollers.value = {
    ...pollers.value,
    [taskId]: setInterval(() => {
      queryTask(taskId)
    }, 3000)
  }
}

function stopPolling(taskId: string) {
  const timer = pollers.value[taskId]
  if (timer) {
    clearInterval(timer)
    const nextPollers = { ...pollers.value }
    delete nextPollers[taskId]
    pollers.value = nextPollers
  }
  if (pollFailureCount.value[taskId] !== undefined) {
    const nextFailCount = { ...pollFailureCount.value }
    delete nextFailCount[taskId]
    pollFailureCount.value = nextFailCount
  }
  if (pollInFlight.value[taskId] !== undefined) {
    const nextInFlight = { ...pollInFlight.value }
    delete nextInFlight[taskId]
    pollInFlight.value = nextInFlight
  }
}

function isRunning(status: string) {
  return status === 'PENDING' || status === 'STARTED' || status === 'RETRY' || status === 'RUNNING'
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILURE' || status === 'NOT_FOUND') return 'danger'
  if (isRunning(status)) return 'warning'
  return 'info'
}

function mergeTask(nextTask: Partial<JobTask> & { taskId: string }) {
  tasks.value = tasks.value.map(task => {
    if (task.taskId !== nextTask.taskId) {
      return task
    }
    return {
      ...task,
      ...nextTask
    }
  })
  persistTasks()
}

function buildResultSummary(payload: any) {
  if (!payload) {
    return '-'
  }
  const algo = payload.algo_name || payload.algo || '两阶段 Hurdle-Gamma'
  const items = payload.result
  if (Array.isArray(items)) {
    const needBuy = items.filter((item: any) => item?.suggestion?.suggested_qty > 0).length
    return `${algo}：${items.length} 条结果，其中 ${needBuy} 条需补货`
  }
  return `${algo} 任务完成`
}

function clearFinished() {
  const running = tasks.value.filter(task => isRunning(task.status))
  tasks.value
    .filter(task => !isRunning(task.status))
    .forEach(task => stopPolling(task.taskId))
  tasks.value = running
  if (selectedTask.value && !running.find(task => task.taskId === selectedTask.value!.taskId)) {
    resultDialogVisible.value = false
    selectedTask.value = null
  }
  persistTasks()
}

function openResultDialog(task: JobTask) {
  selectedTask.value = task
  resultDialogVisible.value = true
}

function priorityTagType(priority: string) {
  if (priority === 'HIGH') return 'danger'
  if (priority === 'MEDIUM') return 'warning'
  if (priority === 'LOW') return 'success'
  return 'info'
}

function formatPayload(payload: any) {
  try {
    return JSON.stringify(payload, null, 2)
  } catch (e) {
    return String(payload)
  }
}

function readThreeMonthDemand(row: any) {
  const demand = row && row.predicted_demand ? row.predicted_demand.total : null
  return demand === null || demand === undefined ? '-' : demand
}

function copyTaskId(taskId: string) {
  const el = document.createElement('textarea')
  el.value = taskId
  document.body.appendChild(el)
  el.select()
  document.execCommand('copy')
  document.body.removeChild(el)
  ElMessage.success('任务ID已复制')
}

function persistTasks() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks.value.slice(0, 100)))
}

function restoreTasks() {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return
  }
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      tasks.value = parsed
    }
  } catch (e) {
    localStorage.removeItem(STORAGE_KEY)
  }
}

function extractError(error: any, fallback: string) {
  const message =
    error?.response?.data?.message ||
    error?.response?.data?.detail ||
    error?.message
  return message || fallback
}

function formatNow() {
  const now = new Date()
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

onMounted(() => {
  if (!hasListPermission.value) {
    return
  }
  restoreTasks()
  tasks.value
    .filter(task => isRunning(task.status))
    .forEach(task => startPolling(task.taskId))
})

onBeforeUnmount(() => {
  Object.keys(pollers.value).forEach(taskId => stopPolling(taskId))
})
</script>

<style scoped>
.ai-job-center-container {
  padding: 20px;
}

.submit-form,
.search-form {
  margin-bottom: 14px;
}
/* button style unified globally */
</style>
