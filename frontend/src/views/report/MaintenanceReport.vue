<template>
    <div style="padding:24px">
        <el-card style="margin-bottom:16px">
            <div slot="header" style="display:flex;justify-content:space-between;align-items:center">
                <span>维修费用分析</span>
                <div>
                    <el-select v-model="months" size="small" style="width:120px;margin-right:8px" @change="load">
                        <el-option :value="3" label="近3个月"></el-option>
                        <el-option :value="6" label="近6个月"></el-option>
                        <el-option :value="12" label="近12个月"></el-option>
                    </el-select>
                    <el-button type="success" size="small" icon="el-icon-download" @click="exportCsv">导出 CSV</el-button>
                </div>
            </div>

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
            <div slot="header">设备维修成本排名（Top 10）</div>
            <el-table :data="deviceList" border size="small" v-loading="loading">
                <el-table-column type="index" width="50" align="center"></el-table-column>
                <el-table-column prop="deviceName" label="设备名称"></el-table-column>
                <el-table-column prop="deviceCode" label="设备编码" width="110"></el-table-column>
                <el-table-column prop="repairCount" label="维修次数" width="90" align="center"></el-table-column>
                <el-table-column prop="totalCost" label="总费用(元)" width="120" align="right">
                    <template slot-scope="scope">
                        <span style="font-weight:bold;color:#F56C6C">{{ scope.row.totalCost }}</span>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
import * as echarts from 'echarts';
export default {
    data() { return { months: 6, monthData: [], deviceList: [], loading: false }; },
    created() { this.load(); },
    methods: {
        async load() {
            this.loading = true;
            try {
                const [mRes, dRes] = await Promise.all([
                    this.$http.get(`/report/maintenance/cost-by-month?months=${this.months}`),
                    this.$http.get('/report/maintenance/cost-by-device')
                ]);
                this.monthData = mRes.data || [];
                this.deviceList = dRes.data || [];
                this.$nextTick(() => this.renderCharts());
            } finally { this.loading = false; }
        },
        renderCharts() {
            const months = this.monthData.map(r => r.month);
            const cc = echarts.init(this.$refs.costChart);
            cc.setOption({
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                legend: { bottom: 0 },
                xAxis: { type: 'category', data: months },
                yAxis: { type: 'value', name: '元' },
                series: [
                    { name: '备件费', type: 'bar', stack: 'cost', data: this.monthData.map(r => r.partCost) },
                    { name: '人工费', type: 'bar', stack: 'cost', data: this.monthData.map(r => r.laborCost) },
                    { name: '外协费', type: 'bar', stack: 'cost', data: this.monthData.map(r => r.outsourceCost) }
                ]
            });
            const latest = this.monthData[this.monthData.length - 1] || {};
            echarts.init(this.$refs.pieChart).setOption({
                tooltip: { trigger: 'item' }, legend: { bottom: 0 },
                series: [{
                    type: 'pie', radius: ['40%', '65%'], data: [
                        { value: latest.partCost || 0, name: '备件费' },
                        { value: latest.laborCost || 0, name: '人工费' },
                        { value: latest.outsourceCost || 0, name: '外协费' }
                    ]
                }]
            });
        },
        exportCsv() {
            const rows = [['月份', '备件费', '人工费', '外协费', '合计'],
            ...this.monthData.map(r => [r.month, r.partCost, r.laborCost, r.outsourceCost, r.totalCost])];
            const csv = rows.map(r => r.join(',')).join('\n');
            const a = document.createElement('a');
            a.href = 'data:text/csv;charset=utf-8,\uFEFF' + encodeURIComponent(csv);
            a.download = '维修费用分析.csv'; a.click();
        }
    }
};
</script>
