<template>
    <div style="padding:24px">
        <el-card style="margin-bottom:16px">
            <div slot="header" style="display:flex;justify-content:space-between;align-items:center">
                <span>库存分析报告</span>
                <el-button type="success" size="small" icon="el-icon-download" @click="exportCsv">导出 CSV</el-button>
            </div>

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
            <div slot="header">
                <span>滞库备件清单</span>
                <span style="margin-left:12px;font-size:12px;color:#999">超过
                    <el-input-number v-model="thresholdDays" :min="30" :max="365" size="mini" style="width:80px"
                        @change="loadStagnant"></el-input-number>
                    天未流动
                </span>
            </div>
            <el-table :data="stagnantList" border size="small" v-loading="loading">
                <el-table-column prop="partCode" label="编码" width="110"></el-table-column>
                <el-table-column prop="partName" label="名称"></el-table-column>
                <el-table-column prop="currentStock" label="库存" width="80" align="center"></el-table-column>
                <el-table-column prop="stockAmount" label="金额(元)" width="100" align="right"></el-table-column>
                <el-table-column prop="stagnantDays" label="滞库天数" width="95" align="center">
                    <template slot-scope="scope">
                        <el-tag type="danger" size="small">{{ scope.row.stagnantDays }}天</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="lastMovementTime" label="最后流动时间" width="160"></el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
import * as echarts from 'echarts';
export default {
    data() {
        return { stagnantList: [], thresholdDays: 90, loading: false };
    },
    async created() { await this.loadAll(); },
    methods: {
        async loadAll() {
            await Promise.all([this.renderCharts(), this.loadStagnant()]);
        },
        async renderCharts() {
            try {
                const [abcRes, tvRes] = await Promise.all([
                    this.$http.get('/report/inventory/abc'),
                    this.$http.get('/report/inventory/turnover')
                ]);
                const abcData = abcRes.data || [];
                echarts.init(this.$refs.abcChart).setOption({
                    tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
                    legend: { bottom: 0 },
                    series: [{
                        type: 'pie', radius: '60%',
                        data: abcData.map(r => ({ name: r.classLevel, value: r.partCount }))
                    }]
                });
                const tvData = tvRes.data || [];
                echarts.init(this.$refs.turnoverChart).setOption({
                    tooltip: { trigger: 'axis' },
                    xAxis: { type: 'category', data: tvData.map(r => r.categoryName || '未分类'), axisLabel: { rotate: 15 } },
                    yAxis: { type: 'value', name: '金额(元)' },
                    series: [{
                        name: '库存金额', type: 'bar', data: tvData.map(r => r.totalAmount),
                        itemStyle: { color: '#409EFF' }
                    }]
                });
            } catch (e) { /* ignore */ }
        },
        async loadStagnant() {
            this.loading = true;
            try {
                const res = await this.$http.get(`/report/inventory/stagnant?thresholdDays=${this.thresholdDays}`);
                this.stagnantList = res.data || [];
            } finally { this.loading = false; }
        },
        exportCsv() {
            const rows = [['编码', '名称', '库存', '金额(元)', '滞库天数'],
            ...this.stagnantList.map(r => [r.partCode, r.partName, r.currentStock, r.stockAmount, r.stagnantDays])];
            const csv = rows.map(r => r.join(',')).join('\n');
            const a = document.createElement('a');
            a.href = 'data:text/csv;charset=utf-8,\uFEFF' + encodeURIComponent(csv);
            a.download = '滞库备件清单.csv'; a.click();
        }
    }
};
</script>
