<template>
  <div class="page-container">
    <el-card style="margin-bottom:16px">
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">库存分析报告</div>
          <div class="head-btn-group">
            <el-button type="success" size="small" @click="exportCsv">📥 导出 CSV</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="20" style="margin-bottom:20px">
        <el-col :span="10">
          <div style="font-weight:bold;margin-bottom:8px">ABC分类分布</div>
          <div ref="abcChart" style="height:260px"></div>
        </el-col>
        <el-col :span="14">
          <div style="font-weight:bold;margin-bottom:8px">各类别库存金额</div>
          <div ref="turnoverChart" style="height:260px"></div>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header>
        <div class="phead header">
          <span class="title-icon">📊</span>
          <div class="title">滞库备件清单</div>
          <div class="head-btn-group">
            <span style="margin-left:12px;font-size:12px;color:#999">
              超过
              <el-input-number v-model="thresholdDays" :min="30" :max="365" size="small" style="width:80px" @change="loadStagnant" />
              天未流动
            </span>
          </div>
        </div>
      </template>
      <el-table :data="stagnantList" border size="small" v-loading="loading">
        <el-table-column prop="partCode" label="编码" width="110" sortable="custom" />
        <el-table-column prop="partName" label="名称" />
        <el-table-column prop="currentStock" label="库存" width="80" align="center" />
        <el-table-column prop="stockAmount" label="金额(元)" width="100" align="right" />
        <el-table-column prop="stagnantDays" label="滞库天数" width="95" align="center">
          <template #default="scope">
            <el-tag type="danger" size="small">{{ scope.row.stagnantDays }}天</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'

const thresholdDays = ref(90)
const stagnantList = ref<any[]>([])
const loading = ref(false)
const abcChart = ref<HTMLElement | null>(null)
const turnoverChart = ref<HTMLElement | null>(null)

function unwrapList(payload: any): any[] {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

async function loadCharts() {
  try {
    const [abcRes, tvRes] = await Promise.all([
      request.get('/report/inventory/abc'),
      request.get('/report/inventory/turnover')
    ])
    const abcData = unwrapList(abcRes.data)
    const tvData = unwrapList(tvRes.data)
    await nextTick()
    if (abcChart.value) {
      const chart = echarts.getInstanceByDom(abcChart.value) || echarts.init(abcChart.value)
      const pieData = abcData.map((r: any) => ({
        name: r.classLevel || r.abcClass || r.name || '未分类',
        value: Number(r.partCount ?? r.count ?? r.amount ?? r.value ?? 0)
      })).filter((d: any) => d.value > 0)
      chart.setOption({
        tooltip: { trigger: 'item' },
        legend: { bottom: 0 },
        series: [{
          type: 'pie',
          radius: ['40%', '65%'],
          data: pieData.length ? pieData : [{ name: '暂无分类数据', value: 1, itemStyle: { color: '#dcdfe6' } }]
        }]
      }, true)
    }
    if (turnoverChart.value) {
      const chart = echarts.getInstanceByDom(turnoverChart.value) || echarts.init(turnoverChart.value)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: tvData.map((r: any) => r.categoryName || r.name || '未分类'), axisLabel: { rotate: 15 } },
        yAxis: { type: 'value', name: '金额(元)' },
        series: [{
          name: '库存金额',
          type: 'bar',
          data: tvData.map((r: any) => Number(r.totalAmount ?? 0)),
          itemStyle: { color: '#409EFF' }
        }]
      }, true)
    }
  } catch (e: any) {
    console.error('库存分析图表加载失败', e)
  }
}

async function loadStagnant() {
  loading.value = true
  try {
    const res = await request.get(`/report/inventory/stagnant?thresholdDays=${thresholdDays.value}`)
    stagnantList.value = unwrapList(res.data)
  } catch (e: any) {
    console.error('滞库清单加载失败', e)
    stagnantList.value = []
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  const rows = [
    ['编码', '名称', '库存', '金额(元)', '滞库天数'],
    ...stagnantList.value.map((r: any) => [r.partCode, r.partName, r.currentStock, r.stockAmount, r.stagnantDays])
  ]
  const csv = rows.map(r => r.join(',')).join('\n')
  const a = document.createElement('a')
  a.href = 'data:text/csv;charset=utf-8,﻿' + encodeURIComponent(csv)
  a.download = '滞库备件清单.csv'
  a.click()
}

onMounted(() => {
  loadCharts()
  loadStagnant()
})
</script>
