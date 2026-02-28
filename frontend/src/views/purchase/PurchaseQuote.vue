<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header"><span>供应商询价比价</span></div>

            <!-- 选择订单 -->
            <el-form inline style="margin-bottom: 16px">
                <el-form-item label="选择采购订单">
                    <el-select v-model="selectedOrderId" placeholder="请选择订单" filterable style="width: 280px"
                        @change="loadQuotes">
                        <el-option v-for="o in orders" :key="o.id" :label="`${o.orderNo}  ${o.sparePartName}`"
                            :value="o.id"></el-option>
                    </el-select>
                </el-form-item>
            </el-form>

            <div v-if="currentOrder" style="margin-bottom: 20px">
                <el-descriptions :column="4" border size="small">
                    <el-descriptions-item label="备件">{{ currentOrder.sparePartCode }} {{ currentOrder.sparePartName
                        }}</el-descriptions-item>
                    <el-descriptions-item label="采购量">{{ currentOrder.orderQty }}</el-descriptions-item>
                    <el-descriptions-item label="参考价(元)">{{ refPrice || '—' }}</el-descriptions-item>
                    <el-descriptions-item label="当前状态">{{ currentOrder.orderStatus }}</el-descriptions-item>
                </el-descriptions>
            </div>

            <!-- 询价录入 -->
            <el-card shadow="never" style="margin-bottom: 20px" v-if="selectedOrderId">
                <div slot="header"><span>录入询价</span></div>
                <el-form :model="quoteForm" ref="quoteForm" inline>
                    <el-form-item label="供应商" :rules="[{ required: true }]">
                        <el-select v-model="quoteForm.supplierId" placeholder="选择供应商" style="width:180px" filterable>
                            <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id"></el-option>
                        </el-select>
                    </el-form-item>
                    <el-form-item label="报价(元)" :rules="[{ required: true }]">
                        <el-input-number v-model="quoteForm.quotePrice" :precision="2" :min="0.01"
                            style="width:140px"></el-input-number>
                    </el-form-item>
                    <el-form-item label="交货天数">
                        <el-input-number v-model="quoteForm.deliveryDays" :min="1"
                            style="width:110px"></el-input-number>
                    </el-form-item>
                    <el-form-item>
                        <el-button type="primary" @click="addQuote" size="small">录入</el-button>
                    </el-form-item>
                </el-form>
            </el-card>

            <!-- 报价列表 -->
            <el-table :data="quotes" border v-if="quotes.length">
                <el-table-column prop="supplierName" label="供应商"></el-table-column>
                <el-table-column prop="quotePrice" label="报价(元)" width="110" align="right"></el-table-column>
                <el-table-column label="偏差" width="110" align="center">
                    <template slot-scope="scope">
                        <span v-if="refPrice">
                            <el-tag :type="deviation(scope.row.quotePrice) > 15 ? 'danger' : 'success'" size="small">
                                {{ deviation(scope.row.quotePrice).toFixed(1) }}%
                            </el-tag>
                        </span>
                        <span v-else>—</span>
                    </template>
                </el-table-column>
                <el-table-column prop="deliveryDays" label="交货天数" width="95" align="center"></el-table-column>
                <el-table-column prop="quoteTime" label="报价时间" width="160"></el-table-column>
                <el-table-column label="中标" width="80" align="center">
                    <template slot-scope="scope">
                        <el-tag v-if="scope.row.isSelected" type="success" size="small">✓ 中标</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center">
                    <template slot-scope="scope">
                        <el-button v-if="!scope.row.isSelected" type="success" size="mini"
                            @click="selectWinner(scope.row)">选为中标</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <el-alert v-if="priceWarning" type="warning" show-icon style="margin-top:16px"
                title="⚠ 所选报价偏离历史参考价超过 ±15%，请主管确认后再中标" :closable="false"></el-alert>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            orders: [], selectedOrderId: null, currentOrder: null,
            quotes: [], suppliers: [], refPrice: null,
            quoteForm: { supplierId: null, quotePrice: 0, deliveryDays: 7 },
            priceWarning: false
        };
    },
    created() { this.loadOrders(); this.loadSuppliers(); },
    methods: {
        async loadOrders() {
            const res = await this.$http.get('/purchase-orders');
            this.orders = res.data || [];
        },
        async loadSuppliers() {
            const res = await this.$http.get('/suppliers');
            this.suppliers = res.data || [];
        },
        async loadQuotes() {
            const res = await this.$http.get(`/purchase-orders/${this.selectedOrderId}`);
            this.currentOrder = res.data;
            const qres = await this.$http.get(`/purchase-orders/${this.selectedOrderId}/quotes`);
            this.quotes = qres.data || [];
            // 获取参考价
            const pRes = await this.$http.get('/spare-parts');
            const part = (pRes.data || []).find(p => p.id === this.currentOrder.sparePartId);
            this.refPrice = part ? part.price : null;
        },
        deviation(price) {
            if (!this.refPrice) return 0;
            return Math.abs((price - this.refPrice) / this.refPrice * 100);
        },
        async addQuote() {
            if (!this.quoteForm.supplierId || !this.quoteForm.quotePrice) {
                this.$message.warning('请填写供应商和报价');
                return;
            }
            await this.$http.post(`/purchase-orders/${this.selectedOrderId}/quotes`, this.quoteForm);
            this.$message.success('询价录入成功');
            this.loadQuotes();
        },
        async selectWinner(row) {
            const dev = this.deviation(row.quotePrice);
            if (dev > 15) {
                this.priceWarning = true;
                const ok = await this.$confirm(
                    `报价偏离历史均价 ${dev.toFixed(1)}%，超过15%警戒线，确认中标？`,
                    '价格异常警告', { type: 'warning', confirmButtonText: '主管确认中标' }
                ).catch(() => false);
                if (!ok) return;
            }
            await this.$http.put(`/purchase-orders/${this.selectedOrderId}/quotes/${row.id}/select`);
            this.$message.success('已选中中标供应商，订单金额已更新');
            this.priceWarning = false;
            this.loadQuotes();
        }
    }
};
</script>
