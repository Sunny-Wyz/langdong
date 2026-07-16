<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">维修费用分析</div>
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

      <el-row :gutter="20">
        <el-col :span="14">
          <div style="font-weight:bold;margin-bottom:8px">月度维修费用构成（元）</div>
          <div ref="costChart" style="height:280px"></div>
        </el-col>
        <el-col :span="10">
          <div style="font-weight:bold;margin-bottom:8px">费用构成占比（本月）</div>
          <div ref="pieChart" style="height:280px"></div>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">设备维修成本排名（Top 10）</div>
        </div>
      </template>
      <el-table :data="deviceList" border size="small" v-loading="loading">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column prop="deviceName" label="设备名称" />
        <el-table-column prop="deviceCode" label="设备编码" width="110" sortable="custom" />
        <el-table-column prop="repairCount" label="维修次数" width="90" align="center" />
        <el-table-column prop="totalCost" label="总费用(元)" width="120" align="right">
          <template #default="scope">
            <span style="font-weight:bold;color:#F56C6C">{{ scope.row.totalCost }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const months = ref(6)
const monthData = ref<any[]>([])
const deviceList = ref<any[]>([])
const loading = ref(false)
const costChart = ref<HTMLElement | null>(null)
const pieChart = ref<HTMLElement | null>(null)

async function load() {
  loading.value = true
  try {
    const [mRes, dRes] = await Promise.all([
      request.get('/report/maintenance/cost-by-month', { params: { months: months.value } }),
      request.get('/report/maintenance/cost-by-device')
    ])
    // 兼容直接数组 / { data: [] } 包装
    const monthPayload = mRes.data
    const devicePayload = dRes.data
    monthData.value = Array.isArray(monthPayload)
      ? monthPayload
      : (monthPayload?.list || monthPayload?.data || [])
    deviceList.value = Array.isArray(devicePayload)
      ? devicePayload
      : (devicePayload?.list || devicePayload?.data || [])
    await nextTick()
    renderCharts()
  } catch (e: any) {
    console.error('维修费用加载失败', e)
    const msg = e?.response?.data?.message || e?.message || '加载维修费用数据失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (costChart.value) {
    const chart = echarts.getInstanceByDom(costChart.value) || echarts.init(costChart.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { bottom: 0 },
      xAxis: { type: 'category', data: monthData.value.map((r: any) => r.month) },
      yAxis: { type: 'value' },
      series: [
        { name: '备件费', type: 'bar', stack: 'cost', data: monthData.value.map((r: any) => Number(r.partCost ?? 0)) },
        { name: '人工费', type: 'bar', stack: 'cost', data: monthData.value.map((r: any) => Number(r.laborCost ?? 0)) },
        { name: '外协费', type: 'bar', stack: 'cost', data: monthData.value.map((r: any) => Number(r.outsourceCost ?? 0)) }
      ]
    }, true)
  }
  if (pieChart.value) {
    const latest = monthData.value[monthData.value.length - 1] || {}
    const chart = echarts.getInstanceByDom(pieChart.value) || echarts.init(pieChart.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        data: [
          { value: Number(latest.partCost ?? 0), name: '备件费' },
          { value: Number(latest.laborCost ?? 0), name: '人工费' },
          { value: Number(latest.outsourceCost ?? 0), name: '外协费' }
        ]
      }]
    }, true)
  }
}

function exportCsv() {
  const rows = [
    ['月份', '备件费', '人工费', '外协费', '合计'],
    ...monthData.value.map((r: any) => [r.month, r.partCost, r.laborCost, r.outsourceCost, r.totalCost])
  ]
  const csv = rows.map(r => r.join(',')).join('\n')
  const a = document.createElement('a')
  a.href = 'data:text/csv;charset=utf-8,﻿' + encodeURIComponent(csv)
  a.download = '维修费用分析.csv'
  a.click()
}

onMounted(() => {
  load()
})
</script>
