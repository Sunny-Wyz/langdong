<template>
  <div class="page-container paper-exp-page">
    <el-card shadow="hover" class="main-card">
      <template #header>
        <div class="phead header">
          <span class="header-icon">📑</span>
          <div class="title">论文实验回测</div>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        class="top-alert"
        title="数据说明：本页数字与论文正文一致，禁止运行时重算覆盖。表 3-5 为 36 件分层实验样本，与业务「分类结果查询」F9 口径（权重 40/30/20/10）不可直接对比；在线预测/服务水平 α 使用论文 AbcXyzCalculator（40/25/20/15 + 帕累托 70/90）。"
      />

      <el-tabs v-model="activeTab" type="border-card" class="tabs">
        <!-- ========== 预测评价 ========== -->
        <el-tab-pane label="预测评价" name="forecast">
          <div class="metric-row">
            <el-card v-for="m in metricCards" :key="m.name" shadow="never" class="metric-card">
              <div class="metric-name">{{ m.name }}</div>
              <div class="metric-def">{{ m.def }}</div>
            </el-card>
          </div>

          <section class="table-section">
            <h3>{{ table3_4.title }}</h3>
            <el-table :data="table3_4.rows" border size="small" :row-class-name="rowClass">
              <el-table-column
                v-for="col in table3_4.columns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :min-width="col.width || 90"
                :fixed="col.prop === 'method' ? 'left' : false"
              />
            </el-table>
            <div ref="chart34Ref" class="chart-box" />
          </section>

          <section class="table-section">
            <h3>{{ table3_5.title }}</h3>
            <p class="table-note">{{ table3_5.note }}</p>
            <el-table :data="table3_5.rows" border size="small" :row-class-name="rowClass">
              <el-table-column v-for="col in table3_5.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table3_6.title }}</h3>
            <el-table :data="table3_6.rows" border size="small" :row-class-name="rowClass">
              <el-table-column
                v-for="col in table3_6.columns"
                :key="col.prop"
                :prop="col.prop"
                :label="col.label"
                :min-width="col.width || 100"
              />
            </el-table>
          </section>

          <el-collapse v-model="forecastCollapse">
            <el-collapse-item :title="table3_7.title" name="t37">
              <p class="table-note">{{ table3_7.note }}</p>
              <el-table :data="table3_7.rows" border size="small" :row-class-name="rowClass">
                <el-table-column v-for="col in table3_7.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
              </el-table>
            </el-collapse-item>
            <el-collapse-item :title="table3_8.title" name="t38">
              <el-table :data="table3_8.rows" border size="small">
                <el-table-column v-for="col in table3_8.columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.prop === 'vs' ? 160 : 100" />
              </el-table>
            </el-collapse-item>
            <el-collapse-item :title="table3_9.title" name="t39">
              <el-table :data="table3_9.rows" border size="small">
                <el-table-column v-for="col in table3_9.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
              </el-table>
            </el-collapse-item>
            <el-collapse-item :title="table3_10.title" name="t310">
              <el-table :data="table3_10.rows" border size="small" :row-class-name="rowClass">
                <el-table-column v-for="col in table3_10.columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.prop === 'config' ? 200 : 120" />
              </el-table>
            </el-collapse-item>
            <el-collapse-item :title="table3_11.title" name="t311">
              <p class="table-note">{{ table3_11.note }}</p>
              <el-table :data="table3_11.rows" border size="small" :row-class-name="rowClass">
                <el-table-column v-for="col in table3_11.columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.prop === 'scheme' ? 180 : 100" />
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <!-- ========== 库存与服务水平 ========== -->
        <el-tab-pane label="库存与服务水平" name="inventory">
          <el-alert type="info" :closable="false" show-icon class="top-alert" :title="paperMeta.cslRule + '；' + paperMeta.mcParams" />

          <div class="kpi-row">
            <div class="kpi-item"><div class="kpi-label">缺货月次（经验→本文）</div><div class="kpi-val">30 → 6</div></div>
            <div class="kpi-item"><div class="kpi-label">缺货量（件）</div><div class="kpi-val">683 → 98</div></div>
            <div class="kpi-item"><div class="kpi-label">需求满足率</div><div class="kpi-val">85.0% → 98.1%</div></div>
            <div class="kpi-item"><div class="kpi-label">平均月末库存</div><div class="kpi-val">15.9 → 38.8</div></div>
          </div>

          <section class="table-section">
            <h3>{{ table3_12.title }}</h3>
            <p class="table-note">{{ table3_12.note }}</p>
            <el-table :data="table3_12.rows" border size="small" :row-class-name="rowClass">
              <el-table-column v-for="col in table3_12.columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.prop === 'stat' ? 200 : 120" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table3_13.title }}</h3>
            <p class="table-note">{{ table3_13.note }}</p>
            <el-table :data="table3_13.rows" border size="small" max-height="480" :row-class-name="rowClass">
              <el-table-column v-for="col in table3_13.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table3_14.title }}</h3>
            <p class="table-note">{{ table3_14.note }}</p>
            <el-table :data="table3_14.rows" border size="small">
              <el-table-column v-for="col in table3_14.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table3_15.title }}</h3>
            <p class="table-note">{{ table3_15.note }}</p>
            <el-table :data="table3_15.rows" border size="small">
              <el-table-column v-for="col in table3_15.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
            </el-table>
          </section>
        </el-tab-pane>

        <!-- ========== 方法说明 + 系统测试 ========== -->
        <el-tab-pane label="方法说明与系统测试" name="method">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            class="top-alert"
            title="ABC 双口径：在线预测 / 服务水平 α / 本页实验分层 = 论文 0.40/0.25/0.20/0.15 + 帕累托 70%/90%；业务「分类结果查询」= F9 0.40/0.30/0.20/0.10，与表 3-5 不可直接对比。"
          />

          <section class="table-section">
            <h3>六标准输出字段（论文）</h3>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="发生概率 p">阶段一 XGBoost 分类</el-descriptions-item>
              <el-descriptions-item label="正需求均值 μ">阶段二 reg:gamma</el-descriptions-item>
              <el-descriptions-item label="区间 [L,U]">Gamma 0.05/0.95 分位</el-descriptions-item>
              <el-descriptions-item label="提前期分位数">Qα(DL) 蒙特卡洛</el-descriptions-item>
              <el-descriptions-item label="安全库存 SS">ROP − ⌈E[DL]⌉</el-descriptions-item>
              <el-descriptions-item label="补货点 ROP">⌈Qα(DL)⌉</el-descriptions-item>
            </el-descriptions>
            <p class="table-note">{{ paperMeta.sampleNote36 }} · {{ paperMeta.note }}</p>
          </section>

          <section class="table-section">
            <h3>{{ table3_3.title }}</h3>
            <el-table :data="table3_3.rows" border size="small">
              <el-table-column v-for="col in table3_3.columns" :key="col.prop" :prop="col.prop" :label="col.label" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table4_5.title }}</h3>
            <p class="table-note">{{ table4_5.note }}</p>
            <el-table :data="table4_5.rows" border size="small" max-height="420">
              <el-table-column prop="id" label="编号" width="120" />
              <el-table-column prop="item" label="测试项" width="140" />
              <el-table-column prop="expect" label="操作与预期结果" min-width="260" />
              <el-table-column prop="result" label="结果" width="80" />
            </el-table>
          </section>

          <section class="table-section">
            <h3>{{ table4_6.title }}</h3>
            <p class="table-note">{{ table4_6.note }}</p>
            <el-table :data="table4_6.rows" border size="small" :row-class-name="rowClass">
              <el-table-column v-for="col in table4_6.columns" :key="col.prop" :prop="col.prop" :label="col.label" :min-width="col.prop === 'api' ? 160 : 80" />
            </el-table>
          </section>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import {
  paperMeta,
  metricCards,
  table3_3,
  table3_4,
  table3_5,
  table3_6,
  table3_7,
  table3_8,
  table3_9,
  table3_10,
  table3_11,
  table3_12,
  table3_13,
  table3_14,
  table3_15,
  table4_5,
  table4_6
} from '@/data/paper/paperExperimentTables'

const activeTab = ref('forecast')
const forecastCollapse = ref(['t37', 't311'])
const chart34Ref = ref<HTMLDivElement | null>(null)
let chart34: echarts.ECharts | null = null

function rowClass({ row }: { row: { highlight?: boolean } }) {
  return row.highlight ? 'row-highlight' : ''
}

function renderChart34() {
  if (!chart34Ref.value) return
  if (!chart34) {
    chart34 = echarts.init(chart34Ref.value)
  }
  const months = table3_4.months
  const seriesRows = table3_4.rows.filter((r) => r.method !== '实际需求')
  const actual = table3_4.rows.find((r) => r.method === '实际需求')
  const series: echarts.SeriesOption[] = []
  if (actual) {
    series.push({
      name: '实际需求',
      type: 'line',
      data: [actual.m07, actual.m08, actual.m09, actual.m10, actual.m11, actual.m12],
      lineStyle: { width: 3 },
      itemStyle: { color: '#303133' }
    })
  }
  // 仅绘制本文 + 两个代表性基线，避免折线过密
  const focus = seriesRows.filter((r) =>
    ['两阶段模型（本文）', '单阶段 XGBoost 回归', 'SBA'].includes(r.method)
  )
  focus.forEach((r) => {
    series.push({
      name: r.method,
      type: 'line',
      data: [r.m07, r.m08, r.m09, r.m10, r.m11, r.m12],
      lineStyle: r.method.includes('本文') ? { width: 3 } : { type: 'dashed' }
    })
  })
  chart34.setOption({
    title: { text: '表 3-4 示意折线（数据=论文表内点）', left: 'center', textStyle: { fontSize: 13 } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, type: 'scroll' },
    grid: { left: 48, right: 24, top: 40, bottom: 48 },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', name: '件' },
    series
  })
}

watch(activeTab, (tab) => {
  if (tab === 'forecast') {
    nextTick(() => renderChart34())
  }
})

onMounted(() => {
  nextTick(() => renderChart34())
  window.addEventListener('resize', () => chart34?.resize())
})

onBeforeUnmount(() => {
  chart34?.dispose()
  chart34 = null
})
</script>

<style scoped>
.paper-exp-page {
  padding-bottom: 24px;
}
.header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.top-alert {
  margin-bottom: 14px;
}
.tabs {
  border: none;
  box-shadow: none;
}
.metric-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.metric-card {
  background: #f5f7fa;
}
.metric-name {
  font-weight: 600;
  color: #0f3086;
  margin-bottom: 4px;
}
.metric-def {
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}
.table-section {
  margin: 18px 0 22px;
}
.table-section h3 {
  margin: 0 0 8px;
  font-size: 15px;
  color: #303133;
}
.table-note {
  font-size: 12px;
  color: #909399;
  margin: 0 0 8px;
  line-height: 1.5;
}
.chart-box {
  width: 100%;
  height: 320px;
  margin-top: 12px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
  margin: 12px 0 20px;
  padding: 12px;
  background: #f0f5ff;
  border-radius: 6px;
}
.kpi-item {
  text-align: center;
}
.kpi-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}
.kpi-val {
  font-size: 18px;
  font-weight: 700;
  color: #0f3086;
}
:deep(.row-highlight) {
  background-color: #ecf5ff !important;
  font-weight: 600;
}
</style>
