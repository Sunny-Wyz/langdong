<template>
    <div style="padding:24px">
        <el-card style="margin-bottom:16px">
            <div slot="header" style="display:flex;justify-content:space-between;align-items:center">
                <span>供应商绩效报告</span>
                <el-button type="success" size="small" icon="el-icon-download" @click="exportCsv">导出 CSV</el-button>
            </div>

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
            <div slot="header">供应商绩效排名</div>
            <el-table :data="tableData" border size="small" v-loading="loading">
                <el-table-column prop="supplierName" label="供应商"></el-table-column>
                <el-table-column prop="totalOrders" label="订单数" width="80" align="center"></el-table-column>
                <el-table-column prop="qualityRate" label="合格率(%)" width="95" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="scope.row.qualityRate >= 90 ? 'success' : 'danger'" size="small">
                            {{ scope.row.qualityRate || '—' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="onTimeRate" label="准时率(%)" width="95" align="center"></el-table-column>
                <el-table-column prop="avgUnitPrice" label="均价(元)" width="100" align="right"></el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
import * as echarts from 'echarts';
export default {
    data() { return { tableData: [], loading: false }; },
    created() { this.load(); },
    methods: {
        async load() {
            this.loading = true;
            try {
                const res = await this.$http.get('/report/supplier/performance');
                this.tableData = res.data || [];
                this.$nextTick(() => this.renderCharts());
            } finally { this.loading = false; }
        },
        renderCharts() {
            const names = this.tableData.map(r => r.supplierName);
            const renderBar = (ref, data, color) => {
                echarts.init(this.$refs[ref]).setOption({
                    tooltip: { trigger: 'axis' },
                    xAxis: { type: 'category', data: names, axisLabel: { rotate: 15 } },
                    yAxis: { type: 'value', max: 100 },
                    series: [{ type: 'bar', data, itemStyle: { color } }]
                });
            };
            renderBar('qualityChart', this.tableData.map(r => r.qualityRate || 0), '#67C23A');
            renderBar('onTimeChart', this.tableData.map(r => r.onTimeRate || 0), '#409EFF');
        },
        exportCsv() {
            const rows = [['供应商', '订单数', '合格率%', '准时率%', '均价(元)'],
            ...this.tableData.map(r => [r.supplierName, r.totalOrders, r.qualityRate, r.onTimeRate, r.avgUnitPrice])];
            const csv = rows.map(r => r.join(',')).join('\n');
            const a = document.createElement('a');
            a.href = 'data:text/csv;charset=utf-8,\uFEFF' + encodeURIComponent(csv);
            a.download = '供应商绩效报告.csv'; a.click();
        }
    }
};
</script>
