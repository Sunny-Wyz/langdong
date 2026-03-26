<template>
    <div class="dashboard-container">
        <!-- 顶部待办摘要条 -->
        <div class="todo-bar" v-if="todoBar.total > 0">
            <i class="el-icon-bell todo-icon"></i>
            <span class="todo-text">今日待办：</span>
            <el-tag v-if="todoBar.lowStock > 0" type="danger" size="small" class="todo-tag">
                低库存预警 {{ todoBar.lowStock }} 条
            </el-tag>
            <el-tag v-if="todoBar.overdueWO > 0" type="warning" size="small" class="todo-tag">
                逾期工单 {{ todoBar.overdueWO }} 条
            </el-tag>
            <el-tag v-if="todoBar.overduePO > 0" size="small" class="todo-tag" style="background:#e6f7ff;color:#1890ff;border-color:#91d5ff">
                采购延期 {{ todoBar.overduePO }} 条
            </el-tag>
            <el-button type="text" size="small" class="todo-link" @click="goToWarning">查看全部 →</el-button>
        </div>

        <!-- 月份筛选栏 -->
        <el-card shadow="never" class="filter-card">
            <div class="filter-bar">
                <i class="el-icon-date filter-icon"></i>
                <span class="filter-label">KPI 月份</span>
                <el-date-picker
                    v-model="selectedMonth"
                    type="month"
                    format="yyyy-MM"
                    value-format="yyyy-MM"
                    placeholder="选择月份"
                    size="small"
                    style="width:140px"
                    @change="loadKpi"
                />
                <el-button
                    size="small"
                    icon="el-icon-refresh"
                    :loading="loading"
                    style="margin-left:8px"
                    @click="handleRefresh"
                >刷新</el-button>
            </div>
        </el-card>

        <!-- KPI 卡片区 -->
        <el-row :gutter="16" class="kpi-row" v-loading="loading">
            <el-col
                v-for="kpi in kpiCards"
                :key="kpi.key"
                :xs="24" :sm="12" :md="8" :lg="6" :xl="6"
            >
                <div class="kpi-card" :style="{ background: kpi.gradient }">
                    <div class="kpi-icon-wrap">
                        <i :class="kpi.icon"></i>
                    </div>
                    <div class="kpi-body">
                        <div class="kpi-label">{{ kpi.label }}</div>
                        <div class="kpi-value">{{ kpi.value }}</div>
                        <div class="kpi-trend" v-if="kpi.trend !== null">
                            <i :class="kpi.trend >= 0 ? 'el-icon-top trend-up' : 'el-icon-bottom trend-down'"></i>
                            <span :class="kpi.trend >= 0 ? 'trend-up' : 'trend-down'">{{ Math.abs(kpi.trend) }}%</span>
                            <span class="trend-label">较上月</span>
                        </div>
                    </div>
                </div>
            </el-col>
        </el-row>

        <!-- 图表区 -->
        <el-row :gutter="16" class="chart-row">
            <el-col :xs="24" :md="12">
                <el-card shadow="hover" class="chart-card">
                    <div slot="header" class="card-header">
                        <span>本月维修费用构成</span>
                        <span v-if="maintenanceTotal > 0" class="header-sub">
                            合计 ¥{{ maintenanceTotal.toLocaleString() }}
                        </span>
                    </div>
                    <div ref="maintenanceChart" style="height:280px"></div>
                </el-card>
            </el-col>
            <el-col :xs="24" :md="12">
                <el-card shadow="hover" class="chart-card">
                    <div slot="header" class="card-header">
                        <span>备件消耗趋势（近6月）</span>
                    </div>
                    <div ref="trendChart" style="height:280px"></div>
                </el-card>
            </el-col>
        </el-row>

        <!-- 库存预警表格 -->
        <el-card shadow="hover" class="warning-table-card" v-if="lowStockList.length > 0">
            <div slot="header" class="card-header">
                <i class="el-icon-warning" style="color:#F56C6C;margin-right:6px"></i>
                <span>库存预警</span>
                <el-badge :value="lowStockList.length" type="danger" style="margin-left:8px"></el-badge>
                <el-button type="text" size="small" class="header-link" @click="goToWarning">查看全部 →</el-button>
            </div>
            <el-table :data="lowStockList.slice(0, 10)" size="small" border stripe>
                <el-table-column prop="severity" label="紧急程度" width="90" align="center">
                    <template slot-scope="scope">
                        <el-tag
                            :type="scope.row.severity === '紧急' ? 'danger' : 'warning'"
                            size="mini"
                        >{{ scope.row.severity }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="title" label="预警标题" min-width="160"></el-table-column>
                <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip></el-table-column>
                <el-table-column label="操作" width="90" align="center">
                    <template>
                        <el-button size="mini" type="primary" plain @click="goToWarning">去处理</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
import * as echarts from 'echarts';

const GRADIENTS = [
    'linear-gradient(135deg, #1890ff 0%, #096dd9 100%)',
    'linear-gradient(135deg, #52c41a 0%, #389e0d 100%)',
    'linear-gradient(135deg, #fa8c16 0%, #d46b08 100%)',
    'linear-gradient(135deg, #f5222d 0%, #cf1322 100%)',
    'linear-gradient(135deg, #722ed1 0%, #531dab 100%)',
];

export default {
    name: 'Dashboard',
    data() {
        return {
            selectedMonth: '',
            loading: false,
            kpiCards: [],
            maintenanceTotal: 0,
            lowStockList: [],
            todoBar: { total: 0, lowStock: 0, overdueWO: 0, overduePO: 0 },
            maintenanceChartInst: null,
            trendChartInst: null,
        };
    },
    async created() {
        this.selectedMonth = this.currentMonth();
        await this.loadAll();
    },
    mounted() {
        window.addEventListener('resize', this.resizeCharts);
    },
    beforeDestroy() {
        window.removeEventListener('resize', this.resizeCharts);
        if (this.maintenanceChartInst) this.maintenanceChartInst.dispose();
        if (this.trendChartInst) this.trendChartInst.dispose();
    },
    methods: {
        currentMonth() {
            const d = new Date();
            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        },
        prevMonth(ym) {
            const [y, m] = ym.split('-').map(Number);
            const d = new Date(y, m - 2, 1);
            return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
        },
        async loadAll() {
            this.loading = true;
            await Promise.all([this.loadKpi(), this.loadWarnings(), this.renderCharts()]);
            this.loading = false;
        },
        async handleRefresh() {
            await this.loadAll();
        },
        async loadKpi() {
            try {
                const ym = this.selectedMonth || this.currentMonth();
                const prev = this.prevMonth(ym);
                const [curRes, prevRes] = await Promise.all([
                    this.$http.get('/report/kpi', { params: { yearMonth: ym } }),
                    this.$http.get('/report/kpi', { params: { yearMonth: prev } }),
                ]);
                const k = curRes.data || {};
                const p = prevRes.data || {};

                const calcTrend = (cur, pre) => {
                    if (!pre || pre === 0) return null;
                    return Math.round(((cur - pre) / pre) * 100);
                };

                this.kpiCards = [
                    {
                        key: 'inventory',
                        label: '库存总金额',
                        value: '¥ ' + (k.totalInventoryAmount || 0).toLocaleString(),
                        icon: 'el-icon-box',
                        gradient: GRADIENTS[0],
                        trend: calcTrend(k.totalInventoryAmount, p.totalInventoryAmount),
                    },
                    {
                        key: 'turnover',
                        label: '库存周转率',
                        value: (k.inventoryTurnoverRate || 0) + ' 次/年',
                        icon: 'el-icon-refresh',
                        gradient: GRADIENTS[1],
                        trend: calcTrend(k.inventoryTurnoverRate, p.inventoryTurnoverRate),
                    },
                    {
                        key: 'purchase',
                        label: '当月采购额',
                        value: '¥ ' + (k.monthPurchaseAmount || 0).toLocaleString(),
                        icon: 'el-icon-shopping-cart-2',
                        gradient: GRADIENTS[2],
                        trend: calcTrend(k.monthPurchaseAmount, p.monthPurchaseAmount),
                    },
                    {
                        key: 'repair',
                        label: '当月维修费用',
                        value: '¥ ' + (k.monthRepairCost || 0).toLocaleString(),
                        icon: 'el-icon-setting',
                        gradient: GRADIENTS[3],
                        trend: calcTrend(k.monthRepairCost, p.monthRepairCost),
                    },
                    {
                        key: 'avail',
                        label: '设备可用率',
                        value: (k.equipmentAvailability || 0) + ' %',
                        icon: 'el-icon-data-line',
                        gradient: GRADIENTS[4],
                        trend: calcTrend(k.equipmentAvailability, p.equipmentAvailability),
                    },
                ];
            } catch (_) {
                this.$message.error('加载 KPI 数据失败');
            }
        },
        async loadWarnings() {
            try {
                const res = await this.$http.get('/warnings');
                const data = res.data || {};
                const low = (data.lowStock || []).length;
                const wo = (data.overdueWO || []).length;
                const po = (data.overduePO || []).length;
                this.todoBar = { total: low + wo + po, lowStock: low, overdueWO: wo, overduePO: po };
                this.lowStockList = data.lowStock || [];
            } catch (_) { /* 预警数据非关键，忽略失败 */ }
        },
        async renderCharts() {
            try {
                const [mRes, tRes] = await Promise.all([
                    this.$http.get('/report/maintenance/cost-by-month?months=1'),
                    this.$http.get('/report/consumption/trend?months=6'),
                ]);

                // 维修费用环形图
                const mData = (mRes.data || [])[0] || {};
                const partCost = Number(mData.partCost || 0);
                const laborCost = Number(mData.laborCost || 0);
                const outsourceCost = Number(mData.outsourceCost || 0);
                this.maintenanceTotal = partCost + laborCost + outsourceCost;

                if (this.maintenanceChartInst) this.maintenanceChartInst.dispose();
                this.maintenanceChartInst = echarts.init(this.$refs.maintenanceChart);
                this.maintenanceChartInst.setOption({
                    color: ['#5470c6', '#91cc75', '#fac858'],
                    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
                    legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 12 } },
                    series: [{
                        type: 'pie',
                        radius: ['38%', '62%'],
                        center: ['50%', '44%'],
                        avoidLabelOverlap: true,
                        label: { show: true, formatter: '{b}\n¥{c}' },
                        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
                        data: [
                            { value: partCost, name: '备件费' },
                            { value: laborCost, name: '人工费' },
                            { value: outsourceCost, name: '外协费' },
                        ],
                    }],
                    graphic: [{
                        type: 'text',
                        left: 'center',
                        top: '40%',
                        style: {
                            text: '¥' + this.maintenanceTotal.toLocaleString(),
                            textAlign: 'center',
                            fill: '#333',
                            fontSize: 14,
                            fontWeight: 'bold',
                        },
                    }],
                });

                // 消耗趋势渐变面积图
                const rows = tRes.data || [];
                if (this.trendChartInst) this.trendChartInst.dispose();
                this.trendChartInst = echarts.init(this.$refs.trendChart);
                this.trendChartInst.setOption({
                    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
                    grid: { left: 40, right: 20, top: 20, bottom: 30 },
                    xAxis: {
                        type: 'category',
                        data: rows.map(r => r.month),
                        axisLine: { lineStyle: { color: '#ddd' } },
                        axisTick: { show: false },
                    },
                    yAxis: {
                        type: 'value',
                        splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
                        axisLine: { show: false },
                        axisTick: { show: false },
                    },
                    series: [{
                        name: '消耗数量',
                        type: 'line',
                        smooth: true,
                        symbol: 'circle',
                        symbolSize: 7,
                        lineStyle: { width: 3, color: '#1890ff' },
                        itemStyle: { color: '#1890ff', borderWidth: 2, borderColor: '#fff' },
                        label: { show: true, position: 'top', fontSize: 11, color: '#555' },
                        areaStyle: {
                            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                                { offset: 0, color: 'rgba(24,144,255,0.35)' },
                                { offset: 1, color: 'rgba(24,144,255,0.02)' },
                            ]),
                        },
                        data: rows.map(r => r.totalQty),
                    }],
                });
            } catch (_) { /* 图表数据非关键 */ }
        },
        resizeCharts() {
            if (this.maintenanceChartInst) this.maintenanceChartInst.resize();
            if (this.trendChartInst) this.trendChartInst.resize();
        },
        goToWarning() {
            this.$router.push('/report/warning-center');
        },
    },
};
</script>

<style scoped>
.dashboard-container {
    padding: 20px 24px;
    background: #f5f7fa;
    min-height: 100%;
}

/* 待办摘要条 */
.todo-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    background: #fff7e6;
    border: 1px solid #ffe7ba;
    border-radius: 6px;
    padding: 8px 16px;
    margin-bottom: 16px;
    flex-wrap: wrap;
}
.todo-icon { color: #fa8c16; font-size: 16px; }
.todo-text { font-size: 13px; color: #595959; font-weight: 600; }
.todo-tag { margin: 0 2px; }
.todo-link { color: #1890ff; margin-left: 4px; padding: 0; }

/* 筛选栏 */
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; }
.filter-icon { color: #1890ff; font-size: 16px; }
.filter-label { font-size: 14px; font-weight: 600; color: #333; white-space: nowrap; }

/* KPI 卡片 */
.kpi-row { margin-bottom: 16px; }
.kpi-row .el-col { margin-bottom: 16px; }
.kpi-card {
    border-radius: 10px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    color: #fff;
    min-height: 100px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
    transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover {
    transform: translateY(-3px);
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.18);
}
.kpi-icon-wrap {
    width: 52px;
    height: 52px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}
.kpi-icon-wrap i { font-size: 26px; color: #fff; }
.kpi-body { flex: 1; min-width: 0; }
.kpi-label { font-size: 12px; color: rgba(255,255,255,0.85); margin-bottom: 4px; }
.kpi-value { font-size: 20px; font-weight: 700; line-height: 1.2; word-break: break-all; }
.kpi-trend { margin-top: 4px; font-size: 12px; display: flex; align-items: center; gap: 3px; }
.trend-up { color: #d4f7d4; }
.trend-down { color: #ffe0e0; }
.trend-label { color: rgba(255,255,255,0.7); }

/* 图表卡片 */
.chart-row { margin-bottom: 16px; }
.chart-card { border-radius: 8px; }
.chart-row .el-col { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.header-sub { font-size: 12px; color: #999; font-weight: 400; margin-left: auto; }
.header-link { margin-left: auto; padding: 0; color: #1890ff; }

/* 预警表格 */
.warning-table-card { border-radius: 8px; }
</style>
