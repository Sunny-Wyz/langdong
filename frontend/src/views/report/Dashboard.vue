<template>
    <div style="padding: 24px">
        <el-row :gutter="20" style="margin-bottom: 24px">
            <el-col :span="6" v-for="kpi in kpiCards" :key="kpi.key">
                <el-card shadow="hover" body-style="padding:20px">
                    <div style="display:flex;align-items:center;gap:12px">
                        <i :class="kpi.icon" :style="{ fontSize: '36px', color: kpi.color }"></i>
                        <div>
                            <div style="font-size:12px;color:#999">{{ kpi.label }}</div>
                            <div style="font-size:22px;font-weight:bold;color:#333">{{ kpi.value }}</div>
                        </div>
                    </div>
                </el-card>
            </el-col>
        </el-row>

        <el-row :gutter="20">
            <el-col :span="12">
                <el-card shadow="hover">
                    <div slot="header">本月维修费用构成</div>
                    <div ref="maintenanceChart" style="height:260px"></div>
                </el-card>
            </el-col>
            <el-col :span="12">
                <el-card shadow="hover">
                    <div slot="header">备件消耗趋势（近6月）</div>
                    <div ref="trendChart" style="height:260px"></div>
                </el-card>
            </el-col>
        </el-row>
    </div>
</template>

<script>
import * as echarts from 'echarts';
export default {
    data() {
        return {
            kpi: null,
            kpiCards: [],
            maintenanceChart: null,
            trendChart: null,
        };
    },
    async created() {
        await this.loadKpi();
        await this.renderCharts();
    },
    methods: {
        async loadKpi() {
            try {
                const res = await this.$http.get('/report/kpi');
                const k = res.data || {};
                this.kpiCards = [
                    { key: 'inventory', label: '库存总金额', value: '¥ ' + (k.totalInventoryAmount || 0).toLocaleString(), icon: 'el-icon-box', color: '#409EFF' },
                    { key: 'turnover', label: '库存周转率', value: (k.inventoryTurnoverRate || 0) + ' 次/年', icon: 'el-icon-refresh', color: '#67C23A' },
                    { key: 'purchase', label: '本月采购额', value: '¥ ' + (k.monthPurchaseAmount || 0).toLocaleString(), icon: 'el-icon-shopping-cart-2', color: '#E6A23C' },
                    { key: 'repair', label: '本月维修费用', value: '¥ ' + (k.monthRepairCost || 0).toLocaleString(), icon: 'el-icon-setting', color: '#F56C6C' },
                    { key: 'avail', label: '设备可用率', value: (k.equipmentAvailability || 0) + ' %', icon: 'el-icon-data-line', color: '#909399' },
                ];
            } catch (e) { this.$message.error('加载KPI数据失败'); }
        },
        async renderCharts() {
            try {
                // 维修费用环形图
                const mRes = await this.$http.get('/report/maintenance/cost-by-month?months=1');
                const mData = (mRes.data || [])[0] || {};
                const mc = echarts.init(this.$refs.maintenanceChart);
                mc.setOption({
                    tooltip: { trigger: 'item' }, series: [{
                        type: 'pie', radius: ['40%', '65%'], data: [
                            { value: mData.partCost || 0, name: '备件费' },
                            { value: mData.laborCost || 0, name: '人工费' },
                            { value: mData.outsourceCost || 0, name: '外协费' }
                        ]
                    }]
                });

                // 消耗趋势折线图
                const tRes = await this.$http.get('/report/consumption/trend?months=6');
                const rows = tRes.data || [];
                const tc = echarts.init(this.$refs.trendChart);
                tc.setOption({
                    tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: rows.map(r => r.month) },
                    yAxis: { type: 'value' }, series: [{ name: '消耗数量', type: 'line', smooth: true, data: rows.map(r => r.totalQty) }]
                });
            } catch (e) { /* ignore */ }
        }
    }
};
</script>
