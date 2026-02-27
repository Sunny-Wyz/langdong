<template>
    <div style="padding: 24px">
        <el-card>
            <div slot="header" style="display: flex; justify-content: space-between; align-items: center">
                <span>收货入库 (按采购单)</span>
            </div>

            <el-form :inline="true" style="margin-bottom: 20px;">
                <el-form-item label="采购单号">
                    <el-input v-model="poCode" placeholder="请输入采购单号 (例: PO202602270001)"></el-input>
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="fetchPendingItems">加载待收明细</el-button>
                </el-form-item>
            </el-form>

            <el-table v-if="items.length > 0" :data="items" border style="width: 100%">
                <el-table-column prop="sparePartCode" label="备件编码" width="120"></el-table-column>
                <el-table-column prop="sparePartName" label="备件名称"></el-table-column>
                <el-table-column prop="quantity" label="采购总量" width="100"></el-table-column>
                <el-table-column prop="receivedQuantity" label="历史已收" width="100"></el-table-column>
                <el-table-column label="本次实收数量" width="150">
                    <template slot-scope="scope">
                        <el-input-number v-model="scope.row.actualQuantity" :min="0" :step="1"
                            size="small"></el-input-number>
                    </template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                    <template slot-scope="scope">
                        <el-tag :type="isOverReceiving(scope.row) ? 'danger' : 'success'">
                            {{ isOverReceiving(scope.row) ? '超收' : '正常' }}
                        </el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="备注(存放位置等)">
                    <template slot-scope="scope">
                        <el-input v-model="scope.row.remark" placeholder="如库区分区" size="small"></el-input>
                    </template>
                </el-table-column>
            </el-table>

            <div v-if="items.length > 0" style="margin-top: 20px;">
                <el-checkbox v-model="allowOverReceive" :disabled="!hasOverReceiving">
                    允许超收并确认差异 (出现红牌警告时必须勾选)
                </el-checkbox>
                <div style="margin-top: 15px; text-align: center;">
                    <el-button type="success" @click="submitStockIn"
                        :disabled="hasOverReceiving && !allowOverReceive">确认入库</el-button>
                </div>
            </div>

            <el-empty v-else description="请输入合法的采购单号以查询待入库明细"></el-empty>
        </el-card>
    </div>
</template>

<script>
export default {
    data() {
        return {
            poCode: 'PO202602270001',
            items: [],
            allowOverReceive: false,
            remark: ''
        }
    },
    computed: {
        hasOverReceiving() {
            return this.items.some(item => this.isOverReceiving(item))
        }
    },
    methods: {
        isOverReceiving(row) {
            const expected = row.quantity - row.receivedQuantity;
            return row.actualQuantity > expected;
        },
        async fetchPendingItems() {
            if (!this.poCode) {
                this.$message.warning("请输入单号");
                return;
            }
            try {
                const res = await this.$http.get(`/stock-in/po/${this.poCode}`);
                this.items = res.data.map(item => ({
                    ...item,
                    actualQuantity: item.quantity - item.receivedQuantity > 0 ? item.quantity - item.receivedQuantity : 0,
                    remark: ''
                }))
                this.allowOverReceive = false;
            } catch (e) {
                this.$message.error(e.response && e.response.data ? e.response.data : "查询采购单失败");
                this.items = [];
            }
        },
        async submitStockIn() {
            const submitItems = this.items.map(i => ({
                poItemId: i.id,          // 采购单明细行ID，用于后端精确匹配
                sparePartId: i.sparePartId,
                actualQuantity: i.actualQuantity,
                remark: i.remark
            })).filter(i => i.actualQuantity > 0);

            if (submitItems.length === 0) {
                this.$message.warning("本次入库没有大于0的明细，无法提交");
                return;
            }

            const payload = {
                purchaseOrderCode: this.poCode,
                remark: "系统界面入库",
                allowOverReceive: this.allowOverReceive,
                items: submitItems
            };

            try {
                await this.$http.post('/stock-in', payload);
                this.$message.success('入库成功，库存台账已更新！');
                this.items = [];
                this.poCode = '';
                this.allowOverReceive = false;
            } catch (e) {
                this.$message.error(e.response && e.response.data ? e.response.data : "入库失败");
            }
        }
    }
}
</script>
