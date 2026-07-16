<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">备件消耗趋势分析</div>
          <div class="head-btn-group">
            <div>
              <el-select v-model="months" size="small" style="width:120px;margin-right:8px" @change="load">
                <el-option :value="3" label="近3个月" />
                <el-option :value="6" label="近6个月" />
                <el-option :value="12" label="近12个月" />
              </el-select>
              <el-button type="success" size="small" @click="exportCsv">📥 导出 CSV</el-button>
            </div>
          </div>
        </div>
      </template>
      <div ref="trendChart" style="height:300px"></div>
    </el-card>

    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">Top 10 高消耗备件</div>
        </div>
      </template>
      <div ref="top10Chart" style="height:300px"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const months = ref(6)
const trendData = ref<any[]>([])
const top10Data = ref<any[]>([])
const trendChart = ref<HTMLElement | null>(null)
const top10Chart = ref<HTMLElement | null>(null)

async function load() {
  try {
    const [tRes, top10Res] = await Promise.all([
      request.get(`/report/consumption/trend?months=${months.value}`),
      request.get('/report/consumption/top10')
    ])
    trendData.value = tRes.data || []
    top10Data.value = top10Res.data || []
    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error('加载消耗数据失败')
  }
}

function renderCharts() {
  if (trendChart.value) {
    const tc = echarts.init(trendChart.value)
    tc.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      xAxis: { type: 'category', data: trendData.value.map((r: any) => r.month) },
      yAxis: { type: 'value' },
      series: [
        { name: '消耗数量', type: 'line', smooth: true, data: trendData.value.map((r: any) => r.totalQty), areaStyle: {} },
        { name: '领用单数', type: 'bar', data: trendData.value.map((r: any) => r.orderCount), yAxisIndex: 0 }
      ]
    })
  }
  if (top10Chart.value) {
    const pc = echarts.init(top10Chart.value)
    pc.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '20%' },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: top10Data.value.map((r: any) => r.partName).reverse() },
      series: [{
        name: '消耗数量',
        type: 'bar',
        data: top10Data.value.map((r: any) => r.totalQty).reverse(),
        itemStyle: { color: '#409EFF' }
      }]
    })
  }
}

function exportCsv() {
  const rows = [
    ['备件编码', '备件名称', '消耗数量'],
    ...top10Data.value.map((r: any) => [r.partCode, r.partName, r.totalQty])
  ]
  const csv = rows.map(r => r.join(',')).join('\n')
  const a = document.createElement('a')
  a.href = 'data:text/csv;charset=utf-8,﻿' + encodeURIComponent(csv)
  a.download = 'Top10消耗备件.csv'
  a.click()
}

onMounted(() => {
  load()
})
</script>
