<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>采购订单管理</span>
            </div>

            <!-- 筛选 -->
            <el-form inline style="margin-bottom: 16px">
                <el-form-item label="状态">
                    <el-select v-model="filter.orderStatus" clearable placeholder="全部" style="width:130px"
                        @change="load">
                        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="供应商">
                    <el-select v-model="filter.supplierId" clearable placeholder="全部" filterable style="width:160px"
                        @change="load">
                        <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="load" icon="el-icon-search">查询</el-button>
                </el-form-item>
            </el-form>

            <el-table :data="orders" border v-loading="loading" :row-class-name="overdueClass">
                <el-table-column prop="orderNo" label="订单号" width="180"></el-table-column>
                <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                <el-table-column prop="sparePartCode" label="备件编码" width="110"></el-table-column>
                <el-table-column prop="supplierName" label="供应商" width="150"></el-table-column>
                <el-table-column prop="orderQty" label="数量" width="70" align="center"></el-table-column>
                <el-table-column prop="unitPrice" label="单价(元)" width="90" align="right"></el-table-column>
                <el-table-column prop="totalAmount" label="总额(元)" width="100" align="right"></el-table-column>
                <el-table-column prop="orderStatus" label="状态" width="100" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="statusType(scope.row.orderStatus)" size="small">{{ scope.row.orderStatus
                        }}</el-tag>
                    </template>
                </el-table-column>
                <el-table-column prop="expectedDate" label="期望到货" width="105"></el-table-column>
                <el-table-column label="操作" width="200" align="center">
                    <template slot-scope="scope">
                        <el-button v-if="scope.row.orderStatus === '已下单'" size="mini"
                            @click="advance(scope.row, '已发货')">标记发货</el-button>
                        <el-button v-if="scope.row.orderStatus === '已发货'" size="mini"
                            @click="advance(scope.row, '到货')">确认到货</el-button>
                        <el-button v-if="scope.row.orderStatus === '到货'" type="success" size="mini"
                            @click="goAccept(scope.row)">去验收</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            orders: [], suppliers: [], loading: false,
            filter: { orderStatus: '', supplierId: null },
            statusOptions: ['已下单', '已发货', '到货', '验收通过', '验收失败']
        };
    },
    created() { this.load(); this.loadSuppliers(); },
    methods: {
        async load() {
            this.loading = true;
            try {
                const params = {};
                if (this.filter.orderStatus) params.orderStatus = this.filter.orderStatus;
                if (this.filter.supplierId) params.supplierId = this.filter.supplierId;
                const res = await this.$http.get('/purchase-orders', { params });
                this.orders = res.data || [];
            } finally { this.loading = false; }
        },
        async loadSuppliers() {
            const res = await this.$http.get('/suppliers');
            this.suppliers = res.data || [];
        },
        async advance(row, nextStatus) {
            await this.$http.put(`/purchase-orders/${row.id}/status`, null, { params: { orderStatus: nextStatus } });
            this.$message.success(`状态已更新为：${nextStatus}`);
            this.load();
        },
        goAccept(row) {
            this.$router.push(`/home/purchase-acceptance?orderId=${row.id}`);
        },
        statusType(s) {
            const map = { '已下单': 'info', '已发货': 'warning', '到货': 'primary', '验收通过': 'success', '验收失败': 'danger' };
            return map[s] || 'info';
        },
        overdueClass({ row }) {
            if (!row.expectedDate || row.actualDate) return '';
            return new Date(row.expectedDate) < new Date() ? 'overdue-row' : '';
        }
    }
};
</script>

<style scoped>
::v-deep .overdue-row {
    background-color: #fffbf0 !important;
}
</style>
