<template>
    <div style="padding:24px">
        <el-card style="margin-bottom:16px">
            <div slot="header" style="display:flex;justify-content:space-between;align-items:center">
                <span>备件消耗趋势分析</span>
                <div>
                    <el-select v-model="months" size="small" style="width:120px;margin-right:8px" @change="load">
                        <el-option :value="3" label="近3个月"></el-option>
                        <el-option :value="6" label="近6个月"></el-option>
                        <el-option :value="12" label="近12个月"></el-option>
                    </el-select>
                    <el-button type="success" size="small" icon="el-icon-download" @click="exportCsv">导出 CSV</el-button>
                </div>
            </div>
            <div ref="trendChart" style="height:300px"></div>
        </el-card>

        <el-card>
            <div slot="header">Top 10 高消耗备件</div>
            <div ref="top10Chart" style="height:300px"></div>
        </el-card>
    </div>
</template>

<script>
import * as echarts from 'echarts';
export default {
    data() { return { months: 6, trendData: [], top10Data: [] }; },
    created() { this.load(); },
    methods: {
        async load() {
            try {
                const [tRes, top10Res] = await Promise.all([
                    this.$http.get(`/report/consumption/trend?months=${this.months}`),
                    this.$http.get('/report/consumption/top10')
                ]);
                this.trendData = tRes.data || [];
                this.top10Data = top10Res.data || [];
                this.$nextTick(() => this.renderCharts());
            } catch (e) { this.$message.error('加载消耗数据失败'); }
        },
        renderCharts() {
            const tc = echarts.init(this.$refs.trendChart);
            tc.setOption({
                tooltip: { trigger: 'axis' }, legend: { bottom: 0 },
                xAxis: { type: 'category', data: this.trendData.map(r => r.month) },
                yAxis: { type: 'value' },
                series: [
                    { name: '消耗数量', type: 'line', smooth: true, data: this.trendData.map(r => r.totalQty), areaStyle: {} },
                    { name: '领用单数', type: 'bar', data: this.trendData.map(r => r.orderCount), yAxisIndex: 0 }
                ]
            });
            const pc = echarts.init(this.$refs.top10Chart);
            pc.setOption({
                tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
                grid: { left: '20%' },
                xAxis: { type: 'value' },
                yAxis: { type: 'category', data: this.top10Data.map(r => r.partName).reverse() },
                series: [{
                    name: '消耗数量', type: 'bar', data: this.top10Data.map(r => r.totalQty).reverse(),
                    itemStyle: { color: '#409EFF' }
                }]
            });
        },
        exportCsv() {
            const rows = [['备件编码', '备件名称', '消耗数量'],
            ...this.top10Data.map(r => [r.partCode, r.partName, r.totalQty])];
            const csv = rows.map(r => r.join(',')).join('\n');
            const a = document.createElement('a');
            a.href = 'data:text/csv;charset=utf-8,\uFEFF' + encodeURIComponent(csv);
            a.download = 'Top10消耗备件.csv'; a.click();
        }
    }
};
</script>
