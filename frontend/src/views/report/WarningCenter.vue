<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">预警任务中心</div>
          <div class="head-btn-group">
            <el-badge :value="totalCount" :max="99" type="danger" style="margin-left:12px" />
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-click="onTabClick">
        <el-tab-pane name="lowStock">
          <template #label>
            低库存预警
            <el-badge v-if="warnings.lowStock.length" :value="warnings.lowStock.length" type="danger" style="margin-left:4px" />
          </template>
          <el-table :data="warnings.lowStock" border size="small" v-loading="loading">
            <el-table-column prop="severity" label="紧急程度" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.severity === '紧急' ? 'danger' : 'warning'" size="small">
                  {{ scope.row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="预警标题" />
            <el-table-column prop="detail" label="详情" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button size="small" type="primary" @click="navigate(scope.row)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="overdueWO">
          <template #label>
            逾期工单
            <el-badge v-if="warnings.overdueWO.length" :value="warnings.overdueWO.length" type="warning" style="margin-left:4px" />
          </template>
          <el-table :data="warnings.overdueWO" border size="small" v-loading="loading">
            <el-table-column prop="priority" label="紧急程度" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.priority === '紧急' ? 'danger' : 'warning'" size="small">
                  {{ scope.row.priority }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="预警标题" />
            <el-table-column prop="detail" label="详情" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button size="small" type="primary" @click="navigate(scope.row)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="overduePO">
          <template #label>
            逾期采购
            <el-badge v-if="warnings.overduePO.length" :value="warnings.overduePO.length" type="info" style="margin-left:4px" />
          </template>
          <el-table :data="warnings.overduePO" border size="small" v-loading="loading">
            <el-table-column prop="priority" label="紧急程度" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.priority === '紧急' ? 'danger' : 'warning'" size="small">
                  {{ scope.row.priority }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="预警标题" />
            <el-table-column prop="detail" label="详情" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button size="small" type="primary" @click="navigate(scope.row)">去处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()

const activeTab = ref('lowStock')
const loading = ref(false)
const totalCount = ref(0)
const warnings = reactive<{ lowStock: any[]; overdueWO: any[]; overduePO: any[] }>({
  lowStock: [],
  overdueWO: [],
  overduePO: []
})

async function load() {
  loading.value = true
  try {
    const res = await request.get('/warnings')
    const d = res.data || {}
    warnings.lowStock = d.lowStock || []
    warnings.overdueWO = d.overdueWO || []
    warnings.overduePO = d.overduePO || []
    totalCount.value = d.totalCount || 0
  } catch (e) {
    ElMessage.error('加载预警数据失败')
  } finally {
    loading.value = false
  }
}

function navigate(row: any) {
  if (row.targetPath) router.push(row.targetPath)
}

function onTabClick() {
  /* no-op */
}

onMounted(() => {
  load()
})
</script>
