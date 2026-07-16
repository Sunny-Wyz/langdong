<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">供应商绩效报告</div>
          <div class="head-btn-group">
            <el-button type="success" size="small" @click="exportCsv">📥 导出 CSV</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="12">
          <div style="font-weight:bold;margin-bottom:8px">质量合格率 (%)</div>
          <div ref="qualityChart" style="height:260px"></div>
        </el-col>
        <el-col :span="12">
          <div style="font-weight:bold;margin-bottom:8px">准时交货率 (%)</div>
          <div ref="onTimeChart" style="height:260px"></div>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">供应商绩效排名</div>
        </div>
      </template>
      <el-table :data="tableData" border size="small" v-loading="loading">
        <el-table-column prop="supplierName" label="供应商" />
        <el-table-column prop="totalOrders" label="订单数" width="80" align="center" />
        <el-table-column prop="qualityRate" label="合格率(%)" width="95" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.qualityRate >= 90 ? 'success' : 'danger'" size="small">
              {{ scope.row.qualityRate || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="onTimeRate" label="准时率(%)" width="95" align="center" />
        <el-table-column prop="avgUnitPrice" label="均价(元)" width="100" align="right" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'

const tableData = ref<any[]>([])
const loading = ref(false)
const qualityChart = ref<HTMLElement | null>(null)
const onTimeChart = ref<HTMLElement | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await request.get('/report/supplier/performance')
    tableData.value = res.data || []
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  const names = tableData.value.map((r: any) => r.supplierName)
  if (qualityChart.value) {
    echarts.init(qualityChart.value).setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 15 } },
      yAxis: { type: 'value', max: 100 },
      series: [{ type: 'bar', data: tableData.value.map((r: any) => r.qualityRate || 0), itemStyle: { color: '#67C23A' } }]
    })
  }
  if (onTimeChart.value) {
    echarts.init(onTimeChart.value).setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: names, axisLabel: { rotate: 15 } },
      yAxis: { type: 'value', max: 100 },
      series: [{ type: 'bar', data: tableData.value.map((r: any) => r.onTimeRate || 0), itemStyle: { color: '#409EFF' } }]
    })
  }
}

function exportCsv() {
  const rows = [
    ['供应商', '订单数', '合格率%', '准时率%', '均价(元)'],
    ...tableData.value.map((r: any) => [r.supplierName, r.totalOrders, r.qualityRate, r.onTimeRate, r.avgUnitPrice])
  ]
  const csv = rows.map(r => r.join(',')).join('\n')
  const a = document.createElement('a')
  a.href = 'data:text/csv;charset=utf-8,﻿' + encodeURIComponent(csv)
  a.download = '供应商绩效报告.csv'
  a.click()
}

onMounted(() => {
  load()
})
</script>
