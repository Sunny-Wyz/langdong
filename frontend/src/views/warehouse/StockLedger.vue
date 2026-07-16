<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📒</span>
          <div class="title">库存台账</div>
          <div class="head-btn-group">
            <el-button type="primary" size="small" @click="refreshData">🔄 刷新</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-click="handleTabClick">
        <el-tab-pane label="备件总览" name="summary">
          <el-table :data="summaryList" border stripe style="width: 100%" v-loading="summaryLoading">
            <el-table-column prop="sparePartCode" label="备件编码" width="140" sortable="custom" />
            <el-table-column prop="sparePartName" label="备件名称" />
            <el-table-column prop="quantity" label="总库存量（件）" width="150" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.quantity > 0 ? 'success' : 'danger'">
                  {{ scope.row.quantity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="最后入库更新时间" width="200">
              <template #default="scope">
                {{ formatTime(scope.row.updatedAt) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!summaryLoading && summaryList.length === 0" description="暂无备件总览数据" />
        </el-tab-pane>

        <el-tab-pane label="货位明细看板" name="location">
          <div style="margin-bottom: 15px; display: flex; gap: 15px;">
            <el-select v-model="filterZone" placeholder="筛选货位专区" clearable style="width: 180px;">
              <el-option v-for="i in 12" :key="i" :label="'专区' + i" :value="'专区' + i" />
            </el-select>
            <el-input
              v-model="filterKeyword"
              placeholder="搜索货位名称或备件名称"
              clearable
              style="width: 250px;"
            />
          </div>

          <el-table :data="filteredLocationList" border stripe style="width: 100%" v-loading="locationLoading">
            <el-table-column prop="locationZone" label="大区" width="120">
              <template #default="{ row }">
                <el-tag type="info">{{ row.locationZone || '未指派' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="locationName" label="货位名称 (编码)" width="200">
              <template #default="{ row }">
                {{ row.locationName }}
                <span style="color:#909399;font-size:12px;">({{ row.locationCode }})</span>
              </template>
            </el-table-column>
            <el-table-column prop="sparePartName" label="存储备件 (编码)">
              <template #default="{ row }">
                {{ row.sparePartName }}
                <span style="color:#909399;font-size:12px;">({{ row.sparePartCode }})</span>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="货位存放数" width="120" align="center">
              <template #default="{ row }">
                <span style="font-weight:bold; color:#409EFF">{{ row.quantity }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="最新上架时间" width="200">
              <template #default="{ row }">
                {{ formatTime(row.updatedAt) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!locationLoading && filteredLocationList.length === 0"
            description="尚无货位上架明细"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'

const activeTab = ref('summary')
const summaryList = ref<any[]>([])
const summaryLoading = ref(false)
const locationList = ref<any[]>([])
const locationLoading = ref(false)
const filterZone = ref('')
const filterKeyword = ref('')

const filteredLocationList = computed(() => {
  return locationList.value.filter(item => {
    let matchZone = true
    if (filterZone.value) {
      matchZone = item.locationZone === filterZone.value
    }
    let matchKy = true
    if (filterKeyword.value) {
      const kw = filterKeyword.value.toLowerCase()
      const ln = (item.locationName || '').toLowerCase()
      const sn = (item.sparePartName || '').toLowerCase()
      const sc = (item.sparePartCode || '').toLowerCase()
      matchKy = ln.includes(kw) || sn.includes(kw) || sc.includes(kw)
    }
    return matchZone && matchKy
  })
})

function formatTime(t: string) {
  return t ? t.replace('T', ' ').substring(0, 19) : '-'
}

function refreshData() {
  if (activeTab.value === 'summary') {
    loadSummaryData()
  } else {
    loadLocationData()
  }
}

function handleTabClick() {
  if (activeTab.value === 'summary' && summaryList.value.length === 0) {
    loadSummaryData()
  } else if (activeTab.value === 'location' && locationList.value.length === 0) {
    loadLocationData()
  }
}

async function loadSummaryData() {
  summaryLoading.value = true
  try {
    const res = await request.get('/stock-ledger')
    summaryList.value = res.data || []
  } catch (e) {
    ElMessage.error('获取库存汇总失败')
  } finally {
    summaryLoading.value = false
  }
}

async function loadLocationData() {
  locationLoading.value = true
  try {
    const res = await request.get('/stock-ledger/locations')
    locationList.value = res.data || []
  } catch (e) {
    ElMessage.error('获取货位明细失败')
  } finally {
    locationLoading.value = false
  }
}

onMounted(() => {
  loadSummaryData()
  loadLocationData()
})
</script>
