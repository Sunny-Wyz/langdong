<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>工单查询统计</span>
            </div>

            <!-- 查询条件 -->
            <el-form :inline="true" :model="query" style="margin-bottom: 16px">
                <el-form-item label="工单状态">
                    <el-select v-model="query.orderStatus" placeholder="全部状态" clearable style="width: 120px">
                        <el-option label="报修" value="报修"></el-option>
                        <el-option label="已派工" value="已派工"></el-option>
                        <el-option label="维修中" value="维修中"></el-option>
                        <el-option label="完工" value="完工"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="紧急程度">
                    <el-select v-model="query.faultLevel" placeholder="全部" clearable style="width: 100px">
                        <el-option label="紧急" value="紧急"></el-option>
                        <el-option label="一般" value="一般"></el-option>
                        <el-option label="计划" value="计划"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="关联设备">
                    <el-select v-model="query.deviceId" placeholder="全部设备" clearable filterable style="width: 180px">
                        <el-option v-for="eq in equipmentList" :key="eq.id"
                            :label="eq.name + ' (' + eq.code + ')'" :value="eq.id"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="报修时间">
                    <el-date-picker v-model="query.timeRange" type="daterange"
                        range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
                        value-format="yyyy-MM-dd" style="width: 240px"></el-date-picker>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" icon="el-icon-search" @click="loadList">查询</el-button>
                    <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
                </el-form-item>
            </el-form>

            <!-- 工单列表 -->
            <el-table :data="workOrders" border stripe v-loading="loading">
                <el-table-column prop="workOrderNo" label="工单编号" width="200"></el-table-column>
                <el-table-column label="故障设备" min-width="150">
                    <template slot-scope="scope">
                        {{ scope.row.deviceName }} <span style="color:#999">({{ scope.row.deviceCode }})</span>
                    </template>
                </el-table-column>
                <el-table-column prop="reporterName" label="报修人" width="90"></el-table-column>
                <el-table-column prop="assigneeName" label="维修人员" width="90">
                    <template slot-scope="scope">{{ scope.row.assigneeName || '-' }}</template>
                </el-table-column>
                <el-table-column label="紧急程度" width="90" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="levelType(scope.row.faultLevel)" size="small">{{ scope.row.faultLevel }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="工单状态" width="90" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="statusType(scope.row.orderStatus)" size="small">{{ scope.row.orderStatus }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="报修时间" width="160">
                    <template slot-scope="scope">{{ formatTime(scope.row.reportTime) }}</template>
                </el-table-column>
                <el-table-column label="维修时长" width="100" align="center">
                    <template slot-scope="scope">
                        <span v-if="scope.row.mttrMinutes">{{ formatMttr(scope.row.mttrMinutes) }}</span>
                        <span v-else style="color:#ccc">-</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center" fixed="right">
                    <template slot-scope="scope">
                        <el-button type="text" @click="viewDetail(scope.row)">详情</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>

        <!-- 详情弹窗 -->
        <el-dialog title="工单详情" :visible.sync="dialogVisible" width="700px">
            <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="工单编号" :span="2">{{ detail.workOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="故障设备">{{ detail.deviceName }} ({{ detail.deviceCode }})</el-descriptions-item>
                <el-descriptions-item label="报修人">{{ detail.reporterName }}</el-descriptions-item>
                <el-descriptions-item label="紧急程度">
                    <el-tag :type="levelType(detail.faultLevel)" size="small">{{ detail.faultLevel }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="工单状态">
                    <el-tag :type="statusType(detail.orderStatus)" size="small">{{ detail.orderStatus }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="报修时间">{{ formatTime(detail.reportTime) }}</el-descriptions-item>
                <el-descriptions-item label="维修人员">{{ detail.assigneeName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="计划完成时间">{{ formatTime(detail.planFinish) }}</el-descriptions-item>
                <el-descriptions-item label="实际完成时间">{{ formatTime(detail.actualFinish) }}</el-descriptions-item>
                <el-descriptions-item label="故障描述" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
                <el-descriptions-item label="故障根因分析" :span="2">{{ detail.faultCause || '-' }}</el-descriptions-item>
                <el-descriptions-item label="维修方案描述" :span="2">{{ detail.repairMethod || '-' }}</el-descriptions-item>
                <el-descriptions-item label="维修时长">{{ detail.mttrMinutes ? formatMttr(detail.mttrMinutes) : '-' }}</el-descriptions-item>
                <el-descriptions-item label=""></el-descriptions-item>
                <el-descriptions-item label="备件费用">{{ detail.partCost != null ? '￥' + detail.partCost : '-' }}</el-descriptions-item>
                <el-descriptions-item label="人工费用">{{ detail.laborCost != null ? '￥' + detail.laborCost : '-' }}</el-descriptions-item>
                <el-descriptions-item label="外协费用">{{ detail.outsourceCost != null ? '￥' + detail.outsourceCost : '-' }}</el-descriptions-item>
                <el-descriptions-item label="费用合计">
                    <strong style="color:#E6A23C">{{ calcTotal(detail) }}</strong>
                </el-descriptions-item>
            </el-descriptions>
            <span slot="footer">
                <el-button @click="dialogVisible = false">关闭</el-button>
            </span>
        </el-dialog>
    </div>
</template>

<script>
export default {
    data() {
        return {
            workOrders: [],
            loading: false,
            equipmentList: [],
            query: { orderStatus: '', faultLevel: '', deviceId: null, timeRange: [] },
            dialogVisible: false,
            detail: {}
        };
    },
    created() {
        this.loadList();
        this.loadEquipments();
    },
    methods: {
        async loadList() {
            this.loading = true;
            const params = {};
            if (this.query.orderStatus) params.orderStatus = this.query.orderStatus;
            if (this.query.faultLevel) params.faultLevel = this.query.faultLevel;
            if (this.query.deviceId) params.deviceId = this.query.deviceId;
            if (this.query.timeRange && this.query.timeRange.length === 2) {
                params.startTime = this.query.timeRange[0];
                params.endTime = this.query.timeRange[1];
            }
            try {
                const res = await this.$http.get('/work-orders', { params });
                this.workOrders = res.data || [];
            } catch (e) {
                this.$message.error('加载工单列表失败');
            } finally {
                this.loading = false;
            }
        },
        async loadEquipments() {
            try {
                const res = await this.$http.get('/equipments');
                this.equipmentList = res.data || [];
            } catch (e) {}
        },
        resetQuery() {
            this.query = { orderStatus: '', faultLevel: '', deviceId: null, timeRange: [] };
            this.loadList();
        },
        viewDetail(row) {
            this.detail = row;
            this.dialogVisible = true;
        },
        calcTotal(d) {
            if (!d || (d.partCost == null && d.laborCost == null && d.outsourceCost == null)) return '-';
            const total = (parseFloat(d.partCost) || 0) + (parseFloat(d.laborCost) || 0) + (parseFloat(d.outsourceCost) || 0);
            return '￥' + total.toFixed(2);
        },
        formatMttr(minutes) {
            if (!minutes) return '-';
            const h = Math.floor(minutes / 60);
            const m = minutes % 60;
            if (h > 0) return `${h}小时${m}分钟`;
            return `${m}分钟`;
        },
        statusType(status) {
            if (status === '报修') return 'warning';
            if (status === '已派工') return 'primary';
            if (status === '维修中') return 'danger';
            if (status === '完工') return 'success';
            return 'info';
        },
        levelType(level) {
            if (level === '紧急') return 'danger';
            if (level === '一般') return 'warning';
            return 'info';
        },
        formatTime(t) {
            if (!t) return '-';
            return t.replace('T', ' ').substring(0, 16);
        }
    }
};
</script>
