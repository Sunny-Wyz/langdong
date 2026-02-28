<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header">
                <span>智能补货建议</span>
                <el-tag type="danger" style="margin-left: 12px" size="small">紧急优先显示</el-tag>
            </div>

            <el-table :data="suggestions" border v-loading="loading" :row-class-name="urgencyRowClass">
                <el-table-column prop="partCode" label="备件编码" width="120"></el-table-column>
                <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                <el-table-column prop="suggestMonth" label="建议月份" width="100"></el-table-column>
                <el-table-column prop="currentStock" label="当前库存" width="90" align="center"></el-table-column>
                <el-table-column prop="reorderPoint" label="ROP补货点" width="100" align="center"></el-table-column>
                <el-table-column prop="suggestQty" label="建议采购量" width="100" align="center"></el-table-column>
                <el-table-column prop="forecastQty" label="月预测量" width="95" align="center"></el-table-column>
                <el-table-column label="置信区间" width="130" align="center">
                    <template slot-scope="scope">
                        <span>{{ scope.row.lowerBound }} ~ {{ scope.row.upperBound }}</span>
                    </template>
                </el-table-column>
                <el-table-column prop="urgency" label="紧急程度" width="95" align="center">
                    <template slot-scope="scope">
                        <el-tag :type="scope.row.urgency === '紧急' ? 'danger' : 'success'" size="small">
                            {{ scope.row.urgency }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="180" align="center">
                    <template slot-scope="scope">
                        <el-button type="primary" size="mini" @click="launchPurchase(scope.row)">发起采购</el-button>
                        <el-button type="warning" size="mini" @click="ignoreSuggest(scope.row)">忽略</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return { suggestions: [], loading: false };
    },
    created() { this.load(); },
    methods: {
        async load() {
            this.loading = true;
            try {
                const res = await this.$http.get('/reorder-suggests?status=待处理');
                this.suggestions = res.data || [];
            } catch (e) {
                this.$message.error('加载补货建议失败');
            } finally { this.loading = false; }
        },
        launchPurchase(row) {
            this.$router.push({
                path: '/home/purchase-apply', query: {
                    partCode: row.partCode, suggestId: row.id, qty: row.suggestQty
                }
            });
        },
        async ignoreSuggest(row) {
            await this.$confirm('确认忽略该补货建议？', '提示', { type: 'warning' });
            await this.$http.put(`/reorder-suggests/${row.id}/ignore`);
            this.$message.success('已忽略');
            this.load();
        },
        urgencyRowClass({ row }) {
            return row.urgency === '紧急' ? 'urgency-row' : '';
        }
    }
};
</script>

<style scoped>
::v-deep .urgency-row {
    background-color: #fff5f5 !important;
}
</style>
